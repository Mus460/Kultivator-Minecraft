package dev.kultivator.client.dunia.tempat.hidup;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * The far-crowd doll (DESIGN 38.N.6 impostor; 40.7.4: no grey model of its own, it wears the Sosok fallback atlas of
 * 40K with the vanilla skin UVs of region A, so a resident far away looks like the body it becomes up close): the six
 * parts of a player body plus, above Minimal, the hat and jacket shells (eight boxes), and at Maximal the sleeve and
 * trouser shells too (twelve). Legs, arms and the body are posed procedurally by pose family ({@code PendudukSnapshot}
 * {@code KLIP_*}): standing with breath, walking with the leg swing, sitting with the knees up, working with the right
 * arm, cheering with both arms in turn. Quads are written camera-relative into a {@link Sink} per atlas and submitted
 * once per frame (no entity, no model baking).
 */
final class BonekaImpostor {

	/** The Sosok atlas is 128 x 128 (40.3.1); vanilla skin coordinates fall into its region A. */
	private static final float TEX = 128.0F;
	private static final float PX = 1.0F / 16.0F;

	static final int DIAM = 0;
	static final int JALAN = 1;
	static final int DUDUK = 2;
	static final int KERJA = 3;
	static final int SORAK = 4;

	/** Box detail: 6 parts, + hat and jacket, + sleeves and trousers. */
	static final int RINCI_DASAR = 0;
	static final int RINCI_SEDANG = 1;
	static final int RINCI_PENUH = 2;

	// the frame of one doll (set by gambar, read by kotak)
	private static float cos;
	private static float sin;
	private static float skala;
	private static float ox;
	private static float oy;
	private static float oz;
	private static float duduk;

	private BonekaImpostor() {
	}

