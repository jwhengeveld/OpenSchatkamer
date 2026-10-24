package nl.openschatkamer.mobile

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import nl.openschatkamer.core.data.FavoritesRepository
import nl.openschatkamer.core.data.NetworkMonitor
import nl.openschatkamer.core.data.ProgressRepository
import nl.openschatkamer.core.data.SchatkamerCatalogRepository
import nl.openschatkamer.core.data.SchatkamerHttpClient
import nl.openschatkamer.core.data.SchatkamerVideoRepository
import nl.openschatkamer.mobile.playback.PlaybackManager

class SchatkamerMobileApp : Application(), ImageLoaderFactory {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val catalog by lazy { SchatkamerCatalogRepository.getInstance(this) }
    val video by lazy { SchatkamerVideoRepository.getInstance(this) }
    val favorites by lazy { FavoritesRepository.getInstance(this) }
    val progress by lazy { ProgressRepository.getInstance(this) }
    val network by lazy { NetworkMonitor(this) }
    val playback by lazy { PlaybackManager(this, video, progress, appScope) }

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
