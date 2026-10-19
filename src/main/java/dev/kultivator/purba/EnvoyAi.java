package dev.kultivator.purba;

import dev.kultivator.Kultivator;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.cultivation.XpSources;
import dev.kultivator.data.XpSource;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.technique.spec.run.ExternalControl;
import dev.kultivator.util.KillCredit;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The envoys' waves, skills and targets (DESIGN 26.5.3, 26.5.4): {@link #spawnWave} (the contract behind
 * {@code Purba.spawnEnvoyWave}, ≤ 8 envoys alive, the rest queued), the Panglima's descent, a skill pass every 10 ticks
 * over the tracked set (Tombak Cahaya; the Panglima's Pedang Hukuman, Rantai Langit and Segel Pengadilan, ×1.3 as often
 * below half health: Murka) with telegraphs fired on time, the lonely despawn and the kill rewards (XP, 20% seal shard).
 * Skills are shares of the target's max health through {@link PurbaHits}; nothing changes a block.
 */
final class EnvoyAi {

	public static final int MAX_ALIVE = 8;
	public static final double SPEAR_SHARE = 0.08D;
	public static final double SWORD_SHARE = 0.20D;
	public static final double SEAL_SHARE = 0.15D;
	public static final double SEAL_RADIUS = 6.0D;
	public static final int SPEAR_COOLDOWN = 100;
	public static final int SWORD_COOLDOWN = 160;
	public static final int CHAIN_COOLDOWN = 240;
	public static final int SEAL_COOLDOWN = 280;
	public static final int ROOT_TICKS = 40;
	public static final double MURKA_RATE = 1.3D;
	public static final double SKILL_RANGE = 16.0D;
	public static final double SHARD_CHANCE = 0.20D;
	public static final double XP_COMBAT = 5.0D;
	/** An envoy with its focus gone, in another level or beyond this range for {@link #LONELY_TICKS} leaves. */
	public static final double LONELY_RANGE = 64.0D;
	public static final int LONELY_TICKS = 600;

	/** A live envoy and its skill clocks. */
	static final class Tracked {
		final HeavenEnvoyEntity mob;
		@Nullable Wave wave;
		long nextA;
		long nextB;
		long nextC;
		long lonelySince;

		Tracked(HeavenEnvoyEntity mob, long now) {
			this.mob = mob;
			this.nextA = now + 40L + mob.getRandom().nextInt(40);
			this.nextB = now + 80L + mob.getRandom().nextInt(40);
			this.nextC = now + 120L + mob.getRandom().nextInt(40);
			this.lonelySince = now;
		}
	}

	/** One wave: told once when its last envoy is gone (true = all slain). */
	static final class Wave {
		final UUID target;
		final int k;
		final int realm;
		int queued;
		final List<HeavenEnvoyEntity> mobs = new ArrayList<>();
		final Consumer<Boolean> onCleared;
		final long startedAt;
		boolean failed;
		boolean done;
		/** Raised during the target's ascension ritual: dismissed as soon as the ritual ends (DESIGN 26.15.2). */
		boolean ascension;

		Wave(UUID target, int k, int realm, int queued, Consumer<Boolean> onCleared, long now) {
			this.target = target;
			this.k = k;
			this.realm = realm;
			this.queued = queued;
			this.onCleared = onCleared;
			this.startedAt = now;
		}

		int alive() {
			int n = 0;
			for (HeavenEnvoyEntity mob : mobs) {
				if (!mob.isRemoved() && mob.isAlive()) {
					n++;
				}
			}
			return n;
		}

		void dismiss() {
			queued = 0;
			for (HeavenEnvoyEntity mob : List.copyOf(mobs)) {
				if (!mob.isRemoved()) {
					mob.vanish();
				}
			}
		}
	}

	/** A telegraphed strike waiting for its moment. */
	private record Shot(HeavenEnvoyEntity mob, int kind, Vec3 from, Vec3 to, double radius, double share, long fireAt) {
	}

	private static final int SHOT_SPEAR = 0;
	private static final int SHOT_SWORD = 1;
	private static final int SHOT_SEAL = 2;

