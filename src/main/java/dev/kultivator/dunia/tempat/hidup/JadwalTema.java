package dev.kultivator.dunia.tempat.hidup;

import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The built-in routines of the five themed towns (DESIGN 38.T.3.3, 38.T.4.3, 38.T.5.3, 38.T.6.3, 38.T.7.3; module
 * 38NL), their weather (the "Cuaca" lines of those sections) and the residents' side of the themed events of
 * {@code TemaKalender} (38.T.3.4-38.T.7.10, bus name {@code tema.<acara>}). A pure function like {@link Jadwal}: it only
 * answers the hours the design names and returns null for the rest, so the group column of 38.N.3 fills the day. A data
 * file {@code tempat/jadwal/kota_tema_<tema>*.json} of the theme agents wins over everything here ({@link JadwalData}).
 *
 * <p>Hours: {@code s} is the resident's own clock (its +-40 minute shift; for the Kota Iblis roles also the 12 hour night
 * shift of 38.T.7.3), {@code asli} the same without the night shift. The tables of 38.T.7.3 are written in real hours,
 * so the Kota Iblis rows read {@code asli}; every other table reads {@code s}.
 */
public final class JadwalTema {

	public static final String PELABUHAN = "kota_tema/pelabuhan";
	public static final String OASIS = "kota_tema/oasis";
	public static final String AIR = "kota_tema/air";
	public static final String GUNUNG = "kota_tema/gunung";
	public static final String IBLIS = "kota_tema/iblis";

	/** Themed events whose crowd fills the stands of the place ({@link Pemanggil} sends the seated far crowd). */
	private static final Set<String> TRIBUN = Set.of("tema.dewi_laut", "tema.tari_putar", "tema.perahu_naga", "tema.lentera_air",
			"tema.pesta_darah", "tema.lelang_gelap", "tema.cahaya_budha", "tema.kafilah_agung");

	private JadwalTema() {
	}

	// ================================================================== shared activities

	private static final Kegiatan MAKAN = new Kegiatan("makan", "meja_makan", GerakId.MAKAN, 0, PropId.SUMPIT, PropId.MANGKUK, 0, 0, false);
	private static final Kegiatan TEH = new Kegiatan("minum_teh", "kursi", GerakId.MINUM_TEH, 0, PropId.CANGKIR, PropId.LEPEK, 0, 0, false);
	private static final Kegiatan BASUH = Kegiatan.di("basuh_muka", Kegiatan.RUMAH, GerakId.BASUH_MUKA, 0);
	private static final Kegiatan TIDUR = Kegiatan.dalam("tidur");

	private static Kegiatan k(String kunci, String tujuan, int klip, int kanan) {
		return Kegiatan.di(kunci, tujuan, klip, kanan);
	}

	private static Kegiatan k(String kunci, String tujuan, int klip, int varian, int kanan, int kiri, int punggung, int jalan) {
		return new Kegiatan(kunci, tujuan, klip, varian, kanan, kiri, punggung, jalan, false);
	}

	/** The resident's own work of {@link KerjaPeran} (keeps its once clip: the call, the bow, the whisper). */
	private static Kegiatan kerja(Penduduk p) {
		return KerjaPeran.untuk(p.peran(), p.benih()).kegiatan();
	}

	/** True for half the residents (by seed bit {@code bit}). */
	private static boolean separuh(Penduduk p, int bit) {
		return (p.benih() >>> bit & 1L) == 0L;
	}

	private static boolean sipil(Penduduk p) {
		NpcRole.Kelompok g = p.peran().kelompok;
		return g != NpcRole.Kelompok.PENJAGA && g != NpcRole.Kelompok.KHUSUS && !p.peran().petarung();
	}

	// ================================================================== daily routines

	/**
	 * The themed routine of {@code p} at this hour in a place of kind {@code jenis}, or null when the design gives none
	 * (the 38.N.3 group column answers).
	 */
	static @Nullable Kegiatan harian(Penduduk p, Jadwal.Saat s, Jadwal.Saat asli, String jenis) {
		return switch (jenis) {
			case PELABUHAN -> pelabuhan(p, s);
			case OASIS -> oasis(p, s);
			case AIR -> air(p, s);
			case GUNUNG -> gunung(p, s);
			case IBLIS -> iblis(p, asli);
			default -> null;
		};
	}

	// ------------------------------------------------------------------ 38.T.3.3 Kota Pelabuhan

