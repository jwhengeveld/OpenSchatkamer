package nl.beeldengeluid.schatkamer.mobile.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.beeldengeluid.schatkamer.core.cast.CastDevice
import nl.beeldengeluid.schatkamer.core.cast.ProtocolType
import nl.beeldengeluid.schatkamer.core.data.PlayableMedia
import nl.beeldengeluid.schatkamer.core.data.SchatkamerVideoRepository
import nl.beeldengeluid.schatkamer.mobile.MainActivity
import nl.beeldengeluid.schatkamer.mobile.cast.CastAvailability
import nl.beeldengeluid.schatkamer.mobile.cast.SchatkamerMediaItemConverter
import nl.beeldengeluid.schatkamer.mobile.cast.SchatkamerTvSenderClient
import nl.beeldengeluid.schatkamer.mobile.cast.airplay.AirPlayDiscoveryManager
import nl.beeldengeluid.schatkamer.mobile.cast.airplay.AirPlaySenderClient

val LocalPlaybackManager = androidx.compose.runtime.staticCompositionLocalOf<PlaybackManager> {
    error("Geen PlaybackManager beschikbaar in CompositionLocal")
}

data class PlaybackState(
    val media: PlayableMedia? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isCasting: Boolean = false,
    val castDeviceName: String? = null,
    val castProtocol: ProtocolType? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null
)

