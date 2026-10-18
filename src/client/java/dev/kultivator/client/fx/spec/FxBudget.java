package dev.kultivator.client.fx.spec;

import dev.kultivator.client.EffectQuality;

/**
 * The client particle token bucket (DESIGN 22.15.2): 80 / 300 / 800 tokens per tick by Effect Quality, capacity 2 ticks;
 * a particle costs max(1, lifetime / 20) tokens, so the budget counts particle-seconds. When tokens run short EXTRA
 * layers are dropped first (below half a tick of tokens), then DETAIL is scaled down (it may use only what is left above
 * a CORE reserve); CORE always gets what is there. Client thread only.
 */
public final class FxBudget {

	/** Layer priority (the same order as the spec's {@code FxLayer.Priority}). */
	public enum Priority { CORE, DETAIL, EXTRA }

	/** Tokens per tick at Minimal / Normal / Maximal. */
	public static final int[] PER_TICK = {80, 300, 800};
	/** Particles one TechFx event may draw at Minimal / Normal / Maximal. */
	public static final int[] PER_EVENT = {40, 300, 800};
	/** Template updates per tick (beyond that, the oldest EXTRA layers end). */
	public static final int MAX_UPDATES = 64;

	private static float tokens = PER_TICK[1] * 2.0F;
	private static int spentLastSecond;
	private static int spentThisSecond;
	private static int ticks;

	/** Takes up to {@code tokens} tokens for a layer of {@code priority}; returns the tokens granted. */
	public static int take(int wanted, Priority priority) {
		if (wanted <= 0) {
			return 0;
		}
		int perTick = perTick();
		float available = tokens;
		float usable = switch (priority) {
			case CORE -> available;
			case DETAIL -> available - perTick * 0.15F;
			case EXTRA -> available < perTick * 0.5F ? 0.0F : available - perTick * 0.5F;
		};
		int granted = (int) Math.max(0.0F, Math.min(wanted, usable));
		tokens -= granted;
		spentThisSecond += granted;
		return granted;
	}

	/** Tokens left right now (the HUD's debug line and templates that size themselves). */
	public static int available() {
		return Math.max(0, Math.round(tokens));
	}

	/** Tokens spent in the last full second ({@code debug teknik beban} on the client). */
	public static int spentLastSecond() {
		return spentLastSecond;
	}

	/** Refills the bucket (once per client tick). */
	static void tick() {
		int perTick = perTick();
		tokens = Math.min(perTick * 2.0F, tokens + perTick);
		if (++ticks >= 20) {
			ticks = 0;
			spentLastSecond = spentThisSecond;
			spentThisSecond = 0;
		}
	}

	public static int perTick() {
		return PER_TICK[EffectQuality.level().ordinal()];
	}

	public static int perEvent() {
		return PER_EVENT[EffectQuality.level().ordinal()];
	}

	/** Cost of one particle that lives {@code lifetimeTicks}. */
	public static int cost(int lifetimeTicks) {
		return Math.max(1, lifetimeTicks / 20);
	}

	private FxBudget() {
	}
}
