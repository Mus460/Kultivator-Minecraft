package dev.kultivator.dunia.kota42.acara;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * {@code kultivator:meja_jamuan} (DESIGN 42.12.2, 42.7.7): the round banquet table of a wedding, funeral meal or birth feast,
 * 1.6 x 0.8 blocks: a 24 x 1 x 24 pixel top (four cubes turned 0 / 22.5 / 45 / 67.5 degrees), a pedestal, eight stools and up
 * to six dish props. Used when the house, tavern or plaza has no table; guests are NPC bodies standing or sitting around it
 * (clips {@code makan}, 396 {@code bersulang}, 412 {@code nyanyi_mabuk}). Never saved (42D-P's {@code Upacara}),
 * invulnerable, solid (a table blocks walking).
 * <p>
 * Synced: {@link #varian()} the cloth ({@link #VARIAN_PERNIKAHAN} red with 囍, {@link #VARIAN_DUKA} white, {@link
 * #VARIAN_PESTA} plain), {@link #hidangan()} a 6-bit mask of the dish places filled (bit {@code i} = plate {@code i}; dishes
 * are emptied as the meal goes on), {@link #nilai()} the meal progress 0..1 (cups and bones appear).
 * <p>
 * Behaviour (42D-P): {@link #jamuan(int)} starts a meal of the given length; the server moves {@link #nilai()} from 0 to 1
 * and empties the plates one by one (the last plate stays until the end). A table whose ceremony stopped holding it
 * ({@link #pegang}) is cleared away.
 */
public class MejaJamuanEntity extends TemaMekanismeEntity {

	public static final int VARIAN_PERNIKAHAN = 0;
	public static final int VARIAN_DUKA = 1;
	public static final int VARIAN_PESTA = 2;
	public static final int VARIAN_JUMLAH = 3;

	public static final int PIRING = 6;
	public static final int SEMUA_PIRING = (1 << PIRING) - 1;

	private static final EntityDataAccessor<Integer> DATA_HIDANGAN = SynchedEntityData.defineId(MejaJamuanEntity.class, EntityDataSerializers.INT);

	private long pegangSampai;
	private long jamuanMulai = -1L;
	private int jamuanLama;

	public MejaJamuanEntity(EntityType<? extends MejaJamuanEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_HIDANGAN, SEMUA_PIRING);
	}

	/** Mask of the filled dish places (bits 0-5). */
	public int hidangan() {
		return this.entityData.get(DATA_HIDANGAN);
	}

	public void setHidangan(int mask) {
		this.entityData.set(DATA_HIDANGAN, mask & SEMUA_PIRING);
	}

	/** Server: a meal of {@code ticks} starts now (all six dishes served, progress 0). */
	public void jamuan(int ticks) {
		this.jamuanMulai = this.level().getGameTime();
		this.jamuanLama = Math.max(20, ticks);
		setHidangan(SEMUA_PIRING);
		setNilai(0.0F);
	}

	/** Server: the owning ceremony keeps the table until game time {@code sampai} (renewed while it runs). */
	public void pegang(long sampai) {
		this.pegangSampai = sampai;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		if (pegangSampai > 0L && now > pegangSampai) {
			this.discard();
			return;
		}
		if (jamuanMulai >= 0L && now % 20L == 0L) {
			float f = Math.min(1.0F, (now - jamuanMulai) / (float) jamuanLama);
			setNilai(f);
			int left = Math.max(1, PIRING - (int) (f * PIRING));
			int mask = (1 << left) - 1;
			if (mask != hidangan()) {
				setHidangan(mask);
			}
		}
	}

	@Override
	protected boolean padat() {
		return true;
	}
}
