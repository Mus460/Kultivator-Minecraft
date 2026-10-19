package dev.kultivator.client.fx.quad;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.epic.render.EpicRenderTypes;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Technique geometry (polish pass S5/S14/S16/S21): bolts, beams, crescents, ribbons (dragons, whips, flame and blood
 * ribbons), movement streaks, flat ground rings, glow discs and giant avatar figures drawn as camera-relative quads from
 * Fabric {@code LevelRenderEvents.COLLECT_SUBMITS}, like the 7b {@code EpicRenderer} (never in a shader pack's shadow
 * pass). Each kind has a per-quality cap (Minimal draws none of the large kinds: callers keep their particle stand-in when
 * {@link #add} refuses), and every frame has a quad budget. Effects live in client ticks, are seeded, and may follow
 * entities. Idle cost: one list check.
 */
public final class QuadFx {

	/** Effect kinds and their caps at Minimal / Normal / Maximal (polish section 4, budget table). */
	public enum Kind {
		BOLT(2, 8, 24), RIBBON(0, 4, 12), CRESCENT(2, 6, 16), BEAM(2, 6, 12), STREAK(2, 8, 16), RING(4, 12, 24), GLOW(8, 24, 64), FIGURE(0, 1, 2);

		final int[] caps;

		Kind(int minimal, int normal, int maximal) {
			this.caps = new int[]{minimal, normal, maximal};
		}

		int cap() {
			return caps[EffectQuality.level().ordinal()];
		}
	}

	static final RenderType ADD = RenderTypes.lightning();
	static final RenderType STRIP = RenderTypes.entityTranslucentEmissive(Kultivator.id("textures/misc/qi/beam_core.png"));
	static final RenderType DISC = RenderTypes.entityTranslucentEmissive(Kultivator.id("textures/misc/qi/glow_disc.png"));
	static final RenderType FLAT_RING = RenderTypes.entityTranslucentEmissive(Kultivator.id("textures/misc/qi/ring_flat.png"));
	static final RenderType SOFT = EpicRenderTypes.GLOW;

	private static final List<Quad> LIVE = new ArrayList<>();
	private static final int[] COUNT = new int[Kind.values().length];
	private static final Frame FRAME = new Frame();
	private static boolean registered;
	private static int warnings;

	/** Registers the render hook (once, from {@code SpecFx.init}). */
	public static void init() {
		if (registered) {
			return;
		}
		registered = true;
		LevelRenderEvents.COLLECT_SUBMITS.register(QuadFx::collect);
	}

	/** Adds an effect; false (and nothing drawn) when its kind is at its cap for the quality in effect. */
	public static boolean add(Quad quad) {
		int k = quad.kind.ordinal();
		if (COUNT[k] >= quad.kind.cap()) {
			return false;
		}
		COUNT[k]++;
		LIVE.add(quad);
		return true;
	}

	/** True when one more effect of {@code kind} would be accepted. */
	public static boolean room(Kind kind) {
		return COUNT[kind.ordinal()] < kind.cap();
	}

	/** Once per client tick (from {@code SpecFx.tick}). */
	public static void tick() {
		if (LIVE.isEmpty()) {
			return;
		}
		for (Iterator<Quad> it = LIVE.iterator(); it.hasNext(); ) {
			Quad quad = it.next();
			try {
				quad.tick();
			} catch (RuntimeException exception) {
				warn("tick", exception);
				quad.age = quad.life;
			}
			quad.age++;
			if (quad.age >= quad.life || quad.dead) {
				COUNT[quad.kind.ordinal()]--;
				it.remove();
			}
		}
	}

	/** Ends every effect of one caster (ABORT) with a short fade. */
	public static void endCaster(int casterId) {
		for (Quad quad : LIVE) {
			if (quad.casterId == casterId && casterId != Integer.MIN_VALUE) {
				quad.fadeOut(4);
			}
		}
	}

	/** Drops everything (world change, disconnect). */
	public static void clear() {
		LIVE.clear();
		java.util.Arrays.fill(COUNT, 0);
	}

	public static int live() {
		return LIVE.size();
	}

	private static void collect(LevelRenderContext context) {
		if (LIVE.isEmpty() || ShaderCompat.shadowPass()) {
			return;
		}
		try {
			CameraRenderState camera = context.levelState().cameraRenderState;
			FRAME.setup(camera, context.levelState().worldPartialTicks, context.poseStack(), context.submitNodeCollector());
			for (int i = 0; i < LIVE.size(); i++) {
				Quad quad = LIVE.get(i);
				float at = Math.min(quad.life, quad.age + FRAME.partial);
				if (at < 0.0F) {
					continue;
				}
				quad.render(FRAME, at);
			}
			FRAME.flush();
		} catch (RuntimeException exception) {
			warn("render", exception);
			FRAME.clear();
		}
	}

	static void warn(String where, Throwable exception) {
		if (warnings++ < 6) {
			Kultivator.LOGGER.warn("Kultivator: technique geometry error in {}", where, exception);
		}
	}

	// ================================================================== the base effect

	/** One live geometry effect. Ages in client ticks; {@link #render} gets the age with the partial tick. */
	public abstract static class Quad {
		final Kind kind;
		int life;
		int age;
		int casterId = Integer.MIN_VALUE;
		boolean dead;
		/** Ticks of the forced fade at the end ({@link #fadeOut}). */
		private int fadeFrom = -1;
		private int fadeTicks;

		protected Quad(Kind kind, int life) {
			this.kind = kind;
			this.life = Math.max(1, life);
		}

		/** Tags this effect with a caster (ABORT ends them). */
		public Quad caster(int id) {
			this.casterId = id;
			return this;
		}

		/** Delays the start by {@code ticks} (negative age). */
		public Quad delay(int ticks) {
			// the age starts negative and counts up to the unchanged life (render skips negative ages)
			this.age = -Math.max(0, ticks);
			return this;
		}

		/** Ends this effect with a fade of {@code ticks}. */
		public void stop(int ticks) {
			fadeOut(ticks);
		}

		public boolean alive() {
			return !dead && age < life;
		}

		void fadeOut(int ticks) {
			if (fadeFrom < 0) {
				fadeFrom = Math.max(0, age);
				fadeTicks = Math.max(1, ticks);
				life = Math.min(life, fadeFrom + fadeTicks);
			}
		}

		/** Extra alpha factor of a forced fade (1 otherwise). */
		protected float forced(float at) {
			return fadeFrom < 0 ? 1.0F : Math.max(0.0F, 1.0F - (at - fadeFrom) / fadeTicks);
		}

		protected void tick() {
		}

		protected abstract void render(Frame frame, float at);

		/** In over {@code in} ticks, out over the last {@code out} ticks. */
		protected float fade(float at, float in, float out) {
			float a = in <= 0.0F ? 1.0F : Math.min(1.0F, at / in);
			float b = out <= 0.0F ? 1.0F : Math.min(1.0F, Math.max(0.0F, (life - at) / out));
			return Math.max(0.0F, Math.min(a, b)) * forced(at);
		}

		/** The entity {@code id} in the client level, or null. */
		protected static @Nullable Entity entity(int id) {
			Minecraft minecraft = Minecraft.getInstance();
			return id == -1 || minecraft.level == null ? null : minecraft.level.getEntity(id);
		}
	}

	// ================================================================== frame and sinks

	/** The per-frame drawing state: camera-relative positions, billboard axes, one sink per render type. */
	public static final class Frame {
		public double camX;
		public double camY;
		public double camZ;
		public float partial;
		public PoseStack poseStack;
		public SubmitNodeCollector collector;
		public final Vector3f right = new Vector3f();
		public final Vector3f up = new Vector3f();
		public final Vector3f forward = new Vector3f();
		private int quads;
		private int quadCap;

		final Sink add = new Sink(ADD, false);
		final Sink strip = new Sink(STRIP, true);
		final Sink disc = new Sink(DISC, true);
		final Sink ring = new Sink(FLAT_RING, true);
		final Sink soft = new Sink(SOFT, true);
		final List<GiantAvatar> figures = new ArrayList<>();

		void setup(CameraRenderState camera, float partialTicks, PoseStack poseStack, SubmitNodeCollector collector) {
			Vec3 pos = camera.pos;
			camX = pos.x;
			camY = pos.y;
			camZ = pos.z;
			partial = partialTicks;
			this.poseStack = poseStack;
			this.collector = collector;
			camera.orientation.transform(1.0F, 0.0F, 0.0F, right);
			camera.orientation.transform(0.0F, 1.0F, 0.0F, up);
			camera.orientation.transform(0.0F, 0.0F, -1.0F, forward);
			quadCap = switch (EffectQuality.level()) {
				case MINIMAL -> 600;
				case NORMAL -> 3000;
				case MAXIMAL -> 8000;
			};
			clear();
		}

		void clear() {
			add.reset();
			strip.reset();
			disc.reset();
			ring.reset();
			soft.reset();
			figures.clear();
			quads = 0;
		}

		/** Takes {@code n} quads of this frame's budget. */
		public boolean take(int n) {
			if (quads + n > quadCap) {
				return false;
			}
			quads += n;
			return true;
		}

		public float rx(double x) {
			return (float) (x - camX);
		}

		public float ry(double y) {
			return (float) (y - camY);
		}

		public float rz(double z) {
			return (float) (z - camZ);
		}

		public double dist2(Vec3 at) {
			double dx = at.x - camX;
			double dy = at.y - camY;
			double dz = at.z - camZ;
			return dx * dx + dy * dy + dz * dz;
		}

		/** A camera-facing strip segment between two world points. */
		public void segment(Sink sink, Vec3 a, Vec3 b, float wa, float wb, int ca, int cb, float u0, float u1) {
			segment(sink, rx(a.x), ry(a.y), rz(a.z), rx(b.x), ry(b.y), rz(b.z), wa, wb, ca, cb, u0, u1);
		}

		/** A camera-facing strip segment between two camera-relative points (side = dir × (A − camera)). */
		public void segment(Sink sink, float ax, float ay, float az, float bx, float by, float bz, float wa, float wb, int ca, int cb, float u0,
				float u1) {
			float dx = bx - ax;
			float dy = by - ay;
			float dz = bz - az;
			float sx = dy * az - dz * ay;
			float sy = dz * ax - dx * az;
			float sz = dx * ay - dy * ax;
			float length = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
			if (length < 1.0E-5F) {
				sx = right.x;
				sy = right.y;
				sz = right.z;
			} else {
				sx /= length;
				sy /= length;
				sz /= length;
			}
			float ha = wa * 0.5F;
			float hb = wb * 0.5F;
			sink.vertex(ax - sx * ha, ay - sy * ha, az - sz * ha, u0, 0.0F, ca, forward.x, forward.y, forward.z);
			sink.vertex(bx - sx * hb, by - sy * hb, bz - sz * hb, u1, 0.0F, cb, forward.x, forward.y, forward.z);
			sink.vertex(bx + sx * hb, by + sy * hb, bz + sz * hb, u1, 1.0F, cb, forward.x, forward.y, forward.z);
			sink.vertex(ax + sx * ha, ay + sy * ha, az + sz * ha, u0, 1.0F, ca, forward.x, forward.y, forward.z);
		}

		/** A camera-facing square of half size {@code half} at a world point. */
		public void billboard(Sink sink, Vec3 at, float half, int color) {
			float x = rx(at.x);
			float y = ry(at.y);
			float z = rz(at.z);
			float rxh = right.x * half;
			float ryh = right.y * half;
			float rzh = right.z * half;
			float uxh = up.x * half;
			float uyh = up.y * half;
			float uzh = up.z * half;
			sink.vertex(x - rxh - uxh, y - ryh - uyh, z - rzh - uzh, 0.0F, 1.0F, color, -forward.x, -forward.y, -forward.z);
			sink.vertex(x + rxh - uxh, y + ryh - uyh, z + rzh - uzh, 1.0F, 1.0F, color, -forward.x, -forward.y, -forward.z);
			sink.vertex(x + rxh + uxh, y + ryh + uyh, z + rzh + uzh, 1.0F, 0.0F, color, -forward.x, -forward.y, -forward.z);
			sink.vertex(x - rxh + uxh, y - ryh + uyh, z - rzh + uzh, 0.0F, 0.0F, color, -forward.x, -forward.y, -forward.z);
		}

		/** A square in the plane of {@code u} × {@code v} (unit axes) at a world point, both faces. */
		public void plane(Sink sink, Vec3 at, Vec3 u, Vec3 v, float half, int color) {
			float x = rx(at.x);
			float y = ry(at.y);
			float z = rz(at.z);
			float ux = (float) u.x * half;
			float uy = (float) u.y * half;
			float uz = (float) u.z * half;
			float vx = (float) v.x * half;
			float vy = (float) v.y * half;
			float vz = (float) v.z * half;
			Vec3 n = u.cross(v);
			float nx = (float) n.x;
			float ny = (float) n.y;
			float nz = (float) n.z;
			sink.vertex(x - ux - vx, y - uy - vy, z - uz - vz, 0.0F, 0.0F, color, nx, ny, nz);
			sink.vertex(x - ux + vx, y - uy + vy, z - uz + vz, 0.0F, 1.0F, color, nx, ny, nz);
			sink.vertex(x + ux + vx, y + uy + vy, z + uz + vz, 1.0F, 1.0F, color, nx, ny, nz);
			sink.vertex(x + ux - vx, y + uy - vy, z + uz - vz, 1.0F, 0.0F, color, nx, ny, nz);
			// the back face (rings seen from below, vertical walls from behind)
			sink.vertex(x + ux - vx, y + uy - vy, z + uz - vz, 1.0F, 0.0F, color, -nx, -ny, -nz);
			sink.vertex(x + ux + vx, y + uy + vy, z + uz + vz, 1.0F, 1.0F, color, -nx, -ny, -nz);
			sink.vertex(x - ux + vx, y - uy + vy, z - uz + vz, 0.0F, 1.0F, color, -nx, -ny, -nz);
			sink.vertex(x - ux - vx, y - uy - vy, z - uz - vz, 0.0F, 0.0F, color, -nx, -ny, -nz);
		}

		void flush() {
			soft.submit(collector, poseStack);
			ring.submit(collector, poseStack);
			strip.submit(collector, poseStack);
			disc.submit(collector, poseStack);
			for (int i = 0; i < figures.size(); i++) {
				figures.get(i).submit(this, i);
			}
			add.submit(collector.order(1), poseStack);
		}
	}

	/** Vertices of one render type for this frame, submitted once through a reused writer. */
	public static final class Sink {
		private static final int STRIDE = 8;
		private final RenderType type;
		private final boolean entity;
		private float[] data = new float[STRIDE * 1024];
		private int[] colors = new int[1024];
		private int count;
		private PoseStack poseStack;
		private final SubmitNodeCollector.CustomGeometryRenderer writer = this::write;

		Sink(RenderType type, boolean entity) {
			this.type = type;
			this.entity = entity;
		}

		public void vertex(float x, float y, float z, float u, float v, int argb, float nx, float ny, float nz) {
			if (count >= colors.length) {
				int size = colors.length * 2;
				float[] more = new float[size * STRIDE];
				System.arraycopy(data, 0, more, 0, data.length);
				data = more;
				int[] moreColors = new int[size];
				System.arraycopy(colors, 0, moreColors, 0, colors.length);
				colors = moreColors;
			}
			int o = count * STRIDE;
			data[o] = x;
			data[o + 1] = y;
			data[o + 2] = z;
			data[o + 3] = u;
			data[o + 4] = v;
			data[o + 5] = nx;
			data[o + 6] = ny;
			data[o + 7] = nz;
			colors[count] = argb;
			count++;
		}

		void reset() {
			count = 0;
		}

		void submit(OrderedSubmitNodeCollector collector, PoseStack poseStack) {
			if (count < 4) {
				return;
			}
			this.poseStack = poseStack;
			collector.submitCustomGeometry(poseStack, type, writer);
		}

		private void write(PoseStack.Pose pose, VertexConsumer buffer) {
			int quads = count / 4 * 4;
			for (int i = 0; i < quads; i++) {
				int o = i * STRIDE;
				if (entity) {
					buffer.addVertex(pose, data[o], data[o + 1], data[o + 2])
							.setColor(colors[i])
							.setUv(data[o + 3], data[o + 4])
							.setOverlay(OverlayTexture.NO_OVERLAY)
							.setLight(LightCoordsUtil.FULL_BRIGHT)
							.setNormal(pose, data[o + 5], data[o + 6], data[o + 7]);
				} else {
					buffer.addVertex(pose, data[o], data[o + 1], data[o + 2]).setColor(colors[i]);
				}
			}
		}
	}

	// ================================================================== colour helpers

	static int argb(float alpha, int rgb) {
		return ARGB.color(Math.max(0, Math.min(255, Math.round(alpha * 255.0F))), rgb & 0xFFFFFF);
	}

	static int mix(int a, int b, float t) {
		int r = Math.round(ARGB.red(a) + (ARGB.red(b) - ARGB.red(a)) * t);
		int g = Math.round(ARGB.green(a) + (ARGB.green(b) - ARGB.green(a)) * t);
		int bl = Math.round(ARGB.blue(a) + (ARGB.blue(b) - ARGB.blue(a)) * t);
		return (Math.max(0, Math.min(255, r)) << 16) | (Math.max(0, Math.min(255, g)) << 8) | Math.max(0, Math.min(255, bl));
	}

	private QuadFx() {
	}
}
