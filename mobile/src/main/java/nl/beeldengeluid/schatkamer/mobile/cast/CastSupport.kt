package nl.beeldengeluid.schatkamer.mobile.cast

import android.content.Context
import android.util.Log
import android.view.ContextThemeWrapper
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Airplay
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import nl.beeldengeluid.schatkamer.core.cast.ProtocolType
import nl.beeldengeluid.schatkamer.mobile.BuildConfig
import nl.beeldengeluid.schatkamer.mobile.R
import nl.beeldengeluid.schatkamer.mobile.cast.airplay.AirPlaySenderDialog
import nl.beeldengeluid.schatkamer.mobile.playback.LocalPlaybackManager
import nl.beeldengeluid.schatkamer.mobile.playback.PlaybackManager
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerCyan

/**
 * Hulpfuncties voor Google Cast beschikbaarheid en de Cast-knoppen.
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
 * De officiële, standaard MediaRouteButton voor Google Chromecast in Jetpack Compose.
 */
@Composable
fun GoogleCastButton(
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier.size(38.dp),
        factory = { ctx ->
            val themed = ContextThemeWrapper(ctx, R.style.Theme_SchatkamerMobile)
            MediaRouteButton(themed).apply {
                runCatching {
                    CastButtonFactory.setUpMediaRouteButton(ctx.applicationContext, this)
                }.onFailure { err ->
                    Log.w("GoogleCastButton", "Kon MediaRouteButton niet koppelen: ${err.message}")
                }
            }
        }
    )
}

/**
 * Toegewijde Apple AirPlay-knop die uitsluitend als zender fungeert.
 * Opent een stabiele AirPlay dialoog waarin je een Apple TV, Mac of Smart TV selecteert.
 */
@Composable
fun AirPlayButton(
    modifier: Modifier = Modifier,
    playbackManager: PlaybackManager = LocalPlaybackManager.current
) {
    val state by playbackManager.state.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    val isAirPlayActive = state.isCasting && state.castProtocol == ProtocolType.AIRPLAY

    IconButton(
        onClick = { showDialog = true },
        modifier = modifier.size(38.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Airplay,
            contentDescription = "Apple AirPlay Zender",
            tint = if (isAirPlayActive) SchatkamerCyan else Color.White,
            modifier = Modifier.size(22.dp)
        )
    }

    if (showDialog) {
        AirPlaySenderDialog(
            playbackManager = playbackManager,
            playbackState = state,
            onDismissRequest = { showDialog = false }
        )
    }
}

/**
 * Rij met zowel de standaard Chromecast-knop als de AirPlay-zenderknop.
 */
@Composable
fun CastButtonRow(
    modifier: Modifier = Modifier,
    playbackManager: PlaybackManager = LocalPlaybackManager.current
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        GoogleCastButton()
        AirPlayButton(playbackManager = playbackManager)
    }
}

/**
 * Compatibiliteitsalias: CastButton toont nu beide knoppen (Chromecast + AirPlay).
 */
@Composable
fun CastButton(
    modifier: Modifier = Modifier,
    playbackManager: PlaybackManager = LocalPlaybackManager.current
) {
    CastButtonRow(modifier = modifier, playbackManager = playbackManager)
}
