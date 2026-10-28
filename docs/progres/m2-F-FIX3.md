# m2-F-FIX3 (finish of the third audit fixes)

Build: compileJava, compileClientJava, jar -> BUILD SUCCESSFUL. cek_sosok 0 errors, cek_tempat 0 errors. Not run in game.

## Fixed (see m2-FIX3-*.md for each checklist)
- murid-sect 1-7 and regresi 2 (void rescue): away lang keys, Founding preview dimension, Soul Seal jade refund, SevenFairies/BreakRefund clears, Undang Calon order, DiscipleEntity.rescueFromVoid, HallBlock stele ownership.
- sekte 1-5: duel results from DuelEndListeners, TETUA cleared, SerapBatu change to pool, teleport* config marked reserved.
- kota K1-K4 + RakDagang ownership: raid waves, fare charged on arrival, auction NPC fallback, jailed player outside the capital, shop-rack break guard.
- dunia/inti/formasi/multiplayer: claimHallsAt cache, Camps ids, primordial scroll and beast tribulation refunds, DaoDomain level check, meditation fuel, TransportNetwork debt, Keepers orphans, tryUpgrade amethyst, DharmaForm resend, Peta rate cap.
- klien/regresi 1: ClientDisconnect replay of dropped DISCONNECT clears; KulitCache no eviction of atlases used this frame.

## Needs applied here
- sekte: HidupApi.kerumunan(level, pos, detik, maks) overload with a per-place cap (TONTON_UKURAN, cleared in lupakan and bubarkan); TempatHuni.pilihPenonton uses it; DuelSeteru passes SeteruConfig.kerumunanMaks; "Reserved" comment removed.
- dunia-inti: PapanMisi stores camp_at on accept and reopens a running camp quest on player JOIN when the camp is gone (Camps ids are in memory only).
- klien (DharmaForm resend): already done by dunia-inti.
- Build fix: GelombangBinatang and ArenaDarah imported ServerEntityEvents from the wrong package (now event.lifecycle.v1).

## Skipped
- multiplayer 5 (WillContest trusts client permille): documented client minigame, server cannot see the use key without ping-sensitive checks.
- kota: lupakan() discarding raiders (runs after save; ALLOW_LOAD guard covers it), mission mobs (intended), other player-owned blocks outside the slice.
- Limit: a camp quest reopened on JOIN uses the player's current dimension.
