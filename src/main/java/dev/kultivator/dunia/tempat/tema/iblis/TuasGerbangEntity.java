package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * {@code kultivator:tuas_gerbang} (DESIGN 38.T.9). Tuas gerbang di rumah bea Gerbang Tengkorak (38.T.7.11 bab 5
 * rute Lurus): ditarik pemain untuk membuka gerbang dari dalam.
 * <p>
 * {@link #nilai()}: tarik 0..1. Never saved; re-made from its anchor or titik. Behaviour (spline, schedule,
 * clicks) by 38TK-I ({@link PenyerbuanLurus#tarik}); the renderer (38TV-M) reads {@link #varian()}, {@link #keadaan()}, {@link
 * #mulai()} and {@link #nilai()} only.
 */
public class TuasGerbangEntity extends TemaMekanismeEntity {

	/** Besi dan tulang. */
	public static final int VARIAN_BIASA = 0;

	/** Terkunci. */
	public static final int KEADAAN_ATAS = 0;
	/** Ditarik. */
	public static final int KEADAAN_BAWAH = 1;

	public TuasGerbangEntity(EntityType<? extends TuasGerbangEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected boolean bisaDipilih() {
		return true;
	}

	/** A pull: {@link PenyerbuanLurus#tarik} decides (only on route Lurus chapter 5 during the siege, past the guards). */
	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp) {
			PenyerbuanLurus.tarik(sp, this);
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.PASS;
	}
}
