package dev.kultivator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.data.Realm;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The cultivator's sword carried on the back from Qi Refining: the synced sword item drawn with its real item model,
 * diagonally across the back with the hilt over the right shoulder, following the body (crouching, meditation seat).
 * Hidden while the sword flies under the feet, while that same sword is held in a hand, for invisible players and when
 * the player switched it off.
 */
public class BackSwordLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

	/** Where the blade's middle sits on the back (model pixels from the neck pivot; +z is behind the body). */
	private static final float CENTER_Y = 6.5F / 16.0F;
	/** Outside the robe, the skin's jacket layer and a chestplate. */
	private static final float CENTER_Z = 3.5F / 16.0F;
	/** Steeper than the sprite's own 45 degrees, so it reads as a sword slung on the back. */
	private static final float TILT = 15.0F;
	private static final float SCALE = 0.8F;

	private final ItemModelResolver itemModelResolver;
	private final ItemStackRenderState item = new ItemStackRenderState();

	public BackSwordLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, EntityRendererProvider.Context context) {
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
		if (!visual.has(VisualState.SHOW_BACK_SWORD) || visual.has(VisualState.FLYING_SWORD) || !CultivatorLook.atLeast(visual, Realm.QI_REFINING)) {
			return;
		}
		ItemStack sword = CultivatorLook.sword(visual.swordItem());
		Player player = CultivatorLook.player(state);
		if (sword.isEmpty() || player == null || CultivatorLook.holds(player, sword)) {
			return;
		}
		this.itemModelResolver.updateForLiving(this.item, sword, ItemDisplayContext.FIXED, player);
		if (this.item.isEmpty()) {
			return;
		}
		PlayerModel model = this.getParentModel();
		poseStack.pushPose();
		model.root().translateAndRotate(poseStack);
		model.body.translateAndRotate(poseStack);
		poseStack.translate(0.0F, CENTER_Y, CENTER_Z);
		// The fixed item pose shows the sprite with the hilt at the lower right; turned around (Y 180) it lies against
		// the back with the hilt up at the right shoulder and the tip down at the left hip.
		poseStack.rotateDegrees(Axis.ZP, TILT);
		poseStack.rotateDegrees(Axis.YP, 180.0F);
		poseStack.scale(SCALE, SCALE, SCALE);
		this.item.submit(poseStack, submitNodeCollector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
	}
}
