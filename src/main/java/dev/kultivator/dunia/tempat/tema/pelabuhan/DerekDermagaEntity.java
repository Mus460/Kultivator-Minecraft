package dev.kultivator.dunia.tempat.tema.pelabuhan;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * {@code kultivator:derek_dermaga} (DESIGN 38.T.9). Derek injak dermaga: dua kaki A setinggi 8 blok, lengan miring
 * 30 derajat, roda injak 12 jari, kait dan peti. Siklus 200 tick saat kapal tambat: roda berputar, peti naik 3
 * blok, lengan berayun 90 derajat (60 tick), turun.
 * <p>
 * {@link #nilai()}: fase siklus 0..1. Never saved; re-made from its anchor or titik. Behaviour (spline, schedule,
 * clicks) lands in wave W2 by 38TK-P; the renderer (38TV-M) reads {@link #varian()}, {@link #keadaan()}, {@link
 * #mulai()} and {@link #nilai()} only.
 */
public class DerekDermagaEntity extends TemaMekanismeEntity {

	/** Kayu jati. */
	public static final int VARIAN_BIASA = 0;

	/** Diam. */
	public static final int KEADAAN_DIAM = 0;
	/** Siklus bongkar 200 tick. */
	public static final int KEADAAN_BONGKAR = 1;

	public DerekDermagaEntity(EntityType<? extends DerekDermagaEntity> type, Level level) {
		super(type, level);
	}
}
