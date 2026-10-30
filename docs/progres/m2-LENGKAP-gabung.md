# m2-LENGKAP-gabung (builder "gabung", DESIGN 41)

Not compiled, not run in game (Gradle forbidden for this task). `tools/cek_tempat.py` 0 errors, `tools/cek_cakap.py` OK, new lang fragment checked for equal keys and no duplicates against every other fragment.

## Checklist

### 1. Ward disciples ("murid titipan", 41.8.7) get their benefits
- [x] New `sekte/gabung/hidup/MuridTitipan.java`, wired from `SitusGabung.init`.
- [x] Pool x1.15: `DiscipleHooks.addXpFactor`, MEDITATION XP of a ward within 3.5 blocks of a meditation seat of its master's own compound (`KolamRoh.dekatKolam`, new). No booking, master's own session untouched.
- [x] Not attacked: `Pemeriksa.luka` counts a blow at a ward inside the sect's grounds as A1 (like a blow at a member; the owner is exempt). Damage that creatures (not players) deal to a ward inside its compound is halved (`addHurtFactor`). Disciples still never die by this.
- [x] Second home (SEKTE_TAMU): the master's sect is marked met for the hall (`SekteApi.diplomasi().meet`) on registration (`LembarKartu.murid`, `Politik.daftarkanMurid`) and every world day (`Keanggotaan.addPenyentuh`). The journey side (no failure penalty as for an ally) needs two lines in `sekte/mandiri/jalan` (see needs).
- [x] Tournament: wards of members at the lei tai enter the bracket as named disciples (`TurnamenMurid.mulai` -> `MuridTitipan.peserta`, slot id `titipan:<master>:<soul>`, a sect body fights, never the disciple itself). The master gets half the placement PK (`selesai` -> `MuridTitipan.hadiah`); a ward champion's quota goes to the master (`juara` = master uuid).
- [x] Housekeeping: a ward whose disciple was released or died is dropped daily so the `1 + rank/2` seat frees again.
- [x] Lectures (Ceramah) were already in `PaviliunJasa`.

### 2. Promotion Luar -> Dalam -> Inti -> Tetua end to end
Finding of the first audit (rank trials unreachable) was already fixed before (Promosi registers `OP_AJUKAN`, `UjianPangkat` runs the Dalam duel and the Tetua trial, `Promosi.ujiUlang` the Luar re-test). Verified the chain and fixed what was still broken:
- [x] Tree and `Promosi` disagreed on Tercatat (tree counted all PK ever, `Promosi` chore PK only): the tree now uses `Promosi.pkTugas`.
- [x] `Promosi.syarat` ignored confinement (the tree did): line `naik.kurungan` added, so a confined member cannot start a trial or ceremony.
- [x] Ceremony end: re-checks next rank, free seat, own grounds, no confinement; otherwise cancels and refunds the PK (`kultivator.gabung.naik.batal`). Leaving the sect during the ceremony ends it (`GabungBus.KELUAR`).
- [x] Rank trial leftovers (`lulus.*` flags, chore PK) survived leaving and re-joining, so a returning member could skip trials: `Keanggotaan.lepas` now clears the "pangkat" part.
- [x] Wakil had a trial flag that nothing ever set (unreachable by promotion): `UjianPangkat.dukungan` counts the elder characters (design 41.8.5 step 2: at least half at attitude >= 50) when [Ajukan Kenaikan] is used in the own grounds; success sets the flag, a second press pays and runs the ceremony. Elders that are players have no vote (the master appoints them).
- [x] NPC door: data dialog `gabung_kenaikan` (speaker `peran:tetua_sekte`, priority 50) offered to a member of that sect whose next rank is ready or lacks only its trial (`gabung.naik_siap [murid|tetua]`); choices [Ajukan kenaikan] (`gabung.naik`), [jalur Tetua] (Inti only), [Pohon Pangkat] (`gabung.pohon`), [Nanti]. New `Syarat`/`Akibat` in `MasukCerita`. Uses the existing elder clip 310 (`tetua_menilai`), no new activity.
- [x] Misleading hints "ask the Admissions Elder" (luar), "ask for the Elder Trial in the hall" (tetua), "dekati para tetua lewat layar Politik" (wakil) rewritten.

### 3. `gabung.aturan_dibaca`
Design silent (41.6.11 only says the rules are read at the oath), so the simplest reward consistent with the Balai Hukum:
- [x] New `sekte/gabung/PahamAturan.java`. The story flag (set by `gabung_aturan_<aliran>`, read at the gate) is turned into a stamp on the new membership when the player is accepted (`GabungApi.terima`) and consumed, so a later membership must read the rules again.
- [x] For 7 world days after joining every offence adds half its points to the violation record (at least 1; A2 never halved). `GabungApi.langgar` uses `PahamAturan.poin`; the history line and the Balai Hukum card show the points really added.
- [x] Member card (tab Kartu): line "Paham aturan: pelanggaran dihitung separuh (N hari lagi)" while it runs (`Kartu.ATURAN`, `LembarKartu`, `LayarKartu`, card made one line taller then). Chat line on joining.

## Files
Server: `Promosi`, `UjianPangkat`, `PahamAturan` (new), `Keanggotaan`, `Pemeriksa`, `BalaiHukum` (CRLF kept), `api/GabungApi`, `hidup/{MuridTitipan (new), KolamRoh, SitusGabung, TurnamenMurid}`, `masuk/MasukCerita`, `politik/Politik`, `ui/{GabungLembar, LembarKartu, LembarPangkat}`.
Client: `ui/layar/LayarKartu`.
Data: `data/kultivator/tempat/dialog/gabung_kenaikan.json` (new).
Lang: `lang-src/sekte_gabung_lengkap.{id_id,en_us}.json` (new, 16 keys), `sekte_gabung_ui.*` (3 hints).

## Not done / left
- The journey half of "second home" (see needs).
- Wakil support does not count player elders; the 41.8.7 hurt reduction covers creatures only.
- No `/kultivasi uji gabung ...` hook added (the existing `gabung naik`, `turnamen mulai`, `langgar` cover these paths).

## Needs (outside this slice)
- `sekte/mandiri/jalan/Tahap.java` case `SEKTE_TAMU` (about line 560): `r.hasil().putBoolean("sekteSekutu", SekteApi.diplomasi().stance(r.owner(), sect) == Sikap.SEKUTU)` -> also true when `sect.equals(dev.kultivator.sekte.gabung.politik.Politik.titipan(<server>, r.owner(), r.soul()))`, so a ward's trip to its master's sect is risk 1 and never counts as a failed trip.
- `sekte/mandiri/jalan/Tujuan.java` `sekteDikenal` (about line 160): return true before the stance test when `sect.equals(Politik.titipan(<server>, owner, <soul>))` if a soul is at hand (else leave: the sect is already met through `meet`, only a war/hostile stance of the hall would still hide it).
