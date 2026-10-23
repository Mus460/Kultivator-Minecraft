# Progres: 38.T Kota Bertema (design only)

- [x] Baca 38 penuh (B, C, N, O, S, R, M, U, D, E) — baris 1-2768 (sebelum 38.T)
- [x] Baca kode: dunia/kota, kapal (KapalClass PERAHU/SEKTE/BAHTERA, Voyages ROUTE_CROSSING 2000), dimensi/laut
      (SeaGate.enter(player, free) + blocker(player) publik, DimensiLevels.LAUT, LAUT_SEA_TOP 99), musuh/Sibuk.java
      (BUKAN util/Sibuk), RogueCultivators VARIANT_DEMONIC 3 / ROLE_*, TechniquePower.markRealmFoe :699,
      mcsrc Camel/CamelHusk/Llama/TraderLlama/Raft ada, bioma 26.3 (dappled_forest, pale_garden, sulfur_caves)
- [x] Salt terpakai: 3405xx, 36091x-36092x, 3800xx-3803xx -> 38.T pakai 380401-380452
- [x] Sumber terverifikasi: Quanzhou UNESCO 2021 (Tianhou/Mazu, Wanshou pagoda penanda navigasi, Liusheng, Zhenwu
      kurban laut); Dunhuang (Yueyaquan, Mingsha bernyanyi, Yumen/Yangguan); Zhouzhuang (Shide bundar + Yong'an
      persegi, Ming 1573-1619, Kunshan); Emei (Jinding, Samantabhadra 48 m, Baoguo awal ziarah); Huashan jalan papan
      Puncak Selatan; lentera air Zhongyuan Jiangsu/Fujian; BTTH Black-Corner Region + Black Mark City biaya masuk (WW bab 387)
- [x] Tulis 38.T bagian 0-2 (appended dari scratchpad t1.md)
- [x] Tulis 5 tema: [x] Pelabuhan (t2) [x] Oasis (t3) [x] Air (t4) [x] Gunung (t5) [x] Iblis (t6)
- [x] Tulis bersama: [x] 8 klip+gear+prop, 9 model (t7) [x] 10 dagang [x] 11 tautan [x] 12 suara (t8) [x] 13 [x] 14 [x] 15 (t9)
- [x] Cek LF (0 CR), rujukan silang diperbaiki, 38.T baris 2770-4358

Keputusan: 2 modul baru 38TK (server/data/worldgen/cerita) + 38TV (model/klien/layar); baris tambahan di 38W, 38NL,
38MD (gear), 38ST (suara), 38EK, 38KS, 38PR. Distrik tema = 2x2 lot (96), kota 3x3 distrik = 288x288.
Langkah berikut: selesai (desain). Implementasi: 38TK + 38TV di langkah 2b, progres docs/progres/kota-TK.md dan kota-TV.md.
