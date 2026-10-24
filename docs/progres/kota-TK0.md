# kota-TK0 — Kontrak tema untuk agen W2 (38TK inti, 38TK-P/O/A/G/I, 38TV-M/F/U)

Ditulis 38TK-0 (W0). Kode adalah kebenaran; berkas ini merangkum API yang sudah ada di disk. Semua di
`src/main/java/dev/kultivator/dunia/tempat/tema/**` kecuali disebut lain. Baris LF. Tanpa kompatibilitas dunia lama.

## 1. Pemilik berkas (W2)

| Berkas | Pemilik W2 | Catatan |
|---|---|---|
| `KotaTema`, `Tema`, `TemaApi`, `TemaConfig`, `TemaEntities`, `TemaItems`, `TemaNet`, `TemaCommands`, `TemaKalender`, `KotaTemaData` | 38TK inti | agen tema TIDAK menyunting; minta lewat `kota-TK.md` |
| `bersama/**` (`TemaMekanismeEntity`, `TemaAnimasi`, `TemaLayar`, `DagangItems`, `PetiDagangItem`, `TanduEntity`, `PintuAirEntity`) | 38TK inti | |
| `pelabuhan/**` (+ `Pelabuhan.init`) | 38TK-P | 6 entitas |
| `oasis/**` (+ `Oasis.init`) | 38TK-O | 7 entitas (termasuk unta, kalajengking) |
| `air/**` (+ `Air.init`) | 38TK-A | 3 entitas |
| `gunung/**` (+ `Gunung.init`) | 38TK-G | 9 entitas (termasuk kera) |
| `iblis/**` (+ `Iblis.init`, `GulunganTerlarangItem`) | 38TK-I | 6 entitas |
| `client/dunia/tempat/tema/TemaClient.java` | 38TV-F | penerima `tema_fx`, `terakhir(jenis, situsId)` |
| `client/dunia/tempat/tema/model/TemaModelClient.java` | 38TV-M | W0: NoopRenderer untuk semua tipe; ganti per tipe |
| `client/tempat/ui/tema/TemaUiClient.java` | 38TV-U | W0 kosong; daftar layar via `UiTempatClient.daftar` |
| `lang-src/tempat_tema.{id_id,en_us}.json` | 38TK inti | nama item/entitas/perintah (96 kunci); agen tema pakai fragmen `tempat_tema_<tema>.*` |

## 2. Init

`KotaTema.init()` (dipanggil `Tempat.init()` 38W, sebelum `ModItems.init()`): `TemaConfig.muat()`, `TemaItems.init()`
(+`DagangItems.init()`), `TemaEntities.init()` (atribut makhluk), `TemaNet.init()`, `TemaCommands.init()`, lalu
`pelabuhan.Pelabuhan.init()`, `oasis.Oasis.init()`, `air.Air.init()`, `gunung.Gunung.init()`, `iblis.Iblis.init()`.
Klien: `TempatClient.init()` (38W) → `TemaClient.init()` → `TemaModelClient.init()`, `TemaUiClient.init()`.

## 3. API untuk agen tema (jangan sunting kelas inti)

- **Keadaan:** `KotaTemaData.get(server).bagian("<tema>")` (CompoundTag hidup) / `.kota("<tema>", situsId)` per kota;
  panggil `.ubah()` setelah mengubah. Infami global `infami(uuid)`/`setInfami`, Gentar `gentar(uuid, situsId)`/`setGentar`
  (hanya inti dan 38TK-I yang menulis meter; pihak luar lewat `TemaApi.tambahInfami`).
- **Perintah uji:** `TemaCommands.daftar("<tema>", () -> Commands.literal("kapal")...)` → `/kultivasi uji tema kapal`.
  Kata inti: `rencana putusan cari distrik lot cek acara dagang kontrak alur model fx kepadatan`. Kata tema (38.T.13.2):
  P `kapal`; O `unta`; A `perahu`; G `lift`; I `arena taruhan infami gentar buronan pemburu`.
- **Acara:** `TemaKalender.daftar(tema, id, dari, sampai, pemilihHari, pelaksana)`; pemilih `selalu()`, `setiap(mod, sisa)`,
  `rentang(mod, a, b)`, `bulan(MoonPhase)`, `peluang(p, salt)`, `dan(a, b)`; `Pelaksana.mulai/tick/akhir`. Id acara sama
  dengan `uji tema acara` 38.T.13.2: P `kapal_datang kapal_pergi dewi_laut bajak_laut`; O `kafilah_agung badai_pasir
  tari_putar`; A `lentera_air perahu_naga tikus_air`; G `ziarah cahaya_budha longsor lift_macet`; I `pesta_darah
  lelang_gelap penyerbuan jam_berburu`. `TemaKalender.paksa(level, situsId, tema, id, mulai)` untuk uji dan quest.
  Hari = `TemaKalender.hari(level)` (jam overworld / 24000). Tidak pernah mengubah cuaca/waktu/tick-rate/blok.