	private static final Map<UUID, Tracked> TRACKED = new LinkedHashMap<>();
	private static final List<Wave> WAVES = new ArrayList<>();
	private static final List<Shot> SHOTS = new ArrayList<>();

	static void clear() {
		TRACKED.clear();
		WAVES.clear();
		SHOTS.clear();
	}

	// ================================================================== spawning

	/** The contract of {@code Purba.spawnEnvoyWave}: spawns up to the alive cap now, queues the rest. */
	static int spawnWave(ServerPlayer target, int count, int k, @Nullable Consumer<Boolean> onCleared) {
		Consumer<Boolean> callback = onCleared == null ? cleared -> {
		} : onCleared;
		ServerLevel level = target.level();
		if (count <= 0 || level.getDifficulty() == Difficulty.PEACEFUL) {
			callback.accept(Boolean.TRUE);
			return 0;
		}
		Wave wave = new Wave(target.getUUID(), Math.max(0, k), Purba.realmEff(target), count, callback, level.getGameTime());
		wave.ascension = k == 0 && dev.kultivator.abadi.Abadi.inAscension(target);
		WAVES.add(wave);
		return fill(wave, target);
	}

	/** Spawns queued envoys of {@code wave} while the alive cap allows; returns how many spawned. */
	private static int fill(Wave wave, ServerPlayer target) {
		int spawned = 0;
		while (wave.queued > 0 && alive() < MAX_ALIVE) {
			HeavenEnvoyEntity mob = spawnOne(target, HeavenEnvoyEntity.ENVOY, wave.realm, wave.k);
			if (mob == null) {
				break;
			}
			wave.queued--;
			wave.mobs.add(mob);
			Tracked tracked = TRACKED.get(mob.getUUID());
			if (tracked != null) {
				tracked.wave = wave;
			}
			spawned++;
		}
		return spawned;
	}

	/** One envoy or the Panglima 6-10 blocks from {@code target} on a safe spot; null when there is none. */
	static @Nullable HeavenEnvoyEntity spawnOne(ServerPlayer target, int kind, int realm, int k) {
		ServerLevel level = target.level();
		HeavenEnvoyEntity mob = PurbaEntities.HEAVEN_ENVOY.create(level, EntitySpawnReason.EVENT);
		if (mob == null) {
			return null;
		}
		RandomSource random = level.getRandom();
		Vec3 spot = null;
		for (int attempt = 0; spot == null && attempt < 8; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double distance = 6.0D + random.nextDouble() * 4.0D;
			Vec3 near = target.position().add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
			if (level.isPositionEntityTicking(BlockPos.containing(near))) {
				spot = Targeting.safeSpot(level, mob, near);
			}
		}
		if (spot == null) {
			spot = Targeting.safeSpot(level, mob, target.position());
		}
		if (spot == null) {
			return null;
		}
		mob.snapTo(spot.x, spot.y, spot.z, random.nextFloat() * 360.0F, 0.0F);
		mob.setup(kind, realm, k, target.getUUID());
		if (!level.addFreshEntity(mob)) {
			return null;
		}
		mob.setTarget(target);
		TRACKED.put(mob.getUUID(), new Tracked(mob, level.getGameTime()));
		Fx.send(level, mob.position(), FxType.PURBA, PurbaFxIds.DESCEND, kind, 0.0F);
		level.playSound(null, spot.x, spot.y, spot.z, kind == HeavenEnvoyEntity.PANGLIMA ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_POWER_SELECT,
				SoundSource.HOSTILE, kind == HeavenEnvoyEntity.PANGLIMA ? 2.0F : 0.8F, kind == HeavenEnvoyEntity.PANGLIMA ? 0.6F : 1.2F);
		return mob;
	}

	/** Envoys alive in the world. */
	static int alive() {
		int n = 0;
		for (Tracked tracked : TRACKED.values()) {
			if (!tracked.mob.isRemoved() && tracked.mob.isAlive()) {
				n++;
			}
		}
		return n;
	}

	/** Every live envoy (and Panglima) of this world within {@code range}. */
	static List<HeavenEnvoyEntity> near(ServerLevel level, Vec3 pos, double range) {
		List<HeavenEnvoyEntity> list = new ArrayList<>();
		for (Tracked tracked : TRACKED.values()) {
			HeavenEnvoyEntity mob = tracked.mob;
			if (!mob.isRemoved() && mob.level() == level && mob.position().distanceToSqr(pos) <= range * range) {
				list.add(mob);
			}
		}
		return list;
	}

