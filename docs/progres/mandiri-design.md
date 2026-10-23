# Progres: desain 39 Murid Mandiri

READ-ONLY kecuali file ini dan docs/rancangan/39-murid-mandiri.md (LF). Tanpa Gradle.

- [x] Fakta kota/38/ekspedisi (Explore 1): layanan kota package-private + ServerPlayer; kontrak 38 (TempatApi, HidupApi,
      EkonomiApi, KotaBesarApi, CeritaApi) semua ServerPlayer; Lapak/Kafilah Sekte ada; RencanaJalan belum ada.
- [x] Fakta sekte 36/kerajinan/UI (Explore 2): tanpa scheduler pusat; addTick20 + setActivity; DiscipleScreen
      MAX_EXTRA_TABS=2 penuh; hub MURID 2 kartu; Sibuk hanya pemain; feed/teach/breakthrough/chooseDao butuh master.
- [x] Baca kode murid: XP (distribute bekerja tanpa tubuh), breakthroughBlocker (bt_offline/bt_master_far 16 blok),
      tribulasi deterministik (doomed bila nyawa <= total share), redCap (akar Merah di Inti Emas Puncak), SectRanks
      (slot teknik 2/3/5, promote butuh master, elderTeaches butuh master online), Paviliun hanya buku setoran guru,
      Dewa offer dipilih guru, NatalTreasure bahan dari master, ACT id: 25-27 (37), 32 (Upacara), 38 klip 79-107.
- [x] Penghalang menuju Abadi Sejati ditentukan (lihat 39.1.3 di dokumen).
- [x] Permintaan tambahan koordinator: belanja ke kota detail (39.6) - masuk rancangan.
- [x] Tulis dokumen bagian 1 (39.0-39.4)
- [x] Tulis dokumen bagian 2 (39.5-39.7)
- [x] Tulis dokumen bagian 3 (39.8-39.13)
- [x] Cek LF (0 CR), konsistensi pemilik (MENGAJAR 39B, Duties/Keepers overload 39D), ringkasan balasan

Keputusan kunci: paket dev.kultivator.sekte.mandiri (modul sekte ke-11 id `mandiri`); aktivitas 40-55; bab panduan 20 (c20.*);
tab ketiga layar murid (MAX_EXTRA_TABS 3); Role.JELMAAN (tubuh kota tidak disimpan); file jembatan paket untuk akses
package-private (KebunMandiri, PaviliunMandiri, HubunganMandiri, KontribusiMurid); Duties/Keepers/NatalTreasure disunting aditif oleh 39D;
terobosan off-screen diantrekan sampai tubuh dimuat; dompet NB abstrak + tas 9 slot di extra.mandiri.

STATUS: SELESAI (2026-10-23). Dokumen 1535 baris, LF. Langkah berikut: tidak ada (menunggu persetujuan skalaXp 1,5 dan bolehGugur dari pengguna).

## Tambahan 39.F Perseteruan antar-sekte (permintaan koordinator)
- [x] Baca diplomasi D (Diplomasi, Simulasi, Perang, AnggotaSekte, Sikap, standing), AntarSekte T, DuelKehormatan R, DuelChallenges
- [x] Tulis 39.F (tokoh murid sekte NPC, insiden, tangga eskalasi, peran guru, adegan + suara, anggaran/config/uji, kepemilikan)
- [x] Cek LF, ringkasan
STATUS 39.F: SELESAI. Modul keenam kecil 39F (tanpa berkas lama; jembatan baru DiplomasiMandiri). Menunggu persetujuan bolehPerang.
