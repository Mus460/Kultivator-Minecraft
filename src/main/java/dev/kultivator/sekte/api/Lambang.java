package dev.kultivator.sekte.api;

/**
 * A sect emblem (DESIGN 36.2.8): an outer shape, a symbol and three colours from {@link #PALETTE}. Packed into one int
 * ({@link #pack()}) for saving and syncing: shape 4 bits, symbol 8 bits, then field, charge and border colour 5 bits
 * each. Module S owns the designer, the textures (shape and symbol atlases) and the banners; module D gives each NPC sect
 * a {@link #seeded} emblem; everyone else only passes the int along. Pure data, safe on both sides.
 */
public record Lambang(int shape, int symbol, int field, int charge, int border) {

	/** Shapes 0-7 (module S's atlas rows): round, square seal, octagon (bagua), lozenge, cloud scroll, flame, shield, banner tongue. */
	public static final int SHAPES = 8;
	/** Symbols 0-31 (module S's atlas cells): sword, lotus, crane, dragon, phoenix, tiger, tortoise, mountain, ... */
	public static final int SYMBOLS = 32;

	/**
	 * The 32 emblem colours (RGB), shared with robes and banners so a 5-bit index is enough: the traditional five colours
	 * first (azure, vermilion, yellow, white, black), then jade, gold, purple and their light and dark steps.
	 */
	public static final int[] PALETTE = {
			0x2F6FB0, 0xC8322B, 0xE2B33C, 0xF2EFE6, 0x1E1E24, 0x3FA37A, 0xD9A53A, 0x6E4AA8,
			0x7FB2E0, 0xE87A6A, 0xF3D98A, 0xFFFFFF, 0x4A4A55, 0x8FD6B5, 0xF5D37A, 0xA98AD8,
			0x173F66, 0x7A1A16, 0x8C6A1C, 0xBDB6A6, 0x0D0D10, 0x1F5C42, 0x8A6420, 0x3C2466,
			0x5A3A22, 0xA0703C, 0xC0C8D0, 0x6A8090, 0xD86A9A, 0x40B0B8, 0xB04A20, 0x90A040};

	/** The emblem of a hall nobody designed yet: a white sword on an azure round seal with a gold border. */
	public static final Lambang DEFAULT = new Lambang(0, 0, 0, 3, 6);

	public Lambang {
		shape = Math.floorMod(shape, SHAPES);
		symbol = Math.floorMod(symbol, SYMBOLS);
		field = Math.floorMod(field, PALETTE.length);
		charge = Math.floorMod(charge, PALETTE.length);
		border = Math.floorMod(border, PALETTE.length);
	}

	public int pack() {
		return shape | symbol << 4 | field << 12 | charge << 17 | border << 22;
	}

	public static Lambang unpack(int packed) {
		return new Lambang(packed & 0xF, packed >>> 4 & 0xFF, packed >>> 12 & 0x1F, packed >>> 17 & 0x1F, packed >>> 22 & 0x1F);
	}

	/** A stable emblem from a seed (module D's NPC sects); field and charge never share a colour. */
	public static Lambang seeded(long seed) {
		long h = seed * 0x9E3779B97F4A7C15L;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		int field = (int) (h >>> 8 & 0x1F);
		int charge = (int) (h >>> 13 & 0x1F);
		if (charge == field) {
			charge = (charge + 11) % PALETTE.length;
		}
		return new Lambang((int) (h & 0x7), (int) (h >>> 3 & 0x1F), field, charge, (int) (h >>> 18 & 0x1F));
	}

	public int fieldRgb() {
		return PALETTE[field];
	}

	public int chargeRgb() {
		return PALETTE[charge];
	}

	public int borderRgb() {
		return PALETTE[border];
	}
}
