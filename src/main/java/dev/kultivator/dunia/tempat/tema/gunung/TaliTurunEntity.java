package dev.kultivator.dunia.tempat.tema.gunung;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * {@code kultivator:tali_turun} (DESIGN 38.T.9). Tali yang dijatuhkan penjaga lift saat Lift Macet: tali panjat
 * dari kabin ke tanah; panjang dari {@code nilai}.
 * <p>
 * {@link #nilai()}: panjang tali dalam blok. Never saved; re-made from its anchor or titik. Behaviour (spline,
 * schedule, clicks) lands in wave W2 by 38TK-G; the renderer (38TV-M) reads {@link #varian()}, {@link #keadaan()},
 * {@link #mulai()} and {@link #nilai()} only.
 * <p>
 * Behaviour (38TK-G): a click (from the cabin or below it) lets the player slide down: Slow Falling for 15 s and a
 * line of the keeper; the stuck cabin's riders may leave this way ({@link KeretaGantung}).
 */
public class TaliTurunEntity extends TemaMekanismeEntity {

	/** Tali ijuk. */
	public static final int VARIAN_BIASA = 0;

	/** Terjulur. */
	public static final int KEADAAN_TURUN = 0;
	/** Ditarik kembali. */
	public static final int KEADAAN_DITARIK = 1;

	public TaliTurunEntity(EntityType<? extends TaliTurunEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected boolean bisaDipilih() {
		return keadaan() == KEADAAN_TURUN;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer sp) {
			if (sp.isPassenger()) {
				sp.stopRiding();
			}
			sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 300, 0, false, true));
			dev.kultivator.util.Msg.actionBar(sp, net.minecraft.network.chat.Component.translatable("kultivator.kota_tema.gunung.lift.tali")
					.withStyle(net.minecraft.ChatFormatting.AQUA));
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.SUCCESS;
	}
}
