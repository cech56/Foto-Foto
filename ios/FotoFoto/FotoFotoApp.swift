import SwiftUI
import PhotosUI
import UIKit
import Combine
import Security
import UniformTypeIdentifiers

private enum Config {
    static let api = URL(string: "https://foto-foto-api.onrender.com")!
    static let supabase = URL(string: "https://jccfdrtxbsnljfkswmog.supabase.co")!
    static let anonKey = "sb_publishable_pA1nlIxTD7-ea1Mo3XH3sQ_EYvC-Vtb"
}

struct AuthSession: Codable {
    let access_token: String
    let refresh_token: String
    let user: AuthUser?
}
struct AuthUser: Codable { let id: String; let email: String?; let user_metadata: [String: String]? }
struct WalletRow: Decodable { let balance_credits: Int }
struct LedgerRow: Decodable, Identifiable {
    var id: String { reference_id ?? UUID().uuidString }
    let amount_credits: Int
    let description: String?
    let entry_type: String?
    let reference_id: String?
}
struct GenerateResponse: Decodable { let taskId: String; let status: String? }
struct HistoryRow: Decodable, Identifiable {
    var id: String { provider_task_id }
    let provider_task_id: String
    let prompt: String
    let status: String
    let video_url: String?
}

@MainActor final class Store: ObservableObject {
    @Published var session: AuthSession?
    @Published var balance = 0
    @Published var ledger: [LedgerRow] = []
    @Published var history: [HistoryRow] = []
    @Published var busy = false
    @Published var message = ""
    @Published var language = UserDefaults.standard.string(forKey: "foto_language") ?? "Türkçe" {
        didSet { UserDefaults.standard.set(language, forKey: "foto_language") }
    }

    func tr(_ turkish: String, _ english: String, _ arabic: String) -> String {
        language == "English" ? english : (language == "العربية" ? arabic : turkish)
    }

    init() { session = KeychainSession.load() }

    func request<T: Decodable>(_ url: URL, method: String = "GET", body: Data? = nil, authenticated: Bool = true) async throws -> T {
        var req = URLRequest(url: url)
        req.httpMethod = method
        req.httpBody = body
        req.setValue(Config.anonKey, forHTTPHeaderField: "apikey")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if authenticated, let token = session?.access_token { req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        else { req.setValue("Bearer \(Config.anonKey)", forHTTPHeaderField: "Authorization") }
        let (data, response) = try await URLSession.shared.data(for: req)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            let detail = String(data: data, encoding: .utf8) ?? "İstek başarısız."
            throw NSError(domain: "FOTOFOTO", code: (response as? HTTPURLResponse)?.statusCode ?? -1, userInfo: [NSLocalizedDescriptionKey: detail])
        }
        return try JSONDecoder().decode(T.self, from: data)
    }

