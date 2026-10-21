package nl.beeldengeluid.schatkamer.tv.ui.screens

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import nl.beeldengeluid.schatkamer.core.data.FavoritesRepository
import nl.beeldengeluid.schatkamer.core.data.PlayableMedia
import nl.beeldengeluid.schatkamer.core.data.SchatkamerItem
import nl.beeldengeluid.schatkamer.core.data.SchatkamerProgramDetail
import nl.beeldengeluid.schatkamer.core.data.SchatkamerVideoRepository
import nl.beeldengeluid.schatkamer.tv.ui.components.TvProgramCard
import nl.beeldengeluid.schatkamer.tv.ui.theme.FocusBorderCyan
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerBorder
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerCyan
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerDarkBackground
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerDarkElevated
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerMagenta
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerTextMuted
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerTextPrimary
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerTextSecondary

@Composable
fun TvProgramDetailScreen(
    item: SchatkamerItem,
    videoRepo: SchatkamerVideoRepository,
    favoritesRepo: FavoritesRepository,
    onBack: () -> Unit,
    onPlay: (PlayableMedia) -> Unit,
    onSelectEpisode: (SchatkamerItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var detail by remember { mutableStateOf<SchatkamerProgramDetail?>(videoRepo.cachedDetail(item.id)) }
    var isLoading by remember { mutableStateOf(detail == null) }
    var isFav by remember { mutableStateOf(favoritesRepo.isFavorite(item.id)) }

    LaunchedEffect(item.id) {
        videoRepo.getProgramDetail(item.url.ifBlank { item.id }, item.id)
            .onSuccess {
                detail = it
                isLoading = false
            }
            .onFailure {
                isLoading = false
            }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SchatkamerDarkBackground)
    ) {
        // Achtergrond poster met zachte gradient
        val poster = detail?.imageUrl?.ifBlank { item.imageUrl } ?: item.imageUrl
        if (poster.isNotBlank()) {
            AsyncImage(
                model = poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                SchatkamerDarkBackground,
                                SchatkamerDarkBackground.copy(alpha = 0.95f),
                                SchatkamerDarkBackground.copy(alpha = 0.75f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        if (isLoading && detail == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SchatkamerCyan)
            }
        } else {
            val d = detail
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 32.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Column(modifier = Modifier.fillMaxWidth(0.65f)) {
                        val sTitle = d?.seriesTitle
                        if (sTitle != null) {
                            Text(
                                text = sTitle.uppercase(),
                                color = SchatkamerCyan,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        Text(
                            text = d?.title?.ifBlank { item.title } ?: item.title,
                            color = SchatkamerTextPrimary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 38.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val broadcaster = d?.broadcaster?.ifBlank { item.broadcaster } ?: item.broadcaster
                            if (broadcaster.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .background(SchatkamerMagenta, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(broadcaster, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            val date = d?.date?.ifBlank { item.date } ?: item.date
                            if (date.isNotBlank()) {
                                Text(date, color = SchatkamerTextMuted, fontSize = 14.sp)
                            }
                            Text(
                                text = if (d?.mediaType == "audio") "Radio / Audio" else "Televisie",
                                color = SchatkamerTextMuted,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Actieknoppen voor TV
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            val stream = d?.streamInfo
                            if (stream != null) {
                                TvActionButton(
                                    text = if (d.mediaType == "audio") "Luisteren" else "Kijken",
                                    icon = Icons.Default.PlayArrow,
                                    isPrimary = true,
                                    onClick = {
                                        onPlay(
                                            PlayableMedia(
                                                title = d.title,
                                                subtitle = d.seriesTitle ?: d.broadcaster,
                                                streamUrl = stream.streamUrl,
                                                posterUrl = stream.posterUrl.ifBlank { d.imageUrl },
                                                id = d.id,
                                                seriesId = d.seriesId.orEmpty(),
                                                mediaType = d.mediaType,
                                                widevineLicenseUrl = stream.widevineLicenseUrl,
                                                subtitlesUrl = stream.subtitlesUrl,
                                                cookieHeader = stream.cookieHeader
                                            )
                                        )
                                    }
                                )
                            }

                            TvActionButton(
                                text = if (isFav) "In Mijn Lijst" else "Bewaren",
                                icon = if (isFav) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                isPrimary = false,
                                onClick = {
                                    isFav = favoritesRepo.toggleFavorite(item)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        val desc = d?.description?.ifBlank { item.description } ?: item.description
                        if (desc.isNotBlank()) {
                            Text(
                                text = desc,
                                color = SchatkamerTextSecondary,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            )
                        }

                        if (!d?.presenters.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Presentatie: ${d!!.presenters.joinToString(", ")}",
                                color = SchatkamerTextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Afleveringen rij
                if (!d?.episodes.isNullOrEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(top = 28.dp)) {
                            Text(
                                text = "Afleveringen in deze serie",
                                color = SchatkamerTextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(d!!.episodes, key = { it.id }) { ep ->
                                    TvProgramCard(
                                        item = ep,
                                        onClick = { onSelectEpisode(ep) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPrimary: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        label = "btn_scale"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isFocused) SchatkamerCyan else if (isPrimary) SchatkamerDarkElevated else Color.Transparent
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else if (isPrimary) SchatkamerCyan else SchatkamerBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isFocused) Color.Black else SchatkamerTextPrimary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = if (isFocused) Color.Black else SchatkamerTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
