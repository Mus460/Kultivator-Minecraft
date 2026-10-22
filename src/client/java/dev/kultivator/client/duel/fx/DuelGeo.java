package dev.kultivator.client.duel.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kultivator.Kultivator;
import dev.kultivator.client.epic.render.EpicRenderTypes;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The per-frame drawing state of the duel's world geometry (DESIGN 35.4.4, 35.4.12): camera-relative quads in vanilla
 * render types only, collected into one sink per type and submitted once each through
 * {@code SubmitNodeCollector.submitCustomGeometry} from {@code LevelRenderEvents.COLLECT_SUBMITS} (the path of 7b and
 * {@code QuadFx}, safe under Sodium and Iris; never in the shadow pass, no post chain, no shader of our own). The heavy
 * "shader" look is faked by layering: a soft emissive sheet ({@code entityTranslucentEmissive}, texture alpha ≥ 0.1, fades
 * in the vertex alpha) under a thin additive core ({@code lightning()}, POSITION_COLOR, only for thin strips since it
 * writes depth). Dark or lit layers (char, ink, rope) use {@code entityTranslucent} with the world light.
 */
public final class DuelGeo {

	public static final Identifier TRAIL_TEX = Kultivator.id("textures/misc/duel/trail_gradient.png");
	public static final Identifier SCORCH_TEX = Kultivator.id("textures/misc/duel/scorch_slash.png");
	public static final Identifier INK_TEX = Kultivator.id("textures/misc/duel/scorch_ring.png");
	public static final Identifier SHOCK_TEX = Kultivator.id("textures/misc/duel/shock_ring.png");
	public static final Identifier ARROW_TEX = Kultivator.id("textures/misc/duel/ink_arrow.png");
	public static final Identifier ROPE_TEX = Kultivator.id("textures/misc/duel/rope.png");
	public static final Identifier GLOW_TEX = Kultivator.id("textures/misc/qi/glow_disc.png");

	public double camX;
	public double camY;
	public double camZ;
	public float partial;
	/** Presentation time of this frame (σ-scaled, {@link DuelTime#now(float)}). */
	public double time;
	public final Vector3f right = new Vector3f();
	public final Vector3f up = new Vector3f();
	public final Vector3f forward = new Vector3f();
	private PoseStack poseStack;
	private SubmitNodeCollector collector;
	private int quads;
	private int quadCap;

	/** Thin additive cores (sparks' streak lines, star flashes, edge lines). */
	public final Sink add = new Sink(RenderTypes.lightning(), false);
	/** Soft glow discs. */
	public final Sink glow = new Sink(RenderTypes.entityTranslucentEmissive(GLOW_TEX), true);
	/** Soft glow strips (qi waves, ink streaks, crescent bodies). */
	public final Sink strip = new Sink(EpicRenderTypes.GLOW, true);
	/** Blade-trail sheets. */
	public final Sink trail = new Sink(RenderTypes.entityTranslucentEmissive(TRAIL_TEX), true);
	/** Shock rings. */
	public final Sink shock = new Sink(RenderTypes.entityTranslucentEmissive(SHOCK_TEX), true);
	/** Scorched slash decals (lit char) and their hot glow. */
	public final Sink scorch = new Sink(RenderTypes.entityTranslucent(SCORCH_TEX), true);
	public final Sink scorchHot = new Sink(RenderTypes.entityTranslucentEmissive(SCORCH_TEX), true);
	/** The ring's ink circle (lit) and its red exit line (glowing). */
	public final Sink ink = new Sink(RenderTypes.entityTranslucent(INK_TEX), true);
	public final Sink inkGlow = new Sink(RenderTypes.entityTranslucentEmissive(INK_TEX), true);
	/** The GATHER arrows. */
	public final Sink arrow = new Sink(RenderTypes.entityTranslucentEmissive(ARROW_TEX), true);
	/** The ring's ropes (lit) and their red glow when someone stands outside. */
	public final Sink rope = new Sink(RenderTypes.entityTranslucent(ROPE_TEX), true);
	public final Sink ropeGlow = new Sink(RenderTypes.entityTranslucentEmissive(ROPE_TEX), true);

