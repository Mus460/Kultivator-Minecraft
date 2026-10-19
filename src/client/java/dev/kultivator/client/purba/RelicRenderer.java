package dev.kultivator.client.purba;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kultivator.purba.PrimordialRelicEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/**
 * Draws a primordial relic (DESIGN 26.4.2): Mata Angin as a spinning teal wind eye, the Lilin as a flickering candle flame,
 * both as additive glowing dots facing the camera ({@code lightning()} render type, no texture).
 */
public class RelicRenderer extends EntityRenderer<PrimordialRelicEntity, RelicRenderer.State> {

	private static final RenderType GLOW = RenderTypes.lightning();
	private static final float DOT = 0.06F;

	public static class State extends EntityRenderState {
		public int kind;
		public float health;
	}

	public RelicRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(PrimordialRelicEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.kind = entity.kind();
		state.health = entity.getMaxHealth() <= 0.0F ? 1.0F : entity.getHealth() / entity.getMaxHealth();
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		boolean lilin = state.kind == PrimordialRelicEntity.LILIN;
		float[] points = lilin ? TaotieMask.candle() : TaotieMask.windEye();
		float age = state.ageInTicks;
		int rgb = lilin ? 0xFFC060 : 0x8EF0E0;
		int alpha = lilin ? Math.round(180.0F + 60.0F * Mth.sin(age * 0.7F)) : Math.round(120.0F + 110.0F * state.health);
		int color = ARGB.color(Mth.clamp(alpha, 40, 255), rgb);
		float spin = lilin ? 0.0F : age * 0.12F;
		float cos = Mth.cos(spin);
		float sin = Mth.sin(spin);
		float size = lilin ? 0.8F : 0.75F;
		poseStack.pushPose();
		poseStack.translate(0.0F, state.boundingBoxHeight * 0.5F, 0.0F);
		poseStack.rotate(Mth.rotationAroundAxis(Mth.Y_AXIS, camera.orientation, new Quaternionf()));
		collector.submitCustomGeometry(poseStack, GLOW, (pose, buffer) -> {
			for (int i = 0; i + 1 < points.length; i += 2) {
				float x = points[i] * size;
				float y = points[i + 1] * size;
				float rx = x * cos - y * sin;
				float ry = x * sin + y * cos;
				dot(pose, buffer, rx, ry, color);
			}
		});
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	private static void dot(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, int color) {
		buffer.addVertex(pose, x - DOT, y - DOT, 0.0F).setColor(color);
		buffer.addVertex(pose, x + DOT, y - DOT, 0.0F).setColor(color);
		buffer.addVertex(pose, x + DOT, y + DOT, 0.0F).setColor(color);
		buffer.addVertex(pose, x - DOT, y + DOT, 0.0F).setColor(color);
	}
}
