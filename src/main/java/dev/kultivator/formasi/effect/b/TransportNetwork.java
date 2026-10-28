package dev.kultivator.formasi.effect.b;

import dev.kultivator.data.Dao;
import dev.kultivator.formasi.core.CoreRecord;
import dev.kultivator.formasi.core.CoreState;
import dev.kultivator.formasi.core.CoreTicker;
import dev.kultivator.formasi.core.CoreView;
import dev.kultivator.formasi.core.Cores;
import dev.kultivator.formasi.effect.CoreContext;
import dev.kultivator.formasi.effect.FormationEffect;
import dev.kultivator.formasi.spec.Family;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.sect.hall.Halls;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The transport nodes of one owner (DESIGN 24.4.10, M3 and L5). M3 nodes link to each other inside the territory; L5
 * nodes link to each other and to every M3 node. A trip from an M3 node costs the source 1 Daya (none with a keeper,
 * whose presence also halves the channel); from an L5 node 2 Daya per started 512 blocks (×0.5 with a Space keeper).
 * The traveller pays 10 qi unless a Space keeper stands at the source. The Daya is spent by the source core on its next
 * pulse through {@code ctx.spend}; a trip is refused when the source cannot cover it now.
 */
public final class TransportNetwork {

	public static final String M3 = "sect_transfer";
	public static final String L5 = "ancient_transfer";
	/**
	 * Formasi Pindah Awan (DESIGN 37.5.1, Sekte Langit 37M; spec of {@code CatalogLangit}): a node pair between the ground
	 * and a sky island. A trip touching an {@code awan_transfer} node is linked when one end stands in the territory and
	 * the other within {@link #AWAN_RANGE} blocks (horizontal, any height) of the stele; channel, refusals and qi as M3.
	 */
	public static final String AWAN = "awan_transfer";
	/** Horizontal reach of a ground Cloud Transfer node from the stele. */
	public static final int AWAN_RANGE = 128;
	/** Daya a Cloud Transfer trip costs its source (none with a keeper, like M3). */
	public static final double AWAN_COST = 1.0D;
	public static final double M3_COST = 1.0D;
	public static final double L5_COST_STEP = 2.0D;
	public static final double L5_STEP_BLOCKS = 512.0D;
	public static final int NAME_MAX = 24;
	/** Ext key of the unpaid trip cost (saved with the record, so a restart does not forgive it). */
	private static final String DEBT = "debt";

	/** Every transport node of {@code owner}, by id. */
	public static List<CoreView> nodes(UUID owner) {
		List<CoreView> out = new ArrayList<>();
		for (CoreView core : Cores.of(owner)) {
			FormationSpec spec = BUtil.spec(core);
			if (spec != null && spec.family() == Family.PERPINDAHAN) {
				out.add(core);
			}
		}
		out.sort(Comparator.comparingInt(CoreView::id));
		return out;
	}

	public static boolean ancient(CoreView core) {
		return L5.equals(core.spec());
	}

	/** The node's name ({@code ext.name}), else "Simpul n" by its place in the owner's list. */
	public static Component name(CoreView core) {
		String name = core.ext().getStringOr("name", "");
		if (!name.isBlank()) {
			return Component.literal(name);
		}
		List<CoreView> all = nodes(core.owner());
		int index = 1;
		for (CoreView each : all) {
			if (each.id() == core.id() && each.dim().equals(core.dim())) {
				break;
			}
			index++;
		}
		return Component.translatable("kultivator.formasib.node.default", index);
	}

	/** True when a trip from {@code from} to {@code to} is allowed by the network rules (same owner and dimension). */
	public static boolean linked(ServerLevel level, CoreView from, CoreView to) {
		if (from.id() == to.id() || !from.dim().equals(to.dim()) || !from.owner().equals(to.owner())) {
			return false;
		}
		if (awan(from) || awan(to)) {
			// 37M: one end in the (sky) hall's territory, the other within AWAN_RANGE of the stele, at any height.
			return inside(level, from) && nearHall(level, to) || inside(level, to) && nearHall(level, from);
		}
		if (ancient(from) || ancient(to)) {
			return ancient(from) && ancient(to) || inside(level, from) && inside(level, to) || ancient(from) && inside(level, to)
					|| ancient(to) && inside(level, from);
		}
		return inside(level, from) && inside(level, to);
	}

	private static boolean inside(ServerLevel level, CoreView core) {
		return Halls.inTerritory(core.owner(), level, Vec3.atCenterOf(core.eye()));
	}

	/** True for a Cloud Transfer node (37M). */
	public static boolean awan(CoreView core) {
		return AWAN.equals(core.spec());
	}

	/** {@code core} stands within {@link #AWAN_RANGE} blocks (horizontal) of its owner's stele, in the hall's dimension. */
	private static boolean nearHall(ServerLevel level, CoreView core) {
		return awanSite(core.owner(), level, core.eye());
	}

