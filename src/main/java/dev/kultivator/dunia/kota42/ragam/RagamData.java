package dev.kultivator.dunia.kota42.ragam;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota42.KotaBagianData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SavedData {@code kultivator:kota_ragam} in the overworld (DESIGN 42.13.1), owner 42S (+ 42S-R). Skeleton of 42A0 with the
 * {@link KotaBagianData#bagian(String) bagian} split of {@code jadwal-41-42.md} matrix row 14: every module of the
 * owner group keeps its state in its own section and calls {@link #ubah()}; nobody edits this class for a new field.
 * Sections (42S), per place {@code kota_<situs>}:
 * <ul>
 *   <li>{@link #ZONA}: the zone map summary ({@code titik} count it was built from, {@code daftar} = {@code id:K;...});</li>
 *   <li>{@link #RIWAYAT}: per resident slot {@code w<id>}: {@code d} = the day of {@code h0}, {@code h0..h2} = today,
 *       yesterday, the day before: {@code [samples, segment...]} (each segment an activity signature, at most
 *       {@value #SEGMEN_MAKS}), {@code s} = staff/stationary;</li>
 *   <li>{@link #METRIK}: per day {@code h<hari>} the accumulators of {@link MetrikRagam} (7 days).</li>
 * </ul>
 * Titik reservations live in memory only. 42S-R keeps its own sections ({@code rencana}, ...).
 */
public final class RagamData extends KotaBagianData {

	public static final Codec<RagamData> CODEC = CompoundTag.CODEC.xmap(tag -> baca(new RagamData(), tag), RagamData::tulis);
	public static final SavedDataType<RagamData> TYPE = new SavedDataType<>(Kultivator.id("kota_ragam"), RagamData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** Sub-module key {@code "zona"}. */
	public static final String ZONA = "zona";
	/** Sub-module key {@code "riwayat"}. */
	public static final String RIWAYAT = "riwayat";
	/** Sub-module key {@code "metrik"}. */
	public static final String METRIK = "metrik";

	/** Activity segments kept per resident per day. */
	public static final int SEGMEN_MAKS = 32;
	/** Days of metrics kept per place. */
	public static final int METRIK_HARI = 7;

	public RagamData() {
	}

	/** The world's data (overworld storage), created on first use. */
	public static RagamData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	@Override
	protected CompoundTag tulis() {
		return super.tulis();
	}

	// ================================================================== zona

	/** Stores the summary of a freshly built zone map. */
	static void simpanZona(MinecraftServer server, PetaZona peta) {
		RagamData data = get(server);
		CompoundTag tag = data.situs(ZONA, peta.situs);
		StringBuilder sb = new StringBuilder();
		for (PetaZona.Zona z : peta.semua()) {
			if (!sb.isEmpty()) {
				sb.append(';');
			}
			sb.append(z.id()).append(':').append(z.kapasitas());
		}
		tag.putInt("titik", peta.dariTitik);
		tag.putString("daftar", sb.toString());
		data.ubah();
	}

	/** The stored zone summary of a place: {@code {id, K}} pairs (empty when never built). */
	static List<String[]> ringkasZona(MinecraftServer server, long situs) {
		String all = get(server).situs(ZONA, situs).getStringOr("daftar", "");
		List<String[]> out = new ArrayList<>();
		if (all.isEmpty()) {
			return out;
		}
		for (String one : all.split(";")) {
			int colon = one.lastIndexOf(':');
			if (colon > 0) {
				out.add(new String[]{one.substring(0, colon), one.substring(colon + 1)});
			}
		}
		return out;
	}

	// ================================================================== riwayat

	/**
	 * One sample of resident {@code id} doing activity signature {@code sig} on day {@code hari}: counts the sample and appends
	 * a segment when the activity changed (the days shift when a new day starts).
	 */
	static void sampel(MinecraftServer server, long situs, long hari, int id, int sig, boolean staf) {
		RagamData data = get(server);
		CompoundTag place = data.situs(RIWAYAT, situs);
		String key = "w" + id;
		CompoundTag w = place.getCompoundOrEmpty(key);
		if (!place.contains(key)) {
			place.put(key, w);
		}
		long d = w.getLongOr("d", hari);
		if (d != hari) {
			long shift = hari - d;
			int[] h0 = w.getIntArray("h0").orElse(new int[]{0});
			int[] h1 = w.getIntArray("h1").orElse(new int[]{0});
			if (shift == 1L) {
				w.putIntArray("h2", h1);
				w.putIntArray("h1", h0);
			} else if (shift == 2L) {
				w.putIntArray("h2", h0);
				w.putIntArray("h1", new int[]{0});
			} else {
				w.putIntArray("h2", new int[]{0});
				w.putIntArray("h1", new int[]{0});
			}
			w.putIntArray("h0", new int[]{0});
		}
		w.putLong("d", hari);
		w.putBoolean("s", staf);
		int[] h0 = w.getIntArray("h0").orElse(new int[]{0});
		int[] next;
		if (h0.length <= 1 || h0[h0.length - 1] != sig) {
			if (h0.length - 1 >= SEGMEN_MAKS) {
				next = h0.clone();
			} else {
				next = Arrays.copyOf(h0, h0.length + 1);
				next[next.length - 1] = sig;
			}
		} else {
			next = h0.clone();
		}
		next[0] = h0[0] + 1;
		w.putIntArray("h0", next);
		data.ubah();
	}

	/**
	 * The stored days of resident {@code id} as {@code [today, yesterday, the day before]} relative to {@code hari}
	 * ({@code [samples, segments...]}; {@code {0}} for a day without samples), plus the staff flag at index 3 ({@code {1}} = staff).
	 */
	static int[][] riwayat(MinecraftServer server, long situs, int id, long hari) {
		CompoundTag w = get(server).situs(RIWAYAT, situs).getCompoundOrEmpty("w" + id);
		int[][] out = {{0}, {0}, {0}, {w.getBooleanOr("s", false) ? 1 : 0}};
		if (!w.contains("d")) {
			return out;
		}
		long shift = hari - w.getLongOr("d", hari);
		for (int i = 0; i < 3; i++) {
			int src = i - (int) shift;
			if (shift >= 0L && src >= 0 && src < 3) {
				out[i] = w.getIntArray("h" + src).orElse(new int[]{0});
			}
		}
		return out;
	}

	/** Resident slots with a history in place {@code situs}. */
	static List<Integer> wargaRiwayat(MinecraftServer server, long situs) {
		List<Integer> out = new ArrayList<>();
		for (String key : get(server).situs(RIWAYAT, situs).keySet()) {
			if (key.startsWith("w")) {
				try {
					out.add(Integer.parseInt(key.substring(1)));
				} catch (NumberFormatException ignored) {
					// not ours
				}
			}
		}
		return out;
	}

	// ================================================================== metrik

	/** The accumulator compound of place {@code situs} for day {@code hari} (older than {@value #METRIK_HARI} days dropped). */
	static CompoundTag metrikHari(MinecraftServer server, long situs, long hari) {
		RagamData data = get(server);
		CompoundTag place = data.situs(METRIK, situs);
		String key = "h" + hari;
		CompoundTag day = place.getCompoundOrEmpty(key);
		if (!place.contains(key)) {
			place.put(key, day);
			for (String old : List.copyOf(place.keySet())) {
				try {
					if (old.startsWith("h") && Long.parseLong(old.substring(1)) <= hari - METRIK_HARI) {
						place.remove(old);
					}
				} catch (NumberFormatException ignored) {
					// not ours
				}
			}
		}
		data.ubah();
		return day;
	}

	/** The accumulator compound of a past day, or an empty one (never created). */
	static CompoundTag metrikLihat(MinecraftServer server, long situs, long hari) {
		return get(server).situs(METRIK, situs).getCompoundOrEmpty("h" + hari);
	}
}
