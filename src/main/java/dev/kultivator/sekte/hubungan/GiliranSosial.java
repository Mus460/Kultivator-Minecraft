package dev.kultivator.sekte.hubungan;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.duel.session.DuelSessions;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.SekteSuara;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Giliran Sosial (DESIGN 36.2.2): the social turn of each hall and the little gatherings of module R.
 * <ul>
 * <li><b>Tea.</b> Every {@code giliranDetik} (20 s) one hall takes its turn (the halls of online masters in rotation, at
 * most one per server tick). Two idle disciples (FOLLOW resting beside a still master, MEDITATE or STAY; no activity,
 * target, breakthrough, duel or defiance) at most {@code jarakTemu} blocks apart walk to two seats facing each other
 * ({@code SekteIds.ACT_SOCIAL}: a cup in the hand, a sip now and then) and drink tea for {@code lamaTehDetik} seconds;
 * then the pair changes by {@code 2 x kecocokan} (plus a little mood). Pairs that have not met for longest go first; a
 * grudge pair never sits down together, it only glares.</li>
 * <li><b>Rites.</b> The sworn-kin oath and the Dao companions' wedding cups take two seats before the hall stele (or
 * where the two stand, far from it); the honour duel lines both up there before the Duel Pedang begins.</li>
 * </ul>
 * Cost (36.3.9): one pair per 20 s per hall, at most 66 distance checks; the open sessions (a few) look at each other
 * every tick. Every activity this sets is cleared when its session ends, aborts or the server restarts.
 */
final class GiliranSosial {

	static final int TEA = 0;
	static final int OATH = 1;
	static final int WEDDING = 2;
	static final int DUEL = 3;

	private static final int RITE_TICKS = 200;
	private static final int DUEL_LINEUP_TICKS = 40;
	private static final int TEA_WAIT = 300;
	private static final int RITE_WAIT = 900;
	private static final double SEATED = 1.6D;

	/** One gathering of two disciples. */
	static final class Session {
		final int type;
		final UUID owner;
		final DiscipleEntity a;
		final DiscipleEntity b;
		final BlockPos spotA;
		final BlockPos spotB;
		final long started;
		long seatedAt = -1L;
		boolean done;

		Session(int type, UUID owner, DiscipleEntity a, DiscipleEntity b, BlockPos spotA, BlockPos spotB, long started) {
			this.type = type;
			this.owner = owner;
			this.a = a;
			this.b = b;
			this.spotA = spotA;
			this.spotB = spotB;
			this.started = started;
		}

		boolean involves(DiscipleEntity entity) {
			return a == entity || b == entity;
		}
	}

	private static final List<Session> SESSIONS = new ArrayList<>();
	private static final Map<UUID, Long> NEXT_TURN = new HashMap<>();
	private static final Map<UUID, Long> LAST_DAY = new HashMap<>();
	private static final Map<UUID, Long> NEXT_SYNC = new HashMap<>();
	private static int rotation;
	private static boolean registered;

	static void init() {
		if (registered) {
			return;
		}
		registered = true;
		ServerTickEvents.END_SERVER_TICK.register(GiliranSosial::tick);
	}

	static void clear() {
		SESSIONS.clear();
		NEXT_TURN.clear();
		LAST_DAY.clear();
		NEXT_SYNC.clear();
	}

	static boolean inSession(DiscipleEntity entity) {
		for (Session session : SESSIONS) {
			if (!session.done && session.involves(entity)) {
				return true;
			}
		}
		return false;
	}

	// ================================================================== the server tick

	private static void tick(MinecraftServer server) {
		if (!Hubungan.active()) {
			if (!SESSIONS.isEmpty()) {
				for (Session session : List.copyOf(SESSIONS)) {
					end(session);
				}
			}
			return;
		}
		long now = server.getTickCount();
		if (!SESSIONS.isEmpty()) {
			for (Session session : List.copyOf(SESSIONS)) {
				tickSession(session, now);
			}
			SESSIONS.removeIf(session -> session.done);
		}
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		if (players.isEmpty()) {
			return;
		}
		// one master per tick, in rotation: its tea turn when due, its daily round when the day changed
		rotation = (rotation + 1) % Math.max(1, players.size());
		ServerPlayer master = players.get(Math.min(rotation, players.size() - 1));
		UUID owner = master.getUUID();
		if (!TabelPasangan.available(owner)) {
			return;
		}
		long today = Hubungan.today();
		Long last = LAST_DAY.get(owner);
		if (last == null || last != today) {
			LAST_DAY.put(owner, today);
			if (last != null) {
				Hubungan.daily(master);
			}
		}
		long due = NEXT_TURN.getOrDefault(owner, 0L);
		if (now >= due) {
			int period = HubunganConfig.get().giliranDetik * 20;
			NEXT_TURN.put(owner, now + period + Hubungan.random().nextInt(Math.max(1, period / 4)));
			turn(master, now);
		}
		if (now >= NEXT_SYNC.getOrDefault(owner, 0L)) {
			NEXT_SYNC.put(owner, now + 100L);
			syncLovers(master);
		}
	}

