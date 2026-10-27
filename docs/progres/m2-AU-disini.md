# m2-AU-disini: perbaikan audit ulang irisan "disini"

Sumber temuan: side-session-notes `gui-audit-ulang-disini.md` (commit 1e305fa0). Gradle tidak dijalankan.

| # | Jenis | Temuan | Status |
|---|-------|--------|--------|
| 1 | bug | Kembali ke percakapan untuk layanan berumah (LembarHub.Halaman) | [x] Halaman.rinci/mundur memakai DiSini.bisaKembali/kembali(this); LayarTempatDasar.onShow mengenali pemegang |
| 2 | ganda | buku_kas tanpa rumah terdaftar | [x] TreasuryPage.daftarRumah: rumah buku_kas (4 tab, sama dengan mount) + V_BUKU memilih tampilan/kafilah dari tab lembar; UiHub.init: rumah lapak di Diri › Harta |
| 3 | tak_terjangkau | Titah Peringatan cahaya / Langit Pemandangan | [x] Titah memanggil SettingsPage.peringatanDampak (Kamera & Film + segel teks, Ya = Penuh seperti dulu); SettingsPage.fokus({baris:kunci judul}) menggulir, LangitClient.openSettings memakainya |
| 4 | ganda | Kota K Teh/Gelap/Pertemuan | [-] dilewati: keputusan K7 di 43.17 masih "belum diputuskan" (pengguna); irisan dunia mencatatnya sebagai G-2 |
| 5 | tampilan | TitleCard STAMP vs Kabar | [x] TitleCard.show(STAMP) mengirim Kabar.kartu(judul tebal).ikon(segel).lama(1500); drawStamp dan bidang stamp dihapus (semua pemakai STAMP ikut) |
| 6 | tampilan | SlotSisi menimpa Kabar | [x] SlotSisi.atas mencatat tinggi slip; Kabar membatasi baris kartu sampai H-44-tinggiAktif-4 selama slip tampil (shortcut: tumpukan kartu polos yang lebih tinggi masih bisa bertemu slip) |
| 7 | kurang | Sakelar per HUD | [x] SettingsRows.HUD_SAKLAR (18 koin "hud.<kunci>" di kultivator-client.json, bawaan nyala) di grup HUD; HudHider membungkus elemen HUD mod lewat replaceElement (Kabar = mandiri_toast); SlotSisi melewatkan isi yang dimatikan sehingga giliran turun; kunci gui43.m18.hud.* id/en. Panduan tetap memakai koinnya sendiri; overlay film/keselamatan (TitleCard, Dampak, RedSky, MeditationAlert, VoiceCaptions, QuickWheel) diatur grupnya sendiri |
| 8 | kurang | Tab Quest dialog | [x] Tab Quest tanpa baris tahap: hanya nama + Bicarakan (op quest:ID) dan tombol "Lihat semua di Perjalanan" (Dunia › Perjalanan); kunci tempat_ui.dialog.perjalanan id/en. Saring per NPC di LayarJurnal tidak dibuat: baris jurnal tidak membawa NPC (perlu data server) |
| 9 | kurang | Lamaran tanpa rumah | [-] needs irisan sekte: daftarRumah dan tampilan rinci ada di sekte/diplomasi/LuarPage (PageKomposit mode daftar menyembunyikan sub lembar anak) |
| 10 | kurang | Kit 38 masih hidup | [-] dilewati: kit 38 masih dipakai kota42 (G2, dilarang disentuh) dan ~40 lembar sekte/gabung + tema; .deactivation belum mungkin. Pemindahan LayarBukuKas/Jurnal/PetaKota/BukuDagang ke LayarInti/TemaHalaman + ganti tanda tangan basis = pekerjaan tersendiri (needs) |
| 11 | ganda | Strip info tiga kali | [x] Satu stripInfo(screen.ui.Lukis,..) + kunciLayar(..) protected final di LayarTempatDasar (adaStrip ikut protected; terkunciSejak basis); LayarInti dan TemaHalaman memakainya, salinannya (strip/gambarStrip/adaStrip/warnaDistrik/STRIP/terkunciSejak) dihapus. Versi kit 38 (gambarStrip) tetap untuk mesinLama sampai 42 dibangun ulang |
| 12 | kurang | uji ui skala untuk lembar kit tunggal | [-] needs irisan inti: skala harus diterapkan di Page.render + transform mouse di Page.mouse* (inti); menghapus UJI_SKALA berarti mengubah perintah server UjiUi (38.U.2 dipertahankan 43.0.5) |
| 13 | kurang | PetaPainter.mini | [-] dilewati: hanya Teleport yang punya peta mini titik-tujuan; LayarKapal menggambar bagan pelayaran hias (tanpa titik), LayarPetaKota adalah peta kota itu sendiri (sub Peta › Kota). Satu pemakai = tidak ada ganda untuk dilebur |
| 14 | bug | Judul kepala Di Sini ganda | [x] Kepala Di Sini hanya menulis konteksJudul |
| 15 | kurang | Denyut tablet Di Sini | [x] init() pertama menggeser konteksSejak ke openedAt+150 bila konteks lebih tua; denyut tidak digambar sebelum waktunya |
| 16 | ganda | UjianScreen action-bar vs Kabar | [x] UjianScreen.onHide mengirim kartu Kabar berkunci di_sini.menunggu (isi m13.ujian.menunggu); baris action-bar dihapus |
| 17 | bug | Jarak konteks di klien | [x] DiSini.jaga (END_CLIENT_TICK dari UiTempatClient.init): sesi TempatLayar dengan NPC hilang/lebih jauh dari JANGKAU+2 kirim OP_TUTUP lalu selesai(berakhir); Kemudi 50 blok (server 48); modul K tetap memakai JAUH sendiri |
| 18 | kurang | PACK_GEOMETRY tanpa baris | [x] toggle(PACK_GEOMETRY,"pack") di EpicSettings.rows (grup Tampilan, urut setelah Geometri raksasa); momen.setting.pack(.tip) id/en |
| 19 | ganda | Titah Bawaan mengatur Film | [x] setTitahFilm dihapus dari Bawaan; reset.tip id/en tidak lagi menyebut film |
| 20 | mati | Sisa transisi UiHub/DiSini/HubNav/PanelTempat | [x] Dihapus: UiHub.mintaTab/ambilTab (+ blok basis), UiTempatClient.terdaftar/lihatDariHub, DiSini.aktif(), HubNav.adaKonteks/SUB_KITAB, Konteks.jangkar (6 pemanggil). PanelTempat.gambar/bingkai/awan sudah tidak ada |
| 21 | mati | Kode mati Pengaturan/HUD | [x] ChallengeOfferToast.draw + panggilan DuelHud dihapus; elemen HUD kosong sekte_tetua_laporan dan mandiri_seteru_judul tidak didaftarkan lagi (LaporanCard/KartuJudul tetap sebagai pengirim Kabar/TitleCard). Konstruktor SettingsPage(CultivationScreen) dan kunci host.lainnya sudah tidak ada |
