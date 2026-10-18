package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Random;

/**
 * Small utility parts for template authors (template-local):
 * <ul>
 * <li>{@link #anchor}: names a standing cell ("post_water", "arrival", "work_gate"...) without placing anything.</li>
 * <li>{@link #demolish}: removes the blocks of an earlier, lighter structure in a box before its replacement is built (the
 * light swaps of DESIGN 21.2 rule 9: wooden gateway → stone paifang); only sect cells are removed (they are refunded),
 * natural blocks and the world are left alone by the engine's own-cell rule.</li>
 * <li>{@link #petals}: pink petals scattered over paving or lawn (the T9 finale).</li>
 * <li>{@link #block}: one block at one cell (a plaque light, a single lantern...).</li>
 * </ul>
 */
public final class Marks {

	/** A named standing cell at (x, z) on {@code level}. */
	/**
	 * A review viewpoint of the template (DESIGN 21.8; {@code uji sekte kamera n}): anchors {@code cam_<view>} (where the
	 * eye stands) and {@code cam_<view>_look} (what it looks at). Both stay inside the footprint column.
	 */
	public static Part camera(int view, int eyeX, int eyeZ, Lv eyeLevel, int lookX, int lookZ, Lv lookLevel) {
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				int y = ctx.level(eyeLevel);
				return new BoundingBox(eyeX, y, eyeZ, eyeX, y, eyeZ);
			}

			@Override
			public String kind() {
				return "anchor";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				sink.anchor("cam_" + view, eyeX, ctx.level(eyeLevel), eyeZ);
				sink.anchor("cam_" + view + "_look", lookX, ctx.level(lookLevel), lookZ);
			}
		};
	}

	public static Part anchor(String id, int x, int z, Lv level) {
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				int y = ctx.level(level);
				return new BoundingBox(x, y, z, x, y, z);
			}

			@Override
			public String kind() {
				return "anchor";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				sink.anchor(id, x, ctx.level(level), z);
			}
		};
	}

	/** Air over a template-local box (absolute y), phase FRAME, top-down: dismantles the earlier structure there. */
	public static Part demolish(int x0, int y0, int z0, int x1, int y1, int z1) {
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				return new BoundingBox(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1));
			}

			@Override
			public String kind() {
				return "demolish";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				Phase previous = sink.phase();
				sink.phase(Phase.FRAME);
				for (int y = Math.max(y0, y1); y >= Math.min(y0, y1); y--) {
					sink.layer(-1000 - y);
					for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
						for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
							sink.demolish(x, y, z);
						}
					}
				}
				sink.layerAuto();
				sink.phase(previous);
			}
		};
	}

	/** Pink petals on about {@code percent}% of the cells of the rectangle (on whatever floor is there). */
	public static Part petals(int x0, int z0, int x1, int z1, Lv level, int percent) {
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				int y = ctx.level(level);
				return new BoundingBox(Math.min(x0, x1), y, Math.min(z0, z1), Math.max(x0, x1), y, Math.max(z0, z1));
			}

			@Override
			public String kind() {
				return "petals";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				Random random = ctx.random("petals" + x0 + "," + z0);
				int y = ctx.level(level);
				Phase previous = sink.phase();
				sink.phase(Phase.GARDEN);
				Direction[] faces = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
				for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
					for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
						if (random.nextInt(100) < percent) {
							sink.set(x, y, z, Palette.petals(Blocks.PINK_PETALS, 1 + random.nextInt(4), faces[random.nextInt(4)]));
						}
					}
				}
				sink.phase(previous);
			}
		};
	}

	/** One block at template-local (x, y, z) in {@code phase}. */
	public static Part block(int x, int y, int z, net.minecraft.world.level.block.state.BlockState state, Phase phase) {
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				return new BoundingBox(x, y, z, x, y, z);
			}

			@Override
			public String kind() {
				return "block";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				Phase previous = sink.phase();
				sink.phase(phase).set(x, y, z, state);
				sink.phase(previous);
			}
		};
	}

	private Marks() {
	}
}
