package dev.kultivator.dunia.naga;

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

/**
 * The host of a Kesengsaraan Binatang Roh (DESIGN 34.5.12, {@code kultivator:kesengsaraan_binatang}): an invisible,
 * untouchable entity floating about 14 blocks above the beast while its tribulation runs, so every client nearby can draw
 * the cloud vortex, the closing telegraph ring of the next strike, the Hu Fa link and the light pillar of a success
 * ({@code KesengsaraanRenderer}). It carries only synced numbers; the tribulation itself lives in
 * {@link BeastTribulation}, which moves and removes it. Never saved, never collides, never hurt, no block is ever touched.
 */
public class KesengsaraanEntity extends Entity {

	private static final EntityDataAccessor<Integer> DATA_BEAST = SynchedEntityData.defineId(KesengsaraanEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(KesengsaraanEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_PHASE_TICKS = SynchedEntityData.defineId(KesengsaraanEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_NEXT = SynchedEntityData.defineId(KesengsaraanEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_STRIKES = SynchedEntityData.defineId(KesengsaraanEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(KesengsaraanEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_GUARD = SynchedEntityData.defineId(KesengsaraanEntity.class, EntityDataSerializers.INT);

	public static final int PHASE_OMEN = 0;
	public static final int PHASE_STRIKES = 1;
	public static final int PHASE_SUCCESS = 2;
	public static final int PHASE_FAILED = 3;

	public KesengsaraanEntity(EntityType<? extends KesengsaraanEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
		this.setPermanentlyInvulnerable(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_BEAST, -1);
		builder.define(DATA_PHASE, PHASE_OMEN);
		builder.define(DATA_PHASE_TICKS, 0);
		builder.define(DATA_NEXT, -1);
		builder.define(DATA_STRIKES, 0);
		builder.define(DATA_COLOR, 0xB8A0FF);
		builder.define(DATA_GUARD, -1);
	}

	// ================================================================== synced values

	/** The beast's entity id. */
	public int beastId() {
		return this.entityData.get(DATA_BEAST);
	}

	public int phase() {
		return this.entityData.get(DATA_PHASE);
	}

	/** Ticks since the phase began. */
	public int phaseTicks() {
		return this.entityData.get(DATA_PHASE_TICKS);
	}

	/** Ticks until the next strike falls, or -1 (the telegraph ring closes over the last 30). */
	public int nextStrike() {
		return this.entityData.get(DATA_NEXT);
	}

	public int strikesDone() {
		return this.entityData.get(DATA_STRIKES) & 0xFF;
	}

	public int strikesTotal() {
		return (this.entityData.get(DATA_STRIKES) >> 8) & 0xFF;
	}

	/** The line colour (wolf star silver, fox moon gold, Jiao azure). */
	public int color() {
		return this.entityData.get(DATA_COLOR);
	}

	/** The entity id of the Hu Fa owner within 6 blocks, or -1. */
	public int guardId() {
		return this.entityData.get(DATA_GUARD);
	}

	void setUp(int beastId, int strikes, int color) {
		this.entityData.set(DATA_BEAST, beastId);
		this.entityData.set(DATA_STRIKES, (Math.min(255, strikes) << 8));
		this.entityData.set(DATA_COLOR, color & 0xFFFFFF);
	}

	void setPhase(int phase) {
		this.entityData.set(DATA_PHASE, phase);
		this.entityData.set(DATA_PHASE_TICKS, 0);
	}

	void setPhaseTicks(int ticks) {
		this.entityData.set(DATA_PHASE_TICKS, ticks);
	}

	void setNext(int ticks) {
		if (this.entityData.get(DATA_NEXT) != ticks) {
			this.entityData.set(DATA_NEXT, ticks);
		}
	}

	void setDone(int done) {
		int total = strikesTotal();
		this.entityData.set(DATA_STRIKES, (total << 8) | Math.min(255, done));
	}

	void setGuard(int id) {
		if (this.entityData.get(DATA_GUARD) != id) {
			this.entityData.set(DATA_GUARD, id);
		}
	}

	// ================================================================== behaviour

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel && this.tickCount > 40 && !BeastTribulation.hosts(this)) {
			// a stray host (its tribulation ended or never registered) leaves
			this.discard();
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
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
	protected boolean canAddPassenger(Entity passenger) {
		return false;
	}

	@Override
	public boolean canUsePortal(boolean ignorePassenger) {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 160.0D * 160.0D;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
