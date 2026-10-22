package nl.beeldengeluid.schatkamer.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.beeldengeluid.schatkamer.core.data.SearchFilter
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerBorder
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerCyan
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerDarkElevated
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerMagenta
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerTextMuted
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerTextPrimary
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerTextSecondary
import java.text.NumberFormat
import java.util.Locale

@Composable
fun FilterBar(
    filter: SearchFilter,
    totalCount: Int,
    availableGenres: List<String> = emptyList(),
    onFilterChange: ((SearchFilter) -> SearchFilter) -> Unit,
    searchQuery: String? = null,
    searchPlaceholder: String = "Zoek in archief...",
    onSearchQueryChange: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showMaterialMenu by remember { mutableStateOf(false) }
    var showDecadeMenu by remember { mutableStateOf(false) }
    var showGenreMenu by remember { mutableStateOf(false) }

    val formattedTotal = remember(totalCount) {
        if (totalCount >= 10000) "10.000+"
        else NumberFormat.getNumberInstance(Locale.GERMAN).format(totalCount)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Optionele zoekbalk binnen de context
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

        // Horizontaal scrollbare Filter Chips
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

            // Sorteer chip
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

            // Type Medium chip (Video / Audio)
            Box {
                val materialLabel = when (filter.material) {
                    "video" -> "🎬 Video"
                    "audio" -> "📻 Audio"
                    else -> "🎬 Type"
                }
                FilterChip(
                    selected = filter.material != null,
                    onClick = { showMaterialMenu = true },
                    label = { Text(materialLabel, fontSize = 12.sp) },
                    trailingIcon = {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = filterChipColors(filter.material != null)
                )
                DropdownMenu(
                    expanded = showMaterialMenu,
                    onDismissRequest = { showMaterialMenu = false },
                    modifier = Modifier.background(SchatkamerDarkElevated)
                ) {
                    DropdownMenuItem(
                        text = { Text("Alle media", color = Color.White) },
                        onClick = {
                            onFilterChange { it.copy(material = null) }
                            showMaterialMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("🎬 Alleen video", color = Color.White) },
                        onClick = {
                            onFilterChange { it.copy(material = "video") }
                            showMaterialMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("📻 Alleen audio (radio/geluid)", color = Color.White) },
                        onClick = {
                            onFilterChange { it.copy(material = "audio") }
                            showMaterialMenu = false
                        }
                    )
                }
            }

            // Decennium chip
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

            // Genre chip (wanneer genres aanwezig zijn)
            if (availableGenres.isNotEmpty()) {
                Box {
                    val genreLabel = filter.genre ?: "🎭 Genre"
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
                        availableGenres.take(15).forEach { g ->
                            DropdownMenuItem(
                                text = { Text(g, color = Color.White) },
                                onClick = {
                                    onFilterChange { it.copy(genre = g) }
                                    showGenreMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // Filter wissen knop
            if (filter.hasActiveFilters) {
                Surface(
                    color = SchatkamerMagenta.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SchatkamerMagenta.copy(alpha = 0.5f)),
                    onClick = {
                        onFilterChange { current ->
                            SearchFilter(
                                query = current.query,
                                broadcasterSlug = current.broadcasterSlug,
                                broadcasterNames = current.broadcasterNames
                            )
                        }
                    }
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
