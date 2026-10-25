package dev.kultivator.dunia.kota42.acara;

import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import dev.kultivator.sosok.SosokApi;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code kultivator:barongsai} (DESIGN 42.12.2, 42.7.1, 42.7.6): the lion dance costume of the New Year and of travelling
 * troupes, 1.2 x 1.6 blocks (2.4 long in the model): a 12 x 10 x 14 head with a hinged jaw, two-frame eyelids, horn, forehead
 * mirror and quad fur, a five-segment cloth body and a fur tail; the two dancers inside are NPC bodies on clips 378/379
 * ({@code KotaGerakId.BARONGSAI_KEPALA/EKOR}) whose legs show under the cloth. Never saved (42D's {@code Rombongan} and
 * {@code MusimKota} make it), invulnerable, not solid.
 * <p>
 * Synced: {@link #varian()} the colours ({@link #VARIAN_MERAH_EMAS}, {@link #VARIAN_HIJAU}, {@link #VARIAN_HITAM}),
 * {@link #keadaan()} one of the eight movements of 42.12.4 or {@link #KEADAAN_JALAN} (a dance step every
 * {@link #LANGKAH_TICK} ticks) with {@link #mulai()} its start tick, {@link #nilai()} the jaw opening 0..1 (the head
 * snaps it on the drum beat), {@link #kedip()} the blink frame 0-1. The clapping drums and cymbals are 42V sound.
 * <p>
 * Behaviour (42D): {@link #arak} sends the lion along a street loop (a parade); every {@link #LANGKAH_TICK} ticks it may
 * stop for a flourish (bow three times, sniff the crowd, shake the head, rear up) and walks on; {@link #pentas} keeps it
 * on a stage cycling the whole repertoire (sleep, wake, scratch, sniff, eat the lettuce). The jaw snaps on every drum beat
 * ({@link #KETUK_TICK}), the eyes blink at random. {@link #bawaPenari} keeps the two dancers inside the costume, in step.
 */
public class BarongsaiEntity extends TemaMekanismeEntity {

	public static final int VARIAN_MERAH_EMAS = 0;
	public static final int VARIAN_HIJAU = 1;
	public static final int VARIAN_HITAM = 2;
	public static final int VARIAN_JUMLAH = 3;

	/** Walking with the troupe (dance step every {@link #LANGKAH_TICK} ticks). */
	public static final int KEADAAN_JALAN = 0;
	/** Greeting: the head bows three times. */
	public static final int KEADAAN_MENYAPA = 1;
	/** Sniffing the ground and the crowd. */
	public static final int KEADAAN_MENGENDUS = 2;
	/** Scratching behind the ear with a front leg. */
	public static final int KEADAAN_MENGGARUK = 3;
	/** Lying down asleep, eyelids closed. */
	public static final int KEADAAN_TIDUR = 4;
	/** Waking up, shaking the fur. */
	public static final int KEADAAN_BANGUN = 5;
	/** Rearing: front legs up (the head dancer lifted). */
	public static final int KEADAAN_MELOMPAT = 6;
	/** Eating the lettuce (cai qing) and spitting the leaves. */
	public static final int KEADAAN_MAKAN_SELADA = 7;
	/** Shaking the head. */
	public static final int KEADAAN_MENGGELENG = 8;

	public static final int LANGKAH_TICK = 40;
	/** One drum beat (the jaw snaps). */
	public static final int KETUK_TICK = 10;
	/** Parade pace in blocks per tick (a dancing walk). */
	public static final double LAJU = 0.045D;

	/** The stage repertoire in order, each {@link #LANGKAH_TICK} x the count. */
	private static final int[][] REPERTOAR = {
			{KEADAAN_TIDUR, 3}, {KEADAAN_BANGUN, 1}, {KEADAAN_MENGGARUK, 2}, {KEADAAN_MENYAPA, 2}, {KEADAAN_MENGENDUS, 2},
			{KEADAAN_JALAN, 2}, {KEADAAN_MELOMPAT, 1}, {KEADAAN_MENGGELENG, 2}, {KEADAAN_MAKAN_SELADA, 3}, {KEADAAN_MENYAPA, 2}};
	/** The flourishes of a parade. */
	private static final int[] HIASAN = {KEADAAN_MENYAPA, KEADAAN_MENGGELENG, KEADAAN_MENGENDUS, KEADAAN_MELOMPAT};

	private static final EntityDataAccessor<Integer> DATA_KEDIP = SynchedEntityData.defineId(BarongsaiEntity.class, EntityDataSerializers.INT);

	private final List<Vec3> jalur = new ArrayList<>();
	private double[] panjang = new double[0];
	private double jarak;
	private boolean ulang;
	private boolean diPanggung;
	private int jedaHiasan;
	private int kedipSisa;

	public BarongsaiEntity(EntityType<? extends BarongsaiEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_KEDIP, 0);
	}

	/** Eyelid frame: 0 open, 1 closed. */
	public int kedip() {
		return this.entityData.get(DATA_KEDIP);
	}

	public void setKedip(int frame) {
		int v = frame == 0 ? 0 : 1;
		if (kedip() != v) {
			this.entityData.set(DATA_KEDIP, v);
		}
	}

	// ================================================================== behaviour (server)

	/** Starts a parade along {@code titik} (at least two nodes); {@code ulang} loops back to the first node. */
	public void arak(List<Vec3> titik, boolean ulang) {
		jalur.clear();
		diPanggung = false;
		jarak = 0.0D;
		if (titik == null || titik.size() < 2) {
			return;
		}
		jalur.addAll(titik);
		if (ulang) {
			jalur.add(titik.getFirst());
		}
		panjang = new double[jalur.size()];
		for (int i = 1; i < jalur.size(); i++) {
			panjang[i] = panjang[i - 1] + jalur.get(i - 1).distanceTo(jalur.get(i));
		}
		this.ulang = ulang;
		jedaHiasan = 4;
		setKeadaan(KEADAAN_JALAN);
		Vec3 first = jalur.getFirst();
		pindah(first, arah(first, jalur.get(1)));
	}

	/** Dances the stage repertoire in place facing {@code yaw} (a troupe show). */
	public void pentas(Vec3 at, float yaw) {
		jalur.clear();
		diPanggung = true;
		pindah(at, yaw);
		setKeadaan(KEADAAN_TIDUR);
	}

	/** True while it walks a parade route that is not finished. */
	public boolean berarak() {
		return !jalur.isEmpty() && (ulang || jarak < panjang[panjang.length - 1]);
	}

	/** The ground position it reached (parade watchers gather there). */
	public Vec3 posisi() {
		return position();
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		// eyes: a blink every few seconds, closed while asleep
		if (keadaan() == KEADAAN_TIDUR) {
			setKedip(1);
		} else if (kedipSisa > 0) {
			kedipSisa--;
			setKedip(kedipSisa > 0 ? 1 : 0);
		} else if (getRandom().nextInt(70) == 0) {
			kedipSisa = 3;
			setKedip(1);
		}
		// jaw: snaps open on the beat and closes over the next ticks
		if (keadaan() != KEADAAN_TIDUR && now % KETUK_TICK == 0L) {
			setNilai(keadaan() == KEADAAN_MAKAN_SELADA || keadaan() == KEADAAN_MENYAPA ? 1.0F : 0.75F);
			if (now % (KETUK_TICK * 4L) == 0L) {
				level.playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.AMBIENT, 1.1F, 0.7F);
			} else {
				level.playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_SNARE.value(), SoundSource.AMBIENT, 0.5F, 1.4F);
			}
		} else {
			setNilai(Math.max(0.0F, nilai() - 0.12F));
		}
		if (diPanggung) {
			tickPanggung(now);
		} else if (!jalur.isEmpty()) {
			tickArak(now);
		}
	}

	private void tickPanggung(long now) {
		int total = 0;
		for (int[] step : REPERTOAR) {
			total += step[1];
		}
		int beat = (int) (Math.floorMod(now, (long) total * LANGKAH_TICK) / LANGKAH_TICK);
		for (int[] step : REPERTOAR) {
			if (beat < step[1]) {
				setKeadaan(step[0]);
				return;
			}
			beat -= step[1];
		}
	}

	private void tickArak(long now) {
		int umur = (int) (now - mulai());
		if (keadaan() != KEADAAN_JALAN) {
			// a flourish lasts one or two dance steps, then the lion walks on
			int lama = keadaan() == KEADAAN_MENYAPA ? LANGKAH_TICK * 2 : LANGKAH_TICK;
			if (umur >= lama) {
				setKeadaan(KEADAAN_JALAN);
			}
			return;
		}
		if (umur > 0 && umur % LANGKAH_TICK == 0 && --jedaHiasan <= 0) {
			jedaHiasan = 3 + getRandom().nextInt(4);
			setKeadaan(HIASAN[getRandom().nextInt(HIASAN.length)]);
			return;
		}
		double total = panjang[panjang.length - 1];
		jarak += LAJU;
		if (jarak >= total) {
			if (ulang && total > 0.0D) {
				jarak -= total;
			} else {
				jarak = total;
				setKeadaan(KEADAAN_MAKAN_SELADA);
				jalur.clear();
				return;
			}
		}
		int seg = 1;
		while (seg < panjang.length - 1 && panjang[seg] < jarak) {
			seg++;
		}
		Vec3 a = jalur.get(seg - 1);
		Vec3 b = jalur.get(seg);
		double len = Math.max(1.0E-4D, panjang[seg] - panjang[seg - 1]);
		double f = Mth.clamp((jarak - panjang[seg - 1]) / len, 0.0D, 1.0D);
		// a little sway left and right like two dancers stepping
		double r = Math.toRadians(getYRot());
		double sway = Math.sin(now * 0.15D) * 0.18D;
		Vec3 at = a.lerp(b, f).add(Math.cos(r) * sway, 0.0D, Math.sin(r) * sway);
		pindah(at, Mth.approachDegrees(getYRot(), arah(a, b), 5.0F));
	}

	/**
	 * Keeps the head dancer (0.6 blocks forward) and the tail dancer (0.9 back) inside the costume, facing its way, on
	 * clips {@code BARONGSAI_KEPALA/EKOR} in step from this movement's start; either may be null.
	 */
	public void bawaPenari(@Nullable LivingEntity kepala, @Nullable LivingEntity ekor) {
		double r = Math.toRadians(getYRot());
		Vec3 maju = new Vec3(-Math.sin(r), 0.0D, Math.cos(r));
		taruh(kepala, position().add(maju.scale(0.6D)), KotaGerakId.BARONGSAI_KEPALA);
		taruh(ekor, position().add(maju.scale(-0.9D)), KotaGerakId.BARONGSAI_EKOR);
	}

	private void taruh(@Nullable LivingEntity e, Vec3 at, int klip) {
		if (e == null || e.isRemoved()) {
			return;
		}
		e.teleportTo(at.x, at.y, at.z);
		e.setYRot(getYRot());
		e.setYHeadRot(getYRot());
		if (SosokApi.gerakSekarang(e) != klip) {
			SosokApi.gerakSinkron(e, klip, mulai());
		}
	}

	private static float arah(Vec3 from, Vec3 to) {
		return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90.0F;
	}
}
