package nl.beeldengeluid.schatkamer.mobile.cast.airplay

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
import androidx.compose.material.icons.filled.Airplay
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import nl.beeldengeluid.schatkamer.core.cast.CastDevice
import nl.beeldengeluid.schatkamer.core.cast.ProtocolType
import nl.beeldengeluid.schatkamer.mobile.playback.PlaybackManager
import nl.beeldengeluid.schatkamer.mobile.playback.PlaybackState
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerBorder
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerCyan
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerDarkBackground
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerDarkElevated
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerMagenta
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerTextMuted
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerTextPrimary
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerTextSecondary

/**
 * Toegewijde Apple AirPlay zenderdialoog.
 * Blijft stabiel geopend totdat de gebruiker zelf een apparaat kiest of op 'Sluiten' klikt.
 */
@Composable
fun AirPlaySenderDialog(
    playbackManager: PlaybackManager,
    playbackState: PlaybackState,
    onDismissRequest: () -> Unit
) {
    val discovery = playbackManager.discoveryManager
    val devices by discovery.discoveredDevices.collectAsState()
    val isScanning by discovery.isScanning.collectAsState()

    // Start actieve mDNS discovery zolang deze dialoog open staat
    DisposableEffect(Unit) {
        discovery.startScanning()
        onDispose {
            discovery.stopScanning()
        }
    }

    // Filter uitsluitend AirPlay apparaten
    val airPlayDevices = devices.filter { it.protocol == ProtocolType.AIRPLAY }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false, // Voorkomt per ongeluk wegschieten van het scherm
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, SchatkamerBorder, RoundedCornerShape(20.dp)),
            color = SchatkamerDarkElevated,
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header met AirPlay logo en sluitknop
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SchatkamerCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Airplay,
                                contentDescription = null,
                                tint = SchatkamerCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Apple AirPlay Zender",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = SchatkamerTextPrimary
                            )
                            Text(
                                text = "Stream naar Apple TV, Mac of Smart TV",
                                fontSize = 12.sp,
                                color = SchatkamerTextMuted
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Sluiten",
                            tint = SchatkamerTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actieve AirPlay sessie banner (als we verbonden zijn)
                if (playbackState.isCasting && playbackState.castProtocol == ProtocolType.AIRPLAY) {
                    val activeDeviceName = playbackState.castDeviceName ?: "AirPlay TV"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SchatkamerCyan.copy(alpha = 0.12f))
                            .border(1.dp, SchatkamerCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(SchatkamerCyan)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Verbonden via AirPlay",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SchatkamerCyan
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = activeDeviceName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }

                            Button(
                                onClick = {
                                    playbackManager.disconnectRemoteCast(resumeLocal = true)
                                    onDismissRequest()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SchatkamerMagenta),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Verbreken", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Gevonden apparaten sectie
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Beschikbare apparaten (${airPlayDevices.size})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SchatkamerTextSecondary
                    )

                    if (isScanning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = SchatkamerCyan,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Zoeken...",
                                fontSize = 11.sp,
                                color = SchatkamerTextMuted
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { discovery.startScanning() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Opnieuw zoeken",
                                tint = SchatkamerCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (airPlayDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SchatkamerDarkBackground)
                            .border(1.dp, SchatkamerBorder, RoundedCornerShape(12.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = SchatkamerTextMuted,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Nog geen AirPlay apparaten gevonden",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = SchatkamerTextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Controleer of je Apple TV, Mac of TV op hetzelfde Wi-Fi netwerk zit.",
                                fontSize = 11.sp,
                                color = SchatkamerTextMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(airPlayDevices, key = { it.id }) { device ->
                            val isCurrent = playbackState.isCasting && playbackState.castDeviceName == device.name
                            AirPlayDeviceRow(
                                device = device,
                                isCurrent = isCurrent,
                                onClick = {
                                    playbackManager.castToDevice(device)
                                    onDismissRequest()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actieknoppen onderaan
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismissRequest,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SchatkamerBorder),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Sluiten")
                    }
                }
            }
        }
    }
}

@Composable
private fun AirPlayDeviceRow(
    device: CastDevice,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) SchatkamerCyan.copy(alpha = 0.15f) else SchatkamerDarkBackground)
            .border(
                1.dp,
                if (isCurrent) SchatkamerCyan else SchatkamerBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SchatkamerCyan.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Airplay,
                    contentDescription = null,
                    tint = SchatkamerCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = device.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "${device.ip}:${device.port}",
                    fontSize = 11.sp,
                    color = SchatkamerTextMuted
                )
            }
        }

        if (isCurrent) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Actief verbonden",
                tint = SchatkamerCyan,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
