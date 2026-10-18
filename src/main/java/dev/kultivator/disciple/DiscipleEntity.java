package dev.kultivator.disciple;

import dev.kultivator.Kultivator;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.fx.Fx;
import dev.kultivator.trial.HeartDemonEntity;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.Targeting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.tags.DamageTypeTags;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

	/**
	 * What kind of body this is (DESIGN 20.2.5). Only {@link #DISCIPLE} is saved, listed by {@link Disciples}, counted
	 * toward the limit or able to leave a Soul Tablet; the others are refused when they would load from disk.
	 */
	public enum Role {
		/** A master's disciple (phase 5). */
		DISCIPLE,
		/** A youth at the Sect Hall's acceptance day: no master, untouchable, strolls near its anchor. */
		CANDIDATE,
		/** A visiting rival for a duel: fights only its {@link DuelRules} partner. */
		RIVAL,
		/** A Void Realm phantom: fights only its {@link DuelRules} partner. */
		PHANTOM,
		/** A Divine Clone: allied with its source's master, fights monsters, follows its source. */
		CLONE;

		public static Role byOrdinal(int ordinal) {
			Role[] values = values();
			return ordinal < 0 || ordinal >= values.length ? DISCIPLE : values[ordinal];
		}
	}

	/**
	 * Activity ids of {@link #activity()} (DESIGN 20.2.5, 21.24). 1-8 and 10 hold their spot like STAY and fight only when hit
	 * (5 guards).
	 */
	public static final int ACTIVITY_NONE = 0;
	public static final int ACTIVITY_WATER = 1;
	public static final int ACTIVITY_GARDEN = 2;
	public static final int ACTIVITY_KITCHEN = 3;
	public static final int ACTIVITY_FARM = 4;
	public static final int ACTIVITY_FORMATION_GUARD = 5;
	public static final int ACTIVITY_TOWER = 6;
	public static final int ACTIVITY_VOID_BODY = 7;
	public static final int ACTIVITY_SPECTATOR = 8;
	public static final int ACTIVITY_DUELLIST = 9;
	/**
	 * Pekerja Bangunan, a sect builder (DESIGN 21.14, phase 6b): holds its work spot like 1-8 and fights only when hit, but
	 * the spot moves with the open component ({@code setActivity(ACTIVITY_BUILD, spot)} may be called again) and it never
	 * sits down to meditate there (it works). Session state like the other activities (the flow re-applies it).
	 */
	public static final int ACTIVITY_BUILD = 10;
	/**
	 * Penjaga Formasi, a sect formation keeper (DESIGN 24.2.8, phase 7c): holds its post beside a Mata Formasi like 1-8,
	 * may sit to meditate there, and survives a save like the duties and the formation guard (5 cannot be reused: the
	 * Formasi Agung frees any activity-5 disciple away from its lodestone).
	 */
	public static final int ACTIVITY_FORMATION_KEEPER = 11;

	/** {@link Mode#GUARD_HALL} patrols within this many blocks of its hall stele. */
	public static final double HALL_PATROL_RADIUS = 32.0D;
	/** Bits of {@link #syncedPower()} that module C (0-15) and module D (16-31) own. */
	public static final int POWER_MASK_C = 0x0000FFFF;
	public static final int POWER_MASK_D = 0xFFFF0000;

	/** The orders a master can give. The id is saved and used by commands and the screen. */
	public enum Mode {
		/** Walks after the master (teleports when far), defends the master. */
		FOLLOW("follow"),
		/** Guards the spot where the order was given and fights monsters within 12 blocks of it. */
		STAY("stay"),
		/** Sits at the spot and cultivates faster; fights only when hurt. */
		MEDITATE("meditate"),
		/** Follows and attacks whatever the master attacks or whatever attacks the master. */
		ASSIST("assist"),
		/** Patrols within 32 blocks of its master's hall stele and fights hostiles inside the territory (DESIGN 20.2.5). */
		GUARD_HALL("guard_hall");

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
	// Phase 6 (DESIGN 20.2.5).
	private static final EntityDataAccessor<Byte> DATA_ROLE = SynchedEntityData.defineId(DiscipleEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> DATA_ACTIVITY = SynchedEntityData.defineId(DiscipleEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> DATA_SECT_RANK = SynchedEntityData.defineId(DiscipleEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Integer> DATA_STARS = SynchedEntityData.defineId(DiscipleEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_TALENT = SynchedEntityData.defineId(DiscipleEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_POWER = SynchedEntityData.defineId(DiscipleEntity.class, EntityDataSerializers.INT);
	/** Transient attack modifier that carries the {@code damageFactor} hooks into a melee blow. */
	private static final Identifier MELEE_FACTOR_ID = Kultivator.id("disciple_melee_factor");

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

	// ---- phase 6 (DESIGN 20.2.5)
	private Role role = Role.DISCIPLE;
	/** Where a candidate strolls, a rival's ring, a clone's start (role specific). */
	private @Nullable BlockPos roleAnchor;
	private int activity;
	private @Nullable BlockPos activitySpot;
	/** A clone's source disciple (soul id), or null. */
	private @Nullable UUID cloneSource;
	/** Set by {@link Disciples#depart}: the body leaves without touching the master's list. */
	private boolean departed;
	private @Nullable CompoundTag departure;
	/** Attribute modifiers the {@code stats} hooks applied last ("attribute id|modifier id"), saved with the body. */
	private final Set<String> hookModifiers = new HashSet<>();
	/** GUARD_HALL: the hall stele (refreshed every 2 s), and the tick until which it rests between patrol legs. */
	private @Nullable BlockPos hallAnchor;
	private int patrolRestUntil;

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
		builder.define(DATA_ROLE, (byte) 0);
		builder.define(DATA_ACTIVITY, (byte) 0);
		builder.define(DATA_SECT_RANK, (byte) 0);
		builder.define(DATA_STARS, 0);
		builder.define(DATA_TALENT, 0);
		builder.define(DATA_POWER, 0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new DiscipleAi.HoldStillGoal(this));
		this.goalSelector.addGoal(2, new DiscipleAi.RetreatGoal(this));
		this.goalSelector.addGoal(3, new DiscipleAi.CombatGoal(this));
		this.goalSelector.addGoal(4, new DiscipleAi.ReturnToPostGoal(this));
		this.goalSelector.addGoal(5, new DiscipleAi.FollowMasterGoal(this));
		this.goalSelector.addGoal(6, new DiscipleAi.PatrolHallGoal(this));
		this.goalSelector.addGoal(6, new DiscipleAi.CandidateStrollGoal(this));
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
		// GUARD_HALL keeps the current spot as its fallback anchor while it has no active hall in this dimension.
		this.post = mode.guardsPost() || mode == Mode.GUARD_HALL ? this.blockPosition() : null;
		if (mode == Mode.MEDITATE) {
			this.setTarget(null);
		}
		this.ownerStillTicks = 0;
		this.patrolRestUntil = 0;
		if (mode == Mode.GUARD_HALL) {
			refreshHallAnchor();
		}
		this.getNavigation().stop();
	}

	// ================================================================== phase 6: roles, activities, synced values

	public Role role() {
		return role;
	}

	/**
	 * Gives this body its role (DESIGN 20.2.5) and the role's anchor: where a candidate strolls, a rival's ring centre,
	 * a clone's start. Roles other than DISCIPLE are never saved.
	 */
	public void setRole(Role role, @Nullable BlockPos anchor) {
		this.role = role == null ? Role.DISCIPLE : role;
		this.roleAnchor = anchor;
		this.entityData.set(DATA_ROLE, (byte) this.role.ordinal());
		if (this.role != Role.DISCIPLE) {
			this.meditating = false;
			this.setTarget(null);
		}
		this.getNavigation().stop();
		syncFlags();
	}

	/** The role's anchor (see {@link #setRole}), or null. */
	public @Nullable BlockPos roleAnchor() {
		return roleAnchor;
	}

	/** The current activity id ({@code ACTIVITY_*}, 0 none). */
	public int activity() {
		return activity;
	}

	/** The spot of an activity 1-8 or 10 (its post), or null. */
	public @Nullable BlockPos activitySpot() {
		return activitySpot;
	}

	/**
	 * Sets the activity (0 none, 1 water, 2 garden, 3 kitchen, 4 farm, 5 formation guard, 6 tower, 7 void body,
	 * 8 spectator, 9 duellist, 10 builder). While the id is 1-8 or 10 the disciple holds {@code spot} like STAY and fights
	 * only when hit (5 guards the spot). Calling it again with 10 while already building only moves the work spot (a fight
	 * it is in goes on). Activities are session state for the module that set them (not saved).
	 */
	public void setActivity(int id, @Nullable BlockPos spot) {
		int next = Math.max(0, Math.min(127, id));
		if (next == ACTIVITY_BUILD && this.activity == ACTIVITY_BUILD) {
			BlockPos moved = spot != null ? spot.immutable() : this.activitySpot;
			if (moved != null && !moved.equals(this.activitySpot)) {
				this.activitySpot = moved;
				if (this.getTarget() == null) {
					this.getNavigation().stop();
				}
			}
			return;
		}
		this.activity = next;
		this.activitySpot = holdsActivitySpot() ? (spot != null ? spot : this.blockPosition()) : spot;
		this.entityData.set(DATA_ACTIVITY, (byte) this.activity);
		if (holdsActivitySpot() && this.activity != ACTIVITY_FORMATION_GUARD) {
			this.setTarget(null);
		}
		this.ownerStillTicks = 0;
		this.getNavigation().stop();
	}

	/** True while an activity 1-8, 10 (builder) or 11 (formation keeper) binds the disciple to its spot. */
	public boolean holdsActivitySpot() {
		return activity >= ACTIVITY_WATER && activity <= ACTIVITY_SPECTATOR || activity == ACTIVITY_BUILD
				|| activity == ACTIVITY_FORMATION_KEEPER;
	}

	/** A clone's source disciple (its soul id), or null. */
	public @Nullable UUID cloneSource() {
		return cloneSource;
	}

	public void setCloneSource(@Nullable UUID source) {
		this.cloneSource = source;
	}

	/** True once {@link Disciples#depart} took this body out of the world. */
	public boolean departed() {
		return departed;
	}

	/** The tag {@link Disciples#depart} made for this body (null before it departed). */
	@Nullable CompoundTag departure() {
		return departure;
	}

	void markDeparted(CompoundTag tag) {
		this.departed = true;
		this.departure = tag;
	}

	/** Role as the client sees it. */
	public Role syncedRole() {
		return Role.byOrdinal(this.entityData.get(DATA_ROLE));
	}

	public int syncedActivity() {
		return this.entityData.get(DATA_ACTIVITY);
	}

	public int syncedSectRank() {
		return this.entityData.get(DATA_SECT_RANK);
	}

	public int syncedStars() {
		return this.entityData.get(DATA_STARS);
	}

	/** Physique (bits 0-3), soul colour (bits 4-6) and special type (bits 7-8); see {@link #packTalent}. */
	public int syncedTalent() {
		return this.entityData.get(DATA_TALENT);
	}

	/** Visual power flags: bits 0-15 belong to module C, bits 16-31 to module D. */
	public int syncedPower() {
		return this.entityData.get(DATA_POWER);
	}

	public void setSyncedSectRank(int rank) {
		byte value = (byte) Math.max(0, Math.min(127, rank));
		if (this.entityData.get(DATA_SECT_RANK) != value) {
			this.entityData.set(DATA_SECT_RANK, value);
		}
	}

	public void setSyncedStars(int stars) {
		int value = Math.max(0, stars);
		if (this.entityData.get(DATA_STARS) != value) {
			this.entityData.set(DATA_STARS, value);
		}
	}

	public void setSyncedTalent(int talent) {
		if (this.entityData.get(DATA_TALENT) != talent) {
			this.entityData.set(DATA_TALENT, talent);
		}
	}

	/** Sets the bits of {@code mask} ({@link #POWER_MASK_C} or {@link #POWER_MASK_D}) to {@code bits}, keeps the others. */
	public void setSyncedPower(int mask, int bits) {
		int old = this.entityData.get(DATA_POWER);
		int value = old & ~mask | bits & mask;
		if (old != value) {
			this.entityData.set(DATA_POWER, value);
		}
	}

	/** The synced talent int: physique 4 bits, soul colour 3 bits, special type 2 bits. */
	public static int packTalent(int physique, int soulColor, int special) {
		return physique & 0xF | (soulColor & 0x7) << 4 | (special & 0x3) << 7;
	}

	public static int talentPhysique(int talent) {
		return talent & 0xF;
	}

	public static int talentSoulColor(int talent) {
		return talent >> 4 & 0x7;
	}

	public static int talentSpecial(int talent) {
		return talent >> 7 & 0x3;
	}

	/**
	 * Where GUARD_HALL patrols: the master's active hall stele in this dimension, else the spot of the order. Null for
	 * other orders.
	 */
	public @Nullable BlockPos guardAnchor() {
		if (mode != Mode.GUARD_HALL) {
			return null;
		}
		return hallAnchor != null ? hallAnchor : post;
	}

	/** Looks the master's hall up again (cheap map lookup by the hall module). */
	void refreshHallAnchor() {
		hallAnchor = null;
		UUID owner = ownerId();
		if (owner == null || mode != Mode.GUARD_HALL || this.level().isClientSide()) {
			return;
		}
		dev.kultivator.sect.hall.Halls.HallView hall = dev.kultivator.sect.hall.Halls.hallOf(owner);
		if (hall != null && hall.active() && hall.dim() != null && hall.dim().equals(this.level().dimension())) {
			hallAnchor = hall.pos();
		}
	}

	/** True when {@code pos} is inside the hall territory GUARD_HALL defends (or near its fallback anchor). */
	boolean inGuardedTerritory(Vec3 pos) {
		BlockPos anchor = guardAnchor();
		if (anchor == null) {
			return false;
		}
		UUID owner = ownerId();
		if (hallAnchor != null && owner != null && dev.kultivator.sect.hall.Halls.inTerritory(owner, this.level(), pos)) {
			return true;
		}
		double dx = pos.x - (anchor.getX() + 0.5D);
		double dz = pos.z - (anchor.getZ() + 0.5D);
		return dx * dx + dz * dz <= HALL_PATROL_RADIUS * HALL_PATROL_RADIUS && Math.abs(pos.y - anchor.getY()) <= 32.0D;
	}

	int patrolRestUntil() {
		return patrolRestUntil;
	}

	void setPatrolRestUntil(int tick) {
		this.patrolRestUntil = tick;
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
		bindTo(master.getUUID(), master.getName().getString());
	}

	/** Sets the master (null: none, as for a candidate or a rival). */
	void bindTo(@Nullable UUID master, String masterName) {
		this.entityData.set(DATA_OWNER, master == null ? Optional.empty() : Optional.of(EntityReference.<LivingEntity>of(master)));
		this.entityData.set(DATA_OWNER_NAME, masterName == null ? "" : masterName);
	}

	/** Gives a candidate, rival, phantom or clone its looks (no master is bound here). */
	void setupBody(DiscipleData fresh, @Nullable Component name, int skin, boolean slim) {
		this.entityData.set(DATA_SKIN, Math.floorMod(skin, SKIN_COUNT));
		this.entityData.set(DATA_SLIM, slim);
		this.soulId = this.getUUID();
		this.life = 0;
		this.mode = Mode.FOLLOW;
		this.post = null;
		if (name != null) {
			this.setCustomName(name);
		}
		setData(fresh);
		this.setHealth(this.getMaxHealth());
	}

	/**
	 * A candidate accepted in place (DESIGN 20.2.7): it becomes {@code master}'s disciple with a new soul, following,
	 * with the data it already has.
	 */
	void acceptAsDisciple(ServerPlayer master) {
		setRole(Role.DISCIPLE, null);
		bindTo(master);
		this.soulId = this.getUUID();
		this.life = 0;
		this.mode = Mode.FOLLOW;
		this.post = null;
		setActivity(ACTIVITY_NONE, null);
		refreshSynced();
	}

	/**
	 * Brings back a disciple taken out with {@link Disciples#depart} ({@link #writeTablet()} plus "health"): the same
	 * soul and life, its master, looks, cultivation and order (a spot order takes the arrival spot as its post).
	 */
	void restoreFromDeparture(CompoundTag tag, UUID master, String masterName) {
		bindTo(master, masterName);
		this.soulId = tag.read("soul", UUIDUtil.CODEC).orElse(this.getUUID());
		this.life = Math.max(0, tag.getIntOr("life", 0));
		this.entityData.set(DATA_SKIN, Math.floorMod(tag.getIntOr("skin", 0), SKIN_COUNT));
		this.entityData.set(DATA_SLIM, tag.getBooleanOr("slim", false));
		tag.read("name", ComponentSerialization.CODEC).ifPresent(this::setCustomName);
		setData(DiscipleData.load(tag.getCompoundOrEmpty("disciple")));
		Mode saved = Mode.byId(tag.getStringOr("mode", Mode.FOLLOW.id()));
		setMode(saved == null ? Mode.FOLLOW : saved);
		float health = tag.getFloatOr("health", this.getMaxHealth());
		this.setHealth(Math.max(1.0F, Math.min(this.getMaxHealth(), health)));
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
		if (target == null || target == this || !target.isAlive()) {
			return false;
		}
		// A registered duel comes first (DESIGN 20.2.8): the two partners may fight whoever they are.
		if (DuelRules.dueling(this, target)) {
			return true;
		}
		// Candidates fight nobody; rivals and phantoms fight only their duel partner.
		if (role == Role.CANDIDATE || role == Role.RIVAL || role == Role.PHANTOM) {
			return false;
		}
		// Disciples never fight disciples (of anyone): that also keeps two disciples from asking each other forever. A heart
		// demon is a matter of one cultivator's heart (and immune to anyone else), invulnerable things are no enemies.
		if (target instanceof DiscipleEntity || untouchable(target) || target instanceof HeartDemonEntity || !target.canBeSeenAsEnemy()) {
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
		// The damageFactor hooks ride on a transient attack modifier for this one blow only.
		double factor = DiscipleHooks.damageFactor(this, living, false);
		AttributeInstance attack = factor != 1.0D ? this.getAttribute(Attributes.ATTACK_DAMAGE) : null;
		if (attack != null) {
			attack.addOrUpdateTransientModifier(new AttributeModifier(MELEE_FACTOR_ID, factor - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		boolean hit;
		try {
			hit = super.doHurtTarget(level, target);
		} finally {
			if (attack != null) {
				attack.removeModifier(MELEE_FACTOR_ID);
			}
		}
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
		// A registered duel comes first (DESIGN 20.2.8): the partner may always hurt it (clamped at 10% by DuelRules).
		if (attacker != null && DuelRules.dueling(attacker, this)) {
			return super.isInvulnerableTo(level, source);
		}
		if (role == Role.CANDIDATE || role == Role.RIVAL || role == Role.PHANTOM) {
			// Candidates are untouchable (only /kill and the void); rivals and phantoms are only for their duel partner.
			return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		}
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
		boolean kill = source.is(DamageTypes.GENERIC_KILL);
		if (!kill && damage > 0.0F) {
			damage = (float) (damage * DiscipleHooks.hurtFactor(this, source));
			if (source.is(Tribulation.DAMAGE)) {
				// Tribulation and calamity strikes are never lethal (phase 5), whatever the hooks multiplied them by.
				damage = Math.min(damage, Math.max(0.0F, this.getHealth() + this.getAbsorptionAmount() - 1.0F));
			}
		}
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt) {
			lastHurtTick = this.tickCount;
			if (meditating) {
				meditating = false;
				syncFlags();
			}
			if (!kill) {
				DiscipleHooks.onHurt(this, source, damage);
			}
		}
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		boolean first = !this.isRemoved() && !this.dead;
		if (first && role == Role.DISCIPLE && releaseTicks <= 0 && !this.level().isClientSide() && !source.is(DamageTypes.GENERIC_KILL)
				&& DiscipleHooks.onDeath(this, source)) {
			// Handled by a module (the Undying Blood, the Nascent Soul's flight...): no death, no tablet.
			if (!this.isRemoved() && this.getHealth() <= 0.0F) {
				this.setHealth(1.0F);
			}
			return;
		}
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

		if (mode == Mode.GUARD_HALL && (this.tickCount / STATE_INTERVAL) % 4 == 0) {
			refreshHallAnchor();
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
				float amount = (float) DiscipleHooks.regenFactor(this);
				if (amount > 0.0F) {
					this.heal(amount);
				}
			}
		}

		if (this.onGround() && !this.isInWater() && !this.isInLava()) {
			lastSafePos = this.blockPosition();
		}
		syncFlags();
	}

	private boolean computeMeditating(@Nullable ServerPlayer owner, boolean sameLevel) {
		if (releaseTicks > 0 || data.breakingThrough() || this.getTarget() != null || !this.onGround() || this.isInWater()
				|| this.isPassenger() || this.tickCount - lastHurtTick < 60 || this.tickCount - lastAttackTick < 40
				|| DuelRules.inDuel(this)) {
			return false;
		}
		if (holdsActivitySpot()) {
			// Formation guard, tower, spirit body and spectators sit at their spot; duties and builders loop their own pose instead.
			return activity >= ACTIVITY_FORMATION_GUARD && activity != ACTIVITY_BUILD && activitySpot != null && this.getNavigation().isDone()
					&& this.distanceToSqr(activitySpot.getX() + 0.5D, activitySpot.getY(), activitySpot.getZ() + 0.5D) <= 2.25D;
		}
		if (role != Role.DISCIPLE) {
			return false;
		}
		return switch (mode) {
			case MEDITATE -> post != null && this.distanceToSqr(post.getX() + 0.5D, post.getY(), post.getZ() + 0.5D) <= 2.25D;
			case STAY -> post != null && this.distanceToSqr(post.getX() + 0.5D, post.getY(), post.getZ() + 0.5D) <= 4.0D
					&& this.getNavigation().isDone();
			// Between two patrol legs it sits down where it stopped.
			case GUARD_HALL -> this.getNavigation().isDone() && this.tickCount < patrolRestUntil;
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
		applyHookStats();
		if (this.getHealth() > this.getMaxHealth()) {
			this.setHealth(this.getMaxHealth());
		}
	}

	/**
	 * The {@code stats} hooks (DESIGN 20.2.6): their modifiers are applied as permanent modifiers; one that no provider
	 * gives any more is removed (the applied ids are saved with the body, so this also holds after a restart).
	 */
	private void applyHookStats() {
		if (!DiscipleHooks.hasStats() && hookModifiers.isEmpty()) {
			return;
		}
		Map<String, AttributeModifier> wanted = new HashMap<>();
		Map<String, Holder<Attribute>> attributes = new HashMap<>();
		DiscipleHooks.stats(this, (attribute, id, amount, operation) -> {
			if (attribute == null || id == null || !Double.isFinite(amount) || amount == 0.0D) {
				return;
			}
			String key = attribute.unwrapKey().map(k -> k.identifier().toString()).orElse("") + "|" + id;
			wanted.put(key, new AttributeModifier(id, amount, operation));
			attributes.put(key, attribute);
		});
		for (String key : hookModifiers) {
			if (!wanted.containsKey(key)) {
				removeHookModifier(key);
			}
		}
		hookModifiers.clear();
		for (Map.Entry<String, AttributeModifier> entry : wanted.entrySet()) {
			AttributeInstance instance = this.getAttribute(attributes.get(entry.getKey()));
			if (instance != null) {
				instance.addOrReplacePermanentModifier(entry.getValue());
				hookModifiers.add(entry.getKey());
			}
		}
	}

	private void removeHookModifier(String key) {
		int split = key.indexOf('|');
		if (split <= 0 || split >= key.length() - 1) {
			return;
		}
		try {
			Identifier attributeId = Identifier.parse(key.substring(0, split));
			Identifier modifierId = Identifier.parse(key.substring(split + 1));
			BuiltInRegistries.ATTRIBUTE.get(attributeId).ifPresent(holder -> {
				AttributeInstance instance = this.getAttribute(holder);
				if (instance != null) {
					instance.removeModifier(modifierId);
				}
			});
		} catch (RuntimeException ignored) {
			// A broken entry from an old save: nothing to remove.
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
	public boolean teleportNear(LivingEntity master) {
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
		return post != null && returnTo(post);
	}

	/** Steps back to {@code target} (a post, an activity spot, the hall) in a flash: safe spot, loaded chunk, read-only. */
	boolean returnTo(BlockPos target) {
		if (target == null || !(this.level() instanceof ServerLevel level) || !level.isLoaded(target)) {
			return false;
		}
		Vec3 spot = Targeting.safeSpot(level, this, new Vec3(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D));
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
		if (role != Role.DISCIPLE) {
			// Never written to a chunk (shouldBeSaved); kept so a copy made another way is still refused on load.
			output.putInt("Role", role.ordinal());
		}
		// Lasting assignments (duties, formation guard, formation keeper) survive a restart; tower, spirit body, spectator and duel do not.
		if ((activity >= ACTIVITY_WATER && activity <= ACTIVITY_FORMATION_GUARD || activity == ACTIVITY_FORMATION_KEEPER) && activitySpot != null) {
			output.putInt("Activity", activity);
			output.store("ActivitySpot", BlockPos.CODEC, activitySpot);
		}
		if (!hookModifiers.isEmpty()) {
			output.store("HookMods", com.mojang.serialization.Codec.STRING.listOf(), List.copyOf(hookModifiers));
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
		this.role = Role.byOrdinal(input.getIntOr("Role", 0));
		this.entityData.set(DATA_ROLE, (byte) this.role.ordinal());
		int savedActivity = input.getIntOr("Activity", 0);
		BlockPos savedSpot = input.read("ActivitySpot", BlockPos.CODEC).orElse(null);
		if ((savedActivity >= ACTIVITY_WATER && savedActivity <= ACTIVITY_FORMATION_GUARD || savedActivity == ACTIVITY_FORMATION_KEEPER)
				&& savedSpot != null) {
			this.activity = savedActivity;
			this.activitySpot = savedSpot;
			this.entityData.set(DATA_ACTIVITY, (byte) savedActivity);
		}
		this.hookModifiers.clear();
		input.read("HookMods", com.mojang.serialization.Codec.STRING.listOf()).ifPresent(this.hookModifiers::addAll);
		this.addTag(NoGrief.TAG);
		this.setCanPickUpLoot(false);
		refreshSynced();
	}

	/** Only disciples are written with their chunk (DESIGN 20.2.5); candidates, rivals, phantoms and clones never are. */
	@Override
	public boolean shouldBeSaved() {
		return role == Role.DISCIPLE && super.shouldBeSaved();
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
