package dev.kultivator.dunia.kota42;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota42.api.JenisUpacara;
import dev.kultivator.dunia.kota42.api.Kategori;
import dev.kultivator.dunia.kota42.warga.Warga;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The event bus of Kota yang Hidup (DESIGN 42.15.3, contract C42.A2). Each event is fired only by the module named in its
 * comment, on the server thread; anyone may listen. A listener that throws is logged and skipped; listeners must be cheap
 * (Hari Warga fires many of them in one tick).
 */
public final class KotaBus {

	/** 42A ({@code SiklusHidup}): a baby was born to {@code ibu} (null for a newcomer child). */
	@FunctionalInterface
	public interface Lahir {
		void onLahir(MinecraftServer server, long situs, Warga bayi, @Nullable Warga ibu);
	}

	/** 42A: two residents married (always one man and one woman, 42.16 K9); the wedding ceremony follows (42D). */
	@FunctionalInterface
	public interface Nikah {
		void onNikah(MinecraftServer server, long situs, Warga pria, Warga wanita);
	}

	/** 42A: a resident died ({@code sebab} "usia", "sakit", "serangan"...); the funeral follows (42D). */
	@FunctionalInterface
	public interface Wafat {
		void onWafat(MinecraftServer server, long situs, Warga warga, String sebab);
	}

	/** 42A ({@code Migrasi}): a resident left {@code asal} for {@code tujuan} ({@code tujuan} 0 = beyond the map) or arrived. */
	@FunctionalInterface
	public interface Pindah {
		void onPindah(MinecraftServer server, Warga warga, long asal, long tujuan);
	}

	/** 42A ({@code Pekerjaan}): a resident's work role changed (role ids; "" = none). */
	@FunctionalInterface
	public interface KerjaBerubah {
		void onKerjaBerubah(MinecraftServer server, long situs, Warga warga, String lama, String baru);
	}

	/** 42K ({@code GosipWarga.lahir}): a new rumour was born in a place. */
	@FunctionalInterface
	public interface GosipLahir {
		void onGosipLahir(MinecraftServer server, long situs, int gosip, String templat);
	}

	/** 42D ({@code Upacara}): a ceremony changed phase ({@code fase} 0 = scheduled, 1.. = steps, -1 = ended). */
	@FunctionalInterface
	public interface Upacara {
		void onUpacara(MinecraftServer server, long situs, JenisUpacara jenis, int id, int fase);
	}

	/** 42C ({@code HargaKota}): a category factor of a place changed by more than 2 %. */
	@FunctionalInterface
	public interface HargaBerubah {
		void onHargaBerubah(MinecraftServer server, long situs, Kategori kategori, float lama, float baru);
	}

	/** 42F ({@code Kavling}): a plot reached a stage ({@code tahap} 0-4; 4 = finished). */
	@FunctionalInterface
	public interface KavlingBerubah {
		void onKavling(MinecraftServer server, long situs, int kavling, int tahap);
	}

	/** 42E ({@code SerbuanKota}): a raid on a town began ({@code mulai}) or ended ({@code menang} from the town's side). */
	@FunctionalInterface
	public interface Serbuan {
		void onSerbuan(MinecraftServer server, long situs, boolean mulai, boolean menang);
	}

	/** 42E ({@code PaktaLindung}): the protector of a town changed (null = none). */
	@FunctionalInterface
	public interface PaktaBerubah {
		void onPaktaBerubah(MinecraftServer server, long situs, @Nullable UUID lama, @Nullable UUID baru);
	}

	/** 42C ({@code RumahKota}): a house was bought, rented, sealed or released ({@code pemilik} null = back to the town). */
	@FunctionalInterface
	public interface RumahBerubah {
		void onRumahBerubah(MinecraftServer server, long situs, int rumah, @Nullable UUID pemilik);
	}

	public static final Event<Lahir> LAHIR = EventFactory.createArrayBacked(Lahir.class, listeners ->
			(server, situs, bayi, ibu) -> {
				for (Lahir l : listeners) {
					try {
						l.onLahir(server, situs, bayi, ibu);
					} catch (RuntimeException exception) {
						failed("LAHIR", exception);
					}
				}
			});

	public static final Event<Nikah> NIKAH = EventFactory.createArrayBacked(Nikah.class, listeners ->
			(server, situs, pria, wanita) -> {
				for (Nikah l : listeners) {
					try {
						l.onNikah(server, situs, pria, wanita);
					} catch (RuntimeException exception) {
						failed("NIKAH", exception);
					}
				}
			});

