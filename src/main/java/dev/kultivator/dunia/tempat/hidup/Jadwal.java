package dev.kultivator.dunia.tempat.hidup;

import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import org.jetbrains.annotations.Nullable;

/**
 * The daily round of every resident (DESIGN 38.N.3): a pure function of the world clock, the day, the season, the
 * weather, the sky and the place's event. No tick per resident: the caller asks when it needs an answer (a body every
 * 40 ticks, the far crowd, a witness, a command). Hour {@code h = (timeOfDay / 1000 + 6) mod 24} (tick 0 = 06:00), shifted
 * by the resident's own +-40 world minutes ({@link Penduduk#geserJadwal}, scaled by {@code jadwalGeser}) so a street
 * never moves in lockstep, and by 12 hours for the night city (38.T.7.3, {@link NpcRole#malam()}) or a data file's
 * {@code geser_jam}.
 *
 * <p>Order of precedence: the role's own fixed life ({@link NpcRole.Kelompok#KHUSUS}; captives by the hour), the place's
 * event ({@link HidupApi#timpaJadwal}: festival, auction, tournament, beast wave, a crowd watching a feud, the themed
 * towns' {@code tema.<acara>} of {@link JadwalTema#acara}), the sky (blood eclipse, meteor shower, twin moons), the data
 * file ({@link JadwalData}), the themed towns' routines ({@link JadwalTema#harian}, 38.T.3.3-38.T.7.3), then the built-in
 * table below with the weekly and seasonal variation; the weather adjusts the result last (the themed towns' own
 * weather first, {@link JadwalTema#cuaca}; then umbrella, shelter, hiding, Qi-mist meditation).
 */
public final class Jadwal {

	/** Days per season (38.N.3: {@code musim = (d / 24) % 4}). */
	public static final int HARI_MUSIM = 24;
	public static final int SEMI = 0;
	public static final int PANAS = 1;
	public static final int GUGUR = 2;
	public static final int DINGIN = 3;

	/**
	 * The weather and sky at a place (read, never changed; 38.0 rule 3).
	 *
	 * @param hujan  rain falls on the place
	 * @param badai  a thunderstorm
	 * @param salju  snow falls (a cold biome while it rains)
	 * @param dingin a snowy biome (winter: 40 % longer indoors, warming hands)
	 * @param kabut  a Qi mist or purple dawn of module L over the place
	 * @param langit the sky event running, or null
	 */
	public record Cuaca(boolean hujan, boolean badai, boolean salju, boolean dingin, boolean kabut, @Nullable SkyKind langit) {
		public static final Cuaca CERAH = new Cuaca(false, false, false, false, false, null);
	}

	/** Hour and minute of a resident (after its shifts), the day and the season. */
	public record Saat(int jam, int menit, long hari, int musim) {
		/** Day of the week 0-6 (0 = market day, 6 = rest day). */
		public int hariPekan() {
			return (int) Math.floorMod(hari, 7L);
		}
	}

	/** The resident's own clock at overworld time {@code waktu} (absolute ticks) in a place of kind {@code jenis}. */
	public static Saat saat(Penduduk p, String jenis, long waktu) {
		JadwalData.Berkas data = JadwalData.untuk(jenis);
		return jam(p, waktu, (p.peran().malam() ? 12 : 0) + (data != null ? data.geserJam() : 0));
	}

	/**
	 * The resident's clock without the night city's 12 hour shift (its own +-40 minutes only): the real hour the tables
	 * of 38.T.7.3 are written in.
	 */
	public static Saat saatAsli(Penduduk p, long waktu) {
		return jam(p, waktu, 0);
	}

	private static Saat jam(Penduduk p, long waktu, int jamGeser) {
		int geserMenit = HidupConfig.get().jadwalGeser;
		long shift = (long) p.geserJadwal() * geserMenit / 40L;
		long t = waktu + shift - jamGeser * 1000L;
		long hari = Math.floorDiv(waktu, 24000L);
		long tod = Math.floorMod(t, 24000L);
		double jamDes = (tod / 1000.0D + 6.0D) % 24.0D;
		int jam = (int) jamDes;
		int menit = (int) ((jamDes - jam) * 60.0D);
		return new Saat(jam, menit, hari, (int) Math.floorMod(hari / HARI_MUSIM, 4L));
	}

