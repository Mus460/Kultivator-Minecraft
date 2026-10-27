# m2-FIX murid-aula

Reports: audit-sekte-murid-murid-inti.md (M), audit-sekte-murid-sekte-aula.md (A). Not compiled (no Gradle).

## murid-inti
- [ ] M1 Soul Flight grave: needs fix in sekte/paviliun/Makam.java (not my file): Makam.tick skip when `e.departed()`.
- [x] M2 lava/wall faint loop: DiscipleEntity.die immortal branch returns to lastSafePos, else beside master.
- [x] M3 void fallback: onBelowWorld tries lastSafePos, post, then spawn only in its own dimension.
- [x] M4 duty clip loop: animateDuty keeps dutyClip while a timed once clip plays.
- [x] M5 faint released keeper: DiscipleBridge releases only when bolehGugur.
- [x] M6 unprotected hook loops: try/catch in DiscipleHooks.onDeath ON_DEATH and Disciples.arrive ON_ARRIVE.

## sekte-aula
- [x] A1 village guard freed each second: GrandFormation.heldElsewhere skips VillageGuards.isGuard (now public).
- [x] A2 Forbidden Ground tablet: as_tablet only if bolehGugur; AwayTeams.arrive drops a leftover as_tablet when off.
- [x] A3 VillageGuards.assign: skips dying/releasing/breakingThrough/duelling/busy (activity != NONE).
- [x] A4 tempa.order.* lang keys added (id_id + en_us).
- [x] A5 Jelmaan stage only when MandiriApi.aktif() (AwayTeams, GatherExpeditions).
- [x] A6 Duties.assign recomputes spot when the duty changes.
- [x] A7 forgotten soul away: new Disciples.forgotten(server, tag); AwayTeams, GatherExpeditions, AwayStore, VillageGuards (release, tick, tickReturns) end the record.
- [ ] A8 vassalVillages=false freezes guards: skipped (intended flag-off behaviour, data kept).
