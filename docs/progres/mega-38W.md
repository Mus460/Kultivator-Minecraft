# mega-38W (wave W1, 38 core: rencana, jalan & pos, zones, markers, guide, chips, harness) — checkpoint

Scope: jadwal-38-39-40.md W1 row 38W + sec 5 note; design 38.B.2.2-4, 38.C.1, 38.C.6, 38.C.9-10, 38.D (no RencanaLama,
new worlds only), 38.T.14.3 row W. No Gradle. Stub list from W0: kota-W0.md.

## Checklist
- [x] 1 RencanaKotaBesar full (ring 12 + far cells r<=6, push 700/400, akhir() verdict ring/spacing, gates=simpul,
      zonaDistrik); Rencana +zonaDistrik/simpulSah defaults; ZonaRencana record; PutusanTanah.Sumber sampling public
- [x] 2 RencanaJalan (places from plans/GerbangPlan/SUMBER sets via isStructureChunk, Prim+spokes+cave paths, Chaikin,
      posts, perChunk index, sah()); JalanPlacement +`titik` (posts use kultivator:jalan, NOT rencana_tempat/jalan_titik:
      the plan needs the structure state); JalanStructure road mode (ubin/lentera/jembatan_<kelas>_<n>/tangga/dermaga)
      + post mode (lot, front to road). javac clean (scratchpad w38/jc.sh)
- [x] 3 Pos runtime: Jalan.init, PosAnchors (module "pos": publish site + TempatZones.daftar), Tonggak (discover,
      known edges in extra "jalan", JalanNet payload jalan_peta, adv tempat/peziarah_jalan at 10), KuilJalan (UseBlock,
      1 NB, Speed I 3 min, 10 min cooldown), Penyeberangan + RakitEntity (ride 4 s, fare 1 NB, far bank via noise),
      KemahBegal (spawnBand via Sibuk), JalanCommands (uji jalan cari/peta/cek/pos). javac clean
- [x] 4 ChunkGeneratorTempatMixin + kultivator.mixins.json line (TempatKeepOut.tolak with lazy verdict Sumber)
- [x] 5 TempatApi place index (tempatDi/dekat via verdict-filtered plans + runtime boxes + sites; arrival capital
      pusat/kota_tema/tiba/kampung/lonceng/pos; groundSpot inside plan = dasarY; spot/faktor providers daftarSpot/
      daftarFaktor with bounds; potongan/titik from loaded chunks), TempatNames (pos + jalan names), server ref in Spine
