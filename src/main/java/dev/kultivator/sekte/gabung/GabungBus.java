package dev.kultivator.sekte.gabung;

import dev.kultivator.Kultivator;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.api.StatusKeluar;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The event bus of Gabung Sekte (DESIGN 41.14.3, contract C41.A2). Its own bus, not {@code SekteBus.RIVAL_SECT_ACTION},
 * so Diplomasi never receives a false action (41.4.7). Each event is fired only by the module named in its comment, on
 * the server thread; anyone may listen. A listener that throws is logged and skipped; listeners must be cheap.
 */
public final class GabungBus {

	/** 41A ({@code GabungApi.terima}): the player became a member of {@code sekte} with {@code pangkat}. */
	@FunctionalInterface
	public interface Diterima {
		void onDiterima(ServerPlayer player, String sekte, Pangkat pangkat);
	}

	/** 41A/41D: the member's rank changed (promotion, seat won, demotion: {@code baru} lower than {@code lama}). */
	@FunctionalInterface
	public interface PangkatNaik {
		void onPangkatNaik(ServerPlayer player, String sekte, Pangkat lama, Pangkat baru);
	}

	/** 41A ({@code GabungApi.keluar}): the member left {@code sekte} as {@code status}. */
	@FunctionalInterface
	public interface KeluarSekte {
		void onKeluar(ServerPlayer player, String sekte, StatusKeluar status);
	}

	/** 41A ({@code GabungApi.tambahPk/bayarPk}): the spendable PK changed by {@code delta} for {@code sebab}. */
	@FunctionalInterface
	public interface PkBerubah {
		void onPkBerubah(ServerPlayer player, String sekte, int lama, int baru, String sebab);
	}

	/** 41D ({@code DiplomasiGabung}): the sect's class 1-5 changed (tier crossed a class boundary). */
	@FunctionalInterface
	public interface KelasBerubah {
		void onKelasBerubah(MinecraftServer server, String sekte, int lama, int baru);
	}

	/** 41D: an NPC war of {@code sekte} against {@code lawan} began (the War Call follows). */
	@FunctionalInterface
	public interface PerangMulai {
		void onPerangMulai(MinecraftServer server, String sekte, String lawan);
	}

	/** 41D: the NPC war ended; {@code menang} from {@code sekte}'s side. */
	@FunctionalInterface
	public interface PerangAkhir {
		void onPerangAkhir(MinecraftServer server, String sekte, String lawan, boolean menang);
	}

	/** 41D ({@code Suksesi}, war defeat): a new master; a player ({@code pemain}) or a tokoh ({@code tokoh}, "" for a player). */
	@FunctionalInterface
	public interface KetuaBaru {
		void onKetuaBaru(MinecraftServer server, String sekte, @Nullable UUID pemain, String tokoh);
	}

	public static final Event<Diterima> DITERIMA = EventFactory.createArrayBacked(Diterima.class, listeners ->
			(player, sekte, pangkat) -> {
				for (Diterima l : listeners) {
					try {
						l.onDiterima(player, sekte, pangkat);
					} catch (RuntimeException exception) {
						failed("DITERIMA", exception);
					}
				}
			});

	public static final Event<PangkatNaik> PANGKAT_NAIK = EventFactory.createArrayBacked(PangkatNaik.class, listeners ->
			(player, sekte, lama, baru) -> {
				for (PangkatNaik l : listeners) {
					try {
						l.onPangkatNaik(player, sekte, lama, baru);
					} catch (RuntimeException exception) {
						failed("PANGKAT_NAIK", exception);
					}
				}
			});

	public static final Event<KeluarSekte> KELUAR = EventFactory.createArrayBacked(KeluarSekte.class, listeners ->
			(player, sekte, status) -> {
				for (KeluarSekte l : listeners) {
					try {
						l.onKeluar(player, sekte, status);
					} catch (RuntimeException exception) {
						failed("KELUAR", exception);
					}
				}
			});

	public static final Event<PkBerubah> PK_BERUBAH = EventFactory.createArrayBacked(PkBerubah.class, listeners ->
			(player, sekte, lama, baru, sebab) -> {
				for (PkBerubah l : listeners) {
					try {
						l.onPkBerubah(player, sekte, lama, baru, sebab);
					} catch (RuntimeException exception) {
						failed("PK_BERUBAH", exception);
					}
				}
			});

	public static final Event<KelasBerubah> KELAS_BERUBAH = EventFactory.createArrayBacked(KelasBerubah.class, listeners ->
			(server, sekte, lama, baru) -> {
				for (KelasBerubah l : listeners) {
					try {
						l.onKelasBerubah(server, sekte, lama, baru);
					} catch (RuntimeException exception) {
						failed("KELAS_BERUBAH", exception);
					}
				}
			});

	public static final Event<PerangMulai> PERANG_MULAI = EventFactory.createArrayBacked(PerangMulai.class, listeners ->
			(server, sekte, lawan) -> {
				for (PerangMulai l : listeners) {
					try {
						l.onPerangMulai(server, sekte, lawan);
					} catch (RuntimeException exception) {
						failed("PERANG_MULAI", exception);
					}
				}
			});

	public static final Event<PerangAkhir> PERANG_AKHIR = EventFactory.createArrayBacked(PerangAkhir.class, listeners ->
			(server, sekte, lawan, menang) -> {
				for (PerangAkhir l : listeners) {
					try {
						l.onPerangAkhir(server, sekte, lawan, menang);
					} catch (RuntimeException exception) {
						failed("PERANG_AKHIR", exception);
					}
				}
			});

	public static final Event<KetuaBaru> KETUA_BARU = EventFactory.createArrayBacked(KetuaBaru.class, listeners ->
			(server, sekte, pemain, tokoh) -> {
				for (KetuaBaru l : listeners) {
					try {
						l.onKetuaBaru(server, sekte, pemain, tokoh);
					} catch (RuntimeException exception) {
						failed("KETUA_BARU", exception);
					}
				}
			});

	private static void failed(String event, RuntimeException exception) {
		Kultivator.LOGGER.error("GabungBus {} listener failed", event, exception);
	}

	private GabungBus() {
	}
}
