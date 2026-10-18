package dev.kultivator.sect.build.blueprint.templates.t4t6;

import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Lv;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Sink;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * The cantilevered deck of a cliff pavilion (template 6's cloud-sea terrace, DESIGN 21.8 "Sky elements"): a dark-oak plank
 * deck jutting out from a cliff over the mist valley on struts, after the Hanging Temple of Mount Heng (art direction, not
 * canon). Under the outer edge runs a log beam; dark-oak log posts stand every 2 cells on it, down to the slope below (the
 * given {@link Surface}, else the natural ground), with fence braces leaning back to the cliff; a mangrove-fence railing
 * closes the open sides. The pavilion itself is a separate {@code Pavilion} part standing on the deck. Template-local
 * rectangle; {@code cliff} is the side that touches the cliff (no railing, no posts).
 */
public final class CliffDeck implements Part {

	/** The design standing level of a column under the deck (a {@code Peak} or a {@link Massif} height). */
	@FunctionalInterface
	public interface Surface {
		int height(Ctx ctx, int x, int z);
	}

	private final int x0;
	private final int z0;
	private final int x1;
	private final int z1;
	private final Lv level;
	private final Direction cliff;
	private final @Nullable Surface peak;

	public CliffDeck(int x0, int z0, int x1, int z1, Lv level, Direction cliff, @Nullable Surface peak) {
		this.x0 = Math.min(x0, x1);
		this.z0 = Math.min(z0, z1);
		this.x1 = Math.max(x0, x1);
		this.z1 = Math.max(z0, z1);
		this.level = level;
		this.cliff = cliff.getAxis().isHorizontal() ? cliff : Direction.EAST;
		this.peak = peak;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = ctx.level(level);
		return new BoundingBox(x0, y - 16, z0, x1, y + 1, z1);
	}

	@Override
	public String kind() {
		return "cliff_deck";
	}

	private int surface(Ctx ctx, int x, int z) {
		int natural = ctx.terrain().ground(x, z);
		if (peak == null) {
			return natural;
		}
		return Math.max(natural, peak.height(ctx, x, z) - 1);
	}

	private boolean onSide(int x, int z, Direction side) {
		return switch (side) {
			case WEST -> x == x0;
			case EAST -> x == x1;
			case NORTH -> z == z0;
			default -> z == z1;
		};
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		int y = ctx.level(level);
		int deck = y - 1;
		sink.phase(Phase.PLINTH);
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				sink.set(x, deck, z, Palette.st(Blocks.DARK_OAK_PLANKS));
				for (int fy = y; fy <= y + 2; fy++) {
					sink.air(x, fy, z);
				}
			}
		}
		Direction out = cliff.getOpposite();
		boolean alongX = out.getAxis() == Direction.Axis.Z;
		sink.phase(Phase.FRAME);
		int index = 0;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (!onSide(x, z, out)) {
					continue;
				}
				// The edge beam under the deck.
				sink.set(x, deck - 1, z, Palette.axis(Blocks.DARK_OAK_LOG, alongX ? Direction.Axis.X : Direction.Axis.Z));
				boolean post = index % 2 == 0 || x == x1 && alongX || z == z1 && !alongX;
				index++;
				if (!post) {
					continue;
				}
				int foot = surface(ctx, x, z);
				for (int fy = foot + 1; fy <= deck - 2; fy++) {
					sink.set(x, fy, z, Palette.axis(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
				}
				// A brace leaning back toward the cliff, two cells under the deck.
				int bx = x + cliff.getStepX();
				int bz = z + cliff.getStepZ();
				if (deck - 3 > surface(ctx, bx, bz)) {
					sink.set(bx, deck - 2, bz, Palette.st(Blocks.DARK_OAK_FENCE));
					sink.set(bx, deck - 3, bz, Palette.st(Blocks.DARK_OAK_FENCE));
				}
			}
		}
		// Railing on the open sides.
		sink.phase(Phase.ORNAMENT);
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				if (!edge || onSide(x, z, cliff)) {
					continue;
				}
				sink.set(x, y, z, Palette.st(Palette.FENCE));
			}
		}
	}
}
