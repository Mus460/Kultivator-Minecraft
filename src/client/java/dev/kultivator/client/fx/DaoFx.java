package dev.kultivator.client.fx;

import dev.kultivator.fx.FxType;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;

/** STUB (phase 0) — owned by P2-dao. Dao chosen. */
public final class DaoFx {

	public static void play(FxType type, Payloads.Fx fx) {
	}

	/** Every client tick (only while in a world and not paused). */
	public static void tick(Minecraft minecraft) {
	}

	private DaoFx() {
	}
}
