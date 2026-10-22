# Progres duel:V (visual klien duel, rancangan 35.4 / 35.9 V)

Run 2 (2026-10-22). Run 1 (06:51-07:12) left files on disk without a progress file.

## Checklist
- [x] (run 1) EpicDirector.duelScene/refreshDuelFlags + ShotGuard.ignoreHostile (diff reviewed, LF, OK)
- [x] review fx/* from run 1 (APIs checked vs mcsrc + repo): DuelLinks, DuelCodes, DuelFxSettings, DuelFx, ContactSolver,
      SparkBurst, DuelSparkParticle, DuelEmberParticle, MetalFlash, BladeTrail, DuelTrailRenderer, ShockRing, ScorchDecal,
      DuelDust, DuelTime, DuelShake, DuelIntroFilm, DuelRingVisual, DuelRopeRenderer, DuelDisplays, DuelGeo, QiWaveVisual,
      CompanionSwordVisual, DuelMarks; main fx/DuelParticles; particles/*.json; textures/misc/duel/*.png
- [x] cross-check refs: DuelIds constants all exist (S), DuelPayloads fields OK, DuelBus/DuelClock/DuelClient (U) OK,
      BladeArcs.sample / DuelPose.forSwing (A) OK; BladeTracker (A client) still to come from A (contract C35.A4)
- [x] DuelFinisherFilm (fx/DuelFinisherFilm.java; DuelIntroFilm.leafOf made package-private)
- [x] DuelCamera + client/mixin/duel/DuelCameraMixin (DuelFx.init calls DuelCamera.init)
- [x] DuelPreviews + SparkBurst.play(e, forcedContact)
- [x] lang-src/duel_fx.en_us.json + .id_id.json (35 keys each, identical)
- [x] final self-review (mcsrc APIs, symbols by hand: no JDK 25 here so no javac check; all V files LF; main DuelParticles has no client refs)

## Next step
Done. Only the finish agent's build remains (it may surface typos the hand review missed).

## Open problems / needs from others
- S: Duel.init must call dev.kultivator.fx.DuelParticles.register() (C35.S1).
- S DuelTests -> DuelPreview args: SPARK arg=outcome (-1 cycle) rgb f=power; SLOWMO arg=ticks; INTRO/FINISHER arg=partner id or -1;
  TRAIL arg=dir f>=0.5 qi; BIND arg=ticks; SHOCK/DECAL rgb; RING arg=radius (documented in DuelPreviews javadoc).
- A's client/duel/anim (BladeTracker) not on disk yet: DuelLinks calls it per contract C35.A4.
- Camera cycling key is U's (DuelSettings.nextCamera); V only reads duel_camera.
