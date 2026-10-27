package dev.kultivator.client.sekte.seragam;

import com.mojang.blaze3d.platform.NativeImage;
import dev.kultivator.Kultivator;
import dev.kultivator.sekte.api.Lambang;
import dev.kultivator.sekte.seragam.SeragamData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The hall emblem textures (DESIGN 36.2.8, 36.3.9: at most four halls at a time, plus the designer's preview), composed
 * on the CPU from the greyscale masks of {@code textures/sekte/seragam/lambang_atlas.png} (8 shape fills, 8 shape
 * borders, 32 symbols) tinted with the emblem's three palette colours, then uploaded once into a {@link DynamicTexture}.
 * No shader and no per-frame work: a slot is rebaked only when its hall's design changes.
 * <p>
 * Each slot holds two textures:
 * <ul>
 * <li>{@code kultivator:dynamic/sekte_lambang_<n>} 128×128: the banner cloth on the left (64×128: silk in the field
 * colour with the weave, a border-colour hem and top band stitched with one gold knot per sect generation, flame-tooth
 * edges in the charge colour, the emblem, two hem lines) and the bare emblem at the top right (64×64, decals and the
 * designer).</li>
 * <li>{@code kultivator:dynamic/sekte_jubah_<n>} 64×64 in the robe's player-skin layout: the emblem shrunk onto the back
 * of the body (8×7) and of the skirt (7×7), replacing the old yin-yang medallion of {@code robe_emblem.png}.</li>
 * </ul>
 * Client thread only.
 */
public final class LambangAtlas {

	public static final int HALL_SLOTS = 4;
	public static final int PREVIEW_SLOT = HALL_SLOTS;
	private static final Identifier SOURCE = Kultivator.id("textures/sekte/seragam/lambang_atlas.png");
	private static final Identifier WEAVE = Kultivator.id("textures/sekte/seragam/kain_tenun.png");
	private static final int SRC_W = 512;
	private static final int SRC_H = 384;
	private static final int CELL = 64;
	/** The cloth's emblem: 52 px wide, centred, its top at y 12. */
	private static final int CLOTH_EMBLEM = 52;
	private static final int CLOTH_EMBLEM_Y = 14;

	private static final class Slot {
		final Identifier clothId;
		final Identifier robeId;
		@Nullable DynamicTexture cloth;
		@Nullable DynamicTexture robe;
		@Nullable String key;
		int stamp = -1;
		@Nullable SeragamData baked;
		long used;

		Slot(int index) {
			String name = index == PREVIEW_SLOT ? "pratinjau" : Integer.toString(index);
			this.clothId = Kultivator.id("dynamic/sekte_lambang_" + name);
			this.robeId = Kultivator.id("dynamic/sekte_jubah_" + name);
		}
	}

	private static final Slot[] SLOTS = new Slot[HALL_SLOTS + 1];
	private static @Nullable int[] source;
	private static @Nullable int[] weave;
	private static boolean sourceFailed;
	private static long clock;
	/** Bakes allowed until the next client tick (more halls in view than slots must not rebake every frame). */
	private static int bakeAllowance = 2;
	private static final int BAKES_PER_TICK = 2;

	static {
		for (int i = 0; i < SLOTS.length; i++) {
			SLOTS[i] = new Slot(i);
		}
	}

	// ================================================================== lookups

	/** The cloth/emblem texture of {@code entry}'s hall, baked if needed; null when the masks could not be read. */
	public static @Nullable Identifier cloth(LambangCache.Entry entry) {
		Slot slot = slotFor(entry);
		return slot == null ? null : slot.clothId;
	}

	/** The robe embroidery sheet of {@code entry}'s hall, baked if needed. */
	public static @Nullable Identifier robe(LambangCache.Entry entry) {
		Slot slot = slotFor(entry);
		return slot == null ? null : slot.robeId;
	}

	/** The designer's live preview of {@code data} (rebaked when it changes). */
	public static @Nullable Identifier preview(SeragamData data) {
		Slot slot = SLOTS[PREVIEW_SLOT];
		if (!ensureSource()) {
			return null;
		}
		if (slot.cloth == null || !data.equals(slot.baked)) {
			bake(slot, data);
		}
		return slot.clothId;
	}

	private static @Nullable Slot slotFor(LambangCache.Entry entry) {
		if (!ensureSource()) {
			return null;
		}
		clock++;
		Slot free = null;
		for (int i = 0; i < HALL_SLOTS; i++) {
			Slot slot = SLOTS[i];
			if (entry.key.equals(slot.key)) {
				if (slot.stamp != entry.stamp && bakeAllowance > 0) {
					bakeAllowance--;
					bake(slot, entry.data);
					slot.stamp = entry.stamp;
				}
				slot.used = clock;
				return slot;
			}
			if (free == null || slot.key == null || (free.key != null && slot.used < free.used)) {
				free = slot;
			}
		}
		if (free == null || bakeAllowance <= 0 || (free.key != null && clock - free.used < 2L * HALL_SLOTS)) {
			// no slot to spare right now (it was used this very frame by another hall): draw the old look meanwhile
			return null;
		}
		bakeAllowance--;
		free.key = entry.key;
		free.stamp = entry.stamp;
		free.used = clock;
		bake(free, entry.data);
		return free;
	}

	/** Called every client tick: refills the bake allowance. */
	static void tick() {
		bakeAllowance = BAKES_PER_TICK;
	}

	/** The cache dropped {@code key}: its slot becomes free (the textures stay allocated for the next hall). */
	static void release(String key) {
		for (int i = 0; i < HALL_SLOTS; i++) {
			if (key.equals(SLOTS[i].key)) {
				SLOTS[i].key = null;
				SLOTS[i].stamp = -1;
			}
		}
	}

	/** Frees every texture (disconnect). */
	static void releaseAll() {
		Minecraft mc = Minecraft.getInstance();
		for (Slot slot : SLOTS) {
			try {
				if (slot.cloth != null) {
					mc.getTextureManager().release(slot.clothId);
				}
				if (slot.robe != null) {
					mc.getTextureManager().release(slot.robeId);
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Kultivator: an emblem texture could not be released", exception);
			}
			slot.cloth = null;
			slot.robe = null;
			slot.key = null;
			slot.stamp = -1;
			slot.baked = null;
		}
	}

	/** {@code uji perguruan seragam atlas}: writes every live slot to {@code screenshots/kultivator_lambang_*.png}. */
	static int dump() {
		Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
		int written = 0;
		try {
			Files.createDirectories(dir);
		} catch (IOException exception) {
			return 0;
		}
		for (int i = 0; i < SLOTS.length; i++) {
			Slot slot = SLOTS[i];
			if (slot.cloth == null || slot.robe == null) {
				continue;
			}
			try {
				slot.cloth.getPixels().writeToFile(dir.resolve("kultivator_lambang_" + i + ".png"));
				slot.robe.getPixels().writeToFile(dir.resolve("kultivator_jubah_" + i + ".png"));
				written++;
			} catch (IOException | RuntimeException exception) {
				Kultivator.LOGGER.warn("Kultivator: emblem slot {} could not be written", i, exception);
			}
		}
		return written;
	}

	// ================================================================== baking

	private static boolean ensureSource() {
		if (source != null) {
			return true;
		}
		if (sourceFailed) {
			return false;
		}
		source = read(SOURCE, SRC_W, SRC_H);
		weave = read(WEAVE, 32, 32);
		if (source == null) {
			sourceFailed = true;
			return false;
		}
		return true;
	}

	/** Forgets the masks (resource reload): read again on next use. */
	static void reloadSources() {
		source = null;
		weave = null;
		sourceFailed = false;
		for (Slot slot : SLOTS) {
			slot.stamp = -1;
			slot.baked = null;
		}
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
			Kultivator.LOGGER.warn("Kultivator: emblem texture {} could not be read", id, exception);
			return null;
		}
	}

	private static void bake(Slot slot, SeragamData data) {
		Minecraft mc = Minecraft.getInstance();
		if (slot.cloth == null) {
			slot.cloth = new DynamicTexture(() -> "kultivator sect emblem", 128, 128, true);
			mc.getTextureManager().register(slot.clothId, slot.cloth);
		}
		if (slot.robe == null) {
			slot.robe = new DynamicTexture(() -> "kultivator sect robe emblem", 64, 64, true);
			mc.getTextureManager().register(slot.robeId, slot.robe);
		}
		Lambang emblem = data.emblem();
		int[] clothPixels = new int[128 * 128];
		int[] robePixels = new int[64 * 64];
		paint(data, clothPixels, robePixels);
		copy(clothPixels, slot.cloth.getPixels(), 128, 128);
		slot.cloth.upload();
		copy(robePixels, slot.robe.getPixels(), 64, 64);
		slot.robe.upload();
		slot.baked = data;
	}

	/**
	 * Paints both textures of a design into ARGB arrays: {@code cloth} 128×128 (cloth on the left, bare emblem at the
	 * top right), {@code robe} 64×64 (the embroidery sheet). Pure arithmetic on the masks (the harness checks it).
	 */
	static void paint(SeragamData data, int[] cloth, int[] robe) {
		Lambang emblem = data.emblem();
		paintCloth(cloth, 128, emblem, data.generation());
		int[] bare = new int[CELL * CELL];
		composeEmblem(bare, CELL, 0, 0, CELL, emblem);
		for (int y = 0; y < CELL; y++) {
			System.arraycopy(bare, y * CELL, cloth, y * 128 + 64, CELL);
		}
		// body back face of the base layer (32,20 8x12): rows 0..6, where the old medallion sat
		shrink(bare, robe, 64, 32, 20, 8, 7);
		// skirt back face (19,5 9x9): 7x7 a column in and a row down
		shrink(bare, robe, 64, 20, 6, 7, 7);
	}

	private static void copy(int[] pixels, NativeImage img, int w, int h) {
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				img.setPixel(x, y, pixels[y * w + x]);
			}
		}
	}

	/** The emblem composed into {@code dst} (ARGB, row width {@code dstW}) at (ox, oy), {@code size} px square. */
	static void composeEmblem(int[] dst, int dstW, int ox, int oy, int size, Lambang emblem) {
		int[] src = source;
		if (src == null) {
			return;
		}
		int shapeX = emblem.shape() * CELL;
		int symX = (emblem.symbol() % 8) * CELL;
		int symY = 128 + (emblem.symbol() / 8) * CELL;
		int field = emblem.fieldRgb();
		int charge = emblem.chargeRgb();
		int border = emblem.borderRgb();
		// a symbol in the same colour as the field would vanish: lift it toward white or black
		if (colourDistance(field, charge) < 60) {
			charge = luminance(field) > 140 ? darken(charge, 0.55F) : lighten(charge, 0.6F);
		}
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				float sx = (x + 0.5F) * CELL / size - 0.5F;
				float sy = (y + 0.5F) * CELL / size - 0.5F;
				int fill = sample(src, shapeX, 0, sx, sy);
				int fa = fill >>> 24;
				if (fa == 0) {
					continue;
				}
				int rim = sample(src, shapeX, 64, sx, sy);
				int sym = sample(src, symX, symY, sx, sy);
				int rgb = shade(field, fill & 0xFF);
				int sa = (sym >>> 24) * fa / 255;
				rgb = mix(rgb, shade(charge, sym & 0xFF), sa / 255.0F);
				int ra = rim >>> 24;
				rgb = mix(rgb, shade(border, rim & 0xFF), ra / 255.0F);
				dst[(oy + y) * dstW + ox + x] = fa << 24 | rgb;
			}
		}
	}

	/** The banner cloth (left 64×128 of the slot texture). */
	private static void paintCloth(int[] img, int stride, Lambang emblem, int generation) {
		int field = emblem.fieldRgb();
		int charge = emblem.chargeRgb();
		int border = emblem.borderRgb();
		int[] weaveSrc = weave;
		for (int y = 0; y < 128; y++) {
			for (int x = 0; x < 64; x++) {
				// flame-tooth edges: 6 px teeth along the sides, 8 px along the bottom, in the charge colour
				int side = Math.min(x, 63 - x);
				boolean bottom = y >= 118;
				int toothSide = (y / 6) % 2 == 0 ? (y % 6) : (5 - y % 6);
				if (side < 5 && y > 12) {
					if (side < 5 - toothSide) {
						continue;
					}
					img[(y) * stride + x] = 0xFF000000 | shade(charge, 236 - side * 6);
					continue;
				}
				if (bottom) {
					int t = (x % 8) < 4 ? (x % 8) : (7 - x % 8);
					if (y - 118 > 2 + t * 2) {
						continue;
					}
					img[(y) * stride + x] = 0xFF000000 | shade(charge, 226);
					continue;
				}
				int base = field;
				int grey = weaveSrc == null ? 228 : weaveSrc[(y % 32) * 32 + (x % 32)] & 0xFF;
				// a soft fold every 16 px and the cloth darkening toward the hem
				float fold = 0.92F + 0.08F * (float) Math.cos((x - 8) / 16.0F * Math.PI * 2.0F);
				float hem = 1.0F - 0.12F * y / 128.0F;
				int rgb = shade(base, Math.round(grey * fold * hem));
				// the hem bands: the top band and side hems in the border colour, two hem lines near the foot
				if (y < 12 || side < 8 || y == 104 || y == 105 || y == 109) {
					rgb = shade(border, Math.round((y < 12 ? 236 : 220) * fold));
				}
				img[(y) * stride + x] = 0xFF000000 | rgb;
			}
		}
		// one gold knot per generation of sect masters along the top band (at most nine)
		int knots = Math.max(1, Math.min(9, generation));
		int gold = Lambang.PALETTE[6];
		for (int k = 0; k < knots; k++) {
			int cx = 32 + Math.round((k - (knots - 1) / 2.0F) * 6.0F);
			for (int dy = -2; dy <= 2; dy++) {
				for (int dx = -2; dx <= 2; dx++) {
					if (dx * dx + dy * dy <= 4 && cx + dx >= 0 && cx + dx < 64) {
						img[(6 + dy) * stride + cx + dx] = 0xFF000000 | shade(gold, dx + dy < 0 ? 255 : 210);
					}
				}
			}
		}
		// the emblem
		int[] tmp = new int[CLOTH_EMBLEM * CLOTH_EMBLEM];
		composeEmblem(tmp, CLOTH_EMBLEM, 0, 0, CLOTH_EMBLEM, emblem);
		int ox = (64 - CLOTH_EMBLEM) / 2;
		for (int y = 0; y < CLOTH_EMBLEM; y++) {
			for (int x = 0; x < CLOTH_EMBLEM; x++) {
				int c = tmp[y * CLOTH_EMBLEM + x];
				int a = c >>> 24;
				if (a == 0) {
					continue;
				}
				int px = ox + x;
				int py = CLOTH_EMBLEM_Y + y;
				int under = img[py * stride + px];
				img[(py) * stride + px] = 0xFF000000 | mix(under & 0xFFFFFF, c & 0xFFFFFF, a / 255.0F);
			}
		}
		// a thin charge-colour lozenge chain under the emblem
		for (int x = 12; x < 52; x++) {
			int t = Math.abs((x % 6) - 3);
			for (int y = 84 + t; y <= 90 - t; y++) {
				if (y == 84 + t || y == 90 - t) {
					img[(y) * stride + x] = 0xFF000000 | shade(charge, 220);
				}
			}
		}
	}

	/** Box-filters the 64×64 emblem into a w×h patch of {@code img} at (ox, oy), cut out to opaque pixels. */
	private static void shrink(int[] bare, int[] img, int stride, int ox, int oy, int w, int h) {
		for (int ty = 0; ty < h; ty++) {
			for (int tx = 0; tx < w; tx++) {
				int x0 = tx * CELL / w;
				int x1 = (tx + 1) * CELL / w;
				int y0 = ty * CELL / h;
				int y1 = (ty + 1) * CELL / h;
				float r = 0;
				float g = 0;
				float b = 0;
				float a = 0;
				int n = 0;
				for (int y = y0; y < y1; y++) {
					for (int x = x0; x < x1; x++) {
						int c = bare[y * CELL + x];
						float ca = (c >>> 24) / 255.0F;
						r += (c >> 16 & 0xFF) * ca;
						g += (c >> 8 & 0xFF) * ca;
						b += (c & 0xFF) * ca;
						a += ca;
						n++;
					}
				}
				if (n == 0 || a / n < 0.4F) {
					continue;
				}
				int rgb = Math.round(r / a) << 16 | Math.round(g / a) << 8 | Math.round(b / a);
				img[(oy + ty) * stride + ox + tx] = 0xFF000000 | rgb;
			}
		}
	}

	/** Bilinear sample of the source atlas cell at (cellX, cellY) + (sx, sy). */
	private static int sample(int[] src, int cellX, int cellY, float sx, float sy) {
		int x0 = (int) Math.floor(sx);
		int y0 = (int) Math.floor(sy);
		float fx = sx - x0;
		float fy = sy - y0;
		int c00 = px(src, cellX, cellY, x0, y0);
		int c10 = px(src, cellX, cellY, x0 + 1, y0);
		int c01 = px(src, cellX, cellY, x0, y0 + 1);
		int c11 = px(src, cellX, cellY, x0 + 1, y0 + 1);
		float a = lerp(lerp(c00 >>> 24, c10 >>> 24, fx), lerp(c01 >>> 24, c11 >>> 24, fx), fy);
		// grey weighted by alpha so transparent texels do not darken the edge
		float w00 = (c00 >>> 24) * (1 - fx) * (1 - fy);
		float w10 = (c10 >>> 24) * fx * (1 - fy);
		float w01 = (c01 >>> 24) * (1 - fx) * fy;
		float w11 = (c11 >>> 24) * fx * fy;
		float sum = w00 + w10 + w01 + w11;
		int grey = sum <= 0 ? 0 : Math.round(((c00 & 0xFF) * w00 + (c10 & 0xFF) * w10 + (c01 & 0xFF) * w01 + (c11 & 0xFF) * w11) / sum);
		return Math.round(a) << 24 | grey;
	}

	private static int px(int[] src, int cellX, int cellY, int x, int y) {
		if (x < 0 || y < 0 || x >= CELL || y >= CELL) {
			return 0;
		}
		return src[(cellY + y) * SRC_W + cellX + x];
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	/** {@code rgb} times {@code grey}/255 (a grey of 255 keeps the colour, the masks' sheen brightens a little). */
	static int shade(int rgb, int grey) {
		float k = Math.max(0, Math.min(280, grey)) / 240.0F;
		int r = Math.min(255, Math.round((rgb >> 16 & 0xFF) * k));
		int g = Math.min(255, Math.round((rgb >> 8 & 0xFF) * k));
		int b = Math.min(255, Math.round((rgb & 0xFF) * k));
		return r << 16 | g << 8 | b;
	}

	static int mix(int a, int b, float t) {
		t = Math.max(0.0F, Math.min(1.0F, t));
		int r = Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
		int g = Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return r << 16 | g << 8 | bl;
	}

	private static int colourDistance(int a, int b) {
		int dr = (a >> 16 & 0xFF) - (b >> 16 & 0xFF);
		int dg = (a >> 8 & 0xFF) - (b >> 8 & 0xFF);
		int db = (a & 0xFF) - (b & 0xFF);
		return (int) Math.sqrt(dr * dr + dg * dg + db * db);
	}

	static int luminance(int rgb) {
		return ((rgb >> 16 & 0xFF) * 299 + (rgb >> 8 & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
	}

	private static int lighten(int rgb, float t) {
		return mix(rgb, 0xFFFFFF, t);
	}

	private static int darken(int rgb, float t) {
		return mix(rgb, 0x000000, t);
	}

	private LambangAtlas() {
	}
}
