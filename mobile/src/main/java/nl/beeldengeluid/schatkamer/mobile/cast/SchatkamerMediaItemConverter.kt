package nl.beeldengeluid.schatkamer.mobile.cast

import android.net.Uri
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.common.images.WebImage
import org.json.JSONArray
import org.json.JSONObject

/**
 * Zet MediaItem om naar Google Cast MediaQueueItem voor de officiële Schatkamer Chromecast Receiver (377B8439).
 * Ondersteunt het THEOplayer receiver framework met EZDRM Widevine & PlayReady content protection.
 */
class SchatkamerMediaItemConverter : MediaItemConverter {

    companion object {
        const val THEOPLAYER_LIBRARY_LOCATION = "https://cdn.myth.theoplayer.com/fb21074d-a7cd-4fef-9206-e490101d6570/"
        const val THEOPLAYER_LICENSE = "sZP7IYe6T6fk0le_IufrTmzz3KCtFSxKTuC-TSIgIOzLTDXgIS3l0LCc0Kh6FOPlUY3zWokgbgjNIOf9flPKIDao0K0oFDCr3oh-3u5tTOz_TSXgFD3g3uB_Iuhi3LRi3ZfVfK4_bQgZCYxNWoryIQXzImf90SRz0uRt3uai0u5i0Oi6Io4pIYP1UQgqWgjeCYxgflEc3LRk0laiTuRr0La_FOPeWok1dDrLYtA1Ioh6TgV6v6fVfKxqWQPrFK3qWmfVfKcqCoXVdQjLUOfVfG3pID49sY3iCYPZWtbpCoj-f6i6UQ1gWZrVdYIgf6i6UQ1gWtAVCYggb6rlWoz6Ymi6IQj-CDgpbkjLWt4ZCoh6TgV6v6fVfKxqWQPrFK3qWmfVfKcqCoXVdQjLUOfVfG3pID49sY3iCYPZWtbpCoj-f6i6UQ1gWZrVdYIgf6i6UQ1gWtAVCYggb6rlWoz6Ymi6box7bZf9DZPEUQkV3mPUFOPLIDreYog-bwPgbt3NWo_6TGxZUDhVfKIgCYxkbK4LflNWYYz"
        const val WIDEVINE_LICENSE_URL = "https://widevine-dash.ezdrm.com/widevine-php/widevine-foreignkey.php?pX=E24145"
        const val PLAYREADY_LICENSE_URL = "https://playready.ezdrm.com/cency/preauth.aspx?pX=164FB4"
        const val FAIRPLAY_LICENSE_URL = "https://fps.ezdrm.com/api/licenses/auth?px=C7BD8D"
        const val FAIRPLAY_CERTIFICATE_URL = "https://player.cdn.beeldengeluid.nl/drm/fairplay.cer"
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
            put("fairplay", JSONObject().apply {
                put("licenseAcquisitionURL", FAIRPLAY_LICENSE_URL)
                put("certificateURL", FAIRPLAY_CERTIFICATE_URL)
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

        // 4. SourceDescription object voor BENG.CastReceiver (exact zoals web client)
        val sourceDescription = JSONObject().apply {
            put("sources", sourcesArray)
            if (textTracksArray.length() > 0) {
                put("textTracks", textTracksArray)
            }
            metadata.artworkUri?.let {
                put("poster", it.toString())
            }
            put("network", JSONObject().apply {
                put("cors", JSONObject().apply {
                    put("credentials", "include")
                })
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
            .setContentUrl(uri.toString())
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
            .setUri(info.contentUrl ?: info.contentId)
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
