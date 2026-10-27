# m2-ANIM-murid-tugas (fixer, slice murid-tugas)

Rule: every disciple activity is REALLY animated (clip + prop + at and facing the work spot). Approach chosen after reading the code: the
server Sosok path (`SosokApi.gerak`, clips and their default props, played by GerakPengendali layers 4/6 and SosokPropLayer) driven from one
place in DiscipleEntity (`animateDuty`, every 10 ticks); DutyPoses/DiscipleHeldItemLayer (legacy path) left alone, see skipped.

- [x] DiscipleEntity: animateDuty + setActivityFocus/setGuardSits + shouldMeditate + look goals gated (STAY no longer sits)
- [x] 12 FORGE_ASSIST (Forging: focus = forge; PALU_LANDASAN / KIPAS_TUNGKU alternate every 5 s, clip props palu / kipas)
- [x] 5 FORMATION_GUARD (BERDIRI_JAGA + tombak; GrandFormation core keeper sits via setGuardSits; VillageGuards faces the owner; PromotionTrial faces the hall heart)
- [x] 8 SPECTATOR (DuelSession/Tournament/Kerumunan/Builders plaza: focus; DuelVoices + Tournament + Kerumunan clap/cheer clips)
- [x] 11 FORMATION_KEEPER / JAGA_FORMASI (Keepers.refresh: face the eye, SEGEL_TANGAN every 12 s; mandiri JAGA_FORMASI goes through Keepers.assignSelf)
- [x] 10 BUILD (Builders: focus = work spot; animateDuty plays PERBAIKI_PALU at the spot, ANGKAT_PAPAN while moving; vanilla swing removed, dust/sound follow the clip)
- [x] GUARD_HALL (BARIS_JAGA walking / BERDIRI_JAGA standing) / STAY (BERDIRI_JAGA); clip stopped when the mode changes

## Skipped
- DutyPoses / DiscipleHeldItemLayer / DiscipleLook: the legacy path is gated to a standing, unseated body, which cannot show a seated cheer,
  a seated seal, a march (BARIS_JAGA) or a plank carry; server clips already bring their own props through SosokPropLayer.
  DiscipleLook name labels for keeper / forge assistant kept as extra info.
- CoreTicker: Keepers.refresh (once a second) already owns the keeper loop; no change needed.
- 6 TOWER, 7 VOID_BODY: meditation kept, as ordered.

## Needs (outside the slice)
- DiscipleCultivation.java lines ~182 and ~1824 (`entity.isMeditatingNow()`): use `entity.cultivatesNow()` so formation guards, forge assistants
  and STAY disciples that now stand keep their meditation XP / qi regen (Forging.java:173 already halves it for FORGE_ASSIST).
- sect/hall/Candidates.java:545 (spectators): add `disciple.setActivityFocus(<arena/ring centre>)` after setActivity(SPECTATOR, seat).
