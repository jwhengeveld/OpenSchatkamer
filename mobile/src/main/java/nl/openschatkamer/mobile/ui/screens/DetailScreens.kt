package nl.openschatkamer.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import nl.openschatkamer.core.data.UserLibrary
import nl.openschatkamer.mobile.ui.components.AddToListSheet
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.openschatkamer.brand.BrandColors
import nl.openschatkamer.core.data.ItemKind
import nl.openschatkamer.core.data.SchatkamerItem
import nl.openschatkamer.core.data.SchatkamerProgramDetail
import nl.openschatkamer.core.data.SchatkamerSeries
import nl.openschatkamer.core.data.SchatkamerText
import nl.openschatkamer.core.data.SortOrder
import nl.openschatkamer.mobile.cast.CastButton
import nl.openschatkamer.core.ui.BrowseSpec
import nl.openschatkamer.core.ui.BrowseViewModel
import nl.openschatkamer.core.ui.ItemMemo
import nl.openschatkamer.core.ui.Load
import nl.openschatkamer.core.ui.ProgramViewModel
import nl.openschatkamer.core.ui.SeriesViewModel
import nl.openschatkamer.core.ui.StoryViewModel
import nl.openschatkamer.mobile.ui.components.Artwork
import nl.openschatkamer.mobile.ui.components.CardShape
import nl.openschatkamer.mobile.ui.components.EmptyState
import nl.openschatkamer.mobile.ui.components.ErrorState
import nl.openschatkamer.mobile.ui.components.LoadingFooter
import nl.openschatkamer.mobile.ui.components.Pill
import nl.openschatkamer.mobile.ui.components.ProgramCard
import nl.openschatkamer.mobile.ui.components.ProgramListItem
import nl.openschatkamer.mobile.ui.components.LocalListColumns
import nl.openschatkamer.mobile.ui.components.adaptiveItems
import nl.openschatkamer.mobile.ui.components.SectionHeader
import nl.openschatkamer.mobile.ui.components.Skeleton
import nl.openschatkamer.mobile.ui.components.SkeletonList
import nl.openschatkamer.mobile.ui.components.countLabel

// ------------------------------------------------------------------ gedeeld

@Composable
private fun TopActions(onBack: () -> Unit, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIcon(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Terug") }
        Spacer(Modifier.weight(1f))
        actions()
        Box(Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))) { CastButton() }
    }
}

@Composable
private fun RoundIcon(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.35f), contentColor = Color.White),
        modifier = Modifier.padding(horizontal = 2.dp)
    ) { content() }
}

private fun share(context: android.content.Context, title: String, url: String) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, "$title\n$url")
    }
    context.startActivity(android.content.Intent.createChooser(send, "Delen"))
}

/** Achtergrond voor detailschermen: het beeld vervaagd, met een verloop naar de achtergrondkleur. */
@Composable
private fun Backdrop(url: String, modifier: Modifier = Modifier, blurred: Boolean = false) {
    Box(modifier) {
        Artwork(url, Modifier.fillMaxSize().then(if (blurred) Modifier.blur(28.dp) else Modifier))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to BrandColors.Ink.copy(alpha = 0.15f), 0.6f to BrandColors.Ink.copy(alpha = 0.55f), 1f to BrandColors.Ink)))
    }
}

// ------------------------------------------------------------------- serie

