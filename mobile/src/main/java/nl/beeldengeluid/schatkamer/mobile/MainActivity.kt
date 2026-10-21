package nl.beeldengeluid.schatkamer.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import nl.beeldengeluid.schatkamer.brand.SchatkamerSplash
import nl.beeldengeluid.schatkamer.core.data.PlayableMedia
import nl.beeldengeluid.schatkamer.core.data.SchatkamerItem
import nl.beeldengeluid.schatkamer.mobile.ui.DiscoverViewModel
import nl.beeldengeluid.schatkamer.mobile.ui.HomeViewModel
import nl.beeldengeluid.schatkamer.mobile.ui.ProgramDetailViewModel
import nl.beeldengeluid.schatkamer.mobile.ui.SearchViewModel
import nl.beeldengeluid.schatkamer.mobile.ui.components.MiniPlayer
import nl.beeldengeluid.schatkamer.mobile.ui.screens.DiscoverScreen
import nl.beeldengeluid.schatkamer.mobile.ui.screens.FavoritesScreen
import nl.beeldengeluid.schatkamer.mobile.ui.screens.HomeScreen
import nl.beeldengeluid.schatkamer.mobile.ui.screens.PlayerScreen
import nl.beeldengeluid.schatkamer.mobile.ui.screens.ProgramDetailScreen
import nl.beeldengeluid.schatkamer.mobile.ui.screens.SearchScreen
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerCyan
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerDarkBackground
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerDarkElevated
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerTextMuted
import nl.beeldengeluid.schatkamer.mobile.ui.theme.SchatkamerTheme

class MainActivity : ComponentActivity() {

    private val app by lazy { application as SchatkamerMobileApp }

    private val homeViewModel by viewModels<HomeViewModel> {
        viewModelFactory { HomeViewModel(app.catalog) }
    }
    private val searchViewModel by viewModels<SearchViewModel> {
        viewModelFactory { SearchViewModel(app.catalog) }
    }
    private val discoverViewModel by viewModels<DiscoverViewModel> {
        viewModelFactory { DiscoverViewModel(app.catalog) }
    }
    private val detailViewModel by viewModels<ProgramDetailViewModel> {
        viewModelFactory { ProgramDetailViewModel(app.video, app.favorites) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SchatkamerTheme {
                MainContent(
                    app = app,
                    homeViewModel = homeViewModel,
                    searchViewModel = searchViewModel,
                    discoverViewModel = discoverViewModel,
                    detailViewModel = detailViewModel
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            app.playback.release()
        }
    }
}

@Composable
fun MainContent(
    app: SchatkamerMobileApp,
    homeViewModel: HomeViewModel,
    searchViewModel: SearchViewModel,
    discoverViewModel: DiscoverViewModel,
    detailViewModel: ProgramDetailViewModel
) {
    var splashFinished by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedProgram by remember { mutableStateOf<SchatkamerItem?>(null) }
    var showPlayerScreen by remember { mutableStateOf(false) }

    val playbackState by app.playback.state.collectAsState()

    // BackHandler navigatie
    BackHandler(enabled = showPlayerScreen || selectedProgram != null) {
        if (showPlayerScreen) {
            showPlayerScreen = false
        } else if (selectedProgram != null) {
            selectedProgram = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    // MiniPlayer zolang video/audio geladen is en niet op volledig spelerscherm
                    if (playbackState.media != null && !showPlayerScreen) {
                        MiniPlayer(
                            playbackState = playbackState,
                            onPlayPause = {
                                if (playbackState.isPlaying) app.playback.pause() else app.playback.resume()
                            },
                            onClick = { showPlayerScreen = true }
                        )
                    }

                    // Onderste navigatiebalk
                    NavigationBar(
                        containerColor = SchatkamerDarkElevated,
                        contentColor = SchatkamerCyan
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0 && selectedProgram == null,
                            onClick = { selectedTab = 0; selectedProgram = null },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Start") },
                            label = { Text("Start") },
                            colors = navigationBarColors()
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1 && selectedProgram == null,
                            onClick = { selectedTab = 1; selectedProgram = null },
                            icon = { Icon(Icons.Default.Search, contentDescription = "Zoeken") },
                            label = { Text("Zoeken") },
                            colors = navigationBarColors()
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2 && selectedProgram == null,
                            onClick = { selectedTab = 2; selectedProgram = null },
                            icon = { Icon(Icons.Default.Explore, contentDescription = "Ontdek") },
                            label = { Text("Ontdek") },
                            colors = navigationBarColors()
                        )
                        NavigationBarItem(
                            selected = selectedTab == 3 && selectedProgram == null,
                            onClick = { selectedTab = 3; selectedProgram = null },
                            icon = { Icon(Icons.Default.Bookmark, contentDescription = "Mijn Lijst") },
                            label = { Text("Mijn Lijst") },
                            colors = navigationBarColors()
                        )
                    }
                }
            },
            containerColor = SchatkamerDarkBackground
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (selectedTab) {
                    0 -> HomeScreen(
                        viewModel = homeViewModel,
                        onSelectItem = { selectedProgram = it }
                    )
                    1 -> SearchScreen(
                        viewModel = searchViewModel,
                        onSelectItem = { selectedProgram = it }
                    )
                    2 -> DiscoverScreen(
                        viewModel = discoverViewModel,
                        onSelectItem = { selectedProgram = it }
                    )
                    3 -> FavoritesScreen(
                        favoritesRepo = app.favorites,
                        onSelectItem = { selectedProgram = it },
                        onExplore = { selectedTab = 0 }
                    )
                }

                // Programma detail scherm
                if (selectedProgram != null) {
                    ProgramDetailScreen(
                        item = selectedProgram!!,
                        viewModel = detailViewModel,
                        onBack = { selectedProgram = null },
                        onPlay = { media ->
                            app.playback.play(media)
                            showPlayerScreen = true
                        },
                        onSelectEpisode = { selectedProgram = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Volledig scherm Player
        AnimatedVisibility(
            visible = showPlayerScreen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PlayerScreen(
                playbackManager = app.playback,
                onBack = { showPlayerScreen = false },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Splash scherm
        if (!splashFinished) {
            SchatkamerSplash(
                ready = true,
                onFinished = { splashFinished = true }
            )
        }
    }
}

@Composable
private fun navigationBarColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = SchatkamerCyan,
    selectedTextColor = SchatkamerCyan,
    indicatorColor = Color.Transparent,
    unselectedIconColor = SchatkamerTextMuted,
    unselectedTextColor = SchatkamerTextMuted
)

private inline fun <reified T : ViewModel> viewModelFactory(crossinline creator: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = creator() as VM
    }
