package dev.kultivator.formasi.effect.b;

import dev.kultivator.formasi.FormasiFxIds;

/**
 * Module B's sub-ids ({@code a}) of {@code FxType.FORMASI} (DESIGN 24.13 item 3), all from {@link FormasiFxIds#B_FIRST}.
 * The client ({@code client/formasi/FormasiFx}) reads the same numbers. Every one is a one-off event; continuous visuals
 * come from {@code FormasiSync}.
 */
public final class FxB {

	/** A particle thunder bolt from the sky onto pos (b = colour rgb, f = height). Never a LightningBolt. */
	public static final int BOLT = FormasiFxIds.B_FIRST;
	/** A phantom sword streak from pos to target (b = colour). */
	public static final int SWORD = FormasiFxIds.B_FIRST + 1;
	/** The seventy-two sword sweep: a ring of swords at pos (f = radius). */
	public static final int SWEEP = FormasiFxIds.B_FIRST + 2;
	/** Mist banks shift and reverse (maze); b = direction (0 / 1), f = radius. */
	public static final int SHIFT = FormasiFxIds.B_FIRST + 3;
	/** Alarm arrow for one member: pos = intruders, target = eye, b = kind | count << 4, f = seconds. */
	public static final int ALARM = FormasiFxIds.B_FIRST + 4;
	/** A traveller dissolves (b = 0 sect transfer, 1 ancient). */
	public static final int JUMP_OUT = FormasiFxIds.B_FIRST + 5;
	/** A traveller arrives (b = 0 sect transfer, 1 ancient). */
	public static final int JUMP_IN = FormasiFxIds.B_FIRST + 6;
	/** Sand shackles on a rooted mob at pos (f = height). */
	public static final int ROOT = FormasiFxIds.B_FIRST + 7;
	/** A channel tick at the source eye (b = ticks done, f = channel length). */
	public static final int CHANNEL = FormasiFxIds.B_FIRST + 8;
	/** A mob turned to ash (visual only) at pos. */
	public static final int ASH = FormasiFxIds.B_FIRST + 9;
	/** Fire on a mob at pos: b = 0 samadhi (blue), 1 air (orange), 2 stone (red), 3 earth vent. */
	public static final int FIRE = FormasiFxIds.B_FIRST + 10;
	/** Red water drips on a mob at pos. */
	public static final int WATER = FormasiFxIds.B_FIRST + 11;
	/** Blade sand whirls on a mob at pos. */
	public static final int SAND = FormasiFxIds.B_FIRST + 12;
	/** Outline an intruder for the owner for 10 s: b = entity id. */
	public static final int OUTLINE = FormasiFxIds.B_FIRST + 13;
	/** Opens the node map on the Formasi tablet: b = core id (sent to one player before the hall screen opens). */
	public static final int NODE_MAP = FormasiFxIds.B_FIRST + 14;
	/** White petals shed by a stripped mob ("the three flowers"). */
	public static final int STRIP = FormasiFxIds.B_FIRST + 15;
	/** A ring of red runes on the zone edge plus a golden sound wave from the bell: pos = crossing point, target = eye. */
	public static final int ALARM_RING = FormasiFxIds.B_FIRST + 16;
	/** A grand array became complete: b = 0 Ten Thousand Immortals, 1 Ten Absolute. */
	public static final int GRAND = FormasiFxIds.B_FIRST + 17;
	/** Thunder rolling between posts or a hit from above without a sky bolt: pos = mob. */
	public static final int THUNDER = FormasiFxIds.B_FIRST + 18;

	private FxB() {
	}
}
