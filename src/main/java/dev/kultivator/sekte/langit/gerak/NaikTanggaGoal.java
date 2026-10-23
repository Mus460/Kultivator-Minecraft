package dev.kultivator.sekte.langit.gerak;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.sekte.langit.api.LangitConfig;
import dev.kultivator.sekte.langit.api.LangitIds;
import dev.kultivator.sekte.langit.api.SkyHallView;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Pendakian Pertama, the First Climb (DESIGN 37.5.2): a disciple newly accepted at the foot of a sky sect's Tangga Awan
 * (37G puts the Acceptance Day's candidates at {@code arrival_kaki}) walks up the real spiral stair to the island as its
 * first deed. The path is read once from the world: the quartz slab treads of the helix around the stair column are
 * found ring by ring (read-only), their mean bearing per half-block gives one point per tread, and the landings (full
 * rings, {@code tangga_rehat_<i>}) are walked round on the arc. At every landing it pauses for three seconds and looks
 * out over the cloud sea. At the top it passes through the hole in the island's belly in a puff of cloud and steps out
 * at the Root Gate ({@code tangga_puncak}).
 * <p>
 * The body is moved by the server ({@link PinMurid}), so it can neither get stuck nor fall; when the stair cannot be read
 * (unloaded, not built yet, changed by hand) it is moved from landing to landing every 3 s instead, never through the
 * air. Its master hears of the start and the arrival. A master's call, a teleport, a duel or a hit ends the climb and
 * gives the body back where it stands. No block changes.
 */
public final class NaikTanggaGoal {

	public static final String FOOT = "tangga_kaki";
	public static final String TOP = "tangga_puncak";
	public static final String REST = "tangga_rehat_";
	public static final int MIN_TIER = 3;
	/** Blocks a tick along the treads (a calm walk). */
	public static final double WALK = 0.13D;
	public static final int REST_TICKS = 60;
	public static final int HOP_TICKS = 60;
	/** Radius of the walking line on the treads (the treads span 3-4). */
	public static final double TREAD_RHO = 3.5D;
	/** A new disciple this close to the stair foot starts the climb. */
	public static final double FOOT_RANGE = 12.0D;

	/** One climb. */
	private static final class Climb {
		final UUID owner;
		final DiscipleEntity body;
		final List<Vec3> path;
		final double[] along;
		final List<Vec3> rests;
		final Vec3 top;
		double s;
		int pause;
		int hop = -1;
		int t;
		Vec3 last;
		final Set<Integer> rested = new HashSet<>();

		Climb(UUID owner, DiscipleEntity body, List<Vec3> path, List<Vec3> rests, Vec3 top) {
			this.owner = owner;
			this.body = body;
			this.path = path;
			this.along = new double[path.size()];
			for (int i = 1; i < path.size(); i++) {
				along[i] = along[i - 1] + path.get(i).distanceTo(path.get(i - 1));
			}
			this.rests = rests;
			this.top = top;
			this.last = body.position();
		}

		double length() {
			return along.length == 0 ? 0.0D : along[along.length - 1];
		}

		Vec3 at(double d) {
			for (int i = 1; i < path.size(); i++) {
				if (d <= along[i]) {
					double seg = along[i] - along[i - 1];
					return path.get(i - 1).lerp(path.get(i), seg <= 1.0E-6D ? 1.0D : (d - along[i - 1]) / seg);
				}
			}
			return path.get(path.size() - 1);
		}
	}

	private static final Map<UUID, Climb> CLIMBS = new HashMap<>();
	/** Owner → disciples already seen (a body seen for the first time after the first scan is a new disciple). */
	private static final Map<UUID, Set<UUID>> SEEN = new HashMap<>();
	/** New disciples (entity id → server tick first seen) waiting {@link #SETTLE_TICKS} before they set out. */
	private static final Map<UUID, Long> PENDING = new HashMap<>();
	/** The welcome bow of an accepted candidate lasts 30 ticks; the climb starts after this. */
	public static final int SETTLE_TICKS = 60;

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(NaikTanggaGoal::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Climb climb : new ArrayList<>(CLIMBS.values())) {
				if (!climb.body.isRemoved() && climb.body.level() instanceof ServerLevel level) {
					PinMurid.land(level, climb.body, climb.body.position());
				}
			}
			CLIMBS.clear();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CLIMBS.clear();
			SEEN.clear();
			PENDING.clear();
		});
	}

	/** True while {@code disciple} climbs. */
	public static boolean climbing(DiscipleEntity disciple) {
		return CLIMBS.containsKey(disciple.getUUID());
	}

	/**
	 * Starts the climb of {@code disciple} up {@code owner}'s Tangga Awan now (the {@code uji} test and 37G); false when the
	 * hall has no stair yet or the disciple cannot go.
	 */
	public static boolean start(ServerLevel level, UUID owner, DiscipleEntity disciple) {
		SkyHallView hall = LangitGerak.hallIn(level, owner);
		if (hall == null || hall.tethered() || hall.tier() < MIN_TIER || climbing(disciple) || PatroliPedang.flying(disciple)) {
			return false;
		}
		BlockPos foot = LangitGerak.anchor(owner, FOOT);
		BlockPos top = LangitGerak.anchor(owner, TOP);
		if (foot == null || top == null) {
			return false;
		}
		List<Vec3> rests = rests(owner);
		List<Vec3> path = helix(level, hall, foot, top, disciple.position());
		Climb climb = new Climb(owner, disciple, path, rests, Vec3.atBottomCenterOf(top));
		if (path.size() < 2) {
			climb.hop = 0;
		}
		PinMurid.pin(disciple);
		CLIMBS.put(disciple.getUUID(), climb);
		tell(level, owner, Component.translatable("kultivator.sekte_langit.gerak.tangga.mulai", disciple.getDisplayName()));
		return true;
	}

	// ================================================================== noticing new disciples

	private static void tick(MinecraftServer server) {
		if (!CLIMBS.isEmpty()) {
			tickClimbs();
		}
		if (server.getTickCount() % 20 != 7 || !LangitConfig.on()) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.players().isEmpty()) {
				continue;
			}
			for (SkyHallView hall : LangitJatuh.halls(level)) {
				if (hall.tethered() || hall.tier() < MIN_TIER) {
					continue;
				}
				Set<UUID> seen = SEEN.get(hall.owner());
				boolean first = seen == null;
				if (first) {
					seen = new HashSet<>();
					SEEN.put(hall.owner(), seen);
				}
				BlockPos foot = LangitGerak.anchor(hall.owner(), FOOT);
				long now = server.getTickCount();
				for (DiscipleEntity disciple : Disciples.loaded(hall.owner())) {
					if (seen.add(disciple.soulId()) && !first && foot != null) {
						// new: let it finish its bow to the master first
						PENDING.put(disciple.getUUID(), now);
						continue;
					}
					Long since = PENDING.get(disciple.getUUID());
					if (since == null || now - since < SETTLE_TICKS) {
						continue;
					}
					PENDING.remove(disciple.getUUID());
					if (foot != null && eligible(level, hall, disciple, foot)) {
						start(level, hall.owner(), disciple);
					}
				}
			}
		}
	}

	private static boolean eligible(ServerLevel level, SkyHallView hall, DiscipleEntity disciple, BlockPos foot) {
		if (!disciple.isAlive() || disciple.level() != level || disciple.role() != DiscipleEntity.Role.DISCIPLE || disciple.isPassenger()
				|| disciple.mode().guardsPost() || disciple.isReleasing() || disciple.data().breakingThrough() || disciple.getTarget() != null
				|| DuelRules.inDuel(disciple) || PinMurid.pinned(disciple)) {
			return false;
		}
		double dx = disciple.getX() - (foot.getX() + 0.5D);
		double dz = disciple.getZ() - (foot.getZ() + 0.5D);
		return dx * dx + dz * dz <= FOOT_RANGE * FOOT_RANGE && Math.abs(disciple.getY() - foot.getY()) <= 6.0D
				&& disciple.getY() < hall.skyFloorY();
	}

	// ================================================================== climbing

	private static void tickClimbs() {
		Iterator<Map.Entry<UUID, Climb>> it = CLIMBS.entrySet().iterator();
		while (it.hasNext()) {
			Climb climb = it.next().getValue();
			DiscipleEntity body = climb.body;
			if (body.isRemoved() || !body.isAlive() || !(body.level() instanceof ServerLevel level)) {
				it.remove();
				if (!body.isRemoved() && PinMurid.pinned(body)) {
					PinMurid.unpin(body);
				}
				continue;
			}
			if (body.position().distanceToSqr(climb.last) > 9.0D || DuelRules.inDuel(body) || body.hurtTime > 0) {
				// called away, teleported, challenged or hit: it is free again where it stands
				it.remove();
				PinMurid.land(level, body, body.position());
				continue;
			}
			climb.t++;
			if (climb.hop >= 0 ? stepHops(level, climb) : stepWalk(level, climb)) {
				continue;
			}
			it.remove();
		}
	}

	/** Walks the treads; false when the climb is over. */
	private static boolean stepWalk(ServerLevel level, Climb climb) {
		if (climb.pause > 0) {
			climb.pause--;
			return true;
		}
		double length = climb.length();
		double next = Math.min(length, climb.s + WALK);
		Vec3 from = climb.at(climb.s);
		Vec3 to = climb.at(next);
		climb.s = next;
		float yaw = yawOf(from, to, climb.body.getYRot());
		PinMurid.move(climb.body, to, yaw);
		climb.last = to;
		// a landing: a short rest looking out over the clouds
		for (int i = 0; i < climb.rests.size(); i++) {
			Vec3 rest = climb.rests.get(i);
			if (!climb.rested.contains(i) && Math.abs(rest.y - to.y) < 1.5D && horizontal(rest, to) < 3.5D) {
				climb.rested.add(i);
				climb.pause = REST_TICKS;
				// away from the stair's axis (the last point of the path): out over the cloud sea
				Vec3 axis = climb.path.get(climb.path.size() - 1);
				float out = yawOf(new Vec3(axis.x, to.y, axis.z), to, yaw);
				PinMurid.move(climb.body, to, out);
				break;
			}
		}
		if (next >= length - 1.0E-6D) {
			arrive(level, climb);
			return false;
		}
		return true;
	}

	/** The fallback: landing to landing every 3 s; false when over. */
	private static boolean stepHops(ServerLevel level, Climb climb) {
		if (climb.t % HOP_TICKS != 0) {
			return true;
		}
		if (climb.hop >= climb.rests.size()) {
			arrive(level, climb);
			return false;
		}
		Vec3 rest = climb.rests.get(climb.hop++);
		BlockPos at = BlockPos.containing(rest);
		if (!level.isLoaded(at)) {
			return true;
		}
		LangitGerak.fx(level, LangitIds.FX_ARRIVE, climb.body.blockPosition(), at);
		PinMurid.move(climb.body, rest, climb.body.getYRot());
		climb.last = rest;
		return true;
	}

	private static void arrive(ServerLevel level, Climb climb) {
		DiscipleEntity body = climb.body;
		BlockPos from = body.blockPosition();
		level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.BREEZE_IDLE_AIR, SoundSource.NEUTRAL, 0.6F, 1.3F);
		PinMurid.land(level, body, climb.top);
		LangitGerak.fx(level, LangitIds.FX_ARRIVE, from, body.blockPosition());
		tell(level, climb.owner, Component.translatable("kultivator.sekte_langit.gerak.tangga.tiba", body.getDisplayName()));
	}

	private static void tell(ServerLevel level, UUID owner, Component line) {
		ServerPlayer master = level.getServer().getPlayerList().getPlayer(owner);
		if (master != null) {
			Msg.actionBar(master, line.copy().withColor(0xBFE6FF));
		}
	}

	// ================================================================== reading the stair

	/** The rest landings {@code tangga_rehat_<i>} from the bottom up (centres), with the top last. */
	private static List<Vec3> rests(UUID owner) {
		TreeMap<Integer, Vec3> sorted = new TreeMap<>();
		for (Map.Entry<String, BlockPos> entry : LangitGerak.anchors(owner).entrySet()) {
			if (entry.getKey().startsWith(REST) && entry.getValue() != null) {
				BlockPos pos = entry.getValue();
				sorted.put(pos.getY() * 64 + sorted.size(), Vec3.atBottomCenterOf(pos));
			}
		}
		return new ArrayList<>(sorted.values());
	}

	/**
	 * The walking line up the helix: from {@code start} to the first tread, one point per tread (mean bearing of the slabs
	 * at that half-block, 3.5 from the axis), arcs round the landings, then the axis under the belly hole. Empty when fewer
	 * than 6 treads were found (the caller then hops).
	 */
	static List<Vec3> helix(ServerLevel level, SkyHallView hall, BlockPos foot, BlockPos top, Vec3 start) {
		BoundingBox kaki = hall.kakiBox();
		double cx = kaki != null ? (kaki.minX() + kaki.maxX() + 1) * 0.5D : foot.getX() + 0.5D;
		double cz = kaki != null ? (kaki.minZ() + kaki.maxZ() + 1) * 0.5D : foot.getZ() + 0.5D;
		int y0 = foot.getY() - 2;
		int y1 = Math.min(level.getMaxY() - 1, Math.max(top.getY(), hall.skyFloorY()) + 2);
		TreeMap<Integer, double[]> treads = new TreeMap<>();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
		int minX = Mth.floor(cx - 5.0D);
		int maxX = Mth.floor(cx + 5.0D);
		int minZ = Mth.floor(cz - 5.0D);
		int maxZ = Mth.floor(cz + 5.0D);
		if (!level.isLoaded(BlockPos.containing(cx, y0, cz))) {
			return List.of();
		}
		for (int y = y0; y <= y1; y++) {
			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					double rx = x + 0.5D - cx;
					double rz = z + 0.5D - cz;
					double rho = Math.sqrt(rx * rx + rz * rz);
					if (rho < 2.5D || rho > 4.8D) {
						continue;
					}
					cursor.set(x, y, z);
					BlockState state = level.getBlockState(cursor);
					if (!(state.getBlock() instanceof SlabBlock)) {
						continue;
					}
					above.set(x, y + 1, z);
					if (!level.getBlockState(above).getCollisionShape(level, above).isEmpty()) {
						continue;
					}
					SlabType type = state.getValue(SlabBlock.TYPE);
					double stand = y + (type == SlabType.BOTTOM ? 0.5D : 1.0D);
					int key = (int) Math.round(stand * 2.0D);
					double[] sum = treads.computeIfAbsent(key, k -> new double[3]);
					double a = Math.atan2(rz, rx);
					sum[0] += Math.cos(a);
					sum[1] += Math.sin(a);
					sum[2] += 1.0D;
				}
			}
		}
		List<double[]> points = new ArrayList<>();
		for (Map.Entry<Integer, double[]> entry : treads.entrySet()) {
			double[] sum = entry.getValue();
			double resultant = Math.sqrt(sum[0] * sum[0] + sum[1] * sum[1]) / sum[2];
			if (resultant < 0.8D) {
				// a whole ring at this height: a landing, walked round on the arc below
				continue;
			}
			points.add(new double[]{Math.atan2(sum[1], sum[0]), entry.getKey() / 2.0D});
		}
		if (points.size() < 6) {
			return List.of();
		}
		List<Vec3> path = new ArrayList<>();
		path.add(start);
		double prevA = points.get(0)[0];
		double prevY = points.get(0)[1];
		path.add(new Vec3(cx + Math.cos(prevA) * TREAD_RHO, prevY, cz + Math.sin(prevA) * TREAD_RHO));
		for (int i = 1; i < points.size(); i++) {
			double a = points.get(i)[0];
			double y = points.get(i)[1];
			double diff = Mth.wrapDegrees(Math.toDegrees(a - prevA));
			int arcs = (int) Math.floor(Math.abs(diff) / 25.0D);
			for (int k = 1; k <= arcs; k++) {
				double f = k / (double) (arcs + 1);
				double aa = prevA + Math.toRadians(diff) * f;
				path.add(new Vec3(cx + Math.cos(aa) * TREAD_RHO, Mth.lerp(f, prevY, y), cz + Math.sin(aa) * TREAD_RHO));
			}
			path.add(new Vec3(cx + Math.cos(a) * TREAD_RHO, y, cz + Math.sin(a) * TREAD_RHO));
			prevA = a;
			prevY = y;
		}
		// under the belly hole, on the axis
		path.add(new Vec3(cx, prevY, cz));
		return path;
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static float yawOf(Vec3 from, Vec3 to, float fallback) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		if (dx * dx + dz * dz < 1.0E-6D) {
			return fallback;
		}
		return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
	}

	private NaikTanggaGoal() {
	}

	/** For tests: one climbing disciple of {@code owner}, or null. */
	public static @Nullable DiscipleEntity anyClimber(UUID owner) {
		for (Climb climb : CLIMBS.values()) {
			if (climb.owner.equals(owner)) {
				return climb.body;
			}
		}
		return null;
	}
}
