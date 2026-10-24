package nl.openschatkamer.mobile.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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

val CardShape = RoundedCornerShape(10.dp)

/** Afbeelding met een rustige placeholder in de achtergrondkleur. */
@Composable
fun Artwork(url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    Box(modifier.background(BrandColors.SurfaceHigh)) {
        if (!url.isNullOrBlank()) {
            AsyncImage(model = url, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Kleine label op een afbeelding: jaar, "Audio", "Serie". */
@Composable
fun ArtBadge(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, tint: Color = BrandColors.Text) {
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(3.dp))
        }
        Text(text, color = tint, style = MaterialTheme.typography.labelSmall)
    }
}

fun SchatkamerItem.metaLine(includeSeries: Boolean = true): String = listOfNotNull(
    seriesTitle?.takeIf { includeSeries && it.isNotBlank() && it != title },
    broadcaster.takeIf { it.isNotBlank() },
    SchatkamerText.formatDate(date).takeIf { it.isNotBlank() }
).joinToString(" · ")

// --------------------------------------------------------------------- kaarten

/** Landschapskaart (16:9) voor een uitzending, met optionele kijkvoortgang. */
@Composable
fun ProgramCard(
    item: SchatkamerItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 236.dp,
    progress: Float? = null,
    large: Boolean = false
) {
    Column(modifier.width(width).clip(CardShape).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(CardShape)) {
            Artwork(item.imageUrl, Modifier.fillMaxSize())
            Row(Modifier.align(Alignment.BottomStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (item.year.isNotBlank()) ArtBadge(item.year)
                if (item.isAudio) ArtBadge("Audio", icon = Icons.Rounded.Headphones, tint = BrandColors.Audio)
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                    color = BrandColors.Amber,
                    trackColor = Color.Black.copy(alpha = 0.5f),
                    drawStopIndicator = {}
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.title,
            style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val meta = item.metaLine()
        if (meta.isNotBlank()) {
            Text(meta, style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Staande poster (2:3) voor een serie. */
@Composable
fun SeriesPoster(item: SchatkamerItem, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 132.dp) {
    Column(modifier.width(width).clip(CardShape).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(CardShape)) {
            Artwork(item.imageUrl, Modifier.fillMaxSize())
            if (item.isAudio) ArtBadge("Audio", Modifier.align(Alignment.TopEnd).padding(6.dp), Icons.Rounded.Headphones, BrandColors.Audio)
        }
        Spacer(Modifier.height(6.dp))
        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val sub = item.broadcaster.ifBlank { item.subTitle }
        if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted, maxLines = 1)
    }
}

/** Verhaal van de redactie: groot beeld met de titel erover. */
@Composable
fun StoryCard(item: SchatkamerItem, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 290.dp) {
    Box(
        modifier
            .width(width)
            .aspectRatio(16f / 10f)
            .clip(CardShape)
            .clickable(onClick = onClick)
    ) {
        Artwork(item.imageUrl, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.88f))))
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text("VERHAAL", style = MaterialTheme.typography.labelSmall, color = BrandColors.Amber)
            Spacer(Modifier.height(2.dp))
            Text(item.title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp), maxLines = 3, overflow = TextOverflow.Ellipsis, color = Color.White)
        }
    }
}

