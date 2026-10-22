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

## Çözülemeyenler

- **17 bayt 0, 18 bayt 8–9, AIDL #14**: A09 (fren pedalı + el freni) sırasında değişiyorlar; ışık adımlarında da oynuyorlar.
  Fren pedalı ile el freni ayrı ayrı denenmeden ayrılamaz. AIDL #14 olasılıkla ikisinden biri (0 = basılı/çekili).
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
