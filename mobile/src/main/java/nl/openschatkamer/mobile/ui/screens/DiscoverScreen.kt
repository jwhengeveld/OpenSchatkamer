package nl.openschatkamer.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import nl.openschatkamer.brand.BrandColors
import nl.openschatkamer.brand.BrandFonts
import nl.openschatkamer.core.data.Decade
import nl.openschatkamer.core.ui.BrowseSpec
import nl.openschatkamer.core.ui.DiscoverViewModel
import nl.openschatkamer.mobile.ui.components.BroadcasterTile
import nl.openschatkamer.mobile.ui.components.CardShape
import nl.openschatkamer.mobile.ui.components.Pill
import nl.openschatkamer.mobile.ui.components.PersonCard
import nl.openschatkamer.mobile.ui.components.ScreenTitle
import nl.openschatkamer.mobile.ui.components.SectionHeader

/** Bladeren zonder zoekterm: per omroep, per tijdperk en per genre. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoverScreen(
    viewModel: DiscoverViewModel,
    onBrowse: (BrowseSpec) -> Unit,
    contentPadding: PaddingValues
) {
    val data by viewModel.data.collectAsState()

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item { ScreenTitle("Ontdekken", Modifier.windowInsetsPadding(WindowInsets.statusBars)) }

        item {
            SectionHeader("Door de tijd")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(Decade.entries.reversed()) { decade ->
                    DecadeTile(decade) { onBrowse(BrowseSpec(title = decade.label.substringBefore(" ("), decade = decade)) }
                }
            }
        }

        item { SectionHeader("Omroepen") }
        val rows = data.broadcasters.chunked(2)
        items(rows.size) { i ->
            androidx.compose.foundation.layout.Row(
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rows[i].forEach { b ->
                    BroadcasterTile(b.name, b.logoUrl, { onBrowse(BrowseSpec(title = b.name, broadcaster = b.name)) }, Modifier.weight(1f), b.count)
                }
                if (rows[i].size == 1) Box(Modifier.weight(1f))
            }
        }

        if (data.people.isNotEmpty()) {
            item {
                SectionHeader("Personen")
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(data.people, key = { it.id }) { p -> PersonCard(p, { onBrowse(BrowseSpec(title = p.title, person = p.title)) }) }
                }
            }
        }
        if (data.subjects.isNotEmpty()) {
            item {
                SectionHeader("Onderwerpen")
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.subjects.take(30).forEach { sub ->
                        val title = sub.label.replaceFirstChar { it.titlecase() }
                        Pill(title, { onBrowse(BrowseSpec(title = title, subject = sub.label)) })
                    }
                }
            }
        }
        if (data.genres.isNotEmpty()) {
            item {
                SectionHeader("Genres")
                FlowRow(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    data.genres.take(36).forEach { g ->
                        Pill(g.label.replaceFirstChar { it.titlecase() }, { onBrowse(BrowseSpec(title = g.label.replaceFirstChar { it.titlecase() }, genre = g.label)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DecadeTile(decade: Decade, onClick: () -> Unit) {
    // Elk decennium een eigen tint, van sepia (oud) naar helder amber (nu).
    val t = decade.ordinal / (Decade.entries.size - 1f)
    val tone = androidx.compose.ui.graphics.lerp(Color(0xFF5B4632), BrandColors.Amber, t)
    Box(
        Modifier
            .width(128.dp)
            .aspectRatio(1.15f)
            .clip(CardShape)
            .background(Brush.linearGradient(listOf(tone, BrandColors.SurfaceHigh)))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Text(
            "${decade.from}",
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = BrandFonts.Display),
            color = BrandColors.Text,
            modifier = Modifier.align(Alignment.TopStart)
        )
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(decade.label.substringBefore(" ("), style = MaterialTheme.typography.labelMedium, color = BrandColors.Text.copy(alpha = 0.85f))
        }
    }
}
