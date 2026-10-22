# Progres duel:S (inti duel server, DESIGN 35 / docs/rancangan/35-duel-pedang.md)

Run 3 (2026-10-22 14:20-): SELESAI. Run 1 (06:54-07:13) wrote the clash core; run 2 only wrote this file.

## Checklist
- [x] (run 1, reviewed run 3) DuelIds, net/DuelPayloads, clash/* (16 files), ai/DuelStyle, DuelistProfile, DuelistRegistry,
      api/Duelist, session/DuelRuleSet
- [x] external APIs verified (mcsrc + project); fixed MobGuard (EntityTypes.WITHER/ENDER_DRAGON/WARDEN in 26.3)
- [x] DuelConfig (config/kultivator-duel.json, unknown keys kept, reload)
- [x] net/DuelNet (all payload types, C2S receivers, prefs per UUID, send via canSend); Fighters sends via DuelNet.send
- [x] session/DuelRing, DuelSession, DuelSessions, DuelChallenges, DuelStakes, DuelEscrow, DuelRecords, (new) DuelResults
- [x] ai/AttackReader, DuelistBrain, DuelistGoal + mixin/duel/MobGoalSelectorAccessor
      (Fighter got staggerCount/nextActionAt/dodgeReadyAt/feintReadyAt accessors)
- [x] DuelHooks (onRoll, telegraph, bcSwing, ALLOW_DAMAGE filter in phase kultivator:duel_clash), Duel (init, events,
      castBlocker, surrender, onClash, cheer, onBindResult, sendResultClash, testChips), (new) clash/Reactive
      (BC hit match, reactive deflect/guard vs mobs, armed clash, posture mobs + Deathblow, technique deflect, projectiles)
- [x] Fighters: public bcSwing(ServerPlayer,...), telegraph(...), sendResult(...); Exchange marks decisive results,
      tells the AI when its cut was deflected
- [x] DuelCommands (/kultivasi duel ...), DuelTests (/kultivasi uji duel ...; CHIPS for CheatHelp)
- [x] data: damage_type/duel.json; data/minecraft/tags/damage_type/bypasses_armor.json += kultivator:duel
- [x] lang-src/duel_inti.{id_id,en_us}.json (123 identical keys; scratchpad/duelS/gen_lang.py)
- [x] edits: Kultivator.java (Duel.init after DuniaContent.init), CultivateCommand.java (Duel.addCommands before
      TestCommands), CheatHelp.java (group duel), kultivator.mixins.json (duel.MobGoalSelectorAccessor)
- [x] voices: kota.wanderer_dao / kota.rival_claim on NPC offers and openings (VoiceEvents.exists, fallback disciple lines)
- [x] javac 25 check (no Gradle) of all 57 main-side duel sources + edited files against build/libs jar: 0 errors
      (scratchpad/duelS/args.txt, mkargs.py); all files LF

## Next step
None. The finish agent builds once.

## Open problems / notes
- Technique deflect (35.3.12) is a heuristic: non-pierce technique hits, first hit on that victim within 10 ticks
  (auras/domains tick repeatedly), facing; the spec shape is not readable from a DamageSource.
- `/kultivasi duel layar` and `tantang` without target: no server->client "open screen" payload exists; tantang takes
  the looked-at entity, layar points to Hub > Dunia > Duel Pedang (U's hub card).
- `duel hormat` / `uji duel pose`: DuelPreview PV_POSE with arg = DuelPose ordinal and f = the player's entity id.
- dunia/kota/KotaDuel (K) already uses DuelistProfile / DuelChallenges.challenge / npcOffer; signatures match.
