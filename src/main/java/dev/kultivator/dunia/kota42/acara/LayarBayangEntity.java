package dev.kultivator.dunia.kota42.acara;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * {@code kultivator:layar_bayang} (DESIGN 42.12.2, 42.9.6 "Teater Bayang"): the shadow-puppet screen, 2 x 1.6 blocks (0.2
 * deep in the model): a 32 x 24 x 2 pixel frame around a glowing 30 x 22 cloth. The puppeteer stands behind it on clip 413
 * ({@code KotaGerakId.DALANG}); the silhouettes are client sprites on the cloth quad that move by the script of
 * {@code data/kultivator/tempat/wayang/<naskah>.json} (position/rotation keys per tick, 42B-C) and the candle flicker is
 * client-only. Never saved (42D's {@code TeaterBayang} at the 19:00 and 21:00 shows), invulnerable, not solid.
 * <p>
 * Synced: {@link #naskah()} the script ({@link #NASKAH_KERA_SAKTI}, {@link #NASKAH_ULAR_PUTIH}, {@link #NASKAH_HUA_MULAN},
 * {@link #NASKAH_HAKIM_BAO}), {@link #keadaan()} {@link #KEADAAN_GELAP} (unlit), {@link #KEADAAN_PENTAS} (playing; the
 * client derives the script time from {@link #mulai()}), {@link #KEADAAN_JEDA} (paused: a stolen puppet in "Malam Wayang"),
 * {@link #nilai()} the lamp brightness 0..1.
 * <p>
 * Behaviour (42D): {@link #pentas} lights the lamp (brightness rises over 2 s with a candle flicker) and stamps the script
 * start, {@link #jeda} dims it to half, {@link #padam} puts it out.
 */
public class LayarBayangEntity extends TemaMekanismeEntity {

	public static final int NASKAH_KERA_SAKTI = 0;
	public static final int NASKAH_ULAR_PUTIH = 1;
	public static final int NASKAH_HUA_MULAN = 2;
	public static final int NASKAH_HAKIM_BAO = 3;
	public static final int NASKAH_JUMLAH = 4;
	/** Script ids as the data files are named ({@code tempat/wayang/<id>.json}). */
	public static final String[] NASKAH_ID = {"kera_sakti", "ular_putih", "hua_mulan", "hakim_bao"};

	public static final int KEADAAN_GELAP = 0;
	public static final int KEADAAN_PENTAS = 1;
	public static final int KEADAAN_JEDA = 2;

	private static final EntityDataAccessor<Integer> DATA_NASKAH = SynchedEntityData.defineId(LayarBayangEntity.class, EntityDataSerializers.INT);

	public LayarBayangEntity(EntityType<? extends LayarBayangEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_NASKAH, NASKAH_KERA_SAKTI);
	}

	/** Starts script {@code naskah} now (server). */
	public void pentas(int naskah) {
		setNaskah(naskah);
		setKeadaan(KEADAAN_GELAP);
		setKeadaan(KEADAAN_PENTAS);
	}

	/** Pauses the play (the lamp at half). */
	public void jeda() {
		setKeadaan(KEADAAN_JEDA);
	}

	/** Puts the lamp out. */
	public void padam() {
		setKeadaan(KEADAAN_GELAP);
	}

	/** Game ticks since the script started (0 when dark). */
	public int waktuNaskah() {
		return keadaan() == KEADAAN_PENTAS ? (int) Math.max(0L, (level().getGameTime() & 0x7fffffffL) - mulai()) : 0;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel)) {
			return;
		}
		float target = switch (keadaan()) {
			case KEADAAN_PENTAS -> 0.92F + getRandom().nextFloat() * 0.08F;
			case KEADAAN_JEDA -> 0.5F;
			default -> 0.0F;
		};
		float v = nilai();
		v += Math.max(-0.05F, Math.min(0.05F, target - v));
		setNilai(v);
	}

	public int naskah() {
		return this.entityData.get(DATA_NASKAH);
	}

	public void setNaskah(int naskah) {
		int v = Math.floorMod(naskah, NASKAH_JUMLAH);
		if (naskah() != v) {
			this.entityData.set(DATA_NASKAH, v);
		}
	}

	/** The data id of the current script. */
	public String naskahId() {
		return NASKAH_ID[Math.floorMod(naskah(), NASKAH_JUMLAH)];
	}
}
