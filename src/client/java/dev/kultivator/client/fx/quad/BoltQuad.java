package dev.kultivator.client.fx.quad;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * A lightning bolt as geometry (polish S5/S14, LIGHTNING style: jagged, instant, flickering): a seeded trunk of 8-14
 * segments in nested additive strips (a white core, then three widening violet layers), forked side branches, a re-seed
 * every 2 ticks for the flicker, and a glow disc at the strike point for the first ticks. The ends may follow entities
 * (chain lightning onto moving victims). Never a {@code LightningBolt}.
 */
public final class BoltQuad extends QuadFx.Quad {

	private static final RandomSource SHARED = RandomSource.create();

	private Vec3 from;
	private Vec3 to;
	private final float width;
	private final int rgb;
	private final int core;
	private final int seed;
	private final int segments;
	private final int branches;
	private int fromEntity = -1;
	private int toEntity = -1;
	private float fromLift;
	private float toLift;
	private final double[] px = new double[15];
	private final double[] py = new double[15];
	private final double[] pz = new double[15];

	public BoltQuad(Vec3 from, Vec3 to, float width, int rgb, int core, int seed, int branches, int life) {
		super(QuadFx.Kind.BOLT, life);
		this.from = from;
		this.to = to;
		this.width = Math.max(0.03F, width);
		this.rgb = rgb & 0xFFFFFF;
		this.core = core & 0xFFFFFF;
		this.seed = seed;
		this.segments = (int) Math.max(6, Math.min(14, from.distanceTo(to) / 2.0D + 6));
		this.branches = Math.max(0, branches);
	}

	/** The ends follow these entities (−1 keeps the point), lifted by the heights. */
	public BoltQuad follow(int fromId, float fromLift, int toId, float toLift) {
		this.fromEntity = fromId;
		this.toEntity = toId;
		this.fromLift = fromLift;
		this.toLift = toLift;
		return this;
	}

	@Override
	protected void tick() {
		Entity a = fromEntity == -1 ? null : entity(fromEntity);
		if (a != null) {
			from = a.position().add(0.0D, fromLift, 0.0D);
		}
		Entity b = toEntity == -1 ? null : entity(toEntity);
		if (b != null) {
			to = b.position().add(0.0D, toLift, 0.0D);
		}
	}

	@Override
	protected void render(QuadFx.Frame frame, float at) {
		int phase = (int) (at / 2.0F);
		float flicker = (phase % 2 == 1 ? 0.7F : 1.0F) * fade(at, 0.0F, Math.max(2.0F, life * 0.5F));
		if (flicker <= 0.02F) {
			return;
		}
		int layers = 4;
		int quads = segments * layers + branches * 3 * 2 + 2;
		if (!frame.take(quads)) {
			return;
		}
		RandomSource random = SHARED;
		random.setSeed(seed * 31L + phase);
		trunk(random);
		for (int layer = 0; layer < layers; layer++) {
			float w = layer == 0 ? width * 0.45F : width * (layer + 0.6F);
			float alpha = (layer == 0 ? 0.95F : 0.32F) * flicker;
			int color = layer == 0 ? QuadFx.mix(0xFFFFFF, core, 0.25F) : layer == 1 ? QuadFx.mix(core, rgb, 0.5F) : rgb;
			int argb = QuadFx.argb(alpha, color);
			for (int i = 0; i < segments; i++) {
				float taper = 1.0F - 0.35F * (i / (float) segments);
				frame.segment(frame.add, frame.rx(px[i]), frame.ry(py[i]), frame.rz(pz[i]), frame.rx(px[i + 1]), frame.ry(py[i + 1]), frame.rz(pz[i + 1]),
						w * taper, w * taper * 0.95F, argb, argb, 0.0F, 1.0F);
			}
		}
		double drop = from.distanceTo(to) / segments;
		for (int b = 0; b < branches; b++) {
			int at0 = 1 + random.nextInt(Math.max(1, segments - 2));
			double bx = px[at0];
			double by = py[at0];
			double bz = pz[at0];
			Vec3 axis = to.subtract(from).normalize();
			Vec3 side = axis.cross(new Vec3(0.0D, 1.0D, 0.0D));
			if (side.lengthSqr() < 1.0E-4D) {
				side = new Vec3(1.0D, 0.0D, 0.0D);
			}
			side = side.normalize().scale(random.nextBoolean() ? 1.0D : -1.0D);
			for (int k = 0; k < 3; k++) {
				double nx = bx + axis.x * drop * 0.7D + side.x * drop * (0.4D + random.nextDouble() * 0.6D) + (random.nextDouble() - 0.5D) * drop * 0.4D;
				double ny = by + axis.y * drop * 0.7D + (random.nextDouble() - 0.5D) * drop * 0.5D;
				double nz = bz + axis.z * drop * 0.7D + side.z * drop * (0.4D + random.nextDouble() * 0.6D) + (random.nextDouble() - 0.5D) * drop * 0.4D;
				float w = width * 0.55F * (1.0F - k * 0.25F);
				frame.segment(frame.add, frame.rx(bx), frame.ry(by), frame.rz(bz), frame.rx(nx), frame.ry(ny), frame.rz(nz), w, w * 0.7F,
						QuadFx.argb(0.3F * flicker, rgb), QuadFx.argb(0.2F * flicker, rgb), 0.0F, 1.0F);
				frame.segment(frame.add, frame.rx(bx), frame.ry(by), frame.rz(bz), frame.rx(nx), frame.ry(ny), frame.rz(nz), w * 0.35F, w * 0.25F,
						QuadFx.argb(0.8F * flicker, core), QuadFx.argb(0.6F * flicker, core), 0.0F, 1.0F);
				bx = nx;
				by = ny;
				bz = nz;
			}
		}
		if (at < 4.0F) {
			float glow = (1.0F - at / 4.0F) * flicker;
			frame.billboard(frame.disc, to, width * 9.0F, QuadFx.argb(0.85F * glow, QuadFx.mix(rgb, 0xFFFFFF, 0.5F)));
			frame.billboard(frame.disc, from, width * 5.0F, QuadFx.argb(0.5F * glow, rgb));
		}
	}

