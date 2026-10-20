package dev.kultivator.client.aura;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kultivator.Kultivator;
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
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The per-frame quad writer of the Aura Kultivasi (DESIGN 31.8 C31.3): camera-facing soft glows, flat ground decals and
 * discs, tilted rings (an annulus whose texture runs across the band), beams, upright textured cards and ribbons, all
 * camera-relative and drawn from Fabric {@code LevelRenderEvents.COLLECT_SUBMITS} like {@code QuadFx} (never in a shader
 * pack's shadow pass). Render types are made lazily per texture: {@code RenderTypes.entityTranslucentEmissive(Identifier)}
 * (full bright, translucent, no culling, no depth write; textures keep alpha ≥ 0.1 where visible because the cutout runs
 * before the vertex colour) and, without a shader pack only, {@code RenderTypes.lightning()} for thin hot lines.
 * <p>
 * Every call is × the subject's fade and returns false once the quad budget is spent (per aura and per frame, 31.2.5).
 * Under a shader pack {@link #large()} grants one large layer per aura.
 */
public final class AuraDraw {

	/** A soft round glow (gaussian), white: the vertex colour tints it. */
	public static final Identifier GLOW = tex("glow_soft");
	/** A soft band profile across v (rings, ripples). */
	public static final Identifier BAND = tex("band_soft");
	/** The beam strip: a hot centre line along u in a soft halo. */
	public static final Identifier STRIP = Kultivator.id("textures/misc/qi/beam_core.png");

	private static final float DECAL_LIFT = 0.04F;

	/** {@code kultivator:textures/misc/aura/<name>.png}. */
	public static Identifier tex(String name) {
		return Kultivator.id("textures/misc/aura/" + name + ".png");
	}

	private final Map<Identifier, Sink> sinks = new LinkedHashMap<>();
	private @Nullable Sink additive;
	private final List<Sink> order = new ArrayList<>();

	// ---- frame
	private double camX;
	private double camY;
	private double camZ;
	private final Vector3f right = new Vector3f();
	private final Vector3f up = new Vector3f();
	private final Vector3f forward = new Vector3f();
	private PoseStack poseStack;
	private SubmitNodeCollector collector;
	private int frameQuads;
	private int frameCap;
	private boolean pack;
	private boolean thin;

	// ---- the aura being drawn
	private @Nullable AuraSubject subject;
	private int auraQuads;
	private int auraCap;
	private int ribbons;
	private int ribbonCap;
	private boolean largeTaken;

	AuraDraw() {
	}

	// ================================================================== lifecycle (engine)

	void setup(CameraRenderState camera, PoseStack poseStack, SubmitNodeCollector collector, int frameCap, boolean pack) {
		Vec3 pos = camera.pos;
		camX = pos.x;
		camY = pos.y;
		camZ = pos.z;
		camera.orientation.transform(1.0F, 0.0F, 0.0F, right);
		camera.orientation.transform(0.0F, 1.0F, 0.0F, up);
		camera.orientation.transform(0.0F, 0.0F, -1.0F, forward);
		this.poseStack = poseStack;
		this.collector = collector;
		this.frameCap = Math.max(0, frameCap);
		this.frameQuads = 0;
		this.pack = pack;
		this.thin = !pack;
		for (Sink sink : order) {
			sink.reset();
		}
		if (additive != null) {
			additive.reset();
		}
	}

	void begin(AuraSubject s, int quadCap, int ribbonCap) {
		this.subject = s;
		this.auraQuads = 0;
		this.auraCap = Math.max(0, quadCap);
		this.ribbons = 0;
		this.ribbonCap = Math.max(0, ribbonCap);
		this.largeTaken = false;
	}

	void end() {
		this.subject = null;
	}

	void flush() {
		for (Sink sink : order) {
			sink.submit(collector, poseStack);
		}
		if (additive != null) {
			additive.submit(collector.order(1), poseStack);
		}
	}

	void clear() {
		for (Sink sink : order) {
			sink.reset();
		}
		if (additive != null) {
			additive.reset();
		}
		subject = null;
	}

	int frameQuads() {
		return frameQuads;
	}

	// ================================================================== budget

	/** Takes {@code n} quads of this aura's and this frame's budget. */
	public boolean take(int n) {
		if (auraQuads + n > auraCap || frameQuads + n > frameCap) {
			return false;
		}
		auraQuads += n;
		frameQuads += n;
		return true;
	}

	/** Quads this aura may still draw. */
	public int left() {
		return Math.max(0, Math.min(auraCap - auraQuads, frameCap - frameQuads));
	}

	/**
	 * Takes the aura's one large-layer slot (the shader-pack profile, 31.2.8): always true without a pack; with a pack
	 * true only the first time per aura, so only the biggest glow or card is drawn.
	 */
	public boolean large() {
		if (!pack) {
			return true;
		}
		if (largeTaken) {
			return false;
		}
		largeTaken = true;
		return true;
	}

	/** True while a shader pack is in use (the pack profile). */
	public boolean pack() {
		return pack;
	}

	/** The fade of the aura being drawn. */
	private float fade() {
		return subject == null ? 1.0F : subject.fade();
	}

	// ================================================================== shapes (C31.3)

	/** A camera-facing soft disc of {@code radius} at {@code c}. */
	public boolean glow(Vec3 c, float radius, int rgb, float alpha) {
		return billboard(c, radius, 0.0F, GLOW, rgb, alpha);
	}

	/**
	 * A soft disc pushed {@code push} blocks toward the camera, so a glow inside the body (a core in the dantian) shines
	 * through it instead of being hidden by the model.
	 */
	public boolean glowFront(Vec3 c, float radius, int rgb, float alpha, float push) {
		return glow(front(c, push), radius, rgb, alpha);
	}

	/** {@code c} moved {@code push} blocks toward the camera (at most half way). */
	public Vec3 front(Vec3 c, float push) {
		double dx = camX - c.x;
		double dy = camY - c.y;
		double dz = camZ - c.z;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1.0E-3D) {
			return c;
		}
		double k = Math.min(push, len * 0.5D) / len;
		return c.add(dx * k, dy * k, dz * k);
	}

	/** A camera-facing textured square of half size {@code half}, turned {@code turnDeg} in the screen plane. */
	public boolean billboard(Vec3 c, float half, float turnDeg, Identifier tex, int rgb, float alpha) {
		float a = alpha * fade();
		if (a < 0.004F || half <= 0.0F) {
			return true;
		}
		if (!take(1)) {
			return false;
		}
		double t = Math.toRadians(turnDeg);
		float cos = (float) Math.cos(t);
		float sin = (float) Math.sin(t);
		float rxh = (right.x * cos + up.x * sin) * half;
		float ryh = (right.y * cos + up.y * sin) * half;
		float rzh = (right.z * cos + up.z * sin) * half;
		float uxh = (up.x * cos - right.x * sin) * half;
		float uyh = (up.y * cos - right.y * sin) * half;
		float uzh = (up.z * cos - right.z * sin) * half;
		float x = rx(c.x);
		float y = ry(c.y);
		float z = rz(c.z);
		int color = argb(a, rgb);
		Sink sink = sink(tex);
		sink.vertex(x - rxh - uxh, y - ryh - uyh, z - rzh - uzh, 0.0F, 1.0F, color, -forward.x, -forward.y, -forward.z);
		sink.vertex(x + rxh - uxh, y + ryh - uyh, z + rzh - uzh, 1.0F, 1.0F, color, -forward.x, -forward.y, -forward.z);
		sink.vertex(x + rxh + uxh, y + ryh + uyh, z + rzh + uzh, 1.0F, 0.0F, color, -forward.x, -forward.y, -forward.z);
		sink.vertex(x - rxh + uxh, y - ryh + uyh, z - rzh + uzh, 0.0F, 0.0F, color, -forward.x, -forward.y, -forward.z);
		return true;
	}

	/** A flat horizontal textured square (a ground decal, a platform, a vortex seen from the side) turned {@code turnDeg}. */
	public boolean flat(Vec3 c, float radius, float turnDeg, Identifier tex, int rgb, float alpha) {
		float a = alpha * fade();
		if (a < 0.004F || radius <= 0.0F) {
			return true;
		}
		if (!take(1)) {
			return false;
		}
		double t = Math.toRadians(turnDeg);
		float ux = (float) Math.cos(t) * radius;
		float uz = (float) Math.sin(t) * radius;
		// v = up × u (turned 90 degrees in the plane)
		float vx = -uz;
		float vz = ux;
		float x = rx(c.x);
		float y = ry(c.y);
		float z = rz(c.z);
		int color = argb(a, rgb);
		Sink sink = sink(tex);
		sink.vertex(x - ux - vx, y, z - uz - vz, 0.0F, 0.0F, color, 0.0F, 1.0F, 0.0F);
		sink.vertex(x - ux + vx, y, z - uz + vz, 0.0F, 1.0F, color, 0.0F, 1.0F, 0.0F);
		sink.vertex(x + ux + vx, y, z + uz + vz, 1.0F, 1.0F, color, 0.0F, 1.0F, 0.0F);
		sink.vertex(x + ux - vx, y, z + uz - vz, 1.0F, 0.0F, color, 0.0F, 1.0F, 0.0F);
		return true;
	}

	/** A ground decal under the subject (0.04 b above its feet; only while it stands on the ground, 31.2.3). */
	public boolean decal(float radius, float turnDeg, Identifier tex, int rgb, float alpha) {
		AuraSubject s = subject;
		if (s == null || !s.onGround()) {
			return true;
		}
		return flat(s.feet().add(0.0D, DECAL_LIFT, 0.0D), radius, turnDeg, tex, rgb, alpha);
	}

	/**
	 * A ring around {@code axis} (its normal) from radius {@code r0} to {@code r1}, turned {@code turnDeg} about the axis.
	 * The texture's v runs across the band (0 inner, 1 outer) and its u spans each segment once.
	 */
	public boolean ring(Vec3 c, Vec3 axis, float r0, float r1, float turnDeg, Identifier tex, int rgb, float alpha) {
		float a = alpha * fade();
		if (a < 0.004F || r1 <= 0.0F || r1 <= r0) {
			return true;
		}
		Vec3 n = axis.lengthSqr() < 1.0E-8D ? new Vec3(0.0D, 1.0D, 0.0D) : axis.normalize();
		int segments = r1 < 0.4F ? 12 : r1 < 1.0F ? 16 : r1 < 2.2F ? 24 : r1 < 4.0F ? 32 : 40;
		if (!take(segments)) {
			// a smaller ring when the budget is short
			segments = Math.min(segments, left());
			if (segments < 8 || !take(segments)) {
				return false;
			}
		}
		Vec3 helper = Math.abs(n.y) < 0.95D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
		Vec3 u0 = n.cross(helper).normalize();
		double t = Math.toRadians(turnDeg);
		Vec3 nu = n.cross(u0);
		Vec3 u = u0.scale(Math.cos(t)).add(nu.scale(Math.sin(t)));
		Vec3 v = n.cross(u);
		float cx = rx(c.x);
		float cy = ry(c.y);
		float cz = rz(c.z);
		int color = argb(a, rgb);
		float nx = (float) n.x;
		float ny = (float) n.y;
		float nz = (float) n.z;
		Sink sink = sink(tex);
		double step = Math.PI * 2.0D / segments;
		double cos0 = 1.0D;
		double sin0 = 0.0D;
		for (int i = 0; i < segments; i++) {
			double angle = (i + 1) * step;
			double cos1 = Math.cos(angle);
			double sin1 = Math.sin(angle);
			double ax = u.x * cos0 + v.x * sin0;
			double ay = u.y * cos0 + v.y * sin0;
			double az = u.z * cos0 + v.z * sin0;
			double bx = u.x * cos1 + v.x * sin1;
			double by = u.y * cos1 + v.y * sin1;
			double bz = u.z * cos1 + v.z * sin1;
			sink.vertex(cx + (float) (ax * r0), cy + (float) (ay * r0), cz + (float) (az * r0), 0.0F, 0.0F, color, nx, ny, nz);
			sink.vertex(cx + (float) (ax * r1), cy + (float) (ay * r1), cz + (float) (az * r1), 0.0F, 1.0F, color, nx, ny, nz);
			sink.vertex(cx + (float) (bx * r1), cy + (float) (by * r1), cz + (float) (bz * r1), 1.0F, 1.0F, color, nx, ny, nz);
			sink.vertex(cx + (float) (bx * r0), cy + (float) (by * r0), cz + (float) (bz * r0), 1.0F, 0.0F, color, nx, ny, nz);
			cos0 = cos1;
			sin0 = sin1;
		}
		return true;
	}

	/** A beam of light: a soft halo, a bright strip and (without a shader pack) a thin white-hot line. */
	public boolean beam(Vec3 from, Vec3 to, float width, int rgb, float alpha) {
		float a = alpha * fade();
		if (a < 0.004F || width <= 0.0F || from.distanceToSqr(to) < 1.0E-4D) {
			return true;
		}
		boolean hot = thin;
		if (!take(hot ? 3 : 2)) {
			return false;
		}
		Sink strip = sink(STRIP);
		segment(strip, from, to, width * 3.0F, width * 3.0F, argb(0.35F * a, rgb), argb(0.35F * a, rgb), 0.0F, 1.0F);
		int core = AuraColors.mix(rgb, 0xFFFFFF, 0.45F);
		segment(strip, from, to, width, width, argb(0.85F * a, core), argb(0.85F * a, core), 0.0F, 1.0F);
		if (hot) {
			segment(additive(), from, to, width * 0.22F, width * 0.22F, argb(0.5F * a, 0xFFFFFF), argb(0.5F * a, 0xFFFFFF), 0.0F, 1.0F);
		}
		return true;
	}

	/**
	 * An upright textured card standing on {@code base}, {@code w} wide and {@code h} tall, facing {@code yawDeg}
	 * (Minecraft yaw); {@code crossed} adds a second card at right angles, so it reads from every side.
	 */
	public boolean card(Vec3 base, float w, float h, float yawDeg, boolean crossed, Identifier tex, int rgb, float alpha) {
		float a = alpha * fade();
		if (a < 0.004F || w <= 0.0F || h <= 0.0F) {
			return true;
		}
		if (!take(crossed ? 2 : 1)) {
			return false;
		}
		int color = argb(a, rgb);
		upright(sink(tex), base, w, h, yawDeg, color);
		if (crossed) {
			upright(sink(tex), base, w, h, yawDeg + 90.0F, color);
		}
		return true;
	}

	/** An upright card that turns about the vertical to face the camera (a soul figure, a silhouette). */
	public boolean standee(Vec3 base, float w, float h, Identifier tex, int rgb, float alpha) {
		double sx = right.x;
		double sz = right.z;
		double len = Math.sqrt(sx * sx + sz * sz);
		float yaw = len < 1.0E-4D ? 0.0F : (float) Math.toDegrees(Math.atan2(sz, sx));
		// the card's side axis is (cos yaw, 0, sin yaw): match it to the camera's right
		return card(base, w, h, yaw, false, tex, rgb, alpha);
	}

	/**
	 * A ribbon through {@code points} (head first): a soft halo and a bright core thinning from {@code width} to
	 * {@code tailWidth}, coloured from {@code head} to {@code tail}, fading toward the tail. Counted against the ribbons
	 * per aura (0 / 2 / 4).
	 */
	public boolean ribbon(List<Vec3> points, float width, float tailWidth, int head, int tail, float alpha) {
		float a = alpha * fade();
		int n = points.size();
		if (a < 0.004F || n < 2) {
			return true;
		}
		if (ribbons >= ribbonCap) {
			return false;
		}
		if (!take((n - 1) * 2)) {
			return false;
		}
		ribbons++;
		Sink strip = sink(STRIP);
		Sink soft = sink(BAND);
		Vec3 prev = points.get(0);
		for (int i = 1; i < n; i++) {
			Vec3 p = points.get(i);
			float t0 = (i - 1) / (float) (n - 1);
			float t1 = i / (float) (n - 1);
			float w0 = width + (tailWidth - width) * t0;
			float w1 = width + (tailWidth - width) * t1;
			int c0 = AuraColors.mix(head, tail, t0);
			int c1 = AuraColors.mix(head, tail, t1);
			float a0 = a * (1.0F - 0.8F * t0) * Math.min(1.0F, t0 * 6.0F + 0.35F);
			float a1 = a * (1.0F - 0.8F * t1) * Math.min(1.0F, t1 * 6.0F + 0.35F);
			segment(soft, prev, p, w0 * 2.6F, w1 * 2.6F, argb(0.35F * a0, c0), argb(0.35F * a1, c1), t0, t1);
			segment(strip, prev, p, w0, w1, argb(0.85F * a0, c0), argb(0.85F * a1, c1), 0.0F, 1.0F);
			prev = p;
		}
		billboard(points.get(0), width * 1.3F, 0.0F, GLOW, AuraColors.mix(head, 0xFFFFFF, 0.4F), 0.7F * alpha);
		return true;
	}

	// ================================================================== geometry helpers

	private void upright(Sink sink, Vec3 base, float w, float h, float yawDeg, int color) {
		double t = Math.toRadians(yawDeg);
		float sx = (float) Math.cos(t) * w * 0.5F;
		float sz = (float) Math.sin(t) * w * 0.5F;
		float nx = (float) -Math.sin(t);
		float nz = (float) Math.cos(t);
		float x = rx(base.x);
		float y = ry(base.y);
		float z = rz(base.z);
		sink.vertex(x - sx, y, z - sz, 0.0F, 1.0F, color, nx, 0.0F, nz);
		sink.vertex(x + sx, y, z + sz, 1.0F, 1.0F, color, nx, 0.0F, nz);
		sink.vertex(x + sx, y + h, z + sz, 1.0F, 0.0F, color, nx, 0.0F, nz);
		sink.vertex(x - sx, y + h, z - sz, 0.0F, 0.0F, color, nx, 0.0F, nz);
	}

	/** A camera-facing strip segment between two world points (side = direction × (A − camera)). */
	private void segment(Sink sink, Vec3 a, Vec3 b, float wa, float wb, int ca, int cb, float u0, float u1) {
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
		sink.vertex(ax - sx * ha, ay - sy * ha, az - sz * ha, u0, 0.0F, ca, -forward.x, -forward.y, -forward.z);
		sink.vertex(bx - sx * hb, by - sy * hb, bz - sz * hb, u1, 0.0F, cb, -forward.x, -forward.y, -forward.z);
		sink.vertex(bx + sx * hb, by + sy * hb, bz + sz * hb, u1, 1.0F, cb, -forward.x, -forward.y, -forward.z);
		sink.vertex(ax + sx * ha, ay + sy * ha, az + sz * ha, u0, 1.0F, ca, -forward.x, -forward.y, -forward.z);
	}

	private float rx(double x) {
		return (float) (x - camX);
	}

	private float ry(double y) {
		return (float) (y - camY);
	}

	private float rz(double z) {
		return (float) (z - camZ);
	}

	private Sink sink(Identifier tex) {
		Sink sink = sinks.get(tex);
		if (sink == null) {
			sink = new Sink(RenderTypes.entityTranslucentEmissive(tex), true);
			sinks.put(tex, sink);
			order.add(sink);
		}
		return sink;
	}

	private Sink additive() {
		if (additive == null) {
			additive = new Sink(RenderTypes.lightning(), false);
		}
		return additive;
	}

	static int argb(float alpha, int rgb) {
		return ARGB.color(Math.max(0, Math.min(255, Math.round(alpha * 255.0F))), rgb & 0xFFFFFF);
	}

	// ================================================================== sink

	/** Vertices of one render type for this frame, submitted once through a reused writer (as {@code QuadFx.Sink}). */
	static final class Sink {
		private static final int STRIDE = 8;
		private final RenderType type;
		private final boolean entity;
		private float[] data = new float[STRIDE * 256];
		private int[] colors = new int[256];
		private int count;
		private final SubmitNodeCollector.CustomGeometryRenderer writer = this::write;

		Sink(RenderType type, boolean entity) {
			this.type = type;
			this.entity = entity;
		}

		void vertex(float x, float y, float z, float u, float v, int argb, float nx, float ny, float nz) {
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
}
