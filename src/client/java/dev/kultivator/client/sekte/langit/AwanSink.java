package dev.kultivator.client.sekte.langit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kultivator.Kultivator;
import dev.kultivator.client.epic.kosmik.KosmikTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;

/**
 * Camera-relative quads of one texture for one frame of the sky-sect visuals (DESIGN 37.6.1), submitted once through a
 * reused writer (the pattern of {@code client.dunia.langit.LangitSink} and the 7b {@code EpicRenderer.Sink}). Two kinds,
 * both vanilla render types created lazily on first submit through {@link KosmikTypes} (never in a static initialiser):
 * <ul>
 * <li>glow ({@code entityTranslucentEmissive}: translucent, full bright, no cull, no depth write) for clouds, falls,
 * stars, cranes, petals, lanterns;</li>
 * <li>add ({@code energySwirl} with additive blending; it writes depth, so the additive sinks are submitted last): the
 * dawn sunbeams and the lightning seen from above; their colour is premultiplied by alpha here.</li>
 * </ul>
 * The "heavy shader" look is layered quads only: no post chain, no core shader, so Sodium and Iris stay happy.
 */
final class AwanSink {

	static final Identifier CLOUD_SEA_256 = Kultivator.id("textures/misc/kosmik/surga/cloud_sea_256.png");
	static final Identifier CLOUD_SEA_512 = Kultivator.id("textures/misc/kosmik/surga/cloud_sea_512.png");
	static final Identifier CLOUD_PUFF = Kultivator.id("textures/misc/kosmik/surga/cloud_puff.png");
	static final Identifier SHAFT = Kultivator.id("textures/misc/kosmik/surga/shaft.png");
	static final Identifier PETAL = Kultivator.id("textures/misc/kosmik/surga/petal.png");
	static final Identifier WATERFALL = Kultivator.id("textures/misc/kosmik/surga/waterfall.png");
	static final Identifier GATE_GLOW = Kultivator.id("textures/misc/kosmik/surga/gate_glow.png");
	static final Identifier RING = Kultivator.id("textures/misc/epic/ring_soft.png");
	static final Identifier HALO = tex("halo");
	static final Identifier BANGAU = tex("bangau");
	static final Identifier SUNGAI = tex("sungai_bintang");
	static final Identifier LENTERA = tex("lentera");
	static final Identifier PILAR = tex("pilar");
	static final Identifier PUSARAN = tex("pusaran");
	static final Identifier KILAT = tex("kilat");
	static final Identifier JARING = tex("jaring");
	static final Identifier PEDANG = tex("pedang");

	private static Identifier tex(String name) {
		return Kultivator.id("textures/sekte/langit/" + name + ".png");
	}

	private static final int STRIDE = 8;

	private final Identifier texture;
	private final boolean additive;
	private float[] data = new float[STRIDE * 256];
	private int[] colors = new int[256];
	private int count;
	private final SubmitNodeCollector.CustomGeometryRenderer writer = this::write;

	AwanSink(Identifier texture, boolean additive) {
		this.texture = texture;
		this.additive = additive;
	}

	void reset() {
		count = 0;
	}

	int quads() {
		return count / 4;
	}

	boolean additive() {
		return additive;
	}

	void vertex(float x, float y, float z, float u, float v, int argb) {
		if (count >= colors.length) {
			int size = colors.length * 2;
			float[] more = new float[size * STRIDE];
			System.arraycopy(data, 0, more, 0, data.length);
			data = more;
			int[] moreColors = new int[size];
			System.arraycopy(colors, 0, moreColors, 0, colors.length);
			colors = moreColors;
		}
		int o = count * STRIDE;
		data[o] = x;
		data[o + 1] = y;
		data[o + 2] = z;
		data[o + 3] = u;
		data[o + 4] = v;
		data[o + 5] = 0.0F;
		data[o + 6] = 1.0F;
		data[o + 7] = 0.0F;
		colors[count] = additive ? premultiply(argb) : argb;
		count++;
	}

	/** A quad from four corners with per-corner UVs and colours. */
	void quad(float x0, float y0, float z0, float u0, float v0, int c0,
			  float x1, float y1, float z1, float u1, float v1, int c1,
			  float x2, float y2, float z2, float u2, float v2, int c2,
			  float x3, float y3, float z3, float u3, float v3, int c3) {
		vertex(x0, y0, z0, u0, v0, c0);
		vertex(x1, y1, z1, u1, v1, c1);
		vertex(x2, y2, z2, u2, v2, c2);
		vertex(x3, y3, z3, u3, v3, c3);
	}

