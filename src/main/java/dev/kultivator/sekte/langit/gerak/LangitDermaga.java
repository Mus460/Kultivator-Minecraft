package dev.kultivator.sekte.langit.gerak;

import dev.kultivator.dunia.kapal.KapalClass;
import dev.kultivator.dunia.kapal.KapalRohEntity;
import dev.kultivator.sekte.langit.api.LangitConfig;
import dev.kultivator.sekte.langit.api.SkyHallView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The sky docks of a sky sect (DESIGN 37.5.4; the cantilevered decks are 37B's {@code DermagaAwan}, T7): read from the
 * site's anchors {@code dermaga_<i>[_<p|s|b>][_<n|e|s|w>]} (ship class perahu/sekte/bahtera, outward side), so the sect
 * ship's autopilot ({@code KapalRohEntity.sailHall}) sails to a free dock that fits its hull instead of the stele: first
 * to a waypoint straight out from the dock at deck height (climbing over the island on the way, never through it), then
 * in along the dock's axis, slowing to the deck, where the ship's own docking rule (the deck is its ground) settles it.
 * One ship per dock ({@link #occupied}); a BAHTERA never takes a PERAHU dock. Without a fitting free dock the ship
 * hovers at {@code yP + 8} just off the rim ({@link #hover}). Pure reads; no block changes.
 */
public final class LangitDermaga {

	public static final String PREFIX = "dermaga_";
	/** Height over the deck anchor at which a ship comes in. */
	public static final double APPROACH_Y = 1.5D;
	/** Extra distance of the waypoint beyond the hull's length. */
	public static final double WAYPOINT_GAP = 24.0D;
	/** Height of the hover point over the plaza when no dock fits. */
	public static final int HOVER_ABOVE = 8;

	/**
	 * One dock: its anchor id, the deck centre (standing level), the largest hull it takes, the side it opens to, the
	 * waypoint straight out from it and the yaw a docked ship faces (bow towards the island).
	 */
	public record Dock(String id, BlockPos pos, KapalClass kelas, Direction out, Vec3 waypoint, float yaw) {

		public Vec3 deck() {
			return new Vec3(pos.getX() + 0.5D, pos.getY() + APPROACH_Y, pos.getZ() + 0.5D);
		}

		/** True when a ship of {@code kind} fits ({@code kind} no bigger than the dock's class). */
		public boolean fits(KapalClass kind) {
			return kind.ordinal() <= kelas.ordinal();
		}
	}

	/** Every dock of {@code owner}'s sky site (any order), empty without one. */
	public static List<Dock> docks(UUID owner) {
		List<Dock> out = new ArrayList<>();
		SkyHallView hall = LangitGerak.api().skyHall(owner);
		if (hall == null) {
			return out;
		}
		for (Map.Entry<String, BlockPos> entry : LangitGerak.anchors(owner).entrySet()) {
			String id = entry.getKey();
			if (!id.startsWith(PREFIX) || entry.getValue() == null) {
				continue;
			}
			Dock dock = parse(hall, id, entry.getValue());
			if (dock != null) {
				out.add(dock);
			}
		}
		out.sort((a, b) -> a.id().compareTo(b.id()));
		return out;
	}

	/** True when a ship other than {@code self} lies at {@code dock}. */
	public static boolean occupied(ServerLevel level, Dock dock, @Nullable Entity self) {
		double r = Math.max(4.0D, dock.kelas().length() * 0.5D);
		AABB box = new AABB(dock.pos()).inflate(r, 6.0D, r);
		return !level.getEntitiesOfClass(KapalRohEntity.class, box, ship -> ship != self && ship.isAlive()).isEmpty();
	}

	/**
	 * The dock a ship of {@code kind} at {@code from} should take at {@code owner}'s sky hall: free, fitting, the smallest
	 * class that fits, then the nearest. Null when none (or the hall is not a standing sky hall, or the sky systems are off).
	 */
	public static @Nullable Dock pick(ServerLevel level, UUID owner, KapalClass kind, Entity ship) {
		if (!LangitConfig.on()) {
			return null;
		}
		SkyHallView hall = LangitGerak.hallIn(level, owner);
		if (hall == null || hall.tethered()) {
			return null;
		}
		Dock best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Dock dock : docks(owner)) {
			if (!dock.fits(kind) || occupied(level, dock, ship)) {
				continue;
			}
			double distance = ship.position().distanceToSqr(dock.waypoint());
			boolean smaller = best != null && dock.kelas().ordinal() < best.kelas().ordinal();
			boolean same = best != null && dock.kelas() == best.kelas();
			if (best == null || smaller || same && distance < bestDistance) {
				best = dock;
				bestDistance = distance;
			}
		}
		return best;
	}

	/** Where a ship waits when no dock fits: {@code yP + 8}, 8 blocks off the island box towards {@code from}. */
	public static Vec3 hover(SkyHallView hall, Vec3 from) {
		BoundingBox box = hall.islandBox();
		double cx = hall.origin().getX() + 0.5D;
		double cz = hall.origin().getZ() + 0.5D;
		double dx = from.x - cx;
		double dz = from.z - cz;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0E-3D) {
			dx = 0.0D;
			dz = 1.0D;
		} else {
			dx /= len;
			dz /= len;
		}
		double halfX = (box.maxX() - box.minX() + 1) * 0.5D + 8.0D;
		double halfZ = (box.maxZ() - box.minZ() + 1) * 0.5D + 8.0D;
		double t = Math.min(Math.abs(dx) < 1.0E-6D ? Double.MAX_VALUE : halfX / Math.abs(dx),
				Math.abs(dz) < 1.0E-6D ? Double.MAX_VALUE : halfZ / Math.abs(dz));
		double mx = (box.minX() + box.maxX() + 1) * 0.5D;
		double mz = (box.minZ() + box.maxZ() + 1) * 0.5D;
		return new Vec3(mx + dx * t, hall.yP() + HOVER_ABOVE, mz + dz * t);
	}

	// ================================================================== anchor parsing

	/** {@code dermaga_<i>[_<p|s|b>][_<n|e|s|w>]} → a dock (class PERAHU and the radial side when a suffix is missing). */
	static @Nullable Dock parse(SkyHallView hall, String id, BlockPos pos) {
		String[] parts = id.substring(PREFIX.length()).toLowerCase(Locale.ROOT).split("_");
		if (parts.length == 0 || parts[0].isEmpty()) {
			return null;
		}
		KapalClass kelas = KapalClass.PERAHU;
		Direction out = null;
		for (int i = 1; i < parts.length; i++) {
			switch (parts[i]) {
				case "p" -> kelas = KapalClass.PERAHU;
				case "b" -> kelas = KapalClass.BAHTERA;
				case "n" -> out = Direction.NORTH;
				case "e" -> out = Direction.EAST;
				case "w" -> out = Direction.WEST;
				case "s" -> {
					// "s" is sekte as the class (first suffix) and south as the side (second suffix)
					if (i == 1 && parts.length > 2) {
						kelas = KapalClass.SEKTE;
					} else {
						out = Direction.SOUTH;
					}
				}
				default -> {
					// an unknown suffix is ignored
				}
			}
		}
		if (out == null) {
			double dx = pos.getX() - hall.origin().getX();
			double dz = pos.getZ() - hall.origin().getZ();
			out = Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? Direction.EAST : Direction.WEST) : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
		}
		double reach = kelas.length() + WAYPOINT_GAP;
		Vec3 deck = new Vec3(pos.getX() + 0.5D, pos.getY() + APPROACH_Y, pos.getZ() + 0.5D);
		Vec3 waypoint = deck.add(out.getStepX() * reach, 0.0D, out.getStepZ() * reach);
		// a docked ship faces the island: its bow points against the open side
		float yaw = out.getOpposite().toYRot();
		return new Dock(id, pos.immutable(), kelas, out, waypoint, yaw);
	}

	private LangitDermaga() {
	}
}
