package nl.beeldengeluid.schatkamer.tv.receiver

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.beeldengeluid.schatkamer.core.data.PlayableMedia
import nl.beeldengeluid.schatkamer.core.data.SchatkamerVideoRepository
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import java.util.UUID

/**
 * Apple AirPlay en Schatkamer TV ontvanger server op poort 7000.
 * Ondersteunt AirPlay streaming vanaf iPhones, iPads, Macs en De Schatkamer Android app.
 */
class AirPlayServer(
    private val context: Context,
    private val port: Int = 7000,
    private val videoRepo: SchatkamerVideoRepository = SchatkamerVideoRepository.getInstance(context)
) {
    private val TAG = "TvAirPlayServer"
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listenJob: Job? = null

    @Volatile
    var isRunning = false
        private set

    val deviceMacAddress: String = run {
        val raw = UUID.nameUUIDFromBytes(context.packageName.toByteArray()).toString().replace("-", "")
        raw.take(12).chunked(2).joinToString(":") { it.uppercase(Locale.US) }
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        listenJob = scope.launch {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                Log.i(TAG, "AirPlay & Schatkamer TV server gestart op poort $port (ID: $deviceMacAddress)")

                while (isActive && isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "AirPlay server accept fout: ${e.message}")
                }
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (_: Exception) {}
        listenJob?.cancel()
        listenJob = null
        Log.i(TAG, "AirPlay & Schatkamer TV server gestopt")
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        val clientIp = socket.inetAddress?.hostAddress ?: "onbekend"
        try {
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())

            while (socket.isConnected && !socket.isClosed) {
                val headers = readHeaders(input) ?: break
                if (headers.isEmpty()) break

                val requestLine = headers[0]
                val parts = requestLine.split(" ")
                if (parts.size < 3) break

                val method = parts[0].uppercase(Locale.US)
                val uri = parts[1]

                val headerMap = parseHeaderMap(headers)
                val contentLength = headerMap["content-length"]?.toIntOrNull() ?: 0

                val body = if (contentLength > 0) {
                    readBody(input, contentLength)
                } else {
                    ByteArray(0)
                }

                handleHttpRequest(method, uri, headerMap, body, output, clientIp)
                output.flush()
            }
        } catch (e: Exception) {
            Log.d(TAG, "AirPlay client ($clientIp) verbroken: ${e.message}")
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private suspend fun handleHttpRequest(
        method: String,
        uri: String,
        headers: Map<String, String>,
        body: ByteArray,
        output: OutputStream,
        clientIp: String
    ) {
        when {
            uri.startsWith("/server-info") -> {
                sendServerInfo(output)
            }
            uri.startsWith("/playback-info") -> {
                sendPlaybackInfo(output)
            }
            uri.startsWith("/play") -> {
                handlePlay(headers, body, output, clientIp)
            }
            uri.startsWith("/rate") -> {
                handleRate(uri, output)
            }
            uri.startsWith("/scrub") -> {
                handleScrub(uri, output)
            }
            uri.startsWith("/stop") -> {
                handleStop(output)
            }
            uri.startsWith("/volume") -> {
                handleVolume(uri, output)
            }
            uri.startsWith("/schatkamer/play") -> {
                handleSchatkamerPlay(body, output)
            }
            uri.startsWith("/schatkamer/control") -> {
                handleSchatkamerControl(body, output)
            }
            else -> {
                sendHttpResponse(output, 200, "OK", "text/plain", "")
            }
        }
    }

    private fun handlePlay(
        headers: Map<String, String>,
        body: ByteArray,
        output: OutputStream,
        clientIp: String
    ) {
        var contentLocation = headers["content-location"]
        var startPosition = 0.0

        val bodyStr = String(body, Charsets.UTF_8)
        if (contentLocation == null && bodyStr.isNotEmpty()) {
            val lines = bodyStr.split("\r\n", "\n")
            for (line in lines) {
                if (line.startsWith("Content-Location:", ignoreCase = true)) {
                    contentLocation = line.substringAfter(":").trim()
                } else if (line.startsWith("Start-Position:", ignoreCase = true)) {
                    startPosition = line.substringAfter(":").trim().toDoubleOrNull() ?: 0.0
                }
            }
        }

        if (contentLocation.isNullOrEmpty()) {
            sendHttpResponse(output, 400, "Bad Request", "text/plain", "Missing Content-Location")
            return
        }

        val title = headers["x-apple-title"] ?: "AirPlay Video"
        Log.i(TAG, "AirPlay video ontvangen van $clientIp: $title -> $contentLocation")

        val media = PlayableMedia(
            id = "airplay_${System.currentTimeMillis()}",
            title = title,
            subtitle = "Apple AirPlay van $clientIp",
            streamUrl = contentLocation,
            posterUrl = "",
            resumePositionMs = (startPosition * 1000).toLong()
        )

        TvCastManager.play(media)
        sendHttpResponse(output, 200, "OK", "text/plain", "")
    }

    private suspend fun handleSchatkamerPlay(
        body: ByteArray,
        output: OutputStream
    ) {
        val bodyStr = String(body, Charsets.UTF_8)
        try {
            val json = JSONObject(bodyStr)
            val programId = json.optString("programId")
            val title = json.optString("title", "Schatkamer Uitzending")
            val positionMs = json.optLong("positionMs", 0L)
            val streamUrl = json.optString("streamUrl")

            if (programId.isNotBlank()) {
                // Haal actuele stream-tokens op via repository zodat CloudFront cookies vers zijn
                val detail = videoRepo.getProgramDetail("", programId).getOrNull()
                val resolved = detail?.let { videoRepo.toPlayableMedia(it, positionMs) }
                if (resolved != null) {
                    TvCastManager.play(resolved)
                    sendHttpResponse(output, 200, "OK", "application/json", "{\"status\":\"ok\"}")
                    return
                }
            }

            if (streamUrl.isNotBlank()) {
                val media = PlayableMedia(
                    id = programId.ifBlank { "schatkamer_cast_${System.currentTimeMillis()}" },
                    title = title,
                    subtitle = "Gecast vanaf telefoon",
                    streamUrl = streamUrl,
                    posterUrl = "",
                    resumePositionMs = positionMs
                )
                TvCastManager.play(media)
                sendHttpResponse(output, 200, "OK", "application/json", "{\"status\":\"ok\"}")
                return
            }

            sendHttpResponse(output, 400, "Bad Request", "application/json", "{\"error\":\"Missing programId or streamUrl\"}")
        } catch (e: Exception) {
            Log.e(TAG, "Fout bij verwerken schatkamer/play", e)
            sendHttpResponse(output, 500, "Internal Error", "application/json", "{\"error\":\"${e.message}\"}")
        }
    }

    private fun handleSchatkamerControl(
        body: ByteArray,
        output: OutputStream
    ) {
        val bodyStr = String(body, Charsets.UTF_8)
        try {
            val json = JSONObject(bodyStr)
            when (json.optString("action")) {
                "play" -> TvCastManager.sendCommand(TvControlCommand.Play)
                "pause" -> TvCastManager.sendCommand(TvControlCommand.Pause)
                "seek" -> {
                    val pos = json.optLong("positionMs", 0L)
                    TvCastManager.sendCommand(TvControlCommand.Seek(pos))
                }
                "stop" -> TvCastManager.sendCommand(TvControlCommand.Stop)
            }
            sendHttpResponse(output, 200, "OK", "application/json", "{\"status\":\"ok\"}")
        } catch (_: Exception) {
            sendHttpResponse(output, 400, "Bad Request", "application/json", "{\"error\":\"Invalid JSON\"}")
        }
    }

    private fun handleRate(uri: String, output: OutputStream) {
        val valueStr = uri.substringAfter("value=").substringBefore("&")
        val value = valueStr.toFloatOrNull() ?: 1.0f
        if (value > 0.0f) {
            TvCastManager.sendCommand(TvControlCommand.Play)
        } else {
            TvCastManager.sendCommand(TvControlCommand.Pause)
        }
        sendHttpResponse(output, 200, "OK", "text/plain", "")
    }

    private fun handleScrub(uri: String, output: OutputStream) {
        val posStr = uri.substringAfter("position=").substringBefore("&")
        posStr.toDoubleOrNull()?.let { posSec ->
            TvCastManager.sendCommand(TvControlCommand.Seek((posSec * 1000).toLong()))
        }
        sendHttpResponse(output, 200, "OK", "text/plain", "")
    }

    private fun handleStop(output: OutputStream) {
        TvCastManager.sendCommand(TvControlCommand.Stop)
        sendHttpResponse(output, 200, "OK", "text/plain", "")
    }

    private fun handleVolume(uri: String, output: OutputStream) {
        val volStr = uri.substringAfter("volume=").substringBefore("&")
        volStr.toFloatOrNull()?.let { volVal ->
            val ratio = if (volVal in -30.0f..0.0f) {
                (1.0f + (volVal / 30.0f)).coerceIn(0.0f, 1.0f)
            } else if (volVal <= -144.0f) {
                0.0f
            } else {
                1.0f
            }
            TvCastManager.sendCommand(TvControlCommand.Volume(ratio))
        }
        sendHttpResponse(output, 200, "OK", "text/plain", "")
    }

    private fun sendServerInfo(output: OutputStream) {
        val plist = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>deviceid</key>
    <string>$deviceMacAddress</string>
    <key>features</key>
    <integer>1518338039</integer>
    <key>model</key>
    <string>AppleTV3,2</string>
    <key>protovers</key>
    <string>1.0</string>
    <key>srcvers</key>
    <string>220.68</string>
</dict>
</plist>""".trimIndent()
        sendHttpResponse(output, 200, "OK", "text/x-apple-plist+xml", plist)
    }

    private fun sendPlaybackInfo(output: OutputStream) {
        val player = TvCastManager.currentPlayer
        val durationSec = (player?.duration?.takeIf { it > 0 } ?: 0L) / 1000.0
        val positionSec = (player?.currentPosition ?: 0L) / 1000.0
        val rate = if (player?.isPlaying == true) 1.0 else 0.0

        val info = """
duration: $durationSec
position: $positionSec
rate: $rate
readyToPlay: 1
playbackBufferEmpty: 0
""".trimIndent()
        sendHttpResponse(output, 200, "OK", "text/parameters", info)
    }

    private fun sendHttpResponse(
        output: OutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: String
    ) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val response = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Server: AirTunes/220.68\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${bodyBytes.size}\r\n" +
                "Connection: keep-alive\r\n\r\n"
        output.write(response.toByteArray(Charsets.UTF_8))
        if (bodyBytes.isNotEmpty()) {
            output.write(bodyBytes)
        }
    }

    private fun readHeaders(input: BufferedInputStream): List<String>? {
        val headers = mutableListOf<String>()
        val line = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) {
                if (headers.isEmpty() && line.isEmpty()) return null
                break
            }
            if (b == '\n'.code) {
                val str = line.toString().trimEnd('\r')
                line.clear()
                if (str.isEmpty()) break
                headers.add(str)
            } else {
                line.append(b.toChar())
            }
        }
        return headers
    }

    private fun parseHeaderMap(headers: List<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (i in 1 until headers.size) {
            val h = headers[i]
            val colon = h.indexOf(':')
            if (colon != -1) {
                val key = h.substring(0, colon).trim().lowercase(Locale.US)
                val value = h.substring(colon + 1).trim()
                map[key] = value
            }
        }
        return map
    }

    private fun readBody(input: BufferedInputStream, length: Int): ByteArray {
        val data = ByteArray(length)
        var totalRead = 0
        while (totalRead < length) {
            val read = input.read(data, totalRead, length - totalRead)
            if (read == -1) break
            totalRead += read
        }
        return data
    }
}
