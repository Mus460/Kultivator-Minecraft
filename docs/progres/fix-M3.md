# fix:M3 Ekonomi Sejalan (checkpoint)

Rencana: docs/progres/audit-integrasi.md bagian M3; detail: audit-ekonomi-laporan.md (+ audit-dunia-laporan D13/D14).
Keputusan pengguna: "Ubah angkanya" (semua angka "perlu persetujuan" disetujui; pakai nilai rekomendasi).
Tanpa Gradle. Progres di berkas ini.

## Sub-tugas
- [x] 0. Baca kode semua berkas milik M3, catat akhir baris (CRLF/LF)
- [x] 1. K2 cincin berisi/terikat ditolak (Toko.buys/view, BalaiLelang.consign) + StorageRingItem.isEmpty
- [x] 2. T14/R28 Titipan.java (persisten KotaData "titipan", 1 buah, whitelist, 0,6-1,0) dipakai BalaiLelang + Auction.payOwed; Kota: SERVER_STOPPING forceClose
- [x] 3. T15 Goals: sisakan Ascend.OFFER_ESSENCE sebelum ascended; sakelar /kultivasi diskonbintang [hidup|mati] + pratinjau; OriginStones ikut sakelar
- [x] 4. T16 herbMax toko 300 (herbMin band4+ 200) + kurva herba kota 2x(1+(age/100)^1.6)
- [x] 5. T17/R10 Harga.NB_PER_PK=2; Yue Lu 6/6/60/300/1000; MARROW -> Pil Sumsum Sempurna 400 PK; persembahan pil = base/2 x mutu (cap 300); diskon Pakta 10%
- [x] 6. S23/R17 NilaiBatu 81/729, MISSION_PK 40.5/364.5; DimensiData+RRewards pakai NilaiBatu; FUEL_* = Meditation
- [x] 7. S24 stok Pasar Abadi 2/1 per periode, Toko tolak fase9, Mutiara 20/Dupa 40/Selendang 300 stok Pasar Abadi per pemain (2/periode, langka 1); Toko tolak fase 9; Mutiara 20 NB (Harga+SeaPeople), Dupa 40, Selendang 300
- [x] 8. S25 (kode + lang dunia_kota titah.* -> Perintah Utusan, jenis/isi.batu tambang, help kota_abadi) "Perintah Utusan"; jenis batu = tambang 16 Batu Asal (hitung di OriginStones), mutiara bayar 50
- [x] 9. S27 Papan Misi x2/band cap band 6; hadiah harian ikut ranah (pesan baru ekonomi.*)
- [x] 10. R13 suap = 20 x band penjaga (maks 3); R18 batas jual 16/hari Pasar Gelap
- [x] 11. S26 applyMarrow chance+mutu+syarat ranah/mutu (MarrowCleansing.stepRealm/needsHighPill); efek murid 4 pil fase 7 (bone_hp di extra.harta); refuseNewPills dihapus; Harta.discipleAccepts publik
- [x] 12. S22 Segel (ekonomi.segel.satu); R16 abadi Batu Roh Agung; R20 harta tooltip.fuel; R21 abadi convert_tip (sisa: item milik fragmen tanpa pemilik -> kunci ekonomi.nama.* + needsFromOthers); S31 dunia_kota Abadi Sejati->Dewa Abadi; S32 Papan Tugas Kota, Kantor Persembahan (harta); teks titipan 0,6-1,0 + help kota (suap, batas 16, upah x2 cap)
- [x] 13. Lang ekonomi_selaras.{id_id,en_us}.json (25 kunci identik, LF); harta tooltip.disciples/disciple_refused; abadi help batu_asal (diskonbintang), lelang titip; needsFromOthers dicatat
- [x] 14. Self-check: diff tiap berkas Java ditinjau (impor, simbol, API mcsrc: ItemContainerContents.nonEmptyItemCopyStream, CompoundTag.read/store, DefaultedRegistry.getValue), kunci lang dipakai=ada, paritas en/id 6 fragmen, akhir baris sesuai git

