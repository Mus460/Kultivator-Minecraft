package dev.kultivator.dunia.tempat.tema.oasis;

import dev.kultivator.dunia.tempat.tema.bersama.TemaAnimasi;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * {@code kultivator:unta_kafilah} (DESIGN 38.T.4.4, 38.T.9.2): the Bactrian caravan camel (2.3 blocks tall, two humps
 * with shaggy tufts, long neck, knee-jointed legs). Walks a {@code pace} (both legs of one side together), nods, chews,
 * swishes its tail; kneels front legs first (20 ticks) then hind legs (20 ticks), body down 0.9 blocks, and stands up in
 * reverse; in a sandstorm it kneels with its head low.
 * <p>
 * Roles (W2, 38TK-O): 4-8 in a Kafilah Agung string (rope drawn by the client to the camel ahead), a hired riding camel
 * (12 NB a world day, speed 0.16, a 4 s dash every 10 s like the vanilla camel, walks home when the hire ends or it is
 * left alone more than 64 blocks for 2 minutes), and the caravan bodies 38EK asks for through
 * {@code TemaApi.tubuhKafilah(..., "UNTA", ...)}. Never saved; no breeding, no taming, no loot. Not hostile.
 */
public class UntaKafilahEntity extends PathfinderMob implements TemaAnimasi {

	/** Bare riding camel with saddle blanket. */
	public static final int VARIAN_POLOS = 0;
	/** Pack camel: frame, two sacks, a rolled carpet and a neck bell. */
	public static final int VARIAN_BEBAN = 1;
	/** Winter coat: heavier fur on humps, neck and thighs. */
	public static final int VARIAN_MUSIM_DINGIN = 2;

	/** Standing or walking (pace gait from the walk animation). */
	public static final int KEADAAN_BERDIRI = 0;
	/** Kneeling down: front legs (ticks 0-20), hind legs (20-40). */
	public static final int KEADAAN_BERLUTUT_TURUN = 1;
	/** Kneeling, chewing. */
	public static final int KEADAAN_BERLUTUT = 2;
	/** Getting up: hind legs then front legs (40 ticks). */
	public static final int KEADAAN_BANGUN = 3;
	/** Sandstorm: kneeling with the head low. */
	public static final int KEADAAN_BADAI = 4;
	/** Dash (the hired camel's 4 s sprint). */
	public static final int KEADAAN_LARI = 5;

	private static final EntityDataAccessor<Integer> DATA_VARIAN = SynchedEntityData.defineId(UntaKafilahEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_KEADAAN = SynchedEntityData.defineId(UntaKafilahEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_MULAI = SynchedEntityData.defineId(UntaKafilahEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_NILAI = SynchedEntityData.defineId(UntaKafilahEntity.class, EntityDataSerializers.FLOAT);

	/** Hired riding speed (38.T.4.4: faster than walking, slower than a horse). */
	public static final double KECEPATAN_SEWA = 0.16D;
	/** Dash: 4 s of sprint every 10 s (the vanilla camel's pattern), x1.6 speed. */
	public static final int LARI_TICK = 80;
	public static final int LARI_JEDA = 200;
	public static final float LARI_KALI = 1.6F;
	/** Kneel down / stand up (front legs then hind legs). */
	public static final int LUTUT_TICK = 40;
	/** A hired camel left this far from its renter for {@link #TINGGAL_TICK} walks home. */
	public static final double TINGGAL_JARAK = 64.0D;
	public static final int TINGGAL_TICK = 2400;
	/** Walk-home time before the body goes. */
	private static final int PULANG_TICK = 160;

	private long situsId;
	/** The renter (hired mode) or null. */
	private @Nullable UUID penyewa;
	/** Game time the hire ends. */
	private long sewaSampai;
	/** Ticks the renter has been away. */
	private int ditinggal;
	/** Dash cooldown and walk-home countdown. */
	private int jedaLari;
	private int pulang = -1;
	/** True while the camel walks in a caravan string (the AI, not the rider, steers). */
	private boolean rombongan;

	public UntaKafilahEntity(EntityType<? extends UntaKafilahEntity> type, Level level) {
		super(type, level);
		this.setCanPickUpLoot(false);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 32.0D)
				.add(Attributes.MOVEMENT_SPEED, KECEPATAN_SEWA)
				.add(Attributes.STEP_HEIGHT, 1.5D)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.5D)
				.add(Attributes.FOLLOW_RANGE, 32.0D);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VARIAN, VARIAN_POLOS);
		builder.define(DATA_KEADAAN, KEADAAN_BERDIRI);
		builder.define(DATA_MULAI, 0);
		builder.define(DATA_NILAI, 0.0F);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
	}

