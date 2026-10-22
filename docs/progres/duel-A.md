# duel:A progress (animations, sword models/textures, blade data) - DESIGN 35.5, 35.9 module A

Run 2 (2026-10-22). Run 1 left the shared blade files + DuelItems on disk (reviewed, kept).

## Checklist
- [x] 1 Shared blade files reviewed (DuelPose 28 poses, DuelPoseTable intent solver, BladeShape, BladeKinematics,
      BladeArcs, BladeSegment) + duel/item/DuelItems; solver checked numerically (scratchpad duelA_test)
- [x] 2 technique/spec/dsl/Pose.java: DUEL constant at the end
- [x] 3 Assets: tools/gen_duel_gerak_assets.py -> textures/item/{pedang_latih,duel_post}.png,
      models/item/{pedang_latih,duel_post}.json, items/{pedang_latih,duel_post}.json;
      data/kultivator/recipe/pedang_latih.json; data/minecraft/tags/item/swords.json (replace false)
- [x] 4 lang-src/duel_gerak.{id_id,en_us}.json (36 identical keys: 2 items, 28 poses, 6 test messages)
- [x] 5 client/duel/anim: DuelAnim, DuelPoses (DuelBus listener, stances, intro/finisher ceremonies, previews),
      DuelPoseLibrary (mirror, wrist fold, off hand on hilt, BC arm rule, underCast merge)
- [x] 6 BladeTracker (segment/velocity/drawn/previous, fallback BladeArcs), BladeProbeLayer,
      DiscipleHeldItemLayer.swordTransform
- [x] 7 FirstPersonDuel + FirstPersonCast branch (localPose falls back to DuelPoses)
- [x] 8 PalBridge (reflection, off by default, plays only shipped animations), BladeDebug (uji duel bilah lines)
- [x] 9 Edits: PoseRenderData.extract (duel first, cast >0.5 wins, SARUNG hides item), AnimClient (probe layer,
      prune, clear), RogueRenderer (extract + lift/tilt)
- [-] 10 tools/duel_pal_export.py + player_animations: skipped (optional; PAL JSON format unverified, own poses suffice)
- [x] 11 Self review: javac 25 (Minecraft runtime, no Gradle) on all A files + edits: 0 errors in A code
      (only 5 pre-existing access-widener errors elsewhere); LF everywhere; no client refs in main

## Interfaces for other modules
- S: DuelPreview PV_POSE: arg = DuelPose ordinal, rgb = 0 local player / entityId+1 for a broadcast salute
  (/kultivasi duel hormat), f = hold ticks. PV_ALL_POSES, PV_BLADE_DEBUG(arg 1/0) handled. S must call
  DuelItems.init() from Duel.init (C35.S1).
- K/Dunia: PlayerModel NPC renderers call PoseRenderData.extract at the end of extractRenderState.

## Next step
Done.
