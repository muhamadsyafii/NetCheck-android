# NetCheck Android

NetCheck adalah aplikasi dashboard Android untuk memantau ketersediaan dan kualitas koneksi internet secara terus-menerus selama aplikasi terbuka. Aplikasi dapat memeriksa jalur Wi-Fi, kabel LAN/Ethernet, dan data seluler/SIM secara terpisah, menjalankan speed test manual, serta menjaga layar tetap menyala untuk kebutuhan monitoring.

Proyek dibangun menggunakan Kotlin, Jetpack Compose, dan Material 3.

## Fitur

- Status internet utama: `ONLINE`, `OFFLINE`, atau sedang diperiksa.
- Pemeriksaan koneksi otomatis dengan interval 5, 10, atau 30 detik.
- Pemeriksaan setiap jenis koneksi secara terpisah:
  - Wi-Fi.
  - LAN/Ethernet, termasuk adaptor USB-C Ethernet yang dikenali Android.
  - Data seluler dari kartu SIM.
- Validasi internet nyata melalui koneksi HTTPS, bukan hanya mengecek apakah perangkat tersambung ke jaringan.
- Fallback menggunakan validasi internet bawaan Android.
- Speed test manual untuk:
  - Kecepatan download.
  - Kecepatan upload.
  - Latensi.
- Informasi jalur jaringan aktif dan alamat IP lokal.
- Riwayat 12 pemeriksaan terakhir.
- Pilihan agar layar tetap menyala selama aplikasi terbuka.
- Bagian detail jaringan dan pengaturan monitor yang dapat dibuka atau ditutup.
- Tampilan gelap yang sesuai untuk dashboard monitoring jangka panjang.
- Dukungan edge-to-edge dengan safe area untuk status bar dan navigation bar.

## Tampilan dan alur penggunaan

Saat aplikasi dibuka, monitor otomatis aktif dengan interval awal 5 detik.

1. Bagian atas menampilkan status internet utama.
2. Kartu metrik menampilkan hasil download, upload, dan latensi terakhir.
3. Ketuk **Detail jaringan** untuk melihat status Wi-Fi, LAN/Ethernet, data seluler/SIM, IP lokal, dan riwayat pemeriksaan.
4. Centang atau hapus centang **Monitor otomatis** untuk mengaktifkan atau menghentikan pemeriksaan berkala.
5. Ketuk bagian **Monitor otomatis** untuk membuka pilihan interval.
6. Aktifkan **Layar selalu aktif** agar perangkat tidak masuk ke mode tidur selama aplikasi tampil.
7. Tekan **Jalankan Speed Test** untuk mengukur koneksi secara manual.

## Arti status koneksi

| Status | Arti |
|---|---|
| `ONLINE` | Jalur berhasil mengakses server publik melalui HTTPS atau telah divalidasi oleh Android. |
| `TANPA INTERNET` | Perangkat terhubung ke jaringan tersebut, tetapi akses internet tidak dapat divalidasi. |
| `TIDAK TERHUBUNG` | Android tidak mendeteksi jalur jaringan tersebut sebagai jaringan yang tersedia. |
| `MEMERIKSA…` | Proses validasi internet sedang berjalan. |

Status utama dianggap `ONLINE` apabila setidaknya satu jalur jaringan yang tersedia berhasil mengakses internet. Status Wi-Fi, LAN, dan data seluler tetap ditampilkan secara terpisah pada detail jaringan.

## Cara kerja pengecekan internet

`NetworkChecker` mengambil seluruh jaringan yang diketahui Android melalui `ConnectivityManager`, kemudian mengelompokkannya berdasarkan transport:

- `TRANSPORT_WIFI` untuk Wi-Fi.
- `TRANSPORT_ETHERNET` untuk LAN/Ethernet.
- `TRANSPORT_CELLULAR` untuk data seluler/SIM.

Setiap jalur diperiksa secara paralel. Request HTTPS diikat ke objek `Network` terkait menggunakan `Network.openConnection()`. Dengan cara ini, pemeriksaan Wi-Fi tidak secara tidak sengaja melewati data seluler, dan sebaliknya.

Endpoint pemeriksaan yang digunakan:

- `https://connectivitycheck.gstatic.com/generate_204`
- `https://clients3.google.com/generate_204`
- `https://www.cloudflare.com/cdn-cgi/trace`

