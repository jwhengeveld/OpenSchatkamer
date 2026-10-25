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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import nl.openschatkamer.brand.BroadcasterLogos
import nl.openschatkamer.brand.forName
import nl.openschatkamer.core.data.SchatkamerProgramDetail
import nl.openschatkamer.core.data.SchatkamerText
import androidx.media3.ui.AspectRatioFrameLayout
import nl.openschatkamer.tv.R
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
 * Hoe het beeld het scherm vult. Veel archiefbeeld is 4:3 met zwarte balken die
 * in de uitzending zelf zitten; inzoomen haalt die weg. De keuze geldt voor alle
 * uitzendingen en blijft bewaard.
 */
private enum class TvZoom(val label: String, val hint: String, val resizeMode: Int, val scale: Float) {
    FIT("Passend", "Het hele beeld, met randen", AspectRatioFrameLayout.RESIZE_MODE_FIT, 1f),
    ZOOM_110("Iets inzoomen", "110%: smalle randen weg", AspectRatioFrameLayout.RESIZE_MODE_FIT, 1.1f),
    ZOOM_125("Inzoomen", "125%: de meeste zwarte randen weg", AspectRatioFrameLayout.RESIZE_MODE_FIT, 1.25f),
    ZOOM_133("4:3 vullend", "133%: oud 4:3-beeld over de hele breedte", AspectRatioFrameLayout.RESIZE_MODE_FIT, 4f / 3f),
    FILL("Scherm vullen", "Bijsnijden tot het scherm vol is", AspectRatioFrameLayout.RESIZE_MODE_ZOOM, 1f),
    STRETCH("Uitrekken", "Het hele scherm, beeld wordt breder", AspectRatioFrameLayout.RESIZE_MODE_FILL, 1f)
}

/**
 * Schaalt het vlak om de video heen (niet de video zelf: een SurfaceView wordt op
 * de rand van zijn ouder afgeknipt). De bediening blijft gewoon staan; de
 * ondertiteling zit in hetzelfde vlak en wordt teruggeschaald, zodat die op zijn
 * plek en even groot blijft.
 */
@OptIn(UnstableApi::class)
private fun PlayerView.applyZoom(zoom: TvZoom) {
    resizeMode = zoom.resizeMode
    val frame = videoSurfaceView?.parent as? android.view.View ?: return
    frame.scaleX = zoom.scale
    frame.scaleY = zoom.scale
    subtitleView?.takeIf { it.parent === frame }?.apply { scaleX = 1f / zoom.scale; scaleY = 1f / zoom.scale }
}

/**
 * Zet een knop voor het beeldformaat in de balk rechtsonder, vóór ondertiteling en
 * instellingen, en neemt het uiterlijk over van de knoppen die er al staan.
 */
@OptIn(UnstableApi::class)
private fun PlayerView.addZoomButton(onClick: () -> Unit) {
    val bar = findViewById<ViewGroup>(androidx.media3.ui.R.id.exo_basic_controls) ?: return
    val reference = findViewById<android.widget.ImageButton>(androidx.media3.ui.R.id.exo_settings)
    val button = android.widget.ImageButton(context).apply {
        id = R.id.tv_zoom_button
        setImageResource(R.drawable.ic_tv_aspect_ratio)
        contentDescription = "Beeldformaat"
        if (reference != null) {
            background = reference.background?.constantState?.newDrawable()
            scaleType = reference.scaleType
            setPadding(reference.paddingLeft, reference.paddingTop, reference.paddingRight, reference.paddingBottom)
            layoutParams = android.widget.LinearLayout.LayoutParams(reference.layoutParams as ViewGroup.MarginLayoutParams)
        }
        isFocusable = false
        setOnClickListener { onClick() }
    }
    bar.addView(button, 0)
}

/**
 * Zet in de middelste rij knoppen voor de vorige en volgende aflevering en voor
 * terug naar het begin: [vorige] [begin] [-10] [afspelen] [+30] [volgende]. Ze
 * nemen het uiterlijk over van de (verborgen) vorige-knop van Media3.
 */
