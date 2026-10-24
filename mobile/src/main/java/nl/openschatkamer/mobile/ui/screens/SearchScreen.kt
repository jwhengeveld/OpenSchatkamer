package nl.openschatkamer.mobile.ui.screens

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import nl.openschatkamer.core.data.SchatkamerText
import nl.openschatkamer.core.ui.BrowseSpec
import nl.openschatkamer.mobile.ui.components.Pill
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import nl.openschatkamer.brand.BrandColors
import nl.openschatkamer.core.data.SchatkamerItem
import nl.openschatkamer.core.ui.QueryState
import nl.openschatkamer.core.ui.SearchViewModel
import nl.openschatkamer.mobile.ui.components.EmptyState
import nl.openschatkamer.mobile.ui.components.ErrorState
import nl.openschatkamer.mobile.ui.components.FilterBar
import nl.openschatkamer.mobile.ui.components.LoadingFooter
import nl.openschatkamer.mobile.ui.components.ProgramListItem
import nl.openschatkamer.mobile.ui.components.LocalListColumns
import nl.openschatkamer.mobile.ui.components.adaptiveItems
import nl.openschatkamer.mobile.ui.components.ScreenTitle
import nl.openschatkamer.mobile.ui.components.SectionHeader
import nl.openschatkamer.mobile.ui.components.SeriesPoster
import nl.openschatkamer.mobile.ui.components.SkeletonList
import nl.openschatkamer.mobile.ui.components.countLabel

private enum class SearchTab(val label: String) { ALL("Alles"), SERIES("Series"), PROGRAMS("Uitzendingen"), PEOPLE("Personen"), SUBJECTS("Onderwerpen") }

private val STARTERS = listOf("Polygoon", "Jiskefet", "Swiebertje", "Willem Ruis", "Sesamstraat", "Elfstedentocht", "Watersnoodramp", "Toppop")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onOpen: (SchatkamerItem) -> Unit,
    onBrowse: (BrowseSpec) -> Unit,
    contentPadding: PaddingValues
) {
    var tab by rememberSaveable { mutableStateOf(SearchTab.ALL) }
    // Spraakzoeken via de spraakherkenning van het toestel.
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { viewModel.submit(it) }
    }
    val context = LocalContext.current
    val canVoice = remember { RecognizerIntent.ACTION_RECOGNIZE_SPEECH.let { a -> context.packageManager.queryIntentActivities(Intent(a), 0).isNotEmpty() } }
    val text by viewModel.text.collectAsState()
    val state by viewModel.state.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val history by viewModel.history.collectAsState()
    val focus = LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    // Zoeken uitvoeren sluit het toetsenbord, zoals in elke Android-app.
    val done = { keyboard?.hide(); focus.clearFocus() }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    InfiniteScroll(listState, state, viewModel::loadMore)
    LaunchedEffect(state.filter) { listState.scrollToItem(0) }
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) done() }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
            ScreenTitle("Zoeken")
            TextField(
                value = text,
                onValueChange = viewModel::onTextChange,
                placeholder = { Text("Programma, persoon, onderwerp of jaar") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    when {
                        text.isNotEmpty() -> IconButton(onClick = viewModel::clear) { Icon(Icons.Rounded.Close, "Wissen") }
                        canVoice -> IconButton(onClick = {
                            voice.launch(
                                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "nl-NL")
                                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Wat zoek je in de Schatkamer?")
                            )
                        }) { Icon(Icons.Rounded.Mic, "Spraakzoeken") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.submit(); done() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = BrandColors.SurfaceHigh,
                    unfocusedContainerColor = BrandColors.SurfaceHigh,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    cursorColor = BrandColors.Amber
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focusRequester)
            )
        }

        val columns = LocalListColumns.current
        LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            // Suggesties tijdens het typen.
            if (suggestions.isNotEmpty() && text.isNotBlank() && text.trim() != state.filter.query) {
                items(suggestions, key = { "s_$it" }) { s ->
                    SuggestionRow(s, Icons.Rounded.NorthWest) { viewModel.submit(s); done() }
                }
            }

            if (state.filter.query.isBlank()) {
                if (history.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Recent gezocht", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = viewModel::clearHistory) { Text("Wissen", color = BrandColors.TextMuted) }
                        }
                    }
                    items(history, key = { "h_$it" }) { h -> SuggestionRow(h, Icons.Rounded.History) { viewModel.submit(h); done() } }
                }
                item { Text("Probeer bijvoorbeeld", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)) }
                items(STARTERS, key = { "t_$it" }) { s -> SuggestionRow(s, Icons.AutoMirrored.Rounded.TrendingUp) { viewModel.submit(s); done() } }
                return@LazyColumn
            }

            item(key = "tabs") {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SearchTab.entries) { t -> Pill(t.label, { tab = t }, selected = tab == t) }
                }
            }
            when (tab) {
                SearchTab.ALL -> resultsContent(state, onOpen, onFilter = { viewModel.setFilter(it) }, onRetry = viewModel::retry, columns = columns)
                SearchTab.PROGRAMS -> resultsContent(state, onOpen, onFilter = { viewModel.setFilter(it) }, onRetry = viewModel::retry, showSeries = false, columns = columns)
                SearchTab.SERIES -> {
                    if (state.series.isEmpty() && state.facets.programs.isEmpty()) item { EmptyState("Geen series", "Geen serie past bij deze zoekterm.") }
                    items(state.series.chunked(3), key = { row -> "sr_" + row.first().id }) { row ->
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { SeriesPoster(it, { onOpen(it) }, Modifier.weight(1f)) }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    if (state.facets.programs.isNotEmpty()) item {
                        SectionHeader("Meer programma's met deze zoekterm")
                        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.facets.programs.take(24).forEach { f ->
                                Pill("${SchatkamerText.prettyTitle(f.label)}  ${f.count}", { viewModel.submit(SchatkamerText.prettyTitle(f.label)) })
                            }
                        }
                    }
                }
                SearchTab.PEOPLE -> facetChips(state.facets.people, "Geen personen gevonden") { onBrowse(BrowseSpec(it, person = it)) }
                SearchTab.SUBJECTS -> facetChips(state.facets.subjects, "Geen onderwerpen gevonden") { onBrowse(BrowseSpec(it.replaceFirstChar { c -> c.titlecase() }, subject = it)) }
            }
        }
    }
}

