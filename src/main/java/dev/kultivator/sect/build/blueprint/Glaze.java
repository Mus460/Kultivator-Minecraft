package dev.kultivator.sect.build.blueprint;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/**
 * The three roof glazes a sect can be built in (DESIGN 21.5, 21.12): Tinta (ink: dark tile, the default), Giok (jade
 * field, yellow trim) and Langit (sky blue field, after the Temple of Heaven). The glaze dresses every rank C/D roof (main
 * hall, template 1's Aula Pendiri, ancestral hall, pagodas, Aula Dao Langit); glazed pagodas always use Giok
 * ({@link Rank#roof}). The constants, their order and the ids are part of the 21.24 contract (saved and sent): never
 * reorder. Art direction from real architecture, not novel canon.
 */
public enum Glaze {
	TINTA("tinta"),
	GIOK("giok"),
	LANGIT("langit");

	private static final Glaze[] VALUES = values();

	private final String id;

	Glaze(String id) {
		this.id = id;
	}

	/** "tinta", "giok" or "langit" (also the chat argument of {@code pratinjau}). */
	public String id() {
		return id;
	}

	/** {@code kultivator.blueprint.glaze.<id>} (defined by the blueprint module). */
	public String nameKey() {
		return "kultivator.blueprint.glaze." + id;
	}

	public Component displayName() {
		return Component.translatable(nameKey());
	}

	/**
	 * The roof materials of this glaze (field, trim, ridge, cap), DESIGN 21.5 "Glazes". Tinta keeps the grey field with a
	 * yellow-glazed border (灰瓦黄剪边), so its rank C/D roofs outrank the rank B jade border and the hierarchy reads in the
	 * default glaze too.
	 */
	public RoofMat roof() {
		return switch (this) {
			case TINTA -> new RoofMat(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILES,
					Palette.concreteStairs(DyeColor.YELLOW), Palette.concreteSlab(DyeColor.YELLOW), Palette.concrete(DyeColor.YELLOW),
					Blocks.DEEPSLATE_BRICKS, Palette.slab(Palette.concreteSlab(DyeColor.YELLOW), false));
			case GIOK -> new RoofMat(Palette.jadeStairs(), Palette.jadeSlab(), Palette.jadeBlock(),
					Palette.concreteStairs(DyeColor.YELLOW), Palette.concreteSlab(DyeColor.YELLOW), Palette.concrete(DyeColor.YELLOW),
					Palette.jadeBlock(), Palette.slab(Palette.concreteSlab(DyeColor.YELLOW), false));
			case LANGIT -> new RoofMat(Palette.concreteStairs(DyeColor.BLUE), Palette.concreteSlab(DyeColor.BLUE), Palette.concrete(DyeColor.BLUE),
					Palette.concreteStairs(DyeColor.YELLOW), Palette.concreteSlab(DyeColor.YELLOW), Palette.concrete(DyeColor.YELLOW),
					Blocks.LAPIS_BLOCK, Palette.slab(Palette.concreteSlab(DyeColor.YELLOW), false));
		};
	}

	/** The colour of the roof-beast candles (black, blue in Langit). */
	public Block beastCandle() {
		return Palette.candle(this == LANGIT ? DyeColor.BLUE : DyeColor.BLACK);
	}

	/** The glaze of an id, or null. */
	public static @Nullable Glaze byId(@Nullable String id) {
		for (Glaze each : VALUES) {
			if (each.id.equals(id)) {
				return each;
			}
		}
		return null;
	}

	/** The glaze of an ordinal (Tinta when out of range). */
	public static Glaze byOrdinal(int ordinal) {
		return ordinal < 0 || ordinal >= VALUES.length ? TINTA : VALUES[ordinal];
	}
}