@OptIn(UnstableApi::class)
private fun PlayerView.addEpisodeButtons(onPrevious: () -> Unit, onRestart: () -> Unit, onNext: () -> Unit) {
    val row = findViewById<android.view.View>(androidx.media3.ui.R.id.exo_play_pause)?.parent as? ViewGroup ?: return
    val reference = findViewById<android.widget.ImageButton>(androidx.media3.ui.R.id.exo_prev)
    fun button(viewId: Int, icon: Int, label: String, onClick: () -> Unit) = android.widget.ImageButton(context).apply {
        id = viewId
        setImageResource(icon)
        contentDescription = label
        if (reference != null) {
            background = reference.background?.constantState?.newDrawable()
            scaleType = reference.scaleType
            setPadding(reference.paddingLeft, reference.paddingTop, reference.paddingRight, reference.paddingBottom)
            layoutParams = android.widget.LinearLayout.LayoutParams(reference.layoutParams as ViewGroup.MarginLayoutParams)
        }
        isFocusable = false
        setOnClickListener { onClick() }
    }
    row.addView(button(R.id.tv_previous_episode, R.drawable.ic_tv_previous_episode, "Vorige aflevering", onPrevious), 0)
    row.addView(button(R.id.tv_restart, R.drawable.ic_tv_restart, "Terug naar het begin", onRestart), 1)
    row.addView(button(R.id.tv_next_episode, R.drawable.ic_tv_next_episode, "Volgende aflevering", onNext))
}

/**
 * Legt de focusring over een knop. Sommige knoppen van Media3 (zoals -10 en +30)
 * tekenen hun icoon als voorgrond; die blijft bewaard en komt onder de ring.
 */
private fun android.view.View.showFocusRing(ring: android.graphics.drawable.Drawable?, on: Boolean) {
    @Suppress("UNCHECKED_CAST")
    val saved = (getTag(R.id.tv_saved_foreground) as? Array<android.graphics.drawable.Drawable?>)
        ?: arrayOf(foreground).also { setTag(R.id.tv_saved_foreground, it) }
    val original = saved[0]
    foreground = when {
        !on || ring == null -> original
        original != null -> android.graphics.drawable.LayerDrawable(arrayOf(original, ring))
        else -> ring
    }
}

/** De middelste rij (afspelen, spoelen, afleveringen) en de balk rechtsonder. */
private enum class ControlRow { CENTER, SEEK, BAR }

/** De zichtbare, bruikbare knoppen in een rij van de bediening, van links naar rechts. */
@OptIn(UnstableApi::class)
private fun PlayerView.rowButtons(row: ControlRow): List<android.view.View> {
    val group = when (row) {
        ControlRow.CENTER -> findViewById<android.view.View>(androidx.media3.ui.R.id.exo_play_pause)?.parent as? ViewGroup
        ControlRow.BAR -> findViewById<ViewGroup>(androidx.media3.ui.R.id.exo_basic_controls)
        ControlRow.SEEK -> null
    } ?: return emptyList()
    return (0 until group.childCount).map(group::getChildAt).filter { it.visibility == android.view.View.VISIBLE && it.isEnabled }
}

