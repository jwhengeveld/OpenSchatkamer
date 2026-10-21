package nl.beeldengeluid.schatkamer.tv.ui.player

import android.content.Context
import android.net.Uri
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import nl.beeldengeluid.schatkamer.core.data.PlayableMedia
import nl.beeldengeluid.schatkamer.tv.R

const val SEEK_BACK_MS = 10_000L
const val SEEK_FORWARD_MS = 30_000L

@OptIn(UnstableApi::class)
fun buildTvPlayer(context: Context): ExoPlayer {
    val factory = DefaultMediaSourceFactory(context)

    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(factory)
        .setSeekBackIncrementMs(SEEK_BACK_MS)
        .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()
        .apply { playWhenReady = true }
}

@OptIn(UnstableApi::class)
fun buildTvMediaItem(media: PlayableMedia): MediaItem {
    val builder = MediaItem.Builder()
        .setUri(media.streamUrl)
        .setMimeType(MimeTypes.APPLICATION_M3U8)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(media.title)
                .setSubtitle(media.subtitle)
                .setArtworkUri(if (media.posterUrl.isNotBlank()) Uri.parse(media.posterUrl) else null)
                .build()
        )

    if (media.widevineLicenseUrl.isNotBlank()) {
        val drmHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
            "Referer" to "https://schatkamer.beeldengeluid.nl/",
            "Origin" to "https://schatkamer.beeldengeluid.nl"
        )
        builder.setDrmConfiguration(
            MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                .setLicenseUri(media.widevineLicenseUrl)
                .setLicenseRequestHeaders(drmHeaders)
                .build()
        )
    }

    if (!media.subtitlesUrl.isNullOrBlank()) {
        val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(media.subtitlesUrl))
            .setMimeType(MimeTypes.TEXT_VTT)
            .setLanguage("nl")
            .setLabel("Nederlands")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        builder.setSubtitleConfigurations(listOf(subtitleConfig))
    }

    return builder.build()
}

@OptIn(UnstableApi::class)
fun playTvMedia(player: ExoPlayer, media: PlayableMedia) {
    val mediaItem = buildTvMediaItem(media)
    val headers = mutableMapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
        "Referer" to "https://schatkamer.beeldengeluid.nl/",
        "Origin" to "https://schatkamer.beeldengeluid.nl"
    )
    if (media.cookieHeader.isNotBlank()) {
        headers["Cookie"] = media.cookieHeader
    }
    val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(headers["User-Agent"]!!)
        .setAllowCrossProtocolRedirects(true)
        .setDefaultRequestProperties(headers)

    val mediaSource = HlsMediaSource.Factory(httpDataSourceFactory)
        .setAllowChunklessPreparation(true)
        .createMediaSource(mediaItem)

    player.stop()
    player.clearMediaItems()
    player.setMediaSource(mediaSource)
    player.prepare()
    if (media.resumePositionMs > 0) {
        player.seekTo(media.resumePositionMs)
    }
    player.play()
}

@OptIn(UnstableApi::class)
fun buildTvPlayerView(
    context: Context,
    @LayoutRes layoutRes: Int,
    player: Player,
    onBack: () -> Unit,
    onControllerVisibilityChanged: (Boolean) -> Unit
): PlayerView {
    val view = LayoutInflater.from(context).inflate(layoutRes, null, false) as PlayerView
    view.layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
    view.setPlayer(player)
    view.findViewById<ImageButton>(R.id.schatkamer_back)?.setOnClickListener { onBack() }

    val playPause = view.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)
    view.setControllerVisibilityListener(
        PlayerView.ControllerVisibilityListener { visibility ->
            val visible = visibility == View.VISIBLE
            if (visible) playPause?.requestFocus()
            onControllerVisibilityChanged(visible)
        }
    )
    return view
}

fun PlayerView.setPlayerLabels(title: String, subtitle: String) {
    findViewById<TextView>(R.id.schatkamer_title)?.text = title
    findViewById<TextView>(R.id.schatkamer_subtitle)?.apply {
        text = subtitle
        visibility = if (subtitle.isBlank()) View.GONE else View.VISIBLE
    }
}
