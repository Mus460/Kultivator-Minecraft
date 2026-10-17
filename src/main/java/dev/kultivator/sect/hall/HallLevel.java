package dev.kultivator.sect.hall;

import dev.kultivator.formation.Formation;
import dev.kultivator.formation.FormationData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Tingkat Aula (DESIGN 20.3.1): the hall level {@code min(renownLevel, buildingCap)} with every number the level decides,
 * and the read-only building scan that sets the building cap (Paviliun Kitab, Formasi Agung, Aula Latihan, Menara Langit
 * Membara). The scan is spread one y-slab per tick over 9 ticks; blocks are only read, never changed.
 * <p>
 * Sources: <i>A Record of a Mortal's Journey</i> ch. 153 (Yue Lu Hall, its formation and pavilion), <i>I Shall Seal the
 * Heavens</i> ch. 36-38 (the Magic Pavilion), <i>Return of the Mount Hua Sect</i> (renown, the drill ground),
 * <i>Perfect World</i> ch. 551/558 (rebuilding to rise), <i>Battle Through the Heavens</i> ch. 491 (the tower).
 */
public final class HallLevel {

	public static final int MAX_LEVEL = 9;
	/** Renown needed for levels 1-9. */
	public static final long[] RENOWN = {0L, 100L, 300L, 700L, 1500L, 3000L, 6000L, 12000L, 25000L};
	/** Renown from which one more candidate arrives ("the sect's fame returns"). */
	public static final long FAME_RENOWN = 3000L;
	public static final int MAX_CANDIDATES = 6;
	/** At this level the server hears that the sect has returned (Mount Hua). */
	public static final int ANNOUNCE_LEVEL = 5;

	// ---- building caps
	public static final int CAP_STELE = 2;
	public static final int CAP_PAVILION = 4;
	public static final int CAP_FORMATION = 6;
	public static final int CAP_DRILL = 8;
	public static final int CAP_TOWER = 9;
	public static final int PAVILION_BOOKSHELVES = 10;
	public static final int DRILL_TARGETS = 4;
	public static final int TOWER_ROOMS = 3;
	public static final int MAX_TOWER_ROOMS = 7;
	public static final double FORMATION_RANGE = 24.0D;
	public static final int SCAN_RADIUS = 16;
	public static final int TOWER_RADIUS = 32;
	public static final int SCAN_BELOW = 2;
	public static final int SCAN_ABOVE = 6;
	public static final int SCAN_TICKS = SCAN_BELOW + SCAN_ABOVE + 1;
	/** Building bits stored in {@code core.bflags}. */
	public static final int B_PAVILION = 1;
	public static final int B_FORMATION = 2;
	public static final int B_DRILL = 4;
	public static final int B_TOWER = 8;

	/** Running scans (at most one per hall). */
	private static final List<Scan> SCANS = new ArrayList<>();

	/** One spread scan: counters filled slab by slab. */
	private static final class Scan {
		final UUID owner;
		final ServerLevel level;
		final BlockPos centre;
		int slab;
		int bookshelves;
		int lecterns;
		int targets;
		int rooms;
		final List<long[]> towerSections = new ArrayList<>();
		int towerIndex;

		Scan(UUID owner, ServerLevel level, BlockPos centre) {
			this.owner = owner;
			this.level = level;
			this.centre = centre.immutable();
			int minSx = SectionPos.blockToSectionCoord(centre.getX() - TOWER_RADIUS);
			int maxSx = SectionPos.blockToSectionCoord(centre.getX() + TOWER_RADIUS);
			int minSz = SectionPos.blockToSectionCoord(centre.getZ() - TOWER_RADIUS);
			int maxSz = SectionPos.blockToSectionCoord(centre.getZ() + TOWER_RADIUS);
			int minSy = SectionPos.blockToSectionCoord(Math.max(level.getMinY(), centre.getY() - TOWER_RADIUS));
			int maxSy = SectionPos.blockToSectionCoord(Math.min(level.getMaxY(), centre.getY() + TOWER_RADIUS));
			for (int sx = minSx; sx <= maxSx; sx++) {
				for (int sz = minSz; sz <= maxSz; sz++) {
					for (int sy = minSy; sy <= maxSy; sy++) {
						towerSections.add(new long[]{sx, sy, sz});
					}
				}
			}
		}
	}

