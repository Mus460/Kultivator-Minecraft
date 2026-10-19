package dev.kultivator.client.fx.quad;

import net.minecraft.world.phys.Vec3;

/**
 * A crescent slash as geometry (polish S14, SWORD style: crescents, fast): an arc of {@code radius} blocks spanning
 * {@code 2 × half} radians, thick in the middle and razor thin at the tips, in a horizontal plane (or a vertical one),
 * flying {@code travel} blocks along its direction over its life with an ease-out. Three strips: a soft wide glow, the
 * coloured blade and a white-hot leading edge; the trailing copies at half alpha give the motion smear.
 */
public final class CrescentQuad extends QuadFx.Quad {

	private static final int POINTS = 18;

	private final Vec3 origin;
	private final Vec3 dir;
	private final Vec3 side;
	private final float radius;
	private final float half;
	private final float thickness;
	private final float travel;
	private final int rgb;
	private final int core;
	private float sweep;

	/**
	 * A crescent at {@code origin} moving along {@code dir}; {@code vertical} puts the arc in the plane of {@code dir} and up
	 * (a standing wall or a downward cleave), otherwise in the horizontal plane.
	 */
	public CrescentQuad(Vec3 origin, Vec3 dir, boolean vertical, float radius, float halfAngle, float thickness, float travel, int rgb, int core,
			int life) {
		super(QuadFx.Kind.CRESCENT, life);
		this.origin = origin;
		Vec3 d = dir.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : dir.normalize();
		this.dir = d;
		Vec3 s = d.cross(new Vec3(0.0D, 1.0D, 0.0D));
		s = s.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : s.normalize();
		this.side = vertical ? s.cross(d).normalize() : s;
		this.radius = Math.max(0.3F, radius);
		this.half = Math.max(0.2F, Math.min(1.6F, halfAngle));
		this.thickness = Math.max(0.05F, thickness);
		this.travel = travel;
		this.rgb = rgb & 0xFFFFFF;
		this.core = core & 0xFFFFFF;
	}

	/** The blade sweeps open over the first {@code ticks} instead of appearing whole (a swing rather than a thrown arc). */
	public CrescentQuad sweep(float ticks) {
		this.sweep = ticks;
		return this;
	}

	@Override
	protected void render(QuadFx.Frame frame, float at) {
		float a = fade(at, 0.0F, Math.max(3.0F, life * 0.45F));
		if (a <= 0.01F) {
			return;
		}
		if (!frame.take(POINTS * 4 + 2)) {
			return;
		}
		float p = Math.min(1.0F, at / life);
		float eased = 1.0F - (1.0F - p) * (1.0F - p);
		float open = sweep > 0.0F ? Math.min(1.0F, at / sweep) : 1.0F;
		Vec3 center = origin.add(dir.scale(travel * eased));
		float grow = 0.75F + 0.25F * Math.min(1.0F, at / 2.0F);
		for (int copy = 1; copy >= 0; copy--) {
			// copy 1: the smear trailing a little behind at half alpha
			Vec3 c = copy == 1 ? center.subtract(dir.scale(Math.min(1.5D, radius * 0.25D))) : center;
			float ca = copy == 1 ? 0.45F : 1.0F;
			Vec3 prev = null;
			float prevW = 0.0F;
			for (int i = 0; i <= POINTS; i++) {
				float t = i / (float) POINTS;
				double angle = -half * open + 2.0D * half * open * t;
				Vec3 pt = c.subtract(dir.scale(radius * 0.7D)).add(dir.scale(Math.cos(angle) * radius * grow)).add(side.scale(Math.sin(angle) * radius * grow));
				float w = thickness * (float) Math.pow(Math.sin(Math.PI * t), 0.8D) + 0.02F;
				if (prev != null) {
					if (copy == 0) {
						frame.segment(frame.soft, prev, pt, prevW * 3.0F, w * 3.0F, QuadFx.argb(0.32F * a, rgb), QuadFx.argb(0.32F * a, rgb), t, t);
					}
					frame.segment(frame.strip, prev, pt, prevW * 1.4F, w * 1.4F, QuadFx.argb(0.85F * a * ca, QuadFx.mix(rgb, core, 0.35F)),
							QuadFx.argb(0.85F * a * ca, QuadFx.mix(rgb, core, 0.35F)), 0.0F, 1.0F);
					if (copy == 0) {
						frame.segment(frame.add, prev, pt, prevW * 0.35F, w * 0.35F, QuadFx.argb(0.9F * a, 0xFFFFFF), QuadFx.argb(0.9F * a, 0xFFFFFF), 0.0F,
								1.0F);
					}
				}
				prev = pt;
				prevW = w;
			}
		}
	}

	/** {@code count} crescents fanned over {@code spreadDeg} (CRESCENT_FLY with a count, moon_blade). */
	public static int fan(Vec3 origin, Vec3 dir, boolean vertical, int count, float spreadDeg, float radius, float halfAngle, float thickness,
			float travel, int rgb, int core, int life, int casterId, int delay) {
		int added = 0;
		for (int i = 0; i < count; i++) {
			double t = count == 1 ? 0.5D : i / (double) (count - 1);
			double angle = Math.toRadians(-spreadDeg / 2.0D + spreadDeg * t);
			double c = Math.cos(angle);
			double s = Math.sin(angle);
			Vec3 d = new Vec3(dir.x * c - dir.z * s, dir.y, dir.x * s + dir.z * c);
			CrescentQuad quad = new CrescentQuad(origin, d, vertical, radius, halfAngle, thickness, travel, rgb, core, life);
			quad.caster(casterId).delay(delay + i / 2);
			if (QuadFx.add(quad)) {
				added++;
			}
		}
		return added;
	}
}
