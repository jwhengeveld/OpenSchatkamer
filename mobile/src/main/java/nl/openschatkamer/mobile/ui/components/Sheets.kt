package nl.openschatkamer.mobile.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.openschatkamer.brand.BrandColors
import nl.openschatkamer.core.data.FacetItem
import nl.openschatkamer.core.data.ItemKind
import nl.openschatkamer.core.data.SchatkamerCatalogRepository
import nl.openschatkamer.core.data.SchatkamerItem
import nl.openschatkamer.core.data.SearchFilter
import nl.openschatkamer.core.data.SortOrder
import nl.openschatkamer.core.data.UserLibrary
import java.util.Calendar

fun shareItem(context: Context, item: SchatkamerItem) {
    val url = when (item.kind) {
        ItemKind.SERIES -> "https://schatkamer.beeldengeluid.nl/serie/${item.id}/${item.slug.orEmpty()}"
        ItemKind.STORY -> "https://schatkamer.beeldengeluid.nl/verhaal/${item.id}"
        else -> "https://schatkamer.beeldengeluid.nl/programma/${item.id}"
    }
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${item.title}\n$url"), "Delen"))
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit, tint: androidx.compose.ui.graphics.Color = BrandColors.Text) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(18.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Alles wat je met een kaart kunt doen; open door lang indrukken. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemActionsSheet(
    item: SchatkamerItem,
    library: UserLibrary,
    onDismiss: () -> Unit,
    onOpenSeries: (SchatkamerItem) -> Unit
) {
    val context = LocalContext.current
    // Verzamelen zodat de status meteen meeverandert.
    library.favorites.collectAsState().value
    library.series.collectAsState().value
    library.local.history.collectAsState().value
    var addToList by remember { mutableStateOf(false) }

    if (addToList) {
        AddToListSheet(item, library, onDismiss)
        return
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BrandColors.Surface) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(8.dp))
            val saved = library.isFavorite(item)
            ActionRow(
                if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                when {
                    item.kind == ItemKind.SERIES && saved -> "Niet meer volgen"
                    item.kind == ItemKind.SERIES -> "Serie volgen"
                    saved -> "Uit Mijn lijst halen"
                    else -> "Bewaren in Mijn lijst"
                },
                { library.toggleFavorite(item); onDismiss() },
                BrandColors.Amber
            )
            if (item.kind == ItemKind.PROGRAM) {
                ActionRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "Toevoegen aan lijst…", { addToList = true })
                val watched = library.local.isWatched(item.id)
                ActionRow(
                    if (watched) Icons.Rounded.RadioButtonUnchecked else Icons.Rounded.CheckCircle,
                    if (watched) "Markeren als niet gezien" else "Markeren als gezien",
                    { library.local.setWatched(item, !watched); onDismiss() }
                )
                if (!item.seriesId.isNullOrBlank()) {
                    ActionRow(Icons.Rounded.VideoLibrary, "Naar de serie", {
                        onDismiss()
                        onOpenSeries(SchatkamerItem(item.seriesId!!, item.seriesTitle.orEmpty(), ItemKind.SERIES, seriesId = item.seriesId, seriesTitle = item.seriesTitle))
                    })
                }
            }
            ActionRow(Icons.Rounded.Share, "Delen", { shareItem(context, item); onDismiss() })
        }
    }
}

