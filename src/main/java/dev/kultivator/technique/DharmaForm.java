package dev.kultivator.technique;

import dev.kultivator.Kultivator;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Technique;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wujud Dharma / Dharma Form (Deity Transformation+): for 30 s the cultivator stands twice as tall (transient SCALE
 * modifier), reaches 2 blocks further (entities and blocks) and has Strength II and Resistance I. Needs head room for the
 * doubled body when cast (stepping up to 0.3 blocks aside first when a wall is that close: players are never nudged by
 * the game when they grow) and ends early when a ceiling gets too low; suffocation damage is cancelled while it lasts
 * (Techniques).
 */
final class DharmaForm {

	static final Identifier SCALE_ID = Kultivator.id("dharma_scale");
	static final Identifier REACH_ID = Kultivator.id("dharma_reach");
	static final Identifier BLOCK_REACH_ID = Kultivator.id("dharma_block_reach");
	private static final int DURATION = 600;
	private static final int STRENGTH_AMPLIFIER = 1;
	private static final int RESISTANCE_AMPLIFIER = 0;
	private static final int GOLD = 0xFFD54A;

	/** Game time each active form ends. */
	private static final Map<UUID, Long> ENDS = new HashMap<>();

	static boolean cast(ServerPlayer player, CultivationData data) {
		if (data.dharmaActive()) {
			return Techniques.refuse(player, Component.translatable("kultivator.techniques.dharma_form.already"));
		}
		double[] offset = roomForDoubleBody(player);
		if (offset == null) {
			return Techniques.refuse(player, Component.translatable("kultivator.techniques.dharma_form.no_room"));
		}
		ServerLevel level = player.level();
		if (offset[0] != 0.0D || offset[1] != 0.0D) {
			// The game never nudges a growing player: step aside first. The current body fits there (it lies inside the
			// checked big box).
			player.teleportTo(level, player.getX() + offset[0], player.getY(), player.getZ() + offset[1], Relative.ROTATION, 0.0F, 0.0F, false);
		}
		modifier(player, Attributes.SCALE, SCALE_ID, 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		modifier(player, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID, 2.0D, AttributeModifier.Operation.ADD_VALUE);
		modifier(player, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID, 2.0D, AttributeModifier.Operation.ADD_VALUE);
		player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, DURATION, STRENGTH_AMPLIFIER, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, DURATION, RESISTANCE_AMPLIFIER, false, true));
		data.setDharmaActive(true);
		ENDS.put(player.getUUID(), level.getGameTime() + DURATION);
		Cultivators.refreshVisual(player);
		Cultivators.dirty(player);

		Vec3 pos = player.position();
		Techniques.fx(player, Technique.DHARMA_FORM, 0, pos, pos.add(0.0D, player.getBbHeight(), 0.0D), 2.0F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.8F, 0.7F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.7F, 0.8F);
		Msg.actionBar(player, Component.translatable("kultivator.techniques.dharma_form.start", String.valueOf(DURATION / 20)).withColor(GOLD));
		Advancements.grant(player, "dharma_form");
		return true;
	}

	static void tick(ServerPlayer player, CultivationData data) {
		Long end = ENDS.get(player.getUUID());
		if (end == null) {
			if (data.dharmaActive()) {
				// Flag without a running form (should not happen): clean up.
				end(player, null);
			}
			return;
		}
		long now = player.level().getGameTime();
		if (now >= end) {
			end(player, "kultivator.techniques.dharma_form.end");
		} else if ((end - now) % 20 == 0 && !roomNow(player)) {
			end(player, "kultivator.techniques.dharma_form.ceiling");
		}
	}

	/** Ends the form: modifiers off, our effects off, flag cleared. {@code messageKey} null = quietly. */
	static void end(ServerPlayer player, @Nullable String messageKey) {
		boolean was = ENDS.remove(player.getUUID()) != null;
		CultivationData data = Cultivators.get(player);
		removeModifiers(player);
		removeOurEffect(player, MobEffects.STRENGTH, STRENGTH_AMPLIFIER);
		removeOurEffect(player, MobEffects.RESISTANCE, RESISTANCE_AMPLIFIER);
		boolean flag = data.dharmaActive();
		data.setDharmaActive(false);
		if (!was && !flag) {
			return;
		}
		Cultivators.refreshVisual(player);
		Cultivators.dirty(player);
		if (messageKey == null) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 pos = player.position();
		Techniques.fx(player, Technique.DHARMA_FORM, 1, pos, pos, 1.0F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.9F, 0.8F);
		Msg.actionBar(player, Component.translatable(messageKey).withColor(Techniques.MUTED));
	}

	/**
	 * Where a body twice the current size has free space: {x, z} offset from here (0, 0 preferred, else up to 0.3 to the
	 * side), or null when there is none.
	 */
	private static double[] roomForDoubleBody(ServerPlayer player) {
		ServerLevel level = player.level();
		AABB box = player.getDimensions(Pose.STANDING).scale(2.0F).makeBoundingBox(player.position());
		double[] offsets = {0.0D, 0.3D, -0.3D};
		for (double ox : offsets) {
			for (double oz : offsets) {
				if (level.noBlockCollision(player, box.move(ox, 0.0D, oz))) {
					return new double[]{ox, oz};
				}
			}
		}
		return null;
	}

	/** While big: the standing body still fits (a low ceiling forces it down otherwise). */
	private static boolean roomNow(ServerPlayer player) {
		AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(player.position()).deflate(0.02D);
		return player.level().noBlockCollision(player, box);
	}

	private static void modifier(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount,
			AttributeModifier.Operation operation) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
		}
	}

	private static void removeModifiers(ServerPlayer player) {
		AttributeInstance scale = player.getAttribute(Attributes.SCALE);
		if (scale != null) {
			scale.removeModifier(SCALE_ID);
		}
		AttributeInstance reach = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
		if (reach != null) {
			reach.removeModifier(REACH_ID);
		}
		AttributeInstance blockReach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
		if (blockReach != null) {
			blockReach.removeModifier(BLOCK_REACH_ID);
		}
	}

	/** Removes the effect only when it is still the one we gave (same strength, not longer than the form). */
	private static void removeOurEffect(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
		MobEffectInstance active = player.getEffect(effect);
		if (active != null && active.getAmplifier() == amplifier && !active.isInfiniteDuration() && active.getDuration() <= DURATION) {
			player.removeEffect(effect);
		}
	}

	static void onJoin(ServerPlayer player) {
		ENDS.remove(player.getUUID());
		removeModifiers(player);
	}

	static void onLeave(ServerPlayer player) {
		if (ENDS.containsKey(player.getUUID()) || Cultivators.get(player).dharmaActive()) {
			end(player, null);
		}
	}

	static void onDeath(ServerPlayer player) {
		if (ENDS.containsKey(player.getUUID()) || Cultivators.get(player).dharmaActive()) {
			end(player, null);
		}
	}

	static void clearAll() {
		ENDS.clear();
	}

	private DharmaForm() {
	}
}
