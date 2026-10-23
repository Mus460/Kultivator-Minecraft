package dev.kultivator.sect.build.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * A read-only picture of one sky site (DESIGN 37.9.1) for the sky API ({@code sect.build.flow.LangitApiImpl}), taken on
 * the server thread by {@link BuildEngine#skyInfos()}: whose it is (the hall owner, also for a tethered path-A site),
 * where, the template and tier, the tether state, the island column, the stair-foot column, the anchors (world) and the
 * rotation (anchor direction suffixes are template-local).
 */
public record SkySiteInfo(UUID owner, ResourceKey<Level> dim, BlockPos throne, int yP, int skyFloorY, int template, int tier, boolean tethered,
		int ascent, BoundingBox island, @Nullable BoundingBox kaki, Map<String, BlockPos> anchors, int rotation, boolean test, boolean showcase,
		int above) {
}
