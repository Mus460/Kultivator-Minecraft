package dev.kultivator.client.sekte.langit;

import dev.kultivator.sekte.langit.api.LangitIds;
import dev.kultivator.sekte.langit.api.LangitPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What this client knows about the sky halls near it (DESIGN 37.6.1): the last {@link LangitPayloads.Sync}, turned once
 * into {@link Hall}s with their visual anchors already parsed (waterfall notches with their outward direction, the four
 * net pillars, the Nine Heavens gate, docks, the ferry stop, the throne, the meditation points). This is the ONLY class of
 * the visuals that reads the payload records, so a change of the 37B contract touches one file. Client thread only.
 */
public final class LangitSkyState {

	/** Visuals only draw within this horizontal distance of a hall's origin. */
	public static final double DRAW_RANGE = 384.0D;

	/** A waterfall notch at the island rim: the top of the falling ribbon and the unit direction it falls away to. */
	public record Fall(BlockPos pos, int dx, int dz, int index) {
	}

	/** One sky hall as the visuals see it. */
	public static final class Hall {
		public final UUID owner;
		public final BlockPos origin;
		public final int yP;
		public final int skyFloorY;
		public final BoundingBox box;
		public final int tier;
		public final int color;
		public final int domeRadius;
		public final int flags;
		public final long seed;
		public final List<Fall> falls = new ArrayList<>();
		public final List<BlockPos> nets = new ArrayList<>();
		public final List<BlockPos> docks = new ArrayList<>();
		public final List<BlockPos> seats = new ArrayList<>();
		public @Nullable BlockPos gate;
		public @Nullable BlockPos throne;
		public @Nullable BlockPos ferry;

		Hall(LangitPayloads.SkyHall hall) {
			this.owner = hall.owner();
			this.origin = hall.origin();
			this.yP = hall.yP();
			this.skyFloorY = hall.skyFloorY();
			this.box = hall.box();
			this.tier = Mth.clamp(hall.tier(), 0, 9);
			this.color = hall.color() & 0xFFFFFF;
			this.domeRadius = Math.max(0, hall.domeRadius());
			this.flags = hall.flags();
			this.seed = hall.owner().getMostSignificantBits() ^ hall.owner().getLeastSignificantBits() * 31L;
			for (LangitPayloads.Anchor anchor : hall.anchors()) {
				String id = anchor.id();
				BlockPos pos = anchor.pos();
				if (id.startsWith("airterjun_")) {
					falls.add(fall(id, pos));
				} else if (id.startsWith("jaring_")) {
					nets.add(pos);
				} else if (id.startsWith("dermaga_")) {
					docks.add(pos);
				} else if (id.startsWith("meditasi_")) {
					seats.add(pos);
				} else if (id.equals("gerbang_langit")) {
					gate = pos;
				} else if (id.equals("tahta")) {
					throne = pos;
				} else if (id.equals("feri_atas")) {
					ferry = pos;
				}
			}
		}

		public boolean tethered() {
			return (flags & LangitIds.FLAG_TETHERED) != 0;
		}

		public boolean grandFormation() {
			return (flags & LangitIds.FLAG_GRAND_FORMATION) != 0;
		}

		public boolean net() {
			return (flags & LangitIds.FLAG_NET) != 0;
		}

		public boolean gateClosed() {
			return (flags & LangitIds.FLAG_GATE_CLOSED) != 0;
		}

		public boolean festival() {
			return (flags & LangitIds.FLAG_FESTIVAL) != 0;
		}

		/** The cloud sea height under this island: {@code clamp(skyFloorY - 10, 150, 205)} (37.6.2). */
		public double cloudY() {
			return Mth.clamp(skyFloorY - 10, 150, 205);
		}

		public double centreX() {
			return (box.minX() + box.maxX() + 1) * 0.5D;
		}

		public double centreZ() {
			return (box.minZ() + box.maxZ() + 1) * 0.5D;
		}

		/** Half the larger horizontal side of the island box (the island's "radius" for orbits and rims). */
		public double radius() {
			return Math.max(box.maxX() - box.minX() + 1, box.maxZ() - box.minZ() + 1) * 0.5D;
		}

