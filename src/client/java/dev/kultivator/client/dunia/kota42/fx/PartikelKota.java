package dev.kultivator.client.dunia.kota42.fx;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.Arrays;

/**
 * The little sprites of the living towns (DESIGN 42.12.6): kitchen smoke and steam, red paper and firecracker sparks, petals,
 * falling leaves, joss paper, ash and willow fluff, drawn as camera-facing quads of {@code textures/fx/kota42/partikel.png} (8 x 8
 * cells of 16 px, made by {@code tools/gen_kota_hidup_fx.py}). They are the mod's own tiny particle pool rather than vanilla
 * particle types, so Kota yang Hidup adds no registry entry and nothing here is ever sent over the network. Pool size follows the
 * 25 % / 100 % / 200 % factor of {@link Bingkai#partikel}; a particle has position, velocity, spin, size growth, a frame run of the
 * atlas, a colour, the world light of where it was born and a few behaviour flags (wind, flutter, glow). Client thread only.
 */
final class PartikelKota {

	/** First atlas cell (row * 8 + column) of each sprite run. */
	static final int ASAP = 0;
	static final int UAP = 8;
	static final int KERTAS = 12;
	static final int KELOPAK = 16;
	static final int DAUN = 20;
	static final int PERCIK = 24;
	static final int UANG = 28;
	static final int PUTIH = 32;
	static final int ABU = 34;
	static final int KAPAS = 36;
	static final int KILAU = 38;

	/** Drawn fully bright (sparks, glints); without it the sprite takes the light of where it was born. */
	static final int PANCAR = 1;
	/** The sprite's frames play over the particle's life (smoke grows, sparks shrink). */
	static final int ANIM = 2;
	/** Flutters side to side as it falls (paper, petals, leaves). */
	static final int KIBAR = 4;
	/** Takes the wind's drift. */
	static final int ANGIN = 8;

	private static final int KAPASITAS = 3200;

	private static float[] px = new float[256];
	private static float[] py = new float[256];
	private static float[] pz = new float[256];
	private static float[] vx = new float[256];
	private static float[] vy = new float[256];
	private static float[] vz = new float[256];
	private static float[] rot = new float[256];
	private static float[] spin = new float[256];
	private static float[] u0 = new float[256];
	private static float[] u1 = new float[256];
	private static float[] grav = new float[256];
	private static float[] seret = new float[256];
	private static int[] umur = new int[256];
	private static int[] hidup = new int[256];
	private static int[] sel = new int[256];
	private static int[] bingkai = new int[256];
	private static int[] warna = new int[256];
	private static int[] cahaya = new int[256];
	private static int[] bendera = new int[256];
	private static int n;

	/** The wind: a drift velocity in blocks per tick, turning slowly with the world day. */
	private static float anginX;
	private static float anginZ;

	/** Number of live particles. */
	static int jumlah() {
		return n;
	}

	static void hapus() {
		n = 0;
	}

	/** The wind of the day (client side hash of the world day, so every client sees the same direction). */
	static void angin(long hari, long waktu) {
		long h = hari * 0x9E3779B97F4A7C15L;
		h ^= h >>> 29;
		float arah = (h & 1023) / 1023.0F * Mth.TWO_PI + 0.15F * Mth.sin(waktu * 0.0007F);
		float kuat = 0.008F + ((h >>> 12) & 255) / 255.0F * 0.014F;
		anginX = Mth.cos(arah) * kuat;
		anginZ = Mth.sin(arah) * kuat;
	}

	static float anginX() {
		return anginX;
	}

	static float anginZ() {
		return anginZ;
	}

