package dev.kultivator.client.epic.render;

import dev.kultivator.client.epic.Element;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.fx.EpicParticles;
import dev.kultivator.client.epic.fx.PointClouds;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * G_GATE (DESIGN 23.10.4): a paifang outline (two pillars, three beams, a curved roof) of glowing line quads 0.4-1.5 b
 * thick, and two door planes swinging open with light pouring from the gap. Used for the Pintu Keabadian (24×36 b,
 * opening L/9 of 80° per layer), the Heavenly Gate hint of the Dewa Abadi omen (48×72, α 0.2) and the Gerbang Langit
 * (64×96). The gate faces the subject; gates out of the fog's reach are sky-projected. One large layer (rank 2).
 * Fallback: a gold dust outline.
 */
public final class GateRenderer extends Element {

	private static final float[][] LINES = {
			{-0.35F, 0.0F, -0.35F, 0.8F}, {0.35F, 0.0F, 0.35F, 0.8F},
			{-0.45F, 0.62F, 0.45F, 0.62F}, {-0.5F, 0.8F, 0.5F, 0.8F}, {-0.36F, 0.9F, 0.36F, 0.9F},
			{0.0F, 0.62F, 0.0F, 0.8F},
			{-0.56F, 0.86F, -0.3F, 0.95F}, {-0.3F, 0.95F, 0.0F, 0.98F}, {0.0F, 0.98F, 0.3F, 0.95F}, {0.3F, 0.95F, 0.56F, 0.86F}};

	private final Vec3 center;
	private final Vec3 facing;
	private final float width;
	private final float height;
	private final int rgb;
	private final float alpha;
	private float open;
	private float openTarget;
	private float openSpeed = 0.01F;
	private boolean firedFallback;
	private final float[] p = new float[3];

	/** A gate whose bottom centre is at {@code center}, facing the point {@code facing}. */
	public GateRenderer(int delay, float life, Vec3 center, Vec3 facing, float width, float height, int rgb, float alpha) {
		super(delay, life);
		this.center = center;
		this.facing = facing;
		this.width = width;
		this.height = height;
		this.rgb = rgb & 0xFFFFFF;
		this.alpha = alpha;
		this.largeRank = 2;
	}

	/** Swings the doors to {@code fraction} of 80° over {@code ticks}. */
	public GateRenderer open(float fraction, float ticks) {
		this.openTarget = Mth.clamp(fraction, 0.0F, 1.0F);
		this.openSpeed = Math.abs(openTarget - open) / Math.max(1.0F, ticks) + 1.0E-4F;
		return this;
	}

	/** Sets the doors' starting opening at once. */
	public GateRenderer openAt(float fraction) {
		this.open = Mth.clamp(fraction, 0.0F, 1.0F);
		this.openTarget = open;
		return this;
	}

	@Override
	public boolean geometry() {
		return true;
	}

	@Override
	protected void tick() {
		open += Mth.clamp(openTarget - open, -openSpeed, openSpeed);
	}

	@Override
	protected void fallback() {
		if (!firedFallback || (int) age % 8 == 0) {
			firedFallback = true;
			Vec3 dir = facing.subtract(center);
			Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
			side = side.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
			EpicParticles.outline(PointClouds.gate(), center, side, height, 60, EpicParticles.dust(rgb, 2.5F), true);
		}
	}

