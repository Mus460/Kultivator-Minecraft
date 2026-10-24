# mega-D-MD (wave W3 detail pass) — 38MD models, textures, gear, props, decorations

Scope: jadwal sec 3 W3 "D-MD (models/textures)" = files of 38MD (see mega-38MD.md): `client/dunia/kota/KotaGear.java`,
`textures/dunia/kota/entity/perlengkapan.png`, `client/dunia/tempat/model/**`, `tools/{gen_tempat_models,gen_tempat_gear,
gen_tempat_textures,tempat_iso}.py`, assets `**/tempat/**` written by those generators, `lang-src/tempat_model.*`.
Must not touch: KotaGearLayer, NpcRenderer, 40 faces/scale, other modules' callers. No Gradle (F3 builds).

## Checklist
- [x] 1. Audit: renderers vs 38.O.2; callers: GuaClient sets cave readers, KotaBesarFx uses HiasanDunia; NOBODY set
      gate/cart/raft readers (KotaBesarClient/EkonomiClient/JalanClient never did) and nobody calls RakSenjata.pasang
- [x] 2a KaitModel defaults read synced entity data: gate bukaLerp/lebar/tinggi/robohLerp + new gerbangRetak/gerbangSel,
      cart muatan(), raft stroke from sedangMendayung() (no wiring needed by other modules)
- [x] 2b GerbangKotaRenderer: no double easing, Yamen cell door variant (barred iron, lock plate), lion-head knockers with
      swinging rings, meeting-edge beam, cracks by retak (light/heavy bands), ground dust when leaves start to swing;
      gen_tempat_models gerbang_kota 128x256 (+knocker_bronze, crack_overlay x2, cell_iron)
- [x] 2c GerobakRenderer: 12-segment felloe + tyre plates, 6 spokes, hub + pin, stake pockets, footboard, chest hasps +
      2 ropes, sack ties in rope, swinging front lantern; gerobak 128x224 (+jar_glaze, rope, red_paper)
- [x] 2d PenjagaKristal: step camera shake 0.1 (CameraShake, setting applies) + floor dust, hammer-blow shake +
      amethyst splinters + END_ROD, death burst once (counts by EffectQuality)
- [x] 2e Rakit: splash at the pole's bite per stroke, bow wake per tick while poling
- [x] 4. PamerModel (new, model/**): /hiasan pamer (9 animated models on an arc, labels, glow passes, 30 s) and
      /hiasan prop [1|2] (props 1-40 / 41-88 via PropKatalog, turning, number+name labels); HiasPerintah wired, info
      shows stands; TempatModelClient.init calls PamerModel.init; lang tempat_model 15 keys identical (id/en)
- [x] javac (scratchpad dmd/jc.sh against B2 classes): model/** + KotaGear clean after steps 2-4
- [x] 3. Texture pass: previews read (scratchpad dmd/prev); gear bands 16-47 already rich (md38 previews re-read, no
      change); pintu_tebing band 0 false_stone (vanilla-like flecks, hairline cracks, worn seal ring, lichen foot),
      band 1 cracked_stone; papan_hidup roof band = roof_tiles (barrel tiles); boneka/kristal/tikus/glif/rakit/sangkar
      kept (read OK)
- [x] 5. gen_tempat_models.py re-run: 45 props, 28 weapons, 13 entity textures, errors 0 (gerbang_kota 128x256,
      gerobak 128x224, pintu_tebing, papan_hidup changed); javac clean; all edited files LF; lang 15/15 identical
- [x] 6. Final report = this file

## Langkah berikutnya
DONE. F3: Gradle build (compileJava compileClientJava jar); in game: /hiasan pamer, /hiasan prop 1|2, a capital gate at
dusk (swing, dust, knockers), a Yamen cell door, a caravan cart and a ferry crossing.

## Butuh dari modul lain
- D-EK (38EK smithy owner): call `RakSenjata.pasang(key, racks)` from the smithy sync (real stock, 38.M.2.1) and
  `hapus` when the shop leaves; nobody calls it yet (only the /hiasan senjata test shows racks).
- D-KS / D-EK / D-W: no longer need to set gate/cart/raft KaitModel readers (defaults read the synced entity data);
  `GerobakEntity.setMuatan` must be called by the caravan owner for loads other than chests (default 0).
- D-GU (optional, old 38GU note): giok_warisan compass range_dispatch item model is a 38GU item file, not D-MD's.
- F3: in-game look check of the above; DESIGN 38 note: /hiasan pamer|prop covers the 38.O.5 model tests client side
  (server `/kultivasi uji model ...` has no owner row).

## Open problems
None.
