package dev.kultivator.client.dunia.tempat.tema.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/**
 * One reusable quad list of the themed-town effects (DESIGN 38.T.9.4) for one vanilla render type: camera-relative
 * positions, uv, ARGB colour, packed light and a normal per vertex, written through {@code submitCustomGeometry} (the
 * {@code HiasQuads} / {@code SigilRenderer} pattern), so Sodium and Iris see ordinary entity geometry and nothing needs
 * a post chain. Arrays grow on demand and are kept between frames: no garbage per frame.
 * <p>
 * An additive list ({@code premul}, the {@code energySwirl} type of {@code KosmikTypes.add}) blends ONE, ONE, so every
 * colour written to it is multiplied by its alpha here; the other lists blend by alpha as usual.
 */
final class FxQuads {

	private static final int STRIDE = 8;

	private final RenderType type;
	private final boolean premul;
	private float[] data = new float[STRIDE * 256];
	private int[] colors = new int[256];
	private int[] lights = new int[256];
	private int count;
	private final SubmitNodeCollector.CustomGeometryRenderer writer = this::write;

	// scratch (render thread only)
	private final Vector3f a = new Vector3f();
	private final Vector3f b = new Vector3f();
	private final Vector3f c = new Vector3f();
	private final Vector3f d = new Vector3f();
	private final Vector3f n = new Vector3f();
	private final Vector3f s = new Vector3f();
	private final float[][] corners = new float[8][3];

	FxQuads(RenderType type, boolean premul) {
		this.type = type;
		this.premul = premul;
	}

	void reset() {
		count = 0;
	}

	int quads() {
		return count / 4;
	}

	void submit(OrderedSubmitNodeCollector collector, PoseStack poseStack) {
		if (count >= 4) {
			collector.submitCustomGeometry(poseStack, type, writer);
		}
	}

	private int warna(int argb) {
		if (!premul) {
			return argb;
		}
		int al = argb >>> 24;
		int r = (argb >> 16 & 0xFF) * al / 255;
		int g = (argb >> 8 & 0xFF) * al / 255;
		int bl = (argb & 0xFF) * al / 255;
		return 0xFF000000 | r << 16 | g << 8 | bl;
	}

	private void vertex(float x, float y, float z, float u, float v, int argb, int light, Vector3f normal) {
		if (count >= colors.length) {
			int size = colors.length * 2;
			float[] more = new float[size * STRIDE];
			System.arraycopy(data, 0, more, 0, data.length);
			data = more;
			int[] moreColors = new int[size];
			System.arraycopy(colors, 0, moreColors, 0, colors.length);
			colors = moreColors;
			int[] moreLights = new int[size];
			System.arraycopy(lights, 0, moreLights, 0, lights.length);
			lights = moreLights;
		}
		int o = count * STRIDE;
		data[o] = x;
		data[o + 1] = y;
		data[o + 2] = z;
		data[o + 3] = u;
		data[o + 4] = v;
		data[o + 5] = normal.x;
		data[o + 6] = normal.y;
		data[o + 7] = normal.z;
		colors[count] = argb;
		lights[count] = light;
		count++;
	}

	private void normalOf(Vector3f p0, Vector3f p1, Vector3f p3) {
		n.set(p1).sub(p0).cross(p3.x - p0.x, p3.y - p0.y, p3.z - p0.z);
		if (n.lengthSquared() > 1.0E-12F) {
			n.normalize();
		} else {
			n.set(0.0F, 1.0F, 0.0F);
		}
	}

	/**
	 * A quad: p0 top-left, p1 bottom-left, p2 bottom-right, p3 top-right, showing texture rectangle (u0, v0)-(u1, v1)
	 * (0..1 of the whole texture), one colour and light.
	 */
	void quad(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float u0, float v0, float u1, float v1, int argb, int light) {
		normalOf(p0, p1, p3);
		int col = warna(argb);
		vertex(p0.x, p0.y, p0.z, u0, v0, col, light, n);
		vertex(p1.x, p1.y, p1.z, u0, v1, col, light, n);
		vertex(p2.x, p2.y, p2.z, u1, v1, col, light, n);
		vertex(p3.x, p3.y, p3.z, u1, v0, col, light, n);
	}

	/** Like {@link #quad} with one colour per corner (soft fades across big sheets). */
	void quad4(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float u0, float v0, float u1, float v1,
			int c0, int c1, int c2, int c3, int light) {
		normalOf(p0, p1, p3);
		vertex(p0.x, p0.y, p0.z, u0, v0, warna(c0), light, n);
		vertex(p1.x, p1.y, p1.z, u0, v1, warna(c1), light, n);
		vertex(p2.x, p2.y, p2.z, u1, v1, warna(c2), light, n);
		vertex(p3.x, p3.y, p3.z, u1, v0, warna(c3), light, n);
	}

