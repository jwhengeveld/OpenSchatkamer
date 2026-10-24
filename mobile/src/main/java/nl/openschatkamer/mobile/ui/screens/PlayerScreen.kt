package nl.openschatkamer.mobile.ui.screens

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionDisabled
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import nl.openschatkamer.brand.BrandColors
import nl.openschatkamer.core.data.SchatkamerItem
import nl.openschatkamer.mobile.cast.CastButton
import nl.openschatkamer.mobile.playback.PlaybackState
import nl.openschatkamer.mobile.ui.components.Artwork
import nl.openschatkamer.mobile.ui.components.CardShape

interface PlayerActions {
    fun togglePlayPause()
    fun seekTo(ms: Long)
    fun seekBy(ms: Long)
    fun retry()
    fun setSubtitles(on: Boolean)
    fun stopCasting()
    fun setCastVolume(v: Float)
    fun toggleCastMute()
    fun setSpeed(speed: Float)
    fun playNext()
    fun cancelNext()
}

/**
 * Volledige speler. Lokaal toont hij de video met de standaardbediening van
 * Media3; tijdens casten wordt hij een afstandsbediening voor de Chromecast.
 */
@Composable
fun PlayerScreen(
    state: PlaybackState,
    player: ExoPlayer?,
    actions: PlayerActions,
    isInPip: Boolean,
    onClose: () -> Unit,
    onOpenSeries: (SchatkamerItem) -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            state.isCasting -> CastRemote(state, actions, onClose)
            state.item?.isAudio == true || state.media?.isAudio == true -> AudioPlayer(state, player, actions, onClose)
            else -> VideoPlayer(state, player, actions, isInPip, onClose)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(state: PlaybackState, player: ExoPlayer?, actions: PlayerActions, isInPip: Boolean, onClose: () -> Unit) {
    var controlsVisible by remember { mutableStateOf(true) }
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    var speedMenu by remember { mutableStateOf(false) }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowSubtitleButton(true)
                setShowNextButton(false)
                setShowPreviousButton(false)
                controllerShowTimeoutMs = 3500
                keepScreenOn = true
                setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v -> controlsVisible = v == android.view.View.VISIBLE })
                // Spoelen met de tijdbalk: toon een miniatuur van die plek.
                findViewById<androidx.media3.ui.DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)?.addListener(object : androidx.media3.ui.TimeBar.OnScrubListener {
                    override fun onScrubStart(timeBar: androidx.media3.ui.TimeBar, position: Long) { scrubMs = position }
                    override fun onScrubMove(timeBar: androidx.media3.ui.TimeBar, position: Long) { scrubMs = position }
                    override fun onScrubStop(timeBar: androidx.media3.ui.TimeBar, position: Long, canceled: Boolean) { scrubMs = null }
                })
            }
        },
        update = { view ->
            view.player = player
            view.useController = !isInPip
        },
        onRelease = { it.player = null },
        modifier = Modifier.fillMaxSize()
    )

    if (state.isLoading && state.error == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = BrandColors.Amber) }
    }
    scrubMs?.let { position -> StoryboardPreview(state, position, Modifier.fillMaxSize()) }
    state.error?.let { PlayerError(it, actions::retry, onClose) }
    if (!isInPip) NextEpisodeCard(state, actions, Modifier.fillMaxSize())

    if (!isInPip) {
        AnimatedVisibility(controlsVisible || state.error != null, enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Speler verkleinen", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(state.item?.title.orEmpty(), color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = state.media?.subtitle ?: state.item?.seriesTitle
                    if (!sub.isNullOrBlank()) Text(sub, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                Box {
                    TextButton(onClick = { speedMenu = true }) { Text("${formatSpeed(state.speed)}×", color = Color.White) }
                    DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                        SPEEDS.forEach { sp ->
                            DropdownMenuItem(text = { Text("${formatSpeed(sp)}×" + if (sp == 1f) "  (normaal)" else "") }, onClick = { actions.setSpeed(sp); speedMenu = false })
                        }
                    }
                }
                CastButton()
            }
        }
    }
}

private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
private fun formatSpeed(speed: Float) = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().replace('.', ',')

