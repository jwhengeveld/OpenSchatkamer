package nl.openschatkamer.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import nl.openschatkamer.brand.BrandColors
import nl.openschatkamer.core.data.SchatkamerItem
import nl.openschatkamer.core.ui.BrowseSpec
import nl.openschatkamer.core.ui.KidsViewModel
import nl.openschatkamer.core.ui.Load
import nl.openschatkamer.mobile.ui.components.CatalogRow
import nl.openschatkamer.mobile.ui.components.ErrorState
import nl.openschatkamer.mobile.ui.components.ScreenTitle
import nl.openschatkamer.mobile.ui.components.SectionHeader
import nl.openschatkamer.mobile.ui.components.SkeletonRows

/** Kinderprogramma's: klassieke series, poppenspel, jeugddrama, Jeugdjournaal en Het Klokhuis. */
@Composable
fun KidsScreen(
    viewModel: KidsViewModel,
    onOpen: (SchatkamerItem) -> Unit,
    onBrowse: (BrowseSpec) -> Unit,
    contentPadding: PaddingValues
) {
    val rows by viewModel.rows.collectAsState()
    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(BrandColors.Amber.copy(alpha = 0.18f), BrandColors.Ink)))
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                ScreenTitle("Kids")
                Text(
                    "Sesamstraat, Swiebertje, Het Klokhuis en meer uit het archief.",
                    style = MaterialTheme.typography.bodyMedium, color = BrandColors.TextMuted,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
                )
            }
        }
        when (val r = rows) {
            Load.Loading -> item { SkeletonRows() }
            is Load.Failed -> item { ErrorState(r.message, viewModel::load) }
            is Load.Ready -> items(r.data, key = { it.row.id }) { row ->
                val more = row.more
                if (more != null) {
                    Column {
                        SectionHeader(row.row.title, actionLabel = "Kijk meer", onAction = { onBrowse(more) })
                        CatalogRow(row.row, onOpen, showHeader = false)
                    }
                } else CatalogRow(row.row, onOpen)
            }
        }
    }
}