	/** A quad with free uv per corner (scrolling sheets): corners in the order of {@link #quad}. */
	void quadUv(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float u0, float v0, float u1, float v1, float u2, float v2,
			float u3, float v3, int c0, int c1, int c2, int c3, int light) {
		normalOf(p0, p1, p3);
		vertex(p0.x, p0.y, p0.z, u0, v0, warna(c0), light, n);
		vertex(p1.x, p1.y, p1.z, u1, v1, warna(c1), light, n);
		vertex(p2.x, p2.y, p2.z, u2, v2, warna(c2), light, n);
		vertex(p3.x, p3.y, p3.z, u3, v3, warna(c3), light, n);
	}

	/** A camera-facing rectangle of half sizes (hw, hh) at (x, y, z), spun by {@code roll} radians in the view plane. */
	void billboard(float x, float y, float z, float hw, float hh, float roll, Vector3f right, Vector3f up, float u0, float v0,
			float u1, float v1, int argb, int light) {
		float cs = (float) Math.cos(roll);
		float sn = (float) Math.sin(roll);
		// rotated basis
		float rx = right.x * cs + up.x * sn;
		float ry = right.y * cs + up.y * sn;
		float rz = right.z * cs + up.z * sn;
		float ux = up.x * cs - right.x * sn;
		float uy = up.y * cs - right.y * sn;
		float uz = up.z * cs - right.z * sn;
		a.set(x - rx * hw + ux * hh, y - ry * hw + uy * hh, z - rz * hw + uz * hh);
		b.set(x - rx * hw - ux * hh, y - ry * hw - uy * hh, z - rz * hw - uz * hh);
		c.set(x + rx * hw - ux * hh, y + ry * hw - uy * hh, z + rz * hw - uz * hh);
		d.set(x + rx * hw + ux * hh, y + ry * hw + uy * hh, z + rz * hw + uz * hh);
		quad(a, b, c, d, u0, v0, u1, v1, argb, light);
	}

	/**
	 * A ribbon from {@code p} to {@code q} (camera-relative) turned about its own axis to face the camera at the origin,
	 * {@code w0} wide at p and {@code w1} at q; v runs from v0 at p to v1 at q (beams, ropes, streaks).
	 */
	void pita(float px, float py, float pz, float qx, float qy, float qz, float w0, float w1, float u0, float v0, float u1,
			float v1, int c0, int c1, int light) {
		float ax = qx - px;
		float ay = qy - py;
		float az = qz - pz;
		// side = axis x (midpoint -> camera)
		float mx = -(px + qx) * 0.5F;
		float my = -(py + qy) * 0.5F;
		float mz = -(pz + qz) * 0.5F;
		s.set(ay * mz - az * my, az * mx - ax * mz, ax * my - ay * mx);
		if (s.lengthSquared() < 1.0E-10F) {
			s.set(ay, -ax, 0.0F);
			if (s.lengthSquared() < 1.0E-10F) {
				s.set(1.0F, 0.0F, 0.0F);
			}
		}
		s.normalize();
		float h0 = w0 * 0.5F;
		float h1 = w1 * 0.5F;
		a.set(px - s.x * h0, py - s.y * h0, pz - s.z * h0);
		b.set(qx - s.x * h1, qy - s.y * h1, qz - s.z * h1);
		c.set(qx + s.x * h1, qy + s.y * h1, qz + s.z * h1);
		d.set(px + s.x * h0, py + s.y * h0, pz + s.z * h0);
		quad4(a, b, c, d, u0, v0, u1, v1, c0, c1, c1, c0, light);
	}

	/**
	 * Like {@link #pita} but the texture runs along u from p to q and across v (pictures drawn lying down, such as the
	 * sand streak {@code gores}).
	 */
	void pitaU(float px, float py, float pz, float qx, float qy, float qz, float w0, float w1, float u0, float v0, float u1,
			float v1, int c0, int c1, int light) {
		float ax = qx - px;
		float ay = qy - py;
		float az = qz - pz;
		float mx = -(px + qx) * 0.5F;
		float my = -(py + qy) * 0.5F;
		float mz = -(pz + qz) * 0.5F;
		s.set(ay * mz - az * my, az * mx - ax * mz, ax * my - ay * mx);
		if (s.lengthSquared() < 1.0E-10F) {
			s.set(0.0F, 1.0F, 0.0F);
		}
		s.normalize();
		float h0 = w0 * 0.5F;
		float h1 = w1 * 0.5F;
		a.set(px - s.x * h0, py - s.y * h0, pz - s.z * h0);
		b.set(qx - s.x * h1, qy - s.y * h1, qz - s.z * h1);
		c.set(qx + s.x * h1, qy + s.y * h1, qz + s.z * h1);
		d.set(px + s.x * h0, py + s.y * h0, pz + s.z * h0);
		quadUv(a, b, c, d, u0, v0, u1, v0, u1, v1, u0, v1, c0, c1, c1, c0, light);
	}

