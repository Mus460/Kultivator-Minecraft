# mega-D-NL (wave W3, detail pass of 38NL: schedules/behaviours) — checkpoint

Scope (jadwal W3 + W2 row 38NL-2): depth, not new systems. Walk 38.N.3/38.N.4/38.N.6, 38.E.4 rows "Kehidupan NPC",
"Takut", 38.T.x.3 routines; fill thin spots in schedules, behaviours, lines, reactions.
Owns: `dunia/kota/{KultivatorNpc,NpcRole,NpcAi,NpcLife}.java`, `dunia/tempat/hidup/**`, `client/dunia/tempat/hidup/**`,
`lang-src/tempat_hidup.*`, `data/kultivator/tempat/jadwal/**`. Keep 40I Sosok fields. No Gradle. LF (check per file).

## Checklist
- [x] 1 Survey: Jadwal/KerjaPeran/TubuhPenduduk/TempatHuni/PendudukData/Pemanggil, 38.N.1-8, 38.E.4, GerakId/PropId,
      SosokApi (moods Gaya.SUASANA_*, setLuka; FUNGSI_* clips are disciple-only -> not for residents)
      Thin spots: no moods on residents; 40 clips 122-176 unused (stretch, guqin, arak, jimat, nadi, ...); titik
      kerja:meja_tulis/kerja:dupa/kerja:kipas + titik data "peran" never used; wounded = gone then back healthy; deaths
      leave no trace; greetings only for high realm/rep; chat topics fixed 9 (dunia_kota keys)
- [x] 2 Moods (SosokApi.setSuasana) in TubuhPenduduk: danger/flee TAKUT, anger MARAH, friend/sale/festival SENANG,
      mourning SEDIH, dawn/late watch MENGANTUK; sky reactions
- [x] 3 Life states: PendudukData SEHAT/PULIH/DUKA/DATANG + keadaan()/ujiKeadaan()/mati(..household..) + "datang" on
      replacement; Hidup.mati passes household; TempatHuni.keadaan map (Pemanggil cycle) + hidupnya() filter; sling via
      SosokApi.setLuka in TubuhPenduduk.pass; moods per activity (suasanaKegiatan, MOOD flag cleared in tuju).
      Player lines for the states -> step 6
- [x] 4 KerjaPeran: Kerja(+varian, selingan, selKanan) + v()/sel()/sela(); ~45 roles got interludes/once clips/new
      variants (KIPAS_TUNGKU, RACIK_HERBA, GULUNG_PIL, PERIKSA_NADI, TULIS/TEMPEL_JIMAT, SEGEL_TANGAN, MENGAJAR, guqin,
      seruling, TIANG_BERDIRI, TELAPAK_LATIHAN, SIRKULASI_QI, ASAH_PEDANG, SIKAP_*, BERSANDAR, TIDUR_MEJA...);
      selinganSekarang (12 min/h, per-resident offset) used in Jadwal.kerja + guards' stance; TempatHuni.kembar()
      twin titik types, titik data "peran" (bertanda/untuk/bebasUntuk) in kerja() and tujuan(). javac 0
- [x] 5 Jadwal: BANGUN (stretch/yawn on arrival, V_MENGUAP_RAPIKAN for officials/merchants) in every group, sleep
      V_TIDUR_MIRING by seed, KEDAI (arak; 1/4 workers, 1/2 on days 5-6; 1/3 merchants after 20), musik (seruling/guqin:
      elders, scholars), guards: sharpen at 04 (asah), terkantuk 03 (1 in 5 night guards), washing day 3 (cuci +
      V_BASUH_JEMUR), no school day 6, children DENGAR_CERITA at 19 (kursi_lantai), cultivators SIRKULASI_QI dawn +
      drill rotation; TubuhPenduduk.selaKegiatan (arak sip, yawn, TEBAS_UDARA, SEGEL_TANGAN, laugh, point). javac 0
- [x] 6 TubuhPenduduk.pemain: state lines (keadaan.<datang|pulih|duka>.0-3) then sapa() 1 in 3 (sapa.<anak|penjaga|
      pedagang|tetua|kultivator|iblis|pagi|siang|sore|malam>.0-2); chat end TEPUK_BAHU; 19 own topics TOPIK_SENDIRI
      (musim_0-3, kerja_<9>, hari_pasar, hari_libur, malam, duka_tetangga, pendatang, pulih_tetangga) x4 lines; javac 0
- [x] 7 HidupCommands `uji hidup keadaan <pulih|duka|datang|-->` preview on nearest body
- [x] 8 Lang tempat_hidup +135 keys (523 id = 523 en, LF, no collision, every literal/dynamic key present);
      Pemanggil.poseJauh knows the new seated/standing clips (far crowd)
- [x] 9 Self-review: nl38/jc.sh javac 0 errors; no block/weather/tick calls (LightningBolt only an instanceof check for
      the sky reaction, never spawned); no client refs in main; no static ItemStacks; every owned file single-EOL
      (kota/* CRLF as HEAD, hidup/** CRLF throughout); lang 525 id = 525 en, all dynamic families (keadaan/sapa/
      obrol 19 + kota.obrol incl. 3 langit_ topics) present; jadwal data 5 json valid

## Langkah berikutnya
DONE. Nothing left for D-NL; finish agent runs Gradle.

## Catatan / masalah terbuka
- New lang keys (tempat_hidup): kegiatan.{bangun,musik,terkantuk,asah,cuci,dengar_cerita,pulih,berkabung,datang,lihat_kota};
  keadaan_nama.{sehat,pulih,duka,datang}; uji.{keadaan,keadaan_tidak,keadaan_sendiri}; keadaan.{datang,pulih,duka}.0-3;
  sapa.{anak,penjaga,pedagang,tetua,kultivator,iblis,pagi,siang,sore,malam}.0-2; obrol.<19 topics>.0-3

## Butuh dari modul lain