	/** Binds the camel to its town or caravan site (0 = unbound). */
	public void ikat(long situsId) {
		this.situsId = situsId;
	}

	public long situsId() {
		return situsId;
	}

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

	/** Lead-rope slack 0..1 (the client draws the rope to the camel ahead). */
	@Override
	public float nilai() {
		return this.entityData.get(DATA_NILAI);
	}

	public void setVarian(int varian) {
		this.entityData.set(DATA_VARIAN, varian);
	}

	public void setKeadaan(int keadaan) {
		if (keadaan() != keadaan) {
			this.entityData.set(DATA_KEADAAN, keadaan);
			this.entityData.set(DATA_MULAI, (int) (this.level().getGameTime() & 0x7fffffffL));
		}
	}

	public void setNilai(float nilai) {
		this.entityData.set(DATA_NILAI, nilai);
	}

	/** True while kneeling (a rider mounts and dismounts only then, or any time when hired). */
	public boolean berlutut() {
		int k = keadaan();
		return k == KEADAAN_BERLUTUT || k == KEADAAN_BADAI || k == KEADAAN_BERLUTUT_TURUN;
	}

	// ================================================================== roles

	/** Hired mode for {@code player} until game time {@code sampai} (12 NB a world day, {@link KafilahUnta}). */
	public void sewa(ServerPlayer player, long sampai) {
		this.penyewa = player.getUUID();
		this.sewaSampai = sampai;
		this.rombongan = false;
		this.ditinggal = 0;
		this.pulang = -1;
		setVarian(VARIAN_POLOS);
	}

	/** True while hired (by anyone). */
	public boolean disewa() {
		return penyewa != null && pulang < 0;
	}

	public @Nullable UUID penyewa() {
		return penyewa;
	}

	/** Caravan string mode: the server AI walks it; a rider only sits. */
	public void setRombongan(boolean on) {
		this.rombongan = on;
	}

	public boolean rombongan() {
		return rombongan;
	}

	/** Kneel (true) or get up (false) with the two-step animation. */
	public void lutut(boolean turun) {
		if (turun && !berlutut()) {
			setKeadaan(KEADAAN_BERLUTUT_TURUN);
			this.getNavigation().stop();
			playSound(SoundEvents.CAMEL_SIT, 0.8F, 1.0F);
		} else if (!turun && berlutut()) {
			setKeadaan(KEADAAN_BANGUN);
			playSound(SoundEvents.CAMEL_STAND, 0.8F, 1.0F);
		}
	}

	/** The sandstorm pose (kneeling, head low) or back to standing. */
	public void badai(boolean on) {
		if (on) {
			setKeadaan(KEADAAN_BADAI);
			this.getNavigation().stop();
		} else if (keadaan() == KEADAAN_BADAI) {
			setKeadaan(KEADAAN_BANGUN);
		}
	}

	/** Ends the hire: the camel walks away and goes. */
	public void pulang() {
		if (pulang >= 0) {
			return;
		}
		this.ejectPassengers();
		this.pulang = PULANG_TICK;
		this.penyewa = null;
		if (berlutut()) {
			setKeadaan(KEADAAN_BANGUN);
		}
		double rad = Math.toRadians(getYRot());
		this.getNavigation().moveTo(getX() - Math.sin(rad) * 20.0D, getY(), getZ() + Math.cos(rad) * 20.0D, 1.0D);
	}

