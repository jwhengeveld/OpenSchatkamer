@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package nl.openschatkamer.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.sp
import nl.openschatkamer.brand.BrandColors
import nl.openschatkamer.brand.UNOFFICIAL_NOTICE
import nl.openschatkamer.brand.Wordmark
import nl.openschatkamer.core.data.Decade
import nl.openschatkamer.core.data.ItemKind
import nl.openschatkamer.core.data.SchatkamerItem
import nl.openschatkamer.core.data.SchatkamerText
import nl.openschatkamer.core.data.SortOrder
import nl.openschatkamer.core.data.WatchProgress
import nl.openschatkamer.core.ui.BrowseSpec
import nl.openschatkamer.core.ui.DiscoverViewModel
import nl.openschatkamer.core.ui.HomeViewModel
import nl.openschatkamer.core.ui.Load
import nl.openschatkamer.core.ui.PagedQueryViewModel
import nl.openschatkamer.core.ui.ProgramViewModel
import nl.openschatkamer.core.ui.QueryState
import nl.openschatkamer.core.ui.SearchViewModel
import nl.openschatkamer.core.ui.SeriesViewModel
import nl.openschatkamer.core.ui.StoryViewModel
import nl.openschatkamer.core.ui.TimeTravelViewModel
import nl.openschatkamer.core.ui.ListViewModel
import nl.openschatkamer.core.ui.AccountViewModel
import nl.openschatkamer.core.data.UserLibrary
import nl.openschatkamer.core.data.AnyList
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRestorer

private val SCREEN_PADDING = PaddingValues(start = 32.dp, end = 40.dp, top = 20.dp, bottom = 40.dp)

// ------------------------------------------------------------------- start

@Composable
fun TvHomeScreen(
    viewModel: HomeViewModel,
    continueWatching: List<WatchProgress>,
    timeTravel: List<TimeTravelViewModel.Row>,
    onOpen: (SchatkamerItem) -> Unit,
    onPlay: (SchatkamerItem) -> Unit,
    onBrowse: (BrowseSpec) -> Unit
) {
    val home by viewModel.home.collectAsState()
    // De kaart met focus kleurt de achtergrond, zoals op Google TV.
    var focused by remember { mutableStateOf<SchatkamerItem?>(null) }
    Box(Modifier.fillMaxSize()) {
        androidx.compose.animation.Crossfade(focused?.imageUrl, label = "achtergrond", modifier = Modifier.fillMaxWidth().fillMaxHeight(0.62f).align(Alignment.TopEnd)) { url ->
            Box(Modifier.fillMaxSize()) {
                TvArtwork(url, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to BrandColors.Ink, 0.55f to BrandColors.Ink.copy(alpha = 0.6f), 1f to Color.Transparent)))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to BrandColors.Ink)))
            }
        }
        CompositionLocalProvider(LocalTvFocusSink provides { focused = it }) {
          Column(Modifier.fillMaxSize()) {
            // Vaste kop, zoals op Google TV: wat je geselecteerd hebt staat bovenaan,
            // de rijen schuiven eronder door. Zo valt er nooit iets boven de rand.
            Column(Modifier.padding(start = 40.dp, top = 24.dp, end = 40.dp).fillMaxWidth().height(156.dp)) {
                Wordmark(markSize = 26.dp, fontSize = 20.sp, platformSuffix = "TV")
                focused?.let { f ->
                    Text(
                        when (f.kind) { ItemKind.SERIES -> "SERIE"; ItemKind.STORY -> "VERHAAL"; ItemKind.PERSON -> "PERSOON"; else -> f.seriesTitle?.uppercase().orEmpty() },
                        style = MaterialTheme.typography.labelSmall, color = BrandColors.Amber, maxLines = 1, modifier = Modifier.padding(top = 14.dp)
                    )
                    Text(f.title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(760.dp))
                    val meta = f.tvMeta()
                    if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodyMedium, color = BrandColors.TextMuted, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                }
            }
            LazyColumn(contentPadding = PaddingValues(start = 32.dp, end = 40.dp, bottom = 40.dp), modifier = Modifier.fillMaxSize().focusRestorer()) {
                when (val h = home) {
                    Load.Loading -> item { TvLoading() }
                    is Load.Failed -> item { TvMessage("Dat lukte even niet", h.message, "Opnieuw", viewModel::refresh) }
                    is Load.Ready -> {
                        if (continueWatching.isNotEmpty()) {
                            item {
                                TvRowTitle("Verder kijken")
                                LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                    items(continueWatching, key = { it.item.id }) { TvProgramCard(it.item, { onPlay(it.item) }, progress = it.fraction) }
                                }
                            }
                        }
                        h.data.browseRows.forEachIndexed { index, row ->
                            item(key = row.id) { TvCatalogRow(row, onOpen) }
                            if (index == 1) timeTravel.forEach { t ->
                                item(key = "tt${t.yearsAgo}") {
                                    TvRowTitle(t.spec.title)
                                    LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                        items(t.items, key = { it.id }) { TvProgramCard(it, { onOpen(it) }) }
                                        item { TvPill("Meer…", { onBrowse(t.spec) }) }
                                    }
                                }
                            }
                        }
                    }
                }
                item { Text(UNOFFICIAL_NOTICE, style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted, modifier = Modifier.padding(8.dp, 40.dp)) }
            }
          }
        }
    }
}

