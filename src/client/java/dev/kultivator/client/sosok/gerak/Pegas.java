package dev.kultivator.client.sosok.gerak;

import net.minecraft.util.Mth;

/**
 * Secondary motion (DESIGN 40.5.8): damped springs that make hair, ponytails, braids, ribbons, hairpin beads, beards,
 * wide sleeves, outer robes, shawls and pendants trail behind the body. Each chain keeps an angle and an angular speed
 * on two axes (x pitch, z roll) per body; every frame the target is its rest angle plus gravity compensation (it hangs
 * straight in the world however the parent part turns) plus drag from the body's own motion (forward, sideways and
 * vertical speed in the body frame, the body's turning rate, the arm's swing speed). Integration is semi-implicit
 * Euler in half-tick sub-steps, dt clamped to 3 ticks: {@code w += (k (target - a) - 2 z sqrt(k) w) dt; a += w dt},
 * then clamped. The skirt panels follow the legs kinematically with a light spring so no leg pokes through.
 * <p>
 * Only bodies at full detail within the spring budget integrate; others get the rest targets (still). Nothing here
 * touches the world: inputs are the entity's positions and the pose already decided this frame.
 */
public final class Pegas {

	/** The chains of 40.5.8 (40K applies each to its named parts). */
	public enum Rantai {
		/** rambut_atas (stiffness 0.6), rambut_bawah(_panjang). */
		RAMBUT(0.10F, 0.55F, -10, 65, -20, 20),
		/** ekor_kuda_1. */
		EKOR_1(0.14F, 0.45F, 0, 80, -35, 35),
		/** ekor_kuda_2 (follows segment 1 with a delay). */
		EKOR_2(0.10F, 0.40F, -30, 60, -35, 35),
		/** kepang_1. */
		KEPANG_1(0.16F, 0.50F, -30, 50, -25, 25),
		/** kepang_2. */
		KEPANG_2(0.12F, 0.50F, -30, 50, -25, 25),
		/** pita_rambut, pita_sabuk, ikat_dahi_ekor. */
		PITA(0.07F, 0.30F, -10, 85, -30, 30),
		/** manik_buyao x3 (phases 0.15 apart, applied by 40K). */
		BUYAO(0.20F, 0.25F, -30, 30, -30, 30),
		/** jenggot_*. */
		JENGGOT(0.25F, 0.60F, -40, 30, -15, 15),
		/** kumis_naga, alis_panjang. */
		KUMIS(0.18F, 0.40F, -30, 30, -30, 30),
		/** poni_samping. */
		PONI(0.20F, 0.50F, -25, 25, -25, 25),
		/** lengan_lebar_kantong of the right arm (hangs down in the world). */
		LENGAN_KANAN(0.12F, 0.50F, -160, 20, -40, 40),
		/** lengan_lebar_kantong of the left arm. */
		LENGAN_KIRI(0.12F, 0.50F, -160, 20, -40, 40),
		/** jubah_luar. */
		JUBAH(0.08F, 0.60F, 0, 75, -15, 15),
		/** selendang_1/2, selendang_punggung (float for immortals). */
		SELENDANG(0.05F, 0.25F, -70, 70, -70, 70),
		/** liontin_giok, lencana_giok, kantong, labu, rumbai_iblis. */
		RUMBAI(0.20F, 0.35F, -35, 35, -35, 35),
		/** Skirt panels' smoothing spring (front and back pitch use x, sides use z). */
		ROK(0.30F, 0.70F, -90, 90, -60, 60);

		final float k;
		final float zeta;
		final float minX;
		final float maxX;
		final float minZ;
		final float maxZ;

		Rantai(float k, float zeta, float minX, float maxX, float minZ, float maxZ) {
			this.k = k;
			this.zeta = zeta;
			this.minX = minX * Mth.DEG_TO_RAD;
			this.maxX = maxX * Mth.DEG_TO_RAD;
			this.minZ = minZ * Mth.DEG_TO_RAD;
			this.maxZ = maxZ * Mth.DEG_TO_RAD;
		}
	}

	/** Number of chains (array sizes in GerakData / GerakKeadaan). */
	public static final int RANTAI = Rantai.values().length;

	private static final Rantai[] SEMUA = Rantai.values();
	private static final float D = Mth.DEG_TO_RAD;

