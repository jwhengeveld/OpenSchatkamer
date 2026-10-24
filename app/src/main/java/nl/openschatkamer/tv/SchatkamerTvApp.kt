package nl.openschatkamer.tv

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
import nl.openschatkamer.core.data.UserLibrary
import nl.openschatkamer.core.data.SchatkamerCatalogRepository
import nl.openschatkamer.core.data.SchatkamerHttpClient
import nl.openschatkamer.core.data.SchatkamerVideoRepository

class SchatkamerTvApp : Application(), ImageLoaderFactory {

    val catalog by lazy { SchatkamerCatalogRepository.getInstance(this) }
    val video by lazy { SchatkamerVideoRepository.getInstance(this) }
    val local by lazy { LibraryRepository.getInstance(this) }
    val library by lazy { UserLibrary.getInstance(this) }
    val account by lazy { SchatkamerAccount.getInstance(this) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastForegroundRefresh = 0L

    /** Terug op de voorgrond: gevolgde series, online lijsten en (na tien minuten) de startpagina verversen. */
    fun onForeground() {
        val now = System.currentTimeMillis()
        if (now - lastForegroundRefresh < 10 * 60_000L) return
        val firstTime = lastForegroundRefresh == 0L
        lastForegroundRefresh = now
        scope.launch {
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
                    .directory(cacheDir.resolve("schatkamer_tv_images"))
                    .maxSizeBytes(200L * 1024 * 1024)
                    .build()
            }
            .build()
}
