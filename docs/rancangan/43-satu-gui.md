## 43. Satu Gulungan (satu GUI untuk semua fitur)

Permintaan pengguna (2026-10-25, bahasa Indonesia, mengikat): sederhanakan dan perbaiki **semua** GUI mod ini supaya
**satu GUI** mencakup semua fitur; audit setiap GUI dari awal sampai sekarang; dan "jangan sampai ada GUI yang berbeda
padahal fungsinya sama".

Bagian ini adalah kontrak implementasi lengkap untuk **satu gelombang kerangka (3 agen), dua gelombang migrasi (16 + 6
agen) dan satu gelombang poles (8 agen)** (43.16). Semua nama kelas, metode, id halaman, kunci lang, tombol dan angka di
bawah adalah nama yang **harus** dipakai. Yang ditandai "(verifikasi)" sudah dicek polanya di kode tetapi nama persisnya
wajib dicocokkan lagi dengan kode mod atau `butterbot-fabric/reference/mcsrc` (26.3, official mappings) sebelum dipakai.
Bagian ini berdiri sendiri di `docs/rancangan/43-satu-gui.md`; `docs/DESIGN.md` dan `docs/PANDUAN.md` hanya disunting oleh
agen finish 43 (satu baris rujukan + satu bab panduan).

Ringkasnya: `screen/CultivationScreen` (gulungan giok, "Gulungan Kultivasi") menjadi **Gulungan Utama**, satu-satunya
layar mod untuk semua menu. Tujuh layar bingkai lain (Aula, Murid, Dunia, Keabadian, Kitab Teknik, Kitab Panduan, Duel)
dan sekitar 28 layar tunggal menjadi **halaman** di dalamnya; sekitar 50 layar kit "Pasar Giok" (18 inti + 20 tema 38,
11 dari 41, dan layar 42 yang sedang dibangun) menjadi halaman yang sama, dibuka langsung di halaman yang tepat saat
pemain mengklik NPC, blok atau item. Satu basis halaman (`Page`, publik),
satu kit widget (`screen/ui`), satu pola konfirmasi, satu gaya HUD. Hanya dua layar wadah vanilla (`ForgeScreen`,
`RingScreen`) tetap terpisah karena slot, dan memakai rupa serta kit yang sama. Setiap kelompok "Fungsi ganda" dari lima
laporan audit punya tepat satu yang bertahan (43.11). Tidak ada berkas yang dihapus: yang tidak dipakai diganti nama
`*.deactivation`.

---------------------------------------------------------------------------------------------------------------------

### 43.0 Ruang lingkup dan aturan mengikat (tambahan atas DESIGN 0, 38.0, 41.0, 42.0)

1. **Dibangun setelah 41 + 42 selesai.** Prasyarat: jadwal `docs/progres/jadwal-41-42.md` selesai sampai F-END (42-G2,
   D-41-42) dan di-commit. Sampai itu semua berkas di bawah hanya dibaca. Layar kota 42 yang sekarang sedang dibangun
   sebagai tab layar lama (jadwal 0.1 "No GUI twice") **ikut** dipetakan di sini lewat kunci `KotaLembar` (43.12.4),
   bukan lewat nama kelas, sehingga apa pun nama kelas yang dibuat 42U-1/42U-2 tetap tertampung.
2. **Klien dulu, kontrak server tetap.** Payload, kunci lembar (`LembarKunci`, `GabungLembar`, `TemaLayar`,
   `KotaLembar`), aksi (`Actions.*`, `TempatAksiPayload`, `KotaNet.Action`, `DimensiNet.Action`, ...) dan data tidak
   berubah. Perubahan server dibatasi pada daftar tertutup 43.14 (lima suntingan kecil). Tidak ada LLM.
3. **Satu layar.** Setelah 43, kode klien membuka layar mod hanya lewat `HubNav.buka(Tujuan)`. Panggilan
   `minecraft.gui.setScreen(new <LayarMod>(...))` hanya tersisa untuk `CultivationScreen` sendiri; `ForgeScreen` /
   `RingScreen` tetap dibuka vanilla oleh `MenuScreens`. Layar vanilla yang boleh dibuka: `ChatScreen` (chip petunjuk),
   `KeyBindsScreen` (tautan "Semua tombol Minecraft"). Pemeriksa: `grep -rn "setScreen(new" src/client` di finish.
4. **Paling banyak tiga tingkat navigasi:** Bagian (rel kiri) > Halaman (koin kepala) > Sub (bilah sub). Di dalam sub,
   pengelompokan hanya boleh berupa **judul bertumpuk dalam satu gulir** atau **saringan** (`Saring`: cari + chip jenis),
   tidak pernah bilah tab keempat, kolom segel kanan, `SectionPicker` atau layar anak. Tidak ada kartu yang hanya membuka
   layar lain berisi tab yang sama.
5. **Teks terbaca.** Teks digambar pada skala 1.0 (isi) atau 1.5 (judul). `pose().scale` < 1.0 untuk teks dan pengecilan
   seluruh layar (`DiplomasiScreen`, `SeteruScreen` sekarang) **dilarang**. `Brush.fit` tidak lagi mengecilkan; ia
   memotong dengan "…" dan teks penuh muncul di `SlipTooltip`. Uji `uji ui skala N` (38.U.2) tetap ada dan sekarang
   berlaku untuk seluruh Gulungan Utama.
6. **Ukuran.** Wajib muat di 1366×768 pada skala GUI 4 (= 341×192 px GUI) tanpa tumpang tindih dan tanpa guliran
   mendatar; setiap tombol yang bisa diklik paling kecil 16×12 px GUI (sekarang ada 11×11 dan 32×11).
7. **Semua bisa dengan papan ketik** (fokus Tab, Enter/Spasi, panah), seperti kit 38.U sekarang.
8. **Lang.** Teks baru di `lang-src/gui43.id_id.json` + `.en_us.json` (kerangka, kit, Gulungan Utama) dan
   `lang-src/gui43_<label>.*` per agen migrasi (43.15). Kunci identik dua bahasa, Indonesia utama, tiap kunci tepat di satu
   fragmen (`mergeLang` gagal bila ganda). Kunci lama yang masih dipakai **tidak dipindah**; kunci lama yang tak dipakai
   lagi dibiarkan (tidak dihapus) dan dicatat di berkas progres.
9. **Berkas.** Tidak pernah dihapus; berkas yang tak dipakai diganti nama `<Nama>.java.deactivation` (gradle tidak
   mengompilasinya). Berkas baru LF; berkas lama mempertahankan akhir barisnya (`KultivatorClient.java` CRLF; cek
   `git ls-files --eol`).
10. **Gradle hanya agen finish**: `gradlew compileJava compileClientJava jar --no-daemon`. Agen modul tidak menjalankan
    Gradle, tes, `runClient` atau `gradlew build`. Progres per agen di `docs/progres/m2-43<label>.md` (daftar centang,
    "Langkah berikutnya", "Butuh dari modul lain", < ~150 baris, LF, diperbarui setiap sub-tugas).
11. **Dunia baru, tanpa kompatibilitas simpanan.** Konfigurasi klien (`config/kultivator-client.json`) boleh diberi satu
    langkah migrasi tombol (43.10.3); tidak ada shim lain.
12. **Laptop lemah.** Halaman yang tidak tampil tidak menggambar dan tidak meminta data; meminta data hanya saat tampil
    (`onShow`) dan dengan selang yang sama dengan sekarang (Aula 4 s, Murid per N tick, Jaring 5 s).

---------------------------------------------------------------------------------------------------------------------

### 43.1 Fakta dari kode (audit 2026-10-25; kode adalah kebenaran)

Sumber: lima laporan audit di `side-session-notes/.../gui-audit-{1-inti,2-sekte,3-dunia,4-tempur,5-rencana}.md`; yang
menentukan keputusan dicek ulang di kode untuk desain ini.

* **Layar mod (kelas `Screen`)**: 97 kelas (grep `extends`, 2026-10-25): 36 turunan langsung `Screen` (bingkai bertab
  `CultivationScreen`, `HallScreen`, `DiscipleScreen`, `DuniaScreen`, `CodexScreen`, `GuideScreen`, `DuelScrollScreen`,
  basis kit `LayarTempatDasar` dan 28 layar tunggal), 2 turunan `DuniaScreen` (`KeabadianScreen`, `KotaScreens.Base` →
  `TownScreen`/`AbadiScreen`/`MeetingScreen`), 2 turunan `DuelScrollScreen` (`DuelScreen`, `ChallengeScreen`), 2 layar
  wadah (`ForgeScreen`, `RingScreen`), 18 layar inti 38, 20 layar tema 38 (+`LayarTemaDasar`), 11 layar 41
  (+`LayarGabungDasar`). 42: kunci 19 lembar dan 2 HUD dibekukan di `S: dunia/kota42/ui/KotaLembar.java`; 42U-2 sedang
  membuat kelas klien (sudah ada `client/dunia/kota42/ui/layar/LayarKavling.java`), maka 43 memetakan 42 per kunci.
* **Basis halaman ganda**: `screen/Page` (**package-private**, hanya klik/lepas/gulir; tanpa `keyPressed`, `charTyped`,
  `mouseDragged`), `hall/HallPage`, `dunia/DuniaPage` (+`dunia/kota/KotaPage`), `codex/CodexPage`,
  `disciple/DiscipleTab` (maks 3 tab tambahan, penuh), `sectevent/SectEventPage`, dan model tab `LayarTempatDasar`
  (diverifikasi: 56 kelas halaman/tab).
* **Gulungan sekarang**: kertas maks 440×284 (`CultivationScreen.MAX_W/MAX_H`), 8 bagian (`HubNav.DIRI..PENGATURAN`
  = 0..7), tombol 1-8, Tab = koin. Modul lain hanya bisa menambah **kartu peluncur** (`hub/HubCards.register`, 29 panggilan;
  Dunia 12 kartu dan Sekte › Aula 12 kartu, melewati anggaran 9).
* **Kit "Pasar Giok"** (`tempat/ui/kit`, 24 kelas): imediat (`Lukis` + `Zona`), fokus papan ketik, tiga tata
  (SEMPIT < 320, SEDANG < 480, LEBAR), mode baca-saja hub (`npcId -1`, `OP_LIHAT`). Semua lembar masuk lewat
  `UiTempatClient.terima` dan pabrik `Pembuat` per kunci; ditutup dengan `OP_TUTUP` dari `removed()` (diverifikasi).
* **Data bisa dari mana saja**: Aula (`Actions.HALL OP_OPEN`), murid (`DISCIPLE_OPEN <id> "refresh"` mengirim
  `DiscipleInfo` tanpa membuka layar, jarak lihat `VIEW_RANGE`; diverifikasi di `Disciples.handleAction`), Dunia/Abadi
  (`sync`), lembar 38/41/42 (`OP_LIHAT`). Jadi hampir semua layar bisa menjadi halaman.
* **Yang benar-benar terikat dunia**: dua menu wadah (slot), sesi lembar ber-jangkar (`TempatLayar.JANGKAU`), percakapan
  NPC, kemudi kapal, prompt reruntuhan, hologram templat sekte.
* **Kunci** (diverifikasi): G cast, V quick, B meditasi, Z seal berikutnya, H pedang terbang, M murid, Backspace lewati
  adegan; `info`, `previous`, `codex`, `wheel`, `seal_1..4`, `dunia`, `cincin`, `abadi`, `guide`, `peta`, `parry`,
  `duel_camera` tanpa tombol bawaan. `Keys.all()` kode mati.
* **`/kultivasi duel layar`** hanya mencetak baris obrolan (`S: duel/DuelCommands.java` `screen(...)`; diverifikasi).
  Tombol Duel Catatan/Hormat/Menyerah/Tunda mengirim perintah obrolan (`DuelScreen.command`), Aula "Saudara Sekte"
  mengirim `sect info` dan menutup layar (`AulaPage`); `HallInfo` adalah `CompoundTag` (diverifikasi).
* **Bukan duplikat (diverifikasi)**: tab "Utusan" di `sectevent/MissionPage` adalah Pencari Bakat (mengirim murid
  merekrut), bukan tawaran utusan sekte NPC di `DiplomasiScreen`. `goals/FormationBlueprint` sudah mengalah bila
  `formasi/PatternHologram.active()`.

---------------------------------------------------------------------------------------------------------------------

### 43.2 Prinsip

1. **Satu tempat per fungsi.** Setiap fungsi punya satu halaman (atau satu sub) sebagai rumahnya. Bila satu data dilihat
   dari dua peran (pemilik aula / anggota sekte NPC), halamannya tetap satu dan menampilkan chip **Sumber** (Aulaku /
   Perguruanku) hanya bila keduanya ada.
2. **Satu kelas boleh dipasang dengan himpunan tab berbeda** (mis. `LayarBukuKas` tab Kas di Sekte, tab Milikku di Diri):
   itu satu GUI, bukan dua, asalkan setiap tab hanya muncul di satu tempat.
3. **Kategori bukan navigasi.** Tab lembar yang berupa kategori barang (Teknik/Pil/Pusaka...) tampil sebagai chip
   `Saring` bila halaman sudah di tingkat 3.
4. **Konteks datang ke halaman.** Klik NPC/blok/item membuka Gulungan Utama langsung di halaman rumah fungsi itu dengan
   konteks hidup (tulis aktif). Fungsi yang hanya ada di dunia (toko senjata, bank, teh, ...) punya rumah di tablet
   **Di Sini** (43.8).
5. **Hapus, jangan tambah.** Layar baru tidak dibuat; layar yang ada dipindah menjadi halaman. Kelas baru hanya: Gulungan
   (registri, tujuan, konteks), widget kit yang menyatukan beberapa widget lama, dan jembatan data klien (43.8.4).

---------------------------------------------------------------------------------------------------------------------

### 43.3 Gulungan Utama (host `screen/CultivationScreen`)

#### 43.3.1 Tata letak

```
 +--+ +-----------------------------------------------------------------------------+
 |Di| | <  SEKTE . Kas & Toko      (Ikhtisar)(Anggota)(Bangun)(Formasi)[Kas](...)  ? |  kepala: kembali, judul,
 |si| +-----------------------------------------------------------------------------+         koin halaman, segel "?"
 +--+ |  [Kas]  (Toko PK)  (Rak Kitab)  (Makam)  (Kafilah)          Sumber: [Aulaku]v |  bilah sub (+ chip Sumber)
 |身| +--------------------------------+--------------------------------------------+
 |技| | / cari...         [Jenis: Semua v]| Pil Penyatuan Qi               Kelas 3      |
 |徒| | > Pil Penyatuan Qi       120 PK  |  .-----------.  Efek: ...                  |
 |宗| |   Kitab Angin Utara      300 PK  |  | pratinjau |  Syarat: Murid Dalam       |
 |界| |   Batu Roh Menengah       18 PK  |  '-----------'                              |
 |仙| |   ...                            |      [ - ]  3  [ + ]    Jumlah: 360 PK     |
 |书| |                                  |              [ Tukar ]                      |
 |设| +--------------------------------+--------------------------------------------+
 +--+ | Tukar berhasil: 3 Pil Penyatuan Qi.          Esc kembali  Q/E halaman  / cari |  kaki: pesan + petunjuk
      +-----------------------------------------------------------------------------+
 rel: 1-8 bagian (+9 Di Sini saat ada konteks); tablet terkunci digambar redup dengan alasan di tooltip
```

* **Rel kiri** (`TabBar` vertikal, rupa `JadeTablet`): 8 bagian tetap + tablet **Di Sini** di atas, hanya saat ada
  konteks hidup (berdenyut sekali saat muncul). Nama di samping glyph bila tinggi kertas ≥ 230 px, glyph saja di bawahnya.
* **Kepala**: kontrol kembali (`TombolKembali`, hanya bila halaman berada di tampilan rinci atau rantai konteks), judul
  bagian · halaman, koin halaman (`TabBar` mendatar, rupa koin giok), segel "?" (`KeyHints.HelpSeal`, topik
  `page.helpTopic()`), cincin uang (`LabelHarga` bergulir) bila halaman memintanya (`Page.uang()`).
* **Bilah sub**: hanya bila halaman punya sub (`Page.subs()`); chip **Sumber** di kanan bila halaman memintanya.
* **Isi**: kotak halaman. Halaman menata diri secara responsif lewat `Page.tata()` (SEMPIT / SEDANG / LEBAR dari lebar
  isi, aturan 38.U.2): SEMPIT satu kolom (daftar, rinci menggantikan daftar dengan `TombolKembali`), SEDANG daftar +
  rinci, LEBAR tiga kolom.
* **Kaki**: pesan hasil terakhir (6 s, dari `Page.pesan` atau lembar `pesan`) dan petunjuk tombol (`KeyHints`, hanya
  di SEDANG/LEBAR).
