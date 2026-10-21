package dev.kultivator.client.epic.kosmik.orbit;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import dev.kultivator.Kultivator;
import dev.kultivator.client.epic.kosmik.KosmikColors;
import dev.kultivator.client.epic.kosmik.StageContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The planet bake (DESIGN 32.2.2): the default day map recoloured around home with the caster's biome colours, so the
 * land under the sword is this world's land. Overworld only; Normal and Maximal without "Ringan" (Minimal and Ringan
 * draw the static map).
 * <ul>
 * <li>At film arm (or preview) {@link #request} starts a run when the sampled colours (hash) or the map size changed:
 * {@code planet_day_<n>} and {@code planet_class_<n>} are decoded off the client thread ({@link Util#backgroundExecutor},
 * PNG decode only: no GL, no level access), so {@code prepare} stays within its 4 ms.</li>
 * <li>The recolour runs in row slices on the client tick until 1.2 ms per tick are spent: per pixel
 * {@code target = palette(class, colours) × (0.75 + 0.5 G)} and
 * {@code out = mix(day, target, 0.85 × B × home + (water ? 0.3 : 0))}, where B is the map's home weight (the home
 * meridian arc) and {@code home} falls from 1 to 0 between 12° and 48° of arc from the actual home latitude.</li>
 * <li>Then one {@link DynamicTexture} (linear filtering) is registered at {@code kultivator:dynamic/kosmik_planet}
 * (the previous one released by {@code TextureManager.register}). Until then the static map is drawn.</li>
 * <li>{@link #release} (disconnect, resource reload) drops the texture, any decoded images and cancels a run.</li>
 * </ul>
 */
final class PlanetBake {

	private static final long SLICE_NANOS = 1_200_000L;

	private static final int IDLE = 0;
	private static final int LOADING = 1;
	private static final int BAKING = 2;
	private static final int READY = 3;
	private static final int FAILED = 4;

	private static final Object LOCK = new Object();
	/** Guarded by {@link #LOCK}: the run the background decode belongs to, and its result. */
	private static int generation;
	private static @Nullable NativeImage pendingDay;
	private static @Nullable NativeImage pendingClass;
	private static boolean pendingFailed;
	private static boolean pendingDone;

	// client thread only
	private static int state = IDLE;
	private static int runHash;
	private static int readyHash;
	private static boolean registered;
	private static @Nullable NativeImage day;
	private static @Nullable NativeImage cls;
	private static @Nullable NativeImage out;
	private static int row;
	private static int width;
	private static int height;
	private static double sinHome;
	private static double cosHome;
	private static double[] cosLon = new double[0];
	private static final int[] PALETTE = new int[11];
	private static KosmikColors colors = KosmikColors.DEFAULT;

	/** The baked map is registered and current. */
	static boolean ready() {
		return state == READY && registered;
	}

	/** Starts a bake for this context when needed (never throws; ≤ a few µs on the client thread). */
	static void request(StageContext ctx) {
		try {
			KosmikColors c = ctx.colors();
			if (c == null || !c.overworld() || ctx.ringan() || ctx.quality() < 1) {
				return;
			}
			int size = ctx.quality() >= 2 ? 2 : 1;
			int hash = (c.hash() * 31 + size) * 31 + Float.floatToIntBits(OrbitBeats.homeLatitude(c));
			if (state == READY && readyHash == hash && registered) {
				return;
			}
			if ((state == LOADING || state == BAKING) && runHash == hash) {
				return;
			}
			cancelRun();
			colors = c;
			runHash = hash;
			state = LOADING;
			int run;
			synchronized (LOCK) {
				run = ++generation;
				pendingDone = false;
				pendingFailed = false;
			}
			ResourceManager manager = Minecraft.getInstance().getResourceManager();
			Identifier dayId = OrbitTex.PLANET_DAY[size];
			Identifier classId = OrbitTex.PLANET_CLASS[size];
			Util.backgroundExecutor().execute(() -> decode(run, manager, dayId, classId));
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Kultivator: Film kosmik 3D: the planet bake could not start", exception);
			state = FAILED;
		}
	}

	/** Off the client thread: decodes both maps (PNG only) and hands them over. */
	private static void decode(int run, ResourceManager manager, Identifier dayId, Identifier classId) {
		NativeImage d = null;
		NativeImage c = null;
		boolean failed = false;
		try {
			d = read(manager, dayId);
			c = read(manager, classId);
			if (d.getWidth() != c.getWidth() || d.getHeight() != c.getHeight()) {
				failed = true;
			}
		} catch (IOException | RuntimeException exception) {
			failed = true;
		}
		synchronized (LOCK) {
			if (run == generation && !pendingDone) {
				if (failed) {
					closeQuietly(d);
					closeQuietly(c);
				} else {
					pendingDay = d;
					pendingClass = c;
				}
				pendingFailed = failed;
				pendingDone = true;
				return;
			}
		}
		// a stale run (released or replaced meanwhile)
		closeQuietly(d);
		closeQuietly(c);
	}

	private static NativeImage read(ResourceManager manager, Identifier id) throws IOException {
		Optional<Resource> resource = manager.getResource(id);
		if (resource.isEmpty()) {
			throw new IOException("missing " + id);
		}
		InputStream in = resource.get().open();
		return NativeImage.read(in);
	}

	/** The client tick: picks up the decoded maps, recolours rows for ≤ 1.2 ms, then uploads. Idle: one int compare. */
	static void tick() {
		if (state != LOADING && state != BAKING) {
			return;
		}
		try {
			if (state == LOADING) {
				synchronized (LOCK) {
					if (!pendingDone) {
						return;
					}
					if (pendingFailed) {
						pendingDone = false;
						state = FAILED;
						Kultivator.LOGGER.warn("Kultivator: Film kosmik 3D: the planet maps could not be read; the static map stays");
						return;
					}
					day = pendingDay;
					cls = pendingClass;
					pendingDay = null;
					pendingClass = null;
					pendingDone = false;
				}
				begin();
			}
			long end = System.nanoTime() + SLICE_NANOS;
			while (row < height && System.nanoTime() < end) {
				bakeRow(row++);
			}
			if (row >= height) {
				upload();
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Kultivator: Film kosmik 3D: the planet bake failed; the static map stays", exception);
			cancelRun();
			state = FAILED;
		}
	}

	private static void begin() {
		NativeImage d = day;
		if (d == null || cls == null) {
			state = FAILED;
			return;
		}
		width = d.getWidth();
		height = d.getHeight();
		out = new NativeImage(width, height, false);
		row = 0;
		double lat = Math.toRadians(OrbitBeats.homeLatitude(colors));
		sinHome = Math.sin(lat);
		cosHome = Math.cos(lat);
		if (cosLon.length != width) {
			cosLon = new double[width];
		}
		for (int x = 0; x < width; x++) {
			double lon = Math.toRadians((x + 0.5D) / width * 360.0D - 180.0D);
			cosLon[x] = Math.cos(lon);
		}
		int grass = colors.grass();
		int foliage = colors.foliage();
		int water = colors.water();
		// −1 keeps the day colour (beach, desert, rock); land is toned to sit with the default palette
		PALETTE[0] = scale(water, 0.42D);
		PALETTE[1] = scale(water, 0.75D);
		PALETTE[2] = -1;
		PALETTE[3] = scale(grass, 0.82D * 0.92D);
		PALETTE[4] = scale(foliage, 0.82D * 0.72D);
		PALETTE[5] = scale(foliage, 0.82D * 0.62D);
		PALETTE[6] = scale(grass, 0.82D * 0.95D);
		PALETTE[7] = -1;
		PALETTE[8] = scale(foliage, 0.82D * 0.88D);
		PALETTE[9] = -1;
		PALETTE[10] = 0xF4F8FF;
		state = BAKING;
	}

	private static void bakeRow(int y) {
		NativeImage d = day;
		NativeImage c = cls;
		NativeImage o = out;
		if (d == null || c == null || o == null) {
			throw new IllegalStateException("bake images gone");
		}
		double lat = Math.toRadians(90.0D - (y + 0.5D) / height * 180.0D);
		double sl = Math.sin(lat) * sinHome;
		double cl = Math.cos(lat) * cosHome;
		for (int x = 0; x < width; x++) {
			int dayArgb = d.getPixel(x, y);
			int info = c.getPixel(x, y);
			int klass = Math.min(10, (info >> 16 & 0xFF) / 16);
			int target = PALETTE[klass];
			if (target < 0) {
				o.setPixel(x, y, 0xFF000000 | dayArgb);
				continue;
			}
			double detail = 0.75D + 0.5D * (info >> 8 & 0xFF) / 255.0D;
			double weight = (info & 0xFF) / 255.0D;
			double arc = Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, sl + cl * cosLon[x]))));
			double home = 1.0D - OrbitBeats.smoothstep(12.0D, 48.0D, arc);
			double mix = 0.85D * weight * home + (klass <= 1 ? 0.3D : 0.0D);
			if (mix <= 0.0D) {
				o.setPixel(x, y, 0xFF000000 | dayArgb);
				continue;
			}
			mix = Math.min(1.0D, mix);
			int r = blend(dayArgb >> 16 & 0xFF, (target >> 16 & 0xFF) * detail, mix);
			int g = blend(dayArgb >> 8 & 0xFF, (target >> 8 & 0xFF) * detail, mix);
			int b = blend(dayArgb & 0xFF, (target & 0xFF) * detail, mix);
			o.setPixel(x, y, 0xFF000000 | r << 16 | g << 8 | b);
		}
	}

	private static void upload() {
		NativeImage o = out;
		out = null;
		closeQuietly(day);
		closeQuietly(cls);
		day = null;
		cls = null;
		if (o == null) {
			state = FAILED;
			return;
		}
		BakedTexture texture = new BakedTexture(() -> "kultivator kosmik planet", o);
		Minecraft.getInstance().getTextureManager().register(OrbitTex.BAKED_PLANET, texture);
		registered = true;
		readyHash = runHash;
		state = READY;
	}

	/** Disconnect / resource reload: drops the texture and any run. */
	static void release() {
		cancelRun();
		if (registered) {
			try {
				Minecraft.getInstance().getTextureManager().release(OrbitTex.BAKED_PLANET);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Kultivator: Film kosmik 3D: could not release the baked planet", exception);
			}
			registered = false;
		}
		readyHash = 0;
		state = IDLE;
	}

	/** Cancels a running decode or recolour (the registered texture stays). */
	private static void cancelRun() {
		synchronized (LOCK) {
			generation++;
			closeQuietly(pendingDay);
			closeQuietly(pendingClass);
			pendingDay = null;
			pendingClass = null;
			pendingDone = false;
			pendingFailed = false;
		}
		closeQuietly(day);
		closeQuietly(cls);
		closeQuietly(out);
		day = null;
		cls = null;
		out = null;
		row = 0;
		if (state == LOADING || state == BAKING) {
			state = registered ? READY : IDLE;
		}
	}

	/** One line for {@code /sinematik kosmik3d info}. */
	static String info() {
		return switch (state) {
			case LOADING -> "bake: reading maps";
			case BAKING -> "bake: " + (height > 0 ? row * 100 / height : 0) + "%";
			case READY -> "bake: ready " + width + "x" + height;
			case FAILED -> "bake: failed (static map)";
			default -> "bake: idle";
		};
	}

	private static int blend(int a, double b, double t) {
		return Math.max(0, Math.min(255, (int) Math.round(a + (b - a) * t)));
	}

	private static int scale(int rgb, double k) {
		int r = (int) Math.max(0, Math.min(255, Math.round((rgb >> 16 & 0xFF) * k)));
		int g = (int) Math.max(0, Math.min(255, Math.round((rgb >> 8 & 0xFF) * k)));
		int b = (int) Math.max(0, Math.min(255, Math.round((rgb & 0xFF) * k)));
		return r << 16 | g << 8 | b;
	}

	private static void closeQuietly(@Nullable NativeImage image) {
		if (image != null) {
			try {
				image.close();
			} catch (RuntimeException ignored) {
				// already closed
			}
		}
	}

	/** The baked planet: a dynamic texture sampled with linear filtering (the static maps blur through their .mcmeta). */
	private static final class BakedTexture extends DynamicTexture {
		BakedTexture(Supplier<String> label, NativeImage image) {
			super(label, image);
			this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
		}
	}

	private PlanetBake() {
	}
}
