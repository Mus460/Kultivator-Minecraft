# F2 finish of wave W2 (38/39/40) — checkpoint

Schedule: docs/progres/jadwal-38-39-40.md section 4 (F2). Record file: this one (the schedule's jadwal-F2.md).

## Checklist
- [x] 1. Read W2 module reports (all 14 report done; reports passed in the F2 prompt)
- [x] 2. Generators run (logs scratchpad/f2): tema satelit/pelabuhan(+cerita)/oasis/air/gunung/iblis(+cerita), data
      (48 sets), TV models/textures/fx/ui, mandiri tekstur/prop/lang/seteru, sosok bagian/resep/cadangan/prop/pratinjau,
      kompat_bioma 53/71. Only blockstate-file warnings. F2 edit: gen_tempat_tema_pelabuhan districts use biome tag
      #kultivator:has_structure/kota_tema/pelabuhan (was inline vanilla list -> districts lost in Terralith)
- [x] 3. gen_voices.py NOT run: no VOICEVOX core/vvm models on this machine (scratchpad voicev/ has only mk_lang.py,
      venv lacks voicevox_core/soundfile); downloading needs the user's OK -> left for next wave (code falls back)
- [x] 4. cek_tempat FOLDERS kota_tema 5.5 -> 6.2 MB (5 x 1.2 + 0.2 satellites); cek_tempat 677 tpl/82 tata_letak/105 sets
      0 errors; cek_sosok 0/0
- [x] 5. Wire needsFromOthers (unowned files)
  - [x] a LayarTempatDasar: first sheet + hooks deferred to first init() (38TV-U: core screens crashed, subclass fields)
  - [x] b HelpPage.TOPICS += "mandiri" (39E)
  - [x] c sekte_hubungan lang sebab/ingat.mandiri_teh|mandiri_tanding + Hubungan.KNOWN (39D)
  - [x] d PertemuanPil rivals: HidupApi.tubuh(.., look) + KotaNames.person(look, ALKEMIS) (38NL-2)
  - [x] e KafilahJalan.hapus discards any lead body (themed camel/junk) (38TK)
  - [x] f DagangLayar: market roles in themed towns/capitals get Jasa "Pasar <kota>" -> market mode (fence MAKELAR_GELAP
        penadah, PEDAGANG_SEBERANG bea 2 %); issuers SYAHBANDAR/KAFILAH_BASHI/NYONYA_SUTRA/KULI_GUNUNG get the cargo
        board (KontrakMuatan.papanChat split out of bukaPapan). Before: market/board reachable only by test commands
  - [x] g LoreIndeks + gen_tempat_cerita LORE_LUAR kota_tema.iblis/oasis; LoreTempat maps kota_tema/<x> (38TK-O/I)
  - [x] h Harga BASE tema/kontrak_kepala 180 (38TK-I); TemaSitus kolam_semedi_darah -> district menara_terlarang
  - [x] i permintaan/kota_tema_gunung.json written (nobody wrote it; 38TK-G thought gen_data did)
  - [x] checked already wired: tolakTagihanIblis (Penagihan), tubuhKafilah, SosokApi.setJalan (PilihJalan/iblis),
        HidupApi.kerumunan/bubarkan (DuelSeteru), theme anchors, TemaSitus.daftarSyarat gunung, event ids vs jadwal
        acara tables; static lang key scan 0 missing (translatable + JalanIds/SeteruIds/TeksTempat/TemaUi.k)
- [x] 6. Build B2 green (attempt 2 after wiring: 3m05s)
- [x] 7. Harness: run_38.sh (+kompat_bioma pack, +new PlanCheckTema.java): TplCheck kota_tema 259/0 (all 38 folders 0),
      WgCheck38 157 structures/105 sets ALL OK, PLAN OK, TEMA PLAN OK (3 seeds; overlaps only where the verdict resolves
      them); run_all.sh (+dunia_kotatema, kompat_bioma): TplCheck 1047/0, ALL OK; langitG run_fin.sh 7/0, ALL OK, 2 palaces x4
- [x] 8. Hard rules: block writers only WorldBreaker (7) + BuildPlacer (8); LightningBolt only instanceof (Hidup, RuangUdara,
      GerakReaksi), no weather/daytime/tick-rate; src/main has no client class (Mandiri javadoc only); no static ItemStacks
      in 866 changed/new java files; 41 tick handlers checked, scans gated (% 20 etc.); 334 tracked files 0 EOL changes (python byte check),
      new files LF; no javac.*.args (build-log.txt deleted at the very end)
- [x] 9. docs/uji/FASE-38.md, FASE-39.md, FASE-40.md drafts (LF); this file is the F2 record (schedule name jadwal-F2.md)

## Status
DONE. B2 green. Nothing committed (orchestrator decides).

## For W3 (next wave must know)
- Voices: gen_voices.py NOT run (no VOICEVOX core/vvm on this machine). Keys exist in VoiceEvents; code plays fallbacks.
  Run `python -I tools/gen_voices.py <repo> <voicevox_dir> <voice_dl_dir>` once the models are back (D-ST owns it).
- D-TV: LayarBukuDagang must draw the market block (TemaLayar.Dagang PASAR rows, ops beli/jual/segel, laba.total);
  today market mode works through the chat list. Optional 38TV-F: CAHAYA uji=true preview, SPLINE 'kabel'; full
  cable-car rope needs station positions on the client.
- D-TK: market entry is the dialog Jasa tab (DagangLayar.jasa + bangunHub: roles PASAR_PERAN / PENADAH / PAPAN_PERAN);
  adjust the role sets there if a theme adds a market NPC. TemaMekanismeEntity seats still teleport riders (38TK-G wants
  serializable seat types). Dagang.bea "giok" could use Bea.suratSah (30-day pass). AdeganCerita recipes for
  tema_pelabuhan_* scenes missing (fallback card).
- D-39/D-39F: 39A wound writer MandiriApi.lukai for home duel losers; CandidateSpec name override for eloping lovers;
  optional Glyphs crossed swords, SAHABAT_LUAR tokoh API.
- Harness: run_38.sh now compiles PlanCheckTema.java (themed plan) and loads kompat_bioma; run_all.sh loads
  dunia_kotatema + kompat_bioma. Run run_38.sh first (compiles out38).
- cek_tempat FOLDERS kota_tema = 6.2 MB (5 x 1.2 + satellites); each theme still <= 1.2 MB (TEMA_BUDGET).
- LoreIndeks is generated: theme lore rows live in gen_tempat_cerita.py LORE_LUAR (texts stay in tempat_tema_<tema>).
- Generators are deterministic; blockstate-file warnings (chest facing, shelves, pots) are known and harmless.
- Write/heredoc on this machine: check new files are LF with a byte check (grep $'$' gives false hits here).

## Build log
- B2 attempt 1 (generators only, no wiring): BUILD SUCCESSFUL 3m48s, mergeLang no dups, lang fragments 0 key mismatches
- B2 attempt 2 (all wiring): BUILD SUCCESSFUL 3m05s

## Open problems
