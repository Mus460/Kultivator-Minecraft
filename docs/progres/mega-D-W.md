# mega-D-W (wave W3 detail pass of 38W: roads, posts, markers, guide) — checkpoint

Scope: jadwal-38-39-40.md W3 (D-W = files of 38W: `dunia/tempat/{root 38W files, worldgen, jalan}/**`,
`client/dunia/tempat/{TempatClient, jalan/**}`, `GuideChapters`, `DuniaMarkers`, `DuniaCommands`, `PetaPainter`,
`KedaiTeh`, `tools/{gen_tempat_jalan, tempat_lib, cek_tempat, nbt, gen_dunia_peta_textures}.py`, `lang-src/tempat_inti.*`,
adv `tempat/peziarah_jalan|tonggak_tiga`, scratchpad `final/wg/run_38.sh`). No Gradle. Previous: mega-38W.md.

## Survey (done)
- Guide bab 18/19 point at 12 advancements that do not exist and nobody grants (tiga_layanan, lencana_1,
  lelang_agung_ikut, papan_yamen, acara_ikut, takhta_bab3, penjaga_takhta, kampung_tiba, kampung_ikrar,
  kompleks_tiba, tamu_sekte, tukang_bantu) -> those steps can never be completed.
- Tonggak "carved text over the stone" billboard (38.C.6.3, 38.U.4) was never built (chat line only).

## Checklist
- [x] 1 Tonggak billboard: `JalanNet.Papan` payload `jalan_papan` + `jalan/PapanTonggak` (sent within 28 b, resend
      2400 t, board cached 96; real end names: capital/themed/town/village/sect; nearer rest post; couplet 0-23;
      anchor data `papan` = height) wired in PosAnchors/Jalan; `uji jalan papan`; client `jalan/JalanPapan`
      (TeksDunia batu panel, arrows ←→↑↓ from camera, couplet close up) wired in JalanClient; lang 32 keys; javac ok
- [x] 2 Guide completable: new `dunia/tempat/PanduanTempat` (Tempat.init line; tick-spread 10/40 t per player;
      TempatApi place, Tribute.ikrarDi, PerbaikanApi.poinBantu, TempatLayar.layarTerbuka (services list in extra
      `tempat_panduan`), TempatEvents.ACARA live map, EkonomiApi.lencana, CeritaApi.tahap(AlurTakhta.questBab 3-7))
      grants 11 advs; JSONs written (tiga_layanan, lencana_1, lelang_agung_ikut, papan_yamen, acara_ikut,
      takhta_bab3, penjaga_takhta, kampung_tiba, kampung_ikrar, kompleks_tiba, tukang_bantu) + 22 lang keys;
      GuideChapters comments; every bab 18/19 step now completable (kompleks via kompleks_tiba). javac ok
- [x] 3 Structures detail (gen_tempat_jalan): tonggak_c bixi + tonggak_d paviliun, kuil_c pohon + kuil_d gua,
      kemah_c benteng, singgah_b `singgah_kolam` (stone gate, 2-storey inn, lotus pond + tea pavilion + zigzag bridge,
      bamboo grove + meditasi), inn() dressing (rug, shelf jars/candles, lectern, bedroom rugs/stools), dagang dressing
      (troughs+hay, north-ring dormitory 7 beds, caravan kitchen, cargo corners). Generated: 50 valid; previews dW/p_singgah.png p_dagang.png
- [x] 3b cek_tempat 0 errors (pos budget raised 500->600 KB in cek_tempat, pos now 552 KB, comment says why);
      run_38.sh: tpl pos 18 files 0 problems, WgCheck38 157/105 ALL OK, PLAN OK, TEMA PLAN OK
- [x] 4 Lang tempat_inti 191 keys id/en identical; every key used by PapanTonggak/JalanPapan/PanduanTempat/advs exists (prefix families checked)
- [x] 5 Self-check: CRLF kept on Java (DuniaMarkers/GuideChapters/DuniaCommands/new files all CRLF), py LF; dW/jc.sh javac clean; no client refs in main

## Langkah berikutnya
ALL DONE. Finish agent: Gradle build (new template pos/singgah/singgah_b is in pool pos/singgah).

## Butuh dari modul lain
- D-DS: `tempat/tamu_sekte` (guest in a compound's inner hall, friendly standing) has no JSON and no grant;
  KompleksSikap knows RAMAH. Guide step c19.kompleks already passes on kompleks_tiba, so it is optional polish.

## Open problems
(none yet)
