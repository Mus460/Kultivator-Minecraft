package dev.kultivator.dunia.tempat.tema.pelabuhan;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * {@code kultivator:kapal_galangan} (DESIGN 38.T.9). Kapal di landasan galangan (38.T.3.4 Galangan): pesanan lunas
 * pemain dibangun di depan mata. Tahap 0 lunas, 1 + 12 gading lengkung, 2 + papan separuh, 3 lambung penuh +
 * pangkal tiang, 4 tiang + layar tergulung; tiap tahap baru tampil dengan efek papan baru (skala 0 ke 1, 10 tick).
 * <p>
 * {@link #nilai()}: tahap bangun 0..4. Never saved; re-made from its anchor or titik. Behaviour (spline, schedule,
 * clicks) lands in wave W2 by 38TK-P; the renderer (38TV-M) reads {@link #varian()}, {@link #keadaan()}, {@link
 * #mulai()} and {@link #nilai()} only.
 */
public class KapalGalanganEntity extends TemaMekanismeEntity {

	/** Pesanan perahu awan. */
	public static final int VARIAN_PERAHU_AWAN = 0;
	/** Pesanan kapal sekte. */
	public static final int VARIAN_KAPAL_SEKTE = 1;

	/** Menunggu. */
	public static final int KEADAAN_DIAM = 0;
	/** Efek papan baru 10 tick. */
	public static final int KEADAAN_TAHAP_BARU = 1;

	public KapalGalanganEntity(EntityType<? extends KapalGalanganEntity> type, Level level) {
		super(type, level);
	}
}