	// ================================================================== tea turn

	private static void turn(ServerPlayer master, long now) {
		UUID owner = master.getUUID();
		for (Session session : SESSIONS) {
			if (session.owner.equals(owner) && session.type == TEA) {
				return;
			}
		}
		List<DiscipleEntity> idle = new ArrayList<>();
		for (DiscipleEntity disciple : Hubungan.loaded(owner).values()) {
			if (idle(disciple, master)) {
				idle.add(disciple);
			}
		}
		if (idle.size() < 2) {
			return;
		}
		double reach = HubunganConfig.get().jarakTemu;
		long today = Hubungan.today();
		DiscipleEntity bestA = null;
		DiscipleEntity bestB = null;
		double bestScore = Double.MAX_VALUE;
		for (int i = 0; i < idle.size(); i++) {
			DiscipleEntity a = idle.get(i);
			for (int j = i + 1; j < idle.size(); j++) {
				DiscipleEntity b = idle.get(j);
				if (a.level() != b.level() || a.distanceToSqr(b) > reach * reach) {
					continue;
				}
				TabelPasangan.Pair pair = TabelPasangan.find(owner, a.soulId(), b.soulId());
				if (pair != null && pair.kind() == Ikatan.DENDAM) {
					// they will not sit together; a glare now and then
					if (a.distanceToSqr(b) < 36.0D && Hubungan.random().nextInt(4) == 0) {
						a.getLookControl().setLookAt(b);
						b.getLookControl().setLookAt(a);
						HubunganPayloads.bubble(a, b, HubunganPayloads.ICON_PETIR, 60);
					}
					continue;
				}
				if (pair != null && pair.meetingsToday(today) >= 2) {
					continue;
				}
				double score = (pair == null ? -1.0D : pair.lastMeeting()) + Hubungan.random().nextDouble() * 0.9D;
				if (score < bestScore) {
					bestScore = score;
					bestA = a;
					bestB = b;
				}
			}
		}
		if (bestA == null) {
			return;
		}
		BlockPos[] spots = seatsBetween(bestA, bestB);
		if (spots == null) {
			return;
		}
		begin(new Session(TEA, owner, bestA, bestB, spots[0], spots[1], now));
	}

	/** Free to sit down for tea right now. */
	static boolean idle(DiscipleEntity d, @Nullable ServerPlayer master) {
		if (d.role() != DiscipleEntity.Role.DISCIPLE || d.activity() != 0 || d.getTarget() != null || d.isReleasing() || d.isPassenger()
				|| d.hurtTime > 0 || d.getHealth() < d.getMaxHealth() * 0.5F || d.data().breakingThrough() || d.data().deviating()
				|| DuelSessions.byEntity(d) != null || DuelRules.inDuel(d) || SekteApi.hati().defiant(d.data()) || inSession(d)) {
			return false;
		}
		return switch (d.mode()) {
			case MEDITATE, STAY -> true;
			// a follower only while it rests beside a master who stands still
			case FOLLOW -> master != null && master.level() == d.level() && master.distanceToSqr(d) < 16.0D * 16.0D && d.isMeditatingNow();
			default -> false;
		};
	}

	// ================================================================== rites

