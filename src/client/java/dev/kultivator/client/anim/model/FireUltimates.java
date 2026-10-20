package dev.kultivator.client.anim.model;

import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.anim.CastPoses;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.client.fx.spec.FxBudget;
import dev.kultivator.technique.spec.dsl.FxLayer;
import dev.kultivator.technique.spec.dsl.Pose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The 3D choreography of the two Fire Dao ultimates, timed on a clock of its own (client ticks, paused with the game) so
 * every beat lands on the server's events without a random size change anywhere: props only grow in once, unfold or fold
 * by eased tilts, turn at a steady speed, breathe by light (never by scale) and leave by one fade.
 * <p>
 * <b>Api Kaisar</b> ({@code emperor_flame}, a 400 tick field ending in a radius-40 nova at the lotus): (a) charge 0-30, a
 * closed bud of glass petals rises from the ground while the glyph 帝 forms above it and qi motes gather; (b) bloom 30-80,
 * eight outer petals unfold to 72 degrees and eight inner ones to 40 degrees (smoothstep, the inner ring 6 ticks later),
 * the seed-pod heart and its violet flame grow in, the whole lotus turning slowly; (c) field 80 to the end - 18, flame
 * wisps circle on three spiral arms, petal embers drift up and the heart breathes by light; every PULSE sends a ground
 * ring out to the rim; (d) finale, the petals fold inward over 14 ticks and collapse into the heart (the caster pushes
 * both palms), then on EXTRA0 a white flash, two shock rings and petal shards flying out.
 * <p>
 * <b>Murka Langit Sembilan Matahari</b> ({@code nine_suns_heavens_fury}): during the 60 tick charge nine suns kindle one by
 * one 40 blocks above the aim (one in the middle, eight around it) with a golden crow circling the middle one; each
 * pre-impact TRAVEL sends the nearest sun streaking down onto its dense point (an accelerating drop with a flame streak),
 * where it flattens into a flash ring and leaves a cooling crater glow; EXTRA0 bursts a great sun over the aim with a
 * double ring and a wide crater.
 * <p>
 * Budgets: every prop goes through {@link ModelFx} (its per-tick and alive caps; Minimal keeps only the lotus heart and a
 * plain falling sun) and every particle through {@link FxBudget} (DETAIL), counts scaled by {@link EffectQuality}.
 */
public final class FireUltimates {

	private static final List<Run> RUNS = new ArrayList<>();
	private static long clock;
	private static int warnings;

	// ================================================================== Api Kaisar (emperor_flame)

	/** CAST of emperor_flame: the lotus at the field's centre, sized to its radius, for the field's duration. */
	static void lotus(int casterId, Vec3 centre, float radius, int duration) {
		abort(casterId, Lotus.class);
		RUNS.add(new Lotus(casterId, centre, radius, duration));
	}

	/** A PULSE of the flame field: a ground ring runs from the heart to the rim. */
	static void lotusPulse(int casterId) {
		Lotus lotus = find(casterId, Lotus.class);
		if (lotus != null) {
			lotus.pulse();
		}
	}

	/** EXTRA0 of emperor_flame (the nova at the lotus): the flash, the shock rings and the shards. */
	static void lotusNova(int casterId, Vec3 at) {
		Lotus lotus = find(casterId, Lotus.class);
		if (lotus == null) {
			lotus = new Lotus(casterId, at, 40.0F, 0);
			RUNS.add(lotus);
		}
		lotus.nova(at);
	}

	// ================================================================== nine suns (nine_suns_heavens_fury)

	/** CHARGE of nine_suns_heavens_fury: the nine suns kindle above the aim. */
	static void sunsCharge(int casterId, Vec3 aim, int chargeTicks, float sunScale) {
		abort(casterId, Suns.class);
		RUNS.add(new Suns(casterId, aim, chargeTicks, sunScale, true));
	}

	/** A pre-impact TRAVEL: the nearest sky sun (or a new one 40 blocks up) streaks down onto {@code land}. */
	static void sunsFall(int casterId, Vec3 land, int fall, float sunScale) {
		suns(casterId, land, sunScale).fall(land, fall);
	}

	/** The IMPACT of one sun: the flash ring and the crater glow. */
	static void sunsImpact(int casterId, Vec3 at, float radius, float sunScale) {
		suns(casterId, at, sunScale).impact(at, radius);
	}

