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

private val SCREEN_PADDING = PaddingValues(start = 40.dp, end = 48.dp, top = 24.dp, bottom = 48.dp)

// ------------------------------------------------------------------- start

@Composable
fun TvHomeScreen(viewModel: HomeViewModel, continueWatching: List<WatchProgress>, onOpen: (SchatkamerItem) -> Unit, onPlay: (SchatkamerItem) -> Unit) {
    val home by viewModel.home.collectAsState()
    LazyColumn(contentPadding = SCREEN_PADDING, modifier = Modifier.fillMaxSize()) {
        item { Wordmark(Modifier.padding(start = 8.dp, bottom = 8.dp), markSize = 40.dp, fontSize = 30.sp) }
        when (val h = home) {
            Load.Loading -> item { TvLoading() }
            is Load.Failed -> item { TvMessage("Dat lukte even niet", h.message, "Opnieuw", viewModel::refresh) }
            is Load.Ready -> {
                h.data.heroItems.firstOrNull()?.let { hero -> item { TvHero(hero, onOpen) } }
                if (continueWatching.isNotEmpty()) {
                    item {
                        TvRowTitle("Verder kijken")
                        LazyRow(contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            items(continueWatching, key = { it.item.id }) { TvProgramCard(it.item, { onPlay(it.item) }, progress = it.fraction) }
                        }
                    }
                }
                h.data.browseRows.forEach { row -> item(key = row.id) { TvCatalogRow(row, onOpen) } }
            }
        }
        item { Text(UNOFFICIAL_NOTICE, style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted, modifier = Modifier.padding(8.dp, 40.dp)) }
    }
}

@Composable
private fun TvHero(item: SchatkamerItem, onOpen: (SchatkamerItem) -> Unit) {
    Box(Modifier.fillMaxWidth().height(340.dp).padding(8.dp).tvFocusable({ onOpen(item) }, scaleTo = 1.02f)) {
        TvArtwork(item.imageUrl, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to BrandColors.Ink.copy(alpha = 0.95f), 0.6f to BrandColors.Ink.copy(alpha = 0.2f))))
        Column(Modifier.align(Alignment.CenterStart).padding(36.dp).width(520.dp)) {
            Text(if (item.kind == ItemKind.SERIES) "UITGELICHTE SERIE" else "UITGELICHT", style = MaterialTheme.typography.labelSmall, color = BrandColors.Amber)
            Text(item.title, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = item.tvMeta()
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodyLarge, color = BrandColors.TextMuted)
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
    gridState: LazyGridState = rememberLazyGridState()
) {
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 8
        }
    }
    LaunchedEffect(gridState, state.items.size) { snapshotFlow { nearEnd }.collect { if (it) viewModel.loadMore() } }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(260.dp),
        state = gridState,
        contentPadding = SCREEN_PADDING,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { header() }
        if (state.series.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    TvRowTitle("Series")
                    LazyRow(contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        items(state.series, key = { it.id }) { TvSeriesPoster(it, { onOpen(it) }, width = 140.dp) }
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
                items(state.items, key = { it.id }) { TvProgramCard(it, { onOpen(it) }, width = 300.dp, progress = progressOf(it.id)) }
                if (state.isLoadingMore) item(span = { GridItemSpan(maxLineSpan) }) { TvLoading() }
            }
        }
    }
}

@Composable
private fun TvFilters(state: QueryState, viewModel: PagedQueryViewModel) {
    val f = state.filter
    Column {
        LazyRow(contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item { TvPill("Alles", { viewModel.update { it.copy(material = null) } }, f.material == null) }
            item { TvPill("Video", { viewModel.update { it.copy(material = "video") } }, f.material == "video") }
            item { TvPill("Audio", { viewModel.update { it.copy(material = "audio") } }, f.material == "audio") }
            val sorts = if (f.query.isBlank()) listOf(SortOrder.NEWEST, SortOrder.OLDEST) else SortOrder.entries
            items(sorts) { s ->
                TvPill(s.label, { viewModel.update { it.copy(sort = s) } }, f.sort == s || (s == SortOrder.NEWEST && f.query.isBlank() && f.sort == SortOrder.RELEVANCE))
            }
        }
        LazyRow(contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
            Text("Zoeken", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
            OutlinedTextField(
                value = text, onValueChange = viewModel::onTextChange, singleLine = true,
                placeholder = { Text("Programma, persoon, onderwerp of jaar") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.submit() }),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandColors.Amber, cursorColor = BrandColors.Amber),
                modifier = Modifier.padding(horizontal = 8.dp).width(720.dp)
            )
            val chips = suggestions.ifEmpty { if (text.isBlank()) history.ifEmpty { listOf("Polygoon", "Jiskefet", "Swiebertje", "Toppop", "Elfstedentocht") } else emptyList() }
            if (chips.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(8.dp, 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
    isFavorite: Boolean,
    onToggleFavorite: (SchatkamerItem) -> Unit,
    progressOf: (String) -> WatchProgress?,
    onOpen: (SchatkamerItem) -> Unit,
    onPlay: (SchatkamerItem) -> Unit
) {
    val series by viewModel.series.collectAsState()
    val state by viewModel.state.collectAsState()
    val data = (series as? Load.Ready)?.data
    TvResultsGrid(state, viewModel, onOpen, progressOf = { progressOf(it)?.fraction }, header = {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
            if (data != null) TvArtwork(data.portraitUrl.ifBlank { data.imageUrl }, Modifier.width(200.dp).aspectRatio(2f / 3f).padding(end = 28.dp))
            Column(Modifier.weight(1f)) {
                Text("SERIE", style = MaterialTheme.typography.labelSmall, color = BrandColors.Amber)
                Text(data?.title.orEmpty(), style = MaterialTheme.typography.displaySmall)
                val meta = listOfNotNull(data?.broadcaster?.ifBlank { null }, data?.period?.ifBlank { null }).joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodyLarge, color = BrandColors.TextMuted)
                if (!data?.summary.isNullOrBlank()) Text(data!!.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp).width(820.dp))
                Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    state.items.firstOrNull()?.let { first -> TvPill(if (state.filter.sort == SortOrder.OLDEST) "▶  Eerste aflevering" else "▶  Nieuwste aflevering", { onPlay(first) }, selected = true) }
                    if (data != null) TvPill(if (isFavorite) "✓  Bewaard" else "+  Mijn lijst", { onToggleFavorite(data.toItem()) })
                }
            }
        }
    })
}

