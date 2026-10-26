# Fabrika radyo uygulaması ve TWUtil (com.tw.radio)

Kaynak: fabrika `com.tw.radio` uygulamasının çözülmüş kodu (kod okuma; **araçta henüz doğrulanmadı**). Radyo, Canbus ve
Bluetooth gibi TWUtil kanalıyla konuşur. Aşağıdaki "güven" sütunu koddan çıkan kesinliği gösterir; kayıt (R grubu)
sonrası güncellenecek.

**Kanal:** fabrika radyo uygulaması tuner'a kendi TWUtil nesnesiyle (`new TWUtil(1)`, modül 1) bağlanır; araç uygulaması
`new TWUtil()` ile (Canbus) ve Bluetooth uygulaması `new TWUtil(8)` + 115200 baud ile (modül 8). Bizim ana dinleyicimiz
araç uygulamasıyla aynı (modül 0). Radyo ve telefon mesajlarını almak için kayıt açıkken ayrıca modül 1 ve modül 8 açılır
(`TwProbe.openExtras`); Bluetooth modülünün kendi mesajlarında `what` 3–173 arası komut numarasıdır.

Radyonun açtığı mesaj kimlikleri: `265, 266, 274, 513, 515, 769, 1025, 1026, 1028, 1029, 1030, 0x9E00 (-25088), 0x9E01 (-25087)`.
Radyoya özgü olanlar: `265, 1025, 1026, 1028, 1029, 1030, -25088, -25087`.

## Gelen mesajlar (ünite → uygulama)

| Kimlik | arg1 | Anlam | Güven |
| --- | --- | --- | --- |
| 0x401 (1025) | 0 | `arg2` bayrak maskesi (bit 2, 4, 8, 16, 128: stereo/yerel/DX türü göstergeler; adları belirsiz) | düşük |
| 0x401 | 1 | Band: `arg2` = 0, 1 FM, 2 AM (AM'de birim kHz) | orta |
| 0x401 | 2 | **Frekans**: `arg2`; FM'de `/100` = MHz (10230 → 102,30), AM'de kHz; `obj` (String) istasyon adı | orta |
| 0x401 | 3 | Sinyal seviyesi `arg2` | orta |
| 0x401 | 4 | Seçili hafıza (preset) sırası `arg2`, yoksa -1 | orta |
| 0x404 (1028) | bayrak | `arg1` bayrakları: bit 4 REG, 32 TA, 64 AF, 2 ve 128 TP/TA göstergesi; üst 16 bit mevcut frekans değeri; `arg2` alt bayt sinyal, `obj` RDS adı | düşük |
| 0x405 (1029) | – | `obj` String: RDS metni | düşük |
| 0x406 (1030) | 0–10 | Bölge ve band aralıkları: 0 bölge, 1/2/5 FM aralığı, 3/4/6 AM aralığı, 7 bayrak, 8–10 ek aralıklar | düşük |
| 0x402 (1026) | – | Hafıza listesi (ad, frekans) | bilinmiyor |

## Giden komutlar (uygulama → ünite)

| Komut | Anlam | Güven |
| --- | --- | --- |
| `write(0x401, 1, 0)` / `(0x401, 1, 1)` | Otomatik arama: aşağı / yukarı (seek) | orta |
| `write(0x401, 2, 0)` / `(0x401, 2, 1)` | İnce ayar: bir adım aşağı / yukarı | orta |
| `write(0x401, 5, band)` | Band seç (FM1, FM2, AM) | orta |
| `write(0x401, 0, 0)` | Tarama ya da otomatik kaydetme (ne olduğu kesin değil) | düşük |
| `write(0x401, 4, 0/1)` | Bir bayrağı değiştirir (`_o & 8`) | düşük |
| `write(0x402, sıra, band)` | Hafızadaki istasyona geç | orta |
| `write(0x402, 255, band)` | Hafıza listesini iste | orta |
| `write(0x404, 0/1/3, 0/1)` | AF / TA / REG aç-kapat | düşük |
| `write(0x404, 255)` | Durumu iste | orta |

## Kullanım planı
1. R grubu kaydıyla yukarıdaki tabloyu doğrula (özellikle frekans birimi, band numaraları, hafıza listesi biçimi).
2. Doğrulanan alanları `RadioState` olarak çöz, Radyo ekranında göstergeye bağla (yalnızca okuma).
3. Sonra komutları (arama, hafıza) "Üniteye komut göndermeye izin ver" anahtarının arkasına ekle.
4. Direksiyon tuşlarının ters çalışması (Sonraki/Önceki) için R09 ve Bluetooth kaydındaki `0x201` satırlarına bak.