	/** EXTRA0: the great strike at the aim. */
	static void sunsGrand(int casterId, Vec3 at, float radius, float sunScale) {
		suns(casterId, at, sunScale).grand(at, radius);
	}

	private static Suns suns(int casterId, Vec3 at, float sunScale) {
		Suns suns = find(casterId, Suns.class);
		if (suns == null) {
			// the charge was not seen (joined late, out of range): the suns fall from the sky without a formation
			suns = new Suns(casterId, at, 0, sunScale, false);
			RUNS.add(suns);
		}
		return suns;
	}

	// ================================================================== the clock

	/** Once per unpaused client tick (AnimClient, after ModelFx). */
	public static void tick(ClientLevel level) {
		if (dev.kultivator.client.epic.EpicClock.frozen() && !RUNS.isEmpty()) {
			// Phase 10 (DESIGN 28.4.2): a hit-stop holds the choreography's clock (σ = 0) for its 2-3 ticks
			return;
		}
		// a run made since the last tick plays its tick-0 beats now; the clock moves on after the pass
		for (Iterator<Run> it = RUNS.iterator(); it.hasNext(); ) {
			Run run = it.next();
			boolean keep;
			try {
				keep = run.tick(level);
			} catch (RuntimeException exception) {
				keep = false;
				if (warnings++ < 3) {
					Kultivator.LOGGER.warn("Kultivator: fire ultimate choreography error", exception);
				}
			}
			if (!keep) {
				run.endProps(6);
				it.remove();
			}
		}
		clock++;
	}

	/** Ends every run of a caster (ABORT, a replaced cast). */
	static void abort(int casterId) {
		abort(casterId, Run.class);
	}

	/** Drops every run (world change, disconnect; the props themselves are cleared by ModelFx). */
	public static void clear() {
		RUNS.clear();
	}

	private static void abort(int casterId, Class<? extends Run> kind) {
		for (Iterator<Run> it = RUNS.iterator(); it.hasNext(); ) {
			Run run = it.next();
			if (run.casterId == casterId && kind.isInstance(run)) {
				run.endProps(6);
				it.remove();
			}
		}
	}

	private static <T extends Run> @Nullable T find(int casterId, Class<T> kind) {
		for (int i = RUNS.size() - 1; i >= 0; i--) {
			Run run = RUNS.get(i);
			if (run.casterId == casterId && kind.isInstance(run)) {
				return kind.cast(run);
			}
		}
		return null;
	}

	// ================================================================== a run: timed beats and an emitter

	private abstract static class Run {
		final int casterId;
		final long start = clock;
		private final List<Beat> beats = new ArrayList<>();
		final List<ModelHandle> props = new ArrayList<>();

		Run(int casterId) {
			this.casterId = casterId;
		}

		/** Ticks since the run began. */
		int age() {
			return (int) (clock - start);
		}

		/** Runs {@code action} at run tick {@code at} (now when it is already past). */
		void at(int at, Runnable action) {
			beats.add(new Beat(at, action));
		}

		boolean tick(ClientLevel level) {
			int age = age();
			for (int i = 0; i < beats.size(); i++) {
				Beat beat = beats.get(i);
				if (!beat.done && beat.at <= age) {
					beat.done = true;
					beat.action.run();
				}
			}
			beats.removeIf(beat -> beat.done);
			emit(level, age);
			props.removeIf(handle -> !handle.alive());
			return !finished(age);
		}

		abstract void emit(ClientLevel level, int age);

		abstract boolean finished(int age);

		@Nullable ModelHandle show(String id, Vec3 pos, float scale, float yaw, float pitch, int ticks, AnimStep... anims) {
			ModelHandle handle = ModelFx.show(new ModelRequest(id, pos, scale, yaw, pitch, 0.0F, Math.max(1, ticks), List.of(anims), -1, Vec3.ZERO,
					false, true, casterId, FxLayer.Priority.CORE));
			if (handle != null) {
				props.add(handle);
			}
			return handle;
		}

		void endProps(int fade) {
			for (ModelHandle handle : props) {
				handle.end(fade);
			}
			props.clear();
		}