	private static @Nullable Kegiatan pelabuhan(Penduduk p, Jadwal.Saat s) {
		int h = s.jam();
		NpcRole r = p.peran();
		if (r == NpcRole.NELAYAN) {
			if (h == 3) {
				// out to the boats with the nets
				return k("berangkat", "tambatan", GerakId.ANGKAT_BAWA, 0, PropId.JALA, 0, 0, GerakId.JALAN_BAWA);
			}
			if (h == 4) {
				return Kegiatan.dalam("melaut");
			}
			if (h >= 5 && h < 8) {
				return k("pulang_melaut", "tambatan", GerakId.TARIK_JALA, PropId.JALA);
			}
			if (h >= 8 && h < 10) {
				return k("jemur_ikan", "tambatan", GerakId.JEMUR_IKAN, PropId.IKAN_TALI);
			}
			if (h >= 10 && h < 12) {
				return k("perbaiki_jala", "kursi_lantai", GerakId.IKAT_TALI, 0, PropId.JALA, 0, 0, 0);
			}
			if (h == 12 || h == 13) {
				return new Kegiatan("makan_dermaga", "tambatan", GerakId.MAKAN, 0, PropId.SUMPIT, PropId.MANGKUK, 0, 0, false);
			}
			if (h >= 14 && h < 17) {
				return k("tidur_siang", Kegiatan.RUMAH, GerakId.TIDUR_DUDUK, 0);
			}
			if (h >= 18 && h < 22) {
				return separuh(p, 6) ? TEH : k("bercerita", "kursi", GerakId.CERITA, 0);
			}
			return h >= 22 ? TIDUR : null;
		}
		if (r == NpcRole.PENJUAL_IKAN) {
			if (h == 4) {
				return BASUH;
			}
			if (h >= 5 && h < 9) {
				// the Pasar Ikan Pagi: open 05-09 only, the call now and then
				return k("pasar_ikan", Kegiatan.KERJA, GerakId.TAWAR, 0, 0, PropId.IKAN_TALI, 0, 0);
			}
			if (h >= 9 && h < 12) {
				return k("jemur_ikan", "tambatan", GerakId.JEMUR_IKAN, PropId.IKAN_TALI);
			}
			if (h == 12 || h == 13) {
				return new Kegiatan("makan_dermaga", "tambatan", GerakId.MAKAN, 0, PropId.SUMPIT, PropId.MANGKUK, 0, 0, false);
			}
			if (h >= 14 && h < 17) {
				return k("tidur_siang", Kegiatan.RUMAH, GerakId.TIDUR_DUDUK, 0);
			}
			if (h >= 18 && h < 22) {
				return TEH;
			}
			return h >= 22 || h < 4 ? TIDUR : null;
		}
		if (r == NpcRole.PELAUT || r == NpcRole.NAKHODA_LAUT) {
			if (h >= 22 || h < 5) {
				return TIDUR;
			}
			if (h == 5) {
				return k("basuh_muka", "sumur_timba", GerakId.BASUH_MUKA, 0);
			}
			if (h == 6 || h == 7) {
				return MAKAN;
			}
			if (h >= 8 && h < 12 || h >= 14 && h < 18) {
				if (r == NpcRole.NAKHODA_LAUT && h >= 14) {
					// the captain writes the cargo papers at the Kantor Urusan Laut
					return k("surat_muatan", "meja_tulis", GerakId.MENULIS, 0, PropId.KUAS, PropId.PAPAN_CATATAN, 0, 0);
				}
				if (r == NpcRole.PELAUT && (s.menit() / 20 + p.id()) % 3 == 0) {
					return k("ikat_tali", "tambatan", GerakId.IKAT_TALI, PropId.TALI);
				}
				return kerja(p);
			}
			if (h == 12 || h == 13) {
				return k("makan", "meja_makan", GerakId.MAKAN, 0, PropId.SUMPIT, PropId.MANGKUK, 0, 0);
			}
			// the Kedai Jangkar Patah: wine, laughter, the sailors' songs in turn
			return switch ((int) Math.floorMod(p.benih() + s.menit() / 15, 3L)) {
				case 0 -> k("kedai", "meja_makan", GerakId.MINUM_TEH, PropId.CAWAN_ARAK);
				case 1 -> k("nyanyi_pelaut", "meja_makan", GerakId.BICARA_TANGAN, GerakId.V_BICARA_MEGAH, PropId.CAWAN_ARAK, 0, 0, 0);
				default -> k("kedai", "meja_makan", GerakId.TERTAWA, 0);
			};
		}
		if (r == NpcRole.TUKANG_KAPAL) {
			if (h >= 5 && h < 12 || h >= 14 && h < 18) {
				return kerja(p);
			}
			if (h == 12 || h == 13) {
				return MAKAN;
			}
			return null;
		}
		if (r == NpcRole.PEDAGANG_SEBERANG) {
			if (h >= 5 && h < 8) {
				return TEH;
			}
			if (h >= 8 && h < 12 || h >= 14 && h < 18) {
				return kerja(p);
			}
			return h == 12 || h == 13 ? MAKAN : null;
		}
		if (r == NpcRole.PENJAGA_SUAR) {
			if (h >= 3 && h < 5) {
				// pours the lamp oil, the light burns bright
				return k("isi_minyak", "jaga_suar", GerakId.TUANG_TEH, GerakId.V_TUANG_TEH_LOOP, PropId.EMBER, 0, 0, 0);
			}
			if (h >= 5 && h < 8 || h >= 14 && h < 18) {
				return k("teropong", "jaga_suar", GerakId.TEROPONG_LAUT, PropId.TEROPONG);
			}
			if (h >= 8 && h < 14) {
				return TIDUR;
			}
			if (h >= 18 && h < 23 || h < 3) {
				// lights the lamp: the beam burns 12500-23500
				return k("jaga_suar", "jaga_suar", GerakId.TEROPONG_LAUT, 0, PropId.TEROPONG, PropId.LENTERA_TANGAN, 0, 0);
			}
			return null;
		}
		if (r == NpcRole.PENDETA_LAUT) {
			if (h >= 3 && h < 5) {
				return k("doa_subuh", "dupa", GerakId.SUJUD_DUPA, PropId.DUPA);
			}
			if (h >= 5 && h < 8) {
				return k("sapu_kuil", "kerja:sapu", GerakId.SAPU, PropId.SAPU);
			}
			if (h >= 8 && h < 12) {
				return kerja(p);
			}
			if (h >= 14 && h < 18) {
				return s.menit() < 30 ? k("doa_sore", "dupa", GerakId.SUJUD_DUPA, PropId.DUPA) : kerja(p);
			}
			if (h >= 18 && h < 23) {
				return h == 18 ? k("lonceng", "dupa", GerakId.SUJUD_DUPA, PropId.DUPA) : k("doa_senja", "dupa", GerakId.MEMBACA, PropId.KITAB);
			}
			return h >= 23 || h < 3 ? TIDUR : null;
		}
		if (r == NpcRole.ANAK && h >= 14 && h < 17) {
			// the children play on the harbour steps
			return k("main_air", "tambatan", separuh(p, 3) ? GerakId.MAIN_KEJAR : GerakId.MAIN_LOMPAT, 0);
		}
		return null;
	}

	// ------------------------------------------------------------------ 38.T.4.3 Kota Oasis Gurun

