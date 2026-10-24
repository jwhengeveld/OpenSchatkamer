package nl.openschatkamer.mobile

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nl.openschatkamer.core.auth.SchatkamerAccount
import nl.openschatkamer.core.data.LibraryRepository
import nl.openschatkamer.core.data.NetworkMonitor
import nl.openschatkamer.core.data.UserLibrary
import nl.openschatkamer.core.data.SchatkamerCatalogRepository
import nl.openschatkamer.core.data.SchatkamerHttpClient
import nl.openschatkamer.core.data.SchatkamerVideoRepository
import nl.openschatkamer.mobile.playback.PlaybackManager

class SchatkamerMobileApp : Application(), ImageLoaderFactory {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val catalog by lazy { SchatkamerCatalogRepository.getInstance(this) }
    val video by lazy { SchatkamerVideoRepository.getInstance(this) }
    val local by lazy { LibraryRepository.getInstance(this) }
    val library by lazy { UserLibrary.getInstance(this) }
    val account by lazy { SchatkamerAccount.getInstance(this) }
    val network by lazy { NetworkMonitor(this) }
    val playback by lazy { PlaybackManager(this, video, local, catalog, appScope) }

    private var lastForegroundRefresh = 0L

    /**
     * Terug op de voorgrond: na tien minuten de startpagina verversen, gevolgde
     * series nakijken en de online lijsten bijwerken. Zo verschijnt nieuwe
     * content vanzelf, zonder dat de app op de achtergrond iets ophaalt.
     */
    fun onForeground() {
        val now = System.currentTimeMillis()
        if (now - lastForegroundRefresh < 10 * 60_000L) return
        // Bij de eerste keer laadt het startscherm zelf al; dan alleen de rest.
        val firstTime = lastForegroundRefresh == 0L
        lastForegroundRefresh = now
        appScope.launch {
            if (!firstTime) catalog.getHome(forceRefresh = true)
            local.refreshFollowed(catalog)
            library.online.refresh()
        }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient { SchatkamerHttpClient.getInstance(this).client }
            .crossfade(true)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("schatkamer_images"))
                    .maxSizeBytes(200L * 1024 * 1024)
                    .build()
            }
            .build()
}