/**
 * Speler voor de tv: volledig scherm, bediening met de standaardknoppen van
 * Media3. Zonder bediening in beeld: OK = pauze, links/rechts = spoelen. Met de
 * bediening: omhoog naar de middelste knoppen, omlaag naar de knoppen rechtsonder.
 * Terug = bediening tonen, nog een keer terug = sluiten.
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
    var previous by remember { mutableStateOf<SchatkamerItem?>(null) }
    var countdown by remember { mutableStateOf<Int?>(null) }
    var seekPreview by remember { mutableStateOf<Long?>(null) }
    var thumb by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val http = remember { SchatkamerHttpClient.getInstance(context) }
    var isPlaying by remember { mutableStateOf(false) }
    // Bij audio geen zwart scherm: de gegevens van de uitzending voor de achtergrond.
    val audio = item.isAudio || media?.isAudio == true
    var detail by remember { mutableStateOf<SchatkamerProgramDetail?>(null) }
    LaunchedEffect(audio) { if (audio && detail == null) detail = video.getProgramDetail(item.id).getOrNull() }

    // De vorige en volgende aflevering van dezelfde serie: voor de knoppen en automatisch doorspelen.
    LaunchedEffect(media?.seriesId) {
        val seriesId = media?.seriesId ?: return@LaunchedEffect
        val episodes = catalog.episodes(seriesId, sort = SortOrder.OLDEST).getOrNull()?.items.orEmpty()
        val index = episodes.indexOfFirst { it.id == item.id }
        next = if (index >= 0) episodes.getOrNull(index + 1) else null
        previous = if (index > 0) episodes.getOrNull(index - 1) else null
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

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) save()
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
    var controlsVisible by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(error) { if (error == null) runCatching { focus.requestFocus() } }

    val prefs = remember { context.getSharedPreferences("tv_player", android.content.Context.MODE_PRIVATE) }
    var zoom by remember { mutableStateOf(TvZoom.entries.firstOrNull { it.name == prefs.getString("zoom", null) } ?: TvZoom.FIT) }
    var zoomMenu by remember { mutableStateOf(false) }
    // Een SurfaceView slaat een gat in het venster; bij audio zou dat de achtergrond
    // zwart maken. Zonder beeld is het videovlak ook niet nodig.
    LaunchedEffect(audio, playerView.value) {
        playerView.value?.videoSurfaceView?.visibility = if (audio) android.view.View.GONE else android.view.View.VISIBLE
        // Beeldformaat heeft bij audio geen zin.
        playerView.value?.findViewById<android.view.View>(R.id.tv_zoom_button)?.visibility = if (audio) android.view.View.GONE else android.view.View.VISIBLE
    }
    // Afleveringsknoppen alleen als er een vorige of volgende aflevering is.
    LaunchedEffect(previous, next, playerView.value) {
        val view = playerView.value ?: return@LaunchedEffect
        view.findViewById<android.view.View>(R.id.tv_previous_episode)?.visibility = if (previous != null) android.view.View.VISIBLE else android.view.View.GONE
        view.findViewById<android.view.View>(R.id.tv_next_episode)?.visibility = if (next != null) android.view.View.VISIBLE else android.view.View.GONE
    }
    // Waar de focus in de bediening staat. Standaard de voortgangsbalk (links/rechts
    // spoelt); omhoog de middelste knoppen, omlaag de knoppen rechtsonder.
    var controlRow by remember { mutableStateOf(ControlRow.SEEK) }
    var controlIndex by remember { mutableIntStateOf(0) }
    val focusRing = remember { androidx.core.content.ContextCompat.getDrawable(context, R.drawable.tv_control_focus) }
    LaunchedEffect(controlRow, controlIndex, controlsVisible, playerView.value, previous, next) {
        val view = playerView.value ?: return@LaunchedEffect
        listOf(ControlRow.CENTER, ControlRow.BAR).forEach { row ->
            view.rowButtons(row).forEachIndexed { i, b -> b.showFocusRing(focusRing, controlsVisible && row == controlRow && i == controlIndex) }
        }
    }
    LaunchedEffect(zoom, playerView.value) {
        playerView.value?.applyZoom(zoom)
        prefs.edit().putString("zoom", zoom.name).apply()
    }
    androidx.activity.compose.BackHandler(enabled = zoomMenu) { zoomMenu = false }
    // Eerste keer Terug: de bediening laten zien (waar ben je, hoe lang nog), met de
    // melding dat nog een keer stoppen is. Staat de bediening al in beeld, dan valt
    // Terug door naar de BackHandler van MainActivity en sluit de speler. Via een
    // BackHandler en niet als toets: met predictive back komt Terug niet als toets binnen.
    var backHint by remember { mutableIntStateOf(0) }
    var showBackHint by remember { mutableStateOf(false) }
    LaunchedEffect(backHint) { if (backHint > 0) { showBackHint = true; delay(4000); showBackHint = false } }
    androidx.activity.compose.BackHandler(enabled = !zoomMenu && !controlsVisible && countdown == null && error == null) {
        playerView.value?.showController()
        backHint++
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (error != null) return@onPreviewKeyEvent false
                val view = playerView.value ?: return@onPreviewKeyEvent false
                // Het beeldmenu: omhoog/omlaag kiest (en toont het meteen), OK of Terug sluit.
                if (zoomMenu) {
                    if (event.key == Key.Back) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown) when (event.key) {
                        Key.DirectionUp -> zoom = TvZoom.entries[(zoom.ordinal - 1).coerceAtLeast(0)]
                        Key.DirectionDown -> zoom = TvZoom.entries[(zoom.ordinal + 1).coerceAtMost(TvZoom.entries.lastIndex)]
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionLeft, Key.DirectionRight -> zoomMenu = false
                        else -> {}
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent event.key != Key.Back
                // In een rij knoppen: links/rechts kiest, OK drukt, omhoog/omlaag naar de volgende rij.
                if (controlsVisible && controlRow != ControlRow.SEEK && countdown == null) {
                    val buttons = view.rowButtons(controlRow)
                    val handled = when (event.key) {
                        Key.DirectionLeft -> { controlIndex = (controlIndex - 1).coerceAtLeast(0); true }
                        Key.DirectionRight -> { controlIndex = (controlIndex + 1).coerceAtMost(buttons.lastIndex); true }
                        Key.DirectionUp -> { if (controlRow == ControlRow.BAR) controlRow = ControlRow.SEEK; true }
                        Key.DirectionDown -> { if (controlRow == ControlRow.CENTER) controlRow = ControlRow.SEEK; true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { buttons.getOrNull(controlIndex)?.performClick(); true }
                        else -> { controlRow = ControlRow.SEEK; false }
                    }
                    if (handled) { view.showController(); return@onPreviewKeyEvent true }
                }
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
                    Key.DirectionUp -> {
                        // Naar de middelste knoppen, met de focus op afspelen.
                        if (countdown == null) {
                            controlRow = ControlRow.CENTER
                            controlIndex = view.rowButtons(ControlRow.CENTER).indexOfFirst { it.id == androidx.media3.ui.R.id.exo_play_pause }.coerceAtLeast(0)
                        }
                        view.showController(); true
                    }
                    Key.DirectionDown -> {
                        // Bediening al in beeld: naar de knoppen rechtsonder.
                        if (controlsVisible && countdown == null && view.rowButtons(ControlRow.BAR).isNotEmpty()) { controlRow = ControlRow.BAR; controlIndex = 0 }
                        view.showController(); true
                    }
                    else -> false
                }
            }
    ) {
        if (audio) TvAudioBackdrop(item, media, detail, isPlaying)
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    // Doorzichtig, zodat bij audio de achtergrond hierachter zichtbaar is.
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    artworkDisplayMode = PlayerView.ARTWORK_DISPLAY_MODE_OFF
                    this.player = player
                    useController = true
                    setShowSubtitleButton(true)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    controllerShowTimeoutMs = 4000
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
                        controlsVisible = v == android.view.View.VISIBLE
                        if (!controlsVisible) controlRow = ControlRow.SEEK
                    })
                    keepScreenOn = true
                    // De toetsen komen via Compose binnen (zie onPreviewKeyEvent hierboven).
                    isFocusable = false
                    addZoomButton { zoomMenu = true; hideController() }
                    addEpisodeButtons(
                        onPrevious = { previous?.let(onPlayNext) },
                        onRestart = { player.seekTo(0); player.play() },
                        onNext = { next?.let(onPlayNext) }
                    )
                    playerView.value = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Titel bovenin zolang de bediening zichtbaar is.
        // Bij audio staat de titel al groot in de achtergrond.
        androidx.compose.animation.AnimatedVisibility(
            controlsVisible && !audio, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                    .padding(start = 48.dp, end = 48.dp, top = 32.dp, bottom = 40.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    item.seriesTitle?.takeIf { it.isNotBlank() && it != item.title }?.let {
                        Text(it.uppercase(), style = MaterialTheme.typography.labelSmall, color = BrandColors.Amber)
                    }
                    Text(item.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Text(
                    "Beeld: ${zoom.label}", style = MaterialTheme.typography.titleSmall, color = Color.White,
                    modifier = Modifier.padding(start = 24.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            showBackHint && controlsVisible, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp)
        ) {
            Text(
                "Druk nog een keer op Terug om te stoppen", style = MaterialTheme.typography.titleSmall, color = Color.White,
                modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }

        if (zoomMenu) {
            Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.CenterEnd) { ZoomMenu(zoom) }
        }

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

/**
 * Achtergrond bij radio en andere audio, in plaats van een zwart scherm: het beeld
 * van de uitzending wazig en langzaam bewegend, met de hoes, titel, gegevens en
 * omschrijving. Onderin blijft ruimte voor de bediening van de speler.
 */
