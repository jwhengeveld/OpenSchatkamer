package nl.openschatkamer.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.openschatkamer.brand.BrandColors
import nl.openschatkamer.brand.UNOFFICIAL_NOTICE
import nl.openschatkamer.core.data.ItemKind
import nl.openschatkamer.core.data.SchatkamerItem
import nl.openschatkamer.core.data.WatchProgress
import nl.openschatkamer.mobile.ui.components.MessageState
import nl.openschatkamer.mobile.ui.components.ProgramListItem
import nl.openschatkamer.mobile.ui.components.ScreenTitle
import nl.openschatkamer.mobile.ui.components.SectionHeader
import nl.openschatkamer.mobile.ui.components.SeriesPoster

@Composable
fun MyListScreen(
    favorites: List<SchatkamerItem>,
    continueWatching: List<WatchProgress>,
    onOpen: (SchatkamerItem) -> Unit,
    onPlay: (SchatkamerItem) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onRemoveProgress: (String) -> Unit,
    contentPadding: PaddingValues
) {
    val series = favorites.filter { it.kind == ItemKind.SERIES }
    val programs = favorites.filter { it.kind != ItemKind.SERIES }

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item { ScreenTitle("Mijn lijst", Modifier.windowInsetsPadding(WindowInsets.statusBars)) }

        if (favorites.isEmpty() && continueWatching.isEmpty()) {
            item {
                MessageState(
                    Icons.Rounded.Bookmark, "Nog niets bewaard",
                    "Tik op het bladwijzer-icoon bij een serie of uitzending om hem hier te bewaren. Wat je half hebt gekeken, verschijnt hier vanzelf."
                )
            }
        }
        if (continueWatching.isNotEmpty()) {
            item { SectionHeader("Verder kijken") }
            items(continueWatching, key = { "c_${it.item.id}" }) { p ->
                ProgramListItem(p.item, { onPlay(p.item) }, progress = p.fraction, trailing = {
                    IconButton(onClick = { onRemoveProgress(p.item.id) }) { Icon(Icons.Rounded.Close, "Verwijderen", tint = BrandColors.TextMuted) }
                })
            }
        }
        if (series.isNotEmpty()) {
            item {
                SectionHeader("Series")
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(series, key = { it.id }) { SeriesPoster(it, { onOpen(it) }) }
                }
            }
        }
        if (programs.isNotEmpty()) {
            item { SectionHeader("Uitzendingen") }
            items(programs, key = { "f_${it.id}" }) { p ->
                ProgramListItem(p, { onOpen(p) }, trailing = {
                    IconButton(onClick = { onRemoveFavorite(p.id) }) { Icon(Icons.Rounded.Close, "Verwijderen", tint = BrandColors.TextMuted) }
                })
            }
        }
        item {
            SectionHeader("Over OpenSchatkamer")
            Text(
                UNOFFICIAL_NOTICE + " Je lijst en kijkvoortgang staan alleen op dit toestel.",
                style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}
