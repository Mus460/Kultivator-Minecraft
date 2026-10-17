package dev.kultivator.trial;

import dev.kultivator.Kultivator;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.Targeting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

/**
 * Iblis Hati: a dark copy of one cultivator. It only ever fights its owner (melee, a dark qi dash and a telegraphed
 * "dark palm"), takes damage only from its owner (their hits, projectiles and techniques), never burns, explodes,
 * drops, picks up, rides or pushes anything, and is never saved. Its blows are {@code kultivator:heart_demon} damage,
 * which does not scale with the difficulty, so Peaceful does not make the trial free. The trial rules (time limit,
 * results, boss bar, taunts) live in {@link HeartDemons}, which also discards the demon when the trial ends; a demon
 * that belongs to no running trial removes itself.
 */
public class HeartDemonEntity extends Monster {

	/** The demon's damage type (data/kultivator/damage_type/heart_demon.json, scaling never). */
	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Kultivator.id("heart_demon"));

	/** The owner, synced so the client can draw the owner's skin and name. */
	private static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> DATA_OWNER =
			SynchedEntityData.defineId(HeartDemonEntity.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);
	/** Ticks left of the dark palm wind-up (0 = not charging), synced for the raised-arm pose and the client motes. */
	private static final EntityDataAccessor<Integer> DATA_PALM_CHARGE =
			SynchedEntityData.defineId(HeartDemonEntity.class, EntityDataSerializers.INT);

	/** The special moves come every 6-10 s. */
	private static final int SPECIAL_MIN_TICKS = 120;
	private static final int SPECIAL_MAX_TICKS = 200;
	private static final int PALM_CHARGE_TICKS = 16;
	private static final double PALM_RANGE = 6.0D;
	private static final float PALM_FACTOR = 1.5F;
	/** Base speed, a little faster than a zombie (0.23). */
	public static final double SPEED = 0.26D;

	private int specialCooldown = SPECIAL_MIN_TICKS;
	private int palmCharge;
	private boolean killedByOwner;

	public HeartDemonEntity(EntityType<? extends HeartDemonEntity> type, Level level) {
		super(type, level);
		this.xpReward = 0;
		this.setCanPickUpLoot(false);
	}

	/** Base attributes; {@link #setup} replaces health and attack with the owner's numbers when it is summoned. */
	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 40.0D)
				.add(Attributes.ATTACK_DAMAGE, 4.0D)
				.add(Attributes.MOVEMENT_SPEED, SPEED)
				.add(Attributes.FOLLOW_RANGE, 48.0D)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.5D)
				.add(Attributes.ARMOR, 2.0D)
				.add(Attributes.STEP_HEIGHT, 1.0D);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_OWNER, Optional.empty());
		builder.define(DATA_PALM_CHARGE, 0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15D, true));
		this.goalSelector.addGoal(3, new LookAtOwnerGoal(this));
		// No target goals: the only target it ever gets is its owner (see setTarget).
	}

	// ================================================================== owner

	/**
	 * Binds the demon to {@code owner} and copies their strength: max health x1.2, the owner's attack damage, speed a
	 * little above a zombie, follow range 48, knockback resistance 0.5. Also tags it so it can never change a block.
	 */
	public void setup(ServerPlayer owner, float healthFraction) {
		this.entityData.set(DATA_OWNER, Optional.of(EntityReference.<LivingEntity>of(owner.getUUID())));
		setBase(Attributes.MAX_HEALTH, Math.max(10.0D, owner.getMaxHealth() * 1.2D));
		setBase(Attributes.ATTACK_DAMAGE, Math.max(2.0D, owner.getAttributeValue(Attributes.ATTACK_DAMAGE)));
		setBase(Attributes.MOVEMENT_SPEED, SPEED);
		setBase(Attributes.FOLLOW_RANGE, 48.0D);
		setBase(Attributes.KNOCKBACK_RESISTANCE, 0.5D);
		this.setHealth(Math.max(1.0F, this.getMaxHealth() * Mth.clamp(healthFraction, 0.05F, 1.0F)));
		this.addTag(NoGrief.TAG);
		this.setCanPickUpLoot(false);
		this.specialCooldown = SPECIAL_MIN_TICKS + this.random.nextInt(SPECIAL_MAX_TICKS - SPECIAL_MIN_TICKS + 1);
	}

	private void setBase(Holder<Attribute> attribute, double value) {
		AttributeInstance instance = this.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	public @Nullable UUID getOwnerUUID() {
		return this.entityData.get(DATA_OWNER).map(EntityReference::getUUID).orElse(null);
	}

	public boolean isOwner(@Nullable Entity entity) {
		UUID owner = getOwnerUUID();
		return entity != null && owner != null && owner.equals(entity.getUUID());
	}

	/** The owner when they are online (server side only; null on the client). */
	public @Nullable ServerPlayer getOwnerPlayer() {
		UUID owner = getOwnerUUID();
		if (owner == null || !(this.level() instanceof ServerLevel level)) {
			return null;
		}
		return level.getServer().getPlayerList().getPlayer(owner);
	}

	/** True while the dark palm is gathering (synced). */
	public boolean isChargingPalm() {
		return this.entityData.get(DATA_PALM_CHARGE) > 0;
	}

	/** True when the owner landed the killing blow (read by {@link HeartDemons} to decide the trial). */
	public boolean killedByOwner() {
		return killedByOwner;
	}

	// ================================================================== behaviour

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide() && this.tickCount % 10 == 0 && !this.isRemoved() && !HeartDemons.isTracked(this)) {
			// Its trial is over (or never existed): a heart demon cannot exist on its own.
			this.discard();
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		ServerPlayer owner = getOwnerPlayer();
		if (owner == null || owner.level() != level || !owner.isAlive()) {
			this.setTarget(null);
			cancelPalm();
			return;
		}
		if (this.getTarget() != owner) {
			this.setTarget(owner);
		}
		if (palmCharge > 0) {
			palmCharge--;
			this.getLookControl().setLookAt(owner, 30.0F, 30.0F);
			if (palmCharge == 0) {
				this.entityData.set(DATA_PALM_CHARGE, 0);
				releasePalm(level, owner);
			}
			return;
		}
		if (--specialCooldown > 0) {
			return;
		}
		specialCooldown = SPECIAL_MIN_TICKS + this.random.nextInt(SPECIAL_MAX_TICKS - SPECIAL_MIN_TICKS + 1);
		if (owner.isCreative() || owner.isSpectator()) {
			return;
		}
		double distanceSq = this.distanceToSqr(owner);
		boolean palmPossible = distanceSq <= PALM_RANGE * PALM_RANGE && this.hasLineOfSight(owner);
		if (palmPossible && (distanceSq < 9.0D || this.random.nextBoolean())) {
			startPalm(level);
		} else if (distanceSq > 9.0D) {
			dash(level, owner);
		} else if (palmPossible) {
			startPalm(level);
		}
	}

	/** A burst of dark qi carries the demon towards its owner. */
	private void dash(ServerLevel level, ServerPlayer owner) {
		Vec3 to = owner.position().subtract(this.position());
		Vec3 flat = new Vec3(to.x, 0.0D, to.z);
		double length = flat.length();
		if (length < 0.1D) {
			return;
		}
		Vec3 dir = flat.scale(1.0D / length);
		double power = Math.min(1.6D, 0.6D + length * 0.12D);
		this.setDeltaMovement(dir.x * power, 0.32D, dir.z * power);
		this.needsSync = true;
		Vec3 from = this.position().add(0.0D, 1.0D, 0.0D);
		Vec3 end = from.add(dir.scale(Math.min(length, 6.0D)));
		Fx.send(level, from, end, FxType.HEART_DEMON_APPEAR, TrialFxIds.DASH, this.getId(), 0.0F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 1.0F, 0.6F);
	}

	/** The mirrored palm gathers for 0.8 s (raised arm, motes), so the owner can step back out of its range. */
	private void startPalm(ServerLevel level) {
		palmCharge = PALM_CHARGE_TICKS;
		this.entityData.set(DATA_PALM_CHARGE, PALM_CHARGE_TICKS);
		Fx.send(level, this.position().add(0.0D, 1.3D, 0.0D), FxType.HEART_DEMON_APPEAR, TrialFxIds.PALM_CHARGE, this.getId(), 0.0F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.HOSTILE, 1.0F, 0.6F);
	}

	private void cancelPalm() {
		if (palmCharge > 0 || isChargingPalm()) {
			palmCharge = 0;
			this.entityData.set(DATA_PALM_CHARGE, 0);
		}
	}

	/** The dark palm: 1.5x attack as heart demon damage, only on the owner and only within 6 blocks with a clear line. */
	private void releasePalm(ServerLevel level, ServerPlayer owner) {
		if (!owner.isAlive() || this.distanceToSqr(owner) > PALM_RANGE * PALM_RANGE || !this.hasLineOfSight(owner)) {
			return;
		}
		this.swingForAttack(InteractionHand.MAIN_HAND);
		Vec3 from = this.getEyePosition().subtract(0.0D, 0.3D, 0.0D);
		Vec3 to = owner.getBoundingBox().getCenter();
		Fx.send(level, from, to, FxType.HEART_DEMON_APPEAR, TrialFxIds.PALM, this.getId(), 0.0F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 1.0F, 0.5F);
		level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 0.8F, 1.3F);
		float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * PALM_FACTOR;
		owner.hurtServer(level, this.createDamageSource(), damage);
	}

	/**
	 * Steps out of the shadows close to the owner (2-4 blocks away on a safe spot, or right on them): used when the
	 * demon falls behind, sinks into lava or drops below the world. Read-only spot search.
	 */
	public void blinkTo(ServerPlayer owner) {
		if (!(this.level() instanceof ServerLevel level) || owner.level() != level) {
			return;
		}
		Vec3 old = this.position();
		Vec3 spot = null;
		for (int attempt = 0; attempt < 10 && spot == null; attempt++) {
			double angle = this.random.nextDouble() * Math.PI * 2.0D;
			double distance = 2.0D + this.random.nextDouble() * 2.0D;
			Vec3 candidate = owner.position().add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
			if (level.isLoaded(BlockPos.containing(candidate))) {
				spot = Targeting.safeSpot(level, this, candidate);
			}
		}
		if (spot == null) {
			spot = owner.position();
		}
		this.teleportTo(spot.x, spot.y, spot.z);
		this.setDeltaMovement(Vec3.ZERO);
		this.resetFallDistance();
		this.needsSync = true;
		Fx.send(level, old.add(0.0D, 1.0D, 0.0D), spot.add(0.0D, 1.0D, 0.0D), FxType.HEART_DEMON_APPEAR, TrialFxIds.BLINK, this.getId(), 0.0F);
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 0.9F, 0.6F);
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		// Fights only its owner: every other target is refused.
		super.setTarget(target != null && isOwner(target) ? target : null);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		return isOwner(target) && super.doHurtTarget(level, target);
	}

	/** Melee (it holds no weapon, so {@code Mob.doHurtTarget} uses this) and the palm: never scaled by difficulty. */
	@Override
	public DamageSource createDamageSource() {
		return new DamageSource(this.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE), this);
	}

	/** Only its owner, also on Peaceful (vanilla refuses every player as a target there). */
	@Override
	public boolean canAttack(LivingEntity target) {
		return isOwner(target) && target.canBeSeenAsEnemy();
	}

	/** Other mobs (golems, pets, beasts) never pick it as an enemy; it is a matter of one cultivator's heart. */
	@Override
	public boolean canBeSeenAsEnemy() {
		return false;
	}

	// ================================================================== immunity

	/** Only its owner can hurt it (their hits, projectiles and techniques all carry them as the attacker); /kill works. */
	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		if (source.is(DamageTypes.GENERIC_KILL)) {
			return false;
		}
		if (!isOwner(source.getEntity())) {
			return true;
		}
		return super.isInvulnerableTo(level, source);
	}

	@Override
	public void die(DamageSource source) {
		if (!this.isRemoved() && !this.dead) {
			killedByOwner = isOwner(source.getEntity());
		}
		super.die(source);
	}

	@Override
	public boolean ignoreExplosion(Explosion explosion) {
		return true;
	}

	@Override
	protected void onBelowWorld() {
		ServerPlayer owner = getOwnerPlayer();
		if (owner != null && owner.level() == this.level()) {
			blinkTo(owner);
		} else {
			this.discard();
		}
	}

	// ================================================================== voice: the owner's, only deeper

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PLAYER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PLAYER_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.55F + this.random.nextFloat() * 0.1F;
	}

	// ================================================================== nothing left behind, nothing changed

	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return false;
	}

	@Override
	public boolean shouldDropExperience() {
		return false;
	}

	@Override
	protected void dropEquipment(ServerLevel level) {
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
	}

	@Override
	public boolean canPickUpLoot() {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	/** Its lifetime belongs to the trial (and Peaceful does not erase it mid-trial). */
	@Override
	public void checkDespawn() {
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public boolean canUsePortal(boolean ignorePassenger) {
		return false;
	}

	@Override
	public boolean startRiding(Entity entity, boolean force, boolean sendEventAndTriggers) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	/** Pressure plates and tripwires never notice it. */
	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	/** Always treads softly: no lit redstone ore, no trampled eggs, no sculk vibrations from its steps. */
	@Override
	public boolean isSteppingCarefully() {
		return true;
	}

	/**
	 * Blocks never act on it: no {@code stepOn} (a sculk sensor's vibration, which ignores careful steps) and no
	 * {@code entityInside} (a big dripleaf tilting, berry bushes, portals). Together with the overrides above no block
	 * state ever changes because of it; it is immune to block harm anyway.
	 */
	@Override
	protected boolean isAffectedByBlocks() {
		return false;
	}

	// ================================================================== goals

	/** Keeps the demon's eyes on its owner whenever it is not busy attacking. */
	private static final class LookAtOwnerGoal extends Goal {
		private final HeartDemonEntity demon;

		LookAtOwnerGoal(HeartDemonEntity demon) {
			this.demon = demon;
			this.setFlags(EnumSet.of(Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return demon.getOwnerPlayer() != null;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			ServerPlayer owner = demon.getOwnerPlayer();
			if (owner != null && owner.level() == demon.level()) {
				demon.getLookControl().setLookAt(owner, 30.0F, 30.0F);
			}
		}
	}
}
