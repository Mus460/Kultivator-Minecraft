# mega-T12 (W0) - one NPC-sect list (audit T12 complete)

Owner files (schedule matrix row 20): `sekte/diplomasi/**` (existing), `sekte/api/DiplomasiService`, `dunia/kota/{Saingan,
SainganData,KotaConfig}`, `dunia/peta/PetaDomains`, `sect/event/{Tournament,Rivals}`, client `SainganScreen`, lang
`dunia_kota`/`dunia_peta`/`musuh`/`sekte_diplomasi`/`sectevent` names. No Gradle.

## Decisions
- Master list = `GerbangPlan` (pure: seed, `diplomasi.jumlahSekte`, `jarakMin`). Sect `s<i>` == Saingan group `g<i>` ==
  Peta domain `sekte:s<i>` == elite pool entry == 38 compound `i`. `kota.rivalGroups` removed.
- Saingan region of `g<i>` = planned gate column of entry `i` (one function `GerbangPlan.of(seed)`).
- Saingan group name = the sect's generated name; style from kataA word + aliran; colour from the sect emblem.
- Names unique inside the plan (distinct kataA, distinct kataB when the pool allows, never a fixed name).
- Static fallbacks (`peta.domain.*`, `sectevent.rival_sect.*`, `musuh.sect.*`) have 3-word shapes the generator cannot
  produce (generator always gives >= 4 words); duplicates renamed.

## Checklist
- [x] GerbangPlan: unique names (distinct A and B), Entry.id/rivalId/name/master/emblem/warna/seal/column, Plan.entry/nearest
- [x] new `sekte/diplomasi/DaftarSekte.java` (one list readable with D on or off)
- [x] SekteNpc/Simulasi: `rivalLink()` = "g"+index, linkRivals/nearestGroup (M6 old-world fill) deleted, NBT "rival" gone
- [x] DiplomasiService helpers (master, aliran, nearest, rivalGroup, sectOfRival, color, atWar) + Diplomasi impl;
      Diplomasi.planConfig() reads the section + configures the plan even with module D off
- [x] DiplomasiPayloads "klan" = band temper (`layar.klan_watak`, needs lang + SainganData.styleName(server,id))
- [x] SainganData (groups = plan entries, Group.sect/name()/putName, styleOf, nameFrom, regionOf, styleName), Saingan
      (groupName, Dao seed, view payload pola/a/b/sekte), KotaConfig (`rivalGroups` removed)
- [x] SainganScreen rebuilds the name via `SainganData.nameFrom`
- [x] PetaDomains: domain `sekte:s<i>` per sect of DaftarSekte (gate, name, emblem ink, word seal); own 16 names removed
- [x] Tournament `eliteSects` (met & not at war, else 3 weakest of the list); Rivals int overload + SECT_COUNT removed
- [x] Lang (scratchpad t12/lang_edit.py, ran once): -kota.grup.0-11, -peta.domain.0-15(+seal), -sectevent.rival_sect.0-7,
      musuh.sect.* -> "Kultus ..."/"... Cult", +nama.a.<n>.seal (20), +layar.klan_watak, en champion.name "of the %s";
      tools/gen_dunia_kota_lang.py GROUPS removed
- [x] Saingan texts (help.saingan, saingan.semua, rival_all) say "one band per NPC sect", axes = body-refiner bands; JSON +
      generator (scratchpad t12/help_edit.py, ran once); Sumber lines kept
- [x] Callers compile check (grep): KotaCommands/KotaDuel/NpcGear (id/style/region/grudge kept), LangitNpcPlan (Entry
      accessors, Plan.entries, of/enabled kept), GerbangSektePlacement, TimMisi/Voyages/TreasureBirths (RivalService same)
- [x] MC API checked in mcsrc: ServerLevel.getServer/getSeed, Component.copy, MutableComponent.withColor

- [x] Check without Gradle: the 14 edited Java files compile with javac 25 against the previous build classes (cp.win);
      scratchpad t12/chk/UniqCheck: 0 noun/modifier collisions over 27 000 plans (n 4-12)

## Status
DONE (W0). No Gradle run. All files LF (as before).

## Next step
None. F0 builds and re-runs the harnesses.

## Butuh dari modul lain
- F0: re-run `scratchpad/langitG/wg/run_fin.sh` (PlanCheckL): GerbangPlan draws extra randoms only when a name word
  collides, so gate positions/tiers of later sects shift for some seeds; check 2 sky palaces per seed still hold.
- F0: grep lang for duplicate sect names is now trivial (kota.grup/peta.domain/rival_sect removed; rogue = "Kultus").
- F3 docs: DESIGN.md 34.5.3 (kota.grup list ~l.20467, `rivalGroups` ~l.20487) and 34.5.16 (map's own 16 domains),
  PANDUAN.md ~l.9026 (`rivalGroups` 6): describe the one list (`diplomasi.jumlahSekte`, band g<i> = sect s<i>).
- dunia/api owner (doc only): `RivalService.name` javadoc example "Keluarga Mo dari Lembah Hitam" is stale.
- musuh owner (doc only): comments in `RogueCultivatorEntity:276`, `MusuhText:25`, `MusuhPage:20` say "Sekte Bulan Darah"
  (now "Kultus Bulan Darah").
- Peta owner (optional): `PetaConfig.npcDomains` (default 8) caps domains; with `jumlahSekte` up to 12 consider default 12.
