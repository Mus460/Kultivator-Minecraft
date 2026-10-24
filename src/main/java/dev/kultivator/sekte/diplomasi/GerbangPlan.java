package dev.kultivator.sekte.diplomasi;

import dev.kultivator.sekte.api.Lambang;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The seeded plan of the NPC sects of a world (DESIGN 36.2.9): for each sect its mountain-gate chunk, the gate's size band
 * and the sect's founding traits. It is a pure function of the world seed, the sect count and the least distance, so the
 * world generation thread ({@link GerbangSektePlacement}, which only sees the level seed) and the server simulation
 * ({@link SekteNpc#founded}) always agree without sharing state. Gates lie on a ring {@code jarakMin} to
 * {@code jarakMin + 1400} blocks from the world centre, one per sector, at least {@link #MIN_APART} blocks apart.
 * Thread safe: the last plan is cached in a volatile field.
 * <p>
 * <b>The one NPC sect list (integration audit T12).</b> Entry {@code i} is sect {@code "s" + i} of Diplomasi, rival group
 * {@code "g" + i} of the towns' Saingan (its home region is this gate column), the map domain {@code "sekte:s" + i}, a
 * possible elite tournament guest and the sect compound of DESIGN 38; there is no other list of NPC sects. The count is
 * {@code diplomasi.jumlahSekte}. Names are unique inside a plan: no two entries share a noun A or a modifier B, so their
 * names differ in every language; the fixed names elsewhere (rogue cults {@code musuh.sect.*}) have a shape the
 * generator never produces. {@link DaftarSekte} reads the list with or without the Diplomasi module.
 */
public final class GerbangPlan {

	/** Least distance between two gates (blocks); larger than the diagonal of a placement grid cell (24 chunks). */
	public static final int MIN_APART = 700;
	/** The ring's width beyond {@code jarakMin} (blocks). */
	public static final int RING = 1400;
	/** Gate size bands (structure sets {@code sekte_gerbang_kecil/sedang/besar}). */
	public static final int BAND_KECIL = 0;
	public static final int BAND_SEDANG = 1;
	public static final int BAND_BESAR = 2;
	public static final String[] BAND_NAMES = {"kecil", "sedang", "besar"};

	/** One planned sect. */
	public record Entry(int index, int blockX, int blockZ, int chunkX, int chunkZ, int band, int aliran, int pola, int kataA, int kataB,
			int tier, int raiderSect, int agresi, int kehormatan, long lambangSeed, int ketuaMarga, int ketuaNama) {

		/** The Diplomasi sect id ({@code "s" + index}). */
		public String id() {
			return DaftarSekte.id(index);
		}

		/** The id of the Saingan rival group that is this sect's travelling band ({@code "g" + index}). */
		public String rivalId() {
			return DaftarSekte.rivalId(index);
		}

		/** The sect's name ("Sekte Pedang Awan Ungu"), the same component as {@code SekteNpc.name()}. */
		public Component name() {
			return GeneratorNama.sect(pola, kataA, kataB);
		}

		/** "Ketua Mo Tianhe". */
		public Component master() {
			return GeneratorNama.master(ketuaMarga, ketuaNama);
		}

		/** The founding emblem. */
		public Lambang emblem() {
			return Lambang.seeded(lambangSeed);
		}

		/** The sect's ink colour (map border, the band's robes and name plate): see {@link GerbangPlan#warna(Lambang, int)}. */
		public int warna() {
			return GerbangPlan.warna(emblem(), aliran);
		}

		/** The seal glyph key of the map ({@code #} + lang key of the noun A's glyph). */
		public String seal() {
			return "#" + GeneratorNama.KEY + "nama.a." + Math.floorMod(kataA, GeneratorNama.KATA_A) + ".seal";
		}

		/** The gate column at sea level (y 64; the anchor knows the real height once the gate is born). */
		public BlockPos column() {
			return new BlockPos(blockX, 64, blockZ);
		}
	}

	/** A fallback ink per alignment when both emblem colours are too dark or too pale to read on parchment. */
	private static final int[] ALIRAN_INK = {0x2F6FB0, 0x8C6A1C, 0x7A1A16};

	/**
	 * The readable colour of an emblem: its field colour, else its charge colour, else the alignment's ink. A colour reads
	 * when its luma lies between 50 and 215 (black and white seals ink grey on grey otherwise).
	 */
	public static int warna(Lambang emblem, int aliran) {
		if (readable(emblem.fieldRgb())) {
			return emblem.fieldRgb();
		}
		if (readable(emblem.chargeRgb())) {
			return emblem.chargeRgb();
		}
		return ALIRAN_INK[Math.floorMod(aliran, ALIRAN_INK.length)];
	}

	private static boolean readable(int rgb) {
		int luma = ((rgb >> 16 & 0xFF) * 299 + (rgb >> 8 & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
		return luma >= 50 && luma <= 215;
	}

	/** The plan of one seed/count/distance. */
	public record Plan(long seed, int count, int minDist, List<Entry> entries) {

		/** True when chunk {@code (cx, cz)} holds the gate of a sect of {@code band}. */
		public boolean isGate(int band, int cx, int cz) {
			for (Entry e : entries) {
				if (e.chunkX == cx && e.chunkZ == cz && e.band == band) {
					return true;
				}
			}
			return false;
		}

		/** The entry of sect {@code index}, or null when out of range. */
		public @Nullable Entry entry(int index) {
			return index >= 0 && index < entries.size() ? entries.get(index) : null;
		}

		/** The entry of sect id {@code "s<i>"} or rival group id {@code "g<i>"}, or null. */
		public @Nullable Entry entry(@Nullable String id) {
			return entry(DaftarSekte.index(id));
		}

		/**
		 * The entry of alignment {@code aliran} (-1 = any) whose gate lies nearest {@code (x, z)}, or null when the plan has
		 * none of that alignment (DESIGN 38: "the nearest righteous sect", the demonic banner of a demonic town).
		 */
		public @Nullable Entry nearest(int x, int z, int aliran) {
			Entry best = null;
			long bestD = Long.MAX_VALUE;
			for (Entry e : entries) {
				if (aliran >= 0 && e.aliran != aliran) {
					continue;
				}
				long dx = e.blockX - x;
				long dz = e.blockZ - z;
				long d = dx * dx + dz * dz;
				if (d < bestD) {
					bestD = d;
					best = e;
				}
			}
			return best;
		}

		/** The gate chunk of {@code band} inside the grid cell of {@code spacing} chunks holding {@code (cx, cz)}, or null. */
		public Entry inCell(int band, int cx, int cz, int spacing) {
			int gx = Math.floorDiv(cx, spacing);
			int gz = Math.floorDiv(cz, spacing);
			for (Entry e : entries) {
				if (e.band == band && Math.floorDiv(e.chunkX, spacing) == gx && Math.floorDiv(e.chunkZ, spacing) == gz) {
					return e;
				}
			}
			return null;
		}
	}

	private static volatile int count = 8;
	private static volatile int minDist = 1500;
	private static volatile boolean enabled = true;
	private static volatile Plan cached;

	/** From {@code Diplomasi.init} and every config reload. */
	public static void configure(int sects, int distance, boolean on) {
		count = sects;
		minDist = distance;
		enabled = on;
	}

	public static boolean enabled() {
		return enabled;
	}

	/** The plan of {@code seed} with the current config. */
	public static Plan of(long seed) {
		Plan plan = cached;
		int n = count;
		int d = minDist;
		if (plan != null && plan.seed == seed && plan.count == n && plan.minDist == d) {
			return plan;
		}
		plan = build(seed, n, d);
		cached = plan;
		return plan;
	}

	/** Builds the plan (no caching; pure). */
	public static Plan build(long seed, int n, int distance) {
		Random random = new Random(seed ^ 0x5EC7E_D1A1L);
		double turn = random.nextDouble() * Math.PI * 2.0D;
		List<Entry> entries = new ArrayList<>();
		boolean[] usedA = new boolean[GeneratorNama.KATA_A];
		boolean[] usedB = new boolean[GeneratorNama.KATA_B];
		for (int i = 0; i < n; i++) {
			int bx = 0;
			int bz = 0;
			for (int attempt = 0; attempt < 24; attempt++) {
				double angle = turn + Math.PI * 2.0D * (i + 0.5D + (random.nextDouble() - 0.5D) * 0.6D) / n;
				double r = distance + random.nextDouble() * RING + attempt * 40.0D;
				bx = (int) Math.round(Math.cos(angle) * r);
				bz = (int) Math.round(Math.sin(angle) * r);
				if (apart(entries, bx, bz)) {
					break;
				}
			}
			int roll = random.nextInt(100);
			int band = roll < 45 ? BAND_KECIL : roll < 80 ? BAND_SEDANG : BAND_BESAR;
			// Alignment: about a quarter of the sects walk the demonic path, a quarter stay neutral.
			int a = random.nextInt(100);
			int aliran = a < 50 ? SekteNpc.LURUS : a < 75 ? SekteNpc.NETRAL : SekteNpc.IBLIS;
			int tier = Math.max(1, Math.min(5, 1 + band + random.nextInt(3)));
			int pola = random.nextInt(GeneratorNama.POLA);
			int kataA = unique(GeneratorNama.kataA(aliran, random), usedA, GeneratorNama.KATA_A, aliran, random, true);
			int kataB = unique(GeneratorNama.kataB(aliran, random), usedB, GeneratorNama.KATA_B, aliran, random, false);
			usedA[kataA] = true;
			usedB[kataB] = true;
			int raider = random.nextInt(8);
			int agresi = aliran == SekteNpc.IBLIS ? 55 + random.nextInt(40) : aliran == SekteNpc.NETRAL ? 25 + random.nextInt(45) : 5 + random.nextInt(40);
			int kehormatan = aliran == SekteNpc.LURUS ? 55 + random.nextInt(40) : aliran == SekteNpc.NETRAL ? 30 + random.nextInt(50) : 5 + random.nextInt(40);
			long lambang = random.nextLong();
			int marga = random.nextInt(GeneratorNama.MARGA.length);
			int nama = random.nextInt(GeneratorNama.NAMA.length);
			entries.add(new Entry(i, bx, bz, Math.floorDiv(bx, 16), Math.floorDiv(bz, 16), band, aliran, pola, kataA, kataB, tier, raider,
					agresi, kehormatan, lambang, marga, nama));
		}
		return new Plan(seed, n, distance, List.copyOf(entries));
	}

	/**
	 * A word index nobody in the plan uses yet (T12: unique names): the rolled word when free, else up to eight more rolls
	 * from the alignment's pool, else the first free word after the rolled one. The plan holds at most 12 sects and each
	 * pool has 20 words, so a free word always exists.
	 */
	private static int unique(int rolled, boolean[] used, int size, int aliran, Random random, boolean nounA) {
		int pick = Math.floorMod(rolled, size);
		for (int tries = 0; used[pick] && tries < 8; tries++) {
			pick = Math.floorMod(nounA ? GeneratorNama.kataA(aliran, random) : GeneratorNama.kataB(aliran, random), size);
		}
		for (int step = 0; used[pick] && step < size; step++) {
			pick = (pick + 1) % size;
		}
		return pick;
	}

	private static boolean apart(List<Entry> entries, int x, int z) {
		for (Entry e : entries) {
			long dx = e.blockX - x;
			long dz = e.blockZ - z;
			if (dx * dx + dz * dz < (long) MIN_APART * MIN_APART) {
				return false;
			}
		}
		return true;
	}

	private GerbangPlan() {
	}
}
