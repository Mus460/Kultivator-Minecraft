package dev.kultivator.purba;

import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.NoGrief;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A primordial relic (DESIGN 26.4.2, 26.4.5): Mata Angin (the three wind eyes orbiting Kun-Peng's whirlwind; breaking one
 * opens a 6 s Celah) or the Lilin (Zhulong's candle in the dark; hitting it opens 4 s). A floating {@link Monster} with no
 * goals, moved by its fight; never saved, {@code abadi_foe} and {@link Purba#TAG}, never {@code kultivator.boss}. Drawn by
 * client {@code RelicRenderer}. Touches no block.
 */
public class PrimordialRelicEntity extends Monster {

	private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(PrimordialRelicEntity.class, EntityDataSerializers.INT);

	public static final int MATA_ANGIN = 0;
	public static final int LILIN = 1;

	/** The fight that owns it (server only). */
	@Nullable PrimordialEvents.Fight fight;

	public PrimordialRelicEntity(EntityType<? extends PrimordialRelicEntity> type, Level level) {
		super(type, level);
		this.xpReward = 0;
		this.setNoGravity(true);
		this.setCanPickUpLoot(false);
		this.addTag(NoGrief.TAG);
		this.addTag(TechniquePower.TAG_ABADI_FOE);
		this.addTag(Purba.TAG);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 60.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.0D)
				.add(Attributes.ARMOR, 4.0D)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_KIND, MATA_ANGIN);
	}

	@Override
	protected void registerGoals() {
	}

	public int kind() {
		return Mth.clamp(this.entityData.get(DATA_KIND), MATA_ANGIN, LILIN);
	}

	void setKind(int kind) {
		this.entityData.set(DATA_KIND, Mth.clamp(kind, MATA_ANGIN, LILIN));
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, damage);
		}
		PrimordialEvents.Fight current = fight;
		if (current == null || !(damage > 0.0F) || !Float.isFinite(damage)) {
			return false;
		}
		return current.relicHurt(this, level, source, damage);
	}

	/** The plain vanilla hurt (Mata Angin takes damage normally). */
	boolean hurtRaw(ServerLevel level, DamageSource source, float amount) {
		return super.hurtServer(level, source, amount);
	}

	@Override
	public void tick() {
		this.noPhysics = true;
		super.tick();
		this.noPhysics = false;
		this.setNoGravity(true);
		this.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		if (!this.level().isClientSide() && this.tickCount > 40 && fight == null && !this.isRemoved()) {
			this.discard();
		}
	}

	@Override
	protected Component getTypeName() {
		return Component.translatable("kultivator.purba.relic." + (kind() == LILIN ? "lilin" : "mata_angin"));
	}

	@Override
	public void setCustomName(@Nullable Component name) {
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
		this.discard();
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
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.AMETHYST_BLOCK_CHIME;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.GLASS_BREAK;
	}
}
