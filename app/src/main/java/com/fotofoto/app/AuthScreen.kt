package com.fotofoto.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

private val AuthAccent = Color(0xFFFF795E)
private val AuthBg = Color(0xFF0B0E18)
private val AuthPanel = Color(0xFF151B2B)

@Composable
fun AuthGate(content: @Composable () -> Unit) {
    var signedIn by remember { mutableStateOf(false) }
    if (signedIn) {
        content()
    } else {
        AuthScreen(onSignedIn = { signedIn = true })
    }
}

@Composable
private fun AuthScreen(onSignedIn: () -> Unit) {
    var isRegister by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("FOTO FOTO hesabınla giriş yap veya ücretsiz hesap oluştur.") }
    val scope = rememberCoroutineScope()

    MaterialTheme(colorScheme = darkColorScheme(primary = AuthAccent, background = AuthBg, surface = AuthPanel)) {
        Box(Modifier.fillMaxSize().background(AuthBg).padding(22.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("FOTO FOTO", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Spacer(Modifier.height(8.dp))
                Text("Hayallerini videoya dönüştür.", color = Color.LightGray)
                Spacer(Modifier.height(25.dp))
                Card(colors = CardDefaults.cardColors(containerColor = AuthPanel), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Text(if (isRegister) "Hesap oluştur" else "Giriş yap", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        if (isRegister) {
                            OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Adın") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Spacer(Modifier.height(10.dp))
                        }
                        OutlinedTextField(value = email, onValueChange = { email = it.trim() }, label = { Text("E-posta") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Şifre (en az 8 karakter)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    message = if (isRegister) createFotoAccount(email, password, displayName) else loginFotoAccount(email, password).first
                                    val success = message == "Giriş başarılı."
                                    if (success) onSignedIn()
                                    busy = false
                                }
                            },
                            enabled = !busy && email.contains("@") && password.length >= 8 && (!isRegister || displayName.isNotBlank()),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AuthAccent, contentColor = Color(0xFF211721))
                        ) { Text(if (busy) "İŞLENİYOR…" else if (isRegister) "ÜCRETSİZ HESAP OLUŞTUR" else "GİRİŞ YAP", fontWeight = FontWeight.Black) }
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { isRegister = !isRegister; message = if (isRegister) "Hesap oluşturabilirsin." else "FOTO FOTO hesabına giriş yap." }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (isRegister) "Zaten hesabın var mı? Giriş yap" else "Hesabın yok mu? Kayıt ol", color = AuthAccent)
                        }
                        Text(message, color = Color.LightGray, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Güvenli hesap · FOTO FOTO", color = Color.Gray, fontSize = 11.sp)
            }
        }
    }
}

private suspend fun createFotoAccount(email: String, password: String, displayName: String): String = withContext(Dispatchers.IO) {
    if (BuildConfig.SUPABASE_PUBLISHABLE_KEY.isBlank()) return@withContext "Supabase anahtarı yapılandırılmamış."
    try {
        val payload = JSONObject()
            .put("email", email)
            .put("password", password)
            .put("data", JSONObject().put("display_name", displayName))
        val request = Request.Builder()
            .url(BuildConfig.SUPABASE_URL.trimEnd('/') + "/auth/v1/signup")
            .addHeader("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        OkHttpClient().newCall(request).execute().use { response ->
            val data = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful) return@withContext data.optString("msg", data.optString("message", "Kayıt tamamlanamadı. Bilgilerini kontrol et."))
            if (data.optString("access_token").isNotBlank()) "Hesap oluşturuldu. Giriş başarılı."
            else "Hesabın oluşturuldu. E-posta doğrulama bağlantısını kontrol et, ardından giriş yap."
        }
    } catch (_: Exception) { "Sunucuya bağlanılamadı. İnternet bağlantını kontrol et." }
}

private suspend fun loginFotoAccount(email: String, password: String): Pair<String, String> = withContext(Dispatchers.IO) {
    try {
        val payload = JSONObject().put("email", email).put("password", password)
        val request = Request.Builder()
            .url(BuildConfig.SUPABASE_URL.trimEnd('/') + "/auth/v1/token?grant_type=password")
            .addHeader("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        OkHttpClient().newCall(request).execute().use { response ->
            val data = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful) data.optString("msg", data.optString("message", "Giriş başarısız. E-posta ve şifreni kontrol et.")) to ""
            else "Giriş başarılı." to data.optString("access_token")
        }
    } catch (_: Exception) { "Sunucuya bağlanılamadı." to "" }
}