		/** A shock or flash ring: grows out once, dims to its faint copy, then is gone. */
		void ring(String id, String faded, Vec3 at, float scale, int grow, int delay) {
			at(age() + delay, () -> {
				ModelHandle ring = show(id, at, scale, 0.0F, 0.0F, grow + 4, step(Anim.GROW, 0, grow, 0.05F, 0.0F));
				if (ring != null) {
					int now = age();
					at(now + grow - 3, () -> ring.swap(faded));
					at(now + grow + 1, () -> ring.end(1));
				}
			});
		}

		void pose(Pose pose, int hold) {
			CastPoses.start(casterId, pose, hold);
		}
	}

	private static final class Beat {
		final int at;
		final Runnable action;
		boolean done;

		Beat(int at, Runnable action) {
			this.at = at;
			this.action = action;
		}
	}

	// ================================================================== the lotus

	private static final class Lotus extends Run {
		/** The petal models are 24 pixels (1.5 blocks) long at scale 1. */
		private static final float PETAL_BLOCKS = 1.5F;
		private static final int CHARGE = 30;
		private static final int BLOOM_END = 80;
		private static final float OUT_CLOSED = -6.0F;
		private static final float OUT_OPEN = 72.0F;
		private static final float IN_CLOSED = -12.0F;
		private static final float IN_OPEN = 40.0F;
		/** Degrees per tick the lotus turns (about 72 s a turn). */
		private static final float TURN = 0.3F;

		final Vec3 centre;
		final float radius;
		final int duration;
		final int fold;
		final float outLength;
		final float inLength;
		@Nullable ModelHandle core;
		@Nullable ModelHandle flame;
		boolean nova;
		int novaAt = -1;

		Lotus(int casterId, Vec3 centre, float radius, int duration) {
			super(casterId);
			this.centre = centre;
			this.radius = Mth.clamp(radius, 16.0F, 40.0F);
			// a nova without its field (late join) plays the finale only
			this.duration = duration <= 0 ? 0 : Mth.clamp(duration, 100, 1200);
			this.fold = this.duration - 18;
			// outer petals reach the rim when open: base ring + length * sin(72)
			this.outLength = (0.98F * this.radius - 0.07F * this.radius) / Mth.sin(OUT_OPEN * Mth.DEG_TO_RAD);
			this.inLength = outLength * 0.62F;
			if (this.duration == 0) {
				return;
			}
			if (!EffectQuality.minimal()) {
				// four props a tick (the display caps): the outer ring first, then the inner one
				for (int i = 0; i < 8; i++) {
					int index = i;
					at(i / 4, () -> petal(true, index));
					at(2 + i / 4, () -> petal(false, index));
				}
			}
			at(24, this::heart);
			at(34, this::flame);
			at(BLOOM_END, () -> {
				if (core != null) {
					core.lightCycle(8, 60, 0);
				}
				if (flame != null) {
					flame.lightCycle(7, 60, 30);
				}
			});
			// the finale: palms push as the petals fold, the heart burns steady
			at(fold - 3, () -> pose(Pose.TWIN_PALM, 20));
			at(fold, () -> {
				if (core != null) {
					core.lightCycle(15, 0, 0);
				}
				if (flame != null) {
					flame.lightCycle(15, 0, 0);
				}
			});
		}

		/** One glass petal: rises as part of the bud, unfolds, turns with the lotus, folds back and collapses. */
		private void petal(boolean outer, int index) {
			int c = age();
			float length = outer ? outLength : inLength;
			float scale = length / PETAL_BLOCKS;
			float closed = outer ? OUT_CLOSED : IN_CLOSED;
			float open = outer ? OUT_OPEN : IN_OPEN;
			int bloom = outer ? CHARGE : CHARGE + 6;
			float base = radius * (outer ? 0.07F : 0.045F);
			float yaw = (outer ? 0.0F : 22.5F) + index * 45.0F;
			float depth = length * 0.36F + 1.5F;
			// every step is timed on the run's clock (the petal was made c ticks after the run began)
			show(outer ? "emperor_lotus_petal_outer" : "emperor_lotus_petal_inner", centre.add(0.0D, outer ? 0.1D : 0.16D, 0.0D), scale, yaw, closed,
					duration + 1 - c,
					step(Anim.GROW, -c, 70, 0.32F, 0.0F),
					step(Anim.RISE, -c, CHARGE, depth, 0.0F),
					step(Anim.REVOLVE, -c, duration + 40, TURN, base),
					step(Anim.TILT, bloom - c, 44, open - closed, 0.0F),
					step(Anim.TILT, fold - c, 14, -(open - closed) - 14.0F, 0.0F),
					step(Anim.SHRINK, duration - 8 - c, 8, 0.0F, 0.0F));
		}

