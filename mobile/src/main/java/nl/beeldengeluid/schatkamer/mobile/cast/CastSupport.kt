package nl.beeldengeluid.schatkamer.mobile.cast

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import nl.beeldengeluid.schatkamer.mobile.BuildConfig

/**
 * Hulpfuncties voor Google Cast beschikbaarheid en de Cast-knop.
 */
object CastAvailability {

    fun init(context: Context, onReady: (CastContext) -> Unit) {
        val playServices = runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        }.getOrDefault(ConnectionResult.SERVICE_MISSING)

        if (playServices != ConnectionResult.SUCCESS) return

        runCatching {
            CastContext.getSharedInstance(context, Runnable::run)
                .addOnSuccessListener { onReady(it) }
        }
    }

    fun isAvailable(context: Context): Boolean =
        runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }.getOrDefault(false)
}

/**
 * De officiële MediaRouteButton voor Google Cast in Jetpack Compose.
 */
@Composable
fun CastButton(
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier.size(40.dp),
        factory = { ctx ->
            val themed = ContextThemeWrapper(ctx, androidx.appcompat.R.style.Theme_AppCompat_NoActionBar)
            MediaRouteButton(themed).apply {
                CastButtonFactory.setUpMediaRouteButton(ctx, this)
            }
        }
    )
}
