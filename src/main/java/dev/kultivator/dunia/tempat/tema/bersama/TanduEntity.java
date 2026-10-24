package dev.kultivator.dunia.tempat.tema.bersama;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code kultivator:tandu} (DESIGN 38.T.9). Tandu arak-arakan (Hari Dewi Laut, Tuan Benteng, penguasa): kabin
 * beratap lengkung dengan tirai, dua galah; goyang +-2 derajat sinkron klip 106 {@code pikul_tandu} para pemikul.
 * <p>
 * {@link #nilai()}: goyang 0..1. Never saved; re-made from its anchor or titik. The renderer (38TV-M) reads
 * {@link #varian()}, {@link #keadaan()}, {@link #mulai()} and {@link #nilai()} only.
 * <p>
 * Behaviour (38TK inti): {@link #arak} carries the palanquin along a polyline (titik {@code tandu} / {@code jalur_tangga}
 * nodes of the theme) at a walking pace as a pure function of time since the procession began (no path finding;
 * the client interpolates); it turns smoothly toward the next node, sways while carried and is set down at the last
 * node (or loops). One seat: a VIP NPC or a player may ride. The bearers (NPCs with clip 106) are the theme's.
 */
public class TanduEntity extends TemaMekanismeEntity {

	/** Patung Dewi Laut, merah-emas. */
	public static final int VARIAN_DEWI_LAUT = 0;
	/** Kain oranye oasis. */
	public static final int VARIAN_PASIR = 1;
	/** Hitam-merah iblis. */
	public static final int VARIAN_HITAM = 2;

	/** Diletakkan. */
	public static final int KEADAAN_DIAM = 0;
	/** Dipikul berjalan. */
	public static final int KEADAAN_PIKUL = 1;

	/** Default procession pace (blocks per tick; a slow solemn walk). */
	public static final double LANGKAH = 0.06D;

	private final List<Vec3> jalur = new ArrayList<>();
	private double[] panjang = new double[0];
	private double langkah = LANGKAH;
	private boolean ulang;

	public TanduEntity(EntityType<? extends TanduEntity> type, Level level) {
		super(type, level);
	}

	/**
	 * Starts a procession along {@code titik} (at least two nodes) at {@code blokPerTick} ({@code <= 0}: {@link #LANGKAH});
	 * {@code ulang}: loop back to the first node at the end.
	 */
	public void arak(List<Vec3> titik, double blokPerTick, boolean ulang) {
		jalur.clear();
		if (titik == null || titik.size() < 2) {
			setKeadaan(KEADAAN_DIAM);
			return;
		}
		jalur.addAll(titik);
		panjang = new double[jalur.size()];
		for (int i = 1; i < jalur.size(); i++) {
			panjang[i] = panjang[i - 1] + jalur.get(i - 1).distanceTo(jalur.get(i));
		}
		this.langkah = blokPerTick > 0.0D ? blokPerTick : LANGKAH;
		this.ulang = ulang;
		setKeadaan(KEADAAN_DIAM);
		setKeadaan(KEADAAN_PIKUL);
		Vec3 first = jalur.getFirst();
		pindah(first, arahKe(first, jalur.get(1)));
	}

	/** Sets the palanquin down where it is. */
	public void letakkan() {
		setKeadaan(KEADAAN_DIAM);
	}

	/** True while carried. */
	public boolean dipikul() {
		return keadaan() == KEADAAN_PIKUL && !jalur.isEmpty();
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		if (!dipikul()) {
			setNilai(0.0F);
			return;
		}
		double total = panjang[panjang.length - 1];
		double s = umurKeadaan(level.getGameTime()) * langkah;
		if (s >= total) {
			if (ulang && total > 0.0D) {
				setKeadaan(KEADAAN_DIAM);
				setKeadaan(KEADAAN_PIKUL);
				s = 0.0D;
			} else {
				Vec3 last = jalur.getLast();
				pindah(last, getYRot());
				setKeadaan(KEADAAN_DIAM);
				setNilai(0.0F);
				return;
			}
		}
		int seg = 1;
		while (seg < panjang.length - 1 && panjang[seg] < s) {
			seg++;
		}
		Vec3 a = jalur.get(seg - 1);
		Vec3 b = jalur.get(seg);
		double len = Math.max(1.0E-4D, panjang[seg] - panjang[seg - 1]);
		double f = Mth.clamp((s - panjang[seg - 1]) / len, 0.0D, 1.0D);
		Vec3 at = a.lerp(b, f);
		float want = arahKe(a, b);
		float yaw = Mth.approachDegrees(getYRot(), want, 6.0F);
		pindah(at, yaw);
		setNilai(1.0F);
	}

	private static float arahKe(Vec3 from, Vec3 to) {
		return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90.0F;
	}

	@Override
	protected int kursi() {
		return 1;
	}
}