	/**
	 * Adds one particle; false when the pool is full for this tier.
	 *
	 * @param cell first atlas cell, {@code frames} the length of the run (the frame follows the age when {@link #ANIM})
	 * @param s0 size at birth, {@code s1} at death (half-width in blocks)
	 */
	static boolean tambah(float x, float y, float z, float dx, float dy, float dz, int nyawa, float s0, float s1, int cell, int frames, float putar, float laju,
			int argb, int flags, float gravitasi, float hambat) {
		int batas = Math.min(KAPASITAS, Math.round(1500.0F * Bingkai.partikel()));
		if (n >= batas) {
			return false;
		}
		if (n >= px.length) {
			int m = px.length * 2;
			px = Arrays.copyOf(px, m);
			py = Arrays.copyOf(py, m);
			pz = Arrays.copyOf(pz, m);
			vx = Arrays.copyOf(vx, m);
			vy = Arrays.copyOf(vy, m);
			vz = Arrays.copyOf(vz, m);
			rot = Arrays.copyOf(rot, m);
			spin = Arrays.copyOf(spin, m);
			u0 = Arrays.copyOf(u0, m);
			u1 = Arrays.copyOf(u1, m);
			grav = Arrays.copyOf(grav, m);
			seret = Arrays.copyOf(seret, m);
			umur = Arrays.copyOf(umur, m);
			hidup = Arrays.copyOf(hidup, m);
			sel = Arrays.copyOf(sel, m);
			bingkai = Arrays.copyOf(bingkai, m);
			warna = Arrays.copyOf(warna, m);
			cahaya = Arrays.copyOf(cahaya, m);
			bendera = Arrays.copyOf(bendera, m);
		}
		int i = n++;
		px[i] = x;
		py[i] = y;
		pz[i] = z;
		vx[i] = dx;
		vy[i] = dy;
		vz[i] = dz;
		rot[i] = putar;
		spin[i] = laju;
		u0[i] = s0;
		u1[i] = s1;
		grav[i] = gravitasi;
		seret[i] = hambat;
		umur[i] = 0;
		hidup[i] = Math.max(2, nyawa);
		sel[i] = cell;
		bingkai[i] = Math.max(1, frames);
		warna[i] = argb;
		cahaya[i] = (flags & PANCAR) != 0 ? Geo.TERANG : Bingkai.cahaya(x, y, z);
		bendera[i] = flags;
		return true;
	}

	/** One tick: move, age, forget the dead. */
	static void tik() {
		for (int i = 0; i < n; i++) {
			if (++umur[i] >= hidup[i]) {
				kill(i--);
				continue;
			}
			int f = bendera[i];
			vy[i] -= grav[i];
			float k = 1.0F - seret[i];
			vx[i] *= k;
			vy[i] *= k;
			vz[i] *= k;
			if ((f & ANGIN) != 0) {
				vx[i] += (anginX - vx[i]) * 0.04F;
				vz[i] += (anginZ - vz[i]) * 0.04F;
			}
			float fx = 0.0F;
			float fz = 0.0F;
			if ((f & KIBAR) != 0) {
				float t = umur[i] * 0.31F + i * 0.7F;
				fx = Mth.sin(t) * 0.011F;
				fz = Mth.cos(t * 0.83F) * 0.011F;
			}
			px[i] += vx[i] + fx;
			py[i] += vy[i];
			pz[i] += vz[i] + fz;
			rot[i] += spin[i];
		}
	}

	private static void kill(int i) {
		int last = --n;
		if (i == last) {
			return;
		}
		px[i] = px[last];
		py[i] = py[last];
		pz[i] = pz[last];
		vx[i] = vx[last];
		vy[i] = vy[last];
		vz[i] = vz[last];
		rot[i] = rot[last];
		spin[i] = spin[last];
		u0[i] = u0[last];
		u1[i] = u1[last];
		grav[i] = grav[last];
		seret[i] = seret[last];
		umur[i] = umur[last];
		hidup[i] = hidup[last];
		sel[i] = sel[last];
		bingkai[i] = bingkai[last];
		warna[i] = warna[last];
		cahaya[i] = cahaya[last];
		bendera[i] = bendera[last];
	}