    func auth(_ endpoint: String, payload: [String: Any]) async throws {
        let url = URL(string: "/auth/v1/\(endpoint)", relativeTo: Config.supabase)!
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue(Config.anonKey, forHTTPHeaderField: "apikey")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: payload)
        let (data, response) = try await URLSession.shared.data(for: req)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw NSError(domain: "FOTOFOTO", code: (response as? HTTPURLResponse)?.statusCode ?? -1, userInfo: [NSLocalizedDescriptionKey: String(data: data, encoding: .utf8) ?? "Giriş başarısız."])
        }
        let decoded = try JSONDecoder().decode(AuthSession.self, from: data)
        session = decoded
        KeychainSession.save(decoded)
    }

    func signIn(email: String, password: String) async throws {
        try await auth("token?grant_type=password", payload: ["email": email, "password": password])
        await refreshAll()
    }
    func authRequest(_ endpoint: String, payload: [String: Any]) async throws -> Data {
        let url = URL(string: "/auth/v1/\(endpoint)", relativeTo: Config.supabase)!
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue(Config.anonKey, forHTTPHeaderField: "apikey")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: payload)
        let (data, response) = try await URLSession.shared.data(for: req)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            let body = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
            let detail = body?["msg"] as? String ?? body?["message"] as? String ?? body?["error_description"] as? String ?? "İstek başarısız."
            throw NSError(domain: "FOTOFOTO", code: (response as? HTTPURLResponse)?.statusCode ?? -1, userInfo: [NSLocalizedDescriptionKey: detail])
        }
        return data
    }

    func signUp(email: String, password: String, displayName: String) async throws {
        _ = try await authRequest("signup", payload: ["email": email, "password": password, "data": ["display_name": displayName]])
        message = "Doğrulama kodu e-posta adresine gönderildi."
    }

    func verifySignup(email: String, code: String) async throws {
        let data = try await authRequest("verify", payload: ["email": email, "token": code, "type": "signup"])
        let decoded = try JSONDecoder().decode(AuthSession.self, from: data)
        session = decoded
        KeychainSession.save(decoded)
        await refreshAll()
    }

    func resendSignupCode(email: String) async throws {
        _ = try await authRequest("resend", payload: ["email": email, "type": "signup"])
        message = "Yeni doğrulama kodu gönderildi."
    }

    func sendPasswordReset(email: String) async throws {
        _ = try await authRequest("recover", payload: ["email": email])
        message = "Şifre yenileme bağlantısı e-posta adresine gönderildi."
    }
    func deleteAccount() async throws {
        guard let token = session?.access_token else { throw NSError(domain: "FOTOFOTO", code: 401, userInfo: [NSLocalizedDescriptionKey: "Oturum bulunamadı."]) }
        var req = URLRequest(url: Config.api.appendingPathComponent("api/account"))
        req.httpMethod = "DELETE"
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let (data, response) = try await URLSession.shared.data(for: req)
        let http = response as? HTTPURLResponse
        let result = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]
        guard let http, (200..<300).contains(http.statusCode), result["ok"] as? Bool == true else {
            throw NSError(domain: "FOTOFOTO", code: http?.statusCode ?? -1, userInfo: [NSLocalizedDescriptionKey: result["message"] as? String ?? "Hesap silinemedi."])
        }
        signOut()
    }

    func signOut() { session = nil; balance = 0; history = []; ledger = []; KeychainSession.clear() }

    func refreshAll() async {
        guard session != nil else { return }
        do {
            let walletURL = URL(string: "/rest/v1/wallets", relativeTo: Config.supabase)!.appending(queryItems: [URLQueryItem(name: "select", value: "balance_credits"), URLQueryItem(name: "limit", value: "1")])
            let wallets: [WalletRow] = try await request(walletURL)
            balance = wallets.first?.balance_credits ?? 0
            let ledgerURL = URL(string: "/rest/v1/credit_ledger", relativeTo: Config.supabase)!.appending(queryItems: [URLQueryItem(name: "select", value: "amount_credits,description,entry_type,reference_id"), URLQueryItem(name: "order", value: "created_at.desc"), URLQueryItem(name: "limit", value: "10")])
            ledger = (try? await request(ledgerURL)) ?? []
            let historyURL = URL(string: "/rest/v1/video_generations", relativeTo: Config.supabase)!.appending(queryItems: [URLQueryItem(name: "select", value: "provider_task_id,prompt,status,video_url"), URLQueryItem(name: "order", value: "created_at.desc"), URLQueryItem(name: "limit", value: "20")])
            history = (try? await request(historyURL)) ?? []
        } catch { message = error.localizedDescription }
    }

    func saveHistory(taskId: String, prompt: String, seconds: Int, ratio: String) async {
        guard let userId = session?.user?.id else { return }
        let url = URL(string: "/rest/v1/video_generations", relativeTo: Config.supabase)!
        let payload: [String: Any] = ["user_id": userId, "provider_task_id": taskId, "prompt": prompt, "duration_seconds": seconds, "aspect_ratio": ratio, "status": "PENDING"]
        do {
            var req = URLRequest(url: url)
            req.httpMethod = "POST"
            req.setValue(Config.anonKey, forHTTPHeaderField: "apikey")
            req.setValue("Bearer \(session?.access_token ?? "")", forHTTPHeaderField: "Authorization")
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.setValue("return=minimal", forHTTPHeaderField: "Prefer")
            req.httpBody = try JSONSerialization.data(withJSONObject: payload)
            let (_, response) = try await URLSession.shared.data(for: req)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { return }
        } catch { message = "Video oluşturuldu ancak proje geçmişine kaydedilemedi: \(error.localizedDescription)" }
    }

    func refreshTask(_ taskId: String) async {
        guard let token = session?.access_token else { return }
        do {
            var req = URLRequest(url: Config.api.appendingPathComponent("api/tasks/\(taskId)"))
            req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
            let (data, response) = try await URLSession.shared.data(for: req)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { return }
            let task = try JSONSerialization.jsonObject(with: data) as? [String: Any] ?? [:]
            let status = (task["status"] as? String ?? "PENDING").uppercased()
            let output = task["output"] as? [String]
            let videoURL = output?.first(where: { $0.hasPrefix("https://") })
            var patch: [String: Any] = ["status": status, "updated_at": ISO8601DateFormatter().string(from: Date())]
            if let videoURL { patch["video_url"] = videoURL }
            let updateURL = URL(string: "/rest/v1/video_generations", relativeTo: Config.supabase)!.appending(queryItems: [URLQueryItem(name: "provider_task_id", value: "eq.\(taskId)")])
            var update = URLRequest(url: updateURL)
            update.httpMethod = "PATCH"
            update.setValue(Config.anonKey, forHTTPHeaderField: "apikey")
            update.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
            update.setValue("application/json", forHTTPHeaderField: "Content-Type")
            update.setValue("return=minimal", forHTTPHeaderField: "Prefer")
            update.httpBody = try JSONSerialization.data(withJSONObject: patch)
            _ = try await URLSession.shared.data(for: update)
        } catch { message = "Görev durumu yenilenemedi: \(error.localizedDescription)" }
    }


    private func submitTool(path: String, payload: [String: Any], prompt: String, ratio: String = "1280:720") async {
        guard let token = session?.access_token else { message = "Önce giriş yap."; return }
        busy = true
        defer { busy = false }
        do {
            var req = URLRequest(url: Config.api.appendingPathComponent(path))
            req.httpMethod = "POST"
            req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.httpBody = try JSONSerialization.data(withJSONObject: payload)
            let (data, response) = try await URLSession.shared.data(for: req)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
                let body = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
                throw NSError(domain: "FOTOFOTO", code: (response as? HTTPURLResponse)?.statusCode ?? -1, userInfo: [NSLocalizedDescriptionKey: body?["message"] as? String ?? String(data: data, encoding: .utf8) ?? "İşlem başlatılamadı."])
            }
            let result = try JSONDecoder().decode(GenerateResponse.self, from: data)
            message = "İstek kabul edildi. Hazırlanıyor… Görev: \(result.taskId)"
            await saveHistory(taskId: result.taskId, prompt: prompt, seconds: 5, ratio: ratio)
            await refreshAll()
        } catch { message = error.localizedDescription }
    }

    func editVideo(prompt: String, data: Data, mime: String) async {
        guard data.count <= 3_500_000 else { message = "Video 3,5 MB sınırını aşıyor. Daha küçük bir video seç."; return }
        guard mime.hasPrefix("video/") else { message = "Geçerli bir video dosyası seç."; return }
        let encoded = "data:\(mime);base64," + data.base64EncodedString()
        await submitTool(path: "api/edit-video", payload: ["prompt": prompt, "videoUri": encoded], prompt: "Video düzenleme: " + prompt)
    }

    func upscaleVideo(data: Data, mime: String, resolution: String) async {
        guard data.count <= 3_500_000 else { message = "Video 3,5 MB sınırını aşıyor. Daha küçük bir video seç."; return }
        guard mime.hasPrefix("video/") else { message = "Geçerli bir video dosyası seç."; return }
        let encoded = "data:\(mime);base64," + data.base64EncodedString()
        await submitTool(path: "api/upscale-video", payload: ["videoUri": encoded, "resolution": resolution], prompt: "Video kalite yükseltme: " + resolution)
    }

    func animateCharacter(imageData: Data, videoData: Data, videoMime: String, ratio: String) async {
        guard videoData.count <= 3_500_000 else { message = "Hareket videosu 3,5 MB sınırını aşıyor."; return }
        guard videoMime.hasPrefix("video/") else { message = "Geçerli bir hareket videosu seç."; return }
        guard let image = UIImage(data: imageData) else { message = "Karakter fotoğrafı okunamadı."; return }
        let maxSide: CGFloat = 1800
        let scale = min(1, maxSide / max(image.size.width, image.size.height))
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: size)
        let resized = renderer.image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
        var quality: CGFloat = 0.88
        var compressed = resized.jpegData(compressionQuality: quality) ?? Data()
        while compressed.count > 3_500_000 && quality > 0.40 {
            quality -= 0.08
            compressed = resized.jpegData(compressionQuality: quality) ?? Data()
        }
        guard !compressed.isEmpty, compressed.count <= 3_500_000 else { message = "Karakter fotoğrafı çok büyük. Daha küçük bir fotoğraf seç."; return }
        let character = "data:image/jpeg;base64," + compressed.base64EncodedString()
        let reference = "data:\(videoMime);base64," + videoData.base64EncodedString()
        await submitTool(path: "api/animate-character", payload: ["characterUri": character, "referenceUri": reference, "ratio": ratio], prompt: "Karakter animasyonu", ratio: ratio)
    }

    func refreshPendingTasks() async {
        let pending = history.filter { !["SUCCEEDED", "FAILED", "CANCELED", "CANCELLED"].contains($0.status.uppercased()) }
        for item in pending { await refreshTask(item.provider_task_id) }
        if !pending.isEmpty { await refreshAll() }
    }

    private func optimizedJPEG(_ source: Data, maxBytes: Int = 3_500_000) -> Data? {
        guard let image = UIImage(data: source) else { return nil }
        let maxSide: CGFloat = 1800
        let scale = min(1, maxSide / max(image.size.width, image.size.height))
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: size)
        let resized = renderer.image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
        var quality: CGFloat = 0.88
        var data = resized.jpegData(compressionQuality: quality) ?? Data()
        while data.count > maxBytes && quality > 0.32 {
            quality -= 0.08
            data = resized.jpegData(compressionQuality: quality) ?? Data()
        }
        return (!data.isEmpty && data.count <= maxBytes) ? data : nil
    }

    func generate(prompt: String, seconds: Int, ratio: String, imageData: Data? = nil) async {
        guard let token = session?.access_token else { message = "Önce giriş yap."; return }
        busy = true
        defer { busy = false }
        do {
            let url = Config.api.appendingPathComponent("api/generate")
            var req = URLRequest(url: url)
            req.httpMethod = "POST"
            req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            var payload: [String: Any] = ["prompt": prompt, "duration": seconds, "ratio": ratio]
            if let imageData {
                guard let compressed = optimizedJPEG(imageData) else {
                    throw NSError(domain: "FOTOFOTO", code: 413, userInfo: [NSLocalizedDescriptionKey: "Fotoğraf 3,5 MB sınırına küçültülemedi. Lütfen başka bir fotoğraf seç."])
                }
                payload["imageUri"] = "data:image/jpeg;base64," + compressed.base64EncodedString()
            }
            req.httpBody = try JSONSerialization.data(withJSONObject: payload)
            let (data, response) = try await URLSession.shared.data(for: req)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
                throw NSError(domain: "FOTOFOTO", code: (response as? HTTPURLResponse)?.statusCode ?? -1, userInfo: [NSLocalizedDescriptionKey: String(data: data, encoding: .utf8) ?? "Video isteği başarısız."])
            }
            let result = try JSONDecoder().decode(GenerateResponse.self, from: data)
            message = "Video isteği Runway'e gönderildi. Görev: \(result.taskId)"
            await saveHistory(taskId: result.taskId, prompt: prompt, seconds: seconds, ratio: ratio)
            await refreshAll()
        } catch { message = error.localizedDescription }
    }
}

