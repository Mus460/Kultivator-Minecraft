package dev.kultivator.sekte.tangga;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.epic.EpicMoments;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.build.SectBuild;
import dev.kultivator.sect.hall.Candidates;
import dev.kultivator.sect.hall.HallFxIds;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.VoiceDisciples;
import dev.kultivator.sekte.SekteSuara;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One climb of the Thousand-Step Stair (DESIGN 36.2.4): the recruitment trial of up to six candidates, a disciple's
 * Ujian Hati re-climb, or the bare projection of the test command. Server only; at most one per hall.
 * <p>
 * Timeline (ticks): gathering {@value #GATHER} (the climbers appear in mist on the lineup cells by the Batu Hati, the
 * projection unfurls, they step onto the threshold), the climb ({@code detikPendakian}, 52 s by default), the peak
 * {@value #PEAK_TICKS}, the announcement (one result every {@value #ANNOUNCE_GAP}), the fade {@value #FADE_TICKS}. Each
 * climber stops at {@code L = 300 * tiers + random(0..99) + Tekad} (at most 1000) with {@code tiers} the old
 * {@code Candidates.tiers}: the old verdict is never changed, the climb only shows it and adds the steps and the heart
 * verdict (3 the peak, 2 "never gave up": under 100 steps with Tekad 70+, 1 gave up: Tekad under 30, else 0). Illusions
 * at steps 300, 600 and 900 make a climber kneel {@value #KNEEL_TICKS} ticks.
 * <p>
 * Budget (DESIGN 36.3.9): at most six bodies; their route is planned every four ticks and they are pinned on it each tick
 * (one position each, no pathfinding, no block collision: {@code noPhysics} and no gravity while they climb, restored
 * after); the state goes to the clients within {@code jarakTampil} every ten ticks. A climb that loses its hall, its
 * level, or meets a live raid settles at once as data (every result revealed, the bodies back on the ground).
 */
final class Pendakian {

	static final int GATHER = 100;
	static final int GLIDE_FROM = 50;
	static final int GLIDE_TICKS = 40;
	static final int PEAK_TICKS = 60;
	static final int ANNOUNCE_GAP = 40;
	static final int FADE_TICKS = 40;
	static final int KNEEL_TICKS = 40;
	static final int SEND_EVERY = 10;
	static final int PLAN_EVERY = 4;
	/** The illusions (heart demons) wait on these steps. */
	static final int[] ILLUSIONS = {300, 600, 900};
	/** The bare projection of {@code uji perguruan tangga proyeksi} lasts this long (ticks). */
	static final int PREVIEW_TICKS = 400;
	private static final int MIST = 0xD8F0E8;
	/** {@code extra.tangga} marks of a disciple on the stair (cleared when it comes down; see {@link #recover}). */
	static final String K_CLIMBING = "naik";
	static final String K_HAD_NO_GRAVITY = "ng";
	static final String K_HAD_NO_AI = "na";

	/** The hook {@code Candidates} calls (installed by {@link Tangga#init()}). */
	static final Candidates.StairTrial TRIAL = new Candidates.StairTrial() {
		@Override
		public boolean begin(ServerPlayer player, UUID owner, int id, boolean shardInHand) {
			return beginTrial(player, owner, id, shardInHand, false);
		}

		@Override
		public boolean busy(UUID owner, int id) {
			Pendakian climb = RUNNING.get(owner);
			if (climb == null) {
				return false;
			}
			for (Peserta p : climb.climbers) {
				if (p.recordId == id) {
					return true;
				}
			}
			return false;
		}

		@Override
		public void accepted(ServerPlayer master, DiscipleEntity disciple, CompoundTag record) {
			Pendakian.accepted(master, disciple);
		}
	};

	private static final Map<UUID, Pendakian> RUNNING = new HashMap<>();

	/** One climber. */
	static final class Peserta {
		/** The candidate record id, or -1 for a disciple's re-climb. */
		final int recordId;
		final DiscipleEntity body;
		final Component name;
		final int lane;
		final int tiers;
		final int tekad;
		final int verdict;
		final int limit;
		final int heart;
		final Vec3 home;
		final double jitter;
		final double wobble;
		final boolean hadNoGravity;
		final boolean hadNoAi;
		final int prevActivity;
		final @Nullable BlockPos prevSpot;
		int plannedEnd;
		double from;
		double to;
		int state = TanggaPayloads.C_CLIMB;
		int kneel;
		int illusionsPassed;
		int chimes;
		int giveUpTicks;
		boolean revealed;
		boolean restored;

		Peserta(int recordId, DiscipleEntity body, Component name, int lane, int tiers, int tekad, int verdict, int limit, int heart, Vec3 home,
				RandomSource random) {
			this.recordId = recordId;
			this.body = body;
			this.name = name;
			this.lane = lane;
			this.tiers = tiers;
			this.tekad = tekad;
			this.verdict = verdict;
			this.limit = limit;
			this.heart = heart;
			this.home = home;
			this.jitter = 0.9D + random.nextDouble() * 0.1D;
			this.wobble = random.nextDouble() * Math.PI * 2.0D;
			this.hadNoGravity = body.isNoGravity();
			this.hadNoAi = body.isNoAi();
			this.prevActivity = body.activity();
			this.prevSpot = body.activitySpot();
		}

		double stepAt(float partial) {
			return from + (to - from) * partial;
		}

		boolean alive() {
			return !body.isRemoved() && body.isAlive();
		}

		boolean moving() {
			return state == TanggaPayloads.C_CLIMB || kneel > 0 || giveUpTicks > 0;
		}
	}

	final UUID owner;
	final UUID tester;
	final ServerLevel level;
	final JalurTangga.Frame frame;
	final int flags;
	final List<Peserta> climbers;
	final int climbTicks;
	int ticks;
	int phase = TanggaPayloads.PHASE_FORMING;
	int peakAt = -1;
	int announceAt = -1;
	int fadeAt = -1;
	boolean peakScene;
	private final double[] point = new double[3];
	private final double[] heading = new double[2];

	private Pendakian(UUID owner, UUID tester, ServerLevel level, JalurTangga.Frame frame, int flags, List<Peserta> climbers) {
		this.owner = owner;
		this.tester = tester;
		this.level = level;
		this.frame = frame;
		this.flags = flags;
		this.climbers = climbers;
		this.climbTicks = (flags & TanggaPayloads.FLAG_PREVIEW) != 0 ? PREVIEW_TICKS - GATHER - FADE_TICKS
				: TanggaConfig.get().detikPendakian * 20;
	}

	// ================================================================== starting

	static boolean running(UUID owner) {
		return owner != null && RUNNING.containsKey(owner);
	}

	/**
	 * The master tests a candidate while the stair stands: every untested candidate present climbs, one amethyst shard
	 * each (the old price; {@code free} for the test command). False hands the test back to the stele.
	 */
	static boolean beginTrial(ServerPlayer player, UUID owner, int id, boolean shardInHand, boolean free) {
		if (!free && (!TanggaConfig.get().aktif || !Tangga.INSTANCE.hasStair(owner))) {
			return false;
		}
		if (RUNNING.containsKey(owner)) {
			Msg.chat(player, Component.translatable("kultivator.sekte.tangga.busy").withStyle(ChatFormatting.YELLOW));
			return true;
		}
		ServerLevel level = Candidates.hallLevel(owner);
		BlockPos stele = Candidates.hallStele(owner);
		if (level == null || stele == null || Candidates.recruitPaused(owner)) {
			return false;
		}
		List<Candidates.Climber> found = Candidates.climbers(owner, id, JalurTangga.LANES);
		if (found.isEmpty() || id >= 0 && found.get(0).id() != id) {
			return false;
		}
		JalurTangga.Frame frame = Tangga.frame(owner, level, stele, player.position());
		if (frame == null) {
			return false;
		}
		List<Candidates.Climber> paid = new ArrayList<>();
		for (Candidates.Climber climber : found) {
			if (!free && !Candidates.payShard(player, paid.isEmpty() && shardInHand)) {
				break;
			}
			paid.add(climber);
		}
		if (paid.isEmpty()) {
			Msg.chat(player, Component.translatable("kultivator.hall.cand.need_shard").withStyle(ChatFormatting.YELLOW));
			return true;
		}
		if (paid.size() < found.size()) {
			Msg.chat(player, Component.translatable("kultivator.sekte.tangga.shards_short", paid.size(), found.size()).withStyle(ChatFormatting.GRAY));
		}
		RandomSource random = level.getRandom();
		List<Vec3> homes = homes(owner, level, frame, paid.size());
		List<Peserta> climbers = new ArrayList<>();
		for (int i = 0; i < paid.size(); i++) {
			Candidates.Climber c = paid.get(i);
			int limit = limit(c.tiers(), c.tekad(), random);
			climbers.add(new Peserta(c.id(), c.body(), c.name(), lane(i, paid.size()), c.tiers(), c.tekad(), c.verdict(), limit,
					heartVerdict(limit, c.tekad()), homes.get(i), random));
		}
		start(new Pendakian(owner, player.getUUID(), level, frame, 0, climbers), player);
		return true;
	}

	/** A disciple's Ujian Hati re-climb (already paid and checked by {@link UjianHati}). False when it cannot start. */
	static boolean beginHeart(ServerPlayer player, UUID owner, DiscipleEntity disciple) {
		if (RUNNING.containsKey(owner) || !(disciple.level() instanceof ServerLevel level)) {
			return false;
		}
		BlockPos stele = Candidates.hallStele(owner);
		if (stele == null || level != Candidates.hallLevel(owner)) {
			return false;
		}
		JalurTangga.Frame frame = Tangga.frame(owner, level, stele, player.position());
		if (frame == null) {
			return false;
		}
		DiscipleData data = disciple.data();
		int tiers = Candidates.tiers(data);
		int tekad = dev.kultivator.sect.hall.Talents.tekad(data);
		int limit = limit(tiers, tekad, level.getRandom());
		Vec3 home = homes(owner, level, frame, 1).get(0);
		List<Peserta> climbers = new ArrayList<>();
		climbers.add(new Peserta(-1, disciple, disciple.getName(), 2, tiers, tekad, Candidates.verdict(data), limit, heartVerdict(limit, tekad), home,
				level.getRandom()));
		start(new Pendakian(owner, player.getUUID(), level, frame, TanggaPayloads.FLAG_HEART, climbers), player);
		return true;
	}

	/** The bare projection for {@code uji perguruan tangga proyeksi} (20 s, no climbers). */
	static boolean beginPreview(ServerPlayer player, UUID owner) {
		ServerLevel level = Candidates.hallLevel(owner);
		BlockPos stele = Candidates.hallStele(owner);
		if (level == null || stele == null || RUNNING.containsKey(owner)) {
			return false;
		}
		JalurTangga.Frame frame = Tangga.frame(owner, level, stele, player.position());
		if (frame == null) {
			return false;
		}
		start(new Pendakian(owner, player.getUUID(), level, frame, TanggaPayloads.FLAG_PREVIEW, new ArrayList<>()), player);
		return true;
	}

	private static void start(Pendakian climb, ServerPlayer player) {
		RUNNING.put(climb.owner, climb);
		Vec3 o = new Vec3(climb.frame.ox(), climb.frame.oy(), climb.frame.oz());
		climb.level.playSound(null, o.x, o.y + 1.0D, o.z, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 2.5F, 0.6F);
		climb.level.playSound(null, o.x, o.y + 1.0D, o.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.6F, 0.7F);
		climb.level.playSound(null, o.x, o.y + 1.0D, o.z, SoundEvents.CONDUIT_ACTIVATE, SoundSource.BLOCKS, 1.4F, 0.8F);
		for (Peserta p : climb.climbers) {
			if (p.recordId < 0) {
				// A saved disciple: a mark that undoes the climb's flags if the game stops without settling (a crash).
				CompoundTag mark = p.body.data().extraSection(SekteIds.EXTRA_G);
				mark.putBoolean(K_CLIMBING, true);
				mark.putBoolean(K_HAD_NO_GRAVITY, p.hadNoGravity);
				mark.putBoolean(K_HAD_NO_AI, p.hadNoAi);
			}
			p.body.getNavigation().stop();
			p.body.setTarget(null);
			p.body.setActivity(SekteIds.ACT_CLIMB, null);
			climb.place(p, p.home.x, p.home.y, p.home.z, JalurTangga.yaw(climb.frame.fx(), climb.frame.fz()));
			Fx.send(climb.level, p.home, FxType.HALL, HallFxIds.MIST, MIST, 1.0F);
		}
		if ((climb.flags & TanggaPayloads.FLAG_PREVIEW) == 0) {
			Component line = (climb.flags & TanggaPayloads.FLAG_HEART) != 0
					? Component.translatable("kultivator.sekte.tangga.start_heart", climb.climbers.get(0).name)
					: Component.translatable("kultivator.sekte.tangga.start", climb.climbers.size());
			Msg.chat(player, line.copy().withStyle(ChatFormatting.AQUA));
			Msg.title(player, Component.translatable("kultivator.sekte.tangga.title").withStyle(ChatFormatting.GOLD),
					Component.translatable("kultivator.sekte.tangga.subtitle").withStyle(ChatFormatting.YELLOW), 10, 50, 20);
		}
		climb.send(true);
	}

	/** {@code L = 300 * tiers + random(0..99) + Tekad}, 0-1000. */
	static int limit(int tiers, int tekad, RandomSource random) {
		return Math.max(0, Math.min(JalurTangga.STEPS, 300 * Math.max(0, tiers) + random.nextInt(100) + Math.max(0, tekad)));
	}

	/** 3 the peak, 2 "never gave up" (under 100 steps with Tekad 70+), 1 gave up (Tekad under 30), else 0. */
	static int heartVerdict(int limit, int tekad) {
		if (limit >= JalurTangga.STEPS) {
			return 3;
		}
		if (limit < 100 && tekad >= 70) {
			return 2;
		}
		return tekad < 30 ? 1 : 0;
	}

	/** Lanes spread over the stair's width for {@code count} climbers. */
	private static int lane(int index, int count) {
		if (count <= 1) {
			return 2;
		}
		return Math.round(index * (JalurTangga.LANES - 1) / (float) (count - 1));
	}

	/** The lineup cells (anchors {@code tangga_baris_n}, else a row behind the projector), fitted to the ground. */
	private static List<Vec3> homes(UUID owner, ServerLevel level, JalurTangga.Frame frame, int count) {
		List<Vec3> homes = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			BlockPos anchor = SectBuild.anchor(owner, BatuHatiKomponen.ANCHOR_LINE + (i + 1));
			double x;
			double z;
			int hint;
			if (anchor != null) {
				x = anchor.getX() + 0.5D;
				z = anchor.getZ() + 0.5D;
				hint = anchor.getY();
			} else {
				double lateral = (i % 2 == 0 ? -1.0D : 1.0D) * (1 + i / 2);
				x = frame.ox() - frame.fx() * 2.0D + frame.rx() * lateral;
				z = frame.oz() - frame.fz() * 2.0D + frame.rz() * lateral;
				hint = (int) Math.floor(frame.oy());
			}
			int y = Tangga.standY(level, (int) Math.floor(x), (int) Math.floor(z), hint);
			homes.add(new Vec3(x, y, z));
		}
		return homes;
	}

	// ================================================================== ticking

	static void tick(MinecraftServer server) {
		if (RUNNING.isEmpty()) {
			return;
		}
		for (Iterator<Pendakian> it = RUNNING.values().iterator(); it.hasNext(); ) {
			Pendakian climb = it.next();
			boolean done;
			try {
				done = climb.step(server);
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.error("Tangga climb of {} failed; settling it", climb.owner, exception);
				climb.settle(server);
				done = true;
			}
			if (done) {
				it.remove();
			}
		}
	}

	/** Ends every climb at once (server stopping): the bodies come down, nothing is revealed twice. */
	static void stopAll(MinecraftServer server) {
		for (Pendakian climb : new ArrayList<>(RUNNING.values())) {
			try {
				climb.settle(server);
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.warn("Tangga climb of {} could not settle", climb.owner, exception);
			}
		}
		RUNNING.clear();
	}

	/** Ends the owner's climb now (test command); false when none runs. */
	static boolean stop(MinecraftServer server, UUID owner) {
		Pendakian climb = RUNNING.remove(owner);
		if (climb == null) {
			return false;
		}
		climb.settle(server);
		return true;
	}

	/** One tick; true when the climb is over. */
	private boolean step(MinecraftServer server) {
		ticks++;
		if (aborted()) {
			settle(server);
			return true;
		}
		climbers.removeIf(p -> {
			if (!p.alive() || p.recordId >= 0 && !candidateKept(p)) {
				p.restored = true;
				return true;
			}
			return false;
		});
		if (ticks < GATHER) {
			gather();
		} else if (phase <= TanggaPayloads.PHASE_CLIMB && peakAt < 0) {
			climb(server);
		} else if (phase == TanggaPayloads.PHASE_PEAK) {
			pinAll(1.0F);
			if (ticks - peakAt >= PEAK_TICKS) {
				phase = (flags & TanggaPayloads.FLAG_PREVIEW) != 0 ? TanggaPayloads.PHASE_FADE : TanggaPayloads.PHASE_ANNOUNCE;
				announceAt = ticks;
				if (phase == TanggaPayloads.PHASE_FADE) {
					fadeAt = ticks;
				}
				send(true);
			}
		} else if (phase == TanggaPayloads.PHASE_ANNOUNCE) {
			pinAll(1.0F);
			announce(server);
		} else if (phase == TanggaPayloads.PHASE_FADE) {
			if (ticks == fadeAt + 1) {
				comeDown();
				level.playSound(null, frame.ox(), frame.oy() + 1.0D, frame.oz(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.4F, 0.7F);
			}
			if (ticks - fadeAt >= FADE_TICKS) {
				finish(server);
				return true;
			}
		}
		if (ticks % SEND_EVERY == 0) {
			send(false);
		}
		return false;
	}

	private boolean aborted() {
		if (!level.isLoaded(BlockPos.containing(frame.ox(), frame.oy(), frame.oz()))) {
			return true;
		}
		if ((flags & TanggaPayloads.FLAG_PREVIEW) != 0) {
			return false;
		}
		return Candidates.hallLevel(owner) != level || Candidates.recruitPaused(owner);
	}

	/** True while the candidate's record is still in the hall (records expire at the next sunrise). */
	private boolean candidateKept(Peserta p) {
		return p.body.role() == DiscipleEntity.Role.CANDIDATE;
	}

	private void gather() {
		phase = TanggaPayloads.PHASE_FORMING;
		for (Peserta p : climbers) {
			if (ticks < GLIDE_FROM) {
				place(p, p.home.x, p.home.y, p.home.z, JalurTangga.yaw(frame.fx(), frame.fz()));
				continue;
			}
			// One after another the climbers step from the lineup onto the threshold.
			float t = Math.max(0.0F, Math.min(1.0F, (ticks - GLIDE_FROM - climbers.indexOf(p) * 4) / (float) (GLIDE_TICKS - 20)));
			float e = t * t * (3.0F - 2.0F * t);
			JalurTangga.point(frame, 0.0D, JalurTangga.lane(p.lane), point);
			double x = p.home.x + (point[0] - p.home.x) * e;
			double y = p.home.y + (point[1] - p.home.y) * e;
			double z = p.home.z + (point[2] - p.home.z) * e;
			place(p, x, y, z, JalurTangga.yaw(point[0] - p.home.x, point[2] - p.home.z));
		}
		if (ticks == GATHER - 1) {
			for (Peserta p : climbers) {
				p.plannedEnd = plan(p);
			}
			phase = TanggaPayloads.PHASE_CLIMB;
			send(true);
		}
	}

	/** The tick (into the climb) a climber reaches its limit: lower limits are reached sooner, kneels add on top. */
	private int plan(Peserta p) {
		int kneels = 0;
		for (int step : ILLUSIONS) {
			if (step < p.limit) {
				kneels++;
			}
		}
		int move = (int) Math.round(climbTicks * (0.28D + 0.62D * p.limit / (double) JalurTangga.STEPS) * p.jitter);
		return Math.max(40, Math.min(climbTicks - 20, move + kneels * KNEEL_TICKS));
	}

	private void climb(MinecraftServer server) {
		int c = ticks - GATHER;
		phase = TanggaPayloads.PHASE_CLIMB;
		if (c % PLAN_EVERY == 0) {
			boolean anyMoving = false;
			for (Peserta p : climbers) {
				replan(server, p, c);
				anyMoving |= p.moving();
			}
			if ((flags & TanggaPayloads.FLAG_PREVIEW) == 0 && !anyMoving && c > 40 || c >= climbTicks) {
				for (Peserta p : climbers) {
					p.from = p.to;
				}
				phase = TanggaPayloads.PHASE_PEAK;
				peakAt = ticks;
				peakMoment(server);
				send(true);
				return;
			}
		}
		pinAll((c % PLAN_EVERY) / (float) PLAN_EVERY);
	}

	/** Plans the next four ticks of one climber: illusions, the chimes every 100 steps, reaching its limit. */
	private void replan(MinecraftServer server, Peserta p, int c) {
		p.from = p.to;
		if (p.kneel > 0) {
			p.kneel = Math.max(0, p.kneel - PLAN_EVERY);
			if (p.kneel == 0) {
				p.state = TanggaPayloads.C_CLIMB;
			}
			return;
		}
		if (p.giveUpTicks > 0) {
			// Turning back: a few dozen steps down, then it stands with its head bowed.
			p.giveUpTicks = Math.max(0, p.giveUpTicks - PLAN_EVERY);
			p.to = Math.max(0.0D, p.to - 1.5D * PLAN_EVERY);
			return;
		}
		if (p.state != TanggaPayloads.C_CLIMB) {
			return;
		}
		int kneelsAhead = 0;
		for (int i = p.illusionsPassed; i < ILLUSIONS.length; i++) {
			if (ILLUSIONS[i] < p.limit) {
				kneelsAhead++;
			}
		}
		int left = Math.max(PLAN_EVERY, p.plannedEnd - c - kneelsAhead * KNEEL_TICKS);
		double rate = (p.limit - p.to) / left;
		// The pressure: a halting rhythm (the stair weighs more the higher it goes), corrected by the plan.
		double wobble = 1.0D + 0.25D * Math.sin(p.wobble + c * 0.11D) * (0.4D + p.to / JalurTangga.STEPS);
		double next = Math.min(p.limit, p.to + Math.max(0.0D, rate * wobble) * PLAN_EVERY);
		int illusion = p.illusionsPassed < ILLUSIONS.length ? ILLUSIONS[p.illusionsPassed] : Integer.MAX_VALUE;
		if (next >= illusion && illusion < p.limit) {
			next = illusion;
			p.illusionsPassed++;
			p.kneel = KNEEL_TICKS;
			p.state = TanggaPayloads.C_KNEEL;
			JalurTangga.point(frame, next, JalurTangga.lane(p.lane), point);
			level.playSound(null, point[0], point[1] + 1.0D, point[2], SoundEvents.BREEZE_IDLE_AIR, SoundSource.NEUTRAL, 1.2F, 0.5F);
			level.playSound(null, point[0], point[1] + 1.0D, point[2], SoundEvents.WARDEN_HEARTBEAT, SoundSource.NEUTRAL, 0.9F, 0.7F);
			SekteSuara.line(p.body, "climb_illusion", "fear", 200);
		}
		p.to = next;
		int hundreds = (int) (next / 100.0D);
		if (hundreds > p.chimes) {
			p.chimes = hundreds;
			JalurTangga.point(frame, next, JalurTangga.lane(p.lane), point);
			level.playSound(null, point[0], point[1] + 0.5D, point[2], SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F,
					0.6F + 0.12F * Math.min(10, hundreds));
		}
		if (next >= p.limit && p.kneel == 0) {
			reached(server, p);
		}
	}

	private void reached(MinecraftServer server, Peserta p) {
		JalurTangga.point(frame, p.limit, JalurTangga.lane(p.lane), point);
		if (p.limit >= JalurTangga.STEPS) {
			p.state = TanggaPayloads.C_PEAK;
			level.playSound(null, point[0], point[1] + 1.0D, point[2], SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 2.0F, 1.2F);
			level.playSound(null, point[0], point[1] + 1.0D, point[2], SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.6F, 1.0F);
			SekteSuara.line(p.body, "climb_peak", "victory", 200);
			if (!peakScene) {
				peakScene = true;
				peakScene(server, p);
			}
		} else if (p.heart == 1) {
			p.state = TanggaPayloads.C_GAVE_UP;
			p.giveUpTicks = 40;
			level.playSound(null, point[0], point[1] + 1.0D, point[2], SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.NEUTRAL, 0.8F, 0.5F);
			VoiceDisciples.line(p.body, "tired", 200);
		} else {
			p.state = TanggaPayloads.C_STOP;
			level.playSound(null, point[0], point[1] + 1.0D, point[2], SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.NEUTRAL, 0.8F,
					0.6F + 0.8F * p.limit / JalurTangga.STEPS);
		}
	}

	/** "Puncak!" (SekteScenes beat 130/131) for the master standing near the stair. */
	private void peakScene(MinecraftServer server, Peserta p) {
		if (!TanggaConfig.get().adegan) {
			return;
		}
		ServerPlayer master = server.getPlayerList().getPlayer(owner);
		if (master == null || master.level() != level || !JalurTangga.near(frame, master.getX(), master.getY(), master.getZ(), 64.0D)) {
			return;
		}
		JalurTangga.peak(frame, point);
		int beat = (flags & TanggaPayloads.FLAG_HEART) != 0 ? Tangga.BEAT_HEART_PEAK : Tangga.BEAT_PEAK;
		EpicMoments.send(level, EpicIds.EXTRA, beat, p.body, master, new Vec3(point[0], point[1], point[2]), level.getGameTime(), JalurTangga.STEPS, 0,
				0.0F, 5, 0);
	}

	/** The peak beat of the whole trial: the best climber's light; with nobody on the summit only a deep bell. */
	private void peakMoment(MinecraftServer server) {
		JalurTangga.peak(frame, point);
		boolean summit = false;
		for (Peserta p : climbers) {
			summit |= p.limit >= JalurTangga.STEPS;
		}
		level.playSound(null, point[0], point[1], point[2], SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 3.0F, summit ? 1.0F : 0.5F);
	}

	private void announce(MinecraftServer server) {
		int since = ticks - announceAt;
		List<Peserta> order = new ArrayList<>(climbers);
		order.sort(Comparator.comparingInt((Peserta p) -> p.limit));
		int index = since / ANNOUNCE_GAP;
		if (since % ANNOUNCE_GAP == 0 && index < order.size()) {
			reveal(server, order.get(index));
			send(true);
		}
		if (index >= order.size() && since >= order.size() * ANNOUNCE_GAP + 20 || order.isEmpty()) {
			phase = TanggaPayloads.PHASE_FADE;
			fadeAt = ticks;
			send(true);
		}
	}

	/** The result of one climber: the candidate's card (the old reveal) or the disciple's own record. */
	private void reveal(MinecraftServer server, Peserta p) {
		if (p.revealed) {
			return;
		}
		p.revealed = true;
		long day = Ingatan.today(server);
		Component verdict = Component.translatable("kultivator.sekte.tangga.hati." + p.heart);
		Component line = Component.translatable("kultivator.sekte.tangga.result", p.name, p.limit, verdict)
				.withStyle(p.limit >= JalurTangga.STEPS ? ChatFormatting.GOLD : ChatFormatting.AQUA);
		JalurTangga.point(frame, p.limit, JalurTangga.lane(p.lane), point);
		level.playSound(null, point[0], point[1] + 1.0D, point[2], SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 1.0F,
				0.5F + p.limit / (float) JalurTangga.STEPS);
		if (p.recordId >= 0) {
			List<Component> extra = new ArrayList<>();
			extra.add(line);
			if (p.limit >= JalurTangga.STEPS) {
				extra.add(Component.translatable("kultivator.sekte.tangga.result_peak").withStyle(ChatFormatting.GOLD));
			} else if (p.heart == 2) {
				extra.add(Component.translatable("kultivator.sekte.tangga.result_gigih").withStyle(ChatFormatting.GREEN));
			}
			Candidates.revealClimb(server, tester, owner, p.recordId, data -> write(data, p.limit, p.heart, day, false), extra);
		} else {
			UjianHati.finished(server, owner, p.body, p.limit, p.heart, day);
			for (ServerPlayer player : viewers(server, true)) {
				Msg.chat(player, line);
			}
		}
	}

	/** Writes {@code extra.tangga} (DESIGN 36.2.4) into {@code data}. */
	static void write(DiscipleData data, int steps, int heart, long day, boolean reclimb) {
		CompoundTag mine = data.extraSection(SekteIds.EXTRA_G);
		mine.putInt(Tangga.K_STEPS, steps);
		mine.putInt(Tangga.K_VERDICT, heart);
		mine.putLong(Tangga.K_DAY, day);
		mine.putInt(Tangga.K_CLIMBS, mine.getIntOr(Tangga.K_CLIMBS, 0) + 1);
		mine.putInt(Tangga.K_BEST, Math.max(mine.getIntOr(Tangga.K_BEST, 0), steps));
		if (steps >= JalurTangga.STEPS) {
			mine.putBoolean(Tangga.K_PEAK, true);
		}
		if (reclimb) {
			mine.putLong(Tangga.K_RECLIMB_DAY, day);
		}
	}

	/** Everyone comes down: mist at the climber, mist at its lineup cell, the body there again. */
	private void comeDown() {
		for (Peserta p : climbers) {
			if (p.alive()) {
				Fx.send(level, p.body.position(), FxType.HALL, HallFxIds.MIST, MIST, 0.0F);
				place(p, p.home.x, p.home.y, p.home.z, JalurTangga.yaw(frame.fx(), frame.fz()));
				Fx.send(level, p.home, FxType.HALL, HallFxIds.MIST, MIST, 1.0F);
			}
			restore(p);
		}
	}

	private void restore(Peserta p) {
		if (p.restored) {
			return;
		}
		p.restored = true;
		if (!p.alive()) {
			return;
		}
		DiscipleEntity body = p.body;
		body.noPhysics = false;
		body.setNoGravity(p.hadNoGravity);
		body.setNoAi(p.hadNoAi);
		if (p.recordId < 0) {
			CompoundTag mark = body.data().extraSection(SekteIds.EXTRA_G);
			mark.remove(K_CLIMBING);
			mark.remove(K_HAD_NO_GRAVITY);
			mark.remove(K_HAD_NO_AI);
		}
		body.setDeltaMovement(Vec3.ZERO);
		body.resetFallDistance();
		int activity = p.prevActivity == SekteIds.ACT_CLIMB ? DiscipleEntity.ACTIVITY_NONE : p.prevActivity;
		body.setActivity(activity, p.prevSpot);
	}

	private void finish(MinecraftServer server) {
		for (Peserta p : climbers) {
			restore(p);
		}
		broadcast(TanggaPayloads.State.off(owner));
		if ((flags & (TanggaPayloads.FLAG_PREVIEW | TanggaPayloads.FLAG_HEART)) != 0 || climbers.isEmpty()) {
			return;
		}
		int best = 0;
		int passed = 0;
		for (Peserta p : climbers) {
			best = Math.max(best, p.limit);
			if (p.verdict == Candidates.VERDICT_PASSED) {
				passed++;
			}
		}
		SekteBus.TRIAL_FINISHED.invoker().onTrialFinished(server, owner, climbers.size(), passed, best);
		SekteLog.log(server, owner, SekteIds.G, Component.translatable("kultivator.sekte.tangga.log", climbers.size(), best, passed));
		ServerPlayer master = server.getPlayerList().getPlayer(owner);
		if (master != null) {
			Advancements.grant(master, SekteIds.ADVANCEMENT_FOLDER + "tangga_selesai");
			Msg.chat(master, Component.translatable("kultivator.sekte.tangga.done", best).withStyle(ChatFormatting.GOLD));
		}
	}

	/** Settles at once: every result revealed as data, the bodies back on the ground, the stair gone. */
	void settle(MinecraftServer server) {
		for (Peserta p : climbers) {
			if (!p.revealed && p.alive() && (flags & TanggaPayloads.FLAG_PREVIEW) == 0) {
				try {
					reveal(server, p);
				} catch (RuntimeException exception) {
					dev.kultivator.Kultivator.LOGGER.warn("Tangga reveal failed for {}", p.name.getString(), exception);
				}
			}
		}
		comeDown();
		finish(server);
	}

	// ================================================================== bodies

	private void pinAll(float partial) {
		for (Peserta p : climbers) {
			double s = p.stepAt(partial);
			JalurTangga.point(frame, s, JalurTangga.lane(p.lane), point);
			float yaw;
			if (p.state == TanggaPayloads.C_GAVE_UP && p.giveUpTicks > 0) {
				JalurTangga.heading(frame, s, heading);
				yaw = JalurTangga.yaw(-heading[0], -heading[1]);
			} else if (phase >= TanggaPayloads.PHASE_PEAK) {
				// Done climbing: they turn to look down at the sect.
				yaw = JalurTangga.yaw(frame.ox() - point[0], frame.oz() - point[2]);
			} else {
				JalurTangga.heading(frame, s, heading);
				yaw = JalurTangga.yaw(heading[0], heading[1]);
			}
			place(p, point[0], point[1], point[2], yaw);
		}
	}

	/** Pins a climber: no physics, no gravity, no path, facing {@code yaw}. */
	private void place(Peserta p, double x, double y, double z, float yaw) {
		DiscipleEntity body = p.body;
		if (body.isRemoved()) {
			return;
		}
		body.noPhysics = true;
		body.setNoGravity(true);
		if (!body.isNoAi()) {
			// no AI step while on the stair: nothing pulls the body off its tread between two pins
			body.setNoAi(true);
		}
		body.getNavigation().stop();
		if (body.getTarget() != null) {
			body.setTarget(null);
		}
		body.setDeltaMovement(Vec3.ZERO);
		body.resetFallDistance();
		body.setPos(x, y, z);
		body.setYRot(yaw);
		body.setYHeadRot(yaw);
		body.setYBodyRot(yaw);
	}

	// ================================================================== sync

	private TanggaPayloads.State state() {
		List<TanggaPayloads.Climber> list = new ArrayList<>();
		for (Peserta p : climbers) {
			boolean shown = phase >= TanggaPayloads.PHASE_ANNOUNCE && p.revealed;
			list.add(new TanggaPayloads.Climber(p.body.getId(), p.lane, (int) Math.round(p.to), shown ? p.limit : -1, p.state, shown ? p.heart : 0));
		}
		return new TanggaPayloads.State(owner, frame.ox(), frame.oy(), frame.oz(), (float) frame.fx(), (float) frame.fz(), phase, ticks,
				TanggaConfig.lodCode(), flags, list);
	}

	/** Sends the state now ({@code phaseChange}: a phase or a result changed; else the regular 10-tick refresh). */
	private void send(boolean phaseChange) {
		if (phaseChange || !climbers.isEmpty() || ticks % (SEND_EVERY * 4) == 0) {
			broadcast(state());
		}
	}

	private void broadcast(TanggaPayloads.State payload) {
		for (ServerPlayer player : viewers(level.getServer(), false)) {
			if (ServerPlayNetworking.canSend(player, TanggaPayloads.State.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	/** Players in the stair's level within {@code jarakTampil} of it, plus the master and the tester ({@code always}: any level). */
	private List<ServerPlayer> viewers(MinecraftServer server, boolean always) {
		double range = TanggaConfig.get().jarakTampil;
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (JalurTangga.near(frame, player.getX(), player.getY(), player.getZ(), range)) {
				out.add(player);
			}
		}
		for (UUID id : new UUID[]{owner, tester}) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null && !out.contains(player) && (always || player.level() == level)) {
				out.add(player);
			}
		}
		return out;
	}

	// ================================================================== after acceptance

	/** A candidate who climbed became a disciple: the Pendaki Puncak bond, one memory, the honour. */
	private static void accepted(ServerPlayer master, DiscipleEntity disciple) {
		DiscipleData data = disciple.data();
		CompoundTag mine = data.extra().getCompound(SekteIds.EXTRA_G).orElse(null);
		if (mine == null || !mine.contains(Tangga.K_STEPS)) {
			return;
		}
		MinecraftServer server = master.level().getServer();
		int steps = mine.getIntOr(Tangga.K_STEPS, 0);
		int heart = mine.getIntOr(Tangga.K_VERDICT, 0);
		UUID owner = master.getUUID();
		switch (heart) {
			case 3 -> {
				int bond = TanggaConfig.get().bondPuncak;
				if (bond > 0) {
					DisciplePaths.addBond(data, bond, "kultivator.sekte.tangga.bond_puncak");
				}
				Ingatan.remember(server, data, "tangga.puncak", owner, 6, "kultivator.sekte.tangga.mem.puncak", Integer.toString(steps));
				SekteBus.honored(server, owner, disciple.soulId(), "tangga.puncak", 6);
				Msg.chat(master, Component.translatable("kultivator.sekte.tangga.title_puncak", disciple.getDisplayName()).withStyle(ChatFormatting.GOLD));
			}
			case 2 -> {
				Ingatan.remember(server, data, "tangga.gigih", owner, 4, "kultivator.sekte.tangga.mem.gigih", Integer.toString(steps));
				SekteBus.honored(server, owner, disciple.soulId(), "tangga.gigih", 3);
			}
			case 1 -> Ingatan.remember(server, data, "tangga.menyerah", null, -2, "kultivator.sekte.tangga.mem.menyerah", Integer.toString(steps));
			default -> Ingatan.remember(server, data, "tangga.naik", null, 1, "kultivator.sekte.tangga.mem.naik", Integer.toString(steps));
		}
	}

	/**
	 * A disciple loaded with the climbing mark but no climb running (the game stopped without settling it): its own
	 * gravity and AI come back and it stands on the ground below (DESIGN 36.0 rule 3: nothing is ever lost to the stair).
	 */
	static void recover(DiscipleEntity disciple) {
		CompoundTag mark = disciple.data().extra().getCompound(SekteIds.EXTRA_G).orElse(null);
		if (mark == null || !mark.getBooleanOr(K_CLIMBING, false)) {
			return;
		}
		UUID owner = disciple.ownerId();
		Pendakian climb = owner == null ? null : RUNNING.get(owner);
		if (climb != null) {
			for (Peserta p : climb.climbers) {
				if (p.body == disciple) {
					return;
				}
			}
		}
		disciple.noPhysics = false;
		disciple.setNoGravity(mark.getBooleanOr(K_HAD_NO_GRAVITY, false));
		disciple.setNoAi(mark.getBooleanOr(K_HAD_NO_AI, false));
		mark.remove(K_CLIMBING);
		mark.remove(K_HAD_NO_GRAVITY);
		mark.remove(K_HAD_NO_AI);
		if (disciple.activity() == SekteIds.ACT_CLIMB) {
			disciple.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
		if (disciple.level() instanceof ServerLevel level) {
			BlockPos at = disciple.blockPosition();
			int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
			if (y < at.getY()) {
				disciple.setPos(disciple.getX(), y, disciple.getZ());
			}
			disciple.resetFallDistance();
		}
	}

	/** The running climb of {@code owner} (commands). */
	static @Nullable Pendakian of(UUID owner) {
		return RUNNING.get(owner);
	}

	/** Sound helper kept for the commands (a soft chime at the projector). */
	void chime(SoundEvent sound, float pitch) {
		level.playSound(null, frame.ox(), frame.oy() + 1.0D, frame.oz(), sound, SoundSource.BLOCKS, 1.0F, pitch);
	}
}
