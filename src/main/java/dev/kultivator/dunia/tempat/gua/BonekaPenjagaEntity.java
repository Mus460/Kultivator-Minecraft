package dev.kultivator.dunia.tempat.gua;

import dev.kultivator.technique.spec.TechniquePower;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * {@code kultivator:boneka_penjaga} (DESIGN 38.C.4 / 38.O.2): a tomb's guardian puppet, old wood and bronze with a
 * lacquer mask and a wooden sword. It sits cross-legged on its plinth in the Strength trial room until the run wakes it
 * ({@link #bangkit()}); while seated it neither moves nor takes harm. Risen it fights with plain melee goals, scaled to
 * the tomb tier ({@link #setup(int)}: tier 1 Foundation, 2 Nascent Soul, 3 Dao Union bands) and marked with
 * {@code TechniquePower.markRealmFoe} (38.0 rule 14). The renderer reads the rise from the synced tick
 * ({@code KaitModel.bonekaBangkit}, 0 seated, 1 standing over {@link #RISE_TICKS}). Never saved, no loot; it never
 * touches a block (vanilla melee only).
 */
public class BonekaPenjagaEntity extends Monster {

	public static final int RISE_TICKS = 30;
	/** Health, attack and armour per tomb tier 1-3. */
	private static final double[] HEALTH = {60.0D, 140.0D, 320.0D};
	private static final double[] ATTACK = {7.0D, 13.0D, 22.0D};
	private static final double[] ARMOR = {6.0D, 10.0D, 16.0D};

	private static final EntityDataAccessor<Long> DATA_BANGKIT = SynchedEntityData.defineId(BonekaPenjagaEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Integer> DATA_TIER = SynchedEntityData.defineId(BonekaPenjagaEntity.class, EntityDataSerializers.INT);

	private boolean owned;
	private int age;

	public BonekaPenjagaEntity(EntityType<? extends BonekaPenjagaEntity> type, Level level) {
		super(type, level);
		this.xpReward = 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 40.0D).add(Attributes.ATTACK_DAMAGE, 6.0D).add(Attributes.ARMOR, 4.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.24D).add(Attributes.FOLLOW_RANGE, 20.0D).add(Attributes.KNOCKBACK_RESISTANCE, 0.4D);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_BANGKIT, 0L);
		builder.define(DATA_TIER, 1);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1D, true));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	/** Scales the puppet to tomb tier 1-3 and marks it as a realm-scaled foe; it starts seated (no AI). */
	public void setup(int tier) {
		int t = Mth.clamp(tier, 1, 3);
		this.entityData.set(DATA_TIER, t);
		base(Attributes.MAX_HEALTH, HEALTH[t - 1]);
		base(Attributes.ATTACK_DAMAGE, ATTACK[t - 1]);
		base(Attributes.ARMOR, ARMOR[t - 1]);
		base(Attributes.MOVEMENT_SPEED, 0.24D + 0.02D * t);
		this.setHealth(this.getMaxHealth());
		this.setNoAi(true);
		this.setPersistenceRequired();
		TechniquePower.markRealmFoe(this);
		this.owned = true;
	}

	private void base(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
		AttributeInstance instance = this.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	public int tier() {
		return this.entityData.get(DATA_TIER);
	}

	public boolean seated() {
		return this.entityData.get(DATA_BANGKIT) == 0L;
	}

	/** Wakes the puppet: it rises over {@link #RISE_TICKS} (wood creaks), then fights. Idempotent. */
	public void bangkit() {
		if (!seated() || !(this.level() instanceof ServerLevel level)) {
			return;
		}
		this.entityData.set(DATA_BANGKIT, Math.max(1L, level.getGameTime()));
		level.playSound(null, getX(), getY() + 1.0D, getZ(), SoundEvents.WOOD_HIT, SoundSource.HOSTILE, 1.0F, 0.5F);
		level.playSound(null, getX(), getY() + 1.0D, getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 0.5F, 1.4F);
	}

	/** Sits down again with full health (the trial resets once the room is empty). */
	public void duduk() {
		this.entityData.set(DATA_BANGKIT, 0L);
		this.setTarget(null);
		this.setNoAi(true);
		this.getNavigation().stop();
		this.setHealth(this.getMaxHealth());
	}

	/** The rise reading 0 (seated) .. 1 (standing) at {@code partialTick}, for the renderer. */
	public float bangkitan(float partialTick) {
		long t0 = this.entityData.get(DATA_BANGKIT);
		if (t0 == 0L) {
			return 0.0F;
		}
		return Mth.clamp((this.level().getGameTime() - t0 + partialTick) / RISE_TICKS, 0.0F, 1.0F);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			return;
		}
		age++;
		if (!owned && age > 40) {
			this.discard();
			return;
		}
		long t0 = this.entityData.get(DATA_BANGKIT);
		if (t0 != 0L && this.isNoAi() && this.level().getGameTime() - t0 >= RISE_TICKS) {
			this.setNoAi(false);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if ((seated() || this.isNoAi()) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		return super.hurtServer(level, source, damage);
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WOOD_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ARMOR_STAND_BREAK;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.WOOD_STEP, 0.4F, 0.6F);
	}
}
