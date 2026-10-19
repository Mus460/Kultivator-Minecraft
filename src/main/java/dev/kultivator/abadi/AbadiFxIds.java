package dev.kultivator.abadi;

/**
 * Sub-ids ({@code a}) of {@code FxType.ABADI} (DESIGN 26.14 item 3), owned by P9-langit and drawn by the client
 * {@code client.abadi.AbadiFx} (particles, the {@code SkyDirector} grade and the {@code VortexRenderer}). {@code b} and
 * {@code f} are per id; {@code tx, ty, tz} is a second point where an id says so. Visuals only: no block, no
 * {@code LightningBolt}, no weather or time change.
 */
public final class AbadiFxIds {

	/** The ascension omen: five-colour sky grade and a cloud disc; b = seconds left of the omen, target = the disc centre. */
	public static final int OMEN = 0;
	/** One ascension strike (a client bolt from the sky): b = strike number 1-9, target = the sky point. */
	public static final int STRIKE = 1;
	/** The black hole (RMJ ch. 2451): b = ticks it still lasts, f = the circle radius, target = the vortex centre above. */
	public static final int VORTEX = 2;
	/** The ritual ended: b = 1 passed, 0 failed (the sky grade and the vortex are released). */
	public static final int STOP = 3;
	/** The passing burst at the start point. */
	public static final int PASS = 4;
	/** The crossing talisman's channel: b = ticks of the channel. */
	public static final int CHANNEL = 5;
	/** A crossing arrives (a burst of jade light). */
	public static final int CROSS = 6;
	/** The soft border pushes back: target = the point the player is pushed toward. */
	public static final int BORDER = 7;
	/** The void catch put the player back on solid ground. */
	public static final int VOID = 8;

	private AbadiFxIds() {
	}
}
