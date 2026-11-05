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

**Araç ve Canbus**
- **Araç durumu**: kapılar, bagaj, sürücü kemeri, akü voltajı ve farlar canlı gösterilir. Sayfanın altındaki
  üç tuş: **Servis**, **Canlı veri** ve **Ambijent**.
- **Gösterge paneli** (Araç → Canlı veri): hız, motor devri, akü voltajı, bu yolculuğun mesafesi, sürüş puanı ve
  dinlenme voltajı ortalaması.
- **Gezi sayfaları (Canbus)**: *Başlangıçtan beri*, *Yakıt aldıktan sonra*, *Uzun süreli*; aracın kendi verisiyle:
  mesafe, süre, ortalama hız, ortalama tüketim, seyir aralığı. Tüm sayfa arka plan resmi seçilebilir (1024×444 önerilir).
- **Yolculuk geçmişi**: aylık toplamlar ve son yolculuklar (mesafe, süre, tüketim, girilen yakıt fiyatına göre tutar,
  sürüş puanı). GPS ile arka planda da kaydedilir.
- **Kontak kapanınca yolculuk özeti** ve **sürüş puanı** (sert fren ve sert kalkış sayılır), MIB açılır penceresinde.
- **Uyarılar** (MIB açılır penceresi): sürüşte açık kapı / bagaj ve bağlanmamış sürücü kemeri; zayıf akü; şarj sorunu.
- **Bekleme saati**: kontak kapandıktan birkaç saniye sonra MIB ünitelerindeki gibi siyah ekranda büyük saat.
  Dokununca launcher döner; ekran parlaklığına dokunulmaz.
- **Aracın kendi ayarları** (Ayarlar → Navigasyon ve araç): fabrikadaki "Araç ayarı" uygulamasının **Işıklar**,
  **Açma ve kapama** ve **Aynalar ve silecekler** sayfaları launcher içinden okunur ve (komut izni açıksa, onaydan sonra)
  değiştirilir.
- **Lastik basıncı sayfası** ve launcher içinden **lastik basıncı sıfırlama** (onaylı).
- **Otomatik gece haritası**: farlar yanınca harita gece görünümüne geçer.

**Medya, radyo, ışık**
- **Radyo**: fabrika radyonun tunerini aynalar ve kumanda eder: frekans, RDS adı, 18 hafıza × 3 sayfa, band tuşu,
  istasyon listesi, elle ayar, hafızaya kaydetme ve istasyon logoları. Ses fabrika radyodan çalmaya devam eder.
- **Bluetooth müzik**: şarkı bilgisi fabrika Bluetooth uygulamasının medya oturumundan gelir; sonraki / önceki /
  oynat-durdur tuşları fabrika uygulamasına gerçekten ulaşır; albüm kapağı internetten bulunur (kapatılabilir).
- **Ambiyans şeridi**: Raise LAMP&FRGN şeridi Bluetooth LE ile doğrudan launcher'dan kontrol edilir; **iki bölge**
  ayrı renk ve parlaklıkla sürülür (ekranın kendi parlaklığına dokunulmaz).

**Genel**
- **Türkçe arayüz**: Ayarlar → Dil. MIB 2 görünümü aynen korunur; yalnızca metinler Türkçedir
  (sesli navigasyon ve sokak adları dahil). Çeviriler `mib/Tr.java` içindedir.
- **Uygulamalar** ekranında uzun basış menüsü (MENU'ye ekle, telefon / radyo uygulaması yap, bilgi, kaldır);
  **Telefon** karosu fabrika Bluetooth uygulamasını (`com.tw.bt`) açar.
- **Ayar yedeği**: ayarlar USB'ye tek dosya olarak kaydedilir ve geri yüklenir.
- **Tanılama kaydedici** ve **uygulama içi ekran görüntüsü** (aşağıda).

Ayrıntılı geçmiş: [CHANGELOG-TR.md](CHANGELOG-TR.md). Protokol notları: [docs/](docs/).

## Durum: neler çalışıyor, neler yolda

| Özellik | Durum |
| --- | --- |
| Türkçe arayüz, MIB 2 görünümü | Çalışıyor |
| Medya (USB / Bluetooth), şarkı bilgisi, kapak, tuşlar | Çalışıyor (direksiyon "sonraki / önceki" tuşları ara sıra gitmeyebilir; araştırılıyor) |
| Gezi sayfaları, yolculuk geçmişi, lastik basıncı | Çalışıyor |
| Radyo (fabrika tuneri) | Çalışıyor; fabrika radyonun logo veritabanı henüz okunmuyor |
| Ambiyans şeridi (BLE) | Çalışıyor (telefonun Bluetooth'u kapalı olmalı) |
| Kapı / kemer uyarısı | Çalışıyor |
| Kontak olayları: yolculuk özeti, akü uyarıları, bekleme saati, gösterge paneli | **Yeni**, araçta denenecek |
| Aracın kendi ayarları: okuma (Işıklar) | Çalışıyor |
| Aracın kendi ayarları: değiştirme, Açma ve kapama, Aynalar ve silecekler | **Yeni**, araçta denenecek |
| Birimler, Park ve manevra, Çok işlevli ekran, Sürücü asistanı sayfaları | **Yolda** (fabrika uygulamasında çözüldü) |
| Sürüş modu / ESC ayarları | **Yolda** |
| Navigasyon (OpenStreetMap) | Upstream'den, internet / çevrimdışı harita |
| Arama ekranı (rehber, arama kaydı) | **Yolda**; şimdilik fabrika Bluetooth uygulamasını açar |
| Dış sıcaklık | Şimdilik internetten (hava durumu); Canbus'ta bulunamadı |
| Soğutma suyu / yağ sıcaklığı | Canbus kutusu vermiyor; olası yol OBD-II (ELM327) adaptörü, henüz yok |

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
| Gezi verilerini OBD'den al | Ayarlar → Araç | Gezi, araç durumu, uyarılar ve kontak olayları için araç verisini okur |
| Üniteye komut göndermeye izin ver | Ayarlar → Araç | Lastik sıfırlama, Bluetooth / radyo kumandası ve aracın kendi ayarlarını okuma-değiştirme için gerekir |
| Araç bildirimleri, Kontak kapalıyken saat ekranı | Ayarlar → Araç | Yolculuk özeti, akü uyarıları ve bekleme saati (ikisi de açık gelir) |
| Bildirim erişimi | Medya → Kaynak | Bluetooth şarkı bilgisi |
| Bluetooth (yakındaki cihazlar) / Konum | İlk açılışta istenir | Ambiyans şeridini aramak için |
| Ambiyans şeridi (Bluetooth LE) | Ayarlar | Telefonun Bluetooth'unu kapatıp şeridi arayın |

## Güvenlik: araca komut gönderme

Varsayılan olarak launcher araca ve üniteye **hiçbir şey göndermez**; yalnızca dinler. Bir şey gönderen her
özellik (lastik sıfırlama, Bluetooth tuşları, radyo kumandası, aracın ayar sayfalarını isteme ve değiştirme)
**"Üniteye komut göndermeye izin ver"** anahtarının arkasındadır ve fabrika uygulamasının kullandığı mesajların
aynısını kullanır. Aracın ayarlarını değiştirmeden önce launcher her açılışta bir kez onay sorar; izin verilen
değerleri fabrika uygulamasının listelerinden aldığı için yalnızca bilinen değerler gönderilir, değerleri
doğrulanmamış ayarlar salt okunur kalır. Kritik ayarlar (ör. ESC) eklenirse ayrıca onay penceresi ile sorulacaktır.

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
