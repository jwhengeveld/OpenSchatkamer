@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package nl.openschatkamer.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import nl.openschatkamer.core.ui.AccountViewModel
import nl.openschatkamer.core.ui.ListViewModel
import nl.openschatkamer.core.ui.TimeTravelViewModel
import nl.openschatkamer.tv.ui.LocalTvItemActions
import nl.openschatkamer.tv.ui.LocalTvItemStates
import nl.openschatkamer.tv.ui.TvAccountScreen
import nl.openschatkamer.tv.ui.TvActionsDialog
import nl.openschatkamer.tv.ui.TvItemStates
import nl.openschatkamer.tv.ui.TvListScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import nl.openschatkamer.brand.BrandColors
import nl.openschatkamer.core.data.ItemKind
import nl.openschatkamer.core.data.SchatkamerItem
import nl.openschatkamer.core.ui.BrowseSpec
import nl.openschatkamer.core.ui.BrowseViewModel
import nl.openschatkamer.core.ui.DiscoverViewModel
import nl.openschatkamer.core.ui.HomeViewModel
import nl.openschatkamer.core.ui.ItemMemo
import nl.openschatkamer.core.ui.Load
import nl.openschatkamer.core.ui.ProgramViewModel
import nl.openschatkamer.core.ui.SearchViewModel
import nl.openschatkamer.core.ui.SeriesViewModel
import nl.openschatkamer.core.ui.StoryViewModel
import nl.openschatkamer.tv.ui.TvBrowseScreen
import nl.openschatkamer.tv.ui.TvDiscoverScreen
import nl.openschatkamer.tv.ui.TvHomeScreen
import nl.openschatkamer.tv.ui.TvMyListScreen
import nl.openschatkamer.tv.ui.TvPlayer
import nl.openschatkamer.tv.ui.TvProgramScreen
import nl.openschatkamer.tv.ui.TvSearchScreen
import nl.openschatkamer.tv.ui.TvSeriesScreen
import nl.openschatkamer.tv.ui.TvStoryScreen
import nl.openschatkamer.tv.ui.TvTheme

private object Dest {
    const val HOME = "home"
    const val SEARCH = "search"
    const val DISCOVER = "discover"
    const val MY_LIST = "mylist"
    const val SERIES = "series/{id}"
    const val PROGRAM = "program/{id}"
    const val STORY = "story/{slug}"
    const val BROWSE = "browse?spec={spec}"
}

class MainActivity : ComponentActivity() {