	/** Cancels every wave aimed at {@code target} (its envoys leave; the callbacks are told false). */
	static void dismissFor(UUID target) {
		for (Wave wave : List.copyOf(WAVES)) {
			if (wave.target.equals(target) && !wave.done) {
				wave.failed = true;
				wave.dismiss();
			}
		}
	}

	// ================================================================== tick

	static void tick(MinecraftServer server, long tick) {
		if (!SHOTS.isEmpty()) {
			fireDue();
		}
		if (tick % 10L != 0L) {
			return;
		}
		if (!TRACKED.isEmpty()) {
			pass(server);
		}
		if (!WAVES.isEmpty()) {
			waves(server);
		}
	}

	private static void pass(MinecraftServer server) {
		Iterator<Tracked> iterator = TRACKED.values().iterator();
		while (iterator.hasNext()) {
			Tracked tracked = iterator.next();
			HeavenEnvoyEntity mob = tracked.mob;
			if (mob.isRemoved() || mob.isDeadOrDying()) {
				iterator.remove();
				Wave wave = tracked.wave;
				if (wave != null && mob.despawned()) {
					wave.failed = true;
				}
				continue;
			}
			if (!(mob.level() instanceof ServerLevel level)) {
				continue;
			}
			long now = level.getGameTime();
			ServerPlayer focus = mob.focus == null ? null : server.getPlayerList().getPlayer(mob.focus);
			boolean near = focus != null && focus.isAlive() && focus.level() == level && focus.distanceToSqr(mob) <= LONELY_RANGE * LONELY_RANGE;
			if (near) {
				tracked.lonelySince = now;
			} else if (now - tracked.lonelySince >= LONELY_TICKS || focus == null) {
				mob.vanish();
				continue;
			}
			skills(level, tracked, now);
		}
	}

	private static void waves(MinecraftServer server) {
		List<Wave> finished = new ArrayList<>();
		for (Wave wave : WAVES) {
			if (wave.done) {
				finished.add(wave);
				continue;
			}
			ServerPlayer target = server.getPlayerList().getPlayer(wave.target);
			// An ascension wave outlives nothing: the ritual ended (target dead, gone, or the ritual over), so it leaves.
			if (wave.ascension && (target == null || !target.isAlive() || !dev.kultivator.abadi.Abadi.inAscension(target))) {
				wave.failed = true;
				wave.dismiss();
				wave.done = true;
				finished.add(wave);
				continue;
			}
			if (wave.queued > 0) {
				if (target == null || !target.isAlive()) {
					wave.failed = true;
					wave.dismiss();
				} else {
					fill(wave, target);
					// Nowhere to stand for 10 s and nothing spawned at all: there is nothing to fight, the wave counts as met.
					if (wave.mobs.isEmpty() && target.level().getGameTime() - wave.startedAt > 200L) {
						wave.queued = 0;
					}
				}
			}
			if (wave.queued <= 0 && wave.alive() == 0) {
				wave.done = true;
				for (HeavenEnvoyEntity mob : wave.mobs) {
					if (mob.despawned()) {
						wave.failed = true;
					}
				}
				finished.add(wave);
			}
		}
		for (Wave wave : finished) {
			WAVES.remove(wave);
			try {
				wave.onCleared.accept(!wave.failed);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("An envoy wave callback failed", exception);
			}
		}
	}

