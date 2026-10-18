package dev.kultivator.technique;

import dev.kultivator.Kultivator;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.Technique;
import dev.kultivator.technique.spec.dsl.Hook;
import dev.kultivator.technique.spec.dsl.Payload;
import dev.kultivator.technique.spec.dsl.Shape;
import dev.kultivator.technique.spec.dsl.Step;
import dev.kultivator.technique.spec.dsl.Val;
import dev.kultivator.technique.spec.run.Caster;
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
 * Wujud Dharma / Dharma Form (DESIGN 22.10): for 600 ticks → (D) the cultivator grows (Deity Transformation ×2, Dao Union
 * ×2.5, Kenaikan Abadi ×3, from Tahap 5 ×3.5, Dewa Abadi ×4; the largest size the head room allows, at least ×2), with
 * Strength II (Kenaikan Abadi III, Dewa Abadi IV), Resistance I (Dewa Abadi II), reach +2 × (scale − 1) and technique damage
 * +15%. +100 ticks at Kesempurnaan Agung; at Puncak Jurus melee hits release a golden arc. A low ceiling ends it early;
 * suffocation is cancelled while it lasts; it shares the one-form slot with the technique forms.
 */
final class DharmaForm {

	static final Identifier SCALE_ID = Kultivator.id("dharma_scale");
	static final Identifier REACH_ID = Kultivator.id("dharma_reach");
	static final Identifier BLOCK_REACH_ID = Kultivator.id("dharma_block_reach");
	private static final int GOLD = 0xFFD54A;

	private record Active(long end, int strength, int resistance, int duration) {
	}

	/** Each active form: end time and the effect levels we gave. */
	private static final Map<UUID, Active> ACTIVE = new HashMap<>();

	static boolean cast(Caster ctx, ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		if (data.dharmaActive()) {
			return Techniques.refuse(player, Component.translatable("kultivator.techniques.dharma_form.already"));
		}
		double wanted = ctx.arena() ? 2.0D : scaleFor(data.rank());
		double scale = 0.0D;
		double[] offset = null;
		for (double candidate = wanted; candidate >= 2.0D - 1.0E-6D; candidate -= 0.5D) {
			offset = room(player, candidate);
			if (offset != null) {
				scale = candidate;
				break;
			}
		}
		if (offset == null) {
			return Techniques.refuse(player, Component.translatable("kultivator.techniques.dharma_form.no_room"));
		}
		Techniques.endTechniqueForm(player);
		ServerLevel level = player.level();
		if (offset[0] != 0.0D || offset[1] != 0.0D) {
			// The game never nudges a growing player: step aside first (the current body fits inside the checked big box).
			player.teleportTo(level, player.getX() + offset[0], player.getY(), player.getZ() + offset[1], Relative.ROTATION, 0.0F, 0.0F, false);
		}
		Realm realm = data.realm();
		int strength = ctx.arena() ? 1 : realm.atLeast(Realm.TRUE_IMMORTAL) ? 3 : realm.atLeast(Realm.IMMORTAL_ASCENSION) ? 2 : 1;
		int resistance = !ctx.arena() && realm.atLeast(Realm.TRUE_IMMORTAL) ? 1 : 0;
		int duration = ctx.arena() ? 600 : ctx.duration(600.0D, 1200.0D);
		modifier(player, Attributes.SCALE, SCALE_ID, scale - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		modifier(player, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID, 2.0D * (scale - 1.0D), AttributeModifier.Operation.ADD_VALUE);
		modifier(player, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID, 2.0D * (scale - 1.0D), AttributeModifier.Operation.ADD_VALUE);
		player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, duration, strength, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, duration, resistance, false, true));
		// technique Resistance counts inside the DR cap (22.14.3)
		dev.kultivator.technique.spec.run.Boons.trackResistance(player, resistance, duration);
		if (!ctx.arena()) {
			ctx.applySelf(new Payload.Buff(Payload.BuffKind.EMPOWER, 0.15D, 0.0D, Val.of(duration), null, false, false, 1));
		}
		if (ctx.special("golden_arc")) {
			Step arc = Step.at(0).shape(Shape.Strike.DEFAULT.range(4.0D).arc(120.0D)).hit(DaoTechniques.dmg(0.8D));
			ctx.applySelf(new Payload.GrantHook(Hook.onMelee(arc).icd(10).ticks(duration)));
		}
		data.setDharmaActive(true);
		ACTIVE.put(player.getUUID(), new Active(level.getGameTime() + duration, strength, resistance, duration));
		Cultivators.refreshVisual(player);
		Cultivators.dirty(player);