* **Lapisan atas**: `SegelKonfirmasi`, `PemilihOrang` mode sembul, `SlipTooltip` (satu per bingkai, digambar terakhir).
* **Latar**: malam tinta + gunung (sekarang). Varian latar per bagian dipertahankan sebagai **warna saja** (langit abadi
  untuk Keabadian, senja untuk Langit, lentera untuk konteks kota) lewat `Palette.Gaya`; tidak ada latar kedua.
* **Animasi buka**: gulungan membuka 250 ms hanya saat Gulungan dibuka dari dunia; pindah bagian/halaman tidak memutar
  ulang animasi (sekarang setiap layar anak memutar ulang dan "melompat").

#### 43.3.2 Ukuran (GUI px; `W×H` = ukuran layar terskala)

| Hal | Aturan |
|---|---|
| Kertas standar | `w = min(440, W − 20 − 12)`, `h = min(284, H − 8)` |
| Kertas lebar (halaman `Page.lebar() == true`: Kodeks, Cincin Segel, Peta, Buku Harian, Templat, Di Sini bertata LEBAR) | `w = min(560, W − 20 − 12)`, `h = min(330, H − 8)`; berubah halus 120 ms saat pindah |
| Tata layar | SEMPIT bila `W < 400` atau `H < 240`: rel glyph saja 18 px, koin glyph saja, kaki tanpa petunjuk; selain itu SEDANG/LEBAR |
| 1366×768 skala 4 (341×192) | rel 20 + kertas 309×184; isi ± 289×148; harus lulus uji 43.18 |
| Tinggi tablet | `clamp((h − 4) / jumlahTablet, 18, 36)` |
| Tombol | tinggi ≥ 12 (SEMPIT) / 14 (lainnya), lebar ≥ 16 |
| Teks | skala 1.0 dan 1.5 saja (43.0.5) |

---------------------------------------------------------------------------------------------------------------------

### 43.4 Navigasi dan tombol

#### 43.4.1 Urutan masukan (sama di semua halaman)

1. Lapisan atas terbuka (`SegelKonfirmasi`, sembul pemilih): menerima semua masukan; Esc menutupnya.
2. Halaman dengan kolom teks terfokus (`Page.mauTombol() == true`): menerima `keyPressed`/`charTyped` lebih dulu; hanya
   Esc (lepas fokus) dan V/`info` (tutup) yang diambil Gulungan.
3. Tombol Gulungan (43.4.2).
4. Halaman (fokus Tab, Enter/Spasi, panah, `/`, dan tombol halaman sendiri).

Mouse: klik kiri/kanan/tengah, lepas, seret, gulir **selalu** diteruskan ke halaman di dalam kotak isi (sekarang klik
kanan dan seret tidak diteruskan).

#### 43.4.2 Satu arti per tombol (semua bagian, semua halaman, semua konteks)

| Tombol | Arti di Gulungan | Catatan |
|---|---|---|
| `1`-`8` | bagian Diri, Teknik, Murid, Sekte, Dunia, Keabadian, Panduan, Pengaturan | sama dengan sekarang |
| `9` | tablet Di Sini | hanya saat ada konteks |
| `Q` / `E` | halaman sebelumnya / berikutnya (koin kepala) | |
| `Tab` / `Shift+Tab` | fokus berikutnya / sebelumnya (cincin emas) | dulu = koin di hub, fokus di kit; kini fokus saja |
| `←` `→` | pada bilah tab terfokus: pindah tab/sub; pada pemilih jumlah: ± ; di tempat lain: tidak dipakai | |
| `↑` `↓` `PgUp` `PgDn` `Home` `End` | daftar terfokus | |
| `Enter` / `Spasi` | jalankan yang terfokus; tekan-tahan pada segel konfirmasi | |
| `/` | fokus kolom cari halaman | |
| `Esc` | mundur satu langkah: tutup lapisan → tutup rinci/tampilan anak → tutup Gulungan (ke dunia, atau kembali ke Gulungan bila dibuka dari wadah, 43.8.5) | |
| V (`quick`) / `info` | tutup Gulungan seluruhnya | |
| Tombol bagian (`codex` K, `murid` M, `dunia`, `abadi`, `guide` J, `peta` N) | buka Gulungan di bagian/halaman itu; bila sudah tampil: tutup | M tidak lagi berarti "murid berikutnya"; pemilih murid memakai ← → |

Pilihan bernomor (dialog, teka-teki, putusan) **tidak** memakai angka (angka = bagian); angka tetap digambar sebagai tanda
urut, dipilih dengan ↑↓ + Enter atau klik (keputusan terbuka K3).

#### 43.4.3 Tujuan (`hub/Tujuan.java`, baru) dan `HubNav.buka`

```java
public record Tujuan(int bagian, String halaman, String sub, @Nullable CompoundTag fokus) {
    public static Tujuan of(int bagian, String halaman) { ... }          // sub "" = sub terakhir
    public Tujuan sub(String sub) { ... }  public Tujuan fokus(CompoundTag f) { ... }
}
// HubNav (diperluas): 
public static void buka(Tujuan t);                 // buka Gulungan atau pindah di dalamnya
public static void bukaKonteks(Page halaman, Konteks k);  // tablet Di Sini (43.8)
public static void antre(Tujuan t);               // buka saat tidak ada layar lain (pengganti 4 "pending")
public static void kembaliSetelah(Class<? extends Screen> layar); // wadah/ChatScreen: Esc kembali ke Gulungan
public static void tutupSemua();                  // V/info
```

Tujuan menyimpan halaman dan sub terakhir per bagian (seperti `HubNav.lastSection/lastSub` sekarang, per sesi). `fokus`
diteruskan ke `Page.fokus(CompoundTag)` (mis. `{"spec": netId}` untuk Kodeks, `{"murid": entityId}`, `{"sekte": id}`).

#### 43.4.4 Peta pembuka (setiap jalan masuk lama → Tujuan)

| Jalan masuk sekarang | Tujuan setelah 43 |
|---|---|
| V ketuk, `info`, tengah roda, `OpenScreen.INFO(tab)` (`/kultivasi jalan`, `/kultivasi resep`) | bagian terakhir / tab lama: 0 Status, 1 Akar & Dao, 2 Kodeks, 3 Pengaturan, 4 Ensiklopedia, 5 Jalan, 6 Alkimia |
| `OpenScreen.CHOOSE_DAO` (+ tunda `pendingDaoChoice`) | Diri › Akar & Dao lewat `HubNav.antre` |
| `OpenScreen.CODEX(id)`, tombol K, irisan roda "Kitab Teknik", `Codex.open/openTab` | Teknik › Kodeks (fokus id) / Cincin Segel / Kehancuran |
| `OpenScreen.DISCIPLE(id)` (klik kanan murid, M, irisan roda) | Murid › Murid (fokus id) |
| `OpenScreen.HALL(page)` (prasasti, calon NPC, inti formasi, `/kultivasi formasi katalog`, Langit "Rancang") | Sekte › halaman sesuai `Halls.PAGE_*` (43.5) |
| `OpenScreen.SECT_TEMPLATE` (prasasti ditaruh) | Sekte › Bangun › Templat |
| `OpenScreen.KEHANCURAN_CONFIRM` | `SegelKonfirmasi` berat di atas halaman sekarang (Teknik › Kehancuran bila Gulungan tertutup) |
| `OpenScreen.DUNIA(tab)`, `/kultivasi dunia` | 0 Dunia › Alam, 1 Diri › Pusaka, 2 Dunia › Musuh, 3 Sekte › Wilayah › Desa & Pakta, **4 Dunia › Duel (baru, 43.14 S1)** |
| `OpenScreen.ABADI(tab)`, NPC Juru Lelang Abadi | Keabadian › halaman (Lelang untuk NPC itu, dengan konteks) |
| `OpenScreen.TITAH` (item Segel Titah, `/kultivasi titah`) | Keabadian › Titah |
| `GuideSync` buka, tombol J, irisan roda Panduan | Panduan › Kitab |
| `PetaNet.Info(open)` (item peta), tombol N, Kemudi "buka peta" | Dunia › Peta (dari Kemudi: mode pilih tujuan, 43.8.3) |
| `TempatLayarPayload` (NPC 38/41/42, papan, cakram teleport, sel, dsb.) | rumah lembar (43.12.4): halaman tetap dengan konteks, atau Di Sini |
| `KotaNet.Screen` (warga modul K), `SainganAsk` | Di Sini lewat `KotaJembatan` / Dunia › Musuh › Saingan |
| `DimensiNet.Open` (11 NPC dunia bawah/laut) | Di Sini, layar dialog lewat `DimensiJembatan` |
| `KapalNet.Info` (kemudi) | Di Sini › Kemudi |
| `RNet` `info` / `warn` / `riddle`, alam `treasure` | Dunia › Alam › Menara / Di Sini prompt Jimat / Di Sini prompt Teka-teki / Di Sini prompt Hadiah |
| `DiplomasiPayloads.Sheet(open)` (perintah, Ketua Sekte NPC, Utusan) | Sekte › Luar › Sekte Dunia (fokus sekte) / Utusan (tawaran) |
| `Jaring` tak diminta (`/kultivasi perguruan hubungan`) | Murid › Jaring |
| Kebun `Open` (klik tanah/mulut tambang), Paviliun `View` (klik paviliun/altar/nisan) | Sekte › Wilayah › Kebun & Tambang / Sekte › Kas & Toko › Rak Kitab atau Makam |
| Langit `Papan(buka)`, `mandiri_papan`, `mandiri_harian`, Seteru `Layar`, Misi `Laporan` (item Gulungan Laporan), Seragam `LambangOpen`, Tetua `Balai`, Turnamen | Sekte › Wilayah › Langit / Murid › Daftar / Murid › Murid › Buku Harian / Sekte › Luar › Seteru / Sekte › Kegiatan › Misi (laporan sebagai rinci) / Sekte › Ikhtisar › Identitas / Sekte › Anggota › Tetua / Sekte › Kegiatan › Arena |
| Pengaturan kamera → Spektakuler, efek dampak → Penuh, `/dampak frames penuh`, Titah "Peringatan cahaya" | Pengaturan › Kamera & Film / Tampilan & Efek dengan `SegelKonfirmasi` peringatan |
| Pengaturan › Kontrol "Ubah tombol" | rebind di tempat (43.10.2); tautan `KeyBindsScreen` vanilla tetap untuk tombol vanilla |
| `MenuScreens` Forge (blok tungku), Ring (item, tombol cincin, irisan roda, Diri › Pusaka) | layar wadah tetap; dari Gulungan → `kembaliSetelah` |

---------------------------------------------------------------------------------------------------------------------

### 43.5 Pohon bagian (final; id halaman/sub = id registri `HubPages`)

Tanda: `*` hanya pemilik aula, `+` hanya anggota sekte NPC (41), `L` lebar, `ctx` bisa dibuka dengan konteks NPC/blok.
Sub yang tidak berlaku disembunyikan (bukan dikunci) kecuali disebut "terkunci".

```
[1] DIRI
    status        Status          StatusPage (+ "Gua Kediaman" pindah ke harta/kediaman)
    akar_dao      Akar & Dao      RootDaoPage (Dao oath: SegelKonfirmasi sedang)
    jalan         Jalan           JalanPage tanpa Langkah: ujian | harian
    alkimia       Alkimia         AlchemyBookPage
    pusaka        Pusaka          PusakaPage + [Buka Cincin Penyimpanan] (RingScreen)
    harta   ctx   Harta           kediaman (gabung_kediaman + 42 rumah + titik Gua Kediaman)
                                  | usaha (buku_kas tab Milikku+Lapak, 42 kota_milikku, toko_pemain, lapak_sewa,
                                    investasi, jabatan tab Jabatan+Pajak)
[2] TEKNIK
    kodeks      L Kodeks          KodeksPage (+ strip ringkas dari TechniquesPage: segel terpilih, P, n/168, tombol)
    cincin_segel L Cincin Segel   LoadoutPage (satu-satunya GUI pasang slot)
    kehancuran    Kehancuran      diri (KehancuranPage) | murid* terkunci tanpa cfg (RedSealPage, eks Segel Merah)
[3] MURID
    daftar        Daftar          MandiriBoard "Murid" (semua murid termasuk yang pergi; klik baris -> murid)
                                  ringkas | suasana (eks SuasanaScreen) | tangga (eks TanggaCatatanScreen)
    murid   ctx   Murid           pemilih murid (<- nama ->) + status (Status+Perintah) | teknik | pil_dao | kekuatan
                                  (Bakat+Pusaka bertumpuk) | jalan (PathTab, 4 bagian bertumpuk) | hati | hubungan
                                  | jalan_hidup (Ikhtisar+Kebijakan) | buku_harian L (eks BukuHarianScreen) | dompet
    jaring        Jaring          JaringScreen (klik murid -> murid › hubungan)
[4] SEKTE   (tanpa aula dan tanpa keanggotaan: hanya ikhtisar "Dirikan atau Bergabung" + luar)
    ikhtisar ctx  Ikhtisar        aula* (AulaPage) | identitas* (nama + LambangDesigner + Seragam + Kebijakan Aula)
                                  | catatan (satu Kronik: aula, kabar murid, formasi, langit, diplomasi; saring jenis)
                                  | kartu+ (LayarKartu: kartu, shifu, murid titipan bertumpuk) | hukum+ (catatan
                                  pelanggaran) | keluar+
    anggota  ctx  Anggota         calon* (CalonPage + undangan dari rekrut) | pangkat (Jenjang* / Pangkat+, chip Sumber)
                                  | tetua (Balai Tetua* / Majelis+Suksesi+, chip Sumber)
    bangun*  ctx  Bangun          ikhtisar | bahan | misi | pekerja | galian | templat L (eks TemplateScreen, mode
                                  "Lihat di dunia") | penyumbang (LAN)
    formasi* ctx  Formasi         inti | kitab | medan | simpul (+ peta simpul) | catatan
    kas      ctx  Kas & Toko      kas (Treasury Kantor Misi + Kas Mandiri + buku_kas Hari ini/7 hari/Sumber + Politik
                                  Kas+) | toko_pk (Paviliun Yue Lu* / Paviliun Jasa+, chip Sumber) | rak* | makam*
                                  (Makam + Silsilah + Altar bertumpuk) | kafilah* (buku_kas Kafilah)
    kegiatan      Kegiatan        tugas (DutyPage* / Tugas harian+) | misi* (MissionPage + Misi Cerita + Kabar Misi +
                                  laporan) | papan+ (LayarPapan) | arena (ArenaPage + Turnamen Elo/juara + Turnamen Murid+)
    wilayah* ctx  Wilayah         pertahanan | desa_pakta (HallDesaPage + 42 pakta) | cabang (Politik Cabang + 42 cabang)
                                  | kebun (KebunScreen: Kebun + Tambang bertumpuk) | langit (LangitScreen)
    luar     ctx  Luar            sekte_dunia (satu DaftarSekte: Direktori + Diplomasi + Tetangga Langit + sekte di
                                  Seteru; rinci: aksi diplomasi*, Lamar, kronik sekte) | seteru* | perang (Diplomasi
                                  perang + Politik Perang+ + panggilan) | utusan* (tawaran utusan)
[5] DUNIA
    peta     L ctx Peta           dunia (PetaScreen) | kota (eks LayarPetaKota, sisipan ibu kota)
    alam          Alam            alam_rahasia (AlamPage) | menara (eks MenaraScreen)
    musuh         Musuh           serbuan (MusuhPage) | saingan (eks SainganScreen) | riwayat
    duel          Duel            tantang (daftar + ChallengeScreen sebagai rinci) | berjalan (tawaran, duel jalan,
                                  Menyerah/Tunda/Hormat) | catatan
    perjalanan ctx Perjalanan     LayarJurnal: aktif | selesai | alur
    kota     ctx  Kota            42 kota_buku: ringkas | warga | acara (LayarAcara + upacara + rombongan) | kavling
                                  | kabar (kabar angin terdengar)
    dagang   ctx  Dagang          LayarBukuDagang: harga | laba | kontrak | gudang (42 gudang)
[6] KEABADIAN
    alam_abadi    Alam Abadi      AlamAbadiPage
    purba         Purba           binatang (PurbaPage) | bencana (BencanaPage)
    batin         Batin           BatinPage
    ciptaan       Ciptaan         CiptaanPage
    lelang   ctx  Lelang          LayarLelang dengan sumber Abadi (eks LelangPage)
    titah         Titah           eks TitahScreen
[7] PANDUAN
    kitab         Kitab           jalan_kultivasi (Langkah dari JalanPage) | bab (eks GuideScreen)
    ensiklopedia  Ensiklopedia    HelpPage (sasaran setiap segel "?")
[8] PENGATURAN
    tampilan | film | suara | hud | kontrol   (SettingsPage; grup jadi halaman; + Langit visual, duel, peringatan)
[9] DI SINI  (hanya saat ada konteks hidup; judul = tempat · lawan bicara, mis. "Toko Senjata · Paman Li")
    halaman = tab lembar konteks; 43.8
```

