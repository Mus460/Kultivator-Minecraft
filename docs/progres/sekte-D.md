# sekte:D — Diplomasi & Sekte Rival (DESIGN 36.2.9, 36.4 Modul D)

Status: DONE 2026-10-22 (first run). javac 25 clean for every D file (no Gradle); worldgen harness green.

## Checklist
- [x] 1 audit (notes below)
- [x] 2 server data: DiplomasiConfig, GerbangPlan, GeneratorNama, SekteNpc, Relasi, DiplomasiData (SavedData
      kultivator:sekte_diplomasi), Standing, Peristiwa, Tubuh (RIVAL bodies + vanilla sect banner)
- [x] 3 logic: Diplomasi (init, DiplomasiService C36.D1, bus listeners C36.D2), Simulasi (1 sect / simulasiDetik s,
      1 online hall / s, proximity / 5 s), Perang (SectRaids.Director, war, guards), Utusan (offers + envoy bodies +
      own envoy via Halls.storeAway), Pakta, Aksi (11 actions + reasons), KetuaSekte (DuelRules boss),
      AnggotaSekte (anchor handler "sektenpc"), GerbangSekte (placement codec, built-in pack, anchors), GerbangSektePlacement
- [x] 4 musuh/SectRaids.java (CRLF kept): Director {pickSect,name,allyDefense,onResult}, setDirector, callRaid,
      liveRaiders, nextRaidDay, raiderName(); old numbers unchanged (ally score is +0 without allies)
- [x] 5 worldgen: tools/gen_sekte_gerbang.py -> structure/sekte/gerbang_{kecil 25x20x33, sedang 37x26x45,
      besar 47x38x48}.nbt, template_pool/sekte/*, worldgen/structure/sekte_gerbang_*.json, biome tag
      has_structure/sekte_gerbang, structure tag sekte_gerbang, resourcepacks/sekte_gerbang (pack.mcmeta + 3 sets)
- [x] 5b harness scratchpad/sekteD/wg/run_d.sh: TplCheck 3 files 0 problems; PlanCheck PLAN OK; WgCheckD ALL OK
      (also iso previews scratchpad/sekteD/iso_*.png, checked)
- [x] 6 members/master bodies, boss duel
- [x] 7 DiplomasiCommands (uji perguruan diplomasi daftar|info|standing|aksi|tumbuh|perang|damai|menang|ketua|gerbang|
      simulasi|layar; player: perguruan diplomasi [sekte]) + chips
- [x] 8 DiplomasiPayloads (Request/Sheet) + client DiplomasiClient (receiver, hub card sekte_diplomasi_rival,
      scenes 180-182) + DiplomasiScreen
- [x] 9 lang-src/sekte_diplomasi.{id_id,en_us}.json (310 keys identical; scratchpad/sekteD/lang_d.py) + advancements
      sekte/diplomasi_{temu,sekutu,menang}.json
- [x] 10 wired: SekteContent line D, SekteContentClient line D
- [x] 11 self-review fixes: offer refresh, minutes left, Leluhur +10 on new relations, once-per-module-per-day dedupe
      of T/X adjust vs bus events, raider name = sect name

## Audit notes / decisions (deviations)
- Three structure sets sekte_gerbang_{kecil,sedang,besar} instead of one (a set cannot pick its structure by band).
- Sets live in built-in pack kultivator:sekte_gerbang registered only when module + strukturAktif on (else a world would
  meet an unregistered placement type).
- Gate positions are seeded purely from the world seed (worldgen thread sees only the seed); sect i is linked to Dunia
  rival group i (DuniaApi.rivals().all()) as flavour ("Berjalan bersama ...").
- Gate size band from a seeded heritage roll (kecil 45%, sedang 35%, besar 20%), start tier 1-5 correlated with it.
- Contribution is package-private: alliance "10% discount" replaced by a weekly ally PK gift (tier x 20).
- Gifts from PK only (Lumbung not used). Envoy banners are vanilla banner items (S draws no NPC cloth; no import of S).
- Member AI thinning is DiscipleEntity's own (not editable); bodies capped (6 + master per gate, 2 guards, 1 envoy/hall).
- World without a born gate: during a war the master sends a duel letter and comes to the hall.
- Error seen while compiling via sourcepath: sekte/kebun/TabelHasil.java:78 (module W, enum name() override) - not D.

## Next step
None (report sent). Finish step: Gradle build, in-game uji (DESIGN 36.7 #15).
