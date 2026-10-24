# mega-38TK-P (wave W2, tema pelabuhan) — checkpoint

Scope: jadwal-38-39-40.md W2 row 38TK-P + sec 5 note; design 38.T.3 (+38.T.8-38.T.13 rows for pelabuhan).
Owns: `dunia/tempat/tema/pelabuhan/**`, `tools/gen_tempat_tema_pelabuhan.py`, `structure/kota_tema/pelabuhan/**`,
`worldgen/**/kota_tema/pelabuhan*`, `data/kultivator/tempat/{dialog,quest,kabar}/tema_pelabuhan*.json`,
`{permintaan,jadwal}/kota_tema_pelabuhan*.json`, `loot_table/tema/pelabuhan/**`, `advancement/tempat/tema_pelabuhan_*.json`,
lang `tempat_tema_pelabuhan.*`. No Gradle. Inti files (KotaTema, TemaApi, TemaLayar, ...) read-only.

## Keputusan (tetap)
- Anchors khas pelabuhan pakai modul sendiri `pelabuhan/<x>` (DuniaAnchors 1 handler per modul; `kota_tema` milik inti):
  `pelabuhan/{tambatan,derek,suar,galangan,lintas_laut,tokoh,peran,perahu_sewa,pintu_dok,kuil,tambat_langit,gudang}`.
  Bersama tetap: `kota/situs` (situs=kota/kota_tema, tema=pelabuhan), `kota/alun|teh|papan|penjaga|kursi_pengembara`,
  `kotabesar/layanan|cakram|tukang`, `kota_tema/situs|zona|tiba` (dilayani inti).
- Generator: kanvas 96x96 per distrik (kota_besar_kit.Kanvas + potong, folder `kota_tema/pelabuhan`), laut PY=24 (tiang
  di bawah), lot dy -20; gaya `utara` lewat processor sendiri `kota_tema/pelabuhan/gaya_utara(_utuh)`; biome inline (tak
  tergantung tag inti). `kerja:jemur/tali` tidak ada di ALAT -> `kerja:rak` / `kerja:jala`.
- Tokoh sendiri (memory `tokoh`): lin_haichao, zhao_wanli, deng_ming, a_lan, gong_shumu, ibrahim, hei_sha, gao_fan, bibi_yu.

## Checklist
- [x] Study contracts (Tema, TemaLayar, KotaTemaData, TemaKalender, TemaCommands, entities, items, config, 38W tata_letak, tempat_lib, cek_tempat, 38ST quest data, anchors)
- [x] Generator `tools/gen_tempat_tema_pelabuhan.py` (9 districts, 36 lots + 7 wall pieces = 43 NBT, 53 lot slots, 1.19 MB) run;
      fixed pool ids (need `kultivator:` prefix); structgen + tempat_lib OK, cek_tempat 0 pelabuhan errors (only inti tag rows),
      TplCheck 43/0, WgCheck38 (other themes removed in private copy) ALL OK; preview scratchpad/tkp/pratinjau.py read
- [x] Worldgen JSON (structure, template_pool incl. `_utara`, processor_list lapuk/utuh/gaya_utara(_utuh)) written by generator
- [ ] Java plan (package `tema/pelabuhan/`, module anchor "pelabuhan"; API study done 00:40):
  - [x] PelabuhanKota (live towns, state helpers, hash/jam), PelabuhanTokoh (10 tokoh incl. pendeta_laut, hei_sha)
  - [x] KapalDagangEntity behaviour appended (deck AABB when moored, interact -> JadwalKapal.klikKapal)
  - [x] PelabuhanAnchors (12 kinds), PelabuhanLayar (header helper)
  - [x] JadwalKapal (pure clock schedule, storms, forced events, nakhoda body, live pointer, KAPAL_JADWAL) + derek state
  - [x] Suar.java (ApiSuar lamp) ; Galangan (orders, stage, keel pickup with name, livery KapalStyle NONE only)
  - [x] TokoPelabuhan (tema_kapal sheet: ship cargo / kiosks ikan, toko_seberang, bahan_bakar, perahu_sewa; lintas tab; jasa provider)
  - [ ] GalanganLayar (tema_galangan)
  - [x] LintasLaut (fare 40x(1+band/4), adegan tema_pelabuhan_lintas, Sibuk, SeaGate.enter(true), own veto + send <=2 disciples)
  - [x] PerahuSewa + PerahuNelayanEntity (client-authoritative rowing, leash 200, Ikan Roh 10 % via xp orb)
  - [x] HariDewiLaut (tandu procession + 4 bearers, prayer scene, regatta 2 laps on jalur_perahu "lomba", A-Lan practice)
  - [x] SerbuanBajakLaut (junk + waves via RogueCultivators.spawnBand ROLE_RAID, Sibuk enemy, win/lose, loot table roll)
  - [x] MenaraTambat (route per harbour, persisted deck pos, fuel desk) ; GalanganLayar ; TekaPelabuhan (dokumen, racik)
  - [x] PelabuhanCerita (syarat/akibat/tujuan, Hei Sha yield at 20 %, star nights, ceremony, Mutiara Fajar buy/serpent)
  - [x] PelabuhanCommands (`uji tema kapal ...`), calendar events kapal_datang kapal_pergi dewi_laut bajak_laut, Pelabuhan.init
  - [x] javac check `sh scratchpad/tkp/jc.sh` (real 26.3 jars + -sourcepath; no Gradle): 0 errors (01:40)
