package dev.kultivator.client.sosok.gerak;

import dev.kultivator.duel.blade.DuelPose;
import dev.kultivator.technique.spec.dsl.Pose;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * One animation clip of the shared Gerak system (DESIGN 40.5.3, contract C40.G1): keys on up to 24 channels, waves,
 * hold modifiers, the layer it plays in, its source (data keys, an existing function pose, sampled cast or duel poses),
 * default props with their attach points, timed events, the face it shows, variants and flags. Built once at client
 * init through {@link Pembangun} ({@code Klip.baru(id, nama, durasi).loop().kunci(t, kanal, nilai, ...)...bangun()}),
 * immutable afterwards; the tables write degrees, the builder stores radians for angle channels.
 * <p>
 * <b>Channels</b> 0-17 are {@code PoseLibrary}'s ({@link #HX} ... {@link #TILT}, meaning and signs as its javadoc: arm
 * {@code xRot < 0} raises forward, right arm {@code yRot < 0} swings inward, {@code LEAN > 0} bends forward, {@code LIFT}
 * in blocks); 18-23 are Gerak's own (40.5.2): {@link #HZ} head roll, {@link #BZ} hip sway, {@link #BOB} whole-body bob in
 * model pixels (+ up), {@link #SPIN} extra yaw in degrees per tick, {@link #BAHU} shoulder raise in pixels, {@link #NAPAS}
 * breath amplitude multiplier.
 */
public record Klip(int id, String nama, int durasi, Mode mode, long mask, Kunci[] kunci, Osilator[] osc, int mods, float modAmp,
		Lapisan lapisan, Sumber sumber, int propKanan, int propKiri, int propPunggung, PropPasang[] pasang, Peristiwa[] peristiwa,
		int wajahMata, int wajahMulut, Varian[] varian, int bendera, Jalur[] jalur, @Nullable float[] bakar, long maskBakar) {

	// ================================================================== channels

	public static final int HX = 0, HY = 1, LEAN = 2, TWIST = 3, RAX = 4, RAY = 5, RAZ = 6, LAX = 7, LAY = 8, LAZ = 9, RLX = 10,
			RLZ = 11, LLX = 12, LLZ = 13, LIFT = 14, RLY = 15, LLY = 16, TILT = 17;
	public static final int HZ = 18, BZ = 19, BOB = 20, SPIN = 21, BAHU = 22, NAPAS = 23;
	/** All channels. */
	public static final int KANAL = 24;
	/** The channels that go through the {@code CastPoses.Snapshot} (PoseApplier); the rest are applied by GerakApplier. */
	public static final int KANAL_POSE = 18;

	/** Channels of each layer mask. */
	public static final long MASK_KEPALA = bit(HX) | bit(HY) | bit(HZ);
	public static final long MASK_LENGAN = bit(RAX) | bit(RAY) | bit(RAZ) | bit(LAX) | bit(LAY) | bit(LAZ);
	public static final long MASK_KAKI = bit(RLX) | bit(RLZ) | bit(LLX) | bit(LLZ) | bit(RLY) | bit(LLY);
	public static final long MASK_ATAS = MASK_KEPALA | MASK_LENGAN | bit(LEAN) | bit(TWIST) | bit(BAHU);

	/** True for a channel stored in radians (tables write degrees). */
	public static boolean sudut(int kanal) {
		return kanal != LIFT && kanal != BOB && kanal != SPIN && kanal != BAHU && kanal != NAPAS;
	}

	public static long bit(int kanal) {
		return 1L << kanal;
	}

	// ================================================================== enums and parts

	/** LOOP repeats; SEKALI returns to the layer below at its end; SEKALI_TAHAN holds the last key. */
	public enum Mode { LOOP, SEKALI, SEKALI_TAHAN }

	/** PENUH every channel; ATAS arms, head and upper body; KEPALA head only; JALAN the walk profile layer. */
	public enum Lapisan { PENUH, ATAS, KEPALA, JALAN }

	/** DATA keys; FUNGSI an existing imperative pose (ids 150-164, no keys); CAST / DUEL sampled poses. */
	public enum Sumber { DATA, FUNGSI, CAST, DUEL }

	/** Attach points of props (40.5.7). */
	public enum Titik { KANAN, KIRI, DUA_TANGAN, PUNGGUNG, PINGGANG_KIRI, PINGGANG_KANAN, KEPALA, PANGKUAN, BAHU_KIRI, DEPAN_KAKI }

	/** A prop at a point with an offset (pixels), a turn (degrees) and a scale. */
	public record PropPasang(int prop, Titik titik, float dx, float dy, float dz, float rx, float ry, float rz, float skala) {
	}

	/** Event kinds: SUARA (id, volume, pitch), PARTIKEL (id, count, colour or -1 = Dao), WAJAH (eye, mouth), PROP (slot, prop), KAIT (id). */
	public enum Jenis { SUARA, PARTIKEL, WAJAH, PROP, KAIT }

	/** Prop event slots. */
	public static final int PROP_SLOT_KANAN = 0, PROP_SLOT_KIRI = 1, PROP_SLOT_PUNGGUNG = 2, PROP_SLOT_PASANG = 3;

	/** A timed event at clip tick {@code t} (once per cycle). */
	public record Peristiwa(int t, Jenis jenis, String id, float a, float b) {
	}

	/** A variant: picked when {@code syarat} matches (see {@link #SY_INDEKS}); {@code isi} is a whole clip. */
	public record Varian(int syarat, Klip isi) {
	}

	/** One channel's keys, sorted by tick (the easing is the one entering each key). */
	public record Jalur(int[] t, float[] v, Easing[] e) {
	}

	// ================================================================== flags, mods, variant conditions

	public static final int DUDUK = 1, BERBARING = 1 << 1, PROP_LAMA = 1 << 2, MATA_TUTUP = 1 << 3, BICARA = 1 << 4, SINKRON = 1 << 5,
			KAKI_PAKSA = 1 << 6, TANPA_NAPAS = 1 << 7, JALAN_OK = 1 << 8, DUA_TANGAN = 1 << 9, MATA_SETENGAH_WAKTU = 1 << 10,
			KERJA = 1 << 11, LIRIK_BAWAH = 1 << 12, MELAYANG = 1 << 13, PUTAR = 1 << 14, LIRIK_ATAS = 1 << 15,
			/** The right arm turns toward the {@code DATA_LOOK_AT} target (pointing, threatening, patting a shoulder). */
			ARAH_SASARAN = 1 << 16,
			/** Seated cross-legged on the meditation seat ({@code MeditationPose}); the clip's arms and head sit on top. */
			BERSILA = 1 << 17;

	public static final int MOD_NAPAS_X = 1, MOD_GEMETAR = 2, MOD_DETAK = 4, MOD_GELENG = 8, MOD_ANGKAT_TUMBUH = 16,
			MOD_GUNCANG_MUATAN = 32;

	/** Low 8 bits of a variant's condition: the server variant index it answers (0 = only when the server sends 0). */
	public static final int SY_INDEKS = 0xFF;
	public static final int SY_P = 1 << 8, SY_L = 1 << 9, SY_ANAK = 1 << 10, SY_TUA = 1 << 11, SY_BELA_DIRI = 1 << 12,
			SY_SIPIL = 1 << 13, SY_TETUA = 1 << 14, SY_SIFAT = 1 << 15;
	private static final int SY_SIFAT_SHIFT = 16;

	/** A condition on a trait ({@code Sifat.ordinal()}). */
	public static int sifat(int ordinal) {
		return SY_SIFAT | (ordinal & 15) << SY_SIFAT_SHIFT;
	}

	/** True when the condition fits the server variant, the body's trait flags ({@code SY_*}) and its trait set. */
	public static boolean cocok(int syarat, int varianServer, int ciri, int sifatBit) {
		if ((syarat & SY_INDEKS) != varianServer) {
			return false;
		}
		int perlu = syarat & (SY_P | SY_L | SY_ANAK | SY_TUA | SY_BELA_DIRI | SY_SIPIL | SY_TETUA);
		if ((ciri & perlu) != perlu) {
			return false;
		}
		if ((syarat & SY_SIFAT) != 0) {
			int ord = syarat >>> SY_SIFAT_SHIFT & 15;
			return (sifatBit & 1 << ord) != 0;
		}
		return true;
	}

	/** The clip to play for these conditions: the first variant that fits, else this clip. */
	public Klip pilih(int varianServer, int ciri, int sifatBit) {
		for (Varian v : varian) {
			if (cocok(v.syarat, varianServer, ciri, sifatBit)) {
				return v.isi;
			}
		}
		return this;
	}

	/** True when the clip has flag {@code b}. */
	public boolean punya(int b) {
		return (bendera & b) != 0;
	}

	/** True when the clip drives {@code kanal}. */
	public boolean menggerakkan(int kanal) {
		return (mask & bit(kanal)) != 0;
	}

	/** True for a looping clip. */
	public boolean loop() {
		return mode == Mode.LOOP;
	}

	// ================================================================== builder

	/** Starts a clip; {@code durasi} in ticks (loops: one cycle). */
	public static Pembangun baru(int id, String nama, int durasi) {
		return new Pembangun(id, nama, durasi);
	}

	/** A cast pose segment of a CAST clip ({@code cermin}: left-handed mirror). */
	public record SegCast(Pose pose, int t0, int t1, boolean cermin) {
	}

	/** A duel pose segment of a DUEL clip, {@code durasi} ticks long. */
	public record SegDuel(DuelPose pose, int durasi, boolean cermin) {
	}

	/** Fluent clip builder (C40.G1). Degrees in, radians stored. */
	public static final class Pembangun {
		private final int id;
		private final String nama;
		private int durasi;
		private Mode mode = Mode.SEKALI;
		private final List<Kunci> kunci = new ArrayList<>();
		private final List<Osilator> osc = new ArrayList<>();
		private int mods;
		private float modAmp;
		private Lapisan lapisan = Lapisan.PENUH;
		private Sumber sumber = Sumber.DATA;
		private int propKanan;
		private int propKiri;
		private int propPunggung;
		private final List<PropPasang> pasang = new ArrayList<>();
		private final List<Peristiwa> peristiwa = new ArrayList<>();
		private int wajahMata = -1;
		private int wajahMulut = -1;
		private final List<Varian> varian = new ArrayList<>();
		private int bendera;
		private long maskTambah;
		private final List<SegCast> segCast = new ArrayList<>();
		private final List<SegDuel> segDuel = new ArrayList<>();
		private long maskSumber = -1L;
		private @Nullable Pembangun induk;

		Pembangun(int id, String nama, int durasi) {
			this.id = id;
			this.nama = nama;
			this.durasi = Math.max(1, durasi);
		}

		public Pembangun loop() {
			mode = Mode.LOOP;
			return this;
		}

		public Pembangun sekali() {
			mode = Mode.SEKALI;
			return this;
		}

		public Pembangun tahan() {
			mode = Mode.SEKALI_TAHAN;
			return this;
		}

		public Pembangun durasi(int ticks) {
			durasi = Math.max(1, ticks);
			return this;
		}

		public Pembangun lapisan(Lapisan l) {
			lapisan = l;
			return this;
		}

		/** A key at tick {@code t}: pairs of (channel, value in degrees / blocks / pixels), easing SINE_IO. */
		public Pembangun kunci(int t, float... pasangan) {
			return kunciE(t, Easing.SINE_IO, pasangan);
		}

		/** A key with its own easing. */
		public Pembangun kunciE(int t, Easing masuk, float... pasangan) {
			float[] v = new float[KANAL];
			Arrays.fill(v, Float.NaN);
			for (int i = 0; i + 1 < pasangan.length; i += 2) {
				int c = (int) pasangan[i];
				v[c] = pasangan[i + 1];
			}
			kunci.add(new Kunci(t, v, masuk));
			return this;
		}

		/** A sine wave on a channel (amplitude in table units, period in ticks). */
		public Pembangun osc(int kanal, float amp, float periode) {
			return osc(kanal, amp, periode, 0.0F, Osilator.Bentuk.SIN);
		}

		/** A wave with phase (radians) and shape. */
		public Pembangun osc(int kanal, float amp, float periode, float fase, Osilator.Bentuk bentuk) {
			osc.add(new Osilator(kanal, amp, periode, fase, bentuk));
			return this;
		}

		public Pembangun mods(int m, float amp) {
			mods = m;
			modAmp = amp;
			return this;
		}

		/** Default props (PropId; 0 = none). */
		public Pembangun prop(int kanan, int kiri, int punggung) {
			propKanan = kanan;
			propKiri = kiri;
			propPunggung = punggung;
			return this;
		}

		/** A prop at a point other than the hand defaults (offset px, turn degrees, scale). */
		public Pembangun pasang(int prop, Titik titik, float dx, float dy, float dz, float rx, float ry, float rz, float skala) {
			pasang.add(new PropPasang(prop, titik, dx, dy, dz, rx, ry, rz, skala));
			return this;
		}

		public Pembangun suara(int t, String id, float volume, float pitch) {
			peristiwa.add(new Peristiwa(t, Jenis.SUARA, id, volume, pitch));
			return this;
		}

		/** A particle event: {@code id} a particle id ({@code kultivator:qi_tipis} = soft qi motes), count, colour (-1 = Dao). */
		public Pembangun partikel(int t, String id, int jumlah, int warna) {
			peristiwa.add(new Peristiwa(t, Jenis.PARTIKEL, id, jumlah, warna));
			return this;
		}

		/** A face change at tick {@code t} (-1 keeps that part). */
		public Pembangun wajahPada(int t, int mata, int mulut) {
			peristiwa.add(new Peristiwa(t, Jenis.WAJAH, "", mata, mulut));
			return this;
		}

		/**
		 * A prop change at tick {@code t}: {@code slot} {@link #PROP_SLOT_KANAN}, {@link #PROP_SLOT_KIRI},
		 * {@link #PROP_SLOT_PUNGGUNG} or {@link #PROP_SLOT_PASANG} (the attached props; 0 hides them), {@code prop} the new id.
		 */
		public Pembangun propPada(int t, int slot, int prop) {
			peristiwa.add(new Peristiwa(t, Jenis.PROP, "", slot, prop));
			return this;
		}

		/** A registered client hook ({@link GerakKatalog#daftarKait}) fired at tick {@code t}. */
		public Pembangun kait(int t, String id) {
			peristiwa.add(new Peristiwa(t, Jenis.KAIT, id, 0.0F, 0.0F));
			return this;
		}

		/** The face the whole clip shows (-1 = the mood decides). */
		public Pembangun wajah(int mata, int mulut) {
			wajahMata = mata;
			wajahMulut = mulut;
			return this;
		}

		public Pembangun bendera(int b) {
			bendera |= b;
			return this;
		}

		/** Extra driven channels (sampled sources). */
		public Pembangun mask(int... kanal) {
			for (int c : kanal) {
				maskTambah |= bit(c);
			}
			return this;
		}

		/** Restricts which channels a CAST/DUEL source writes (default: arms, head, lean, twist). */
		public Pembangun maskSumber(int... kanal) {
			maskSumber = 0L;
			for (int c : kanal) {
				maskSumber |= bit(c);
			}
			return this;
		}

		/** A cast pose segment (ticks {@code t0..t1}). */
		public Pembangun cast(Pose pose, int t0, int t1, boolean cermin) {
			sumber = Sumber.CAST;
			segCast.add(new SegCast(pose, t0, t1, cermin));
			return this;
		}

		/** A duel pose segment appended after the previous ones. */
		public Pembangun duel(DuelPose pose, int ticks) {
			return duel(pose, ticks, false);
		}

		public Pembangun duel(DuelPose pose, int ticks, boolean cermin) {
			sumber = Sumber.DUEL;
			segDuel.add(new SegDuel(pose, ticks, cermin));
			return this;
		}

		/** Marks an imperative function pose (ids 150-164): no keys. */
		public Pembangun fungsi() {
			sumber = Sumber.FUNGSI;
			return this;
		}

		/**
		 * A variant: {@code isi} configures a copy that inherits everything but the keys and waves (call
		 * {@link #warisi()} inside to keep those too).
		 */
		public Pembangun varian(int syarat, Consumer<Pembangun> isi) {
			Pembangun v = new Pembangun(id, nama, durasi);
			v.induk = this;
			v.mode = mode;
			v.mods = mods;
			v.modAmp = modAmp;
			v.lapisan = lapisan;
			v.propKanan = propKanan;
			v.propKiri = propKiri;
			v.propPunggung = propPunggung;
			v.pasang.addAll(pasang);
			v.wajahMata = wajahMata;
			v.wajahMulut = wajahMulut;
			v.bendera = bendera;
			isi.accept(v);
			varian.add(new Varian(syarat, v.bangun()));
			return this;
		}

		/** Inside a variant: copies the parent's keys, waves, events and sources first. */
		public Pembangun warisi() {
			if (induk != null) {
				kunci.addAll(0, induk.kunci);
				osc.addAll(0, induk.osc);
				peristiwa.addAll(0, induk.peristiwa);
				if (segCast.isEmpty()) {
					segCast.addAll(induk.segCast);
				}
				if (segDuel.isEmpty()) {
					segDuel.addAll(induk.segDuel);
				}
				if (sumber == Sumber.DATA) {
					sumber = induk.sumber;
				}
				maskTambah |= induk.maskTambah;
				maskSumber = induk.maskSumber;
			}
			return this;
		}

		/** Clears the keys and waves gathered so far (variants that start from scratch after {@link #warisi()}). */
		public Pembangun tanpaKunci() {
			kunci.clear();
			osc.clear();
			return this;
		}

		public Pembangun tanpaProp() {
			propKanan = 0;
			propKiri = 0;
			propPunggung = 0;
			pasang.clear();
			return this;
		}

		public Pembangun tanpaBendera(int b) {
			bendera &= ~b;
			return this;
		}

		/** Builds the immutable clip (degrees to radians, per-channel tracks, sampled sources baked per tick). */
		public Klip bangun() {
			Kunci[] ks = new Kunci[kunci.size()];
			long m = maskTambah;
			for (int i = 0; i < ks.length; i++) {
				Kunci k = kunci.get(i);
				float[] v = k.nilai().clone();
				for (int c = 0; c < KANAL; c++) {
					if (!Float.isNaN(v[c])) {
						m |= bit(c);
						if (sudut(c)) {
							v[c] *= Mth.DEG_TO_RAD;
						}
					}
				}
				ks[i] = new Kunci(Math.max(0, k.t()), v, k.masuk());
			}
			Arrays.sort(ks, (a, b) -> Integer.compare(a.t(), b.t()));
			Osilator[] os = new Osilator[osc.size()];
			for (int i = 0; i < os.length; i++) {
				Osilator o = osc.get(i);
				os[i] = sudut(o.kanal()) ? o.kali(Mth.DEG_TO_RAD) : o;
				m |= bit(o.kanal());
			}
			Jalur[] jalur = new Jalur[KANAL];
			for (int c = 0; c < KANAL; c++) {
				int n = 0;
				for (Kunci k : ks) {
					if (k.menulis(c)) {
						n++;
					}
				}
				if (n == 0) {
					continue;
				}
				int[] t = new int[n];
				float[] v = new float[n];
				Easing[] e = new Easing[n];
				int j = 0;
				for (Kunci k : ks) {
					if (k.menulis(c)) {
						t[j] = k.t();
						v[j] = k.nilai()[c];
						e[j] = k.masuk();
						j++;
					}
				}
				jalur[c] = new Jalur(t, v, e);
			}
			float[] bakar = null;
			long maskBakar = 0L;
			long sumberMask = maskSumber != -1L ? maskSumber : MASK_LENGAN | bit(HX) | bit(HY) | bit(LEAN) | bit(TWIST);
			if (sumber == Sumber.CAST && !segCast.isEmpty()) {
				bakar = SumberCast.bakar(segCast, durasi, sumberMask);
				maskBakar = sumberMask;
			} else if (sumber == Sumber.DUEL && !segDuel.isEmpty()) {
				int total = 0;
				for (SegDuel s : segDuel) {
					total += s.durasi();
				}
				durasi = Math.max(durasi, total);
				bakar = SumberDuel.bakar(segDuel, durasi, sumberMask, mode == Mode.LOOP);
				maskBakar = sumberMask;
			}
			m |= maskBakar;
			if ((mods & (MOD_GEMETAR | MOD_GUNCANG_MUATAN)) != 0) {
				m |= bit(RAX) | bit(RAZ) | bit(LAX) | bit(LAZ);
			}
			if ((mods & MOD_NAPAS_X) != 0) {
				m |= bit(HX);
			}
			if ((mods & MOD_GELENG) != 0) {
				m |= bit(HY);
			}
			if ((mods & MOD_DETAK) != 0) {
				m |= bit(BAHU);
			}
			if ((mods & MOD_ANGKAT_TUMBUH) != 0) {
				m |= bit(LIFT);
			}
			m &= maskLapisan(lapisan);
			Peristiwa[] ps = peristiwa.toArray(new Peristiwa[0]);
			Arrays.sort(ps, (a, b) -> Integer.compare(a.t(), b.t()));
			return new Klip(id, nama, durasi, mode, m, ks, os, mods, modAmp, lapisan, sumber, propKanan, propKiri, propPunggung,
					pasang.toArray(new PropPasang[0]), ps, wajahMata, wajahMulut, varian.toArray(new Varian[0]), bendera, jalur, bakar,
					maskBakar);
		}

		private static long maskLapisan(Lapisan l) {
			return switch (l) {
				case PENUH, JALAN -> -1L;
				case ATAS -> MASK_ATAS | bit(BZ) | bit(NAPAS) | bit(BOB) | bit(LIFT);
				case KEPALA -> MASK_KEPALA | bit(NAPAS);
			};
		}
	}
}