Halaman Sekte untuk pemain tanpa aula tetapi anggota sekte NPC: ikhtisar (kartu, hukum, keluar), anggota (pangkat,
tetua), kas (kas, toko_pk), kegiatan (tugas, papan, arena), luar. Untuk pemilik aula tanpa keanggotaan: semua `*`.

---------------------------------------------------------------------------------------------------------------------

### 43.6 Basis halaman tunggal (`screen/Page.java`, kini **public**)

`Page` lama dipertahankan (nama dan berkas sama) dan dijadikan superset `HallPage`, `DuniaPage`, `CodexPage`,
`DiscipleTab`, `KotaPage` dan model halaman `LayarTempatDasar`. Tanda tangan masukan mengikuti `Screen` 26.3 (verifikasi)
agar halaman yang dulu layar bisa dipindah dengan suntingan kecil.

```java
public abstract class Page {
    // identitas
    public abstract Component title();
    public Component shortName();                 // koin; bawaan = title()
    public int glyph();                           // Glyphs.*
    public String helpTopic();                    // segel "?" -> Panduan › Ensiklopedia
    public List<TabBar.Tab> subs();               // kosong = tanpa bilah sub
    public void showSub(String id);
    public boolean lebar();                       // kertas lebar 43.3.2
    public @Nullable Component kunci();           // alasan terkunci (tablet/koin redup)
    public boolean tampil();                      // false = disembunyikan
    public @Nullable SumberChip sumber();         // chip Aulaku/Perguruanku
    public long uang();                           // >= 0 = tampilkan LabelHarga di kepala
    // daur hidup
    protected abstract void build();              // susun widget untuk kotak x,y,w,h dan tata()
    public void onShow(@Nullable CompoundTag fokus);  // minta data, jalankan fokus
    public void onHide();                         // OP_TUTUP / OP_CLOSE bila perlu
    public void tick();
    public void onData(); public boolean needsRebuild();
    public void fokus(CompoundTag fokus);
    public enum Tata { SEMPIT, SEDANG, LEBAR }    // pindah dari LayarTempatDasar.Tata (nama lama tetap terjangkau
    protected final Tata tata();                  //  lewat pewarisan); dari lebar isi: < 320 / < 480 / sisanya
    // masukan (semua mengembalikan true bila dipakai)
    public boolean mouseClicked(MouseButtonEvent e, boolean dobel);
    public boolean mouseReleased(MouseButtonEvent e);
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy);
    public boolean mouseScrolled(double x, double y, double sx, double sy);
    public boolean keyPressed(KeyEvent e);
    public boolean charTyped(CharacterEvent e);
    public boolean mauTombol();                   // ada InkField terfokus
    public boolean mundur();                      // Esc: true = tutup rinci sendiri, false = serahkan ke Gulungan
    // gambar
    protected abstract void drawBehind(GuiGraphicsExtractor g, int mx, int my, float s);
    protected void drawAbove(GuiGraphicsExtractor g, int mx, int my, float s);
    // layanan untuk halaman
    protected final Fokus fokusRing;              // cincin fokus Tab untuk Widget dan Zona Lukis
    protected final void pesan(Component t, int warna);
    protected final void konfirmasi(SegelKonfirmasi.Tingkat t, Component teks, Runnable ya);
    protected final Minecraft minecraft; protected final Font font;
    protected int x, y, w, h;                     // kotak isi
    protected static void send(int action); protected static void send(int action, int arg, String text);
}
```

* Widget bertahan (`Widget` + `add`) **dan** gambar imediat (`Lukis` + `Zona`) dua-duanya didukung oleh `Page`; satu
  `Fokus` mengurus keduanya. Tidak ada basis halaman lain: `HallPage`, `DuniaPage`, `CodexPage`, `DiscipleTab`,
  `KotaPage` → `.deactivation` setelah halamannya pindah. `SectEventPage`, `LayarTempatDasar`, `LayarTemaDasar`,
  `LayarGabungDasar` tetap sebagai **turunan** `Page` (pembantu bersama, bukan basis kedua).
* Registri (`hub/HubPages.java`, baru; menggantikan `HubCards`):
  `HubPages.daftar(int bagian, String id, int urutan, Supplier<Page> buat)` dan
  `HubPages.daftarLembar(int bagian, String id, int urutan, String layar, Set<String> tab)` (lembar 38/41/42 dipasang
  dengan himpunan tab; baca-saja dari hub sesuai server). Urutan baku di `hub/Urutan.java` (konstanta dari pohon 43.5,
  dibekukan di kerangka). Pendaftaran ditulis di berkas `*Client` modul masing-masing (pola `HubCards` sekarang), bukan di
  satu berkas pusat.
* `LayarTempatDasar` (tetap di `tempat/ui/kit`) berubah dari `extends Screen` menjadi `extends Page`. Bingkai
  `PanelTempat`, kepala, kaki dan `TabLentera` dihapus darinya (Gulungan yang menggambar); tab lembar menjadi koin (Di
  Sini) atau sub (halaman tetap). Selama gelombang migrasi ia menyediakan bidang lama (`minecraft`, `font`, `width`,
  `height`, `onClose()`, `cari`, `tombol`, `uangGulir`, `tablet`) sebagai jembatan agar 52 turunannya tetap terkompilasi;
  gelombang poles P1 menghapus jembatan itu. `UiTempatClient.Pembuat.buat` mengembalikan `Page`.

---------------------------------------------------------------------------------------------------------------------

### 43.7 Kit widget tunggal (`client/screen/ui/`)

Satu paket. Rupa giok (Palette, Brush, ScrollFrame); fitur terbaik kit 38 (fokus papan ketik, `/` cari, pemilih jumlah,
tata responsif, keadaan kosong/terkunci, baca-saja). Setiap widget punya satu kelas dengan dua cara panggil: objek
bertahan (`Widget`) dan gambar imediat (`Lukis`), satu pelukis.

| Widget | Kelas yang bertahan | Dilebur dari (berkas → `.deactivation` di P1 setelah tak ada pengimpor) | Fitur wajib |
|---|---|---|---|
| Tombol | `TalismanButton` (+ `TalismanButton.gambar(Lukis, ...)`) | `tempat/ui/kit/TombolAksi`, `codex/CodexWidgets.Chip/Cycle`, `dunia/peta/PetaUi.Button`, `FormasiPage.talisman()`, `DuelScreen.cycle()`, tombol gambar tangan di Jimat/Ujian/Dimensi/Menara/Kebun/Diplomasi/Turnamen/MandiriBoard/JalanHidupTab/BuildPage, `record Hit` ×5, `mandiri/Sentuh`, `LangitScreen.Seal`, vanilla `Button` | mode siklus (klik kanan mundur), `stampedWhen`, `urgentWhen`, alasan nonaktif di tooltip, ≥ 16×12 |
| Sakelar | `JadeCoin` | `CodexWidgets.Coin`, `DuelScreen.coin()`, `FormasiPage.coin()` | `askingWhen` dihapus (konfirmasi = `SegelKonfirmasi`) |
| Bilah tab (rel, koin, sub) | `TabBar` (baru) | `JadeTablet`, `SectionPicker`, `CodexWidgets.Tablet`, `JalanPage.ChapterTablet`, `HelpPage.LetterCoin` (jadi indeks huruf `TabBar` mini), `TabLentera`, koin gambar `CultivationScreen.drawCoin`, kolom segel BuildPage/FormasiPage, tab gambar Kebun/Langit/Seteru/MandiriBoard/JalanHidupTab/Paviliun/BalaiTetua/Lambang | tegak/mendatar, kunci + alasan, sembunyi, label/glyph menurut tata, luapan dengan panah, fokus ← → |
| Daftar/gulir | `DaftarGulir` (pindah dari kit 38) | gulir tangan di HubCardsPage, HelpPage, AlchemyBookPage, JalanPage, SettingsPage, StatusPage, GuideScreen, MusuhPage, SainganScreen, PetaScreen daftar situs, Dimensi; `codex/CodexGrid` (mode kisi + judul kelompok) | baris/kisi, judul kelompok, virtual, rel seret, papan ketik |
| Kolom teks | `InkField` (pindah dari `hall/`) | `codex/InkSearchField`, `batin/CiptaanPage.NameField`, `FormasiPage` ganti nama di tempat, `tempat/ui/kit/KotakCari`, vanilla `EditBox` (Helm, Peta, Langit, Lambang) | `Ketik.fokus(this, terfokus)` tiap gambar, semua karakter cetak (termasuk tanda baca), panjang maks, mode cari (`/`, placeholder, ×), kursor/seleksi, Ctrl+A/C/V/X, Enter kirim, Esc lepas |
| Saringan | `Saring` (baru: baris cari + chip jenis + urut) | baris saring Kodeks, Direktori, Paviliun Jasa, Toko Senjata, Kronik | jadi pengganti tab kategori di tingkat 3 |
| Konfirmasi | `SegelKonfirmasi` (baru) | `KehancuranConfirmScreen`, `MotionWarningScreen`, `PhotosensitivityScreen`, `JimatScreen` tahan, Dimensi "Coret Namamu" tahan, Templat centang + tahan 3 s, klik-kedua-3-s (RootDaoPage, StatusPage, LoadoutPage, CiptaanPage, RingScreen, DuelScreen, PillTab, TrigramTile, PathTab, HatiTab, CalonPage, JenjangPage, BuildPage, Rak/Makam/Paviliun, BalaiTetua), `LayarGabungDasar.konfirmasi`, `JadeCoin.askingWhen` | kartu lapisan di atas halaman (redupkan isi); tiga tingkat: **ringan** (klik Ya), **sedang** (tahan 1.5 s), **berat** (centang + tahan 3/5 s, warna darah); Enter tahan, Esc batal |
| Pemilih jumlah | `PemilihJumlah` | `CalonPage`/Treasury siklus angka, Lelang +5/+10/+25 % | ← →, Shift ×10, maks dari data |
| Uang | `LabelHarga` (+ mode gulir dari `AngkaGulir`) | `AngkaGulir`, garis dompet `KotaPage`, harga LelangPage | NB/PK/batu, warna mampu/tidak |
| Bilah kemajuan | `Bilah` (baru) | bilah giok `KotaPage`, `PetaUi` nilai, `TemaUi` meter, `BandingStat` (mode banding), bilah dendam Saingan, kemajuan Menara, bilah batas harian Treasury | label, tanda, mode banding |
| Hitung mundur | `CincinWaktu` (+ teks m:ss dari `TeksTempat`) | m:ss `KotaPage`, sapuan LelangPage/AlamPage/TitahScreen | cincin / teks |
| Kartu barang | `KartuBarang` | `KotaPage.Card`, `HubCardsPage.Card` (peluncur, hilang) | ikon item, harga, stok, kunci |
| Pemilih orang | `PemilihOrang` (baru: baris potret + tanda + aktif; mode daftar dan mode sembul) | `sectevent/RosterView`, pemilih pekerja Kebun, pemilih Angkat BalaiTetua, pemilih siklus TreasuryPage, chip pekerja BuildPage, `hall/CandidateCard` (mode kartu), `Potret` dipakai di dalamnya | saring, kolom tambahan, klik buka orang |
| Daftar sekte | `DaftarSekte` (baru) | daftar Direktori, Diplomasi, Tetangga Langit, sekte di Seteru | segel, pip tingkat, garis sikap, aliran, peringkat |
| Papan misi | `PapanMisi` (baru, tata `LayarGildaMisi`) | Gilda Misi, LayarPapan, Kota Papan Misi (K), Kontrak Buruan | lencana pangkat, Terima, Tunjuk di peta |
| Kronik / log | `Kronik` (baru) | `LogPage`, Kabar MandiriBoard, Kronik Langit, kronik per sekte Diplomasi, `formasi/LogPanel`, riwayat MusuhPage | saring jenis, cap waktu, 50 baris |
| Kartu hasil | `KartuHasil` (baru) | `MisiLaporanScreen`, `LayarPutusan`, hasil `TreasureScreen`, hasil duel terakhir, hasil Acara | stempel nilai, regu, baris |
| Daftar pilihan | `DaftarPilihan` (baru, dari daftar pilihan `LayarDialog`) | strip Dimensi, Ujian 1-3, Bait, Racik, teka-teki Acara, Diskusi Dao Pertemuan, Hukum 1-3, Shifu 1-3 | alasan terkunci, ↑↓ Enter, nomor sebagai tanda |
| Segel bantuan | `KeyHints.HelpSeal` | `CultivationScreen.drawHelpSeal`, `KeyHints.SealCorner`, tombol `HubNav.help` MandiriBoard | satu di kepala Gulungan |
| Kembali | `TombolKembali` (baru) | `back/parent/induk` di Guide, DuelScroll, Template, KehancuranConfirm, MisiLaporan, BukuHarian, LangitVisual, Lambang, Menara, Motion, Photo | sama dengan Esc |
| Tooltip | `SlipTooltip` | tooltip vanilla (Jaring, Kebun, Langit, BukuHarian, MandiriBoard), tanpa tooltip (Turnamen, MisiLaporan, Tangga) | satu per bingkai |
| Geser | `JadeSlider` (ditulis ulang sebagai `Widget`) | `AbstractSliderButton` vanilla | masuk daftar widget halaman |
| Kosong / terkunci | `KeadaanKosong`, `KeadaanTerkunci` | (pindah dari kit 38) | gambar + alasan |
| Potret, Pratinjau 3D, Lencana pangkat, Papan skor | `Potret`, `Pratinjau3D`, `LencanaPangkat`, `PapanSkor` | (pindah dari kit 38) | — |
| Warna & gambar | `Palette` (+ `Palette.Gaya` 5 gaya) , `Brush`, `Lukis`, `Glyphs` | `TempatWarna`, `IkonTempat`, `TeksTempat` (pembantu teks ke `Brush`), `PanelTempat` (bingkai → Gulungan) | — |

Tetap di luar `screen/ui` karena milik satu layar: `GelembungDialog` (dialog), `Lembar` (pembaca lembar, di samping
`LayarTempatDasar`), `codex/SealCard` tetap di `screen/ui/SealCard` (kartu segel teknik, beda benda dari kartu barang).

---------------------------------------------------------------------------------------------------------------------

### 43.8 Konteks: NPC, blok, item (`hub/DiSini.java`, baru)

#### 43.8.1 Aturan

1. Lembar/payload yang datang karena klik di dunia membawa **konteks** (`Konteks(String layar, int npcId, @Nullable
   BlockPos jangkar, Component judul, int glyph)`).
2. Bila fungsi itu punya halaman rumah di pohon (43.12.4), Gulungan dibuka di halaman itu dengan konteks: tulis aktif,
   chip "bersama <nama> ×" di kepala. Bila tidak (layanan murni dunia), halaman lembar dipasang di tablet **Di Sini**.
3. Tablet Di Sini tampil selama konteks hidup dan selalu menuju halaman konteks (rumah atau Di Sini). Satu konteks
   sekaligus; konteks baru menggantikan yang lama (dialog → Jasa → toko: `TombolKembali` "‹ Kembali ke percakapan").
4. Server menutup sesi (`tutupLayar`, di luar `JANGKAU`, NPC hilang): tablet hilang; bila sedang tampil, Gulungan pindah
   ke bagian terakhir dengan pesan kaki "Percakapan berakhir". Gulungan ditutup (Esc/V) → `OP_TUTUP` (seperti `removed()`
   sekarang). Pindah bagian **tidak** menutup sesi.
5. Pemain sibuk (layar wadah/obrolan terbuka) saat prompt datang: `Kabar` toast + tablet Di Sini berdenyut; prompt
   menunggu di tablet sampai dijawab atau kadaluwarsa (server). Tidak ada layar yang membuka dirinya lagi.

#### 43.8.2 Prompt (konteks tanpa NPC)

| Prompt | Dari | Halaman Di Sini |
|---|---|---|
| Teka-teki menara | `RNet` `riddle` (`TowerRun`) | `UjianScreen` → halaman `DaftarPilihan` (tanpa buka-sendiri 3 s) |
| Peringatan Jimat Segel | `RNet` `warn` (`ShrineRun`) | `JimatScreen` → halaman rinci + `SegelKonfirmasi` sedang ("Cabut") |
| Hadiah Alam Rahasia | alam `treasure` | `TreasureScreen` → halaman `KartuHasil` + pilih 1 dari 3 (otomatis 60 s tetap) |
| Putusan ujian masuk, Pilih Shifu | `gabung_putusan`, `gabung_shifu` | `LayarPutusan`, `LayarShifu` |
| Sidang (Balai Hukum, Yamen, perkara kota) | `gabung_hukum` sesi, `yamen`, 42 `perkara` | satu halaman sidang `LayarYamen` |

