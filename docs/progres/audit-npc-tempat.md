# Audit NPC tempat (setiap tempat dan pekerjaan punya orang)

Checkpoint agen. Baca dulu sebelum melanjutkan; jangan mulai ulang.

## Checklist

- [x] Telusuri jalur spawn: DuniaAnchors, KotaAnchors, KotaBesarAnchors, HidupApi.tubuh, Pemanggil, DuniaBudget
- [x] Temukan akar masalah (lihat bagian "Akar masalah")
- [x] Fix budget: tubuh penduduk tidak lagi dihitung di budget NPC (KotaEntities)
- [x] Fix staf: HidupApi.tubuhStaf (bebas topi/MSPT), dipakai semua jangkar layanan/toko/tokoh
- [x] Fix KotaAnchors: peran toko/layanan bebas npcPerTown + budget; radius 128 (perilaku lain tetap 48)
- [x] Fix KotaBesarAnchors: radius 128 (perilaku lain tetap 56), LEPAS_JARAK 160
- [x] Fix generator: peran tidak dikenal (PETUGAS_CUKAI, PEGADAI, PELATIH, KEPALA_KAFILAH + titik CENDEKIA, JURI, PANDAI_LADAM, PEMILIK_KEDAI, PENCERITA, PENGURUS_KUDA, PREMAN, TUKANG_BATU, TUKANG_RODA)
- [x] Regenerasi gen_tempat_kota_besar.py + gen_kompat_bioma.py, cek_tempat.py 0 error
- [x] Tokoh 38.S.2 (Bibi Hong, Qian Duoduo, ... 22 tokoh) diikat ke jangkar layanan (data tokoh) + jangkar baru istana_ajudan, istana_pewaris, ketua_alkemis, kumuh, kumuh_anak; lao_qiao di pos dagang
- [x] Perintah /kultivasi uji hidup audit + lang id/en
- [x] PANDUAN.md bab 69 (69.5)
- [x] Pemanggil: penduduk peran stationary didahulukan, bebas topi tempat/MSPT
- [x] Penjaga lapak: pasar desa (PEDAGANG_KELILING), pasar/aula kompleks sekte (MURID_SEKTE_DALAM)
- [x] Tabel audit (bawah)
- [x] Build compileJava compileClientJava jar (BUILD SUCCESSFUL)

## Akar masalah

1. `KotaEntities` melacak SETIAP KultivatorNpc yang masuk dunia di budget `npc` (ENTITY_LOAD), termasuk tubuh penduduk
   Pemanggil (yang sudah punya budget sendiri `npc_hidup`, sampai 300 per tempat / 300 per pemain). Di ibu kota penduduk
   muncul 48 per siklus dari jarak 128 blok, jadi `npc` (umum.npcCap 300, npcPerPlayer 200 dalam 96 blok) penuh sebelum
   pemain sampai 48-56 blok dari toko. `HidupApi.tubuh` dan `KotaAnchors.person` lalu selalu menolak: penjaga toko,
   pemilik kedai teh, bankir, juru lelang dst. tidak pernah muncul. Tukang (38PR) mencoba lagi tiap 10 tick dan merebut
   slot yang sesekali kosong saat penduduk dilepas, makanya hanya tukang yang terlihat.
2. Penjaga MSPT (`DuniaBudget.overloaded`) menolak semua NPC baru; ratusan penduduk menaikkan MSPT, jadi layanan ikut mati.
3. Empat jangkar layanan ibu kota memakai peran yang tidak ada di NpcRole (`NpcRole.valueOf` gagal -> tak pernah ada tubuh):
   cukai PETUGAS_CUKAI, pegadaian PEGADAI, pelatih PELATIH, pos kafilah KEPALA_KAFILAH. Sembilan titik kerja juga bertanda
   peran yang tidak ada, jadi tak ada penduduk yang pernah memakainya.
4. Jarak: KotaAnchors hanya memanggil orang dalam 48 blok, KotaBesarAnchors 56; tubuh layanan pergi bila tak ada pemain
   dalam 56 blok selama 30 detik.