	private static @Nullable Kegiatan oasis(Penduduk p, Jadwal.Saat s) {
		int h = s.jam();
		NpcRole r = p.peran();
		boolean jedaPanas = h >= 11 && h < 15;
		if (r == NpcRole.PENUNTUN_UNTA || r == NpcRole.KAFILAH_BASHI) {
			if (h == 5) {
				// water for the camels at the trough
				return k("beri_minum_unta", "kandang_unta", GerakId.ANGKAT_AIR, 0, PropId.EMBER, PropId.EMBER, 0, GerakId.ANGKAT_AIR);
			}
			if (h == 6) {
				return k("ikat_muatan", "kandang_unta", GerakId.IKAT_TALI, PropId.TALI);
			}
			if (h >= 7 && h < 11) {
				return kerja(p);
			}
			if (jedaPanas) {
				return separuh(p, 4) ? k("karavanserai", "kursi", GerakId.MINUM_TEH, 0, PropId.CANGKIR, PropId.LEPEK, 0, 0)
						: k("karavanserai", "kursi", GerakId.CATUR, 0);
			}
			if (h >= 15 && h < 17) {
				return k("rawat_unta", "kandang_unta", GerakId.SAPU, PropId.SAPU);
			}
			if (h >= 17) {
				// tales by the caravan field's fire
				return k("api_unggun", "obrol", GerakId.CERITA, 0);
			}
			return TIDUR;
		}
		if (r == NpcRole.PEDAGANG_BAZAR) {
			if (h == 5) {
				return k("angkat_karung", "lapak", GerakId.ANGKAT_BAWA, 0, PropId.PETI, 0, 0, GerakId.JALAN_BAWA);
			}
			if (h >= 6 && h < 11 || h >= 15 && h < 17) {
				return k("bazar", Kegiatan.KERJA, GerakId.TAWAR_BAZAR, 0);
			}
			if (jedaPanas) {
				return jeda(p);
			}
			if (h >= 17) {
				// the Pasar Malam Bulan Sabit: brass lamps lit, the calls
				return k("pasar_bulan", Kegiatan.KERJA, GerakId.TAWAR_BAZAR, 0, 0, PropId.LENTERA_TANGAN, 0, 0);
			}
			return TIDUR;
		}
		if (r == NpcRole.PENDETA_AIR) {
			if (h == 5) {
				return k("timba_sumur", "sumur_timba", GerakId.TIMBA_SUMUR, 0);
			}
			if (h == 6) {
				return k("doa_kolam", "dupa", GerakId.SUJUD_DUPA, PropId.DUPA);
			}
			if (h >= 7 && h < 11) {
				// reads the water gauge stone and writes the level down
				return k("ukur_air", "sumur_timba", GerakId.MENULIS, 0, PropId.KUAS, PropId.PAPAN_CATATAN, 0, 0);
			}
			if (jedaPanas) {
				return jeda(p);
			}
			if (h >= 15 && h < 17) {
				return k(h == 15 ? "lonceng" : "upacara_sore", "dupa", GerakId.SUJUD_DUPA, 0, PropId.DUPA, PropId.KENDI, 0, 0);
			}
			if (h >= 17 && h < 22) {
				return k("lentera_danau", "pandang", GerakId.MAIN_LENTERA, PropId.LENTERA_TANGAN);
			}
			return TIDUR;
		}
		if (r == NpcRole.PELUKIS_GUA) {
			if (h == 5 || h == 6) {
				return k("naik_tebing", "jalur_tangga", GerakId.SIKAP_TANGAN_BELAKANG, 0, 0, 0, 0, GerakId.JALAN_SANTAI);
			}
			if (h >= 7 && h < 15) {
				// the niche caves stay cool: painting goes on through the heat
				return kerja(p);
			}
			if (h >= 15 && h < 17) {
				return k("membaca", "kursi", GerakId.MEMBACA, PropId.KITAB);
			}
			return null;
		}
		if (r == NpcRole.PENARI_GURUN) {
			if (h >= 7 && h < 11) {
				return k("latihan_tari", "obrol", GerakId.TARI_PUTAR, 0);
			}
			if (jedaPanas) {
				return k("tidur_siang", Kegiatan.RUMAH, GerakId.TIDUR_DUDUK, 0);
			}
			if (h >= 15 && h < 17) {
				return k("dandan", Kegiatan.RUMAH, GerakId.KIPAS, PropId.KIPAS_SUTRA);
			}
			if (h == 17 || h == 18) {
				return MAKAN;
			}
			if (h >= 19 && h < 21) {
				return k("pertunjukan", "panggung", GerakId.TARI_PUTAR, 0);
			}
			if (h >= 21 && h < 23) {
				return k("obrol", "obrol", GerakId.TERTAWA, 0);
			}
			return TIDUR;
		}
		if (jedaPanas && sipil(p) && p.peran().kelompok != NpcRole.Kelompok.ANAK) {
			// the heat break 11-15: everyone civil indoors or under a roof, the bazaar shut
			return jeda(p);
		}
		if (jedaPanas && p.peran().kelompok == NpcRole.Kelompok.ANAK) {
			return k("jeda_panas", Kegiatan.RUMAH, GerakId.DUDUK_LANTAI, 0);
		}
		return null;
	}

	/** The heat break: sitting on the floor, fanning, dozing in the shade (by seed). */
	private static Kegiatan jeda(Penduduk p) {
		return switch ((int) Math.floorMod(p.benih() >>> 9, 3L)) {
			case 0 -> k("jeda_panas", Kegiatan.RUMAH, GerakId.DUDUK_LANTAI, 0);
			case 1 -> k("jeda_panas", Kegiatan.RUMAH, GerakId.KIPAS, PropId.KIPAS_LIPAT);
			default -> k("jeda_panas", Kegiatan.RUMAH, GerakId.TIDUR_DUDUK, 0);
		};
	}

	// ------------------------------------------------------------------ 38.T.5.3 Kota Air