## Catatan
- JANGAN sed -i pada berkas CRLF (Git Bash mengubah ke LF). Pakai scratchpad langtool.js (node) atau Edit.
- CRLF (pertahankan): abadi/Auction.java, abadi/OriginStones.java, disciple/DiscipleCultivation.java, lang-src/harta.*.json. Lainnya LF.
- Cek akhir baris pakai `git ls-files --eol` / od (grep -c \r di Git Bash menipu). Python tidak ada; pakai node bila perlu.
- Fabric SERVER_STOPPING jalan di HEAD stopServer (pemain masih ada, sebelum simpan) -> forceClose di sana.
- goals.* milik M1: teks "1 Batu Roh Rendah" harian perlu diubah M1.

## Berkas disentuh
- tempa/StorageRingItem (isEmpty, unsafeToSell); dunia/kota/{Toko (tradable, pesan cincin/fase9), Titipan (baru), BalaiLelang, KotaData.playerIds, Kota SERVER_STOPPING}; abadi/Auction (CONSIGN_* dari Titipan)
- goals/Goals (ESSENCE_RESERVE, starDiscountOn, /kultivasi diskonbintang); abadi/OriginStones (public, starStones)
- dunia/kota/KotaAbadi (stok abadi_beli, countEdict via Abadi.addActivityListener, EDICT_PAY 50); Harga (NB_PER_PK, herbValue, phase9, herbMax 300, Dupa 40, Selendang 300, MUTIARA_NB 20); laut/SeaPeople (Mutiara = Harga.MUTIARA_NB)
- sect/hall/Contribution (Ware 6/6/60/1000/300, MARROW Sempurna 400, unitValue pil = base/2 x mutu, PACT_DISCOUNT via SekteApi Sikap.SEKUTU)
- NilaiBatu 81/729; SpiritStones MISSION_PK 40.5/364.5, FUEL_* = Meditation; DimensiData.NB/RRewards.NB dari NilaiBatu
- PapanMisi x2/band cap 6; DailyChores.dayReward per ranah (pesan ekonomi.harian.selesai); PasarGelap bribePrice/guardBand, batas jual 16

## Lang TODO (dikerjakan di sub-tugas 13)
- ekonomi.murid.{tanpa_dao,sumsum_ranah_fondasi,sumsum_ranah_inti,sumsum_mutu} (tanpa arg, layar) + .chat (arg: murid, pil); sumsum_menolak(%), bersih(n), tulang(add,now,cap), tulang_penuh(cap), wawasan_penuh(dao,max)
- ekonomi.cincin_berisi, ekonomi.toko.fase9; ekonomi.bintang.{hidup,mati,pratinjau(4 arg),sisih}; dunia_kota titah.* -> Perintah, titah.jenis/isi.batu = tambang; ekonomi.harian.selesai(3 arg: barang, beruntun, %); dunia_kota lelang.tip_titip (1 buah, 0,6-1,0, tersimpan)

## needsFromOthers (catat)
- M6: hall.ware.marrow(+.desc) -> Sempurna; help.kontribusi angka baru Yue Lu/persembahan pil/diskon Pakta
- M2: KedaiTeh.RUMOUR_PRICE 80 -> 81 (satu Batu Tinggi)
- M1: goals.daily.done_all/reward_line/page.daily.reward/help.harian -> hadiah per ranah; PANDUAN catatan keseimbangan M3

## Langkah berikut
SELESAI. Tinggal build oleh agen finish.

## needsFromOthers (final)
- M1: PANDUAN "Perubahan keseimbangan" (daftar angka M3, lihat laporan akhir); goals.daily.done_all kini tak dipakai (ekonomi.harian.selesai)
- M6: hall.ware.marrow(+.desc) -> Pil Sumsum Sempurna 400 PK; help.kontribusi (Yue Lu 6/6/60/300/1000, persembahan pil = base NB/2 x mutu, diskon Pakta 10%); Dupa tooltip pakai kultivator.ekonomi.nama.dupa; Kepribadian.giftValue pakai Harta.discipleAccepts
- M2: KedaiTeh.RUMOUR_PRICE 80 -> 81
- M5: glosarium: Perintah Utusan, Papan Tugas Kota, Kantor Persembahan; opsional tombol diskonbintang di UI
- finish: tooltip ekonomi.nama.{dupa_arwah,mutiara_esensi,esensi_makam} di DimensiItem/BatinItems/AlamItems (berkas tanpa pemilik)
