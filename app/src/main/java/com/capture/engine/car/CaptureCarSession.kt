package com.capture.engine.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

/**
 * Manages the lifecycle of the Capture Engine session on the car display.
 */
class CaptureCarSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        return CaptureCarScreen(carContext)
    }
}
