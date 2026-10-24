package dev.kultivator.client.dunia.tempat.perbaikan;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * The quads of one vanilla entity render type for one frame of the repair scaffolds (DESIGN 38.R.3), written relative
 * to the pose handed to {@link #submit} and given to the collector once ({@code submitCustomGeometry}). Unlike the glow
 * sinks of the effect modules every quad carries its own face normal and light, so the bamboo and planks are shaded like
 * any entity (the render types used are lit, not emissive). A frame's vertices stay until the next {@link #reset}, so
 * the deferred writer sees them. Client render thread only.
 */
final class PerancahSink {

	private static final int STRIDE = 8;
	private final RenderType type;
	private float[] data;
	private int[] colors;
	private int[] lights;
	private int count;
	private final SubmitNodeCollector.CustomGeometryRenderer writer = this::write;

	PerancahSink(RenderType type, int vertices) {
		this.type = type;
		int n = Math.max(16, vertices);
		this.data = new float[STRIDE * n];
		this.colors = new int[n];
		this.lights = new int[n];
	}

	void reset() {
		count = 0;
	}

	int quads() {
		return count / 4;
	}

	private void vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz, int argb, int light) {
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

	/**
	 * One quad a-b-c-d (counter-clockwise seen from the normal's side; the render types used do not cull, so it shows
	 * from both sides) with the uv rectangle {@code u0..u1 x v0..v1}: a gets (u0, v1), b (u1, v1), c (u1, v0), d (u0, v0).
	 */
	void quad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
			  float u0, float v0, float u1, float v1, float nx, float ny, float nz, int argb, int light) {
		vertex(ax, ay, az, u0, v1, nx, ny, nz, argb, light);
		vertex(bx, by, bz, u1, v1, nx, ny, nz, argb, light);
		vertex(cx, cy, cz, u1, v0, nx, ny, nz, argb, light);
		vertex(dx, dy, dz, u0, v0, nx, ny, nz, argb, light);
	}

	void submit(SubmitNodeCollector collector, PoseStack poseStack) {
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
