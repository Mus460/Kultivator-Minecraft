package dev.kultivator.client.epic.render;

import dev.kultivator.client.epic.Element;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.EpicClock;
import dev.kultivator.epic.EpicColors;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * G_VORTEX (DESIGN 23.10.4): the tribulation cloud whirlpool. A concentric ring mesh with a logarithmic-spiral offset
 * ({@code cloud_swirl.png}), alpha-blended so it can be dark; a funnel dips toward the eye, the eye rim is darker, an eye
 * glow disc shines inside and thin horizontal micro-arcs (lightning type) flicker in the cloud. Two shells: the inner
 * one in true position (out to 0.85 × fog start), the outer rim sky-projected above 20° elevation so the rim reads
 * kilometres wide. Each shell is a large layer (inner keeps rank 1, the rim rank 5). The mesh is built once per beat in
 * local coordinates and turned by one sine and cosine per frame. Minimal: no vortex (the existing tribulation disc, the
 * sky grade and the HUD gradient carry it).
 */
public final class VortexRenderer extends Element {

	private static final int[] RINGS = {0, 10, 16};
	private static final int[] SEGMENTS = {0, 40, 48};
	private static final int MAX_ARCS = 6;
	private static final RandomSource SHARED = RandomSource.create();

	private final double cx;
	private final double cy;
	private final double cz;
	private final float radius;
	private final float eye;
	private final int cloud;
	private final int glowRgb;
	private final RandomSource random;
	private int rings;
	private int segments;
	private float[] localX;
	private float[] localZ;
	private float[] localDip;
	private float[] ringAlpha;
	private int tierBuilt = -2;

	private float growFrom = 0.15F;
	private float growTo = 0.5F;
	private float growTicks = 240.0F;
	private float growAge;
	private float omega = 2.0F;
	private float omegaTarget = 2.0F;
	private double rotation;
	private float alpha = 1.0F;
	private float alphaTarget = 1.0F;
	private float alphaSpeed = 0.05F;
	private float baseDrop;
	private float baseDropTarget;
	private float eyeScale = 1.0F;
	private float eyeTarget = 1.0F;
	private float radiusMul = 1.0F;
	private float radiusMulTarget = 1.0F;
	private float radiusMulSpeed = 0.02F;
	private boolean sevenRim;
	private boolean arcs = true;
	private int arcEvery = 40;
	private int arcTimer;
	private final float[] arcAngle = new float[MAX_ARCS];
	private final float[] arcR0 = new float[MAX_ARCS];
	private final float[] arcR1 = new float[MAX_ARCS];
	private final int[] arcLife = new int[MAX_ARCS];
	private final int[] arcSeed = new int[MAX_ARCS];
	private final Rim rim;

	/**
	 * A vortex centred at {@code (cx, cz)} with its cloud base at {@code cy}; {@code radius} R_v, {@code eye} the eye
	 * radius, {@code cloud} the cloud RGB, {@code glow} the eye and arc RGB.
	 */
	public VortexRenderer(int delay, float life, double cx, double cy, double cz, float radius, float eye, int cloud, int glow, int seed) {
		super(delay, life);
		this.cx = cx;
		this.cy = cy;
		this.cz = cz;
		this.radius = Math.max(8.0F, radius);
		this.eye = Math.max(2.0F, eye);
		this.cloud = cloud & 0xFFFFFF;
		this.glowRgb = glow & 0xFFFFFF;
		this.random = RandomSource.create(seed);
		this.largeRank = 1;
		this.rim = new Rim(this);
		if (EpicBudget.tierOrMinimal() >= 2) {
			arcEvery = 8;
		}
	}

	@Override
	protected void start() {
		scene.add(rim);
	}

	@Override
	public boolean geometry() {
		return true;
	}

	// ================================================================ controls (any beat may call them)

	/** Radius fraction of R_v from {@code from} to {@code to} over {@code ticks}. */
	public VortexRenderer grow(float from, float to, float ticks) {
		float now = currentGrowth();
		growFrom = Float.isNaN(from) ? now : from;
		growTo = to;
		growTicks = Math.max(1.0F, ticks);
		growAge = 0.0F;
		return this;
	}

