package dev.kultivator.client.anim.form;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.client.anim.model.ModelFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws one form piece (a cuboid item model from {@code items/fx}) at a model part (polish plan C3). The caller has
 * already moved the pose stack into the part's space ({@code part.translateAndRotate}); the piece's (8, 8, 8) lands on
 * the part pivot, its +y on the part's "up", its front on the part's front and its +x on the wearer's right (part space
 * has y down, so the piece turns by 180 degrees about z). {@code alpha} scales the piece in from nothing (0..1) for
 * grow-in and fade-out; below 0.02 nothing is drawn. GiantAvatarRenderer (M2) draws the dharma armour on giants with
 * this too.
 */
public final class FormModels {

	private static final Map<String, ItemStackRenderState> STATES = new HashMap<>();

	public static void submitPiece(String pieceId, PoseStack ps, SubmitNodeCollector c, int light, float alpha) {
		if (alpha <= 0.02F || pieceId == null || pieceId.isEmpty()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		ItemStackRenderState state = STATES.computeIfAbsent(pieceId, id -> new ItemStackRenderState());
		minecraft.getItemModelResolver().updateForTopItem(state, ModelFx.stack(pieceId), ItemDisplayContext.FIXED, minecraft.level, null, 0);
		if (state.isEmpty()) {
			return;
		}
		ps.pushPose();
		ps.rotateDegrees(Axis.ZP, 180.0F);
		if (alpha < 1.0F) {
			float s = alpha * alpha * (3.0F - 2.0F * alpha);
			ps.scale(s, s, s);
		}
		state.submit(ps, c, light, OverlayTexture.NO_OVERLAY, 0);
		ps.popPose();
	}

	/** Like {@link #submitPiece} with an offset (part pixels, part space) and a turn (degrees about the part's y). */
	public static void submitPiece(String pieceId, PoseStack ps, SubmitNodeCollector c, int light, float alpha, float x, float y, float z,
			float yawDegrees, float pitchDegrees, float rollDegrees, float scale) {
		ps.pushPose();
		ps.translate(x / 16.0F, y / 16.0F, z / 16.0F);
		if (yawDegrees != 0.0F) {
			ps.rotateDegrees(Axis.YP, yawDegrees);
		}
		if (pitchDegrees != 0.0F) {
			ps.rotateDegrees(Axis.XP, pitchDegrees);
		}
		if (rollDegrees != 0.0F) {
			ps.rotateDegrees(Axis.ZP, rollDegrees);
		}
		if (scale != 1.0F) {
			ps.scale(scale, scale, scale);
		}
		submitPiece(pieceId, ps, c, light, alpha);
		ps.popPose();
	}

	/**
	 * Draws an already resolved item state with the piece convention (its +y up in part space, its (8, 8, 8) at the pose
	 * stack origin): the disciple's natal treasures, held qi swords and duty props.
	 */
	public static void submitState(ItemStackRenderState state, PoseStack ps, SubmitNodeCollector c, int light, int outlineColor) {
		if (state == null || state.isEmpty()) {
			return;
		}
		ps.pushPose();
		ps.rotateDegrees(Axis.ZP, 180.0F);
		state.submit(ps, c, light, OverlayTexture.NO_OVERLAY, outlineColor);
		ps.popPose();
	}

	/** Forgets the cached item states (resource reload). */
	public static void clear() {
		STATES.clear();
	}

	private FormModels() {
	}
}
