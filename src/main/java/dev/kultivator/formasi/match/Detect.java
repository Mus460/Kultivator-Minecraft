package dev.kultivator.formasi.match;

import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.spec.Flag;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formasi.spec.Grade;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Detection when a player wakes an eye (DESIGN 24.2.3): the eye's target spec only, or every spec the player may register
 * there ordered by grade then slot count (both descending), each rotation stopping at its first wrong slot. At most
 * {@link #DETECT_READS} reads per click. The first full match the hall allows wins; a full match that needs a higher hall
 * becomes the hint; otherwise the best partial is read in full for the report. Nothing is registered or consumed here.
 */
public final class Detect {

	public static final int DETECT_READS = 2048;
	public static final int COOLDOWN_TICKS = 20;

	/** The result of one detection. */
	public record Outcome(@Nullable MatchResult match, @Nullable MatchResult hint, @Nullable MatchResult partial, boolean overBudget) {
	}

	public static Outcome detect(ServerLevel level, BlockPos eye, @Nullable String target, int hallLevel, boolean personal) {
		List<FormationSpec> candidates = new ArrayList<>();
		FormationSpec targeted = target == null ? null : FormationRegistry.byId(target);
		if (targeted != null && !targeted.has(Flag.LEGACY_MIRROR)) {
			candidates.add(targeted);
		} else {
			for (FormationSpec spec : FormationRegistry.all()) {
				if (spec.has(Flag.LEGACY_MIRROR) || spec.pattern().size() == 0) {
					continue;
				}
				if (personal && (!spec.has(Flag.PERSONAL_OK) || spec.grade().ordinal() > Grade.MISTERIUS.ordinal())) {
					continue;
				}
				candidates.add(spec);
			}
			candidates.sort(Comparator.comparingInt((FormationSpec s) -> -s.grade().ordinal()).thenComparingInt(s -> -s.pattern().size()));
		}
		ChunkReader reader = new ChunkReader(level);
		int[] budget = {DETECT_READS};
		MatchResult hint = null;
		FormationSpec bestSpec = null;
		int bestRot = 0;
		double bestScore = -1.0D;
		boolean over = false;
		outer:
		for (FormationSpec spec : candidates) {
			int rotations = PatternMatcher.rotations(spec);
			for (int rot = 0; rot < rotations; rot++) {
				int ok = PatternMatcher.quick(reader, eye, spec, rot, budget);
				if (ok < 0) {
					over = true;
					break outer;
				}
				int total = spec.pattern().size();
				if (ok == total) {
					MatchResult full = PatternMatcher.full(reader, eye, spec, rot);
					if (!full.complete()) {
						continue;
					}
					if (spec.minHallLevel() <= hallLevel) {
						return new Outcome(full, hint, null, false);
					}
					if (hint == null) {
						hint = full;
					}
					continue;
				}
				double score = ok + ok / (double) Math.max(1, total);
				if (score > bestScore) {
					bestScore = score;
					bestSpec = spec;
					bestRot = rot;
				}
			}
		}
		MatchResult partial = null;
		if (hint == null && bestSpec != null) {
			partial = PatternMatcher.full(reader, eye, bestSpec, bestRot);
			// Among the rotations of the best spec, report the one with the most OK slots.
			for (int rot = 0; rot < PatternMatcher.rotations(bestSpec); rot++) {
				if (rot != bestRot && reader.reads() < DETECT_READS + 4 * 160) {
					MatchResult other = PatternMatcher.full(reader, eye, bestSpec, rot);
					if (other.ok() > partial.ok()) {
						partial = other;
					}
				}
			}
		}
		return new Outcome(null, hint, partial, over);
	}

	private Detect() {
	}
}