	private static @Nullable Kegiatan air(Penduduk p, Jadwal.Saat s) {
		int h = s.jam();
		NpcRole r = p.peran();
		if (r == NpcRole.PENDAYUNG) {
			if (h == 4) {
				return k("siapkan_perahu", "jalur_perahu", GerakId.SAPU, PropId.SAPU);
			}
			if (h == 5) {
				return k("siapkan_perahu", "jalur_perahu", GerakId.IKAT_TALI, PropId.TALI);
			}
			if (h >= 6 && h < 10) {
				// the Pasar Air Pagi: boats moored as stalls, calls from the boat
				return k("pasar_air", "jalur_perahu", GerakId.TAWAR, 0, 0, PropId.KERANJANG, 0, 0);
			}
			if (h >= 10 && h < 18) {
				return kerja(p);
			}
			if (h == 18) {
				return k("tambat_perahu", "jalur_perahu", GerakId.IKAT_TALI, 0, PropId.TALI, PropId.LENTERA_TANGAN, 0, 0);
			}
			return null;
		}
		if (r == NpcRole.PEMELIHARA_ULAT) {
			if (h >= 4 && h < 6) {
				return k("beri_daun", "kerja:sabit", GerakId.LEMPAR_PAKAN, GerakId.V_LEMPAR_PAKAN_LOOP, 0, PropId.KERANJANG, 0, 0);
			}
			if (h >= 6 && h < 18) {
				return h == 12 ? k("makan_halaman", "obrol", GerakId.MAKAN, 0, PropId.SUMPIT, PropId.MANGKUK, 0, 0) : kerja(p);
			}
			return null;
		}
		if (r == NpcRole.PENENUN_SUTRA) {
			if (h >= 6 && h < 14) {
				return h == 12 ? k("makan_halaman", "obrol", GerakId.MAKAN, 0, PropId.SUMPIT, PropId.MANGKUK, 0, 0) : kerja(p);
			}
			if (h >= 14 && h < 18) {
				// weaving, and the finished cloth hung up for show
				return separuh(p, 5) ? kerja(p) : k("pajang_kain", "lapak", GerakId.BASUH_PAKAIAN, GerakId.V_BASUH_JEMUR, PropId.KAIN, 0, 0, 0);
			}
			return null;
		}
		if (r == NpcRole.PEMETIK_TEH) {
			int mulai = s.musim() == Jadwal.SEMI ? 4 : 6;
			if (h >= mulai && h < 10) {
				return kerja(p);
			}
			if (h >= 10 && h < 14) {
				// roasting the leaves in the hut
				return k("sangrai", "kerja:kuali", GerakId.MASAK, PropId.SENDOK_SAYUR);
			}
			return null;
		}
		if (r == NpcRole.AHLI_TEH) {
			if (h >= 6 && h < 10) {
				return k("sangrai", "kerja:kuali", GerakId.MASAK, PropId.SENDOK_SAYUR);
			}
			if (h >= 10 && h < 14) {
				return k("upacara_teh", "kerja:teko", GerakId.SEDUH_TEH, 0, PropId.TEKO, PropId.CANGKIR, 0, 0);
			}
			if (h >= 14 && h < 18) {
				return k("tamu_teh", "kerja:teko", GerakId.TUANG_TEH, GerakId.V_TUANG_TEH_LOOP, PropId.TEKO, 0, 0, 0);
			}
			return null;
		}
		if (r == NpcRole.SASTRAWAN) {
			if (h >= 4 && h < 6) {
				return TIDUR;
			}
			if (h >= 6 && h < 10) {
				return k("menyimak", "kursi", GerakId.MEMBACA, PropId.KITAB);
			}
			if (h >= 10 && h < 14) {
				return k("menulis_puisi", "meja_tulis", GerakId.MENULIS, PropId.KUAS);
			}
			if (h >= 14 && h < 18) {
				return k("catur", "kursi", GerakId.CATUR, 0);
			}
			if (h >= 18 && h < 22) {
				return separuh(p, 7) ? k("tatap_bulan", "pandang", GerakId.TATAP_LANGIT, GerakId.V_TATAP_RENUNG, 0, 0, 0, 0)
						: k("tatap_bulan", "pandang", GerakId.KIPAS, PropId.KIPAS_LIPAT);
			}
			return null;
		}
		if (r == NpcRole.WARGA) {
			if (h >= 4 && h < 6) {
				return k("cuci_kanal", "tambatan", GerakId.BASUH_PAKAIAN, PropId.KAIN);
			}
			if (h >= 6 && h < 10) {
				return k("belanja_air", "jalur_perahu", GerakId.LIHAT_BARANG, 0, 0, PropId.KERANJANG, 0, 0);
			}
			if (h >= 14 && h < 18) {
				return k("obrol_jembatan", "pandang", GerakId.BICARA_TANGAN, 0);
			}
			if (h >= 18 && h < 22) {
				return k("teh_jendela", Kegiatan.RUMAH, GerakId.MINUM_TEH, 0, PropId.CANGKIR, PropId.LENTERA_TANGAN, 0, 0);
			}
			return null;
		}
		if (r == NpcRole.ANAK && h >= 14 && h < 17 && separuh(p, 2)) {
			// fishing from the bridge
			return k("mancing_jembatan", "pandang", GerakId.MEMANCING, PropId.JORAN);
		}
		return null;
	}

	// ------------------------------------------------------------------ 38.T.6.3 Kota Gunung

