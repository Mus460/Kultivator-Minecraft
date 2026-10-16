package dev.kultivator.client.fx;

import dev.kultivator.fx.FxType;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;

/** STUB (phase 0) — owned by P2-techniques. Every technique effect; continuous ones (domain dome, flying sword trail) in tick. */
public final class TechniqueFx {

	public static void play(FxType type, Payloads.Fx fx) {
	}

	/** Divine Sense ore positions to outline for a while. */
	public static void onOrePings(Payloads.OrePings pings) {
	}

	/** Every client tick (only while in a world and not paused). */
	public static void tick(Minecraft minecraft) {
	}

	private TechniqueFx() {
	}
}
