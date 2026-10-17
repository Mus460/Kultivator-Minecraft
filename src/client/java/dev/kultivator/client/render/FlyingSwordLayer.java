package dev.kultivator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.data.VisualState;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The flying sword (Pedang Terbang): while the synced look says FLYING_SWORD, the real sword item is drawn large and
 * lying flat under the feet, tip pointing forward, bobbing gently and rocking a little, slightly lit by its own qi.
 * The particle trail is drawn by TechniqueFx.
 */
public class FlyingSwordLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

	/** The model's feet in layer space (24 model pixels below the neck pivot). */
	private static final float FEET_Y = 24.0F / 16.0F;
	/** Just under the soles, so the feet stand on the flat of the blade. */
	private static final float UNDER_FEET = 0.07F;
	private static final float SCALE = 1.45F;

	private final ItemModelResolver itemModelResolver;
	private final ItemStackRenderState item = new ItemStackRenderState();

	public FlyingSwordLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, EntityRendererProvider.Context context) {
		super(renderer);
		this.itemModelResolver = context.getItemModelResolver();
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, AvatarRenderState state,
					   float yRot, float xRot) {
		if (state.isInvisible) {
			return;
		}
		VisualState visual = CultivatorLook.visual(state);
		if (!visual.has(VisualState.FLYING_SWORD)) {
			return;
		}
		ItemStack sword = CultivatorLook.sword(visual.swordItem());
		Player player = CultivatorLook.player(state);
		if (sword.isEmpty() || player == null) {
			return;
		}
		this.itemModelResolver.updateForLiving(this.item, sword, ItemDisplayContext.FIXED, player);
		if (this.item.isEmpty()) {
			return;
		}
		float age = state.ageInTicks;
		float bob = Mth.sin(age * 0.11F) * 0.035F;
		float roll = Mth.sin(age * 0.067F) * 3.5F;
		float pitch = Mth.sin(age * 0.05F + 1.3F) * 1.5F;
		// The blade glows faintly with qi: block light raised towards full.
		int light = LightCoordsUtil.addSmoothBlockEmission(lightCoords, 0.45F);
		poseStack.pushPose();
		poseStack.translate(0.0F, FEET_Y + UNDER_FEET + bob, 0.0F);
		// Rock around the forward axis and nod a little, like a sword riding the wind.
		poseStack.rotateDegrees(Axis.ZP, roll);
		poseStack.rotateDegrees(Axis.XP, pitch);
		// Lay the sprite flat (its face up) and turn its diagonal blade so the tip points forward (-z).
		poseStack.rotateDegrees(Axis.XP, 90.0F);
		poseStack.rotateDegrees(Axis.ZP, 135.0F);
		poseStack.scale(SCALE, SCALE, SCALE);
		this.item.submit(poseStack, submitNodeCollector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
	}
}
