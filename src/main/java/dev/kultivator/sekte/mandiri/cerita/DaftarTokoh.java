package dev.kultivator.sekte.mandiri.cerita;

import dev.kultivator.disciple.DiscipleNames;
import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.sekte.mandiri.api.Tokoh;
import dev.kultivator.sosok.Sosok;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The named characters of a hall (DESIGN 39.8.3): at most {@link #MAKS} in {@code MandiriData.tokoh}, the one met the
 * longest ago forgotten first. A character's name is kept as a <i>name spec</i> so it reads in the reader's language:
 * {@code @kota:<look>:<p|l>} (a townsperson, {@code KotaNames.person}) or {@code @murid:<index>} (a cultivator,
 * {@code DiscipleNames}); the same spec is the diary argument, resolved by the composer ({@link #nama}).
 * <p>
 * The arcs (merchant regular, wanderer, old master, pickpocket, village head here; the rival in {@link BusurSaingan}, the
 * sweetheart in {@link BusurAsmara}) move on the real events of the other modules ({@code MandiriBus.BELANJA},
 * {@code KOTA_PERISTIWA}, {@code JALAN_TIBA}); each meeting writes one diary entry with the character's name.
 */
public final class DaftarTokoh {

	/** Characters kept per hall. */
	public static final int MAKS = 32;

	public static List<Tokoh> semua(MinecraftServer server, UUID owner) {
		CompoundTag root = MandiriApi.aula(server, owner, MandiriIds.A_TOKOH);
		ListTag list = root.getListOrEmpty("daftar");
		List<Tokoh> out = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			out.add(Tokoh.fromTag(list.getCompoundOrEmpty(i)));
		}
		return out;
	}

	public static @Nullable Tokoh cari(MinecraftServer server, UUID owner, String id) {
		for (Tokoh t : semua(server, owner)) {
			if (t.id().equals(id)) {
				return t;
			}
		}
		return null;
	}

	/** The first character of {@code peran} living at {@code situs} ("" matches any), or null. */
	public static @Nullable Tokoh cari(MinecraftServer server, UUID owner, Tokoh.Peran peran, String situs) {
		for (Tokoh t : semua(server, owner)) {
			if (t.peran() == peran && (situs == null || situs.isEmpty() || situs.equals(t.situs()))) {
				return t;
			}
		}
		return null;
	}

	/** The first character of {@code peran} this soul has met, or null. */
	public static @Nullable Tokoh dikenal(MinecraftServer server, UUID owner, Tokoh.Peran peran, UUID soul) {
		for (Tokoh t : semua(server, owner)) {
			if (t.peran() == peran && t.sikap().containsKey(soul)) {
				return t;
			}
		}
		return null;
	}

	/** Replaces (by id) or adds a character; the one unseen the longest is forgotten past {@link #MAKS}. */
	public static void simpan(MinecraftServer server, UUID owner, Tokoh tokoh) {
		List<Tokoh> all = semua(server, owner);
		all.removeIf(t -> t.id().equals(tokoh.id()));
		all.add(tokoh);
		while (all.size() > MAKS) {
			Tokoh oldest = all.get(0);
			for (Tokoh t : all) {
				if (t.hari() < oldest.hari()) {
					oldest = t;
				}
			}
			all.remove(oldest);
		}
		tulis(server, owner, all);
	}

	public static void lupakan(MinecraftServer server, UUID owner, String id) {
		List<Tokoh> all = semua(server, owner);
		if (all.removeIf(t -> t.id().equals(id))) {
			tulis(server, owner, all);
		}
	}

	private static void tulis(MinecraftServer server, UUID owner, List<Tokoh> all) {
		CompoundTag root = MandiriApi.aula(server, owner, MandiriIds.A_TOKOH);
		ListTag list = new ListTag();
		for (Tokoh t : all) {
			list.add(t.toTag());
		}
		root.put("daftar", list);
		root.putInt("n", root.getIntOr("n", 0));
		MandiriApi.tandai(server);
	}

	/**
	 * A new character of {@code peran} at {@code situs}, first met by {@code soul} on {@code hari} with attitude
	 * {@code sikap}. Townsfolk get a {@code KotaNames} name (sex by role: the sweetheart is the other sex of the
	 * disciple's name when known), cultivators a {@code DiscipleNames} name.
	 */
	public static Tokoh baru(MinecraftServer server, UUID owner, Tokoh.Peran peran, String situs, String watak, UUID soul, long hari,
			int sikap, long benih) {
		CompoundTag root = MandiriApi.aula(server, owner, MandiriIds.A_TOKOH);
		int n = root.getIntOr("n", 0) + 1;
		root.putInt("n", n);
		long h = PilihVarian.aduk(benih ^ n * 0x9E3779B97F4A7C15L ^ peran.ordinal());
		String nama = switch (peran) {
			case SAINGAN, AHLI_TUA, PENGEMBARA, SAHABAT_LUAR -> "@murid:" + Math.floorMod(h, (long) DiscipleNames.TOTAL);
			case KENALAN_HATI -> "@kota:" + (int) (h & 0x7FFFFFFF) + ":" + (Math.floorMod(h >>> 33, 2L) == 0 ? "p" : "l");
			case PEDAGANG, PENCURI, KEPALA_DESA -> "@kota:" + (int) (h & 0x7FFFFFFF) + ":" + (Math.floorMod(h >>> 33, 3L) == 0 ? "p" : "l");
		};
		String id = peran.name().toLowerCase(Locale.ROOT) + "#" + n;
		Tokoh t = new Tokoh(id, nama, peran, situs == null ? "" : situs, watak == null ? "" : watak, Map.of(soul, sikap), 1, hari);
		simpan(server, owner, t);
		return t;
	}

	/** The character's name in the reader's language. */
	public static MutableComponent nama(Tokoh tokoh) {
		return nama(tokoh.nama());
	}

	/** A name spec ({@code @kota:<look>:<p|l>}, {@code @murid:<index>}) or a plain name. */
	public static MutableComponent nama(String spec) {
		if (spec != null && spec.startsWith("@kota:")) {
			String[] part = spec.split(":");
			try {
				return KotaNames.person(Integer.parseInt(part[1]), part.length > 2 && "p".equals(part[2]));
			} catch (RuntimeException exception) {
				return Component.literal(spec);
			}
		}
		if (spec != null && spec.startsWith("@murid:")) {
			try {
				return DiscipleNames.name(Integer.parseInt(spec.substring(7)));
			} catch (NumberFormatException exception) {
				return Component.literal(spec);
			}
		}
		return Component.literal(spec == null ? "?" : spec);
	}

	/** True for a woman's name spec. */
	public static boolean perempuan(String spec) {
		if (spec != null && spec.startsWith("@kota:")) {
			return spec.endsWith(":p");
		}
		if (spec != null && spec.startsWith("@murid:")) {
			try {
				return DiscipleNames.kelamin(Integer.parseInt(spec.substring(7))) == Sosok.PEREMPUAN;
			} catch (NumberFormatException exception) {
				return false;
			}
		}
		return false;
	}

	/** The diary argument of a town ({@code @kota_nama:<townId>}). */
	public static String argKota(long town) {
		return "@kota_nama:" + town;
	}

	private DaftarTokoh() {
	}
}