		/** The seed-pod heart with its stamens (the one prop Minimal keeps, with the flame). */
		private void heart() {
			float scale = 0.38F * radius;
			core = show("emperor_lotus_core", centre.add(0.0D, 0.08D, 0.0D), scale, 0.0F, 0.0F, duration + 30 - age(),
					step(Anim.GROW, 0, 30, 0.1F, 0.0F), step(Anim.SPIN, 0, duration + 30, 0.4F, 0.0F));
		}

		private void flame() {
			float scale = 0.38F * radius;
			flame = show("emperor_lotus_flame", centre.add(0.0D, 0.08D, 0.0D), scale, 45.0F, 0.0F, duration + 30 - age(),
					step(Anim.GROW, 0, 40, 0.1F, 0.0F), step(Anim.SPIN, 0, duration + 30, -0.7F, 0.0F));
		}

		void pulse() {
			if (age() < BLOOM_END - 10 || age() >= fold) {
				return;
			}
			spend(Qi.groundRing(0xB878FF, 3.0F, radius * 2.0F, 18), centre.x, centre.y + 0.15D, centre.z, 0.0D, 0.0D, 0.0D);
		}

		void nova(Vec3 at) {
			if (nova) {
				return;
			}
			nova = true;
			novaAt = age();
			Vec3 heart = at;
			// the heart flares once (one swell, full bright) and goes out with the flash
			for (ModelHandle prop : new ModelHandle[] {core, flame}) {
				if (prop != null) {
					prop.lightCycle(15, 0, 0);
					prop.play(step(Anim.PULSE, 0, 6, 0.1F, 0.0F));
				}
			}
			at(novaAt + 5, () -> {
				if (core != null) {
					core.end(10);
				}
				if (flame != null) {
					flame.end(4);
				}
			});
			if (!EffectQuality.minimal()) {
				float ringScale = 0.8F * radius;
				ring("emperor_shock_ring", "emperor_shock_ring_fade", heart.add(0.0D, 0.6D, 0.0D), ringScale, 12, 0);
				ring("emperor_shock_ring", "emperor_shock_ring_fade", heart.add(0.0D, 3.0D, 0.0D), ringScale * 0.78F, 12, 4);
			}
			// the white flash at the heart, the ground wave, shards and petals flying out, sparks shooting up
			ClientLevel level = Minecraft.getInstance().level;
			if (level == null) {
				return;
			}
			RandomSource random = level.getRandom();
			spend(Qi.ring(0xFFFFFF, 2.0F, 22.0F, 8), heart.x, heart.y + 4.0D, heart.z, 0.0D, 0.0D, 0.0D);
			spend(Qi.groundRing(0xD8B0FF, 4.0F, radius * 2.0F, 20), heart.x, heart.y + 0.2D, heart.z, 0.0D, 0.0D, 0.0D);
			int shards = EffectQuality.scale(40);
			for (int i = 0; i < shards; i++) {
				double a = Math.PI * 2.0D * i / shards + random.nextDouble() * 0.2D;
				double speed = 0.9D + random.nextDouble() * 0.7D;
				double y = heart.y + 1.0D + random.nextDouble() * 9.0D;
				spend(Qi.shard(i % 3 == 0 ? 0xFFFFFF : 0xE0B8FF, 0.6F, 30), heart.x, y, heart.z, Math.cos(a) * speed, 0.1D + random.nextDouble() * 0.3D,
						Math.sin(a) * speed);
			}
			int petals = EffectQuality.scale(24);
			for (int i = 0; i < petals; i++) {
				double a = Math.PI * 2.0D * (i + 0.5D) / petals;
				double speed = 0.5D + random.nextDouble() * 0.5D;
				spend(Qi.petal(i % 2 == 0 ? 0xE0A0FF : 0xFF80D8, 0.6F, 40), heart.x, heart.y + 3.0D, heart.z, Math.cos(a) * speed, 0.25D,
						Math.sin(a) * speed);
			}
			int sparks = EffectQuality.scale(20);
			for (int i = 0; i < sparks; i++) {
				spend(Qi.spark(0xF8F0FF, 0.5F, 16), heart.x + (random.nextDouble() - 0.5D) * 3.0D, heart.y + 2.0D, heart.z + (random.nextDouble() - 0.5D) * 3.0D,
						(random.nextDouble() - 0.5D) * 0.2D, 0.8D + random.nextDouble() * 0.6D, (random.nextDouble() - 0.5D) * 0.2D);
			}
		}

