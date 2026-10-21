package dev.kultivator.dunia.reruntuhan;

import dev.kultivator.musuh.RogueTargets;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.NoGrief;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The shared body of module R's fighters (DESIGN 34.5.5-34.5.7): the Boneka Batu guardian, the mirror shadow, the
 * demon kings and their lesser demons, the ten floor guardians. A {@link Monster} that is never saved (types are
 * {@code noSave}, {@link #TAG} is refused in {@code ALLOW_LOAD}), never despawns by itself (its {@link Brain} removes
 * it), never drops loot or picks anything up, never gets a custom name, carries {@link NoGrief#TAG} and never changes a
 * block: every skill is an entity effect. Its damage intake per second is clamped to {@link #intakeCap} of its max
 * health (the purba rule), its outgoing damage is scaled by {@link #damageFactor} (re-lit seal pillars).
 * <p>
 * Synced for the client: kind, tier, the running skill and the tick it started (telegraph and strike animations),
 * and flags. The server-only {@link Brain} plays the fight.
 */
public abstract class RCreature extends Monster {

	public static final String TAG = "kultivator.reruntuhan_foe";

	/** A skill is being telegraphed (the client brightens the body). */
	public static final int FLAG_TELEGRAPH = 1;
	/** Enraged (second phase). */
	public static final int FLAG_ENRAGED = 2;
	/** Rising from the ground / the pit (invulnerable, no attacks). */
	public static final int FLAG_RISING = 4;
	/** Translucent (the mirror shadow, an illusion). */
	public static final int FLAG_GHOST = 8;

	private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(RCreature.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_TIER = SynchedEntityData.defineId(RCreature.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_SKILL = SynchedEntityData.defineId(RCreature.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_SKILL_AT = SynchedEntityData.defineId(RCreature.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_FLAGS = SynchedEntityData.defineId(RCreature.class, EntityDataSerializers.INT);

	/** What drives a fighter (server only). */
	public interface Brain {
		/** Every server tick while the creature lives. */
		void tick(RCreature creature, ServerLevel level);

		/** The creature died (rewards, doors). */
		default void died(RCreature creature, ServerLevel level, DamageSource source) {
		}

		/** A damage share for rewards ({@code amount} after the clamp). */
		default void damaged(RCreature creature, ServerLevel level, DamageSource source, float amount) {
		}

		/** A multiplier of the damage it takes (a hardened shell). */
		default double incoming(RCreature creature) {
			return 1.0D;
		}
	}

	protected @Nullable Brain brain;
	/** Share of max health it may lose per second (0 = no clamp). */
	protected double intakeCap;
	/** Outgoing skill damage multiplier. */
	protected double damageFactor = 1.0D;
	private float takenThisSecond;
	private long secondStart;
	private int alone;
	private boolean finished;

	protected RCreature(EntityType<? extends RCreature> type, Level level) {
		super(type, level);
		this.xpReward = 0;
		this.setCanPickUpLoot(false);
		this.setPersistenceRequired();
		this.addTag(NoGrief.TAG);
		this.addTag(TAG);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_KIND, 0);
		builder.define(DATA_TIER, 1);
		builder.define(DATA_SKILL, 0);
		builder.define(DATA_SKILL_AT, 0);
		builder.define(DATA_FLAGS, 0);
	}

	// ================================================================== synced values

	public int kind() {
		return entityData.get(DATA_KIND);
	}

	public void setKind(int kind) {
		entityData.set(DATA_KIND, kind);
		refreshDimensions();
	}

	public int tier() {
		return entityData.get(DATA_TIER);
	}

	public void setTier(int tier) {
		entityData.set(DATA_TIER, tier);
	}

	/** The running skill (0 none) and the tick count it started at. */
	public int skill() {
		return entityData.get(DATA_SKILL);
	}

	public int skillAge() {
		return this.tickCount - entityData.get(DATA_SKILL_AT);
	}

	public void setSkill(int skill) {
		entityData.set(DATA_SKILL, skill);
		entityData.set(DATA_SKILL_AT, this.tickCount);
	}

	public int flags() {
		return entityData.get(DATA_FLAGS);
	}

	public boolean has(int flag) {
		return (flags() & flag) != 0;
	}

	public void setFlag(int flag, boolean on) {
		int now = flags();
		int next = on ? now | flag : now & ~flag;
		if (next != now) {
			entityData.set(DATA_FLAGS, next);
		}
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_KIND.equals(accessor)) {
			refreshDimensions();
		}
	}

	public void setBrain(@Nullable Brain brain) {
		this.brain = brain;
	}

	public @Nullable Brain brain() {
		return brain;
	}

	public void setIntakeCap(double share) {
		this.intakeCap = Math.max(0.0D, share);
	}

	public void setDamageFactor(double factor) {
		this.damageFactor = Math.max(0.0D, factor);
	}

	public double damageFactor() {
		return damageFactor;
	}

	/** Sets a base attribute (health, attack, armour, speed) and, for health, fills it. */
	public void setBase(Holder<Attribute> attribute, double value) {
		AttributeInstance instance = this.getAttribute(attribute);
		if (instance != null && Double.isFinite(value)) {
			instance.setBaseValue(value);
		}
	}

	// ================================================================== behaviour

	@Override
	protected void registerGoals() {
		// Moved by its Brain (and the subclass's few goals).
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		Brain current = brain;
		if (current == null) {
			if (++alone > 40) {
				// A stray fighter (its fight ended, the server restarted it from nowhere) leaves.
				this.discard();
			}
			return;
		}
		alone = 0;
		try {
			current.tick(this, level);
		} catch (RuntimeException exception) {
			dev.kultivator.Kultivator.LOGGER.error("Kultivator: a ruin fighter's brain failed; it leaves", exception);
			this.discard();
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (has(FLAG_RISING) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		float amount = damage;
		if (brain != null && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			amount = (float) (amount * Math.max(0.0D, brain.incoming(this)));
		}
		if (intakeCap > 0.0D && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			long now = level.getGameTime();
			if (now - secondStart >= 20L) {
				secondStart = now;
				takenThisSecond = 0.0F;
			}
			float cap = (float) (getMaxHealth() * intakeCap);
			float room = Math.max(0.0F, cap - takenThisSecond);
			if (room <= 0.0F) {
				return false;
			}
			amount = Math.min(amount, room);
			takenThisSecond += amount;
		}
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && brain != null) {
			try {
				brain.damaged(this, level, source, amount);
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.warn("Kultivator: a ruin fighter's damage hook failed", exception);
			}
		}
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!finished && this.level() instanceof ServerLevel level && brain != null) {
			finished = true;
			try {
				brain.died(this, level, source);
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.error("Kultivator: a ruin fighter's death hook failed", exception);
			}
		}
	}

	// ================================================================== skill helpers (entities only)

	/** Every living thing this fighter may hit within {@code radius} (horizontal) of {@code centre}, ±{@code height}. */
	public List<LivingEntity> targetsAround(Vec3 centre, double radius, double height) {
		AABB box = new AABB(centre, centre).inflate(radius, height, radius);
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && mayHit(e))) {
			double dx = e.getX() - centre.x;
			double dz = e.getZ() - centre.z;
			if (dx * dx + dz * dz <= radius * radius) {
				out.add(e);
			}
		}
		return out;
	}

	/** Targets within {@code width} of the segment {@code from}-{@code to}. */
	public List<LivingEntity> targetsAlong(Vec3 from, Vec3 to, double width) {
		AABB box = new AABB(from, to).inflate(width + 1.0D);
		List<LivingEntity> out = new ArrayList<>();
		Vec3 ab = to.subtract(from);
		double len2 = Math.max(1.0E-6D, ab.lengthSqr());
		for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && mayHit(e))) {
			Vec3 p = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D);
			double t = Mth.clamp(p.subtract(from).dot(ab) / len2, 0.0D, 1.0D);
			if (p.distanceToSqr(from.add(ab.scale(t))) <= width * width) {
				out.add(e);
			}
		}
		return out;
	}

	/** True for the targets of DESIGN 25.1.2 (survival players, disciples, beasts, golems) and never another foe of R. */
	public boolean mayHit(@Nullable LivingEntity target) {
		if (target == null || target instanceof RCreature || target.entityTags().contains(TAG)) {
			return false;
		}
		return RogueTargets.mayHit(this, target);
	}

	/** Deals {@code amount} (× {@link #damageFactor}) as this fighter's attack; true when it landed. */
	public boolean strike(ServerLevel level, LivingEntity victim, float amount) {
		float dealt = (float) (amount * damageFactor);
		if (!(dealt > 0.0F) || !Float.isFinite(dealt)) {
			return false;
		}
		if (!(victim instanceof Player)) {
			victim.addTag(NoGrief.TAG);
		}
		return victim.hurtServer(level, level.damageSources().mobAttack(this), dealt);
	}

	/** Pushes {@code entity} (synced; never a block). */
	public static void push(LivingEntity entity, Vec3 velocity) {
		entity.push(velocity.x, velocity.y, velocity.z);
		entity.needsSync = true;
		entity.resetFallDistance();
	}

	/** The nearest target within {@code range}, or null. */
	public @Nullable LivingEntity nearestTarget(double range) {
		LivingEntity best = null;
		double bestSq = range * range;
		for (LivingEntity e : targetsAround(this.position(), range, range)) {
			double d = e.distanceToSqr(this);
			if (d < bestSq && (!(e instanceof Player player) || !player.isCreative())) {
				bestSq = d;
				best = e;
			}
		}
		return best;
	}

	/** Flat unit vector toward {@code to} (+z when they meet). */
	public static Vec3 flat(Vec3 from, Vec3 to) {
		Vec3 d = new Vec3(to.x - from.x, 0.0D, to.z - from.z);
		return d.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : d.normalize();
	}

	// ================================================================== inert where it should be

	@Override
	public void setCustomName(@Nullable Component name) {
		// Never: formations and techniques treat custom-named mobs as pets.
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
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	protected boolean canRide(Entity vehicle) {
		return false;
	}

	@Override
	public boolean canUsePortal(boolean ignorePassenger) {
		return false;
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
	}

	/** Bosses: the tags that make the 7b kill-cam and the technique boss rules see it. */
	protected void markBoss() {
		this.addTag(TechniquePower.TAG_BOSS);
	}
}
