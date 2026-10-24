package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota.Harga;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The money loop public face (contract C38.EK1 + 38.T.14.3, owner 38EK). Every 38 purchase and payout goes through
 * here so prices stay on the single rate {@code NilaiBatu.VALUE} {1, 9, 81, 729} (38.0 rule 16): payments and payouts
 * through {@code NilaiBatu.pay/give} (never an Agung stone handed out), prices through {@code Harga.value}, sect ledger
 * lines in {@link BukuKas} (PK and NB always separate columns), town treasuries in {@link KasKota} (filled only by sinks),
 * the guild badge of {@link Lencana}, and the registry of guild mission kinds used by 38TK
 * ({@link #daftarJenisMisi}; a module that runs the objective of its own kind reports it with {@link #misiSelesai}).
 */
public final class EkonomiApi {

	private static final Map<String, JenisMisi> MISI = Collections.synchronizedMap(new LinkedHashMap<>());
	private static volatile @Nullable MinecraftServer server;

	/** The running server (set by {@link Ekonomi} on start, cleared on stop) for the calls without a player. */
	static void setServer(@Nullable MinecraftServer current) {
		server = current;
	}

	/** Takes {@code nb} Nilai Batu from {@code player}; false (nothing taken) when he cannot pay. */
	public static boolean bayar(ServerPlayer player, long nb, String alasan) {
		if (nb <= 0L) {
			return true;
		}
		boolean paid = NilaiBatu.pay(player, nb);
		if (paid && Kultivator.LOGGER.isDebugEnabled()) {
			Kultivator.LOGGER.debug("Economy: {} paid {} NB for {}", player.getScoreboardName(), nb, alasan);
		}
		return paid;
	}

	/** Gives {@code nb} Nilai Batu (never Agung stones, {@code NilaiBatu.give}). */
	public static void beri(ServerPlayer player, long nb, String alasan) {
		if (nb > 0L) {
			NilaiBatu.give(player, nb);
			if (Kultivator.LOGGER.isDebugEnabled()) {
				Kultivator.LOGGER.debug("Economy: {} received {} NB for {}", player.getScoreboardName(), nb, alasan);
			}
		}
	}

	/** The shop value of {@code stack} in Nilai Batu (0 for unsellable); a town weapon uses its own price table. */
	public static long harga(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0L;
		}
		long weapon = SenjataKota.harga(stack);
		return weapon > 0L ? weapon : Harga.value(stack);
	}

	/**
	 * Writes a sect ledger line for hall owner {@code pemilikAula} (38.M.4; {@code pk} = contribution points column, else
	 * the NB column). {@code sumber} is a {@code LembarKunci.BukuKas.SUMBER_*} id; {@code catatan} is a lang key or a plain
	 * note.
	 */
	public static void kas(UUID pemilikAula, String sumber, boolean pk, long jumlah, String catatan) {
		MinecraftServer current = server;
		if (current == null || jumlah == 0L) {
			return;
		}
		String source = sumber == null || sumber.isBlank() ? LembarKunci.BukuKas.SUMBER_MISI : sumber;
		String note = catatan == null ? "" : catatan;
		if (pk) {
			int points = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, jumlah));
			BukuKas.catat(current, pemilikAula, source, points, 0L, note);
		} else {
			BukuKas.catat(current, pemilikAula, source, 0, jumlah, note);
		}
	}

	/** Adds {@code jumlah} to the treasury of town/place {@code situsId} (fees, fines, taxes; 38.M.5). */
	public static void kasKota(long situsId, long jumlah) {
		KasKota.tambah(server, situsId, jumlah);
	}

	/** Guild badge stars of {@code player} (0 none .. 9, 38.M.3). */
	public static int lencana(ServerPlayer player) {
		return Lencana.bintang(player);
	}

	/** Adds guild points (a themed mission of another module paid out by that module; 38.T.10). */
	public static void poinGilda(ServerPlayer player, int poin) {
		if (poin > 0) {
			Lencana.tambah(player, poin);
		}
	}

	/**
	 * A module that runs the objective of its own registered kind (38TK: {@code muatan}, {@code bajak_laut}, ...) reports
	 * that the player reached it; the active mission of that kind becomes ready to hand in at any guild clerk. False when
	 * the player has no such mission running.
	 */
	public static boolean misiSelesai(ServerPlayer player, String jenis) {
		return jenis != null && MISI.containsKey(jenis) && MisiGilda.selesaiDariLuar(player, jenis);
	}

	/** True while the player runs a guild mission of kind {@code jenis} (38TK spawns its foes only then). */
	public static boolean misiAktif(ServerPlayer player, String jenis) {
		for (net.minecraft.nbt.Tag each : MisiGilda.aktif(player)) {
			if (each instanceof net.minecraft.nbt.CompoundTag misi && jenis.equals(misi.getStringOr("jenis", "")) && !misi.getBooleanOr("selesai", false)) {
				return true;
			}
		}
		return false;
	}

	/** Registers a guild mission kind (38TK and others; once per id, a second registration is ignored with a warning). */
	public static void daftarJenisMisi(JenisMisi jenis) {
		if (MISI.putIfAbsent(jenis.id(), jenis) != null) {
			Kultivator.LOGGER.warn("Guild mission kind {} registered twice; the first stays", jenis.id());
		}
	}

	/** Every registered mission kind, in registration order. */
	public static List<JenisMisi> jenisMisi() {
		synchronized (MISI) {
			return List.copyOf(MISI.values());
		}
	}

	/** The registered kind {@code id}, or null. */
	public static @Nullable JenisMisi jenisMisi(String id) {
		return MISI.get(id);
	}

	private EkonomiApi() {
	}
}
