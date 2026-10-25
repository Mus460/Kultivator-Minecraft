package dev.kultivator.dunia.kota42.ekonomi;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * {@code kultivator:lapak_hantu} (DESIGN 42.12.2, 42.6.7 "Pasar Hantu"): one stall of the Ghost Market, a straw mat 24 x 16
 * pixels with two small chests and a green lantern hanging from a bent pole; 2 x 1.5 blocks, never saved (re-made by 42C's
 * {@code PasarHantu} at its night location), invulnerable, not solid, pickable (right-click = the stall's trade, 42C).
 * <p>
 * Synced ({@code TemaAnimasi}): {@link #varian()} the goods laid out ({@link #VARIAN_JIMAT} .. {@link #VARIAN_PUSAKA}, the
 * 42V model swaps the props on the mat), {@link #keadaan()} {@link #KEADAAN_BUKA} open, {@link #KEADAAN_MEMUDAR} fading at
 * dawn (the stall dissolves into mist over {@link #MEMUDAR_TICK} ticks, then 42C discards it), {@link #nilai()} the opacity
 * 0..1 the renderer uses for the fade (1 = solid). The green lantern sways and the thin mist is client-only.
 * <p>
 * Behaviour (42C-2): right-click opens the stall ({@code PasarHantu.klik}); once set {@link #KEADAAN_MEMUDAR} the server
 * lowers {@link #nilai()} by {@code 1 / MEMUDAR_TICK} each tick and discards the stall at 0.
 */
public class LapakHantuEntity extends TemaMekanismeEntity {

	/** Talismans and paper charms. */
	public static final int VARIAN_JIMAT = 0;
	/** Old jars and bottles (pills of doubtful origin). */
	public static final int VARIAN_GUCI = 1;
	/** Jade slips and scrolls. */
	public static final int VARIAN_GULUNGAN = 2;
	/** Rusted weapons and relics. */
	public static final int VARIAN_PUSAKA = 3;
	public static final int VARIAN_JUMLAH = 4;

	public static final int KEADAAN_BUKA = 0;
	public static final int KEADAAN_MEMUDAR = 1;

	/** Fade length at dawn (42.6.7: "lenyap bertahap saat fajar"). */
	public static final int MEMUDAR_TICK = 100;

	public LapakHantuEntity(EntityType<? extends LapakHantuEntity> type, Level level) {
		super(type, level);
		setNilai(1.0F);
	}

	@Override
	protected boolean bisaDipilih() {
		return true;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide() || keadaan() != KEADAAN_MEMUDAR) {
			return;
		}
		float next = nilai() - 1.0F / MEMUDAR_TICK;
		if (next <= 0.0F) {
			this.discard();
		} else {
			setNilai(next);
		}
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (this.level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		return player instanceof ServerPlayer sp && PasarHantu.klik(this, sp) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}
}
