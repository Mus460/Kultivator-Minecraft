# m2-F-ANIM (finish of the animation wave)

Build: `compileJava compileClientJava jar` BUILD SUCCESSFUL (no errors, mergeLang clean). `tools/cek_sosok.py`: errors 0, warnings 0.
Jar copied to `%TEMP%/kultivator-F-ANIM.jar` (not into .minecraft). Nothing rendered or played in game.

## Needs applied
- `DiscipleCultivation` (lines 182, 1824): `isMeditatingNow()` -> `cultivatesNow()` (guards, forge assistants, STAY keep XP / qi regen while standing).
- `sect/hall/Candidates` night spectators: `setActivityFocus(stele)`.
- `sekte/misi/Misi.tickOwner`: calls `TimMisi.tampilkan(server, owner)` once a second (mission team stage bodies).
- `Utusan` (NPC envoy arrival: post, face master, HORMAT) was already done by the jelmaan slice; verified.

## Audit section 4: clips that were unused
All referenced now except PASANG_BENDERA (133): no sect-claim / territory event with a body exists (the hall stele is placed by the player, who has no Sosok body).
SUJUD_GURU, MAKAN_PIL, TEROBOSAN_BERHASIL/GAGAL, and all 24 design-42 clips (lerai ... pincang) have a caller.

## New verdicts for the 111 audit rows
REAL 108 (was 69), PARTIAL 1 (was 23), TEXT-ONLY 2 (was 19).
- PARTIAL: VillageGuards depart (guard has a real body at the plaque only when the owner is within 64 blocks; no far proxy).
- TEXT-ONLY: Crossing / Alam / RealmRun, spirit ship (another realm, no place in this world).
- The "memandang" rows (jalan_jalan, pengantin, lihat_kota, pandang_*, belokan, burung_sangkar, penggemar_opera) count REAL via TATAP_LANGIT (sky gaze, not a looking-at-the-view clip).
- Not checked: keyframe quality, Jelmaan stage bodies at runtime, saw / kitchen knife are stand-in props (papan_kayu, pisau_ganda).
