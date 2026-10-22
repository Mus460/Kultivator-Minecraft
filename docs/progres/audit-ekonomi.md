# audit:ekonomi — progres (READ-ONLY audit)
- [x] Peta DESIGN (bagian ekonomi) — DESIGN 19.3, 20.3.7, 26.3/26.8, 34.1/34.5
- [x] Mata uang: batu roh (NilaiBatu 1/9/80/600 vs resep 9:1), PK kolam (Contribution), Nama Baik, renown
- [x] Toko/lelang/pasar gelap/Lelang Abadi/balai lelang
- [x] Herba, inti binatang, pil & alkimia
- [x] Jimat, relik, tempa/pusaka natal, cincin
- [x] Titah, batu asal, esensi
- [x] Tabel hadiah semua fitur (faucet vs sink)
- [x] Sekte Hidup (DESIGN 36) + Sekte Langit (37) catatan
- [x] Tulis laporan docs/progres/audit-ekonomi-laporan.md

## Temuan sementara (bukti sudah dicek)
1. NilaiBatu.VALUE {1,9,80,600} (dunia/kota/NilaiBatu.java:21) vs resep 9:1 dua arah (recipe/spirit_stone_*), MISSION_PK {0.5,4.5,40,300} (harta/SpiritStones.java:72) -> Supreme->9 High (+20%).
2. Kantor Misi pil flat 5/10/20/40 PK per kualitas (sect/hall/Contribution.java:153-160) -> pil toko 4 NB = 10 PK.
3. Yue Lu vs Toko harga pil 8-22x (Contribution.java:60-72 vs Harga.java:49-64); MARROW 900 PK lvl6 vs Toko 80 NB + resep vanilla (recipe/marrow_cleansing_pill.json).
4. Murid: Pil Sumsum selalu naik 1 tingkat (disciple/DiscipleCultivation.java:731-736) vs pemain 100/85/55/30% (item/MarrowCleansing.java:54).
5. Pil fase-7 ditolak murid "for now" (harta/Harta.java:214-222, alchemy/PillKind.java:131).
6. Yatim: lencana_pengembara (KotaItems.java:45), sobekan_segel (RItems.java:30), kitab_formasi_kuno hanya lore (RItems.java:34).
7. Toko/Balai Lelang membeli cincin penyimpanan berisi -> isi hilang (Toko.java:104-113,196; BalaiLelang.java:463-483; StorageRingItem CONTAINER).
8. Titipan Balai Lelang hanya di memori, SERVER_STOPPED -> BalaiLelang.clear() tanpa bayar (Kota.java:72-75, BalaiLelang.java:138-142) — item hilang. Lelang Abadi simpan di extra (Auction.java:520-530).
9. Titipan Balai Lelang ambil SELURUH stack, nilai dicap meanBase/2 per stack (BalaiLelang.java:390-395,475-481) vs Abadi 1 item.
10. Pasar Gelap jual tanpa batas harian (PasarGelap.java:192-205) vs Toko 16/hari.
11. Bintang: Goals memakan tomb_essence s/d 10 otomatis (goals/Goals.java:82-83,143-158) padahal Kenaikan butuh 9 (abadi/Ascend.java:67,215) di gerbang ★9 (ImmortalPerks.java:118) -> bertabrakan. Batu Asal juga dimakan (OriginStones.java:66-67) bersaing dengan tawaran lelang.
Langkah berikut: tempa/talisman/titah/KotaAbadi, lalu tabel hadiah (HartaLoot, Guide rewards, DailyChores, Pursuit, TreasureRewards, Misi sekte).
12. Herba 1000 th di Toko band 6+ cuma ~22 NB (SpiritHerbs.java:188-190, Harga.java:174,209-215) vs KotaAbadi 8 Batu Asal, Lelang Abadi 25.
13. Pasar Abadi tanpa batas stok (KotaAbadi.java:270-319); immortal_pill 14 Batu Asal -> Toko beli ~3150-3600 NB (Harga 9000). Rasio NB/Batu 22..642.
14. Titah Utusan batu 16->24 (gratis +8), mutiara 2 (40 Batu) -> 30 (KotaAbadi.java:67-68,361-380).
15. mutiara_penunjuk_laut 12 NB di Pedagang Pulau (SeaPeople.java:85) vs Harga 900 / Pasar Gelap 1350 (Harga.java:101,206).
16. Papan Misi kota bayaran x3 per band (PapanMisi.java:123-140) -> 19.440 NB band 8; tugas harian 1 Batu Rendah (DailyChores.java:274).
17. Istilah: Papan Misi dobel, Titah x3, Pusaka Natal dobel (disciple/power/NatalTreasure vs tempa/NatalTreasureSelf), "batu roh Tertinggi" (abadi.id_id.json:43,49,209).
18. Sekte Hidup: sink PK baru besar vs faucet; hadiah murid (Kepribadian.java:751-763); TambangRoh.poolHas probe.
19. DESIGN 37 sesaji A9 (2 Agung=1200 NB) < A7-A8 (24 Tinggi=1920 NB) (37-sekte-langit.md:285-290).
20. Duplikasi tabel NB (DimensiData.java:27, RRewards.java:38), FUEL_XP vs FUEL_FACTOR (SpiritStones.java:67-68, Meditation.java:103-104).
- [x] Laporan ditulis: docs/progres/audit-ekonomi-laporan.md (K1, T1-T4, S1-S11, R1-R7). SELESAI.
