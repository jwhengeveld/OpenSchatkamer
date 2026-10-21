package nl.beeldengeluid.schatkamer.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import nl.beeldengeluid.schatkamer.brand.SchatkamerSplash
import nl.beeldengeluid.schatkamer.core.data.FavoritesRepository
import nl.beeldengeluid.schatkamer.core.data.PlayableMedia
import nl.beeldengeluid.schatkamer.core.data.SchatkamerCatalogRepository
import nl.beeldengeluid.schatkamer.core.data.SchatkamerItem
import nl.beeldengeluid.schatkamer.core.data.SchatkamerVideoRepository
import nl.beeldengeluid.schatkamer.tv.ui.components.TvNavigationRail
import nl.beeldengeluid.schatkamer.tv.ui.screens.TvDiscoverScreen
import nl.beeldengeluid.schatkamer.tv.ui.screens.TvFavoritesScreen
import nl.beeldengeluid.schatkamer.tv.ui.screens.TvHomeScreen
import nl.beeldengeluid.schatkamer.tv.ui.screens.TvPlayerScreen
import nl.beeldengeluid.schatkamer.tv.ui.screens.TvProgramDetailScreen
import nl.beeldengeluid.schatkamer.tv.ui.screens.TvSearchScreen
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerDarkBackground
import nl.beeldengeluid.schatkamer.tv.ui.theme.SchatkamerTvTheme

class MainActivity : ComponentActivity() {

    private val catalogRepo by lazy { SchatkamerCatalogRepository.getInstance(this) }
    private val videoRepo by lazy { SchatkamerVideoRepository.getInstance(this) }
    private val favoritesRepo by lazy { FavoritesRepository.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Schermvullend op Android TV zonder afleidende balken
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        setContent {
            SchatkamerTvTheme {
                TvMainContent(
                    catalogRepo = catalogRepo,
                    videoRepo = videoRepo,
                    favoritesRepo = favoritesRepo
                )
            }
        }
    }
}

@Composable
fun TvMainContent(
    catalogRepo: SchatkamerCatalogRepository,
    videoRepo: SchatkamerVideoRepository,
    favoritesRepo: FavoritesRepository
) {
    var splashFinished by remember { mutableStateOf(false) }
    var selectedDestination by remember { mutableIntStateOf(0) }
    var selectedProgram by remember { mutableStateOf<SchatkamerItem?>(null) }
    var activePlayerMedia by remember { mutableStateOf<PlayableMedia?>(null) }

    // D-Pad Terugknop afhandeling
    BackHandler(enabled = activePlayerMedia != null || selectedProgram != null) {
        if (activePlayerMedia != null) {
            activePlayerMedia = null
        } else if (selectedProgram != null) {
            selectedProgram = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SchatkamerDarkBackground)
    ) {
        if (activePlayerMedia != null) {
            // Fullscreen TV speler
            TvPlayerScreen(
                media = activePlayerMedia!!,
                onBack = { activePlayerMedia = null }
            )
        } else if (selectedProgram != null) {
            // Programma detailscherm
            TvProgramDetailScreen(
                item = selectedProgram!!,
                videoRepo = videoRepo,
                favoritesRepo = favoritesRepo,
                onBack = { selectedProgram = null },
                onPlay = { media -> activePlayerMedia = media },
                onSelectEpisode = { selectedProgram = it }
            )
        } else {
            // Hoofdscherm met linker TV navigatiebalk
            Row(modifier = Modifier.fillMaxSize()) {
                TvNavigationRail(
                    selectedDestination = selectedDestination,
                    onSelectDestination = { selectedDestination = it }
                )

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedDestination) {
                        0 -> TvHomeScreen(
                            catalogRepo = catalogRepo,
                            onSelectItem = { selectedProgram = it }
                        )
                        1 -> TvSearchScreen(
                            catalogRepo = catalogRepo,
                            onSelectItem = { selectedProgram = it }
                        )
                        2 -> TvDiscoverScreen(
                            catalogRepo = catalogRepo,
                            onSelectItem = { selectedProgram = it }
                        )
                        3 -> TvFavoritesScreen(
                            favoritesRepo = favoritesRepo,
                            onSelectItem = { selectedProgram = it }
                        )
                    }
                }
            }
        }

        // Geanimeerd splashscherm bij eerste start
        if (!splashFinished) {
            SchatkamerSplash(
                ready = true,
                onFinished = { splashFinished = true }
            )
        }
    }
}