enum KeychainSession {
    static let service = "com.fotofoto.ios.session"
    static func save(_ session: AuthSession) {
        guard let data = try? JSONEncoder().encode(session) else { return }
        clear()
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecValueData as String: data, kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
        SecItemAdd(query as CFDictionary, nil)
    }
    static func load() -> AuthSession? {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne]
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess, let data = result as? Data else { return nil }
        return try? JSONDecoder().decode(AuthSession.self, from: data)
    }
    static func clear() {
        SecItemDelete([kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service] as CFDictionary)
    }
}

@main struct FotoFotoApp: App {
    @StateObject private var store = Store()
    var body: some Scene {
        WindowGroup {
            Group { if store.session == nil { LoginView() } else { MainTabs() } }
                .environmentObject(store)
                .preferredColorScheme(.dark)
        }
    }
}

struct LoginView: View {
    @EnvironmentObject var store: Store
    @State private var email = ""
    @State private var password = ""
    @State private var displayName = ""
    @State private var code = ""
    @State private var register = false
    @State private var reset = false
    @State private var awaitingCode = false
    @State private var busy = false

    var body: some View {
        ZStack {
            LinearGradient(colors: [Color(red: 0.12, green: 0.09, blue: 0.20), Color(red: 0.04, green: 0.05, blue: 0.10)], startPoint: .top, endPoint: .bottom).ignoresSafeArea()
            ScrollView {
                VStack(spacing: 20) {
                    HStack {
                        Spacer()
                        Picker("Dil / Language", selection: $store.language) {
                            Text("Türkçe").tag("Türkçe")
                            Text("English").tag("English")
                            Text("العربية").tag("العربية")
                        }.pickerStyle(.menu).tint(.white)
                    }
                    Spacer(minLength: 30)
                    Text("▶").font(.system(size: 42, weight: .black)).frame(width: 76, height: 76).background(LinearGradient(colors: [.cyan, .purple, .pink], startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: 24))
                    Text("FOTO FOTO").font(.system(size: 30, weight: .black, design: .rounded))
                    Text(store.tr("YAPAY ZEKÂ VİDEO STÜDYOSU", "AI VIDEO STUDIO", "استوديو فيديو بالذكاء الاصطناعي")).font(.caption.bold()).tracking(3).foregroundStyle(.cyan)
                    VStack(spacing: 14) {
                        Text(awaitingCode ? store.tr("E-posta doğrulama", "Email verification", "تأكيد البريد الإلكتروني") : (reset ? store.tr("Şifremi yenile", "Reset password", "إعادة تعيين كلمة المرور") : (register ? store.tr("Ücretsiz hesap oluştur", "Create a free account", "إنشاء حساب مجاني") : store.tr("Tekrar hoş geldin!", "Welcome back!", "مرحباً بعودتك!"))))
                            .font(.title2.bold()).frame(maxWidth: .infinity, alignment: .leading)
                        if !awaitingCode {
                            if register && !reset {
                                TextField(store.tr("Adın", "Your name", "اسمك"), text: $displayName).textInputAutocapitalization(.words).textFieldStyle(.roundedBorder)
                            }
                            TextField(store.tr("E-posta", "Email", "البريد الإلكتروني"), text: $email).textInputAutocapitalization(.never).keyboardType(.emailAddress).autocorrectionDisabled().textFieldStyle(.roundedBorder)
                        }
                        if awaitingCode {
                            Text(store.tr("E-posta adresine gelen doğrulama kodunu gir.", "Enter the verification code sent to your email.", "أدخل رمز التحقق المرسل إلى بريدك الإلكتروني.")).font(.footnote).foregroundStyle(.secondary).frame(maxWidth: .infinity, alignment: .leading)
                            TextField(store.tr("Doğrulama kodu", "Verification code", "رمز التحقق"), text: $code).keyboardType(.numberPad).textFieldStyle(.roundedBorder)
                        } else if !reset {
                            SecureField(store.tr("Şifre (en az 8 karakter)", "Password (at least 8 characters)", "كلمة المرور (8 أحرف على الأقل)"), text: $password).textFieldStyle(.roundedBorder)
                        }
                        Button {
                            Task {
                                busy = true
                                store.message = ""
                                do {
                                    if awaitingCode {
                                        try await store.verifySignup(email: email, code: code)
                                    } else if reset {
                                        try await store.sendPasswordReset(email: email)
                                    } else if register {
                                        try await store.signUp(email: email, password: password, displayName: displayName.trimmingCharacters(in: .whitespacesAndNewlines))
                                        awaitingCode = true
                                    } else {
                                        try await store.signIn(email: email, password: password)
                                    }
                                } catch { store.message = error.localizedDescription }
                                busy = false
                            }
                        } label: {
                            if busy { ProgressView().tint(.white) }
                            else { Text(awaitingCode ? store.tr("KODU DOĞRULA", "VERIFY CODE", "تحقق من الرمز") : (reset ? store.tr("YENİLEME E-POSTASI GÖNDER", "SEND RESET EMAIL", "إرسال بريد إعادة التعيين") : (register ? store.tr("HESAP OLUŞTUR", "CREATE ACCOUNT", "إنشاء حساب") : store.tr("GİRİŞ YAP", "SIGN IN", "تسجيل الدخول")))).fontWeight(.bold).frame(maxWidth: .infinity) }
                        }.buttonStyle(PrimaryButton()).disabled(busy || (awaitingCode ? code.count < 6 : (!email.contains("@") || (!reset && (password.count < 8 || (register && displayName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty))))))
                        if awaitingCode {
                            Button(store.tr("Kodu tekrar gönder", "Resend code", "إعادة إرسال الرمز")) {
                                Task { busy = true; do { try await store.resendSignupCode(email: email) } catch { store.message = error.localizedDescription }; busy = false }
                            }.disabled(busy)
                        } else if !register && !reset {
                            Button(store.tr("Şifremi unuttum", "Forgot password?", "نسيت كلمة المرور؟")) { reset = true; store.message = "" }.foregroundStyle(.purple)
                        }
                        Button(awaitingCode ? store.tr("Giriş ekranına dön", "Back to sign in", "العودة لتسجيل الدخول") : (reset ? store.tr("Giriş ekranına dön", "Back to sign in", "العودة لتسجيل الدخول") : (register ? store.tr("Zaten hesabım var", "I already have an account", "لدي حساب بالفعل") : store.tr("Yeni hesap oluştur", "Create a new account", "إنشاء حساب جديد")))) {
                            awaitingCode = false
                            reset = false
                            register = !register && !reset
                            code = ""
                            store.message = ""
                        }.foregroundStyle(.purple)
                    }.padding(22).background(Color.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 24))
                    if !store.message.isEmpty { Text(store.message).font(.footnote).foregroundStyle(.orange).multilineTextAlignment(.center).textSelection(.enabled) }
                    Spacer(minLength: 50)
                }.padding(24)
            }
        }
    }
}

