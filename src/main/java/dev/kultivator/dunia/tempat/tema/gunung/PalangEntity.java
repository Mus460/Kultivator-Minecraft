package dev.kultivator.dunia.tempat.tema.gunung;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * {@code kultivator:palang} (DESIGN 38.T.9). Palang rantai penutup jalur (longsor, Lift Macet, ujung tebing): dua
 * tiang dan rantai melintang; padat saat terpasang.
 * <p>
 * {@link #nilai()}: kendur rantai 0..1. Never saved; re-made from its anchor or titik. Behaviour (spline,
 * schedule, clicks) lands in wave W2 by 38TK-G; the renderer (38TV-M) reads {@link #varian()}, {@link #keadaan()},
 * {@link #mulai()} and {@link #nilai()} only.
 */
public class PalangEntity extends TemaMekanismeEntity {

	/** Rantai besi. */
	public static final int VARIAN_BIASA = 0;

	/** Menutup. */
	public static final int KEADAAN_TERPASANG = 0;
	/** Dibuka. */
	public static final int KEADAAN_TERBUKA = 1;

	public PalangEntity(EntityType<? extends PalangEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected boolean padat() {
		return keadaan() == KEADAAN_TERPASANG;
	}
}
