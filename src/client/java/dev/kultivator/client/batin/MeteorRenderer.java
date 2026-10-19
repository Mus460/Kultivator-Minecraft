package dev.kultivator.client.batin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.batin.BatinMeteorEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Draws a meteor of the Hujan Api Meteor calamity (DESIGN 26.6.4) as a large, full-bright, slowly tumbling magma block (the
 * item model of the vanilla block, no texture of its own); its fire tail is particles from the entity's own client tick.
 */
public class MeteorRenderer extends EntityRenderer<BatinMeteorEntity, MeteorRenderer.State> {

	/** Made on first use: item components are not bound yet when entity renderers are created at resource reload. */
	private static ItemStack look;
	private static final float SCALE = 2.6F;

	private final ItemModelResolver itemModelResolver;

	/** The render state: the block's item model and the tumble angle. */
	public static class State extends EntityRenderState {
		public final ItemStackRenderState item = new ItemStackRenderState();
		public float spin;
	}

	public MeteorRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModelResolver = context.getItemModelResolver();
		this.shadowRadius = 0.0F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected int getBlockLightLevel(BatinMeteorEntity entity, BlockPos blockPos) {
		return 15;
	}

	@Override
	public void extractRenderState(BatinMeteorEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		if (look == null) {
			look = new ItemStack(Items.MAGMA_BLOCK);
		}
		itemModelResolver.updateForNonLiving(state.item, look, ItemDisplayContext.GROUND, entity);
		state.spin = (entity.tickCount + partialTicks) * 7.0F;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.0F, 0.7F, 0.0F);
		poseStack.rotate(Axis.YP.rotationDegrees(state.spin));
		poseStack.rotate(Axis.XP.rotationDegrees(state.spin * 0.6F));
		poseStack.scale(SCALE, SCALE, SCALE);
		poseStack.translate(0.0F, -0.25F, 0.0F);
		state.item.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, submitNodeCollector, camera);
	}
}
