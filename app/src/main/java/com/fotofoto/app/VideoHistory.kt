package com.fotofoto.app

import android.content.Context
import android.content.Intent
import android.app.DownloadManager
import android.os.Environment
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import kotlinx.coroutines.launch
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

suspend fun refreshVideoHistoryItem(context: Context, taskId: String): String = withContext(Dispatchers.IO) {
    try {
        val token = SessionStore.read(context)?.optString("access_token").orEmpty()
        if (token.isBlank()) return@withContext "Oturum bulunamadı. Tekrar giriş yap."
        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/tasks/" + Uri.encode(taskId))
            .header("Authorization", "Bearer $token")
            .get().build()
        historyHttp.newCall(request).execute().use { response ->
            val data = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful) return@withContext data.optString("message").ifBlank { "Görev durumu alınamadı." }
            val status = data.optString("status", "PENDING").uppercase()
            val output = data.optJSONArray("output")
            val videoUrl = output?.optString(0).orEmpty().takeIf { it.startsWith("https://") }
            updateVideoHistory(context, taskId, status, videoUrl)
            when (status) {
                "SUCCEEDED" -> "Video hazır."
                "FAILED" -> "Üretim başarısız oldu. Ayrılan krediler iade edildi."
                "CANCELED", "CANCELLED" -> "Üretim iptal edildi. Ayrılan krediler iade edildi."
                else -> "Video hâlâ hazırlanıyor: $status"
            }
        }
    } catch (_: Exception) {
        "Durum kontrol edilemedi. İnternet bağlantını kontrol et."
    }
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
fun VideoHistoryCard(refreshKey: Int, language: String = "Türkçe") {
    val en = language == "English"
    val ar = language == "العربية"
    fun tr(t: String, e: String, a: String) = if (en) e else if (ar) a else t
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<VideoHistoryItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var checkingTask by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf("") }
    suspend fun reloadHistory() {
        loading = true
        items = loadVideoHistory(context)
        loading = false
    }
    LaunchedEffect(refreshKey) {
        reloadHistory()
        // Projelerim ekranı açıkken bekleyen görevleri düzenli kontrol et.
        // Uygulama kapalıyken arka plan takibi değildir; ekran yeniden açılınca kontrol devam eder.
        while (true) {
            kotlinx.coroutines.delay(25_000)
            val pending = items.filter {
                it.status.uppercase() !in listOf("SUCCEEDED", "FAILED", "CANCELED", "CANCELLED")
            }
            if (pending.isNotEmpty()) {
                pending.forEach { item ->
                    refreshVideoHistoryItem(context, item.taskId)
                }
                reloadHistory()
            }
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151B2B)), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF2D3550), RoundedCornerShape(24.dp))) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tr("VİDEO GEÇMİŞİ", "VIDEO HISTORY", "سجل الفيديو"), color = Color(0xFFB99AFF), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                TextButton(onClick = { scope.launch { reloadHistory() } }, enabled = !loading && checkingTask.isBlank(), contentPadding = PaddingValues(0.dp)) {
                    Text(tr("YENİLE", "REFRESH", "تحديث"), color = Color(0xFFFFA47D), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (feedback.isNotBlank()) Text(feedback, color = Color(0xFF9AA3B6), fontSize = 10.sp)
            Spacer(Modifier.height(8.dp))
            when {
                loading -> Text(tr("Geçmiş yükleniyor…", "Loading history…", "جارٍ تحميل السجل…"), color = Color(0xFF9AA3B6), fontSize = 12.sp)
                items.isEmpty() -> Text(tr("Henüz oluşturulmuş video yok.", "No videos created yet.", "لم يتم إنشاء مقاطع فيديو بعد."), color = Color(0xFF9AA3B6), fontSize = 12.sp)
                else -> items.forEachIndexed { index, item ->
                    val state = item.status.uppercase()
                    val done = state == "SUCCEEDED"
                    val failed = state in listOf("FAILED", "CANCELED", "CANCELLED")
                    val stateColor = if (done) Color(0xFF55D6A2) else if (failed) Color(0xFFFF718B) else Color(0xFFFFC46B)
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = if (index == 0) 2.dp else 9.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF101726)),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(13.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Box(Modifier.size(38.dp).background(Color(0xFF30204A), RoundedCornerShape(11.dp)), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                    Text("▶", color = Color(0xFFB99AFF), fontSize = 17.sp, fontWeight = FontWeight.Black)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(tr("FOTO FOTO PROJESİ", "FOTO FOTO PROJECT", "مشروع FOTO FOTO"), color = Color(0xFF9AA3B6), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                    Text(item.prompt.ifBlank { tr("İsimsiz video projesi", "Untitled video project", "مشروع فيديو بلا عنوان") }, color = Color.White, fontSize = 12.sp, maxLines = 2)
                                }
                                Box(Modifier.background(stateColor.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 5.dp)) {
                                    Text(when { done -> tr("HAZIR", "READY", "جاهز"); failed -> tr("BAŞARISIZ", "FAILED", "فشل"); state == "PENDING" || state == "THROTTLED" -> tr("SIRADA", "QUEUED", "في الانتظار"); else -> tr("ÜRETİLİYOR", "GENERATING", "قيد الإنشاء") }, color = stateColor, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(7.dp))
                            if (!done && !failed) {
                                TextButton(
                                    onClick = {
                                        checkingTask = item.taskId
                                        feedback = ""
                                        scope.launch {
                                            feedback = refreshVideoHistoryItem(context, item.taskId)
                                            checkingTask = ""
                                            reloadHistory()
                                        }
                                    },
                                    enabled = checkingTask.isBlank(),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(if (checkingTask == item.taskId) tr("KONTROL EDİLİYOR…", "CHECKING…", "جارٍ التحقق…") else "↻  " + tr("DURUMU KONTROL ET", "CHECK STATUS", "تحقق من الحالة"), color = Color(0xFFFFB36B), fontSize = 10.sp)
                                }
                            }
                            if (item.videoUrl.startsWith("https://")) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.videoUrl))) }, contentPadding = PaddingValues(0.dp)) {
                                        Text("▶  " + tr("VİDEOYU AÇ", "OPEN VIDEO", "افتح الفيديو"), color = Color(0xFF55D6D0), fontSize = 10.sp)
                                    }
                                    TextButton(onClick = {
                                        val safeId = item.taskId.take(12).replace(Regex("[^A-Za-z0-9_-]"), "")
                                        val request = DownloadManager.Request(Uri.parse(item.videoUrl))
                                            .setTitle("FOTO FOTO videon").setDescription("Video indiriliyor…")
                                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "foto-foto-$safeId.mp4")
                                            .setMimeType("video/mp4")
                                        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                                    }, contentPadding = PaddingValues(0.dp)) {
                                        Text("↓  " + tr("İNDİR", "DOWNLOAD", "تنزيل"), color = Color(0xFFFFB36B), fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
