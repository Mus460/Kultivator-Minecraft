package dev.kultivator.client.sekte.mandiri;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.cerita.DaftarTokoh;
import dev.kultivator.sekte.mandiri.cerita.PilihVarian;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The diary composer (DESIGN 39.8.1): turns one stored {@link EntriHarian} (keys and arguments only) into the 3-4
 * sentences the reader sees, in the reader's language:
 * <ol>
 * <li><b>opener</b> {@code kultivator.sekte.mandiri.harian.buka.<konteks>.<n>}: the weather of that real day (rain, snow,
 * storm; the writer stored {@code ctx:<cuaca>:<bulan>}), else the moon at night (full / new), else the place (a town, the
 * road), else the hour (dawn, noon, dusk, night);</li>
 * <li><b>core</b>: the entry's key, a short one under {@code kultivator.sekte.mandiri.harian.} or a full key of any
 * fragment (39A {@code inti.harian.*}, 39C {@code jalan.harian.*}, 39F {@code seteru.*}), with variants {@code .0}-{@code .n}
 * when they exist;</li>
 * <li><b>reflection</b> {@code harian.renung.<sifat>.<n>}: the disciple's trait that best fits the mood;</li>
 * <li><b>closing</b> {@code harian.tutup.<cita>.<n>}, half of the time: the priority-1 goal.</li>
 * </ol>
 * Every variant is chosen with {@link PilihVarian} seeded by {@code (soul, day, kind, hour)}, so an entry always reads the
 * same. Arguments: {@code t:} technique, {@code r:} realm (or a rank step), {@code d:} Dao, {@code i:} item, {@code n:} number,
 * {@code @kota_nama:<id>} a town, {@code @kota:}/{@code @murid:} a named character, {@code key|a|b} a nested line, lang keys
 * translated, anything else literal. Travel story lines ({@code jenis} starting with {@code kisah}) are shown as they are.
 * Results are cached per entry (the page composes once per arrival). Client only.
 */
public final class HarianTeks {

	public static final String H = "kultivator.sekte.mandiri.harian.";
	private static final String C = "kultivator.sekte.mandiri.cerita.";
	private static final int VARIAN_MAKS = 16;

	/** One composed entry. */
	public record Tersusun(Component kepala, Component isi, EntriHarian.Suasana suasana, boolean penting, String jenis) {
	}

	/** What the composer knows about the writer: traits (ids, strongest first) and the priority-1 goal kind ("" none). */
	public record Penulis(UUID soul, List<String> sifat, String cita) {
	}

	private static final Map<String, Integer> VARIAN = new HashMap<>();
	private static Language bahasa;

	/** The number of variants {@code base.0 .. base.n-1} in the current language (0 = none; cached per language). */
	public static int varian(String base) {
		Language now = Language.getInstance();
		if (now != bahasa) {
			bahasa = now;
			VARIAN.clear();
		}
		Integer known = VARIAN.get(base);
		if (known != null) {
			return known;
		}
		int n = 0;
		while (n < VARIAN_MAKS && now.has(base + "." + n)) {
			n++;
		}
		VARIAN.put(base, n);
		return n;
	}

	/** The key of variant {@code salt} of {@code base} (the base itself without variants), or null when neither exists. */
	public static @Nullable String pilih(String base, long benih, int salt) {
		int n = varian(base);
		if (n > 0) {
			return base + "." + PilihVarian.indeks(benih, salt, n);
		}
		return Language.getInstance().has(base) ? base : null;
	}