		/** The lotus's turn (degrees) at run tick {@code age}, the same eased curve as the petals' REVOLVE. */
		private float turn(int age) {
			float into = age + 1.0F;
			return TURN * (into < 40.0F ? into * into / 80.0F : into - 20.0F);
		}

		@Override
		void emit(ClientLevel level, int age) {
			if (nova || duration == 0 || age > duration) {
				return;
			}
			RandomSource random = level.getRandom();
			double cx = centre.x, cy = centre.y, cz = centre.z;
			if (age < CHARGE) {
				// (a) qi gathering into the rising bud
				for (int i = 0, n = EffectQuality.scale(3); i < n; i++) {
					double a = random.nextDouble() * Math.PI * 2.0D;
					double r = radius * (0.35D + random.nextDouble() * 0.25D);
					double x = cx + Math.cos(a) * r, y = cy + 0.5D + random.nextDouble() * 3.0D, z = cz + Math.sin(a) * r;
					spend(Qi.mote(i % 2 == 0 ? 0xE0C0FF : 0xFFD878, 0.5F, 20), x, y, z, (cx - x) * 0.05D, 0.04D, (cz - z) * 0.05D);
				}
				if (age % 2 == 0) {
					spend(Qi.spark(0xFFE8A0, 0.35F, 14), cx, cy + 1.0D, cz, 0.0D, 0.35D, 0.0D);
				}
				return;
			}
			if (age < BLOOM_END) {
				// (b) embers shed from the opening petal tips
				float spin = turn(age);
				for (int i = 0, n = EffectQuality.scale(4); i < n; i++) {
					// along one of the sixteen petals (Minecraft yaw: the petal's front looks along (-sin, 0, cos))
					double yaw = Math.toRadians(random.nextInt(16) * 22.5D + spin);
					double dx = -Math.sin(yaw), dz = Math.cos(yaw);
					double r = radius * (0.45D + random.nextDouble() * 0.45D);
					spend(Qi.petal(0xF0D0FF, 0.45F, 40), cx + dx * r, cy + 2.0D + random.nextDouble() * 6.0D, cz + dz * r, dx * 0.08D, 0.04D, dz * 0.08D);
				}
				return;
			}
			if (age < fold) {
				// (c) the field: wisps circling on three spiral arms, embers drifting up, sparks from the heart
				for (int k = 0; k < 3; k++) {
					if (EffectQuality.minimal() && k > 0) {
						break;
					}
					double phase = (age * 0.011D + k / 3.0D) % 1.0D;
					double r = radius * (0.2D + 0.7D * phase);
					double a = Math.toRadians(k * 120.0D + age * 4.0D + phase * 220.0D);
					double x = cx + Math.cos(a) * r, z = cz + Math.sin(a) * r;
					double speed = 0.18D;
					spend(Qi.flame(k == 1 ? 0xE050C0 : 0xB070FF, 0.9F, 26), x, cy + 1.0D + r * 0.12D, z, -Math.sin(a) * speed, 0.03D, Math.cos(a) * speed);
				}
				if (age % 2 == 0) {
					for (int i = 0, n = EffectQuality.scale(2); i < n; i++) {
						double a = random.nextDouble() * Math.PI * 2.0D;
						double r = radius * Math.sqrt(random.nextDouble()) * 0.9D;
						spend(Qi.petal(0xF4E0FF, 0.35F, 40), cx + Math.cos(a) * r, cy + 2.0D + random.nextDouble() * 4.0D, cz + Math.sin(a) * r,
								(random.nextDouble() - 0.5D) * 0.03D, 0.05D, (random.nextDouble() - 0.5D) * 0.03D);
					}
				}
				if (age % 3 == 0) {
					for (int i = 0, n = EffectQuality.scale(2); i < n; i++) {
						spend(Qi.spark(0xF8F0FF, 0.4F, 18), cx + (random.nextDouble() - 0.5D) * 2.0D, cy + radius * 0.2D, cz + (random.nextDouble() - 0.5D) * 2.0D,
								(random.nextDouble() - 0.5D) * 0.05D, 0.3D + random.nextDouble() * 0.1D, (random.nextDouble() - 0.5D) * 0.05D);
					}
				}
				return;
			}
			// (d) the fold: light streams in from the rim to the heart
			for (int i = 0, n = EffectQuality.scale(4); i < n; i++) {
				double a = random.nextDouble() * Math.PI * 2.0D;
				double r = radius * (0.5D + random.nextDouble() * 0.4D);
				double x = cx + Math.cos(a) * r, y = cy + 1.0D + random.nextDouble() * 4.0D, z = cz + Math.sin(a) * r;
				spend(Qi.spark(0xF0E0FF, 0.4F, 12), x, y, z, (cx - x) * 0.08D, (cy + 4.0D - y) * 0.08D, (cz - z) * 0.08D);
			}
		}

