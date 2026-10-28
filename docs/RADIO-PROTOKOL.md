# Fabrika radyo uygulaması ve TWUtil (com.tw.radio)

Kaynak: fabrika `com.tw.radio` uygulamasının çözülmüş kodu ve **28 Ekim 2026 araç kaydı** (R01–R05 ve R09 adımları). Radyo,
Canbus ve Bluetooth gibi TWUtil kanalıyla konuşur. "Güven" sütunu: **kayıtla doğrulandı** / koddan (kayıtta görülmedi).

**Kanal:** fabrika radyo uygulaması tuner'a kendi TWUtil nesnesiyle (`new TWUtil(1)`, modül 1) bağlanır; araç uygulaması
`new TWUtil()` ile (Canbus) ve Bluetooth uygulaması `new TWUtil(8)` + 115200 baud ile (modül 8). Bizim ana dinleyicimiz
araç uygulamasıyla aynı (modül 0). Radyo ve telefon mesajlarını almak için kayıt açıkken ayrıca modül 1 ve modül 8 açılır
(`TwProbe.openExtras`); Bluetooth modülünün kendi mesajlarında `what` 3–173 arası komut numarasıdır.

Radyonun açtığı mesaj kimlikleri: `265, 266, 274, 513, 515, 769, 1025, 1026, 1028, 1029, 1030, 0x9E00 (-25088), 0x9E01 (-25087)`.
Radyoya özgü olanlar: `265, 1025, 1026, 1028, 1029, 1030, -25088, -25087`.

## Gelen mesajlar (ünite → uygulama)

| Kimlik | arg1 | Anlam | Güven |
| --- | --- | --- | --- |
| 0x401 (1025) | 0 | `arg2` bayrak maskesi. **Arama sürerken 64, bitince 0** (kayıtta her aramada 64 → 0). Diğer bitler (2, 4, 8, 16, 128) stereo/yerel/DX türü göstergeler; adları belirsiz | kayıtla doğrulandı (64) |
| 0x401 | 1 ve 5 | Band: `arg2` = 0, 1 FM, 2 AM. Fabrika uygulaması arg1 = 1'i okur; kayıtta aramadan sonra **`5/0`** geldi (FM1), `1` hiç görülmedi. İkisi de kabul edilir | 5/0 kayıtla doğrulandı |
| 0x401 | 2 | **Frekans**: `arg2`; FM'de 10 kHz biriminde (8955 → 89,55 MHz), AM'de kHz; `obj` (String) varsa istasyon adı. Arama sürerken yarım saniyede bir ara frekanslar gelir, ince adım 5 (0,05 MHz) | kayıtla doğrulandı |
| 0x401 | 3 | Sinyal seviyesi `arg2` (kayıtta hep 0) | kayıtla doğrulandı (görüldü) |
| 0x401 | 4 | Seçili hafıza sırası `arg2`; FM'de **0–14 arası** görüldü, yoksa -1 | kayıtla doğrulandı |
| 0x404 (1028) | bayrak | `arg1` bayrakları (bit 4 REG, 32 TA, 64 AF, 2 ve 128 TP/TA göstergesi); **üst 16 bit RDS PI kodu** (0x322D, 0x32A1; frekans değil); `arg2` alt bayt sinyal; `obj` RDS adı (PS). Ad titrer: "A HABER", "A HABEa", "A HABER@" | kayıtla doğrulandı |
| 0x405 (1029) | – | `obj` String: RDS metni (kayıtta gelmedi) | koddan |
| 0x406 (1030) | 0–7 | Aralıklar: 0 bölge (1), 1 FM üst sınır (10800), 2 FM alt sınır (8750), 3 AM üst (1620 kHz), 4 AM alt (522 kHz), 5 FM adım (5 = 0,05 MHz), 6 AM adım (9 kHz), 7 bayrak (1). Radyo uygulaması açılınca bir kerede gelir | kayıtla doğrulandı |
| 0x402 (1026) | `slot \| band<<8 \| PI<<16` | Hafıza listesinin bir satırı: `arg2` frekans, `obj` ad. Fabrika uygulaması her bantta **18 satır** tutar (`Ib[18]`). `write(0x402, 255, band)` ile istenir; kayıtta istenmediği için gelmedi | koddan |
| 0x301 (769) | – | `arg1` = 1: etkin kaynak radyo; radyodan çıkınca 0 | kayıtla doğrulandı |
| 0x109 (265) | – | `byte[0]` bölge (kayıtta 1) | kayıtla doğrulandı |
| 0x10A (266) | 0 | `obj` ünite protokol dizesi `T13.1.1-300-114-A6939D-2` | kayıtla doğrulandı |