Jika endpoint tidak dapat diakses karena DNS, firewall, atau kebijakan jaringan, aplikasi menggunakan `NET_CAPABILITY_VALIDATED` dari Android sebagai fallback. Respons HTTP dari server publik membuktikan bahwa resolusi jaringan, koneksi, dan TLS berhasil dilakukan.

### Latensi

Latensi dihitung dari waktu membuka koneksi sampai server memberikan respons HTTP. Aplikasi mengutamakan latensi jalur aktif. Jika jalur aktif tidak memberikan hasil, aplikasi menggunakan hasil jalur online lain yang tersedia.

### Speed test

Speed test manual menggunakan layanan Cloudflare:

- Download sekitar 10 MB melalui endpoint `__down`.
- Upload sekitar 2 MB melalui endpoint `__up`.
- Total pemakaian data sekitar 12 MB untuk satu kali pengujian, di luar overhead jaringan.

Speed test memakai jalur jaringan default/aktif Android. Jika ingin menguji data seluler secara khusus, nonaktifkan Wi-Fi dan LAN terlebih dahulu. Jika ingin menguji Wi-Fi atau LAN, pastikan jalur tersebut menjadi koneksi aktif.

## Catatan tentang data seluler/SIM

Aplikasi tidak membaca nomor telepon, identitas SIM, operator, IMEI, atau informasi pribadi lainnya. Aplikasi hanya mendeteksi apakah Android menyediakan jaringan dengan transport data seluler dan apakah jaringan tersebut memiliki akses internet.

Pada beberapa perangkat, Android dapat menidurkan jaringan seluler saat Wi-Fi aktif. Dalam kondisi tersebut, data seluler mungkin tampil `TIDAK TERHUBUNG` walaupun kartu SIM terpasang dan paket data aktif. Aktifkan data seluler dan nonaktifkan Wi-Fi sementara jika ingin memastikan koneksi SIM secara khusus.

## Layar selalu aktif

Fitur ini menggunakan:

```kotlin
WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
```

Efeknya hanya berlaku selama activity NetCheck tampil di layar. Fitur ini:

- Mencegah layar tidur karena timeout.
- Tidak melewati PIN, pola, sidik jari, atau sistem keamanan lock screen.
- Tidak membuat aplikasi menjadi foreground service.
- Tidak menjamin aplikasi tetap berjalan jika pengguna menutup aplikasi atau sistem menghentikan proses.

Untuk perangkat kiosk atau monitoring permanen, pengembangan berikutnya dapat menambahkan Lock Task Mode, foreground service, auto-start setelah boot, dan kebijakan battery optimization sesuai kebutuhan deployment.

## Persyaratan proyek

| Komponen | Nilai |
|---|---|
| Android Studio | Versi yang mendukung AGP 9.2.1 dan Android API 37 |
| JDK | Java 11 atau lebih baru yang kompatibel dengan Android Gradle Plugin |
| Compile SDK | 37 |
| Target SDK | 36 |
| Minimum SDK | 24 (Android 7.0) |
| Kotlin | 2.2.10 |
| Android Gradle Plugin | 9.2.1 |
| Jetpack Compose BOM | 2026.02.01 |

Pastikan **Android SDK Platform 37** sudah terpasang melalui Android Studio:

1. Buka **Tools > SDK Manager**.
2. Pilih tab **SDK Platforms**.
3. Aktifkan Android API 37.
4. Terapkan instalasi.

## Menjalankan proyek

### Melalui Android Studio

1. Clone atau buka folder proyek.
2. Tunggu Gradle Sync selesai.
3. Pilih emulator atau perangkat Android fisik.
4. Tekan **Run**.
5. Pastikan perangkat memiliki koneksi Wi-Fi, LAN, atau data seluler untuk melakukan pemeriksaan.

### Melalui terminal

Build APK debug:

```bash
./gradlew assembleDebug
```

APK akan tersedia di:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Instal ke perangkat yang terhubung:

```bash
./gradlew installDebug
```

Menjalankan unit test:

```bash
./gradlew test
```

Menjalankan instrumented test pada perangkat/emulator:

```bash
./gradlew connectedAndroidTest
```

## Permission

