package com.fotofoto.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
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
    var mode by remember { mutableStateOf("login") }
    var language by remember { mutableStateOf(context.getSharedPreferences("foto_foto_settings", Context.MODE_PRIVATE).getString("language", "Türkçe") ?: "Türkçe") }
    val en = language == "English"
    val ar = language == "العربية"
    fun tr(t: String, e: String, a: String) = if (en) e else if (ar) a else t
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var pendingVerification by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("FOTO FOTO hesabına giriş yap.") }
    val scope = rememberCoroutineScope()

    MaterialTheme(colorScheme = darkColorScheme(primary = AuthAccent, background = AuthBg, surface = AuthPanel)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF241A35), AuthBg, Color(0xFF090C15)))).padding(22.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    listOf("Türkçe", "English", "العربية").forEach { lang ->
                        val short = if (lang == "Türkçe") "TR" else if (lang == "English") "EN" else "ع"
                        Text(short, modifier = Modifier.padding(start = 5.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (language == lang) Color(0xFF603A9B) else Color(0xFF171D30))
                            .border(1.dp, if (language == lang) Color(0xFFC49AFF) else Color(0xFF343B50), RoundedCornerShape(10.dp))
                            .clickable { language = lang; context.getSharedPreferences("foto_foto_settings", Context.MODE_PRIVATE).edit().putString("language", lang).apply() }
                            .padding(horizontal = 13.dp, vertical = 9.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(28.dp))
                Box(Modifier.size(66.dp).background(Brush.linearGradient(listOf(Color(0xFF12D8FA), Color(0xFF8B35FF), Color(0xFFFF5AA8))), RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                    Text("▶", color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(12.dp))
                Text("FOTO FOTO", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Spacer(Modifier.height(7.dp))
                Text(tr("Hayallerini videoya dönüştür.", "Turn your imagination into video.", "حوّل خيالك إلى فيديو."), color = Color.LightGray, fontSize = 13.sp)
                Spacer(Modifier.height(25.dp))
                Card(colors = CardDefaults.cardColors(containerColor = AuthPanel), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Text(when { pendingVerification -> tr("E-posta doğrulama", "Email verification", "تأكيد البريد الإلكتروني"); mode == "register" -> tr("Hesap oluştur", "Create account", "إنشاء حساب"); mode == "reset" -> tr("Şifremi yenile", "Reset password", "إعادة تعيين كلمة المرور"); else -> tr("Tekrar hoş geldin!", "Welcome back!", "مرحباً بعودتك!") },
                            color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        if (pendingVerification) {
                            Text("E-posta adresine gelen kodu gir. Bağlantıya tıklamana gerek yok.", color = Color.LightGray, fontSize = 12.sp)
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(value = otp, onValueChange = { otp = it.filter(Char::isDigit).take(8) }, label = { Text(tr("E-posta kodu", "Email code", "رمز البريد الإلكتروني")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        }
                        if (mode == "register" && !pendingVerification) {
                            OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text(tr("Adın", "Your name", "اسمك")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Spacer(Modifier.height(10.dp))
                        }
                        if (!pendingVerification) OutlinedTextField(value = email, onValueChange = { email = it.trim() }, label = { Text(tr("E-posta", "Email", "البريد الإلكتروني")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        if (mode != "reset" && !pendingVerification) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text(tr("Şifre (en az 8 karakter)", "Password (at least 8 characters)", "كلمة المرور (8 أحرف على الأقل)")) },
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
                            Text(if (busy) tr("İŞLENİYOR…", "WORKING…", "جارٍ التنفيذ…") else if (pendingVerification) tr("KODU DOĞRULA", "VERIFY CODE", "تحقق من الرمز") else when(mode) {
                                "register" -> tr("ÜCRETSİZ HESAP OLUŞTUR", "CREATE FREE ACCOUNT", "إنشاء حساب مجاني")
                                "reset" -> tr("YENİLEME E-POSTASI GÖNDER", "SEND RESET EMAIL", "إرسال بريد إعادة التعيين")
                                else -> tr("GİRİŞ YAP", "SIGN IN", "تسجيل الدخول")
                            }, fontWeight = FontWeight.Black)
                        }
                        if (mode == "login") {
                            TextButton(onClick = { mode = "reset"; message = "Şifre yenileme bağlantısı e-posta adresine gönderilecek." }, modifier = Modifier.fillMaxWidth()) {
                                Text(tr("Şifremi unuttum", "Forgot password?", "نسيت كلمة المرور؟"), color = AuthAccent)
                            }
                        }
                        if (pendingVerification) TextButton(onClick = { scope.launch { busy = true; message = resendSignupOtp(email); busy = false } }, modifier = Modifier.fillMaxWidth()) { Text(tr("KODU TEKRAR GÖNDER", "RESEND CODE", "إعادة إرسال الرمز"), color = AuthAccent) }
                        TextButton(onClick = {
                            pendingVerification = false
                            mode = if (mode == "login") "register" else "login"
                            message = if (mode == "register") tr("Hesap oluşturabilirsin.", "You can create an account.", "يمكنك إنشاء حساب.") else tr("FOTO FOTO hesabına giriş yap.", "Sign in to your FOTO FOTO account.", "سجّل الدخول إلى حسابك في FOTO FOTO.")
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (mode == "register") tr("Zaten hesabın var mı? Giriş yap", "Already have an account? Sign in", "لديك حساب؟ سجّل الدخول") else tr("Hesabın yok mu? Ücretsiz kayıt ol", "New here? Create a free account", "ليس لديك حساب؟ أنشئ حساباً مجانياً"), color = AuthAccent)
                        }
                        Text(message, color = Color.LightGray, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(tr("Güvenli hesap · FOTO FOTO", "Secure account · FOTO FOTO", "حساب آمن · FOTO FOTO"), color = Color.Gray, fontSize = 11.sp)
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
