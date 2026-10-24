package dev.kultivator.dunia.tempat.tema.gunung;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * {@code kultivator:kereta_gantung} (DESIGN 38.T.9). Kabin kereta gantung (6 penumpang): kabin kayu berkisi
 * beratap bertingkat, lengan gantung, troli beroda, pintu geser. Dua bentang lewat Tiang Tengah, 40 s sekali
 * jalan; berayun pendulum teredam; tali = kurva gantung klien. Tidak pernah menjatuhkan penumpang; Lift Macet
 * berhenti di tengah bentang.
 * <p>
 * {@link #nilai()}: kemajuan bentang 0..1 (0 = stasiun bawah, 1 = stasiun atas). Never saved; re-made from its anchor or
 * titik. The line, the schedule and the riders are {@link KeretaGantung}'s; this class only answers clicks (board),
 * seats and the dismount spot. The renderer (38TV-M) reads {@link #varian()}, {@link #keadaan()}, {@link #mulai()} and
 * {@link #nilai()} only.
 */
public class KeretaGantungEntity extends TemaMekanismeEntity {

	/** Kayu merah. */
	public static final int VARIAN_BIASA = 0;

	/** Pintu terbuka. */
	public static final int KEADAAN_STASIUN = 0;
	/** Berjalan. */
	public static final int KEADAAN_JALAN = 1;
	/** Berhenti di tengah bentang. */
	public static final int KEADAAN_MACET = 2;
	/** Pintu geser 10 tick. */
	public static final int KEADAAN_PINTU = 3;

	/** Seat offsets in the cabin (x across, z along the line) for 6 riders: two benches of three. */
	private static final double[][] KURSI = {{-0.6D, -0.7D}, {0.0D, -0.7D}, {0.6D, -0.7D}, {-0.6D, 0.7D}, {0.0D, 0.7D}, {0.6D, 0.7D}};

	public KeretaGantungEntity(EntityType<? extends KeretaGantungEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected boolean padat() {
		return keadaan() != KEADAAN_STASIUN;
	}

	@Override
	protected boolean bisaDipilih() {
		return true;
	}

	@Override
	protected int kursi() {
		return 6;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer sp) {
			return KeretaGantung.naik(sp, this) ? InteractionResult.SUCCESS_SERVER : InteractionResult.CONSUME;
		}
		return InteractionResult.SUCCESS;
	}

	/** Seat {@code index} (0-5) offset from the cabin origin, turned with the cabin's yaw. */
	public static Vec3 kursiOffset(int index, float yaw) {
		double[] k = KURSI[Math.floorMod(index, KURSI.length)];
		return new Vec3(k[0], 0.35D, k[1]).yRot((float) Math.toRadians(-yaw));
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		int index = Math.max(0, this.getPassengers().indexOf(passenger));
		double[] k = KURSI[index % KURSI.length];
		return new Vec3(k[0], 0.35D, k[1]).yRot((float) Math.toRadians(-this.getYRot()));
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		return KeretaGantung.turunDi(this);
	}

	/** Never written to a chunk (once the type accepts passengers it is no longer {@code noSave}). */
	@Override
	public boolean shouldBeSaved() {
		return false;
	}
}
