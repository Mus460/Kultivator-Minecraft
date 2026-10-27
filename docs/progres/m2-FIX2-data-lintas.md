# m2-FIX2-data-lintas (fixer data-lintas)

Reports: audit-data-aset.md, audit-lintas-dunia.md, audit-goals-codex.md (except the DesaClient item). Gradle not run.

- [x] L1 RingProtection: kept rings live in the saved `RescueStore` (setDirty) instead of the static `KEPT`; given back on AFTER_RESPAWN and JOIN, and never to a player who joins dead (isDeadOrDying), so quit-from-death-screen / restart / other world no longer loses or transfers rings
- [x] L2 TitikSiar: CACHE and TERKIRIM cleared on SERVER_STOPPED
- [x] L3 DistrikMalam.ADA: "found" kept for the server, "none" re-checked after 1200 ticks; cleared in `lupakan()` (SERVER_STOPPED)
- [x] L4 ObrolanWarga.bersihkan clears TAMPIL
- [x] L5 LembarKavling: SERVER_STOPPED nulls `siluetServer` and clears SILUET_CACHE
- [x] L6 GaleriUji.hapus collects entities first, then discards
- [x] D1 papan_cabang: recipe `recipe/papan_cabang.json` (planks, banner, gold ingot, stick) and `loot_table/blocks/papan_cabang.json` (self drop)
- [x] D2 lang `kultivator.peta.penunjuk.source` (dunia_peta id_id + en_us)
- [ ] D3 gabung_aturan dialogs: skipped (no natural owner, see needs)
- [x] D4 lang `entity.kultivator.gerbang_kota` (tempat_kota_besar) and `entity.kultivator.papan_hidup` (tempat_ui), id_id + en_us
- [x] D5 GuideChapters step kompleks: dead `adv:tempat/tamu_sekte` alternative dropped
- [x] G1 DuniaCommands CHIPS: six dead `model ...` chips removed (`tema model` is a different, registered node)
- [x] G4 Targeting.blockHit and hasLineOfSight use LoadedPath.ray (CRLF kept; unused ClipContext import removed)
- [ ] G3 DesaClient: not mine