	/** Starts a rite between {@code a} and {@code b} (OATH, WEDDING or DUEL); null when it began, else why not. */
	static @Nullable Component startRite(UUID owner, int type, DiscipleEntity a, DiscipleEntity b) {
		Component why = riteProblem(a);
		if (why == null) {
			why = riteProblem(b);
		}
		if (why != null) {
			return why;
		}
		if (a.level() != b.level() || a.distanceToSqr(b) > 64.0D * 64.0D) {
			return Component.translatable("kultivator.sekte.hubungan.alasan.jauh", a.getName(), b.getName());
		}
		// a tea of either is put aside for the rite
		for (Session session : List.copyOf(SESSIONS)) {
			if (!session.done && (session.involves(a) || session.involves(b))) {
				if (session.type != TEA) {
					return Component.translatable("kultivator.sekte.hubungan.alasan.sibuk", session.involves(a) ? a.getName() : b.getName());
				}
				end(session);
			}
		}
		BlockPos[] spots = seatsAtStele(owner, a, b, type == DUEL ? 3.0D : 1.0D);
		if (spots == null) {
			spots = seatsBetween(a, b);
		}
		if (spots == null) {
			return Component.translatable("kultivator.sekte.hubungan.alasan.tempat");
		}
		MinecraftServer server = a.level().getServer();
		begin(new Session(type, owner, a, b, spots[0], spots[1], server == null ? 0L : server.getTickCount()));
		return null;
	}

	private static @Nullable Component riteProblem(DiscipleEntity d) {
		if (d.isRemoved() || !d.isAlive() || d.role() != DiscipleEntity.Role.DISCIPLE) {
			return Component.translatable("kultivator.sekte.hubungan.alasan.tidak_ada");
		}
		if (d.data().breakingThrough() || d.getTarget() != null || DuelSessions.byEntity(d) != null || DuelRules.inDuel(d) || d.isReleasing()) {
			return Component.translatable("kultivator.sekte.hubungan.alasan.sibuk", d.getName());
		}
		if (d.activity() != 0 && d.activity() != SekteIds.ACT_SOCIAL) {
			return Component.translatable("kultivator.sekte.hubungan.alasan.tugas", d.getName());
		}
		return null;
	}

	// ================================================================== sessions

	private static void begin(Session session) {
		session.a.setActivity(SekteIds.ACT_SOCIAL, session.spotA);
		session.b.setActivity(SekteIds.ACT_SOCIAL, session.spotB);
		SESSIONS.add(session);
	}

	private static void tickSession(Session s, long now) {
		if (s.done) {
			return;
		}
		if (broken(s.a) || broken(s.b)) {
			if (s.type != TEA) {
				ServerPlayer master = s.a.level().getServer() == null ? null : s.a.level().getServer().getPlayerList().getPlayer(s.owner);
				if (master != null) {
					dev.kultivator.util.Msg.chat(master, Component.translatable("kultivator.sekte.hubungan.upacara.batal").withColor(0xE0A060));
				}
				if (s.type == DUEL) {
					DuelKehormatan.lineupFailed(s.owner, s.a, s.b);
				}
			}
			end(s);
			return;
		}
		boolean seated = s.seatedAt >= 0L;
		if (seated || s.a.distanceToSqr(s.b) < 25.0D) {
			s.a.getLookControl().setLookAt(s.b, 30.0F, 30.0F);
			s.b.getLookControl().setLookAt(s.a, 30.0F, 30.0F);
		}
		if ((now - s.started) % 10L != 0L) {
			return;
		}
		if (!seated) {
			if (near(s.a, s.spotA) && near(s.b, s.spotB)) {
				s.seatedAt = now;
				onSeated(s);
			} else if (now - s.started > (s.type == TEA ? TEA_WAIT : RITE_WAIT)) {
				if (s.type != TEA && s.a.distanceToSqr(s.b) < 64.0D) {
					// close enough for the rite even if the seats were hard to reach
					s.seatedAt = now;
					onSeated(s);
				} else {
					if (s.type == DUEL) {
						DuelKehormatan.lineupFailed(s.owner, s.a, s.b);
					}
					end(s);
				}
			}
			return;
		}
		int length = switch (s.type) {
			case TEA -> HubunganConfig.get().lamaTehDetik * 20;
			case DUEL -> DUEL_LINEUP_TICKS;
			default -> RITE_TICKS;
		};
		if (s.type == TEA && (now - s.seatedAt) % 160L == 80L) {
			HubunganPayloads.bubble(Hubungan.random().nextBoolean() ? s.a : s.b, null, HubunganPayloads.ICON_TEH, 60);
		}
		if (now - s.seatedAt >= length) {
			finish(s);
		}
	}

