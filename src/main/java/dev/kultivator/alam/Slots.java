package dev.kultivator.alam;

import dev.kultivator.KultivatorConfig;
import net.minecraft.core.BlockPos;

/**
 * Slot leases of the realm dimension (DESIGN 25.3.3): slot {@code s} (0-7, at most {@code secretRealmSlots}) is centred at
 * {@code x = 1024 + s × 2048, z = 1024} on the generated surface y = 5. First free in memory, reused; a reused slot reloads
 * identical generated chunks because nothing can change them. Memory only.
 */
public final class Slots {

	public static final int MAX = 8;
	public static final int SURFACE_Y = 5;

	private static final boolean[] USED = new boolean[MAX];

	/** Leases the first free slot; -1 when every allowed slot is taken. */
	public static synchronized int lease() {
		int allowed = Math.max(1, Math.min(MAX, KultivatorConfig.get().secretRealmSlots));
		for (int s = 0; s < allowed; s++) {
			if (!USED[s]) {
				USED[s] = true;
				return s;
			}
		}
		return -1;
	}

	public static synchronized void release(int slot) {
		if (slot >= 0 && slot < MAX) {
			USED[slot] = false;
		}
	}

	/** The centre block of slot {@code s} on the surface (the pad stands on top of it). */
	public static BlockPos centre(int slot) {
		return new BlockPos(1024 + slot * 2048, SURFACE_Y, 1024);
	}

	/** How many slots are leased now. */
	public static synchronized int leased() {
		int n = 0;
		for (boolean used : USED) {
			if (used) {
				n++;
			}
		}
		return n;
	}

	public static synchronized boolean used(int slot) {
		return slot >= 0 && slot < MAX && USED[slot];
	}

	public static synchronized void clear() {
		java.util.Arrays.fill(USED, false);
	}

	private Slots() {
	}
}
