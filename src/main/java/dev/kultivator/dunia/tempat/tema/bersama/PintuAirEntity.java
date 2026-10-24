package dev.kultivator.dunia.tempat.tema.bersama;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * {@code kultivator:pintu_air} (DESIGN 38.T.9). Pintu air kanal dan dok kering: daun papan 7 x 4 blok di alur
 * samping dengan rantai katrol; naik-turun 40 tick (05:00 buka, 21:00 tutup) dengan percikan.
 * <p>
 * {@link #nilai()}: angkat 0..1. Never saved; re-made from its anchor or titik. The renderer (38TV-M) reads
 * {@link #varian()}, {@link #keadaan()}, {@link #mulai()} and {@link #nilai()} only.
 * <p>
 * Behaviour (38TK inti): on its own schedule ({@link #setOtomatis}) the gate rises at 05:00 (time of day 23000) and
 * drops at 21:00 (15000), 40 ticks each way; the theme code may force it with {@link #buka()} / {@link #tutup()} (a dock
 * flooding scene, a quest, a festival boat) and switch the schedule back on. While at least half down the leaf is solid
 * (boats and players stop at it). The splash and the chain rattle play only while it moves. No block ever changes.
 */
public class PintuAirEntity extends TemaMekanismeEntity {

	/** Pintu air Kota Air. */
	public static final int VARIAN_KANAL = 0;
	/** Dok kering galangan. */
	public static final int VARIAN_DOK = 1;

	/** Tertutup. */
	public static final int KEADAAN_TERTUTUP = 0;
	/** Naik. */
	public static final int KEADAAN_NAIK = 1;
	/** Terbuka. */
	public static final int KEADAAN_TERBUKA = 2;
	/** Turun. */
	public static final int KEADAAN_TURUN = 3;

	/** Ticks of one rise or drop. */
	public static final int GERAK_TICK = 40;
	/** Time of day the gate opens (05:00) and closes (21:00). */
	public static final int JAM_BUKA = 23000;
	public static final int JAM_TUTUP = 15000;

	private boolean otomatis = true;

	public PintuAirEntity(EntityType<? extends PintuAirEntity> type, Level level) {
		super(type, level);
	}

	/** True: follow the 05:00 / 21:00 schedule (default); false: stay as last forced. */
	public void setOtomatis(boolean otomatis) {
		this.otomatis = otomatis;
	}

	/** Raises the leaf now (schedule off until {@link #setOtomatis} turns it back on). */
	public void buka() {
		otomatis = false;
		gerak(true);
	}

	/** Lowers the leaf now (schedule off until {@link #setOtomatis} turns it back on). */
	public void tutup() {
		otomatis = false;
		gerak(false);
	}

	/** True when fully open. */
	public boolean terbuka() {
		return keadaan() == KEADAAN_TERBUKA;
	}

	private void gerak(boolean naik) {
		int k = keadaan();
		if (naik && (k == KEADAAN_TERTUTUP || k == KEADAAN_TURUN)) {
			setKeadaan(KEADAAN_NAIK);
		} else if (!naik && (k == KEADAAN_TERBUKA || k == KEADAAN_NAIK)) {
			setKeadaan(KEADAAN_TURUN);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		long time = level.getGameTime();
		if (otomatis && time % 20L == 3L) {
			long t = Math.floorMod(level.getOverworldClockTime(), 24000L);
			gerak(t >= JAM_BUKA || t < JAM_TUTUP);
		}
		int k = keadaan();
		if (k != KEADAAN_NAIK && k != KEADAAN_TURUN) {
			setNilai(k == KEADAAN_TERBUKA ? 1.0F : 0.0F);
			return;
		}
		int age = umurKeadaan(time);
		float p = Math.min(1.0F, age / (float) GERAK_TICK);
		setNilai(k == KEADAAN_NAIK ? p : 1.0F - p);
		if (age % 4 == 0) {
			level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.2D, getZ(), 6, 1.6D, 0.1D, 0.3D, 0.05D);
		}
		if (age == 0) {
			level.playSound(null, blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.7F, 0.7F);
		}
		if (age >= GERAK_TICK) {
			setKeadaan(k == KEADAAN_NAIK ? KEADAAN_TERBUKA : KEADAAN_TERTUTUP);
			level.playSound(null, blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.6F, 0.9F);
		}
	}

	@Override
	protected boolean padat() {
		return nilai() < 0.5F;
	}
}