		Vec3 pos = player.position();
		Techniques.fx(player, Technique.DHARMA_FORM, 0, pos, pos.add(0.0D, player.getBbHeight(), 0.0D), (float) scale);
		ctx.castFx(pos, pos.add(0.0D, player.getBbHeight(), 0.0D), (float) scale, player.getBbHeight(), 1, duration);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.8F, 0.7F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.7F, 0.8F);
		Msg.actionBar(player, Component.translatable("kultivator.techniques.dharma_form.start", String.valueOf(duration / 20)).withColor(GOLD));
		if (!ctx.copy()) {
			Advancements.grant(player, "dharma_form");
		}
		return true;
	}

	/** The size of 22.10: DT 2, DU 2.5, IA 3, IA Tahap 5+ 3.5, TI 4. */
	static double scaleFor(Rank rank) {
		return switch (rank.realm()) {
			case TRUE_IMMORTAL -> 4.0D;
			case IMMORTAL_ASCENSION -> rank.layer() >= 4 ? 3.5D : 3.0D;
			case DAO_UNION -> 2.5D;
			default -> 2.0D;
		};
	}

	static void tick(ServerPlayer player, CultivationData data) {
		Active active = ACTIVE.get(player.getUUID());
		if (active == null) {
			if (data.dharmaActive()) {
				end(player, null);
			}
			return;
		}
		long now = player.level().getGameTime();
		if (now >= active.end()) {
			end(player, "kultivator.techniques.dharma_form.end");
		} else if ((active.end() - now) % 20 == 0 && !roomNow(player)) {
			end(player, "kultivator.techniques.dharma_form.ceiling");
		}
	}

	/** Ends the form: modifiers off, our effects off, flag cleared. {@code messageKey} null = quietly. */
	static void end(ServerPlayer player, @Nullable String messageKey) {
		Active active = ACTIVE.remove(player.getUUID());
		CultivationData data = Cultivators.get(player);
		removeModifiers(player);
		if (active != null) {
			removeOurEffect(player, MobEffects.STRENGTH, active.strength(), active.duration());
			removeOurEffect(player, MobEffects.RESISTANCE, active.resistance(), active.duration());
		}
		boolean flag = data.dharmaActive();
		data.setDharmaActive(false);
		if (active == null && !flag) {
			return;
		}
		Cultivators.refreshVisual(player);
		Cultivators.dirty(player);
		ServerLevel level = player.level();
		if (!level.noBlockCollision(player, player.getBoundingBox().deflate(0.02D))) {
			// IN_WALL check on revert: never left stuck after shrinking.
			Vec3 spot = dev.kultivator.util.Targeting.safeSpot(level, player, player.position());
			if (spot != null) {
				player.teleportTo(level, spot.x, spot.y, spot.z, Relative.ROTATION, 0.0F, 0.0F, false);
			}
		}
		if (messageKey == null) {
			return;
		}
		Vec3 pos = player.position();
		Techniques.fx(player, Technique.DHARMA_FORM, 1, pos, pos, 1.0F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.9F, 0.8F);
		Msg.actionBar(player, Component.translatable(messageKey).withColor(Techniques.MUTED));
	}

	/** Where a body {@code scale} times the standing size has free space: {x, z} offset (0, 0 preferred, else 0.3 aside), or null. */
	private static double[] room(ServerPlayer player, double scale) {
		ServerLevel level = player.level();
		AABB box = player.getDimensions(Pose.STANDING).scale((float) (scale / Math.max(1.0E-3D, player.getScale()))).makeBoundingBox(player.position());
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
	private static void removeOurEffect(ServerPlayer player, Holder<MobEffect> effect, int amplifier, int duration) {
		MobEffectInstance active = player.getEffect(effect);
		if (active != null && active.getAmplifier() == amplifier && !active.isInfiniteDuration() && active.getDuration() <= duration) {
			player.removeEffect(effect);
		}
	}

	static boolean active(ServerPlayer player) {
		return ACTIVE.containsKey(player.getUUID());
	}

	static void onJoin(ServerPlayer player) {
		ACTIVE.remove(player.getUUID());
		removeModifiers(player);
	}

	static void onLeave(ServerPlayer player) {
		if (ACTIVE.containsKey(player.getUUID()) || Cultivators.get(player).dharmaActive()) {
			end(player, null);
		}
	}

	static void onDeath(ServerPlayer player) {
		if (ACTIVE.containsKey(player.getUUID()) || Cultivators.get(player).dharmaActive()) {
			end(player, null);
		}
	}

	static void clearAll() {
		ACTIVE.clear();
	}

	private DharmaForm() {
	}
}