		@Override
		boolean finished(int age) {
			return nova ? age > novaAt + 40 : age > duration + 40;
		}
	}

	// ================================================================== the nine suns

	private static final class Suns extends Run {
		private static final double SKY = 40.0D;

		final Vec3 aim;
		final int charge;
		final float sunScale;
		final List<SkySun> sky = new ArrayList<>(9);
		final List<Crater> craters = new ArrayList<>();
		@Nullable ModelHandle crow;
		int grandAt = -1;
		int lastActive;

		Suns(int casterId, Vec3 aim, int charge, float sunScale, boolean formation) {
			super(casterId);
			this.aim = aim;
			this.charge = Math.max(0, charge);
			this.sunScale = sunScale;
			this.lastActive = 0;
			if (!formation || EffectQuality.minimal()) {
				return;
			}
			// one sun kindles every 5 ticks: the middle one first, then the ring of eight
			for (int k = 0; k < 9; k++) {
				Vec3 at = k == 0 ? aim.add(0.0D, SKY, 0.0D)
						: aim.add(Math.cos(Math.toRadians((k - 1) * 45.0D)) * 14.0D, SKY - 2.0D + (k % 2) * 1.5D, Math.sin(Math.toRadians((k - 1) * 45.0D)) * 14.0D);
				SkySun sun = new SkySun(at);
				sky.add(sun);
				int index = k;
				at(2 + 5 * k, () -> kindle(sun, index));
			}
			at(16, this::crow);
		}

		private void kindle(SkySun sun, int index) {
			if (sun.used) {
				return;
			}
			int life = Math.max(40, charge + 140 - age());
			sun.handle = show("nine_sun_orb", sun.pos, sunScale, index * 40.0F, 0.0F, life, step(Anim.GROW, 0, 12, 0.05F, 0.0F),
					step(Anim.SPIN, 0, life, 1.2F, 0.0F));
			if (sun.handle != null) {
				sun.handle.lightCycle(9, 36, index * 4);
			}
		}

		/** A golden crow circling the middle sun (terinspirasi dari Sanzuwu, the three-legged sun crow of Chinese myth). */
		private void crow() {
			if (sky.isEmpty()) {
				return;
			}
			int life = Math.max(40, charge + 80 - age());
			crow = show("nine_sun_crow", sky.get(0).pos, 2.2F, 0.0F, 0.0F, life, step(Anim.GROW, 0, 10, 0.2F, 0.0F),
					step(Anim.REVOLVE, 0, life, 3.0F, -(sunScale * 1.5F)), step(Anim.BOB, 0, life, 0.4F, 30.0F));
		}

		void fall(Vec3 land, int fall) {
			lastActive = age();
			Vec3 target = land.add(0.0D, sunScale * 0.35D, 0.0D);
			SkySun from = nearest(land.add(0.0D, SKY, 0.0D));
			ModelHandle sun;
			Vec3 start;
			if (from != null) {
				from.used = true;
				sun = from.handle;
				start = from.pos;
				sun.lightCycle(15, 0, 0);
				// the accelerating drop keeps the sun's own (level) turn, so it lands without a twist
				sun.play(new AnimStep(Anim.SLIDE, 0, fall, 0.0F, 2.0F, target));
			} else {
				start = target.add(0.0D, SKY, 0.0D);
				sun = show("nine_sun_orb", target, sunScale, 0.0F, 0.0F, fall + 12, step(Anim.FALL, 0, fall, (float) SKY, 0.0F),
						step(Anim.SPIN, 0, fall + 12, 4.0F, 0.0F));
			}
			if (sun != null) {
				ModelHandle landed = sun;
				landed.play(step(Anim.FLATTEN, fall, 4, 0.12F, 1.6F));
				at(age() + fall + 4, () -> landed.end(3));
			}
			if (!EffectQuality.minimal()) {
				// the streak: faces the fall (its tail trails behind), drops with the sun and is gone at the landing
				ModelHandle trail = show("nine_sun_trail", start, sunScale * 1.15F, ModelFx.yawTo(start, target), ModelFx.pitchTo(start, target), fall + 4,
						step(Anim.GROW, 0, 3, 0.4F, 0.0F), new AnimStep(Anim.SLIDE, 0, fall, 0.0F, 2.0F, target));
				if (trail != null) {
					at(age() + fall, () -> trail.end(2));
				}
			}
			if (crow != null && remaining() == 0) {
				crow.play(step(Anim.DISSOLVE, 0, 10, 0.0F, 0.0F));
			}
		}

