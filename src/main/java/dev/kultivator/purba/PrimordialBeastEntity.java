package dev.kultivator.purba;

import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.NoGrief;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * A primordial beast (DESIGN 26.4.2): Kun-Peng, Zhulong or Taotie. A flying {@link Monster} with no model (client
 * {@code PrimordialRenderer} draws a point-cloud silhouette), moved only by its fight script ({@link PrimordialEvents} /
 * {@link PrimordialKinds}): no goals, no gravity, no physics, no block collision effects, no projectiles, no explosion.
 * Never saved (the type is {@code noSave} and {@link Purba#TAG} is refused in {@code ALLOW_LOAD}), never despawns by itself,
 * never gets a custom name. Tags {@code kultivator.boss}, {@code kultivator.abadi_foe}, {@code kultivator.purba}, so the 7b
 * kill-cam sends {@code BOSS_PRIMORDIAL} by itself. Its damage intake is clamped per second by the fight (26.2.2).
 * Synced: kind (0-2), phase (0-2) and flags (Celah, hidden, mouth open, transition).
 */
public class PrimordialBeastEntity extends Monster {

	private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(PrimordialBeastEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(PrimordialBeastEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_FLAGS = SynchedEntityData.defineId(PrimordialBeastEntity.class, EntityDataSerializers.INT);

	public static final int KUNPENG = 0;
	public static final int ZHULONG = 1;
	public static final int TAOTIE = 2;

	/** The Celah (opening): the intake window is 6% instead of 1.5%. */
	public static final int FLAG_CELAH = 1;
	/** Zhulong with its eyes closed: only the candle glow is drawn. */
	public static final int FLAG_HIDDEN = 2;
	/** Taotie's mouth is open (phase 3: technique damage heals it). */
	public static final int FLAG_MOUTH = 4;
	/** The 3 s invulnerable phase transition. */
	public static final int FLAG_TRANSITION = 8;
	/** A big skill is being telegraphed (the silhouette brightens). */
	public static final int FLAG_GLOW = 16;

	/** The fight that moves it (server only; a beast without one leaves at once). */
	@Nullable PrimordialEvents.Fight fight;

	public PrimordialBeastEntity(EntityType<? extends PrimordialBeastEntity> type, Level level) {
		super(type, level);
		this.xpReward = 0;
		this.setNoGravity(true);
		this.setCanPickUpLoot(false);
		this.addTag(NoGrief.TAG);
		this.addTag(TechniquePower.TAG_BOSS);
		this.addTag(TechniquePower.TAG_ABADI_FOE);
		this.addTag(Purba.TAG);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 960.0D)
				.add(Attributes.ATTACK_DAMAGE, 10.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.3D)
				.add(Attributes.FLYING_SPEED, 0.6D)
				.add(Attributes.FOLLOW_RANGE, 64.0D)
				.add(Attributes.ARMOR, 10.0D)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_KIND, KUNPENG);
		builder.define(DATA_PHASE, 0);
		builder.define(DATA_FLAGS, 0);
	}

	@Override
	protected void registerGoals() {
		// Moved by its fight script only.
	}

	// ================================================================== synced values

	public int kind() {
		return Mth.clamp(this.entityData.get(DATA_KIND), 0, 2);
	}

	void setKind(int kind) {
		this.entityData.set(DATA_KIND, Mth.clamp(kind, 0, 2));
	}

	public int phase() {
		return Mth.clamp(this.entityData.get(DATA_PHASE), 0, 2);
	}

	void setPhase(int phase) {
		this.entityData.set(DATA_PHASE, Mth.clamp(phase, 0, 2));
	}

	public int flags() {
		return this.entityData.get(DATA_FLAGS);
	}

	public boolean hasFlag(int flag) {
		return (flags() & flag) != 0;
	}

	void setFlag(int flag, boolean on) {
		int now = flags();
		int next = on ? now | flag : now & ~flag;
		if (next != now) {
			this.entityData.set(DATA_FLAGS, next);
		}
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_KIND.equals(accessor) || DATA_PHASE.equals(accessor)) {
			this.refreshDimensions();
		}
	}

	/** Kun 8×3, Peng 6×2, Zhulong head 4×4 (the body is visual only), Taotie 6×6 (DESIGN 26.4.2). */
	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		return switch (kind()) {
			case KUNPENG -> phase() == 0 ? EntityDimensions.scalable(8.0F, 3.0F) : EntityDimensions.scalable(6.0F, 2.0F);
			case ZHULONG -> EntityDimensions.scalable(4.0F, 4.0F);
			default -> EntityDimensions.scalable(6.0F, 6.0F);
		};
	}

	// ================================================================== damage (the fight clamps the intake)

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, damage);
		}
		PrimordialEvents.Fight current = fight;
		if (current == null || !(damage > 0.0F) || !Float.isFinite(damage)) {
			return false;
		}
		return current.hurt(this, level, source, damage);
	}

	/** The plain vanilla hurt, called by the fight with the clamped amount. */
	boolean hurtRaw(ServerLevel level, DamageSource source, float amount) {
		return super.hurtServer(level, source, amount);
	}

	// ================================================================== behaviour

	@Override
	public void tick() {
		this.noPhysics = true;
		super.tick();
		this.noPhysics = false;
		this.setNoGravity(true);
		this.setDeltaMovement(this.getDeltaMovement().scale(0.5D));
		if (!this.level().isClientSide() && this.tickCount > 40 && fight == null && !this.isRemoved()) {
			// A stray beast (its fight ended or was never registered) leaves.
			this.discard();
		}
	}

	@Override
	protected Component getTypeName() {
		return PurbaItems.kindName(kind());
	}

	@Override
	public void setCustomName(@Nullable Component name) {
		// Never: formations skip custom-named mobs.
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	protected void pushEntities() {
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
	}

	@Override
	public boolean canFreeze() {
		return false;
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	public boolean canUsePortal(boolean ignorePassenger) {
		return false;
	}

	@Override
	protected void onBelowWorld() {
		PrimordialEvents.Fight current = fight;
		if (current != null) {
			current.depart(false);
		} else {
			this.discard();
		}
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return false;
	}

	@Override
	protected boolean canRide(Entity vehicle) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	@Override
	public boolean canPickUpLoot() {
		return false;
	}

	@Override
	public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
		return false;
	}

	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return false;
	}

	@Override
	protected void dropEquipment(ServerLevel level) {
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return switch (kind()) {
			case KUNPENG -> phase() == 0 ? SoundEvents.ELDER_GUARDIAN_AMBIENT : SoundEvents.PHANTOM_AMBIENT;
			case ZHULONG -> SoundEvents.ENDER_DRAGON_AMBIENT;
			default -> SoundEvents.RAVAGER_AMBIENT;
		};
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return kind() == ZHULONG ? SoundEvents.ENDER_DRAGON_HURT : SoundEvents.RAVAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return kind() == ZHULONG ? SoundEvents.ENDER_DRAGON_DEATH : SoundEvents.RAVAGER_DEATH;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 200;
	}

	@Override
	protected float getSoundVolume() {
		return 3.0F;
	}
}
