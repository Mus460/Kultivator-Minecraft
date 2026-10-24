# mega-38ST (wave W1) — Cerita, tokoh, dialog, quest, rumor, suara, teks panduan

Scope: jadwal-38-39-40.md W1 row 38ST + sec 5 note; design 38.S, 38.B.8, 38.D.11 (texts), 38.T.12, 38.T.14.3 row ST.
Owns: `dunia/tempat/cerita/**`, `client/dunia/tempat/cerita/**`, `data/kultivator/tempat/{dialog,quest,kabar}/**`,
`lang-src/tempat_{cerita,lore,kalimat,panduan}.*`, `tools/gen_voices.py`, `voice/VoiceEvents.java`,
`dunia/kota/Kota.java` (one dialog hook line), new `tools/gen_tempat_cerita.py`. No Gradle. Voices: script only (F2 runs VOICEVOX).

## Checklist
- [x] Study: Kota.interact, KultivatorNpc (memory() runtime tag -> "tokoh"), TempatLayar/LembarKunci (Dialog, Jurnal),
      NilaiBatu, KotaData.reputation, DuniaEvents/DuniaSites, SavedData, ResourceLoader v1 (verified jar 3.0.4),
      DuniaScenes/SekteScenes, DuelEndListeners, DuelChallenges.challenge, gen_voices.py WORLD, VoiceEvents groups
- [x] CeritaConfig, CeritaData, Dialog, DialogNode, Quest, CeritaReload (written)
- [x] Isian, BarangCerita, Tokoh (23 chars), KabarTempat, Syarat (+!/semua/gagal), SyaratDasar, Akibat.semua,
      QuestTracker (full stage machine, goals, deadlines, journal helpers)
- [x] AkibatDasar, TempatDialog (+jasa providers, sapa sheet), Kalimat (+KalimatIndeks generated), JurnalCerita
      (+/kultivasi jurnal), AlurTakhta (7 chapters, listeners, chapter 6 band), AdeganPayload, CeritaApi, CeritaCommands,
      Cerita.init (death/duel/site listeners, spread tick); Kota.java hook line (LF kept)
- [x] Client AdeganCerita (8 recipes) + CeritaClient receiver
- [x] javac check (scratchpad st38/jc.sh, JDK25 javac vs F0 build classes): 0 errors in 38ST files; only KotaNames
      person(int,NpcRole)/(int,boolean) come from 40I's in-progress source (SosokGen not yet on disk)
- [x] tools/gen_tempat_cerita.py core (DSL D/N/P/Q/T/K, validation vs Java ids/clips/props/voices/items, writers,
      KalimatIndeks.java writer); tools/cerita_isi_umum.py (engine strings, 23 tokoh, 28 items, titles, arc frame, scenes)
