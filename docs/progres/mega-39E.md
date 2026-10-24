# mega-39E (W2, DESIGN 39E Cerita, Layar & Suara + 39F voices + guide bab 21)

Scope: jadwal-38-39-40.md W2 row 39E + section 5 note; design 39.7.3-39.7.5, 39.8, 39.10.4 (guide/encyclopedia), 39.12
module 39E, 39.F.7 (18 voice keys), 40.7.4 (MandiriPoses: props 100-129, clips 200-249).
Owns old files: `client/disciple/DiscipleScreen.java`, `guide/GuideChapters.java`, `tools/gen_voices.py`,
`voice/VoiceEvents.java`, `client/screen/ui/Glyphs.java` (only if needed). New: `sekte/mandiri/cerita/**`,
`client/sekte/mandiri/**` (not `seteru/`), `textures/sekte/mandiri/**`, `items/fx/sekte_mandiri_*.json`, `tools/gen_mandiri_*.py`,
`lang-src/sekte_mandiri_cerita.*`, advancements `sekte/mandiri_harian`, `sekte/mandiri_cita`.
Must not touch: `SekteContentClient`, `SektePoses`, other 39 packages, 38 files. No Gradle.

## Checklist
- [x] Read schedule, design 39 (39E parts), 39A-0/39A/39B/39C progress, mandiri api, old APIs (DiscipleScreen, HubCards,
      GuideChapters, gen_voices/VoiceEvents, SekteSuara, 40G GerakKatalog/PropKatalog/KaitGerak/SosokPropLayer)
- [x] Server cerita/* (16 files; + CeritaCommands papan sends "buka" so [Lihat]/command opens the board)
- [x] Client client/sekte/mandiri: MandiriClient, HarianTeks, Halaman, Ikon, Sentuh, PitaJadwal, JalanHidupTab (4 pages),
      BukuHarianScreen, MandiriBoardScreen (4 views + "?" help), ToastMandiri (HUD), GelembungMandiri (+lokal Zzz),
      MandiriPoses (props 100-123, clips 200-212, 13 activity overrides, 7 kaits incl. 40G's mandiri:* ids)
- [x] DiscipleScreen: MAX_EXTRA_TABS 3 + `bukaTab(glyph)` (LF)
- [x] GuideChapters bab 21 (save 20, CHAPTER_COUNT 21, 8 steps c21.*, gate foundation/immortal_ascension/hall, after 14)
- [x] gen_voices.py (+24 39.8.4, +16 39.F.7, +2 elder, +heaven.langit_naik, +6 sekte_langit.*) + VoiceEvents groups
- [x] tools/gen_mandiri_tekstur.py: 8 atlases in textures/sekte/mandiri/ (preview read)
- [x] tools/gen_mandiri_prop.py: 24 prop models items/fx/sekte_mandiri_* + models/item/fx/ + textures/sekte/mandiri/prop/
      (37 mats, max 25 elements, validate 0 errors, iso preview read)
- [x] tools/gen_mandiri_lang.py -> lang-src/sekte_mandiri_cerita.* 802 keys each (372 diary), identical, 0 dups
- [x] advancements sekte/mandiri_harian, sekte/mandiri_cita
- [x] Self-review: jc.sh (cerita + client mandiri + DiscipleScreen + GuideChapters + VoiceEvents) 0 errors; all owned
      files LF; no setBlock/LightningBolt/static ItemStack; client uses only payload records/api constants

## Decisions
- Diary entry keys: full key (`kultivator.`...) or short `<kunci>` -> `kultivator.sekte.mandiri.harian.<kunci>`; variants
  `.0..n` probed on the client (Language.has), picked by PilihVarian seed (soul, hari, jenis). Args: `t:`spec `r:`realm
  `d:`dao `i:`item `n:`number, lang-key args translated, `key|a|b` nested, `ctx:<cuaca>` (added by my writer) stripped.
- Diary text composed only on the client (server has no mod lang); payload `mandiri_harian` carries entries + traits +
  goal + schedule + bag + purse. Own hall-record key `cerita` (ringkas.<soul> mirror for the board, tunda notices).
- Extension: `AksiMandiri.daftar(op, handler)` for 39C/39F ops (panggil, jelmaan) - they cannot import cerita.

## Langkah berikutnya
DONE (W2 scope). Gradle not run (finish agent). W3 polish (D-39): look at props/clips in game.

## Butuh dari modul lain
- F2/finish: Gradle compile; run tools/gen_voices.py with VOICEVOX (new events are registered and SekteSuara now picks
  them, silent until the clips exist); re-run gen_mandiri_{tekstur,prop,lang}.py is not needed (outputs on disk).
- F2 (HelpPage owner): add "mandiri" to HelpPage.TOPICS (texts kultivator.screen.help.mandiri(.title) are in my fragment).
- 39F: advancements sekte/mandiri_seteru_damai + _duel (guide step c21.seteru checks them).
- 39C: lang for kultivator.sekte.mandiri.kisah.* story keys (shown in the travel scrolls) and tujuan strings.
- D-39/D-40G (W3): tune DEPAN_KAKI offsets (kuali -2, meja -1, dupa -3), flying sword pasang (rx 90, dz 17, dy 1),
  Hu Fa sword (KANAN rx 70), pouch in lap; check 10 tablets at 240 px window height.
