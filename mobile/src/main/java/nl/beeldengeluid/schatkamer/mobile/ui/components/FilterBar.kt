package nl.beeldengeluid.schatkamer.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nl.beeldengeluid.schatkamer.core.data.FacetItem
import nl.beeldengeluid.schatkamer.core.data.SearchFilter
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerBorder
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerCyan
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerDarkBackground
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerDarkElevated
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerMagenta
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerTextMuted
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerTextPrimary
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerTextSecondary
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBar(
    filter: SearchFilter,
    totalCount: Int,
    availableGenres: List<FacetItem> = emptyList(),
    availableMaterials: List<FacetItem> = emptyList(),
    onFilterChange: ((SearchFilter) -> SearchFilter) -> Unit,
    onResetFilters: () -> Unit = { onFilterChange { it.withoutFilters() } },
    searchQuery: String? = null,
    searchPlaceholder: String = "Zoek in archief...",
    onSearchQueryChange: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showDecadeMenu by remember { mutableStateOf(false) }
    var showGenreMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    val formattedTotal = remember(totalCount) {
        if (totalCount >= 10000) "10.000+"
        else NumberFormat.getNumberInstance(Locale.GERMAN).format(totalCount)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Optionele context-zoekbalk
        if (onSearchQueryChange != null && searchQuery != null) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = searchPlaceholder,
                        color = SchatkamerTextMuted,
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = SchatkamerCyan,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Wissen",
                                tint = SchatkamerTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SchatkamerDarkElevated,
                    unfocusedContainerColor = SchatkamerDarkElevated,
                    focusedBorderColor = SchatkamerCyan,
                    unfocusedBorderColor = SchatkamerBorder,
                    focusedTextColor = SchatkamerTextPrimary,
                    unfocusedTextColor = SchatkamerTextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Horizontaal scrollbare snelfilters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Aantal indicator badge
            if (totalCount > 0) {
                Surface(
                    color = SchatkamerCyan.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SchatkamerCyan.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "$formattedTotal items",
                        color = SchatkamerCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }

            // Grote 'Alle Filters' knop (opent BottomSheet)
            FilterChip(
                selected = filter.hasActiveFilters,
                onClick = { showFilterSheet = true },
                leadingIcon = {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = if (filter.hasActiveFilters) Color.White else SchatkamerCyan,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = {
                    val label = if (filter.activeFilterCount > 0) "Filters (${filter.activeFilterCount})" else "Filters"
                    Text(label, fontSize = 12.sp, fontWeight = if (filter.hasActiveFilters) FontWeight.Bold else FontWeight.Normal)
                },
                colors = filterChipColors(filter.hasActiveFilters)
            )

            // Snelle sorteer chip
            Box {
                val sortLabel = when (filter.sort) {
                    "date-newest" -> "📅 Nieuwste"
                    "date-oldest" -> "⏳ Oudste"
                    else -> "🎯 Relevantie"
                }
                FilterChip(
                    selected = filter.sort != "relevance",
                    onClick = { showSortMenu = true },
                    label = { Text(sortLabel, fontSize = 12.sp) },
                    trailingIcon = {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = filterChipColors(filter.sort != "relevance")
                )
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    modifier = Modifier.background(SchatkamerDarkElevated)
                ) {
                    DropdownMenuItem(
                        text = { Text("🎯 Relevantie", color = Color.White) },
                        onClick = {
                            onFilterChange { it.copy(sort = "relevance") }
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("📅 Nieuwste eerst", color = Color.White) },
                        onClick = {
                            onFilterChange { it.copy(sort = "date-newest") }
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("⏳ Oudste eerst", color = Color.White) },
                        onClick = {
                            onFilterChange { it.copy(sort = "date-oldest") }
                            showSortMenu = false
                        }
                    )
                }
            }

            // Directe Video toggle chip
            FilterChip(
                selected = filter.material == "video",
                onClick = {
                    onFilterChange { it.withToggledMaterial("video") }
                },
                label = { Text("🎬 Video", fontSize = 12.sp) },
                colors = filterChipColors(filter.material == "video")
            )

            // Directe Audio toggle chip
            FilterChip(
                selected = filter.material == "audio",
                onClick = {
                    onFilterChange { it.withToggledMaterial("audio") }
                },
                label = { Text("📻 Audio", fontSize = 12.sp) },
                colors = filterChipColors(filter.material == "audio")
            )

            // Snelle Periode dropdown chip
            Box {
                val decadeLabel = when (filter.decade) {
                    "1950s" -> "Jaren '50"
                    "1960s" -> "Jaren '60"
                    "1970s" -> "Jaren '70"
                    "1980s" -> "Jaren '80"
                    "1990s" -> "Jaren '90"
                    "2000s" -> "2000+"
                    else -> "🗓️ Periode"
                }
                FilterChip(
                    selected = filter.decade != null,
                    onClick = { showDecadeMenu = true },
                    label = { Text(decadeLabel, fontSize = 12.sp) },
                    trailingIcon = {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = filterChipColors(filter.decade != null)
                )
                DropdownMenu(
                    expanded = showDecadeMenu,
                    onDismissRequest = { showDecadeMenu = false },
                    modifier = Modifier.background(SchatkamerDarkElevated)
                ) {
                    DropdownMenuItem(
                        text = { Text("Alle jaren", color = Color.White) },
                        onClick = {
                            onFilterChange { it.copy(decade = null) }
                            showDecadeMenu = false
                        }
                    )
                    listOf(
                        "1950s" to "Jaren '50 (1950-1959)",
                        "1960s" to "Jaren '60 (1960-1969)",
                        "1970s" to "Jaren '70 (1970-1979)",
                        "1980s" to "Jaren '80 (1980-1989)",
                        "1990s" to "Jaren '90 (1990-1999)",
                        "2000s" to "2000 en later"
                    ).forEach { (key, title) ->
                        DropdownMenuItem(
                            text = { Text(title, color = Color.White) },
                            onClick = {
                                onFilterChange { it.copy(decade = key) }
                                showDecadeMenu = false
                            }
                        )
                    }
                }
            }

            // Snelle Genre dropdown chip (indien beschikbaar)
            if (availableGenres.isNotEmpty()) {
                Box {
                    val genreLabel = filter.genre?.let { g ->
                        val item = availableGenres.find { it.label.equals(g, ignoreCase = true) }
                        if (item != null && item.count > 0) "${item.label} (${formatCount(item.count)})" else g
                    } ?: "🎭 Genre"

                    FilterChip(
                        selected = filter.genre != null,
                        onClick = { showGenreMenu = true },
                        label = { Text(genreLabel, fontSize = 12.sp) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = filterChipColors(filter.genre != null)
                    )
                    DropdownMenu(
                        expanded = showGenreMenu,
                        onDismissRequest = { showGenreMenu = false },
                        modifier = Modifier.background(SchatkamerDarkElevated)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Alle genres", color = Color.White) },
                            onClick = {
                                onFilterChange { it.copy(genre = null) }
                                showGenreMenu = false
                            }
                        )
                        availableGenres.take(20).forEach { item ->
                            val countStr = if (item.count > 0) " (${formatCount(item.count)})" else ""
                            DropdownMenuItem(
                                text = { Text("${item.label}$countStr", color = Color.White) },
                                onClick = {
                                    onFilterChange { it.copy(genre = item.label) }
                                    showGenreMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // Wis filters knop
            if (filter.hasActiveFilters) {
                Surface(
                    color = SchatkamerMagenta.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SchatkamerMagenta.copy(alpha = 0.5f)),
                    onClick = onResetFilters
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Reset",
                            tint = SchatkamerMagenta,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Wis filters",
                            color = SchatkamerMagenta,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet voor geavanceerd filteren
    if (showFilterSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()

        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
            containerColor = SchatkamerDarkElevated,
            contentColor = SchatkamerTextPrimary
        ) {
            FilterBottomSheetContent(
                filter = filter,
                totalCount = totalCount,
                availableGenres = availableGenres,
                availableMaterials = availableMaterials,
                onFilterChange = onFilterChange,
                onResetFilters = onResetFilters,
                onClose = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) showFilterSheet = false
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterBottomSheetContent(
    filter: SearchFilter,
    totalCount: Int,
    availableGenres: List<FacetItem>,
    availableMaterials: List<FacetItem>,
    onFilterChange: ((SearchFilter) -> SearchFilter) -> Unit,
    onResetFilters: () -> Unit,
    onClose: () -> Unit
) {
    val scrollState = rememberScrollState()

    val formattedTotal = remember(totalCount) {
        if (totalCount >= 10000) "10.000+"
        else NumberFormat.getNumberInstance(Locale.GERMAN).format(totalCount)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Filters & Sortering",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (filter.activeFilterCount > 0) {
                    Text(
                        text = "${filter.activeFilterCount} filter(s) actief • $formattedTotal programma's",
                        fontSize = 12.sp,
                        color = SchatkamerCyan
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Sluiten", tint = SchatkamerTextSecondary)
            }
        }

        HorizontalDivider(color = SchatkamerBorder, thickness = 1.dp)

        // Filter Content
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Sectie: Sortering
            FilterSection(title = "Sortering") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "relevance" to "🎯 Meest relevant",
                        "date-newest" to "📅 Nieuwste eerst",
                        "date-oldest" to "⏳ Oudste eerst"
                    ).forEach { (key, label) ->
                        val isSelected = filter.sort == key
                        FilterChip(
                            selected = isSelected,
                            onClick = { onFilterChange { it.copy(sort = key) } },
                            label = { Text(label, fontSize = 13.sp) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = filterChipColors(isSelected)
                        )
                    }
                }
            }

            // Sectie: Mediumtype
            FilterSection(title = "Type medium") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val allSelected = filter.material == null
                    FilterChip(
                        selected = allSelected,
                        onClick = { onFilterChange { it.copy(material = null) } },
                        label = { Text("Alle media", fontSize = 13.sp) },
                        colors = filterChipColors(allSelected)
                    )

                    val videoFacet = availableMaterials.find { it.label.equals("video", ignoreCase = true) }
                    val videoLabel = if (videoFacet != null && videoFacet.count > 0) "🎬 Video (${formatCount(videoFacet.count)})" else "🎬 Alleen Video"
                    val isVideo = filter.material == "video"
                    FilterChip(
                        selected = isVideo,
                        onClick = { onFilterChange { it.copy(material = if (isVideo) null else "video") } },
                        label = { Text(videoLabel, fontSize = 13.sp) },
                        colors = filterChipColors(isVideo)
                    )

                    val audioFacet = availableMaterials.find { it.label.equals("audio", ignoreCase = true) }
                    val audioLabel = if (audioFacet != null && audioFacet.count > 0) "📻 Audio (${formatCount(audioFacet.count)})" else "📻 Alleen Audio"
                    val isAudio = filter.material == "audio"
                    FilterChip(
                        selected = isAudio,
                        onClick = { onFilterChange { it.copy(material = if (isAudio) null else "audio") } },
                        label = { Text(audioLabel, fontSize = 13.sp) },
                        colors = filterChipColors(isAudio)
                    )
                }
            }

            // Sectie: Tijdsperiode
            FilterSection(title = "Tijdsperiode") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val allDecades = filter.decade == null && filter.yearMin == null && filter.yearMax == null
                    FilterChip(
                        selected = allDecades,
                        onClick = { onFilterChange { it.copy(decade = null, yearMin = null, yearMax = null) } },
                        label = { Text("Alle jaren", fontSize = 13.sp) },
                        colors = filterChipColors(allDecades)
                    )

                    listOf(
                        "1950s" to "Jaren '50",
                        "1960s" to "Jaren '60",
                        "1970s" to "Jaren '70",
                        "1980s" to "Jaren '80",
                        "1990s" to "Jaren '90",
                        "2000s" to "2000 en later"
                    ).forEach { (key, label) ->
                        val isSelected = filter.decade == key
                        FilterChip(
                            selected = isSelected,
                            onClick = { onFilterChange { it.copy(decade = if (isSelected) null else key, yearMin = null, yearMax = null) } },
                            label = { Text(label, fontSize = 13.sp) },
                            colors = filterChipColors(isSelected)
                        )
                    }
                }
            }

            // Sectie: Genres & Thema's
            if (availableGenres.isNotEmpty()) {
                FilterSection(title = "Genre & Thema") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val allGenres = filter.genre == null
                        FilterChip(
                            selected = allGenres,
                            onClick = { onFilterChange { it.copy(genre = null) } },
                            label = { Text("Alle genres", fontSize = 13.sp) },
                            colors = filterChipColors(allGenres)
                        )

                        availableGenres.take(25).forEach { item ->
                            val isSelected = filter.genre.equals(item.label, ignoreCase = true)
                            val countStr = if (item.count > 0) " (${formatCount(item.count)})" else ""
                            FilterChip(
                                selected = isSelected,
                                onClick = { onFilterChange { it.copy(genre = if (isSelected) null else item.label) } },
                                label = { Text("${item.label}$countStr", fontSize = 13.sp) },
                                colors = filterChipColors(isSelected)
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = SchatkamerBorder, thickness = 1.dp)

        // Footer Knoppen
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onResetFilters,
                enabled = filter.hasActiveFilters
            ) {
                Text(
                    text = "Filters wissen",
                    color = if (filter.hasActiveFilters) SchatkamerMagenta else SchatkamerTextMuted,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = SchatkamerCyan),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Toon $formattedTotal resultaten",
                    color = SchatkamerDarkBackground,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = SchatkamerTextSecondary
        )
        content()
    }
}

private fun formatCount(count: Int): String {
    return if (count >= 10000) "10k+"
    else if (count >= 1000) "${count / 1000}k"
    else count.toString()
}

@Composable
private fun filterChipColors(selected: Boolean) = FilterChipDefaults.filterChipColors(
    containerColor = SchatkamerDarkElevated,
    labelColor = SchatkamerTextSecondary,
    iconColor = SchatkamerTextSecondary,
    selectedContainerColor = SchatkamerCyan.copy(alpha = 0.25f),
    selectedLabelColor = Color.White,
    selectedLeadingIconColor = SchatkamerCyan,
    selectedTrailingIconColor = SchatkamerCyan
)
