package nl.openschatkamer.mobile.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nl.openschatkamer.core.data.NotPlayableException
import nl.openschatkamer.core.data.PlayableMedia
import nl.openschatkamer.core.data.LibraryRepository
import nl.openschatkamer.core.data.SchatkamerCatalogRepository
import nl.openschatkamer.core.data.SortOrder
import nl.openschatkamer.core.data.SchatkamerItem
import nl.openschatkamer.core.data.SchatkamerVideoRepository
import nl.openschatkamer.core.playback.CastPayload
import nl.openschatkamer.core.playback.SchatkamerPlayback
import nl.openschatkamer.mobile.MainActivity
import nl.openschatkamer.mobile.cast.CastAvailability
import java.io.IOException

data class PlaybackState(
    /** Wat er gevraagd is; ook gevuld terwijl de stream nog wordt opgehaald. */
    val item: SchatkamerItem? = null,
    val media: PlayableMedia? = null,
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
    /** Naam van de Chromecast waarmee een sessie loopt, of null. */
    val castDevice: String? = null,
    val castVolume: Float = 0.5f,
    val castMuted: Boolean = false,
    val subtitlesOn: Boolean = false,
    val speed: Float = 1f,
    /** De volgende aflevering van dezelfde serie, als die er is. */
    val next: SchatkamerItem? = null,
    /** Seconden tot de volgende aflevering vanzelf start; null = geen aftelling. */
    val nextCountdown: Int? = null
) {
    val isCasting: Boolean get() = castDevice != null
    val hasMedia: Boolean get() = item != null
    val hasSubtitles: Boolean get() = media?.stream?.subtitlesUrl != null
}

/**
 * Eén afspeelpunt voor de hele app: speelt af op de telefoon (ExoPlayer) of op
 * een Chromecast, en wisselt daartussen zonder de plek kwijt te raken.
 *
 * Casten gaat bewust niet via Media3's CastPlayer: die laadt met queueLoad en
 * stuurt dan geen `customData` in het LOAD-verzoek mee, terwijl de receiver
 * van De Schatkamer daar zijn THEOplayer-instellingen en bron uit leest.
 * We laden daarom zelf met [RemoteMediaClient.load]; zie [CastPayload].
 */
