package dev.kultivator.client.sekte.paviliun;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;

/**
 * Camera-relative quads of one render type for one frame, submitted once through a reused writer (module A's own copy
 * of the {@code SigilRenderer.Sink} pattern, so no other module's file is touched). Entity render types: uv, overlay,
 * a light value per vertex (full bright for the glows, the world's light for the tablets) and a normal.
 */
final class PaviliunSink {

	private static final int STRIDE = 8;
	private final RenderType type;
	private float[] data = new float[STRIDE * 256];
	private int[] colors = new int[256];
	private int[] lights = new int[256];
	private int count;
	private final SubmitNodeCollector.CustomGeometryRenderer writer = this::write;

	PaviliunSink(RenderType type) {
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

	void vertex(float x, float y, float z, float u, float v, int argb, float nx, float ny, float nz) {
		vertex(x, y, z, u, v, argb, LightCoordsUtil.FULL_BRIGHT, nx, ny, nz);
	}

	int quads() {
		return count / 4;
	}

	void reset() {
		count = 0;
	}

	void submit(OrderedSubmitNodeCollector collector, PoseStack poseStack) {
		if (count < 4) {
			return;
		}
		collector.submitCustomGeometry(poseStack, type, writer);
	}

	private void write(PoseStack.Pose pose, VertexConsumer buffer) {
		int vertices = count / 4 * 4;
		for (int i = 0; i < vertices; i++) {
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
