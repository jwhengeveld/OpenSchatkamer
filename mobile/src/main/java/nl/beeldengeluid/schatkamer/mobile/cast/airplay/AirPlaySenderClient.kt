package nl.beeldengeluid.schatkamer.mobile.cast.airplay

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.beeldengeluid.schatkamer.core.cast.CastDevice
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Apple AirPlay v1/v2 sender client.
 * Verzendt HLS of MP4 streams direct naar Apple TV's, Macs en AirPlay 2 Smart TV's via HTTP control op poort 7000.
 */
class AirPlaySenderClient(
    val device: CastDevice
) {
    private val TAG = "AirPlaySenderClient"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var statusJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    suspend fun playMedia(
        mediaUrl: String,
        title: String,
        startPositionSec: Double = 0.0
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://${device.ip}:${device.port}/play")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 6000
                readTimeout = 6000
                doOutput = true
                setRequestProperty("Content-Type", "text/parameters")
                setRequestProperty("User-Agent", "MediaControl/1.0")
                setRequestProperty("X-Apple-Title", title)
            }

            val body = "Content-Location: $mediaUrl\nStart-Position: $startPositionSec\n"
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                _isPlaying.value = true
                _errorMessage.value = null
                startStatusPolling()
                true
            } else if (responseCode == 403) {
                _errorMessage.value = "AirPlay-toegang geweigerd door ontvanger (wachtwoord of beveiligingsinstelling)."
                false
            } else {
                _errorMessage.value = "AirPlay HTTP-fout ($responseCode)."
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "AirPlay playMedia fout", e)
            _errorMessage.value = "Verbindingsfout met ${device.name}: ${e.message}"
            false
        }
    }

    suspend fun pause(): Boolean = withContext(Dispatchers.IO) {
        sendPost("/rate?value=0.000000").also { success ->
            if (success) _isPlaying.value = false
        }
    }

    suspend fun resume(): Boolean = withContext(Dispatchers.IO) {
        sendPost("/rate?value=1.000000").also { success ->
            if (success) _isPlaying.value = true
        }
    }

    suspend fun seekTo(positionMs: Long): Boolean = withContext(Dispatchers.IO) {
        val seconds = positionMs / 1000.0
        sendPost("/scrub?position=$seconds").also { success ->
            if (success) _positionMs.value = positionMs
        }
    }

    suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        stopStatusPolling()
        sendPost("/stop").also {
            _isPlaying.value = false
            _positionMs.value = 0L
        }
    }

    private fun startStatusPolling() {
        stopStatusPolling()
        statusJob = scope.launch {
            while (isActive) {
                delay(1000)
                fetchPlaybackInfo()
            }
        }
    }

    private fun stopStatusPolling() {
        statusJob?.cancel()
        statusJob = null
    }

    private suspend fun fetchPlaybackInfo() = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://${device.ip}:${device.port}/playback-info")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("User-Agent", "MediaControl/1.0")
            }
            if (conn.responseCode in 200..299) {
                val response = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).readText()
                parsePlaybackInfo(response)
            }
        } catch (_: Exception) {}
    }

    private fun parsePlaybackInfo(text: String) {
        val durationMatch = Regex("duration[:\\s]+([0-9.]+)").find(text)
        val positionMatch = Regex("position[:\\s]+([0-9.]+)").find(text)
        val rateMatch = Regex("rate[:\\s]+([0-9.]+)").find(text)

        durationMatch?.groupValues?.get(1)?.toDoubleOrNull()?.let {
            if (it > 0) _durationMs.value = (it * 1000).toLong()
        }
        positionMatch?.groupValues?.get(1)?.toDoubleOrNull()?.let {
            _positionMs.value = (it * 1000).toLong()
        }
        rateMatch?.groupValues?.get(1)?.toDoubleOrNull()?.let {
            _isPlaying.value = it > 0.0
        }
    }

    private fun sendPost(endpoint: String): Boolean {
        return try {
            val url = URL("http://${device.ip}:${device.port}$endpoint")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "MediaControl/1.0")
            }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            Log.w(TAG, "sendPost $endpoint fout: ${e.message}")
            false
        }
    }

    fun release() {
        stopStatusPolling()
        scope.cancel()
    }
}
