# m2-FIX3 murid-sect

- [x] audit3-multiplayer-eksploit #2 HallBlock stele ownership: Halls.mayBreakStele + HallBlock.initBreakGuard (PlayerBlockBreakEvents.BEFORE; owner, sect mate, op, creative)
- [x] murid-sect 1 away lang keys: 13 keys in lang-src/hall.{id_id,en_us}.json
- [x] murid-sect 2 Founding preview dim: hallDimOf for FOUND/TEST/UPGRADE/sky (previewAt keeps the player's dim)
- [x] murid-sect 3 SoulSeal jade: refundJade (master online -> inventory, offline -> Storehouse, full -> drop at disciple); SERVER_STOPPING refunds running rituals
- [x] murid-sect 4 SevenFairies.clear (DisciplePowers stop handler), BreakRefund.clear (BuildEngine.stop)
- [x] murid-sect 5 Contribution invite: PK spent first, refunded (Halls.addPool) when invite fails
- [x] regresi 2 DiscipleEntity.onBelowWorld: resetFallDistance; rescueFromVoid (ground, post, master same/other dim); voidHeld (no gravity, unsaved) retried each second
