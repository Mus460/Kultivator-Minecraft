package dev.kultivator.technique.spec.run;

import dev.kultivator.network.Net;
import dev.kultivator.network.Payloads;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The visual syncs of the polish pass (DESIGN 27.4): forms and binds of an entity, sent to the players around it (the
 * entity itself included when it is a player). Pure visuals: the server state stays in {@link Forms},
 * {@link StatusTracker} and {@link Phantoms}; a client that misses one packet sees the next periodic one.
 */
public final class VisualSync {

	/** Players within this range of the entity get the sync (the TechFx range). */
	static final double RANGE = 96.0D;

	/** A TRANSFORM form started, changed stage or ended ({@code form} "" = ended). */
	public static void form(LivingEntity entity, String form, int ticks, int stage) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		around(level, entity.position(), new Payloads.FormSync(entity.getId(), form == null ? "" : clip(form),
				(short) Math.max(0, Math.min(Short.MAX_VALUE, ticks)), (byte) Math.max(0, Math.min(127, stage))));
	}

	/** A visual bind on {@code entity} ({@code ticks} 0 = it ended). */
	public static void bind(LivingEntity entity, BindKind kind, int ticks, @Nullable LivingEntity caster, int specNet) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		around(level, entity.position(), new Payloads.BindSync(entity.getId(), (byte) kind.ordinal(),
				(short) Math.max(0, Math.min(Short.MAX_VALUE, ticks)), caster == null ? -1 : caster.getId(), specNet));
	}

	static void around(ServerLevel level, Vec3 pos, CustomPacketPayload payload) {
		for (ServerPlayer player : PlayerLookup.around(level, pos, RANGE)) {
			Net.send(player, payload);
		}
	}

	private static String clip(String value) {
		return value.length() <= 64 ? value : value.substring(0, 64);
	}

	private VisualSync() {
	}
}
