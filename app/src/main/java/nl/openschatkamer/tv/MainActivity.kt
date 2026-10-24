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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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
import nl.openschatkamer.brand.BrandSplash
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
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent { TvTheme { androidx.compose.material3.Surface(color = BrandColors.Ink) { TvRoot() } } }
    }

    @Composable
    private fun TvRoot() {
        val nav = rememberNavController()
        val favorites by app.favorites.favorites.collectAsState()
        val progressEntries by app.progress.entries.collectAsState()
        val continueWatching = remember(progressEntries) { app.progress.continueWatching }
        val homeViewModel: HomeViewModel = viewModel { HomeViewModel(app.catalog) }
        val homeState by homeViewModel.home.collectAsState()
        var playing by remember { mutableStateOf<Pair<SchatkamerItem, Boolean>?>(null) }
        var splashDone by rememberSaveable { mutableStateOf(false) }
        var splashTimedOut by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(4_000); splashTimedOut = true }

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
        val browse: (BrowseSpec) -> Unit = { nav.navigate(browseRoute(it)) }

        // Na elke navigatie hoort de focus in de inhoud, niet in de zijbalk. De inhoud
        // laadt vaak nog; probeer het dus even tot er iets focusbaars is.
        val contentFocus = remember { FocusRequester() }
        var contentHasFocus by remember { mutableStateOf(false) }
        val backStack by nav.currentBackStackEntryAsState()
        LaunchedEffect(backStack?.id, playing == null) {
            if (playing != null) return@LaunchedEffect
            // Eerst de overgang laten aflopen: tot dan houdt het vorige scherm de focus.
            kotlinx.coroutines.delay(750)
            contentFocus.requestFocusSafely()
            repeat(15) {
                kotlinx.coroutines.delay(250)
                if (contentHasFocus) return@LaunchedEffect
                contentFocus.requestFocusSafely()
            }
        }

        Box(Modifier.fillMaxSize().background(BrandColors.Ink)) {
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
                        composable(Dest.HOME) { TvHomeScreen(homeViewModel, continueWatching, open) { play(it, false) } }
                        composable(Dest.SEARCH) {
                            val vm: SearchViewModel = viewModel { SearchViewModel(app.catalog, applicationContext) }
                            TvSearchScreen(vm, open)
                        }
                        composable(Dest.DISCOVER) {
                            val vm: DiscoverViewModel = viewModel { DiscoverViewModel(app.catalog) }
                            TvDiscoverScreen(vm, browse)
                        }
                        composable(Dest.MY_LIST) { TvMyListScreen(favorites, continueWatching, open) { play(it, false) } }
                        composable(Dest.SERIES, listOf(navArgument("id") { type = NavType.StringType })) { e ->
                            val id = e.arguments?.getString("id").orEmpty()
                            val vm: SeriesViewModel = viewModel { SeriesViewModel(app.catalog, id) }
                            TvSeriesScreen(vm, favorites.any { it.id == id }, { app.favorites.toggleFavorite(it) }, app.progress::progressOf, open) { play(it, false) }
                        }
                        composable(Dest.PROGRAM, listOf(navArgument("id") { type = NavType.StringType })) { e ->
                            val id = e.arguments?.getString("id").orEmpty()
                            val vm: ProgramViewModel = viewModel { ProgramViewModel(app.video, app.catalog, id) }
                            TvProgramScreen(
                                vm, favorites.any { it.id == id }, progressEntries.firstOrNull { it.item.id == id },
                                { app.favorites.toggleFavorite(it) }, play, open, browse
                            )
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
                    }
                }
            }

            playing?.let { (item, fromStart) ->
                BackHandler { playing = null }
                TvPlayer(item, fromStart, app.video, app.progress, onClose = { playing = null })
            }

            if (!splashDone) {
                BrandSplash(ready = homeState !is Load.Loading || splashTimedOut, onFinished = { splashDone = true })
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
    Column(
        Modifier.fillMaxHeight().width(124.dp).background(BrandColors.Surface).padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(Modifier.height(40.dp))
        ENTRIES.forEach { e ->
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val selected = current == e.route
            Column(
                Modifier
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
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .width(84.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val color = if (focused) BrandColors.Ink else if (selected) BrandColors.Amber else BrandColors.TextMuted
                Icon(e.icon, null, tint = color, modifier = Modifier.size(28.dp))
                Text(e.label, color = color, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun FocusRequester.requestFocusSafely() {
    runCatching { requestFocus() }
}