	private static void onSeated(Session s) {
		switch (s.type) {
			case TEA -> {
				HubunganPayloads.bubble(s.a, s.b, HubunganPayloads.ICON_TEH, 80);
				SekteSuara.line(Hubungan.random().nextBoolean() ? s.a : s.b, "tea", "greet", 2400);
			}
			case OATH -> HubunganPayloads.bubble(s.a, s.b, HubunganPayloads.ICON_SIMPUL, RITE_TICKS);
			case WEDDING -> HubunganPayloads.bubble(s.a, s.b, HubunganPayloads.ICON_TERATAI, RITE_TICKS);
			case DUEL -> HubunganPayloads.bubble(s.a, s.b, HubunganPayloads.ICON_PEDANG, 60);
			default -> {
			}
		}
	}

	private static void finish(Session s) {
		end(s);
		switch (s.type) {
			case TEA -> finishTea(s);
			case OATH -> Upacara.completeOath(s.owner, s.a, s.b);
			case WEDDING -> Upacara.completeWedding(s.owner, s.a, s.b);
			case DUEL -> DuelKehormatan.begin(s.owner, s.a, s.b);
			default -> {
			}
		}
	}

	private static void finishTea(Session s) {
		int compat = Kecocokan.compatibility(s.a.data(), s.b.data());
		int mood = Hubungan.random().nextInt(4) - 1;
		int delta = 2 * compat + mood;
		int applied = Hubungan.change(s.owner, s.a.soulId(), s.b.soulId(), delta, SekteIds.R + ".teh",
				Hubungan.CAP | Hubungan.TRAITS | Hubungan.MEMORY | Hubungan.MEETING);
		if (applied >= 5) {
			HubunganPayloads.bubble(s.a, s.b, HubunganPayloads.ICON_HATI, 60);
		} else if (applied > 0) {
			HubunganPayloads.bubble(s.a, s.b, HubunganPayloads.ICON_BINTANG, 50);
		} else if (applied <= -3) {
			HubunganPayloads.bubble(s.a, s.b, HubunganPayloads.ICON_PETIR, 60);
		}
	}

	/** Ends a session: both get their own activity back (only if it is still the tea or rite of this session). */
	private static void end(Session s) {
		s.done = true;
		release(s.a);
		release(s.b);
	}

	private static void release(DiscipleEntity d) {
		if (!d.isRemoved() && d.activity() == SekteIds.ACT_SOCIAL) {
			d.setActivity(0, null);
		}
	}

	private static boolean broken(DiscipleEntity d) {
		return d.isRemoved() || !d.isAlive() || d.activity() != SekteIds.ACT_SOCIAL || d.getTarget() != null || d.hurtTime > 0
				|| d.data().breakingThrough() || d.isReleasing() || DuelSessions.byEntity(d) != null;
	}

	private static boolean near(DiscipleEntity d, BlockPos spot) {
		return d.distanceToSqr(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D) <= SEATED * SEATED;
	}

	// ================================================================== seats

	/** Two seats facing each other between {@code a} and {@code b}, about two blocks apart, or null. */
	static BlockPos[] seatsBetween(DiscipleEntity a, DiscipleEntity b) {
		Level level = a.level();
		Vec3 pa = a.position();
		Vec3 pb = b.position();
		Vec3 mid = pa.add(pb).scale(0.5D);
		Vec3 dir = new Vec3(pb.x - pa.x, 0.0D, pb.z - pa.z);
		if (dir.lengthSqr() < 1.0E-4D) {
			dir = Vec3.directionFromRotation(0.0F, a.getYRot());
			dir = new Vec3(dir.x, 0.0D, dir.z);
		}
		dir = dir.normalize();
		return pairOfSeats(level, mid, dir, 1.0D);
	}

