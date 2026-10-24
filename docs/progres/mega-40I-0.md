# mega-40I-0 (W0, DESIGN 40 module 40I skeleton) - SELESAI

Scope (jadwal sec 3 W0 row 40I-0, sec 5 note): new `src/main/java/dev/kultivator/sosok/{Sosok, Gaya, GerakId, PropId,
SosokApi, SosokJalan, RupaKunci}.java` with full C40.I1-I3 signatures. No old file touched. No Gradle.

## Checklist
- [x] Read design 40 (40.2, 40.4.3, 40.5, 40.7), 38.N.5, 38.T.8.1, 38.O.4; code: DiscipleEntity activity API,
      SekteIds.activityRuns, LangitConfig.on, Sifat, NpcRole.id, LambangCache keys (u:/npc:), SekteNpc.id ("s<i>");
      mcsrc: Attributes.SCALE (Holder, syncable, in createLivingAttributes), LivingEntity.getAttribute,
      AttributeInstance.get/setBaseValue, Entity.getId/getUUID/level, Level.isClientSide, LevelAccessor.getGameTime
- [x] GerakId.java - generated: 1-176 names exactly 38/40 (19 reserved, ada(19)=false), ranges C40.I2 (39E 200-249,
      39F 250-299), named variants V_*, durasiSekali (once lengths), fungsi 150-164 <-> act 13-27, pack/klip/varian,
      id(String) with aliases (jemur, lentera_angkat, sikap names without prefix), Kelompok, kunciLang
- [x] PropId.java - generated: 1-88 names exactly 38 for 1-62, ranges (39E 100-129, 39F 130-149), jalurModel
      (fx/tempat/, fx/tempat/tema/, fx/sosok/), pack kanan|kiri<<10|punggung<<20, kunciLang
- [x] Sosok.java: record, pack/unpack/dari, dariBenihEntitas(UUID), aduk (SplitMix64 finaliser), 40.2.3 proportions
      (skala, skalaKepala, skalaKakiY, skalaBadanX/Y, geserBahu, lenganRamping), lang keys
- [x] Gaya.java: record, LUKA_/BUSANA_/SUASANA_ constants, pack/unpack (bits 24-31 always 0), withers, sifat helpers,
      kunciAtlas (busanaKhusus + luka 2), lang keys
- [x] SosokJalan.java: bits 1-10 = 39 paths, 11-15 extras, ALIAS map, bit/id/jalan39/kunciRupa/kunciLang
- [x] RupaKunci.java: record kunci/sekte/dao/pangkat, parse/format (max 96, case kept, safe chars),
      murid/muridSekteNpc/ketuaSekteNpc helpers
- [x] SosokApi.java: all C40.I1 methods with REAL bodies via nested `Pemegang` interface + `Waktu` timers + `tik(e)`;
      extras: setGaya, rupa, terapkanSkala, gerakSekarang, tatapSekarang, pemegang
- [x] Self-review: javac (Java 25 runtime's jdk.compiler, scratch out dir, -Xlint:all) clean; scratch test
      `scratchpad/w0_40I/t/Cek.java` round-trips 100k Sosok/Gaya, all clip/prop names, packs, aliases: ALL OK.
      All 7 files LF, no client refs.

## Decisions (contract for W1)
- Bodies join by implementing `SosokApi.Pemegang` (sosokData/gayaData/gerakData+gerakT0/propData/tatapData,
  default rupaData, waktuSosok(), default bicaraSendiri). 40I W1 makes `DiscipleEntity` and `KultivatorNpc` implement
  it, holds `private final SosokApi.Waktu sosokWaktu = new SosokApi.Waktu()`, and calls `SosokApi.tik(this)` each
  server tick; on load calls `SosokApi.terapkanSkala(this, sosok)`. KultivatorNpc may override `bicaraSendiri` to use
  FLAG_TALK.
- Timed clip = interruption with phase-kept underlay; untimed re-send of the running clip is a no-op (no phase reset);
  untimed clip during an interruption becomes the underlay. Function ids 150-164 set activity 13-27 only for a free
  DISCIPLE-role disciple and only when SekteIds.activityRuns / LangitConfig.on.
- Clip 19 is not in 38.N.5 -> reserved. 57 = basuh_pakaian (alias jemur), 72 = main_lentera (alias lentera_angkat).
- `Sosok.init()` does not exist yet: 40I W1 adds it (SosokConfig, SosokUsia, SosokCommands) with the Kultivator line.
- Lang keys referenced (fragment `lang-src/sosok.*`, 40I W1): kultivator.sosok.klip.<id>, .prop.<id>, .usia.<0-3>,
  .kelamin.<0|1>, .suasana.<0-7>, .luka.<0-4>, .busana.<0-7>, .jalan.<id>/.jalan.tidak_ada.
- Generator: scratchpad `w0_40I/gen_ids.py <out dir>` (run with -I); edit the list there, never hand-edit GerakId/PropId.

## Langkah berikutnya
None for W0. W1 40I: entity fields + Pemegang, SosokGen, SosokConfig, SosokUsia, SosokCommands, GaleriUji, lang.

## Butuh dari modul lain
- 40G (W1): give variant indexes the meanings of GerakId.V_* ; skip id 19 in `klip semua`; read prop defaults when
  DATA_PROP slot = 0.
- 40K (W1): recipe rows `peran.<RupaKunci.kunci>` with case as given (`kelompok.PEKERJA`).
- 38NL (W1): `HidupApi.klip(npc, klip, prop, tick)` -> `SosokApi.gerak(npc, klip, 0, tick, prop, 0, 0)`;
  `HidupApi.tatap` -> `SosokApi.tatap`.