@Composable
private fun TvAudioBackdrop(item: SchatkamerItem, media: PlayableMedia?, detail: SchatkamerProgramDetail?, playing: Boolean) {
    val image = detail?.imageUrl?.ifBlank { null } ?: media?.posterUrl?.ifBlank { null } ?: item.imageUrl.ifBlank { null }
    val title = detail?.title?.ifBlank { null } ?: media?.title?.ifBlank { null } ?: item.title
    val series = (detail?.seriesTitle ?: media?.subtitle ?: item.seriesTitle)?.takeIf { it.isNotBlank() && it != title }
    val meta = listOfNotNull(
        SchatkamerText.formatDate(detail?.date ?: item.date).ifBlank { null },
        detail?.durationMinutes?.takeIf { it > 0 }?.let { "$it min" },
        (detail?.broadcaster ?: media?.broadcaster ?: item.broadcaster).ifBlank { null }
    ).joinToString(" · ")
    // Heel langzaam in- en uitzoomen: het beeld leeft, zonder af te leiden.
    val drift by rememberInfiniteTransition(label = "achtergrond").animateFloat(
        1.08f, 1.2f, infiniteRepeatable(tween(24_000), RepeatMode.Reverse), label = "zoom"
    )
    Box(Modifier.fillMaxSize().background(BrandColors.Ink)) {
        if (image != null) AsyncImage(
            image, null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = drift; scaleY = drift; alpha = 0.5f }.blur(40.dp)
        ) else Box(
            // Zonder beeld (vaak bij radio): een zachte, langzaam bewegende gloed.
            Modifier.fillMaxSize().graphicsLayer { scaleX = drift; scaleY = drift }.background(
                Brush.radialGradient(listOf(BrandColors.Audio.copy(alpha = 0.30f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(400f, 300f), radius = 1300f)
            ).background(
                Brush.radialGradient(listOf(BrandColors.Amber.copy(alpha = 0.14f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(1900f, 1100f), radius = 1100f)
            )
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to BrandColors.Ink.copy(alpha = 0.92f), 0.6f to BrandColors.Ink.copy(alpha = 0.72f), 1f to BrandColors.Ink.copy(alpha = 0.5f))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to BrandColors.Ink)))
        Row(Modifier.fillMaxSize().padding(start = 96.dp, end = 96.dp, bottom = 120.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(340.dp).aspectRatio(1f).clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))) {
                if (image != null) TvArtwork(image, Modifier.fillMaxSize(), audio = true)
                else AudioCover(series ?: title, detail?.broadcaster ?: media?.broadcaster ?: item.broadcaster)
                Icon(
                    Icons.Rounded.Headphones, null, tint = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).clip(CircleShape).background(BrandColors.Audio).padding(10.dp).size(26.dp)
                )
            }
            Spacer(Modifier.width(56.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull("LUISTEREN", series?.uppercase()).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall, color = BrandColors.Audio, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(title, style = MaterialTheme.typography.displaySmall, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodyLarge, color = BrandColors.TextMuted, modifier = Modifier.padding(top = 6.dp))
                AudioBars(playing, Modifier.padding(top = 22.dp))
                detail?.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = BrandColors.Text, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 22.dp).width(720.dp))
                }
            }
        }
    }
}

