# mega-38EK — 38EK lingkar uang (wave W1)

Scope: DESIGN 38.M (all) + 38.T.14.3 row EK + economic services of 38.B.3. Owned old files this wave:
`dunia/kota/{BalaiLelang,PapanMisi}.java`, `alchemy/AlchemyFurnace.java`, `tempa/Forging.java`; new files
`dunia/tempat/ekonomi/**`, item tags/loot/advancement `tempat/*` of the economy, `lang-src/tempat_ekonomi.*`.
Read-only: `Harga`, `NilaiBatu`, `Toko`, `Titipan`, everything else. No Gradle.

## Checklist
- [x] 0 read repo APIs (javap works for Fabric jars in ~/.gradle cache; mcsrc for MC)
- [x] 1 EkonomiConfig, EkonomiData (`tempat_ekonomi`), BukuKas (`tempat_kas`, screen buku_kas), KasKota, Lembar (sheet
      helpers), Barang (helpers + components `asal_toko`, `ekonomi`), EkonomiApi bodies (+misiSelesai/misiAktif/poinGilda)
- [x] 2 items: SenjataKota (28 `senjata/<b>_<m>`), EkonomiItems (4 `hidangan/*`, 4 `jubah_kultivator_*`, `surat_wesel`,
      4 `batu_mentah/*`, `lot_misteri`); Hidangan (effects + rumah_makan), Penginapan (penginapan)
- [x] 3 old-file hooks: [x] Forging (orderMaterials, buatStok, buatPesanan), [x] AlchemyFurnace.addFinishListener,
      [x] BalaiLelang.mulai(ProfilLelang) + public engine, [x] PapanMisi pangkat E/D/C + guild points + ledger
- [x] 4 [x] TokoSenjata (ids `s.<b>.<m>`, `p.<i>`, `z.<x>`), [x] PesananTempa, [x] GildaTempa (gilda_tempa) + GildaUjian helper
- [x] 5 RumahUang/SuratWesel/Gadai/Brankas (bank sheet)
- [x] 6 [x] JudiBatu (EV 0.88; `potong:<slot>|tangan`, rak `anda` = owns that stone) [x] Penginapan/Hidangan
      [x] PasarBinatang [x] Jasa (tabib/kuil/perpustakaan+arsip; Restu Kuil +5% MisiGilda roll) [x] GildaAlkemis (furnace listener = Tungku Gilda)
- [x] 7 [x] MisiGilda (12 kinds, board E-S/E-C/E-D, twists via `/kultivasi misi pilih <id> <0|1>`), [x] Lencana,
      [x] DaftarNagaMuda, [x] GildaKultivator (gilda_misi sheet + exam duel)
      Jasa tab: one `CeritaApi.daftarJasa` provider in Ekonomi.init (done).
- [x] 8 [x] ProfilLelang, [x] BalaiLelang public engine (mulai(level, anchor, profil), mulaiUji, kunci, lembarSesi,
      tawarLembar, titipKota/titipanKota/titipSisaKota/utangKota/bayarUtangKota; profile sessions run from server tick,
      commission -> KasKota, consigned lots via LelangAgung.lotTitipan/terjual/kembali/selesai/menang/segarkan)
      [x] LelangAgung (LotLelang/Penawar, buatLot from tabelLot, penawar, consignments SavedData, lelang screen,
      schedule d%7==5 12000-15000, jangkarDekat public for 38KS, tick fallback near JURU_LELANG) [x] data lelang/agung.json
- [x] 9 [x] BukuKas, [x] LapakSekte, [x] KafilahJalan, [x] GerobakEntity, [x] EkonomiReload (permintaan/lelang data)
      [x] KafilahSekte (lembar/aksi/hidup/hariBaru/tick bodies/bayar on login)
- [x] 10 EkonomiCommands (`uji ekonomi simulasi|judi|lapak|kafilah|kas|kurs|stok ulang|senjata|pesanan|lencana|misi|bank|lelang`,
      player `/kultivasi kas`, `/kultivasi misi pilih <id> <0|1>`)
