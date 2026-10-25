package dev.kultivator.dunia.tempat.tema.bersama;

import dev.kultivator.sosok.SosokApi;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
 * <p>
 * DESIGN 42.7.7 (weddings, 42D): {@link #warna()} is a synced cloth colour over the variant; 0 keeps the variant's own
 * colours, {@link #WARNA_PENGANTIN} is the red bridal sedan chair. The renderer tints curtains and roof cloth with it.
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

	/** {@link #warna()}: no tint, the variant's own colours. */
	public static final int WARNA_ASLI = 0;
	/** {@link #warna()}: the red of a bridal sedan chair (42.7.7). */
	public static final int WARNA_PENGANTIN = 0xB3121B;

	private static final EntityDataAccessor<Integer> DATA_WARNA = SynchedEntityData.defineId(TanduEntity.class, EntityDataSerializers.INT);

	/** Default procession pace (blocks per tick; a slow solemn walk). */
	public static final double LANGKAH = 0.06D;

	private final List<Vec3> jalur = new ArrayList<>();
	private double[] panjang = new double[0];
	private double langkah = LANGKAH;
	private boolean ulang;

	public TanduEntity(EntityType<? extends TanduEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_WARNA, WARNA_ASLI);
	}

	/** The cloth tint 0xRRGGBB, or {@link #WARNA_ASLI} (0) for the variant's own colours. */
	public int warna() {
		return this.entityData.get(DATA_WARNA);
	}

	public void setWarna(int rgb) {
		this.entityData.set(DATA_WARNA, rgb & 0xFFFFFF);
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

	/** Makes this the red bridal sedan chair of a wedding (42.7.7): the cloth turns {@link #WARNA_PENGANTIN}. */
	public void jadikanPengantin() {
		setWarna(WARNA_PENGANTIN);
	}

	/** True when it is the red bridal chair. */
	public boolean pengantin() {
		return warna() == WARNA_PENGANTIN;
	}

	/** True when a procession reached its last node and the chair was set down (never for a looping one). */
	public boolean sampai() {
		return !jalur.isEmpty() && !ulang && keadaan() == KEADAAN_DIAM;
	}

	/**
	 * Where bearer {@code i} walks: 0 front-left, 1 front-right, 2 back-left, 3 back-right, at the pole ends 2.6 blocks
	 * ahead of and behind the cabin and 0.7 blocks to the side (the {@code HariDewiLaut} spacing); 4 and more stand in a
	 * second pair further out (eight bearers of a heavy chair).
	 */
	public Vec3 titikPemikul(int i) {
		double r = Math.toRadians(getYRot());
		Vec3 maju = new Vec3(-Math.sin(r), 0.0D, Math.cos(r));
		Vec3 sisi = new Vec3(Math.cos(r), 0.0D, Math.sin(r));
		int k = Math.floorMod(i, 4);
		double jauh = i >= 4 ? 3.4D : 2.6D;
		double f = k < 2 ? jauh : -jauh;
		double s = k % 2 == 0 ? 0.7D : -0.7D;
		return position().add(maju.scale(f)).add(sisi.scale(s));
	}

	/**
	 * Keeps the bearers at their poles (server, every tick while carried is smooth; every few ticks is enough) and in
	 * step: each plays the looping clip {@code klip} from the procession's start so every shoulder rises together.
	 */
	public void bawaPemikul(List<? extends LivingEntity> pemikul, int klip) {
		if (isRemoved()) {
			return;
		}
		for (int i = 0; i < pemikul.size(); i++) {
			LivingEntity e = pemikul.get(i);
			if (e == null || e.isRemoved()) {
				continue;
			}
			Vec3 at = titikPemikul(i);
			e.teleportTo(at.x, at.y, at.z);
			e.setYRot(getYRot());
			e.setYHeadRot(getYRot());
			if (dipikul()) {
				if (SosokApi.gerakSekarang(e) != klip) {
					SosokApi.gerakSinkron(e, klip, mulai());
				}
			} else if (SosokApi.gerakSekarang(e) == klip) {
				SosokApi.henti(e);
			}
		}
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
