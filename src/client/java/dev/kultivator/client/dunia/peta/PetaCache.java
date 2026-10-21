package dev.kultivator.client.dunia.peta;

import dev.kultivator.dunia.peta.PetaNet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * What this client knows of the world map (DESIGN 34.5.16): the regions the server sent for the current dimension (cell
 * bytes, unexplored already blank), the cell size and the latest marker sheet. Forgotten on disconnect and on a change
 * of dimension. Client thread only.
 */
public final class PetaCache {

	private static String dim = "";
	private static int cellSize = 64;
	private static final Long2ObjectMap<byte[]> REGIONS = new Long2ObjectOpenHashMap<>();
	private static final LongOpenHashSet ASKED = new LongOpenHashSet();
	private static CompoundTag sheet = new CompoundTag();
	private static int version;
	private static int sheetVersion;

	static void onRegion(PetaNet.Region region) {
		if (!region.dim().equals(dim) || region.cellSize() != cellSize) {
			reset(region.dim(), region.cellSize());
		}
		if (region.cells() != null && region.cells().length == 1024) {
			REGIONS.put(key(region.rx(), region.rz()), region.cells());
			version++;
		}
	}

	static void onSheet(CompoundTag tag) {
		String d = tag.getStringOr("dim", dim);
		int cs = tag.getIntOr("cell", cellSize);
		if (!d.equals(dim) || cs != cellSize) {
			reset(d, cs);
		}
		sheet = tag;
		sheetVersion++;
	}

	static void reset(String newDim, int newCell) {
		dim = newDim == null ? "" : newDim;
		cellSize = Math.max(1, newCell);
		REGIONS.clear();
		ASKED.clear();
		version++;
	}

	static void clear() {
		reset("", 64);
		sheet = new CompoundTag();
		sheetVersion++;
	}

	public static long key(int rx, int rz) {
		return ((long) rx << 32) | (rz & 0xFFFFFFFFL);
	}

	public static String dim() {
		return dim;
	}

	public static int cellSize() {
		return cellSize;
	}

	/** Changes whenever a region arrives (the painter repaints). */
	public static int version() {
		return version;
	}

	public static int sheetVersion() {
		return sheetVersion;
	}

	public static CompoundTag sheet() {
		return sheet;
	}

	static @Nullable byte[] region(long key) {
		return REGIONS.get(key);
	}

	/** The byte of cell cx/cz (0 when unknown). */
	public static byte cell(int cx, int cz) {
		byte[] region = REGIONS.get(key(Math.floorDiv(cx, 32), Math.floorDiv(cz, 32)));
		return region == null ? 0 : region[Math.floorMod(cz, 32) * 32 + Math.floorMod(cx, 32)];
	}

	/** True once the region was asked for (asked again only after a reset or a refresh). */
	static boolean asked(long key) {
		return ASKED.contains(key);
	}

	static void markAsked(long key) {
		ASKED.add(key);
	}

	static void forgetAsked() {
		ASKED.clear();
	}

	private PetaCache() {
	}
}
