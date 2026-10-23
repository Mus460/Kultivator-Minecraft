package dev.kultivator.client.sekte.langit.gerak;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.util.Arrays;

/**
 * A few textured quads in an entity's local space, collected per frame and handed to the collector once
 * ({@code submitCustomGeometry}), plus the shapes module 37M draws with them: atlas cuboids, camera-facing billboards,
 * flat turned discs and a waving ribbon. Vertices carry colour, uv, light and a normal. Client render thread only.
 */
final class GerakQuads {

	private float[] data = new float[8 * 256];
	private int[] colors = new int[256];
	private int[] lights = new int[256];
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

	/**
	 * An axis-aligned cuboid from (x0, y0, z0) to (x1, y1, z1), every face showing the atlas rectangle (u0, v0)-(u1, v1)
	 * stretched over it (the side faces darkened a little by the vertex colour, like block shading).
	 */
	void box(float x0, float y0, float z0, float x1, float y1, float z1, float u0, float v0, float u1, float v1, int argb, int light) {
		int side = shadeColor(argb, 0.86F);
		int front = shadeColor(argb, 0.93F);
		int bottom = shadeColor(argb, 0.62F);
		// up
		vertex(x0, y1, z0, u0, v0, argb, light, 0.0F, 1.0F, 0.0F);
		vertex(x0, y1, z1, u0, v1, argb, light, 0.0F, 1.0F, 0.0F);
		vertex(x1, y1, z1, u1, v1, argb, light, 0.0F, 1.0F, 0.0F);
		vertex(x1, y1, z0, u1, v0, argb, light, 0.0F, 1.0F, 0.0F);
		// down
		vertex(x0, y0, z0, u0, v0, bottom, light, 0.0F, -1.0F, 0.0F);
		vertex(x1, y0, z0, u1, v0, bottom, light, 0.0F, -1.0F, 0.0F);
		vertex(x1, y0, z1, u1, v1, bottom, light, 0.0F, -1.0F, 0.0F);
		vertex(x0, y0, z1, u0, v1, bottom, light, 0.0F, -1.0F, 0.0F);
		// north (-z)
		vertex(x0, y1, z0, u0, v0, front, light, 0.0F, 0.0F, -1.0F);
		vertex(x1, y1, z0, u1, v0, front, light, 0.0F, 0.0F, -1.0F);
		vertex(x1, y0, z0, u1, v1, front, light, 0.0F, 0.0F, -1.0F);
		vertex(x0, y0, z0, u0, v1, front, light, 0.0F, 0.0F, -1.0F);
		// south (+z)
		vertex(x0, y1, z1, u0, v0, front, light, 0.0F, 0.0F, 1.0F);
		vertex(x0, y0, z1, u0, v1, front, light, 0.0F, 0.0F, 1.0F);
		vertex(x1, y0, z1, u1, v1, front, light, 0.0F, 0.0F, 1.0F);
		vertex(x1, y1, z1, u1, v0, front, light, 0.0F, 0.0F, 1.0F);
		// west (-x)
		vertex(x0, y1, z0, u0, v0, side, light, -1.0F, 0.0F, 0.0F);
		vertex(x0, y0, z0, u0, v1, side, light, -1.0F, 0.0F, 0.0F);
		vertex(x0, y0, z1, u1, v1, side, light, -1.0F, 0.0F, 0.0F);
		vertex(x0, y1, z1, u1, v0, side, light, -1.0F, 0.0F, 0.0F);
		// east (+x)
		vertex(x1, y1, z0, u0, v0, side, light, 1.0F, 0.0F, 0.0F);
		vertex(x1, y1, z1, u1, v0, side, light, 1.0F, 0.0F, 0.0F);
		vertex(x1, y0, z1, u1, v1, side, light, 1.0F, 0.0F, 0.0F);
		vertex(x1, y0, z0, u0, v1, side, light, 1.0F, 0.0F, 0.0F);
	}

	/** A camera-facing square of half size {@code half} centred on x/y/z ({@code cam*}: the camera in the same frame). */
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
		float rx = vz;
		float rz = -vx;
		float rl = (float) Math.sqrt(rx * rx + rz * rz);
		if (rl < 1.0E-4F) {
			rx = 1.0F;
			rz = 0.0F;
			rl = 1.0F;
		}
		rx /= rl;
		rz /= rl;
		float ux = vy * rz;
		float uy = vz * rx - vx * rz;
		float uz = -vy * rx;
		rx *= half;
		rz *= half;
		ux *= half;
		uy *= half;
		uz *= half;
		vertex(x - rx - ux, y - uy, z - rz - uz, 0.0F, 1.0F, argb, light, vx, vy, vz);
		vertex(x + rx - ux, y - uy, z + rz - uz, 1.0F, 1.0F, argb, light, vx, vy, vz);
		vertex(x + rx + ux, y + uy, z + rz + uz, 1.0F, 0.0F, argb, light, vx, vy, vz);
		vertex(x - rx + ux, y + uy, z - rz + uz, 0.0F, 0.0F, argb, light, vx, vy, vz);
	}

	/** A flat horizontal square turned by {@code angle} radians about its centre (the levitation ring). */
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
			count = 0;
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

	static int shadeColor(int argb, float f) {
		int a = argb >>> 24;
		int r = Math.min(255, Math.round((argb >> 16 & 0xFF) * f));
		int g = Math.min(255, Math.round((argb >> 8 & 0xFF) * f));
		int b = Math.min(255, Math.round((argb & 0xFF) * f));
		return a << 24 | r << 16 | g << 8 | b;
	}
}