Permission yang digunakan pada `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

| Permission | Kegunaan |
|---|---|
| `INTERNET` | Mengakses endpoint validasi dan speed test. |
| `ACCESS_NETWORK_STATE` | Membaca jaringan aktif, jenis transport, link properties, dan status validasi Android. |

Kedua permission merupakan permission normal dan tidak memerlukan dialog persetujuan runtime.

## Struktur proyek

```text
NetCheck-android/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/pilkupil/netcheck/
│       │   │   ├── MainActivity.kt
│       │   │   ├── network/
│       │   │   │   └── NetworkChecker.kt
│       │   │   └── ui/
│       │   │       ├── NetCheckDashboard.kt
│       │   │       └── theme/
│       │   │           ├── Color.kt
│       │   │           ├── Theme.kt
│       │   │           └── Type.kt
│       │   └── res/
│       ├── test/
│       └── androidTest/
├── gradle/
│   └── libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Komponen utama

### `MainActivity.kt`

- Menyiapkan Jetpack Compose.
- Menampilkan `NetCheckDashboard`.
- Menambah atau menghapus `FLAG_KEEP_SCREEN_ON` berdasarkan pilihan pengguna.

### `NetworkChecker.kt`

- Membaca jaringan melalui `ConnectivityManager`.
- Mendeteksi Wi-Fi, Ethernet, dan data seluler.
- Mengikat request validasi ke masing-masing jaringan.
- Menggunakan beberapa endpoint dan validasi Android sebagai fallback.
- Menghitung latensi, download Mbps, dan upload Mbps.
- Menjalankan operasi jaringan pada `Dispatchers.IO`.

### `NetCheckDashboard.kt`

- Menyimpan state tampilan Compose.
- Menjalankan monitor otomatis menggunakan `LaunchedEffect`.
- Menampilkan status, metrik, riwayat, pengaturan interval, dan speed test.
- Menyediakan bagian expandable agar halaman tidak terlalu panjang.
- Menambahkan padding navigation bar untuk tampilan edge-to-edge.

## Model status dan state

State penting yang digunakan pada dashboard:

- `automaticCheck`: mengaktifkan pemeriksaan berkala.
- `intervalSeconds`: interval pemeriksaan 5, 10, atau 30 detik.
- `status`: status umum `CHECKING`, `ONLINE`, atau `OFFLINE`.
- `networkInfo`: informasi jalur aktif dan status setiap transport.
- `latencyMs`: latensi terakhir.
- `downloadMbps`: hasil download terakhir.
- `uploadMbps`: hasil upload terakhir.
- `history`: maksimal 12 hasil pemeriksaan terakhir.
- `keepScreenOn`: pilihan agar layar tetap aktif.

Sebagian state menggunakan `rememberSaveable`, sehingga pilihan dasar tetap dipertahankan saat terjadi perubahan konfigurasi seperti rotasi layar. Data tidak disimpan permanen setelah proses aplikasi dihentikan.

## Troubleshooting

### Dependency membutuhkan compileSdk 37

Contoh pesan:

```text
Dependency requires libraries and applications that depend on it to compile against version 37 or later
```

Solusi:

- Pastikan `compileSdk` menggunakan API 37.
- Instal Android SDK Platform 37 melalui SDK Manager.
- Lakukan Gradle Sync kembali.

`targetSdk` tidak harus dinaikkan bersama `compileSdk` karena keduanya memiliki fungsi berbeda.

### Jaringan tersambung tetapi tampil tanpa internet

Periksa hal berikut:

- Pastikan perangkat dapat membuka situs HTTPS melalui browser.
- Pastikan tanggal dan waktu perangkat benar agar TLS tidak gagal.
- Periksa apakah jaringan memakai captive portal/login Wi-Fi.
- Periksa DNS, VPN, firewall, ad blocker, atau Private DNS.
- Tunggu sampai pemeriksaan otomatis berikutnya selesai.
- Coba nonaktifkan lalu aktifkan kembali jalur jaringan terkait.

Aplikasi mencoba tiga endpoint publik dan memakai validasi Android sebagai fallback untuk mengurangi false offline.

### Data seluler tidak terdeteksi saat Wi-Fi aktif

Android dapat menonaktifkan jaringan seluler aktif ketika Wi-Fi digunakan. Untuk mengujinya:

