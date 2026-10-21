package nl.beeldengeluid.schatkamer.mobile.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.util.Rational
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import nl.beeldengeluid.schatkamer.mobile.cast.CastButton
import nl.beeldengeluid.schatkamer.mobile.playback.PlaybackManager
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerCyan
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerMagenta
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerTextMuted
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerTextPrimary

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    playbackManager: PlaybackManager,
    onBack: () -> Unit,
    isInPipMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activity = context.findActivity()
    val state by playbackManager.state.collectAsState()
    val player by playbackManager.player.collectAsState()

    var showControls by remember { mutableStateOf(true) }
    var seekFeedbackText by remember { mutableStateOf<String?>(null) }
    var seekFeedbackSide by remember { mutableIntStateOf(0) } // -1 links, 1 rechts

    // Automatisch herstellen naar default oriëntatie bij verlaten van de speler
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Modern Android 12+ (API 31+) automatisch Picture-in-Picture bij home swipe
    DisposableEffect(activity, state.isPlaying) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activity != null) {
            runCatching {
                val pipParams = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setAutoEnterEnabled(state.isPlaying)
                    .build()
                activity.setPictureInPictureParams(pipParams)
            }
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activity != null) {
                runCatching {
                    activity.setPictureInPictureParams(
                        PictureInPictureParams.Builder().setAutoEnterEnabled(false).build()
                    )
                }
            }
        }
    }

    // Auto-hide controls na 4 seconden wanneer aan het afspelen
    LaunchedEffect(showControls, state.isPlaying) {
        if (showControls && state.isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    // Reset van de dubbeltik seek indicator na 750ms
    LaunchedEffect(seekFeedbackText) {
        if (seekFeedbackText != null) {
            delay(750)
            seekFeedbackText = null
            seekFeedbackSide = 0
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (!isInPipMode) {
                            showControls = !showControls
                        }
                    },
                    onDoubleTap = { offset ->
                        if (!isInPipMode) {
                            val width = size.width
                            if (offset.x < width * 0.35f) {
                                playbackManager.seekBy(-10_000L)
                                seekFeedbackText = "-10s"
                                seekFeedbackSide = -1
                            } else if (offset.x > width * 0.65f) {
                                playbackManager.seekBy(10_000L)
                                seekFeedbackText = "+10s"
                                seekFeedbackSide = 1
                            } else {
                                if (state.isPlaying) playbackManager.pause() else playbackManager.resume()
                            }
                        }
                    }
                )
            }
    ) {
        // Media3 PlayerView voor video weergave
        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        this.player = player
                    }
                },
                update = { view ->
                    view.player = player
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Buffer-indicator
        if (state.isLoading && state.error == null) {
            CircularProgressIndicator(
                color = SchatkamerCyan,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Dubbeltik seek feedback animatie (-10s / +10s)
        if (seekFeedbackText != null && !isInPipMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp),
                contentAlignment = if (seekFeedbackSide < 0) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (seekFeedbackSide < 0) Icons.Default.Replay10 else Icons.Default.Forward30,
                            contentDescription = null,
                            tint = SchatkamerCyan,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = seekFeedbackText.orEmpty(),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Cast indicator als we aan het casten zijn
        if (state.isCasting && !isInPipMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CastButton()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Aan het afspelen op ${state.castDeviceName ?: "TV"}",
                        color = SchatkamerTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Foutafhandeling overlay met herstelknop
        if (state.error != null && !isInPipMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Fout",
                        tint = SchatkamerMagenta,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Kan stream niet afspelen",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.error.orEmpty(),
                        color = SchatkamerTextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { playbackManager.retry() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SchatkamerCyan,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Opnieuw proberen", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onBack,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text("Sluiten")
                        }
                    }
                }
            }
        }

        // Spelerbediening overlay (verborgen in PiP mode)
        if (showControls && !isInPipMode && state.error == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                // Bovenbalk: Terug, Titel, Oriëntatie, PiP, Cast
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .displayCutoutPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Terug", tint = Color.White)
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.media?.title.orEmpty(),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        if (!state.media?.subtitle.isNullOrBlank()) {
                            Text(
                                text = state.media?.subtitle.orEmpty(),
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }

                    // Schakelen naar liggend/staand (Oriëntatie toggle)
                    IconButton(
                        onClick = {
                            activity?.requestedOrientation = if (isLandscape) {
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isLandscape) "Staand (portret)" else "Liggend (volledig scherm)",
                            tint = Color.White
                        )
                    }

                    // Picture in Picture knop
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        IconButton(
                            onClick = {
                                if (activity != null) {
                                    runCatching {
                                        val params = PictureInPictureParams.Builder()
                                            .setAspectRatio(Rational(16, 9))
                                            .build()
                                        activity.enterPictureInPictureMode(params)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.PictureInPictureAlt, contentDescription = "Beeld-in-beeld", tint = Color.White)
                        }
                    }

                    CastButton(modifier = Modifier.padding(end = 4.dp))
                }

                // Midden: Spoelen en Afspelen/Pauze
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    IconButton(
                        onClick = { playbackManager.seekBy(-10_000L) },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(Icons.Default.Replay10, contentDescription = "10s terug", tint = Color.White, modifier = Modifier.size(36.dp))
                    }

                    IconButton(
                        onClick = {
                            if (state.isPlaying) playbackManager.pause() else playbackManager.resume()
                        },
                        modifier = Modifier
                            .size(70.dp)
                            .background(SchatkamerCyan, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pauze" else "Afspelen",
                            tint = Color.Black,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    IconButton(
                        onClick = { playbackManager.seekBy(30_000L) },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(Icons.Default.Forward30, contentDescription = "30s vooruit", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }

                // Onderbalk: Voortgangsbalk, Tijdsaanduiding, Fullscreen toggle
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .displayCutoutPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    Slider(
                        value = if (state.durationMs > 0) state.currentPositionMs.toFloat() else 0f,
                        onValueChange = { playbackManager.seekTo(it.toLong()) },
                        valueRange = 0f..(state.durationMs.toFloat().coerceAtLeast(1f)),
                        colors = SliderDefaults.colors(
                            thumbColor = SchatkamerCyan,
                            activeTrackColor = SchatkamerCyan,
                            inactiveTrackColor = Color.Gray.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(formatDuration(state.currentPositionMs), color = Color.White, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatDuration(state.durationMs), color = Color.LightGray, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    activity?.requestedOrientation = if (isLandscape) {
                                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    } else {
                                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = if (isLandscape) "Staand (portret)" else "Liggend (volledig scherm)",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
