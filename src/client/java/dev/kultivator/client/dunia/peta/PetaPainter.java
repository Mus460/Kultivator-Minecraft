package dev.kultivator.client.dunia.peta;

import com.mojang.blaze3d.platform.NativeImage;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.peta.PetaInk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Paints the world map (DESIGN 34.5.16) into ONE 512 × 512 {@link DynamicTexture} ({@code kultivator:dynamic/peta}):
 * an ink-wash on aged parchment. Water is a soft blue-grey wash with a darker deep sea and an inked coastline (smooth,
 * from the cell grid by bilinear interpolation); land a pale wash per biome with relief shading from the height bands;
 * then brush stamps are pressed on it (mountain strokes, tree dots, waves, dunes, reeds, snow) at a density that suits
 * the zoom; unexplored land stays under a mist of fog wisps that feathers into the painted part. Cartographic grid lines
 * every 1 024 blocks.
 * <p>
 * Weak laptop: painted only when the view leaves the painted window, the zoom changes or new regions arrived (at most
 * every 0.4 s), time-sliced at 4 ms per frame into a back buffer, then uploaded once; nothing is drawn in the world.
 */
public final class PetaPainter {

	public static final int SIZE = 512;
	public static final Identifier TEXTURE = Kultivator.id("dynamic/peta");
	private static final long SLICE_NANOS = 4_000_000L;
	private static final long MIN_GAP_MILLIS = 400L;

	private static final Identifier PARCHMENT = Kultivator.id("textures/dunia/peta/parchment.png");
	private static final Identifier FOG = Kultivator.id("textures/dunia/peta/fog.png");
	private static final Identifier BRUSHES = Kultivator.id("textures/dunia/peta/brushes.png");

	/** The class washes (RGB) by {@link PetaInk} class. */
	private static final int[] WASH = new int[32];

	static {
		WASH[PetaInk.DEEP_OCEAN] = 0x4A6674;
		WASH[PetaInk.OCEAN] = 0x6A8A94;
		WASH[PetaInk.WARM_OCEAN] = 0x77A39E;
		WASH[PetaInk.FROZEN_OCEAN] = 0x9FBAC6;
		WASH[PetaInk.RIVER] = 0x7898A2;
		WASH[PetaInk.BEACH] = 0xE4D5A4;
		WASH[PetaInk.STONY_SHORE] = 0xB3AA96;
		WASH[PetaInk.PLAINS] = 0xCBC99C;
		WASH[PetaInk.MEADOW] = 0xC0CD98;
		WASH[PetaInk.FLOWER] = 0xCBC292;
		WASH[PetaInk.FOREST] = 0x93A67C;
		WASH[PetaInk.BIRCH] = 0xA6B689;
		WASH[PetaInk.DARK_FOREST] = 0x748566;
		WASH[PetaInk.PALE] = 0xB6B7AA;
		WASH[PetaInk.CHERRY] = 0xDCB8B2;
		WASH[PetaInk.TAIGA] = 0x84977E;
		WASH[PetaInk.SNOW_FOREST] = 0xC0CAC4;
		WASH[PetaInk.SNOWY] = 0xEBEEE8;
		WASH[PetaInk.SNOW_PEAK] = 0xF4F6F2;
		WASH[PetaInk.MOUNTAIN] = 0xA29C8E;
		WASH[PetaInk.HILLS] = 0xAEAA92;
		WASH[PetaInk.JUNGLE] = 0x74986A;
		WASH[PetaInk.SAVANNA] = 0xCBBA7E;
		WASH[PetaInk.DESERT] = 0xE3CB8E;
		WASH[PetaInk.BADLANDS] = 0xC48E5E;
		WASH[PetaInk.SWAMP] = 0x83906A;
		WASH[PetaInk.MANGROVE] = 0x738A64;
		WASH[PetaInk.MUSHROOM] = 0xBAA3B2;
		WASH[PetaInk.NETHER] = 0x8E3E34;
		WASH[PetaInk.END] = 0xDAD8B4;
		WASH[PetaInk.SPIRIT] = 0xB8A8CA;
	}

