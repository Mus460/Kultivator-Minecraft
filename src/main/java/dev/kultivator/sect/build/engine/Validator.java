package dev.kultivator.sect.build.engine;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.sect.build.blueprint.TemplateSpec;
import dev.kultivator.sect.build.blueprint.TerrainView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Validation of a footprint (DESIGN 21.10 "Validation"): red refuses the confirmation, amber warns, notes inform. Checks
 * needing the world (dimension, height, other sites, spawn protection, villages, structures) run on the server thread
 * ({@link #world}); terrain checks (relief, earthwork, water, cliffs, obstacles) run on the frozen snapshot off the server
 * thread ({@link #terrain}). Messages are {@code kultivator.blueprint.warn.*} keys with string arguments; {@code paksa}
 * (force, test only) turns relief, earthwork and water reds into amber, never the protected-block, container, village or
 * other-site checks.
 */
public final class Validator {

	public static final int NOTE = PreviewData.SEV_NOTE;
	public static final int AMBER = PreviewData.SEV_AMBER;
	public static final int RED = PreviewData.SEV_RED;
	/** Footprints must stay this far from other sites and showcases. */
	public static final int SITE_MARGIN = 16;
	/** Surface water share above which the site is amber / red. */
	public static final double WATER_LIMIT = 0.30D;
	public static final double DEEP_LIMIT = 0.10D;
	/** Structure ids that matter to players (a site never covers them). */
	private static final String[] STRUCTURES = {"village", "pillager_outpost", "temple", "pyramid", "igloo", "mansion", "monument", "stronghold",
			"ancient_city", "trial_chambers", "ruined_portal", "jungle", "desert", "swamp_hut", "bastion", "fortress", "end_city"};

	/** One warning line. */
	public record Warning(int sev, String key, List<String> args) {
		public CompoundTag toTag() {
			CompoundTag tag = new CompoundTag();
			tag.putInt("sev", sev);
			tag.putString("key", key);
			ListTag list = new ListTag();
			for (String arg : args) {
				list.add(StringTag.valueOf(arg));
			}
			tag.put("args", list);
			return tag;
		}
	}

	static Warning warn(int sev, String key, Object... args) {
		List<String> list = new ArrayList<>();
		for (Object arg : args) {
			list.add(String.valueOf(arg));
		}
		return new Warning(sev, "kultivator.blueprint.warn." + key, list);
	}

	/** World checks on the server thread. {@code ignore} = the site being upgraded or the owner's own site (not an overlap). */
	static List<Warning> world(ServerLevel level, BoundingBox footprint, List<BoundingBox> otherSites) {
		List<Warning> warnings = new ArrayList<>();
		if (!level.dimension().equals(Level.OVERWORLD)) {
			warnings.add(warn(RED, "dimension"));
		}
		if (footprint.minY() < level.getMinY() + 2) {
			warnings.add(warn(RED, "height_low"));
		}
		if (footprint.maxY() > level.getMaxY() - 2) {
			warnings.add(warn(RED, "height_high"));
		}
		BoundingBox wide = footprint.inflatedBy(SITE_MARGIN, 0, SITE_MARGIN);
		for (BoundingBox other : otherSites) {
			if (wide.intersects(other.minX(), other.minZ(), other.maxX(), other.maxZ())) {
				warnings.add(warn(RED, "overlap"));
				break;
			}
		}
		if (level.getServer() instanceof DedicatedServer dedicated && dedicated.spawnProtectionRadius() > 0) {
			BlockPos spawn = level.getServer().overworld().getRespawnData().globalPos().pos();
			int r = dedicated.spawnProtectionRadius();
			if (level.dimension().equals(Level.OVERWORLD) && footprint.intersects(spawn.getX() - r, spawn.getZ() - r, spawn.getX() + r, spawn.getZ() + r)) {
				warnings.add(warn(RED, "spawn"));
			}
		}
		AABB around = AABB.of(wide).inflate(0.0D, 32.0D, 0.0D);
		if (!level.getEntitiesOfClass(LivingEntity.class, around, entity -> entity instanceof AbstractVillager || entity instanceof IronGolem).isEmpty()) {
			warnings.add(warn(RED, "village"));
		}
		String structure = structureIn(level, footprint);
		if (structure != null) {
			warnings.add(warn(RED, "structure", structure));
		}
		return warnings;
	}

	/** The id of a player-relevant structure referenced in a loaded chunk of the footprint, or null. */
	private static String structureIn(ServerLevel level, BoundingBox footprint) {
		var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (int cx = footprint.minX() >> 4; cx <= footprint.maxX() >> 4; cx++) {
			for (int cz = footprint.minZ() >> 4; cz <= footprint.maxZ() >> 4; cz++) {
				if (level.getChunkSource().getChunkNow(cx, cz) == null) {
					continue;
				}
				BlockPos probe = new BlockPos((cx << 4) + 8, footprint.minY() + 1, (cz << 4) + 8);
				Map<Structure, it.unimi.dsi.fastutil.longs.LongSet> found = level.structureManager().getAllStructuresAt(probe);
				for (Structure each : found.keySet()) {
					Identifier id = registry.getKey(each);
					String path = id == null ? "" : id.getPath();
					for (String relevant : STRUCTURES) {
						if (path.contains(relevant)) {
							return path;
						}
					}
				}
			}
		}
		return null;
	}

	/** Terrain checks on the frozen snapshot (template-local view), off the server thread. */
	static List<Warning> terrain(TemplateSpec spec, TerrainView terrain, Map<String, Integer> levels, boolean force, List<BoundingBox> localBoxes) {
		List<Warning> warnings = new ArrayList<>();
		int x0 = spec.x0();
		int x1 = spec.x1();
		int z0 = spec.z0();
		int z1 = spec.z1();
		int n = 0;
		int[] grounds = new int[(x1 - x0 + 1) * (z1 - z0 + 1)];
		int wet = 0;
		int deep = 0;
		int voids = 0;
		int playerMade = 0;
		int trees = 0;
		int lava = 0;
		int columns = 0;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (!terrain.known(x, z)) {
					continue;
				}
				columns++;
				grounds[n++] = terrain.ground(x, z);
				int water = terrain.water(x, z);
				if (water > 0) {
					wet++;
				}
				if (water > 6) {
					deep++;
				}
				if (-1 - terrain.ground(x, z) > 16) {
					voids++;
				}
				int flags = terrain.flags(x, z);
				boolean inBox = inAny(localBoxes, x, z);
				if ((flags & TerrainView.PLAYER_MADE) != 0 && inBox) {
					playerMade++;
				}
				if ((flags & TerrainView.TREE) != 0 && inBox) {
					trees++;
				}
				if ((flags & TerrainView.LAVA) != 0) {
					lava++;
				}
			}
		}
		if (n > 0) {
			int[] sorted = Arrays.copyOf(grounds, n);
			Arrays.sort(sorted);
			int relief = sorted[Math.min(n - 1, (int) (n * 0.95D))] - sorted[(int) (n * 0.05D)];
			if (relief > spec.maxRelief()) {
				warnings.add(warn(force ? AMBER : RED, "relief_red", relief, spec.maxRelief()));
			} else if (relief > spec.maxRelief() * 3 / 4) {
				warnings.add(warn(AMBER, "relief", relief, spec.maxRelief()));
			}
		}
		long[] earth = TerraceFitter.earthwork(spec, terrain, levels);
		warnings.add(warn(NOTE, "earthwork_note", earth[0], earth[1]));
		if (earth[0] + earth[1] > spec.earthworkCap()) {
			warnings.add(warn(force ? AMBER : RED, "earthwork", earth[0] + earth[1], spec.earthworkCap()));
		}
		if (columns > 0) {
			double wetShare = wet / (double) columns;
			double deepShare = deep / (double) columns;
			if (wetShare > WATER_LIMIT || deepShare > DEEP_LIMIT) {
				warnings.add(warn(force ? AMBER : RED, "water_red", Math.round(wetShare * 100)));
			} else if (wet > 0) {
				warnings.add(warn(AMBER, "water", Math.round(wetShare * 100)));
			}
			double voidShare = voids / (double) columns;
			if (voidShare > 0.15D) {
				warnings.add(warn(RED, "cliffs_red", Math.round(voidShare * 100)));
			} else if (voidShare > 0.05D) {
				warnings.add(warn(AMBER, "cliffs", Math.round(voidShare * 100)));
			}
		}
		if (playerMade > 0) {
			warnings.add(warn(AMBER, "player_made", playerMade));
		}
		if (trees > 0) {
			warnings.add(warn(NOTE, "trees", trees));
		}
		if (lava > 0) {
			warnings.add(warn(AMBER, "lava", lava));
		}
		return warnings;
	}

	/**
	 * Block entities the builder did not place, by height (DESIGN 21.10 table): only those inside a component's volume
	 * ({@code boxes}, template-local, every op included) count. A container with items is red, amber once "Kosongkan" is
	 * authorized; a protected one (spawner, vault, beacon...) is red; any other (a sign, a bed, a bee nest, an empty
	 * barrel) is an amber obstacle decided per position at placement.
	 */
	static List<Warning> blockEntities(List<BoundingBox> boxes, List<Long> localPositions, List<Byte> kinds, boolean emptyAuthorized) {
		int items = 0;
		int protectedOnes = 0;
		int others = 0;
		for (int i = 0; i < localPositions.size() && i < kinds.size(); i++) {
			long pos = localPositions.get(i);
			int x = BlockPos.getX(pos);
			int y = BlockPos.getY(pos);
			int z = BlockPos.getZ(pos);
			boolean inside = false;
			for (BoundingBox box : boxes) {
				if (box.isInside(x, y, z)) {
					inside = true;
					break;
				}
			}
			if (!inside) {
				continue;
			}
			switch (kinds.get(i)) {
				case Snapshot.BE_ITEMS -> items++;
				case Snapshot.BE_PROTECTED -> protectedOnes++;
				default -> others++;
			}
		}
		List<Warning> warnings = new ArrayList<>();
		if (items > 0) {
			warnings.add(emptyAuthorized ? warn(AMBER, "containers_empty", items) : warn(RED, "containers", items));
		}
		if (protectedOnes > 0) {
			warnings.add(warn(RED, "protected", protectedOnes));
		}
		if (others > 0) {
			warnings.add(warn(AMBER, "block_entities", others));
		}
		return warnings;
	}

	private static boolean inAny(List<BoundingBox> boxes, int x, int z) {
		for (BoundingBox box : boxes) {
			if (x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ()) {
				return true;
			}
		}
		return false;
	}

	/** True when the config allows the template (sectBuildMaxTemplate). */
	static boolean allowed(int template) {
		return template >= 1 && template <= KultivatorConfig.get().sectBuildMaxTemplate;
	}

	private Validator() {
	}
}
