package dev.kultivator.client.duel.fx;

import dev.kultivator.Kultivator;
import dev.kultivator.client.duel.DuelBus;
import dev.kultivator.client.epic.EpicDirector;
import dev.kultivator.client.epic.camera.ShotGuard;
import dev.kultivator.duel.net.DuelPayloads;
import dev.kultivator.fx.DuelParticles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The client visuals of the sword duel (DESIGN 35.4, module V; contract C35.V1). {@link #init} is called once by
 * {@code DuelClient.init} (module U): it registers the spark and ember providers, listens to {@code DuelBus} (every duel
 * payload, U's receivers feed it on the client thread), ticks every client tick and draws the world geometry from
 * {@code LevelRenderEvents.COLLECT_SUBMITS} ({@link DuelTrailRenderer}).
 * <p>
 * It mirrors what the visuals need of the duels in range (sessions, fighters, the swings of the last seconds, blade
 * locks) and routes every event to the effect classes: {@link SparkBurst} (sparks, embers, metal flashes, shock rings,
 * decals, dust, hit-stop, slow motion, shake, impact frames), {@link DuelTrailRenderer} / {@link BladeTrail} (blade
 * trails), {@link DuelRingVisual} and {@link DuelRopeRenderer} (the ring), {@link DuelMarks} (危, 決, the GATHER arrows),
 * {@link QiWaveVisual}, {@link CompanionSwordVisual}, {@link DuelIntroFilm}, {@link DuelFinisherFilm}, {@link DuelCamera}
 * and {@link DuelPreviews}. Nothing here changes a block, sends a packet or plays a sound (U plays the duel's sounds from
 * the same events). Idle cost: a few empty-map checks per tick and one empty-list check per frame.
 */
public final class DuelFx {

	/** One duel session as the last {@code DuelSessionSync} described it. */
	public static final class SessionView {
		public final int id;
		public int phase = -1;
		public int pair;
		public int aId = -1;
		public int bId = -1;
		public Vec3 center = Vec3.ZERO;
		public int radius = 9;
		public float markAx;
		public float markAz;
		public float markBx;
		public float markBz;
		public int phaseTicksLeft;
		public int fightTicksLeft;
		public Component aName = Component.empty();
		public Component bName = Component.empty();
		public Component aRank = Component.empty();
		public Component bRank = Component.empty();
		public int aRgb = 0xE8F0FF;
		public int bRgb = 0xE8F0FF;
		public int flags;
		/** Client tick of the last sync and of the last phase change. */
		long lastSync;
		long phaseSince;
		long endedAt = -1L;
		boolean introStarted;
		boolean finisherStarted;
		boolean resultShown;

		SessionView(int id) {
			this.id = id;
		}

		public boolean has(int flag) {
			return (flags & flag) != 0;
		}

		public boolean live() {
			return phase >= DuelCodes.PHASE_GATHER && phase <= DuelCodes.PHASE_FINISHER;
		}

		public boolean involves(int entityId) {
			return entityId == aId || entityId == bId;
		}

		public int partnerOf(int entityId) {
			return entityId == aId ? bId : entityId == bId ? aId : -1;
		}

		public Component nameOf(int entityId) {
			return entityId == bId ? bName : aName;
		}

		public int rgbOf(int entityId) {
			return entityId == bId ? bRgb : aRgb;
		}

		public Vec3 markA() {
			return new Vec3(markAx, center.y, markAz);
		}

		public Vec3 markB() {
			return new Vec3(markBx, center.y, markBz);
		}
	}

	/** One fighter's state from {@code DuelFighterSync}. */
	public static final class FighterView {
		public final int entityId;
		public int state;
		public int flags;
		public int chain;
		public int weapon;
		public float posture;
		public float postureMax = 100.0F;
		long lastSync;

		FighterView(int entityId) {
			this.entityId = entityId;
		}

		public boolean has(int flag) {
			return (flags & flag) != 0;
		}
	}

	/** One swing from {@code DuelSwing} (re-sent with the same id when it changes). */
	public static final class SwingView {
		public final int entityId;
		public final int swingId;
		public int kind;
		public int dir;
		public int weapon;
		public int windup;
		public long startTick;
		public int flags;
		public int qiRgb;
		public boolean predicted;
		long received;
		boolean impactSeen;

		SwingView(int entityId, int swingId) {
			this.entityId = entityId;
			this.swingId = swingId;
		}

		public boolean has(int flag) {
			return (flags & flag) != 0;
		}

		/** The server tick of the impact (open-ended while a heavy charge is held). */
		public double impact() {
			return windup == DuelCodes.WINDUP_CHARGING ? Double.MAX_VALUE : startTick + windup;
		}

		public boolean charging() {
			return windup == DuelCodes.WINDUP_CHARGING;
		}
	}

	/** One blade lock from {@code DuelBindSync}. */
	public static final class BindView {
		public final int aId;
		public final int bId;
		public int pressure;
		public int ticksLeft;
		public int flags;
		long lastSync;
		long started;

		BindView(int aId, int bId) {
			this.aId = aId;
			this.bId = bId;
		}
	}

	/** One result of a clash ({@code DuelClash}), as the effects read it. */
	public record ClashEvent(int aId, int bId, int swingA, int swingB, int outcome, Vec3 anchor, int dirA, int dirB, int power, int rgbA, int rgbB,
							 long serverTick, int seed, int flags, boolean predicted, int group) {
		public boolean decisive() {
			return (flags & DuelCodes.CLASH_DECISIVE) != 0;
		}

		/** 0..1 of the payload's power byte. */
		public float power01() {
			return Math.max(0.0F, Math.min(1.0F, power / 255.0F));
		}
	}

	private static final Map<Integer, SessionView> SESSIONS = new HashMap<>();
	private static final Map<Integer, FighterView> FIGHTERS = new HashMap<>();
	private static final Map<Long, SwingView> SWINGS = new HashMap<>();
	private static final Map<Long, BindView> BINDS = new HashMap<>();
	/** Predicted clashes by (attacker, swing): their spark group and outcome, to skip or fade on the server's answer. */
	private static final Map<Long, int[]> PREDICTED = new HashMap<>();
	private static final Map<Integer, Long> CANCELLED = new HashMap<>();
	private static final List<ClashEvent> PENDING_DECISIVE = new ArrayList<>();

	private static boolean initialised;
	private static long ticks;
	private static @Nullable ClientLevel lastLevel;
	private static int warnings;

	/** Registers everything once (from {@code DuelClient.init}, module U). */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		registerProviders();
		DuelBus.register(new Listener());
		ClientTickEvents.END_CLIENT_TICK.register(DuelFx::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearAll());
		DuelTrailRenderer.init();
		DuelCamera.init();
		// the 7b shot guard never counts a fighter of a live duel as the hostile that blocks the intro or the finisher
		ShotGuard.ignoreHostile = DuelFx::isLiveFighter;
	}

	private static void registerProviders() {
		if (!DuelParticles.registered()) {
			Kultivator.LOGGER.warn("Kultivator: duel particle types are not registered; duel sparks use film sparks");
			return;
		}
		// fabric-particles-v1: register(type, PendingParticleProvider) receives the sprite set of particles/<id>.json
		ParticleProviderRegistry.getInstance().register(DuelParticles.SPARK,
				(ParticleProviderRegistry.PendingParticleProvider<SimpleParticleType>) DuelSparkParticle.Provider::new);
		ParticleProviderRegistry.getInstance().register(DuelParticles.EMBER,
				(ParticleProviderRegistry.PendingParticleProvider<SimpleParticleType>) DuelEmberParticle.Provider::new);
		DuelSparkParticle.markReady();
		DuelEmberParticle.markReady();
	}

	// ================================================================ the bus

	private static final class Listener implements DuelBus.Listener {
		@Override
		public void session(DuelPayloads.DuelSessionSync p) {
			safe("session", () -> onSession(p));
		}

		@Override
		public void fighter(DuelPayloads.DuelFighterSync p) {
			safe("fighter", () -> onFighter(p));
		}

		@Override
		public void swing(DuelPayloads.DuelSwing p, boolean predicted) {
			safe("swing", () -> onSwing(p, predicted));
		}

		@Override
		public void clash(DuelPayloads.DuelClash p, boolean predicted) {
			safe("clash", () -> onClash(p, predicted));
		}

		@Override
		public void bind(DuelPayloads.DuelBindSync p) {
			safe("bind", () -> onBind(p));
		}

		@Override
		public void result(DuelPayloads.DuelResult p) {
			safe("result", () -> onResult(p));
		}

		@Override
		public void preview(DuelPayloads.DuelPreview p) {
			safe("preview", () -> DuelPreviews.play(p.kind(), p.arg(), p.f(), p.rgb()));
		}

		@Override
		public void correction(int swingId, int outcome) {
			safe("correction", () -> onCorrection(swingId));
		}
	}

	private static void onSession(DuelPayloads.DuelSessionSync p) {
		SessionView s = SESSIONS.computeIfAbsent(p.sessionId(), SessionView::new);
		int previous = s.phase;
		s.phase = p.phase();
		s.pair = p.pair();
		s.aId = p.aId();
		s.bId = p.bId();
		s.center = new Vec3(p.cx(), p.cy(), p.cz());
		s.radius = Math.max(3, Math.min(16, p.radius()));
		s.markAx = p.markAx();
		s.markAz = p.markAz();
		s.markBx = p.markBx();
		s.markBz = p.markBz();
		s.phaseTicksLeft = p.phaseTicksLeft();
		s.fightTicksLeft = p.fightTicksLeft();
		s.aName = p.aName();
		s.bName = p.bName();
		s.aRank = p.aRank();
		s.bRank = p.bRank();
		s.aRgb = p.aRgb() & 0xFFFFFF;
		s.bRgb = p.bRgb() & 0xFFFFFF;
		s.flags = p.flags();
		s.lastSync = ticks;
		if (previous != s.phase) {
			s.phaseSince = ticks;
			onPhase(s, previous);
		}
		keepScene(s);
	}

	private static void onPhase(SessionView s, int previous) {
		switch (s.phase) {
			case DuelCodes.PHASE_INTRO -> {
				if (!s.introStarted) {
					s.introStarted = true;
					int total = s.has(DuelCodes.SESSION_INTRO_FILM) ? 100 : 40;
					DuelIntroFilm.start(s, Math.max(0, total - s.phaseTicksLeft));
				}
			}
			case DuelCodes.PHASE_COUNTDOWN -> DuelIntroFilm.countdown(s);
			case DuelCodes.PHASE_FIGHT -> DuelIntroFilm.stop(s);
			case DuelCodes.PHASE_FINISHER -> {
				DuelIntroFilm.stop(s);
				if (!s.finisherStarted) {
					ClashEvent decisive = takeDecisive(s);
					if (s.has(DuelCodes.SESSION_SURRENDER)) {
						s.finisherStarted = true;
						DuelFinisherFilm.surrender(s, -1, -1);
					} else if (decisive != null || s.has(DuelCodes.SESSION_DECISIVE)) {
						s.finisherStarted = true;
						DuelFinisherFilm.decisive(s, decisive);
					}
				}
			}
			case DuelCodes.PHASE_ENDED -> {
				s.endedAt = ticks;
				DuelIntroFilm.stop(s);
			}
			default -> {
			}
		}
	}

	/** Keeps the duel's 7b scene open while the duel is live and the local player is near (it carries σ and the shots). */
	private static void keepScene(SessionView s) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || !s.live() || s.phase == DuelCodes.PHASE_GATHER) {
			return;
		}
		if (minecraft.player.position().distanceToSqr(s.center) > 96.0D * 96.0D && !s.involves(minecraft.player.getId())) {
			return;
		}
		EpicDirector.duelScene(s.aId, s.bId, s.center, s.id * 7919, 200);
	}

	private static void onFighter(DuelPayloads.DuelFighterSync p) {
		FighterView f = FIGHTERS.computeIfAbsent(p.entityId(), FighterView::new);
		int oldState = f.state;
		int oldChain = f.chain;
		int oldFlags = f.flags;
		f.state = p.state();
		f.flags = p.flags();
		f.chain = p.chain();
		f.weapon = p.weaponClass();
		f.posture = p.postureX10() / 10.0F;
		f.postureMax = Math.max(1.0F, p.postureMaxX10() / 10.0F);
		f.lastSync = ticks;
		SparkBurst.onFighter(f, oldState, oldChain, oldFlags);
		DuelMarks.onFighter(f, oldFlags);
	}

	private static void onSwing(DuelPayloads.DuelSwing p, boolean predicted) {
		long key = key(p.entityId(), p.swingId());
		SwingView v = SWINGS.computeIfAbsent(key, k -> new SwingView(p.entityId(), p.swingId()));
		boolean fresh = v.received == 0L;
		v.kind = p.kind();
		v.dir = p.dir();
		v.weapon = p.weaponClass();
		v.windup = p.windup();
		v.startTick = p.startTick();
		v.flags = p.flags();
		v.qiRgb = p.qiRgb() & 0xFFFFFF;
		v.predicted = predicted;
		v.received = ticks;
		DuelTrailRenderer.onSwing(v, fresh);
		DuelMarks.onSwing(v, fresh);
		QiWaveVisual.onSwing(v);
		CompanionSwordVisual.onSwing(v);
	}

	private static void onClash(DuelPayloads.DuelClash p, boolean predicted) {
		long key = key(p.aId(), p.swingA());
		int[] earlier = PREDICTED.get(key);
		if (!predicted && earlier != null) {
			PREDICTED.remove(key);
			boolean match = (p.flags() & DuelCodes.CLASH_PREDICTED_MATCH) != 0 || earlier[1] == p.outcome();
			if (match) {
				// the prediction already showed this result: never replayed
				return;
			}
			cancel(earlier[0]);
		}
		int group = 0;
		if (predicted) {
			group = 1 + (int) ((key * 0x9E3779B97F4A7C15L) >>> 40);
			PREDICTED.put(key, new int[]{group, p.outcome(), (int) ticks});
		}
		ClashEvent event = new ClashEvent(p.aId(), p.bId(), p.swingA(), p.swingB(), p.outcome(), new Vec3(p.x(), p.y(), p.z()), p.dirA(), p.dirB(),
				p.power(), p.rgbA() & 0xFFFFFF, p.rgbB() & 0xFFFFFF, p.serverTick(), p.seed(), p.flags(), predicted, group);
		SwingView swing = SWINGS.get(key);
		if (swing != null) {
			swing.impactSeen = true;
		}
		SparkBurst.play(event);
		QiWaveVisual.onClash(event);
		CompanionSwordVisual.onClash(event);
		DuelRingVisual.onClash(event);
		DuelTrailRenderer.onClash(event);
		if (event.decisive() && !predicted) {
			SessionView s = sessionOf(event.aId());
			if (s == null) {
				s = sessionOf(event.bId());
			}
			if (s != null && !s.finisherStarted) {
				s.finisherStarted = true;
				DuelFinisherFilm.decisive(s, event);
			} else if (s == null) {
				PENDING_DECISIVE.add(event);
			}
		}
	}

	private static void onBind(DuelPayloads.DuelBindSync p) {
		long key = pairKey(p.aId(), p.bId());
		BindView b = BINDS.get(key);
		if (b == null || (p.flags() & DuelCodes.BIND_START) != 0 && ticks - b.lastSync > 4) {
			b = new BindView(p.aId(), p.bId());
			b.started = ticks;
			BINDS.put(key, b);
		}
		b.pressure = Math.max(-100, Math.min(100, p.pressure()));
		b.ticksLeft = p.ticksLeft();
		b.flags = p.flags();
		b.lastSync = ticks;
		if ((p.flags() & DuelCodes.BIND_END) != 0) {
			BINDS.remove(key);
			SparkBurst.bindEnded(b);
		}
	}

	private static void onResult(DuelPayloads.DuelResult p) {
		SessionView s = SESSIONS.get(p.sessionId());
		if (s == null) {
			return;
		}
		if (!s.resultShown) {
			s.resultShown = true;
			DuelFinisherFilm.result(s, p.winnerId(), p.loserId(), p.reason(), p.title());
		}
		s.endedAt = ticks;
	}

	private static void onCorrection(int swingId) {
		for (Iterator<Map.Entry<Long, int[]>> it = PREDICTED.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Long, int[]> entry = it.next();
			if ((int) (entry.getKey() & 0xFFFFL) == (swingId & 0xFFFF)) {
				cancel(entry.getValue()[0]);
				it.remove();
			}
		}
	}

	private static @Nullable ClashEvent takeDecisive(SessionView s) {
		for (Iterator<ClashEvent> it = PENDING_DECISIVE.iterator(); it.hasNext(); ) {
			ClashEvent event = it.next();
			if (s.involves(event.aId()) || s.involves(event.bId())) {
				it.remove();
				return event;
			}
		}
		return null;
	}

	// ================================================================ tick

	private static void tick(Minecraft minecraft) {
		ticks++;
		DuelLinks.tick();
		ClientLevel level = minecraft.level;
		if (level != lastLevel) {
			lastLevel = level;
			clearAll();
			return;
		}
		if (level == null || minecraft.player == null) {
			return;
		}
		try {
			DuelFxSettings.tick(minecraft);
			DuelTime.tick(minecraft);
			SparkBurst.tick();
			tickSessions();
			tickBinds();
			if (ticks % 20 == 0) {
				SWINGS.values().removeIf(v -> ticks - v.received > 200);
				FIGHTERS.values().removeIf(f -> ticks - f.lastSync > 600 && sessionOf(f.entityId) == null);
				PREDICTED.values().removeIf(v -> ticks - v[2] > 60);
				CANCELLED.values().removeIf(at -> ticks - at > 60);
				if (PENDING_DECISIVE.size() > 4) {
					PENDING_DECISIVE.clear();
				}
			}
			DuelRingVisual.tick(minecraft);
			DuelMarks.tick(minecraft);
			CompanionSwordVisual.tick(minecraft);
			QiWaveVisual.tick(minecraft);
			MetalFlash.tick();
			ShockRing.tick();
			ScorchDecal.tick();
			DuelTrailRenderer.tick();
			DuelIntroFilm.tick(minecraft);
			DuelFinisherFilm.tick(minecraft);
			DuelCamera.tick(minecraft);
			DuelPreviews.tick(minecraft);
		} catch (RuntimeException exception) {
			warn("tick", exception);
		}
	}

	private static void tickSessions() {
		for (Iterator<SessionView> it = SESSIONS.values().iterator(); it.hasNext(); ) {
			SessionView s = it.next();
			if (s.endedAt >= 0L && ticks - s.endedAt > 80) {
				it.remove();
				continue;
			}
			if (s.endedAt < 0L && ticks - s.lastSync > 240) {
				// no sync for 12 s (out of range, a lost ENDED): forget it
				it.remove();
				continue;
			}
			if (s.live() && ticks % 40 == 0) {
				keepScene(s);
			}
		}
	}

	private static void tickBinds() {
		if (BINDS.isEmpty()) {
			return;
		}
		for (Iterator<BindView> it = BINDS.values().iterator(); it.hasNext(); ) {
			BindView b = it.next();
			if (ticks - b.lastSync > 8) {
				it.remove();
				SparkBurst.bindEnded(b);
				continue;
			}
			SparkBurst.bindTick(b, ticks);
		}
	}

	// ================================================================ queries

	/** Client ticks since the visuals started. */
	public static long ticks() {
		return ticks;
	}

	/** Every session in range (live or ending). */
	public static Collection<SessionView> sessions() {
		return SESSIONS.values();
	}

	/** The live (not ENDED) session that has {@code entityId} as a duellist, or null. */
	public static @Nullable SessionView sessionOf(int entityId) {
		for (SessionView s : SESSIONS.values()) {
			if (s.involves(entityId) && s.endedAt < 0L && s.phase != DuelCodes.PHASE_ENDED) {
				return s;
			}
		}
		return null;
	}

	public static @Nullable FighterView fighter(int entityId) {
		return FIGHTERS.get(entityId);
	}

	public static Collection<FighterView> fighters() {
		return FIGHTERS.values();
	}

	public static @Nullable SwingView swing(int entityId, int swingId) {
		return SWINGS.get(key(entityId, swingId));
	}

	/** The newest swing of {@code entityId} (or null). */
	public static @Nullable SwingView latestSwing(int entityId) {
		SwingView best = null;
		for (SwingView v : SWINGS.values()) {
			if (v.entityId == entityId && (best == null || v.received > best.received || v.received == best.received && v.startTick > best.startTick)) {
				best = v;
			}
		}
		return best;
	}

	public static Collection<SwingView> swings() {
		return SWINGS.values();
	}

	public static Collection<BindView> binds() {
		return BINDS.values();
	}

	/** True when {@code entityId} is a duellist of a live session (the 7b shot guard skips it). */
	public static boolean isLiveFighter(int entityId) {
		for (SessionView s : SESSIONS.values()) {
			if (s.live() && s.involves(entityId)) {
				return true;
			}
		}
		return false;
	}

	/** True when the spark group of a corrected prediction must fade (2 ticks, no sound replay). */
	public static boolean groupCancelled(int group) {
		return group != 0 && CANCELLED.containsKey(group);
	}

	private static void cancel(int group) {
		if (group != 0) {
			CANCELLED.put(group, ticks);
		}
	}

	/** The id of the local player (−1 without one). */
	public static int localId() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player == null ? -1 : minecraft.player.getId();
	}

	/** The client entity {@code id}, or null. */
	public static @Nullable Entity entity(int id) {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level == null || id == -1 ? null : minecraft.level.getEntity(id);
	}

	/** True when the local player is {@code a} or {@code b}. */
	public static boolean involved(int a, int b) {
		int self = localId();
		return self != -1 && (self == a || self == b);
	}

	static long key(int entityId, int swingId) {
		return ((long) entityId << 16) ^ (swingId & 0xFFFFL);
	}

	static long pairKey(int a, int b) {
		int lo = Math.min(a, b);
		int hi = Math.max(a, b);
		return ((long) lo << 32) | (hi & 0xFFFFFFFFL);
	}

	// ================================================================ lifecycle

	/** World change, disconnect: everything ends at once (the client displays are removed). */
	public static void clearAll() {
		SESSIONS.clear();
		FIGHTERS.clear();
		SWINGS.clear();
		BINDS.clear();
		PREDICTED.clear();
		CANCELLED.clear();
		PENDING_DECISIVE.clear();
		try {
			DuelTime.clear();
			SparkBurst.clear();
			MetalFlash.clear();
			ShockRing.clear();
			ScorchDecal.clear();
			DuelTrailRenderer.clear();
			DuelRingVisual.clear();
			DuelMarks.clear();
			CompanionSwordVisual.clear();
			QiWaveVisual.clear();
			DuelIntroFilm.clear();
			DuelFinisherFilm.clear();
			DuelCamera.clear();
			DuelPreviews.clear();
		} catch (RuntimeException exception) {
			warn("clear", exception);
		}
	}

	private static void safe(String where, Runnable action) {
		try {
			action.run();
		} catch (RuntimeException | LinkageError exception) {
			warn(where, exception);
		}
	}

	/** Logs at most a few warnings per session (presentation never breaks the game). */
	public static void warn(String where, Throwable exception) {
		if (warnings++ < 8) {
			Kultivator.LOGGER.warn("Kultivator: duel visuals error in {}", where, exception);
		}
	}

	private DuelFx() {
	}
}
