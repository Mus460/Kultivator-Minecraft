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
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * {@code kultivator:kepiting_batu}, Kepiting Batu Raksasa (DESIGN 34.5.14): a giant rock crab of the reefs. It walks the
 * sea floor and the reef islands (it sinks: no swimming), wears a stone shell (high armour, no knockback), pinches with
 * its claws (Slowness) and, below half health, draws its legs in for 3 s (Resistance). Tier 0-4 as the serpent's.
 */
public class KepitingBatuEntity extends Monster {

	public static final String TAG = "kultivator.kepiting_batu";
	private static final EntityDataAccessor<Integer> DATA_TIER = SynchedEntityData.defineId(KepitingBatuEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_SHELL = SynchedEntityData.defineId(KepitingBatuEntity.class, EntityDataSerializers.BOOLEAN);

	private int shellTicks;
	private boolean shellUsed;
	private int lonely;

	public KepitingBatuEntity(EntityType<? extends KepitingBatuEntity> type, Level level) {
		super(type, level);
		this.xpReward = 12;
		this.setCanPickUpLoot(false);
		this.addTag(NoGrief.TAG);
		this.addTag(TAG);
		// realm-scaled Dunia foe: the trial-mob rule of 6c (audit M4 T5)
		TechniquePower.markRealmFoe(this);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 90.0D)
				.add(Attributes.ATTACK_DAMAGE, 9.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.24D)
				.add(Attributes.FOLLOW_RANGE, 24.0D)
				.add(Attributes.ARMOR, 14.0D)
				.add(Attributes.ARMOR_TOUGHNESS, 4.0D)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
				.add(Attributes.STEP_HEIGHT, 1.0D);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_TIER, 0);
		builder.define(DATA_SHELL, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25D, true) {
			@Override
			public boolean canUse() {
				return !inShell() && super.canUse();
			}
		});
		this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.7D));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
				(target, level) -> target instanceof Player player && !player.isCreative() && !player.isSpectator()));
	}

	public void setup(int tier) {
		int t = Mth.clamp(tier, 0, 4);
		this.entityData.set(DATA_TIER, t);
		var health = this.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(90.0D + 70.0D * t);
		}
		var attack = this.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attack != null) {
			attack.setBaseValue(9.0D + 5.0D * t);
		}
		var armor = this.getAttribute(Attributes.ARMOR);
		if (armor != null) {
			armor.setBaseValue(14.0D + 1.5D * t);
		}
		this.setHealth(this.getMaxHealth());
		this.xpReward = 12 + 8 * t;
	}

	public int tier() {
		return this.entityData.get(DATA_TIER);
	}

	/** True while it hides in its shell (the client draws the legs drawn in). */
	public boolean inShell() {
		return this.entityData.get(DATA_SHELL);
	}

	@Override
	protected Component getTypeName() {
		return Component.translatable("entity.kultivator.kepiting_batu");
	}

	@Override
	public void setCustomName(@Nullable Component name) {
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (shellTicks > 0 && --shellTicks == 0) {
			this.entityData.set(DATA_SHELL, false);
		}
		if (!shellUsed && this.getHealth() < this.getMaxHealth() * 0.5F) {
			shellUsed = true;
			shellTicks = 60;
			this.entityData.set(DATA_SHELL, true);
			this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 2), this);
			this.getNavigation().stop();
		}
		if (this.tickCount % 40 == 0) {
			if (level.getNearestPlayer(this, 80.0D) == null) {
				if (++lonely >= 15) {
					this.discard();
				}
			} else {
				lonely = 0;
			}
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && target instanceof LivingEntity living) {
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 1), this);
		}
		return hit;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level && source.getEntity() instanceof ServerPlayer killer) {
			Dimensi.monsterSlain(level, killer, this, "kepiting", tier());
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
		return SoundEvents.ARMADILLO_HURT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 1.3F + this.random.nextFloat() * 0.2F;
	}
}
