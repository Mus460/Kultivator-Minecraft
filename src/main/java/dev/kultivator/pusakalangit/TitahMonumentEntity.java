package dev.kultivator.pusakalangit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * {@code kultivator:titah_monument} (DESIGN 29.6.1, contract C10b.4): the planted weapon of Pedang Pemisah Langit or
 * Tiang Penenang Samudra, a saved, inert entity (never a mob, never a block): no AI, no gravity, no physics, never hurt,
 * never pushed (entities, pistons, fluids), never hit by projectiles, never through a portal, drops nothing. For its
 * first 400 ticks (and when its carve job is DONE) it re-seats every 20 ticks onto the column's
 * {@code MOTION_BLOCKING_NO_LEAVES} height (a read-only call), so it stays planted while the crater forms. Every 100
 * ticks it asks {@link TitahMonuments} whether its record still stands; expired, revoked or missing, it rises and fades
 * (STATE 3, 40 ticks) and leaves. The owner pulls it out with sneak + use (STATE 2, 40 ticks, the owner within 4 blocks
 * and alive). {@link #onRemoval} hands a destroyed one (KILLED by {@code /kill}, DISCARDED by our own leave) to the
 * idempotent {@code TitahMonuments.release}. The client draws it with M3's {@code TitahMonumentRenderer} from the
 * synced data and the getters below; nothing here references a client class.
 */
public class TitahMonumentEntity extends Entity {

	public static final byte MODEL_SWORD = 0;
	public static final byte MODEL_PILLAR = 1;
	/** Only with {@code titahUltimateMonuments} (other Dewa ultimates; never a reward). */
	public static final byte MODEL_PUNISHING = 2;
	public static final byte MODEL_ZHUXIAN = 3;
	/** The {@code kultivator:fx/<id>} model of each MODEL value. */
	public static final String[] MODELS = {TitahArts.SWORD_MODEL, TitahArts.PILLAR_MODEL, "heaven_punishing_sword", "zhuxian_sword"};

	public static final byte STATE_SETTLING = 0;
	public static final byte STATE_STANDING = 1;
	public static final byte STATE_PULLING = 2;
	public static final byte STATE_LEAVING = 3;

	public static final int SETTLE_TICKS = 40;
	public static final int PULL_TICKS = 40;
	public static final int LEAVE_TICKS = 40;
	/** Re-seated every 20 ticks for this long after it was planted. */
	public static final int SEAT_TICKS = 400;
	public static final int CHECK_TICKS = 100;
	/** Hot (full-bright, heat 1 → 0) for this long. */
	public static final int HEAT_TICKS = 1200;
	/** The owner must stay this close while pulling. */
	public static final double PULL_RANGE = 4.0D;
	/** The client draws it up to this far (the tracking range of 10 chunks). */
	public static final double RENDER_RANGE = 160.0D;

	private static final EntityDataAccessor<Byte> DATA_MODEL = SynchedEntityData.defineId(TitahMonumentEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(TitahMonumentEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_TILT = SynchedEntityData.defineId(TitahMonumentEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(TitahMonumentEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Integer> DATA_STATE_TICK = SynchedEntityData.defineId(TitahMonumentEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<String> DATA_OWNER_NAME = SynchedEntityData.defineId(TitahMonumentEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Long> DATA_BORN = SynchedEntityData.defineId(TitahMonumentEntity.class, EntityDataSerializers.LONG);
	/** Phase 10b Tingkat (DESIGN 29.16.11, contract C10b.10): the record id and the Tingkat, synced for the far LOD. */
	private static final EntityDataAccessor<Integer> DATA_RECORD = SynchedEntityData.defineId(TitahMonumentEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Byte> DATA_TIER = SynchedEntityData.defineId(TitahMonumentEntity.class, EntityDataSerializers.BYTE);
	/** The largest model scale (29.16.1: mScale(t) ≤ 1100). */
	public static final float MAX_SCALE = 1100.0F;
	/** Model length per scale of each MODEL value (the renderer's {@code SWORD_TIP} / {@code PILLAR_HALF}; 29.16.1). */
	private static final float[] LENGTH_PER_SCALE = {1.87F, 3.0F, 1.87F, 1.87F};

	// ---- server only (saved)
	private int recordId = -1;
	private @Nullable UUID owner;
	private long expires;
	private boolean rewarded;
	private boolean test;
	private boolean ultimate;
	// ---- server only (session)
	private @Nullable UUID puller;
	private int seatUntil = SEAT_TICKS;

	public TitahMonumentEntity(EntityType<? extends TitahMonumentEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		setNoGravity(true);
		setPermanentlyInvulnerable(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_MODEL, MODEL_SWORD);
		builder.define(DATA_SCALE, 12.0F);
		builder.define(DATA_TILT, 0.0F);
		builder.define(DATA_STATE, STATE_SETTLING);
		builder.define(DATA_STATE_TICK, 0);
		builder.define(DATA_OWNER_NAME, "");
		builder.define(DATA_BORN, 0L);
		builder.define(DATA_RECORD, -1);
		builder.define(DATA_TIER, (byte) 1);
	}

	// ================================================================== contract C10b.4 (read by the client renderer)

	/** The {@code kultivator:fx/<id>} model id. */
	public String modelId() {
		int model = entityData.get(DATA_MODEL);
		return MODELS[Math.max(0, Math.min(MODELS.length - 1, model))];
	}

	/** The MODEL byte (0 sword, 1 pillar, 2-3 ultimate monuments). */
	public byte model() {
		return entityData.get(DATA_MODEL);
	}

	/** The model's display scale. */
	public float scale() {
		return entityData.get(DATA_SCALE);
	}

	/** The tilt in degrees toward the caster (the entity's yaw faces the caster). */
	public float tilt() {
		return entityData.get(DATA_TILT);
	}

	/** 0 settling, 1 standing, 2 pulling, 3 leaving. */
	public byte state() {
		return entityData.get(DATA_STATE);
	}

	/** Ticks (with the partial tick) since the current state began. */
	public float stateAge(float partial) {
		int now = (int) level().getGameTime();
		return Math.max(0.0F, now - entityData.get(DATA_STATE_TICK) + partial);
	}

	/** 1 when just planted, cooling to 0 over {@link #HEAT_TICKS} (the renderer draws it full-bright while hot). */
	public float heat(float partial) {
		float age = (float) (level().getGameTime() - entityData.get(DATA_BORN)) + partial;
		return Math.max(0.0F, Math.min(1.0F, 1.0F - age / HEAT_TICKS));
	}

	/** The owner's name for the look-at tooltip. */
	public String ownerName() {
		return entityData.get(DATA_OWNER_NAME);
	}

	/** Game time it was planted. */
	public long born() {
		return entityData.get(DATA_BORN);
	}

	/** The Tingkat of the decree that planted it (1-9; 1 for every weapon planted before Tingkat existed). */
	public int tier() {
		return Math.max(1, Math.min(9, entityData.get(DATA_TIER)));
	}

	/** The model's length in blocks at its scale (Pedang ≈ 1.87 × scale, Tiang ≈ 3 × scale). */
	public float length() {
		int model = Math.max(0, Math.min(LENGTH_PER_SCALE.length - 1, model()));
		return LENGTH_PER_SCALE[model] * scale();
	}

	// ================================================================== server setup and accessors

	/** Sets everything once, before {@code addFreshEntity} (Tingkat 1). */
	void setup(int record, UUID ownerId, String name, byte model, float scale, float tilt, long born, long expiresAt, boolean isTest,
			boolean isUltimate) {
		setup(record, ownerId, name, model, scale, tilt, born, expiresAt, isTest, isUltimate, 1);
	}

	/** Sets everything once, before {@code addFreshEntity}. */
	void setup(int record, UUID ownerId, String name, byte model, float scale, float tilt, long born, long expiresAt, boolean isTest,
			boolean isUltimate, int tier) {
		this.recordId = record;
		this.owner = ownerId;
		this.expires = expiresAt;
		this.test = isTest;
		this.ultimate = isUltimate;
		entityData.set(DATA_MODEL, model);
		entityData.set(DATA_SCALE, Math.max(0.5F, Math.min(MAX_SCALE, scale)));
		entityData.set(DATA_TILT, tilt);
		entityData.set(DATA_OWNER_NAME, name == null ? "" : name);
		entityData.set(DATA_BORN, born);
		entityData.set(DATA_RECORD, record);
		entityData.set(DATA_TIER, (byte) Math.max(1, Math.min(9, tier)));
		setState(STATE_SETTLING, born);
	}

	/** The {@code TitahMonuments} record id (synced, so the client can match it with {@code TitahFar}). */
	public int recordId() {
		return level().isClientSide() ? entityData.get(DATA_RECORD) : recordId;
	}

	public @Nullable UUID owner() {
		return owner;
	}

	boolean rewarded() {
		return rewarded;
	}

	boolean test() {
		return test;
	}

	boolean ultimate() {
		return ultimate;
	}

	private void setState(byte state, long gameTime) {
		entityData.set(DATA_STATE, state);
		entityData.set(DATA_STATE_TICK, (int) gameTime);
	}

	/** A state change while it stands in the world: the far clients hear of it too ({@code TitahFar}, 29.16.11). */
	private void changeState(byte state, long gameTime) {
		setState(state, gameTime);
		if (level() instanceof ServerLevel level && recordId >= 0) {
			TitahMonuments.noteState(level.getServer(), recordId, state);
		}
	}

	// ================================================================== tick (server)

	@Override
	public void tick() {
		// no baseTick: no fire, fluids, portals or physics; the client only draws the synced state
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		long age = now - born();
		byte state = state();
		if (state == STATE_SETTLING && age >= SETTLE_TICKS) {
			changeState(STATE_STANDING, now);
			state = STATE_STANDING;
		}
		if (age <= seatUntil && tickCount % 20 == 0) {
			seat(level);
		}
		if (state == STATE_PULLING) {
			tickPull(level, now);
			return;
		}
		if (state == STATE_LEAVING) {
			if (stateTicks(now) >= LEAVE_TICKS) {
				discard();
			}
			return;
		}
		if ((tickCount + getId()) % CHECK_TICKS == 0 && TitahMonuments.status(level.getServer(), recordId, getUUID(), now) != TitahMonuments.Status.LIVE) {
			leave();
		}
	}

	/** Re-seats onto the column's surface (a read-only heightmap call; loaded chunks only). */
	void seat(ServerLevel level) {
		BlockPos column = BlockPos.containing(getX(), getY(), getZ());
		if (!level.isLoaded(column)) {
			return;
		}
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
		if (top <= level.getMinY()) {
			return;
		}
		if (Math.abs(top - getY()) > 0.01D) {
			double before = getY();
			setPos(getX(), top, getZ());
			if (Math.abs(top - before) >= 2.0D && recordId >= 0) {
				// a crater formed under it: the far clients' copy moves too (TitahFar)
				TitahMonuments.noteSeat(level.getServer(), recordId, getX(), top, getZ());
			}
		}
	}

	/** The carve job is done: re-seat now and keep re-seating for another 400 ticks. */
	void jobDone() {
		if (level() instanceof ServerLevel level) {
			seat(level);
			seatUntil = (int) Math.min(Integer.MAX_VALUE, level.getGameTime() - born() + SEAT_TICKS);
		}
	}

	/** Expired, revoked or missing: rise and fade over 40 ticks, then discard. */
	void leave() {
		if (state() != STATE_LEAVING) {
			puller = null;
			changeState(STATE_LEAVING, level().getGameTime());
		}
	}

	private void tickPull(ServerLevel level, long now) {
		ServerPlayer player = puller == null ? null : level.getServer().getPlayerList().getPlayer(puller);
		if (player == null || !player.isAlive() || player.level() != level || Math.abs(player.getY() - getY()) > 8.0D
				|| horizontalDistance(player) > PULL_RANGE) {
			puller = null;
			changeState(STATE_STANDING, now);
			if (player != null) {
				dev.kultivator.util.Msg.actionBar(player, Component.translatable("kultivator.pusaka_langit.pull.cancel").withColor(0xFF8A7A));
			}
			return;
		}
		if (stateTicks(now) >= PULL_TICKS) {
			finishPull(player);
		}
	}

	/** Ticks since the current state began (the state tick is the int game time). */
	private int stateTicks(long now) {
		return (int) now - entityData.get(DATA_STATE_TICK);
	}

	private double horizontalDistance(Entity other) {
		double dx = other.getX() - getX();
		double dz = other.getZ() - getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** The pull ended (or {@code uji pusaka_langit cabut}): the reward if eligible, the clear, the PULL beat, then leave. */
	void finishPull(ServerPlayer player) {
		if (isRemoved()) {
			return;
		}
		boolean gift = TitahMonuments.pulled(this, player);
		rewarded = rewarded || gift;
		puller = null;
		discard();
	}

	/** Pulling: owner and puller. */
	public boolean pulling() {
		return state() == STATE_PULLING;
	}

	// ================================================================== interaction

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (!(player instanceof ServerPlayer serverPlayer) || !(level() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		byte state = state();
		if (state == STATE_PULLING || state == STATE_LEAVING) {
			return InteractionResult.SUCCESS_SERVER;
		}
		if (owner == null || !owner.equals(serverPlayer.getUUID())) {
			dev.kultivator.util.Msg.actionBar(serverPlayer, Component.translatable("kultivator.pusaka_langit.pull.not_owner", ownerName())
					.withColor(0xFFD54A));
			return InteractionResult.SUCCESS_SERVER;
		}
		if (!serverPlayer.isShiftKeyDown()) {
			dev.kultivator.util.Msg.actionBar(serverPlayer, Component.translatable("kultivator.pusaka_langit.pull.hint").withColor(0xE8E0C0));
			return InteractionResult.SUCCESS_SERVER;
		}
		puller = serverPlayer.getUUID();
		changeState(STATE_PULLING, level.getGameTime());
		dev.kultivator.util.Msg.actionBar(serverPlayer, Component.translatable("kultivator.pusaka_langit.pull.start").withColor(0xFFF4D0));
		level.playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, net.minecraft.sounds.SoundSource.PLAYERS,
				1.4F, 0.6F);
		return InteractionResult.SUCCESS_SERVER;
	}

	// ================================================================== inert

	@Override
	public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isPickable() {
		// targetable for "use" (the pull); the ray-hit of projectiles is refused below
		return state() != STATE_LEAVING;
	}

	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return false;
	}

	@Override
	public PushReaction getPistonPushReaction() {
		return PushReaction.IGNORE_ENTITY;
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
	public boolean skipAttackInteraction(Entity source) {
		return true;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		// 29.16.10: a giant weapon stays drawn up to max(160, 1.5 × its length) (the far LOD takes over beyond tracking)
		double range = Math.max(RENDER_RANGE, 1.5D * length());
		return distance < range * range;
	}

	@Override
	public void onRemoval(Entity.RemovalReason reason) {
		super.onRemoval(reason);
		// KILLED (/kill) and DISCARDED (our own leave or pull) destroy it: the record goes and its block monument is cleared.
		// UNLOADED_* and CHANGED_DIMENSION keep the record. release() is idempotent (our discard() arrives here too).
		if (reason.shouldDestroy() && recordId >= 0 && level() instanceof ServerLevel level) {
			TitahMonuments.release(level.getServer(), recordId);
		}
	}

	// ================================================================== save (29.6.1)

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		recordId = input.getIntOr("record", -1);
		entityData.set(DATA_RECORD, recordId);
		entityData.set(DATA_TIER, (byte) Math.max(1, Math.min(9, input.getIntOr("tier", 1))));
		owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
		expires = input.getLongOr("expires", 0L);
		rewarded = input.getBooleanOr("rewarded", false);
		test = input.getBooleanOr("test", false);
		ultimate = input.getBooleanOr("ultimate", false);
		entityData.set(DATA_MODEL, (byte) Math.max(0, Math.min(MODELS.length - 1, input.getIntOr("model", 0))));
		entityData.set(DATA_SCALE, Math.max(0.5F, Math.min(MAX_SCALE, input.getFloatOr("scale", 12.0F))));
		entityData.set(DATA_TILT, input.getFloatOr("tilt", 0.0F));
		entityData.set(DATA_OWNER_NAME, input.getStringOr("owner_name", ""));
		long born = input.getLongOr("born", 0L);
		entityData.set(DATA_BORN, born);
		// a pull or a leave in progress is never resumed after a reload: it stands again (the record check decides the rest)
		byte state = (byte) input.getIntOr("state", STATE_STANDING);
		setState(state == STATE_SETTLING ? STATE_SETTLING : STATE_STANDING, born);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("record", recordId);
		output.putInt("tier", tier());
		if (owner != null) {
			output.store("owner", UUIDUtil.CODEC, owner);
		}
		output.putLong("expires", expires);
		output.putBoolean("rewarded", rewarded);
		output.putBoolean("test", test);
		output.putBoolean("ultimate", ultimate);
		output.putInt("model", model());
		output.putFloat("scale", scale());
		output.putFloat("tilt", tilt());
		output.putString("owner_name", ownerName());
		output.putLong("born", born());
		output.putInt("state", state() == STATE_SETTLING ? STATE_SETTLING : STATE_STANDING);
	}
}