@Composable
fun SeriesScreen(
    viewModel: SeriesViewModel,
    seriesId: String,
    library: UserLibrary,
    onOpen: (SchatkamerItem) -> Unit,
    onPlay: (SchatkamerItem) -> Unit,
    onBrowse: (BrowseSpec) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues
) {
    val series by viewModel.series.collectAsState()
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val memo = remember(seriesId) { ItemMemo.get(ItemKind.SERIES, seriesId) }
    val context = LocalContext.current
    InfiniteScroll(listState, state, viewModel::loadMore)

    val data = (series as? Load.Ready)?.data
    val item = data?.toItem() ?: memo ?: SchatkamerItem(id = seriesId, title = "", kind = ItemKind.SERIES, seriesId = seriesId)
    library.series.collectAsState().value
    val following = library.isFollowing(seriesId)
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    // Geopend: eventuele "nieuwe afleveringen" zijn nu gezien.
    LaunchedEffect(state.total) { if (state.total > 0) library.local.markSeriesSeen(seriesId, state.total) }

    Box(Modifier.fillMaxSize()) {
        val columns = LocalListColumns.current
        LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            item {
                Box(Modifier.fillMaxWidth().height(420.dp)) {
                    Backdrop(data?.imageUrl?.ifBlank { null } ?: item.imageUrl, Modifier.fillMaxSize())
                    Row(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalAlignment = Alignment.Bottom) {
                        if (data?.portraitUrl?.isNotBlank() == true) {
                            Artwork(data.portraitUrl, Modifier.width(112.dp).aspectRatio(2f / 3f).clip(CardShape))
                            Spacer(Modifier.width(14.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("SERIE", style = MaterialTheme.typography.labelSmall, color = BrandColors.Amber)
                            Text(item.title, style = MaterialTheme.typography.headlineLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            val meta = listOfNotNull(data?.broadcaster?.ifBlank { null }, data?.period?.ifBlank { null }).joinToString(" · ")
                            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodyMedium, color = BrandColors.TextMuted)
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val first = state.items.firstOrNull()
                    Button(
                        onClick = { first?.let(onPlay) }, enabled = first != null,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Amber, contentColor = BrandColors.Ink)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp))
                        Text(if (state.filter.sort == SortOrder.OLDEST) "Eerste aflevering" else "Nieuwste aflevering")
                    }
                    OutlinedButton(onClick = { library.toggleFollow(item, state.total) }) {
                        Icon(if (following) Icons.Rounded.Check else Icons.Rounded.Add, null, tint = BrandColors.Amber)
                        Spacer(Modifier.width(6.dp))
                        Text(if (following) "Volgend" else "Volgen", color = BrandColors.Text)
                    }
                }
            }
            when (val s = series) {
                is Load.Ready -> if (s.data.summary.isNotBlank()) item { ExpandableText(s.data.summary) }
                is Load.Failed -> if (state.items.isEmpty()) item { ErrorState(s.message, viewModel::loadSeries) }
                Load.Loading -> {}
            }
            if (data != null && data.genres.isNotEmpty()) {
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(data.genres) { g ->
                            val title = g.replaceFirstChar { c -> c.titlecase() }
                            Pill(title, { onBrowse(BrowseSpec(title, genre = g)) })
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Afleveringen", style = MaterialTheme.typography.headlineSmall)
                        if (state.loaded) Text(countLabel(state.total, if (state.total == 1) "aflevering" else "afleveringen"), style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted)
                    }
                    IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) { query = ""; viewModel.update { it.copy(query = "", sort = SortOrder.OLDEST) } } }) {
                        Icon(Icons.Rounded.Search, "Zoeken in deze serie", tint = BrandColors.Amber)
                    }
                    TextButton(onClick = viewModel::toggleSort) {
                        Icon(Icons.Rounded.SwapVert, null, tint = BrandColors.Amber, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(state.filter.sort.label, color = BrandColors.Amber)
                    }
                }
            }
            if (searchOpen) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { q -> query = q; viewModel.update { it.copy(query = q.trim(), sort = if (q.isBlank()) SortOrder.OLDEST else SortOrder.RELEVANCE) } },
                        singleLine = true, placeholder = { Text("Zoek in ${item.title}") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandColors.Amber, cursorColor = BrandColors.Amber),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
            when {
                state.isLoading -> item { SkeletonList(4) }
                state.error != null -> item { ErrorState(state.error!!, viewModel::retry) }
                state.loaded && state.items.isEmpty() -> item {
                    EmptyState(if (query.isNotBlank()) "Niets gevonden" else "Geen afleveringen", if (query.isNotBlank()) "Geen aflevering past bij \"$query\"." else "Van deze serie is (nog) niets af te spelen.")
                }
                else -> {
                    adaptiveItems(state.items, columns, key = { it.id }) { ep ->
                        ProgramListItem(ep, { onOpen(ep) }, showSeries = false)
                    }
                    if (state.isLoadingMore) item { LoadingFooter() }
                }
            }
        }
        TopActions(onBack, actions = {
            if (data != null) RoundIcon({ share(context, data.title, "https://schatkamer.beeldengeluid.nl/serie/${data.id}/${data.slug}") }) {
                Icon(Icons.Rounded.Share, "Delen")
            }
        })
    }
}

@Composable
private fun ExpandableText(text: String, collapsedLines: Int = 4) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clickable { expanded = !expanded }) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = BrandColors.Text.copy(alpha = 0.88f),
            maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis
        )
        if (text.length > 220) Text(if (expanded) "Minder" else "Meer lezen", color = BrandColors.Amber, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
    }
}

