package dev.kultivator.dunia.tempat.hidup;

import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * The work of every role (DESIGN 38.N.2 equipment column, 38.N.3 "kerja" rows, 38.N.5 clips, 38.T.3.3-38.T.7.3 themed
 * roles, 38.T.8.1 clips 79-107): the work titik it goes to, the looping clip it plays there, its props, an occasional
 * once clip ({@code sela}, e.g. the vendor's call or the judge's gavel) and, for carriers, the second titik it shuttles
 * to. A role has one to four variants; a resident keeps the variant of its seed ({@link #untuk}).
 *
 * <p>Work has a rhythm (detail pass): many kinds have an interlude ({@code selingan}), a second looping clip played at the
 * same spot for twelve world minutes of every hour ({@link #selinganSekarang}): the cook fans the stove between stirs,
 * the alchemist rolls pills after grinding, the physician takes a pulse between notes, the farmer leans on the hoe, the
 * outer disciple holds the horse stance between sword forms.
 */
public final class KerjaPeran {

	/**
	 * One kind of work.
	 *
	 * @param titik    work titik type ({@code kerja:<alat>}, {@code lapak}, {@code jaga}, {@code latihan}, ...)
	 * @param klip     looping clip at the spot
	 * @param kanan    right-hand prop
	 * @param kiri     left-hand prop
	 * @param punggung back prop
	 * @param sela     once clip played now and then (0 = none)
	 * @param pindah   second titik type a carrier shuttles to (null = stays)
	 * @param jalan    walking clip while carrying (0 = the role's walk)
	 * @param varian   variant of {@code klip}
	 * @param selingan interlude loop clip at the same spot (0 = none)
	 * @param selKanan right-hand prop of the interlude (0 = the clip's own)
	 */
	public record Kerja(String titik, int klip, int kanan, int kiri, int punggung, int sela, String pindah, int jalan, int varian,
			int selingan, int selKanan) {

		/** The work as a schedule activity at the resident's own work spot. */
		public Kegiatan kegiatan() {
			return new Kegiatan("kerja", Kegiatan.KERJA, klip, varian, kanan, kiri, punggung, jalan, false);
		}

		/** The interlude at the same spot (the left and back props stay). */
		public Kegiatan kegiatanSelingan() {
			return new Kegiatan("kerja", Kegiatan.KERJA, selingan, 0, selKanan, kiri, punggung, jalan, false);
		}

		/** The same work with clip variant {@code v}. */
		Kerja v(int v) {
			return new Kerja(titik, klip, kanan, kiri, punggung, sela, pindah, jalan, v, selingan, selKanan);
		}

		/** The same work with an interlude loop {@code klipSelingan} holding {@code prop}. */
		Kerja sel(int klipSelingan, int prop) {
			return new Kerja(titik, klip, kanan, kiri, punggung, sela, pindah, jalan, varian, klipSelingan, prop);
		}

		/** The same work with the once clip {@code klipSela}. */
		Kerja sela(int klipSela) {
			return new Kerja(titik, klip, kanan, kiri, punggung, klipSela, pindah, jalan, varian, selingan, selKanan);
		}
	}

	private static final Map<NpcRole, Kerja[]> KERJA = new EnumMap<>(NpcRole.class);

	private static Kerja k(String titik, int klip, int kanan) {
		return new Kerja(titik, klip, kanan, 0, 0, 0, null, 0, 0, 0, 0);
	}

	private static Kerja k(String titik, int klip, int kanan, int kiri, int punggung, int sela) {
		return new Kerja(titik, klip, kanan, kiri, punggung, sela, null, 0, 0, 0, 0);
	}

	private static Kerja angkut(String dari, String ke, int klip, int kanan, int punggung, int jalan) {
		return new Kerja(dari, klip, kanan, 0, punggung, GerakId.ANGKAT_BAWA, ke, jalan, 0, 0, 0);
	}

	private static void put(NpcRole role, Kerja... variants) {
		KERJA.put(role, variants);
	}

	static {
		// ---------------------------------------------------------------- 34 (old towns, Immortal City)
		put(NpcRole.JURU_LELANG, k("panggung", GerakId.BICARA_TANGAN, PropId.PALU_KAYU, 0, 0, GerakId.SERU_DAGANG));
		put(NpcRole.PEMILIK_TEH, k("kerja:teko", GerakId.MINUM_TEH, PropId.CANGKIR, PropId.LEPEK, 0, GerakId.TUANG_TEH));
		put(NpcRole.MAKELAR_GELAP, k("lapak", GerakId.HITUNG_KOIN_GELAP, PropId.KANTONG_UANG, 0, 0, GerakId.BISIK));
		put(NpcRole.PEDAGANG_PIL, k("lapak", GerakId.HITUNG_SEMPOA, PropId.SEMPOA, 0, 0, GerakId.SERU_DAGANG).sel(GerakId.GULUNG_PIL, PropId.PIL),
				k("kerja:lumpang", GerakId.TUMBUK_HERBA, PropId.ALU).sel(GerakId.RACIK_HERBA, PropId.HERBA));
		put(NpcRole.PANDAI_PUSAKA, k("kerja:palu", GerakId.PALU_LANDASAN, PropId.PALU).sel(GerakId.KIPAS_TUNGKU, PropId.KIPAS_TUNGKU));
		put(NpcRole.PENJAGA, k("jaga", GerakId.BERDIRI_JAGA, 0).sel(GerakId.SIKAP_TANGAN_BELAKANG, 0));
		put(NpcRole.WARGA, k("kerja:sapu", GerakId.SAPU, PropId.SAPU).sel(GerakId.BERSANDAR, PropId.SAPU),
				k("kerja:lesung", GerakId.TUMBUK_HERBA, PropId.ALU).sela(GerakId.MENGUAP_PEREGANGAN),
				angkut("lapak", "peti_milik", GerakId.ANGKAT_AIR, PropId.EMBER, 0, GerakId.ANGKAT_AIR));
		put(NpcRole.PENGEMBARA, k("latihan", GerakId.LATIHAN_PEDANG_1, 0).sel(GerakId.ASAH_PEDANG, 0));
		put(NpcRole.SAINGAN, k("latihan", GerakId.LATIHAN_PEDANG_2, 0).sela(GerakId.TEBAS_UDARA).sel(GerakId.SIKAP_ANGKUH, 0));
		put(NpcRole.ABADI, k("meditasi", GerakId.NAPAS_MEDITASI, 0).sel(GerakId.SIRKULASI_QI, 0));
		put(NpcRole.PENCERAMAH, k("panggung", GerakId.BICARA_TANGAN, PropId.KIPAS_LIPAT).sel(GerakId.MENGAJAR, PropId.KIPAS_LIPAT));
		put(NpcRole.PEDAGANG_ABADI, k("lapak", GerakId.HITUNG_SEMPOA, PropId.SEMPOA, 0, 0, GerakId.SERU_DAGANG));
		put(NpcRole.JURU_LELANG_ABADI, k("panggung", GerakId.BICARA_TANGAN, PropId.PALU_KAYU, 0, 0, GerakId.SERU_DAGANG));
		put(NpcRole.UTUSAN, k("jaga", GerakId.BERDIRI_JAGA, PropId.PAPAN_TITAH));
		put(NpcRole.PENJAGA_KEBUN, k("kerja:sabit", GerakId.MEMANEN, 0, PropId.KERANJANG, 0, 0).v(GerakId.V_MEMANEN_SABIT)
				.sel(GerakId.RACIK_HERBA, PropId.HERBA));

		// ---------------------------------------------------------------- 38.N.2
		put(NpcRole.PANGERAN_KOTA, k("meja_tulis", GerakId.MENULIS, PropId.KUAS).sel(GerakId.MEMBACA, PropId.GULUNGAN),
				k("latihan", GerakId.LATIHAN_PEDANG_2, 0).sela(GerakId.TEBAS_UDARA).sel(GerakId.SIKAP_TANGAN_BELAKANG, 0));
		put(NpcRole.MENTERI, k("meja_tulis", GerakId.MENULIS, PropId.KUAS, PropId.PAPAN_CATATAN, 0, 0),
				k("obrol", GerakId.BICARA_TANGAN, PropId.PAPAN_CATATAN));
		put(NpcRole.PENGAWAL_ISTANA, k("jaga", GerakId.BERDIRI_JAGA, PropId.TOMBAK).sel(GerakId.SIKAP_TOMBAK, PropId.TOMBAK));
		put(NpcRole.KAPTEN_PENJAGA, k("jaga", GerakId.BERDIRI_JAGA, 0, 0, 0, GerakId.HORMAT).sel(GerakId.SIKAP_BERKACAK, 0));
		put(NpcRole.PENJAGA_GERBANG, k("jaga", GerakId.BERDIRI_JAGA, PropId.TOMBAK).sel(GerakId.SIKAP_TOMBAK, PropId.TOMBAK));
		put(NpcRole.HAKIM, k("meja_tulis", GerakId.MENULIS, PropId.KUAS, 0, 0, GerakId.PALU_HAKIM));
		put(NpcRole.BANKIR, k("kerja:timbangan", GerakId.HITUNG_SEMPOA, PropId.SEMPOA).sel(GerakId.MENULIS, PropId.KUAS),
				k("meja_tulis", GerakId.MENULIS, PropId.KUAS).sel(GerakId.HITUNG_SEMPOA, PropId.SEMPOA));
		put(NpcRole.BANDAR_BATU, k("kerja:pahat", GerakId.TAWAR, PropId.PAHAT, PropId.BATU_MENTAH, 0, GerakId.SERU_DAGANG));
		put(NpcRole.PEMOTONG_BATU, k("kerja:pahat", GerakId.PALU_LANDASAN, PropId.PAHAT, PropId.BATU_MENTAH, 0, 0));
		put(NpcRole.PEDAGANG_BINATANG, k("lapak", GerakId.LEMPAR_PAKAN, 0, PropId.TALI_KEKANG, 0, GerakId.TAWAR)
				.sel(GerakId.BERI_MAKAN_BINATANG, PropId.HERBA));
		put(NpcRole.PEDAGANG_SENJATA, k("lapak", GerakId.HITUNG_SEMPOA, PropId.SEMPOA, 0, 0, GerakId.SERU_DAGANG).sel(GerakId.ASAH_PEDANG, 0),
				k("lapak", GerakId.ASAH_PEDANG, 0, 0, 0, GerakId.SERU_DAGANG).sel(GerakId.HITUNG_SEMPOA, PropId.SEMPOA));
		put(NpcRole.EMPU, k("kerja:palu", GerakId.PALU_LANDASAN, PropId.PALU).sel(GerakId.KIPAS_TUNGKU, PropId.KIPAS_TUNGKU),
				k("kerja:tungku", GerakId.KIPAS_TUNGKU, PropId.KIPAS_TUNGKU).sel(GerakId.ASAH_PEDANG, 0));
		put(NpcRole.ALKEMIS, k("kerja:lumpang", GerakId.TUMBUK_HERBA, PropId.ALU).sel(GerakId.RACIK_HERBA, PropId.HERBA),
				k("kerja:tungku", GerakId.KIPAS_TUNGKU, PropId.KIPAS_TUNGKU).sel(GerakId.GULUNG_PIL, PropId.PIL),
				k("meja_tulis", GerakId.RACIK_HERBA, PropId.HERBA).sel(GerakId.MENULIS, PropId.KUAS));
		put(NpcRole.KETUA_GILDA, k("meja_tulis", GerakId.MENULIS, PropId.KUAS, PropId.GULUNGAN, 0, 0).sel(GerakId.MEMBACA, PropId.GULUNGAN));
		put(NpcRole.PETUGAS_GILDA, k("kerja:meja_tulis", GerakId.MENULIS, PropId.KUAS, PropId.GULUNGAN, 0, 0).sel(GerakId.HITUNG_SEMPOA, PropId.SEMPOA));
		put(NpcRole.PENGUJI, k("latihan", GerakId.LATIHAN_PEDANG_3, 0).sela(GerakId.TEBAS_UDARA).sel(GerakId.SIKAP_LIPAT_TANGAN, 0));
		put(NpcRole.PENJAGA_FORMASI, k("jaga", GerakId.BERDIRI_JAGA, PropId.PIRINGAN_FORMASI).sela(GerakId.SEGEL_TANGAN)
				.sel(GerakId.TULIS_JIMAT, PropId.KERTAS_JIMAT),
				k("meja_tulis", GerakId.TULIS_JIMAT, PropId.KERTAS_JIMAT).sela(GerakId.TEMPEL_JIMAT));
		put(NpcRole.NAKHODA, k("tambatan", GerakId.TEROPONG_LAUT, PropId.TEROPONG), k("jaga", GerakId.TEROPONG_LAUT, PropId.TEROPONG));
		put(NpcRole.KULI, angkut("peti_milik", "lapak", GerakId.ANGKAT_BAWA, 0, PropId.PIKULAN, GerakId.JALAN_PIKUL),
				angkut("tambatan", "peti_milik", GerakId.ANGKAT_BAWA, PropId.PETI, 0, GerakId.JALAN_BAWA));
		put(NpcRole.PEMILIK_PENGINAPAN, k("lapak", GerakId.HITUNG_SEMPOA, PropId.SEMPOA, PropId.KUNCI_KAMAR, 0, GerakId.LAMBAI)
				.sel(GerakId.MENULIS, PropId.KUAS));
		put(NpcRole.KOKI, k("kerja:kuali", GerakId.MASAK, PropId.SENDOK_SAYUR).sel(GerakId.KIPAS_TUNGKU, PropId.KIPAS_TUNGKU),
				k("kerja:tungku", GerakId.KIPAS_TUNGKU, PropId.KIPAS_TUNGKU).sel(GerakId.MASAK, PropId.SENDOK_SAYUR));
		put(NpcRole.PELAYAN, angkut("kerja:kuali", "meja_makan", GerakId.BERDIRI_JAGA, PropId.NAMPAN, 0, GerakId.JALAN_BAWA));
		put(NpcRole.PUSTAKAWAN, k("kerja:rak", GerakId.RAK_SUSUN, PropId.KITAB).sel(GerakId.MEMBACA, PropId.KITAB),
				k("meja_tulis", GerakId.MENULIS, PropId.KUAS).sel(GerakId.MEMBACA, PropId.GULUNGAN));
		put(NpcRole.PENCERAMAH_FANA, k("panggung", GerakId.BICARA_TANGAN, PropId.KIPAS_LIPAT).sel(GerakId.MENGAJAR, PropId.KIPAS_LIPAT),
				k("meja_tulis", GerakId.MEMBACA, PropId.KITAB).sel(GerakId.MENULIS, PropId.KUAS));
		put(NpcRole.RAHIB, k("kerja:sapu", GerakId.SAPU, PropId.SAPU, 0, 0, 0).sel(GerakId.SIKAP_DOA, PropId.TASBIH_TANGAN),
				k("dupa", GerakId.MEMBACA, PropId.KITAB, 0, 0, GerakId.SUJUD_DUPA).sel(GerakId.TULIS_JIMAT, PropId.KERTAS_JIMAT));
		put(NpcRole.TABIB, k("kerja:lumpang", GerakId.TUMBUK_HERBA, PropId.ALU, 0, PropId.KOTAK_OBAT, 0).sel(GerakId.RACIK_HERBA, PropId.HERBA),
				k("meja_tulis", GerakId.MENULIS, PropId.KUAS).sel(GerakId.PERIKSA_NADI, 0));
		// craft work at the bench (carving with mallet and chisel, looking a plank over); hammering at walls and plastering
		// belong to repair crews and are only seen where something is really damaged (38.R)
		put(NpcRole.TUKANG, k("kerja:gergaji", GerakId.PERBAIKI_PALU, PropId.PAHAT).sel(GerakId.LIHAT_BARANG, PropId.PAPAN_KAYU),
				angkut("kerja:gergaji", "peti_milik", GerakId.ANGKAT_PAPAN, PropId.PAPAN_KAYU, 0, GerakId.ANGKAT_PAPAN));
		put(NpcRole.PENJAGA_TEKA, k("lapak", GerakId.BICARA_TANGAN, PropId.LENTERA_TANGAN, 0, 0, GerakId.SERU_DAGANG));
		put(NpcRole.JUARA_SEKTE, k("latihan", GerakId.LATIHAN_PEDANG_2, 0).sela(GerakId.TEBAS_UDARA).sel(GerakId.SIRKULASI_QI, 0),
				k("latihan", GerakId.TANDING_A, 0));
		put(NpcRole.KEPALA_DESA, k("obrol", GerakId.BICARA_TANGAN, 0, PropId.TONGKAT, 0, GerakId.ANGGUK_DENGAR));
		put(NpcRole.PETANI, k("kerja:cangkul", GerakId.MENCANGKUL, PropId.CANGKUL).sela(GerakId.MENGUAP_PEREGANGAN)
				.sel(GerakId.BERSANDAR, PropId.CANGKUL));
		put(NpcRole.NELAYAN, k("kerja:pancing", GerakId.MEMANCING, PropId.JORAN).sela(GerakId.MENGUAP_PEREGANGAN),
				k("kerja:jala", GerakId.TARIK_JALA, PropId.JALA).sel(GerakId.IKAT_TALI, PropId.TALI));
		put(NpcRole.PANDAI_DESA, k("kerja:palu", GerakId.PALU_LANDASAN, PropId.PALU).sel(GerakId.KIPAS_TUNGKU, PropId.KIPAS_TUNGKU));
		put(NpcRole.PEDAGANG_KELILING, k("lapak", GerakId.TAWAR, 0, 0, PropId.KERANJANG, GerakId.SERU_DAGANG));
		put(NpcRole.ANAK, k("main", GerakId.MAIN_KEJAR, 0), k("main", GerakId.MAIN_LOMPAT, 0), k("main", GerakId.MAIN_GASING, PropId.GASING),
				k("main", GerakId.MAIN_LAYANGAN, PropId.LAYANG_LAYANG));
		put(NpcRole.TETUA_DESA, k("kursi", GerakId.CATUR, 0), k("kursi", GerakId.KIPAS, PropId.KIPAS_LIPAT).sel(GerakId.MAIN_SERULING, PropId.SERULING),
				k("kursi", GerakId.CERITA, 0).sela(GerakId.TERTAWA));
		put(NpcRole.PATRIARK_KLAN, k("meditasi", GerakId.NAPAS_MEDITASI, 0).sel(GerakId.SIRKULASI_QI, 0),
				k("kursi", GerakId.DUDUK_KURSI, 0).sel(GerakId.MINUM_TEH, PropId.CANGKIR));
		put(NpcRole.TUAN_MUDA, k("obrol", GerakId.KIPAS, PropId.KIPAS_LIPAT).sel(GerakId.SIKAP_ANGKUH, PropId.KIPAS_LIPAT),
				k("latihan", GerakId.LATIHAN_PEDANG_1, 0).sela(GerakId.TEBAS_UDARA),
				k("pandang", GerakId.MAIN_SERULING, PropId.SERULING).sel(GerakId.KIPAS, PropId.KIPAS_LIPAT));
		put(NpcRole.TETUA_KLAN, k("latihan", GerakId.TAI_CHI_PAGI, 0).sel(GerakId.MENGAJAR, PropId.KEBUT),
				k("meja_tulis", GerakId.MEMBACA, PropId.KITAB).sel(GerakId.MENULIS, PropId.KUAS));
		put(NpcRole.MURID_KLAN, k("latihan", GerakId.LATIHAN_TINJU, 0).sel(GerakId.TIANG_BERDIRI, 0),
				k("latihan", GerakId.LATIHAN_TOMBAK, PropId.TOMBAK).sel(GerakId.TELAPAK_LATIHAN, 0));
		put(NpcRole.MURID_SEKTE_LUAR, k("latihan", GerakId.LATIHAN_PEDANG_1, 0).sel(GerakId.TELAPAK_LATIHAN, 0),
				k("latihan", GerakId.LATIHAN_TOMBAK, PropId.TOMBAK).sel(GerakId.TIANG_BERDIRI, 0),
				k("latihan", GerakId.LATIHAN_TINJU, 0).sel(GerakId.TIANG_BERDIRI, 0),
				k("latihan", GerakId.TELAPAK_LATIHAN, 0).sela(GerakId.SEGEL_TANGAN).sel(GerakId.LATIHAN_PEDANG_1, PropId.PEDANG_KAYU));
		put(NpcRole.MURID_SEKTE_DALAM, k("latihan", GerakId.LATIHAN_PEDANG_3, 0).sela(GerakId.TEBAS_UDARA).sel(GerakId.SIRKULASI_QI, 0),
				k("latihan", GerakId.TANDING_A, 0),
				k("meditasi", GerakId.NAPAS_MEDITASI, 0).sela(GerakId.SEGEL_TANGAN).sel(GerakId.SIRKULASI_QI, 0));
		put(NpcRole.TETUA_SEKTE, k("meja_tulis", GerakId.MENULIS, PropId.KUAS).sel(GerakId.MEMBACA, PropId.GULUNGAN),
				k("latihan", GerakId.TAI_CHI_PAGI, 0).sel(GerakId.MENGAJAR, PropId.KEBUT));
		put(NpcRole.PELAYAN_SEKTE, k("kerja:sapu", GerakId.SAPU, PropId.SAPU),
				angkut("kerja:kuali", "meja_makan", GerakId.BERDIRI_JAGA, PropId.NAMPAN, 0, GerakId.JALAN_BAWA));
		put(NpcRole.PERTAPA, k("meditasi", GerakId.NAPAS_MEDITASI, 0).sela(GerakId.SEGEL_TANGAN).sel(GerakId.SIRKULASI_QI, 0));
		put(NpcRole.SISA_JIWA, k("meditasi", GerakId.NAPAS_MEDITASI, 0));
		put(NpcRole.TETUA_IBLIS, k("meditasi", GerakId.SEMEDI_DARAH, 0));
		put(NpcRole.TAWANAN, k("sel_tawanan", GerakId.BERLUTUT_RANTAI, PropId.RANTAI_TANGAN));
		put(NpcRole.PENGEMIS, k("kursi_lantai", GerakId.DUDUK_LANTAI, PropId.MANGKUK, 0, 0, GerakId.LAMBAI));

		// ---------------------------------------------------------------- 38.T.3.3 Kota Pelabuhan
		put(NpcRole.SYAHBANDAR, k("meja_tulis", GerakId.MENULIS, PropId.KUAS, PropId.PAPAN_CATATAN, 0, 0).sel(GerakId.HITUNG_SEMPOA, PropId.SEMPOA));
		put(NpcRole.PETUGAS_BEA, k("tambatan", GerakId.MENULIS, PropId.KUAS, PropId.PAPAN_CATATAN, 0, GerakId.TUNJUK));
		put(NpcRole.PELAUT, k("kerja:jala", GerakId.TARIK_JALA, PropId.JALA), k("tambatan", GerakId.IKAT_TALI, PropId.TALI),
				angkut("tambatan", "peti_milik", GerakId.ANGKAT_BAWA, PropId.PETI, 0, GerakId.JALAN_BAWA));
		put(NpcRole.NAKHODA_LAUT, k("tambatan", GerakId.TEROPONG_LAUT, PropId.TEROPONG));
		put(NpcRole.PENJAGA_SUAR, k("jaga_suar", GerakId.TEROPONG_LAUT, PropId.TEROPONG, PropId.LENTERA_TANGAN, 0, 0));
		put(NpcRole.TUKANG_KAPAL, k("kerja:pahat", GerakId.PALU_LUNAS, PropId.PALU_DEMPUL, PropId.PAHAT, 0, 0),
				k("kerja:gergaji", GerakId.IKAT_TALI, PropId.TALI));
		put(NpcRole.PENJUAL_IKAN, k("lapak", GerakId.JEMUR_IKAN, PropId.IKAN_TALI, 0, 0, GerakId.SERU_DAGANG));
		put(NpcRole.PEDAGANG_SEBERANG, k("lapak", GerakId.TAWAR_BAZAR, 0, 0, 0, GerakId.SERU_DAGANG));
		put(NpcRole.PENDETA_LAUT, k("dupa", GerakId.MEMBACA, PropId.KITAB, 0, 0, GerakId.SUJUD_DUPA));

		// ---------------------------------------------------------------- 38.T.4.3 Kota Oasis Gurun
		put(NpcRole.TUAN_BENTENG, k("kursi", GerakId.KIPAS, PropId.KIPAS_SUTRA), k("meja_tulis", GerakId.MENULIS, PropId.KUAS));
		put(NpcRole.KAFILAH_BASHI, k("kandang_unta", GerakId.TAWAR, 0, PropId.CAMBUK_KAFILAH, 0, GerakId.TUNJUK));
		put(NpcRole.PENUNTUN_UNTA, k("kandang_unta", GerakId.PIMPIN_UNTA, PropId.TALI_KEKANG),
				k("kandang_unta", GerakId.LEMPAR_PAKAN, 0).sel(GerakId.BERI_MAKAN_BINATANG, PropId.HERBA));
		put(NpcRole.PEDAGANG_BAZAR, k("lapak", GerakId.TAWAR_BAZAR, 0, 0, 0, GerakId.SERU_DAGANG));
		put(NpcRole.PENDETA_AIR, k("dupa", GerakId.NAPAS_MEDITASI, 0, 0, 0, GerakId.SUJUD_DUPA), k("sumur_timba", GerakId.TIMBA_SUMUR, 0));
		put(NpcRole.PELUKIS_GUA, k("kerja:kuas", GerakId.LUKIS_DINDING, PropId.KUAS_BESAR, PropId.PALET, 0, 0));
		put(NpcRole.PENARI_GURUN, k("panggung", GerakId.TARI_PUTAR, 0), k("obrol", GerakId.TERTAWA, 0));
		put(NpcRole.PENGAWAL_GURUN, k("jaga", GerakId.BERDIRI_JAGA, PropId.TOMBAK));

		// ---------------------------------------------------------------- 38.T.5.3 Kota Air
		put(NpcRole.NYONYA_SUTRA, k("lapak", GerakId.KIPAS, PropId.KIPAS_SUTRA, 0, 0, GerakId.SERU_DAGANG));
		put(NpcRole.PENENUN_SUTRA, k("alat_tenun", GerakId.TENUN, 0), k("kerja:tenun", GerakId.TENUN, 0));
		put(NpcRole.PEMELIHARA_ULAT, k("kerja:sabit", GerakId.MEMANEN, 0, PropId.KERANJANG, 0, 0).sel(GerakId.BERI_MAKAN_BINATANG, PropId.HERBA));
		put(NpcRole.AHLI_TEH, k("kerja:teko", GerakId.MINUM_TEH, PropId.CANGKIR, PropId.LEPEK, 0, GerakId.SEDUH_TEH));
		put(NpcRole.PEMETIK_TEH, k("kerja:sabit", GerakId.PETIK_TEH, 0, PropId.KERANJANG_TEH, 0, 0));
		put(NpcRole.PENDAYUNG, k("jalur_perahu", GerakId.DAYUNG_YULOH, PropId.DAYUNG_YULOH, 0, 0, GerakId.NYANYI_PERAHU));
		put(NpcRole.SASTRAWAN, k("meja_tulis", GerakId.MENULIS, PropId.KUAS).sel(GerakId.MEMBACA, PropId.GULUNGAN),
				k("pandang", GerakId.KIPAS, PropId.KIPAS_LIPAT).sel(GerakId.MAIN_GUQIN, PropId.GUQIN),
				k("kursi", GerakId.MAIN_GUQIN, PropId.GUQIN).sel(GerakId.MINUM_TEH, PropId.CANGKIR));

		// ---------------------------------------------------------------- 38.T.6.3 Kota Gunung
		put(NpcRole.KEPALA_BIARA, k("meditasi", GerakId.NAPAS_MEDITASI, 0, 0, PropId.TONGKAT_CINCIN, 0).sel(GerakId.MENGAJAR, PropId.TASBIH_TANGAN));
		put(NpcRole.BIKSU_GUNUNG, k("dupa", GerakId.TABUH_IKAN_KAYU, PropId.PEMUKUL_KECIL), k("kerja:sapu", GerakId.SAPU, PropId.SAPU));
		put(NpcRole.PENJAGA_LIFT, k("stasiun_lift", GerakId.PUTAR_KATROL, 0));
		put(NpcRole.KULI_GUNUNG, angkut("stasiun_lift", "jalur_tangga", GerakId.ANGKAT_BAWA, 0, PropId.RANGKA_PUNGGUNG, GerakId.DAKI_PIKUL));
		put(NpcRole.PEMETIK_HERBA, k("kerja:sabit", GerakId.PETIK_HERBA_TEBING, PropId.SABIT_HERBA));
		put(NpcRole.PEZIARAH, k("dupa", GerakId.DUDUK_LANTAI, 0, 0, PropId.TAS_DUPA, GerakId.SUJUD_ZIARAH));

		// ---------------------------------------------------------------- 38.T.7.3 Kota Iblis
		put(NpcRole.PENGUASA_IBLIS, k("meditasi", GerakId.SEMEDI_DARAH, 0));
		put(NpcRole.PENEGAK_IBLIS, k("jaga", GerakId.BERDIRI_JAGA, 0, 0, 0, GerakId.ANCAM).sel(GerakId.SIKAP_BERKACAK, 0));
		put(NpcRole.JAGAL_ARENA, k("arena_pintu", GerakId.ASAH_PISAU, PropId.PISAU_GANDA));
		put(NpcRole.GLADIATOR_IBLIS, k("latihan", GerakId.LATIHAN_TINJU, 0), k("kursi", GerakId.ASAH_PISAU, PropId.PISAU_GANDA));
		put(NpcRole.PENJAGA_KITAB_TERLARANG, k("kursi", GerakId.MEMBACA, PropId.KITAB).sel(GerakId.TIDUR_MEJA, 0));
		put(NpcRole.PEMBUNUH_BAYARAN, k("kursi", GerakId.ASAH_PISAU, PropId.PISAU_GANDA, 0, 0, GerakId.HITUNG_KOIN_GELAP));
		put(NpcRole.WARGA_IBLIS, k("obrol", GerakId.BICARA_TANGAN, PropId.CAWAN_ARAK), k("kerja:kuali", GerakId.MASAK, PropId.SENDOK_SAYUR),
				k("lapak", GerakId.HITUNG_KOIN_GELAP, PropId.KANTONG_UANG));
		put(NpcRole.PEMBURU_LURUS, k("jaga", GerakId.BERDIRI_JAGA, 0).sel(GerakId.ASAH_PEDANG, 0));
	}

	/** The work of {@code peran} for a resident with seed {@code benih} (variants by seed). */
	public static Kerja untuk(NpcRole peran, long benih) {
		Kerja[] all = KERJA.get(peran);
		if (all == null || all.length == 0) {
			return k("obrol", GerakId.BICARA_TANGAN, 0);
		}
		return all[(int) Math.floorMod(benih >>> 17, (long) all.length)];
	}

	/**
	 * The interlude of {@code kerja} for a resident with seed {@code benih} at world minute {@code menit}: twelve minutes of
	 * every hour, the start shifted per resident so a workshop never switches in step. Null when it works on.
	 */
	public static @Nullable Kegiatan selinganSekarang(Kerja kerja, long benih, int menit) {
		if (kerja.selingan() == 0 || kerja.pindah() != null) {
			return null;
		}
		int start = (int) Math.floorMod(benih >>> 23, 48L);
		int m = Math.floorMod(menit - start, 60);
		return m < 12 ? kerja.kegiatanSelingan() : null;
	}

	/** Every variant of {@code peran} (commands). */
	public static Kerja[] semua(NpcRole peran) {
		Kerja[] all = KERJA.get(peran);
		return all == null ? new Kerja[0] : all.clone();
	}

	/** True when the work of {@code peran} is out in the open (fields, ponds, terraces, cliffs): rain stops it. */
	public static boolean diLuar(Kerja kerja) {
		return switch (kerja.titik()) {
			case "kerja:cangkul", "kerja:pancing", "kerja:jala", "kerja:sabit", "main", "latihan", "tambatan", "kandang_unta",
					"jalur_tangga", "jalur_perahu", "obrol", "pandang" -> true;
			default -> false;
		};
	}

	private KerjaPeran() {
	}
}