struct MainTabs: View {
    @EnvironmentObject var store: Store
    var body: some View {
        TabView {
            HomeView().tabItem { Label(store.tr("Ana Sayfa", "Home", "الرئيسية"), systemImage: "house.fill") }
            HistoryView().tabItem { Label(store.tr("Projelerim", "My Projects", "مشاريعي"), systemImage: "film") }
            CreateView().tabItem { Label(store.tr("Oluştur", "Create", "إنشاء"), systemImage: "plus.square.fill") }
            ToolsView().tabItem { Label(store.tr("Araçlar", "Tools", "الأدوات"), systemImage: "wand.and.stars") }
            WalletView().tabItem { Label(store.tr("Hesabım", "Account", "حسابي"), systemImage: "person.crop.circle") }
        }.tint(Color(red: 0.75, green: 0.40, blue: 1.0))
            .task { await store.refreshAll() }
    }
}

struct HomeView: View {
    @EnvironmentObject var store: Store
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    HStack(spacing: 12) {
                        Text("▶").font(.title.bold()).frame(width: 52, height: 52).background(LinearGradient(colors: [.cyan, .purple, .pink], startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: 16))
                        VStack(alignment: .leading) { Text("FOTO FOTO").font(.title2.bold()); Text("AI VIDEO STÜDYOSU").font(.caption.bold()).tracking(2).foregroundStyle(.cyan) }
                    }
                    Text("Hayallerini videoya dönüştür.").font(.title2.bold())
                    Text("Yapay zekâ destekli video oluşturma stüdyon.").foregroundStyle(.secondary)
                    NavigationLink(destination: CreateView()) { Label("Fotoğraftan veya metinden video oluştur", systemImage: "sparkles").frame(maxWidth: .infinity) }.buttonStyle(PrimaryButton())
                    HStack {
                        VStack(alignment: .leading) { Text("CÜZDANIN").font(.caption).foregroundStyle(.secondary); Text("\(store.balance) kredi").font(.title.bold()).foregroundStyle(.purple) }
                        Spacer()
                        VStack(alignment: .trailing) { Text("PROJELERİN").font(.caption).foregroundStyle(.secondary); Text("\(store.history.count)").font(.title.bold()) }
                    }.padding().background(Color.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 18))
                    Text("YARATICI ARAÇLAR").font(.caption.bold()).tracking(2).foregroundStyle(.purple)
                    NavigationLink(destination: ToolsView(initialMode: "edit")) { FeatureTile(title: "Video düzenleme", detail: "Videolarında yapay zekâ ile değişiklik yap", icon: "wand.and.stars") }
                    NavigationLink(destination: ToolsView(initialMode: "upscale")) { FeatureTile(title: "Kalite yükseltme", detail: "Videolarını daha net hâle getir", icon: "4k.tv") }
                    NavigationLink(destination: ToolsView(initialMode: "character")) { FeatureTile(title: "Karakter animasyonu", detail: "Karakterlerine hareket kazandır", icon: "person.crop.square") }
                }.padding()
            }.background(Color(red: 0.04, green: 0.05, blue: 0.10)).navigationTitle("FOTO FOTO")
        }
    }
}

