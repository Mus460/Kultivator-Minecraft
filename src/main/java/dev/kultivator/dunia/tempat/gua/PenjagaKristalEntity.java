package dev.kultivator.dunia.tempat.gua;

import dev.kultivator.technique.spec.TechniquePower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * {@code kultivator:penjaga_kristal} (DESIGN 38.C.4 / 38.O.2): a 2.4-block golem of violet crystal that guards a
 * spirit-vein cave. Two of them sit dormant in the geode floor (no AI, no harm) until the run wakes them on the first
 * visit of a world day ({@link #bangun()}): amethyst chimes, then a fight at Golden Core strength (realm-scaled foe,
 * 38.0 rule 14). Besides plain melee it has one telegraphed skill, the Hentakan Kristal: it raises both fists for
 * {@link #SLAM_WIND} ticks (amethyst resonance as warning), then strikes the ground, hurting and throwing back what
 * stands within {@link #SLAM_RADIUS} blocks (an entity effect and particles; never a block). Never saved, no loot.
 */
public class PenjagaKristalEntity extends Monster {

	public static final int SLAM_WIND = 24;
	public static final int SLAM_COOLDOWN = 120;
	public static final double SLAM_RADIUS = 4.5D;

	private static final EntityDataAccessor<Long> DATA_BANGUN = SynchedEntityData.defineId(PenjagaKristalEntity.class, EntityDataSerializers.LONG);
	/** The tick the slam's wind-up began (0 none); the renderer may raise the arms from it. */
	private static final EntityDataAccessor<Long> DATA_HENTAK = SynchedEntityData.defineId(PenjagaKristalEntity.class, EntityDataSerializers.LONG);

	private boolean owned;
	private int age;
	private long nextSlam;

	public PenjagaKristalEntity(EntityType<? extends PenjagaKristalEntity> type, Level level) {
		super(type, level);
		this.xpReward = 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 80.0D).add(Attributes.ATTACK_DAMAGE, 9.0D).add(Attributes.ARMOR, 10.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.18D).add(Attributes.KNOCKBACK_RESISTANCE, 0.9D).add(Attributes.FOLLOW_RANGE, 24.0D);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_BANGUN, 0L);
		builder.define(DATA_HENTAK, 0L);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	/** Golden Core strength, dormant; marked as a realm-scaled foe. */
	public void setup() {
		base(Attributes.MAX_HEALTH, 220.0D);
		base(Attributes.ATTACK_DAMAGE, 16.0D);
		base(Attributes.ARMOR, 14.0D);
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

	public boolean dormant() {
		return this.entityData.get(DATA_BANGUN) == 0L;
	}

	/** Wakes the guardian (amethyst chimes, a shiver of shards); it fights after one second. Idempotent. */
	public void bangun() {
		if (!dormant() || !(this.level() instanceof ServerLevel level)) {
			return;
		}
		this.entityData.set(DATA_BANGUN, Math.max(1L, level.getGameTime()));
		level.playSound(null, getX(), getY() + 1.2D, getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.6F, 0.6F);
		level.playSound(null, getX(), getY() + 1.2D, getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.2F, 0.5F);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()),
				getX(), getY() + 1.2D, getZ(), 30, 0.6D, 1.0D, 0.6D, 0.1D);
		nextSlam = level.getGameTime() + 60L;
	}

	/** The wind-up tick of the current slam (0 none), for the renderer. */
	public long hentakSejak() {
		return this.entityData.get(DATA_HENTAK);
	}

	/** 0..1 how far the fists are raised for the slam at {@code partialTick}. */
	public float hentakan(float partialTick) {
		long t0 = hentakSejak();
		if (t0 == 0L) {
			return 0.0F;
		}
		return Mth.clamp((this.level().getGameTime() - t0 + partialTick) / SLAM_WIND, 0.0F, 1.0F);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		age++;
		if (!owned && age > 40) {
			this.discard();
			return;
		}
		long now = level.getGameTime();
		long woke = this.entityData.get(DATA_BANGUN);
		if (woke != 0L && this.isNoAi() && now - woke >= 20L) {
			this.setNoAi(false);
		}
		if (this.isNoAi() || !this.isAlive()) {
			return;
		}
		long wind = hentakSejak();
		LivingEntity target = this.getTarget();
		if (wind == 0L) {
			if (target != null && now >= nextSlam && this.distanceToSqr(target) <= 9.0D) {
				this.entityData.set(DATA_HENTAK, now);
				this.getNavigation().stop();
				level.playSound(null, getX(), getY() + 1.5D, getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.4F, 1.3F);
			}
			return;
		}
		this.getNavigation().stop();
		if (now - wind < SLAM_WIND) {
			if ((now - wind) % 6L == 0L) {
				level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 2.4D, getZ(), 3, 0.5D, 0.2D, 0.5D, 0.01D);
			}
			return;
		}
		slam(level);
		this.entityData.set(DATA_HENTAK, 0L);
		nextSlam = now + SLAM_COOLDOWN;
	}

	/** The Hentakan Kristal: damage and a throw-back around the golem; particles and sound only, never a block. */
	private void slam(ServerLevel level) {
		Vec3 at = this.position();
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 1.6F, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 1.2F, 0.6F);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()),
				at.x, at.y + 0.1D, at.z, 60, SLAM_RADIUS * 0.5D, 0.1D, SLAM_RADIUS * 0.5D, 0.15D);
		level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y + 0.5D, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
		float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.25F;
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(SLAM_RADIUS, 2.0D, SLAM_RADIUS),
				e -> e != this && e.isAlive() && !(e instanceof PenjagaKristalEntity))) {
			Vec3 away = victim.position().subtract(at);
			double length = Math.max(0.3D, away.horizontalDistance());
			if (length > SLAM_RADIUS) {
				continue;
			}
			victim.hurtServer(level, this.damageSources().mobAttack(this), damage * (float) (1.0D - 0.5D * length / SLAM_RADIUS));
			victim.push(away.x / length * 0.9D, 0.45D, away.z / length * 0.9D);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (this.isNoAi() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
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
		return dormant() ? null : SoundEvents.AMETHYST_BLOCK_CHIME;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.AMETHYST_BLOCK_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.AMETHYST_BLOCK_BREAK;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.AMETHYST_BLOCK_STEP, 0.8F, 0.6F);
	}
}