	// ================================================================== ticking

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		// two-step kneel and stand
		int age = umurKeadaan(level.getGameTime());
		if (keadaan() == KEADAAN_BERLUTUT_TURUN && age >= LUTUT_TICK) {
			setKeadaan(KEADAAN_BERLUTUT);
		} else if (keadaan() == KEADAAN_BANGUN && age >= LUTUT_TICK) {
			setKeadaan(KEADAAN_BERDIRI);
		} else if (keadaan() == KEADAAN_LARI && age >= LARI_TICK) {
			setKeadaan(KEADAAN_BERDIRI);
		}
		if (jedaLari > 0) {
			jedaLari--;
		}
		if (pulang >= 0) {
			if (--pulang <= 0) {
				this.discard();
			}
			return;
		}
		if (penyewa != null && this.tickCount % 20 == 0) {
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(penyewa);
			if (level.getGameTime() >= sewaSampai || owner == null) {
				if (owner != null) {
					owner.sendSystemMessage(Component.translatable("kultivator.kota_tema.oasis.sewa.habis"));
				}
				KafilahUnta.sewaSelesai(level, this, owner);
				pulang();
				return;
			}
			if (owner.level() != level || owner.distanceToSqr(this) > TINGGAL_JARAK * TINGGAL_JARAK) {
				ditinggal += 20;
				if (ditinggal >= TINGGAL_TICK) {
					owner.sendSystemMessage(Component.translatable("kultivator.kota_tema.oasis.sewa.ditinggal"));
					KafilahUnta.sewaSelesai(level, this, owner);
					pulang();
					return;
				}
			} else {
				ditinggal = 0;
			}
			if (!isVehicle() && !berlutut() && this.getNavigation().isDone() && level.getRandom().nextInt(30) == 0) {
				lutut(true);
			}
		}
		if (penyewa == null && !rombongan && !isVehicle() && this.tickCount % 200 == 0 && level.getRandom().nextInt(4) == 0) {
			// resting pen camels kneel and rise now and then
			lutut(!berlutut());
		}
	}

	// ================================================================== riding (hired mode)

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND || this.isVehicle() || pulang >= 0) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer sp)) {
			return penyewa != null ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		if (penyewa == null || !penyewa.equals(sp.getUUID())) {
			if (!rombongan) {
				sp.sendSystemMessage(Component.translatable(penyewa == null ? "kultivator.kota_tema.oasis.sewa.bukan" : "kultivator.kota_tema.oasis.sewa.milik_lain"));
			}
			return InteractionResult.PASS;
		}
		if (berlutut()) {
			setKeadaan(KEADAAN_BERDIRI);
		}
		sp.startRiding(this);
		return InteractionResult.SUCCESS;
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		if (!rombongan && penyewa != null && this.getFirstPassenger() instanceof Player rider && penyewa.equals(rider.getUUID())) {
			return rider;
		}
		return super.getControllingPassenger();
	}

	@Override
	protected void tickRidden(Player controller, Vec3 riddenInput) {
		super.tickRidden(controller, riddenInput);
		Vec2 rotation = new Vec2(controller.getXRot() * 0.5F, controller.getYRot());
		this.setRot(rotation.y, rotation.x);
		this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
		if (!this.level().isClientSide() && controller.isSprinting() && jedaLari <= 0 && keadaan() == KEADAAN_BERDIRI && riddenInput.z > 0.0D) {
			setKeadaan(KEADAAN_LARI);
			jedaLari = LARI_JEDA;
			playSound(SoundEvents.CAMEL_DASH, 1.0F, 1.0F);
		}
	}

	@Override
	protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
		if (berlutut()) {
			return Vec3.ZERO;
		}
		float sideways = controller.xxa * 0.5F;
		float forward = controller.zza;
		if (forward <= 0.0F) {
			forward *= 0.25F;
		}
		return new Vec3(sideways, 0.0D, forward);
	}

	@Override
	protected float getRiddenSpeed(Player controller) {
		float speed = (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
		return keadaan() == KEADAAN_LARI ? speed * LARI_KALI : speed;
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		Vec3 base = super.getPassengerAttachmentPoint(passenger, dimensions, scale);
		return berlutut() ? base.add(0.0D, -0.9D * scale, 0.0D) : base;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		// players never hurt the town's camels; beasts of a caravan ambush may
		if (source.getEntity() instanceof Player) {
			return false;
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			KafilahUnta.untaMati(level, this);
		}
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return !berlutut();
	}
}