	public static final Event<Wafat> WAFAT = EventFactory.createArrayBacked(Wafat.class, listeners ->
			(server, situs, warga, sebab) -> {
				for (Wafat l : listeners) {
					try {
						l.onWafat(server, situs, warga, sebab);
					} catch (RuntimeException exception) {
						failed("WAFAT", exception);
					}
				}
			});

	public static final Event<Pindah> PINDAH = EventFactory.createArrayBacked(Pindah.class, listeners ->
			(server, warga, asal, tujuan) -> {
				for (Pindah l : listeners) {
					try {
						l.onPindah(server, warga, asal, tujuan);
					} catch (RuntimeException exception) {
						failed("PINDAH", exception);
					}
				}
			});

	public static final Event<KerjaBerubah> KERJA_BERUBAH = EventFactory.createArrayBacked(KerjaBerubah.class, listeners ->
			(server, situs, warga, lama, baru) -> {
				for (KerjaBerubah l : listeners) {
					try {
						l.onKerjaBerubah(server, situs, warga, lama, baru);
					} catch (RuntimeException exception) {
						failed("KERJA_BERUBAH", exception);
					}
				}
			});

	public static final Event<GosipLahir> GOSIP_LAHIR = EventFactory.createArrayBacked(GosipLahir.class, listeners ->
			(server, situs, gosip, templat) -> {
				for (GosipLahir l : listeners) {
					try {
						l.onGosipLahir(server, situs, gosip, templat);
					} catch (RuntimeException exception) {
						failed("GOSIP_LAHIR", exception);
					}
				}
			});

	public static final Event<Upacara> UPACARA = EventFactory.createArrayBacked(Upacara.class, listeners ->
			(server, situs, jenis, id, fase) -> {
				for (Upacara l : listeners) {
					try {
						l.onUpacara(server, situs, jenis, id, fase);
					} catch (RuntimeException exception) {
						failed("UPACARA", exception);
					}
				}
			});

	public static final Event<HargaBerubah> HARGA_BERUBAH = EventFactory.createArrayBacked(HargaBerubah.class, listeners ->
			(server, situs, kategori, lama, baru) -> {
				for (HargaBerubah l : listeners) {
					try {
						l.onHargaBerubah(server, situs, kategori, lama, baru);
					} catch (RuntimeException exception) {
						failed("HARGA_BERUBAH", exception);
					}
				}
			});

	public static final Event<KavlingBerubah> KAVLING = EventFactory.createArrayBacked(KavlingBerubah.class, listeners ->
			(server, situs, kavling, tahap) -> {
				for (KavlingBerubah l : listeners) {
					try {
						l.onKavling(server, situs, kavling, tahap);
					} catch (RuntimeException exception) {
						failed("KAVLING", exception);
					}
				}
			});

	public static final Event<Serbuan> SERBUAN = EventFactory.createArrayBacked(Serbuan.class, listeners ->
			(server, situs, mulai, menang) -> {
				for (Serbuan l : listeners) {
					try {
						l.onSerbuan(server, situs, mulai, menang);
					} catch (RuntimeException exception) {
						failed("SERBUAN", exception);
					}
				}
			});

	public static final Event<PaktaBerubah> PAKTA_BERUBAH = EventFactory.createArrayBacked(PaktaBerubah.class, listeners ->
			(server, situs, lama, baru) -> {
				for (PaktaBerubah l : listeners) {
					try {
						l.onPaktaBerubah(server, situs, lama, baru);
					} catch (RuntimeException exception) {
						failed("PAKTA_BERUBAH", exception);
					}
				}
			});

	public static final Event<RumahBerubah> RUMAH_BERUBAH = EventFactory.createArrayBacked(RumahBerubah.class, listeners ->
			(server, situs, rumah, pemilik) -> {
				for (RumahBerubah l : listeners) {
					try {
						l.onRumahBerubah(server, situs, rumah, pemilik);
					} catch (RuntimeException exception) {
						failed("RUMAH_BERUBAH", exception);
					}
				}
			});

	private static void failed(String event, RuntimeException exception) {
		Kultivator.LOGGER.error("KotaBus {} listener failed", event, exception);
	}

	private KotaBus() {
	}
}
