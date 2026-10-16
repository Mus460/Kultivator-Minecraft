package dev.kultivator.cultivation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kultivator.Kultivator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spirit veins (DESIGN section 4): the world's qi, per 4x4-chunk region and dimension, density 0..1 (1 when never
 * touched). Meditation drains it; it grows back by itself at {@link #REGEN_PER_SECOND}, computed lazily from the game
 * time stored with the region, so nothing ticks. Regions that are full again are forgotten. Only data, never blocks.
 */
public final class SpiritVeins {

	/** Density regained per second. */
	public static final double REGEN_PER_SECOND = 0.0002D;
	private static final double REGEN_PER_TICK = REGEN_PER_SECOND / 20.0D;

	public static final SavedDataType<Data> TYPE = new SavedDataType<>(Kultivator.id("spirit_veins"), Data::new, Data.CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	public static void init() {
	}

	/** Qi density 0..1 of the region around {@code pos}. */
	public static double density(ServerLevel level, BlockPos pos) {
		return data(level).density(key(pos), level.getGameTime());
	}

	/** Draws {@code amount} density from the region around {@code pos}. */
	public static void drain(ServerLevel level, BlockPos pos, double amount) {
		if (amount <= 0.0D) {
			return;
		}
		Data data = data(level);
		long key = key(pos);
		long now = level.getGameTime();
		data.set(key, data.density(key, now) - amount, now);
	}

	/** Sets the density of the region around {@code pos} (debug command). */
	public static void set(ServerLevel level, BlockPos pos, double density) {
		data(level).set(key(pos), density, level.getGameTime());
	}

	/** Region key: chunk x and z, each divided by 4, packed into one long. */
	public static long key(BlockPos pos) {
		int regionX = SectionPos.blockToSectionCoord(pos.getX()) >> 2;
		int regionZ = SectionPos.blockToSectionCoord(pos.getZ()) >> 2;
		return ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
	}

	private static Data data(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	/** One drained region: its density at {@code time} (game time); it has been regrowing since. */
	private record Region(double density, long time) {
		double at(long now) {
			return Math.min(1.0D, density + Math.max(0L, now - time) * REGEN_PER_TICK);
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

	/** The drained regions of one dimension. */
	public static final class Data extends SavedData {

		public static final Codec<Data> CODEC = Entry.CODEC.listOf().fieldOf("regions").codec()
				.xmap(Data::fromEntries, Data::toEntries);

		private final Map<Long, Region> regions = new HashMap<>();

		public Data() {
		}

		private static Data fromEntries(List<Entry> entries) {
			Data data = new Data();
			for (Entry entry : entries) {
				double density = Math.max(0.0D, Math.min(1.0D, entry.density()));
				if (density < 1.0D) {
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

		/** Density now; a region that has fully recovered is dropped. */
		double density(long key, long now) {
			Region region = regions.get(key);
			if (region == null) {
				return 1.0D;
			}
			double density = region.at(now);
			if (density >= 1.0D) {
				regions.remove(key);
				setDirty();
				return 1.0D;
			}
			return density;
		}

		void set(long key, double density, long now) {
			double clamped = Math.max(0.0D, Math.min(1.0D, density));
			if (clamped >= 1.0D) {
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
