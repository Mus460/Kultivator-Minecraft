# m2-LENGKAP langit-mandiri-misi

Builder: langit-mandiri-misi. Not built with Gradle (forbidden for this run); every API was read against the sources. Not run in game.

## Checklist

### 1. Sekte Langit `langit.feriMisi` (DESIGN 37.5.5, "G mendengar SekteBus.MISSION_SENT")
- [x] `FeriPanggil.init` listens to `SekteBus.MISSION_SENT` and calls `FeriAwanEntity.misi(level, owner, team)` (only while `langit.aktif`, `feri`, `feriMisi`).
- [x] `FeriAwanEntity.misi`: a display ferry at the sky stop (`feri_atas`), up to 4 seats, only for a non-tethered tier 7+ hall with both stops, a route, a loaded stop, a player within 128 blocks and no other live ferry of the hall.
- [x] Guests are stage bodies of the away disciples (`Jelmaan.pinjam`, new, reads the away record, NPC budget checked, unsaved, unowned). They bow (`HORMAT`/`V_HORMAT_BAOQUAN`) while boarding, sit (`DUDUK_LANTAI`) while flying, step to the floor and bow again on arrival, then vanish in a puff.
- [x] Disciples cannot ride entities (`DiscipleEntity.startRiding` always answers false), so the guests are placed on their seats every tick (`seatTamu`), AI and gravity off, never a real passenger. The ferry is never saved with them, and removing the ferry (unload, stop) discards them.
- [x] A real player ferry in use is never interrupted; a player ringing for a mission ferry gets `feri.misi` ("seeing off disciples").
- [x] Test hook `/kultivasi uji langit_sekte feri_misi` (same tree as patroli/tangga): shows the ferry with up to 4 of the hall's away disciples.
- [x] Lang: `sekte_langit_gerak` id + en (`feri.misi`, `gerak.uji.feri_misi*`). Config comment on `feriMisi`.
- Design choice: only the leaving leg is shown (the design names MISSION_SENT only). The return still uses the existing arrival spot. `MandiriBus.JALAN_BERANGKAT` (an optional Mandiri departure on the ferry, 39.10.3) is not hooked: a sky hall's journey departures keep the sword platform.

### 2. Murid Mandiri teleport-station journey mode (DESIGN 39.6.2, C39.K7)
- [x] `Rencana.TELEPORT = "teleport"` next to KAKI/PEDANG/AWAN; plan key `tel {tarif, asal, kotaAsal, pulang}`.
- [x] `Tahap.susun`: a MALAS/SOMBONG leader that is not LICIK, whose policy does not forbid `teleport`, going to a town with a station (capital or themed city), takes the station when: the nearest station town lies within `mandiri.teleportAsal` (new, default 600) blocks of the hall, the road would be over 900 blocks plus twice the walk to the station, and every member's purse can pay both rides. It wins over the Cloud Passage. Fare per head and way: `min(teleportMaks, ceil(teleportDasar + d / teleportPer))`; both rides are taken from each member's journey purse at the gate (`ubahUang`, the same persistent plan record as the Cloud Passage fare).
- [x] Stages: road (foot) to the station, `teleport` stage 10 s (arah 0), the normal town visit, `teleport` stage (arah 1) at the destination station, road home. The `teleport` stage has no road events.
- [x] Refund: a plan cut short (master recall, captivity, ransom) that no longer holds the return ride gives its fare back once (`Rencana.potong` -> `kembalikanTarifPulang`). A cancelled/never-left journey was never charged (the plan holds the delta).
- [x] Departure clip: the party stands round the station disc as stage bodies (`Jelmaan.tampilkanTeleport`, through `tunjukkan`): first paying (`TAWAR` with the money pouch), then hand seals (`SEGEL_TANGAN`); the real formation pillar of 38 (`KotaBesarPayload` "teleport", via `KontrakKota.kilat`) rises for the last 3 s. Arrival clip: the flash at the landing station when the stage resolves, the town body steps out with a ring and tidies its robe (`MENGUAP_PEREGANGAN`/`V_MENGUAP_RAPIKAN`). Bodies leave at once when the stage resolves (`Jelmaan.akhiriPentas`).
- [x] `KontrakKota.punyaStasiun/stasiun/kilat` are the only new calls into 38 (nearest live disc, else the arrival point).
- [x] Story/diary: `kisah.teleport.pergi|pulang`, `harian.teleport.pergi|pulang`, `moda.teleport`, `kisah|harian.berangkat.kota.teleport` (+ variants .0-.3), id + en in `sekte_mandiri_jalan`.
- [x] `MandiriConfig.Harga.teleport*` "Reserved" comment removed (now read); `teleportMaks = 0` turns the mode off.
- Not done: capitals beyond the radius for a hall near a station (39.6.1 "ibu kota tanpa batas"): the destination cache of `Tujuan` stays 4000 blocks. No test hook (the Cloud Passage has none either).

### 3. Papan Misi PK for sect members (DESIGN 41.6.2/41.6.3)
- [x] `PapanMisi.pay` also pays PK to a member of a sect: `dasarPK(band) x rank factor x class factor kf` (half the NB base, the same numbers as the sect board) through the single PK door `GabungApi.tambahPk(.., "gabung.misi_kota")`; shown in chat (`kota.misi.hadiah_pk`, id + en in `dunia_kota`). Escort and all other kinds use the same `pay`. The board's bounty-per-day cap bounds it; no extra cap (the design asks 3/day for the sect board, which this equals).
- [x] `PapanMisi.view` adds `pk` per board entry (0 for non-members), seeded like the payout; `PapanMisi.pkOf` is public.

## Decisions where the design was silent
- Origin station = nearest station town to the hall (the hall is not a station); one walk of at most 600 blocks.
- Station towns: `kota/kota_besar` and `kota/kota_tema` (the kinds with a Teleportation Formation Station).
- The 10 % of the fare that the capital's treasury takes from a player is not taken for a disciple.
- Mission ferry returns nothing to the player (display only), so nothing to refund.

## Needs (outside my files)
- `client/dunia/kota/TownPages.Misi` (and the K7 sheet when decided): show `entry.getInt("pk")` of each board entry for sect members (the view now carries it).
- `disciple/DiscipleEntity.startRiding` always answers false, so the old `FeriAwanEntity.boardFollowers/keepRiders/board` for real disciples never seat anyone; fixing it needs an exception for `FeriAwanEntity` there (the mission show does not depend on it).
- `docs/DESIGN.md` line ~25056 ("Belum dibuat: feri untuk murid misi") and `docs/rancangan/37-sekte-langit.md`: mark `feriMisi` as built; `docs/rancangan/39-murid-mandiri.md` 39.6.2: teleport mode built.