- [x] 6 TempatZones rewritten: verdict-filtered plan indexes (keep-out/aman/tempat), daftar(+jenis,kekuatan), sumber();
      tags tempat_berpenghuni (all 38 inhabited SETS incl. 46 kota_tema + pos_pemburu, all optional) and tempat_dirawat
      (structures; other modules' entries optional) written whole by gen_tempat_jalan.tag_tempat (regenerated)
- [x] 7 TempatCommands cek/kepadatan/ukur/lihat (DuniaCommands.TEMPLATES public + lihat(ctx,path)); JalanCommands; javac clean
- [x] 8 DuniaCommands CHIPS: 38.D.10 groups (tempat..ui) + hidup 38.N.8 + model 38.O.5 + tema 38.T.13 + penampilan 40.6
- [x] 9 DuniaMarkers: markers.png cells 23-31 are module T's, so 29 new kinds use icons 32-60 in a NEW atlas
      markers_tempat.png (128x64, cell = icon-32; ATLAS2_FIRST); markers.png byte-identical; preview checked.
      PetaScreen.icon (not owned) must pick the atlas -> needsFromOthers. Lang keys -> item 14
- [x] 10 PetaPainter.addOverlay(Overlay)/invalidate()/ink() (run once per bake after stamps); JalanNet.Peta +Denah
      (capital district boxes of known kota/kota_besar sites, Tonggak.denah, resent on SITE_DISCOVERED); client
      JalanPeta (roads by class + 3x3 district washes + wall line) + JalanClient receiver. javac clean
- [x] 11 GuideChapters CHAPTER_COUNT 20: bab 18 Kota Besar (8 steps), 19 Tempat (8), 20 Tema (10); DISPLAY_ORDER
      {0,1,12,17,18,19,2,...}; GATES qi_refining/null/null, past immortal_ascension. Checks = adv:tempat/* (list in
      needsFromOthers). Tonggak grants tempat/tonggak_tiga (3) + peziarah_jalan (10); both advancement JSONs written
- [x] 12 tools/gen_tempat_jalan.py: 44 NBT (ubin x8, lentera x2, jembatan 3x6, tangga x3, penyeberangan, tonggak a/b,
      kuil a/b, singgah, singgah_besar 4 lots, dagang, kemah a/b), pools, structures jalan/jalan + pos/*, sets in pack
      dunia_jalan (380301-380307), tags jalan/pos/boleh_di_tempat/tempat_dirawat(+pos)/tempat_berpenghuni(+pos), loot
      pos/kemah_begal. Previews checked (scratchpad w38/iso.py, p_*.png)
- [x] 13 cek_tempat.py: + family limits (titik/anchors per 38.C.10), tema salts parsed from Tema.java (48 incl.
      satellites), #kota_tema membership, tag coverage berpenghuni/dirawat; pos limits raised (titik 48, 0.45 MB:
      inns are 40x40 with rooms, deviation noted); kemah_begal/reruntuhan exempt from spawn rule. Clean except 38KS
      kota_besar/kumuh+luar "tata_letak without lots" (38KS in progress)
- [x] 14 lang tempat_inti (134 keys id/en identical, no dup elsewhere): jalan/tonggak/kuil/seberang/begal/uji texts,
      road classes + ends, post/road names (16 x 8 words), 32 marker kinds, adv tonggak_tiga/peziarah_jalan, kuil
      Sumber line. Scan of all 38W Java: 0 missing keys
- [x] 15 scratchpad final/wg/run_38.sh + WgCheck38.java + PlanCheck38.java (EXTRA=<classes dir> for pre-build runs).
      Run with w38/out: TplCheck jalan 32 / pos 12 / kota_besar 78 files 0 problems; WgCheck38 49 structs, 39 sets
      (18 of 38), tags berpenghuni 16 / dirawat 21 resolved, ALL OK; PlanCheck38 3 seeds PLAN OK
- [x] 16 KedaiTeh: no M2 registry exists, so RUMOUR_KINDS += kota_besar, kampung, gua (appended, old indexes kept) +
      siteKind cases; lang kultivator.kota.kabar.jenis.* in tempat_inti. UI coins (TownPages) -> needsFromOthers
- [x] 17 self-check: all touched files LF (none was CRLF in git), no client refs in main, no setBlock/LightningBolt/
      weather/time, no static ItemStack; javac (w38/jc.sh) clean for all 38W main+client files + DuniaCommands,
      DuniaMarkers, GuideChapters, KedaiTeh, PetaPainter, JalanPeta/JalanClient

## Langkah berikutnya
DONE (wave W1). F1: run generators gen_tempat_jalan + gen_dunia_peta_textures, then run_38.sh after B1.

## Butuh dari modul lain
- F1 (TownPages, not owned): rumour coins loop over KINDS: make it the 9 kinds of KedaiTeh.RUMOUR_KINDS (3 x 3 grid)
  and move `by = coinY + 28` to `coinY + 3 * 13 + 2`.
- 38DS ship `#kultivator:kampung` (structure tag of all kampung/* incl. klan) and `#kultivator:kompleks_sekte`; 38GU
  ship `#kultivator:gua` (38 caves) - used by KedaiTeh rumours and tempat_dirawat. Nobody but 38W writes
  tempat_berpenghuni / tempat_dirawat (gen_tempat_jalan.tag_tempat writes them whole; new set ids -> tell F1/38W).
- 38KS: structure kota_besar/kumuh + luar have no lots (cek_tempat "tata_letak without lots").
- F1 (PetaScreen, not owned): `icon(...)` must pick the atlas: `int i = Mth.clamp(index, 0, 63); Identifier atlas =
  i >= DuniaMarkers.ATLAS2_FIRST ? MARKERS_TEMPAT : MARKERS; int c = i % 32;` blit cell c from 128x64 atlas
  (`MARKERS_TEMPAT = Kultivator.id("textures/dunia/peta/markers_tempat.png")`). Until then new kinds show the unknown icon.
- Guide advancements (impossible-trigger, granted by owner): 38KS `tempat/{ibu_kota,tiga_layanan,papan_yamen,acara_ikut,
  teleport}`; 38EK `tempat/{lencana_1,lelang_agung_ikut,lelang_agung}`; 38ST/38KS `tempat/{takhta_bab3,penjaga_takhta}`;
  38DS `tempat/{kampung_tiba,kampung_ikrar,kompleks_tiba,tamu_sekte}`; 38GU `tempat/{murid_pertapa,pewaris_makam,
  sarang_bersih}`; 38PR `tempat/tukang_bantu` (jujur/tukang_sukarela exist); 38TK `tempat/tema_{pelabuhan,oasis,air,
  gunung,dagang,lintas_laut,ziarah,lentera_air,iblis,tebus}`.
- Guide texts: 38ST `kultivator.guide.chapter.18|19(.desc)` + `c18.*`/`c19.*` title/body (tempat_panduan); 38TK
  `chapter.20(.desc)` + `c20.*` (tempat_tema_panduan). Step ids are in GuideChapters (bab 18-20).

## Open problems
- Design icon numbers 23-49 collide with module T's cells 23-31 in markers.png; implemented as icons 32-60 in a 2nd
  atlas (see item 9). DESIGN text 38.D.12 / 38.T should say so at F3.
- Pos budget deviation (titik 48, 0.45 MB gzip instead of 24 / 0.4 MB): the inns are 40 x 40 lots with rooms.