// --------------------------------------------------------------- uitzending

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TvProgramScreen(
    viewModel: ProgramViewModel,
    isFavorite: Boolean,
    progress: WatchProgress?,
    onToggleFavorite: (SchatkamerItem) -> Unit,
    onPlay: (SchatkamerItem, Boolean) -> Unit,
    onOpen: (SchatkamerItem) -> Unit,
    onBrowse: (BrowseSpec) -> Unit
) {
    val detail by viewModel.detail.collectAsState()
    val more by viewModel.more.collectAsState()
    when (val d = detail) {
        Load.Loading -> TvLoading()
        is Load.Failed -> TvMessage("Dat lukte even niet", d.message, "Opnieuw", viewModel::load)
        is Load.Ready -> {
            val p = d.data
            val item = p.toItem()
            Box(Modifier.fillMaxSize()) {
                TvArtwork(p.imageUrl, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to BrandColors.Ink, 0.55f to BrandColors.Ink.copy(alpha = 0.85f), 1f to BrandColors.Ink.copy(alpha = 0.3f))))
                LazyColumn(contentPadding = SCREEN_PADDING, modifier = Modifier.fillMaxSize()) {
                    item {
                        Column(Modifier.padding(start = 8.dp, top = 40.dp).width(760.dp)) {
                            p.seriesTitle?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelSmall, color = BrandColors.Amber) }
                            Text(p.title, style = MaterialTheme.typography.displaySmall)
                            val meta = listOfNotNull(
                                SchatkamerText.formatDate(p.date).ifBlank { null },
                                p.durationMinutes.takeIf { it > 0 }?.let { "$it min" },
                                p.broadcaster.ifBlank { null }
                            ).joinToString(" · ")
                            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodyLarge, color = BrandColors.TextMuted)
                            Row(Modifier.padding(vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                val resumable = progress != null && !progress.isFinished && progress.positionMs > 15_000
                                TvPill(if (resumable) "▶  Verder kijken" else if (p.isAudio) "▶  Luisteren" else "▶  Afspelen", { onPlay(item, false) }, selected = true)
                                if (resumable) TvPill("Vanaf het begin", { onPlay(item, true) })
                                TvPill(if (isFavorite) "✓  Bewaard" else "+  Mijn lijst", { onToggleFavorite(item) })
                                if (p.seriesId != null) TvPill("Hele serie", { onOpen(SchatkamerItem(p.seriesId!!, p.seriesTitle.orEmpty(), ItemKind.SERIES, seriesId = p.seriesId)) })
                            }
                            if (p.description.isNotBlank()) Text(p.description, style = MaterialTheme.typography.bodyLarge, maxLines = 6, overflow = TextOverflow.Ellipsis)
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
fun TvMyListScreen(favorites: List<SchatkamerItem>, continueWatching: List<WatchProgress>, onOpen: (SchatkamerItem) -> Unit, onPlay: (SchatkamerItem) -> Unit) {
    LazyColumn(contentPadding = SCREEN_PADDING, modifier = Modifier.fillMaxSize()) {
        item { Text("Mijn lijst", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 8.dp)) }
        if (favorites.isEmpty() && continueWatching.isEmpty()) {
            item { TvMessage("Nog niets bewaard", "Kies \"Mijn lijst\" bij een serie of uitzending om hem hier te bewaren.") }
        }
        if (continueWatching.isNotEmpty()) item {
            TvRowTitle("Verder kijken")
            LazyRow(contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                items(continueWatching, key = { it.item.id }) { TvProgramCard(it.item, { onPlay(it.item) }, progress = it.fraction) }
            }
        }
        val series = favorites.filter { it.kind == ItemKind.SERIES }
        if (series.isNotEmpty()) item {
            TvRowTitle("Series")
            LazyRow(contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                items(series, key = { it.id }) { TvSeriesPoster(it, { onOpen(it) }) }
            }
        }
        val programs = favorites.filter { it.kind != ItemKind.SERIES }
        if (programs.isNotEmpty()) item {
            TvRowTitle("Uitzendingen")
            LazyRow(contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                items(programs, key = { it.id }) { TvProgramCard(it, { onOpen(it) }) }
            }
        }
        item { Spacer(Modifier.height(24.dp)); Text(UNOFFICIAL_NOTICE, style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted, modifier = Modifier.padding(8.dp)) }
    }
}
