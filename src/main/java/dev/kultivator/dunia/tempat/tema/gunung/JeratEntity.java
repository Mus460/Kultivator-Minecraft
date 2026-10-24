package dev.kultivator.dunia.tempat.tema.gunung;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * {@code kultivator:jerat} (DESIGN 38.T.9). Jerat roh Gou Tie di ceruk jalan papan (38.T.6.5 bab 3): cincin
 * bergerigi yang menutup 4 tick; dilepas pemain.
 * <p>
 * {@link #nilai()}: tegang 0..1. Never saved; re-made from its anchor or titik. Behaviour (spline, schedule,
 * clicks) lands in wave W2 by 38TK-G; the renderer (38TV-M) reads {@link #varian()}, {@link #keadaan()}, {@link
 * #mulai()} and {@link #nilai()} only.
 * <p>
 * Behaviour (38TK-G): made by {@link GunungCerita} at the six niches while chain stage 3 runs for a nearby player; a
 * click releases it ({@link #KEADAAN_DILEPAS}, gone after 20 ticks) and counts for that player's stage.
 */
public class JeratEntity extends TemaMekanismeEntity {

	/** Besi roh. */
	public static final int VARIAN_BIASA = 0;

	/** Siap. */
	public static final int KEADAAN_TERBUKA = 0;
	/** Menutup 4 tick. */
	public static final int KEADAAN_MENUTUP = 1;
	/** Dilepas, memudar. */
	public static final int KEADAAN_DILEPAS = 2;

	public JeratEntity(EntityType<? extends JeratEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected boolean bisaDipilih() {
		return keadaan() != KEADAAN_DILEPAS;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND || keadaan() == KEADAAN_DILEPAS) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer sp) {
			GunungCerita.lepasJerat(sp, this);
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide() && keadaan() == KEADAAN_DILEPAS && umurKeadaan(this.level().getGameTime()) > 20) {
			dev.kultivator.dunia.tempat.tema.TemaMekanisme.lepas(this);
		}
	}
}
