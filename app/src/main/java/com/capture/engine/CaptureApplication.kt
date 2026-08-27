package com.capture.engine

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.capture.engine.data.CaptureDatabase
import com.capture.engine.data.SettingsRepository

class CaptureApplication : Application() {

    lateinit var database: CaptureDatabase
        private set
    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = CaptureDatabase.getDatabase(this)
        settingsRepository = SettingsRepository(this)

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SCREEN_CAPTURE_CHANNEL_ID,
                getString(R.string.screen_capture_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.screen_capture_channel_desc)
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val SCREEN_CAPTURE_CHANNEL_ID = "screen_capture_channel"
        lateinit var instance: CaptureApplication
            private set
    }
}
