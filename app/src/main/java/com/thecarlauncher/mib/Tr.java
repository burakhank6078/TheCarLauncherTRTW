package com.thecarlauncher.mib;

import java.util.HashMap;

/**
 * Turkish translations, keyed by the English text used in the code.
 *
 * Keeping the key identical to the English string means the upstream code (s("sr", "en")) stays
 * untouched and merges cleanly. A string that is missing here simply shows in English, so new
 * upstream texts never break the UI; add them here after each update (see CHANGELOG-TR.md).
 */
final class Tr {
    private static final HashMap<String, String> M = new HashMap<String, String>(1024);

    private Tr() {}

    /** Turkish for the given English text, or the English text itself when not translated yet. */
    static String get(String en) {
        String t = M.get(en);
        return t != null ? t : en;
    }

    private static void p(String en, String tr) { M.put(en, tr); }

    static {
        // ---- Toasts, hints, status ----
        p("No app to choose a picture", "Resim seçmek için uygulama yok");
        p("Choose a phone app in Setup", "Ayarlar'dan bir telefon uygulaması seçin");
        p("Choose a radio app (long-press it in Apps)", "Bir radyo uygulaması seçin (Uygulamalar'da uzun basın)");
        p("Allow \"All files access\" for Car Launcher so music on USB sticks can be read.", "USB belleklerdeki müziğin okunabilmesi için Car Launcher'a \"Tüm dosyalara erişim\" izni verin.");
        p("Oil change due now!", "Yağ değişimi zamanı geldi!");
        p("Oil change in ", "Yağ değişimine kalan: ");
        p("Annual service due now!", "Yıllık bakım zamanı geldi!");
        p("Service in ", "Bakıma kalan: ");
        p(" days", " gün");
        p("Registration has expired!", "Muayene süresi doldu!");
        p("Registration expires in ", "Muayeneye kalan: ");
        p("Time for winter tyres", "Kış lastiği zamanı");
        p("You can switch back to summer tyres", "Yaz lastiğine geri dönebilirsiniz");
        p("Low fuel: about ", "Yakıt azaldı: yaklaşık ");
        p("Disconnect the phone first", "Önce telefonun bağlantısını kesin");
        p("Searching for devices…", "Cihazlar aranıyor…");
        p("Connected: ", "Bağlandı: ");
        p("Could not connect — turn on Bluetooth on the phone", "Bağlanılamadı — telefonda Bluetooth'u açın");
        p("Turn on \"Display over other apps\" for Car Launcher, then choose BT audio again.", "Car Launcher için \"Diğer uygulamaların üzerinde göster\" seçeneğini açın, sonra BT ses'i tekrar seçin.");
        p("Reading music from USB and storage…", "Müzikler USB'den ve depolamadan okunuyor…");

        // ---- Navigation and voice guidance ----
        p("No GPS position", "GPS konumu yok");
        p("Could not calculate route", "Rota hesaplanamadı");
        p("Route calculated.", "Rota hesaplandı.");
        p("Recalculating.", "Yeniden hesaplanıyor.");
        p("You have arrived.", "Varış noktasına ulaştınız.");
        p("Destination", "Varış noktası");
        p("Start", "Başlat");
        p("Roundabout", "Döner kavşak");
        p("Merge", "Yola katılın");
        p("Take the ramp", "Rampaya girin");
        p("Exit left", "Soldan çıkın");
        p("Exit right", "Sağdan çıkın");
        p("Exit", "Çıkış");
        p("Keep left", "Solda kalın");
        p("Keep right", "Sağda kalın");
        p("Continue", "Devam edin");
        p("At the end, left", "Sonunda sola");
        p("At the end, right", "Sonunda sağa");
        p("Make a U-turn", "U dönüşü yapın");
        p("Sharp left", "Keskin sola");
        p("Sharp right", "Keskin sağa");
        p("Bear left", "Hafif sola");
        p("Bear right", "Hafif sağa");
        p("Turn left", "Sola dönün");
        p("Turn right", "Sağa dönün");
        p("Continue straight", "Düz devam edin");
        p("Now ", "Şimdi ");
        p(" kilometres", " kilometre");
        p(" metres", " metre");
        p("Loading the list of maps…", "Harita listesi yükleniyor…");
        p("Downloading map: ", "Harita indiriliyor: ");
        p("Searching for radio stations…", "Radyo istasyonları aranıyor…");
        p("Searching for radio stations… ", "Radyo istasyonları aranıyor… ");
        p("No named stations — update the station list first", "Adlandırılmış istasyon yok — önce istasyon listesini güncelleyin");
        p("Downloading station logos… ", "İstasyon logoları indiriliyor… ");
        p("Logos: ", "Logolar: ");

        // ---- App-Connect ----
        p("Ambient lighting", "Ortam aydınlatması");
        p("ZLink is not installed", "ZLink yüklü değil");
        p("App-Connect is not available.\nNo ZLink app was found on this unit.", "App-Connect kullanılamıyor.\nBu cihazda ZLink uygulaması bulunamadı.");
        p("Screen mirroring", "Ekran yansıtma");
        p(" is connected.\nTap to show the phone.", " bağlı.\nTelefonu göstermek için dokunun.");
        p("Welcome to App-Connect.\nPlease connect a device using the USB.", "App-Connect'e hoş geldiniz.\nLütfen bir cihazı USB ile bağlayın.");
        p("Phone", "Telefon");
        p(" connected", " bağlandı");
        p("Phone disconnected", "Telefonun bağlantısı kesildi");
        p("Connecting: ", "Bağlanıyor: ");
        p("no data", "veri yok");
        p("Phones connect by USB cable or wirelessly (Android Auto, Apple CarPlay, screen mirroring). The phone's screen is shown by the unit's ZLink app.\n\nStatus: ", "Telefonlar USB kablo ile ya da kablosuz bağlanır (Android Auto, Apple CarPlay, ekran yansıtma). Telefonun ekranı cihazın ZLink uygulaması tarafından gösterilir.\n\nDurum: ");
        p("Mode: ", "Mod: ");

        // ---- Apps ----
        p("Apps", "Uygulamalar");
        p("Search", "Ara");
        p("Search apps", "Uygulama ara");
        p("App name", "Uygulama adı");
        p("All", "Tümü");
        p("Remove from MENU", "MENÜ'den kaldır");
        p("Add to MENU", "MENÜ'ye ekle");
        p("Use as phone app", "Telefon uygulaması olarak kullan");
        p("Use as radio app", "Radyo uygulaması olarak kullan");
        p("App info", "Uygulama bilgisi");
        p("Uninstall", "Kaldır");

        // ---- Bluetooth devices ----
        p("Bluetooth devices", "Bluetooth cihazları");
        p("New device", "Yeni cihaz");
        p("No remembered devices. Connect a new phone:", "Kayıtlı cihaz yok. Yeni bir telefon bağlayın:");
        p("Connect a new device", "Yeni cihaz bağla");
        p("Connected", "Bağlı");
        p("Connecting…", "Bağlanıyor…");
        p("Tap a device to connect. Long press: forget the device.", "Bağlanmak için bir cihaza dokunun. Uzun basış: cihazı unut.");
        p("A phone is connected. Disconnect it first to add a new device.", "Bir telefon bağlı. Yeni cihaz eklemek için önce bağlantıyı kesin.");
        p("Disconnect the current phone", "Mevcut telefonun bağlantısını kes");
        p("Turn on Bluetooth on the phone and make it visible, then pick it from the list:", "Telefonda Bluetooth'u açıp görünür yapın, sonra listeden seçin:");
        p("Pairing…", "Eşleştiriliyor…");
        p("Searching…", "Aranıyor…");
        p("No devices found. Tap \"Search\" in the corner.", "Cihaz bulunamadı. Köşedeki \"Ara\"ya dokunun.");
        p("Disconnect", "Bağlantıyı kes");
        p("Cancel", "İptal");
        p("Forget device", "Cihazı unut");

        // ---- Media ----
        p("Media", "Medya");
        p("Source", "Kaynak");
        p("Browse", "Gözat");
        p("Previous", "Önceki");
        p("Play", "Oynat");
        p("Next", "Sonraki");
        p("Back", "Geri");
        p("Pause", "Duraklat");
        p("To show the song and cover, enable\nnotification access — tap here.", "Şarkıyı ve kapağı göstermek için\nbildirim erişimini açın — buraya dokunun.");
        p("Start music on your phone.", "Telefonunuzda müziği başlatın.");
        p("No media selected.\nTap Browse or Play.", "Medya seçilmedi.\nGözat'a veya Oynat'a dokunun.");
        p("No track information.", "Parça bilgisi yok.");
        p("Connect apps", "Uygulamaları bağla");
        p("Enable \"Car Launcher\" so Spotify & other players show up here", "Spotify ve diğer oynatıcıların burada görünmesi için \"Car Launcher\"ı etkinleştirin");
        p("Songs", "Şarkılar");
        p("Albums", "Albümler");
        p("Artists", "Sanatçılar");
        p("Folders", "Klasörler");
        p("Allow access to music.", "Müziğe erişime izin verin.");
        p("No media.\nTap here and allow \"All files access\" so USB sticks can be read.", "Medya yok.\nBuraya dokunup USB belleklerin okunabilmesi için \"Tüm dosyalara erişim\" izni verin.");
        p("No media.\nInsert a USB stick or SD card with music.", "Medya yok.\nMüzik içeren bir USB bellek veya SD kart takın.");

        // ---- Main menu ----
        p("Radio", "Radyo");
        p("Vehicle", "Araç");
        p("Navigation", "Navigasyon");
        p("Setup", "Ayarlar");
        p("Add", "Ekle");
        p("No music", "Müzik yok");

        // ---- Navigation screen ----
        p("Driving direction up", "Harita sürüş yönüne dönsün");
        p("North up", "Kuzey yukarıda");
        p("New dest.", "Yeni hedef");
        p("New destination", "Yeni hedef");
        p("My dest.", "Hedeflerim");
        p("Nearby", "Yakında");
        p("View", "Görünüm");
        p("Nav app", "Nav uygulaması");
        p("Street and number, city or place", "Sokak ve numara, şehir veya yer");
        p("Search: ", "Ara: ");
        p("No connection — try again", "Bağlantı yok — tekrar deneyin");
        p("No results", "Sonuç yok");
        p("Waiting for GPS position", "GPS konumu bekleniyor");
        p(" nearby", " yakında");
        p("Nothing within 5 km", "5 km içinde hiçbir şey yok");
        p("Navigate to this point?", "Bu noktaya gidilsin mi?");
        p("Point on map", "Haritadaki nokta");
        p("Home", "Ev");
        p("Work", "İş");
        p("Recent destinations", "Son hedefler");
        p("Set", "Ayarla");
        p("set", "ayarla");
        p("Empty", "Boş");
        p("No destinations yet", "Henüz hedef yok");
        p("Change home address", "Ev adresini değiştir");
        p("Change work address", "İş adresini değiştir");
        p("Stop", "Durdur");
        p("Calculating route…", "Rota hesaplanıyor…");
        p("You have arrived", "Varış noktasına ulaştınız");
        p("Allow location access", "Konum erişimine izin verin");
        p("Waiting for GPS…", "GPS bekleniyor…");

        // ---- Radio ----
        p("Stations", "İstasyonlar");
        p("Manual", "Manuel");
        p("switched off in Setup", "Ayarlar'da kapatıldı");
        p("Station list", "İstasyon listesi");
        p("Scanning…", "Taranıyor…");
        p("empty", "boş");
        p("Stop scan", "Taramayı durdur");
        p("Update station list", "İstasyon listesini güncelle");

        // ---- Settings ----
        p("System settings", "Sistem ayarları");
        p("Screen", "Ekran");
        p("Time and date", "Saat ve tarih");
        p("Units", "Birimler");
        p("Sound", "Ses");
        p("Phone and radio", "Telefon ve radyo");
        p("Hotspot", "Hotspot");
        p("Android system", "Android sistemi");
        p("System information", "Sistem bilgisi");
        p("Brightness", "Parlaklık");
        p("Allow changing the system brightness", "Sistem parlaklığını değiştirmeye izin ver");
        p("now only inside the launcher", "şu an yalnızca launcher içinde");
        p("Automatic brightness", "Otomatik parlaklık");
        p("Colour profile", "Renk profili");
        p("Custom", "Özel");
        p("Saturation", "Doygunluk");
        p("Contrast", "Kontrast");
        p("Warmth", "Sıcaklık");
        p("Full screen (hide Android bars)", "Tam ekran (Android çubuklarını gizle)");
        p("The colour profile changes the launcher's picture (all its screens, the map too). Other apps' colours can only be changed once the launcher is a system app.", "Renk profili launcher'ın görüntüsünü değiştirir (tüm ekranlar ve harita dahil). Diğer uygulamaların renkleri ancak launcher sistem uygulaması olduktan sonra değiştirilebilir.");
        p("Wi-Fi not available", "Wi-Fi kullanılamıyor");
        p("Wi-Fi is off.", "Wi-Fi kapalı.");
        p("Search for networks", "Ağları ara");
        p("Searching for Wi-Fi networks…", "Wi-Fi ağları aranıyor…");
        p("No networks found (location permission is needed).", "Ağ bulunamadı (konum izni gerekli).");
        p("secured", "korumalı");
        p("open", "açık");
        p("Password", "Parola");
        p("System Wi-Fi settings", "Sistem Wi-Fi ayarları");
        p("forget a network, advanced", "ağı unut, gelişmiş");
        p("Confirm the network in the system window", "Ağı sistem penceresinde onaylayın");
        p("Connecting to ", "Bağlanılıyor: ");
        p("Could not connect", "Bağlanılamadı");
        p("state unknown", "durum bilinmiyor");
        p("On", "Açık");
        p("Off", "Kapalı");
        p("Turn the hotspot on / off", "Hotspot'u aç / kapat");
        p("Hotspot settings not found", "Hotspot ayarları bulunamadı");
        p("Android does not let an ordinary app switch the hotspot itself: the system hotspot page opens (name, password, on/off). Once the launcher is a system app this works right here.", "Android, sıradan bir uygulamanın hotspot'u kendisinin açmasına izin vermez: sistem hotspot sayfası açılır (ad, parola, aç/kapat). Launcher sistem uygulaması olduğunda bu doğrudan buradan çalışır.");
        p("24-hour format", "24 saat biçimi");
        p("Date and time (Android)", "Tarih ve saat (Android)");
        p("Kilometres (km/h)", "Kilometre (km/sa)");
        p("Miles (mph)", "Mil (mph)");
        p("Navigation app", "Navigasyon uygulaması");
        p("None found", "Bulunamadı");
        p("Map turns with the driving direction", "Harita sürüş yönüyle döner");
        p("Offline maps", "Çevrimdışı haritalar");
        p("None downloaded", "İndirilen yok");
        p(" downloaded", " indirildi");
        p("Voice guidance", "Sesli yönlendirme");
        p("Clear recent destinations", "Son hedefleri temizle");
        p("Cleared", "Temizlendi");
        p("CARTO key for the online map", "Çevrimiçi harita için CARTO anahtarı");
        p("Not set – OpenStreetMap is used", "Ayarlanmadı – OpenStreetMap kullanılıyor");
        p("CARTO API key (free: carto.com/basemaps/apikey)", "CARTO API anahtarı (ücretsiz: carto.com/basemaps/apikey)");
        p("Day", "Gündüz");
        p("Night", "Gece");
        p("OpenStreetMap (dimmed)", "OpenStreetMap (karartılmış)");
        p("Off (no internet)", "Kapalı (internet yok)");
        p("Map style", "Harita stili");
        p("Maps work without internet and are drawn on the unit.", "Haritalar internetsiz çalışır ve cihaz üzerinde çizilir.");
        p("Use offline map", "Çevrimdışı haritayı kullan");
        p("Downloading: ", "İndiriliyor: ");
        p(" · tap to delete", " · silmek için dokunun");
        p("Delete map?", "Harita silinsin mi?");
        p("Delete", "Sil");
        p("Download a map", "Harita indir");
        p("Countries and regions", "Ülkeler ve bölgeler");
        p("Data: © OpenStreetMap contributors (ODbL), files: mapsforge.org", "Veri: © OpenStreetMap katkıda bulunanlar (ODbL), dosyalar: mapsforge.org");
        p("Loading the list…", "Liste yükleniyor…");
        p("List not available (needs internet): ", "Liste alınamadı (internet gerekli): ");
        p("Download started: ", "İndirme başladı: ");
        p("Resume playback on start", "Açılışta oynatmaya devam et");
        p("Bass - Mid - Treble", "Bas - Orta - Tiz");
        p("Music library", "Müzik kitaplığı");
        p("Rescan", "Yeniden tara");
        p("Allow access", "Erişime izin ver");
        p("Library will be rescanned", "Kitaplık yeniden taranacak");
        p("All files access (USB)", "Tüm dosyalara erişim (USB)");
        p("Allowed", "İzin verildi");
        p("Not allowed – tap", "İzin verilmedi – dokunun");
        p("Other media apps", "Diğer medya uygulamaları");
        p("Not connected", "Bağlı değil");
        p("connect / new device", "bağlan / yeni cihaz");
        p("BT audio as soon as the phone connects", "Telefon bağlanır bağlanmaz BT ses");
        p("Cover art from the internet", "İnternetten albüm kapağı");

        // ---- Diagnostics ----
        p("Diagnostics (QF / Bluetooth)", "Tanılama (QF / Bluetooth)");
        p("Diagnostics", "Tanılama");
        p("Automatic (detect from media session)", "Otomatik (medya oturumundan algıla)");
        p("Read the BT module directly (only without the factory BT app)", "BT modülünü doğrudan oku (yalnızca fabrika BT uygulaması yokken)");
        p("Media access", "Medya erişimi");
        p("Yes", "Evet");
        p("No", "Hayır");
        p("Sources", "Kaynaklar");
        p("BT screen", "BT ekranı");
        p("BT_STATE", "BT_STATE");
        p("MCU audio channel now", "Şu anki MCU ses kanalı");
        p("unavailable", "kullanılamıyor");
        p("Learned BT channel", "Öğrenilen BT kanalı");
        p("Test channel ", "Test kanalı ");
        p(" (back to Android)", " (Android'e dön)");
        p("tap and listen", "dokunun ve dinleyin");
        p("MCU channel → ", "MCU kanalı → ");
        p("MCU not available", "MCU kullanılamıyor");
        p("Phone (battery / signal)", "Telefon (pil / sinyal)");
        p("Last QF broadcast", "Son QF yayını");
        p("none yet — play BT music", "henüz yok — BT müziği çalın");
        p("Notification access", "Bildirim erişimi");
        p("✗ — tap to enable", "✗ — etkinleştirmek için dokunun");
        p("BT package (from media session)", "BT paketi (medya oturumundan)");
        p("Media session", "Medya oturumu");
        p("Media notification", "Medya bildirimi");
        p("Current source / title", "Geçerli kaynak / başlık");
        p("GOC Bluetooth daemon", "GOC Bluetooth hizmeti");
        p("GOC received: ", "GOC alındı: ");
        p("GOC song", "GOC şarkısı");
        p("GOC: ask for song info", "GOC: şarkı bilgisini iste");
        p("Sent — refresh in 2 s", "Gönderildi — 2 sn sonra yenileyin");
        p("GOC not connected: ", "GOC bağlı değil: ");
        p("Song from BT app (broadcast)", "BT uygulamasından şarkı (yayın)");
        p("nothing received", "hiçbir şey alınmadı");
        p("unreadable", "okunamıyor");
        p("GOC song numbers (MI)", "GOC şarkı numaraları (MI)");
        p("GOC position (MP)", "GOC konumu (MP)");
        p("GOC length / position", "GOC uzunluk / konum");
        p("Probe BT daemon (play music)", "BT hizmetini yokla (müzik çalın)");
        p("takes ~10 s", "yaklaşık 10 sn sürer");
        p("Probing for about ten seconds…", "Yaklaşık on saniye yoklanıyor…");
        p("BT daemon probe", "BT hizmeti yoklaması");
        p("Nothing found", "Hiçbir şey bulunamadı");
        p("— MCU packets (non-radio) —", "— MCU paketleri (radyo dışı) —");
        p("none yet", "henüz yok");
        p("Text ", "Metin ");
        p("— System services (BT / QF) —", "— Sistem hizmetleri (BT / QF) —");
        p("service list unavailable", "hizmet listesi kullanılamıyor");
        p("— BT app components —", "— BT uygulama bileşenleri —");
        p("— TW platform (vehicle) —", "— TW platformu (araç) —");
        p("Listen to TW messages (read-only)", "TW mesajlarını dinle (yalnızca okuma)");

        // ---- Phone / radio setup ----
        p("App (ZLink, CarPlay…)", "Uygulama (ZLink, CarPlay…)");
        p("Not found", "Bulunamadı");
        p("Phone app", "Telefon uygulaması");
        p("System dialer", "Sistem arayıcısı");
        p("Radio app", "Radyo uygulaması");
        p("FM tuner", "FM tuner");
        p("Built-in launcher radio", "Launcher'ın yerleşik radyosu");
        p("Strong stations only (LOC)", "Yalnızca güçlü istasyonlar (LOC)");
        p("Station logos from the internet", "İnternetten istasyon logoları");
        p("Download logos for found stations", "Bulunan istasyonların logolarını indir");
        p(" saved", " kaydedildi");
        p("Clear presets", "Kayıtlı istasyonları temizle");
        p("Presets cleared", "Kayıtlı istasyonlar temizlendi");
        p("Turn radio off", "Radyoyu kapat");
        p("Factory radio app", "Fabrika radyo uygulaması");

        // ---- Vehicle pictures ----
        p("Brand", "Marka");
        p("Model", "Model");
        p("Car picture (Vehicle status)", "Araç resmi (Araç durumu)");
        p("Interior picture (Ambient lighting)", "İç mekân resmi (Ortam aydınlatması)");
        p("Choose a picture", "Resim seçin");
        p("Looking for pictures…", "Resimler aranıyor…");
        p("Allow access to pictures", "Resimlere erişime izin verin");
        p("needed", "gerekli");
        p("Corsa C (built-in)", "Corsa C (yerleşik)");
        p("No picture", "Resim yok");
        p("Import a picture from the unit or USB:", "Cihazdan veya USB'den resim aktarın:");
        p("Another app (gallery, file manager)", "Başka bir uygulama (galeri, dosya yöneticisi)");
        p("Loading…", "Yükleniyor…");
        p("No pictures on the unit or on USB", "Cihazda veya USB'de resim yok");
        p("Picture set", "Resim ayarlandı");
        p("Saving the picture…", "Resim kaydediliyor…");
        p("The picture can't be opened", "Resim açılamıyor");

        // ---- System / software information ----
        p("Default launcher", "Varsayılan launcher");
        p("Permissions", "İzinler");
        p("All Android settings", "Tüm Android ayarları");
        p("Software", "Yazılım");
        p("Software version", "Yazılım sürümü");
        p("EN", "TR");
        p("Software base", "Yazılım tabanı");
        p("Build number", "Derleme numarası");
        p("Head unit", "Multimedya ünitesi");
        p("Hardware", "Donanım");
        p("System version", "Sistem sürümü");
        p("System date", "Sistem tarihi");
        p("MCU version", "MCU sürümü");
        p("Factory software version", "Fabrika yazılım sürümü");
        p("Memory", "Bellek");
        p("free", "boş");
        p("Map data", "Harita verisi");
        p("Not set", "Ayarlanmadı");
        p("Street, city", "Sokak, şehir");
        p("Reset", "Sıfırla");

        // ---- Sound ----
        p("Bass", "Bas");
        p("Mid", "Orta");
        p("Treble", "Tiz");

        // ---- Vehicle status ----
        p("Vehicle status", "Araç durumu");
        p("Since start", "Başlangıçtan itibaren");
        p("Long-term", "Uzun süreli");
        p("Since refuelling", "Yakıt aldıktan sonra");
        p("Fuel", "Yakıt");
        p("Service", "Bakım");
        p("Select", "Seçim");
        p("Driving time", "Sürüş süresi");
        p("Distance", "Mesafe");
        p("Average speed", "Ortalama hız");
        p("Current speed", "Anlık hız");
        p("Heading", "Yön");
        p("Altitude", "Rakım");
        p("Odometer", "Kilometre sayacı");
        p("Avg. consumption", "Ort. tüketim");
        p("Range", "Menzil");
        p("Last fill-up", "Son yakıt alımı");
        p("Cost per 100 km", "100 km maliyeti");
        p("Avg. fuel price", "Ort. yakıt fiyatı");
        p("Fill-up", "Yakıt alımı");
        p("No entries.", "Kayıt yok.");
        p("Add fill-up", "Yakıt alımı ekle");
        p("Average consumption", "Ortalama tüketim");
        p("needs 2 full tanks", "2 dolu depo gerekir");
        p("  (partial)", "  (kısmi)");
        p("Delete last fill-up", "Son yakıt alımını sil");
        p("Litres", "Litre");
        p("Price per litre", "Litre fiyatı");
        p("Full tank", "Depo dolu");
        p("Fill-up saved", "Yakıt alımı kaydedildi");
        p("enter", "girin");
        p("Current odometer (km)", "Güncel kilometre (km)");
        p("Next oil change", "Sonraki yağ değişimi");
        p("Oil change at (km)", "Yağ değişimi (km)");
        p("Annual service", "Yıllık bakım");
        p("Service done today", "Bakım bugün yapıldı");
        p("+15,000 km / +1 yr", "+15.000 km / +1 yıl");
        p("Next service scheduled", "Sonraki bakım planlandı");
        p("Registration expires", "Muayene tarihi");
        p("Winter tyres fitted", "Kış lastiği takılı");
        p("Winter tyre reminder (1 Nov – 1 Apr)", "Kış lastiği hatırlatıcısı (1 Kas – 1 Nis)");
        p("Fuel tank", "Yakıt deposu");
        p("Fuel tank (l)", "Yakıt deposu (l)");
        p("Vehicle and pictures", "Araç ve resimler");
        p("Imported picture", "İçe aktarılan resim");
        p("Not selected", "Seçilmedi");
        p("No picture selected", "Resim seçilmedi");
        p("Setup → Vehicle", "Ayarlar → Araç");
        p("Factory car settings", "Fabrika araç ayarları");
        p("Ambient strip (Bluetooth LE)", "Ambiyans şeridi (Bluetooth LE)");
        p("Control the ambient strip over Bluetooth LE", "Ambiyans şeridini Bluetooth LE ile yönet");
        p("Status", "Durum");
        p("Chosen device", "Seçilen cihaz");
        p("Search for BLE devices", "BLE cihazlarını ara");
        p("Send the current colour", "Geçerli rengi gönder");
        p("Forget the device", "Cihazı unut");
        p("Close the phone app first: the strip accepts one connection. The colour and level of Ambient lighting are sent to the strip.", "Önce telefondaki uygulamayı kapatın: şerit tek bağlantı kabul eder. Ortam aydınlatmasının rengi ve seviyesi şeride gönderilir.");
        p("opens the unit's app", "cihazın uygulamasını açar");
        p("App not found", "Uygulama bulunamadı");
        p("Tyre pressure", "Lastik basıncı");
        p("Tyre picture (Tyre pressure)", "Lastik resmi (Lastik basıncı)");
        p("Fuel consumption picture (Trip pages, 1024×444 px)", "Yakıt tüketimi resmi (Yolculuk sayfaları, 1024×444 px)");
        p("Whole page: 1024×444 px recommended (PNG, up to 1600 px). The car, road and lines go in the picture.", "Tüm sayfa: 1024×444 px önerilir (PNG, en fazla 1600 px). Araç, yol ve çizgiler resmin içinde olmalı.");
        p("Take trip data from OBD", "Yolculuk verilerini OBD'den al");
        p("Diagnostic recording", "Tanılama kaydı");
        p("Recorded size", "Kayıt boyutu");
        p("Folder", "Klasör");
        p("Export the record to USB", "Kaydı USB'ye aktar");
        p("USB / Downloads", "USB / İndirilenler");
        p("Saved: ", "Kaydedildi: ");
        p("Export failed", "Aktarma başarısız");
        p("Clear the record", "Kaydı sil");
        p("Records system details, errors, Canbus messages and Bluetooth commands, without positions. Drive as usual, then export the record.", "Sistem bilgilerini, hataları, Canbus mesajlarını ve Bluetooth komutlarını kaydeder, konum kaydetmez. Normal sürün, sonra kaydı aktarın.");
        p("Tyre pressure settings", "Lastik basıncı ayarları");
        p("On: the trip pages look like the original MIB 2 with OBD data and the launcher may send the tyre reset and Bluetooth keys to the unit. Off: the current view, nothing is sent.", "Açık: yolculuk sayfaları orijinal MIB 2 gibi görünür ve OBD verilerini gösterir; launcher lastik sıfırlama ve Bluetooth tuşlarını üniteye gönderebilir. Kapalı: mevcut görünüm, hiçbir şey gönderilmez.");
        p("After adjusting the pressure, press Reset tyre pressure.", "Basıncı ayarladıktan sonra Lastik basıncını sıfırla düğmesine basın.");
        p("Reset tyre pressure", "Lastik basıncını sıfırla");
        p("Switch on \"Take trip data from OBD\" in Setup → Vehicle", "Ayarlar → Araç bölümünde \"Yolculuk verilerini OBD'den al\" seçeneğini açın");
        p("Reset the tyre pressure monitor? The pressure must be set.", "Lastik basıncı izleme sıfırlansın mı? Basınç ayarlanmış olmalı.");
        p("Command sent", "Komut gönderildi");
        p("Could not send", "Gönderilemedi");

        p("Searching for the address…", "Adres aranıyor…");
        p("Calculating the route…", "Rota hesaplanıyor…");
        p("Finding places nearby…", "Yakındaki yerler bulunuyor…");

        // ---- Nearby places (NavScreen.kinds) ----
        p("Fuel station", "Benzin istasyonu");
        p("Parking", "Otopark");
        p("EV charger", "Elektrikli araç şarj istasyonu");
        p("Car repair", "Oto servis");
        p("Café", "Kafe");
        p("Restaurant", "Restoran");
        p("Pharmacy", "Eczane");
        p("ATM", "ATM");

        // ---- Colour profiles (DisplayControl.PROFILES) ----
        p("Standard", "Standart");
        p("Vivid", "Canlı");
        p("Soft", "Yumuşak");
        p("Warm", "Sıcak");
        p("Cool", "Soğuk");

        // ---- Ambient colours (Mib.AMBIENT) ----
        p("Blue (MIB)", "Mavi (MIB)");
        p("Ice blue", "Buz mavisi");
        p("Light blue", "Açık mavi");
        p("Royal blue", "Kraliyet mavisi");
        p("Deep blue", "Koyu mavi");
        p("Indigo", "Çivit mavisi");
        p("Violet", "Menekşe");
        p("Lavender", "Lavanta");
        p("Purple", "Mor");
        p("Magenta", "Macenta");
        p("Pink", "Pembe");
        p("Rose", "Gül");
        p("Red", "Kırmızı");
        p("Ruby", "Yakut");
        p("Coral", "Mercan");
        p("Orange", "Turuncu");
        p("Amber", "Kehribar");
        p("Gold", "Altın");
        p("Yellow", "Sarı");
        p("Lime", "Misket limonu");
        p("Green", "Yeşil");
        p("Emerald", "Zümrüt");
        p("Mint", "Nane");
        p("Turquoise", "Turkuaz");
        p("Cyan", "Camgöbeği");
        p("Teal", "Petrol yeşili");
        p("White", "Beyaz");
        p("Silver", "Gümüş");
        p("Warm white", "Sıcak beyaz");
        p("Cool white", "Soğuk beyaz");
    }
}