#### 43.8.3 Layanan dunia (Di Sini)

Semua lembar 38 inti dan tema tanpa rumah tetap: `dialog` (+ 8 tablet 42), `toko_senjata`, `lelang`, `gilda_misi`,
`bank`, `tagihan`, `teleport`/`dermaga`, `pasar_binatang`, `judi_batu`, `penginapan`/`rumah_makan`/`jasa`, `yamen`,
`acara` (saat di acara), `lapak_rekrut` (+ 42 `kantor_rekrut`), `gilda_alkemis`/`gilda_tempa`, `kafilah` (+ `tema_kafilah`),
seluruh `tema_*` kecuali `tema_dagang`, 42 `pasar_hantu` (→ `LayarPasarGelap`), `guanpu` (→ `LayarTaruhan`),
`warga_kartu`/`warga_pohon` (kartu orang, sub Pohon), modul K (43.8.4), Dimensi (43.8.4), dan **Kemudi** kapal: `HelmScreen`
menjadi halaman Di Sini; daftar pelayarannya dihapus dan diganti tombol "Pilih tujuan di Peta" yang membuka Dunia › Peta
dalam mode berlayar; setelah memilih, Gulungan kembali ke Di Sini › Kemudi (perbaikan U10).

#### 43.8.4 Jembatan data klien (server tetap)

| Jembatan (baru) | Mengubah | Menjadi halaman |
|---|---|---|
| `dunia/kota/KotaJembatan.java` | `KotaNet.Screen` (screen, tab, data) ↔ lembar + op `KotaNet.Action` | Toko → halaman toko kit (`KartuBarang` + `PemilihJumlah`, kelas `TownPages.Toko` dirombak), Lelang → `LayarLelang`, Kedai Teh → `LayarTeh`, Pasar Gelap → `LayarPasarGelap`, Papan Misi → `PapanMisi` (`LayarGildaMisi`), Abadi Pasar/Kuliah/Persik/Maklumat Utusan → halaman kit (kelas `AbadiPages.*` dirombak), Pertemuan → `LayarDialog` |
| `abadi/LelangJembatan.java` | sinkron `CH_ABADI` lelang ↔ lembar; op → `Action(ABADI, "bid:..."/"titip")` | Keabadian › Lelang = `LayarLelang` |
| `dunia/dimensi/DimensiJembatan.java` | `DimensiNet.Open` ↔ lembar dialog; strip → `DaftarPilihan`; tahan 2 s → `SegelKonfirmasi` sedang | `LayarDialog` di Di Sini (dua palet Dimensi = `Palette.Gaya`) |

#### 43.8.5 Dua layar wadah

`ForgeScreen`, `RingScreen` tetap `AbstractContainerScreen`, digambar dengan `ScrollFrame`, `TabBar` (tab Forge),
`TalismanButton`, `HelpSeal` dan `SegelKonfirmasi` (Hapus Jejak: sedang). Dibuka dari Gulungan (tombol Pusaka, tombol
cincin saat Gulungan terbuka) → `HubNav.kembaliSetelah`: Esc kembali ke halaman asal (perbaikan Esc cincin). Ringkasan
pusaka natal hanya di Diri › Pusaka; tab Roh Forge hanya aksi.

---------------------------------------------------------------------------------------------------------------------

### 43.9 HUD: satu gaya, tanpa ganda

Gaya HUD tunggal = slip kertas giok (`ScrollFrame.slip`, `Palette`), teks 1.0, tidak bergambar saat
`EffectQuality.hudVisible()` palsu. Lima **slot** tetap; dalam satu slot hanya satu isi tampil (prioritas), sisanya antre.

| Slot | Yang bertahan | Yang dilebur / diubah |
|---|---|---|
| Kiri bawah (diri) | `QiHud` (orb, cincin, pedang, racun, nadi, bintang, tugas) | ubin segel terpilih pindah ke `CodexHud` saja (F34) |
| Bawah tengah (segel) | `CodexHud` (Roda Segel, 4 segel cepat, korsel, ubin terpilih, bilah isi) | — |
| Tengah (cincin di bidik) | satu pelukis cincin `CodexHud.cincin(...)` (publik) | cincin `formasi/AlarmHud`, teleport `KotaBesarHud`, kontes `NagaHud` memanggilnya |
| Sisi kanan (gulungan peristiwa) | satu slot `hud/SlotSisi` (baru) dengan prioritas: Ujian Gabung > Lei Tai > Tangga > Pertahanan Gerbang > Panduan | `HudUjian`, `LeiTaiHud`, `TanggaHud`, bilah gerbang `KotaBesarHud`, `GuideTracker` mendaftar sebagai isi slot; `TribulationHud` tetap kiri (bahaya) |
| Kanan atas (kabar) | `hud/Kabar` (baru, dari tumpukan kartu `ToastMandiri`) | `SystemToast` (DesaFx, GuideClient, HallFx, BuildFx), toast giok `CodexHud`, `ToastMandiri`, `LaporanCard` (varian panjang), `ChallengeOfferToast` (dengan petunjuk "V › Dunia › Duel") |
| Tepi layar | satu pelukis `ScreenFlash.tepi(warna, kuat)` dengan tumpukan prioritas (yang terkuat tampil) | `AuraHud`, `DimensiHud`, `LangitHud` vinyet, `TemaHud`, `MeditationAlert`, `EpicScreen` vinyet, `RedSkyHud` |
| Kartu judul | `epic/screen/TitleCard` | `sekte/mandiri/seteru/KartuJudul` memanggil `TitleCard` |
| Cuaca roh | chip `LangitHud` + lapisan Peta | baris cuaca `KapalHud` dan `HelmScreen` dihapus |
| Rel tetap | `DuelHud`, `ElitePlate`, `KapalHud`, `BuildHud`, `VoiceCaptions`, film | tidak ganda; hanya ikut gaya |

42 HUD `kota_irama` = mesin `LayarIrama`; `kota_kejar` = panah kompas misi yang ada (jadwal 0.1). Semua HUD punya baris
saklar di Pengaturan › HUD.

---------------------------------------------------------------------------------------------------------------------

### 43.10 Pengaturan dan tombol

#### 43.10.1 Semua pengaturan di Pengaturan

| Dari | Ke |
|---|---|
| `sekte/langit/LangitVisualScreen` (9 sakelar, `kultivator-langit-client.json`) | Tampilan & Efek, judul "Langit Sekte"; baris dari `LangitClientConfig.rows()` (baru, pola `DuelSettings.rows()`); berkas JSON tetap; tampil juga tanpa sekte langit |
| Pengaturan cepat `DuelScreen` (8 baris) | sudah ada lewat `DuelSettings.rows()`; dihapus dari halaman Duel (tautan "Atur di Pengaturan") |
| `/dampak frames`, `PhotosensitivityScreen`, `MotionWarningScreen` | baris yang ada + `SegelKonfirmasi` sedang saat menyalakan; perintah klien tetap dan membuka baris itu |
| `TitahScreen` chip Film, "Pilih/Bawaan/Peringatan cahaya" | tautan ke baris Kamera & Film / Tampilan |
| `gabung.*`, 42V-F `kota42` sakelar, HUD per elemen | sudah di `SettingsRows`; diperiksa lengkap |

#### 43.10.2 Kontrol

Halaman Kontrol menampilkan semua `key.kultivator.*` (dari `SettingsRows.kultivatorKeys()`) dalam `DaftarGulir`: klik
baris → "tekan tombol…" → `KeyMapping.setKey` + `KeyMapping.resetMapping()` + `options.save()` (verifikasi nama 26.3);
bentrok ditandai merah dengan nama tombol lain. `Keys.all()` (mati) dihapus; `SkipKey` memakai kategori `Keys` yang sama.

#### 43.10.3 Bawaan tombol

| Kunci | Bawaan | Arti |
|---|---|---|
| `cast` | G | sama |
| `quick` | V | ketuk: Gulungan; tahan: roda |
| `meditate` | B | sama |
| `next` / `previous` | Z / tanpa | sama |
| `flying_sword` | H | sama |
| `murid` | M | Murid › Murid (murid dilihat/terdekat) |
| `codex` | **K** (baru) | Teknik › Kodeks |
| `peta` | **N** (baru) | Dunia › Peta |
| `guide` | **J** (baru) | Panduan › Kitab |
| `info`, `dunia`, `abadi`, `cincin`, `wheel`, `seal_1..4`, `parry`, `duel_camera` | tanpa | sama |
| `skip_scene` | Backspace | sama |

K, N, J tidak dipakai vanilla 26.3 (verifikasi daftar `options.keyMappings`). `hub/KeyLayout` mendapat langkah
`key_layout = 43`: mengisi K/N/J hanya bila tombol itu masih tanpa ikatan dan tidak dipakai ikatan lain; pesan obrolan
sekali. Roda Akses Cepat: irisan Kitab Teknik/Murid/Dunia/Keabadian/Panduan memanggil `HubNav.buka` (sekarang irisan Dunia
membuka bagian hub tetapi tombol `dunia` membuka layar lain).

---------------------------------------------------------------------------------------------------------------------

### 43.11 Fungsi ganda: satu yang bertahan per kelompok

Kolom "Laporan": nomor laporan audit (1 inti, 2 sekte, 3 dunia, 4 tempur, 5 rencana) dan id kelompok di laporan itu.
"Lebur" = fiturnya pindah ke yang bertahan lalu berkas `.deactivation`; "Halaman" = kelasnya tetap tetapi menjadi
halaman/sub (bukan layar); "Pasang" = kelas lembar yang sama dipasang dengan himpunan tab.

