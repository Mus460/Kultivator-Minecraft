package dev.kultivator.sekte.gabung.hidup;

import dev.kultivator.alchemy.AlchemyComponents;
import dev.kultivator.alchemy.PillQuality;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Element;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.dunia.kota.Harga;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.tempat.ekonomi.SenjataKota;
import dev.kultivator.harta.BeastCores;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.item.RootItems;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import dev.kultivator.technique.source.JadeSlips;
import dev.kultivator.technique.spec.dsl.Route;
import dev.kultivator.tempa.ArtifactItem;
import dev.kultivator.tempa.Forging;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The stock of a sect's Paviliun Jasa (DESIGN 41.6.5): what the pavilion of one sect holds this week, at which base PK
 * (one price source: {@code TechniqueGrade.pavilionPrice}, the hall prices of {@code Contribution.Ware}, NB / 2 for
 * weapons and materials, never a new price), behind which rank and class gate, and how much is left this week.
 * <p>
 * Drawn once per world week ({@code day / 7}) from {@code (world seed, sect, week)}: {@code 6 + class} technique slips
 * of the corak's Dao plus two other Dao (grades up to the class's best, MEROSOT: HUANG), the pills up to the class's best
 * pill (41.4.5), town weapons and forged artifacts of grade up to {@code class - 1}, materials flavoured by the corak
 * (herbs for ALKIMIA/RACUN, beast cores for BINATANG, ores for TUBUH/PEDANG), the one Teknik Warisan, and the realm keys
 * left over from the month's quota ({@link KuotaAlam}). The corak colours the whole shelf (D-41C): the pill room of an
 * ALKIMIA sect stocks twice as much and one high-quality Golden Core pill, BUDDHA opens the soul pill and TUBUH the marrow
 * pill one class earlier; weapons and artifact forms lean to the style's own shapes ({@link #FORMA_CORAK}); the material
 * tab adds two items only that style keeps ({@link #EKSTRA}). Sold counts per week live in
 * {@code DataSekteGabung.bagian("paviliun")}; a member's purchases are shared stock (multiplayer competes).
 */
public final class StokPaviliun {

	/**
	 * One ware: id (the op argument), tab ({@code GabungLembar.Paviliun.TAB_*}), how to make it (lazy), base PK, weekly
	 * stock ({@code -1} unlimited), grade/quality for the colour ({@code -1} none), its Dao (corak discount), the lowest
	 * rank, the lowest class, a Teknik Warisan, and the realm-key column of {@link KuotaAlam} ({@code -1} none).
	 */
	public record Barang(String id, String tab, Supplier<ItemStack> buat, int harga, int stok, int mutu, @Nullable Dao dao, Pangkat pangkatMin,
			int kelasMin, boolean warisan, int kuota) {
	}

	/** Pills of the pavilion: id, PK (the {@code Contribution.Ware} price where the hall sells it, else town base / 2), class, rank, weekly stock. */
	private record Pil(String id, int pk, int kelasMin, Pangkat pangkatMin, int stok, PillQuality mutu) {
	}

	// prices copied from Contribution.Ware (package-private there): Penyembuh 6, Pengumpul Qi 6, Fondasi 60, Inti Emas 300,
	// Penangkal Tribulasi 1000, Pembersih Sumsum Sempurna 400; the Abadi extras at town base / 2 (Harga.NB_PER_PK).
	private static final List<Pil> PIL = List.of(
			new Pil("healing_pill", 6, 1, Pangkat.TERCATAT, -1, PillQuality.MID),
			new Pil("qi_gathering_pill", 6, 1, Pangkat.TERCATAT, -1, PillQuality.MID),
			new Pil("foundation_pill", 60, 1, Pangkat.LUAR, 6, PillQuality.MID),
			new Pil("golden_core_pill", 300, 2, Pangkat.DALAM, 3, PillQuality.MID),
			new Pil("tribulation_warding_pill", 1000, 3, Pangkat.INTI, 2, PillQuality.MID),
			new Pil("marrow_cleansing_pill", 400, 4, Pangkat.INTI, 1, PillQuality.PERFECT),
			new Pil("soul_nurturing_pill", 450, 5, Pangkat.PEWARIS, 2, PillQuality.MID),
			new Pil("dao_insight_pill", 600, 5, Pangkat.PEWARIS, 1, PillQuality.MID));

	/** Ores of the material tab: item, base NB (the guild forge's metal prices), weekly stock. */
	private static final Object[][] LOGAM = {
			{"iron", Items.IRON_INGOT, 4, 32}, {"gold", Items.GOLD_INGOT, 10, 16}, {"amethyst", Items.AMETHYST_SHARD, 6, 16},
			{"diamond", Items.DIAMOND, 40, 4}};

	/**
	 * The shapes each style forges and stocks: town weapon forms ({@code SenjataKota.Bentuk} ids) and artifact forms
	 * ({@code ArtifactItem.FORMS}); three of the four weapons and two of the three artifacts are drawn from these.
	 */
	private record Forma(List<String> senjata, List<String> pusaka) {
	}

	static final Map<Corak, Forma> FORMA_CORAK = new EnumMap<>(Corak.class);

	static {
		FORMA_CORAK.put(Corak.PEDANG, new Forma(List.of("jian", "dao", "kebut"), List.of("pedang")));
		FORMA_CORAK.put(Corak.BUDDHA, new Forma(List.of("kebut", "gada"), List.of("lonceng")));
		FORMA_CORAK.put(Corak.BINATANG, new Forma(List.of("qiang", "kapak"), List.of("lonceng", "payung")));
		FORMA_CORAK.put(Corak.FORMASI, new Forma(List.of("kipas_besi", "kebut"), List.of("payung")));
		FORMA_CORAK.put(Corak.UNSUR, new Forma(List.of("kipas_besi", "jian"), List.of("payung")));
		FORMA_CORAK.put(Corak.ALKIMIA, new Forma(List.of("kipas_besi", "dao"), List.of("wajan")));
		FORMA_CORAK.put(Corak.TUBUH, new Forma(List.of("gada", "kapak", "qiang"), List.of("pedang", "lonceng")));
		FORMA_CORAK.put(Corak.DARAH, new Forma(List.of("kapak", "dao"), List.of("pedang", "wajan")));
		FORMA_CORAK.put(Corak.RACUN, new Forma(List.of("kipas_besi", "dao"), List.of("wajan", "payung")));
		FORMA_CORAK.put(Corak.BAYANGAN, new Forma(List.of("dao", "jian"), List.of("payung", "pedang")));
	}

	/** Materials only one style keeps: name, item, base NB (the shop price), weekly stock, lowest class. Two per style. */
	private static final Map<Corak, Object[][]> EKSTRA = new EnumMap<>(Corak.class);

	static {
		EKSTRA.put(Corak.PEDANG, new Object[][] {{"batu_asah", Items.GRINDSTONE, 8, 4, 1}, {"kuarsa", Items.QUARTZ, 4, 12, 2}});
		EKSTRA.put(Corak.BUDDHA, new Object[][] {{"madu", Items.HONEY_BOTTLE, 6, 8, 1}, {"kristal_ametis", Items.AMETHYST_SHARD, 6, 8, 2}});
		EKSTRA.put(Corak.BINATANG, new Object[][] {{"kulit", Items.LEATHER, 2, 24, 1}, {"pelana", Items.SADDLE, 30, 1, 2}});
		EKSTRA.put(Corak.FORMASI, new Object[][] {{"lazuardi", Items.LAPIS_LAZULI, 2, 24, 1}, {"kompas", Items.COMPASS, 12, 2, 2}});
		EKSTRA.put(Corak.UNSUR, new Object[][] {{"tembaga", Items.COPPER_INGOT, 3, 24, 1}, {"batang_petir", Items.LIGHTNING_ROD, 12, 2, 2}});
		EKSTRA.put(Corak.ALKIMIA, new Object[][] {{"serbuk_api", Items.BLAZE_POWDER, 8, 8, 1}, {"debu_cahaya", Items.GLOWSTONE_DUST, 4, 12, 1}});
		EKSTRA.put(Corak.TUBUH, new Object[][] {{"batu_bara", Items.COAL, 1, 48, 1}, {"batu_asah", Items.GRINDSTONE, 8, 3, 2}});
		EKSTRA.put(Corak.DARAH, new Object[][] {{"batang_api", Items.BLAZE_ROD, 10, 4, 2}, {"bunga_neraka", Items.NETHER_WART, 4, 12, 1}});
		EKSTRA.put(Corak.RACUN, new Object[][] {{"mata_laba", Items.SPIDER_EYE, 3, 16, 1}, {"mata_laba_busuk", Items.FERMENTED_SPIDER_EYE, 8, 4, 2}});
		EKSTRA.put(Corak.BAYANGAN, new Object[][] {{"mutiara_ender", Items.ENDER_PEARL, 10, 4, 2}, {"tinta", Items.INK_SAC, 2, 24, 1}});
	}

	static final String BAGIAN = "paviliun";

	/** The highest technique grade a member of {@code pangkat} may buy (41.6.1 rights; Wakil/Ketua: no rank cap). */
	public static @Nullable TechniqueGrade hakTeknik(Pangkat pangkat) {
		return switch (pangkat) {
			case PELAYAN, TERCATAT -> null;
			case LUAR -> TechniqueGrade.HUANG;
			case DALAM -> TechniqueGrade.XUAN;
			case INTI -> TechniqueGrade.DI;
			case PEWARIS, TETUA, TAMU -> TechniqueGrade.TIAN;
			case WAKIL, KETUA -> TechniqueGrade.ABADI;
		};
	}

	/** The best grade the pavilion of {@code sekte} stocks (MEROSOT: HUANG only, 41.4.7). */
	public static TechniqueGrade teknikMaks(MinecraftServer server, String sekte) {
		GabungData.DataSekteGabung ds = GabungData.get(server).peekSekte(sekte);
		return ds != null && ds.merosot ? TechniqueGrade.HUANG : GabungApi.kelasSekte(sekte).teknikMaks();
	}

	/** The world week of day {@code hari}. */
	public static long minggu(long hari) {
		return Math.floorDiv(hari, 7L);
	}

	/** Everything the pavilion of {@code sekte} offers this week (made lazily; stock not yet subtracted). */
	public static List<Barang> semua(MinecraftServer server, String sekte) {
		long hari = Kota.day(server);
		long week = minggu(hari);
		long seed = server.overworld().getSeed();
		RandomSource random = RandomSource.create(seed ^ sekte.hashCode() * 0x632BE59BD9B4E019L ^ week * 0x9E3779B97F4A7C15L);
		KelasSekte kelas = GabungApi.kelasSekte(sekte);
		Corak corak = GabungApi.corak(sekte);
		List<Barang> out = new ArrayList<>();
		teknik(server, sekte, kelas, corak, random, out);
		TechniqueSpec warisan = warisan(server, sekte);
		if (warisan != null) {
			String spec = warisan.id();
			Pangkat min = kelas.nomor() <= KelasSekte.MENENGAH.nomor() ? Pangkat.DALAM : Pangkat.INTI;
			out.add(new Barang("w." + spec, GabungLembar.Paviliun.TAB_WARISAN, () -> JadeSlips.stack(spec),
					PaviliunJasa.KALI_WARISAN * warisan.grade().pavilionPrice(), 1, warisan.grade().tier(), warisan.dao(), min, 1, true, -1));
		}
		for (Pil p : PIL) {
			int stok = p.stok < 0 || corak != Corak.ALKIMIA ? p.stok : p.stok * 2;
			out.add(new Barang("p." + p.id, GabungLembar.Paviliun.TAB_PIL, () -> pil(p), p.pk, stok, p.mutu.ordinal(), null, p.pangkatMin,
					kelasPil(p, corak), false, -1));
		}
		if (corak == Corak.ALKIMIA) {
			// the furnace sect's own batch: one Golden Core pill of high quality (a third dearer, two a week)
			Pil emas = PIL.get(3);
			out.add(new Barang("p." + emas.id + ".tinggi", GabungLembar.Paviliun.TAB_PIL,
					() -> AlchemyComponents.withQuality(Harga.item(emas.id), PillQuality.HIGH), emas.pk * 4 / 3, 2, PillQuality.HIGH.ordinal(), null,
					emas.pangkatMin, emas.kelasMin, false, -1));
		}
		pusaka(server, kelas, corak, random, out);
		bahan(kelas, corak, random, out);
		int[] sisa = KuotaAlam.sisa(server, sekte);
		for (int col = 0; col < sisa.length; col++) {
			if (sisa[col] > 0) {
				int c = col;
				out.add(new Barang("k." + col, GabungLembar.Paviliun.TAB_KUOTA, () -> KuotaAlam.kunci(c, sekte), KuotaAlam.HARGA[col], sisa[col], col,
						null, Pangkat.DALAM, 1, false, col));
			}
		}
		return out;
	}

	/** The lowest class that stocks pill {@code p} in a sect of {@code corak}: BUDDHA the soul pill, TUBUH the marrow pill, one class earlier. */
	private static int kelasPil(Pil p, Corak corak) {
		boolean awal = corak == Corak.BUDDHA && "soul_nurturing_pill".equals(p.id) || corak == Corak.TUBUH && "marrow_cleansing_pill".equals(p.id);
		return awal ? Math.max(1, p.kelasMin - 1) : p.kelasMin;
	}

	private static ItemStack pil(Pil p) {
		if ("marrow_cleansing_pill".equals(p.id)) {
			return AlchemyComponents.withQuality(new ItemStack(RootItems.MARROW_CLEANSING_PILL), PillQuality.PERFECT);
		}
		ItemStack stack = Harga.item(p.id);
		return stack.isEmpty() ? stack : AlchemyComponents.withQuality(stack, p.mutu);
	}

	/** The pills of the weekly ration ({@link Jatah}), the same tap: a MID pill of kind {@code id}. */
	static ItemStack pilJatah(String id, int jumlah) {
		ItemStack stack = Harga.item(id);
		if (stack.isEmpty()) {
			return stack;
		}
		stack.setCount(Math.max(1, Math.min(stack.getMaxStackSize(), jumlah)));
		return AlchemyComponents.withQuality(stack, PillQuality.MID);
	}

	/** The best pill (id) the pavilion of class {@code kelas} keeps, {@code turun} steps below it (ration, 41.6.7). */
	static String pilKelas(int kelas, int turun) {
		List<String> ids = new ArrayList<>();
		for (Pil p : PIL) {
			if (p.kelasMin <= kelas && !"marrow_cleansing_pill".equals(p.id)) {
				ids.add(p.id);
			}
		}
		return ids.get(Math.max(0, ids.size() - 1 - Math.max(0, turun)));
	}

	// ------------------------------------------------------------------ techniques

	private static void teknik(MinecraftServer server, String sekte, KelasSekte kelas, Corak corak, RandomSource random, List<Barang> out) {
		TechniqueGrade maks = teknikMaks(server, sekte);
		List<Dao> pool = new ArrayList<>(List.of(corak.dao()));
		List<Dao> others = new ArrayList<>();
		for (Dao d : Dao.values()) {
			if (!pool.contains(d)) {
				others.add(d);
			}
		}
		for (int i = 0; i < 2 && !others.isEmpty(); i++) {
			pool.add(others.remove(random.nextInt(others.size())));
		}
		List<TechniqueSpec> candidates = new ArrayList<>();
		for (TechniqueSpec spec : TechniqueRegistry.all()) {
			if (spec.dao() != null && pool.contains(spec.dao()) && spec.grade().tier() <= maks.tier() && sellable(spec)) {
				candidates.add(spec);
			}
		}
		candidates.sort(Comparator.comparing(TechniqueSpec::id));
		int n = PaviliunJasa.STOK_TEKNIK_DASAR + kelas.nomor();
		// corak Dao first (half the shelf), then the guests
		List<TechniqueSpec> own = new ArrayList<>();
		List<TechniqueSpec> guest = new ArrayList<>();
		for (TechniqueSpec spec : candidates) {
			(corak.punyaDao(spec.dao()) ? own : guest).add(spec);
		}
		List<TechniqueSpec> chosen = new ArrayList<>();
		while (chosen.size() < n && (!own.isEmpty() || !guest.isEmpty())) {
			List<TechniqueSpec> from = !own.isEmpty() && (chosen.size() % 2 == 0 || guest.isEmpty()) ? own : guest;
			chosen.add(from.remove(random.nextInt(from.size())));
		}
		chosen.sort(Comparator.comparingInt((TechniqueSpec s) -> s.grade().tier()).thenComparing(TechniqueSpec::id));
		for (TechniqueSpec spec : chosen) {
			String id = spec.id();
			out.add(new Barang("t." + id, GabungLembar.Paviliun.TAB_TEKNIK, () -> JadeSlips.stack(id), spec.grade().pavilionPrice(), 1,
					spec.grade().tier(), spec.dao(), Pangkat.LUAR, 1, false, -1));
		}
	}

	/** The rule of the hall pavilion ({@code Contribution.sellable}, package-private there, copied). */
	static boolean sellable(TechniqueSpec spec) {
		if (!spec.slipAllowed() || spec.grade().pavilionPrice() <= 0) {
			return false;
		}
		if (spec.pavilion()) {
			return true;
		}
		if (spec.isLegacy()) {
			for (Route route : spec.routes()) {
				if (route.kind() == Route.Kind.BOOK) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * The Teknik Warisan of {@code sekte} (41.4.3): one real existing spec of the corak's Dao at the class's best grade
	 * (else the best grade below it), chosen with the sect's plan seed, so it is the same for every member and only
	 * changes when the class does. Null when no spec fits.
	 */
	public static @Nullable TechniqueSpec warisan(MinecraftServer server, String sekte) {
		Corak corak = GabungApi.corak(sekte);
		TechniqueGrade maks = teknikMaks(server, sekte);
		var entry = GabungApi.entry(server, sekte);
		long seed = entry == null ? sekte.hashCode() : entry.lambangSeed();
		for (int tier = maks.tier(); tier >= TechniqueGrade.HUANG.tier(); tier--) {
			List<TechniqueSpec> fit = new ArrayList<>();
			for (Dao dao : corak.dao()) {
				for (TechniqueSpec spec : TechniqueRegistry.ofDao(dao)) {
					if (spec.grade().tier() == tier && spec.slipAllowed() && spec.playerVisible() && spec.grade().pavilionPrice() > 0) {
						fit.add(spec);
					}
				}
			}
			if (!fit.isEmpty()) {
				fit.sort(Comparator.comparing(TechniqueSpec::id));
				return fit.get((int) Math.floorMod(seed, (long) fit.size()));
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ weapons and artifacts

	private static void pusaka(MinecraftServer server, KelasSekte kelas, Corak corak, RandomSource random, List<Barang> out) {
		int maks = Math.max(0, kelas.nomor() - 1);
		SenjataKota.Bentuk[] shapes = SenjataKota.Bentuk.values();
		SenjataKota.Mutu[] grades = SenjataKota.Mutu.values();
		Forma forma = FORMA_CORAK.get(corak);
		for (int i = 0; i < 4; i++) {
			// three of the four are the style's own shapes
			SenjataKota.Bentuk bentuk = bentukDari(shapes, forma != null && i < 3 ? forma.senjata() : null, random);
			SenjataKota.Mutu mutu = grades[Math.min(maks, Math.min(grades.length - 1, random.nextInt(maks + 1)))];
			String id = "s." + bentuk.id + "." + mutu.id;
			if (out.stream().anyMatch(b -> b.id().equals(id))) {
				continue;
			}
			int pk = (int) Math.max(1L, Math.round(bentuk.harga(mutu) / Harga.NB_PER_PK));
			out.add(new Barang(id, GabungLembar.Paviliun.TAB_PUSAKA, () -> SenjataKota.stack(bentuk, mutu), pk, 1, mutu.ordinal(), null,
					Pangkat.LUAR, 1, false, -1));
		}
		for (int i = 0; i < 3; i++) {
			// two of the three artifacts are the style's own forms
			List<String> forms = forma != null && i < 2 ? forma.pusaka() : ArtifactItem.FORMS;
			String form = forms.get(random.nextInt(forms.size()));
			int grade = random.nextInt(Math.min(3, maks) + 1);
			long seed = random.nextLong();
			int pk = (int) Math.max(1L, Math.round(Harga.ARTIFACT[grade] / Harga.NB_PER_PK));
			out.add(new Barang("a." + i + "." + form + "." + grade, GabungLembar.Paviliun.TAB_PUSAKA,
					() -> Forging.buatStok(server.overworld(), form, grade, RandomSource.create(seed), null), pk, 1, grade, null, Pangkat.LUAR, 1, false,
					-1));
		}
	}

	/** A weapon shape: one of {@code pilihan} (ids) when given, else any. */
	private static SenjataKota.Bentuk bentukDari(SenjataKota.Bentuk[] shapes, @Nullable List<String> pilihan, RandomSource random) {
		if (pilihan != null) {
			String want = pilihan.get(random.nextInt(pilihan.size()));
			for (SenjataKota.Bentuk b : shapes) {
				if (b.id.equals(want)) {
					return b;
				}
			}
		}
		return shapes[random.nextInt(shapes.length)];
	}

	// ------------------------------------------------------------------ materials

	private static void bahan(KelasSekte kelas, Corak corak, RandomSource random, List<Barang> out) {
		boolean herbal = corak == Corak.ALKIMIA || corak == Corak.RACUN || corak == Corak.BUDDHA;
		boolean buas = corak == Corak.BINATANG || corak == Corak.DARAH;
		boolean logam = corak == Corak.TUBUH || corak == Corak.PEDANG || corak == Corak.FORMASI;
		int herbs = herbal ? 5 : 2;
		int minAge = 30 + 40 * kelas.nomor();
		int maxAge = Math.min(Harga.SHOP_HERB_MAX_AGE, minAge + 120);
		Element element = corak.daoUtama().affinity();
		for (int i = 0; i < herbs; i++) {
			long seed = random.nextLong();
			ItemStack sample = SpiritHerbs.randomFor(element, RandomSource.create(seed), minAge, maxAge);
			int pk = (int) Math.max(1L, Math.round(Harga.value(sample) / Harga.NB_PER_PK));
			out.add(new Barang("h." + i, GabungLembar.Paviliun.TAB_BAHAN, () -> SpiritHerbs.randomFor(element, RandomSource.create(seed), minAge, maxAge),
					pk, herbal ? 4 : 2, -1, null, Pangkat.LUAR, 1, false, -1));
		}
		int cores = buas ? 4 : 1;
		Element[] elements = Element.values();
		for (int i = 0; i < cores; i++) {
			Element e = elements[random.nextInt(elements.length)];
			int grade = Math.max(1, Math.min(4, 1 + random.nextInt(Math.max(1, kelas.nomor()))));
			ItemStack sample = BeastCores.create(e, grade);
			int pk = (int) Math.max(1L, Math.round(Harga.value(sample) / Harga.NB_PER_PK));
			out.add(new Barang("c." + i, GabungLembar.Paviliun.TAB_BAHAN, () -> BeastCores.create(e, grade), pk, buas ? 3 : 1, grade, null,
					Pangkat.LUAR, 1, false, -1));
		}
		for (Object[] row : LOGAM) {
			Item item = (Item) row[1];
			int nb = (int) row[2];
			int stock = (int) row[3] * (logam ? 2 : 1);
			out.add(new Barang("m." + row[0], GabungLembar.Paviliun.TAB_BAHAN, () -> new ItemStack(item), Math.max(1, nb / 2), stock, -1, null,
					Pangkat.LUAR, 1, false, -1));
		}
		Object[][] ekstra = EKSTRA.get(corak);
		if (ekstra != null) {
			for (Object[] row : ekstra) {
				Item item = (Item) row[1];
				out.add(new Barang("x." + row[0], GabungLembar.Paviliun.TAB_BAHAN, () -> new ItemStack(item), Math.max(1, (int) row[2] / 2), (int) row[3],
						-1, null, Pangkat.LUAR, (int) row[4], false, -1));
			}
		}
	}

	// ------------------------------------------------------------------ weekly stock

	/** The sold-count compound of this week (reset when the week turns). */
	static CompoundTag terjual(MinecraftServer server, String sekte) {
		GabungData data = GabungData.get(server);
		CompoundTag b = data.sekte(sekte).bagian(BAGIAN);
		long week = minggu(Kota.day(server));
		if (b.getLongOr("minggu", -1L) != week) {
			b.putLong("minggu", week);
			b.put("terjual", new CompoundTag());
			data.setDirty();
		}
		CompoundTag sold = b.getCompoundOrEmpty("terjual");
		if (!b.contains("terjual")) {
			b.put("terjual", sold);
		}
		return sold;
	}

	/** What is left of {@code barang} this week ({@code -1} unlimited). */
	static int sisa(MinecraftServer server, String sekte, Barang barang) {
		if (barang.kuota() >= 0) {
			// realm keys: the month's leftover count itself (KuotaAlam), not this week's sales
			return KuotaAlam.sisa(server, sekte)[barang.kuota()];
		}
		if (barang.stok() < 0) {
			return -1;
		}
		return Math.max(0, barang.stok() - terjual(server, sekte).getIntOr(barang.id(), 0));
	}

	static void catatJual(MinecraftServer server, String sekte, Barang barang, int n) {
		if (barang.kuota() >= 0) {
			return;
		}
		CompoundTag sold = terjual(server, sekte);
		sold.putInt(barang.id(), sold.getIntOr(barang.id(), 0) + n);
		GabungData.get(server).setDirty();
	}

	private StokPaviliun() {
	}
}
