package nl.beeldengeluid.schatkamer.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import nl.beeldengeluid.schatkamer.mobile.cast.CastAvailability
import nl.beeldengeluid.schatkamer.mobile.playback.PlaybackManager
import nl.beeldengeluid.schatkamer.mobile.ui.DiscoverViewModel
import nl.beeldengeluid.schatkamer.mobile.ui.HomeViewModel
import nl.beeldengeluid.schatkamer.mobile.ui.MainNavigationViewModel
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

class MainActivity : AppCompatActivity() {

    private val app by lazy { application as SchatkamerMobileApp }

    private val navViewModel by viewModels<MainNavigationViewModel>()
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
        CastAvailability.discoverWhileStarted(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        handleIntent(intent)

        setContent {
            SchatkamerTheme {
                MainContent(
                    app = app,
                    navViewModel = navViewModel,
                    homeViewModel = homeViewModel,
                    searchViewModel = searchViewModel,
                    discoverViewModel = discoverViewModel,
                    detailViewModel = detailViewModel
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == PlaybackManager.ACTION_OPEN_PLAYER) {
            navViewModel.openPlayer()
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
    navViewModel: MainNavigationViewModel,
    homeViewModel: HomeViewModel,
    searchViewModel: SearchViewModel,
    discoverViewModel: DiscoverViewModel,
    detailViewModel: ProgramDetailViewModel
) {
    val selectedTab by navViewModel.selectedTab.collectAsState()
    val selectedProgram by navViewModel.selectedProgram.collectAsState()
    val showPlayerScreen by navViewModel.showPlayerScreen.collectAsState()
    var splashFinished by remember { mutableStateOf(navViewModel.splashFinished) }

    val playbackState by app.playback.state.collectAsState()

    // Behoud spelerweergave als er media aan het afspelen is
    LaunchedEffect(playbackState.media, playbackState.isPlaying) {
        if (playbackState.media != null && playbackState.isPlaying && !showPlayerScreen && selectedProgram == null) {
            navViewModel.openPlayer()
        }
    }

    // BackHandler navigatie
    BackHandler(enabled = showPlayerScreen || selectedProgram != null) {
        if (showPlayerScreen) {
            navViewModel.closePlayer()
        } else if (selectedProgram != null) {
            navViewModel.selectProgram(null)
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
                            onClick = { navViewModel.openPlayer() }
                        )
                    }

                    // Onderste navigatiebalk
                    NavigationBar(
                        containerColor = SchatkamerDarkElevated,
                        contentColor = SchatkamerCyan
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0 && selectedProgram == null,
                            onClick = { navViewModel.selectTab(0) },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Start") },
                            label = { Text("Start") },
                            colors = navigationBarColors()
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1 && selectedProgram == null,
                            onClick = { navViewModel.selectTab(1) },
                            icon = { Icon(Icons.Default.Search, contentDescription = "Zoeken") },
                            label = { Text("Zoeken") },
                            colors = navigationBarColors()
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2 && selectedProgram == null,
                            onClick = { navViewModel.selectTab(2) },
                            icon = { Icon(Icons.Default.Explore, contentDescription = "Ontdek") },
                            label = { Text("Ontdek") },
                            colors = navigationBarColors()
                        )
                        NavigationBarItem(
                            selected = selectedTab == 3 && selectedProgram == null,
                            onClick = { navViewModel.selectTab(3) },
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
                        onSelectItem = { navViewModel.selectProgram(it) }
                    )
                    1 -> SearchScreen(
                        viewModel = searchViewModel,
                        onSelectItem = { navViewModel.selectProgram(it) }
                    )
                    2 -> DiscoverScreen(
                        viewModel = discoverViewModel,
                        onSelectItem = { navViewModel.selectProgram(it) }
                    )
                    3 -> FavoritesScreen(
                        favoritesRepo = app.favorites,
                        onSelectItem = { navViewModel.selectProgram(it) },
                        onExplore = { navViewModel.selectTab(0) }
                    )
                }

                // Programma detail scherm
                if (selectedProgram != null) {
                    ProgramDetailScreen(
                        item = selectedProgram!!,
                        viewModel = detailViewModel,
                        onBack = { navViewModel.selectProgram(null) },
                        onPlay = { media ->
                            app.playback.play(media)
                            navViewModel.openPlayer()
                        },
                        onSelectEpisode = { navViewModel.selectProgram(it) },
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
                onBack = { navViewModel.closePlayer() },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Splash scherm
        if (!splashFinished) {
            SchatkamerSplash(
                ready = true,
                onFinished = {
                    splashFinished = true
                    navViewModel.splashFinished = true
                }
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
