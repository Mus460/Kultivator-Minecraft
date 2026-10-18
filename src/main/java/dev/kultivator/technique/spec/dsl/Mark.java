package dev.kultivator.technique.spec.dsl;

import dev.kultivator.data.Dao;
import org.jetbrains.annotations.Nullable;

/**
 * A mark on a hostile (DESIGN 22.3.2 MARK): {@code VULNERABLE +x%} (at most 3 stacks from one caster),
 * {@code DAO_VULN(dao, +x%)}, {@code WET} (also true in water or rain), {@code SOUL ×k} (the caster's next hit) and
 * {@code HIGHLIGHT} (seen only by the caster, never GLOWING). Marks live in memory only.
 */
public record Mark(Kind kind, double value, @Nullable Dao dao, int maxStacks, double perTier, double cap, double bossFactor) {

	public enum Kind { VULNERABLE, DAO_VULN, WET, SOUL, HIGHLIGHT }

	public static final int VULNERABLE_STACKS = 3;

	/** Damage taken from this caster +{@code share} (0.2 = +20%), up to 3 stacks. */
	public static Mark vulnerable(double share) {
		return new Mark(Kind.VULNERABLE, share, null, VULNERABLE_STACKS, 0.0D, share, 1.0D);
	}

	/** Damage taken from {@code dao}'s techniques +{@code share}. */
	public static Mark daoVuln(Dao dao, double share) {
		return new Mark(Kind.DAO_VULN, share, dao, 1, 0.0D, share, 1.0D);
	}

	public static Mark wet() {
		return new Mark(Kind.WET, 0.0D, null, 1, 0.0D, 0.0D, 1.0D);
	}

	/** The caster's next hit on the target deals ×{@code factor}. */
	public static Mark soul(double factor) {
		return new Mark(Kind.SOUL, factor, null, 1, 0.0D, factor, 1.0D);
	}

	/** A highlight only the caster sees (VIEWER_MARK; never GLOWING). */
	public static Mark highlight() {
		return new Mark(Kind.HIGHLIGHT, 0.0D, null, 1, 0.0D, 0.0D, 1.0D);
	}

	/** +{@code step} per tier above the gate, at most {@code max} ("+20% (+5%/tier, ≤ +40%)"). */
	public Mark perTier(double step, double max) {
		return new Mark(kind, value, dao, maxStacks, step, max, bossFactor);
	}

	/** Bosses get this share of the value ("bosses: half values"). */
	public Mark boss(double factor) {
		return new Mark(kind, value, dao, maxStacks, perTier, cap, factor);
	}
}
