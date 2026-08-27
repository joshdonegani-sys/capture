package com.capture.engine.services

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log
import com.capture.engine.model.MediaContext

class MediaMetadataListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.d(TAG, "MediaMetadataListener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance == this) {
            instance = null
        }
    }

    fun getCurrentMediaContext(): MediaContext? {
        try {
            val sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                ?: return null

            val component = ComponentName(this, MediaMetadataListener::class.java)
            val activeSessions = sessionManager.getActiveSessions(component)

            val activeController = activeSessions.firstOrNull { controller ->
                val state = controller.playbackState?.state
                state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
            } ?: activeSessions.firstOrNull() ?: return null

            val metadata = activeController.metadata
            val playbackState = activeController.playbackState

            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_AUTHOR)

            val stateString = when (playbackState?.state) {
                PlaybackState.STATE_PLAYING -> "playing"
                PlaybackState.STATE_PAUSED -> "paused"
                PlaybackState.STATE_BUFFERING -> "buffering"
                PlaybackState.STATE_STOPPED -> "stopped"
                else -> "unknown"
            }

            return MediaContext(
                platform = activeController.packageName,
                title = title,
                channelOrArtist = artist,
                canonicalUrl = null,
                timestampMs = playbackState?.position,
                playbackState = stateString
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification listener permission not granted", e)
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching media metadata", e)
            return null
        }
    }

    companion object {
        private const val TAG = "MediaMetadataListener"
        var instance: MediaMetadataListener? = null
            private set

        val isConnected: Boolean
            get() = instance != null
    }
}
