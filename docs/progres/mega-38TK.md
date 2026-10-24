# mega-38TK (wave W2, 38TK inti) — checkpoint

Scope: jadwal-38-39-40.md W2 row 38TK (inti) + sec 5 note; design 38.T.2, 38.T.10, 38.T.11, 38.T.13 (inti commands,
config), shared data, `tema/bersama/**`. Owns: `dunia/tempat/tema/*.java` root, `tema/bersama/**`, `tools/tempat_tema_lib.py`,
`tools/gen_tempat_tema_{satelit,data}.py`, shared tags/sets/`has_structure/kota_tema/**`, pack `dunia_kotatema` root,
`data/kultivator/tempat/dagang/**`, lang `tempat_tema.*`, `tempat_tema_panduan.*`. No Gradle. Theme dirs NOT touched.

## Checklist
- [x] 0 Read W0 tema code + tempat core; contract for theme agents written: `docs/progres/kota-TK.md`
- [x] 1 `PutusanTema`, `RencanaKotaTema` (4 rotation variants pelabuhan/gunung), `RencanaPosPemburu`, `KotaTemaNames`,
      `TemaKota` (lookups), `TemaAnchors` (dispatcher) — registration in KotaTema.init still TODO (step 3)
- [x] 2a TemaAnchors (situs/tiba/zona/reruntuhan/pos + daftar forwarders), TemaSibuk, TemaMekanisme, TemaKota (session 1)
- [x] 2b TemaSitus (taman_klasik/aula_emas/kolam_semedi_darah), bersama/TemaCerita (syarat/akibat/8 goal kinds, kunjungan)
- [x] 2c TemaKalender runner (berjalan, berjalanDi, berikutnya, forced windows, ACARA bus, FESTIVAL packets)
- [x] 3 TemaInti provider (temaDi, rupaKafilah, permintaan->Dagang, infami log, zonaPerjanjian, tolak bill + dimusuhi,
      camel strings / junk bodies) — needs Dagang.permintaan (step 4)
- [x] 4 bersama/Dagang (prices, drift, jenuh, cap, cost basis, buy-back, smuggling, bea, crates, book) + KontrakMuatan
      (board in chat, accept/deliver/fail, sky x1.3, misi muatan) + DagangLayar (tema_dagang hub + market mode, /kultivasi
      dagang beli|jual|segel|kontrak, PETI use) + TemaIrama (tema_irama engine). Data `tempat/dagang/**` still TODO (step 11)
- [x] 5 bersama/TanduEntity (arak polyline, sway, loop) + PintuAirEntity (05:00/21:00, buka/tutup, solid, splash)
- [x] 6 tema_dagang (DagangLayar) + tema_irama (TemaIrama) builders; TemaLayar.Dagang market keys added; `/kultivasi dagang`
- [x] 7 TemaUji: rencana putusan cari distrik lot cek acara dagang(harga|batas|jenuh|jual|simulasi) kontrak alur model fx kepadatan
- [x] 8 TemaMisi (9 kinds); vetoes via TemaSibuk; KotaTema.init wired (all inti inits). Advancement JSON -> step 11
- [x] 9 tools/tempat_tema_lib.py (palettes, styles, potong_distrik, pools/processors/structure writers, kanal, piles, walls) smoke-tested
- [x] 8b DuniaPacks.worldgen("kotatema") registered in KotaTema.init (nobody had it); lang dunia.pack.kotatema
- [x] 10 tools/gen_tempat_tema_satelit.py: reruntuhan_gurun jigsaw 7 NBT (gate, 2 towers, court stair, 3 buried rooms,
      12 mosaic murals, 4 rune pillars), pos_pemburu tata_letak 4 lots (palisade, tents, board, smithy, horses), loot
      tema/reruntuhan(+_1/2/3 by location), tema/pos_pemburu; structgen + lot rules pass; iso preview scratch tk/iso_nbt.py
- [x] 11 tools/gen_tempat_tema_data.py: 38 sets now (oasis 10 wait for 38TK-O), biome tags (putusan, gaya, has_structure =
      is_overworld), structure tags, pack.mcmeta, processors lapuk_ringan/utuh/iblis_lapuk, 22 advancements, dagang/{barang,jalur}.json
- [x] 11b TemaAnchors sends SPLINE lanes (jalur_perahu/kapal/lentera_air by jalur+urut, stasiun_lift pairs -> kabel)
- [x] 11c harness run_38.sh: TplCheck kota_tema 223 files 0 problems, WgCheck38 147 structures 95 sets ALL OK, PLAN OK
- [x] 12 lang: tempat_tema 299 keys, tempat_tema_panduan 22 keys (bab 20), identical, %% checked; source
      scratchpad `tk/lang_tk.py` (re-run: `python -I lang_tk.py <repo>`; merges, fails on clashes with other fragments)
- [x] 13 Review: APIs checked in mcsrc (CompoundTag Optional getters, AABB, PathNavigation, Mth, loot codecs, sounds),
      no client imports, all LF (byte scan), ItemStacks only at runtime, no setBlock/LightningBolt/weather

## Langkah berikutnya
DONE for W2 inti. F2: run theme gens, then gen_tempat_tema_satelit.py, then gen_tempat_tema_data.py; lang via
scratchpad tk/lang_tk.py only if keys change (JSON in repo is the source otherwise).

## Masalah terbuka
- cek_tempat: structure/kota_tema 5.53 MB > folder budget 5.5 MB already (oasis 12/40 templates); design 5 x 1.2 MB
  themes + satellites cannot fit 5.5 MB -> F2 decides (raise FOLDERS["kota_tema"] in cek_tempat.py or themes trim).

## Butuh dari modul lain
- 38EK/F2: KafilahJalan.hapus() must discard non-NPC lead bodies (themed camel/junk) like GerobakEntity.
- 38TV-U/D-TV: LayarBukuDagang market block `pasar` (beli/jual/segel rows, ops beli:ID:N jual:ID:N segel:ID) + laba.total.
- 38TV-F: CAHAYA previews with data uji=true (apa budha|suar|awan|kabut|riak|ombak); SPLINE kind `kabel` lanes.
- 38KS/38EK: capital/34-town market NPC calls DagangLayar.bukaPasar(player, townId, npcId, null); fence Opsi(true,0).
- Theme agents (kota-TK.md sec 7): TemaCerita.lapor, Dagang.bukaPasar/bea, KontrakMuatan.bukaPapan, TemaSitus.daftarSyarat,
  handlers for ruin/camp anchors (tema_oasis/pintu|pilar|sarang, kota_tema/mural_gua|temuan_reruntuhan|pemburu|papan_buronan).
- 38TK-P: Menara Tambat Langit routes (DuniaApi.routes registerDestination) are pelabuhan's.
