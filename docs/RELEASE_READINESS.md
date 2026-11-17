# FOTO FOTO — Yayın Hazırlık Kontrol Listesi

Bu liste kod deposunda görülen mevcut durum ile henüz dış sistemlerde yapılması gereken işleri ayırır. Bir kutu yalnızca gerçek test/kanıt tamamlandığında işaretlenmelidir.

## Mevcut kodda bulunanlar

- [x] Supabase e-posta/oturum temelli hesap altyapısı
- [x] Cüzdan bakiyesi ve kredi hareketlerini gösteren Android ekranı
- [x] Sunucu tarafında kredi rezervasyonu ve başarısız üretimde rezervasyonu serbest bırakma akışı
- [x] Runway Gen-4.5 metinden videoya ve referans fotoğraftan videoya üretim endpoint'i
- [x] Üretim görevi durumunu sorgulama ve video indirme akışı
- [x] Uygulama içinden hesap silme isteği
- [x] iyzico Checkout Form için backend temeli ve Supabase migration dosyası

## Kodda tamamlanması gerekenler

- [ ] Google Play dağıtımı için Google Play Billing entegrasyonu (dijital kredi satışı)
- [ ] iyzico'nun uygun olduğu web/diğer dağıtım kanalı için Android dışı satın alma akışı veya web ödeme sayfası; hesap ve mağaza kuralları doğrulandıktan sonra
- [ ] Kredi paketleri, fiyatlar ve kredi miktarları için onaylanmış ürün kataloğu
- [ ] Fotoğraf/video düzenleme, kalite yükseltme ve karakter modlarının gerçek backend, kredi tarifesi, hata/iade ve testleri
- [ ] Gizlilik politikası ve kullanım koşullarının gerçek işletmeci/veri işleme bilgileriyle tamamlanması
- [ ] Uygulama içinde gizlilik, kullanım koşulları ve destek bağlantıları
- [ ] Hesap silme işleminin ilişkili kayıtlar, yedekler ve üçüncü taraf sağlayıcılar açısından uçtan uca doğrulanması
- [ ] E-posta doğrulama, şifre sıfırlama ve oturum süresi dolma akışlarının uçtan uca testi

## Supabase / Render / sağlayıcı kurulumu

- [ ] Supabase migration 0001–0008 sırasıyla uygulanmalı
- [ ] Migration 0008 uygulandıktan sonra iyzico settlement RPC ve indeksleri doğrulanmalı
- [ ] Render ortam değişkenleri doğrulanmalı; servis anahtarları yalnızca sunucuda tutulmalı
- [ ] iyzico sandbox anahtarları ve callback URL yapılandırılmalı (iyzico onayı ve ürün uygunluğu sonrasında)
- [ ] Runway API anahtarı, kota ve model erişimi doğrulanmalı
- [ ] Ürünler gerçek fiyat/kredi değerleriyle eklenmeli ve test tamamlanana kadar pasif tutulmalı
- [ ] CORS izin verilen web origin'leriyle sınırlandırılmalı; üretim domain'i belirlenmeli

## Zorunlu test senaryoları

- [ ] Yeni kayıt, e-posta doğrulama, giriş, çıkış, şifre sıfırlama
- [ ] Yetersiz kredi, başarılı üretim, sağlayıcı hatası ve kredi iadesi
- [ ] Aynı ödeme callback'inin tekrar gelmesi ve çift kredi yüklenmemesi
- [ ] Ödeme tutarı, para birimi, sipariş ve kullanıcı eşleşmesi
- [ ] Uygulamanın ödeme/üretim sırasında kapanması ve yeniden açılması
- [ ] Zayıf internet, zaman aşımı ve tekrar deneme
- [ ] Fotoğraf izinleri, desteklenmeyen dosya türü ve büyük dosya
- [ ] Hesap silme ve silme sonrası yeniden girişin engellenmesi
- [ ] Farklı ekran boyutları ve gerçek Android cihazlar

## Yayın kapısı

Uygulama; gizlilik metni, kullanım koşulları, mağaza ödeme yöntemi, içerik derecelendirmesi, destek kanalı, gerçek cihaz testleri ve yasal inceleme tamamlanmadan üretim yayınına hazır kabul edilmemelidir.