	public static Tersusun susun(EntriHarian e, Penulis p) {
		long benih = PilihVarian.benih(p.soul(), e.hari(), e.jenis(), e.jam());
		String[] ctx = konteks(e.arg());
		List<Object> args = new ArrayList<>(e.arg().size());
		String kota = null;
		for (String a : e.arg()) {
			if (a == null || a.startsWith("ctx:")) {
				continue;
			}
			if (kota == null && a.startsWith("@kota_nama:")) {
				kota = a;
			}
			args.add(arg(a));
		}
		Component kepala = kepala(e, ctx);
		MutableComponent isi = Component.literal("");
		if (e.jenis().startsWith("kisah")) {
			isi.append(inti(e, args, benih));
			return new Tersusun(kepala, isi, e.suasana(), e.penting(), e.jenis());
		}
		String buka = pilih(H + "buka." + konteksBuka(e, ctx, kota != null), benih, PilihVarian.BUKA);
		if (buka != null) {
			isi.append(Component.translatable(buka, kota == null ? "" : arg(kota))).append(" ");
		}
		isi.append(inti(e, args, benih));
		String sifat = sifatUntuk(e.suasana(), p.sifat());
		if (sifat != null) {
			String renung = pilih(H + "renung." + sifat, benih, PilihVarian.RENUNG);
			if (renung != null) {
				isi.append(" ").append(Component.translatable(renung));
			}
		}
		if (!p.cita().isEmpty() && PilihVarian.peluang(benih, PilihVarian.TUTUP_ADA, 50)) {
			String tutup = pilih(H + "tutup." + p.cita(), benih, PilihVarian.TUTUP);
			if (tutup != null) {
				isi.append(" ").append(Component.translatable(tutup));
			}
		}
		return new Tersusun(kepala, isi, e.suasana(), e.penting(), e.jenis());
	}

	/** "Hari ke-41, senja." (or "Hari ke-58, malam purnama."). */
	private static Component kepala(EntriHarian e, String[] ctx) {
		String waktu = waktu(e.jam());
		if ("malam".equals(waktu) && "purnama".equals(ctx[1])) {
			waktu = "malam_purnama";
		} else if ("malam".equals(waktu) && "baru".equals(ctx[1])) {
			waktu = "malam_gelap";
		}
		return Component.translatable(C + "harian.kepala", e.hari() + 1, Component.translatable(C + "waktu." + waktu));
	}

	/** The core sentence: the entry key (variants when they exist), else a quiet fallback. */
	private static Component inti(EntriHarian e, List<Object> args, long benih) {
		String key = e.kunci();
		String base = key.startsWith("kultivator.") ? key : H + key;
		String chosen = pilih(base, benih, PilihVarian.INTI);
		if (chosen != null) {
			return Component.translatable(chosen, args.toArray());
		}
		String fallback = pilih(H + "umum", benih, PilihVarian.INTI);
		return fallback == null ? Component.literal("...") : Component.translatable(fallback, Component.translatable(C + "jenis." + jenisDasar(e.jenis())));
	}

	/** The base kind for the fallback label ("jalan.begal" reads as "jalan"). */
	private static String jenisDasar(String jenis) {
		int dot = jenis.indexOf('.');
		String base = dot < 0 ? jenis : jenis.substring(0, dot);
		return Language.getInstance().has(C + "jenis." + base) ? base : "lain";
	}

	/** {@code [cuaca, bulan]} from the writer's {@code ctx:} argument ({@code cerah}/{@code biasa} when missing). */
	static String[] konteks(List<String> arg) {
		for (String a : arg) {
			if (a != null && a.startsWith("ctx:")) {
				String[] part = a.substring(4).split(":");
				return new String[]{part.length > 0 ? part[0] : "cerah", part.length > 1 ? part[1] : "biasa"};
			}
		}
		return new String[]{"cerah", "biasa"};
	}

	private static String konteksBuka(EntriHarian e, String[] ctx, boolean punyaKota) {
		if (!"cerah".equals(ctx[0])) {
			return ctx[0];
		}
		String waktu = waktu(e.jam());
		if ("malam".equals(waktu) && ("purnama".equals(ctx[1]) || "baru".equals(ctx[1]))) {
			return "purnama".equals(ctx[1]) ? "purnama" : "bulan_baru";
		}
		String jenis = e.jenis();
		if (punyaKota && (jenis.startsWith("belanja") || jenis.startsWith("kota") || jenis.startsWith("tawar") || jenis.startsWith("tokoh"))) {
			return "kota";
		}
		if (jenis.startsWith("jalan") || jenis.startsWith("kisah") || jenis.startsWith("berburu")) {
			return "jalan";
		}
		return switch (waktu) {
			case "fajar", "pagi" -> "fajar";
			case "siang", "sore" -> "siang";
			case "senja" -> "senja";
			default -> "malam";
		};
	}