| # | Fungsi | Laporan | GUI sekarang | Bertahan | Nasib yang lain |
|---|---|---|---|---|---|
| G01 | Bingkai gulungan | 1:F9, 2:1, 2:24, 3:F2, 4:1 | `CultivationScreen`, `HallScreen`, `DiscipleScreen`, `DuniaScreen`, `KeabadianScreen`, `KotaScreens.Base`, `CodexScreen`, `GuideScreen`, `DuelScrollScreen`, `PanelTempat` + 25 layar yang melukis kertas sendiri | `CultivationScreen` | bingkai lain lebur; isinya Halaman (43.12) |
| G02 | Basis halaman | 1:F8, 2:2, 3:F2, 4:2 | `Page`, `HallPage`, `DuniaPage`, `KotaPage`, `CodexPage`, `DiscipleTab`, `DuelScrollScreen.build/add`, tab `LayarTempatDasar` | `screen/Page` publik | lebur (43.6) |
| G03 | Kartu peluncur vs tablet (navigasi dua kali) | 1:F1, F2, F3, F5; 2:4, 2:21; 3:F1; 5a hub; 5b hub | `HubCardsPage` (Murid 1 kartu, Sekte 21, Dunia 12, Keabadian 8, Panduan 2), `HubCards` 29 kartu, tablet `DuniaScreen`/`KeabadianScreen`/`HallScreen` | `HubPages` + koin halaman | `HubCardsPage`, `HubCards` lebur; kartu `kota_abadi` (hanya bantuan) dan `ensiklopedia` dihapus |
| G04 | Masuk Kitab Teknik / tampil teknik terpilih | 1:F4; 4:16, 4:20 | `TechniquesPage` + tablet `CodexScreen` | Teknik › Kodeks (strip ringkas) | `TechniquesPage` lebur |
| G05 | Kolom tab tegak | 1:F10; 4:3 | `JadeTablet`, `CodexWidgets.Tablet`, `ChapterTablet`, `TabLentera`, `SectionPicker` | `TabBar` tegak | lebur |
| G06 | Navigasi tingkat dua dalam halaman | 1:F11; 2:3 | koin kepala hub/Aula, koin Jalan, `CodexWidgets.Coin`, kolom segel Bangun/Formasi, `SectionPicker` (PathTab, Bangun Misi, SectEventPage), deretan jimat-tab (Paviliun, Makam, BalaiTetua, Lambang), tab gambar (Kebun, Langit, Seteru, MandiriBoard, JalanHidupTab), `TabLentera` | `TabBar` mendatar (koin + sub) | lebur; tingkat 4 jadi tumpukan judul/`Saring` |
| G07 | Tombol | 1:F12; 2:5; 3:F14 | `TalismanButton`, `TombolAksi`, `CodexWidgets.Chip/Cycle`, `PetaUi.Button`, vanilla `Button`, `record Hit`, `Sentuh`, `LangitScreen.Seal`, tombol gambar tangan | `TalismanButton` | lebur |
| G08 | Sakelar dan siklus | 1:F13; 4:4 | `JadeCoin`, `CodexWidgets.Coin`, coin/cycle Duel, Formasi, siklus Ciptaan, jimat aturan Challenge | `JadeCoin` + `TalismanButton` mode siklus | lebur |
| G09 | Kartu barang / daftar barang toko | 1:F14; 3:F6 | `HubCardsPage.Card`, `KotaPage.Card`, `KartuBarang`, `TitahScreen.DecreeCard`, `CandidateCard` | `KartuBarang` (barang), `SealCard` (teknik/titah), `PemilihOrang` mode kartu (calon) | lebur |
| G10 | Kolom teks | 1:F15; 2:7; 3:F15; 4:5 | `InkField`, `InkSearchField`, `NameField`, ganti nama Formasi, `KotakCari`, `EditBox` ×4 | `InkField` | lebur |
| G11 | Tooltip | 1:F16; 2:23 | `SlipTooltip`, tooltip vanilla ×5, tanpa tooltip ×3 | `SlipTooltip` | lebur |
| G12 | Daftar gulir | 1:F17; 3:F16 | `DaftarGulir` + 12 gulir tangan, `CodexGrid` | `DaftarGulir` | lebur |
| G13 | Konfirmasi | 1:F18; 2:8; 3:F13; 4:12 | lihat baris Konfirmasi 43.7 | `SegelKonfirmasi` | lebur; juga ditambah di tempat yang belum punya (43.13) |
| G14 | Pengaturan | 1:F19; 2:20; 4:13 | `SettingsPage`, pengaturan cepat Duel, `LangitVisualScreen`, `/dampak`, Aktifkan/Batal peringatan | Pengaturan | lebur (43.10.1) |
| G15 | Daftar tombol | 1:F20 | Kontrol, `KeyBindsScreen`, `Keys.all()` | Pengaturan › Kontrol (rebind di tempat) | `Keys.all()` dihapus; `KeyBindsScreen` hanya tautan vanilla |
| G16 | Segel "?" | 1:F7; 2:22; 3 U6; 4:6 | `drawHelpSeal`, `HelpSeal`, `SealCorner`, tombol help MandiriBoard, kit 38 tanpa segel | `HelpSeal` di kepala Gulungan | lebur; setiap halaman punya `helpTopic()` |
| G17 | Buku panduan / Ensiklopedia / pembuka | 1:F6; 4:7, 4:17 | `GuideScreen`, `HelpPage`, kartu PANDUAN, Langkah `JalanPage`, `CodexScreen.TAB_HELP` | Panduan › Kitab (jalan_kultivasi + bab) + Ensiklopedia | `GuideScreen` Halaman; kartu dan `TAB_HELP` dihapus |
| G18 | HUD kemajuan panduan | 4:8 | `GuideTracker` (dua buku), toast "Bab selesai" | `GuideTracker` di slot sisi | toast → `Kabar` |
| G19 | Toko | 1:F21; 3:F6 | Toko modul K, `LayarTokoSenjata`, `LayarLayanan`, `LayarPasarBinatang`, `LayarHerba`, `LayarSutra`, ... | satu komposisi toko kit (`KartuBarang` + `LabelHarga` + `PemilihJumlah` + `Saring`) | tiap toko tetap halamannya sendiri (barang beda), Toko K dirombak dengan komposisi itu |
| G20 | Lelang | 1:F22; 3:F3 | Lelang K, `LayarLelang`, `LelangPage` | `LayarLelang` | K dan Abadi lewat jembatan; `LelangPage` lebur |
| G21 | Kedai teh / pasar gelap | 1:F23; 5b pasar_hantu | Teh/Gelap K, `LayarTeh`, `LayarPasarGelap`, 42 pasar_hantu | `LayarTeh`, `LayarPasarGelap` | K lewat jembatan; pasar_hantu = tab `LayarPasarGelap` (jadwal 0.1) |
| G22 | Papan misi dan daftar misi aktif | 1:F24; 2:14; 3:F4; 5a gabung_papan | Papan Misi K, `LayarGildaMisi`, `LayarPapan`, `LayarKontrak`, tab Aktif Jurnal, tab Quest Dialog, `DutyPage`, `MissionPage` | widget `PapanMisi`; daftar aktif tunggal = Dunia › Perjalanan | Quest Dialog = saringan Perjalanan per NPC; `MissionPage` tetap (fungsi lain: mengirim murid) |
| G23 | Peta | 1:F25; 3:F8 | `PetaScreen`, `LayarPetaKota`, peta mini Teleport/KafilahTema/BukuKas/Kapal/Ziarah, dua kartu peta | Dunia › Peta | `LayarPetaKota` = sub Kota; peta mini memanggil `PetaPainter.mini` |
| G24 | Roda radial | 1:F26 | `QuickWheel`, `SealWheel` | dua-duanya (fungsi beda: navigasi vs pilih segel) | satu pelukis radial bersama; bukan duplikat |
| G25 | Deteksi "datang dari hub" | 1:F27 | `HubNav.expectChild/openChild`, `SeragamClient.hubAskedAt`, `UiTempatClient.lihatDariHub` jendela 3 s | tidak perlu (semua halaman) + `HubNav.kembaliSetelah` untuk wadah | lebur |
| G26 | Buka-tertunda | 1:F28 | `pendingDaoChoice`, `pendingTreasure`, `UjianScreen.pending`, `DiscipleClient.pendingOpen` | `HubNav.antre` + prompt Di Sini | lebur |
| G27 | Toast/kabar | 1:F29; 4:21 | `SystemToast` ×4, toast `CodexHud`, `ToastMandiri`, `LaporanCard`, `ChallengeOfferToast`, `TitleCard` STAMP | `hud/Kabar` | lebur |
| G28 | Kartu judul | 1:F30 | `TitleCard`, `KartuJudul`, intro duel | `TitleCard` | `KartuJudul` memanggil `TitleCard` |
| G29 | Vinyet/tint tepi | 1:F31 | 8 HUD (43.9) | `ScreenFlash.tepi` + prioritas | panggilan |
| G30 | Cincin di bidik | 1:F32 | `CodexHud`, `AlarmHud`, `KotaBesarHud`, `NagaHud` | pelukis `CodexHud.cincin` | panggilan |
| G31 | Gulungan peristiwa sisi | 1:F33; 5a HudUjian | `LeiTaiHud`, `TanggaHud`, `GuideTracker`, `HudUjian`, bilah `KotaBesarHud`, `TribulationHud` | `hud/SlotSisi` (Tribulasi tetap kiri) | isi slot |
| G32 | HUD status pemain | 1:F34; 4:20 | `QiHud` + `CodexHud` (ubin segel ganda) | `QiHud` (vital) + `CodexHud` (segel) | ubin segel di `QiHud` dihapus |
| G33 | Warna/lapisan gambar | 1:F35 | `Palette`+`Brush` vs `TempatWarna`+`Lukis` | `Palette` (+`Gaya`), `Brush`, `Lukis` | `TempatWarna` lebur |
| G34 | Kit widget | 1:5.1; 2:6 | `screen/ui` vs `tempat/ui/kit` | `screen/ui` (43.7) | kit 38 lebur kecuali `LayarTempatDasar`, `Lembar`, `GelembungDialog` |
| G35 | Daftar murid sekte | 2:9 | MandiriBoard Murid, Suasana, TanggaCatatan, Jaring, wajah Jenjang; pemilih Kebun/Tetua/Treasury/Bangun/Misi | Murid › Daftar + `PemilihOrang` | Suasana/Tangga = sub Daftar; pemilih lebur |
| G36 | Daftar sekte dengan sikap | 2:10; 5a direktori | `DiplomasiScreen`, Seteru "Murid sekte lain", Langit "Tetangga", `LayarDirektori` | Sekte › Luar › Sekte Dunia (`DaftarSekte`) | lebur; Lamaran = rinci di sana |
| G37 | Log/kronik | 2:11 | `LogPage`, Kabar MandiriBoard, Kronik Langit, kronik Diplomasi, `LogPanel` | `Kronik`; Sekte › Ikhtisar › Catatan | Formasi › Catatan memakai `Kronik` saringan formasi |
| G38 | Kas/perbendaharaan | 2:12; 5a Politik Kas | `TreasuryPage`, Kas MandiriBoard, `LayarBukuKas` (2 kartu hub), Politik Kas | Sekte › Kas & Toko › Kas | lebur/Pasang |
| G39 | Toko PK | 2:13; 5a paviliun | Paviliun Yue Lu (`TreasuryPage`), `LayarPaviliun` | Sekte › Kas & Toko › Toko PK (chip Sumber) | Pasang; nama "Paviliun" hanya untuk Rak Kitab |
| G40 | Turnamen | 2:15 | `TurnamenScreen`, `ArenaPage` Turnamen | Sekte › Kegiatan › Arena | `TurnamenScreen` lebur |
| G41 | Tangga pangkat | 2:16; 5a pangkat | `JenjangPage`, `LayarPangkat` | Sekte › Anggota › Pangkat (satu pohon, chip Sumber) | `LayarPangkat` Pasang di halaman yang sama |
| G42 | Buku harian | 2:17 | tab Buku Harian JalanHidupTab, `BukuHarianScreen` | Murid › Murid › buku_harian | lebur |
| G43 | Tawaran utusan | 2:18 | Diplomasi strip, MissionPage "Utusan" | Sekte › Luar › Utusan | **bukan duplikat** (diverifikasi): tab MissionPage diganti label "Pencari Bakat" |
| G44 | Identitas sekte | 2:19 | nama `AulaPage`, `LambangDesignerScreen`, Kebijakan MandiriBoard | Sekte › Ikhtisar › Identitas | lebur |
| G45 | Pandangan "sekteku" | 2:21; 5a kartu | `AulaPage`, `LayarKartu`, `/sect info` di obrolan, Direktori tab Sekteku | Sekte › Ikhtisar | "Saudara Sekte" jadi daftar di Aula (43.14 S3); tab Sekteku dihapus |
| G46 | Kisi teknik + slip rinci | 2:25; 4:10 | `CodexGrid`+`DetailSlip`, `TechniqueTab`, `formasi/CodexPanel`+`FormationSlip` | satu komponen katalog (`DaftarGulir` kisi + `DetailSlip`) | dipakai Kodeks, Murid › Teknik, Formasi › Kitab |
| G47 | Kendali Kehancuran | 4:11 | `KehancuranPage`, `RedSealPage` | Teknik › Kehancuran (diri / murid) | lebur jadi dua sub satu halaman |
| G48 | Pasang segel ke slot | 4:9 | `LoadoutPage`, `DetailSlip` "Pasang ke Cincin", pin roda 1-4 | Teknik › Cincin Segel | tombol slip melompat ke Cincin Segel dengan kartu terpilih; pin roda tetap (jalan pintas tempur) |
| G49 | Pengaturan duel | 4:13 | Duel cepat vs Pengaturan | Pengaturan (G14) | — |
| G50 | Menjawab tantangan duel | 4:14 | Terima/Tolak Duel, chip obrolan, toast | Dunia › Duel › Berjalan + chip obrolan (jalan pintas dunia) | toast menunjuk halaman |
| G51 | Membuka cincin | 4:15 | kartu Dunia, irisan roda, tombol, item | item, tombol, irisan, tombol di Diri › Pusaka | kartu hilang |
| G52 | Hologram formasi | 4:18 | `PatternHologram`, `FormationBlueprint`, `SiteHologram` | `PatternHologram` (formasi), `SiteHologram` (tapak sekte, benda lain) | `FormationBlueprint` tetap pemicu panduan, menggambar lewat pelukis `PatternHologram` (P) |
| G53 | Info pusaka natal | 4:19 | `PusakaPage`, tab Roh Forge | Diri › Pusaka | Forge Roh hanya aksi |
| G54 | Daftar musuh/ancaman | 3:F7 | `MusuhPage`, `SainganScreen`, `PertahananPage` | Dunia › Musuh (serbuan/saingan/riwayat) | `PertahananPage` tetap (aksi pertahanan aula), Serbuan menautnya |
| G55 | Percakapan NPC | 3:F5; 5b dialog | `LayarDialog`, `DimensiScreen`, `MeetingPage`, salam K di obrolan | `LayarDialog` | Dimensi/Pertemuan lewat jembatan; lebur |
| G56 | Kafilah | 3:F9; 5b gudang Kirim | `LayarKafilah`, `LayarKafilahTema`, BukuKas Kafilah | `LayarKafilah` (mode tema: penumpang, unta) | `LayarKafilahTema` lebur; dispatch sekte = Pasang tab Kafilah |
| G57 | Cuaca roh | 3:F10 | `LangitHud`, `KapalHud`, `HelmScreen`, lapisan Peta | chip `LangitHud` + lapisan Peta | dihapus dari Kapal/Helm |
| G58 | Pemilih tujuan | 3:F11 | daftar Helm, kartu Peta "Berlayar", Teleport/Dermaga, Kapal, Lift, penumpang kafilah | Peta (kapal); widget baris tujuan tunggal (`DaftarGulir` + `PetaPainter.mini`) untuk Teleport/Lift/Kapal/Kafilah | daftar Helm dihapus |
| G59 | Pilihan 3 opsi | 3:F12 | Ujian, Bait, Racik, teka-teki Acara, Diskusi Dao, strip Dimensi | `DaftarPilihan` | tiap permainan tetap halamannya, widget satu |
| G60 | Irama | 3:F12; 5b HUD irama | `LayarIrama`, HUD `kota_irama` | mesin `LayarIrama` | HUD memakai mesin itu |
| G61 | Uang/hitung mundur/bilah/format | 3:F17 | lihat 43.7 | `LabelHarga`, `CincinWaktu`, `Bilah`, `Brush` teks | lebur |
| G62 | Buku besar 7 hari | 3:F18; 5b kota_milikku | `LayarBukuKas`, `LayarBukuDagang`, 42 milikku | satu pelukis grafik `Bilah.grafik7` | dua halaman berbeda data (Kas, Dagang) |
| G63 | Bentrok nama | 3:F19; 2:4 | Alam ×2, Titah ×2, Kontrak ×2, Kafilah ×3, Paviliun ×3 | nama tunggal: Alam Rahasia / Alam Abadi; Titah Langit / Maklumat Utusan; Kontrak Buruan / Kontrak Muatan; Paviliun = Rak Kitab, toko = Toko PK | hanya lang |
| G64 | Pengadilan | 5a hukum; 5b jabatan/perkara | `LayarYamen`, `LayarHukum`, pengadilan Dimensi, 42 perkara | `LayarYamen` (mode sidang dari lembar) | `LayarHukum` lebur; pengadilan Dimensi = dialog (G55) |
| G65 | Kartu hasil | 5a putusan | `LayarPutusan`, `MisiLaporanScreen`, hasil Treasure/duel/acara | `KartuHasil` | tiap pemakai memanggilnya |
| G66 | Kediaman | 5a kediaman; 5b rumah; 5c | `LayarKediaman`, koin Gua Kediaman `StatusPage`, 42 rumah, tab Kediaman `LayarPaviliun`, kamar penginapan | Diri › Harta › Kediaman | koin StatusPage dan tab Paviliun dihapus; penginapan tetap layanan Di Sini, sewanya tampil di Kediaman |
| G67 | Politik sekte NPC | 5a politik | `LayarPolitik` 5 tab vs BalaiTetua/Diplomasi/Treasury | Pasang per tab: Majelis+Suksesi → Anggota › Tetua; Perang → Luar › Perang; Kas → Kas; Cabang → Wilayah › Cabang | kelas tetap satu |
| G68 | Cabang | 5a cabang; 5b cabang; 5c | Politik Cabang, 42 cabang, 42 pakta tab Cabang | Sekte › Wilayah › Cabang | Pasang |
| G69 | Kartu orang / hubungan | 5b warga_kartu/pohon | `JaringScreen`, `HubunganTab`, `SuasanaScreen`, 42 warga | satu pelukis ikatan (`sekte/hubungan/PelukisIkatan`, dari Jaring + HubunganTab) | dipakai Jaring, Murid › Hubungan, kartu warga Di Sini (sub Pohon) |
| G70 | Ringkasan kota | 5b kota_buku | Town K, `LayarPetaKota`, 42 kota_buku | Dunia › Kota | Town K hilang (jembatan per tab), PetaKota = Peta › Kota |
| G71 | Kalender acara | 5b Acara/upacara/rombongan | `LayarAcara`, 42 tab Acara, upacara, rombongan | `LayarAcara` (Dunia › Kota › Acara; Di Sini saat di acara) | Pasang |
| G72 | Buku harga | 5b Harga | 42 kota_buku Harga, `LayarBukuDagang` Harga | Dunia › Dagang › Harga | tab 42 Harga tidak dipasang |
| G73 | Kabar angin | 5b Kabar | 42 Kabar, Kabar Angin Teh K, Jurnal | Dunia › Kota › Kabar (yang sudah terdengar) | Teh tetap tempat membeli kabar |
| G74 | Harta & usaha / lapak | 5b milikku, toko_pemain, lapak_sewa, investasi | `LayarBukuKas` Lapak/Milikku, 42 toko/lapak/investasi, `LayarBank` | Diri › Harta › Usaha | Pasang; Bank tetap layanan Di Sini |
| G75 | Kontrak dagang | 5b gudang Kontrak | 42 gudang Kontrak, `LayarBukuDagang` Kontrak | Dunia › Dagang › Kontrak | Pasang |
| G76 | Judi | 5b guanpu | 42 guanpu, `LayarTaruhan`, `LayarJudiBatu` | `LayarTaruhan` (tab dadu) | `LayarJudiBatu` tetap (toko batu mentah + potong, bukan taruhan), memakai widget taruhan yang sama |
| G77 | Rekrut | 5b kantor_rekrut | 42 kantor_rekrut, `LayarLapakRekrut`, `CalonPage` | `LayarLapakRekrut` (Di Sini) + Sekte › Anggota › Calon (hasilnya) | widget `PemilihOrang` kartu |
| G78 | Desa / pakta | 5b pakta | 42 pakta, `HallDesaPage` | Sekte › Wilayah › Desa & Pakta | Pasang |
| G79 | Kavling | 5b kavling | 42 kavling, `TemplateScreen`/`BuildPage` (lemah) | Dunia › Kota › Kavling (+ Di Sini di kavling) | memakai pelukis diagram `BlueprintDiagram` |
| G80 | HUD kejar | 5b | 42 `kota_kejar`, panah kompas | panah kompas | jadwal 0.1 |
| G81 | Duplikat di dalam 41/42 | 5c | Direktori Sekteku, Paviliun Kediaman, jabatan Perkara/Kavling, milikku vs rumah Milikku | satu tempat masing-masing (G45, G66, G64, G79, G74) | tab tidak dipasang |
| G82 | Bingkai Duel | 4:1.4 | `DuelScrollScreen`, `DuelScreen`, `ChallengeScreen` | Dunia › Duel (Challenge = rinci) | `DuelScrollScreen` lebur |
| G83 | Ring/Forge di luar Gulungan | 4:1.5 | dua wadah | tetap layar wadah, rupa kit | 43.8.5 |
| G84 | Peringatan keselamatan | 1:F18; 4:1.11 | `MotionWarningScreen`, `PhotosensitivityScreen` (77/73 baris, hampir sama) | `SegelKonfirmasi` sedang | lebur |

---------------------------------------------------------------------------------------------------------------------

### 43.12 Peta semua kelas layar dan halaman

Kolom Nasib: **H** = jadi halaman/sub (kelas tetap, basis diganti), **L** = lebur lalu `.deactivation`, **W** = tetap
layar wadah, **J** = halaman lewat jembatan, **K** = konteks Di Sini/prompt. Path relatif `src/client/java/dev/kultivator/client/`.

#### 43.12.1 Layar inti dan sekte (kelas `Screen`)

