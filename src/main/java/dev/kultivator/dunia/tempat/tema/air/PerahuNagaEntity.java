package dev.kultivator.dunia.tempat.tema.air;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * {@code kultivator:perahu_naga} (DESIGN 38.T.9). Perahu naga (18 x 2 blok): lambung sempit, kepala naga berahang
 * dan bertanduk, ekor melingkar, genderang di tengah, 10 pasang dayung serempak; pendayung = impostor klien. Lomba
 * Perahu Naga {@code d % 14 == 6}.
 * <p>
 * {@link #nilai()}: laju 0..1 (frekuensi dayung). Never saved; re-made from its anchor or titik. The renderer (38TV-M)
 * reads {@link #varian()}, {@link #keadaan()}, {@link #mulai()} and {@link #nilai()} only; the race is
 * {@link LombaPerahuNaga}. The one seat is the drum in the middle: the player who signs on as drummer sits there and
 * beats the drum (use key) on the beat.
 */
public class PerahuNagaEntity extends TemaMekanismeEntity {

	/** Sisik merah-emas. */
	public static final int VARIAN_MERAH = 0;
	/** Sisik hijau-emas. */
	public static final int VARIAN_HIJAU = 1;
	/** Sisik biru-perak. */
	public static final int VARIAN_BIRU = 2;
	/** Sisik hitam-emas. */
	public static final int VARIAN_HITAM = 3;

	/** Menunggu start. */
	public static final int KEADAAN_DIAM = 0;
	/** Dayung sinus, rahang membuka tiap ketukan. */
	public static final int KEADAAN_LOMBA = 1;

	public PerahuNagaEntity(EntityType<? extends PerahuNagaEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected int kursi() {
		return 1;
	}

	@Override
	protected boolean bisaDipilih() {
		return true;
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		return new Vec3(0.0D, 0.55D, 0.0D).yRot(-this.getYRot() * Mth.DEG_TO_RAD);
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (this.level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		return player instanceof ServerPlayer sp && LombaPerahuNaga.klik(this, sp) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}
}