struct ToolsView: View {
    @EnvironmentObject var store: Store
    @State private var mode: String
    init(initialMode: String = "edit") { _mode = State(initialValue: initialMode) }
    @State private var prompt = ""
    @State private var resolution = "2k"
    @State private var vertical = false
    @State private var editPicker: PhotosPickerItem?
    @State private var upscalePicker: PhotosPickerItem?
    @State private var characterPicker: PhotosPickerItem?
    @State private var motionPicker: PhotosPickerItem?
    @State private var editData: Data?
    @State private var upscaleData: Data?
    @State private var characterData: Data?
    @State private var motionData: Data?
    @State private var editMime = "video/mp4"
    @State private var upscaleMime = "video/mp4"
    @State private var motionMime = "video/mp4"

    private var modes: [String] { ["edit", "upscale", "character"] }
    var body: some View {
        NavigationStack {
            Form {
                Section("YARATICI ARAÇLAR") {
                    Picker("Araç", selection: $mode) {
                        Text("Video düzenleme").tag("edit")
                        Text("Kalite yükseltme").tag("upscale")
                        Text("Karakter animasyonu").tag("character")
                    }.pickerStyle(.menu)
                }
                if mode == "edit" {
                    Section("VİDEONU DÜZENLE") {
                        Text("Videonda yapay zekâ ile neyin değişmesini istediğini anlat.")
                        TextEditor(text: $prompt).frame(minHeight: 100)
                        PhotosPicker(selection: $editPicker, matching: .videos) {
                            Label(editData == nil ? "Düzenlenecek videoyu seç" : "Video seçildi · Değiştir", systemImage: "video")
                        }
                        if let editData { Text("Seçilen video: \(ByteCountFormatter.string(fromByteCount: Int64(editData.count), countStyle: .file))").font(.caption).foregroundStyle(.secondary) }
                        Button {
                            Task { await store.editVideo(prompt: prompt.trimmingCharacters(in: .whitespacesAndNewlines), data: editData ?? Data(), mime: editMime) }
                        } label: {
                            if store.busy { ProgressView() } else { Text("VİDEOYU DÜZENLE").fontWeight(.bold).frame(maxWidth: .infinity) }
                        }.buttonStyle(PrimaryButton()).disabled(store.busy || editData == nil || prompt.trimmingCharacters(in: .whitespacesAndNewlines).count < 3)
                    }
                } else if mode == "upscale" {
                    Section("VİDEO KALİTESİNİ YÜKSELT") {
                        PhotosPicker(selection: $upscalePicker, matching: .videos) {
                            Label(upscaleData == nil ? "Kalitesi yükseltilecek videoyu seç" : "Video seçildi · Değiştir", systemImage: "video")
                        }
                        Picker("Hedef çözünürlük", selection: $resolution) {
                            Text("2K").tag("2k")
                            Text("4K").tag("4k")
                        }
                        if let upscaleData { Text("Seçilen video: \(ByteCountFormatter.string(fromByteCount: Int64(upscaleData.count), countStyle: .file))").font(.caption).foregroundStyle(.secondary) }
                        Button {
                            Task { await store.upscaleVideo(data: upscaleData ?? Data(), mime: upscaleMime, resolution: resolution) }
                        } label: {
                            if store.busy { ProgressView() } else { Text("KALİTEYİ YÜKSELT").fontWeight(.bold).frame(maxWidth: .infinity) }
                        }.buttonStyle(PrimaryButton()).disabled(store.busy || upscaleData == nil)
                    }
                } else {
                    Section("KARAKTERİNE HAREKET VER") {
                        PhotosPicker(selection: $characterPicker, matching: .images) {
                            Label(characterData == nil ? "Karakter fotoğrafını seç" : "Karakter fotoğrafı seçildi · Değiştir", systemImage: "person.crop.square")
                        }
                        if let characterData, let image = UIImage(data: characterData) {
                            Image(uiImage: image).resizable().scaledToFit().frame(maxHeight: 160)
                        }
                        PhotosPicker(selection: $motionPicker, matching: .videos) {
                            Label(motionData == nil ? "Örnek hareket videosunu seç" : "Hareket videosu seçildi · Değiştir", systemImage: "video")
                        }
                        Picker("Video formatı", selection: $vertical) {
                            Text("Yatay 16:9").tag(false)
                            Text("Dikey 9:16").tag(true)
                        }
                        Button {
                            Task { await store.animateCharacter(imageData: characterData ?? Data(), videoData: motionData ?? Data(), videoMime: motionMime, ratio: vertical ? "720:1280" : "1280:720") }
                        } label: {
                            if store.busy { ProgressView() } else { Text("KARAKTERİ ANİME ET").fontWeight(.bold).frame(maxWidth: .infinity) }
                        }.buttonStyle(PrimaryButton()).disabled(store.busy || characterData == nil || motionData == nil)
                    }
                }
                Section("DOSYA SINIRI") {
                    Text("Video dosyaları en fazla 3,5 MB olmalıdır. İşlem tamamlandığında sonuç Projelerim bölümüne kaydedilir.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                if !store.message.isEmpty {
                    Section("İŞLEM DURUMU") { Text(store.message).font(.footnote).textSelection(.enabled) }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Color(red: 0.04, green: 0.05, blue: 0.10))
            .navigationTitle("Araçlar")
            .onChange(of: editPicker) { item in
                Task {
                    editData = try? await item?.loadTransferable(type: Data.self)
                    editMime = item?.supportedContentTypes.first?.preferredMIMEType ?? "video/mp4"
                }
            }
            .onChange(of: upscalePicker) { item in
                Task {
                    upscaleData = try? await item?.loadTransferable(type: Data.self)
                    upscaleMime = item?.supportedContentTypes.first?.preferredMIMEType ?? "video/mp4"
                }
            }
            .onChange(of: characterPicker) { item in Task { characterData = try? await item?.loadTransferable(type: Data.self) } }
            .onChange(of: motionPicker) { item in
                Task {
                    motionData = try? await item?.loadTransferable(type: Data.self)
                    motionMime = item?.supportedContentTypes.first?.preferredMIMEType ?? "video/mp4"
                }
            }
        }
    }
}

struct FeatureTile: View {
    let title: String
    let detail: String
    let icon: String
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon).font(.title2).foregroundStyle(.purple).frame(width: 42, height: 42).background(Color.purple.opacity(0.14), in: RoundedRectangle(cornerRadius: 12))
            VStack(alignment: .leading, spacing: 4) { Text(title).font(.headline); Text(detail).font(.caption).foregroundStyle(.secondary) }
        }.padding(.vertical, 5)
    }
}

