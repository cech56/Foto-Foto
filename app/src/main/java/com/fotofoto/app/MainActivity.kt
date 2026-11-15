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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.draw.clip
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
        window.statusBarColor = android.graphics.Color.rgb(33, 24, 42)
        window.navigationBarColor = android.graphics.Color.rgb(11, 14, 24)
        window.decorView.systemUiVisibility = 0
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
    var selectedTab by remember { mutableStateOf("home") }
    var settingsOpen by remember { mutableStateOf(false) }
    var appLanguage by remember { mutableStateOf(context.getSharedPreferences("foto_foto_settings", android.content.Context.MODE_PRIVATE).getString("language", "Türkçe") ?: "Türkçe") }
    val en = appLanguage == "English"
    val ar = appLanguage == "العربية"
    fun tr(t: String, e: String, a: String) = if (en) e else if (ar) a else t

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Panel, onPrimary = Color(0xFF211721), onBackground = White, onSurface = White)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF21182A), Bg, Bg)))) {
            if (settingsOpen) {
                AlertDialog(
                    onDismissRequest = { settingsOpen = false },
                    containerColor = Color(0xFF1B2033),
                    title = { Text(if (appLanguage == "English") "Settings" else if (appLanguage == "العربية") "الإعدادات" else "Ayarlar", color = White, fontWeight = FontWeight.Black) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(if (appLanguage == "English") "App language" else if (appLanguage == "العربية") "لغة التطبيق" else "Uygulama dili", color = Muted, fontWeight = FontWeight.Bold)
                            listOf("Türkçe", "English", "العربية").forEach { language ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (appLanguage == language) Color(0xFF342653) else Color(0xFF111827)).clickable {
                                    appLanguage = language
                                    context.getSharedPreferences("foto_foto_settings", android.content.Context.MODE_PRIVATE).edit().putString("language", language).apply()
                                }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(language, color = White, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                    if (appLanguage == language) Text("✓", color = Color(0xFF45D6E8), fontWeight = FontWeight.Black)
                                }
                            }
                            Text(if (appLanguage == "English") "Your language preference is saved on this device." else if (appLanguage == "العربية") "يتم حفظ اختيارك على هذا الجهاز." else "Dil tercihin bu cihaza kaydedilir.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    },
                    confirmButton = { TextButton(onClick = { settingsOpen = false }) { Text(if (appLanguage == "English") "Done" else if (appLanguage == "العربية") "تم" else "Tamam", color = Accent) } }
                )
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 92.dp)) {
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(
                        Brush.linearGradient(listOf(Color(0xFF12D8FA), Color(0xFF8B35FF), Color(0xFFFF5AA8)))
                    ), contentAlignment = Alignment.Center) {
                        Text("▶", color = White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("FOTO FOTO", color = White, fontSize = 19.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp)
                        Text(if (appLanguage == "English") "AI VIDEO STUDIO" else if (appLanguage == "العربية") "استوديو فيديو ذكي" else "AI VIDEO STÜDYOSU", color = Muted, fontSize = 9.sp, letterSpacing = 2.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { settingsOpen = true }) {
                        Text("⚙", color = White, fontSize = 23.sp)
                    }
                    TextButton(onClick = onSignOut) { Text(if (appLanguage == "English") "SIGN OUT" else if (appLanguage == "العربية") "خروج" else "ÇIKIŞ", color = Muted, fontSize = 10.sp) }
                }
                if (selectedTab == "home") {
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF302080), Color(0xFF6D2FC4), Color(0xFFE84E9B), Color(0xFFFF8A55))))
                        .padding(20.dp)
                ) {
                    Column {
                        Text(if (appLanguage == "English") "BRING YOUR IMAGINATION TO LIFE" else if (appLanguage == "العربية") "حوّل خيالك إلى واقع" else "HAYALİNİ HAREKETE GEÇİR", color = Color.White.copy(alpha = 0.78f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                        Spacer(Modifier.height(9.dp))
                        Text(if (appLanguage == "English") "Turn Your Photo\nInto a Cinematic Video" else if (appLanguage == "العربية") "حوّل صورتك إلى\nفيديو سينمائي" else "Fotoğrafından\nSinematik Video Üret", color = White, fontSize = 27.sp, lineHeight = 31.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(8.dp))
                        Text(if (appLanguage == "English") "Write an idea. Let AI do the rest." else if (appLanguage == "العربية") "اكتب فكرتك ودع الذكاء الاصطناعي يتولى الباقي." else "Bir fikir yaz. Gerisini yapay zekâya bırak.", color = White.copy(alpha = 0.86f), fontSize = 12.sp)
                        Spacer(Modifier.height(15.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.background(Color.White.copy(alpha = 0.20f), RoundedCornerShape(50)).padding(horizontal = 13.dp, vertical = 8.dp)) {
                                Text("✦  GEN-4.5 STÜDYOSU", color = White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.weight(1f))
                            Text("● CANLI MOTOR", color = White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    QuickStatCard("01", "Yarat", tr("Fikrini yaz", "Write your idea", "اكتب فكرتك"), Modifier.weight(1f), listOf(Color(0xFF4732A8), Color(0xFF8B43D8)))
                    QuickStatCard("02", "Canlandır", tr("Fotoğraf ekle", "Add a photo", "أضف صورة"), Modifier.weight(1f), listOf(Color(0xFFB64A73), Color(0xFFEE8758)))
                    QuickStatCard("03", "İndir", tr("Videonu al", "Get your video", "احصل على الفيديو"), Modifier.weight(1f), listOf(Color(0xFF176E9B), Color(0xFF3A9FBE)))
                }
                Spacer(Modifier.height(20.dp))
                }
                if (selectedTab == "create") {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17172D)), shape = RoundedCornerShape(26.dp), elevation = CardDefaults.cardElevation(defaultElevation = 10.dp), modifier = Modifier.border(1.dp, Color(0xFF34304D), RoundedCornerShape(26.dp))) {
                    Column(Modifier.padding(18.dp)) {
                        Text(tr("✦  YARATIM MASASI", "✦  CREATION STUDIO", "✦  استوديو الإنشاء"), color = Color(0xFFB99AFF), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            ChoiceButton(tr("OLUŞTUR", "CREATE", "إنشاء"), studioMode == "generate") { studioMode = "generate" }
                            ChoiceButton(tr("DÜZENLE", "EDIT", "تعديل"), studioMode == "edit") { studioMode = "edit" }
                            ChoiceButton(tr("KALİTE", "ENHANCE", "تحسين"), studioMode == "upscale") { studioMode = "upscale" }
                            ChoiceButton(tr("KARAKTER", "CHARACTER", "الشخصية"), studioMode == "character") { studioMode = "character" }
                        }
                        if (studioMode != "generate") {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                tr("Bu özellik şu anda hazırlık aşamasında. Güvenli kredi tarifesi ve dosya yükleme tamamlandığında açılacak. Şimdilik Runway Gen-4.5 ile video oluşturabilirsin.", "This feature is being prepared. It will open after secure pricing and file uploads are ready. For now, create videos with Runway Gen-4.5.", "هذه الميزة قيد الإعداد. ستتوفر بعد تجهيز الأسعار الآمنة ورفع الملفات. يمكنك حالياً إنشاء الفيديو باستخدام Runway Gen-4.5."),
                                color = Accent, fontSize = 11.sp, lineHeight = 16.sp
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(when (studioMode) { "edit" -> tr("Videonda ne değişsin?", "What would you like to change?", "ما الذي تريد تغييره؟"); "upscale" -> tr("Videonu hangi kaliteye yükseltelim?", "Choose your video quality", "اختر جودة الفيديو"); "character" -> tr("Karakterine hareket ver", "Bring your character to life", "امنح شخصيتك الحركة"); else -> tr("Ne hayal ediyorsun?", "What do you imagine?", "ماذا تتخيل؟") }, color = White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                        if (studioMode == "generate" && prompt.isBlank()) {
                            Text(if (appLanguage == "English") "GET INSPIRED" else if (appLanguage == "العربية") "استلهم فكرة" else "İLHAM AL", color = Color(0xFFB99AFF), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    Triple("🌅", if (appLanguage == "English") "Sunset" else if (appLanguage == "العربية") "غروب" else "Gün batımı", if (appLanguage == "English") "A cinematic sunset over the ocean, golden light, slow camera movement" else if (appLanguage == "العربية") "غروب سينمائي فوق المحيط، ضوء ذهبي وحركة كاميرا بطيئة" else "Okyanus üzerinde sinematik gün batımı, altın ışık, yavaş kamera hareketi"),
                                    Triple("🚀", if (appLanguage == "English") "Space" else if (appLanguage == "العربية") "فضاء" else "Uzay", if (appLanguage == "English") "An astronaut floating in a colorful nebula, cinematic wide shot" else if (appLanguage == "العربية") "رائد فضاء يطفو في سديم ملون، لقطة سينمائية واسعة" else "Renkli bir nebulada süzülen astronot, sinematik geniş çekim"),
                                    Triple("🏙️", if (appLanguage == "English") "City" else if (appLanguage == "العربية") "مدينة" else "Şehir", if (appLanguage == "English") "A futuristic city at night, neon reflections, dynamic camera movement" else if (appLanguage == "العربية") "مدينة مستقبلية ليلاً وانعكاسات نيون وحركة كاميرا ديناميكية" else "Gece fütüristik şehir, neon yansımalar, dinamik kamera hareketi")
                                ).forEach { idea ->
                                    AssistChip(onClick = { prompt = idea.third }, label = { Text(idea.second, fontSize = 9.sp) }, leadingIcon = { Text(idea.first, fontSize = 11.sp) }, colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF24213A), labelColor = White), modifier = Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
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
                            Text(tr(tr("REFERANS FOTOĞRAFI", "REFERENCE PHOTO", "الصورة المرجعية"), "REFERENCE IMAGE", "الصورة المرجعية"), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Button(onClick = { imagePicker.launch("image/*") }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242D40))) { Text(if (selectedImageUri == null) tr("＋ FOTOĞRAF EKLE", "＋ ADD PHOTO", "＋ أضف صورة") else tr("FOTOĞRAFI DEĞİŞTİR", "CHANGE PHOTO", "تغيير الصورة"), color = White) }
                                if (selectedImageUri != null) TextButton(onClick = {
                                selectedImageUri = null
                                if (ratio !in listOf("1280:720", "720:1280")) ratio = "1280:720"
                            }) { Text(tr("KALDIR", "REMOVE", "إزالة"), color = Accent) }
                            }
                            if (selectedImageUri != null) {
                                val previewBitmap = remember(selectedImageUri) { try { context.contentResolver.openInputStream(selectedImageUri!!)?.use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null } }
                                previewBitmap?.let { Image(bitmap = it.asImageBitmap(), contentDescription = "Referans fotoğrafı", modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp)) }
                                Text("Fotoğraf videonun ilk karesi olarak kullanılacak.", color = Muted, fontSize = 10.sp)
                            } else Text(tr("Kendi fotoğrafını veya canlandırmak istediğin görseli ekle.", "Add your photo or the image you want to animate.", "أضف صورتك أو الصورة التي تريد تحريكها."), color = Muted, fontSize = 10.sp)
                        } else {
                            if (studioMode == "character") {
                                Text(tr("KARAKTER FOTOĞRAFI", "CHARACTER PHOTO", "صورة الشخصية"), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                                    Text(if (selectedVideoUri == null) tr("＋ VİDEO SEÇ", "＋ SELECT VIDEO", "＋ اختر فيديو") else tr("VİDEOYU DEĞİŞTİR", "CHANGE VIDEO", "تغيير الفيديو"), color = White)
                                }
                                if (selectedVideoUri != null) TextButton(onClick = { selectedVideoUri = null }) { Text("KALDIR", color = Accent) }
                            }
                            Text(when (studioMode) { "edit" -> "Runway Aleph 2 · En fazla 30 saniye · İlk sürümde dosya boyutu 3,5 MB."; "character" -> "Act-Two · 3–30 saniyelik performans videosu · Tek kişi, yüz görünür olmalı."; else -> "Magnific Video Upscaler · 720p, 1K, 2K veya 4K · En fazla 30 saniye." }, color = Muted, fontSize = 10.sp)
                            if (studioMode == "upscale") {
                                Spacer(Modifier.height(10.dp))
                                Text(tr("HEDEF ÇÖZÜNÜRLÜK", "TARGET RESOLUTION", "الدقة المستهدفة"), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                                    listOf("720p", "1k", "2k", "4k").forEach { value ->
                                        ChoiceButton(value.uppercase(), upscaleResolution == value) { upscaleResolution = value }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(17.dp))
                        Text(tr("VİDEO MOTORU", "VIDEO ENGINE", "محرك الفيديو"), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(9.dp))
                        EngineCard("RUNWAY", "Gen-4.5 · İlk aktif motor", true, true)
                        Spacer(Modifier.height(8.dp))
                        EngineCard("KLING", "API bağlantısı sonraki aşamada", false, false)
                        Spacer(Modifier.height(8.dp))
                        EngineCard("VEO", "API bağlantısı sonraki aşamada", false, false)
                        Spacer(Modifier.height(18.dp))
                        Text(tr("VİDEO SÜRESİ", "VIDEO DURATION", "مدة الفيديو"), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.padding(top = 8.dp)) {
                            ChoiceButton(tr("5 saniye", "5 seconds", "٥ ثوانٍ"), duration == 5) { duration = 5 }
                            ChoiceButton(tr("10 saniye", "10 seconds", "١٠ ثوانٍ"), duration == 10) { duration = 10 }
                        }
                        if (studioMode == "generate") {
                            Text(
                                if (duration == 5) "Bu üretimin ücreti: 59 FOTO FOTO kredisi (59 TL)"
                                else "Bu üretimin ücreti: 119 FOTO FOTO kredisi (119 TL)",
                                color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 9.dp)
                            )
                            Text(tr("Üretim başarısız olursa ayrılan krediler iade edilir.", "Reserved credits are refunded if generation fails.", "تُعاد الأرصدة المحجوزة إذا فشل الإنشاء."), color = Muted, fontSize = 10.sp)
                        }
                        Spacer(Modifier.height(15.dp))
                        Text(tr("VİDEO FORMATI", "VIDEO FORMAT", "تنسيق الفيديو"), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                            Text(if (loading) tr("İŞLENİYOR…", "PROCESSING…", "جارٍ المعالجة…") else if (studioMode == "generate") tr("✦  VİDEOMU OLUŞTUR", "✦  CREATE MY VIDEO", "✦  أنشئ الفيديو") else tr("YAKINDA KULLANIMA AÇILACAK", "COMING SOON", "قريباً"), fontWeight = FontWeight.Black)
                        }
                        if (loading) {
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                                color = Color(0xFFB99AFF),
                                trackColor = Color(0xFF34304A)
                            )
                            Text(if (appLanguage == "English") "Your creation is being prepared. You can follow the status here." else if (appLanguage == "العربية") "يجري تجهيز الفيديو الخاص بك. يمكنك متابعة الحالة هنا." else "Videon hazırlanıyor. Durumu buradan takip edebilirsin.", color = Muted, fontSize = 10.sp)
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
                }
                if (selectedTab == "account") {
                    Spacer(Modifier.height(18.dp))
                    AccountWalletCard(historyRefresh, appLanguage, onAccountDeleted = onSignOut)
                }
                if (selectedTab == "projects") {
                    Spacer(Modifier.height(18.dp))
                    VideoHistoryCard(historyRefresh, appLanguage)
                }
                if (selectedTab == "tools") {
                    Spacer(Modifier.height(22.dp))
                    Text(if (appLanguage == "English") "CREATIVE TOOLS" else if (appLanguage == "العربية") "أدوات إبداعية" else "YARATICI ARAÇLAR", color = Color(0xFFB99AFF), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(12.dp))
                    listOf(Triple("✦", if (appLanguage == "English") "Photo to video" else if (appLanguage == "العربية") "صورة إلى فيديو" else "Fotoğraftan video", if (appLanguage == "English") "Turn a still image into a cinematic scene." else if (appLanguage == "العربية") "حوّل صورة ثابتة إلى مشهد سينمائي." else "Tek bir görseli sinematik sahneye dönüştür."), Triple("◈", if (appLanguage == "English") "Video editing" else if (appLanguage == "العربية") "تحرير الفيديو" else "Video düzenleme", if (appLanguage == "English") "AI editing for existing videos is coming soon." else if (appLanguage == "العربية") "تحرير مقاطع الفيديو بالذكاء الاصطناعي قريباً." else "Mevcut videolar üzerinde yapay zekâ düzenlemeleri yakında."), Triple("⬆", if (appLanguage == "English") "Video upscaling" else if (appLanguage == "العربية") "تحسين جودة الفيديو" else "Video kalite artırma", if (appLanguage == "English") "Higher-resolution tools are in development." else if (appLanguage == "العربية") "أدوات الدقة الأعلى قيد التطوير." else "Daha yüksek çözünürlük araçları hazırlanıyor."), Triple("☺", if (appLanguage == "English") "Character animation" else if (appLanguage == "العربية") "تحريك الشخصيات" else "Karakter animasyonu", if (appLanguage == "English") "Animate a character with reference motion. Coming soon." else if (appLanguage == "العربية") "حرّك شخصيتك باستخدام حركة مرجعية. قريباً." else "Karakterini referans hareketlerle canlandırma yakında." )).forEachIndexed { index, item ->
                        Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2032)), shape = RoundedCornerShape(18.dp)) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(listOf(Color(0xFF5535A6), Color(0xFF9B3F82), Color(0xFF176E9B), Color(0xFFB56A3C))[index]), contentAlignment = Alignment.Center) { Text(item.first, color = White, fontSize = 22.sp, fontWeight = FontWeight.Black) }
                                Spacer(Modifier.width(13.dp))
                                Column(Modifier.weight(1f)) { Text(item.second, color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp); Text(item.third, color = Muted, fontSize = 11.sp, lineHeight = 16.sp) }
                                if (index == 0) TextButton(onClick = { selectedTab = "create" }) { Text("AÇ", color = Accent) } else Text("YAKINDA", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (selectedTab == "home") {
                Spacer(Modifier.height(16.dp))
                Spacer(Modifier.height(26.dp))
                Text(tr("FOTO FOTO  ·  FİKRİNİ VİDEOYA DÖNÜŞTÜR", "FOTO FOTO  ·  TURN YOUR IDEA INTO VIDEO", "FOTO FOTO · حوّل فكرتك إلى فيديو"), color = Muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(28.dp))
                }
            }
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().background(Color(0xF2171B2C)).padding(horizontal = 5.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                listOf(Triple("home", "⌂", if (appLanguage == "English") "Home" else if (appLanguage == "العربية") "الرئيسية" else "Ana Sayfa"), Triple("projects", "▤", if (appLanguage == "English") "Projects" else if (appLanguage == "العربية") "مشاريعي" else "Projelerim"), Triple("create", "＋", if (appLanguage == "English") "Create" else if (appLanguage == "العربية") "إنشاء" else "Oluştur"), Triple("tools", "✧", if (appLanguage == "English") "Tools" else if (appLanguage == "العربية") "الأدوات" else "Araçlar"), Triple("account", "◉", if (appLanguage == "English") "Account" else if (appLanguage == "العربية") "حسابي" else "Hesabım")).forEach { item ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { selectedTab = item.first }.padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(item.second, color = if (selectedTab == item.first) Color(0xFFFF8B72) else Muted, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Text(item.third, color = if (selectedTab == item.first) White else Muted, fontSize = 9.sp, fontWeight = if (selectedTab == item.first) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickStatCard(number: String, title: String, subtitle: String, modifier: Modifier, colors: List<Color>) {
    Column(
        modifier.clip(RoundedCornerShape(17.dp))
            .background(Brush.linearGradient(colors))
            .padding(12.dp)
    ) {
        Text(number, color = Color.White.copy(alpha = 0.72f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = Color.White.copy(alpha = 0.78f), fontSize = 9.sp)
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