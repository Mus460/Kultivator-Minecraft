package dev.kultivator.client.dampak.world;

import dev.kultivator.Kultivator;
import dev.kultivator.client.anim.model.ModelFx;
import dev.kultivator.client.epic.EpicClock;
import dev.kultivator.client.fx.spec.CameraShake;
import dev.kultivator.client.pusakalangit.world.TitahWorld;
import dev.kultivator.data.Dao;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.dsl.Dampak;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.SigilPattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phase 10 client world effects (DESIGN 28.5-28.10, 28.13; module M3, contract C10.2): shock rings, debris, terrain
 * lift, the molten decal, ground sigils and monuments, all client-only and visual. Real blocks change only on the server
 * through {@code WorldBreaker} (its batches arrive here as {@link Payloads.DampakDebris} and are only drawn); without Mode
 * Kehancuran everything here is drawn from the client's own view of the terrain and touches nothing.
 * <p>
 * Every public method never throws and runs on the client thread. Presentation time advances by the 7b σ
 * ({@link EpicClock#sigma()}), so a hit-stop freezes the debris, cells, sigils and rings while vanilla entities, particles
 * and the server go on. Idle cost: one boolean per frame and a few list checks per tick.
 * <p>
 * Titah Langit (DESIGN 29, phase 10b): the events of the four TITAH specs ({@code titah_*}) go to
 * {@code client/pusakalangit/world/TitahWorld} instead of the Phase 10 flow (their radii reach 256 b and their timelines
 * are their own); the {@code titah*} methods below are its pass-through to these package systems, with the Titah scale.
 */
public final class DampakWorld {

	private static final int CAST_MEMORY = 1200;
	/** A job without a batch for this long is forgotten (its DONE may never come: out of range, a lost packet). */
	private static final int JOB_MEMORY = 200;
	private static final int LIFT_LEAD = 15;
	private static final int OWN_KEHANCURAN_WINDOW = 80;
	private static final int DEFAULT_RGB = 0xFFD54A;
	private static final int KEHANCURAN_RGB = 0xFF5A3C;
	private static final int DUST_RGB = 0xE6D8BC;

	/** What the client remembers of one cast (caster × spec). */
	private static final class CastState {
		final TechniqueSpec spec;
		final Dampak dampak;
		Vec3 castPos;
		Vec3 aim;
		int seed;
		boolean own;
		boolean climaxed;
		boolean lifted;
		boolean suspended;
		double lastSeen;
		double lastMinor = -10_000.0D;

		CastState(TechniqueSpec spec, Dampak dampak, Vec3 castPos, Vec3 aim, int seed, boolean own, double now) {
			this.spec = spec;
			this.dampak = dampak;
			this.castPos = castPos;
			this.aim = aim;
			this.seed = seed;
			this.own = own;
			this.lastSeen = now;
		}
	}

	/** A world job seen through its debris batches ({@code seen} = the clock of its last batch). */
	private record Job(long cast, boolean own, @Nullable String ring, int rgb, double seen) {
	}

	private record Scheduled(double at, long cast, Runnable action) {
	}

	private static final Map<Long, CastState> CASTS = new HashMap<>();
	private static final Map<Integer, Job> JOBS = new HashMap<>();
	private static final List<Scheduled> SCHEDULED = new ArrayList<>();
	private static final Map<String, Integer> WARNINGS = new HashMap<>();

	private static double clock;
	private static float sigma = 1.0F;
	private static boolean geometry;
	private static double lastOwnKehancuran = -10_000.0D;
	private static @Nullable ClientLevel lastLevel;
	private static boolean registered;

	// ================================================================== C10.2

	/** Registers the client tick, the world-quad renderer (after EpicRenderer) and the disconnect cleanup. */
	public static void init() {
		if (registered) {
			return;
		}
		registered = true;
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			try {
				tick(minecraft);
			} catch (RuntimeException exception) {
				warn("tick", exception);
			}
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(SigilRenderer::collect);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
	}

	/**
	 * One technique event of a spec (M2's hub calls it after the cinema). {@code own} = the local player's cast;
	 * {@code othersScale} = "Efek pemain lain" for anyone else's (0 = nothing).
	 */
	public static void onTechFx(Payloads.TechFx p, TechniqueSpec spec, Phase phase, boolean own, float othersScale) {
		try {
			if (TitahWorld.isTitah(spec)) {
				// Titah specs have their own world flow (TitahWorld drops the copy when the hub also calls it directly)
				TitahWorld.onTechFx(p, spec, phase, own, othersScale);
				return;
			}
			handleTechFx(p, spec, phase, own, othersScale);
		} catch (RuntimeException exception) {
			warn("techfx", exception);
		}
	}

	/** One {@code DampakDebris} batch of a WorldBreaker job (or a test / stop). */
	public static void onDebris(Payloads.DampakDebris p) {
		try {
			handleDebris(p);
		} catch (RuntimeException exception) {
			warn("debris", exception);
		}
	}

	/**
	 * A Kehancuran build was accepted ({@code KehancuranFxIds.IMPACT}, 28.13.1): a ShockRing of {@code max(4, 1.5 r)} and
	 * its second ring, and a short camera shake for the owner.
	 */
	public static void onKehancuranImpact(Vec3 pos, float radius, boolean own) {
		try {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.level == null || pos == null) {
				return;
			}
			boolean mine = own || clock - lastOwnKehancuran <= OWN_KEHANCURAN_WINDOW && minecraft.player != null
					&& minecraft.player.position().distanceToSqr(pos) <= (radius + 48.0D) * (radius + 48.0D);
			float r = Math.max(4.0F, 1.5F * Math.max(1.0F, radius));
			ShockRing.start(pos, r, "DUST", KEHANCURAN_RGB, mine, 0, true);
			if (mine) {
				double blocks = Math.max(1.0D, 2.1D * radius * radius * radius);
				float intensity = (float) Math.min(0.6D, 0.2D + 0.1D * Math.log10(blocks)) * DampakWorldBudget.power();
				CameraShake.add(Math.min(0.6F, intensity), 12, pos, r * 2.0F);
			}
		} catch (RuntimeException exception) {
			warn("impact", exception);
		}
	}

	/**
	 * Test hooks ({@code /sinematik dampak ...}, {@code uji dampak ...}): {@code puing} (n visual chunks), {@code angkat}
	 * (visual lift cells over a disc of radius n), {@code sigil} (pattern {@code arg}, radius n, lit node by node),
	 * {@code monumen} (model {@code arg} for n seconds), {@code cincin} (a ring of radius n, variant {@code arg}),
	 * {@code kawah} (a cooling decal of radius n, palette {@code arg}).
	 */
	public static void test(String what, Vec3 at, int n, @Nullable String arg) {
		try {
			handleTest(what, at, n, arg);
		} catch (RuntimeException exception) {
			warn("test", exception);
		}
	}

	/** Alive displays, cells, sigils, monuments and the last frame's quads. */
	public static String stats() {
		try {
			return String.format(Locale.ROOT,
					"Dampak dunia: tampilan %d (puing %d, sel %d, kristal %d, duri es %d), antre %d, cincin %d, sigil %d, grid %d, kawah %d, monumen %d, quad %d, tier %d, daya %.1f, puing&monumen %s",
					DampakDisplays.alive(), DampakDisplays.count(DampakDisplays.DEBRIS), DampakDisplays.count(DampakDisplays.CELL),
					DampakDisplays.count(DampakDisplays.CRYSTAL), DampakDisplays.count(DampakDisplays.SPIKE), DebrisFx.pending(), ShockRing.count(),
					GroundSigil.count(), TerrainLift.squares(), MoltenGlow.count(), Monument.count(), SigilRenderer.lastQuads(), DampakWorldBudget.tier(),
					DampakWorldBudget.power(), DampakWorldBudget.worldFx() ? "nyala" : "mati");
		} catch (RuntimeException exception) {
			return "Dampak dunia: -";
		}
	}

	/** Clears every Phase 10 world visual (stop, disconnect, dimension change). */
	public static void clear() {
		try {
			DampakDisplays.clear();
			DebrisFx.clear();
			TerrainLift.clear();
			GroundSigil.clear();
			ShockRing.clear();
			MoltenGlow.clear();
			Monument.clear();
			CASTS.clear();
			JOBS.clear();
			SCHEDULED.clear();
			geometry = false;
		} catch (RuntimeException exception) {
			warn("clear", exception);
		}
	}

	/** True while debris displays fly or wait (KehancuranFx halves its per-tick dust then, 28.13.1). */
	public static boolean debrisRunning() {
		try {
			return DebrisFx.running() || !JOBS.isEmpty();
		} catch (RuntimeException exception) {
			return false;
		}
	}

	// ================================================================== Titah Langit pass-through (DESIGN 29.8, contract C10b.6)

	/** The cast key (caster × spec net) the Phase 10 systems file a cast's pieces under. */
	public static long titahCastKey(int caster, int specNet) {
		return castKey(caster, specNet);
	}

	/** Presentation time in ticks (σ-scaled; a hit-stop holds it). */
	public static double titahClock(float partial) {
		return clock(partial);
	}

	/** The client surface height of a column (first free y above MOTION_BLOCKING_NO_LEAVES), or {@code fallback}. */
	public static double titahGround(ClientLevel level, double x, double z, double fallback) {
		return DampakDisplays.ground(level, x, z, fallback);
	}

	/**
	 * A Titah shock ring up to 256 b ({@link ShockRing#startTitah}); {@code variant} is a {@code Dampak.Ring} name (its
	 * front), {@code rgb} the band's tint as given (Bintang's VOID rings are tinted ember-violet, 29.15).
	 */
	public static void titahRing(Vec3 centre, float radius, String variant, int rgb, boolean own, int delay, boolean twin) {
		try {
			ShockRing.startTitah(centre, radius, variant, rgb & 0xFFFFFF, own, delay, twin);
		} catch (RuntimeException exception) {
			warn("titah ring", exception);
		}
	}

	/**
	 * Tingkat Titah (DESIGN 29.16.10): the far ring of a decree wider than this client draws, at its true radius (≤ 4096
	 * b); only its horizon arc within render distance draws, and nothing inside {@code nearCap} (the near ring's radius).
	 */
	public static void titahFarRing(Vec3 centre, float radius, float nearCap, String variant, int rgb, boolean own, int delay) {
		try {
			ShockRing.startTitahFar(centre, radius, nearCap, variant, rgb & 0xFFFFFF, own, delay);
		} catch (RuntimeException exception) {
			warn("titah far ring", exception);
		}
	}

	/**
	 * A Titah sigil of {@code pattern} (up to 256 b at tier 1, the true radius up to 4096 b from tier 2, of which only the
	 * visible part draws), faint until its nodes light; false when none is drawn.
	 */
	public static boolean titahSigil(ClientLevel level, long cast, @Nullable SigilPattern pattern, Vec3 castPos, Vec3 centre, float radius, int seed,
									 int rgb, boolean own, @Nullable String palette) {
		try {
			return GroundSigil.createTitah(level, cast, pattern, castPos, centre, radius, seed, rgb, own, palette) != null;
		} catch (RuntimeException exception) {
			warn("titah sigil", exception);
			return false;
		}
	}

	/** The next node of a cast's sigil lights (with its incoming lines). */
	public static void titahSigilIgnite(ClientLevel level, long cast, Vec3 at) {
		try {
			GroundSigil.ignite(level, cast, at);
		} catch (RuntimeException exception) {
			warn("titah ignite", exception);
		}
	}

	/** The whole sigil lights; with {@code burnTicks} &gt; 0 its lines burn as glowing trenches in {@code hotRgb}. */
	public static void titahSigilClimax(long cast, int burnTicks, int hotRgb) {
		try {
			GroundSigil.climaxTitah(cast, burnTicks, hotRgb);
		} catch (RuntimeException exception) {
			warn("titah sigil climax", exception);
		}
	}

	/** The node positions of a cast's sigil (ground y), in pattern order; empty when it has none. */
	public static List<Vec3> titahSigilNodes(long cast) {
		try {
			return GroundSigil.nodesOf(cast);
		} catch (RuntimeException exception) {
			return List.of();
		}
	}

	/** The cast ended without its climax: its sigil fades. */
	public static void titahSigilEnd(long cast) {
		GroundSigil.end(cast);
	}

	/** ABORT: the cast's sigil fades now, its lift slabs and crystals end. */
	public static void titahAbort(long cast) {
		try {
			GroundSigil.abort(cast);
			DampakDisplays.abort(cast);
		} catch (RuntimeException exception) {
			warn("titah abort", exception);
		}
	}

	/**
	 * Visual debris of the client's own surface ({@code wake}: along {@code lineFrom → centre}, else a burst around
	 * {@code centre}); displays only when the budget allows ({@link DampakWorldBudget#displays}), else particles.
	 */
	public static void titahDebris(Vec3 centre, float radius, int n, boolean wake, @Nullable Vec3 lineFrom, long cast, boolean own, boolean lowLoad) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || !finite(centre) || n <= 0) {
			return;
		}
		try {
			boolean displays = DampakWorldBudget.displays(own, lowLoad);
			DebrisFx.fake(level, centre, Math.max(1.0F, radius), n, wake ? DebrisFx.WAKE : DebrisFx.BURST, null, lineFrom, cast, own, displays);
		} catch (RuntimeException exception) {
			warn("titah debris", exception);
		}
	}

	/** A cooling molten decal (≤ 24 b) at {@code centre} in a {@code Dampak.Palette}'s glow colour. */
	public static void titahDecal(Vec3 centre, float radius, String palette, int rgb, boolean crack) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		try {
			MoltenGlow.decal(level, centre, radius, palette, rgb, crack);
		} catch (RuntimeException exception) {
			warn("titah decal", exception);
		}
	}

	/** One sky-carried three-layer slab ({@link TerrainLift#slab}); false when it could not be made. */
	public static boolean titahSlab(ClientLevel level, double x0, double z0, float size, double ground, long cast, int delay, Vec3 carry, int gridRgb,
									boolean grid) {
		try {
			return TerrainLift.slab(level, x0, z0, size, ground, cast, delay, carry, gridRgb, grid);
		} catch (RuntimeException exception) {
			warn("titah slab", exception);
			return false;
		}
	}

	/** A glowing crystal block display (grows after {@code delay}, stays {@code life} ticks, then shrinks away). */
	public static void titahCrystal(ClientLevel level, BlockState state, Vec3 at, long cast, int delay, int life) {
		try {
			GroundSigil.crystal(level, state, at, cast, delay, life);
		} catch (RuntimeException exception) {
			warn("titah crystal", exception);
		}
	}

	/** Live Dampak displays of the crystal kind (the Seven Stars' crystals count against the shared pool). */
	public static int titahCrystals() {
		return DampakDisplays.count(DampakDisplays.CRYSTAL);
	}

	/** Lift cells and slab layers alive. */
	public static int titahCells() {
		return DampakDisplays.count(DampakDisplays.CELL);
	}

	// ================================================================== package helpers

	/** Presentation time in ticks (σ-scaled), with the frame's partial tick. */
	static double clock(float partial) {
		return clock + partial * sigma;
	}

	/** True while any world quad may draw (the renderer's one boolean). */
	static boolean anyGeometry() {
		return geometry;
	}

	/** A system just added something to draw. */
	static void touchGeometry() {
		geometry = true;
	}

	static void warn(String where, Throwable exception) {
		int count = WARNINGS.merge(where, 1, Integer::sum);
		if (count <= 3) {
			Kultivator.LOGGER.warn("Kultivator: Dampak world effect error in {}", where, exception);
		}
	}

	// ================================================================== tick

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level != lastLevel) {
			lastLevel = level;
			clear();
			return;
		}
		if (level == null || minecraft.isPaused()) {
			return;
		}
		sigma = Math.max(0.0F, Math.min(1.0F, EpicClock.sigma()));
		clock += sigma;
		DampakDisplays.tick(level, sigma);
		DebrisFx.tick(level, clock);
		ShockRing.tick(level, clock);
		GroundSigil.tick(level, clock);
		TerrainLift.tick(level, clock);
		MoltenGlow.tick(level, clock);
		Monument.tick(level);
		if (!SCHEDULED.isEmpty()) {
			List<Scheduled> due = new ArrayList<>();
			for (Iterator<Scheduled> it = SCHEDULED.iterator(); it.hasNext(); ) {
				Scheduled scheduled = it.next();
				if (clock >= scheduled.at()) {
					due.add(scheduled);
					it.remove();
				}
			}
			for (Scheduled scheduled : due) {
				try {
					scheduled.action().run();
				} catch (RuntimeException exception) {
					warn("scheduled", exception);
				}
			}
		}
		if (minecraft.level.getGameTime() % 40L == 0L) {
			CASTS.values().removeIf(state -> clock - state.lastSeen > CAST_MEMORY);
			JOBS.values().removeIf(job -> clock - job.seen() > JOB_MEMORY);
		}
		geometry = ShockRing.active() || GroundSigil.active() || MoltenGlow.active() || TerrainLift.active() || TitahWorld.active();
	}

	private static void schedule(double delay, long cast, Runnable action) {
		if (SCHEDULED.size() < 64) {
			SCHEDULED.add(new Scheduled(clock + delay, cast, action));
		}
	}

	// ================================================================== technique events

	private static void handleTechFx(Payloads.TechFx p, TechniqueSpec spec, Phase phase, boolean own, float othersScale) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || p == null || spec == null) {
			return;
		}
		if (own && p.has(Payloads.TechFx.FLAG_KEHANCURAN)) {
			lastOwnKehancuran = clock;
		}
		Dampak dampak = spec.dampak();
		if (dampak == null) {
			return;
		}
		long cast = castKey(p.caster(), spec.netId());
		if (phase == Phase.ABORT) {
			GroundSigil.abort(cast);
			DampakDisplays.abort(cast);
			SCHEDULED.removeIf(scheduled -> scheduled.cast() == cast);
			CASTS.remove(cast);
			return;
		}
		if (!own && othersScale <= 0.0F) {
			return;
		}
		Vec3 pos = new Vec3(p.x(), p.y(), p.z());
		Vec3 to = new Vec3(p.tx(), p.ty(), p.tz());
		if (!finite(pos) || !finite(to)) {
			return;
		}
		CastState state = CASTS.get(cast);
		if (phase == Phase.CAST || phase == Phase.CHARGE) {
			if (state == null || state.climaxed) {
				state = new CastState(spec, dampak, pos, to, p.seed(), own, clock);
				CASTS.put(cast, state);
			} else {
				state.castPos = pos;
				state.aim = to;
				state.seed = p.seed();
			}
			state.lastSeen = clock;
			onCast(level, state, cast, p, phase);
		} else if (state != null) {
			state.lastSeen = clock;
		}
		boolean climax = p.has(Payloads.TechFx.FLAG_CLIMAX);
		if (dampak.sigilIgnite() != null && phase == dampak.sigilIgnite()) {
			Vec3 at = eventPoint(p, phase);
			if (GroundSigil.find(cast) == null && dampak.sigil() == SigilPattern.EVENT_CHAIN) {
				GroundSigil.create(level, cast, dampak.sigil(), state == null ? pos : state.castPos, at, dampak.sigilRadius(), p.seed(), colour(spec, dampak),
						own, paletteName(dampak));
			}
			GroundSigil.ignite(level, cast, at);
		}
		if (climax) {
			onClimax(level, state, cast, p, phase, spec, dampak, own);
		} else if (ultimate(spec) && minorStrike(p, phase)) {
			onMinorImpact(level, state, cast, p, phase, spec, dampak, own);
		}
		if (phase == Phase.END) {
			GroundSigil.end(cast);
			DampakDisplays.release(cast);
			CASTS.remove(cast);
		}
	}

	/** CAST / CHARGE: the sigil at the aim; visual suspended chunks and the lasting fields' visual lift. */
	private static void onCast(ClientLevel level, CastState state, long cast, Payloads.TechFx p, Phase phase) {
		Dampak dampak = state.dampak;
		boolean kehancuran = p.has(Payloads.TechFx.FLAG_KEHANCURAN);
		GroundSigil.Sigil existing = GroundSigil.find(cast);
		boolean moved = existing != null && existing.centre.distanceToSqr(state.aim) > 4.0D && existing.lit == 0 && Double.isNaN(existing.climaxAt);
		if (dampak.sigil() != SigilPattern.EVENT_CHAIN && (existing == null || moved)) {
			GroundSigil.create(level, cast, dampak.sigil(), state.castPos, state.aim, dampak.sigilRadius(), state.seed, colour(state.spec, dampak),
					state.own, paletteName(dampak));
		}
		if (phase != Phase.CAST || kehancuran) {
			return;
		}
		boolean lowLoad = p.has(Payloads.TechFx.FLAG_LOW_LOAD);
		boolean displays = DampakWorldBudget.displays(state.own, lowLoad);
		Dampak.Debris debris = dampak.debris();
		if (debris == Dampak.Debris.SUSPEND && !state.suspended) {
			state.suspended = true;
			int index = DampakWorldBudget.index();
			int n = DampakWorldBudget.scaled(DampakWorldBudget.FAKE_DEBRIS[index], state.own, DampakWorldBudget.FAKE_DEBRIS[index]);
			DebrisFx.fake(level, state.aim, Math.min(16.0F, Math.max(4.0F, dampak.sigilRadius() * 0.6F)), n, DebrisFx.SUSPEND, null, null, cast, state.own,
					displays);
		}
		if ((debris == Dampak.Debris.LIFT || debris == Dampak.Debris.IMPLODE) && state.spec.lasting() && !state.lifted) {
			state.lifted = true;
			liftVisual(level, state, cast, displays, LIFT_LEAD + 5);
		}
	}

	/** The climax event: ring, sigil star impacts, visual debris / lift / decal, monument. */
	private static void onClimax(ClientLevel level, @Nullable CastState state, long cast, Payloads.TechFx p, Phase phase, TechniqueSpec spec,
								 Dampak dampak, boolean own) {
		boolean kehancuran = p.has(Payloads.TechFx.FLAG_KEHANCURAN);
		boolean lowLoad = p.has(Payloads.TechFx.FLAG_LOW_LOAD);
		boolean displays = DampakWorldBudget.displays(own, lowLoad);
		Vec3 impact = impactOf(p, phase, state);
		Vec3 castPos = state != null ? state.castPos : new Vec3(p.x(), p.y(), p.z());
		int rgb = colour(spec, dampak);
		String palette = paletteName(dampak);
		if (state == null) {
			state = new CastState(spec, dampak, castPos, impact, p.seed(), own, clock);
			CASTS.put(cast, state);
		}
		state.climaxed = true;
		// the sigil (created late when the cast's start was missed) lights fully, with star impacts
		if (GroundSigil.find(cast) == null && dampak.sigil() != SigilPattern.EVENT_CHAIN) {
			GroundSigil.create(level, cast, dampak.sigil(), castPos, state.aim, dampak.sigilRadius(), p.seed(), rgb, own, palette);
		}
		int crystalLife = dampak.monumentSeconds() > 0 ? dampak.monumentSeconds() * 20 : 1200;
		GroundSigil.climax(level, cast, kehancuran, dampak.sigilTrench(), crystalLife, displays);
		DampakDisplays.release(cast);
		if (!ultimate(spec)) {
			// the lite rows (28.12: not ultimates) keep their mark and monument only
			raiseMonument(level, p, spec, dampak, castPos, impact, own);
			return;
		}
		// the shock ring (and its second ring 8 ticks later)
		float radius = ringRadius(p, dampak);
		String ring = dampak.ring() == null ? "DUST" : dampak.ring().name();
		ShockRing.start(impact, radius, ring, ShockRing.variantColor(ring, "DUST".equals(ring) ? blend(rgb, DUST_RGB) : rgb), own, 0, true);
		// without Mode Kehancuran the ground's answer is visual only (28.6.2, 28.7, 28.8.3)
		if (!kehancuran) {
			visualGround(level, state, cast, impact, radius, dampak, palette, rgb, own, displays);
		}
		raiseMonument(level, p, spec, dampak, castPos, impact, own);
	}

	/** The monument stays at the impact (28.10), facing away from the caster. */
	private static void raiseMonument(ClientLevel level, Payloads.TechFx p, TechniqueSpec spec, Dampak dampak, Vec3 castPos, Vec3 impact, boolean own) {
		if (p.has(Payloads.TechFx.FLAG_ECHO)) {
			// an echo repeats the strike, not the monument
			return;
		}
		String monument = monumentId(dampak.monument(), spec);
		if (monument != null) {
			float yaw = castPos.distanceToSqr(impact) > 1.0D ? ModelFx.yawTo(castPos, impact) : Math.floorMod(p.seed(), 360);
			Monument.raise(level, monument, impact, dampak.monumentScale(), dampak.monumentSeconds(),
					dampak.pose() == null ? null : dampak.pose().name(), yaw, p.seed(), own);
		}
	}

	/** EpicDirector's rule: a PAMUNGKAS of grade Abadi or higher, or any Dewa spec. */
	private static boolean ultimate(TechniqueSpec spec) {
		return spec.pamungkas() && spec.grade().ordinal() >= TechniqueGrade.ABADI.ordinal() || spec.dewa();
	}

	/** A landing strike that is not the climax (each falling sun, scatter crater, footprint, stomp). */
	private static boolean minorStrike(Payloads.TechFx p, Phase phase) {
		if (p.has(Payloads.TechFx.FLAG_DOT) || p.has(Payloads.TechFx.FLAG_PRE_IMPACT)) {
			return false;
		}
		return phase == Phase.IMPACT || phase == Phase.LAND || phase == Phase.PHANTOM;
	}

	/** A smaller answer of the ground: one ring, a handful of chunks (visual only without Kehancuran); ≤ 1 per 4 ticks. */
	private static void onMinorImpact(ClientLevel level, @Nullable CastState state, long cast, Payloads.TechFx p, Phase phase, TechniqueSpec spec,
									  Dampak dampak, boolean own) {
		if (state != null) {
			if (clock - state.lastMinor < 4.0D) {
				return;
			}
			state.lastMinor = clock;
		}
		Vec3 impact = phase == Phase.PHANTOM ? new Vec3(p.tx(), p.ty(), p.tz()) : new Vec3(p.x(), p.y(), p.z());
		if (!finite(impact)) {
			return;
		}
		int rgb = colour(spec, dampak);
		String ring = dampak.ring() == null ? "DUST" : dampak.ring().name();
		float radius = Math.max(3.0F, Math.min(12.0F, (p.radius() > 0.5F ? p.radius() : 3.0F) * 1.3F));
		ShockRing.start(impact, radius, ring, ShockRing.variantColor(ring, blend(rgb, DUST_RGB)), own, 0, false);
		if (!p.has(Payloads.TechFx.FLAG_KEHANCURAN) && dampak.debris() != Dampak.Debris.NONE && dampak.debris() != Dampak.Debris.CONVERT) {
			int index = DampakWorldBudget.index();
			int n = Math.max(1, DampakWorldBudget.scaled(DampakWorldBudget.FAKE_DEBRIS[index], own, DampakWorldBudget.FAKE_DEBRIS[index]) / 4);
			boolean displays = DampakWorldBudget.displays(own, p.has(Payloads.TechFx.FLAG_LOW_LOAD));
			DebrisFx.fake(level, impact, Math.max(2.0F, radius * 0.4F), n, DebrisFx.BURST, null, null, cast, own, displays);
		}
	}

	private static void visualGround(ClientLevel level, CastState state, long cast, Vec3 impact, float radius, Dampak dampak, String palette, int rgb,
									 boolean own, boolean displays) {
		int index = DampakWorldBudget.index();
		int fake = DampakWorldBudget.scaled(DampakWorldBudget.FAKE_DEBRIS[index], own, DampakWorldBudget.FAKE_DEBRIS[index]);
		float spread = Math.max(3.0F, Math.min(12.0F, radius * 0.45F));
		Dampak.Debris debris = dampak.debris() == null ? Dampak.Debris.BURST : dampak.debris();
		boolean decal = true;
		switch (debris) {
			case NONE -> decal = dampak.palette() != null && dampak.palette() != Dampak.Palette.NONE;
			case CONVERT -> {
				int n = DampakParticles.grant(DampakWorldBudget.scaled(DampakWorldBudget.BATCH_PARTICLES[index] * 2, own, 96));
				for (int i = 0; i < n; i++) {
					double angle = DampakParticles.random().nextDouble() * Math.PI * 2.0D;
					double r = spread * Math.sqrt(DampakParticles.random().nextDouble());
					double x = impact.x + Math.cos(angle) * r;
					double z = impact.z + Math.sin(angle) * r;
					double y = DampakDisplays.ground(level, x, z, impact.y) + 0.3D;
					DampakParticles.one(i % 2 == 0 ? ParticleTypes.ASH : ParticleTypes.PALE_OAK_LEAVES, x, y, z, 0.0D, 0.04D, 0.0D);
				}
			}
			case SUSPEND -> {
				if (!state.suspended) {
					DebrisFx.fake(level, impact, spread, fake, DebrisFx.BURST, null, null, cast, own, displays);
				}
			}
			case LIFT, IMPLODE -> {
				if (!state.lifted) {
					state.lifted = true;
					liftVisual(level, state, cast, displays, 10);
				}
				DebrisFx.fake(level, impact, spread, Math.max(2, fake / 3), debris == Dampak.Debris.IMPLODE ? DebrisFx.IMPLODE : DebrisFx.BURST,
						core(level, state.aim), null, cast, own, displays);
			}
			case WAKE -> DebrisFx.fake(level, impact, spread, fake, DebrisFx.WAKE, null, state.castPos, cast, own, displays);
			default -> DebrisFx.fake(level, impact, spread, fake, DebrisFx.BURST, null, null, cast, own, displays);
		}
		if (decal) {
			MoltenGlow.decal(level, impact, Math.max(3.0F, Math.min(16.0F, radius * 0.4F)), palette, rgb, true);
		}
	}

	/** The visual Gunung Terangkat of a cast (disc at the aim, or a strip along a PATH mark), lifting after {@code lead}. */
	private static void liftVisual(ClientLevel level, CastState state, long cast, boolean displays, int lead) {
		Dampak dampak = state.dampak;
		int mode = dampak.palette() == Dampak.Palette.TAIJI ? TerrainLift.FLIP
				: dampak.debris() == Dampak.Debris.IMPLODE ? TerrainLift.IMPLODE : TerrainLift.LIFT;
		Vec3 lineFrom = dampak.mark() == Dampak.Mark.PATH ? state.castPos : null;
		float radius = Math.max(4.0F, Math.min(20.0F, dampak.sigilRadius()));
		int rgb = colour(state.spec, dampak);
		TerrainLift.visual(level, state.aim, radius, lineFrom, mode, core(level, state.aim), cast, state.own, displays, rgb, lead);
		MoltenGlow.decal(level, state.aim, Math.min(12.0F, radius * 0.6F), paletteName(dampak), rgb, true);
	}

	// ================================================================== debris batches

	private static void handleDebris(Payloads.DampakDebris p) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || p == null) {
			return;
		}
		byte kind = p.kind();
		if (kind == Payloads.DampakDebris.STOP) {
			if (p.job() < 0) {
				clear();
			} else {
				JOBS.remove(p.job());
				TerrainLift.forget(p.job());
			}
			return;
		}
		int flags = p.flags();
		boolean own = (flags & Payloads.DampakDebris.FLAG_SELF) != 0 && minecraft.player != null && p.caster() == minecraft.player.getId();
		if (!own && DampakWorldBudget.othersFactor(false) <= 0.0F) {
			return;
		}
		TechniqueSpec spec = p.spec() >= 0 ? TechniqueRegistry.byNet(p.spec()) : null;
		Dampak dampak = spec == null ? null : spec.dampak();
		long cast = spec == null ? 0L : castKey(p.caster(), spec.netId());
		CastState state = cast == 0L ? null : CASTS.get(cast);
		Vec3 centre = new Vec3(p.cx(), p.cy(), p.cz());
		if (!finite(centre)) {
			return;
		}
		int rgb = spec == null ? KEHANCURAN_RGB : colour(spec, dampak);
		String ring = dampak == null || dampak.ring() == null ? "DUST" : dampak.ring().name();
		if (kind == Payloads.DampakDebris.DONE) {
			Job job = JOBS.remove(p.job());
			TerrainLift.forget(p.job());
			float radius = (float) Math.max(4.0D, Math.min(40.0D, 2.0D + Math.sqrt(Math.max(0, p.total())) / 2.0D));
			String variant = job != null && job.ring() != null ? job.ring() : ring;
			int colour = job != null ? job.rgb() : rgb;
			ShockRing.start(centre, radius, variant, ShockRing.variantColor(variant, blend(colour, DUST_RGB)), job != null ? job.own() : own, 0, false);
			return;
		}
		int size = Math.min(p.offsets().length, p.states().length);
		if (size <= 0) {
			return;
		}
		BlockPos base = BlockPos.containing(p.cx(), p.cy(), p.cz());
		BlockPos[] positions = new BlockPos[size];
		BlockState[] states = new BlockState[size];
		long[] packed = new long[size];
		for (int i = 0; i < size; i++) {
			int o = p.offsets()[i];
			positions[i] = base.offset(Payloads.DampakDebris.dx(o), Payloads.DampakDebris.dy(o), Payloads.DampakDebris.dz(o));
			states[i] = Block.stateById(p.states()[i]);
			packed[i] = positions[i].asLong();
		}
		boolean lowLoad = (flags & Payloads.DampakDebris.FLAG_LOW_LOAD) != 0;
		boolean displays = DampakWorldBudget.displays(own, lowLoad);
		switch (kind) {
			case Payloads.DampakDebris.CONVERT -> DebrisFx.convert(positions, states, own);
			case Payloads.DampakDebris.SURFACE -> {
				String palette = dampak == null ? "NONE" : paletteName(dampak);
				MoltenGlow.surface(packed, palette, rgb);
				int n = DampakParticles.grant(Math.min(12, size));
				for (int i = 0; i < n; i++) {
					Vec3 at = Vec3.atCenterOf(positions[i % size]).add(0.0D, 0.6D, 0.0D);
					DampakParticles.one(MoltenGlow.paletteParticle(palette), at.x, at.y, at.z, 0.0D, 0.03D, 0.0D);
				}
			}
			default -> {
				if (kind != Payloads.DampakDebris.CARVE && kind != Payloads.DampakDebris.TEST) {
					return;
				}
				JOBS.put(p.job(), new Job(cast, own, dampak == null ? null : ring, rgb, clock));
				Vec3 aim = state != null ? state.aim : centre;
				if ((flags & Payloads.DampakDebris.FLAG_LIFT) != 0) {
					boolean implode = (flags & Payloads.DampakDebris.FLAG_IMPLODE) != 0;
					int mode = dampak != null && dampak.palette() == Dampak.Palette.TAIJI ? TerrainLift.FLIP : implode ? TerrainLift.IMPLODE : TerrainLift.LIFT;
					TerrainLift.fromBatch(level, p.job(), positions, states, mode, implode ? core(level, aim) : null, cast, own, displays, rgb);
					return;
				}
				int mode = (flags & Payloads.DampakDebris.FLAG_IMPLODE) != 0 ? DebrisFx.IMPLODE
						: (flags & Payloads.DampakDebris.FLAG_SUSPEND) != 0 ? DebrisFx.SUSPEND
						: (flags & Payloads.DampakDebris.FLAG_WAKE) != 0 ? DebrisFx.WAKE : DebrisFx.BURST;
				Vec3 lineFrom = mode == DebrisFx.WAKE && state != null ? state.castPos : null;
				DebrisFx.batch(level, positions, states, centre, mode, mode == DebrisFx.IMPLODE ? core(level, aim) : null, lineFrom, cast, own, displays);
			}
		}
	}

	// ================================================================== tests

	private static void handleTest(String what, Vec3 at, int n, @Nullable String arg) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || what == null || at == null || !finite(at)) {
			return;
		}
		boolean displays = DampakWorldBudget.displays(true, false);
		long cast = castKey(-7, (int) (clock % 100_000));
		switch (what.toLowerCase(Locale.ROOT)) {
			case "puing" -> DebrisFx.fake(level, at, 5.0F, Math.max(1, Math.min(256, n)), DebrisFx.BURST, null, null, cast, true, displays);
			case "angkat" -> {
				float radius = Math.max(4.0F, Math.min(20.0F, n <= 0 ? 8.0F : n));
				TerrainLift.visual(level, at, radius, null, TerrainLift.LIFT, null, cast, true, displays, DEFAULT_RGB, LIFT_LEAD);
				MoltenGlow.decal(level, at, radius * 0.6F, "SPACE", DEFAULT_RGB, true);
			}
			case "sigil" -> {
				SigilPattern pattern = SigilPattern.BEIDOU_NINE;
				if (arg != null) {
					try {
						pattern = SigilPattern.valueOf(arg.toUpperCase(Locale.ROOT));
					} catch (IllegalArgumentException ignored) {
						// keep the Dipper
					}
				}
				float radius = n <= 0 ? 12.0F : Math.min(48.0F, n);
				Vec3 from = Minecraft.getInstance().player == null ? at.add(0.0D, 0.0D, -8.0D) : Minecraft.getInstance().player.position();
				GroundSigil.Sigil sigil = GroundSigil.create(level, cast, pattern, from, at, radius, (int) clock, DEFAULT_RGB, true, "LIGHTNING");
				if (sigil == null) {
					return;
				}
				int nodes = Math.max(1, Math.min(24, sigil.nodeCount));
				for (int i = 0; i < nodes; i++) {
					schedule(10 + i * 6, cast, () -> GroundSigil.ignite(level, cast, at));
				}
				schedule(16 + nodes * 6, cast, () -> {
					GroundSigil.climax(level, cast, false, false, 600, DampakWorldBudget.displays(true, false));
					ShockRing.start(at, radius, "DUST", DEFAULT_RGB, true, 0, true);
				});
			}
			case "monumen" -> Monument.raise(level, arg == null ? "heaven_punishing_sword" : arg, at, 3.0F, n <= 0 ? 30 : n, "PLANTED", 0.0F,
					(int) clock, true);
			case "cincin" -> {
				String variant = arg == null ? "DUST" : arg.toUpperCase(Locale.ROOT);
				ShockRing.start(at, n <= 0 ? 12.0F : n, variant, ShockRing.variantColor(variant, DUST_RGB), true, 0, true);
			}
			case "kawah" -> MoltenGlow.decal(level, at, n <= 0 ? 6.0F : n, arg == null ? "FIRE" : arg.toUpperCase(Locale.ROOT), DEFAULT_RGB, true);
			case "berhenti" -> clear();
			default -> {
			}
		}
	}

	// ================================================================== helpers

	private static long castKey(int caster, int specNet) {
		return (long) caster << 32 | specNet & 0xFFFFFFFFL;
	}

	/** Where an event lands: TRAVEL / CAST / CHARGE land at their target, everything else at its position. */
	private static Vec3 impactOf(Payloads.TechFx p, Phase phase, @Nullable CastState state) {
		Vec3 pos = new Vec3(p.x(), p.y(), p.z());
		Vec3 to = new Vec3(p.tx(), p.ty(), p.tz());
		if ((phase == Phase.TRAVEL || phase == Phase.CAST || phase == Phase.CHARGE) && to.distanceToSqr(pos) > 1.0D) {
			return to;
		}
		if (phase.extraIndex() >= 0 && to.distanceToSqr(pos) > 1.0D && state != null && to.distanceToSqr(state.aim) < pos.distanceToSqr(state.aim)) {
			return to;
		}
		return pos;
	}

	/** The point an ignition event names (its target when it has one, else its position). */
	private static Vec3 eventPoint(Payloads.TechFx p, Phase phase) {
		Vec3 pos = new Vec3(p.x(), p.y(), p.z());
		Vec3 to = new Vec3(p.tx(), p.ty(), p.tz());
		if (phase == Phase.PULSE || phase == Phase.IMPACT || phase == Phase.LAND) {
			return pos;
		}
		return to.distanceToSqr(pos) > 1.0D ? to : pos;
	}

	private static float ringRadius(Payloads.TechFx p, Dampak dampak) {
		float base = p.radius() > 0.5F ? p.radius() : dampak.sigilRadius() > 0.5F ? dampak.sigilRadius() * 0.6F : 6.0F;
		return Math.max(6.0F, Math.min(40.0F, base * 1.6F));
	}

	/** The core an IMPLODE pulls toward: 3 b over the ground at the aim. */
	private static Vec3 core(ClientLevel level, Vec3 aim) {
		return new Vec3(aim.x, DampakDisplays.ground(level, aim.x, aim.z, aim.y) + 3.0D, aim.z);
	}

	/** The Dao colour of a spec (own arts without a Dao: the second frame's colour, else gold). */
	private static int colour(TechniqueSpec spec, @Nullable Dampak dampak) {
		Dao dao = spec.dao();
		if (dao != null) {
			return dao.color();
		}
		if (dampak != null && dampak.frame2() >= 0) {
			return dampak.frame2() & 0xFFFFFF;
		}
		return DEFAULT_RGB;
	}

	private static String paletteName(@Nullable Dampak dampak) {
		return dampak == null || dampak.palette() == null ? "NONE" : dampak.palette().name();
	}

	/** The monument model id ({@code Dampak.MONUMENT_BY_DAO}: the first Dao's signature prop). */
	private static @Nullable String monumentId(@Nullable String id, TechniqueSpec spec) {
		if (id == null || id.isEmpty()) {
			return null;
		}
		if (!Dampak.MONUMENT_BY_DAO.equals(id)) {
			return id;
		}
		Dao dao = spec.dao();
		if (dao == null) {
			return "glyph_ring_gold";
		}
		return switch (dao) {
			case SWORD -> "giant_sword";
			case FIRE -> "heavenly_flame_orb";
			case ICE -> "ice_spear";
			case LIGHTNING -> "thunder_sword";
			case BODY -> "golden_fist";
			case LIFE -> "lotus_pedestal";
			case SHADOW -> "soul_flag";
			case SPACE -> "space_symbol";
		};
	}

	/** Half way between two colours (a Dao-tinted dust ring). */
	private static int blend(int a, int b) {
		int r = ((a >> 16 & 0xFF) + (b >> 16 & 0xFF)) / 2;
		int g = ((a >> 8 & 0xFF) + (b >> 8 & 0xFF)) / 2;
		int bl = ((a & 0xFF) + (b & 0xFF)) / 2;
		return r << 16 | g << 8 | bl;
	}

	private static boolean finite(Vec3 v) {
		return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
	}

	private DampakWorld() {
	}
}