	/** Per-frame motion of a body (filled by the controller). */
	static final class Gerak {
		/** Body-frame speed per tick: forward, sideways (+ right), up. */
		float vF;
		float vS;
		float vY;
		/** Body turning rate, degrees per tick. */
		float omegaBadan;
		/** Head turning rates, radians per tick. */
		float omegaKepalaYaw;
		float omegaKepalaPitch;
		/** Final lean and head pitch (radians). */
		float lean;
		float kepalaX;
		/** Arm pitch (radians) and their speeds (radians per tick). */
		float lenganKananX;
		float lenganKiriX;
		float lenganKananZ;
		float lenganKiriZ;
		float omegaLenganKanan;
		float omegaLenganKiri;
		/** Hip sway (radians) and step phase. */
		float bz;
		float fasaLangkah;
		/** Legs (radians) for the skirt panels. */
		float kakiKananX;
		float kakiKiriX;
		float kakiKananZ;
		float kakiKiriZ;
		boolean bersila;
		boolean dudukKursi;
		boolean lari;
		/** Immortal shawls float (no gravity, a slow sway). */
		boolean melayang;
		float age;
	}

	/** The rest/drag target of a chain on x (pitch) for this frame's motion. */
	private static float targetX(Rantai r, Gerak g, float age) {
		float vF = g.vF;
		return switch (r) {
			case RAMBUT -> -g.lean * 0.9F + 50.0F * D * vF * 4.0F + 25.0F * D * Math.max(0.0F, g.vY) * 4.0F;
			case EKOR_1 -> 25.0F * D - g.kepalaX * 0.7F + 40.0F * D * vF * 4.0F;
			case EKOR_2, KEPANG_2 -> 0.0F;
			case KEPANG_1 -> -g.lean * 0.9F + 0.7F * 40.0F * D * vF * 4.0F;
			case PITA -> -g.lean + 60.0F * D * vF * 4.0F
					+ 8.0F * D * Mth.sin(0.9F * age + 1.3F) * Math.min(1.0F, Math.abs(vF) * 24.0F);
			case BUYAO -> g.omegaKepalaPitch * 1.5F * 10.0F;
			case JENGGOT -> -g.kepalaX + 15.0F * D * vF * 4.0F;
			case KUMIS, PONI -> -g.kepalaX * 0.8F + 20.0F * D * vF * 4.0F;
			case LENGAN_KANAN -> -g.lenganKananX * 0.85F - g.omegaLenganKanan * 0.8F * 4.0F;
			case LENGAN_KIRI -> -g.lenganKiriX * 0.85F - g.omegaLenganKiri * 0.8F * 4.0F;
			case JUBAH -> 6.0F * D + 70.0F * D * Math.max(0.0F, vF) * 4.0F - g.lean * 0.5F;
			case SELENDANG -> g.melayang ? 6.0F * D * Mth.sin(age * Mth.TWO_PI / 70.0F) + 10.0F * D
					: 20.0F * D + 45.0F * D * vF * 4.0F - g.lean * 0.6F;
			case RUMBAI -> -g.lean * 0.8F + 6.0F * D * Mth.sin(g.fasaLangkah) * Math.min(1.0F, Math.abs(vF) * 12.0F);
			case ROK -> 0.0F;
		};
	}

	/** The target on z (roll). */
	private static float targetZ(Rantai r, Gerak g) {
		float vS = g.vS;
		float turn = g.omegaBadan;
		return switch (r) {
			case RAMBUT -> -20.0F * D * vS * 4.0F + Mth.clamp(turn, -8.0F, 8.0F) * D;
			case EKOR_1 -> -30.0F * D * vS * 4.0F - 1.2F * turn * D;
			case KEPANG_1 -> 0.7F * (-30.0F * D * vS * 4.0F - 1.2F * turn * D);
			case PITA -> -25.0F * D * vS * 4.0F - turn * D;
			case BUYAO -> g.omegaKepalaYaw * 1.5F * 10.0F;
			case LENGAN_KANAN -> -g.lenganKananZ * 0.6F;
			case LENGAN_KIRI -> -g.lenganKiriZ * 0.6F;
			case JUBAH -> -15.0F * D * turn / 10.0F;
			case SELENDANG -> -25.0F * D * vS * 4.0F;
			case RUMBAI -> -g.bz;
			default -> 0.0F;
		};
	}

