# mega-D-ST (wave W3, detail pass of 38ST) — dialog, quest, rumour, lore, voices

Scope: jadwal sec 3 W3 (D-ST = files of 38ST: `dunia/tempat/cerita/**`, `client/dunia/tempat/cerita/**`,
`data/kultivator/tempat/{dialog,quest,kabar}/**` (non-tema), `lang-src/tempat_{cerita,lore,kalimat,panduan}.*`,
`tools/gen_tempat_cerita.py` + `tools/cerita_isi_*.py`, `tools/gen_voices.py`, `voice/VoiceEvents.java`,
`dunia/kota/Kota.java` hook line). No Gradle (F3). Base: mega-38ST.md (W1 done), F1/F2 W3 notes.

## Checklist
- [x] 0 Survey leftovers (F1: kota_besar.* rumours, per-service Sumber lines, PapanHidup notices; F2: AdeganCerita
      tema_pelabuhan_* recipes; voices not generated). Found: 9 kota_besar.* templates fired by 38KS do not exist (rumour
      silently dropped); acara_jam_malam/lonceng_pagi/festival_panen have no template; `mulai_duel` uses
      DuelChallenges.challenge, which refuses every 38 role (KotaDuel profiles only PENGEMBARA/SAINGAN/PENJAGA/ABADI):
      Feng Ao, Mo Ying, young master, sect disciple, demon elder duels all fail; clan dialogs should use tanding_klan.
- [x] 1 AkibatDasar mulai_duel: fallback TandingTempat.mulai (style by role/arg, intro for TETUA_IBLIS) when
      DuelistRegistry.challengeable false; closing lines kultivator.cerita.duel.<menang|kalah>.<iblis|muda|tua>.<0-2>
- [x] 2 Clan dialogs -> tanding_klan (tuan_muda_tantang/duel/ulang); murid_dalam tanding -> tanding_sekte
- [x] 2b gen_tempat_cerita no longer deletes kabar/mandiri*.json (39 generators' files)
- [x] 3 Rumours (tools/cerita_isi_detail.py, new file kabar/kota_besar.json): kota_besar.* 9 triggers x 2-3 variants
      (ids <trigger>.<n>), acara_jam_malam/lonceng_pagi/festival_panen (7), 12 capital background lines (umum_kb_*)
- [x] 4 SumberLayanan (new, cerita pkg): service Sumber lines once per player (dialog with capital service NPC) +
      capital event Sumber lines (ACARA listener); keys kultivator.kota_besar.<layanan>.inspired
- [x] 5 AdeganCerita: recipes for tema_pelabuhan_{lintas,dewi,bajak,heisha,pelita} (+ .sub keys), Map.ofEntries
- [x] 6 Coverage (tools/cerita_isi_detail_dialog.py): dialogs for the 16 roles that had none + kuli/pelayan/petani/
      nelayan, situational variants (gate night/wanted, builder fear, throne outcome, clan flags), 3 chains with branches:
      pedang_ayah (pengawal_istana/Ou Ye), kristal_formasi (penjaga_formasi), demam_kumuh (tabib/Dou); scratch
      dry run st38/cek_isi.py (lore now loads first): 232 dialogs, 52 quests, 190 rumours, 0 errors
      (scratch st38/cov.py = per-role coverage). Theme roles without dialog are D-TK1/TK2's.
- [x] 4a SUMBER texts (23 keys, tempat_lore) in cerita_isi_detail.py; SumberLayanan.java hooked in TempatDialog.interact
      + KabarTempat ACARA listener (flags sumber.<id>; istana wrapped with kultivator.cerita.sumber.awal)
- [x] 7a gen_tempat_cerita.py ran (13:50; kabar/kota_besar.json + tempat_{cerita,lore,kalimat,panduan} written); recheck
      (session 3): all tempat_* id/en identical, no dup keys across lang-src, Sumber/duel/adegan keys present
- [x] 7b javac (scratch st38/jc.sh, client path fixed): all cerita main+client files + TandingTempat, 0 errors;
      dry run: generator output in sync (232 dialogs, 52 quests, 190 rumours, 0 key drift); line endings kept
- [x] 8 Voices: VOICEVOX core/models not on machine (download needs user OK) -> needsFromOthers; fallback lines play

DONE. Next: nothing; F3 runs Gradle.
