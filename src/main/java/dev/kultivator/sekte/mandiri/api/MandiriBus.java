package dev.kultivator.sekte.mandiri.api;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Rank;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The event bus of Murid Mandiri (DESIGN 39.10.2, contract C39.A2; 39.F.9 adds the three feud events). The
 * {@code SekteBus} pattern: each event is fired only by the module named in its comment, on the server thread, through
 * its {@code fire*} helper; anyone may listen (37G's cloud ferry hears {@link #JALAN_BERANGKAT}, 38KS hears
 * {@link #KOTA_PERISTIWA}). A listener that throws is logged and skipped. Listeners must be cheap; heavier work is queued
 * on the listener's own spread tick.
 */
public final class MandiriBus {

	/** 39A: a disciple's life path changed ({@code dari} null at the first choice). */
	@FunctionalInterface
	public interface JalanGanti {
		void onJalanGanti(MinecraftServer server, UUID owner, UUID soul, @Nullable JalanHidup dari, JalanHidup ke);
	}

	/** 39A: a goal ended, reached ({@code tercapai}) or given up. */
	@FunctionalInterface
	public interface CitaSelesai {
		void onCitaSelesai(MinecraftServer server, UUID owner, UUID soul, JenisCita jenis, boolean tercapai);
	}

	/** 39C: a journey left the gate (the party's souls, leader first). */
	@FunctionalInterface
	public interface JalanBerangkat {
		void onJalanBerangkat(MinecraftServer server, UUID owner, UUID soul, JenisJalan jenis, TujuanProvider.Tujuan tujuan,
				List<UUID> rombongan);
	}

	/** 39C: a journey's result was applied at the gate (or as data when the hall is unloaded). */
	@FunctionalInterface
	public interface JalanTiba {
		void onJalanTiba(MinecraftServer server, UUID owner, UUID soul, HasilJalan hasil);
	}

	/** 39C: a disciple bought {@code n} of {@code itemId} for {@code harga} NB in total in {@code town} (packed centre). */
	@FunctionalInterface
	public interface Belanja {
		void onBelanja(MinecraftServer server, UUID owner, UUID soul, long town, String itemId, int n, long harga);
	}

	/** 39B: a breakthrough started by the disciple itself ended. */
	@FunctionalInterface
	public interface TerobosanSendiri {
		void onTerobosanSendiri(MinecraftServer server, UUID owner, UUID soul, Rank rankBaru, boolean berhasil);
	}

	/** 39A: temptation crossed a threshold (60 Tergoda, 85 Jalan Iblis) or was redeemed. */
	@FunctionalInterface
	public interface Iblis {
		void onIblis(MinecraftServer server, UUID owner, UUID soul, int sebelum, int sesudah);
	}

	/** 39C: a disciple was captured at {@code situs} (bandit camp, rival gate, slum); a rescue starts. */
	@FunctionalInterface
	public interface Tertawan {
		void onTertawan(MinecraftServer server, UUID owner, UUID soul, String situs);
	}

	/**
	 * 39C: something a disciple did in a town that moves the master's Good Name there ({@code jenis}: {@code copet},
	 * {@code tolong}, {@code razia}, {@code insiden_iblis} ...); C39.K3, 38KS may listen.
	 */
	@FunctionalInterface
	public interface KotaPeristiwa {
		void onKotaPeristiwa(MinecraftServer server, UUID owner, UUID soul, long town, String jenis, int deltaNamaBaik);
	}

	/** 39F: a feud began with the NPC sect {@code sekte} ({@code sebab}: the trigger id of 39.F.2). */
	@FunctionalInterface
	public interface SeteruMulai {
		void onSeteruMulai(MinecraftServer server, UUID owner, String idSeteru, String sekte, String sebab);
	}

	/** 39F: a feud climbed to {@code tingkat} (0-7, T0 Tegang ... T7 Pemicu besar). */
	@FunctionalInterface
	public interface SeteruNaik {
		void onSeteruNaik(MinecraftServer server, UUID owner, String idSeteru, int tingkat);
	}

	/** 39F: a feud ended ({@code status}: {@code DAMAI}, {@code SAINGAN}, {@code DENDAM}, {@code TERLARANG}). */
	@FunctionalInterface
	public interface SeteruAkhir {
		void onSeteruAkhir(MinecraftServer server, UUID owner, String idSeteru, String status);
	}

	// ================================================================== events

	public static final Event<JalanGanti> JALAN_GANTI = EventFactory.createArrayBacked(JalanGanti.class, listeners ->
			(server, owner, soul, dari, ke) -> {
				for (JalanGanti listener : listeners) {
					try {
						listener.onJalanGanti(server, owner, soul, dari, ke);
					} catch (RuntimeException exception) {
						failed("JALAN_GANTI", exception);
					}
				}
			});

	public static final Event<CitaSelesai> CITA_SELESAI = EventFactory.createArrayBacked(CitaSelesai.class, listeners ->
			(server, owner, soul, jenis, tercapai) -> {
				for (CitaSelesai listener : listeners) {
					try {
						listener.onCitaSelesai(server, owner, soul, jenis, tercapai);
					} catch (RuntimeException exception) {
						failed("CITA_SELESAI", exception);
					}
				}
			});

	public static final Event<JalanBerangkat> JALAN_BERANGKAT = EventFactory.createArrayBacked(JalanBerangkat.class, listeners ->
			(server, owner, soul, jenis, tujuan, rombongan) -> {
				for (JalanBerangkat listener : listeners) {
					try {
						listener.onJalanBerangkat(server, owner, soul, jenis, tujuan, rombongan);
					} catch (RuntimeException exception) {
						failed("JALAN_BERANGKAT", exception);
					}
				}
			});

	public static final Event<JalanTiba> JALAN_TIBA = EventFactory.createArrayBacked(JalanTiba.class, listeners ->
			(server, owner, soul, hasil) -> {
				for (JalanTiba listener : listeners) {
					try {
						listener.onJalanTiba(server, owner, soul, hasil);
					} catch (RuntimeException exception) {
						failed("JALAN_TIBA", exception);
					}
				}
			});

	public static final Event<Belanja> BELANJA = EventFactory.createArrayBacked(Belanja.class, listeners ->
			(server, owner, soul, town, itemId, n, harga) -> {
				for (Belanja listener : listeners) {
					try {
						listener.onBelanja(server, owner, soul, town, itemId, n, harga);
					} catch (RuntimeException exception) {
						failed("BELANJA", exception);
					}
				}
			});

	public static final Event<TerobosanSendiri> TEROBOSAN_SENDIRI = EventFactory.createArrayBacked(TerobosanSendiri.class, listeners ->
			(server, owner, soul, rankBaru, berhasil) -> {
				for (TerobosanSendiri listener : listeners) {
					try {
						listener.onTerobosanSendiri(server, owner, soul, rankBaru, berhasil);
					} catch (RuntimeException exception) {
						failed("TEROBOSAN_SENDIRI", exception);
					}
				}
			});

	public static final Event<Iblis> IBLIS = EventFactory.createArrayBacked(Iblis.class, listeners ->
			(server, owner, soul, sebelum, sesudah) -> {
				for (Iblis listener : listeners) {
					try {
						listener.onIblis(server, owner, soul, sebelum, sesudah);
					} catch (RuntimeException exception) {
						failed("IBLIS", exception);
					}
				}
			});

	public static final Event<Tertawan> TERTAWAN = EventFactory.createArrayBacked(Tertawan.class, listeners ->
			(server, owner, soul, situs) -> {
				for (Tertawan listener : listeners) {
					try {
						listener.onTertawan(server, owner, soul, situs);
					} catch (RuntimeException exception) {
						failed("TERTAWAN", exception);
					}
				}
			});

	public static final Event<KotaPeristiwa> KOTA_PERISTIWA = EventFactory.createArrayBacked(KotaPeristiwa.class, listeners ->
			(server, owner, soul, town, jenis, deltaNamaBaik) -> {
				for (KotaPeristiwa listener : listeners) {
					try {
						listener.onKotaPeristiwa(server, owner, soul, town, jenis, deltaNamaBaik);
					} catch (RuntimeException exception) {
						failed("KOTA_PERISTIWA", exception);
					}
				}
			});

	public static final Event<SeteruMulai> SETERU_MULAI = EventFactory.createArrayBacked(SeteruMulai.class, listeners ->
			(server, owner, idSeteru, sekte, sebab) -> {
				for (SeteruMulai listener : listeners) {
					try {
						listener.onSeteruMulai(server, owner, idSeteru, sekte, sebab);
					} catch (RuntimeException exception) {
						failed("SETERU_MULAI", exception);
					}
				}
			});

	public static final Event<SeteruNaik> SETERU_NAIK = EventFactory.createArrayBacked(SeteruNaik.class, listeners ->
			(server, owner, idSeteru, tingkat) -> {
				for (SeteruNaik listener : listeners) {
					try {
						listener.onSeteruNaik(server, owner, idSeteru, tingkat);
					} catch (RuntimeException exception) {
						failed("SETERU_NAIK", exception);
					}
				}
			});

	public static final Event<SeteruAkhir> SETERU_AKHIR = EventFactory.createArrayBacked(SeteruAkhir.class, listeners ->
			(server, owner, idSeteru, status) -> {
				for (SeteruAkhir listener : listeners) {
					try {
						listener.onSeteruAkhir(server, owner, idSeteru, status);
					} catch (RuntimeException exception) {
						failed("SETERU_AKHIR", exception);
					}
				}
			});

	// ================================================================== fire helpers (null-safe)

	public static void jalanGanti(@Nullable MinecraftServer server, @Nullable UUID owner, @Nullable UUID soul,
			@Nullable JalanHidup dari, @Nullable JalanHidup ke) {
		if (server != null && owner != null && soul != null && ke != null && dari != ke) {
			JALAN_GANTI.invoker().onJalanGanti(server, owner, soul, dari, ke);
		}
	}

	public static void citaSelesai(@Nullable MinecraftServer server, @Nullable UUID owner, @Nullable UUID soul,
			@Nullable JenisCita jenis, boolean tercapai) {
		if (server != null && owner != null && soul != null && jenis != null) {
			CITA_SELESAI.invoker().onCitaSelesai(server, owner, soul, jenis, tercapai);
		}
	}

	public static void jalanBerangkat(@Nullable MinecraftServer server, @Nullable UUID owner, @Nullable UUID soul,
			@Nullable JenisJalan jenis, @Nullable TujuanProvider.Tujuan tujuan, @Nullable List<UUID> rombongan) {
		if (server != null && owner != null && soul != null && jenis != null && tujuan != null) {
			JALAN_BERANGKAT.invoker().onJalanBerangkat(server, owner, soul, jenis, tujuan, rombongan == null ? List.of(soul) : List.copyOf(rombongan));
		}
	}

	public static void jalanTiba(@Nullable MinecraftServer server, @Nullable UUID owner, @Nullable UUID soul, @Nullable HasilJalan hasil) {
		if (server != null && owner != null && soul != null && hasil != null) {
			JALAN_TIBA.invoker().onJalanTiba(server, owner, soul, hasil);
		}
	}

	public static void belanja(@Nullable MinecraftServer server, @Nullable UUID owner, @Nullable UUID soul, long town,
			@Nullable String itemId, int n, long harga) {
		if (server != null && owner != null && soul != null && itemId != null && n > 0) {
			BELANJA.invoker().onBelanja(server, owner, soul, town, itemId, n, Math.max(0L, harga));
		}
	}

	public static void terobosanSendiri(@Nullable MinecraftServer server, @Nullable UUID owner, @Nullable UUID soul,
			@Nullable Rank rankBaru, boolean berhasil) {
		if (server != null && owner != null && soul != null && rankBaru != null) {
			TEROBOSAN_SENDIRI.invoker().onTerobosanSendiri(server, owner, soul, rankBaru, berhasil);
		}
	}

	public static void iblis(@Nullable MinecraftServer server, @Nullable UUID owner, @Nullable UUID soul, int sebelum, int sesudah) {
		if (server != null && owner != null && soul != null && sebelum != sesudah) {
			IBLIS.invoker().onIblis(server, owner, soul, clamp(sebelum), clamp(sesudah));
		}
	}

	public static void tertawan(@Nullable MinecraftServer server, @Nullable UUID owner, @Nullable UUID soul, @Nullable String situs) {
		if (server != null && owner != null && soul != null) {
			TERTAWAN.invoker().onTertawan(server, owner, soul, situs == null ? "" : situs);
		}
	}

	public static void kotaPeristiwa(@Nullable MinecraftServer server, @Nullable UUID owner, @Nullable UUID soul, long town,
			@Nullable String jenis, int deltaNamaBaik) {
		if (server != null && owner != null && soul != null && jenis != null) {
			KOTA_PERISTIWA.invoker().onKotaPeristiwa(server, owner, soul, town, jenis, deltaNamaBaik);
		}
	}

	public static void seteruMulai(@Nullable MinecraftServer server, @Nullable UUID owner, @Nullable String idSeteru,
			@Nullable String sekte, @Nullable String sebab) {
		if (server != null && owner != null && idSeteru != null && sekte != null) {
			SETERU_MULAI.invoker().onSeteruMulai(server, owner, idSeteru, sekte, sebab == null ? "" : sebab);
		}
	}

	public static void seteruNaik(@Nullable MinecraftServer server, @Nullable UUID owner, @Nullable String idSeteru, int tingkat) {
		if (server != null && owner != null && idSeteru != null) {
			SETERU_NAIK.invoker().onSeteruNaik(server, owner, idSeteru, Math.max(0, Math.min(7, tingkat)));
		}
	}

	public static void seteruAkhir(@Nullable MinecraftServer server, @Nullable UUID owner, @Nullable String idSeteru,
			@Nullable String status) {
		if (server != null && owner != null && idSeteru != null && status != null) {
			SETERU_AKHIR.invoker().onSeteruAkhir(server, owner, idSeteru, status);
		}
	}

	private static int clamp(int value) {
		return Math.max(0, Math.min(100, value));
	}

	private static void failed(String event, RuntimeException exception) {
		Kultivator.LOGGER.error("A Mandiri {} listener failed", event, exception);
	}

	private MandiriBus() {
	}
}