5. KotaAnchors `npcPerTown` (24) berlaku untuk semua jangkar kota/* satu kota; ibu kota (satu id kota untuk 11 distrik)
   punya ~23 jangkar orang kota/* sehingga toko pil/pusaka/teh bisa kalah oleh penjaga.

6. Tokoh cerita ibu kota (38.S.2, 23 tokoh: Bibi Hong, Gendut Zhu, Qian Duoduo, Bao Qing, Lin Fei, Dan Chen, ...) tidak
   pernah dipasang ke tubuh mana pun: `Tokoh.pasang`/`CeritaApi.pasangTokoh` tak dipanggil siapa pun, jadi semua dialog
   dan quest `tokoh:<id>` mereka tidak pernah bisa dibuka.

## Tabel audit

Keterangan kolom "Sebelum": **lapar** = jangkar benar tetapi tubuhnya ditolak budget NPC begitu penduduk muncul (akar 1-2),
hanya kadang muncul; **tidak pernah** = peran salah / tak ada jangkar / tokoh tak pernah dipasang. "Sesudah": **staf** =
`HidupApi.tubuhStaf` / KotaAnchors staf, bebas topi NPC dan penjaga MSPT, datang dalam 128 blok; **penduduk** = penduduk
Pemanggil di titik kerjanya (peran stationary kini didahulukan dan bebas topi tempat/MSPT).

### Kota Besar (ibu kota; 11 distrik, satu id kota)

| Bangunan / lot | Jangkar | Peran (tokoh) | Sebelum | Sesudah |
|---|---|---|---|---|
| Gerbang Agung Selatan | kotabesar/layanan gerbang + kota/penjaga | PENJAGA_GERBANG, PENJAGA | lapar | staf |
| Gerbang samping (x3) | kota/penjaga | PENJAGA | lapar | staf |
| Alun-alun gerbang, kantor cukai | layanan cukai + kota/penjaga + kota/papan | MENTERI (dulu PETUGAS_CUKAI) | tidak pernah | staf, layar buku kas |
| Pusat: alun, papan, penjaga | kota/papan, kota/penjaga | PENJAGA (papan misi) | lapar | staf |
| Menara Genderang | kota/penjaga | PENJAGA | lapar | staf |
| Menara Lonceng (kuil dewa kota) | layanan kuil_dewa_kota | RAHIB | lapar | staf |
| Alun Penerimaan (Hari Gerbang) | layanan hari_gerbang | PENGUJI | lapar | staf |
| Kantor Yamen | layanan yamen, denda + kota/penjaga | HAKIM (Bao Qing), KAPTEN_PENJAGA (Tie Hu) | lapar, tokoh tidak pernah | staf + tokoh |
| Kantor Pos | layanan kantor_pos | PELAYAN | lapar | staf |
| Istana: aula takhta | layanan audiensi | PANGERAN_KOTA (Yan Wushuang) | lapar, tokoh tidak pernah | staf + tokoh |
| Istana: sayap barat / timur | layanan perintah_istana / istana_ajudan (baru) | MENTERI (Gu Lianshan / Mo Ying) | lapar / tidak ada | staf + tokoh |
| Istana: balai kediaman | layanan istana_pewaris (baru) | PANGERAN_KOTA (Yan Ruyi) | tidak ada | staf + tokoh |
| Barak Pengawal, Gerbang Istana | kota/penjaga | PENJAGA | lapar | staf |
| Balai Pertemuan Sekte | layanan pertemuan_sekte | MENTERI | lapar | staf |
| Kantor Catatan Sipil | layanan arsip | PUSTAKAWAN | lapar | staf |
| Perpustakaan Agung | layanan perpustakaan, kuliah, kantor_peta | PUSTAKAWAN (Tuan Wen), PENCERAMAH_FANA | lapar | staf + tokoh |
| Menara Pengamat Langit | layanan menara_pengamat | PENCERAMAH_FANA | lapar | staf |
| Kuil Langit | layanan kuil | RAHIB (Jing Xin) | lapar | staf + tokoh |
| Sekolah | layanan sekolah (+ titik kursi CENDEKIA -> PENCERAMAH_FANA) | PENCERAMAH_FANA | lapar | staf |
| Kantor Pelabuhan Langit | layanan dermaga | NAKHODA (Yun Fei) | lapar | staf + tokoh |
| Stasiun Teleportasi | layanan teleport | PENJAGA_FORMASI | lapar | staf |
| Penginapan Awak | layanan penginapan | PEMILIK_PENGINAPAN | lapar | staf |
| Bengkel Kapal, Bengkel Tukang, Pasar Loak, Kios Jalan, Bengkel Roda | kotabesar/tukang (+ layanan perbaikan) | TUKANG (38PR) | ada | staf + regu tukang |
| Gilda Alkemis | layanan gilda_alkemis, ujian_alkemis, ketua_alkemis (baru) | ALKEMIS, PENGUJI, KETUA_GILDA (Dan Chen) | lapar / tidak ada | staf + tokoh |
| Gilda Pandai Pusaka | layanan gilda_tempa, altar_pusaka | EMPU (Ou Ye) | lapar | staf + tokoh |
| Gilda Kultivator | layanan gilda_misi, naga_muda, uji_duel + kota/papan | PETUGAS_GILDA (Xiao Mei), KETUA_GILDA (Lin Fei), PENGUJI (Feng Ao) | lapar | staf + tokoh |
| Balai Tabib | layanan tabib | TABIB | lapar | staf |
| Rumah Makan (bakmi) | layanan rumah_makan | KOKI (Gendut Zhu) | lapar | staf + tokoh |
| Rumah Makan Kecil, Rumah Makan Teratai, Warung Jalan | layanan rumah_makan / warung | KOKI | lapar | staf |
| Balai Lelang Agung | layanan lelang_agung, titip_lelang | JURU_LELANG | lapar | staf |
| Rumah Uang | layanan bank | BANKIR (Qian Duoduo) | lapar | staf + tokoh |
| Rumah Batu Sumber (judi) | layanan judi_batu | BANDAR_BATU (Lao Shi) | lapar | staf + tokoh |
| Pasar Binatang Roh | layanan pasar_binatang | PEDAGANG_BINATANG (Ma Laoliu) | lapar | staf + tokoh |
| Paviliun Seribu Bilah | layanan toko_senjata | PEDAGANG_SENJATA | lapar | staf |
| Kios Pil | kota/pil x2, kota/pusaka, layanan jimat | PEDAGANG_PIL, PANDAI_PUSAKA, PEDAGANG_KELILING | lapar (+ npcPerTown) | staf, layar toko |
| Deret Lapak Sekte | kotabesar/lapak_sekte 0-5 | PEDAGANG_KELILING per lapak sewaan | lapar | staf (saat disewa) |
| Kedai Teh Agung | kota/teh + 2 kota/kursi_pengembara (+ titik teko PEMILIK_KEDAI -> PEMILIK_TEH, kursi PENCERITA -> TETUA_DESA) | PEMILIK_TEH | lapar (+ npcPerTown, 48 blok) | staf, menu teh |
| Penginapan Awan Singgah | layanan penginapan | PEMILIK_PENGINAPAN (Bibi Hong) | lapar | staf + tokoh |
| Penginapan Bulan Perak | layanan penginapan | PEMILIK_PENGINAPAN | lapar | staf |
| Arena Agung | layanan ruang_juara, turnamen (+ titik kursi JURI -> PENGUJI) | PENGUJI | lapar | staf |
| Wisma Delegasi | layanan wisma_delegasi | PELAYAN | lapar | staf |
| Lapangan Latihan | layanan pelatih | PENGUJI (dulu PELATIH) | tidak pernah | staf |
| Sarang Tikus (pegadaian, pasar gelap) | layanan pegadaian, kota/gelap, kota/gerbang_gelap | BANKIR (dulu PEGADAI; layar bank + gadai), MAKELAR_GELAP | tidak pernah / lapar | staf |
| Kumuh: pojok pengemis, sumur | layanan kumuh, kumuh_anak (baru) | PENGEMIS (Kakek Lumpur), ANAK (Dou) | tidak ada | staf + tokoh |
| Dapur Umum | layanan dapur_umum | RAHIB | lapar | staf |
| Tugu Jarak | layanan tugu_jarak | PENJAGA | lapar | staf |
| Pos Kafilah | layanan kafilah | PEDAGANG_KELILING (dulu KEPALA_KAFILAH; layar kafilah) | tidak pernah | staf |
| Kandang kuda, kebun dewa jalan, bengkel roda | titik PENGURUS_KUDA -> KULI, PANDAI_LADAM / TUKANG_RODA / TUKANG_BATU -> TUKANG; layanan dewa_jalan | KULI, TUKANG, RAHIB | titik tak terpakai / lapar | penduduk / staf |
| Rumah toko, rumah susun, gudang, ladang, dermaga langit, taman | titik kerja (tanpa jangkar) | penduduk (KULI, PETANI, PEDAGANG_KELILING, WARGA ...) | penduduk | penduduk |

Jumlah ibu kota: jangkar layanan 51 -> 56 (4 peran salah diperbaiki, 5 baru), jangkar orang kota/* 18, tokoh terpasang
0 -> 22; titik kerja bertanda peran salah 13 -> 0.

### Kota bertema (kota_tema)

| Kota | Tempat kerja | Penjaga | Sebelum | Sesudah |
|---|---|---|---|---|
| semua | bank cabang, papan gilda, penginapan, rumah makan (kotabesar/layanan) | BANKIR, PETUGAS_GILDA, PEMILIK_PENGINAPAN, KOKI (BIKSU_GUNUNG di rumah tamu biara) | lapar | staf |
| semua | kota/teh, kota/pil, kota/pusaka, kota/papan, kota/penjaga | PEMILIK_TEH, PEDAGANG_PIL, PANDAI_PUSAKA, PENJAGA | lapar | staf |
| Pelabuhan | pelabuhan/peran (bea, toko seberang, kontrak, ikan, galangan, kuil, gudang), pelabuhan/tokoh x7, menara tambat | PETUGAS_BEA, PEDAGANG_SEBERANG, PENJUAL_IKAN, TUKANG_KAPAL, PENDETA_LAUT, KULI, PELAUT + tokoh | lapar | staf |
| Oasis | istana, karavanserai, kuil air, lapangan, mural (tokoh), toko air, tenun, kebun, bea, kandang sewa | tokoh Oasis, PEDAGANG_BAZAR, PENENUN_SUTRA, PETANI, PENGAWAL_GURUN, PENUNTUN_UNTA | lapar | staf |
| Gunung | peran/tokoh di biara, lift, katrol, pos kuli, aula herba, aula emas, titipan | tokoh Gunung, PENJAGA_LIFT, BIKSU_GUNUNG, KULI_GUNUNG, PELAYAN | lapar | staf |
| Iblis | iblis_tokoh, taruhan, tawanan, lelang gelap, pasar gelap | tokoh Iblis, WARGA_IBLIS, PENEGAK_IBLIS, JURU_LELANG | lapar | staf |
| Air | taman, rumah sutra, paviliun teh awan (tokoh) | tokoh Air (Wen Shi, Su Jin, Lu Yuqing ...) | lapar | staf |

### Kota kultivator lama (kota) dan Kota Abadi

| Tempat kerja | Jangkar | Peran | Sebelum | Sesudah |
|---|---|---|---|---|
| Balai lelang | kota/lelang | JURU_LELANG | lapar + npcPerTown | staf |
| Kedai teh | kota/teh | PEMILIK_TEH | lapar + npcPerTown | staf |
| Paviliun pil / pusaka | kota/pil, kota/pusaka | PEDAGANG_PIL, PANDAI_PUSAKA | lapar + npcPerTown | staf |
| Pasar gelap | kota/gelap + pintu | MAKELAR_GELAP | lapar + npcPerTown | staf |
| Papan misi, penjaga | kota/papan, kota/penjaga | PENJAGA | lapar + npcPerTown | staf |
| Warga | kota/warga | WARGA | dibatasi | dibatasi (kerumunan) |
| Tabib, dapur, penginapan, kuil, tukang | titik kerja | penduduk TABIB, KOKI, PEMILIK_PENGINAPAN, RAHIB, TUKANG | dibatasi | penduduk (stationary didahulukan) |
| Kota Abadi: pasar, lelang, utusan, kebun persik, paviliun dao | kota/abadi_* | PEDAGANG_ABADI, JURU_LELANG_ABADI, UTUSAN, PENJAGA_KEBUN, PENCERAMAH | lapar + npcPerTown | staf (warga ABADI tetap dibatasi) |

### Kampung, kompleks sekte, pos jalan

| Tempat | Tempat kerja | Penjaga | Sebelum | Sesudah |
|---|---|---|---|---|
| Kampung | balai/tetua, tabib, pandai | penduduk KEPALA_DESA, TABIB, PANDAI_DESA | penduduk | penduduk (stationary didahulukan) |
| Kampung desa/desa_besar | pasar desa (kampung/pasar) | tidak ada (hanya kartu dagang) | tidak ada | staf PEDAGANG_KELILING (baru) |
| Kampung klan | patriark, tuan muda (Klan) | PATRIARK_KLAN, TUAN_MUDA | lapar | staf (topi kampung tetap) |
| Kompleks sekte | aula (konter jual sekte non-netral), pasar sekte (netral) | tidak ada | tidak ada | staf MURID_SEKTE_DALAM (baru) |
| Kompleks sekte | dapur, tabib, alkemis, pandai, perpustakaan, gerbang | penduduk KOKI, TABIB, ALKEMIS, EMPU, PUSTAKAWAN, PENJAGA | kalah topi 60 tubuh (kompleks 80-120 penduduk) | penduduk, peran stationary didahulukan |
| Pos jalan | penginapan x4, rumah makan x3, papan gilda x4, dagang wilayah, gudang kafilah, kandang, pandai kuda | PEMILIK_PENGINAPAN, KOKI, PETUGAS_GILDA, PEDAGANG_KELILING (Lao Qiao), KULI, PANDAI_DESA | lapar | staf + tokoh |
| Pos jalan | kota/papan x3 | PENJAGA | lapar | staf |
| Pos kuil jalan | titik | penduduk RAHIB | penduduk | penduduk |

Sisa: Ah Lun (tokoh kompleks_ah_lun) belum punya jangkar di kompleks sekte; penjaga yang hanya penduduk (kampung,
kompleks) pulang tidur malam hari sesuai jadwal; modul kota bertema memakai jarak panggil sendiri (oasis 48, gunung
32-64, iblis 56, air 96, pelabuhan 128).
