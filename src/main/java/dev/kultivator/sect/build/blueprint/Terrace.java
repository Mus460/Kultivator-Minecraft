package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * A raised terrace (DESIGN 21.9 {@code Terrace}, 21.8 "Terrace edges"): a rectangle whose deck stands at {@code top},
 * rising from {@code base} (the level around it), filled solid inside (never hollow), with dressed retaining faces, a
 * coping one cell out (a shadow line), spouts under every post, a fret course, a planting strip at the foot and a
 * balustrade (posts every 3rd cell, a lantern on every ~8th) except at the {@link #opening}s where stairs arrive.
 * Template-local. Style {@link Style#STONE}: stone bricks (some mossy), chiseled tuff fret, andesite coping, stone-brick
 * wall balustrade with chiseled posts. Style {@link Style#MARBLE}: polished diorite face, chiseled quartz fret, smooth
 * quartz deck and coping, pale oak rails between quartz pillars with a quartz slab cap.
 * <p>
 * Faces higher than 4 step back 2 with a planting strip (DESIGN 21.8) when {@link #stepped}.
 */
public final class Terrace implements Part {

	public enum Style {
		STONE,
		MARBLE
	}

	private final int x0;
	private final int z0;
	private final int x1;
	private final int z1;
	private final Lv top;
	private final Lv base;
	private Style style = Style.STONE;
	private boolean balustrade = true;
	private final List<int[]> openings = new ArrayList<>();
	private boolean deck = true;

	public Terrace(int x0, int z0, int x1, int z1, Lv top, Lv base) {
		this.x0 = Math.min(x0, x1);
		this.z0 = Math.min(z0, z1);
		this.x1 = Math.max(x0, x1);
		this.z1 = Math.max(z0, z1);
		this.top = top;
		this.base = base;
	}

	public Terrace style(Style style) {
		this.style = style;
		return this;
	}

	public Terrace balustrade(boolean balustrade) {
		this.balustrade = balustrade;
		return this;
	}

	/** No balustrade within {@code halfWidth} of (x, z) on the edge (a stair arrives there). */
	public Terrace opening(int x, int z, int halfWidth) {
		openings.add(new int[]{x, z, halfWidth});
		return this;
	}

	/** Leave the deck surface to a paving part (default: plain deck). */
	public Terrace deck(boolean deck) {
		this.deck = deck;
		return this;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		return new BoundingBox(x0 - 1, ctx.level(base) - 1, z0 - 1, x1 + 1, ctx.level(top) + 2, z1 + 1);
	}

	@Override
	public String kind() {
		return "terrace";
	}

	private boolean inOpening(int x, int z) {
		for (int[] opening : openings) {
			if (Math.abs(x - opening[0]) <= opening[2] && Math.abs(z - opening[1]) <= opening[2]) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		int deckY = ctx.level(top) - 1;
		int foot = ctx.level(base) - 1;
		boolean marble = style == Style.MARBLE;
		Block face = marble ? Palette.MARBLE_FACE : Palette.STONE;
		Block fret = marble ? Palette.MARBLE_FRET : Palette.STONE_FRET;
		BlockState deckState = Palette.st(marble ? Palette.MARBLE_DECK : Palette.PLINTH);
		int edgeIndex = 0;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				int ground = ctx.terrain().ground(x, z);
				// Clear above, then fill solid from the ground to the deck.
				sink.phase(Phase.CLEAR);
				for (int y = Math.max(ground + 1, deckY + 1); y <= Math.min(ctx.terrain().top(x, z), deckY + 12); y++) {
					sink.air(x, y, z);
				}
				sink.phase(Phase.CUT);
				for (int y = deckY + 1; y <= ground; y++) {
					sink.air(x, y, z);
				}
				sink.phase(Phase.FILL);
				for (int y = Math.max(ground + 1, foot - 6); y < deckY; y++) {
					sink.set(x, y, z, Palette.st(edge ? face : (y < deckY - 3 ? Palette.FILL_STONE : Palette.FILL_EARTH)));
				}
				if (edge) {
					edgeIndex++;
					sink.phase(Phase.PLINTH);
					for (int y = foot; y < deckY; y++) {
						BlockState state;
						if (y == deckY - 3 && deckY - foot >= 4) {
							state = Palette.st(fret);
						} else if (!marble && edgeIndex % 7 == 0) {
							state = Palette.st(Palette.STONE_PILASTER);
						} else if (!marble && (x * 31 + z * 17 + y) % 10 == 0) {
							state = Palette.st(Palette.STONE_MOSSY);
						} else {
							state = Palette.st(face);
						}
						sink.set(x, y, z, state);
					}
				}
				if (deck) {
					sink.phase(Phase.PLINTH);
					sink.set(x, deckY, z, deckState);
				}
			}
		}
		// Coping one out, spouts, the planting strip and the balustrade along the edge.
		int index = 0;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				if (!edge) {
					continue;
				}
				index++;
				Direction out = x == x0 ? Direction.WEST : x == x1 ? Direction.EAST : z == z0 ? Direction.NORTH : Direction.SOUTH;
				int ox = x + out.getStepX();
				int oz = z + out.getStepZ();
				boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
				boolean open = inOpening(x, z);
				sink.phase(Phase.ORNAMENT);
				if (!corner && !open) {
					sink.set(ox, deckY, oz, Palette.slab(marble ? Palette.MARBLE_CAP : Palette.STONE_COPING, true));
				}
				boolean post = index % 3 == 0 || corner;
				if (post && !open && deckY - foot >= 2 && !corner) {
					sink.set(ox, deckY - 1, oz, Palette.stair(marble ? Palette.MARBLE_STAIRS : Palette.STONE_STAIRS, out.getOpposite(), true));
				}
				if (!open && deckY - foot >= 2) {
					sink.phase(Phase.GARDEN);
					int roll = Math.floorMod(x * 13 + z * 7, 6);
					if (roll == 0) {
						sink.setIfAbsent(ox, foot + 1, oz, Palette.st(Blocks.FLOWERING_AZALEA));
					} else if (roll == 1) {
						sink.setIfAbsent(ox, foot + 1, oz, Palette.st(Blocks.FERN));
					}
				}
				if (balustrade && !open) {
					sink.phase(Phase.ORNAMENT);
					if (post) {
						sink.set(x, deckY + 1, z, marble ? Palette.axis(Palette.MARBLE_POST, Direction.Axis.Y) : Palette.st(Palette.STONE_PILASTER));
						if (index % 8 == 0 || corner && index % 16 == 1) {
							sink.set(x, deckY + 2, z, Palette.lantern(Blocks.LANTERN, false));
						} else {
							sink.set(x, deckY + 2, z, Palette.slab(marble ? Palette.MARBLE_CAP : Blocks.STONE_BRICK_SLAB, false));
						}
					} else {
						sink.set(x, deckY + 1, z, Palette.st(marble ? Palette.MARBLE_RAIL : Blocks.STONE_BRICK_WALL));
					}
				}
			}
		}
	}

	public int x0() {
		return x0;
	}

	public int z0() {
		return z0;
	}

	public int x1() {
		return x1;
	}

	public int z1() {
		return z1;
	}
}
