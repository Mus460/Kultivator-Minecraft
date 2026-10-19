package dev.kultivator.client.fx.quad;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * A ribbon as geometry (polish C4 RIBBON): a segmented camera-facing strip along a path that moves with time, thick and
 * bright at the head and thinning into a dark tail, with a glow disc at the head. The path is a function of (t along the
 * body 0 = head .. 1 = tail, age), so one class draws coiling dragons around a dome or a pillar, whips that crack, wavy
 * flame and blood ribbons, aurora curtains and twin helices.
 */
public final class RibbonQuad extends QuadFx.Quad {

	/** A point of the body at {@code t} (0 head, 1 tail) and age {@code age}. */
	@FunctionalInterface
	public interface Path {
		Vec3 at(double t, float age);
	}

	private final Path path;
	private final int segments;
	private final float width;
	private final float tailWidth;
	private final int head;
	private final int tail;
	private final boolean scales;
	private float alphaMul = 1.0F;
	private int followId = -1;
	private Vec3 followAnchor = Vec3.ZERO;
	private Vec3 offset = Vec3.ZERO;

	public RibbonQuad(Path path, int segments, float width, float tailWidth, int head, int tail, boolean scales, int life) {
		super(QuadFx.Kind.RIBBON, life);
		this.path = path;
		this.segments = Math.max(4, Math.min(48, segments));
		this.width = width;
		this.tailWidth = tailWidth;
		this.head = head & 0xFFFFFF;
		this.tail = tail & 0xFFFFFF;
		this.scales = scales;
	}

	public RibbonQuad alpha(float value) {
		this.alphaMul = value;
		return this;
	}

	/** The whole path is shifted by the entity's movement since {@code anchor} (domains following their caster). */
	public RibbonQuad follow(int entityId, Vec3 anchor) {
		this.followId = entityId;
		this.followAnchor = anchor;
		return this;
	}

	@Override
	protected void tick() {
		if (followId != -1) {
			Entity entity = entity(followId);
			if (entity != null) {
				offset = entity.position().subtract(followAnchor);
			}
		}
	}

	@Override
	protected void render(QuadFx.Frame frame, float at) {
		float a = fade(at, 4.0F, Math.min(10.0F, life * 0.3F)) * alphaMul;
		if (a <= 0.01F) {
			return;
		}
		if (!frame.take(segments * (scales ? 3 : 2) + 1)) {
			return;
		}
		Vec3 prev = path.at(0.0D, at).add(offset);
		Vec3 headPos = prev;
		for (int i = 1; i <= segments; i++) {
			double t = i / (double) segments;
			Vec3 p = path.at(t, at).add(offset);
			float t0 = (float) ((i - 1) / (double) segments);
			float t1 = (float) t;
			float w0 = body(t0);
			float w1 = body(t1);
			int c0 = QuadFx.mix(head, tail, t0);
			int c1 = QuadFx.mix(head, tail, t1);
			float a0 = a * (1.0F - 0.75F * t0);
			float a1 = a * (1.0F - 0.75F * t1);
			frame.segment(frame.soft, prev, p, w0 * 2.2F, w1 * 2.2F, QuadFx.argb(0.3F * a0, c0), QuadFx.argb(0.3F * a1, c1), t0 * 4.0F, t1 * 4.0F);
			frame.segment(frame.strip, prev, p, w0, w1, QuadFx.argb(0.92F * a0, c0), QuadFx.argb(0.92F * a1, c1), 0.0F, 1.0F);
			if (scales && i % 2 == 0) {
				// bright dorsal glints every other segment read as scales / flame tongues
				frame.segment(frame.add, prev, p, w0 * 0.3F, w1 * 0.3F, QuadFx.argb(0.55F * a0, 0xFFFFFF), QuadFx.argb(0.25F * a1, c1), 0.0F, 1.0F);
			}
			prev = p;
		}
		frame.billboard(frame.disc, headPos, width * 1.6F, QuadFx.argb(0.9F * a, QuadFx.mix(head, 0xFFFFFF, 0.4F)));
	}

	private float body(float t) {
		// a quick swell behind the head, then a long taper
		float swell = t < 0.12F ? 0.65F + 0.35F * (t / 0.12F) : 1.0F;
		return (width + (tailWidth - width) * t) * swell;
	}

	// ================================================================== path factories

