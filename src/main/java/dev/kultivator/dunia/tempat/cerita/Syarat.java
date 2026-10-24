package dev.kultivator.dunia.tempat.cerita;

import dev.kultivator.Kultivator;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dialog and quest conditions (DESIGN 38.S.1; owner 38ST). A dialog choice or quest branch lists conditions as
 * {@code "<id> <arg> <arg>..."}; each id is answered by a registered {@link Penilai}. 38ST registers the built-in ones
 * ({@code nama_baik}, {@code ranah}, {@code bendera}, {@code punya}, {@code nb}, {@code quest}, {@code hari},
 * {@code sikap_sekte}, {@code takut}, {@code jam}); other modules register theirs in their own init (38TK:
 * {@code infami}, {@code gentar}, {@code pita_dagang}, ...; 38.T.14.3). An unknown id is false (the choice stays
 * hidden) and logged once.
 */
public final class Syarat {

	/** Answers one condition for {@code player}; {@code args} are the words after the id. */
	@FunctionalInterface
	public interface Penilai {
		boolean cek(ServerPlayer player, List<String> args);
	}

	private static final Map<String, Penilai> PENILAI = new ConcurrentHashMap<>();
	private static final Map<String, Boolean> DILAPOR = new ConcurrentHashMap<>();

	/** Registers condition {@code id} (a second registration replaces the first with a warning). */
	public static void daftar(String id, Penilai penilai) {
		if (PENILAI.put(id, penilai) != null) {
			Kultivator.LOGGER.warn("Story condition {} registered twice; the later one wins", id);
		}
	}

	public static boolean terdaftar(String id) {
		return PENILAI.containsKey(id);
	}

	/** Evaluates condition {@code id} with {@code args}; false for an unknown id or a failing evaluator. */
	public static boolean cek(ServerPlayer player, String id, List<String> args) {
		Penilai p = PENILAI.get(id);
		if (p == null) {
			if (DILAPOR.putIfAbsent(id, Boolean.TRUE) == null) {
				Kultivator.LOGGER.warn("Unknown story condition {}", id);
			}
			return false;
		}
		try {
			return p.cek(player, args);
		} catch (RuntimeException e) {
			Kultivator.LOGGER.warn("Story condition {} failed", id, e);
			return false;
		}
	}

	/** Evaluates a whole condition line {@code "<id> <args...>"}; a leading {@code !} negates it. */
	public static boolean cek(ServerPlayer player, String baris) {
		String line = baris.trim();
		boolean not = line.startsWith("!");
		if (not) {
			line = line.substring(1).trim();
		}
		List<String> words = List.of(line.split("\\s+"));
		if (words.isEmpty() || words.get(0).isEmpty()) {
			return true;
		}
		return not != cek(player, words.get(0), words.subList(1, words.size()));
	}

	/** True when every line of {@code baris} holds (an empty list holds). */
	public static boolean semua(ServerPlayer player, List<String> baris) {
		for (String line : baris) {
			if (!cek(player, line)) {
				return false;
			}
		}
		return true;
	}

	/** The first line of {@code baris} that fails, or null. */
	public static @org.jetbrains.annotations.Nullable String gagal(ServerPlayer player, List<String> baris) {
		for (String line : baris) {
			if (!cek(player, line)) {
				return line;
			}
		}
		return null;
	}

	private Syarat() {
	}
}
