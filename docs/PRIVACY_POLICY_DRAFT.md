# FOTO FOTO — Gizlilik Politikası Taslağı

> **YAYIN ÖNCESİ TAMAMLANMASI GEREKEN TASLAK:** Bu metin hukuki danışmanlık değildir. Uygulama mağazasında yayımlanmadan önce işletmeci unvanı, iletişim bilgileri, veri sorumlusu bilgileri, saklama süreleri, yurt dışı aktarım mekanizması ve kullanıcı başvuru kanalları doğrulanmalı; KVKK ve uygulanabilir diğer mevzuata göre hukuk danışmanı tarafından gözden geçirilmelidir. Köşeli parantezli alanlar doldurulmadan yayımlanamaz.

**Son güncelleme:** [GG.AA.YYYY]  
**Uygulama:** FOTO FOTO  
**Veri sorumlusu/işletmeci:** [Gerçek kişi veya şirketin tam yasal unvanı]  
**İletişim:** [Destek e-posta adresi ve posta adresi]

## 1. Hangi veriler işlenir?

FOTO FOTO kullanıldığında aşağıdaki veri kategorileri işlenebilir:

- **Hesap bilgileri:** Kayıt ve oturum açma için kullanılan e-posta adresi ve hesap kimliği; kullanıcı tarafından sağlanmışsa görünen ad.
- **Üretim girdileri:** Kullanıcının yazdığı video komutları ve seçerek yüklediği referans fotoğrafları.
- **Kredi ve işlem kayıtları:** Kredi bakiyesi, kredi hareketleri, üretim işlemi referansları ve hata/durum kayıtları.
- **Teknik veriler:** İsteklerin güvenli ve doğru çalışması için gerekli bağlantı, zaman, cihaz/uygulama sürümü ve hata kayıtları. Gerçekte toplanmayan veriler bu listeye eklenmemelidir.

## 2. Veriler hangi amaçlarla kullanılır?

Veriler; hesap oluşturma ve oturum yönetimi, kullanıcının başlattığı video üretim işlemlerini gerçekleştirme, kredi bakiyesini ve işlem geçmişini yönetme, hizmet güvenliğini sağlama, hataları giderme ve destek taleplerini yanıtlama amaçlarıyla işlenir.

## 3. Hizmet sağlayıcılar ve veri aktarımı

- **Supabase:** Hesap kimlik doğrulama ve uygulamanın veritabanı/cüzdan altyapısı için kullanılır.
- **Runway:** Kullanıcının başlattığı üretim talebini gerçekleştirmek için komut ve seçilmiş görsel Runway hizmetine gönderilir. Runway'in kendi gizlilik, saklama ve yurt dışı işleme koşulları ayrıca incelenmelidir.
- **iyzico:** Yalnızca ödeme entegrasyonu etkinleştirilir ve kullanıcı ödeme işlemi başlatırsa ödeme bilgileri ilgili ödeme hizmeti üzerinden işlenebilir. Bu entegrasyon henüz etkin değilse bu bölüm yayına çıkmadan önce buna göre güncellenmelidir.

Hizmet sağlayıcıların güncel veri işleme şartları, sunucu bölgeleri, alt işleyenleri ve yurt dışı aktarım için kullanılacak hukuki mekanizma yayın öncesinde doğrulanmalıdır.

## 4. Ödeme bilgileri

Ödeme özelliği etkinleştirildiğinde kart bilgileri FOTO FOTO sunucularında saklanmamalıdır. Ödeme, onaylanmış ödeme sağlayıcısının barındırdığı güvenli ödeme akışında tamamlanmalıdır. FOTO FOTO yalnızca siparişin ve kredi yükleme sonucunun yönetimi için gerekli işlem referanslarını tutabilir. Gerçek uygulama akışı doğrulanmadan bu açıklama yayımlanmamalıdır.

## 5. Saklama ve silme

Veriler yalnızca belirtilen amaçlar için gerekli süre boyunca ve uygulanabilir yasal yükümlülükler çerçevesinde saklanmalıdır.

- Hesap ve cüzdan verileri için saklama/silme süresi: [Belirlenecek]
- Üretim girdileri ve sağlayıcı işlem kayıtları için saklama süresi: [Belirlenecek]
- Finansal kayıtlar için yasal saklama süresi: [Hukuk/muhasebe incelemesiyle belirlenecek]

Uygulamada hesap silme işlevi bulunur. Silme talebinin hangi verileri, yedekleri ve üçüncü taraf hizmet sağlayıcı kayıtlarını kapsadığı teknik olarak doğrulanmalı ve burada açıkça anlatılmalıdır.

## 6. Güvenlik

Hesap erişimi kimlik doğrulama ile korunur. Sunucu tarafı gizli anahtarlar Android uygulamasına gömülmemeli; özel ortam değişkenlerinde tutulmalıdır. Makul teknik ve idari güvenlik önlemleri uygulanır. Hiçbir internet aktarımı veya depolama yöntemi mutlak güvenlik garantisi vermez.

## 7. Çocukların gizliliği

Uygulamanın hedef yaş grubu ve çocukların kullanımına ilişkin yaklaşım: [Yayın öncesi belirlenecek]. Yaş sınırı ve mağaza içerik derecelendirmesi bu metinle tutarlı olmalıdır.

## 8. Kullanıcı hakları ve başvuru

Kişisel verilerle ilgili talepler için: [KVKK başvuru e-posta adresi], [posta adresi] üzerinden başvurulabilir. Başvuru yöntemi, yanıt süresi ve gerekli kimlik doğrulama usulü yayın öncesinde tamamlanmalıdır.

## 9. Politika değişiklikleri

Politikada değişiklik yapılırsa güncel metin ve yürürlük tarihi uygulama içinde ve/veya resmi web sitesinde yayımlanır.

## Yayın öncesi doldurulacak bilgiler

- [ ] Veri sorumlusu/işletmeci tam yasal unvanı
- [ ] Açık destek ve KVKK başvuru e-posta adresi
- [ ] Fiziksel/tebligat adresi gerekiyorsa
- [ ] Supabase ve Runway veri bölgeleri ve güncel veri işleme şartları
- [ ] Yurt dışı aktarım hukuki mekanizması
- [ ] Her veri kategorisi için saklama ve silme süreleri
- [ ] Hesap silme kapsamı ve yedeklerden silinme takvimi
- [ ] Yaş sınırı ve çocuklara yönelik yaklaşım
- [ ] iyzico entegrasyonu etkinleştirilirse ödeme veri akışının doğrulanması
- [ ] Hukuk danışmanı incelemesi