- [x] 11 lang tempat_ekonomi (579 keys, id/en identical, generator scratchpad ek/genlang.py + lang1-4.py), item tags
      (swords/spears/axes + kultivator:senjata_gada, senjata_kota), 9 advancements tempat/*, demand tables
      tempat/permintaan/{kota_kota,kota_kota_besar,pos_dagang}.json, lot table tempat/lelang/agung.json
- [x] 11b tools/gen_tempat_ekonomi.py: 14 item textures 32x32 (textures/item/ekonomi/*), models, item defs; preview read OK
- [x] 12 self-review: no client refs, no static ItemStacks, all LF, MC names checked in mcsrc (EntityTypes.WOLF/FOX,
      WOLF_AMBIENT_BABY, BELL_RESONATE, getDoubleOr...), javac clean for 38EK files

- [x] 9b Ekonomi.init wiring (all inits, ticks, day change, JOIN/DISCONNECT, dialog Jasa provider)
- javac check: scratchpad ek/jc.sh <extra newer sources> (pass tempa/Forging, alchemy/AlchemyFurnace, tempat/cerita/*):
  0 errors in 38EK files (remaining errors are other modules' in-progress KotaNames/SosokGen).
## Langkah berikutnya
DONE (session 2). Session 3 (re-dispatch after F0): re-verified scope on disk (daftarJenisMisi, permintaan/*.json,
TemaApi.rupaKafilah/tubuhKafilah, public ProfilLelang+tabelLot, SavedData titipan/orders, entity.kultivator.gerobak
in tempat_ekonomi); nothing redone. Only F1 work left: Gradle build B1 and the hand-offs below.

## Butuh dari modul lain
- 38KS: forward anchors: `kotabesar/lapak_sekte` -> `LapakSekte.jangkarDekat(level, anchor, within)` / `jangkarLepas(anchor)`;
  `kotabesar/panggung acara=lelang` and `kotabesar/layanan layanan=lelang_agung` -> `LelangAgung.jangkarDekat(level, anchor,
  within)`; Record Office desk -> `BukuKas.bukaDiJangkar(player, pos)`. Capital service bodies need the roles the 38EK
  screens accept (JURU_LELANG, MENTERI, BANKIR, BANDAR_BATU/PEMOTONG_BATU, PEDAGANG_BINATANG, PEDAGANG_SENJATA,
  EMPU/PANDAI_PUSAKA, ALKEMIS, PETUGAS_GILDA/KETUA_GILDA/PENGUJI, TABIB, RAHIB, PUSTAKAWAN, PEMILIK_PENGINAPAN/PELAYAN/KOKI).
  Screen `jasa` is registered by 38EK `Jasa` (healer/temple/library); defence/festival prizes from `KasKota.ambil`;
  `KotaBesarApi.acaraAktif` may report `lelang` while `BalaiLelang.berjalan(site)`.
- 38W: add chips `ekonomi simulasi <pita> <hari>`, `ekonomi judi <n>`, `ekonomi lapak [hari]`, `ekonomi kafilah [permintaan]`,
  `ekonomi kas`, `ekonomi kurs`, `ekonomi stok ulang`, `ekonomi lelang [agung|kota]` to `DuniaCommands.CHIPS`.
- Duel owner / F1: `WeaponClass.of` should map `#kultivator:senjata_gada` to GADA (now gada counts as KAPAK via #minecraft:axes).
- 38MD: `item.kultivator.senjata.*` and `entity.kultivator.gerobak` live in `lang-src/tempat_ekonomi.*`; do not repeat them.
- 38TK: kinds via `EkonomiApi.daftarJenisMisi`, finish with `EkonomiApi.misiSelesai`, `misiAktif`, `poinGilda`; texts
  `kultivator.misi.<id>.judul|cerita|tahap.0|tahap.1`; Lelang Gelap = `BalaiLelang.mulai(level, anchor, new ProfilLelang(...,
  List.of("saingan","iblis_tokoh"), "gelap"))` + `data/kultivator/tempat/lelang/gelap.json`; demand tables
  `data/kultivator/tempat/permintaan/kota_tema_<tema>.json`.
- 39C (optional W2): `MandiriApi.setPasar/setGilda` may be backed by 38EK shops/board later.

## Masalah terbuka
- F1 javac shows 2 errors outside 38EK (38ST TempatDialog/Tokoh vs 40I KotaNames.person signature).
