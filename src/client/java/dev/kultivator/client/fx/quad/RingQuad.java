package dev.kultivator.client.fx.quad;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * A luminous ring as geometry (polish style guide: rings and domes of 16 blocks and more get a ground decal, not only
 * particles): a textured annulus that expands from {@code r0} to {@code r1} with an ease-out and fades, flat on the ground
 * by default or in any plane (vertical wheels, lens rings facing the caster). It may follow an entity (auras).
 */
public final class RingQuad extends QuadFx.Quad {

	private Vec3 center;
	private final Vec3 u;
	private final Vec3 v;
	private final float r0;
	private final float r1;
	private final int rgb;
	private final float alpha;
	private final float spin;
	private int followId = -1;
	private double followLift;

	/** A flat ground ring at {@code center}. */
	public RingQuad(Vec3 center, float r0, float r1, int rgb, float alpha, int life) {
		this(center, new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D), r0, r1, rgb, alpha, 0.0F, life);
	}

	/** A ring in the plane of the unit axes {@code u} × {@code v}, turning {@code spin} radians per tick. */
	public RingQuad(Vec3 center, Vec3 u, Vec3 v, float r0, float r1, int rgb, float alpha, float spin, int life) {
		super(QuadFx.Kind.RING, life);
		this.center = center;
		this.u = u.normalize();
		this.v = v.normalize();
		this.r0 = Math.max(0.05F, r0);
		this.r1 = Math.max(0.05F, r1);
		this.rgb = rgb & 0xFFFFFF;
		this.alpha = alpha;
		this.spin = spin;
	}

	public RingQuad follow(int entityId, double lift) {
		this.followId = entityId;
		this.followLift = lift;
		return this;
	}

	@Override
	protected void tick() {
		if (followId != -1) {
			Entity entity = entity(followId);
			if (entity != null) {
				center = entity.position().add(0.0D, followLift, 0.0D);
			}
		}
	}

	@Override
	protected void render(QuadFx.Frame frame, float at) {
		float a = alpha * fade(at, 1.0F, Math.max(2.0F, life * 0.6F));
		if (a <= 0.01F || !frame.take(2)) {
			return;
		}
		float p = Math.min(1.0F, at / Math.max(1.0F, life * 0.7F));
		float eased = 1.0F - (1.0F - p) * (1.0F - p) * (1.0F - p);
		float r = r0 + (r1 - r0) * eased;
		Vec3 uu = u;
		Vec3 vv = v;
		if (spin != 0.0F) {
			double angle = spin * at;
			double c = Math.cos(angle);
			double s = Math.sin(angle);
			uu = u.scale(c).add(v.scale(s));
			vv = v.scale(c).subtract(u.scale(s));
		}
		frame.plane(frame.ring, center.add(0.0D, 0.02D, 0.0D), uu, vv, r, QuadFx.argb(a, rgb));
	}
}
