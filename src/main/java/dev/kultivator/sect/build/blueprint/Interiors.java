package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Interior presets of halls (DESIGN 21.9 "Interior presets"), drawn in the hall's canonical frame (door toward +z) inside
 * the interior rectangle {@code x0..x1 × z0..z1} with the floor at y = -1 (people stand at y = 0). Furniture never
 * overwrites what the hall already planned (pillars, ladders): it uses {@link Sink#setIfAbsent}.
 * <ul>
 * <li>{@link Preset#MAIN_HALL}: throne dais, gilt screen, incense table with red candles (the hall hangs the palace
 * lanterns).</li>
 * <li>{@link Preset#LIBRARY}: exactly {@code count} bookshelves along the back and side walls, lecterns facing the door,
 * reading tables; anchor "library".</li>
 * <li>{@link Preset#DORM}: kang beds of dark-oak slabs with red carpet (no bed block entities).</li>
 * <li>{@link Preset#KITCHEN}: smoker, open campfire hearth, barrel, water vats; never a cauldron on a fire (that reads as
 * an alchemy furnace); anchor "post_kitchen".</li>
 * <li>{@link Preset#ALCHEMY}: {@code count} cauldrons on lit soul campfires (working {@code AlchemyFurnace}s) under jade
 * copper-grate hoods, brewing stands; anchor "alchemy".</li>
 * <li>{@link Preset#ANCESTRAL}: stepped tablet altar, soul lanterns, lit red candles.</li>
 * <li>{@link Preset#MEDITATION}: a {@link QiSeat} of tier {@code count} in the middle; anchor "meditation".</li>
 * <li>{@link Preset#STORE}: decorative empty barrels; anchor "treasury".</li>
 * </ul>
 */
public final class Interiors {

	public enum Preset {
		EMPTY,
		MAIN_HALL,
		LIBRARY,
		DORM,
		KITCHEN,
		ALCHEMY,
		ANCESTRAL,
		MEDITATION,
		STORE
	}

	/** Bookshelves of a library when no count is given. */
	public static final int DEFAULT_SHELVES = 10;

	/** Draws {@code preset} into the interior rectangle; {@code count} as documented per preset (0 = default). */
	public static void emit(Preset preset, int count, Ctx ctx, Sink sink, int x0, int x1, int z0, int z1, int height, Rank rank) {
		if (preset == null || preset == Preset.EMPTY || x1 < x0 || z1 < z0) {
			return;
		}
		Phase previous = sink.phase();
		sink.phase(Phase.INTERIOR);
		switch (preset) {
			case MAIN_HALL -> mainHall(sink, x0, x1, z0, z1);
			case LIBRARY -> library(sink, x0, x1, z0, z1, count > 0 ? count : DEFAULT_SHELVES, height);
			case DORM -> dorm(sink, x0, x1, z0, z1);
			case KITCHEN -> kitchen(sink, x0, x1, z0, z1);
			case ALCHEMY -> alchemy(sink, x0, x1, z0, z1, Math.max(1, count));
			case ANCESTRAL -> ancestral(sink, x0, x1, z0, z1);
			case MEDITATION -> {
				int cz = (z0 + z1) / 2;
				QiSeat.emitAt(sink, 0, 0, cz, Math.max(1, Math.min(3, count)));
				sink.anchor("meditation", 0, 0, cz + 1);
			}
			case STORE -> {
				for (int x = x0; x <= x1; x += 2) {
					sink.setIfAbsent(x, 0, z0, Palette.barrel(Direction.UP));
				}
				sink.anchor("treasury", 0, 0, (z0 + z1) / 2);
			}
			default -> {
			}
		}
		sink.phase(previous);
	}

	private static void mainHall(Sink sink, int x0, int x1, int z0, int z1) {
		int half = Math.min(2, (x1 - x0) / 2);
		// Gilt screen against the back wall: yellow glazed cloud panels with a gold crown.
		for (int x = -half; x <= half; x++) {
			for (int y = 0; y <= 2; y++) {
				sink.setIfAbsent(x, y, z0, Palette.st(Palette.glazed(DyeColor.YELLOW)));
			}
		}
		sink.setIfAbsent(0, 3, z0, Palette.st(Palette.GOLD));
		// Throne dais: one step of polished deepslate slabs, the seat a quartz stair facing the door.
		if (z1 - z0 >= 4) {
			for (int x = -half; x <= half; x++) {
				sink.set(x, 0, z0 + 1, Palette.slab(Blocks.POLISHED_DEEPSLATE_SLAB, false));
				sink.set(x, 0, z0 + 2, Palette.slab(Blocks.POLISHED_DEEPSLATE_SLAB, false));
			}
			sink.set(0, 0, z0 + 1, Palette.st(Blocks.POLISHED_DEEPSLATE));
			sink.set(0, 1, z0 + 1, Palette.stair(Blocks.SMOOTH_QUARTZ_STAIRS, Direction.NORTH, false));
			sink.set(-1, 1, z0 + 1, Palette.slab(Blocks.SMOOTH_QUARTZ_SLAB, false));
			sink.set(1, 1, z0 + 1, Palette.slab(Blocks.SMOOTH_QUARTZ_SLAB, false));
		}
		// Incense table with lit red candles before the dais.
		if (z1 - z0 >= 6) {
			int tz = z0 + 4;
			sink.setIfAbsent(-1, 0, tz, Palette.stair(Blocks.DARK_OAK_STAIRS, Direction.WEST, true));
			sink.setIfAbsent(0, 0, tz, Palette.slab(Blocks.DARK_OAK_SLAB, true));
			sink.setIfAbsent(1, 0, tz, Palette.stair(Blocks.DARK_OAK_STAIRS, Direction.EAST, true));
			sink.setIfAbsent(0, 1, tz, Palette.candles(Palette.candle(DyeColor.RED), 3, true));
		}
		sink.anchor("main_hall", 0, 0, Math.min(z1, z0 + 6));
	}

	private static void library(Sink sink, int x0, int x1, int z0, int z1, int shelves, int height) {
		int left = shelves;
		int rows = Math.max(1, Math.min(3, height - 2));
		// A two-storey hall's ladder stands in the back east corner: the cells beside it stay free so it can be climbed.
		BlockState corner = sink.get(x1, 0, z0);
		boolean ladder = corner != null && corner.is(Blocks.LADDER);
		// Back wall first, then both side walls (never in the door row), one row at a time.
		for (int y = 0; y < rows && left > 0; y++) {
			for (int x = x0; x <= x1 && left > 0; x++) {
				if (ladder && x == x1 - 1) {
					continue;
				}
				if (sink.get(x, y, z0) == null) {
					sink.set(x, y, z0, Palette.st(Blocks.BOOKSHELF));
					left--;
				}
			}
			for (int z = z0 + 1; z <= z1 - 1 && left > 0; z++) {
				for (int x : new int[]{x0, x1}) {
					if (ladder && x == x1 && z == z0 + 1) {
						continue;
					}
					if (left > 0 && sink.get(x, y, z) == null) {
						sink.set(x, y, z, Palette.st(Blocks.BOOKSHELF));
						left--;
					}
				}
			}
		}
		// Shelf islands in the middle when the walls are full.
		for (int z = z0 + 2; z <= z1 - 2 && left > 0; z += 3) {
			for (int x = x0 + 2; x <= x1 - 2 && left > 0; x++) {
				if (x == 0) {
					continue;
				}
				if (sink.get(x, 0, z) == null) {
					sink.set(x, 0, z, Palette.st(Blocks.BOOKSHELF));
					left--;
				}
			}
		}
		int lz = Math.min(z1, z0 + 2);
		sink.setIfAbsent(0, 0, lz, Palette.facing(Blocks.LECTERN, Direction.SOUTH));
		if (z1 - z0 >= 4 && x1 - x0 >= 4) {
			int tz = Math.min(z1 - 1, z0 + 4);
			sink.setIfAbsent(-2, 0, tz, Palette.slab(Blocks.DARK_OAK_SLAB, true));
			sink.setIfAbsent(2, 0, tz, Palette.slab(Blocks.DARK_OAK_SLAB, true));
		}
		sink.anchor("library", 0, 0, Math.min(z1, lz + 1));
	}

	/** A second lectern for templates 5-9 (exact item count 2). */
	public static void secondLectern(Sink sink, int x, int z) {
		sink.setIfAbsent(x, 0, z, Palette.facing(Blocks.LECTERN, Direction.SOUTH));
	}

	private static void dorm(Sink sink, int x0, int x1, int z0, int z1) {
		boolean alongZ = z1 - z0 >= x1 - x0;
		if (alongZ) {
			for (int z = z0; z <= z1 - 1; z++) {
				for (int x : new int[]{x0, x1}) {
					sink.setIfAbsent(x, 0, z, Palette.slab(Blocks.DARK_OAK_SLAB, true));
					sink.setIfAbsent(x, 1, z, Palette.st(Palette.carpet(DyeColor.RED)));
				}
			}
		} else {
			for (int x = x0; x <= x1; x++) {
				if (x == 0) {
					continue;
				}
				sink.setIfAbsent(x, 0, z0, Palette.slab(Blocks.DARK_OAK_SLAB, true));
				sink.setIfAbsent(x, 1, z0, Palette.st(Palette.carpet(DyeColor.RED)));
			}
		}
		sink.setIfAbsent(x0, 0, z1, Palette.barrel(Direction.UP));
	}

	private static void kitchen(Sink sink, int x0, int x1, int z0, int z1) {
		int cx = (x0 + x1) / 2;
		sink.setIfAbsent(cx - 1, 0, z0, Palette.facing(Blocks.SMOKER, Direction.SOUTH));
		sink.setIfAbsent(cx + 1, 0, z0, Palette.campfire(Blocks.CAMPFIRE, false));
		sink.setIfAbsent(x1, 0, z0, Palette.barrel(Direction.UP));
		sink.setIfAbsent(x0, 0, z0, Palette.waterVat());
		if (z1 > z0 + 1) {
			sink.setIfAbsent(x0, 0, z0 + 1, Palette.waterVat());
		}
		sink.anchor("post_kitchen", cx, 0, Math.min(z1, z0 + 1));
	}

	private static void alchemy(Sink sink, int x0, int x1, int z0, int z1, int furnaces) {
		int span = x1 - x0;
		for (int i = 0; i < furnaces; i++) {
			int fx = furnaces == 1 ? 0 : x0 + 1 + i * Math.max(2, span - 2);
			int fz = z0 + Math.min(1, z1 - z0);
			sink.set(fx, 0, fz, Palette.campfire(Blocks.SOUL_CAMPFIRE, false));
			sink.set(fx, 1, fz, Palette.st(Blocks.CAULDRON));
			sink.set(fx, 2, fz, Palette.air());
			sink.set(fx, 3, fz, Palette.st(Palette.jade(Blocks.COPPER_GRATE)));
			sink.setIfAbsent(fx - 1, 3, fz, Palette.slab(Palette.jadeSlab(), true));
			sink.setIfAbsent(fx + 1, 3, fz, Palette.slab(Palette.jadeSlab(), true));
			if (i == 0) {
				sink.anchor("alchemy", fx, 0, Math.min(z1, fz + 2));
			}
		}
		sink.setIfAbsent(x0, 0, z0, Palette.st(Blocks.BREWING_STAND));
	}

	/** Extra brewing stands (exact items: templates 4-9 need 2 or 3). */
	public static void brewingStand(Sink sink, int x, int z) {
		sink.setIfAbsent(x, 0, z, Palette.st(Blocks.BREWING_STAND));
	}

	private static void ancestral(Sink sink, int x0, int x1, int z0, int z1) {
		int half = Math.min(3, (x1 - x0) / 2);
		// Three rising steps of tablets toward the back wall, soul lanterns and lit candles.
		for (int x = -half; x <= half; x++) {
			sink.setIfAbsent(x, 0, z0, Palette.st(Blocks.POLISHED_BLACKSTONE));
			sink.setIfAbsent(x, 1, z0, Palette.slab(Blocks.POLISHED_BLACKSTONE_SLAB, false));
			if (z1 > z0) {
				sink.setIfAbsent(x, 0, z0 + 1, Palette.slab(Blocks.POLISHED_BLACKSTONE_SLAB, true));
			}
		}
		for (int x = -half; x <= half; x += 2) {
			sink.setIfAbsent(x, 2, z0, Palette.lantern(Blocks.SOUL_LANTERN, false));
		}
		if (z1 > z0) {
			sink.setIfAbsent(-1, 1, z0 + 1, Palette.candles(Palette.candle(DyeColor.RED), 2, true));
			sink.setIfAbsent(1, 1, z0 + 1, Palette.candles(Palette.candle(DyeColor.RED), 2, true));
		}
	}

	private Interiors() {
	}
}