- [x] cerita_isi_tokoh (23 general dialogs, done); cerita_isi_umum syntax fix (owner's quote)
- [x] engine adds (session 3): Cerita AlchemyFurnace finish -> peristiwa racik <item>; afterDeath kalahkan elit/monster;
      goal kinds misi_gilda + ujian (QuestTracker); Syarat makam DAO TIER (npc memory dao/tier, look fallback);
      CeritaApi docs + TEKA_TEKI=40 (keys kultivator.cerita.teka.<n>.soal/benar/salah1/salah2); generator sets updated
- [x] cerita_isi_takhta (7 chapter quests, ~30 dialogs incl. 5 support talks, aftermath, 19 arc rumours); AkibatDasar
      kabar: word "pemain" = spreader name. Dry run: scratchpad st38/cek_isi.py <modules> (0 errors)
- [x] cerita_isi_quest: 18 chains (tungku_pertama..tawanan_pulang) + offer/stage dialogs, 12 koans by watak, 14 rumours;
      generator: reserved stage ids check. Dry run 101 dialogs / 25 quests / 0 errors
- [x] cerita_isi_watak: 5 roles x 8 watak (prio -1) + fear/patriarch/rematch/disciples/demon elder; 148 dialogs, 0 errors
- [x] cerita_isi_warisan: 24 ancestors (8 Dao x 3), story 3 parts + 3 heart illusions + farewell each; dialogs warisan_<d>_<t>
      (+_pamit), syarat makam; dry run 196 dialogs / 49 quests / 0 errors
- [x] cerita_isi_kabar: 34 umum, world/place/acara/sekte/quest/warisan/perbaikan.* templates (141 total), 40 riddles;
      KabarTempat hooks SekteBus RIVAL_SECT_ACTION (arg sekte:<id>, Isian resolves) + TOURNAMENT_ENDED
- [x] cerita_isi_kalimat: 11 groups (umum covers all 10 situations) ~570 lines; returns KalimatIndeks map
- [x] LoreTempat.java (kind/district -> ids, SITE_DISCOVERED + 200-tick spread tempatDi in Cerita.tick, player key
      "lore", /kultivasi legenda [id], Akibat "lore <id>"); generator L() helper + LoreIndeks writer; wired in Cerita (s5)
      Lore ids used by LoreTempat: kota_besar(.<distrik>), gilda.{alkemis,pandai,kultivator}, kampung(.klan), kompleks,
      jalan.{tonggak,kuil,singgah,dagang,kemah_begal,penyeberangan}, gua.<x>, situs.<x>; ui keys kultivator.lore.ui.*
- [x] s5: takhta Sumber line (umum key kultivator.cerita.takhta.inspired, shown in AlurTakhta.umumkan bab 1 start / bab 7 end);
      Akibat lore = learn + notify; lore hooks in 8 tokoh dialogs (yan_wushuang, tuan_wen, ou_ye, dan_chen, lin_fei, qian, jing_xin, bao_qing)
- [x] tools/cerita_isi_lore.py: 33 entries (capital + 11 districts, 3 guilds, kampung(12-row table), klan(8), kompleks(12),
      6 caves (pertapa 10 reasons), 3 sites, 6 road posts), 18 lore Sumber lines (+1 takhta), ui keys kultivator.lore.ui.*
- [x] tools/cerita_isi_panduan.py: chapter.18/19 (+desc), c18.* 8 steps, c19.* 8 steps (title/body, %% escaped)
- [x] s5 generator run OK: dialogs 196, quests 49, rumours 141, kalimat 574, lore 33; lang tempat_cerita 2692,
      tempat_lore 179, tempat_kalimat 574, tempat_panduan 36; KalimatIndeks + LoreIndeks written; javac (jc.sh now
      -sourcepath -Xprefer:newer) 0 errors
- [x] gen_voices.py WORLD +110 lines (30 kotabesar, 20 tempat, 4 cerita, 56 tema) + READ fixes (修理 written in kana:
      READ '理' would break it); VoiceEvents groups KOTABESAR/TEMPAT/CERITA/TEMA_* registered. VOICEVOX run = F2
- [x] Self-check (s5): 4 fragments id/en identical, 0 duplicate keys across all lang-src, every literal key in Java present,
      dynamic keys (arah 8, takhta.hasil incl. BAWAAN, adegan 8 recipes, kalimat index) present; LF everywhere (old files
      Kota/VoiceEvents/gen_voices LF in HEAD and now); no setBlock/LightningBolt/weather/client refs/static ItemStack;
      cek_tempat 0 errors

## Langkah berikutnya
DONE (session 5). Rerun after content edits: `python tools/gen_tempat_cerita.py` (writes data tempat/{dialog,quest,kabar},
4 lang fragments, KalimatIndeks.java, LoreIndeks.java; keeps tema_* data of 38TK), then scratchpad st38/jc.sh.
Gradle = wave finish agent; VOICEVOX = F2. Design notes: dialog keys `kultivator.cerita.<dialog>.<node>[.<n>]`; quest keys
`kultivator.cerita.quest.<id>.{nama,ringkas,<stage>,hasil.<stage>}`; lore keys `kultivator.lore.<id>.{judul,<n>,tabel.<n>,sumber}`;
tokoh tag `npc.memory().putString("tokoh", id)`; quest items = vanilla stacks with custom_data `kultivator_cerita`.

## Butuh dari modul lain
- 38GU: fire CeritaApi.peristiwa(p,"ujian","pertapa:meditasi"|"makam:<dao>:<tier>"|"cermin_hati"); remnant soul body
  memory putString("dao",id)/putInt("tier",1-3); peristiwa(p,"bebaskan","tawanan") per freed captive. Code has 8 Dao
  (not 9): 24 warisan quests.
- 38EK: do NOT fire "racik" (38ST does from AlchemyFurnace); flags alkemis_tingkat_1 / alkemis_resep_gilda may feed the
  Alchemist rank. 38KS: lantern riddles keys (CeritaApi.TEKA_TEKI).
