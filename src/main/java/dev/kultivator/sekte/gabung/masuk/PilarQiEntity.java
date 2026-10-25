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
 * {@code kultivator:pilar_qi} (DESIGN 41.11.3): one of the five carved stone pillars of the UNSUR corak test "Lima Pilar
 * Qi" (41.5.4), standing on titik {@code pilar_0..4} in a ring of 9 blocks. A marker, not a wall: solid (0.8 x 3) so it
 * reads as stone, never hurt. The player stands at each pillar in turn and keeps the needle of the HUD (payload
 * {@code gabung_ujian}) inside a narrow band for 15 s; the pillar shows how well with its element light.
 * <p>
 * Synced: {@link #unsur()} the element (Wu Xing order {@link #LOGAM}, {@link #KAYU}, {@link #AIR}, {@link #API},
 * {@link #TANAH}; the 41V texture has five carved faces and five light colours), {@link #fase()} ({@link #FASE_DIAM} idle,
 * {@link #FASE_AKTIF} the player is holding it, {@link #FASE_LULUS} done well, {@link #FASE_GAGAL} done badly) and
 * {@link #nyala()} 0-1 the light strength (the accuracy so far; the client lerps it). Never saved; a pillar without a test
 * leaves after 2 s.
 * <p>
 * Holding: a held use key repeats the interaction every 4 ticks (vanilla {@code Minecraft.handleKeybinds}), so the test
 * hears {@link Pendengar#ditahan} about five times a second while the player keeps right-clicking the pillar and counts
 * the needle as "held" until about 6 ticks after the last call.
 */
public class PilarQiEntity extends Entity {

	public static final int LOGAM = 0;
	public static final int KAYU = 1;
	public static final int AIR = 2;
	public static final int API = 3;
	public static final int TANAH = 4;
	public static final int UNSUR_JUMLAH = 5;
	/** Light colour per element (0xRRGGBB), the same the HUD needle uses. */
	public static final int[] WARNA_UNSUR = {0xE8E8F0, 0x5FD068, 0x4FA8F0, 0xF06A3A, 0xD8B060};

	public static final int FASE_DIAM = 0;
	public static final int FASE_AKTIF = 1;
	public static final int FASE_LULUS = 2;
	public static final int FASE_GAGAL = 3;

	public static final int TANPA_PEMILIK_TICKS = 40;

	/** The running test's hook: {@code player} is holding the use key on {@code pilar} (server thread). */
	@FunctionalInterface
	public interface Pendengar {
		void ditahan(PilarQiEntity pilar, ServerPlayer player);
	}

	private static final EntityDataAccessor<Integer> DATA_UNSUR = SynchedEntityData.defineId(PilarQiEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_FASE = SynchedEntityData.defineId(PilarQiEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_NYALA = SynchedEntityData.defineId(PilarQiEntity.class, EntityDataSerializers.FLOAT);

	private @Nullable Pendengar pendengar;
	private boolean pemilik;
	private int umur;

	public PilarQiEntity(EntityType<? extends PilarQiEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_UNSUR, LOGAM);
		builder.define(DATA_FASE, FASE_DIAM);
		builder.define(DATA_NYALA, 0.0F);
	}

	// ================================================================== synced state

	public int unsur() {
		return entityData.get(DATA_UNSUR);
	}

	public int fase() {
		return entityData.get(DATA_FASE);
	}

	public float nyala() {
		return entityData.get(DATA_NYALA);
	}

	/** The element's light colour. */
	public int warna() {
		return WARNA_UNSUR[Math.floorMod(unsur(), UNSUR_JUMLAH)];
	}

	public void setUnsur(int unsur) {
		entityData.set(DATA_UNSUR, Math.floorMod(unsur, UNSUR_JUMLAH));
	}

	public void setFase(int fase) {
		entityData.set(DATA_FASE, Mth.clamp(fase, FASE_DIAM, FASE_GAGAL));
	}

	public void setNyala(float nyala) {
		entityData.set(DATA_NYALA, Mth.clamp(nyala, 0.0F, 1.0F));
	}

	/** Marks the pillar as held by a running test (server). */
	public void setPemilik(boolean pemilik) {
		this.pemilik = pemilik;
		this.umur = 0;
	}

	/** Binds the pillar to a running test with its hold hook (server); {@code null} releases it. */
	public void setPendengar(@Nullable Pendengar pendengar) {
		this.pendengar = pendengar;
		setPemilik(pendengar != null);
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		Pendengar current = pendengar;
		if (current != null && player instanceof ServerPlayer serverPlayer && !serverPlayer.isSpectator()) {
			try {
				current.ditahan(this, serverPlayer);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Kultivator: a gabung qi pillar failed on use", exception);
			}
		}
		return InteractionResult.SUCCESS;
	}

	// ================================================================== a solid marker

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return !this.isRemoved();
	}

	/** Pickable so the crosshair finds it (right-click = hold the Qi). */
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
