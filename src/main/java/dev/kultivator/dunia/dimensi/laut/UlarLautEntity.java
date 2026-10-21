package dev.kultivator.dunia.dimensi.laut;

import dev.kultivator.dunia.dimensi.Dimensi;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.NoGrief;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * {@code kultivator:ular_laut}, Ular Laut Raksasa (DESIGN 34.5.14): a giant sea serpent. Only its head has a hit box;
 * the body is drawn on the client by the spine's {@code SegmentChain}. It swims toward swimmers ({@link SeaSwim}),
 * bites, coils (Slowness) and lashes the water (a push up and away). Its tier (0-4) follows the ring of 1 000 b from the
 * sea's origin (Golden Core to Immortal Ascension) and sets its length on the client. A serpent spawned for the Dragon
 * King's second quest carries the stolen pearl ({@link #hasPearl()}).
 */
public class UlarLautEntity extends Monster {

	public static final String TAG = "kultivator.ular_laut";
	private static final EntityDataAccessor<Integer> DATA_TIER = SynchedEntityData.defineId(UlarLautEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_PEARL = SynchedEntityData.defineId(UlarLautEntity.class, EntityDataSerializers.BOOLEAN);

	private final double[] heading = new double[3];
	private int biteCooldown;
	private int lashCooldown;
	private int lonely;

	public UlarLautEntity(EntityType<? extends UlarLautEntity> type, Level level) {
		super(type, level);
		this.xpReward = 20;
		this.setCanPickUpLoot(false);
		this.addTag(NoGrief.TAG);
		this.addTag(TAG);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 120.0D)
				.add(Attributes.ATTACK_DAMAGE, 10.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.3D)
				.add(Attributes.FOLLOW_RANGE, 48.0D)
				.add(Attributes.ARMOR, 8.0D)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.9D);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_TIER, 0);
		builder.define(DATA_PEARL, false);
	}

	@Override
	protected void registerGoals() {
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
				(target, level) -> target instanceof Player player && !player.isCreative() && !player.isSpectator() && player.isInWater()));
	}

	public void setup(int tier, boolean pearl) {
		int t = Mth.clamp(tier, 0, 4);
		this.entityData.set(DATA_TIER, t);
		this.entityData.set(DATA_PEARL, pearl);
		var health = this.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue((140.0D + 110.0D * t) * (pearl ? 1.8D : 1.0D));
		}
		var attack = this.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attack != null) {
			attack.setBaseValue(10.0D + 6.0D * t);
		}
		var armor = this.getAttribute(Attributes.ARMOR);
		if (armor != null) {
			armor.setBaseValue(8.0D + 2.5D * t);
		}
		this.setHealth(this.getMaxHealth());
		this.xpReward = 20 + 15 * t;
		this.addTag(TechniquePower.TAG_BOSS);
	}

	public int tier() {
		return this.entityData.get(DATA_TIER);
	}

	public boolean hasPearl() {
		return this.entityData.get(DATA_PEARL);
	}

	@Override
	protected Component getTypeName() {
		return Component.translatable(hasPearl() ? "entity.kultivator.ular_laut.pearl" : "entity.kultivator.ular_laut");
	}

	@Override
	public void setCustomName(@Nullable Component name) {
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		LivingEntity target = this.getTarget();
		SeaSwim.steer(this, target, 0.36D + 0.03D * tier(), heading);
		if (biteCooldown > 0) {
			biteCooldown--;
		}
		if (lashCooldown > 0) {
			lashCooldown--;
		}
		if (target != null && target.isAlive()) {
			double d = this.distanceToSqr(target);
			if (d < 9.0D && biteCooldown <= 0) {
				biteCooldown = 30;
				this.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
				if (this.doHurtTarget(level, target)) {
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), this);
				}
			} else if (d < 64.0D && lashCooldown <= 0) {
				lashCooldown = 140;
				// the tail lashes the sea: a spout that throws the swimmer up and away (no block is touched)
				Vec3 push = target.position().subtract(this.position()).normalize().scale(0.9D).add(0.0D, 0.8D, 0.0D);
				target.setDeltaMovement(target.getDeltaMovement().add(push));
				target.needsSync = true;
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.HOSTILE,
						1.2F, 0.6F);
				Dimensi.spout(level, target.position());
			}
		}
		if (this.tickCount % 40 == 0) {
			if (level.getNearestPlayer(this, 96.0D) == null) {
				if (++lonely >= 15) {
					this.discard();
				}
			} else {
				lonely = 0;
			}
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level && source.getEntity() instanceof ServerPlayer killer) {
			Dimensi.monsterSlain(level, killer, this, hasPearl() ? "ular_mutiara" : "ular");
		}
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean isPushedByFluid() {
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
	protected void onBelowWorld() {
		this.discard();
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ELDER_GUARDIAN_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ELDER_GUARDIAN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ELDER_GUARDIAN_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.5F + this.random.nextFloat() * 0.1F;
	}
}
