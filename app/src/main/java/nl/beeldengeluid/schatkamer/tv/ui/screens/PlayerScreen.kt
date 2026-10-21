package nl.beeldengeluid.schatkamer.tv.ui.screens

import android.app.Activity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import nl.beeldengeluid.schatkamer.core.data.PlayableMedia
import nl.beeldengeluid.schatkamer.tv.R
import nl.beeldengeluid.schatkamer.tv.receiver.TvCastManager
import nl.beeldengeluid.schatkamer.tv.receiver.TvControlCommand
import nl.beeldengeluid.schatkamer.tv.ui.player.buildTvPlayer
import nl.beeldengeluid.schatkamer.tv.ui.player.buildTvPlayerView
import nl.beeldengeluid.schatkamer.tv.ui.player.playTvMedia
import nl.beeldengeluid.schatkamer.tv.ui.player.setPlayerLabels
import nl.beeldengeluid.schatkamer.tv.ui.theme.FocusBorderCyan
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerCyan
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerDarkElevated
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerMagenta
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerTextMuted
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerTextPrimary

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

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var isControllerVisible by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var playerError by remember { mutableStateOf<String?>(null) }

    // Luister naar spelerstatus en fouten
    DisposableEffect(player) {
        TvCastManager.currentPlayer = player

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    playerError = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playerError = error.message ?: "Afspeelfout opgetreden"
            }
        }
        player.addListener(listener)

        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            if (TvCastManager.currentPlayer === player) {
                TvCastManager.currentPlayer = null
            }
            player.removeListener(listener)
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            mediaSession.release()
            player.release()
        }
    }

    LaunchedEffect(player) {
        TvCastManager.controlCommands.collect { cmd ->
            when (cmd) {
                is TvControlCommand.Play -> player.play()
                is TvControlCommand.Pause -> player.pause()
                is TvControlCommand.Seek -> player.seekTo(cmd.positionMs)
                is TvControlCommand.Stop -> onBack()
                is TvControlCommand.Volume -> player.volume = cmd.ratio
            }
        }
    }

    LaunchedEffect(media.streamUrl) {
        playerError = null
        playTvMedia(player, media)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { keyEvent ->
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
                            playerViewRef?.showController()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            player.seekForward()
                            playerViewRef?.showController()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            // Als de bediening niet zichtbaar is, toon de bediening en focus de play/pause knop
                            if (!isControllerVisible) {
                                playerViewRef?.showController()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            // Als bediening verborgen is: direct 10s terugspoelen
                            if (!isControllerVisible) {
                                player.seekBack()
                                playerViewRef?.showController()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            // Als bediening verborgen is: direct 30s vooruitspoelen
                            if (!isControllerVisible) {
                                player.seekForward()
                                playerViewRef?.showController()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!isControllerVisible) {
                                playerViewRef?.showController()
                                true
                            } else false
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
                    onControllerVisibilityChanged = { visible ->
                        isControllerVisible = visible
                    }
                ).apply {
                    setPlayerLabels(media.title, media.subtitle)
                    controllerShowTimeoutMs = 4500
                    playerViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Buffer-indicator voor TV
        if (isBuffering && playerError == null) {
            CircularProgressIndicator(
                color = SchatkamerCyan,
                strokeWidth = 4.dp,
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.Center)
            )
        }

        // Foutafhandeling overlay met TV D-Pad herstelmogelijkheid
        if (playerError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.90f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Fout",
                        tint = SchatkamerMagenta,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Kan stream niet afspelen",
                        color = SchatkamerTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Controleer je netwerkverbinding of probeer het opnieuw.",
                        color = SchatkamerTextMuted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TvDialogButton(
                            text = "Opnieuw proberen",
                            icon = Icons.Default.Refresh,
                            isPrimary = true,
                            onClick = {
                                playerError = null
                                playTvMedia(player, media)
                            }
                        )
                        TvDialogButton(
                            text = "Terug",
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            isPrimary = false,
                            onClick = onBack
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvDialogButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPrimary: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFocused) {
                    if (isPrimary) SchatkamerCyan else SchatkamerDarkElevated
                } else {
                    if (isPrimary) SchatkamerCyan.copy(alpha = 0.3f) else SchatkamerDarkElevated.copy(alpha = 0.6f)
                }
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) FocusBorderCyan else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isFocused && isPrimary) Color.Black else Color.White,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = if (isFocused && isPrimary) Color.Black else Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
