package dev.kultivator.client.epic.render;

import dev.kultivator.client.epic.Element;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.fx.EpicParticles;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

/**
 * G_RAYS (DESIGN 23.10.4): god rays from a cloud hole or a point: tapered translucent strips at 15-30° from the vertical,
 * slowly turning, alpha 0.12-0.25 ({@code ray_fan.png}). 6 rays at Normal, 12 at Maximal. One large layer (rank 4; the
 * dark down-shafts of big omens rank 9, alpha-blended so they can darken). Fallback: END_ROD rain (≤ 15 % of a layer's
 * tokens) with dust.
 */
public final class RaysRenderer extends Element {

	private final Vec3 origin;
	private final float length;
	private final int rgb;
	private final float alpha;
	private final int count;
	private final float tilt;
	private final float width;
	private final boolean upward;
	private float turn = 0.15F;
	private boolean firedFallback;

	/**
	 * {@code count} rays (halved at Normal) from {@code origin}, {@code length} blocks long, tilted {@code tiltDegrees}
	 * from the vertical; {@code upward} points them up (a vase, a tree) instead of down from a cloud hole.
	 */
	public RaysRenderer(int delay, float life, Vec3 origin, float length, int count, float tiltDegrees, float width, int rgb, float alpha,
						boolean upward) {
		super(delay, life);
		this.origin = origin;
		this.length = length;
		this.count = Math.max(1, count);
		this.tilt = (float) Math.toRadians(tiltDegrees);
		this.width = width;
		this.rgb = rgb & 0xFFFFFF;
		this.alpha = alpha;
		this.upward = upward;
		this.largeRank = 4;
	}

	/** Marks these rays as the dark down-shafts (the lowest large-layer priority). */
	public RaysRenderer shafts() {
		this.largeRank = 9;
		return this;
	}

	public RaysRenderer turn(float degreesPerTick) {
		this.turn = degreesPerTick;
		return this;
	}

	@Override
	public boolean geometry() {
		return true;
	}

	@Override
	protected void fallback() {
		if (largeRank == 9) {
			return;
		}
		if (!firedFallback || (int) age % 4 == 0) {
			firedFallback = true;
			Vec3 ground = upward ? origin : origin.subtract(0.0D, length * 0.9D, 0.0D);
			EpicParticles.rain(ground, Math.max(3.0F, length * 0.25F), Math.min(40.0F, length * 0.6F), 6, rgb);
		}
	}

	@Override
	public void render(EpicRenderer.Frame frame, float at) {
		int n = EpicBudget.tier() >= 2 ? count : Math.max(1, count / 2);
		if (!EpicBudget.takeQuads(n)) {
			return;
		}
		float a = alpha * fade(at, 10.0F, Math.min(60.0F, life * 0.4F));
		if (a <= 0.005F) {
			return;
		}
		float ox = frame.rx(origin.x);
		float oy = frame.ry(origin.y);
		float oz = frame.rz(origin.z);
		double spin = Math.toRadians(at * turn);
		float sinTilt = (float) Math.sin(tilt);
		float cosTilt = (float) Math.cos(tilt);
		int head = ARGB.color(Math.round(a * 255.0F), rgb);
		int tail = ARGB.color(Math.round(a * 60.0F), rgb);
		float[] p = frame.tmp;
		for (int i = 0; i < n; i++) {
			double azimuth = spin + Math.PI * 2.0D * i / n + (i % 2) * 0.3D;
			float dx = (float) Math.cos(azimuth) * sinTilt * (0.7F + 0.3F * (i % 3) / 2.0F);
			float dz = (float) Math.sin(azimuth) * sinTilt * (0.7F + 0.3F * (i % 3) / 2.0F);
			float dy = upward ? cosTilt : -cosTilt;
			float ex = ox + dx * length;
			float ey = oy + dy * length;
			float ez = oz + dz * length;
			float sx = ox;
			float sy = oy;
			float sz = oz;
			float headAlpha = 1.0F;
			if ((float) Math.sqrt(sx * sx + sz * sz) > frame.trueReach) {
				headAlpha = SkyProjection.project(sx, sy, sz, frame.drawRadius, p);
				sx = p[0];
				sy = p[1];
				sz = p[2];
			}
			int h = headAlpha >= 0.999F ? head : ARGB.color(Math.round(a * 255.0F * headAlpha), rgb);
			frame.segment(frame.rays, sx, sy, sz, ex, ey, ez, width * 0.35F, width, h, tail, 0.0F, 1.0F);
		}
	}
}
