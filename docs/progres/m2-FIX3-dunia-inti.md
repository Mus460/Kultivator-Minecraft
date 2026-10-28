# m2-FIX3 dunia-inti (audit3-dunia, audit3-inti, audit3-formasi-epic, DharmaForm, Peta/WillContest/ProtectedZones)

## Checklist
- [x] dunia 0 / multiplayer 1. ProtectedZones.claimHallsAt starts at Long.MIN_VALUE / 2; cache reset on SERVER_STOPPED (Kehancuran.init -> ProtectedZones.clearClaimCache)
- [x] dunia 1. Camps ids are random positive ints (unique across restarts, no collision with ids saved in missions). PapanMisi lazy reopen is a "needs" for kota (file dunia/kota/PapanMisi.java)
- [x] dunia 2. PrimordialEvents.depart: dead/offline owner gets the scroll in the Lumbung (if a hall) else an owner-locked drop at the arena; omen abandoned on LEAVE and SERVER_STOPPING (Purba.init)
- [x] dunia 3. BeastTribulation: LEAVE and SERVER_STOPPING refund half the offering while the owner is still online, then end the session
- [x] inti 1. DaoDomain.tick ends the domain when the player's level differs from the cast's level
- [x] inti 2. Meditation: rest of a stone's fuel period saved in the persistent extra (fuel_grade, fuel_left) on leave, restored by the next refuel
- [x] formasi 1. TransportNetwork.Effect.pulse: pays what the core has, the rest stays owed (debt written back)
- [x] formasi 2. Keepers.refresh frees loaded keepers (activity 11) that no core lists (freeOrphans)
- [x] formasi 3. Formasi.tryUpgrade refuses (new lang key refuse.amethyst_full, formasi fragment, id + en) when the amethyst share does not fit the amethyst Kas
- [x] klien 2. DharmaForm.tick resends VisualSync.form every 40 ticks
- [x] multiplayer 4. Peta.onRequest: 20 requests per second per player (CLOSE exempt), watch.regions capped at 4096
- [x] multiplayer 5. skipped: WillContest trusts the client minigame by design (see reply)

Not compiled (no Gradle run). Line endings kept per file (Camps, Purba, Meditation are CRLF).
