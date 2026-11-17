package com.fotofoto.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val iyzicoHttp = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .callTimeout(40, TimeUnit.SECONDS)
    .build()

private data class IyzicoCreditPack(
    val id: String,
    val title: String,
    val description: String,
    val priceMinor: Long,
    val currency: String,
    val credits: Long
)

private data class IyzicoApiResult<T>(val value: T? = null, val error: String? = null)

private suspend fun loadIyzicoPacks(context: android.content.Context): IyzicoApiResult<List<IyzicoCreditPack>> = withContext(Dispatchers.IO) {
    try {
        val token = SessionStore.read(context)?.optString("access_token").orEmpty()
        if (token.isBlank()) return@withContext IyzicoApiResult(error = "Oturum bulunamadı. Tekrar giriş yap.")
        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/catalog")
            .header("Authorization", "Bearer $token")
            .get().build()
        iyzicoHttp.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)
            if (!response.isSuccessful) return@withContext IyzicoApiResult(error = json.optString("message").ifBlank { "Paketler alınamadı (HTTP ${response.code})." })
            val array = json.optJSONArray("products") ?: JSONArray()
            val packs = (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                IyzicoCreditPack(
                    item.optString("product_id"),
                    item.optString("title"),
                    item.optString("description"),
                    item.optLong("price_minor"),
                    item.optString("currency", "TRY"),
                    item.optLong("credits")
                )
            }
            IyzicoApiResult(value = packs)
        }
    } catch (e: Exception) {
        IyzicoApiResult(error = "Paketler yüklenemedi: ${(e.message ?: "Bağlantı hatası").take(100)}")
    }
}

private suspend fun createIyzicoCheckout(
    context: android.content.Context,
    packId: String,
    name: String,
    surname: String,
    identityNumber: String,
    phone: String,
    address: String,
    city: String,
    zipCode: String
): IyzicoApiResult<String> = withContext(Dispatchers.IO) {
    try {
        val token = SessionStore.read(context)?.optString("access_token").orEmpty()
        if (token.isBlank()) return@withContext IyzicoApiResult(error = "Oturum bulunamadı. Tekrar giriş yap.")
        val buyer = JSONObject()
            .put("name", name.trim())
            .put("surname", surname.trim())
            .put("identityNumber", identityNumber.filter(Char::isDigit))
            .put("gsmNumber", phone.trim())
            .put("address", address.trim())
            .put("city", city.trim())
            .put("zipCode", zipCode.trim())
        val payload = JSONObject().put("productId", packId).put("buyer", buyer)
        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/payments/iyzico/create")
            .header("Authorization", "Bearer $token")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        iyzicoHttp.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)
            if (!response.isSuccessful) return@withContext IyzicoApiResult(error = json.optString("message").ifBlank { "Ödeme başlatılamadı (HTTP ${response.code})." })
            val url = json.optString("paymentPageUrl")
            val parsed = Uri.parse(url)
            if (!url.startsWith("https://") || parsed.host !in setOf("api.iyzipay.com", "sandbox-api.iyzipay.com")) {
                return@withContext IyzicoApiResult(error = "iyzico güvenli ödeme bağlantısı geçersiz.")
            }
            IyzicoApiResult(value = url)
        }
    } catch (e: Exception) {
        IyzicoApiResult(error = "Ödeme başlatılamadı: ${(e.message ?: "Bağlantı hatası").take(100)}")
    }
}

