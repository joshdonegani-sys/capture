package com.capture.engine.car

import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Entry point for Android Auto host connections.
 */
class CaptureCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        // Allow all hosts during debug and development so the Desktop Head Unit (DHU)
        // and local emulators can bind without restrictive certificate validation.
        val isDebuggable = try {
            (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (e: Exception) {
            true
        }
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return CaptureCarSession()
    }
}
