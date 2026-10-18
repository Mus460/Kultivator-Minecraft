package dev.kultivator.technique;

import dev.kultivator.data.Technique;
import dev.kultivator.technique.spec.dsl.Bonus;
import dev.kultivator.technique.spec.dsl.Payload;
import dev.kultivator.technique.spec.dsl.Val;
import dev.kultivator.technique.spec.run.Caster;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.LoadedPath;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The legacy techniques without a Dao (DESIGN 22.10): Qi Palm and Buddha Palm here, the Flying Sword, Divine Sense,
 * Dharma Form and Dao Domain in their own classes; Cloud Step is passive ({@link CloudStep}). Damage is a coefficient × P
 * through the cast's choke point; sizes are scaled reads. Nothing here touches a block: beams stop at the first solid
 * block of a read-only clip along loaded chunks.
 */
public final class UniversalTechniques {

	/** The golden palm falls for this long before it lands. */
	private static final int BUDDHA_DELAY = 20;

	/** Performs a legacy universal technique with its cast context. True when it really happened. */
	public static boolean cast(Caster ctx) {
		Technique technique = ctx.spec().legacy();
		ServerPlayer player = ctx.player();
		if (technique == null || player == null) {
			return false;
		}
		return switch (technique) {
			case CLOUD_STEP -> Techniques.refuse(player, Component.translatable("kultivator.techniques.refuse.passive",
					Component.keybind("key.jump").withStyle(ChatFormatting.YELLOW)));
			case QI_PALM -> qiPalm(ctx, player);
			case FLYING_SWORD -> FlyingSword.mount(ctx, player);
			case DIVINE_SENSE -> DivineSense.cast(ctx, player);
			case BUDDHA_PALM -> buddhaPalm(ctx, player);
			case DHARMA_FORM -> DharmaForm.cast(ctx, player);
			case DAO_DOMAIN -> DaoDomain.cast(ctx, player);
			default -> false;
		};
	}

	// ================================================================ Qi Palm

