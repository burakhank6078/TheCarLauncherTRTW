# Sürüm notları / Release notes (TR)

Her upstream (LazarC11/TheCarLauncher) sürümünden sonra buraya Türkçe bülten eklenir.
Yeni eklenen her özellik için `Tr.java` içindeki eksik çeviriler de tamamlanır.

## Sürüm sonrası kontrol listesi
1. `git fetch upstream && git merge upstream/main`
2. `powershell -ExecutionPolicy Bypass -File tools/check-translations.ps1` — eksik Türkçe metinleri listeler
3. Eksikleri `app/src/main/java/com/thecarlauncher/mib/Tr.java` içine ekle
4. Aşağıya yeni sürümün bültenini (yeni özellikler + düzeltmeler) Türkçe yaz
5. Commit

## Türkçe dil desteği (ilk sürüm)
- Ayarlar → Dil bölümüne **Türkçe** eklendi (bayrak, sesli navigasyon, sokak adları dahil).
- Arayüz VW MIB 2 görünümünü korur; yalnızca metinler Türkçeleşti.
