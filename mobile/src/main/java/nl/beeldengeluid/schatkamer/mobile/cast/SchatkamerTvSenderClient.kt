package nl.beeldengeluid.schatkamer.mobile.cast

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
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Direct zender-client voor De Schatkamer Android TV app.
 * Stuurt het programma-ID en afspeelpositie door zodat de Android TV het programma
 * native in ExoPlayer met volledige hardware Widevine DRM kan afspelen.
 */
class SchatkamerTvSenderClient(
    val device: CastDevice
) {
    private val TAG = "SchatkamerTvSender"
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

    suspend fun playProgram(
        programId: String,
        title: String,
        startPositionMs: Long = 0L,
        streamUrl: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://${device.ip}:${device.port}/schatkamer/play")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 6000
                readTimeout = 6000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "SchatkamerMobile/1.0")
            }

            val payload = JSONObject().apply {
                put("programId", programId)
                put("title", title)
                put("positionMs", startPositionMs)
                if (!streamUrl.isNullOrBlank()) {
                    put("streamUrl", streamUrl)
                }
            }.toString()

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload) }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                _isPlaying.value = true
                _positionMs.value = startPositionMs
                _errorMessage.value = null
                startStatusPolling()
                true
            } else {
                _errorMessage.value = "TV weigerde afspelen (HTTP $responseCode)"
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Schatkamer TV afspelen fout", e)
            _errorMessage.value = "Kan geen verbinding maken met ${device.name}: ${e.message}"
            false
        }
    }

    suspend fun pause(): Boolean = sendControl("pause").also { if (it) _isPlaying.value = false }

    suspend fun resume(): Boolean = sendControl("play").also { if (it) _isPlaying.value = true }

    suspend fun seekTo(positionMs: Long): Boolean =
        sendControl("seek", mapOf("positionMs" to positionMs)).also { if (it) _positionMs.value = positionMs }

    suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        stopStatusPolling()
        sendControl("stop").also {
            _isPlaying.value = false
            _positionMs.value = 0L
        }
    }

    private suspend fun sendControl(action: String, extra: Map<String, Any> = emptyMap()): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("http://${device.ip}:${device.port}/schatkamer/control")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 4000
                    readTimeout = 4000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                val payload = JSONObject().apply {
                    put("action", action)
                    extra.forEach { (k, v) -> put(k, v) }
                }.toString()

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload) }
                conn.responseCode in 200..299
            } catch (e: Exception) {
                Log.w(TAG, "sendControl $action fout: ${e.message}")
                false
            }
        }

    private fun startStatusPolling() {
        stopStatusPolling()
        statusJob = scope.launch {
            while (isActive) {
                delay(1000)
                fetchStatus()
            }
        }
    }

    private fun stopStatusPolling() {
        statusJob?.cancel()
        statusJob = null
    }

    private suspend fun fetchStatus() = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://${device.ip}:${device.port}/playback-info")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
            }
            if (conn.responseCode in 200..299) {
                val response = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).readText()
                val durationMatch = Regex("duration[:\\s]+([0-9.]+)").find(response)
                val positionMatch = Regex("position[:\\s]+([0-9.]+)").find(response)
                val rateMatch = Regex("rate[:\\s]+([0-9.]+)").find(response)

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
        } catch (_: Exception) {}
    }

    fun release() {
        stopStatusPolling()
        scope.cancel()
    }
}
