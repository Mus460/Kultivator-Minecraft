package dev.kultivator.dunia.tempat.cerita;

import dev.kultivator.Kultivator;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dialog and quest effects (DESIGN 38.S.1; owner 38ST). A dialog choice or quest stage lists effects as
 * {@code "<id> <arg> <arg>..."}; each id runs a registered {@link Pelaksana}. 38ST registers the built-in ones
 * ({@code bendera}, {@code nama_baik}, {@code beri}, {@code ambil}, {@code bayar}, {@code quest_mulai},
 * {@code quest_maju}, {@code buka_layanan}, {@code mulai_duel}, {@code klip}, {@code adegan}, {@code kabar}); other
 * modules register theirs (38TK: {@code tambah_infami}, {@code tebus}, ...; 38.T.14.3). Unknown ids do nothing and
 * are logged once. Effects never change blocks (38.0 rule 1).
 */
public final class Akibat {

	/** Runs one effect for {@code player}; {@code args} are the words after the id. */
	@FunctionalInterface
	public interface Pelaksana {
		void jalankan(ServerPlayer player, List<String> args);
	}

	private static final Map<String, Pelaksana> PELAKSANA = new ConcurrentHashMap<>();
	private static final Map<String, Boolean> DILAPOR = new ConcurrentHashMap<>();

	/** Registers effect {@code id} (a second registration replaces the first with a warning). */
	public static void daftar(String id, Pelaksana pelaksana) {
		if (PELAKSANA.put(id, pelaksana) != null) {
			Kultivator.LOGGER.warn("Story effect {} registered twice; the later one wins", id);
		}
	}

	public static boolean terdaftar(String id) {
		return PELAKSANA.containsKey(id);
	}

	/** Runs effect {@code id}; false when unknown or it failed. */
	public static boolean jalankan(ServerPlayer player, String id, List<String> args) {
		Pelaksana p = PELAKSANA.get(id);
		if (p == null) {
			if (DILAPOR.putIfAbsent(id, Boolean.TRUE) == null) {
				Kultivator.LOGGER.warn("Unknown story effect {}", id);
			}
			return false;
		}
		try {
			p.jalankan(player, args);
			return true;
		} catch (RuntimeException e) {
			Kultivator.LOGGER.warn("Story effect {} failed", id, e);
			return false;
		}
	}

	/** Runs every line of {@code baris} in order. */
	public static void semua(ServerPlayer player, List<String> baris) {
		for (String line : baris) {
			jalankan(player, line);
		}
	}

	/** Runs a whole effect line {@code "<id> <args...>"}. */
	public static boolean jalankan(ServerPlayer player, String baris) {
		List<String> words = List.of(baris.trim().split("\\s+"));
		if (words.isEmpty() || words.get(0).isEmpty()) {
			return false;
		}
		return jalankan(player, words.get(0), words.subList(1, words.size()));
	}

	private Akibat() {
	}
}