@Composable
fun IyzicoTopUpCard(language: String = "Türkçe") {
    val en = language == "English"
    val ar = language == "العربية"
    fun tr(t: String, e: String, a: String) = if (en) e else if (ar) a else t
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var loadingPacks by remember { mutableStateOf(false) }
    var packs by remember { mutableStateOf<List<IyzicoCreditPack>>(emptyList()) }
    var selectedPack by remember { mutableStateOf<IyzicoCreditPack?>(null) }
    var name by remember { mutableStateOf("") }
    var surname by remember { mutableStateOf("") }
    var identityNumber by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var zipCode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var startingPayment by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF20283B)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(tr("KREDİ YÜKLE", "ADD CREDITS", "اشحن الرصيد"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(5.dp))
            Text(tr("Ödemeni iyzico'nun güvenli ödeme sayfasında tamamlarsın. Kredi, ödeme doğrulandıktan sonra otomatik yüklenir.", "Complete payment on iyzico's secure checkout page. Credits are added only after payment is verified.", "أكمل الدفع عبر صفحة iyzico الآمنة. تتم إضافة الرصيد بعد تأكيد الدفع."), color = Color(0xFFB6C0D1), fontSize = 10.sp, lineHeight = 15.sp)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    showDialog = true
                    error = ""
                    loadingPacks = true
                    scope.launch {
                        val result = loadIyzicoPacks(context)
                        packs = result.value.orEmpty()
                        error = result.error.orEmpty()
                        loadingPacks = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7651B8)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(tr("✦  KREDİ PAKETLERİNİ GÖR", "✦  VIEW CREDIT PACKS", "✦  عرض باقات الرصيد"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { if (!startingPayment) showDialog = false },
            title = { Text(tr("iyzico ile kredi yükle", "Top up with iyzico", "اشحن عبر iyzico")) },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                    Text(tr("Bir kredi paketi seç", "Choose a credit pack", "اختر باقة رصيد"), fontWeight = FontWeight.Bold, color = Color(0xFFB99AFF), fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    when {
                        loadingPacks -> Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
                        packs.isEmpty() -> Text(error.ifBlank { tr("Paketler henüz satışa açılmadı.", "Credit packs are not available yet.", "باقات الرصيد غير متاحة بعد.") }, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        else -> packs.forEach { pack ->
                            val selected = selectedPack?.id == pack.id
                            OutlinedButton(
                                onClick = { selectedPack = pack; error = "" },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) Color(0xFF302052) else Color.Transparent),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Color(0xFF9D7BD8) else Color(0xFF6B7280))
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(pack.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                    Text("${pack.credits} kredi  •  ${"%.2f".format(java.util.Locale("tr", "TR"), pack.priceMinor / 100.0)} TL", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                    if (pack.description.isNotBlank()) Text(pack.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(tr("Ödeme bilgileri", "Billing details", "بيانات الدفع"), fontWeight = FontWeight.Bold, color = Color(0xFFB99AFF), fontSize = 12.sp)
                    Text(tr("Bu bilgiler iyzico'ya ödeme sırasında iletilir; kart bilgilerini FOTO FOTO almaz.", "These details are sent to iyzico for checkout; FOTO FOTO never collects card details.", "يتم إرسال هذه البيانات إلى iyzico عند الدفع؛ ولا يجمع FOTO FOTO بيانات البطاقة."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(name, { name = it }, label = { Text(tr("Ad", "First name", "الاسم")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(surname, { surname = it }, label = { Text(tr("Soyad", "Last name", "اسم العائلة")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(identityNumber, { identityNumber = it.filter(Char::isDigit).take(11) }, label = { Text(tr("T.C. kimlik numarası", "National ID number", "رقم الهوية")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(phone, { phone = it }, label = { Text(tr("Telefon", "Phone", "الهاتف")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    OutlinedTextField(address, { address = it }, label = { Text(tr("Adres", "Address", "العنوان")) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(city, { city = it }, label = { Text(tr("Şehir", "City", "المدينة")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(zipCode, { zipCode = it }, label = { Text(tr("Posta kodu (isteğe bağlı)", "Postal code (optional)", "الرمز البريدي (اختياري)")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (error.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(error, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !startingPayment && !loadingPacks && selectedPack != null,
                    onClick = {
                        val pack = selectedPack ?: return@TextButton
                        if (name.isBlank() || surname.isBlank() || identityNumber.length != 11 || phone.filter(Char::isDigit).length !in 10..15 || address.isBlank() || city.isBlank()) {
                            error = tr("Lütfen zorunlu ödeme bilgilerini eksiksiz gir.", "Please complete all required billing details.", "يرجى إكمال جميع بيانات الدفع المطلوبة.")
                            return@TextButton
                        }
                        scope.launch {
                            startingPayment = true
                            error = ""
                            val result = createIyzicoCheckout(context, pack.id, name, surname, identityNumber, phone, address, city, zipCode)
                            if (result.value != null) {
                                showDialog = false
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.value)))
                                } catch (_: Exception) {
                                    error = tr("Ödeme sayfası açılamadı.", "Could not open the checkout page.", "تعذر فتح صفحة الدفع.")
                                    showDialog = true
                                }
                            } else {
                                error = result.error.orEmpty()
                            }
                            startingPayment = false
                        }
                    }
                ) { Text(if (startingPayment) tr("HAZIRLANIYOR…", "PREPARING…", "جارٍ التجهيز…") else tr("GÜVENLİ ÖDEMEYE GEÇ", "CONTINUE TO SECURE PAYMENT", "المتابعة للدفع الآمن")) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }, enabled = !startingPayment) { Text(tr("KAPAT", "CLOSE", "إغلاق")) }
            }
        )
    }
}
