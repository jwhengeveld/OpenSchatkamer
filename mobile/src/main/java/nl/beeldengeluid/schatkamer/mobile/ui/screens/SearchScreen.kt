package nl.beeldengeluid.schatkamer.mobile.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.beeldengeluid.schatkamer.core.data.SchatkamerItem
import nl.beeldengeluid.schatkamer.mobile.cast.CastButtonRow
import nl.beeldengeluid.schatkamer.mobile.ui.SearchUiState
import nl.beeldengeluid.schatkamer.mobile.ui.SearchViewModel
import nl.beeldengeluid.schatkamer.mobile.ui.components.ProgramCard
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerBorder
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerCyan
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerDarkBackground
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerDarkElevated
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerTextMuted
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerTextPrimary
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onSelectItem: (SchatkamerItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val query by viewModel.query.collectAsState()
    val state by viewModel.uiState.collectAsState()

    BackHandler(enabled = query.isNotBlank()) {
        viewModel.onQueryChanged("")
    }

    val suggestions = listOf("EO", "Polygoon", "Journaal", "Cabaret", "Van Kooten en De Bie", "Kinderen voor Kinderen")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zoeken in het archief", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                actions = { CastButtonRow(modifier = Modifier.padding(end = 6.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SchatkamerDarkBackground)
            )
        },
        containerColor = SchatkamerDarkBackground,
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Zoekbalk
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.onQueryChanged(it) },
                placeholder = { Text("Zoek op titel, presentator, onderwerp...", color = SchatkamerTextMuted, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SchatkamerCyan) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChanged("") }) {
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

            // Suggesties als zoekbalk leeg is
            if (query.isBlank()) {
                Text(
                    text = "Populaire zoekopdrachten",
                    color = SchatkamerTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestions.take(3).forEach { s ->
                        SuggestionChip(
                            onClick = { viewModel.onQueryChanged(s) },
                            label = { Text(s, fontSize = 12.sp) },
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
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (val s = state) {
                is SearchUiState.Idle -> {
                    if (query.isNotBlank()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Druk op zoeken", color = SchatkamerTextMuted)
                        }
                    }
                }
                is SearchUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SchatkamerCyan)
                    }
                }
                is SearchUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(s.message, color = SchatkamerTextSecondary)
                    }
                }
                is SearchUiState.Success -> {
                    if (s.result.items.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Geen programma's gevonden voor \"${s.result.query}\".", color = SchatkamerTextSecondary)
                        }
                    } else {
                        Text(
                            text = "${s.result.items.size} programma's gevonden",
                            color = SchatkamerTextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 32.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(s.result.items, key = { it.id }) { item ->
                                ProgramCard(
                                    item = item,
                                    onClick = { onSelectItem(item) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
