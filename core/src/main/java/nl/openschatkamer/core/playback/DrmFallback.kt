package nl.openschatkamer.core.playback

import android.content.Context
import androidx.media3.common.PlaybackException

/**
 * Onthoudt per toestel welke Widevine-route (hardware of software/L3) het laatst
 * werkte. Zo hoeft een tv met een haperende hardwareketen maar één keer te
 * falen; daarna begint hij meteen goed. Gaat de bewaarde route later mis, dan
 * probeert de speler de andere en onthoudt die.
 */
object DrmFallback {

    private const val PREFS = "drm_fallback"
    private const val KEY_L3 = "prefer_l3"

    fun prefersL3(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_L3, false)

    /** Bewaar de route waarmee een beveiligd programma net echt begon te spelen. */
    fun remember(context: Context, l3: Boolean) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_L3, false) != l3) prefs.edit().putBoolean(KEY_L3, l3).apply()
    }

    /**
     * Fouten waarbij L3 kan helpen: alles uit de DRM-reeks (6000–6999) en
     * decoderfouten van de beveiligde decoder (4000–4999 met een crypto-oorzaak).
     */
    fun canHelp(e: PlaybackException): Boolean =
        e.errorCode in 6000..6999 ||
            (e.errorCode in 4000..4999 && generateSequence(e as Throwable) { it.cause }
                .any { it is android.media.MediaCodec.CryptoException || it is android.media.MediaDrmException })

    /** Korte code voor in een foutmelding, bv. "drm license acquisition failed (6004)". */
    fun code(e: PlaybackException): String =
        "${e.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')} (${e.errorCode})"
}
