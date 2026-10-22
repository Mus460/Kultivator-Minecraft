# audit:ekonomi — progres (READ-ONLY audit)
- [x] Peta DESIGN (bagian ekonomi) — DESIGN 19.3, 20.3.7, 26.3/26.8, 34.1/34.5
- [~] Mata uang: batu roh (NilaiBatu 1/9/80/600 vs resep 9:1), PK kolam (Contribution), Nama Baik, renown
- [~] Toko/lelang/pasar gelap/Lelang Abadi/balai lelang
- [~] Herba, inti binatang, pil & alkimia
- [ ] Jimat, relik, tempa/pusaka natal, cincin
- [ ] Titah, batu asal, esensi
- [ ] Tabel hadiah semua fitur (faucet vs sink)
- [ ] Sekte Hidup (DESIGN 36) + Sekte Langit (37) catatan
- [ ] Tulis laporan docs/progres/audit-ekonomi-laporan.md

## Temuan sementara (bukti sudah dicek)
1. NilaiBatu.VALUE {1,9,80,600} (dunia/kota/NilaiBatu.java:20) vs resep 9:1 (recipe/spirit_stone_*), MISSION_PK {0.5,4.5,40,300} (harta/SpiritStones.java:71) -> arbitrase Supreme->9 High (+20%).
2. Kantor Misi pil flat 5/10/20/40 PK per kualitas (sect/hall/Contribution.java:139-147) -> pil murah toko (4 NB) = 10 PK; pil mahal = 10 PK juga.
3. Yue Lu vs Toko harga pil berbeda 10-20x (Contribution.java:58-71 vs Harga.java:46-62); MARROW 900 PK lvl6 vs Toko 80 NB + resep crafting vanilla (recipe/marrow_cleansing_pill.json).
4. Murid: Pil Sumsum selalu naik 1 tingkat (disciple/DiscipleCultivation.java:730) vs pemain 85/55/30% (item/MarrowCleansing.java).
5. Pil fase-7 ditolak murid "for now" (harta/Harta.java:214-222).
6. Yatim: lencana_pengembara (KotaItems.java:45), sobekan_segel (RItems.java:30), kitab_formasi_kuno hanya lore (RItems.java:34).
Langkah berikut: BalaiLelang/Auction/PasarGelap/KotaAbadi, lalu tempa/talisman/titah.
