package dev.kultivator.desa;

/** Sub-ids ({@code a}) of {@code FxType.DESA} (DESIGN 25.2 item 4), owned by P8-desa. */
public final class DesaFxIds {

	/** A village swore allegiance at its plaque: gold rings and a rising seal. */
	public static final int OATH = 0;
	/** A plaque was placed near a bell: a small jade ring. */
	public static final int PLAQUE = 1;
	/** A bandit camp's smoke column (sent every 2 s while the owner is within 64 blocks). */
	public static final int CAMP_SMOKE = 2;
	/** A help request was finished: green motes over the plaque. */
	public static final int REQUEST_DONE = 3;
	/**
	 * A toast for the receiving player (sent with {@code Fx.sendTo}): {@code b} 0-4 = a new request of
	 * {@code Village.REQUESTS[b]}, 10 + kind = a wave event began at a plaque.
	 */
	public static final int TOAST = 4;

	private DesaFxIds() {
	}
}
