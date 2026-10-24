package nl.openschatkamer.mobile.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.openschatkamer.brand.BrandColors
import nl.openschatkamer.core.update.UpdateViewModel
import nl.openschatkamer.core.update.UpdateViewModel.State

/** Venster voor een nieuwe versie uit de GitHub-releases. */
@Composable
fun UpdateDialog(viewModel: UpdateViewModel) {
    val state by viewModel.state.collectAsState()
    when (val s = state) {
        State.Idle -> Unit
        State.Checking -> AlertDialog(
            onDismissRequest = viewModel::dismiss, containerColor = BrandColors.Surface,
            title = { Text("Zoeken naar updates…") },
            text = { LinearProgressIndicator(color = BrandColors.Amber, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {}
        )
        State.UpToDate -> AlertDialog(
            onDismissRequest = viewModel::dismiss, containerColor = BrandColors.Surface,
            title = { Text("Je hebt de nieuwste versie") },
            confirmButton = { TextButton(onClick = viewModel::dismiss) { Text("Oké", color = BrandColors.Amber) } }
        )
        is State.Available -> AlertDialog(
            onDismissRequest = viewModel::later, containerColor = BrandColors.Surface,
            title = { Text("Versie ${s.release.version} is er") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text(s.release.notes.replace("**", "").replace("### ", "").replace("## ", ""), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = viewModel::install) { Text("Bijwerken", color = BrandColors.Amber) } },
            dismissButton = { TextButton(onClick = viewModel::later) { Text("Later", color = BrandColors.TextMuted) } }
        )
        is State.Downloading -> AlertDialog(
            onDismissRequest = {}, containerColor = BrandColors.Surface,
            title = { Text("Versie ${s.release.version} downloaden") },
            text = {
                LinearProgressIndicator(
                    progress = { s.progress }, color = BrandColors.Amber, trackColor = BrandColors.SurfaceHigh,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            },
            confirmButton = {}
        )
        is State.Installing -> AlertDialog(
            onDismissRequest = viewModel::dismiss, containerColor = BrandColors.Surface,
            title = { Text("Bijna klaar") },
            text = { Text("Bevestig de installatie in het venster van Android. Vraagt Android om toestemming voor onbekende apps, sta die dan toe voor OpenSchatkamer.") },
            confirmButton = { TextButton(onClick = viewModel::dismiss) { Text("Oké", color = BrandColors.Amber) } }
        )
        is State.Failed -> AlertDialog(
            onDismissRequest = viewModel::dismiss, containerColor = BrandColors.Surface,
            title = { Text("Bijwerken lukte niet") },
            text = { Text(s.message) },
            confirmButton = { if (s.release != null) TextButton(onClick = viewModel::install) { Text("Opnieuw", color = BrandColors.Amber) } },
            dismissButton = { TextButton(onClick = viewModel::dismiss) { Text("Sluiten", color = BrandColors.TextMuted) } }
        )
    }
}
