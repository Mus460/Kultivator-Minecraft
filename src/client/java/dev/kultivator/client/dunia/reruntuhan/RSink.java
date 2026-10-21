package dev.kultivator.client.dunia.reruntuhan;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Vector3f;

/**
 * The quads of one render type for one frame (module R's world FX, DESIGN 34.0 rule V2: layered additive quads and glow
 * textures, no post chain, no core shader), written relative to the pose the caller hands to {@link #submit} and handed
 * to the collector once ({@code submitCustomGeometry}). Every vertex carries a colour and uv; light is full bright unless
 * a quad sets its own. A frame's vertices stay until the next {@link #reset}, so the deferred writer sees them. Client
 * render thread only.
 */
final class RSink {

	private static final int STRIDE = 5;
	private final RenderType type;
	private float[] data;
	private int[] colors;
	private int[] lights;
	private int count;
	private final SubmitNodeCollector.CustomGeometryRenderer writer = this::write;

	RSink(RenderType type) {
		this(type, 256);
	}

	/** A sink sized for {@code vertices} (it grows when needed); renderers make a small one per entity and frame. */
	RSink(RenderType type, int vertices) {
		this.type = type;
		int n = Math.max(8, vertices);
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

	void vertex(float x, float y, float z, float u, float v, int argb) {
		vertex(x, y, z, u, v, argb, LightCoordsUtil.FULL_BRIGHT);
	}

	void vertex(float x, float y, float z, float u, float v, int argb, int light) {
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
		colors[count] = argb;
		lights[count] = light;
		count++;
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
					.setNormal(pose, 0.0F, 1.0F, 0.0F);
		}
	}

	// ================================================================== shapes (x, y, z relative to the pose origin)

	/** A camera-facing square of half size {@code half} ({@code right} / {@code up}: the camera's axes). */
	void billboard(float x, float y, float z, Vector3f right, Vector3f up, float half, int color) {
		float rx = right.x * half;
		float ry = right.y * half;
		float rz = right.z * half;
		float ux = up.x * half;
		float uy = up.y * half;
		float uz = up.z * half;
		vertex(x - rx - ux, y - ry - uy, z - rz - uz, 0.0F, 1.0F, color);
		vertex(x + rx - ux, y + ry - uy, z + rz - uz, 1.0F, 1.0F, color);
		vertex(x + rx + ux, y + ry + uy, z + rz + uz, 1.0F, 0.0F, color);
		vertex(x - rx + ux, y - ry + uy, z - rz + uz, 0.0F, 0.0F, color);
	}

	/** A flat horizontal square turned by {@code angle} (radians), seen from above and below. */
	void flat(float x, float y, float z, float half, float angle, int color) {
		float c = (float) Math.cos(angle) * half;
		float s = (float) Math.sin(angle) * half;
		vertex(x - c + s, y, z - s - c, 0.0F, 0.0F, color);
		vertex(x - c - s, y, z - s + c, 0.0F, 1.0F, color);
		vertex(x + c - s, y, z + s + c, 1.0F, 1.0F, color);
		vertex(x + c + s, y, z + s - c, 1.0F, 0.0F, color);
		vertex(x + c + s, y, z + s - c, 1.0F, 0.0F, color);
		vertex(x + c - s, y, z + s + c, 1.0F, 1.0F, color);
		vertex(x - c - s, y, z - s + c, 0.0F, 1.0F, color);
		vertex(x - c + s, y, z - s - c, 0.0F, 0.0F, color);
	}

	/**
	 * A strip from {@code a} to {@code b} ({@code ax..bz}), {@code wa} wide at a and {@code wb} at b, its width along
	 * ({@code sx}, {@code sy}, {@code sz}) (a unit vector), both faces; u runs across, v from b (0) to a (1).
	 */
	void strip(float ax, float ay, float az, float bx, float by, float bz, float sx, float sy, float sz, float wa, float wb, int colorA, int colorB) {
		float ha = wa * 0.5F;
		float hb = wb * 0.5F;
		vertex(ax - sx * ha, ay - sy * ha, az - sz * ha, 0.0F, 1.0F, colorA);
		vertex(ax + sx * ha, ay + sy * ha, az + sz * ha, 1.0F, 1.0F, colorA);
		vertex(bx + sx * hb, by + sy * hb, bz + sz * hb, 1.0F, 0.0F, colorB);
		vertex(bx - sx * hb, by - sy * hb, bz - sz * hb, 0.0F, 0.0F, colorB);
		vertex(bx - sx * hb, by - sy * hb, bz - sz * hb, 0.0F, 0.0F, colorB);
		vertex(bx + sx * hb, by + sy * hb, bz + sz * hb, 1.0F, 0.0F, colorB);
		vertex(ax + sx * ha, ay + sy * ha, az + sz * ha, 1.0F, 1.0F, colorA);
		vertex(ax - sx * ha, ay - sy * ha, az - sz * ha, 0.0F, 1.0F, colorA);
	}

	/**
	 * A ribbon from {@code a} to {@code b} that turns its face to the camera ({@code cx..cz}: the camera relative to the
	 * same origin), {@code width} wide.
	 */
	void beam(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float width, int colorA, int colorB) {
		float dx = bx - ax;
		float dy = by - ay;
		float dz = bz - az;
		float mx = (ax + bx) * 0.5F - cx;
		float my = (ay + by) * 0.5F - cy;
		float mz = (az + bz) * 0.5F - cz;
		// side = dir x view
		float sx = dy * mz - dz * my;
		float sy = dz * mx - dx * mz;
		float sz = dx * my - dy * mx;
		float len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (len < 1.0E-5F) {
			return;
		}
		strip(ax, ay, az, bx, by, bz, sx / len, sy / len, sz / len, width, width, colorA, colorB);
	}

	/**
	 * A vertical band around a circle (a wall of light: shock waves, pillars, the cloud ring's skirt), {@code segments}
	 * quads from {@code y0} to {@code y1}; both faces. Colours fade from {@code bottom} to {@code top}.
	 */
	void band(float x, float y0, float y1, float z, float radius, int segments, int bottom, int top) {
		for (int i = 0; i < segments; i++) {
			double a0 = Math.PI * 2.0D * i / segments;
			double a1 = Math.PI * 2.0D * (i + 1) / segments;
			float x0 = x + (float) Math.cos(a0) * radius;
			float z0 = z + (float) Math.sin(a0) * radius;
			float x1 = x + (float) Math.cos(a1) * radius;
			float z1 = z + (float) Math.sin(a1) * radius;
			float u0 = i / (float) segments;
			float u1 = (i + 1) / (float) segments;
			vertex(x0, y0, z0, u0, 1.0F, bottom);
			vertex(x1, y0, z1, u1, 1.0F, bottom);
			vertex(x1, y1, z1, u1, 0.0F, top);
			vertex(x0, y1, z0, u0, 0.0F, top);
			vertex(x0, y1, z0, u0, 0.0F, top);
			vertex(x1, y1, z1, u1, 0.0F, top);
			vertex(x1, y0, z1, u1, 1.0F, bottom);
			vertex(x0, y0, z0, u0, 1.0F, bottom);
		}
	}

	/** {@code rgb} with alpha {@code alpha} (0..1). */
	static int argb(float alpha, int rgb) {
		return ARGB.color(Math.max(0, Math.min(255, Math.round(alpha * 255.0F))), rgb & 0xFFFFFF);
	}
}
