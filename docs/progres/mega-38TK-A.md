# mega-38TK-A (wave W2, Kota Air 38.T.5) — checkpoint

Scope: jadwal-38-39-40.md W2 row 38TK-P/O/A/G/I (A = air) + sec 5 note; design 38.T.5 (+38.T.2, 38.T.8-38.T.13 for air).
Owns: `dunia/tempat/tema/air/**`, `tools/gen_tempat_tema_air.py`, `structure/kota_tema/air/**`,
`worldgen/**/kota_tema/air*`, `data/kultivator/tempat/{dialog,quest,kabar}/tema_air*.json`,
`{permintaan,jadwal}/kota_tema_air*.json`, `loot_table/tema/air/**`, `advancement/tempat/tema_air_*.json`,
lang `tempat_tema_air.*`. No Gradle. Never edit inti files (`tema/*.java`, `tema/bersama/**`).

## Checklist
- [ ] 0. Study: Tema/TemaLayar/TemaKalender/TemaCommands/KotaTemaData/TemaNet/TemaItems/DagangItems/TemaMekanismeEntity,
      38 core APIs (TataLetak, tempat_lib.py, cek_tempat side codes/titik kinds, Syarat/Akibat/QuestTracker, HidupApi,
      Sibuk, NilaiBatu, quest/dialog data format), another 38 generator as pattern
- [x] 0. Study (session 2): kit, tempat_lib, kota-TK.md contract (TemaAnchors.daftar, TemaKota, TemaMekanisme, TemaSibuk)
- [x] 1. Generator `tools/gen_tempat_tema_air.py`: 9 districts done (session 1: taman_sastra, rumah_sutra, bukit_teh +
      kit; session 2: jembatan_kembar, danau_lentera, pasar_kanal, hunian_kanal (2 variants), gerbang_air, dermaga_perahu).
      Fixes: datar() keeps ox/oz, froglight 'hanging' strip, jembatan(air=) + gaya "shide", market canal z -20..-12,
      hunian back-water canal z 93..98, titik trimming `_hemat_titik`, slim foundations (beard_box, like kota_besar).
- [x] 2. Output: 68 templates, 64 pools, 9 tata_letak structures, 4 processors; gzip 1,173,836 < 1.2 MB budget.
      TplCheck kota_tema/air: files=68 problems=0. Preview scratchpad air_kota.png looks right (air_preview.py).
- [x] 3/4. Java DONE (18 files, standalone javac type-check in scratchpad airchk = 0 errors). Session 3 fixes: removed
      duplicate adv tema_air_lentera (inti owns tema_lentera_air, now via TemaCerita.lapor); TikusAir forces the raid at
      night for stage kejar / mission pencuri_kanal + misiSelesai; AirCerita syarat air_misi, akibat air_misi_selesai
      (antar_sutra), Air Suci from kuil_air basin at stage kain, air_anugerah allows tema_penenun_malam.
      Plan was: (files in tema/air/): AirKota (towns, frame lokal->dunia, state), JalurKanal (RUTE_POS mirror,
      tur, lomba, lentera lanes), AirTokoh (7), AirAnchors (TemaAnchors.daftar kinds), Perahu (pos/tur/sewa/lapak; rider
      input via ServerPlayer.getLastClientInput), RumahSutra (tema_sutra; loom rhythm = click on beat), PaviliunTeh
      (tema_teh, tea effects via Meditation/Qi providers, petik teh), PasanganBait (tema_bait, 30 couplets in lang),
      LenteraAir (+Arwah), LombaPerahuNaga, TikusAir, AirCerita (goal kinds/effects, anak jembatan), AirCommands, Air.init,
      entity behaviour (interact(Player,hand,Vec3), hurtServer for sampan)
- [x] 5+6. Writer scratchpad tka/air_data.py + tka/air_lang.py (run: python -I air_lang.py <repo>; checks every Java key,
      clashes) -> 29 dialogs tema_air_* (offers merged into greetings so services never hide), 5 quests (tema_air_benang, _teh_pertama, _bait_tua, _perahu_naga, _anak_jembatan;
      side quests renamed into tema_air_* glob, Java constants follow), kabar/tema_air.json 19, jadwal+permintaan
      kota_tema_air.json, 7 adv tema_air_*, lang tempat_tema_air 458 keys. Validator tka/cek_air.py: 0 bad in mine.
      Loot tables: none (town containers stay empty, like the capital). Quest chain tema_air_benang + 4 side quests, dialogs, kabar, jadwal, permintaan, loot, advancements
- [x] 6. Lang tempat_tema_air.{id_id,en_us} identical keys (458, % parity checked, no clash with other fragments)
- [x] 7. TplCheck kota_tema/air files=68 problems=0; cek_tempat: no air errors (only oasis + kota_tema budget, F2/inti)
- [x] 8. Hard-rule scan clean (no setBlock/LightningBolt/weather/client refs, no static ItemStacks, all LF); javac 0 errors

## Langkah berikutnya
DONE for W2 (session 3). Re-run data: `python -I tka/air_lang.py <repo>` then `python -I tka/cek_air.py <repo>` (scratchpad).
F2: compile; W3 D-TK1 may add voices/adegan for the chain ending and more couplets.

## Butuh dari modul lain
- (done by inti) air structures in #kultivator:tempat_dirawat / #kota_tema.
- 38W (optional): a "cuci" tool for washing points (now kerja:sapu nama=cuci, AirCerita reads that).
- F2/inti: structure/kota_tema gzip 5.69 MB > 5.5 MB folder budget (air alone 1.17 MB, within its 1.2 MB share).

## Open problems
(none yet)
