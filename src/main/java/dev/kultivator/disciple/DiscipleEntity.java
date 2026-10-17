package dev.kultivator.disciple;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.fx.Fx;
import dev.kultivator.trial.HeartDemonEntity;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.Targeting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * A disciple (Murid, DESIGN 18): a humanoid follower drawn with the player model and one of the default player skins.
 * It belongs to one master, cultivates on its own ({@link DiscipleData} / {@link DiscipleCultivation}), obeys four
 * orders ({@link Mode}), fights only what {@link Targeting#isHostileTo} lets its master hurt, sits down cross-legged to
 * meditate when idle and is saved with the world. It never changes a block: it opens no doors, picks up nothing, presses
 * no plates, tramples nothing ({@link NoGrief#TAG}), is not touched by blocks at all and travels through no portal. Death
 * leaves only its Soul Tablet ({@link Disciples#onDeath}).
 */
public class DiscipleEntity extends PathfinderMob implements OwnableEntity {

	/** Bits of {@link #syncedFlags()}. */
	public static final int FLAG_MEDITATING = 1;
	public static final int FLAG_BREAKING_THROUGH = 2;
	public static final int FLAG_FIGHTING = 4;
	public static final int FLAG_DEVIATING = 8;
	/** The default player skins a disciple can wear (index 0..8). */
	public static final int SKIN_COUNT = 9;
	/** Key of {@link DiscipleData#save()} in the entity's saved data. */
	public static final String DATA_KEY = "kultivator_disciple";

	/** Every disciple's state is looked at this often (ticks). */
	static final int STATE_INTERVAL = 10;
	/** The master standing still this long lets a following disciple sit down and meditate (ticks). */
	private static final int OWNER_STILL_TICKS = 100;
	/** Out of combat (not hurt, not attacking, no target) it heals 1 health this often (ticks). */
	private static final int REGEN_INTERVAL = 60;
	private static final int COMBAT_MEMORY = 100;
	/** The bow before a released disciple vanishes (ticks). */
	private static final int RELEASE_TICKS = 40;

	/** The orders a master can give. The id is saved and used by commands and the screen. */
	public enum Mode {
		/** Walks after the master (teleports when far), defends the master. */
		FOLLOW("follow"),
		/** Guards the spot where the order was given and fights monsters within 12 blocks of it. */
		STAY("stay"),
		/** Sits at the spot and cultivates faster; fights only when hurt. */
		MEDITATE("meditate"),
		/** Follows and attacks whatever the master attacks or whatever attacks the master. */
		ASSIST("assist");

		private final String id;

		Mode(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		/** True for the orders that keep the disciple at the master's side. */
		public boolean follows() {
			return this == FOLLOW || this == ASSIST;
		}

		/** True for the orders bound to a spot. */
		public boolean guardsPost() {
			return this == STAY || this == MEDITATE;
		}

		public Component displayName() {
			return Component.translatable("kultivator.disciples.mode." + id);
		}

		/** Null when unknown. */
		public static @Nullable Mode byId(String id) {
			for (Mode mode : values()) {
				if (mode.id.equals(id)) {
					return mode;
				}
			}
			return null;
		}
	}

	private static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> DATA_OWNER =
			SynchedEntityData.defineId(DiscipleEntity.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);
	private static final EntityDataAccessor<String> DATA_OWNER_NAME = SynchedEntityData.defineId(DiscipleEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Integer> DATA_REALM = SynchedEntityData.defineId(DiscipleEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_LAYER = SynchedEntityData.defineId(DiscipleEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_STAGE = SynchedEntityData.defineId(DiscipleEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_DAO = SynchedEntityData.defineId(DiscipleEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ROOT_COLOR = SynchedEntityData.defineId(DiscipleEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_FLAGS = SynchedEntityData.defineId(DiscipleEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_SKIN = SynchedEntityData.defineId(DiscipleEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_SLIM = SynchedEntityData.defineId(DiscipleEntity.class, EntityDataSerializers.BOOLEAN);

	private DiscipleData data = new DiscipleData();
	private Mode mode = Mode.FOLLOW;
	/** The guarded / meditation spot of STAY and MEDITATE. */
	private @Nullable BlockPos post;
	/** Stays the same through death and revival (the entity UUID may change). */
	private @Nullable UUID soulId;
	/** Raised by one at every revival, so an old copy of a Soul Tablet cannot bring the soul back twice. */
	private int life;
	private boolean meditating;
	private int lastHurtTick = -10000;
	private int lastAttackTick = -10000;
	private int regenTimer;
	private @Nullable Vec3 lastOwnerPos;
	private int ownerStillTicks;
	/** {@code tickCount} when it was last seen within 24 blocks of its master in the same dimension. */
	private int nearOwnerTick = -10000;
	private int releaseTicks;
	private @Nullable BlockPos lastSafePos;

	public DiscipleEntity(EntityType<? extends DiscipleEntity> type, Level level) {
		super(type, level);
		this.xpReward = 0;
		this.setCanPickUpLoot(false);
		this.setPersistenceRequired();
		this.addTag(NoGrief.TAG);
		this.getNavigation().setCanOpenDoors(false);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_OWNER, Optional.empty());
		builder.define(DATA_OWNER_NAME, "");
		builder.define(DATA_REALM, 0);
		builder.define(DATA_LAYER, 0);
		builder.define(DATA_STAGE, 0);
		builder.define(DATA_DAO, -1);
		builder.define(DATA_ROOT_COLOR, 0xCFE8FF);
		builder.define(DATA_FLAGS, 0);
		builder.define(DATA_SKIN, 0);
		builder.define(DATA_SLIM, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new DiscipleAi.HoldStillGoal(this));
		this.goalSelector.addGoal(2, new DiscipleAi.RetreatGoal(this));
		this.goalSelector.addGoal(3, new DiscipleAi.CombatGoal(this));
		this.goalSelector.addGoal(4, new DiscipleAi.ReturnToPostGoal(this));
		this.goalSelector.addGoal(5, new DiscipleAi.FollowMasterGoal(this));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new DiscipleAi.ChooseTargetGoal(this));
	}

	// ================================================================== API (DESIGN 18.1)

	/** The disciple's cultivation (server side; the client only has a default copy and reads the synced values). */
	public DiscipleData data() {
		return data;
	}

	/** Replaces the cultivation data (recruitment, revival, debug) and syncs it. */
	public void setData(DiscipleData data) {
		this.data = data;
		refreshSynced();
	}

	/** The master when online (server side only; null on the client and while the master is offline). */
	public @Nullable ServerPlayer owner() {
		UUID id = ownerId();
		if (id == null || !(this.level() instanceof ServerLevel level)) {
			return null;
		}
		return level.getServer().getPlayerList().getPlayer(id);
	}

	/** The master's UUID (synced, so the client knows it too); null only for a broken disciple without a master. */
	public @Nullable UUID ownerId() {
		return this.entityData.get(DATA_OWNER).map(EntityReference::getUUID).orElse(null);
	}

	public boolean isOwnedBy(@Nullable Entity entity) {
		UUID id = ownerId();
		return entity != null && id != null && id.equals(entity.getUUID());
	}

	@Override
	public @Nullable EntityReference<LivingEntity> getOwnerReference() {
		return this.entityData.get(DATA_OWNER).orElse(null);
	}

	public Mode mode() {
		return mode;
	}

	/** The spot STAY and MEDITATE keep to (null for the other orders). */
	public @Nullable BlockPos post() {
		return post;
	}

	/**
	 * Gives an order. STAY and MEDITATE take the disciple's current spot as their post; MEDITATE drops the current target
	 * (it only fights back when hurt).
	 */
	public void setMode(Mode mode) {
		this.mode = mode;
		this.post = mode.guardsPost() ? this.blockPosition() : null;
		if (mode == Mode.MEDITATE) {
			this.setTarget(null);
		}
		this.ownerStillTicks = 0;
		this.getNavigation().stop();
	}

	/**
	 * True while it sits cross-legged and cultivates: meditating at its post, idle at its guard post, or waiting beside a
	 * master who stands still (or is away). Synced as flag 1 for the client.
	 */
	public boolean isMeditatingNow() {
		return this.level().isClientSide() ? (syncedFlags() & FLAG_MEDITATING) != 0 : meditating;
	}

	/**
	 * Copies realm, layer, stage, Dao, root colour and flags from {@link #data()} into the synced entity data and
	 * re-applies the permanent attribute bonuses of its cultivation ({@code kultivator:disciple_health/attack/speed}).
	 */
	public void refreshSynced() {
		Rank rank = data.rank();
		this.entityData.set(DATA_REALM, rank.realm().ordinal());
		this.entityData.set(DATA_LAYER, rank.layer());
		this.entityData.set(DATA_STAGE, rank.stage());
		this.entityData.set(DATA_DAO, data.daoIndex());
		this.entityData.set(DATA_ROOT_COLOR, data.rootColor());
		syncFlags();
		if (!this.level().isClientSide()) {
			applyBonuses();
		}
	}

	public int skinIndex() {
		return Math.floorMod(this.entityData.get(DATA_SKIN), SKIN_COUNT);
	}

	public boolean slimSkin() {
		return this.entityData.get(DATA_SLIM);
	}

	public int syncedRealm() {
		return this.entityData.get(DATA_REALM);
	}

	public int syncedLayer() {
		return this.entityData.get(DATA_LAYER);
	}

	public int syncedStage() {
		return this.entityData.get(DATA_STAGE);
	}

	/** The Dao ordinal, -1 for none. */
	public int syncedDao() {
		return this.entityData.get(DATA_DAO);
	}

	public int syncedRootColor() {
		return this.entityData.get(DATA_ROOT_COLOR);
	}

	/** {@link #FLAG_MEDITATING}, {@link #FLAG_BREAKING_THROUGH}, {@link #FLAG_FIGHTING}, {@link #FLAG_DEVIATING}. */
	public int syncedFlags() {
		return this.entityData.get(DATA_FLAGS);
	}

	/** The master's name (synced, for the screen and others' "disciple of" line). */
	public String ownerName() {
		return this.entityData.get(DATA_OWNER_NAME);
	}

	// ================================================================== identity (used by Disciples)

	/** The soul's id: stays the same through death and revival. */
	public UUID soulId() {
		return soulId != null ? soulId : this.getUUID();
	}

	public int life() {
		return life;
	}

	/** Binds a freshly created disciple to its master: data, name, skin, the FOLLOW order and full health. */
	void setupNew(ServerPlayer master, DiscipleData fresh, Component name, int skin, boolean slim) {
		bindTo(master);
		this.entityData.set(DATA_SKIN, Math.floorMod(skin, SKIN_COUNT));
		this.entityData.set(DATA_SLIM, slim);
		this.soulId = this.getUUID();
		this.life = 0;
		this.mode = Mode.FOLLOW;
		this.post = null;
		this.setCustomName(name);
		setData(fresh);
		this.setHealth(this.getMaxHealth());
	}

	private void bindTo(ServerPlayer master) {
		this.entityData.set(DATA_OWNER, Optional.of(EntityReference.<LivingEntity>of(master.getUUID())));
		this.entityData.set(DATA_OWNER_NAME, master.getName().getString());
	}

	/** Everything a Soul Tablet keeps: soul, life, master, name, skin and the whole cultivation. */
	CompoundTag writeTablet() {
		CompoundTag tag = new CompoundTag();
		tag.store("soul", UUIDUtil.CODEC, soulId());
		tag.putInt("life", life);
		UUID owner = ownerId();
		if (owner != null) {
			tag.store("owner", UUIDUtil.CODEC, owner);
		}
		tag.putString("owner_name", ownerName());
		tag.putInt("skin", skinIndex());
		tag.putBoolean("slim", slimSkin());
		Component name = this.getCustomName();
		if (name != null) {
			tag.store("name", ComponentSerialization.CODEC, name);
		}
		tag.putString("mode", mode.id());
		tag.put("disciple", data.save());
		return tag;
	}

	/**
	 * Brings the soul of a tablet ({@link #writeTablet()}) back in this new body: same soul, next life, the master who
	 * calls it, its name, skin and cultivation, at full health and following its master.
	 */
	void restoreFromTablet(CompoundTag tag, ServerPlayer master) {
		bindTo(master);
		this.soulId = tag.read("soul", UUIDUtil.CODEC).orElse(this.getUUID());
		this.life = tag.getIntOr("life", 0) + 1;
		this.entityData.set(DATA_SKIN, Math.floorMod(tag.getIntOr("skin", 0), SKIN_COUNT));
		this.entityData.set(DATA_SLIM, tag.getBooleanOr("slim", false));
		tag.read("name", ComponentSerialization.CODEC).ifPresent(this::setCustomName);
		this.mode = Mode.FOLLOW;
		this.post = null;
		setData(DiscipleData.load(tag.getCompoundOrEmpty("disciple")));
		this.setHealth(this.getMaxHealth());
	}

	/** True while it bows before leaving (released). */
	public boolean isReleasing() {
		return releaseTicks > 0;
	}

	/** Released by its master: it stops, bows for 2 s and vanishes. */
	void startRelease() {
		this.releaseTicks = RELEASE_TICKS;
		this.setTarget(null);
		this.getNavigation().stop();
		this.meditating = false;
		syncFlags();
	}

	/** True when it was within 24 blocks of its master during the last 2 s (it follows them across dimensions). */
	boolean wasNearOwner() {
		return this.tickCount - nearOwnerTick <= 40;
	}

	/** Where it last stood on safe ground (its tablet appears there when it dies in the void). */
	@Nullable BlockPos lastSafePos() {
		return lastSafePos;
	}

	/** It holds still: meditating, breaking through or bowing to leave. */
	boolean shouldHoldStill() {
		return meditating || data.breakingThrough() || releaseTicks > 0;
	}

	/** In combat recently: hurt, attacking or with a target. */
	boolean inCombat() {
		LivingEntity target = this.getTarget();
		return target != null && target.isAlive() || this.tickCount - lastHurtTick < COMBAT_MEMORY
				|| this.tickCount - lastAttackTick < COMBAT_MEMORY;
	}

	// ================================================================== who it may fight

	/**
	 * True when the disciple may attack {@code target}: only what {@link Targeting#isHostileTo} accepts for its master
	 * (monsters, mobs attacking the master, an accepted sparring partner); with the master offline only monsters that are
	 * nobody's pet. Never its master, a player outside sparring, a pet, a villager, a passive animal, any disciple, a heart
	 * demon, anything that cannot be seen as an enemy, nor the mobs whose fights change blocks ({@link #untouchable}).
	 */
	public boolean mayAttack(@Nullable LivingEntity target) {
		// Disciples never fight disciples (of anyone): that also keeps two disciples from asking each other forever. A heart
		// demon is a matter of one cultivator's heart (and immune to anyone else), invulnerable things are no enemies.
		if (target == null || target == this || target instanceof DiscipleEntity || !target.isAlive() || untouchable(target)
				|| target instanceof HeartDemonEntity || !target.canBeSeenAsEnemy()) {
			return false;
		}
		ServerPlayer owner = owner();
		if (owner != null && target == owner) {
			return false;
		}
		if (target instanceof Player) {
			return owner != null && Targeting.isHostileTo(target, owner);
		}
		if (owner != null) {
			return Targeting.isHostileTo(target, owner);
		}
		return target instanceof Enemy && !isPet(target);
	}

	/**
	 * Mobs a disciple never fights because the fight would change blocks: creepers explode, ghasts and blazes shoot fire,
	 * breezes' wind charges flip doors and levers, ravagers break leaves while chasing, a hurt silverfish breaks infested
	 * blocks, a heart-bound creaking grows resin and the Wither breaks blocks after every hit.
	 */
	public static boolean untouchable(@Nullable Entity entity) {
		return entity instanceof Creeper || entity instanceof Ghast || entity instanceof Blaze || entity instanceof Breeze
				|| entity instanceof Ravager || entity instanceof Silverfish || entity instanceof WitherBoss
				|| entity instanceof Creaking creaking && creaking.isHeartBound();
	}

	private static boolean isPet(LivingEntity entity) {
		return entity instanceof OwnableEntity ownable && ownable.getOwnerReference() != null;
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return mayAttack(target) && super.canAttack(target);
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		super.setTarget(target);
		if (this.getTarget() != null && meditating) {
			meditating = false;
		}
		syncFlags();
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		if (!(target instanceof LivingEntity living) || !mayAttack(living)) {
			return false;
		}
		if (!(living instanceof Player)) {
			// The blow knocks it back: it must not land on farmland or turtle eggs, nor fly into a portal (blocks).
			living.addTag(NoGrief.TAG);
			living.setPortalCooldown();
		}
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			lastAttackTick = this.tickCount;
		}
		return hit;
	}

	@Override
	public boolean killedEntity(ServerLevel level, LivingEntity entity, DamageSource source) {
		DiscipleCultivation.onKill(this, entity);
		if (entity.getLastHurtByPlayer() == null) {
			// No player took part: no experience, so a sculk catalyst nearby does not bloom (it would spread sculk blocks).
			entity.skipDropExperience();
		}
		return super.killedEntity(level, entity, source);
	}

	// ================================================================== damage

	/**
	 * Its master, the master's other disciples and pets, and every player outside an accepted sparring duel with the
	 * master cannot hurt it (no griefing, no friendly fire); /kill still works.
	 */
	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		if (source.is(DamageTypes.GENERIC_KILL)) {
			return false;
		}
		if (releaseTicks > 0) {
			// Released: nothing interrupts the farewell.
			return true;
		}
		Entity attacker = source.getEntity();
		if (attacker != null && attacker != this && isFriendly(attacker)) {
			return true;
		}
		return super.isInvulnerableTo(level, source);
	}

	private boolean isFriendly(Entity attacker) {
		UUID owner = ownerId();
		if (attacker instanceof Player player) {
			if (owner != null && owner.equals(player.getUUID())) {
				return true;
			}
			ServerPlayer master = owner();
			return !(master != null && player instanceof ServerPlayer other && Targeting.sparring.test(master, other));
		}
		if (owner == null) {
			return false;
		}
		if (attacker instanceof DiscipleEntity other) {
			return owner.equals(other.ownerId());
		}
		if (attacker instanceof OwnableEntity pet) {
			EntityReference<LivingEntity> petOwner = pet.getOwnerReference();
			return petOwner != null && owner.equals(petOwner.getUUID());
		}
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt) {
			lastHurtTick = this.tickCount;
			if (meditating) {
				meditating = false;
				syncFlags();
			}
		}
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		boolean first = !this.isRemoved() && !this.dead;
		super.die(source);
		if (first && this.dead && this.level() instanceof ServerLevel level) {
			Disciples.onDeath(this, level);
		}
	}

	/** Nothing drops but the Soul Tablet ({@link Disciples#onDeath}): no loot, no equipment, no experience. */
	@Override
	protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
	}

	/** Killed by the Wither it leaves no wither rose block behind. */
	@Override
	protected void createWitherRose(@Nullable LivingEntity killer) {
	}

	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return false;
	}

	@Override
	public boolean shouldDropExperience() {
		return false;
	}

	/** Falling out of the world it comes back to its master when it can (else the void takes it, see onDeath). */
	@Override
	protected void onBelowWorld() {
		ServerPlayer owner = owner();
		if (owner != null && owner.level() == this.level() && !owner.isSpectator() && teleportNear(owner)) {
			return;
		}
		super.onBelowWorld();
	}

	// ================================================================== ticking

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide() || this.isRemoved() || !this.isAlive()) {
			return;
		}
		// Blocks do not act on it (isAffectedByBlocks), so water, rain and lava are handled here, on the entity only.
		if (this.isOnFire() && this.isInWaterOrRain()) {
			this.clearFire();
		}
		if (this.isInLava()) {
			this.lavaIgnite();
			this.lavaHurt();
		}
		if (releaseTicks > 0) {
			tickRelease();
			return;
		}
		DiscipleCultivation.tick(this);
	}

	/**
	 * Its delayed technique effects go with it (death, unload, another dimension, release), and a running breakthrough
	 * stops its clouds and vortex for everyone. The entity was already saved when it is unloaded, so a tribulation past its
	 * first strike still counts as failed when it loads again ({@link DiscipleData#save()}).
	 */
	@Override
	public void onRemoval(Entity.RemovalReason reason) {
		super.onRemoval(reason);
		if (this.level() instanceof ServerLevel level) {
			DiscipleTechniques.forget(this);
			if (data.breakingThrough()) {
				DiscipleBreakthrough.cancel(this, level, null);
			}
		}
	}

	private void tickRelease() {
		this.getNavigation().stop();
		// The bow: the head sinks towards the ground over half a second and stays low.
		this.setXRot(Math.min(60.0F, (RELEASE_TICKS - releaseTicks + 1) * 6.0F));
		if (--releaseTicks > 0 || !(this.level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 at = this.position();
		Fx.burst(level, at.add(0.0D, 1.0D, 0.0D), data.rootColor(), 40, 1.0F);
		Fx.pillar(level, at, data.rootColor(), 3.0F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.0F, 0.7F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.NEUTRAL, 0.8F, 1.2F);
		this.discard();
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if ((this.tickCount + this.getId()) % STATE_INTERVAL == 0) {
			updateState(level);
		}
	}

	/** Every 10 ticks: the master's stillness, target checks, meditation, healing, synced flags. Cheap. */
	private void updateState(ServerLevel level) {
		ServerPlayer owner = owner();
		boolean sameLevel = owner != null && owner.level() == level;
		if (sameLevel) {
			Vec3 pos = owner.position();
			ownerStillTicks = lastOwnerPos != null && pos.distanceToSqr(lastOwnerPos) < 0.04D ? ownerStillTicks + STATE_INTERVAL : 0;
			lastOwnerPos = pos;
			if (this.distanceToSqr(owner) <= DiscipleAi.TELEPORT_DISTANCE * DiscipleAi.TELEPORT_DISTANCE) {
				nearOwnerTick = this.tickCount;
			}
			String name = owner.getName().getString();
			if (!name.equals(ownerName())) {
				this.entityData.set(DATA_OWNER_NAME, name);
			}
		} else {
			lastOwnerPos = null;
			ownerStillTicks = 0;
		}

		LivingEntity target = this.getTarget();
		if (target != null && (!mayAttack(target) || !DiscipleAi.withinDuty(this, target, owner))) {
			this.setTarget(null);
		}

		boolean nowMeditating = computeMeditating(owner, sameLevel);
		if (nowMeditating != meditating) {
			meditating = nowMeditating;
			if (meditating) {
				this.getNavigation().stop();
			}
		}

		regenTimer += STATE_INTERVAL;
		if (regenTimer >= REGEN_INTERVAL) {
			regenTimer = 0;
			if (!inCombat() && this.getHealth() < this.getMaxHealth()) {
				this.heal(1.0F);
			}
		}

		if (this.onGround() && !this.isInWater() && !this.isInLava()) {
			lastSafePos = this.blockPosition();
		}
		syncFlags();
	}

	private boolean computeMeditating(@Nullable ServerPlayer owner, boolean sameLevel) {
		if (releaseTicks > 0 || data.breakingThrough() || this.getTarget() != null || !this.onGround() || this.isInWater()
				|| this.isPassenger() || this.tickCount - lastHurtTick < 60 || this.tickCount - lastAttackTick < 40) {
			return false;
		}
		return switch (mode) {
			case MEDITATE -> post != null && this.distanceToSqr(post.getX() + 0.5D, post.getY(), post.getZ() + 0.5D) <= 2.25D;
			case STAY -> post != null && this.distanceToSqr(post.getX() + 0.5D, post.getY(), post.getZ() + 0.5D) <= 4.0D
					&& this.getNavigation().isDone();
			case FOLLOW, ASSIST -> {
				if (!sameLevel) {
					// The master is offline or in another dimension: it waits where it is, cultivating.
					yield true;
				}
				yield this.distanceToSqr(owner) <= 64.0D && this.getNavigation().isDone()
						&& (ownerStillTicks >= OWNER_STILL_TICKS || Cultivators.get(owner).meditating());
			}
		};
	}

	private void syncFlags() {
		LivingEntity target = this.getTargetUnchecked();
		int flags = (meditating ? FLAG_MEDITATING : 0) | (target != null && target.isAlive() ? FLAG_FIGHTING : 0) | data.cultivationFlags();
		if (this.entityData.get(DATA_FLAGS) != flags) {
			this.entityData.set(DATA_FLAGS, flags);
		}
	}

	private void applyBonuses() {
		setModifier(Attributes.MAX_HEALTH, DiscipleEntities.HEALTH_ID, DiscipleCultivation.maxHealthBonus(data), AttributeModifier.Operation.ADD_VALUE);
		setModifier(Attributes.ATTACK_DAMAGE, DiscipleEntities.ATTACK_ID, DiscipleCultivation.attackBonus(data), AttributeModifier.Operation.ADD_VALUE);
		setModifier(Attributes.MOVEMENT_SPEED, DiscipleEntities.SPEED_ID, Math.min(1.0D, DiscipleCultivation.speedBonus(data)),
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		if (this.getHealth() > this.getMaxHealth()) {
			this.setHealth(this.getMaxHealth());
		}
	}

	private void setModifier(Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = this.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (!Double.isFinite(amount) || amount == 0.0D) {
			instance.removeModifier(id);
		} else {
			instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, operation));
		}
	}

	// ================================================================== moving it around

	/**
	 * Puts the disciple on a safe free spot 1.5-3 blocks from the master (read-only spot search, never in a portal) with
	 * a small burst of qi where it left and where it arrives. False when there is no such spot.
	 */
	public boolean teleportNear(ServerPlayer master) {
		if (!(this.level() instanceof ServerLevel level) || master.level() != level) {
			return false;
		}
		Vec3 spot = spotNear(level, master.position());
		if (spot == null) {
			return false;
		}
		Vec3 old = this.position();
		this.snapTo(spot.x, spot.y, spot.z, this.getYRot(), this.getXRot());
		arrived(level, old);
		return true;
	}

	/** Returns to its post when it was carried far away (read-only spot search). */
	boolean teleportToPost() {
		if (post == null || !(this.level() instanceof ServerLevel level) || !level.isLoaded(post)) {
			return false;
		}
		Vec3 spot = Targeting.safeSpot(level, this, new Vec3(post.getX() + 0.5D, post.getY(), post.getZ() + 0.5D));
		if (spot == null) {
			return false;
		}
		Vec3 old = this.position();
		this.snapTo(spot.x, spot.y, spot.z, this.getYRot(), this.getXRot());
		arrived(level, old);
		return true;
	}

	/**
	 * Follows the master into another dimension (the master just changed level): a direct teleport to a safe spot next to
	 * them, never through a portal (nothing is built). Returns the disciple in its new level, or null when it stayed.
	 */
	@Nullable DiscipleEntity followAcross(ServerPlayer master) {
		ServerLevel destination = master.level();
		if (destination == this.level() || this.isRemoved()) {
			return null;
		}
		Vec3 spot = spotNear(destination, master.position());
		if (spot == null) {
			return null;
		}
		Entity moved = this.teleport(new TeleportTransition(destination, spot, Vec3.ZERO, this.getYRot(), this.getXRot(), TeleportTransition.DO_NOTHING));
		if (moved instanceof DiscipleEntity disciple) {
			disciple.arrived(destination, null);
			return disciple;
		}
		return null;
	}

	/** A safe free spot for this disciple 1.5-3 blocks from {@code center} in loaded chunks (read-only), or null. */
	@Nullable Vec3 spotNear(ServerLevel level, Vec3 center) {
		for (int attempt = 0; attempt < 12; attempt++) {
			double angle = this.random.nextDouble() * Math.PI * 2.0D;
			double radius = 1.5D + this.random.nextDouble() * 1.5D;
			Vec3 near = center.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
			if (!level.isLoaded(BlockPos.containing(near))) {
				continue;
			}
			Vec3 spot = Targeting.safeSpot(level, this, near);
			if (spot != null) {
				return spot;
			}
		}
		return null;
	}

	private void arrived(ServerLevel level, @Nullable Vec3 from) {
		this.setPortalCooldown();
		this.getNavigation().stop();
		this.setDeltaMovement(Vec3.ZERO);
		this.resetFallDistance();
		int color = data.rootColor();
		if (from != null) {
			Fx.burst(level, from.add(0.0D, 1.0D, 0.0D), color, 12, 0.6F);
		}
		Vec3 at = this.position();
		Fx.burst(level, at.add(0.0D, 1.0D, 0.0D), color, 16, 0.7F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.NEUTRAL, 0.5F, 1.4F);
	}

	// ================================================================== saving

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.store(DATA_KEY, CompoundTag.CODEC, data.save());
		UUID owner = ownerId();
		if (owner != null) {
			output.store("Owner", UUIDUtil.CODEC, owner);
		}
		output.putString("OwnerName", ownerName());
		output.putString("Mode", mode.id());
		if (post != null) {
			output.store("Post", BlockPos.CODEC, post);
		}
		output.putInt("Skin", skinIndex());
		output.putBoolean("Slim", slimSkin());
		output.store("Soul", UUIDUtil.CODEC, soulId());
		output.putInt("Life", life);
		if (releaseTicks > 0) {
			output.putInt("Releasing", releaseTicks);
		}
		if (lastSafePos != null) {
			output.store("SafePos", BlockPos.CODEC, lastSafePos);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.data = input.read(DATA_KEY, CompoundTag.CODEC).map(DiscipleData::load)
				.orElseGet(() -> DiscipleData.fresh(this.getRandom()));
		input.read("Owner", UUIDUtil.CODEC).ifPresent(id -> this.entityData.set(DATA_OWNER, Optional.of(EntityReference.<LivingEntity>of(id))));
		this.entityData.set(DATA_OWNER_NAME, input.getStringOr("OwnerName", ""));
		Mode saved = Mode.byId(input.getStringOr("Mode", Mode.FOLLOW.id()));
		this.mode = saved == null ? Mode.FOLLOW : saved;
		this.post = input.read("Post", BlockPos.CODEC).orElse(null);
		if (this.mode.guardsPost() && this.post == null) {
			this.post = this.blockPosition();
		}
		this.entityData.set(DATA_SKIN, Math.floorMod(input.getIntOr("Skin", 0), SKIN_COUNT));
		this.entityData.set(DATA_SLIM, input.getBooleanOr("Slim", false));
		this.soulId = input.read("Soul", UUIDUtil.CODEC).orElse(null);
		this.life = Math.max(0, input.getIntOr("Life", 0));
		this.releaseTicks = Math.max(0, input.getIntOr("Releasing", 0));
		this.lastSafePos = input.read("SafePos", BlockPos.CODEC).orElse(null);
		this.addTag(NoGrief.TAG);
		this.setCanPickUpLoot(false);
		refreshSynced();
	}

	// ================================================================== never despawns, never changes the world

	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	@Override
	public void checkDespawn() {
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
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean startRiding(Entity entity, boolean force, boolean sendEventAndTriggers) {
		return false;
	}

	@Override
	public boolean canUsePortal(boolean ignorePassenger) {
		return false;
	}

	/** Pressure plates and tripwires never notice it. */
	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	/** Treads softly: no lit redstone ore, no cracked turtle eggs, no sculk vibrations from its steps. */
	@Override
	public boolean isSteppingCarefully() {
		return true;
	}

	/**
	 * Blocks never act on it: no {@code stepOn} and no {@code entityInside} (a burning disciple can never melt powder
	 * snow or empty a water cauldron, tilt a dripleaf or enter a portal). It is not hurt by cactus, berry bushes or fire
	 * blocks either, which suits a cultivator. Water and rain still put its fire out and lava still burns it: {@link #tick}
	 * does that on the entity alone.
	 */
	@Override
	protected boolean isAffectedByBlocks() {
		return false;
	}

	// ================================================================== voice: a young cultivator's

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PLAYER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PLAYER_DEATH;
	}
}
