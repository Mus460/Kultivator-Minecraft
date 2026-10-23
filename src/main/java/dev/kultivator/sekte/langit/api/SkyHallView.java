package dev.kultivator.sekte.langit.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One sky hall as every module sees it (DESIGN 37.9.1), valid from the moment its preview is confirmed (tethered) until
 * the site is gone. World coordinates throughout.
 *
 * @param owner          the hall owner (also for a tethered path-A site, whose engine key is {@code LangitKeys.skyKey})
 * @param origin         the throne cell (the stele cell of the sky site; the stele stands here after the Ascension)
 * @param yP             the plaza standing level (the plaza floor is {@code yP - 1}; equals {@code origin.getY()})
 * @param skyFloorY      the lowest world y of any island, satellite or crystal block of the full plan (not the stair)
 * @param template       the sky template 11-19
 * @param tier           completed tier 0-9 of the sky site
 * @param tethered       true while the stele still stands on the ground (path A before the Ascension, path B before
 *                       the stele is carried up)
 * @param ascent         0 not yet, 1 waiting for the stele (the ground stele was removed), 2 ascended
 * @param islandBox      the footprint column of the island (whole height envelope)
 * @param volume         the warded air volume of DESIGN 37.4.10: footprint + 8 horizontally ×
 *                       {@code [skyFloorY - 8, yP + above + 8]}
 * @param kakiBox        the stair foot column (Tangga Awan) down to the ground, or null when the plan has none yet
 * @param grandFormation the Grand Formation of the hall is active
 * @param gateClosed     the owner closed the gate (non-members are pushed out gently)
 */
public record SkyHallView(UUID owner, ResourceKey<Level> dim, BlockPos origin, int yP, int skyFloorY, int template, int tier,
		boolean tethered, int ascent, BoundingBox islandBox, BoundingBox volume, @Nullable BoundingBox kakiBox, boolean grandFormation,
		boolean gateClosed) {

	/** True when {@code p} lies in the island footprint column. */
	public boolean inIsland(Vec3 p) {
		return inside(islandBox, p);
	}

	/** True when {@code p} lies in the warded volume or the stair foot column. */
	public boolean inVolume(Vec3 p) {
		return inside(volume, p) || kakiBox != null && inside(kakiBox, p);
	}

	public boolean inIsland(BlockPos pos) {
		return islandBox.isInside(pos);
	}

	public boolean inVolume(BlockPos pos) {
		return volume.isInside(pos) || kakiBox != null && kakiBox.isInside(pos);
	}

	/** Horizontal distance from the throne. */
	public double horizontal(Vec3 p) {
		double dx = p.x - (origin.getX() + 0.5D);
		double dz = p.z - (origin.getZ() + 0.5D);
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static boolean inside(BoundingBox box, Vec3 p) {
		return p.x >= box.minX() && p.x < box.maxX() + 1 && p.y >= box.minY() && p.y < box.maxY() + 1 && p.z >= box.minZ()
				&& p.z < box.maxZ() + 1;
	}
}
