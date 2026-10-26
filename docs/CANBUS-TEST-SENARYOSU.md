# Canbus analiz testi (Golf 7, Hiworld kutusu)

**Amaç:** Hiworld kutusunun canlı gönderdiği ama henüz çözemediğimiz çerçeveleri (18, 22, 23, 26, 117, 118, 119, 250
ve bilgi servisindeki `-1` olmayan değerler) gerçek araç eylemleriyle eşleştirmek. Hangi kapı, far, sinyal,
el freni, vites, silecek, klima, direksiyon tuşu hangi çerçevede hangi bitte? Dış ve kabin sıcaklığı kutudan
geliyor mu? Sonuç `docs/CANBUS-CERCEVELER.md` dosyasına yazılacak (test sonrası).

**Kural:** Bu test yalnızca *dinler ve kayıt alır*. Launcher araca hiçbir şey göndermez. Yalnızca D grubundaki
"Araç ayarı" gezintisini **fabrikanın kendi uygulaması** yapar; o uygulamanın istekleri ve araçtan gelen
yanıtlar da kayda düşer. Hiçbir ayarı değiştirme (D13'teki zararsız birim değişikliği hariç, geri alınır).

**Güvenlik:** C grubu (sürüş) yalnızca boş, güvenli bir yolda ve ideal olarak yanında biri varsa yapılır; yolcu
yoksa tüm adımlara hareket etmeden önce dokun ve sonra eylemi yap. Sürüş sırasında ekrana bakma.

## Hazırlık (5 dk)
1. En son sürümü kur (GitHub Releases'teki en üst). Yeşil derleme bekle.
2. Ayarlar → Araç: **Gezi verilerini OBD'den al** açık olsun. Komut gönderme anahtarı kapalı kalabilir
   (okuma isteği yok; Bakım bilgisi hariç).
3. Ayarlar → **Tanılama kaydı** → anahtarı aç. (Eski kaydı silmek için "Kaydı sil".)
4. Telefonun Bluetooth'u açık kalsın, müzik çalma (kayıt gürültüsünü azaltır).
5. Telefonda kronometre ya da saat hazır; fotoğraf için kamera.

## Nasıl işliyor
- Ayarlar → Tanılama kaydı → **Canbus test senaryosu** sayfasını aç. Aşağıdaki adımlar orada aynı numaralarla durur.
- Her adımda: **önce adıma dokun** (kayıta `MARK` satırı ve saat düşer, adımın yanında ✓ ve saat çıkar), **sonra eylemi yap**.
- "AÇ-KAPAT" adımları: eylemi yap, 5 sn bekle, geri al, 5 sn daha bekle.
- Bir adımı atlayabilirsin; sırayı bozma. Yanlış dokunduysan not al (kayıtta saati görünür).
- Hava sıcaklığı adımlarında (B01, C06) göstergedeki değerin **fotoğrafını** çek; ekran görüntüsüne gerek yok.
- Test bitince Ayarlar → Tanılama kaydı → **Kaydı USB'ye aktar**; dosyanın yolunu bana yaz, fotoğrafları da ekle.

## Adımlar

### A: Ateşleme AÇIK, motor KAPALI (park halinde, fren pedalı serbest)

| No | Adım |
| --- | --- |
| A01 | Başlangıç: 30 sn hiçbir şeye dokunma |
| A02 | Sürücü kapısı AÇ-KAPAT |
| A03 | Yolcu kapısı AÇ-KAPAT |
| A04 | Arka sol kapı AÇ-KAPAT |
| A05 | Arka sağ kapı AÇ-KAPAT |
| A06 | Bagaj AÇ-KAPAT |
| A07 | Sürücü emniyet kemeri TAK-ÇIKAR |
| A08 | Yolcu emniyet kemeri TAK-ÇIKAR (koltukta biri varsa) |
| A09 | El freni ÇEK-BIRAK (fren pedalına basarak) |
| A10 | Park lambaları AÇ-KAPAT |
| A11 | Kısa far AÇ-KAPAT |
| A12 | Ön sis farı AÇ-KAPAT (varsa) |
| A13 | Arka sis lambası AÇ-KAPAT |
| A14 | Uzun far AÇ-KAPAT |
| A15 | Dörtlü flaşör AÇ-KAPAT |
| A16 | Sol sinyal AÇ-KAPAT |
| A17 | Sağ sinyal AÇ-KAPAT |
| A18 | Silecek: tek silme, hız 1, hız 2, KAPAT |
| A19 | İç aydınlatma AÇ-KAPAT |
| A20 | Kapı düğmesiyle kilitle, sonra kilidi AÇ |
| A21 | Klima sıcaklığı 22, sonra 24, sonra 20 (ekrandaki değerleri not et) |
| A22 | Fan hızı 1, sonra 4, sonra OTO |
| A23 | Klima (A/C) AÇ-KAPAT |
| A24 | İç hava (resirkülasyon) AÇ-KAPAT |
| A25 | Arka cam ısıtma AÇ-KAPAT |
| A26 | Direksiyon tuşları: ses+, ses-, ileri, geri, mod, telefon (3 sn arayla) |
| A27 | Fren pedalı BAS-BIRAK iki kez |
| A28 | Vites kolunu tüm vitesler arasında gezdir (fren pedalı basılı; şanzımanını not et) |

### B: Motor ÇALIŞIYOR, duruyor, el freni çekili

| No | Adım |
| --- | --- |
| B01 | Fotoğraf: gösterge panelindeki dış sıcaklığa şimdi bak ve fotoğrafla |
| B02 | MOTOR ÇALIŞTIR (anahtarı çevirmeden hemen önce dokun) |
| B03 | 20 sn rölantide bekle |
| B04 | Devri yaklaşık 2500'e çıkar, 5 sn tut, iki kez |
| B05 | Direksiyonu yavaşça sondan sona çevir |
| B06 | Geri vites 10 sn (fren pedalı basılı) |
| B07 | Silecek ve cam suyu bir kez |

### C: Sürüş (boş yol, yanında biri varsa o dokunsun; yoksa kalkmadan önce dokun)

| No | Adım |
| --- | --- |
| C01 | Yavaşça 30 km/s'e çık, 20 sn tut |
| C02 | 50 km/s'te 20 sn tut |
| C03 | Tam durana kadar fren yap |
| C04 | Kavşakta sola dön (sinyal açık) |
| C05 | Kavşakta sağa dön (sinyal açık) |
| C06 | Fotoğraf: gösterge panelindeki dış sıcaklığa şimdi bak ve fotoğrafla |

### D: Fabrika "Araç ayarı" uygulamasında sayfa gez (park halinde; hiçbir ayarı değiştirme)

| No | Adım |
| --- | --- |
| D01 | Aç: Sürüş modu |
| D02 | Aç: ESC |
| D03 | Aç: Lastikler |
| D04 | Aç: Servis |
| D05 | Aç: Işık |
| D06 | Aç: Açma ve kapama |
| D07 | Aç: Park ve manevra |
| D08 | Aç: Sürüş asistanları |
| D09 | Aç: Aynalar ve silecekler |
| D10 | Aç: Çoklu gösterge |
| D11 | Aç: Klima |
| D12 | Aç: Birimler |
| D13 | Birimler: sıcaklık C'den F'ye, sonra tekrar C'ye (zararsız ayar, geri al) |

### E: Kapanış

| No | Adım |
| --- | --- |
| E01 | MOTORU KAPAT (kapatmadan hemen önce dokun) |
| E02 | Kontağı kapat, 60 sn bekle |
| E03 | Sürücü kapısını aç, aracı kilitle |
| E04 | Canbus testi sonu (kaydı USB'ye aktar; telefon ve radyo grupları ayrı testtir) |

### P: Telefon (ayrı kısa test)

Bluetooth uygulamasının telefonla nasıl konuştuğunu görmek için. **Dikkat:** bu test Bluetooth modülünün kanalını fabrika
Bluetooth uygulamasıyla paylaşır ve Bluetooth'u bozabilir; o sırada arama beklemeyin, Bluetooth çalışmazsa üniteyi yeniden
başlatın. Önce Canbus test sayfasındaki **"Telefon kanalını dinle"** anahtarını aç, kayıt **açıkken uygulamayı yeniden
başlat**, telefonu bağla, sonra adımları sırayla yap. Kayda isim ve
numara **yazılmaz**: metinler yalnızca biçim olarak (`#` rakam, `a` harf, `*` Türkçe karakter) kaydedilir.

| No | Adım |
| --- | --- |
| P01 | Fabrika Bluetooth uygulamasını aç, telefon sayfasına gel |
| P02 | Rehber: aç ve kişilerin yüklenmesini bekle |
| P03 | Bir kişiyi aç (arama) |
| P04 | Arama geçmişi: aç |
| P05 | Tuş takımıyla bir numara ara, 10 sn sonra kapat |
| P06 | Gelen arama: cevapla, 10 sn konuş, araçtan kapat |
| P07 | Gelen arama: reddet |
| P08 | Gelen arama: çalsın, cevaplama |

### R: Radyo (ayrı kısa test)

Fabrika radyo uygulamasının ünite ile nasıl konuştuğunu (frekans, band, istasyon adı, hafıza) görmek için. Telefon
testi gibi: kayıt **açıkken uygulamayı yeniden başlat**, sonra fabrika radyo uygulamasını aç. Radyo yazıları (istasyon adı)
kayda olduğu gibi yazılır. R09 direksiyon tuşlarının hangi kodla geldiğini de gösterir.

| No | Adım |
| --- | --- |
| R01 | Fabrika radyo uygulamasını FM'de aç |
| R02 | Bir istasyonda 15 sn kal (frekansı ve adı not et) |
| R03 | Ekran tuşuyla sonraki istasyona geç |
| R04 | Ekran tuşuyla önceki istasyona geç |
| R05 | Hafızadaki 1. istasyona, sonra 2. istasyona dokun |
| R06 | Bir ince ayar adımı yukarı, bir adım aşağı |
| R07 | Bandı değiştir: FM1, FM2, AM, sonra tekrar FM1 |
| R08 | AF'yi bir kez aç-kapat (eski haline getir) |
| R09 | Direksiyon: sonraki, sonra önceki (radyo çalarken) |

## Test sonrası: ben ne yapacağım
1. Kaydı `MARK` satırlarına göre bölüp her adım için önceki ve sonraki 12 saniyedeki **çerçeve farklarını** çıkaracağım
   (hangi çerçeve, hangi bayt, hangi bit değişti).
2. Aynı çerçevede birden çok adım çakışıyorsa (örn. kapılar) bit eşlemesini çıkaracağım; tek başına anlam
   taşımayan değişimleri (sayaçlar, kalp atışı 255) eleyeceğim.
3. Bilgi servisi (AIDL) değerlerini aynı adımlarla eşleştireceğim (özellikle #59 ve #23: kabin sıcaklığı mı?).
4. D grubunda fabrikanın istediği ayar çerçevelerinden (30, 49, 70, 100, 103–105, 118, 133–136, 193) bit düzenini
   fabrika kodundaki çözümle karşılaştırıp doğrulayacağım.
5. Sonuçları `docs/CANBUS-CERCEVELER.md` dosyasına yazacağım: çerçeve numarası, bayt/bit, anlamı, güven düzeyi.
   Güvenilir çıkanlar launcher'a eklenir; her ekleme için sana sorarım.

## Bu belge ne için
Test senaryosunun kalıcı kaydıdır. Adım listesinin kaynağı uygulamanın `assets/canbus-test.txt` dosyasıdır; bu tablo
oradan üretilmiştir. Liste değişirse ikisi birlikte güncellenir.
