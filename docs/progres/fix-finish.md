# fix:finish (final pass after M1-M6)

## Checklist
- [x] 1. Read fix-M1..M6 progress + needsFromOthers list
- [x] 2. Wire needsFromOthers (unowned files / cross-module leftovers)
  - [x] VoiceEvents LANGIT group + voice.langit.* lang removed; dunia_peta Bab 3 dan 13; Payloads javadoc; c11.kota_abadi direction
  - [x] lencana tooltip_rumour (KotaItems + dunia_kota); KedaiTeh 81; hall ware.marrow Sempurna + help.kontribusi; sectbuild tier_feature.6
  - [x] Kepribadian.giftValue uses Harta.discipleAccepts; tooltips ekonomi.nama.* (Dupa, DimensiItems, BatinItems, AlamItems)
  - [x] HuFaTribulation onRitual + join in ritual; FieldPanel medcap row + formasiui gather.tip
  - [x] XpSources.milestone callers: TowerRun (first clear %10), HallRun, CoreRun.resealed (once per seal id ms_seal_<id>), Courts.pass, DarahJiaoItem
  - [x] lang renames: c15.tetua, sekte_misi hub, duel 'Gulungan ›', PANDUAN/README 'menu utama' -> Gulungan Kultivasi (2.1 anchor), help.calon, help.serbuan, screen help perguruan/sekte (alam 'Pembentukan Fondasi' = wiki quote, kept)
  - [x] Sibuk keys: all ids covered (no extra registrations by M4/M6)
  - [x] PANDUAN 67 additions (M3 11/12, M6 S28/67.2/67.5, Hu Fa 67.3, Kedai Teh 81, M2 67.4); DESIGN 22.5.5/24.5/26.5/36 kebun
- [x] 3. Gradle build until BUILD SUCCESSFUL (#1 before wiring, #2 after wiring: both OK)
- [x] 4. Lang: duplicates (fixed literal newline in dunia_panduan c11.kota_abadi; 15082 keys, parity OK, no dups; sounds.json 0 missing ogg/subs, VoiceEvents 341 ids all in sounds.json) ("defined in both"), missing keys, JSON validity, voice keys
- [x] 5. Re-verify KRITIS + TINGGI: section 5 appended to audit-integrasi.md (K 2/2, T 17 FIXED + T12 PARTIAL)
- [x] 6. Self-review greps (blocks, LightningBolt/weather/time, client refs in main, lazy ItemStacks, payloads, save migration)
- [x] 7. Docs: PANDUAN (CRLF) "Perubahan keseimbangan (audit integrasi)", README line, DESIGN 22.5/24.5/26.5.2/36
- [x] 8. Final rebuild #3 BUILD SUCCESSFUL (jar build/libs/kultivator-0.1.0.jar), javac*.args none, build-log.txt deleted

## Next step
DONE. Also: PANDUAN Kantor Misi -> Kantor Persembahan (Sumber citations kept), stone PK 40,5/364,5, calamity x1,5 text; README line; audit-ux.md LF restored. Nothing committed. Build #1 (before wiring) = BUILD SUCCESSFUL.

## Open problems
- S11 '?' seals not added to the 10 Sekte screens (untested UI risk); T12 partial by design
