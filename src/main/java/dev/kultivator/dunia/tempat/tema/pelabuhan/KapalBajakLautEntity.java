package dev.kultivator.dunia.tempat.tema.pelabuhan;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * {@code kultivator:kapal_bajak_laut} (DESIGN 38.T.9). Jung bajak laut Kapten Hiu Hitam (38.T.3.4 Serbuan Bajak
 * Laut): lambung {@code kapal_dagang}, layar robek ber-alpha hitam-merah, panji hitam, haluan bertaji, tanpa
 * meriam. Berlayar ke Dermaga Agung, titian turun cepat 10 tick, band bajak laut ({@code
 * RogueCultivators.spawnBand}) turun; tidak pernah merusak blok.
 * <p>
 * {@link #nilai()}: sudut kemudi -1..1. Never saved; re-made from its anchor or titik. Behaviour (spline,
 * schedule, clicks) lands in wave W2 by 38TK-P; the renderer (38TV-M) reads {@link #varian()}, {@link #keadaan()},
 * {@link #mulai()} and {@link #nilai()} only.
 */
public class KapalBajakLautEntity extends TemaMekanismeEntity {

	/** Layar hitam-merah robek. */
	public static final int VARIAN_BIASA = 0;

	/** Berlabuh. */
	public static final int KEADAAN_DIAM = 0;
	/** Datang atau pergi. */
	public static final int KEADAAN_BERLAYAR = 1;
	/** Titian turun cepat 10 tick. */
	public static final int KEADAAN_TITIAN_TURUN = 2;
	/** Kabur, memudar. */
	public static final int KEADAAN_MEMUDAR = 3;

	public KapalBajakLautEntity(EntityType<? extends KapalBajakLautEntity> type, Level level) {
		super(type, level);
	}
}
