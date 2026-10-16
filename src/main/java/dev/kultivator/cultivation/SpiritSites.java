package dev.kultivator.cultivation;

import dev.kultivator.data.Dao;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Spiritual sites that speed up meditation (DESIGN section 4). Every check only reads the world. The result is cached
 * per player for 5 s (and as long as they stay on the same block), so asking often is cheap.
 */
public final class SpiritSites {

	/** Multipliers are multiplied together and capped at this. */
	public static final double CAP = 4.0D;
	private static final int CACHE_TICKS = 100;
	private static final int BEACON_RANGE = 8;
	private static final int AMETHYST_RANGE = 4;
	private static final int AMETHYST_NEEDED = 4;

	/** The kinds of spiritual site, each with its multiplier and the Dao whose comprehension it nourishes. */
	public enum Site {
		MOUNTAIN("mountain", 1.5D, Dao.SHADOW),
		CHERRY_GROVE("cherry_grove", 1.4D, Dao.LIFE),
		BEACON("beacon", 1.5D, Dao.FIRE),
		AMETHYST("amethyst", 1.3D, Dao.SWORD),
		END("end", 1.6D, Dao.SPACE),
		FULL_MOON("full_moon", 1.5D, Dao.ICE);

		private final String id;
		private final double multiplier;
		private final Dao dao;

		Site(String id, double multiplier, Dao dao) {
			this.id = id;
			this.multiplier = multiplier;
			this.dao = dao;
		}

		public String id() {
			return id;
		}

		public double multiplier() {
			return multiplier;
		}

		/** The Dao that meditating here slowly teaches. */
		public Dao dao() {
			return dao;
		}

		public Component displayName() {
			return Component.translatable("kultivator.meditation.site." + id);
		}
	}

	/** What a place offers: the combined multiplier (1.0 for nothing, at most {@link #CAP}) and the sites found. */
	public record Result(double multiplier, List<Site> sites) {
		public static final Result NONE = new Result(1.0D, List.of());

		public boolean any() {
			return !sites.isEmpty();
		}

		public List<Component> names() {
			List<Component> names = new ArrayList<>(sites.size());
			for (Site site : sites) {
				names.add(site.displayName());
			}
			return names;
		}
	}

	private record Cached(long time, BlockPos pos, ResourceKey<Level> dimension, Result result) {
	}

	private static final Map<UUID, Cached> CACHE = new HashMap<>();

	public static void init() {
	}

	/** Combined multiplier of every spiritual site the player is at, 1.0 for none, at most 4.0. */
	public static double multiplier(ServerPlayer player) {
		return current(player).multiplier();
	}

	/** Names of the spiritual sites the player is at (empty for none), for messages. */
	public static List<Component> siteNames(ServerPlayer player) {
		return current(player).names();
	}

	/** The cached result for where the player is now (re-evaluated after 5 s or when they moved to another block). */
	public static Result current(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		BlockPos pos = player.blockPosition();
		Cached cached = CACHE.get(player.getUUID());
		if (cached != null && now - cached.time() < CACHE_TICKS && now >= cached.time() && cached.pos().equals(pos)
				&& cached.dimension() == level.dimension()) {
			return cached.result();
		}
		return evaluate(player);
	}

	/** Checks the player's surroundings right now and refreshes the cache. */
	public static Result evaluate(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos feet = player.blockPosition();
		BlockPos head = feet.above();
		boolean sky = level.canSeeSky(head);
		List<Site> sites = new ArrayList<>();
		if (level.dimension() == Level.OVERWORLD && feet.getY() >= 120 && sky) {
			sites.add(Site.MOUNTAIN);
		}
		if (level.getBiome(feet).is(Biomes.CHERRY_GROVE)) {
			sites.add(Site.CHERRY_GROVE);
		}
		if (activeBeaconNear(level, feet)) {
			sites.add(Site.BEACON);
		}
		if (amethystAround(level, feet) >= AMETHYST_NEEDED) {
			sites.add(Site.AMETHYST);
		}
		if (level.dimension() == Level.END) {
			sites.add(Site.END);
		}
		if (sky && fullMoonNight(level, head)) {
			sites.add(Site.FULL_MOON);
		}
		double multiplier = 1.0D;
		for (Site site : sites) {
			multiplier *= site.multiplier();
		}
		Result result = sites.isEmpty() ? Result.NONE : new Result(Math.min(CAP, multiplier), List.copyOf(sites));
		CACHE.put(player.getUUID(), new Cached(level.getGameTime(), feet, level.dimension(), result));
		return result;
	}

