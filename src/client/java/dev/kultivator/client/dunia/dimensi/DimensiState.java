package dev.kultivator.client.dunia.dimensi;

import dev.kultivator.dunia.dimensi.DimensiNet;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

/**
 * The local player's last {@link DimensiNet.State} (DESIGN 34.5.13-14): soul flag, soul time left, the body's place
 * (the homesick compass of Teras Wangxiang), Luka Jiwa, and the sea flags. Times are kept as client game times so the
 * HUD timer runs between the server's 5 s refreshes.
 */
public final class DimensiState {

	private static boolean soul;
	private static long soulEnds;
	private static long lukaEnds;
	private static double bodyX;
	private static double bodyZ;
	private static String bodyDim = "";
	private static boolean sea;
	private static boolean breath;

	static void onState(DimensiNet.State payload) {
		CompoundTag tag = payload.data();
		long now = now();
		soul = tag.getBooleanOr("soul", false);
		soulEnds = now + tag.getLongOr("left", 0L);
		lukaEnds = now + tag.getLongOr("luka", 0L);
		bodyX = tag.getDoubleOr("bx", 0.0D);
		bodyZ = tag.getDoubleOr("bz", 0.0D);
		bodyDim = tag.getStringOr("bdim", "");
		sea = tag.getBooleanOr("sea", false);
		breath = tag.getBooleanOr("breath", false);
	}

	static void clear() {
		soul = false;
		soulEnds = 0L;
		lukaEnds = 0L;
		bodyDim = "";
		sea = false;
		breath = false;
	}

	private static long now() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level == null ? 0L : minecraft.level.getGameTime();
	}

	public static boolean soul() {
		return soul;
	}

	/** Soul ticks left (0 when not a soul). */
	public static long soulLeft() {
		return soul ? Math.max(0L, soulEnds - now()) : 0L;
	}

	public static long lukaLeft() {
		return Math.max(0L, lukaEnds - now());
	}

	public static double bodyX() {
		return bodyX;
	}

	public static double bodyZ() {
		return bodyZ;
	}

	public static String bodyDim() {
		return bodyDim;
	}

	public static boolean sea() {
		return sea;
	}

	public static boolean breath() {
		return breath;
	}

	private DimensiState() {
	}
}