		/** Horizontal distance squared from (x, z) to the origin. */
		public double distSqr(double x, double z) {
			double dx = x - (origin.getX() + 0.5D);
			double dz = z - (origin.getZ() + 0.5D);
			return dx * dx + dz * dz;
		}

		/** True when (x, y, z) is above the cloud sea inside the island's air volume (weather is not drawn there). */
		public boolean aboveClouds(double x, double y, double z) {
			int margin = 24;
			return x >= box.minX() - margin && x <= box.maxX() + 1 + margin && z >= box.minZ() - margin && z <= box.maxZ() + 1 + margin
					&& y >= cloudY() && y <= box.maxY() + 40;
		}

		/** True when (x, y, z) stands on or over the island itself (the "on the island" of dawn, stars and halo). */
		public boolean onIsland(double x, double y, double z) {
			int margin = 8;
			return x >= box.minX() - margin && x <= box.maxX() + 1 + margin && z >= box.minZ() - margin && z <= box.maxZ() + 1 + margin
					&& y >= skyFloorY - 4 && y <= box.maxY() + 48;
		}

		private static Fall fall(String id, BlockPos pos) {
			// airterjun_<i>_<n|e|s|w>: the last token is the outward side of the notch
			String[] parts = id.split("_");
			int index = 0;
			if (parts.length > 1) {
				try {
					index = Integer.parseInt(parts[1]);
				} catch (NumberFormatException ignored) {
					index = Math.floorMod(id.hashCode(), 16);
				}
			}
			String side = parts[parts.length - 1];
			int dx = 0;
			int dz = 0;
			switch (side) {
				case "n" -> dz = -1;
				case "s" -> dz = 1;
				case "e" -> dx = 1;
				case "w" -> dx = -1;
				default -> dz = 1;
			}
			return new Fall(pos, dx, dz, index);
		}
	}

	private static final List<Hall> HALLS = new ArrayList<>();
	private static @Nullable ClientLevel syncedLevel;

	/** The {@link LangitPayloads.Sync} receiver: replaces the list. */
	static void onSync(LangitPayloads.Sync sync) {
		HALLS.clear();
		syncedLevel = Minecraft.getInstance().level;
		for (LangitPayloads.SkyHall hall : sync.halls()) {
			if (hall != null && hall.owner() != null && hall.box() != null) {
				HALLS.add(new Hall(hall));
			}
		}
	}

	/** Every known hall of the level the player is in now (empty after a dimension change until the next sync). */
	public static List<Hall> halls() {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || level != syncedLevel) {
			return List.of();
		}
		return HALLS;
	}

	/** The nearest hall within {@link #DRAW_RANGE} of (x, z), or null. */
	public static @Nullable Hall nearest(double x, double z) {
		Hall best = null;
		double bestD = DRAW_RANGE * DRAW_RANGE;
		for (Hall hall : halls()) {
			double d = hall.distSqr(x, z);
			if (d <= bestD) {
				best = hall;
				bestD = d;
			}
		}
		return best;
	}

	/** The hall whose island volume keeps the weather away from (x, y, z), or null (the weather mixins). */
	public static @Nullable Hall shelter(double x, double y, double z) {
		for (Hall hall : halls()) {
			if (!hall.tethered() && hall.aboveClouds(x, y, z)) {
				return hall;
			}
		}
		return null;
	}

	/** The hall whose throne or origin is within 3 blocks of {@code pos}, or null. */
	public static @Nullable Hall at(BlockPos pos) {
		for (Hall hall : halls()) {
			BlockPos t = hall.throne != null ? hall.throne : hall.origin;
			if (t.distManhattan(pos) <= 3 || hall.origin.distManhattan(pos) <= 3) {
				return hall;
			}
		}
		return null;
	}

	public static boolean any() {
		return !halls().isEmpty();
	}

	static void clear() {
		HALLS.clear();
		syncedLevel = null;
	}

	private LangitSkyState() {
	}
}
