# Sürüm notları / Release notes (TR)

Bu fork (burakhank6078/TheCarLauncherTRTW), LazarC11/TheCarLauncher projesinin VW Golf 7 (MQB) ve TW
platformlu (Allwinner A133) head unit için uyarlanmış halidir. Her yayında yeni özellikler bu dosyanın
en üstüne eklenir; GitHub sürüm sayfası bu en üst bölümü gösterir.

## Yayın sonrası kontrol listesi
1. `git fetch upstream && git merge upstream/main`
2. `powershell -ExecutionPolicy Bypass -File tools/check-translations.ps1` — eksik Türkçe metinleri listeler
3. Eksikleri `app/src/main/java/com/thecarlauncher/mib/Tr.java` içine ekle
4. Aşağıya yeni sürümün bültenini (yeni özellikler + düzeltmeler) Türkçe yaz
5. Commit

---

## 22 Ekim 2026
**Araç durumu sayfası canlandı**
- Araç → Araç durumu sayfasında aracın o anki hali görünür: **açık kapı uyarıları** (sürücü, yolcu, arka sol, arka sağ),
  **sürücü emniyet kemeri** (yalnızca motor çalışırken), **anlık akü voltajı** (motor kapalıyken 11,8 V altı, çalışırken
  13 V altı ya da 15 V üstü uyarı verir), farlar açıksa bilgi satırı ve **şasi numarası**.
- Canlı satırlar için Ayarlar → Araç → "Yolculuk verilerini OBD'den al" açık olmalı; şasi numarasını ilk okumada almak için
  "Üniteye komut göndermeye izin ver" de açık olmalı (sonra kaydedilir).
- Bagaj kapağı ve yolcu kemeri henüz yok; bunların çerçevesi bir sonraki testte aranacak.
- **Telefon testi hazırlığı**: tanılama kaydı açıkken (uygulama o şekilde başlatılmışsa) fabrika Bluetooth uygulamasının
  telefon mesajları da dinlenir; kayda isim ve numara yazılmaz, yalnızca biçimleri (`#` rakam, `a` harf). Test senaryosuna
  P grubu eklendi. Şasi numarası artık yedeğe girmeyen bir dosyada saklanır.
- Canbus test kaydından çözülen çerçeveler `docs/CANBUS-CERCEVELER.md` dosyasında.

## 21 Ekim 2026
**Araç verileri**
- Gezi sayfaları (*Başlangıçtan beri*, *Yakıt aldıktan sonra*, *Uzun süreli*) artık aracın Canbus
  çerçevelerinden doluyor: mesafe, süre, ortalama hız, ortalama tüketim, seyir aralığı.
  Ayarlar → Araç → "Gezi verilerini OBD'den al" açık olmalı. Geçersiz değerler `---` görünür,
  15 dakikadan eski değerler gizlenir.

- **Bakım bilgisi araçtan**: Araç → Bakım sayfasının üstünde aracın kendi "Bakım" ve "Yağ bakımı" hatırlatması
  (kalan gün ve km, gecikmişse "gecikti") gösterilir. Okuma için "Üniteye komut göndermeye izin ver" açık olmalı;
  elle girilen kilometre, yağ ve muayene kayıtları olduğu gibi durur.
- **Canbus test senaryosu**: Ayarlar → Tanılama kaydı → Canbus test senaryosu; her adıma dokununca kayda saat işareti
  düşer (`docs/CANBUS-TEST-SENARYOSU.md`).

**Ambiyans şeridi (Raise LAMP&FRGN, Bluetooth LE)**
- Daha hızlı arama, arama sonunda neden bulunamadığını anlatan uyarı (şerit telefona bağlıyken görünmez).
- Bağlantı takılması düzeltildi; birden fazla bağlantı nesnesi artık temizleniyor.
- Ambiyans ekranındaki güneş tuşu **%100'den %1'e** parlaklık listesi açar (1, 2, 3, 4, 5, 7, 10, 20 … 100).
  Ekranın kendi parlaklığına dokunulmaz.
- BLE durum metinleri Türkçe.
- **Otomatik yeniden bağlanma**: şerit kapalıysa ya da menzil dışındaysa launcher 5 sn, 10 sn ... en çok 1 dk
  aralıkla yeniden dener; 25 sn içinde yanıt gelmeyen bağlantı bırakılıp tekrar denenir.

**Bluetooth ve medya**
- TW cihazlarda *Ayarlar → Bluetooth* fabrika Bluetooth uygulamasını açar; yalnızca QF cihazlara ait
  eşleştirme sayfaları gizlendi.
- Bluetooth müzikte albüm kapağı sanatçı ve şarkı adıyla internetten bulunur (Ayarlar'dan kapatılabilir).

**Araçlar ve belgeler**
- Ayarlar → Tanılama kaydı: **10 saniye sonra ekran görüntüsü** (USB veya İndirilenler, sokak adı
  varsayılan olarak gizlenir). README için gerçek cihaz görüntüleri bununla alınır.
- GitHub sürüm sayfası artık bu dosyanın en yeni bölümünü sürüm notu olarak gösterir.
- README fork'a göre yeniden yazıldı; upstream'in README'si `README-upstream.md` olarak duruyor.

**Kalite**
- Üç kod incelemesi turunda bulunan hatalar düzeltildi (çökme, iş parçacığı, bağlantı yarışları).
- QF cihazlara ait arka plan işleri (Goodocom, QF yayınları, MCU radyo dinleyici) TW cihazda artık
  başlatılmıyor.
- Birim testler (çerçeve çözümü, BLE mesaj çerçevesi) ve çeviri denetimi GitHub derlemesinde çalışıyor.

## 20 Ekim 2026
- **Bluetooth medya tuşları** (sonraki, önceki, oynat/durdur) artık fabrika Bluetooth uygulamasına gerçekten
  ulaşıyor (TWUtil üzerinden). Ayarlar → Araç → "Üniteye komut göndermeye izin ver" açık olmalı.
- **Lastik basıncı sıfırlama** launcher içinden (onaylı, aynı anahtarla).
- Aracın bilgi servisi (voltaj, devir, seyir aralığı …) okunur ve tanılama kaydına yazılır.
- **Ambiyans şeridi (BLE)**: ilk sürüm; renk ve seviye şeride gönderilir.
- **Türk bayrağı** resmi bayrak oranlarıyla yeniden çizildi.
- Fabrika MIB 2 terimleri (Muayene, Başlayın vb.) düzeltildi.

## 19 Ekim 2026
- **Lastik basıncı sayfası** (Araç → sağ ok).
- **MIB 2 gezi sayfaları**: tüm sayfa arka plan resmi (1024×444 önerilir), fabrika düzeni.
- **Tanılama kaydedici**: Ayarlar → Tanılama kaydı; kaydı USB'ye aktarır.

## 17 Ekim 2026
- Ayarlar → Araç'tan fabrika **Araç ayarı** uygulamasını açma.
- TW platformu için salt okunur Canbus dinleyici.
- Çeviri denetim betiği (`tools/check-translations.ps1`).

## İlk sürüm (17 Ekim 2026)
- Ayarlar → Dil bölümüne **Türkçe** eklendi (bayrak, sesli navigasyon, sokak adları dahil).
- Arayüz VW MIB 2 görünümünü korur; yalnızca metinler Türkçeleşti.