	/**
	 * Qi Palm: a beam of qi 12→24 blocks (half width 1.2→2) along the look, stopped by the first solid block: 1 × P once to
	 * every monster on it, knocked away 0.9. At Kesempurnaan Agung three palms fanned ±12° (the side palms ×0.6); at Puncak
	 * Jurus an echo at 40%.
	 */
	private static boolean qiPalm(Caster ctx, ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		double range = ctx.area(12.0D, 24.0D);
		double width = ctx.area(1.2D, 2.0D);
		int extra = ctx.bonusCount();
		int palms = 1 + extra;
		Set<LivingEntity> done = new HashSet<>();
		Vec3 mainEnd = eye.add(look.scale(range));
		Payload.Move knock = new Payload.Move(Payload.MoveKind.KNOCK, Val.of(0.9D), Payload.Center.CASTER, false, 0, false, false, false);
		for (int i = 0; i < palms; i++) {
			double offset = i == 0 ? 0.0D : (i % 2 == 1 ? 12.0D : -12.0D) * ((i + 1) / 2);
			double factor = i == 0 ? 1.0D : ctx.bonusCountFactor();
			Vec3 dir = Caster.rotateYaw(look, offset);
			BlockHitResult block = LoadedPath.ray(level, eye, eye.add(dir.scale(range)), player);
			Vec3 end = block.getLocation();
			if (i == 0) {
				mainEnd = end;
			}
			double length = end.distanceTo(eye);
			List<LivingEntity> candidates = ctx.hostiles(eye.add(end).scale(0.5D), length * 0.5D + width + 2.0D, true);
			for (LivingEntity target : candidates) {
				if (done.contains(target)) {
					continue;
				}
				Vec3 center = target.getBoundingBox().getCenter();
				double along = center.subtract(eye).dot(dir);
				if (along < -0.5D || along > length + 0.5D) {
					continue;
				}
				Vec3 closest = eye.add(dir.scale(Math.max(0.0D, Math.min(length, along))));
				if (closest.distanceTo(center) > width + target.getBbWidth() * 0.5D || !Targeting.hasLineOfSight(player, target)) {
					continue;
				}
				done.add(target);
				if (ctx.hurt(ctx.damage(target, 1.0D * factor).precise(true))) {
					if (target.isAlive() && !(target instanceof Player)) {
						ctx.applyHit(target, knock, true, eye, range);
					}
					Techniques.fx(player, Technique.QI_PALM, 1, center, center, 1.0F);
				}
			}
			Vec3 hand = eye.add(dir.scale(0.7D)).add(0.0D, -0.25D, 0.0D);
			Techniques.fx(player, Technique.QI_PALM, 0, hand, end, (float) (1.0D + 0.15D * ctx.visualTier()));
		}
		ctx.markEnd(mainEnd);
		ctx.castFx(eye, mainEnd, (float) width, (float) eye.distanceTo(mainEnd), palms, 0);
		level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, 0.7F);
		level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.7F, 1.2F);
		if (!done.isEmpty() || ctx.kehancuran()) {
			ctx.impact(mainEnd, look);
		}
		DaoTechniques.echoPeak(ctx);
		return true;
	}

	// ================================================================ Buddha Palm

	/**
	 * Buddha Palm: a giant golden palm forms 12→24 blocks above the looked-at spot (24→48 away) and lands a second later:
	 * 3 × P (2.5 in an arena) within 5→16 blocks, slammed down (1.0) and Slowness II for 3 s. At Puncak Jurus two more
	 * palms follow 10 ticks apart at 60%.
	 */
	private static boolean buddhaPalm(Caster ctx, ServerPlayer player) {
		ServerLevel level = player.level();
		double range = ctx.area(24.0D, 48.0D);
		double radius = ctx.area(5.0D, 16.0D);
		double sky = ctx.area(12.0D, 24.0D);
		Vec3 point = Targeting.aimPoint(player, Math.min(range, 64.0D));
		if (ctx.copy()) {
			point = ctx.aim();
		}
		Techniques.fx(player, Technique.BUDDHA_PALM, 0, point, player.position(), (float) radius);
		ctx.castFx(point.add(0.0D, sky, 0.0D), point, (float) radius, (float) sky, 1, BUDDHA_DELAY);
		level.playSound(null, point.x, point.y + 6.0D, point.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.5F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8F, 0.7F);
		Vec3 landing = point;
		double coef = ctx.arena() ? 2.5D : 3.0D;
		Payload.Move slam = new Payload.Move(Payload.MoveKind.SLAM, Val.of(1.0D), Payload.Center.ANCHOR, false, 0, false, false, false);
		ctx.schedule(BUDDHA_DELAY, () -> {
			for (LivingEntity target : ctx.hostiles(landing, radius, false)) {
				if (ctx.hurt(ctx.damage(target, coef)) && target.isAlive()) {
					if (!(target instanceof Player)) {
						ctx.applyHit(target, slam, false, landing, radius);
					}
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1, false, true), player);
				}
			}
			Techniques.fx(player, Technique.BUDDHA_PALM, 1, landing, landing, (float) radius);
			ctx.fx(dev.kultivator.technique.spec.dsl.Phase.IMPACT, landing, landing, (float) radius, 0.0F, 1, 0);
			level.playSound(null, landing.x, landing.y, landing.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.4F, 0.6F);
			level.playSound(null, landing.x, landing.y, landing.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.2F, 0.5F);
			ctx.impact(landing, ctx.look());
		});
		ctx.keepAlive(BUDDHA_DELAY + 1);
		if (!ctx.copy() && ctx.peak(Bonus.Kind.EXTRA_CAST)) {
			ctx.copyAfter(10, 0.6D, false);
			ctx.copyAfter(20, 0.6D, false);
		}
		if (!ctx.copy()) {
			Advancements.grant(player, "buddha_palm");
		}
		return true;
	}

	private UniversalTechniques() {
	}
}