@Composable
private fun SuggestionRow(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, tint = BrandColors.TextMuted, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.facetChips(facets: List<nl.openschatkamer.core.data.FacetItem>, empty: String, onPick: (String) -> Unit) {
    if (facets.isEmpty()) {
        item { EmptyState(empty, "Probeer een andere zoekterm.") }
        return
    }
    item {
        FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            facets.forEach { f -> Pill("${f.label}  ${countLabel(f.count, "").trim()}", { onPick(f.label) }) }
        }
    }
}

/** Resultaten van een zoek- of bladeropdracht: series, filters en de uitzendingen. */
fun androidx.compose.foundation.lazy.LazyListScope.resultsContent(
    state: QueryState,
    onOpen: (SchatkamerItem) -> Unit,
    onFilter: (nl.openschatkamer.core.data.SearchFilter) -> Unit,
    onRetry: () -> Unit,
    showSeries: Boolean = true,
    showSeriesLabel: Boolean = true,
    lockBroadcaster: Boolean = false,
    columns: Int = 1
) {
    if (showSeries && state.series.isNotEmpty()) {
        item(key = "series") {
            SectionHeader("Series")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.series, key = { it.id }) { SeriesPoster(it, { onOpen(it) }, width = 116.dp) }
            }
        }
    }
    item(key = "filters") {
        Column(Modifier.padding(top = 8.dp)) {
            FilterBar(state.filter, state.facets.genres, onFilter, broadcasterFacets = state.facets.broadcasters, lockBroadcaster = lockBroadcaster)
            if (state.loaded && state.error == null) {
                Text(
                    countLabel(state.total, if (state.total == 1) "uitzending" else "uitzendingen"),
                    style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
    when {
        state.isLoading -> item { SkeletonList() }
        state.error != null -> item { ErrorState(state.error!!, onRetry) }
        state.loaded && state.items.isEmpty() -> item {
            EmptyState(
                "Niets gevonden",
                if (state.filter.activeFilterCount > 0) "Probeer het met minder filters." else "Probeer een andere zoekterm of spelling.",
                actionLabel = if (state.filter.activeFilterCount > 0) "Filters wissen" else null,
                onAction = { onFilter(state.filter.withoutRefinements()) }
            )
        }
        else -> {
            adaptiveItems(state.items, columns, key = { "p_${it.id}" }) { ProgramListItem(it, { onOpen(it) }, showSeries = showSeriesLabel) }
            if (state.isLoadingMore) item { LoadingFooter() }
        }
    }
}

/** Laadt de volgende pagina zodra de laatste regels in beeld komen. */
@Composable
fun InfiniteScroll(listState: LazyListState, state: QueryState, loadMore: () -> Unit) {
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 10
        }
    }
    LaunchedEffect(listState, state.items.size, state.endReached) {
        snapshotFlow { nearEnd }.collect { if (it && !state.endReached) loadMore() }
    }
}
