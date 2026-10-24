package dev.kultivator.dunia.tempat.tema.oasis;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * {@code kultivator:batu_ukur_air} (DESIGN 38.T.9). Batu Ukur Air Kuil Air (38.T.4.7): tiang ukur bertanda di
 * kolam suci; penanda bergerak naik empat tanda selama adegan pemulihan 4 s.
 * <p>
 * {@link #nilai()}: tinggi air 0..1 (0 = dua tanda turun, 1 = pulih). Never saved; re-made from its anchor
 * {@code tema_oasis/batu_ukur} with the town's lake state ({@link KuilAir#pulih}). {@link #naik} starts the ceremony:
 * state {@link #KEADAAN_NAIK} for {@link #NAIK_TICK} ticks (the client eases the marker from the old height to
 * {@code nilai} over that time from {@link #mulai()}), water chimes and spray, then back to {@link #KEADAAN_DIAM}. The
 * renderer (38TV-M) reads {@link #varian()}, {@link #keadaan()}, {@link #mulai()} and {@link #nilai()} only.
 */
public class BatuUkurAirEntity extends TemaMekanismeEntity {

	/** Batu kapur bertanda. */
	public static final int VARIAN_BIASA = 0;

	/** Diam. */
	public static final int KEADAAN_DIAM = 0;
	/** Penanda naik (adegan). */
	public static final int KEADAAN_NAIK = 1;

	/** Length of the rise (4 s). */
	public static final int NAIK_TICK = 80;

	public BatuUkurAirEntity(EntityType<? extends BatuUkurAirEntity> type, Level level) {
		super(type, level);
	}

	/** Server: the ceremony rise to the restored mark. */
	void naik() {
		setNilai(1.0F);
		setKeadaan(KEADAAN_DIAM);
		setKeadaan(KEADAAN_NAIK);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level) || keadaan() != KEADAAN_NAIK) {
			return;
		}
		int age = umurKeadaan(level.getGameTime());
		if (age % 20 == 0 && age < NAIK_TICK) {
			level.playSound(null, getX(), getY() + 1.0D, getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2F, 0.8F + age / 100.0F);
			level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.4D + age / 40.0D, getZ(), 16, 0.4D, 0.2D, 0.4D, 0.1D);
		}
		if (age >= NAIK_TICK) {
			level.sendParticles(ParticleTypes.GLOW, getX(), getY() + 2.2D, getZ(), 24, 0.5D, 0.6D, 0.5D, 0.02D);
			setKeadaan(KEADAAN_DIAM);
		}
	}
}
