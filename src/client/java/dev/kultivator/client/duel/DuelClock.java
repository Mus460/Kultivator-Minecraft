package dev.kultivator.client.duel;

/**
 * The client's estimate of the server tick (DESIGN 35.8.2, contract C35.U3). Every duel payload that carries a server
 * tick ({@code DuelSessionSync.serverTick}, {@code DuelSwing.startTick}, {@code DuelClash.serverTick}) feeds
 * {@link #observe}; between them the estimate runs on with the client's own ticks:
 * {@code serverTickNow(partial) = lastServerTick + (clientTicks − clientTickAtReceipt) + partial}.
 * <p>
 * The offset {@code serverTick − clientTick} is smoothed with an EMA of 0.2, so one late packet does not jerk the poses;
 * a jump of more than 40 ticks (a server pause, a relog, a dimension change) is not smoothed but taken as is, and the
 * clock counts as unstable until two more samples agree. A {@code DuelSwing.startTick} is the tick the server put on the
 * swing, which lies in the past by up to {@code maxRewindTicks}: it only moves the offset forward, never back, so it can
 * correct a clock that runs late but not drag it behind. Client thread only.
 */
public final class DuelClock {

	/** EMA weight of a new sample. */
	private static final double SMOOTH = 0.2D;
	/** A sample this far from the estimate restarts the clock instead of being smoothed. */
	private static final double JUMP = 40.0D;
	/** Samples that must agree (within 3 ticks) after a restart before the offset counts as stable. */
	private static final int STABLE_AFTER = 2;

	private static long clientTicks;
	private static double offset;
	private static boolean known;
	private static int agreeing;
	private static long lastSampleClientTick = Long.MIN_VALUE;

	/** Once per client tick (from {@code DuelClient}). */
	static void tick() {
		clientTicks++;
	}

	/** The client tick counter this clock runs on (never reset while the game runs). */
	public static long clientTicks() {
		return clientTicks;
	}

	/**
	 * A server tick observed now. {@code exact} is true for ticks the server stamped at sending time (session sync,
	 * clash), false for a swing's start tick (which may lie a few ticks in the past and only pulls the clock forward).
	 */
	public static void observe(long serverTick, boolean exact) {
		double sample = serverTick - (double) clientTicks;
		if (!known) {
			offset = sample;
			known = true;
			agreeing = 0;
			lastSampleClientTick = clientTicks;
			return;
		}
		double error = sample - offset;
		if (Math.abs(error) > JUMP) {
			if (!exact) {
				return;
			}
			offset = sample;
			agreeing = 0;
		} else if (exact) {
			offset += error * SMOOTH;
			if (Math.abs(error) <= 3.0D) {
				agreeing = Math.min(STABLE_AFTER, agreeing + 1);
			} else {
				agreeing = 0;
			}
		} else if (error > 0.0D) {
			offset += error * SMOOTH;
		}
		lastSampleClientTick = clientTicks;
	}

	/** The estimated server tick right now, with the frame's partial tick (0 when nothing was ever received). */
	public static double serverTickNow(float partial) {
		if (!known) {
			return 0.0D;
		}
		return clientTicks + offset + partial;
	}

	/** {@link #serverTickNow} rounded down (the server's whole tick). */
	public static long serverTick() {
		return (long) Math.floor(serverTickNow(0.0F));
	}

	/** True once the offset is known and the last samples agreed (a fresh relog is unstable for ~2 syncs). */
	public static boolean offsetStable() {
		return known && agreeing >= STABLE_AFTER;
	}

	/** True when at least one server tick was received since the last reset. */
	public static boolean known() {
		return known;
	}

	/** Client ticks since the last sample (a stale clock after a long silence is still usable but not trusted). */
	public static long sinceSample() {
		return lastSampleClientTick == Long.MIN_VALUE ? Long.MAX_VALUE : clientTicks - lastSampleClientTick;
	}

	/** On disconnect: the next sample starts the clock afresh. */
	static void reset() {
		known = false;
		offset = 0.0D;
		agreeing = 0;
		lastSampleClientTick = Long.MIN_VALUE;
	}

	private DuelClock() {
	}
}