	/** What {@code p} of a place of kind {@code jenis} does at overworld time {@code waktu}. */
	public static Kegiatan kegiatan(Penduduk p, String jenis, long waktu, Cuaca cuaca, @Nullable String acara) {
		Saat s = saat(p, jenis, waktu);
		NpcRole peran = p.peran();
		if (peran == NpcRole.TAWANAN) {
			return JadwalTema.tawanan(p, saatAsli(p, waktu));
		}
		if (peran.kelompok == NpcRole.Kelompok.KHUSUS) {
			return khusus(p);
		}
		if (p.tamu() && s.hariPekan() != 0) {
			return Kegiatan.dalam("pergi");
		}
		Kegiatan k = null;
		JadwalData.Berkas data = JadwalData.untuk(jenis);
		if (acara != null) {
			k = acara(p, s, acara, data);
		}
		if (k == null && cuaca.langit() != null && HidupConfig.get().reaksiCuaca) {
			k = langit(p, s, cuaca.langit());
		}
		if (k == null && data != null) {
			k = dariData(p, s, data);
		}
		if (k == null) {
			k = JadwalTema.harian(p, s, saatAsli(p, waktu), jenis);
		}
		if (k == null) {
			k = harian(p, s, jenis);
		}
		if (!HidupConfig.get().reaksiCuaca) {
			return k;
		}
		Kegiatan tema = k.dalam() ? null : JadwalTema.cuaca(p, s, k, cuaca, jenis);
		return tema != null ? tema : cuaca(p, s, k, cuaca);
	}

	// ================================================================== fixed lives

	/** Ghosts, captives, demonic lords: they keep their spot all day (their owner module drives the rest). */
	private static Kegiatan khusus(Penduduk p) {
		KerjaPeran.Kerja kerja = KerjaPeran.untuk(p.peran(), p.benih());
		return kerja.kegiatan();
	}

	// ================================================================== events

	private static @Nullable Kegiatan acara(Penduduk p, Saat s, String acara, @Nullable JadwalData.Berkas data) {
		NpcRole peran = p.peran();
		NpcRole.Kelompok g = peran.kelompok;
		if (data != null) {
			var table = data.acara().get(acara);
			if (table != null) {
				Kegiatan k = table.get(peran.id());
				if (k == null) {
					k = table.get(g.id());
				}
				if (k == null) {
					k = table.get("*");
				}
				if (k != null) {
					return k;
				}
			}
		}
		boolean tidur = s.jam() >= 23 || s.jam() < 5;
		if (acara.startsWith("tonton:")) {
			// a crowd watching a scene (39F feud, C39.K11; TempatHuni lets only the nearest eight come): a ring of watchers
			// cheering, clapping or standing arms folded
			if (g == NpcRole.Kelompok.PENJAGA || tidur || xz(acara.substring(7)) == null) {
				return null;
			}
			int klip = g == NpcRole.Kelompok.ANAK ? GerakId.SORAK_ARENA : switch ((int) Math.floorMod(p.benih() >>> 12, 3L)) {
				case 0 -> GerakId.SORAK_ARENA;
				case 1 -> GerakId.TEPUK_TANGAN;
				default -> GerakId.SIKAP_LIPAT_TANGAN;
			};
			return new Kegiatan("tonton", Kegiatan.XZ + acara.substring(7), klip, 0, 0, 0, 0, GerakId.JALAN_TERGESA, false);
		}
		if (acara.startsWith("tema.")) {
			// the themed towns' events (38.T, TemaKalender): the residents' side
			return JadwalTema.acara(p, s, acara);
		}
		return switch (acara) {
			case "festival_lentera" -> {
				if (s.jam() < 17 || tidur) {
					yield null;
				}
				if (g == NpcRole.Kelompok.PEDAGANG) {
					yield new Kegiatan("festival", Kegiatan.KERJA, GerakId.SERU_DAGANG, 0, PropId.LENTERA_TANGAN, 0, 0, 0, false)
							.klipBaru(GerakId.BICARA_TANGAN);
				}
				if (g == NpcRole.Kelompok.PENJAGA) {
					yield Kegiatan.di("festival", "jalan_lentera", GerakId.BERDIRI_JAGA, PropId.LENTERA_TANGAN);
				}
				yield Kegiatan.di("festival", "jalan_lentera", GerakId.MAIN_LENTERA, PropId.LENTERA_TANGAN);
			}
			case "festival_panen" -> {
				if (s.jam() < 17 || s.jam() > 22) {
					yield null;
				}
				yield g == NpcRole.Kelompok.ANAK ? Kegiatan.di("festival", "obrol", GerakId.MAIN_KEJAR, 0)
						: Kegiatan.di("festival", "obrol", GerakId.MENARI_PANEN, 0);
			}
			case "lelang_agung", "lelang" -> g == NpcRole.Kelompok.PEDAGANG && (p.benih() & 3L) == 0L && s.jam() >= 9 && s.jam() < 18
					? Kegiatan.di("lelang", "penonton", GerakId.TEPUK_TANGAN, PropId.KANTONG_UANG) : null;
			case "turnamen", "turnamen_agung", "pertemuan_sekte" -> {
				if (tidur || g == NpcRole.Kelompok.PENJAGA || s.jam() < 8 || s.jam() > 18) {
					yield null;
				}
				if (g == NpcRole.Kelompok.PEDAGANG) {
					yield null;
				}
				yield Kegiatan.di("tonton", "penonton", GerakId.SORAK_ARENA, 0);
			}
			case "gelombang_binatang", "pertahanan" -> {
				if (g == NpcRole.Kelompok.PENJAGA || peran.petarung()) {
					yield Kegiatan.di("bertahan", "jaga", GerakId.BERDIRI_JAGA, 0);
				}
				yield Kegiatan.dalam("berlindung");
			}
			case "pertemuan_pil", "pil" -> g == NpcRole.Kelompok.CENDEKIA && s.jam() >= 9 && s.jam() < 18
					? Kegiatan.di("pertemuan", "lapak", GerakId.TUMBUK_HERBA, PropId.ALU) : null;
			default -> null;
		};
	}

