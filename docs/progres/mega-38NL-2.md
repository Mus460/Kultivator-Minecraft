# mega-38NL-2 (wave W2, 38NL-2 NPC life integration) — checkpoint

Scope (jadwal W2 row 38NL-2 + sec 5 note): KultivatorNpc hooks (goals, schedule delegation, `pendudukId`) on top of
40I's fields; 38NL W1 hand-offs; 38TK requests (Jadwal tema data); crowd override `tonton:<x,z>` for 39F (C39.K11).
Owns: `dunia/kota/{KultivatorNpc,NpcRole,NpcAi,NpcLife}.java`, `dunia/tempat/hidup/**`, `client/dunia/tempat/hidup/**`,
`lang-src/tempat_hidup.*`. Must NOT touch: 40I's Sosok fields in KultivatorNpc (keep), renderers, other modules' files.
No Gradle. All owned files LF.

## Checklist
- [x] Read planning: jadwal sec 0/0.1/1/3/4/5, 38.N, 38.T.3.3/4.3/5.3/6.3/7.3/7.10, 38.T.14.3, C39.K11,
      mega-38NL/-0, F1 notes (kompleks band, PertemuanPil look), 38DS/40K hand-offs, kota-TK0.md
- [x] Read code: KultivatorNpc, NpcAi, NpcLife, NpcRole, all hidup/** + client hidup/**, SosokApi, GerakId/PropId,
      KompleksApi/KompleksAnchors, TemaKalender, Tema, tempat_lib titik kinds
- [x] A KultivatorNpc: resident fields `situsPenduduk`/`pendudukId` (+ setPenduduk/penduduk()), still-state no-push,
      resident goals registered (new GolPenduduk: Diam, Jalan L0/L1 with jalan nodes, Fokus look), no lonely puff for
      residents (NpcAi.check); HomeGoal/HoldGoal skip residents
- [x] A2 memory keys hd_situs/hd_id -> fields (TubuhPenduduk, Pemanggil, Hidup); TubuhPenduduk walking rewritten
      (cekPerjalanan 20-tick arrival/stuck, langkah/arah/hentiLangkah, diam/fokus/tahanDiam/lihatKerja);
      TempatHuni: kegiatan() wrapper, tonton picks (kursiTonton), street node graph + A* rute()
- [x] B NpcAi HomeGoal/HoldGoal: residents skipped (GolPenduduk drives); NpcLife sky/treasure reactions for residents
      (once clip + re-read schedule), lightning read-only reaction (ENTITY_LOAD), temple bell bow, drum closes night market
- [x] C kegiatan wrapper `TempatHuni.kegiatan` (tonton max 8 + cheering, genderang close) used everywhere
- [x] D JadwalTema (wired in Jadwal) + JadwalData multi-file merge (`"jenis"` field or `kota_tema_<tema>[_x]` name): themed routines 38.T.3.3-7.3 (real hours, oasis heat break, iblis table), theme weather, built-in
      `tema.*` event reactions (38.T events list), JadwalData merge of several files per kind (`"jenis"` field)
- [x] E PendudukGen: compounds active (dihuni bug: sekte/kompleks was never active) + size by KompleksApi band
- [x] F HidupApi: `tubuh(..., look)` overload (PertemuanPil rivals keep their face), `kerumunan(level,pos,detik)` helper
- [x] G Lang tempat_hidup: 388 keys identical id/en (kegiatan, petir, uji.simpul); every literal/dynamic key checked
- [x] H Self-review: javac 0 errors (23 owned files + KultivatorNpc); grep: no client refs/setBlock/weather/time/
      LightningBolt spawn/static ItemStack; all owned files LF (= HEAD); lang parity OK

## Langkah berikutnya
DONE (session 3). No data/kultivator/tempat/jadwal/*.json shipped by 38NL-2 (theme agents ship theirs).

## Catatan / masalah terbuka
- Impostor already uses 40K fallback atlas via SosokRender.cadangan -> KulitCadangan (40K hand-off done in W1).

## Butuh dari modul lain
- PertemuanPil (kotabesar owner): spawn rivals with HidupApi.tubuh(level, town, NpcRole.ALKEMIS, at, yaw+180, look) and
  plate KotaNames.person(look, NpcRole.ALKEMIS) so face/sex/name match.
- 38TK-P/O/A/G/I: timpaJadwal names "tema.<id>" handled by JadwalTema.acara: kapal_datang, kapal_pergi, dewi_laut,
  bajak_laut, kafilah_agung, badai_pasir, tari_putar, lentera_air, perahu_naga, tikus_air, ziarah, cahaya_budha,
  longsor, lift_macet, pesta_darah, lelang_gelap, penyerbuan, jam_berburu (other ids: no resident change unless a
  jadwal json gives an "acara" table). Jadwal json: add "jenis": "kota_tema/<tema>" (or name kota_tema_<tema>[_x].json).
