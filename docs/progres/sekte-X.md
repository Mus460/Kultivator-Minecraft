# sekte:X progress (Modul X — Misi luar & kepulangan, DESIGN 36.2.7 + 36.4 Modul X)

Run 1 (2026-10-22): started fresh (no earlier files). STATUS: DONE (javac 25 clean, no Gradle).

## Checklist
- [x] 1 Read shared core (sekte/**, sekte/api MisiService/MisiHasil/SekteBus/Sikap, Expeditions, Halls, MissionPage, Envoys)
- [x] 2 Server sekte/misi: MisiConfig, TemplatMisi (12 + Jenis + Pendekatan + Flags), MisiData, JalanCerita, TimMisi,
      Kepulangan, Misi (init + MisiService + tick + listeners DISCIPLE_LEFT / RIVAL_SECT_ACTION(SERGAP_MISI))
      + Expeditions.java: class public, facades lootTable(type,tier,rnd) and releaseErrands(owner,soul) only
      + SekteContent X line wired
- [x] 3 Item GulunganLaporan (kultivator:gulungan_laporan, not in tab, lazy stack) + MisiPayloads (Aksi/Keadaan/Laporan)
      + MisiCommands (uji perguruan misi kirim/selesai/hasil/laporan/pulang/papan/info; perguruan misi)
- [x] 4 Advancements data/kultivator/advancement/sekte/misi_pulang.json, misi_bawa.json
- [x] 5 Client client/sekte/misi: MisiClient (receivers, pose ACT_MUSTER + prop, scenes 160/161, hub card SEKTE),
      MisiPose, MisiTab, MisiLaporanScreen; MissionPage tabs 5 "Misi Cerita" / 6 "Kabar Misi", PAGE=6, selectStory()
- [x] 5b Assets via tools/gen_sekte_misi_assets.py (checked with Read): textures/item/sekte/misi/gulungan_laporan.png,
      textures/item/fx/sekte_misi_bundle.png, models/item/gulungan_laporan.json, models/item/fx/sekte_misi_bundle.json,
      items/gulungan_laporan.json, items/fx/sekte_misi_bundle.json
- [x] 6 Lang lang-src/sekte_misi.{id_id,en_us}.json (290 keys, identical, no duplicates elsewhere) via tools/gen_sekte_misi_lang.py
- [x] 7 SekteContent + SekteContentClient one line each
- [x] 8 javac (scratchpad/misijc/jc.sh, own args file) clean for all X files + MissionPage + Expeditions; all files LF

## Design decisions
- Hall section sekte_misi: papan, aktif, efek (per-soul share for the return handler), datang (arrivals to line up),
  riwayat (8 reports), dicari (DISCIPLE_LEFT MENGEMBARA/MINGGAT), tertunda (reports for an offline master), seq, selesai.
- Away via Halls.storeAway(reason "sekte_misi", info{mid}) + addReturnHandler (C36.X3); recall moves return_at of the
  record in Halls.section(owner,"core").away (same compound as core, as Envoys uses it).
- Stage chance: DESIGN formula + X's team term +0.02/extra member, -0.04 per member already hurt; Penakut halves own
  injury chance; Licik member 10% extra loot roll; grade = successful stages (3 = legendary).
- Homecoming: team appears jarakGerbang blocks beyond the arrival anchor (master online), walks in, ACT_MUSTER 10 s,
  leader greets + beat 160 (161 legendary) for a master within 64 blocks, then the report scroll is handed over.

## Open problems / for the finish step
- Watch the gate walk in game (uji perguruan misi kirim kawal tegas <nama>; uji perguruan misi selesai).
- The story tab's roster uses the Misi tablet's sect-event data (as the other Misi sections do).

## Next step
None (job finished).
