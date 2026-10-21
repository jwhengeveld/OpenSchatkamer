package nl.beeldengeluid.schatkamer.tv.ui.screens

import android.app.Activity
import android.content.Context
import android.view.KeyEvent
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import nl.beeldengeluid.schatkamer.core.data.PlayableMedia
import nl.beeldengeluid.schatkamer.tv.R
import nl.beeldengeluid.schatkamer.tv.ui.player.buildTvPlayer
import nl.beeldengeluid.schatkamer.tv.ui.player.buildTvPlayerView
import nl.beeldengeluid.schatkamer.tv.ui.player.playTvMedia
import nl.beeldengeluid.schatkamer.tv.ui.player.setPlayerLabels

@OptIn(UnstableApi::class)
@Composable
fun TvPlayerScreen(
    media: PlayableMedia,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val player = remember { buildTvPlayer(context) }
    val mediaSession = remember {
        MediaSession.Builder(context, player).build()
    }

    // Houd het TV-scherm aan tijdens het afspelen
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            mediaSession.release()
            player.release()
        }
    }

    LaunchedEffect(media.streamUrl) {
        playTvMedia(player, media)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { keyEvent ->
                // Afhandeling van Android TV afstandsbedieningstoetsen
                val nativeEvent = keyEvent.nativeKeyEvent
                if (nativeEvent.action == KeyEvent.ACTION_DOWN) {
                    when (nativeEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                            if (player.isPlaying) player.pause() else player.play()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            player.play()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            player.pause()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            player.seekBack()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            player.seekForward()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        AndroidView(
            factory = { ctx ->
                buildTvPlayerView(
                    context = ctx,
                    layoutRes = R.layout.schatkamer_player_view,
                    player = player,
                    onBack = onBack,
                    onControllerVisibilityChanged = {}
                ).apply {
                    setPlayerLabels(media.title, media.subtitle)
                    controllerShowTimeoutMs = 5000
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
