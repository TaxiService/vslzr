package dev.taxi.vslzr

import android.content.ComponentName
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService

class MediaBridge : NotificationListenerService() {
    companion object {
        fun isPlaying(ctx: android.content.Context): Boolean {
            val mgr = ctx.getSystemService(MediaSessionManager::class.java)
            val comps = ComponentName(ctx, MediaBridge::class.java)
            val sessions = mgr.getActiveSessions(comps)
            return sessions.any { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        }
    }
}
