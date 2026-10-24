# mega-38TK-I (wave W2, Kota Iblis 38.T.7) — checkpoint. STATUS: DONE (session 3)

Scope: jadwal row 38TK-P/O/A/G/I (theme iblis) + sec 5 note; design 38.T.7 (+38.T.2.4, 38.T.9 iblis entities, 38.T.10.2 rows,
38.T.13.2 commands). Owns: `dunia/tempat/tema/iblis/**`, `tools/gen_tempat_tema_iblis.py`, `structure/kota_tema/iblis/**`,
`worldgen/**/kota_tema/iblis*`, `data/kultivator/tempat/{dialog,quest,kabar}/tema_iblis*.json`,
`{permintaan,jadwal}/kota_tema_iblis*.json`, `loot_table/tema/iblis/**`, `advancement/tempat/tema_iblis_*.json`, lang
`tempat_tema_iblis.*`. New iblis-only files (no other owner): `tools/gen_tempat_tema_iblis_cerita.py`,
`data/kultivator/tempat/lelang/gelap.json`. No Gradle (javac check only: scratchpad/iblis/jc.sh).

## Checklist
- [x] s1 Study + generator `tools/gen_tempat_tema_iblis.py` (9 districts, 36 lots + 8 wall pieces = 44 NBT; pools x3 styles,
      processors, 9 structures). structgen/lot rules OK, TplCheck 0 problems, previews read. Anchors: gerbang_iblis terowongan
      (sisi luar|dalam) tuas_gerbang iblis_tokoh arena taruhan kandang_binatang perbendaharaan kitab_terlarang segel_menara
      kolam_semedi tawanan makelar pasar_gelap lelang_gelap kontrak gudang_minyak (+ camp: pemburu, papan_buronan).
- [x] s2 IblisData, IblisKota, TokohIblis, IblisMeter, Gerbang, ZonaPerjanjian, IblisAnchors, IblisLayar, ArenaDarah, Taruhan,
      KandangTawanan, PasarGelap, mechanism entity classes.
- [x] s3 fixes: ArenaDarah EntityTypes.RAVAGER, adv tempat/tema_juara_tangga_darah, tournament bouts (no fee/no cap);
      lelang_gelap anchor body = JURU_LELANG; A-Fu body owned by the pens (tokoh-tagged, cerita 10), her anchor empty.
- [x] s3 LelangGelap (setiap(9,4) 18000-20000, BalaiLelang.mulai profile 5/2/15 % "gelap", commission KasKota -> Kas Kota Iblis)
- [x] s3 MenaraTerlarang (floors by Gentar 10x(n-1) (+20 with flag iblis_akses_lantai), 4 scrolls + 9 forbidden slips with
      custom-data stain -> Infami +8 when learned, floor-9 SegelPintuEntity, Kitab Darah Senja stock 1); IblisKota.kanvas/dalam
- [x] s3 GulunganTerlarangItem.use (Bakar Darah, Langkah Hantu, Rantai Jiwa, Topeng Iblis Hati; cooldown; witness Infami)
- [x] s3 SarangKontrak (5/day, ONLY binatang/iblis_saingan/begal/pemburu, foes spawned at the mark, 2 running, 3 days;
      Shadow Oil barrels: hit = pukul on chapter 3, 6 broken -> IblisCerita.sabotase); entity clicks Tong/Tuas/Sel/Segel
- [x] s3 JamBerburu (jam_berburu 13000-23000, roll 0.6 halved by Gentar 30 and the mask, none at Gentar 60 / Fajar title,
      Sibuk enemy tema.berburu, answers /kultivasi tema_iblis bayar|lawan (DuniaCommands.player))
- [x] s3 PemburuLurus (camp + Han; interception Infami >= 40 once/5 days via Sibuk enemy tema.pemburu; dialog tema_iblis_cegat;
      Han yields at 10 %), BuronanIblis (3-6 fugitives, roam schedule, bodies + guards, claim, JenisMisi buronan_iblis B-S,
      PapanHidup board, Kontrak Kepala), PenyerbuanLurus (penyerbuan 6000-12000 hash 20+-5, 3 waves live/data, sides,
      results, ch5 lever / tunnel (GuaEntities.PINTU_TEBING) / Han duel), PestaDarah (follows BLOOD_ECLIPSE, Turnamen Darah)
- [x] s3 IblisCerita (iblis_* syarat/akibat/tujuan, chain start on first toll, Xue Yao 3-phase boss yields at 10 %, oil share,
      endings, side quests, DuelEndListeners), IblisCommands, Iblis.init (16 inits, one spread tick), schedule overrides
      (HidupApi.timpaJadwal tema.malam_pertandingan|lelang_gelap|penyerbuan|pesta_darah via IblisKota.timpa/lepasTimpa)
- [x] s3 tools/gen_tempat_tema_iblis_cerita.py: 661 lang keys (every Java key), 42 dialogs, 5 quests (rantai 16 stages,
      3 routes; topeng, gladiator_tua, kitab, anak_liar), 26 rumours, 9 advancements, 6 loot tables, jadwal, permintaan.
      Validators pass (clips, props, voices, roles, words read from IblisCerita, placeholders vs Java args, adv files of
      every grant, chest loot refs, duplicates vs other fragments). RUN: OK.
- [x] Final review: jc.sh 0 errors (52+ classes); no setBlock/LightningBolt/weather/time/client refs/static ItemStack in
      iblis/**; all new files LF; Gradle not run (finish agent).

## Langkah berikutnya
Nothing left in scope. Finish agent: run `python tools/gen_tempat_tema_iblis_cerita.py` again only after edits (it rewrites
the iblis dialogs/quests/kabar/loot/adv/jadwal/permintaan + lang fragment), mergeLang, Gradle B2.

## Butuh dari modul lain
- 38EK (Harga): a BASE price for `tema/kontrak_kepala` (~180 NB); until then the `gelap` lot rows naming it are skipped by
  LelangAgung.buatLot (value 0) and another row is drawn.
- 38TK inti (TemaSitus): `kolam_semedi_darah` is served for district `istana_darah`; the pool lot (anchor kota_tema/kolam_semedi,
  titik meditasi x6) is in district `menara_terlarang` lot (1,1). Either is fine in play (istana has 4 meditasi points);
  consider `menara_terlarang`.
- 38ST (LoreTempat/LoreIndeks): lore keys `kultivator.lore.kota_tema.iblis.{judul,1,2,3,sumber}` are in tempat_tema_iblis
  (oasis pattern); map site kind `kota_tema/iblis` -> lore id `kota_tema.iblis` (3 paragraphs + Sumber).
- 38TV-U (screens): `tema_kontrak` also runs in bounty mode for Han/camp hunters (rows jenis "buronan", extra keys tier,
  penerbit T; aktif rows: tempat T, sisa_hari -1 = no deadline); `tema_kitab_terlarang` rows carry `lantai` I, and GIOK holds
  the floor-9 row id `kitab_darah_senja`; ALASAN on locked rows.
- 38TV-F: festival packets acara `pesta_darah` (red lanterns), `penyerbuan`, `lelang_gelap`, `jam_berburu`, `malam_pertandingan`.
- 38GU: the Old Tunnel rubble reuses `GuaEntities.PINTU_TEBING` (buka/tutup/ikat API) outside a lair; check its renderer.
