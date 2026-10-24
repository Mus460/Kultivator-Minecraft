package dev.kultivator.dunia.tempat.gua;

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
 * {@code kultivator:lempeng_glif} (DESIGN 38.C.4 / 38.O.2): one of the five stone plates of a tomb's Comprehension
 * trial, lying in its floor socket. Each use turns it a quarter turn and brings up the next of the five elements; the
 * run checks whether the ring of five follows the generating cycle. An entity, never a block (38.0 rule 1). Synced for
 * the renderer ({@code KaitModel.glifJenis/glifPutar/glifNyala}): the element shown ({@link #FIRE} .. {@link #EARTH},
 * the renderer's order), the number of quarter turns and the tick of the last turn (eased with easeOutBack over
 * {@link #TURN_TICKS}), and the tick the glyph began to burn (a solved ring fades in over {@link #GLOW_TICKS}; 0 dark).
 * A {@link #tetap() fixed} plate is the given start of the ring: it glows faintly and never turns. Never saved.
 */
public class LempengGlifEntity extends Entity {

	public static final int FIRE = 0;
	public static final int WATER = 1;
	public static final int WOOD = 2;
	public static final int METAL = 3;
	public static final int EARTH = 4;
	/** The five elements of the trial (thunder, glyph 5, is never used here). */
	public static final int ELEMENTS = 5;
	public static final int TURN_TICKS = 10;
	public static final int GLOW_TICKS = 20;

	/** Told when a player uses the plate; server only. */
	@FunctionalInterface
	public interface Putar {
		void putar(ServerPlayer player, LempengGlifEntity plate);
	}

	private static final EntityDataAccessor<Integer> DATA_JENIS = SynchedEntityData.defineId(LempengGlifEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_PUTAR = SynchedEntityData.defineId(LempengGlifEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Long> DATA_PUTAR_T0 = SynchedEntityData.defineId(LempengGlifEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Long> DATA_NYALA = SynchedEntityData.defineId(LempengGlifEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Boolean> DATA_TETAP = SynchedEntityData.defineId(LempengGlifEntity.class, EntityDataSerializers.BOOLEAN);

	private @Nullable Putar listener;
	private boolean owned;
	private int age;
	private int indeks = -1;

	public LempengGlifEntity(EntityType<? extends LempengGlifEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.setPermanentlyInvulnerable(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_JENIS, WOOD);
		builder.define(DATA_PUTAR, 0);
		builder.define(DATA_PUTAR_T0, 0L);
		builder.define(DATA_NYALA, 0L);
		builder.define(DATA_TETAP, false);
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

	/** The element shown, 0-4 ({@link #FIRE} .. {@link #EARTH}). */
	public int jenis() {
		return Math.floorMod(this.entityData.get(DATA_JENIS), 6);
	}

	public boolean tetap() {
		return this.entityData.get(DATA_TETAP);
	}

	/** Sets up the plate (before it is added): its ring index, element, and whether it is the fixed start. */
	public void setup(int indeks, int jenis, boolean tetap) {
		this.indeks = indeks;
		this.entityData.set(DATA_JENIS, Math.floorMod(jenis, ELEMENTS));
		this.entityData.set(DATA_TETAP, tetap);
		this.entityData.set(DATA_NYALA, tetap ? 1L : 0L);
	}

	public int indeks() {
		return indeks;
	}

	/** One quarter turn and the next element (server). */
	public void putarSatu() {
		if (tetap()) {
			return;
		}
		this.entityData.set(DATA_JENIS, Math.floorMod(jenis() + 1, ELEMENTS));
		this.entityData.set(DATA_PUTAR, this.entityData.get(DATA_PUTAR) + 1);
		this.entityData.set(DATA_PUTAR_T0, this.level().getGameTime());
	}

	/** Lights (or darkens) the glyph: a solved ring burns, a reset ring goes dark again. */
	public void nyala(boolean on) {
		long now = Math.max(2L, this.level().getGameTime());
		if (on) {
			if (this.entityData.get(DATA_NYALA) <= 1L) {
				this.entityData.set(DATA_NYALA, now);
			}
		} else {
			this.entityData.set(DATA_NYALA, tetap() ? 1L : 0L);
		}
	}

	/** The plate's turn in quarter turns at {@code partialTick} (easeOutBack on the last turn), for the renderer. */
	public float putaran(float partialTick) {
		int turns = this.entityData.get(DATA_PUTAR);
		long t0 = this.entityData.get(DATA_PUTAR_T0);
		if (turns == 0 || t0 == 0L) {
			return 0.0F;
		}
		float t = Mth.clamp((this.level().getGameTime() - t0 + partialTick) / TURN_TICKS, 0.0F, 1.0F);
		float c1 = 1.70158F;
		float c3 = c1 + 1.0F;
		float back = 1.0F + c3 * (float) Math.pow(t - 1.0F, 3) + c1 * (float) Math.pow(t - 1.0F, 2);
		return turns - 1 + back;
	}

	/** How bright the glyph burns, 0..1 (a fixed start plate glows at 0.35), for the renderer. */
	public float cahaya(float partialTick) {
		long t0 = this.entityData.get(DATA_NYALA);
		if (t0 == 0L) {
			return 0.0F;
		}
		if (t0 == 1L) {
			return 0.35F;
		}
		return Mth.clamp(0.35F + (this.level().getGameTime() - t0 + partialTick) / GLOW_TICKS, 0.0F, 1.0F);
	}

	public void ikat(@Nullable Putar listener) {
		this.owned = true;
		this.listener = listener;
	}

	@Override
	public void tick() {
		super.tick();
		age++;
		if (!this.level().isClientSide() && !owned && age > 40) {
			this.discard();
		}
	}

	// ================================================================== use

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer && listener != null && !serverPlayer.isSpectator()) {
			try {
				listener.putar(serverPlayer, this);
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.error("Kultivator: a glyph plate failed on use", exception);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean isPickable() {
		return !this.isRemoved();
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
}
