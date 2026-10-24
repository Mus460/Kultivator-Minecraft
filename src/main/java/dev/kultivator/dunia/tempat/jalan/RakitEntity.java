package dev.kultivator.dunia.tempat.jalan;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * {@code kultivator:rakit}: the bamboo ferry raft of a wide-river crossing (DESIGN 38.C.6.2, 38.O.2). An entity, never a
 * block (38.0 rule 1): never saved, made by its dock anchor ({@code pos/penyeberangan}) while a player is near and
 * removed when the dock unloads; invulnerable, not pushable. Using it pays the ferryman 1 NB ({@link Penyeberangan}):
 * the passenger sits, the raft is poled out over the water for 4 s ({@link #DAYUNG_TICKS}, synced flag
 * {@link #sedangMendayung} for the rowing pose of 38MD's renderer), then the passenger steps off on the far bank's dock
 * and the raft is back at its mooring.
 */
public class RakitEntity extends Entity {

	/** Length of the rowing scene (ticks). */
	public static final int DAYUNG_TICKS = 80;
	private static final EntityDataAccessor<Boolean> DATA_DAYUNG = SynchedEntityData.defineId(RakitEntity.class, EntityDataSerializers.BOOLEAN);

	private @Nullable BlockPos tambat;
	private @Nullable Vec3 arah;
	private @Nullable Vec3 tujuan;
	private int sisa;

	public RakitEntity(EntityType<? extends RakitEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.setPermanentlyInvulnerable(true);
	}

	/** Where the raft waits (its dock), set by the dock anchor. */
	public void tambat(BlockPos at) {
		this.tambat = at.immutable();
	}

	public @Nullable BlockPos tambatan() {
		return tambat;
	}

	/** True while the ferryman poles (client: rowing pose and paddle splashes). */
	public boolean sedangMendayung() {
		return this.entityData.get(DATA_DAYUNG);
	}

	/** True while a crossing is under way (no second fare). */
	public boolean sibuk() {
		return sisa > 0;
	}

	/** Starts the crossing toward {@code tujuan} (the far bank), moving along {@code arah} for the scene. */
	void mulai(Vec3 arah, Vec3 tujuan) {
		this.arah = arah.normalize();
		this.tujuan = tujuan;
		this.sisa = DAYUNG_TICKS;
		this.entityData.set(DATA_DAYUNG, Boolean.TRUE);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (sisa > 0 && arah != null) {
			sisa--;
			double speed = 0.12D * Math.min(1.0D, (DAYUNG_TICKS - sisa) / 20.0D);
			this.setPos(this.getX() + arah.x * speed, this.getY(), this.getZ() + arah.z * speed);
			this.setYRot((float) Math.toDegrees(Math.atan2(-arah.x, arah.z)));
			if (sisa % 10 == 0) {
				level.playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.BOAT_PADDLE_WATER,
						net.minecraft.sounds.SoundSource.NEUTRAL, 0.8F, 0.9F + level.getRandom().nextFloat() * 0.2F);
			}
			if (sisa == 0) {
				selesai(level);
			}
		}
	}

	private void selesai(ServerLevel level) {
		Vec3 to = tujuan;
		for (Entity passenger : this.getPassengers()) {
			passenger.stopRiding();
			if (to != null && passenger instanceof ServerPlayer player) {
				Penyeberangan.tiba(player, level, to);
			}
		}
		this.entityData.set(DATA_DAYUNG, Boolean.FALSE);
		this.arah = null;
		this.tujuan = null;
		if (tambat != null) {
			this.setPos(tambat.getX() + 0.5D, tambat.getY(), tambat.getZ() + 0.5D);
		}
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (player instanceof ServerPlayer serverPlayer && hand == InteractionHand.MAIN_HAND) {
			return Penyeberangan.naik(serverPlayer, this) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return this.getPassengers().size() < 2;
	}

	@Override
	public boolean isPickable() {
		return !this.isRemoved();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_DAYUNG, Boolean.FALSE);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
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
}