	/**
	 * Advances every chain of one body by {@code dt} ticks (clamped to 3) and writes the angles into {@code d}.
	 * {@code aktif} false sets every chain to its target (no motion: beyond the budget or LOD).
	 */
	static void langkah(GerakKeadaan k, GerakData d, Gerak g, float dt, boolean aktif) {
		float[] ax = k.pegasX;
		float[] az = k.pegasZ;
		float[] wx = k.pegasWX;
		float[] wz = k.pegasWZ;
		float sisa = Math.min(3.0F, Math.max(0.0F, dt));
		for (Rantai r : SEMUA) {
			if (r == Rantai.ROK) {
				continue;
			}
			int i = r.ordinal();
			float tx = targetX(r, g, g.age);
			float tz = targetZ(r, g);
			if (r == Rantai.EKOR_2) {
				// segment 2 takes 0.6 of segment 1's motion, delayed by its own spring
				tx = (ax[Rantai.EKOR_1.ordinal()] - 25.0F * D) * 0.6F;
				tz = az[Rantai.EKOR_1.ordinal()] * 0.6F;
			} else if (r == Rantai.KEPANG_2) {
				tx = ax[Rantai.KEPANG_1.ordinal()] * 0.6F;
				tz = az[Rantai.KEPANG_1.ordinal()] * 0.6F;
			}
			if (!aktif || !k.pegasMulai) {
				ax[i] = Mth.clamp(tx, r.minX, r.maxX);
				az[i] = Mth.clamp(tz, r.minZ, r.maxZ);
				wx[i] = 0.0F;
				wz[i] = 0.0F;
				continue;
			}
			float damp = 2.0F * r.zeta * (float) Math.sqrt(r.k);
			float t = sisa;
			while (t > 0.0F) {
				float h = Math.min(0.5F, t);
				wx[i] += (r.k * (tx - ax[i]) - damp * wx[i]) * h;
				ax[i] += wx[i] * h;
				wz[i] += (r.k * (tz - az[i]) - damp * wz[i]) * h;
				az[i] += wz[i] * h;
				t -= h;
			}
			if (ax[i] < r.minX || ax[i] > r.maxX) {
				ax[i] = Mth.clamp(ax[i], r.minX, r.maxX);
				wx[i] *= -0.3F;
			}
			if (az[i] < r.minZ || az[i] > r.maxZ) {
				az[i] = Mth.clamp(az[i], r.minZ, r.maxZ);
				wz[i] *= -0.3F;
			}
		}
		k.pegasMulai = true;
		System.arraycopy(ax, 0, d.pegasX, 0, RANTAI);
		System.arraycopy(az, 0, d.pegasZ, 0, RANTAI);
		d.pegasAktif = aktif;
		rok(k, d, g, sisa, aktif);
	}

	/** The skirt panels: kinematic from the legs, smoothed by a stiff spring (40.5.8 "Panel rok"). */
	private static void rok(GerakKeadaan k, GerakData d, Gerak g, float dt, boolean aktif) {
		float depan;
		float belakang;
		float kanan;
		float kiri;
		float skalaDepan = 1.0F;
		float skalaBelakang = 1.0F;
		if (g.bersila) {
			// spread on the floor around the crossed legs
			depan = -1.35F;
			belakang = 0.0F;
			skalaDepan = 0.45F;
			skalaBelakang = 0.35F;
			kanan = 0.9F;
			kiri = -0.9F;
		} else if (g.dudukKursi) {
			depan = -1.5F;
			belakang = 0.0F;
			skalaBelakang = 0.5F;
			kanan = 0.2F;
			kiri = -0.2F;
		} else {
			float maju = Math.min(0.0F, Math.min(g.kakiKananX, g.kakiKiriX));
			float mundur = Math.max(0.0F, Math.max(g.kakiKananX, g.kakiKiriX));
			depan = maju * 0.85F - g.lean * 0.9F;
			belakang = mundur * 0.85F - g.lean * 0.9F;
			kanan = Math.max(0.0F, g.kakiKananZ) * 0.9F;
			kiri = -Math.max(0.0F, -g.kakiKiriZ) * 0.9F;
			if (g.lari) {
				depan -= 10.0F * D;
				belakang += 10.0F * D;
			}
		}
		Rantai r = Rantai.ROK;
		float[] st = k.rok;
		float[] w = k.rokW;
		float[] tgt = k.rokTarget;
		tgt[0] = depan;
		tgt[1] = belakang;
		tgt[2] = kanan;
		tgt[3] = kiri;
		if (!aktif || !k.rokMulai) {
			System.arraycopy(tgt, 0, st, 0, 4);
			java.util.Arrays.fill(w, 0.0F);
		} else {
			float damp = 2.0F * r.zeta * (float) Math.sqrt(r.k);
			for (int i = 0; i < 4; i++) {
				float t = dt;
				while (t > 0.0F) {
					float h = Math.min(0.5F, t);
					w[i] += (r.k * (tgt[i] - st[i]) - damp * w[i]) * h;
					st[i] += w[i] * h;
					t -= h;
				}
			}
			if (!g.bersila && !g.dudukKursi) {
				// the spring may lead the legs but never lag behind them: a fast stride cannot poke through the hem
				if (st[0] > tgt[0] + 0.04F) {
					st[0] = tgt[0] + 0.04F;
					w[0] = Math.min(0.0F, w[0]);
				}
				if (st[1] < tgt[1] - 0.04F) {
					st[1] = tgt[1] - 0.04F;
					w[1] = Math.max(0.0F, w[1]);
				}
			}
		}
		k.rokMulai = true;
		d.rokDepanX = st[0];
		d.rokBelakangX = st[1];
		d.rokKananZ = st[2];
		d.rokKiriZ = st[3];
		d.rokDepanSkalaY = skalaDepan;
		d.rokBelakangSkalaY = skalaBelakang;
	}

	private Pegas() {
	}
}