### Direksiyon tuşları (radyo kanalından)
Direksiyondaki "sonraki" tuşuna basınca `0x201` (513) `arg1 = 1, arg2 = 19` ve `0x9E01 arg1 = 3`; "önceki" tuşunda `arg2 = 21` ve
`0x9E01 arg1 = 4` geldi (R09). Radyoda sonraki → bir sonraki hafıza (1 → 2), önceki → bir önceki (2 → 1): **yön doğru**.
Müzikteki ters yön bu yüzden tuş kodundan değil müzik tarafından geliyor (müzik açıkken ayrı kayıt gerekir). `0x201/1 = 18`
başka bir tuş (kaynak/mod); basılınca radyo uygulaması açıldı ve durum bilgileri (0x9E00, 0x301, 0x406…) yağdı.

## Giden komutlar (uygulama → ünite)

| Komut | Anlam | Güven |
| --- | --- | --- |
| `write(0x401, 1, 0)` / `(0x401, 1, 1)` | Otomatik arama: **0 = sonraki (yukarı), 1 = önceki (aşağı)**. Fabrika uygulamasında "next" `(1,0)`, "prev" `(1,1)`; kayıtta launcher `(1,1)` gönderince frekans düştü (8955 → 8800). İlk sürümde yön ters yazılmıştı | kayıtla doğrulandı |
| `write(0x401, 2, 0)` / `(0x401, 2, 1)` | İnce adım: 0 yukarı, 1 aşağı (fabrikada `step_right` = 0, `step_left` = 1) | koddan |
| `write(0x401, 5, band)` | Band seç (0 FM1, 1 FM2, 2 AM) | koddan |
| `write(0x401, 255)` | **Tuner durumunu iste** (band, frekans, hafıza); fabrika uygulaması açılışta gönderir, ilk sürümde eksikti | koddan |
| `write(0x401, 0, 0)` | Tarama ya da otomatik kaydetme (ne olduğu kesin değil) | düşük |
| `write(0x401, 4, 0/1)` | Bir bayrağı değiştirir | düşük |
| `write(0x402, sıra, band)` | Hafızadaki istasyona geç | koddan |
| `write(0x402, 255, band)` | Hafıza listesini iste | koddan |
| `write(0x404, 0/1/3, 0/1)` | AF / TA / REG aç-kapat | düşük |
| `write(0x404, 255)`, `write(0x301, 255)` | RDS durumunu ve etkin kaynağı iste | kayıtla doğrulandı (cevap geldi) |

Fabrika uygulamasının açılış dizisi: `265/255, 266/255, 769/255, 274/255, 1030/0, 1025/255, 1028/255, 515/255`.

## Launcher'daki sürüm (`radio/TwRadio.java`)
Fabrika uygulaması gibi kendi kanalını (`TWUtil(1)` + yukarıdaki kimlikler) açar, mesajları çözer ve `RadioController`
üzerinden Radyo ekranına verir. Komutlar yalnızca "Üniteye komut göndermeye izin ver" açıkken gider. 28 Ekim kaydından sonra:
- **Arama ve ince adım yönü düzeltildi** (0 = sonraki, 1 = önceki).
- Açılışta `0x401, 255` de istenir; ilk kayıtta frekans, aramaya kadar hiç gelmemişti.
- **Hafıza kareleri 18 slottur** (6'lı üç sayfa), bant başına ayrı değil: karelerin her biri ünitenin o anki bandındaki slottur.
  Band bilinince `write(0x402, 255, band)` ile liste istenir; gelen satırlar karelerde ad ve frekans olarak görünür.
- RDS adı iki kez üst üste görülmeden değişmez (titreme süzgeci).
- Band mesajı `5/0` da kabul edilir.

## Sıradaki adımlar
1. Yeni kayıtta `0x402` satırlarını görüp liste biçimini doğrulamak (özellikle `band` baytının anlamını).
2. İstasyon listesi (Stations) ve elle ayar (Manual) tuşlarını açmak: elle ayar için ünitede "frekansa git" komutu görülmedi,
   ince adımla ilerlemek gerekir (87,5 → 108 MHz arası 0,05'lik adımlar).
3. Müzik açıkken direksiyon tuşu kaydı (müziğin ters yönünü ayırmak için).
