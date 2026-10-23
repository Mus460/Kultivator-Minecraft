package dev.kultivator.client.sekte.langit;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/**
 * Kubah Formasi, the island's wards made visible (DESIGN 37.6.5):
 * <ul>
 * <li>while the Grand Formation runs: a soft aura band standing round the island's rim and a sparse half-dome of 1 / 3 / 6
 * latitude rings (≤ 24 / 40 / 64 quads each, {@code ring_soft.png}) of radius {@code domeRadius} over the throne, in the
 * sect's emblem colour (fallback jade {@code 0x8FE3C8}), breathing over 4 s. The formation module draws no continuous
 * Grand Formation dome of its own ({@code FormasiFx} only rings and one-off events), so nothing is drawn twice;</li>
 * <li>a closed gate (Gerbang Sembilan Langit or the Grand Formation shut to outsiders) raises the rim band to a taller,
 * brighter wall with a slow upward shimmer;</li>
 * <li>while the Cloud Net formation runs: four faint light columns at the {@code jaring_*} pillars and the net itself
 * ({@code jaring.png}) spread under the island's belly, pulsing as if breathing.</li>
 * </ul>
 */
final class KubahFormasi {

	private static final int FALLBACK = 0x8FE3C8;
	private static final float BREATH = 4.0F;

	private static final AwanSink RING = new AwanSink(AwanSink.RING, false);
	private static final AwanSink NET = new AwanSink(AwanSink.JARING, false);
	private static final AwanSink PILLAR = new AwanSink(AwanSink.PILAR, false);

	static AwanSink ring() {
		return RING;
	}

	static AwanSink net() {
		return NET;
	}

	static AwanSink pillar() {
		return PILLAR;
	}

	static void draw(LangitFrame f) {
		LangitSkyState.Hall hall = f.hall;
		if (f.tier < 0 || hall.tethered()) {
			return;
		}
		int rgb = hall.color != 0 ? hall.color : FALLBACK;
		float breath = 0.75F + 0.25F * Mth.sin(f.seconds * Mth.TWO_PI / BREATH);
		boolean grand = hall.grandFormation();
		boolean closed = hall.gateClosed();
		double cx = hall.centreX();
		double cz = hall.centreZ();
		float rimY = f.ry(hall.yP - 1.0D);
		int segments = LangitBudget.DOME_SEGMENTS[f.tier];
		if (grand || closed) {
			// the rim band (a wall of soft light standing on the island's edge)
			float r = (float) (hall.radius() * 0.92D);
			float height = closed ? 7.0F : 2.5F;
			float a = (closed ? 0.42F : 0.24F) * breath;
			if (LangitBudget.take(segments)) {
				float scroll = closed ? f.seconds * 0.15F : 0.0F;
				for (int i = 0; i < segments; i++) {
					float t0 = (float) i / segments * Mth.TWO_PI;
					float t1 = (float) (i + 1) / segments * Mth.TWO_PI;
					float x0 = f.rx(cx) + Mth.cos(t0) * r;
					float z0 = f.rz(cz) + Mth.sin(t0) * r;
					float x1 = f.rx(cx) + Mth.cos(t1) * r;
					float z1 = f.rz(cz) + Mth.sin(t1) * r;
					float u0 = i * 0.5F;
					float u1 = (i + 1) * 0.5F;
					RING.quad(x0, rimY, z0, u0, 0.5F + scroll, AwanSink.argb(a, rgb),
							x1, rimY, z1, u1, 0.5F + scroll, AwanSink.argb(a, rgb),
							x1, rimY + height, z1, u1, 1.0F + scroll, AwanSink.argb(0.0F, rgb),
							x0, rimY + height, z0, u0, 1.0F + scroll, AwanSink.argb(0.0F, rgb));
				}
			}
		}
		if (grand && hall.domeRadius > 0) {
			dome(f, hall, rgb, breath, segments);
		}
		if (hall.net()) {
			net(f, hall, rgb, breath);
		}
	}

