# mega-38GU (wave W1, 38.C.4 caves, 38.C.5 natural sites, sky links 38.A #20-28) — checkpoint

Scope: jadwal-38-39-40.md W1 row 38GU + sec 5 note; design 38.C.4, 38.C.5, 38.A #20-28, 38.0 rules 4/10/13/14/18,
38.D.9 `gua` config, 38.D.10 `uji gua`, 38.D.13 adv, 38.D.14 `tempat_gua`. No Gradle. New worlds only.
Owned old files: cultivation/Tribulation, trial/HeartDemons, technique/source/InheritanceTrials, purba/StarCalamity,
beast/SpiritBeasts, dunia/langit/{TreasureBirths,GreatMeteors,LangitItems} (one bounded hook each).
New: dunia/tempat/gua/** (stubs from W0), tools/gen_tempat_gua.py, structure/{gua,situs}/**, pack dunia_gua,
lang-src/tempat_gua.*, advancement tempat/{murid_pertapa,pewaris_makam,sarang_bersih,pembebas}.

## Checklist
- [x] 1 Read APIs: TempatApi (daftarFaktor keys kesengsaraan/iblis_hati/warisan/bintang/binatang_xp, bounds),
      TempatZones.daftar (dynamic box = Kehancuran-protected + optional safe), DuniaAnchors (module "gua", kind
      gua/situs publishes data.situs), KotaPintuEntity, HidupApi.tubuh (PERTAPA/SISA_JIWA/TETUA_IBLIS/TAWANAN roles
      exist), KultivatorNpc (markHostile, setCompanion, memory), Sibuk, Disciples (followVeto only on dim change ->
      also park followers STAY at entrance), markRealmFoe = technique/spec/TechniquePower, CeritaApi events wanted by
      38ST (ujian pertapa:meditasi|makam:<dao>:<tier>|cermin_hati, bebaskan tawanan; SISA_JIWA memory dao/tier),
      KaitModel readers (client; needs GuaClient + 1 line in TempatClient by F1), old files: Tribulation has
      addStrikeFactorProvider/addSuccessListener already; SpiritBeasts kill XP in onKill; StarCalamity package-private
      (needs public listener); InheritanceTrials roll/JadeItem; TreasureBirths.search; GreatMeteors.landed stones;
      LangitItems.ECLIPSE_DROP used in SkyEvents (not owned -> hook method + F1 one-line swap)
      Harness: scratchpad final/wg/run_38.sh (TplCheck+WgCheck38), w38/iso.py previews, javac pattern w38/jc.sh
- [x] 2 tools/gen_tempat_gua.py (done in a cut-off session, verified 19:40): 53 NBT (pertapa 5, makam t1-3 x10,
      binatang 3, iblis 8, urat 2, cermin 2, situs 3), pools, 11 structures, 11 sets cincin_tempat salts 380511-380523
      in pack dunia_gua, biome tags, structure tags gua/situs/gua_makam, loot chests/tempat/gua/* + gua/hadiah_bintang.
      Rerun clean (53 valid, lot rules ok); cek_tempat 0 errors, gua+situs gzip 634 KB. Previews scratchpad gu38/p_*.png
      read OK. Anchor contract (module "gua"): situs, masuk{nama,ranah,tier}, pertapa{ruang}, makam_penghalang{tier,
      px,pz}, makam_hati{px,pz,pyaw}, makam_kekuatan{kursi}, makam_pemahaman{lempeng}, makam_aula, sarang_bos{sarang},
      iblis_pintu{px,pz}, iblis_band, iblis_sel{sel x,y,z;..}, iblis_bos, urat_inti{jaga,harta}, cermin_kolam{cx,cz},
      petir_altar{r,bx,bz}, bintang_panggung{r}, mata_air{r}
- [x] 3 Java core (session 2: all written, javac clean): GuaConfig (gua: worldgen/aktif/penjagaKristal), GuaData (SavedData
      kultivator:tempat_gua: makam bits per player, pertapa pergi, urat harta pos, panggung pos, captives, cermin/mata
      air days, Batu Catatan), GuaRun+GuaRuns (own lifecycle; reruntuhan Runs is package-private; Frame reused),
      GuaTempat (factor providers kesengsaraan/iblis_hati/warisan/bintang/binatang_xp + SpiritSites extra sites, nearest
      anchor checks), GuaPayload (kartu/fx), Gua init (anchor handler "gua", Sibuk tempat.warisan/cermin, follow veto +
      park followers STAY, Tribulation listener+strike factor (no Tribulation edit needed), Aula reward on quest done,
      giok_warisan pointer), GuaApi real, GuaCommands (uji tempat_gua cari|ujian|penjaga|tikus|status, player /kultivasi cermin)
- [x] 4 Entities (session 2, all 6 files): PintuTebing (6 per 3x4 door), Sangkar (open lerp), LempengGlif (jenis/putar/nyala synced),
      BonekaPenjaga (bangkit, realm scale), PenjagaKristal (slam, wake), TikusRoh (zig-zag, harmless)
      Makam seal = reruntuhan REntities.GERBANG_SEGEL ColliderEntity (public API, rendered by R client)
- [x] 5 Runs (all written session 2): PertapaRun, MakamRun (penghalang/hati/kekuatan/pemahaman/aula), SarangBinatang, SarangIblis
      (pintu/band/sel/bos), GuaUratRoh, CerminHati, PuncakPetir, PanggungBintang, MataAirRoh, MasukRun (entry card)
- [x] 5b client GuaClient (written session 2) (KaitModel readers + GuaPayload receiver: banner/stamp) -> F1 adds 1 line in TempatClient
## Langkah berikutnya
DONE (session 2). All items checked. Only F1 work remains (see below), then in-game check of each cave/site.
Command literal is `uji tempat_gua` (`uji gua` belongs to module R RTests and ImmortalPerks).
Facts: 38ST owns pertapa/warisan/tawanan dialogs+quests; 38GU fires CeritaApi.peristiwa(p,"ujian","pertapa:meditasi"|
"makam:<dao>:<tier>"|"cermin_hati"), (p,"bebaskan","tawanan"); soul memory dao/tier (tier overrides NPC tier, harmless).

## Butuh dari modul lain
- F1: SkyEvents.java:280 `LangitItems.ECLIPSE_DROP` -> `LangitItems.eclipseDrop(slayer)` (one line; SkyEvents not mine)
- F1: TempatClient.init() one line `dev.kultivator.client.dunia.tempat.gua.GuaClient.init();` (KaitModel readers + GuaPayload)
- 38W: DuniaCommands.CHIPS: "tempat_gua cari <jenis>", "tempat_gua ujian <warisan|cermin|pertapa> [tahap]",
  "tempat_gua penjaga", "tempat_gua tikus [n]", "tempat_gua status"; player chip "cermin"
- 38MD/detail (optional): giok_warisan item model as compass range_dispatch (stack now carries LODESTONE_TRACKER);
  PenjagaKristal renderer may raise fists from PenjagaKristalEntity.hentakan(pt) (slam wind-up)
- 38ST: TikusRohEntity.lepas(level, pos, n) exists for quest tikus_dapur (stage jaga) if they want real rats

## Open problems
- 38.A #26 (Ascension name on Batu Catatan) not wired: abadi/Ascend is not mine; PuncakPetir records Tribulation successes only
- 38.A #20 "kamera lebar" at Panggung Bintang not done (EpicHooks not mine); extra reward roll is done
- Cermin "lulus -20 tekanan": a won trial already clears all pressure (HeartDemons.won), so it is subsumed
- Makam seal reuses module R's public gerbang_segel ColliderEntity (drawn by R client)
