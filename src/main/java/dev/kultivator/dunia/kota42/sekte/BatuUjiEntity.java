package dev.kultivator.dunia.kota42.sekte;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * {@code kultivator:batu_uji} (DESIGN 42.12.2, 42.8.1 "Kantor Rekrutmen"): the root-testing stone of a sect recruitment
 * office, 0.6 x 0.9 blocks, an 8 x 12 x 8 pixel stone carved with the glyphs of the five elements. An applicant lays both
 * palms on it (clip 409 {@code KotaGerakId.UJI_AKAR}) and it lights up in the colour of the root. Never saved (42E's
 * {@code KantorRekrut} places it on the office titik), invulnerable, solid, pickable (right-click = test day info, 42E).
 * <p>
 * Synced: {@link #keadaan()} {@link #KEADAAN_DIAM} / {@link #KEADAAN_MENYALA} (rising glow from {@link #mulai()}) /
 * {@link #KEADAAN_PADAM}, {@link #akar()} the root grade shown ({@link #AKAR_TIDAK_ADA} grey, {@link #AKAR_RENDAH} green,
 * {@link #AKAR_MENENGAH} blue, {@link #AKAR_TINGGI} purple, {@link #AKAR_LANGIT} gold; colours {@link #WARNA_AKAR}),
 * {@link #unsur()} a 5-bit mask of the element glyphs that light (bit order metal, wood, water, fire, earth), {@link #nilai()}
 * the glow strength 0..1.
 */
public class BatuUjiEntity extends TemaMekanismeEntity {

	public static final int KEADAAN_DIAM = 0;
	public static final int KEADAAN_MENYALA = 1;
	public static final int KEADAAN_PADAM = 2;

	public static final int AKAR_TIDAK_ADA = 0;
	public static final int AKAR_RENDAH = 1;
	public static final int AKAR_MENENGAH = 2;
	public static final int AKAR_TINGGI = 3;
	public static final int AKAR_LANGIT = 4;
	/** Glow colour per root grade (grey, green, blue, purple, gold). */
	public static final int[] WARNA_AKAR = {0x9A9A9A, 0x5FD068, 0x4FA8F0, 0xA45FE0, 0xF0C850};

	public static final int UNSUR_LOGAM = 1;
	public static final int UNSUR_KAYU = 1 << 1;
	public static final int UNSUR_AIR = 1 << 2;
	public static final int UNSUR_API = 1 << 3;
	public static final int UNSUR_TANAH = 1 << 4;
	public static final int UNSUR_SEMUA = 0x1F;

	private static final EntityDataAccessor<Integer> DATA_AKAR = SynchedEntityData.defineId(BatuUjiEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_UNSUR = SynchedEntityData.defineId(BatuUjiEntity.class, EntityDataSerializers.INT);

	public BatuUjiEntity(EntityType<? extends BatuUjiEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_AKAR, AKAR_TIDAK_ADA);
		builder.define(DATA_UNSUR, 0);
	}

	public int akar() {
		return this.entityData.get(DATA_AKAR);
	}

	public int unsur() {
		return this.entityData.get(DATA_UNSUR);
	}

	/** The glow colour of the current root grade. */
	public int warna() {
		return WARNA_AKAR[Math.max(0, Math.min(AKAR_LANGIT, akar()))];
	}

	public void setAkar(int akar) {
		this.entityData.set(DATA_AKAR, Math.max(AKAR_TIDAK_ADA, Math.min(AKAR_LANGIT, akar)));
	}

	public void setUnsur(int mask) {
		this.entityData.set(DATA_UNSUR, mask & UNSUR_SEMUA);
	}

	@Override
	protected boolean padat() {
		return true;
	}

	@Override
	protected boolean bisaDipilih() {
		return true;
	}
}
