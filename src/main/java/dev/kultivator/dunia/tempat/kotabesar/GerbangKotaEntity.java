package dev.kultivator.dunia.tempat.kotabesar;

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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * {@code kultivator:gerbang_kota}: the twin leaves of a capital gate (DESIGN 38.B.4.1, model 38.O.2 by 38MD). An entity,
 * never a block (38.0 rule 1): re-made by its {@code kotabesar/gerbang} anchor, never saved, invulnerable to players.
 * <ul>
 * <li>Shut, it blocks the arch with a thin box across the opening ({@code lebar} x {@code tinggi} from the anchor data,
 * oriented by the anchor's yaw), the boat / shulker collision pattern ({@link #canBeCollidedWith}); open, it lets
 * everything through.</li>
 * <li>{@link JamMalam} shuts every gate after the night drum (16000, 22.00) and opens them with the morning bell (1000, 07.00); a player
 * outside may knock ({@link #interact}): the gate guard opens it for a moment for 2 NB, free for an Honoured citizen,
 * never for a wanted one.</li>
 * <li>During a beast tide ({@code GelombangBinatang}) monsters may hurt it: {@link #KESEHATAN} points; at zero one leaf
 * breaks in ({@link #roboh()}) and the gate stays open until the tide ends. No block ever changes.</li>
 * </ul>
 * The client reads {@link #bukaLerp}, {@link #lebar()}, {@link #tinggi()} and {@link #robohLerp} through
 * {@code KaitModel} (set by {@code KotaBesarClient}).
 */
public class GerbangKotaEntity extends Entity {

	/** Leaf swing time (ticks), the 38.O.2 animation length. */
	public static final int AYUN_TICK = 24;
	/** Health against a beast tide (38.B.6.3). */
	public static final float KESEHATAN = 600.0F;
	/** Name of a Yamen cell door (one block wide, two high; {@link Hukum} makes and removes it). */
	public static final String NAMA_SEL = "sel";

	private static final EntityDataAccessor<Boolean> DATA_BUKA = SynchedEntityData.defineId(GerbangKotaEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_LEBAR = SynchedEntityData.defineId(GerbangKotaEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_TINGGI = SynchedEntityData.defineId(GerbangKotaEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_ROBOH = SynchedEntityData.defineId(GerbangKotaEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Float> DATA_RETAK = SynchedEntityData.defineId(GerbangKotaEntity.class, EntityDataSerializers.FLOAT);

	private long town;
	private String nama = "";
	private float kesehatan = KESEHATAN;
	/** Game time until which a knock keeps the gate open (0: follow the curfew). */
	private long bukaSampai;
	/** Client swing progress 0 shut .. 1 open, and its previous tick value. */
	private float ayun = 1.0F;
	private float ayunPrev = 1.0F;
	private float robohAnim;
	private float robohPrev;

	public GerbangKotaEntity(EntityType<? extends GerbangKotaEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.setPermanentlyInvulnerable(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_BUKA, true);
		builder.define(DATA_LEBAR, 4);
		builder.define(DATA_TINGGI, 6);
		builder.define(DATA_ROBOH, false);
		builder.define(DATA_RETAK, 0.0F);
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

	// ================================================================== setup and state

	/** From the anchor handler: size, town and name of this gate. */
	void pasang(long town, String nama, int lebar, int tinggi, boolean buka) {
		this.town = town;
		this.nama = nama == null ? "" : nama;
		this.entityData.set(DATA_LEBAR, Mth.clamp(lebar, 2, 12));
		this.entityData.set(DATA_TINGGI, Mth.clamp(tinggi, 3, 14));
		this.entityData.set(DATA_BUKA, buka);
		this.ayun = buka ? 1.0F : 0.0F;
		this.ayunPrev = this.ayun;
		this.reapplyPosition();
	}

	public long town() {
		return town;
	}

	public String nama() {
		return nama;
	}

	/** True for a Yamen cell door (never on the curfew, never knocked open). */
	public boolean sel() {
		return NAMA_SEL.equals(nama);
	}

	public boolean buka() {
		return this.entityData.get(DATA_BUKA) || roboh();
	}

	public int lebar() {
		return this.entityData.get(DATA_LEBAR);
	}

	public int tinggi() {
		return this.entityData.get(DATA_TINGGI);
	}

	public boolean roboh() {
		return this.entityData.get(DATA_ROBOH);
	}

	/** Damage share 0 whole .. 1 broken (client cracks and tremble). */
	public float retak() {
		return this.entityData.get(DATA_RETAK);
	}

	public float kesehatan() {
		return kesehatan;
	}

	/** Swings the leaves to {@code buka} (sound once, server side). */
	public void setBuka(boolean buka) {
		if (this.entityData.get(DATA_BUKA) == buka) {
			return;
		}
		this.entityData.set(DATA_BUKA, buka);
		if (this.level() instanceof ServerLevel level) {
			level.playSound(null, getX(), getY() + 1.0D, getZ(), buka ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.WOODEN_DOOR_CLOSE,
					SoundSource.BLOCKS, 1.6F, 0.45F);
			level.playSound(null, getX(), getY() + 1.0D, getZ(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 0.6F);
		}
	}

	/** Keeps the gate open until {@code sampai} (a knock at night); the curfew closes it again afterwards. */
	public void bukaSampai(long sampai) {
		this.bukaSampai = Math.max(this.bukaSampai, sampai);
		setBuka(true);
	}

	/** True while a knock still holds it open. */
	public boolean ditahanBuka(long now) {
		return now < bukaSampai;
	}

	/** Mends the gate (end of a beast tide). */
	public void pulihkan() {
		kesehatan = KESEHATAN;
		this.entityData.set(DATA_RETAK, 0.0F);
		this.entityData.set(DATA_ROBOH, false);
	}

	// ================================================================== tick

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			ayunPrev = ayun;
			float target = buka() ? 1.0F : 0.0F;
			float step = 1.0F / AYUN_TICK;
			ayun = target > ayun ? Math.min(target, ayun + step) : Math.max(target, ayun - step);
			robohPrev = robohAnim;
			robohAnim = roboh() ? Math.min(1.0F, robohAnim + 0.1F) : Math.max(0.0F, robohAnim - 0.05F);
		}
	}

	/** Client: leaf opening 0..1 at {@code partial}, eased in and out (easeInOutSine). */
	public float bukaLerp(float partial) {
		float t = Mth.lerp(partial, ayunPrev, ayun);
		return (float) (-(Math.cos(Math.PI * t) - 1.0D) / 2.0D);
	}

	/** Client: 0 whole .. 1 one leaf broken in. */
	public float robohLerp(float partial) {
		return Mth.lerp(partial, robohPrev, robohAnim);
	}

	// ================================================================== shape

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_LEBAR.equals(accessor) || DATA_TINGGI.equals(accessor)) {
			this.reapplyPosition();
		}
	}

	/** A thin box across the arch: {@code lebar} wide along the gate's own x axis (by yaw), one block deep. */
	@Override
	protected AABB makeBoundingBox(Vec3 pos) {
		if (this.entityData == null) {
			return super.makeBoundingBox(pos);
		}
		double half = lebar() / 2.0D;
		double depth = 0.5D;
		boolean alongX = Math.abs(Mth.wrapDegrees(this.getYRot())) < 45.0F || Math.abs(Mth.wrapDegrees(this.getYRot())) > 135.0F;
		double hx = alongX ? half : depth;
		double hz = alongX ? depth : half;
		return new AABB(pos.x - hx, pos.y, pos.z - hz, pos.x + hx, pos.y + tinggi(), pos.z + hz);
	}

	@Override
	public void setYRot(float yRot) {
		super.setYRot(yRot);
		if (this.entityData != null) {
			this.reapplyPosition();
		}
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return !buka();
	}

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
	protected boolean canAddPassenger(Entity passenger) {
		return false;
	}

	@Override
	protected boolean couldAcceptPassenger() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 160.0D * 160.0D;
	}

	// ================================================================== use and harm

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer && this.level() instanceof ServerLevel level) {
			if (sel()) {
				Hukum.ketukSel(serverPlayer, this);
			} else {
				JamMalam.ketuk(level, serverPlayer, this);
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** Players and projectiles never harm the gate; a beast tide's monsters do through {@link #pukul}. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	/**
	 * A blow of a beast tide monster standing at the shut gate (38.B.6.3; the tide's tick calls it, monster AI cannot
	 * target an entity that is not alive). At zero health one leaf breaks in and the gate stays open until the tide ends.
	 */
	public void pukul(ServerLevel level, Entity penyerang, float damage) {
		if (sel() || roboh() || buka() || !(penyerang instanceof Enemy) || !GelombangBinatang.serbu(town)) {
			return;
		}
		kesehatan = Math.max(0.0F, kesehatan - Math.max(0.0F, damage));
		this.entityData.set(DATA_RETAK, 1.0F - kesehatan / KESEHATAN);
		level.playSound(null, getX(), getY() + 1.0D, getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 1.0F, 0.6F);
		if (kesehatan <= 0.0F) {
			this.entityData.set(DATA_ROBOH, true);
			level.playSound(null, getX(), getY() + 1.0D, getZ(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 2.0F, 0.5F);
			GelombangBinatang.gerbangRoboh(level, this);
		}
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean skipAttackInteraction(Entity source) {
		return true;
	}
}
