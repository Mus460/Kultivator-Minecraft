# mega-D-GU (wave W3 detail pass of 38GU: caves 38.C.4, sites 38.C.5, sky links 38.A #20-28) — checkpoint

Owns: dunia/tempat/gua/**, client/dunia/tempat/gua/**, tools/gen_tempat_gua.py, structure/{gua,situs}/**,
worldgen dunia_gua pack, loot chests/tempat/gua/**, lang-src/tempat_gua.*, advancement tempat/{murid_pertapa,
pewaris_makam,sarang_bersih,pembebas}, one bounded hook in each 38GU old file (InheritanceTrials owns giok_warisan,
so its item model is taken here). Renderers (client/dunia/tempat/model/**) are D-MD's. No Gradle.
Harness: scratchpad dgu/ (lihat.py previews with cutaway), gu38/jc.sh javac check (not Gradle).
Budgets: structure/gua 700 KB gzip (was 572 KB), situs 300 KB (was 63 KB); <= 6 anchors/template, <= 24 titik.

## Checklist
- [x] 0 Survey: 53 templates read, previews a_*.png read; left items from F1: giok compass model, Ascension name on
      Batu Catatan (#26), Panggung Bintang wide camera (#20)
- [x] 1-4 gen (tools/gen_tempat_gua.py, section "detail pass (D-GU)"): 20 new templates (73 total), pools weighted
      base + variant: pertapa ruang_meditasi_air / bilik_kitab / kebun_jamur; makam_warisan_<1-3>
      lorong_a_relung / lorong_b_jembatan / lorong_c_persembahan (weight 2); sarang_binatang/sarang_rusuk;
      sarang_iblis blok_sel (6 cells, same captive geometry) / kebun_racun; gua_urat_roh/kemah; cermin_hati/kolam_bakau;
      situs puncak_petir_awan / balai_bintang / mata_air_lumut (same anchor contracts). G.simpan hidden-rock pass
      (hidden mix rock -> main rock) cut gua gzip 713 -> 694 KB (budget 700 KB).
- [x] 5 gen run clean (73 valid, lot rules ok); cek_tempat: no gua/situs errors (gua 694 KB, situs 128 KB; the 6
      kampung campfire errors are D-DS's); previews dgu/b_*.png c1-c4.png read; TplCheck gua 67 / situs 6 = 0
      problems; WgCheck38 (private copy dgu/wgpack) 157 structures ALL OK
- [x] 6 Java (found done on disk from a cut-off session): PuncakPetir.nearby follows Abadi.inAscension/ascended ->
      "|naik" row; PanggungBintang.bencana -> GuaPayload.kamera -> GuaClient.kamera (DuniaScenes.world/pullback/card,
      stamp fallback). giok_warisan compass: gen_tempat_gua.py giok_kompas() (--giok) reads giok_warisan.png, 32
      frames giok_warisan_00..31 + models, items/giok_warisan.json condition lodestone_tracker -> compass range_dispatch
      (preview dgu/giok.png read, amber spark visible)
- [x] 7 lang (tempat_gua 129 -> 192 keys, en/id identical, same order): petir naik_mulai/naik_terukir(.kartu)/
      baris_naik, kamera.bintang; Gua.acak(key,n) variant picker; variants sarang.bangun_serigala/laba .0-2,
      iblis.tawanan_terima_kasih .0-4, tetua_ancam .0-3, makam.pesan.<tier>.0-2, urat.bangun .0-2, cermin.tawar .0-3
      (old single keys renamed to .0); lore.<nama>.0-3 for all 9 places, one shown on every entry card (Gua.kartu)
- [x] 8 javac check gu38/jc.sh clean (fixed acak -> MutableComponent); lang parity 192/192; all touched files LF (as in git)

## Langkah berikutnya
DONE. All steps checked; only the F3 notes below remain (Gradle by F3).

## Butuh dari modul lain
- F3 (cek_tempat.py owner): gua budget is near its cap (694/700 KB) while situs uses 128/300 KB; 38.C.10 gives
  gua+situs 1 MB together: suggest FOLDERS gua 760_000, situs 240_000 for headroom (optional).

## Open problems
