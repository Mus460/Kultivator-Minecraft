package dev.kultivator.client.dunia.reruntuhan;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

/**
 * Small drawing helpers of module R's renderers: block models as boxes (a block model scaled into any box, the way a
 * block Display is scaled), carved or floating glyph text, and the facing rotations. Everything is an entity model or a
 * text quad: no block is ever placed (DESIGN 34.0 rule W1/W2).
 */
final class RDraw {

	/** One text pixel at scale 1 (vanilla name tags use 0.025). */
	static final float TEXT = 0.025F;

	/** Draws {@code model} filling the box from ({@code x}, {@code y}, {@code z}) of size {@code sx × sy × sz}. */
	static void box(PoseStack poseStack, SubmitNodeCollector collector, BlockModelRenderState model, float x, float y, float z, float sx, float sy,
					float sz, int light, int overlay) {
		if (sx <= 0.0F || sy <= 0.0F || sz <= 0.0F) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(x, y, z);
		poseStack.scale(sx, sy, sz);
		model.submit(poseStack, collector, light, overlay, 0);
		poseStack.popPose();
	}

	/** {@link #box} centred on x / z with its bottom at y. */
	static void centred(PoseStack poseStack, SubmitNodeCollector collector, BlockModelRenderState model, float x, float y, float z, float sx, float sy,
						float sz, int light, int overlay) {
		box(poseStack, collector, model, x - sx * 0.5F, y, z - sz * 0.5F, sx, sy, sz, light, overlay);
	}

	/** The rotation that turns text (and quads in the xy plane) to face along the horizontal normal ({@code nx}, {@code nz}). */
	static Quaternionf facing(float nx, float nz) {
		float yaw = (float) Math.atan2(nx, -nz);
		return new Quaternionf().rotationY((float) Math.PI - yaw);
	}

	/** The rotation that lays text flat, readable from above, its top toward {@code yaw} radians (0 = toward -z). */
	static Quaternionf flat(float yaw) {
		return new Quaternionf().rotationYXZ((float) Math.PI - yaw, -(float) Math.PI / 2.0F, 0.0F);
	}

	/**
	 * Text centred on the pose point ({@code x}, {@code y}, {@code z}), turned by {@code rotation}, {@code size} the height
	 * of one line in blocks (≈ 9 text pixels), full bright.
	 */
	static void text(PoseStack poseStack, SubmitNodeCollector collector, Component text, float x, float y, float z, Quaternionfc rotation,
					 float size, int argb, boolean seeThrough) {
		Font font = Minecraft.getInstance().font;
		FormattedCharSequence seq = text.getVisualOrderText();
		float width = font.width(seq);
		float s = size / 9.0F;
		poseStack.pushPose();
		poseStack.translate(x, y, z);
		poseStack.rotate(rotation);
		poseStack.scale(s, -s, s);
		collector.submitText(poseStack, -width / 2.0F, -4.5F, seq, false, seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.POLYGON_OFFSET,
				LightCoordsUtil.FULL_BRIGHT, argb, 0, 0);
		poseStack.popPose();
	}

	/** A text line with a dark backing (floating boards: the ranking stele, the band gates). */
	static void board(PoseStack poseStack, SubmitNodeCollector collector, Component text, float x, float y, float z, Quaternionfc rotation, float size,
					  int argb, int background) {
		Font font = Minecraft.getInstance().font;
		FormattedCharSequence seq = text.getVisualOrderText();
		float width = font.width(seq);
		float s = size / 9.0F;
		poseStack.pushPose();
		poseStack.translate(x, y, z);
		poseStack.rotate(rotation);
		poseStack.scale(s, -s, s);
		if (background != 0) {
			collector.submitTextBackground(poseStack, -width / 2.0F - 2.0F, -5.5F, width / 2.0F + 2.0F, 5.0F, background, Font.DisplayMode.NORMAL,
					LightCoordsUtil.FULL_BRIGHT);
		}
		collector.submitText(poseStack, -width / 2.0F, -4.5F, seq, false, Font.DisplayMode.NORMAL, LightCoordsUtil.FULL_BRIGHT, argb, 0, 0);
		poseStack.popPose();
	}

	/** A colour 0xRRGGBB lightened toward white by {@code t}. */
	static int lighten(int rgb, float t) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		r = Math.round(r + (255 - r) * t);
		g = Math.round(g + (255 - g) * t);
		b = Math.round(b + (255 - b) * t);
		return (r << 16) | (g << 8) | b;
	}

	/** A colour 0xRRGGBB mixed toward {@code to} by {@code t}. */
	static int mix(int from, int to, float t) {
		float k = Math.max(0.0F, Math.min(1.0F, t));
		int r = Math.round(((from >> 16) & 0xFF) * (1 - k) + ((to >> 16) & 0xFF) * k);
		int g = Math.round(((from >> 8) & 0xFF) * (1 - k) + ((to >> 8) & 0xFF) * k);
		int b = Math.round((from & 0xFF) * (1 - k) + (to & 0xFF) * k);
		return (r << 16) | (g << 8) | b;
	}

	private RDraw() {
	}
}
