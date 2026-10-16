package dev.kultivator.cultivation;

import dev.kultivator.data.CultivationData;
import net.minecraft.server.level.ServerPlayer;

/** STUB (phase 0) — owned by P1-pills. Pill toxicity: too many pills too fast (DESIGN section 9). */
public final class PillToxicity {

	public static void init() {
	}

	public static void tick(ServerPlayer player, CultivationData data) {
	}

	/** How well a pill works right now, 0..1 (before adding its toxin). */
	public static double effectiveness(CultivationData data) {
		return 1.0D - data.toxicity() / 120.0D;
	}

	/** Adds the pill's toxin and applies the poisoning side effects above 80. */
	public static void add(ServerPlayer player, double toxin) {
	}

	private PillToxicity() {
	}
}
