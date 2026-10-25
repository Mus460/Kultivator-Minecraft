package dev.kultivator.sekte.gabung.masuk;

import dev.kultivator.Kultivator;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * {@code kultivator:cawan_ujian} (DESIGN 41.11.3): one bronze cup (0.5 x 0.4) on the table of the RACUN corak test "Tiga
 * Cawan" (41.5.4, the Five Poison Sect of <i>The Smiling, Proud Wanderer</i>). Clicking it = drinking: the running test
 * ({@link Pendengar}) applies the poison, prints the scent hint and starts the 20 s choice of antidote. The cup steams on the
 * client until drunk.
 * <p>
 * Synced: {@link #nomor()} which of the three cups (0-2, the client spaces the lids), {@link #warnaIsi()} the liquid tint
 * (0xRRGGBB, also the steam colour), {@link #fase()} ({@link #FASE_PENUH} full and steaming, {@link #FASE_DIMINUM} empty,
 * {@link #FASE_TAWAR} neutralised: the right antidote was chosen, a pale wisp). Never saved; a cup without a test leaves
 * after 2 s.
 */
public class CawanUjianEntity extends Entity {

	public static final int FASE_PENUH = 0;
	public static final int FASE_DIMINUM = 1;
	public static final int FASE_TAWAR = 2;

	public static final int TANPA_PEMILIK_TICKS = 40;

	/** The running test's hook: {@code player} drank from {@code cawan} (server thread). */
	@FunctionalInterface
	public interface Pendengar {
		void diminum(CawanUjianEntity cawan, ServerPlayer player);
	}

	private static final EntityDataAccessor<Integer> DATA_NOMOR = SynchedEntityData.defineId(CawanUjianEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_WARNA_ISI = SynchedEntityData.defineId(CawanUjianEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_FASE = SynchedEntityData.defineId(CawanUjianEntity.class, EntityDataSerializers.INT);

	private @Nullable Pendengar pendengar;
	private boolean pemilik;
	private int umur;

	public CawanUjianEntity(EntityType<? extends CawanUjianEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_NOMOR, 0);
		builder.define(DATA_WARNA_ISI, 0x6A8A3A);
		builder.define(DATA_FASE, FASE_PENUH);
	}

	// ================================================================== synced state

	public int nomor() {
		return entityData.get(DATA_NOMOR);
	}

	public int warnaIsi() {
		return entityData.get(DATA_WARNA_ISI);
	}

	public int fase() {
		return entityData.get(DATA_FASE);
	}

	public boolean penuh() {
		return fase() == FASE_PENUH;
	}

	public void setNomor(int nomor) {
		entityData.set(DATA_NOMOR, Mth.clamp(nomor, 0, 2));
	}

	public void setWarnaIsi(int rgb) {
		entityData.set(DATA_WARNA_ISI, rgb & 0xFFFFFF);
	}

	public void setFase(int fase) {
		entityData.set(DATA_FASE, Mth.clamp(fase, FASE_PENUH, FASE_TAWAR));
	}

	/** Binds the cup to a running test (server); {@code null} releases it. */
	public void setPemilik(@Nullable Pendengar pendengar) {
		this.pendengar = pendengar;
		this.pemilik = pendengar != null;
		this.umur = 0;
	}

	// ================================================================== click = drink

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND || !penuh()) {
			return InteractionResult.PASS;
		}
		Pendengar current = pendengar;
		if (current != null && player instanceof ServerPlayer serverPlayer && !serverPlayer.isSpectator()) {
			try {
				current.diminum(this, serverPlayer);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Kultivator: a gabung test cup failed on use", exception);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean isPickable() {
		return !this.isRemoved();
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
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
	public boolean skipAttackInteraction(Entity source) {
		return true;
	}

	@Override
	public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public void tick() {
		if (this.level().isClientSide()) {
			return;
		}
		umur++;
		if (!pemilik && umur > TANPA_PEMILIK_TICKS) {
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
