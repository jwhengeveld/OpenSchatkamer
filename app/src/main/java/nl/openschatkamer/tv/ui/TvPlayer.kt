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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.width
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
import nl.openschatkamer.core.data.LibraryRepository
import nl.openschatkamer.core.data.SchatkamerCatalogRepository
import nl.openschatkamer.core.data.SchatkamerHttpClient
import nl.openschatkamer.core.data.SortOrder
import nl.openschatkamer.core.playback.StoryboardLoader
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
    progress: LibraryRepository,
    catalog: SchatkamerCatalogRepository,
    onPlayNext: (SchatkamerItem) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val player = remember { SchatkamerPlayback.buildPlayer(context) }
    var media by remember { mutableStateOf<PlayableMedia?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var attempt by remember { mutableIntStateOf(0) }
    var next by remember { mutableStateOf<SchatkamerItem?>(null) }
    var countdown by remember { mutableStateOf<Int?>(null) }
    var seekPreview by remember { mutableStateOf<Long?>(null) }
    var thumb by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val http = remember { SchatkamerHttpClient.getInstance(context) }

    // De volgende aflevering van dezelfde serie, voor automatisch doorspelen.
    LaunchedEffect(media?.seriesId) {
        val seriesId = media?.seriesId ?: return@LaunchedEffect
        val episodes = catalog.episodes(seriesId, sort = SortOrder.OLDEST).getOrNull()?.items.orEmpty()
        val index = episodes.indexOfFirst { it.id == item.id }
        next = if (index >= 0) episodes.getOrNull(index + 1) else null
    }
    LaunchedEffect(countdown) {
        val c = countdown ?: return@LaunchedEffect
        if (c <= 0) { next?.let(onPlayNext); countdown = null } else { delay(1000); countdown = c - 1 }
    }
    // Miniatuur van de plek waarnaar je spoelt.
    LaunchedEffect(seekPreview) {
        val pos = seekPreview ?: return@LaunchedEffect
        val m = media
        val url = m?.stream?.storyboardUrl
        if (url != null) {
            val board = StoryboardLoader.load(http, url)
            val tile = board?.tileAt(pos + (m.stream.clipStartSec * 1000).toLong())
            thumb = tile?.let { StoryboardLoader.bitmap(http, it)?.asImageBitmap() }
        }
        delay(1600)
        seekPreview = null
    }

    val scope = rememberCoroutineScope()
    fun save() {
        val m = media ?: return
        if (player.duration <= 0) return
        val saved = item.copy(
            title = item.title.ifBlank { m.title },
            imageUrl = item.imageUrl.ifBlank { m.posterUrl },
            seriesId = item.seriesId ?: m.seriesId,
            seriesTitle = item.seriesTitle ?: m.subtitle.ifBlank { null }
        )
        val position = player.currentPosition
        val duration = player.duration
        progress.saveProgress(saved, position, duration)
        // Ook op het startscherm van de tv bij "Verder kijken".
        scope.launch { nl.openschatkamer.tv.WatchNext.update(context, saved, position, duration, finished = duration - position < 60_000) }
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
                if (state == Player.STATE_ENDED) { save(); if (next != null) countdown = 10 else onClose() }
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
                        if (countdown != null) { countdown = 0; return@onPreviewKeyEvent true }
                        if (player.isPlaying) player.pause() else player.play()
                        view.showController(); true
                    }
                    Key.MediaPlay -> { player.play(); true }
                    Key.MediaPause -> { player.pause(); view.showController(); true }
                    Key.DirectionLeft, Key.MediaRewind -> { player.seekBack(); seekPreview = player.currentPosition; view.showController(); true }
                    Key.DirectionRight, Key.MediaFastForward -> { player.seekForward(); seekPreview = player.currentPosition; view.showController(); true }
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

        seekPreview?.let { pos ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Column(Modifier.padding(bottom = 130.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    thumb?.let { androidx.compose.foundation.Image(it, null, Modifier.width(260.dp).height(195.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))) }
                    Text(formatTime(pos), style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        val upcoming = next
        val count = countdown
        if (upcoming != null && count != null) {
            Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.BottomEnd) {
                Row(
                    Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(BrandColors.Surface).padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvArtwork(upcoming.imageUrl, Modifier.width(220.dp).height(124.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)))
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.width(380.dp)) {
                        Text("Volgende aflevering over $count s", style = MaterialTheme.typography.titleSmall, color = BrandColors.Amber)
                        Text(upcoming.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
                        Text("OK = nu afspelen · Terug = stoppen", style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted)
                    }
                }
            }
        }
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

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

