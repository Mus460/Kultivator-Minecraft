package dev.kultivator.technique;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Technique;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The techniques without a Dao (DESIGN sections 8 and 15): Qi Palm and Buddha Palm here, the Flying Sword, Divine
 * Sense, Dharma Form and Dao Domain in their own classes; Cloud Step is passive ({@link CloudStep}). Nothing here ever
 * touches a block: beams stop at the first solid block found by a read-only raycast.
 */
public final class UniversalTechniques {

	private static final double PALM_RANGE = 12.0D;
	private static final double PALM_WIDTH = 1.2D;
	private static final double BUDDHA_RANGE = 24.0D;
	private static final double BUDDHA_RADIUS = 5.0D;
	/** The golden palm falls for this long before it lands. */
	private static final int BUDDHA_DELAY = 20;

	/** Performs {@code technique} (qi and cooldown are handled by Techniques). True when it really happened. */
	public static boolean cast(ServerPlayer player, CultivationData data, Technique technique) {
		return switch (technique) {
			case CLOUD_STEP -> Techniques.refuse(player, Component.translatable("kultivator.techniques.refuse.passive",
					Component.keybind("key.jump").withStyle(ChatFormatting.YELLOW)));
			case QI_PALM -> qiPalm(player, data);
			case FLYING_SWORD -> FlyingSword.mount(player, data);
			case DIVINE_SENSE -> DivineSense.cast(player, data);
			case BUDDHA_PALM -> buddhaPalm(player, data);
			case DHARMA_FORM -> DharmaForm.cast(player, data);
			case DAO_DOMAIN -> DaoDomain.cast(player, data);
			default -> false;
		};
	}

	// ================================================================ Qi Palm

	/**
	 * A blast of qi 12 blocks along the look direction, stopped by the first solid block; every monster within 1.2
	 * blocks of it takes D once and is knocked away from the caster.
	 */
	private static boolean qiPalm(ServerPlayer player, CultivationData data) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		BlockHitResult block = Targeting.blockHit(player, PALM_RANGE);
		Vec3 end = block.getType() == HitResult.Type.MISS ? eye.add(look.scale(PALM_RANGE)) : block.getLocation();
		double length = end.distanceTo(eye);
		float damage = (float) Techniques.power(data);

		List<LivingEntity> candidates = Targeting.hostilesAround(player, eye.add(end).scale(0.5D), length * 0.5D + PALM_WIDTH + 2.0D);
		for (LivingEntity target : candidates) {
			Vec3 center = target.getBoundingBox().getCenter();
			double along = center.subtract(eye).dot(look);
			if (along < -0.5D || along > length + 0.5D) {
				continue;
			}
			Vec3 closest = eye.add(look.scale(Math.max(0.0D, Math.min(length, along))));
			if (closest.distanceTo(center) > PALM_WIDTH + target.getBbWidth() * 0.5D || !Targeting.hasLineOfSight(player, target)) {
				continue;
			}
			if (Techniques.hurt(player, target, damage)) {
				knock(target, look.x, look.z, 0.9D, 0.3D);
				Techniques.fx(player, Technique.QI_PALM, 1, center, center, 1.0F);
			}
		}

		Vec3 hand = eye.add(look.scale(0.7D)).add(0.0D, -0.25D, 0.0D);
		Techniques.fx(player, Technique.QI_PALM, 0, hand, end, 1.0F);
		level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, 0.7F);
		level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.7F, 1.2F);
		return true;
	}

	// ================================================================ Buddha Palm

	/**
	 * A giant golden palm forms 12 blocks above the looked-at spot (24 blocks at most) and lands after a second: every
	 * monster within 5 blocks takes 2.5 D, is slammed down and slowed (Slowness II, 3 s).
	 */
	private static boolean buddhaPalm(ServerPlayer player, CultivationData data) {
		ServerLevel level = player.level();
		Vec3 point = Targeting.aimPoint(player, BUDDHA_RANGE);
		Techniques.fx(player, Technique.BUDDHA_PALM, 0, point, player.position(), (float) BUDDHA_RADIUS);
		level.playSound(null, point.x, point.y + 6.0D, point.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.5F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8F, 0.7F);
		Techniques.schedule(player, BUDDHA_DELAY, caster -> buddhaImpact(caster, point));
		Advancements.grant(player, "buddha_palm");
		return true;
	}

	private static void buddhaImpact(ServerPlayer player, Vec3 point) {
		ServerLevel level = player.level();
		CultivationData data = Cultivators.get(player);
		float damage = (float) (2.5D * Techniques.power(data));
		for (LivingEntity target : Targeting.hostilesAround(player, point, BUDDHA_RADIUS)) {
			if (Techniques.hurt(player, target, damage) && target.isAlive()) {
				double resist = Math.max(0.0D, Math.min(1.0D, target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)));
				Vec3 motion = target.getDeltaMovement();
				if (!(target instanceof Player)) {
					// Slammed down: its landing must not trample farmland or crush turtle eggs.
					target.addTag(NoGrief.TAG);
				}
				target.setDeltaMovement(motion.x * 0.3D, -1.0D * (1.0D - resist * 0.5D), motion.z * 0.3D);
				target.syncVelocity = true;
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1, false, true), player);
			}
		}
		Techniques.fx(player, Technique.BUDDHA_PALM, 1, point, point, (float) BUDDHA_RADIUS);
		level.playSound(null, point.x, point.y, point.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.4F, 0.6F);
		level.playSound(null, point.x, point.y, point.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.2F, 0.5F);
	}

	// ================================================================ helpers

	/**
	 * Pushes {@code target} along the horizontal direction (x, z), less for knockback-resistant targets. Never a ravager
	 * (pushed into trees it breaks leaves); a pushed monster gets {@link NoGrief#TAG} (no trampling, no egg crushing).
	 */
	static void knock(LivingEntity target, double x, double z, double strength, double lift) {
		double length = Math.sqrt(x * x + z * z);
		if (length < 1.0E-6D || target instanceof Ravager) {
			return;
		}
		if (!(target instanceof Player)) {
			target.addTag(NoGrief.TAG);
		}
		double resist = Math.max(0.0D, Math.min(1.0D, target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)));
		double scale = strength * (1.0D - resist) / length;
		if (scale <= 0.0D) {
			return;
		}
		Vec3 motion = target.getDeltaMovement();
		target.setDeltaMovement(motion.x * 0.5D + x * scale, Math.max(motion.y, lift * (1.0D - resist)), motion.z * 0.5D + z * scale);
		target.syncVelocity = true;
	}

	private UniversalTechniques() {
	}
}