	// ================================================================== the sky (38.N.3 "peristiwa langit")

	private static @Nullable Kegiatan langit(Penduduk p, Saat s, SkyKind sky) {
		NpcRole.Kelompok g = p.peran().kelompok;
		boolean tidur = s.jam() >= 23 || s.jam() < 4;
		return switch (sky) {
			case BLOOD_ECLIPSE -> {
				if (g == NpcRole.Kelompok.PENJAGA) {
					yield Kegiatan.di("jaga_ganda", "jaga", GerakId.BERDIRI_JAGA, PropId.LENTERA_TANGAN);
				}
				if (g == NpcRole.Kelompok.RAHIB) {
					yield Kegiatan.di("doa", "dupa", GerakId.SUJUD_DUPA, PropId.DUPA);
				}
				if (g == NpcRole.Kelompok.KULTIVATOR && p.peran().petarung()) {
					yield Kegiatan.di("waspada", "latihan", GerakId.BERDIRI_JAGA, 0);
				}
				yield Kegiatan.dalam("berlindung");
			}
			case METEOR_SHOWER -> tidur && g == NpcRole.Kelompok.ANAK ? null
					: Kegiatan.di("lihat_langit", "obrol", (p.id() & 1) == 0 ? GerakId.TUNJUK : GerakId.TATAP_LANGIT, 0);
			case TWIN_MOONS -> tidur ? null : Kegiatan.di("lihat_langit", Kegiatan.TETAP, (p.id() % 3) == 0 ? GerakId.BISIK : GerakId.TATAP_LANGIT, 0);
		};
	}

	// ================================================================== data files

	private static @Nullable Kegiatan dariData(Penduduk p, Saat s, JadwalData.Berkas data) {
		Kegiatan[] byRole = data.peran().get(p.peran().id());
		if (byRole != null && byRole[s.jam()] != null) {
			return byRole[s.jam()];
		}
		Kegiatan[] byGroup = data.kelompok().get(p.peran().kelompok.id());
		if (byGroup != null && byGroup[s.jam()] != null) {
			return byGroup[s.jam()];
		}
		return null;
	}

	// ================================================================== the built-in table (38.N.3)

	private static final Kegiatan MAKAN = new Kegiatan("makan", "meja_makan", GerakId.MAKAN, 0, PropId.SUMPIT, PropId.MANGKUK, 0, 0, false);
	private static final Kegiatan BASUH = Kegiatan.di("basuh_muka", Kegiatan.RUMAH, GerakId.BASUH_MUKA, 0);
	private static final Kegiatan SAPU_HALAMAN = Kegiatan.di("sapu", Kegiatan.RUMAH, GerakId.SAPU, PropId.SAPU);
	private static final Kegiatan TEH = new Kegiatan("minum_teh", "kursi", GerakId.MINUM_TEH, 0, PropId.CANGKIR, PropId.LEPEK, 0, 0, false);
	private static final Kegiatan OBROL = Kegiatan.di("obrol", "obrol", GerakId.BICARA_TANGAN, 0);
	private static final Kegiatan SERAMBI = Kegiatan.di("serambi", Kegiatan.RUMAH, GerakId.DUDUK_KURSI, 0);
	private static final Kegiatan MEDITASI = Kegiatan.di("meditasi", "meditasi", GerakId.NAPAS_MEDITASI, 0);
	/** Out of the door after the night: a loose stance, the big stretch and yawn play on arrival (TubuhPenduduk). */
	private static final Kegiatan BANGUN = Kegiatan.di("bangun", Kegiatan.RUMAH, GerakId.SIKAP_SANTAI_L, 0);
	/** An evening at the tavern table: a cup of wine in hand, the jug beside it (the sip plays now and then). */
	private static final Kegiatan KEDAI = new Kegiatan("kedai", "meja_makan", GerakId.MINUM_TEH, 0, PropId.CAWAN_ARAK, PropId.LABU_ARAK, 0, 0, false);
	/** Children sitting on the ground before a storyteller (38.N.4 butir 5). */
	private static final Kegiatan DENGAR_CERITA = Kegiatan.di("dengar_cerita", "kursi_lantai", GerakId.DUDUK_LANTAI, 0);

	/** The first ten minutes of the waking hour {@code jam}: out of the door, stretching. */
	private static boolean bangun(Saat s, int jam) {
		return s.jam() == jam && s.menit() < 10;
	}

	/** An evening tune: a bamboo flute or a guqin on the knees, by seed. */
	private static Kegiatan musik(Penduduk p, String tujuan) {
		return (p.benih() >>> 19 & 1L) == 0L ? Kegiatan.di("musik", tujuan, GerakId.MAIN_SERULING, PropId.SERULING)
				: Kegiatan.di("musik", tujuan, GerakId.MAIN_GUQIN, PropId.GUQIN);
	}

