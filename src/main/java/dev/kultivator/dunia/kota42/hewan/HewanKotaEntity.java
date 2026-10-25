package dev.kultivator.dunia.kota42.hewan;

import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.bersama.TemaAnimasi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Base of the seven town animals of DESIGN 42 (42.7.5, 42.12.2): light {@link PathfinderMob}s, never saved (the type is
 * {@code noSave}; {@link HewanKota} calls them from animal titik inside the place box only and takes them back when no
 * player is near), no loot table, never despawning on their own, never leashed or tamed, no fall damage (cats leap off
 * roofs).
 * <p>
 * Synced state for the 42V renderers ({@link TemaAnimasi}, the same contract as the themed creatures of 38.T):
 * {@link #varian()} the coat or breed ({@code VARIAN_*} of the class), {@link #keadaan()} the current animation state
 * ({@code KEADAAN_*}; 0 = idle, walking and running come from the speed), {@link #mulai()} the tick the state began,
 * {@link #nilai()} one class-defined 0..1 value. A variant may change the body size ({@link #skala(int)}: guard dog,
 * chick); the box follows on both sides.
 * <p>
 * Behaviour (42D-H): every animal keeps to its titik ({@link #ikat(long, long)}, {@link #rumah()}): roaming kinds stroll
 * within {@link #jelajah()} blocks of it and walk back when pushed away, tethered kinds stand at the rail. Timed poses
 * ({@link #pose(int, int)}) fall back to idle when they run out or when the animal starts walking. Monsters within 10
 * blocks, a danger zone of 38 ({@link HidupApi#bahayaDi}) or a hit make it run and hide for 10 s ({@link #kabur}).
 * An animal can be asked to follow a player or a resident body for a while ({@link #ikuti(Entity, int)}). Subclasses add
 * their own goals ({@link #tambahTujuan()}), the idle choice every quarter second ({@link #perilaku(ServerLevel, long)})
 * and the click ({@link #interaksi(ServerPlayer, ItemStack)}); a plain click with an empty hand pets it
 * ({@link #elus(ServerPlayer)}).
 */
public abstract class HewanKotaEntity extends PathfinderMob implements TemaAnimasi {

	private static final EntityDataAccessor<Integer> DATA_VARIAN = SynchedEntityData.defineId(HewanKotaEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_KEADAAN = SynchedEntityData.defineId(HewanKotaEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_MULAI = SynchedEntityData.defineId(HewanKotaEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_NILAI = SynchedEntityData.defineId(HewanKotaEntity.class, EntityDataSerializers.FLOAT);

	/** Seconds an animal runs and hides after a fright. */
	public static final int KABUR_TICK = 200;
	/** Monsters within this many blocks frighten it. */
	public static final double JARAK_MONSTER = 10.0D;

	private long situsId;
	private long jangkar;
	/** Game time the timed pose ends (0 = the pose holds until changed). */
	private long poseSampai;
	/** Fright: until game time, and what it runs from. */
	private long kaburSampai;
	private @Nullable Vec3 ancaman;
	/** Following: the entity (player or resident body), until game time, and the player uuid for a re-login. */
	private @Nullable Entity ikutEntitas;
	private @Nullable UUID ikutPemain;
	private long ikutSampai;
	/** The spawner's key of this animal ({@link HewanKota} plan entry), "" for a test or event animal. */
	private String kunci = "";
	/** Untied and led by someone else (a wedding procession): the tether goal lets go. */
	private boolean dituntun;

	protected HewanKotaEntity(EntityType<? extends HewanKotaEntity> type, Level level) {
		super(type, level);
		this.setCanPickUpLoot(false);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VARIAN, 0);
		builder.define(DATA_KEADAAN, 0);
		builder.define(DATA_MULAI, 0);
		builder.define(DATA_NILAI, 0.0F);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new TujuanKabur());
		this.goalSelector.addGoal(3, new TujuanIkut());
		tambahTujuan();
		if (berkeliaran()) {
			this.goalSelector.addGoal(6, new TujuanJelajah());
		} else {
			this.goalSelector.addGoal(6, new TujuanTambat());
		}
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F) {
			@Override
			public boolean canUse() {
				return !tidur() && super.canUse();
			}
		});
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this) {
			@Override
			public boolean canUse() {
				return !tidur() && super.canUse();
			}
		});
	}

	/** Extra goals of the subclass (priorities 2, 4 and 5); called from the constructor, so read no subclass field here. */
	protected void tambahTujuan() {
	}

	/** True for animals that roam their titik (cats, dogs, chickens); tethered animals stay put. Constant per class. */
	protected boolean berkeliaran() {
		return true;
	}

	/** Blocks a roaming animal strolls from its titik. */
	protected int jelajah() {
		return 8;
	}

	/** Size factor of a variant (1 = the type's box). */
	protected float skala(int varian) {
		return 1.0F;
	}

	/** True for a resting pose (no strolling, no looking around while it holds). */
	protected boolean istirahat(int keadaan) {
		return false;
	}

	/** True while it sleeps (eyes shut: no looking at players). */
	protected boolean tidur() {
		return false;
	}

	/** Where it hides from a fright, or null to run away from the threat. */
	protected @Nullable BlockPos tempatSembunyi() {
		return null;
	}

	/** The idle choice, every 5 ticks on the server (not while frightened or walking a goal of its own). */
	protected void perilaku(ServerLevel level, long now) {
	}

	/** A click with {@code stack} in the main hand; return true when handled. The default only pets with an empty hand. */
	protected boolean interaksi(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			elus(player);
			return true;
		}
		return false;
	}

	/** Petting: the subclass answers (purr, wag, nuzzle). */
	protected void elus(ServerPlayer player) {
	}

	// ================================================================== binding

	/** Binds the animal to its site and titik (packed {@code BlockPos.asLong()}); server side, right after spawning. */
	public void ikat(long situsId, long jangkar) {
		this.situsId = situsId;
		this.jangkar = jangkar;
	}

	public long situsId() {
		return situsId;
	}

	public long jangkar() {
		return jangkar;
	}

	/** The titik it keeps to (its anchor block), or null when unbound (a test animal keeps to where it was made). */
	public @Nullable BlockPos rumah() {
		return jangkar == 0L ? null : BlockPos.of(jangkar);
	}

	/** Moves its home (a guard dog posted to a new door). */
	public void pindahRumah(BlockPos pos) {
		this.jangkar = pos.asLong();
	}

	/** Unties it (true) so an event can lead or ride it, or ties it back to its titik (false). */
	public void setDituntun(boolean dituntun) {
		this.dituntun = dituntun;
	}

	public boolean dituntun() {
		return dituntun;
	}

	/** The spawner key ("" = not from a plan). */
	public String kunci() {
		return kunci;
	}

	void setKunci(String kunci) {
		this.kunci = kunci == null ? "" : kunci;
	}

	// ================================================================== animation state

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

	/** Sets the state and stamps its start tick (server); the same state again keeps the old start. Holds until changed. */
	public void setKeadaan(int keadaan) {
		this.poseSampai = 0L;
		ubahKeadaan(keadaan);
	}

	/** A timed pose: {@code keadaan} for {@code tick} ticks, then idle (0). */
	public void pose(int keadaan, int tick) {
		ubahKeadaan(keadaan);
		this.poseSampai = this.level().getGameTime() + Math.max(1, tick);
	}

	private void ubahKeadaan(int keadaan) {
		if (keadaan() != keadaan) {
			this.entityData.set(DATA_KEADAAN, keadaan);
			this.entityData.set(DATA_MULAI, (int) (this.level().getGameTime() & 0x7fffffffL));
		}
	}

	/** Ticks the current state has run. */
	public int lamaKeadaan() {
		return (int) ((this.level().getGameTime() & 0x7fffffffL) - mulai());
	}

	public void setNilai(float nilai) {
		if (Float.compare(nilai(), nilai) != 0) {
			this.entityData.set(DATA_NILAI, nilai);
		}
	}

	// ================================================================== fright and following

	/** True while frightened. */
	public boolean takut() {
		return kaburSampai > this.level().getGameTime();
	}

	/** Frightens it for {@link #KABUR_TICK} ticks, running from {@code dari}. */
	public void kabur(Vec3 dari) {
		boolean baru = !takut();
		this.kaburSampai = this.level().getGameTime() + KABUR_TICK;
		this.ancaman = dari;
		this.ikutSampai = 0L;
		if (baru) {
			takutMulai();
		}
	}

	/** The subclass reacts the moment a fright starts (a squawk, a hiss). */
	protected void takutMulai() {
	}

	/** Follows {@code sasaran} (a player or a resident body) for {@code tick} ticks. */
	public void ikuti(Entity sasaran, int tick) {
		this.ikutEntitas = sasaran;
		this.ikutPemain = sasaran instanceof Player p ? p.getUUID() : null;
		this.ikutSampai = this.level().getGameTime() + Math.max(1, tick);
		if (istirahat(keadaan())) {
			setKeadaan(0);
		}
	}

	/** Stops following. */
	public void berhentiIkut() {
		this.ikutSampai = 0L;
		this.ikutEntitas = null;
		this.ikutPemain = null;
	}

	/** Who it follows now, or null. */
	public @Nullable Entity diikuti() {
		if (ikutSampai <= this.level().getGameTime()) {
			return null;
		}
		Entity e = ikutEntitas;
		if ((e == null || e.isRemoved()) && ikutPemain != null && this.level() instanceof ServerLevel level) {
			e = level.getServer().getPlayerList().getPlayer(ikutPemain);
			ikutEntitas = e;
		}
		if (e == null || e.isRemoved() || e.level() != this.level()) {
			return null;
		}
		return e;
	}

	/** The player it follows (fed by them), or null. */
	public @Nullable UUID pemainDiikuti() {
		return ikutSampai > this.level().getGameTime() ? ikutPemain : null;
	}

	// ================================================================== vanilla hooks

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		Entity by = source.getEntity();
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && this.isAlive()) {
			kabur(by != null ? by.position() : this.position().add(this.random.nextDouble() - 0.5D, 0.0D, this.random.nextDouble() - 0.5D));
		}
		return hurt;
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || !this.isAlive()) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		if (takut()) {
			return InteractionResult.PASS;
		}
		return interaksi(sp, player.getItemInHand(hand)) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		long now = level.getGameTime();
		if (poseSampai != 0L && now >= poseSampai) {
			poseSampai = 0L;
			ubahKeadaan(0);
		}
		if (Math.floorMod(this.tickCount + this.getId(), 10) == 0) {
			periksaBahaya(level);
		}
		if (this.getNavigation().isInProgress() && istirahat(keadaan())) {
			setKeadaan(0);
		}
		if (!takut() && Math.floorMod(this.tickCount + this.getId(), 5) == 0) {
			perilaku(level, now);
		}
	}

	private void periksaBahaya(ServerLevel level) {
		List<Monster> near = level.getEntitiesOfClass(Monster.class, this.getBoundingBox().inflate(JARAK_MONSTER), Entity::isAlive);
		if (!near.isEmpty()) {
			Monster nearest = near.getFirst();
			for (Monster m : near) {
				if (m.distanceToSqr(this) < nearest.distanceToSqr(this)) {
					nearest = m;
				}
			}
			kabur(nearest.position());
			return;
		}
		HidupApi.Bahaya b = HidupApi.bahayaDi(level, this.blockPosition());
		if (b != null) {
			kabur(Vec3.atCenterOf(b.pusat()));
		}
	}

	// ================================================================== helpers for subclasses

	/** Plays {@code sound} at the animal (server; heard by everyone near). */
	protected void bunyi(@Nullable SoundEvent sound, float volume, float pitch) {
		if (sound != null && !this.isSilent()) {
			this.level().playSound(null, this.getX(), this.getY(), this.getZ(), sound, SoundSource.NEUTRAL, volume, pitch);
		}
	}

	/** Sends {@code n} particles above the animal (server). */
	protected void partikel(ParticleOptions particle, int n, double dy) {
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(particle, this.getX(), this.getY() + dy, this.getZ(), n, 0.25D, 0.15D, 0.25D, 0.02D);
		}
	}

	/** True when a player is within {@code r} blocks and moving fast (sprinting) — what scatters chickens and pigeons. */
	protected @Nullable Player pemainBerlari(double r) {
		for (Player p : this.level().players()) {
			if (!p.isSpectator() && p.isSprinting() && p.distanceToSqr(this) <= r * r) {
				return p;
			}
		}
		return null;
	}

	/** Distance squared to its titik (0 when unbound). */
	protected double jarakRumahSqr() {
		BlockPos home = rumah();
		return home == null ? 0.0D : this.distanceToSqr(Vec3.atBottomCenterOf(home));
	}

	/** Walks to {@code pos} at {@code speed}; false when no path. */
	protected boolean jalanKe(Vec3 pos, double speed) {
		return this.getNavigation().moveTo(pos.x, pos.y, pos.z, speed);
	}

	// ================================================================== size

	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		return super.getDefaultDimensions(pose).scale(skala(varian()));
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_VARIAN.equals(accessor)) {
			this.refreshDimensions();
		}
	}

	// ================================================================== goals

	/** Runs from a fright: to its hiding place (cats: the roof) or away from the threat. */
	private final class TujuanKabur extends Goal {
		private int ulang;

		TujuanKabur() {
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return takut() && !HewanKotaEntity.this.isPassenger();
		}

		@Override
		public void start() {
			setKeadaan(0);
			ulang = 0;
			lari();
		}

		@Override
		public void tick() {
			if (--ulang <= 0 && HewanKotaEntity.this.getNavigation().isDone()) {
				lari();
			}
		}

		private void lari() {
			ulang = 20;
			BlockPos hide = tempatSembunyi();
			if (hide != null && HewanKotaEntity.this.distanceToSqr(Vec3.atBottomCenterOf(hide)) > 2.0D
					&& jalanKe(Vec3.atBottomCenterOf(hide), 1.5D)) {
				return;
			}
			Vec3 from = ancaman != null ? ancaman : HewanKotaEntity.this.position();
			Vec3 away = DefaultRandomPos.getPosAway(HewanKotaEntity.this, 10, 4, from);
			if (away != null) {
				jalanKe(away, 1.5D);
			}
		}

		@Override
		public void stop() {
			HewanKotaEntity.this.getNavigation().stop();
		}
	}

	/** Follows the player who fed it, or a resident body that feeds or plays with it. */
	private final class TujuanIkut extends Goal {
		private int ulang;

		TujuanIkut() {
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			Entity e = diikuti();
			return e != null && e.distanceToSqr(HewanKotaEntity.this) > 6.0D;
		}

		@Override
		public boolean canContinueToUse() {
			Entity e = diikuti();
			return e != null && e.distanceToSqr(HewanKotaEntity.this) > 3.0D;
		}

		@Override
		public void start() {
			ulang = 0;
			if (istirahat(keadaan())) {
				setKeadaan(0);
			}
		}

		@Override
		public void tick() {
			Entity e = diikuti();
			if (e == null) {
				return;
			}
			HewanKotaEntity.this.getLookControl().setLookAt(e, 20.0F, 20.0F);
			if (--ulang <= 0) {
				ulang = 10;
				double d2 = e.distanceToSqr(HewanKotaEntity.this);
				if (d2 > 24.0D * 24.0D) {
					// left far behind: it gives up rather than crossing the whole town
					berhentiIkut();
					return;
				}
				HewanKotaEntity.this.getNavigation().moveTo(e, d2 > 64.0D ? 1.35D : 1.05D);
			}
		}

		@Override
		public void stop() {
			HewanKotaEntity.this.getNavigation().stop();
		}
	}

	/** Strolls within {@link #jelajah()} blocks of the titik; walks home when it strayed further. */
	private final class TujuanJelajah extends Goal {
		private @Nullable Vec3 tujuan;

		TujuanJelajah() {
			setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (istirahat(keadaan()) || dituntun || HewanKotaEntity.this.isPassenger() || HewanKotaEntity.this.isVehicle()) {
				return false;
			}
			BlockPos home = rumah();
			int r = jelajah();
			if (home != null && jarakRumahSqr() > (double) (r + 4) * (r + 4)) {
				tujuan = Vec3.atBottomCenterOf(home);
				return true;
			}
			if (HewanKotaEntity.this.getRandom().nextInt(90) != 0) {
				return false;
			}
			BlockPos base = home != null ? home : HewanKotaEntity.this.blockPosition();
			var rnd = HewanKotaEntity.this.getRandom();
			tujuan = new Vec3(base.getX() + 0.5D + rnd.nextInt(r * 2 + 1) - r, base.getY(), base.getZ() + 0.5D + rnd.nextInt(r * 2 + 1) - r);
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return !HewanKotaEntity.this.getNavigation().isDone() && !istirahat(keadaan());
		}

		@Override
		public void start() {
			if (tujuan != null && !jalanKe(tujuan, 0.8D)) {
				Vec3 near = DefaultRandomPos.getPos(HewanKotaEntity.this, Math.max(3, jelajah() / 2), 2);
				if (near != null) {
					jalanKe(near, 0.8D);
				}
			}
		}
	}

	/** A tethered animal stays at its rail: led back when pushed more than 1.5 blocks away. */
	private final class TujuanTambat extends Goal {

		TujuanTambat() {
			setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return rumah() != null && !dituntun && !HewanKotaEntity.this.isPassenger() && !HewanKotaEntity.this.isVehicle()
					&& jarakRumahSqr() > 2.25D;
		}

		@Override
		public boolean canContinueToUse() {
			return !HewanKotaEntity.this.getNavigation().isDone() && jarakRumahSqr() > 0.5D;
		}

		@Override
		public void start() {
			BlockPos home = rumah();
			if (home != null && !jalanKe(Vec3.atBottomCenterOf(home), 0.7D) && jarakRumahSqr() > 36.0D) {
				// no path back (a fence closed behind it): it is simply found at the rail again
				HewanKotaEntity.this.snapTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, HewanKotaEntity.this.getYRot(), 0.0F);
			}
		}
	}
}
