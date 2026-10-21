package dev.kultivator.dunia.peta;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

/**
 * The ink classes of the world map (DESIGN 34.5.16): one byte per map cell, bits 0-4 the class (1-31, 0 = not sampled)
 * and bits 5-7 a height band (1-7, 0 = unknown; known only when the cell's chunk was loaded while it was sampled). Pure
 * data shared by the server (which classifies biomes) and the client (which paints them). No client class.
 */
public final class PetaInk {

	public static final int UNKNOWN = 0;
	public static final int DEEP_OCEAN = 1;
	public static final int OCEAN = 2;
	public static final int WARM_OCEAN = 3;
	public static final int FROZEN_OCEAN = 4;
	public static final int RIVER = 5;
	public static final int BEACH = 6;
	public static final int STONY_SHORE = 7;
	public static final int PLAINS = 8;
	public static final int MEADOW = 9;
	public static final int FLOWER = 10;
	public static final int FOREST = 11;
	public static final int BIRCH = 12;
	public static final int DARK_FOREST = 13;
	public static final int PALE = 14;
	public static final int CHERRY = 15;
	public static final int TAIGA = 16;
	public static final int SNOW_FOREST = 17;
	public static final int SNOWY = 18;
	public static final int SNOW_PEAK = 19;
	public static final int MOUNTAIN = 20;
	public static final int HILLS = 21;
	public static final int JUNGLE = 22;
	public static final int SAVANNA = 23;
	public static final int DESERT = 24;
	public static final int BADLANDS = 25;
	public static final int SWAMP = 26;
	public static final int MANGROVE = 27;
	public static final int MUSHROOM = 28;
	public static final int NETHER = 29;
	public static final int END = 30;
	/** The mod's own spirit lands (Dunia Bawah ash fields, mirror plains, Alam Abadi, the tower's void). */
	public static final int SPIRIT = 31;

	/** The class of a byte. */
	public static int kind(byte cell) {
		return cell & 0x1F;
	}

	/** The height band of a byte (0 unknown, 1 low ... 7 peak). */
	public static int band(byte cell) {
		return (cell >> 5) & 0x07;
	}

	public static byte pack(int kind, int band) {
		return (byte) ((kind & 0x1F) | ((band & 0x07) << 5));
	}

	/** True for water classes (the coastline is drawn between water and land). */
	public static boolean water(int kind) {
		return kind >= DEEP_OCEAN && kind <= RIVER;
	}

	/** The height band of a surface height {@code h} over {@code sea}. */
	public static int bandOf(int h, int sea) {
		int d = h - sea;
		if (d < -1) {
			return 1;
		}
		if (d <= 4) {
			return 2;
		}
		if (d <= 20) {
			return 3;
		}
		if (d <= 45) {
			return 4;
		}
		if (d <= 75) {
			return 5;
		}
		if (d <= 110) {
			return 6;
		}
		return 7;
	}

	/** The ink class of a biome (tags first, then the biome's name, then its temperature). */
	public static int classify(Holder<Biome> biome) {
		String path = biome.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
		String namespace = biome.unwrapKey().map(key -> key.identifier().getNamespace()).orElse(Identifier.DEFAULT_NAMESPACE);
		if (!Identifier.DEFAULT_NAMESPACE.equals(namespace)) {
			if (path.contains("laut") || path.contains("ocean") || path.contains("sea")) {
				return OCEAN;
			}
			if (path.contains("wangchuan") || path.contains("river") || path.contains("sungai")) {
				return RIVER;
			}
			if (path.contains("hutan") || path.contains("forest")) {
				return DARK_FOREST;
			}
		}
		if (biome.is(BiomeTags.IS_NETHER)) {
			return NETHER;
		}
		if (biome.is(BiomeTags.IS_END)) {
			return END;
		}
		if (biome.is(BiomeTags.IS_DEEP_OCEAN)) {
			return path.contains("frozen") ? FROZEN_OCEAN : DEEP_OCEAN;
		}
		if (biome.is(BiomeTags.IS_OCEAN)) {
			if (path.contains("frozen") || path.contains("cold")) {
				return FROZEN_OCEAN;
			}
			return path.contains("warm") || path.contains("lukewarm") ? WARM_OCEAN : OCEAN;
		}
		if (biome.is(BiomeTags.IS_RIVER)) {
			return RIVER;
		}
		if (path.contains("stony_shore")) {
			return STONY_SHORE;
		}
		if (biome.is(BiomeTags.IS_BEACH)) {
			return BEACH;
		}
		if (path.contains("mushroom")) {
			return MUSHROOM;
		}
		if (path.contains("mangrove")) {
			return MANGROVE;
		}
		if (path.contains("swamp")) {
			return SWAMP;
		}
		if (biome.is(BiomeTags.IS_BADLANDS)) {
			return BADLANDS;
		}
		if (path.contains("desert")) {
			return DESERT;
		}
		if (biome.is(BiomeTags.IS_JUNGLE)) {
			return JUNGLE;
		}
		if (biome.is(BiomeTags.IS_SAVANNA)) {
			return SAVANNA;
		}
		if (path.contains("cherry")) {
			return CHERRY;
		}
		if (path.contains("pale")) {
			return PALE;
		}
		if (path.contains("dark_forest")) {
			return DARK_FOREST;
		}
		if (path.contains("frozen_peaks") || path.contains("snowy_slopes") || path.contains("jagged_peaks") || path.contains("ice_spikes")) {
			return SNOW_PEAK;
		}
		if (path.contains("stony_peaks")) {
			return MOUNTAIN;
		}
		if (path.contains("meadow")) {
			return MEADOW;
		}
		if (path.contains("grove") || path.contains("snowy_taiga")) {
			return SNOW_FOREST;
		}
		if (biome.is(BiomeTags.IS_MOUNTAIN)) {
			return MOUNTAIN;
		}
		if (path.contains("windswept") || biome.is(BiomeTags.IS_HILL)) {
			return HILLS;
		}
		if (biome.is(BiomeTags.IS_TAIGA)) {
			return TAIGA;
		}
		if (path.contains("flower")) {
			return FLOWER;
		}
		if (path.contains("birch")) {
			return BIRCH;
		}
		if (biome.is(BiomeTags.IS_FOREST)) {
			return FOREST;
		}
		if (path.contains("snowy")) {
			return SNOWY;
		}
		if (path.contains("plains") || path.contains("sunflower")) {
			return PLAINS;
		}
		if (!Identifier.DEFAULT_NAMESPACE.equals(namespace)) {
			return SPIRIT;
		}
		float t = biome.value().getBaseTemperature();
		if (t < 0.15F) {
			return SNOWY;
		}
		if (t > 1.5F) {
			return DESERT;
		}
		return PLAINS;
	}

	private PetaInk() {
	}
}