	private void trunk(RandomSource random) {
		double length = from.distanceTo(to);
		double jitter = Math.min(3.5D, Math.max(0.25D, length / 14.0D));
		Vec3 axis = to.subtract(from);
		Vec3 side = axis.cross(new Vec3(0.0D, 1.0D, 0.0D));
		if (side.lengthSqr() < 1.0E-6D) {
			side = new Vec3(1.0D, 0.0D, 0.0D);
		}
		side = side.normalize();
		Vec3 lift = side.cross(axis.normalize()).normalize();
		for (int i = 0; i <= segments; i++) {
			double t = i / (double) segments;
			Vec3 p = from.lerp(to, t);
			if (i > 0 && i < segments) {
				double env = Math.sin(Math.PI * t);
				p = p.add(side.scale((random.nextDouble() - 0.5D) * 2.0D * jitter * env)).add(lift.scale((random.nextDouble() - 0.5D) * jitter * env));
			}
			px[i] = p.x;
			py[i] = p.y;
			pz[i] = p.z;
		}
	}

	/** Bolts fanned across a cone from {@code from} along {@code dir} (BOLT FANNED, three_thunder_styles). */
	public static int fan(Vec3 from, Vec3 dir, float length, int count, float spreadDeg, float width, int rgb, int core, int seed, int life, int casterId,
			int delay) {
		int added = 0;
		Vec3 flat = new Vec3(dir.x, 0.0D, dir.z);
		if (flat.lengthSqr() < 1.0E-4D) {
			flat = new Vec3(0.0D, 0.0D, 1.0D);
		}
		flat = flat.normalize();
		for (int i = 0; i < count; i++) {
			double t = count == 1 ? 0.5D : i / (double) (count - 1);
			double angle = Math.toRadians(-spreadDeg / 2.0D + spreadDeg * t);
			double c = Math.cos(angle);
			double s = Math.sin(angle);
			Vec3 d = new Vec3(flat.x * c - flat.z * s, dir.y, flat.x * s + flat.z * c).normalize();
			BoltQuad bolt = new BoltQuad(from, from.add(d.scale(length * (0.85D + 0.15D * ((seed + i) % 3) / 2.0D))), width, rgb, core, seed + i * 97, 2,
					life);
			bolt.caster(casterId).delay(delay + i % 2);
			if (QuadFx.add(bolt)) {
				added++;
			}
		}
		return added;
	}
}
