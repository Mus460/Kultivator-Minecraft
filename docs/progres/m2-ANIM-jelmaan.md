# m2-ANIM jelmaan (fixer): away disciples get a proxy body at their stage

Rule: an away disciple (body discarded) is shown near a player by an unowned JELMAAN proxy: clip + prop at the work spot.
Spawn within 128 blocks of the place, despawn above 160, never loot, never a real disciple, cannot be hurt to death.
Checklist (updated after every item).

- [x] Jelmaan.tunjukkan / Adegan / PANGGUNG (generalised proxy: place, facing, clip, prop, activity, wound, walking)
- [x] Jelmaan.titikLuar made stable (a fixed site per team/party/mission, never moves when chunks load), dekatPemain public
- [x] Disciples.addArriveListener hook (real disciple back -> stage body gone at once); no edit to DiscipleEntity
- [x] GatherExpeditions: party members at a site 140-159 blocks from the stele: FLORA herb sickle (PETIK_HERBA_TEBING), BATU/KAPUR/LOGAM/PERMATA
      mine duty pose + pick, TANAH spade, KAYU saw, GENTENG plank carry, PERNIS sap sickle; ring around the site, facing it
- [x] AwayTeams: expedition (patrol/caravan spear post, herbs sickle, hunt sword routine) + recruiting envoy (reads its roll) at a fixed site
- [x] KafilahSekte: sect caravan escorts march beside the cart (BARIS_JAGA + spear, cart pace 0.35) within 128 blocks
- [x] GiliranTambang: a miner hurt in a cave-in sits bandaged at the mine mouth for its day away
- [x] TimMisi.tampilkan: story mission team at its place (sect gate for envoy/spy, village bell, else fixed wild site), clip by stage kind
- [x] Utusan: the player's own envoy bows at the other sect's gate (stage body); the NPC envoy arriving at the sect stands at its post,
      faces the master, plays HORMAT on arrival and again when the master walks up
- [ ] needs (other files): Misi.tickOwner -> TimMisi.tampilkan; VillageGuards optional far proxy

Skipped (checked, with reason):
- Crossing / Alam / RealmRun / spirit ship / forbidden ground / Primordial Ark: another realm, no place in this world.
- AwayTeams has no tournament team (the player's own fighters are never away); nothing to show.
- VillageGuards: the guard's real body is at the plaque whenever the owner is within 64 blocks (murid-tugas slice owns that file).
Nothing compiled (no Gradle by rule); finish agent builds.
