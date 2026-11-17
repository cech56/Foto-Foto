package com.fotofoto.app

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class VideoReconcileWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val token = SessionStore.read(applicationContext)?.optString("access_token").orEmpty()
        if (token.isBlank()) return@withContext Result.success()
        try {
            val request = Request.Builder()
                .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/tasks/reconcile")
                .header("Authorization", "Bearer " + token)
                .post(ByteArray(0).toRequestBody(null))
                .build()
            val response = client.newCall(request).execute()
            response.use {
                if (it.isSuccessful) Result.success()
                else if (it.code == 401 || it.code == 403) Result.success()
                else Result.retry()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private val client = OkHttpClient()

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<VideoReconcileWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "foto_foto_video_reconciliation",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

object VideoReconcileScheduler {
    fun schedule(context: Context) = VideoReconcileWorker.schedule(context)
}
