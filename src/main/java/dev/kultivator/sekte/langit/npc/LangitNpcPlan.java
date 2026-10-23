package dev.kultivator.sekte.langit.npc;

import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.diplomasi.GerbangPlan;
import dev.kultivator.sekte.langit.api.LangitConfig;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The NPC sky sects of a world (DESIGN 37.7.6): at most {@code sekteLangit.sekteNpcMaks} (2) of module D's NPC sects
 * also own an "Istana Awan", a floating palace generated (world generation only, in new chunks) right above the chunk of
 * their mountain gate. They stay the same sects of module D (name, emblem, tier, standing, war); they only gain a palace.
 * <p>
 * Adaptation: the design asked for gate sects of tier 7 or more, but {@code GerbangPlan} rolls tiers 1-5, so the palaces go
 * to the strongest planned sects instead (highest tier, then the larger gate band, then a seed hash). Pure and cheap:
 * worldgen threads call {@link #of} through {@link LangitSektePlacement}.
 */
public final class LangitNpcPlan {

	/** Lowest and highest start height of the palace (the structure JSON's uniform start height). */
	public static final int Y_MIN = 204;
	public static final int Y_MAX = 228;
	public static final int MAX = 2;

	/** One palace: its 0-based {@code index} (the structure set {@code istana_langit_<index>}) and its gate entry. */
	public record Istana(int index, GerbangPlan.Entry gate) {

		public int chunkX() {
			return gate.chunkX();
		}

		public int chunkZ() {
			return gate.chunkZ();
		}

		/** The palace column (the gate's chunk centre; the island itself lies within 34 blocks of it). */
		public BlockPos column() {
			return new BlockPos(gate.chunkX() * 16 + 8, 0, gate.chunkZ() * 16 + 8);
		}

		/**
		 * The four possible island centres: the start piece (48 × 48) sits at the chunk's min corner and turns with its
		 * random rotation, so its centre is the corner ± 24 on both axes.
		 */
		public BlockPos[] centres() {
			int x = gate.chunkX() * 16;
			int z = gate.chunkZ() * 16;
			return new BlockPos[]{new BlockPos(x + 24, 0, z + 24), new BlockPos(x - 24, 0, z + 24), new BlockPos(x - 24, 0, z - 24),
					new BlockPos(x + 24, 0, z - 24)};
		}
	}

	private static volatile List<Istana> cached = List.of();
	private static volatile long cachedSeed;
	private static volatile int cachedMax = -1;
	private static volatile GerbangPlan.Plan cachedFrom;

	/** The palaces of {@code seed} with the current config (empty when the gates or the palaces are off). */
	public static List<Istana> of(long seed) {
		int max = Math.max(0, Math.min(MAX, LangitConfig.get().sekteNpcMaks));
		if (!GerbangPlan.enabled() || max == 0) {
			return List.of();
		}
		GerbangPlan.Plan plan = GerbangPlan.of(seed);
		if (cachedFrom == plan && cachedSeed == seed && cachedMax == max) {
			return cached;
		}
		List<GerbangPlan.Entry> entries = new ArrayList<>(plan.entries());
		entries.sort(Comparator.comparingInt(GerbangPlan.Entry::tier).reversed()
				.thenComparing(Comparator.comparingInt(GerbangPlan.Entry::band).reversed())
				.thenComparingLong(e -> mix(seed, e.index())));
		List<Istana> out = new ArrayList<>();
		for (int i = 0; i < entries.size() && out.size() < max; i++) {
			out.add(new Istana(out.size(), entries.get(i)));
		}
		List<Istana> list = List.copyOf(out);
		cached = list;
		cachedSeed = seed;
		cachedMax = max;
		cachedFrom = plan;
		return list;
	}

	/** The palace {@code index} of {@code seed}, or null. */
	public static @Nullable Istana get(long seed, int index) {
		List<Istana> list = of(seed);
		return index >= 0 && index < list.size() ? list.get(index) : null;
	}

	/** The palace whose column lies within {@code range} blocks (horizontal) of (x, z), or null. */
	public static @Nullable Istana near(long seed, double x, double z, double range) {
		Istana best = null;
		double bestD = range * range;
		for (Istana istana : of(seed)) {
			double dx = istana.column().getX() + 0.5D - x;
			double dz = istana.column().getZ() + 0.5D - z;
			double d = dx * dx + dz * dz;
			if (d <= bestD) {
				bestD = d;
				best = istana;
			}
		}
		return best;
	}

	/** Module D's sect id of the palace (its planned gate column matches), or null while D knows no such sect. */
	public static @Nullable String sectId(Istana istana) {
		DiplomasiService d = SekteApi.diplomasi();
		for (String id : d.sects()) {
			BlockPos planned = d.plannedGate(id);
			if (planned != null && planned.getX() == istana.gate().blockX() && planned.getZ() == istana.gate().blockZ()) {
				return id;
			}
		}
		return null;
	}

	private static long mix(long seed, int index) {
		long h = seed * 0x9E3779B97F4A7C15L + index * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		return h ^ h >>> 29;
	}

	private LangitNpcPlan() {
	}
}