	/** A flat horizontal square (half size h) at (x, y, z) turned by {@code yaw} radians: ripples, reflections, rings. */
	void datar(float x, float y, float z, float hx, float hz, float yaw, float u0, float v0, float u1, float v1, int argb, int light) {
		float cs = (float) Math.cos(yaw);
		float sn = (float) Math.sin(yaw);
		float ex = cs * hx;
		float ez = sn * hx;
		float fx = -sn * hz;
		float fz = cs * hz;
		a.set(x - ex - fx, y, z - ez - fz);
		b.set(x - ex + fx, y, z - ez + fz);
		c.set(x + ex + fx, y, z + ez + fz);
		d.set(x + ex - fx, y, z + ez - fz);
		quad(a, b, c, d, u0, v0, u1, v1, argb, light);
	}

	/** A vertical rectangle facing {@code yaw} (radians; 0 faces +z): wall murals and fixed boards. */
	void dinding(float x, float y, float z, float hw, float hh, float yaw, float u0, float v0, float u1, float v1, int argb, int light) {
		float rx = (float) Math.cos(yaw);
		float rz = (float) Math.sin(yaw);
		a.set(x - rx * hw, y + hh, z - rz * hw);
		b.set(x - rx * hw, y - hh, z - rz * hw);
		c.set(x + rx * hw, y - hh, z + rz * hw);
		d.set(x + rx * hw, y + hh, z + rz * hw);
		quad(a, b, c, d, u0, v0, u1, v1, argb, light);
	}

	/**
	 * A cuboid centred at (x, y, z) (camera-relative) with half sizes (hx, hy, hz) turned by {@code rot}; every face
	 * shows the texture rectangle {@code uv} = {u0, v0, u1, v1} of its face index (north, south, west, east, up, down),
	 * {@code uv} holding 6 × 4 floats. {@code skipDown} drops the bottom face (bodies sitting in a hull).
	 */
	void kotak(float x, float y, float z, float hx, float hy, float hz, Quaternionfc rot, float[] uv, int argb, int light, boolean skipDown) {
		for (int i = 0; i < 8; i++) {
			n.set((i & 1) == 0 ? -hx : hx, (i & 2) == 0 ? -hy : hy, (i & 4) == 0 ? -hz : hz);
			rot.transform(n);
			corners[i][0] = n.x + x;
			corners[i][1] = n.y + y;
			corners[i][2] = n.z + z;
		}
		face(2, 3, 1, 0, uv, 0, argb, light); // north (-z)
		face(7, 6, 4, 5, uv, 1, argb, light); // south (+z)
		face(6, 2, 0, 4, uv, 2, argb, light); // west (-x)
		face(3, 7, 5, 1, uv, 3, argb, light); // east (+x)
		face(6, 7, 3, 2, uv, 4, argb, light); // up
		if (!skipDown) {
			face(0, 1, 5, 4, uv, 5, argb, light); // down
		}
	}

	private void face(int i0, int i1, int i2, int i3, float[] uv, int f, int argb, int light) {
		float[][] k = corners;
		a.set(k[i0][0], k[i0][1], k[i0][2]);
		b.set(k[i1][0], k[i1][1], k[i1][2]);
		c.set(k[i2][0], k[i2][1], k[i2][2]);
		d.set(k[i3][0], k[i3][1], k[i3][2]);
		int o = f * 4;
		// top-left, bottom-left, bottom-right, top-right as seen from outside
		quad(a, d, c, b, uv[o], uv[o + 1], uv[o + 2], uv[o + 3], argb, light);
	}

	private void write(PoseStack.Pose pose, VertexConsumer buffer) {
		int total = count / 4 * 4;
		for (int i = 0; i < total; i++) {
			int o = i * STRIDE;
			buffer.addVertex(pose, data[o], data[o + 1], data[o + 2])
					.setColor(colors[i])
					.setUv(data[o + 3], data[o + 4])
					.setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(lights[i])
					.setNormal(pose, data[o + 5], data[o + 6], data[o + 7]);
		}
	}
}
