package dev.kultivator.dunia.tempat.tema.air;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * {@code kultivator:perahu_kanal} (DESIGN 38.T.9). Perahu kanal (7 x 2 blok, ujung naik, atap hitam lengkung tiga
 * segmen, yuloh berpivot di buritan, lentera haluan): Perahu Pos antar-halte, Tur Kanal, sewa didayung pemain
 * (kembali bila ditinggal 2 menit), perahu-lapak, dan sampan cepat Tikus Air. Bergerak sepanjang spline {@code
 * jalur_perahu}; yaw dari turunan spline.
 * <p>
 * {@link #nilai()}: laju 0..1. Never saved; re-made from its anchor or titik. The renderer (38TV-M) reads
 * {@link #varian()}, {@link #keadaan()}, {@link #mulai()} and {@link #nilai()} only; the behaviour lives in
 * {@link Perahu} (Perahu Pos, tour, hire, stall boats) and {@link TikusAir} (the thieves' black skiff).
 * <p>
 * Seats ({@link #kursi()} 3): passenger 0 sits at the stern (the rower: an NPC body playing {@code dayung_yuloh}, or the
 * player who hired the boat), 1 and 2 midships and at the bow. Right-click boards / pays ({@link Perahu#klik}); a hit on
 * the black skiff is a hit on the thieves' getaway ({@link TikusAir#dipukul}).
 */
public class PerahuKanalEntity extends TemaMekanismeEntity {

	/** Perahu pos. */
	public static final int VARIAN_BIASA = 0;
	/** Tur kanal berhias. */
	public static final int VARIAN_WISATA = 1;
	/** 4 peti/keranjang + tenda. */
	public static final int VARIAN_LAPAK = 2;
	/** Sampan cepat Tikus Air. */
	public static final int VARIAN_HITAM = 3;

	/** Diam di halte. */
	public static final int KEADAAN_TAMBAT = 0;
	/** Yuloh mengayun +-25 derajat sinkron klip 79. */
	public static final int KEADAAN_JALAN = 1;
	/** Sampan kabur cepat. */
	public static final int KEADAAN_KEJAR = 2;

	/** Seat offsets along the boat (blocks toward the bow): stern rower, midships, bow. */
	private static final double[] KURSI_Z = {-2.4D, 0.1D, 1.5D};
	/** Seat height above the entity position. */
	private static final double KURSI_Y = 0.32D;

	public PerahuKanalEntity(EntityType<? extends PerahuKanalEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected int kursi() {
		return 3;
	}

	@Override
	protected boolean bisaDipilih() {
		return true;
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		int i = Math.max(0, Math.min(KURSI_Z.length - 1, this.getPassengers().indexOf(passenger)));
		return new Vec3(0.0D, KURSI_Y, KURSI_Z[i]).yRot(-this.getYRot() * Mth.DEG_TO_RAD);
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (this.level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		return player instanceof ServerPlayer sp && Perahu.klik(this, sp) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (varian() == VARIAN_HITAM && source.getEntity() instanceof ServerPlayer player) {
			TikusAir.dipukul(level, this, player);
		}
		return false;
	}
}