	/** The hour word (05-07 dawn, 07-11 morning, 11-15 noon, 15-17 afternoon, 17-19 dusk, else night). */
	static String waktu(int jam) {
		if (jam >= 5 && jam < 7) {
			return "fajar";
		}
		if (jam >= 7 && jam < 11) {
			return "pagi";
		}
		if (jam >= 11 && jam < 15) {
			return "siang";
		}
		if (jam >= 15 && jam < 17) {
			return "sore";
		}
		if (jam >= 17 && jam < 19) {
			return "senja";
		}
		return "malam";
	}

	/** The reflecting trait: the first of the mood's list the disciple has, else its strongest trait. */
	static @Nullable String sifatUntuk(EntriHarian.Suasana suasana, List<String> sifat) {
		if (sifat.isEmpty()) {
			return null;
		}
		String[] prefer = switch (suasana) {
			case BANGGA -> new String[]{"sombong", "ambisius", "rendah_hati", "setia"};
			case SENANG -> new String[]{"penyayang", "rajin", "setia", "berani"};
			case KESAL -> new String[]{"pendendam", "sombong", "berani", "licik"};
			case SEDIH -> new String[]{"penyayang", "setia", "tenang", "penakut"};
			case LELAH -> new String[]{"malas", "rajin", "tenang"};
			case TENANG -> new String[]{"tenang", "rendah_hati", "rajin"};
			case TERGODA -> new String[]{"licik", "ambisius", "pendendam"};
		};
		for (String want : prefer) {
			if (sifat.contains(want)) {
				return want;
			}
		}
		return sifat.get(0);
	}

	/** One stored argument as text (see the class comment). */
	public static Object arg(String a) {
		if (a == null || a.isEmpty()) {
			return "";
		}
		try {
			if (a.startsWith("t:")) {
				TechniqueSpec spec = TechniqueRegistry.byId(a.substring(2));
				return spec == null ? a.substring(2) : spec.displayName();
			}
			if (a.startsWith("r:")) {
				String v = a.substring(2);
				if (!v.isEmpty() && Character.isDigit(v.charAt(0))) {
					return Rank.ofStep(Math.max(0, Math.min(Rank.MAX_STEP, Integer.parseInt(v)))).displayName();
				}
				Realm realm = Realm.byId(v);
				return realm == null ? v : realm.displayName().copy().withColor(realm.color());
			}
			if (a.startsWith("d:")) {
				Dao dao = Dao.byId(a.substring(2));
				return dao == null ? a.substring(2) : dao.displayName();
			}
			if (a.startsWith("i:")) {
				Identifier id = Identifier.tryParse(a.substring(2));
				Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
				return item == null ? a.substring(2) : Component.translatable(item.getDescriptionId());
			}
			if (a.startsWith("n:")) {
				String v = a.substring(2);
				try {
					return String.format(Locale.ROOT, "%,d", Long.parseLong(v)).replace(',', '.');
				} catch (NumberFormatException exception) {
					return v;
				}
			}
			if (a.startsWith("@kota_nama:")) {
				return KotaNames.town(Long.parseLong(a.substring(11)));
			}
			if (a.startsWith("@kota:") || a.startsWith("@murid:")) {
				return DaftarTokoh.nama(a);
			}
			if (a.indexOf('|') > 0 && a.startsWith("kultivator.")) {
				String[] part = a.split("\\|");
				Object[] inner = new Object[part.length - 1];
				for (int i = 1; i < part.length; i++) {
					inner[i - 1] = arg(part[i]);
				}
				return Component.translatable(part[0], inner);
			}
			if (a.indexOf(' ') < 0 && a.indexOf('.') > 0 && (a.startsWith("kultivator.") || a.startsWith("item.") || a.startsWith("block.")
					|| a.startsWith("entity."))) {
				return Component.translatable(a);
			}
		} catch (RuntimeException exception) {
			return a;
		}
		return a;
	}

	private HarianTeks() {
	}
}