struct CreateView: View {
    @EnvironmentObject var store: Store
    @State private var prompt = ""
    @State private var seconds = 5
    @State private var vertical = false
    @State private var selectedPhoto: PhotosPickerItem?
    @State private var photoData: Data?
    var body: some View {
        NavigationStack {
            Form {
                Section("FİKRİNİ VİDEOYA DÖNÜŞTÜR") {
                    TextEditor(text: $prompt).frame(minHeight: 130).overlay(alignment: .topLeading) { if prompt.isEmpty { Text("Nasıl bir video hayal ediyorsun?").foregroundStyle(.secondary).padding(.top, 8).padding(.leading, 5).allowsHitTesting(false) } }
                    Picker("Video süresi", selection: $seconds) { Text("5 saniye · 59 kredi").tag(5); Text("10 saniye · 119 kredi").tag(10) }
                    Picker("Format", selection: $vertical) { Text("16:9").tag(false); Text("9:16").tag(true) }
                    PhotosPicker(selection: $selectedPhoto, matching: .images) { Label(photoData == nil ? "Fotoğraf ekle" : "Fotoğraf seçildi", systemImage: "photo") }
                    if let photoData, let image = UIImage(data: photoData) { Image(uiImage: image).resizable().scaledToFit().frame(maxHeight: 190) }
                    Text("Runway Gen-4.5 · \(seconds == 5 ? 59 : 119) kredi").foregroundStyle(.orange).font(.headline)
                    Button { Task { await store.generate(prompt: prompt, seconds: seconds, ratio: vertical ? "720:1280" : "1280:720", imageData: photoData) } } label: {
                        if store.busy { ProgressView() } else { Text("✦ VİDEOMU OLUŞTUR").fontWeight(.bold).frame(maxWidth: .infinity) }
                    }.buttonStyle(PrimaryButton()).disabled(store.busy || prompt.trimmingCharacters(in: .whitespacesAndNewlines).count < 10)
                }
                if !store.message.isEmpty { Section("Durum") { Text(store.message).font(.footnote).textSelection(.enabled) } }
            }.scrollContentBackground(.hidden).background(Color(red: 0.04, green: 0.05, blue: 0.10))
                .navigationTitle("FOTO FOTO")
                .onChange(of: selectedPhoto) { item in Task { photoData = try? await item?.loadTransferable(type: Data.self) } }
        }
    }
}

