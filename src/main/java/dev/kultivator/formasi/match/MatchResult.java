package dev.kultivator.formasi.match;

import dev.kultivator.formasi.spec.FormationSpec;

/**
 * A full read of one spec at one eye in one rotation (DESIGN 24.2.3). {@code states} follows
 * {@code spec.pattern().slots(rot)} with the {@link ChunkReader} codes.
 */
public record MatchResult(FormationSpec spec, int rot, int total, int ok, int missing, int wrong, int unknown, byte[] states) {

	/** Every slot read OK. */
	public boolean complete() {
		return ok == total;
	}

	/** Missing or wrong slots. */
	public int bad() {
		return missing + wrong;
	}
}