// --------------------------------------------------------- lijsten (raster)

/** Raster van uitzendingen met oneindig scrollen, voor zoeken, bladeren en series. */
@Composable
private fun TvResultsGrid(
    state: QueryState,
    viewModel: PagedQueryViewModel,
    onOpen: (SchatkamerItem) -> Unit,
    header: @Composable () -> Unit,
    progressOf: (String) -> Float? = { null },
    gridState: LazyGridState = rememberLazyGridState(),
    showSeries: Boolean = true
) {
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 8
        }
    }
    LaunchedEffect(gridState, state.items.size) { snapshotFlow { nearEnd }.collect { if (it) viewModel.loadMore() } }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(200.dp),
        state = gridState,
        contentPadding = SCREEN_PADDING,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            // Focus in de kop (zoekveld, knoppen): helemaal naar boven, zodat titel en veld zichtbaar zijn.
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            Box(Modifier.onFocusChanged { if (it.hasFocus) scope.launch { gridState.animateScrollToItem(0) } }) { header() }
        }
        if (showSeries && state.series.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    TvRowTitle("Series")
                    LazyRow(contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        items(state.series, key = { it.id }) { TvSeriesPoster(it, { onOpen(it) }, width = 120.dp) }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { TvFilters(state, viewModel) }
        when {
            state.isLoading -> item(span = { GridItemSpan(maxLineSpan) }) { TvLoading() }
            state.error != null -> item(span = { GridItemSpan(maxLineSpan) }) { TvMessage("Dat lukte even niet", state.error!!, "Opnieuw", viewModel::retry) }
            state.loaded && state.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) { TvMessage("Niets gevonden", "Probeer een andere zoekterm of minder filters.") }
            else -> {
                items(state.items, key = { it.id }) { TvProgramCard(it, { onOpen(it) }, width = androidx.compose.ui.unit.Dp.Unspecified, progress = progressOf(it.id)) }
                if (state.isLoadingMore) item(span = { GridItemSpan(maxLineSpan) }) { TvLoading() }
            }
        }
    }
}

@Composable
private fun TvFilters(state: QueryState, viewModel: PagedQueryViewModel) {
    val f = state.filter
    Column {
        // Alle filters op één rij: soort, volgorde en periode.
        LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item { TvPill("Alles", { viewModel.update { it.copy(material = null) } }, f.material == null) }
            item { TvPill("Video", { viewModel.update { it.copy(material = "video") } }, f.material == "video") }
            item { TvPill("Audio", { viewModel.update { it.copy(material = "audio") } }, f.material == "audio") }
            val sorts = if (f.query.isBlank()) listOf(SortOrder.NEWEST, SortOrder.OLDEST) else SortOrder.entries
            items(sorts) { s ->
                TvPill(s.label, { viewModel.update { it.copy(sort = s) } }, f.sort == s || (s == SortOrder.NEWEST && f.query.isBlank() && f.sort == SortOrder.RELEVANCE))
            }
            item { Box(Modifier.padding(horizontal = 6.dp).width(2.dp).height(28.dp).background(BrandColors.Outline)) }
            item { TvPill("Alle jaren", { viewModel.update { it.copy(decade = null) } }, f.decade == null) }
            items(Decade.entries.reversed()) { d -> TvPill(d.label, { viewModel.update { it.copy(decade = d) } }, f.decade == d) }
        }
        if (state.loaded) {
            Text(
                "${state.total.coerceAtMost(10_000)}${if (state.total >= 10_000) "+" else ""} uitzendingen",
                style = MaterialTheme.typography.bodyMedium, color = BrandColors.TextMuted, modifier = Modifier.padding(start = 8.dp, top = 4.dp)
            )
        }
    }
}

