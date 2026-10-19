package dev.kultivator.client.fx.spec;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The AVATAR and CLONE renderers on players and disciples (DESIGN 22.4.4; generic since the polish pass so
 * {@code DiscipleRenderer} can add it): the caster's own model drawn again at render time, scaled up about the feet as a
 * translucent full-bright rim (up to 2×; larger avatars are the world-level {@code GiantAvatar}), tinted copies turning
 * on a ring around it, and frozen afterimages at fixed world points along a movement path (S16). No entity is created.
 * Render layers never run for one's own first-person view; the particle outline of {@link RendererRun} covers that case
 * and Minimal quality. A caster already enlarged by a form keeps a 1.05 rim instead of growing twice (S21).
 */
public final class AvatarLayer<S extends AvatarRenderState, M extends PlayerModel> extends RenderLayer<S, M> {

	/** Model space: the feet are 1.501 below the model origin (y points down after the renderer's flip). */
	private static final float FEET = 1.501F;

	public AvatarLayer(RenderLayerParent<S, M> renderer) {
		super(renderer);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, S state, float yRot, float xRot) {
		if (state.isInvisible) {
			return;
		}
		AvatarFx.Avatar avatar = AvatarFx.avatarOf(state.id);
		AvatarFx.Clones clones = AvatarFx.clonesOf(state.id);
		AvatarFx.Fixed fixed = AvatarFx.fixedOf(state.id);
		java.util.List<AvatarFx.Copy> copies = AvatarFx.copiesOf(state.id);
		if (avatar == null && clones == null && fixed == null && copies.isEmpty()) {
			return;
		}
		Identifier skin = state.skin.body().texturePath();
		int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
		if (avatar != null) {
			float fade = AvatarFx.fade(avatar.start(), avatar.end());
			float grow = Math.min(1.0F, (Util.getMillis() - avatar.start()) / 400.0F);
			// a form that already enlarged the caster keeps a thin rim instead of a second enlargement
			float target = state.scale > 1.3F ? 1.05F : avatar.scale();
			float scale = 1.0F + (target - 1.0F) * grow;
			int color = ARGB.color(Math.round(avatar.alpha() * fade), avatar.rgb());
			poseStack.pushPose();
			poseStack.translate(0.0F, FEET, 0.0F);
			poseStack.scale(scale, scale, scale);
			poseStack.translate(0.0F, -FEET, 0.0F);
			submitNodeCollector.order(4).submitModel(this.getParentModel(), state, poseStack, RenderTypes.entityTranslucentEmissive(skin),
					LightCoordsUtil.FULL_BRIGHT, overlay, color, null, 0);
			poseStack.popPose();
		}
		if (clones != null) {
			float fade = AvatarFx.fade(clones.start(), clones.end());
			int color = ARGB.color(Math.round(150 * fade), clones.rgb());
			double turn = (Util.getMillis() - clones.start()) / 1000.0D * 0.6D;
			for (int i = 0; i < clones.count(); i++) {
				double angle = Math.PI * 2.0D * i / clones.count() + turn;
				poseStack.pushPose();
				poseStack.translate((float) (Math.cos(angle) * clones.radius()), 0.0F, (float) (Math.sin(angle) * clones.radius()));
				submitNodeCollector.order(4).submitModel(this.getParentModel(), state, poseStack, RenderTypes.entityTranslucentEmissive(skin),
						LightCoordsUtil.FULL_BRIGHT, overlay, color, null, 0);
				poseStack.popPose();
			}
		}
		if (fixed != null) {
			submitFixed(poseStack, submitNodeCollector, state, skin, overlay, fixed);
		}
		if (!copies.isEmpty()) {
			Quaternionf undo = new Quaternionf().rotationY((float) Math.toRadians(state.bodyRot - 180.0F));
			float inv = 1.0F / Math.max(0.05F, state.scale);
			for (AvatarFx.Copy copy : copies) {
				Vector3f d = new Vector3f((float) (copy.at().x - state.x), (float) (copy.at().y - state.y), (float) (copy.at().z - state.z));
				undo.transform(d);
				poseStack.pushPose();
				poseStack.translate(-d.x * inv, -d.y * inv, d.z * inv);
				poseStack.rotateDegrees(Axis.YP, copy.yaw() - state.bodyRot);
				float s = copy.scale() * inv;
				poseStack.translate(0.0F, FEET, 0.0F);
				poseStack.scale(s, s, s);
				poseStack.translate(0.0F, -FEET, 0.0F);
				submitNodeCollector.order(4).submitModel(this.getParentModel(), state, poseStack, RenderTypes.entityTranslucentEmissive(skin),
						LightCoordsUtil.FULL_BRIGHT, overlay, ARGB.color(copy.alpha(), copy.rgb()), null, 0);
				poseStack.popPose();
			}
		}
	}

	/**
	 * Afterimages at fixed world points: the world offset from the entity is turned back into model space (the renderer
	 * rotated by 180 − bodyRot, flipped x and y and scaled), and each copy is turned to the path's yaw. The oldest copy
	 * fades first.
	 */
	private void submitFixed(PoseStack poseStack, SubmitNodeCollector collector, S state, Identifier skin, int overlay, AvatarFx.Fixed fixed) {
		long now = Util.getMillis();
		float total = Math.max(1.0F, fixed.end() - fixed.start());
		float t = (now - fixed.start()) / total;
		int n = fixed.points().size();
		Quaternionf undo = new Quaternionf().rotationY((float) Math.toRadians(state.bodyRot - 180.0F));
		float inv = 1.0F / Math.max(0.05F, state.scale);
		for (int i = 0; i < n; i++) {
			// copy i fades out from t = i / n · 0.6 to + 0.4 (first along the path goes first)
			float start = i / (float) n * 0.6F;
			float a = 1.0F - Math.max(0.0F, Math.min(1.0F, (t - start) / 0.4F));
			if (a <= 0.02F) {
				continue;
			}
			Vec3 point = fixed.points().get(i);
			Vector3f d = new Vector3f((float) (point.x - state.x), (float) (point.y - state.y), (float) (point.z - state.z));
			undo.transform(d);
			poseStack.pushPose();
			poseStack.translate(-d.x * inv, -d.y * inv, d.z * inv);
			poseStack.rotateDegrees(Axis.YP, fixed.yaw() - state.bodyRot);
			int color = ARGB.color(Math.round(fixed.alpha() * a), fixed.rgb());
			collector.order(4).submitModel(this.getParentModel(), state, poseStack, RenderTypes.entityTranslucentEmissive(skin), LightCoordsUtil.FULL_BRIGHT,
					overlay, color, null, 0);
			poseStack.popPose();
		}
	}
}