| Kelas | Nasib | Mendarat di |
|---|---|---|
| `screen/CultivationScreen` | host | Gulungan Utama |
| `hall/HallScreen` | L | Sekte (halaman Aula tersebar, 43.12.3) |
| `disciple/DiscipleScreen` | L | Murid › Murid (pemilih + sub) |
| `dunia/DuniaScreen` | L | Dunia / Diri › Pusaka |
| `keabadian/KeabadianScreen` | L | Keabadian |
| `dunia/kota/KotaScreens` (`Base`, `TownScreen`, `AbadiScreen`, `MeetingScreen`) | L | Di Sini lewat `KotaJembatan` |
| `codex/CodexScreen` | L | Teknik |
| `guide/GuideScreen` | H | Panduan › Kitab › bab |
| `duel/ui/DuelScrollScreen` | L | — |
| `duel/ui/DuelScreen` | H | Dunia › Duel › tantang / berjalan / catatan |
| `duel/ui/ChallengeScreen` | H | Dunia › Duel › tantang (rinci) |
| `pusakalangit/TitahScreen` | H | Keabadian › Titah |
| `sectbuild/TemplateScreen` | H | Sekte › Bangun › templat (mode Lihat di dunia: Gulungan tersembunyi, hologram tampil, petunjuk HUD "Enter/Esc kembali") |
| `alam/TreasureScreen` | K | prompt Hadiah |
| `kehancuran/KehancuranConfirmScreen` | L | `SegelKonfirmasi` berat |
| `epic/screen/MotionWarningScreen`, `PhotosensitivityScreen` | L | `SegelKonfirmasi` sedang di Pengaturan |
| `dunia/peta/PetaScreen` | H | Dunia › Peta › dunia (L) |
| `dunia/kapal/HelmScreen` | K | Di Sini › Kemudi |
| `dunia/reruntuhan/MenaraScreen` | H | Dunia › Alam › menara |
| `dunia/reruntuhan/JimatScreen` | K | prompt Jimat |
| `dunia/reruntuhan/UjianScreen` | K | prompt Teka-teki |
| `dunia/kota/SainganScreen` | H | Dunia › Musuh › saingan |
| `dunia/dimensi/DimensiScreen` | J/L | Di Sini, `LayarDialog` lewat `DimensiJembatan` |
| `sekte/diplomasi/DiplomasiScreen` | H | Sekte › Luar › sekte_dunia / perang / utusan (satu kelas, tiga sub) |
| `sekte/hubungan/JaringScreen` | H | Murid › Jaring |
| `sekte/kebun/KebunScreen` | H | Sekte › Wilayah › kebun |
| `sekte/kepribadian/SuasanaScreen` | L | Murid › Daftar › suasana |
| `sekte/langit/ui/LangitScreen` | H | Sekte › Wilayah › langit (Tetangga → Sekte Dunia; Kronik → Catatan; Undang memakai `PemilihOrang` pemain daring + `InkField`) |
| `sekte/langit/LangitVisualScreen` | L | Pengaturan › Tampilan (baris) |
| `sekte/mandiri/MandiriBoardScreen` | H | Murid › Daftar (Kabar → Catatan, Kebijakan → Identitas, Kas → Kas) |
| `sekte/mandiri/BukuHarianScreen` | H | Murid › Murid › buku_harian |
| `sekte/mandiri/seteru/SeteruScreen` | H | Sekte › Luar › seteru (sekte lain → Sekte Dunia) |
| `sekte/misi/MisiLaporanScreen` | L | `KartuHasil` rinci di Sekte › Kegiatan › Misi |
| `sekte/paviliun/PaviliunScreen` (+ pelukis `RakScreen`, `MakamScreen`) | H | Sekte › Kas & Toko › rak / makam |
| `sekte/seragam/LambangDesignerScreen` | H | Sekte › Ikhtisar › identitas |
| `sekte/tangga/TanggaCatatanScreen` | L | Murid › Daftar › tangga |
| `sekte/tetua/BalaiTetuaScreen` | H | Sekte › Anggota › tetua |
| `sekte/turnamen/TurnamenScreen` | L | Sekte › Kegiatan › arena |
| `tempa/ForgeScreen`, `tempa/RingScreen` | W | 43.8.5 |
| `tempat/ui/layar/*` (18), `tempat/ui/tema/*` (20), `sekte/gabung/ui/layar/*` (11), `dunia/kota42/ui/layar/*` (42-G2, mis. `LayarKavling`) | H / L / K | per kunci lembar, 43.12.4 |
| `tempat/ui/kit/LayarTempatDasar` | H (basis lembar) | turunan `Page` |
| `tempat/ui/tema/LayarTemaDasar`, `sekte/gabung/ui/layar/LayarGabungDasar` | H (pembantu) | turunan `LayarTempatDasar` |

#### 43.12.2 Halaman/tab lama (basis lama → `Page`)

| Kelas | Mendarat di |
|---|---|
| `screen/StatusPage`, `RootDaoPage`, `AlchemyBookPage` | Diri › status / akar_dao / alkimia |
| `screen/JalanPage` | Diri › Jalan (ujian, harian) + Panduan › Kitab › jalan_kultivasi (Langkah) — satu kelas, dua pasang, tab tidak ganda |
| `screen/TechniquesPage` | L (strip di Kodeks) |
| `screen/HubCardsPage` | L |
| `screen/HelpPage` | Panduan › Ensiklopedia |
| `screen/SettingsPage` | Pengaturan (grup = halaman) |
| `codex/KodeksPage`, `LoadoutPage`, `KehancuranPage` | Teknik › kodeks / cincin_segel / kehancuran › diri |
| `kehancuran/RedSealPage` | Teknik › kehancuran › murid |
| `disciple/StatusTab` + `OrderTab` | Murid › Murid › status (Perintah sebagai baris bawah) |
| `disciple/TechniqueTab` (+`TeachCard`), `PillTab` (+`TrigramTile`), `power/TalentTab` + `power/TreasureTab`, `path/PathTab` | teknik / pil_dao / kekuatan / jalan |
| `sekte/kepribadian/HatiTab`, `sekte/hubungan/HubunganTab` | hati / hubungan |
| `sekte/mandiri/JalanHidupTab` | jalan_hidup + dompet (Buku Harian pindah ke buku_harian) |
| `hall/AulaPage`, `LogPage`, `CalonPage`, `JenjangPage`, `TreasuryPage` | Sekte › ikhtisar/aula, ikhtisar/catatan, anggota/calon, anggota/pangkat, kas/kas + kas/toko_pk |
| `sectbuild/BuildPage` | Sekte › Bangun (kolom segel → sub) |
| `formasi/FormasiPage` (+`CorePanel`, `CodexPanel`, `FieldPanel`, `NodePanel`, `LogPanel`, `FormationSlip`, `NodeMap`, `BlueprintDiagram`) | Sekte › Formasi (panel → sub; slip = rinci; NodeMap = tampilan sub simpul; perjalanan/hologram menutup Gulungan ke dunia, disengaja) |
| `sectevent/SectEventPage`, `DutyPage`, `MissionPage`, `ArenaPage` (+`BracketView`, `RosterView`) | Sekte › Kegiatan › tugas / misi / arena (`RosterView` → `PemilihOrang`) |
| `musuh/PertahananPage`, `desa/HallDesaPage` | Sekte › Wilayah › pertahanan / desa_pakta |
| `alam/AlamPage`, `tempa/PusakaPage`, `musuh/MusuhPage` | Dunia › alam / Diri › pusaka / Dunia › musuh |
| `abadi/AlamAbadiPage`, `purba/PurbaPage`, `purba/BencanaPage`, `batin/BatinPage`, `batin/CiptaanPage` | Keabadian |
| `abadi/LelangPage` | L (Keabadian › Lelang = `LayarLelang` + `LelangJembatan`) |
| `dunia/kota/TownPages` (`Toko`, `Lelang`, `Teh`, `Gelap`, `Misi`), `AbadiPages` (`Pasar`, `Kuliah`, `Persik`, `Titah`), `MeetingPage` | J: Toko dan Abadi.* dirombak jadi halaman kit Di Sini; Lelang/Teh/Gelap/Misi/Meeting lebur ke `LayarLelang`/`LayarTeh`/`LayarPasarGelap`/`PapanMisi`/`LayarDialog` |
| `sekte/misi/MisiTab` | bagian halaman Misi |
| `codex/CodexPage`, `hall/HallPage`, `dunia/DuniaPage`, `dunia/kota/KotaPage`, `disciple/DiscipleTab` | L |

#### 43.12.3 `Halls.PAGE_*` → halaman

| `Halls.PAGE_*` | Tujuan |
|---|---|
| AULA 0 | sekte/ikhtisar/aula |
| BANGUN 1 | sekte/bangun |
| CALON 2 | sekte/anggota/calon |
| JENJANG 3 | sekte/anggota/pangkat |
| TREASURY 4 | sekte/kas/kas |
| Catatan, Tugas, Misi, Arena (indeks 5-9 daftar halaman `HallScreen`, hanya di klien; verifikasi urutannya) | sekte/ikhtisar/catatan, kegiatan/tugas, kegiatan/misi, kegiatan/arena |
| FORMASI 10 | sekte/formasi |
| PERTAHANAN 11 | sekte/wilayah/pertahanan |
| DESA 12 | sekte/wilayah/desa_pakta |
| Segel Merah (hub `HALL_SEGEL_MERAH`) | teknik/kehancuran/murid |

#### 43.12.4 Lembar 38 / 41 / 42 → rumah (`UiTempatClient.rumah(layar, tab)`; Di Sini bila tidak tertulis)

| Kunci lembar (kelas) | Rumah | Nasib kelas |
|---|---|---|
| `dialog` (`LayarDialog`, + 42 tablet) | Di Sini | H |
| `toko_senjata` (`LayarTokoSenjata`), `bank` (`LayarBank`), `tagihan` (`LayarTagihan`), `teleport`/`dermaga` (`LayarTeleport`), `pasar_binatang` (`LayarPasarBinatang`), `judi_batu` (`LayarJudiBatu`), `penginapan`/`rumah_makan`/`jasa` (`LayarLayanan`), `gilda_alkemis`/`gilda_tempa` (`LayarGilda`), `gilda_misi` (`LayarGildaMisi`), `lelang` (`LayarLelang`) | Di Sini | H |
| `yamen` (`LayarYamen`) + `gabung_hukum` sesi + 42 `perkara` + jabatan tab Perkara | Di Sini (prompt sidang) | H; `LayarHukum` L |
| `acara` (`LayarAcara`) + 42 upacara/rombongan + kota_buku Acara | Dunia › Kota › acara; Di Sini di tempat acara | H |
| `lapak_rekrut` (`LayarLapakRekrut`) + 42 `kantor_rekrut` | Di Sini | H |
| `kafilah` (`LayarKafilah`) + `tema_kafilah` | Di Sini; tab Kafilah buku_kas → Sekte › Kas › kafilah | H; `LayarKafilahTema` L |
| `jurnal` (`LayarJurnal`) | Dunia › Perjalanan | H |
| `buku_kas` (`LayarBukuKas`) | tab Hari ini/7 hari/Sumber → Sekte › Kas › kas; Kafilah → Sekte › Kas › kafilah; Lapak/Milikku (+42) → Diri › Harta › usaha | H (Pasang ×3) |
| `peta_kota` (`LayarPetaKota`) | Dunia › Peta › kota | H |
| `tema_dagang` (`LayarBukuDagang`) + 42 kota_buku Harga + gudang | Dunia › Dagang | H |
| `tema_teh` (`LayarTeh`), `tema_pasar_gelap` (`LayarPasarGelap`, + 42 pasar_hantu), `tema_taruhan` (`LayarTaruhan`, + 42 guanpu), `tema_irama` (`LayarIrama`) | Di Sini | H |
| `tema_tawar`, `tema_kapal`, `tema_galangan`, `tema_mural`, `tema_sutra`, `tema_bait`, `tema_herba`, `tema_ziarah`, `tema_lift`, `tema_kitab_terlarang`, `tema_kontrak`, `tema_tawanan`, `tema_dokumen`, `tema_racik` (`LayarTawar`, `LayarKapal`, `LayarGalangan`, `LayarMural`, `LayarSutra`, `LayarBait`, `LayarHerba`, `LayarZiarah`, `LayarLift`, `LayarKitabTerlarang`, `LayarKontrak`, `LayarTawanan`, `LayarDokumen`, `LayarRacik`) | Di Sini | H |
| `gabung_direktori` (`LayarDirektori`), `gabung_lamaran` (`LayarLamaran`) | Sekte › Luar › sekte_dunia (Lamaran = rinci) | H |
| `gabung_kartu` (`LayarKartu`) | Sekte › Ikhtisar › kartu / hukum (catatan) / keluar | H |
| `gabung_pangkat` (`LayarPangkat`) | Sekte › Anggota › pangkat (Sumber Perguruanku) | H |
| `gabung_paviliun` (`LayarPaviliun`) | Sekte › Kas › toko_pk (Sumber Perguruanku); tab Kediaman tidak dipasang | H |
| `gabung_papan` (`LayarPapan`) | Sekte › Kegiatan › papan; tab Tugas → kegiatan/tugas | H |
| `gabung_kediaman` (`LayarKediaman`, + 42 `rumah`) | Diri › Harta › kediaman | H |
| `gabung_politik` (`LayarPolitik`) | per tab (G67) | H (Pasang ×4) |
| `gabung_putusan` (`LayarPutusan`), `gabung_shifu` (`LayarShifu`) | prompt Di Sini | H |
| `gabung_hukum` (`LayarHukum`) | Di Sini sidang (`LayarYamen`) | L |
| 42 `warga_kartu`, `warga_pohon` | Di Sini (kartu orang; Pohon = sub) | H (kelas apa pun buatan 42U-1) |
| 42 `kota_buku` | Dunia › Kota (tanpa tab Harga) | H |
| 42 `kota_milikku`, `toko_pemain`, `lapak_sewa`, `investasi`, `jabatan` (Jabatan/Pajak) | Diri › Harta › usaha; Di Sini saat di toko/lapak/kantor | H |
| 42 `gudang` | Dunia › Dagang › gudang (Kontrak → kontrak; Kirim → kafilah) | H |
| 42 `pakta`, `cabang` | Sekte › Wilayah › desa_pakta / cabang | H |
| 42 `kavling` (+ jabatan tab Kavling) | Dunia › Kota › kavling; Di Sini di kavling | H |
| 42 hub `kota_buku`, `kota_milikku`, `kota_sekte` | Dunia › Kota / Diri › Harta / Sekte › Wilayah | kartu tidak dibuat (sudah aturan jadwal) |

Kunci lembar baru yang muncul dari 42-G2 dan tidak ada di tabel ini: agen finish F43-S menambahkannya dengan aturan
"orang → Di Sini; kota → Dunia › Kota; milik pribadi → Diri › Harta; sekte di kota → Sekte › Wilayah" dan mencatatnya
di `m2-F43-S.md`.

---------------------------------------------------------------------------------------------------------------------

### 43.13 Perbaikan yang wajib (bagian dari migrasi)

