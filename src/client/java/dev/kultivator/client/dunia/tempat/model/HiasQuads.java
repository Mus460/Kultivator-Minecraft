package dev.kultivator.client.dunia.tempat.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/**
 * A reusable quad list for one render type of the DESIGN 38 decorations ({@link HiasanDunia}): camera-relative
 * positions, uv, ARGB colour, packed light and a normal per vertex, written through {@code submitCustomGeometry} with
 * vanilla entity render types only (the {@code SeragamQuads} / {@code SigilRenderer} pattern), so Sodium and Iris see
 * ordinary entity geometry. Arrays grow when needed and are kept between frames (no garbage per frame). Cells are the
 * 16 px cells of {@code textures/dunia/tempat/hias.png} (8 × 8 grid).
 */
final class HiasQuads {

	private static final int STRIDE = 8;
	private static final float CELL = 1.0F / 8.0F;

	private final RenderType type;
	private float[] data = new float[STRIDE * 512];
	private int[] colors = new int[512];
	private int[] lights = new int[512];
	private int count;
	private final SubmitNodeCollector.CustomGeometryRenderer writer = this::write;

	// scratch vectors (render thread only)
	private final Vector3f a = new Vector3f();
	private final Vector3f b = new Vector3f();
	private final Vector3f c = new Vector3f();
	private final Vector3f d = new Vector3f();
	private final Vector3f n = new Vector3f();
	private final float[][] corners = new float[8][3];

	HiasQuads(RenderType type) {
		this.type = type;
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

	private void vertex(Vector3f p, float u, float v, int argb, int light, Vector3f normal) {
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
		data[o] = p.x;
		data[o + 1] = p.y;
		data[o + 2] = p.z;
		data[o + 3] = u;
		data[o + 4] = v;
		data[o + 5] = normal.x;
		data[o + 6] = normal.y;
		data[o + 7] = normal.z;
		colors[count] = argb;
		lights[count] = light;
		count++;
	}

	/** A quad of four corners with the whole cell (col, row) mapped from a (top-left) to c (bottom-right). */
	void quad(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, int col, int row, int argb, int light) {
		float u0 = col * CELL;
		float v0 = row * CELL;
		n.set(p1).sub(p0).cross(p3.x - p0.x, p3.y - p0.y, p3.z - p0.z);
		if (n.lengthSquared() > 1.0E-12F) {
			n.normalize();
		} else {
			n.set(0.0F, 1.0F, 0.0F);
		}
		vertex(p0, u0, v0, argb, light, n);
		vertex(p1, u0, v0 + CELL, argb, light, n);
		vertex(p2, u0 + CELL, v0 + CELL, argb, light, n);
		vertex(p3, u0 + CELL, v0, argb, light, n);
	}

	/**
	 * A cuboid centred at (x, y, z) (camera-relative blocks) with half sizes (hx, hy, hz), turned by {@code rot}, every
	 * face showing cell (col, row). No allocation: the corners live in a scratch array.
	 */
	void kotak(float x, float y, float z, float hx, float hy, float hz, Quaternionfc rot, int col, int row, int argb, int light) {
		for (int i = 0; i < 8; i++) {
			n.set((i & 1) == 0 ? -hx : hx, (i & 2) == 0 ? -hy : hy, (i & 4) == 0 ? -hz : hz);
			rot.transform(n);
			corners[i][0] = n.x + x;
			corners[i][1] = n.y + y;
			corners[i][2] = n.z + z;
		}
		float[][] k = corners;
		face(k, 2, 3, 1, 0, col, row, argb, light); // north (-z)
		face(k, 7, 6, 4, 5, col, row, argb, light); // south (+z)
		face(k, 6, 2, 0, 4, col, row, argb, light); // west (-x)
		face(k, 3, 7, 5, 1, col, row, argb, light); // east (+x)
		face(k, 6, 7, 3, 2, col, row, argb, light); // up
		face(k, 0, 1, 5, 4, col, row, argb, light); // down
	}

	/**
	 * A quad showing only part of cell (col, row): {@code (u0, v0)} to {@code (u1, v1)} in 0..1 of the cell, p0 top-left,
	 * p1 bottom-left, p2 bottom-right, p3 top-right (cloth strips, the slices of a waving banner).
	 */
	void quadSub(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, int col, int row, float u0, float v0, float u1, float v1,
			int argb, int light) {
		float cu = col * CELL;
		float cv = row * CELL;
		n.set(p1).sub(p0).cross(p3.x - p0.x, p3.y - p0.y, p3.z - p0.z);
		if (n.lengthSquared() > 1.0E-12F) {
			n.normalize();
		} else {
			n.set(0.0F, 1.0F, 0.0F);
		}
		vertex(p0, cu + u0 * CELL, cv + v0 * CELL, argb, light, n);
		vertex(p1, cu + u0 * CELL, cv + v1 * CELL, argb, light, n);
		vertex(p2, cu + u1 * CELL, cv + v1 * CELL, argb, light, n);
		vertex(p3, cu + u1 * CELL, cv + v0 * CELL, argb, light, n);
	}

	private void face(float[][] k, int i0, int i1, int i2, int i3, int col, int row, int argb, int light) {
		a.set(k[i0][0], k[i0][1], k[i0][2]);
		b.set(k[i1][0], k[i1][1], k[i1][2]);
		c.set(k[i2][0], k[i2][1], k[i2][2]);
		d.set(k[i3][0], k[i3][1], k[i3][2]);
		// top-left, bottom-left, bottom-right, top-right as seen from outside
		quad(a, d, c, b, col, row, argb, light);
	}

	/** A camera-facing square of half size {@code h} at (x, y, z) using the camera's right and up vectors. */
	void billboard(float x, float y, float z, float h, Vector3f right, Vector3f up, int col, int row, int argb, int light) {
		a.set(x - right.x * h + up.x * h, y - right.y * h + up.y * h, z - right.z * h + up.z * h);
		b.set(x - right.x * h - up.x * h, y - right.y * h - up.y * h, z - right.z * h - up.z * h);
		c.set(x + right.x * h - up.x * h, y + right.y * h - up.y * h, z + right.z * h - up.z * h);
		d.set(x + right.x * h + up.x * h, y + right.y * h + up.y * h, z + right.z * h + up.z * h);
		quad(a, b, c, d, col, row, argb, light);
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
