package nl.beeldengeluid.schatkamer.mobile.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import nl.beeldengeluid.schatkamer.core.data.BroadcasterItem
import nl.beeldengeluid.schatkamer.core.data.GenreItem
import nl.beeldengeluid.schatkamer.core.data.SchatkamerItem
import nl.beeldengeluid.schatkamer.brand.BroadcasterLogos
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import nl.beeldengeluid.schatkamer.mobile.cast.CastButtonRow
import nl.beeldengeluid.schatkamer.mobile.ui.DiscoverViewModel
import nl.beeldengeluid.schatkamer.mobile.ui.components.FilterBar
import nl.beeldengeluid.schatkamer.mobile.ui.components.ProgramCard
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerBorder
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerCyan
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerDarkBackground
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerDarkElevated
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerMagenta
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerTextMuted
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerTextPrimary
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    viewModel: DiscoverViewModel,
    onSelectItem: (SchatkamerItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedCategoryTitle by remember { mutableStateOf<String?>(null) }
    var insideSearchQuery by remember { mutableStateOf("") }

    val selectedPrograms by viewModel.selectedItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val availableGenres by viewModel.availableGenres.collectAsState()
    val selectedBroadcaster by viewModel.selectedBroadcaster.collectAsState()

    BackHandler(enabled = selectedCategoryTitle != null) {
        selectedCategoryTitle = null
        insideSearchQuery = ""
        viewModel.clearSelection()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = selectedCategoryTitle ?: "Ontdekken",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (selectedCategoryTitle != null && totalCount > 0) {
                            val formattedCount = if (totalCount >= 10000) "10.000+" else java.text.NumberFormat.getNumberInstance(java.util.Locale.GERMAN).format(totalCount)
                            Text(
                                text = "$formattedCount programma's in archief",
                                fontSize = 11.sp,
                                color = SchatkamerTextMuted
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (selectedCategoryTitle != null) {
                        IconButton(onClick = {
                            selectedCategoryTitle = null
                            insideSearchQuery = ""
                            viewModel.clearSelection()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Terug", tint = Color.White)
                        }
                    }
                },
                actions = { CastButtonRow(modifier = Modifier.padding(end = 6.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SchatkamerDarkBackground)
            )
        },
        containerColor = SchatkamerDarkBackground,
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (selectedCategoryTitle != null) {
                // Filter balk met sorteer, type medium, decennium, genres en zoekbalk binnen omroep
                FilterBar(
                    filter = currentFilter,
                    totalCount = totalCount,
                    availableGenres = availableGenres,
                    onFilterChange = { viewModel.updateFilter(it) },
                    searchQuery = insideSearchQuery,
                    searchPlaceholder = "Zoek binnen ${selectedBroadcaster?.name ?: selectedCategoryTitle}...",
                    onSearchQueryChange = { q ->
                        insideSearchQuery = q
                        viewModel.updateFilter { it.copy(query = q) }
                    }
                )

                // Programmaweergave voor geselecteerde categorie
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SchatkamerCyan)
                    }
                } else if (selectedPrograms.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Geen programma's gevonden voor deze selectie", color = SchatkamerTextSecondary)
                    }
                } else {
                    val gridState = rememberLazyGridState()
                    LaunchedEffect(gridState, selectedPrograms.size, hasMore, isLoadingMore) {
                        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                            .collect { lastIdx ->
                                if (lastIdx != null && lastIdx >= selectedPrograms.size - 4 && hasMore && !isLoadingMore) {
                                    viewModel.loadMore()
                                }
                            }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        state = gridState,
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(selectedPrograms, key = { it.id }) { item ->
                            ProgramCard(item = item, onClick = { onSelectItem(item) })
                        }
                        if (isLoadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            color = SchatkamerCyan,
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Meer programma's laden...",
                                            color = SchatkamerTextSecondary,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Tab switcher tussen Omroepen en Genres
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = SchatkamerDarkBackground,
                    contentColor = SchatkamerCyan,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = SchatkamerCyan
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Omroepen", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Genres", fontWeight = FontWeight.Bold) }
                    )
                }

                if (selectedTab == 0) {
                    // Omroepen grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(viewModel.broadcasters, key = { it.id }) { b ->
                            BroadcasterCard(
                                broadcaster = b,
                                onClick = {
                                    selectedCategoryTitle = b.name
                                    viewModel.selectBroadcaster(b)
                                }
                            )
                        }
                    }
                } else {
                    // Genres grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(viewModel.genres, key = { it.slug }) { g ->
                            GenreCard(
                                genre = g,
                                onClick = {
                                    selectedCategoryTitle = g.title
                                    viewModel.selectGenre(g)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BroadcasterCard(
    broadcaster: BroadcasterItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SchatkamerDarkElevated)
            .border(1.dp, SchatkamerBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        val logoRes = BroadcasterLogos.getLogoRes(broadcaster.slug)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            if (logoRes != null) {
                Image(
                    painter = painterResource(id = logoRes),
                    contentDescription = broadcaster.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    contentScale = ContentScale.Fit
                )
            } else if (broadcaster.logoUrl.isNotBlank()) {
                AsyncImage(
                    model = broadcaster.logoUrl,
                    contentDescription = broadcaster.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = broadcaster.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = SchatkamerCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = broadcaster.name,
            color = SchatkamerTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        if (broadcaster.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = broadcaster.description,
                color = SchatkamerTextMuted,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GenreCard(
    genre: GenreItem,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(12.dp))
            .background(SatkamerElevatedOrSurface())
            .border(1.dp, SchatkamerBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        if (genre.imageUrl.isNotBlank()) {
            AsyncImage(
                model = genre.imageUrl,
                contentDescription = genre.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            )
        }

        Text(
            text = genre.title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        )
    }
}

@Composable
private fun SatkamerElevatedOrSurface(): Color = SchatkamerDarkElevated