	/**
	 * A camera-facing square centred on the camera-relative point (x, y, z), half size {@code h}, turned by
	 * {@code roll} radians, showing the UV rectangle u0..u1 × v0..v1.
	 */
	void billboard(float x, float y, float z, float h, float roll, float u0, float u1, float v0, float v1, int color) {
		LangitFrame f = LangitFrame.INSTANCE;
		float c = (float) Math.cos(roll) * h;
		float s = (float) Math.sin(roll) * h;
		// the rotated right and up axes of the view
		float rx = f.rightX * c + f.upX * s;
		float ry = f.rightY * c + f.upY * s;
		float rz = f.rightZ * c + f.upZ * s;
		float ux = -f.rightX * s + f.upX * c;
		float uy = -f.rightY * s + f.upY * c;
		float uz = -f.rightZ * s + f.upZ * c;
		vertex(x - rx - ux, y - ry - uy, z - rz - uz, u0, v1, color);
		vertex(x + rx - ux, y + ry - uy, z + rz - uz, u1, v1, color);
		vertex(x + rx + ux, y + ry + uy, z + rz + uz, u1, v0, color);
		vertex(x - rx + ux, y - ry + uy, z - rz + uz, u0, v0, color);
	}

	/** A flat horizontal square at camera-relative (x, y, z), half size h, turned by {@code yaw} radians. */
	void flat(float x, float y, float z, float h, float yaw, int color) {
		float c = (float) Math.cos(yaw) * h;
		float s = (float) Math.sin(yaw) * h;
		vertex(x - c + s, y, z - s - c, 0.0F, 0.0F, color);
		vertex(x + c + s, y, z + s - c, 1.0F, 0.0F, color);
		vertex(x + c - s, y, z + s + c, 1.0F, 1.0F, color);
		vertex(x - c - s, y, z - s + c, 0.0F, 1.0F, color);
	}

	/**
	 * A vertical ribbon from camera-relative a to b that always turns its face toward the camera (an axis-aligned
	 * billboard), half width w; u across 0..1, v from va (at a) to vb (at b); colours at a and at b.
	 */
	void beam(float ax, float ay, float az, float bx, float by, float bz, float w, float va, float vb, int ca, int cb) {
		float dx = bx - ax;
		float dy = by - ay;
		float dz = bz - az;
		// side = axis × (view direction to the beam's middle)
		float mx = (ax + bx) * 0.5F;
		float my = (ay + by) * 0.5F;
		float mz = (az + bz) * 0.5F;
		float sx = dy * mz - dz * my;
		float sy = dz * mx - dx * mz;
		float sz = dx * my - dy * mx;
		float l = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (l < 1.0E-5F) {
			LangitFrame f = LangitFrame.INSTANCE;
			sx = f.rightX;
			sy = f.rightY;
			sz = f.rightZ;
			l = 1.0F;
		}
		sx *= w / l;
		sy *= w / l;
		sz *= w / l;
		vertex(ax - sx, ay - sy, az - sz, 0.0F, va, ca);
		vertex(ax + sx, ay + sy, az + sz, 1.0F, va, ca);
		vertex(bx + sx, by + sy, bz + sz, 1.0F, vb, cb);
		vertex(bx - sx, by - sy, bz - sz, 0.0F, vb, cb);
	}

	void submit(SubmitNodeCollector collector, PoseStack stack) {
		if (count < 4) {
			return;
		}
		collector.submitCustomGeometry(stack, additive ? KosmikTypes.add(texture) : KosmikTypes.glow(texture), writer);
	}

	private void write(PoseStack.Pose pose, VertexConsumer buffer) {
		int vertices = count / 4 * 4;
		for (int i = 0; i < vertices; i++) {
			int o = i * STRIDE;
			buffer.addVertex(pose, data[o], data[o + 1], data[o + 2])
					.setColor(colors[i])
					.setUv(data[o + 3], data[o + 4])
					.setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(LightCoordsUtil.FULL_BRIGHT)
					.setNormal(pose, data[o + 5], data[o + 6], data[o + 7]);
		}
	}

	// ---------------------------------------------------------------- colour helpers

	/** ARGB of {@code rgb} with alpha {@code a} (0-1). */
	static int argb(float a, int rgb) {
		int alpha = Math.round(Math.max(0.0F, Math.min(1.0F, a)) * 255.0F);
		return alpha << 24 | rgb & 0xFFFFFF;
	}

	/** Linear blend of two RGB colours. */
	static int mix(int a, int b, float t) {
		t = Math.max(0.0F, Math.min(1.0F, t));
		int r = Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
		int g = Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return r << 16 | g << 8 | bl;
	}

	/** RGB scaled by {@code k} (0-1). */
	static int scale(int rgb, float k) {
		k = Math.max(0.0F, Math.min(1.0F, k));
		return Math.round((rgb >> 16 & 0xFF) * k) << 16 | Math.round((rgb >> 8 & 0xFF) * k) << 8 | Math.round((rgb & 0xFF) * k);
	}

	private static int premultiply(int argb) {
		float a = (argb >>> 24) / 255.0F;
		return 0xFF000000 | scale(argb & 0xFFFFFF, a);
	}
}
