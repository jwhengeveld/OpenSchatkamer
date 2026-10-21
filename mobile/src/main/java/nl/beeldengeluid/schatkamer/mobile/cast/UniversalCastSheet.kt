package nl.beeldengeluid.schatkamer.mobile.cast

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.Airplay
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.beeldengeluid.schatkamer.core.cast.CastDevice
import nl.beeldengeluid.schatkamer.core.cast.ProtocolType
import nl.beeldengeluid.schatkamer.mobile.cast.miracast.MiracastLauncher
import nl.beeldengeluid.schatkamer.mobile.playback.PlaybackManager
import nl.beeldengeluid.schatkamer.mobile.playback.PlaybackState
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerCyan
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerDarkBackground
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerDarkElevated
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerMagenta
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerTextMuted
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalCastSheet(
    playbackManager: PlaybackManager,
    playbackState: PlaybackState,
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val context = LocalContext.current
    val discovery = playbackManager.discoveryManager
    val devices by discovery.discoveredDevices.collectAsState()
    val isScanning by discovery.isScanning.collectAsState()

    DisposableEffect(Unit) {
        discovery.startScanning()
        onDispose {
            discovery.stopScanning()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = SchatkamerDarkBackground,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Titelbalk
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Verbinden & Casten",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Kies hoe je wilt kijken op je televisie",
                        fontSize = 13.sp,
                        color = SchatkamerTextMuted
                    )
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, contentDescription = "Sluiten", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Actieve Cast Banner als er een verbinding actief is
            if (playbackState.isCasting) {
                ActiveCastBanner(
                    deviceName = playbackState.castDeviceName ?: "TV",
                    protocol = playbackState.castProtocol?.displayName ?: "Google Cast",
                    onDisconnect = {
                        playbackManager.disconnectRemoteCast(resumeLocal = true)
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Sectie 1: Draadloos Scherm (Miracast / Smart View / Cast Screen)
            MiracastCard(
                onOpen = {
                    MiracastLauncher.launchWirelessDisplaySettings(context)
                    onDismissRequest()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Sectie 2: Beschikbare Smart TV's & AirPlay (mDNS)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Gevonden TV's & AirPlay",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                if (isScanning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = SchatkamerCyan,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Zoeken...", fontSize = 12.sp, color = SchatkamerCyan)
                    }
                } else {
                    IconButton(
                        onClick = { discovery.startScanning() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Opnieuw zoeken",
                            tint = SchatkamerCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (devices.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = SchatkamerDarkElevated
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isScanning) "Zoeken naar Apple TV's en Schatkamer TV's..." else "Geen AirPlay apparaten gevonden op dit Wi-Fi netwerk.",
                            fontSize = 13.sp,
                            color = SchatkamerTextMuted
                        )
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    devices.forEach { device ->
                        DiscoveredDeviceItem(
                            device = device,
                            isSelected = playbackState.isCasting && playbackState.castDeviceName == device.name,
                            onClick = {
                                playbackManager.castToDevice(device)
                                onDismissRequest()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sectie 3: Google Cast (Chromecast)
            GoogleCastSection()
        }
    }
}

@Composable
private fun ActiveCastBanner(
    deviceName: String,
    protocol: String,
    onDisconnect: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SchatkamerMagenta.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, SchatkamerMagenta)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SchatkamerMagenta.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CastConnected,
                        contentDescription = null,
                        tint = SchatkamerMagenta,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = deviceName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Text(
                        text = "Verbonden via $protocol",
                        fontSize = 12.sp,
                        color = SchatkamerCyan
                    )
                }
            }

            OutlinedButton(
                onClick = onDisconnect,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("Ontkoppelen", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MiracastCard(
    onOpen: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        color = SchatkamerDarkElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, SchatkamerCyan.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SchatkamerCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ScreenShare,
                            contentDescription = null,
                            tint = SchatkamerCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Draadloos Scherm",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SchatkamerCyan.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "AANBEVOLEN",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SchatkamerCyan,
                                    maxLines = 1
                                )
                            }
                        }
                        Text(
                            text = "Miracast · Smart View",
                            fontSize = 11.sp,
                            color = SchatkamerCyan
                        )
                    }
                }

                Button(
                    onClick = onOpen,
                    colors = ButtonDefaults.buttonColors(containerColor = SchatkamerCyan),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Openen", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Beste keuze voor video met DRM: streamt het beeldscherm native naar je TV.",
                fontSize = 12.sp,
                color = SchatkamerTextMuted
            )
        }
    }
}

@Composable
private fun DiscoveredDeviceItem(
    device: CastDevice,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val icon = when (device.protocol) {
        ProtocolType.AIRPLAY -> Icons.Default.Airplay
        ProtocolType.SCHATKAMER_TV -> Icons.Default.Tv
        else -> Icons.Default.Cast
    }

    val badgeColor = when (device.protocol) {
        ProtocolType.AIRPLAY -> SchatkamerYellow
        ProtocolType.SCHATKAMER_TV -> SchatkamerCyan
        else -> SchatkamerMagenta
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) SchatkamerMagenta.copy(alpha = 0.2f) else SchatkamerDarkElevated,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) SchatkamerMagenta else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(badgeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.White
                )
                Text(
                    text = device.model ?: device.protocol.displayName,
                    fontSize = 11.sp,
                    color = SchatkamerTextMuted
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (device.protocol == ProtocolType.AIRPLAY) "AirPlay" else "Schatkamer TV",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
            }
        }
    }
}

@Composable
private fun GoogleCastSection() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SchatkamerDarkElevated
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Cast,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Google Cast (Chromecast)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Kies een Chromecast of Google TV",
                            fontSize = 11.sp,
                            color = SchatkamerTextMuted
                        )
                    }
                }

                // Originele Cast-knop die het Google Cast dialoogvenster opent
                GoogleCastMediaRouteButton()
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Uitlegkaart over Widevine DRM en blauw logo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .padding(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = SchatkamerCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Let op: De meeste historische archiefvideo's bevatten Widevine DRM. Als een standaard Chromecast niet start (alleen blauw logo), gebruik dan 'Draadloos Scherm (Miracast)' hierboven of installeer De Schatkamer app direct op je Android TV.",
                    fontSize = 11.sp,
                    color = SchatkamerTextMuted,
                    lineHeight = 15.sp
                )
            }
        }
    }
}
