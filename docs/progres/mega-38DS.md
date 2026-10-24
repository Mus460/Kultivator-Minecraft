# mega-38DS (wave W1) — kampung, desa klan, kompleks sekte, titik kota lama — checkpoint

Scope: jadwal-38-39-40.md W1 row 38DS + sec 5 note; design 38.C.2, 38.C.3, 38.C.7 (+ 38.D.9 kampung/kompleksSekte config,
38.D.10 uji kampung/kompleks, C38.DS1). Owns: `dunia/tempat/kampung/**`, `dunia/tempat/kompleks/**`, `desa/Tribute.java`,
`tools/gen_dunia_kota.py` (+ regenerated `structure/kota/**`), new `tools/gen_tempat_kampung.py`, `tools/gen_tempat_kompleks.py`,
`structure/{kampung,kompleks_sekte}/**` + worldgen, `resourcepacks/{dunia_kampung,dunia_kompleks}`, `lang-src/tempat_kampung.*`.
No Gradle. New worlds only.

## Facts found (code is truth)
- Gate template front = +z (south, steps), back = -z (hall/throne, rim all round, no rear door). Start piece at chunk MIN
  corner, rotation = Rotation.values()[WorldgenRandom(LegacyRandomSource(0)).setLargeFeatureSeed(seed,cx,cz).nextInt(4)].
  Ground = getFirstFreeHeight(box centre, WORLD_SURFACE_WG); template y -> ground - 1 + y. Biome check at box centre.
- Gate floors: kecil/sedang y4, besar high court y12; sizes 25x20x33, 37x26x45, 47x38x48.
- Multi-structure sets try each structure until one generates. Pool alias type `minecraft:direct` {alias,target} exists
  (26.3 PoolAliasBindings), jigsaw horizontal max_distance <= 128.
- TataLetak: piece y = dasarY - 5 + distrik.dy + lot.dy (template y4 = dasarY-1+dy); later pieces overwrite earlier ones.
- DuniaSites.Site has no data map -> compound sect id comes from the plan (position -> entry), not stored data.
- SekteNpc.LURUS/NETRAL/IBLIS = aliran 0/1/2; Sikap via DiplomasiService.stance.

## Decisions
- Compound frame: rotation = gate rotation; local +z points to the gate. Origin O = gate centre + rot(0, -(dalam/2+8)).
  District centres local z: halaman -72, dalam -216, puncak -360 (besar) / -192 (sedang, 3x2 lots). dy 0 / +10 / +18.
  District ids `<aliran>_<distrik>`, distrik in halaman_kecil | halaman | dalam | puncak_sedang | puncak; placement distrik
  matches by prefix of the part after the aliran. Sets kompleks_sekte/{halaman,dalam,puncak} salt 380201-380203.
- Upper district: stair piece `tangga_<naik>` (9 wide, dx -4) + terrace walls `tebing_<naik>_{sisi,tengah}` (48x3) at the
  district's south edge with dy = -naik, listed after the lots. JARAK_GERBANG 12 + no keep-out margin on the court's
  front so the gate start (chunk middle) is never refused by TempatKeepOut.
- Compound lots: one base NBT (lurus look, unique block per role) + processor per aliran (SWAP maps, props kept per
  family); alignment-only lots (slots A/B/C court, D/E/F inner) per aliran. Back wall of halaman_kecil = 3 pieces at
  dz -75. Kampung pieces built per style (stilts/oasis/terraces differ).
  Kampung size pools via pool_aliases: `kampung/<gaya>/bangunan` -> `bangunan_<ukuran>`.

## Plan / checklist
- [x] RotasiGerbang.java, KompleksConfig.java (compound plan helpers + config section)
- [x] A kompleks/RencanaKompleks.java (plan per GerbangPlan entry, verdict, zones, safe court; registered in D)
- [x] B tools/gen_tempat_kompleks.py + own kit copy tools/tempat_ds_kit.py: 47 lots (dedupe), 3 aliran processors,
      120 pools, 24 structures `kompleks_sekte/<aliran>_<distrik>`, sets halaman/dalam/puncak in pack dunia_kompleks,
      tags biome/kompleks_sekte/semua + structure/kompleks_sekte; gzip 817 KB; cek_tempat clean for kompleks.
      Anchors used: kompleks/{situs,gerbang,aula,arena,pasar,dalam,puncak,tetua,dermaga_awan}. Preview tool
      scratchpad ds38/iso.py (top|iso).
- [x] C tools/gen_tempat_kampung.py: 7 styles x (pusat, 5 streets + ujung, rumah kecil/sedang/besar, ladang, sawah,
      lumbung|gudang_ikan, kandang, kolam_ikan, kincir_air, pasar_desa, rumah_tabib, bengkel_pandai, balai_desa,
      sekolah_desa, makam_desa, menara_pengawas) + klan canvas (9 lots chained by unique jigsaw edges + 2 fields, 4 styles
      via pool_aliases + tl.GAYA klan processors). 172 templates, gzip 503 KB. Structures kampung/<ukuran>_<gaya> (21) +
      kampung/klan_<gaya> (4); size pools by pool_aliases bangunan -> bangunan_<ukuran>; villagers (VillagerData type per
      style) + animals as template entities; sets kampung/{dusun,desa,desa_besar,klan} 380101-4 (cincin_tempat, exclusion
      minecraft:villages) in pack dunia_kampung; tags structure/kampung (+ /dusun /desa /desa_besar /klan),
      biome/has_structure/kampung/<gaya> + klan_<gaya>. cek_tempat: 0 errors. Anchors: kampung/{situs,lonceng,tetua,
      pasar,tabib,pandai,balai} + klan kampung/{situs(data situs=kampung/klan),lonceng,patriark,tuan_muda,tetua_klan}.
      NOTE 26.3 BlockState JSON keys are "id"/"properties" (fixed in both processors).
