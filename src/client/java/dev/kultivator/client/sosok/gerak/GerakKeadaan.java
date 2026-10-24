package dev.kultivator.client.sosok.gerak;

import dev.kultivator.client.anim.CastPoses;
import dev.kultivator.sosok.Gaya;
import dev.kultivator.sosok.Sosok;
import dev.kultivator.technique.spec.dsl.Pose;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Per-body animation state of the Gerak controller (DESIGN 40.5.4): the layer tracks with their fades, the smoothed
 * channel values, gaze, blink, breath, idle gestures and springs. One instance per entity id, kept in an
 * {@code Int2ObjectOpenHashMap} and swept every 200 ticks; all arrays are allocated once here and reused every frame.
 */
final class GerakKeadaan {

	/** One layer: its clip, start, fade in/out and the cross-fade from the clip it replaced. */
	static final class Jalur {
		final float masuk;
		final float keluar;
		int klip;
		int varian;
		@Nullable Klip k;
		float mulai;
		boolean on;
		float berubah = -1.0E9F;
		@Nullable Klip kLama;
		float mulaiLama;
		float lamaSampai = -1.0E9F;
		/** The last clip tick whose events fired, and the cycle it was in. */
		int tickPeristiwa = -1;
		int siklusPeristiwa = -1;

		Jalur(float masuk, float keluar) {
			this.masuk = masuk;
			this.keluar = keluar;
		}

		/** 0..1 weight of the layer now (SINE_IO fade). */
		float bobot(float now) {
			float x;
			if (on) {
				x = masuk <= 0.0F ? 1.0F : (now - berubah) / masuk;
			} else {
				x = keluar <= 0.0F ? 0.0F : 1.0F - (now - berubah) / keluar;
			}
			return Easing.SINE_IO.f(Math.max(0.0F, Math.min(1.0F, x)));
		}

		/**
		 * Asks for clip {@code resolved} (null = off; {@code id} identifies it, internal gestures use -1) at {@code now}. A different clip while the layer is visible cross-fades
		 * from the old one over the fade-in time; a fade direction change keeps the current weight.
		 */
		void minta(int id, int var, @Nullable Klip resolved, float now, float tMulai) {
			boolean mau = resolved != null;
			if (mau && (id != klip || var != varian || resolved != k)) {
				float w = bobot(now);
				if (w > 0.02F && k != null) {
					kLama = k;
					mulaiLama = mulai;
					lamaSampai = now + masuk;
				}
				klip = id;
				varian = var;
				k = resolved;
				mulai = tMulai;
				tickPeristiwa = -1;
				siklusPeristiwa = -1;
			} else if (mau && tMulai != mulai && Math.abs(tMulai - mulai) > 0.5F) {
				mulai = tMulai;
			}
			if (mau != on) {
				float current = bobot(now);
				on = mau;
				// continue from the current weight in the new direction
				berubah = mau ? now - current * masuk : now - (1.0F - current) * keluar;
			}
		}

		/** Turns a weight-only layer on or off (activity, cast/duel freedom), keeping the current weight. */
		void nyala(boolean mau, float now) {
			if (mau != on) {
				float current = bobot(now);
				on = mau;
				berubah = mau ? now - current * masuk : now - (1.0F - current) * keluar;
			}
		}

		/** The cross-fade share of the new clip (1 = only the new one). */
		float silang(float now) {
			if (kLama == null || now >= lamaSampai) {
				kLama = null;
				return 1.0F;
			}
			return Easing.SINE_IO.f(1.0F - (lamaSampai - now) / Math.max(1.0F, masuk));
		}

		void reset() {
			klip = 0;
			varian = 0;
			k = null;
			on = false;
			berubah = -1.0E9F;
			kLama = null;
			lamaSampai = -1.0E9F;
		}
	}

	final int id;
	int benih;
	float ageLalu = Float.NaN;
	/** Game time of the last evaluation (cleanup). */
	long dilihat;

	// traits (cached by packed value)
	int sosokPacked = Integer.MIN_VALUE;
	int gayaPacked = Integer.MIN_VALUE;
	Sosok sosok;
	Gaya gaya = Gaya.NETRAL;

	// layers 4 (once), 6 (loop), 7 (reactions and idle gestures), 8 (idle stance)
	final Jalur sekali = new Jalur(3.0F, 4.0F);
	final Jalur loop = new Jalur(6.0F, 6.0F);
	final Jalur reaksi = new Jalur(3.0F, 4.0F);
	final Jalur sikap = new Jalur(10.0F, 10.0F);
	/** Layer 5 (the disciple activity pose) as a weight only: it suppresses 6-8. */
	final Jalur aktivitas = new Jalur(6.0F, 6.0F);
	/** Layer 5's clip when the activity is mapped to a Gerak clip (DESIGN 39 activities 40-60, owners' remaps). */
	final Jalur aktKlip = new Jalur(6.0F, 6.0F);
	/** The clip a town NPC's old flags ask for ({@code NpcPoses.klip}, packed) and since when (local clip time). */
	int bendera;
	float benderaMulai;
	/** Cast/duel fade multiplier of layers 4-8 (0 while a cast or duel pose plays, 4 tick fade). */
	final Jalur bebas = new Jalur(4.0F, 4.0F);

