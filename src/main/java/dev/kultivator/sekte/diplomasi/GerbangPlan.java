package dev.kultivator.sekte.diplomasi;

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
			int kataA = GeneratorNama.kataA(aliran, random);
			int kataB = GeneratorNama.kataB(aliran, random);
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