	/** The sparse half-dome: latitude rings, each a thin band tangent to the sphere. */
	private static void dome(LangitFrame f, LangitSkyState.Hall hall, int rgb, float breath, int segments) {
		BlockPos centre = hall.throne != null ? hall.throne : hall.origin;
		float ox = f.rx(centre.getX() + 0.5D);
		float oy = f.ry(centre.getY());
		float oz = f.rz(centre.getZ() + 0.5D);
		float radius = hall.domeRadius;
		int rings = LangitBudget.DOME_RINGS[f.tier];
		float band = 0.9F * Mth.DEG_TO_RAD * 90.0F / Math.max(8.0F, radius);
		for (int k = 0; k < rings; k++) {
			if (!LangitBudget.take(segments)) {
				return;
			}
			float phi = (k + 1.0F) / (rings + 1.0F) * 78.0F * Mth.DEG_TO_RAD;
			// each ring brightens in turn, a pulse climbing the dome
			float climb = 0.5F + 0.5F * Mth.sin(f.seconds * 1.2F - k * 0.9F);
			float a = (0.10F + 0.14F * climb) * breath;
			float lo = phi - band;
			float hi = phi + band;
			float rLo = radius * Mth.cos(lo);
			float rHi = radius * Mth.cos(hi);
			float yLo = oy + radius * Mth.sin(lo);
			float yHi = oy + radius * Mth.sin(hi);
			int c = AwanSink.argb(a, AwanSink.mix(rgb, 0xFFFFFF, 0.25F));
			for (int i = 0; i < segments; i++) {
				float t0 = (float) i / segments * Mth.TWO_PI + f.seconds * 0.02F;
				float t1 = (float) (i + 1) / segments * Mth.TWO_PI + f.seconds * 0.02F;
				float c0 = Mth.cos(t0);
				float s0 = Mth.sin(t0);
				float c1 = Mth.cos(t1);
				float s1 = Mth.sin(t1);
				RING.quad(ox + c0 * rLo, yLo, oz + s0 * rLo, i * 0.5F, 0.0F, c,
						ox + c1 * rLo, yLo, oz + s1 * rLo, (i + 1) * 0.5F, 0.0F, c,
						ox + c1 * rHi, yHi, oz + s1 * rHi, (i + 1) * 0.5F, 1.0F, c,
						ox + c0 * rHi, yHi, oz + s0 * rHi, i * 0.5F, 1.0F, c);
			}
		}
	}

	/** The Cloud Net: its four pillars and the lattice under the island. */
	private static void net(LangitFrame f, LangitSkyState.Hall hall, int rgb, float breath) {
		int pillars = Math.min(4, hall.nets.size());
		if (pillars > 0 && LangitBudget.take(pillars)) {
			for (int i = 0; i < pillars; i++) {
				BlockPos p = hall.nets.get(i);
				float x = f.rx(p.getX() + 0.5D);
				float z = f.rz(p.getZ() + 0.5D);
				float top = f.ry(p.getY() + 5.0D);
				float bottom = f.ry(hall.skyFloorY - 6.0D);
				float a = 0.22F * breath;
				PILLAR.beam(x, top, z, x, bottom, z, 0.45F, f.seconds * -0.5F, f.seconds * -0.5F + (top - bottom) / 8.0F,
						AwanSink.argb(a, 0xDDF2FF), AwanSink.argb(a * 0.3F, 0xDDF2FF));
			}
		}
		if (LangitBudget.take(1)) {
			float size = (float) (hall.radius() * 1.15D);
			float y = f.ry(hall.skyFloorY - 6.0D);
			float a = 0.16F + 0.08F * breath;
			NET.flat(f.rx(hall.centreX()), y, f.rz(hall.centreZ()), size, f.seconds * 0.01F, AwanSink.argb(a, AwanSink.mix(rgb, 0xE8F6FF, 0.6F)));
		}
	}

	private KubahFormasi() {
	}
}
