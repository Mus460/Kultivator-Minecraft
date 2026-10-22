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

	/**
	 * A strip from point 0 to point 1 turned about its own axis to face the camera ({@code cam*}), {@code half} wide on
	 * each side: rigging lines, qi oars. u runs across (0 → 1), v along ({@code v0} at point 0, {@code v1} at point 1).
	 */
	void ribbon(float x0, float y0, float z0, float x1, float y1, float z1, float half, float v0, float v1, int argb, int light, float camX,
			float camY, float camZ) {
		float dx = x1 - x0;
		float dy = y1 - y0;
		float dz = z1 - z0;
		float vx = camX - (x0 + x1) * 0.5F;
		float vy = camY - (y0 + y1) * 0.5F;
		float vz = camZ - (z0 + z1) * 0.5F;
		// side = along × view
		float sx = dy * vz - dz * vy;
		float sy = dz * vx - dx * vz;
		float sz = dx * vy - dy * vx;
		float len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (len < 1.0E-5F) {
			return;
		}
		sx = sx / len * half;
		sy = sy / len * half;
		sz = sz / len * half;
		float vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
		float nx = vl < 1.0E-5F ? 0.0F : vx / vl;
		float ny = vl < 1.0E-5F ? 1.0F : vy / vl;
		float nz = vl < 1.0E-5F ? 0.0F : vz / vl;
		vertex(x0 - sx, y0 - sy, z0 - sz, 0.0F, v0, argb, light, nx, ny, nz);
		vertex(x0 + sx, y0 + sy, z0 + sz, 1.0F, v0, argb, light, nx, ny, nz);
		vertex(x1 + sx, y1 + sy, z1 + sz, 1.0F, v1, argb, light, nx, ny, nz);
		vertex(x1 - sx, y1 - sy, z1 - sz, 0.0F, v1, argb, light, nx, ny, nz);
	}

	/** A flat horizontal square turned by {@code angle} radians about its centre (formation discs). */
	void flatTurned(float x, float y, float z, float half, float angle, int argb, int light) {
		float c = (float) Math.cos(angle) * half;
		float s = (float) Math.sin(angle) * half;
		vertex(x - c + s, y, z - s - c, 0.0F, 0.0F, argb, light, 0.0F, 1.0F, 0.0F);
		vertex(x - c - s, y, z - s + c, 0.0F, 1.0F, argb, light, 0.0F, 1.0F, 0.0F);
		vertex(x + c - s, y, z + s + c, 1.0F, 1.0F, argb, light, 0.0F, 1.0F, 0.0F);
		vertex(x + c + s, y, z + s - c, 1.0F, 0.0F, argb, light, 0.0F, 1.0F, 0.0F);
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
