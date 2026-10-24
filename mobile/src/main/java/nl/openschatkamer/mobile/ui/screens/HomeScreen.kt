package nl.openschatkamer.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import nl.openschatkamer.brand.BrandColors
import nl.openschatkamer.brand.UNOFFICIAL_NOTICE
import nl.openschatkamer.brand.Wordmark
import nl.openschatkamer.core.data.ItemKind
import nl.openschatkamer.core.data.SchatkamerHome
import nl.openschatkamer.core.data.SchatkamerItem
import nl.openschatkamer.core.data.WatchProgress
import nl.openschatkamer.core.ui.BrowseSpec
import nl.openschatkamer.core.ui.TimeTravelViewModel
import nl.openschatkamer.mobile.cast.CastButton
import nl.openschatkamer.core.ui.HomeViewModel
import nl.openschatkamer.core.ui.Load
import nl.openschatkamer.mobile.ui.components.Artwork
import nl.openschatkamer.mobile.ui.components.CatalogRow
import nl.openschatkamer.mobile.ui.components.ErrorState
import nl.openschatkamer.mobile.ui.components.ProgramCard
import nl.openschatkamer.mobile.ui.components.SectionHeader
import nl.openschatkamer.mobile.ui.components.SkeletonRows
import nl.openschatkamer.mobile.ui.components.metaLine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    continueWatching: List<WatchProgress>,
    timeTravel: List<TimeTravelViewModel.Row>,
    onOpen: (SchatkamerItem) -> Unit,
    onPlay: (SchatkamerItem) -> Unit,
    onBrowse: (BrowseSpec) -> Unit,
    contentPadding: PaddingValues
) {
    val home by viewModel.home.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            item { TopBar() }
            when (val h = home) {
                Load.Loading -> item { SkeletonRows() }
                is Load.Failed -> item { ErrorState(h.message, viewModel::refresh) }
                is Load.Ready -> homeContent(h.data, continueWatching, timeTravel, onOpen, onPlay, onBrowse)
            }
            item {
                Text(
                    UNOFFICIAL_NOTICE,
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.TextMuted.copy(alpha = 0.75f),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp)
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.homeContent(
    home: SchatkamerHome,
    continueWatching: List<WatchProgress>,
    timeTravel: List<TimeTravelViewModel.Row>,
    onOpen: (SchatkamerItem) -> Unit,
    onPlay: (SchatkamerItem) -> Unit,
    onBrowse: (BrowseSpec) -> Unit
) {
    if (home.heroItems.isNotEmpty()) item { Hero(home.heroItems, onOpen) }
    if (continueWatching.isNotEmpty()) {
        item {
            SectionHeader("Verder kijken")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(continueWatching, key = { it.item.id }) { p ->
                    ProgramCard(p.item, onClick = { onPlay(p.item) }, progress = p.fraction)
                }
            }
        }
    }
    // De redactierijen van de site, met na de eerste twee "terug in de tijd".
    home.browseRows.forEachIndexed { index, row ->
        item(key = row.id, contentType = "row") { CatalogRow(row, onOpen) }
        if (index == 1) timeTravel.forEach { t ->
            item(key = "tt-${t.yearsAgo}", contentType = "row") {
                SectionHeader(t.spec.title, actionLabel = "Kijk meer", onAction = { onBrowse(t.spec) })
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(t.items, key = { it.id }) { ProgramCard(it, { onOpen(it) }) }
                }
            }
        }
    }
}

@Composable
private fun TopBar() {
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Wordmark(Modifier.weight(1f))
        CastButton()
    }
}

/** Uitgelicht: grote beelden die vanzelf doorschuiven. */
@Composable
private fun Hero(items: List<SchatkamerItem>, onOpen: (SchatkamerItem) -> Unit) {
    val pager = rememberPagerState { items.size }
    LaunchedEffect(pager.currentPage, items.size) {
        delay(7_000)
        if (items.size > 1 && !pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % items.size)
    }
    androidx.compose.foundation.layout.BoxWithConstraints {
    // Staand 4:5 op een telefoon, maar nooit hoger dan 520dp (tablet, liggend).
    val cardHeight = minOf((maxWidth - 32.dp) * 1.25f, 520.dp)
    Column {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.padding(top = 8.dp)
        ) { page ->
            val item = items[page]
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .clip(MaterialTheme.shapes.large)
                    .clickable { onOpen(item) }
            ) {
                Artwork(item.imageUrl, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to BrandColors.Ink)))
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                    Text(
                        if (item.kind == ItemKind.SERIES) "SERIE" else if (item.isAudio) "LUISTEREN" else "UITGELICHT",
                        style = MaterialTheme.typography.labelSmall, color = BrandColors.Amber
                    )
                    Text(item.title, style = MaterialTheme.typography.displaySmall, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    val meta = item.metaLine()
                    if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodyMedium, color = BrandColors.Text.copy(alpha = 0.8f))
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { onOpen(item) },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Amber, contentColor = BrandColors.Ink)
                    ) {
                        Icon(if (item.isAudio) Icons.Rounded.Headphones else Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (item.kind == ItemKind.SERIES) "Bekijk de serie" else "Bekijken")
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
            repeat(items.size) { i ->
                Box(
                    Modifier.padding(3.dp).size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape)
                        .background(if (i == pager.currentPage) BrandColors.Amber else BrandColors.Outline)
                )
            }
        }
    }
    }
}
