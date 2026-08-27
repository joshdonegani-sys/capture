package com.capture.engine.model

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val inputPreview: String?,
    val hasScreenGrab: Boolean,
    val hasAppTree: Boolean,
    val hasHighlightedText: Boolean,
    val hasMedia: Boolean,
    val rawJson: String,
    val status: DeliveryStatus = DeliveryStatus.SENDING,
    val responseCode: Int? = null,
    val responseTimeMs: Long? = null,
    val errorMessage: String? = null,
    val retryCount: Int = 0
)
