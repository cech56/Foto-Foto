package com.fotofoto.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

private val walletHttp = OkHttpClient()

data class CreditEntry(val amount: Long, val type: String, val description: String, val createdAt: String)
data class AccountSummary(
    val email: String,
    val displayName: String,
    val credits: Long,
    val entries: List<CreditEntry>
)

suspend fun loadAccountSummary(context: Context): AccountSummary? = withContext(Dispatchers.IO) {
    val session = SessionStore.read(context) ?: return@withContext null
    val token = session.optString("access_token")
    if (token.isBlank()) return@withContext null
    try {
        val userReq = Request.Builder()
            .url(BuildConfig.SUPABASE_URL.trimEnd('/') + "/auth/v1/user")
            .addHeader("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            .addHeader("Authorization", "Bearer $token")
            .get().build()
        val user = walletHttp.newCall(userReq).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            JSONObject(response.body?.string().orEmpty())
        }
        val id = user.optString("id")
        if (id.isBlank()) return@withContext null
        val walletReq = Request.Builder()
            .url(BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/wallets?select=balance_credits&user_id=eq.$id")
            .addHeader("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            .addHeader("Authorization", "Bearer $token")
            .get().build()
        val credits = walletHttp.newCall(walletReq).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val rows = JSONArray(response.body?.string().orEmpty())
            if (rows.length() > 0) rows.getJSONObject(0).optLong("balance_credits", 0L) else 0L
        }
        val ledgerReq = Request.Builder()
            .url(BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/credit_ledger?select=amount_credits,entry_type,description,created_at&user_id=eq.$id&order=created_at.desc&limit=8")
            .addHeader("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            .addHeader("Authorization", "Bearer $token")
            .get().build()
        val entries = walletHttp.newCall(ledgerReq).execute().use { response ->
            if (!response.isSuccessful) emptyList()
            else {
                val rows = JSONArray(response.body?.string().orEmpty())
                (0 until rows.length()).map { index ->
                    val row = rows.getJSONObject(index)
                    CreditEntry(
                        row.optLong("amount_credits"),
                        row.optString("entry_type"),
                        row.optString("description"),
                        row.optString("created_at")
                    )
                }
            }
        }
        val metadata = user.optJSONObject("user_metadata") ?: JSONObject()
        AccountSummary(user.optString("email"), metadata.optString("display_name"), credits, entries)
    } catch (_: Exception) { null }
}

@Composable
fun AccountWalletCard(onRefresh: Int = 0) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var summary by remember { mutableStateOf<AccountSummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(onRefresh) }
    LaunchedEffect(onRefresh, refreshKey) {
        loading = true
        summary = loadAccountSummary(context)
        loading = false
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151B2B)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text("HESABIM & KREDİ CÜZDANIM", color = Color(0xFF9AA3B6), fontSize = 10.sp)
            Spacer(Modifier.height(8.dp))
            Text(summary?.displayName?.takeIf { it.isNotBlank() } ?: "FOTO FOTO kullanıcısı", color = Color.White, fontSize = 19.sp)
            Text(summary?.email ?: if (loading) "Hesap bilgisi yükleniyor…" else "Hesap bilgisi alınamadı. İnternetini kontrol et.", color = Color(0xFF9AA3B6), fontSize = 11.sp)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("KREDİ BAKİYESİ", color = Color(0xFF9AA3B6), fontSize = 9.sp)
                    Text(if (loading) "…" else summary?.let { "${it.credits} kredi" } ?: "—", color = Color(0xFFFF795E), fontSize = 24.sp)
                    Text("1 kredi = 1 TL", color = Color(0xFF9AA3B6), fontSize = 10.sp)
                }
                TextButton(onClick = { refreshKey++ }, enabled = !loading) { Text(if (loading) "YÜKLENİYOR" else "YENİLE", color = Color(0xFFFF795E)) }
            }
            Text("Gen-4.5 fiyatı: 5 sn = 59 kredi · 10 sn = 119 kredi. 1 kredi = 1 TL.", color = Color(0xFF9AA3B6), fontSize = 10.sp)
            Spacer(Modifier.height(12.dp))
            Text("SON KREDİ HAREKETLERİ", color = Color(0xFFFF795E), fontSize = 10.sp)
            Spacer(Modifier.height(6.dp))
            when {
                loading -> Text("Hareketler yükleniyor…", color = Color(0xFF9AA3B6), fontSize = 11.sp)
                summary == null -> Text("Kredi hareketleri alınamadı.", color = Color(0xFF9AA3B6), fontSize = 11.sp)
                summary!!.entries.isEmpty() -> Text("Henüz kredi hareketi yok.", color = Color(0xFF9AA3B6), fontSize = 11.sp)
                else -> summary!!.entries.forEach { entry ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.description.ifBlank { entry.type }, color = Color.White, fontSize = 11.sp, maxLines = 2)
                            Text(entry.type.uppercase(), color = Color(0xFF9AA3B6), fontSize = 9.sp)
                        }
                        Text(
                            (if (entry.amount > 0) "+" else "") + entry.amount + " kredi",
                            color = if (entry.amount > 0) Color(0xFF8BC6A0) else Color(0xFFFF795E),
                            fontSize = 11.sp
                        )
                    }
                }
            }
            Text("Kredi yükleme ve ödeme henüz etkin değil.", color = Color(0xFF9AA3B6), fontSize = 10.sp)
        }
    }
}
