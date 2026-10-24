# mega-39F (W2, DESIGN 39.F Perseteruan antar-sekte)

Scope: jadwal-38-39-40.md W2 row 39F + section 5 note; design 39.F.0-39.F.9. No Gradle (F2 builds).
Owns only NEW files: `sekte/mandiri/seteru/**` (incl. stub Seteru.java), `client/sekte/mandiri/seteru/**` (incl. stub
SeteruClient.java), `sekte/diplomasi/DiplomasiMandiri.java`, `lang-src/sekte_mandiri_seteru.*`, advancements
`mandiri_seteru_*`, textures `sekte/mandiri/seteru/`, own generator in tools/. New files LF.

## Checklist
- [x] Read contracts: MandiriApi, MandiriBus (SETERU_*), PertemuanProvider (C39.C3; 39C Tahap.pertemuan applies result;
      NOTE 39C drops kenangan kinds not in its BOBOT table -> 39F writes its own memories itself), MandiriIds, Kegiatan
      (CEKCOK/TANTANG/TERLUKA/SUMPAH owner 39F), Pelaksana, MandiriCommands.uji/chips, MandiriConfig, Diplomasi
      (Simulasi.apply, Standing.adjust, Relasi.pakta, Tubuh.spawn/banner pkg-private -> bridge), DuelKehormatan pattern,
      DuelChallenges.startDirect, Ingatan.remember, Halls.addCandidates/storeAway, HubCards/HubNav.MURID
- [x] Contracts read: SosokApi (gerak/tatap/setLuka/setSosok), HidupApi (kerumunan/bubarkan/tatap), GerakKatalog
      (daftar 200-399, petakanAktivitas), PropKatalog.daftar(100-199), Klip builder, Wajah, EpicSettings.cards(),
      TitleCard, HudElementRegistry, DiscipleNames (72 names, kelamin), CeritaApi.kabar (tempat/kabar/*.json "teks"),
      Halls.addCandidates/storeAway/addReturnHandler, away records (core.away[].stored.disciple), TurnamenHasil
- [x] Server done: SeteruIds, SeteruConfig, TokohSekte, Perseteruan, SeteruData
- [x] Server done also: Roster, MuridInfo, Antrean (ingat/kegiatan/dompet), Teks, Tangga (ladder, endings, daily, notices)
- [x] Server done also: Kabar, TubuhSeteru, Adegan, DuelSeteru (jadwalkan/periksa/undian/dukung/tantangTetua/tick/
      tickTetua), Tawuran (jadwalkan/periksa/tick)
- [x] Server done also: Patroli (buat/hapusUntuk/hariBaru/tampilkan/diJalan/ejekGuru/usir/tick), CampurTangan (8 actions
      + restui/pisahkan, tetuaPulang, kembali = Halls return handler AWAY_SETERU), AsmaraLintas (sahabat, kekasih,
      rahasia, kawinLari, pisahkan, restui); bridge got tawarPakta/pembelot/dataTubuh; statuses BERPISAH/KAWIN_LARI
- [x] Server done also: SeteruPayloads (Layar/Aksi/Judul + sheet), Insiden (presence, 39.F.2 table, D bus + tournament),
      Pertemuan (provider PENYEDIA), PelaksanaSeteru (57-60, permit-only), SeteruCommands (uji + pemain + chips),
      Seteru (init/ticks/budget ms)
- [x] Server scratch-compiled with plain javac against scratchpad final/wg/cp.win (+ source HidupApi.java, whose
      kerumunan/bubarkan are newer than build/classes): 0 errors (output scratchpad/seteru39f/out; not Gradle)
- [x] DiplomasiMandiri bridge (insiden via Standing.adjust reason mandiri.seteru.<sebab>, pemicuBesar via Simulasi.apply,
      tubuh/panji via Tubuh, berpakta/perang/bawahan)
- [x] Client SeteruPoses (clips 250-267 + variants SY_P/SOMBONG, props 130-140 grips, map 57-60)
- [x] Client: SeteruClient (receivers, hub card MURID, HUD attach, disconnect), SeteruScreen (3 tabs, ladder, actions,
      roster cards, friends/patrol; InputConstants keys), KartuJudul (HUD 2 s, cards() setting, Minimal/thin fallback)
- [x] Server+client scratch-compile with javac: 0 errors (files list scratchpad/seteru39f/files_all.txt)
- [x] tools/gen_mandiri_seteru.py (deterministic): ikon.png 32 icons, kartu.png, 14 materials, props 130-140 (105
      elements); previews read (scratchpad/seteru39f/preview.png, props.png via tempat_iso.py) -> OK
- [x] Lang sekte_mandiri_seteru en_us + id_id: 445 identical keys, built by scratchpad/seteru39f/lang.py (literal % doubled),
      checked by cek_lang.py (placeholders) + cek_keys.py (every static key used in code exists; dynamic families
      sebab/status/tingkat/aksi/riwayat.t*/harian.mulai.*/kisah.* checked by hand). Also D reason keys
      kultivator.sekte.diplomasi.alasan.mandiri.seteru.*, hub card, kultivator.sibuk_murid.seteru
- [x] Advancements sekte/mandiri_seteru_{duel,damai,kekasih}.json (parent sekte/mandiri_jalan), rumour templates
      data/kultivator/tempat/kabar/mandiri_seteru.json (5, teks = our keys)
- [x] Self-review: design walk 39.F.1-39.F.9; fixes (no scene for feuds without a place, friends refuse brawls,
      purse cause "seteru" = 39E money-book label, memories store literal text); final javac 0 errors; all files LF
- [x] Final report

## Langkah berikutnya
Done. F2: run `python tools/gen_mandiri_seteru.py` (deterministic, already run), Gradle build B2, mergeLang.
Known simplifications (documented in code): a stolen scroll is a story beat (39F cannot write an away bag); the T5 vow
keeps the feud AKTIF with the heat frozen 10 days and ends as DENDAM when it cools; "Larang" contentment loss is a
bitter memory (no contentment writer); an eloped lover arrives as a candidate with a hall-rolled name (origin line says
why); rumours are world-wide (feuds keep a town id, not a place id).

## Butuh dari modul lain
- 39E (gen_voices/VoiceEvents): the 16 x 6 disciple lines + 2 elder lines of 39.F.7 (keys used: disciple.<v>.seteru_*,
  elder.seteru_lerai, elder.seteru_ganti_rugi; fallbacks wired through SekteSuara until they exist).
- 39E (optional): guide step c21.seteru (adv sekte/mandiri_seteru_duel); a crossed-swords glyph in Glyphs for the hub
  card (now Glyphs.SEAL); a way for 39F to record a cross-sect friend as Tokoh peran SAHABAT_LUAR (39.F.3) - no API.
- 39A (MandiriApi): a wound writer `lukai(DiscipleData, JenisLuka, sampaiHari, sebab)` so live-duel losers at home get
  extra.mandiri.luka (today: TERLUKA activity + Slowness + Gaya.LUKA_PINCANG only; journey wounds go through C39.C3);
  optionally `larang(owner, sekteId)` into the hall policy for the master's "Larang".
- 39C: BOBOT in Kepulangan ignores mandiri.seteru.* memory kinds (39F writes its own, nothing needed unless 39C wants
  them in the travel scroll).
- Halls owner: CandidateSpec name override, so an eloping lover keeps their roster name.
- F2: HidupApi.kerumunan/bubarkan (38NL-2, W2) must be in B2 (39F calls them; javac checked against the source).
