# mega-39A-0 (W0, skeleton for DESIGN 39 + 39.F tie-ins) - SELESAI

Scope: jadwal-38-39-40.md W0 row 39A-0 + section 5 note; design 39.12 (39A step 1), 39.12.1, 39.12.2, 39.F.9.
No Gradle run (finish agent F0 builds). New worlds only. All touched old files are LF (checked), new files LF.

## Decisions
- `SekteIds.MODULES` stays the ten 36 modules (epic beat ranges 100-199 and `SekteCommands.modules`/`liveModules` depend
  on index/count); 39 gets its own `SekteIds.MANDIRI` + `SekteContent.on("mandiri")`; `uji perguruan mandiri modul`
  reports what landed.
- ACT 40-60: holding = 40-45, 48, 51, 53-60; pose-only = 46, 47, 49, 50, 52; sit-meditating = 45, 54, 56. Not saved
  (save/load lists only 13-20 of the sekte ids, unchanged). Helpers `SekteIds.mandiriActivity/mandiriHolding/mandiriSits`.
- CheatHelp group id `perguruan_mandiri` (lang key needs no space), chips = live `MandiriCommands.CHIPS`.
- Each 39 sub-package stub has `registerContent()` (K1, always) + `init()` (switch-gated) called from `Mandiri`.
- Enum display keys live under `kultivator.sekte.mandiri.inti.*` (no clash with 39C's `...mandiri.jalan.*` prefix).
- SavedData ids reserved: `kultivator:mandiri`, `kultivator:mandiri_seteru` (`MandiriIds.DATA_*`).
- `Mandiri.init` wires `DiscipleEntity.addModeListener(MandiriApi::onPerintah)` (39.7.2 order pause); DESIGN 39's own
  mode changes go through `MandiriApi.setModeSendiri` so they are not taken as orders.

## Checklist
- [x] Read schedule, design 39.0-39.12, 39.F, 40.5.13; existing SekteContent/SekteIds/DiscipleEntity/CheatHelp/SekteContentClient
- [x] sekte/mandiri/api/**: JalanHidup, JenisCita, Dorong, IzinKegiatan, ModeMandiri, Kehatian, JenisJalan, Kegiatan,
      Pelaksana (C39.A5), Kebijakan (+Perseteruan row), JenisLuka, EntriHarian, Tokoh, HasilJalan, PasarMurid (C39.K1),
      GildaMurid (C39.K4), TujuanProvider, PertemuanProvider (C39.C3), MandiriIds (schema C39.A4/payload/adv names),
      MandiriBus (C39.A2 + SETERU_MULAI/NAIK/AKHIR), MandiriApi (C39.A1 + addPertemuanProvider)
- [x] MandiriKegiatan (ACT 40-60 constants + Pelaksana registry, empty)
- [x] MandiriApi real parts: extra.mandiri reads, purse `ubahDompet` (clamp, money-book hook), bag (ItemStack codec with
      registry ops, 9 slots), order pause `onPerintah`/`setModeSendiri`/`setBebas`, policy (hall + overlay), setters
- [x] Mandiri (all 5 sub-module lines active), MandiriConfig (skalaXp 1.5, bolehGugur false, all keys of 39.10.4 + C39.K7
      prices), MandiriCommands (CHIPS, `uji`/`pemain` sub-tree registry, `uji perguruan mandiri [modul]`)
- [x] stub inits tumbuh/Tumbuh, jalan/Perjalanan, kerja/Kerja, cerita/Cerita, seteru/Seteru (registerContent+init)
- [x] util/SibukMurid (full: register/addProvider/why/whyExcept/busy/text + 16 built-in reasons)
- [x] old: SekteIds (MANDIRI, EXTRA_MANDIRI, ACT 40-60 helpers), SekteContent (umum.mandiri, on(), registerContent +
      init lines), DiscipleEntity (Role.JELMAAN last, addModeListener in setMode, addGoalInstaller at end of
      registerGoals, holding/sitting 40-60, JELMAAN fights nobody + untouchable), CheatHelp (group perguruan_mandiri),
      SekteContentClient (MandiriClient line)
- [x] client stubs client/sekte/mandiri/MandiriClient (calls SeteruClient), client/sekte/mandiri/seteru/SeteruClient
- [x] lang-src/sekte_mandiri_inti.{en_us,id_id}.json: 161 identical keys, no collision with other fragments
      (generator: scratchpad/mandiri39a0/gen_lang.py)
- [x] Self-review pass (imports, MC APIs verified in mcsrc: CompoundTag get*Or/store/read with ops, ListTag,
      ItemStack.CODEC/split/grow/shrink, HolderLookup.Provider.createSerializationContext, GoalSelector, getGameTime)

## Langkah berikutnya
None for 39A-0. W1 owners: 39A fills planner in sekte/mandiri root; 39B/C/D/E/F take their stub files.

## Butuh dari modul lain
- F0 (DiscipleLook has no W0 owner; B0 will not compile without it): `client/disciple/DiscipleLook.nameTag` has an
  exhaustive `switch (disciple.syncedRole())` without default; `Role.JELMAAN` breaks it. Change `case DISCIPLE -> {` to
  `case DISCIPLE, JELMAAN -> {` (the town body shows the disciple's own tag; it copies the synced look).
- 39A (W1): `addModeListener` fires for every `setMode` caller (rituals: SoulSeal, Heir, Calamity, SectRanks, P sulk in
  Kepribadian/KepergianMurid, dragons); all count as orders now. Refine if a pause after a sulk/ritual is unwanted.
- 39C (W2): `Disciples.onUseEntity` returns SUCCESS for every non-DISCIPLE body, so the master's right-click on a
  JELMAAN needs a `UseEntityCallback` registered in a phase ordered before `Event.DEFAULT_PHASE`.
- 39E (W2): guide chapter is bab 21 (`MandiriIds.BAB_PANDUAN`), not 20.