	/** Drops the cached result (the player left). */
	public static void forget(ServerPlayer player) {
		CACHE.remove(player.getUUID());
	}

	/**
	 * A beacon within 8 blocks whose pyramid is complete and whose beam is not blocked: its beam sections are only
	 * reported while its levels are above 0. Only chunks that are already loaded are looked at.
	 */
	private static boolean activeBeaconNear(ServerLevel level, BlockPos pos) {
		int minChunkX = SectionPos.blockToSectionCoord(pos.getX() - BEACON_RANGE);
		int maxChunkX = SectionPos.blockToSectionCoord(pos.getX() + BEACON_RANGE);
		int minChunkZ = SectionPos.blockToSectionCoord(pos.getZ() - BEACON_RANGE);
		int maxChunkZ = SectionPos.blockToSectionCoord(pos.getZ() + BEACON_RANGE);
		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
					if (!(blockEntity instanceof BeaconBlockEntity beacon)) {
						continue;
					}
					BlockPos at = beacon.getBlockPos();
					if (Math.abs(at.getX() - pos.getX()) <= BEACON_RANGE && Math.abs(at.getY() - pos.getY()) <= BEACON_RANGE
							&& Math.abs(at.getZ() - pos.getZ()) <= BEACON_RANGE && !beacon.getBeamSections().isEmpty()) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Amethyst blocks, budding amethyst, clusters and large buds within 4 blocks of the feet (stops counting at the
	 * threshold). The pillars of an active qi gathering formation do not count: the formation already gives its own
	 * multiplier, other amethyst nearby still does.
	 */
	private static int amethystAround(ServerLevel level, BlockPos center) {
		int tier = QiFormations.tier(level, center.below());
		int count = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dx = -AMETHYST_RANGE; dx <= AMETHYST_RANGE; dx++) {
			for (int dy = -AMETHYST_RANGE; dy <= AMETHYST_RANGE; dy++) {
				for (int dz = -AMETHYST_RANGE; dz <= AMETHYST_RANGE; dz++) {
					if (dy == -1 && formationPillar(tier, dx, dz)) {
						continue;
					}
					cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					BlockState state = level.getBlockState(cursor);
					if (state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.BUDDING_AMETHYST) || state.is(Blocks.AMETHYST_CLUSTER)
							|| state.is(Blocks.LARGE_AMETHYST_BUD)) {
						count++;
						if (count >= AMETHYST_NEEDED) {
							return count;
						}
					}
				}
			}
		}
		return count;
	}

	/** True for a seat-level offset that is an amethyst pillar of a formation of {@code tier} (cardinals from 1, corners from 2). */
	private static boolean formationPillar(int tier, int dx, int dz) {
		if (tier <= 0) {
			return false;
		}
		boolean cardinal = (Math.abs(dx) == 3 && dz == 0) || (dx == 0 && Math.abs(dz) == 3);
		boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
		return cardinal || (corner && tier >= 2);
	}

	/**
	 * Night under a full moon that can be seen: a dimension with a day cycle, dark outside, no rain clouds in the way,
	 * and the moon phase environment attribute at the spot is the full moon.
	 */
	private static boolean fullMoonNight(ServerLevel level, BlockPos pos) {
		if (!level.isDarkOutside() || level.isRaining()) {
			return false;
		}
		return level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, pos) == MoonPhase.FULL_MOON;
	}

	private SpiritSites() {
	}
}