@OptIn(UnstableApi::class)
class PlaybackManager(
    context: Context,
    private val videoRepo: SchatkamerVideoRepository,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()

    private var localPlayer: ExoPlayer? = null
    private var castPlayer: CastPlayer? = null
    private var _mediaSession: MediaSession? = null
    val mediaSession: MediaSession?
        get() = _mediaSession
    private var positionPollJob: Job? = null

    val discoveryManager = AirPlayDiscoveryManager(appContext)
    private var airPlayClient: AirPlaySenderClient? = null
    private var tvClient: SchatkamerTvSenderClient? = null
    private var remoteCastJob: Job? = null

    companion object {
        const val ACTION_OPEN_PLAYER = "nl.beeldengeluid.schatkamer.ACTION_OPEN_PLAYER"
    }

    init {
        CastAvailability.init(appContext) { castContext ->
            attachCast(castContext)
        }
    }

    private fun attachCast(castContext: CastContext) {
        val cp = CastPlayer(castContext, SchatkamerMediaItemConverter())
        cp.addListener(playerListener)
        cp.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() {
                switchToCast()
            }

            override fun onCastSessionUnavailable() {
                switchToLocal()
            }
        })
        castPlayer = cp

        castContext.sessionManager.addSessionManagerListener(
            object : SessionManagerListener<CastSession> {
                override fun onSessionStarted(session: CastSession, sessionId: String) {
                    _state.update {
                        it.copy(
                            isCasting = true,
                            castDeviceName = session.castDevice?.friendlyName
                        )
                    }
                }

                override fun onSessionEnded(session: CastSession, error: Int) {
                    _state.update {
                        it.copy(
                            isCasting = false,
                            castDeviceName = null
                        )
                    }
                }

                override fun onSessionStarting(session: CastSession) {}
                override fun onSessionStartFailed(session: CastSession, error: Int) {}
                override fun onSessionEnding(session: CastSession) {}
                override fun onSessionResuming(session: CastSession, sessionId: String) {}
                override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {}
                override fun onSessionResumeFailed(session: CastSession, error: Int) {}
                override fun onSessionSuspended(session: CastSession, reason: Int) {}
            },
            CastSession::class.java
        )
    }

    fun play(media: PlayableMedia) {
        _state.update {
            it.copy(
                media = media,
                isLoading = true,
                isPlaying = false,
                error = null
            )
        }

        val active = activePlayer()
        val mediaItem = buildMediaItem(media)

        active.stop()
        active.clearMediaItems()

        if (active is ExoPlayer) {
            val headers = mutableMapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36",
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

            active.setMediaSource(mediaSource)
        } else {
            active.setMediaItem(mediaItem)
        }

        active.prepare()
        if (media.resumePositionMs > 0) {
            active.seekTo(media.resumePositionMs)
        }
        active.playWhenReady = true
        startPositionPolling()
        startPlaybackService()
    }

    private fun startPlaybackService() {
        try {
            val serviceIntent = Intent(appContext, SchatkamerPlaybackService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                appContext.startForegroundService(serviceIntent)
            } else {
                appContext.startService(serviceIntent)
            }
        } catch (_: Exception) {
            // Background start restrictions fallback
        }
    }

    fun pause() {
        if (airPlayClient != null) {
            scope.launch { airPlayClient?.pause() }
            _state.update { it.copy(isPlaying = false) }
        } else if (tvClient != null) {
            scope.launch { tvClient?.pause() }
            _state.update { it.copy(isPlaying = false) }
        } else {
            _player.value?.pause()
        }
    }

    fun resume() {
        if (airPlayClient != null) {
            scope.launch { airPlayClient?.resume() }
            _state.update { it.copy(isPlaying = true) }
        } else if (tvClient != null) {
            scope.launch { tvClient?.resume() }
            _state.update { it.copy(isPlaying = true) }
        } else {
            _player.value?.play()
        }
    }

    fun seekTo(positionMs: Long) {
        if (airPlayClient != null) {
            scope.launch { airPlayClient?.seekTo(positionMs) }
            _state.update { it.copy(currentPositionMs = positionMs) }
        } else if (tvClient != null) {
            scope.launch { tvClient?.seekTo(positionMs) }
            _state.update { it.copy(currentPositionMs = positionMs) }
        } else {
            _player.value?.seekTo(positionMs)
        }
    }

    fun seekBy(offsetMs: Long) {
        val current = _state.value.currentPositionMs
        seekTo((current + offsetMs).coerceAtLeast(0L))
    }

    fun stop() {
        disconnectRemoteCast(resumeLocal = false)
        _player.value?.stop()
        _state.update { it.copy(isPlaying = false, isLoading = false, media = null) }
        positionPollJob?.cancel()
        try {
            appContext.stopService(Intent(appContext, SchatkamerPlaybackService::class.java))
        } catch (_: Exception) {}
    }

    fun castToDevice(device: CastDevice) {
        val currentMedia = _state.value.media ?: return
        val pos = _state.value.currentPositionMs

        disconnectRemoteCast(resumeLocal = false)
        localPlayer?.pause()

        when (device.protocol) {
            ProtocolType.AIRPLAY -> {
                val client = AirPlaySenderClient(device)
                airPlayClient = client
                _state.update {
                    it.copy(
                        isCasting = true,
                        castDeviceName = device.name,
                        castProtocol = ProtocolType.AIRPLAY
                    )
                }

                scope.launch {
                    val success = client.playMedia(currentMedia.streamUrl, currentMedia.title, pos / 1000.0)
                    if (!success) {
                        _state.update {
                            it.copy(
                                error = client.errorMessage.value ?: "Kon niet verbinden met ${device.name}",
                                isCasting = false,
                                castDeviceName = null,
                                castProtocol = null
                            )
                        }
                        airPlayClient = null
                    } else {
                        observeAirPlay(client)
                    }
                }
            }
            ProtocolType.SCHATKAMER_TV -> {
                val client = SchatkamerTvSenderClient(device)
                tvClient = client
                _state.update {
                    it.copy(
                        isCasting = true,
                        castDeviceName = device.name,
                        castProtocol = ProtocolType.SCHATKAMER_TV
                    )
                }

                scope.launch {
                    val success = client.playProgram(currentMedia.id, currentMedia.title, pos, currentMedia.streamUrl)
                    if (!success) {
                        _state.update {
                            it.copy(
                                error = client.errorMessage.value ?: "Kon niet verbinden met ${device.name}",
                                isCasting = false,
                                castDeviceName = null,
                                castProtocol = null
                            )
                        }
                        tvClient = null
                    } else {
                        observeTv(client)
                    }
                }
            }
            else -> {}
        }
    }

    fun disconnectRemoteCast(resumeLocal: Boolean = true) {
        val wasRemote = airPlayClient != null || tvClient != null
        val pos = _state.value.currentPositionMs

        remoteCastJob?.cancel()
        remoteCastJob = null

        val ac = airPlayClient
        val tc = tvClient
        airPlayClient = null
        tvClient = null

        scope.launch {
            ac?.stop()
            ac?.release()
            tc?.stop()
            tc?.release()
        }

        if (wasRemote) {
            _state.update {
                it.copy(
                    isCasting = false,
                    castDeviceName = null,
                    castProtocol = null
                )
            }
            if (resumeLocal) {
                val ep = ensureLocal()
                ep.seekTo(pos)
                ep.play()
            }
        } else if (_state.value.isCasting) {
            stopCasting()
        }
    }

    private fun observeAirPlay(client: AirPlaySenderClient) {
        remoteCastJob?.cancel()
        remoteCastJob = scope.launch {
            launch {
                client.isPlaying.collect { playing ->
                    _state.update { it.copy(isPlaying = playing) }
                }
            }
            launch {
                client.positionMs.collect { pos ->
                    if (pos > 0) _state.update { it.copy(currentPositionMs = pos) }
                }
            }
            launch {
                client.durationMs.collect { dur ->
                    if (dur > 0) _state.update { it.copy(durationMs = dur) }
                }
            }
            launch {
                client.errorMessage.collect { err ->
                    if (err != null) _state.update { it.copy(error = err) }
                }
            }
        }
    }

    private fun observeTv(client: SchatkamerTvSenderClient) {
        remoteCastJob?.cancel()
        remoteCastJob = scope.launch {
            launch {
                client.isPlaying.collect { playing ->
                    _state.update { it.copy(isPlaying = playing) }
                }
            }
            launch {
                client.positionMs.collect { pos ->
                    if (pos > 0) _state.update { it.copy(currentPositionMs = pos) }
                }
            }
            launch {
                client.durationMs.collect { dur ->
                    if (dur > 0) _state.update { it.copy(durationMs = dur) }
                }
            }
            launch {
                client.errorMessage.collect { err ->
                    if (err != null) _state.update { it.copy(error = err) }
                }
            }
        }
    }

    fun retry() {
        val currentMedia = _state.value.media ?: return
        val pos = _state.value.currentPositionMs
        val resumeMedia = currentMedia.copy(resumePositionMs = pos)
        play(resumeMedia)
    }

    fun stopCasting() {
        runCatching {
            CastContext.getSharedInstance(appContext, androidx.core.content.ContextCompat.getMainExecutor(appContext))
                .addOnSuccessListener { castContext ->
                    castContext.sessionManager.endCurrentSession(true)
                }
        }
    }

    private fun activePlayer(): Player {
        val cp = castPlayer
        val active = if (cp != null && cp.isCastSessionAvailable) cp else ensureLocal()
        if (_player.value !== active) {
            _player.value = active
        }
        return active
    }

    private fun ensureLocal(): ExoPlayer {
        localPlayer?.let { return it }

        val mediaSourceFactory = DefaultMediaSourceFactory(appContext)

        val ep = ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(30_000L)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        ep.addListener(playerListener)
        localPlayer = ep

        // MediaSession instellen
        val intent = Intent(appContext, MainActivity::class.java).apply {
            action = ACTION_OPEN_PLAYER
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        _mediaSession = MediaSession.Builder(appContext, ep)
            .setId("schatkamer-mobile")
            .setSessionActivity(pendingIntent)
            .build()

        return ep
    }

    private fun switchToCast() {
        val local = localPlayer ?: return
        val pos = local.currentPosition
        val currentMedia = _state.value.media ?: return
        local.stop()

        val cp = castPlayer ?: return
        _player.value = cp
        val mediaItem = buildMediaItem(currentMedia)
        cp.setMediaItem(mediaItem, pos)
        cp.prepare()
        cp.play()
    }

    private fun switchToLocal() {
        val cp = castPlayer
        val pos = cp?.currentPosition ?: 0L
        val currentMedia = _state.value.media ?: return

        val local = ensureLocal()
        _player.value = local
        val mediaItem = buildMediaItem(currentMedia)

        val headers = mutableMapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
            "Referer" to "https://schatkamer.beeldengeluid.nl/",
            "Origin" to "https://schatkamer.beeldengeluid.nl"
        )
        if (currentMedia.cookieHeader.isNotBlank()) {
            headers["Cookie"] = currentMedia.cookieHeader
        }
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(headers["User-Agent"]!!)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)

        val mediaSource = HlsMediaSource.Factory(httpDataSourceFactory)
            .setAllowChunklessPreparation(true)
            .createMediaSource(mediaItem)

        local.setMediaSource(mediaSource, pos)
        local.prepare()
        local.pause() // Gepauzeerd klaarzetten op mobiel
    }

    private fun buildMediaItem(media: PlayableMedia): MediaItem {
        val builder = MediaItem.Builder()
            .setUri(media.streamUrl)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(media.title)
                    .setSubtitle(media.subtitle)
                    .setArtist("De Schatkamer · Beeld & Geluid")
                    .setDisplayTitle(media.title)
                    .setArtworkUri(if (media.posterUrl.isNotBlank()) Uri.parse(media.posterUrl) else null)
                    .build()
            )

        // Widevine DRM configuratie
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

        // Ondertitels (VTT)
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

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> _state.update { it.copy(isLoading = true) }
                Player.STATE_READY -> _state.update {
                    it.copy(
                        isLoading = false,
                        durationMs = _player.value?.duration?.coerceAtLeast(0L) ?: 0L
                    )
                }
                Player.STATE_ENDED -> _state.update { it.copy(isLoading = false, isPlaying = false) }
                else -> _state.update { it.copy(isLoading = false) }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update {
                it.copy(
                    isLoading = false,
                    isPlaying = false,
                    error = error.localizedMessage ?: "Fout bij afspelen van stream."
                )
            }
        }
    }

    private fun startPositionPolling() {
        positionPollJob?.cancel()
        positionPollJob = scope.launch {
            while (true) {
                val p = _player.value
                if (p != null) {
                    _state.update {
                        it.copy(
                            currentPositionMs = p.currentPosition.coerceAtLeast(0L),
                            durationMs = p.duration.coerceAtLeast(0L)
                        )
                    }
                }
                delay(1000)
            }
        }
    }

    fun release() {
        positionPollJob?.cancel()
        disconnectRemoteCast(resumeLocal = false)
        discoveryManager.release()
        try {
            appContext.stopService(Intent(appContext, SchatkamerPlaybackService::class.java))
        } catch (_: Exception) {}
        _mediaSession?.release()
        localPlayer?.release()
        localPlayer = null
        _mediaSession = null
    }
}
