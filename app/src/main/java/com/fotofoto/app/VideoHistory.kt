package com.fotofoto.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private val historyHttp = OkHttpClient()

data class VideoHistoryItem(
    val taskId: String,
    val prompt: String,
    val status: String,
    val videoUrl: String
)

private fun historyHeaders(context: Context, builder: Request.Builder): Request.Builder {
    val token = SessionStore.read(context)?.optString("access_token").orEmpty()
    return builder.addHeader("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
        .addHeader("Authorization", "Bearer $token")
}

suspend fun saveVideoHistory(context: Context, taskId: String, prompt: String, duration: Int, ratio: String) =
    withContext(Dispatchers.IO) {
        try {
            val token = SessionStore.read(context)?.optString("access_token").orEmpty()
            if (token.isBlank()) return@withContext false
            val userRequest = Request.Builder()
                .url(BuildConfig.SUPABASE_URL.trimEnd('/') + "/auth/v1/user")
                .addHeader("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .get().build()
            val userId = historyHttp.newCall(userRequest).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                JSONObject(response.body?.string().orEmpty()).optString("id")
            }
            if (userId.isBlank()) return@withContext false
            val body = JSONObject()
                .put("user_id", userId)
                .put("provider_task_id", taskId)
                .put("prompt", prompt)
                .put("duration_seconds", duration)
                .put("aspect_ratio", ratio)
                .put("status", "PENDING")
                .toString().toRequestBody("application/json".toMediaType())
            val request = historyHeaders(context, Request.Builder()
                .url(BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/video_generations"))
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .post(body).build()
            historyHttp.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) { false }
    }

suspend fun updateVideoHistory(context: Context, taskId: String, status: String, videoUrl: String? = null) =
    withContext(Dispatchers.IO) {
        try {
            val data = JSONObject().put("status", status).put("updated_at", java.time.Instant.now().toString())
            if (!videoUrl.isNullOrBlank()) data.put("video_url", videoUrl)
            val body = data.toString().toRequestBody("application/json".toMediaType())
            val request = historyHeaders(context, Request.Builder()
                .url(BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/video_generations?provider_task_id=eq." + Uri.encode(taskId)))
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .patch(body).build()
            historyHttp.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) { false }
    }

suspend fun loadVideoHistory(context: Context): List<VideoHistoryItem> = withContext(Dispatchers.IO) {
    try {
        val request = historyHeaders(context, Request.Builder()
            .url(BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/video_generations?select=provider_task_id,prompt,status,video_url&order=created_at.desc&limit=10"))
            .get().build()
        historyHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val rows = JSONArray(response.body?.string().orEmpty())
            (0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i)
                VideoHistoryItem(row.optString("provider_task_id"), row.optString("prompt"),
                    row.optString("status"), row.optString("video_url"))
            }
        }
    } catch (_: Exception) { emptyList() }
}

@Composable
fun VideoHistoryCard(refreshKey: Int) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<VideoHistoryItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(refreshKey) {
        loading = true
        items = loadVideoHistory(context)
        loading = false
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151B2B)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text("VİDEO GEÇMİŞİ", color = Color(0xFFFF795E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            when {
                loading -> Text("Geçmiş yükleniyor…", color = Color(0xFF9AA3B6), fontSize = 12.sp)
                items.isEmpty() -> Text("Henüz oluşturulmuş video yok.", color = Color(0xFF9AA3B6), fontSize = 12.sp)
                else -> items.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = Color(0xFF30384A))
                    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                        Text(item.prompt, color = Color.White, fontSize = 12.sp, maxLines = 2)
                        Spacer(Modifier.height(4.dp))
                        Text(item.status, color = Color(0xFFFF795E), fontSize = 10.sp)
                        if (item.videoUrl.startsWith("https://")) {
                            TextButton(onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.videoUrl)))
                            }, contentPadding = PaddingValues(0.dp)) {
                                Text("VİDEOYU AÇ", color = Color(0xFFFF795E), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