/** Hoes voor audio zonder beeld: het omroeplogo (of een koptelefoon) met de naam van de serie. */
@Composable
private fun AudioCover(name: String, broadcaster: String) {
    val logo = BroadcasterLogos.forName(broadcaster)
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(BrandColors.Audio.copy(alpha = 0.45f), BrandColors.Surface))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
            if (logo != null) androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(logo), broadcaster, Modifier.width(190.dp).height(96.dp), contentScale = ContentScale.Fit
            ) else Icon(Icons.Rounded.Headphones, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(110.dp))
            Text(
                name, style = MaterialTheme.typography.headlineSmall, color = Color.White, textAlign = TextAlign.Center,
                maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 20.dp)
            )
        }
    }
}

/** Balkjes die bewegen zolang er geluid speelt, en stilstaan bij pauze. */
@Composable
private fun AudioBars(playing: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "balkjes")
    Row(modifier.height(30.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
        listOf(520, 380, 640, 450, 580).forEachIndexed { i, ms ->
            val level by transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(ms), RepeatMode.Reverse), label = "balk$i")
            Box(
                Modifier.width(6.dp).fillMaxHeight(if (playing) level else 0.25f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp)).background(BrandColors.Audio)
            )
        }
    }
}

/** Keuzelijst voor het beeldformaat; de keuze is meteen te zien in het beeld erachter. */
@Composable
private fun ZoomMenu(current: TvZoom) {
    Column(
        Modifier.width(380.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(BrandColors.Surface.copy(alpha = 0.94f)).padding(vertical = 18.dp)
    ) {
        Text("BEELD", style = MaterialTheme.typography.labelSmall, color = BrandColors.Amber, modifier = Modifier.padding(start = 22.dp, bottom = 8.dp))
        TvZoom.entries.forEach { z ->
            val selected = z == current
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                    .background(if (selected) BrandColors.Amber else Color.Transparent).padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(z.label, style = MaterialTheme.typography.titleSmall, color = if (selected) BrandColors.Ink else BrandColors.Text)
                Text(z.hint, style = MaterialTheme.typography.bodySmall, color = if (selected) BrandColors.Ink.copy(alpha = 0.75f) else BrandColors.TextMuted)
            }
        }
        Text("▲▼ kiezen · OK of Terug = klaar", style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted, modifier = Modifier.padding(start = 22.dp, top = 10.dp))
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

