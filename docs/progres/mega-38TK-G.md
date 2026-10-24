# mega-38TK-G (wave W2, Kota Gunung 38.T.6) — checkpoint. STATUS: DONE (no Gradle; finish agent builds)

Scope: jadwal-38-39-40.md W2 row 38TK-P/O/A/G/I (G = gunung) + sec 5 note; design 38.T.6 (+38.T.2 lot rules, 38.T.8-38.T.13
gunung rows). Owns: `dunia/tempat/tema/gunung/**`, `tools/gen_tempat_tema_gunung.py`, `structure/kota_tema/gunung/**`,
`worldgen/**/kota_tema/gunung*`, `data/kultivator/tempat/{dialog,quest,kabar}/tema_gunung*.json`,
`{permintaan,jadwal}/kota_tema_gunung*.json`, `loot_table/tema/gunung/**`, `advancement/tempat/tema_gunung*.json`,
lang `tempat_tema_gunung.*`. No Gradle. Never edit inti files (`tema/*.java`, `tema/bersama/**`) or other themes.

## Checklist
- [x] 0 Study: tema W0 code, gunung stubs, TataLetak/Rencana/Distrik, tempat_lib/structgen/nbt, cek_tempat, anchors,
      38ST quest/dialog/kabar format + Syarat/Akibat/QuestTracker, HidupApi/Jadwal json, NilaiBatu, Sibuk, a generator
- [x] 1 Generator `tools/gen_tempat_tema_gunung.py`: all 9 districts + walls, 45 templates, 41 pools x3 styles, 9
      structures, 6 processors; gzip 1.035 MB (TEMA_BUDGET 1.2 MB). `--pratinjau <dir>` top-down PNGs (read: OK).
      Positions used by Java (canvas coords; world y = dasarY - 5 + Distrik.dy + canvas y, xz = district centre +
      rotate(putar, canvas - 48)): lift bawah kaki_gerbang (24,19,25) [stop x0+9=24, z zi=25]; roda besar kaki
      (24,23,32); tiang aula_herba wheel (24,37,80); lift atas puncak_emas (24,13,90); katrol teras teras_kebun x38 z3
      y FN+2=12 -> FU+1=23; katrol tebing tebing_papan x68 z78 y FS+1=5 -> 21 (ty-1=20 floor); bordes 1-3 kaki, 4-6
      aula_herba (48,5,56)/(48,11,24)/(48,17,7), 7 (48,5,56) 8 (48,11,46) 9 (48,11,23) puncak; ceruk z 84,72,60,40,28,18.
- [x] 2 Run + cek: tempat_lib 45 pass, cek_tempat no gunung errors except shared `#tempat_dirawat` tag (all themes,
      inti/F2), harness TplCheck kota_tema/gunung files=45 problems=0
- [x] 3/4 Java plan (package tema/gunung, tabs, LF). Files: GunungTata (geometry), GunungData (KotaTemaData.bagian
      "gunung": p.<uuid> player, k.<situs> town), GunungTokoh (jue_yuan tie_suo shan_ling lao_dan pei_wenzhi jing_yue
      gou_tie yao_po), GunungAnchors (module tema_gunung), KeretaGantung (controller), Katrol logic in KatrolEntity,
      Ziarah, GunungAcara (ziarah/cahaya_budha/longsor/lift_macet), AulaHerba, JasaKuli (Akibat gunung_titip/ambil),
      Kera logic in KeraGunungEntity, GunungLayar (tema_herba/ziarah/lift + PenyediaJasa), GunungCerita (goals,
      quest hooks), GunungCommands (`uji tema lift`). Entities: interact/tick in their own classes.
      FACT: Entity.startRiding refuses !type.canSerialize() (noSave) -> request inti: seat types serializable +
      TemaMekanismeEntity.shouldBeSaved()=false; my seats fall back to "carry" (connection.teleport each tick).
      Done files: GunungTata, GunungData, GunungTokoh, GunungAnchors, KeretaGantung, KeretaGantungEntity,
      KatrolEntity, HerbaTebingEntity, Ziarah, GunungAcara, BatuRuntuhEntity, JeratEntity(click->GunungCerita.lepasJerat),
      TaliTurunEntity, KeraGunungEntity, JasaKuli, GunungLayar (incl. herb hall), GunungCerita (633 l, all hooks),
      GunungCommands, Gunung.init wiring — all on disk (session 2 found them written after last checkpoint).
