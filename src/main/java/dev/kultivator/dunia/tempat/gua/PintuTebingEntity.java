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
 * {@code kultivator:pintu_tebing} (DESIGN 38.C.4 / 38.O.2): one 1 × 2 stone of the false-stone cliff door of a demonic
 * lair (the {@code KotaPintuEntity} pattern). A doorway of 3 × 4 holds six of them. An entity, never a block (38.0 rule
 * 1): while shut it blocks with its collision box ({@link #canBeCollidedWith}, the boat / shulker pattern); opened it
 * slides aside over {@link #SLIDE_TICKS} (the client poses the stone from the synced open time, {@code KaitModel.pintuGeser})
 * and lets everything through. Never saved: the {@code gua/iblis_pintu} anchor re-makes it; a stray stone that no run
 * holds leaves after two seconds. Invulnerable, not pushable.
 */
public class PintuTebingEntity extends Entity {

	/** The slide aside: 1.2 s (as the town's hidden door). */
	public static final int SLIDE_TICKS = 24;

	/** Told when a player touches (uses) the stone; server only. */
	@FunctionalInterface
	public interface Sentuh {
		void sentuh(ServerPlayer player, PintuTebingEntity door);
	}

	private static final EntityDataAccessor<Long> DATA_BUKA = SynchedEntityData.defineId(PintuTebingEntity.class, EntityDataSerializers.LONG);

	private @Nullable Sentuh sentuh;
	private boolean owned;
	private int age;

	public PintuTebingEntity(EntityType<? extends PintuTebingEntity> type, Level level) {
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

	/** The game time the stone began to slide (0 while shut). */
	public long bukaSejak() {
		return this.entityData.get(DATA_BUKA);
	}

	public boolean terbuka() {
		return bukaSejak() != 0L;
	}

	/** Slides the stone aside (grinding stone sounds); idempotent. */
	public void buka() {
		if (terbuka() || !(this.level() instanceof ServerLevel level)) {
			return;
		}
		this.entityData.set(DATA_BUKA, Math.max(1L, level.getGameTime()));
		level.playSound(null, getX(), getY() + 1.0D, getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.7F, 0.45F);
		level.playSound(null, getX(), getY() + 1.0D, getZ(), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5F, 0.4F);
	}

	/** Slides it back (the lair shuts behind a visitor who left). */
	public void tutup() {
		if (!terbuka() || !(this.level() instanceof ServerLevel level)) {
			return;
		}
		this.entityData.set(DATA_BUKA, 0L);
		level.playSound(null, getX(), getY() + 1.0D, getZ(), SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.5F, 0.4F);
	}

	/** How far the stone has slid aside at {@code partialTick}, 0..1 (smoothstep), for the renderer hook. */
	public float geser(float partialTick) {
		long t0 = bukaSejak();
		if (t0 == 0L) {
			return 0.0F;
		}
		float t = Mth.clamp((this.level().getGameTime() - t0 + partialTick) / SLIDE_TICKS, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	/** Marks the stone as held by a run (server only), and who is told when a player touches it. */
	public void ikat(@Nullable Sentuh listener) {
		this.owned = true;
		this.sentuh = listener;
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
		if (player instanceof ServerPlayer serverPlayer && sentuh != null && !serverPlayer.isSpectator()) {
			try {
				sentuh.sentuh(serverPlayer, this);
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.error("Kultivator: a cliff door failed on use", exception);
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
