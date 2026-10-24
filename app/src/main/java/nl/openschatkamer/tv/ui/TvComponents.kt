@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package nl.openschatkamer.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.rounded.Movie
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.focusRestorer
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

/**
 * Hoe compact de tv-interface is. Op een tv kijk je van een afstand, maar met de
 * standaarddichtheid paste er te weinig op het scherm; 0,8 geeft ongeveer vijf
 * kaarten per rij en de hele zijbalk in beeld.
 */
private const val TV_UI_SCALE = 0.8f

@Composable
fun TvTheme(content: @Composable () -> Unit) {
    val base = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density * TV_UI_SCALE, base.fontScale)
    ) { TvMaterialTheme(content) }
}

@Composable
private fun TvMaterialTheme(content: @Composable () -> Unit) {
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

/** Krijgt de kaart die focus heeft, voor de meebewegende achtergrond op het startscherm. */
val LocalTvFocusSink = androidx.compose.runtime.staticCompositionLocalOf<(SchatkamerItem) -> Unit> { {} }

/** Lang op OK drukken op een kaart: het actiemenu. */
val LocalTvItemActions = androidx.compose.runtime.staticCompositionLocalOf<(SchatkamerItem) -> Unit> { {} }

/** Kaartstatus (nieuw, voortgang, gezien) zoals op de telefoon. */
interface TvItemStates {
    fun isNew(item: SchatkamerItem): Boolean = false
    fun progress(item: SchatkamerItem): Float? = null
    fun watched(item: SchatkamerItem): Boolean = false
}
val LocalTvItemStates = androidx.compose.runtime.compositionLocalOf<TvItemStates> { object : TvItemStates {} }

/**
 * Maakt iets focusbaar met de afstandsbediening: licht vergroot met een
 * amberkleurige rand, zodat altijd duidelijk is waar je bent.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Modifier.tvFocusable(onClick: () -> Unit, shape: Shape = TvShape, scaleTo: Float = 1.06f, item: SchatkamerItem? = null): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) scaleTo else 1f, tween(160), label = "focus")
    val sink = LocalTvFocusSink.current
    val actions = LocalTvItemActions.current
    if (item != null) androidx.compose.runtime.LaunchedEffect(focused) { if (focused) sink(item) }
    return this
        .scale(scale)
        .border(if (focused) 3.dp else 0.dp, if (focused) BrandColors.Amber else Color.Transparent, shape)
        .clip(shape)
        .combinedClickable(
            interactionSource = interaction, indication = null, onClick = onClick,
            onLongClick = item?.let { { actions(it) } }
        )
}

/**
 * Beeld bij een kaart. Zonder beeld (vaak bij radio) of als het niet laadt, staat
 * er een rustig vlak met een icoon en eventueel de titel, nooit een leeg gat.
 */
@Composable
fun TvArtwork(url: String?, modifier: Modifier = Modifier, audio: Boolean = false, label: String? = null) {
    Box(
        modifier.background(Brush.linearGradient(listOf(BrandColors.SurfaceHigh, BrandColors.Surface))),
        contentAlignment = Alignment.Center
    ) {
        var loaded by remember(url) { mutableStateOf(false) }
        if (!loaded) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 18.dp)) {
            Icon(
                if (audio) Icons.Rounded.Headphones else Icons.Rounded.Movie, null,
                tint = (if (audio) BrandColors.Audio else BrandColors.Amber).copy(alpha = 0.7f), modifier = Modifier.size(26.dp)
            )
            if (!label.isNullOrBlank()) Text(
                label, style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted, textAlign = TextAlign.Center,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (!url.isNullOrBlank()) AsyncImage(url, null, contentScale = ContentScale.Crop, onSuccess = { loaded = true }, modifier = Modifier.fillMaxSize())
    }
}

fun SchatkamerItem.tvMeta(): String = listOfNotNull(
    seriesTitle?.takeIf { it.isNotBlank() && it != title },
    broadcaster.ifBlank { null },
    SchatkamerText.formatDate(date).ifBlank { null }
).joinToString(" · ")

@Composable
fun TvProgramCard(item: SchatkamerItem, onClick: () -> Unit, width: Dp = 240.dp, progress: Float? = null, modifier: Modifier = Modifier) {
    // Dp.Unspecified: vult de kolom van een raster.
    Column(if (width == Dp.Unspecified) modifier.fillMaxWidth() else modifier.width(width)) {
        val states = LocalTvItemStates.current
        val shown = progress ?: states.progress(item)
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).tvFocusable(onClick, item = item)) {
            TvArtwork(item.imageUrl, Modifier.fillMaxSize(), audio = item.isAudio, label = item.seriesTitle ?: item.broadcaster)
            Row(Modifier.align(Alignment.BottomStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (item.year.isNotBlank()) Badge(item.year)
                if (item.isAudio) Badge("Audio", BrandColors.Audio)
            }
            Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (states.isNew(item)) Badge("NIEUW", BrandColors.Amber)
                if (states.watched(item)) Badge("✓ Gezien", BrandColors.Amber)
            }
            if (shown != null && shown > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = 0.5f))) {
                    Box(Modifier.fillMaxWidth(shown).height(4.dp).background(BrandColors.Amber))
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
fun TvSeriesPoster(item: SchatkamerItem, onClick: () -> Unit, width: Dp = 140.dp) {
    Column(Modifier.width(width)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).tvFocusable(onClick, item = item)) {
            TvArtwork(item.imageUrl, Modifier.fillMaxSize(), audio = item.isAudio, label = item.title)
            if (item.isAudio) Icon(Icons.Rounded.Headphones, null, tint = BrandColors.Audio, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TvStoryCard(item: SchatkamerItem, onClick: () -> Unit) {
    // Tekst onder het beeld, zoals de andere kaarten: rustiger en beter leesbaar.
    Column(Modifier.width(300.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).tvFocusable(onClick, item = item)) {
            TvArtwork(item.imageUrl, Modifier.fillMaxSize(), label = item.title)
        }
        Spacer(Modifier.height(8.dp))
        Text("VERHAAL", style = MaterialTheme.typography.labelSmall, color = BrandColors.Amber)
        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TvPersonCard(item: SchatkamerItem, onClick: () -> Unit) {
    Column(Modifier.width(140.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TvArtwork(item.imageUrl, Modifier.size(104.dp).tvFocusable(onClick, CircleShape), label = null)
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
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = modifier.padding(start = 8.dp, top = 20.dp, bottom = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun TvCatalogRow(row: SchatkamerRow, onOpen: (SchatkamerItem) -> Unit) {
    Column {
        TvRowTitle(row.title.trim())
        LazyRow(
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            itemsIndexed(row.items, key = { _, it -> it.kind.name + it.id }) { index, item ->
                val open = { onOpen(item) }
                when {
                    row.layout == RowLayout.RANKED -> Row(verticalAlignment = Alignment.Bottom) {
                        Text("${index + 1}", style = MaterialTheme.typography.displaySmall.copy(fontSize = 72.sp), color = BrandColors.Amber, modifier = Modifier.padding(end = 6.dp, bottom = 40.dp))
                        TvProgramCard(item, open, width = 220.dp)
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

/**
 * Tekstvelden op de tv houden de pijltjes vast (die verplaatsen de cursor).
 * Hiermee gaan omhoog en omlaag gewoon naar het vorige of volgende element,
 * zodat je nooit vastzit in een veld.
 */
@Composable
fun Modifier.tvTextFieldNavigation(): Modifier {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    return this.onPreviewKeyEvent { event ->
        if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            androidx.compose.ui.input.key.Key.DirectionDown -> focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
            androidx.compose.ui.input.key.Key.DirectionUp -> focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Up)
            else -> false
        }
    }
}

/**
 * Invoerveld voor de tv: een knop met de waarde. OK opent het toetsenbord;
 * klaar, Terug of omhoog/omlaag sluit het weer. Zo komt de afstandsbediening
 * nooit vast te zitten in een tekstveld, en springt het toetsenbord niet
 * vanzelf open als een scherm verschijnt.
 */
@Composable
fun TvInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    password: Boolean = false,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Done,
    leading: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onSubmit: () -> Unit = {}
) {
    var editing by remember { mutableStateOf(false) }
    val fieldFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val buttonFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var wasEditing by remember { mutableStateOf(false) }
    fun stopEditing(submit: Boolean) {
        keyboard?.hide()
        editing = false
        if (submit) onSubmit()
    }
    androidx.compose.runtime.LaunchedEffect(editing) {
        if (editing) { wasEditing = true; runCatching { fieldFocus.requestFocus(); keyboard?.show() } }
        else if (wasEditing) runCatching { buttonFocus.requestFocus() }
    }
    Column(modifier) {
        if (label != null) Text(label, style = MaterialTheme.typography.labelSmall, color = BrandColors.TextMuted, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        if (editing) {
            androidx.activity.compose.BackHandler { stopEditing(submit = false) }
            androidx.compose.material3.OutlinedTextField(
                value = value, onValueChange = onValueChange, singleLine = true,
                placeholder = { Text(placeholder) },
                visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { stopEditing(true) }, onDone = { stopEditing(true) }, onNext = { stopEditing(true) }, onGo = { stopEditing(true) }
                ),
                shape = RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandColors.Amber, cursorColor = BrandColors.Amber),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fieldFocus)
                    .onPreviewKeyEvent { event ->
                        val vertical = event.key == androidx.compose.ui.input.key.Key.DirectionDown || event.key == androidx.compose.ui.input.key.Key.DirectionUp
                        if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && vertical) { stopEditing(submit = imeAction == androidx.compose.ui.text.input.ImeAction.Search); true } else false
                    }
            )
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .focusRequester(buttonFocus)
                    .tvFocusable({ editing = true }, RoundedCornerShape(14.dp), 1.02f)
                    .background(BrandColors.SurfaceHigh)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leading != null) {
                    Icon(leading, null, tint = BrandColors.Amber)
                    Spacer(Modifier.width(14.dp))
                }
                Text(
                    when {
                        value.isBlank() -> placeholder
                        password -> "•".repeat(value.length.coerceAtMost(16))
                        else -> value
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (value.isBlank()) BrandColors.TextMuted else BrandColors.Text
                )
            }
        }
    }
}

@Composable
fun TvSearchField(text: String, onTextChange: (String) -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) =
    TvInputField(
        text, onTextChange, "Zoeken: programma, persoon, onderwerp of jaar", modifier,
        imeAction = androidx.compose.ui.text.input.ImeAction.Search,
        leading = androidx.compose.material.icons.Icons.Rounded.Search,
        onSubmit = onSearch
    )

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

/** Een glans die over de laadvlakken loopt. */
@Composable
fun rememberTvShimmer(): Brush {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "laden")
    val x by transition.animateFloat(
        initialValue = -600f, targetValue = 1800f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(tween<Float>(1400)), label = "glans"
    )
    return Brush.linearGradient(
        listOf(BrandColors.Surface, BrandColors.SurfaceHigh, BrandColors.Surface),
        start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x + 600f, 300f)
    )
}

/** Voorlopige kaart terwijl de inhoud laadt. */
@Composable
fun TvSkeletonCard(brush: Brush, modifier: Modifier = Modifier.width(240.dp)) {
    Column(modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(TvShape).background(brush))
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(0.8f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(brush))
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(0.5f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(brush))
    }
}

/** Voorlopige rijen terwijl Start laadt: het scherm oogt meteen gevuld. */
@Composable
fun TvSkeletonRows(rows: Int = 3) {
    val brush = rememberTvShimmer()
    Column(Modifier.padding(start = 8.dp)) {
        repeat(rows) {
            Box(Modifier.padding(top = 24.dp, bottom = 14.dp).width(260.dp).height(22.dp).clip(RoundedCornerShape(6.dp)).background(brush))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) { repeat(6) { TvSkeletonCard(brush) } }
        }
    }
}
