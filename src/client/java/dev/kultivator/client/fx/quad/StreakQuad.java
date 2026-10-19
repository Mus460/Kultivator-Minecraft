package dev.kultivator.client.fx.quad;

import net.minecraft.world.phys.Vec3;

/**
 * A movement streak (polish S16): a straight light trail from {@code from} to {@code to} whose head travels there over
 * {@code travel} ticks while the tail follows {@code tail} ticks behind and fades, so a dash or blink reads as motion in
 * view instead of a camera snap. Optional zig-zag for lightning steps.
 */
public final class StreakQuad extends QuadFx.Quad {

	private static final int PIECES = 12;

	private final Vec3 from;
	private final Vec3 to;
	private final float width;
	private final int rgb;
	private final int core;
	private final float travel;
	private final float tail;
	private final float zigzag;
	private final int seed;

	public StreakQuad(Vec3 from, Vec3 to, float width, int rgb, int core, float travel, float tail, float zigzag, int seed, int life) {
		super(QuadFx.Kind.STREAK, life);
		this.from = from;
		this.to = to;
		this.width = Math.max(0.04F, width);
		this.rgb = rgb & 0xFFFFFF;
		this.core = core & 0xFFFFFF;
		this.travel = Math.max(0.0F, travel);
		this.tail = Math.max(0.0F, tail);
		this.zigzag = zigzag;
		this.seed = seed;
	}

	@Override
	protected void render(QuadFx.Frame frame, float at) {
		float a = fade(at, 0.0F, Math.max(3.0F, life * 0.5F));
		if (a <= 0.01F || !frame.take(PIECES * 2)) {
			return;
		}
		double head = travel <= 0.0F ? 1.0D : Math.min(1.0D, at / travel);
		double back = travel <= 0.0F ? Math.min(1.0D, at / Math.max(1.0F, life)) : Math.max(0.0D, Math.min(1.0D, (at - tail) / Math.max(1.0F, travel)));
		if (head - back < 1.0E-3D) {
			return;
		}
		Vec3 axis = to.subtract(from);
		Vec3 side0 = axis.cross(new Vec3(0.0D, 1.0D, 0.0D));
		Vec3 side = side0.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side0.normalize();
		Vec3 prev = null;
		for (int i = 0; i <= PIECES; i++) {
			double t = back + (head - back) * i / PIECES;
			Vec3 p = from.add(axis.scale(t));
			if (zigzag > 0.0F && i > 0 && i < PIECES) {
				double k = Math.sin((i * 7919 + seed) * 0.37D);
				p = p.add(side.scale(k * zigzag)).add(0.0D, Math.cos((i * 104729 + seed) * 0.21D) * zigzag * 0.5D, 0.0D);
			}
			if (prev != null) {
				float f0 = (i - 1) / (float) PIECES;
				float f1 = i / (float) PIECES;
				frame.segment(frame.soft, prev, p, width * 2.4F * f0, width * 2.4F * f1, QuadFx.argb(0.35F * a * f0, rgb), QuadFx.argb(0.35F * a * f1, rgb),
						f0, f1);
				frame.segment(frame.add, prev, p, width * 0.6F * f0, width * 0.6F * f1, QuadFx.argb(0.85F * a * f0, core), QuadFx.argb(0.85F * a * f1, core),
						0.0F, 1.0F);
			}
			prev = p;
		}
	}
}
