# m2-F-FIX2 - finish of the second audit fixes

Build: `compileJava compileClientJava jar` BUILD SUCCESSFUL. mergeLang: no duplicate or missing keys. cek_sosok 0 errors, cek_tempat 0 errors.
Jar copy: `%TEMP%/kultivator-F-FIX2.jar`. Per-fixer checklists: `m2-FIX2-formasi.md`, `m2-FIX2-pusaka-epic.md`, `m2-FIX2-klien-sekte.md`, `m2-FIX2-data-lintas.md`.

## Needs applied
- Id wrap `<` to `<=` in `RealmScenery` (329), `TowerScenery` (298), `FarRelic` (473): fixed.
- gabung_aturan: new `Akibat` `gabung.aturan` in `MasukCerita` (opens `gabung_aturan_<aliran>` for the gate NPC), and a "read the sect rules" choice in `gabung_gerbang.json` (choice 3) and `gabung_gerbang_sumpah.json` (choice 2). Lang keys `kultivator.cerita.gabung_gerbang.mulai.3/.4` and `gabung_gerbang_sumpah.mulai.2/.3` in `sekte_gabung_masuk` id_id and en_us ("Nanti" moved to the last number).

## formasi
Fixed: 1 personal K1 alarm, 2 "Removed" line, 3 killing-array affinity, 4 amethyst in stone-only cores, 5 crack timer after restart, 7 zombie door-breaking, 8 A2 arts and trip debt, 9 Stacking.fieldAt cache, 10 test mobs.
Skipped: 6 unbuilt (Titik Formasi 6b anchor path).

## pusaka-epic
Fixed: 1 Titah menu reset, 2 far giant weapons after dimension round trip, 3 Bawaan tooltip refresh, 4 BEAT_STOP_ALL id collision.
Skipped: 5 TitahSettings.OP_OPEN never sent (harmless, hub opens TitahScreen directly).

## klien-sekte
Fixed: aura-fx 1 old world after disconnect, 2 SpecFx try/catch, 3 id overlap (Phantom/Dampak plus the three client files above), sekte-klien 1 hall policy "Memuat...", 2 preview tied to dimension, 3 action text over 64 chars, goals-codex 3 stale Desa card.
Skipped: none false. Unused `DesaClient.data()` and server `CH_DESA` push left as is (spine stub).
Not applied: `EpicDisplays.java:153` same `<` wrap, optional (boundary -4M unused, no overlap).

## data-lintas
Fixed: RingProtection (saved RescueStore), TitikSiar, DistrikMalam.ADA, ObrolanWarga, LembarKavling, GaleriUji.hapus, papan_cabang recipe and loot table, PetaItems lang, entity lang keys, GuideChapters dead alternative, DuniaCommands chips, Targeting LoadedPath.ray.
gabung_aturan dialogs: now reachable (see needs applied).
Skipped: DesaClient (not that fixer's item), 6b Titik Formasi (not touched there).

Risks: nothing run in game. Formasi 5 (crack timer) only protects the pre-verify window if no event re-read hits the core first after load; formasi 8 state is per core record, not per owner.
