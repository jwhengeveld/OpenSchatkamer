package nl.openschatkamer.mobile

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import nl.openschatkamer.core.ui.AccountViewModel
import nl.openschatkamer.core.ui.ListViewModel
import nl.openschatkamer.core.ui.TimeTravelViewModel
import nl.openschatkamer.mobile.ui.components.ItemActionsSheet
import nl.openschatkamer.mobile.ui.components.ItemStates
import nl.openschatkamer.mobile.ui.components.LocalItemActions
import nl.openschatkamer.mobile.ui.components.LocalItemStates
import nl.openschatkamer.mobile.ui.screens.AccountScreen
import nl.openschatkamer.mobile.ui.screens.DiagnosticsScreen
import nl.openschatkamer.mobile.ui.screens.HistoryScreen
import nl.openschatkamer.mobile.ui.screens.ListScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import kotlinx.coroutines.flow.MutableStateFlow
import nl.openschatkamer.brand.BrandColors
import nl.openschatkamer.core.data.ItemKind
import nl.openschatkamer.core.data.SchatkamerItem
import nl.openschatkamer.mobile.cast.CastAvailability
import nl.openschatkamer.mobile.playback.PlaybackManager
import nl.openschatkamer.core.ui.BrowseSpec
import nl.openschatkamer.core.ui.BrowseViewModel
import nl.openschatkamer.core.ui.DiscoverViewModel
import nl.openschatkamer.core.ui.HomeViewModel
import nl.openschatkamer.core.ui.Load
import nl.openschatkamer.core.ui.ProgramViewModel
import nl.openschatkamer.mobile.ui.Routes
import nl.openschatkamer.core.ui.SearchViewModel
import nl.openschatkamer.core.ui.SeriesViewModel
import nl.openschatkamer.core.ui.StoryViewModel
import nl.openschatkamer.mobile.ui.open
import nl.openschatkamer.mobile.ui.screens.BrowseScreen
import nl.openschatkamer.mobile.ui.screens.DiscoverScreen
import nl.openschatkamer.mobile.ui.screens.HomeScreen
import nl.openschatkamer.mobile.ui.screens.MiniPlayer
import nl.openschatkamer.mobile.ui.screens.MyListScreen
import nl.openschatkamer.mobile.ui.screens.PlayerActions
import nl.openschatkamer.mobile.ui.screens.PlayerScreen
import nl.openschatkamer.mobile.ui.screens.ProgramScreen
import nl.openschatkamer.mobile.ui.screens.SearchScreen
import nl.openschatkamer.mobile.ui.screens.SeriesScreen
import nl.openschatkamer.mobile.ui.screens.StoryScreen
import nl.openschatkamer.mobile.ui.theme.OpenSchatkamerTheme

class MainActivity : AppCompatActivity() {

    private val app by lazy { application as SchatkamerMobileApp }
    private val playerOpen = MutableStateFlow(false)
    private val inPip = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        val started = System.currentTimeMillis()
        splash.setKeepOnScreenCondition {
            app.catalog.cachedHome() == null && System.currentTimeMillis() - started < 2_500
        }
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        CastAvailability.discoverWhileStarted(this)
        // Een link bij het opstarten verwerkt Navigation zelf; alleen "open speler" hier.
        if (intent?.action == PlaybackManager.ACTION_OPEN_PLAYER) playerOpen.value = true