	private static Kegiatan harian(Penduduk p, Saat s, String jenis) {
		return switch (p.peran().kelompok) {
			case PEKERJA -> pekerja(p, s, jenis);
			case PEDAGANG -> pedagang(p, s, jenis);
			case PENJAGA -> penjaga(p, s);
			case ANAK -> anak(p, s);
			case TETUA -> tetua(p, s);
			case KULTIVATOR -> kultivator(p, s);
			case CENDEKIA, RAHIB -> cendekia(p, s);
			case PEJABAT -> pejabat(p, s);
			case KHUSUS -> khusus(p);
		};
	}

	/** The resident's work for the hour, with the week and the season (38.N.3 "variasi"). */
	private static Kegiatan kerja(Penduduk p, Saat s, String jenis) {
		NpcRole peran = p.peran();
		KerjaPeran.Kerja kerja = KerjaPeran.untuk(peran, p.benih());
		Kegiatan k = kerja.kegiatan();
		if (peran == NpcRole.PETANI) {
			if (s.hariPekan() == 0 && (jenis.equals("kampung/desa_besar") || jenis.startsWith("pos/dagang"))) {
				// market day: the farmer sells his harvest at the village market
				return new Kegiatan("pasar", "lapak", GerakId.TAWAR, 0, 0, PropId.KERANJANG, 0, 0, false);
			}
			if (s.musim() == SEMI) {
				return k.klipBaru(GerakId.TANAM_BIBIT).denganProp(PropId.BIBIT, 0, 0);
			}
			if (s.musim() == GUGUR) {
				return k.klipBaru(GerakId.MEMANEN).denganProp(0, PropId.KERANJANG, 0);
			}
		}
		if (peran == NpcRole.NELAYAN && s.musim() == PANAS && s.jam() >= 15) {
			// summer evenings: the long catch with the net
			return k.ke("kerja:jala").klipBaru(GerakId.TARIK_JALA).denganProp(PropId.JALA, 0, 0);
		}
		if (s.hariPekan() == 6 && peran.kelompok == NpcRole.Kelompok.PEKERJA && (p.benih() & 1L) == 0L) {
			// rest day: half the workers chat, play chess or fish
			return switch ((int) Math.floorMod(p.benih() >>> 3, 3L)) {
				case 0 -> OBROL;
				case 1 -> Kegiatan.di("catur", "kursi", GerakId.CATUR, 0);
				default -> Kegiatan.di("memancing", "kerja:pancing", GerakId.MEMANCING, PropId.JORAN);
			};
		}
		if (kerja.pindah() != null) {
			// carriers: half an hour at one end, half at the other
			boolean balik = (s.menit() / 30 + s.jam()) % 2 == 1;
			if (balik) {
				return new Kegiatan("angkut", kerja.pindah(), GerakId.ANGKAT_BAWA, 0, kerja.kanan(), kerja.kiri(), kerja.punggung(), kerja.jalan(), false);
			}
		}
		// the rhythm of the work: twelve minutes of every hour on the interlude at the same spot (KerjaPeran.selingan)
		Kegiatan selingan = KerjaPeran.selinganSekarang(kerja, p.benih(), s.menit());
		return selingan != null ? selingan : k;
	}

	private static boolean malamPanjang(Penduduk p, Saat s) {
		return s.musim() == DINGIN && Math.floorMod(p.benih() >>> 5, 5L) < 2L;
	}