@Composable
fun PersonCard(item: SchatkamerItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.width(96.dp).clip(CardShape).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Artwork(item.imageUrl, Modifier.size(84.dp).clip(CircleShape).border(2.dp, BrandColors.Outline, CircleShape))
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (item.subTitle.isNotBlank()) {
            Text(item.subTitle, style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Omroeptegel: het transparante logo als we dat hebben, anders het beeld van de site. */
@Composable
fun BroadcasterTile(name: String, imageUrl: String, onClick: () -> Unit, modifier: Modifier = Modifier, count: Int = 0) {
    val logo = BroadcasterLogos.forName(name)
    Column(modifier.clip(CardShape).clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(CardShape).background(if (logo != null) BrandColors.SurfaceHigh else Color(0xFFF4F0E8)),
            contentAlignment = Alignment.Center
        ) {
            when {
                logo != null -> Image(painterResource(logo), name, Modifier.fillMaxSize().padding(18.dp), contentScale = ContentScale.Fit)
                imageUrl.isNotBlank() -> Artwork(imageUrl, Modifier.fillMaxSize())
                else -> Text(name, style = MaterialTheme.typography.headlineSmall, color = BrandColors.Ink)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        if (count > 0) Text(countLabel(count), style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted)
    }
}

fun countLabel(count: Int, noun: String = "uitzendingen"): String = when {
    count >= 10_000 -> "10.000+ $noun"
    count >= 1000 -> "%,d $noun".format(count).replace(',', '.')
    else -> "$count $noun"
}

/** Meest bekeken: een groot rangnummer naast de kaart. */
@Composable
fun RankedCard(rank: Int, item: SchatkamerItem, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.clickable(onClick = onClick)) {
        Text(
            "$rank",
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 64.sp, lineHeight = 64.sp),
            color = BrandColors.Amber.copy(alpha = 0.9f),
            modifier = Modifier.padding(end = 4.dp)
        )
        ProgramCard(item, onClick, width = 200.dp)
    }
}

// --------------------------------------------------------------------- rijen

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, color = BrandColors.Amber)
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = BrandColors.Amber, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Een rij van de website, met de kaartvorm die bij de rij hoort. */
@Composable
fun CatalogRow(row: SchatkamerRow, onOpen: (SchatkamerItem) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        SectionHeader(row.title.trim())
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(row.items, key = { _, it -> it.kind.name + it.id }) { index, item ->
                val open = { onOpen(item) }
                when {
                    row.layout == RowLayout.RANKED -> RankedCard(index + 1, item, open)
                    item.kind == ItemKind.STORY -> StoryCard(item, open)
                    item.kind == ItemKind.PERSON -> PersonCard(item, open)
                    item.kind == ItemKind.SERIES -> SeriesPoster(item, open)
                    item.kind == ItemKind.BROADCASTER -> BroadcasterTile(item.title, item.imageUrl, open, Modifier.width(150.dp))
                    else -> ProgramCard(item, open, large = row.layout == RowLayout.LANDSCAPE_LARGE, width = if (row.layout == RowLayout.LANDSCAPE_LARGE) 290.dp else 236.dp)
                }
            }
        }
    }
}

/** Regel in een lijst van uitzendingen: beeld links, tekst rechts. */
@Composable
fun ProgramListItem(
    item: SchatkamerItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showSeries: Boolean = true,
    progress: Float? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(132.dp).aspectRatio(16f / 9f).clip(CardShape)) {
            Artwork(item.imageUrl, Modifier.fillMaxSize())
            Icon(
                if (item.isAudio) Icons.Rounded.Headphones else Icons.Rounded.PlayArrow, null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(30.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)).padding(4.dp)
            )
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress }, color = BrandColors.Amber, trackColor = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp), drawStopIndicator = {}
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (showSeries && !item.seriesTitle.isNullOrBlank()) {
                Text(item.seriesTitle!!.uppercase(), style = MaterialTheme.typography.labelSmall, color = BrandColors.Amber, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = item.metaLine(includeSeries = false)
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

// ------------------------------------------------------------------- toestanden

@Composable
fun MessageState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = BrandColors.Amber, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = BrandColors.TextMuted, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            OutlinedButton(onClick = onAction) { Text(actionLabel, color = BrandColors.Amber) }
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) =
    MessageState(Icons.Rounded.CloudOff, "Dat lukte even niet", message, modifier, "Opnieuw proberen", onRetry)

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) =
    MessageState(Icons.Rounded.SearchOff, title, message, modifier, actionLabel, onAction)

@Composable
fun LoadingFooter(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = BrandColors.Amber, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
    }
}

/** Pulserende vlakken in de vorm van de inhoud, zolang die laadt. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val pulse = rememberInfiniteTransition(label = "skelet")
    val alpha by pulse.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "alpha")
    Box(modifier.clip(CardShape).background(BrandColors.SurfaceHigh).alpha(alpha), content = content)
}

@Composable
fun SkeletonRows(rows: Int = 3) {
    Column {
        repeat(rows) {
            Skeleton(Modifier.padding(start = 16.dp, top = 26.dp, bottom = 12.dp).width(180.dp).height(22.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) { Skeleton(Modifier.width(236.dp).aspectRatio(16f / 9f)) }
            }
        }
    }
}

@Composable
fun SkeletonList(count: Int = 6) {
    Column {
        repeat(count) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Skeleton(Modifier.width(132.dp).aspectRatio(16f / 9f))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Skeleton(Modifier.fillMaxWidth(0.8f).height(14.dp))
                    Spacer(Modifier.height(8.dp))
                    Skeleton(Modifier.fillMaxWidth(0.5f).height(12.dp))
                }
            }
        }
    }
}

/** Het grote kopje bovenaan een tabblad. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.headlineLarge.copy(fontFamily = BrandFonts.Display, fontWeight = FontWeight.Normal),
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun Pill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, leading: ImageVector? = null) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) BrandColors.Amber else BrandColors.SurfaceHigh,
        contentColor = if (selected) BrandColors.Ink else BrandColors.Text,
        modifier = modifier
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                Icon(leading, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}
