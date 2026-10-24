# mega-39A (W1, 39A rest: planner, scoring, rhythm, policy, catch-up, paths, goals, temptation)

Scope: jadwal-38-39-40.md W1 row 39A + section 5 note; design 39.3, 39.4, 39.5.1 (K table for catch-up), 39.7.1-39.7.2,
39.9, 39.10.1/39.10.4 (39A commands), 39.12 (39A file list). Owns only `sekte/mandiri/**` root (+ api if needed),
`lang-src/sekte_mandiri_inti.*`, advancements `mandiri_jalan`, `mandiri_kebijakan`. No old file. No Gradle.

## Checklist
- [x] Read schedule, 39A-0 progress, design 39.0-39.5.1, 39.5.9, 39.7, 39.9-39.13, existing mandiri code
- [x] Explore old APIs (DiscipleEntity/Data, Halls, SekteApi hati/hubungan/ingatan, SectBuild.anchor ids, DiscipleHooks.addTick20,
      ReturnToPostGoal walks to activitySpot, SavedDataType pattern, MoonPhase via environmentAttributes)
- [x] api additions: MandiriIds (K_HARI, K_SAKU_HARI, K_RIWAYAT_HARI, K_KEBIASAAN, K_SUKA, K_TEBUS, K_TEGUR_HARI, KG_*, S_*, A_*),
      MandiriApi (statistik, Laju/beriXpKegiatan, Pemberita/beritahu, aula/tandai/simpanHarian/harian/kabar, tegur/puji/tebus/
      dorongJalan/bebaskan)
- [x] MandiriData (SavedData kultivator:mandiri: per-owner record, harian ring 40, kabar 30, kas 7 days, antre)
- [x] KebijakanAula (SumberKebijakan, setAula/setTumpang/setTumpangKunci/Angka, bebaskan, izinEfektif NS secret realms)
- [x] Irama (hour, rhythm curve, moon, weekday, weather, tournament/raid)
- [x] Dorongan (hourly drive update + equilibrium)
- [x] TempatCache (anchors + stele ring, 17 kinds, slots, reservations, dim guard)
- [x] AnggaranGerak (<= gerakMaks walking per hall, counted once per tick)
- [x] Skor (39.4.3 all factors + reasons, top-3 draw with temperature, companion pick)
- [x] PilihJalan (39.3.2 score, recruit draw skor^2, +0.5 switch, bonus/dorong/kebiasaan/pudarkan, adv mandiri_jalan)
- [x] CitaCita (39.3.4 goals/progress/finish/fail/refill, dukung/batalkan/tambah/selesaikan/dendam, 36 events + antre)
- [x] JalanIblis (daily delta, thresholds 60/85 overlay + bus/notice/diary, Lumbung 2%, new-moon xp x1.3, tegur/puji,
      master ritual 60 s with Fx ring violet->gold, friend nights, ancestor rite -5)
- [x] KejarKetinggalan (closed-form XP, tutup K 1.9, drives rest, events + MandiriApi.PeristiwaKejar, pocket money bayarSaku,
      default Kas writer)
- [x] PelaksanaDasar (MEDITASI 56 + seat factor, ISTIRAHAT 51, MAKAN 48, JAGA_GERBANG GUARD_HALL, BERMAIN, acara salam/tebus)
- [x] Perencana (registry via tick20, queues <= penilaianPerTick, hourly bookkeeping + daily step, gate, choose/start/end,
      breath XP, honours, salute 05:00, paksa/mulaiAcara/batal/batalSemua, gauge)
- [x] Mandiri.init wiring (setSumberKebijakan, setKas default, default Pencatat -> simpanHarian, inits of
      Perencana/PelaksanaDasar/CitaCita/JalanIblis before sub-modules so 39B-F may replace)
- [x] MandiriUji.java (39A test cmds: info, skor, jalan, cita, dorong, lompat, iblis, kebijakan, anggaran, reset + chips)
- [x] lang sekte_mandiri_inti additions (341 keys each, identical; scratchpad/m39a/addlang.py) (identical keys): ~50 missing keys found by scratchpad/m39a/keys.py
      (harian.*, ingatan.*, kabar.*, pesan.*, tebus.*, log.mode, prefix families harian.cita./jalan_awal./jalan_ganti./kejar.,
      ingatan.cita.) + uji.* command keys
- [x] advancements data/kultivator/advancement/sekte/mandiri_jalan.json, mandiri_kebijakan.json (keys inti.adv.<n>.title/.desc)
- [x] kabar args that are names now pass lang keys; MandiriApi.teks/teksKabar translate key args (convention for 39E);
      ingatan.jalan_ganti is now ingatan.jalan_ganti.<jalan> (arg "")
- [x] Self-review: all lang keys used exist (keys.py), no dup keys in other fragments (dup.py), imports exist in
      mcsrc/project (imp.py; removed unused SekteIds import in Perencana), all called method names exist (mc.py, sym.py),
      braces balanced (brace.py), all owned files LF, MandiriUji signatures checked by hand

## Langkah berikutnya
DONE. Nothing left for 39A in W1. Gradle not run (finish agent of the wave compiles).

## Butuh dari modul lain
- 39E (hub card / board news feed): build each `MandiriData.kabar` line with `MandiriApi.teksKabar(tag)` (args that are lang
  keys - `kultivator.`/`item.`/`block.`/`entity.` - are translated); diary composer may reuse `MandiriApi.teks(kunci, arg)`.
- Finish agent W1: compile (`compileJava compileClientJava jar`); new files are all under sekte/mandiri + 2 advancements + lang.
