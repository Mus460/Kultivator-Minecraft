package dev.kultivator.dunia.tempat.tema.air;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * {@code kultivator:alat_tenun} (DESIGN 38.T.9). Alat tenun Rumah Sutra Su (6 per aula): rangka, benang lungsin,
 * sisir berayun 15 derajat, teropong meluncur, dua pedal, gulungan kain yang membesar; sinkron klip 90 {@code
 * tenun}.
 * <p>
 * {@link #nilai()}: kemajuan kain 0..1. Never saved; re-made from the {@code alat_tenun} points of the weaving hall by
 * {@link RumahSutra}. The renderer (38TV-M) reads {@link #varian()}, {@link #keadaan()}, {@link #mulai()} and
 * {@link #nilai()} only. While {@link #KEADAAN_TENUN} the beater swings once every {@link #KETUKAN} ticks counted from
 * {@link #mulai()}: the weaving apprenticeship is the player's right-click on that beat ({@link RumahSutra#klikTenun}).
 */
public class AlatTenunEntity extends TemaMekanismeEntity {

	/** Kayu tua. */
	public static final int VARIAN_BIASA = 0;
	/** Benang merah (Kain Xiu Niang). */
	public static final int VARIAN_MERAH = 1;

	/** Diam. */
	public static final int KEADAAN_DIAM = 0;
	/** Sisir dan teropong bergerak. */
	public static final int KEADAAN_TENUN = 1;

	/** Ticks of one beat of the beater (the shuttle crosses on each beat). */
	public static final int KETUKAN = 20;

	public AlatTenunEntity(EntityType<? extends AlatTenunEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected boolean bisaDipilih() {
		return true;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (this.level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		return player instanceof ServerPlayer sp && RumahSutra.klikTenun(this, sp) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}
}
