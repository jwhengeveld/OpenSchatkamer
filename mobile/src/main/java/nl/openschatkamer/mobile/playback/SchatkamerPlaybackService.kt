package nl.openschatkamer.mobile.playback

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import nl.openschatkamer.mobile.SchatkamerMobileApp

@OptIn(UnstableApi::class)
class SchatkamerPlaybackService : MediaSessionService() {

    override fun onCreate() {
        super.onCreate()
        val app = application as? SchatkamerMobileApp
        val session = app?.playback?.mediaSession
        if (session != null && !isSessionAdded(session)) {
            addSession(session)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        val app = application as? SchatkamerMobileApp
        return app?.playback?.mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as? SchatkamerMobileApp
        val session = app?.playback?.mediaSession
        if (session != null && !isSessionAdded(session)) {
            addSession(session)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val app = application as? SchatkamerMobileApp
        val player = app?.playback?.player?.value
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        val app = application as? SchatkamerMobileApp
        val session = app?.playback?.mediaSession
        if (session != null && isSessionAdded(session)) {
            removeSession(session)
        }
        super.onDestroy()
    }
}
