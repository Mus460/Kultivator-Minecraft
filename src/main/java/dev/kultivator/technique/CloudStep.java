package dev.kultivator.technique;

import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.data.Technique;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.dsl.Payload;
import dev.kultivator.technique.spec.dsl.Shape;
import dev.kultivator.technique.spec.dsl.Step;
import dev.kultivator.technique.spec.dsl.Val;
import dev.kultivator.technique.spec.run.Boons;
import dev.kultivator.technique.spec.run.CastOptions;
import dev.kultivator.technique.spec.run.Mastery;
import dev.kultivator.technique.spec.run.PlayerCaster;
import dev.kultivator.technique.spec.run.TechniqueRunner;
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
 * Langkah Awan / Cloud Step (passive, DESIGN 22.10): the client asks for it when jump is pressed in mid-air. Air jumps
 * by realm (Qi Refining 1, Foundation 2, Golden Core 3, Deity Transformation 4, Kenaikan Abadi 5, Dewa Abadi 6; +1 at
 * Kesempurnaan Agung and from AIR_JUMP boons), each costing 6 qi after the realm gap (Dewa Abadi 3), and no fall damage
 * until the next landing. At Puncak Jurus a landing from 4 blocks or more sends a ring (0.3 × P, knockback 0.4, every
 * 20 ticks at most). Blocked during a Qi Deviation and a breakthrough, like every technique.
 */
final class CloudStep {

	/** Fall damage stays cancelled this long after the landing (safety margin; the landing itself is already covered). */
	private static final int LANDING_TAIL_TICKS = 10;
	/** Horizontal speed after a step never exceeds this (blocks per tick). */
	private static final double MAX_HORIZONTAL = 0.75D;
	private static final double RING_FALL = 4.0D;
	private static final int RING_ICD = 20;

	private static final class State {
		int used;
		long immuneUntil;
		double peakY = Double.NEGATIVE_INFINITY;
		long lastRing;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	/** Air jumps per flight by realm (22.10). */
	static int maxJumps(Realm realm) {
		return switch (realm) {
			case MORTAL -> 0;
			case QI_REFINING -> 1;
			case FOUNDATION -> 2;
			case GOLDEN_CORE, NASCENT_SOUL -> 3;
			case DEITY_TRANSFORMATION, DAO_UNION -> 4;
			case IMMORTAL_ASCENSION -> 5;
			case TRUE_IMMORTAL -> 6;
		};
	}

	/**
	 * Every tick: landing (or water, ladders) refills the air jumps and lets the fall immunity run out shortly after; at
	 * Puncak Jurus a high landing sends the ring.
	 */
	static void tick(ServerPlayer player, CultivationData data) {
		State state = STATES.get(player.getUUID());
		if (state == null) {
			return;
		}
		if (state.used > 0) {
			state.peakY = Math.max(state.peakY, player.getY());
		}
		if (state.used > 0 && (player.onGround() || player.isInWater() || player.onClimbable() || player.isPassenger())) {
			long now = player.level().getGameTime();
			if (player.onGround() && state.peakY - player.getY() >= RING_FALL && now - state.lastRing >= RING_ICD
					&& Mastery.level(data, Technique.CLOUD_STEP.id()) >= TechniquePower.MAX_MASTERY) {
				state.lastRing = now;
				landingRing(player);
			}
			state.used = 0;
			state.peakY = Double.NEGATIVE_INFINITY;
			state.immuneUntil = now + LANDING_TAIL_TICKS;
		}
		if (state.used == 0 && player.level().getGameTime() > state.immuneUntil) {
			STATES.remove(player.getUUID());
		}
	}

	/** The Puncak Jurus landing ring: a by-product (no Kehancuran, no mastery). */
	private static void landingRing(ServerPlayer player) {
		TechniqueSpec spec = Technique.CLOUD_STEP.spec();
		PlayerCaster ctx = new PlayerCaster(player, spec, new CastOptions(false, true, false, false, true));
		TechniqueRunner.start(ctx);
		Step ring = Step.at(0).shape(Shape.Nova.DEFAULT.radius(3.0D)).hit(DaoTechniques.dmg(0.3D),
				new Payload.Move(Payload.MoveKind.KNOCK, Val.of(0.4D), Payload.Center.CASTER, false, 0, false, false, false));
		ctx.run(ring);
		Techniques.fx(player, Technique.CLOUD_STEP, 0, player.position(), player.position(), 3.0F);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST, SoundSource.PLAYERS, 0.6F, 1.3F);
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
		boolean free = TechniqueRunner.bypass(player);
		if (!free && data.cooldownLeft(technique, now) > 0L) {
			return;
		}
		State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
		TechniqueSpec spec = technique.spec();
		int mastery = Mastery.level(data, technique.id());
		int max = maxJumps(data.realm()) + (mastery >= 3 ? 1 : 0) + Boons.airJumps(player);
		if (state.used >= max) {
			return;
		}
		int cost = TechniquePower.qi(spec, data, mastery);
		if (!free && data.qi() + 1.0E-6D < cost) {
			Techniques.refuse(player, Component.translatable("kultivator.techniques.cloud_step.no_qi", String.valueOf(cost)));
			return;
		}
		if (!free && !Qi.trySpend(player, cost)) {
			return;
		}
		state.used++;
		// Airborne after a step: immune until the next landing (tick() shortens it then).
		state.immuneUntil = Long.MAX_VALUE;
		state.peakY = Math.max(state.peakY, player.getY());
		if (!free) {
			data.setCooldown(technique, now, TechniquePower.cooldownTicks(spec, data, mastery));
		}

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