/** Kies in welke lijsten een uitzending staat, of maak een nieuwe lijst. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToListSheet(item: SchatkamerItem, library: UserLibrary, onDismiss: () -> Unit) {
    val lists by library.lists.collectAsState()
    val account by library.account.collectAsState()
    var newName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BrandColors.Surface) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp).verticalScroll(rememberScrollState())) {
            Text("Toevoegen aan lijst", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            Text(item.title, style = MaterialTheme.typography.bodyMedium, color = BrandColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(8.dp))
            if (lists.isEmpty() && !creating) {
                Text("Je hebt nog geen lijsten.", color = BrandColors.TextMuted, modifier = Modifier.padding(24.dp, 8.dp))
            }
            lists.forEach { list ->
                val inList = library.isInList(list, item)
                Row(
                    Modifier.fillMaxWidth().clickable { library.toggleInList(list, item) }.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (inList) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null, tint = if (inList) BrandColors.Amber else BrandColors.TextMuted)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(list.name, style = MaterialTheme.typography.bodyLarge)
                        Text("${list.count} ${if (list.count == 1) "uitzending" else "uitzendingen"}", style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted)
                    }
                    Icon(if (list.online) Icons.Rounded.Cloud else Icons.Rounded.PhoneAndroid, if (list.online) "Online" else "Op dit toestel", tint = BrandColors.TextMuted, modifier = Modifier.size(18.dp))
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = BrandColors.Outline)
            if (creating) {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it }, singleLine = true, label = { Text("Naam van de lijst") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandColors.Amber, cursorColor = BrandColors.Amber),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                )
                Text(
                    if (account != null) "Wordt opgeslagen in je Schatkamer-account." else "Wordt opgeslagen op dit toestel.",
                    style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted, modifier = Modifier.padding(24.dp, 6.dp)
                )
                Row(Modifier.padding(horizontal = 16.dp)) {
                    TextButton(onClick = { creating = false }) { Text("Annuleren", color = BrandColors.TextMuted) }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { library.createList(newName, onlineList = account != null, first = item); creating = false; newName = "" },
                        enabled = newName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Amber, contentColor = BrandColors.Ink)
                    ) { Text("Maken en toevoegen") }
                }
            } else {
                ActionRow(Icons.Rounded.Add, "Nieuwe lijst", { creating = true }, BrandColors.Amber)
            }
        }
    }
}

/**
 * Alle filters in één paneel: omroepen en genres (meerdere tegelijk, met
 * aantallen uit de resultaten), een jaarbereik, video/audio, sortering en
 * "ook niet afspeelbaar".
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    filter: SearchFilter,
    genres: List<FacetItem>,
    broadcasterFacets: List<FacetItem>,
    onApply: (SearchFilter) -> Unit,
    onDismiss: () -> Unit,
    lockBroadcaster: Boolean = false
) {
    var draft by remember { mutableStateOf(filter) }
    val thisYear = Calendar.getInstance().get(Calendar.YEAR)
    var years by remember {
        mutableStateOf((draft.effectiveYearFrom ?: 1920).toFloat()..(draft.effectiveYearTo ?: thisYear).toFloat())
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BrandColors.Surface) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { draft = draft.withoutRefinements().copy(broadcasters = if (lockBroadcaster) draft.broadcasters else emptySet()); years = 1920f..thisYear.toFloat() }) {
                    Text("Alles wissen", color = BrandColors.TextMuted)
                }
            }

            SheetSection("Soort")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Alles", { draft = draft.copy(material = null) }, selected = draft.material == null)
                Pill("Video", { draft = draft.copy(material = "video") }, selected = draft.material == "video")
                Pill("Audio", { draft = draft.copy(material = "audio") }, selected = draft.material == "audio")
            }

            SheetSection("Sorteren")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val options = if (draft.query.isBlank()) listOf(SortOrder.NEWEST, SortOrder.OLDEST) else SortOrder.entries
                options.forEach { s ->
                    val selected = draft.sort == s || (s == SortOrder.NEWEST && draft.query.isBlank() && draft.sort == SortOrder.RELEVANCE)
                    Pill(s.label, { draft = draft.copy(sort = s) }, selected = selected)
                }
            }

            SheetSection("Periode: ${years.start.toInt()} – ${years.endInclusive.toInt()}")
            RangeSlider(
                value = years,
                onValueChange = { years = it },
                valueRange = 1920f..thisYear.toFloat(),
                steps = 0,
                onValueChangeFinished = {
                    val from = years.start.toInt(); val to = years.endInclusive.toInt()
                    draft = draft.copy(decade = null, yearFrom = from.takeIf { it > 1920 }, yearTo = to.takeIf { it < thisYear })
                },
                colors = SliderDefaults.colors(thumbColor = BrandColors.Amber, activeTrackColor = BrandColors.Amber, inactiveTrackColor = BrandColors.Outline)
            )

            if (genres.isNotEmpty() || draft.genres.isNotEmpty()) {
                SheetSection("Genres")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val shown = (draft.genres.map { FacetItem(it) } + genres).distinctBy { it.label }.take(30)
                    shown.forEach { g ->
                        val on = g.label in draft.genres
                        Pill(
                            g.label.replaceFirstChar { it.titlecase() } + if (g.count > 0) "  ${countLabel(g.count, "").trim()}" else "",
                            { draft = draft.copy(genres = if (on) draft.genres - g.label else draft.genres + g.label) },
                            selected = on
                        )
                    }
                }
            }

            if (!lockBroadcaster) {
                SheetSection("Omroepen")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SchatkamerCatalogRepository.CURRENT_BROADCASTERS.forEach { b ->
                        val on = b.archiveNames.any { it in draft.broadcasters }
                        val count = b.archiveNames.sumOf { n -> broadcasterFacets.firstOrNull { it.label.equals(n, true) }?.count ?: 0 }
                        Pill(
                            b.name + if (count > 0) "  ${countLabel(count, "").trim()}" else "",
                            { draft = draft.copy(broadcasters = if (on) draft.broadcasters - b.archiveNames.toSet() else draft.broadcasters + b.archiveNames) },
                            selected = on
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ook niet-afspeelbare uitzendingen", style = MaterialTheme.typography.bodyLarge)
                    Text("Metadata uit het archief zonder video of geluid.", style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted)
                }
                Switch(
                    checked = !draft.playableOnly, onCheckedChange = { draft = draft.copy(playableOnly = !it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = BrandColors.Amber, checkedThumbColor = BrandColors.Ink)
                )
            }

            Button(
                onClick = { onApply(draft); onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Amber, contentColor = BrandColors.Ink),
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
            ) { Text("Toon resultaten") }
        }
    }
}

@Composable
private fun SheetSection(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
}
