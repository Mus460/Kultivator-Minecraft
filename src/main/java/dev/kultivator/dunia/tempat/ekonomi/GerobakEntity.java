package dev.kultivator.dunia.tempat.ekonomi;

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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiPredicate;

/**
 * {@code kultivator:gerobak}, the caravan cart (DESIGN 38.C.6.3, 38.M.4.2, 38.O.2). An entity, never a block (38.0 rule 1):
 * never saved (the caravan lives as data in {@link KafilahJalan} / {@link KafilahSekte} and the body is re-made when a
 * player comes within 64 blocks), invulnerable, not pushable. On the server it rolls at the caravan's 0.8 blocks a second
 * toward its target, following the ground and facing its way; the client renderer (38MD) turns the wheels by the distance
 * travelled and draws the load {@link #muatan()} (0 chests, 1 sacks, 2 jars).
 * <p>
 * DESIGN 42.7.4 (street event "gerobak terguling", 42D-P): {@link #terguling()} is synced; the renderer lays the cart on
 * its side with the load spilled, and an overturned cart never rolls until it is set upright again. An overturned cart
 * is pickable and a right-click goes to the handler the street event registers ({@link #setPenangan}: lift it). A street
 * cart (no caravan, no target) lives only while its event holds it ({@link #pegang}).
 */
public class GerobakEntity extends Entity {

	/** Blocks per tick (0.8 blocks a second). */
	public static final double LAJU = 0.04D;
	private static final EntityDataAccessor<Integer> DATA_MUATAN = SynchedEntityData.defineId(GerobakEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_TERGULING = SynchedEntityData.defineId(GerobakEntity.class, EntityDataSerializers.BOOLEAN);

	private static volatile @Nullable BiPredicate<GerobakEntity, ServerPlayer> penangan;

	private long kafilah;
	private Vec3 tujuan;
	private boolean berhenti;
	private long pegangSampai;

	/** 42D-P ({@code PeristiwaJalan}): what a right-click on an overturned cart does; true when handled. */
	public static void setPenangan(@Nullable BiPredicate<GerobakEntity, ServerPlayer> handler) {
		penangan = handler;
	}

	public GerobakEntity(EntityType<? extends GerobakEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(false);
		this.setPermanentlyInvulnerable(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_MUATAN, 0);
		builder.define(DATA_TERGULING, false);
	}

	/** The load drawn: 0 chests, 1 sacks, 2 jars. */
	public int muatan() {
		return this.entityData.get(DATA_MUATAN);
	}

	public void setMuatan(int muatan) {
		this.entityData.set(DATA_MUATAN, Math.floorMod(muatan, 3));
	}

	/** True while the cart lies overturned on its side (42.7.4). */
	public boolean terguling() {
		return this.entityData.get(DATA_TERGULING);
	}

	public void setTerguling(boolean terguling) {
		this.entityData.set(DATA_TERGULING, terguling);
	}

	/** The caravan this body shows (0 for none). */
	public long kafilah() {
		return kafilah;
	}

	/** Binds the body to caravan {@code id} rolling toward {@code to}. */
	public void ikat(long id, Vec3 to) {
		this.kafilah = id;
		this.tujuan = to;
	}

	/** Holds the cart (bandits on the road, the escort fell behind). */
	public void setBerhenti(boolean berhenti) {
		this.berhenti = berhenti;
	}

	/** Server: a street-event cart (no caravan) is kept until game time {@code sampai}, renewed while its event runs. */
	public void pegang(long sampai) {
		this.pegangSampai = sampai;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel held && kafilah == 0L && pegangSampai > 0L && held.getGameTime() > pegangSampai) {
			this.discard();
			return;
		}
		if (!(this.level() instanceof ServerLevel level) || tujuan == null) {
			return;
		}
		if (kafilah != 0L && !KafilahJalan.hidup(kafilah) && !KafilahSekte.hidup(kafilah)) {
			this.discard();
			return;
		}
		if (berhenti || terguling()) {
			return;
		}
		double dx = tujuan.x - this.getX();
		double dz = tujuan.z - this.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance < 0.5D) {
			return;
		}
		double nx = this.getX() + dx / distance * LAJU;
		double nz = this.getZ() + dz / distance * LAJU;
		int bx = Mth.floor(nx);
		int bz = Mth.floor(nz);
		if (!level.isPositionEntityTicking(new net.minecraft.core.BlockPos(bx, 0, bz))) {
			return;
		}
		double ny = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
		// never jump more than a step: a cliff or a wall holds the cart where it is
		if (Math.abs(ny - this.getY()) > 1.2D) {
			ny = this.getY() + Math.signum(ny - this.getY()) * 0.1D;
		}
		this.setYRot((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F);
		this.setPos(nx, ny, nz);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return terguling() && !this.isRemoved();
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND || !terguling()) {
			return InteractionResult.PASS;
		}
		if (this.level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		BiPredicate<GerobakEntity, ServerPlayer> handler = penangan;
		return handler != null && player instanceof ServerPlayer sp && handler.test(this, sp) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	@Override
	public PushReaction getPistonPushReaction() {
		return PushReaction.IGNORE_ENTITY;
	}
}
