import SwiftUI
import PhotosUI
import UIKit
import Combine
import Security

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
struct AuthUser: Codable { let id: String; let email: String? }
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
    @Published var language = "Türkçe"

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
        let url = Config.supabase.appendingPathComponent("auth/v1/\(endpoint)")
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
    func signUp(email: String, password: String) async throws {
        try await auth("signup", payload: ["email": email, "password": password])
        message = "Doğrulama e-postası gönderildiyse gelen kutunu kontrol et. İlk sürümde e-posta kodu doğrulama ekranı henüz tamamlanmadı."
        session = nil
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
            if let imageData { payload["imageUri"] = "data:image/jpeg;base64," + imageData.base64EncodedString() }
            req.httpBody = try JSONSerialization.data(withJSONObject: payload)
            let (data, response) = try await URLSession.shared.data(for: req)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
                throw NSError(domain: "FOTOFOTO", code: (response as? HTTPURLResponse)?.statusCode ?? -1, userInfo: [NSLocalizedDescriptionKey: String(data: data, encoding: .utf8) ?? "Video isteği başarısız."])
            }
            let result = try JSONDecoder().decode(GenerateResponse.self, from: data)
            message = "Video isteği Runway'e gönderildi. Görev: \(result.taskId)"
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
    @State private var register = false
    var body: some View {
        ZStack {
            Color(red: 0.04, green: 0.05, blue: 0.10).ignoresSafeArea()
            VStack(spacing: 20) {
                Spacer()
                Text("▶").font(.system(size: 42, weight: .black)).frame(width: 76, height: 76).background(LinearGradient(colors: [.cyan, .purple, .pink], startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: 24))
                Text("FOTO FOTO").font(.system(size: 30, weight: .black, design: .rounded))
                Text("AI VIDEO STÜDYOSU").font(.caption.bold()).tracking(3).foregroundStyle(.cyan)
                VStack(spacing: 14) {
                    TextField("E-posta", text: $email).textInputAutocapitalization(.never).keyboardType(.emailAddress).autocorrectionDisabled().textFieldStyle(.roundedBorder)
                    SecureField("Şifre", text: $password).textFieldStyle(.roundedBorder)
                    Button(register ? "HESAP OLUŞTUR" : "GİRİŞ YAP") {
                        Task { do { if register { try await store.signUp(email: email, password: password) } else { try await store.signIn(email: email, password: password) } } catch { store.message = error.localizedDescription } }
                    }.buttonStyle(PrimaryButton()).disabled(email.isEmpty || password.count < 8)
                    Button(register ? "Zaten hesabım var" : "Yeni hesap oluştur") { register.toggle() }.foregroundStyle(.purple.opacity(0.9))
                }.padding(22).background(Color.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 24))
                if !store.message.isEmpty { Text(store.message).font(.footnote).foregroundStyle(.orange).multilineTextAlignment(.center) }
                Spacer()
            }.padding(24)
        }
    }
}

struct MainTabs: View {
    @EnvironmentObject var store: Store
    var body: some View {
        TabView {
            CreateView().tabItem { Label("Oluştur", systemImage: "plus.square.fill") }
            HistoryView().tabItem { Label("Projelerim", systemImage: "film") }
            WalletView().tabItem { Label("Hesabım", systemImage: "person.crop.circle") }
        }.tint(Color(red: 0.75, green: 0.40, blue: 1.0))
            .task { await store.refreshAll() }
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
                .toolbar { Button { Task { await store.refreshAll() } } label: { Image(systemName: "arrow.clockwise") } }
                .refreshable { await store.refreshAll() }
        }
    }
}

struct WalletView: View {
    @EnvironmentObject var store: Store
    var body: some View {
        NavigationStack {
            List {
                Section("KREDİ CÜZDANIN") {
                    Text("\(store.balance) kredi").font(.largeTitle.bold()).foregroundStyle(.purple)
                    Text("1 kredi = 1 TL").foregroundStyle(.secondary)
                    Text("Ödeme ve kredi satın alma özellikleri henüz etkin değil.").font(.footnote).foregroundStyle(.secondary)
                    Button("Bakiyeyi yenile") { Task { await store.refreshAll() } }
                }
                Section("SON KREDİ HAREKETLERİ") {
                    ForEach(store.ledger) { row in
                        HStack { Text(row.description ?? row.entry_type ?? "Kredi hareketi"); Spacer(); Text("\(row.amount_credits)") }
                    }
                }
                Section { Button("Çıkış yap", role: .destructive) { store.signOut() } }
            }.navigationTitle("Hesabım")
        }
    }
}

struct PrimaryButton: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.padding(.vertical, 14).padding(.horizontal, 16)
            .foregroundStyle(.white).background(Color(red: 1, green: 0.43, blue: 0.34).opacity(configuration.isPressed ? 0.7 : 1), in: RoundedRectangle(cornerRadius: 16))
    }
}