	private static @Nullable Kegiatan gunung(Penduduk p, Jadwal.Saat s) {
		int h = s.jam();
		NpcRole r = p.peran();
		if (r == NpcRole.KULI_GUNUNG) {
			if (h == 4) {
				return MAKAN;
			}
			if (h == 5) {
				return k("ikat_muatan", "stasiun_lift", GerakId.IKAT_TALI, 0, PropId.TALI, 0, PropId.RANGKA_PUNGGUNG, 0);
			}
			if (h >= 6 && h < 11 || h >= 13 && h < 17) {
				return kerja(p);
			}
			if (h == 11 || h == 12) {
				return MAKAN;
			}
			if (h >= 17 && h < 20) {
				return separuh(p, 4) ? TEH : k("catur", "kursi", GerakId.CATUR, 0);
			}
			return TIDUR;
		}
		if (r == NpcRole.PEZIARAH) {
			if (h >= 4 && h < 6) {
				return k("dupa_kaki", "dupa", GerakId.SUJUD_DUPA, 0, PropId.DUPA, 0, PropId.TAS_DUPA, 0);
			}
			if (h >= 6 && h < 11) {
				// three steps, one prostration; the others climb and stop for breath
				if (separuh(p, 3)) {
					return k("ziarah", "jalur_tangga", GerakId.SUJUD_ZIARAH, 0, PropId.TONGKAT_BAMBU, 0, PropId.TAS_DUPA, GerakId.JALAN_TUA);
				}
				return k("ziarah", "jalur_tangga", GerakId.SIKAP_TUA, GerakId.V_TUA_TONGKAT, PropId.TONGKAT_BAMBU, 0, PropId.TAS_DUPA, GerakId.JALAN_TUA);
			}
			if (h == 11 || h == 12) {
				return MAKAN;
			}
			if (h >= 13 && h < 15) {
				return k("aula_emas", "dupa", GerakId.SUJUD_DUPA, 0, PropId.DUPA, 0, PropId.TAS_DUPA, 0);
			}
			if (h >= 15 && h < 17) {
				return k("pandang", "pandang", GerakId.TATAP_LANGIT, GerakId.V_TATAP_RENUNG, PropId.TONGKAT_BAMBU, 0, 0, 0);
			}
			if (h >= 17 && h < 20) {
				return separuh(p, 6) ? Kegiatan.dalam("menginap") : k("turun", "jalur_tangga", GerakId.SIKAP_TUA, GerakId.V_TUA_TONGKAT,
						PropId.TONGKAT_BAMBU, 0, PropId.TAS_DUPA, GerakId.JALAN_TUA);
			}
			return TIDUR;
		}
		if (r == NpcRole.BIKSU_GUNUNG || r == NpcRole.KEPALA_BIARA) {
			if (h == 4) {
				// the dawn bell of the Menara Lonceng Awan
				return k("lonceng_subuh", "dupa", GerakId.TABUH_IKAN_KAYU, 0, PropId.PEMUKUL_KECIL, PropId.IKAN_KAYU, 0, 0);
			}
			if (h == 5) {
				return k("tabuh_ikan_kayu", "dupa", GerakId.TABUH_IKAN_KAYU, 0, PropId.PEMUKUL_KECIL, PropId.IKAN_KAYU, 0, 0);
			}
			if (h == 6) {
				return MAKAN;
			}
			if (h >= 7 && h < 11) {
				return r == NpcRole.KEPALA_BIARA ? kerja(p) : separuh(p, 5) ? k("sapu_biara", "kerja:sapu", GerakId.SAPU, PropId.SAPU)
						: k("rak_kitab", "kerja:rak", GerakId.RAK_SUSUN, PropId.KITAB);
			}
			if (h == 11 || h == 12) {
				// the silent meal
				return k("makan_hening", "meja_makan", GerakId.MAKAN, 0, PropId.SUMPIT, PropId.MANGKUK, 0, 0);
			}
			if (h >= 13 && h < 17) {
				return r == NpcRole.KEPALA_BIARA ? k("ceramah", "panggung", GerakId.BICARA_TANGAN, GerakId.V_BICARA_MEGAH, PropId.TONGKAT_CINCIN, 0, 0, 0)
						: k("ceramah", "kursi_lantai", GerakId.DUDUK_LANTAI, 0);
			}
			if (h >= 17 && h < 20) {
				return k(h == 17 ? "doa_senja" : "tabuh_ikan_kayu", "dupa", GerakId.TABUH_IKAN_KAYU, 0, PropId.PEMUKUL_KECIL, PropId.IKAN_KAYU, 0, 0);
			}
			return k("meditasi_malam", "meditasi", GerakId.NAPAS_MEDITASI, 0);
		}
		if (r == NpcRole.PEMETIK_HERBA) {
			if (h == 4) {
				return MAKAN;
			}
			if (h == 5) {
				return k("turun_teras", "jalur_tangga", GerakId.SIKAP_TANGAN_BELAKANG, 0, 0, 0, PropId.KERANJANG, GerakId.JALAN_SANTAI);
			}
			if (h >= 6 && h < 11) {
				return kerja(p);
			}
			if (h == 11 || h == 12) {
				return new Kegiatan("makan_ceruk", Kegiatan.KERJA, GerakId.MAKAN, 0, PropId.SUMPIT, PropId.MANGKUK, 0, 0, false);
			}
			if (h >= 13 && h < 17) {
				return k("jemur_herba", "lapak", GerakId.BASUH_PAKAIAN, GerakId.V_BASUH_JEMUR, PropId.HERBA, 0, 0, 0);
			}
			if (h >= 17 && h < 20) {
				return k("nilai_herba", "lapak", GerakId.LIHAT_BARANG, PropId.HERBA);
			}
			return TIDUR;
		}
		if (r == NpcRole.PENJAGA_LIFT) {
			if (h == 4) {
				return k("periksa_roda", "stasiun_lift", GerakId.PERBAIKI_PALU, PropId.PALU);
			}
			if (h >= 5 && h < 18) {
				if ((h == 11 || h == 12) && separuh(p, 3) == (h == 11)) {
					// lunch in turns: one keeper always at the wheel
					return MAKAN;
				}
				return kerja(p);
			}
			if (h >= 18 && h < 20) {
				return k("roda_terakhir", "stasiun_lift", GerakId.PUTAR_KATROL, 0);
			}
			return TIDUR;
		}
		return null;
	}

	// ------------------------------------------------------------------ 38.T.7.3 Kota Iblis (real hours)

