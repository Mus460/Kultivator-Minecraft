package dev.kultivator.client.dunia.kota42.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kultivator.Kultivator;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * The geometry helper of the town fx of 42V-F: a {@link Sink} gathers the camera-relative quads of one render type for a
 * frame (position, uv, colour, light and normal per vertex) and submits them once through
 * {@code SubmitNodeCollector.submitCustomGeometry}; the {@code Geo.tipe*} methods hand out one cached sink per texture so
 * lanterns, smoke, banners, glyphs and decals each cost one draw call per texture however many quads they have. Every fx
 * class draws into these sinks from {@link KotaFx#kumpul}; {@link #reset} and {@link #kirim} bracket a frame. Client only.
 */
final class Geo {

	static final Identifier PARTIKEL = Kultivator.id("textures/fx/kota42/partikel.png");
	static final Identifier CAHAYA = Kultivator.id("textures/fx/kota42/cahaya.png");

	/** Full bright packed light (the sink writes it as given). */
	static final int TERANG = 0xF000F0;

	private static final Map<String, Sink> SINK = new HashMap<>();

	/** Which blending a cached sink uses. */
	enum Jenis {
		/** Alpha cutout, lit by the world (cloth, boards). */
		POTONG,
		/** Translucent, lit by the world (smoke, paper, soot). */
		LENGKUNG,
		/** Translucent and fully bright (glow, sparks, halos, lit paper). */
		PANCAR
	}

	/** The sink of {@code texture} for {@code jenis} (created once). */
	static Sink tipe(Identifier texture, Jenis jenis) {
		return SINK.computeIfAbsent(jenis + "|" + texture, k -> new Sink(switch (jenis) {
			case POTONG -> RenderTypes.entityCutout(texture);
			case LENGKUNG -> RenderTypes.entityTranslucent(texture);
			case PANCAR -> RenderTypes.entityTranslucentEmissive(texture);
		}));
	}

	/** Start of a frame: every sink is emptied. */
	static void reset() {
		for (Sink s : SINK.values()) {
			s.reset();
		}
	}

	/** End of a frame: every non-empty sink is submitted. */
	static void kirim(SubmitNodeCollector collector, PoseStack pose) {
		for (Sink s : SINK.values()) {
			s.kirim(collector, pose);
		}
	}

	static int argb(float alpha, int rgb) {
		return ARGB.color(Mth.clamp(Math.round(alpha * 255.0F), 0, 255), rgb & 0xFFFFFF);
	}

	/** Colour channels of {@code rgb} scaled by {@code k} (0..1.5), alpha kept at {@code alpha}. */
	static int gelap(float alpha, int rgb, float k) {
		int r = Mth.clamp(Math.round(ARGB.red(rgb) * k), 0, 255);
		int g = Mth.clamp(Math.round(ARGB.green(rgb) * k), 0, 255);
		int b = Mth.clamp(Math.round(ARGB.blue(rgb) * k), 0, 255);
		return ARGB.color(Mth.clamp(Math.round(alpha * 255.0F), 0, 255), r, g, b);
	}

	/** Vertices of one render type for this frame (camera-relative). */
	static final class Sink {

		private static final int STRIDE = 8;
		private final RenderType type;
		private float[] data = new float[STRIDE * 1024];
		private int[] colors = new int[1024];
		private int[] lights = new int[1024];
		private int count;
		private final SubmitNodeCollector.CustomGeometryRenderer writer = this::tulis;

		Sink(RenderType type) {
			this.type = type;
		}

		void reset() {
			count = 0;
		}

		int quads() {
			return count / 4;
		}

		void vertex(float x, float y, float z, float u, float v, int argb, int light, float nx, float ny, float nz) {
			if (count >= colors.length) {
				int size = colors.length * 2;
				data = java.util.Arrays.copyOf(data, size * STRIDE);
				colors = java.util.Arrays.copyOf(colors, size);
				lights = java.util.Arrays.copyOf(lights, size);
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
		 * One quad, corners in order (a, b, c, d) counter-clockwise seen from the normal side; {@code dua} also writes the
		 * back face (entity render types do not cull, so a banner is seen from both sides with the right shading).
		 */
		void quad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
				float u0, float v0, float u1, float v1, int argb, int light, boolean dua) {
			float ex = bx - ax;
			float ey = by - ay;
			float ez = bz - az;
			float fx = dx - ax;
			float fy = dy - ay;
			float fz = dz - az;
			float nx = ey * fz - ez * fy;
			float ny = ez * fx - ex * fz;
			float nz = ex * fy - ey * fx;
			float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
			if (len > 1.0E-6F) {
				nx /= len;
				ny /= len;
				nz /= len;
			} else {
				nx = 0.0F;
				ny = 1.0F;
				nz = 0.0F;
			}
			// a at the top-left of the uv rect, b bottom-left, c bottom-right, d top-right
			vertex(ax, ay, az, u0, v0, argb, light, nx, ny, nz);
			vertex(bx, by, bz, u0, v1, argb, light, nx, ny, nz);
			vertex(cx, cy, cz, u1, v1, argb, light, nx, ny, nz);
			vertex(dx, dy, dz, u1, v0, argb, light, nx, ny, nz);
			if (dua) {
				vertex(dx, dy, dz, u1, v0, argb, light, -nx, -ny, -nz);
				vertex(cx, cy, cz, u1, v1, argb, light, -nx, -ny, -nz);
				vertex(bx, by, bz, u0, v1, argb, light, -nx, -ny, -nz);
				vertex(ax, ay, az, u0, v0, argb, light, -nx, -ny, -nz);
			}
		}

		void kirim(SubmitNodeCollector collector, PoseStack poseStack) {
			if (count >= 4) {
				collector.submitCustomGeometry(poseStack, type, writer);
			}
		}

		private void tulis(PoseStack.Pose pose, VertexConsumer buffer) {
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

	private Geo() {
	}
}
