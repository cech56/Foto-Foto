package com.fotofoto.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val walletHttp = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .callTimeout(25, TimeUnit.SECONDS)
    .build()

data class CreditEntry(val amount: Long, val type: String, val description: String, val createdAt: String)
data class AccountSummary(
    val email: String,
    val displayName: String,
    val credits: Long,
    val entries: List<CreditEntry>
)
data class AccountLoadResult(val summary: AccountSummary?, val error: String? = null)

private fun responseError(stage: String, code: Int, body: String): String {
    val detail = try {
        val json = JSONObject(body)
        json.optString("message").ifBlank {
            json.optString("msg").ifBlank { json.optString("error_description").ifBlank { json.optString("hint") } }
        }
    } catch (_: Exception) { "" }
    val safeDetail = detail.take(180)
    return "$stage başarısız (HTTP $code)" + if (safeDetail.isNotBlank()) ": $safeDetail" else "."
}

suspend fun loadAccountSummary(context: Context): AccountLoadResult = withContext(Dispatchers.IO) {
    val session = SessionStore.read(context)
        ?: return@withContext AccountLoadResult(null, "Oturum bulunamadı. Çıkış yapıp tekrar giriş yap.")
    val token = session.optString("access_token")
    if (token.isBlank()) return@withContext AccountLoadResult(null, "Oturum anahtarı eksik. Tekrar giriş yap.")
    try {
        val userReq = Request.Builder()
            .url(BuildConfig.SUPABASE_URL.trimEnd('/') + "/auth/v1/user")
            .addHeader("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            .addHeader("Authorization", "Bearer $token")
            .get().build()
        val user = walletHttp.newCall(userReq).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext AccountLoadResult(null, responseError("Kullanıcı doğrulaması", response.code, body))
            JSONObject(body)
        }
        val id = user.optString("id")
        if (id.isBlank()) return@withContext AccountLoadResult(null, "Supabase kullanıcı kimliği boş döndü. Tekrar giriş yap.")
        val walletReq = Request.Builder()
            .url(BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/wallets?select=balance_credits&user_id=eq.$id")
            .addHeader("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            .addHeader("Authorization", "Bearer $token")
            .get().build()
        val credits = walletHttp.newCall(walletReq).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext AccountLoadResult(null, responseError("Cüzdan sorgusu", response.code, body))
            val rows = JSONArray(body)
            if (rows.length() > 0) rows.getJSONObject(0).optLong("balance_credits", 0L) else 0L
        }
        val ledgerReq = Request.Builder()
            .url(BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/credit_ledger?select=amount_credits,entry_type,description,created_at&user_id=eq.$id&order=created_at.desc&limit=8")
            .addHeader("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            .addHeader("Authorization", "Bearer $token")
            .get().build()
        val entries = walletHttp.newCall(ledgerReq).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext AccountLoadResult(null, responseError("Kredi geçmişi sorgusu", response.code, body))
            val rows = JSONArray(body)
            (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                CreditEntry(row.optLong("amount_credits"), row.optString("entry_type"), row.optString("description"), row.optString("created_at"))
            }
        }
        val metadata = user.optJSONObject("user_metadata") ?: JSONObject()
        AccountLoadResult(AccountSummary(user.optString("email"), metadata.optString("display_name"), credits, entries))
    } catch (e: Exception) {
        AccountLoadResult(null, "Bağlantı hatası: " + (e.message ?: "Bilinmeyen ağ hatası").take(140))
    }
}

private suspend fun deleteFotoAccount(context: android.content.Context): String = withContext(Dispatchers.IO) {
    try {
        val token = SessionStore.read(context)?.optString("access_token").orEmpty()
        if (token.isBlank()) return@withContext "Oturum bulunamadı. Tekrar giriş yap."
        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/account")
            .addHeader("Authorization", "Bearer $token")
            .delete(ByteArray(0).toRequestBody())
            .build()
        walletHttp.newCall(request).execute().use { response ->
            val data = JSONObject(response.body?.string().orEmpty())
            if (response.isSuccessful && data.optBoolean("ok")) "Hesap silindi."
            else data.optString("message").ifBlank { "Hesap silinemedi (HTTP ${response.code})." }
        }
    } catch (e: Exception) {
        "Hesap silme isteği tamamlanamadı: " + (e.message ?: "Bağlantı hatası").take(120)
    }
}

@Composable
fun AccountWalletCard(onRefresh: Int = 0, language: String = "Türkçe", onAccountDeleted: () -> Unit = {}) {
    val en = language == "English"
    val ar = language == "العربية"
    fun tr(t: String, e: String, a: String) = if (en) e else if (ar) a else t
    val context = androidx.compose.ui.platform.LocalContext.current
    var result by remember { mutableStateOf<AccountLoadResult?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(onRefresh) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deleteMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(onRefresh, refreshKey) {
        loading = true
        result = loadAccountSummary(context)
        loading = false
    }
    val summary = result?.summary
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151B2B)), shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF2D3550), RoundedCornerShape(26.dp))) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("SENİN YARATICI ALANIN", "YOUR CREATIVE SPACE", "مساحتك الإبداعية"), color = Color(0xFFB99AFF), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("Merhaba, ${summary?.displayName?.takeIf { it.isNotBlank() } ?: if (en) "creative friend" else if (ar) "صديقي المبدع" else "yaratıcı dostumuz"}! ✨", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text(summary?.email ?: if (loading) tr("Hesap bilgisi yükleniyor…", "Loading account…", "جارٍ تحميل الحساب…") else (result?.error ?: tr("Hesap bilgisi alınamadı.", "Could not load account details.", "تعذر تحميل بيانات الحساب.")), color = Color(0xFF9AA3B6), fontSize = 10.sp)
                }
                Box(Modifier.size(54.dp).background(Brush.linearGradient(listOf(Color(0xFF7045B5), Color(0xFFE66D8D))), RoundedCornerShape(18.dp)), contentAlignment = androidx.compose.ui.Alignment.Center) { Text("✦", color = Color.White, fontSize = 28.sp) }
            }
            Spacer(Modifier.height(14.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF302052)), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF39266B), Color(0xFF8A3F83), Color(0xFFB45B59)))).padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("KREDİ CÜZDANIN", "YOUR CREDIT WALLET", "محفظة الرصيد"), color = Color.White.copy(alpha = 0.78f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(if (loading) "…" else summary?.let { "${it.credits} ${tr("kredi", "credits", "رصيد")}" } ?: "—", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black)
                        Text(tr("1 kredi = 1 TL", "1 credit = 1 TRY", "1 رصيد = 1 ليرة"), color = Color.White.copy(alpha = 0.78f), fontSize = 10.sp)
                    }
                    Text("✧", color = Color.White, fontSize = 34.sp)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(tr("Bakiye bilgisi hesabına özeldir.", "Your balance is private to your account.", "رصيدك خاص بحسابك."), color = Color(0xFF9AA3B6), fontSize = 9.sp)
                TextButton(onClick = { refreshKey++ }, enabled = !loading, contentPadding = PaddingValues(4.dp)) { Text(if (loading) tr("YÜKLENİYOR", "LOADING", "جارٍ التحميل") else "↻ YENİLE", color = Color(0xFFFF795E), fontSize = 10.sp) }
            }
            Text(tr("ÜRETİM ÜCRETLERİ", "GENERATION PRICES", "أسعار الإنشاء"), color = Color(0xFFB99AFF), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Text(tr("Her video için gereken kredi miktarı", "Credits required for each video", "الرصيد المطلوب لكل فيديو"), color = Color(0xFF9AA3B6), fontSize = 9.sp)
            Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f).background(Color(0xFF101726), RoundedCornerShape(12.dp)).padding(11.dp)) { Text(tr("5 SANİYE", "5 SECONDS", "٥ ثوانٍ"), color = Color(0xFF9AA3B6), fontSize = 9.sp); Text("59 kredi", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                Column(Modifier.weight(1f).background(Color(0xFF101726), RoundedCornerShape(12.dp)).padding(11.dp)) { Text(tr("10 SANİYE", "10 SECONDS", "١٠ ثوانٍ"), color = Color(0xFF9AA3B6), fontSize = 9.sp); Text("119 kredi", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
                        Spacer(Modifier.height(12.dp))
            Text(tr("SON KREDİ HAREKETLERİ", "RECENT CREDIT ACTIVITY", "آخر حركات الرصيد"), color = Color(0xFFB99AFF), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            Spacer(Modifier.height(6.dp))
            when {
                loading -> Text(tr("Hareketler yükleniyor…", "Loading activity…", "جارٍ تحميل الحركات…"), color = Color(0xFF9AA3B6), fontSize = 11.sp)
                summary == null -> Text(tr("Kredi hareketleri alınamadı.", "Could not load credit activity.", "تعذر تحميل حركات الرصيد."), color = Color(0xFF9AA3B6), fontSize = 11.sp)
                summary.entries.isEmpty() -> Text(tr("Henüz kredi hareketi yok.", "No credit activity yet.", "لا توجد حركات رصيد بعد."), color = Color(0xFF9AA3B6), fontSize = 11.sp)
                else -> summary.entries.forEach { entry ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.description.ifBlank { entry.type }, color = Color.White, fontSize = 11.sp, maxLines = 2)
                            Text(entry.type.uppercase(), color = Color(0xFF9AA3B6), fontSize = 9.sp)
                        }
                        Text((if (entry.amount > 0) "+" else "") + entry.amount + if (en) " credits" else if (ar) " رصيد" else " kredi",
                            color = if (entry.amount > 0) Color(0xFF8BC6A0) else Color(0xFFFF795E), fontSize = 11.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().background(Color(0xFF20283B), RoundedCornerShape(13.dp)).padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text("ⓘ", color = Color(0xFF65C9D8), fontSize = 18.sp); Spacer(Modifier.width(9.dp)); Text(tr("Kredi yükleme ve ödeme henüz etkin değil. Bu alan etkinleştiğinde buradan bilgi alacaksın.", "Credit top-ups and payments are not active yet. You will find updates here when they launch.", "شحن الرصيد والدفع غير متاحين بعد. ستجد التحديثات هنا عند توفرهما."), color = Color(0xFFB6C0D1), fontSize = 9.sp, lineHeight = 14.sp) }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFF30384A))
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { showDeleteConfirm = true }, enabled = !deleting) {
                Text(tr("HESABIMI KALICI OLARAK SİL", "PERMANENTLY DELETE MY ACCOUNT", "حذف حسابي نهائياً"), color = Color(0xFFFF8A80), fontSize = 10.sp)
            }
            if (deleteMessage.isNotBlank()) Text(deleteMessage, color = Color(0xFFFF8A80), fontSize = 11.sp)
        }
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteConfirm = false },
            title = { Text(tr("Hesabın kalıcı olarak silinsin mi?", "Permanently delete your account?", "هل تريد حذف حسابك نهائياً؟")) },
            text = { Text("FOTO FOTO hesabın ve hesaba bağlı profil, cüzdan ve video geçmişi verileri silinir. Bu işlem geri alınamaz.") },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        scope.launch {
                            deleting = true
                            deleteMessage = deleteFotoAccount(context)
                            deleting = false
                            if (deleteMessage == "Hesap silindi.") {
                                SessionStore.clear(context)
                                showDeleteConfirm = false
                                onAccountDeleted()
                            }
                        }
                    }
                ) { Text(if (deleting) "SİLİNİYOR…" else "EVET, HESABIMI SİL", color = Color(0xFFFF8A80)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }, enabled = !deleting) { Text(tr("VAZGEÇ", "CANCEL", "إلغاء")) }
            }
        )
    }
}
