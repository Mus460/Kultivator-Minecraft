package dev.kultivator.dunia.kota42.acara;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code kultivator:usungan_peti} (DESIGN 42.12.2, 42.7.7 funeral): the coffin on its carrying poles, a black-lacquered coffin
 * 10 x 10 x 28 pixels with gold ornaments on two 56-pixel poles; box 1 x 1 blocks (3.6 long in the model). Eight bearers
 * walk it to the grave hill on clip 400 ({@code KotaGerakId.PIKUL_USUNGAN}, synced start). Never saved (42D-P's
 * {@code Upacara} moves it with {@code pindah} along the procession path), invulnerable, not solid.
 * <p>
 * Synced: {@link #varian()} {@link #VARIAN_HITAM} or {@link #VARIAN_MERAH} (an elder over 80: the "red joy" funeral),
 * {@link #keadaan()} {@link #KEADAAN_DIAM} (on trestles at the house), {@link #KEADAAN_DIUSUNG} (sways +-1 degree with
 * the steps), {@link #KEADAAN_TENGGELAM} (lowered into the grave over {@link #TENGGELAM_TICK} ticks), {@link #nilai()}
 * the lowering progress 0..1.
 * <p>
 * Behaviour (42D-P): {@link #tenggelamkan()} lowers it; the server sinks the body {@value #TENGGELAM_DALAM} blocks while
 * earth particles rise (no block is dug, 42.0) and removes it at the end. A coffin whose ceremony stopped holding it
 * ({@link #pegang}) is lowered where it stands.
 */
public class UsunganPetiEntity extends TemaMekanismeEntity {

	public static final int VARIAN_HITAM = 0;
	/** "Xi sang": the red coffin cloth of a long life. */
	public static final int VARIAN_MERAH = 1;

	public static final int KEADAAN_DIAM = 0;
	public static final int KEADAAN_DIUSUNG = 1;
	public static final int KEADAAN_TENGGELAM = 2;

	public static final int TENGGELAM_TICK = 40;
	/** How far the body sinks while lowered (blocks). */
	public static final double TENGGELAM_DALAM = 1.2D;

	private long pegangSampai;
	private double dasarY = Double.NaN;

	public UsunganPetiEntity(EntityType<? extends UsunganPetiEntity> type, Level level) {
		super(type, level);
	}

	/** Server: the owning ceremony keeps the coffin until game time {@code sampai} (renewed while it runs). */
	public void pegang(long sampai) {
		this.pegangSampai = sampai;
	}

	/** Server: lowers the coffin into the grave; it is removed when the lowering ends. */
	public void tenggelamkan() {
		if (keadaan() != KEADAAN_TENGGELAM) {
			dasarY = this.getY();
			setKeadaan(KEADAAN_TENGGELAM);
			setNilai(0.0F);
		}
	}

	/** True once the lowering began. */
	public boolean tenggelam() {
		return keadaan() == KEADAAN_TENGGELAM;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		if (keadaan() != KEADAAN_TENGGELAM) {
			if (pegangSampai > 0L && now > pegangSampai) {
				tenggelamkan();
			}
			return;
		}
		float f = Math.min(1.0F, umurKeadaan(now) / (float) TENGGELAM_TICK);
		setNilai(f);
		if (!Double.isNaN(dasarY)) {
			this.setPos(this.getX(), dasarY - TENGGELAM_DALAM * f, this.getZ());
		}
		if (umurKeadaan(now) % 8 == 0) {
			BlockPos below = BlockPos.containing(this.getX(), (Double.isNaN(dasarY) ? this.getY() : dasarY) - 0.5D, this.getZ());
			BlockState ground = level.getBlockState(below);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground.isAir() ? net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState() : ground),
					this.getX(), (Double.isNaN(dasarY) ? this.getY() : dasarY) + 0.1D, this.getZ(), 12, 0.8D, 0.1D, 0.8D, 0.05D);
			level.playSound(null, below, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 0.6F, 0.8F);
		}
		if (f >= 1.0F) {
			this.discard();
		}
	}
}
