package dev.kultivator.purba;

import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.NoGrief;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Utusan Murka Langit and the Panglima Langit (DESIGN 26.5.4): a hostile {@link Monster} drawn with the disciple's player
 * model in a white-gold robe (client {@code EnvoyRenderer}). Built like the rogue cultivator: never saved, {@link NoGrief#TAG},
 * no loot pickup, no doors, no custom name (the title comes from {@link #getTypeName()}). Tag {@code kultivator.abadi_foe};
 * the Panglima also {@code kultivator.boss}, so the 7b kill-cam sends {@code BOSS_HEAVEN_GENERAL} by itself. Only the
 * challenged player's damage counts against the Panglima, at most 4% of its health per second.
 */
public class HeavenEnvoyEntity extends Monster {

	private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(HeavenEnvoyEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_SKIN = SynchedEntityData.defineId(HeavenEnvoyEntity.class, EntityDataSerializers.INT);

	public static final int ENVOY = 0;
	public static final int PANGLIMA = 1;
	public static final double BASE_HEALTH = 24.0D;
	public static final double ENVOY_TIER = 3.0D;
	public static final double PANGLIMA_TIER = 12.0D;
	public static final double HEALTH_PER_REALM = 0.35D;
	/** The Panglima's intake per 20-tick window (DESIGN 26.2.2). */
	public static final double PANGLIMA_INTAKE = 0.04D;

	/** The player the envoy hunts first (server only). */
	@Nullable UUID focus;
	/** The Panglima's challenged player: only their damage counts (server only). */
	@Nullable UUID challenger;
	private long windowStart;
	private float windowTaken;
	private boolean despawned;

	public HeavenEnvoyEntity(EntityType<? extends HeavenEnvoyEntity> type, Level level) {
		super(type, level);
		this.xpReward = 0;
		this.setCanPickUpLoot(false);
		this.getNavigation().setCanOpenDoors(false);
		this.addTag(NoGrief.TAG);
		this.addTag(TechniquePower.TAG_ABADI_FOE);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 72.0D)
				.add(Attributes.ATTACK_DAMAGE, 7.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.32D)
				.add(Attributes.FOLLOW_RANGE, 48.0D)
				.add(Attributes.ARMOR, 6.0D)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.4D)
				.add(Attributes.STEP_HEIGHT, 1.0D);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_KIND, ENVOY);
		builder.define(DATA_SKIN, 0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, true));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, HeavenEnvoyEntity.class));
		this.targetSelector.addGoal(2, new EnvoyAi.ChooseTargetGoal(this));
	}

	/**
	 * Health {@code 24 × {3, 12}[kind] × (1 + 0.35 R_eff) × (1 + 0.5k)} (k only for envoys of a calamity), melee
	 * {@code 4 + R_eff + 3} (Panglima {@code 6 + 1.5 R_eff}), armour {@code R_eff} (DESIGN 26.5.4).
	 */
	public void setup(int kind, int realmEff, int k, @Nullable UUID focus) {
		int type = Mth.clamp(kind, ENVOY, PANGLIMA);
		int r = Math.max(0, realmEff);
		this.entityData.set(DATA_KIND, type);
		this.entityData.set(DATA_SKIN, this.random.nextInt(9));
		this.focus = focus;
		double tier = type == PANGLIMA ? PANGLIMA_TIER : ENVOY_TIER;
		double calamity = type == ENVOY ? 1.0D + 0.5D * Math.max(0, k) : 1.0D;
		setBase(Attributes.MAX_HEALTH, BASE_HEALTH * tier * (1.0D + HEALTH_PER_REALM * r) * calamity);
		setBase(Attributes.ATTACK_DAMAGE, type == PANGLIMA ? 6.0D + 1.5D * r : 4.0D + r + 3.0D);
		setBase(Attributes.ARMOR, Math.min(30.0D, r));
		setBase(Attributes.KNOCKBACK_RESISTANCE, type == PANGLIMA ? 0.9D : 0.4D);
		setBase(Attributes.MOVEMENT_SPEED, type == PANGLIMA ? 0.34D : 0.32D);
		this.setHealth(this.getMaxHealth());
		this.addTag(NoGrief.TAG);
		this.addTag(TechniquePower.TAG_ABADI_FOE);
		if (type == PANGLIMA) {
			this.addTag(TechniquePower.TAG_BOSS);
			this.challenger = focus;
		}
	}

	private void setBase(Holder<Attribute> attribute, double value) {
		AttributeInstance instance = this.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	public int kind() {
		return Mth.clamp(this.entityData.get(DATA_KIND), ENVOY, PANGLIMA);
	}

	public boolean isPanglima() {
		return kind() == PANGLIMA;
	}

	public int skin() {
		return Math.floorMod(this.entityData.get(DATA_SKIN), 9);
	}

	boolean despawned() {
		return despawned;
	}

	/** Leaves in a column of light (not a kill: its wave counts as not cleared). */
	public void vanish() {
		if (this.isRemoved()) {
			return;
		}
		this.despawned = true;
		if (this.level() instanceof ServerLevel level) {
			EnvoyAi.vanishFx(level, this);
		}
		this.discard();
	}

	// ================================================================== damage

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (!isPanglima() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, damage);
		}
		// Duel Kehendak Langit: only the challenged player's damage counts (disciples, mates and formations deal 0).
		Entity attacker = source.getEntity();
		if (challenger == null || attacker == null || !challenger.equals(attacker.getUUID()) || !(damage > 0.0F)) {
			return false;
		}
		long now = level.getGameTime();
		if (now - windowStart >= 20L) {
			windowStart = now;
			windowTaken = 0.0F;
		}
		float cap = (float) (this.getMaxHealth() * PANGLIMA_INTAKE) - windowTaken;
		if (cap <= 0.0F) {
			return false;
		}
		float before = this.getHealth();
		boolean hurt = super.hurtServer(level, source, Math.min(damage, cap));
		windowTaken += Math.max(0.0F, before - this.getHealth());
		return hurt;
	}

	// ================================================================== name (never a custom name)

	@Override
	protected Component getTypeName() {
		return Component.translatable(isPanglima() ? "kultivator.purba.panglima.name" : "kultivator.purba.envoy.name");
	}

	/** The coloured title on its name plate. */
	public Component plate() {
		return Component.empty().append(getTypeName()).withColor(isPanglima() ? 0xFFE08A : 0xF4EED8).withStyle(ChatFormatting.BOLD);
	}

	@Override
	public void setCustomName(@Nullable Component name) {
	}

	// ================================================================== targets

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		super.setTarget(target != null && !PurbaHits.mayHit(this, target) ? null : target);
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return PurbaHits.mayHit(this, target) && super.canAttack(target);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		return target instanceof LivingEntity living && PurbaHits.mayHit(this, living) && super.doHurtTarget(level, target);
	}

	// ================================================================== nothing left behind, nothing changed

	@Override
	protected void onBelowWorld() {
		vanish();
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
	public boolean canUsePortal(boolean ignorePassenger) {
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
		return SoundEvents.PLAYER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.BEACON_DEACTIVATE;
	}

	@Override
	public float getVoicePitch() {
		return isPanglima() ? 0.7F : 1.1F;
	}
}
