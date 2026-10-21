package nl.beeldengeluid.schatkamer.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.beeldengeluid.schatkamer.brand.R
import nl.beeldengeluid.schatkamer.tv.ui.theme.FocusBorderCyan
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerCyan
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerDarkBackground
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerDarkElevated
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerTextMuted
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerTextPrimary

@Composable
fun TvNavigationRail(
    selectedDestination: Int,
    onSelectDestination: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(84.dp)
            .fillMaxHeight()
            .background(SchatkamerDarkBackground)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Schatkamer Embleem bovenaan
        Image(
            painter = painterResource(R.drawable.schatkamer_mark),
            contentDescription = "De Schatkamer",
            modifier = Modifier.size(44.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        TvNavRailItem(
            icon = Icons.Default.Home,
            label = "Start",
            selected = selectedDestination == 0,
            onClick = { onSelectDestination(0) }
        )

        TvNavRailItem(
            icon = Icons.Default.Search,
            label = "Zoeken",
            selected = selectedDestination == 1,
            onClick = { onSelectDestination(1) }
        )

        TvNavRailItem(
            icon = Icons.Default.Explore,
            label = "Ontdek",
            selected = selectedDestination == 2,
            onClick = { onSelectDestination(2) }
        )

        TvNavRailItem(
            icon = Icons.Default.Bookmark,
            label = "Mijn Lijst",
            selected = selectedDestination == 3,
            onClick = { onSelectDestination(3) }
        )
    }
}

@Composable
private fun TvNavRailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1.0f,
        animationSpec = tween(150),
        label = "nav_item_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    if (isFocused) SchatkamerCyan else if (selected) SchatkamerDarkElevated else Color.Transparent,
                    shape = CircleShape
                )
                .border(
                    width = if (isFocused) 2.dp else if (selected) 1.dp else 0.dp,
                    color = if (isFocused) Color.White else if (selected) SchatkamerCyan else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isFocused) Color.Black else if (selected) SchatkamerCyan else SchatkamerTextMuted,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            fontSize = 10.sp,
            color = if (isFocused || selected) SchatkamerTextPrimary else SchatkamerTextMuted
        )
    }
}
