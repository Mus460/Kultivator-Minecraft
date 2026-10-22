package dev.kultivator.dunia.dimensi.laut;

import dev.kultivator.dunia.dimensi.Dimensi;
import dev.kultivator.dunia.dimensi.bawah.ArwahEntity;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * {@code kultivator:yaksha} (DESIGN 34.5.13-14): the blue-skinned trident demons. Fengshen Yanyi ch. 12: the Dragon King
 * sends the yaksha Li Gen, whom Nezha kills; in Diyu yaksha patrol the courts.
 * <ul>
 * <li>{@link #BAWAH}: a court patrol; hunts hungry ghosts and anyone who strikes it, never a peaceful soul.</li>
 * <li>{@link #LAUT}: a sea yaksha; hunts swimmers, fights in the water; the rebel chief ({@link #isChief()}) is the
 * Dragon King's first quest.</li>
 * </ul>
 * Never saved ({@code noSave}, refused on load), never picks anything up, drops nothing but its quest result; the
 * trident is only drawn (no drop).
 */
public class YakshaEntity extends Monster {

	public static final int BAWAH = 0;
	public static final int LAUT = 1;
	public static final String TAG = "kultivator.yaksha";

	private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(YakshaEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_TIER = SynchedEntityData.defineId(YakshaEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_CHIEF = SynchedEntityData.defineId(YakshaEntity.class, EntityDataSerializers.BOOLEAN);

	private final double[] heading = new double[3];
	private int lonely;

	public YakshaEntity(EntityType<? extends YakshaEntity> type, Level level) {
		super(type, level);
		this.xpReward = 8;
		this.setCanPickUpLoot(false);
		this.getNavigation().setCanOpenDoors(false);
		this.addTag(NoGrief.TAG);
		this.addTag(TAG);
		// realm-scaled Dunia foe: the trial-mob rule of 6c (audit M4 T5)
		TechniquePower.markRealmFoe(this);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 60.0D)
				.add(Attributes.ATTACK_DAMAGE, 8.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.3D)
				.add(Attributes.FOLLOW_RANGE, 40.0D)
				.add(Attributes.ARMOR, 6.0D)
				.add(Attributes.STEP_HEIGHT, 1.0D);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VARIANT, LAUT);
		builder.define(DATA_TIER, 0);
		builder.define(DATA_CHIEF, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this) {
			@Override
			public boolean canUse() {
				return variant() == BAWAH && super.canUse();
			}
		});
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, true));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, YakshaEntity.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
				(target, level) -> variant() == LAUT && target instanceof Player player && !player.isCreative() && !player.isSpectator()));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, ArwahEntity.class, true,
				(target, level) -> variant() == BAWAH && target instanceof ArwahEntity ghost && ghost.role() == ArwahEntity.HANTU_LAPAR));
	}

	/** Variant, tier 0-4 (Golden Core to Immortal Ascension), chief flag; health and melee scale with the tier. */
	public void setup(int variant, int tier, boolean chief) {
		int t = Mth.clamp(tier, 0, 4);
		this.entityData.set(DATA_VARIANT, variant == BAWAH ? BAWAH : LAUT);
		this.entityData.set(DATA_TIER, t);
		this.entityData.set(DATA_CHIEF, chief);
		double health = (70.0D + 55.0D * t) * (chief ? 2.5D : 1.0D);
		var maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(health);
		}
		var attack = this.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attack != null) {
			attack.setBaseValue((8.0D + 4.5D * t) * (chief ? 1.4D : 1.0D));
		}
		var armor = this.getAttribute(Attributes.ARMOR);
		if (armor != null) {
			armor.setBaseValue(6.0D + 2.0D * t);
		}
		this.setHealth(this.getMaxHealth());
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
		this.xpReward = 8 + 6 * t;
		if (chief) {
			this.addTag(TechniquePower.TAG_BOSS);
		}
	}

	public int variant() {
		return this.entityData.get(DATA_VARIANT);
	}

	public int tier() {
		return this.entityData.get(DATA_TIER);
	}

	public boolean isChief() {
		return this.entityData.get(DATA_CHIEF);
	}

	@Override
	protected Component getTypeName() {
		String key = isChief() ? "chief" : variant() == BAWAH ? "bawah" : "laut";
		return Component.translatable("entity.kultivator.yaksha." + key);
	}

	@Override
	public void setCustomName(@Nullable Component name) {
		// never: the type name carries the title
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (variant() == LAUT) {
			SeaSwim.steer(this, this.getTarget(), 0.32D + 0.02D * tier(), heading);
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
		if (hit && variant() == LAUT && this.isInWater() && target instanceof Player) {
			// the trident's sweep drags the swimmer down a little
			target.setDeltaMovement(target.getDeltaMovement().add(new Vec3(0.0D, -0.25D, 0.0D)));
			target.needsSync = true;
		}
		return hit;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level && source.getEntity() instanceof ServerPlayer killer) {
			Dimensi.monsterSlain(level, killer, this, isChief() ? "yaksha_chief" : "yaksha", variant() == LAUT ? tier() : -1);
		}
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean isPushedByFluid() {
		return variant() == BAWAH;
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
		return SoundEvents.DROWNED_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.DROWNED_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.DROWNED_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.65F + this.random.nextFloat() * 0.1F;
	}
}
