# m2 FIX dunia-inti (fixer)

Reports: audit-dunia-lain.md, audit-inti-ui.md

- [x] inti-ui 1: Caster.lastSwing Long.MIN_VALUE -> /2
- [x] inti-ui 2: Caster.fxWindow, hitWindow -> /2
- [x] inti-ui 3: DISCONNECT via client.execute: HubNav, DuelClient, DuelFx, DuelAnim, QuickWheel, KultivatorClient (CRLF kept)
- [-] inti-ui 4a: BadaiPasir.java:299 (dunia/tempat) -> needs tempat-npc
- [-] inti-ui 4b: Mural.java:394 (dunia/tempat) -> needs tempat-npc
- [x] inti-ui 4c: LangitJatuh hallsAt -> /2 (init + clearAll); "not cleared on stop" was false, clearAll already on SERVER_STOPPED
- [-] inti-ui 4d: KotaAcaraItems lang -> excluded
- [x] dunia-lain 1: SeaMonsters.openWater hasChunkAt; turtle picks survival/adventure player only
- [x] dunia-lain 2: DuniaTickets LIVE.clear() on SERVER_STOPPED
- [x] dunia-lain 3: BawahPeople.applyPerk uses permanent modifier (saved before Health)