	/**
	 * True when {@code eye} may hold a Cloud Transfer node of {@code owner} outside the territory: the hall is active, in
	 * this dimension, and its stele lies within {@link #AWAN_RANGE} blocks horizontally (37M; the ground end of the pair).
	 */
	public static boolean awanSite(UUID owner, ServerLevel level, BlockPos eye) {
		Halls.HallView hall = owner == null ? null : Halls.hallOf(owner);
		if (hall == null || !hall.active() || !hall.dim().equals(level.dimension())) {
			return false;
		}
		long dx = eye.getX() - hall.pos().getX();
		long dz = eye.getZ() - hall.pos().getZ();
		return dx * dx + dz * dz <= (long) AWAN_RANGE * AWAN_RANGE;
	}

	/** Daya the source pays for a trip (before keeper rules). */
	public static double cost(CoreView from, CoreView to) {
		if (awan(from)) {
			return AWAN_COST;
		}
		if (!ancient(from)) {
			return M3_COST;
		}
		double distance = Math.sqrt(from.eye().distSqr(to.eye()));
		return Math.max(L5_COST_STEP, L5_COST_STEP * Math.ceil(distance / L5_STEP_BLOCKS));
	}

	/** Starts a trip from node {@code fromId} to {@code toId}; null when it started, else why not. */
	public static @Nullable Component travel(ServerPlayer player, int fromId, int toId) {
		return travel(player, fromId, toId, false);
	}

	/** {@code instant}: {@code uji formasi teleport} skips the channel but not the refusals. */
	public static @Nullable Component travel(ServerPlayer player, int fromId, int toId, boolean instant) {
		ServerLevel level = player.level();
		ResourceKey<Level> dim = level.dimension();
		CoreView from = Cores.byId(dim, fromId);
		CoreView to = Cores.byId(dim, toId);
		if (from == null || to == null || BUtil.spec(from) == null || BUtil.spec(from).family() != Family.PERPINDAHAN
				|| BUtil.spec(to) == null || BUtil.spec(to).family() != Family.PERPINDAHAN) {
			return Component.translatable("kultivator.formasib.trip.refuse.no_node");
		}
		if (!BUtil.member(level.getServer(), from.owner(), player)) {
			return Component.translatable("kultivator.formasib.trip.refuse.stranger");
		}
		if (TransportChannel.channeling(player)) {
			return Component.translatable("kultivator.formasib.trip.refuse.busy");
		}
		Component why = TransportChannel.refusal(player, true);
		if (why != null) {
			return why;
		}
		if (!instant) {
			Vec3 eye = Vec3.atBottomCenterOf(from.eye().above());
			double dx = player.getX() - eye.x;
			double dz = player.getZ() - eye.z;
			if (dx * dx + dz * dz > TransportChannel.STAND_RANGE * TransportChannel.STAND_RANGE || Math.abs(player.getY() - eye.y) > 2.0D) {
				return Component.translatable("kultivator.formasib.trip.refuse.stand");
			}
		}
		if (!linked(level, from, to)) {
			return Component.translatable("kultivator.formasib.trip.refuse.unlinked");
		}
		if (to.state() == CoreState.BROKEN) {
			return Component.translatable("kultivator.formasib.trip.refuse.broken");
		}
		if (destinationOff(to)) {
			return Component.translatable("kultivator.formasib.trip.refuse.dest_off");
		}
		boolean cloud = awan(from) || awan(to);
		if (!level.isLoaded(to.eye()) && cloud) {
			// 37M: the other end of a Cloud Transfer pair may lie out of view; a short ticket loads it during the channel
			dev.kultivator.dunia.DuniaTickets.touch(level, net.minecraft.world.level.ChunkPos.containing(to.eye()), 1);
		} else if (!level.isLoaded(to.eye()) && !ancient(from)) {
			return Component.translatable("kultivator.formasib.trip.refuse.unloaded");
		}
		// The source must run: a switched-off, suspended or broken node never sends anyone, keeper or not. A keeper at
		// its post carries a node without Daya ("no Daya cost"), so ACTIVE with a keeper counts as kept.
		CoreState state = from.state();
		boolean kept = from.keepers() > 0 && (state.running() || state == CoreState.ACTIVE);
		if (state == CoreState.DORMANT || state == CoreState.SUSPENDED || state == CoreState.BROKEN) {
			return Component.translatable("kultivator.formasib.trip.refuse.source_off");
		}
		if (!state.running() && !kept) {
			return Component.translatable("kultivator.formasib.trip.refuse.unpowered");
		}
		boolean space = BUtil.keeperDao(from, Dao.SPACE);
		double cost = cost(from, to);
		if (ancient(from)) {
			cost *= space ? 0.5D : 1.0D;
		} else if (kept) {
			cost = 0.0D;
		}
		double owed = from.ext().getDoubleOr(DEBT, 0.0D);
		if (cost > 0.0D && from.dayaCenti() < Math.round((cost + owed) * 100.0D)) {
			return Component.translatable("kultivator.formasib.trip.refuse.daya", Math.round(cost));
		}
		int length = ancient(from) ? TransportChannel.L5_CHANNEL : kept ? TransportChannel.M3_CHANNEL_KEPT : TransportChannel.M3_CHANNEL;
		Component refused = TransportChannel.start(player, from, to, length, ancient(from), cost, !space, instant);
		if (refused == null && cloud) {
			// 37M: the cloud pillar at both ends, the arrival effect and the first-trip advancement
			dev.kultivator.sekte.langit.gerak.PindahAwan.started(player, from.eye(), to.eye(), instant ? 0 : length);
		}
		return refused;
	}

