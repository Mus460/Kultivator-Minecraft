package dev.kultivator.dunia.naga;

import dev.kultivator.beast.BeastData;
import dev.kultivator.util.NoGrief;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Naga Liar (DESIGN 34.5.11): a Jiao, a dragon that is not yet a true dragon. One hitbox at the head (2.0 × 1.2), no
 * goals, moved only by its flight script below; the long body (head, jaw, 8 / 14 / 20 segments, claws, fins, whiskers)
 * is drawn on the client from a chain that follows the head's trail. Never saved ({@code noSave}): a wild Jiao simply
 * leaves with its chunk, a tamed one goes back into its Mutiara Naga Jinak ({@link TamedJiao}).
 * <p>
 * Wild: a lazy figure-eight at y 120-200 around its spawn point; neutral until hit or provoked (a flying player within
 * 24 blocks at its height for 5 s). Fighting it circles its target and uses telegraphed skills: Napas Unsur (a breath
 * cone: water Slowness, wind knockback or a lightning chain, all particles), Ekor Sabet (a tail ring), Raungan Naga
 * (Weakness 4 s) and Lilitan (it coils around the target, tightening), plus a diving bite. At 25 % health it is Lemah
 * (hovers low and exhausted for 30 s, the moment for the Selendang); at 10 % it flees. Nothing it does touches a block,
 * sets a fire or calls weather: every effect is an entity effect, damage or a particle (rule W1, W3).
 * <p>
 * Tamed (flag {@link #FLAG_TAMED}, an owner): follows its owner through the air, breathes at the owner's target every
 * 12 s, can be ridden (owner first, one extra passenger) with the Napas Naga stamina, never dies (a lethal blow sends it
 * back into the pearl to heal).
 */
public class NagaLiarEntity extends Mob implements OwnableEntity {

	private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(NagaLiarEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_FLAGS = SynchedEntityData.defineId(NagaLiarEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_TIER = SynchedEntityData.defineId(NagaLiarEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_SKILL = SynchedEntityData.defineId(NagaLiarEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_SKILL_TICKS = SynchedEntityData.defineId(NagaLiarEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_AIM = SynchedEntityData.defineId(NagaLiarEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_SEGMENTS = SynchedEntityData.defineId(NagaLiarEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_STAMINA = SynchedEntityData.defineId(NagaLiarEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> DATA_OWNER = SynchedEntityData.defineId(
			NagaLiarEntity.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);

	// ------------------------------------------------------------------ synced flags
	public static final int FLAG_LEMAH = 1;
	public static final int FLAG_ANGRY = 2;
	public static final int FLAG_TAMED = 4;
	public static final int FLAG_CONTEST = 8;
	public static final int FLAG_FLEE = 16;
	public static final int FLAG_TIRED = 32;
	public static final int FLAG_LEAVING = 64;
	/** Held still by a beast tribulation (DESIGN 34.5.12). */
	public static final int FLAG_TRIBULATION = 128;

	// ------------------------------------------------------------------ skills (DATA_SKILL)
	public static final int SKILL_NONE = 0;
	/** Napas Unsur: 20 ticks of telegraph (the jaw opens, light gathers), then the cone for {@link #BREATH_ACTIVE}. */
	public static final int SKILL_BREATH = 1;
	/** Ekor Sabet: the tail point glows for 16 ticks, then the ring. */
	public static final int SKILL_TAIL = 2;
	/** Raungan Naga: 12 ticks of rearing, then the roar. */
	public static final int SKILL_ROAR = 3;
	/** Lilitan: 100 ticks circling the target ever closer. */
	public static final int SKILL_COIL = 4;
	/** A dive at the target. */
	public static final int SKILL_BITE = 5;

	public static final int BREATH_TELEGRAPH = 20;
	public static final int BREATH_ACTIVE = 30;
	public static final int TAIL_TELEGRAPH = 16;
	public static final int ROAR_TELEGRAPH = 12;
	public static final int COIL_TICKS = 100;
	public static final int BITE_TICKS = 50;
	/** The breath cone: length (blocks) and the cosine of its half angle (about 25 degrees). */
	public static final double BREATH_LENGTH = 14.0D;
	public static final double BREATH_COS = 0.9D;

	/** Base health at Nascent Soul: 600 × (1 + 0.8 × (realm − Nascent Soul)) (DESIGN 34.5.11). */
	public static final double BASE_HEALTH = 600.0D;
	public static final double HEALTH_PER_REALM = 0.8D;
	/** Wild life: 10 minutes, then it leaves. */
	public static final int WILD_LIFE = 12000;
	/** Lemah lasts 30 s. */
	public static final int LEMAH_TICKS = 600;
	public static final float LEMAH_AT = 0.25F;
	public static final float FLEE_AT = 0.10F;
	/** A flying player within this range at its height for {@link #PROVOKE_TICKS} provokes a wild Jiao. */
	public static final double PROVOKE_RANGE = 24.0D;
	public static final int PROVOKE_TICKS = 100;
	/** A tamed Jiao breathes at its owner's target this often (12 s). */
	public static final int TAMED_BREATH_EVERY = 240;
	public static final double TAMED_BREATH_RANGE = 24.0D;

	static final net.minecraft.resources.Identifier HEALTH_ID = dev.kultivator.Kultivator.id("naga_health");

	/** What the flight script does now (server only). */
	enum Mode { WANDER, FIGHT, LEMAH, FLEE, LEAVE, CONTEST, TAMED }

	// ------------------------------------------------------------------ server state
	Mode mode = Mode.WANDER;
	/** The centre of the figure-eight (wild) and where it came from. */
	Vec3 home = Vec3.ZERO;
	private double phase;
	private double amplitude = 64.0D;
	private int life;
	private int modeTicks;
	@Nullable UUID targetId;
	private int lostTicks;
	private int skillCooldown = 60;
	private int skillTicks;
	private int skill;
	private Vec3 tailPoint = Vec3.ZERO;
	private double coilAngle;
	private float coilDamageTaken;
	/** The realm factor fixed at spawn: 1 + 0.8 × (realm − Nascent Soul) for health, 1 + 0.5 × ... for damage. */
	double healthFactor = 1.0D;
	double damageFactor = 1.0D;
	private boolean lemahUsed;
	/** Provoke counters per player (wild). */
	private final java.util.Map<UUID, Integer> provoke = new java.util.HashMap<>();
	private final ArrayDeque<Vec3> trail = new ArrayDeque<>();
	private @Nullable ServerBossEvent bar;
	/** The test flag: spawned by {@code uji naga}, ignores caps; no rewards beyond the scales. */
	boolean test;
	/** The live map site of this sighting (0 when none). */
	long siteId;
	/** Tamed: breath timer; ridden: the input of this tick. */
	int breathTimer = TAMED_BREATH_EVERY / 2;
	private Vec3 riddenVelocity = Vec3.ZERO;
	private int followAngleSeed;

	public NagaLiarEntity(EntityType<? extends NagaLiarEntity> type, Level level) {
		super(type, level);
		this.xpReward = 0;
		this.noPhysics = true;
		this.setNoGravity(true);
		this.setCanPickUpLoot(false);
		this.addTag(NoGrief.TAG);
		this.followAngleSeed = this.random.nextInt(360);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, BASE_HEALTH)
				.add(Attributes.ATTACK_DAMAGE, 10.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.3D)
				.add(Attributes.FLYING_SPEED, 0.6D)
				.add(Attributes.FOLLOW_RANGE, 64.0D)
				.add(Attributes.ARMOR, 8.0D)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_KIND, 0);
		builder.define(DATA_FLAGS, 0);
		builder.define(DATA_TIER, 3);
		builder.define(DATA_SKILL, SKILL_NONE);
		builder.define(DATA_SKILL_TICKS, 0);
		builder.define(DATA_AIM, -1);
		builder.define(DATA_STAMINA, 1.0F);
		builder.define(DATA_SEGMENTS, 20);
		builder.define(DATA_OWNER, Optional.empty());
	}

	@Override
	protected void registerGoals() {
		// Moved by its flight script only.
	}

	// ================================================================== synced values

	public NagaKind kind() {
		return NagaKind.byOrdinal(this.entityData.get(DATA_KIND));
	}

	void setKind(NagaKind kind) {
		this.entityData.set(DATA_KIND, kind.ordinal());
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

	/** The beast tier of a tamed Jiao (3 Jiao, 4 Horned, 5 True Dragon, 6 Yinglong); 3 for a wild one. */
	public int tier() {
		return Mth.clamp(this.entityData.get(DATA_TIER), BeastData.MAX_TIER, BeastData.TOP_TIER);
	}

	void setTier(int tier) {
		this.entityData.set(DATA_TIER, Mth.clamp(tier, BeastData.MAX_TIER, BeastData.TOP_TIER));
	}

	public int skill() {
		return this.entityData.get(DATA_SKILL);
	}

	/** Ticks the current skill has run (the client times its telegraph from this). */
	public int skillTicks() {
		return this.entityData.get(DATA_SKILL_TICKS);
	}

	/** The entity id the breath aims at, or -1 (straight ahead). */
	public int aimId() {
		return this.entityData.get(DATA_AIM);
	}

	/** The server's body length cap (config {@code segmentsMax}, 6-24); the client draws at most this many segments. */
	public int segmentsMax() {
		return Mth.clamp(this.entityData.get(DATA_SEGMENTS), 6, 24);
	}

	/** Napas Naga, 0..1 (a ridden tamed Jiao). */
	public float stamina() {
		return Mth.clamp(this.entityData.get(DATA_STAMINA), 0.0F, 1.0F);
	}

	void setStamina(float stamina) {
		float clamped = Mth.clamp(stamina, 0.0F, 1.0F);
		if (Math.abs(clamped - this.entityData.get(DATA_STAMINA)) > 0.001F || clamped == 0.0F || clamped == 1.0F) {
			this.entityData.set(DATA_STAMINA, clamped);
		}
	}

	public boolean isTamed() {
		return hasFlag(FLAG_TAMED);
	}

	@Override
	public @Nullable EntityReference<LivingEntity> getOwnerReference() {
		return this.entityData.get(DATA_OWNER).orElse(null);
	}

	void setOwnerPlayer(@Nullable ServerPlayer owner) {
		if (owner == null) {
			this.entityData.set(DATA_OWNER, Optional.empty());
		} else {
			EntityReference<LivingEntity> reference = EntityReference.of(owner.getUUID());
			this.entityData.set(DATA_OWNER, Optional.of(reference));
		}
	}

	/** The owner's UUID, or null for a wild Jiao. */
	public @Nullable UUID ownerId() {
		EntityReference<LivingEntity> reference = getOwnerReference();
		return reference == null ? null : reference.getUUID();
	}

	public @Nullable ServerPlayer serverOwner() {
		UUID id = ownerId();
		return id == null || !(this.level() instanceof ServerLevel level) ? null : level.getServer().getPlayerList().getPlayer(id);
	}

	private void startSkill(int id, @Nullable Entity aim) {
		this.skill = id;
		this.skillTicks = 0;
		this.entityData.set(DATA_SKILL, id);
		this.entityData.set(DATA_SKILL_TICKS, 0);
		this.entityData.set(DATA_AIM, aim == null ? -1 : aim.getId());
	}

	/** Starts skill {@code id} aimed at {@code aim} (a tamed Jiao's breath at its owner's target). */
	void beginSkill(int id, @Nullable Entity aim) {
		startSkill(id, aim);
	}

	/** The skill counter of this tick (server). */
	int serverSkill() {
		return skill;
	}

	int serverSkillTicks() {
		return skillTicks;
	}

	private void endSkill(int cooldown) {
		this.skill = SKILL_NONE;
		this.skillTicks = 0;
		this.skillCooldown = cooldown;
		this.entityData.set(DATA_SKILL, SKILL_NONE);
		this.entityData.set(DATA_SKILL_TICKS, 0);
		this.entityData.set(DATA_AIM, -1);
	}

	// ================================================================== set-up (server)

	/** A wild Jiao of {@code kind} at {@code pos}; {@code realm} (an ordinal, at least Nascent Soul) scales health and damage. */
	void setUpWild(NagaKind kind, Vec3 pos, int realmOrdinal, boolean test) {
		setKind(kind);
		this.entityData.set(DATA_SEGMENTS, Naga.config().segmentsMax);
		this.test = test;
		this.home = pos;
		this.amplitude = 48.0D + this.random.nextDouble() * 32.0D;
		this.phase = this.random.nextDouble() * Math.PI * 2.0D;
		int over = Math.max(0, realmOrdinal - dev.kultivator.data.Realm.NASCENT_SOUL.ordinal());
		this.healthFactor = 1.0D + HEALTH_PER_REALM * over;
		this.damageFactor = 1.0D + 0.5D * over;
		setHealthBase(BASE_HEALTH * healthFactor);
		this.setHealth(this.getMaxHealth());
		this.snapTo(pos.x, pos.y, pos.z, this.random.nextFloat() * 360.0F, 0.0F);
		this.mode = Mode.WANDER;
	}

	/** Max health = {@code value} (a modifier on the 600 base, so every reader sees the real value). */
	void setHealthBase(double value) {
		AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
		if (health == null) {
			return;
		}
		double add = value - BASE_HEALTH;
		if (Math.abs(add) < 0.01D) {
			health.removeModifier(HEALTH_ID);
		} else {
			health.addOrReplacePermanentModifier(new AttributeModifier(HEALTH_ID, add, AttributeModifier.Operation.ADD_VALUE));
		}
		if (this.getHealth() > this.getMaxHealth()) {
			this.setHealth(this.getMaxHealth());
		}
	}

	/** Turns this Jiao into {@code owner}'s tamed beast of {@code tier} (the subdue, or a summon from the pearl). */
	void becomeTamed(ServerPlayer owner, NagaKind kind, int tier, float healthFraction, float stamina) {
		setKind(kind);
		setTier(tier);
		this.entityData.set(DATA_SEGMENTS, Naga.config().segmentsMax);
		setOwnerPlayer(owner);
		setFlag(FLAG_TAMED, true);
		setFlag(FLAG_LEMAH | FLAG_ANGRY | FLAG_CONTEST | FLAG_FLEE | FLAG_LEAVING, false);
		this.mode = Mode.TAMED;
		this.targetId = null;
		this.test = false;
		this.healthFactor = 1.0D;
		this.damageFactor = 1.0D + 0.5D * (tier - BeastData.MAX_TIER);
		setHealthBase(TamedJiao.maxHealth(tier));
		this.setHealth(Math.max(1.0F, this.getMaxHealth() * Mth.clamp(healthFraction, 0.05F, 1.0F)));
		setStamina(stamina);
		endSkill(40);
		this.setCustomName(Component.translatable(BeastData.titleKey(BeastData.JIAO, tier)));
		this.setCustomNameVisible(false);
		removeBar();
	}

	// ================================================================== tick

	@Override
	public void tick() {
		boolean ridden = this.getControllingPassenger() instanceof Player;
		this.noPhysics = !ridden;
		super.tick();
		this.setNoGravity(true);
		if (this.level() instanceof ServerLevel level) {
			serverTick(level, ridden);
		}
	}

	private void serverTick(ServerLevel level, boolean ridden) {
		if (this.isRemoved() || !this.isAlive()) {
			return;
		}
		trail.addFirst(this.position());
		while (trail.size() > 60) {
			trail.removeLast();
		}
		modeTicks++;
		if (isTamed()) {
			TamedJiao.tick(level, this, ridden);
			if (this.isRemoved()) {
				return;
			}
		} else if (this.tickCount % 40 == 0 && wildShouldVanish(level)) {
			vanish(level, false);
			return;
		}
		if (this.tickCount % 10 == 0) {
			updateBar(level);
		}
		if (skill != SKILL_NONE) {
			skillTicks++;
			this.entityData.set(DATA_SKILL_TICKS, skillTicks);
		} else if (skillCooldown > 0) {
			skillCooldown--;
		}
	}

	/** Wild: gone after 10 minutes (it leaves), or at once with no player within 256 blocks. */
	private boolean wildShouldVanish(ServerLevel level) {
		if (mode == Mode.CONTEST) {
			return false;
		}
		if (level.getNearestPlayer(this, 256.0D) == null) {
			return true;
		}
		life += 40;
		if (life >= WILD_LIFE && mode == Mode.WANDER) {
			setMode(Mode.LEAVE);
		}
		return false;
	}

	void setMode(Mode next) {
		this.mode = next;
		this.modeTicks = 0;
		setFlag(FLAG_ANGRY, next == Mode.FIGHT);
		setFlag(FLAG_LEMAH, next == Mode.LEMAH);
		setFlag(FLAG_FLEE, next == Mode.FLEE);
		setFlag(FLAG_LEAVING, next == Mode.LEAVE);
		setFlag(FLAG_CONTEST, next == Mode.CONTEST);
		if (next != Mode.FIGHT) {
			endSkill(Math.max(skillCooldown, 40));
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.getControllingPassenger() instanceof Player) {
			return;
		}
		if (hasFlag(FLAG_TRIBULATION)) {
			hover(this.position(), 0.0D);
			return;
		}
		switch (mode) {
			case WANDER -> wander(level);
			case FIGHT -> fight(level);
			case LEMAH -> lemah(level);
			case FLEE -> flee(level);
			case LEAVE -> leave(level);
			case CONTEST -> contest(level);
			case TAMED -> {
				// TamedJiao.tick steers it (in serverTick, so the order with the owner's movement is stable)
			}
		}
	}

	// ================================================================== flight

	/** Steers toward {@code point} at {@code speed} blocks per tick, turning smoothly ({@code grip} 0..1 per tick). */
	void steer(Vec3 point, double speed, double grip) {
		Vec3 to = point.subtract(this.position());
		double length = to.length();
		Vec3 desired = length < 1.0E-4D ? Vec3.ZERO : to.scale(Math.min(speed, length * 0.5D) / length);
		Vec3 velocity = this.getDeltaMovement();
		this.setDeltaMovement(velocity.add(desired.subtract(velocity).scale(Mth.clamp(grip, 0.0D, 1.0D))));
		faceVelocity(null);
	}

	/** Slows to a hover at {@code point}, facing {@code look} (or along its drift). */
	void hover(Vec3 point, double grip) {
		Vec3 to = point.subtract(this.position());
		Vec3 velocity = this.getDeltaMovement().scale(0.85D);
		if (grip > 0.0D) {
			velocity = velocity.add(to.scale(0.02D * grip));
		}
		this.setDeltaMovement(velocity);
	}

	/** Turns the head along the velocity, or toward {@code look} when given. */
	void faceVelocity(@Nullable Vec3 look) {
		Vec3 dir = look != null ? look.subtract(this.position()) : this.getDeltaMovement();
		if (dir.lengthSqr() < 0.0004D) {
			return;
		}
		double horizontal = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
		float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float) -(Mth.atan2(dir.y, horizontal) * Mth.RAD_TO_DEG);
		float smoothYaw = Mth.approachDegrees(this.getYRot(), yaw, 9.0F);
		float smoothPitch = Mth.approach(this.getXRot(), Mth.clamp(pitch, -60.0F, 60.0F), 6.0F);
		this.setYRot(smoothYaw);
		this.setXRot(smoothPitch);
		this.yHeadRot = smoothYaw;
		this.yBodyRot = smoothYaw;
	}

	@Override
	public void travel(Vec3 input) {
		if (this.getControllingPassenger() instanceof Player) {
			// ridden: the velocity the rider's input asked for (getRiddenInput), with collisions
			Vec3 velocity = this.getDeltaMovement();
			velocity = velocity.add(riddenVelocity.subtract(velocity).scale(0.14D));
			this.setDeltaMovement(velocity);
			this.move(MoverType.SELF, velocity);
			return;
		}
		// the flight script set the velocity; no gravity, no friction
		this.move(MoverType.SELF, this.getDeltaMovement());
	}

	private void wander(ServerLevel level) {
		phase += Math.PI * 2.0D / 1400.0D;
		double t = phase + 0.12D;
		double s = Math.sin(t);
		Vec3 point = new Vec3(home.x + amplitude * s, home.y + 14.0D * Math.sin(t * 0.5D), home.z + amplitude * s * Math.cos(t));
		steer(point, 0.55D, 0.05D);
		if (this.tickCount % 10 == 0) {
			checkProvoke(level);
		}
	}

	/** A flying player within 24 blocks at its height (±8) for 5 s provokes it (counted in steps of 10 ticks). */
	private void checkProvoke(ServerLevel level) {
		AABB box = this.getBoundingBox().inflate(PROVOKE_RANGE);
		java.util.Set<UUID> seen = new java.util.HashSet<>();
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box, p -> !p.isSpectator() && !p.isCreative() && p.isAlive())) {
			boolean flying = player.getAbilities().flying || player.isFallFlying() || player.getVehicle() != null && !player.getVehicle().onGround();
			if (!flying || Math.abs(player.getY() - this.getY()) > 8.0D || player.distanceToSqr(this) > PROVOKE_RANGE * PROVOKE_RANGE) {
				continue;
			}
			seen.add(player.getUUID());
			int ticks = provoke.getOrDefault(player.getUUID(), 0) + 10;
			provoke.put(player.getUUID(), ticks);
			if (ticks >= PROVOKE_TICKS) {
				provoke.clear();
				engage(level, player, true);
				return;
			}
			if (ticks == 50) {
				// a warning growl halfway
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.2F, 1.6F);
				player.sendOverlayMessage(Component.translatable("kultivator.naga.provoke.warn", kind().displayName())
						.withStyle(net.minecraft.ChatFormatting.YELLOW));
			}
		}
		provoke.keySet().retainAll(seen);
	}

	/** Starts (or switches) a fight against {@code target}; {@code roar} opens it with the Raungan Naga. */
	void engage(ServerLevel level, LivingEntity target, boolean roar) {
		if (isTamed() || mode == Mode.CONTEST || mode == Mode.LEMAH || mode == Mode.FLEE) {
			if (mode == Mode.LEMAH && target != null) {
				this.targetId = target.getUUID();
			}
			return;
		}
		boolean fresh = mode != Mode.FIGHT;
		this.targetId = target.getUUID();
		this.lostTicks = 0;
		setMode(Mode.FIGHT);
		this.setTarget(target);
		if (fresh) {
			home = this.position();
			dev.kultivator.voice.Voice.boom(this, "boss.awaken", 96.0D);
			if (roar) {
				startSkill(SKILL_ROAR, target);
			} else {
				skillCooldown = 30;
			}
		}
	}

	private @Nullable LivingEntity currentTarget(ServerLevel level) {
		if (targetId == null) {
			return null;
		}
		Entity entity = level.getEntity(targetId);
		if (entity instanceof LivingEntity living && living.isAlive() && !living.isSpectator() && living.distanceToSqr(this) < 96.0D * 96.0D
				&& !(living instanceof Player player && (player.isCreative() || player.getAbilities().invulnerable))) {
			return living;
		}
		return null;
	}

	private void fight(ServerLevel level) {
		LivingEntity target = currentTarget(level);
		if (target == null) {
			if (++lostTicks > 100) {
				this.setTarget(null);
				this.targetId = null;
				home = this.position();
				setMode(Mode.WANDER);
			} else {
				hover(this.position().add(0.0D, 0.1D, 0.0D), 0.0D);
			}
			return;
		}
		lostTicks = 0;
		if (skill == SKILL_NONE) {
			orbit(target, 14.0D, 0.6D);
			if (skillCooldown <= 0) {
				pickSkill(target);
			}
			return;
		}
		NagaSkills.run(level, this, target, skill, skillTicks);
	}

	/** Circles {@code target} at {@code radius}, a few blocks above it. */
	void orbit(LivingEntity target, double radius, double speed) {
		coilAngle += speed / Math.max(4.0D, radius);
		Vec3 centre = target.position().add(0.0D, 4.0D + 2.0D * Math.sin(this.tickCount * 0.03D), 0.0D);
		Vec3 point = centre.add(Math.cos(coilAngle) * radius, 0.0D, Math.sin(coilAngle) * radius);
		steer(point, speed, 0.08D);
	}

	private void pickSkill(LivingEntity target) {
		double distance = Math.sqrt(this.distanceToSqr(target));
		int roll = this.random.nextInt(100);
		int next;
		if (roll < 30) {
			next = SKILL_BREATH;
		} else if (roll < 52) {
			next = SKILL_BITE;
		} else if (roll < 70) {
			next = SKILL_TAIL;
		} else if (roll < 84) {
			next = SKILL_COIL;
		} else {
			next = SKILL_ROAR;
		}
		if (next == SKILL_TAIL && distance > 20.0D) {
			next = SKILL_BREATH;
		}
		if (next == SKILL_TAIL) {
			tailPoint = trailPoint(10.0D);
		}
		if (next == SKILL_COIL) {
			coilAngle = Math.atan2(this.getZ() - target.getZ(), this.getX() - target.getX());
			coilDamageTaken = 0.0F;
		}
		startSkill(next, target);
	}

	/** Ends the running skill with a cooldown scaled by the realm (stronger Jiao act more often). */
	void finishSkill() {
		int base = 70 - (int) Math.round(10.0D * (damageFactor - 1.0D));
		endSkill(Math.max(30, base + this.random.nextInt(30)));
	}

	/** The point of its trail about {@code behind} blocks back along the body (the tail end for Ekor Sabet). */
	Vec3 trailPoint(double behind) {
		double walked = 0.0D;
		Vec3 last = this.position();
		Iterator<Vec3> iterator = trail.iterator();
		while (iterator.hasNext()) {
			Vec3 next = iterator.next();
			walked += next.distanceTo(last);
			last = next;
			if (walked >= behind) {
				return next;
			}
		}
		// too short a trail (it just turned): straight behind
		float yaw = this.getYRot() * Mth.DEG_TO_RAD;
		return this.position().add(Mth.sin(yaw) * behind, 0.0D, -Mth.cos(yaw) * behind);
	}

	Vec3 storedTailPoint() {
		return tailPoint;
	}

	double nextCoilAngle(double step) {
		coilAngle += step;
		return coilAngle;
	}

	float coilDamageTaken() {
		return coilDamageTaken;
	}

	/** The mouth: the front of the head. */
	public Vec3 mouth() {
		float yaw = this.getYRot() * Mth.DEG_TO_RAD;
		float pitch = this.getXRot() * Mth.DEG_TO_RAD;
		Vec3 forward = new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
		return this.position().add(0.0D, 0.55D, 0.0D).add(forward.scale(1.8D));
	}

	private void lemah(ServerLevel level) {
		// exhausted: sinks to about six blocks over the ground and drifts there
		BlockPos at = this.blockPosition();
		double ground = level.hasChunkAt(at) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ()) : this.getY() - 6.0D;
		double y = Math.max(ground + 6.0D, level.getMinY() + 8.0D);
		Vec3 point = new Vec3(this.getX() + Math.sin(modeTicks * 0.02D) * 0.6D, y + Math.sin(modeTicks * 0.08D) * 0.6D, this.getZ());
		steer(point, 0.25D, 0.05D);
		if (modeTicks % 40 == 0) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDER_DRAGON_AMBIENT, SoundSource.HOSTILE, 0.8F, 0.6F);
		}
		if (modeTicks >= LEMAH_TICKS) {
			if (this.getHealth() <= this.getMaxHealth() * FLEE_AT) {
				startFlee(level);
			} else {
				LivingEntity target = currentTarget(level);
				this.mode = Mode.WANDER;
				if (target != null) {
					engage(level, target, true);
				} else {
					setMode(Mode.WANDER);
					home = this.position().add(0.0D, 40.0D, 0.0D);
				}
			}
		}
	}

	void startFlee(ServerLevel level) {
		setMode(Mode.FLEE);
		Player nearest = level.getNearestPlayer(this, 96.0D);
		Vec3 away = nearest == null ? new Vec3(1.0D, 0.0D, 0.0D) : this.position().subtract(nearest.position()).multiply(1.0D, 0.0D, 1.0D);
		if (away.lengthSqr() < 1.0E-4D) {
			away = new Vec3(1.0D, 0.0D, 0.0D);
		}
		home = this.position().add(away.normalize().scale(200.0D)).add(0.0D, 60.0D, 0.0D);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDER_DRAGON_HURT, SoundSource.HOSTILE, 2.0F, 0.7F);
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(64.0D))) {
			player.sendOverlayMessage(Component.translatable("kultivator.naga.flee", kind().displayName())
					.withStyle(net.minecraft.ChatFormatting.GRAY));
		}
	}

	private void flee(ServerLevel level) {
		steer(home, 1.1D, 0.07D);
		if (modeTicks > 200) {
			vanish(level, true);
		}
	}

	private void leave(ServerLevel level) {
		float yaw = this.getYRot() * Mth.DEG_TO_RAD;
		Vec3 point = this.position().add(-Mth.sin(yaw) * 30.0D, 18.0D, Mth.cos(yaw) * 30.0D);
		steer(point, 0.9D, 0.05D);
		if (modeTicks > 120) {
			vanish(level, false);
		}
	}

	private void contest(ServerLevel level) {
		// pinned by the Selendang: hovering, facing the binder
		ServerPlayer binder = WillContest.binderOf(this);
		hover(this.position(), 0.0D);
		if (binder != null) {
			faceVelocity(binder.getEyePosition());
		}
	}

	/** A wild Jiao leaves the world (escaped, left, or no player near). */
	void vanish(ServerLevel level, boolean escaped) {
		if (escaped) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 2.0F, 0.8F);
		}
		removeBar();
		WildDragons.forget(this);
		this.discard();
	}

	// ================================================================== the boss bar (wild fights)

	private void updateBar(ServerLevel level) {
		boolean show = !isTamed() && (mode == Mode.FIGHT || mode == Mode.LEMAH || mode == Mode.FLEE || mode == Mode.CONTEST);
		if (!show) {
			removeBar();
			return;
		}
		if (bar == null) {
			BossEvent.BossBarColor color = switch (kind()) {
				case AIR -> BossEvent.BossBarColor.BLUE;
				case AWAN -> BossEvent.BossBarColor.WHITE;
				case PETIR -> BossEvent.BossBarColor.PURPLE;
			};
			bar = new ServerBossEvent(UUID.randomUUID(), kind().displayName(), color, BossEvent.BossBarOverlay.NOTCHED_10);
		}
		Component name = kind().displayName();
		if (mode == Mode.LEMAH) {
			name = Component.translatable("kultivator.naga.bar.lemah", name);
		} else if (mode == Mode.FLEE) {
			name = Component.translatable("kultivator.naga.bar.flee", name);
		} else if (mode == Mode.CONTEST) {
			name = Component.translatable("kultivator.naga.bar.contest", name);
		}
		bar.setName(name);
		bar.setProgress(Mth.clamp(this.getHealth() / this.getMaxHealth(), 0.0F, 1.0F));
		List<ServerPlayer> near = level.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(64.0D));
		for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
			if (!near.contains(player)) {
				bar.removePlayer(player);
			}
		}
		for (ServerPlayer player : near) {
			if (!bar.getPlayers().contains(player)) {
				bar.addPlayer(player);
			}
		}
	}

	void removeBar() {
		if (bar != null) {
			bar.removeAllPlayers();
			bar = null;
		}
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		removeBar();
		if (!this.level().isClientSide()) {
			WildDragons.forget(this);
			if (isTamed()) {
				TamedJiao.onRemoved(this);
			}
		}
		super.remove(reason);
	}

	// ================================================================== damage

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, damage);
		}
		if (mode == Mode.CONTEST || hasFlag(FLAG_TRIBULATION) && !TamedJiao.isTribulationDamage(source)) {
			return false;
		}
		Entity attacker = source.getEntity();
		if (isTamed()) {
			ServerPlayer owner = serverOwner();
			if (attacker != null && (attacker == owner || attacker == this || attacker instanceof NagaLiarEntity other && other.isTamed()
					&& java.util.Objects.equals(other.ownerId(), ownerId()))) {
				return false;
			}
			if (damage >= this.getHealth() && !TamedJiao.isTribulationDamage(source)) {
				// a tamed Jiao never dies: it returns into its pearl to heal
				TamedJiao.knockedOut(level, this);
				return true;
			}
			return super.hurtServer(level, source, damage);
		}
		if (attacker instanceof NagaLiarEntity) {
			return false;
		}
		float before = this.getHealth();
		boolean hurt = super.hurtServer(level, source, damage);
		if (!hurt || !this.isAlive()) {
			return hurt;
		}
		float taken = before - this.getHealth();
		if (skill == SKILL_COIL) {
			coilDamageTaken += taken;
		}
		if (attacker instanceof LivingEntity living && !(attacker instanceof NagaLiarEntity) && mode != Mode.FLEE) {
			if (mode == Mode.WANDER || mode == Mode.LEAVE) {
				engage(level, living, true);
			} else if (mode == Mode.FIGHT && (targetId == null || this.random.nextInt(4) == 0)) {
				this.targetId = living.getUUID();
				this.setTarget(living);
			}
		}
		float fraction = this.getHealth() / this.getMaxHealth();
		if (fraction <= FLEE_AT && mode != Mode.FLEE && mode != Mode.CONTEST) {
			startFlee(level);
		} else if (fraction <= LEMAH_AT && !lemahUsed && (mode == Mode.FIGHT || mode == Mode.WANDER || mode == Mode.LEAVE)) {
			lemahUsed = true;
			setMode(Mode.LEMAH);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDER_DRAGON_HURT, SoundSource.HOSTILE, 2.0F, 0.5F);
			for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(64.0D))) {
				player.sendOverlayMessage(Component.translatable("kultivator.naga.lemah", kind().displayName())
						.withStyle(net.minecraft.ChatFormatting.AQUA));
			}
		}
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level && !isTamed()) {
			WildDragons.onKilled(level, this, source);
		}
		removeBar();
	}

	/** The Pertarungan Kehendak failed: it recovers 30 % of its health and flees. */
	void recoverAndFlee(ServerLevel level) {
		this.setHealth(Math.min(this.getMaxHealth(), this.getHealth() + this.getMaxHealth() * 0.3F));
		lemahUsed = true;
		this.mode = Mode.FIGHT;
		startFlee(level);
	}

	// ================================================================== riding (tamed)

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		if (isTamed() && this.getFirstPassenger() instanceof Player player && java.util.Objects.equals(player.getUUID(), ownerId())) {
			return player;
		}
		return null;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return isTamed() && passenger instanceof Player && this.getPassengers().size() < 2;
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		int index = Math.max(0, this.getPassengers().indexOf(passenger));
		double back = index == 0 ? 1.55D : 3.1D;
		double up = index == 0 ? 0.95D : 0.8D;
		float yaw = this.getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(Mth.sin(yaw) * back, up, -Mth.cos(yaw) * back);
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		return new Vec3(this.getX(), this.getBoundingBox().maxY + 0.2D, this.getZ());
	}

	@Override
	public boolean isFlyingVehicle() {
		return true;
	}

	@Override
	protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
		double speed = TamedJiao.rideSpeed(tier());
		float yaw = controller.getYRot() * Mth.DEG_TO_RAD;
		float pitch = controller.getXRot() * Mth.DEG_TO_RAD;
		Vec3 look = new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
		Vec3 right = new Vec3(-Mth.cos(yaw), 0.0D, -Mth.sin(yaw));
		float forward = controller.zza;
		float strafe = controller.xxa;
		Vec3 desired = look.scale(forward > 0.0F ? forward * speed : forward * speed * 0.4D).add(right.scale(-strafe * speed * 0.5D));
		if (controller.isJumping()) {
			desired = desired.add(0.0D, speed * 0.55D, 0.0D);
		}
		if (stamina() <= 0.0F || hasFlag(FLAG_TIRED)) {
			// out of Napas Naga: it glides down and cannot climb until it rests on the ground
			desired = new Vec3(desired.x * 0.6D, Math.min(desired.y, -0.18D), desired.z * 0.6D);
		}
		this.riddenVelocity = desired;
		return Vec3.ZERO;
	}

	@Override
	protected void tickRidden(Player controller, Vec3 riddenInput) {
		super.tickRidden(controller, riddenInput);
		float yaw = Mth.approachDegrees(this.getYRot(), controller.getYRot(), 7.0F);
		float pitch = Mth.approach(this.getXRot(), controller.getXRot() * 0.5F, 4.0F);
		this.setYRot(yaw);
		this.setXRot(pitch);
		this.yRotO = yaw;
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
	}

	@Override
	protected float getRiddenSpeed(Player controller) {
		return (float) TamedJiao.rideSpeed(tier());
	}

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
		super.positionRider(passenger, moveFunction);
		if (passenger instanceof LivingEntity living) {
			living.yBodyRot = this.getYRot();
		}
	}

	// ================================================================== misc behaviour

	@Override
	protected Component getTypeName() {
		return kind().displayName();
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		// a Jiao shrugs off mortal potions; Slowness from the coil's own logic never targets itself
		return isTamed() && effect.getEffect().value().isBeneficial();
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
		if (isTamed() && this.level() instanceof ServerLevel level) {
			TamedJiao.knockedOut(level, this);
		} else {
			this.discard();
		}
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
	public boolean shouldRenderAtSqrDistance(double distance) {
		// the body is some 25 blocks long: seen from far away like a boss
		return distance < 256.0D * 256.0D;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return isTamed() ? SoundEvents.ENDER_DRAGON_AMBIENT : SoundEvents.ENDER_DRAGON_GROWL;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENDER_DRAGON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENDER_DRAGON_DEATH;
	}

	@Override
	public int getAmbientSoundInterval() {
		return isTamed() ? 600 : 260;
	}

	@Override
	protected float getSoundVolume() {
		return isTamed() ? 0.7F : 2.5F;
	}

	@Override
	public float getVoicePitch() {
		return 1.25F + 0.1F * kind().ordinal();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		// never written to disk (noSave); only /data and debugging read this
		super.addAdditionalSaveData(output);
		output.putString("naga_kind", kind().id());
		output.putString("naga_mode", mode.name());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
	}
}
