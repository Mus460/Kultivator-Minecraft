package dev.kultivator.dunia.dimensi;

import dev.kultivator.disciple.DuelRules;
import dev.kultivator.util.NoGrief;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * The shared body of module D's people (Meng Po, the judges and their guards, the ghosts; the Dragon King, his guard, the
 * hermits and the sea merchant): a {@link PathfinderMob}, never an {@code Enemy}, drawn with the disciple player model
 * on the client. It is never saved (the types are {@code noSave}, {@link #TAG} is refused on load); the anchor or quest
 * that made it re-makes it. It never picks anything up, never opens doors ({@link NoGrief#TAG}: never tramples farmland)
 * and never takes a custom name. Officials take no damage outside a {@link DuelRules} duel; hostile roles fight like
 * mobs (and so count as hostile for area techniques only while they target the caster).
 * <p>
 * Synced: role, look seed, variant (the court number for judges), flags ({@link #FLAG_FOLLOW}, {@link #FLAG_QUEST},
 * {@link #FLAG_FIGHT}). Server only: home, the escorted player, the making anchor, the lonely-despawn counter.
 * AI is light: custom goals run every 4th tick, every 10th with no player within 24 b (DESIGN 34.4.4 throttle).
 */
public abstract class DimensiNpc extends PathfinderMob {

	public static final String TAG = "kultivator.dimensi_npc";
	public static final int FLAG_FOLLOW = 1;
	public static final int FLAG_QUEST = 2;
	public static final int FLAG_FIGHT = 4;

	private static final EntityDataAccessor<Integer> DATA_ROLE = SynchedEntityData.defineId(DimensiNpc.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_LOOK = SynchedEntityData.defineId(DimensiNpc.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(DimensiNpc.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_FLAGS = SynchedEntityData.defineId(DimensiNpc.class, EntityDataSerializers.INT);

	private @Nullable BlockPos home;
	private int leash = 10;
	private @Nullable UUID follow;
	private @Nullable UUID anchor;
	private int lonely;
	/** Free text the owning system keeps on the NPC (quest kind, the escort's court). */
	private String note = "";

	protected DimensiNpc(EntityType<? extends DimensiNpc> type, Level level) {
		super(type, level);
		this.xpReward = 0;
		this.setCanPickUpLoot(false);
		this.getNavigation().setCanOpenDoors(false);
		this.addTag(NoGrief.TAG);
		this.addTag(TAG);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 40.0D)
				.add(Attributes.ATTACK_DAMAGE, 6.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.28D)
				.add(Attributes.FOLLOW_RANGE, 32.0D)
				.add(Attributes.ARMOR, 4.0D)
				.add(Attributes.STEP_HEIGHT, 1.0D);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ROLE, 0);
		builder.define(DATA_LOOK, 0);
		builder.define(DATA_VARIANT, 0);
		builder.define(DATA_FLAGS, 0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.15D, true) {
			@Override
			public boolean canUse() {
				return fighting() && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return fighting() && super.canContinueToUse();
			}
		});
		this.goalSelector.addGoal(2, new FollowGoal(this));
		this.goalSelector.addGoal(3, new HomeGoal(this));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7D) {
			@Override
			public boolean canUse() {
				return wanders() && !hasFlag(FLAG_FOLLOW) && super.canUse();
			}
		});
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true,
				(target, level) -> hostileToPlayers() && target instanceof Player player && !player.isCreative() && !player.isSpectator()));
	}

	// ================================================================== identity

	/** Sets role, look seed and variant; the subclass then applies the role's numbers. */
	protected void setIdentity(int role, int look, int variant) {
		this.entityData.set(DATA_ROLE, role);
		this.entityData.set(DATA_LOOK, look);
		this.entityData.set(DATA_VARIANT, variant);
	}

	public int roleIndex() {
		return this.entityData.get(DATA_ROLE);
	}

	public int look() {
		return this.entityData.get(DATA_LOOK);
	}

	public int variant() {
		return this.entityData.get(DATA_VARIANT);
	}

	public int flags() {
		return this.entityData.get(DATA_FLAGS);
	}

	public boolean hasFlag(int flag) {
		return (flags() & flag) != 0;
	}

	public void setFlag(int flag, boolean on) {
		int value = on ? flags() | flag : flags() & ~flag;
		if (value != flags()) {
			this.entityData.set(DATA_FLAGS, value);
		}
	}

	protected void setBase(Holder<Attribute> attribute, double value) {
		AttributeInstance instance = this.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	/** Health, melee and armour, full health afterwards. */
	public void setStats(double health, double attack, double armor, double speed) {
		setBase(Attributes.MAX_HEALTH, Math.max(1.0D, health));
		setBase(Attributes.ATTACK_DAMAGE, Math.max(0.0D, attack));
		setBase(Attributes.ARMOR, Math.max(0.0D, armor));
		setBase(Attributes.MOVEMENT_SPEED, speed);
		this.setHealth(this.getMaxHealth());
	}

	public @Nullable BlockPos home() {
		return home;
	}

	public void setHome(@Nullable BlockPos home, int leash) {
		this.home = home;
		this.leash = Math.max(2, leash);
	}

	public @Nullable UUID following() {
		return follow;
	}

	/** Follows {@code player} (an escort) or stops following ({@code null}). */
	public void setFollow(@Nullable UUID player) {
		this.follow = player;
		setFlag(FLAG_FOLLOW, player != null);
	}

	public @Nullable UUID anchor() {
		return anchor;
	}

	public void setAnchor(@Nullable UUID anchor) {
		this.anchor = anchor;
	}

	public String note() {
		return note;
	}

	public void setNote(String note) {
		this.note = note == null ? "" : note;
	}

	/** True while it fights (a hostile role, or a duel partner). */
	public boolean fighting() {
		return hasFlag(FLAG_FIGHT) || hostileToPlayers();
	}

	/** Strolls around its home (wandering ghosts, hungry ghosts). */
	protected abstract boolean wanders();

	/** Hunts players on sight (hungry ghosts). */
	protected abstract boolean hostileToPlayers();

	/** May be hurt by {@code source} (officials only inside a duel). */
	protected boolean vulnerableTo(DamageSource source) {
		Entity attacker = source.getEntity();
		return DuelRules.inDuel(this) && attacker != null && DuelRules.dueling(this, attacker);
	}

	/** A right-click of the player (server). */
	protected abstract boolean onInteract(ServerPlayer player);

	// ================================================================== behaviour

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (!vulnerableTo(source) && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level && this.tickCount % 20 == 0 && !this.isRemoved()) {
			if (level.getNearestPlayer(this, 64.0D) == null) {
				if (++lonely >= 30) {
					this.discard();
				}
			} else {
				lonely = 0;
			}
			if (hasFlag(FLAG_FIGHT) && !DuelRules.inDuel(this)) {
				setFlag(FLAG_FIGHT, false);
				this.setTarget(null);
			}
		}
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		return onInteract(serverPlayer) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
	}

	@Override
	public void setCustomName(@Nullable Component name) {
		// Never: formations skip custom-named mobs; the type name carries the role's title.
	}

	@Override
	protected void onBelowWorld() {
		this.discard();
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
	public void setTarget(@Nullable LivingEntity target) {
		if (target instanceof DimensiNpc) {
			return;
		}
		super.setTarget(target);
	}

	// ================================================================== goals

	/** Follows the escorted player: walks within 3 b, catches up by teleport beyond 24 b (same level only). */
	static final class FollowGoal extends Goal {

		private final DimensiNpc npc;
		private int wait;

		FollowGoal(DimensiNpc npc) {
			this.npc = npc;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		private @Nullable Player owner() {
			UUID id = npc.follow;
			return id == null ? null : npc.level().getPlayerByUUID(id);
		}

		@Override
		public boolean canUse() {
			Player owner = owner();
			return owner != null && owner.isAlive() && npc.distanceToSqr(owner) > 9.0D;
		}

		@Override
		public boolean canContinueToUse() {
			Player owner = owner();
			return owner != null && owner.isAlive() && npc.distanceToSqr(owner) > 4.0D;
		}

		@Override
		public void tick() {
			Player owner = owner();
			if (owner == null) {
				return;
			}
			npc.getLookControl().setLookAt(owner, 10.0F, npc.getMaxHeadXRot());
			if (--wait > 0) {
				return;
			}
			wait = 8;
			if (npc.distanceToSqr(owner) > 24.0D * 24.0D) {
				npc.snapTo(owner.getX(), owner.getY(), owner.getZ(), npc.getYRot(), npc.getXRot());
				npc.getNavigation().stop();
				return;
			}
			npc.getNavigation().moveTo(owner, 1.1D);
		}

		@Override
		public void stop() {
			npc.getNavigation().stop();
		}
	}

	/** Walks back toward its home when it strays beyond its leash (officials, quest ghosts). */
	static final class HomeGoal extends Goal {

		private final DimensiNpc npc;

		HomeGoal(DimensiNpc npc) {
			this.npc = npc;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			BlockPos home = npc.home;
			return home != null && !npc.hasFlag(FLAG_FOLLOW) && npc.getTarget() == null
					&& npc.blockPosition().distSqr(home) > (double) npc.leash * npc.leash && npc.tickCount % 4 == 0;
		}

		@Override
		public boolean canContinueToUse() {
			BlockPos home = npc.home;
			return home != null && !npc.getNavigation().isDone() && npc.blockPosition().distSqr(home) > 4.0D;
		}

		@Override
		public void start() {
			BlockPos home = npc.home;
			if (home != null) {
				if (npc.blockPosition().distSqr(home) > 48.0D * 48.0D) {
					npc.snapTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, npc.getYRot(), npc.getXRot());
				} else {
					npc.getNavigation().moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, 0.9D);
				}
			}
		}
	}
}