    private val app by lazy { application as SchatkamerTvApp }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        val started = System.currentTimeMillis()
        splash.setKeepOnScreenCondition {
            app.catalog.cachedHome() == null && System.currentTimeMillis() - started < 2_500
        }
        super.onCreate(savedInstanceState)
        handleLink(intent)
        setContent { TvTheme { androidx.compose.material3.Surface(color = BrandColors.Ink) { TvRoot() } } }
    }

    override fun onStart() {
        super.onStart()
        app.onForeground()
    }

    /** openschatkamer://program/{id}?play=1 (vanuit "Verder kijken" op het tv-startscherm). */
    private val pendingLink = kotlinx.coroutines.flow.MutableStateFlow<android.net.Uri?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    private fun handleLink(intent: android.content.Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "openschatkamer") pendingLink.value = data
    }

    @Composable
    private fun TvRoot() {
        val nav = rememberNavController()
        val library = app.library
        val history by library.local.history.collectAsState()
        val continueWatching by library.local.continueWatching.collectAsState()
        val homeViewModel: HomeViewModel = viewModel { HomeViewModel(app.catalog) }
        val timeTravelViewModel: TimeTravelViewModel = viewModel { TimeTravelViewModel(app.catalog) }
        val timeTravel by timeTravelViewModel.rows.collectAsState()
        val homeState by homeViewModel.home.collectAsState()
        var playing by remember { mutableStateOf<Pair<SchatkamerItem, Boolean>?>(null) }
        var actionItem by remember { mutableStateOf<SchatkamerItem?>(null) }

        val open: (SchatkamerItem) -> Unit = { item ->
            ItemMemo.remember(item)
            when (item.kind) {
                ItemKind.PROGRAM -> nav.navigate("program/${item.id}")
                ItemKind.SERIES -> nav.navigate("series/${item.seriesId ?: item.id}")
                ItemKind.STORY -> nav.navigate("story/${android.net.Uri.encode(item.id)}")
                ItemKind.PERSON -> nav.navigate(browseRoute(BrowseSpec(item.title, person = item.title)))
                ItemKind.BROADCASTER -> nav.navigate(browseRoute(BrowseSpec(item.title, broadcaster = item.title)))
            }
        }
        val play: (SchatkamerItem, Boolean) -> Unit = { item, fromStart -> playing = item to fromStart }
        val link by pendingLink.collectAsState()
        LaunchedEffect(link) {
            val uri = link ?: return@LaunchedEffect
            pendingLink.value = null
            val id = uri.pathSegments.firstOrNull() ?: return@LaunchedEffect
            when (uri.host) {
                "program" -> if (uri.getQueryParameter("play") == "1") {
                    // Meteen verder kijken; de gegevens voor titel en beeld halen we erbij.
                    val item = app.video.getProgramDetail(id).getOrNull()?.toItem() ?: SchatkamerItem(id = id, title = "")
                    playing = item to false
                } else nav.navigate("program/$id")
                "series" -> nav.navigate("series/$id")
            }
        }
        val browse: (BrowseSpec) -> Unit = { nav.navigate(browseRoute(it)) }

        val home = (homeState as? Load.Ready)?.data
        val itemStates = remember(history, home) {
            val byId = history.associateBy { it.item.id }
            object : TvItemStates {
                override fun isNew(item: SchatkamerItem) = home?.isNew(item) == true
                override fun progress(item: SchatkamerItem) = byId[item.id]?.takeIf { !it.isFinished && it.positionMs > 15_000 }?.fraction
                override fun watched(item: SchatkamerItem) = byId[item.id]?.isFinished == true
            }
        }

        // Na elke navigatie hoort de focus in de inhoud, niet in de zijbalk. De inhoud
        // laadt soms nog (koude start); blijf het proberen tot hij er is, maar stop
        // zodra de gebruiker zelf een pijltje gebruikt, zodat we nooit de focus afpakken.
        val contentFocus = remember { FocusRequester() }
        var contentHasFocus by remember { mutableStateOf(false) }
        var userMoved by remember { mutableStateOf(false) }
        val backStack by nav.currentBackStackEntryAsState()
        LaunchedEffect(backStack?.id, playing == null) {
            if (playing != null) return@LaunchedEffect
            userMoved = false
            kotlinx.coroutines.delay(600)
            val until = System.currentTimeMillis() + 20_000
            while (!contentHasFocus && !userMoved && System.currentTimeMillis() < until) {
                contentFocus.requestFocusSafely()
                kotlinx.coroutines.delay(250)
            }
        }

        CompositionLocalProvider(LocalTvItemActions provides { actionItem = it }, LocalTvItemStates provides itemStates) {
        Box(
            Modifier
                .fillMaxSize()
                .background(BrandColors.Ink)
                .onPreviewKeyEvent { event ->
                    if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                        event.key in listOf(
                            androidx.compose.ui.input.key.Key.DirectionUp, androidx.compose.ui.input.key.Key.DirectionDown,
                            androidx.compose.ui.input.key.Key.DirectionLeft, androidx.compose.ui.input.key.Key.DirectionRight
                        )
                    ) userMoved = true
                    false
                }
        ) {
            Row(Modifier.fillMaxSize()) {
                SideNav(nav)
                Box(
                    Modifier
                        .weight(1f)
                        .onFocusChanged { contentHasFocus = it.hasFocus }
                        .focusRequester(contentFocus)
                        .focusGroup()
                ) {
                    NavHost(nav, startDestination = Dest.HOME) {
                        composable(Dest.HOME) { TvHomeScreen(homeViewModel, continueWatching, timeTravel, open, { play(it, false) }, browse) }
                        composable(Dest.SEARCH) {
                            val vm: SearchViewModel = viewModel { SearchViewModel(app.catalog, applicationContext) }
                            TvSearchScreen(vm, open)
                        }
                        composable(Dest.DISCOVER) {
                            val vm: DiscoverViewModel = viewModel { DiscoverViewModel(app.catalog) }
                            TvDiscoverScreen(vm, browse)
                        }
                        composable(Dest.MY_LIST) {
                            TvMyListScreen(library, open, { play(it, false) }, onOpenList = { nav.navigate("list/${android.net.Uri.encode(it.key)}") }, onAccount = { nav.navigate("account") })
                        }
                        composable(Dest.SERIES, listOf(navArgument("id") { type = NavType.StringType })) { e ->
                            val id = e.arguments?.getString("id").orEmpty()
                            val vm: SeriesViewModel = viewModel { SeriesViewModel(app.catalog, id) }
                            TvSeriesScreen(vm, id, library, open) { play(it, false) }
                        }
                        composable(Dest.PROGRAM, listOf(navArgument("id") { type = NavType.StringType })) { e ->
                            val id = e.arguments?.getString("id").orEmpty()
                            val vm: ProgramViewModel = viewModel { ProgramViewModel(app.video, app.catalog, id) }
                            LaunchedEffect(id) { app.video.resolveStream(id) }
                            TvProgramScreen(vm, library, onAddToList = { actionItem = it }, onPlay = play, onOpen = open, onBrowse = browse)
                        }
                        composable(Dest.STORY, listOf(navArgument("slug") { type = NavType.StringType })) { e ->
                            val slug = e.arguments?.getString("slug").orEmpty()
                            val vm: StoryViewModel = viewModel { StoryViewModel(app.catalog, slug) }
                            TvStoryScreen(vm, open)
                        }
                        composable(Dest.BROWSE, listOf(navArgument("spec") { type = NavType.StringType; defaultValue = "" })) { e ->
                            val spec = BrowseSpec.decode(e.arguments?.getString("spec").orEmpty())
                            val vm: BrowseViewModel = viewModel { BrowseViewModel(app.catalog, spec.toFilter(app.catalog)) }
                            TvBrowseScreen(vm, spec, open)
                        }
                        composable("list/{key}", listOf(navArgument("key") { type = NavType.StringType })) { e ->
                            val key = e.arguments?.getString("key").orEmpty()
                            val favorites by library.favorites.collectAsState()
                            val vm: ListViewModel? = if (key == "local:favorites") null else viewModel { ListViewModel(library, key) }
                            TvListScreen(vm, favorites.filter { it.kind != ItemKind.SERIES }, "Bewaarde uitzendingen", open) { play(it, false) }
                        }
                        composable("account") {
                            val vm: AccountViewModel = viewModel { AccountViewModel(app.account) }
                            TvAccountScreen(vm, onBack = { nav.popBackStack() })
                        }
                    }
                }
            }

            playing?.let { (item, fromStart) ->
                BackHandler { playing = null }
                key(item.id) {
                    TvPlayer(
                        item, fromStart, app.video, app.local, app.catalog,
                        onPlayNext = { next -> playing = next to true },
                        onClose = { playing = null }
                    )
                }
            }

            actionItem?.let { item -> TvActionsDialog(item, library, onDismiss = { actionItem = null }, onOpenSeries = open) }

        }
        }
    }

    private fun browseRoute(spec: BrowseSpec) = "browse?spec=${android.net.Uri.encode(spec.encode())}"
}

