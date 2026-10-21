package nl.beeldengeluid.schatkamer.tv.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import nl.beeldengeluid.schatkamer.core.data.BroadcasterItem
import nl.beeldengeluid.schatkamer.core.data.GenreItem
import nl.beeldengeluid.schatkamer.core.data.SchatkamerCatalogRepository
import nl.beeldengeluid.schatkamer.core.data.SchatkamerItem
import nl.beeldengeluid.schatkamer.tv.ui.components.TvProgramCard
import nl.beeldengeluid.schatkamer.tv.ui.theme.FocusBorderCyan
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerBorder
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerCyan
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerDarkElevated
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerTextPrimary
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerTextSecondary

@Composable
fun TvDiscoverScreen(
    catalogRepo: SchatkamerCatalogRepository,
    onSelectItem: (SchatkamerItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val broadcasters = remember { catalogRepo.getBroadcasters() }
    val genres = remember { catalogRepo.getGenres() }

    var selectedTitle by remember { mutableStateOf<String?>(null) }
    var selectedItems by remember { mutableStateOf<List<SchatkamerItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    fun loadBroadcaster(b: BroadcasterItem) {
        selectedTitle = b.name
        scope.launch {
            isLoading = true
            catalogRepo.getProgramsForBroadcaster(b.id, b.slug)
                .onSuccess { selectedItems = it.items }
                .onFailure { selectedItems = emptyList() }
            isLoading = false
        }
    }

    fun loadGenre(g: GenreItem) {
        selectedTitle = g.title
        scope.launch {
            isLoading = true
            catalogRepo.getProgramsForGenre(g.slug)
                .onSuccess { selectedItems = it.items }
                .onFailure { selectedItems = emptyList() }
            isLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = selectedTitle ?: "Ontdekken op omroep en genre",
            color = SchatkamerTextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedTitle != null) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SchatkamerCyan)
                }
            } else if (selectedItems.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 200.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 40.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(selectedItems, key = { it.id }) { item ->
                        TvProgramCard(item = item, onClick = { onSelectItem(item) })
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Geen programma's gevonden.", color = SchatkamerTextSecondary)
                }
            }
        } else {
            // Omroepen rij
            Text(
                text = "Publieke Omroepen",
                color = SchatkamerCyan,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(broadcasters, key = { it.id }) { b ->
                    TvCategoryCard(
                        title = b.name,
                        imageUrl = b.logoUrl,
                        onClick = { loadBroadcaster(b) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Genres rij
            Text(
                text = "Tv-genres uit 75 jaar geschiedenis",
                color = SchatkamerCyan,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(genres, key = { it.slug }) { g ->
                    TvCategoryCard(
                        title = g.title,
                        imageUrl = g.imageUrl,
                        onClick = { loadGenre(g) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TvCategoryCard(
    title: String,
    imageUrl: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = tween(150),
        label = "tv_cat_scale"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .aspectRatio(16f / 9f)
            .height(110.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SchatkamerDarkElevated)
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) FocusBorderCyan else SchatkamerBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
    ) {
        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            )
        }

        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        )
    }
}
