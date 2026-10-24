package dev.kultivator.dunia.tempat.tema.pelabuhan;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * {@code kultivator:perahu_nelayan} (DESIGN 38.T.9). Perahu nelayan (6 x 2 blok, lambung berujung lengkung, atap
 * anyaman, bangku, dayung): sewa memancing 2 NB per jam dunia, didayung pemain di teluk (tidak keluar > 200 blok
 * dari dermaga, kembali sendiri bila ditinggal), dan Lomba Perahu Nelayan Hari Dewi Laut (6 perahu di {@code
 * jalur_perahu}).
 * <p>
 * {@link #nilai()}: laju 0..1 (frekuensi kayuh). Never saved; re-made from its anchor or titik. Behaviour (spline,
 * schedule, clicks) lands in wave W2 by 38TK-P; the renderer (38TV-M) reads {@link #varian()}, {@link #keadaan()},
 * {@link #mulai()} and {@link #nilai()} only.
 */
public class PerahuNelayanEntity extends TemaMekanismeEntity {

	/** Kayu dan anyaman. */
	public static final int VARIAN_BIASA = 0;
	/** Pita merah lomba. */
	public static final int VARIAN_LOMBA = 1;

	/** Terombang-ambing. */
	public static final int KEADAAN_DIAM = 0;
	/** Dayung mengayuh. */
	public static final int KEADAAN_DAYUNG = 1;
	/** Kayuh cepat dan percikan. */
	public static final int KEADAAN_LOMBA = 2;

	public PerahuNelayanEntity(EntityType<? extends PerahuNelayanEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected int kursi() {
		return 2;
	}

	// ================================================================== behaviour (38TK-P, W2)

	/** Top speed in blocks per tick (a strong oarsman, about 5 blocks a second). */
	public static final double LAJU_MAKS = 0.26D;
	/** Turn rate in degrees per tick at full strafe input. */
	public static final float BELOK = 3.2F;

	private double laju;
	private double lastX;
	private double lastZ;

	/**
	 * The rower: the first passenger when it is a player. While a player rows, this boat is client-authoritative like a
	 * vanilla boat (the rower's client moves it, the server checks the move), so rowing feels immediate.
	 */
	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return this.getFirstPassenger() instanceof Player player ? player : null;
	}

	/** Seats: the rower at the stern bench, a passenger forward (model bench positions, 38TV-M). */
	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		int index = Math.max(0, this.getPassengers().indexOf(passenger));
		double z = index == 0 ? -1.1D : 1.0D;
		return new Vec3(0.0D, 0.28D, z).yRot(-this.getYRot() * ((float) Math.PI / 180.0F));
	}

	@Override
	public boolean isPickable() {
		return !this.isRemoved();
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (player.isSecondaryUseActive() || this.getPassengers().contains(player)) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer sp) {
			return PerahuSewa.naik(sp, this) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void tick() {
		double px = this.getX();
		double pz = this.getZ();
		super.tick();
		if (!this.level().isClientSide() && !this.isLocalInstanceAuthoritative()) {
			// rowed by a client: the server sees the moves arrive and derives the stroke rate for the renderer
			double moved = Math.sqrt((this.getX() - lastX) * (this.getX() - lastX) + (this.getZ() - lastZ) * (this.getZ() - lastZ));
			lastX = this.getX();
			lastZ = this.getZ();
			laju += (moved - laju) * 0.25D;
			int k = laju > 0.02D ? (keadaan() == KEADAAN_LOMBA ? KEADAAN_LOMBA : KEADAAN_DAYUNG) : KEADAAN_DIAM;
			if (keadaan() != KEADAAN_LOMBA || laju <= 0.02D) {
				setKeadaan(k);
			}
			setNilai((float) Math.min(1.0D, laju / LAJU_MAKS));
			return;
		}
		if (!this.isLocalInstanceAuthoritative()) {
			return;
		}
		lastX = px;
		lastZ = pz;
		LivingEntity rower = getControllingPassenger();
		if (rower == null) {
			laju *= 0.85D;
		} else {
			float forward = rower.zza;
			float strafe = rower.xxa;
			this.setYRot(this.getYRot() - strafe * BELOK);
			double want = forward > 0.0F ? LAJU_MAKS * forward : forward < 0.0F ? LAJU_MAKS * 0.4D * forward : 0.0D;
			laju += (want - laju) * 0.08D;
		}
		double r = Math.toRadians(this.getYRot());
		Vec3 motion = new Vec3(-Math.sin(r) * laju, 0.0D, Math.cos(r) * laju);
		// float: settle on the water surface under the hull (never sinks; a storm adds a small random push)
		Double surface = permukaan();
		double dy = surface == null ? -0.04D : (surface - 0.12D - this.getY()) * 0.3D;
		if (this.level().isThundering() && rower != null) {
			motion = motion.add((this.random.nextDouble() - 0.5D) * 0.03D, 0.0D, (this.random.nextDouble() - 0.5D) * 0.03D);
		}
		this.move(MoverType.SELF, new Vec3(motion.x, dy, motion.z));
		if (!this.level().isClientSide()) {
			int k = Math.abs(laju) > 0.02D ? (keadaan() == KEADAAN_LOMBA ? KEADAAN_LOMBA : KEADAAN_DAYUNG) : KEADAAN_DIAM;
			setKeadaan(k);
			setNilai((float) Math.min(1.0D, Math.abs(laju) / LAJU_MAKS));
		}
	}

	/** The water surface (top of the fluid) at the hull, or null when the hull is not over water within 3 blocks. */
	private @Nullable Double permukaan() {
		BlockPos.MutableBlockPos p = BlockPos.containing(this.getX(), this.getY() + 1.0D, this.getZ()).mutable();
		for (int i = 0; i < 4; i++) {
			FluidState fluid = this.level().getFluidState(p);
			if (!fluid.isEmpty()) {
				return p.getY() + (double) fluid.getHeight(this.level(), p);
			}
			p.move(0, -1, 0);
		}
		return null;
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (!this.level().isClientSide() && passenger instanceof ServerPlayer player) {
			PerahuSewa.turun(player, this);
		}
	}
}
