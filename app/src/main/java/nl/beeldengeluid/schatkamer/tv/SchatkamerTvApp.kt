package nl.beeldengeluid.schatkamer.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Applicatieklasse voor De Schatkamer Android TV.
 * Configureert een geoptimaliseerde Coil ImageLoader met geheugen- en schijfcache
 * voor soepele 60fps D-Pad navigatie op televisies en streaming sticks.
 */
class SchatkamerTvApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("schatkamer_tv_images"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .build()
}
