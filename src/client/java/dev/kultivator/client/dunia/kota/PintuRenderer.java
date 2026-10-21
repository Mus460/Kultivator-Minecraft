package dev.kultivator.client.dunia.kota;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.dunia.kota.KotaPintuEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The hidden door of the Pasar Gelap (DESIGN 34.5.1, rule W2): drawn as two bookshelf blocks (the coffin shop's back wall)
 * with an iron-banded frame strip, sliding to the door's right over 1.2 s and sinking a hand's width into the wall when it
 * opens. A barred door shows a crossbar of dark oak. Only an entity model: no block is ever placed.
 */
public class PintuRenderer extends EntityRenderer<KotaPintuEntity, PintuRenderer.State> {

	/** The door's render state: the two block models, the slide, the yaw and the bar. */
	public static final class State extends EntityRenderState {
		final BlockModelRenderState panel = new BlockModelRenderState();
		final BlockModelRenderState band = new BlockModelRenderState();
		final BlockModelRenderState bar = new BlockModelRenderState();
		float slide;
		float yaw;
		boolean barred;
	}

	private static final BlockState PANEL = Blocks.BOOKSHELF.defaultBlockState();
	private static final BlockState BAND = Blocks.IRON_BLOCK.defaultBlockState();
	private static final BlockState BAR = Blocks.DARK_OAK_PLANKS.defaultBlockState();

	private final BlockModelResolver blocks;

	public PintuRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.blocks = context.getBlockModelResolver();
		this.shadowRadius = 0.0F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(KotaPintuEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		blocks.update(state.panel, PANEL, DisplayRenderer.BLOCK_DISPLAY_CONTEXT);
		blocks.update(state.band, BAND, DisplayRenderer.BLOCK_DISPLAY_CONTEXT);
		blocks.update(state.bar, BAR, DisplayRenderer.BLOCK_DISPLAY_CONTEXT);
		state.slide = entity.slide(partialTicks);
		state.yaw = entity.getYRot();
		state.barred = entity.barred();
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, -state.yaw);
		// slide right (local +x) and a little into the wall (local +z)
		float sink = Math.min(1.0F, state.slide * 3.0F) * 0.08F;
		poseStack.translate(state.slide * 0.98F, 0.0F, sink);
		for (int y = 0; y < 2; y++) {
			poseStack.pushPose();
			poseStack.translate(-0.5F, y, -0.5F);
			state.panel.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
			poseStack.popPose();
		}
		// two thin iron bands across the panel
		for (int i = 0; i < 2; i++) {
			poseStack.pushPose();
			poseStack.translate(-0.52F, 0.35F + i * 1.2F, -0.53F);
			poseStack.scale(1.04F, 0.08F, 0.04F);
			state.band.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
			poseStack.popPose();
		}
		poseStack.popPose();
		if (state.barred) {
			poseStack.pushPose();
			poseStack.rotateDegrees(Axis.YP, -state.yaw);
			poseStack.translate(-0.65F, 1.0F, -0.62F);
			poseStack.rotateDegrees(Axis.ZP, -28.0F);
			poseStack.scale(1.5F, 0.14F, 0.12F);
			state.bar.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
			poseStack.popPose();
		}
		super.submit(state, poseStack, collector, camera);
	}
}