- [x] 3b javac smoke compile (java-runtime-epsilon javac, cp final/wg/cp.win, -sourcepath src/main/java -implicit:none,
      out scratchpad/gunung_out): 0 errors. Fixed hasTag->entityTags(); goals via TemaCerita.lapor (naik_lift
      gantung:/katrol:gunung, ziarah_selesai); grants tema_peziarah (restu), tema_penjaga_tali (gunung_gelar),
      tema_gunung_cahaya_budha, tema_gunung_tujuh_ziarah (7th climb), effect gunung_adv <ibu_peziarah|kuli_sehari|
      kera_nakal|rumput_awan|tobat_gou>; repentant Gou Tie (kota gt=1) spawns beside Lao Dan at the Pos Kuli.
- [x] 5 `python tools/gen_tempat_tema_gunung.py --cerita` (also in a full run): 5 quests (tema_gunung_tali 14 stages
      incl. branches; tema_ziarah_ibu, tema_kuli_sehari, tema_kera_nakal, tema_rumput_awan), 43 dialogs tema_gunung_*,
      kabar/tema_gunung.json (26), jadwal/kota_tema_gunung.json (cahaya_budha only), 7 adv tema_gunung_*, 4 loot
      tema/gunung/*. permintaan = inti's (DagangItems javadoc). Validator scratchpad g/cek.py: OK 0.
- [x] 6 Lang written by the same mode: lang-src/tempat_tema_gunung.{id_id,en_us}.json, 417 identical keys (all Java
      keys, tokoh, dialogs, quests, rumours, adv). Sumber is inti's key (kota_tema.gunung.sumber). No kalimat hook.
- [x] 7 Final review: grep clean (no setBlock/LightningBolt/weather/time/tick rate, no static ItemStack, no client
      refs), all files LF, javac smoke compile 0 errors after the last edit, cek_tempat: 0 gunung errors (kota_tema
      total gzip 5.69 MB > 5.5 MB budget is shared, gunung 1.035 MB < TEMA_BUDGET 1.2 MB).

## Keputusan
- Terrace heights (lot rows south->north): r0 0, r1 +6, r2 +18, r3 +24, r4 +36, r5 +42. Structure JSON lot dy holds only
  the in-district part (south lots 0, north lots +6); district dy must come from RencanaKotaTema = row x 18
  (bottom 0, middle 18, top 36). Stairs live in the LOWER lot (rise against a retaining wall at its north edge).

## Rencana data (langkah 5) — generator `--cerita` mode writes JSON
Talk events fire BEFORE dialog pick -> talk stages are goalless + dialog `quest_maju`; antar stage then a goalless
thank-you stage. Chain stages: macet, tali_putus, lao_dan, jaga(cabang tangkap->interogasi / jejak), shan_ling, jerat
(x6), kera_putih (goal tanda gunung_tali_sutra), pasang, bela (cabang suap/paksa/normal) -> berkat|berkat_paksa|
berkat_suap (ke selesai). Syarat `gunung_titipan <tujuan>` (JasaKuli) for keeper dialogs. permintaan = inti's.

## Sesi 2 progres langkah 5/6
- Java extra: GunungCerita.hadiah + effect gunung_hadiah <sarang_kera|persembahan_ziarah|bekal_kuli|rampasan_pemburu>
  (loot tema/gunung/*; sarang_kera on polite fibre, persembahan_ziarah first pilgrimage, rampasan on Gou tobat/usir);
  Syarat gunung_titipan <tujuan> + JasaKuli.siap; gunung_gelar also sets flag gelar.penjaga_tali_awan. javac clean.
- Writing story section in scratchpad g/part1.py (helpers + all Java lang keys + tokoh) -> part2 (quests/dialogs) ->
  part3 (kabar/adv/loot/jadwal/writer) then insert before `def main` of the generator, add `--cerita` mode.

## Langkah berikutnya
None (done). Finish agent: Gradle + mergeLang; re-run `gen_tempat_tema_gunung.py --cerita` only if lang changes. (old note:) step 5 data (quest ids in GunungCerita: tema_gunung_tali, tema_ziarah_ibu, tema_kuli_sehari,
tema_kera_nakal, tema_rumput_awan; tanda marks gunung_lift_macet/jaga_selesai/jerat/sarang/tali_sutra/bela_selesai).

## Butuh dari modul lain
- 38TK inti (RencanaKotaTema/PutusanTema): gunung `Distrik.dy` exactly 0 / 18 / 36 for bottom / middle / top row
  (grid z = +1 / 0 / -1); do not add terrain-variable dy (stair lots are built for these steps).

## Masalah terbuka
- Anchors use own module `tema_gunung/*` (like pelabuhan's `pelabuhan/*`), handler GunungAnchors via
  DuniaAnchors.register; shared kinds kota/*, kotabesar/layanan (penginapan, rumah_makan, bank_cabang, papan_gilda),
  kota_tema/situs|tiba|zona.
- ALAT has no `jemur`/`ikan_kayu`: drying = `kerja:rak`, wooden fish = `kerja:dupa`.