1. Pastikan mobile data aktif.
2. Nonaktifkan Wi-Fi sementara.
3. Tunggu 5–10 detik.
4. Buka detail jaringan dan periksa status **Data seluler / SIM**.

### LAN tidak terdeteksi

- Pastikan adaptor Ethernet didukung oleh perangkat Android.
- Periksa kabel, adaptor, dan indikator link.
- Pastikan Android menampilkan ikon atau status Ethernet.
- Beberapa perangkat membutuhkan adaptor USB-C dengan daya eksternal.

### Halaman tertutup navigation bar

Dashboard menggunakan `navigationBarsPadding()` dan dapat digulir. Pastikan aplikasi yang terpasang merupakan build terbaru jika tombol bagian bawah masih tertutup.

### Speed test gagal

- Pastikan status koneksi sudah online.
- Periksa apakah domain `speed.cloudflare.com` diblokir jaringan.
- Coba jalur jaringan lain.
- Perhatikan bahwa satu speed test memakai sekitar 12 MB data.

## Privasi dan penggunaan data

- Aplikasi tidak mengumpulkan nomor telepon, identitas SIM, kontak, lokasi, atau identitas perangkat.
- Aplikasi tidak memiliki backend milik sendiri.
- Request validasi dikirim langsung ke Google Connectivity Check dan Cloudflare.
- Speed test dikirim langsung ke Cloudflare.
- Penyedia endpoint dapat melihat informasi jaringan standar seperti alamat IP publik dan user-agent.
- Riwayat pemeriksaan hanya disimpan sementara di memori aplikasi dan tidak dikirim ke server aplikasi.

Sebelum distribusi publik, tinjau kembali kebijakan privasi dan ketentuan penggunaan penyedia endpoint.

## Keterbatasan saat ini

- Monitoring berjalan saat layar Compose aktif; belum menggunakan foreground service.
- Belum ada notifikasi ketika koneksi terputus.
- Belum ada penyimpanan histori permanen.
- Belum ada grafik kecepatan dari waktu ke waktu.
- Speed test selalu menggunakan jalur default Android, belum dapat memilih jalur dari UI.
- Belum ada auto-start setelah perangkat reboot.
- Belum ada mode kiosk/Lock Task Mode.
- Status SIM menunjukkan jaringan data seluler, bukan status fisik kartu atau informasi operator.

## Ide pengembangan berikutnya

- Pilihan speed test khusus Wi-Fi, LAN, atau data seluler.
- Foreground service untuk monitoring saat aplikasi berada di background.
- Notifikasi ketika internet putus atau kembali normal.
- Penyimpanan histori menggunakan Room.
- Grafik uptime, downtime, latensi, dan speed test.
- Export laporan CSV atau JSON.
- Pengaturan endpoint pengecekan dari UI.
- Mode kiosk dan auto-start setelah boot untuk perangkat monitoring khusus.
- Threshold kualitas jaringan: baik, lambat, atau tidak stabil.
- Dukungan tema terang dan gelap.
- Lokalisasi string melalui resource `strings.xml`.

## Testing

Proyek menyediakan struktur test berikut:

- Unit test di `app/src/test`.
- Instrumented/UI test di `app/src/androidTest`.

Area yang disarankan untuk diuji:

- Perhitungan Mbps.
- Pemilihan jaringan berdasarkan transport.
- Perubahan status online/offline.
- Batas maksimal 12 item riwayat.
- Perubahan interval monitor.
- State expandable/collapsible.
- Perilaku layar selalu aktif.
- Layout pada ukuran layar dan mode navigasi Android yang berbeda.

## Kontribusi

Saat menambahkan fitur:

1. Jaga operasi jaringan tetap berada di `Dispatchers.IO`.
2. Hindari request besar pada monitor otomatis.
3. Gunakan speed test hanya melalui tindakan pengguna.
4. Pastikan setiap request memiliki timeout.
5. Pertahankan pemeriksaan per transport agar hasil tidak berpindah jalur secara tidak sengaja.
6. Uji pada perangkat fisik karena perilaku Wi-Fi, Ethernet, dan data seluler dapat berbeda antar produsen.

## Lisensi

Lisensi proyek belum ditentukan. Tambahkan file `LICENSE` sebelum proyek didistribusikan atau menerima kontribusi publik.
