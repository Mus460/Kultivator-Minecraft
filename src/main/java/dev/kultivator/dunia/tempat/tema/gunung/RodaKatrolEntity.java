package dev.kultivator.dunia.tempat.tema.gunung;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * {@code kultivator:roda_katrol} (DESIGN 38.T.9). Roda pemandu stasiun dan Tiang Tengah (besar diameter 9 blok,
 * kecil 3): 8 jari, pelek 16 segmen, poros; berputar saat kereta atau katrol bergerak.
 * <p>
 * {@link #nilai()}: laju putar -1..1. Never saved; re-made from its anchor or titik. Behaviour (spline, schedule,
 * clicks) lands in wave W2 by 38TK-G; the renderer (38TV-M) reads {@link #varian()}, {@link #keadaan()}, {@link
 * #mulai()} and {@link #nilai()} only.
 */
public class RodaKatrolEntity extends TemaMekanismeEntity {

	/** Diameter 9 blok. */
	public static final int VARIAN_BESAR = 0;
	/** Diameter 3 blok. */
	public static final int VARIAN_KECIL = 1;

	/** Diam. */
	public static final int KEADAAN_DIAM = 0;
	/** Berputar. */
	public static final int KEADAAN_PUTAR = 1;

	public RodaKatrolEntity(EntityType<? extends RodaKatrolEntity> type, Level level) {
		super(type, level);
	}
}
