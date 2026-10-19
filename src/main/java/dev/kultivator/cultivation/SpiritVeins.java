package dev.kultivator.cultivation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kultivator.Kultivator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Living spirit veins (DESIGN 4, 19.3.5): the world's qi per 4x4-chunk region and dimension. Every region has a grade
 * from an O(1) seeded hash ({@link #grade}): Great (cap 2.0), Rich (1.4), normal (1.0) or Thin (0.6). Untouched regions
 * sit at their cap and nothing is stored; meditation drains a region and it grows back by itself at
 * {@link #REGEN_SHARE_PER_SECOND} of its cap per second, computed lazily from the game time stored with the region, so
 * nothing ticks. Regions that are full again are forgotten. Only data, never blocks. {@code uji urat tingkat} may override
 * a region's grade (saved with the world, honoured by every grade and cap read) until {@code uji urat tingkat reset}.
 * <p>
 * Sumber: veins make places richer, RMJ ch. 391 and 1347; grades <i>terinspirasi dari</i> Martial World ch. 879; drain
 * and refill <i>terinspirasi dari</i> ISSTH ch. 25. The numbers are game design.
 */
public final class SpiritVeins {

	/** Share of the cap regained per second (empty to full in about 42 minutes). */
	public static final double REGEN_SHARE_PER_SECOND = 0.0004D;
	/** Kept for older callers: the regeneration of a normal region per second. */
	public static final double REGEN_PER_SECOND = REGEN_SHARE_PER_SECOND;
	/** Grades: Thin, normal, Rich, Great. */
	public static final int THIN = -1;
	public static final int NORMAL = 0;
	public static final int RICH = 1;
	public static final int GREAT = 2;
	/** Hash thresholds of the grades (u below the value). */
	public static final double GREAT_BELOW = 0.02D;
	public static final double RICH_BELOW = 0.10D;
	public static final double THIN_BELOW = 0.18D;
	/** Density caps by grade. */
	public static final double CAP_THIN = 0.6D;
	public static final double CAP_NORMAL = 1.0D;
	public static final double CAP_RICH = 1.4D;
	public static final double CAP_GREAT = 2.0D;
	/** The highest cap of any region (saved entries are clamped to it on load). */
	public static final double MAX_CAP = CAP_GREAT;
	/** Region edge in blocks (4 chunks). */
	public static final int REGION_BLOCKS = 64;

	public static final SavedDataType<Data> TYPE = new SavedDataType<>(Kultivator.id("spirit_veins"), Data::new, Data.CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	public static void init() {
	}

	// ================================================================== grades

	/** Vein grade of the region around {@code pos} (DESIGN 19.3.5): −1 Thin, 0 normal, 1 Rich, 2 Great. */
	public static int grade(ServerLevel level, BlockPos pos) {
		return gradeOf(level, regionX(pos), regionZ(pos));
	}

	/** Vein grade of region {@code (regionX, regionZ)} of {@code level} (a test override first, else the seeded hash). */
	public static int gradeOf(ServerLevel level, int regionX, int regionZ) {
		Integer override = data(level).override(key(regionX, regionZ));
		return override != null ? override : naturalGrade(level, regionX, regionZ);
	}

	/** The seeded grade of a region, ignoring any test override. */
	public static int naturalGrade(ServerLevel level, int regionX, int regionZ) {
		return gradeOf(hash(level.getSeed(), dimensionHash(level), regionX, regionZ));
	}

	/**
	 * Overrides the grade of the region around {@code pos} ({@code uji urat tingkat}); null restores the natural grade. The
	 * region starts full at its new cap. Saved with the world.
	 */
	public static void setOverride(ServerLevel level, BlockPos pos, @Nullable Integer grade) {
		Data data = data(level);
		long key = key(pos);
		data.setOverride(key, grade == null ? null : Math.max(THIN, Math.min(GREAT, grade)));
		data.set(key, MAX_CAP, level.getGameTime(), cap(level, pos));
	}

	/** True when the region around {@code pos} has a test override. */
	public static boolean overridden(ServerLevel level, BlockPos pos) {
		return data(level).override(key(pos)) != null;
	}

	/** Grade of a hash value u in [0, 1). */
	public static int gradeOf(double u) {
		if (u < GREAT_BELOW) {
			return GREAT;
		}
		if (u < RICH_BELOW) {
			return RICH;
		}
		return u < THIN_BELOW ? THIN : NORMAL;
	}

	/** Density cap of the region around {@code pos} (0.6 / 1.0 / 1.4 / 2.0 by grade). */
	public static double cap(ServerLevel level, BlockPos pos) {
		return capOf(grade(level, pos));
	}

	/** The cap of a grade. */
	public static double capOf(int grade) {
		return switch (grade) {
			case GREAT -> CAP_GREAT;
			case RICH -> CAP_RICH;
			case THIN -> CAP_THIN;
			default -> CAP_NORMAL;
		};
	}

	/** "Urat Roh Agung" and so on; the normal grade has the plain "Urat Roh" name. */
	public static Component gradeName(int grade) {
		return Component.translatable("kultivator.kultivasi.vein.grade." + switch (grade) {
			case GREAT -> "great";
			case RICH -> "rich";
			case THIN -> "thin";
			default -> "normal";
		});
	}

	/** RGB colour of a grade (HUD, messages). */
	public static int gradeColor(int grade) {
		return switch (grade) {
			case GREAT -> 0xFFD54A;
			case RICH -> 0x7CFFB2;
			case THIN -> 0xB08A7A;
			default -> 0x8FE3C8;
		};
	}

	/**
	 * The seeded hash u in [0, 1) of a region: a SplitMix64 finish over the world seed, the dimension and the region
	 * coordinates. Pure and O(1); nothing is loaded.
	 */
	public static double hash(long seed, int dimension, int regionX, int regionZ) {
		long h = seed ^ (0x9E3779B97F4A7C15L * (dimension + 0x632BE5ABL));
		h ^= regionX * 0xC2B2AE3D27D4EB4FL;
		h = mix(h);
		h ^= regionZ * 0x165667B19E3779F9L;
		h = mix(h);
		return (h >>> 11) * 0x1.0p-53;
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** A stable hash of the dimension id (never {@code Object.hashCode} of a record). */
	public static int dimensionHash(ServerLevel level) {
		Identifier id = level.dimension().identifier();
		return 31 * id.getNamespace().hashCode() + id.getPath().hashCode();
	}

	public static int regionX(BlockPos pos) {
		return SectionPos.blockToSectionCoord(pos.getX()) >> 2;
	}

	public static int regionZ(BlockPos pos) {
		return SectionPos.blockToSectionCoord(pos.getZ()) >> 2;
	}

	/** The centre block of region {@code (regionX, regionZ)} at height {@code y}. */
	public static BlockPos regionCenter(int regionX, int regionZ, int y) {
		return new BlockPos(regionX * REGION_BLOCKS + REGION_BLOCKS / 2, y, regionZ * REGION_BLOCKS + REGION_BLOCKS / 2);
	}

	/** One region found by {@link #search}: its coordinates, grade and distance in regions. */
	public record Found(int regionX, int regionZ, int grade, double distance) {
	}

	/** Nearest Great and nearest Rich region within {@code radius} regions (either may be null). */
	public record SearchResult(@Nullable Found great, @Nullable Found rich, int evaluated) {
	}

	/**
	 * Hash-only search around {@code pos} (the vein compass and {@code uji urat cari}): every region whose centre lies
	 * within {@code radius} regions, nearest first by distance. At most about π·r² hash evaluations, no chunk loading.
	 */
	public static SearchResult search(ServerLevel level, BlockPos pos, int radius) {
		long seed = level.getSeed();
		int dim = dimensionHash(level);
		Data data = data(level);
		int cx = regionX(pos);
		int cz = regionZ(pos);
		Found great = null;
		Found rich = null;
		int evaluated = 0;
		int radiusSq = radius * radius;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				int distSq = dx * dx + dz * dz;
				if (distSq > radiusSq) {
					continue;
				}
				evaluated++;
				Integer override = data.override(key(cx + dx, cz + dz));
				int grade = override != null ? override : gradeOf(hash(seed, dim, cx + dx, cz + dz));
				if (grade == GREAT && (great == null || distSq < great.distance() * great.distance())) {
					great = new Found(cx + dx, cz + dz, grade, Math.sqrt(distSq));
				} else if (grade == RICH && (rich == null || distSq < rich.distance() * rich.distance())) {
					rich = new Found(cx + dx, cz + dz, grade, Math.sqrt(distSq));
				}
			}
		}
		return new SearchResult(great, rich, evaluated);
	}

	// ================================================================== density

	/** Qi density 0..2.0 of the region around {@code pos} (its cap when untouched). */
	public static double density(ServerLevel level, BlockPos pos) {
		return data(level).density(key(pos), level.getGameTime(), cap(level, pos));
	}

	/** Draws {@code amount} density from the region around {@code pos}. */
	public static void drain(ServerLevel level, BlockPos pos, double amount) {
		if (!(amount > 0.0D)) {
			return;
		}
		Data data = data(level);
		long key = key(pos);
		long now = level.getGameTime();
		double cap = cap(level, pos);
		data.set(key, data.density(key, now, cap) - amount, now, cap);
	}

	/** Sets the density of the region around {@code pos}, clamped to 0..its cap (debug and test commands). */
	public static void set(ServerLevel level, BlockPos pos, double density) {
		data(level).set(key(pos), density, level.getGameTime(), cap(level, pos));
	}

	/** Region key: chunk x and z, each divided by 4, packed into one long. */
	public static long key(BlockPos pos) {
		return key(regionX(pos), regionZ(pos));
	}

	/** Region key of region {@code (regionX, regionZ)}. */
	public static long key(int regionX, int regionZ) {
		return ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
	}

	private static Data data(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	/** One drained region: its density at {@code time} (game time); it has been regrowing since. */
	private record Region(double density, long time) {
		double at(long now, double cap) {
			double regenPerTick = REGEN_SHARE_PER_SECOND * cap / 20.0D;
			return Math.min(cap, Math.min(cap, density) + Math.max(0L, now - time) * regenPerTick);
		}
	}

	/** How a region is saved. */
	private record Entry(long key, double density, long time) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.LONG.fieldOf("key").forGetter(Entry::key),
				Codec.DOUBLE.fieldOf("density").forGetter(Entry::density),
				Codec.LONG.fieldOf("time").forGetter(Entry::time)
		).apply(instance, Entry::new));
	}

	/** How a grade override is saved. */
	private record OverrideEntry(long key, int grade) {
		static final Codec<OverrideEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.LONG.fieldOf("key").forGetter(OverrideEntry::key),
				Codec.INT.fieldOf("grade").forGetter(OverrideEntry::grade)
		).apply(instance, OverrideEntry::new));
	}

	/** The drained regions and the test grade overrides of one dimension. */
	public static final class Data extends SavedData {

		public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Entry.CODEC.listOf().fieldOf("regions").forGetter(Data::toEntries),
				OverrideEntry.CODEC.listOf().optionalFieldOf("overrides", List.of()).forGetter(Data::toOverrides)
		).apply(instance, Data::fromEntries));

		private final Map<Long, Region> regions = new HashMap<>();
		private final Map<Long, Integer> overrides = new HashMap<>();

		public Data() {
		}

		@Nullable Integer override(long key) {
			return overrides.isEmpty() ? null : overrides.get(key);
		}

		void setOverride(long key, @Nullable Integer grade) {
			if (grade == null) {
				overrides.remove(key);
			} else {
				overrides.put(key, grade);
			}
			setDirty();
		}

		private List<OverrideEntry> toOverrides() {
			List<OverrideEntry> list = new ArrayList<>(overrides.size());
			overrides.forEach((key, grade) -> list.add(new OverrideEntry(key, grade)));
			return list;
		}

		private static Data fromEntries(List<Entry> entries, List<OverrideEntry> overrides) {
			Data data = new Data();
			for (OverrideEntry entry : overrides) {
				data.overrides.put(entry.key(), Math.max(THIN, Math.min(GREAT, entry.grade())));
			}
			for (Entry entry : entries) {
				// Old saves (0..1) stay valid; the region's own cap is applied when it is read.
				double density = Math.max(0.0D, Math.min(MAX_CAP, entry.density()));
				if (Double.isFinite(density)) {
					data.regions.put(entry.key(), new Region(density, entry.time()));
				}
			}
			return data;
		}

		private List<Entry> toEntries() {
			List<Entry> entries = new ArrayList<>(regions.size());
			regions.forEach((key, region) -> entries.add(new Entry(key, region.density(), region.time())));
			return entries;
		}

		/** Density now (at most {@code cap}); a region that has fully recovered is dropped. */
		double density(long key, long now, double cap) {
			Region region = regions.get(key);
			if (region == null) {
				return cap;
			}
			double density = region.at(now, cap);
			if (density >= cap) {
				regions.remove(key);
				setDirty();
				return cap;
			}
			return density;
		}

		void set(long key, double density, long now, double cap) {
			double clamped = Math.max(0.0D, Math.min(cap, Double.isFinite(density) ? density : cap));
			if (clamped >= cap) {
				if (regions.remove(key) != null) {
					setDirty();
				}
				return;
			}
			regions.put(key, new Region(clamped, now));
			setDirty();
		}
	}

	private SpiritVeins() {
	}
}
