package dev.kultivator.technique.spec.dsl;

import dev.kultivator.data.Realm;

import java.util.List;

/** The flow records of DESIGN 22.3.4 (data only; the runner drives them). */
public final class Flow {

	/**
	 * {@code CHG t}: a fixed wind-up with the caster slowed (−90%). Casting again releases early at proportional power
	 * (at least {@code minShare}); taking ≥ {@code interrupt} of max health interrupts it (half the qi back, 25% of the
	 * cooldown set).
	 */
	public record Charge(int ticks, double minShare, double interrupt) {
		public static final double MIN_SHARE = 0.5D;
		public static final double INTERRUPT = 0.2D;
	}

	/**
	 * {@code CHAN t/pulse}: the steps repeat every {@code pulse} ticks for {@code ticks}; moving more than 1.5 blocks,
	 * casting anything else, casting again or a hit of ≥ {@code interrupt} of max health ends it.
	 */
	public record Channel(int ticks, int pulse, double interrupt) {
		public static final double MOVE_LIMIT = 1.5D;
	}

	/** {@code TOG qi/s (start)}: a sustained stance with no movement limit; cast again to end it. */
	public record Toggle(double qiPerSecond, int startQi) {
	}

	/**
	 * {@code ST n (window)}: recasts within {@code window} ticks advance the stage and skip the global cooldown; the
	 * cooldown starts after the last stage or when the window runs out. {@code qi} per stage (empty = the spec's qi) and
	 * a realm gate per stage ({@code realms}, index = stage − 1; {@code Realm.MORTAL} or a missing entry = no gate);
	 * {@code gap}: a recast sooner than this many ticks after the last stage is refused (0 = none).
	 */
	public record Stages(int count, int window, List<Integer> qi, List<Realm> realms, int gap) {
		/** The realm stage {@code stage} (1-based) needs, MORTAL for none. */
		public Realm realmFor(int stage) {
			return stage >= 1 && stage <= realms.size() ? realms.get(stage - 1) : Realm.MORTAL;
		}

		/** Qi of stage {@code stage} (1-based), or {@code fallback} when not listed. */
		public int qiFor(int stage, int fallback) {
			return stage >= 1 && stage <= qi.size() ? qi.get(stage - 1) : fallback;
		}
	}

	/** {@code CHARGES n (recharge)}: n casts before cooling down (b→c grows with N); each charge recharges separately. */
	public record Charges(Val count, int recharge) {
	}

	/**
	 * {@code STACK}: per recast within {@code window} ticks (RECAST) or per kill within the cast (ON_KILL); each stack
	 * multiplies damage by (1 + {@code perStack}) inside F unless the payload counts stacks itself; at most {@code max}.
	 */
	public record Stack(Source source, double perStack, int max, int window) {
		public enum Source { RECAST, ON_KILL }

		public static Stack onKill(double perStack, int max) {
			return new Stack(Source.ON_KILL, perStack, max, 0);
		}

		public static Stack onRecast(double perStack, int max, int window) {
			return new Stack(Source.RECAST, perStack, max, window);
		}
	}

	private Flow() {
	}
}
