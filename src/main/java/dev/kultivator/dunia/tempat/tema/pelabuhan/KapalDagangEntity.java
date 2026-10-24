package dev.kultivator.dunia.tempat.tema.pelabuhan;

import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * {@code kultivator:kapal_dagang} (DESIGN 38.T.9). Jung dagang terjadwal (22 x 7 blok): lunas, lambung tiga lapis,
 * haluan bermata, buritan tinggi, tiga tiang dengan layar bilah 9 panel, kemudi, jangkar, titian, lentera buritan,
 * 3-6 peti muatan. Siklus 3 hari dunia per tambatan (38.T.3.4): datang 2000-4000 berlayar 90 s sepanjang {@code
 * jalur_kapal}, gulung layar, jangkar turun, tambat (derek dan kuli bongkar; geladak dipijak lewat collider pola
 * {@code KapalDekEntity}), berangkat hari 2 pukul 14000 lalu memudar di 160 blok. Tanpa pemain dalam 128 blok
 * siklus berjalan sebagai data.
 * <p>
 * {@link #nilai()}: sudut kemudi -1..1 (x 12 derajat) saat berbelok. Never saved; re-made from its anchor or
 * titik. Behaviour (spline, schedule, clicks) lands in wave W2 by 38TK-P; the renderer (38TV-M) reads {@link
 * #varian()}, {@link #keadaan()}, {@link #mulai()} and {@link #nilai()} only.
 */
public class KapalDagangEntity extends TemaMekanismeEntity {

	/** Layar tikar kuning-coklat bergaris. */
	public static final int VARIAN_TIKAR = 0;
	/** Layar biru-putih. */
	public static final int VARIAN_JUNG_LAUT = 1;

	/** Terombang-ambing di tambatan, layar tergulung. */
	public static final int KEADAAN_DIAM = 0;
	/** Layar terkembang, miring 3 derajat, buih buritan. */
	public static final int KEADAAN_BERLAYAR = 1;
	/** Panel layar turun bergiliran (panel i mulai t = 4i, 40 tick). */
	public static final int KEADAAN_GULUNG_LAYAR = 2;
	/** Kebalikan gulung. */
	public static final int KEADAAN_KEMBANG_LAYAR = 3;
	/** Jangkar turun 30 tick (rantai garis klien). */
	public static final int KEADAAN_JANGKAR_TURUN = 4;
	/** Jangkar naik 30 tick. */
	public static final int KEADAAN_JANGKAR_NAIK = 5;
	/** Titian turun 20 tick, bongkar muat. */
	public static final int KEADAAN_TITIAN_TURUN = 6;
	/** Berangkat, memudar di 160 blok. */
	public static final int KEADAAN_MEMUDAR = 7;

	public KapalDagangEntity(EntityType<? extends KapalDagangEntity> type, Level level) {
		super(type, level);
	}

	// ================================================================== behaviour (38TK-P, W2)

	/** Half length of the hull along the keel (22 blocks, bow to stern). */
	public static final double SETENGAH_PANJANG = 11.0D;
	/** Half beam of the hull (7 blocks). */
	public static final double SETENGAH_LEBAR = 3.5D;
	/** Deck height over the waterline (the entity's y is the water surface; model deck at 16.5 px). */
	public static final double TINGGI_DEK = 1.05D;

	/** Server: the mooring number this body serves ({@code pelabuhan/tambatan} data {@code nomor}); -1 unbound. */
	int tambatan = -1;

	/** True while moored with the gangway down: the deck is solid and walkable, the hull can be clicked. */
	public boolean tertambat() {
		return keadaan() == KEADAAN_TITIAN_TURUN || keadaan() == KEADAAN_DIAM;
	}

	@Override
	protected boolean padat() {
		return tertambat();
	}

	@Override
	protected boolean bisaDipilih() {
		return tertambat();
	}

	/**
	 * Moored: a deck slab as long and wide as the hull (axis-aligned to the nearest quarter turn of the keel, the
	 * moorings of a town are always quarter turns), from half a block under the water to the deck, so players and the
	 * captain walk on it (the {@code AbstractBoat} collision pattern). Under way: the plain type box (never solid). Read
	 * only synced values (state, yaw), so client and server agree.
	 */
	@Override
	protected AABB makeBoundingBox(Vec3 position) {
		if (!tertambat()) {
			return super.makeBoundingBox(position);
		}
		int quarter = Math.floorMod(Math.round(this.getYRot() / 90.0F), 4);
		boolean alongZ = quarter == 0 || quarter == 2;
		double hx = alongZ ? SETENGAH_LEBAR : SETENGAH_PANJANG;
		double hz = alongZ ? SETENGAH_PANJANG : SETENGAH_LEBAR;
		return new AABB(position.x - hx, position.y - 0.5D, position.z - hz, position.x + hx, position.y + TINGGI_DEK, position.z + hz);
	}

	/** Re-makes the box after a state or yaw change (both sides call it through {@link #setKeadaan}). */
	@Override
	public void setKeadaan(int keadaan) {
		super.setKeadaan(keadaan);
		this.setBoundingBox(this.makeBoundingBox());
	}

	@Override
	public void pindah(Vec3 pos, float yaw) {
		super.pindah(pos, yaw);
		this.setBoundingBox(this.makeBoundingBox());
	}

	@Override
	public void onSyncedDataUpdated(net.minecraft.network.syncher.EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		this.setBoundingBox(this.makeBoundingBox());
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (!tertambat()) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer sp) {
			JadwalKapal.klikKapal(sp, this);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 192.0D * 192.0D;
	}
}
