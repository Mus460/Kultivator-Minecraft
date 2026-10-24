# mega-D-DS (wave W3, detail pass) — kampung, desa klan, kompleks sekte, titik kota lama — checkpoint

Owns (= 38DS files): `dunia/tempat/kampung/**`, `dunia/tempat/kompleks/**`, `desa/Tribute.java`, `tools/gen_dunia_kota.py`
(+ `structure/kota/**`), `tools/gen_tempat_kampung.py`, `tools/gen_tempat_kompleks.py`, `tools/tempat_ds_kit.py`,
`structure/{kampung,kompleks_sekte}/**` + worldgen, `resourcepacks/{dunia_kampung,dunia_kompleks}`, `lang-src/tempat_kampung.*`.
No Gradle. New worlds only. Prior work: mega-38DS.md (W1 done).

## Checklist
- [x] 0 survey: F1 wired Pledge->Tribute.vetoIkrar; PendudukGen uses KompleksApi.kompleksSitus band; tabib service
      is 38EK Jasa; tanding_klan/tanding_sekte Akibat exist but no dialog uses them (dialogs = D-ST). Thin spots by
      scratchpad dds/stats.py (furniture count) + dds/dump.py (ASCII layers): kampung rumah_tabib/bengkel/balai/sekolah,
      lumbung, kincir_air, kolam_ikan, sawah hut, kandang; klan gerbang/lapangan/aula_leluhur; kompleks makam_leluhur,
      makam_pendiri, paviliun_pandang, pelataran, rumah_tetua, aula_harta, menara_lonceng/genderang, dermaga_awan.
- [x] A1 kampung craft houses (gen_tempat_kampung.rumah_kerja): tabib drying loft/drawers/screen/consult table/flag,
      pandai brick hearth+chimney+campfire/quench/iron stock, balai dais desk/records/2 bench banks/runner/drum, sekolah
      blackboard/primers/hopscotch; house lanterns raised to f+3 (were at head height)
- [x] A2 lumbung loft+ladder+steelyard (kerja:timbangan)+sacks+cat+hoist, gudang_ikan ice floor+smoker; kincir axle
      +millstone+flour sacks; kolam drying line/baskets/lantern (gurun jars+seat); sawah stilt pondok+scarecrow; kandang
      trough+coop+gate lantern. Generator runs clean (172 tpl, 517 KB)
- [x] A3 new pieces x7 styles: rumah_panjang (2 rooms, partition door, veranda, loom, lesung; 16 titik) + warung_teh
      (kitchen counter, kettle on fire, awning, 2 low tables + table; kerja:teko/obrol); weights dusun 1/0, desa 2/1,
      desa_besar 3/2. 186 tpl, 559 KB
- [x] A4 klan: helpers _stand/_rak; gerbang benches+racks+drum+lanterns+edict board+couplets+poles; aula_leluhur
      offering table (candles were floating) + cake/melons + founding sword stand + banners + lamps + rug; lapangan
      patriarch pavilion, spectator benches (penonton), archery butts, rack, lifting stones, banner poles
- [x] B1 makam_leluhur offering slabs/fence/spirit way+lions+white poles; makam_pendiri bixi tortoise (stele was
      floating), altar, big censer, huabiao, pine grove, 3 dupa points
- [x] B2 paviliun_pandang rails/tea table/desk/bonsai/lamps; pelataran big censer, huabiao, beds, poles; taman_tiba
      plums, benches, beds, tiba point. New helper bedeng() is rng-free (K.flowerbed made pelataran differ between the
      2- and 3-row summit -> stray pelataran_2; deleted it and its 3 pools)
- [x] B3 rumah_tetua screen/wardrobe/altar/scroll/lamps/weiqi table; aula_harta pedestals, stone heaps, steelyard,
      ledger, runner, lamps; asrama_dalam meditation cushions/chest/lamp per room. menara/dermaga left as is.
      kompleks: 47 tpl, 120 pools, 824 KB
- [x] C Java/lang polish (lang writer = scratchpad ds38/lang_ds.py, reproduces repo file byte-identical)
  - [x] C1 FestivalPanen: 4 acts (AMBANG 0.08/0.32/0.58/0.85: tari, barongsai, tarik tambang, lentera), hidangan per
        gaya (sedang unchanged), panen.tutup on end; keys panen.babak.0-3, panen.regu.0-1, panen.jamuan.<gaya>, panen.tutup
  - [x] C2 AnakBerbakat: anak.datang.0-4 (CERITA=5) + anak.akar (root grade coloured)
  - [x] C3 KompleksSikap.salam(): <key>.<aliran>.<0|1> for sambut.sekutu.chat/sambut.netral.chat/peringatan.chat/
        dalam.peringatan
  - [x] C4 Klan: ejek 0-7, patriark.undang.<gaya>, patriark.sapa.0-2 (Java done; lang pending in C5)
  - [x] C5 lang_ds.py patched by dds/c5.py (231 keys, old single keys removed, no other users); javac clean
- [x] D gen_tempat_kampung 186 tpl 558 KB, gen_tempat_kompleks 47 tpl/120 pools/24 structures 824 KB (only the usual
      blockstate-property warnings); cek_tempat 717 tpl 0 errors; run_38.sh (EXTRA=ds38/out): tpl kampung 186/0,
      kompleks_sekte 47/0, WgCheck38 157/105 ALL OK, PLAN OK, TEMA PLAN OK; iso preview dds/d_warung.png read

## Langkah berikutnya
Selesai. F3: Gradle build + mergeLang (tempat_kampung 231 keys).

## Butuh dari modul lain
- D-ST: Akibat tanding_klan / tanding_sekte exist but no dialog uses them yet.