	void setup(CameraRenderState camera, float partialTicks, PoseStack poseStack, SubmitNodeCollector collector) {
		Vec3 pos = camera.pos;
		camX = pos.x;
		camY = pos.y;
		camZ = pos.z;
		partial = partialTicks;
		time = DuelTime.now(partialTicks);
		this.poseStack = poseStack;
		this.collector = collector;
		camera.orientation.transform(1.0F, 0.0F, 0.0F, right);
		camera.orientation.transform(0.0F, 1.0F, 0.0F, up);
		camera.orientation.transform(0.0F, 0.0F, -1.0F, forward);
		quadCap = DuelFxSettings.quadCap();
		clear();
	}

	void clear() {
		add.reset();
		glow.reset();
		strip.reset();
		trail.reset();
		shock.reset();
		scorch.reset();
		scorchHot.reset();
		ink.reset();
		inkGlow.reset();
		arrow.reset();
		rope.reset();
		ropeGlow.reset();
		quads = 0;
	}

	/** Submits every sink: lit ground layers first, soft layers, then the additive cores on top. */
	void flush() {
		ink.submit(collector, poseStack);
		scorch.submit(collector, poseStack);
		rope.submit(collector, poseStack);
		scorchHot.submit(collector, poseStack);
		inkGlow.submit(collector, poseStack);
		ropeGlow.submit(collector, poseStack);
		arrow.submit(collector, poseStack);
		shock.submit(collector, poseStack);
		trail.submit(collector, poseStack);
		strip.submit(collector, poseStack);
		glow.submit(collector, poseStack);
		add.submit(collector.order(1), poseStack);
	}

	/** Takes {@code n} quads of this frame's budget (35.4.12); false (nothing drawn) when it would overflow. */
	public boolean take(int n) {
		if (quads + n > quadCap) {
			return false;
		}
		quads += n;
		return true;
	}

