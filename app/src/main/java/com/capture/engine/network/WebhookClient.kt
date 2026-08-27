package com.capture.engine.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

data class WebhookResult(
    val isSuccess: Boolean,
    val statusCode: Int?,
    val responseTimeMs: Long,
    val responseBody: String?,
    val errorMessage: String?
)

class WebhookClient(private val okHttpClient: OkHttpClient = defaultClient()) {

    suspend fun sendPayload(
        url: String,
        jsonPayload: String,
        apiKey: String? = null
    ): WebhookResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonPayload.toRequestBody(mediaType)

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("User-Agent", "CaptureEngine-Android/1.0")

        if (!apiKey.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        try {
            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            val duration = System.currentTimeMillis() - startTime
            val body = response.body?.string()

            WebhookResult(
                isSuccess = response.isSuccessful,
                statusCode = response.code,
                responseTimeMs = duration,
                responseBody = body,
                errorMessage = if (!response.isSuccessful) "HTTP ${response.code}: ${response.message}" else null
            )
        } catch (e: IOException) {
            val duration = System.currentTimeMillis() - startTime
            Log.e("WebhookClient", "Network error sending payload", e)
            WebhookResult(
                isSuccess = false,
                statusCode = null,
                responseTimeMs = duration,
                responseBody = null,
                errorMessage = e.localizedMessage ?: "Unknown network failure"
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e("WebhookClient", "Unexpected error", e)
            WebhookResult(
                isSuccess = false,
                statusCode = null,
                responseTimeMs = duration,
                responseBody = null,
                errorMessage = e.localizedMessage ?: "Unexpected error"
            )
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()
        }
    }
}
