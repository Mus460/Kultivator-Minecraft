package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * {@code kultivator:sel_tawanan} (DESIGN 38.T.9). Pintu sel Kandang Tawanan (10 per kandang, pola {@code
 * SangkarEntity}, model {@code sangkar} 38MD): hanya dibuka dengan Tebus atau Tebusan Darah; tidak ada pilihan
 * memiliki tawanan (38.T.0 butir 5).
 * <p>
 * {@link #nilai()}: ayun pintu 0..1. Never saved; re-made from its anchor or titik. Behaviour (spline, schedule,
 * clicks) by 38TK-I (a click opens {@code tema_tawanan}; {@link KandangTawanan}); the renderer (38TV-M) reads {@link #varian()}, {@link #keadaan()}, {@link
 * #mulai()} and {@link #nilai()} only.
 */
public class SelTawananEntity extends TemaMekanismeEntity {

	/** Jeruji besi. */
	public static final int VARIAN_BIASA = 0;

	/** Terkunci. */
	public static final int KEADAAN_TERKUNCI = 0;
	/** Dibuka. */
	public static final int KEADAAN_TERBUKA = 1;

	public SelTawananEntity(EntityType<? extends SelTawananEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected boolean padat() {
		return keadaan() == KEADAAN_TERKUNCI;
	}

	@Override
	protected boolean bisaDipilih() {
		return true;
	}

	/** A click on a locked cell opens the pens' screen (ransom or Tebusan Darah only; there is no other choice). */
	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp && keadaan() == KEADAAN_TERKUNCI) {
			TemaLayar.buka(sp, TemaLayar.Tawanan.LAYAR, -1);
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.PASS;
	}
}
