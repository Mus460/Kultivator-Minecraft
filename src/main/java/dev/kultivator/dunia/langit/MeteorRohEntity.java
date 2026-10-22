package dev.kultivator.dunia.langit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Meteor Roh (DESIGN 34.5.8): a spirit meteor of the Hujan Meteor Roh night. A plain entity on a scripted path, no
 * gravity, no physics, no collision, never hurt, never saved: it slides from {@code landing + offset} down to
 * {@code landing} (the heightmap top, found when it was spawned) over {@link #fallTicks()} with an accelerating ease, then
 * {@link SkyEvents#meteorLanded} drops a Batu Meteor Roh and sends the impact visual. Never a block, never fire, never an
 * explosion: nothing in the world changes.
 * <p>
 * The client moves its copy along the same path from the synced landing, offset and birth time (the server sends no
 * position updates for this type), so the fall is smooth on every frame and identical for every viewer.
 */
public class MeteorRohEntity extends Entity {

	public static final int DEFAULT_FALL = 64;
	/** A Meteor Agung falls slower and longer, so everyone around sees it cross the sky. */
	public static final int GREAT_FALL = 140;

	private static final EntityDataAccessor<BlockPos> DATA_LANDING = SynchedEntityData.defineId(MeteorRohEntity.class, EntityDataSerializers.BLOCK_POS);
	private static final EntityDataAccessor<Vector3fc> DATA_OFFSET = SynchedEntityData.defineId(MeteorRohEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Long> DATA_BORN = SynchedEntityData.defineId(MeteorRohEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Integer> DATA_FALL = SynchedEntityData.defineId(MeteorRohEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(MeteorRohEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_GREAT = SynchedEntityData.defineId(MeteorRohEntity.class, EntityDataSerializers.BOOLEAN);

	private boolean landed;

	public MeteorRohEntity(EntityType<? extends MeteorRohEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		entityData.define(DATA_LANDING, BlockPos.ZERO);
		entityData.define(DATA_OFFSET, new Vector3f(0.0F, 100.0F, 0.0F));
		entityData.define(DATA_BORN, 0L);
		entityData.define(DATA_FALL, DEFAULT_FALL);
		entityData.define(DATA_SEED, 0);
		entityData.define(DATA_GREAT, false);
	}

	/** Sets the path (before the entity is added): lands on top of {@code landing}, starts at {@code landing + offset}. */
	public void setup(BlockPos landing, Vector3fc offset, int fallTicks, int seed) {
		setup(landing, offset, fallTicks, seed, false);
	}

	/** {@link #setup(BlockPos, Vector3fc, int, int)} for a common meteor or ({@code great}) a Meteor Agung. */
	public void setup(BlockPos landing, Vector3fc offset, int fallTicks, int seed, boolean great) {
		entityData.set(DATA_GREAT, great);
		entityData.set(DATA_LANDING, landing.immutable());
		entityData.set(DATA_OFFSET, new Vector3f(offset));
		entityData.set(DATA_BORN, level().getGameTime());
		entityData.set(DATA_FALL, Math.max(10, fallTicks));
		entityData.set(DATA_SEED, seed);
		Vec3 start = positionAt(0.0D);
		setPos(start.x, start.y, start.z);
	}

	public BlockPos landing() {
		return entityData.get(DATA_LANDING);
	}

	public Vector3fc offset() {
		return entityData.get(DATA_OFFSET);
	}

	public int fallTicks() {
		return entityData.get(DATA_FALL);
	}

	public int seed() {
		return entityData.get(DATA_SEED);
	}

	/** True for a Meteor Agung (bigger, slower, a crater glow and the Inti Meteor Agung where it lands). */
	public boolean great() {
		return entityData.get(DATA_GREAT);
	}

	/** Ticks since the meteor appeared (game time, so a viewer who starts tracking late is in step). */
	public double age(float partial) {
		return Math.max(0.0D, level().getGameTime() - entityData.get(DATA_BORN) + partial);
	}

	/** 0 at the start of the fall .. 1 at the ground. */
	public double progress(double age) {
		return Math.min(1.0D, Math.max(0.0D, age / fallTicks()));
	}

	/** The point of the path at {@code age} ticks: an accelerating ease ({@code t^1.7}) from the start to the ground. */
	public Vec3 positionAt(double age) {
		BlockPos land = landing();
		Vector3fc offset = offset();
		double t = Math.pow(progress(age), 1.7D);
		double k = 1.0D - t;
		return new Vec3(land.getX() + 0.5D + offset.x() * k, land.getY() + 0.6D + offset.y() * k, land.getZ() + 0.5D + offset.z() * k);
	}

	/** The unit direction of flight (towards the ground). */
	public Vec3 direction() {
		Vector3fc offset = offset();
		Vec3 dir = new Vec3(-offset.x(), -offset.y(), -offset.z());
		double length = dir.length();
		return length < 1.0E-4D ? new Vec3(0.0D, -1.0D, 0.0D) : dir.scale(1.0D / length);
	}

	@Override
	public void tick() {
		super.tick();
		double age = age(0.0F);
		Vec3 now = positionAt(age);
		setPos(now.x, now.y, now.z);
		setDeltaMovement(Vec3.ZERO);
		if (level() instanceof ServerLevel serverLevel && !landed && age >= fallTicks()) {
			landed = true;
			SkyEvents.meteorLanded(serverLevel, this);
			discard();
		}
		if (!level().isClientSide() && age > fallTicks() + 40) {
			discard();
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < (great() ? 512.0D * 512.0D : 256.0D * 256.0D);
	}

	@Override
	public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
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
	public PushReaction getPistonPushReaction() {
		return PushReaction.IGNORE_ENTITY;
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean displayFireAnimation() {
		return false;
	}
}