- [x] Data written by scratchpad/tkp/data_pelabuhan.py (+dp_ui, dp_cerita, dp_quest): 40 dialogs, 5 quests, 27 rumours,
  3 advancements, loot tema/pelabuhan/bajak_laut, jadwal kota_tema_pelabuhan (syahbandar/petugas_bea/kuli), permintaan kota_tema_pelabuhan (trade column parsed
  from DagangItems, contraband 0, shipbuilding needs)
- [x] Lang tempat_tema_pelabuhan.{id_id,en_us}.json 408 identical keys, no dup with other fragments
- [x] Verify: Java keys all defined (incl. dynamic), syarat/akibat/goal ids registered, javac 0 errors (session 3)
- [x] Final hard-rule review (no setBlock/LightningBolt/weather/client refs/static ItemStacks; LF) + report

## Sesi 3 (data + lang) — formats studied
- Quest/dialog/kabar formats (Quest.java, DialogNode, KabarTempat.Templat), base syarat/akibat, goal kinds; jadwal =
  JadwalData (`tempat/jadwal/kota_tema_pelabuhan.json`, peran syahbandar/petugas_bea/kuli not in JadwalTema built-ins);
  permintaan: inti generator does not write it; siblings write their own -> I write it (it shadows TemaApi fallback).
- Advancements granted by code: tema_pelabuhan_{lunas,lomba,bajak} (mine). Adegan cards: `kultivator.cerita.adegan.<id>`.
- Generator: scratchpad/tkp/data_pelabuhan.py writes all JSON + both lang files (keys from one table).
- [x] Java tweaks for data: syarat `pelabuhan_bintang_malam N`, akibat `pelabuhan_heisha_ramah` (friendly Duel Pedang
  route), goal `pelabuhan_heisha` also matches `duel tokoh:hei_sha`; Galangan sets flag `pelabuhan_lunas_diambil`;
  PelabuhanCommands TAHAP + `titipan`. Chain stages: redup catatan lapor lensa serbuan mutiara titipan duel nasib upacara
  (titipan = deadline fallback that hands the Mutiara Fajar; mutiara needs pearl + `pelabuhan_di_kota`).
- [x] scratchpad/tkp/dp_ui.py (UI strings + 10 tokoh names/titles). Side quests renamed to own prefix:
  tema_pelabuhan_{ikan_roh,tali_putus,bintang,lunas_pertama}. NEXT: dp_cerita.py (dialogs/quests/kabar), main writer.

## Langkah berikutnya
DONE (session 3). Source of all story data + lang is now the repo tool `tools/gen_tempat_tema_pelabuhan_cerita.py`
(scratchpad dp_*.py copies are stale). Arrival rumour split per origin (`tema_pelabuhan_kapal_tiba_<asal>`, JadwalKapal)
so the listener's client translates it. Final: 40 dialogs, 5 quests, 30 rumours, 411 lang keys; javac 0 errors.
Open: Hei Sha body is in-memory (restart during `duel` -> Deng's dialog re-summons; `nasib` has Lin/Deng fallbacks).

## Butuh dari modul lain
- 38TK inti: structure biomes stay INLINE (unbound biome tags crash registry load, seen in WgCheck38 with gunung/iblis
  using `#kultivator:has_structure/kota_tema/*` before inti writes them). Inti's tags must still list pelabuhan sets.
- 38TK inti: `#kultivator:tempat_dirawat` / `tempat_berpenghuni` rows for the 9 pelabuhan structures (cek_tempat).
- 38TK inti: DagangItems doc says inti writes `permintaan/kota_tema_<tema>.json`; per schedule 38TK-P wrote pelabuhan's
  (trade column parsed from DagangItems) -> inti must not write that file too.
- Finish agent: Gradle + mergeLang (new fragment tempat_tema_pelabuhan) + run_38.sh; new tool file
  `tools/gen_tempat_tema_pelabuhan_cerita.py` (theme-scoped, outside the literal glob).
- 38TV-F (optional): AdeganCerita recipes for tema_pelabuhan_{lintas,dewi,bajak,heisha,pelita} (now title-only cards).
- Voices (optional): tema.pelabuhan.{black_shark,lamp_lit,sea_crossing,pirates,goddess_day,ship_arrive} (guarded by exists).
