package nl.beeldengeluid.schatkamer.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import nl.beeldengeluid.schatkamer.mobile.cast.GoogleCastButton
import nl.beeldengeluid.schatkamer.mobile.playback.PlaybackManager
import nl.beeldengeluid.schatkamer.mobile.playback.PlaybackState
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerBorder
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerCyan
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerDarkBackground
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerDarkElevated
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerMagenta
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerTextMuted
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerTextPrimary

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

/**
 * Volledig afstandsbedieningsscherm tijdens Google Cast / Chromecast weergave,
 * gebaseerd op het beproefde Family7 Cast-design.
 */
@Composable
fun CastController(
    state: PlaybackState,
    playback: PlaybackManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val media = state.media

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SchatkamerDarkBackground)
    ) {
        // Vervaagde achtergrondposter
        if (!media?.posterUrl.isNullOrBlank()) {
            AsyncImage(
                model = media?.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(45.dp)
                    .alpha(0.35f)
            )
        }

        // Donker verloop over de achtergrond
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to SchatkamerDarkBackground.copy(alpha = 0.6f),
                        0.45f to SchatkamerDarkBackground.copy(alpha = 0.88f),
                        1f to SchatkamerDarkBackground
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // Bovenbalk: Sluiten, Verbonden Apparaat Pill, en Cast-knop
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Sluiten",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Apparaat-indicator pill
                DevicePill(deviceName = state.castDeviceName)

                Spacer(modifier = Modifier.weight(1f))

                GoogleCastButton()
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Gecentreerde inhoud
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                // Poster kaart
                Box(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .shadow(16.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(SchatkamerDarkElevated),
                    contentAlignment = Alignment.Center
                ) {
                    if (!media?.posterUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = media?.posterUrl,
                            contentDescription = media?.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (state.isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = SchatkamerCyan,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Titel
                Text(
                    text = media?.title ?: "Geen programma geselecteerd",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = SchatkamerTextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!media?.subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = media?.subtitle.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SchatkamerTextMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Tijdbalk / Scrubber
                var draggingFraction by remember { mutableStateOf<Float?>(null) }
                val currentFraction = if (state.durationMs > 0) {
                    (state.currentPositionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f
                val shownFraction = draggingFraction ?: currentFraction

                Slider(
                    value = shownFraction,
                    onValueChange = { draggingFraction = it },
                    onValueChangeFinished = {
                        draggingFraction?.let { fraction ->
                            val targetMs = (fraction * state.durationMs).toLong()
                            playback.seekTo(targetMs)
                        }
                        draggingFraction = null
                    },
                    enabled = state.durationMs > 0,
                    colors = SliderDefaults.colors(
                        thumbColor = SchatkamerCyan,
                        activeTrackColor = SchatkamerCyan,
                        inactiveTrackColor = SchatkamerBorder
                    ),
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .semantics { contentDescription = "Tijdbalk" }
                )

                Row(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    val currentPos = (shownFraction * state.durationMs).toLong()
                    Text(
                        text = formatTime(if (state.durationMs > 0) currentPos else state.currentPositionMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = SchatkamerTextMuted
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (state.durationMs > 0) formatTime(state.durationMs) else "--:--",
                        style = MaterialTheme.typography.labelMedium,
                        color = SchatkamerTextMuted
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Afspeelknoppen: 10s terug, Play/Pause, 30s vooruit
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IconButton(
                        onClick = { playback.seekBy(-10_000L) },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "10 seconden terug",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(SatkamerCyanButton)
                            .clickable { playback.togglePlayPause() }
                            .semantics { contentDescription = if (state.isPlaying) "Pauzeren" else "Afspelen" }
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    IconButton(
                        onClick = { playback.seekBy(30_000L) },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward30,
                            contentDescription = "30 seconden vooruit",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // TV Volume Slider
                CastVolumeRow(state = state, playback = playback)

                Spacer(modifier = Modifier.height(24.dp))

                // Foutmelding weergave indien aanwezig
                if (state.error != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = SchatkamerMagenta, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = state.error.orEmpty(),
                                color = SchatkamerTextMuted,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { playback.retry() },
                            colors = ButtonDefaults.buttonColors(containerColor = SchatkamerCyan, contentColor = Color.Black)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Opnieuw proberen", fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Op telefoon kijken knop
                OutlinedButton(
                    onClick = { playback.stopCasting() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Op telefoon kijken", fontWeight = FontWeight.Medium)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

private val SatkamerCyanButton: Color = Color(0xFF00E5FF)

@Composable
private fun DevicePill(deviceName: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CastConnected,
            contentDescription = null,
            tint = SchatkamerCyan,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = deviceName ?: "Chromecast",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CastVolumeRow(state: PlaybackState, playback: PlaybackManager) {
    var draggingVolume by remember { mutableStateOf<Float?>(null) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        IconButton(onClick = { playback.toggleCastMute() }) {
            Icon(
                imageVector = if (state.castMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = if (state.castMuted) "Dempen opheffen" else "Dempen",
                tint = if (state.castMuted) SchatkamerMagenta else SchatkamerTextMuted,
                modifier = Modifier.size(24.dp)
            )
        }
        Slider(
            value = draggingVolume ?: state.castVolume,
            onValueChange = {
                draggingVolume = it
                playback.setCastVolume(it)
            },
            onValueChangeFinished = { draggingVolume = null },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = SchatkamerBorder
            ),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "Volume van de tv" }
        )
    }
}
