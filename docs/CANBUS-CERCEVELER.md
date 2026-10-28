# Canbus çerçeveleri — 22 Ekim 2026 testi (Golf 7, Hiworld, TW ünite)

Kaynak: `carlauncher-log(1).txt` (ateşleme açık/motor kapalı turu + motor çalışırken kısa tur). Çerçeveler `0x501` mesajının
`arg1` numarasıdır; baytlar 0'dan sayılır. Güven: **yüksek** = adımla birebir örtüştü ve ikinci bir kaynak (AIDL) doğruladı,
**orta** = tek adımla örtüştü, **düşük** = tahmin.

## Çözülenler

| Kaynak | Anlam | Kanıt | Güven |
|---|---|---|---|
| 26, bayt 1 üst yarı | Kapılar: `0x80` sürücü, `0x40` yolcu, `0x20` arka sol, `0x10` arka sağ (alt bit `0x01` hep 1) | A02–A05: `7F81`, `7F41`, `7F21`, `7F11` | yüksek (arka sağ: sıra varsayımı) |
| 18, bayt 2 | Aynı kapı bitleri | `00008063…`, `00004063…`, `00002063…`, `00001063…` | yüksek |
| 26, bayt 2 | Sinyal: `0x01` sağ, `0x02` sol, `0x04` dörtlü flaşör | A15–A17; AIDL #60 aynı değerleri veriyor (1, 2, 3) | yüksek |
| 18, bayt 6, bit `0x20` | Sürücü emniyet kemeri **takılı değil** (kemer takılınca 0) | A07; AIDL #12: 1 = takılı | yüksek |
| AIDL #5 | Farlar/park lambası açık (1/0) | A10 ile A14 arası 1 | yüksek |
| 17, bayt 5 | Gösterge aydınlatma seviyesi: ışıklar açılınca `0x20→0x39`, kapanınca `0x19→0x00` | A10 ve A13–A14 | orta |
| 119, bayt 0, bit 0 | Arka sis lambası | A13: açılışta `01`, kapanışta `00` | orta |
| 25, son iki bayt | Devir (16 bit): `0x0300` = 768 d/dk | 11:24 kaydı, AIDL #19 ile aynı büyüklük (789–894) | yüksek |
| AIDL #4 | Akü/şarj voltajı: motor kapalı 11,8–12,1 V, motor çalışırken 13,9–14,3 V | B02 | yüksek |
| 194 | Saat/tarih: bayt 1 saat, 2 dakika, 6 yıl−2000, 7 ay, 8 gün | araç saati ile aynı | yüksek |
| 20, 21 | Dakika sayacı her dakika 1 artıyor (örn. `0127→0128→0129`); mesafe/tüketim alanları tutarlı | A bölümü boyunca | yüksek |

## 28 Ekim 2026 kaydı ile eklenenler (motor çalışırken ~3 dk + radyo)

Kaynak: `carlauncher-log_7.txt`. Motor 17:38:30'da kapandı (akü voltajı 13,2 V'tan 12,5 V'a düştü).

| Kaynak | Anlam | Kanıt | Güven |
|---|---|---|---|
| 26 bayt 1 alt yarı ve 18 bayt 2 alt yarı, bit `0x08` | **Bagaj açık** (kapı bitlerinin altındaki bit) | A06: `7F89` / `00 00 88`, kapanınca `7F81` / `00 00 80`; tek denemede görüldü | orta |
| 18, bayt 8–9 | **Akü voltajı**: bayt 8 volt, bayt 9 onda biri (`0D 02` = 13,2 V, `0C 08` = 12,8 V, `0C 05` = 12,5 V). AIDL #4 ile aynı değerler | motor kapanırken 13,2 → 12,5 V inişi iki kaynakta birebir | yüksek |
| AIDL #4 | Motor çalışırken **13,1–13,2 V**, durunca 12,5 V; motorun çalıştığı voltajdan da anlaşılır | motor çalışırken ~3 dk boyunca 13,1/13,2 | yüksek |
| 25 ve AIDL #19 | Devir: `018C` = 396 d/dk, motor durduğu anda **tek kez** geldi. **Rölantide devir hiç gelmedi** (çerçeve 25 yok, AIDL #19 = 0) | 17:38:30–17:38:34 | yüksek |
| 201/1 (`0x201`) arg2 19 / 21 | Direksiyon "sonraki" / "önceki" tuşu; `0x9E01` arg1 3 / 4 aynı anda gelir | R09; radyoda yön doğru (sonraki → bir sonraki hafıza) | yüksek |
| 194 | Saat/tarih doğru çözülmüş: `00 11 24 80 00 01 1A 0A 1C 01` = 17:36, 28.10.2026 (bayt 6/7/8 = `1A 0A 1C` = yıl 26, ay 10, gün 28) | araç saati | yüksek |
| 17 ve 26, bayt 6–7 | İşaretli 16 bit, saniyede ~2 kez ±birkaç değer, direksiyon çevrilince `FFDB…0018` aralığında dalgalanıyor: büyük olasılıkla **direksiyon açısı** | A06 sırasında direksiyon oynadı | düşük |

**Yolcu emniyet kemeri (A08)**: dört işaret sırasında hiçbir çerçeve değişmedi. Yolcu koltuğu boşken kutu kemeri
göndermiyor olabilir (doluluk sensörü); dolu koltukla bir kez daha denemeye değer.
**Dış sıcaklık**: AIDL #59 17:35–17:38 arası 23,1–24,8 °C verdi; kümedeki dış sıcaklıkla karşılaştırılırsa kabin mi dış mı
olduğu anlaşılır (bu kayıtta fotoğraf/not yok).

## Çözülemeyenler

- **17 bayt 0, AIDL #14**: A09 (fren pedalı + el freni) sırasında değişiyorlar. Fren pedalı ile el freni ayrı ayrı
  denenmeden ayrılamaz. AIDL #14 olasılıkla ikisinden biri (0 = basılı/çekili). (18 bayt 8–9 artık biliniyor: voltaj.)
- **133** (`2020` ↔ `2040`), **116**, **117**, **22**, **23**, **24**, **250**: sadece iki değer arasında geziyorlar; eşleşen adım yok.
  133, ESC durumu olabilir (fabrika kodunda öyle), ama ESC o sırada değiştirilmedi.
- **118**: yaklaşık 12 sn'de bir `C0`/`80` arasında dönüyor; araçla ilgisi yok, kalp atışı gibi.
- **AIDL #59**: 23,8 / 23,7 °C (0,1 birim). Kabin sıcaklığı olabilir; kümede ya da klima ekranında gördüğün değerle
  karşılaştırılmadı.

## Eksik kalanlar

- Motor çalışırken (B grubu, klima, arka cam ısıtma) kayıtta **hiç veri çerçevesi yok**, yalnızca `255` kalp atışı ve AIDL
  satırları var. Cihaz saati o sırada 21 Ekim 09:26'ya geri sıçramış; sonra 11:24'te tüm çerçeveler tek seferde geldi.
  Motor çalışırken kutu yaklaşık 3 dakika çerçeve göndermemiş görünüyor (nedeni bilinmiyor).
- Yapılmayan ya da işaretlenmeyen adımlar: A06, A08, A12, A19–A22, A24, A26–A28, B01, B04, B05, B07, C bölümü, D03–D13, E01–E03.
- Dış sıcaklık yine bulunamadı. Adaylar: çerçeve 49 son bayt, çerçeve 24 bayt 4 (önceki kayıttan).
