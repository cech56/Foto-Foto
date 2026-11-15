package com.fotofoto.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
private val authClient = OkHttpClient()

@Composable
fun AuthGate(content: @Composable (onSignOut: () -> Unit) -> Unit) {
    val context = LocalContext.current
    var signedIn by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        signedIn = restoreFotoSession(context)
        checking = false
    }
    when {
        checking -> Box(Modifier.fillMaxSize().background(AuthBg), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AuthAccent)
        }
        signedIn -> content { SessionStore.clear(context); signedIn = false }
        else -> AuthScreen(onSignedIn = { signedIn = true })
    }
}

@Composable
private fun AuthScreen(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf("register") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var pendingVerification by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("FOTO FOTO hesabınla giriş yap veya ücretsiz hesap oluştur.") }
    val scope = rememberCoroutineScope()

    MaterialTheme(colorScheme = darkColorScheme(primary = AuthAccent, background = AuthBg, surface = AuthPanel)) {
        Box(Modifier.fillMaxSize().background(AuthBg).padding(22.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("FOTO FOTO", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Spacer(Modifier.height(8.dp))
                Text("Hayallerini videoya dönüştür.", color = Color.LightGray)
                Spacer(Modifier.height(25.dp))
                Card(colors = CardDefaults.cardColors(containerColor = AuthPanel), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Text(when { pendingVerification -> "E-posta doğrulama"; mode == "register" -> "Hesap oluştur"; mode == "reset" -> "Şifremi yenile"; else -> "Giriş yap" },
                            color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        if (pendingVerification) {
                            Text("E-posta adresine gelen kodu gir. Bağlantıya tıklamana gerek yok.", color = Color.LightGray, fontSize = 12.sp)
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(value = otp, onValueChange = { otp = it.filter(Char::isDigit).take(8) }, label = { Text("E-posta kodu") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        }
                        if (mode == "register" && !pendingVerification) {
                            OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Adın") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Spacer(Modifier.height(10.dp))
                        }
                        if (!pendingVerification) OutlinedTextField(value = email, onValueChange = { email = it.trim() }, label = { Text("E-posta") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        if (mode != "reset" && !pendingVerification) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Şifre (en az 8 karakter)") },
                                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    message = when {
                                        pendingVerification -> verifySignupOtp(context, email, otp)
                                        mode == "register" -> registerFoto(context, email, password, displayName).also {
                                            if (it == "OTP_SENT") { pendingVerification = true; otp = "" }
                                        }
                                        mode == "reset" -> resetFotoPassword(email)
                                        else -> loginFoto(context, email, password)
                                    }
                                    if (message == "Giriş başarılı.") onSignedIn()
                                    if (message == "OTP_SENT") message = "Doğrulama kodu e-posta adresine gönderildi. Kodu uygulamaya gir."
                                    busy = false
                                }
                            },
                            enabled = !busy && (if (pendingVerification) otp.length in 6..8 else email.contains("@") &&
                                (mode == "reset" || (password.length >= 8 && (mode != "register" || displayName.isNotBlank())))),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AuthAccent, contentColor = Color(0xFF211721))
                        ) {
                            Text(if (busy) "İŞLENİYOR…" else if (pendingVerification) "KODU DOĞRULA" else when(mode) {
                                "register" -> "ÜCRETSİZ HESAP OLUŞTUR"
                                "reset" -> "YENİLEME E-POSTASI GÖNDER"
                                else -> "GİRİŞ YAP"
                            }, fontWeight = FontWeight.Black)
                        }
                        if (mode == "login") {
                            TextButton(onClick = { mode = "reset"; message = "Şifre yenileme bağlantısı e-posta adresine gönderilecek." }, modifier = Modifier.fillMaxWidth()) {
                                Text("Şifremi unuttum", color = AuthAccent)
                            }
                        }
                        if (pendingVerification) TextButton(onClick = { scope.launch { busy = true; message = resendSignupOtp(email); busy = false } }, modifier = Modifier.fillMaxWidth()) { Text("KODU TEKRAR GÖNDER", color = AuthAccent) }
                        TextButton(onClick = {
                            pendingVerification = false
                            mode = if (mode == "login") "register" else "login"
                            message = if (mode == "register") "Hesap oluşturabilirsin." else "FOTO FOTO hesabına giriş yap."
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (mode == "register") "Zaten hesabın var mı? Giriş yap" else "Giriş ekranına / kayıt ekranına dön", color = AuthAccent)
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

private fun authRequest(path: String, body: JSONObject): Request = Request.Builder()
    .url(BuildConfig.SUPABASE_URL.trimEnd('/') + path)
    .addHeader("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
    .post(body.toString().toRequestBody("application/json".toMediaType()))
    .build()

private fun authError(data: JSONObject, fallback: String): String =
    data.optString("msg").ifBlank { data.optString("message").ifBlank { data.optString("error_description").ifBlank { fallback } } }

private suspend fun registerFoto(context: Context, email: String, password: String, displayName: String): String = withContext(Dispatchers.IO) {
    try {
        val body = JSONObject().put("email", email).put("password", password)
            .put("data", JSONObject().put("display_name", displayName))
        authClient.newCall(authRequest("/auth/v1/signup", body)).execute().use { response ->
            val data = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful) return@withContext authError(data, "Kayıt tamamlanamadı. Bilgilerini kontrol et.")
            val access = data.optString("access_token")
            val refresh = data.optString("refresh_token")
            if (access.isNotBlank() && refresh.isNotBlank()) {
                SessionStore.save(context, access, refresh)
                "Giriş başarılı."
            } else {
                "Hesap yanıtı alındı ancak oturum açılamadı. Giriş ekranından tekrar dene."
            }
        }
    } catch (_: Exception) { "Sunucuya bağlanılamadı. İnternet bağlantını kontrol et." }
}


private suspend fun verifySignupOtp(context: Context, email: String, token: String): String = withContext(Dispatchers.IO) {
    try {
        val body = JSONObject().put("email", email).put("token", token).put("type", "signup")
        authClient.newCall(authRequest("/auth/v1/verify", body)).execute().use { response ->
            val data = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful) return@withContext authError(data, "Kod doğrulanamadı. Kodu kontrol edip tekrar dene.")
            val access = data.optString("access_token")
            val refresh = data.optString("refresh_token")
            if (access.isBlank() || refresh.isBlank()) return@withContext "Oturum bilgisi alınamadı. Tekrar giriş yap."
            SessionStore.save(context, access, refresh)
            "Giriş başarılı."
        }
    } catch (_: Exception) { "Sunucuya bağlanılamadı. İnternet bağlantını kontrol et." }
}

private suspend fun resendSignupOtp(email: String): String = withContext(Dispatchers.IO) {
    try {
        val body = JSONObject().put("type", "signup").put("email", email)
        authClient.newCall(authRequest("/auth/v1/resend", body)).execute().use { response ->
            if (response.isSuccessful) "Yeni doğrulama kodu gönderildi."
            else authError(JSONObject(response.body?.string().orEmpty()), "Kod gönderilemedi.")
        }
    } catch (_: Exception) { "Sunucuya bağlanılamadı. İnternet bağlantını kontrol et." }
}

private suspend fun loginFoto(context: Context, email: String, password: String): String = withContext(Dispatchers.IO) {
    try {
        val body = JSONObject().put("email", email).put("password", password)
        authClient.newCall(authRequest("/auth/v1/token?grant_type=password", body)).execute().use { response ->
            val data = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful) return@withContext authError(data, "Giriş başarısız. E-posta ve şifreni kontrol et.")
            val access = data.optString("access_token")
            val refresh = data.optString("refresh_token")
            if (access.isBlank() || refresh.isBlank()) return@withContext "Oturum bilgisi alınamadı. Tekrar dene."
            SessionStore.save(context, access, refresh)
            "Giriş başarılı."
        }
    } catch (_: Exception) { "Sunucuya bağlanılamadı. İnternet bağlantını kontrol et." }
}

private suspend fun resetFotoPassword(email: String): String = withContext(Dispatchers.IO) {
    try {
        authClient.newCall(authRequest("/auth/v1/recover", JSONObject().put("email", email))).execute().use { response ->
            if (response.isSuccessful) "Şifre yenileme bağlantısı e-posta adresine gönderildi."
            else authError(JSONObject(response.body?.string().orEmpty()), "E-posta gönderilemedi.")
        }
    } catch (_: Exception) { "Sunucuya bağlanılamadı. İnternet bağlantını kontrol et." }
}

private suspend fun restoreFotoSession(context: Context): Boolean = withContext(Dispatchers.IO) {
    val saved = SessionStore.read(context) ?: return@withContext false
    val refresh = saved.optString("refresh_token")
    if (refresh.isBlank()) { SessionStore.clear(context); return@withContext false }
    try {
        val request = authRequest("/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", refresh))
        authClient.newCall(request).execute().use { response ->
            val data = JSONObject(response.body?.string().orEmpty())
            val access = data.optString("access_token")
            val newRefresh = data.optString("refresh_token")
            if (!response.isSuccessful || access.isBlank() || newRefresh.isBlank()) {
                SessionStore.clear(context)
                false
            } else {
                SessionStore.save(context, access, newRefresh)
                true
            }
        }
    } catch (_: Exception) { false }
}
