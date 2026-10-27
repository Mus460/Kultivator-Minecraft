package dev.kultivator.disciple;

import dev.kultivator.Kultivator;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.data.RootGrade;
import dev.kultivator.fx.Fx;
import dev.kultivator.sekte.mandiri.MandiriConfig;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sosok.Gaya;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.Sosok;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.sosok.SosokUsia;
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
import net.minecraft.util.Mth;
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
import net.minecraft.world.entity.ai.goal.GoalSelector;
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
import net.minecraft.world.level.levelgen.Heightmap;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A disciple (Murid, DESIGN 18): a humanoid follower drawn with the player model and one of the default player skins.
 * It belongs to one master, cultivates on its own ({@link DiscipleData} / {@link DiscipleCultivation}), obeys four
 * orders ({@link Mode}), fights only what {@link Targeting#isHostileTo} lets its master hurt, sits down cross-legged to
 * meditate when idle and is saved with the world. It never changes a block: it opens no doors, picks up nothing, presses
 * no plates, tramples nothing ({@link NoGrief#TAG}), is not touched by blocks at all and travels through no portal. Death
 * leaves only its Soul Tablet ({@link Disciples#onDeath}).
 */
public class DiscipleEntity extends PathfinderMob implements OwnableEntity, SosokApi.Pemegang {

	/** Bits of {@link #syncedFlags()}. */
	public static final int FLAG_MEDITATING = 1;
	public static final int FLAG_BREAKING_THROUGH = 2;
	public static final int FLAG_FIGHTING = 4;
	public static final int FLAG_DEVIATING = 8;
	/** Kunpeng Wings spread (= {@code PowerIds.BIT_KUNPENG}). */
	public static final int FLAG_KUNPENG = 16;
	/** The Seven Fairies linked (= {@code PowerIds.BIT_FAIRIES}). */
	public static final int FLAG_FAIRIES = 32;
	/** The default player skins a disciple can wear (index 0..8). */
	public static final int SKIN_COUNT = 9;
	/** Key of {@link DiscipleData#save()} in the entity's saved data. */
	public static final String DATA_KEY = "kultivator_disciple";

	/** {@link #addModeListener}: called after every {@link #setMode} on the server (DESIGN 39.7.2). */
	private static final List<Consumer<DiscipleEntity>> MODE_LISTENERS = new CopyOnWriteArrayList<>();
	/** {@link #addGoalInstaller}: called at the end of {@link #registerGoals} for every new body. */
	private static final List<BiConsumer<DiscipleEntity, GoalSelector>> GOAL_INSTALLERS = new CopyOnWriteArrayList<>();

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
		CLONE,
		/**
		 * Murid Mandiri's town body (DESIGN 39.6.6, module 39C): a read-only look-alike of a disciple who is away on a
		 * journey (the real data stays in {@code AwayStore}). Never saved, never listed by {@link Disciples}, fights nobody,
		 * and every hurt is refused except the void and {@code /kill}; its walk and poses come from goals that 39C adds
		 * with {@link #addGoalInstaller}. Appended last so the ordinals of the older roles never shift.
		 */
		JELMAAN;

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
	/**
	 * Asisten Tempa, a forge assistant (DESIGN 25.5.8, phase 8): holds its spot within 2 blocks of an Earthfire Forge like
	 * 11, may sit to meditate there, and survives a save; the assignment itself is kept in {@code DiscipleData.extra().dunia}.
	 */
	public static final int ACTIVITY_FORGE_ASSIST = 12;

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
	// Aura Kultivasi (DESIGN 31, C31.5): Heavenly root, breakthrough ready and the root element mask.
	private static final EntityDataAccessor<Integer> DATA_AURA = SynchedEntityData.defineId(DiscipleEntity.class, EntityDataSerializers.INT);
	// Sosok (DESIGN 40.2.4, module 40I): identity, changeable style, server clip, its start, props, the entity looked at.
	// Only "sosok" and "gaya" are saved; clips, props and the gaze are session state ({@link SosokApi}).
	private static final EntityDataAccessor<Integer> DATA_SOSOK = SynchedEntityData.defineId(DiscipleEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_GAYA = SynchedEntityData.defineId(DiscipleEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_GERAK = SynchedEntityData.defineId(DiscipleEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_GERAK_T0 = SynchedEntityData.defineId(DiscipleEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_PROP = SynchedEntityData.defineId(DiscipleEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_LOOK_AT = SynchedEntityData.defineId(DiscipleEntity.class, EntityDataSerializers.INT);
	/** {@link #syncedAura()} bit: the root is of the Heavenly grade. */
	public static final int AURA_HEAVENLY_ROOT = 1;
	/** {@link #syncedAura()} bit: at the bottleneck with a full bar, not the maximum rank. */
	public static final int AURA_BOTTLENECK_READY = 2;
	/** {@link #syncedAura()}: the root element mask sits in bits 8-23. */
	public static final int AURA_ELEMENT_SHIFT = 8;
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
	/** The clip {@link #animateDuty} started (0 none), where the activity's work is, and whether it faces it now (session state). */
	private int dutyClip;
	private @Nullable BlockPos activityFocus;
	private boolean facingWork;
	/** A formation guard that sits in meditation (the grand formation's core keeper) instead of standing guard. */
	private boolean guardSits;
	/** Timers of the timed Sosok clips, moods and function activities (DESIGN 40, session state). */
	private final SosokApi.Waktu sosokWaktu = new SosokApi.Waktu();

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
		builder.define(DATA_AURA, 0);
		builder.define(DATA_SOSOK, Sosok.KOSONG);
		builder.define(DATA_GAYA, 0);
		builder.define(DATA_GERAK, 0);
		builder.define(DATA_GERAK_T0, 0);
		builder.define(DATA_PROP, 0);
		builder.define(DATA_LOOK_AT, SosokApi.TATAP_TIDAK_ADA);
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
		// the head stays on the work while it faces it (animateDuty)
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F) {
			@Override
			public boolean canUse() {
				return !facingWork && super.canUse();
			}
		});
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this) {
			@Override
			public boolean canUse() {
				return !facingWork && super.canUse();
			}
		});
		this.targetSelector.addGoal(1, new DiscipleAi.ChooseTargetGoal(this));
		for (BiConsumer<DiscipleEntity, GoalSelector> installer : GOAL_INSTALLERS) {
			try {
				installer.accept(this, this.goalSelector);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("A disciple goal installer failed", exception);
			}
		}
	}

	/**
	 * Adds goals to every disciple body created from now on (DESIGN 39.12 39A hook; 39C's town-body goals). Runs from the
	 * entity constructor on both sides, before the role, data and owner are set: an installed goal must test
	 * {@link #role()} (or anything else) in its own {@code canUse}, never at install time. Register once from a module
	 * init, before any world loads.
	 */
	public static void addGoalInstaller(BiConsumer<DiscipleEntity, GoalSelector> installer) {
		if (installer != null) {
			GOAL_INSTALLERS.add(installer);
		}
	}

	/**
	 * Called after every {@link #setMode} on the server (DESIGN 39.12 39A hook): Murid Mandiri treats it as the master's
	 * explicit order and pauses its planner for that disciple (39.7.2). Listeners must be cheap; one that throws is logged
	 * and skipped.
	 */
	public static void addModeListener(Consumer<DiscipleEntity> listener) {
		if (listener != null) {
			MODE_LISTENERS.add(listener);
		}
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
		applyMode(mode);
		if (!this.level().isClientSide()) {
			for (Consumer<DiscipleEntity> listener : MODE_LISTENERS) {
				try {
					listener.accept(this);
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.error("A disciple mode listener failed", exception);
				}
			}
		}
	}

	/** setMode without the listeners: for restoring a saved mode, which is not a master's order. */
	private void applyMode(Mode mode) {
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
		if (next != this.activity) {
			this.activityFocus = null;
			this.guardSits = false;
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

	/**
	 * Where the current activity's work is (the forge, the building site, a core's eye, the ring a spectator watches): the
	 * disciple faces it while it works or sits at its spot. Forgotten when the activity changes; null clears it.
	 */
	public void setActivityFocus(@Nullable BlockPos focus) {
		this.activityFocus = focus == null ? null : focus.immutable();
	}

	/** A formation guard (activity 5) that sits in meditation instead of standing guard; forgotten when the activity changes. */
	public void setGuardSits(boolean sits) {
		this.guardSits = sits;
	}

	/**
	 * {@link #isMeditatingNow} or standing at a guard or forge post, where it does not sit but keeps cultivating (the
	 * forge assistant's half-rate meditation XP, DESIGN 25.5.8). Server side.
	 */
	public boolean cultivatesNow() {
		return isMeditatingNow() || dutyClip != 0 && (activity == ACTIVITY_FORMATION_GUARD || activity == ACTIVITY_FORGE_ASSIST
				|| activity == ACTIVITY_NONE && mode == Mode.STAY);
	}

	/**
	 * True while an activity 1-8, 10 (builder), 11 (formation keeper), 12 (forge assistant), one of Sekte yang Hidup's
	 * 13-20 ({@code sekte.SekteIds.ACT_*}, DESIGN 36.3.3) or one of Murid Mandiri's holding ids among 40-60
	 * ({@code SekteIds.mandiriHolding}, DESIGN 39.4.2) binds the disciple to its spot.
	 */
	public boolean holdsActivitySpot() {
		return activity >= ACTIVITY_WATER && activity <= ACTIVITY_SPECTATOR || activity == ACTIVITY_BUILD
				|| activity == ACTIVITY_FORMATION_KEEPER || activity == ACTIVITY_FORGE_ASSIST || sekteHolding(activity)
				|| dev.kultivator.sekte.SekteIds.mandiriHolding(activity);
	}

	/** Sekte yang Hidup's spot-holding activities 13-20 (DESIGN 36.3.3); 21-24 are poses only. */
	private static boolean sekteHolding(int id) {
		return id >= dev.kultivator.sekte.SekteIds.ACT_FIRST && id <= dev.kultivator.sekte.SekteIds.ACT_LAST_HOLDING;
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

	/**
	 * The Aura Kultivasi bits (DESIGN 31.3): {@link #AURA_HEAVENLY_ROOT}, {@link #AURA_BOTTLENECK_READY} and the root
	 * element mask in bits 8-23 (see {@link #auraElements}).
	 */
	public int syncedAura() {
		return this.entityData.get(DATA_AURA);
	}

	/** The root element mask of a {@link #syncedAura()} value. */
	public static int auraElements(int aura) {
		return aura >>> AURA_ELEMENT_SHIFT & 0xFFFF;
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

	// ================================================================== Sosok (DESIGN 40.2.4, contract C40.I1)

	/** The disciple's identity: sex, visible age, build, look seed (the entity-seed stand-in while still unset). */
	public Sosok syncedSosok() {
		return Sosok.dari(this.entityData.get(DATA_SOSOK), this.getUUID());
	}

	/** The changeable style: shown traits, life path, wound, special outfit, mood. */
	public Gaya syncedGaya() {
		return Gaya.unpack(this.entityData.get(DATA_GAYA));
	}

	/** The server clip ({@code GerakId.pack}: clip {@code << 8 | variant}; 0 = none). */
	public int syncedGerak() {
		return this.entityData.get(DATA_GERAK);
	}

	/** Game time {@code & 0x7FFFFFFF} when the server clip started. */
	public int syncedGerakT0() {
		return this.entityData.get(DATA_GERAK_T0);
	}

	/** The props of the server clip ({@code PropId.pack}; 0 = the clip's own). */
	public int syncedProp() {
		return this.entityData.get(DATA_PROP);
	}

	/** The id of the entity it looks at, or {@link SosokApi#TATAP_TIDAK_ADA}. */
	public int syncedLookAt() {
		return this.entityData.get(DATA_LOOK_AT);
	}

	@Override
	public int sosokData() {
		return this.entityData.get(DATA_SOSOK);
	}

	/**
	 * Sets the packed Sosok and keeps the old look fields in step: the slim arm model follows the body (every woman and
	 * child) and the vanilla skin index, used only as the last fallback, comes from the look seed.
	 */
	@Override
	public void setSosokData(int packed) {
		this.entityData.set(DATA_SOSOK, packed);
		Sosok sosok = Sosok.unpack(packed);
		if (sosok != null) {
			this.entityData.set(DATA_SLIM, sosok.lenganRamping());
			this.entityData.set(DATA_SKIN, Math.floorMod(sosok.benih(), SKIN_COUNT));
		}
	}

	@Override
	public int gayaData() {
		return this.entityData.get(DATA_GAYA);
	}

	@Override
	public void setGayaData(int packed) {
		this.entityData.set(DATA_GAYA, packed);
	}

	@Override
	public int gerakData() {
		return this.entityData.get(DATA_GERAK);
	}

	@Override
	public int gerakT0() {
		return this.entityData.get(DATA_GERAK_T0);
	}

	@Override
	public void setGerakData(int gerak, int t0) {
		this.entityData.set(DATA_GERAK, gerak);
		this.entityData.set(DATA_GERAK_T0, t0);
	}

	@Override
	public int propData() {
		return this.entityData.get(DATA_PROP);
	}

	@Override
	public void setPropData(int packed) {
		this.entityData.set(DATA_PROP, packed);
	}

	@Override
	public int tatapData() {
		return this.entityData.get(DATA_LOOK_AT);
	}

	@Override
	public void setTatapData(int entityId) {
		this.entityData.set(DATA_LOOK_AT, entityId);
	}

	@Override
	public SosokApi.Waktu waktuSosok() {
		return sosokWaktu;
	}

	/** Gives the body {@code sosok} (synced data, slim model, skin fallback, {@code Attributes.SCALE}). Server side. */
	void applySosok(Sosok sosok) {
		if (sosok != null) {
			SosokApi.setSosok(this, sosok);
		}
	}

	/** Restores a saved pair (body NBT, Soul Tablet, departure): sosok 0 keeps the entity-seed stand-in. */
	private void loadSosok(int sosok, int gaya) {
		Sosok saved = Sosok.unpack(sosok);
		if (saved != null) {
			setSosokData(saved.pack());
			if (!this.level().isClientSide()) {
				SosokApi.terapkanSkala(this, saved);
			}
		}
		this.entityData.set(DATA_GAYA, Gaya.unpack(gaya).withSuasana(Gaya.SUASANA_NETRAL).pack());
		sosokWaktu.reset();
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

	/**
	 * Binds a freshly created disciple to its master: data, name, its Sosok (sex, age, build, look seed; the slim model
	 * and skin fallback follow it, DESIGN 40.2.4), the FOLLOW order and full health. The day it joined is noted for the
	 * visible ageing ({@link SosokUsia}).
	 */
	void setupNew(ServerPlayer master, DiscipleData fresh, Component name, Sosok sosok) {
		bindTo(master);
		this.soulId = this.getUUID();
		this.life = 0;
		this.mode = Mode.FOLLOW;
		this.post = null;
		this.setCustomName(name);
		setData(fresh);
		applySosok(sosok);
		SosokUsia.catatLahir(this);
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

	/**
	 * Gives a candidate, rival, phantom or clone its looks (no master is bound here). A body made only from the old
	 * look fields gets the deterministic Sosok of its name, skin and slim flag ({@code SosokGen.dariTubuhLama}); a clone
	 * or phantom passes its source's Sosok and style so it looks exactly like it.
	 */
	void setupBody(DiscipleData fresh, @Nullable Component name, int skin, boolean slim, @Nullable Sosok sosok, int gaya) {
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
		applySosok(sosok != null ? sosok : dev.kultivator.sosok.SosokGen.dariTubuhLama(name, skin, slim));
		this.entityData.set(DATA_GAYA, Gaya.unpack(gaya).withSuasana(Gaya.SUASANA_NETRAL).pack());
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
		SosokUsia.catatLahir(this);
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
		loadSosok(tag.getIntOr("sosok", Sosok.KOSONG), tag.getIntOr("gaya", 0));
		Mode saved = Mode.byId(tag.getStringOr("mode", Mode.FOLLOW.id()));
		applyMode(saved == null ? Mode.FOLLOW : saved); // not setMode: listeners would read the return as a master's order
		float health = tag.getFloatOr("health", this.getMaxHealth());
		this.setHealth(Math.max(1.0F, Math.min(this.getMaxHealth(), health)));
	}

	/** Everything a Soul Tablet keeps: soul, life, master, name, skin, Sosok and style, and the whole cultivation. */
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
		tag.putInt("sosok", sosokData());
		tag.putInt("gaya", gayaData());
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
		// the same face in the new life; the wound and the special outfit of the old body do not come back
		loadSosok(tag.getIntOr("sosok", Sosok.KOSONG), Gaya.unpack(tag.getIntOr("gaya", 0))
				.withLuka(Gaya.LUKA_SEHAT).withBusanaKhusus(Gaya.BUSANA_OTOMATIS).pack());
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

	/**
	 * It holds still: meditating, breaking through, bowing to leave, charging or channelling a technique, holding the fire
	 * lotus or sitting in a heavenly-flame ritual (DESIGN 27.6: the cast pose stays put instead of running about).
	 */
	boolean shouldHoldStill() {
		return meditating || data.breakingThrough() || releaseTicks > 0 || castHold();
	}

	/** Charging or channelling a technique, the lotus charge or a flame ritual. */
	private boolean castHold() {
		return dev.kultivator.technique.spec.run.TechniqueRunner.holding(this) || dev.kultivator.disciple.power.DisciplePowers.holdingStill(this);
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
		// Candidates and town bodies fight nobody; rivals and phantoms fight only their duel partner.
		if (role == Role.CANDIDATE || role == Role.RIVAL || role == Role.PHANTOM || role == Role.JELMAAN) {
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
		if (role == Role.CANDIDATE || role == Role.RIVAL || role == Role.PHANTOM || role == Role.JELMAAN) {
			// Candidates and town bodies are untouchable (only /kill and the void); rivals and phantoms are only for their
			// duel partner.
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
		if (first && role == Role.DISCIPLE && releaseTicks <= 0 && !this.level().isClientSide() && !source.is(DamageTypes.GENERIC_KILL)) {
			if (DiscipleHooks.onDeath(this, source)) {
				// Handled by a module (the Undying Blood, the Nascent Soul's flight...): no death, no tablet.
				if (!this.isRemoved() && this.getHealth() <= 0.0F) {
					this.setHealth(1.0F);
				}
				return;
			}
			if (!MandiriConfig.get().bolehGugur) {
				// The user's rule: disciples never die (only /kill). It collapses with a heavy wound and rests a day instead.
				this.setHealth(Math.max(1.0F, this.getMaxHealth() * 0.25F));
				if (this.isInLava() || this.isInWall()) {
					// it cannot climb out of lava or rock while it lies there: back to the last safe ground, else beside the master
					ServerPlayer master = owner();
					if (!(lastSafePos != null && returnTo(lastSafePos)) && master != null) {
						teleportNear(master);
					}
				}
				this.clearFire();
				this.setInvulnerableTime(60);
				long now = this.level().getGameTime();
				MandiriApi.lukai(data, JenisLuka.BERAT, now, now + 24000L, "pingsan");
				return;
			}
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
		if (role == Role.DISCIPLE && !MandiriConfig.get().bolehGugur) {
			// It cannot die, so the void must not hold it: back up onto the ground above (or the world spawn).
			BlockPos top = this.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, this.blockPosition());
			this.setDeltaMovement(Vec3.ZERO);
			if (top.getY() <= this.level().getMinY() && this.level() instanceof ServerLevel level) {
				// a void column: the last safe ground or the post first; the (overworld) spawn only in its own dimension
				if ((lastSafePos != null && returnTo(lastSafePos)) || (post != null && returnTo(post))) {
					return;
				}
				net.minecraft.core.GlobalPos spawn = level.getRespawnData().globalPos();
				if (spawn.dimension().equals(level.dimension())) {
					top = spawn.pos();
				}
			}
			this.teleportTo(top.getX() + 0.5D, top.getY() + 1.0D, top.getZ() + 0.5D);
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
		// Sosok (DESIGN 40): timed clips, moods and function activities end; once a second the automatic look state
		// (wound, mourning, shown traits) and, spread over the minute, the visible ageing
		SosokApi.tik(this);
		if ((this.tickCount + this.getId()) % SosokUsia.INTERVAL == 0) {
			SosokUsia.periksa(this);
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
		} else if ((this.tickCount & 1) == 0) {
			// the short look flags (wings spread, a cast hold) reach the clients within two ticks
			syncFlags();
		}
		if (castHold()) {
			// a charge or channel: stand still and face the foe so the cast pose reads (216)
			this.getNavigation().stop();
			LivingEntity target = this.getTarget();
			if (target != null && target.isAlive()) {
				this.getLookControl().setLookAt(target, 30.0F, 30.0F);
			}
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

		animateDuty();

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

	/** How close to its spot (squared) it counts as at its work. */
	private static final double AT_WORK_SQ = 9.0D;
	/** Forge assistant: ticks of one hammering or fanning spell. */
	private static final int FORGE_SPELL = 100;

	/**
	 * Sosok (DESIGN 40, the animation rule): every 10 ticks the body clip (with the clip's own prop) of what it does at its
	 * spot or under its order, and the turn to face the work. A formation guard stands with the spear (BERDIRI_JAGA), a
	 * forge assistant alternates hammer and bellows (PALU_LANDASAN, KIPAS_TUNGKU), a builder carries a plank on the way
	 * (ANGKAT_PAPAN) and mends with the hammer at the site (PERBAIKI_PALU), GUARD_HALL marches the spear (BARIS_JAGA) and
	 * stands guard between legs; spectators and keepers keep their seat and only face their focus (their cheer and seal
	 * are played by the modules owning them). Only the clip started here is stopped here, and no other module's clip is
	 * ever replaced.
	 */
	private void animateDuty() {
		int clip = 0;
		BlockPos face = null;
		boolean free = role == Role.DISCIPLE && releaseTicks == 0 && !data.breakingThrough() && this.onGround() && !this.isInWater()
				&& !this.isPassenger() && !inCombat() && !castHold() && !DuelRules.inDuel(this);
		if (free) {
			boolean moving = !this.getNavigation().isDone();
			boolean atWork = holdsActivitySpot() && activitySpot != null
					&& this.distanceToSqr(activitySpot.getX() + 0.5D, activitySpot.getY(), activitySpot.getZ() + 0.5D) <= AT_WORK_SQ;
			switch (activity) {
				case ACTIVITY_NONE -> {
					if (!meditating) {
						if (mode == Mode.GUARD_HALL) {
							clip = moving ? GerakId.BARIS_JAGA : GerakId.BERDIRI_JAGA;
						} else if (mode == Mode.STAY && !moving) {
							clip = GerakId.BERDIRI_JAGA;
						}
					}
				}
				case ACTIVITY_FORMATION_GUARD -> {
					if (atWork && !moving && !meditating) {
						clip = GerakId.BERDIRI_JAGA;
						face = activityFocus != null ? activityFocus : activitySpot;
					}
				}
				case ACTIVITY_FORGE_ASSIST -> {
					if (atWork && !moving) {
						clip = (this.tickCount / FORGE_SPELL + this.getId()) % 2 == 0 ? GerakId.PALU_LANDASAN : GerakId.KIPAS_TUNGKU;
						face = activityFocus;
					}
				}
				case ACTIVITY_BUILD -> {
					if (atWork) {
						clip = GerakId.PERBAIKI_PALU;
						face = activityFocus;
					} else if (moving) {
						clip = GerakId.ANGKAT_PAPAN;
					}
				}
				case ACTIVITY_SPECTATOR, ACTIVITY_FORMATION_KEEPER -> face = atWork && !moving ? activityFocus : null;
				default -> {
				}
			}
		}
		facingWork = face != null;
		if (face != null) {
			double dx = face.getX() + 0.5D - this.getX();
			double dz = face.getZ() + 0.5D - this.getZ();
			float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
			if (dx * dx + dz * dz >= 0.36D && Math.abs(Mth.wrapDegrees(yaw - this.getYRot())) > 8.0F) {
				this.setYRot(yaw);
				this.setYHeadRot(yaw);
				this.setYBodyRot(yaw);
			}
		}
		int now = SosokApi.gerakSekarang(this);
		if (clip != 0) {
			if (now == 0 || now == dutyClip) {
				SosokApi.gerak(this, clip, 0, 0);
				dutyClip = clip;
			}
		} else if (dutyClip != 0) {
			if (now == dutyClip) {
				if (GerakId.dariAktivitas(activity) != GerakId.TIDAK_ADA) {
					// henti would also end the function activity (13-27) of another module
					this.setGerakData(0, 0);
					this.setPropData(dev.kultivator.sosok.PropId.TIDAK_ADA);
				} else {
					SosokApi.henti(this);
				}
				dutyClip = 0;
			} else if (now != GerakId.BICARA && GerakId.durasiSekali(now, 0) <= 0) {
				// another module's looping clip took over: it is theirs now (a once clip or a spoken line plays over ours,
				// which it saved and restores, and ends by itself)
				dutyClip = 0;
			}
		}
	}

	private boolean computeMeditating(@Nullable ServerPlayer owner, boolean sameLevel) {
		if (releaseTicks > 0 || data.breakingThrough() || this.getTarget() != null || !this.onGround() || this.isInWater()
				|| this.isPassenger() || this.tickCount - lastHurtTick < 60 || this.tickCount - lastAttackTick < 40
				|| DuelRules.inDuel(this)) {
			return false;
		}
		if (holdsActivitySpot()) {
			// Tower, spirit body, spectators, formation keepers and a grand formation's core keeper sit at their spot; duties,
			// builders, forge assistants and the other formation guards stand and loop their own clip instead (animateDuty).
			// Of Sekte yang Hidup's activities only the pavilion reader sits (the others loop their own pose); of Murid
			// Mandiri's 40-60 only seclusion, absorbing stones and self-chosen meditation sit (DESIGN 39.4.2).
			boolean mandiri = dev.kultivator.sekte.SekteIds.mandiriActivity(activity);
			return activity >= ACTIVITY_FORMATION_GUARD && activity != ACTIVITY_BUILD && activity != ACTIVITY_FORGE_ASSIST
					&& (activity != ACTIVITY_FORMATION_GUARD || guardSits) && (!sekteHolding(activity) || activity == dev.kultivator.sekte.SekteIds.ACT_STUDY)
					&& (!mandiri || dev.kultivator.sekte.SekteIds.mandiriSits(activity))
					&& activitySpot != null && this.getNavigation().isDone()
					&& this.distanceToSqr(activitySpot.getX() + 0.5D, activitySpot.getY(), activitySpot.getZ() + 0.5D) <= 2.25D;
		}
		if (role != Role.DISCIPLE) {
			return false;
		}
		return switch (mode) {
			case MEDITATE -> post != null && this.distanceToSqr(post.getX() + 0.5D, post.getY(), post.getZ() + 0.5D) <= 2.25D;
			// STAY holds its point standing on guard (BERDIRI_JAGA, animateDuty); it keeps cultivating (cultivatesNow)
			case STAY -> false;
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
		int flags = (meditating || dev.kultivator.disciple.power.DisciplePowers.inRitual(this) ? FLAG_MEDITATING : 0) | (target != null && target.isAlive() ? FLAG_FIGHTING : 0) | data.cultivationFlags()
				| dev.kultivator.disciple.power.DisciplePowers.lookFlags(this);
		if (this.entityData.get(DATA_FLAGS) != flags) {
			this.entityData.set(DATA_FLAGS, flags);
		}
		int aura = auraBits();
		if (this.entityData.get(DATA_AURA) != aura) {
			this.entityData.set(DATA_AURA, aura);
		}
	}

	/** The value of {@link #syncedAura()} for the current data (DESIGN 31, C31.5). */
	private int auraBits() {
		Rank rank = data.rank();
		int aura = (data.rootElements() & 0xFFFF) << AURA_ELEMENT_SHIFT;
		if (data.rootGrade() == RootGrade.HEAVENLY) {
			aura |= AURA_HEAVENLY_ROOT;
		}
		if (rank.isBottleneck() && data.barFull() && !rank.isMax()) {
			aura |= AURA_BOTTLENECK_READY;
		}
		return aura;
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
		output.putInt("sosok", sosokData());
		output.putInt("gaya", gayaData());
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
		// Lasting assignments (duties, formation guard, formation keeper, forge assistant, Sekte yang Hidup's 13-20) survive a
		// restart; tower, spirit body, spectator, duel and Murid Mandiri's 40-60 (the planner picks again, S15) do not.
		if ((activity >= ACTIVITY_WATER && activity <= ACTIVITY_FORMATION_GUARD || activity == ACTIVITY_FORMATION_KEEPER
				|| activity == ACTIVITY_FORGE_ASSIST || sekteHolding(activity)) && activitySpot != null) {
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
		loadSosok(input.getIntOr("sosok", Sosok.KOSONG), input.getIntOr("gaya", 0));
		this.soulId = input.read("Soul", UUIDUtil.CODEC).orElse(null);
		this.life = Math.max(0, input.getIntOr("Life", 0));
		this.releaseTicks = Math.max(0, input.getIntOr("Releasing", 0));
		this.lastSafePos = input.read("SafePos", BlockPos.CODEC).orElse(null);
		this.role = Role.byOrdinal(input.getIntOr("Role", 0));
		this.entityData.set(DATA_ROLE, (byte) this.role.ordinal());
		int savedActivity = input.getIntOr("Activity", 0);
		BlockPos savedSpot = input.read("ActivitySpot", BlockPos.CODEC).orElse(null);
		if ((savedActivity >= ACTIVITY_WATER && savedActivity <= ACTIVITY_FORMATION_GUARD || savedActivity == ACTIVITY_FORMATION_KEEPER
				|| savedActivity == ACTIVITY_FORGE_ASSIST || sekteHolding(savedActivity)) && savedSpot != null) {
			if (sekteHolding(savedActivity) && !dev.kultivator.sekte.SekteIds.activityRuns(savedActivity)) {
				// S15: the module that owns this activity is switched off, so nothing would ever release it. The disciple
				// loads free (no activity); a sulker gets back the mode it had before it sulked.
				if (savedActivity == dev.kultivator.sekte.SekteIds.ACT_SULK && this.mode == Mode.STAY) {
					Mode before = dev.kultivator.sekte.kepribadian.Kepribadian.modeBeforeSulk(this.data);
					this.mode = before == null ? Mode.FOLLOW : before;
				}
			} else {
				this.activity = savedActivity;
				this.activitySpot = savedSpot;
				this.entityData.set(DATA_ACTIVITY, (byte) savedActivity);
			}
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