	/**
	 * A dragon circling {@code center} at {@code radius} and {@code height}, undulating, its body trailing {@code arcDeg}
	 * behind the head; {@code speed} radians per tick (negative turns the other way), {@code phase} offsets copies.
	 */
	public static Path orbit(Vec3 center, double radius, double height, double speed, double phase, double arcDeg, double wave) {
		double arc = Math.toRadians(arcDeg);
		return (t, age) -> {
			double angle = phase + age * speed - t * arc * Math.signum(speed == 0.0D ? 1.0D : speed);
			double y = height + Math.sin(angle * 2.0D + age * 0.15D) * wave;
			double r = radius * (1.0D + 0.06D * Math.sin(angle * 3.0D));
			return center.add(Math.cos(angle) * r, y, Math.sin(angle) * r);
		};
	}

	/** A dragon coiling up a column of {@code height} around {@code base} (fire pillars, dragon soul). */
	public static Path coil(Vec3 base, double radius, double height, double turns, double speed, double phase) {
		return (t, age) -> {
			double rise = (age * speed * 0.15D + phase / (Math.PI * 2.0D)) % 1.0D;
			double s = Math.max(0.0D, Math.min(1.0D, rise - t * 0.45D));
			double angle = phase + s * turns * Math.PI * 2.0D + age * speed;
			return base.add(Math.cos(angle) * radius, s * height, Math.sin(angle) * radius);
		};
	}

	/** A whip from {@code from} to {@code to} that unrolls over {@code crack} ticks with a travelling S-wave, then snaps straight. */
	public static Path whip(Vec3 from, Vec3 to, double amplitude, float crack) {
		Vec3 axis = to.subtract(from);
		Vec3 side0 = axis.cross(new Vec3(0.0D, 1.0D, 0.0D));
		Vec3 side = side0.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side0.normalize();
		return (t, age) -> {
			double reach = Math.min(1.0D, age / Math.max(1.0F, crack));
			double along = (1.0D - t) * reach;
			double settle = Math.max(0.0D, 1.0D - (age - crack) / 6.0D);
			double wave = Math.sin(along * Math.PI * 3.0D - age * 0.9D) * amplitude * Math.sin(Math.PI * along) * (age < crack ? 1.0D : settle);
			return from.add(axis.scale(along)).add(side.scale(wave)).add(0.0D, Math.sin(Math.PI * along) * amplitude * 0.4D, 0.0D);
		};
	}

	/** A wavy ribbon flowing from {@code from} to {@code to} (flame lines, blood streams), its head running ahead over {@code travel}. */
	public static Path wave(Vec3 from, Vec3 to, double amplitude, float travel) {
		Vec3 axis = to.subtract(from);
		Vec3 side0 = axis.cross(new Vec3(0.0D, 1.0D, 0.0D));
		Vec3 side = side0.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side0.normalize();
		return (t, age) -> {
			double headAt = Math.min(1.0D, age / Math.max(1.0F, travel));
			double along = Math.max(0.0D, headAt - t * 0.9D * Math.max(0.3D, headAt));
			double wave = Math.sin(along * 9.0D - age * 0.5D) * amplitude;
			return from.add(axis.scale(along)).add(side.scale(wave)).add(0.0D, Math.cos(along * 7.0D - age * 0.4D) * amplitude * 0.5D, 0.0D);
		};
	}

	/** One strand of a twin helix from {@code from} to {@code to} (fusion orbs, binding ropes). */
	public static Path helix(Vec3 from, Vec3 to, double radius, double turns, double phase, float travel) {
		Vec3 axis = to.subtract(from);
		Vec3 dir = axis.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : axis.normalize();
		Vec3 side0 = dir.cross(new Vec3(0.0D, 1.0D, 0.0D));
		Vec3 side = side0.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side0.normalize();
		Vec3 lift = side.cross(dir).normalize();
		return (t, age) -> {
			double headAt = Math.min(1.0D, age / Math.max(1.0F, travel));
			double along = Math.max(0.0D, headAt - t * 0.5D);
			double angle = phase + along * turns * Math.PI * 2.0D + age * 0.35D;
			return from.add(axis.scale(along)).add(side.scale(Math.cos(angle) * radius)).add(lift.scale(Math.sin(angle) * radius));
		};
	}

	/** A hanging aurora band over {@code center}: a wide arc that ripples (frozen_end_divine_art). */
	public static Path aurora(Vec3 center, double radius, double height, double phase) {
		return (t, age) -> {
			double angle = phase + (t - 0.5D) * Math.PI * 1.2D + Math.sin(age * 0.03D) * 0.3D;
			double y = height + Math.sin(t * 8.0D + age * 0.12D) * 0.8D;
			return center.add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
		};
	}
}