	private static final int INK = 0x23231F;

	// sources (ARGB), loaded once
	private static int[] parchment;
	private static int[] fog;
	private static int[] brushes;
	private static boolean loaded;

	// the texture on the GPU and what it shows
	private static @Nullable DynamicTexture texture;
	private static int shownX;
	private static int shownZ;
	private static int shownBpp;
	private static boolean shown;

	// the bake in progress
	private static @Nullable NativeImage back;
	private static int bakeX;
	private static int bakeZ;
	private static int bakeBpp;
	private static int bakeRow = -1;
	private static int bakeVersion = -1;
	private static int shownVersion = -1;
	private static long lastStart;
	private static byte[] grid = new byte[0];
	private static int gridSize;
	private static int gridX0;
	private static int gridZ0;
	private static int nodeSize;
	private static int step;
	private static int stampPhase;

	/** True once a painted texture is on the GPU. */
	public static boolean ready() {
		return shown && texture != null;
	}

	/** World x of the texture's left edge, its top edge and blocks per texture pixel. */
	public static int shownX() {
		return shownX;
	}

	public static int shownZ() {
		return shownZ;
	}

	public static int shownBpp() {
		return shownBpp;
	}

	/**
	 * Keeps the painted window around the view centre {@code vx/vz} at {@code bpp} blocks per pixel; starts a new bake
	 * when the view drifted more than 96 px from the window's middle, the zoom changed or new regions arrived. Then works
	 * on the running bake for up to 4 ms.
	 */
	public static void frame(double vx, double vz, int bpp) {
		ensureLoaded();
		long now = System.currentTimeMillis();
		int half = SIZE / 2 * bpp;
		boolean baking = bakeRow >= 0;
		int targetX = (int) Math.floor(vx) - half;
		int targetZ = (int) Math.floor(vz) - half;
		// snap the window to whole pixels of the zoom so the parchment stays put while panning
		targetX = Math.floorDiv(targetX, bpp) * bpp;
		targetZ = Math.floorDiv(targetZ, bpp) * bpp;
		int refX = baking ? bakeX : shownX;
		int refZ = baking ? bakeZ : shownZ;
		int refBpp = baking ? bakeBpp : shownBpp;
		double driftX = (vx - (refX + half)) / bpp;
		double driftZ = (vz - (refZ + half)) / bpp;
		boolean far = !shown && !baking || refBpp != bpp || Math.abs(driftX) > 96.0D || Math.abs(driftZ) > 96.0D;
		boolean fresh = PetaCache.version() != (baking ? bakeVersion : shownVersion);
		if (far || (fresh && !baking && now - lastStart >= MIN_GAP_MILLIS)) {
			start(far ? targetX : (baking ? bakeX : shownX), far ? targetZ : (baking ? bakeZ : shownZ), bpp);
			lastStart = now;
		}
		if (bakeRow >= 0) {
			work();
		}
	}

	/** Drops the texture (disconnect, the screen closed for good) and any bake. */
	public static void release() {
		if (back != null) {
			back.close();
			back = null;
		}
		bakeRow = -1;
		if (texture != null) {
			try {
				Minecraft.getInstance().getTextureManager().release(TEXTURE);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Kultivator: the map texture could not be released", exception);
			}
			texture = null;
		}
		shown = false;
		shownVersion = -1;
	}

	// ================================================================== the bake

	private static void start(int x, int z, int bpp) {
		if (back == null) {
			back = new NativeImage(SIZE, SIZE, false);
		}
		bakeX = x;
		bakeZ = z;
		bakeBpp = bpp;
		bakeVersion = PetaCache.version();
		bakeRow = 0;
		stampPhase = 0;
		int cs = Math.max(1, PetaCache.cellSize());
		step = Math.max(1, bpp / cs);
		nodeSize = cs * step;
		gridX0 = Math.floorDiv(x, nodeSize) - 2;
		gridZ0 = Math.floorDiv(z, nodeSize) - 2;
		gridSize = SIZE * bpp / nodeSize + 6;
		if (grid.length < gridSize * gridSize) {
			grid = new byte[gridSize * gridSize];
		}
		for (int j = 0; j < gridSize; j++) {
			for (int i = 0; i < gridSize; i++) {
				int cx = (gridX0 + i) * step + step / 2;
				int cz = (gridZ0 + j) * step + step / 2;
				grid[j * gridSize + i] = PetaCache.cell(cx, cz);
			}
		}
	}

