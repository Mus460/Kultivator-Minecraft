# mega-38TK-0 (wave W0, tema skeleton) — checkpoint. STATUS: DONE

Scope: jadwal-38-39-40.md W0 row 38TK-0 + sec 5 note; design 38.T (38.T.9, 38.T.10, 38.T.13, 38.T.14.4).
Owns: `dunia/tempat/tema/**`, `client/dunia/tempat/tema/TemaClient.java` + `model/TemaModelClient.java`,
`client/tempat/ui/tema/TemaUiClient.java`, `lang-src/tempat_tema.*`, `docs/progres/kota-TK0.md`. No Gradle run.
Per-theme API contract for W2 agents: `docs/progres/kota-TK0.md`.

## Checklist
- [x] Study patterns (items, entities, payloads, SavedData, DuniaConfig.section, DuniaCommands.test, TempatLayar 38UI-0)
- [x] `tema/Tema.java` enum: 46 districts (grid, salts 380401-380449), ring data, priority, marker colour; satellite salts
- [x] `tema/KotaTema.java` init (config, items, entities, net, commands, 5 theme inits called actively)
- [x] 5 theme init stubs `pelabuhan/Pelabuhan`, `oasis/Oasis`, `air/Air`, `gunung/Gunung`, `iblis/Iblis`
- [x] `tema/TemaConfig.java`: 6 sections `kotaTema`, `temaPelabuhan|Oasis|Air|Gunung|Iblis`, all 38.T.13.1 fields + clamps
- [x] `tema/TemaApi.java`: C38.T1 static facade with safe defaults + `Penyedia`/`pasang` for 38TK inti
- [x] `bersama/TemaAnimasi.java` (synced varian/keadaan/mulai/nilai contract for 38TV-M)
- [x] `bersama/TemaMekanismeEntity.java` (noSave base: ikat, setKeadaan stamps start, pindah, padat/bisaDipilih/kursi)
- [x] 30 mechanism classes (P6 O5 A3 G8 I6 bersama 2) with VARIAN_/KEADAAN_ constants + javadoc (scratchpad tk0/gen_entities.py)
- [x] 3 creatures: `oasis/UntaKafilahEntity`, `oasis/KalajengkingRajaEntity`, `gunung/KeraGunungEntity` (attributes, goals)
- [x] `tema/TemaEntities.java`: 33 types, all noSave/noSummon/noLootTable, MISC, `semua()`, attributes in init
- [x] `tema/TemaNet.java`: `TemaFxPayload` `kultivator:tema_fx` (jenis, situsId, CompoundTag), kinds + data keys, 8 KB check
- [x] `tema/TemaItems.java` (44 `tema/*`), `bersama/DagangItems.java` (17 `dagang/*` + table 38.T.10.1 + `PETI`),
      `bersama/PetiDagangItem.java` (+component `kultivator:peti_dagang`), `iblis/GulunganTerlarangItem.java`
- [x] `tema/KotaTemaData.java` SavedData `kultivator:kota_tema`: bagian(tema), kota(tema, situs), inti(), Infami, Gentar
- [x] `tema/TemaCommands.java` (`uji tema` root, `daftar`, clickable help), `tema/TemaKalender.java` (`daftar`, pickers, `paksa`)
- [x] `bersama/TemaLayar.java`: 20 screens as nested key classes (schema javadoc), `daftar`/`daftarHub`/`buka` -> TempatLayar
- [x] Client: `TemaClient` (receiver, `terakhir`), `model/TemaModelClient` (NoopRenderer safety net), `tempat/ui/tema/TemaUiClient`
- [x] `lang-src/tempat_tema.{id_id,en_us}.json` 96 identical keys (items, entities, command help); no duplicates elsewhere
- [x] Registry-id collision grep (entities, payload, component): clean
- [x] `docs/progres/kota-TK0.md` contract
- [x] All new files LF; MC APIs checked in mcsrc (Entity hooks, EntityType.Builder, Mob attrs/goals, Item.Properties
      equippable/food/usingConvertsTo, CompoundTag accessors/sizeInBytes, ByteBufCodecs.COMPOUND_TAG,
      Level.getOverworldClockTime, environmentAttributes MOON_PHASE, Util.makeDescriptionId, EntityRenderers.register,
      NoopRenderer)

## Langkah berikutnya
None for W0. F0 builds. W2: 38TK inti / theme agents start from kota-TK0.md.

## Butuh dari modul lain
- 38W-0/F0: `client/dunia/tempat/TempatClient.init()` must call `dev.kultivator.client.dunia.tempat.tema.TemaClient.init()`
  (server side `Tempat.init()` already calls `KotaTema.init()`); `Tempat.init()` must run before `ModItems.init()`.
- F0: compile check of the 33 generated entity classes and TemaEntities (generic factory inference); items have no
  models/textures until 38TV-M (W2).
- 38W (W1): chip `tema` in `DuniaCommands.CHIPS`, markers 42-49, bab 20 guide (as scheduled).
