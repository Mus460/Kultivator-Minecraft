# mega-40G (W1, DESIGN 40.5 Gerak, client animation system)

Scope: jadwal W1 row 40G + sec 5 note. Owns old files: `client/mixin/PlayerModelMixin.java`,
`client/disciple/power/DutyPoses.java`, `client/dunia/kota/NpcPoses.java`; new `client/sosok/gerak/**` (40.7.1 list).
Must not touch: SektePoses, LangitPoses, LangitDutyPosesMixin, PoseLibrary, CastPoses, PoseApplier, DuelPose*, mixins json.
No Gradle.

## Checklist
- [x] Read design 40.5/40.6/40.7, 38.N.5 + 38.T.8.1 clip tables, 38.O.4 props, 40.4 sikap/jalan columns; code: PoseApplier,
      PoseLibrary, CastPoses, DuelPoseLibrary/Table, MeditationPose, DutyPoses, NpcPoses, NpcRenderer, DiscipleRenderer,
      DiscipleHeldItemLayer, FormModels, SosokApi/GerakId/PropId/Sosok/Gaya; mcsrc HumanoidModel.setupAnim swing
- [x] Klip/Kunci/Easing/Osilator/Sampler core (+ SumberCast/SumberDuel bake, Wajah strips/blink/mouth)
- [x] KatalogTempat 1-107 (40.5.10 changes folded in)
- [x] KatalogSosok 108-176 + activity map 40-56 (+ defaults 57-60), petakanBawaan never overrides owners
- [x] GerakKatalog (lazy build, daftar 200-399, petakanAktivitas, daftarKait) + KaitGerak
- [x] GerakData, GerakKeadaan (layer tracks Jalur), ProfilJalan, Pegas (chains + skirt)
- [x] GerakPengendali (layers, priority) + SikapTabel + Tatap/Iseng/GerakReaksi (files on disk, complete; session 2 reviewing)
- [x] GerakApplier (file on disk)
- [x] PlayerModelMixin line (GerakApplier.apply after PoseApplier.apply)
- [x] DutyPoses (duties 1-4 -> Sampler.terapkan clips 5/44/23/51, SektePoses 13-24 kept, signature kept, bend() gone,
      showsProp only legacy) / NpcPoses (public; flags -> clips `klip(npc, walking)`, snapshot/clear delegate)
- [x] Controller: NPC flag clips (local start time, GerakKeadaan.bendera), layer 5 Gerak activity clip track `aktKlip`
      (JALAN_OK may walk, DUDUK/BERSILA may sit; own props/events/lying), DutyPoses only falls back without Gerak data
- [x] PropKatalog (model ids 1-88 via PropId.jalurModel, daftar 100-199, grips hand/back/two-hand, missing model = not drawn)
- [x] SosokPropLayer (KANAN/KIRI/DUA_TANGAN/PUNGGUNG/PINGGANG/BAHU/PANGKUAN/KEPALA/DEPAN_KAKI, LOD, propLama, pasangTetap)
- [x] GerakKlien (init: katalog, GerakReaksi.init, 200-tick sweep, join/leave reset; suara; partikel ids)
- [x] Self-review: javac harness `scratchpad/w1_40G/jc.sh` = 0 errors (gerak/** + DutyPoses + NpcPoses + mixin, sources newer
      than B0 from the tree); runtime harness `w1_40G/t/UjiKatalog.java`: 175 clips + 30 variants built, every driven channel
      finite at every tick, activity map 40-60 resolves, 64 prop ids used, sampling 3 clips = 0.5 us/body. All files LF.

## Kontrak 40G untuk 40R/40K (C40.G1/G2; package dev.kultivator.client.sosok.gerak)
- 40R extract: `GerakPengendali.evaluasi(LivingEntity, DiscipleRenderState, float age)` AFTER PoseRenderData.extract
  (and after state.castActive is set); writes POSE only if empty, always writes `GerakData.KUNCI` (key `kultivator:gerak`),
  edits walkAnimationPos/Speed. Returns the idle Snapshot it stored (or null). `NpcPoses.snapshot(...)` still works (calls it).
- 40R setupRotations: `MeditationPose.lower`, `PoseApplier.lift`, `GerakApplier.angkat(state, ps)` (BOB, SPIN),
  super, `GerakApplier.putarTidur(state, ps)` (clip 13: 90 deg), `PoseApplier.tilt`.
- 40R submit layers: `new SosokPropLayer(parent)` for disciples and NPCs.
- 40K reads `GerakData.dari(state)` (null = not Sosok): `pegasX[]/pegasZ[]` per `Pegas.Rantai.ordinal()` (radians),
  `rokDepanX, rokBelakangX, rokKananZ, rokKiriZ, rokDepanSkalaY, rokBelakangSkalaY`, `mata` (0-15 strip B1),
  `mulut` (0-15 strip B2), `lod` (SosokDetail.Lod ordinal), `pegasAktif`.
- C40.G1: `GerakKatalog.klip(id)`, `daftar(Klip)` (200-399), `petakanAktivitas(act, klip, varian)`, `daftarKait(id, KaitGerak)`;
  `PropKatalog.daftar(id 100-199, Identifier)`; `Klip.baru(id, nama, durasi).loop().kunci(t, kanal, nilai...)...bangun()`.

## Langkah berikutnya
DONE (W1 scope). Only the wave finish agent runs Gradle. W3 polish (D-40G): tune grips in PropKatalog after looking at the
38MD/38TV/40K prop models in game.

## Butuh dari modul lain
- 40R: `/sosok muat_ulang` may call `GerakKlien.muatUlang()` (resets states + prop caches); `/sosok info` can show
  `GerakPengendali.jumlahKeadaan()` and `GerakKatalog.jumlah()`. SosokJembatan already calls `GerakKlien.init()` and
  `new SosokPropLayer(parent)` (matches).
- 38MD/38TV/40K: prop item models `items/fx/tempat/<name>.json`, `items/fx/tempat/tema/<name>.json`, `items/fx/sosok/<name>.json`
  (names = PropId); anchor (8,8,8), +y up, no `display`/parent scaling (grips assume FIXED = identity). Missing = not drawn.
- 39E/39F: `PropKatalog.daftar(id, Identifier[, Pegangan])` (100-149), `GerakKatalog.daftar` (200-299),
  `petakanAktivitas`; a mapped activity clip plays as controller layer 5 (JALAN_OK may walk, DUDUK/BERSILA may sit).
- Finish/F0 grep note: `GerakReaksi` only *detects* `LightningBolt` entities loading (instanceof), never creates one.
