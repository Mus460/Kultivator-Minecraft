package dev.kultivator.dunia.kota;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
 * {@code kultivator:kota_pintu} (DESIGN 34.5.1, rule W2): the hidden door of the coffin shop's back room that leads down
 * to the Pasar Gelap. An entity, never a block: while closed it blocks the doorway with its collision box (the boat /
 * shulker pattern, {@link #canBeCollidedWith}); opened by a player holding a Token Pasar Gelap it slides aside over 1.2 s
 * ({@link #SLIDE_TICKS}; the client draws the panel, posed from the synced state) and stays open for
 * {@link #OPEN_TICKS}. During a Razia it is barred. Never saved: the {@code kota/gerbang_gelap} anchor re-makes it.
 * Facing: {@code yaw} picks the slide direction (the panel slides to the door's right).
 */
public class KotaPintuEntity extends Entity {

	public static final int SLIDE_TICKS = 24;
	public static final int OPEN_TICKS = 120;

	private static final EntityDataAccessor<Boolean> DATA_OPEN = SynchedEntityData.defineId(KotaPintuEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_BARRED = SynchedEntityData.defineId(KotaPintuEntity.class, EntityDataSerializers.BOOLEAN);

	private long closeAt;
	private long anchorSite;
	private long town;
	/** Client: slide progress 0 (closed) .. 1 (open), and last tick's value for frame interpolation. */
	private float slide;
	private float slidePrev;

	public KotaPintuEntity(EntityType<? extends KotaPintuEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.setPermanentlyInvulnerable(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_OPEN, false);
		builder.define(DATA_BARRED, false);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	public boolean open() {
		return this.entityData.get(DATA_OPEN);
	}

	public boolean barred() {
		return this.entityData.get(DATA_BARRED);
	}

	public void setBarred(boolean on) {
		if (barred() != on) {
			this.entityData.set(DATA_BARRED, on);
		}
		if (on && open()) {
			close();
		}
	}

	public long anchorSite() {
		return anchorSite;
	}

	public long town() {
		return town;
	}

	void bind(long anchorSite, long town) {
		this.anchorSite = anchorSite;
		this.town = town;
	}

	/** Opens for {@link #OPEN_TICKS}; false while barred. */
	public boolean openFor(ServerLevel level) {
		if (barred()) {
			return false;
		}
		closeAt = level.getGameTime() + SLIDE_TICKS + OPEN_TICKS;
		if (!open()) {
			this.entityData.set(DATA_OPEN, true);
			level.playSound(null, getX(), getY() + 1.0D, getZ(), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.35F, 0.55F);
			level.playSound(null, getX(), getY() + 1.0D, getZ(), SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 0.6F, 0.7F);
		}
		return true;
	}

	public void close() {
		if (open()) {
			this.entityData.set(DATA_OPEN, false);
			if (this.level() instanceof ServerLevel level) {
				level.playSound(null, getX(), getY() + 1.0D, getZ(), SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 0.6F, 0.7F);
			}
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			slidePrev = slide;
			float target = open() ? 1.0F : 0.0F;
			float step = 1.0F / SLIDE_TICKS;
			slide = target > slide ? Math.min(target, slide + step) : Math.max(target, slide - step);
			return;
		}
		if (open() && this.level().getGameTime() >= closeAt) {
			// never shut on someone standing in the doorway
			if (this.level().getEntities(this, this.getBoundingBox().inflate(0.05D), entity -> entity instanceof Player).isEmpty()) {
				close();
			} else {
				closeAt = this.level().getGameTime() + 20L;
			}
		}
	}

	/** Client: the slide progress at {@code partial} (0 closed .. 1 open), eased. */
	public float slide(float partial) {
		float t = Mth.lerp(partial, slidePrev, slide);
		return t * t * (3.0F - 2.0F * t);
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer && this.level() instanceof ServerLevel level) {
			PasarGelap.doorUsed(level, serverPlayer, this);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return !open();
	}

	@Override
	public boolean isPickable() {
		return !this.isRemoved();
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
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return false;
	}

	@Override
	protected boolean couldAcceptPassenger() {
		return false;
	}
}