@OptIn(UnstableApi::class)
class PlaybackManager(
    context: Context,
    private val video: SchatkamerVideoRepository,
    private val progress: LibraryRepository,
    private val catalog: SchatkamerCatalogRepository,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _player = MutableStateFlow<ExoPlayer?>(null)
    /** De lokale speler, voor de PlayerView. Null tot er iets lokaal speelt. */
    val player: StateFlow<ExoPlayer?> = _player.asStateFlow()

    var mediaSession: MediaSession? = null
        private set

    private var castContext: CastContext? = null
    private var loadJob: Job? = null
    private var tickJob: Job? = null
    private var lastSavedAt = 0L

    init {
        CastAvailability.init(appContext) { ctx ->
            castContext = ctx
            ctx.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
            ctx.sessionManager.currentCastSession?.let(::onCastConnected)
        }
    }

    private val remote: RemoteMediaClient?
        get() = castContext?.sessionManager?.currentCastSession?.remoteMediaClient

    // ------------------------------------------------------------- afspelen

    /** Speel [item] af, op de Chromecast als die verbonden is, anders op de telefoon. */
    fun play(item: SchatkamerItem, fromStart: Boolean = false) {
        loadJob?.cancel()
        refreshedAfter403 = false
        _state.update {
            it.copy(item = item, media = null, isLoading = true, isPlaying = false, error = null, positionMs = 0, durationMs = 0)
        }
        loadJob = scope.launch {
            val resume = if (fromStart) 0L else progress.resumePosition(item.id)
            video.playable(item.id, resume)
                .onSuccess { media -> start(media) }
                .onFailure { error -> _state.update { it.copy(isLoading = false, error = describe(error)) } }
        }
    }

    private fun start(media: PlayableMedia) {
        _state.update { it.copy(media = media, durationMs = media.stream.clipDurationMs, next = null, nextCountdown = null) }
        findNextEpisode(media)
        applySpeed()
        if (_state.value.isCasting) loadOnCast(media, media.resumePositionMs) else loadLocally(media, playWhenReady = true)
        startTicking()
    }

    /** Zoekt de aflevering die na deze komt (op uitzenddatum), voor "volgende aflevering". */
    private fun findNextEpisode(media: PlayableMedia) {
        val seriesId = media.seriesId ?: return
        scope.launch {
            val episodes = catalog.episodes(seriesId, sort = SortOrder.OLDEST).getOrNull()?.items.orEmpty()
            val index = episodes.indexOfFirst { it.id == media.id }
            val next = if (index >= 0) episodes.getOrNull(index + 1) else null
            if (_state.value.media?.id == media.id) _state.update { it.copy(next = next) }
        }
    }

    /** Aan het einde: na een korte aftelling de volgende aflevering. */
    private fun onEnded() {
        val next = _state.value.next ?: return
        countdownJob?.cancel()
        countdownJob = scope.launch {
            for (seconds in 10 downTo 1) {
                _state.update { it.copy(nextCountdown = seconds) }
                delay(1000)
            }
            _state.update { it.copy(nextCountdown = null) }
            play(next, fromStart = true)
        }
    }

    fun playNextNow() {
        val next = _state.value.next ?: return
        countdownJob?.cancel()
        _state.update { it.copy(nextCountdown = null) }
        play(next, fromStart = true)
    }

    fun cancelNext() {
        countdownJob?.cancel()
        _state.update { it.copy(nextCountdown = null) }
    }

    fun setSpeed(speed: Float) {
        _state.update { it.copy(speed = speed) }
        applySpeed()
    }

    private fun applySpeed() {
        val speed = _state.value.speed
        _player.value?.setPlaybackSpeed(speed)
        if (_state.value.isCasting) remote?.setPlaybackRate(speed.toDouble())
    }

    private var countdownJob: Job? = null

    fun retry() {
        val item = _state.value.item ?: return
        play(item)
    }

    fun togglePlayPause() = if (_state.value.isPlaying) pause() else resume()

    fun pause() {
        if (_state.value.isCasting) remote?.pause() else _player.value?.pause()
        saveProgress(force = true)
    }

    fun resume() {
        if (_state.value.isCasting) remote?.play() else _player.value?.play()
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceIn(0, _state.value.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
        if (_state.value.isCasting) {
            remote?.seek(MediaSeekOptions.Builder().setPosition(target).build())
        } else {
            _player.value?.seekTo(target)
        }
        _state.update { it.copy(positionMs = target) }
    }

    fun seekBy(deltaMs: Long) = seekTo(_state.value.positionMs + deltaMs)

    /** Stopt het afspelen helemaal (mini-speler weg). Een cast-sessie blijft verbonden. */
    fun stop() {
        saveProgress(force = true)
        loadJob?.cancel()
        tickJob?.cancel()
        if (_state.value.isCasting) remote?.stop()
        _player.value?.let { it.stop(); it.clearMediaItems() }
        _state.update { PlaybackState(castDevice = it.castDevice, castVolume = it.castVolume, castMuted = it.castMuted) }
        runCatching { appContext.stopService(Intent(appContext, SchatkamerPlaybackService::class.java)) }
    }

    fun setSubtitles(on: Boolean) {
        _state.update { it.copy(subtitlesOn = on) }
        _player.value?.let { p ->
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !on)
                .apply { if (on) setPreferredTextLanguage("nl") }
                .build()
        }
        remote?.let { client ->
            val textTrack = client.mediaInfo?.mediaTracks?.firstOrNull { it.type == com.google.android.gms.cast.MediaTrack.TYPE_TEXT }
            client.setActiveMediaTracks(if (on && textTrack != null) longArrayOf(textTrack.id) else longArrayOf())
        }
    }

    // --------------------------------------------------------------- lokaal

    private fun ensureLocal(): ExoPlayer {
        _player.value?.let { return it }
        val player = SchatkamerPlayback.buildPlayer(appContext)
        player.trackSelectionParameters = TrackSelectionParameters.Builder(appContext)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !_state.value.subtitlesOn)
            .setPreferredTextLanguage("nl")
            .build()
        player.addListener(localListener)
        val open = PendingIntent.getActivity(
            appContext, 0,
            Intent(appContext, MainActivity::class.java).setAction(ACTION_OPEN_PLAYER)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        mediaSession = MediaSession.Builder(appContext, player).setId("openschatkamer").setSessionActivity(open).build()
        _player.value = player
        return player
    }

    private fun loadLocally(media: PlayableMedia, playWhenReady: Boolean) {
        val player = ensureLocal()
        SchatkamerPlayback.play(appContext, player, media, playWhenReady)
        // Gewone start: Media3 maakt de service zelf voorgrond zodra er echt iets speelt.
        runCatching { appContext.startService(Intent(appContext, SchatkamerPlaybackService::class.java)) }
    }

    private val localListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!_state.value.isCasting) _state.update { it.copy(isPlaying = isPlaying) }
            if (!isPlaying) saveProgress(force = true)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (_state.value.isCasting) return
            val player = _player.value ?: return
            _state.update {
                it.copy(
                    isLoading = playbackState == Player.STATE_BUFFERING,
                    durationMs = player.duration.takeIf { d -> d > 0 } ?: it.durationMs
                )
            }
            if (playbackState == Player.STATE_ENDED) {
                saveProgress(force = true)
                onEnded()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (_state.value.isCasting) return
            // Een verlopen handtekening geeft 403: één keer opnieuw ophalen en verder op dezelfde plek.
            val media = _state.value.media
            val http = (error.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)
            if (media != null && http?.responseCode == 403 && !refreshedAfter403) {
                refreshedAfter403 = true
                val position = _player.value?.currentPosition ?: 0L
                scope.launch {
                    video.resolveStream(media.id, forceRefresh = true).onSuccess { stream ->
                        start(media.copy(stream = stream, resumePositionMs = position))
                    }.onFailure { e -> _state.update { it.copy(isLoading = false, error = describe(e)) } }
                }
                return
            }
            _state.update { it.copy(isLoading = false, isPlaying = false, error = describe(error)) }
        }
    }
    private var refreshedAfter403 = false

    // ---------------------------------------------------------------- casten

    private fun loadOnCast(media: PlayableMedia, positionMs: Long) {
        val client = remote ?: return loadLocally(media, playWhenReady = true)
        _state.update { it.copy(isLoading = true, error = null) }
        scope.launch {
            // De receiver krijgt het ondertekende adres; ververs het als het bijna verloopt.
            val fresh = if (media.stream.isExpired()) {
                video.resolveStream(media.id, forceRefresh = true).getOrNull()?.let { media.copy(stream = it) } ?: media
            } else media
            _state.update { it.copy(media = fresh) }

            val metadata = MediaMetadata(if (fresh.isAudio) MediaMetadata.MEDIA_TYPE_MUSIC_TRACK else MediaMetadata.MEDIA_TYPE_TV_SHOW).apply {
                putString(MediaMetadata.KEY_TITLE, fresh.title)
                putString(MediaMetadata.KEY_SUBTITLE, fresh.subtitle)
                if (!fresh.isAudio) putString(MediaMetadata.KEY_SERIES_TITLE, fresh.subtitle)
                fresh.posterUrl.takeIf { it.isNotBlank() }?.let { addImage(WebImage(Uri.parse(it))) }
            }
            val info = MediaInfo.Builder(fresh.stream.playUrl)
                .setContentUrl(fresh.stream.playUrl)
                .setContentType(CastPayload.CONTENT_TYPE)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setMetadata(metadata)
                .apply { if (fresh.stream.clipDurationMs > 0) setStreamDuration(fresh.stream.clipDurationMs) }
                .build()
            val request = MediaLoadRequestData.Builder()
                .setMediaInfo(info)
                .setAutoplay(true)
                .setCurrentTime(positionMs.coerceAtLeast(0))
                .setCustomData(CastPayload.customData(fresh, video.siteConfig()))
                .build()
            client.load(request).setResultCallback { result ->
                if (!result.status.isSuccess) {
                    _state.update {
                        it.copy(isLoading = false, error = "${it.castDevice ?: "De Chromecast"} kon dit programma niet laden (code ${result.status.statusCode}).")
                    }
                }
            }
        }
    }

    private val remoteCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val status = remote?.mediaStatus ?: return
            when (status.playerState) {
                MediaStatus.PLAYER_STATE_PLAYING -> _state.update { it.copy(isPlaying = true, isLoading = false, error = null) }
                MediaStatus.PLAYER_STATE_PAUSED -> _state.update { it.copy(isPlaying = false, isLoading = false) }
                MediaStatus.PLAYER_STATE_BUFFERING, MediaStatus.PLAYER_STATE_LOADING -> _state.update { it.copy(isLoading = true) }
                MediaStatus.PLAYER_STATE_IDLE -> when (status.idleReason) {
                    MediaStatus.IDLE_REASON_ERROR -> _state.update {
                        it.copy(isLoading = false, isPlaying = false, error = "Dit programma speelt niet af op ${it.castDevice ?: "de Chromecast"}.")
                    }
                    MediaStatus.IDLE_REASON_FINISHED -> {
                        _state.update { it.copy(isPlaying = false, isLoading = false) }
                        saveProgress(force = true)
                        onEnded()
                    }
                    else -> _state.update { it.copy(isPlaying = false, isLoading = false) }
                }
            }
            val volume = castContext?.sessionManager?.currentCastSession
            _state.update { it.copy(castVolume = volume?.volume?.toFloat() ?: it.castVolume, castMuted = volume?.isMute ?: it.castMuted) }
        }
    }

    private fun onCastConnected(session: CastSession) {
        session.remoteMediaClient?.registerCallback(remoteCallback)
        val name = session.castDevice?.friendlyName ?: "Chromecast"
        val wasLocal = !_state.value.isCasting
        val media = _state.value.media
        val position = _player.value?.currentPosition ?: _state.value.positionMs
        _state.update { it.copy(castDevice = name) }

        // Speelt de receiver al iets van ons (sessie hervat)? Dan niets opnieuw laden.
        val remoteBusy = session.remoteMediaClient?.mediaStatus?.playerState.let {
            it != null && it != MediaStatus.PLAYER_STATE_IDLE && it != MediaStatus.PLAYER_STATE_UNKNOWN
        }
        if (wasLocal && media != null && !remoteBusy) {
            _player.value?.pause()
            loadOnCast(media, position)
        }
        startTicking()
    }

    private fun onCastDisconnected() {
        val media = _state.value.media
        val position = _state.value.positionMs
        _state.update { it.copy(castDevice = null, isPlaying = false, isLoading = false, error = null) }
        // Verder op de telefoon, gepauzeerd op dezelfde plek (zoals Family7).
        if (media != null) loadLocally(media.copy(resumePositionMs = position), playWhenReady = false)
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = onCastConnected(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = onCastConnected(session)
        override fun onSessionEnded(session: CastSession, error: Int) = onCastDisconnected()
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) {}
        override fun onSessionEnding(session: CastSession) {
            saveProgress(force = true)
        }
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
    }

    fun setCastVolume(volume: Float) {
        runCatching { castContext?.sessionManager?.currentCastSession?.volume = volume.toDouble().coerceIn(0.0, 1.0) }
        _state.update { it.copy(castVolume = volume) }
    }

    fun toggleCastMute() {
        val session = castContext?.sessionManager?.currentCastSession ?: return
        runCatching { session.isMute = !session.isMute }
        _state.update { it.copy(castMuted = !it.castMuted) }
    }

    /** Stopt de cast-sessie; het programma gaat gepauzeerd verder op de telefoon. */
    fun stopCasting() {
        castContext?.sessionManager?.endCurrentSession(true)
    }

    // ------------------------------------------------------------- voortgang

    private fun startTicking() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            while (isActive) {
                val s = _state.value
                if (s.isCasting) {
                    remote?.let { client ->
                        val pos = client.approximateStreamPosition
                        val dur = client.streamDuration
                        _state.update { it.copy(positionMs = pos.coerceAtLeast(0), durationMs = if (dur > 0) dur else it.durationMs) }
                    }
                } else _player.value?.let { p ->
                    _state.update {
                        it.copy(positionMs = p.currentPosition.coerceAtLeast(0), durationMs = p.duration.takeIf { d -> d > 0 } ?: it.durationMs)
                    }
                }
                if (s.isPlaying) saveProgress()
                delay(500)
            }
        }
    }

    private fun saveProgress(force: Boolean = false) {
        val s = _state.value
        val item = s.item ?: return
        if (s.durationMs <= 0) return
        val now = System.currentTimeMillis()
        if (!force && now - lastSavedAt < 10_000) return
        lastSavedAt = now
        val saved = s.media?.toItem()?.copy(description = item.description, date = item.date) ?: item
        progress.saveProgress(saved.copy(id = item.id), s.positionMs, s.durationMs)
    }

    private fun describe(error: Throwable): String = when {
        error is NotPlayableException -> error.message!!
        error is IOException || error.cause is IOException ->
            "Geen verbinding met De Schatkamer. Controleer je internet en probeer het opnieuw."
        error is PlaybackException && error.errorCode in 6000..6999 ->
            "Dit programma is beveiligd en kan op dit toestel niet worden afgespeeld (DRM)."
        error is PlaybackException -> "Afspelen lukt niet: ${error.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')}."
        else -> error.message ?: "Er ging iets mis bij het afspelen."
    }

    fun release() {
        stop()
        mediaSession?.release()
        _player.value?.release()
        _player.value = null
        mediaSession = null
    }

    companion object {
        const val ACTION_OPEN_PLAYER = "nl.openschatkamer.ACTION_OPEN_PLAYER"
    }
}
