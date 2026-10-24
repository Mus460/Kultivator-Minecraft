package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * {@code kultivator:pintu_arena} (DESIGN 38.T.9). Pintu kurungan Arena Darah (empat per arena): jeruji 12 batang
 * dan 4 palang, rantai; naik 32 tick dengan debu, jatuh cepat 8 tick dengan guncang kamera klien kecil.
 * <p>
 * {@link #nilai()}: angkat 0..1. Never saved; re-made from its anchor or titik. Behaviour (spline, schedule,
 * clicks) lands in wave W2 by 38TK-I; the renderer (38TV-M) reads {@link #varian()}, {@link #keadaan()}, {@link
 * #mulai()} and {@link #nilai()} only.
 */
public class PintuArenaEntity extends TemaMekanismeEntity {

	/** Besi hitam. */
	public static final int VARIAN_BIASA = 0;

	/** Tertutup. */
	public static final int KEADAAN_TERTUTUP = 0;
	/** Naik. */
	public static final int KEADAAN_NAIK = 1;
	/** Terbuka. */
	public static final int KEADAAN_TERBUKA = 2;
	/** Jatuh cepat. */
	public static final int KEADAAN_JATUH = 3;

	public PintuArenaEntity(EntityType<? extends PintuArenaEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected boolean padat() {
		return keadaan() == KEADAAN_TERTUTUP || keadaan() == KEADAAN_JATUH;
	}
}
