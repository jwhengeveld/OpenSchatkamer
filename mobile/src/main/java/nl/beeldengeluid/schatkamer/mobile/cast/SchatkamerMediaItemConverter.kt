package nl.beeldengeluid.schatkamer.mobile.cast

import android.net.Uri
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.common.images.WebImage
import org.json.JSONArray
import org.json.JSONObject

/**
 * Zet MediaItem om naar Google Cast MediaQueueItem voor de officiële Schatkamer Chromecast Receiver (377B8439).
 * Ondersteunt THEOplayer receiver framework met EZDRM Widevine & PlayReady content protection.
 */
class SchatkamerMediaItemConverter : MediaItemConverter {

    companion object {
        const val THEOPLAYER_LIBRARY_LOCATION = "https://cdn.myth.theoplayer.com/6035a502-5393-44ab-ad99-860715846af6/"
        const val THEOPLAYER_LICENSE = "sZP7IYe6T6fk0le_IufrTmzz3KCtFSxKTuC-TSIgIOzLTDXgIS3l0LCc0Kh6FOPlUY3zWokgbgjNIOf9flPKIDao0K0oFDCr3oh-3u5tTOz_TSXgFD3g3uB_Iuhi3LRi3ZfVfK4_bQgZCYxNWoryIQXzImf90SRz0uRt3uai0u5i0Oi6Io4pIYP1UQgqWgjeCYxgflEc3LRk0laiTuRr0La_FOPeWok1dDrLYtA1Ioh6TgV6v6fVfKxqWQPrFK3qWmfVfKcqCo"
        const val WIDEVINE_LICENSE_URL = "https://widevine-dash.ezdrm.com/widevine-php/widevine-foreignkey.php?pX=E24145"
        const val PLAYREADY_LICENSE_URL = "https://playready.ezdrm.com/cency/preauth.aspx?pX=164FB4"
    }

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val uri = mediaItem.localConfiguration?.uri ?: Uri.EMPTY
        val metadata = mediaItem.mediaMetadata

        val castMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, metadata.title?.toString().orEmpty())
            putString(MediaMetadata.KEY_SUBTITLE, metadata.subtitle?.toString().orEmpty())
            metadata.artworkUri?.let { addImage(WebImage(it)) }
        }

        // 1. EZDRM Content Protection voor THEOplayer receiver
        val contentProtection = JSONObject().apply {
            put("integration", "ezdrm")
            put("widevine", JSONObject().apply {
                put("licenseAcquisitionURL", WIDEVINE_LICENSE_URL)
            })
            put("playready", JSONObject().apply {
                put("licenseAcquisitionURL", PLAYREADY_LICENSE_URL)
            })
        }

        // 2. THEOplayer source specificatie
        val sourceObj = JSONObject().apply {
            put("src", uri.toString())
            put("type", "application/x-mpegurl")
            put("useCredentials", true)
            put("contentProtection", contentProtection)
            put("drm", contentProtection)
        }

        val sourcesArray = JSONArray().apply {
            put(sourceObj)
        }

        // 3. Optionele ondertiteling
        val textTracksArray = JSONArray()
        for (sub in mediaItem.localConfiguration?.subtitleConfigurations.orEmpty()) {
            val trackObj = JSONObject().apply {
                put("src", sub.uri.toString())
                put("kind", "subtitles")
                put("srclang", sub.language ?: "nl")
                put("label", sub.label ?: "Nederlands")
                put("default", true)
            }
            textTracksArray.put(trackObj)
        }

        // 4. SourceDescription object voor BENG.CastReceiver
        val sourceDescription = JSONObject().apply {
            put("sources", sourcesArray)
            if (textTracksArray.length() > 0) {
                put("textTracks", textTracksArray)
            }
            put("clip", JSONObject().apply {
                put("startTime", 0)
            })
        }

        // 5. THEOplayer receiver initialisatie configuratie
        val configuration = JSONObject().apply {
            put("libraryLocation", THEOPLAYER_LIBRARY_LOCATION)
            put("license", THEOPLAYER_LICENSE)
        }

        // 6. Complete customData payload
        val customData = JSONObject().apply {
            put("configuration", configuration)
            put("sourceDescription", sourceDescription)
            put("licenseUrl", WIDEVINE_LICENSE_URL)
            put("drmScheme", "widevine")
            put("playreadyLicenseUrl", PLAYREADY_LICENSE_URL)
        }

        val mediaInfo = MediaInfo.Builder(uri.toString())
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType("application/x-mpegurl")
            .setMetadata(castMetadata)
            .setCustomData(customData)
            .build()

        return MediaQueueItem.Builder(mediaInfo)
            .setAutoplay(true)
            .build()
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        val info = mediaQueueItem.media ?: return MediaItem.EMPTY
        val metadata = info.metadata
        val title = metadata?.getString(MediaMetadata.KEY_TITLE)
        val subtitle = metadata?.getString(MediaMetadata.KEY_SUBTITLE)
        val imageUri = metadata?.images?.firstOrNull()?.url

        return MediaItem.Builder()
            .setUri(info.contentId)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setArtworkUri(imageUri)
                    .build()
            )
            .build()
    }
}
