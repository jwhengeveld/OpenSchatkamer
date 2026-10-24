package nl.openschatkamer.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.openschatkamer.core.data.FacetItem
import nl.openschatkamer.core.data.SchatkamerCatalogRepository
import nl.openschatkamer.core.data.SearchFilter

/**
 * De filters boven een lijst: snelkeuzes, een knop voor alle filters en een
 * chip per actief filter om het met één tik weer weg te halen.
 */
@Composable
fun FilterBar(
    filter: SearchFilter,
    genres: List<FacetItem>,
    onChange: (SearchFilter) -> Unit,
    modifier: Modifier = Modifier,
    broadcasterFacets: List<FacetItem> = emptyList(),
    lockBroadcaster: Boolean = false
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                val n = filter.activeFilterCount + if (!lockBroadcaster) filter.broadcasters.size.coerceAtMost(1) else 0
                Pill(if (n > 0) "Filters ($n)" else "Filters", { open = true }, selected = n > 0, leading = Icons.Rounded.Tune)
            }
            item {
                Pill("Video", { onChange(filter.copy(material = if (filter.material == "video") null else "video")) },
                    selected = filter.material == "video", leading = Icons.Rounded.Movie)
            }
            item {
                Pill("Audio", { onChange(filter.copy(material = if (filter.material == "audio") null else "audio")) },
                    selected = filter.material == "audio", leading = Icons.Rounded.Headphones)
            }
            // Actieve filters, elk weg te tikken.
            filter.genres.forEach { g ->
                item(key = "g$g") { Pill(g.replaceFirstChar { it.titlecase() }, { onChange(filter.copy(genres = filter.genres - g)) }, selected = true, leading = Icons.Rounded.Close) }
            }
            if (filter.hasPeriod) {
                item(key = "period") {
                    val label = filter.decade?.label ?: "${filter.effectiveYearFrom ?: "…"} – ${filter.effectiveYearTo ?: "nu"}"
                    Pill(label, { onChange(filter.copy(decade = null, yearFrom = null, yearTo = null, dateFrom = null, dateTo = null)) }, selected = true, leading = Icons.Rounded.Close)
                }
            }
            if (!lockBroadcaster) {
                SchatkamerCatalogRepository.CURRENT_BROADCASTERS.filter { b -> b.archiveNames.any { it in filter.broadcasters } }.forEach { b ->
                    item(key = "b${b.name}") {
                        Pill(b.name, { onChange(filter.copy(broadcasters = filter.broadcasters - b.archiveNames.toSet())) }, selected = true, leading = Icons.Rounded.Close)
                    }
                }
            }
            if (!filter.playableOnly) {
                item(key = "np") { Pill("Ook niet afspeelbaar", { onChange(filter.copy(playableOnly = true)) }, selected = true, leading = Icons.Rounded.Close) }
            }
        }
    }
    if (open) {
        FilterSheet(filter, genres, broadcasterFacets, onApply = onChange, onDismiss = { open = false }, lockBroadcaster = lockBroadcaster)
    }
}
