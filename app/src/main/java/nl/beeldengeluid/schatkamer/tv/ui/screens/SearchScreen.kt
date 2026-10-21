package nl.beeldengeluid.schatkamer.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.beeldengeluid.schatkamer.core.data.SchatkamerCatalogRepository
import nl.beeldengeluid.schatkamer.core.data.SchatkamerItem
import nl.beeldengeluid.schatkamer.tv.ui.components.TvProgramCard
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerBorder
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerCyan
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerDarkElevated
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerTextMuted
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerTextPrimary
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerTextSecondary

@Composable
fun TvSearchScreen(
    catalogRepo: SchatkamerCatalogRepository,
    onSelectItem: (SchatkamerItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SchatkamerItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val presets = listOf("Journaal", "Cabaret", "Muziek", "Van Kooten en De Bie", "Kinderen voor Kinderen", "Swiebertje")

    fun doSearch(term: String) {
        query = term
        searchJob?.cancel()
        if (term.isBlank()) {
            results = emptyList()
            isLoading = false
            return
        }
        searchJob = scope.launch {
            delay(300)
            isLoading = true
            catalogRepo.search(term)
                .onSuccess { results = it.items }
                .onFailure { results = emptyList() }
            isLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "Zoeken in de Schatkamer",
            color = SchatkamerTextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // TV Zoekbalk
        OutlinedTextField(
            value = query,
            onValueChange = { doSearch(it) },
            placeholder = { Text("Typ een titel, thema of omroep...", color = SchatkamerTextMuted, fontSize = 16.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SchatkamerCyan) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { doSearch("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Wissen", tint = SchatkamerTextSecondary)
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
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Preset chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(presets) { p ->
                SuggestionChip(
                    onClick = { doSearch(p) },
                    label = { Text(p, fontSize = 13.sp) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = SchatkamerDarkElevated,
                        labelColor = SchatkamerTextPrimary
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = SchatkamerBorder
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SchatkamerCyan)
            }
        } else if (results.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 200.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 40.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(results, key = { it.id }) { item ->
                    TvProgramCard(
                        item = item,
                        onClick = { onSelectItem(item) }
                    )
                }
            }
        } else if (query.isNotBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Geen programma's gevonden voor \"$query\"", color = SchatkamerTextSecondary)
            }
        }
    }
}
