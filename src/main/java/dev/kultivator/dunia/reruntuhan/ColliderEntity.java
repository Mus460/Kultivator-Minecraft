package dev.kultivator.dunia.reruntuhan;

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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * A solid part that stops players (DESIGN 34.0 rule W2): a Gerbang Segel (the sealed stone doors of a ruin, the
 * prison's iron gate, the tower's bronze doors) or a phantom wall of the Labirin Ilusi. It is an entity, never a block:
 * its box is any size ({@link #makeBoundingBox} reads the synced half extents, so client and server agree) and it is
 * collidable through {@code canBeCollidedWith} (the {@code AbstractBoat} / {@code Shulker} pattern, checked in mcsrc)
 * until it opens. The client draws it with block Displays posed through {@code DisplayKey}: the panels slide apart
 * over {@link #OPEN_TICKS} ticks when it opens. Never saved (state lives in the anchor), never hurt.
 * <p>
 * Synced: half extents, the look block, flags ({@link #FLAG_OPEN}, {@link #FLAG_PHANTOM}, {@link #FLAG_REVEALED},
 * {@link #FLAG_GATE}, {@link #FLAG_PRISON}, {@link #FLAG_TOWER}), the game time it opened at and the glyph of the seal.
 */
public class ColliderEntity extends Entity {

	public static final int FLAG_OPEN = 1;
	public static final int FLAG_PHANTOM = 2;
	public static final int FLAG_REVEALED = 4;
	/** A door with seal panels (else a plain wall block). */
	public static final int FLAG_GATE = 8;
	public static final int FLAG_PRISON = 16;
	public static final int FLAG_TOWER = 32;
	/** Clickable (the outer gate of a ruin, a revealed phantom wall, the tower gate). */
	public static final int FLAG_CLICK = 64;

	/** The slide of the panels: 1.2 s (DESIGN 34.5.5). */
	public static final int OPEN_TICKS = 24;

	public static final String TAG = "kultivator.reruntuhan_collider";

	private static final EntityDataAccessor<Vector3fc> DATA_HALF = SynchedEntityData.defineId(ColliderEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<BlockState> DATA_LOOK = SynchedEntityData.defineId(ColliderEntity.class, EntityDataSerializers.BLOCK_STATE);
	private static final EntityDataAccessor<Integer> DATA_FLAGS = SynchedEntityData.defineId(ColliderEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Long> DATA_OPENED = SynchedEntityData.defineId(ColliderEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Integer> DATA_GLYPH = SynchedEntityData.defineId(ColliderEntity.class, EntityDataSerializers.INT);

	private TekaPartEntity.@Nullable Listener clickListener;
	/** Server only: true while a puzzle, gate or seal holds this collider (a stray one leaves). */
	private boolean owned;
	private int age;

	public ColliderEntity(EntityType<? extends ColliderEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_HALF, new Vector3f(0.5F, 1.5F, 0.5F));
		builder.define(DATA_LOOK, Blocks.STONE_BRICKS.defaultBlockState());
		builder.define(DATA_FLAGS, 0);
		builder.define(DATA_OPENED, 0L);
		builder.define(DATA_GLYPH, 0);
	}

	// ================================================================== setup

	/**
	 * Sizes the collider: {@code hx} and {@code hz} are half widths, {@code height} the full height from the entity's feet.
	 * Call before adding it to the level.
	 */
	public void setup(float hx, float height, float hz, BlockState look, int flags, int glyph) {
		entityData.set(DATA_HALF, new Vector3f(Math.max(0.05F, hx), Math.max(0.1F, height), Math.max(0.05F, hz)));
		entityData.set(DATA_LOOK, look);
		entityData.set(DATA_FLAGS, flags);
		entityData.set(DATA_GLYPH, glyph);
		this.setBoundingBox(this.makeBoundingBox());
	}

	public Vector3fc half() {
		return entityData.get(DATA_HALF);
	}

	public BlockState look() {
		return entityData.get(DATA_LOOK);
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

	public int glyph() {
		return entityData.get(DATA_GLYPH);
	}

	/** The game time the door opened (0 when closed). */
	public long openedAt() {
		return entityData.get(DATA_OPENED);
	}

	public boolean isOpen() {
		return has(FLAG_OPEN);
	}

	/** Opens (no collision from now on; the client slides the panels apart). */
	public void open() {
		if (!isOpen()) {
			entityData.set(DATA_OPENED, level().getGameTime());
			setFlag(FLAG_OPEN, true);
		}
	}

	/** Closes again (a puzzle reset). */
	public void close() {
		if (isOpen()) {
			setFlag(FLAG_OPEN, false);
			entityData.set(DATA_OPENED, 0L);
		}
	}

	public void setClickListener(TekaPartEntity.@Nullable Listener listener) {
		this.clickListener = listener;
	}

	/** Marks the collider as held by a puzzle, gate or seal (server only). */
	public void setOwned(boolean owned) {
		this.owned = owned;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_HALF.equals(accessor)) {
			this.setBoundingBox(this.makeBoundingBox());
		}
	}

	@Override
	protected AABB makeBoundingBox(Vec3 position) {
		Vector3fc h = entityData.get(DATA_HALF);
		return new AABB(position.x - h.x(), position.y, position.z - h.z(), position.x + h.x(), position.y + h.y(), position.z + h.z());
	}

	// ================================================================== collision and clicks

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return !isOpen() && !this.isRemoved();
	}

	@Override
	public boolean isPickable() {
		return !this.isRemoved() && has(FLAG_CLICK) && !isOpen();
	}

	@Override
	public boolean isPushable() {
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
	public boolean canBeHitByProjectile() {
		return !isOpen();
	}

	@Override
	public boolean skipAttackInteraction(Entity source) {
		if (source instanceof ServerPlayer player && isPickable()) {
			fire(player, true);
		}
		return true;
	}

	@Override
	public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (!isPickable()) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer && hand == InteractionHand.MAIN_HAND) {
			fire(serverPlayer, false);
		}
		return InteractionResult.SUCCESS;
	}

	private void fire(ServerPlayer player, boolean attack) {
		TekaPartEntity.Listener current = clickListener;
		if (current == null || player.isSpectator()) {
			return;
		}
		try {
			current.use(player, null, attack);
		} catch (RuntimeException exception) {
			dev.kultivator.Kultivator.LOGGER.error("Kultivator: a ruin gate failed on use", exception);
		}
	}

	@Override
	public void tick() {
		age++;
		if (!this.level().isClientSide() && !owned && age > 40) {
			this.discard();
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}
}
