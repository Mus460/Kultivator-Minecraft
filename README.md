# Kultivator — mod kultivasi xianxia untuk Minecraft 26.3 (Fabric)

**Bahasa Indonesia** · [English below](#english)

Mod Fabric berdiri sendiri di mana **pemain** berkultivasi seperti di novel xianxia: memurnikan qi, menembus ranah dari
Manusia Fana sampai Dewa Abadi, bertahan dari Tribulasi Langit, memilih Jalan Dao, menguasai teknik, terbang di atas
pedang, meracik pil, menghadapi Iblis Hati, membangkitkan binatang roh, dan mendirikan sekte.

📖 **Panduan pemain lengkap: [docs/PANDUAN.md](docs/PANDUAN.md)** · Daftar uji per fase: [docs/uji/](docs/uji/) ·
Rancangan dan aturan kode: [docs/DESIGN.md](docs/DESIGN.md)

**Aturan keras:** efek dan kekuatan mod ini tidak pernah membakar, merusak, memasang, atau mengubah blok apa pun.
Petir tribulasi hanya partikel dan suara (tanpa entity petir), cuaca dan waktu tidak diubah, dan tribulasi tidak pernah
membunuh — kalau gagal, pemain terluka (Penyimpangan Qi).

## Fitur

* **9 ranah, 65 posisi** — Manusia Fana, Pemurnian Qi, Pembangunan Fondasi, Inti Emas, Jiwa Baru Lahir, Transformasi
  Dewa, Penyatuan Dao, Kenaikan Abadi (9 tingkat), Dewa Abadi; masing-masing dengan tahap Awal, Menengah, Akhir, Puncak.
  Kultivasi dari monster, bijih, meditasi, pil, dan pencerahan; leher botol di Tahap Puncak menyimpan **Qi Tertimbun**.
* **Terobosan** — kebangkitan, lemparan peluang, lalu **Tribulasi Langit** dengan varian Permukaan, Api Neraka (Nether),
  dan Kehampaan (End), gelombang **Pelindung Dao**, **Kualitas Terobosan** (Retak/Biasa/Sempurna/Surgawi),
  **Penyimpangan Qi**, dan mode Hardcore opsional.
* **Meditasi** — pose bersila, tempat spiritual (puncak gunung, Hutan Ceri, suar, kecubung, End, bulan purnama), urat roh
  dengan kepadatan qi, Momen Pencerahan, dan tiga **formasi pengumpul qi** dari blok vanilla.
* **Akar spiritual** — upacara pengujian dengan 9 elemen dan 5 mutu, Pil Pembersih Sumsum.
* **8 Jalan Dao** dengan Pemahaman Dao dan **23 teknik** (Langkah Awan, Pedang Terbang, Telapak Buddha, Wujud Dharma,
  Wilayah Dao, Seribu Pedang, Api Phoenix, Petir Surgawi, Penjara Ruang, …); kitab teknik dari Buku Pesona vanilla.
* **Pil** — barang vanilla sebagai pil dengan racun pil, dan **Alkimia** di tungku Kuali di atas Api Unggun dengan pil
  bermutu Rendah sampai Sempurna.
* **Iblis Hati**, **Binatang Roh** (serigala dan rubah hingga Serigala Langit / Rubah Ekor Sembilan), **Formasi
  Pelindung**, **Sekte** dan **Latih Tanding**.
* **Pengakuan Langit** — peristiwa sinematik saat menjadi Dewa Abadi: pilar cahaya emas, langit terbuka, suara langit,
  aura pelangi, kebal Penyimpangan Qi, dan regenerasi abadi.
* **Visual mewah** — aura per ranah, jubah sesuai Dao, pedang di punggung, mata bercahaya, Lingkaran Dao, pedang terbang
  di bawah kaki; HUD bola dantian dan layar "Gulungan Giok"; **Kualitas Efek** Minimal / Normal / Maksimal.
* Semua teks dalam **Bahasa Indonesia** dan **English**. Kompatibel dengan Sodium, Iris, ImmediatelyFast, EMF/ETF,
  Lithium, FerriteCore.

## Status pengembangan

| Fase | Isi | Status |
|------|-----|--------|
| 0 | Proyek, data pemain (tahan respawn/keluar-masuk/pindah dimensi), perintah uji | ✅ |
| 1 | Ranah & tahap, leher botol + Qi Tertimbun, terobosan + tribulasi, meditasi, pil vanilla, bar Qi, layar info | ✅ |
| 2 | Akar spiritual, Jalan Dao, teknik, kitab teknik | ✅ |
| 3 | Iblis Hati, binatang roh, formasi pelindung, sekte | ✅ |
| 4 | Visual mewah, alkimia, Pengakuan Langit (event Dewa Abadi), pencapaian akhir, panduan pemain | ✅ |
| 5 | Murid: bot pengikut yang diajari teknik dan ikut berkultivasi, perintah uji | ✅ |
| 6 | Sekte Abadi & murid OP (Aula Sekte, Bintang Abadi, Mode Kehancuran) | ⏳ |
| 7 | Dunia yang Hidup (monster elite, batu roh, jimat, herba) | ⏳ |

## Perintah singkat

`/kultivasi` atau `/cultivate` — `info`, `meditate`, `breakthrough`, `hardcore on|off`, `root test|info`,
`dao [info|choose <dao>]`, `technique list|select|cast`, `visual <bagian> on|off`, `beast list`, `formation list`.
`/sekte` atau `/sect` — `create`, `invite`, `join`, `leave`, `kick`, `disband`, `info`, `list`, `chat`,
`spar <pemain>|accept|decline`. Perintah uji operator ada di bawah `/kultivasi debug` — daftar lengkapnya di
[panduan](docs/PANDUAN.md#33-perintah-uji-untuk-operator-kultivasi-debug-).

## Tombol bawaan

`G` teknik terpilih · `;` meditasi · `'` layar kultivasi · `,` `.` ganti teknik · `\` pedang terbang · lompat di udara =
Langkah Awan. Semua bisa diganti di Options → Controls → Key Binds → Kultivator.

## Konfigurasi

`config/kultivator.json` (server): `xpMultiplier`, `hardcoreForced`, `daoProtectorEvent`, `tribulationDamageScale`,
`heartDemonTrials`, `protectiveFormations`, `announceBreakthroughs`. `config/kultivator-client.json` (klien):
`effect_quality`, `hud`. Penjelasan di [panduan](docs/PANDUAN.md#23-berkas-konfigurasi).

## Build

Java 25. `gradlew.bat compileJava compileClientJava jar --no-daemon` → `build/libs/kultivator-<versi>.jar`.
Teks ditulis per modul di `lang-src/*.en_us.json` dan `lang-src/*.id_id.json`; task `mergeLang` menggabungkannya dan
gagal kalau ada kunci yang hanya ada di satu bahasa. Rancangan lengkap: [docs/DESIGN.md](docs/DESIGN.md).

---

## English

A standalone Fabric mod for Minecraft 26.3 in which **you** cultivate like in a xianxia novel: refine qi, break through
nine realms (65 ranks) from Mortal to True Immortal, survive heavenly tribulations (with Nether and End variants and
waves of Dao Protector monsters), walk one of eight Daos, master 23 techniques, fly on your sword, refine pills in an
alchemy furnace, face your heart demon, awaken spirit beasts, build protective formations, found a sect and spar with
friends. Reaching True Immortal triggers a cinematic ascension event (a golden pillar of light, the heavens opening, a
celestial voice) that grants a rainbow aura, immunity to Qi Deviation and slow regeneration. Visuals (auras, robes, back
swords, glowing eyes, Dao circles) scale with a client Effect Quality setting.

Nothing in this mod ever burns, breaks, places or changes a block; tribulation lightning is particles and sound only,
the weather and time are never changed, and a failed tribulation injures you (Qi Deviation) instead of killing you.
The full player guide is in Indonesian: [docs/PANDUAN.md](docs/PANDUAN.md); every rule and number is in
[docs/DESIGN.md](docs/DESIGN.md); per-phase test lists in [docs/uji/](docs/uji/).

License: MIT.