	/** Draws every particle within {@code jarak} blocks of the camera into the two atlas sinks. */
	static void gambar(double jarak) {
		if (n == 0) {
			return;
		}
		Geo.Sink lit = Geo.tipe(Geo.PARTIKEL, Geo.Jenis.LENGKUNG);
		Geo.Sink glow = Geo.tipe(Geo.PARTIKEL, Geo.Jenis.PANCAR);
		double j2 = jarak * jarak;
		float sp = Bingkai.sebagian;
		var cam = Bingkai.kamera;
		var r = Bingkai.KANAN;
		var u = Bingkai.ATAS;
		for (int i = 0; i < n; i++) {
			float x = px[i] + vx[i] * sp;
			float y = py[i] + vy[i] * sp;
			float z = pz[i] + vz[i] * sp;
			float cx = (float) (x - cam.x);
			float cy = (float) (y - cam.y);
			float cz = (float) (z - cam.z);
			if (cx * cx + cy * cy + cz * cz > j2) {
				continue;
			}
			float t = (umur[i] + sp) / hidup[i];
			float half = u0[i] + (u1[i] - u0[i]) * t;
			float alpha = Math.min(1.0F, t * 12.0F) * Math.min(1.0F, (1.0F - t) * 3.0F);
			int base = warna[i];
			int a = Math.round((base >>> 24) * alpha);
			if (a <= 2) {
				continue;
			}
			int frame = (bendera[i] & ANIM) != 0 ? Math.min(bingkai[i] - 1, (int) (t * bingkai[i])) : (i % bingkai[i]);
			int cell = sel[i] + frame;
			float uu0 = (cell & 7) / 8.0F;
			float vv0 = (cell >> 3) / 8.0F;
			float uu1 = uu0 + 0.125F;
			float vv1 = vv0 + 0.125F;
			float c = Mth.cos(rot[i] + spin[i] * sp) * half;
			float s = Mth.sin(rot[i] + spin[i] * sp) * half;
			float rx = r.x * c + u.x * s;
			float ry = r.y * c + u.y * s;
			float rz = r.z * c + u.z * s;
			float ux = -r.x * s + u.x * c;
			float uy = -r.y * s + u.y * c;
			float uz = -r.z * s + u.z * c;
			int color = (a << 24) | (base & 0xFFFFFF);
			Geo.Sink sink = (bendera[i] & PANCAR) != 0 ? glow : lit;
			sink.quad(cx - rx + ux, cy - ry + uy, cz - rz + uz, cx - rx - ux, cy - ry - uy, cz - rz - uz, cx + rx - ux, cy + ry - uy, cz + rz - uz,
					cx + rx + ux, cy + ry + uy, cz + rz + uz, uu0, vv0, uu1, vv1, color, cahaya[i], false);
		}
	}

	// ============================================================================== emitters

	/** A puff of kitchen smoke: rises, grows, thins, leans with the wind. {@code tebal} 0.4-2 scales size and alpha. */
	static void asap(RandomSource rnd, float x, float y, float z, float tebal, int rgb) {
		int nyawa = 78 + rnd.nextInt(34);
		tambah(x + (rnd.nextFloat() - 0.5F) * 0.12F, y, z + (rnd.nextFloat() - 0.5F) * 0.12F, (rnd.nextFloat() - 0.5F) * 0.004F, 0.034F + rnd.nextFloat() * 0.014F,
				(rnd.nextFloat() - 0.5F) * 0.004F, nyawa, 0.13F * tebal, 0.55F * tebal + 0.35F, ASAP, 8, rnd.nextFloat() * Mth.TWO_PI, (rnd.nextFloat() - 0.5F) * 0.03F,
				Geo.argb(Mth.clamp(0.62F * tebal, 0.12F, 0.9F), rgb) , ANIM | ANGIN, -0.0004F, 0.012F);
	}

	/** A wisp of steam over a hot dish (rises faster than smoke, vanishes sooner). */
	static void uap(RandomSource rnd, float x, float y, float z) {
		tambah(x + (rnd.nextFloat() - 0.5F) * 0.18F, y, z + (rnd.nextFloat() - 0.5F) * 0.18F, 0.0F, 0.024F + rnd.nextFloat() * 0.01F, 0.0F, 34 + rnd.nextInt(14), 0.1F,
				0.28F, UAP + rnd.nextInt(4), 1, 0.0F, (rnd.nextFloat() - 0.5F) * 0.04F, Geo.argb(0.55F, 0xFFFFFF), ANGIN, -0.0002F, 0.02F);
	}

	/** A scrap of red paper from a firecracker or a thrown handful. */
	static void kertasMerah(RandomSource rnd, float x, float y, float z, float dx, float dy, float dz) {
		tambah(x, y, z, dx, dy, dz, 46 + rnd.nextInt(40), 0.06F + rnd.nextFloat() * 0.03F, 0.05F, KERTAS + rnd.nextInt(4), 1, rnd.nextFloat() * Mth.TWO_PI,
				(rnd.nextFloat() - 0.5F) * 0.5F, 0xFFFFFFFF, KIBAR | ANGIN, 0.011F, 0.045F);
	}

	/** A fleck of white paper (mourning). */
	static void kertasPutih(RandomSource rnd, float x, float y, float z, float dx, float dy, float dz) {
		tambah(x, y, z, dx, dy, dz, 60 + rnd.nextInt(40), 0.07F, 0.06F, PUTIH + rnd.nextInt(2), 1, rnd.nextFloat() * Mth.TWO_PI, (rnd.nextFloat() - 0.5F) * 0.4F,
				0xFFFFFFFF, KIBAR | ANGIN, 0.006F, 0.04F);
	}

