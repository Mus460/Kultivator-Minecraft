# mega-D-40G (W3 detail pass of 40G: clips / pegas / wajah polish)

Scope: jadwal W3 row D-40G. Owns exactly 40G's files: `client/sosok/gerak/**`, `client/mixin/PlayerModelMixin.java`,
`client/disciple/power/DutyPoses.java`, `client/dunia/kota/NpcPoses.java`. No Gradle (F3 only). MandiriPoses offsets are
39E's (D-39), not mine.

Harness (scratchpad `d40g/`): `jc.sh [run]` = javac of gerak/** + DutyPoses + NpcPoses + mixin + `t/DumpPose.java`
against cp.win (F2 classes), then DumpPose writes `pose.jsonl` (6 samples per clip/variant) + `grips.txt`;
`grip.py <repo> pose.jsonl grips.txt <outdir> [clip ids]` rebuilds SosokPropLayer's transform chain in Python and draws
body + real prop models (texture-mean colours) in 3 views -> `grip_NN.png` (ids 900-909 = life-path worn props).

## Checklist
- [x] Audit: infrastructure complete (layers, Wajah, Iseng, Tatap, Pegas, ProfilJalan, reactions); gaps = content
- [x] Grip preview harness (above); first full review of 85 prop tiles
- [x] PropKatalog: new grip flag `TEGAK` (arm turn undone at the fist: hanging buckets/baskets/purses/fish/rope/
      tasbih/keys/medicine box/trophy, level bowl/saucer/tray/palette, draping fly whisk) + SosokPropLayer support
- [x] Grip fixes: tongkat/sapu/cangkul/sekop/bendera rx 180 (tool ends at the ground), alu -135 + kuas -118 (point
      down into mortar/paper), sempoa tilted to the face, teropong at the eye (clip 84 arms -118), seruling hangs
      down the forearm when carried, labu_arak dy 0
- [x] Clip prop fixes: latihan_tombak tip forward; guqin across the lap; asah_pedang sword across the lap (solved
      numerically); tulis_jimat paper flat; angkat_papan plank on the shoulder (PUNGGUNG offset); seruling across lips
- [x] SosokPropLayer: DEPAN_KAKI cancels the body lift (`GerakApplier.naikModel`: seat lowering + LIFT + bob/float),
      top/wooden fish no longer sink; PINGGANG points moved to the front belt corners (design point was inside the arm)
- [x] SikapTabel: merchant's abacus tucked flat in the back of the belt
- [x] Second full review of all 95 tiles (3 views): + sendok_sayur down into the pot, sapu dy -8, kebut 0.7, yuloh
      blade back-down into the water (-65), kite 2.5 blocks up/1.5 ahead (was behind the child), washing-line cloth
      hangs from both hands (clip 57 v1)
- [x] Gender/age variants (40.5.6 rule; variants 30 -> 54): hormat 32/33 anak + tua, kaget 18 anak/tua/P, lambai 34
      anak/tua/P, tertawa 36 anak/tua/P (sleeve at mouth), minum_teh 21 P, minum_arak 141 P (cup behind sleeve),
      tepuk_tangan 165 anak/P, tepuk_bahu 166 anak (tugs sleeve), angguk_dengar 167 L (hands behind), lari_lambai 168 P,
      menangis 149 anak/P, menguap 148 P, sikap_berkacak 117 P; hand-to-mouth arms checked in pv/ previews
- [x] Iseng: + selipkan_rambut (women), elus_jenggot (old men), batuk (elders), tendang_kerikil (children),
      kebas_jubah, lihat_langit; pilih() gets tua/perempuan
- [x] Wajah.mulutBicara: per-letter timing walk (vowel 2, consonant E 1 + closed 1, space 2, punctuation 6, +6 tail);
      harness self-check "Salam, Tuan!" OK
- [x] GerakApplier: PENARI toe walk (backward leg kick x0.35). Skipped: anak lari_riang (needs target distance)
- [x] Pegas: skirt front/back panels may lead the legs but never lag (no poke-through on a fast stride); harness
      spring self-check (walk/sprint/turn/stop, 60 fps): 0 out-of-limit, settles (OK)
- [x] NpcPoses.kerja: a standing work clip for ~55 roles of 38/38.T (cook stirs, fisher hauls the net, captain's
      telescope, caravan leads the camel, cave painter, dancer spins, pilgrims pray, prisoner kneels in chains...)
- [x] Final: javac 0 errors; DumpPose: 175 clips, 54 variants, 0 bad samples; mouth OK; pegas OK; all files LF

## Langkah berikutnya
DONE (W3 scope). F3 compiles. Not done: anak lari_riang (no target distance on the client), rok_zirah x1.6 stiffness (40K side).

## Butuh dari modul lain
- F3 / DESIGN owner: 40.5.7 PINGGANG_KIRI/KANAN points are now (+-2.6, 10.5, -2.6) (the design's (+-4.6, 9, 0) is inside
  the hanging arm); new grip flag PropKatalog.TEGAK (C40.G1 addition, optional for 39E/39F `daftar(.., Pegangan)`).
- D-39 (MandiriPoses, 39E): PINGGANG_KANAN now sits at the front belt corner (-2.6, 10.5, -2.6); its labu air offset
  (0,0,0) still fits. DEPAN_KAKI props now stay on the floor while seated/squatting (kuali/meja/dupa offsets that
  compensated the sink by hand should be re-checked: the -2/-1/-3 dy notes in mega-39E may now be too low).
