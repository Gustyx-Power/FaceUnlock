# Universal Face Unlock

Aplikasi Face Unlock untuk Android yang bekerja di semua perangkat dengan kamera depan.

## Fitur

- 📷 **Face Enrollment** - Daftarkan wajah menggunakan kamera depan
- 🔓 **Auto Unlock** - Otomatis unlock saat wajah terdeteksi di lock screen
- ⚡ **Fast Detection** - Menggunakan ML Kit untuk deteksi cepat
- 🌐 **Universal** - Bekerja di semua perangkat Android 8.0+

## Cara Kerja

1. **Daftarkan Wajah** - Buka app, tap "Daftarkan Wajah", dan ikuti instruksi
2. **Aktifkan Accessibility** - App memerlukan Accessibility Service untuk melakukan swipe unlock
3. **Izinkan Overlay** - Diperlukan untuk menampilkan status deteksi di lock screen
4. **Toggle ON** - Aktifkan Face Unlock dari switch di app

## Flow Unlock

```
[Setelah Reboot] → [Masukkan PIN/Pattern] → [Device Unlocked]
                                                    ↓
                               [Face Unlock Aktif untuk sesi berikutnya]

[Lock Screen] → [Kamera Detect Wajah] → [Match?]
                                           ↓
                            [Ya] → Auto Swipe → [Unlocked!]
                            [Tidak] → Manual PIN/Pattern
```

## Persyaratan

- Android 8.0 (Oreo) atau lebih baru
- Kamera depan
- Permission: Camera, Accessibility, Overlay

## Catatan Keamanan

⚠️ Face Unlock ini menggunakan deteksi wajah 2D dan **kurang aman** dibandingkan:
- Fingerprint
- Face ID dengan depth sensor
- PIN/Pattern

Disarankan tetap gunakan PIN/Pattern sebagai backup.

## Build

1. Buka project di Android Studio
2. Sync Gradle
3. Build → Build APK

## License

MIT License
