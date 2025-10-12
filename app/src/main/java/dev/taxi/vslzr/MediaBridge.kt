package dev.taxi.vslzr

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService

class MediaBridge : NotificationListenerService() {
    companion object {
        fun isPlaying(ctx: Context): Boolean = try {
            val mgr: MediaSessionManager =
                ctx.getSystemService(MediaSessionManager::class.java)
            val comps = ComponentName(ctx, MediaBridge::class.java)
            val sessions: List<MediaController> = mgr.getActiveSessions(comps)
            sessions.any { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        } catch (_: SecurityException) {
            false
        } catch (_: Throwable) {
            false
        }
    }
}
