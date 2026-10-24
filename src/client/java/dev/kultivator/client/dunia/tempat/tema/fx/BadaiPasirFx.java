package dev.kultivator.client.dunia.tempat.tema.fx;

import dev.kultivator.client.epic.EpicBudget;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Badai Pasir of a Kota Oasis, client side (DESIGN 38.T.4.6, 38.T.9.4), from {@code TemaNet.BADAI}
 * {@code {mulai, x, z, r, tick, panjang, kuat}} where {@code tick} is the start of the storm proper:
 * <ul>
 * <li><b>Omen</b> ({@code tick − 1200 .. tick}): the haboob front, a brown wall 256 × 64 blocks ({@code dinding_pasir.png},
 * two layers with their own billow scroll, slightly curved toward the town), rolls in from the wind's side at
 * 0.3 blocks per tick and reaches the town centre when the storm begins. Past the render distance it is drawn nearer
 * and smaller along the same line of sight (same angle), so a weak laptop with 6 chunks still sees it on the horizon.
 * The wind starts whistling (a thin, high loop).</li>
 * <li><b>Storm</b> ({@code tick .. tick + panjang}): the fake 16-block "fog" #C8A060 is three or four shells of sand
 * haze ({@code pasir.png}) at 8-24 blocks around the camera, scrolling with the wind, and a low sand ceiling; sand
 * streaks ({@code gores}) and dust clumps ({@code debu}) fly past horizontally, 400 per second on Normal and 900 on
 * Maximal through {@code EpicBudget.takeQuads}; the HUD ({@link TemaHud}) lays the tint and gusting grain lines over it
 * (the only thing drawn on Minimal). The wind roars low; gusts hiss.</li>
 * </ul>
 * Under a roof (no sky above the camera) the near shell and the streaks stop and the tint thins, so a house is a
 * shelter on screen too. Nothing of the world changes (no fog, weather, time or block). {@link #goyang} gives the ±20°
 * needle jitter for compasses and quest pointers.
 */
public final class BadaiPasirFx {

	/** Omen length before the storm proper (ticks). */
	static final int PERTANDA = 1200;
	/** The wall's speed, blocks per tick. */
	static final float LAJU_DINDING = 0.3F;
	static final float LEBAR_DINDING = 256.0F;
	static final float TINGGI_DINDING = 64.0F;
	static final int WARNA = 0xC8A060;
	/** Sand particles per second per tier (Minimal, Normal, Maximal). */
	static final int[] PARTIKEL = {0, 400, 900};
	private static final int MAKS_HIDUP = 900;
	private static final float[] RADII = {8.0F, 16.0F, 24.0F};
	private static final float[] RADII_MAKS = {6.0F, 10.0F, 16.0F, 24.0F};

	static final class Badai {
		final long situs;
		final double x;
		final double z;
		final int r;
		final long tick;
		final int panjang;
		final float kuat;
		/** Unit vector from the centre toward the side the wall comes from. */
		final float ax;
		final float az;
		long berakhir = Long.MAX_VALUE;

		Badai(long situs, CompoundTag d) {
			this.situs = situs;
			this.x = d.getIntOr("x", 0) + 0.5D;
			this.z = d.getIntOr("z", 0) + 0.5D;
			this.r = Math.max(32, d.getIntOr("r", 600));
			this.tick = d.getLongOr("tick", 0L);
			this.panjang = Math.max(200, d.getIntOr("panjang", 2400));
			this.kuat = Mth.clamp(d.getFloatOr("kuat", 1.0F), 0.0F, 1.0F);
			float a = Bingkai.hash(situs ^ tick, 17) * Mth.TWO_PI;
			this.ax = Mth.cos(a);
			this.az = Mth.sin(a);
		}

		boolean habis(long now) {
			return now > tick + panjang + 220L || now > berakhir + 120L;
		}

		/** 0..1 strength of the storm proper at {@code now} (fades 160 ticks in and 200 out, or 120 after an early end). */
		float badai(double now) {
			float k = Bingkai.selubung((float) (now - tick), 0.0F, panjang, 160.0F, 200.0F) * kuat;
			if (now > berakhir) {
				k *= 1.0F - Bingkai.halus((float) (now - berakhir) / 120.0F);
			}
			return k;
		}

		/** 0..1 visibility of the omen wall: fades in over 200 ticks, lingers 200 ticks into the storm as it passes. */
		float pertanda(double now) {
			float k = Bingkai.selubung((float) (now - (tick - PERTANDA)), 0.0F, PERTANDA + 200.0F, 200.0F, 260.0F) * kuat;
			if (now > berakhir) {
				k *= 1.0F - Bingkai.halus((float) (now - berakhir) / 60.0F);
			}
			return k;
		}

		/** 1 inside the storm radius, falling to 0 over 48 blocks outside it. */
		float di(double cx, double cz) {
			double d = Math.sqrt(Mth.square(cx - x) + Mth.square(cz - z));
			return 1.0F - Bingkai.halus((float) ((d - r) / 48.0D));
		}
	}

	private static final Map<Long, Badai> BADAI = new HashMap<>();
	private static Badai dekat;
	private static float kuatKamera;
	private static float kuatKameraLalu;
	private static boolean bawahAtap;
	private static TemaSuara.Lingkup angin;
	private static long hembusBerikut;

	// streak pool (world positions; previous tick for interpolation)
	private static final double[] PX = new double[MAKS_HIDUP];
	private static final double[] PY = new double[MAKS_HIDUP];
	private static final double[] PZ = new double[MAKS_HIDUP];
	private static final double[] OX = new double[MAKS_HIDUP];
	private static final double[] OY = new double[MAKS_HIDUP];
	private static final double[] OZ = new double[MAKS_HIDUP];
	private static final float[] VX = new float[MAKS_HIDUP];
	private static final float[] VY = new float[MAKS_HIDUP];
	private static final float[] VZ = new float[MAKS_HIDUP];
	private static final int[] UMUR = new int[MAKS_HIDUP];
	private static final int[] HIDUP = new int[MAKS_HIDUP];
	private static final float[] UKURAN = new float[MAKS_HIDUP];
	private static final boolean[] GUMPAL = new boolean[MAKS_HIDUP];
	private static int jumlah;
	private static float sisaSpawn;
	private static final Random RNG = new Random(380931L);
	private static final BlockPos.MutableBlockPos CEK = new BlockPos.MutableBlockPos();
	private static final Vector3f P0 = new Vector3f();
	private static final Vector3f P1 = new Vector3f();
	private static final Vector3f P2 = new Vector3f();
	private static final Vector3f P3 = new Vector3f();

	private BadaiPasirFx() {
	}

	/** {@code TemaNet.BADAI} for site {@code situs}. */
	static void terima(long situs, CompoundTag data) {
		long now = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
		if (data.getBooleanOr("mulai", false)) {
			BADAI.put(situs, new Badai(situs, data));
			TemaJalur.ingatPusat(situs, data);
		} else {
			Badai b = BADAI.get(situs);
			if (b != null) {
				b.berakhir = Math.min(b.berakhir, now);
			}
		}
	}

	/** A preview storm on the client ({@code /temafx badai}): the omen at once, {@code pertanda} ticks before the storm. */
	static void uji(double x, double z, long now, int pertanda) {
		CompoundTag d = new CompoundTag();
		d.putBoolean("mulai", true);
		d.putInt("x", Mth.floor(x));
		d.putInt("z", Mth.floor(z));
		d.putInt("r", 600);
		d.putLong("tick", now + pertanda);
		d.putInt("panjang", 1200);
		d.putFloat("kuat", 1.0F);
		BADAI.put(-1L, new Badai(-1L, d));
	}

	static boolean aktif() {
		return !BADAI.isEmpty();
	}

	static void bersihkan() {
		BADAI.clear();
		dekat = null;
		jumlah = 0;
		kuatKamera = 0.0F;
		kuatKameraLalu = 0.0F;
		TemaSuara.lepas(angin);
		angin = null;
	}

	/** 0..1 storm strength at the camera (interpolated), for the HUD tint. */
	public static float kuat(float partial) {
		return Mth.lerp(partial, kuatKameraLalu, kuatKamera);
	}

	/** True while the camera has no sky above it (the HUD tint thins). */
	static boolean bawahAtap() {
		return bawahAtap;
	}

	/**
	 * How much the wind blows toward the camera's right (−1..1) for a camera yaw given as (cos, sin): the screen sense of
	 * the HUD grain lines.
	 */
	static float anginX(float cos, float sin) {
		Badai b = dekat;
		return b == null ? 0.0F : b.ax * cos + b.az * sin;
	}

	/** 0..1 visibility of the omen wall for the nearest storm (HUD caption "badai datang"). */
	static float pertandaSekarang() {
		Minecraft minecraft = Minecraft.getInstance();
		if (dekat == null || minecraft.level == null) {
			return 0.0F;
		}
		return dekat.pertanda(minecraft.level.getGameTime());
	}

	/**
	 * Needle jitter in degrees (±20 at full storm) for compasses and quest pointers of other modules; 0 without a storm.
	 */
	public static float goyang(float partial) {
		float k = kuat(partial);
		if (k <= 0.01F) {
			return 0.0F;
		}
		Minecraft minecraft = Minecraft.getInstance();
		float t = minecraft.level == null ? 0.0F : (float) (minecraft.level.getGameTime() % 24000L) + partial;
		float wob = Mth.sin(t * 0.37F) * 0.6F + Mth.sin(t * 1.13F + 1.7F) * 0.3F + Mth.sin(t * 2.9F) * 0.1F;
		return wob * 20.0F * k;
	}

	// ================================================================ tick

	static void tick(Minecraft minecraft, long now) {
		kuatKameraLalu = kuatKamera;
		if (BADAI.isEmpty() || minecraft.player == null || minecraft.level == null) {
			kuatKamera = 0.0F;
			jumlah = 0;
			angin = TemaSuara.jaga(angin, SoundEvents.ELYTRA_FLYING, 0.0F, 1.0F);
			return;
		}
		BADAI.values().removeIf(b -> b.habis(now));
		double cx = minecraft.player.getX();
		double cz = minecraft.player.getZ();
		dekat = null;
		double best = Double.MAX_VALUE;
		for (Badai b : BADAI.values()) {
			double d = Mth.square(b.x - cx) + Mth.square(b.z - cz);
			if (d < best) {
				best = d;
				dekat = b;
			}
		}
		if (dekat == null) {
			kuatKamera = 0.0F;
			jumlah = 0;
			TemaSuara.lepas(angin);
			return;
		}
		Badai b = dekat;
		float di = b.di(cx, cz);
		float storm = b.badai(now) * di;
		CEK.set(minecraft.player.getX(), minecraft.player.getEyeY(), minecraft.player.getZ());
		bawahAtap = !minecraft.level.canSeeSky(CEK);
		kuatKamera = storm;
		// the wind: whistling thin while the wall comes, roaring low in the storm
		float omen = b.pertanda(now) * (1.0F - Bingkai.halus((float) ((Math.sqrt(best) - b.r - 64.0D) / 96.0D)));
		float vol = Math.max(omen * 0.22F, storm * (bawahAtap ? 0.25F : 0.55F));
		float pitch = storm > omen ? 0.62F + 0.1F * Mth.sin(now * 0.05F) : 1.35F + 0.15F * Mth.sin(now * 0.11F);
		angin = TemaSuara.jaga(angin, SoundEvents.ELYTRA_FLYING, vol, pitch);
		if (storm > 0.3F && !bawahAtap && now >= hembusBerikut) {
			hembusBerikut = now + 40L + RNG.nextInt(50);
			double a = RNG.nextDouble() * Math.PI * 2.0D;
			TemaSuara.sekali(SoundEvents.SAND_IDLE, 0.35F * storm, 0.7F + RNG.nextFloat() * 0.3F, cx + Math.cos(a) * 6.0D,
					minecraft.player.getEyeY(), cz + Math.sin(a) * 6.0D);
		}
		tickPartikel(minecraft, b, storm, now);
	}

	private static void tickPartikel(Minecraft minecraft, Badai b, float storm, long now) {
		// move and age
		for (int i = 0; i < jumlah; i++) {
			OX[i] = PX[i];
			OY[i] = PY[i];
			OZ[i] = PZ[i];
			PX[i] += VX[i];
			PY[i] += VY[i];
			PZ[i] += VZ[i];
			VY[i] += (RNG.nextFloat() - 0.5F) * 0.02F;
			UMUR[i]++;
		}
		for (int i = jumlah - 1; i >= 0; i--) {
			if (UMUR[i] >= HIDUP[i]) {
				buang(i);
			}
		}
		int tier = Math.max(0, Math.min(2, EpicBudget.tier()));
		int perDetik = PARTIKEL[tier];
		if (perDetik <= 0 || storm <= 0.02F || bawahAtap) {
			return;
		}
		sisaSpawn += perDetik / 20.0F * storm;
		double ex = minecraft.player.getX();
		double ey = minecraft.player.getEyeY();
		double ez = minecraft.player.getZ();
		// the wind blows from the wall's side toward the town: -a
		float wx = -b.ax;
		float wz = -b.az;
		while (sisaSpawn >= 1.0F && jumlah < MAKS_HIDUP) {
			sisaSpawn -= 1.0F;
			float speed = 0.9F + RNG.nextFloat() * 0.8F;
			// born upwind so they cross the view: a disc of 3..18 blocks shifted 8 blocks into the wind
			float ang = RNG.nextFloat() * Mth.TWO_PI;
			float rad = 3.0F + RNG.nextFloat() * 15.0F;
			double x = ex + Mth.cos(ang) * rad - wx * 8.0F;
			double z = ez + Mth.sin(ang) * rad - wz * 8.0F;
			double y = ey - 2.2D + RNG.nextFloat() * 7.0F;
			CEK.set(x, y, z);
			if (!minecraft.level.canSeeSky(CEK)) {
				continue;
			}
			int i = jumlah++;
			PX[i] = OX[i] = x;
			PY[i] = OY[i] = y;
			PZ[i] = OZ[i] = z;
			float side = (RNG.nextFloat() - 0.5F) * 0.25F;
			VX[i] = wx * speed - wz * side;
			VZ[i] = wz * speed + wx * side;
			VY[i] = (RNG.nextFloat() - 0.55F) * 0.06F;
			UMUR[i] = 0;
			GUMPAL[i] = RNG.nextFloat() < 0.18F;
			HIDUP[i] = GUMPAL[i] ? 26 + RNG.nextInt(14) : 14 + RNG.nextInt(10);
			UKURAN[i] = GUMPAL[i] ? 0.9F + RNG.nextFloat() * 1.6F : 0.07F + RNG.nextFloat() * 0.12F;
			if (GUMPAL[i]) {
				VX[i] *= 0.55F;
				VZ[i] *= 0.55F;
			}
		}
		if (sisaSpawn > 4.0F) {
			sisaSpawn = 4.0F;
		}
	}

	private static void buang(int i) {
		int last = --jumlah;
		if (i == last) {
			return;
		}
		PX[i] = PX[last];
		PY[i] = PY[last];
		PZ[i] = PZ[last];
		OX[i] = OX[last];
		OY[i] = OY[last];
		OZ[i] = OZ[last];
		VX[i] = VX[last];
		VY[i] = VY[last];
		VZ[i] = VZ[last];
		UMUR[i] = UMUR[last];
		HIDUP[i] = HIDUP[last];
		UKURAN[i] = UKURAN[last];
		GUMPAL[i] = GUMPAL[last];
	}

	// ================================================================ drawing

	static void gambar() {
		Badai b = dekat;
		if (b == null || Bingkai.level == null) {
			return;
		}
		double now = Bingkai.sekarang();
		float omen = b.pertanda(now);
		if (omen > 0.01F) {
			dinding(b, now, omen);
		}
		float storm = b.badai(now) * b.di(Bingkai.camX, Bingkai.camZ);
		if (storm <= 0.01F || Bingkai.tier <= 0) {
			return;
		}
		cangkang(b, storm);
		partikel(storm);
	}

	/** The haboob wall (see the class note). */
	private static void dinding(Badai b, double now, float alpha) {
		float jarakPusat = LAJU_DINDING * (float) (b.tick - now);
		double wx = b.x + b.ax * jarakPusat;
		double wz = b.z + b.az * jarakPusat;
		double vx = wx - Bingkai.camX;
		double vz = wz - Bingkai.camZ;
		double dist = Math.sqrt(vx * vx + vz * vz);
		if (dist < 24.0D) {
			return; // it is upon us: the shells take over
		}
		Minecraft minecraft = Minecraft.getInstance();
		double far = Math.max(64.0D, minecraft.options.getEffectiveRenderDistance() * 16.0D * 0.82D);
		float s = (float) Math.min(1.0D, far / dist);
		int ground = Bingkai.level.getHeight(Heightmap.Types.WORLD_SURFACE, Mth.floor(Bingkai.camX), Mth.floor(Bingkai.camZ));
		float baseRel = (float) (Math.min(ground, Bingkai.camY - 1.6D) - 6.0D - Bingkai.camY);
		float cx = (float) vx * s;
		float cz = (float) vz * s;
		float by = baseRel * s;
		// the wall's face: across the wind (perpendicular to a), curved a little toward the town
		float rx = -b.az;
		float rz = b.ax;
		int light = LightCoordsUtil.FULL_BRIGHT;
		float terang = Bingkai.terang;
		int depan = Bingkai.redup(0xFFFFFF, 0.35F + 0.65F * terang);
		int belakang = Bingkai.redup(0xE0C8A8, 0.3F + 0.6F * terang);
		int lapis = Bingkai.tier == 0 ? 1 : 2;
		int seg = Bingkai.tier == 0 ? 1 : Bingkai.tier == 1 ? 4 : 6;
		for (int layer = lapis - 1; layer >= 0; layer--) {
			float back = layer * 18.0F * s;
			float w = LEBAR_DINDING * s * (layer == 1 ? 1.25F : 1.0F);
			float h = TINGGI_DINDING * s * (layer == 1 ? 1.3F : 1.0F);
			float scroll = Bingkai.t * (layer == 1 ? 0.00035F : 0.0006F) % 1.0F;
			float lift = -(Bingkai.t * (layer == 1 ? 0.0004F : 0.0007F) % 1.0F);
			int col = Bingkai.argb(alpha * (layer == 1 ? 0.7F : 0.92F), layer == 1 ? belakang : depan);
			int colBawah = Bingkai.argb(alpha * (layer == 1 ? 0.8F : 1.0F), layer == 1 ? belakang : depan);
			if (!TemaFx.muat(seg)) {
				return;
			}
			for (int i = 0; i < seg; i++) {
				float k0 = i / (float) seg - 0.5F;
				float k1 = (i + 1) / (float) seg - 0.5F;
				// bow: the ends lean toward the town by up to 12 % of the width
				float bow0 = (k0 * k0) * 0.48F * w;
				float bow1 = (k1 * k1) * 0.48F * w;
				float x0 = cx + rx * k0 * w + b.ax * (back - bow0);
				float z0 = cz + rz * k0 * w + b.az * (back - bow0);
				float x1 = cx + rx * k1 * w + b.ax * (back - bow1);
				float z1 = cz + rz * k1 * w + b.az * (back - bow1);
				float u0 = (k0 + 0.5F) * 2.0F + scroll + layer * 0.37F;
				float u1 = (k1 + 0.5F) * 2.0F + scroll + layer * 0.37F;
				P0.set(x0, by + h, z0);
				P1.set(x0, by, z0);
				P2.set(x1, by, z1);
				P3.set(x1, by + h, z1);
				TemaFx.dinding.quadUv(P0, P1, P2, P3, u0, lift, u0, 1.0F, u1, 1.0F, u1, lift, col, colBawah, colBawah, col, light);
			}
		}
	}

	/** The sand-haze shells and the low ceiling around the camera. */
	private static void cangkang(Badai b, float storm) {
		int light = LightCoordsUtil.FULL_BRIGHT;
		float terang = 0.3F + 0.7F * Bingkai.terang;
		int atas = Bingkai.redup(0xE8C890, terang);
		int bawah = Bingkai.redup(WARNA, terang * 0.9F);
		float[] radii = Bingkai.tier >= 2 ? RADII_MAKS : RADII;
		// the wind turns the bands: scroll along the ring in the wind's sense
		float spin = Bingkai.t * 0.0025F;
		for (int i = 0; i < radii.length; i++) {
			float r = radii[i];
			if (bawahAtap && r < 12.0F) {
				continue;
			}
			float a = storm * (0.2F + 0.05F * i) * (bawahAtap ? 0.6F : 1.0F);
			int seg = r < 12.0F ? 12 : 16;
			float top = 9.0F + r * 0.35F;
			float bottom = -4.0F - r * 0.15F;
			Bentuk.cangkang(TemaFx.pasir, r, bottom, top, seg, Math.max(2.0F, r / 4.0F), spin * (24.0F / r) + i * 0.29F,
					Bingkai.argb(a, atas), Bingkai.argb(a * 1.1F, bawah), light);
		}
		if (!bawahAtap && TemaFx.muat(4)) {
			// a low sand ceiling (the sky gone brown), four quads with a soft rim
			Bentuk.lembar(TemaFx.pasir, 0.0F, 13.0F, 0.0F, Bingkai.camX, Bingkai.camZ, 2, 40.0F, 64.0F, -Bingkai.t * 0.004F * b.ax,
					-Bingkai.t * 0.004F * b.az, atas, storm * 0.55F, 0.35F, light);
		}
	}

	/** The flying sand: streaks along their velocity and a few tumbling dust clumps. */
	private static void partikel(float storm) {
		int n = jumlah;
		if (n == 0 || !EpicBudget.takeQuads(Math.min(n, 64))) {
			return;
		}
		int grant = n <= 64 ? n : 64;
		// take the rest of the stream from the shared 7b budget in chunks: a crowded frame drops the tail, never a picture
		while (grant < n && EpicBudget.takeQuads(Math.min(64, n - grant))) {
			grant += Math.min(64, n - grant);
		}
		float[] gores = FxTekstur.sel(FxTekstur.GORES);
		float[] debu = FxTekstur.sel(FxTekstur.DEBU);
		float pt = Bingkai.pt;
		float terang = 0.35F + 0.65F * Bingkai.terang;
		int pasir = Bingkai.redup(0xF0D8A8, terang);
		int gumpal = Bingkai.redup(0xD8B480, terang);
		int light = LightCoordsUtil.FULL_BRIGHT;
		for (int i = 0; i < grant; i++) {
			if (!TemaFx.muat(1)) {
				return;
			}
			float x = (float) (Mth.lerp(pt, OX[i], PX[i]) - Bingkai.camX);
			float y = (float) (Mth.lerp(pt, OY[i], PY[i]) - Bingkai.camY);
			float z = (float) (Mth.lerp(pt, OZ[i], PZ[i]) - Bingkai.camZ);
			float life = UMUR[i] / (float) HIDUP[i];
			float fade = Math.min(1.0F, life * 5.0F) * Math.min(1.0F, (1.0F - life) * 4.0F);
			if (GUMPAL[i]) {
				float roll = (UMUR[i] + pt) * 0.09F + i;
				TemaFx.pasir.billboard(x, y, z, UKURAN[i], UKURAN[i] * 0.8F, roll, Bingkai.KANAN, Bingkai.ATAS, debu[0], debu[1], debu[2], debu[3],
						Bingkai.argb(0.5F * fade * storm, gumpal), light);
			} else {
				float len = 1.6F + UKURAN[i] * 9.0F;
				float sp = Mth.sqrt(VX[i] * VX[i] + VY[i] * VY[i] + VZ[i] * VZ[i]);
				float k = sp < 1.0E-4F ? 0.0F : len / sp;
				TemaFx.pasir.pitaU(x, y, z, x - VX[i] * k, y - VY[i] * k, z - VZ[i] * k, UKURAN[i], UKURAN[i] * 0.6F, gores[0], gores[1], gores[2],
						gores[3], Bingkai.argb(0.75F * fade * storm, pasir), Bingkai.argb(0.0F, pasir), light);
			}
		}
	}
}
