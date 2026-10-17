package dev.kultivator.disciple;

import dev.kultivator.beast.SpiritBeasts;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Technique;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.technique.DaoTechniqueSub;
import dev.kultivator.technique.DaoTechniques;
import dev.kultivator.trial.HeartDemonEntity;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.Targeting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The techniques a disciple can use once its master taught them (DESIGN 18): a simplified cast of each one with the
 * same client visuals ({@code FxType.TECHNIQUE}, a = technique ordinal) and damage {@code D = (3 + 2 x realm + 0.5 x
 * stage) x (1 + comprehension / 100)} times the multiplier of the player's table. Costs 60% of the technique's qi from
 * the disciple's own pool, cooldowns as the player's.
 * <p>
 * Block safety and targets, as for the master's techniques: only what {@link Targeting#isHostileTo} accepts for the
 * master is hit, and never a player (not even a sparring partner: the duel rules only know the two players), the master,
 * a pet that leaves the master alone, a spirit beast, another disciple, a heart demon or a mob whose fight would change
 * blocks ({@link #provokesBlockChanges}). Nothing here touches a block: lines stop at the first solid block found by a
 * read-only raycast, fire is only put on entities, lightning is particles, teleports only land on spots checked by
 * {@link Targeting#safeSpot} and reached through open space, every monster hit or pushed gets {@link NoGrief#TAG} and a
 * portal cooldown, and nothing is pushed near a portal. Never a projectile, explosion or LightningBolt.
 */
public final class DiscipleTechniques {

	/** Share of the technique's qi cost a disciple pays. */
	public static final double QI_FACTOR = 0.6D;

	private static final double PALM_RANGE = 12.0D;
	private static final double PALM_WIDTH = 1.2D;
	private static final double BUDDHA_RANGE = 24.0D;
	private static final double BUDDHA_RADIUS = 5.0D;
	private static final int BUDDHA_DELAY = 20;
	private static final double SWORD_QI_RANGE = 16.0D;
	private static final double SWORD_QI_HALF_WIDTH = 1.0D;
	private static final double SWORDS_RANGE = 16.0D;
	private static final int SWORDS_WAVES = 3;
	private static final int SWORDS_WAVE_INTERVAL = 10;
	private static final int SWORDS_PER_WAVE = 4;
	private static final int SWORD_FALL_TICKS = 4;
	private static final double FLAME_RANGE = 7.0D;
	private static final double FLAME_COS = Math.cos(Math.toRadians(30.0D));
	private static final double PHOENIX_RANGE = 24.0D;
	private static final double PHOENIX_REACH = 1.8D;
	private static final double FROST_RADIUS = 6.0D;
	private static final double GLACIER_RANGE = 24.0D;
	private static final double GLACIER_RADIUS = 5.0D;
	private static final int PRISON_TICKS = 80;
	private static final double THUNDER_RANGE = 24.0D;
	private static final double CHAIN_RADIUS = 6.0D;
	private static final int CHAIN_JUMPS = 4;
	private static final int CHAIN_DELAY = 3;
	private static final double HEAVENLY_RANGE = 16.0D;
	private static final int HEAVENLY_BOLTS = 4;
	private static final int HEAVENLY_INTERVAL = 5;
	private static final double SKY_HEIGHT = 14.0D;
	private static final int IRON_BODY_TICKS = 300;
	private static final double STOMP_RADIUS = 7.0D;
	private static final double SPRING_RADIUS = 8.0D;
	private static final double DRAIN_RADIUS = 8.0D;
	private static final double SHADOW_RANGE = 20.0D;
	private static final double GALE_RADIUS = 10.0D;
	private static final int GALE_TICKS = 120;
	private static final double BLINK_RANGE = 24.0D;
	private static final double BLINK_MIN = 6.0D;
	private static final double SPACE_PRISON_RADIUS = 12.0D;
	/** A prison's slowness: strong, but only for a short while (DESIGN 18: disciples' prisons are a short slow). */
	private static final int PRISON_AMPLIFIER = 4;

	/** Something a technique does a few ticks later (the Buddha Palm landing, chain hops...). */
	private record Pending(long due, Consumer<ServerPlayer> action) {
	}

	/**
	 * Pending effects per disciple. The scheduled actions hold their disciple, so entries are removed explicitly: when
	 * they ran ({@link #tick}), when the disciple leaves the world ({@link #forget}) and when the server stops.
	 */
	private static final Map<DiscipleEntity, List<Pending>> PENDING = new HashMap<>();

	// ================================================================== rules

	/** Every technique except the five that need a cultivator's own body or senses (they show as "cannot be taught"). */
	public static boolean teachable(Technique technique) {
		return switch (technique) {
			case FLYING_SWORD, DHARMA_FORM, DAO_DOMAIN, DIVINE_SENSE, CLOUD_STEP -> false;
			default -> true;
		};
	}

	/** Qi a disciple pays for one cast. */
	public static double qiCost(Technique technique) {
		return technique.qiCost() * QI_FACTOR;
	}

	/** D for the disciple: {@code (3 + 2 x realm + 0.5 x stage) x (1 + comprehension / 100)}. */
	public static double power(DiscipleData data) {
		Rank rank = data.rank();
		double base = 3.0D + 2.0D * rank.realm().ordinal() + 0.5D * rank.stage();
		return base * (1.0D + data.comprehension() / 100.0D);
	}

	/** Known, realm reached, Dao matches, not deviating or breaking through, cooled down and affordable. */
	public static boolean ready(DiscipleEntity entity, Technique technique) {
		DiscipleData data = entity.data();
		if (!teachable(technique) || !data.knows(technique) || !data.realm().atLeast(technique.minRealm())) {
			return false;
		}
		Dao dao = technique.dao();
		if (dao != null && dao != data.dao()) {
			return false;
		}
		return !data.deviating() && !data.breakingThrough() && data.cooldownLeft(technique) <= 0
				&& data.qi() + 1.0E-6D >= qiCost(technique);
	}

	/**
	 * True when the disciple may hurt {@code target} for {@code master}: what {@link Targeting#isHostileTo} accepts (a pet
	 * only while it attacks the master), and never a player, the master, a spirit beast, a disciple, a heart demon (a matter
	 * of one cultivator's heart), anything that cannot be seen as an enemy or a mob listed in {@link #provokesBlockChanges}.
	 */
	public static boolean mayHit(DiscipleEntity entity, @Nullable ServerPlayer master, @Nullable Entity target) {
		if (master == null || !(target instanceof LivingEntity living) || living == entity || living == master || !living.isAlive()
				|| living.isRemoved() || living.level() != entity.level()) {
			return false;
		}
		if (living instanceof Player || living instanceof DiscipleEntity || living instanceof HeartDemonEntity || !living.canBeSeenAsEnemy()
				|| SpiritBeasts.isSpiritBeast(living) || provokesBlockChanges(living)) {
			return false;
		}
		return Targeting.isHostileTo(living, master);
	}

	/**
	 * Mobs a disciple's technique never touches because the fight that follows would change blocks (the same list the
	 * disciple's melee keeps to): a provoked creeper explodes, ghasts and blazes shoot fire, breezes' wind charges flip
	 * doors and levers, ravagers break leaves while chasing, a hurt silverfish breaks infested blocks, a heart-bound
	 * creaking grows resin and the Wither breaks blocks after every hit.
	 */
	public static boolean provokesBlockChanges(Entity entity) {
		return entity instanceof Creeper || entity instanceof Ghast || entity instanceof Blaze || entity instanceof Breeze
				|| entity instanceof Ravager || entity instanceof Silverfish || entity instanceof WitherBoss
				|| entity instanceof Creaking creaking && creaking.isHeartBound();
	}

	/** Living things the disciple may hit within {@code radius} of {@code center}, nearest first. */
	public static List<LivingEntity> hostilesAround(DiscipleEntity entity, ServerPlayer master, Vec3 center, double radius) {
		AABB box = new AABB(center, center).inflate(radius);
		double radiusSq = radius * radius;
		List<LivingEntity> list = entity.level().getEntitiesOfClass(LivingEntity.class, box,
				other -> other.distanceToSqr(center) <= radiusSq && mayHit(entity, master, other));
		list.sort(Comparator.comparingDouble(other -> other.distanceToSqr(center)));
		return list;
	}

	// ================================================================== casting

	/**
	 * Casts {@code technique} if the disciple is {@link #ready} and its master is online; spends the qi, starts the
	 * cooldown and feeds Dao comprehension when it really happened. False (nothing spent) when there was nothing to hit,
	 * no safe spot, or nobody to heal.
	 */
	public static boolean cast(DiscipleEntity entity, Technique technique, @Nullable LivingEntity target) {
		ServerPlayer master = entity.owner();
		if (master == null || !(entity.level() instanceof ServerLevel level) || !entity.isAlive() || entity.isDeadOrDying()
				|| !ready(entity, technique)) {
			return false;
		}
		DiscipleData data = entity.data();
		double d = power(data);
		LivingEntity aim = target != null && mayHit(entity, master, target) ? target : null;
		boolean done = switch (technique) {
			case QI_PALM -> aim != null && qiPalm(entity, master, level, aim, d);
			case BUDDHA_PALM -> aim != null && buddhaPalm(entity, master, level, aim, d);
			case SWORD_QI -> aim != null && swordQi(entity, master, level, aim, d);
			case THOUSAND_SWORDS -> thousandSwords(entity, master, level, d);
			case FLAME_PALM -> aim != null && flamePalm(entity, master, level, aim, d);
			case PHOENIX_FLAME -> aim != null && phoenixFlame(entity, master, level, aim, d);
			case FROST_LOTUS -> frostLotus(entity, master, level, d);
			case GLACIER_PRISON -> aim != null && glacierPrison(entity, master, level, aim, d);
			case THUNDER_PALM -> aim != null && thunderPalm(entity, master, level, aim, d);
			case HEAVENLY_THUNDER -> heavenlyThunder(entity, master, level, d);
			case IRON_BODY -> ironBody(entity, level);
			case MOUNTAIN_STOMP -> mountainStomp(entity, master, level, d);
			case SPRING_OF_LIFE -> springOfLife(entity, master, level);
			case LIFE_DRAIN -> lifeDrain(entity, master, level, d);
			case SHADOW_STEP -> aim != null && shadowStep(entity, master, level, aim, d);
			case GALE -> gale(entity, master, level);
			case SPACE_BLINK -> aim != null && spaceBlink(entity, level, aim);
			case SPACE_PRISON -> spacePrison(entity, master, level, d);
			default -> false;
		};
		if (!done) {
			return false;
		}
		data.setQi(data.qi() - qiCost(technique));
		data.setCooldown(technique, technique.cooldownTicks());
		data.addTechniqueCast();
		Dao dao = technique.dao();
		if (dao != null && dao == data.dao()) {
			data.addComprehension(DiscipleCultivation.comprehensionGain(data, dao, DiscipleCultivation.TECHNIQUE_COMPREHENSION));
		}
		if (aim != null && technique != Technique.SPRING_OF_LIFE) {
			face(entity, middleOf(aim));
		}
		return true;
	}

	/** Every server tick of the disciple: runs the delayed parts of its techniques. */
	static void tick(DiscipleEntity entity, ServerLevel level) {
		if (PENDING.isEmpty()) {
			return;
		}
		List<Pending> list = PENDING.get(entity);
		if (list == null) {
			return;
		}
		ServerPlayer master = entity.owner();
		long now = level.getGameTime();
		List<Pending> due = null;
		for (Iterator<Pending> it = list.iterator(); it.hasNext(); ) {
			Pending pending = it.next();
			if (pending.due() <= now) {
				it.remove();
				if (due == null) {
					due = new ArrayList<>();
				}
				due.add(pending);
			}
		}
		if (list.isEmpty()) {
			PENDING.remove(entity);
		}
		if (due == null || master == null || !entity.isAlive() || entity.data().breakingThrough()) {
			return;
		}
		for (Pending pending : due) {
			pending.action().accept(master);
		}
	}

	private static void schedule(DiscipleEntity entity, ServerLevel level, int delayTicks, Consumer<ServerPlayer> action) {
		PENDING.computeIfAbsent(entity, key -> new ArrayList<>()).add(new Pending(level.getGameTime() + Math.max(1, delayTicks), action));
	}

	/** Drops the pending effects of a disciple that left the world (died, unloaded, changed dimension, discarded). */
	static void forget(DiscipleEntity entity) {
		PENDING.remove(entity);
	}

	/** The server stopped: nothing pending survives it. */
	static void clearAll() {
		PENDING.clear();
	}

	// ================================================================== universal

	/** A blast of qi along the line to the target (12 blocks, stopped by blocks): D to everything on it, knocked back. */
	private static boolean qiPalm(DiscipleEntity entity, ServerPlayer master, ServerLevel level, LivingEntity aim, double d) {
		Vec3 eye = entity.getEyePosition();
		if (eye.distanceTo(middleOf(aim)) > PALM_RANGE + 1.0D || !entity.hasLineOfSight(aim)) {
			return false;
		}
		Vec3 dir = middleOf(aim).subtract(eye).normalize();
		Vec3 end = clipEnd(entity, level, eye, dir, PALM_RANGE);
		double length = end.distanceTo(eye);
		boolean any = false;
		for (LivingEntity target : hostilesAround(entity, master, eye.add(end).scale(0.5D), length * 0.5D + PALM_WIDTH + 2.0D)) {
			Vec3 center = middleOf(target);
			double along = center.subtract(eye).dot(dir);
			if (along < -0.5D || along > length + 0.5D) {
				continue;
			}
			Vec3 closest = eye.add(dir.scale(Math.max(0.0D, Math.min(length, along))));
			if (closest.distanceTo(center) > PALM_WIDTH + target.getBbWidth() * 0.5D || !entity.hasLineOfSight(target)) {
				continue;
			}
			if (hurt(entity, master, level, target, d)) {
				any = true;
				knock(level, target, dir.x, dir.z, 0.9D, 0.3D);
				fx(level, Technique.QI_PALM, 1, center, center, 1.0F);
			}
		}
		if (!any) {
			return false;
		}
		fx(level, Technique.QI_PALM, 0, hand(entity, dir), end, 1.0F);
		sound(level, eye, SoundEvents.PLAYER_ATTACK_STRONG, 0.8F, 0.8F);
		sound(level, eye, SoundEvents.BREEZE_SHOOT, 0.7F, 1.3F);
		return true;
	}

	/** A golden palm forms over the target and lands a second later: 2.5 D within 5 blocks, slammed down and slowed. */
	private static boolean buddhaPalm(DiscipleEntity entity, ServerPlayer master, ServerLevel level, LivingEntity aim, double d) {
		if (entity.distanceTo(aim) > BUDDHA_RANGE || !entity.hasLineOfSight(aim)) {
			return false;
		}
		Vec3 point = aim.position();
		fx(level, Technique.BUDDHA_PALM, 0, point, entity.position(), (float) BUDDHA_RADIUS);
		sound(level, point.add(0.0D, 6.0D, 0.0D), SoundEvents.BELL_BLOCK, 1.4F, 0.7F);
		sound(level, entity.position(), SoundEvents.BELL_RESONATE, 0.7F, 0.8F);
		float damage = (float) (2.5D * d);
		schedule(entity, level, BUDDHA_DELAY, owner -> {
			for (LivingEntity target : hostilesAround(entity, owner, point, BUDDHA_RADIUS)) {
				if (hurt(entity, owner, level, target, damage) && target.isAlive()) {
					if (!(target instanceof EnderDragon)) {
						double resist = Math.max(0.0D, Math.min(1.0D, target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)));
						Vec3 motion = target.getDeltaMovement();
						target.addTag(NoGrief.TAG);
						target.setDeltaMovement(motion.x * 0.3D, -1.0D * (1.0D - resist * 0.5D), motion.z * 0.3D);
						target.syncVelocity = true;
					}
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1, false, true), entity);
				}
			}
			fx(level, Technique.BUDDHA_PALM, 1, point, point, (float) BUDDHA_RADIUS);
			sound(level, point, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.3F, 0.7F);
			sound(level, point, SoundEvents.BELL_BLOCK, 1.1F, 0.6F);
		});
		return true;
	}

	// ================================================================== Sword

	/** A crescent of sword qi flies to the target and on (16 blocks, stopped by blocks), 1.5 D to all it passes. */
	private static boolean swordQi(DiscipleEntity entity, ServerPlayer master, ServerLevel level, LivingEntity aim, double d) {
		Vec3 eye = entity.getEyePosition();
		if (eye.distanceTo(middleOf(aim)) > SWORD_QI_RANGE || !entity.hasLineOfSight(aim)) {
			return false;
		}
		Vec3 dir = middleOf(aim).subtract(eye).normalize();
		Vec3 start = eye.add(0.0D, -0.3D, 0.0D).add(dir.scale(0.6D));
		Vec3 end = clipEnd(entity, level, start, dir, SWORD_QI_RANGE);
		List<LivingEntity> hits = new ArrayList<>();
		for (LivingEntity target : hostilesAround(entity, master, start.add(end).scale(0.5D), start.distanceTo(end) * 0.5D + 3.0D)) {
			if (nearSegment(target, start, end, SWORD_QI_HALF_WIDTH)) {
				hits.add(target);
			}
		}
		if (hits.isEmpty()) {
			return false;
		}
		fx(level, Technique.SWORD_QI, DaoTechniqueSub.CAST, start, end, (float) (SWORD_QI_HALF_WIDTH * 2.0D));
		sound(level, entity.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.8F);
		sound(level, entity.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.7F);
		for (LivingEntity target : hits) {
			hurt(entity, master, level, target, d * 1.5D);
			fx(level, Technique.SWORD_QI, DaoTechniqueSub.HIT, middleOf(target), middleOf(target), target.getBbWidth());
		}
		return true;
	}

	/** Three waves of falling swords of light on up to 4 monsters within 16 (0.8 D per sword). */
	private static boolean thousandSwords(DiscipleEntity entity, ServerPlayer master, ServerLevel level, double d) {
		if (hostilesAround(entity, master, entity.position(), SWORDS_RANGE).isEmpty()) {
			return false;
		}
		fx(level, Technique.THOUSAND_SWORDS, DaoTechniqueSub.CAST, entity.position(), entity.position(), (float) SWORDS_RANGE);
		sound(level, entity.position(), SoundEvents.BEACON_ACTIVATE, 0.9F, 1.6F);
		sound(level, entity.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.8F);
		float damage = (float) (d * 0.8D);
		for (int wave = 0; wave < SWORDS_WAVES; wave++) {
			schedule(entity, level, 1 + wave * SWORDS_WAVE_INTERVAL, owner -> {
				List<LivingEntity> around = new ArrayList<>(hostilesAround(entity, owner, entity.position(), SWORDS_RANGE));
				RandomSource random = entity.getRandom();
				for (int i = around.size() - 1; i > 0; i--) {
					int j = random.nextInt(i + 1);
					LivingEntity swap = around.get(i);
					around.set(i, around.get(j));
					around.set(j, swap);
				}
				int count = Math.min(SWORDS_PER_WAVE, around.size());
				for (int i = 0; i < count; i++) {
					LivingEntity target = around.get(i);
					fx(level, Technique.THOUSAND_SWORDS, DaoTechniqueSub.SWORD_FALL, target.position(), target.position(), SWORD_FALL_TICKS);
					schedule(entity, level, SWORD_FALL_TICKS, later -> {
						if (hurt(entity, later, level, target, damage)) {
							sound(level, target.position(), SoundEvents.TRIDENT_HIT, 0.5F, 1.3F + random.nextFloat() * 0.4F);
						}
					});
				}
				if (count > 0) {
					sound(level, entity.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F, 1.5F);
				}
			});
		}
		return true;
	}

	// ================================================================== Fire

	/** A 60 degree cone of spirit fire 7 blocks long towards the target: D and 6 s of burning (entities only). */
	private static boolean flamePalm(DiscipleEntity entity, ServerPlayer master, ServerLevel level, LivingEntity aim, double d) {
		Vec3 eye = entity.getEyePosition();
		if (eye.distanceTo(middleOf(aim)) > FLAME_RANGE + aim.getBbWidth() * 0.5D) {
			return false;
		}
		Vec3 dir = middleOf(aim).subtract(eye).normalize();
		List<LivingEntity> hits = new ArrayList<>();
		for (LivingEntity target : hostilesAround(entity, master, eye, FLAME_RANGE + 1.0D)) {
			Vec3 to = middleOf(target).subtract(eye);
			double distance = to.length();
			if (distance > FLAME_RANGE + target.getBbWidth() * 0.5D) {
				continue;
			}
			boolean inCone = distance < 1.5D ? to.dot(dir) > 0.0D : to.scale(1.0D / distance).dot(dir) >= FLAME_COS;
			if (inCone && entity.hasLineOfSight(target)) {
				hits.add(target);
			}
		}
		if (hits.isEmpty()) {
			return false;
		}
		fx(level, Technique.FLAME_PALM, DaoTechniqueSub.CAST, hand(entity, dir), eye.add(dir.scale(FLAME_RANGE)), (float) FLAME_RANGE);
		sound(level, entity.position(), SoundEvents.FIRECHARGE_USE, 1.0F, 0.9F);
		sound(level, entity.position(), SoundEvents.BLAZE_SHOOT, 0.7F, 1.3F);
		for (LivingEntity target : hits) {
			hurt(entity, master, level, target, d);
			if (target.isAlive()) {
				DaoTechniques.ignite(target, 6.0F);
			}
			fx(level, Technique.FLAME_PALM, DaoTechniqueSub.HIT, middleOf(target), middleOf(target), target.getBbWidth());
		}
		return true;
	}

	/** A phoenix of fire flies through the target (24 blocks, stopped by blocks): 2 D and 8 s of burning to all it passes. */
	private static boolean phoenixFlame(DiscipleEntity entity, ServerPlayer master, ServerLevel level, LivingEntity aim, double d) {
		Vec3 eye = entity.getEyePosition();
		if (eye.distanceTo(middleOf(aim)) > PHOENIX_RANGE || !entity.hasLineOfSight(aim)) {
			return false;
		}
		Vec3 dir = middleOf(aim).subtract(eye).normalize();
		Vec3 start = eye.add(0.0D, -0.2D, 0.0D).add(dir.scale(0.8D));
		Vec3 end = clipEnd(entity, level, start, dir, PHOENIX_RANGE);
		double length = start.distanceTo(end);
		List<LivingEntity> hits = new ArrayList<>();
		for (LivingEntity target : hostilesAround(entity, master, start.add(end).scale(0.5D), length * 0.5D + 3.0D)) {
			if (nearSegment(target, start, end, PHOENIX_REACH)) {
				hits.add(target);
			}
		}
		if (hits.isEmpty()) {
			return false;
		}
		int ticks = Math.max(1, (int) Math.ceil(length));
		fx(level, Technique.PHOENIX_FLAME, DaoTechniqueSub.CAST, start, end, ticks);
		sound(level, entity.position(), SoundEvents.BLAZE_SHOOT, 1.0F, 0.6F);
		sound(level, entity.position(), SoundEvents.ENDER_DRAGON_FLAP, 0.9F, 1.4F);
		float damage = (float) (d * 2.0D);
		for (LivingEntity target : hits) {
			// The phoenix flies one block per tick: each monster burns when it gets there.
			int delay = Math.max(1, (int) Math.ceil(middleOf(target).subtract(start).dot(dir)));
			schedule(entity, level, delay, owner -> {
				if (hurt(entity, owner, level, target, damage) && target.isAlive()) {
					DaoTechniques.ignite(target, 8.0F);
				}
				if (target.isAlive()) {
					fx(level, Technique.PHOENIX_FLAME, DaoTechniqueSub.HIT, middleOf(target), middleOf(target), target.getBbWidth());
				}
			});
		}
		return true;
	}

	// ================================================================== Ice

	/** A lotus of frost blooms around the disciple: D, Slowness IV and frozen for 6 s. No ice blocks, ever. */
	private static boolean frostLotus(DiscipleEntity entity, ServerPlayer master, ServerLevel level, double d) {
		List<LivingEntity> hits = hostilesAround(entity, master, entity.position(), FROST_RADIUS);
		if (hits.isEmpty()) {
			return false;
		}
		fx(level, Technique.FROST_LOTUS, DaoTechniqueSub.CAST, entity.position(), entity.position(), (float) FROST_RADIUS);
		sound(level, entity.position(), SoundEvents.POWDER_SNOW_BREAK, 1.1F, 0.6F);
		sound(level, entity.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 0.9F, 1.5F);
		for (LivingEntity target : hits) {
			hurt(entity, master, level, target, d);
			if (target.isAlive()) {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 120, 3), entity);
				freeze(target, 240);
			}
			fx(level, Technique.FROST_LOTUS, DaoTechniqueSub.HIT, middleOf(target), middleOf(target), target.getBbWidth());
		}
		return true;
	}

	/** The target and every monster within 5 of it are sealed in frost for a short while: 1.5 D and a strong slow. */
	private static boolean glacierPrison(DiscipleEntity entity, ServerPlayer master, ServerLevel level, LivingEntity aim, double d) {
		if (entity.distanceTo(aim) > GLACIER_RANGE || !entity.hasLineOfSight(aim)) {
			return false;
		}
		List<LivingEntity> victims = new ArrayList<>();
		victims.add(aim);
		for (LivingEntity other : hostilesAround(entity, master, aim.position(), GLACIER_RADIUS)) {
			if (other != aim) {
				victims.add(other);
			}
		}
		Vec3 dir = middleOf(aim).subtract(entity.getEyePosition()).normalize();
		fx(level, Technique.GLACIER_PRISON, DaoTechniqueSub.CAST, middleOf(aim), hand(entity, dir), (float) GLACIER_RADIUS);
		sound(level, aim.position(), SoundEvents.POWDER_SNOW_BREAK, 1.1F, 0.5F);
		sound(level, aim.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 0.9F, 0.6F);
		for (LivingEntity victim : victims) {
			hurt(entity, master, level, victim, d * 1.5D);
			if (victim.isAlive()) {
				victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, PRISON_TICKS, PRISON_AMPLIFIER), entity);
				freeze(victim, PRISON_TICKS);
				fx(level, Technique.GLACIER_PRISON, DaoTechniqueSub.MARK, victim.position(), new Vec3(victim.getId(), PRISON_TICKS, 0.0D),
						victim.getBbHeight());
			}
		}
		return true;
	}

	// ================================================================== Lightning

	/** A particle bolt to the target (D), then up to 4 jumps to monsters within 6 of each other (D each). */
	private static boolean thunderPalm(DiscipleEntity entity, ServerPlayer master, ServerLevel level, LivingEntity aim, double d) {
		if (entity.distanceTo(aim) > THUNDER_RANGE || !entity.hasLineOfSight(aim)) {
			return false;
		}
		Vec3 dir = middleOf(aim).subtract(entity.getEyePosition()).normalize();
		Vec3 hand = hand(entity, dir);
		fx(level, Technique.THUNDER_PALM, DaoTechniqueSub.CAST, hand, middleOf(aim), 1.0F);
		fx(level, Technique.THUNDER_PALM, DaoTechniqueSub.BOLT, hand, middleOf(aim), 1.0F);
		sound(level, aim.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.9F, 1.3F);
		sound(level, entity.position(), SoundEvents.FIRECHARGE_USE, 0.5F, 1.8F);
		float damage = (float) d;
		Set<Integer> chained = new HashSet<>();
		chained.add(aim.getId());
		LivingEntity previous = aim;
		for (int jump = 1; jump <= CHAIN_JUMPS; jump++) {
			LivingEntity next = null;
			for (LivingEntity candidate : hostilesAround(entity, master, previous.position(), CHAIN_RADIUS)) {
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
			schedule(entity, level, CHAIN_DELAY * jump, owner -> {
				if (!mayHit(entity, owner, hop)) {
					return;
				}
				Vec3 origin = from.isRemoved() ? fromPos : middleOf(from);
				fx(level, Technique.THUNDER_PALM, DaoTechniqueSub.BOLT, origin, middleOf(hop), 0.8F);
				hurt(entity, owner, level, hop, damage);
				sound(level, hop.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.5F, 1.5F + entity.getRandom().nextFloat() * 0.3F);
			});
			previous = next;
		}
		hurt(entity, master, level, aim, damage);
		return true;
	}

	/** 4 particle bolts from the sky over a second on monsters within 16 (2 D each). Never a LightningBolt entity. */
	private static boolean heavenlyThunder(DiscipleEntity entity, ServerPlayer master, ServerLevel level, double d) {
		if (hostilesAround(entity, master, entity.position(), HEAVENLY_RANGE).isEmpty()) {
			return false;
		}
		fx(level, Technique.HEAVENLY_THUNDER, DaoTechniqueSub.CAST, entity.position(), entity.position(), (float) HEAVENLY_RANGE);
		sound(level, entity.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6F, 1.1F);
		float damage = (float) (d * 2.0D);
		Set<Integer> struck = new HashSet<>();
		for (int i = 0; i < HEAVENLY_BOLTS; i++) {
			schedule(entity, level, 1 + i * HEAVENLY_INTERVAL, owner -> {
				List<LivingEntity> around = hostilesAround(entity, owner, entity.position(), HEAVENLY_RANGE);
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
				RandomSource random = entity.getRandom();
				LivingEntity target = pool.get(random.nextInt(pool.size()));
				struck.add(target.getId());
				Vec3 ground = target.position();
				fx(level, Technique.HEAVENLY_THUNDER, DaoTechniqueSub.BOLT, skyAbove(entity, level, ground), ground, 2.0F);
				hurt(entity, owner, level, target, damage);
				sound(level, ground, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.9F, 0.9F + random.nextFloat() * 0.3F);
			});
		}
		return true;
	}

	// ================================================================== Body

	/** 15 s of Resistance II on the disciple itself. */
	private static boolean ironBody(DiscipleEntity entity, ServerLevel level) {
		if (entity.hasEffect(MobEffects.RESISTANCE)) {
			return false;
		}
		entity.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, IRON_BODY_TICKS, 1), entity);
		fx(level, Technique.IRON_BODY, DaoTechniqueSub.CAST, entity.position(), entity.position(), entity.getBbHeight());
		fx(level, Technique.IRON_BODY, DaoTechniqueSub.MARK, entity.position(), new Vec3(entity.getId(), IRON_BODY_TICKS, 0.0D),
				entity.getBbHeight());
		sound(level, entity.position(), SoundEvents.ANVIL_LAND, 0.4F, 1.4F);
		sound(level, entity.position(), SoundEvents.IRON_GOLEM_REPAIR, 0.9F, 0.9F);
		return true;
	}

	/** A shockwave of radius 7: 2 D, monsters thrown up (never ravagers or the dragon, nothing near a portal) and slowed. */
	private static boolean mountainStomp(DiscipleEntity entity, ServerPlayer master, ServerLevel level, double d) {
		List<LivingEntity> hits = hostilesAround(entity, master, entity.position(), STOMP_RADIUS);
		if (hits.isEmpty()) {
			return false;
		}
		boolean throwing = !portalNear(entity, STOMP_RADIUS + 6.0D);
		fx(level, Technique.MOUNTAIN_STOMP, DaoTechniqueSub.CAST, entity.position(), entity.position(), (float) STOMP_RADIUS);
		sound(level, entity.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.1F, 0.7F);
		sound(level, entity.position(), SoundEvents.ANVIL_LAND, 0.5F, 0.5F);
		for (LivingEntity target : hits) {
			boolean landed = hurt(entity, master, level, target, d * 2.0D);
			if (target.isAlive()) {
				if (landed && throwing) {
					Vec3 away = target.position().subtract(entity.position()).horizontal();
					Vec3 push = away.lengthSqr() < 1.0E-4D ? Vec3.ZERO : away.normalize().scale(0.35D);
					launch(target, new Vec3(push.x, 0.9D, push.z));
				}
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 1), entity);
			}
			fx(level, Technique.MOUNTAIN_STOMP, DaoTechniqueSub.HIT, middleOf(target), middleOf(target), target.getBbWidth());
		}
		return true;
	}

	// ================================================================== Life

	/**
	 * Heals the master (when within 8), the disciple itself and the friendly beings around it (players, pets, spirit
	 * beasts, disciples) by 40% of their max health and cures Poison and Wither. Never a sparring opponent, nor a pet
	 * that attacks the master or the disciple. False when nobody needs it.
	 */
	private static boolean springOfLife(DiscipleEntity entity, ServerPlayer master, ServerLevel level) {
		AABB box = entity.getBoundingBox().inflate(SPRING_RADIUS);
		List<LivingEntity> allies = level.getEntitiesOfClass(LivingEntity.class, box, other -> other.isAlive() && !other.isSpectator()
				&& (other == entity || other instanceof Player || other instanceof DiscipleEntity || SpiritBeasts.isSpiritBeast(other)
						|| other instanceof OwnableEntity ownable && ownable.getOwnerReference() != null)
				&& !Targeting.isHostileTo(other, master)
				&& !(other instanceof Mob mob && (mob.getTarget() == master || mob.getTarget() == entity)));
		boolean needed = false;
		for (LivingEntity ally : allies) {
			if (ally.getHealth() < ally.getMaxHealth() - 0.01F || ally.hasEffect(MobEffects.POISON) || ally.hasEffect(MobEffects.WITHER)) {
				needed = true;
				break;
			}
		}
		if (!needed) {
			return false;
		}
		fx(level, Technique.SPRING_OF_LIFE, DaoTechniqueSub.CAST, entity.position(), entity.position(), (float) SPRING_RADIUS);
		sound(level, entity.position(), SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.6F);
		sound(level, entity.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.2F);
		for (LivingEntity ally : allies) {
			ally.heal(ally.getMaxHealth() * 0.4F);
			ally.removeEffect(MobEffects.POISON);
			ally.removeEffect(MobEffects.WITHER);
			ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0), entity);
			fx(level, Technique.SPRING_OF_LIFE, DaoTechniqueSub.HEAL, ally.position(), ally.position(), ally.getBbHeight());
		}
		return true;
	}

	/** Every monster within 8 takes D; the disciple heals half of what was really dealt. */
	private static boolean lifeDrain(DiscipleEntity entity, ServerPlayer master, ServerLevel level, double d) {
		List<LivingEntity> hits = hostilesAround(entity, master, entity.position(), DRAIN_RADIUS);
		if (hits.isEmpty()) {
			return false;
		}
		Vec3 heart = middleOf(entity);
		double dealt = 0.0D;
		for (LivingEntity target : hits) {
			float before = target.getHealth();
			hurt(entity, master, level, target, d);
			dealt += Math.max(0.0F, before - Math.max(0.0F, target.getHealth()));
			fx(level, Technique.LIFE_DRAIN, DaoTechniqueSub.DRAIN, middleOf(target), heart, target.getBbHeight());
		}
		float heal = (float) (dealt * 0.5D);
		if (heal > 0.0F) {
			entity.heal(heal);
		}
		fx(level, Technique.LIFE_DRAIN, DaoTechniqueSub.CAST, entity.position(), entity.position(), (float) DRAIN_RADIUS);
		fx(level, Technique.LIFE_DRAIN, DaoTechniqueSub.HEAL, entity.position(), entity.position(), entity.getBbHeight());
		sound(level, entity.position(), SoundEvents.EVOKER_CAST_SPELL, 0.9F, 0.8F);
		sound(level, entity.position(), SoundEvents.WARDEN_HEARTBEAT, 0.9F, 1.3F);
		return true;
	}

	// ================================================================== Shadow & Wind

	/** Steps through the shadows to a safe spot right behind the target and strikes it from behind (1.5 D). */
	private static boolean shadowStep(DiscipleEntity entity, ServerPlayer master, ServerLevel level, LivingEntity aim, double d) {
		double distance = entity.distanceTo(aim);
		if (distance > SHADOW_RANGE || distance < 3.0D || aim instanceof Creeper || !entity.hasLineOfSight(aim)) {
			return false;
		}
		Vec3 from = entity.position();
		Vec3 approach = aim.position().subtract(from).horizontal();
		approach = approach.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : approach.normalize();
		double base = aim.getBbWidth() * 0.5D + entity.getBbWidth() * 0.5D;
		Vec3 spot = null;
		for (double gap : new double[]{0.9D, 0.5D, 1.5D}) {
			Vec3 candidate = Targeting.safeSpot(level, entity, aim.position().add(approach.scale(base + gap)));
			if (candidate != null && Math.abs(candidate.y - aim.getY()) <= 2.5D
					&& clearBetween(entity, level, middleOf(aim), candidate.add(0.0D, entity.getBbHeight() * 0.5D, 0.0D))) {
				spot = candidate;
				break;
			}
		}
		if (spot == null) {
			return false;
		}
		fx(level, Technique.SHADOW_STEP, DaoTechniqueSub.CAST, from, spot, entity.getBbHeight());
		sound(level, from, SoundEvents.ENDERMAN_TELEPORT, 0.6F, 1.4F);
		sound(level, from, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.7F, 1.0F);
		moveTo(entity, spot, middleOf(aim));
		sound(level, spot, SoundEvents.SQUID_SQUIRT, 0.5F, 0.6F);
		if (hurt(entity, master, level, aim, d * 1.5D)) {
			fx(level, Technique.SHADOW_STEP, DaoTechniqueSub.HIT, middleOf(aim), middleOf(aim), aim.getBbWidth());
			sound(level, aim.position(), SoundEvents.PLAYER_ATTACK_CRIT, 0.9F, 0.7F);
		}
		return true;
	}

	/**
	 * A storm wind hurls every monster within 10 away (harder when close; never ravagers, the dragon or players, nothing
	 * near a portal) and the disciple turns swift for 6 s.
	 */
	private static boolean gale(DiscipleEntity entity, ServerPlayer master, ServerLevel level) {
		if (portalNear(entity, GALE_RADIUS + 16.0D)) {
			return false;
		}
		List<LivingEntity> blown = hostilesAround(entity, master, entity.position(), GALE_RADIUS);
		if (blown.isEmpty()) {
			return false;
		}
		for (LivingEntity target : blown) {
			Vec3 away = target.position().subtract(entity.position()).horizontal();
			double distance = away.length();
			Vec3 dir = distance < 1.0E-3D ? new Vec3(1.0D, 0.0D, 0.0D) : away.scale(1.0D / distance);
			double closeness = 1.0D - Math.min(1.0D, distance / GALE_RADIUS);
			double strength = 0.9D + 1.7D * closeness;
			launch(target, new Vec3(dir.x * strength, 0.35D + 0.3D * closeness, dir.z * strength));
		}
		entity.addEffect(new MobEffectInstance(MobEffects.SPEED, GALE_TICKS, 1, false, false, true), entity);
		fx(level, Technique.GALE, DaoTechniqueSub.CAST, entity.position(), entity.position(), (float) GALE_RADIUS);
		sound(level, entity.position(), SoundEvents.PHANTOM_SWOOP, 0.9F, 0.8F);
		sound(level, entity.position(), SoundEvents.BREEZE_SHOOT, 0.8F, 0.6F);
		return true;
	}

	// ================================================================== Space

	/** Folds space to a safe spot right in front of a target farther than 6 blocks (24 at most), through open space only. */
	private static boolean spaceBlink(DiscipleEntity entity, ServerLevel level, LivingEntity aim) {
		double distance = entity.distanceTo(aim);
		if (distance > BLINK_RANGE || distance < BLINK_MIN || !entity.hasLineOfSight(aim)) {
			return false;
		}
		Vec3 from = entity.position();
		Vec3 back = from.subtract(aim.position()).horizontal();
		back = back.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : back.normalize();
		double base = aim.getBbWidth() * 0.5D + entity.getBbWidth() * 0.5D + 1.0D;
		Vec3 spot = Targeting.safeSpot(level, entity, aim.position().add(back.scale(base)));
		if (spot == null) {
			return false;
		}
		Vec3 middle = spot.add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
		if (!clearBetween(entity, level, entity.getEyePosition(), middle) || !clearBetween(entity, level, middle, middleOf(aim))) {
			return false;
		}
		fx(level, Technique.SPACE_BLINK, DaoTechniqueSub.CAST, from, spot, entity.getBbHeight());
		sound(level, from, SoundEvents.ENDERMAN_TELEPORT, 0.7F, 0.7F);
		moveTo(entity, spot, middleOf(aim));
		sound(level, spot, SoundEvents.ENDERMAN_TELEPORT, 0.7F, 1.2F);
		sound(level, spot, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5F, 1.8F);
		return true;
	}

	/** Every monster within 12 is caught in a fold of space for a short while: 1.5 D and a strong slow. */
	private static boolean spacePrison(DiscipleEntity entity, ServerPlayer master, ServerLevel level, double d) {
		List<LivingEntity> hits = hostilesAround(entity, master, entity.position(), SPACE_PRISON_RADIUS);
		if (hits.isEmpty()) {
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
		fx(level, Technique.SPACE_PRISON, DaoTechniqueSub.CAST, centre, entity.position(), (float) spread);
		sound(level, centre, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.9F, 0.6F);
		sound(level, centre, SoundEvents.ILLUSIONER_CAST_SPELL, 0.9F, 0.8F);
		for (LivingEntity target : hits) {
			hurt(entity, master, level, target, d * 1.5D);
			if (target.isAlive()) {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, PRISON_TICKS, PRISON_AMPLIFIER), entity);
				fx(level, Technique.SPACE_PRISON, DaoTechniqueSub.MARK, target.position(), new Vec3(target.getId(), PRISON_TICKS, 0.0D),
						target.getBbHeight());
			}
		}
		return true;
	}

	// ================================================================== helpers

	/**
	 * Technique damage from the disciple (a mob attack, so kills are the disciple's), only on targets {@link #mayHit}
	 * accepts; not slowed by the target's short invulnerability after a melee hit. True when it landed.
	 */
	static boolean hurt(DiscipleEntity entity, ServerPlayer master, ServerLevel level, LivingEntity target, double amount) {
		float damage = (float) amount;
		if (!(damage > 0.0F) || !Float.isFinite(damage) || !mayHit(entity, master, target)) {
			return false;
		}
		if (target.damageCooldownTime > 10) {
			target.damageCooldownTime = 10;
		}
		// The hit's knockback could push a monster into a portal (travel could build an exit portal) or make it land on
		// farmland or turtle eggs: the tag keeps every block safe from it.
		target.addTag(NoGrief.TAG);
		target.setPortalCooldown();
		DamageSource source = level.damageSources().mobAttack(entity);
		boolean landed = target.hurtServer(level, source, damage);
		if (landed) {
			// Remembered for the kill share when someone else lands the last blow (Disciples.afterDeath).
			entity.setLastHurtMob(target);
		}
		return landed;
	}

	/** Sets a monster's velocity for every client, with {@link NoGrief#TAG} and a portal cooldown; never moves the dragon or a ravager. */
	private static void launch(LivingEntity target, Vec3 velocity) {
		if (target instanceof EnderDragon || target instanceof Ravager || target instanceof Player) {
			return;
		}
		target.addTag(NoGrief.TAG);
		target.setPortalCooldown();
		target.setDeltaMovement(velocity);
		target.needsSync = true;
		target.syncVelocity = true;
	}

	/** Pushes along (x, z), less for knockback-resistant targets (same rules as {@link #launch}). */
	private static void knock(ServerLevel level, LivingEntity target, double x, double z, double strength, double lift) {
		double length = Math.sqrt(x * x + z * z);
		if (length < 1.0E-6D || target instanceof Ravager || target instanceof EnderDragon || target instanceof Player) {
			return;
		}
		double resist = Math.max(0.0D, Math.min(1.0D, target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)));
		double scale = strength * (1.0D - resist) / length;
		if (scale <= 0.0D) {
			return;
		}
		Vec3 motion = target.getDeltaMovement();
		launch(target, new Vec3(motion.x * 0.5D + x * scale, Math.max(motion.y, lift * (1.0D - resist)), motion.z * 0.5D + z * scale));
	}

	/** Frozen look (shaking, frost) for about {@code extraTicks / 2} ticks beyond full freeze. Entities only. */
	private static void freeze(LivingEntity target, int extraTicks) {
		if (target.isAlive() && target.canFreeze()) {
			target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + extraTicks));
		}
	}

	/** Puts the disciple on a checked spot facing {@code look}. */
	private static void moveTo(DiscipleEntity entity, Vec3 spot, Vec3 look) {
		Vec3 delta = look.subtract(spot.add(0.0D, entity.getEyeHeight(), 0.0D));
		float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
		entity.getNavigation().stop();
		entity.snapTo(spot.x, spot.y, spot.z, yaw, 0.0F);
		entity.setYHeadRot(yaw);
		entity.setDeltaMovement(Vec3.ZERO);
		entity.resetFallDistance();
		// Portal travel could build an exit portal or the End platform (blocks).
		entity.setPortalCooldown();
		entity.needsSync = true;
	}

	/** Turns the disciple's body and head towards {@code point}. */
	private static void face(DiscipleEntity entity, Vec3 point) {
		Vec3 delta = point.subtract(entity.getEyePosition());
		if (delta.horizontalDistanceSqr() < 1.0E-4D) {
			return;
		}
		float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
		entity.setYRot(yaw);
		entity.setYHeadRot(yaw);
	}

	/** Read-only: a Nether portal, End portal or End gateway within {@code radius} (nothing is thrown then). */
	private static boolean portalNear(DiscipleEntity entity, double radius) {
		return entity.level().getBlockStatesIfLoaded(entity.getBoundingBox().inflate(radius))
				.anyMatch(state -> state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY));
	}

	/** Read-only: where a line from {@code from} along {@code dir} meets the first solid block, or its end. */
	private static Vec3 clipEnd(DiscipleEntity entity, ServerLevel level, Vec3 from, Vec3 dir, double range) {
		Vec3 to = from.add(dir.scale(range));
		BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
		return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
	}

	/** Read-only: nothing solid between two points. */
	private static boolean clearBetween(DiscipleEntity entity, ServerLevel level, Vec3 a, Vec3 b) {
		return level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getType() == HitResult.Type.MISS;
	}

	/** Where a particle bolt starts: up to 14 blocks above, below the first ceiling (read-only). */
	private static Vec3 skyAbove(DiscipleEntity entity, ServerLevel level, Vec3 ground) {
		RandomSource random = entity.getRandom();
		Vec3 from = ground.add(0.0D, 1.5D, 0.0D);
		Vec3 sky = ground.add((random.nextDouble() - 0.5D) * 4.0D, SKY_HEIGHT, (random.nextDouble() - 0.5D) * 4.0D);
		BlockHitResult hit = level.clip(new ClipContext(from, sky, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
		return hit.getType() == HitResult.Type.MISS ? sky : hit.getLocation().add(0.0D, -0.2D, 0.0D);
	}

	/** True when the entity's box, grown by {@code reach}, touches the segment. */
	private static boolean nearSegment(LivingEntity entity, Vec3 from, Vec3 to, double reach) {
		AABB box = entity.getBoundingBox().inflate(reach);
		return box.contains(from) || box.clip(from, to).isPresent();
	}

	private static Vec3 middleOf(Entity entity) {
		return entity.getBoundingBox().getCenter();
	}

	/** Roughly where the casting palm is, towards {@code dir}. */
	private static Vec3 hand(DiscipleEntity entity, Vec3 dir) {
		return entity.getEyePosition().add(dir.scale(0.6D)).add(0.0D, -0.35D, 0.0D);
	}

	/** FxType.TECHNIQUE from the disciple (Techniques.fx needs a player caster). */
	private static void fx(ServerLevel level, Technique technique, int sub, Vec3 pos, Vec3 target, float scale) {
		Fx.send(level, pos, target, FxType.TECHNIQUE, technique.ordinal(), sub, scale);
	}

	private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.NEUTRAL, volume, pitch);
	}

	private DiscipleTechniques() {
	}
}