	// channel smoothing (pattern of NpcPoses.Blend)
	final float[] val = new float[Klip.KANAL];
	final float[] w = new float[Klip.KANAL];
	boolean blendAwal = true;
	final float[] target = new float[Klip.KANAL];
	final float[] bobot = new float[Klip.KANAL];
	final float[] sampel = new float[Klip.KANAL];
	final float[] sampelLama = new float[Klip.KANAL];
	/** KURANG detail: the last sampled targets, reused every second frame. */
	final float[] cacheTarget = new float[Klip.KANAL];
	final float[] cacheBobot = new float[Klip.KANAL];
	int frameGenap;

	// the snapshot handed to PoseApplier (arrays and records made once)
	final float[] snapVal = new float[Klip.KANAL_POSE];
	final float[] snapW = new float[Klip.KANAL_POSE];
	final CastPoses.Snapshot snap = new CastPoses.Snapshot(Pose.NONE, snapVal, snapW, null, null, 1.0F, false);
	final CastPoses.Snapshot snapDua = new CastPoses.Snapshot(Pose.NONE, snapVal, snapW, null, null, 1.0F, true);

	// gaze
	float tatapYaw;
	float tatapPitch;
	float tatapTwist;
	int pemainLirik = -1;
	float pemainLirikSampai;
	final int[] pemainTerakhir = {-1, -1, -1, -1};
	final float[] pemainTerakhirWaktu = new float[4];
	float lirikCepatBerikut;
	float lirikCepatSampai;
	float lirikCepatArah;

	// face
	float kedipBerikut;
	float kedipMulai;
	boolean kedipGanda;
	float diamSejak = -1.0F;
	int wajahMataPeristiwa = -1;
	int wajahMulutPeristiwa = -1;

	// breath
	float lariSejak = -1.0F;
	float terengahSejak = -1.0E9F;

	// idle gestures
	float isengBerikut;
	@Nullable Klip isengKlip;

	// look key cache (no string building per frame)
	@Nullable String rupaMentah;
	int peranOrdinal = -1;
	int pangkatCache = Integer.MIN_VALUE;
	@Nullable String kunci;
	@Nullable Object garisKomponen;
	String garis = "";
	/** Prop changes from clip events (-1 = none), per slot (right, left, back, attached). */
	final int[] propPeristiwa = {-1, -1, -1, -1};
	/** The arm aim toward the look target (radians) for ARAH_SASARAN clips. */
	float bidikYaw;

	// reactions requested by GerakReaksi
	int reaksiKlip;
	float reaksiSampai;
	long petirTerakhir;

	// springs
	final float[] pegasX = new float[Pegas.RANTAI];
	final float[] pegasZ = new float[Pegas.RANTAI];
	final float[] pegasWX = new float[Pegas.RANTAI];
	final float[] pegasWZ = new float[Pegas.RANTAI];
	boolean pegasMulai;
	final float[] rok = new float[4];
	final float[] rokW = new float[4];
	final float[] rokTarget = new float[4];
	boolean rokMulai;
	final Pegas.Gerak gerak = new Pegas.Gerak();
	double xLalu;
	double yLalu;
	double zLalu;
	float bodyRotLalu;
	float kepalaYawLalu;
	float kepalaPitchLalu;
	float lenganKananLalu;
	float lenganKiriLalu;
	boolean posAwal = true;

	// walk
	float langkahSinLalu;
	/** The integrated step phase (walk position x rhythm) and last frame's raw walk position. */
	float fasa;
	float posLalu = Float.NaN;
	/** Run blend, turn, start/stop and landing of the shared locomotion polish. */
	final Langkah.Keadaan langkah = new Langkah.Keadaan();
	float spin;

	/** The data handed to the render state (reused). */
	final GerakData data = new GerakData();

	GerakKeadaan(int id) {
		this.id = id;
		this.benih = id * 0x45D9F3B;
		Arrays.fill(val, Float.NaN);
		this.isengBerikut = 200.0F + Math.floorMod(benih, 120);
		this.lirikCepatBerikut = 60.0F + Math.floorMod(benih >>> 3, 60);
	}

	/** Forgets everything (the entity id was reused by a new body). */
	void reset() {
		sosokPacked = Integer.MIN_VALUE;
		gayaPacked = Integer.MIN_VALUE;
		sekali.reset();
		loop.reset();
		reaksi.reset();
		sikap.reset();
		aktivitas.reset();
		aktKlip.reset();
		bebas.reset();
		bendera = 0;
		Arrays.fill(val, Float.NaN);
		Arrays.fill(w, 0.0F);
		blendAwal = true;
		pegasMulai = false;
		rokMulai = false;
		posAwal = true;
		spin = 0.0F;
		diamSejak = -1.0F;
		lariSejak = -1.0F;
		posLalu = Float.NaN;
		langkah.reset();
		isengKlip = null;
		reaksiKlip = 0;
		ageLalu = Float.NaN;
	}
}
