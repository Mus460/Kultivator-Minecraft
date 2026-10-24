package dev.kultivator.dunia.tempat.gua;

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
 * {@code kultivator:sangkar} (DESIGN 38.C.4 / 38.O.2): the barred, padlocked door of a captive's cell in a demonic
 * lair. An entity, never a block (38.0 rule 1): shut it blocks the cell with its collision box; a player opens it with
 * a use (the run decides whether the lock gives, e.g. not while the lair's band still stands guard). The renderer
 * reads {@link #buka(float)} through {@code KaitModel.sangkarBuka}: the shackle springs and the lock drops (first 30 %),
 * then the door swings to 100° (over {@link #OPEN_TICKS}). Never saved: the {@code gua/iblis_sel} anchor re-makes it
 * from the anchor's state (a freed cell stays open for that world day). Invulnerable, not pushable.
 */
public class SangkarEntity extends Entity {

	/** Lock drop and swing: 1.5 s. */
	public static final int OPEN_TICKS = 30;

	/** Told when a player uses the cage door; server only. */
	@FunctionalInterface
	public interface Buka {
		void coba(ServerPlayer player, SangkarEntity cage);
	}

	private static final EntityDataAccessor<Long> DATA_BUKA = SynchedEntityData.defineId(SangkarEntity.class, EntityDataSerializers.LONG);

	private @Nullable Buka listener;
	private boolean owned;
	private int age;
	/** The cell index of the anchor (server only). */
	private int sel = -1;

	public SangkarEntity(EntityType<? extends SangkarEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.setPermanentlyInvulnerable(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_BUKA, 0L);
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

	// ================================================================== state

	public long bukaSejak() {
		return this.entityData.get(DATA_BUKA);
	}

	public boolean terbuka() {
		return bukaSejak() != 0L;
	}

	/** Opens the cage (the lock clatters to the floor); {@code diam}: already open on spawn, no sound. */
	public void buka(boolean diam) {
		if (terbuka() || !(this.level() instanceof ServerLevel level)) {
			return;
		}
		this.entityData.set(DATA_BUKA, diam ? 1L : Math.max(1L, level.getGameTime()));
		if (!diam) {
			level.playSound(null, getX(), getY() + 0.8D, getZ(), SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.9F, 0.9F);
			level.playSound(null, getX(), getY() + 0.8D, getZ(), SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.8F, 0.8F);
		}
	}

	/** The open reading 0..1 at {@code partialTick} for the renderer ({@code KaitModel.sangkarBuka}). */
	public float buka(float partialTick) {
		long t0 = bukaSejak();
		if (t0 == 0L) {
			return 0.0F;
		}
		if (t0 == 1L) {
			return 1.0F;
		}
		return Mth.clamp((this.level().getGameTime() - t0 + partialTick) / OPEN_TICKS, 0.0F, 1.0F);
	}

	/** Binds the cage to its run: the cell index and who is asked when a player tries the lock. */
	public void ikat(int sel, @Nullable Buka listener) {
		this.owned = true;
		this.sel = sel;
		this.listener = listener;
	}

	public int sel() {
		return sel;
	}

	@Override
	public void tick() {
		super.tick();
		age++;
		if (!this.level().isClientSide() && !owned && age > 40) {
			this.discard();
		}
	}

	// ================================================================== collision and use

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND || terbuka()) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer && listener != null && !serverPlayer.isSpectator()) {
			try {
				listener.coba(serverPlayer, this);
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.error("Kultivator: a cage door failed on use", exception);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return !terbuka() && !this.isRemoved();
	}

	@Override
	public boolean isPickable() {
		return !this.isRemoved() && !terbuka();
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
	public PushReaction getPistonPushReaction() {
		return PushReaction.IGNORE_ENTITY;
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