	/** True when a destination node is switched off or suspended (it takes no traveller; BROKEN has its own line). */
	static boolean destinationOff(CoreView to) {
		return to.state() == CoreState.DORMANT || to.state() == CoreState.SUSPENDED;
	}

	/** A trip happened: the source owes {@code daya}, paid on its next pulse. */
	static void addDebt(ResourceKey<Level> dim, int coreId, double daya) {
		CoreRecord record = daya > 0.0D ? CoreTicker.record(dim, coreId) : null;
		if (record != null) {
			CompoundTag ext = record.ext.copy();
			ext.putDouble(DEBT, ext.getDoubleOr(DEBT, 0.0D) + daya);
			Cores.setExt(dim, coreId, ext);
		}
	}

	/** Renames a node (owner only); null when done, else why not. */
	public static @Nullable Component rename(ServerPlayer player, CoreView core, String raw) {
		if (!player.getUUID().equals(core.owner())) {
			return Component.translatable("kultivator.formasib.node.owner_only");
		}
		String name = raw == null ? "" : raw.replaceAll("[\\p{Cntrl}§]", "").trim();
		if (name.length() > NAME_MAX) {
			name = name.substring(0, NAME_MAX);
		}
		CompoundTag ext = core.ext().copy();
		if (name.isEmpty()) {
			ext.remove("name");
		} else {
			ext.putString("name", name);
		}
		Cores.setExt(core.dim(), core.id(), ext);
		return null;
	}

	/** The node list of {@code owner} as {@code viewer} sees it (the tablet's Simpul panel and the node map). */
	public static net.minecraft.nbt.ListTag info(ServerPlayer viewer, UUID owner) {
		net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
		ServerLevel level = viewer.level();
		for (CoreView core : nodes(owner)) {
			CompoundTag tag = new CompoundTag();
			tag.putInt("id", core.id());
			tag.putString("spec", core.spec());
			tag.putString("dim", core.dim().identifier().toString());
			tag.putInt("x", core.eye().getX());
			tag.putInt("y", core.eye().getY());
			tag.putInt("z", core.eye().getZ());
			tag.putString("name", name(core).getString());
			tag.putInt("st", core.state().ordinal());
			tag.putLong("daya", core.dayaCenti());
			tag.putBoolean("kept", core.keepers() > 0);
			boolean here = core.dim().equals(level.dimension());
			tag.putBoolean("loaded", here && level.isLoaded(core.eye()));
			tag.putBoolean("out", here && !inside(level, core));
			list.add(tag);
		}
		return list;
	}

	/** The "transport" handler: pays trip debts; always standby (each trip pays its own cost). */
	public static final class Effect implements FormationEffect {

		@Override
		public void pulse(CoreContext ctx) {
			CoreView core = ctx.core();
			double owed = core.ext().getDoubleOr(DEBT, 0.0D);
			if (owed > 0.0D) {
				// a trip is never free: what the core cannot pay now stays owed (spend takes all or nothing)
				double left = owed;
				double have = core.dayaCenti() / 100.0D;
				if (ctx.spend(owed)) {
					left = 0.0D;
				} else if (have > 0.0D && ctx.spend(have)) {
					left = owed - have;
				}
				if (left != owed) {
					CompoundTag ext = core.ext().copy();
					if (left > 1.0E-6D) {
						ext.putDouble(DEBT, left);
					} else {
						ext.remove(DEBT);
					}
					Cores.setExt(core.dim(), core.id(), ext);
				}
			}
		}

		@Override
		public boolean idle(CoreContext ctx) {
			return true;
		}

		@Override
		public void sync(CoreContext ctx, CompoundTag out) {
			out.putByte("t", (byte) (ancient(ctx.core()) ? 1 : 0));
		}
	}

	private TransportNetwork() {
	}
}
