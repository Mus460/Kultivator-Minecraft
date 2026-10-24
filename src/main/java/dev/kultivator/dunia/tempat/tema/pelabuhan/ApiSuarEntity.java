package dev.kultivator.dunia.tempat.tema.pelabuhan;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * {@code kultivator:api_suar} (DESIGN 38.T.9). Lampu mercusuar Pelita Angin: rumah lampu kuningan berangka, atap
 * kerucut tiga tingkat, lensa bercincin yang berputar 3 derajat per tick, sumbu pancar; sinar = 2 quad aditif 4 x
 * 64 blok (klien, 12500-23500 dan saat badai). Lensa retak di awal rantai {@code tema_pelabuhan_pelita}.
 * <p>
 * {@link #nilai()}: kekuatan cahaya 0..1. Never saved; re-made from its anchor or titik. Behaviour (spline,
 * schedule, clicks) lands in wave W2 by 38TK-P; the renderer (38TV-M) reads {@link #varian()}, {@link #keadaan()},
 * {@link #mulai()} and {@link #nilai()} only.
 */
public class ApiSuarEntity extends TemaMekanismeEntity {

	/** Lensa retak, redup (sebelum bab 3). */
	public static final int VARIAN_RETAK = 0;
	/** Lensa baru. */
	public static final int VARIAN_UTUH = 1;
	/** Mutiara Fajar: sinar biru-emas (bab 7). */
	public static final int VARIAN_FAJAR = 2;

	/** Mati. */
	public static final int KEADAAN_PADAM = 0;
	/** Berputar dan bersinar. */
	public static final int KEADAAN_MENYALA = 1;

	public ApiSuarEntity(EntityType<? extends ApiSuarEntity> type, Level level) {
		super(type, level);
	}
}
