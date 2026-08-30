package com.capture.engine.network

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.capture.engine.CaptureApplication
import com.capture.engine.model.DeliveryStatus
import java.util.concurrent.TimeUnit

class WebhookWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? CaptureApplication ?: return Result.failure()
        val dao = app.database.captureDao()
        val settings = app.settingsRepository
        val client = WebhookClient()

        val pendingErrors = dao.getByStatus(DeliveryStatus.ERROR)
        for (capture in pendingErrors) {
            val result = client.sendPayload(
                url = settings.webhookUrl,
                jsonPayload = capture.getRawJson(applicationContext),
                apiKey = settings.apiKey.ifBlank { null }
            )

            if (result.isSuccess) {
                dao.update(
                    capture.copy(
                        status = DeliveryStatus.SUCCESS,
                        responseCode = result.statusCode,
                        responseTimeMs = result.responseTimeMs,
                        errorMessage = null,
                        retryCount = capture.retryCount + 1
                    )
                )
            } else {
                dao.update(
                    capture.copy(
                        status = DeliveryStatus.ERROR,
                        responseCode = result.statusCode,
                        responseTimeMs = result.responseTimeMs,
                        errorMessage = result.errorMessage,
                        retryCount = capture.retryCount + 1
                    )
                )
            }
        }

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "webhook_retry_work"

        fun schedulePeriodicRetry(context: Context, intervalMinutes: Int) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // WorkManager periodic minimum is 15 minutes
            val minutes = intervalMinutes.coerceAtLeast(15)

            val request = PeriodicWorkRequestBuilder<WebhookWorker>(
                minutes.toLong(), TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
