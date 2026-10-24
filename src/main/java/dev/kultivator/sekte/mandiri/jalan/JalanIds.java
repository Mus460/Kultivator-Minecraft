package dev.kultivator.sekte.mandiri.jalan;

import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Names and small text helpers of module 39C (DESIGN 39.6). Everything 39C stores for later reading (diary entries,
 * news lines, the stage lines of the travel scroll) keeps only lang keys and plain arguments; an argument that is itself
 * a lang key is translated by {@code MandiriApi.teks} in the reader's language. Town and person names therefore travel
 * as two key arguments each ({@link #kota}, {@link #orang}) and every 39C sentence places them in its own word order
 * ("Kota %1$s %2$s" / "%2$s %1$s Town").
 * <p>
 * A stage line of the travel scroll ({@code HasilJalan.tahap}) is {@code <full key>|<arg>|<arg>...}: split at {@code '|'}
 * and pass the rest to {@code MandiriApi.teks(key, args)} ({@link #teksTahap} does exactly that).
 */
public final class JalanIds {

	/** Lang prefix of 39C ({@code kultivator.sekte.mandiri.jalan.}). */
	public static final String P = MandiriIds.LANG + "jalan.";
	/** 39C's own part of {@code DiscipleData.extra()} (outside the 39A schema): earnings of the day, gear tiers, badge. */
	public static final String EXTRA = "mandiri_jalan";
	/** Saved data {@code kultivator:mandiri_jalan} ({@link JalanData}). */
	public static final String DATA = "mandiri_jalan";
	/** Live site kind of a captive (DuniaMarkers row, red bandit-camp icon). */
	public static final String SITUS_TAWANAN = "mandiri/tawanan";
	/** Separator of the stage lines of the travel scroll. */
	public static final char PISAH = '|';

	/** Parchment colour of names in 39C chat lines. */
	public static final int WARNA_NAMA = 0xF2E6C8;
	/** Warm gold of good news. */
	public static final int WARNA_EMAS = 0xFFD54A;
	/** Soft grey of plain lines. */
	public static final int WARNA_ABU = 0xB8B8B8;
	/** Alarm red of wounds and captivity. */
	public static final int WARNA_MERAH = 0xFF6A5A;
	/** Jade of journeys. */
	public static final int WARNA_GIOK = 0x8FE3C8;

	/** The full key {@code kultivator.sekte.mandiri.jalan.<rest>}. */
	public static String k(String rest) {
		return P + rest;
	}

	/** A translatable 39C line. */
	public static MutableComponent t(String rest, Object... args) {
		return Component.translatable(P + rest, args);
	}

	// ================================================================== names as key arguments

	/** The two key arguments of a town's name ({@code KotaNames.town}): name, suffix. */
	public static List<String> kota(long town) {
		int seed = benihKota(town);
		return List.of("kultivator.kota.nama." + Math.floorMod(seed, KotaNames.NAMES),
				"kultivator.kota.akhiran." + Math.floorMod(seed / KotaNames.NAMES, KotaNames.SUFFIXES));
	}

	/** The same seed as {@code KotaNames.seed} (package-private there): the town's names are identical. */
	static int benihKota(long id) {
		long z = id * 0x9E3779B97F4A7C15L;
		z ^= z >>> 31;
		return (int) (z & 0x7FFFFFFF);
	}

	/** The two key arguments of a seeded person ({@code KotaNames.person}): surname, given name of the sex. */
	public static List<String> orang(int look, boolean perempuan) {
		int seed = Math.abs(look * 0x9E3779B1);
		String pool = perempuan ? "kultivator.kota.nama_diri_p." : "kultivator.kota.nama_diri_l.";
		return List.of("kultivator.kota.marga." + Math.floorMod(seed, KotaNames.SURNAMES), pool + Math.floorMod(seed / KotaNames.SURNAMES, KotaNames.GIVEN));
	}

	/**
	 * The two key arguments of a shown name: the surname and given name inside a {@code kultivator.kota.nama_orang}
	 * anywhere in {@code name} (an NPC's plate "Lin Feng · Juru Lelang"), else the seeded person of {@code look}.
	 */
	public static List<String> orang(@Nullable Component name, int look, boolean perempuan) {
		List<String> found = name == null ? null : cariOrang(name, 0);
		return found != null ? found : orang(look, perempuan);
	}

	private static @Nullable List<String> cariOrang(Object node, int depth) {
		if (depth > 4 || !(node instanceof Component component)) {
			return null;
		}
		if (component.getContents() instanceof TranslatableContents tr) {
			Object[] args = tr.getArgs();
			if ("kultivator.kota.nama_orang".equals(tr.getKey()) && args.length >= 2) {
				String a = kunciDari(args[0]);
				String b = kunciDari(args[1]);
				if (a != null && b != null) {
					return List.of(a, b);
				}
			}
			for (Object arg : args) {
				List<String> inner = cariOrang(arg, depth + 1);
				if (inner != null) {
					return inner;
				}
			}
		}
		for (Component sibling : component.getSiblings()) {
			List<String> inner = cariOrang(sibling, depth + 1);
			if (inner != null) {
				return inner;
			}
		}
		return null;
	}

	private static @Nullable String kunciDari(Object arg) {
		if (arg instanceof Component c && c.getContents() instanceof TranslatableContents tr) {
			return tr.getKey();
		}
		return arg instanceof String s && !s.isEmpty() ? s : null;
	}

	/** The town's name as a component (chat). */
	public static MutableComponent namaKota(long town) {
		return KotaNames.town(town);
	}

	/** {@code n} formatted as NB text ("12 NB"). */
	public static MutableComponent nb(long n) {
		return t("nb", n);
	}

	/** An item id ({@code kultivator:healing_pill}, {@code kultivator:herb@100}) as its item name key. */
	public static String kunciBarang(String itemId) {
		String id = itemId == null ? "" : itemId;
		int hash = id.indexOf('#');
		if (hash >= 0) {
			id = id.substring(0, hash);
		}
		int grade = 1;
		int at = id.indexOf('@');
		if (at >= 0) {
			try {
				grade = Integer.parseInt(id.substring(at + 1));
			} catch (NumberFormatException ignored) {
				grade = 1;
			}
			id = id.substring(0, at);
		}
		if (id.endsWith(":beast_core")) {
			String[] cores = {"low", "mid", "high", "king"};
			return "item.kultivator.beast_core_" + cores[Math.max(0, Math.min(3, grade - 1))];
		}
		int colon = id.indexOf(':');
		String ns = colon < 0 ? "kultivator" : id.substring(0, colon);
		String path = colon < 0 ? id : id.substring(colon + 1);
		if ("herb".equals(path)) {
			return P + "barang.herba";
		}
		if (path.startsWith("pedang_tier_")) {
			return P + "barang.pedang";
		}
		if (path.startsWith("jubah_tier_")) {
			return P + "barang.jubah";
		}
		return "item." + ns + "." + path.replace('/', '.');
	}

	// ================================================================== stage lines

	/** Packs a stage line: {@code key|arg|arg}. */
	public static String tahap(String key, List<String> args) {
		StringBuilder out = new StringBuilder(key);
		for (String arg : args) {
			out.append(PISAH).append(arg == null ? "" : arg.replace(PISAH, '/'));
		}
		return out.toString();
	}

	/** Splits a stage line into key (index 0) and arguments. */
	public static List<String> pecah(String line) {
		List<String> out = new ArrayList<>(6);
		if (line == null) {
			out.add("");
			return out;
		}
		int from = 0;
		for (int i = 0; i < line.length(); i++) {
			if (line.charAt(i) == PISAH) {
				out.add(line.substring(from, i));
				from = i + 1;
			}
		}
		out.add(line.substring(from));
		return out;
	}

	/** The text of one stage line (lang-key arguments translated). */
	public static Component teksTahap(String line) {
		List<String> parts = pecah(line);
		return dev.kultivator.sekte.mandiri.api.MandiriApi.teks(parts.get(0), parts.subList(1, parts.size()));
	}

	private JalanIds() {
	}
}