	/** Angular speed in degrees per second (eased). */
	public VortexRenderer spin(float degreesPerSecond) {
		omegaTarget = degreesPerSecond;
		return this;
	}

	public float spinTarget() {
		return omegaTarget;
	}

	/** The eye pulses or contracts to {@code scale} (eased). */
	public VortexRenderer eye(float scale) {
		eyeTarget = scale;
		return this;
	}

	/** Pulses the eye once (geometry only, not a screen flash). */
	public void pulse(float scale) {
		eyeScale = scale;
	}

	/** Lowers the cloud base by {@code fraction} of its height above the subject (eased). */
	public VortexRenderer sink(float blocks) {
		baseDropTarget = blocks;
		return this;
	}

	/** Dissolves outward: radius × 1.4 and alpha → 0 over {@code ticks}. */
	public void dissolve(float ticks) {
		radiusMulTarget = 1.4F;
		radiusMulSpeed = 0.4F / Math.max(1.0F, ticks);
		alphaTarget = 0.0F;
		alphaSpeed = 1.0F / Math.max(1.0F, ticks);
		arcs = false;
		extend(ticks + 2.0F);
		life = Math.min(life, age + ticks + 2.0F);
	}

	/** Collapses inward: radius → 0 in {@code ticks}, ω × 4. */
	public void collapse(float ticks) {
		radiusMulTarget = 0.0F;
		radiusMulSpeed = 1.0F / Math.max(1.0F, ticks);
		omegaTarget *= 4.0F;
		alphaTarget = 0.0F;
		alphaSpeed = 0.8F / Math.max(1.0F, ticks);
		arcs = false;
		life = Math.min(life, age + ticks + 2.0F);
	}

	/** Micro-arcs on or off (they stop in the hush). */
	public VortexRenderer arcs(boolean on) {
		arcs = on;
		return this;
	}

	/** Kenaikan Abadi: the eye rim shimmers in the seven strike colours. */
	public VortexRenderer sevenRim(boolean on) {
		sevenRim = on;
		return this;
	}

	/** Every micro-arc converges at once (the charge before a strike). */
	public void converge() {
		for (int i = 0; i < MAX_ARCS; i++) {
			spawnArc(i, 0.15F);
		}
	}

	/** The cloud base (world y) and the eye centre, for bolts. */
	public double baseY() {
		return cy - baseDrop;
	}

	public double centerX() {
		return cx;
	}

	public double centerZ() {
		return cz;
	}

	/** The current radius in blocks. */
	public float currentRadius() {
		return radius * currentGrowth() * radiusMul;
	}

	/** The current eye radius in blocks. */
	public float eyeRadius() {
		return eye * eyeScale;
	}

	private float currentGrowth() {
		float t = Math.min(1.0F, growAge / growTicks);
		float eased = t * t * (3.0F - 2.0F * t);
		return growFrom + (growTo - growFrom) * eased;
	}

	@Override
	protected void tick() {
		float sigma = EpicClock.sigma();
		growAge += sigma;
		omega += (omegaTarget - omega) * 0.05F * sigma;
		rotation += omega / 20.0F * sigma;
		alpha += Mth.clamp(alphaTarget - alpha, -alphaSpeed * sigma, alphaSpeed * sigma);
		radiusMul += Mth.clamp(radiusMulTarget - radiusMul, -radiusMulSpeed * sigma, radiusMulSpeed * sigma);
		baseDrop += (baseDropTarget - baseDrop) * 0.06F * sigma;
		eyeScale += (eyeTarget - eyeScale) * 0.15F * sigma;
		for (int i = 0; i < MAX_ARCS; i++) {
			if (arcLife[i] > 0) {
				arcLife[i]--;
			}
		}
		if (arcs && sigma > 0.0F && ++arcTimer >= arcEvery) {
			arcTimer = 0;
			for (int i = 0; i < MAX_ARCS; i++) {
				if (arcLife[i] <= 0) {
					spawnArc(i, 0.6F);
					break;
				}
			}
		}
	}

	private void spawnArc(int i, float reach) {
		float r = currentRadius();
		arcAngle[i] = random.nextFloat() * (float) (Math.PI * 2.0D);
		arcR0[i] = eyeRadius() * 1.2F + random.nextFloat() * r * 0.2F;
		arcR1[i] = arcR0[i] + 6.0F + random.nextFloat() * Math.max(6.0F, r * reach * 0.3F);
		arcLife[i] = 4;
		arcSeed[i] = random.nextInt();
	}

