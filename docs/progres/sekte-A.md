# sekte:A — Paviliun Kitab & Pemakaman/Altar Leluhur (DESIGN 36.2.5, 36.4 Modul A)

Status: DONE (run 2, 2026-10-22). javac 25 server+client 0 errors (aA/jc.sh out5, incl. SekteContent/SekteContentClient). No Gradle.

## Checklist
- [x] 0 Audit core (sekte/, api PaviliunService, SekteBus, Ingatan, SekteIds, SekteConfig, SekteCommands, TemplateExtensions,
      SekteScenes, SektePoses, HubCards, KotaNet payload pattern, MenaraScreen UI pattern, death flow, JadeSlips.discipleBlocker)
- [x] 0b Space audit of templates: harness scratchpad/aA (QuadMap, FreeFind, CellWho; private jc.sh/args.txt — the lead's
      sektejc/args.txt is shared, do not use). RESULT: west-back quadrant is FULL in T2-T6 (t8_pagoda, t8_ancestral, lotus,
      main hall). Plots chosen per template from free pockets (west side first), documented below.
- [x] 1 Server: [x] PaviliunConfig, [x] PaviliunData (hall section sekte_paviliun + extra.kitab), [x] Tempat (spots/free), [x] Paviliun (init+service+useBlock+ticks), PaviliunView (screen data + acts)
- [x] 2 RakKitab (RakKitab.java) (deposit technique books / jade slips via library anchor or hub), capacity 9/18/27
- [x] 3 BelajarMandiri (BelajarMandiri.java) (idle disciples study, ACT_STUDY, points, learn, DISCIPLE_STUDIED, ingatan)
- [x] 4 Makam + Silsilah (Makam.java, Silsilah.java; uses PaviliunSync.dirty + Upacara.funeral, written) (onDeath check next tick, Soul Tablet use on altar/makam, 3 strongest memories, overflow)
- [x] 5 AltarLeluhur + Upacara + item Dupa (AltarLeluhur.java, Upacara.java, Dupa.java; Qingming kowtow drawn by A's MOURN pose timed from Places rites, not ACT_BOW) (daily incense, Qingming 15 days ACT_BOW, funeral ACT_MOURN, blessing)
- [x] 6 PaviliunKomponen (sekte/paviliun/PaviliunKomponen.java): sx_paviliun_altar tier 2 (T2-T4 compact 3x2, T5-T7 5x5),
      sx_paviliun_makam tier 3 (T3 3, T4 4, T5 5, T6 9, T7 16, T8 12, T9 32 graves); anchors altar, altar_meja, altar_dupa,
      makam_<n>, makam_pusat, makam_tugu; zone sx_paviliun_tanah on T7/T9. Harness FreeFind ext: 0 overlaps (X/Y), 0 outside,
      0 unknown blocks in all 8 templates (flower_pot was unknown to Costs -> removed).
- [x] 7 PaviliunPayloads (View/Places/Act) + PaviliunSync + PaviliunCommands. javac server 0 errors (aA/jc.sh out2)
- [x] 8 Client (client/sekte/paviliun): PaviliunClient (1 hub card SEKTE sekte_paviliun, scenes 140-143, motes), PaviliunScreen shell +
      RakScreen/MakamScreen pages, PaviliunWorld (Places state, lantern scan), PaviliunRenderer+PaviliunSink (labels, tablets,
      smoke, night glows, floating sutra for seated readers), PaviliunPoses (13 study, 14 mourn + Qingming kowtow). javac 0 errors (out3)
- [x] 9 Assets via tools/gen_sekte_paviliun_assets.py (checked with Read: scratchpad aA/prev_assets.png, prev_dupa.png): textures/sekte/paviliun/
      {smoke,glow,tablet,segel}.png, item/fx/sekte_paviliun_{sutra,stick,ember}.png, pieces sekte_paviliun_scroll/_incense, item dupa
      (items/models/texture), recipe/dupa.json, advancement/sekte/paviliun_belajar|_upacara.json
- [x] 10 Lang via tools/gen_sekte_paviliun_lang.py: lang-src/sekte_paviliun.{id_id,en_us}.json 153 keys identical, all used keys present
- [x] 11 Wired (SekteContent line 68, SekteContentClient line 28; both LF)
- [x] 12 javac self-check 0 errors; harness aA/h2 (Clash + FreeFindE with ALL module extensions registered): run-1 layouts
      collided with W (T2 beds, T4/T5 mine) and T (T8 tribune) -> relocated T2 shrine (-13..-11,13..14), T3 east end trimmed
      to x-10, T4 graves to front lane z17-18, T5 graves outside front wall x-21..-13 z21-23, T8 trimmed to x-38..-32.
      Result: 0 shared columns with any sx_ component, 0 X/Y, outside=0 in T2-T9 (only T8 touches T's stands, T4 altar
      touches W's mine diagonally).

## Next step
None (module done). Finish step: Gradle; `uji sekte audit/pamer` for T2-T9; `uji perguruan paviliun ...` in game.

## Run-2 audit notes
- Death: DiscipleHooks.addOnDeath runs BEFORE death (may be cancelled by later handlers) -> capture snapshot, confirm next tick
  (entity.isDeadOrDying/removed). Soul Tablet = DiscipleItems.DISCIPLE_TABLET, custom_data "kultivator_tablet" (soul,name,disciple).
  Tablet is NOT consumed by A (revival stays possible); revived soul -> its grave is lifted.
- R drops its pair table on DISCIPLE_BURIED -> collect mourners BEFORE firing. P reads blessing(); E reads studying().
- Study gates: JadeSlips.discipleBlocker(entity, spec) + not known; books via BookPools.poolOf(STORED_ENCHANTMENTS); slips JadeSlips.specOfStack.
- Anchors via SectBuild.anchor(owner,id) (only once the component is built). T8/T9 have no A altar -> rites at makam_tugu.

## Open problems / notes for finish
- Plot rule of DESIGN 36.3.5 (west-back quadrant) cannot hold in T2-T6; documented in PaviliunKomponen.
- ACT_BOW not used for Qingming (it holds no spot; STAY disciples would walk off): A's own MOURN pose kowtows, timed by Places.
- One hub card (sekte_paviliun) with both pages inside, not two (HubCards: one card per module, SEKTE grid).
- Beats: 140 Qingming, 141 funeral, 142 enshrine, 143 learned (stamp, no camera).
- Voice proposals: disciple.<v>.mourn "Beristirahatlah dengan tenang." 「安らかに眠れ。」; disciple.<v>.study
  "Kitab ini... aku mulai mengerti." 「この書、少しずつ分かってきた。」 (code uses farewell/cultivate/thanks now).
- Guide wording request (lead): c15.kitab mention the hub card and the rack click; c15.leluhur mention the Dupa recipe.
