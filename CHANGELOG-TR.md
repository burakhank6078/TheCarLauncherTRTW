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

## 28 Ekim 2026
**Ayarlar düzeni**
- **Ayarlar ana listesi 6 satıra indi** (önceden 20'den fazlaydı): **Görünüm** (Ekran, Ambiyans, Ambiyans şeridi, Dil, Birimler,
  Saat ve tarih), **Ses ve medya** (Ses, Medya, Radyo, Bluetooth ses, Telefon ve radyo uygulamaları), **Navigasyon ve araç**
  (Navigasyon, Araç), **Bağlantılar** (Wi-Fi, Hotspot, App-Connect), **Sistem** (Ayar yedeği, Android sistem, Sistem bilgileri)
  ve doğrudan açılan **Tanılama kaydı**. Sayfaların içeriği aynı; yalnızca bir basamak daha var. Radyo, Araç gibi sayfalara
  başka ekranlardan açılan kısayollar eskisi gibi çalışır ve geri tuşu çağıran ekrana döner.

**Sürüş uyarısı ve direksiyon tuşları**
- **Kapı / kemer uyarısı MIB açılır penceresi gibi:** üst çubuğun altındaki metin şeridi kalktı. Resim biraz kararır, ortada
  siyah, beyaz çerçeveli bir panel hafifçe büyüyerek yükselir: uyarı üçgeni, "Dikkat" başlığı, altında mavi çizgi, satırlar ve
  "Tamam" tuşu (telefon bağlanırken çıkan MIB pencereleri gibi). Yalnızca panel dokunuş alır, harita ve tuşlar kullanılabilir
  kalır; "Tamam" ya da panele dokunmak uyarıyı kapatır. Ayarlar → Araç → "Uyarıyı dene" ile görülür.
- **Radyo kanalı sadeleştirildi:** launcher'ın radyo kanalı artık yalnızca kaynak, tuner ve RDS mesajlarına abone olur;
  fabrika radyonun abone olduğu tuş yolu (0x201) ve radyo tuş mesajları (0x9E00/0x9E01) bırakıldı. Direksiyon "sonraki /
  önceki" tuşlarının ara sıra gitmesi için bulabildiğim tek launcher kaynaklı olasılık buydu; yine de sebebi kesin değil: tuş
  kaybolunca tanılama kaydını alın (0x201 satırları tuşun ünitenin ucuna gelip gelmediğini gösterir).

