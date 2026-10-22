package dev.kultivator.client.sekte.tangga;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;

/**
 * Camera-relative vertices of one render type for one frame, submitted once through a reused writer (the pattern of
 * {@code SigilRenderer.Sink}, module G's own copy so nothing of another module is touched). {@code entity} types get
 * uv, overlay, full-bright light and a normal; the {@code lightning} type gets colour only.
 */
final class TanggaSink {

	private static final int STRIDE = 8;
	private final RenderType type;
	private final boolean entity;
	private float[] data = new float[STRIDE * 1024];
	private int[] colors = new int[1024];
	private int count;
	private final SubmitNodeCollector.CustomGeometryRenderer writer = this::write;

	TanggaSink(RenderType type, boolean entity) {
		this.type = type;
		this.entity = entity;
	}

	void vertex(float x, float y, float z, float u, float v, int argb, float nx, float ny, float nz) {
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
		data[o + 5] = nx;
		data[o + 6] = ny;
		data[o + 7] = nz;
		colors[count] = argb;
		count++;
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
			if (entity) {
				buffer.addVertex(pose, data[o], data[o + 1], data[o + 2])
						.setColor(colors[i])
						.setUv(data[o + 3], data[o + 4])
						.setOverlay(OverlayTexture.NO_OVERLAY)
						.setLight(LightCoordsUtil.FULL_BRIGHT)
						.setNormal(pose, data[o + 5], data[o + 6], data[o + 7]);
			} else {
				buffer.addVertex(pose, data[o], data[o + 1], data[o + 2]).setColor(colors[i]);
			}
		}
	}
}