- [ ] D Java kompleks runtime (session 3 plan): Kompleks (init/config reload/pack/registry/spot/anchors/commands),
      KompleksAnchors (situs publish sekte/kompleks, spots, gerbang/dalam/arena/pasar/puncak behaviour), KompleksSikap
      (stance per visit: sambut/peringatan/standing -2/dalam -1 per 30 s/PERANG hostile + markRealmFoe), TandingTempat
      (friendly bouts: DuelistRegistry.override + DuelChallenges.startDirect, DuelEndListeners -> standing +1/day,
      clan patriarch/young master results), PasarSekte (chat-card market, 6 wares per aliran, stance gate, -10 % allies,
      NilaiBatu pay), Rupa (sect looks on resident bodies), KompleksCommands (cari/rotasi/sikap + player chips)
      [x] already on disk: KompleksApi (+spot), KompleksData, KompleksConfig, RencanaKompleks, RotasiGerbang
      [x] session 3 written: Kartu (chat cards+chips), TandingTempat, KompleksSikap, PasarSekte, Kompleks (init),
          KompleksAnchors (+uniforms, body cap), KompleksCommands (uji daftar/cari/rotasi/sikap; player tanding/pasar/beli)
          -> D done except lang keys (H) and javac (I)
- [x] E Java kampung runtime (session 3): Kampung, KampungConfig, KampungNames, KampungData, KampungAnchors (site,
      TempatZones safe box from structure start), FestivalPanen, AnakBerbakat, PasarDesa, Klan (young master card+duel,
      patriarch tea/duel, pledge veto, tribute bonus), KampungApi, KampungCommands (uji cari/klan/panen/anak/daftar;
      player tantang/patriark/pasar/beli)
- [x] F desa/Tribute: BonusProvider/addBonusProvider (called in pay), PledgeVeto/addPledgeVeto/vetoIkrar, Ikrar+ikrarDi
      (LF kept). F1 must call Tribute.vetoIkrar from Pledge.refusal (see Butuh)
- [x] G gen_dunia_kota.py: section "life points" (titik_hidup in save(): kasur from beds, kursi from chair() + square
      benches, kerja:<alat> in front of work blocks, pintu above building connectors, jalan nodes on streets/bridges,
      anchor-kind points jaga/obrol/kursi/lapak/tiba/penonton/kerja:*, dupa at burners; berdiri() stand check, <=96).
      Regenerated: 220/222 templates gain 804 points; blocks/palette/entities identical to HEAD (scratchpad
      ds38/kota_cmp.py); 36 variant street/bridge/shophouse templates get their generator height (11->14, stale in
      HEAD). TplCheck kota 222 / kampung 172 / kompleks 47: 0 problems; cek_tempat 0 errors. Patch helper
      scratchpad ds38/patch_kota.py (idempotent).
- [x] H lang tempat_kampung.{id_id,en_us}: 181 keys, identical, no dup vs other fragments, %% checked; writer
      scratchpad ds38/lang_ds.py (re-run: python lang_ds.py <repo>)
- [x] I javac (scratchpad ds38/jc.sh, vs F0 classes + sourcepath): 0 errors for kampung/**, kompleks/**, Tribute.
      All my files LF. Harness: TplCheck kota 222 / kampung 172 / kompleks_sekte 47 = 0 problems; WgCheck38 with my
      classes (EXTRA) and packs incl. dunia_kampung + dunia_kompleks, gua/situs data removed from the copy (see Butuh):
      98 structures, 46 sets, ALL OK (25 kampung + 24 kompleks structures, sets kampung/* Cincin n=7/7/7/4,
      kompleks_sekte/* Rencana n=9/3/12). Story bridges: Akibat "tanding_klan" (Klan) and "tanding_sekte" (Kompleks).

## Langkah berikutnya
DONE for W1. (W3 D-DS: walk 38.E.4 rows for villages/compounds in game, preview PNGs, tune prices.)

## Butuh dari modul lain
- F1 (desa/Pledge.java, unowned): in Pledge.onPlaced, after the "taken" check:
  `if (why == null) { why = Tribute.vetoIkrar(player, level, bell); }` (clan pledge gate 38.C.2.3).
- 38ST: clan dialogs use effect `tanding_klan` instead of `mulai_duel` for TUAN_MUDA / PATRIARK_KLAN (KotaDuel
  refuses those roles: profile only for PENGEMBARA/SAINGAN/PENJAGA/ABADI); sect disciple dialogs may add a choice
  with `tanding_sekte` (friends only). Flags set by 38DS on the young master's bout: klan_hormat / klan_meremehkan.
- 38NL: PendudukGen.kompleks(kekuatan) gets TempatInfo.kekuatan = 3 for every compound (TempatApi.kekuatanRencana);
  use the band instead: KompleksApi.kompleksSitus(site).entry().band() -> 40/80/120 (38.C.3.3).
- 38W (TempatApi.kekuatanRencana): compounds could answer KompleksApi.kekuatan (sect tier - 1).
- 38GU / F1: processor_list gua/lapuk uses BlockState key "Name" (26.3 needs "id"/"properties") -> WgCheck38
  registry load fails ("Unbound gua/utuh", situs pools); fix before run_38.sh.
- F1: run gen_tempat_kampung.py, gen_tempat_kompleks.py, gen_dunia_kota.py (deterministic, already run), cek_tempat.
