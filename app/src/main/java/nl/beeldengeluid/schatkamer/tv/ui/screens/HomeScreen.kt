package nl.beeldengeluid.schatkamer.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.beeldengeluid.schatkamer.core.data.SchatkamerCatalogRepository
import nl.beeldengeluid.schatkamer.core.data.SchatkamerHome
import nl.beeldengeluid.schatkamer.core.data.SchatkamerItem
import nl.beeldengeluid.schatkamer.tv.ui.components.TvProgramCard
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerCyan
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerTextPrimary
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerTextSecondary

@Composable
fun TvHomeScreen(
    catalogRepo: SchatkamerCatalogRepository,
    onSelectItem: (SchatkamerItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var homeData by remember { mutableStateOf<SchatkamerHome?>(catalogRepo.cachedHome()) }
    var isLoading by remember { mutableStateOf(homeData == null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        catalogRepo.getHome(forceRefresh = false)
            .onSuccess {
                homeData = it
                isLoading = false
            }
            .onFailure {
                if (homeData == null) {
                    errorMessage = it.localizedMessage ?: "Fout bij laden van de Schatkamer"
                }
                isLoading = false
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (isLoading && homeData == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SchatkamerCyan)
            }
        } else if (errorMessage != null && homeData == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(errorMessage!!, color = SchatkamerTextSecondary, fontSize = 16.sp)
            }
        } else {
            val home = homeData ?: return@Box
            LazyColumn(
                contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(home.rows, key = { it.id }) { row ->
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        Text(
                            text = row.title,
                            color = SchatkamerTextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(row.items, key = { it.id }) { item ->
                                TvProgramCard(
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