	// ================================================================== formulas (DESIGN 20.3.1 table)

	/** The highest level the renown allows. */
	public static int renownLevel(long renown) {
		int level = 1;
		for (int i = 0; i < RENOWN.length; i++) {
			if (renown >= RENOWN[i]) {
				level = i + 1;
			}
		}
		return level;
	}

	/** Renown needed for the next level, or -1 at level 9. */
	public static long nextRenown(long renown) {
		int level = renownLevel(renown);
		return level >= MAX_LEVEL ? -1L : RENOWN[level];
	}

	public static int level(long renown, int buildingCap) {
		return Math.max(1, Math.min(renownLevel(renown), Math.max(CAP_STELE, Math.min(MAX_LEVEL, buildingCap))));
	}

	/** Candidates per Penerimaan: {@code 2 + floor(L/3)}. */
	public static int candidates(int level) {
		return 2 + level / 3;
	}

	/** Disciple cap: {@code 5 + floor(L/2)}. */
	public static int cap(int level) {
		return 5 + level / 2;
	}

	/** Territory radius: {@code 20 + 4L}. */
	public static int radius(int level) {
		return 20 + 4 * level;
	}

	/** Exchange price factor: {@code 1 - 0.05 x floor((L-1)/2)}. */
	public static double price(int level) {
		return 1.0D - 0.05D * ((Math.max(1, level) - 1) / 2);
	}

	/** Duty posts: {@code 1 + floor(L/3)}. */
	public static int posts(int level) {
		return 1 + level / 3;
	}

	/** Missions per day: 3, 3, 3, 4, 4, 4, 4, 5, 5. */
	public static int missions(int level) {
		return level >= 8 ? 5 : level >= 4 ? 4 : 3;
	}

	/** Formasi Agung radius: {@code min(32, 16 + 2L)} (DESIGN 20.3.2). */
	public static double formationRadius(int level) {
		return Math.min(32.0D, 16.0D + 2.0D * level);
	}

	/** The building cap from the building bits: each building needs the ones before it. */
	public static int capFromFlags(int flags) {
		int cap = CAP_STELE;
		if ((flags & B_PAVILION) != 0) {
			cap = CAP_PAVILION;
			if ((flags & B_FORMATION) != 0) {
				cap = CAP_FORMATION;
				if ((flags & B_DRILL) != 0) {
					cap = CAP_DRILL;
					if ((flags & B_TOWER) != 0) {
						cap = CAP_TOWER;
					}
				}
			}
		}
		return cap;
	}

	// ================================================================== the building scan

	/** Starts a spread scan of the hall around {@code centre} (ignored while one runs for this owner). */
	public static void startScan(UUID owner, ServerLevel level, BlockPos centre) {
		for (Scan scan : SCANS) {
			if (scan.owner.equals(owner)) {
				return;
			}
		}
		SCANS.add(new Scan(owner, level, centre));
	}

	public static void clearScans() {
		SCANS.clear();
	}

	/** Every server tick: one slab of every running scan. */
	static void tickScans() {
		if (SCANS.isEmpty()) {
			return;
		}
		for (Iterator<Scan> it = SCANS.iterator(); it.hasNext(); ) {
			Scan scan = it.next();
			if (!scan.level.isLoaded(scan.centre)) {
				it.remove();
				continue;
			}
			scanSlab(scan);
			scanTower(scan);
			scan.slab++;
			if (scan.slab >= SCAN_TICKS) {
				it.remove();
				finish(scan);
			}
		}
	}