	public double dist2(Vec3 at) {
		double dx = at.x - camX;
		double dy = at.y - camY;
		double dz = at.z - camZ;
		return dx * dx + dy * dy + dz * dz;
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

	// ================================================================ primitives

	/** A quad of four world points (a b c d in order) with per-corner colours, UVs (u0,v0) at a and (u1,v1) at c. */
	public void quad(Sink sink, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float u0, float v0, float u1, float v1, int ca, int cb, int cc, int cd,
			int light) {
		float nx = forward.x;
		float ny = forward.y;
		float nz = forward.z;
		sink.vertex(rx(a.x), ry(a.y), rz(a.z), u0, v0, ca, -nx, -ny, -nz, light);
		sink.vertex(rx(b.x), ry(b.y), rz(b.z), u0, v1, cb, -nx, -ny, -nz, light);
		sink.vertex(rx(c.x), ry(c.y), rz(c.z), u1, v1, cc, -nx, -ny, -nz, light);
		sink.vertex(rx(d.x), ry(d.y), rz(d.z), u1, v0, cd, -nx, -ny, -nz, light);
	}

	/** A camera-facing strip segment between two world points ({@code wa}, {@code wb} full widths), full bright. */
	public void segment(Sink sink, Vec3 a, Vec3 b, float wa, float wb, int ca, int cb, float u0, float u1) {
		segment(sink, a, b, wa, wb, ca, cb, u0, u1, LightCoordsUtil.FULL_BRIGHT);
	}

	/** A camera-facing strip segment between two world points with the given packed light (lit layers). */
	public void segment(Sink sink, Vec3 a, Vec3 b, float wa, float wb, int ca, int cb, float u0, float u1, int light) {
		float ax = rx(a.x);
		float ay = ry(a.y);
		float az = rz(a.z);
		float bx = rx(b.x);
		float by = ry(b.y);
		float bz = rz(b.z);
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
		sink.vertex(ax - sx * ha, ay - sy * ha, az - sz * ha, u0, 0.0F, ca, forward.x, forward.y, forward.z, light);
		sink.vertex(bx - sx * hb, by - sy * hb, bz - sz * hb, u1, 0.0F, cb, forward.x, forward.y, forward.z, light);
		sink.vertex(bx + sx * hb, by + sy * hb, bz + sz * hb, u1, 1.0F, cb, forward.x, forward.y, forward.z, light);
		sink.vertex(ax + sx * ha, ay + sy * ha, az + sz * ha, u0, 1.0F, ca, forward.x, forward.y, forward.z, light);
	}

	/** A camera-facing square of half size {@code half} turned by {@code roll} radians. */
	public void billboard(Sink sink, Vec3 at, float half, float roll, int color) {
		float x = rx(at.x);
		float y = ry(at.y);
		float z = rz(at.z);
		float c = (float) Math.cos(roll) * half;
		float s = (float) Math.sin(roll) * half;
		// the turned axes: r' = r cos + u sin, u' = u cos - r sin
		float rxh = right.x * c + up.x * s;
		float ryh = right.y * c + up.y * s;
		float rzh = right.z * c + up.z * s;
		float uxh = up.x * c - right.x * s;
		float uyh = up.y * c - right.y * s;
		float uzh = up.z * c - right.z * s;
		int light = LightCoordsUtil.FULL_BRIGHT;
		sink.vertex(x - rxh - uxh, y - ryh - uyh, z - rzh - uzh, 0.0F, 1.0F, color, -forward.x, -forward.y, -forward.z, light);
		sink.vertex(x + rxh - uxh, y + ryh - uyh, z + rzh - uzh, 1.0F, 1.0F, color, -forward.x, -forward.y, -forward.z, light);
		sink.vertex(x + rxh + uxh, y + ryh + uyh, z + rzh + uzh, 1.0F, 0.0F, color, -forward.x, -forward.y, -forward.z, light);
		sink.vertex(x - rxh + uxh, y - ryh + uyh, z - rzh + uzh, 0.0F, 0.0F, color, -forward.x, -forward.y, -forward.z, light);
	}

	/**
	 * A rectangle in the plane of the unit axes {@code u} × {@code v} at a world point (half sizes {@code hu}, {@code hv}),
	 * UVs 0..1, both faces when {@code twoSided}.
	 */
	public void plane(Sink sink, Vec3 at, Vec3 u, Vec3 v, float hu, float hv, int color, int light, boolean twoSided) {
		float x = rx(at.x);
		float y = ry(at.y);
		float z = rz(at.z);
		float ux = (float) u.x * hu;
		float uy = (float) u.y * hu;
		float uz = (float) u.z * hu;
		float vx = (float) v.x * hv;
		float vy = (float) v.y * hv;
		float vz = (float) v.z * hv;
		float nx = (float) (u.y * v.z - u.z * v.y);
		float ny = (float) (u.z * v.x - u.x * v.z);
		float nz = (float) (u.x * v.y - u.y * v.x);
		sink.vertex(x - ux - vx, y - uy - vy, z - uz - vz, 0.0F, 0.0F, color, nx, ny, nz, light);
		sink.vertex(x - ux + vx, y - uy + vy, z - uz + vz, 0.0F, 1.0F, color, nx, ny, nz, light);
		sink.vertex(x + ux + vx, y + uy + vy, z + uz + vz, 1.0F, 1.0F, color, nx, ny, nz, light);
		sink.vertex(x + ux - vx, y + uy - vy, z + uz - vz, 1.0F, 0.0F, color, nx, ny, nz, light);
		if (twoSided) {
			sink.vertex(x + ux - vx, y + uy - vy, z + uz - vz, 1.0F, 0.0F, color, -nx, -ny, -nz, light);
			sink.vertex(x + ux + vx, y + uy + vy, z + uz + vz, 1.0F, 1.0F, color, -nx, -ny, -nz, light);
			sink.vertex(x - ux + vx, y - uy + vy, z - uz + vz, 0.0F, 1.0F, color, -nx, -ny, -nz, light);
			sink.vertex(x - ux - vx, y - uy - vy, z - uz - vz, 0.0F, 0.0F, color, -nx, -ny, -nz, light);
		}
	}

	/**
	 * A four-branch star of additive light facing the camera (the metal flash): each branch a thin wedge from a bright
	 * centre to a transparent tip, turned by {@code roll}; the diagonal branches are half as long.
	 */
	public void star(Vec3 at, float size, float roll, int centre, float alpha) {
		float x = rx(at.x);
		float y = ry(at.y);
		float z = rz(at.z);
		int core = argb(alpha, centre);
		int tip = argb(0.0F, centre);
		for (int i = 0; i < 8; i++) {
			double angle = roll + i * Math.PI / 4.0D;
			float length = (i & 1) == 0 ? size : size * 0.45F;
			float width = (i & 1) == 0 ? size * 0.09F : size * 0.06F;
			float ca = (float) Math.cos(angle);
			float sa = (float) Math.sin(angle);
			// branch direction d and its side s in the camera plane
			float dx = right.x * ca + up.x * sa;
			float dy = right.y * ca + up.y * sa;
			float dz = right.z * ca + up.z * sa;
			float sx = -right.x * sa + up.x * ca;
			float sy = -right.y * sa + up.y * ca;
			float sz = -right.z * sa + up.z * ca;
			int light = LightCoordsUtil.FULL_BRIGHT;
			add.vertex(x - sx * width, y - sy * width, z - sz * width, 0.0F, 0.0F, core, 0.0F, 1.0F, 0.0F, light);
			add.vertex(x + dx * length, y + dy * length, z + dz * length, 0.0F, 0.0F, tip, 0.0F, 1.0F, 0.0F, light);
			add.vertex(x + dx * length, y + dy * length, z + dz * length, 0.0F, 0.0F, tip, 0.0F, 1.0F, 0.0F, light);
			add.vertex(x + sx * width, y + sy * width, z + sz * width, 0.0F, 0.0F, core, 0.0F, 1.0F, 0.0F, light);
		}
	}

	// ================================================================ colours

	public static int argb(float alpha, int rgb) {
		return ARGB.color(Math.max(0, Math.min(255, Math.round(alpha * 255.0F))), rgb & 0xFFFFFF);
	}

	public static int mix(int a, int b, float t) {
		float k = Math.max(0.0F, Math.min(1.0F, t));
		int r = Math.round(ARGB.red(a) + (ARGB.red(b) - ARGB.red(a)) * k);
		int g = Math.round(ARGB.green(a) + (ARGB.green(b) - ARGB.green(a)) * k);
		int bl = Math.round(ARGB.blue(a) + (ARGB.blue(b) - ARGB.blue(a)) * k);
		return (Math.max(0, Math.min(255, r)) << 16) | (Math.max(0, Math.min(255, g)) << 8) | Math.max(0, Math.min(255, bl));
	}

	// ================================================================ sinks

	/** Vertices of one render type for this frame, submitted once through a reused writer. */
	public static final class Sink {
		private static final int STRIDE = 8;
		private final RenderType type;
		private final boolean entity;
		private float[] data = new float[STRIDE * 512];
		private int[] colors = new int[512];
		private int[] lights = new int[512];
		private int count;
		private final SubmitNodeCollector.CustomGeometryRenderer writer = this::write;

		Sink(RenderType type, boolean entity) {
			this.type = type;
			this.entity = entity;
		}

		public void vertex(float x, float y, float z, float u, float v, int argb, float nx, float ny, float nz, int light) {
			if (count >= colors.length) {
				int size = colors.length * 2;
				float[] more = new float[size * STRIDE];
				System.arraycopy(data, 0, more, 0, data.length);
				data = more;
				int[] moreColors = new int[size];
				System.arraycopy(colors, 0, moreColors, 0, colors.length);
				colors = moreColors;
				int[] moreLights = new int[size];
				System.arraycopy(lights, 0, moreLights, 0, lights.length);
				lights = moreLights;
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
			lights[count] = light;
			count++;
		}

		void reset() {
			count = 0;
		}

		void submit(OrderedSubmitNodeCollector collector, PoseStack poseStack) {
			if (count < 4) {
				return;
			}
			collector.submitCustomGeometry(poseStack, type, writer);
		}

		private void write(PoseStack.Pose pose, VertexConsumer buffer) {
			int vertices = count / 4 * 4;
			for (int i = 0; i < vertices; i++) {
				int o = i * STRIDE;
				if (entity) {
					buffer.addVertex(pose, data[o], data[o + 1], data[o + 2])
							.setColor(colors[i])
							.setUv(data[o + 3], data[o + 4])
							.setOverlay(OverlayTexture.NO_OVERLAY)
							.setLight(lights[i])
							.setNormal(pose, data[o + 5], data[o + 6], data[o + 7]);
				} else {
					buffer.addVertex(pose, data[o], data[o + 1], data[o + 2]).setColor(colors[i]);
				}
			}
		}
	}
}