	/**
	 * Draws one doll with its feet at camera-relative {@code (x, y, z)}, facing {@code yaw} (degrees, Minecraft yaw), of
	 * height scale {@code s}, slim arms or not, in pose {@code pose} at animation time {@code t} (ticks, already phased),
	 * with {@code light} (packed) and {@code alpha}.
	 */
	static void gambar(Sink sink, float x, float y, float z, float yaw, float s, boolean ramping, int pose, float t, int light, float alpha,
			int rinci) {
		float r = yaw * Mth.DEG_TO_RAD;
		cos = Mth.cos(r);
		sin = Mth.sin(r);
		skala = s;
		ox = x;
		oy = y;
		oz = z;
		duduk = pose == DUDUK ? -10.0F : 0.0F;
		int c = Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F) << 24 | 0xFFFFFF;
		// pose angles (degrees; positive = forward)
		float kakiKanan = 0.0F;
		float kakiKiri = 0.0F;
		float lenganKanan = 0.0F;
		float lenganKiri = 0.0F;
		float napas = Mth.sin(t * 0.08F) * 0.25F;
		switch (pose) {
			case JALAN -> {
				float swing = Mth.sin(t * 0.55F) * 32.0F;
				kakiKanan = swing;
				kakiKiri = -swing;
				lenganKanan = -swing * 0.7F;
				lenganKiri = swing * 0.7F;
				napas = Math.abs(Mth.sin(t * 0.55F)) * 0.6F;
			}
			case DUDUK -> {
				kakiKanan = 90.0F;
				kakiKiri = 90.0F;
				lenganKanan = 30.0F;
				lenganKiri = 30.0F;
			}
			case KERJA -> {
				lenganKanan = 55.0F + Mth.sin(t * 0.3F) * 30.0F;
				lenganKiri = 35.0F;
			}
			case SORAK -> {
				float up = Mth.sin(t * 0.35F);
				lenganKanan = 150.0F + up * 25.0F;
				lenganKiri = 150.0F - up * 25.0F;
				napas = Math.max(0.0F, up) * 0.8F;
			}
			default -> {
			}
		}
		float w = ramping ? 3.0F : 4.0F;
		// legs (pivot at the hip, 12 px)
		kotak(sink, 0, 16, 4, 12, 4, -4.0F, 0.0F, -2.0F, 0.0F, 12.0F, 0.0F, kakiKanan, c, light);
		kotak(sink, 16, 48, 4, 12, 4, 0.0F, 0.0F, -2.0F, 0.0F, 12.0F, 0.0F, kakiKiri, c, light);
		// body and head (the breath lifts them a quarter pixel)
		kotak(sink, 16, 16, 8, 12, 4, -4.0F, 12.0F + napas, -2.0F, 0.0F, 0.0F, 0.0F, 0.0F, c, light);
		kotak(sink, 0, 0, 8, 8, 8, -4.0F, 24.0F + napas, -4.0F, 0.0F, 0.0F, 0.0F, 0.0F, c, light);
		// arms (pivot at the shoulder, 22 px)
		kotak(sink, 40, 16, w, 12, 4, -4.0F - w, 12.0F + napas, -2.0F, 0.0F, 22.0F, 0.0F, lenganKanan, c, light);
		kotak(sink, 32, 48, w, 12, 4, 4.0F, 12.0F + napas, -2.0F, 0.0F, 22.0F, 0.0F, lenganKiri, c, light);
		if (rinci >= RINCI_SEDANG) {
			kotak(sink, 32, 0, 8, 8, 8, -4.0F, 24.0F + napas, -4.0F, 0.5F, 0.0F, 0.0F, 0.0F, c, light);
			kotak(sink, 16, 32, 8, 12, 4, -4.0F, 12.0F + napas, -2.0F, 0.25F, 0.0F, 0.0F, 0.0F, c, light);
		}
		if (rinci >= RINCI_PENUH) {
			kotak(sink, 40, 32, w, 12, 4, -4.0F - w, 12.0F + napas, -2.0F, 0.25F, 22.0F, 0.0F, lenganKanan, c, light);
			kotak(sink, 48, 48, w, 12, 4, 4.0F, 12.0F + napas, -2.0F, 0.25F, 22.0F, 0.0F, lenganKiri, c, light);
			kotak(sink, 0, 32, 4, 12, 4, -4.0F, 0.0F, -2.0F, 0.25F, 12.0F, 0.0F, kakiKanan, c, light);
			kotak(sink, 0, 48, 4, 12, 4, 0.0F, 0.0F, -2.0F, 0.25F, 12.0F, 0.0F, kakiKiri, c, light);
		}
	}

	private static final float[] CX = new float[8];
	private static final float[] CY = new float[8];
	private static final float[] CZ = new float[8];

	/**
	 * One box of the doll: texture offset {@code (u, v)} (64-space) and size {@code w h d} (px), min corner
	 * {@code (x0, y0, z0)} (px from the feet, the doll facing +Z), grown by {@code tumbuh} px, turned {@code sudut} degrees
	 * forward about the X axis through {@code (py, pz)} (0 = no limb turn).
	 */
	private static void kotak(Sink sink, float u, float v, float w, float h, float d, float x0, float y0, float z0, float tumbuh, float py,
			float pz, float sudut, int color, int light) {
		float xa = x0 - tumbuh;
		float xb = x0 + w + tumbuh;
		float ya = y0 - tumbuh;
		float yb = y0 + h + tumbuh;
		float za = z0 - tumbuh;
		float zb = z0 + d + tumbuh;
		float a = -sudut * Mth.DEG_TO_RAD;
		float ca = Mth.cos(a);
		float sa = Mth.sin(a);
		boolean putar = sudut != 0.0F;
		for (int i = 0; i < 8; i++) {
			float px = (i & 1) == 0 ? xa : xb;
			float pyy = (i & 2) == 0 ? ya : yb;
			float pzz = (i & 4) == 0 ? za : zb;
			if (putar) {
				float ry = pyy - py;
				float rz = pzz - pz;
				pyy = py + ry * ca - rz * sa;
				pzz = pz + ry * sa + rz * ca;
			}
			// a seated doll sits 10 px lower (the stool takes the rest)
			pyy += duduk;
			float lx = px * PX * skala;
			float ly = pyy * PX * skala;
			float lz = pzz * PX * skala;
			// Minecraft yaw: local +Z is the facing (-sin, cos), local +X is (cos, sin)
			CX[i] = ox + lx * cos - lz * sin;
			CY[i] = oy + ly;
			CZ[i] = oz + lx * sin + lz * cos;
		}
		// corner index = x | y << 1 | z << 2 (0 = min, 1 = max)
		float s = 1.0F / TEX;
		// front (+Z): left of the viewer is -X
		face(sink, 6, 4, 5, 7, (u + d) * s, (v + d) * s, (u + d + w) * s, (v + d + h) * s, color, light);
		// back (-Z)
		face(sink, 3, 1, 0, 2, (u + d + d + w) * s, (v + d) * s, (u + d + d + w + w) * s, (v + d + h) * s, color, light);
		// the doll's right side (-X)
		face(sink, 2, 0, 4, 6, u * s, (v + d) * s, (u + d) * s, (v + d + h) * s, color, light);
		// the doll's left side (+X)
		face(sink, 7, 5, 1, 3, (u + d + w) * s, (v + d) * s, (u + d + w + d) * s, (v + d + h) * s, color, light);
		// top (+Y)
		face(sink, 2, 6, 7, 3, (u + d) * s, v * s, (u + d + w) * s, (v + d) * s, color, light);
		// bottom (-Y)
		face(sink, 4, 0, 1, 5, (u + d + w) * s, v * s, (u + d + w + w) * s, (v + d) * s, color, light);
	}

	/** One face: corners top-left, bottom-left, bottom-right, top-right (counter-clockwise from outside). */
	private static void face(Sink sink, int tl, int bl, int br, int tr, float u0, float v0, float u1, float v1, int color, int light) {
		float ax = CX[bl] - CX[tl];
		float ay = CY[bl] - CY[tl];
		float az = CZ[bl] - CZ[tl];
		float bx = CX[tr] - CX[tl];
		float by = CY[tr] - CY[tl];
		float bz = CZ[tr] - CZ[tl];
		// outward normal = (bottom-left - top-left) x (top-right - top-left)
		float nx = ay * bz - az * by;
		float ny = az * bx - ax * bz;
		float nz = ax * by - ay * bx;
		float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
		if (len < 1.0E-6F) {
			return;
		}
		nx /= len;
		ny /= len;
		nz /= len;
		sink.vertex(CX[tl], CY[tl], CZ[tl], u0, v0, color, light, nx, ny, nz);
		sink.vertex(CX[bl], CY[bl], CZ[bl], u0, v1, color, light, nx, ny, nz);
		sink.vertex(CX[br], CY[br], CZ[br], u1, v1, color, light, nx, ny, nz);
		sink.vertex(CX[tr], CY[tr], CZ[tr], u1, v0, color, light, nx, ny, nz);
	}

	/** Camera-relative quads of one atlas for one frame, submitted once through a reused writer. */
	static final class Sink {
		private static final int STRIDE = 8;
		private final RenderType type;
		private float[] data = new float[STRIDE * 4 * 96];
		private int[] colors = new int[4 * 96];
		private int[] lights = new int[4 * 96];
		private int count;
		private final SubmitNodeCollector.CustomGeometryRenderer writer = this::write;

		Sink(RenderType type) {
			this.type = type;
		}

		void reset() {
			count = 0;
		}

		boolean kosong() {
			return count < 4;
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

		void submit(SubmitNodeCollector collector, PoseStack poseStack) {
			if (count >= 4) {
				collector.submitCustomGeometry(poseStack, type, writer);
			}
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
}