| # | Masalah (sumber) | Perbaikan | Pemilik |
|---|---|---|---|
| R1 | Esc dari Murid (M/kartu) tidak kembali ke hub (1:3.3) | halaman, tidak ada layar anak | M03 |
| R2 | Esc dari Cincin (kartu Dunia) jatuh ke dunia (1:3.3, 4:1.5) | `HubNav.kembaliSetelah(RingScreen.class)` | M21 |
| R3 | Esc dari Titah (kartu Keabadian) jatuh ke dunia (1:3.3) | halaman | M16 |
| R4 | `/kultivasi duel layar` tidak membuka apa-apa (4:1.4) | `OpenScreen(DUNIA, DUNIA_DUEL)` (S1) | M14 |
| R5 | Kartu Ensiklopedia yang hanya pindah koin, kartu `kota_abadi` yang hanya membuka bantuan (4:4, 3:U4) | kartu hilang (G03) | S-A / M15 |
| R6 | Tombol perintah-obrolan: Duel Catatan/Hormat/Menyerah/Tunda, Aula "Saudara Sekte", chip petunjuk Panduan (4:1.4, 2:5, 4:4) | Catatan: payload baru (S2) tampil di sub catatan; Menyerah/Tunda: tetap perintah server tanpa menutup Gulungan + `SegelKonfirmasi`; Hormat: perintah lalu tutup Gulungan (pose terlihat, disengaja); Saudara Sekte: daftar di Aula (S3); chip petunjuk: `ChatScreen` terisi + `kembaliSetelah(ChatScreen.class)` | M14, M05, M17 |
| R7 | Tanpa konfirmasi: Diplomasi perang/putus/tuntut/tantang, semua aksi Seteru (2:5) | `SegelKonfirmasi` sedang (perang: berat) | M11 |
| R8 | Ukuran tetap meluap: Saingan 360×236, Helm 300×236, Diplomasi 440×280 dan Seteru 470×296 diperkecil pose ≈ 0.64 (3:U5, 2:5) | tata responsif 43.3.2; tanpa pengecilan | M13, M12, M11 |
| R9 | Kotak klik kecil: segel Kebun 11×11, "Beli" Treasury 32×11, tombol Calon/Treasury 11 px (2:5) | ≥ 16×12 | M10, M08, M06 |
| R10 | Kolom tidak bisa diketik / tolak tanda baca diam-diam: `InkField` hanya huruf/angka/spasi/-/_ (2:5) | `InkField` tunggal menerima semua karakter cetak (nama aula tetap dibatasi 24) + `Ketik.fokus` | S-B |
| R11 | Layar membuka dirinya lagi: `UjianScreen` 3 s setelah Esc; `TreasureScreen` menyela (3:U9) | prompt Di Sini (43.8.2) | M13 |
| R12 | `TurnamenScreen` Terima menutup tanpa kembali (2:4) | halaman; Terima tetap di halaman | M09 |
| R13 | Kemudi menutup diri ke Peta tanpa kembali (3:U10) | mode pilih tujuan (43.8.3) | M12 |
| R14 | `MisiLaporanScreen` muncul di atas layar apa pun (2:4) | rinci halaman Misi; dari item: `HubNav.buka` | M09 |
| R15 | `SuasanaScreen` hanya anak lewat heuristik; `JaringScreen` membuka diri dari payload tak diminta (2:4) | halaman; payload tak diminta → `HubNav.buka` | M04 |
| R16 | Lembar Diplomasi/Seteru basi saat dibuka (cache) (2:5) | `onShow` meminta segar, tampil "memuat…" | M11 |
| R17 | Murid tertutup otomatis > 64 blok tanpa peringatan; tidak ada cara ganti murid (2:5) | pemilih murid; murid jauh: sub selain buku_harian terkunci dengan alasan, tidak menutup | M03 |
| R18 | Daftar baca-saja yang tidak bisa diklik (Suasana, Tangga, Jaring) (2:5) | klik baris → Murid › Murid | M04 |
| R19 | Pemilih pekerja Kebun menutup dengan klik di mana saja (2:5) | `PemilihOrang` sembul dengan Batal dan Esc | M10 |
| R20 | `DiscipleScreen.addExtraTab` penuh (3/3) (2:4) | sub didaftarkan lewat `subs()`, tanpa batas tetap | M03 |
| R21 | Kartu melewati anggaran 9 (Dunia 12, Aula 12) (1:5.6) | kartu hilang; halaman ≤ 8 per bagian | S-A |
| R22 | Seteru di bagian Murid; Direktori di Dunia (2:4, 3:U3) | Sekte › Luar | M11 |
| R23 | Kunci pembuka tanpa ikatan (codex, guide, peta) (3:U2, 4:4) | K, J, N (43.10.3) | S-A |
| R24 | M di layar murid = murid berikutnya (1:5.4) | M = buka/tutup Murid; ganti murid ← → | S-A / M03 |
| R25 | Lembar 42 tanpa pabrik dibuang, `pasar_hantu` jatuh ke obrolan (3:U8) | diselesaikan jadwal 42; 43 memetakan kunci (43.12.4) | S-C |
| R26 | Kit 38 tanpa segel "?" (3:U6) | `helpTopic()` setiap halaman lembar (`kultivator.help.<layar>`) | S-C, M19, M20 |
| R27 | Tooltip vanilla di 5 layar, tanpa tooltip di 3 (1:5.2, 2:23) | `SlipTooltip` | pemilik masing-masing |
| R28 | `CodexScreen.TAB_HELP` rintisan; `HallScreen()` tanpa pemanggil; `Keys.all()` mati (4:4, 2:4, 1:2) | dihapus bersama kelasnya / metodenya | M02, M05, S-A |
| R29 | Ukuran bingkai melompat antar layar anak (1:5.5) | satu kertas (43.3.2) | S-A |
| R30 | Undang Langit harus mengetik nama persis (2:5) | `PemilihOrang` pemain daring + `InkField` cadangan | M10 |
| R31 | Detail slip Kodeks selalu menutupi kisi di hub sempit (4:1.1) | halaman lebar 560 | M02 |
| R32 | Hologram Formasi dan perjalanan simpul menutup layar tiba-tiba (4:4) | tetap menutup Gulungan ke dunia (disengaja), dengan pesan Kabar | M07 |
| R33 | Tiga hal bernama "Paviliun", dua "Titah", dua "Alam", dua "Kontrak" (2:4, 3:F19) | G63 | pemilik lang masing-masing |

---------------------------------------------------------------------------------------------------------------------

### 43.14 Perubahan server (daftar tertutup)

| # | Berkas | Suntingan | Pemilik |
|---|---|---|---|
| S1 | `network/Payloads.java`; `duel/DuelCommands.java` | konstanta `OpenScreen.DUNIA_DUEL = 4`; `screen(player)` mengirim `new OpenScreen(OpenScreen.DUNIA, DUNIA_DUEL)` (baris obrolan tetap sebagai cadangan klien tanpa mod) | M14 |
| S2 | `duel/net/DuelPayloads.java`, `duel/net/DuelNet` (verifikasi nama), `duel/DuelCommands.java` | `DuelAnswer` jawaban baru `ANSWER_CATATAN` (c→s) dibalas `DuelCatatan(CompoundTag)` (s→c) berisi data yang sekarang dicetak `records(...)`; perintah obrolan tetap | M14 |
| S3 | `sect/hall/Halls.java` (pembangun `HallInfo`) | daftar `saudara` (nama anggota grup `/sect` pemilik) di tag `HallInfo` | M05 |
| S4 | — | tidak ada perubahan lain: semua rute lain klien saja | — |
| S5 | `TempatLayar` | tidak berubah; hub tetap memakai `OP_LIHAT` dan aturan `JANGKAU` | — |

---------------------------------------------------------------------------------------------------------------------

### 43.15 Lang, nama, deactivation

* Fragmen baru: `gui43` (S-A, S-B, S-C bersama: setiap agen kerangka menulis kuncinya di bagian berawalan
  `kultivator.gui43.host.*`, `kultivator.gui43.kit.*`, `kultivator.gui43.konteks.*`; satu berkas, kunci tidak ganda; S-A
  pemilik berkas, S-B/S-C menyerahkan kunci lewat "Butuh dari modul lain" dan S-A menempelkannya), dan
  `gui43_<label>` per agen migrasi (`gui43_m01` ... `gui43_m22`, `gui43_p1` ... `gui43_p8`).
* Nama halaman/sub: `kultivator.gui43.hal.<bagian>.<halaman>` dan `kultivator.gui43.sub.<halaman>.<sub>`; bantuan
  `kultivator.help.<topik>` (Ensiklopedia) bila belum ada.
* Ganti nama G63 lewat kunci lang yang ada (fragmen pemilik); bila kunci lama dipakai juga oleh server (pesan obrolan),
  tambah kunci baru, jangan ubah yang lama.
* `.deactivation`: dilakukan oleh agen yang memindahkan fitur bila hanya berkasnya sendiri yang memakai kelas itu.
  **Bingkai, basis dan kit yang mungkin masih direferensikan agen lain** (`HallScreen`, `HallPage`, `DiscipleScreen`,
  `DiscipleTab`, `DuniaScreen`, `DuniaPage`, `KeabadianScreen`, `KotaScreens`, `KotaPage`, `CodexScreen`, `CodexPage`,
  `DuelScrollScreen`, `HubCards`, `HubCardsPage`, `hall/InkField`, `codex/CodexWidgets`, `codex/InkSearchField`,
  `dunia/peta/PetaUi`, `tempat/ui/tema/TemaUi`, seluruh kit 38 lama) diganti nama **hanya oleh P1** setelah `grep`
  membuktikan tak ada pengimpor; pemiliknya di M1/M2 cukup berhenti memakainya. Setiap `.deactivation` dicatat di
  progres agen.

---------------------------------------------------------------------------------------------------------------------

### 43.16 Modul, kepemilikan berkas dan urutan

#### 43.16.1 Gelombang kerangka 43-S (3 agen) → F43-S

| Label | Lingkup | Berkas (milik penuh) |
|---|---|---|
| **43S-A** Gulungan & navigasi | 43.3, 43.4, 43.6 (`Page`), 43.10.3, rute `KultivatorClient` | `screen/{CultivationScreen, Page}`, baru `hub/{HubPages, Urutan, Tujuan}`, `hub/{HubNav, QuickWheel, KeyLayout}`, `Keys.java`, `epic/camera/SkipKey.java`, `KultivatorClient.java` (rute `OpenScreen` → `HubNav.buka`, CRLF), lang `gui43` |
| **43S-B** Kit tunggal | 43.7 | baru `screen/ui/{TabBar, DaftarGulir, InkField, Saring, SegelKonfirmasi, PemilihJumlah, LabelHarga, Bilah, CincinWaktu, KartuBarang, PemilihOrang, DaftarSekte, PapanMisi, Kronik, KartuHasil, DaftarPilihan, TombolKembali, KeadaanKosong, KeadaanTerkunci, Potret, Pratinjau3D, LencanaPangkat, PapanSkor, Lukis, Fokus}`; lama `screen/ui/{TalismanButton, JadeCoin, JadeSlider, Palette, Brush, Glyphs, KeyHints, SlipTooltip, ScrollFrame}` |
| **43S-C** Lembar & konteks | 43.6 (`LayarTempatDasar`), 43.8.1-43.8.2, 43.12.4 rute | `tempat/ui/kit/LayarTempatDasar`, `tempat/ui/tema/LayarTemaDasar`, `sekte/gabung/ui/layar/LayarGabungDasar`, `tempat/ui/UiTempatClient`, baru `hub/DiSini.java`, `hub/Konteks.java`, `hub/LembarHub.java` (pemasang `daftarLembar`) |

Urutan dalam 43-S: S-A menulis **langkah 1** dulu (tanda tangan `Page`, `HubPages`, `Tujuan`, `HubNav.buka`, `Urutan`
dibekukan, rintisan bisa dikompilasi) dan menandainya di `m2-43S-A.md`; S-B dan S-C mulai setelah itu (S-B bisa langsung
karena widget tidak bergantung pada `Page`). Masa transisi: S-A mempertahankan `HubCardsPage` sebagai halaman terakhir
"Lainnya" di setiap bagian yang masih punya kartu `HubCards` (agar permainan tetap bisa dipakai di antara gelombang) dan
`Tujuan` yang halamannya belum terdaftar memanggil pembuka lama (`HubNav.cadangan(Tujuan, Runnable)`). S-C menjaga semua
52 turunan kit tetap terkompilasi (bidang jembatan 43.6).

**F43-S**: tunggu 3 label selesai; terapkan "Butuh dari modul lain"; build B0; periksa: Gulungan muat 341×192 (uji skala 4),
semua lembar 38/41/42 terbuka di Gulungan (Di Sini atau baca-saja hub), kunci lembar 42 baru dipetakan (43.12.4 catatan).

#### 43.16.2 Gelombang migrasi 43-M1 (16 agen, paralel) → F43-M1

Setiap agen: memindahkan kelasnya menjadi `Page` (atau memasang lembar), mendaftarkan halamannya dengan `HubPages` di
berkas `*Client` modulnya, menghapus `HubCards.register` dan pembuka layar lama miliknya, memakai kit `screen/ui` saja,
menerapkan perbaikan 43.13 miliknya, menulis lang `gui43_mNN`, mengganti nama berkas yang dileburnya menjadi
`.deactivation`.

| Label | Halaman | Berkas (milik penuh selama 43-M1) |
|---|---|---|
| **M01** Diri | status, akar_dao, jalan (pasang), alkimia, pusaka, harta | `screen/{StatusPage, RootDaoPage, AlchemyBookPage}`, `tempa/PusakaPage`, `sekte/gabung/ui/layar/LayarKediaman`, kelas klien 42 `rumah`/`kota_milikku`/`toko_pemain`/`lapak_sewa`/`investasi`/`jabatan`; pasang `buku_kas` usaha (kelasnya milik M08) |
| **M02** Teknik | kodeks, cincin_segel, kehancuran | `codex/**` kecuali `CodexHud`, `SealWheel`, `QuickSeals`, `CodexClient`; `screen/TechniquesPage`; `kehancuran/{RedSealPage, KehancuranConfirmScreen, KehancuranClient}` |
| **M03** Murid | murid (pemilih + 10 sub) | `disciple/{DiscipleScreen, DiscipleTab, DiscipleClient, StatusTab, OrderTab, PillTab, TrigramTile, TechniqueTab, TeachCard}`, `disciple/power/{TalentTab, TreasureTab}`, `disciple/path/PathTab`, `sekte/kepribadian/HatiTab`, `sekte/hubungan/HubunganTab`, `sekte/mandiri/{JalanHidupTab, BukuHarianScreen, Sentuh, Halaman, HarianTeks}` |
| **M04** Daftar & Jaring | daftar, jaring | `sekte/mandiri/{MandiriBoardScreen, MandiriClient}`, `sekte/kepribadian/{SuasanaScreen, KepribadianClient}`, `sekte/tangga/{TanggaCatatanScreen, TanggaClient}`, `sekte/hubungan/{JaringScreen, HubunganClient}`, baru `sekte/hubungan/PelukisIkatan` |
| **M05** Sekte Ikhtisar | ikhtisar | `hall/{HallScreen, HallPage, AulaPage, LogPage, HallClient, HallUi, InkField}`, `sekte/seragam/{LambangDesignerScreen, SeragamClient}`, `sekte/gabung/ui/layar/{LayarKartu, LayarHukum}`, `sekte/gabung/ui/{GabungHub, GabungUiClient}`; server S3 `sect/hall/Halls.java` (hanya pembangun `HallInfo`) |
| **M06** Sekte Anggota | anggota | `hall/{CalonPage, CandidateCard, JenjangPage}`, `sekte/tetua/{BalaiTetuaScreen, TetuaClient}`, `sekte/gabung/ui/layar/{LayarPangkat, LayarPolitik}` |
| **M07** Bangun & Formasi | bangun, formasi | `sectbuild/{BuildPage, TemplateScreen, BuildClient}`, `formasi/{FormasiPage, CorePanel, CodexPanel, FieldPanel, NodePanel, LogPanel, FormationSlip, NodeMap, BlueprintDiagram, FormasiClient}` |
| **M08** Kas & Toko | kas | `hall/TreasuryPage`, `sekte/gabung/ui/layar/LayarPaviliun`, `tempat/ui/layar/LayarBukuKas`, `tempat/ui/UiHub`, `sekte/paviliun/{PaviliunScreen, RakScreen, MakamScreen, PaviliunClient}` |
| **M09** Kegiatan | kegiatan | `sectevent/{SectEventPage, DutyPage, MissionPage, ArenaPage, BracketView, RosterView, SectEventClient}`, `sekte/misi/{MisiLaporanScreen, MisiTab, MisiClient}`, `sekte/turnamen/{TurnamenScreen, TurnamenClient}`, `sekte/gabung/ui/layar/LayarPapan` |
| **M10** Wilayah | wilayah | `musuh/PertahananPage`, `desa/HallDesaPage`, `sekte/kebun/{KebunScreen, KebunClient}`, `sekte/langit/ui/{LangitScreen, LangitUiClient}`, `sekte/langit/{LangitVisualScreen, LangitClient, LangitClientConfig}` (baris `rows()`), pasang 42 pakta/cabang |
| **M11** Luar | luar | `sekte/diplomasi/{DiplomasiScreen, DiplomasiClient}`, `sekte/mandiri/seteru/{SeteruScreen, SeteruClient}`, `sekte/gabung/ui/layar/{LayarDirektori, LayarLamaran}` |
| **M12** Peta & Kemudi | peta, perjalanan, Di Sini › Kemudi | `dunia/peta/{PetaScreen, PetaUi, PetaPainter, PetaClient}`, `dunia/kapal/{HelmScreen, KapalHud}`, `tempat/ui/layar/{LayarPetaKota, LayarJurnal}` |
| **M13** Alam & Musuh | alam, musuh, prompt Jimat/Teka-teki/Hadiah | `alam/{AlamPage, TreasureScreen, AlamClient}`, `dunia/reruntuhan/{MenaraScreen, JimatScreen, UjianScreen, ReruntuhanClient}`, `musuh/MusuhPage`, `dunia/kota/SainganScreen`, `dunia/{DuniaScreen, DuniaPage, DuniaClient}` |
| **M14** Duel | duel | `duel/ui/{DuelScrollScreen, DuelScreen, ChallengeScreen, DuelSettings}`, `duel/DuelClient`; server S1 + S2 (`network/Payloads.java` hanya konstanta, `duel/DuelCommands.java`, `duel/net/DuelPayloads.java`, `duel/net/DuelNet`) |
| **M15** Kota & Dagang | kota, dagang, jembatan K | `dunia/kota/{KotaScreens, KotaPage, TownPages, AbadiPages, MeetingPage, KotaClient}`, baru `dunia/kota/KotaJembatan`, `tempat/ui/tema/{LayarBukuDagang, TemaHub}`, kelas klien 42 `kota_buku`/`gudang` (apa pun namanya, 43.12.4) |
| **M16** Keabadian | alam_abadi, purba, batin, ciptaan, lelang, titah | `keabadian/KeabadianScreen`, `abadi/{AlamAbadiPage, LelangPage, AbadiClient}`, baru `abadi/LelangJembatan`, `purba/{PurbaPage, BencanaPage}`, `batin/{BatinPage, CiptaanPage}`, `pusakalangit/{TitahScreen, TitahClient}` |