/** Miniatuur uit het storyboard op de plek waar je naartoe spoelt. */
@Composable
private fun StoryboardPreview(state: PlaybackState, positionMs: Long, modifier: Modifier) {
    val url = state.media?.stream?.storyboardUrl ?: return
    val clipStart = ((state.media?.stream?.clipStartSec ?: 0.0) * 1000).toLong()
    val context = LocalContext.current
    val http = remember { nl.openschatkamer.core.data.SchatkamerHttpClient.getInstance(context) }
    var image by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(url, positionMs / 3000) {
        val board = nl.openschatkamer.core.playback.StoryboardLoader.load(http, url) ?: return@LaunchedEffect
        val tile = board.tileAt(positionMs + clipStart) ?: return@LaunchedEffect
        image = nl.openschatkamer.core.playback.StoryboardLoader.bitmap(http, tile)?.asImageBitmap()
    }
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.padding(bottom = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            image?.let {
                androidx.compose.foundation.Image(
                    it, null,
                    modifier = Modifier.width(200.dp).aspectRatio(4f / 3f).clip(RoundedCornerShape(8.dp))
                        .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                )
            }
            Text(formatTime(positionMs), color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Aan het einde: de volgende aflevering, met aftellen en een knop om meteen te beginnen. */
@Composable
private fun NextEpisodeCard(state: PlaybackState, actions: PlayerActions, modifier: Modifier) {
    val next = state.next ?: return
    val countdown = state.nextCountdown ?: return
    Box(modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp), contentAlignment = Alignment.BottomEnd) {
        Row(
            Modifier.widthIn(max = 420.dp).clip(RoundedCornerShape(12.dp)).background(BrandColors.Surface.copy(alpha = 0.95f)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Artwork(next.imageUrl, Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Volgende aflevering over $countdown s", style = MaterialTheme.typography.labelMedium, color = BrandColors.Amber)
                Text(next.title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row {
                    TextButton(onClick = actions::playNext) { Text("Nu afspelen", color = BrandColors.Amber) }
                    TextButton(onClick = actions::cancelNext) { Text("Annuleren", color = BrandColors.TextMuted) }
                }
            }
        }
    }
}

@Composable
private fun PlayerError(message: String, onRetry: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(message, color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Amber, contentColor = BrandColors.Ink)) { Text("Opnieuw") }
                OutlinedButton(onClick = onClose) { Text("Sluiten", color = Color.White) }
            }
        }
    }
}

/** Gedeelde bediening voor audio en casten: tijdlijn plus terug/afspelen/vooruit. */
@Composable
private fun Transport(state: PlaybackState, actions: PlayerActions, modifier: Modifier = Modifier) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    val duration = state.durationMs.coerceAtLeast(1)
    val position = if (scrubbing) (scrubValue * duration).toLong() else state.positionMs
    Column(modifier) {
        Slider(
            value = (position.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = { scrubbing = true; scrubValue = it },
            onValueChangeFinished = { actions.seekTo((scrubValue * duration).toLong()); scrubbing = false },
            colors = SliderDefaults.colors(thumbColor = BrandColors.Amber, activeTrackColor = BrandColors.Amber, inactiveTrackColor = Color.White.copy(alpha = 0.2f))
        )
        Row {
            Text(formatTime(position), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text(if (state.durationMs > 0) formatTime(state.durationMs) else "--:--", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.seekBy(-10_000) }, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.Replay10, "10 seconden terug", tint = Color.White, modifier = Modifier.size(34.dp)) }
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(BrandColors.Amber).clickable { actions.togglePlayPause() },
                contentAlignment = Alignment.Center
            ) {
                if (state.isLoading) CircularProgressIndicator(color = BrandColors.Ink, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
                else Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "Pauzeren" else "Afspelen", tint = BrandColors.Ink, modifier = Modifier.size(42.dp))
            }
            IconButton(onClick = { actions.seekBy(30_000) }, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.Forward30, "30 seconden vooruit", tint = Color.White, modifier = Modifier.size(34.dp)) }
        }
    }
}

@Composable
private fun ArtworkBackdrop(url: String?) {
    Box(Modifier.fillMaxSize()) {
        Artwork(url, Modifier.fillMaxSize().blur(36.dp))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.92f)))))
    }
}

