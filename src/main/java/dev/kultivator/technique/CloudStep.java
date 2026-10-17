package dev.kultivator.technique;

import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.data.Technique;
import dev.kultivator.trial.HeartDemons;
import dev.kultivator.util.Advancements;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Langkah Awan / Cloud Step (passive): the client asks for it when jump is pressed in mid-air. Extra air jumps (Qi
 * Refining 1, Foundation 2, Golden Core and up 3, refilled on landing), 6 qi each, and no fall damage until the next
 * landing (however long the fall). Like every technique it is blocked during a Qi Deviation and a breakthrough.
 */
final class CloudStep {

	/** Fall damage stays cancelled this long after the landing (safety margin; the landing itself is already covered). */
	private static final int LANDING_TAIL_TICKS = 10;
	/** Horizontal speed after a step never exceeds this (blocks per tick). */
	private static final double MAX_HORIZONTAL = 0.75D;

	private static final class State {
		int used;
		long immuneUntil;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	/** Air jumps per flight: Qi Refining 1, Foundation 2, Golden Core and above 3. */
	static int maxJumps(Realm realm) {
		if (realm.atLeast(Realm.GOLDEN_CORE)) {
			return 3;
		}
		return realm.atLeast(Realm.FOUNDATION) ? 2 : realm.atLeast(Realm.QI_REFINING) ? 1 : 0;
	}

	/**
	 * Every tick: landing (or water, ladders) refills the air jumps and lets the fall immunity run out shortly after. The
	 * landing's own fall damage comes from the move packet before this tick sees the ground, so it is still cancelled.
	 */
	static void tick(ServerPlayer player, CultivationData data) {
		State state = STATES.get(player.getUUID());
		if (state == null) {
			return;
		}
		if (state.used > 0 && (player.onGround() || player.isInWater() || player.onClimbable() || player.isPassenger())) {
			state.used = 0;
			state.immuneUntil = player.level().getGameTime() + LANDING_TAIL_TICKS;
		}
		if (state.used == 0 && player.level().getGameTime() > state.immuneUntil) {
			STATES.remove(player.getUUID());
		}
	}

	/** True while fall damage is cancelled after a Cloud Step (from the step until just after the next landing). */
	static boolean fallImmune(ServerPlayer player) {
		State state = STATES.get(player.getUUID());
		return state != null && player.level().getGameTime() <= state.immuneUntil;
	}

	/** Jump pressed in mid-air. Quietly does nothing when a step is not possible right now. */
	static void tryStep(ServerPlayer player, CultivationData data) {
		Technique technique = Technique.CLOUD_STEP;
		if (!data.knows(technique) || !player.isAlive() || player.isDeadOrDying() || player.isSpectator()) {
			return;
		}
		if (data.deviating() || (data.breakingThrough() || Breakthrough.isBusy(player)) && !HeartDemons.inBreakthroughTrial(player)) {
			return;
		}
		if (player.onGround() || player.isInWater() || player.isSwimming() || player.isPassenger() || player.isFallFlying()
				|| player.onClimbable() || player.getAbilities().flying || data.flyingSword() || player.isSleeping()) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (data.cooldownLeft(technique, now) > 0L) {
			return;
		}
		State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
		int max = maxJumps(data.realm());
		if (state.used >= max) {
			return;
		}
		if (data.qi() + 1.0E-6D < technique.qiCost()) {
			Techniques.refuse(player, Component.translatable("kultivator.techniques.cloud_step.no_qi",
					String.valueOf(technique.qiCost())));
			return;
		}
		if (!Qi.trySpend(player, technique.qiCost())) {
			return;
		}
		state.used++;
		// Airborne after a step: immune until the next landing (tick() shortens it then).
		state.immuneUntil = Long.MAX_VALUE;
		data.setCooldown(technique, now, technique.cooldownTicks());

		// Keep the horizontal momentum the client reported, add a little push where the player looks.
		Vec3 known = player.getKnownMovement();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 flatLook = new Vec3(look.x, 0.0D, look.z);
		if (flatLook.lengthSqr() > 1.0E-6D) {
			flatLook = flatLook.normalize().scale(0.12D);
		}
		double vx = known.x + flatLook.x;
		double vz = known.z + flatLook.z;
		double horizontal = Math.sqrt(vx * vx + vz * vz);
		if (horizontal > MAX_HORIZONTAL) {
			vx *= MAX_HORIZONTAL / horizontal;
			vz *= MAX_HORIZONTAL / horizontal;
		}
		double vy = 0.55D + 0.01D * data.realm().ordinal();
		player.setDeltaMovement(vx, vy, vz);
		player.resetFallDistance();
		// The client owns its own movement: tell it directly.
		player.connection.send(new ClientboundSetEntityMotionPacket(player));

		Vec3 feet = player.position();
		Techniques.fx(player, technique, 0, feet, feet.add(vx, 0.0D, vz), 1.0F + 0.1F * state.used);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.45F, 1.35F + 0.1F * state.used);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.5F, 0.7F);
		Advancements.grant(player, "cloud_step");
		Cultivators.dirty(player);
	}

	static void forget(ServerPlayer player) {
		STATES.remove(player.getUUID());
	}

	static void clearAll() {
		STATES.clear();
	}

	private CloudStep() {
	}
}