	private static Kegiatan pekerja(Penduduk p, Saat s, String jenis) {
		int h = s.jam();
		boolean winter = malamPanjang(p, s);
		if (h >= 22 || h < (winter ? 6 : 5)) {
			return Kegiatan.dalam("tidur");
		}
		if (bangun(s, winter ? 6 : 5)) {
			return BANGUN;
		}
		if (h == 5) {
			return s.menit() < 30 ? BASUH : SAPU_HALAMAN;
		}
		if (h == 6) {
			return MAKAN;
		}
		if (h == 12) {
			return p.peran() == NpcRole.KOKI ? kerja(p, s, jenis) : MAKAN;
		}
		if (h == 7 && s.menit() < 30 && p.peran() == NpcRole.KOKI) {
			// the cook's morning round: to the market stalls with an empty basket (38.N.4 butir 2)
			return new Kegiatan("belanja", "lapak", GerakId.TAWAR, 0, 0, PropId.KERANJANG, 0, GerakId.JALAN_SANTAI, false);
		}
		if (h == 7 && s.menit() < 45 && p.peran() == NpcRole.KOKI) {
			// ... and back to the kitchen carrying the vegetables
			return new Kegiatan("belanja", Kegiatan.KERJA, GerakId.ANGKAT_BAWA, 0, PropId.KERANJANG, 0, 0, GerakId.JALAN_BAWA, false);
		}
		if (h >= 7 && h < 17) {
			if (s.menit() >= 45 && s.menit() < 50 && p.peran() != NpcRole.KOKI) {
				return new Kegiatan("minum", Kegiatan.TETAP, GerakId.MINUM_TEH, 0, PropId.CANGKIR, 0, 0, 0, false);
			}
			return kerja(p, s, jenis);
		}
		if (h == 16 && s.hariPekan() == 3 && Math.floorMod(p.benih() >>> 6, 3L) == 0L && !"kerja:kuali".equals(KerjaPeran.untuk(p.peran(), p.benih()).titik())) {
			// washing day: the clothes scrubbed at home, then hung out to dry
			return s.menit() < 30 ? Kegiatan.di("cuci", Kegiatan.RUMAH, GerakId.BASUH_PAKAIAN, PropId.KAIN)
					: new Kegiatan("cuci", Kegiatan.RUMAH, GerakId.BASUH_PAKAIAN, GerakId.V_BASUH_JEMUR, PropId.KAIN, 0, 0, 0, false);
		}
		if (h == 17 && p.peran() == NpcRole.PETANI && s.musim() == GUGUR && s.menit() < 30) {
			// autumn: the harvest basket carried home from the field
			return new Kegiatan("pulang", Kegiatan.RUMAH, GerakId.ANGKAT_BAWA, 0, PropId.KERANJANG, 0, 0, GerakId.JALAN_BAWA, false);
		}
		if (h == 17) {
			return BASUH.berjalan(GerakId.JALAN_SANTAI);
		}
		if (h == 18) {
			return MAKAN;
		}
		if (h == 19) {
			return (p.benih() & 2L) == 0L ? TEH : MAKAN;
		}
		if (winter) {
			return Kegiatan.di("hangat", Kegiatan.RUMAH, GerakId.HANGAT_TANGAN, 0);
		}
		// the tavern: a quarter of the workers most nights, half of them on the rest day's eve
		if (Math.floorMod(p.benih() >>> 14, s.hariPekan() == 5 || s.hariPekan() == 6 ? 2L : 4L) == 0L) {
			return KEDAI;
		}
		return (p.benih() & 4L) == 0L ? OBROL : SERAMBI;
	}

	private static Kegiatan pedagang(Penduduk p, Saat s, String jenis) {
		int h = s.jam();
		boolean pasarMalam = jenis.equals("kota/kota_besar") && Math.floorMod(p.benih() >>> 7, 3L) == 0L;
		if (h >= (pasarMalam ? 24 : 22) || h < 5) {
			return Kegiatan.dalam("tidur");
		}
		if (bangun(s, 5)) {
			return BANGUN;
		}
		if (h == 5) {
			return BASUH;
		}
		if (h == 6) {
			return s.menit() < 30 ? MAKAN : new Kegiatan("buka_kios", "lapak", GerakId.ANGKAT_BAWA, 0, PropId.PETI, 0, 0, GerakId.JALAN_BAWA, false);
		}
		if (h == 12) {
			return new Kegiatan("makan", Kegiatan.KERJA, GerakId.MAKAN, 0, PropId.SUMPIT, PropId.MANGKUK, 0, 0, false);
		}
		if (h >= 7 && h < 17) {
			return kerja(p, s, jenis);
		}
		if (h == 17) {
			return new Kegiatan("tutup_kios", Kegiatan.RUMAH, GerakId.ANGKAT_BAWA, 0, PropId.PETI, 0, 0, GerakId.JALAN_BAWA, false);
		}
		if (h == 18) {
			return MAKAN;
		}
		if (pasarMalam) {
			return new Kegiatan("pasar_malam", Kegiatan.KERJA, GerakId.HITUNG_SEMPOA, 0, PropId.LENTERA_TANGAN, 0, 0, 0, false);
		}
		if (h >= 20 && Math.floorMod(p.benih() >>> 14, 3L) == 0L) {
			// the day's takings counted over a cup at the tavern
			return KEDAI;
		}
		return h == 19 ? TEH : OBROL;
	}

