package com.fotofoto.app

import android.os.Bundle
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import org.json.JSONObject

private val Bg = Color(0xFF0B0E18)
private val Panel = Color(0xFF151B2B)
private val Accent = Color(0xFFFF795E)
private val Muted = Color(0xFF9AA3B6)
private val White = Color(0xFFF8F6F2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FotoFotoApp() }
    }
}

@Composable
private fun FotoFotoApp() {
    var prompt by remember { mutableStateOf("") }
    var duration by remember { mutableIntStateOf(5) }
    var ratio by remember { mutableStateOf("1280:720") }
    var status by remember { mutableStateOf("Fikrini yaz, ilk videonu oluşturmaya hazırlan.") }
    var loading by remember { mutableStateOf(false) }
    var taskId by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

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
                    Text("AI STUDIO", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
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
                            value = prompt, onValueChange = { if (it.length <= 2500) prompt = it },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                            placeholder = { Text("Örnek: Gün batımında deniz kenarında ilerleyen kırmızı klasik bir araba, sinematik kamera hareketi…", color = Muted) },
                            minLines = 5, maxLines = 8,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Color(0xFF343B4D), focusedTextColor = White, unfocusedTextColor = White)
                        )
                        Text("${prompt.length} / 2500", color = Muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.End).padding(top = 5.dp))
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
                            ChoiceButton("1:1", ratio == "960:960") { ratio = "960:960" }
                        }
                        Spacer(Modifier.height(22.dp))
                        Button(onClick = {
                            scope.launch {
                                loading = true; status = "İstek gönderiliyor…"; taskId = ""
                                val result = submitGeneration(prompt, duration, ratio)
                                status = result.first; taskId = result.second; loading = false
                            }
                        }, enabled = !loading && prompt.trim().length >= 10, modifier = Modifier.fillMaxWidth().height(54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF211721)), shape = RoundedCornerShape(13.dp)) {
                            Text(if (loading) "GÖNDERİLİYOR…" else "✦  VİDEOMU OLUŞTUR", fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(status, color = Accent, fontSize = 12.sp, lineHeight = 18.sp)
                        if (taskId.isNotBlank()) Text("Üretim kodu: $taskId", color = Muted, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("FOTO FOTO CÜZDAN", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("Yakında", color = Accent, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("Kredi sistemi: 1 kredi = 1 TL", color = Muted, fontSize = 11.sp)
                    }
                }
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

private suspend fun submitGeneration(prompt: String, duration: Int, ratio: String): Pair<String, String> = withContext(Dispatchers.IO) {
    if (BuildConfig.API_BASE_URL.contains("YOUR-BACKEND-DOMAIN")) return@withContext "Video sunucusu henüz bağlanmadı." to ""
    try {
        val json = JSONObject().put("prompt", prompt).put("model", "gen4.5").put("duration", duration).put("ratio", ratio)
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/generate").post(body).build()
        OkHttpClient().newCall(request).execute().use { response ->
            val data = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful) return@withContext data.optString("message", "Üretim başlatılamadı.") to ""
            "Üretim isteği gönderildi. Görev takibi sonraki aşamada eklenecek." to data.optString("taskId")
        }
    } catch (_: Exception) { "Sunucuya bağlanılamadı. İnternet bağlantını kontrol et." to "" }
}