	private void build(int tier) {
		tierBuilt = tier;
		int index = Math.max(1, Math.min(2, tier));
		rings = RINGS[index];
		segments = SEGMENTS[index];
		int n = (rings + 1) * (segments + 1);
		localX = new float[n];
		localZ = new float[n];
		localDip = new float[rings + 1];
		ringAlpha = new float[rings + 1];
		for (int i = 0; i <= rings; i++) {
			float t = i / (float) rings;
			// Unit radius from the eye (≈ eye/R) to 1; the log-spiral turns the outer rings.
			float r = t * t * 0.92F + t * 0.08F;
			double spiral = 2.4D * Math.log(1.0D + r * 12.0D);
			localDip[i] = (1.0F - t) * (1.0F - t);
			ringAlpha[i] = i == 0 ? 0.95F : t > 0.75F ? Math.max(0.0F, (1.0F - t) / 0.25F) * 0.85F : 0.85F;
			for (int j = 0; j <= segments; j++) {
				double angle = Math.PI * 2.0D * j / segments + spiral;
				localX[i * (segments + 1) + j] = (float) Math.cos(angle) * r;
				localZ[i * (segments + 1) + j] = (float) Math.sin(angle) * r;
			}
		}
	}

	@Override
	public void render(EpicRenderer.Frame frame, float at) {
		int tier = EpicBudget.tier();
		if (tier <= 0 || alpha <= 0.01F) {
			return;
		}
		if (tier != tierBuilt) {
			build(tier);
		}
		float r = currentRadius();
		if (r < 1.0F) {
			return;
		}
		float trueReach = frame.trueReach;
		int quads = rings * segments / 2;
		if (!EpicBudget.takeQuads(quads)) {
			return;
		}
		drawShell(frame, at, r, 0, rings / 2 + rings % 2, false, trueReach);
		drawEye(frame, at, r);
	}

	/** Draws rings [from, to) of the mesh; {@code rimOnly} draws only vertices beyond the true reach. */
	void drawShell(EpicRenderer.Frame frame, float at, float r, int from, int to, boolean rimShell, float trueReach) {
		double angle = Math.toRadians(rotation + omega / 20.0F * frame.partial * EpicClock.sigma());
		float cos = (float) Math.cos(angle);
		float sin = (float) Math.sin(angle);
		float dip = Math.min(r * 0.12F, 14.0F);
		double base = baseY();
		float fadeIn = Math.min(1.0F, at / 30.0F);
		float a0 = alpha * fadeIn * fade(at, 0.0F, 20.0F);
		int stride = segments + 1;
		float[] p = frame.tmp;
		float uScale = 4.0F;
		double scroll = rotation * 0.002D;
		EpicRenderer.Sink sink = frame.swirl;
		for (int i = from; i < to && i < rings; i++) {
			for (int j = 0; j < segments; j++) {
				int v00 = i * stride + j;
				int v01 = v00 + 1;
				int v10 = v00 + stride;
				int v11 = v10 + 1;
				emit(frame, sink, v00, i, j, r, cos, sin, dip, base, a0, uScale, scroll, trueReach, p);
				emit(frame, sink, v10, i + 1, j, r, cos, sin, dip, base, a0, uScale, scroll, trueReach, p);
				emit(frame, sink, v11, i + 1, j + 1, r, cos, sin, dip, base, a0, uScale, scroll, trueReach, p);
				emit(frame, sink, v01, i, j + 1, r, cos, sin, dip, base, a0, uScale, scroll, trueReach, p);
			}
		}
	}