- **Layar:** `TemaLayar.daftar(layar, pembangun, penangan)` (hub: `daftarHub`), `TemaLayar.buka(player, layar, npcId)`.
  Kunci lembar = kelas bersarang `TemaLayar.<Layar>` + kunci umum `LembarKunci`. Pembangun per layar ada di javadoc
  `TemaLayar` (20 layar 38.T.13.3).
- **Efek klien:** `TemaNet.kirim(player, jenis, situsId, data)` / `kirimSekitar(level, pusat, r, ...)`; jenis `BADAI`,
  `FESTIVAL`, `SPLINE`, `CAHAYA`, `KAPAL_JADWAL`; kunci data di javadoc `TemaNet`; maks 8 KB (`muat`).
- **Entitas:** semua turunan `bersama.TemaMekanismeEntity` (noSave, kebal, tanpa gravitasi): `ikat(situsId, jangkar)`,
  `setVarian`, `setKeadaan` (cap waktu mulai otomatis), `setNilai`, `pindah(pos, yaw)` (satu langkah spline); kenop
  `padat()`, `bisaDipilih()`, `kursi()`. Konstanta `VARIAN_*`/`KEADAAN_*` per kelas dan arti `nilai()` di javadoc kelas.
  Makhluk (`UntaKafilahEntity`, `KalajengkingRajaEntity`, `KeraGunungEntity`) memakai `TemaAnimasi` yang sama + `ikat(situsId)`.
  Agen tema boleh menambah perilaku/konstanta di kelasnya sendiri (tambah di akhir), tidak mengubah tipe di
  `TemaEntities` (ukuran, jangkauan): minta ke inti.
- **Item:** `TemaItems.<NAMA>` (44 `tema/*`), `DagangItems.<NAMA>` (17 `dagang/*` + `PETI`), tabel 38.T.10.1 di
  `DagangItems.barang()` (asal, harga dasar NB, permintaan per pasar `PASAR_*`). Perilaku pakai item = event di paket tema
  sendiri (mis. `UseItemCallback`); properti item diubah hanya oleh inti. Isi peti: komponen `PetiDagangItem.ISI`.
- **Config:** `TemaConfig.inti()/pelabuhan()/oasis()/air()/gunung()/iblis()`, `TemaConfig.aktif(tema)`, `worldgen(tema)`,
  `peluangSel(tema)`, `ukuranSel(tema)`. Saklar hanya menghentikan perilaku.
- **Data tetap:** `Tema.<X>.distrik()` (id, x, z, salt, laut), `jenisSitus()`, `idRencana()`, `prioritas()`, `warna()`,
  `Tema.SALT_RERUNTUHAN_GURUN` 380451, `SALT_POS_PEMBURU` 380452.
- **Untuk modul inti 38 (W1):** `TemaApi.temaDi`, `rupaKafilah` (`RUPA_GEROBAK|UNTA|KAPAL`), `permintaan`, `infami`,
  `tambahInfami`, `gentar`, `zonaPerjanjian`, `tolakTagihanIblis` (38PR), `tubuhKafilah` (38EK). Semua aman tanpa
  penyedia; inti memasang `TemaApi.pasang(Penyedia)` di W2.

## 4. Daftar entitas (33, `kultivator:<id>`)

P: `kapal_dagang kapal_bajak_laut perahu_nelayan api_suar derek_dermaga kapal_galangan`.
O: `timba_sumur temuan_pasir pintu_pasir pilar_rune batu_ukur_air unta_kafilah kalajengking_raja`.
A: `perahu_kanal perahu_naga alat_tenun`. G: `kereta_gantung katrol roda_katrol batu_runtuh herba_tebing palang
tali_turun jerat kera_gunung`. I: `gerbang_tengkorak pintu_arena sel_tawanan segel_pintu tong_minyak tuas_gerbang`.
Bersama: `tandu pintu_air`. (`tong_minyak`, `tuas_gerbang`, `pintu_pasir`, `batu_ukur_air` dari teks quest 38.T.3-7.)

## 5. Tidak dibuat di W0 (milik W2)

`RencanaKotaTema`, `PutusanTema`, `KotaTemaNames`, `TemaAnchors`, `TemaSitus`, `TemaSibuk`, `Dagang`, `KontrakMuatan`,
`TemaCerita`, generator Python tema, data JSON, NBT, model/tekstur, efek, layar. Senjata unik quest (Gigi Hiu, Ekor Angin,
Palu Darah Senja, Kipas Senja) dan giok terlarang dibuat dari sistem artefak/giok yang ada, bukan item baru.
Sibuk = `dev.kultivator.util.Sibuk` (bukan `musuh/Sibuk`; catatan 38.T.0 butir 4 salah).
