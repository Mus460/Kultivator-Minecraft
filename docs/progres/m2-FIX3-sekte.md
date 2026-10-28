# m2-FIX3-sekte (audit3-sekte.md)

- [x] 1. Patroli.usir / DuelSeteru.tantangTetua: result now from DuelEndListeners (winner == master); cancel, draw, timeout = loss. Listeners registered in Seteru.init (Patroli.daftar, DuelSeteru.daftarTetua).
- [x] 2. DuelSeteru.bersihkan clears TETUA.
- [x] 3. SerapBatu: change that does not fit the purse cap goes to Halls.addPool(owner, rest / 2).
- [x] 4. MandiriConfig harga.teleport*: marked reserved (no teleport mode; not trivial to build).
- [x] 5. SeteruConfig.kerumunanMaks: marked reserved (HidupApi.kerumunan is outside the slice; see needs).
