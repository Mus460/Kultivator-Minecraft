package dev.kultivator.sekte.langit.gerak;

import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.musuh.SectRaids;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.sekte.langit.api.LangitConfig;
import dev.kultivator.sekte.langit.api.LangitIds;
import dev.kultivator.sekte.langit.api.SkyHallView;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Patroli Pedang, the flying sword patrol of a sky sect (DESIGN 37.5.3; inspired by the sword-flying swordsmen of
 * <i>Shushan Jianxia Zhuan</i>): every 2-5 minutes, while a member is within 160 blocks, one or two disciples of rank
 * Murid Inti or higher and realm Golden Core or higher, posted as hall guards ({@code GUARD_HALL}), walk to the sword
 * terrace ({@code panggung_pedang}, T7), rise on their swords and circle the island outside its footprint at
 * {@code yP + 12} (a gentle wave in height) for 30-60 s, then fly back and land on the terrace.
 * <p>
 * The server moves them ({@link PinMurid}: no physics, no gravity, no AI step; a path computed once), with activity
 * {@code ACT_TERBANG_PEDANG} (25, not saved) so 37V draws the sword under their feet. Never more than {@code patroliMaks}
 * (2) per hall. A raid on the hall, a duel, a hit or a teleport ends the flight: the disciple lands on the terrace (or
 * the island's arrival) at once, never in the air. New patrols wait while the server is busy; flights in the air always
 * finish. No block changes.
 */
public final class PatroliPedang {

	public static final String STAGE = "panggung_pedang";
	public static final int MIN_TIER = 7;
	public static final int MIN_GAP = 20 * 120;
	public static final int MAX_GAP = 20 * 300;
	public static final int MIN_LOOP = 20 * 30;
	public static final int MAX_LOOP = 20 * 60;
	public static final double SPEED = 0.42D;
	public static final int LIFT_TICKS = 24;
	public static final int WALK_TIMEOUT = 600;
	public static final double ABOVE_PLAZA = 12.0D;
	public static final double PRESENCE = 160.0D;

	private static final int PH_WALK = 0;
	private static final int PH_LIFT = 1;
	private static final int PH_OUT = 2;
	private static final int PH_LOOP = 3;
	private static final int PH_BACK = 4;
	private static final int PH_DOWN = 5;

	/** One flight. */
	private static final class Flight {
		final UUID owner;
		final DiscipleEntity body;
		final Vec3 stage;
		final Vec3 lift;
		final Vec3 entry;
		final double cx;
		final double cz;
		final double radius;
		final double loopY;
		final double startAngle;
		final int dir;
		final int loopTicks;
		int phase = PH_WALK;
		int t;
		double angle;
		double travelled;
		Vec3 last;

		Flight(UUID owner, DiscipleEntity body, Vec3 stage, Vec3 entry, double cx, double cz, double radius, double loopY, int dir,
				int loopTicks) {
			this.owner = owner;
			this.body = body;
			this.stage = stage;
			this.lift = stage.add(0.0D, 3.0D, 0.0D);
			this.entry = entry;
			this.cx = cx;
			this.cz = cz;
			this.radius = radius;
			this.loopY = loopY;
			this.startAngle = Math.atan2(entry.z - cz, entry.x - cx);
			this.angle = startAngle;
			this.dir = dir;
			this.loopTicks = loopTicks;
			this.last = body.position();
		}

		boolean airborne() {
			return phase >= PH_LIFT;
		}
	}

	private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();
	private static final Map<UUID, Long> NEXT = new HashMap<>();

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(PatroliPedang::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(PatroliPedang::stopAll);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FLIGHTS.clear();
			NEXT.clear();
		});
	}

	/** True while one of {@code owner}'s disciples flies a patrol for {@code owner}'s hall. */
	public static boolean active(UUID owner) {
		for (Flight flight : FLIGHTS.values()) {
			if (flight.owner.equals(owner) && flight.airborne()) {
				return true;
			}
		}
		return false;
	}

	/** True while a disciple of {@code master} is in the air on patrol (follow veto). */
	static boolean anyFlying(UUID master) {
		for (Flight flight : FLIGHTS.values()) {
			if (flight.airborne() && master.equals(flight.body.ownerId())) {
				return true;
			}
		}
		return false;
	}

	/** True while {@code disciple} walks to the terrace or flies. */
	static boolean flying(DiscipleEntity disciple) {
		return FLIGHTS.containsKey(disciple.getUUID());
	}

	/** {@code uji}: starts a patrol at {@code owner}'s hall now (rules apply); the number of disciples sent. */
	public static int startNow(ServerLevel level, UUID owner) {
		SkyHallView hall = LangitGerak.hallIn(level, owner);
		return hall == null ? 0 : launch(level, hall, level.getRandom());
	}

	// ================================================================== scheduling

	private static void tick(MinecraftServer server) {
		if (!FLIGHTS.isEmpty()) {
			tickFlights(server);
		}
		if (server.getTickCount() % 100 != 0) {
			return;
		}
		LangitConfig cfg = LangitConfig.get();
		if (!LangitConfig.on() || !cfg.patroliPedang || cfg.patroliMaks <= 0 || LangitGerak.busy(server)) {
			return;
		}
		long now = server.getTickCount();
		for (ServerLevel level : server.getAllLevels()) {
			if (level.players().isEmpty()) {
				continue;
			}
			for (SkyHallView hall : LangitJatuh.halls(level)) {
				Long next = NEXT.get(hall.owner());
				if (next == null) {
					NEXT.put(hall.owner(), now + Mth.nextInt(level.getRandom(), MIN_GAP, MAX_GAP));
					continue;
				}
				if (now < next) {
					continue;
				}
				NEXT.put(hall.owner(), now + Mth.nextInt(level.getRandom(), MIN_GAP, MAX_GAP));
				if (memberNear(level, hall)) {
					launch(level, hall, level.getRandom());
				}
			}
		}
	}

	private static boolean memberNear(ServerLevel level, SkyHallView hall) {
		for (ServerPlayer player : level.players()) {
			if (hall.horizontal(player.position()) <= PRESENCE && LangitGerak.member(hall.owner(), player)) {
				return true;
			}
		}
		return false;
	}

	/** Sends one or two guards (up to the cap); the number sent. */
	private static int launch(ServerLevel level, SkyHallView hall, RandomSource random) {
		if (hall.tethered() || hall.tier() < MIN_TIER || SectRaids.active(hall.owner())) {
			return 0;
		}
		BlockPos stagePos = LangitGerak.anchor(hall.owner(), STAGE);
		if (stagePos == null || !level.isLoaded(stagePos)) {
			return 0;
		}
		int cap = LangitConfig.get().patroliMaks;
		int flying = 0;
		for (Flight flight : FLIGHTS.values()) {
			if (flight.owner.equals(hall.owner())) {
				flying++;
			}
		}
		int want = Math.min(cap - flying, 1 + random.nextInt(2));
		if (want <= 0) {
			return 0;
		}
		List<DiscipleEntity> pool = new ArrayList<>();
		for (DiscipleEntity disciple : Disciples.loaded(hall.owner())) {
			if (eligible(level, hall, disciple)) {
				pool.add(disciple);
			}
		}
		int sent = 0;
		Vec3 stage = Vec3.atBottomCenterOf(stagePos);
		BoundingBox box = hall.islandBox();
		double cx = (box.minX() + box.maxX() + 1) * 0.5D;
		double cz = (box.minZ() + box.maxZ() + 1) * 0.5D;
		double hx = (box.maxX() - box.minX() + 1) * 0.5D;
		double hz = (box.maxZ() - box.minZ() + 1) * 0.5D;
		double radius = Math.sqrt(hx * hx + hz * hz) + 6.0D;
		double loopY = Math.min(level.getMaxY() - 4.0D, hall.yP() + ABOVE_PLAZA);
		while (sent < want && !pool.isEmpty()) {
			DiscipleEntity body = pool.remove(random.nextInt(pool.size()));
			double a = Math.atan2(stage.z - cz, stage.x - cx);
			// the second guard enters the ring a little further round, flying the other way
			double offset = sent == 0 ? 0.0D : 0.35D;
			Vec3 entry = new Vec3(cx + Math.cos(a + offset) * radius, loopY, cz + Math.sin(a + offset) * radius);
			int dir = sent == 0 ? (random.nextBoolean() ? 1 : -1) : -1;
			Flight flight = new Flight(hall.owner(), body, stage, entry, cx, cz, radius, loopY, dir, Mth.nextInt(random, MIN_LOOP, MAX_LOOP));
			FLIGHTS.put(body.getUUID(), flight);
			body.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
			sent++;
		}
		return sent;
	}

	/** A hall guard of Murid Inti rank or higher, Golden Core or higher, idle on the island. */
	private static boolean eligible(ServerLevel level, SkyHallView hall, DiscipleEntity disciple) {
		if (!disciple.isAlive() || disciple.level() != level || disciple.role() != DiscipleEntity.Role.DISCIPLE || disciple.isPassenger()
				|| FLIGHTS.containsKey(disciple.getUUID()) || NaikTanggaGoal.climbing(disciple)) {
			return false;
		}
		if (disciple.mode() != DiscipleEntity.Mode.GUARD_HALL || disciple.activity() != DiscipleEntity.ACTIVITY_NONE || disciple.isReleasing()
				|| disciple.data().breakingThrough() || disciple.getTarget() != null || DuelRules.inDuel(disciple)) {
			return false;
		}
		if (!hall.inVolume(disciple.position())) {
			return false;
		}
		return SectRanks.rank(disciple.data()) >= SectRanks.INTI && disciple.data().realm().atLeast(Realm.GOLDEN_CORE);
	}

	// ================================================================== flying

	private static void tickFlights(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Flight>> it = FLIGHTS.entrySet().iterator();
		while (it.hasNext()) {
			Flight flight = it.next().getValue();
			DiscipleEntity body = flight.body;
			if (body.isRemoved() || !body.isAlive() || !(body.level() instanceof ServerLevel level)) {
				it.remove();
				if (!body.isRemoved() && PinMurid.pinned(body)) {
					PinMurid.unpin(body);
				}
				continue;
			}
			if (stop(level, flight)) {
				it.remove();
				end(level, flight);
				continue;
			}
			if (!step(level, flight)) {
				it.remove();
			}
		}
	}

	/** A reason to come down now. */
	private static boolean stop(ServerLevel level, Flight flight) {
		DiscipleEntity body = flight.body;
		if (SectRaids.active(flight.owner) || DuelRules.inDuel(body) || body.hurtTime > 0) {
			return true;
		}
		if (flight.airborne() && body.position().distanceToSqr(flight.last) > 9.0D) {
			// somebody moved it (a call of the master, a teleport): it is not ours any more
			return true;
		}
		return !flight.airborne() && (body.getTarget() != null || body.mode() != DiscipleEntity.Mode.GUARD_HALL);
	}

	/** Lands at once on the terrace (airborne) or just lets go (still walking). */
	private static void end(ServerLevel level, Flight flight) {
		DiscipleEntity body = flight.body;
		if (flight.airborne() && body.position().distanceToSqr(flight.last) <= 9.0D) {
			PinMurid.land(level, body, flight.stage);
		} else if (PinMurid.pinned(body)) {
			PinMurid.unpin(body);
		}
		if (body.activity() == LangitIds.ACT_TERBANG_PEDANG) {
			body.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
	}

	/** One tick of a flight; false when it is over. */
	private static boolean step(ServerLevel level, Flight flight) {
		DiscipleEntity body = flight.body;
		flight.t++;
		switch (flight.phase) {
			case PH_WALK -> {
				double d2 = body.distanceToSqr(flight.stage);
				if (d2 <= 1.8D * 1.8D) {
					PinMurid.pin(body);
					body.setActivity(LangitIds.ACT_TERBANG_PEDANG, null);
					flight.phase = PH_LIFT;
					flight.t = 0;
					flight.last = body.position();
					level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.NEUTRAL, 0.5F, 1.4F);
					return true;
				}
				if (flight.t > WALK_TIMEOUT) {
					return false;
				}
				if (flight.t % 10 == 1) {
					body.getNavigation().moveTo(flight.stage.x, flight.stage.y, flight.stage.z, 1.0D);
				}
				return true;
			}
			case PH_LIFT -> {
				double k = Math.min(1.0D, flight.t / (double) LIFT_TICKS);
				double ease = k * k * (3.0D - 2.0D * k);
				place(flight, flight.stage.lerp(flight.lift, ease), yawTo(flight.lift, flight.entry));
				if (k >= 1.0D) {
					flight.phase = PH_OUT;
					flight.t = 0;
					flight.travelled = 0.0D;
				}
				return true;
			}
			case PH_OUT -> {
				double length = flight.lift.distanceTo(flight.entry);
				flight.travelled = Math.min(length, flight.travelled + SPEED);
				double k = length < 1.0E-3D ? 1.0D : flight.travelled / length;
				Vec3 at = flight.lift.lerp(flight.entry, k);
				place(flight, at, yawTo(flight.lift, flight.entry));
				if (k >= 1.0D) {
					flight.phase = PH_LOOP;
					flight.t = 0;
					flight.angle = flight.startAngle;
				}
				return true;
			}
			case PH_LOOP -> {
				double dA = SPEED / Math.max(4.0D, flight.radius) * flight.dir;
				flight.angle += dA;
				double around = flight.angle - flight.startAngle;
				double y = flight.loopY + 2.0D * Math.sin(around * 3.0D) * Math.min(1.0D, flight.t / 40.0D);
				Vec3 at = new Vec3(flight.cx + Math.cos(flight.angle) * flight.radius, y, flight.cz + Math.sin(flight.angle) * flight.radius);
				// tangent heading
				double tx = -Math.sin(flight.angle) * flight.dir;
				double tz = Math.cos(flight.angle) * flight.dir;
				place(flight, at, (float) (Math.toDegrees(Math.atan2(tz, tx)) - 90.0D));
				if (flight.t % 80 == 0) {
					level.playSound(null, at.x, at.y, at.z, SoundEvents.PHANTOM_FLAP, SoundSource.NEUTRAL, 0.25F, 1.6F);
				}
				// back over the entry point once the time is up (a full turn at most more)
				double wrapped = Mth.wrapDegrees(Math.toDegrees(around));
				if (flight.t >= flight.loopTicks && Math.abs(wrapped) < Math.toDegrees(Math.abs(dA)) * 1.5D) {
					flight.phase = PH_BACK;
					flight.t = 0;
					flight.travelled = 0.0D;
				}
				return true;
			}
			case PH_BACK -> {
				Vec3 from = new Vec3(flight.cx + Math.cos(flight.angle) * flight.radius, flight.loopY,
						flight.cz + Math.sin(flight.angle) * flight.radius);
				double length = from.distanceTo(flight.lift);
				flight.travelled = Math.min(length, flight.travelled + SPEED);
				double k = length < 1.0E-3D ? 1.0D : flight.travelled / length;
				Vec3 at = from.lerp(flight.lift, k);
				place(flight, at, yawTo(from, flight.lift));
				if (k >= 1.0D) {
					flight.phase = PH_DOWN;
					flight.t = 0;
				}
				return true;
			}
			default -> {
				double k = Math.min(1.0D, flight.t / (double) LIFT_TICKS);
				double ease = k * k * (3.0D - 2.0D * k);
				place(flight, flight.lift.lerp(flight.stage, ease), body.getYRot());
				if (k >= 1.0D) {
					PinMurid.land(level, body, flight.stage);
					return false;
				}
				return true;
			}
		}
	}

	private static void place(Flight flight, Vec3 at, float yaw) {
		PinMurid.move(flight.body, at, yaw);
		flight.last = at;
	}

	private static float yawTo(Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		if (dx * dx + dz * dz < 1.0E-6D) {
			return 0.0F;
		}
		return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
	}

	/** Server stopping: everyone in the air lands (before the world is saved). */
	private static void stopAll(MinecraftServer server) {
		for (Flight flight : new ArrayList<>(FLIGHTS.values())) {
			if (!flight.body.isRemoved() && flight.body.level() instanceof ServerLevel level) {
				end(level, flight);
			}
		}
		FLIGHTS.clear();
	}

	private PatroliPedang() {
	}
}
