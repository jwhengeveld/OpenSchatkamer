package nl.beeldengeluid.schatkamer.mobile.cast

import android.net.Uri
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.common.images.WebImage
import org.json.JSONObject

/**
 * Zet MediaItem om naar Google Cast MediaQueueItem.
 */
class SchatkamerMediaItemConverter : MediaItemConverter {

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val uri = mediaItem.localConfiguration?.uri ?: Uri.EMPTY
        val metadata = mediaItem.mediaMetadata

        val castMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, metadata.title?.toString().orEmpty())
            putString(MediaMetadata.KEY_SUBTITLE, metadata.subtitle?.toString().orEmpty())
            metadata.artworkUri?.let { addImage(WebImage(it)) }
        }

        val customData = JSONObject().apply {
            put("licenseUrl", "https://widevine-dash.ezdrm.com/widevine-php/widevine-foreignkey.php?pX=E24145")
            put("drmScheme", "widevine")
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
