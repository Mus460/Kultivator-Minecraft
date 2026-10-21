package dev.kultivator.client.epic.kosmik;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.client.anim.model.ModelFx;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.render.EpicRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One frame of a bound stage (DESIGN 32.1.3, 32.1.4, contract C32.3). The stage writes stage-metre geometry through it;
 * every point is drawn camera-relative through the <b>logarithmic diorama</b>: a stage point P at distance ρ from the
 * virtual eye keeps its exact direction and is drawn at {@code g(ρ) = clamp(N + (F − N)·ln(ρ/ρ0)/ln(ρ1/ρ0), N, F)} from
 * the real camera ({@code N = 0.12 b, F = 0.90 R_d, ρ0 = 0.5 sm, ρ1 = 20 000 sm}), so the picture is the true perspective
 * picture and only depth is compressed (monotonically: the depth test still orders surfaces). Sky objects are drawn at
 * 0.94 R_d, the dome at 0.97 R_d (behind everything).
 * <p>
 * Sinks: one reused sink per render type and texture ({@link KosmikTypes}); the core submits them after the stage's
 * {@link KosmikStage#render}: solid (dome first) and cutout, the item models (already submitted by {@link #item}),
 * translucent glow, additive ({@code order(1)}). Normals are written +Y (constant entity diffuse); shading is baked into
 * the vertex colours by the stage. Additive colours are premultiplied by their alpha here (the blend is ONE, ONE).
 * Client thread only; no allocation per frame after the first frames (the scratch arrays grow once).
 */
public final class StageFrame {

	public static final float NEAR = 0.12F;
	public static final double RHO0 = 0.5D;
	public static final double RHO1 = 20_000.0D;
	public static final float FAR_SHARE = 0.90F;
	public static final float SKY_SHARE = 0.94F;
	public static final float DOME_SHARE = 0.97F;
	/** Item models per quality (C32.7). */
	public static final int[] ITEMS = {2, 8, 16};

	/** The virtual eye (stage metres). */
	public double ex;
	public double ey;
	public double ez;
	/** Camera axes (stage axes = world axes). */
	public final Vector3f right = new Vector3f();
	public final Vector3f up = new Vector3f();
	public final Vector3f forward = new Vector3f();
	/** Shot time (ticks, fractional; held by a hit-stop). */
	public double time;
	/** 0..1 of the shot. */
	public float shotT;
	public int quality;
	public boolean pack;
	/** R_d (blocks). */
	public float domeRadius;
	/** Vertical FOV (degrees) of this frame. */
	public float fovDeg = 70.0F;
	/** The window aspect (width / height). */
	public float aspect = 16.0F / 9.0F;
	/** The context of the running shot. */
	public @Nullable StageContext ctx;
	/** The beat of the running shot. */
	public String beat = "";
	/** The shot's length in ticks (after re-timing). */
	public int ticks = 1;
	/** Partial tick of this frame. */
	public float partial;

	private float far;
	private double lnRho0;
	private double invLnRange;
	private float cosCull;
	private int stamp;
	private boolean domeDrawn;
	private int itemsUsed;
	private int quadsWritten;
	private @Nullable EpicRenderer.Frame frame;

	private final Map<Identifier, Slot> solid = new HashMap<>();
	private final Map<Identifier, Slot> cutout = new HashMap<>();
	private final Map<Identifier, Slot> glow = new HashMap<>();
	private final Map<Identifier, Slot> add = new HashMap<>();
	private final List<Slot> usedSolid = new ArrayList<>();
	private final List<Slot> usedCutout = new ArrayList<>();
	private final List<Slot> usedGlow = new ArrayList<>();
	private final List<Slot> usedAdd = new ArrayList<>();
	private final java.util.Set<EpicRenderer.Sink> additive = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
	private @Nullable EpicRenderer.Sink lastSink;
	private boolean lastAdditive;

	private final float[] m0 = new float[3];
	private final float[] m1 = new float[3];
	private final float[] m2 = new float[3];
	private final float[] m3 = new float[3];
	private final float[] corners = new float[24];
	private final Vector3f vec = new Vector3f();
	private float[] ribbonA = new float[64 * 3];
	private float[] ribbonB = new float[64 * 3];
	private final ItemStackRenderState[] itemStates = new ItemStackRenderState[16];
	private final String[] itemIds = new String[16];

	private static final class Slot {
		final EpicRenderer.Sink sink;
		int stamp = -1;

		Slot(EpicRenderer.Sink sink) {
			this.sink = sink;
		}
	}

	// ================================================================ core (F)

	/** Starts a frame (called by {@link Kosmik3D} before the stage's render). */
	void begin(EpicRenderer.Frame epic, double eyeX, double eyeY, double eyeZ, float rd) {
		this.frame = epic;
		this.ex = eyeX;
		this.ey = eyeY;
		this.ez = eyeZ;
		this.right.set(epic.right);
		this.up.set(epic.up);
		this.forward.set(epic.forward);
		this.partial = epic.partial;
		this.domeRadius = Math.max(2.0F, rd);
		this.far = FAR_SHARE * domeRadius;
		this.lnRho0 = Math.log(RHO0);
		this.invLnRange = 1.0D / Math.log(RHO1 / RHO0);
		this.stamp++;
		this.domeDrawn = false;
		this.itemsUsed = 0;
		this.quadsWritten = 0;
		this.lastSink = null;
		usedSolid.clear();
		usedCutout.clear();
		usedGlow.clear();
		usedAdd.clear();
		// the dome's view cone: the diagonal half-FOV + 12°
		double halfV = Math.toRadians(Math.max(10.0F, Math.min(170.0F, fovDeg)) * 0.5D);
		double diag = Math.atan(Math.tan(halfV) * Math.sqrt(1.0D + aspect * aspect));
		this.cosCull = (float) Math.cos(Math.min(Math.PI, diag + Math.toRadians(12.0D)));
	}

	/** Submits every used sink (after the stage's render): solid and cutout, glow, additive last. */
	void flush() {
		EpicRenderer.Frame epic = frame;
		if (epic == null) {
			return;
		}
		for (int i = 0; i < usedSolid.size(); i++) {
			usedSolid.get(i).sink.submit(epic.collector);
		}
		for (int i = 0; i < usedCutout.size(); i++) {
			usedCutout.get(i).sink.submit(epic.collector);
		}
		for (int i = 0; i < usedGlow.size(); i++) {
			usedGlow.get(i).sink.submit(epic.collector);
		}
		for (int i = 0; i < usedAdd.size(); i++) {
			usedAdd.get(i).sink.submit(epic.collector.order(1));
		}
	}

	/** Drops this frame's vertices (a failed render). */
	void discard() {
		for (List<Slot> list : List.of(usedSolid, usedCutout, usedGlow, usedAdd)) {
			for (int i = 0; i < list.size(); i++) {
				list.get(i).sink.reset();
			}
			list.clear();
		}
	}

	boolean domeDrawn() {
		return domeDrawn;
	}

	/** Quads written this frame (for {@code /sinematik kosmik3d info}). */
	public int quadsWritten() {
		return quadsWritten;
	}

	// ================================================================ the diorama

	/** The drawn distance of a stage distance {@code rho} (blocks). */
	public float g(double rho) {
		if (!(rho > 1.0E-9D)) {
			return NEAR;
		}
		double g = NEAR + (far - NEAR) * (Math.log(rho) - lnRho0) * invLnRange;
		return (float) Math.max(NEAR, Math.min(far, g));
	}

	/** Stage point → drawn camera-relative position (32.1.3). */
	public void map(double x, double y, double z, float[] out) {
		double dx = x - ex;
		double dy = y - ey;
		double dz = z - ez;
		double rho = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (!(rho > 1.0E-9D)) {
			out[0] = 0.0F;
			out[1] = 0.0F;
			out[2] = NEAR;
			return;
		}
		double k = g(rho) / rho;
		out[0] = (float) (dx * k);
		out[1] = (float) (dy * k);
		out[2] = (float) (dz * k);
	}

	/**
	 * The uniform scale {@code k = g(ρc)/ρc} of a compact cluster (an item model and its own decorations) centred at stage
	 * point C; {@code centreOut} receives C's drawn position. Draw the cluster's parts at {@code centreOut + offset × k}.
	 */
	public float cluster(double cx, double cy, double cz, float[] centreOut) {
		double dx = cx - ex;
		double dy = cy - ey;
		double dz = cz - ez;
		double rho = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (!(rho > 1.0E-6D)) {
			centreOut[0] = 0.0F;
			centreOut[1] = 0.0F;
			centreOut[2] = 0.0F;
			return NEAR;
		}
		float k = (float) (g(rho) / rho);
		centreOut[0] = (float) (dx * k);
		centreOut[1] = (float) (dy * k);
		centreOut[2] = (float) (dz * k);
		return k;
	}

	/** The stage distance from the virtual eye to a point. */
	public double distance(double x, double y, double z) {
		double dx = x - ex;
		double dy = y - ey;
		double dz = z - ez;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/**
	 * True when a sphere of stage radius {@code radius} at P may be on screen (a cheap cone test with the dome's margin);
	 * use it to skip props and patches out of view.
	 */
	public boolean inView(double x, double y, double z, double radius) {
		double dx = x - ex;
		double dy = y - ey;
		double dz = z - ez;
		double rho = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (rho <= radius) {
			return true;
		}
		double cos = (dx * forward.x + dy * forward.y + dz * forward.z) / rho;
		double spread = Math.asin(Math.min(1.0D, radius / rho));
		return Math.acos(Math.max(-1.0D, Math.min(1.0D, cos))) - spread <= Math.acos(cosCull);
	}

	// ================================================================ sinks

	/** {@code entitySolid}: opaque, back faces culled, depth write. */
	public EpicRenderer.Sink solid(Identifier tex) {
		return use(solid, usedSolid, tex, 0);
	}

	/** {@code entityCutout}: alpha cutout, no cull. */
	public EpicRenderer.Sink cutout(Identifier tex) {
		return use(cutout, usedCutout, tex, 1);
	}

	/** {@code entityTranslucentEmissive}: translucent, no cull, no depth write. */
	public EpicRenderer.Sink glow(Identifier tex) {
		return use(glow, usedGlow, tex, 2);
	}

	/** {@code energySwirl(tex, 0, 0)}: additive (premultiplied here), no cull, writes depth; submitted with order(1). */
	public EpicRenderer.Sink add(Identifier tex) {
		return use(add, usedAdd, tex, 3);
	}

	private EpicRenderer.Sink use(Map<Identifier, Slot> map, List<Slot> used, Identifier tex, int kind) {
		Slot slot = map.get(tex);
		if (slot == null) {
			EpicRenderer.Sink sink = switch (kind) {
				case 0 -> new EpicRenderer.Sink(KosmikTypes.solid(tex), true);
				case 1 -> new EpicRenderer.Sink(KosmikTypes.cutout(tex), true);
				case 2 -> new EpicRenderer.Sink(KosmikTypes.glow(tex), true);
				default -> new EpicRenderer.Sink(KosmikTypes.add(tex), true);
			};
			if (kind == 3) {
				additive.add(sink);
			}
			slot = new Slot(sink);
			map.put(tex, slot);
		}
		if (slot.stamp != stamp) {
			slot.stamp = stamp;
			slot.sink.reset();
			used.add(slot);
		}
		return slot.sink;
	}

	// ================================================================ primitives

	/** One vertex at stage point P (mapped), normal +Y. Write four per quad. */
	public void vertex(EpicRenderer.Sink s, double x, double y, double z, float u, float v, int argb) {
		map(x, y, z, m0);
		raw(s, m0[0], m0[1], m0[2], u, v, argb);
	}

	/** One vertex at an already drawn camera-relative position. */
	public void raw(EpicRenderer.Sink s, float x, float y, float z, float u, float v, int argb) {
		if (s != lastSink) {
			lastSink = s;
			lastAdditive = additive.contains(s);
		}
		s.vertex(x, y, z, u, v, lastAdditive ? premultiply(argb) : argb, 0.0F, 1.0F, 0.0F);
		if ((s.vertices() & 3) == 0) {
			quadsWritten++;
		}
	}

	/** ARGB with the RGB scaled by the alpha (the additive blend ignores alpha). */
	public static int premultiply(int argb) {
		int a = argb >>> 24;
		if (a == 255) {
			return argb;
		}
		int r = (argb >> 16 & 0xFF) * a / 255;
		int g = (argb >> 8 & 0xFF) * a / 255;
		int b = (argb & 0xFF) * a / 255;
		return a << 24 | r << 16 | g << 8 | b;
	}

	/** A camera-facing square of stage half size {@code half} at P, turned by {@code rollRad} (corners mapped). */
	public void billboard(EpicRenderer.Sink s, double x, double y, double z, double half, float rollRad, int argb) {
		double c = Math.cos(rollRad);
		double sn = Math.sin(rollRad);
		double rx = (right.x * c + up.x * sn) * half;
		double ry = (right.y * c + up.y * sn) * half;
		double rz = (right.z * c + up.z * sn) * half;
		double ux = (-right.x * sn + up.x * c) * half;
		double uy = (-right.y * sn + up.y * c) * half;
		double uz = (-right.z * sn + up.z * c) * half;
		map(x - rx - ux, y - ry - uy, z - rz - uz, m0);
		map(x + rx - ux, y + ry - uy, z + rz - uz, m1);
		map(x + rx + ux, y + ry + uy, z + rz + uz, m2);
		map(x - rx + ux, y - ry + uy, z - rz + uz, m3);
		raw(s, m0[0], m0[1], m0[2], 0.0F, 1.0F, argb);
		raw(s, m1[0], m1[1], m1[2], 1.0F, 1.0F, argb);
		raw(s, m2[0], m2[1], m2[2], 1.0F, 0.0F, argb);
		raw(s, m3[0], m3[1], m3[2], 0.0F, 0.0F, argb);
	}

	/**
	 * A sky object in direction ({@code dx, dy, dz}) (any length), {@code angularHalf} radians across, at 0.94 R_d: behind
	 * every mapped body (a planet in front hides it through the depth test), in front of the dome.
	 */
	public void skyBillboard(EpicRenderer.Sink s, float dx, float dy, float dz, float angularHalf, float rollRad, int argb) {
		float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (!(len > 1.0E-6F)) {
			return;
		}
		float r = SKY_SHARE * domeRadius;
		float cx = dx / len * r;
		float cy = dy / len * r;
		float cz = dz / len * r;
		float half = (float) (r * Math.tan(Math.max(0.0F, Math.min(1.2F, angularHalf))));
		float c = (float) Math.cos(rollRad);
		float sn = (float) Math.sin(rollRad);
		float rx = (right.x * c + up.x * sn) * half;
		float ry = (right.y * c + up.y * sn) * half;
		float rz = (right.z * c + up.z * sn) * half;
		float ux = (-right.x * sn + up.x * c) * half;
		float uy = (-right.y * sn + up.y * c) * half;
		float uz = (-right.z * sn + up.z * c) * half;
		raw(s, cx - rx - ux, cy - ry - uy, cz - rz - uz, 0.0F, 1.0F, argb);
		raw(s, cx + rx - ux, cy + ry - uy, cz + rz - uz, 1.0F, 1.0F, argb);
		raw(s, cx + rx + ux, cy + ry + uy, cz + rz + uz, 1.0F, 0.0F, argb);
		raw(s, cx - rx + ux, cy - ry + uy, cz - rz + uz, 0.0F, 0.0F, argb);
	}

	/**
	 * A camera-facing strip through {@code n} stage points ({@code xyz}: 3 doubles each), width {@code w0} → {@code w1}
	 * (stage metres), colour {@code c0} → {@code c1}, u from {@code uOffset} to {@code uOffset + 1} along it, v 0..1 across.
	 * Returns the quads written ({@code n − 1}).
	 */
	public int ribbon(EpicRenderer.Sink s, double[] xyz, int n, double w0, double w1, int c0, int c1, float uOffset) {
		if (n < 2 || xyz.length < n * 3) {
			return 0;
		}
		if (ribbonA.length < n * 3) {
			ribbonA = new float[n * 3 + 48];
			ribbonB = new float[n * 3 + 48];
		}
		for (int i = 0; i < n; i++) {
			int a = Math.max(0, i - 1);
			int b = Math.min(n - 1, i + 1);
			double tx = xyz[b * 3] - xyz[a * 3];
			double ty = xyz[b * 3 + 1] - xyz[a * 3 + 1];
			double tz = xyz[b * 3 + 2] - xyz[a * 3 + 2];
			double px = xyz[i * 3];
			double py = xyz[i * 3 + 1];
			double pz = xyz[i * 3 + 2];
			double vx = px - ex;
			double vy = py - ey;
			double vz = pz - ez;
			// side = tangent × view
			double sx = ty * vz - tz * vy;
			double sy = tz * vx - tx * vz;
			double sz = tx * vy - ty * vx;
			double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
			if (sl < 1.0E-9D) {
				sx = right.x;
				sy = right.y;
				sz = right.z;
			} else {
				sx /= sl;
				sy /= sl;
				sz /= sl;
			}
			double w = (w0 + (w1 - w0) * (i / (double) (n - 1))) * 0.5D;
			map(px - sx * w, py - sy * w, pz - sz * w, m0);
			map(px + sx * w, py + sy * w, pz + sz * w, m1);
			System.arraycopy(m0, 0, ribbonA, i * 3, 3);
			System.arraycopy(m1, 0, ribbonB, i * 3, 3);
		}
		for (int i = 0; i < n - 1; i++) {
			float t0 = i / (float) (n - 1);
			float t1 = (i + 1) / (float) (n - 1);
			int ca = lerpColor(c0, c1, t0);
			int cb = lerpColor(c0, c1, t1);
			float u0 = uOffset + t0;
			float u1 = uOffset + t1;
			raw(s, ribbonA[i * 3], ribbonA[i * 3 + 1], ribbonA[i * 3 + 2], u0, 0.0F, ca);
			raw(s, ribbonA[(i + 1) * 3], ribbonA[(i + 1) * 3 + 1], ribbonA[(i + 1) * 3 + 2], u1, 0.0F, cb);
			raw(s, ribbonB[(i + 1) * 3], ribbonB[(i + 1) * 3 + 1], ribbonB[(i + 1) * 3 + 2], u1, 1.0F, cb);
			raw(s, ribbonB[i * 3], ribbonB[i * 3 + 1], ribbonB[i * 3 + 2], u0, 1.0F, ca);
		}
		return n - 1;
	}

	/**
	 * The backdrop (first): an inward sphere at 0.97 R_d around the real camera in the solid phase, turned by
	 * {@code uTurn} of a full turn about +Y, CPU view-culled. The grid follows the quality (6 × 12, 10 × 20, 12 × 24).
	 */
	public void dome(Identifier tex, float uTurn) {
		dome(tex, uTurn, 0xFFFFFFFF);
	}

	/** {@link #dome(Identifier, float)} tinted by {@code argb}. */
	public void dome(Identifier tex, float uTurn, int argb) {
		domeDrawn = true;
		KosmikMesh.Sphere m = quality <= 0 ? KosmikMesh.sphere(6, 12) : quality == 1 ? KosmikMesh.sphere(10, 20) : KosmikMesh.sphere(12, 24);
		EpicRenderer.Sink s = solid(tex);
		float r = DOME_SHARE * domeRadius;
		double turn = uTurn * Math.PI * 2.0D;
		float c = (float) Math.cos(turn);
		float sn = (float) Math.sin(turn);
		int verts = m.vertices();
		for (int k = 0; k < verts; k++) {
			float x = m.pos[k * 3];
			float y = m.pos[k * 3 + 1];
			float z = m.pos[k * 3 + 2];
			float rx = x * c + z * sn;
			float rz = -x * sn + z * c;
			m.drawn[k * 3] = rx * r;
			m.drawn[k * 3 + 1] = y * r;
			m.drawn[k * 3 + 2] = rz * r;
			m.normal[k * 3] = rx;
			m.normal[k * 3 + 1] = y;
			m.normal[k * 3 + 2] = rz;
		}
		int quads = 0;
		for (int i = 0; i < m.rings; i++) {
			for (int j = 0; j < m.segments; j++) {
				int a = m.index(i, j);
				int b = m.index(i, j + 1);
				int cc = m.index(i + 1, j + 1);
				int d = m.index(i + 1, j);
				// the quad's centre direction against the view cone
				float nx = m.normal[a * 3] + m.normal[b * 3] + m.normal[cc * 3] + m.normal[d * 3];
				float ny = m.normal[a * 3 + 1] + m.normal[b * 3 + 1] + m.normal[cc * 3 + 1] + m.normal[d * 3 + 1];
				float nz = m.normal[a * 3 + 2] + m.normal[b * 3 + 2] + m.normal[cc * 3 + 2] + m.normal[d * 3 + 2];
				float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
				if (nl > 1.0E-6F && (nx * forward.x + ny * forward.y + nz * forward.z) / nl < cosCull - quadSpread(m)) {
					continue;
				}
				// seen from inside: a, b, c, d faces the centre
				domeVertex(s, m, a, argb);
				domeVertex(s, m, b, argb);
				domeVertex(s, m, cc, argb);
				domeVertex(s, m, d, argb);
				quads++;
			}
		}
		// the dome is the cover of the world: it is always drawn (first in the priority order), its quads still count
		EpicBudget.takeQuads(quads);
	}

	/** The cosine slack of a dome quad's half extent (so a big quad at the cone's edge is kept). */
	private static float quadSpread(KosmikMesh.Sphere m) {
		return (float) (1.0D - Math.cos(Math.PI / Math.max(2, m.rings)));
	}

	private void domeVertex(EpicRenderer.Sink s, KosmikMesh.Sphere m, int k, int argb) {
		raw(s, m.drawn[k * 3], m.drawn[k * 3 + 1], m.drawn[k * 3 + 2], m.uv[k * 2], m.uv[k * 2 + 1], argb);
	}

	/**
	 * A sphere body of stage radius {@code r} centred at C, turned by {@code spinRad} about +Y, coloured per vertex by
	 * {@code shade} (called with the turned unit normal and the original UV). {@code inside}: seen from inside (a shell
	 * around the eye). Quads whose four corners face away from the eye are dropped on the CPU, and so are quads whose four
	 * colours have alpha ≤ 5. Returns the quads written. Ask {@link #take} for the layer first.
	 */
	public int sphere(EpicRenderer.Sink s, KosmikMesh.Sphere m, double cx, double cy, double cz, double r, float spinRad, boolean inside,
					  KosmikMesh.Shade shade) {
		float c = (float) Math.cos(spinRad);
		float sn = (float) Math.sin(spinRad);
		int verts = m.vertices();
		int mark = stamp * 31 + m.rings * 7 + m.segments + (int) Math.round(r) * 131;
		for (int k = 0; k < verts; k++) {
			float x = m.pos[k * 3];
			float y = m.pos[k * 3 + 1];
			float z = m.pos[k * 3 + 2];
			float nx = x * c + z * sn;
			float nz = -x * sn + z * c;
			m.normal[k * 3] = nx;
			m.normal[k * 3 + 1] = y;
			m.normal[k * 3 + 2] = nz;
			double px = cx + nx * r;
			double py = cy + y * r;
			double pz = cz + nz * r;
			double dot = nx * (ex - px) + y * (ey - py) + nz * (ez - pz);
			m.facing[k] = inside ? dot < 0.0D : dot > 0.0D;
			map(px, py, pz, m0);
			m.drawn[k * 3] = m0[0];
			m.drawn[k * 3 + 1] = m0[1];
			m.drawn[k * 3 + 2] = m0[2];
			m.colorStamp[k] = mark - 1;
		}
		int quads = 0;
		for (int i = 0; i < m.rings; i++) {
			for (int j = 0; j < m.segments; j++) {
				int a = m.index(i, j);
				int b = m.index(i, j + 1);
				int cc = m.index(i + 1, j + 1);
				int d = m.index(i + 1, j);
				if (!m.facing[a] && !m.facing[b] && !m.facing[cc] && !m.facing[d]) {
					continue;
				}
				int ca = shadeOf(m, a, shade, mark);
				int cb = shadeOf(m, b, shade, mark);
				int ccol = shadeOf(m, cc, shade, mark);
				int cd = shadeOf(m, d, shade, mark);
				if ((ca >>> 24) <= 5 && (cb >>> 24) <= 5 && (ccol >>> 24) <= 5 && (cd >>> 24) <= 5) {
					continue;
				}
				if (inside) {
					sphereVertex(s, m, a, ca);
					sphereVertex(s, m, b, cb);
					sphereVertex(s, m, cc, ccol);
					sphereVertex(s, m, d, cd);
				} else {
					// outward: a, d, c, b is counter-clockwise seen from outside
					sphereVertex(s, m, a, ca);
					sphereVertex(s, m, d, cd);
					sphereVertex(s, m, cc, ccol);
					sphereVertex(s, m, b, cb);
				}
				quads++;
			}
		}
		return quads;
	}

	private static int shadeOf(KosmikMesh.Sphere m, int k, KosmikMesh.Shade shade, int mark) {
		if (m.colorStamp[k] != mark) {
			m.colorStamp[k] = mark;
			m.color[k] = shade.argb(m.normal[k * 3], m.normal[k * 3 + 1], m.normal[k * 3 + 2], m.uv[k * 2], m.uv[k * 2 + 1]);
		}
		return m.color[k];
	}

	private void sphereVertex(EpicRenderer.Sink s, KosmikMesh.Sphere m, int k, int argb) {
		raw(s, m.drawn[k * 3], m.drawn[k * 3 + 1], m.drawn[k * 3 + 2], m.uv[k * 2], m.uv[k * 2 + 1], argb);
	}

	/**
	 * A cuboid stage model (C32.4): every part ({@code part == null}) or one part, placed with its local origin at stage
	 * point ({@code x, y, z}); a local point p is drawn at {@code (x, y, z) + scale · (pivot + rot·(p − pivot))}, so
	 * {@code rot} turns the part about its pivot (a door on its hinge; an identity for a fixed part). Colour = atlas ×
	 * the cuboid's tint × {@code tint} (ARGB; alpha 0 means opaque) × the baked face shade × {@code light}. Faces of
	 * {@code glow} cuboids go to the additive sink (no shade, no light). Cuboids above the frame's quality (their
	 * {@code lod}) are skipped. Returns the quads written; ask {@link #take} first ({@link KosmikMesh.Model#quads}).
	 */
	public int model(KosmikMesh.Model m, @Nullable String part, double x, double y, double z, @Nullable Quaternionf rot, double scale, int tint,
					 float light) {
		if (m == null || m.parts.isEmpty()) {
			return 0;
		}
		int alpha = tint >>> 24;
		if (alpha == 0) {
			alpha = 255;
		}
		float tr = (tint >> 16 & 0xFF) / 255.0F;
		float tg = (tint >> 8 & 0xFF) / 255.0F;
		float tb = (tint & 0xFF) / 255.0F;
		float lit = Math.max(0.0F, Math.min(1.5F, light));
		EpicRenderer.Sink body = null;
		EpicRenderer.Sink shine = null;
		int quads = 0;
		for (int pi = 0; pi < m.parts.size(); pi++) {
			KosmikMesh.Part p = m.parts.get(pi);
			if (part != null && !part.equals(p.name)) {
				continue;
			}
			for (int ci = 0; ci < p.cuboids.size(); ci++) {
				KosmikMesh.Cuboid cub = p.cuboids.get(ci);
				if (cub.lod > quality) {
					continue;
				}
				for (int k = 0; k < 8; k++) {
					float lx = cub.corners[k * 3] - p.px;
					float ly = cub.corners[k * 3 + 1] - p.py;
					float lz = cub.corners[k * 3 + 2] - p.pz;
					if (rot != null) {
						rot.transform(lx, ly, lz, vec);
						lx = vec.x;
						ly = vec.y;
						lz = vec.z;
					}
					map(x + (p.px + lx) * scale, y + (p.py + ly) * scale, z + (p.pz + lz) * scale, m0);
					corners[k * 3] = m0[0];
					corners[k * 3 + 1] = m0[1];
					corners[k * 3 + 2] = m0[2];
				}
				float cr = (cub.tint >> 16 & 0xFF) / 255.0F * tr;
				float cg = (cub.tint >> 8 & 0xFF) / 255.0F * tg;
				float cbl = (cub.tint & 0xFF) / 255.0F * tb;
				EpicRenderer.Sink s;
				if (cub.glow) {
					if (shine == null) {
						shine = add(m.atlas);
					}
					s = shine;
				} else {
					if (body == null) {
						body = cutout(m.atlas);
					}
					s = body;
				}
				for (KosmikMesh.Face f : cub.faces) {
					float k = cub.glow ? 1.0F : f.shade * lit;
					int argb = alpha << 24 | channel(cr * k) << 16 | channel(cg * k) << 8 | channel(cbl * k);
					corner(s, f.c0, f.u0, f.v0, argb);
					corner(s, f.c1, f.u1, f.v0, argb);
					corner(s, f.c2, f.u1, f.v1, argb);
					corner(s, f.c3, f.u0, f.v1, argb);
					quads++;
				}
			}
		}
		return quads;
	}

	private void corner(EpicRenderer.Sink s, int c, float u, float v, int argb) {
		raw(s, corners[c * 3], corners[c * 3 + 1], corners[c * 3 + 2], u, v, argb);
	}

	private static int channel(float v) {
		return Math.max(0, Math.min(255, Math.round(v * 255.0F)));
	}

	/**
	 * An item model ({@code ModelFx} id, e.g. {@code heaven_splitting_sword}) centred at stage point C, turned by
	 * {@code rot}, {@code scale} stage metres per model unit, drawn with the uniform cluster scale of C (32.1.3; keep the
	 * eye ≥ 1.6 model radii away). Full bright. At most 2 / 8 / 16 per frame by quality; the stack is made on first draw.
	 */
	public void item(String modelId, double x, double y, double z, @Nullable Quaternionf rot, double scale) {
		EpicRenderer.Frame epic = frame;
		if (epic == null || modelId == null || itemsUsed >= ITEMS[Math.max(0, Math.min(2, quality))]) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		int slot = itemsUsed++;
		ItemStackRenderState state = itemStates[slot];
		if (state == null) {
			state = new ItemStackRenderState();
			itemStates[slot] = state;
			itemIds[slot] = null;
		}
		if (!modelId.equals(itemIds[slot])) {
			// made on first draw (never in a constructor or a static initialiser)
			minecraft.getItemModelResolver().updateForTopItem(state, ModelFx.stack(modelId), ItemDisplayContext.FIXED, minecraft.level, null, 0);
			itemIds[slot] = modelId;
		}
		float k = cluster(x, y, z, m0);
		PoseStack pose = epic.poseStack;
		pose.pushPose();
		pose.translate(m0[0], m0[1], m0[2]);
		if (rot != null) {
			pose.rotate(rot);
		}
		float size = (float) (k * scale);
		pose.scale(size, size, size);
		state.submit(pose, epic.collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
		pose.popPose();
	}

	/** Forgets the cached item states (resource reload, disconnect). */
	void releaseItems() {
		for (int i = 0; i < itemIds.length; i++) {
			itemIds[i] = null;
			itemStates[i] = null;
		}
	}

	/** {@code EpicBudget.takeQuads}: false = skip the whole layer this frame. */
	public boolean take(int quads) {
		return EpicBudget.takeQuads(Math.max(0, quads));
	}

	/** {@code EpicBudget.takeLarge}: one large additive layer slot. */
	public boolean large() {
		return EpicBudget.takeLarge();
	}

	/** Linear ARGB blend. */
	public static int lerpColor(int a, int b, float t) {
		if (t <= 0.0F) {
			return a;
		}
		if (t >= 1.0F) {
			return b;
		}
		int aa = a >>> 24;
		int ar = a >> 16 & 0xFF;
		int ag = a >> 8 & 0xFF;
		int ab = a & 0xFF;
		int ba = b >>> 24;
		int br = b >> 16 & 0xFF;
		int bg = b >> 8 & 0xFF;
		int bb = b & 0xFF;
		return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16 | Math.round(ag + (bg - ag) * t) << 8
				| Math.round(ab + (bb - ab) * t);
	}

	/** ARGB from RGB and a 0..1 alpha. */
	public static int argb(float alpha, int rgb) {
		return Math.max(0, Math.min(255, Math.round(alpha * 255.0F))) << 24 | (rgb & 0xFFFFFF);
	}

	static void logOnce(String message) {
		Kultivator.LOGGER.warn("Kultivator: Film kosmik 3D: {}", message);
	}
}
