package dev.kultivator.client.dunia.langit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kultivator.Kultivator;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;

/**
 * Camera-relative vertices of one of module L's render types for one frame, submitted once through a reused writer (no
 * per-frame lambda), the pattern of the 7b {@code EpicRenderer.Sink} (DESIGN 23.10.2). Every type is the vanilla
 * {@code entityTranslucentEmissive} of a generated texture whose visible pixels keep alpha ≥ 0.1 (the pipeline's cutout),
 * full bright, no culling, no depth write: the "heavy shader" look is layered translucent quads (rule V2), never a post
 * chain, so Sodium and Iris stay happy. The textures come from {@code tools/gen_dunia_langit_textures.py}.
 */
final class LangitSink {

	static final Identifier STREAK_TEX = Kultivator.id("textures/dunia/langit/meteor_streak.png");
	static final Identifier HEAD_TEX = Kultivator.id("textures/dunia/langit/meteor_head.png");
	static final Identifier MOON_BLOOD_TEX = Kultivator.id("textures/dunia/langit/moon_blood.png");
	static final Identifier MOON_TWIN_TEX = Kultivator.id("textures/dunia/langit/moon_twin.png");
	static final Identifier HALO_TEX = Kultivator.id("textures/dunia/langit/moon_halo.png");
	static final Identifier PILLAR_TEX = Kultivator.id("textures/dunia/langit/pillar_band.png");
	static final Identifier CROWN_TEX = Kultivator.id("textures/dunia/langit/pillar_crown.png");
	static final Identifier MIST_TEX = Kultivator.id("textures/dunia/langit/mist_plane.png");
	static final Identifier GLOW_TEX = Kultivator.id("textures/dunia/langit/glow_dot.png");
	static final Identifier LOTUS_TEX = Kultivator.id("textures/dunia/langit/lotus_petal.png");
	static final Identifier ROCK_TEX = Kultivator.id("textures/dunia/langit/meteor_rock.png");
	static final Identifier RING_TEX = Kultivator.id("textures/misc/epic/ring_soft.png");
	static final Identifier MOON_SHADOW_TEX = Kultivator.id("textures/dunia/langit/moon_shadow.png");
	static final Identifier CRATER_TEX = Kultivator.id("textures/dunia/langit/crater_decal.png");
	static final Identifier RUNE_TEX = Kultivator.id("textures/dunia/langit/rune_ring.png");
	static final Identifier SHAFT_TEX = Kultivator.id("textures/dunia/langit/light_shaft.png");
	static final Identifier WISP_TEX = Kultivator.id("textures/dunia/langit/qi_wisp.png");

	static final RenderType STREAK = RenderTypes.entityTranslucentEmissive(STREAK_TEX);
	static final RenderType HEAD = RenderTypes.entityTranslucentEmissive(HEAD_TEX);
	static final RenderType MOON_BLOOD = RenderTypes.entityTranslucentEmissive(MOON_BLOOD_TEX);
	static final RenderType MOON_TWIN = RenderTypes.entityTranslucentEmissive(MOON_TWIN_TEX);
	static final RenderType HALO = RenderTypes.entityTranslucentEmissive(HALO_TEX);
	static final RenderType PILLAR = RenderTypes.entityTranslucentEmissive(PILLAR_TEX);
	static final RenderType CROWN = RenderTypes.entityTranslucentEmissive(CROWN_TEX);
	static final RenderType MIST = RenderTypes.entityTranslucentEmissive(MIST_TEX);
	static final RenderType GLOW = RenderTypes.entityTranslucentEmissive(GLOW_TEX);
	static final RenderType LOTUS = RenderTypes.entityTranslucentEmissive(LOTUS_TEX);
	static final RenderType RING = RenderTypes.entityTranslucentEmissive(RING_TEX);
	static final RenderType ROCK = RenderTypes.entitySolid(ROCK_TEX);
	static final RenderType MOON_SHADOW = RenderTypes.entityTranslucentEmissive(MOON_SHADOW_TEX);
	static final RenderType CRATER = RenderTypes.entityTranslucentEmissive(CRATER_TEX);
	static final RenderType RUNE = RenderTypes.entityTranslucentEmissive(RUNE_TEX);
	static final RenderType SHAFT = RenderTypes.entityTranslucentEmissive(SHAFT_TEX);
	static final RenderType WISP = RenderTypes.entityTranslucentEmissive(WISP_TEX);

	private static final int STRIDE = 8;

	private final RenderType type;
	private float[] data = new float[STRIDE * 256];
	private int[] colors = new int[256];
	private int count;
	private final SubmitNodeCollector.CustomGeometryRenderer writer = this::write;

	LangitSink(RenderType type) {
		this.type = type;
	}

	void reset() {
		count = 0;
	}

	int quads() {
		return count / 4;
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

	/** One quad from four corners (counter-clockwise), with its UVs, one colour per corner pair (a, b). */
	void quad(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
			  float u0, float v0, float u1, float v1, int ca, int cb) {
		vertex(x0, y0, z0, u0, v0, ca, 0.0F, 1.0F, 0.0F);
		vertex(x1, y1, z1, u1, v0, ca, 0.0F, 1.0F, 0.0F);
		vertex(x2, y2, z2, u1, v1, cb, 0.0F, 1.0F, 0.0F);
		vertex(x3, y3, z3, u0, v1, cb, 0.0F, 1.0F, 0.0F);
	}

	void submit(SubmitNodeCollector collector, PoseStack stack) {
		if (count < 4) {
			return;
		}
		collector.submitCustomGeometry(stack, type, writer);
	}

	private void write(PoseStack.Pose pose, VertexConsumer buffer) {
		int vertices = count / 4 * 4;
		for (int i = 0; i < vertices; i++) {
			int o = i * STRIDE;
			buffer.addVertex(pose, data[o], data[o + 1], data[o + 2])
					.setColor(colors[i])
					.setUv(data[o + 3], data[o + 4])
					.setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(LightCoordsUtil.FULL_BRIGHT)
					.setNormal(pose, data[o + 5], data[o + 6], data[o + 7]);
		}
	}
}