	private void emit(EpicRenderer.Frame frame, EpicRenderer.Sink sink, int v, int ring, int seg, float r, float cos, float sin, float dip,
					  double base, float a0, float uScale, double scroll, float trueReach, float[] p) {
		float lx = localX[v] * r;
		float lz = localZ[v] * r;
		float x = frame.rx(cx + lx * cos - lz * sin);
		float y = frame.ry(base - localDip[ring] * dip);
		float z = frame.rz(cz + lx * sin + lz * cos);
		float a = a0 * ringAlpha[ring];
		float horizontal = (float) Math.sqrt(x * x + z * z);
		if (horizontal > trueReach) {
			a *= SkyProjection.project(x, y, z, frame.drawRadius, p);
			x = p[0];
			y = p[1];
			z = p[2];
		}
		int shade = ring == 0 ? darken(cloud, 0.55F) : ring == 1 ? darken(cloud, 0.75F) : cloud;
		int color = ARGB.color(Math.round(Mth.clamp(a, 0.0F, 1.0F) * 255.0F), shade);
		float u = (float) (seg / (float) segments * uScale + scroll);
		float vv = ring / (float) rings;
		sink.vertex(x, y, z, u, vv, color, 0.0F, -1.0F, 0.0F);
	}

	private void drawEye(EpicRenderer.Frame frame, float at, float r) {
		float er = eyeRadius();
		double base = baseY();
		float x = frame.rx(cx);
		float y = frame.ry(base) - 0.5F;
		float z = frame.rz(cz);
		int rgb = glowRgb;
		if (sevenRim) {
			int index = (int) (frame.clock / 6.0D) % EpicColors.IMMORTAL_STRIKE.length;
			rgb = EpicColors.IMMORTAL_STRIKE[index];
		}
		int glowAlpha = Math.round(alpha * 200.0F * Math.min(1.0F, at / 20.0F));
		RingRenderer.annulus(frame, frame.ring, x, y, z, er * 0.15F, er, 24, ARGB.color(glowAlpha, rgb), ARGB.color(glowAlpha / 3, rgb), 0.0F);
		EpicBudget.takeQuads(24);
		// Micro-arcs: short jagged horizontal strips just under the cloud base.
		EpicRenderer.Sink bolts = frame.bolts();
		for (int i = 0; i < MAX_ARCS; i++) {
			if (arcLife[i] <= 0) {
				continue;
			}
			RandomSource arc = SHARED;
			arc.setSeed(arcSeed[i]);
			float px = 0.0F;
			float pz = 0.0F;
			float py = y - 0.3F;
			for (int k = 0; k <= 5; k++) {
				float t = k / 5.0F;
				float rr = arcR0[i] + (arcR1[i] - arcR0[i]) * t;
				float ang = arcAngle[i] + (arc.nextFloat() - 0.5F) * 0.12F + t * 0.2F;
				float nx = x + Mth.cos(ang) * rr;
				float nz = z + Mth.sin(ang) * rr;
				float ny = py - arc.nextFloat() * 1.5F;
				if (k > 0) {
					int color = ARGB.color(170, glowRgb);
					frame.segment(bolts, px, py, pz, nx, ny, nz, 0.35F, 0.25F, color, color, 0.0F, 1.0F);
				}
				px = nx;
				py = ny;
				pz = nz;
			}
			EpicBudget.takeQuads(5);
		}
	}

	private static int darken(int rgb, float factor) {
		return ARGB.color(0, Math.round(ARGB.red(rgb) * factor), Math.round(ARGB.green(rgb) * factor), Math.round(ARGB.blue(rgb) * factor)) & 0xFFFFFF;
	}

	/** The outer half of the mesh as its own large layer (rank 5), drawn when the director grants it. */
	public static final class Rim extends Element {
		private final VortexRenderer owner;

		Rim(VortexRenderer owner) {
			super(0, 1_000_000.0F);
			this.owner = owner;
			this.largeRank = 5;
		}

		@Override
		protected void tick() {
			if (owner.finished()) {
				stop();
			}
		}

		@Override
		public boolean geometry() {
			return true;
		}

		@Override
		public void render(EpicRenderer.Frame frame, float at) {
			if (!owner.alive() || !owner.granted() || owner.alpha <= 0.01F || owner.localX == null) {
				return;
			}
			int tier = EpicBudget.tier();
			if (tier <= 0 || tier != owner.tierBuilt) {
				return;
			}
			float r = owner.currentRadius();
			int half = owner.rings / 2 + owner.rings % 2;
			if (!EpicBudget.takeQuads((owner.rings - half) * owner.segments)) {
				return;
			}
			owner.drawShell(frame, owner.progressAge(frame.partial), r, half, owner.rings, true, frame.trueReach);
		}
	}
}