	private static void work() {
		NativeImage img = back;
		if (img == null) {
			bakeRow = -1;
			return;
		}
		long end = System.nanoTime() + SLICE_NANOS;
		try {
			while (bakeRow < SIZE && System.nanoTime() < end) {
				paintRow(img, bakeRow++);
			}
			if (bakeRow >= SIZE) {
				while (stampPhase < gridSize && System.nanoTime() < end) {
					stampRow(img, stampPhase++);
				}
				if (stampPhase >= gridSize) {
					finish(img);
				}
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Kultivator: painting the world map failed", exception);
			bakeRow = -1;
		}
	}

	private static void finish(NativeImage img) {
		Minecraft minecraft = Minecraft.getInstance();
		if (texture == null) {
			texture = new DynamicTexture(() -> "kultivator world map", SIZE, SIZE, false);
			minecraft.getTextureManager().register(TEXTURE, texture);
		}
		NativeImage target = texture.getPixels();
		target.copyFrom(img);
		texture.upload();
		shownX = bakeX;
		shownZ = bakeZ;
		shownBpp = bakeBpp;
		shownVersion = bakeVersion;
		shown = true;
		bakeRow = -1;
	}

	private static byte node(int i, int j) {
		if (i < 0 || j < 0 || i >= gridSize || j >= gridSize) {
			return 0;
		}
		return grid[j * gridSize + i];
	}

	private static void paintRow(NativeImage img, int py) {
		int bpp = bakeBpp;
		double wz = bakeZ + (py + 0.5D) * bpp;
		double fz = wz / nodeSize - 0.5D - gridZ0;
		int j = (int) Math.floor(fz);
		double tz = fz - j;
		for (int px = 0; px < SIZE; px++) {
			double wx = bakeX + (px + 0.5D) * bpp;
			double fx = wx / nodeSize - 0.5D - gridX0;
			int i = (int) Math.floor(fx);
			double tx = fx - i;
			byte b00 = node(i, j);
			byte b10 = node(i + 1, j);
			byte b01 = node(i, j + 1);
			byte b11 = node(i + 1, j + 1);
			double known = bilinear(b00 != 0, b10 != 0, b01 != 0, b11 != 0, tx, tz);
			double water = bilinear(isWater(b00), isWater(b10), isWater(b01), isWater(b11), tx, tz);
			byte near = tx < 0.5D ? (tz < 0.5D ? b00 : b01) : (tz < 0.5D ? b10 : b11);
			if (near == 0) {
				near = b00 != 0 ? b00 : b10 != 0 ? b10 : b01 != 0 ? b01 : b11;
			}
			int ux = (int) Math.floor(wx / bpp);
			int uz = (int) Math.floor(wz / bpp);
			int paper = sample(parchment, 256, ux, uz);
			int color;
			if (known <= 0.02D) {
				color = fogged(paper, ux, uz);
			} else {
				int painted = paint(near, water, known, paper, i, j, tx, tz, (int) wx, (int) wz);
				if (known < 0.98D) {
					double k = smooth(Math.min(1.0D, Math.max(0.0D, (known - 0.25D) / 0.55D)));
					painted = mix(fogged(paper, ux, uz), painted, k);
				}
				color = painted;
			}
			// grid lines every 64 pixels of the zoom (1 024 / 4 096 / 16 384 blocks; one pixel, faint ink)
			int gridStep = bpp * 64;
			if (Math.floorMod((int) Math.floor(wx), gridStep) < bpp || Math.floorMod((int) Math.floor(wz), gridStep) < bpp) {
				color = mix(color, INK, 0.08D);
			}
			img.setPixel(px, py, 0xFF000000 | (color & 0xFFFFFF));
		}
	}

	private static int paint(byte near, double water, double known, int paper, int i, int j, double tx, double tz, int wx, int wz) {
		int kind = PetaInk.kind(near);
		double coastWidth = bakeBpp >= 256 ? 0.22D : bakeBpp >= 64 ? 0.16D : 0.12D;
		int color;
		if (water > 0.5D) {
			// water: deeper further from the shore, with a lighter shallow band
			int wash = WASH[PetaInk.water(kind) ? kind : PetaInk.OCEAN];
			double depth = Math.min(1.0D, (water - 0.5D) * 2.6D);
			int shallow = mix(paper, 0x9DB7B4, 0.55D);
			int deep = mix(paper, wash, 0.78D);
			color = mix(shallow, deep, smooth(depth));
			// faint horizontal brush texture on the water
			double streak = ((hash(wx >> 3, wz >> 1) & 0xFF) / 255.0D - 0.5D) * 0.05D;
			color = shade(color, streak);
		} else {
			int wash = kind > 0 ? WASH[kind] : WASH[PetaInk.PLAINS];
			color = mix(paper, wash, 0.6D);
			// relief: brighter lowlands, darker highlands, and a north-west light on slopes
			int band = bandOf(near);
			color = shade(color, 0.07D - band * 0.025D);
			int bl = bandOf(node(i - 1, j));
			int br = bandOf(node(i + 1, j));
			int bu = bandOf(node(i, j - 1));
			int bd = bandOf(node(i, j + 1));
			color = shade(color, ((bl - br) + (bu - bd)) * 0.018D);
			// a sandy rim just inland of the coast
			if (water > 0.32D) {
				color = mix(color, 0xE6D8AA, (water - 0.32D) / 0.18D * 0.5D);
			}
			double grain = ((hash(wx >> 2, wz >> 2) & 0xFF) / 255.0D - 0.5D) * 0.04D;
			color = shade(color, grain);
		}
		// the inked coastline
		double edge = Math.abs(water - 0.5D);
		if (edge < coastWidth && known > 0.5D) {
			double a = 1.0D - edge / coastWidth;
			color = mix(color, INK, 0.75D * a * a);
		}
		return color;
	}

	private static int bandOf(byte cell) {
		if (cell == 0) {
			return 3;
		}
		int band = PetaInk.band(cell);
		if (band > 0) {
			return band;
		}
		return switch (PetaInk.kind(cell)) {
			case PetaInk.SNOW_PEAK, PetaInk.MOUNTAIN -> 6;
			case PetaInk.HILLS, PetaInk.MEADOW, PetaInk.SNOW_FOREST -> 4;
			case PetaInk.DEEP_OCEAN, PetaInk.OCEAN, PetaInk.WARM_OCEAN, PetaInk.FROZEN_OCEAN, PetaInk.RIVER -> 1;
			case PetaInk.BEACH, PetaInk.SWAMP, PetaInk.MANGROVE -> 2;
			default -> 3;
		};
	}

	private static int fogged(int paper, int ux, int uz) {
		int base = shade(mix(paper, 0xB9B2A0, 0.35D), -0.08D);
		int wisp = sample(fog, 64, Math.floorDiv(ux, 4), Math.floorDiv(uz, 4));
		double a = ((wisp >>> 24) & 0xFF) / 255.0D;
		return mix(base, 0xF2EFE6, a * 0.18D);
	}

	// ================================================================== brush stamps

	private static void stampRow(NativeImage img, int j) {
		int bpp = bakeBpp;
		for (int i = 0; i < gridSize; i++) {
			byte cell = grid[j * gridSize + i];
			if (cell == 0) {
				continue;
			}
			int kind = PetaInk.kind(cell);
			int gx = gridX0 + i;
			int gz = gridZ0 + j;
			int h = hash(gx * 3 + 11, gz * 7 + 5);
			int sprite = -1;
			int every;
			float alpha;
			switch (kind) {
				case PetaInk.MOUNTAIN -> {
					sprite = h & 3;
					every = bpp >= 256 ? 9 : bpp >= 64 ? 5 : 2;
					alpha = 0.85F;
				}
				case PetaInk.SNOW_PEAK -> {
					sprite = 3 + (h & 1);
					every = bpp >= 256 ? 9 : bpp >= 64 ? 5 : 2;
					alpha = 0.8F;
				}
				case PetaInk.HILLS -> {
					sprite = 5 + (h & 1);
					every = bpp >= 256 ? 14 : bpp >= 64 ? 6 : 3;
					alpha = 0.6F;
				}
				case PetaInk.FOREST, PetaInk.BIRCH, PetaInk.FLOWER -> {
					sprite = 8 + (h & 1);
					every = bpp >= 256 ? 0 : bpp >= 64 ? 4 : 2;
					alpha = 0.5F;
				}
				case PetaInk.TAIGA, PetaInk.SNOW_FOREST -> {
					sprite = 10 + (h & 1);
					every = bpp >= 256 ? 0 : bpp >= 64 ? 4 : 2;
					alpha = 0.65F;
				}
				case PetaInk.DARK_FOREST, PetaInk.JUNGLE -> {
					sprite = kind == PetaInk.JUNGLE ? 12 : 9;
					every = bpp >= 256 ? 10 : bpp >= 64 ? 3 : 2;
					alpha = 0.65F;
				}
				case PetaInk.CHERRY -> {
					sprite = 13;
					every = bpp >= 256 ? 0 : bpp >= 64 ? 4 : 2;
					alpha = 0.8F;
				}
				case PetaInk.PALE -> {
					sprite = 14;
					every = bpp >= 256 ? 0 : bpp >= 64 ? 5 : 2;
					alpha = 0.6F;
				}
				case PetaInk.MUSHROOM -> {
					sprite = 15;
					every = bpp >= 256 ? 0 : bpp >= 64 ? 5 : 2;
					alpha = 0.8F;
				}
				case PetaInk.DEEP_OCEAN, PetaInk.OCEAN, PetaInk.WARM_OCEAN -> {
					sprite = 16 + (h & 1);
					every = bpp >= 256 ? 0 : bpp >= 64 ? 9 : 4;
					alpha = 0.28F;
				}
				case PetaInk.DESERT, PetaInk.SAVANNA, PetaInk.BADLANDS -> {
					sprite = 18 + (h & 1);
					every = bpp >= 256 ? 0 : bpp >= 64 ? 6 : 2;
					alpha = 0.32F;
				}
				case PetaInk.SWAMP, PetaInk.MANGROVE -> {
					sprite = 20 + (h & 1);
					every = bpp >= 256 ? 0 : bpp >= 64 ? 5 : 2;
					alpha = 0.55F;
				}
				case PetaInk.SNOWY, PetaInk.FROZEN_OCEAN -> {
					sprite = 22;
					every = bpp >= 256 ? 0 : bpp >= 64 ? 7 : 3;
					alpha = 0.35F;
				}
				case PetaInk.STONY_SHORE -> {
					sprite = 23;
					every = bpp >= 256 ? 0 : bpp >= 64 ? 6 : 2;
					alpha = 0.5F;
				}
				default -> {
					every = 0;
					alpha = 0.0F;
				}
			}
			if (sprite < 0 || every <= 0) {
				continue;
			}
			if (Math.floorMod(gx, every) != Math.floorMod(h >> 4, every) % every
					|| Math.floorMod(gz, every) != Math.floorMod(h >> 9, every) % every) {
				continue;
			}
			// the stamp's foot sits on the cell (jittered a little)
			double wx = (gx + 0.5D) * nodeSize + ((h >> 12 & 0xF) - 7.5D) / 16.0D * nodeSize;
			double wz = (gz + 0.5D) * nodeSize + ((h >> 16 & 0xF) - 7.5D) / 16.0D * nodeSize;
			int px = (int) Math.floor((wx - bakeX) / bpp);
			int py = (int) Math.floor((wz - bakeZ) / bpp);
			stamp(img, sprite, px - 8, py - 13, alpha);
		}
	}

	private static void stamp(NativeImage img, int sprite, int x0, int y0, float alpha) {
		if (brushes == null) {
			return;
		}
		int sx = (sprite % 8) * 16;
		int sy = (sprite / 8) * 16;
		for (int y = 0; y < 16; y++) {
			int py = y0 + y;
			if (py < 0 || py >= SIZE) {
				continue;
			}
			for (int x = 0; x < 16; x++) {
				int px = x0 + x;
				if (px < 0 || px >= SIZE) {
					continue;
				}
				int src = brushes[(sy + y) * 128 + (sx + x)];
				int a = (src >>> 24) & 0xFF;
				if (a < 8) {
					continue;
				}
				double t = a / 255.0D * alpha;
				int dst = img.getPixel(px, py);
				int ink = (src & 0xFFFFFF) == 0 ? INK : (src & 0xFFFFFF);
				img.setPixel(px, py, 0xFF000000 | mix(dst & 0xFFFFFF, ink, t));
			}
		}
	}

	// ================================================================== sources

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		parchment = read(PARCHMENT, 256, 256);
		fog = read(FOG, 64, 64);
		brushes = read(BRUSHES, 128, 64);
		if (parchment == null) {
			parchment = new int[256 * 256];
			java.util.Arrays.fill(parchment, 0xFFE6DAB6);
		}
		if (fog == null) {
			fog = new int[64 * 64];
		}
	}

