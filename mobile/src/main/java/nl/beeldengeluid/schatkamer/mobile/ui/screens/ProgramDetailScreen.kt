package nl.beeldengeluid.schatkamer.mobile.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import nl.beeldengeluid.schatkamer.core.data.PlayableMedia
import nl.beeldengeluid.schatkamer.core.data.SchatkamerItem
import nl.beeldengeluid.schatkamer.core.data.SchatkamerProgramDetail
import nl.beeldengeluid.schatkamer.mobile.cast.CastButton
import nl.beeldengeluid.schatkamer.mobile.ui.DetailUiState
import nl.beeldengeluid.schatkamer.mobile.ui.ProgramDetailViewModel
import nl.beeldengeluid.schatkamer.mobile.ui.components.ProgramCard
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerBorder
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerCyan
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerDarkBackground
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerDarkElevated
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerMagenta
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerTextMuted
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerTextPrimary
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramDetailScreen(
    item: SchatkamerItem,
    viewModel: ProgramDetailViewModel,
    onBack: () -> Unit,
    onPlay: (PlayableMedia) -> Unit,
    onSelectEpisode: (SchatkamerItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(item.id) {
        viewModel.load(item.url.ifBlank { item.id }, item.id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Terug", tint = Color.White)
                    }
                },
                actions = {
                    CastButton(modifier = Modifier.padding(end = 8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = SchatkamerDarkBackground,
        modifier = modifier
    ) { padding ->
        when (val s = state) {
            is DetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SchatkamerCyan)
                }
            }
            is DetailUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(s.message, color = SchatkamerTextSecondary)
                }
            }
            is DetailUiState.Success -> {
                val detail = s.detail
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header / Poster backdrop
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                        ) {
                            val img = detail.imageUrl.ifBlank { item.imageUrl }
                            if (img.isNotBlank()) {
                                AsyncImage(
                                    model = img,
                                    contentDescription = detail.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.5f),
                                                Color.Transparent,
                                                SchatkamerDarkBackground
                                            )
                                        )
                                    )
                            )
                        }
                    }

                    // Content sectie
                    item {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .offset(y = (-20).dp)
                        ) {
                            val seriesTitle = detail.seriesTitle
                            if (seriesTitle != null) {
                                Text(
                                    text = seriesTitle.uppercase(),
                                    color = SchatkamerCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                            }

                            Text(
                                text = detail.title.ifBlank { item.title },
                                color = SchatkamerTextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (detail.broadcaster.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .background(SchatkamerMagenta, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(detail.broadcaster, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (detail.date.isNotBlank()) {
                                    Text(detail.date, color = SchatkamerTextMuted, fontSize = 12.sp)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (detail.mediaType == "audio") Icons.Default.Headphones else Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = SchatkamerCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (detail.mediaType == "audio") "Radio / Audio" else "Televisie",
                                        color = SchatkamerTextMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Knoppen: Kijken / Luisteren, Bewaren, Delen
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val stream = detail.streamInfo
                                if (stream != null) {
                                    Button(
                                        onClick = {
                                            onPlay(
                                                PlayableMedia(
                                                    title = detail.title,
                                                    subtitle = detail.seriesTitle ?: detail.broadcaster,
                                                    streamUrl = stream.streamUrl,
                                                    posterUrl = stream.posterUrl.ifBlank { detail.imageUrl },
                                                    id = detail.id,
                                                    seriesId = detail.seriesId.orEmpty(),
                                                    mediaType = detail.mediaType,
                                                    widevineLicenseUrl = stream.widevineLicenseUrl,
                                                    subtitlesUrl = stream.subtitlesUrl,
                                                    cookieHeader = stream.cookieHeader
                                                )
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = SchatkamerCyan,
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (detail.mediaType == "audio") "Luisteren" else "Kijken", fontWeight = FontWeight.Bold)
                                    }
                                }

                                OutlinedButton(
                                    onClick = { viewModel.toggleFavorite(item) },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (detail.isFavorite) SchatkamerMagenta else SchatkamerTextPrimary
                                    ),
                                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                        brush = Brush.linearGradient(
                                            listOf(if (detail.isFavorite) SchatkamerMagenta else SchatkamerBorder, if (detail.isFavorite) SchatkamerMagenta else SchatkamerBorder)
                                        )
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = if (detail.isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (detail.isFavorite) "Bewaard" else "Bewaren")
                                }

                                IconButton(
                                    onClick = {
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, "${detail.title} - Kijk in De Schatkamer: ${item.url}")
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Deel programma"))
                                    }
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Delen", tint = SchatkamerTextPrimary)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Beschrijving
                            if (detail.description.isNotBlank()) {
                                Text(
                                    text = detail.description,
                                    color = SchatkamerTextSecondary,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            // Presentatoren & Regie
                            if (detail.presenters.isNotEmpty()) {
                                Text(
                                    text = "Presentatie: ${detail.presenters.joinToString(", ")}",
                                    color = SchatkamerTextMuted,
                                    fontSize = 12.sp
                                )
                            }
                            if (detail.directors.isNotEmpty()) {
                                Text(
                                    text = "Regie: ${detail.directors.joinToString(", ")}",
                                    color = SchatkamerTextMuted,
                                    fontSize = 12.sp
                                )
                            }

                            // Onderwerpen tags
                            if (detail.subjects.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(detail.subjects) { sub ->
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text(sub, fontSize = 11.sp) },
                                            colors = SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = SchatkamerDarkElevated,
                                                labelColor = SchatkamerTextSecondary
                                            ),
                                            border = SuggestionChipDefaults.suggestionChipBorder(
                                                enabled = true,
                                                borderColor = SchatkamerBorder
                                            )
                                        )
                                    }
                                }
                            }

                            // Out of commerce toelichting
                            if (detail.legalBasisText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = detail.legalBasisText,
                                    color = SchatkamerTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Afleveringen carrousel
                    if (detail.episodes.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)) {
                                Text(
                                    text = "Afleveringen in deze serie",
                                    color = SchatkamerTextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(detail.episodes, key = { it.id }) { ep ->
                                        ProgramCard(
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
}
