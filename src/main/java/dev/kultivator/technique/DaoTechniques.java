package dev.kultivator.technique;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.data.Technique;
import dev.kultivator.technique.spec.dsl.Bonus;
import dev.kultivator.technique.spec.dsl.Payload;
import dev.kultivator.technique.spec.dsl.Shape;
import dev.kultivator.technique.spec.dsl.Step;
import dev.kultivator.technique.spec.run.CastContext;
import dev.kultivator.technique.spec.run.Caster;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The 16 legacy Dao techniques made stronger (DESIGN 22.10) and the Dao flavour of the Dao Domain (22.10.1). The cast
 * pipeline ({@code TechniqueRunner}) handles refusals, qi and cooldowns; {@link #cast} performs the effect with the
 * cast's {@link Caster}: damage is a coefficient × P through the choke point (boss, arena, trial-mob and heart-demon caps
 * on the cast's ledger), sizes are scaled reads (A/D/N × mastery), the level 3/4 bonuses of 22.10 apply, and movement
 * follows the shared rules (never-moved list, PortalIndex, NoGrief). In an arena the pre-6c coefficients apply and the new
 * payloads are skipped. Nothing here changes a block: rays are read-only clips, fire is entity fire only, lightning is
 * particles. The old {@code FxType.TECHNIQUE} sub effects keep playing ({@link DaoTechniqueSub}); the tier layers come as
 * {@code TechFx} events of the spec.
 */
public final class DaoTechniques {

	/** Transient knockback resistance id of the old Iron Body (removed on join from saves of older versions). */
	public static final Identifier IRON_BODY_ID = dev.kultivator.Kultivator.id("iron_body");

	private static final int SWORD_FALL_TICKS = 4;
	private static final int THOUSAND_SWORDS_INTERVAL = 5;
	private static final double SKY_HEIGHT = 14.0D;
	private static final int DOMAIN_EFFECT_TICKS = 40;
	private static final double PULL_SPEED = 0.3D;

	private static final Map<UUID, DaoTechniqueState> STATES = new HashMap<>();
	/** Monsters set on fire by a technique -> game time their fire ends (watched so it never melts powder snow). */
	private static final Map<LivingEntity, Long> BURNING = new HashMap<>();
	/** True while we deal the empowered Shadow Step damage, so it never triggers itself again. */
	private static boolean retaliating;

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(DaoTechniques::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				STATES.remove(player.getUUID());
			} else {
				forgetEverywhere(entity);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (!(entity instanceof Player)) {
				forgetEverywhere(entity);
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> STATES.remove(player.getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(server -> BURNING.entrySet().removeIf(entry -> {
			LivingEntity entity = entry.getKey();
			if (entity.isRemoved() || !entity.isAlive() || !entity.isOnFire() || entity.level().getGameTime() > entry.getValue()) {
				return true;
			}
			if (nearExtinguisher(entity)) {
				// A burning entity entering powder snow melts it, one in a water cauldron empties it: put the fire out first.
				entity.clearFire();
				return true;
			}
			return false;
		}));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			STATES.clear();
			BURNING.clear();
		});
	}

	/** Performs a legacy Dao technique with its cast context. True when it really happened (false spends nothing). */
	public static boolean cast(Caster ctx) {
		Technique technique = ctx.spec().legacy();
		ServerPlayer player = ctx.player();
		if (technique == null || technique.dao() == null || player == null || player.isRemoved() || player.isDeadOrDying()) {
			return false;
		}
		boolean done = switch (technique) {
			case SWORD_QI -> swordQi(ctx, player);
			case THOUSAND_SWORDS -> thousandSwords(ctx, player);
			case FLAME_PALM -> flamePalm(ctx, player);
			case PHOENIX_FLAME -> phoenixFlame(ctx, player);
			case FROST_LOTUS -> frostLotus(ctx, player);
			case GLACIER_PRISON -> glacierPrison(ctx, player);
			case THUNDER_PALM -> thunderPalm(ctx, player);
			case HEAVENLY_THUNDER -> heavenlyThunder(ctx, player);
			case IRON_BODY -> ironBody(ctx, player);
			case MOUNTAIN_STOMP -> mountainStomp(ctx, player);
			case SPRING_OF_LIFE -> springOfLife(ctx, player);
			case LIFE_DRAIN -> lifeDrain(ctx, player);
			case SHADOW_STEP -> shadowStep(ctx, player, false);
			case GALE -> gale(ctx, player);
			case SPACE_BLINK -> spaceBlink(ctx, player);
			case SPACE_PRISON -> spacePrison(ctx, player);
			default -> false;
		};
		if (done && !ctx.copy()) {
			Advancements.grant(player, "dao_technique");
			if (technique.unlock() == Technique.Unlock.BOOK) {
				Advancements.grant(player, "dao_ultimate");
			}
			switch (technique) {
				case THOUSAND_SWORDS -> Advancements.grant(player, "thousand_swords");
				case PHOENIX_FLAME -> Advancements.grant(player, "phoenix_flame");
				case HEAVENLY_THUNDER -> Advancements.grant(player, "heavenly_thunder");
				default -> {
				}
			}
		}
		return done;
	}

	// ================================================================== Dao Domain pulses (22.10.1)

	/**
	 * One pulse of the Dao Domain's law (22.10.1), coefficients × P through the cast's choke point (DOT and pulse ticks
	 * ≤ 2% of a boss each); {@code factor} ×2 for {@code profound_mystery_fusion}. {@code pulseIndex} counts the pulses
	 * (Ice: every 4th from Dao Union freezes). True when the pulse's damage landed on a monster (the Kehancuran impact of
	 * the domain, 22.11.4: the first one carves the Dao Domain's ring around the caster).
	 */
	public static boolean domainPulse(CastContext ctx, @Nullable Dao dao, List<LivingEntity> inside, double areaRadius, double factor, int pulseIndex) {
		LivingEntity caster = ctx.caster();
		if (caster.isRemoved() || caster.isDeadOrDying()) {
			return false;
		}
		boolean struck = false;
		List<LivingEntity> targets = new ArrayList<>();
		for (LivingEntity entity : inside) {
			if (entity.isAlive() && !entity.isRemoved() && entity.level() == caster.level() && ctx.mayHit(entity, false)) {
				targets.add(entity);
			}
		}
		ServerLevel level = ctx.level();
		Vec3 pos = caster.position();
		float radius = (float) Math.max(1.0D, areaRadius);
		if (dao != null) {
			fx(level, flavour(dao), DaoTechniqueSub.DOMAIN, pos, pos, radius);
		}
		boolean arena = ctx.arena();
		if (dao == null) {
			for (LivingEntity target : targets) {
				boolean hit = pulse(ctx, target, 0.4D * factor);
				struck |= hit;
				if (hit && target.isAlive()) {
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, DOMAIN_EFFECT_TICKS, 0, false, true), caster);
				}
			}
			return struck;
		}
		switch (dao) {
			case SWORD -> {
				for (LivingEntity target : targets) {
					struck |= pulse(ctx, target, 0.5D * factor);
					fx(level, Technique.SWORD_QI, DaoTechniqueSub.HIT, middleOf(target), middleOf(target), target.getBbWidth());
				}
				if (!targets.isEmpty()) {
					sound(level, pos, SoundEvents.PLAYER_ATTACK_SWEEP, 0.4F, 1.4F);
				}
			}
			case FIRE -> {
				Payload.Dot dot = new Payload.Dot(0.4D, 3, Payload.DotKind.FIRE, false, Map.of());
				for (LivingEntity target : targets) {
					ignite(target, 3.0F);
					struck |= pulse(ctx, target, 0.3D * factor);
					if (!arena && target.isAlive()) {
						ctx.apply(target, dot);
					}
				}
			}
			case ICE -> {
				boolean deep = !arena && caster instanceof ServerPlayer player
						&& dev.kultivator.data.Cultivators.get(player).realm().atLeast(Realm.DAO_UNION) && pulseIndex % 4 == 3;
				for (LivingEntity target : targets) {
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, DOMAIN_EFFECT_TICKS, 2), caster);
					freezeLook(target, DOMAIN_EFFECT_TICKS);
					if (deep) {
						ctx.apply(target, new Payload.Freeze(80));
					}
				}
			}
			case LIGHTNING -> {
				int bolts = arena ? 1 : Math.min(4, 1 + ctx.tier() / 2);
				List<LivingEntity> pool = new ArrayList<>(targets);
				RandomSource random = caster.getRandom();
				for (int i = 0; i < bolts && !pool.isEmpty(); i++) {
					LivingEntity target = pool.remove(random.nextInt(pool.size()));
					Vec3 ground = target.position();
					fx(level, Technique.THUNDER_PALM, DaoTechniqueSub.BOLT, skyAbove(level, caster, ground), ground, 1.0F);
					struck |= pulse(ctx, target, 0.8D * factor);
					sound(level, ground, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6F, 1.3F);
				}
			}
			case BODY -> {
				caster.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, DOMAIN_EFFECT_TICKS, 1, false, false, true), caster);
				// technique Resistance counts inside the DR cap (22.14.3)
				dev.kultivator.technique.spec.run.Boons.trackResistance(caster, 1, DOMAIN_EFFECT_TICKS);
				if (!arena) {
					for (LivingEntity target : targets) {
						struck |= pulse(ctx, target, 0.4D * factor);
					}
				}
			}
			case LIFE -> {
				for (LivingEntity ally : ctx.allies(pos, radius)) {
					ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, DOMAIN_EFFECT_TICKS, 1, false, false, true), caster);
				}
				for (LivingEntity target : targets) {
					target.addEffect(new MobEffectInstance(MobEffects.WITHER, DOMAIN_EFFECT_TICKS, 0), caster);
					if (!arena && pulse(ctx, target, 0.3D * factor)) {
						struck = true;
						ctx.apply(target, new Payload.Drain(1.0D, 0.0D, 0.0D, 0.0D, 0.0D));
					}
				}
			}
			case SHADOW -> {
				for (LivingEntity target : targets) {
					target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, DOMAIN_EFFECT_TICKS, 0), caster);
					if (!arena) {
						struck |= pulse(ctx, target, 0.4D * factor);
					}
				}
				caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, DOMAIN_EFFECT_TICKS, 0, false, false, true), caster);
			}
			case SPACE -> {
				for (LivingEntity target : targets) {
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, DOMAIN_EFFECT_TICKS, 4), caster);
					if (!arena) {
						struck |= pulse(ctx, target, 0.3D * factor);
					}
					if (target.isAlive() && ctx.moveAllowed(target)) {
						Vec3 to = pos.subtract(target.position()).horizontal();
						if (to.lengthSqr() > 1.0D) {
							Vec3 pull = to.normalize().scale(PULL_SPEED);
							launch(target, target.getDeltaMovement().add(pull.x, 0.0D, pull.z));
						}
					}
				}
			}
		}
		return struck;
	}

	private static boolean pulse(CastContext ctx, LivingEntity target, double coef) {
		return target.isAlive() && ctx.hurt(ctx.damage(target, coef).kind(dev.kultivator.technique.spec.DamageContext.Kind.PULSE));
	}

	// ================================================================== Sword

	/**
	 * Sword Qi Slash: crescents (GC 1, DT 2, IA 3, TI 5; +1 at Kesempurnaan Agung) fanned 20°, each a front of 16→48 blocks
	 * at 2 blocks per tick (stopped by blocks), 1.5 × P to everything in its way once.
	 */
	private static boolean swordQi(Caster ctx, ServerPlayer player) {
		Vec3 look = player.getViewVector(1.0F);
		Vec3 start = player.getEyePosition().add(0.0D, -0.3D, 0.0D).add(look.scale(0.6D));
		Realm realm = dev.kultivator.data.Cultivators.get(player).realm();
		int base = ctx.arena() ? 1 : realm.atLeast(Realm.TRUE_IMMORTAL) ? 5 : realm.atLeast(Realm.IMMORTAL_ASCENSION) ? 3
				: realm.atLeast(Realm.DEITY_TRANSFORMATION) ? 2 : 1;
		int count = base + ctx.bonusCount();
		double length = ctx.area(16.0D, 48.0D);
		double reach = Math.max(1.0D, ctx.area(1.0D, 3.0D) * 0.5D + 0.5D);
		List<Vec3> ends = new ArrayList<>();
		Map<LivingEntity, Double> hits = new HashMap<>();
		Map<LivingEntity, Double> along = new HashMap<>();
		for (int i = 0; i < count; i++) {
			double offset = count <= 1 ? 0.0D : (i - (count - 1) / 2.0D) * 20.0D;
			Vec3 dir = Caster.rotateYaw(look, offset);
			Vec3 end = clipEnd(player, start, dir, length);
			ends.add(end);
			for (LivingEntity target : ctx.hostiles(start.add(end).scale(0.5D), start.distanceTo(end) * 0.5D + 3.0D, true)) {
				if (nearSegment(target, start, end, reach) && !hits.containsKey(target)) {
					hits.put(target, 1.0D);
					along.put(target, middleOf(target).distanceTo(start));
				}
			}
		}
		if (hits.isEmpty()) {
			refuse(player, "no_target_path");
			return false;
		}
		ServerLevel level = player.level();
		for (Vec3 end : ends) {
			fx(level, Technique.SWORD_QI, DaoTechniqueSub.CAST, start, end, (float) (reach * 2.0D));
		}
		ctx.castFx(start, ends.get(0), (float) reach, (float) length, count, (int) Math.ceil(length / 2.0D));
		sound(level, player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.7F);
		sound(level, player.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 1.7F);
		for (Map.Entry<LivingEntity, Double> entry : hits.entrySet()) {
			LivingEntity target = entry.getKey();
			int delay = Math.max(0, (int) Math.round(along.getOrDefault(target, 0.0D) / 2.0D));
			ctx.schedule(delay, () -> {
				if (strike(ctx, target, 1.5D, true)) {
					fx(level, Technique.SWORD_QI, DaoTechniqueSub.HIT, middleOf(target), middleOf(target), target.getBbWidth());
				}
			});
		}
		for (Vec3 end : ends) {
			ctx.hop(end.subtract(look.horizontal().normalize().scale(Math.min(8.0D, start.distanceTo(end)))), look);
		}
		echoPeak(ctx);
		return true;
	}

	/**
	 * Thousand Swords: a wave every 5 ticks over 80→160 ticks; each wave swords of light fall on up to lin(6, 2, 24) random
	 * monsters within 16→32 (+2 at Kesempurnaan Agung), 0.6 × P each; at Puncak Jurus a giant sword on the strongest (3 × P).
	 */
	private static boolean thousandSwords(Caster ctx, ServerPlayer player) {
		double range = ctx.area(16.0D, 32.0D);
		if (ctx.hostiles(player.position(), range, false).isEmpty()) {
			refuse(player, "no_target");
			return false;
		}
		int duration = ctx.arena() ? 80 : ctx.duration(80.0D, 160.0D);
		int perWave = (ctx.arena() ? 6 : (int) ctx.lin(6.0D, 2.0D, 24.0D)) + ctx.bonusCount();
		ServerLevel level = player.level();
		fx(level, Technique.THOUSAND_SWORDS, DaoTechniqueSub.CAST, player.position(), player.position(), (float) range);
		ctx.castFx(player.position(), player.position(), (float) range, 0.0F, perWave, duration);
		sound(level, player.position(), SoundEvents.BEACON_ACTIVATE, 1.0F, 1.6F);
		sound(level, player.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 0.8F);
		Msg.actionBar(player, Component.translatable("kultivator.daotech.swords").withColor(Dao.SWORD.color()));
		int waves = Math.max(1, duration / THOUSAND_SWORDS_INTERVAL);
		for (int wave = 0; wave < waves; wave++) {
			int index = wave;
			ctx.schedule(1 + wave * THOUSAND_SWORDS_INTERVAL, () -> {
				List<LivingEntity> around = new ArrayList<>(ctx.hostiles(player.position(), range, false));
				RandomSource random = player.getRandom();
				for (int i = around.size() - 1; i > 0; i--) {
					int j = random.nextInt(i + 1);
					LivingEntity swap = around.get(i);
					around.set(i, around.get(j));
					around.set(j, swap);
				}
				int count = Math.min(perWave, around.size());
				for (int i = 0; i < count; i++) {
					LivingEntity target = around.get(i);
					int stagger = 1 + random.nextInt(THOUSAND_SWORDS_INTERVAL);
					ctx.schedule(stagger, () -> {
						if (valid(ctx, target)) {
							fx(level, Technique.THOUSAND_SWORDS, DaoTechniqueSub.SWORD_FALL, target.position(), target.position(), SWORD_FALL_TICKS);
							// the falling sword on every client, timed to land with the strike (FLAG_PRE_IMPACT)
							ctx.fx(dev.kultivator.technique.spec.dsl.Phase.IMPACT, target.position().add(0.0D, 14.0D, 0.0D), target.position(), 1.0F, 14.0F,
									1, SWORD_FALL_TICKS, target.getId(), 0, 1, 0, dev.kultivator.network.Payloads.TechFx.FLAG_PRE_IMPACT);
							ctx.schedule(SWORD_FALL_TICKS, () -> {
								if (valid(ctx, target) && strike(ctx, target, 0.6D, false)) {
									sound(level, target.position(), SoundEvents.TRIDENT_HIT, 0.5F, 1.3F + random.nextFloat() * 0.4F);
									ctx.hop(target.position(), ctx.look());
								}
							});
						}
					});
				}
				if (count > 0 && index % 4 == 0) {
					sound(level, player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F, 1.5F);
				}
			});
		}
		ctx.keepAlive(duration + SWORD_FALL_TICKS + THOUSAND_SWORDS_INTERVAL + 2);
		ctx.runBonusSteps();
		return true;
	}

	// ================================================================== Fire

	/** Flame Palm: a cone 7→20 blocks, 30°→45°: 1 × P, a fire DOT of 0.2 × P per second for 5 s and 6 s of burning. */
	private static boolean flamePalm(Caster ctx, ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		double range = ctx.area(7.0D, 20.0D);
		double half = ctx.area(30.0D, 45.0D);
		double cos = Math.cos(Math.toRadians(half));
		List<LivingEntity> hits = new ArrayList<>();
		for (LivingEntity target : ctx.hostiles(eye, range + 1.0D, false)) {
			Vec3 to = middleOf(target).subtract(eye);
			double distance = to.length();
			if (distance > range + target.getBbWidth() * 0.5D) {
				continue;
			}
			boolean inCone = distance < 1.5D ? to.dot(look) > 0.0D : to.scale(1.0D / distance).dot(look) >= cos;
			if (inCone && Targeting.hasLineOfSight(player, target)) {
				hits.add(target);
			}
		}
		if (hits.isEmpty()) {
			refuse(player, "no_target_path");
			return false;
		}
		ServerLevel level = player.level();
		fx(level, Technique.FLAME_PALM, DaoTechniqueSub.CAST, hand(player), eye.add(look.scale(range)), (float) range);
		ctx.castFx(hand(player), eye.add(look.scale(range)), (float) half, (float) range, hits.size(), 0);
		sound(level, player.position(), SoundEvents.FIRECHARGE_USE, 1.0F, 0.8F);
		sound(level, player.position(), SoundEvents.BLAZE_SHOOT, 0.8F, 1.2F);
		boolean deep = ctx.special("ignite_dot");
		Payload.Dot dot = new Payload.Dot(0.2D, deep ? 6 : 5, Payload.DotKind.FIRE, false, Map.of());
		for (LivingEntity target : hits) {
			strike(ctx, target, 1.0D, false);
			if (target.isAlive()) {
				ignite(target, deep ? 7.0F : 6.0F);
				if (!ctx.arena()) {
					ctx.apply(target, dot);
				}
			}
			fx(level, Technique.FLAME_PALM, DaoTechniqueSub.HIT, middleOf(target), middleOf(target), target.getBbWidth());
		}
		ctx.impact(player.position(), look);
		echoPeak(ctx);
		return true;
	}

	/**
	 * Phoenix Flame: phoenixes (NS 1, IA 2, TI 3; +1 at Kesempurnaan Agung) fanned 20°, each flying one block per tick for
	 * 24→64 blocks (stopped by blocks): 2 × P and 8 s of burning to everything within 1.8→3 blocks of its path.
	 */
	private static boolean phoenixFlame(Caster ctx, ServerPlayer player) {
		Vec3 look = player.getViewVector(1.0F);
		Vec3 start = player.getEyePosition().add(0.0D, -0.2D, 0.0D).add(look.scale(0.8D));
		Realm realm = dev.kultivator.data.Cultivators.get(player).realm();
		int base = ctx.arena() ? 1 : realm.atLeast(Realm.TRUE_IMMORTAL) ? 3 : realm.atLeast(Realm.IMMORTAL_ASCENSION) ? 2 : 1;
		int count = base + ctx.bonusCount();
		double range = ctx.area(24.0D, 64.0D);
		double reach = ctx.area(1.8D, 3.0D);
		List<Vec3[]> paths = new ArrayList<>();
		boolean any = false;
		for (int i = 0; i < count; i++) {
			double offset = count <= 1 ? 0.0D : (i - (count - 1) / 2.0D) * 20.0D;
			Vec3 dir = Caster.rotateYaw(look, offset);
			Vec3 end = clipEnd(player, start, dir, range);
			paths.add(new Vec3[]{start, end});
			if (!any) {
				for (LivingEntity target : ctx.hostiles(start.add(end).scale(0.5D), start.distanceTo(end) * 0.5D + 3.0D, false)) {
					if (nearSegment(target, start, end, reach + 0.7D)) {
						any = true;
						break;
					}
				}
			}
		}
		if (!any) {
			refuse(player, "no_target_path");
			return false;
		}
		DaoTechniqueState state = state(player);
		ServerLevel level = player.level();
		int longest = 1;
		for (Vec3[] path : paths) {
			double length = path[0].distanceTo(path[1]);
			int ticks = Math.max(1, (int) Math.ceil(length));
			longest = Math.max(longest, ticks);
			Vec3 step = length < 1.0E-4D ? look : path[1].subtract(path[0]).scale(1.0D / ticks);
			state.phoenixes.add(new DaoTechniqueState.Phoenix(ctx, path[0], step, ticks, 2.0D, reach));
			fx(level, Technique.PHOENIX_FLAME, DaoTechniqueSub.CAST, path[0], path[1], ticks);
		}
		ctx.castFx(start, paths.get(0)[1], (float) reach, (float) range, count, longest);
		sound(level, player.position(), SoundEvents.BLAZE_SHOOT, 1.0F, 0.6F);
		sound(level, player.position(), SoundEvents.ENDER_DRAGON_FLAP, 1.0F, 1.4F);
		sound(level, player.position(), SoundEvents.FIRECHARGE_USE, 0.8F, 0.7F);
		ctx.keepAlive(longest + 2);
		echoPeak(ctx);
		return true;
	}

	private static void tickPhoenixes(ServerPlayer player, DaoTechniqueState state) {
		Iterator<DaoTechniqueState.Phoenix> iterator = state.phoenixes.iterator();
		ServerLevel level = player.level();
		while (iterator.hasNext()) {
			DaoTechniqueState.Phoenix phoenix = iterator.next();
			Vec3 next = phoenix.pos.add(phoenix.step);
			BlockHitResult wall = dev.kultivator.util.LoadedPath.ray(level, phoenix.pos, next, player);
			boolean blocked = wall.getType() != HitResult.Type.MISS;
			if (blocked) {
				next = wall.getLocation();
			}
			if (phoenix.age % 4 == 0 || blocked) {
				// One query covers the next 4 blocks of flight.
				Vec3 ahead = next.add(phoenix.step.scale(3.0D));
				for (LivingEntity target : phoenix.ctx.hostiles(next.add(ahead).scale(0.5D), phoenix.reach + 4.5D, false)) {
					if (!phoenix.hit.contains(target.getId()) && nearSegment(target, phoenix.pos, ahead, phoenix.reach)) {
						phoenix.hit.add(target.getId());
						int delay = Math.max(0, (int) Math.round(middleOf(target).distanceTo(phoenix.pos) / Math.max(0.2D, phoenix.step.length())));
						phoenix.ctx.schedule(delay, () -> {
							if (strike(phoenix.ctx, target, phoenix.coef, false) && target.isAlive()) {
								ignite(target, 8.0F);
							}
							if (target.isAlive()) {
								fx(level, Technique.PHOENIX_FLAME, DaoTechniqueSub.HIT, middleOf(target), middleOf(target), target.getBbWidth());
							}
						});
					}
				}
			}
			phoenix.pos = next;
			phoenix.ticksLeft--;
			phoenix.age++;
			if (phoenix.ticksLeft % 6 == 0) {
				sound(level, next, SoundEvents.PHANTOM_FLAP, 0.8F, 0.7F);
			}
			if (blocked || phoenix.ticksLeft <= 0 || phoenix.ctx.ended()) {
				iterator.remove();
				if (phoenix.ctx instanceof Caster cast) {
					cast.hop(next, phoenix.step);
				}
				sound(level, next, SoundEvents.BLAZE_SHOOT, 0.7F, 0.5F);
				sound(level, next, SoundEvents.FIRE_EXTINGUISH, 0.5F, 1.2F);
			}
		}
	}

	// ================================================================== Ice

	/**
	 * Frost Lotus: a nova of 6→18 blocks: 1 × P, Slowness IV for 6 s and frozen 240 ticks (+40 at Kesempurnaan Agung); at
	 * Puncak Jurus ice spikes ride a wave out to the same radius 20 ticks later (0.5 × P, shatter).
	 */
	private static boolean frostLotus(Caster ctx, ServerPlayer player) {
		double radius = ctx.area(6.0D, 18.0D);
		List<LivingEntity> hits = ctx.hostiles(player.position(), radius, false);
		if (hits.isEmpty()) {
			refuse(player, "no_target");
			return false;
		}
		ctx.setLastAreaRadius(radius);
		ServerLevel level = player.level();
		fx(level, Technique.FROST_LOTUS, DaoTechniqueSub.CAST, player.position(), player.position(), (float) radius);
		ctx.castFx(player.position(), player.position(), (float) radius, 0.0F, hits.size(), 0);
		sound(level, player.position(), SoundEvents.POWDER_SNOW_BREAK, 1.2F, 0.6F);
		sound(level, player.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 1.5F);
		sound(level, player.position(), SoundEvents.PLAYER_HURT_FREEZE, 0.7F, 0.8F);
		int freeze = 240 + (ctx.perfection(Bonus.Kind.PAYLOAD) ? 40 : 0);
		for (LivingEntity target : hits) {
			strike(ctx, target, 1.0D, false);
			if (target.isAlive()) {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 120, 3), player);
				ctx.apply(target, new Payload.Freeze(freeze));
			}
			fx(level, Technique.FROST_LOTUS, DaoTechniqueSub.HIT, middleOf(target), middleOf(target), target.getBbWidth());
		}
		ctx.impact(player.position(), ctx.look());
		ctx.runBonusSteps();
		return true;
	}

	/**
	 * Glacier Prison: the looked-at monster (24→48) and every monster within 5→8 of it: 1.5 × P and rooted 160 ticks
	 * (bosses 40; +40 at Kesempurnaan Agung). At Puncak Jurus the prisoners shatter at release (1 × P, ×1.5 on the frozen).
	 */
	private static boolean glacierPrison(Caster ctx, ServerPlayer player) {
		double range = ctx.area(24.0D, 48.0D);
		LivingEntity main = ctx.target() != null && ctx.target().distanceTo(player) <= range + 2.0D ? ctx.target()
				: Targeting.lookedAtHostile(player, range);
		if (main == null) {
			refuse(player, "no_target_look");
			return false;
		}
		double splash = ctx.area(5.0D, 8.0D);
		List<LivingEntity> victims = new ArrayList<>();
		victims.add(main);
		for (LivingEntity other : ctx.hostiles(main.position(), splash, false)) {
			if (other != main) {
				victims.add(other);
			}
		}
		ServerLevel level = player.level();
		fx(level, Technique.GLACIER_PRISON, DaoTechniqueSub.CAST, middleOf(main), hand(player), (float) splash);
		ctx.castFx(middleOf(main), hand(player), (float) splash, 0.0F, victims.size(), 0, main.getId());
		sound(level, main.position(), SoundEvents.POWDER_SNOW_BREAK, 1.2F, 0.5F);
		sound(level, main.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 0.6F);
		sound(level, main.position(), SoundEvents.GLASS_PLACE, 1.0F, 0.7F);
		int ticks = ctx.duration(160.0D, 160.0D);
		int sealed = 0;
		boolean struck = false;
		for (LivingEntity victim : victims) {
			struck |= strike(ctx, victim, 1.5D, victim == main);
			if (victim.isAlive()) {
				ctx.apply(victim, new Payload.Bind(Payload.BindKind.ROOT, dev.kultivator.technique.spec.dsl.Val.of(ticks), 40));
				freezeLook(victim, 20);
				fx(level, Technique.GLACIER_PRISON, DaoTechniqueSub.MARK, victim.position(), new Vec3(victim.getId(), ticks, 0.0D), victim.getBbHeight());
				// one prison per sealed monster on every client (pos = its body, target = its id)
				ctx.fx(dev.kultivator.technique.spec.dsl.Phase.IMPACT, middleOf(victim), hand(player), victim.getBbWidth(), 0.0F, 1, ticks,
						victim.getId(), sealed, victims.size(), 0, 0);
				sealed++;
			}
		}
		ctx.schedule(ticks, () -> {
			for (LivingEntity victim : victims) {
				if (victim.isAlive() && !victim.isRemoved()) {
					fx(level, Technique.GLACIER_PRISON, DaoTechniqueSub.END, middleOf(victim), new Vec3(victim.getId(), 0.0D, 0.0D), victim.getBbHeight());
					sound(level, victim.position(), SoundEvents.GLASS_BREAK, 0.8F, 1.0F);
				}
			}
		});
		if (struck) {
			// the Kehancuran impact (22.11.4): the ice shatters a crater under the sealed target
			ctx.impact(main.position(), ctx.look());
		}
		ctx.keepAlive(ticks + 1);
		ctx.runBonusSteps();
		Msg.actionBar(player, Component.translatable("kultivator.daotech.glacier", String.valueOf(sealed)).withColor(Dao.ICE.color()));
		return true;
	}

	// ================================================================== Lightning

	/** Thunder Palm: a bolt to the looked-at monster (24→48), then lin(4, 1, 10) hops (+2) to monsters within 6→12, 1 × P each. */
	private static boolean thunderPalm(Caster ctx, ServerPlayer player) {
		double range = ctx.area(24.0D, 48.0D);
		LivingEntity main = ctx.target() != null && ctx.target().distanceTo(player) <= range + 2.0D ? ctx.target()
				: Targeting.lookedAtHostile(player, range);
		if (main == null) {
			refuse(player, "no_target_look");
			return false;
		}
		ServerLevel level = player.level();
		Vec3 hand = hand(player);
		fx(level, Technique.THUNDER_PALM, DaoTechniqueSub.CAST, hand, middleOf(main), 1.0F);
		fx(level, Technique.THUNDER_PALM, DaoTechniqueSub.BOLT, hand, middleOf(main), 1.0F);
		int jumps = (ctx.arena() ? 4 : (int) ctx.lin(4.0D, 1.0D, 10.0D)) + ctx.bonusChain();
		double jumpRange = ctx.area(6.0D, 12.0D);
		ctx.castFx(hand, middleOf(main), (float) jumpRange, (float) hand.distanceTo(middleOf(main)), jumps + 1, jumps * 3);
		sound(level, main.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0F, 1.2F);
		sound(level, player.position(), SoundEvents.FIRECHARGE_USE, 0.5F, 1.8F);
		Set<Integer> chained = new HashSet<>();
		chained.add(main.getId());
		LivingEntity previous = main;
		for (int jump = 1; jump <= jumps; jump++) {
			LivingEntity next = null;
			for (LivingEntity candidate : ctx.hostiles(previous.position(), jumpRange, false)) {
				if (!chained.contains(candidate.getId())) {
					next = candidate;
					break;
				}
			}
			if (next == null) {
				break;
			}
			chained.add(next.getId());
			LivingEntity from = previous;
			Vec3 fromPos = middleOf(previous);
			LivingEntity hop = next;
			ctx.schedule(3 * jump, () -> {
				if (valid(ctx, hop)) {
					Vec3 origin = from.isRemoved() ? fromPos : middleOf(from);
					fx(level, Technique.THUNDER_PALM, DaoTechniqueSub.BOLT, origin, middleOf(hop), 0.8F);
					strike(ctx, hop, 1.0D, false);
					ctx.hop(hop.position(), ctx.look());
					sound(level, hop.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6F, 1.5F + player.getRandom().nextFloat() * 0.3F);
				}
			});
			previous = next;
		}
		strike(ctx, main, 1.0D, true);
		ctx.hop(main.position(), ctx.look());
		ctx.keepAlive(3 * jumps + 1);
		echoPeak(ctx);
		return true;
	}

	/**
	 * Heavenly Thunder: lin(8, 2, 24) particle bolts (+4) over 40 ticks on monsters within 16→32 (2 × P each, preferring
	 * ones not struck yet); at Puncak Jurus a converging bolt on the strongest at tick 50 (4 × P).
	 */
	private static boolean heavenlyThunder(Caster ctx, ServerPlayer player) {
		double range = ctx.area(16.0D, 32.0D);
		if (ctx.hostiles(player.position(), range, false).isEmpty()) {
			refuse(player, "no_target");
			return false;
		}
		int bolts = (ctx.arena() ? 8 : (int) ctx.lin(8.0D, 2.0D, 24.0D)) + ctx.bonusCount();
		ServerLevel level = player.level();
		fx(level, Technique.HEAVENLY_THUNDER, DaoTechniqueSub.CAST, player.position(), player.position(), (float) range);
		ctx.castFx(player.position(), player.position(), (float) range, 0.0F, bolts, 40);
		sound(level, player.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.7F, 1.1F);
		Set<Integer> struck = new HashSet<>();
		for (int i = 0; i < bolts; i++) {
			int delay = 1 + (int) Math.round(40.0D * i / Math.max(1, bolts));
			ctx.schedule(delay, () -> skyBolt(ctx, player, range, struck));
		}
		ctx.keepAlive(43);
		ctx.runBonusSteps();
		return true;
	}

	private static void skyBolt(Caster ctx, ServerPlayer player, double range, Set<Integer> struck) {
		List<LivingEntity> around = ctx.hostiles(player.position(), range, false);
		if (around.isEmpty()) {
			return;
		}
		List<LivingEntity> fresh = new ArrayList<>();
		for (LivingEntity candidate : around) {
			if (!struck.contains(candidate.getId())) {
				fresh.add(candidate);
			}
		}
		List<LivingEntity> pool = fresh.isEmpty() ? around : fresh;
		RandomSource random = player.getRandom();
		LivingEntity target = pool.get(random.nextInt(pool.size()));
		struck.add(target.getId());
		Vec3 ground = target.position();
		ServerLevel level = player.level();
		fx(level, Technique.HEAVENLY_THUNDER, DaoTechniqueSub.BOLT, skyAbove(level, player, ground), ground, 2.0F);
		strike(ctx, target, 2.0D, false);
		ctx.hop(ground, ctx.look());
		sound(level, ground, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0F, 0.9F + random.nextFloat() * 0.3F);
		if (random.nextInt(3) == 0) {
			sound(level, ground, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.4F, 1.3F);
		}
	}

	// ================================================================== Body

	/**
	 * Iron Body: 300 ticks → (D): Resistance II (Kenaikan Abadi III), knockback resistance +1 and 30% of every melee blow
	 * thrown back (Dewa Abadi 50%) through the choke point and this cast's cap. +100 ticks at Kesempurnaan Agung; at Puncak
	 * Jurus a nova of 4 blocks when it ends (1 × P, knockback 0.8).
	 */
	private static boolean ironBody(Caster ctx, ServerPlayer player) {
		Realm realm = dev.kultivator.data.Cultivators.get(player).realm();
		int ticks = ctx.duration(300.0D, 600.0D);
		int resistance = realm.atLeast(Realm.IMMORTAL_ASCENSION) && !ctx.arena() ? 3 : 2;
		ctx.applySelf(new Payload.Buff(Payload.BuffKind.RESISTANCE, resistance, 0.0D, dev.kultivator.technique.spec.dsl.Val.of(ticks), null, false, false, 1));
		ctx.applySelf(new Payload.Buff(Payload.BuffKind.KNOCKBACK_RESISTANCE, 1.0D, 0.0D, dev.kultivator.technique.spec.dsl.Val.of(ticks), null, false, false, 1));
		double reflect = realm.atLeast(Realm.TRUE_IMMORTAL) && !ctx.arena() ? 0.5D : 0.3D;
		ctx.applySelf(new Payload.Buff(Payload.BuffKind.REFLECT, reflect, 0.0D, dev.kultivator.technique.spec.dsl.Val.of(ticks), null, false, false, 1));
		ServerLevel level = player.level();
		fx(level, Technique.IRON_BODY, DaoTechniqueSub.CAST, player.position(), player.position(), player.getBbHeight());
		fx(level, Technique.IRON_BODY, DaoTechniqueSub.MARK, player.position(), new Vec3(player.getId(), ticks, 0.0D), player.getBbHeight());
		ctx.castFx(player.position(), player.position(), player.getBbHeight(), 0.0F, 1, ticks);
		sound(level, player.position(), SoundEvents.ANVIL_LAND, 0.5F, 1.4F);
		sound(level, player.position(), SoundEvents.ARMOR_EQUIP_IRON, 1.0F, 0.8F);
		sound(level, player.position(), SoundEvents.IRON_GOLEM_REPAIR, 1.0F, 0.9F);
		Msg.actionBar(player, Component.translatable("kultivator.daotech.iron_body_on").withColor(Dao.BODY.color()));
		ctx.schedule(ticks, () -> {
			fx(level, Technique.IRON_BODY, DaoTechniqueSub.END, middleOf(player), new Vec3(player.getId(), 0.0D, 0.0D), player.getBbHeight());
			sound(level, player.position(), SoundEvents.ARMOR_EQUIP_IRON, 0.6F, 0.6F);
			Msg.actionBar(player, Component.translatable("kultivator.daotech.iron_body_off").withColor(0xA8B5B0));
		});
		ctx.keepAlive(ticks + 1);
		ctx.runBonusSteps();
		return true;
	}

	/**
	 * Mountain Stomp: a shockwave of 7→20 blocks: 2 × P, thrown up (launch 0.9; the never-moved list and the portal rule
	 * apply per monster) and Slowness II for 4 s; aftershock waves lin(0, 1 per 3 tiers, 2) (+1) of 0.8 × P each.
	 */
	private static boolean mountainStomp(Caster ctx, ServerPlayer player) {
		double radius = ctx.area(7.0D, 20.0D);
		List<LivingEntity> hits = ctx.hostiles(player.position(), radius, false);
		if (hits.isEmpty()) {
			refuse(player, "no_target");
			return false;
		}
		ServerLevel level = player.level();
		fx(level, Technique.MOUNTAIN_STOMP, DaoTechniqueSub.CAST, player.position(), player.position(), (float) radius);
		ctx.castFx(player.position(), player.position(), (float) radius, 0.0F, hits.size(), 0);
		sound(level, player.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.2F, 0.7F);
		sound(level, player.position(), SoundEvents.ANVIL_LAND, 0.6F, 0.5F);
		Payload.Move up = new Payload.Move(Payload.MoveKind.LAUNCH, dev.kultivator.technique.spec.dsl.Val.of(0.9D), Payload.Center.CASTER, false, 0,
				false, false, false);
		for (LivingEntity target : hits) {
			boolean landed = strike(ctx, target, 2.0D, false);
			if (target.isAlive()) {
				if (landed && !(target instanceof Player)) {
					ctx.applyHit(target, up, false, player.position(), radius);
				}
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 1), player);
			}
			fx(level, Technique.MOUNTAIN_STOMP, DaoTechniqueSub.HIT, middleOf(target), middleOf(target), target.getBbWidth());
		}
		ctx.impact(player.position(), ctx.look());
		int aftershocks = ctx.arena() ? 0 : Math.min(2, ctx.factors().above() / 3) + ctx.bonusWaves();
		ctx.setLastAreaRadius(radius);
		for (int i = 1; i <= aftershocks; i++) {
			ctx.run(Step.at(10 * i).shape(Shape.Wave.DEFAULT.r1(radius).speed(1.5D)).hit(dmg(0.8D)));
		}
		echoPeak(ctx);
		return true;
	}

	// ================================================================== Life

	/**
	 * Spring of Life: the caster and the allies within 8→16: 40% of max health (Dewa Abadi 60%), Poison and Wither cured
	 * (every listed effect at Kesempurnaan Agung) and Regeneration I for 5 s (II for 10 s at Puncak Jurus).
	 */
	private static boolean springOfLife(Caster ctx, ServerPlayer player) {
		double radius = ctx.area(8.0D, 16.0D);
		List<LivingEntity> allies = new ArrayList<>(ctx.allies(player.position(), radius));
		if (!allies.contains(player)) {
			allies.add(0, player);
		}
		allies.removeIf(Entity::isSpectator);
		boolean needed = false;
		for (LivingEntity ally : allies) {
			if (ally.getHealth() < ally.getMaxHealth() - 0.01F || ally.hasEffect(MobEffects.POISON) || ally.hasEffect(MobEffects.WITHER)) {
				needed = true;
				break;
			}
		}
		if (!needed) {
			refuse(player, "nothing_to_heal");
			return false;
		}
		ServerLevel level = player.level();
		fx(level, Technique.SPRING_OF_LIFE, DaoTechniqueSub.CAST, player.position(), player.position(), (float) radius);
		ctx.castFx(player.position(), player.position(), (float) radius, 0.0F, allies.size(), 0);
		sound(level, player.position(), SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.6F);
		sound(level, player.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.2F);
		boolean deep = !ctx.arena() && dev.kultivator.data.Cultivators.get(player).realm().atLeast(Realm.TRUE_IMMORTAL);
		boolean fullCleanse = ctx.perfection(Bonus.Kind.PAYLOAD);
		boolean strongRegen = ctx.peak(Bonus.Kind.PAYLOAD);
		for (LivingEntity ally : allies) {
			ally.heal(ally.getMaxHealth() * (deep ? 0.6F : 0.4F));
			if (fullCleanse) {
				for (var effect : Payload.Cleanse.DEFAULT) {
					ally.removeEffect(effect);
				}
			} else {
				ally.removeEffect(MobEffects.POISON);
				ally.removeEffect(MobEffects.WITHER);
			}
			ally.addEffect(strongRegen ? new MobEffectInstance(MobEffects.REGENERATION, 200, 1) : new MobEffectInstance(MobEffects.REGENERATION, 100, 0), player);
			fx(level, Technique.SPRING_OF_LIFE, DaoTechniqueSub.HEAL, ally.position(), ally.position(), ally.getBbHeight());
		}
		return true;
	}

	/**
	 * Life Drain: every monster within 8→16 takes 1 × P; the caster heals half of what was really dealt (0.6 at
	 * Kesempurnaan Agung), overheal becoming absorption up to 20% of max health; at Puncak Jurus also 10% of it as qi.
	 */
	private static boolean lifeDrain(Caster ctx, ServerPlayer player) {
		double radius = ctx.area(8.0D, 16.0D);
		List<LivingEntity> hits = ctx.hostiles(player.position(), radius, false);
		if (hits.isEmpty()) {
			refuse(player, "no_target");
			return false;
		}
		ServerLevel level = player.level();
		Vec3 heart = middleOf(player);
		double share = ctx.perfection(Bonus.Kind.PAYLOAD) ? 0.6D : 0.5D;
		double qi = ctx.peak(Bonus.Kind.ECHO) && !ctx.copy() ? 0.1D : 0.0D;
		Payload.Drain drain = new Payload.Drain(share, 0.0D, 0.0D, qi, ctx.arena() ? 0.0D : 0.2D);
		double dealt = 0.0D;
		for (LivingEntity target : hits) {
			if (strike(ctx, target, 1.0D, false)) {
				dealt += ctx.lastDealt();
				ctx.apply(target, drain);
			}
			fx(level, Technique.LIFE_DRAIN, DaoTechniqueSub.DRAIN, middleOf(target), heart, target.getBbHeight());
		}
		fx(level, Technique.LIFE_DRAIN, DaoTechniqueSub.CAST, player.position(), player.position(), (float) radius);
		fx(level, Technique.LIFE_DRAIN, DaoTechniqueSub.HEAL, player.position(), player.position(), player.getBbHeight());
		ctx.castFx(player.position(), player.position(), (float) radius, 0.0F, hits.size(), 0);
		sound(level, player.position(), SoundEvents.EVOKER_CAST_SPELL, 1.0F, 0.7F);
		sound(level, player.position(), SoundEvents.WARDEN_HEARTBEAT, 1.0F, 1.3F);
		Msg.actionBar(player, Component.translatable("kultivator.daotech.drained", String.format(Locale.ROOT, "%.1f", dealt * share))
				.withColor(Dao.LIFE.color()));
		ctx.impact(player.position(), ctx.look());
		echoPeak(ctx);
		return true;
	}

	// ================================================================== Shadow & Wind

	/**
	 * Shadow Step: a safe step behind the looked-at monster (20→32; never behind a creeper); the next melee hit within 5 s
	 * lands ×2 (Kenaikan Abadi ×3). From Deity Transformation a second step behind another monster within 20 may follow
	 * within 60 ticks (+20 at Kesempurnaan Agung). At Puncak Jurus an afterimage bursts 10 ticks later (0.5 × P).
	 */
	private static boolean shadowStep(Caster ctx, ServerPlayer player, boolean second) {
		double range = second ? 20.0D : ctx.area(20.0D, 32.0D);
		DaoTechniqueState state = state(player);
		LivingEntity target;
		if (second) {
			target = null;
			for (LivingEntity candidate : ctx.hostiles(player.position(), range, true)) {
				if (!state.shadowVisited.contains(candidate.getId()) && !(candidate instanceof Creeper)) {
					target = candidate;
					break;
				}
			}
		} else {
			target = ctx.target() != null && ctx.target().distanceTo(player) <= range + 2.0D ? ctx.target() : Targeting.lookedAtHostile(player, range);
		}
		if (target == null) {
			refuse(player, "no_target_look");
			return false;
		}
		if (target instanceof Creeper) {
			refuse(player, "creeper");
			return false;
		}
		ServerLevel level = player.level();
		Vec3 from = player.position();
		Vec3 approach = target.position().subtract(from).horizontal();
		if (approach.lengthSqr() < 1.0E-4D) {
			approach = player.getViewVector(1.0F).horizontal();
		}
		approach = approach.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : approach.normalize();
		double base = target.getBbWidth() * 0.5D + player.getBbWidth() * 0.5D;
		Vec3 spot = null;
		for (double gap : new double[]{0.9D, 0.5D, 1.5D}) {
			Vec3 candidate = ctx.safeSpot(target.position().add(approach.scale(base + gap)));
			if (candidate != null && Math.abs(candidate.y - target.getY()) <= 2.5D
					&& clearBetween(player, middleOf(target), candidate.add(0.0D, player.getBbHeight() * 0.5D, 0.0D))) {
				spot = candidate;
				break;
			}
		}
		if (spot == null) {
			refuse(player, "no_room_behind");
			return false;
		}
		Vec3 look = middleOf(target).subtract(spot.add(0.0D, player.getEyeHeight(), 0.0D));
		float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float) (-(Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG));
		fx(level, Technique.SHADOW_STEP, DaoTechniqueSub.CAST, from, spot, player.getBbHeight());
		sound(level, from, SoundEvents.ENDERMAN_TELEPORT, 0.7F, 1.4F);
		sound(level, from, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.8F, 1.0F);
		player.teleportTo(level, spot.x, spot.y, spot.z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
		player.resetFallDistance();
		ctx.markPath(from, spot);
		ctx.addAfterimage(from);
		ctx.castFx(from, spot, player.getBbWidth(), (float) from.distanceTo(spot), 1, 1);
		sound(level, spot, SoundEvents.ENDERMAN_TELEPORT, 0.7F, 1.6F);
		sound(level, spot, SoundEvents.SQUID_SQUIRT, 0.6F, 0.6F);
		long now = level.getGameTime();
		Realm realm = dev.kultivator.data.Cultivators.get(player).realm();
		state.shadowUntil = now + 100L;
		state.shadowFactor = realm.atLeast(Realm.IMMORTAL_ASCENSION) && !ctx.arena() ? 3.0D : 2.0D;
		state.shadowCast = ctx;
		state.shadowVisited.add(target.getId());
		if (!second && !ctx.arena() && realm.atLeast(Realm.DEITY_TRANSFORMATION)) {
			state.shadowWindowUntil = now + 60L + (ctx.perfection(Bonus.Kind.DURATION_TICKS) ? 20L : 0L);
		} else if (second) {
			state.shadowWindowUntil = 0L;
		}
		fx(level, Technique.SHADOW_STEP, DaoTechniqueSub.MARK, spot, new Vec3(player.getId(), 100, 0.0D), player.getBbHeight());
		Msg.actionBar(player, Component.translatable("kultivator.daotech.shadow_ready").withColor(Dao.SHADOW.color()));
		ctx.keepAlive(100);
		if (!second) {
			ctx.runBonusSteps();
		}
		return true;
	}

	/** The Shadow Step stage-2 recast inside its window (skips the cooldown). True when consumed. */
	public static boolean shadowRecast(Caster ctx, ServerPlayer player) {
		DaoTechniqueState state = STATES.get(player.getUUID());
		if (state == null || player.level().getGameTime() >= state.shadowWindowUntil) {
			return false;
		}
		return shadowStep(ctx, player, true);
	}

	/** True while the Shadow Step stage-2 window is open. */
	public static boolean shadowWindow(ServerPlayer player) {
		DaoTechniqueState state = STATES.get(player.getUUID());
		return state != null && player.level().getGameTime() < state.shadowWindowUntil;
	}

	/**
	 * Gale: a storm wind of 10→20 blocks: 0.8 × P (not in an arena) and every monster hurled away, 0.9→2.6 by closeness × A
	 * (the never-moved list and the portal rule apply per monster); the caster turns invisible and swift for 120 ticks → (D)
	 * (+40 at Kesempurnaan Agung).
	 */
	private static boolean gale(Caster ctx, ServerPlayer player) {
		double radius = ctx.area(10.0D, 20.0D);
		List<LivingEntity> blown = ctx.hostiles(player.position(), radius, false);
		Payload.Move knock = new Payload.Move(Payload.MoveKind.KNOCK, dev.kultivator.technique.spec.dsl.Val.of(0.9D, 2.6D), Payload.Center.CASTER, false,
				0, false, true, false);
		for (LivingEntity target : blown) {
			if (!ctx.arena()) {
				strike(ctx, target, 0.8D, false);
			}
			if (target.isAlive() && !(target instanceof Player)) {
				ctx.applyHit(target, knock, false, player.position(), radius);
			}
		}
		int ticks = ctx.duration(120.0D, 240.0D);
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false, true), player);
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 1, false, false, true), player);
		ServerLevel level = player.level();
		fx(level, Technique.GALE, DaoTechniqueSub.CAST, player.position(), player.position(), (float) radius);
		ctx.castFx(player.position(), player.position(), (float) radius, 0.0F, blown.size(), ticks);
		sound(level, player.position(), SoundEvents.BREEZE_WIND_CHARGE_BURST, 1.0F, 0.8F);
		sound(level, player.position(), SoundEvents.PHANTOM_SWOOP, 0.7F, 1.5F);
		if (!blown.isEmpty()) {
			ctx.impact(player.position(), ctx.look());
		}
		echoPeak(ctx);
		return true;
	}

	// ================================================================== Space

	/** Space Blink: folds space to the looked-at spot (24→64), only onto a safe spot reached through open, loaded space. */
	private static boolean spaceBlink(Caster ctx, ServerPlayer player) {
		ServerLevel level = player.level();
		double range = ctx.area(24.0D, 64.0D);
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = dev.kultivator.util.LoadedPath.ray(level, eye, eye.add(player.getViewVector(1.0F).scale(range)), player);
		Vec3 aim = hit.getLocation();
		if (hit.getType() != HitResult.Type.MISS) {
			net.minecraft.core.Direction face = hit.getDirection();
			double dy = face == net.minecraft.core.Direction.DOWN ? -player.getBbHeight() - 0.1D : face.getStepY() * 0.1D;
			aim = aim.add(face.getStepX() * 0.6D, dy, face.getStepZ() * 0.6D);
		}
		Vec3 spot = ctx.safeSpot(aim);
		if (spot == null) {
			refuse(player, "no_safe_spot");
			return false;
		}
		Vec3 middle = spot.add(0.0D, player.getBbHeight() * 0.5D, 0.0D);
		if (!clearBetween(player, aim, middle)
				|| !clearBetween(player, eye, spot.add(0.0D, player.getEyeHeight(), 0.0D)) && !clearBetween(player, eye, middle)) {
			refuse(player, "no_safe_spot");
			return false;
		}
		Vec3 from = player.position();
		fx(level, Technique.SPACE_BLINK, DaoTechniqueSub.CAST, from, spot, player.getBbHeight());
		ctx.castFx(from, spot, player.getBbWidth(), (float) from.distanceTo(spot), 1, 1);
		sound(level, from, SoundEvents.ENDERMAN_TELEPORT, 0.8F, 0.7F);
		player.teleportTo(level, spot.x, spot.y, spot.z, Relative.ROTATION, 0.0F, 0.0F, false);
		player.resetFallDistance();
		ctx.markPath(from, spot);
		ctx.addAfterimage(from);
		sound(level, spot, SoundEvents.ENDERMAN_TELEPORT, 0.8F, 1.2F);
		sound(level, spot, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 1.8F);
		ctx.runBonusSteps();
		return true;
	}

	/**
	 * Space Prison: every monster within 12→24 takes 1.5 × P, is rooted 120 ticks (+20 at Kesempurnaan Agung) and pulled
	 * together 0.3 blocks per tick (never-moved list; near a portal it is only held). At Puncak Jurus the prison implodes
	 * at release (1.5 × P).
	 */
	private static boolean spacePrison(Caster ctx, ServerPlayer player) {
		double radius = ctx.area(12.0D, 24.0D);
		List<LivingEntity> hits = ctx.hostiles(player.position(), radius, false);
		if (hits.isEmpty()) {
			refuse(player, "no_target");
			return false;
		}
		double x = 0.0D;
		double y = 0.0D;
		double z = 0.0D;
		for (LivingEntity target : hits) {
			x += target.getX();
			y += target.getY();
			z += target.getZ();
		}
		Vec3 centre = new Vec3(x / hits.size(), y / hits.size(), z / hits.size());
		double spread = 2.0D;
		for (LivingEntity target : hits) {
			spread = Math.max(spread, target.position().distanceTo(centre) + 1.0D);
		}
		ServerLevel level = player.level();
		fx(level, Technique.SPACE_PRISON, DaoTechniqueSub.CAST, centre, player.position(), (float) spread);
		ctx.castFx(centre, player.position(), (float) spread, 0.0F, hits.size(), 120);
		sound(level, centre, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.0F, 0.6F);
		sound(level, centre, SoundEvents.ILLUSIONER_CAST_SPELL, 1.0F, 0.8F);
		int ticks = ctx.duration(120.0D, 120.0D);
		Payload.Move pull = new Payload.Move(Payload.MoveKind.PULL, dev.kultivator.technique.spec.dsl.Val.of(PULL_SPEED), Payload.Center.ANCHOR, true,
				ticks, false, false, false);
		int locked = 0;
		for (LivingEntity target : hits) {
			strike(ctx, target, 1.5D, false);
			if (target.isAlive()) {
				ctx.apply(target, new Payload.Bind(Payload.BindKind.ROOT, dev.kultivator.technique.spec.dsl.Val.of(ticks), -1));
				ctx.applyHit(target, pull, false, centre, spread);
				fx(level, Technique.SPACE_PRISON, DaoTechniqueSub.MARK, centre, new Vec3(target.getId(), ticks, 0.0D), target.getBbHeight());
				locked++;
			}
		}
		ctx.markEnd(centre);
		ctx.schedule(ticks, () -> {
			for (LivingEntity target : hits) {
				if (target.isAlive() && !target.isRemoved()) {
					fx(level, Technique.SPACE_PRISON, DaoTechniqueSub.END, middleOf(target), new Vec3(target.getId(), 0.0D, 0.0D), target.getBbHeight());
					sound(level, target.position(), SoundEvents.ENDERMAN_TELEPORT, 0.4F, 1.8F);
				}
			}
		});
		ctx.keepAlive(ticks + 1);
		ctx.runBonusSteps();
		Msg.actionBar(player, Component.translatable("kultivator.daotech.space_prison", String.valueOf(locked)).withColor(Dao.SPACE.color()));
		return true;
	}

	// ================================================================== lasting effects

	/** Every server tick for every player: the flying phoenixes and the end of the empowered strike. */
	public static void tick(ServerPlayer player, dev.kultivator.data.CultivationData data) {
		DaoTechniqueState state = STATES.get(player.getUUID());
		if (state == null || player.isRemoved() || player.isDeadOrDying()) {
			return;
		}
		long now = player.level().getGameTime();
		if (state.shadowUntil != 0L && now >= state.shadowUntil) {
			state.shadowUntil = 0L;
			fx(player.level(), Technique.SHADOW_STEP, DaoTechniqueSub.END, middleOf(player), new Vec3(player.getId(), 0.0D, 0.0D), 0.0F);
		}
		if (!state.phoenixes.isEmpty()) {
			tickPhoenixes(player, state);
		}
		if (now >= state.shadowWindowUntil) {
			state.shadowVisited.clear();
		}
		if (state.idle(now)) {
			STATES.remove(player.getUUID());
		}
	}

	// ================================================================== events

	private static void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (retaliating || blocked || damageTaken <= 0.0F) {
			return;
		}
		// Shadow Step: the next melee hit after the step lands ×2 (×3 from Kenaikan Abadi), through the choke point.
		if (source.is(DamageTypes.PLAYER_ATTACK) && source.getEntity() instanceof ServerPlayer attacker && source.getDirectEntity() == attacker) {
			DaoTechniqueState state = STATES.get(attacker.getUUID());
			if (state != null && state.shadowUntil > attacker.level().getGameTime() && entity.isAlive() && state.shadowCast != null
					&& state.shadowCast.mayHit(entity, true)) {
				state.shadowUntil = 0L;
				retaliating = true;
				try {
					double extra = damageTaken * Math.max(0.0D, state.shadowFactor - 1.0D);
					if (state.shadowCast instanceof Caster cast) {
						cast.hurt(cast.raw(entity, extra, dev.kultivator.technique.spec.DamageContext.Kind.HIT));
					}
				} finally {
					retaliating = false;
				}
				ServerLevel level = attacker.level();
				fx(level, Technique.SHADOW_STEP, DaoTechniqueSub.HIT, middleOf(entity), middleOf(entity), entity.getBbWidth());
				fx(level, Technique.SHADOW_STEP, DaoTechniqueSub.END, middleOf(attacker), new Vec3(attacker.getId(), 0.0D, 0.0D), 0.0F);
				sound(level, entity.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.6F);
				Msg.actionBar(attacker, Component.translatable("kultivator.daotech.shadow_strike").withColor(Dao.SHADOW.color()));
			}
		}
	}

	private static void forgetEverywhere(Entity entity) {
		if (entity instanceof LivingEntity living) {
			for (DaoTechniqueState state : STATES.values()) {
				state.forget(living);
			}
			BURNING.remove(living);
		}
	}

	// ================================================================== helpers

	private static DaoTechniqueState state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), id -> new DaoTechniqueState());
	}

	/** ECHO .4 at Puncak Jurus (22.10): the cast again 10 ticks later at 40%, no Kehancuran, no mastery. */
	static void echoPeak(Caster ctx) {
		if (!ctx.copy() && ctx.peak(Bonus.Kind.ECHO)) {
			ctx.copyAfter(10, 0.4D, true);
		}
	}

	/** Technique damage of {@code coef} × P through the cast's choke point. True when it landed. */
	static boolean strike(CastContext ctx, LivingEntity target, double coef, boolean precise) {
		return coef > 0.0D && target.isAlive() && ctx.hurt(ctx.damage(target, coef).precise(precise));
	}

	static Payload.Dmg dmg(double coef) {
		return new Payload.Dmg(coef, false, Map.of(), Double.NaN, 0.0D, Payload.Per.NONE, false, false);
	}

	private static boolean valid(CastContext ctx, @Nullable LivingEntity target) {
		return target != null && target.isAlive() && !target.isRemoved() && target.level() == ctx.level() && ctx.mayHit(target, false);
	}

	/**
	 * Sets an entity's velocity for every client. A moved monster gets {@link NoGrief#TAG} and a portal cooldown. Callers
	 * check {@code moveAllowed} (never-moved list and portals) first.
	 */
	private static void launch(Entity entity, Vec3 velocity) {
		if (!(entity instanceof Player)) {
			entity.addTag(NoGrief.TAG);
			entity.setPortalCooldown();
		}
		entity.setDeltaMovement(velocity);
		entity.needsSync = true;
		entity.syncVelocity = true;
	}

	/**
	 * Sets an entity (never a block) on fire for {@code seconds}, unless powder snow or a water cauldron is right next to
	 * it, and watches it until the fire is out (see init): a burning entity would melt powder snow or lower a cauldron.
	 * Used by every module that sets entities on fire. False when the fire was skipped.
	 */
	public static boolean ignite(LivingEntity target, float seconds) {
		if (nearExtinguisher(target)) {
			return false;
		}
		target.igniteForSeconds(seconds);
		BURNING.merge(target, target.level().getGameTime() + (long) (seconds * 20.0F) + 20L, Math::max);
		return true;
	}

	/** Read-only: powder snow or a water / powder snow cauldron within a block of where the entity is heading. */
	private static boolean nearExtinguisher(Entity entity) {
		return entity.level().getBlockStatesIfLoaded(entity.getBoundingBox().expandTowards(entity.getDeltaMovement()).inflate(1.0D))
				.anyMatch(state -> state.is(Blocks.POWDER_SNOW) || state.is(Blocks.WATER_CAULDRON) || state.is(Blocks.POWDER_SNOW_CAULDRON));
	}

	/** Frozen look (shaking, frost) for about {@code extraTicks / 2} ticks beyond full freeze. Entities only, no control. */
	private static void freezeLook(LivingEntity target, int extraTicks) {
		if (target.isAlive() && target.canFreeze()) {
			target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + extraTicks));
		}
	}

	private static Vec3 middleOf(Entity entity) {
		return entity.getBoundingBox().getCenter();
	}

	/** Roughly where the casting palm is. */
	private static Vec3 hand(ServerPlayer player) {
		return player.getEyePosition().add(player.getViewVector(1.0F).scale(0.6D)).add(0.0D, -0.35D, 0.0D);
	}

	/** Read-only: where a line from {@code from} along {@code dir} meets the first solid block (or an unloaded chunk), or its end. */
	private static Vec3 clipEnd(ServerPlayer player, Vec3 from, Vec3 dir, double range) {
		BlockHitResult hit = dev.kultivator.util.LoadedPath.ray(player.level(), from, from.add(dir.scale(range)), player);
		return hit.getLocation();
	}

	/** Read-only: nothing solid between two points (and loaded). */
	private static boolean clearBetween(ServerPlayer player, Vec3 a, Vec3 b) {
		return dev.kultivator.util.LoadedPath.loaded(player.level(), a, b)
				&& dev.kultivator.util.LoadedPath.ray(player.level(), a, b, player).getType() == HitResult.Type.MISS;
	}

	/** Where a particle bolt starts: up to 14 blocks above, below the first ceiling (read-only). */
	private static Vec3 skyAbove(ServerLevel level, LivingEntity caster, Vec3 ground) {
		RandomSource random = caster.getRandom();
		Vec3 from = ground.add(0.0D, 1.5D, 0.0D);
		Vec3 sky = ground.add((random.nextDouble() - 0.5D) * 4.0D, SKY_HEIGHT, (random.nextDouble() - 0.5D) * 4.0D);
		BlockHitResult hit = dev.kultivator.util.LoadedPath.ray(level, from, sky, caster);
		return hit.getType() == HitResult.Type.MISS ? sky : hit.getLocation().add(0.0D, -0.2D, 0.0D);
	}

	/** True when the entity's box, grown by {@code reach}, touches the segment. */
	private static boolean nearSegment(LivingEntity entity, Vec3 from, Vec3 to, double reach) {
		AABB box = entity.getBoundingBox().inflate(reach);
		return box.contains(from) || box.clip(from, to).isPresent();
	}

	/** The Dao technique whose ordinal carries the Dao Domain pulse of {@code dao} to the client. */
	static Technique flavour(Dao dao) {
		return switch (dao) {
			case SWORD -> Technique.SWORD_QI;
			case FIRE -> Technique.FLAME_PALM;
			case ICE -> Technique.FROST_LOTUS;
			case LIGHTNING -> Technique.THUNDER_PALM;
			case BODY -> Technique.IRON_BODY;
			case LIFE -> Technique.SPRING_OF_LIFE;
			case SHADOW -> Technique.SHADOW_STEP;
			case SPACE -> Technique.SPACE_BLINK;
		};
	}

	private static void fx(ServerLevel level, Technique technique, int sub, Vec3 pos, Vec3 target, float f) {
		dev.kultivator.fx.Fx.send(level, pos, target, dev.kultivator.fx.FxType.TECHNIQUE, technique.ordinal(), sub, f);
	}

	private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	private static void sound(ServerLevel level, Vec3 at, Holder<SoundEvent> sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	/** Why the technique did not happen, in the same action-bar style as the pipeline's refusals. */
	private static void refuse(ServerPlayer player, String key) {
		Techniques.refuse(player, Component.translatable("kultivator.daotech." + key));
	}

	private DaoTechniques() {
	}
}
