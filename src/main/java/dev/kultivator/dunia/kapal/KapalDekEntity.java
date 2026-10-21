package dev.kultivator.dunia.kapal;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * {@code kultivator:kapal_dek}: one deck collider of a docked ship (DESIGN 34.5.15, rule W2: the {@code AbstractBoat} /
 * {@code Shulker} pattern, {@code canBeCollidedWith} true, so players stand and walk on it). A flat box
 * {@code width × 0.5 × depth} (synced, so the client predicts the same collision) whose top is the deck surface. Never
 * saved, never drawn (the ship's renderer draws the deck); it removes itself when its ship is gone or no longer docked.
 * Right-click on it is a right-click on the ship (board, or sneak for the helm).
 */
public class KapalDekEntity extends Entity {

	public static final float THICKNESS = 0.5F;

	private static final EntityDataAccessor<Float> DATA_WIDTH = SynchedEntityData.defineId(KapalDekEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_DEPTH = SynchedEntityData.defineId(KapalDekEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> DATA_SHIP = SynchedEntityData.defineId(KapalDekEntity.class, EntityDataSerializers.INT);

	public KapalDekEntity(EntityType<? extends KapalDekEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_WIDTH, 3.0F);
		builder.define(DATA_DEPTH, 3.0F);
		builder.define(DATA_SHIP, -1);
	}

	void setup(KapalRohEntity ship, float width, float depth) {
		entityData.set(DATA_SHIP, ship.getId());
		entityData.set(DATA_WIDTH, Math.max(1.0F, Math.min(24.0F, width)));
		entityData.set(DATA_DEPTH, Math.max(1.0F, Math.min(24.0F, depth)));
		setBoundingBox(makeBoundingBox());
	}

	public float width() {
		return entityData.get(DATA_WIDTH);
	}

	public float depth() {
		return entityData.get(DATA_DEPTH);
	}

	/** The ship this deck belongs to (loaded on this side), or null. */
	public @Nullable KapalRohEntity ship() {
		return level().getEntity(entityData.get(DATA_SHIP)) instanceof KapalRohEntity ship ? ship : null;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_WIDTH.equals(accessor) || DATA_DEPTH.equals(accessor)) {
			refreshDimensions();
		}
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		return EntityDimensions.fixed(Math.max(width(), depth()), THICKNESS);
	}

	@Override
	protected AABB makeBoundingBox(Vec3 position) {
		double hx = width() * 0.5D;
		double hz = depth() * 0.5D;
		return new AABB(position.x - hx, position.y, position.z - hz, position.x + hx, position.y + THICKNESS, position.z + hz);
	}

	@Override
	public void tick() {
		if (!(level() instanceof ServerLevel)) {
			return;
		}
		if ((tickCount & 7) == 0) {
			KapalRohEntity ship = ship();
			if (ship == null || ship.isRemoved() || !ship.docked()) {
				discard();
			}
		}
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		KapalRohEntity ship = ship();
		return ship == null ? InteractionResult.PASS : ship.board(serverPlayer);
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return true;
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean skipAttackInteraction(Entity source) {
		return true;
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
	public boolean canBeHitByProjectile() {
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
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