	private static @Nullable Kegiatan iblis(Penduduk p, Jadwal.Saat asli) {
		int h = asli.jam();
		NpcRole r = p.peran();
		if (r == NpcRole.PENEGAK_IBLIS) {
			// two watches by seed; each watch keeps the table's hours
			boolean siangJaga = separuh(p, 2);
			boolean bertugas = siangJaga ? h >= 6 && h < 18 : h >= 18 || h < 6;
			if (!bertugas) {
				return TIDUR;
			}
			if (h >= 6 && h < 12) {
				return k("patroli_siang", "jaga", GerakId.BERDIRI_JAGA, 0, 0, 0, 0, GerakId.BARIS_JAGA);
			}
			if (h >= 12 && h < 18) {
				return kerja(p);
			}
			if (h >= 18) {
				return separuh(p, 5) ? k("patroli_zona", "jaga", GerakId.BERDIRI_JAGA, 0, 0, 0, 0, GerakId.BARIS_JAGA)
						: k("tonton_arena", "penonton", GerakId.SIKAP_LIPAT_TANGAN, 0);
			}
			return k("patroli_lentera", "jaga", GerakId.BERDIRI_JAGA, 0, PropId.LENTERA_TANGAN, 0, 0, GerakId.BARIS_JAGA);
		}
		if (r == NpcRole.GLADIATOR_IBLIS || r == NpcRole.JAGAL_ARENA) {
			if (h >= 6 && h < 12) {
				return TIDUR;
			}
			if (h >= 12 && h < 18) {
				if (r == NpcRole.JAGAL_ARENA) {
					return kerja(p);
				}
				int[] bentuk = {GerakId.LATIHAN_PEDANG_1, GerakId.LATIHAN_PEDANG_2, GerakId.LATIHAN_PEDANG_3, GerakId.ASAH_PISAU};
				int pick = bentuk[(int) Math.floorMod(p.benih() >>> 6, 4L)];
				return pick == GerakId.ASAH_PISAU ? k("asah_pisau", "kursi", GerakId.ASAH_PISAU, PropId.PISAU_GANDA) : k("latihan", "latihan", pick, 0);
			}
			if (h == 18) {
				return MAKAN;
			}
			if (h >= 19 && h < 23) {
				// the night's bouts: the fighters wait at the cage gate
				return r == NpcRole.JAGAL_ARENA ? k("pertandingan", "arena_pintu", GerakId.SIKAP_BERKACAK, 0)
						: k("pertandingan", "arena_pintu", GerakId.LATIH_JURUS, 0);
			}
			return k("pesta_kedai", "meja_makan", separuh(p, 4) ? GerakId.MAKAN : GerakId.MINUM_TEH, 0, PropId.CAWAN_ARAK, 0, 0, 0);
		}
		if (r == NpcRole.MAKELAR_GELAP) {
			if (h >= 6 && h < 15) {
				return TIDUR;
			}
			if (h >= 15 && h < 18) {
				return kerja(p);
			}
			if (h >= 18) {
				// the Pasar Seribu Bayangan in full cry: calls made as whispers
				return k("pasar_gelap", Kegiatan.KERJA, GerakId.TAWAR, 0, PropId.KANTONG_UANG, 0, 0, 0);
			}
			return k("hitung_malam", Kegiatan.KERJA, GerakId.HITUNG_KOIN_GELAP, PropId.KANTONG_UANG);
		}
		if (r == NpcRole.WARGA_IBLIS) {
			if (h >= 6 && h < 12 || h >= 4 && h < 6) {
				return TIDUR;
			}
			if (h >= 12 && h < 18) {
				return h == 12 ? BASUH : k("semedi", Kegiatan.RUMAH, GerakId.SEMEDI_DARAH, 0);
			}
			if (h >= 18) {
				return switch ((int) Math.floorMod(p.benih() >>> 8, 3L)) {
					case 0 -> k("ke_arena", "penonton", GerakId.SORAK_ARENA, 0);
					case 1 -> k("kedai_racun", "meja_makan", GerakId.MINUM_TEH, PropId.CAWAN_RACUN);
					default -> k("berdebat", "obrol", GerakId.BERDEBAT, 0);
				};
			}
			return separuh(p, 6) ? k("kedai_racun", "meja_makan", GerakId.MINUM_TEH, PropId.CAWAN_RACUN) : k("obrol", "obrol", GerakId.BICARA_TANGAN, 0);
		}
		return null;
	}

	/** The captives' day of 38.T.7.3 (a fixed spot; real hours; the feeding at 08:00, flinching at the cheers 18-24). */
	static Kegiatan tawanan(Penduduk p, Jadwal.Saat asli) {
		int h = asli.jam();
		Kegiatan base = KerjaPeran.untuk(p.peran(), p.benih()).kegiatan();
		if (h >= 6 && h < 12) {
			return separuh(p, 3) ? base : base.klipBaru(GerakId.DUDUK_LANTAI);
		}
		if (h >= 18) {
			return base.klipBaru(GerakId.TAKUT_SEMBUNYI);
		}
		if (h < 6) {
			return base.klipBaru(GerakId.TIDUR_DUDUK);
		}
		return base;
	}

	// ================================================================== weather (the "Cuaca" lines)

