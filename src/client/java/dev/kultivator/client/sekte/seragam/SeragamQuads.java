package dev.kultivator.client.sekte.seragam;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * A reusable vertex list for one render type of module S's world geometry (banner cloths, fittings, decals): positions
 * camera-relative, uv, ARGB colour, packed light and a normal per vertex, written as quads through
 * {@code submitCustomGeometry} (the {@code SigilRenderer} pattern, vanilla entity render types only, so Sodium and Iris
 * see ordinary entity geometry). Grows when needed and keeps its arrays between frames (no garbage per frame).
 */
final class SeragamQuads {

	private static final int STRIDE = 8;
	private RenderType type;
	private float[] data = new float[STRIDE * 256];
	private int[] colors = new int[256];
	private int[] lights = new int[256];
	private int count;
	private final SubmitNodeCollector.CustomGeometryRenderer writer = this::write;

	SeragamQuads(RenderType type) {
		this.type = type;
	}

	void retarget(RenderType type) {
		this.type = type;
	}

	void vertex(float x, float y, float z, float u, float v, int argb, int light, float nx, float ny, float nz) {
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
		data[o + 5] = nx;
		data[o + 6] = ny;
		data[o + 7] = nz;
		colors[count] = argb;
		lights[count] = light;
		count++;
	}

	/** A quad from four corners (counter-clockwise seen from the normal's side). */
	void quad(float[] a, float[] b, float[] c, float[] d, float u0, float v0, float u1, float v1, int argb, int light, float nx, float ny, float nz) {
		vertex(a[0], a[1], a[2], u0, v0, argb, light, nx, ny, nz);
		vertex(b[0], b[1], b[2], u0, v1, argb, light, nx, ny, nz);
		vertex(c[0], c[1], c[2], u1, v1, argb, light, nx, ny, nz);
		vertex(d[0], d[1], d[2], u1, v0, argb, light, nx, ny, nz);
	}

	int quads() {
		return count / 4;
	}

	void reset() {
		count = 0;
	}

	void submit(OrderedSubmitNodeCollector collector, PoseStack poseStack) {
		if (count >= 4) {
			collector.submitCustomGeometry(poseStack, type, writer);
		}
	}

	private void write(PoseStack.Pose pose, VertexConsumer buffer) {
		int n = count / 4 * 4;
		for (int i = 0; i < n; i++) {
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
