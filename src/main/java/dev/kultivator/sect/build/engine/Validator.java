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
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

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
		if (!level.getEntitiesOfClass(LivingEntity.class, around, Validator::villageDweller).isEmpty()) {
			warnings.add(warn(RED, "village"));
		}
		String structure = structureIn(level, footprint);
		if (structure != null) {
			warnings.add(warn(RED, "structure", structure));
		}
		return warnings;
	}

	/**
	 * A villager or a village golem. A wandering trader passes through and a golem a player built belongs to nobody's
	 * village, so neither makes the site red.
	 */
	private static boolean villageDweller(LivingEntity entity) {
		if (entity instanceof WanderingTrader) {
			return false;
		}
		if (entity instanceof IronGolem golem) {
			return !golem.isPlayerCreated();
		}
		return entity instanceof AbstractVillager;
	}

	/**
	 * The id of a player-relevant structure one of whose pieces actually overlaps the footprint box, or null. The
	 * footprint box already spans exactly the height the engine digs or builds ({@code -1 - below .. -1 + above} around
	 * the plaza), so a trial chamber, stronghold or ancient city deep under the site, or a structure merely referenced by
	 * a chunk of the footprint, does not count; only loaded footprint chunks are read.
	 */
	private static String structureIn(ServerLevel level, BoundingBox footprint) {
		var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Set<StructureStart> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		for (int cx = footprint.minX() >> 4; cx <= footprint.maxX() >> 4; cx++) {
			for (int cz = footprint.minZ() >> 4; cz <= footprint.maxZ() >> 4; cz++) {
				if (level.getChunkSource().getChunkNow(cx, cz) == null) {
					continue;
				}
				List<StructureStart> starts = level.structureManager().startsForStructure(cx, cz, each -> relevant(registry.getKey(each)) != null);
				for (StructureStart start : starts) {
					if (!seen.add(start) || !start.getBoundingBox().intersects(footprint)) {
						continue;
					}
					for (StructurePiece piece : start.getPieces()) {
						if (piece.getBoundingBox().intersects(footprint)) {
							return relevant(registry.getKey(start.getStructure()));
						}
					}
				}
			}
		}
		return null;
	}

	/** The path of a structure id that matters to players ({@link #STRUCTURES}), or null. */
	private static String relevant(Identifier id) {
		String path = id == null ? "" : id.getPath();
		// Other mods' ruins, temples and towers ("structory:desert_ruin") are cleared like terrain (Classifier.worldgen);
		// only their villages keep a site away. Vanilla and this mod's own ids keep the full list.
		boolean foreign = id != null && !"minecraft".equals(id.getNamespace()) && !dev.kultivator.Kultivator.MOD_ID.equals(id.getNamespace());
		for (String each : STRUCTURES) {
			if (path.contains(each) && (!foreign || "village".equals(each))) {
				return path;
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
	 * authorized; a protected one (spawner, vault, beacon, a modded non-container) is red; any other vanilla one (a sign,
	 * a bed, an empty barrel) is an amber player-made obstacle decided per position at placement. Natural ones (a tree's
	 * bee nest) are never listed (Snapshot).
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

	// ================================================================== Sekte Langit (DESIGN 37.4.8)

	/**
	 * The world rules of a sky design on the server thread: S1 dimension, S5 other sites (horizontal, the island box +
	 * {@link #SITE_MARGIN}), S10 structures, S12 spawn and S13 villages of {@link #world} on the island box; then the stair
	 * foot (S7 its chunks not shared with a footprint of the owner's ground site or of another site, S10, S12, S13 ±16 around
	 * it), S6 no other hall within {@code Halls.OTHER_HALL_RANGE} of the throne, S14 strangers in the island space (amber).
	 */
	static List<Warning> skyWorld(ServerLevel level, BoundingBox island, @Nullable BoundingBox kaki, BlockPos throne, java.util.UUID owner,
			List<BoundingBox> otherSites, @Nullable BoundingBox ownGround) {
		List<Warning> warnings = new ArrayList<>(world(level, island, otherSites));
		if (kaki != null) {
			int kx0 = kaki.minX() >> 4;
			int kx1 = kaki.maxX() >> 4;
			int kz0 = kaki.minZ() >> 4;
			int kz1 = kaki.maxZ() >> 4;
			List<BoundingBox> others = new ArrayList<>(otherSites);
			if (ownGround != null) {
				others.add(ownGround);
			}
			for (BoundingBox other : others) {
				if (kx1 >= other.minX() >> 4 && kx0 <= other.maxX() >> 4 && kz1 >= other.minZ() >> 4 && kz0 <= other.maxZ() >> 4) {
					warnings.add(warn(RED, "langit_kaki_tapak"));
					break;
				}
			}
			AABB foot = new AABB(kaki.minX(), kaki.minY(), kaki.minZ(), kaki.maxX() + 1, kaki.maxY() + 1, kaki.maxZ() + 1).inflate(16.0D);
			if (!level.getEntitiesOfClass(LivingEntity.class, foot, Validator::villageDweller).isEmpty()) {
				warnings.add(warn(RED, "village"));
			}
			String structure = structureIn(level, kaki);
			if (structure != null) {
				warnings.add(warn(RED, "structure", structure));
			}
			if (level.getServer() instanceof DedicatedServer dedicated && dedicated.spawnProtectionRadius() > 0) {
				BlockPos spawn = level.getServer().overworld().getRespawnData().globalPos().pos();
				int r = dedicated.spawnProtectionRadius();
				if (kaki.intersects(spawn.getX() - r, spawn.getZ() - r, spawn.getX() + r, spawn.getZ() + r)) {
					warnings.add(warn(RED, "spawn"));
				}
			}
		}
		// S6: no other hall (active or dormant) within 128 blocks of the throne.
		net.minecraft.server.MinecraftServer server = level.getServer();
		if (server != null) {
			double range = dev.kultivator.sect.hall.Halls.OTHER_HALL_RANGE;
			for (java.util.UUID other : dev.kultivator.sect.hall.HallData.get(server).owners()) {
				if (other.equals(owner)) {
					continue;
				}
				dev.kultivator.sect.hall.Halls.HallView hall = dev.kultivator.sect.hall.Halls.hallOf(other);
				if (hall == null || !hall.dim().equals(level.dimension())) {
					continue;
				}
				double dx = hall.pos().getX() - throne.getX();
				double dz = hall.pos().getZ() - throne.getZ();
				if (dx * dx + dz * dz <= range * range) {
					warnings.add(warn(RED, "langit_aula_lain", (int) range));
					break;
				}
			}
			// S14: strangers in the island space (the builder defers its blocks around them).
			AABB space = AABB.of(island);
			int strangers = 0;
			for (net.minecraft.server.level.ServerPlayer player : level.players()) {
				if (player.getUUID().equals(owner) || !space.contains(player.position())) {
					continue;
				}
				dev.kultivator.sect.Sect sect = dev.kultivator.sect.Sects.sectOf(player);
				if (sect == null || !sect.isMember(owner)) {
					strangers++;
				}
			}
			if (strangers > 0) {
				warnings.add(warn(AMBER, "langit_ada_orang", strangers));
			}
		}
		return warnings;
	}

	/**
	 * The terrain rules of a sky design on the frozen snapshot (template-local, relative to the throne): S2 the plaza band,
	 * S3 a clear sky over every island column ({@code top <= skyFloor - 8}), S4 the section rule against the owner's ground
	 * site ({@code groundTop}, world y of its column top; {@code Integer.MIN_VALUE} without one), S7 the stair foot (relief
	 * at most 4, water at most 30%, no lava), S8 the shift, S9 the notes. {@code force} (tests) turns S2 and the foot's
	 * relief and water amber, never S3, S4, S7 lava or S8.
	 */
	static List<Warning> skyTerrain(TemplateSpec spec, TerrainView terrain, Map<String, Integer> levels, int yP, int minY, int maxY, int groundTop,
			int dx, int dz, int offsetMax, boolean force) {
		List<Warning> warnings = new ArrayList<>();
		dev.kultivator.sect.build.blueprint.awan.AwanLayout layout = dev.kultivator.sect.build.blueprint.awan.AwanLayout.of(spec.template());
		int skyFloor = spec.skyFloor();
		int topMax = Integer.MIN_VALUE;
		int blocked = 0;
		for (int x = spec.x0(); x <= spec.x1(); x++) {
			for (int z = spec.z0(); z <= spec.z1(); z++) {
				if (!terrain.known(x, z)) {
					continue;
				}
				int top = terrain.top(x, z);
				topMax = Math.max(topMax, top);
				if (dev.kultivator.sect.build.blueprint.awan.AwanTerrain.islandTop(layout, x, z) != Integer.MIN_VALUE && top > skyFloor - 8) {
					blocked++;
				}
			}
		}
		// S2: the band, in world y.
		int bandMin = Math.max(dev.kultivator.sect.build.flow.LangitPlan.PLAZA_MIN,
				(topMax == Integer.MIN_VALUE ? minY : yP + topMax) + spec.below() + dev.kultivator.sect.build.flow.LangitPlan.CLEARANCE);
		int bandMax = maxY - 2 - spec.above();
		if (bandMin > bandMax) {
			warnings.add(warn(force ? AMBER : RED, "langit_sempit", bandMin, bandMax));
		} else if (yP < bandMin) {
			warnings.add(warn(force ? AMBER : RED, "langit_rendah", yP, bandMin));
		} else if (yP > bandMax) {
			warnings.add(warn(RED, "langit_tinggi", yP, bandMax));
		}
		// S3: a clear sky.
		if (blocked > 0) {
			warnings.add(warn(RED, "langit_terhalang", blocked));
		}
		// S4: the section rule.
		int floorWorld = yP + skyFloor;
		if (groundTop != Integer.MIN_VALUE) {
			int needed = ((groundTop >> 4) + 1) << 4;
			if (floorWorld < needed) {
				warnings.add(warn(RED, "langit_seksi", floorWorld, needed));
			}
		}
		// S7: the stair foot.
		TemplateSpec.Zone kaki = spec.kaki();
		if (kaki != null) {
			int lo = Integer.MAX_VALUE;
			int hi = Integer.MIN_VALUE;
			int cells = 0;
			int wet = 0;
			int lava = 0;
			for (int x = Math.min(kaki.x0(), kaki.x1()); x <= Math.max(kaki.x0(), kaki.x1()); x++) {
				for (int z = Math.min(kaki.z0(), kaki.z1()); z <= Math.max(kaki.z0(), kaki.z1()); z++) {
					if (!terrain.known(x, z)) {
						continue;
					}
					cells++;
					int ground = terrain.ground(x, z);
					lo = Math.min(lo, ground);
					hi = Math.max(hi, ground);
					if (terrain.water(x, z) > 0) {
						wet++;
					}
					if ((terrain.flags(x, z) & TerrainView.LAVA) != 0) {
						lava++;
					}
				}
			}
			if (cells > 0) {
				if (hi - lo > 4) {
					warnings.add(warn(force ? AMBER : RED, "langit_kaki_relief", hi - lo));
				}
				if (wet / (double) cells > WATER_LIMIT) {
					warnings.add(warn(force ? AMBER : RED, "langit_kaki_air", Math.round(100.0D * wet / cells)));
				}
				if (lava > 0) {
					warnings.add(warn(RED, "langit_kaki_lava"));
				}
				int foot = levels.getOrDefault(kaki.id(), kaki.design());
				if (yP + foot < minY + 3) {
					warnings.add(warn(RED, "langit_kaki_rendah"));
				}
				warnings.add(warn(NOTE, "langit_tangga", Math.max(0, -foot)));
			}
		}
		// S8: the shift.
		if (Math.abs(dx) > offsetMax || Math.abs(dz) > offsetMax) {
			warnings.add(warn(RED, "langit_jauh", Math.max(Math.abs(dx), Math.abs(dz)), offsetMax));
		}
		// S9: what is not checked in the air, and the island's shadow.
		warnings.add(warn(NOTE, "langit_udara"));
		warnings.add(warn(NOTE, "langit_bayangan"));
		return warnings;
	}

	/** True when the config allows the template (sectBuildMaxTemplate); a sky template counts as its ground sibling. */
	static boolean allowed(int template) {
		int rank = dev.kultivator.sect.build.api.SectTemplates.rank(template);
		return rank >= 1 && rank <= KultivatorConfig.get().sectBuildMaxTemplate;
	}

	private Validator() {
	}
}
