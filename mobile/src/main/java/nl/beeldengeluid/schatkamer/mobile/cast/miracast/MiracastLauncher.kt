package nl.beeldengeluid.schatkamer.mobile.cast.miracast

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

object MiracastLauncher {

    /**
     * Opent het Android systeemmenu voor Draadloos Scherm / Miracast / Smart View / Cast Screen.
     * Dit stelt de gebruiker in staat om het scherm rechtstreeks te beamen naar elke Miracast,
     * Google Cast of Smart TV met volledige hardwarematige Widevine DRM decodering.
     */
    fun launchWirelessDisplaySettings(context: Context): Boolean {
        val intents = listOf(
            Intent(Settings.ACTION_CAST_SETTINGS),
            Intent("android.settings.WIFI_DISPLAY_SETTINGS"),
            Intent(Settings.ACTION_DISPLAY_SETTINGS)
        )

        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    Toast.makeText(
                        context,
                        "Kies je TV in de lijst met Draadloze Schermen",
                        Toast.LENGTH_LONG
                    ).show()
                    return true
                }
            } catch (_: Exception) {
                // Probeer het volgende intent alternatief
            }
        }

        // Laatste poging zonder resolveActivity controle
        return try {
            val fallback = Intent(Settings.ACTION_CAST_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
            true
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Kan draadloos scherm instellingen niet openen op dit apparaat.",
                Toast.LENGTH_SHORT
            ).show()
            false
        }
    }
}
