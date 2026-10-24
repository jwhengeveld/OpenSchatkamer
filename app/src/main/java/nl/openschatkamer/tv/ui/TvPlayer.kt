package nl.openschatkamer.tv.ui

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import nl.openschatkamer.brand.BrandColors
import nl.openschatkamer.core.data.NotPlayableException
import nl.openschatkamer.core.data.PlayableMedia
import nl.openschatkamer.core.data.ProgressRepository
import nl.openschatkamer.core.data.SchatkamerItem
import nl.openschatkamer.core.data.SchatkamerVideoRepository
import nl.openschatkamer.core.playback.SchatkamerPlayback
import java.io.IOException

/**
 * Speler voor de tv: volledig scherm, bediening met de standaardknoppen van
 * Media3 (OK = pauze, links/rechts = spoelen, terug = sluiten).
 */
@OptIn(UnstableApi::class)
@Composable
fun TvPlayer(
    item: SchatkamerItem,
    fromStart: Boolean,
    video: SchatkamerVideoRepository,
    progress: ProgressRepository,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val player = remember { SchatkamerPlayback.buildPlayer(context) }
    var media by remember { mutableStateOf<PlayableMedia?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var attempt by remember { mutableIntStateOf(0) }

    fun save() {
        val m = media ?: return
        if (player.duration > 0) progress.save(item.copy(imageUrl = item.imageUrl.ifBlank { m.posterUrl }), player.currentPosition, player.duration)
    }

    LaunchedEffect(attempt) {
        error = null
        loading = true
        val resume = if (fromStart && attempt == 0) 0L else progress.resumePosition(item.id)
        video.playable(item.id, resume)
            .onSuccess { m -> media = m; SchatkamerPlayback.play(context, player, m) }
            .onFailure { e -> loading = false; error = describe(e) }
    }

    DisposableEffect(player) {
        var refreshed = false
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                loading = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) { save(); onClose() }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) save()
            }

            override fun onPlayerError(e: PlaybackException) {
                // Verlopen handtekening: één keer een verse stream ophalen en verder op dezelfde plek.
                if ((e.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode == 403 && !refreshed) {
                    refreshed = true
                    save()
                    attempt++
                    return
                }
                loading = false
                error = describe(e)
            }
        }
        player.addListener(listener)
        onDispose {
            save()
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (isActive) {
            delay(10_000)
            if (player.isPlaying) save()
        }
    }

    val playerView = remember { mutableStateOf<PlayerView?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(error) { if (error == null) runCatching { focus.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (error != null) return@onPreviewKeyEvent false
                val view = playerView.value ?: return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent event.key != Key.Back
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause, Key.Spacebar -> {
                        if (player.isPlaying) player.pause() else player.play()
                        view.showController(); true
                    }
                    Key.MediaPlay -> { player.play(); true }
                    Key.MediaPause -> { player.pause(); view.showController(); true }
                    Key.DirectionLeft, Key.MediaRewind -> { player.seekBack(); view.showController(); true }
                    Key.DirectionRight, Key.MediaFastForward -> { player.seekForward(); view.showController(); true }
                    Key.DirectionUp, Key.DirectionDown -> { view.showController(); true }
                    else -> false
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                    useController = true
                    setShowSubtitleButton(true)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    controllerShowTimeoutMs = 4000
                    keepScreenOn = true
                    // De toetsen komen via Compose binnen (zie onPreviewKeyEvent hierboven).
                    isFocusable = false
                    playerView.value = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (loading && error == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = BrandColors.Amber) }
        }
        error?.let { message ->
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(48.dp)) {
                    Text(item.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text(message, style = MaterialTheme.typography.bodyLarge, color = BrandColors.TextMuted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TvPill("Opnieuw proberen", { attempt++ }, selected = true)
                        TvPill("Terug", onClose)
                    }
                }
            }
        }
    }
}

private fun describe(e: Throwable): String = when {
    e is NotPlayableException -> e.message!!
    e is IOException || e.cause is IOException -> "Geen verbinding met De Schatkamer. Controleer het netwerk van de tv."
    e is PlaybackException && e.errorCode in 6000..6999 -> "Deze tv kan het beveiligde programma niet afspelen (Widevine DRM)."
    e is PlaybackException -> "Afspelen lukt niet (${e.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')})."
    else -> e.message ?: "Er ging iets mis bij het afspelen."
}