	/** Two watches (by seed): the day watch 06-18, the night watch 18-06 with the hand lantern and the 22:00 drum. */
	private static Kegiatan penjaga(Penduduk p, Saat s) {
		int h = s.jam();
		boolean malam = (p.benih() & 1L) == 1L;
		KerjaPeran.Kerja kerja = KerjaPeran.untuk(p.peran(), p.benih());
		Kegiatan jaga = kerja.kegiatan();
		Kegiatan patroli = new Kegiatan("patroli", "jaga", GerakId.BERDIRI_JAGA, 0, jaga.propKanan(), 0, 0, GerakId.BARIS_JAGA, false);
		Kegiatan sikap = KerjaPeran.selinganSekarang(kerja, p.benih(), s.menit());
		if (sikap != null) {
			// at the post the stance changes now and then: spear grounded, hands behind the back, arms akimbo
			jaga = sikap;
		}
		if (!malam) {
			if (h >= 22 || h < 4) {
				return Kegiatan.dalam("tidur");
			}
			if (h == 4) {
				// up before the town: a stretch, a wash, the blade honed on the barrack bench
				return s.menit() < 10 ? BANGUN : s.menit() < 30 ? BASUH : Kegiatan.di("asah", "kursi", GerakId.ASAH_PEDANG, 0);
			}
			if (h == 5) {
				return new Kegiatan("ganti_regu", Kegiatan.KERJA, GerakId.HORMAT, 0, jaga.propKanan(), 0, 0, GerakId.BARIS_JAGA, false);
			}
			if (h == 6 || h == 12) {
				return MAKAN;
			}
			if (h >= 18) {
				return h < 20 ? MAKAN : OBROL;
			}
			// a round every other half hour, the post between
			return (s.menit() / 30) % 2 == 0 ? jaga : patroli;
		}
		if (h >= 7 && h < 15) {
			return Kegiatan.dalam("tidur");
		}
		if (bangun(s, 15)) {
			return BANGUN;
		}
		if (h == 15 || h == 16) {
			return h == 15 ? BASUH : MAKAN;
		}
		if (h == 17) {
			return new Kegiatan("ganti_regu", Kegiatan.KERJA, GerakId.HORMAT, 0, jaga.propKanan(), 0, 0, GerakId.BARIS_JAGA, false);
		}
		if (h == 22 && s.menit() < 10 && Math.floorMod(p.benih() >>> 9, 4L) == 0L) {
			return new Kegiatan("genderang", "jaga", GerakId.GENDERANG, 0, PropId.PEMUKUL, PropId.PEMUKUL, 0, 0, false);
		}
		if (h == 3 && Math.floorMod(p.benih() >>> 9, 5L) == 1L) {
			// the small hours: one guard in five nods off leaning on the post (yawns now and then; a bad look if seen)
			return new Kegiatan("terkantuk", "jaga", GerakId.BERSANDAR, 0, jaga.propKanan(), 0, 0, GerakId.BARIS_JAGA, false);
		}
		Kegiatan lentera = patroli.denganProp(PropId.LENTERA_TANGAN, 0, 0);
		return (s.menit() / 30) % 2 == 0 ? jaga : (h >= 20 || h < 5 ? lentera : patroli);
	}

	private static Kegiatan anak(Penduduk p, Saat s) {
		int h = s.jam();
		if (h >= 21 || h < 6 || h == 20 && s.menit() >= 30) {
			return Kegiatan.dalam("tidur");
		}
		if (bangun(s, 6)) {
			return BANGUN.klipBaru(GerakId.SIKAP_ANAK);
		}
		if (h == 6 || h == 12 || h == 18) {
			return h == 12 ? MAKAN.ke(Kegiatan.RUMAH) : MAKAN;
		}
		if (h >= 7 && h < 10 && s.hariPekan() != 6) {
			return new Kegiatan("sekolah", "kursi", GerakId.MEMBACA, 0, PropId.KITAB, 0, 0, GerakId.JALAN_ANAK, false);
		}
		if (h >= 14 && h < 16 && s.musim() == PANAS && (p.benih() >>> 8 & 1L) == 0L) {
			// summer afternoons: splashing and jumping at the water's edge by the fishing spots (38.N.3 "panas")
			return new Kegiatan("main_air", "kerja:pancing", GerakId.MAIN_LOMPAT, 0, 0, 0, 0, GerakId.LARI, false);
		}
		if (h >= 13 && h < 17 && Math.floorMod(p.benih() >>> 4, 4L) == 0L) {
			// helping the parents carry a little basket
			return new Kegiatan("bantu", "lapak", GerakId.ANGKAT_BAWA, 0, 0, PropId.KERANJANG, 0, GerakId.JALAN_BAWA, false);
		}
		if (h == 17) {
			return new Kegiatan("pulang", Kegiatan.RUMAH, GerakId.LAMBAI, 0, 0, 0, 0, GerakId.LARI, false);
		}
		if (h == 19 && (p.benih() & 1L) == 0L) {
			// a story before bed, sitting on the ground in front of a grandparent
			return DENGAR_CERITA;
		}
		if (h >= 19) {
			return Kegiatan.di("main_rumah", Kegiatan.RUMAH, GerakId.DUDUK_LANTAI, 0);
		}
		// play: the game changes with the day so the square is never the same
		KerjaPeran.Kerja[] games = KerjaPeran.semua(NpcRole.ANAK);
		KerjaPeran.Kerja game = games[(int) Math.floorMod(p.benih() + s.hari() + h / 2, (long) games.length)];
		return new Kegiatan("main", "main", game.klip(), 0, game.kanan(), 0, 0, GerakId.JALAN_ANAK, false);
	}

