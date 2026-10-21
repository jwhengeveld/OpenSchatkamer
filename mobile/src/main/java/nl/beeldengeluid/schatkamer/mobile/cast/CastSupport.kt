package nl.beeldengeluid.schatkamer.mobile.cast

import android.content.Context
import android.util.Log
import android.view.ContextThemeWrapper
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import nl.beeldengeluid.schatkamer.mobile.BuildConfig
import nl.beeldengeluid.schatkamer.mobile.R

/**
 * Hulpfuncties voor Google Cast beschikbaarheid en de Cast-knop.
 */
object CastAvailability {

    /**
     * Zoekt passief naar Cast-apparaten zolang de app op de voorgrond is,
     * zodat de apparatenkiezer bij de eerste tik direct gevuld is.
     */
    fun discoverWhileStarted(activity: androidx.activity.ComponentActivity) {
        val playServices = runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity)
        }.getOrDefault(ConnectionResult.SERVICE_MISSING)
        if (playServices != ConnectionResult.SUCCESS) return

        runCatching {
            val router = androidx.mediarouter.media.MediaRouter.getInstance(activity)
            val selector = androidx.mediarouter.media.MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(BuildConfig.CAST_RECEIVER_ID))
                .build()
            val callback = object : androidx.mediarouter.media.MediaRouter.Callback() {}
            activity.lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
                override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                    router.addCallback(selector, callback, androidx.mediarouter.media.MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
                }
                override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                    router.removeCallback(callback)
                }
            })
        }
    }

    fun init(context: Context, onReady: (CastContext) -> Unit) {
        val playServices = runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        }.getOrDefault(ConnectionResult.SERVICE_MISSING)

        if (playServices != ConnectionResult.SUCCESS) return

        runCatching {
            CastContext.getSharedInstance(context, ContextCompat.getMainExecutor(context))
                .addOnSuccessListener { onReady(it) }
                .addOnFailureListener {
                    Log.w("CastAvailability", "CastContext initialisatie mislukt", it)
                }
        }
    }

    fun isAvailable(context: Context): Boolean =
        runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }.getOrDefault(false)
}

/**
 * De officiële MediaRouteButton voor Google Cast in Jetpack Compose.
 * Vereist dat de hosting Activity een AppCompatActivity / FragmentActivity is.
 */
@Composable
fun CastButton(
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier.size(40.dp),
        factory = { ctx ->
            val themed = ContextThemeWrapper(ctx, R.style.Theme_SchatkamerMobile)
            MediaRouteButton(themed).apply {
                runCatching {
                    CastButtonFactory.setUpMediaRouteButton(ctx.applicationContext, this)
                }.onFailure { err ->
                    Log.w("CastButton", "Kon MediaRouteButton niet koppelen aan Cast: ${err.message}")
                }
            }
        }
    )
}