	private static void skills(ServerLevel level, Tracked tracked, long now) {
		HeavenEnvoyEntity mob = tracked.mob;
		LivingEntity target = mob.getTarget();
		if (target == null || !target.isAlive() || !PurbaHits.mayHit(mob, target)) {
			return;
		}
		double distance = mob.distanceTo(target);
		if (distance > SKILL_RANGE) {
			return;
		}
		double rate = mob.isPanglima() && mob.getHealth() < mob.getMaxHealth() * 0.5F ? MURKA_RATE : 1.0D;
		if (!mob.isPanglima()) {
			if (now >= tracked.nextA && mob.hasLineOfSight(target)) {
				tracked.nextA = now + SPEAR_COOLDOWN;
				line(level, mob, target, SHOT_SPEAR, SPEAR_SHARE, PurbaFxIds.SPEAR_TICKS);
			}
			return;
		}
		if (now >= tracked.nextC && distance <= SEAL_RADIUS + 3.0D) {
			tracked.nextC = now + Math.round(SEAL_COOLDOWN / rate);
			Vec3 at = target.position();
			SHOTS.add(new Shot(mob, SHOT_SEAL, at, at, SEAL_RADIUS, SEAL_SHARE, now + PurbaFxIds.SEAL_TICKS));
			Fx.send(level, at.add(0.0D, 0.1D, 0.0D), new Vec3(PurbaFxIds.SEAL_TICKS, 0.0D, 0.0D), FxType.PURBA, PurbaFxIds.RING, 0xFFE08A, (float) SEAL_RADIUS);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.0F, 0.6F);
			return;
		}
		if (now >= tracked.nextB) {
			tracked.nextB = now + Math.round(CHAIN_COOLDOWN / rate);
			chain(level, mob, target);
			return;
		}
		if (now >= tracked.nextA && mob.hasLineOfSight(target)) {
			tracked.nextA = now + Math.round(SWORD_COOLDOWN / rate);
			line(level, mob, target, SHOT_SWORD, SWORD_SHARE, PurbaFxIds.SWORD_TICKS);
		}
	}

	/** Tombak Cahaya / Pedang Hukuman: a line from the envoy through the target, telegraphed. */
	private static void line(ServerLevel level, HeavenEnvoyEntity mob, LivingEntity target, int kind, double share, int ticks) {
		Vec3 from = mob.position().add(0.0D, 1.2D, 0.0D);
		Vec3 aim = target.position().add(0.0D, 1.0D, 0.0D);
		Vec3 dir = aim.subtract(from);
		double length = dir.length();
		Vec3 to = length < 1.0E-3D ? aim : from.add(dir.scale((length + 4.0D) / length));
		SHOTS.add(new Shot(mob, kind, from, to, kind == SHOT_SWORD ? 1.6D : 1.2D, share, level.getGameTime() + ticks));
		Fx.send(level, from, to, FxType.PURBA, PurbaFxIds.LINE, kind == SHOT_SWORD ? 0xFFD060 : 0xFFF6D0, ticks);
		level.playSound(null, from.x, from.y, from.z, kind == SHOT_SWORD ? SoundEvents.ILLUSIONER_CAST_SPELL : SoundEvents.EVOKER_CAST_SPELL,
				SoundSource.HOSTILE, 0.9F, kind == SHOT_SWORD ? 0.7F : 1.3F);
	}

	/** Rantai Langit: a 2 s root (players are slowed to a stop; others through the 6c bind rules). */
	private static void chain(ServerLevel level, HeavenEnvoyEntity mob, LivingEntity target) {
		Fx.send(level, mob.position().add(0.0D, 1.2D, 0.0D), target.position().add(0.0D, 1.0D, 0.0D), FxType.PURBA, PurbaFxIds.LINE, 0xF0E6B0, 6.0F);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 1.2F, 0.6F);
		if (target instanceof Player) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ROOT_TICKS, 9, false, true, true), mob);
		} else {
			ExternalControl.root(target, ROOT_TICKS, -1);
		}
	}

	private static void fireDue() {
		List<Shot> due = new ArrayList<>();
		Iterator<Shot> iterator = SHOTS.iterator();
		while (iterator.hasNext()) {
			Shot shot = iterator.next();
			if (shot.mob().isRemoved() || !shot.mob().isAlive()) {
				iterator.remove();
				continue;
			}
			if (shot.mob().level().getGameTime() >= shot.fireAt()) {
				iterator.remove();
				due.add(shot);
			}
		}
		for (Shot shot : due) {
			fire(shot);
		}
	}

	private static void fire(Shot shot) {
		HeavenEnvoyEntity mob = shot.mob();
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		if (shot.kind() == SHOT_SEAL) {
			Vec3 at = shot.from();
			Fx.send(level, at.add(0.0D, 0.1D, 0.0D), FxType.PURBA, PurbaFxIds.HIT, 0xFFE08A, (float) shot.radius());
			level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.2F, 0.8F);
			for (LivingEntity each : PurbaHits.around(level, mob, at, shot.radius(), 2.5D)) {
				if (PurbaHits.share(level, mob, each, shot.share())) {
					each.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1), mob);
				}
			}
			return;
		}
		Fx.send(level, shot.to(), FxType.PURBA, PurbaFxIds.HIT, shot.kind() == SHOT_SWORD ? 0xFFD060 : 0xFFF6D0, 1.2F);
		level.playSound(null, shot.from().x, shot.from().y, shot.from().z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.0F,
				shot.kind() == SHOT_SWORD ? 0.6F : 1.2F);
		for (LivingEntity each : PurbaHits.alongLine(level, mob, shot.from(), shot.to(), shot.radius())) {
			PurbaHits.share(level, mob, each, shot.share());
		}
	}

	/** A column of light where an envoy leaves. */
	static void vanishFx(ServerLevel level, HeavenEnvoyEntity mob) {
		Fx.send(level, mob.position(), FxType.PURBA, PurbaFxIds.DESCEND, mob.kind(), 1.0F);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 0.8F, 1.4F);
	}

	// ================================================================== rewards (DESIGN 26.5.4)

	/** An envoy died: XP {@code combatXp × 5}, 20% a heaven seal shard (no beast core); the Panglima goes to the wrath. */
	static void afterDeath(HeavenEnvoyEntity mob, DamageSource source) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		if (mob.isPanglima()) {
			HeavenlyWrath.onPanglimaDeath(mob);
			return;
		}
		KillCredit.Credit credit = KillCredit.resolve(mob, source);
		if (credit == null || credit.owner() instanceof FakePlayer) {
			return;
		}
		ServerPlayer owner = credit.owner();
		if (level.getRandom().nextDouble() < SHARD_CHANCE) {
			Purba.drop(level, mob.position(), new net.minecraft.world.item.ItemStack(PurbaItems.HEAVEN_SEAL_SHARD), null);
		}
		if (credit.kind() != KillCredit.Kind.DISCIPLE) {
			double combat = XpSources.combatXp(mob);
			double xp = combat * XP_COMBAT;
			double extra = credit.kind() == KillCredit.Kind.PLAYER ? Math.max(0.0D, xp - combat) : xp;
			if (extra > 0.0D) {
				XpSources.showKillGain(owner, Progression.grantXp(owner, extra, XpSource.COMBAT));
			}
		}
	}

	// ================================================================== target goal

	/** Picks the envoy's focus first, else the nearest target {@link PurbaHits#mayHit} allows (every 10 ticks). */
	static final class ChooseTargetGoal extends Goal {

		private final HeavenEnvoyEntity mob;
		private int cooldown;

		ChooseTargetGoal(HeavenEnvoyEntity mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Goal.Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			if (--cooldown > 0) {
				return false;
			}
			cooldown = 10;
			LivingEntity current = mob.getTarget();
			if (current != null && PurbaHits.mayHit(mob, current) && mob.distanceToSqr(current) <= 32.0D * 32.0D) {
				return false;
			}
			LivingEntity found = null;
			if (mob.focus != null && mob.level() instanceof ServerLevel level) {
				Entity focus = level.getEntity(mob.focus);
				if (focus instanceof LivingEntity living && PurbaHits.mayHit(mob, living) && mob.distanceToSqr(living) <= 48.0D * 48.0D) {
					found = living;
				}
			}
			if (found == null) {
				AABB box = mob.getBoundingBox().inflate(24.0D, 6.0D, 24.0D);
				double best = 24.0D * 24.0D;
				for (LivingEntity each : mob.level().getEntitiesOfClass(LivingEntity.class, box, e -> PurbaHits.mayHit(mob, e))) {
					double sq = mob.distanceToSqr(each);
					if (sq <= best && mob.getSensing().hasLineOfSight(each)) {
						best = sq;
						found = each;
					}
				}
			}
			if (found != current) {
				mob.setTarget(found);
			}
			return false;
		}
	}

	private EnvoyAi() {
	}
}
