package com.capture.engine.model

import android.content.Context
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.File

enum class DeliveryStatus {
    SENDING,
    SUCCESS,
    ERROR
}

@Entity(tableName = "captures")
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String,
    val source: String,
    val packageName: String?,
    val appName: String? = null,
    val inputPreview: String?,
    val hasScreenGrab: Boolean,
    val hasAppTree: Boolean,
    val hasHighlightedText: Boolean,
    val hasMedia: Boolean,
    val hasShareContext: Boolean = false,
    val hasAttachment: Boolean = false,
    val payloadFilePath: String,
    val status: DeliveryStatus = DeliveryStatus.SENDING,
    val responseCode: Int? = null,
    val responseTimeMs: Long? = null,
    val errorMessage: String? = null,
    val retryCount: Int = 0
) {
    fun getRawJson(context: Context? = null): String {
        return try {
            val file = File(payloadFilePath)
            if (file.exists()) {
                file.readText(Charsets.UTF_8)
            } else ""
        } catch (e: Exception) {
            ""
        }
    }
}