		void impact(Vec3 at, float radius) {
			lastActive = age();
			float r = Mth.clamp(radius, 4.0F, 16.0F);
			if (!EffectQuality.minimal()) {
				ring("nine_sun_flash_ring", "nine_sun_flash_ring_fade", at.add(0.0D, 0.35D, 0.0D), r * 0.72F, 8, 0);
				crater(at, r * 0.63F, 70);
			}
			spend(Qi.groundRing(0xFFD060, 2.0F, r * 2.2F, 14), at.x, at.y + 0.2D, at.z, 0.0D, 0.0D, 0.0D);
			ClientLevel level = Minecraft.getInstance().level;
			if (level == null) {
				return;
			}
			RandomSource random = level.getRandom();
			for (int i = 0, n = EffectQuality.scale(14); i < n; i++) {
				double a = Math.PI * 2.0D * i / n;
				double speed = 0.5D + random.nextDouble() * 0.4D;
				spend(Qi.spark(0xFFE070, 0.45F, 14), at.x, at.y + 0.6D, at.z, Math.cos(a) * speed, 0.15D + random.nextDouble() * 0.25D, Math.sin(a) * speed);
			}
		}

		void grand(Vec3 at, float radius) {
			lastActive = age();
			grandAt = age();
			float r = Mth.clamp(radius, 8.0F, 32.0F);
			// suns still hanging (fewer dense points than suns) fall into the great strike
			for (SkySun left : sky) {
				if (!left.used && left.handle != null && left.handle.alive()) {
					left.used = true;
					ModelHandle sun = left.handle;
					sun.play(new AnimStep(Anim.SLIDE, 0, 6, 0.0F, 2.0F, at.add(0.0D, 3.0D, 0.0D)));
					at(age() + 6, () -> sun.end(3));
				}
			}
			if (crow != null && crow.alive()) {
				crow.play(step(Anim.DISSOLVE, 0, 10, 0.0F, 0.0F));
			}
			pose(Pose.TWIN_PALM, 12);
			ModelHandle burst = show("nine_sun_orb", at.add(0.0D, 3.0D, 0.0D), sunScale * 2.2F, 0.0F, 0.0F, 16, step(Anim.GROW, 0, 4, 0.3F, 0.0F),
					step(Anim.FLATTEN, 4, 6, 0.1F, 1.8F));
			if (burst != null) {
				at(age() + 10, () -> burst.end(4));
			}
			if (!EffectQuality.minimal()) {
				ring("nine_sun_flash_ring", "nine_sun_flash_ring_fade", at.add(0.0D, 0.4D, 0.0D), r * 0.68F, 10, 0);
				ring("nine_sun_flash_ring", "nine_sun_flash_ring_fade", at.add(0.0D, 2.5D, 0.0D), r * 0.5F, 10, 3);
				crater(at, r * 0.57F, 90);
			}
			ClientLevel level = Minecraft.getInstance().level;
			if (level == null) {
				return;
			}
			RandomSource random = level.getRandom();
			spend(Qi.ring(0xFFFFF0, 3.0F, 28.0F, 8), at.x, at.y + 3.0D, at.z, 0.0D, 0.0D, 0.0D);
			spend(Qi.groundRing(0xFFC040, 4.0F, r * 2.0F, 20), at.x, at.y + 0.2D, at.z, 0.0D, 0.0D, 0.0D);
			for (int i = 0, n = EffectQuality.scale(36); i < n; i++) {
				double a = Math.PI * 2.0D * i / n + random.nextDouble() * 0.15D;
				double speed = 0.8D + random.nextDouble() * 0.8D;
				spend(Qi.spark(i % 3 == 0 ? 0xFFFFFF : 0xFFC040, 0.55F, 18), at.x, at.y + 1.0D + random.nextDouble() * 3.0D, at.z, Math.cos(a) * speed,
						0.1D + random.nextDouble() * 0.4D, Math.sin(a) * speed);
			}
		}

