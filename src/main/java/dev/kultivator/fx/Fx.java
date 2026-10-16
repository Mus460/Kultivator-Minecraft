package dev.kultivator.fx;

import dev.kultivator.network.Net;
import dev.kultivator.network.Payloads;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Asks nearby clients to draw an effect. Particles are made on each client, scaled by its Effect Quality setting. */
public final class Fx {

	/** Players this far away still see the effect. */
	public static final double RANGE = 96.0D;

	public static void send(ServerLevel level, Vec3 pos, FxType type, int a, int b, float f) {
		send(level, pos, pos, type, a, b, f);
	}

	public static void send(ServerLevel level, Vec3 pos, Vec3 target, FxType type, int a, int b, float f) {
		Payloads.Fx payload = new Payloads.Fx(type.ordinal(), pos.x, pos.y, pos.z, target.x, target.y, target.z, a, b, f);
		for (ServerPlayer player : PlayerLookup.around(level, pos, RANGE)) {
			Net.send(player, payload);
		}
	}

	/** Only for one player (personal effects like a private omen). */
	public static void sendTo(ServerPlayer player, Vec3 pos, Vec3 target, FxType type, int a, int b, float f) {
		Net.send(player, new Payloads.Fx(type.ordinal(), pos.x, pos.y, pos.z, target.x, target.y, target.z, a, b, f));
	}

	public static void burst(ServerLevel level, Vec3 pos, int rgb, int count, float radius) {
		send(level, pos, FxType.BURST, rgb, count, radius);
	}

	public static void ring(ServerLevel level, Vec3 pos, int rgb, float radius) {
		send(level, pos, FxType.RING, rgb, 0, radius);
	}

	public static void beam(ServerLevel level, Vec3 from, Vec3 to, int rgb, float width) {
		send(level, from, to, FxType.BEAM, rgb, 0, width);
	}

	public static void pillar(ServerLevel level, Vec3 pos, int rgb, float height) {
		send(level, pos, FxType.PILLAR, rgb, 0, height);
	}

	private Fx() {
	}
}