	/** Joss paper scattered into the air (square, ingot, foil, strip), floats down. */
	static void uang(RandomSource rnd, float x, float y, float z, float dx, float dy, float dz) {
		tambah(x, y, z, dx, dy, dz, 70 + rnd.nextInt(50), 0.08F, 0.075F, UANG + rnd.nextInt(4), 1, rnd.nextFloat() * Mth.TWO_PI, (rnd.nextFloat() - 0.5F) * 0.45F,
				0xFFFFFFFF, KIBAR | ANGIN, 0.0065F, 0.05F);
	}

	/** A petal (peach, pink, cream, white by {@code jenis} 0-3), falls fluttering. */
	static void kelopak(RandomSource rnd, float x, float y, float z, int jenis, float dy) {
		tambah(x, y, z, (rnd.nextFloat() - 0.5F) * 0.02F, dy, (rnd.nextFloat() - 0.5F) * 0.02F, 90 + rnd.nextInt(60), 0.07F, 0.07F, KELOPAK + Mth.clamp(jenis, 0, 3), 1,
				rnd.nextFloat() * Mth.TWO_PI, (rnd.nextFloat() - 0.5F) * 0.3F, 0xFFFFFFFF, KIBAR | ANGIN, 0.0035F, 0.04F);
	}

	/** An autumn leaf (gold, orange, red, brown). */
	static void daun(RandomSource rnd, float x, float y, float z) {
		tambah(x, y, z, (rnd.nextFloat() - 0.5F) * 0.02F, -0.01F, (rnd.nextFloat() - 0.5F) * 0.02F, 110 + rnd.nextInt(70), 0.09F, 0.09F, DAUN + rnd.nextInt(4), 1,
				rnd.nextFloat() * Mth.TWO_PI, (rnd.nextFloat() - 0.5F) * 0.25F, 0xFFFFFFFF, KIBAR | ANGIN, 0.0028F, 0.045F);
	}

	/** A glowing spark that shrinks (frames 0-3). */
	static void percik(RandomSource rnd, float x, float y, float z, float dx, float dy, float dz, int nyawa, float ukuran) {
		tambah(x, y, z, dx, dy, dz, nyawa, ukuran, ukuran * 0.4F, PERCIK, 4, 0.0F, 0.0F, 0xFFFFFFFF, PANCAR | ANIM, 0.012F, 0.06F);
	}

	/** A flake of ash. */
	static void abu(RandomSource rnd, float x, float y, float z) {
		tambah(x, y, z, (rnd.nextFloat() - 0.5F) * 0.02F, 0.012F, (rnd.nextFloat() - 0.5F) * 0.02F, 70 + rnd.nextInt(50), 0.05F, 0.04F, ABU + rnd.nextInt(2), 1,
				rnd.nextFloat() * Mth.TWO_PI, (rnd.nextFloat() - 0.5F) * 0.2F, Geo.argb(0.8F, 0xFFFFFF), KIBAR | ANGIN, -0.0003F, 0.03F);
	}

	/** A tuft of willow fluff drifting by. */
	static void kapas(RandomSource rnd, float x, float y, float z) {
		tambah(x, y, z, 0.0F, 0.0F, 0.0F, 140 + rnd.nextInt(80), 0.06F, 0.06F, KAPAS + rnd.nextInt(2), 1, rnd.nextFloat() * Mth.TWO_PI, (rnd.nextFloat() - 0.5F) * 0.1F,
				Geo.argb(0.85F, 0xFFFFFF), KIBAR | ANGIN, 0.0004F, 0.02F);
	}

	/** A glint that flashes and goes. */
	static void kilau(RandomSource rnd, float x, float y, float z, float ukuran, int nyawa, int rgb) {
		tambah(x, y, z, 0.0F, 0.004F, 0.0F, nyawa, ukuran, ukuran * 0.6F, KILAU + rnd.nextInt(2), 1, rnd.nextFloat() * Mth.TWO_PI, (rnd.nextFloat() - 0.5F) * 0.2F,
				Geo.argb(1.0F, rgb), PANCAR, 0.0F, 0.02F);
	}

	private PartikelKota() {
	}
}
