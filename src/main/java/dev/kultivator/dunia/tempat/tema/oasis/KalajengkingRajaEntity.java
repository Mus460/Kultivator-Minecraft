package dev.kultivator.dunia.tempat.tema.oasis;

import dev.kultivator.dunia.tempat.tema.bersama.TemaAnimasi;
import dev.kultivator.technique.spec.TechniquePower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * {@code kultivator:kalajengking_raja} (DESIGN 38.T.4.6, 38.T.9.2): Raja Kalajengking, the boss of the
 * {@code reruntuhan_gurun} nest (4 blocks long, 2.2 tall): cephalothorax, four belly segments, a six-segment tail curled
 * over the back with a hooked sting, two great claws, eight three-jointed legs and six glowing eyes on black-red chitin
 * with orange glowing cracks.
 * <p>
 * Fighting (38TK-O): melee hits are the claw snap ({@link #KEADAAN_CAPIT}, 6 ticks); every 5 s with a target within 6
 * blocks the sting swings forward ({@link #KEADAAN_SENGAT}, 10 ticks; Poison II 6 s, poison particles); below 70 %
 * health, every 15 s, {@code gali}: it sinks into the sand for 30 ticks ({@link #KEADAAN_GALI}, drawing and particles
 * only: no block ever changes), stays hidden and immune for 4 s ({@link #KEADAAN_TERSEMBUNYI}) and surfaces next to its
 * target ({@link #KEADAAN_MUNCUL}, 20 ticks of sand burst that throws close players back). Band 1-4 from the distance
 * ring ({@link #setup}) with {@code TechniquePower.markRealmFoe}; the reward is once per player per ruin
 * ({@link Reruntuhan#bosMati}). Never saved.
 */
public class KalajengkingRajaEntity extends Monster implements TemaAnimasi {

	/** Black-red chitin (the nest king). */
	public static final int VARIAN_RAJA = 0;
	/** Pale sand chitin (Xie Duo's tamed king, quest 38.T.4.7). */
	public static final int VARIAN_PASIR = 1;

	/** Idle: tail sways, claws click; walking uses the leg wave. */
	public static final int KEADAAN_DIAM = 0;
	/** Claw snap, 6 ticks. */
	public static final int KEADAAN_CAPIT = 1;
	/** Sting, 10 ticks. */
	public static final int KEADAAN_SENGAT = 2;
	/** Digging down, 30 ticks (then hidden). */
	public static final int KEADAAN_GALI = 3;
	/** Hidden under the sand (not drawn, immune). */
	public static final int KEADAAN_TERSEMBUNYI = 4;
	/** Surfacing, 20 ticks of sand burst. */
	public static final int KEADAAN_MUNCUL = 5;

	/** Health, attack and armour per band 1-4 (R1-R2 Pemurnian Qi, R3 Fondasi, R4 Inti Emas). */
	private static final double[] DARAH = {140.0D, 240.0D, 460.0D, 900.0D};
	private static final double[] SERANG = {8.0D, 12.0D, 20.0D, 34.0D};
	private static final double[] ZIRAH = {6.0D, 10.0D, 14.0D, 18.0D};
	private static final int SENGAT_JEDA = 100;
	private static final int GALI_JEDA = 300;
	private static final int GALI_TICK = 30;
	private static final int SEMBUNYI_TICK = 80;
	private static final int MUNCUL_TICK = 20;

	private static final EntityDataAccessor<Integer> DATA_VARIAN = SynchedEntityData.defineId(KalajengkingRajaEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_KEADAAN = SynchedEntityData.defineId(KalajengkingRajaEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_MULAI = SynchedEntityData.defineId(KalajengkingRajaEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_NILAI = SynchedEntityData.defineId(KalajengkingRajaEntity.class, EntityDataSerializers.FLOAT);

	private long situsId;
	private long ruin;
	private int tingkat = 1;
	private int jedaSengat = SENGAT_JEDA;
	private int jedaGali = GALI_JEDA;

	public KalajengkingRajaEntity(EntityType<? extends KalajengkingRajaEntity> type, Level level) {
		super(type, level);
		this.xpReward = 40;
		this.setCanPickUpLoot(false);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 220.0D)
				.add(Attributes.ATTACK_DAMAGE, 12.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.26D)
				.add(Attributes.FOLLOW_RANGE, 32.0D)
				.add(Attributes.ARMOR, 12.0D)
				.add(Attributes.ARMOR_TOUGHNESS, 4.0D)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
				.add(Attributes.STEP_HEIGHT, 1.0D);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VARIAN, VARIAN_RAJA);
		builder.define(DATA_KEADAAN, KEADAAN_DIAM);
		builder.define(DATA_MULAI, 0);
		builder.define(DATA_NILAI, 0.0F);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15D, true) {
			@Override
			public boolean canUse() {
				return !tersembunyi() && !sibuk() && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return !tersembunyi() && super.canContinueToUse();
			}
		});
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
				(target, level) -> target instanceof Player player && !player.isCreative() && !player.isSpectator()));
	}

	/**
	 * Scales the king to band {@code band} (1-4), binds it to ruin {@code ruin} and marks it as a realm-scaled foe;
	 * {@code pasir} gives the pale chitin of Xie Duo's tamed king (the chain's formation stage).
	 */
	public void setup(int band, long ruin, boolean pasir) {
		this.tingkat = Mth.clamp(band, 1, 4);
		this.ruin = ruin;
		base(Attributes.MAX_HEALTH, DARAH[tingkat - 1]);
		base(Attributes.ATTACK_DAMAGE, SERANG[tingkat - 1]);
		base(Attributes.ARMOR, ZIRAH[tingkat - 1]);
		this.setHealth(this.getMaxHealth());
		setVarian(pasir ? VARIAN_PASIR : VARIAN_RAJA);
		setNilai(tingkat * 2.0F);
		this.xpReward = 30 + 30 * tingkat;
		TechniquePower.markRealmFoe(this);
		this.addTag(TechniquePower.TAG_BOSS);
	}

	private void base(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
		AttributeInstance instance = this.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	/** Binds the boss to its ruin site (0 = test spawn). */
	public void ikat(long situsId) {
		this.situsId = situsId;
	}

	public long situsId() {
		return situsId;
	}

	/** The ruin key ({@link Reruntuhan#kunci}). */
	public long ruin() {
		return ruin;
	}

	/** Band 1-4. */
	public int tingkat() {
		return tingkat;
	}

	@Override
	public int varian() {
		return this.entityData.get(DATA_VARIAN);
	}

	@Override
	public int keadaan() {
		return this.entityData.get(DATA_KEADAAN);
	}

	@Override
	public int mulai() {
		return this.entityData.get(DATA_MULAI);
	}

	/** Band x 2 as a float (the client scales the crack glow with it). */
	@Override
	public float nilai() {
		return this.entityData.get(DATA_NILAI);
	}

	public void setVarian(int varian) {
		this.entityData.set(DATA_VARIAN, varian);
	}

	public void setKeadaan(int keadaan) {
		if (keadaan() != keadaan) {
			this.entityData.set(DATA_KEADAAN, keadaan);
			this.entityData.set(DATA_MULAI, (int) (this.level().getGameTime() & 0x7fffffffL));
		}
	}

	public void setNilai(float nilai) {
		this.entityData.set(DATA_NILAI, nilai);
	}

	/** True while under the sand (not drawn, not targetable by its own melee goal). */
	public boolean tersembunyi() {
		return keadaan() == KEADAAN_TERSEMBUNYI || keadaan() == KEADAAN_GALI;
	}

	private boolean sibuk() {
		int k = keadaan();
		return k == KEADAAN_SENGAT || k == KEADAAN_MUNCUL;
	}

	// ================================================================== fighting

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		long now = level.getGameTime();
		int age = umurKeadaan(now);
		LivingEntity target = this.getTarget();
		switch (keadaan()) {
			case KEADAAN_CAPIT -> {
				if (age >= 6) {
					setKeadaan(KEADAAN_DIAM);
				}
			}
			case KEADAAN_SENGAT -> {
				if (age == 6 && target != null && target.isAlive() && this.distanceToSqr(target) <= 36.0D) {
					target.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 1));
					this.doHurtTarget(level, target);
					level.sendParticles(ParticleTypes.ITEM_SLIME, target.getX(), target.getY() + 1.0D, target.getZ(), 14, 0.3D, 0.4D, 0.3D, 0.05D);
				}
				if (age >= 10) {
					setKeadaan(KEADAAN_DIAM);
				}
			}
			case KEADAAN_GALI -> {
				this.getNavigation().stop();
				if (age % 5 == 0) {
					debu(level, 18);
				}
				if (age >= GALI_TICK) {
					setKeadaan(KEADAAN_TERSEMBUNYI);
					this.setInvisible(true);
				}
			}
			case KEADAAN_TERSEMBUNYI -> {
				this.getNavigation().stop();
				if (age >= SEMBUNYI_TICK) {
					muncul(level, target);
				}
			}
			case KEADAAN_MUNCUL -> {
				if (age == 1) {
					debu(level, 60);
					level.playSound(null, this.blockPosition(), SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 2.0F, 0.6F);
					for (Player p : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(3.0D))) {
						Vec3 push = p.position().subtract(this.position()).normalize().scale(0.9D);
						p.push(push.x, 0.45D, push.z);
					}
				}
				if (age >= MUNCUL_TICK) {
					setKeadaan(KEADAAN_DIAM);
				}
			}
			default -> {
				if (jedaSengat > 0) {
					jedaSengat--;
				}
				if (jedaGali > 0) {
					jedaGali--;
				}
				if (target != null && target.isAlive()) {
					double d2 = this.distanceToSqr(target);
					if (jedaGali <= 0 && this.getHealth() < this.getMaxHealth() * 0.7F && d2 > 9.0D) {
						jedaGali = GALI_JEDA;
						setKeadaan(KEADAAN_GALI);
						level.playSound(null, this.blockPosition(), SoundEvents.SAND_STEP, SoundSource.HOSTILE, 1.5F, 0.5F);
					} else if (jedaSengat <= 0 && d2 <= 36.0D) {
						jedaSengat = SENGAT_JEDA;
						setKeadaan(KEADAAN_SENGAT);
						level.playSound(null, this.blockPosition(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 1.4F, 0.5F);
					}
				}
			}
		}
	}

	/** Surfaces next to the target (or where it sank when none). */
	private void muncul(ServerLevel level, LivingEntity target) {
		this.setInvisible(false);
		if (target != null && target.isAlive() && target.level() == level) {
			double a = this.getRandom().nextDouble() * Math.PI * 2.0D;
			double x = target.getX() + Math.cos(a) * 2.5D;
			double z = target.getZ() + Math.sin(a) * 2.5D;
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
			double y = Math.abs(top - target.getY()) <= 3.0D ? top : target.getY();
			this.teleportTo(x, y, z);
		}
		setKeadaan(KEADAAN_MUNCUL);
	}

	/** Sand thrown up around the body (particles only). */
	private void debu(ServerLevel level, int n) {
		BlockPos below = this.blockPosition().below();
		BlockParticleOption sand = new BlockParticleOption(ParticleTypes.BLOCK,
				level.getBlockState(below).isAir() ? Blocks.SAND.defaultBlockState() : level.getBlockState(below));
		level.sendParticles(sand, getX(), getY() + 0.3D, getZ(), n, 1.4D, 0.3D, 1.4D, 0.12D);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		if (tersembunyi()) {
			return false;
		}
		if (keadaan() == KEADAAN_DIAM) {
			setKeadaan(KEADAAN_CAPIT);
		}
		return super.doHurtTarget(level, target);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (tersembunyi()) {
			return false;
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	public boolean isInvulnerable() {
		return tersembunyi() || super.isInvulnerable();
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			Reruntuhan.bosMati(level, this);
		}
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}
}
