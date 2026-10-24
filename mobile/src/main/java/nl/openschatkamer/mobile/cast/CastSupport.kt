package nl.openschatkamer.mobile.cast

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.mediarouter.app.MediaRouteButton
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import nl.openschatkamer.core.BuildConfig
import nl.openschatkamer.mobile.R

/**
 * Start Google Cast als dat op dit toestel kan (zoals in de Family7-app).
 * Zonder Google Play-services werkt de app gewoon, alleen zonder Cast-knop;
 * dat mag nooit tot een crash leiden.
 */
object CastAvailability {

    fun isSupported(context: Context): Boolean = runCatching {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }.getOrDefault(false)

    fun init(context: Context, onReady: (CastContext) -> Unit) {
        if (!isSupported(context)) return
        runCatching {
            CastContext.getSharedInstance(context, ContextCompat.getMainExecutor(context))
                .addOnSuccessListener { onReady(it) }
        }
    }

    /**
     * Zoekt naar Chromecasts zolang de app op de voorgrond is, zodat de
     * apparatenkiezer bij de eerste tik al gevuld is. Passief: het systeem
     * bepaalt hoe vaak, dat spaart de accu.
     */
    fun discoverWhileStarted(activity: ComponentActivity) {
        if (!isSupported(activity)) return
        val router = MediaRouter.getInstance(activity)
        val selector = MediaRouteSelector.Builder()
            .addControlCategory(CastMediaControlIntent.categoryForCast(BuildConfig.CAST_RECEIVER_ID))
            .build()
        val callback = object : MediaRouter.Callback() {}
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                router.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
            }

            override fun onStop(owner: LifecycleOwner) {
                router.removeCallback(callback)
            }
        })
    }
}

/** De standaard Cast-knop; verbergt zichzelf als er geen Chromecast in de buurt is. */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.size(44.dp),
        factory = { ctx ->
            MediaRouteButton(ContextThemeWrapper(ctx, R.style.Theme_SchatkamerMobile)).apply {
                if (CastAvailability.isSupported(ctx)) {
                    runCatching { CastButtonFactory.setUpMediaRouteButton(ctx.applicationContext, this) }
                }
            }
        }
    )
}
