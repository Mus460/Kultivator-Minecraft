package dev.kultivator.sekte.gabung.masuk;

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
import org.jetbrains.annotations.Nullable;

/**
 * {@code kultivator:dinding_ilusi} (DESIGN 41.11.3): one 1 x 3 x 1 cell of the FORMASI corak test "Labirin Batu" (41.5.4,
 * Lu Xun lost in Zhuge Liang's Stone Sentinel Maze). An invisible collider in the {@code ColliderEntity} pattern
 * ({@link #canBeCollidedWith}, the boat/shulker rule checked in mcsrc): the client draws mist and, on revealed cells, one of
 * the eight trigrams. A cell may instead be a formation eye ({@link #FLAG_MATA}): not solid, the player must step on the
 * three eyes in order; a stepped eye sets {@link #FLAG_TERINJAK}. When the test ends the cells fade ({@link #pudar()}, the
 * client thins the mist over {@link #PUDAR_TICKS}) and then leave. Never saved, never hurt, at most 48 per test.
 * <p>
 * Synced: {@link #trigram()} 0-7 ({@code -1} none; order Qian, Dui, Li, Zhen, Xun, Kan, Gen, Kun), {@link #flags()},
 * {@link #pudarSejak()} game time the fade began (0 = standing), {@link #urutan()} the eye's place in the order 1-3 (0 for
 * walls).
 */
public class DindingIlusiEntity extends Entity {

	/** The cell is a formation eye (no collision). */
	public static final int FLAG_MATA = 1;
	/** The eye was stepped on in the right order (lit). */
	public static final int FLAG_TERINJAK = 2;
	/** The trigram on this wall is visible (a hint face). */
	public static final int FLAG_TERUNGKAP = 4;
	/** A wrong eye was stepped on: the client flashes red once. */
	public static final int FLAG_SALAH = 8;

	/** The fade at the end of the test (1.5 s). */
	public static final int PUDAR_TICKS = 30;
	/** Ticks a cell without a test may live before it removes itself. */
	public static final int TANPA_PEMILIK_TICKS = 40;

	private static final EntityDataAccessor<Integer> DATA_TRIGRAM = SynchedEntityData.defineId(DindingIlusiEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_FLAGS = SynchedEntityData.defineId(DindingIlusiEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Long> DATA_PUDAR = SynchedEntityData.defineId(DindingIlusiEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Integer> DATA_URUTAN = SynchedEntityData.defineId(DindingIlusiEntity.class, EntityDataSerializers.INT);

	private boolean pemilik;
	private int umur;

	public DindingIlusiEntity(EntityType<? extends DindingIlusiEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_TRIGRAM, -1);
		builder.define(DATA_FLAGS, 0);
		builder.define(DATA_PUDAR, 0L);
		builder.define(DATA_URUTAN, 0);
	}

	// ================================================================== synced state

	public int trigram() {
		return entityData.get(DATA_TRIGRAM);
	}

	public int flags() {
		return entityData.get(DATA_FLAGS);
	}

	public boolean has(int flag) {
		return (flags() & flag) != 0;
	}

	public long pudarSejak() {
		return entityData.get(DATA_PUDAR);
	}

	/** Order of a formation eye (1-3), 0 for a wall. */
	public int urutan() {
		return entityData.get(DATA_URUTAN);
	}

	public boolean mata() {
		return has(FLAG_MATA);
	}

	public boolean memudar() {
		return pudarSejak() > 0L;
	}

	/** Sets the cell up before it is added: a wall with {@code trigram} (-1 none) or an eye with order {@code urutan} 1-3. */
	public void setup(int trigram, boolean mata, int urutan) {
		entityData.set(DATA_TRIGRAM, Math.max(-1, Math.min(7, trigram)));
		entityData.set(DATA_URUTAN, mata ? Math.max(1, Math.min(3, urutan)) : 0);
		setFlag(FLAG_MATA, mata);
	}

	public void setFlag(int flag, boolean on) {
		int now = flags();
		int next = on ? now | flag : now & ~flag;
		if (next != now) {
			entityData.set(DATA_FLAGS, next);
		}
	}

	/** Starts the fade (server); the cell leaves after {@link #PUDAR_TICKS}. */
	public void pudar() {
		if (!memudar()) {
			entityData.set(DATA_PUDAR, Math.max(1L, level().getGameTime()));
		}
	}

	/** Marks the cell as held by a running test (server). */
	public void setPemilik(boolean pemilik) {
		this.pemilik = pemilik;
		this.umur = 0;
	}

	// ================================================================== collision

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return !mata() && !memudar() && !this.isRemoved();
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
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	public boolean canBeHitByProjectile() {
		return !mata() && !memudar();
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
		if (!pemilik && umur > TANPA_PEMILIK_TICKS || memudar() && level().getGameTime() - pudarSejak() >= PUDAR_TICKS) {
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
