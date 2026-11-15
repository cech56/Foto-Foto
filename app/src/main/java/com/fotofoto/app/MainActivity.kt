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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
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
                Spacer(Modifier.height(30.dp))
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
                HomeAiShowcase(appLanguage) { selectedTab = "create" }
                Spacer(Modifier.height(18.dp))
                Card(Modifier.fillMaxWidth().border(1.dp, Color(0xFF51406F), RoundedCornerShape(22.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF171D30)), shape = RoundedCornerShape(22.dp)) {
                    Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("HAYALİNİ VİDEOYA DÖNÜŞTÜR", "TURN YOUR IDEA INTO VIDEO", "حوّل فكرتك إلى فيديو"), color = Color(0xFFFFA17D), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text(tr("Bir fotoğraf seç veya sahneni tarif et.", "Choose a photo or describe your scene.", "اختر صورة أو صف مشهدك."), color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Button(onClick = { selectedTab = "create" }, colors = ButtonDefaults.buttonColors(containerColor = Accent), shape = RoundedCornerShape(14.dp)) {
                            Text(tr("BAŞLA", "START", "ابدأ"), color = Color(0xFF211721), fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(tr("FOTO FOTO İLE NELER YAPABİLİRSİN?", "WHAT CAN YOU CREATE WITH FOTO FOTO?", "ماذا يمكنك أن تصنع مع FOTO FOTO؟"), color = Color(0xFFB99AFF), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.1.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    HomeFeatureTile("✦", tr("Fotoğrafı canlandır", "Animate a photo", "حرّك صورة"), tr("Tek kareden video", "Turn a still into video", "حوّل صورة إلى فيديو"), Color(0xFF5834A0), Modifier.weight(1f)) { selectedTab = "create" }
                    HomeFeatureTile("◈", tr("Sinematik sahne", "Cinematic scene", "مشهد سينمائي"), tr("Fikrini tarif et", "Describe your idea", "صف فكرتك"), Color(0xFFB64282), Modifier.weight(1f)) { selectedTab = "create" }
                }
                Spacer(Modifier.height(18.dp))
                Card(Modifier.fillMaxWidth().border(1.dp, Color(0xFF343B5C), RoundedCornerShape(22.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151B2B)), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(17.dp)) {
                        Text(tr("NASIL ÇALIŞIR?", "HOW IT WORKS", "كيف يعمل؟"), color = Color(0xFFFFA17D), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                        Spacer(Modifier.height(13.dp))
                        HomeStepRow("01", tr("Fotoğrafını ekle", "Add your photo", "أضف صورتك"), tr("Galerinden bir görsel seç.", "Choose an image from your gallery.", "اختر صورة من معرضك."))
                        HomeStepRow("02", tr("Hayalini anlat", "Describe your vision", "صف رؤيتك"), tr("Sahneyi ve kamera hareketini yaz.", "Describe the scene and camera movement.", "صف المشهد وحركة الكاميرا."))
                        HomeStepRow("03", tr("Videonu oluştur", "Create your video", "أنشئ الفيديو"), tr("Ayarlarını seç ve üretimi başlat.", "Choose your settings and start generation.", "اختر الإعدادات وابدأ الإنشاء."))
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { selectedTab = "create" }, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6E42B5)), shape = RoundedCornerShape(14.dp)) {
                            Text(tr("YARATIM STÜDYOSUNA GİT  →", "OPEN CREATION STUDIO  →", "انتقل إلى استوديو الإنشاء  ←"), color = White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                        }
                    }
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
                            placeholder = { Text(when (studioMode) { "edit" -> tr("Örnek: Arka planı gün batımı sahiline dönüştür, kişiyi ve kamera hareketini koru…", "Example: Change the background to a sunset beach while keeping the person and camera movement…", "مثال: حوّل الخلفية إلى شاطئ عند الغروب مع الحفاظ على الشخص وحركة الكاميرا…"); "upscale" -> tr("İsteğe bağlı: Doğal detayları koru, görüntüyü netleştir…", "Optional: Preserve natural details and sharpen the image…", "اختياري: حافظ على التفاصيل الطبيعية وحسّن وضوح الصورة…"); "character" -> tr("Karakterinin nasıl hareket etmesini istediğini açıkla (isteğe bağlı)…", "Describe how your character should move (optional)…", "اشرح كيف تريد أن تتحرك الشخصية (اختياري)…"); else -> tr("Örnek: Gün batımında deniz kenarında ilerleyen kırmızı klasik bir araba, sinematik kamera hareketi…", "Example: A red classic car driving by the sea at sunset, cinematic camera movement…", "مثال: سيارة كلاسيكية حمراء تسير بجانب البحر عند الغروب، حركة كاميرا سينمائية…") }, color = Muted) },
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
                                Text(tr("Fotoğraf videonun ilk karesi olarak kullanılacak.", "Your photo will be used as the first frame of the video.", "سيتم استخدام صورتك كالإطار الأول للفيديو."), color = Muted, fontSize = 10.sp)
                            } else Text(tr("Kendi fotoğrafını veya canlandırmak istediğin görseli ekle.", "Add your photo or the image you want to animate.", "أضف صورتك أو الصورة التي تريد تحريكها."), color = Muted, fontSize = 10.sp)
                        } else {
                            if (studioMode == "character") {
                                Text(tr("KARAKTER FOTOĞRAFI", "CHARACTER PHOTO", "صورة الشخصية"), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(7.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Button(onClick = { imagePicker.launch("image/*") }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242D40))) {
                                        Text(if (selectedImageUri == null) tr("＋ KARAKTER FOTOĞRAFI", "＋ CHARACTER PHOTO", "＋ صورة الشخصية") else tr("FOTOĞRAFI DEĞİŞTİR", "CHANGE PHOTO", "تغيير الصورة"), color = White)
                                    }
                                    if (selectedImageUri != null) TextButton(onClick = { selectedImageUri = null }) { Text("KALDIR", color = Accent) }
                                }
                                Text(tr("Hareket videosunda yüzü net görünen tek kişi olmalı.", "The motion video should show one person with a clearly visible face.", "يجب أن يظهر في فيديو الحركة شخص واحد بوجه واضح."), color = Muted, fontSize = 10.sp)
                            }
                            Text(if (studioMode == "edit") tr("DÜZENLENECEK VİDEO", "VIDEO TO EDIT", "الفيديو المراد تعديله") else if (studioMode == "character") tr("HAREKET / PERFORMANS VİDEOSU", "MOTION / PERFORMANCE VIDEO", "فيديو الحركة / الأداء") else tr("KALİTESİ YÜKSELTİLECEK VİDEO", "VIDEO TO UPSCALE", "الفيديو المراد تحسينه"), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(onClick = { videoPicker.launch("video/*") }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242D40))) {
                                    Text(if (selectedVideoUri == null) tr("＋ VİDEO SEÇ", "＋ SELECT VIDEO", "＋ اختر فيديو") else tr("VİDEOYU DEĞİŞTİR", "CHANGE VIDEO", "تغيير الفيديو"), color = White)
                                }
                                if (selectedVideoUri != null) TextButton(onClick = { selectedVideoUri = null }) { Text(tr("KALDIR", "REMOVE", "إزالة"), color = Accent) }
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
                        EngineCard("RUNWAY", tr("Gen-4.5 · İlk aktif motor", "Gen-4.5 · Primary active engine", "Gen-4.5 · المحرك النشط الأساسي"), true, true, appLanguage)
                        Spacer(Modifier.height(8.dp))
                        EngineCard("KLING", tr("API bağlantısı sonraki aşamada", "API integration planned", "تكامل API مخطط له"), false, false, appLanguage)
                        Spacer(Modifier.height(8.dp))
                        EngineCard("VEO", tr("API bağlantısı sonraki aşamada", "API integration planned", "تكامل API مخطط له"), false, false, appLanguage)
                        Spacer(Modifier.height(18.dp))
                        Text(tr("VİDEO SÜRESİ", "VIDEO DURATION", "مدة الفيديو"), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.padding(top = 8.dp)) {
                            ChoiceButton(tr("5 saniye", "5 seconds", "٥ ثوانٍ"), duration == 5) { duration = 5 }
                            ChoiceButton(tr("10 saniye", "10 seconds", "١٠ ثوانٍ"), duration == 10) { duration = 10 }
                        }
                        if (studioMode == "generate") {
                            Text(
                                if (duration == 5) tr("Bu üretimin ücreti: 59 FOTO FOTO kredisi (59 TL)", "This video costs 59 FOTO FOTO credits (59 TRY)", "تكلفة هذا الفيديو 59 رصيداً (59 ليرة تركية)")
                                else tr("Bu üretimin ücreti: 119 FOTO FOTO kredisi (119 TL)", "This video costs 119 FOTO FOTO credits (119 TRY)", "تكلفة هذا الفيديو 119 رصيداً (119 ليرة تركية)"),
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
                        if (taskId.isNotBlank()) Text(tr("Üretim kodu: $taskId", "Generation ID: $taskId", "معرّف الإنشاء: $taskId"), color = Muted, fontSize = 10.sp)
                        if (videoUrl.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                                    context.startActivity(intent)
                                }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242D40))) {
                                    Text(tr("VİDEOYU AÇ", "OPEN VIDEO", "افتح الفيديو"), color = White, fontWeight = FontWeight.Bold)
                                }
                                Button(onClick = { downloadFotoVideo(context, videoUrl, taskId) }, modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                                    Text(tr("İNDİR", "DOWNLOAD", "تنزيل"), color = Color(0xFF211721), fontWeight = FontWeight.Bold)
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
                        Card(Modifier.fillMaxWidth().padding(bottom = 10.dp).border(1.dp, Color(0xFF303A55), RoundedCornerShape(18.dp)), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2032)), shape = RoundedCornerShape(18.dp)) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(listOf(Color(0xFF5535A6), Color(0xFF9B3F82), Color(0xFF176E9B), Color(0xFFB56A3C))[index]), contentAlignment = Alignment.Center) { Text(item.first, color = White, fontSize = 22.sp, fontWeight = FontWeight.Black) }
                                Spacer(Modifier.width(13.dp))
                                Column(Modifier.weight(1f)) { Text(item.second, color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp); Text(item.third, color = Muted, fontSize = 11.sp, lineHeight = 16.sp) }
                                if (index == 0) TextButton(onClick = { selectedTab = "create" }) { Text(tr("AÇ", "OPEN", "افتح"), color = Accent) } else Text(tr("YAKINDA", "COMING SOON", "قريباً"), color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                    Column(Modifier.weight(1f).padding(horizontal = 2.dp).clip(RoundedCornerShape(15.dp))
                        .background(if (selectedTab == item.first) Brush.verticalGradient(listOf(Color(0xFF59318A), Color(0xFF302342))) else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)))
                        .border(1.dp, if (selectedTab == item.first) Color(0xFF9B6BCE) else Color.Transparent, RoundedCornerShape(15.dp))
                        .clickable { selectedTab = item.first }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(item.second, color = if (selectedTab == item.first) Color(0xFFFFA17D) else Color(0xFF9AA3B6), fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Text(item.third, color = if (selectedTab == item.first) White else Color(0xFF9AA3B6), fontSize = 9.sp, fontWeight = if (selectedTab == item.first) FontWeight.ExtraBold else FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeFeatureTile(icon: String, title: String, subtitle: String, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick).border(1.dp, tint.copy(alpha = 0.65f), RoundedCornerShape(19.dp)),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.20f)), shape = RoundedCornerShape(19.dp)) {
        Column(Modifier.fillMaxWidth().padding(13.dp)) {
            Text(icon, color = tint.copy(alpha = 0.95f), fontSize = 26.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Text(title, color = White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Muted, fontSize = 9.sp, lineHeight = 13.sp)
        }
    }
}