**Ambiyans şeridi (LAMP&FRGN)**
- **İki bölge ayrı ayarlanır:** LAMP&FRGN uygulamasındaki "Bölge 1" ve "Bölge 2" artık Ambiyans ekranından ayrı renk ve ayrı
  parlaklıkla sürülür. Şerit açıksa renk çubuğunun yanında üç ayrı tuş çıkar: **Hepsi, 1 ve 2**; seçili olan vurgu
  renginde yazılır ve üstteki değer satırı da hangi bölgede olduğunuzu söyler. Seçim, ekran yeniden kurulsa da (Bölge 1 ya da
  Hepsi'nin rengi değişince ekran yenilenir) korunur; önceden tuş her yenilemede "Hepsi"ne dönüyor ve ikinci değişiklik iki
  bölgeyi birden karıştırıyordu. Bir bölge seçiliyken renk ve parlaklık (güneş tuşu) yalnızca o bölgeyi değiştirir; "Hepsi" seçilip bir şey seçilince iki bölge yine birlikte gider.
  **Ekranın kendi rengi (MIB vurgu rengi) Hepsi'yi ve Bölge 1'i izler, Bölge 2'ye hiç bakmaz**; Bölge 2 yalnızca şeridi
  değiştirir. Hangi bölgenin ayak aydınlatması, hangisinin şerit olduğunu dokunup görerek anlayın.
  Komut biçimleri uygulamanın kodundan okundu (iki bölge: renk `8D 01 00 R1G1B1 R2G2B2`, parlaklık `8D 00 40 L1 L2`).
- **Renkler şeritte solmuyor:** paletteki pastel renkler (buz mavisi, lavanta, gül…) ekran için seçilmişti; LED şeritte sıfır
  olmayan her kanal beyaz katar, bu yüzden bu renkler soluk çıkıyor ve kısılınca beyaza dönüyordu. Renk artık şeride tam
  doygunlukta gönderiliyor (en koyu kanal 0, en açık kanal 255, ton aynı); gri, gümüş, sıcak ve soğuk beyaz olduğu gibi
  kalır. Ayarlar → Ambiyans şeridi → "Şerit için canlı renkler" ile kapatılabilir.

**Radyo (araç kaydına göre düzeltmeler)**
- **Radyo kendiliğinden açılır:** Radyo karosuna basınca, ünite başka bir kaynaktaysa (müzik, Bluetooth, radyo durmuşsa) fabrika
  radyo uygulaması kısaca başlatılır, ünite radyoya geçince launcher yeniden öne alınır; radyo ekranı açık kalır. Ünitenin hangi
  kaynakta olduğunu söylemesi ve komut izni gerekir; 20 saniyede en çok bir kez denenir; 5 saniyede geçmezse fabrika uygulaması
  ekranda kalır. Ayarlar → Radyo → "Radyoyu otomatik aç" ile kapatılabilir.
- **Arama yönü düzeltildi:** ekrandaki "sonraki" oku artık bir sonraki istasyona gider (öncekinde ters çalışıyordu).
- **Açılışta frekans görünür:** ünite, frekansı yalnızca değişince bildiriyordu; radyo ekranı açılınca artık tuner
  durumu da istenir.
- **Hafıza kareleri:** ünite bantta 18 hafıza tutar (6'lı üç sayfa). Kareler ünitenin o anki bandındaki slotlara bağlandı.
  Ünite listeyi kendiliğinden vermediği için kareler, hafızaya gidildikçe (kare ya da direksiyon tuşuyla) ya da buradan
  kaydedildikçe dolar. Sayfa, seçili hafızanın sayfasını izler.
- **Radyo ekranı fabrikadaki düzene yaklaştı:** üstte büyük frekans, altında küçük istasyon adı (önceden ad, frekansın yerini
  alıyordu); hafıza karelerinin içinde frekans, altında ad; alt çubuk dolu: **band tuşu** (FM1 / FM2 / AM, dokununca sıradaki
  banda geçer), **İstasyonlar** ve **Elle**. İstasyonlar listesi, ünitenin ad gönderdiği istasyonlardan kendiliğinden dolar
  (FM ve AM için ayrı, en çok 80'er, uygulama kapansa da kalır); satıra dokununca o istasyona gider. Ünite kendi listesini
  vermediği için "listeyi güncelle" taraması henüz yok.
- **Elle ayar geri geldi:** alt çubuktaki "Elle / Manual" tuşu ve frekans ölçeği açıldı; ölçeği bırakınca ünite o frekansa
  gider (fabrika uygulamasının frekans çubuğunun komutu). Yalnızca FM; komut izni ve etkin radyo kaynağı gerekir. İstasyon
  listesi tuşu ünitede yine yok.
- **İstasyon logoları Türkçe adlarla da bulunur:** arama, Türkçe harfleri ve aksanları sadeleştirir ("TRT Türkü" ile RDS'teki
  "TRTTURKU" artık aynı sayılır); boşluksuz RDS adları için ilk üç harfle ikinci bir arama yapılır. Türkiye ve Kıbrıs kanalları
  öne alınır. Logolar yine bir kez inip ünitede kalır.
- **Kaydedilen kareye RDS adı da gelir:** bir istasyonu hafızaya kaydedince ya da bir hafızaya gidince ad, frekanstan birkaç
  saniye sonra geliyor; kare o zamana kadar yalnızca frekansı bilip adı hiç almıyordu. Ad artık sonradan gelse de aynı
  frekansı tutan, adsız tüm karelere işlenir (ünitede aynı istasyon iki slotta da olabiliyor).
- **Logo eşleşmesi gevşetildi, bulanık olanlar yine elenir:** "TRT Radyo Haber", "TRT Haber Radyo" ve RDS'teki "TRTHABER"
  artık aynı istasyon sayılır ("radyo", "radio", "fm", "canlı" sözcükleri eşleşmede yok sayılır). Logo, karedeki logo kutusuna
  sığdırılırken yaklaşık %15'ten fazla büyütülecekse kullanılmaz (32 px'lik ikon bulanık olur, 64 px'lik olur); geniş ama büyük
  logolar (TRT Haber yazısı gibi) kabul edilir. PNG, JPEG'den önce denenir (JPEG çoğu zaman fotoğraf ya da kapak resmi);
  sonraki adaylar denenir, hiçbiri uygun değilse karede yalnızca ad kalır. Veritabanında hiç logosu olmayan istasyonlarda
  (TRT Türkü gibi) internetten logo bulunamaz; fabrika radyonun kendi logoları için hazırlık aşağıda.
- **Fabrika radyonun kendi logoları için hazırlık:** fabrika radyo, logoyu RDS PI koduna ve frekansa göre ünitenin kendi
  veritabanından alıyor (`radio_data.txt` + `RadioLogo.zip`). Tanılama kaydı artık bu dosyaların bu ünitede olup olmadığını
  `SYS radiologo` satırlarıyla yazar; kayıt gelince fabrikanın logoları okunacak.
- **İstasyon adı titremesi giderildi:** RDS adı ("A HABER") ara sıra bozuk harfle geliyordu ("A HABEa"); ad artık iki kez
  üst üste görülmeden değişmez.
- **Hafızaya kaydetme:** bir hafıza karesine uzun basınca çalan istasyon ünitenin kendi hafızasına
  kaydedilir (fabrika uygulamasındaki uzun basışın aynısı). Komut izni açık ve radyo etkin kaynak olmalı; değilse ne eksik
  olduğunu söyleyen bir uyarı çıkar.

**Araç durumu**
- **Şasi numarası (VIN) araç durumu sayfasından kaldırıldı.** Araçtan artık istenmez; önceki sürümlerin ünitede bıraktığı kopya
  uygulama açılınca silinir.
- **Bagaj açık** uyarısı: kapı satırlarına ve sürüş uyarısına eklendi (tek denemeyle bulundu).
- **Akü voltajı** artık doğrudan araç çerçevesinden de okunur; motorun çalıştığı, rölantide devir gelmese de 13 V ve üzeri
  şarj voltajından anlaşılır (kemer uyarısı bu yüzden rölantide de çalışır).

---

## 26 Ekim 2026
**Günlük kullanım**
- **Otomatik gece haritası:** Ayarlar → Navigasyon → "Otomatik gece haritası (farlara göre)". Farlar yanınca (4 sn kararlı)
  harita gece görünümüne, sönünce gündüz görünümüne geçer; kısa far yakıp söndürmeler haritayı oynatmaz. "Yolculuk
  verilerini OBD'den al" açık olmalı. Ekran parlaklığına dokunulmaz.
- **Ayar yedeği:** Ayarlar → Ayar yedeği: ayarlar USB'ye (yoksa İndirilenler'e) tek dosya (`TheCarLauncher-ayarlar.txt`)
  olarak kaydedilir ve yeniden kurulumdan sonra geri yüklenir. Resimler, çevrimdışı haritalar, yolculuk geçmişi, araç
  verileri, **komut gönderme izni ve harita anahtarı dahil değildir** (güvenlik için bunlar her ünitede elle açılır).
  USB işlemleri arka planda yapılır.
- **Yolculuk geçmişi:** Araç → yeni sayfa "Yolculuk geçmişi". Hız 5 km/s'i (GPS) geçince yolculuk başlar, 5 dakika durunca
  biter (kısa park manevraları sayılmaz). Bu ay ve geçen ayın toplamı (sayı, km, süre, tüketim, girilen yakıt fiyatına göre
  maliyet) ve son 10 yolculuk listelenir; tüketim, aracın "başlangıçtan beri" değeri mesafeyle uyuşuyorsa eklenir. Son 400
  yolculuk saklanır, sayfadan silinebilir. Kayıt kendi GPS dinleyicisiyle yapılır (başka uygulama öndeyken de), ancak
  yalnızca ünite GPS'i arka plandaki uygulamalara veriyorsa çalışır; Ayarlar → Araç → "Yolculuk geçmişi" ile kapatılabilir.
  Süre ölçümü ünitenin saat sıçramalarından etkilenmez.
- Otomatik gece haritası açıkken OBD verisi kapalıysa ayarlar sayfası bunu söyler.

**Sürüş uyarısı**
- **Kapı ve kemer uyarısı:** araç giderken (GPS hızı 5 km/s üstü) bir kapı açıksa ya da (10 km/s üstü, motor çalışırken)
  sürücü kemeri takılı değilse üst çubuğun altında yazılı bir uyarı çıkar (ör. "Sürücü kapısı açık"). 8 sn sonra kendiliğinden
  kapanır, dokununca kapanır, sorun sürerse dakikada bir tekrar eder, durum değişirse hemen yeniden çıkar. Üst çubuk
  (geri oku, saat) uyarı sırasında da kullanılabilir. Ayarlar → Araç'ta açılıp kapatılır ve "Uyarıyı dene" ile örnek gösterilir.
  Çalışması için "Yolculuk verilerini OBD'den al" açık olmalı. Uyarı yalnızca launcher ekrandayken görünür (başka bir
  uygulamanın üstüne çıkmaz). Kapı bilgisi eskiyse (2 dakikadan eski) giderken güvenilmez; komut izni açıksa araca yeniden sorulur.
- Motorun çalıştığı artık yalnızca devirden değil, şarj voltajından da anlaşılır (motor kapalıyken eski devir değeri
  kalsa bile yanlış kemer ya da akü uyarısı çıkmaz).
- Tanılama kaydı ses yongası özelliklerini de yazar.
- **Radyo ekranı fabrika tunerine bağlandı (ilk sürüm, araçta denenmedi):** TW ünitelerde Radyo ekranı artık fabrika radyo
  uygulamasının tunerini aynalar ve uzaktan kumanda eder: frekans (FM MHz, AM kHz), RDS istasyon adı ve metni, hafıza
  sırası; oklarla sonraki/önceki istasyon, hafıza karelerine dokununca o hafızaya geçiş (üç sayfa = FM1, FM2, AM).
  Ses fabrika radyo uygulamasından çalmaya devam eder; launcher kaynağı değiştirmez, bu yüzden radyo etkin kaynak
  değilse ekran "Radyo etkin kaynak değil (radyo uygulamasını açın)" der. Kumanda için "Üniteye komut göndermeye izin ver"
  açık olmalı. Doğrudan frekans girme, istasyon listesi/tarama ve hafızaya kaydetme henüz yok. Alanların doğruluğu
  R grubu kaydıyla kesinleşecek.
- **Radyo ve telefon testi hazırlığı:** kayıt açıkken (uygulama o şekilde başlatılmışsa) fabrika radyo uygulaması gibi
  tuner (modül 1) ayrı bir kanalla dinlenir; frekans, band, istasyon adı ve hafıza kayda düşer. Test senaryosuna P ve R
  grupları eklendi; fabrikanın radyo protokolü `docs/RADIO-PROTOKOL.md` dosyasında.
  **Telefon kanalı** (Bluetooth modülü, modül 8) yalnızca Ayarlar → Tanılama kaydı → Canbus test senaryosu sayfasındaki
  "Telefon kanalını dinle" anahtarı açıksa dinlenir; isim ve numaralar kayda yalnızca biçim olarak yazılır. **Dikkat:** bu
  kanal fabrikanın Bluetooth uygulamasıyla aynı bağlantıyı kullanır ve Bluetooth'u bozabilir; yalnızca telefon testi için
  açın, o sırada arama beklemeyin, Bluetooth çalışmazsa üniteyi yeniden başlatın. Kanallar kayıt kapanınca kapatılır;
  açılırken uygulama çökerse bir daha açılmaz.

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
