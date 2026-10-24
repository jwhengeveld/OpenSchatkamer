package nl.openschatkamer.mobile.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.openschatkamer.brand.BrandColors
import nl.openschatkamer.core.data.Decade
import nl.openschatkamer.core.data.FacetItem
import nl.openschatkamer.core.data.SearchFilter
import nl.openschatkamer.core.data.SortOrder

private enum class Sheet { SORT, DECADE, GENRE }

/**
 * De filters boven een lijst. Genres komen met hun aantallen uit de huidige
 * resultaten, dus je kiest nooit iets dat niets oplevert.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBar(
    filter: SearchFilter,
    genres: List<FacetItem>,
    onChange: (SearchFilter) -> Unit,
    modifier: Modifier = Modifier,
    showGenre: Boolean = true
) {
    var sheet by remember { mutableStateOf<Sheet?>(null) }

    LazyRow(
        modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            val label = if (filter.query.isBlank() && filter.sort == SortOrder.RELEVANCE) SortOrder.NEWEST.label else filter.sort.label
            Pill(label, { sheet = Sheet.SORT }, leading = Icons.Rounded.SwapVert)
        }
        item {
            Pill("Video", { onChange(filter.copy(material = if (filter.material == "video") null else "video")) },
                selected = filter.material == "video", leading = Icons.Rounded.Movie)
        }
        item {
            Pill("Audio", { onChange(filter.copy(material = if (filter.material == "audio") null else "audio")) },
                selected = filter.material == "audio", leading = Icons.Rounded.Headphones)
        }
        item {
            Pill(filter.decade?.label ?: "Periode", { sheet = Sheet.DECADE }, selected = filter.decade != null, leading = Icons.Rounded.ExpandMore)
        }
        if (showGenre && (genres.isNotEmpty() || filter.genre != null)) {
            item {
                Pill(filter.genre?.replaceFirstChar { it.titlecase() } ?: "Genre", { sheet = Sheet.GENRE },
                    selected = filter.genre != null, leading = Icons.Rounded.ExpandMore)
            }
        }
        if (filter.material != null || filter.decade != null || filter.genre != null) {
            item { Pill("Wissen", { onChange(filter.withoutRefinements().copy(sort = filter.sort)) }, leading = Icons.Rounded.Close) }
        }
    }

    sheet?.let { current ->
        ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = BrandColors.Surface) {
            val close = { sheet = null }
            when (current) {
                Sheet.SORT -> SheetList(
                    title = "Sorteren",
                    options = (if (filter.query.isBlank()) listOf(SortOrder.NEWEST, SortOrder.OLDEST) else SortOrder.entries.toList())
                        .map { it.label to (it == filter.sort || (filter.sort == SortOrder.RELEVANCE && filter.query.isBlank() && it == SortOrder.NEWEST)) },
                    onPick = { i ->
                        val options = if (filter.query.isBlank()) listOf(SortOrder.NEWEST, SortOrder.OLDEST) else SortOrder.entries.toList()
                        onChange(filter.copy(sort = options[i])); close()
                    }
                )
                Sheet.DECADE -> SheetList(
                    title = "Periode",
                    options = listOf("Alle jaren" to (filter.decade == null)) + Decade.entries.map { "${it.label}  (${it.from}–${it.to})" to (it == filter.decade) },
                    onPick = { i -> onChange(filter.copy(decade = if (i == 0) null else Decade.entries[i - 1])); close() }
                )
                Sheet.GENRE -> SheetList(
                    title = "Genre",
                    options = listOf("Alle genres" to (filter.genre == null)) +
                        genres.map { "${it.label.replaceFirstChar { c -> c.titlecase() }}  ·  ${countLabel(it.count, "")}".trimEnd() to (it.label == filter.genre) },
                    onPick = { i -> onChange(filter.copy(genre = if (i == 0) null else genres[i - 1].label)); close() }
                )
            }
        }
    }
}

@Composable
private fun SheetList(title: String, options: List<Pair<String, Boolean>>, onPick: (Int) -> Unit) {
    Column(Modifier.navigationBarsPadding()) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        LazyColumn {
            items(options.size) { i ->
                val (label, selected) = options[i]
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(i) }.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                        color = if (selected) BrandColors.Amber else BrandColors.Text)
                    if (selected) Icon(Icons.Rounded.Check, null, tint = BrandColors.Amber)
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
