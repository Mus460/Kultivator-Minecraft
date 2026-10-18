package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * One reusable building block of a component (DESIGN 21.9): a hall, a roof, a pond, a tree, a stretch of paving... A
 * part knows its own template-local position (it pushes its own frame on the {@link Sink} inside {@link #emit}), reads
 * only the immutable {@link Ctx} (never the world: emission runs off the server thread) and is itself immutable after its
 * builder methods ran, so one part may be emitted many times (dry runs, previews, the real build).
 * <p>
 * {@link #decorate} lets a later tier's "Hiasan Tingkat" ornament component add the tier's finery to an already built
 * part (wind bells under the flying tips at T4, gilt at T6, glowing plaques at T9...), so the whole sect looks upgraded,
 * not just one plot (DESIGN 21.2 rule 7). It must only add blocks in cells the part left empty.
 */
public interface Part {

	/** The template-local box the part occupies (approximate; for the hologram, diagrams and overlap checks). */
	BoundingBox bounds(Ctx ctx);

	/** Plans the part's blocks (in its own frame) with the right phases. */
	void emit(Ctx ctx, Sink sink);

	/** Adds the finery of {@code tier} to this already built part (default: nothing). */
	default void decorate(Ctx ctx, Sink sink, int tier) {
	}

	/** A short kind id for the UI and the audit ("hall", "pond", ...). */
	default String kind() {
		return getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
	}

	/** The template-local box of a canonical box (x across, z toward the front) of a part at (x, z) facing {@code front}. */
	static BoundingBox box(int x, int z, Direction front, int cx0, int y0, int cz0, int cx1, int y1, int cz1) {
		int ax;
		int az;
		int bx;
		int bz;
		switch (front) {
			case WEST -> {
				ax = x - cz1;
				bx = x - cz0;
				az = z + cx0;
				bz = z + cx1;
			}
			case NORTH -> {
				ax = x - cx1;
				bx = x - cx0;
				az = z - cz1;
				bz = z - cz0;
			}
			case EAST -> {
				ax = x + cz0;
				bx = x + cz1;
				az = z - cx1;
				bz = z - cx0;
			}
			default -> {
				ax = x + cx0;
				bx = x + cx1;
				az = z + cz0;
				bz = z + cz1;
			}
		}
		return new BoundingBox(Math.min(ax, bx), Math.min(y0, y1), Math.min(az, bz), Math.max(ax, bx), Math.max(y0, y1), Math.max(az, bz));
	}
}
