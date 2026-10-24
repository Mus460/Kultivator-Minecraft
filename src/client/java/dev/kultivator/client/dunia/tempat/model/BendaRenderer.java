package dev.kultivator.client.dunia.tempat.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The base of the DESIGN 38 mechanism renderers (gate, cart, raft, cage door, glyph plate, cliff door, notice board):
 * a cuboid {@link EntityModel} on band textures (32 px material bands), turned to the entity's yaw and drawn in model
 * space exactly like a mob (y down, 24 = the ground), with an optional emissive pass through the vanilla {@code eyes}
 * render type (no post chain). Subclasses fill the state and animate the model; no block is ever touched.
 */
public abstract class BendaRenderer<T extends Entity, S extends BendaRenderer.State, M extends EntityModel<S>> extends EntityRenderer<T, S> {

	/** The common state: the yaw and the strength of the emissive pass. */
	public static class State extends EntityRenderState {
		public float yaw;
		public float pancar;
	}

	protected final M model;
	private final RenderType type;
	private final @Nullable RenderType glow;

	protected BendaRenderer(EntityRendererProvider.Context context, M model, Identifier texture, @Nullable Identifier glowTexture, float shadow) {
		super(context);
		this.model = model;
		this.type = RenderTypes.entityCutout(texture);
		this.glow = glowTexture == null ? null : RenderTypes.eyes(glowTexture);
		this.shadowRadius = shadow;
	}

	/** A cube list on band {@code band} of a band texture. */
	protected static CubeListBuilder band(int band) {
		return CubeListBuilder.create().texOffs(0, band * 32);
	}

	@Override
	public void extractRenderState(T entity, S state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yaw = entity.getYRot(partialTicks);
		state.pancar = 0.0F;
	}

	/** How far the model reaches beyond the entity's box (blocks), for frustum culling of wide models. */
	protected double reach(T entity) {
		return 0.5D;
	}

	@Override
	protected AABB getBoundingBoxForCulling(T entity, float partialTicks) {
		return super.getBoundingBoxForCulling(entity, partialTicks).inflate(reach(entity));
	}

	@Override
	public void submit(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.isInvisible) {
			poseStack.pushPose();
			poseStack.rotateDegrees(Axis.YP, 180.0F - state.yaw);
			poseStack.scale(-1.0F, -1.0F, 1.0F);
			poseStack.translate(0.0F, -1.501F, 0.0F);
			collector.submitModel(model, state, poseStack, type, state.lightCoords, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
			if (glow != null && state.pancar > 0.02F) {
				int alpha = Math.round(255.0F * Math.min(1.0F, state.pancar));
				collector.order(1).submitModel(model, state, poseStack, glow, state.lightCoords, OverlayTexture.NO_OVERLAY, ARGB.color(alpha, 0xFFFFFF),
						null, state.outlineColor);
			}
			submitExtra(state, poseStack, collector);
			poseStack.popPose();
		}
		super.submit(state, poseStack, collector, camera);
	}

	/** Extra geometry in model space (after the model, same transform); none by default. */
	protected void submitExtra(S state, PoseStack poseStack, SubmitNodeCollector collector) {
	}

	/** easeOutBack(1.7). */
	protected static float backOut(float t) {
		float c1 = 1.70158F;
		float c3 = c1 + 1.0F;
		float u = t - 1.0F;
		return 1.0F + c3 * u * u * u + c1 * u * u;
	}

	/** easeInOutSine. */
	protected static float sine(float t) {
		return (float) (-(Math.cos(Math.PI * t) - 1.0D) / 2.0D);
	}
}
