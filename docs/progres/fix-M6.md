# fix:M6 "Sekte Hidup Menyambung" (+ suara 36)

Plan: docs/progres/audit-integrasi.md section M6. Findings: K1, T8, T10, T11, T12, T13, S1, S12-S17, S28, S32, R8, R11,
R12, R19, R23, R24 + register 30 voice events 36 (sekte-voices.md). User approved number changes ("Ubah angkanya").
No Gradle. Keep line endings.

## Checklist
- [x] 0. Explore code
- [x] 1. K1 registerContent() in all 10 modules (SekteContent calls before umum.aktif); payload receivers gated by on(); GerbangSekte.registerContent (placement) vs register (anchors+pack); TurnamenConfig/SeragamConfig loaded in registerContent
- [x] 2. S15 SekteIds.activityRuns + DiscipleEntity load downgrade (sulker gets modeLama via Kepribadian.modeBeforeSulk); tick20 frees SULK when !on()
- [x] 3. T10 Simulasi TANTANG fires UTUSAN (not TANTANGAN), KETUA fires nothing; S12 AntarSekte.host clears Musim pending on start
- [x] 4. T11 DiplomasiConfig.mulaiAulaLevel=3, Simulasi.early() quiet table + max 1 event/day, pickSect met|perang|tier<=lvl+2; S14 Perang.serangan/jadwal use callRaid result
- [x] 5. T12 rivalLink per id (Simulasi.linkRivals nearest region via RivalService.region, fills empty links in old worlds); AnggotaSekte publishes DuniaSites "sekte/gerbang" (SITE_KIND) on anchor load; Diplomasi.meet discovers it; DiplomasiService.meet/met/plannedGate; Tournament elite uses met D sects (Rivals.champion(Component)). GerbangPlan placement NOT moved (save compat: gates of existing worlds would move) -> M2 PetaDomains reads DiplomasiService
- [x] 6. T13 MissionPage tabs Harian/Tim/Pencari Bakat/.../Cerita/Kabar Cerita (sekte_sambung keys); Envoys+MissionPage envoy texts -> sekte_sambung.bakat.*; MisiService.carriesEnvoys/sendEnvoy, TimMisi.sendEnvoy+depart refactor, Utusan.send routes via X; Kepulangan UTUSAN standing = D formula x grade (approved number change)
- [x] 7. T8 Halls.found plays VoiceHooks.sectFounded (removed from /sect create); sects.* (CRLF) rewritten "Saudara Sekte"/"saudara"; AulaPage button "Saudara Sekte" -> /sect info
- [x] 8. S1 option (a): Generasi HEIR_REALM=DEITY_TRANSFORMATION, HEIR_STARS=0 (stars line hidden when 0)
- [x] 9. S12 LumbungOverflow.store+tell in ForbiddenGround/PrimordialArk; S13 Expeditions.refuses/REFUSES in Expeditions/Envoys/ForbiddenGround/VoidRealm/PrimordialArk/Tournament.candidates; S16 "Butuh templat N+" in sekte_kebun/paviliun/tangga/turnamen not-built texts
- [x] 10. Voices: 30 events registered in VoiceEvents (DISCIPLE_SEKTE/ELDER_SEKTE/BOSS_SEKTE; oggs+captions verified).
  Swaps via new sekte/SekteSuara (line/body/say/inner/key with fallback): Kepribadian sulk, KepergianMurid rebel/leave_night/
  submit, GiliranSosial tea, hubungan/Upacara oath+beloved, Hubungan.setKind grudge/beloved, Turnamen leitai_salam/juara/
  kalah(Voice.later 70)/elder.leitai_buka/boss.tamu_tantang, Pendakian climb_illusion/peak, paviliun/Upacara mourn+qingming,
  BelajarMandiri study, TimMisi+Utusan depart, Kepulangan abandoned/mission_report/envoy_return (arrival "left","tpl"),
  Utusan elder.envoy+envoy_return, KetuaSekte boss.sect_master(_defeat), GiliranTambang ore_found, Pertumbuhan harvest,
  Seragam robe + elder.banner (LambangData.markOnce "suara_bendera"). LaporanPagi report already in place.
- [x] 11. S28 Halls.pool/poolWeek + core "pk7" 7-slot ring in addPool/spendPool; info pool_week -> TreasuryPage line
  (kas.minggu key); TambangRoh.poolHas reads Halls.pool; mine cost tambangPKDasar 100 x lvl^1.3 (KebunConfig, old field ignored)
- [x] 12. S32 renames: sekte.jabatan.agung + sekte_tetua (why.rank_agung, memory.appointed.agung, ui.report.none) -> Tetua
  Pengatur/Steward Elder; hall ui.kantor/offer.refused/offer.cap/help.kontribusi -> Kantor Persembahan; dpower natal.heading +
  help.pusaka.title -> Pusaka Natal Murid (Sumber lines untouched)
- [x] 13. R8 KetuaSekte: Sibuk.why + DuelChallenges.selfProblem/startDirect PAIR_NPC (intro, 10%%, JURUS_BEBAS, 180 s), FIGHTS +
  DuelEndListeners (KetuaSekte.init from Diplomasi.init), settle next tick via Voice.later; [x] R11 /kultivasi sekte redirect (SekteCommands); [x] R12 "Utus ke Lelang Kota" (sekte_misi t/asal/desc);
  [x] R19 stones 2/4/6/8 + disciple keeps 1/3/9/27 PK (Kepribadian, sekte_sambung.hadiah.*); [x] R24 sekte.cmd.player.help 10 modules; [x] R23 TanggaPayloads Minta/Catatan (registerContent), Tangga.init receiver, TanggaCommands.records,
  client TanggaCatatanScreen (HubNav.openChild MURID), sekte_sambung.tangga.memuat; 
- [x] 14. PANDUAN is M1-owned (CRLF): 67.2 tambang curve + 67.5 heir/diplomasi/utusan already there; remaining text -> needsFromOthers (M1)
- [x] 15. Self-check: javac 21 parse scan of the 28 touched Java files (no syntax errors; JDK 25 absent so no symbol check),
  manual API review, lang parity (14 fragments) + every sekte_sambung key used exists, line endings unchanged

## Notes
- CRLF owned files: lang-src/sects.*.json, lang-src/dpower.*.json (edit with python, keep CRLF). Rest LF.
- sekte/** treated as M6-owned (36 core). New lang keys only in lang-src/sekte_sambung.* (helper scratchpad/addlang.py). sectevent.* not edited (M5 edits help there); code points to new keys instead.

## Next step
Done. Final report sent.

## Open problems / needsFromOthers
- M1 (PANDUAN 67, CRLF): 67.2 "Kantor Misi" -> "Kantor Persembahan"; 67.5 add: Hadiah batu roh ke murid 2/4/6/8 kepuasan
  (dulu 2/3/5/6) dan batu menjadi PK pribadi murid 1/3/9/27; Ketua Sekte NPC kini lewat Duel Pedang (intro, cek Sibuk);
  Kas aula menampilkan "7 hari: +masuk / -keluar PK"; misi "Utus ke Lelang Kota"; alias /kultivasi sekte.
- M1 (sekte_panduan c15.tetua.body): "Tetua Agung memimpin" -> "Tetua Pengatur memimpin" (jabatan, rank tetap Tetua Agung).
- M5 (glosarium/screen): R24 screen.help perintah sebut perguruan (alias sekte); sectbuild tier_feature.6 "Kantor Misi" -> Kantor Persembahan.
- DESIGN.md 36 kebun config: tambangPK (200) -> tambangPKDasar (100 x tingkat^1,3) (shared doc, not edited).
