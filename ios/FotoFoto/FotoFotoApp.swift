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

    func refreshPendingTasks() async {
        let pending = history.filter { !["SUCCEEDED", "FAILED", "CANCELED", "CANCELLED"].contains($0.status.uppercased()) }
        for item in pending { await refreshTask(item.provider_task_id) }
        if !pending.isEmpty { await refreshAll() }
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
            HomeView().tabItem { Label("Ana Sayfa", systemImage: "house.fill") }
            HistoryView().tabItem { Label("Projelerim", systemImage: "film") }
            CreateView().tabItem { Label("Oluştur", systemImage: "plus.square.fill") }
            ToolsView().tabItem { Label("Araçlar", systemImage: "wand.and.stars") }
            WalletView().tabItem { Label("Hesabım", systemImage: "person.crop.circle") }
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
                    Text("YAKINDA").font(.caption.bold()).tracking(2).foregroundStyle(.purple)
                    FeatureTile(title: "Video düzenleme", detail: "Videolarında yapay zekâ ile değişiklik yap", icon: "wand.and.stars")
                    FeatureTile(title: "Kalite yükseltme", detail: "Videolarını daha net hâle getir", icon: "4k.tv")
                    FeatureTile(title: "Karakter animasyonu", detail: "Karakterlerine hareket kazandır", icon: "person.crop.square")
                }.padding()
            }.background(Color(red: 0.04, green: 0.05, blue: 0.10)).navigationTitle("FOTO FOTO")
        }
    }
}
struct ToolsView: View {
    var body: some View {
        NavigationStack {
            List {
                Section("YARATICI ARAÇLAR") {
                    FeatureTile(title: "Video düzenleme", detail: "Hazırlanıyor — güvenli yükleme ve fiyatlandırma tamamlanınca açılacak.", icon: "wand.and.stars")
                    FeatureTile(title: "Kalite yükseltme", detail: "Hazırlanıyor — servis ve kredi hesabı tamamlanınca açılacak.", icon: "4k.tv")
                    FeatureTile(title: "Karakter animasyonu", detail: "Hazırlanıyor — karakter hareketi entegrasyonu tamamlanınca açılacak.", icon: "person.crop.square")
                }
            }.navigationTitle("Araçlar")
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
                            Text(store.session?.user?.email ?? "FOTO FOTO kullanıcısı").font(.headline)
                            Text("Kişisel hesabın").font(.caption).foregroundStyle(.secondary)
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
