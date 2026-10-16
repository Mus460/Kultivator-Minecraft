package dev.kultivator.technique;

/**
 * Sub effects ({@code b}) of the {@code FxType.TECHNIQUE} packets of the Dao techniques ({@code a} = technique ordinal).
 * Shared by the server ({@link DaoTechniques}) and the client ({@code DaoTechniqueFx}); never renumber.
 */
public final class DaoTechniqueSub {

	/** The technique's main effect. pos / target / f depend on the technique (see {@code DaoTechniqueFx}). */
	public static final int CAST = 0;
	/** An impact on one monster: pos = its middle, f = size. */
	public static final int HIT = 1;
	/** Thousand Swords: a sword of light falls onto pos within f ticks. */
	public static final int SWORD_FALL = 2;
	/** A particle lightning bolt from pos to target (never a LightningBolt entity). */
	public static final int BOLT = 3;
	/** A lasting effect on the entity with id target.x for target.y ticks; pos = anchor (prison centre), f = size. */
	public static final int MARK = 4;
	/** A lasting effect ends at pos; target.x = entity id whose mark is removed. */
	public static final int END = 5;
	/** Life flowing from pos (a monster) to target (the caster). */
	public static final int DRAIN = 6;
	/** A living being healed at pos; f = its height. */
	public static final int HEAL = 7;
	/** One Dao Domain pulse of the technique's Dao around pos; f = domain radius. */
	public static final int DOMAIN = 8;

	private DaoTechniqueSub() {
	}
}
