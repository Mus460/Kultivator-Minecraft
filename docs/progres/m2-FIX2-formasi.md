# m2-FIX2 formasi (audit-formasi.md, 10 findings)

## Checklist
- [x] 1. K1 alarm: territory mode only when the core stands in the hall territory (AlarmEffect.pulse); TerritorySweep cache keyed by owner + dimension
- [x] 2. CoreTicker.forget: the "removed" line and log print the Daya that really went to the Kas (refund minus what did not fit)
- [x] 3. Affinity.killing reads the spec param `kind` (sword; heaven and earth = thunder; blaze = fire; water) instead of the never-set `affinity`
- [x] 4. Formasi.tryUpgrade: amethyst share goes back to the amethyst pool when the new spec is STONE_ONLY
- [x] 5. crackSince saved ("crack_since", CoreRecord, store marked dirty); not reset before the first read after a load; Verifier does one full PatternMatcher.full per core on its first visit
- [x] 6. skipped: unbuilt (Titik Formasi 6b anchor path, EngineApi stub left alone)
- [x] 7. Zombie door-breaking: steered zombies that could break doors get tag kultivator.door_locked; ENTITY_LOAD (Formasi.init) restores the flag and removes the tag
- [x] 8. A2 arts count ("arts_seen") and trip debt ("debt") now live in the core record ext (Cores.setExt, saved); ARTS_SEEN and DEBT maps removed
- [x] 9. Stacking.fieldAt cache also keyed by the chunk of the asked position
- [x] 10. uji formasi musuh: ALLOW_LOAD blocks loading tagged test mobs from disk (unloaded or after restart)

Not compiled (no Gradle run). Line endings kept (CRLF).
