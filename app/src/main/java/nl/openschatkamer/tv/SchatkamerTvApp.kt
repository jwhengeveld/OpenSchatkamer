package nl.openschatkamer.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import nl.openschatkamer.core.data.FavoritesRepository
import nl.openschatkamer.core.data.ProgressRepository
import nl.openschatkamer.core.data.SchatkamerCatalogRepository
import nl.openschatkamer.core.data.SchatkamerHttpClient
import nl.openschatkamer.core.data.SchatkamerVideoRepository

class SchatkamerTvApp : Application(), ImageLoaderFactory {

    val catalog by lazy { SchatkamerCatalogRepository.getInstance(this) }
    val video by lazy { SchatkamerVideoRepository.getInstance(this) }
    val favorites by lazy { FavoritesRepository.getInstance(this) }
    val progress by lazy { ProgressRepository.getInstance(this) }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient { SchatkamerHttpClient.getInstance(this).client }
            .crossfade(true)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("schatkamer_tv_images"))
                    .maxSizeBytes(200L * 1024 * 1024)
                    .build()
            }
            .build()
}
