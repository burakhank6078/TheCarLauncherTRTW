# TheCarLauncher TR

**VW Golf 7 (MQB) ve TW platformlu head unit'ler için Türkçe, VW MIB 2 görünümlü launcher.**

Bu depo, [LazarC11/TheCarLauncher](https://github.com/LazarC11/TheCarLauncher) projesinin bir
fork'udur ve proje sahibinin izniyle geliştirilmektedir. Orijinal proje fabrika bilgi-eğlence
sistemi görünümünde bir ana ekran uygulamasıdır; bu fork onu **TW (TopWay) tabanlı Android head
unit'e** ve **Golf 7 Canbus verisine** uyarlar, arayüzü **Türkçeleştirir**.

> *English:* A fork of LazarC11/TheCarLauncher for a VW Golf 7 (MQB) with a TW-platform Android head
> unit (Allwinner A133, Hiworld Canbus box). Turkish UI, MIB 2 look kept, Canbus trip data, tyre
> pressure, Bluetooth media keys, ambient light strip over BLE. The original project's README is in
> [README-upstream.md](README-upstream.md).

Kişisel, ticari olmayan bir hobi projesidir; hiçbir araç veya head unit üreticisiyle bağlantısı
yoktur ([Telif ve lisanslar](#telif-ve-lisanslar)).

## İçindekiler
- [Test edilen donanım](#test-edilen-donanım)
- [Bu fork'un eklediği özellikler](#bu-forkun-eklediği-özellikler)
- [Durum: neler çalışıyor, neler yolda](#durum-neler-çalışıyor-neler-yolda)
- [Kurulum](#kurulum)
- [İlk ayarlar](#ilk-ayarlar)
- [Güvenlik: araca komut gönderme](#güvenlik-araca-komut-gönderme)
- [Sorun olursa: tanılama kaydı ve ekran görüntüsü](#sorun-olursa-tanılama-kaydı-ve-ekran-görüntüsü)
- [Derleme](#derleme)
- [Upstream ile birleştirme ve çeviri](#upstream-ile-birleştirme-ve-çeviri)
- [Ekran görüntüleri](#ekran-görüntüleri)
- [Telif ve lisanslar](#telif-ve-lisanslar)

## Test edilen donanım

| | |
| --- | --- |
| Araç | VW Golf 7 (MQB) |
| Head unit | Allwinner A133 (`ceres-b6`), Android 10, TW platformu (`com.tw.*`) |
| Canbus | Hiworld Golf 7 kutusu (fabrika uygulaması: Araç ayarı `com.tw.car`) |
| Ambiyans şeridi | Raise LAMP&FRGN (Bluetooth LE) |

Aynı TW yazılımını kullanan başka head unit'lerde de çalışması beklenir; denenmemiştir.

## Bu fork'un eklediği özellikler

- **Türkçe arayüz**: Ayarlar → Dil. MIB 2 görünümü aynen korunur; yalnızca metinler Türkçedir
  (sesli navigasyon ve sokak adları dahil). Çeviriler `mib/Tr.java` içindedir.
- **Gezi sayfaları (Canbus)**: *Başlangıçtan beri*, *Yakıt aldıktan sonra*, *Uzun süreli*; aracın
  kendi verisiyle: mesafe, süre, ortalama hız, ortalama tüketim, seyir aralığı. Tüm sayfa arka plan
  resmi seçilebilir (1024×444 önerilir).
- **Lastik basıncı sayfası** ve launcher içinden **lastik basıncı sıfırlama** (onaylı).
- **Bluetooth müzik**: şarkı bilgisi fabrika Bluetooth uygulamasının medya oturumundan gelir;
  sonraki / önceki / oynat-durdur tuşları fabrika uygulamasına gerçekten ulaşır; albüm kapağı
  internetten bulunur (kapatılabilir).
- **Ambiyans şeridi**: Raise LAMP&FRGN şeridi Bluetooth LE ile doğrudan launcher'dan kontrol edilir.
  Renk ve **%100'den %1'e** parlaklık listesi (ekranın kendi parlaklığına dokunulmaz).
- **Fabrika uygulamalarına kısayollar**: Araç ayarı (`com.tw.car`) ve Bluetooth (`com.tw.bt`).
- **Tanılama kaydedici** ve **uygulama içi ekran görüntüsü** (aşağıda).

Ayrıntılı geçmiş: [CHANGELOG-TR.md](CHANGELOG-TR.md).

## Durum: neler çalışıyor, neler yolda

| Özellik | Durum |
| --- | --- |
| Türkçe arayüz, MIB 2 görünümü | Çalışıyor |
| Medya (USB / Bluetooth), şarkı bilgisi, kapak, tuşlar | Çalışıyor |
| Gezi sayfaları (Canbus), lastik basıncı | Çalışıyor |
| Ambiyans şeridi (BLE) | Çalışıyor (telefonun Bluetooth'u kapalı olmalı) |
| Navigasyon (OpenStreetMap) | Upstream'den, internet / çevrimdışı harita |
| Radyo | **Yolda**: yerleşik FM ekranı QF tuner içindir, TW cihazda çalışmaz; fabrika radyosuna bağlanılacak |
| Arama ekranı (rehber, arama kaydı) | **Yolda** |
| Sürüş modu / ESC ayarları | **Yolda** (fabrika uygulamasındaki protokol çözüldü) |
| Dış sıcaklık | Şimdilik internetten (hava durumu); Canbus'ta bulunamadı |

Upstream'in QF cihaza özel parçaları (Goodocom Bluetooth eşleştirme sayfaları, QF tuner) TW
cihazlarda gizlenir veya kullanılmaz.

## Kurulum

1. [Releases](../../releases) sayfasındaki en üst sürümden **TheCarLauncher.apk** dosyasını indirin.
   Her push otomatik bir sürüm üretir (`build-N`); sürüm notu bu dosyadaki en yeni bölümdür.
2. USB belleğe koyup cihazda açın, gerekirse *Bilinmeyen kaynaklardan kurulum*a izin verin.
   Eskisinin üzerine kurulur, ayarlar korunur.
3. Home tuşuna basıp **Car Launcher**'ı seçin → *Her zaman*.

Android 5.0 veya üzeri gerekir.

## İlk ayarlar

| Ayar | Nerede | Ne işe yarar |
| --- | --- | --- |
| Dil → Türkçe | Ayarlar → Dil | Arayüz dili |
| Gezi verilerini OBD'den al | Ayarlar → Araç | Gezi sayfalarını Canbus ile doldurur (yalnızca okur) |
| Üniteye komut göndermeye izin ver | Ayarlar → Araç | Lastik sıfırlama ve Bluetooth tuşları için gerekir |
| Bildirim erişimi | Medya → Kaynak | Bluetooth şarkı bilgisi |
| Bluetooth (yakındaki cihazlar) / Konum | İlk açılışta istenir | Ambiyans şeridini aramak için |
| Ambiyans şeridi (Bluetooth LE) | Ayarlar | Telefonun Bluetooth'unu kapatıp şeridi arayın |

## Güvenlik: araca komut gönderme

Varsayılan olarak launcher araca ve üniteye **hiçbir şey göndermez**; yalnızca dinler. Komut gönderen
her özellik (lastik sıfırlama, Bluetooth tuşları) **"Üniteye komut göndermeye izin ver"** anahtarının
arkasındadır ve fabrika uygulamasının kullandığı mesajların aynısını kullanır. Araç için kritik
ayarlar (ör. ESC) eklenirse ayrıca onay penceresi ile sorulacaktır.

## Sorun olursa: tanılama kaydı ve ekran görüntüsü

*Ayarlar → Tanılama kaydı*:

- **Tanılama kaydı**: açıkken sistem bilgisini, hataları, Canbus çerçevelerini, Bluetooth ve BLE
  olaylarını (konum olmadan) kaydeder. *Kaydı USB'ye aktar* ile dosyayı alın.
- **10 saniye sonra ekran görüntüsü**: dokunun, istediğiniz ekranı açın; 10 saniye sonra launcher
  penceresinin görüntüsü USB belleğe ya da İndirilenler klasörüne (`TheCarLauncher-screenshots`)
  kaydedilir. Sokak adı varsayılan olarak gizlenir. Harita ve video gibi ayrı yüzeyler görüntüye
  girmez.

## Derleme

```
./gradlew assembleRelease
```

JDK 17 ve Android SDK (API 34) gerekir. GitHub Actions her push'ta APK üretir ve sürüm olarak yayınlar.

## Upstream ile birleştirme ve çeviri

Türkçe metinler yalnızca `Tr.java` içindedir, İngilizce metne göre eşleşir; upstream'in `s("Sırpça", "English")`
çağrılarına dokunulmaz (birleştirme temiz kalsın). Her upstream sürümünden sonra:

```
powershell -ExecutionPolicy Bypass -File tools/check-translations.ps1
```

eksik çevirileri listeler; eklenir, bülten `CHANGELOG-TR.md` dosyasına yazılır. Ayrıntı: [CLAUDE.md](CLAUDE.md).

## Ekran görüntüleri

Bu cihazdan alınan görüntüler eklenecek. Upstream'in İngilizce görüntüleri
[README-upstream.md](README-upstream.md) içinde bulunur.

## Telif ve lisanslar

Simge, döşeme ve araç resimleri proje sahibi tarafından bu kişisel, ticari olmayan launcher için
sağlanmıştır; sahiplerinin malıdır. Tüm markalar sahiplerine aittir. Yazı tipi Source Sans 3 (SIL OFL),
harita ve veri kaynakları ve diğer lisanslar için bkz. [README-upstream.md](README-upstream.md#artwork-and-licences)
ile `NOTICE` ve `licenses/` klasörü.
