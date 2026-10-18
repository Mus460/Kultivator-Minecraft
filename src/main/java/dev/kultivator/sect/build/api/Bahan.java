package dev.kultivator.sect.build.api;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Sembilan Bahan, the nine material categories of the Gudang Bahan Sekte (DESIGN 21.11). Every accepted item belongs to
 * exactly one category; amounts are kept in centi-units ({@code long}, 100 = one unit, about one full block of that
 * material when deposited). The ordinal is saved and sent: never reorder, add new ones at the end only.
 * <p>
 * The names live in the flow's language fragment ({@code lang-src/sectbuild.*}) under {@link #nameKey()}.
 * Categories and numbers are game design (DESIGN 21.11).
 */
public enum Bahan {
	/** Batu / Stone: plinths, terraces, paving, stairs, retaining walls. */
	BATU("batu", 0x9C9C9C),
	/** Tanah / Earth: fill and soil (mostly paid by digging credit). */
	TANAH("tanah", 0x8B6A45),
	/** Kayu / Timber: beams, frames, doors, lattice. */
	KAYU("kayu", 0xA87A4A),
	/** Genteng / Tile: roof tiles, ridges, interior floors. */
	GENTENG("genteng", 0x4B5260),
	/** Kapur / Lime &amp; Plaster: plaster, marble. */
	KAPUR("kapur", 0xECE6D6),
	/** Pernis &amp; Pewarna / Lacquer &amp; Dye: lacquer pillars, palace walls, glaze concrete. */
	PERNIS("pernis", 0xC23B2E),
	/** Flora / Plants: trees, beds, hedges, lotus. */
	FLORA("flora", 0x6FAE4C),
	/** Logam &amp; Pelita / Metal &amp; Lamps: chains, lights, fittings. */
	LOGAM("logam", 0xE0B04A),
	/** Emas &amp; Giok / Gold &amp; Jade: gilt, jade trims and lattice, the Giok and Langit glazes. */
	PERMATA("permata", 0x4FC3A1);

	/** Number of categories (the length of every {@link Bill#centi()} array). */
	public static final int COUNT = 9;
	/** Centi-units per unit. */
	public static final long CENTI = 100L;
	/** The ledger holds at most this many units per category. */
	public static final long MAX_UNITS = 1_000_000L;
	public static final long MAX_CENTI = MAX_UNITS * CENTI;

	private static final Bahan[] VALUES = values();

	private final String id;
	private final int rgb;

	Bahan(String id, int rgb) {
		this.id = id;
		this.rgb = rgb;
	}

	/** The stable id ("batu", "tanah", ...), used as the key in saved data and payload tags. */
	public String id() {
		return id;
	}

	/** The category colour for bill bars and diagrams (RGB). */
	public int rgb() {
		return rgb;
	}

	/** {@code kultivator.sectbuild.bahan.<id>} (defined by the flow module). */
	public String nameKey() {
		return "kultivator.sectbuild.bahan." + id;
	}

	public Component displayName() {
		return Component.translatable(nameKey());
	}

	/** Whole units of a centi amount (rounded down). */
	public static long units(long centi) {
		return Math.floorDiv(centi, CENTI);
	}

	public static @Nullable Bahan byId(@Nullable String id) {
		if (id == null) {
			return null;
		}
		for (Bahan each : VALUES) {
			if (each.id.equals(id)) {
				return each;
			}
		}
		return null;
	}

	public static @Nullable Bahan byOrdinal(int ordinal) {
		return ordinal < 0 || ordinal >= VALUES.length ? null : VALUES[ordinal];
	}
}
