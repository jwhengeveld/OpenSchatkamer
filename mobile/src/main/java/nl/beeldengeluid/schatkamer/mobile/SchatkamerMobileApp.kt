package nl.beeldengeluid.schatkamer.mobile

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import nl.beeldengeluid.schatkamer.core.data.FavoritesRepository
import nl.beeldengeluid.schatkamer.core.data.NetworkMonitor
import nl.beeldengeluid.schatkamer.core.data.SchatkamerCatalogRepository
import nl.beeldengeluid.schatkamer.core.data.SchatkamerVideoRepository
import nl.beeldengeluid.schatkamer.mobile.playback.PlaybackManager

/**
 * Applicatieklasse voor De Schatkamer Mobile.
 */
class SchatkamerMobileApp : Application(), ImageLoaderFactory {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val catalog by lazy { SchatkamerCatalogRepository(this) }
    val video by lazy { SchatkamerVideoRepository(this) }
    val favorites by lazy { FavoritesRepository(this) }
    val network by lazy { NetworkMonitor(this) }
    val playback by lazy { PlaybackManager(this, video, appScope) }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .crossfade(true)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("schatkamer_images"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .build()
}
