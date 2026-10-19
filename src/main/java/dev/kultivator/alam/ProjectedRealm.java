package dev.kultivator.alam;

import dev.kultivator.util.Targeting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Mode Ringan, the projected realms (DESIGN 25.3.7, zero blocks): used when {@code secretRealmDimension=false}, when the
 * dimension is missing, or after {@code uji alam ringan nyala}. The realm "descends" around the gate as a 20-block ring of
 * glyph Displays (client) with the realm's sky grade; the chambers run as the same entity mechanics with the wall grid
 * laid over the ring. Leaving the ring for more than 5 s forfeits the run without Luka Alam. At least 70% of the ring's
 * cells must be standable ({@code Targeting.safeSpot}, read-only) or the gate is refused with a hint.
 */
public final class ProjectedRealm {

	/** Radius of the projected ring. */
	public static final double RING = 20.0D;
	/** The share of sampled ring cells that must be standable. */
	public static final double GROUND_SHARE = 0.70D;
	/** Cells are sampled every this many blocks. */
	private static final int STEP = 3;

	/** True when at least 70% of the sampled cells within the ring around {@code centre} are standable. Read-only. */
	public static boolean groundOk(ServerLevel level, ServerPlayer player, Vec3 centre) {
		int total = 0;
		int good = 0;
		int r = (int) RING;
		for (int dx = -r; dx <= r; dx += STEP) {
			for (int dz = -r; dz <= r; dz += STEP) {
				if (dx * dx + dz * dz > r * r) {
					continue;
				}
				total++;
				Vec3 at = new Vec3(Math.floor(centre.x) + dx + 0.5D, Math.floor(centre.y), Math.floor(centre.z) + dz + 0.5D);
				if (level.isLoaded(net.minecraft.core.BlockPos.containing(at)) && Targeting.safeSpot(level, player, at) != null) {
					good++;
				}
			}
		}
		return total > 0 && good >= total * GROUND_SHARE;
	}

	private ProjectedRealm() {
	}
}
