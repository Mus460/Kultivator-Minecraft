# sekte:T — Turnamen sekte (DESIGN 36.2.3, Modul T)

Status: SELESAI (run 1, 2026-10-22). javac 25 langsung bersih untuk semua file T (tanpa Gradle). Semua LF.

## Checklist
- [x] 1 Baca inti (SekteContent/Api/Bus/Config/Commands/Ids, Tournament, DuelArena, DuelResults, DuelChallenges,
      TemplateExtensions, Grounds.arena, SekteScenes, HubCards)
- [x] 2 duel/api/DuelEndListeners (baru, C36.T3) + satu baris di duel/session/DuelResults.settle
- [x] 3 server sekte/turnamen: TurnamenConfig, Elo, Musim, ArenaTempat, BabakDuel, AntarSekte, Penonton (entitas),
      TurnamenEntities, Kerumunan, TribunKomponen, TurnamenPayloads (Lingkar, Papan, Minta), TurnamenCommands, Turnamen
- [x] 4 sect/event/Tournament.java: Extension/BoutDone/Ending + setExtension, isEntrant, runningKind, whyNot,
      startNow(kind, tamu), skipBout, view; hook venue/seats/startBout/bout/finished/cancelled; tamu antar-sekte (GUESTS 4);
      angka lama tetap
- [x] 5 klien client/sekte/turnamen: TurnamenClient, PenontonRenderer + PenontonRenderState + PenontonModels (caping, guan,
      sorak), LeiTaiRenderer (cincin, halo, tirai qi, lentera, pilar + kelopak juara; LOD EffectQuality), LeiTaiHud,
      TurnamenScreen (kartu hub SEKTE "Turnamen"), resep adegan 120 Final, 121 Juara, 122 Tamu
- [x] 6 tekstur textures/sekte/turnamen/leitai_{ring,tirai,lentera,bunga}.png (tools/gen_sekte_turnamen_textures.py,
      dicek dengan Read); topi memakai pita Headwear.TEXTURE yang sudah ada; advancement sekte/turnamen_juara.json
- [x] 7 lang-src/sekte_turnamen.{id_id,en_us}.json 74 kunci identik (tools/gen_sekte_turnamen_lang.py), tanpa duplikat
- [x] 8 baris SekteContent (Turnamen.init) dan SekteContentClient (TurnamenClient.init)
- [x] 9 javac penuh + CheckT: tribun 0 bentrok, 0 kursi terhalang; T6 14 kursi/44 blok, T7 45/136, T8 48/162, T9 48/195

## Catatan
Harness: scratchpad/turnamenT (jc.sh, run.sh, src/DumpT.java, src/CheckT.java).
Penyimpangan kecil: Lingkar Lei Tai digambar dari payload (tanpa entitas penanda) — lebih ringan.
Antar-sekte: menang +10 standing (dihormati), kalah -10, menolak -3, tantangan lewat 3 hari tanpa biaya.

## Langkah berikut
Langkah finish: Gradle compileJava/compileClientJava/jar; uji sekte audit 6-9 dan pamer tingkat 7 (bersama ekstensi modul lain).

## Masalah terbuka
- CheckT hanya memuat ekstensi T; tumpang tindih dengan ekstensi modul lain dicek di langkah finish (petak berbeda menurut 36.3.5).
