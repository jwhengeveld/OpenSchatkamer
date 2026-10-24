package nl.openschatkamer.mobile.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.MediaIntentReceiver
import com.google.android.gms.cast.framework.media.NotificationOptions
import nl.openschatkamer.core.BuildConfig
import nl.openschatkamer.mobile.MainActivity

/**
 * Google Cast-instellingen: de app stuurt naar de Chromecast-receiver van De Schatkamer.
 */
class CastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions {
        val notification = NotificationOptions.Builder()
            .setTargetActivityClassName(MainActivity::class.java.name)
            .setActions(
                listOf(
                    MediaIntentReceiver.ACTION_REWIND,
                    MediaIntentReceiver.ACTION_TOGGLE_PLAYBACK,
                    MediaIntentReceiver.ACTION_FORWARD,
                    MediaIntentReceiver.ACTION_STOP_CASTING
                ),
                intArrayOf(1, 3)
            )
            .setSkipStepMs(30_000)
            .build()

        val media = CastMediaOptions.Builder()
            .setNotificationOptions(notification)
            .build()

        return CastOptions.Builder()
            .setReceiverApplicationId(BuildConfig.CAST_RECEIVER_ID)
            .setCastMediaOptions(media)
            .setResumeSavedSession(true)
            .setEnableReconnectionService(true)
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
