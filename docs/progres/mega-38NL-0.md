# mega-38NL-0 (wave W0, 38NL skeleton) — checkpoint

Scope (jadwal sec 3 W0 row 38NL-0 + sec 5 note): append to `dunia/kota/NpcRole.java` all roles of 38.N.2, then the
38.T.3.3-38.T.7.3 theme roles (38.T.14.3 list) at the END of the enum; update `townsfolk()`; new
`dunia/tempat/hidup/HidupApi.java` with the C38.NL1 signatures (`klip`/`tatap` delegate to `dev.kultivator.sosok.SosokApi`,
40.7.4). No `PropId` (that is `dev.kultivator.sosok.PropId`, 40I). No Gradle. Must not touch `KultivatorNpc`, `NpcAi`,
`NpcLife`, client kota files.

## Checklist
- [x] Read jadwal (sec 0, 0.1, 1, W0 row, sec 5), 38.N.1-38.N.4, 38.E.1-38.E.3 (C38.NL1), 38.T.3.3-38.T.7.3, 38.T.14.3,
      40.4 (rupa key = `NpcRole.id()`, fallback `kelompok.<KELOMPOK>`), 40.7.4
- [x] NpcRole: `Kelompok` enum (schedule group of 38.N.3) + `tierMax`, place javadoc; 49 roles 38.N.2 + 38 theme roles
- [x] NpcRole: `townsfolk()` (civil peaceful roles; not iblis roles, not WARGA_IBLIS), helpers (`iblis()`, `anak()`,
      `kelompok()`, `rupaKelompok()`)
- [x] `dunia/tempat/hidup/Penduduk.java` record (needed by `HidupApi.penduduk` signature)
- [x] `dunia/tempat/hidup/HidupApi.java` (tubuh real minimal spawn, klip/tatap -> SosokApi, timpaJadwal store, penduduk,
      saksi, bahaya; + readers `timpaan`, `bahayaDi`)
- [x] `dunia/tempat/hidup/Hidup.java` server package init (SERVER_STOPPED clears HidupApi state)
- [x] lang `lang-src/tempat_hidup.{id_id,en_us}.json`: `kultivator.kota.peran.<id>` for every new role (identical keys; 87 keys, no collision with other fragments, checked by script)
- [x] Self-review (imports, MC names vs mcsrc, LF, no client refs), final report

## Langkah berikutnya
NpcRole done (102 constants: 15 + 49 + 38; `Kelompok`, `Tema`, `tierMax`, `tema`, helpers `iblis/malam/anak/petarung/
rupaKelompok`). Penduduk, HidupApi, Hidup, lang done. **W0 scope DONE.** Next owner: 38NL (W1) per jadwal sec 5
(Jadwal, PendudukGen, Pemanggil, Saksi reactions, impostors via 40K `KulitCadangan`).

## Catatan / masalah terbuka
- New role tiers: 0 (non-combatant) and 5 (`PENGUASA_IBLIS`, `GLADIATOR_IBLIS` max) are clamped to 1-4 by
  `KultivatorNpc.setup`/`NpcSkills`; ranges kept in `tierMax`. `uji kota barisan` now lines up 102 roles (stops at NPC cap).
- Theme roles' "diam" flag was not in 38.T tables; chosen per workplace (office/stall/loom/lift = stationary).
- `PENGEMIS`/`PEZIARAH` (two groups in the design) use TETUA; `WARGA_IBLIS` uses PEKERJA; Jadwal may mix by hash.
- 38.T.14.3 lists `MAKELAR_GELAP` among the 39 theme roles, but it already exists (ordinal 2, black-market broker).
  Reused (no duplicate constant): 38 theme roles are added, not 39.

## Butuh dari modul lain
- **F0 (compile blocker):** `client/dunia/kota/KotaGear.java` line ~121 `kindOf` is an exhaustive switch expression on
  `npc.role()` with no `default`; the new roles break it. Add before `};`:
  `default -> switch (npc.role().kelompok) { case PENJAGA -> GUARD; case PEDAGANG -> MERCHANT; case PEJABAT -> FUTOU;
  case CENDEKIA, RAHIB -> SAGE; case KULTIVATOR -> WANDERER_GUAN; default -> FOLK_HAT + Math.floorMod(look >>> 9, 4); };`
  (38MD replaces it with the real bands 16-47 in W1). All other role switches have `default` (scanned).
- **38W-0:** `Tempat.init()` must call `dev.kultivator.dunia.tempat.hidup.Hidup.init()` (exists, 38NL-0).
- **40I-0:** `HidupApi` calls static `SosokApi.gerak(LivingEntity, int klip, int varian, int tick, int propKanan,
  int propKiri, int propPunggung)` and static `SosokApi.tatap(LivingEntity, @Nullable Entity)` (C40.I1). If the shape
  differs, F0 adapts the two calls in `HidupApi.klip/tatap`.
- **38W-0:** if `TempatClient.init()` calls a 38NL client init, use `dev.kultivator.client.dunia.tempat.hidup.HidupClient.init()`
  (38NL-0 created no client file; 38W-0 stub or F0 creates it, 38NL owns it from W1).
