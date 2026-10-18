package dev.kultivator.formasi;

/**
 * Sub-ids ({@code a}) of {@code FxType.FORMASI} (DESIGN 24.13 item 3). Module A owns 0-31; module B adds its own from
 * {@link #B_FIRST} (BOLT, SWORD, SWEEP, FOG, ALARM, JUMP_OUT, JUMP_IN, ROOT, ...) in its own class.
 */
public final class FormasiFxIds {

	/** Validation marker at a slot, sent to one player: b = 0 wrong, 1 missing, 2 ok. */
	public static final int SLOT_MARK = 0;
	/** Hologram start: pos = anchor, b = netId | rot << 12, f = minutes (0 hides). */
	public static final int PATTERN_SHOW = 1;
	/** Pengenalan Darah: blood into the eye. */
	public static final int AWAKEN = 2;
	/** A core changed state: b = state ordinal. */
	public static final int STATE = 3;
	/** One pulse of a running core: b = family ordinal. */
	public static final int PULSE = 4;
	/** A formation hit on a mob. */
	public static final int HIT = 5;
	/** A ripple across a barrier (projectile absorbed, intruder pushed). */
	public static final int RIPPLE = 6;
	/** Tandai: a 10 s light pillar on the eye. */
	public static final int MARK_PILLAR = 7;
	/** The first sub-id module B may use. */
	public static final int B_FIRST = 32;

	private FormasiFxIds() {
	}
}
