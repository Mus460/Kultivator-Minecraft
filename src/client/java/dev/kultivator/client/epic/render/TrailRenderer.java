package dev.kultivator.client.epic.render;

import dev.kultivator.client.epic.Element;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.fx.EpicParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * G_TRAIL and G_SEAM (DESIGN 23.10.4). A trail is a camera-facing ribbon along a quadratic Bézier arc whose head travels
 * from the start to the end (soul flight, the rescue streak, the Hu Fa thread, meridian lines, the sect axis); its ends
 * can follow entities. A seam is a jagged bright strip (sky tear, mirror crack, cracks) that flickers. Thin ones (≤ 0.3 b)
 * use the additive lightning type, wide ones the emissive glow strip (a wide seam is a large layer, rank 7). Far vertices
 * are sky-projected. Fallback: dust along the path, or the seam as sparks.
 */
public final class TrailRenderer extends Element {

	private static final RandomSource SHARED = RandomSource.create();
	private static final int POINTS = 24;

	private Vec3 from;
	private Vec3 to;
	private final float arc;
	private final float width;
	private final int rgb;
	private final float alpha;
	private final boolean seam;
	private final int seed;
	private float travelTicks;
	private float tailTicks = -1.0F;
	private int fromEntity = -1;
	private int toEntity = -1;
	private float fromLift;
	private float toLift;
	private boolean firedFallback;
	private final float[] px = new float[POINTS + 1];
	private final float[] py = new float[POINTS + 1];
	private final float[] pz = new float[POINTS + 1];

	/** A trail from {@code from} to {@code to} bowing up by {@code arc} blocks, or a seam when {@code seam}. */
	public TrailRenderer(int delay, float life, Vec3 from, Vec3 to, float arc, float width, int rgb, float alpha, boolean seam, int seed) {
		super(delay, life);
		this.from = from;
		this.to = to;
		this.arc = arc;
		this.width = width;
		this.rgb = rgb & 0xFFFFFF;
		this.alpha = alpha;
		this.seam = seam;
		this.seed = seed;
		this.largeRank = seam && width > 0.3F ? 7 : 0;
	}

	/** The head travels from start to end over {@code ticks} (0 = the whole path at once). */
	public TrailRenderer travel(float ticks) {
		this.travelTicks = ticks;
		return this;
	}

	/** The tail follows the head {@code ticks} behind (a streak instead of a growing line). */
	public TrailRenderer tail(float ticks) {
		this.tailTicks = ticks;
		return this;
	}

	/** The ends follow these entities (−1 keeps the fixed point), lifted by the given heights. */
	public TrailRenderer follow(int fromId, float fromLift, int toId, float toLift) {
		this.fromEntity = fromId;
		this.toEntity = toId;
		this.fromLift = fromLift;
		this.toLift = toLift;
		return this;
	}

	@Override
	public boolean geometry() {
		return true;
	}

	@Override
	public boolean soft() {
		return width > 0.3F;
	}

	@Override
	protected void tick() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		if (fromEntity >= 0) {
			Entity entity = minecraft.level.getEntity(fromEntity);
			if (entity != null) {
				from = entity.position().add(0.0D, fromLift, 0.0D);
			}
		}
		if (toEntity >= 0) {
			Entity entity = minecraft.level.getEntity(toEntity);
			if (entity != null) {
				to = entity.position().add(0.0D, toLift, 0.0D);
			}
		}
	}

	@Override
	protected void fallback() {
		if (!firedFallback || (int) age % 5 == 0) {
			firedFallback = true;
			int n = EpicParticles.grant(Math.min(30, (int) (from.distanceTo(to) * 1.5D) + 4), false);
			for (int i = 0; i < n; i++) {
				double t = i / (double) Math.max(1, n - 1);
				Vec3 at = point(t);
				EpicParticles.add(seam ? net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK : EpicParticles.dust(rgb, 1.0F), at.x, at.y, at.z,
						0.0D, 0.0D, 0.0D, true);
			}
		}
	}

	private Vec3 point(double t) {
		Vec3 mid = from.lerp(to, 0.5D).add(0.0D, arc, 0.0D);
		Vec3 a = from.lerp(mid, t);
		Vec3 b = mid.lerp(to, t);
		return a.lerp(b, t);
	}

	@Override
	public void render(EpicRenderer.Frame frame, float at) {
		boolean thin = width <= 0.3F;
		if (thin ? !EpicBudget.thinGeometry() : !EpicBudget.softGeometry()) {
			return;
		}
		float head = travelTicks <= 0.0F ? 1.0F : Math.min(1.0F, at / travelTicks);
		float tailT = tailTicks < 0.0F ? 0.0F : Math.max(0.0F, Math.min(1.0F, (at - tailTicks) / Math.max(1.0F, travelTicks)));
		int segments = seam ? 12 : POINTS;
		if (!EpicBudget.takeQuads(segments)) {
			return;
		}
		float a = alpha * fade(at, 3.0F, Math.min(12.0F, life * 0.4F));
		if (seam) {
			a *= 0.7F + 0.3F * (float) Math.abs(Math.sin(at * 1.7F + seed));
		}
		if (a <= 0.01F) {
			return;
		}
		RandomSource random = SHARED;
		random.setSeed(seed);
		Vec3 axis = to.subtract(from);
		Vec3 side = axis.cross(new Vec3(0.0D, 1.0D, 0.0D));
		if (side.lengthSqr() < 1.0E-6D) {
			side = new Vec3(1.0D, 0.0D, 0.0D);
		}
		side = side.normalize();
		double jag = seam ? Math.min(3.0D, Math.max(0.15D, axis.length() * 0.05D)) : 0.0D;
		float[] p = frame.tmp;
		for (int i = 0; i <= segments; i++) {
			double t = tailT + (head - tailT) * i / (double) segments;
			Vec3 at3 = point(t);
			if (seam && i > 0 && i < segments) {
				double off = (random.nextDouble() - 0.5D) * 2.0D * jag;
				at3 = at3.add(side.scale(off)).add(0.0D, (random.nextDouble() - 0.5D) * jag * 0.6D, 0.0D);
			}
			float x = frame.rx(at3.x);
			float y = frame.ry(at3.y);
			float z = frame.rz(at3.z);
			if ((float) Math.sqrt(x * x + z * z) > frame.trueReach) {
				SkyProjection.project(x, y, z, frame.drawRadius, p);
				x = p[0];
				y = p[1];
				z = p[2];
			}
			px[i] = x;
			py[i] = y;
			pz[i] = z;
		}
		EpicRenderer.Sink sink = thin ? frame.bolts() : frame.glow;
		for (int i = 0; i < segments; i++) {
			float t0 = i / (float) segments;
			float t1 = (i + 1) / (float) segments;
			float w0 = width * (seam ? 0.6F + 0.4F * (1.0F - Math.abs(t0 - 0.5F) * 2.0F) : 1.0F);
			float w1 = width * (seam ? 0.6F + 0.4F * (1.0F - Math.abs(t1 - 0.5F) * 2.0F) : 1.0F);
			float fadeTail = tailTicks < 0.0F ? 1.0F : t0;
			int c0 = ARGB.color(Math.round(a * 255.0F * fadeTail), rgb);
			int c1 = ARGB.color(Math.round(a * 255.0F * (tailTicks < 0.0F ? 1.0F : t1)), rgb);
			frame.segment(sink, px[i], py[i], pz[i], px[i + 1], py[i + 1], pz[i + 1], w0, w1, c0, c1, t0, t1);
		}
	}
}
