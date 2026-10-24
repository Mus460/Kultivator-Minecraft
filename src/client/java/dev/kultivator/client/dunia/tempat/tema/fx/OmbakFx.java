package dev.kultivator.client.dunia.tempat.tema.fx;

import dev.kultivator.dunia.tempat.tema.pelabuhan.PerahuNelayanEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Ombak Teluk Badai (DESIGN 38.T.3 "Badai Laut", 38.T.9.4): while the world thunders ({@code getThunderLevel}, only
 * read) and the local player rows a hired fishing boat ({@link PerahuNelayanEntity}), a heaving storm surface rolls
 * around the boat: a 32-block sheet of {@code ombak.png} (dark swell with foam) a hand above the vanilla water, its
 * vertices lifted by three crossing swells (the main one from the storm's side), foam brightening the crests, the
 * sheet clear around the hull so the boat sits in it, soft at the rim, lit by the world light. The surf roars under it
 * (a low rain bed). Minimal none, Normal one sheet of 10 × 10, Maximal 14 × 14. The push on the boat is the server's.
 */
final class OmbakFx {

	private static float kuat;
	private static float kuatLalu;
	private static double permukaan = Double.NaN;
	private static long ujiSampai = Long.MIN_VALUE;
	private static TemaSuara.Lingkup deru;
	private static final BlockPos.MutableBlockPos CEK = new BlockPos.MutableBlockPos();
	private static final Vector3f P0 = new Vector3f();
	private static final Vector3f P1 = new Vector3f();
	private static final Vector3f P2 = new Vector3f();
	private static final Vector3f P3 = new Vector3f();
	private static final float ARAH_X = Mth.cos(0.6F);
	private static final float ARAH_Z = Mth.sin(0.6F);

	private OmbakFx() {
	}

	/** Preview: a storm surface around the player for {@code panjang} ticks, boat or not. */
	static void uji(long now, int panjang) {
		ujiSampai = now + panjang;
	}

	static boolean aktif() {
		return kuat > 0.0F || kuatLalu > 0.0F;
	}

	static void bersihkan() {
		kuat = 0.0F;
		kuatLalu = 0.0F;
		ujiSampai = Long.MIN_VALUE;
		TemaSuara.lepas(deru);
		deru = null;
	}

	private static Entity pusat(Minecraft minecraft) {
		Entity vehicle = minecraft.player.getVehicle();
		return vehicle instanceof PerahuNelayanEntity ? vehicle : minecraft.player;
	}

	static void tick(Minecraft minecraft, long now) {
		kuatLalu = kuat;
		if (minecraft.player == null || minecraft.level == null) {
			kuat = 0.0F;
			return;
		}
		boolean naik = minecraft.player.getVehicle() instanceof PerahuNelayanEntity;
		float badai = minecraft.level.getThunderLevel(1.0F);
		float target = naik ? Bingkai.halus((badai - 0.3F) / 0.4F) : 0.0F;
		if (now < ujiSampai) {
			target = 1.0F;
		}
		kuat += (target - kuat) * 0.05F;
		if (kuat < 0.005F && target <= 0.0F) {
			kuat = 0.0F;
		}
		if (kuat > 0.0F && (now % 10L == 0L || Double.isNaN(permukaan))) {
			Entity e = pusat(minecraft);
			permukaan = cariPermukaan(minecraft, e.getX(), e.getY(), e.getZ());
		}
		deru = TemaSuara.jaga(deru, SoundEvents.WEATHER_RAIN, kuat * 0.35F, 0.55F);
	}

	private static double cariPermukaan(Minecraft minecraft, double x, double y, double z) {
		int by = Mth.floor(y);
		for (int dy = 1; dy >= -3; dy--) {
			CEK.set(Mth.floor(x), by + dy, Mth.floor(z));
			FluidState f = minecraft.level.getFluidState(CEK);
			if (!f.isEmpty()) {
				return by + dy + f.getHeight(minecraft.level, CEK);
			}
		}
		return y;
	}

	/** Height of the storm swell at world (x, z), time t. */
	private static float tinggi(double x, double z, float t) {
		float along = (float) (x * ARAH_X + z * ARAH_Z);
		float across = (float) (-x * ARAH_Z + z * ARAH_X);
		return 0.32F * Mth.sin(along * 0.42F - t * 0.11F) + 0.14F * Mth.sin(across * 0.61F + along * 0.2F - t * 0.17F)
				+ 0.07F * Mth.sin((float) (x * 1.3D - z * 0.9D) - t * 0.31F);
	}

	static void gambar() {
		float k = Mth.lerp(Bingkai.pt, kuatLalu, kuat);
		int tier = Bingkai.tier;
		if (k <= 0.01F || tier == 0 || Double.isNaN(permukaan)) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		Vec3 c = pusat(minecraft).getPosition(Bingkai.pt);
		int n = tier >= 2 ? 14 : 10;
		float tile = 32.0F / n;
		if (!TemaFx.muat(n * n)) {
			return;
		}
		// snap the sheet to the tile grid so the swell does not slide with the boat
		double ox = Math.floor(c.x / tile) * tile - n * tile * 0.5D;
		double oz = Math.floor(c.z / tile) * tile - n * tile * 0.5D;
		float baseY = (float) (permukaan + 0.09D - Bingkai.camY);
		float t = Bingkai.t;
		int light = Bingkai.cahaya(c.x, permukaan + 0.5D, c.z);
		float day = 0.45F + 0.55F * Bingkai.terang;
		for (int ix = 0; ix < n; ix++) {
			for (int iz = 0; iz < n; iz++) {
				double x0 = ox + ix * tile;
				double z0 = oz + iz * tile;
				double x1 = x0 + tile;
				double z1 = z0 + tile;
				int c00 = warna(x0, z0, c, k, t, day);
				int c01 = warna(x0, z1, c, k, t, day);
				int c11 = warna(x1, z1, c, k, t, day);
				int c10 = warna(x1, z0, c, k, t, day);
				if ((c00 | c01 | c11 | c10) >>> 24 == 0) {
					continue;
				}
				P0.set((float) (x0 - Bingkai.camX), baseY + tinggi(x0, z0, t) * k * redam(x0, z0, c), (float) (z0 - Bingkai.camZ));
				P1.set((float) (x0 - Bingkai.camX), baseY + tinggi(x0, z1, t) * k * redam(x0, z1, c), (float) (z1 - Bingkai.camZ));
				P2.set((float) (x1 - Bingkai.camX), baseY + tinggi(x1, z1, t) * k * redam(x1, z1, c), (float) (z1 - Bingkai.camZ));
				P3.set((float) (x1 - Bingkai.camX), baseY + tinggi(x1, z0, t) * k * redam(x1, z0, c), (float) (z0 - Bingkai.camZ));
				float su = t * 0.004F;
				float u0 = (float) (x0 / 8.0D - Math.floor(ox / 8.0D)) + su;
				float u1 = (float) (x1 / 8.0D - Math.floor(ox / 8.0D)) + su;
				float v0 = (float) (z0 / 8.0D - Math.floor(oz / 8.0D)) + su * 0.6F;
				float v1 = (float) (z1 / 8.0D - Math.floor(oz / 8.0D)) + su * 0.6F;
				TemaFx.ombak.quadUv(P0, P1, P2, P3, u0, v0, u0, v1, u1, v1, u1, v0, c00, c01, c11, c10, light);
			}
		}
	}

	/** 0 at the hull, 1 from 4 blocks out: the boat sits in calm water of its own. */
	private static float redam(double x, double z, Vec3 c) {
		double d = Math.sqrt(Mth.square(x - c.x) + Mth.square(z - c.z));
		return Bingkai.halus((float) ((d - 2.0D) / 2.5D));
	}

	private static int warna(double x, double z, Vec3 c, float k, float t, float day) {
		double d = Math.sqrt(Mth.square(x - c.x) + Mth.square(z - c.z));
		float rim = 1.0F - Bingkai.halus((float) ((d - 10.0D) / 6.0D));
		float hull = Bingkai.halus((float) ((d - 1.8D) / 2.0D));
		float crest = Bingkai.halus((tinggi(x, z, t) - 0.18F) / 0.25F);
		int rgb = Bingkai.campur(0x9AB0C4, 0xFFFFFF, crest * 0.8F);
		return Bingkai.argb(k * rim * hull * (0.82F + 0.18F * crest), Bingkai.redup(rgb, day));
	}
}