struct HistoryView: View {
    @EnvironmentObject var store: Store
    var body: some View {
        NavigationStack {
            List(store.history) { item in
                VStack(alignment: .leading, spacing: 6) {
                    Text(item.prompt).font(.headline).lineLimit(3)
                    Text(item.status).font(.caption).foregroundStyle(.orange)
                    if let link = item.video_url, let url = URL(string: link) { Link("Videoyu aç", destination: url) }
                }.padding(.vertical, 5)
            }.navigationTitle("Projelerim")
                .toolbar { Button { Task { await store.refreshAll(); await store.refreshPendingTasks() } } label: { Image(systemName: "arrow.clockwise") } }
                .refreshable { await store.refreshAll(); await store.refreshPendingTasks() }
                .task {
                    while !Task.isCancelled {
                        await store.refreshPendingTasks()
                        try? await Task.sleep(nanoseconds: 25_000_000_000)
                    }
                }
        }
    }
}

struct WalletView: View {
    @EnvironmentObject var store: Store
    @State private var showDeleteConfirm = false
    @State private var deleting = false
    @State private var accountMessage = ""

    var body: some View {
        NavigationStack {
            List {
                Section("HESABIM") {
                    HStack(spacing: 12) {
                        Image(systemName: "person.crop.circle.fill").font(.system(size: 42)).foregroundStyle(.purple)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(store.session?.user?.user_metadata?["display_name"] ?? "FOTO FOTO kullanıcısı").font(.headline)
                            Text(store.session?.user?.email ?? "Kişisel hesabın").font(.caption).foregroundStyle(.secondary)
                        }
                    }.padding(.vertical, 6)
                    Button("Yenile") { Task { await store.refreshAll() } }
                }
                Section("KREDİ CÜZDANIN") {
                    Text("\(store.balance) kredi").font(.largeTitle.bold()).foregroundStyle(.purple)
                    Text("1 kredi = 1 TL").foregroundStyle(.secondary)
                    HStack {
                        VStack(alignment: .leading) { Text("5 SANİYE").font(.caption).foregroundStyle(.secondary); Text("59 kredi").font(.headline) }
                        Spacer()
                        VStack(alignment: .leading) { Text("10 SANİYE").font(.caption).foregroundStyle(.secondary); Text("119 kredi").font(.headline) }
                    }.padding(.vertical, 4)
                    Text("Kredi satın alma henüz etkin değil. Ödeme sistemi hazır olduğunda bu bölümden açılacak.").font(.footnote).foregroundStyle(.secondary)
                }
                Section("SON KREDİ HAREKETLERİ") {
                    if store.ledger.isEmpty {
                        Text("Henüz kredi hareketi yok.").foregroundStyle(.secondary)
                    } else {
                        ForEach(store.ledger) { row in
                            HStack {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(row.description ?? row.entry_type ?? "Kredi hareketi")
                                    if let type = row.entry_type { Text(type.uppercased()).font(.caption2).foregroundStyle(.secondary) }
                                }
                                Spacer()
                                Text("\(row.amount_credits > 0 ? "+" : "")\(row.amount_credits)")
                                    .fontWeight(.semibold)
                                    .foregroundStyle(row.amount_credits >= 0 ? .green : .orange)
                            }
                        }
                    }
                }
                Section("GÜVENLİK") {
                    Button("Çıkış yap", role: .destructive) { store.signOut() }
                    Button("Hesabımı kalıcı olarak sil", role: .destructive) { showDeleteConfirm = true }
                    if !accountMessage.isEmpty { Text(accountMessage).font(.footnote).foregroundStyle(.orange) }
                }
            }
            .navigationTitle("Hesabım")
            .confirmationDialog("Hesabın kalıcı olarak silinsin mi?", isPresented: $showDeleteConfirm, titleVisibility: .visible) {
                Button("Evet, hesabımı kalıcı olarak sil", role: .destructive) {
                    Task {
                        deleting = true
                        do {
                            try await store.deleteAccount()
                            accountMessage = "Hesabın silindi."
                        } catch { accountMessage = error.localizedDescription }
                        deleting = false
                    }
                }
                Button("Vazgeç", role: .cancel) {}
            } message: {
                Text("Hesabın ve hesaba bağlı profil, cüzdan ve video geçmişi verileri silinir. Bu işlem geri alınamaz.")
            }
            .overlay {
                if deleting { ProgressView("Hesap siliniyor…").padding().background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14)) }
            }
        }
    }
}

struct PrimaryButton: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.padding(.vertical, 14).padding(.horizontal, 16)
            .foregroundStyle(.white).background(Color(red: 1, green: 0.43, blue: 0.34).opacity(configuration.isPressed ? 0.7 : 1), in: RoundedRectangle(cornerRadius: 16))
    }
}
