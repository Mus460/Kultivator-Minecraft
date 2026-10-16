package dev.kultivator.technique;

import dev.kultivator.Kultivator;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Technique;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
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
 * The 16 Dao techniques (DESIGN 8) and the Dao flavour of the Dao Domain. Qi, cooldowns and comprehension are handled by
 * {@link Techniques}; {@link #cast} only performs the effect. Targets always come from {@link Targeting} and damage
 * always goes through {@link Techniques#hurt}. Nothing here ever changes a block: block raycasts are read-only clips,
 * fire is only put on entities (never next to powder snow or a water cauldron, and put out when a burning monster gets
 * close to one), lightning is particles only, teleports only land on spots checked by {@link Targeting#safeSpot} and
 * reached through open space. Creepers and ravagers are never pulled or thrown (a creeper dragged close would explode,
 * a ravager thrown into trees breaks leaves), prisons stop a creeper's fuse, nothing is thrown or pulled near a portal,
 * and every thrown monster gets {@link NoGrief#TAG} so it cannot trample farmland or crush turtle eggs. Every particle is drawn on the client ({@code DaoTechniqueFx}) from TECHNIQUE packets
 * whose sub effects are listed in {@link DaoTechniqueSub}.
 */
public final class DaoTechniques {

	/** Transient knockback resistance of Iron Body (never saved, removed when it ends). */
	public static final Identifier IRON_BODY_ID = Kultivator.id("iron_body");

	private static final double SWORD_QI_RANGE = 16.0D;
	private static final double SWORD_QI_HALF_WIDTH = 1.0D;
	private static final int THOUSAND_SWORDS_TICKS = 80;
	private static final int THOUSAND_SWORDS_INTERVAL = 5;
	private static final int THOUSAND_SWORDS_MAX = 6;
	private static final double THOUSAND_SWORDS_RANGE = 16.0D;
	/** Ticks a sword of light takes to fall (the client animates the same time). */
	private static final int SWORD_FALL_TICKS = 4;
	private static final double FLAME_RANGE = 7.0D;
	/** Cosine of the half angle of the 60 degree flame cone. */
	private static final double FLAME_COS = Math.cos(Math.toRadians(30.0D));
	private static final double PHOENIX_RANGE = 24.0D;
	private static final double PHOENIX_REACH = 1.8D;
	private static final double FROST_RADIUS = 6.0D;
	private static final double GLACIER_RANGE = 24.0D;
	private static final double GLACIER_RADIUS = 5.0D;
	private static final int GLACIER_TICKS = 160;
	private static final double THUNDER_RANGE = 24.0D;
	private static final double CHAIN_RADIUS = 6.0D;
	private static final int CHAIN_JUMPS = 4;
	private static final int CHAIN_DELAY = 3;
	private static final double HEAVENLY_RANGE = 16.0D;
	private static final int HEAVENLY_BOLTS = 8;
	private static final int HEAVENLY_INTERVAL = 5;
	private static final double SKY_HEIGHT = 14.0D;
	private static final int IRON_BODY_TICKS = 300;
	private static final float REFLECT_SHARE = 0.3F;
	private static final double STOMP_RADIUS = 7.0D;
	private static final double SPRING_RADIUS = 8.0D;
	private static final double DRAIN_RADIUS = 8.0D;
	private static final double SHADOW_RANGE = 20.0D;
	private static final int SHADOW_TICKS = 100;
	private static final double GALE_RADIUS = 10.0D;
	private static final int GALE_TICKS = 120;
	private static final double BLINK_RANGE = 24.0D;
	private static final double SPACE_PRISON_RADIUS = 12.0D;
	private static final int SPACE_PRISON_TICKS = 120;
	/** Blocks per tick a Space Prison pulls its prisoners (and per pulse for the Space domain). */
	private static final double PULL_SPEED = 0.3D;
	private static final double DOMAIN_RADIUS = 10.0D;
	/** Effects of a domain pulse last 2 s (pulses come every second, so they never run out inside the domain). */
	private static final int DOMAIN_EFFECT_TICKS = 40;
	/** Slowness amplifier that brings a mob's own movement to zero (prisons). */
	private static final int HOLD_AMPLIFIER = 9;

	private static final Map<UUID, DaoTechniqueState> STATES = new HashMap<>();
	/** Monsters set on fire by a technique -> game time their fire ends (watched so it never melts powder snow). */
	private static final Map<LivingEntity, Long> BURNING = new HashMap<>();
	/** True while we deal retaliation / empowered damage, so it never triggers itself again. */
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
		ServerPlayerEvents.LEAVE.register(DaoTechniques::onLeave);
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

	/** Performs {@code technique} (qi and cooldown are handled by Techniques). True when it really happened. */
	public static boolean cast(ServerPlayer player, CultivationData data, Technique technique) {
		if (technique.dao() == null || player.isRemoved() || player.isDeadOrDying()) {
			return false;
		}
		double power = Techniques.power(data);
		boolean done = switch (technique) {
			case SWORD_QI -> swordQi(player, power);
			case THOUSAND_SWORDS -> thousandSwords(player, power);
			case FLAME_PALM -> flamePalm(player, power);
			case PHOENIX_FLAME -> phoenixFlame(player, power);
			case FROST_LOTUS -> frostLotus(player, power);
			case GLACIER_PRISON -> glacierPrison(player, power);
			case THUNDER_PALM -> thunderPalm(player, power);
			case HEAVENLY_THUNDER -> heavenlyThunder(player, power);
			case IRON_BODY -> ironBody(player);
			case MOUNTAIN_STOMP -> mountainStomp(player, power);
			case SPRING_OF_LIFE -> springOfLife(player);
			case LIFE_DRAIN -> lifeDrain(player, power);
			case SHADOW_STEP -> shadowStep(player);
			case GALE -> gale(player);
			case SPACE_BLINK -> spaceBlink(player);
			case SPACE_PRISON -> spacePrison(player, power);
			default -> false;
		};
		if (done) {
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

	/** Once per second while the Dao Domain is up: the Dao's effect on the monsters inside (and on the caster). */
	public static void domainPulse(ServerPlayer player, CultivationData data, Dao dao, List<LivingEntity> insideHostiles) {
		if (dao == null || player.isRemoved() || player.isDeadOrDying()) {
			return;
		}
		double power = Techniques.power(data);
		fx(player, flavour(dao), DaoTechniqueSub.DOMAIN, player.position(), player.position(), (float) DOMAIN_RADIUS);
		List<LivingEntity> targets = new ArrayList<>();
		for (LivingEntity entity : insideHostiles) {
			if (entity.isAlive() && !entity.isRemoved() && entity.level() == player.level() && Targeting.isHostileTo(entity, player)) {
				targets.add(entity);
			}
		}
		switch (dao) {
			case SWORD -> {
				for (LivingEntity target : targets) {
					strike(player, target, power * 0.5D);
					fx(player, Technique.SWORD_QI, DaoTechniqueSub.HIT, middleOf(target), middleOf(target), target.getBbWidth());
				}
				if (!targets.isEmpty()) {
					sound(player, player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.4F, 1.4F);
				}
			}
			case FIRE -> {
				for (LivingEntity target : targets) {
					ignite(target, 3.0F);
					strike(player, target, power * 0.3D);
				}
			}
			case ICE -> {
				for (LivingEntity target : targets) {
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, DOMAIN_EFFECT_TICKS, 2), player);
					freeze(target, DOMAIN_EFFECT_TICKS);
				}
			}
			case LIGHTNING -> {
				if (!targets.isEmpty()) {
					LivingEntity target = targets.get(player.getRandom().nextInt(targets.size()));
					Vec3 ground = target.position();
					fx(player, Technique.THUNDER_PALM, DaoTechniqueSub.BOLT, skyAbove(player, ground), ground, 1.0F);
					strike(player, target, power * 0.8D);
					sound(player, ground, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6F, 1.3F);
				}
			}
			case BODY -> player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, DOMAIN_EFFECT_TICKS, 1, false, false, true), player);
			case LIFE -> {
				List<LivingEntity> allies = new ArrayList<>(Targeting.alliesAround(player, DOMAIN_RADIUS));
				if (!allies.contains(player)) {
					allies.add(player);
				}
				for (LivingEntity ally : allies) {
					ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, DOMAIN_EFFECT_TICKS, 1, false, false, true), player);
				}
				for (LivingEntity target : targets) {
					target.addEffect(new MobEffectInstance(MobEffects.WITHER, DOMAIN_EFFECT_TICKS, 0), player);
				}
			}
			case SHADOW -> {
				for (LivingEntity target : targets) {
					target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, DOMAIN_EFFECT_TICKS, 0), player);
				}
				player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, DOMAIN_EFFECT_TICKS, 0, false, false, true), player);
			}
			case SPACE -> {
				boolean pulling = !portalNear(player, DOMAIN_RADIUS + 4.0D);
				for (LivingEntity target : targets) {
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, DOMAIN_EFFECT_TICKS, 4), player);
					if (!pulling || target instanceof Creeper || target instanceof Ravager) {
						continue;
					}
					Vec3 to = player.position().subtract(target.position()).horizontal();
					if (to.lengthSqr() > 1.0D) {
						Vec3 pull = to.normalize().scale(PULL_SPEED);
						launch(target, target.getDeltaMovement().add(pull.x, 0.0D, pull.z));
					}
				}
			}
		}
	}

	/** Every server tick for every player: lasting effects of Dao techniques (iron body, prisons...). */
	public static void tick(ServerPlayer player, CultivationData data) {
		DaoTechniqueState state = STATES.get(player.getUUID());
		if (state == null || player.isRemoved() || player.isDeadOrDying()) {
			return;
		}
		long now = now(player);
		if (state.ironBodyUntil != 0L && now >= state.ironBodyUntil) {
			endIronBody(player, state);
		}
		if (state.shadowUntil != 0L && now >= state.shadowUntil) {
			state.shadowUntil = 0L;
			fx(player, Technique.SHADOW_STEP, DaoTechniqueSub.END, middleOf(player), new Vec3(player.getId(), 0.0D, 0.0D), 0.0F);
		}
		tickSwords(player, state);
		tickScheduled(player, state);
		tickPhoenixes(player, state);
		tickLocks(player, state);
		if (state.idle()) {
			STATES.remove(player.getUUID());
		}
	}

	// ================================================================== Sword

	/** A crescent of sword qi flies 16 blocks forward (stopped by solid blocks), cutting every monster in its way once. */
	private static boolean swordQi(ServerPlayer player, double power) {
		Vec3 look = player.getViewVector(1.0F);
		Vec3 start = player.getEyePosition().add(0.0D, -0.3D, 0.0D).add(look.scale(0.6D));
		Vec3 end = clipEnd(player, start, look, SWORD_QI_RANGE);
		List<LivingEntity> hits = new ArrayList<>();
		for (LivingEntity target : Targeting.hostilesAround(player, start.add(end).scale(0.5D), start.distanceTo(end) * 0.5D + 3.0D)) {
			if (nearSegment(target, start, end, SWORD_QI_HALF_WIDTH)) {
				hits.add(target);
			}
		}
		if (hits.isEmpty()) {
			refuse(player, "no_target_path");
			return false;
		}
		fx(player, Technique.SWORD_QI, DaoTechniqueSub.CAST, start, end, (float) (SWORD_QI_HALF_WIDTH * 2.0D));
		sound(player, player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.7F);
		sound(player, player.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 1.7F);
		for (LivingEntity target : hits) {
			strike(player, target, power * 1.5D);
			fx(player, Technique.SWORD_QI, DaoTechniqueSub.HIT, middleOf(target), middleOf(target), target.getBbWidth());
		}
		return true;
	}

	/** For 4 s, every 5 ticks swords of light fall on up to 6 random monsters within 16 blocks. */
	private static boolean thousandSwords(ServerPlayer player, double power) {
		if (Targeting.hostilesAround(player, player.position(), THOUSAND_SWORDS_RANGE).isEmpty()) {
			refuse(player, "no_target");
			return false;
		}
		DaoTechniqueState state = state(player);
		state.swordsLeft = THOUSAND_SWORDS_TICKS;
		state.swordsDamage = power * 0.6D;
		fx(player, Technique.THOUSAND_SWORDS, DaoTechniqueSub.CAST, player.position(), player.position(), (float) THOUSAND_SWORDS_RANGE);
		sound(player, player.position(), SoundEvents.BEACON_ACTIVATE, 1.0F, 1.6F);
		sound(player, player.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 0.8F);
		Msg.actionBar(player, Component.translatable("kultivator.daotech.swords").withColor(Dao.SWORD.color()));
		return true;
	}

	private static void tickSwords(ServerPlayer player, DaoTechniqueState state) {
		if (state.swordsLeft <= 0) {
			return;
		}
		int elapsed = THOUSAND_SWORDS_TICKS - state.swordsLeft;
		state.swordsLeft--;
		if (elapsed % THOUSAND_SWORDS_INTERVAL != 0) {
			return;
		}
		List<LivingEntity> around = new ArrayList<>(Targeting.hostilesAround(player, player.position(), THOUSAND_SWORDS_RANGE));
		RandomSource random = player.getRandom();
		for (int i = around.size() - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			LivingEntity swap = around.get(i);
			around.set(i, around.get(j));
			around.set(j, swap);
		}
		int count = Math.min(THOUSAND_SWORDS_MAX, around.size());
		for (int i = 0; i < count; i++) {
			// Staggered: each sword starts falling 1..5 ticks after the wave.
			state.scheduled.add(new DaoTechniqueState.Scheduled(DaoTechniqueState.Kind.SWORD, 1 + random.nextInt(THOUSAND_SWORDS_INTERVAL),
					around.get(i), state.swordsDamage, null));
		}
		if (count > 0 && elapsed % 20 == 0) {
			sound(player, player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F, 1.5F);
		}
	}

	// ================================================================== Fire

	/** A 60 degree cone of spirit fire 7 blocks long: D and 6 s of burning (on entities only). */
	private static boolean flamePalm(ServerPlayer player, double power) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		List<LivingEntity> hits = new ArrayList<>();
		for (LivingEntity target : Targeting.hostilesAround(player, eye, FLAME_RANGE + 1.0D)) {
			Vec3 to = middleOf(target).subtract(eye);
			double distance = to.length();
			if (distance > FLAME_RANGE + target.getBbWidth() * 0.5D) {
				continue;
			}
			boolean inCone = distance < 1.5D ? to.dot(look) > 0.0D : to.scale(1.0D / distance).dot(look) >= FLAME_COS;
			if (inCone && Targeting.hasLineOfSight(player, target)) {
				hits.add(target);
			}
		}
		if (hits.isEmpty()) {
			refuse(player, "no_target_path");
			return false;
		}
		fx(player, Technique.FLAME_PALM, DaoTechniqueSub.CAST, hand(player), eye.add(look.scale(FLAME_RANGE)), (float) FLAME_RANGE);
		sound(player, player.position(), SoundEvents.FIRECHARGE_USE, 1.0F, 0.8F);
		sound(player, player.position(), SoundEvents.BLAZE_SHOOT, 0.8F, 1.2F);
		for (LivingEntity target : hits) {
			strike(player, target, power);
			if (target.isAlive()) {
				ignite(target, 6.0F);
			}
			fx(player, Technique.FLAME_PALM, DaoTechniqueSub.HIT, middleOf(target), middleOf(target), target.getBbWidth());
		}
		return true;
	}

	/** A virtual phoenix flies one block per tick for 24 blocks (or to the first solid block) and burns what it passes. */
	private static boolean phoenixFlame(ServerPlayer player, double power) {
		Vec3 look = player.getViewVector(1.0F);
		Vec3 start = player.getEyePosition().add(0.0D, -0.2D, 0.0D).add(look.scale(0.8D));
		Vec3 end = clipEnd(player, start, look, PHOENIX_RANGE);
		double length = start.distanceTo(end);
		int ticks = Math.max(1, (int) Math.ceil(length));
		boolean any = false;
		for (LivingEntity target : Targeting.hostilesAround(player, start.add(end).scale(0.5D), length * 0.5D + 3.0D)) {
			if (nearSegment(target, start, end, PHOENIX_REACH + 0.7D)) {
				any = true;
				break;
			}
		}
		if (!any) {
			refuse(player, "no_target_path");
			return false;
		}
		Vec3 step = length < 1.0E-4D ? look : end.subtract(start).scale(1.0D / ticks);
		state(player).phoenixes.add(new DaoTechniqueState.Phoenix(start, step, ticks, power * 2.0D));
		fx(player, Technique.PHOENIX_FLAME, DaoTechniqueSub.CAST, start, end, ticks);
		sound(player, player.position(), SoundEvents.BLAZE_SHOOT, 1.0F, 0.6F);
		sound(player, player.position(), SoundEvents.ENDER_DRAGON_FLAP, 1.0F, 1.4F);
		sound(player, player.position(), SoundEvents.FIRECHARGE_USE, 0.8F, 0.7F);
		return true;
	}

	private static void tickPhoenixes(ServerPlayer player, DaoTechniqueState state) {
		Iterator<DaoTechniqueState.Phoenix> iterator = state.phoenixes.iterator();
		while (iterator.hasNext()) {
			DaoTechniqueState.Phoenix phoenix = iterator.next();
			Vec3 next = phoenix.pos.add(phoenix.step);
			BlockHitResult wall = player.level().clip(new ClipContext(phoenix.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			boolean blocked = wall.getType() != HitResult.Type.MISS;
			if (blocked) {
				next = wall.getLocation();
			}
			for (LivingEntity target : Targeting.hostilesAround(player, next, PHOENIX_REACH + 2.5D)) {
				if (target.getBoundingBox().distanceToSqr(next) <= PHOENIX_REACH * PHOENIX_REACH && phoenix.hit.add(target.getId())) {
					strike(player, target, phoenix.damage);
					if (target.isAlive()) {
						ignite(target, 8.0F);
					}
					fx(player, Technique.PHOENIX_FLAME, DaoTechniqueSub.HIT, middleOf(target), middleOf(target), target.getBbWidth());
				}
			}
			phoenix.pos = next;
			phoenix.ticksLeft--;
			if (phoenix.ticksLeft % 6 == 0) {
				sound(player, next, SoundEvents.PHANTOM_FLAP, 0.8F, 0.7F);
			}
			if (blocked || phoenix.ticksLeft <= 0) {
				iterator.remove();
				sound(player, next, SoundEvents.BLAZE_SHOOT, 0.7F, 0.5F);
				sound(player, next, SoundEvents.FIRE_EXTINGUISH, 0.5F, 1.2F);
			}
		}
	}

	// ================================================================== Ice

	/** A lotus of frost blooms around the caster: D, Slowness IV and frozen for 6 s. No ice blocks, ever. */
	private static boolean frostLotus(ServerPlayer player, double power) {
		List<LivingEntity> hits = Targeting.hostilesAround(player, player.position(), FROST_RADIUS);
		if (hits.isEmpty()) {
			refuse(player, "no_target");
			return false;
		}
		fx(player, Technique.FROST_LOTUS, DaoTechniqueSub.CAST, player.position(), player.position(), (float) FROST_RADIUS);
		sound(player, player.position(), SoundEvents.POWDER_SNOW_BREAK, 1.2F, 0.6F);
		sound(player, player.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 1.5F);
		sound(player, player.position(), SoundEvents.PLAYER_HURT_FREEZE, 0.7F, 0.8F);
		for (LivingEntity target : hits) {
			strike(player, target, power);
			if (target.isAlive()) {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 120, 3), player);
				// Frozen ticks thaw by 2 per tick: fully frozen (shaking) for 6 s.
				freeze(target, 240);
			}
			fx(player, Technique.FROST_LOTUS, DaoTechniqueSub.HIT, middleOf(target), middleOf(target), target.getBbWidth());
		}
		return true;
	}

	/** The looked-at monster and every monster within 5 of it are sealed in ice: frozen in place for 8 s, 1.5 D. */
	private static boolean glacierPrison(ServerPlayer player, double power) {
		LivingEntity main = Targeting.lookedAtHostile(player, GLACIER_RANGE);
		if (main == null) {
			refuse(player, "no_target_look");
			return false;
		}
		List<LivingEntity> victims = new ArrayList<>();
		victims.add(main);
		for (LivingEntity other : Targeting.hostilesAround(player, main.position(), GLACIER_RADIUS)) {
			if (other != main) {
				victims.add(other);
			}
		}
		fx(player, Technique.GLACIER_PRISON, DaoTechniqueSub.CAST, middleOf(main), hand(player), (float) GLACIER_RADIUS);
		sound(player, main.position(), SoundEvents.POWDER_SNOW_BREAK, 1.2F, 0.5F);
		sound(player, main.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 0.6F);
		sound(player, main.position(), SoundEvents.GLASS_PLACE, 1.0F, 0.7F);
		int sealed = 0;
		for (LivingEntity victim : victims) {
			strike(player, victim, power * 1.5D);
			if (victim.isAlive()) {
				victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, GLACIER_TICKS, HOLD_AMPLIFIER), player);
				freeze(victim, 20);
				lock(player, victim, Technique.GLACIER_PRISON, victim.position(), GLACIER_TICKS);
				sealed++;
			}
		}
		Msg.actionBar(player, Component.translatable("kultivator.daotech.glacier", String.valueOf(sealed)).withColor(Dao.ICE.color()));
		return true;
	}

	// ================================================================== Lightning

	/** A particle bolt from the palm to the looked-at monster (D), then up to 4 jumps to monsters within 6 (D each). */
	private static boolean thunderPalm(ServerPlayer player, double power) {
		LivingEntity main = Targeting.lookedAtHostile(player, THUNDER_RANGE);
		if (main == null) {
			refuse(player, "no_target_look");
			return false;
		}
		Vec3 hand = hand(player);
		fx(player, Technique.THUNDER_PALM, DaoTechniqueSub.CAST, hand, middleOf(main), 1.0F);
		fx(player, Technique.THUNDER_PALM, DaoTechniqueSub.BOLT, hand, middleOf(main), 1.0F);
		sound(player, main.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0F, 1.2F);
		sound(player, player.position(), SoundEvents.FIRECHARGE_USE, 0.5F, 1.8F);
		DaoTechniqueState state = state(player);
		Set<Integer> chained = new HashSet<>();
		chained.add(main.getId());
		// The chain is chosen now (from where the monsters stand), the jumps follow a few ticks apart.
		LivingEntity previous = main;
		for (int jump = 1; jump <= CHAIN_JUMPS; jump++) {
			LivingEntity next = null;
			for (LivingEntity candidate : Targeting.hostilesAround(player, previous.position(), CHAIN_RADIUS)) {
				if (!chained.contains(candidate.getId())) {
					next = candidate;
					break;
				}
			}
			if (next == null) {
				break;
			}
			chained.add(next.getId());
			DaoTechniqueState.Scheduled hop = new DaoTechniqueState.Scheduled(DaoTechniqueState.Kind.CHAIN, CHAIN_DELAY * jump, next, power, null);
			hop.from = previous;
			hop.fromPos = middleOf(previous);
			state.scheduled.add(hop);
			previous = next;
		}
		strike(player, main, power);
		return true;
	}

	/** 8 particle bolts from the sky over 2 s on monsters within 16 (2 D each; the same one may be hit again if few). */
	private static boolean heavenlyThunder(ServerPlayer player, double power) {
		if (Targeting.hostilesAround(player, player.position(), HEAVENLY_RANGE).isEmpty()) {
			refuse(player, "no_target");
			return false;
		}
		DaoTechniqueState state = state(player);
		Set<Integer> struck = new HashSet<>();
		for (int i = 0; i < HEAVENLY_BOLTS; i++) {
			state.scheduled.add(new DaoTechniqueState.Scheduled(DaoTechniqueState.Kind.SKY_BOLT, 1 + i * HEAVENLY_INTERVAL, null,
					power * 2.0D, struck));
		}
		fx(player, Technique.HEAVENLY_THUNDER, DaoTechniqueSub.CAST, player.position(), player.position(), (float) HEAVENLY_RANGE);
		sound(player, player.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.7F, 1.1F);
		return true;
	}

	// ================================================================== Body

	/** 15 s of Resistance II, knockback immunity and 30% of every melee blow thrown back at the monster. */
	private static boolean ironBody(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, IRON_BODY_TICKS, 1), player);
		AttributeInstance knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (knockback != null) {
			knockback.addOrUpdateTransientModifier(new AttributeModifier(IRON_BODY_ID, 1.0D, AttributeModifier.Operation.ADD_VALUE));
		}
		state(player).ironBodyUntil = now(player) + IRON_BODY_TICKS;
		fx(player, Technique.IRON_BODY, DaoTechniqueSub.CAST, player.position(), player.position(), player.getBbHeight());
		fx(player, Technique.IRON_BODY, DaoTechniqueSub.MARK, player.position(), new Vec3(player.getId(), IRON_BODY_TICKS, 0.0D),
				player.getBbHeight());
		sound(player, player.position(), SoundEvents.ANVIL_LAND, 0.5F, 1.4F);
		sound(player, player.position(), SoundEvents.ARMOR_EQUIP_IRON, 1.0F, 0.8F);
		sound(player, player.position(), SoundEvents.IRON_GOLEM_REPAIR, 1.0F, 0.9F);
		Msg.actionBar(player, Component.translatable("kultivator.daotech.iron_body_on").withColor(Dao.BODY.color()));
		return true;
	}

	private static void endIronBody(ServerPlayer player, DaoTechniqueState state) {
		state.ironBodyUntil = 0L;
		removeIronBody(player);
		fx(player, Technique.IRON_BODY, DaoTechniqueSub.END, middleOf(player), new Vec3(player.getId(), 0.0D, 0.0D), player.getBbHeight());
		sound(player, player.position(), SoundEvents.ARMOR_EQUIP_IRON, 0.6F, 0.6F);
		Msg.actionBar(player, Component.translatable("kultivator.daotech.iron_body_off").withStyle(ChatFormatting.GRAY));
	}

	private static void removeIronBody(ServerPlayer player) {
		AttributeInstance knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (knockback != null) {
			knockback.removeModifier(IRON_BODY_ID);
		}
	}

	/** A shockwave of radius 7: 2 D, monsters thrown into the air (not ravagers, nothing near a portal) and slowed. */
	private static boolean mountainStomp(ServerPlayer player, double power) {
		List<LivingEntity> hits = Targeting.hostilesAround(player, player.position(), STOMP_RADIUS);
		if (hits.isEmpty()) {
			refuse(player, "no_target");
			return false;
		}
		boolean throwing = !portalNear(player, STOMP_RADIUS + 6.0D);
		fx(player, Technique.MOUNTAIN_STOMP, DaoTechniqueSub.CAST, player.position(), player.position(), (float) STOMP_RADIUS);
		sound(player, player.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.2F, 0.7F);
		sound(player, player.position(), SoundEvents.ANVIL_LAND, 0.6F, 0.5F);
		for (LivingEntity target : hits) {
			strike(player, target, power * 2.0D);
			if (target.isAlive()) {
				if (throwing && !(target instanceof Ravager)) {
					Vec3 away = target.position().subtract(player.position()).horizontal();
					Vec3 push = away.lengthSqr() < 1.0E-4D ? Vec3.ZERO : away.normalize().scale(0.35D);
					launch(target, new Vec3(push.x, 0.9D, push.z));
				}
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 1), player);
			}
			fx(player, Technique.MOUNTAIN_STOMP, DaoTechniqueSub.HIT, middleOf(target), middleOf(target), target.getBbWidth());
		}
		return true;
	}

	// ================================================================== Life

	/** Heals the caster and the allies / pets within 8 by 40% of their max health, cures Poison and Wither. */
	private static boolean springOfLife(ServerPlayer player) {
		List<LivingEntity> allies = new ArrayList<>(Targeting.alliesAround(player, SPRING_RADIUS));
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
		fx(player, Technique.SPRING_OF_LIFE, DaoTechniqueSub.CAST, player.position(), player.position(), (float) SPRING_RADIUS);
		sound(player, player.position(), SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.6F);
		sound(player, player.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.2F);
		for (LivingEntity ally : allies) {
			ally.heal(ally.getMaxHealth() * 0.4F);
			ally.removeEffect(MobEffects.POISON);
			ally.removeEffect(MobEffects.WITHER);
			ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0), player);
			fx(player, Technique.SPRING_OF_LIFE, DaoTechniqueSub.HEAL, ally.position(), ally.position(), ally.getBbHeight());
		}
		return true;
	}

	/** Every monster within 8 takes D; the caster heals half of what was really dealt. */
	private static boolean lifeDrain(ServerPlayer player, double power) {
		List<LivingEntity> hits = Targeting.hostilesAround(player, player.position(), DRAIN_RADIUS);
		if (hits.isEmpty()) {
			refuse(player, "no_target");
			return false;
		}
		Vec3 heart = middleOf(player);
		double dealt = 0.0D;
		for (LivingEntity target : hits) {
			float before = target.getHealth();
			strike(player, target, power);
			dealt += Math.max(0.0F, before - Math.max(0.0F, target.getHealth()));
			fx(player, Technique.LIFE_DRAIN, DaoTechniqueSub.DRAIN, middleOf(target), heart, target.getBbHeight());
		}
		float heal = (float) (dealt * 0.5D);
		if (heal > 0.0F) {
			player.heal(heal);
		}
		fx(player, Technique.LIFE_DRAIN, DaoTechniqueSub.CAST, player.position(), player.position(), (float) DRAIN_RADIUS);
		fx(player, Technique.LIFE_DRAIN, DaoTechniqueSub.HEAL, player.position(), player.position(), player.getBbHeight());
		sound(player, player.position(), SoundEvents.EVOKER_CAST_SPELL, 1.0F, 0.7F);
		sound(player, player.position(), SoundEvents.WARDEN_HEARTBEAT, 1.0F, 1.3F);
		Msg.actionBar(player, Component.translatable("kultivator.daotech.drained", String.format(Locale.ROOT, "%.1f", heal))
				.withColor(Dao.LIFE.color()));
		return true;
	}

	// ================================================================== Shadow & Wind

	/** Steps through the shadows to a safe spot right behind the looked-at monster; the next melee hit within 5 s is doubled. */
	private static boolean shadowStep(ServerPlayer player) {
		LivingEntity target = Targeting.lookedAtHostile(player, SHADOW_RANGE);
		if (target == null) {
			refuse(player, "no_target_look");
			return false;
		}
		if (target instanceof Creeper) {
			// Right behind a creeper is inside its fuse range.
			refuse(player, "creeper");
			return false;
		}
		ServerLevel level = player.level();
		Vec3 from = player.position();
		// "Behind" = on the far side of the monster, seen from where the caster comes from.
		Vec3 approach = target.position().subtract(from).horizontal();
		if (approach.lengthSqr() < 1.0E-4D) {
			approach = player.getViewVector(1.0F).horizontal();
		}
		approach = approach.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : approach.normalize();
		double base = target.getBbWidth() * 0.5D + player.getBbWidth() * 0.5D;
		Vec3 spot = null;
		for (double gap : new double[]{0.9D, 0.5D, 1.5D}) {
			Vec3 candidate = Targeting.safeSpot(level, player, target.position().add(approach.scale(base + gap)));
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
		fx(player, Technique.SHADOW_STEP, DaoTechniqueSub.CAST, from, spot, player.getBbHeight());
		sound(player, from, SoundEvents.ENDERMAN_TELEPORT, 0.7F, 1.4F);
		sound(player, from, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.8F, 1.0F);
		player.teleportTo(level, spot.x, spot.y, spot.z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
		player.resetFallDistance();
		sound(player, spot, SoundEvents.ENDERMAN_TELEPORT, 0.7F, 1.6F);
		sound(player, spot, SoundEvents.SQUID_SQUIRT, 0.6F, 0.6F);
		state(player).shadowUntil = now(player) + SHADOW_TICKS;
		fx(player, Technique.SHADOW_STEP, DaoTechniqueSub.MARK, spot, new Vec3(player.getId(), SHADOW_TICKS, 0.0D), player.getBbHeight());
		Msg.actionBar(player, Component.translatable("kultivator.daotech.shadow_ready").withColor(Dao.SHADOW.color()));
		return true;
	}

	/**
	 * A storm wind hurls every monster within 10 away (harder when close; never ravagers, nothing near a portal); the
	 * caster turns invisible and swift for 6 s.
	 */
	private static boolean gale(ServerPlayer player) {
		List<LivingEntity> blown = portalNear(player, GALE_RADIUS + 16.0D) ? List.of()
				: Targeting.hostilesAround(player, player.position(), GALE_RADIUS);
		for (LivingEntity target : blown) {
			if (target instanceof Ravager) {
				continue;
			}
			Vec3 away = target.position().subtract(player.position()).horizontal();
			double distance = away.length();
			Vec3 dir = distance < 1.0E-3D ? player.getViewVector(1.0F).horizontal() : away.scale(1.0D / distance);
			dir = dir.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : dir.normalize();
			double closeness = 1.0D - Math.min(1.0D, distance / GALE_RADIUS);
			double strength = 0.9D + 1.7D * closeness;
			launch(target, new Vec3(dir.x * strength, 0.35D + 0.3D * closeness, dir.z * strength));
		}
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, GALE_TICKS, 0, false, false, true), player);
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, GALE_TICKS, 1, false, false, true), player);
		fx(player, Technique.GALE, DaoTechniqueSub.CAST, player.position(), player.position(), (float) GALE_RADIUS);
		sound(player, player.position(), SoundEvents.BREEZE_WIND_CHARGE_BURST, 1.0F, 0.8F);
		sound(player, player.position(), SoundEvents.PHANTOM_SWOOP, 0.7F, 1.5F);
		return true;
	}

	// ================================================================== Space

	/** Folds space to the looked-at spot (24 blocks), only onto a spot checked to be safe; refuses otherwise. */
	private static boolean spaceBlink(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockHitResult hit = Targeting.blockHit(player, BLINK_RANGE);
		Vec3 aim = Targeting.aimPoint(player, BLINK_RANGE);
		if (hit.getType() != HitResult.Type.MISS) {
			// Step out of the face we look at: on top of a floor, in front of a wall, under a ceiling.
			Direction face = hit.getDirection();
			double dy = face == Direction.DOWN ? -player.getBbHeight() - 0.1D : face.getStepY() * 0.1D;
			aim = aim.add(face.getStepX() * 0.6D, dy, face.getStepZ() * 0.6D);
		}
		Vec3 spot = Targeting.safeSpot(level, player, aim);
		if (spot == null) {
			refuse(player, "no_safe_spot");
			return false;
		}
		// Only through open space: never onto a roof through a ceiling, or into a room under a floor.
		Vec3 middle = spot.add(0.0D, player.getBbHeight() * 0.5D, 0.0D);
		Vec3 eye = player.getEyePosition();
		if (!clearBetween(player, aim, middle)
				|| !clearBetween(player, eye, spot.add(0.0D, player.getEyeHeight(), 0.0D)) && !clearBetween(player, eye, middle)) {
			refuse(player, "no_safe_spot");
			return false;
		}
		Vec3 from = player.position();
		fx(player, Technique.SPACE_BLINK, DaoTechniqueSub.CAST, from, spot, player.getBbHeight());
		sound(player, from, SoundEvents.ENDERMAN_TELEPORT, 0.8F, 0.7F);
		player.teleportTo(level, spot.x, spot.y, spot.z, Relative.ROTATION, 0.0F, 0.0F, false);
		player.resetFallDistance();
		sound(player, spot, SoundEvents.ENDERMAN_TELEPORT, 0.8F, 1.2F);
		sound(player, spot, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 1.8F);
		return true;
	}

	/**
	 * Every monster within 12 is locked in place for 6 s and pulled together (0.3 block per tick; creepers and ravagers are
	 * only held, and nothing is pulled near a portal); 1.5 D at the start.
	 */
	private static boolean spacePrison(ServerPlayer player, double power) {
		List<LivingEntity> hits = Targeting.hostilesAround(player, player.position(), SPACE_PRISON_RADIUS);
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
		fx(player, Technique.SPACE_PRISON, DaoTechniqueSub.CAST, centre, player.position(), (float) spread);
		sound(player, centre, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.0F, 0.6F);
		sound(player, centre, SoundEvents.ILLUSIONER_CAST_SPELL, 1.0F, 0.8F);
		int locked = 0;
		boolean pull = !portalNear(player, SPACE_PRISON_RADIUS + 4.0D);
		for (LivingEntity target : hits) {
			strike(player, target, power * 1.5D);
			if (target.isAlive()) {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SPACE_PRISON_TICKS, HOLD_AMPLIFIER), player);
				// Near a portal every prisoner is anchored where it stands (held, never pulled).
				lock(player, target, Technique.SPACE_PRISON, pull ? centre : target.position(), SPACE_PRISON_TICKS);
				locked++;
			}
		}
		Msg.actionBar(player, Component.translatable("kultivator.daotech.space_prison", String.valueOf(locked)).withColor(Dao.SPACE.color()));
		return true;
	}

	// ================================================================== lasting effects

	/** Holds {@code entity} for {@code ticks} (replacing any other prison on it) and tells clients to draw it. */
	private static void lock(ServerPlayer player, LivingEntity entity, Technique technique, Vec3 anchor, int ticks) {
		for (DaoTechniqueState other : STATES.values()) {
			if (other.holds(entity)) {
				other.locks.removeIf(lock -> lock.entity == entity);
			}
		}
		state(player).locks.add(new DaoTechniqueState.Lock(entity, technique, anchor, ticks));
		Vec3 velocity = entity.getDeltaMovement();
		launch(entity, new Vec3(0.0D, Math.min(0.0D, velocity.y), 0.0D));
		if (entity instanceof Creeper creeper) {
			// A prisoner's fuse never burns.
			creeper.setSwellDir(-1);
		}
		if (entity instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		fx(player, technique, DaoTechniqueSub.MARK, anchor, new Vec3(entity.getId(), ticks, 0.0D), entity.getBbHeight());
	}

	private static void tickLocks(ServerPlayer player, DaoTechniqueState state) {
		Iterator<DaoTechniqueState.Lock> iterator = state.locks.iterator();
		while (iterator.hasNext()) {
			DaoTechniqueState.Lock lock = iterator.next();
			LivingEntity entity = lock.entity;
			lock.ticksLeft--;
			lock.age++;
			boolean gone = !entity.isAlive() || entity.isRemoved() || entity.level() != player.level();
			if (gone || lock.ticksLeft <= 0) {
				iterator.remove();
				if (!gone) {
					release(player, lock);
				}
				continue;
			}
			Vec3 velocity = entity.getDeltaMovement();
			if (entity instanceof Creeper creeper) {
				// Runs after the entity ticks: the next Creeper.tick reads -1, so the fuse never grows while held.
				creeper.setSwellDir(-1);
			}
			boolean held = entity instanceof Creeper || entity instanceof Ravager;
			if (lock.technique == Technique.SPACE_PRISON && !held) {
				Vec3 to = lock.anchor.subtract(entity.position()).horizontal();
				double distance = to.length();
				Vec3 pull = distance > 1.0D ? to.scale(Math.min(PULL_SPEED, distance - 0.9D) / distance) : Vec3.ZERO;
				entity.setDeltaMovement(pull.x, Math.min(0.0D, velocity.y), pull.z);
			} else {
				entity.setDeltaMovement(0.0D, Math.min(0.0D, velocity.y), 0.0D);
				if (lock.technique == Technique.GLACIER_PRISON) {
					freeze(entity, 20);
				}
			}
			if (entity instanceof Mob mob) {
				mob.getNavigation().stop();
			}
			if (lock.age % 3 == 1) {
				// Mobs only send their motion now and then; make sure clients see them stop / slide.
				entity.needsSync = true;
				entity.syncVelocity = true;
			}
		}
	}

	private static void release(ServerPlayer player, DaoTechniqueState.Lock lock) {
		LivingEntity entity = lock.entity;
		fx(player, lock.technique, DaoTechniqueSub.END, middleOf(entity), new Vec3(entity.getId(), 0.0D, 0.0D), entity.getBbHeight());
		if (lock.technique == Technique.GLACIER_PRISON) {
			sound(player, entity.position(), SoundEvents.GLASS_BREAK, 0.8F, 1.0F);
		} else {
			sound(player, entity.position(), SoundEvents.ENDERMAN_TELEPORT, 0.4F, 1.8F);
		}
	}

	private static void tickScheduled(ServerPlayer player, DaoTechniqueState state) {
		if (state.scheduled.isEmpty()) {
			return;
		}
		List<DaoTechniqueState.Scheduled> due = new ArrayList<>();
		Iterator<DaoTechniqueState.Scheduled> iterator = state.scheduled.iterator();
		while (iterator.hasNext()) {
			DaoTechniqueState.Scheduled each = iterator.next();
			if (--each.delay <= 0) {
				iterator.remove();
				due.add(each);
			}
		}
		for (DaoTechniqueState.Scheduled each : due) {
			switch (each.kind) {
				case SWORD -> {
					LivingEntity target = each.target;
					if (valid(player, target)) {
						fx(player, Technique.THOUSAND_SWORDS, DaoTechniqueSub.SWORD_FALL, target.position(), target.position(), SWORD_FALL_TICKS);
						state.scheduled.add(new DaoTechniqueState.Scheduled(DaoTechniqueState.Kind.SWORD_HIT, SWORD_FALL_TICKS, target,
								each.amount, null));
					}
				}
				case SWORD_HIT -> {
					LivingEntity target = each.target;
					if (valid(player, target)) {
						strike(player, target, each.amount);
						sound(player, target.position(), SoundEvents.TRIDENT_HIT, 0.5F, 1.3F + player.getRandom().nextFloat() * 0.4F);
					}
				}
				case CHAIN -> {
					LivingEntity target = each.target;
					if (valid(player, target)) {
						Vec3 from = each.from != null && !each.from.isRemoved() ? middleOf(each.from) : each.fromPos;
						fx(player, Technique.THUNDER_PALM, DaoTechniqueSub.BOLT, from, middleOf(target), 0.8F);
						strike(player, target, each.amount);
						sound(player, target.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6F, 1.5F + player.getRandom().nextFloat() * 0.3F);
					}
				}
				case SKY_BOLT -> skyBolt(player, each);
			}
		}
	}

	/** One Heavenly Thunder bolt: prefers a monster not struck yet by this cast. */
	private static void skyBolt(ServerPlayer player, DaoTechniqueState.Scheduled bolt) {
		List<LivingEntity> around = Targeting.hostilesAround(player, player.position(), HEAVENLY_RANGE);
		if (around.isEmpty()) {
			return;
		}
		List<LivingEntity> fresh = new ArrayList<>();
		for (LivingEntity candidate : around) {
			if (bolt.hitSet == null || !bolt.hitSet.contains(candidate.getId())) {
				fresh.add(candidate);
			}
		}
		List<LivingEntity> pool = fresh.isEmpty() ? around : fresh;
		RandomSource random = player.getRandom();
		LivingEntity target = pool.get(random.nextInt(pool.size()));
		if (bolt.hitSet != null) {
			bolt.hitSet.add(target.getId());
		}
		Vec3 ground = target.position();
		fx(player, Technique.HEAVENLY_THUNDER, DaoTechniqueSub.BOLT, skyAbove(player, ground), ground, 2.0F);
		strike(player, target, bolt.amount);
		sound(player, ground, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0F, 0.9F + random.nextFloat() * 0.3F);
		if (random.nextInt(3) == 0) {
			sound(player, ground, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.4F, 1.3F);
		}
	}

	// ================================================================== events

	private static void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (retaliating || blocked || baseDamage <= 0.0F) {
			return;
		}
		// Iron Body: a melee blow taken is partly thrown back at the monster.
		if (entity instanceof ServerPlayer victim && isMelee(source) && source.getEntity() instanceof LivingEntity attacker) {
			DaoTechniqueState state = STATES.get(victim.getUUID());
			if (state != null && state.ironBodyUntil > now(victim) && attacker.isAlive() && Targeting.isHostileTo(attacker, victim)) {
				retaliating = true;
				try {
					strike(victim, attacker, baseDamage * REFLECT_SHARE);
				} finally {
					retaliating = false;
				}
				fx(victim, Technique.IRON_BODY, DaoTechniqueSub.HIT, middleOf(attacker), middleOf(attacker), attacker.getBbWidth());
				sound(victim, attacker.position(), SoundEvents.SHIELD_BLOCK, 0.7F, 0.7F);
			}
		}
		// Shadow Step: the next melee hit after the step lands twice.
		if (damageTaken > 0.0F && source.is(DamageTypes.PLAYER_ATTACK) && source.getEntity() instanceof ServerPlayer attacker
				&& source.getDirectEntity() == attacker) {
			DaoTechniqueState state = STATES.get(attacker.getUUID());
			if (state != null && state.shadowUntil > now(attacker) && entity.isAlive() && Targeting.isHostileTo(entity, attacker)) {
				state.shadowUntil = 0L;
				retaliating = true;
				try {
					strike(attacker, entity, damageTaken);
				} finally {
					retaliating = false;
				}
				fx(attacker, Technique.SHADOW_STEP, DaoTechniqueSub.HIT, middleOf(entity), middleOf(entity), entity.getBbWidth());
				fx(attacker, Technique.SHADOW_STEP, DaoTechniqueSub.END, middleOf(attacker), new Vec3(attacker.getId(), 0.0D, 0.0D), 0.0F);
				sound(attacker, entity.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.6F);
				Msg.actionBar(attacker, Component.translatable("kultivator.daotech.shadow_strike").withColor(Dao.SHADOW.color()));
			}
		}
	}

	private static boolean isMelee(DamageSource source) {
		return (source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO) || source.is(DamageTypes.PLAYER_ATTACK))
				&& source.getEntity() != null && source.getDirectEntity() == source.getEntity();
	}

	private static void onLeave(ServerPlayer player) {
		DaoTechniqueState state = STATES.remove(player.getUUID());
		if (state != null && state.ironBodyUntil != 0L) {
			removeIronBody(player);
		}
	}

	private static void forgetEverywhere(Entity entity) {
		for (DaoTechniqueState state : STATES.values()) {
			state.forget(entity);
		}
		if (entity instanceof LivingEntity living) {
			BURNING.remove(living);
		}
	}

	// ================================================================== helpers

	private static DaoTechniqueState state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), id -> new DaoTechniqueState());
	}

	private static long now(ServerPlayer player) {
		return player.level().getGameTime();
	}

	/** Technique damage (Techniques.hurt checks the target again and is not swallowed by the short hurt cooldown). */
	private static void strike(ServerPlayer player, LivingEntity target, double amount) {
		if (amount > 0.0D && target.isAlive()) {
			Techniques.hurt(player, target, (float) amount);
		}
	}

	private static boolean valid(ServerPlayer player, @Nullable LivingEntity target) {
		return target != null && target.isAlive() && !target.isRemoved() && target.level() == player.level()
				&& Targeting.isHostileTo(target, player);
	}

	/**
	 * Sets an entity's velocity and makes sure every client (and a player's own client) receives it. A thrown monster is
	 * tagged {@link NoGrief#TAG}, so its landing never tramples farmland or crushes turtle eggs.
	 */
	private static void launch(Entity entity, Vec3 velocity) {
		if (!(entity instanceof Player)) {
			entity.addTag(NoGrief.TAG);
		}
		entity.setDeltaMovement(velocity);
		entity.needsSync = true;
		entity.syncVelocity = true;
	}

	/**
	 * Sets an entity (never a block) on fire for {@code seconds}, unless powder snow or a water cauldron is right next to
	 * it, and watches it until the fire is out (see init).
	 */
	private static void ignite(LivingEntity target, float seconds) {
		if (nearExtinguisher(target)) {
			return;
		}
		target.igniteForSeconds(seconds);
		BURNING.merge(target, target.level().getGameTime() + (long) (seconds * 20.0F) + 20L, Math::max);
	}

	/** Read-only: powder snow or a water / powder snow cauldron within a block of where the entity is heading. */
	private static boolean nearExtinguisher(Entity entity) {
		return entity.level().getBlockStatesIfLoaded(entity.getBoundingBox().expandTowards(entity.getDeltaMovement()).inflate(1.0D))
				.anyMatch(state -> state.is(Blocks.POWDER_SNOW) || state.is(Blocks.WATER_CAULDRON) || state.is(Blocks.POWDER_SNOW_CAULDRON));
	}

	/** Read-only: a Nether portal, End portal or End gateway block within {@code radius} (nothing is thrown or pulled then). */
	private static boolean portalNear(ServerPlayer player, double radius) {
		return player.level().getBlockStatesIfLoaded(player.getBoundingBox().inflate(radius))
				.anyMatch(state -> state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY));
	}

	/** Frozen look (shaking, frost) for about {@code extraTicks / 2} ticks beyond full freeze. Entities only. */
	private static void freeze(LivingEntity target, int extraTicks) {
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

	/** Read-only: where a line from {@code from} along {@code dir} meets the first solid block, or its end. */
	private static Vec3 clipEnd(ServerPlayer player, Vec3 from, Vec3 dir, double range) {
		Vec3 to = from.add(dir.scale(range));
		BlockHitResult hit = player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
	}

	/** Read-only: nothing solid between two points. */
	private static boolean clearBetween(ServerPlayer player, Vec3 a, Vec3 b) {
		return player.level().clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType()
				== HitResult.Type.MISS;
	}

	/** Where a particle bolt starts: up to 14 blocks above, below the first ceiling (read-only). */
	private static Vec3 skyAbove(ServerPlayer player, Vec3 ground) {
		RandomSource random = player.getRandom();
		Vec3 from = ground.add(0.0D, 1.5D, 0.0D);
		Vec3 sky = ground.add((random.nextDouble() - 0.5D) * 4.0D, SKY_HEIGHT, (random.nextDouble() - 0.5D) * 4.0D);
		BlockHitResult hit = player.level().clip(new ClipContext(from, sky, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? sky : hit.getLocation().add(0.0D, -0.2D, 0.0D);
	}

	/** True when the entity's box, grown by {@code reach}, touches the segment. */
	private static boolean nearSegment(LivingEntity entity, Vec3 from, Vec3 to, double reach) {
		AABB box = entity.getBoundingBox().inflate(reach);
		return box.contains(from) || box.clip(from, to).isPresent();
	}

	/** The Dao technique whose ordinal carries the Dao Domain pulse of {@code dao} to the client. */
	private static Technique flavour(Dao dao) {
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

	private static void fx(ServerPlayer player, Technique technique, int sub, Vec3 pos, Vec3 target, float f) {
		Techniques.fx(player, technique, sub, pos, target, f);
	}

	private static void sound(ServerPlayer player, Vec3 at, SoundEvent sound, float volume, float pitch) {
		player.level().playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	private static void sound(ServerPlayer player, Vec3 at, Holder<SoundEvent> sound, float volume, float pitch) {
		player.level().playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	/** Why the technique did not happen, in the same action-bar style as Techniques' own refusals. */
	private static void refuse(ServerPlayer player, String key) {
		Techniques.refuse(player, Component.translatable("kultivator.daotech." + key));
	}

	private DaoTechniques() {
	}
}
