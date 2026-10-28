# m2-FIX3 kota (audit3-kota + RakDagang from audit3-multiplayer-eksploit)

- [x] K1 GelombangBinatang: an unloaded raider no longer counts as dead (count stands still with no defender around), `lahir` rises only after a real spawn, ALLOW_LOAD refuses stale `kultivator.gelombang` raiders (only those an active raid tracks may load). ArenaDarah: ALLOW_LOAD refuses `kultivator.iblis_arena` beasts, SERVER_STOPPING ends every bout (foe removed, entry fee returned from the till). Not discarding raiders in `lupakan()`: it runs on SERVER_STOPPED, after the save; ALLOW_LOAD covers the saved ones.
- [x] K2 Teleport / Dermaga fare: charged on arrival in `pindah` (funds checked at start), so damage, a step, logout, stop and a missing level cost nothing; free ride and capital tax move with it.
- [x] K3 BalaiLelang: a defaulted bid falls back to the last NPC bid (capped by `ceiling`), no NPC bid means the lot is unsold (consignment returned); `action` refuses the seller bidding on his own lot.
- [x] K4 Hukum: a prisoner found outside any capital (respawn, teleport) loses his cell-door entry each tick, so `tempat.sel` stops being "busy"; the sentence stays and the next capital visit judges it.
- [x] RakDagang: PlayerBlockBreakEvents.BEFORE in `TokoPemain.init`: owner, sect mate, op, creative only; new lang key `kultivator.kota42.toko.gagal.rak_milik` (id + en).