		/** A crater glow decal: grows in, cools down by light, then shrinks away slowly. */
		private void crater(Vec3 at, float scale, int life) {
			ModelHandle crater = show("nine_sun_crater", at.add(0.0D, 0.06D, 0.0D), scale, 0.0F, 0.0F, life, step(Anim.GROW, 0, 5, 0.5F, 0.0F));
			if (crater == null) {
				return;
			}
			int now = age();
			at(now + 10, () -> crater.lightTo(5, life - 24));
			at(now + life - 14, () -> crater.end(14));
			craters.add(new Crater(at, scale * 0.85F, now + life - 14));
		}

		private int remaining() {
			int n = 0;
			for (SkySun sun : sky) {
				if (!sun.used && sun.handle != null && sun.handle.alive()) {
					n++;
				}
			}
			return n;
		}

		private @Nullable SkySun nearest(Vec3 to) {
			SkySun best = null;
			double bestDistance = Double.MAX_VALUE;
			for (SkySun sun : sky) {
				if (sun.used || sun.handle == null || !sun.handle.alive()) {
					continue;
				}
				double d = sun.pos.distanceToSqr(to);
				if (d < bestDistance) {
					bestDistance = d;
					best = sun;
				}
			}
			return best;
		}

		@Override
		void emit(ClientLevel level, int age) {
			RandomSource random = level.getRandom();
			if (age % 2 == 0 && !EffectQuality.minimal()) {
				// heat licking off the hanging suns
				for (SkySun sun : sky) {
					if (sun.used || sun.handle == null || !sun.handle.alive()) {
						continue;
					}
					double s = sunScale * 0.5D;
					spend(Qi.flame(0xFFB030, 1.1F, 16), sun.pos.x + (random.nextDouble() - 0.5D) * s, sun.pos.y + (random.nextDouble() - 0.5D) * s,
							sun.pos.z + (random.nextDouble() - 0.5D) * s, 0.0D, 0.05D, 0.0D);
				}
			}
			if (age % 3 == 0) {
				// embers rising from the cooling craters
				craters.removeIf(crater -> age > crater.until);
				for (Crater crater : craters) {
					for (int i = 0, n = EffectQuality.scale(2); i < n; i++) {
						double a = random.nextDouble() * Math.PI * 2.0D;
						double r = crater.radius * Math.sqrt(random.nextDouble());
						spend(Qi.flame(i % 2 == 0 ? 0xFF8A20 : 0xFFC040, 0.5F, 18), crater.pos.x + Math.cos(a) * r, crater.pos.y + 0.2D,
								crater.pos.z + Math.sin(a) * r, 0.0D, 0.06D, 0.0D);
					}
				}
			}
		}

		@Override
		boolean finished(int age) {
			if (grandAt >= 0) {
				return age > grandAt + 100;
			}
			return age > Math.max(charge + 200, lastActive + 120);
		}
	}

	private static final class SkySun {
		final Vec3 pos;
		@Nullable ModelHandle handle;
		boolean used;

		SkySun(Vec3 pos) {
			this.pos = pos;
		}
	}

	private record Crater(Vec3 pos, float radius, int until) {
	}

	// ================================================================== helpers

	private static AnimStep step(Anim kind, int start, int duration, float a, float b) {
		return AnimStep.of(kind, start, duration, a, b);
	}

	/** One particle within the shared budget (DETAIL: it never starves the spec's own core layers). */
	private static boolean spend(ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
			return false;
		}
		int life = Qi.lifeOf(particle);
		int cost = FxBudget.cost(life > 0 ? life : 20);
		int granted = FxBudget.take(cost, FxBudget.Priority.DETAIL);
		if (granted < cost) {
			FxBudget.refund(granted);
			return false;
		}
		level.addParticle(particle, true, false, x, y, z, vx, vy, vz);
		return true;
	}

	private FireUltimates() {
	}
}
