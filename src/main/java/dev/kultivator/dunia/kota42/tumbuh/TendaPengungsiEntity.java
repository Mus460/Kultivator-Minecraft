package dev.kultivator.dunia.kota42.tumbuh;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * {@code kultivator:tenda_pengungsi} (DESIGN 42.12.2, 42.9.5 "Rusak dan dibangun kembali"): the refugee tent where townsfolk
 * whose house was really destroyed sleep while it is rebuilt, an A-frame of cloth 24 x 14 x 28 pixels with a small
 * lantern at the entrance; 1.6 x 1.8 blocks. Never saved (42F's {@code BangunKembali} pitches tents on the plaza for each
 * homeless household and strikes them when the house is finished), invulnerable, not solid.
 * <p>
 * Synced: {@link #varian()} the cloth ({@link #VARIAN_ABU} grey canvas, {@link #VARIAN_COKLAT} oiled brown,
 * {@link #VARIAN_BIRU} indigo; patched more with the days), {@link #keadaan()} {@link #KEADAAN_SIANG} (flap tied open) or
 * {@link #KEADAAN_MALAM} (flap closed, lantern lit), {@link #nilai()} the lantern brightness 0..1 (the client flickers it).
 */
public class TendaPengungsiEntity extends TemaMekanismeEntity {

	public static final int VARIAN_ABU = 0;
	public static final int VARIAN_COKLAT = 1;
	public static final int VARIAN_BIRU = 2;
	public static final int VARIAN_JUMLAH = 3;

	public static final int KEADAAN_SIANG = 0;
	public static final int KEADAAN_MALAM = 1;

	public TendaPengungsiEntity(EntityType<? extends TendaPengungsiEntity> type, Level level) {
		super(type, level);
	}

	@Override
	public void tick() {
		super.tick();
		// a tent nobody keeps (project over, test spawn, owner forgot it) folds itself away
		if (!this.level().isClientSide() && this.tickCount % 40 == 0 && !BangunKembali.dipegang(this)) {
			this.discard();
		}
	}
}
