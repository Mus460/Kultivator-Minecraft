package dev.kultivator.dunia.kota42.acara;

import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * {@code kultivator:tenda_pasar} (DESIGN 42.12.2, 42.7.6, 42.9.6): the festival and night-market tent, a 32 x 2 x 32 pixel
 * striped canopy on four poles with a crate and baskets underneath; 2 x 2.4 blocks. Never saved (42D's {@code MusimKota}
 * and {@code DistrikMalam} put tents up on the plaza for a festival or a night market), invulnerable, not solid.
 * <p>
 * Synced: {@link #varian()} what is sold ({@link #VARIAN_LENTERA} .. {@link #VARIAN_PERMAINAN}), {@link #keadaan()}
 * {@link #KEADAAN_BERDIRI} / {@link #KEADAAN_DIPASANG} / {@link #KEADAAN_DIBONGKAR} with {@link #nilai()} the assembly
 * progress 0..1 over {@link #RAKIT_TICK} ticks, {@link #warna()} the stripe colour 0xRRGGBB (festival red, Qixi blue,
 * Mid-Autumn gold, ...). The cloth ripple and hanging lanterns are client-only.
 * <p>
 * Behaviour (42D): the tent raises itself over {@link #RAKIT_TICK} ticks after {@link #pasang}, and folds and leaves
 * after {@link #bongkar}. {@link Pengelola} keeps the wanted tents of every 42D system by key (one place, one purpose,
 * one spot) so the morning market, the festivals and the night market never double a tent.
 */
public class TendaPasarEntity extends TemaMekanismeEntity {

	/** Lanterns (Mid-Autumn, New Year). */
	public static final int VARIAN_LENTERA = 0;
	/** Snacks: dumplings, tanghulu, mooncakes. */
	public static final int VARIAN_JAJANAN = 1;
	/** Toys and kites. */
	public static final int VARIAN_MAINAN = 2;
	/** Red paper: couplets, paper cuts, fortune. */
	public static final int VARIAN_KERTAS_MERAH = 3;
	/** Ring toss and guessing games. */
	public static final int VARIAN_PERMAINAN = 4;
	public static final int VARIAN_JUMLAH = 5;

	public static final int KEADAAN_BERDIRI = 0;
	public static final int KEADAAN_DIPASANG = 1;
	public static final int KEADAAN_DIBONGKAR = 2;

	public static final int RAKIT_TICK = 20;
	/** Default stripe: festival red. */
	public static final int WARNA_BAWAAN = 0xC0262D;

	private static final EntityDataAccessor<Integer> DATA_WARNA = SynchedEntityData.defineId(TendaPasarEntity.class, EntityDataSerializers.INT);

	public TendaPasarEntity(EntityType<? extends TendaPasarEntity> type, Level level) {
		super(type, level);
		setNilai(1.0F);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_WARNA, WARNA_BAWAAN);
	}

	/** The canopy stripe colour 0xRRGGBB. */
	public int warna() {
		return this.entityData.get(DATA_WARNA);
	}

	public void setWarna(int rgb) {
		int v = rgb & 0xFFFFFF;
		if (warna() != v) {
			this.entityData.set(DATA_WARNA, v);
		}
	}

	/** Raises the tent from folded (server). */
	public void pasang() {
		setNilai(0.0F);
		setKeadaan(KEADAAN_DIPASANG);
	}

	/** Folds the tent; it leaves when folded (server). */
	public void bongkar() {
		if (keadaan() != KEADAAN_DIBONGKAR) {
			setKeadaan(KEADAAN_DIBONGKAR);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel)) {
			return;
		}
		float step = 1.0F / RAKIT_TICK;
		switch (keadaan()) {
			case KEADAAN_DIPASANG -> {
				float v = Math.min(1.0F, nilai() + step);
				setNilai(v);
				if (v >= 1.0F) {
					setKeadaan(KEADAAN_BERDIRI);
				}
			}
			case KEADAAN_DIBONGKAR -> {
				float v = Math.max(0.0F, nilai() - step);
				setNilai(v);
				if (v <= 0.0F) {
					discard();
				}
			}
			default -> {
			}
		}
	}

	/**
	 * The tents wanted by the 42D systems this cycle, by key ({@code "<situs>:pagi:2"}): {@link #pastikan} keeps or makes
	 * one, {@link #rapikan} folds every tent whose key was not asked for since the last call. Spawns count toward the
	 * {@code kota42} budget.
	 */
	public static final class Pengelola {
		private final Map<String, TendaPasarEntity> tenda = new HashMap<>();
		private final Set<String> diminta = new java.util.HashSet<>();

		/** Keeps (or raises) the tent {@code kunci} at {@code pos} facing {@code yaw} with a look; null when over budget. */
		public @Nullable TendaPasarEntity pastikan(ServerLevel level, long situs, String kunci, BlockPos pos, float yaw, int varian, int warna) {
			diminta.add(kunci);
			TendaPasarEntity t = tenda.get(kunci);
			if (t == null || t.isRemoved() || t.keadaan() == KEADAAN_DIBONGKAR) {
				if (t != null && !t.isRemoved()) {
					t.discard();
				}
				if (!level.isLoaded(pos) || DuniaBudget.count(KotaAcaraEntities.ANGGARAN) >= DuniaBudget.cap(KotaAcaraEntities.ANGGARAN)) {
					tenda.remove(kunci);
					return null;
				}
				t = KotaAcaraEntities.TENDA_PASAR.create(level, EntitySpawnReason.EVENT);
				if (t == null) {
					return null;
				}
				t.ikat(situs, pos.asLong());
				t.pindah(Vec3.atBottomCenterOf(pos), yaw);
				t.pasang();
				if (!level.addFreshEntity(t)) {
					return null;
				}
				DuniaBudget.track(KotaAcaraEntities.ANGGARAN, t);
				tenda.put(kunci, t);
			}
			t.setVarian(varian);
			t.setWarna(warna);
			return t;
		}

		/** Folds every tent not asked for since the last call and starts a new cycle. */
		public void rapikan() {
			Iterator<Map.Entry<String, TendaPasarEntity>> it = tenda.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<String, TendaPasarEntity> e = it.next();
				TendaPasarEntity t = e.getValue();
				if (t.isRemoved()) {
					it.remove();
				} else if (!diminta.contains(e.getKey())) {
					t.bongkar();
					it.remove();
				}
			}
			diminta.clear();
		}

		/** Removes every tent at once (server stop, reset). */
		public void hapusSemua() {
			for (TendaPasarEntity t : tenda.values()) {
				if (!t.isRemoved()) {
					t.discard();
				}
			}
			tenda.clear();
			diminta.clear();
		}

		/** Tents standing now. */
		public int jumlah() {
			return tenda.size();
		}
	}
}