@Composable
private fun RemoteLayout(
    state: PlaybackState,
    actions: PlayerActions,
    onClose: () -> Unit,
    topLabel: @Composable () -> Unit,
    below: @Composable () -> Unit
) {
    val art = state.media?.posterUrl ?: state.item?.imageUrl
    ArtworkBackdrop(art)
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Verkleinen", tint = Color.White) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { topLabel() }
            CastButton()
        }
        Spacer(Modifier.weight(1f))
        Artwork(art, Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(16f / 9f).clip(CardShape))
        Spacer(Modifier.height(24.dp))
        state.media?.subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(it.uppercase(), color = BrandColors.Amber, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
        Text(state.item?.title.orEmpty(), color = Color.White, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(20.dp))
        if (state.error != null) {
            Text(state.error, color = BrandColors.Danger, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Button(onClick = actions::retry, colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Amber, contentColor = BrandColors.Ink)) { Text("Opnieuw proberen") }
            Spacer(Modifier.height(12.dp))
        }
        Transport(state, actions, Modifier.widthIn(max = 520.dp))
        below()
        Spacer(Modifier.weight(1f))
    }
    NextEpisodeCard(state, actions, Modifier.fillMaxSize())
}

/** Afstandsbediening tijdens het casten (in de geest van de Family7-app). */
@Composable
private fun CastRemote(state: PlaybackState, actions: PlayerActions, onClose: () -> Unit) {
    RemoteLayout(
        state, actions, onClose,
        topLabel = {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Cast, null, tint = BrandColors.Amber, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Speelt af op ${state.castDevice}", color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        },
        below = {
            Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions::toggleCastMute) {
                    Icon(if (state.castMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, "Geluid", tint = Color.White)
                }
                Slider(
                    value = state.castVolume, onValueChange = actions::setCastVolume, modifier = Modifier.width(200.dp),
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.2f))
                )
                if (state.hasSubtitles) {
                    IconButton(onClick = { actions.setSubtitles(!state.subtitlesOn) }) {
                        Icon(if (state.subtitlesOn) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionDisabled, "Ondertiteling", tint = Color.White)
                    }
                }
            }
            OutlinedButton(onClick = actions::stopCasting, modifier = Modifier.padding(top = 12.dp)) {
                Icon(Icons.Rounded.PhoneAndroid, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Verder op de telefoon", color = Color.White)
            }
        }
    )
}

/** Radio- en audio-opnames: geen videovlak, maar een rustige luisterweergave. */
@Composable
private fun AudioPlayer(state: PlaybackState, player: ExoPlayer?, actions: PlayerActions, onClose: () -> Unit) {
    RemoteLayout(
        state, actions, onClose,
        topLabel = { Text("LUISTEREN", color = BrandColors.Audio, style = MaterialTheme.typography.labelMedium) },
        below = { Spacer(Modifier.navigationBarsPadding()) }
    )
}

/** Balk onderaan tijdens het bladeren: wat er speelt, met afspelen/pauze en sluiten. */
@Composable
fun MiniPlayer(state: PlaybackState, onExpand: () -> Unit, onTogglePlay: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val item = state.item ?: return
    Column(modifier.fillMaxWidth().background(BrandColors.SurfaceHigh).clickable(onClick = onExpand)) {
        val fraction = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
        Box(Modifier.fillMaxWidth().height(2.dp).background(BrandColors.Outline)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(2.dp).background(BrandColors.Amber))
        }
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(state.media?.posterUrl ?: item.imageUrl, Modifier.width(72.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        state.error != null -> "Afspelen mislukt"
                        state.isCasting -> "Op ${state.castDevice}"
                        else -> state.media?.subtitle ?: item.seriesTitle ?: ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.isCasting) BrandColors.Amber else BrandColors.TextMuted, maxLines = 1
                )
            }
            IconButton(onClick = onTogglePlay) {
                if (state.isLoading) CircularProgressIndicator(color = BrandColors.Amber, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                else Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "Pauzeren" else "Afspelen")
            }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Sluiten", tint = BrandColors.TextMuted) }
        }
    }
}