	/** Forgets the sources (resource reload). */
	static void reloadSources() {
		loaded = false;
	}

	private static @Nullable int[] read(Identifier id, int w, int h) {
		try {
			Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
			if (resource.isEmpty()) {
				return null;
			}
			try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
				if (image.getWidth() != w || image.getHeight() != h) {
					return null;
				}
				int[] out = new int[w * h];
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						out[y * w + x] = image.getPixel(x, y);
					}
				}
				return out;
			}
		} catch (IOException | RuntimeException exception) {
			Kultivator.LOGGER.warn("Kultivator: map texture {} could not be read", id, exception);
			return null;
		}
	}

	private static int sample(int[] src, int size, int x, int y) {
		return src[Math.floorMod(y, size) * size + Math.floorMod(x, size)];
	}

	// ================================================================== maths

	private static double bilinear(boolean a, boolean b, boolean c, boolean d, double tx, double tz) {
		double top = (a ? 1.0D : 0.0D) * (1.0D - tx) + (b ? 1.0D : 0.0D) * tx;
		double bottom = (c ? 1.0D : 0.0D) * (1.0D - tx) + (d ? 1.0D : 0.0D) * tx;
		return top * (1.0D - tz) + bottom * tz;
	}

	private static boolean isWater(byte cell) {
		return cell != 0 && PetaInk.water(PetaInk.kind(cell));
	}

	private static double smooth(double t) {
		return t * t * (3.0D - 2.0D * t);
	}

	static int mix(int a, int b, double t) {
		double k = Math.max(0.0D, Math.min(1.0D, t));
		int r = (int) Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
		int g = (int) Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
		int bl = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
		return r << 16 | g << 8 | bl;
	}

	/** Brightens (amount > 0) or darkens a colour. */
	private static int shade(int rgb, double amount) {
		return amount >= 0.0D ? mix(rgb, 0xFFFFFF, amount) : mix(rgb, 0x000000, -amount);
	}

	private static int hash(int x, int z) {
		int h = x * 0x27D4EB2D ^ z * 0x165667B1;
		h ^= h >>> 15;
		h *= 0x85EBCA6B;
		h ^= h >>> 13;
		return h & 0x7FFFFFFF;
	}

	private PetaPainter() {
	}
}
