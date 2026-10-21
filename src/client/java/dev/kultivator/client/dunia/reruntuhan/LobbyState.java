package dev.kultivator.client.dunia.reruntuhan;

import net.minecraft.nbt.CompoundTag;

/**
 * What this client knows of the tower lobby it stands in (DESIGN 34.5.7, {@code op: "lobby"} every two seconds): the
 * highest band gate open to the player and the best floor, for the band gates' look ({@link PartRenderer}) and the
 * tower screen. Forgotten on a disconnect.
 */
final class LobbyState {

	/** Band colours (1-10, index 0 = the pagoda's own gate): the band of ten floors each gate leads to. */
	static final int[] BAND_RGB = {0xFFE6A0, 0x9AE87A, 0xE8ECF4, 0xFF6A3A, 0xC8A070, 0xB57CFF, 0xFF9AD8, 0xFFD54A, 0xE0F4FF, 0x8A5AE0, 0xFFF4D0};
	/** The Chinese numerals of the bands (一 .. 十). */
	static final String[] NUMERALS = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};

	static int unlocked = 1;
	static int best;
	static long seenAt = -1L;

	static void update(CompoundTag tag, long now) {
		unlocked = Math.max(1, Math.min(10, tag.getIntOr("unlocked", 1)));
		best = Math.max(0, Math.min(100, tag.getIntOr("best", 0)));
		seenAt = now;
	}

	static int bandRgb(int band) {
		return BAND_RGB[Math.max(0, Math.min(BAND_RGB.length - 1, band))];
	}

	static void clear() {
		unlocked = 1;
		best = 0;
		seenAt = -1L;
	}

	private LobbyState() {
	}
}
