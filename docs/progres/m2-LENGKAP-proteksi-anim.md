# m2-LENGKAP proteksi-anim

- [x] 1. `util/Kepemilikan.java`: `jaga(Block, Pemilik, kunciPesan)` + one lazy `PlayerBlockBreakEvents.BEFORE` (map lookup by block); `boleh()` = no owner, owner, sect mate (SectData), op, creative.
- [x] 1a. Guarded: Mata Formasi (registered cores only, `Formasi.init`), Batu/Urat Pusaka (`TitahMonuments.ownerAt`, live records only, `PusakaLangit.init`), Plakat Pelindung (`Desa.init`, owner of the pledge record), Papan Nama Toko, Meja Kasir, Papan Cabang (`TokoPemain.init`).
- [x] 1b. Reused: rack guard in `TokoPemain` (same text key as before), `Halls.mayBreakStele` (own message kept).
- [x] 1c. Lang: `lang-src/kepemilikan.{id_id,en_us}.json`.
- [x] 2. VillageGuards far stage body: a stored guard (guardTag) of a pledged village is shown through `Jelmaan.tunjukkan` at its post ("desa:owner:id", BERDIRI_JAGA + tombak, or DUDUK_LANTAI + LUKA_PARAH while it rests). Also for offline owners (`Desa.tickServer` ticks them with player null).

Decisions (design silent):
- A sign inside a shop answers to the shop owner, not the placer (a stranger's sign in your shop can be pulled down); outside a shop the placer.
- Free blocks (no owner, unregistered eye, plaque without a record, revoked/expired monument) break as vanilla. Pistons/explosions are not player breaks and are not covered.
- Stage bodies need Murid Mandiri running (`Perjalanan.berjalan()` inside `tunjukkan`), like every other stage. After a village is forgotten its stage lapses within the 100 tick ttl.
- Not built: no /kultivasi uji hook (no similar hook exists for guards).
