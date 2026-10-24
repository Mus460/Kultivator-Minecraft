package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import dev.kultivator.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * {@code kultivator:segel_pintu} (DESIGN 38.T.9). Segel lantai 9 Menara Kitab Terlarang: pelat bertulisan pancar;
 * terbuka saat Pesta Darah atau rute quest, retak dan memudar 20 tick.
 * <p>
 * {@link #nilai()}: denyut 0..1. Never saved; re-made from its anchor or titik. Behaviour (spline, schedule,
 * clicks) by 38TK-I ({@link MenaraTerlarang}); the renderer (38TV-M) reads {@link #varian()}, {@link #keadaan()}, {@link
 * #mulai()} and {@link #nilai()} only.
 */
public class SegelPintuEntity extends TemaMekanismeEntity {

	/** Segel merah. */
	public static final int VARIAN_BIASA = 0;

	/** Berdenyut. */
	public static final int KEADAAN_UTUH = 0;
	/** Retak 20 tick. */
	public static final int KEADAAN_RETAK = 1;
	/** Lenyap. */
	public static final int KEADAAN_TERBUKA = 2;

	public SegelPintuEntity(EntityType<? extends SegelPintuEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected boolean padat() {
		return keadaan() != KEADAAN_TERBUKA;
	}

	@Override
	protected boolean bisaDipilih() {
		return true;
	}

	/** A touch of the sealed plate: it burns cold; it only gives way by itself ({@link MenaraTerlarang#nearbySegel}). */
	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp && keadaan() == KEADAAN_UTUH) {
			Msg.actionBar(sp, Component.translatable("kultivator.kota_tema.iblis.segel.tolak").withStyle(ChatFormatting.DARK_PURPLE));
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.PASS;
	}
}
