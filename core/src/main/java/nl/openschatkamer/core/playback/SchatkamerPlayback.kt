package nl.openschatkamer.core.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import nl.openschatkamer.core.BuildConfig
import nl.openschatkamer.core.data.PlayableMedia
import nl.openschatkamer.core.data.SchatkamerHttpClient

/**
 * Bouwt de ExoPlayer en de mediabron voor een Schatkamer-uitzending, gedeeld
 * door de telefoon- en de tv-app.
 *
 * - De CloudFront-toegang gaat als Cookie-header mee op elk verzoek
 *   (manifest, playlists en fragmenten), zoals een browser dat doet.
 * - Widevine via de EZDRM-licentieserver die de website ook gebruikt.
 * - Alleen het programma zelf wordt getoond: de archiefband begint vaak met
 *   aanloop en eindigt met zwart; [PlayableMedia.stream] geeft het begin en eind.
 */
@OptIn(UnstableApi::class)
object SchatkamerPlayback {

    const val SEEK_BACK_MS = 10_000L
    const val SEEK_FORWARD_MS = 30_000L

    fun buildPlayer(context: Context, handleAudioFocus: Boolean = true): ExoPlayer =
        ExoPlayer.Builder(context.applicationContext)
            .setSeekBackIncrementMs(SEEK_BACK_MS)
            .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(15_000, 60_000, 1_500, 3_000)
                    .build()
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                handleAudioFocus
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

    fun mediaItem(media: PlayableMedia): MediaItem {
        val stream = media.stream
        val builder = MediaItem.Builder()
            .setMediaId(media.id)
            .setUri(stream.manifestUrl)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(media.title)
                    .setSubtitle(media.subtitle)
                    .setArtist(media.subtitle.ifBlank { media.broadcaster })
                    .setDisplayTitle(media.title)
                    .setArtworkUri(media.posterUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
                    .setMediaType(if (media.isAudio) MediaMetadata.MEDIA_TYPE_RADIO_STATION else MediaMetadata.MEDIA_TYPE_TV_SHOW)
                    .build()
            )

        if (stream.drmProtected) {
            builder.setDrmConfiguration(
                MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri(BuildConfig.WIDEVINE_LICENSE_URL)
                    .setLicenseRequestHeaders(mapOf("Origin" to SchatkamerHttpClient.BASE_URL, "Referer" to "${SchatkamerHttpClient.BASE_URL}/"))
                    .setMultiSession(false)
                    .build()
            )
        }

        if (stream.clipEndSec > stream.clipStartSec || stream.clipStartSec > 0) {
            builder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs((stream.clipStartSec * 1000).toLong())
                    .apply { if (stream.clipEndSec > stream.clipStartSec) setEndPositionMs((stream.clipEndSec * 1000).toLong()) }
                    .build()
            )
        }

        stream.subtitlesUrl?.let { url ->
            builder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(url))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage("nl")
                        .setLabel("Nederlands")
                        .build()
                )
            )
        }
        return builder.build()
    }

    fun mediaSource(context: Context, media: PlayableMedia): MediaSource {
        val headers = buildMap {
            put("Origin", SchatkamerHttpClient.BASE_URL)
            put("Referer", "${SchatkamerHttpClient.BASE_URL}/")
            if (media.stream.cookieHeader.isNotBlank()) put("Cookie", media.stream.cookieHeader)
        }
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(SchatkamerHttpClient.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setDefaultRequestProperties(headers)
        return DefaultMediaSourceFactory(context.applicationContext)
            .setDataSourceFactory(http)
            .createMediaSource(mediaItem(media))
    }

    /** Start [media] op [player], vanaf de opgeslagen positie (binnen het programma). */
    fun play(context: Context, player: ExoPlayer, media: PlayableMedia, playWhenReady: Boolean = true) {
        player.setMediaSource(mediaSource(context, media), media.resumePositionMs.coerceAtLeast(0))
        player.prepare()
        player.playWhenReady = playWhenReady
    }
}
