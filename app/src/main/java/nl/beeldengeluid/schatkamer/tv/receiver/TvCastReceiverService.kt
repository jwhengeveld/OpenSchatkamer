package nl.beeldengeluid.schatkamer.tv.receiver

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.IBinder
import android.util.Log

/**
 * Achtergrondservice op Android TV die Apple AirPlay en Schatkamer TV casting ontvangt.
 * Adverteert mDNS services op het lokale Wi-Fi netwerk.
 */
class TvCastReceiverService : Service() {

    private val TAG = "TvCastReceiverService"
    private var airPlayServer: AirPlayServer? = null
    private var nsdManager: NsdManager? = null

    private var airPlayRegistrationListener: NsdManager.RegistrationListener? = null
    private var schatkamerRegistrationListener: NsdManager.RegistrationListener? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "TvCastReceiverService gestart")

        val server = AirPlayServer(applicationContext, port = 7000)
        airPlayServer = server
        server.start()

        startNsdRegistration(server)
    }

    private fun startNsdRegistration(server: AirPlayServer) {
        try {
            nsdManager = getSystemService(Context.NSD_SERVICE) as? NsdManager
            val nsd = nsdManager ?: return

            // 1. Apple AirPlay mDNS registratie
            val airPlayInfo = NsdServiceInfo().apply {
                serviceName = "De Schatkamer TV"
                serviceType = "_airplay._tcp"
                port = 7000
                setAttribute("model", "AppleTV3,2")
                setAttribute("srcvers", "220.68")
                setAttribute("deviceid", server.deviceMacAddress)
                setAttribute("features", "0x5A7FFFF7,0x1E")
                setAttribute("flags", "0x4")
                setAttribute("vv", "2")
            }

            airPlayRegistrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    Log.i(TAG, "AirPlay service geregistreerd: ${serviceInfo.serviceName}")
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "AirPlay registratie mislukt met code $errorCode")
                }

                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            }
            nsd.registerService(airPlayInfo, NsdManager.PROTOCOL_DNS_SD, airPlayRegistrationListener)

            // 2. De Schatkamer Direct TV mDNS registratie
            val schatkamerInfo = NsdServiceInfo().apply {
                serviceName = "De Schatkamer TV"
                serviceType = "_schatkamer._tcp"
                port = 7000
                setAttribute("app", "De Schatkamer TV")
                setAttribute("version", "1.0.5")
            }

            schatkamerRegistrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    Log.i(TAG, "Schatkamer TV service geregistreerd: ${serviceInfo.serviceName}")
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "Schatkamer TV registratie mislukt met code $errorCode")
                }

                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            }
            nsd.registerService(schatkamerInfo, NsdManager.PROTOCOL_DNS_SD, schatkamerRegistrationListener)

        } catch (e: Exception) {
            Log.e(TAG, "Fout bij mDNS registratie: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        val nsd = nsdManager
        airPlayRegistrationListener?.let {
            try { nsd?.unregisterService(it) } catch (_: Exception) {}
        }
        schatkamerRegistrationListener?.let {
            try { nsd?.unregisterService(it) } catch (_: Exception) {}
        }
        airPlayServer?.stop()
        airPlayServer = null
        Log.i(TAG, "TvCastReceiverService beëindigd")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            try {
                val intent = Intent(context, TvCastReceiverService::class.java)
                context.startService(intent)
            } catch (e: Exception) {
                Log.w("TvCastReceiverService", "Kon service niet starten: ${e.message}")
            }
        }
    }
}