	private static void scanSlab(Scan scan) {
		int y = scan.centre.getY() - SCAN_BELOW + scan.slab;
		if (y < scan.level.getMinY() || y > scan.level.getMaxY()) {
			return;
		}
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		// The full box of DESIGN 20.3.1 (+-16 horizontally, a square): about 1,089 reads per slab.
		for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
			for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
				cursor.set(scan.centre.getX() + dx, y, scan.centre.getZ() + dz);
				if (!scan.level.isLoaded(cursor)) {
					continue;
				}
				BlockState state = scan.level.getBlockState(cursor);
				if (state.is(Blocks.BOOKSHELF) || state.is(Blocks.CHISELED_BOOKSHELF)) {
					scan.bookshelves++;
				} else if (state.is(Blocks.LECTERN)) {
					scan.lecterns++;
				} else if (state.is(Blocks.TARGET)) {
					scan.targets++;
				}
			}
		}
	}

	/** A share of the tower box: sections without any soul campfire are skipped at once (read-only palette check). */
	private static void scanTower(Scan scan) {
		int share = (scan.towerSections.size() + SCAN_TICKS - 1) / SCAN_TICKS;
		int end = Math.min(scan.towerSections.size(), scan.towerIndex + share);
		for (; scan.towerIndex < end && scan.rooms < MAX_TOWER_ROOMS; scan.towerIndex++) {
			long[] section = scan.towerSections.get(scan.towerIndex);
			LevelChunk chunk = scan.level.getChunkSource().getChunkNow((int) section[0], (int) section[2]);
			if (chunk == null) {
				continue;
			}
			int index = chunk.getSectionIndexFromSectionY((int) section[1]);
			if (index < 0 || index >= chunk.getSectionsCount()) {
				continue;
			}
			LevelChunkSection levelSection = chunk.getSection(index);
			if (levelSection.hasOnlyAir() || !levelSection.maybeHas(state -> state.is(Blocks.SOUL_CAMPFIRE))) {
				continue;
			}
			int baseX = SectionPos.sectionToBlockCoord((int) section[0]);
			int baseY = SectionPos.sectionToBlockCoord((int) section[1]);
			int baseZ = SectionPos.sectionToBlockCoord((int) section[2]);
			BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
			for (int lx = 0; lx < 16 && scan.rooms < MAX_TOWER_ROOMS; lx++) {
				for (int ly = 0; ly < 16 && scan.rooms < MAX_TOWER_ROOMS; ly++) {
					for (int lz = 0; lz < 16; lz++) {
						BlockState state = levelSection.getBlockState(lx, ly, lz);
						if (!state.is(Blocks.SOUL_CAMPFIRE) || !CampfireBlock.isLitCampfire(state)) {
							continue;
						}
						cursor.set(baseX + lx, baseY + ly, baseZ + lz);
						if (cursor.distSqr(scan.centre) > (double) TOWER_RADIUS * TOWER_RADIUS) {
							continue;
						}
						if (scan.level.getBlockState(cursor.below()).is(Blocks.CRYING_OBSIDIAN)) {
							scan.rooms++;
							if (scan.rooms >= MAX_TOWER_ROOMS) {
								break;
							}
						}
					}
				}
			}
		}
	}

	private static void finish(Scan scan) {
		boolean pavilion = scan.bookshelves >= PAVILION_BOOKSHELVES && scan.lecterns >= 1;
		boolean formation = hasOwnFormation(scan.level, scan.centre, scan.owner);
		boolean drill = scan.targets >= DRILL_TARGETS;
		boolean tower = scan.rooms >= TOWER_ROOMS;
		int flags = (pavilion ? B_PAVILION : 0) | (formation ? B_FORMATION : 0) | (drill ? B_DRILL : 0) | (tower ? B_TOWER : 0);
		CompoundTag counts = new CompoundTag();
		counts.putInt("books", scan.bookshelves);
		counts.putInt("lecterns", scan.lecterns);
		counts.putInt("targets", scan.targets);
		counts.putInt("rooms", scan.rooms);
		counts.putBoolean("formation", formation);
		Halls.onScanFinished(scan.owner, scan.level, scan.centre, flags, counts);
	}

	/** True when the owner has an active, powered (or guarded) Protective Formation within 24 blocks of the stele. */
	public static boolean hasOwnFormation(ServerLevel level, BlockPos stele, UUID owner) {
		FormationData data = FormationData.get(level);
		if (data.isEmpty()) {
			return false;
		}
		Vec3 centre = Vec3.atCenterOf(stele);
		for (Formation formation : data.all()) {
			if (formation.owner().equals(owner) && formation.active()
					&& Vec3.atCenterOf(formation.pos()).distanceToSqr(centre) <= FORMATION_RANGE * FORMATION_RANGE) {
				return true;
			}
		}
		return false;
	}

	private HallLevel() {
	}
}