	/**
	 * The themed weather reaction of {@code p} doing {@code k}, or null when the place has none for this weather (the
	 * generic reaction of {@link Jadwal} then applies).
	 */
	static @Nullable Kegiatan cuaca(Penduduk p, Jadwal.Saat s, Kegiatan k, Jadwal.Cuaca c, String jenis) {
		NpcRole r = p.peran();
		switch (jenis) {
			case PELABUHAN -> {
				if (c.badai()) {
					// the boats stay moored; fishermen watch the sea, the sea priest prays without pause
					if (r == NpcRole.NELAYAN || r == NpcRole.PELAUT || r == NpcRole.NAKHODA_LAUT) {
						return k("tatap_laut", "tambatan", GerakId.TATAP_LANGIT, GerakId.V_TATAP_RENUNG, 0, 0, 0, 0);
					}
					if (r == NpcRole.PENDETA_LAUT) {
						return k("doa_badai", "dupa", GerakId.SUJUD_DUPA, PropId.DUPA);
					}
				}
				if (c.hujan()) {
					if (r == NpcRole.PELAUT || r == NpcRole.NELAYAN) {
						// oilskins: the sailors work on, no umbrella
						return k;
					}
					if (r.kelompok == NpcRole.Kelompok.PEDAGANG && separuh(p, 8) && !k.dalam()) {
						return k("tutup_hujan", Kegiatan.RUMAH, GerakId.DUDUK_KURSI, 0);
					}
				}
			}
			case GUNUNG -> {
				if (c.badai()) {
					if (r == NpcRole.PEZIARAH) {
						return k("berlindung_aula", "dupa", GerakId.DUDUK_LANTAI, 0);
					}
					if (r == NpcRole.PENJAGA_LIFT && !k.dalam()) {
						// the cable car stops in a thunderstorm
						return k("lift_berhenti", "stasiun_lift", GerakId.SIKAP_LIPAT_TANGAN, 0);
					}
				}
				if (c.hujan() && r == NpcRole.PEMETIK_HERBA && "kerja".equals(k.kunci())) {
					// the plank walk is closed in the rain
					return k("berteduh", Kegiatan.RUMAH, GerakId.DUDUK_KURSI, 0);
				}
			}
			case AIR -> {
				if (c.hujan() && r == NpcRole.SASTRAWAN && !k.dalam() && s.jam() >= 6 && s.jam() < 22) {
					// the poets go out to the Paviliun Menyimak Hujan to listen to the rain
					return k("menyimak_hujan", "kursi", GerakId.TATAP_LANGIT, GerakId.V_TATAP_RENUNG, 0, 0, 0, 0);
				}
				if (c.hujan() && r == NpcRole.PENDAYUNG) {
					// the post boat runs on under its woven roof
					return k;
				}
			}
			default -> {
			}
		}
		return null;
	}

	// ================================================================== themed events (tema.<acara>)