// ------------------------------------------------------------------ zoeken

@Composable
fun TvSearchScreen(viewModel: SearchViewModel, onOpen: (SchatkamerItem) -> Unit) {
    val state by viewModel.state.collectAsState()
    val text by viewModel.text.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val history by viewModel.history.collectAsState()

    TvResultsGrid(state, viewModel, onOpen, header = {
        Column {
            Text("Zoeken", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 8.dp, bottom = 10.dp))
            TvSearchField(text, viewModel::onTextChange, onSearch = { viewModel.submit() }, modifier = Modifier.padding(horizontal = 8.dp).width(720.dp))
            val chips = suggestions.ifEmpty { if (text.isBlank()) history.ifEmpty { listOf("Polygoon", "Jiskefet", "Swiebertje", "Toppop", "Elfstedentocht") } else emptyList() }
            if (chips.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(8.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(chips) { TvPill(it, { viewModel.submit(it) }) }
                }
            }
        }
    })
}

// ---------------------------------------------------------------- bladeren

@Composable
fun TvBrowseScreen(viewModel: PagedQueryViewModel, spec: BrowseSpec, onOpen: (SchatkamerItem) -> Unit) {
    val state by viewModel.state.collectAsState()
    TvResultsGrid(state, viewModel, onOpen, header = {
        Column(Modifier.padding(start = 8.dp)) {
            Text(
                when {
                    spec.person != null -> "PERSOON"
                    spec.broadcaster != null -> "OMROEP"
                    spec.genre != null -> "GENRE"
                    spec.decade != null -> "PERIODE"
                    else -> "ONDERWERP"
                }, style = MaterialTheme.typography.labelSmall, color = BrandColors.Amber
            )
            Text(spec.title, style = MaterialTheme.typography.headlineMedium)
        }
    })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TvDiscoverScreen(viewModel: DiscoverViewModel, onBrowse: (BrowseSpec) -> Unit) {
    val data by viewModel.data.collectAsState()
    LazyColumn(contentPadding = SCREEN_PADDING, modifier = Modifier.fillMaxSize()) {
        item { Text("Ontdekken", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 8.dp)) }
        item {
            TvRowTitle("Door de tijd")
            LazyRow(contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(Decade.entries.reversed()) { d -> TvPill(d.label, { onBrowse(BrowseSpec(d.label, decade = d)) }) }
            }
        }
        item {
            TvRowTitle("Omroepen")
            LazyRow(contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                items(data.broadcasters, key = { it.name }) { b ->
                    TvBroadcasterTile(b.name, b.logoUrl, { onBrowse(BrowseSpec(b.name, broadcaster = b.name)) },
                        count = if (b.count > 0) "${if (b.count >= 10_000) "10.000+" else b.count.toString()} uitzendingen" else null)
                }
            }
        }
        if (data.people.isNotEmpty()) item {
            TvRowTitle("Personen")
            LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                items(data.people, key = { it.id }) { p -> TvPersonCard(p) { onBrowse(BrowseSpec(p.title, person = p.title)) } }
            }
        }
        if (data.subjects.isNotEmpty()) item {
            TvRowTitle("Onderwerpen")
            FlowRow(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                data.subjects.take(24).forEach { sub ->
                    val title = sub.label.replaceFirstChar { it.titlecase() }
                    TvPill(title, { onBrowse(BrowseSpec(title, subject = sub.label)) })
                }
            }
        }
        if (data.genres.isNotEmpty()) {
            item {
                TvRowTitle("Genres")
                FlowRow(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    data.genres.take(30).forEach { g ->
                        val title = g.label.replaceFirstChar { it.titlecase() }
                        TvPill(title, { onBrowse(BrowseSpec(title, genre = g.label)) })
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------- serie

@Composable
fun TvSeriesScreen(
    viewModel: SeriesViewModel,
    seriesId: String,
    library: UserLibrary,
    onOpen: (SchatkamerItem) -> Unit,
    onPlay: (SchatkamerItem) -> Unit
) {
    val series by viewModel.series.collectAsState()
    val state by viewModel.state.collectAsState()
    val data = (series as? Load.Ready)?.data
    library.series.collectAsState().value
    val following = library.isFollowing(seriesId)
    LaunchedEffect(state.total) { if (state.total > 0) library.local.markSeriesSeen(seriesId, state.total) }
    // Brede achtergrondfoto achter de kop, zoals bij een uitzending; een staande
    // uitsnede van een liggende foto zag er vreemd uit.
    Box(Modifier.fillMaxSize()) {
        if (data != null) {
            TvArtwork(data.imageUrl.ifBlank { data.portraitUrl }, Modifier.fillMaxWidth().fillMaxHeight(0.7f).align(Alignment.TopEnd))
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.7f).align(Alignment.TopEnd).background(Brush.horizontalGradient(0f to BrandColors.Ink, 0.5f to BrandColors.Ink.copy(alpha = 0.8f), 1f to BrandColors.Ink.copy(alpha = 0.25f))))
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.7f).align(Alignment.TopEnd).background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to BrandColors.Ink)))
        }
        TvResultsGrid(state, viewModel, onOpen, showSeries = false, header = {
            Column(Modifier.padding(start = 8.dp, top = 28.dp).width(820.dp)) {
                Text("SERIE", style = MaterialTheme.typography.labelSmall, color = BrandColors.Amber)
                Text(data?.title.orEmpty(), style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(
                    data?.broadcaster?.ifBlank { null }, data?.period?.ifBlank { null },
                    state.total.takeIf { it > 0 }?.let { "$it afleveringen" }
                ).joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodyLarge, color = BrandColors.TextMuted)
                if (!data?.summary.isNullOrBlank()) Text(data!!.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                Row(Modifier.padding(top = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.items.firstOrNull()?.let { first -> TvPill(if (state.filter.sort == SortOrder.OLDEST) "▶  Eerste aflevering" else "▶  Nieuwste aflevering", { onPlay(first) }, selected = true) }
                    if (data != null) TvPill(if (following) "✓  Volgend" else "+  Volgen", { library.toggleFollow(data.toItem(), state.total) })
                    TvPill(if (state.filter.sort == SortOrder.OLDEST) "Oudste eerst" else "Nieuwste eerst", viewModel::toggleSort)
                }
            }
        })
    }
}

// --------------------------------------------------------------- uitzending

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TvProgramScreen(
    viewModel: ProgramViewModel,
    library: UserLibrary,
    onAddToList: (SchatkamerItem) -> Unit,
    onPlay: (SchatkamerItem, Boolean) -> Unit,
    onOpen: (SchatkamerItem) -> Unit,
    onBrowse: (BrowseSpec) -> Unit
) {
    val detail by viewModel.detail.collectAsState()
    val more by viewModel.more.collectAsState()
    val history by library.local.history.collectAsState()
    library.favorites.collectAsState().value
    library.lists.collectAsState().value
    when (val d = detail) {
        Load.Loading -> TvLoading()
        is Load.Failed -> TvMessage("Dat lukte even niet", d.message, "Opnieuw", viewModel::load)
        is Load.Ready -> {
            val p = d.data
            val item = p.toItem()
            val progress = history.firstOrNull { it.item.id == p.id }
            val isFavorite = library.isFavorite(item)
            Box(Modifier.fillMaxSize()) {
                TvArtwork(p.imageUrl, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to BrandColors.Ink, 0.55f to BrandColors.Ink.copy(alpha = 0.85f), 1f to BrandColors.Ink.copy(alpha = 0.3f))))
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                LazyColumn(state = listState, contentPadding = SCREEN_PADDING, modifier = Modifier.fillMaxSize()) {
                    item {
                        Column(Modifier.padding(start = 8.dp, top = 28.dp).width(820.dp)) {
                            p.seriesTitle?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelSmall, color = BrandColors.Amber) }
                            Text(p.title, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val meta = listOfNotNull(
                                SchatkamerText.formatDate(p.date).ifBlank { null },
                                p.durationMinutes.takeIf { it > 0 }?.let { "$it min" },
                                p.broadcaster.ifBlank { null }
                            ).joinToString(" · ")
                            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodyLarge, color = BrandColors.TextMuted)
                            Row(
                                // De knoppen bovenaan: scroll terug naar de titel.
                                Modifier.padding(vertical = 16.dp).onFocusChanged { if (it.hasFocus) scope.launch { listState.animateScrollToItem(0) } },
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val resumable = progress != null && !progress.isFinished && progress.positionMs > 15_000
                                TvPill(if (resumable) "▶  Verder kijken" else if (p.isAudio) "▶  Luisteren" else "▶  Afspelen", { onPlay(item, false) }, selected = true)
                                if (resumable) TvPill("Vanaf het begin", { onPlay(item, true) })
                                TvPill(if (isFavorite) "✓  Bewaard" else "+  Mijn lijst", { library.toggleFavorite(item) })
                                TvPill("Lijsten…", { onAddToList(item) })
                                val watched = progress?.isFinished == true
                                TvPill(if (watched) "✓  Gezien" else "Gezien", { library.local.setWatched(item, !watched) }, selected = false)
                                if (p.seriesId != null) TvPill("Hele serie", { onOpen(SchatkamerItem(p.seriesId!!, p.seriesTitle.orEmpty(), ItemKind.SERIES, seriesId = p.seriesId)) })
                            }
                            if (p.description.isNotBlank()) Text(p.description, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)
                            if (p.credits.isNotEmpty()) {
                                TvRowTitle("Wie zie of hoor je")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    p.credits.take(10).forEach { c -> TvPill(c.name, { onBrowse(BrowseSpec(c.name, person = c.name)) }) }
                                }
                            }
                        }
                    }
                    if (more.isNotEmpty()) {
                        item {
                            TvRowTitle("Meer uit ${p.seriesTitle ?: "deze serie"}")
                            LazyRow(contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                items(more, key = { it.id }) { TvProgramCard(it, { onOpen(it) }) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ verhaal

@Composable
fun TvStoryScreen(viewModel: StoryViewModel, onOpen: (SchatkamerItem) -> Unit) {
    val story by viewModel.story.collectAsState()
    when (val s = story) {
        Load.Loading -> TvLoading()
        is Load.Failed -> TvMessage("Dat lukte even niet", s.message, "Opnieuw", viewModel::load)
        is Load.Ready -> LazyVerticalGrid(
            columns = GridCells.Adaptive(300.dp), contentPadding = SCREEN_PADDING,
            horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(8.dp).width(900.dp)) {
                    Text("VERHAAL", style = MaterialTheme.typography.labelSmall, color = BrandColors.Amber)
                    Text(s.data.title, style = MaterialTheme.typography.displaySmall)
                    if (s.data.description.isNotBlank()) Text(s.data.description, style = MaterialTheme.typography.bodyLarge, color = BrandColors.TextMuted, modifier = Modifier.padding(top = 8.dp))
                }
            }
            items(s.data.programs, key = { it.id }) { TvProgramCard(it, { onOpen(it) }, width = 300.dp) }
        }
    }
}

// --------------------------------------------------------------- mijn lijst

@Composable
fun TvMyListScreen(
    library: UserLibrary,
    onOpen: (SchatkamerItem) -> Unit,
    onPlay: (SchatkamerItem) -> Unit,
    onOpenList: (AnyList) -> Unit,
    onAccount: () -> Unit
) {
    val account by library.account.collectAsState()
    val favorites by library.favorites.collectAsState()
    val continueWatching by library.local.continueWatching.collectAsState()
    val series by library.series.collectAsState()
    val lists by library.lists.collectAsState()
    val history by library.local.history.collectAsState()
    val programs = favorites.filter { it.kind != ItemKind.SERIES }
    LazyColumn(contentPadding = SCREEN_PADDING, modifier = Modifier.fillMaxSize().focusRestorer()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                Text("Mijn lijst", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                TvPill(account?.let { "Ingelogd: ${it.name}" } ?: "Inloggen met Schatkamer-account", onAccount, selected = account == null)
            }
        }
        if (programs.isEmpty() && continueWatching.isEmpty() && series.isEmpty() && lists.isEmpty()) {
            item { TvMessage("Nog niets bewaard", "Kies \"Mijn lijst\" of \"Volgen\" bij een uitzending of serie. Houd OK ingedrukt op een kaart voor meer opties.") }
        }
        if (continueWatching.isNotEmpty()) item {
            TvRowTitle("Verder kijken")
            LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                items(continueWatching, key = { it.item.id }) { TvProgramCard(it.item, { onPlay(it.item) }, progress = it.fraction) }
            }
        }
        if (series.isNotEmpty()) item {
            TvRowTitle(if (account != null) "Mijn series" else "Gevolgde series")
            LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                items(series, key = { it.item.id }) { f ->
                    Box {
                        TvSeriesPoster(f.item, { onOpen(f.item) })
                        if (f.newEpisodes > 0) Text(
                            "+${f.newEpisodes}", color = BrandColors.Ink, style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(BrandColors.Amber).padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
        if (programs.isNotEmpty()) item {
            TvRowTitle(if (account != null) "Op dit toestel bewaard" else "Bewaarde uitzendingen")
            LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                items(programs, key = { it.id }) { TvProgramCard(it, { onOpen(it) }) }
            }
        }
        if (lists.isNotEmpty()) item {
            TvRowTitle("Lijsten")
            LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                items(lists, key = { it.key }) { list ->
                    Column(Modifier.width(280.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).tvFocusable({ onOpenList(list) }).background(BrandColors.SurfaceHigh), contentAlignment = Alignment.Center) {
                            if (list.thumbnailUrl.isNotBlank()) TvArtwork(list.thumbnailUrl, Modifier.fillMaxSize())
                            Text(if (list.online) "☁" else "", modifier = Modifier.align(Alignment.TopEnd).padding(8.dp), color = Color.White)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(list.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text("${list.count} uitzendingen" + if (list.online) " · online" else "", style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted)
                    }
                }
            }
        }
        val watched = history.filter { it.isFinished }
        if (watched.isNotEmpty()) item {
            TvRowTitle("Bekeken")
            LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                items(watched.take(30), key = { it.item.id }) { TvProgramCard(it.item, { onOpen(it.item) }) }
            }
        }
        item { Spacer(Modifier.height(24.dp)); Text(UNOFFICIAL_NOTICE, style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted, modifier = Modifier.padding(8.dp)) }
    }
}

// ------------------------------------------------------------------- lijst

@Composable
fun TvListScreen(viewModel: ListViewModel?, localItems: List<SchatkamerItem>, title: String, onOpen: (SchatkamerItem) -> Unit, onPlay: (SchatkamerItem) -> Unit) {
    val list = viewModel?.list?.collectAsState()?.value
    val state = viewModel?.items?.collectAsState()?.value ?: Load.Ready(localItems)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(300.dp), contentPadding = SCREEN_PADDING,
        horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(28.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(8.dp)) {
                Text(if (list?.online == true) "ONLINE LIJST" else "LIJST", style = MaterialTheme.typography.labelSmall, color = BrandColors.Amber)
                Text(list?.name ?: title, style = MaterialTheme.typography.headlineMedium)
                val items = (state as? Load.Ready)?.data.orEmpty()
                if (items.isNotEmpty()) Row(Modifier.padding(top = 14.dp)) { TvPill("▶  Alles afspelen", { onPlay(items.first()) }, selected = true) }
            }
        }
        when (state) {
            Load.Loading -> item(span = { GridItemSpan(maxLineSpan) }) { TvLoading() }
            is Load.Failed -> item(span = { GridItemSpan(maxLineSpan) }) { TvMessage("Dat lukte even niet", state.message, "Opnieuw", { viewModel?.reload() }) }
            is Load.Ready -> if (state.data.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                TvMessage("Deze lijst is leeg", "Kies \"Lijsten…\" bij een uitzending om hem toe te voegen.")
            } else items(state.data, key = { it.id }) { TvProgramCard(it, { onOpen(it) }, width = 300.dp) }
        }
    }
}

// --------------------------------------------------------------- account

@Composable
fun TvAccountScreen(viewModel: AccountViewModel, onBack: () -> Unit) {
    val user by viewModel.user.collectAsState()
    val state by viewModel.state.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val fieldColors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandColors.Amber, cursorColor = BrandColors.Amber)
    LazyColumn(contentPadding = SCREEN_PADDING, modifier = Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(8.dp).width(640.dp)) {
                Text("Je Schatkamer-account", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                val signedIn = user
                when {
                    signedIn != null -> {
                        Text("Ingelogd als ${signedIn.name} (${signedIn.email})", style = MaterialTheme.typography.bodyLarge)
                        Text("Je lijsten, favorieten en Mijn series staan in je account.", style = MaterialTheme.typography.bodyMedium, color = BrandColors.TextMuted)
                        Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            TvPill("Terug", onBack, selected = true)
                            TvPill("Uitloggen", viewModel::signOut)
                        }
                    }
                    state.pendingCode != null -> {
                        Text(state.pendingCode!!.hint, style = MaterialTheme.typography.bodyLarge)
                        TvInputField(
                            code, { code = it.filter(Char::isDigit).take(8) }, "Code", Modifier.padding(top = 12.dp).width(420.dp),
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword, onSubmit = { viewModel.confirmCode(code) }
                        )
                        state.error?.let { Text(it, color = BrandColors.Danger, modifier = Modifier.padding(top = 8.dp)) }
                        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            TvPill("Bevestigen", { viewModel.confirmCode(code) }, selected = true)
                            TvPill("Annuleren", viewModel::cancelCode)
                        }
                    }
                    else -> {
                        Text(
                            "Log in met het account dat je op schatkamer.beeldengeluid.nl hebt gemaakt. Zonder account werkt alles ook, alleen op deze tv.",
                            style = MaterialTheme.typography.bodyMedium, color = BrandColors.TextMuted
                        )
                        TvInputField(
                            email, { email = it.trim() }, "Kies om je e-mailadres in te voeren", Modifier.padding(top = 16.dp).width(560.dp),
                            label = "E-MAILADRES", keyboardType = androidx.compose.ui.text.input.KeyboardType.Email
                        )
                        TvInputField(
                            password, { password = it }, "Kies om je wachtwoord in te voeren", Modifier.padding(top = 12.dp).width(560.dp),
                            label = "WACHTWOORD", password = true, keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                        )
                        state.error?.let { Text(it, color = BrandColors.Danger, modifier = Modifier.padding(top = 10.dp)) }
                        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            TvPill(if (state.busy) "Bezig…" else "Inloggen", { viewModel.signIn(email, password) }, selected = true)
                            TvPill("Terug", onBack)
                        }
                        Text("Je wachtwoord gaat versleuteld naar De Schatkamer en wordt niet bewaard.", style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted, modifier = Modifier.padding(top = 14.dp))
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------- acties / lijsten

/** Dialoog bij lang OK: bewaren, lijsten, gezien, naar de serie. */
@Composable
fun TvActionsDialog(item: SchatkamerItem, library: UserLibrary, onDismiss: () -> Unit, onOpenSeries: (SchatkamerItem) -> Unit) {
    library.favorites.collectAsState().value
    val lists by library.lists.collectAsState()
    library.local.history.collectAsState().value
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(16.dp)).background(BrandColors.Surface).padding(28.dp).width(560.dp)) {
            Text(item.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(18.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val saved = library.isFavorite(item)
                TvPill(
                    when { item.kind == ItemKind.SERIES && saved -> "✓  Volgend"; item.kind == ItemKind.SERIES -> "+  Volgen"; saved -> "✓  In Mijn lijst"; else -> "+  Mijn lijst" },
                    { library.toggleFavorite(item) }, selected = true
                )
                if (item.kind == ItemKind.PROGRAM) {
                    val watched = library.local.isWatched(item.id)
                    TvPill(if (watched) "Niet gezien" else "Gezien", { library.local.setWatched(item, !watched) })
                    if (!item.seriesId.isNullOrBlank()) TvPill("Naar de serie", {
                        onDismiss(); onOpenSeries(SchatkamerItem(item.seriesId!!, item.seriesTitle.orEmpty(), ItemKind.SERIES, seriesId = item.seriesId))
                    })
                }
            }
            if (item.kind == ItemKind.PROGRAM) {
                TvRowTitle("Lijsten")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    lists.forEach { list ->
                        val inList = library.isInList(list, item)
                        TvPill((if (inList) "✓  " else "") + list.name + if (list.online) " ☁" else "", { library.toggleInList(list, item) }, selected = inList)
                    }
                    TvPill("+  Nieuwe lijst \"Bewaard op tv\"", { library.createList("Bewaard op tv", onlineList = library.online.isSignedIn, first = item) })
                }
            }
            Row(Modifier.padding(top = 22.dp)) { TvPill("Sluiten", onDismiss) }
        }
    }
}
