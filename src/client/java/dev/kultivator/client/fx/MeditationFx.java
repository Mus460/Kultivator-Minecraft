package dev.kultivator.client.fx;

import dev.kultivator.fx.FxType;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;

/** STUB (phase 0) — owned by P1-meditation. Meditation motes (continuous, from synced state), enlightenment, qi formation lines. */
public final class MeditationFx {

	public static void play(FxType type, Payloads.Fx fx) {
	}

	/** Every client tick (only while in a world and not paused). */
	public static void tick(Minecraft minecraft) {
	}

	private MeditationFx() {
	}
}
