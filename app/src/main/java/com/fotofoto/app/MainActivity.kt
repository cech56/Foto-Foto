package com.fotofoto.app

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.app.DownloadManager
import android.os.Environment
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
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
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var studioMode by remember { mutableStateOf("generate") }
    var upscaleResolution by remember { mutableStateOf("2k") }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> selectedImageUri = uri }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> selectedVideoUri = uri }
    var duration by remember { mutableIntStateOf(5) }
    var ratio by remember { mutableStateOf("1280:720") }
    var status by remember { mutableStateOf("Fikrini yaz, ilk videonu oluşturmaya hazırlan.") }
    var loading by remember { mutableStateOf(false) }
    var taskId by remember { mutableStateOf("") }
    var videoUrl by remember { mutableStateOf("") }
    var historyRefresh by remember { mutableIntStateOf(0) }
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
                        Text("YAPAY ZEKÂ STÜDYOSU", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            ChoiceButton("OLUŞTUR", studioMode == "generate") { studioMode = "generate" }
                            ChoiceButton("DÜZENLE", studioMode == "edit") { studioMode = "edit" }
                            ChoiceButton("KALİTE", studioMode == "upscale") { studioMode = "upscale" }
                            ChoiceButton("KARAKTER", studioMode == "character") { studioMode = "character" }
                        }
                        if (studioMode != "generate") {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Bu özellik şu anda hazırlık aşamasında. Güvenli kredi tarifesi ve dosya yükleme tamamlandığında açılacak. Şimdilik Runway Gen-4.5 ile video oluşturabilirsin.",
                                color = Accent, fontSize = 11.sp, lineHeight = 16.sp
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(when (studioMode) { "edit" -> "Videonda ne değişsin?"; "upscale" -> "Videonu hangi kaliteye yükseltelim?"; "character" -> "Karakterine hareket ver"; else -> "Ne hayal ediyorsun?" }, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = prompt, onValueChange = { if (it.length <= 1000) prompt = it },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                            placeholder = { Text(when (studioMode) { "edit" -> "Örnek: Arka planı gün batımı sahiline dönüştür, kişiyi ve kamera hareketini koru…"; "upscale" -> "İsteğe bağlı: Doğal detayları koru, görüntüyü netleştir…"; "character" -> "Karakterinin nasıl hareket etmesini istediğini açıkla (isteğe bağlı)…"; else -> "Örnek: Gün batımında deniz kenarında ilerleyen kırmızı klasik bir araba, sinematik kamera hareketi…" }, color = Muted) },
                            minLines = 5, maxLines = 8,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Color(0xFF343B4D), focusedTextColor = White, unfocusedTextColor = White)
                        )
                        Text("${prompt.length} / 1000", color = Muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.End).padding(top = 5.dp))
                        Spacer(Modifier.height(14.dp))
                        if (studioMode == "generate") {
                            Text("REFERANS FOTOĞRAFI", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Button(onClick = { imagePicker.launch("image/*") }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242D40))) { Text(if (selectedImageUri == null) "＋ FOTOĞRAF EKLE" else "FOTOĞRAFI DEĞİŞTİR", color = White) }
                                if (selectedImageUri != null) TextButton(onClick = {
                                selectedImageUri = null
                                if (ratio !in listOf("1280:720", "720:1280")) ratio = "1280:720"
                            }) { Text("KALDIR", color = Accent) }
                            }
                            if (selectedImageUri != null) {
                                val previewBitmap = remember(selectedImageUri) { try { context.contentResolver.openInputStream(selectedImageUri!!)?.use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null } }
                                previewBitmap?.let { Image(bitmap = it.asImageBitmap(), contentDescription = "Referans fotoğrafı", modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp)) }
                                Text("Fotoğraf videonun ilk karesi olarak kullanılacak.", color = Muted, fontSize = 10.sp)
                            } else Text("Kendi fotoğrafını veya canlandırmak istediğin görseli ekle.", color = Muted, fontSize = 10.sp)
                        } else {
                            if (studioMode == "character") {
                                Text("KARAKTER FOTOĞRAFI", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(7.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Button(onClick = { imagePicker.launch("image/*") }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242D40))) {
                                        Text(if (selectedImageUri == null) "＋ KARAKTER FOTOĞRAFI" else "FOTOĞRAFI DEĞİŞTİR", color = White)
                                    }
                                    if (selectedImageUri != null) TextButton(onClick = { selectedImageUri = null }) { Text("KALDIR", color = Accent) }
                                }
                                Text("Hareket videosunda yüzü net görünen tek kişi olmalı.", color = Muted, fontSize = 10.sp)
                            }
                            Text(if (studioMode == "edit") "DÜZENLENECEK VİDEO" else if (studioMode == "character") "HAREKET / PERFORMANS VİDEOSU" else "KALİTESİ YÜKSELTİLECEK VİDEO", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(onClick = { videoPicker.launch("video/*") }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242D40))) {
                                    Text(if (selectedVideoUri == null) "＋ VİDEO SEÇ" else "VİDEOYU DEĞİŞTİR", color = White)
                                }
                                if (selectedVideoUri != null) TextButton(onClick = { selectedVideoUri = null }) { Text("KALDIR", color = Accent) }
                            }
                            Text(when (studioMode) { "edit" -> "Runway Aleph 2 · En fazla 30 saniye · İlk sürümde dosya boyutu 3,5 MB."; "character" -> "Act-Two · 3–30 saniyelik performans videosu · Tek kişi, yüz görünür olmalı."; else -> "Magnific Video Upscaler · 720p, 1K, 2K veya 4K · En fazla 30 saniye." }, color = Muted, fontSize = 10.sp)
                            if (studioMode == "upscale") {
                                Spacer(Modifier.height(10.dp))
                                Text("HEDEF ÇÖZÜNÜRLÜK", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                                    listOf("720p", "1k", "2k", "4k").forEach { value ->
                                        ChoiceButton(value.uppercase(), upscaleResolution == value) { upscaleResolution = value }
                                    }
                                }
                            }
                        }
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
                        if (studioMode == "generate") {
                            Text(
                                if (duration == 5) "Bu üretimin ücreti: 59 FOTO FOTO kredisi (59 TL)"
                                else "Bu üretimin ücreti: 119 FOTO FOTO kredisi (119 TL)",
                                color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 9.dp)
                            )
                            Text("Üretim başarısız olursa ayrılan krediler iade edilir.", color = Muted, fontSize = 10.sp)
                        }
                        Spacer(Modifier.height(15.dp))
                        Text("VİDEO FORMATI", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ChoiceButton("16:9", ratio == "1280:720") { ratio = "1280:720" }
                                ChoiceButton("9:16", ratio == "720:1280") { ratio = "720:1280" }
                            }
                            if (selectedImageUri != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ChoiceButton("1:1", ratio == "960:960") { ratio = "960:960" }
                                    ChoiceButton("4:3", ratio == "1104:832") { ratio = "1104:832" }
                                    ChoiceButton("3:4", ratio == "832:1104") { ratio = "832:1104" }
                                    ChoiceButton("21:9", ratio == "1584:672") { ratio = "1584:672" }
                                }
                            }
                        }
                        Spacer(Modifier.height(22.dp))
                        Button(onClick = {
                            scope.launch {
                                loading = true; status = "İstek gönderiliyor…"; taskId = ""; videoUrl = ""
                                val result = when (studioMode) {
                                    "edit" -> selectedVideoUri?.let { submitVideoEdit(context, prompt, it) } ?: ("Önce düzenlenecek videoyu seç." to "")
                                    "upscale" -> selectedVideoUri?.let { submitVideoUpscale(context, it, upscaleResolution) } ?: ("Önce kalite yükseltilecek videoyu seç." to "")
                                    "character" -> if (selectedImageUri != null && selectedVideoUri != null) submitCharacterAnimation(context, selectedImageUri!!, selectedVideoUri!!, ratio) else ("Karakter fotoğrafı ve hareket videosu seçmelisin." to "")
                                    else -> submitGeneration(context, prompt, duration, ratio, selectedImageUri)
                                }
                                status = result.first
                                taskId = result.second
                                if (taskId.isNotBlank()) {
                                    val saved = saveVideoHistory(context, taskId, prompt, duration, ratio)
                                    if (saved) {
                                        historyRefresh++
                                        status = "Video kuyruğa alındı. Üretim durumu kontrol ediliyor…"
                                    } else {
                                        status = "Video görevi alındı ancak geçmişe kaydedilemedi. İşlem güvenli şekilde izlenemiyor."
                                    }
                                    for (attempt in 0 until 120) {
                                        delay(5000)
                                        val task = checkGeneration(context, taskId)
                                        status = task.message
                                        if (task.videoUrl.isNotBlank()) videoUrl = task.videoUrl
                                        if (task.finished) {
                                            if (task.videoUrl.isNotBlank()) updateVideoHistory(context, taskId, "SUCCEEDED", task.videoUrl)
                                            historyRefresh++
                                            break
                                        }
                                    }
                                }
                                if (loading && taskId.isNotBlank() && videoUrl.isBlank() && status.contains("kontrol")) status = "Üretim takibi zaman aşımına uğradı. Daha sonra tekrar kontrol edebilirsin."
                                loading = false
                            }
                        }, enabled = !loading && studioMode == "generate" && prompt.trim().length >= 10, modifier = Modifier.fillMaxWidth().height(54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF211721)), shape = RoundedCornerShape(13.dp)) {
                            Text(if (loading) "İŞLENİYOR…" else if (studioMode == "generate") "✦  VİDEOMU OLUŞTUR" else "YAKINDA KULLANIMA AÇILACAK", fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(status, color = Accent, fontSize = 12.sp, lineHeight = 18.sp)
                        if (taskId.isNotBlank()) Text("Üretim kodu: $taskId", color = Muted, fontSize = 10.sp)
                        if (videoUrl.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                                    context.startActivity(intent)
                                }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242D40))) {
                                    Text("VİDEOYU AÇ", color = White, fontWeight = FontWeight.Bold)
                                }
                                Button(onClick = { downloadFotoVideo(context, videoUrl, taskId) }, modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                                    Text("İNDİR", color = Color(0xFF211721), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                AccountWalletCard(historyRefresh, onAccountDeleted = onSignOut)
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

private suspend fun submitGeneration(context: android.content.Context, prompt: String, duration: Int, ratio: String, imageUri: Uri?): Pair<String, String> = withContext(Dispatchers.IO) {
    if (BuildConfig.API_BASE_URL.contains("YOUR-BACKEND-DOMAIN")) return@withContext "Video sunucusu henüz bağlanmadı." to ""
    try {
        val json = JSONObject().put("prompt", prompt).put("model", "gen4.5").put("duration", duration).put("ratio", ratio)
        if (imageUri != null) { val imageData = compressImageForRunway(context, imageUri) ?: return@withContext "Fotoğraf okunamadı." to ""; json.put("imageUri", imageData) }
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


private suspend fun submitVideoEdit(context: android.content.Context, prompt: String, uri: Uri): Pair<String, String> = withContext(Dispatchers.IO) {
    if (BuildConfig.API_BASE_URL.contains("YOUR-BACKEND-DOMAIN")) return@withContext "Video sunucusu henüz bağlanmadı." to ""
    try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext "Video dosyası okunamadı." to ""
        if (bytes.size > 3_500_000) return@withContext "Video 3,5 MB sınırını aşıyor. Daha küçük bir video seç." to ""
        val mime = context.contentResolver.getType(uri) ?: "video/mp4"
        if (!mime.startsWith("video/")) return@withContext "Geçerli bir video dosyası seç." to ""
        val videoData = "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        val json = JSONObject().put("prompt", prompt).put("videoUri", videoData)
        val token = SessionStore.read(context)?.optString("access_token").orEmpty()
        val request = Request.Builder().url(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/edit-video")
            .header("Authorization", "Bearer $token")
            .post(json.toString().toRequestBody("application/json".toMediaType())).build()
        OkHttpClient().newCall(request).execute().use { response ->
            val data = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful) return@withContext data.optString("message", "Video düzenleme başlatılamadı.") to ""
            "Video düzenleme isteği kabul edildi. Hazırlanıyor…" to data.optString("taskId")
        }
    } catch (_: Exception) { "Video sunucusuna bağlanılamadı. Dosyayı ve internet bağlantını kontrol et." to "" }
}


private suspend fun submitVideoUpscale(context: android.content.Context, uri: Uri, resolution: String): Pair<String, String> = withContext(Dispatchers.IO) {
    if (BuildConfig.API_BASE_URL.contains("YOUR-BACKEND-DOMAIN")) return@withContext "Video sunucusu henüz bağlanmadı." to ""
    try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext "Video dosyası okunamadı." to ""
        if (bytes.size > 3_500_000) return@withContext "Video 3,5 MB sınırını aşıyor. Daha küçük bir video seç." to ""
        val mime = context.contentResolver.getType(uri) ?: "video/mp4"
        if (!mime.startsWith("video/")) return@withContext "Geçerli bir video dosyası seç." to ""
        val videoData = "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        val json = JSONObject().put("videoUri", videoData).put("resolution", resolution)
        val token = SessionStore.read(context)?.optString("access_token").orEmpty()
        val request = Request.Builder().url(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/upscale-video")
            .header("Authorization", "Bearer $token")
            .post(json.toString().toRequestBody("application/json".toMediaType())).build()
        OkHttpClient().newCall(request).execute().use { response ->
            val data = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful) return@withContext data.optString("message", "Video kalite işlemi başlatılamadı.") to ""
            "Kalite yükseltme isteği kabul edildi. Hazırlanıyor…" to data.optString("taskId")
        }
    } catch (_: Exception) { "Video sunucusuna bağlanılamadı. Dosyayı ve internet bağlantını kontrol et." to "" }
}


private suspend fun submitCharacterAnimation(context: android.content.Context, characterUri: Uri, referenceUri: Uri, ratio: String): Pair<String, String> = withContext(Dispatchers.IO) {
    if (BuildConfig.API_BASE_URL.contains("YOUR-BACKEND-DOMAIN")) return@withContext "Video sunucusu henüz bağlanmadı." to ""
    try {
        val imageData = compressImageForRunway(context, characterUri) ?: return@withContext "Karakter fotoğrafı okunamadı." to ""
        val bytes = context.contentResolver.openInputStream(referenceUri)?.use { it.readBytes() }
            ?: return@withContext "Hareket videosu okunamadı." to ""
        if (bytes.size > 3_500_000) return@withContext "Hareket videosu 3,5 MB sınırını aşıyor." to ""
        val mime = context.contentResolver.getType(referenceUri) ?: "video/mp4"
        if (!mime.startsWith("video/")) return@withContext "Geçerli bir hareket videosu seç." to ""
        val videoData = "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        val json = JSONObject().put("characterUri", imageData).put("referenceUri", videoData).put("ratio", ratio)
        val token = SessionStore.read(context)?.optString("access_token").orEmpty()
        val request = Request.Builder().url(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/animate-character")
            .header("Authorization", "Bearer $token")
            .post(json.toString().toRequestBody("application/json".toMediaType())).build()
        OkHttpClient().newCall(request).execute().use { response ->
            val data = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful) return@withContext data.optString("message", "Karakter animasyonu başlatılamadı.") to ""
            "Karakter animasyonu isteği kabul edildi. Hazırlanıyor…" to data.optString("taskId")
        }
    } catch (_: Exception) { "Karakter animasyonu sunucusuna bağlanılamadı." to "" }
}

private fun downloadFotoVideo(context: android.content.Context, videoUrl: String, taskId: String) {
    if (!videoUrl.startsWith("https://")) return
    val fileName = "foto-foto-${taskId.take(12).replace(Regex("[^A-Za-z0-9_-]"), "")}.mp4"
    val request = DownloadManager.Request(Uri.parse(videoUrl))
        .setTitle("FOTO FOTO videon")
        .setDescription("Video indiriliyor…")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        .setMimeType("video/mp4")
    val manager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.enqueue(request)
}

private fun compressImageForRunway(context: android.content.Context, uri: Uri): String? {
    return try {
        val source = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        var bitmap = source
        val maxSide = 1800
        if (source.width > maxSide || source.height > maxSide) {
            val scale = minOf(maxSide.toFloat() / source.width, maxSide.toFloat() / source.height)
            bitmap = Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true)
            if (bitmap !== source) source.recycle()
        }
        var quality = 88
        var bytes: ByteArray
        do { val output = java.io.ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output); bytes = output.toByteArray(); quality -= 8 } while (bytes.size > 3500000 && quality >= 48)
        bitmap.recycle()
        if (bytes.size > 3500000) null else "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (_: Exception) { null }
}