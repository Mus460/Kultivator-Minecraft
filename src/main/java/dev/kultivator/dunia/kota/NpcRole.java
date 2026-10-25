package dev.kultivator.dunia.kota;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The roles of {@code kultivator:kultivator_npc} (DESIGN 34.5.1-34.5.4, 38.N.2, 38.T.3.3-38.T.7.3). Each role has a robe
 * colour (the disciple robe layer tinted), whether it stays at its anchor, its combat tier (1-4, the rogue numbers of
 * 25.4.2; 0 = non-combatant and 5 = realm lord, both clamped to 1-4 by {@link KultivatorNpc#setup}; {@link #tierMax} is
 * the top of a "3-4" range), the screen tab a right-click opens ({@link KotaNet} tabs; -1 = a spoken line only, 38ST's
 * dialog hook takes over for 38 roles), its daily schedule group ({@link Kelompok}, 38.N.3) and, for the themed-city roles,
 * the theme id (38.T). Title key {@code kultivator.kota.peran.<id>}; look key for DESIGN 40 is {@link #id()} with
 * {@link #rupaKelompok()} as the fallback row.
 *
 * <p>Ordinals are synced and saved inside one world, so constants are only ever appended at the end: the 15 roles of 34,
 * then the 49 roles of 38.N.2, then the 38 themed-city roles of 38.T.14.3 ({@code MAKELAR_GELAP} of that list is the
 * existing broker, reused), then the 13 town-life roles of 42.12.2.
 */
public enum NpcRole {
	// ===================================================================================== 34 (kota lama, Kota Abadi)

	/** Juru Lelang: the auctioneer on the Balai Lelang stage. */
	JURU_LELANG(0x7A1E22, true, 2, KotaNet.TAB_LELANG, Kelompok.PEDAGANG),
	/** Pemilik Kedai Teh: the tea master of the Paviliun Angin Bambu. */
	PEMILIK_TEH(0x3E6E4E, true, 2, KotaNet.TAB_TEH, Kelompok.PEDAGANG),
	/** Makelar Gelap: the black-market broker (also the broker of the Pasar Seribu Bayangan in the Kota Iblis, 38.T.7.3). */
	MAKELAR_GELAP(0x262230, true, 2, KotaNet.TAB_GELAP, Kelompok.PEDAGANG),
	/** Pedagang Pil: the pill pavilion's merchant. */
	PEDAGANG_PIL(0x5C7C3A, true, 1, KotaNet.TAB_TOKO, Kelompok.PEDAGANG),
	/** Pandai Pusaka: the treasure pavilion's smith. */
	PANDAI_PUSAKA(0x6A4A2A, true, 1, KotaNet.TAB_TOKO, Kelompok.PEKERJA),
	/** Penjaga Kota: the guard sect's watchman (Foundation-Golden Core), also keeps the notice board. */
	PENJAGA(0x2E3E5C, false, 3, KotaNet.TAB_MISI, Kelompok.PENJAGA),
	/** Warga: townsfolk walking between home and square. */
	WARGA(0x8A7A60, false, 1, -1, Kelompok.PEKERJA),
	/** Kultivator Pengembara: a wandering cultivator (34.5.2). */
	PENGEMBARA(0x4A5A6A, false, 2, KotaNet.TAB_PERTEMUAN, Kelompok.KULTIVATOR),
	/** A member or leader of a rival group (34.5.3). */
	SAINGAN(0x3A2A2A, false, 2, -1, Kelompok.KULTIVATOR),
	/** An immortal native of the Immortal City (white-gold robe, True Immortal aura). */
	ABADI(0xF2E6C0, false, 4, -1, Kelompok.KULTIVATOR),
	/** The Dao lecturer of the Paviliun Dao. */
	PENCERAMAH(0xE8D7A8, true, 4, KotaNet.TAB_KULIAH, Kelompok.CENDEKIA),
	/** The Pasar Abadi's merchant (Batu Asal Abadi). */
	PEDAGANG_ABADI(0xD8C890, true, 3, KotaNet.TAB_PASAR_ABADI, Kelompok.PEDAGANG),
	/** The stage of the Balai Lelang Abadi: opens the existing Lelang Abadi tab (no auction logic here). */
	JURU_LELANG_ABADI(0xC8A860, true, 3, KotaNet.TAB_LELANG_ABADI, Kelompok.PEDAGANG),
	/** The Jade Emperor's envoy in the Istana Giok: the weekly Titah Utusan. */
	UTUSAN(0xFFE9A8, true, 4, KotaNet.TAB_TITAH, Kelompok.PEJABAT),
	/** The keeper of the Kebun Persik Abadi. */
	PENJAGA_KEBUN(0xF0B8C8, true, 3, KotaNet.TAB_PERSIK, Kelompok.PEKERJA),

	// ===================================================================================== 38.N.2 (ibu kota, kampung, klan, kompleks, gua)

	/** Pangeran Penjaga Kota (istana): jade guan crown, dragon robe. */
	PANGERAN_KOTA(0x8E1F1F, true, 4, -1, Kelompok.PEJABAT),
	/** Menteri Kota (istana, yamen): black winged official hat, record tablet. */
	MENTERI(0x1E1A18, false, 2, -1, Kelompok.PEJABAT),
	/** Pengawal Istana (istana): red-plumed helm, lamellar armour, spear. */
	PENGAWAL_ISTANA(0x8E1F1F, false, 3, -1, Kelompok.PENJAGA),
	/** Kapten Penjaga (yamen, gerbang): golden helm, war robe, dao sabre. */
	KAPTEN_PENJAGA(0x2B3A5C, false, 4, -1, Kelompok.PENJAGA),
	/** Penjaga Gerbang (gerbang): helm, spear, shield. */
	PENJAGA_GERBANG(0x2E3E5C, true, 3, -1, Kelompok.PENJAGA),
	/** Hakim Yamen (yamen): official hat, wooden gavel. */
	HAKIM(0x1E1A18, true, 2, -1, Kelompok.PEJABAT),
	/** Juragan Rumah Uang (rumah uang): round cap, abacus. */
	BANKIR(0x6A4A30, true, 1, -1, Kelompok.PEDAGANG),
	/** Bandar Batu (judi batu): leather apron, chisel. */
	BANDAR_BATU(0x5A3A22, true, 2, -1, Kelompok.PEDAGANG),
	/** Tukang Potong Batu (judi batu): stone goggles, head cloth. */
	PEMOTONG_BATU(0x8A8278, true, 1, -1, Kelompok.PEKERJA),
	/** Pedagang Binatang (pasar binatang): fur hat, bridle rope. */
	PEDAGANG_BINATANG(0x8C6A3A, true, 2, -1, Kelompok.PEDAGANG),
	/** Pedagang Senjata (toko senjata): leather vest, sword on the back. */
	PEDAGANG_SENJATA(0x7C8288, true, 2, -1, Kelompok.PEDAGANG),
	/** Empu Pandai Pusaka (gilda tempa): leather apron, gloves, hammer. */
	EMPU(0x5A3A22, true, 3, -1, Kelompok.PEKERJA),
	/** Alkemis Gilda (gilda alkemis): alchemist hat, pill gourd. */
	ALKEMIS(0x5C7C3A, true, 2, -1, Kelompok.CENDEKIA),
	/** Ketua Gilda (every guild hall): guild robe, star badge. */
	KETUA_GILDA(0xC9A24A, true, 4, -1, Kelompok.PEJABAT),
	/** Petugas Gilda (gilda kultivator): clerk hat, brush; keeps the one guild board (38.M.3). */
	PETUGAS_GILDA(0x3A5A8C, true, 1, KotaNet.TAB_MISI, Kelompok.PEDAGANG),
	/** Penguji Gilda (gilda kultivator): sword, headband. */
	PENGUJI(0x4A5A6A, true, 3, -1, Kelompok.KULTIVATOR),
	/** Penjaga Formasi (stasiun teleport): trigram robe, formation disc. */
	PENJAGA_FORMASI(0x2E7A78, true, 3, -1, Kelompok.KULTIVATOR),
	/** Nakhoda Langit (dermaga awan): wide hat, spyglass. */
	NAKHODA(0x2E5A7A, false, 2, -1, Kelompok.PEKERJA),
	/** Kuli Angkut (dermaga, pasar, luar): head cloth, carrying pole. */
	KULI(0x8A7A60, false, 1, -1, Kelompok.PEKERJA),
	/** Pemilik Penginapan (penginapan, pos): apron, room keys. */
	PEMILIK_PENGINAPAN(0x9C7448, true, 1, -1, Kelompok.PEDAGANG),
	/** Koki (rumah makan, dapur): cloth chef's cap, ladle. */
	KOKI(0xEDE6D6, true, 1, -1, Kelompok.PEKERJA),
	/** Pelayan (penginapan, istana, klan): tray. */
	PELAYAN(0x9C7448, false, 1, -1, Kelompok.PEKERJA),
	/** Pustakawan (perpustakaan): scholar's cap, book. */
	PUSTAKAWAN(0x3A5A8C, true, 2, -1, Kelompok.CENDEKIA),
	/** Guru Kuliah (perpustakaan): scholar's cap, fan. */
	PENCERAMAH_FANA(0xE8DDC0, true, 3, -1, Kelompok.CENDEKIA),
	/** Rahib Kuil (kuil): grey robe, prayer beads. */
	RAHIB(0x8A8278, false, 2, -1, Kelompok.RAHIB),
	/** Tabib (balai tabib, kampung): medicine box. */
	TABIB(0x5FA88C, true, 1, -1, Kelompok.CENDEKIA),
	/** Tukang (bengkel, every place): tool belt, hammer, plaster bucket (38.R repairs). */
	TUKANG(0x6A4A30, false, 1, -1, Kelompok.PEKERJA),
	/** Penjaga Kios Teka-teki (festival): hand lantern. */
	PENJAGA_TEKA(0xB08A3A, true, 1, -1, Kelompok.PEDAGANG),
	/** Juara Sekte (arena, balai pertemuan): sect emblem robe, sword; tier 3-4. */
	JUARA_SEKTE(0x4A2A5A, false, 3, 4, -1, Kelompok.KULTIVATOR, null),
	/** Kepala Desa (kampung): old straw hat, walking staff. */
	KEPALA_DESA(0x6A4A30, false, 1, -1, Kelompok.TETUA),
	/** Petani (kampung, outside the walls): straw hat, hoe. */
	PETANI(0x8A7A60, false, 1, -1, Kelompok.PEKERJA),
	/** Nelayan (kampung, ponds; also the fishermen of the Kota Pelabuhan): straw hat, fishing rod. */
	NELAYAN(0x2E5A7A, false, 1, -1, Kelompok.PEKERJA),
	/** Pandai Besi Desa (kampung): apron, hammer. */
	PANDAI_DESA(0x5A3A22, true, 1, -1, Kelompok.PEKERJA),
	/** Pedagang Keliling (kampung, pos, jalan): back basket. */
	PEDAGANG_KELILING(0xB08A3A, false, 1, -1, Kelompok.PEDAGANG),
	/** Anak (every settlement): hair tie; child body scale 0.6 (DESIGN 40 usia ANAK). */
	ANAK(0xE8A8B8, false, 0, -1, Kelompok.ANAK),
	/** Kakek/Nenek (kampung, hunian): walking stick, fan. */
	TETUA_DESA(0x8A8278, false, 0, -1, Kelompok.TETUA),
	/** Patriark Klan (klan): small jade crown. */
	PATRIARK_KLAN(0x4A2A5A, true, 4, -1, Kelompok.PEJABAT),
	/** Tuan Muda Klan (klan): silk robe, folding fan. */
	TUAN_MUDA(0x3A5A8C, false, 2, -1, Kelompok.KULTIVATOR),
	/** Tetua Klan (klan): dark robe. */
	TETUA_KLAN(0x2B3A5C, false, 3, -1, Kelompok.TETUA),
	/** Murid Klan (klan): clan sash. */
	MURID_KLAN(0x5A6A7A, false, 1, -1, Kelompok.KULTIVATOR),
	/** Murid Luar (kompleks sekte NPC): sect emblem robe. */
	MURID_SEKTE_LUAR(0x6A7A8A, false, 1, -1, Kelompok.KULTIVATOR),
	/** Murid Dalam (kompleks sekte NPC): sect emblem robe. */
	MURID_SEKTE_DALAM(0x3A4A6A, false, 2, -1, Kelompok.KULTIVATOR),
	/** Tetua Sekte (kompleks sekte NPC): elder robe with emblem. */
	TETUA_SEKTE(0x2A2A3A, false, 3, -1, Kelompok.TETUA),
	/** Pelayan Sekte (kompleks sekte NPC): broom. */
	PELAYAN_SEKTE(0x8A7A60, false, 0, -1, Kelompok.PEKERJA),
	/** Pertapa (gua pertapa): patched robe, prayer beads, long white beard. */
	PERTAPA(0x8A7A60, true, 4, -1, Kelompok.RAHIB),
	/** Sisa Jiwa (makam warisan): translucent (existing {@code FLAG_GHOST}); no daily schedule. */
	SISA_JIWA(0xBFD8E8, true, 4, -1, Kelompok.KHUSUS),
	/** Tetua Iblis (sarang iblis): black-red robe, skull at the waist; hostile, not townsfolk; no daily schedule. */
	TETUA_IBLIS(0x6A1420, true, 4, -1, Kelompok.KHUSUS),
	/** Tawanan (sarang iblis, Kota Iblis): ragged clothes, rope; only ever freed or ransomed; no daily schedule. */
	TAWANAN(0x8A7A60, true, 0, -1, Kelompok.KHUSUS),
	/** Pengemis (kumuh, Kota Iblis): bowl, stick; schedule of the old (Jadwal may mix in ANAK by hash). */
	PENGEMIS(0x6A5A48, false, 0, -1, Kelompok.TETUA),

	// ===================================================================================== 38.T.3.3 Kota Pelabuhan

	/** Syahbandar (Kantor Urusan Laut): sea official's hat, sea-blue robe. */
	SYAHBANDAR(0x2E5A7A, true, 3, 3, -1, Kelompok.PEJABAT, Tema.PELABUHAN),
	/** Petugas Bea (Kantor Urusan Laut, dermaga): clerk hat, seal book. */
	PETUGAS_BEA(0x3A5A8C, true, 1, 1, -1, Kelompok.PEDAGANG, Tema.PELABUHAN),
	/** Pelaut (dermaga, Kedai Jangkar Patah): head cloth, striped vest, shark-tooth necklace. */
	PELAUT(0x2E5A7A, false, 1, 1, -1, Kelompok.PEKERJA, Tema.PELABUHAN),
	/** Nakhoda Laut (kapal dagang, Kantor Urusan Laut): wide hat, mantle coat. */
	NAKHODA_LAUT(0x2B3A5C, false, 2, 2, -1, Kelompok.PEKERJA, Tema.PELABUHAN),
	/** Penjaga Suar (mercusuar): oilskin coat, lantern; tends the light 12500-23500. */
	PENJAGA_SUAR(0xB08A3A, true, 2, 2, -1, Kelompok.TETUA, Tema.PELABUHAN),
	/** Tukang Kapal (galangan): tar apron, tool belt, caulking hammer. */
	TUKANG_KAPAL(0x5A3A22, false, 1, 1, -1, Kelompok.PEKERJA, Tema.PELABUHAN),
	/** Penjual Ikan (Pasar Ikan Pagi 05-09): wet apron, cloth bun. */
	PENJUAL_IKAN(0x2E7A78, true, 1, 1, -1, Kelompok.PEDAGANG, Tema.PELABUHAN),
	/** Pedagang Seberang (Pasar Seberang): turban, long embroidered robe. */
	PEDAGANG_SEBERANG(0xC9A24A, true, 2, 2, -1, Kelompok.PEDAGANG, Tema.PELABUHAN),
	/** Pendeta Laut (Kuil Dewi Laut): blue-white robe, small crown. */
	PENDETA_LAUT(0x8EB8D8, false, 2, 2, -1, Kelompok.RAHIB, Tema.PELABUHAN),

	// ===================================================================================== 38.T.4.3 Kota Oasis Gurun

	/** Tuan Benteng (benteng oasis): golden turban, striped silk robe, curved-sword sash. */
	TUAN_BENTENG(0xC9A24A, true, 4, 4, -1, Kelompok.PEJABAT, Tema.OASIS),
	/** Kafilah Bashi (karavanserai, lapangan kafilah): wide fur hat, long coat, whip. */
	KAFILAH_BASHI(0x8C6A3A, false, 2, 2, -1, Kelompok.PEDAGANG, Tema.OASIS),
	/** Penuntun Unta (lapangan kafilah): sand veil over the mouth, vest; leads the camel string. */
	PENUNTUN_UNTA(0xC8A878, false, 1, 1, -1, Kelompok.PEKERJA, Tema.OASIS),
	/** Pedagang Bazar (bazar pagi 06-11, Pasar Malam Bulan Sabit 17-24): embroidered round cap, vest. */
	PEDAGANG_BAZAR(0xB08A3A, true, 1, 1, -1, Kelompok.PEDAGANG, Tema.OASIS),
	/** Pendeta Air (Kuil Air, danau bulan sabit): water-blue robe, thin veil, water jar. */
	PENDETA_AIR(0x3A5A8C, false, 2, 2, -1, Kelompok.RAHIB, Tema.OASIS),
	/** Pelukis Gua (Tebing Seribu Lukisan): paint-stained apron, brush in the bun. */
	PELUKIS_GUA(0xE8DDC0, false, 1, 1, -1, Kelompok.CENDEKIA, Tema.OASIS),
	/** Penari Gurun (panggung, 19-21): long shawl, bell bangles. */
	PENARI_GURUN(0x8E1F1F, false, 1, 1, -1, Kelompok.PEKERJA, Tema.OASIS),
	/** Pengawal Gurun (Gerbang Giok, tembok): spiked helm, scale coat, spear. */
	PENGAWAL_GURUN(0x8C6A3A, false, 2, 2, -1, Kelompok.PENJAGA, Tema.OASIS),

	// ===================================================================================== 38.T.5.3 Kota Air

	/** Nyonya Sutra (Rumah Sutra): jade-pin bun, embroidered silk robe. */
	NYONYA_SUTRA(0x4A2A5A, true, 2, 2, -1, Kelompok.PEDAGANG, Tema.AIR),
	/** Penenun Sutra (Rumah Sutra, at the loom entity): tied sleeves, apron. */
	PENENUN_SUTRA(0xE8A8B8, true, 1, 1, -1, Kelompok.PEKERJA, Tema.AIR),
	/** Pemelihara Ulat (rumah ulat): leaf basket. */
	PEMELIHARA_ULAT(0x6E8E4E, false, 1, 1, -1, Kelompok.PEKERJA, Tema.AIR),
	/** Ahli Teh (Paviliun Teh Awan): soft scholar's cap, tea-green robe. */
	AHLI_TEH(0x3E6E4E, true, 2, 2, -1, Kelompok.CENDEKIA, Tema.AIR),
	/** Pemetik Teh (teras teh): straw hat, small back basket. */
	PEMETIK_TEH(0x6E8E4E, false, 1, 1, -1, Kelompok.PEKERJA, Tema.AIR),
	/** Pendayung (Perahu Pos, Pasar Air Pagi): blue straw hat, head cloth; yuloh oar. */
	PENDAYUNG(0x2E5A7A, false, 1, 1, -1, Kelompok.PEKERJA, Tema.AIR),
	/** Sastrawan (Paviliun Menyimak Hujan, Paviliun Bulan di Air): scholar's cap, fan. */
	SASTRAWAN(0xE8DDC0, false, 2, 2, -1, Kelompok.CENDEKIA, Tema.AIR),

	// ===================================================================================== 38.T.6.3 Kota Gunung

	/** Kepala Biara (Aula Emas): red-gold kasaya, nine-ring staff. */
	KEPALA_BIARA(0xC9A24A, true, 4, 4, -1, Kelompok.RAHIB, Tema.GUNUNG),
	/** Biksu Gunung (biara, Menara Lonceng Awan): grey robe, prayer beads, monk's straw hat when going down. */
	BIKSU_GUNUNG(0x8A8278, false, 2, 2, -1, Kelompok.RAHIB, Tema.GUNUNG),
	/** Penjaga Lift (stasiun kereta gantung, katrol): thick gloves, wheel key. */
	PENJAGA_LIFT(0x6A4A30, true, 1, 1, -1, Kelompok.PEKERJA, Tema.GUNUNG),
	/** Kuli Gunung (Pos Kuli, jalur tangga): head cloth, vest, carrying frame. */
	KULI_GUNUNG(0x8A7A60, false, 1, 1, -1, Kelompok.PEKERJA, Tema.GUNUNG),
	/** Pemetik Herba (jalan papan tebing, aula herba): coiled climbing rope, back basket, small sickle. */
	PEMETIK_HERBA(0x6E8E4E, false, 1, 1, -1, Kelompok.PEKERJA, Tema.GUNUNG),
	/** Peziarah (jalur ziarah, Rumah Tamu Biara): yellow headband, incense bag, bamboo staff (Jadwal mixes PEKERJA). */
	PEZIARAH(0xE8C860, false, 0, 0, -1, Kelompok.TETUA, Tema.GUNUNG),

	// ===================================================================================== 38.T.7.3 Kota Iblis (kota malam: jadwal digeser 12 jam)

	/** Penguasa Iblis (istana tengkorak): black-red dragon robe, horned crown; tier 5 (realm lord); no daily schedule. */
	PENGUASA_IBLIS(0x1A1A20, true, 5, 5, -1, Kelompok.KHUSUS, Tema.IBLIS),
	/** Penegak Iblis (gerbang, Zona Perjanjian): iron mask, shoulder chains, cleaver; tier 3-4. */
	PENEGAK_IBLIS(0x2A2A30, false, 3, 4, -1, Kelompok.PENJAGA, Tema.IBLIS),
	/** Jagal Arena (Arena Darah): bloody leather apron, great axe. */
	JAGAL_ARENA(0x6A1420, true, 3, 3, -1, Kelompok.PEJABAT, Tema.IBLIS),
	/** Gladiator Iblis (ruang gladiator, Arena Darah): incomplete armour, scars; tier 2-5. */
	GLADIATOR_IBLIS(0x4A3A3A, false, 2, 5, -1, Kelompok.KULTIVATOR, Tema.IBLIS),
	/** Penjaga Kitab Terlarang (Menara Kitab Terlarang): cloth blindfold, inscribed robe. */
	PENJAGA_KITAB_TERLARANG(0x4A2A5A, true, 4, 4, -1, Kelompok.CENDEKIA, Tema.IBLIS),
	/** Pembunuh Bayaran (Sarang Kontrak): hood, twin knives. */
	PEMBUNUH_BAYARAN(0x1E1A18, false, 3, 3, -1, Kelompok.KULTIVATOR, Tema.IBLIS),
	/** Warga Iblis (rumah, kedai, arena): dark robe; not townsfolk (38.T.14.3). */
	WARGA_IBLIS(0x3A2A30, false, 1, 1, -1, Kelompok.PEKERJA, Tema.IBLIS),
	/** Pemburu Lurus (pos pemburu, penyerbuan lurus only): righteous-sect emblem robe, sword; tier 3-4. */
	PEMBURU_LURUS(0xEDE6D6, false, 3, 4, -1, Kelompok.KULTIVATOR, Tema.IBLIS),

	// ===================================================================================== 42.12.2 Kota yang Hidup (42A)

	/** Pencopet (pasar, jalan ramai; 42D Copet): patched grey jacket, cap; not townsfolk for Nama Baik. */
	PENCOPET(0x5A5650, false, 1, -1, Kelompok.PEKERJA),
	/** Penjaga Malam (ronda 42D): kentongan, paper lantern on a pole, padded coat. */
	PENJAGA_MALAM(0x2E3A4C, false, 1, -1, Kelompok.PENJAGA),
	/** Mak Comblang (matchmaker, 42.4.6): red flower in the hair, red fan. */
	MAK_COMBLANG(0xB0303A, false, 0, -1, Kelompok.TETUA),
	/** Pendongeng (storyteller at the tea house / square, Lao Shuo): folding fan, clapper. */
	PENDONGENG(0x6A5A3A, true, 1, -1, Kelompok.CENDEKIA),
	/** Pengamen (street musician): erhu or pipa, worn hat. */
	PENGAMEN(0x7A6A4A, false, 0, -1, Kelompok.PEDAGANG),
	/** Akrobat (travelling troupe 42.7.1): sash, tight trousers, plates on sticks. */
	AKROBAT(0xC8503A, false, 1, -1, Kelompok.PEKERJA),
	/** Penjual Obat (medicine hawker with a banner pole and gourd). */
	PENJUAL_OBAT(0x5C7C3A, false, 1, -1, Kelompok.PEDAGANG),
	/** Guru Sekolah (village school, children's lessons): scholar's cap, cane, scroll. */
	GURU_SEKOLAH(0x3A4A6A, false, 1, -1, Kelompok.CENDEKIA),
	/** Pengurus Duka (funeral master, 42.7.7): white hemp robe, bell. */
	PENGURUS_DUKA(0xE6E2D6, false, 1, -1, Kelompok.RAHIB),
	/** Penjaga Gudang (town warehouse, 42.6.5): ledger, keys on the belt. */
	PENJAGA_GUDANG(0x6A5A40, true, 1, -1, Kelompok.PEDAGANG),
	/** Pialang (trading house broker, 42.6.4-42.6.5): silk vest, abacus. */
	PIALANG(0x2E5A4A, true, 2, -1, Kelompok.PEDAGANG),
	/** Penari (troupe and festival dancer): long water sleeves. */
	PENARI(0xE07A9A, false, 0, -1, Kelompok.PEKERJA),
	/** Pemusik (wedding / funeral / troupe musician): suona or drum. */
	PEMUSIK(0xA0603A, false, 0, -1, Kelompok.PEKERJA);

	/** Theme ids of 38.T (same strings as {@code kota_tema/<id>} and {@code KotaTema}). */
	public static final class Tema {
		public static final String PELABUHAN = "pelabuhan";
		public static final String OASIS = "oasis";
		public static final String AIR = "air";
		public static final String GUNUNG = "gunung";
		public static final String IBLIS = "iblis";

		private Tema() {
		}
	}

	/**
	 * The daily schedule groups of 38.N.3 (columns of the hour table). {@code KHUSUS} = no daily schedule: the role stays
	 * at its anchor or is driven by its owning module (ghosts, captives, demonic lords).
	 */
	public enum Kelompok {
		PEKERJA, PEDAGANG, PENJAGA, ANAK, TETUA, KULTIVATOR, CENDEKIA, RAHIB, PEJABAT, KHUSUS;

		/** "pekerja", "pedagang"... (commands, data files {@code tempat/jadwal/*.json}). */
		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public final int robe;
	public final boolean stationary;
	public final int tier;
	/** Top of the tier range ("3-4" in 38.N.2/38.T.7.3); equal to {@link #tier} for a single tier. */
	public final int tierMax;
	public final int tab;
	public final Kelompok kelompok;
	/** Theme id ({@link Tema}) of a themed-city role, null for every other role. */
	public final @Nullable String tema;

	NpcRole(int robe, boolean stationary, int tier, int tab, Kelompok kelompok) {
		this(robe, stationary, tier, tier, tab, kelompok, null);
	}

	NpcRole(int robe, boolean stationary, int tier, int tierMax, int tab, Kelompok kelompok, @Nullable String tema) {
		this.robe = robe;
		this.stationary = stationary;
		this.tier = tier;
		this.tierMax = Math.max(tier, tierMax);
		this.tab = tab;
		this.kelompok = kelompok;
		this.tema = tema;
	}

	/** "juru_lelang", "pengembara"... (commands, lang keys and the DESIGN 40 look key {@code peran.<id>}). */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String titleKey() {
		return "kultivator.kota.peran." + id();
	}

	/** The DESIGN 40.4 fallback look row for roles without their own row: {@code kelompok.<KELOMPOK>}. */
	public String rupaKelompok() {
		return "kelompok." + kelompok.name();
	}

	/** True for the roles of the Immortal City. */
	public boolean immortal() {
		return this == ABADI || this == PENCERAMAH || this == PEDAGANG_ABADI || this == JURU_LELANG_ABADI || this == UTUSAN || this == PENJAGA_KEBUN;
	}

	/**
	 * True for the demonic roles: the lair elder of 38.N.2 and the Kota Iblis roles of 38.T.7.3 except the visiting
	 * righteous hunters. The Kota Iblis roles keep a night schedule (hours shifted by 12, {@link #malam()}).
	 */
	public boolean iblis() {
		return this == TETUA_IBLIS || (Tema.IBLIS.equals(this.tema) && this != PEMBURU_LURUS);
	}

	/** True when the role lives by night: the 38.T.7.3 schedule shift of 12 hours (Kota Iblis roles). */
	public boolean malam() {
		return Tema.IBLIS.equals(this.tema) && this != PEMBURU_LURUS;
	}

	/** True for children (DESIGN 40 usia ANAK, body scale 0.6; flee home first in danger, 38.N.4). */
	public boolean anak() {
		return this == ANAK;
	}

	/** True for a combat-capable role (tier >= 2): joins a fight in danger instead of fleeing (38.N.4 butir 11). */
	public boolean petarung() {
		return this.tier >= 2 && this.kelompok != Kelompok.ANAK;
	}

	/**
	 * True for the town's own people (their harm lowers Nama Baik and wakes the guards). Every peaceful settled role of
	 * 38 is townsfolk (civil roles, clan and NPC-sect residents of their compound, themed-city people); not the
	 * outsiders (wanderers, rival groups, visiting sect champions, righteous hunters), not the cave hermit, the ghost
	 * remnant or captives, and no demonic role ({@code WARGA_IBLIS} included, 38.T.14.3).
	 */
	public boolean townsfolk() {
		return switch (this) {
			case PENGEMBARA, SAINGAN, JUARA_SEKTE, PERTAPA, SISA_JIWA, TAWANAN, PEMBURU_LURUS, PENCOPET -> false;
			default -> !iblis();
		};
	}

	public static NpcRole byOrdinal(int ordinal) {
		NpcRole[] all = values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : WARGA;
	}

	public static NpcRole byId(String id) {
		for (NpcRole role : values()) {
			if (role.id().equals(id)) {
				return role;
			}
		}
		return null;
	}
}