// --------------------------------------------------------------- uitzending

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgramScreen(
    viewModel: ProgramViewModel,
    programId: String,
    library: UserLibrary,
    onPlay: (SchatkamerItem, Boolean) -> Unit,
    onOpen: (SchatkamerItem) -> Unit,
    onBrowse: (BrowseSpec) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues
) {
    val detailState by viewModel.detail.collectAsState()
    val more by viewModel.more.collectAsState()
    val memo = remember(programId) { ItemMemo.get(ItemKind.PROGRAM, programId) }
    val detail = (detailState as? Load.Ready<SchatkamerProgramDetail>)?.data
    val item = detail?.toItem() ?: memo ?: SchatkamerItem(id = programId, title = "")
    val context = LocalContext.current
    val history by library.local.history.collectAsState()
    library.favorites.collectAsState().value
    library.lists.collectAsState().value
    val progress = history.firstOrNull { it.item.id == programId }
    val isFavorite = library.isFavorite(item)
    var addToList by remember { mutableStateOf(false) }

    // Telefoon: foto bovenaan, alles eronder. Tablet/liggend: foto en titel naast elkaar,
    // zodat de foto niet het halve scherm vult.
    val hero: @Composable (Modifier) -> Unit = { heroModifier ->
        Box(heroModifier.clickable { if (item.title.isNotBlank()) onPlay(item, false) }) {
            Backdrop(item.imageUrl, Modifier.fillMaxSize())
            Icon(
                if (item.isAudio) Icons.Rounded.Headphones else Icons.Rounded.PlayArrow, "Afspelen",
                tint = BrandColors.Ink,
                modifier = Modifier.align(Alignment.Center).size(68.dp).clip(CircleShape).background(BrandColors.Amber).padding(14.dp)
            )
        }
    }
    val titleBlock: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = 16.dp)) {
            item.seriesTitle?.takeIf { it.isNotBlank() }?.let { s ->
                Text(
                    s.uppercase(), style = MaterialTheme.typography.labelMedium, color = BrandColors.Amber,
                    modifier = Modifier.clickable { item.seriesId?.let { onOpen(SchatkamerItem(id = it, title = s, kind = ItemKind.SERIES, seriesId = it, seriesTitle = s)) } }
                )
            }
            if (item.title.isNotBlank()) Text(item.title, style = MaterialTheme.typography.headlineMedium)
            else Skeleton(Modifier.fillMaxWidth(0.7f).height(28.dp))
            val meta = listOfNotNull(
                SchatkamerText.formatDate(item.date).ifBlank { null },
                detail?.durationMinutes?.takeIf { it > 0 }?.let { "$it min" },
                item.broadcaster.ifBlank { null },
                detail?.episodeNumber?.let { "aflevering $it" }
            ).joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodyMedium, color = BrandColors.TextMuted, modifier = Modifier.padding(top = 4.dp))
        }
    }
    val actions: @Composable () -> Unit = {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val resumable = progress != null && !progress.isFinished && progress.positionMs > 15_000
            Button(
                onClick = { onPlay(item, false) }, enabled = item.title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Amber, contentColor = BrandColors.Ink)
            ) {
                Icon(if (item.isAudio) Icons.Rounded.Headphones else Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        resumable -> "Verder vanaf ${formatTime(progress!!.positionMs)}"
                        item.isAudio -> "Luisteren"
                        else -> "Afspelen"
                    }
                )
            }
            if (resumable) {
                IconButton(onClick = { onPlay(item, true) }) { Icon(Icons.Rounded.Replay, "Vanaf het begin", tint = BrandColors.Text) }
            }
            IconButton(onClick = { library.toggleFavorite(item) }) {
                Icon(if (isFavorite) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, if (isFavorite) "Uit Mijn lijst halen" else "Bewaren", tint = BrandColors.Amber)
            }
            IconButton(onClick = { addToList = true }) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Toevoegen aan lijst", tint = BrandColors.Text)
            }
            val watched = progress?.isFinished == true
            IconButton(onClick = { library.local.setWatched(item, !watched) }) {
                Icon(if (watched) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, if (watched) "Gezien" else "Markeer als gezien", tint = if (watched) BrandColors.Amber else BrandColors.Text)
            }
            IconButton(onClick = { share(context, item.title, "https://schatkamer.beeldengeluid.nl/programma/$programId") }) {
                Icon(Icons.Rounded.Share, "Delen", tint = BrandColors.Text)
            }
        }
        if (progress != null && !progress.isFinished && progress.positionMs > 15_000) {
            LinearProgressIndicator(
                progress = { progress.fraction }, color = BrandColors.Amber, trackColor = BrandColors.SurfaceHigh,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(3.dp), drawStopIndicator = {}
            )
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            if (wide) {
                item {
                    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        hero(Modifier.weight(1.1f).aspectRatio(16f / 9f).clip(CardShape))
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            titleBlock()
                            actions()
                        }
                    }
                }
            } else {
                item { hero(Modifier.fillMaxWidth().aspectRatio(16f / 11f)) }
                item { titleBlock() }
                item { actions() }
            }
            when (val d = detailState) {
                is Load.Failed -> item { ErrorState(d.message, viewModel::load) }
                Load.Loading -> item { Column(Modifier.padding(16.dp)) { repeat(3) { Skeleton(Modifier.fillMaxWidth().height(14.dp).padding(vertical = 2.dp)); Spacer(Modifier.height(8.dp)) } } }
                is Load.Ready -> programInfo(d.data, onBrowse)
            }
            if (more.isNotEmpty()) {
                item {
                    SectionHeader("Meer uit ${item.seriesTitle ?: "deze serie"}")
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(more, key = { it.id }) { ProgramCard(it, { onOpen(it) }, width = 200.dp) }
                    }
                }
            }
        }
        TopActions(onBack)
    }
    if (addToList && item.title.isNotBlank()) AddToListSheet(item, library) { addToList = false }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.programInfo(detail: SchatkamerProgramDetail, onBrowse: (BrowseSpec) -> Unit) {
    if (detail.disclaimer != null) {
        item {
            Text(
                detail.disclaimer!!, style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted,
                modifier = Modifier.padding(16.dp).clip(CardShape).background(BrandColors.Surface).padding(12.dp)
            )
        }
    }
    if (detail.description.isNotBlank()) item { ExpandableText(detail.description, collapsedLines = 6) }
    if (detail.credits.isNotEmpty()) {
        item {
            SectionHeader("Wie zie of hoor je")
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                detail.credits.take(16).forEach { c ->
                    Pill(c.name, { onBrowse(BrowseSpec(title = c.name, person = c.name)) })
                }
            }
        }
    }
    val tags = detail.genres.map { it to true } + detail.subjects.filter { s -> detail.credits.none { it.name == s } }.map { it to false }
    if (tags.isNotEmpty()) {
        item {
            SectionHeader("Meer zoals dit")
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.take(14).forEach { (label, isGenre) ->
                    val title = label.replaceFirstChar { it.titlecase() }
                    Pill(title, { onBrowse(if (isGenre) BrowseSpec(title, genre = label) else BrowseSpec(title, subject = label)) })
                }
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

// ----------------------------------------------------------------- bladeren

@Composable
fun BrowseScreen(viewModel: BrowseViewModel, spec: BrowseSpec, onOpen: (SchatkamerItem) -> Unit, onBack: () -> Unit, contentPadding: PaddingValues) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    InfiniteScroll(listState, state, viewModel::loadMore)
    LaunchedEffect(state.filter) { if (!state.loaded) listState.scrollToItem(0) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = 4.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Terug") }
            Column(Modifier.weight(1f)) {
                Text(spec.kindLabel, style = MaterialTheme.typography.labelSmall, color = BrandColors.Amber)
                Text(spec.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CastButton()
        }
        val columns = LocalListColumns.current
        LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            resultsContent(state, onOpen, onFilter = { viewModel.setFilter(it) }, onRetry = viewModel::retry, lockBroadcaster = spec.broadcaster != null, columns = columns)
        }
    }
}

// ------------------------------------------------------------------ verhaal

@Composable
fun StoryScreen(viewModel: StoryViewModel, onOpen: (SchatkamerItem) -> Unit, onBack: () -> Unit, contentPadding: PaddingValues) {
    val story by viewModel.story.collectAsState()
    val columns = LocalListColumns.current
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            when (val s = story) {
                Load.Loading -> item { Column(Modifier.padding(top = 80.dp)) { SkeletonList() } }
                is Load.Failed -> item { Spacer(Modifier.height(80.dp)); ErrorState(s.message, viewModel::load) }
                is Load.Ready -> {
                    item {
                        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
                            Backdrop(s.data.imageUrl, Modifier.fillMaxSize())
                            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                                Text("VERHAAL", style = MaterialTheme.typography.labelSmall, color = BrandColors.Amber)
                                Text(s.data.title, style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                    }
                    if (s.data.description.isNotBlank()) item { ExpandableText(s.data.description) }
                    item { SectionHeader("In dit verhaal") }
                    if (s.data.programs.isEmpty()) item { EmptyState("Geen uitzendingen", "Dit verhaal bevat geen uitzendingen om af te spelen.") }
                    adaptiveItems(s.data.programs, columns, key = { it.id }) { ProgramListItem(it, { onOpen(it) }) }
                }
            }
        }
        TopActions(onBack)
    }
}
