package dev.kultivator.dunia.tempat.tema.oasis;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * {@code kultivator:timba_sumur} (DESIGN 38.T.9). Timba sumur bertuas (shaduf): tiang, balok berpivot dengan
 * pemberat batu dan ember di tali; balok miring +-25 derajat sinkron klip 89 {@code timba_sumur} NPC yang antre
 * air (38.T.4.7 bab 1).
 * <p>
 * {@link #nilai()}: fase siklus 0..1 (also derivable from {@link #mulai()}: one cycle {@link #SIKLUS} ticks). Never
 * saved; re-made from its anchor {@code tema_oasis/timba}. {@link KuilAir#timbaDekat} switches it to
 * {@link #KEADAAN_TIMBA} in the water hours (05-08, 16-19) and while the rationing queue stands; the bucket splashes
 * at the bottom of each cycle. The renderer (38TV-M) reads {@link #varian()}, {@link #keadaan()}, {@link #mulai()} and
 * {@link #nilai()} only.
 */
public class TimbaSumurEntity extends TemaMekanismeEntity {

	/** Kayu palma. */
	public static final int VARIAN_BIASA = 0;

	/** Diam. */
	public static final int KEADAAN_DIAM = 0;
	/** Siklus timba 60 tick. */
	public static final int KEADAAN_TIMBA = 1;

	/** One lift cycle (clip 89 {@code timba_sumur} loops 60 ticks). */
	public static final int SIKLUS = 60;

	public TimbaSumurEntity(EntityType<? extends TimbaSumurEntity> type, Level level) {
		super(type, level);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level) || keadaan() != KEADAAN_TIMBA) {
			return;
		}
		int age = umurKeadaan(level.getGameTime());
		int fase = age % SIKLUS;
		if (fase % 10 == 0) {
			setNilai(fase / (float) SIKLUS);
		}
		if (fase == SIKLUS / 2) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.5F, 0.9F);
		} else if (fase == 0) {
			level.playSound(null, getX(), getY() + 2.0D, getZ(), SoundEvents.WOOD_STEP, SoundSource.BLOCKS, 0.4F, 0.7F);
		}
	}
}
