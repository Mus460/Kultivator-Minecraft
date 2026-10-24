# mega-38MD (wave W1) — 38.O models, textures, gear bands 16-47, props 1-40

Scope: jadwal-38-39-40.md W1 row 38MD + sec 5 note; design 38.O (minus 40.7.4 transfers), 38.T.8.2 non-body items.
Owns: `client/dunia/kota/KotaGear.java`, `textures/dunia/kota/entity/perlengkapan.png`, new `client/dunia/tempat/model/**`,
`tools/{gen_tempat_models,gen_tempat_gear,gen_tempat_textures,tempat_iso}.py`, assets `**/tempat/**`, `lang-src/tempat_model.*`.
Must not touch: KotaGearLayer, NpcRenderer, faces/scale/sleep (40). No Gradle.

## Checklist
- [x] Study: KotaGear, KotaGearLayer, gen_dunia_kota_gear.py, png.py, PropId, TempatModelClient, entity stubs
- [x] tools/gen_tempat_gear.py: atlas 64x1536 written; bands 0-15 verified equal to git HEAD pixels; previews read
      (scratchpad md38/perlengkapan_16_31_x8.png, _32_47_x8.png: all 32 materials distinct and readable)
- [x] KotaGear.java: kinds 21-60 (GUAN_GIOK..TUDUNG), BAND_* 16-47, mesh38 + helpers, 64x1536 layers, kindOf for every
      38 role (NONE where 40.4.1 has no headwear), timpa() override, nama/dariNama, SosokGear.daftarTopi(kind, menutup)
      javac check OK (scratchpad md38/jc.sh with a SosokGear stub; LF kept)
- [x] tools/gen_tempat_models.py: props 1-40 + batu_mentah_s/l/xl + belah_kiri/kanan (45 models, 42 material tex),
      28 weapons senjata/<bentuk>_<mutu> (items/models/textures), 10 entity band textures textures/entity/tempat/*.png
- [x] tools/tempat_iso.py: z-buffered textured iso sheets; read iso_props_1-3 + iso_senjata (fixed chessboard uv,
      umbrella octagon, iron-fan ribs)
- [x] tools/gen_tempat_textures.py -> textures/dunia/tempat/hias.png 128x128 (28 cells: lantern papers, bamboo, gold,
      bronze, wood, flame, silk, awning, cloth, tassel, 3 smoke, star, 8 firework frames, jade orb, spray, rim, cloud);
      glyph + crystal live in entity textures. Fireworks/spray redrawn (session 2), preview md38/hias_x4.png read OK
- [x] HiasQuads.java (custom-geometry quad buffer for decorations)
- [x] Entity renderers (client/dunia/tempat/model): PenjagaKristal (+PancarLayer glow), BonekaPenjaga, TikusRoh (glow eyes),
      BendaRenderer base, GerbangKota (panel tiles sized by hook), Gerobak (rolling wheels, 3 loads), Rakit, Sangkar,
      LempengGlif (6 glyph cells, glow), PintuTebing, PapanHidup<T> (generic; 38UI registers), KaitModel hooks
      (owners set readers), emissive textures *_pancar.png; TempatModelClient wired (uncompiled yet)
- [x] HiasanDunia.java: client API pasang/festival/hapus/uji + COLLECT_SUBMITS drawing of all 38.O.3 kinds (street +
      sky lanterns, fireworks, armillary, water wheel, banner = KainKota role, awning, incense); budgets per tier
- [x] RakSenjata.java (weapon racks, real SenjataKota item models, uji 28 weapons) + HiasPerintah (/hiasan
      festival|senjata|hapus|info, client only); wired in TempatModelClient
- [x] javac (md38/jc.sh + SenjataKota.java source): all tempat/model + KotaGear compile clean (SosokGear stub = C40.K2)
- [x] lang-src/tempat_model.{id_id,en_us}.json: 4 identical keys (kultivator.tempat_model.perintah.*)
- [x] Run generators (session 2): gen_tempat_models 45 props/28 weapons/13 entity tex, errors 0; gen_tempat_gear
      64x1536, rows of HEAD 64x512 identical (re-checked); gen_tempat_textures hias.png; previews read

## Langkah berikutnya
DONE (session 2). Session 3 re-check: all files on disk; F0 placeholder default branch is gone (real kinds 21-60,
33 roles left NONE on purpose: their 40.4.1 look is all Sosok); no NoopRenderer left. Only F1 Gradle + in-game look remain.

## Butuh dari modul lain
- 40K: real `SosokGear.daftarTopi(int, boolean, String...)` (C40.K2); KotaGear.register calls it (stub-checked only).
- 38KS/38DS/38TV/38W client: call `HiasanDunia.pasang/festival/hapus` (lantern hooks, banners, awnings, armillary,
  wheels, incense) and set their `KaitModel` readers; 38EK: `RakSenjata.pasang` for smithies + `gerobakMuatan`;
  38GU: KaitModel cage/glyph/door/puppet readers; 38UI: `PapanHidupRenderer` for its board entity type.
- Server test `/kultivasi uji model pamer|prop|gear|wajah` (38.O.5) has no owner row; client `/hiasan` covers
  festival + senjata.
- F1: Gradle build (compileJava compileClientJava jar), mergeLang of tempat_model.