@Composable
private fun HomeStepRow(number: String, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(Color(0xFF30234B), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Text(number, color = Color(0xFFD0A8FF), fontWeight = FontWeight.Black, fontSize = 11.sp)
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(title, color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun HomeAiShowcase(language: String, onCreate: () -> Unit) {
    val en = language == "English"
    val ar = language == "العربية"
    fun tr(t: String, e: String, a: String) = if (en) e else if (ar) a else t
    val transition = rememberInfiniteTransition(label = "ai-showcase")
    val pulse by transition.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val rotation by transition.animateFloat(-12f, 12f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Reverse), label = "rotation")
    Card(Modifier.fillMaxWidth().height(292.dp).border(1.dp, Color(0xFF7552B5), RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF251B59), Color(0xFF7133A8), Color(0xFFD84D91), Color(0xFFFF865C)))).padding(20.dp)) {
            Box(Modifier.align(Alignment.TopEnd).size(178.dp).graphicsLayer { rotationZ = rotation; scaleX = pulse; scaleY = pulse }.border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(48.dp)))
            Box(Modifier.align(Alignment.TopEnd).padding(top = 25.dp, end = 25.dp).size(128.dp).graphicsLayer { rotationZ = -rotation }.border(1.dp, Color.White.copy(alpha = 0.42f), RoundedCornerShape(38.dp)))
            Column(Modifier.align(Alignment.CenterStart)) {
                Text(tr("YAPAY ZEKÂ YARATICI STÜDYOSU", "AI CREATIVE STUDIO", "استوديو الإبداع بالذكاء الاصطناعي"), color = Color.White.copy(alpha = 0.82f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Spacer(Modifier.height(13.dp))
                Text(tr("Hayal et.\nBiz canlandıralım.", "Imagine it.\nWe'll bring it to life.", "تخيّل.\nونحن نُحييه."), color = White, fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                Text(tr("Fotoğraflarına hareket, fikirlerine hayat ver.", "Add motion to photos. Bring ideas to life.", "أضف الحركة إلى صورك وأحيِ أفكارك."), color = White.copy(alpha = 0.88f), fontSize = 11.sp)
                Spacer(Modifier.height(17.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onCreate, colors = ButtonDefaults.buttonColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp), contentPadding = PaddingValues(horizontal = 17.dp, vertical = 10.dp)) {
                        Text(tr("✦  HEMEN OLUŞTUR", "✦  CREATE NOW", "✦  أنشئ الآن"), color = Color(0xFF48266F), fontWeight = FontWeight.Black, fontSize = 10.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("GEN-4.5  •  AI", color = White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("✦", Modifier.align(Alignment.TopEnd).padding(top = 46.dp, end = 48.dp).graphicsLayer { scaleX = pulse; scaleY = pulse; rotationZ = rotation }, color = White, fontSize = 55.sp)
            Text(tr("FOTOĞRAFINI CANLANDIR", "ANIMATE YOUR PHOTO", "حرّك صورتك"), Modifier.align(Alignment.BottomEnd), color = White.copy(alpha = 0.75f), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun EngineCard(name: String, detail: String, active: Boolean, selected: Boolean, language: String) {
    Row(Modifier.fillMaxWidth().background(if (selected) Color(0xFF30202B) else Color(0xFF101625), RoundedCornerShape(12.dp))
        .border(1.dp, if (selected) Accent else Color(0xFF30384A), RoundedCornerShape(12.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (active) White else Muted)
            Text(detail, color = Muted, fontSize = 10.sp)
        }
        Text(if (active) if (language == "English") "ACTIVE" else if (language == "العربية") "نشط" else "AKTİF" else if (language == "English") "COMING SOON" else if (language == "العربية") "قريباً" else "YAKINDA", color = if (active) Accent else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RowScope.ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f).heightIn(min = 44.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) Color(0xFF603A9B) else Color(0xFF111827),
            contentColor = if (selected) Color.White else Color(0xFFB9C2D3)
        ),
        border = androidx.compose.foundation.BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) Color(0xFFCB9CFF) else Color(0xFF394154)),
        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 7.dp), shape = RoundedCornerShape(13.dp)) {
        Text(label, fontSize = 10.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold, maxLines = 1)
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