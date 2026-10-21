package dev.kultivator.client.epic.kosmik;

import dev.kultivator.Kultivator;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.epic.camera.CinematicDirector;
import dev.kultivator.client.epic.kosmik.orbit.OrbitStage;
import dev.kultivator.client.epic.kosmik.surga.SurgaStage;
import dev.kultivator.client.epic.render.EpicRenderer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Film Kosmik 3D (DESIGN 32, contract C32.1): the core of the two 3D sets. The camera never travels to space: at a stage
 * shot's start {@link #enter} finds a pocket of clear air near the caster ({@link StagePocket}); the real camera stands
 * there still and only turns, while the virtual eye moves inside the stage (written every frame by {@code FilmPath} or,
 * for Kamera Penuh's calm beat, by {@code CinematicDirector}). Each frame {@link #render} reads
 * {@link CinematicDirector#stageBinding()}: the binding of the running shot, non-null only while a stage shot runs and is
 * not exiting. No binding, nothing is drawn and a stage that was bound gets {@link KosmikStage#end()}, so a stage never
 * outlives its shot (a danger abort, a death, a disconnect, a skip or the next cut drop it in the same frame).
 * <p>
 * The stage hides the world with an opaque, depth-writing dome close around the camera (32.1.2); everything drawn after
 * it (water, clouds, rain, particles) fails the depth test. Presentation only, for the caster's own client: nothing here
 * touches a block, the weather, the time or the tick rate. Idle cost: one boolean in {@link #wants()}.
 */
public final class Kosmik3D {

	/** The two stages. */
	public enum StageId {
		ORBIT, SURGA;

		public String id() {
			return this == ORBIT ? "orbit" : "surga";
		}

		public static @Nullable StageId byId(String id) {
			for (StageId value : values()) {
				if (value.id().equals(id)) {
					return value;
				}
			}
			return null;
		}
	}

	/** Kamera Penuh's calm stage beat in Film Pusaka F3 (32.5.6): the binding, the pocket C and the two look points. */
	public record PenuhPlan(Binding binding, Vec3 c, Vec3 look0, Vec3 look1) {
	}

	/**
	 * One stage shot: the stage, its beat and context, the pocket C (world) and R_d, and the virtual eye (stage metres)
	 * with the shot time, written every frame by the film path (or by {@link #follow} for a calm beat).
	 */
	public static final class Binding {
		public final StageId id;
		final KosmikStage stage;
		public final String beat;
		public final StageContext ctx;
		public final double cx;
		public final double cy;
		public final double cz;
		public final float radius;
		public final int ticks;
		public final boolean calm;
		final boolean continued;
		/** The virtual eye (stage metres). */
		public final double[] eye = new double[3];
		/** Shot time (ticks, fractional). */
		public double time;
		final StageAnchor anchor = new StageAnchor();
		final double[] tmp = new double[3];
		// a calm beat's eye path and look (anchor-local points)
		@Nullable String eyeAnchor;
		final double[] eye0 = new double[3];
		final double[] eye1 = new double[3];

		Binding(StageId id, KosmikStage stage, String beat, StageContext ctx, Vec3 c, float radius, int ticks, boolean calm, boolean continued) {
			this.id = id;
			this.stage = stage;
			this.beat = beat;
			this.ctx = ctx;
			this.cx = c.x;
			this.cy = c.y;
			this.cz = c.z;
			this.radius = radius;
			this.ticks = Math.max(1, ticks);
			this.calm = calm;
			this.continued = continued;
		}

		/** The pocket C (world). */
		public Vec3 c() {
			return new Vec3(cx, cy, cz);
		}

		/** The film path's per-frame write: the virtual eye and the shot time. */
		public void publish(double x, double y, double z, double shotTime) {
			if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) {
				eye[0] = x;
				eye[1] = y;
				eye[2] = z;
			}
			time = Math.max(0.0D, shotTime);
		}

		/** A calm beat's per-frame eye: its authored path at eased progress {@code e}, the stage at {@code shotTime}. */
		public void follow(float e, double shotTime) {
			time = Math.max(0.0D, shotTime);
			if (eyeAnchor == null) {
				return;
			}
			point(this, eyeAnchor, eye0[0], eye0[1], eye0[2], time, tmp);
			double ax = tmp[0];
			double ay = tmp[1];
			double az = tmp[2];
			point(this, eyeAnchor, eye1[0], eye1[1], eye1[2], time, tmp);
			double k = Math.max(0.0F, Math.min(1.0F, e));
			publish(ax + (tmp[0] - ax) * k, ay + (tmp[1] - ay) * k, az + (tmp[2] - az) * k, time);
		}

		/** True when this shot follows a shot of the same stage in the same pocket (no camera jump). */
		public boolean continued() {
			return continued;
		}

		public float shotT() {
			return (float) Math.max(0.0D, Math.min(1.0D, time / ticks));
		}
	}

	private static final int MAX_FAILURES = 3;
	private static final StageFrame FRAME = new StageFrame();
	private static final Set<String> WARNED = new HashSet<>();
	private static @Nullable KosmikStage orbit;
	private static @Nullable KosmikStage surga;
	private static final int[] FAILURES = new int[2];
	private static boolean created;
	private static boolean registered;
	/** The binding drawn last frame. */
	private static @Nullable Binding bound;
	/** The last binding handed out. */
	private static @Nullable Binding current;
	/** A film that uses the stages is armed: the renderer keeps running so the fog start stays fresh. */
	private static boolean armed;
	/** Frames drawn while armed with nothing bound (the arm lapses after a minute). */
	private static int idleFrames;
	private static String lastRefusal = "-";
	private static int lastQuads;
	private static float lastMs;
	private static int lastBudget;

	private Kosmik3D() {
	}

	// ================================================================ API (C32.1)

	/** Creates nothing; registers the disconnect release (from {@code EpicClient.init}). */
	public static void init() {
		if (registered) {
			return;
		}
		registered = true;
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> release());
	}

	/** One boolean while idle (from {@code EpicRenderer.collect}). */
	public static boolean wants() {
		return armed || bound != null || CinematicDirector.stageBinding() != null;
	}

	/** True while a stage is drawn this frame (the name-tag mixin, {@code EpicRenderer}). */
	public static boolean covering() {
		return CinematicDirector.stageBinding() != null;
	}

	/** At film arm / Penuh plan / preview: the stage builds its meshes (once) and starts any bake. */
	public static void prepare(StageId id, StageContext ctx) {
		KosmikStage stage = stage(id);
		if (stage == null) {
			return;
		}
		armed = true;
		idleFrames = 0;
		try {
			stage.prepare(ctx);
		} catch (RuntimeException | LinkageError exception) {
			fail(id, "prepare", exception);
		}
	}

	/**
	 * A stage shot of {@code ticks} starts: the setting, the stage tier (frame guard, Ringan), the stage's readiness, the
	 * beat and the pocket. Null = play the shot's fallback ({@link #lastRefusal()} says why). A shot of the same stage that
	 * follows the running stage shot keeps its pocket (no camera jump).
	 */
	public static @Nullable Binding enter(StageId id, String beat, StageContext ctx, int ticks, boolean penuh) {
		try {
			return open(id, beat, ctx, ticks, penuh);
		} catch (RuntimeException | LinkageError exception) {
			fail(id, "enter", exception);
			return refuse("error");
		}
	}

	private static @Nullable Binding open(StageId id, String beat, StageContext ctx, int ticks, boolean penuh) {
		if (setting() == EpicSettings.Kosmik3d.MATI) {
			return refuse("setting");
		}
		int tier = stageTier();
		if (tier < 0) {
			return refuse("budget");
		}
		KosmikStage stage = stage(id);
		if (stage == null || FAILURES[id.ordinal()] >= MAX_FAILURES) {
			return refuse("stage");
		}
		StageContext context = new StageContext(ctx.skill(), ctx.tier(), ctx.s(), ctx.fs(), ctx.rgb(), ctx.seed(), tier, setting() == EpicSettings.Kosmik3d.RINGAN,
				ShaderCompat.packOn(), penuh || ctx.calm(), ctx.colors());
		if (!stage.ready()) {
			prepare(id, context);
			if (!stage.ready()) {
				return refuse("not ready");
			}
		}
		if (beat == null || !stage.beats().contains(beat)) {
			warnOnce("unknown beat " + id.id() + "/" + beat);
			return refuse("beat");
		}
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			return refuse("player");
		}
		Binding running = CinematicDirector.stageBinding();
		boolean continued = running != null && running == current && running.id == id && running.calm == penuh;
		Vec3 c;
		float radius;
		if (continued) {
			c = running.c();
			radius = running.radius;
		} else {
			StagePocket.Found found = StagePocket.find(player, tier, penuh, EpicRenderer.lastFogStart());
			if (found == null) {
				return refuse("pocket: " + StagePocket.lastReason());
			}
			c = found.c();
			radius = found.radius();
		}
		Binding binding = new Binding(id, stage, beat, context, c, radius, ticks, penuh, continued);
		if (continued) {
			System.arraycopy(running.eye, 0, binding.eye, 0, 3);
		}
		try {
			stage.beat(beat, context, binding.ticks, continued);
		} catch (RuntimeException | LinkageError exception) {
			fail(id, "beat", exception);
			return refuse("error");
		}
		current = binding;
		armed = true;
		lastRefusal = "-";
		return binding;
	}

	/**
	 * The stage point of a film key: local ({@code x, y, z}) in the frame of anchor {@code anchor} at shot time {@code t}
	 * into {@code out}. An unknown anchor resolves to the stage origin (logged once); false then.
	 */
	public static boolean point(Binding b, String anchor, double x, double y, double z, double t, double[] out) {
		StageAnchor a = b.anchor;
		a.reset();
		boolean ok;
		try {
			ok = anchor != null && b.stage.anchor(anchor, t, a);
		} catch (RuntimeException | LinkageError exception) {
			fail(b.id, "anchor", exception);
			ok = false;
		}
		if (!ok) {
			a.reset();
			if (anchor != null && !anchor.isEmpty()) {
				warnOnce("unknown anchor " + b.id.id() + "/" + anchor);
			}
		}
		a.place(x, y, z, out);
		return ok;
	}

	/**
	 * The world point of a stage point as the frame draws it (C + the diorama offset from the eye), or null when a body of
	 * the stage hides it (the flare's light point, 32.4).
	 */
	public static @Nullable Vec3 lightPoint(Binding b, double px, double py, double pz) {
		boolean hidden;
		try {
			hidden = b.stage.occluded(px, py, pz, b.eye[0], b.eye[1], b.eye[2]);
		} catch (RuntimeException | LinkageError exception) {
			hidden = false;
		}
		if (hidden) {
			return null;
		}
		double dx = px - b.eye[0];
		double dy = py - b.eye[1];
		double dz = pz - b.eye[2];
		double rho = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (!(rho > 1.0E-6D)) {
			return b.c();
		}
		double far = StageFrame.FAR_SHARE * b.radius;
		double g = StageFrame.NEAR + (far - StageFrame.NEAR) * (Math.log(rho / StageFrame.RHO0) / Math.log(StageFrame.RHO1 / StageFrame.RHO0));
		g = Math.max(StageFrame.NEAR, Math.min(far, g));
		double k = g / rho;
		return new Vec3(b.cx + dx * k, b.cy + dy * k, b.cz + dz * k);
	}

	/**
	 * Kamera Penuh (32.5.6): the calm stage beat of Film Pusaka's F3 for decree {@code skill} (Pedang and Bintang in
	 * orbit, Tiang and Gunung in Heaven), or null (setting, budget, pocket, stage): F3 stays as today.
	 */
	public static @Nullable PenuhPlan penuh(int skill, StageContext ctx, int ticks) {
		int s = Math.max(0, Math.min(3, skill));
		StageId id = s == 0 || s == 2 ? StageId.ORBIT : StageId.SURGA;
		String beat = switch (s) {
			case 0 -> "pedang_tenang";
			case 1 -> "tiang_tenang";
			case 2 -> "bintang_tenang";
			default -> "gunung_tenang";
		};
		return calm(id, beat, ctx, ticks);
	}

	/**
	 * A calm beat of {@code id}: the pocket (≤ 24 b above the caster, R_d 8 b), the authored eye drift and the two look
	 * points (turning ≤ 16°/s; the eye drifts ≤ 0.4 × its distance to the look target per second). Null when refused.
	 */
	public static @Nullable PenuhPlan calm(StageId id, String beat, StageContext ctx, int ticks) {
		prepare(id, ctx);
		Binding b = enter(id, beat, ctx.asCalm(), ticks, true);
		if (b == null) {
			return null;
		}
		CalmPath path = CalmPath.of(id, beat);
		b.eyeAnchor = path.eyeAnchor;
		b.eye0[0] = path.e0[0];
		b.eye0[1] = path.e0[1];
		b.eye0[2] = path.e0[2];
		double[] p0 = new double[3];
		double[] p1 = new double[3];
		double[] l0 = new double[3];
		double[] l1 = new double[3];
		point(b, path.eyeAnchor, path.e0[0], path.e0[1], path.e0[2], 0.0D, p0);
		point(b, path.eyeAnchor, path.e1[0], path.e1[1], path.e1[2], 0.0D, p1);
		point(b, path.lookAnchor0, path.l0[0], path.l0[1], path.l0[2], 0.0D, l0);
		point(b, path.lookAnchor1, path.l1[0], path.l1[1], path.l1[2], ticks, l1);
		// the flow cap: ≤ 0.4 × distance per second
		double distance = Math.max(1.0D, dist(p0, l0));
		double move = dist(p0, p1);
		double allowed = 0.4D * distance * ticks / 20.0D;
		double k = move > allowed && move > 1.0E-9D ? allowed / move : 1.0D;
		for (int i = 0; i < 3; i++) {
			b.eye1[i] = path.e0[i] + (path.e1[i] - path.e0[i]) * k;
			p1[i] = p0[i] + (p1[i] - p0[i]) * k;
		}
		b.publish(p0[0], p0[1], p0[2], 0.0D);
		Vec3 d0 = new Vec3(l0[0] - p0[0], l0[1] - p0[1], l0[2] - p0[2]);
		Vec3 d1 = new Vec3(l1[0] - p1[0], l1[1] - p1[1], l1[2] - p1[2]);
		d0 = d0.lengthSqr() < 1.0E-9D ? new Vec3(0.0D, 0.0D, 1.0D) : d0.normalize();
		d1 = d1.lengthSqr() < 1.0E-9D ? d0 : d1.normalize();
		// the turn cap: ≤ 16°/s
		double angle = Math.acos(Math.max(-1.0D, Math.min(1.0D, d0.dot(d1))));
		double max = Math.toRadians(16.0D) * ticks / 20.0D;
		if (angle > max && angle > 1.0E-6D) {
			double t = max / angle;
			double sin = Math.sin(angle);
			Vec3 d = sin < 1.0E-4D ? d0.scale(1.0D - t).add(d1.scale(t))
					: d0.scale(Math.sin((1.0D - t) * angle) / sin).add(d1.scale(Math.sin(t * angle) / sin));
			d1 = d.lengthSqr() < 1.0E-9D ? d0 : d.normalize();
		}
		Vec3 c = b.c();
		return new PenuhPlan(b, c, c.add(d0.scale(16.0D)), c.add(d1.scale(16.0D)));
	}

	private static double dist(double[] a, double[] b) {
		double dx = a[0] - b[0];
		double dy = a[1] - b[1];
		double dz = a[2] - b[2];
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** The authored drift of the calm beats (anchor-local points). */
	private record CalmPath(String eyeAnchor, double[] e0, double[] e1, String lookAnchor0, double[] l0, String lookAnchor1, double[] l1) {
		static CalmPath of(StageId id, String beat) {
			return switch (beat) {
				case "pedang_tenang" -> new CalmPath("sword", v(60, 40, -110), v(54, 36, -100), "sword", v(0, 0, 0), "sword", v(0, 8, 0));
				case "bintang_tenang" -> new CalmPath("dipper", v(-20, -10, -160), v(-14, -7, -142), "dipper", v(0, 0, 0), "core7", v(0, 0, 0));
				case "tiang_tenang" -> new CalmPath("gerbang", v(0, -20, -140), v(0, -16, -120), "gerbang", v(0, 0, 0), "gerbang", v(0, 12, 0));
				case "gunung_tenang" -> new CalmPath("laut", v(40, 60, -80), v(40, 76, -80), "segel", v(0, 0, 0), "segel", v(0, 4, 0));
				case "preview_b" -> id == StageId.ORBIT
						? new CalmPath("sword", v(60, 30, -100), v(50, 32, -96), "sword", v(0, 0, 0), "sword", v(0, 4, 0))
						: new CalmPath("gerbang", v(20, 20, 30), v(18, 21, 50), "aula", v(0, 20, 0), "aula", v(0, 20, 0));
				case "preview_c" -> id == StageId.ORBIT
						? new CalmPath("dipper", v(-20, -10, -160), v(-14, -7, -142), "dipper", v(0, 0, 0), "dipper", v(0, 0, 0))
						: new CalmPath("gerbang", v(60, -20, -60), v(54, -24, -54), "cincin1", v(0, 0, 0), "cincin1", v(0, 0, 0));
				default -> id == StageId.ORBIT
						? new CalmPath("planet", v(-900, 700, 3000), v(-860, 710, 2950), "planet", v(0, 0, 0), "planet", v(0, 150, 0))
						: new CalmPath("gerbang", v(0, -40, -150), v(0, -34, -132), "gerbang", v(0, 0, 0), "gerbang", v(0, 12, 0));
			};
		}

		private static double[] v(double x, double y, double z) {
			return new double[]{x, y, z};
		}
	}

	/** Every frame from {@code EpicRenderer.collect}, before the 7b elements (which it replaces while it covers). */
	public static void render(EpicRenderer.Frame epic) {
		Binding b = CinematicDirector.stageBinding();
		if (b != bound) {
			Binding old = bound;
			bound = b;
			if (old != null && (b == null || b.stage != old.stage)) {
				end(old);
			}
			if (b != null && !b.continued) {
				// item models are resolved again per stage entry: no reload listener exists, so a resource reload (F3+T)
				// between films never leaves a stale baked model in the cached render states
				FRAME.releaseItems();
			}
		}
		if (b == null) {
			if (armed && ++idleFrames > 3600) {
				armed = false;
			}
			return;
		}
		idleFrames = 0;
		boolean broken = FAILURES[b.id.ordinal()] >= MAX_FAILURES;
		long start = System.nanoTime();
		Minecraft minecraft = Minecraft.getInstance();
		StageFrame frame = FRAME;
		frame.ctx = b.ctx;
		frame.beat = b.beat;
		frame.ticks = b.ticks;
		frame.quality = b.ctx.quality();
		frame.pack = b.ctx.pack();
		frame.time = b.time;
		frame.shotT = b.shotT();
		frame.fovDeg = (float) (minecraft.options.fov().get() * CinematicDirector.fovScale());
		int width = Math.max(1, minecraft.getWindow().getWidth());
		int height = Math.max(1, minecraft.getWindow().getHeight());
		frame.aspect = width / (float) height;
		frame.begin(epic, b.eye[0], b.eye[1], b.eye[2], b.radius);
		try {
			if (!broken) {
				b.stage.render(frame);
			}
			if (!frame.domeDrawn()) {
				frame.dome(KosmikTypes.BLACK, 0.0F);
			}
			frame.flush();
		} catch (RuntimeException | LinkageError exception) {
			fail(b.id, "render", exception);
			// the cover must stay: a black dome alone
			frame.discard();
			frame.begin(epic, b.eye[0], b.eye[1], b.eye[2], b.radius);
			frame.dome(KosmikTypes.BLACK, 0.0F);
			frame.flush();
		}
		lastQuads = frame.quadsWritten();
		lastBudget = EpicBudget.quadCap();
		lastMs = (System.nanoTime() - start) / 1_000_000.0F;
	}

	/** Ends every stage binding (from {@code FilmDirector.clear}: skip-all, level change, disconnect). */
	public static void clear() {
		Binding old = bound;
		bound = null;
		current = null;
		armed = false;
		if (old != null) {
			end(old);
		}
	}

	/** The film that armed the stages ended. */
	public static void disarm() {
		armed = false;
	}

	/** Disconnect (and resource reload): stages free their dynamic textures and caches. */
	public static void release() {
		clear();
		for (KosmikStage stage : new KosmikStage[]{orbit, surga}) {
			if (stage != null) {
				try {
					stage.release();
				} catch (RuntimeException | LinkageError exception) {
					warnOnce("release " + exception);
				}
			}
		}
		KosmikMesh.clearCache();
		FRAME.releaseItems();
	}

	/** {@code /sinematik kosmik3d info}. */
	public static List<String> info() {
		List<String> lines = new ArrayList<>();
		int tier = stageTier();
		lines.add("setting=" + setting().id() + " stage tier=" + (tier < 0 ? "-1 (fallback)" : String.valueOf(tier)) + " budget tier=" + EpicBudget.tier()
				+ " pack=" + ShaderCompat.packOn() + " fog=" + Math.round(EpicRenderer.lastFogStart()));
		lines.add(StagePocket.describe() + "; last refusal: " + lastRefusal);
		lines.add(String.format(Locale.ROOT, "last stage frame: %d quads (cap %d), %.2f ms; bound=%s", lastQuads, lastBudget, lastMs,
				bound == null ? "-" : bound.id.id() + "/" + bound.beat));
		for (StageId id : StageId.values()) {
			KosmikStage stage = stage(id);
			if (stage == null) {
				lines.add(id.id() + ": unavailable");
				continue;
			}
			try {
				lines.add(id.id() + ": ready=" + stage.ready() + " failures=" + FAILURES[id.ordinal()] + " beats=" + String.join(",", stage.beats()));
				for (String line : stage.info()) {
					lines.add("  " + line);
				}
			} catch (RuntimeException | LinkageError exception) {
				lines.add(id.id() + ": error " + exception);
			}
		}
		return lines;
	}

	/** Why the last {@link #enter} returned null. */
	public static String lastRefusal() {
		return lastRefusal;
	}

	/** The beats of a stage (empty when it is unavailable), for command suggestions. */
	public static List<String> beats(StageId id) {
		KosmikStage stage = stage(id);
		if (stage == null) {
			return List.of();
		}
		try {
			return stage.beats();
		} catch (RuntimeException | LinkageError exception) {
			return List.of();
		}
	}

	// ================================================================ context

	/**
	 * The context of a film or a preview: the decree, the tier and its scales, the tint, the seed, the stage tier (frame
	 * guard and Ringan), the pack profile and the home colours sampled now around the local player.
	 */
	public static StageContext context(int skill, int tier, int rgb, long seed, boolean calm) {
		int t = Math.max(1, Math.min(9, tier));
		Minecraft minecraft = Minecraft.getInstance();
		KosmikColors colors = minecraft.level != null && minecraft.player != null ? KosmikColors.sample(minecraft.level, minecraft.player.blockPosition())
				: KosmikColors.DEFAULT;
		return new StageContext(skill, t, StageContext.tierScale(t), StageContext.propScale(t), rgb & 0xFFFFFF, seed, Math.max(0, stageTier()),
				setting() == EpicSettings.Kosmik3d.RINGAN, ShaderCompat.packOn(), calm, colors);
	}

	/** The stage tier: {@code EpicBudget.tier()} lowered one step by "Ringan" (Minimal stays Minimal); −1 = fallback. */
	public static int stageTier() {
		int tier = EpicBudget.tier();
		if (tier < 0) {
			return -1;
		}
		return setting() == EpicSettings.Kosmik3d.RINGAN ? Math.max(0, tier - 1) : tier;
	}

	public static EpicSettings.Kosmik3d setting() {
		return EpicSettings.kosmik3d();
	}

	// ================================================================ internals

	private static @Nullable KosmikStage stage(StageId id) {
		if (!created) {
			created = true;
			try {
				orbit = OrbitStage.create();
			} catch (RuntimeException | LinkageError exception) {
				orbit = null;
				warnOnce("orbit stage unavailable: " + exception);
			}
			try {
				surga = SurgaStage.create();
			} catch (RuntimeException | LinkageError exception) {
				surga = null;
				warnOnce("heaven stage unavailable: " + exception);
			}
		}
		return id == StageId.ORBIT ? orbit : surga;
	}

	private static void end(Binding binding) {
		try {
			binding.stage.end();
		} catch (RuntimeException | LinkageError exception) {
			fail(binding.id, "end", exception);
		}
	}

	private static @Nullable Binding refuse(String reason) {
		lastRefusal = reason;
		return null;
	}

	private static void fail(StageId id, String where, Throwable exception) {
		int n = ++FAILURES[id.ordinal()];
		if (n <= MAX_FAILURES) {
			Kultivator.LOGGER.warn("Kultivator: Film kosmik 3D stage {} failed in {} ({}/{})", id.id(), where, n, MAX_FAILURES, exception);
		}
	}

	private static void warnOnce(String message) {
		if (WARNED.add(message)) {
			Kultivator.LOGGER.warn("Kultivator: Film kosmik 3D: {}", message);
		}
	}
}
