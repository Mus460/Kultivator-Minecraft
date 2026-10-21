package dev.kultivator.client.dunia.kapal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.util.Arrays;

/**
 * A few textured quads in the entity's local space, collected per frame and handed to the collector once
 * ({@code submitCustomGeometry}), like the Titah monument's extras. Vertices carry colour, uv, light and a normal.
 * Client render thread only.
 */
final class QuadBuf {

	private float[] data = new float[8 * 64];
	private int[] colors = new int[64];
	private int[] lights = new int[64];
	private int count;

	void clear() {
		count = 0;
	}

	int quads() {
		return count / 4;
	}

	void vertex(float x, float y, float z, float u, float v, int argb, int light, float nx, float ny, float nz) {
		if (count >= colors.length) {
			data = Arrays.copyOf(data, data.length * 2);
			colors = Arrays.copyOf(colors, colors.length * 2);
			lights = Arrays.copyOf(lights, lights.length * 2);
		}
		int o = count * 8;
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

	/** A camera-facing square of half size {@code half} centred on x/y/z ({@code cam*}: the camera relative to the entity). */
	void billboard(float x, float y, float z, float half, int argb, int light, float camX, float camY, float camZ) {
		float vx = camX - x;
		float vy = camY - y;
		float vz = camZ - z;
		float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
		if (len < 1.0E-4F) {
			return;
		}
		vx /= len;
		vy /= len;
		vz /= len;
		// right = up × view, up' = view × right
		float rx = vz;
		float ry = 0.0F;
		float rz = -vx;
		float rl = (float) Math.sqrt(rx * rx + rz * rz);
		if (rl < 1.0E-4F) {
			rx = 1.0F;
			rz = 0.0F;
			rl = 1.0F;
		}
		rx /= rl;
		rz /= rl;
		float ux = vy * rz - vz * ry;
		float uy = vz * rx - vx * rz;
		float uz = vx * ry - vy * rx;
		rx *= half;
		ry *= half;
		rz *= half;
		ux *= half;
		uy *= half;
		uz *= half;
		vertex(x - rx - ux, y - ry - uy, z - rz - uz, 0.0F, 1.0F, argb, light, vx, vy, vz);
		vertex(x + rx - ux, y + ry - uy, z + rz - uz, 1.0F, 1.0F, argb, light, vx, vy, vz);
		vertex(x + rx + ux, y + ry + uy, z + rz + uz, 1.0F, 0.0F, argb, light, vx, vy, vz);
		vertex(x - rx + ux, y - ry + uy, z - rz + uz, 0.0F, 0.0F, argb, light, vx, vy, vz);
	}

	/** A flat horizontal square facing up (ground glows). */
	void flat(float x, float y, float z, float half, int argb, int light) {
		vertex(x - half, y, z - half, 0.0F, 0.0F, argb, light, 0.0F, 1.0F, 0.0F);
		vertex(x - half, y, z + half, 0.0F, 1.0F, argb, light, 0.0F, 1.0F, 0.0F);
		vertex(x + half, y, z + half, 1.0F, 1.0F, argb, light, 0.0F, 1.0F, 0.0F);
		vertex(x + half, y, z - half, 1.0F, 0.0F, argb, light, 0.0F, 1.0F, 0.0F);
	}

	void submit(SubmitNodeCollector collector, PoseStack poseStack, RenderType type) {
		if (count < 4) {
			return;
		}
		int n = count / 4 * 4;
		float[] d = Arrays.copyOf(data, n * 8);
		int[] c = Arrays.copyOf(colors, n);
		int[] l = Arrays.copyOf(lights, n);
		collector.submitCustomGeometry(poseStack, type, (PoseStack.Pose pose, VertexConsumer buffer) -> {
			for (int i = 0; i < n; i++) {
				int o = i * 8;
				buffer.addVertex(pose, d[o], d[o + 1], d[o + 2])
						.setColor(c[i])
						.setUv(d[o + 3], d[o + 4])
						.setOverlay(OverlayTexture.NO_OVERLAY)
						.setLight(l[i])
						.setNormal(pose, d[o + 5], d[o + 6], d[o + 7]);
			}
		});
		count = 0;
	}
}