        setContent {
            OpenSchatkamerTheme {
                androidx.compose.material3.Surface(color = BrandColors.Ink) { AppRoot() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Links die binnenkomen terwijl de app al open is (snelkoppeling, gedeelde link). */
    private val pendingLink = kotlinx.coroutines.flow.MutableStateFlow<Intent?>(null)

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == PlaybackManager.ACTION_OPEN_PLAYER) playerOpen.value = true
        else if (intent?.action == Intent.ACTION_VIEW && intent.data != null) pendingLink.value = intent
    }

    /** Thuisknop tijdens een video: verder in een zwevend venster. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val s = app.playback.state.value
        if (playerOpen.value && s.isPlaying && !s.isCasting && s.media?.isAudio != true &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
    }

    override fun onStart() {
        super.onStart()
        app.onForeground()
    }

    @Composable
    private fun AppRoot() {
        val playback = app.playback
        val library = app.library
        val state by playback.state.collectAsState()
        val player by playback.player.collectAsState()
        val showPlayer by playerOpen.collectAsState()
        val pip by inPip.collectAsState()
        val history by library.local.history.collectAsState()
        val continueWatching by library.local.continueWatching.collectAsState()
        val nav = rememberNavController()
        val link by pendingLink.collectAsState()
        LaunchedEffect(link) {
            link?.let { nav.handleDeepLink(it); pendingLink.value = null }
        }

        val homeViewModel: HomeViewModel = viewModel { HomeViewModel(app.catalog) }
        val timeTravelViewModel: TimeTravelViewModel = viewModel { TimeTravelViewModel(app.catalog) }
        val timeTravel by timeTravelViewModel.rows.collectAsState()
        val homeState by homeViewModel.home.collectAsState()
        // Met een bewaarde startpagina is de app er meteen; alleen bij de allereerste start wachten.

        val play: (SchatkamerItem, Boolean) -> Unit = { item, fromStart ->
            playback.play(item, fromStart)
            playerOpen.value = true
        }
        val actions = remember(playback) { playbackActions(playback) }
        var actionItem by remember { mutableStateOf<SchatkamerItem?>(null) }

        // Kaartstatus voor alle schermen: nieuw, voortgang en gezien.
        val home = (homeState as? Load.Ready)?.data
        val itemStates = remember(history, home) {
            val byId = history.associateBy { it.item.id }
            object : ItemStates {
                override fun isNew(item: SchatkamerItem) = home?.isNew(item) == true
                override fun progress(item: SchatkamerItem) = byId[item.id]?.takeIf { !it.isFinished && it.positionMs > 15_000 }?.fraction
                override fun watched(item: SchatkamerItem) = byId[item.id]?.isFinished == true
            }
        }

        // Video draait mee met het toestel; zonder speler volgt de app gewoon het systeem.
        val videoFullscreen = showPlayer && state.hasMedia && !state.isCasting && state.media?.isAudio != true
        LaunchedEffect(videoFullscreen) {
            requestedOrientation = if (videoFullscreen) ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        LaunchedEffect(videoFullscreen, landscape) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (videoFullscreen && landscape) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else controller.show(WindowInsetsCompat.Type.systemBars())
        }

        CompositionLocalProvider(LocalItemActions provides { actionItem = it }, LocalItemStates provides itemStates) {
        Box(Modifier.fillMaxSize().background(BrandColors.Ink)) {
            if (pip) {
                PlayerScreen(state, player, actions, isInPip = true, onClose = {}, onOpenSeries = {})
                return@Box
            }

            Shell(nav, showMini = state.hasMedia && !showPlayer, mini = {
                MiniPlayer(
                    state,
                    onExpand = { playerOpen.value = true },
                    onTogglePlay = { if (state.error != null) playback.retry() else playback.togglePlayPause() },
                    onClose = { playback.stop() }
                )
            }) { padding ->
                NavHost(nav, startDestination = Routes.HOME) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            homeViewModel, continueWatching, timeTravel,
                            onOpen = { nav.open(it) }, onPlay = { play(it, false) },
                            onBrowse = { nav.navigate(Routes.browse(it)) }, contentPadding = padding
                        )
                    }
                    composable(Routes.SEARCH, deepLinks = listOf(navDeepLink { uriPattern = "openschatkamer://search" })) {
                        val vm: SearchViewModel = viewModel { SearchViewModel(app.catalog, applicationContext) }
                        SearchScreen(vm, onOpen = { nav.open(it) }, onBrowse = { nav.navigate(Routes.browse(it)) }, contentPadding = padding)
                    }
                    composable(Routes.DISCOVER) {
                        val vm: DiscoverViewModel = viewModel { DiscoverViewModel(app.catalog) }
                        DiscoverScreen(vm, onBrowse = { nav.navigate(Routes.browse(it)) }, contentPadding = padding)
                    }
                    composable(Routes.MY_LIST, deepLinks = listOf(navDeepLink { uriPattern = "openschatkamer://mylist" })) {
                        MyListScreen(
                            library,
                            onOpen = { nav.open(it) }, onPlay = { play(it, false) },
                            onOpenList = { nav.navigate(Routes.list(it.key)) },
                            onAccount = { nav.navigate(Routes.ACCOUNT) },
                            onHistory = { nav.navigate(Routes.HISTORY) },
                            onDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) },
                            contentPadding = padding
                        )
                    }
                    composable(
                        Routes.SERIES, listOf(navArgument("id") { type = NavType.StringType }),
                        deepLinks = listOf(
                            navDeepLink { uriPattern = "openschatkamer://series/{id}" },
                            navDeepLink { uriPattern = "https://schatkamer.beeldengeluid.nl/serie/{id}" },
                            navDeepLink { uriPattern = "https://schatkamer.beeldengeluid.nl/serie/{id}/{slug}" }
                        )
                    ) { entry ->
                        val id = entry.arguments?.getString("id").orEmpty()
                        val vm: SeriesViewModel = viewModel { SeriesViewModel(app.catalog, id) }
                        SeriesScreen(
                            vm, id, library,
                            onOpen = { nav.open(it) }, onPlay = { play(it, false) },
                            onBrowse = { nav.navigate(Routes.browse(it)) },
                            onBack = { nav.popBackStack() }, contentPadding = padding
                        )
                    }
                    composable(
                        Routes.PROGRAM, listOf(navArgument("id") { type = NavType.StringType }),
                        deepLinks = listOf(
                            navDeepLink { uriPattern = "openschatkamer://program/{id}" },
                            navDeepLink { uriPattern = "https://schatkamer.beeldengeluid.nl/programma/{id}" },
                            navDeepLink { uriPattern = "https://schatkamer.beeldengeluid.nl/programma/{id}/{slug}" },
                            navDeepLink { uriPattern = "https://schatkamer.beeldengeluid.nl/serie/{sid}/{sslug}/aflevering/{id}" }
                        )
                    ) { entry ->
                        val id = entry.arguments?.getString("id").orEmpty()
                        val vm: ProgramViewModel = viewModel { ProgramViewModel(app.video, app.catalog, id) }
                        // Alvast de stream ophalen: afspelen begint dan meteen.
                        LaunchedEffect(id) { app.video.resolveStream(id) }
                        ProgramScreen(
                            vm, id, library,
                            onPlay = play, onOpen = { nav.open(it) },
                            onBrowse = { nav.navigate(Routes.browse(it)) },
                            onBack = { nav.popBackStack() }, contentPadding = padding
                        )
                    }
                    composable(Routes.STORY, listOf(navArgument("slug") { type = NavType.StringType })) { entry ->
                        val slug = entry.arguments?.getString("slug").orEmpty()
                        val vm: StoryViewModel = viewModel { StoryViewModel(app.catalog, slug) }
                        StoryScreen(vm, onOpen = { nav.open(it) }, onBack = { nav.popBackStack() }, contentPadding = padding)
                    }
                    composable(Routes.BROWSE, listOf(navArgument("spec") { type = NavType.StringType; defaultValue = "" })) { entry ->
                        val spec = BrowseSpec.decode(entry.arguments?.getString("spec").orEmpty())
                        val vm: BrowseViewModel = viewModel { BrowseViewModel(app.catalog, spec.toFilter(app.catalog)) }
                        BrowseScreen(vm, spec, onOpen = { nav.open(it) }, onBack = { nav.popBackStack() }, contentPadding = padding)
                    }
                    composable(Routes.LIST, listOf(navArgument("key") { type = NavType.StringType })) { entry ->
                        val key = entry.arguments?.getString("key").orEmpty()
                        val favorites by library.favorites.collectAsState()
                        val vm: ListViewModel? = if (key == "local:favorites") null else viewModel { ListViewModel(library, key) }
                        ListScreen(
                            vm, favorites.filter { it.kind != ItemKind.SERIES }, library,
                            onOpen = { nav.open(it) }, onPlay = { play(it, false) },
                            onBack = { nav.popBackStack() }, contentPadding = padding
                        )
                    }
                    composable(Routes.ACCOUNT) {
                        val vm: AccountViewModel = viewModel { AccountViewModel(app.account) }
                        AccountScreen(vm, library, onBack = { nav.popBackStack() })
                    }
                    composable(Routes.HISTORY) {
                        HistoryScreen(library, onOpen = { nav.open(it) }, onBack = { nav.popBackStack() }, contentPadding = padding)
                    }
                    composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(onBack = { nav.popBackStack() }) }
                }
            }

            AnimatedVisibility(
                visible = showPlayer && state.hasMedia,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                BackHandler { playerOpen.value = false }
                PlayerScreen(
                    state, player, actions, isInPip = false,
                    onClose = { playerOpen.value = false },
                    onOpenSeries = { playerOpen.value = false; nav.open(it) }
                )
            }

            actionItem?.let { item ->
                ItemActionsSheet(item, library, onDismiss = { actionItem = null }, onOpenSeries = { nav.open(it) })
            }

        }
        }
    }

    private fun playbackActions(p: PlaybackManager) = object : PlayerActions {
        override fun togglePlayPause() = p.togglePlayPause()
        override fun seekTo(ms: Long) = p.seekTo(ms)
        override fun seekBy(ms: Long) = p.seekBy(ms)
        override fun retry() = p.retry()
        override fun setSubtitles(on: Boolean) = p.setSubtitles(on)
        override fun stopCasting() = p.stopCasting()
        override fun setCastVolume(v: Float) = p.setCastVolume(v)
        override fun toggleCastMute() = p.toggleCastMute()
        override fun setSpeed(speed: Float) = p.setSpeed(speed)
        override fun playNext() = p.playNextNow()
        override fun cancelNext() = p.cancelNext()
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "Start", Icons.Rounded.Home),
    Tab(Routes.SEARCH, "Zoeken", Icons.Rounded.Search),
    Tab(Routes.DISCOVER, "Ontdekken", Icons.Rounded.Explore),
    Tab(Routes.MY_LIST, "Mijn lijst", Icons.Rounded.Bookmarks)
)

/** Telefoon: tabbalk onderaan. Tablet of liggend: een zijbalk. */
@Composable
private fun Shell(
    nav: NavHostController,
    showMini: Boolean,
    mini: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val activeTab = TABS.firstOrNull { it.route == current }?.route
        ?: TABS.lastOrNull { tab -> tab.route != Routes.HOME && nav.currentBackStack.value.any { it.destination.route == tab.route } }?.route
        ?: Routes.HOME
    val select: (String) -> Unit = { route ->
        if (route == activeTab) {
            // Nogmaals op het actieve tabblad: terug naar het begin daarvan.
            if (!nav.popBackStack(route, inclusive = false)) nav.navigate(route) { launchSingleTop = true }
        } else {
            nav.navigate(route) {
                popUpTo(Routes.HOME) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(containerColor = BrandColors.Surface, modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                    TABS.forEach { tab ->
                        NavigationRailItem(
                            selected = activeTab == tab.route, onClick = { select(tab.route) },
                            icon = { Icon(tab.icon, null) }, label = { Text(tab.label) },
                            colors = NavigationRailItemDefaults.colors(selectedIconColor = BrandColors.Ink, indicatorColor = BrandColors.Amber)
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Box(Modifier.weight(1f)) { content(PaddingValues(bottom = 24.dp)) }
                    if (showMini) Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) { mini() }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { content(PaddingValues(bottom = 16.dp)) }
                if (showMini) mini()
                NavigationBar(containerColor = BrandColors.Surface) {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = activeTab == tab.route, onClick = { select(tab.route) },
                            icon = { Icon(tab.icon, null) }, label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = BrandColors.Ink, indicatorColor = BrandColors.Amber, selectedTextColor = BrandColors.Amber)
                        )
                    }
                }
            }
        }
    }
}
