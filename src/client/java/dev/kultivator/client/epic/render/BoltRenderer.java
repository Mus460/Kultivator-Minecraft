package dev.kultivator.client.epic.render;

import dev.kultivator.client.epic.Element;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.fx.EpicParticles;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * G_BOLT (DESIGN 23.10.4): lightning drawn as geometry like the vanilla bolt renderer (never a {@code LightningBolt}).
 * A seeded trunk of 8-12 segments drawn as camera-facing strips in four nested widths w…4w at alpha 0.3 each (additive
 * {@code lightning()}), with side branches; it lives 8-10 ticks with two re-seeds (one luminance event) and is held at
 * peak brightness during a hit-stop (its age stops with σ). Vertices beyond the fog's reach are sky-projected. Thin
 * convergence bolts (half width, two layers, no branches) cost half a big-bolt slot. Fallback: particle bolts.
 */
public final class BoltRenderer extends Element {

	private static final RandomSource SHARED = RandomSource.create();

	private final Vec3 top;
	private final Vec3 bottom;
	private final @Nullable Vec3 kink;
	private final float width;
	private final int rgb;
	private final int core;
	private final int seed;
	private final int segments;
	private final int branches;
	private final boolean thin;
	private final boolean straight;
	private int layers = 4;
	private boolean firedFallback;
	private final float[] px = new float[13];
	private final float[] py = new float[13];
	private final float[] pz = new float[13];

	/** A bolt from {@code top} to {@code bottom}; {@code branches} side branches; {@code thin} = a convergence bolt. */
	public BoltRenderer(int delay, float life, Vec3 top, Vec3 bottom, @Nullable Vec3 kink, float width, int rgb, int core, int seed, int branches,
						boolean thin) {
		super(delay, life);
		this.top = top;
		this.bottom = bottom;
		this.kink = kink;
		this.width = Math.max(0.03F, width);
		this.rgb = rgb & 0xFFFFFF;
		this.core = core & 0xFFFFFF;
		this.seed = seed;
		this.thin = thin;
		this.straight = false;
		this.segments = 8 + Math.floorMod(seed, 5);
		this.branches = thin ? 0 : Math.max(0, branches);
		if (thin) {
			layers = 2;
		}
	}

	/** A straight glowing thread (the 0.05-b lead thread from the eye to the ring), no jitter, one layer. */
	public BoltRenderer(int delay, float life, Vec3 top, Vec3 bottom, float width, int rgb) {
		super(delay, life);
		this.top = top;
		this.bottom = bottom;
		this.kink = null;
		this.width = Math.max(0.02F, width);
		this.rgb = rgb & 0xFFFFFF;
		this.core = rgb & 0xFFFFFF;
		this.seed = 0;
		this.thin = true;
		this.straight = true;
		this.segments = 1;
		this.branches = 0;
		this.layers = 1;
	}

	@Override
	public boolean geometry() {
		return true;
	}

	@Override
	public boolean soft() {
		return false;
	}

	@Override
	protected void fallback() {
		if (!firedFallback) {
			firedFallback = true;
			if (straight) {
				return;
			}
			if (kink != null) {
				EpicParticles.bolt(top, kink, rgb, 4);
				EpicParticles.bolt(kink, bottom, rgb, 4);
			} else {
				EpicParticles.bolt(top, bottom, rgb, Math.min(8, segments));
			}
		}
	}

	@Override
	public void render(EpicRenderer.Frame frame, float at) {
		if (!EpicBudget.thinGeometry()) {
			return;
		}
		boolean big = !straight && (!thin || width > 0.3F);
		if (big && !EpicBudget.takeBolt(thin)) {
			return;
		}
		int quads = segments * layers + branches * 3 * layers;
		if (!EpicBudget.takeQuads(quads)) {
			return;
		}
		// Two re-seeds over the life; brightness peaks early and flickers with each re-seed.
		int phase = straight ? 0 : Math.min(2, (int) (at / Math.max(1.0F, life / 3.0F)));
		float flicker = straight ? fade(at, 2.0F, 4.0F) : (phase == 1 ? 0.75F : 1.0F) * fade(at, 0.0F, Math.max(2.0F, life * 0.35F));
		if (flicker <= 0.02F) {
			return;
		}
		RandomSource random = SHARED;
		random.setSeed(seed * 31L + phase);
		buildTrunk(frame, random);
		EpicRenderer.Sink sink = frame.bolts();
		for (int layer = 0; layer < layers; layer++) {
			float w = width * (layer + 1);
			float alpha = (layers == 1 ? 0.9F : 0.3F) * flicker;
			int color = layer == 0 ? mix(core, rgb, 0.25F) : rgb;
			int argb = ARGB.color(Math.round(alpha * 255.0F), color);
			for (int i = 0; i < segments; i++) {
				float taper = 1.0F - 0.25F * (i / (float) segments);
				frame.segment(sink, px[i], py[i], pz[i], px[i + 1], py[i + 1], pz[i + 1], w * taper, w * taper * 0.95F, argb, argb, 0.0F, 1.0F);
			}
		}
		for (int b = 0; b < branches; b++) {
			int from = 1 + random.nextInt(Math.max(1, segments - 2));
			float bx = px[from];
			float by = py[from];
			float bz = pz[from];
			float dx = (random.nextFloat() - 0.5F) * 8.0F;
			float dz = (random.nextFloat() - 0.5F) * 8.0F;
			float drop = Math.abs(py[0] - py[segments]) / segments;
			for (int k = 0; k < 3; k++) {
				float nx = bx + dx * (0.5F + random.nextFloat() * 0.5F);
				float ny = by - drop * (0.6F + random.nextFloat() * 0.6F);
				float nz = bz + dz * (0.5F + random.nextFloat() * 0.5F);
				for (int layer = 0; layer < layers; layer++) {
					float w = width * 0.5F * (layer + 1);
					int argb = ARGB.color(Math.round(0.25F * flicker * 255.0F), rgb);
					frame.segment(sink, bx, by, bz, nx, ny, nz, w, w * 0.7F, argb, argb, 0.0F, 1.0F);
				}
				bx = nx;
				by = ny;
				bz = nz;
			}
		}
	}

	private void buildTrunk(EpicRenderer.Frame frame, RandomSource random) {
		double length = top.distanceTo(bottom);
		double jitter = straight ? 0.0D : Math.min(6.0D, Math.max(0.6D, length / 14.0D));
		float[] p = frame.tmp;
		for (int i = 0; i <= segments; i++) {
			double t = i / (double) segments;
			Vec3 at;
			if (kink != null) {
				at = t < 0.5D ? top.lerp(kink, t * 2.0D) : kink.lerp(bottom, (t - 0.5D) * 2.0D);
			} else {
				at = top.lerp(bottom, t);
			}
			double jx = i == 0 || i == segments ? 0.0D : (random.nextDouble() - 0.5D) * 2.0D * jitter;
			double jz = i == 0 || i == segments ? 0.0D : (random.nextDouble() - 0.5D) * 2.0D * jitter;
			float x = frame.rx(at.x + jx);
			float y = frame.ry(at.y);
			float z = frame.rz(at.z + jz);
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
	}

	private static int mix(int a, int b, float t) {
		int r = Math.round(ARGB.red(a) + (ARGB.red(b) - ARGB.red(a)) * t);
		int g = Math.round(ARGB.green(a) + (ARGB.green(b) - ARGB.green(a)) * t);
		int bl = Math.round(ARGB.blue(a) + (ARGB.blue(b) - ARGB.blue(a)) * t);
		return (r << 16) | (g << 8) | bl;
	}
}
