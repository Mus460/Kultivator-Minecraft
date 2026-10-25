package dev.kultivator.sekte.gabung.masuk;

import dev.kultivator.Kultivator;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * {@code kultivator:boneka_ujian} (DESIGN 41.11.3): the straw-and-wood training dummy on its post of the PEDANG corak test
 * "Seratus Tebasan" (41.5.4). It stands still, lights up (a glow quad drawn by 41V) while {@link #aktif()}, takes hits and
 * can never die: an attack goes through {@link #skipAttackInteraction} (the {@code ColliderEntity} pattern; vanilla
 * {@code Player.attack} asks it before dealing damage) to the {@link Pendengar} of the running test, which reads the weapon
 * in the player's hand ({@code WeaponClass.of}) and counts the hit. No damage is ever applied, no block is touched.
 * <p>
 * Synced (all read by the 41V renderer): {@link #aktif()} lit or not, {@link #kena()} a hit counter (the client starts a
 * shake/flash when it changes), {@link #warna()} the sect's ink colour for the cloth band, {@link #nyalaSampai()} the game
 * time the light goes out (the client draws the 2 s ring from it). Never saved: the test spawns at most 6 (41.12.3) and a
 * stray dummy that no test holds leaves after 2 s ({@link #setPemilik}).
 */
public class BonekaUjianEntity extends Entity {

	/** A lit dummy stays lit this long unless struck (41.5.4: "menyala bergiliran 2 s"). */
	public static final int NYALA_TICKS = 40;
	/** Ticks a dummy without a test may live before it removes itself. */
	public static final int TANPA_PEMILIK_TICKS = 40;

	/** The running test's hook: one attack by {@code player} on {@code boneka} (server thread). */
	@FunctionalInterface
	public interface Pendengar {
		void dipukul(BonekaUjianEntity boneka, ServerPlayer player);
	}

	private static final EntityDataAccessor<Boolean> DATA_AKTIF = SynchedEntityData.defineId(BonekaUjianEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_KENA = SynchedEntityData.defineId(BonekaUjianEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_WARNA = SynchedEntityData.defineId(BonekaUjianEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Long> DATA_NYALA_SAMPAI = SynchedEntityData.defineId(BonekaUjianEntity.class, EntityDataSerializers.LONG);

	private @Nullable Pendengar pendengar;
	private boolean pemilik;
	private int umur;

	public BonekaUjianEntity(EntityType<? extends BonekaUjianEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_AKTIF, false);
		builder.define(DATA_KENA, 0);
		builder.define(DATA_WARNA, 0xC8A060);
		builder.define(DATA_NYALA_SAMPAI, 0L);
	}

	// ================================================================== synced state

	public boolean aktif() {
		return entityData.get(DATA_AKTIF);
	}

	/** Hits taken so far (wraps; the client only compares it with its last value). */
	public int kena() {
		return entityData.get(DATA_KENA);
	}

	/** The sect's ink colour (0xRRGGBB) for the cloth band. */
	public int warna() {
		return entityData.get(DATA_WARNA);
	}

	/** Game time the light goes out (0 while dark). */
	public long nyalaSampai() {
		return entityData.get(DATA_NYALA_SAMPAI);
	}

	/** Lights the dummy for {@link #NYALA_TICKS} (server). */
	public void nyalakan() {
		entityData.set(DATA_NYALA_SAMPAI, level().getGameTime() + NYALA_TICKS);
		entityData.set(DATA_AKTIF, true);
	}

	/** Puts the light out (server). */
	public void padamkan() {
		if (aktif()) {
			entityData.set(DATA_AKTIF, false);
			entityData.set(DATA_NYALA_SAMPAI, 0L);
		}
	}

	/** Counts one hit (server; the client shakes the dummy). */
	public void tandaiKena() {
		entityData.set(DATA_KENA, kena() + 1);
	}

	public void setWarna(int rgb) {
		entityData.set(DATA_WARNA, rgb & 0xFFFFFF);
	}

	// ================================================================== owner

	/** Binds the dummy to a running test (server); {@code null} releases it (it leaves after {@link #TANPA_PEMILIK_TICKS}). */
	public void setPemilik(@Nullable Pendengar pendengar) {
		this.pendengar = pendengar;
		this.pemilik = pendengar != null;
		this.umur = 0;
	}

	// ================================================================== hits, never damage

	@Override
	public boolean skipAttackInteraction(Entity source) {
		Pendengar current = pendengar;
		if (current != null && source instanceof ServerPlayer player && !player.isSpectator()) {
			try {
				current.dipukul(this, player);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Kultivator: a gabung test dummy failed on hit", exception);
			}
		}
		return true;
	}

	@Override
	public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return !this.isRemoved();
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
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
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			return;
		}
		umur++;
		if (!pemilik && umur > TANPA_PEMILIK_TICKS) {
			this.discard();
			return;
		}
		if (aktif() && level().getGameTime() >= nyalaSampai()) {
			padamkan();
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
