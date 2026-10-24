package dev.kultivator.sekte.mandiri;

import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.api.HatiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.mandiri.api.Dorong;
import dev.kultivator.sekte.mandiri.api.IzinKegiatan;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.JenisCita;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.sekte.mandiri.api.ModeMandiri;
import dev.kultivator.sekte.mandiri.api.Pelaksana;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The utility score of every activity for one disciple at one hour (DESIGN 39.4.3):
 * <pre>
 * skor(k) = dasar(k) x jalan(m.jalan, k) x jam(k, h) x (1 + sum_d w(k,d) x dorong_d / 100) x sifat(m, k) x cita(m, k)
 *         x kebaruan(k) x izin(k) x keadaan(m) + inersia
 * </pre>
 * with the path table of 39.3.1, the hour curve and day factors of {@link Irama}, the drive weights of the table in 39.4.3
 * (config {@code mandiri.bobot} overrides {@code "<kegiatan>.<dorong>"} and {@code "<jalan>.<kegiatan>"}), the traits
 * (diligent x1.2 work/training/study; lazy x1.3 rest, x0.7 work; brave x1.3 journeys; timid x0.5 journeys; caring x1.3
 * company/teaching; proud x1.2 sparring, x0.7 work), the goals (x1.5 for the priority-1 goal, x1.25 for the others),
 * novelty (x0.6 after three hours today, x1.2 when not done for three days), the policy and the place (0 when forbidden,
 * missing, refused by the executor or {@code SibukMurid}), the loyalty state (restless x0.7 on sect duties; defiant only
 * rest, contemplation, journeys) and +0.4 inertia for the activity still inside its block. One evaluation is about thirty
 * multiplications plus a place lookup in the hall cache; nothing scans the world.
 * <p>
 * The choice ({@link #pilih}): the three best scores, drawn by {@code skor^(1/T)} with temperature 0.5 (calm or
 * diligent), 1.0, or 1.6 (lazy, brave or sly).
 */
public final class Skor {

	/** The factors of one activity, kept for {@code uji perguruan mandiri skor}. */
	public static final int F_DASAR = 0;
	public static final int F_JALAN = 1;
	public static final int F_JAM = 2;
	public static final int F_DORONG = 3;
	public static final int F_SIFAT = 4;
	public static final int F_CITA = 5;
	public static final int F_BARU = 6;
	public static final int F_IZIN = 7;
	public static final int F_KEADAAN = 8;
	public static final int F_INERSIA = 9;
	public static final int F_JUMLAH = 10;

	/** Why the place factor became 0 (shown by the test command): the reason key suffix. */
	public static final String[] SEBAB = {"", "izin", "tempat", "pelaksana", "keadaan", "jam_jalan", "antre", "gerak"};

	/** One evaluation. Arrays are indexed by {@link Kegiatan#ordinal()}. */
	public static final class Hasil {
		public final double[] skor = new double[Kegiatan.values().length];
		public final double[][] faktor = new double[Kegiatan.values().length][F_JUMLAH];
		public final BlockPos[] tempat = new BlockPos[Kegiatan.values().length];
		public final int[] sebab = new int[Kegiatan.values().length];
		public @Nullable UUID teman;
		public double suhu = 1.0D;
		public int jam;

		/** The best {@code n} activities with a score above 0, best first. */
		public Kegiatan[] teratas(int n) {
			Kegiatan[] all = Kegiatan.values();
			Kegiatan[] top = new Kegiatan[n];
			for (int slot = 0; slot < n; slot++) {
				Kegiatan best = null;
				for (Kegiatan k : all) {
					if (skor[k.ordinal()] <= 0.0D || contains(top, k)) {
						continue;
					}
					if (best == null || skor[k.ordinal()] > skor[best.ordinal()]) {
						best = k;
					}
				}
				top[slot] = best;
			}
			return top;
		}

		private static boolean contains(Kegiatan[] array, Kegiatan k) {
			for (Kegiatan each : array) {
				if (each == k) {
					return true;
				}
			}
			return false;
		}
	}

	// ================================================================== tables

	/** The base of an activity: 0 for the journey steps and feud scenes the owning modules start themselves. */
	static double dasar(Kegiatan k) {
		return switch (k) {
			case KOSONG, BERKEMAS, BERANGKAT, TIBA, CEKCOK, TANTANG, TERLUKA, SUMPAH -> 0.0D;
			case TEROBOSAN -> 3.0D;
			case JAGA_TEROBOSAN -> 2.5D;
			default -> 1.0D;
		};
	}

	/** The path weight of 39.3.1 (1.0 when the path does not care). */
	static double jalan(@Nullable JalanHidup jalan, Kegiatan k) {
		if (jalan == null) {
			return 1.0D;
		}
		double bawaan = switch (jalan) {
			case PEDANG -> switch (k) {
				case LATIH_JURUS -> 2.0D;
				case TANDING -> 1.8D;
				case PERJALANAN -> 1.3D;
				default -> 1.0D;
			};
			case ALKEMIS -> switch (k) {
				case KEBUN -> 1.8D;
				case MERAMU -> 2.5D;
				case BELAJAR -> 1.2D;
				case PERJALANAN -> 1.5D;
				default -> 1.0D;
			};
			case FORMASI -> switch (k) {
				case JAGA_FORMASI -> 2.0D;
				case JIMAT -> 2.2D;
				case BELAJAR -> 1.5D;
				case MERENUNG -> 1.3D;
				default -> 1.0D;
			};
			case PERTAPA -> switch (k) {
				case MEDITASI -> 2.2D;
				case TUTUP_DIRI -> 3.0D;
				case MERENUNG -> 2.0D;
				case PERJALANAN -> 1.2D;
				case SOSIAL -> 0.7D;
				default -> 1.0D;
			};
			case PETUALANG -> switch (k) {
				case PERJALANAN -> 2.5D;
				case SOSIAL -> 1.2D;
				case TUTUP_DIRI -> 0.6D;
				default -> 1.0D;
			};
			case PEMBURU -> switch (k) {
				case PERJALANAN -> 2.5D;
				case LATIH_JURUS -> 1.4D;
				default -> 1.0D;
			};
			case PEDAGANG -> switch (k) {
				case PERJALANAN -> 2.5D;
				case SOSIAL -> 1.6D;
				case MAKAN -> 1.2D;
				default -> 1.0D;
			};
			case CENDEKIA -> switch (k) {
				case BELAJAR -> 2.5D;
				case MENGAJAR -> 2.0D;
				case MERENUNG -> 1.5D;
				case PERJALANAN -> 1.2D;
				default -> 1.0D;
			};
			case PENJAGA -> switch (k) {
				case JAGA_GERBANG -> 2.2D;
				case LATIH_JURUS -> 1.5D;
				case MENGAJAR -> 1.3D;
				case TAMBANG -> 1.2D;
				default -> 1.0D;
			};
			case IBLIS -> switch (k) {
				case TUTUP_DIRI -> 2.0D;
				case PERJALANAN -> 2.5D;
				case SOSIAL -> 0.4D;
				case MAKAN -> 0.7D;
				default -> 1.0D;
			};
		};
		return MandiriConfig.get().bobot(jalan.id() + "." + k.id(), bawaan);
	}

	/** The drive weight {@code w(k, d)} of 39.4.3 ({@code belanja}: the journey row while supplies are wanted). */
	static double bobot(Kegiatan k, Dorong d, boolean belanja) {
		double bawaan = switch (k) {
			case MEDITASI -> switch (d) {
				case KULTIVASI -> 1.5D;
				case BOSAN -> -0.3D;
				case LELAH -> 0.3D;
				case SOSIAL -> -0.2D;
				case AMBISI, LUKA -> 0.5D;
				default -> 0.0D;
			};
			case TUTUP_DIRI -> switch (d) {
				case KULTIVASI -> 2.0D;
				case BOSAN -> -1.0D;
				case SOSIAL -> -0.8D;
				case AMBISI -> 1.0D;
				case BARANG -> -0.5D;
				default -> 0.0D;
			};
			case SERAP_BATU -> switch (d) {
				case KULTIVASI -> 1.2D;
				case AMBISI -> 0.8D;
				case BOSAN -> 0.2D;
				default -> 0.0D;
			};
			case LATIH_JURUS -> switch (d) {
				case KULTIVASI -> 0.5D;
				case BOSAN -> 0.3D;
				case LELAH -> -1.0D;
				case AMBISI -> 0.8D;
				case LUKA -> -1.5D;
				default -> 0.0D;
			};
			case TANDING -> switch (d) {
				case KULTIVASI -> 0.3D;
				case BOSAN, SOSIAL -> 0.6D;
				case LELAH -> -1.2D;
				case AMBISI -> 1.0D;
				case LUKA -> -2.0D;
				default -> 0.0D;
			};
			case BELAJAR -> switch (d) {
				case KULTIVASI -> 0.4D;
				case BOSAN -> 0.2D;
				case LELAH -> -0.2D;
				case AMBISI -> 0.6D;
				default -> 0.0D;
			};
			case MENGAJAR -> switch (d) {
				case SOSIAL -> 0.6D;
				case AMBISI -> 0.4D;
				case BOSAN -> 0.2D;
				case LELAH -> -0.3D;
				default -> 0.0D;
			};
			case MERAMU, JIMAT -> switch (d) {
				case BOSAN, AMBISI -> 0.4D;
				case LELAH -> -0.4D;
				case BARANG -> -0.6D;
				default -> 0.0D;
			};
			case KEBUN, TAMBANG, POS_TUGAS, JAGA_FORMASI, JAGA_GERBANG -> switch (d) {
				case BOSAN, SOSIAL -> 0.2D;
				case LELAH -> -0.8D;
				case LUKA -> -1.0D;
				default -> 0.0D;
			};
			case SOSIAL, BERMAIN -> switch (d) {
				case BOSAN -> 0.6D;
				case LELAH -> 0.3D;
				case SOSIAL -> 2.0D;
				case LUKA -> 0.2D;
				default -> 0.0D;
			};
			case ISTIRAHAT -> switch (d) {
				case BOSAN, AMBISI -> -0.5D;
				case LELAH -> 2.5D;
				case LUKA -> 2.0D;
				default -> 0.0D;
			};
			case MAKAN -> switch (d) {
				case LELAH -> 1.0D;
				case SOSIAL -> 0.4D;
				case LUKA -> 0.3D;
				default -> 0.0D;
			};
			case PERJALANAN -> belanja ? switch (d) {
				case BOSAN -> 1.0D;
				case LELAH -> -0.6D;
				case SOSIAL, AMBISI -> 0.3D;
				case BARANG -> 2.5D;
				case LUKA -> -1.5D;
				default -> 0.0D;
			} : switch (d) {
				case KULTIVASI -> 0.3D;
				case BOSAN -> 1.5D;
				case LELAH -> -0.8D;
				case SOSIAL -> 0.2D;
				case AMBISI -> 1.2D;
				case LUKA -> -2.0D;
				default -> 0.0D;
			};
			case MERENUNG -> switch (d) {
				case KULTIVASI -> 0.6D;
				case BOSAN, LELAH, AMBISI -> d == Dorong.AMBISI ? 0.3D : 0.2D;
				case SOSIAL -> -0.3D;
				default -> 0.0D;
			};
			default -> 0.0D;
		};
		return MandiriConfig.get().bobot(k.id() + "." + d.id(), bawaan);
	}

	/** The trait factor of 39.4.3. */
	static double sifat(HatiService hati, DiscipleData data, Kegiatan k) {
		double f = 1.0D;
		boolean kerja = k.izin() == IzinKegiatan.KERJA || k.izin() == IzinKegiatan.KERAJINAN;
		if (hati.has(data, Sifat.RAJIN) && (kerja || k == Kegiatan.LATIH_JURUS || k == Kegiatan.BELAJAR)) {
			f *= 1.2D;
		}
		if (hati.has(data, Sifat.MALAS)) {
			if (k == Kegiatan.ISTIRAHAT) {
				f *= 1.3D;
			} else if (kerja) {
				f *= 0.7D;
			}
		}
		if (k == Kegiatan.PERJALANAN) {
			if (hati.has(data, Sifat.BERANI)) {
				f *= 1.3D;
			}
			if (hati.has(data, Sifat.PENAKUT)) {
				f *= 0.5D;
			}
		}
		if (hati.has(data, Sifat.PENYAYANG) && (k == Kegiatan.SOSIAL || k == Kegiatan.MENGAJAR || k == Kegiatan.JAGA_TEROBOSAN)) {
			f *= 1.3D;
		}
		if (hati.has(data, Sifat.SOMBONG)) {
			if (k == Kegiatan.TANDING) {
				f *= 1.2D;
			} else if (kerja) {
				f *= 0.7D;
			}
		}
		return f;
	}

	/** True when activity {@code k} works toward a goal of kind {@code jenis} (table 39.3.4). */
	static boolean mendorong(JenisCita jenis, Kegiatan k) {
		return switch (jenis) {
			case RANAH -> k == Kegiatan.MEDITASI || k == Kegiatan.TUTUP_DIRI || k == Kegiatan.SERAP_BATU || k == Kegiatan.TEROBOSAN;
			case DAO -> k == Kegiatan.MERENUNG || k == Kegiatan.LATIH_JURUS || k == Kegiatan.PERJALANAN;
			case TEKNIK -> k == Kegiatan.BELAJAR || k == Kegiatan.MENGAJAR || k == Kegiatan.PERJALANAN;
			case PUSAKA, RAHASIA -> k == Kegiatan.PERJALANAN;
			case JUARA -> k == Kegiatan.LATIH_JURUS || k == Kegiatan.TANDING;
			case DENDAM -> k == Kegiatan.LATIH_JURUS || k == Kegiatan.TANDING || k == Kegiatan.PERJALANAN;
			case JODOH -> k == Kegiatan.SOSIAL || k == Kegiatan.MAKAN || k == Kegiatan.PERJALANAN;
			case GURU -> k == Kegiatan.MEDITASI || k == Kegiatan.TUTUP_DIRI || k == Kegiatan.SERAP_BATU || k == Kegiatan.MERENUNG;
			case JENJANG -> k.izin() == IzinKegiatan.KERJA || k.izin() == IzinKegiatan.JAGA || k == Kegiatan.MENGAJAR;
			case KEKAYAAN -> k == Kegiatan.PERJALANAN || k == Kegiatan.MERAMU || k == Kegiatan.JIMAT;
			case BINATANG -> k == Kegiatan.PERJALANAN || k == Kegiatan.LATIH_JURUS;
			case MENOLONG -> k == Kegiatan.JAGA_GERBANG || k == Kegiatan.PERJALANAN;
			case KARYA -> k == Kegiatan.MERAMU || k == Kegiatan.JIMAT;
		};
	}

	/** Every journey permission (any one allows the planner to consider a journey; the executor picks the kind). */
	private static final int IZIN_JALAN = IzinKegiatan.BELANJA.bit() | IzinKegiatan.MISI_GILDA.bit() | IzinKegiatan.BERBURU.bit()
			| IzinKegiatan.RERUNTUHAN.bit() | IzinKegiatan.GUA.bit() | IzinKegiatan.ALAM_RAHASIA.bit()
			| IzinKegiatan.BERKELANA.bit() | IzinKegiatan.DESA_SEKTE.bit();

	// ================================================================== evaluation

	/**
	 * Scores every activity for {@code murid} now. {@code berjalan} is the running planner activity (inertia) or null;
	 * {@code bebasPergi} whether the hall may send one more disciple away this hour (departure queue and away share).
	 */
	public static Hasil nilai(DiscipleEntity murid, @Nullable Kegiatan berjalan, boolean bebasPergi) {
		ServerLevel level = (ServerLevel) murid.level();
		DiscipleData data = murid.data();
		UUID owner = murid.ownerId();
		HatiService hati = SekteApi.hati();
		Kebijakan kebijakan = MandiriApi.kebijakan(murid);
		int izin = KebijakanAula.izinEfektif(murid, kebijakan);
		JalanHidup jalan = MandiriApi.jalan(data);
		int[] dorong = Dorongan.baca(data);
		boolean belanja = dorong[Dorong.BARANG.ordinal()] >= 30;
		int jam = Irama.jam(level);
		long hari = Irama.hari(level);
		long now = level.getGameTime();
		BlockPos pusat = owner == null ? null : Irama.pusat(owner);
		BlockPos at = murid.blockPosition();
		int bulan = Irama.bulan(level, pusat != null ? pusat : at);
		boolean hujan = Irama.hujan(level, at);
		boolean salju = Irama.salju(level, at);
		int realm = data.realm().ordinal();
		boolean penjaga = jalan == JalanHidup.PENJAGA;
		boolean turnamen = owner != null && Irama.turnamen(owner);
		boolean serbuan = owner != null && Irama.serbuan(owner);
		HatiService.Keadaan keadaan = hati.state(data);
		boolean membangkang = hati.defiant(data);
		JenisLuka luka = MandiriApi.luka(data, now);
		boolean berat = luka == JenisLuka.BERAT || luka == JenisLuka.TERTAWAN;
		boolean deviasi = data.deviating();
		boolean tergoda = MandiriApi.iblis(data) >= 60;
		boolean muda = realm < Realm.GOLDEN_CORE.ordinal();
		Halls.HallView aula = owner == null ? null : Halls.hallOf(owner);
		boolean diAula = aula != null && aula.dim() == level.dimension()
				&& at.distSqr(aula.pos()) <= (double) (aula.radius() + 8) * (aula.radius() + 8);

		CompoundTag mandiri = MandiriApi.bagian(data);
		int[] hariIni = jamHariIni(mandiri, hari);
		CompoundTag kegHari = mandiri.getCompoundOrEmpty(MandiriIds.K_KEG_HARI);
		ListTag cita = mandiri.getListOrEmpty(MandiriIds.K_CITA);

		Hasil out = new Hasil();
		out.jam = jam;
		out.suhu = suhu(hati, data);
		out.teman = teman(murid);

		for (Kegiatan k : Kegiatan.values()) {
			int i = k.ordinal();
			double[] f = out.faktor[i];
			f[F_DASAR] = dasar(k);
			if (f[F_DASAR] <= 0.0D) {
				continue;
			}
			f[F_JALAN] = jalan(jalan, k);
			f[F_JAM] = Irama.kurva(k, jam, realm, penjaga) * Irama.hariIni(k, jam, hari, bulan, hujan, salju, realm);
			if (muda && (jam == 23 || jam <= 2)) {
				// Mortal to Foundation sleep four hours a night (39.4.4).
				f[F_JAM] *= k == Kegiatan.ISTIRAHAT ? 5.0D : 0.1D;
			}
			if (turnamen && (k == Kegiatan.LATIH_JURUS || k == Kegiatan.TANDING)) {
				f[F_JAM] *= 1.5D;
			}
			double sum = 1.0D;
			for (Dorong d : Dorong.values()) {
				sum += bobot(k, d, belanja) * dorong[d.ordinal()] / 100.0D;
			}
			f[F_DORONG] = Math.max(0.05D, sum);
			f[F_SIFAT] = sifat(hati, data, k);
			if (tergoda) {
				if (k == Kegiatan.PERJALANAN) {
					f[F_SIFAT] *= 1.3D;
				} else if (k == Kegiatan.SOSIAL) {
					f[F_SIFAT] *= 0.7D;
				}
			}
			if (kebijakan.mode() == ModeMandiri.SEIMBANG && (k.izin() == IzinKegiatan.KERJA || k.izin() == IzinKegiatan.JAGA)) {
				f[F_SIFAT] *= 1.2D;
			}
			f[F_CITA] = citaFaktor(cita, k);
			f[F_BARU] = kebaruan(k, hariIni, kegHari, hari);
			f[F_KEADAAN] = keadaan(k, keadaan, membangkang, berat, deviasi, serbuan);

			// Permission, place, executor, departure rules: 0 with the reason kept.
			f[F_IZIN] = 1.0D;
			int sebab = 0;
			boolean bolehIzin = k == Kegiatan.PERJALANAN ? (izin & IZIN_JALAN) != 0 : k.izin().boleh(izin);
			if (!bolehIzin) {
				sebab = 1;
			} else if (f[F_KEADAAN] <= 0.0D) {
				sebab = 4;
			} else if (k == Kegiatan.PERJALANAN && (jam < 5 || jam > 9)) {
				sebab = 5;
			} else if (k == Kegiatan.PERJALANAN && !bebasPergi) {
				sebab = 6;
			}
			BlockPos tempat = null;
			if (sebab == 0) {
				tempat = k == berjalan ? murid.activitySpot() != null ? murid.activitySpot() : at : TempatCache.cari(murid, k);
				if (tempat == null) {
					sebab = 2;
				}
			}
			if (sebab == 0 && k != berjalan && !AnggaranGerak.boleh(murid, tempat)) {
				sebab = 7;
			}
			if ((sebab == 2 || sebab == 7) && diTempat(k) && diAula) {
				// 39.4.6: no free seat or the walking budget is full: rest, sit or ponder where it stands (inside the hall)
				// instead of idling until the next hour
				tempat = at;
				sebab = 0;
			}
			if (sebab == 0) {
				Pelaksana pelaksana = MandiriKegiatan.pelaksana(k);
				Pelaksana.Konteks konteks = new Pelaksana.Konteks(level, owner, k, now,
						k.blokMin() < 0 ? -1L : now + k.blokMin(), tempat, out.teman, kebijakan);
				boolean bisa;
				try {
					bisa = pelaksana.bisa(murid, konteks);
				} catch (RuntimeException exception) {
					bisa = false;
				}
				if (!bisa) {
					sebab = 3;
				}
			}
			if (sebab != 0) {
				f[F_IZIN] = 0.0D;
				out.sebab[i] = sebab;
				continue;
			}
			out.tempat[i] = tempat;
			f[F_INERSIA] = k == berjalan ? 0.4D : 0.0D;
			out.skor[i] = f[F_DASAR] * f[F_JALAN] * f[F_JAM] * f[F_DORONG] * f[F_SIFAT] * f[F_CITA] * f[F_BARU] * f[F_IZIN]
					* f[F_KEADAAN] + f[F_INERSIA];
		}
		return out;
	}

	/**
	 * Draws one of the three best activities by {@code skor^(1/T)}; KOSONG when nothing scores (the disciple keeps its old
	 * behaviour: its post, its mode).
	 */
	public static Kegiatan pilih(Hasil hasil, RandomSource random) {
		Kegiatan[] top = hasil.teratas(3);
		double total = 0.0D;
		double[] w = new double[top.length];
		for (int i = 0; i < top.length; i++) {
			if (top[i] != null) {
				w[i] = Math.pow(hasil.skor[top[i].ordinal()], 1.0D / hasil.suhu);
				total += w[i];
			}
		}
		if (total <= 0.0D) {
			return Kegiatan.KOSONG;
		}
		double roll = random.nextDouble() * total;
		for (int i = 0; i < top.length; i++) {
			if (top[i] == null) {
				continue;
			}
			roll -= w[i];
			if (roll <= 0.0D) {
				return top[i];
			}
		}
		return top[0] == null ? Kegiatan.KOSONG : top[0];
	}

	// ================================================================== parts

	/** Activities that need no station: done on the spot when no seat is free or nobody more may walk (39.4.6). */
	static boolean diTempat(Kegiatan k) {
		return k == Kegiatan.ISTIRAHAT || k == Kegiatan.MEDITASI || k == Kegiatan.MERENUNG;
	}

	/** The choice temperature: 0.5 calm or diligent, 1.6 lazy, brave or sly, else 1.0. */
	static double suhu(HatiService hati, DiscipleData data) {
		if (hati.has(data, Sifat.MALAS) || hati.has(data, Sifat.BERANI) || hati.has(data, Sifat.LICIK)) {
			return 1.6D;
		}
		if (hati.has(data, Sifat.TENANG) || hati.has(data, Sifat.RAJIN)) {
			return 0.5D;
		}
		return 1.0D;
	}

	private static double citaFaktor(ListTag cita, Kegiatan k) {
		double best = 1.0D;
		for (int i = 0; i < cita.size(); i++) {
			CompoundTag goal = cita.getCompoundOrEmpty(i);
			JenisCita jenis = JenisCita.byId(goal.getStringOr("jenis", ""));
			if (jenis != null && mendorong(jenis, k)) {
				best = Math.max(best, goal.getIntOr("prio", 3) == 1 ? 1.5D : 1.25D);
			}
		}
		return best;
	}

	private static double kebaruan(Kegiatan k, int[] hariIni, CompoundTag kegHari, long hari) {
		if (hariIni[k.ordinal()] >= 3) {
			return 0.6D;
		}
		long last = kegHari.getLongOr(k.id(), -1L);
		return last < 0L || hari - last >= 3L ? 1.2D : 1.0D;
	}

	/** The loyalty and body state factor (0 = not allowed in this state). */
	private static double keadaan(Kegiatan k, HatiService.Keadaan keadaan, boolean membangkang, boolean berat, boolean deviasi,
			boolean serbuan) {
		if (serbuan) {
			return k == Kegiatan.JAGA_GERBANG || k == Kegiatan.JAGA_FORMASI ? 2.0D : 0.0D;
		}
		if (membangkang && k != Kegiatan.ISTIRAHAT && k != Kegiatan.MERENUNG && k != Kegiatan.PERJALANAN) {
			return 0.0D;
		}
		if (berat && k != Kegiatan.ISTIRAHAT && k != Kegiatan.MEDITASI && k != Kegiatan.MAKAN && k != Kegiatan.MERENUNG) {
			return 0.0D;
		}
		if (deviasi && k != Kegiatan.ISTIRAHAT && k != Kegiatan.MAKAN && k != Kegiatan.SOSIAL && k != Kegiatan.MERENUNG
				&& k != Kegiatan.MEDITASI) {
			return 0.0D;
		}
		if (keadaan == HatiService.Keadaan.GELISAH && (k.izin() == IzinKegiatan.KERJA || k.izin() == IzinKegiatan.JAGA)) {
			return 0.7D;
		}
		return 1.0D;
	}

	/** Hours of each activity in today's strip. */
	static int[] jamHariIni(CompoundTag mandiri, long hari) {
		int[] out = new int[Kegiatan.values().length];
		if (mandiri.getLongOr(MandiriIds.K_RIWAYAT_HARI, -1L) != hari) {
			return out;
		}
		int[] strip = mandiri.getIntArray(MandiriIds.K_RIWAYAT).orElse(new int[0]);
		for (int value : strip) {
			Kegiatan k = Kegiatan.byOrdinal(value);
			if (k != Kegiatan.KOSONG) {
				out[k.ordinal()]++;
			}
		}
		return out;
	}

	/**
	 * A companion for shared activities: the loaded hall-mate within 24 blocks with the best affinity that is free
	 * ({@code SibukMurid}) or idling in a planner rest; null when none.
	 */
	static @Nullable UUID teman(DiscipleEntity murid) {
		UUID owner = murid.ownerId();
		if (owner == null) {
			return null;
		}
		UUID best = null;
		int bestAffinity = Integer.MIN_VALUE;
		for (DiscipleEntity other : Perencana.dimuat(owner)) {
			if (other == murid || other.distanceToSqr(murid) > 24.0D * 24.0D || !Perencana.bebasUntukTeman(other)) {
				continue;
			}
			int affinity = SekteApi.hubungan().affinity(owner, murid.soulId(), other.soulId());
			if (affinity > bestAffinity) {
				bestAffinity = affinity;
				best = other.soulId();
			}
		}
		return best;
	}

	private Skor() {
	}
}
