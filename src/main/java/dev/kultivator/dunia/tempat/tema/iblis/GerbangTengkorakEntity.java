package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * {@code kultivator:gerbang_tengkorak} (DESIGN 38.T.9). Gerbang Tengkorak Kota Iblis: daun ganda besi berelief dan
 * rahang bawah raksasa bergigi. Membuka: rahang turun 25 derajat (24 tick, debu, suara pintu besi pitch 0.6) lalu
 * daun berayun 90 derajat. Penyerbuan Lurus mengepungnya; tidak ada blok yang runtuh.
 * <p>
 * {@link #nilai()}: buka 0..1. Never saved; re-made from its anchor or titik. Behaviour (spline, schedule, clicks)
 * lands in wave W2 by 38TK-I; the renderer (38TV-M) reads {@link #varian()}, {@link #keadaan()}, {@link #mulai()}
 * and {@link #nilai()} only.
 */
public class GerbangTengkorakEntity extends TemaMekanismeEntity {

	/** Besi hitam. */
	public static final int VARIAN_HITAM = 0;
	/** Besi berkarat merah. */
	public static final int VARIAN_MERAH = 1;

	/** Tertutup. */
	public static final int KEADAAN_TERTUTUP = 0;
	/** Rahang lalu daun. */
	public static final int KEADAAN_MEMBUKA = 1;
	/** Terbuka. */
	public static final int KEADAAN_TERBUKA = 2;
	/** Kebalikan membuka. */
	public static final int KEADAAN_MENUTUP = 3;

	public GerbangTengkorakEntity(EntityType<? extends GerbangTengkorakEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected boolean padat() {
		return keadaan() != KEADAAN_TERBUKA;
	}
}
