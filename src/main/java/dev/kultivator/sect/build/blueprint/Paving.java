package dev.kultivator.sect.build.blueprint;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Random;

/**
 * Ground finish of an area (DESIGN 21.9 {@code Paving}, 21.5 "Paving"), template-local: it also clears plants and levels
 * the floor cell lightly (cut up to 3 above, fill up to 6 below), so a path never floats. Paving grows with use (DESIGN
 * 21.2 rule 3): T1 paves the axis and the forecourt, later tiers the rest.
 * <ul>
 * <li>{@link Pattern#PLAZA}: polished andesite with a stone-brick grid every 4 (aligned on the stele).</li>
 * <li>{@link Pattern#AXIS}: smooth stone with a chiseled-stone-brick band every 4th row (the imperial way).</li>
 * <li>{@link Pattern#PATH}: a cheap stone path (stone bricks, some mossy) connecting components.</li>
 * <li>{@link Pattern#STEPPING}: tuff-brick slab stepping stones on moss (garden paths, every other cell).</li>
 * <li>{@link Pattern#LAWN}: grass with a little short grass and the odd flower.</li>
 * <li>{@link Pattern#MOSS}: formal moss beds.</li>
 * <li>{@link Pattern#OCTAGON_INLAY}: a chiseled-tuff-brick octagon ring of radius {@code r} around the area centre (the
 * stele surround of DESIGN 21.7: no roof over the stele).</li>
 * </ul>
 * {@link #ellipse} shapes the area; {@link #skipDisc} leaves the Bagua disc cells to the formation component.
 */
public final class Paving implements Part {

	public enum Pattern {
		PLAZA,
		AXIS,
		PATH,
		STEPPING,
		LAWN,
		MOSS,
		OCTAGON_INLAY
	}

	private final int x0;
	private final int z0;
	private final int x1;
	private final int z1;
	private final Lv level;
	private final Pattern pattern;
	private boolean ellipse;
	private boolean earth = true;
	private int radius = 2;

	public Paving(int x0, int z0, int x1, int z1, Lv level, Pattern pattern) {
		this.x0 = Math.min(x0, x1);
		this.z0 = Math.min(z0, z1);
		this.x1 = Math.max(x0, x1);
		this.z1 = Math.max(z0, z1);
		this.level = level;
		this.pattern = pattern;
	}

	/** Only the ellipse inscribed in the rectangle. */
	public Paving ellipse(boolean ellipse) {
		this.ellipse = ellipse;
		return this;
	}

	/** Light levelling of the floor cell (default on; off when a {@link TerrainZone} already did it). */
	public Paving earth(boolean earth) {
		this.earth = earth;
		return this;
	}

	/** {@link Pattern#OCTAGON_INLAY}: the ring radius. */
	public Paving radius(int radius) {
		this.radius = Math.max(1, radius);
		return this;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = ctx.level(level);
		return new BoundingBox(x0, y - 1, z0, x1, y, z1);
	}

	@Override
	public String kind() {
		return "paving";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		int y = ctx.level(level);
		Random random = ctx.random("paving" + x0 + "," + z0 + pattern);
		double cx = (x0 + x1) / 2.0D;
		double cz = (z0 + z1) / 2.0D;
		double rx = Math.max(0.5D, (x1 - x0) / 2.0D + 0.5D);
		double rz = Math.max(0.5D, (z1 - z0) / 2.0D + 0.5D);
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (ellipse) {
					double ex = (x - cx) / rx;
					double ez = (z - cz) / rz;
					if (ex * ex + ez * ez > 1.0D) {
						continue;
					}
				}
				if (pattern == Pattern.OCTAGON_INLAY) {
					int dx = Math.abs(x - (int) Math.round(cx));
					int dz = Math.abs(z - (int) Math.round(cz));
					int chamfered = Math.max(Math.max(dx, dz), (dx + dz + 1) * 2 / 3);
					if (chamfered == radius) {
						sink.phase(Phase.PLINTH).set(x, y - 1, z, Palette.st(Palette.STONE_FRET));
					}
					continue;
				}
				if (earth) {
					int ground = ctx.terrain().ground(x, z);
					int top = ctx.terrain().top(x, z);
					sink.phase(Phase.CLEAR);
					for (int cy = Math.max(ground + 1, y); cy <= Math.min(top, y + 6); cy++) {
						sink.air(x, cy, z);
					}
					sink.phase(Phase.CUT);
					for (int cy = y; cy <= Math.min(ground, y + 3); cy++) {
						sink.air(x, cy, z);
					}
					sink.phase(Phase.FILL);
					for (int cy = Math.max(ground + 1, y - 7); cy <= y - 2; cy++) {
						sink.set(x, cy, z, Palette.st(Palette.FILL_EARTH));
					}
				}
				sink.phase(Phase.PLINTH);
				BlockState floor;
				BlockState above = null;
				switch (pattern) {
					case PLAZA -> floor = Palette.st(Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0 ? Palette.STONE : Palette.PLINTH);
					case AXIS -> floor = Palette.st(Math.floorMod(z, 4) == 0 ? Palette.AXIS_ACCENT : Palette.AXIS_PAVE);
					case PATH -> floor = Palette.st(random.nextInt(7) == 0 ? Palette.STONE_MOSSY : Palette.STONE);
					case STEPPING -> {
						floor = Palette.st(Palette.MOSS);
						if (Math.floorMod(x + z, 2) == 0) {
							above = Palette.slab(Palette.STEPPING, false);
						}
					}
					case LAWN -> {
						floor = Palette.st(Blocks.GRASS_BLOCK);
						int roll = random.nextInt(20);
						if (roll < 3) {
							above = Palette.st(Blocks.SHORT_GRASS);
						} else if (roll == 3) {
							above = Palette.st(Blocks.LILY_OF_THE_VALLEY);
						}
					}
					case MOSS -> floor = Palette.st(Palette.MOSS);
					default -> floor = Palette.st(Palette.PLINTH);
				}
				sink.set(x, y - 1, z, floor);
				if (above != null) {
					sink.phase(Phase.GARDEN).set(x, y, z, above);
				}
			}
		}
	}
}
