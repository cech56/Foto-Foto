package com.fotofoto.app

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private val Bg = Color(0xFF0B0E18)
private val Panel = Color(0xFF151B2B)
private val Accent = Color(0xFFFF795E)
private val Muted = Color(0xFF9AA3B6)
private val White = Color(0xFFF8F6F2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AuthGate { onSignOut -> FotoFotoApp(onSignOut) } }
    }
}

@Composable
private fun FotoFotoApp(onSignOut: () -> Unit) {
    var prompt by remember { mutableStateOf("") }
    var duration by remember { mutableIntStateOf(5) }
    var ratio by remember { mutableStateOf("1280:720") }
    var status by remember { mutableStateOf("Fikrini yaz, ilk videonu oluşturmaya hazırlan.") }
    var loading by remember { mutableStateOf(false) }
    var taskId by remember { mutableStateOf("") }
    var videoUrl by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Panel, onPrimary = Color(0xFF211721), onBackground = White, onSurface = White)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF21182A), Bg, Bg)))) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(Accent, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                        Text("F", color = Color(0xFF211721), fontSize = 25.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("FOTO FOTO", fontSize = 19.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onSignOut) { Text("ÇIKIŞ", color = Muted, fontSize = 10.sp) }
                }
                Spacer(Modifier.height(34.dp))
                Text("HAYAL ET.", color = White, fontSize = 35.sp, fontWeight = FontWeight.Black)
                Text("FOTO FOTO OLUŞTUR.", color = Accent, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                Text("Aklındaki sahneyi anlat. Yapay zekâ videona dönüştürsün.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(25.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("YENİ VİDEO", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(9.dp))
                        Text("Ne hayal ediyorsun?", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = prompt, onValueChange = { if (it.length <= 1000) prompt = it },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                            placeholder = { Text("Örnek: Gün batımında deniz kenarında ilerleyen kırmızı klasik bir araba, sinematik kamera hareketi…", color = Muted) },
                            minLines = 5, maxLines = 8,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Color(0xFF343B4D), focusedTextColor = White, unfocusedTextColor = White)
                        )
                        Text("${prompt.length} / 1000", color = Muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.End).padding(top = 5.dp))
                        Spacer(Modifier.height(17.dp))
                        Text("VİDEO MOTORU", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(9.dp))
                        EngineCard("RUNWAY", "Gen-4.5 · İlk aktif motor", true, true)
                        Spacer(Modifier.height(8.dp))
                        EngineCard("KLING", "API bağlantısı sonraki aşamada", false, false)
                        Spacer(Modifier.height(8.dp))
                        EngineCard("VEO", "API bağlantısı sonraki aşamada", false, false)
                        Spacer(Modifier.height(18.dp))
                        Text("VİDEO SÜRESİ", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.padding(top = 8.dp)) {
                            ChoiceButton("5 saniye", duration == 5) { duration = 5 }
                            ChoiceButton("10 saniye", duration == 10) { duration = 10 }
                        }
                        Spacer(Modifier.height(15.dp))
                        Text("VİDEO FORMATI", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            ChoiceButton("16:9", ratio == "1280:720") { ratio = "1280:720" }
                            ChoiceButton("9:16", ratio == "720:1280") { ratio = "720:1280" }
                        }
                        Spacer(Modifier.height(22.dp))
                        Button(onClick = {
                            scope.launch {
                                loading = true; status = "İstek gönderiliyor…"; taskId = ""; videoUrl = ""
                                val result = submitGeneration(context, prompt, duration, ratio)
                                status = result.first
                                taskId = result.second
                                if (taskId.isNotBlank()) {
                                    status = "Video kuyruğa alındı. Üretim durumu kontrol ediliyor…"
                                    for (attempt in 0 until 120) {
                                        delay(5000)
                                        val task = checkGeneration(context, taskId)
                                        status = task.message
                                        if (task.videoUrl.isNotBlank()) videoUrl = task.videoUrl
                                        if (task.finished) break
                                    }
                                }
                                if (loading && taskId.isNotBlank() && videoUrl.isBlank() && status.contains("kontrol")) status = "Üretim takibi zaman aşımına uğradı. Daha sonra tekrar kontrol edebilirsin."
                                loading = false
                            }
                        }, enabled = !loading && prompt.trim().length >= 10, modifier = Modifier.fillMaxWidth().height(54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF211721)), shape = RoundedCornerShape(13.dp)) {
                            Text(if (loading) "GÖNDERİLİYOR…" else "✦  VİDEOMU OLUŞTUR", fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(status, color = Accent, fontSize = 12.sp, lineHeight = 18.sp)
                        if (taskId.isNotBlank()) Text("Üretim kodu: $taskId", color = Muted, fontSize = 10.sp)
                        if (videoUrl.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                                context.startActivity(intent)
                            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242D40))) {
                                Text("VİDEOYU AÇ", color = White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                AccountWalletCard()
                Spacer(Modifier.height(16.dp))
                VideoHistoryCard(historyRefresh)
                Spacer(Modifier.height(16.dp))
                Spacer(Modifier.height(26.dp))
                Text("FOTO FOTO  ·  HAYAL ET, OLUŞTUR.", color = Muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun EngineCard(name: String, detail: String, active: Boolean, selected: Boolean) {
    Row(Modifier.fillMaxWidth().background(if (selected) Color(0xFF30202B) else Color(0xFF101625), RoundedCornerShape(12.dp))
        .border(1.dp, if (selected) Accent else Color(0xFF30384A), RoundedCornerShape(12.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (active) White else Muted)
            Text(detail, color = Muted, fontSize = 10.sp)
        }
        Text(if (active) "AKTİF" else "YAKINDA", color = if (active) Accent else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RowScope.ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (selected) Accent else White),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Accent else Color(0xFF394154)),
        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 5.dp), shape = RoundedCornerShape(10.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

private data class TaskCheck(val message: String, val finished: Boolean, val videoUrl: String = "")

private suspend fun checkGeneration(context: android.content.Context, taskId: String): TaskCheck = withContext(Dispatchers.IO) {
    try {
        val token = SessionStore.read(context)?.optString("access_token").orEmpty()
        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/tasks/" + taskId)
            .header("Authorization", "Bearer $token")
            .get().build()
        OkHttpClient().newCall(request).execute().use { response ->
            val data = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful) return@withContext TaskCheck(data.optString("message", "Görev durumu alınamadı."), true)
            when (data.optString("status").uppercase()) {
                "SUCCEEDED" -> {
                    val output = data.optJSONArray("output")
                    val videoUrl = output?.optString(0).orEmpty()
                    if (videoUrl.startsWith("https://")) TaskCheck("Video hazır! Aşağıdaki butondan açabilirsin.", true, videoUrl)
                    else TaskCheck("Video tamamlandı; ancak video bağlantısı yanıtta bulunamadı.", true)
                }
                "FAILED" -> { updateVideoHistory(context, taskId, "FAILED"); TaskCheck("Video üretimi başarısız oldu. Başka bir açıklamayla tekrar deneyebilirsin.", true) }
                "CANCELED", "CANCELLED" -> { updateVideoHistory(context, taskId, "CANCELED"); TaskCheck("Video üretimi iptal edildi.", true) }
                else -> { updateVideoHistory(context, taskId, data.optString("status", "PENDING")); TaskCheck("Video hazırlanıyor… Durum: " + data.optString("status", "BEKLEMEDE"), false) }
            }
        }
    } catch (_: Exception) { TaskCheck("Görev durumu kontrol edilemedi. Bağlantı yeniden denenecek.", false) }
}

private suspend fun submitGeneration(context: android.content.Context, prompt: String, duration: Int, ratio: String): Pair<String, String> = withContext(Dispatchers.IO) {
    if (BuildConfig.API_BASE_URL.contains("YOUR-BACKEND-DOMAIN")) return@withContext "Video sunucusu henüz bağlanmadı." to ""
    try {
        val json = JSONObject().put("prompt", prompt).put("model", "gen4.5").put("duration", duration).put("ratio", ratio)
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val token = SessionStore.read(context)?.optString("access_token").orEmpty()
        val request = Request.Builder().url(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/generate")
            .header("Authorization", "Bearer $token").post(body).build()
        OkHttpClient().newCall(request).execute().use { response ->
            val data = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful) return@withContext data.optString("message", "Üretim başlatılamadı.") to ""
            "Üretim isteği kabul edildi. Video hazırlanıyor…" to data.optString("taskId")
        }
    } catch (_: Exception) { "Sunucuya bağlanılamadı. İnternet bağlantını kontrol et." to "" }
}