	@Override
	public void render(EpicRenderer.Frame frame, float at) {
		if (!EpicBudget.takeQuads(LINES.length + 12)) {
			return;
		}
		float a = alpha * fade(at, 30.0F, 30.0F);
		if (a <= 0.01F) {
			return;
		}
		// Basis: n toward the facing point; up from world y, or the horizontal view when the gate is overhead.
		double nx = facing.x - center.x;
		double ny = facing.y - center.y;
		double nz = facing.z - center.z;
		double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (nl < 1.0E-4D) {
			return;
		}
		nx /= nl;
		ny /= nl;
		nz /= nl;
		double hx = 0.0D;
		double hy = 1.0D;
		double hz = 0.0D;
		if (Math.abs(ny) > 0.9D) {
			hx = -nx;
			hy = 0.0D;
			hz = -nz;
			double hl = Math.sqrt(hx * hx + hz * hz);
			if (hl < 1.0E-4D) {
				hx = 0.0D;
				hz = 1.0D;
			} else {
				hx /= hl;
				hz /= hl;
			}
		}
		// right = n × h, up = right × n
		double rxv = ny * hz - nz * hy;
		double ryv = nz * hx - nx * hz;
		double rzv = nx * hy - ny * hx;
		double rl = Math.sqrt(rxv * rxv + ryv * ryv + rzv * rzv);
		rxv /= rl;
		ryv /= rl;
		rzv /= rl;
		double uxv = ryv * nz - rzv * ny;
		double uyv = rzv * nx - rxv * nz;
		double uzv = rxv * ny - ryv * nx;
		int line = ARGB.color(Math.round(a * 255.0F), rgb);
		float thick = Mth.clamp(width / 40.0F, 0.4F, 1.5F);
		for (float[] l : LINES) {
			drawLine(frame, l[0], l[1], l[2], l[3], 0.0F, 0.0F, rxv, ryv, rzv, uxv, uyv, uzv, nx, ny, nz, thick, line, line);
		}
		// Doors: hinged at the pillars, swinging away from the viewer by up to 80°.
		double angle = Math.toRadians(80.0D * open);
		float swingX = (float) Math.cos(angle) * 0.35F;
		float swingN = (float) -Math.sin(angle) * 0.35F;
		int door = ARGB.color(Math.round(a * 200.0F), rgb);
		drawLine(frame, -0.35F, 0.0F, -0.35F + swingX, 0.0F, 0.0F, swingN, rxv, ryv, rzv, uxv, uyv, uzv, nx, ny, nz, thick * 0.7F, door, door);
		drawLine(frame, -0.35F + swingX, 0.0F, -0.35F + swingX, 0.6F, swingN, swingN, rxv, ryv, rzv, uxv, uyv, uzv, nx, ny, nz, thick * 0.7F, door, door);
		drawLine(frame, -0.35F, 0.6F, -0.35F + swingX, 0.6F, 0.0F, swingN, rxv, ryv, rzv, uxv, uyv, uzv, nx, ny, nz, thick * 0.7F, door, door);
		drawLine(frame, 0.35F, 0.0F, 0.35F - swingX, 0.0F, 0.0F, swingN, rxv, ryv, rzv, uxv, uyv, uzv, nx, ny, nz, thick * 0.7F, door, door);
		drawLine(frame, 0.35F - swingX, 0.0F, 0.35F - swingX, 0.6F, swingN, swingN, rxv, ryv, rzv, uxv, uyv, uzv, nx, ny, nz, thick * 0.7F, door, door);
		drawLine(frame, 0.35F, 0.6F, 0.35F - swingX, 0.6F, 0.0F, swingN, rxv, ryv, rzv, uxv, uyv, uzv, nx, ny, nz, thick * 0.7F, door, door);
		// Light pouring from the gap: a broad glow strip down the middle, as wide as the opening.
		if (open > 0.02F) {
			float gap = 0.7F * (float) (1.0D - Math.cos(angle)) * 0.5F + 0.02F;
			int light = ARGB.color(Math.round(a * 230.0F * Math.min(1.0F, open * 2.0F)), 0xFFF6D8);
			drawLine(frame, 0.0F, 0.0F, 0.0F, 0.6F, -0.01F, -0.01F, rxv, ryv, rzv, uxv, uyv, uzv, nx, ny, nz, gap * width, light, light);
		}
	}

	private void drawLine(EpicRenderer.Frame frame, float x0, float y0, float x1, float y1, float n0, float n1, double rxv, double ryv, double rzv,
						  double uxv, double uyv, double uzv, double nx, double ny, double nz, float thick, int c0, int c1) {
		double ax = center.x + rxv * x0 * width + uxv * y0 * height - nx * n0 * width;
		double ay = center.y + ryv * x0 * width + uyv * y0 * height - ny * n0 * width;
		double az = center.z + rzv * x0 * width + uzv * y0 * height - nz * n0 * width;
		double bx = center.x + rxv * x1 * width + uxv * y1 * height - nx * n1 * width;
		double by = center.y + ryv * x1 * width + uyv * y1 * height - ny * n1 * width;
		double bz = center.z + rzv * x1 * width + uzv * y1 * height - nz * n1 * width;
		float sax = frame.rx(ax);
		float say = frame.ry(ay);
		float saz = frame.rz(az);
		float sbx = frame.rx(bx);
		float sby = frame.ry(by);
		float sbz = frame.rz(bz);
		float w0 = thick;
		float w1 = thick;
		float fa = 1.0F;
		float fb = 1.0F;
		float da = (float) Math.sqrt(sax * sax + saz * saz);
		if (da > frame.trueReach) {
			float length = (float) Math.sqrt(sax * sax + say * say + saz * saz);
			fa = SkyProjection.project(sax, say, saz, frame.drawRadius, p);
			w0 *= frame.drawRadius / Math.max(1.0F, length);
			sax = p[0];
			say = p[1];
			saz = p[2];
		}
		float db = (float) Math.sqrt(sbx * sbx + sbz * sbz);
		if (db > frame.trueReach) {
			float length = (float) Math.sqrt(sbx * sbx + sby * sby + sbz * sbz);
			fb = SkyProjection.project(sbx, sby, sbz, frame.drawRadius, p);
			w1 *= frame.drawRadius / Math.max(1.0F, length);
			sbx = p[0];
			sby = p[1];
			sbz = p[2];
		}
		int ca = fa >= 0.999F ? c0 : ARGB.color(Math.round(ARGB.alpha(c0) * fa), c0 & 0xFFFFFF);
		int cb = fb >= 0.999F ? c1 : ARGB.color(Math.round(ARGB.alpha(c1) * fb), c1 & 0xFFFFFF);
		frame.segment(frame.glow, sax, say, saz, sbx, sby, sbz, w0, w1, ca, cb, 0.0F, 1.0F);
	}
}
