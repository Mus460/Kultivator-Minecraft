package dev.kultivator.client.purba;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.spec.PointClouds;
import dev.kultivator.purba.PrimordialBeastEntity;
import dev.kultivator.technique.spec.dsl.FxLayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;

/**
 * Draws a primordial beast (DESIGN 26.4.2) with no model: a point-cloud silhouette of additive glowing dots turned toward
 * the camera. Kun, the Peng bird and the Zhulong dragon reuse the {@link PointClouds#beast} silhouettes; Taotie is the
 * module's own bronze mask ({@link TaotieMask}). At most 800 dots, half beyond 48 blocks and a third on Minimal effects.
 * The Celah makes it flash pale, a telegraph brightens it, the transition flickers; Zhulong with closed eyes shows only its
 * candle glow. Vanilla {@code lightning()} render type only; nothing here touches the world.
 */
public class PrimordialRenderer extends EntityRenderer<PrimordialBeastEntity, PrimordialRenderer.State> {

	private static final RenderType GLOW = RenderTypes.lightning();
	private static final int MAX_POINTS = 800;
	private static final double FAR_SQ = 48.0D * 48.0D;
	private static final float DOT = 0.14F;
	private static final int[] COLOR = {0x3A7BD5, 0xFF5A2A, 0xC08A3A};
	private static final int PENG = 0x9AD8FF;
	private static final int CANDLE = 0xFFD27A;

	/** What the silhouette needs from the entity. */
	public static class State extends EntityRenderState {
		public int kind;
		public int phase;
		public int flags;
	}

	public PrimordialRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(PrimordialBeastEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.kind = entity.kind();
		state.phase = entity.phase();
		state.flags = entity.flags();
	}

	@Override
	protected AABB getBoundingBoxForCulling(PrimordialBeastEntity entity, float partialTicks) {
		return entity.getBoundingBox().inflate(10.0D);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		boolean peng = state.kind == PrimordialBeastEntity.KUNPENG && state.phase >= 1;
		boolean hidden = (state.flags & PrimordialBeastEntity.FLAG_HIDDEN) != 0;
		float[] points = hidden ? TaotieMask.candle() : points(state.kind, peng);
		float scale = hidden ? 1.6F : switch (state.kind) {
			case PrimordialBeastEntity.KUNPENG -> peng ? 6.0F : 5.0F;
			case PrimordialBeastEntity.ZHULONG -> 6.0F;
			default -> 3.6F;
		};
		int stride = Math.max(1, (int) Math.ceil(points.length / 2.0D / MAX_POINTS));
		if (state.distanceToCameraSq > FAR_SQ) {
			stride *= 2;
		}
		if (EffectQuality.minimal()) {
			stride *= 3;
		}
		int rgb = hidden ? CANDLE : peng ? PENG : COLOR[Mth.clamp(state.kind, 0, 2)];
		float age = state.ageInTicks;
		int alpha = 150;
		if ((state.flags & PrimordialBeastEntity.FLAG_GLOW) != 0) {
			alpha = 220;
			rgb = lighten(rgb, 0.25F);
		}
		if ((state.flags & PrimordialBeastEntity.FLAG_CELAH) != 0) {
			rgb = lighten(rgb, 0.45F + 0.25F * Mth.sin(age * 0.5F));
			alpha = 235;
		}
		if ((state.flags & PrimordialBeastEntity.FLAG_TRANSITION) != 0) {
			alpha = Math.round(90.0F + 120.0F * Math.abs(Mth.sin(age * 0.6F)));
		}
		if (state.kind == PrimordialBeastEntity.TAOTIE && (state.flags & PrimordialBeastEntity.FLAG_MOUTH) != 0) {
			rgb = ARGB.color(0, 0xFF, 0x60, 0x30) & 0xFFFFFF;
		}
		int color = ARGB.color(alpha, rgb);
		int inner = ARGB.color(Math.max(40, alpha / 3), lighten(rgb, 0.5F));
		float wobble = hidden ? 0.0F : 0.04F;
		poseStack.pushPose();
		poseStack.translate(0.0F, state.boundingBoxHeight * 0.5F, 0.0F);
		if (peng) {
			poseStack.rotate(camera.orientation);
		} else {
			poseStack.rotate(Mth.rotationAroundAxis(Mth.Y_AXIS, camera.orientation, new Quaternionf()));
		}
		final int step = stride;
		final float size = scale;
		collector.submitCustomGeometry(poseStack, GLOW, (pose, buffer) -> {
			for (int i = 0; i + 1 < points.length; i += 2 * step) {
				float px = points[i] * size;
				float py = points[i + 1] * size;
				float drift = wobble * Mth.sin(age * 0.15F + i * 0.37F);
				dot(pose, buffer, px + drift, py, DOT, color);
				if ((i / 2) % 7 == 0) {
					dot(pose, buffer, px * 0.92F, py * 0.92F, DOT * 0.7F, inner);
				}
			}
		});
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	private static float[] points(int kind, boolean peng) {
		return switch (kind) {
			case PrimordialBeastEntity.KUNPENG -> PointClouds.beast(peng ? FxLayer.BeastKind.BIRD : FxLayer.BeastKind.KUN);
			case PrimordialBeastEntity.ZHULONG -> PointClouds.beast(FxLayer.BeastKind.DRAGON);
			default -> TaotieMask.mask();
		};
	}

	private static void dot(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float r, int color) {
		buffer.addVertex(pose, x - r, y - r, 0.0F).setColor(color);
		buffer.addVertex(pose, x + r, y - r, 0.0F).setColor(color);
		buffer.addVertex(pose, x + r, y + r, 0.0F).setColor(color);
		buffer.addVertex(pose, x - r, y + r, 0.0F).setColor(color);
	}

	static int lighten(int rgb, float t) {
		float k = Mth.clamp(t, 0.0F, 1.0F);
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		r = Math.round(r + (255 - r) * k);
		g = Math.round(g + (255 - g) * k);
		b = Math.round(b + (255 - b) * k);
		return (r << 16) | (g << 8) | b;
	}
}