Serah-terima tetap dalam 43-M1: sub Hati, Hubungan dan Jalan Hidup dibangun M03 langsung di halaman Murid (mengimpor
`HatiTab`, `HubunganTab`, `JalanHidupTab`); M04 menghapus tiga baris `DiscipleScreen.addExtraTab` di `KepribadianClient`,
`HubunganClient`, `MandiriClient`. Kelas klien 42 di `client/dunia/kota42/ui/**` dimiliki agen halaman rumah kuncinya
(43.12.4): M01 (milik pribadi), M10 (`pakta`, `cabang`), M15 (`kota_buku`, `gudang`, `kavling`), M19 (`warga_kartu`,
`warga_pohon`, `kantor_rekrut`, `perkara`), M20 (sisanya); `KotaUiClient` (pendaftaran) milik M15, yang lain menyerahkan
baris pendaftarannya. Kartu hub di berkas bersama dihapus oleh pemilik berkas, halamannya didaftarkan pemilik halaman:
`UiHub` (M08) menghapus `kota_besar`, `catatan_perjalanan`, `buku_kas`, `lapak_kafilah` (M12 mendaftarkan peta/kota dan
perjalanan); `GabungHub` (M05) menghapus `gabung_sekteku` dan `gabung_direktori` (M11 mendaftarkan luar).

**MUST-NOT-TOUCH (43-M1)**: semua berkas 43-S (Gulungan, `Page`, `HubNav`, `HubPages`, `Urutan`, `Tujuan`, `DiSini`,
`Konteks`, `LembarHub`, `Keys`, `KeyLayout`, `QuickWheel`, `KultivatorClient`, seluruh `screen/ui/**`, `LayarTempatDasar`,
`LayarTemaDasar`, `LayarGabungDasar`, `UiTempatClient`); `screen/{SettingsPage, SettingsRows, HelpPage, JalanPage,
HubCardsPage}`, `guide/**`, `hub/HubCards` (M2/P); semua `tempat/ui/layar/**` dan `tempat/ui/tema/**` yang tidak
tercantum di baris agen; HUD (43.9); `tempa/**` kecuali `PusakaPage`; server kecuali S1-S3; worldgen, NBT, aset kecuali
lang; berkas agen lain. Kebutuhan di berkas yang tidak dimiliki → "Butuh dari modul lain".

#### 43.16.3 Gelombang migrasi 43-M2 (6 agen) → F43-M2

| Label | Lingkup | Berkas |
|---|---|---|
| **M17** Panduan | kitab, ensiklopedia, R6 chip | `guide/{GuideScreen, GuideClient}`, `screen/{HelpPage, JalanPage}`, `goals/GuideTracker` (hanya pembuka; slot HUD milik M22) |
| **M18** Pengaturan | 43.10.1-43.10.2 | `screen/{SettingsPage, SettingsRows}`, `epic/screen/{MotionWarningScreen, PhotosensitivityScreen}`, `epic/EpicClient` (baris pembuka), `dampak/DampakClient` (baris pembuka) |
| **M19** Di Sini inti | 18 layar inti 38 + jembatan Dimensi | `tempat/ui/layar/**` kecuali `LayarBukuKas`, `LayarPetaKota`, `LayarJurnal`; `tempat/ui/layar/SemuaLayar`; `dunia/dimensi/{DimensiScreen, DimensiClient (verifikasi)}`, baru `dunia/dimensi/DimensiJembatan`; kelas klien 42 `warga_kartu`/`warga_pohon`/`kantor_rekrut`/`perkara` |
| **M20** Di Sini tema | 20 layar tema | `tempat/ui/tema/**` kecuali `LayarBukuDagang`, `TemaHub`; `tempat/ui/tema/TemaUi`, `TemaUiClient`; `sekte/gabung/ui/layar/{LayarPutusan, LayarShifu}`; kelas klien 42 `pasar_hantu`/`guanpu`/`upacara`/`rombongan` bila 42U membuatnya terpisah |
| **M21** Tempa | 43.8.5 | `tempa/{ForgeScreen, RingScreen, TempaClient}` |
| **M22** HUD | 43.9 | baru `hud/{Kabar, SlotSisi}`; `QiHud`, `codex/CodexHud`, `goals/{GuideTracker (slot), TribulationHud, MeditationAlert}`, `sekte/mandiri/ToastMandiri`, `sekte/tetua/LaporanCard`, `sekte/mandiri/seteru/KartuJudul`, `sekte/turnamen/LeiTaiHud`, `sekte/tangga/TanggaHud`, `sekte/gabung/ui/HudUjian`, `dunia/tempat/kotabesar/KotaBesarHud`, `formasi/AlarmHud`, `dunia/naga/NagaHud`, `aura/AuraHud`, `dunia/dimensi/DimensiHud`, `dunia/langit/LangitHud`, `dunia/tempat/tema/fx/TemaHud`, `fx/spec/ScreenFlash`, `epic/screen/EpicScreen` (vinyet), `kehancuran/RedSkyHud`, `duel/ui/ChallengeOfferToast`; baris `SystemToast` di `desa/DesaFx`, `hall/HallFx`, `sectbuild/BuildFx` (hanya panggilan toast) |

M17 berbagi `goals/GuideTracker` dengan M22: M17 hanya mengubah pembuka (`GuideClient.open` → `HubNav.buka`), M22 memiliki
berkasnya; M17 menyerahkan suntingan lewat "Butuh dari modul lain". MUST-NOT-TOUCH sama dengan 43-M1 ditambah semua
berkas 43-M1.

#### 43.16.4 Gelombang poles 43-P (8 agen) → F43-END

| Label | Fokus | Berkas |
|---|---|---|
| **P1** Bersih kit | `.deactivation` kit 38 lama tanpa pengimpor; hapus bidang jembatan `LayarTempatDasar`; `HubCards`, `HubCardsPage`, mekanisme anak lama `HubNav` (`openChild`, `expectChild`, `fromHub`, `hubRemoved`, `cadangan`) | semua berkas 43-S + `tempat/ui/kit/**`, `hub/HubCards`, `screen/HubCardsPage` |
| **P2** Diri + Teknik | skala 2/3/4, papan ketik, kosong/terkunci, bantuan | berkas M01, M02 |
| **P3** Murid | sama | M03, M04 |
| **P4** Sekte a | sama | M05, M06, M07 |
| **P5** Sekte b | sama | M08, M09, M10, M11 |
| **P6** Dunia + Keabadian | sama | M12, M13, M14, M15, M16 |
| **P7** Di Sini + Tempa | sama + semua lembar dibuka dari NPC di 2/3/4 | M19, M20, M21 |
| **P8** Panduan, Pengaturan, HUD | sama + topik Ensiklopedia untuk setiap `helpTopic()` + bab panduan | M17, M18, M22 |

Setiap P-label berjalan menurut daftar uji 43.18, memeriksa `grep -rn "pose().scale"` di jalur teks miliknya dan
`grep -rn "setScreen(new"` miliknya, dan menutup "Butuh dari modul lain" yang tersisa.

#### 43.16.5 Langkah finish (hanya agen finish menjalankan Gradle)

Umum: tunggu semua label gelombang selesai di berkas progresnya; baca dan terapkan setiap "Butuh dari modul lain"; cek EOL
setiap berkas lama yang disentuh (`git ls-files --eol`); `mergeLang` tanpa kunci ganda; **satu** kali
`gradlew compileJava compileClientJava jar --no-daemon` (perbaiki dan ulangi sampai hijau); tidak pernah tes, `runClient`
atau `gradlew build`. Tidak menghapus; hanya `.deactivation`. Hapus hanya `javac.*.args`/log build milik sendiri. Catat di
`docs/progres/m2-F43-<S|M1|M2|END>.md`. Tidak commit, tidak menyalin jar.

- **F43-S**: build B0; centang 43.16.1 akhir.
- **F43-M1**: build B1; `grep -rn "HubCards.register"` kosong (ke-20 berkas pendaftar milik M1); `grep -rn "extends HallPage\|extends DuniaPage\|extends CodexPage\|extends DiscipleTab\|extends KotaPage"` kosong.
- **F43-M2**: build B2; `grep -rn "setScreen(new"` di `src/client` hanya `CultivationScreen`, `ChatScreen`, `KeyBindsScreen`;
  `grep -rn "extends Screen"` hanya `CultivationScreen` (+ dua wadah lewat `AbstractContainerScreen`).
- **F43-END**: build B3; docs: tambah `## 43.` ke `docs/DESIGN.md` (LF; ringkasan + rujukan berkas ini), bab
  `docs/PANDUAN.md` berikutnya setelah bab 41/42 ("Gulungan Utama", CRLF), satu baris README (CRLF), `docs/uji/FASE-43.md`
  dari 43.18; perbarui centang berkas ini; lapor ke orkestrator.

#### 43.16.6 Urutan kerja

**Perubahan urutan (orkestrator, 2026-10-25, pengguna: "MEGA nanti dulu, kerjakan yang lainnya dulu"):** 43 dibangun
**sebelum** sisa 42. Kondisi pohon: 41 lengkap, 42-G0 dan 42-G1 selesai (F42-1 hijau), 42-G2 **setengah jadi** (dihentikan
di tengah gelombang). Aturan tambahan yang mengikat untuk semua label 43:
- **Jangan sentuh berkas 42-G2** (tabel `### 42-G2` di `docs/progres/jadwal-41-42.md`: 42B, 42B-C, 42D, 42D-P, 42D-H, 42E,
  42F, 42U-1, 42U-2, 42V-H, 42V-F), termasuk semua `client/dunia/kota42/**` dan `dunia/kota42/ui/**`. Kepemilikan "kelas
  klien 42" di tabel 43.16 **dibatalkan**; lembar 42 tidak dipetakan sekarang. Saat MEGA dilanjutkan, 42U-1/42U-2 membangun
  UI 42 langsung sebagai halaman/lembar Gulungan memakai `Page`, kit `screen/ui`, `HubPages` dan `DiSini` dari 43.
- Kalau berkas 42-G2 setengah jadi membuat build gagal, agen finish boleh memperbaiki **hanya sampai terkompilasi** (rintisan
  minimal, catat di progresnya), tanpa menambah fitur 42.
- Berkas lama yang juga dimiliki label 42-G2 (`SettingsRows`, `RiuhKota`, `TempatWarna`, `LayarDialog`, `PerbaikanApi` dll.):
  43 boleh menyunting bagian UI-nya, dan wajib mempertahankan suntingan 42-G2 yang sudah ada.

```
Prasyarat (asli): 41 + 42 selesai (jadwal-41-42 F-END) dan di-commit
  |
43-S   S-A langkah 1 (Page/HubPages/Tujuan/Urutan beku) -> S-A sisa | S-B | S-C        -> F43-S  (B0)
  |
43-M1  M01 ... M16 (16 paralel; kontrak 43-S; berkas tidak bertumpuk)                     -> F43-M1 (B1)
  |
43-M2  M17 | M18 | M19 | M20 | M21 | M22                                                  -> F43-M2 (B2)
  |
43-P   P1 | P2 | ... | P8                                                                 -> F43-END (B3, docs)
```

---------------------------------------------------------------------------------------------------------------------

### 43.17 Keputusan terbuka (untuk pengguna; saran bawaan dicetak tebal)

| # | Pertanyaan | Pilihan | Saran bawaan |
|---|---|---|---|
| K1 | Daftar dan urutan bagian | **8 bagian sekarang (Diri, Teknik, Murid, Sekte, Dunia, Keabadian, Panduan, Pengaturan) + tablet Di Sini saat ada konteks** / tambah bagian Kota terpisah (layanan kota menjadi Kota › <layanan>) / tambah bagian Perguruan terpisah untuk sekte NPC | 8 + Di Sini |
| K2 | Pola konfirmasi tunggal | **kartu lapisan dengan segel Ya: ringan klik, sedang tahan 1.5 s, berat centang + tahan** / klik dua kali dalam 3 s di tempat (tanpa lapisan) untuk semua | kartu lapisan |
| K3 | Angka 1-9 | **selalu bagian; pilihan dialog dengan ↑↓ + Enter atau klik** / di tablet Di Sini angka memilih pilihan dialog | selalu bagian |
| K4 | Tombol bawaan baru | **K Kitab Teknik, N Peta, J Panduan; Q/E pindah halaman** / tanpa tombol bawaan baru (hanya V) | K, N, J, Q/E |
| K5 | Klik NPC membuka | **Gulungan penuh dengan rel (Di Sini terpilih)** / mode fokus: hanya halaman konteks tanpa rel (rel muncul dengan Tab) | Gulungan penuh |
| K6 | Ukuran teks | **hanya 1.0 dan 1.5** / izinkan 0.75 untuk catatan kaki pada skala GUI ≥ 3 | 1.0 dan 1.5 |

**Diputuskan (orkestrator, 2026-10-25; pengguna: "kerjakan"):** K1, K2, K4, K5, K6 = saran bawaan. **K3 diubah:** angka
1-9 memilih bagian, **kecuali** saat tablet Di Sini menampilkan dialog atau daftar pilihan: di sana angka memilih pilihan
(seperti layar kit sekarang); ↑↓ + Enter tetap bisa. Tidak ada kemunduran bagi pemain yang terbiasa memilih dengan angka.

---------------------------------------------------------------------------------------------------------------------

### 43.18 Daftar uji (Indonesia; disalin ke `docs/uji/FASE-43.md` oleh F43-END)

**Uji terima:**
1. 1366×768 skala GUI 4: setiap bagian, setiap halaman, setiap sub tampil tanpa tumpang tindih, tanpa teks di bawah 1.0,
   tanpa guliran mendatar; juga skala 2 dan 3 dan `uji ui skala 2/3/4` untuk lembar.
2. Hanya dengan papan ketik: buka (V), pindah bagian (1-8), halaman (Q/E), fokus (Tab), sub (←/→), cari (`/`), beli di
   Toko PK dengan pemilih jumlah, konfirmasi (Enter tahan), mundur (Esc) sampai dunia.
3. Klik kanan murid → Murid › Murid murid itu; ← → mengganti murid; menjauh > 64 blok: sub terkunci dengan alasan,
   Gulungan tetap.
4. Klik prasasti aula → Sekte › Ikhtisar › Aula; Bangun › Templat "Lihat di dunia" memperlihatkan hologram dan Enter
   kembali.
5. Klik NPC toko senjata → tablet Di Sini "Toko Senjata · <nama>", beli berfungsi; menjauh → tablet hilang dengan pesan.
6. Klik warga modul K (Juru Lelang) → `LayarLelang` yang sama dengan balai lelang 38 dan Keabadian › Lelang.
7. Klik NPC dunia bawah → dialog yang sama dengan dialog warga 38 (palet violet).
8. Teka-teki menara: Esc tidak membuat layar kembali sendiri; tablet Di Sini menunggu; jawaban berfungsi.
9. Cincin dari Diri › Pusaka → layar wadah; Esc kembali ke Diri › Pusaka.
10. `/kultivasi duel layar` membuka Dunia › Duel; Catatan tampil di halaman; Menyerah meminta konfirmasi dan Gulungan tetap
    terbuka.
11. Diplomasi "Perang" meminta konfirmasi berat; Seteru aksi meminta konfirmasi.
12. Pengaturan › Tampilan berisi sakelar Langit Sekte; menyalakan efek dampak Penuh / kamera Spektakuler memunculkan
    konfirmasi di dalam Gulungan.
13. Pengaturan › Kontrol: ubah tombol di tempat; bentrok ditandai.
14. HUD: hanya satu gulungan sisi pada satu waktu; toast semua lewat Kabar; cincin bidik satu rupa.
15. `grep` 43.16.5 bersih; semua kelas di 43.12 punya tempat; tidak ada kartu peluncur.

**Daftar centang:**
- [x] `Page` publik superset; lima basis lama `.deactivation`
- [ ] Gulungan responsif 341×192 … 560×330; animasi hanya saat buka
- [ ] Satu kit `screen/ui`; kit 38 lebur kecuali `LayarTempatDasar`/`Lembar`/`GelembungDialog`
- [ ] 84 kelompok fungsi ganda (43.11) masing-masing satu yang bertahan
- [x] Semua jalan masuk 43.4.4 menuju `Tujuan`; tidak ada layar anak
- [ ] Di Sini: konteks, prompt, jembatan K/Abadi/Dimensi
- [ ] Pengaturan menyerap Langit, duel, peringatan; Kontrol rebind; K/N/J bawaan
- [ ] HUD lima slot, satu gaya
- [ ] Perbaikan R1-R33
- [ ] Server hanya S1-S3
- [x] Lang `gui43*` dua bahasa identik; tanpa kunci ganda
