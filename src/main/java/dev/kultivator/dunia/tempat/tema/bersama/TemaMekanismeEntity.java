package dev.kultivator.dunia.tempat.tema.bersama;

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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Base of every themed-town mechanism, boat and scenery entity (DESIGN 38.T.0 butir 1, 38.T.9): never saved (the type
 * is {@code noSave}; it is re-made from its anchor or NBT titik by 38TK), never a block, no gravity, permanently
 * invulnerable unless a subclass says otherwise, not pushable, ignores pressure plates and tripwires. Moving ones follow
 * a spline as a pure function of time ({@code t = gameTime - start}); the server moves them with {@link #pindah} and the
 * client interpolates (no path finding, 38.T.2.7).
 * <p>
 * Synced state for the renderer: {@link TemaAnimasi}. Binding: {@link #ikat(long, long)} stores the owning site id and
 * the anchor (packed {@code BlockPos}) so the theme systems find "their" body again after a chunk reload.
 * <p>
 * Subclass knobs: {@link #padat()} (solid collision box, e.g. a closed gate), {@link #bisaDipilih()} (crosshair-pickable
 * for a click or a hit), {@link #kursi()} (seats for riders; 0 = no passengers).
 */
public abstract class TemaMekanismeEntity extends Entity implements TemaAnimasi {

	private static final EntityDataAccessor<Integer> DATA_VARIAN = SynchedEntityData.defineId(TemaMekanismeEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_KEADAAN = SynchedEntityData.defineId(TemaMekanismeEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_MULAI = SynchedEntityData.defineId(TemaMekanismeEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_NILAI = SynchedEntityData.defineId(TemaMekanismeEntity.class, EntityDataSerializers.FLOAT);

	private long situsId;
	private long jangkar;

	protected TemaMekanismeEntity(EntityType<? extends TemaMekanismeEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.setPermanentlyInvulnerable(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_VARIAN, 0);
		builder.define(DATA_KEADAAN, 0);
		builder.define(DATA_MULAI, 0);
		builder.define(DATA_NILAI, 0.0F);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		// never saved (noSave type); the anchor re-makes it
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	// ------------------------------------------------------------------ binding

	/** Binds the body to its site and anchor (packed {@code BlockPos.asLong()}); server side, right after spawning. */
	public void ikat(long situsId, long jangkar) {
		this.situsId = situsId;
		this.jangkar = jangkar;
	}

	/** The owning site id (0 = unbound, e.g. a test spawn). */
	public long situsId() {
		return situsId;
	}

	/** The packed anchor position (0 = unbound). */
	public long jangkar() {
		return jangkar;
	}

	// ------------------------------------------------------------------ animation state

	@Override
	public int varian() {
		return this.entityData.get(DATA_VARIAN);
	}

	@Override
	public int keadaan() {
		return this.entityData.get(DATA_KEADAAN);
	}

	@Override
	public int mulai() {
		return this.entityData.get(DATA_MULAI);
	}

	@Override
	public float nilai() {
		return this.entityData.get(DATA_NILAI);
	}

	public void setVarian(int varian) {
		if (varian() != varian) {
			this.entityData.set(DATA_VARIAN, varian);
		}
	}

	/** Sets the state and stamps its start tick (server); same state again keeps the old start. */
	public void setKeadaan(int keadaan) {
		if (keadaan() != keadaan) {
			this.entityData.set(DATA_KEADAAN, keadaan);
			this.entityData.set(DATA_MULAI, (int) (this.level().getGameTime() & 0x7fffffffL));
		}
	}

	public void setNilai(float nilai) {
		if (Float.compare(nilai(), nilai) != 0) {
			this.entityData.set(DATA_NILAI, nilai);
		}
	}

	/** Server: moves the body to {@code pos} facing {@code yaw} (one spline step; the client interpolates). */
	public void pindah(Vec3 pos, float yaw) {
		this.setPos(pos.x, pos.y, pos.z);
		this.setYRot(yaw);
		this.setYHeadRot(yaw);
	}

	// ------------------------------------------------------------------ knobs

	/** True: the box blocks movement (closed gate, cell door, barrier, cabin). Default false. */
	protected boolean padat() {
		return false;
	}

	/** True: the crosshair can pick it (clicks, hits). Default false. */
	protected boolean bisaDipilih() {
		return false;
	}

	/** Seats for riders; 0 = takes no passengers. */
	protected int kursi() {
		return 0;
	}

	// ------------------------------------------------------------------ vanilla hooks

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return padat() && !this.isRemoved();
	}

	@Override
	public boolean isPickable() {
		return bisaDipilih() && !this.isRemoved();
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
		return this.getPassengers().size() < kursi();
	}

	@Override
	protected boolean couldAcceptPassenger() {
		return kursi() > 0;
	}
}