private data class NavEntry(val route: String, val label: String, val icon: ImageVector)

private val ENTRIES = listOf(
    NavEntry(Dest.HOME, "Start", Icons.Rounded.Home),
    NavEntry(Dest.SEARCH, "Zoeken", Icons.Rounded.Search),
    NavEntry(Dest.DISCOVER, "Ontdekken", Icons.Rounded.Explore),
    NavEntry(Dest.MY_LIST, "Mijn lijst", Icons.Rounded.Bookmarks)
)

@Composable
private fun SideNav(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    // Het tabblad waar je bent; diepere schermen horen bij het tabblad waar je vandaan kwam.
    val activeIndex = ENTRIES.indexOfFirst { it.route == current }.takeIf { it >= 0 }
        ?: ENTRIES.indexOfLast { e -> e.route != Dest.HOME && nav.currentBackStack.value.any { it.destination.route == e.route } }.takeIf { it >= 0 }
        ?: 0
    val requesters = remember { ENTRIES.map { FocusRequester() } }
    Column(
        Modifier
            .fillMaxHeight().width(88.dp).background(BrandColors.Surface).padding(vertical = 28.dp)
            // Naar links de zijbalk in: altijd op het actieve tabblad landen (zoals Google TV).
            .focusProperties { enter = { requesters[activeIndex] } }
            .focusGroup(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Spacer(Modifier.height(40.dp))
        ENTRIES.forEachIndexed { index, e ->
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val selected = index == activeIndex
            Column(
                Modifier
                    .focusRequester(requesters[index])
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        when {
                            focused -> BrandColors.Amber
                            selected -> BrandColors.SurfaceHigh
                            else -> androidx.compose.ui.graphics.Color.Transparent
                        }
                    )
                    .clickable(interactionSource = interaction, indication = null) {
                        nav.navigate(e.route) {
                            popUpTo(Dest.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                    .padding(horizontal = 4.dp, vertical = 8.dp)
                    .width(68.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val color = if (focused) BrandColors.Ink else if (selected) BrandColors.Amber else BrandColors.TextMuted
                Icon(e.icon, null, tint = color, modifier = Modifier.size(24.dp))
                Text(e.label, color = color, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

private fun FocusRequester.requestFocusSafely() {
    runCatching { requestFocus() }
}
