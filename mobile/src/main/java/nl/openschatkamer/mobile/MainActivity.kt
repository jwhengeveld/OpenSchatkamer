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
import kotlinx.coroutines.flow.MutableStateFlow
import nl.openschatkamer.brand.BrandColors
import nl.openschatkamer.brand.BrandSplash
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
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        CastAvailability.discoverWhileStarted(this)
        handleIntent(intent)

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

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == PlaybackManager.ACTION_OPEN_PLAYER) playerOpen.value = true
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

    @Composable
    private fun AppRoot() {
        val playback = app.playback
        val state by playback.state.collectAsState()
        val player by playback.player.collectAsState()
        val showPlayer by playerOpen.collectAsState()
        val pip by inPip.collectAsState()
        val favorites by app.favorites.favorites.collectAsState()
        val progressEntries by app.progress.entries.collectAsState()
        val continueWatching = remember(progressEntries) { app.progress.continueWatching }
        val nav = rememberNavController()

        val homeViewModel: HomeViewModel = viewModel { HomeViewModel(app.catalog) }
        val homeState by homeViewModel.home.collectAsState()
        var splashDone by rememberSaveable { mutableStateOf(false) }
        var splashTimedOut by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(4_000); splashTimedOut = true }

        val play: (SchatkamerItem, Boolean) -> Unit = { item, fromStart ->
            playback.play(item, fromStart)
            playerOpen.value = true
        }
        val actions = remember(playback) { playbackActions(playback) }

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
                        HomeScreen(homeViewModel, continueWatching, onOpen = { nav.open(it) }, onPlay = { play(it, false) }, contentPadding = padding)
                    }
                    composable(Routes.SEARCH) {
                        val vm: SearchViewModel = viewModel { SearchViewModel(app.catalog, applicationContext) }
                        SearchScreen(vm, onOpen = { nav.open(it) }, contentPadding = padding)
                    }
                    composable(Routes.DISCOVER) {
                        val vm: DiscoverViewModel = viewModel { DiscoverViewModel(app.catalog) }
                        DiscoverScreen(vm, onBrowse = { nav.navigate(Routes.browse(it)) }, contentPadding = padding)
                    }
                    composable(Routes.MY_LIST) {
                        MyListScreen(
                            favorites, continueWatching,
                            onOpen = { nav.open(it) }, onPlay = { play(it, false) },
                            onRemoveFavorite = app.favorites::removeFavorite, onRemoveProgress = app.progress::remove,
                            contentPadding = padding
                        )
                    }
                    composable(Routes.SERIES, listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                        val id = entry.arguments?.getString("id").orEmpty()
                        val vm: SeriesViewModel = viewModel { SeriesViewModel(app.catalog, id) }
                        SeriesScreen(
                            vm, id, isFavorite = favorites.any { it.id == id },
                            onToggleFavorite = { app.favorites.toggleFavorite(it) },
                            progressOf = app.progress::progressOf,
                            onOpen = { nav.open(it) }, onPlay = { play(it, false) },
                            onBrowse = { nav.navigate(Routes.browse(it)) },
                            onBack = { nav.popBackStack() }, contentPadding = padding
                        )
                    }
                    composable(Routes.PROGRAM, listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                        val id = entry.arguments?.getString("id").orEmpty()
                        val vm: ProgramViewModel = viewModel { ProgramViewModel(app.video, app.catalog, id) }
                        ProgramScreen(
                            vm, id, isFavorite = favorites.any { it.id == id },
                            progress = progressEntries.firstOrNull { it.item.id == id },
                            onToggleFavorite = { app.favorites.toggleFavorite(it) },
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

            if (!splashDone) {
                BrandSplash(
                    ready = homeState !is Load.Loading || splashTimedOut,
                    onFinished = { splashDone = true }
                )
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
    val select: (String) -> Unit = { route ->
        if (route == current) {
            // Nogmaals op het tabblad tikken: terug naar het begin van dat tabblad.
            nav.popBackStack(route, inclusive = false)
        } else {
            nav.navigate(route) {
                popUpTo(Routes.HOME) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    val activeTab = TABS.firstOrNull { it.route == current }?.route
        ?: TABS.firstOrNull { tab -> nav.currentBackStack.value.any { it.destination.route == tab.route } && tab.route != Routes.HOME }?.route
        ?: Routes.HOME

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