	/** The residents' side of themed event {@code acara} ({@code tema.<id>}), or null for no change. */
	static @Nullable Kegiatan acara(Penduduk p, Jadwal.Saat s, String acara) {
		NpcRole r = p.peran();
		NpcRole.Kelompok g = r.kelompok;
		boolean jaga = g == NpcRole.Kelompok.PENJAGA;
		boolean tidur = s.jam() >= 23 || s.jam() < 4;
		return switch (acara) {
			// ---------------------------------------------------------------- Kota Pelabuhan
			case "tema.kapal_datang" -> {
				if (r == NpcRole.PELAUT || r == NpcRole.KULI) {
					yield k("bongkar_kapal", "tambatan", GerakId.ANGKAT_BAWA, 0, 0, 0, PropId.PIKULAN, GerakId.JALAN_PIKUL);
				}
				if (r == NpcRole.PETUGAS_BEA || r == NpcRole.SYAHBANDAR) {
					yield k("periksa_muatan", "tambatan", GerakId.MENULIS, 0, PropId.KUAS, PropId.PAPAN_CATATAN, 0, 0);
				}
				yield !jaga && !tidur && (p.benih() & 3L) == 0L ? k("sambut_kapal", "tambatan", GerakId.LAMBAI, 0) : null;
			}
			case "tema.kapal_pergi" -> !jaga && !tidur && (p.benih() & 3L) == 0L ? k("lepas_kapal", "tambatan", GerakId.LAMBAI, 0) : null;
			case "tema.dewi_laut" -> {
				if (tidur || jaga) {
					yield null;
				}
				if (r == NpcRole.PENDETA_LAUT) {
					yield k("upacara_dewi", "dupa", GerakId.BICARA_TANGAN, GerakId.V_BICARA_MEGAH, PropId.DUPA, 0, 0, 0);
				}
				if (g == NpcRole.Kelompok.ANAK) {
					yield k("festival", "jalan_lentera", GerakId.MAIN_LENTERA, PropId.LENTERA_TANGAN);
				}
				yield (p.benih() & 1L) == 0L ? k("persembahan", "dupa", GerakId.SUJUD_DUPA, PropId.DUPA) : k("festival", "penonton", GerakId.TEPUK_TANGAN, 0);
			}
			case "tema.bajak_laut" -> {
				if (jaga || r.petarung() || r == NpcRole.PELAUT) {
					yield k("tahan_dermaga", "tambatan", GerakId.BERDIRI_JAGA, 0, 0, 0, 0, GerakId.LARI);
				}
				if (r == NpcRole.NAKHODA_LAUT || r == NpcRole.PENJAGA_SUAR) {
					yield k("awasi_laut", "jaga_suar", GerakId.TEROPONG_LAUT, PropId.TEROPONG);
				}
				yield Kegiatan.dalam("berlindung").berjalan(GerakId.LARI_TAKUT);
			}
			// ---------------------------------------------------------------- Kota Oasis
			case "tema.kafilah_agung" -> {
				if (r == NpcRole.KAFILAH_BASHI || r == NpcRole.PENUNTUN_UNTA) {
					yield k("kafilah_agung", "kandang_unta", GerakId.PIMPIN_UNTA, PropId.TALI_KEKANG);
				}
				if (r == NpcRole.PEDAGANG_BAZAR) {
					yield k("bazar", Kegiatan.KERJA, GerakId.TAWAR_BAZAR, 0);
				}
				yield !jaga && !tidur && (p.benih() & 1L) == 0L ? k("sambut_kafilah", "kandang_unta", GerakId.TUNJUK, 0) : null;
			}
			case "tema.badai_pasir" -> {
				if (jaga) {
					// the guards close the Gerbang Giok and hold it
					yield k("tutup_gerbang", "jaga", GerakId.SIKAP_TOMBAK, 0, PropId.TOMBAK, 0, 0, GerakId.LARI);
				}
				if (r == NpcRole.PENUNTUN_UNTA || r == NpcRole.KAFILAH_BASHI) {
					// the camels kneel, the leaders shelter behind them
					yield k("berlindung_unta", "kandang_unta", GerakId.TAKUT_SEMBUNYI, 0);
				}
				yield Kegiatan.dalam("berlindung").berjalan(GerakId.LARI);
			}
			case "tema.tari_putar" -> {
				if (tidur && g != NpcRole.Kelompok.PEDAGANG) {
					yield null;
				}
				if (r == NpcRole.PENARI_GURUN) {
					yield k("tari_putar", "panggung", GerakId.TARI_PUTAR, 0);
				}
				yield jaga ? null : k("tonton_tari", "penonton", (p.benih() & 1L) == 0L ? GerakId.TEPUK_TANGAN : GerakId.SORAK_ARENA, 0);
			}
			// ---------------------------------------------------------------- Kota Air
			case "tema.lentera_air" -> {
				if (r == NpcRole.PENDAYUNG) {
					yield k("dayung_lentera", "jalur_perahu", GerakId.DAYUNG_YULOH, PropId.DAYUNG_YULOH);
				}
				yield jaga ? null : k("lepas_lentera", "lentera_air", GerakId.LEPAS_LENTERA_AIR, PropId.LENTERA_TERATAI);
			}
			case "tema.perahu_naga" -> {
				if (r == NpcRole.PENDAYUNG) {
					yield (p.benih() & 7L) == 0L ? k("genderang_perahu", "jalur_perahu", GerakId.TABUH_GENDERANG_PERAHU, PropId.PEMUKUL)
							: k("lomba_perahu", "jalur_perahu", GerakId.DAYUNG_YULOH, PropId.DAYUNG_YULOH);
				}
				yield jaga || tidur ? null : k("sorak_lomba", "pandang", (p.benih() & 1L) == 0L ? GerakId.SORAK_ARENA : GerakId.TEPUK_TANGAN, 0);
			}
			case "tema.tikus_air" -> g == NpcRole.Kelompok.ANAK ? k("takut_tikus", Kegiatan.RUMAH, GerakId.TAKUT_SEMBUNYI, 0)
					: r == NpcRole.PENENUN_SUTRA || r == NpcRole.NYONYA_SUTRA ? k("marah_tikus", Kegiatan.KERJA, GerakId.MARAH_HENTAK, 0) : null;
			// ---------------------------------------------------------------- Kota Gunung
			case "tema.ziarah" -> {
				if (r == NpcRole.PEZIARAH && s.jam() >= 5 && s.jam() < 18) {
					yield k("ziarah", "jalur_tangga", GerakId.SUJUD_ZIARAH, 0, PropId.TONGKAT_BAMBU, 0, PropId.TAS_DUPA, GerakId.JALAN_TUA);
				}
				if (r == NpcRole.BIKSU_GUNUNG && s.jam() >= 7 && s.jam() < 17 && (p.benih() & 1L) == 0L) {
					yield k("sambut_peziarah", "dupa", GerakId.HORMAT, GerakId.V_HORMAT_WANFU, 0, 0, 0, 0);
				}
				yield null;
			}
			case "tema.cahaya_budha" -> {
				if (jaga) {
					yield null;
				}
				if (g == NpcRole.Kelompok.RAHIB) {
					yield k("sujud_cahaya", "pandang", GerakId.SUJUD_DUPA, PropId.DUPA);
				}
				yield k("cahaya_budha", "pandang", (p.benih() & 1L) == 0L ? GerakId.TATAP_LANGIT : GerakId.TUNJUK, 0);
			}
			case "tema.longsor" -> {
				if (r == NpcRole.PENJAGA_LIFT || jaga) {
					yield k("awasi_longsor", "stasiun_lift", GerakId.BERDIRI_JAGA, 0);
				}
				yield Kegiatan.dalam("berlindung").berjalan(GerakId.LARI_TAKUT);
			}
			case "tema.lift_macet" -> {
				if (r == NpcRole.PENJAGA_LIFT) {
					yield k("perbaiki_lift", "stasiun_lift", GerakId.PERBAIKI_PALU, PropId.PALU);
				}
				yield r == NpcRole.KULI_GUNUNG ? k("tunggu_lift", "stasiun_lift", GerakId.SIKAP_LIPAT_TANGAN, 0, 0, 0, PropId.RANGKA_PUNGGUNG, 0) : null;
			}
			// ---------------------------------------------------------------- Kota Iblis
			case "tema.pesta_darah" -> {
				if (r == NpcRole.GLADIATOR_IBLIS || r == NpcRole.JAGAL_ARENA) {
					yield k("turnamen_darah", "arena_pintu", GerakId.LATIH_JURUS, 0);
				}
				if (jaga) {
					yield k("jaga_pesta", "jaga", GerakId.BERDIRI_JAGA, PropId.LENTERA_TANGAN);
				}
				yield k("pesta_darah", "penonton", GerakId.SORAK_ARENA, 0);
			}
			case "tema.lelang_gelap" -> {
				if (r == NpcRole.MAKELAR_GELAP) {
					yield k("lelang_gelap", "panggung", GerakId.BICARA_TANGAN, GerakId.V_BICARA_MEGAH, PropId.KANTONG_UANG, 0, 0, 0);
				}
				yield r == NpcRole.WARGA_IBLIS && (p.benih() & 1L) == 0L ? k("tawar_lelang", "penonton", GerakId.TEPUK_TANGAN, PropId.KANTONG_UANG) : null;
			}
			case "tema.penyerbuan" -> {
				if (jaga || r.petarung()) {
					yield k("bela_gerbang", "jaga", GerakId.BERDIRI_JAGA, 0, 0, 0, 0, GerakId.LARI);
				}
				yield Kegiatan.dalam("berlindung").berjalan(GerakId.LARI_TAKUT);
			}
			case "tema.jam_berburu" -> jaga || r.petarung() ? null : Kegiatan.dalam("berlindung").berjalan(GerakId.JALAN_TERGESA);
			default -> null;
		};
	}

	/**
	 * True when the stands of a place are filled during {@code acara} (or, in the Kota Iblis, during the nightly bouts
	 * 19-23, 38.T.7.10 "Malam Pertandingan"); {@code jamAsli} is the real hour.
	 */
	static boolean tribun(String jenis, @Nullable String acara, int jamAsli) {
		if (acara != null && TRIBUN.contains(acara)) {
			return true;
		}
		return IBLIS.equals(jenis) && jamAsli >= 19 && jamAsli < 23;
	}
}