	/**
	 * Two seats before the hall stele, {@code half} blocks to each side of the line from the stele to the two disciples,
	 * three blocks out; null when the stele is in another dimension or more than 96 blocks away.
	 */
	static BlockPos[] seatsAtStele(UUID owner, DiscipleEntity a, DiscipleEntity b, double half) {
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null || !a.level().dimension().equals(hall.dim())) {
			return null;
		}
		Vec3 stele = Vec3.atBottomCenterOf(hall.pos());
		Vec3 mid = a.position().add(b.position()).scale(0.5D);
		if (mid.distanceToSqr(stele) > 96.0D * 96.0D) {
			return null;
		}
		Vec3 front = new Vec3(mid.x - stele.x, 0.0D, mid.z - stele.z);
		front = front.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : front.normalize();
		Vec3 center = stele.add(front.scale(3.0D));
		Vec3 side = new Vec3(-front.z, 0.0D, front.x);
		return pairOfSeats(a.level(), center, side, half);
	}

	private static BlockPos[] pairOfSeats(Level level, Vec3 center, Vec3 axis, double half) {
		BlockPos first = standable(level, BlockPos.containing(center.subtract(axis.scale(half))));
		BlockPos second = standable(level, BlockPos.containing(center.add(axis.scale(half))));
		if (first == null || second == null) {
			return null;
		}
		if (first.equals(second)) {
			BlockPos moved = standable(level, first.offset((int) Math.round(axis.x * 2.0D), 0, (int) Math.round(axis.z * 2.0D)));
			if (moved == null || moved.equals(first)) {
				return null;
			}
			second = moved;
		}
		return new BlockPos[]{first, second};
	}

	/** A block a body can stand in at or near {@code pos} (two free blocks over a sturdy floor, no fluid), or null. */
	static @Nullable BlockPos standable(Level level, BlockPos pos) {
		int[] dys = {0, 1, -1, 2, -2};
		for (int r = 0; r <= 1; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (r > 0 && Math.abs(dx) != r && Math.abs(dz) != r) {
						continue;
					}
					for (int dy : dys) {
						BlockPos at = pos.offset(dx, dy, dz);
						if (free(level, at) && free(level, at.above()) && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
							return at.immutable();
						}
					}
				}
			}
		}
		return null;
	}

	private static boolean free(Level level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
	}

	// ================================================================== lovers' motes

	/** Every 5 s: the lover and Dao-companion bodies of this master near players (the client draws pink motes). */
	private static void syncLovers(ServerPlayer master) {
		UUID owner = master.getUUID();
		Map<UUID, DiscipleEntity> bodies = null;
		List<int[]> found = new ArrayList<>();
		for (TabelPasangan.Pair pair : TabelPasangan.all(owner)) {
			Ikatan kind = pair.kind();
			if (kind != Ikatan.KEKASIH && kind != Ikatan.PASANGAN_DAO) {
				continue;
			}
			if (bodies == null) {
				bodies = Hubungan.loaded(owner);
			}
			DiscipleEntity a = bodies.get(pair.a);
			DiscipleEntity b = bodies.get(pair.b);
			if (a != null && b != null && a.level() == b.level() && a.distanceToSqr(b) < 24.0D * 24.0D) {
				found.add(new int[]{a.getId(), b.getId(), kind == Ikatan.PASANGAN_DAO ? 1 : 0});
				if (found.size() >= 16) {
					break;
				}
			}
		}
		if (found.isEmpty() || bodies == null) {
			return;
		}
		int[] ids = new int[found.size() * 2];
		int[] kinds = new int[found.size()];
		for (int i = 0; i < found.size(); i++) {
			ids[2 * i] = found.get(i)[0];
			ids[2 * i + 1] = found.get(i)[1];
			kinds[i] = found.get(i)[2];
		}
		HubunganPayloads.Pasangan payload = new HubunganPayloads.Pasangan(ids, kinds);
		DiscipleEntity any = null;
		for (DiscipleEntity body : bodies.values()) {
			if (body.getId() == ids[0]) {
				any = body;
				break;
			}
		}
		if (any != null && any.level() instanceof ServerLevel level) {
			for (ServerPlayer player : PlayerLookup.around(level, any.position(), 64.0D)) {
				HubunganPayloads.send(player, payload);
			}
		}
	}

	/** {@code uji perguruan hubungan temu}: a tea now, whatever the turn says (null when it began). */
	static @Nullable Component forceTea(UUID owner, DiscipleEntity a, DiscipleEntity b) {
		for (Iterator<Session> it = SESSIONS.iterator(); it.hasNext(); ) {
			Session session = it.next();
			if (!session.done && (session.involves(a) || session.involves(b))) {
				end(session);
				it.remove();
			}
		}
		if (a.activity() != 0 && a.activity() != SekteIds.ACT_SOCIAL || b.activity() != 0 && b.activity() != SekteIds.ACT_SOCIAL) {
			return Component.translatable("kultivator.sekte.hubungan.alasan.tugas", a.activity() != 0 ? a.getName() : b.getName());
		}
		BlockPos[] spots = seatsBetween(a, b);
		if (spots == null) {
			return Component.translatable("kultivator.sekte.hubungan.alasan.tempat");
		}
		MinecraftServer server = a.level().getServer();
		begin(new Session(TEA, owner, a, b, spots[0], spots[1], server == null ? 0L : server.getTickCount()));
		return null;
	}

	private GiliranSosial() {
	}
}
