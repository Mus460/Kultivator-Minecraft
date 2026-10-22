package dev.kultivator.dunia.reruntuhan;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Ids of the client effects of module R (sent as {@code op: "fx"} by {@link RNet#fx}; drawn by
 * {@code client.dunia.reruntuhan.RFxClient}, every one budgeted by EffectQuality and EpicBudget, no post chain). The
 * server only names the moment; it never places a block for it.
 */
public final class RFx {

	public static final int DOOR_OPEN = 1;
	public static final int BACKLASH = 2;
	public static final int PLATE_LIT = 3;
	public static final int LAMP_LIT = 4;
	public static final int RING_TURN = 5;
	public static final int SOLVED = 6;
	public static final int PULSE_WARN = 7;
	public static final int PULSE_HIT = 8;
	public static final int GUARDIAN_RISE = 9;
	public static final int SLAM = 10;
	public static final int TELEGRAPH = 11;
	public static final int LINE = 12;
	public static final int SEAL_BREAK = 13;
	public static final int SEAL_SEALED = 14;
	public static final int TALISMAN_TORN = 15;
	public static final int PILLAR_LIT = 16;
	public static final int KING_SKILL = 17;
	public static final int TOWER_CLEAR = 18;
	public static final int TOWER_MILESTONE = 19;
	public static final int QI_WAVE = 20;
	public static final int INHERIT = 21;
	public static final int MIRROR_HEAL = 22;
	public static final int BAND_GATE = 23;
	public static final int SOUL_APPEAR = 24;
	public static final int GUARDIAN_DOWN = 25;
	public static final int SEAL_LEAK = 26;
	public static final int SKILL_HIT = 27;
	/**
	 * The air of a dressed puzzle room (detail pass), re-sent every 2.5 s while someone is near: a = the room's look
	 * (1 library: drifting dust motes in the lamplight, 2 shrine: incense from the four braziers, 3 garden grotto:
	 * falling petals and leaves), b = the tier. The client emits for 3 s after each pulse.
	 */
	public static final int ROOM_AIR = 28;

	/** Radius of ordinary effects (players beyond it never get them). */
	public static final double RANGE = 64.0D;

	public static void at(ServerLevel level, Vec3 at, int kind, int a, int b, float f) {
		RNet.fx(level, at, RANGE, kind, a, b, f);
	}

	/** An effect with a second point (lines, charges): {@code to} rides in x2/y2/z2. */
	public static void line(ServerLevel level, Vec3 from, Vec3 to, int kind, int a, int b, float f) {
		CompoundTag tag = new CompoundTag();
		tag.putString("op", "fx");
		tag.putInt("k", kind);
		tag.putDouble("x", from.x);
		tag.putDouble("y", from.y);
		tag.putDouble("z", from.z);
		tag.putDouble("x2", to.x);
		tag.putDouble("y2", to.y);
		tag.putDouble("z2", to.z);
		tag.putInt("a", a);
		tag.putInt("b", b);
		tag.putFloat("f", f);
		RNet.near(level, from, RANGE, tag);
	}

	/** A big moment (seal break, tower milestone) seen from further away. */
	public static void big(ServerLevel level, Vec3 at, int kind, int a, int b, float f) {
		RNet.fx(level, at, 160.0D, kind, a, b, f);
	}

	private RFx() {
	}
}