	private static Kegiatan tetua(Penduduk p, Saat s) {
		int h = s.jam();
		NpcRole peran = p.peran();
		if (peran == NpcRole.PEZIARAH && h >= 6 && h < 18) {
			// pilgrims climb by day: the stair path, then the shrine
			return h < 11 ? new Kegiatan("ziarah", "jalur_tangga", GerakId.SUJUD_ZIARAH, 0, PropId.TONGKAT_BAMBU, 0, PropId.TAS_DUPA, GerakId.JALAN_TUA, false)
					: KerjaPeran.untuk(peran, p.benih()).kegiatan();
		}
		if (h >= 21 || h < 4) {
			return Kegiatan.dalam("tidur");
		}
		if (bangun(s, 4)) {
			return BANGUN.klipBaru(GerakId.SIKAP_TUA);
		}
		if (h == 4) {
			return SERAMBI;
		}
		if (h == 5) {
			return Kegiatan.di("tai_chi", "obrol", GerakId.TAI_CHI_PAGI, 0);
		}
		if (h == 6 || h == 18) {
			return MAKAN;
		}
		if (h == 12) {
			return Kegiatan.di("tidur_siang", "kursi", GerakId.TIDUR_DUDUK, 0);
		}
		if (h >= 7 && h < 12) {
			return kerja(p, s, "");
		}
		if (h >= 13 && h < 17) {
			return (p.benih() & 8L) == 0L ? OBROL : Kegiatan.di("beri_pakan", "pandang", GerakId.LEMPAR_PAKAN, 0);
		}
		if (h == 17) {
			return SERAMBI;
		}
		if (Math.floorMod(p.benih() >>> 15, 3L) == 0L) {
			// an old tune in the dusk
			return musik(p, "kursi");
		}
		return (p.benih() & 16L) == 0L ? TEH : Kegiatan.di("bercerita", "kursi", GerakId.CERITA, 0);
	}

	private static Kegiatan kultivator(Penduduk p, Saat s) {
		int h = s.jam();
		NpcRole peran = p.peran();
		boolean setengah = (p.benih() & 1L) == 0L;
		if (h >= 22 || h < 4) {
			return setengah ? MEDITASI : Kegiatan.dalam("tidur");
		}
		if (h == 4) {
			// the dawn breath: half sit still, half guide the Qi round the meridians with their hands
			return (p.benih() >>> 2 & 1L) == 0L ? MEDITASI : MEDITASI.klipBaru(GerakId.SIRKULASI_QI);
		}
		if (h == 5) {
			// the morning drill: sword forms, the horse stance or palm strikes, by seed and day
			return switch ((int) Math.floorMod(p.benih() + s.hari(), 3L)) {
				case 0 -> Kegiatan.di("latihan_pagi", "latihan", GerakId.LATIHAN_PEDANG_1, 0);
				case 1 -> Kegiatan.di("latihan_pagi", "latihan", GerakId.TIANG_BERDIRI, 0);
				default -> Kegiatan.di("latihan_pagi", "latihan", GerakId.TELAPAK_LATIHAN, 0);
			};
		}
		if (h == 6 || h == 18) {
			return MAKAN;
		}
		if (h == 12) {
			return s.menit() < 30 ? MAKAN : MEDITASI;
		}
		if (h >= 13 && h < 17 && (peran == NpcRole.MURID_SEKTE_LUAR || peran == NpcRole.MURID_KLAN)) {
			// outer disciples' chores
			return (p.benih() & 2L) == 0L ? Kegiatan.di("tugas", "kerja:sapu", GerakId.SAPU, PropId.SAPU)
					: new Kegiatan("tugas", "sumur_timba", GerakId.ANGKAT_AIR, 0, PropId.EMBER, PropId.EMBER, 0, GerakId.ANGKAT_AIR, false);
		}
		if (h >= 13 && h < 17 && peran == NpcRole.MURID_SEKTE_DALAM) {
			return MEDITASI;
		}
		if (h == 17) {
			return Kegiatan.di("latihan_senja", "latihan", GerakId.LATIHAN_TOMBAK, PropId.TOMBAK);
		}
		if (h == 19) {
			return OBROL;
		}
		if (h >= 20) {
			return MEDITASI;
		}
		return kerja(p, s, "");
	}

	private static Kegiatan cendekia(Penduduk p, Saat s) {
		int h = s.jam();
		boolean rahib = p.peran().kelompok == NpcRole.Kelompok.RAHIB;
		if (h >= 22 || h < 4) {
			return Kegiatan.dalam("tidur");
		}
		if (h == 4) {
			return rahib ? Kegiatan.di("lonceng_subuh", "dupa", GerakId.SUJUD_DUPA, PropId.DUPA) : Kegiatan.dalam("tidur");
		}
		if (bangun(s, 5) && !rahib) {
			return BANGUN;
		}
		if (h == 5) {
			return rahib ? Kegiatan.di("sapu_kuil", "kerja:sapu", GerakId.SAPU, PropId.SAPU) : BASUH;
		}
		if (h == 6 || h == 12 || h == 18) {
			return MAKAN;
		}
		if (h == 17) {
			return rahib ? Kegiatan.di("doa_senja", "dupa", GerakId.SUJUD_DUPA, PropId.DUPA) : SERAMBI;
		}
		if (h >= 19) {
			if (!rahib && h < 21 && Math.floorMod(p.benih() >>> 15, 3L) == 0L) {
				// scholars play for one another in the evening
				return musik(p, "kursi");
			}
			return rahib ? MEDITASI : Kegiatan.di("menulis", "meja_tulis", GerakId.MENULIS, PropId.KUAS);
		}
		if (h == 15 && p.peran() != NpcRole.PENCERAMAH_FANA && !rahib && (p.benih() & 4L) == 0L) {
			// the afternoon lecture: listeners sit with a book
			return Kegiatan.di("kuliah", "kursi", GerakId.MEMBACA, PropId.KITAB);
		}
		return kerja(p, s, "");
	}

