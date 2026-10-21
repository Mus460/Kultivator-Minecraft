package dev.kultivator.dunia.dimensi.laut;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * {@code kultivator:penyu_dek}: one of the four deck colliders on Penyu Ao's back (DESIGN 34.0 W2: moving parts are
 * entities). 5 × 0.5 b, invisible (no renderer geometry), collidable only for a living body whose feet are at or above
 * its top: players can stand on it but never bump into it from the side or below (the happy ghast rule, verified in
 * 26.3 {@code HappyGhast.canBeCollidedWith}). Never saved; it removes itself when its turtle is gone.
 */
public class PenyuDekEntity extends Entity {

	public static final float WIDTH = 5.0F;
	public static final float HEIGHT = 0.5F;
	private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(PenyuDekEntity.class, EntityDataSerializers.INT);

	private int orphan;

	public PenyuDekEntity(EntityType<? extends PenyuDekEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_OWNER, -1);
	}

	public void setOwner(int id) {
		this.entityData.set(DATA_OWNER, id);
	}

	public int owner() {
		return this.entityData.get(DATA_OWNER);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level && this.tickCount % 20 == 0) {
			Entity turtle = level.getEntity(owner());
			if (!(turtle instanceof PenyuAoEntity) || turtle.isRemoved()) {
				if (++orphan >= 2) {
					this.discard();
				}
			} else {
				orphan = 0;
			}
		}
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return other instanceof LivingEntity living && living.getBoundingBox().minY >= this.getBoundingBox().maxY - 0.35D;
	}

	@Override
	public boolean isPickable() {
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
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}
}
