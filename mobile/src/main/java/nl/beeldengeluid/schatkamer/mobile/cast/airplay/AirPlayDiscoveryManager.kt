package nl.beeldengeluid.schatkamer.mobile.cast.airplay

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import nl.beeldengeluid.schatkamer.core.cast.CastDevice
import nl.beeldengeluid.schatkamer.core.cast.ProtocolType
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Ontdekt Apple AirPlay en De Schatkamer Android TV apparaten op het lokale netwerk via mDNS (NsdManager).
 * Gebruikt een seriële resolve-queue om 'FAILURE_ALREADY_ACTIVE' op Android 12-14 te voorkomen.
 */
class AirPlayDiscoveryManager(
    private val context: Context
) {
    private val TAG = "AirPlayDiscovery"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val deviceMap = ConcurrentHashMap<String, CastDevice>()
    private val _discoveredDevices = MutableStateFlow<List<CastDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<CastDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var nsdManager: NsdManager? = null
    private var airplayListener: NsdManager.DiscoveryListener? = null
    private var schatkamerTvListener: NsdManager.DiscoveryListener? = null

    private val resolveQueue = Channel<Pair<NsdServiceInfo, (NsdServiceInfo) -> Unit>>(Channel.UNLIMITED)
    private var resolveWorker: Job? = null

    init {
        startResolveWorker()
    }

    private fun startResolveWorker() {
        resolveWorker?.cancel()
        resolveWorker = scope.launch {
            for ((info, onResolved) in resolveQueue) {
                val resolved = resolveOnce(info) ?: continue
                try {
                    onResolved(resolved)
                } catch (e: Exception) {
                    Log.w(TAG, "Fout bij verwerken van ${info.serviceName}: ${e.message}")
                }
            }
        }
    }

    fun startScanning() {
        if (_isScanning.value) return
        _isScanning.value = true

        deviceMap.clear()
        publishDevices()

        try {
            nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            val nsd = nsdManager ?: return

            // 1. AirPlay listener
            airplayListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    resolveQueue.trySend(serviceInfo to { resolved ->
                        val host = resolved.host?.hostAddress ?: return@to
                        val port = resolved.port
                        val name = resolved.serviceName ?: "Apple AirPlay TV"
                        val model = resolved.attributes["model"]?.toString(Charsets.UTF_8)
                        val dev = CastDevice(
                            id = "airplay_${host}_$port",
                            name = name,
                            ip = host,
                            port = port,
                            protocol = ProtocolType.AIRPLAY,
                            model = model ?: "AirPlay Apparaat"
                        )
                        deviceMap[dev.id] = dev
                        publishDevices()
                    })
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    val removeId = deviceMap.values.find { it.name == serviceInfo.serviceName }?.id
                    if (removeId != null) {
                        deviceMap.remove(removeId)
                        publishDevices()
                    }
                }
            }
            nsd.discoverServices("_airplay._tcp", NsdManager.PROTOCOL_DNS_SD, airplayListener)

            // 2. Schatkamer TV listener
            schatkamerTvListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    resolveQueue.trySend(serviceInfo to { resolved ->
                        val host = resolved.host?.hostAddress ?: return@to
                        val port = resolved.port
                        val name = resolved.serviceName ?: "De Schatkamer TV"
                        val dev = CastDevice(
                            id = "schatkamer_tv_${host}_$port",
                            name = name,
                            ip = host,
                            port = port,
                            protocol = ProtocolType.SCHATKAMER_TV,
                            model = "De Schatkamer Android TV"
                        )
                        deviceMap[dev.id] = dev
                        publishDevices()
                    })
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    val removeId = deviceMap.values.find { it.name == serviceInfo.serviceName }?.id
                    if (removeId != null) {
                        deviceMap.remove(removeId)
                        publishDevices()
                    }
                }
            }
            nsd.discoverServices("_schatkamer._tcp", NsdManager.PROTOCOL_DNS_SD, schatkamerTvListener)

        } catch (e: Exception) {
            Log.e(TAG, "Kon mDNS discovery niet starten", e)
        }

        // Stop automatisch na 15 seconden om batterij te sparen
        scope.launch {
            delay(15_000)
            stopScanning()
        }
    }

    fun stopScanning() {
        if (!_isScanning.value) return
        _isScanning.value = false

        val nsd = nsdManager ?: return
        airplayListener?.let {
            try { nsd.stopServiceDiscovery(it) } catch (_: Exception) {}
        }
        schatkamerTvListener?.let {
            try { nsd.stopServiceDiscovery(it) } catch (_: Exception) {}
        }
        airplayListener = null
        schatkamerTvListener = null
    }

    private fun publishDevices() {
        val list = deviceMap.values.toList().sortedBy { it.name }
        _discoveredDevices.value = list
    }

    @Suppress("DEPRECATION")
    private suspend fun resolveOnce(info: NsdServiceInfo): NsdServiceInfo? {
        val nsd = nsdManager ?: return null
        return withTimeoutOrNull(4_000L) {
            suspendCancellableCoroutine { cont ->
                try {
                    nsd.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            if (cont.isActive) cont.resume(null)
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            if (cont.isActive) cont.resume(serviceInfo)
                        }
                    })
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    fun release() {
        stopScanning()
        resolveWorker?.cancel()
    }
}
