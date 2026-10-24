package nl.openschatkamer.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import nl.openschatkamer.brand.BrandColors
import nl.openschatkamer.brand.BrandFonts
import nl.openschatkamer.brand.BroadcasterLogos
import nl.openschatkamer.brand.forName
import nl.openschatkamer.core.data.ItemKind
import nl.openschatkamer.core.data.RowLayout
import nl.openschatkamer.core.data.SchatkamerItem
import nl.openschatkamer.core.data.SchatkamerRow
import nl.openschatkamer.core.data.SchatkamerText

@Composable
fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = BrandColors.Amber, onPrimary = BrandColors.Ink,
            background = BrandColors.Ink, onBackground = BrandColors.Text,
            surface = BrandColors.Ink, onSurface = BrandColors.Text,
            surfaceVariant = BrandColors.Surface, onSurfaceVariant = BrandColors.TextMuted,
            surfaceContainerHighest = BrandColors.SurfaceHigh, outline = BrandColors.Outline
        ),
        typography = Typography(
            displaySmall = TextStyle(fontFamily = BrandFonts.Display, fontSize = 44.sp, lineHeight = 48.sp),
            headlineMedium = TextStyle(fontFamily = BrandFonts.Display, fontSize = 32.sp, lineHeight = 36.sp),
            headlineSmall = TextStyle(fontFamily = BrandFonts.Display, fontSize = 24.sp, lineHeight = 28.sp),
            titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
            titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
            bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
            bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 17.sp),
            labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
        ),
        content = content
    )
}

val TvShape = RoundedCornerShape(12.dp)

/**
 * Maakt iets focusbaar met de afstandsbediening: licht vergroot met een
 * amberkleurige rand, zodat altijd duidelijk is waar je bent.
 */
@Composable
fun Modifier.tvFocusable(onClick: () -> Unit, shape: Shape = TvShape, scaleTo: Float = 1.06f): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) scaleTo else 1f, tween(160), label = "focus")
    return this
        .scale(scale)
        .border(if (focused) 3.dp else 0.dp, if (focused) BrandColors.Amber else Color.Transparent, shape)
        .clip(shape)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

@Composable
fun TvArtwork(url: String?, modifier: Modifier = Modifier) {
    Box(modifier.background(BrandColors.SurfaceHigh)) {
        if (!url.isNullOrBlank()) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

fun SchatkamerItem.tvMeta(): String = listOfNotNull(
    seriesTitle?.takeIf { it.isNotBlank() && it != title },
    broadcaster.ifBlank { null },
    SchatkamerText.formatDate(date).ifBlank { null }
).joinToString(" · ")

@Composable
fun TvProgramCard(item: SchatkamerItem, onClick: () -> Unit, width: Dp = 280.dp, progress: Float? = null, modifier: Modifier = Modifier) {
    Column(modifier.width(width)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).tvFocusable(onClick)) {
            TvArtwork(item.imageUrl, Modifier.fillMaxSize())
            Row(Modifier.align(Alignment.BottomStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (item.year.isNotBlank()) Badge(item.year)
                if (item.isAudio) Badge("Audio", BrandColors.Audio)
            }
            if (progress != null) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = 0.5f))) {
                    Box(Modifier.fillMaxWidth(progress).height(4.dp).background(BrandColors.Amber))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val meta = item.tvMeta()
        if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Badge(text: String, color: Color = BrandColors.Text) {
    Text(
        text, color = color, style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 6.dp, vertical = 3.dp)
    )
}

@Composable
fun TvSeriesPoster(item: SchatkamerItem, onClick: () -> Unit, width: Dp = 168.dp) {
    Column(Modifier.width(width)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).tvFocusable(onClick)) {
            TvArtwork(item.imageUrl, Modifier.fillMaxSize())
            if (item.isAudio) Icon(Icons.Rounded.Headphones, null, tint = BrandColors.Audio, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TvStoryCard(item: SchatkamerItem, onClick: () -> Unit) {
    Box(Modifier.width(380.dp).aspectRatio(16f / 9f).tvFocusable(onClick)) {
        TvArtwork(item.imageUrl, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.9f))))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text("VERHAAL", style = MaterialTheme.typography.labelSmall, color = BrandColors.Amber)
            Text(item.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun TvPersonCard(item: SchatkamerItem, onClick: () -> Unit) {
    Column(Modifier.width(140.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TvArtwork(item.imageUrl, Modifier.size(120.dp).tvFocusable(onClick, CircleShape))
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
fun TvBroadcasterTile(name: String, imageUrl: String, onClick: () -> Unit, width: Dp = 220.dp, count: String? = null) {
    val logo = BroadcasterLogos.forName(name)
    Column(Modifier.width(width)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).tvFocusable(onClick).background(if (logo != null) BrandColors.SurfaceHigh else Color(0xFFF4F0E8)),
            contentAlignment = Alignment.Center
        ) {
            when {
                logo != null -> Image(painterResource(logo), name, Modifier.fillMaxSize().padding(22.dp), contentScale = ContentScale.Fit)
                imageUrl.isNotBlank() -> TvArtwork(imageUrl, Modifier.fillMaxSize())
                else -> Text(name, style = MaterialTheme.typography.headlineSmall, color = BrandColors.Ink)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(name, style = MaterialTheme.typography.titleSmall)
        if (count != null) Text(count, style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted)
    }
}

@Composable
fun TvRowTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = modifier.padding(start = 8.dp, top = 28.dp, bottom = 14.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun TvCatalogRow(row: SchatkamerRow, onOpen: (SchatkamerItem) -> Unit) {
    Column {
        TvRowTitle(row.title.trim())
        LazyRow(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            itemsIndexed(row.items, key = { _, it -> it.kind.name + it.id }) { index, item ->
                val open = { onOpen(item) }
                when {
                    row.layout == RowLayout.RANKED -> Row(verticalAlignment = Alignment.Bottom) {
                        Text("${index + 1}", style = MaterialTheme.typography.displaySmall.copy(fontSize = 72.sp), color = BrandColors.Amber, modifier = Modifier.padding(end = 6.dp, bottom = 40.dp))
                        TvProgramCard(item, open, width = 250.dp)
                    }
                    item.kind == ItemKind.STORY -> TvStoryCard(item, open)
                    item.kind == ItemKind.PERSON -> TvPersonCard(item, open)
                    item.kind == ItemKind.SERIES -> TvSeriesPoster(item, open)
                    item.kind == ItemKind.BROADCASTER -> TvBroadcasterTile(item.title, item.imageUrl, open)
                    else -> TvProgramCard(item, open)
                }
            }
        }
    }
}

@Composable
fun TvPill(text: String, onClick: () -> Unit, selected: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = if (selected) BrandColors.Ink else BrandColors.Text,
        modifier = Modifier
            .tvFocusable(onClick, RoundedCornerShape(50), 1.08f)
            .background(if (selected) BrandColors.Amber else BrandColors.SurfaceHigh)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    )
}

@Composable
fun TvLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = BrandColors.Amber)
    }
}

@Composable
fun TvMessage(title: String, message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = BrandColors.TextMuted, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            TvPill(actionLabel, onAction, selected = true)
        }
    }
}
