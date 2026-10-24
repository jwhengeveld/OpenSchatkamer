package nl.openschatkamer.mobile.ui.screens

import androidx.compose.foundation.clickable
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
import nl.openschatkamer.mobile.ui.components.ScreenTitle
import nl.openschatkamer.mobile.ui.components.SectionHeader
import nl.openschatkamer.mobile.ui.components.SeriesPoster
import nl.openschatkamer.mobile.ui.components.SkeletonList
import nl.openschatkamer.mobile.ui.components.countLabel

private val STARTERS = listOf("Polygoon", "Jiskefet", "Swiebertje", "Willem Ruis", "Sesamstraat", "Elfstedentocht", "Watersnoodramp", "Toppop")

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onOpen: (SchatkamerItem) -> Unit,
    contentPadding: PaddingValues
) {
    val text by viewModel.text.collectAsState()
    val state by viewModel.state.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val history by viewModel.history.collectAsState()
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    InfiniteScroll(listState, state, viewModel::loadMore)
    LaunchedEffect(state.filter) { listState.scrollToItem(0) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
            ScreenTitle("Zoeken")
            TextField(
                value = text,
                onValueChange = viewModel::onTextChange,
                placeholder = { Text("Programma, persoon, onderwerp of jaar") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (text.isNotEmpty()) IconButton(onClick = viewModel::clear) { Icon(Icons.Rounded.Close, "Wissen") }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.submit(); focus.clearFocus() }),
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

        LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            // Suggesties tijdens het typen.
            if (suggestions.isNotEmpty() && text.isNotBlank() && text.trim() != state.filter.query) {
                items(suggestions, key = { "s_$it" }) { s ->
                    SuggestionRow(s, Icons.Rounded.NorthWest) { viewModel.submit(s); focus.clearFocus() }
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
                    items(history, key = { "h_$it" }) { h -> SuggestionRow(h, Icons.Rounded.History) { viewModel.submit(h) } }
                }
                item { Text("Probeer bijvoorbeeld", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)) }
                items(STARTERS, key = { "t_$it" }) { s -> SuggestionRow(s, Icons.AutoMirrored.Rounded.TrendingUp) { viewModel.submit(s) } }
                return@LazyColumn
            }

            resultsContent(state, onOpen, onFilter = { viewModel.setFilter(it) }, onRetry = viewModel::retry)
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

/** Resultaten van een zoek- of bladeropdracht: series, filters en de uitzendingen. */
fun androidx.compose.foundation.lazy.LazyListScope.resultsContent(
    state: QueryState,
    onOpen: (SchatkamerItem) -> Unit,
    onFilter: (nl.openschatkamer.core.data.SearchFilter) -> Unit,
    onRetry: () -> Unit,
    showSeries: Boolean = true,
    showSeriesLabel: Boolean = true
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
            FilterBar(state.filter, state.facets.genres, onFilter)
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
            items(state.items, key = { "p_${it.id}" }) { ProgramListItem(it, { onOpen(it) }, showSeries = showSeriesLabel) }
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
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(listState, state.items.size, state.endReached) {
        snapshotFlow { nearEnd }.collect { if (it && !state.endReached) loadMore() }
    }
}