	private static Kegiatan pejabat(Penduduk p, Saat s) {
		int h = s.jam();
		if (h >= 22 || h < 6) {
			return Kegiatan.dalam("tidur");
		}
		if (bangun(s, 6)) {
			// officials straighten their sleeves when they come out
			return new Kegiatan("bangun", Kegiatan.RUMAH, GerakId.SIKAP_TANGAN_BELAKANG, 0, 0, 0, 0, 0, false);
		}
		if (h == 6 || h == 12 || h == 18) {
			return MAKAN;
		}
		if (h >= 13 && h < 17 && p.peran() == NpcRole.HAKIM) {
			// the Yamen court in session: the gavel falls now and then (sela clip of the work)
			return new Kegiatan("sidang", Kegiatan.KERJA, GerakId.MENULIS, 0, PropId.PALU_KAYU, 0, 0, 0, false);
		}
		if (h == 17) {
			return SERAMBI;
		}
		if (h == 19) {
			return Kegiatan.di("jamuan", "meja_makan", GerakId.MINUM_TEH, PropId.CANGKIR);
		}
		if (h >= 20) {
			return Kegiatan.di("kerja_lilin", "meja_tulis", GerakId.MENULIS, PropId.KUAS);
		}
		return kerja(p, s, "");
	}

	// ================================================================== the weather (38.N.3 "Cuaca")

	private static Kegiatan cuaca(Penduduk p, Saat s, Kegiatan k, Cuaca c) {
		if (k.dalam()) {
			return k;
		}
		NpcRole peran = p.peran();
		NpcRole.Kelompok g = peran.kelompok;
		boolean luar = luar(k.tujuan());
		if (c.badai()) {
			if (g == NpcRole.Kelompok.ANAK) {
				return Kegiatan.di("takut_petir", Kegiatan.RUMAH, GerakId.TAKUT_SEMBUNYI, 0);
			}
			if (g == NpcRole.Kelompok.KULTIVATOR && peran.petarung() && (p.benih() & 2L) == 0L) {
				return Kegiatan.di("tatap_petir", Kegiatan.TETAP, GerakId.TATAP_LANGIT, 0);
			}
		}
		if (c.kabut() && g == NpcRole.Kelompok.KULTIVATOR && s.jam() >= 7 && s.jam() < 19) {
			// dense Qi mist: the cultivators sit outside to drink it in
			return Kegiatan.di("meditasi_kabut", "latihan", GerakId.NAPAS_MEDITASI, 0);
		}
		if (c.salju() && luar && g != NpcRole.Kelompok.PENJAGA) {
			return Kegiatan.di("hangat", Kegiatan.RUMAH, GerakId.HANGAT_TANGAN, 0);
		}
		if (c.hujan()) {
			if (g == NpcRole.Kelompok.PENJAGA) {
				return k;
			}
			KerjaPeran.Kerja kerja = KerjaPeran.untuk(peran, p.benih());
			if ("kerja".equals(k.kunci()) && KerjaPeran.diLuar(kerja) && (peran == NpcRole.PETANI || peran == NpcRole.PEMETIK_TEH
					|| peran == NpcRole.PEMETIK_HERBA || peran == NpcRole.PEMELIHARA_ULAT || g == NpcRole.Kelompok.ANAK)) {
				return Kegiatan.di("berteduh", Kegiatan.RUMAH, GerakId.DUDUK_KURSI, 0);
			}
			if (g == NpcRole.Kelompok.ANAK && luar) {
				return Kegiatan.di("main_rumah", Kegiatan.RUMAH, GerakId.MAIN_GASING, PropId.GASING);
			}
			if (luar && k.propKiri() == 0) {
				// the paper umbrella in the free hand
				return k.denganProp(k.propKanan(), PropId.PAYUNG_KERTAS, k.propPunggung());
			}
		}
		return k;
	}

	/** {@code "<x>,<z>"} as two ints, or null when it is not that. */
	public static int @Nullable [] xz(String text) {
		String[] parts = text.split(",");
		if (parts.length < 2) {
			return null;
		}
		try {
			return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** True for goals out in the open (rain falls on them). */
	public static boolean luar(String tujuan) {
		if (tujuan.startsWith(Kegiatan.XZ)) {
			return true;
		}
		return switch (tujuan) {
			case "obrol", "main", "latihan", "jaga", "jalan_lentera", "penonton", "pandang", "kerja:cangkul", "kerja:pancing", "kerja:jala",
					"kerja:sabit", "tambatan", "kandang_unta", "jalur_tangga", "jalur_perahu", "jalan" -> true;
			default -> false;
		};
	}

	private Jadwal() {
	}
}
