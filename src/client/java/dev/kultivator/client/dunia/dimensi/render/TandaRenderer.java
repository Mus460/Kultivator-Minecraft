package dev.kultivator.client.dunia.dimensi.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.dunia.dimensi.DimensiState;
import dev.kultivator.dunia.dimensi.DimensiLevels;
import dev.kultivator.dunia.dimensi.TandaEntity;
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
 * The marks of module D ({@link TandaEntity}) drawn as layered additive quads (vanilla {@code lightning()} render type,
 * POSITION_COLOR, no texture, no shader; Sodium and Iris keep them): the sea gate's turning whirlpool of foam arms and
 * rings on the water, the return gates' twin pillars of light with a veil, Teras Wangxiang's arrival ring and the
 * homesick compass (an arrow on the stone pointing toward the soul's body), the floating Book of Life and Death, the
 * incense altar's smoke glow, herb beds, the Penglai spring's rings, Dizang's halo, and under the Naihe bridge the
 * Wangchuan's slow pale glow (flowing strips on the water and a thin mist above them, in the bridge piece's own frame)
 * with soft halos around the bridge's fourteen soul lanterns (within 48 b, not on Minimal). At most ~170 quads per mark,
 * halved beyond 64 b and on Minimal effects.
 */
public class TandaRenderer extends EntityRenderer<TandaEntity, TandaRenderer.State> {

	private static final RenderType GLOW = RenderTypes.lightning();

	public static class State extends EntityRenderState {
		public int kind;
		public int markState;
		public boolean sea;
		public boolean bawah;
		public float compass = Float.NaN;
		public float yaw;
		public int sky;
	}

	public TandaRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(TandaEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.kind = entity.kind();
		state.markState = entity.state();
		state.sea = DimensiLevels.isLaut(entity.level()) || state.kind == TandaEntity.PUSARAN;
		state.bawah = DimensiLevels.isBawah(entity.level());
		state.yaw = entity.getYRot();
		state.sky = DimensiState.sky();
		state.compass = Float.NaN;
		if (state.kind == TandaEntity.TERAS && DimensiState.soul()) {
			double dx = DimensiState.bodyX() - entity.getX();
			double dz = DimensiState.bodyZ() - entity.getZ();
			if (dx * dx + dz * dz > 1.0D) {
				state.compass = (float) Math.atan2(dz, dx);
			}
		}
	}

	@Override
	protected AABB getBoundingBoxForCulling(TandaEntity entity, float partialTicks) {
		if (entity.kind() == TandaEntity.NAIHE) {
			return entity.getBoundingBox().inflate(26.0D, 8.0D, 26.0D);
		}
		return entity.getBoundingBox().inflate(entity.kind() == TandaEntity.PUSARAN ? 8.0D : 3.0D);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		float detail = (state.distanceToCameraSq > 64.0D * 64.0D ? 0.5F : 1.0F) * (EffectQuality.minimal() ? 0.5F : 1.0F);
		float age = state.ageInTicks;
		switch (state.kind) {
			case TandaEntity.PUSARAN -> collector.submitCustomGeometry(poseStack, GLOW, (pose, buffer) -> whirl(pose, buffer, age, detail));
			case TandaEntity.GERBANG -> gate(state, poseStack, collector, camera, age, detail);
			case TandaEntity.TERAS -> collector.submitCustomGeometry(poseStack, GLOW, (pose, buffer) -> terrace(pose, buffer, state, age, detail));
			case TandaEntity.BUKU -> book(poseStack, collector, camera, age);
			case TandaEntity.ALTAR -> billboard(poseStack, collector, camera, (pose, buffer) -> altar(pose, buffer, age));
			case TandaEntity.HERBA -> billboard(poseStack, collector, camera, (pose, buffer) ->
					halo(pose, buffer, 0.4F, 0x8FE8A0, state.markState > 0 ? 40 : 110, 1.1F, age));
			case TandaEntity.MATA_AIR -> collector.submitCustomGeometry(poseStack, GLOW, (pose, buffer) -> springRings(pose, buffer, age, detail));
			case TandaEntity.DIZANG -> billboard(poseStack, collector, camera, (pose, buffer) ->
					ring(pose, buffer, 0.0F, 1.4F, 0.0F, 1.1F, 1.35F, 0xFFD27A, 150, Math.round(32 * detail), age * 0.01F, false));
			case TandaEntity.CERMIN -> billboard(poseStack, collector, camera, (pose, buffer) ->
					halo(pose, buffer, 1.2F, 0xE0E8FF, 90, 1.4F, age));
			case TandaEntity.NAIHE -> naihe(state, poseStack, collector, camera, age, detail);
			default -> {
			}
		}
		super.submit(state, poseStack, collector, camera);
	}

	/** Geometry turned to face the camera about the vertical axis. */
	private static void billboard(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera,
								  SubmitNodeCollector.CustomGeometryRenderer geometry) {
		poseStack.pushPose();
		poseStack.rotate(Mth.rotationAroundAxis(Mth.Y_AXIS, camera.orientation, new Quaternionf()));
		collector.submitCustomGeometry(poseStack, GLOW, geometry);
		poseStack.popPose();
	}

	// ================================================================== the kinds

	/** Six foam arms spiralling inward on the water and three dashed rings turning against them. */
	private static void whirl(PoseStack.Pose pose, VertexConsumer buffer, float age, float detail) {
		int arms = 6;
		int steps = Math.max(6, Math.round(16 * detail));
		float spin = age * 0.06F;
		for (int a = 0; a < arms; a++) {
			float base = spin + a * Mth.TWO_PI / arms;
			for (int i = 0; i < steps; i++) {
				float r0 = 0.6F + 6.0F * i / steps;
				float r1 = 0.6F + 6.0F * (i + 1) / steps;
				float t0 = base + r0 * 0.55F;
				float t1 = base + r1 * 0.55F;
				float w = 0.18F + 0.32F * i / steps;
				int alpha = Math.round(170.0F * (1.0F - (float) i / steps) + 30.0F);
				int color = ARGB.color(alpha, i % 3 == 0 ? 0xFFFFFF : 0x9FF0F8);
				flatQuad(pose, buffer, t0, r0, t1, r1, w, 0.02F + a * 0.002F, color);
			}
		}
		for (int k = 0; k < 3; k++) {
			float r = 1.8F + k * 1.8F;
			int dashes = Math.max(8, Math.round(24 * detail));
			for (int i = 0; i < dashes; i += 2) {
				float t0 = -spin * (1.3F + k * 0.2F) + i * Mth.TWO_PI / dashes;
				float t1 = t0 + Mth.TWO_PI / dashes;
				flatQuad(pose, buffer, t0, r, t1, r, 0.12F, 0.05F, ARGB.color(90 - k * 20, 0xE8FFFF));
			}
		}
		// the dark-blue heart of the whirl, faint
		for (int i = 0; i < 12; i++) {
			float t0 = i * Mth.TWO_PI / 12.0F;
			float t1 = (i + 1) * Mth.TWO_PI / 12.0F;
			flatQuad(pose, buffer, t0, 0.3F, t1, 0.3F, 0.5F, 0.01F, ARGB.color(70, 0x2060A0));
		}
	}

	// ================================================================== the Wangchuan under the Naihe bridge

	/** The template's river relative to the naihe anchor (tools/gen_dunia_dimensi.py jembatan_naihe, anchor 23.5 9 20.5). */
	private static final float RIVER_X0 = -22.5F;
	private static final float RIVER_X1 = 23.5F;
	private static final float RIVER_Z0 = -10.5F;
	private static final float RIVER_Z1 = 9.5F;
	private static final float RIVER_Y = -3.96F;

	/** The river's glow in the piece's frame and the lantern halos along the bridge rails. */
	private static void naihe(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, float age, float detail) {
		boolean eclipse = state.sky == 2;
		int water = eclipse ? 0xB05060 : 0x6FA8C8;
		int mist = eclipse ? 0xD08080 : 0xC9D8FF;
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, -state.yaw);
		int segments = Math.max(6, Math.round(16 * detail));
		int strips = Math.max(3, Math.round(6 * detail));
		collector.submitCustomGeometry(poseStack, GLOW, (pose, buffer) -> {
			float segW = (RIVER_X1 - RIVER_X0) / segments;
			float stripW = (RIVER_Z1 - RIVER_Z0) / strips;
			for (int s = 0; s < strips; s++) {
				float z0 = RIVER_Z0 + s * stripW + stripW * 0.15F;
				float z1 = z0 + stripW * 0.7F;
				for (int i = 0; i < segments; i++) {
					float x0 = RIVER_X0 + i * segW;
					float x1 = x0 + segW;
					// the current flows along the river: a slow wave of light moving down its length
					float wave = 0.5F + 0.5F * Mth.sin(age * 0.045F - i * 0.55F + s * 1.3F);
					int alpha = Math.round(14 + 34 * wave * wave);
					flat(pose, buffer, x0, z0, x1, z1, RIVER_Y, ARGB.color(alpha, water));
				}
			}
			// a thin mist drifting the other way, a little above the water
			for (int i = 0; i < segments; i += 2) {
				float x0 = RIVER_X0 + i * segW;
				float x1 = x0 + segW * 2.0F;
				float drift = 0.5F + 0.5F * Mth.sin(age * 0.03F + i * 0.7F);
				flat(pose, buffer, x0, RIVER_Z0 + 2.0F, x1, RIVER_Z1 - 2.0F, RIVER_Y + 0.45F, ARGB.color(Math.round(8 + 14 * drift), mist));
			}
		});
		if (detail >= 0.99F && state.distanceToCameraSq < 48.0D * 48.0D) {
			// the bridge's soul lanterns: x 20 / 26 at z 7, 11 ... 31, two blocks above the arched deck (F 5, rise 3)
			for (int z = 7; z <= 31; z += 4) {
				double t = (z - 7) / 25.0D;
				int h = 5 + (int) Math.round(3.0D * Math.sin(Math.PI * t));
				float y = h + 2.5F - 9.0F;
				float lz = z + 0.5F - 20.5F;
				for (float lx : new float[] {-3.0F, 3.0F}) {
					poseStack.pushPose();
					poseStack.translate(lx, y, lz);
					poseStack.rotateDegrees(Axis.YP, state.yaw);
					poseStack.rotate(Mth.rotationAroundAxis(Mth.Y_AXIS, camera.orientation, new Quaternionf()));
					float flick = 0.85F + 0.15F * Mth.sin(age * 0.2F + z + lx);
					collector.submitCustomGeometry(poseStack, GLOW, (pose, buffer) -> halo(pose, buffer, 0.0F, eclipse ? 0xFF7060 : 0x9FD8FF,
							Math.round(70 * flick), 0.9F, age));
					poseStack.popPose();
				}
			}
		}
		poseStack.popPose();
	}

	/** A horizontal quad (x0, z0)-(x1, z1) at height {@code y}. */
	private static void flat(PoseStack.Pose pose, VertexConsumer buffer, float x0, float z0, float x1, float z1, float y, int color) {
		buffer.addVertex(pose, x0, y, z0).setColor(color);
		buffer.addVertex(pose, x0, y, z1).setColor(color);
		buffer.addVertex(pose, x1, y, z1).setColor(color);
		buffer.addVertex(pose, x1, y, z0).setColor(color);
	}

	/** A quad lying on the water between polar points (t0, r0) and (t1, r1), {@code w} wide, at height {@code y}. */
	private static void flatQuad(PoseStack.Pose pose, VertexConsumer buffer, float t0, float r0, float t1, float r1, float w, float y, int color) {
		float x0 = Mth.cos(t0) * r0;
		float z0 = Mth.sin(t0) * r0;
		float x1 = Mth.cos(t1) * r1;
		float z1 = Mth.sin(t1) * r1;
		float dx = x1 - x0;
		float dz = z1 - z0;
		float len = Mth.sqrt(dx * dx + dz * dz);
		if (len < 1.0E-4F) {
			return;
		}
		float nx = -dz / len * w * 0.5F;
		float nz = dx / len * w * 0.5F;
		buffer.addVertex(pose, x0 - nx, y, z0 - nz).setColor(color);
		buffer.addVertex(pose, x0 + nx, y, z0 + nz).setColor(color);
		buffer.addVertex(pose, x1 + nx, y, z1 + nz).setColor(color);
		buffer.addVertex(pose, x1 - nx, y, z1 - nz).setColor(color);
	}

	/** Twin pillars of light, the lintel and a breathing veil between them. */
	private static void gate(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, float age, float detail) {
		int rgb = state.sea ? 0x8FF0F8 : 0xC9D8FF;
		float breath = 0.7F + 0.3F * Mth.sin(age * 0.08F);
		billboard(poseStack, collector, camera, (pose, buffer) -> {
			for (int side = -1; side <= 1; side += 2) {
				float x = side * 1.1F;
				for (int layer = 0; layer < 3; layer++) {
					float w = 0.08F + layer * 0.09F;
					int alpha = Math.round((200 - layer * 60) * breath);
					rect(pose, buffer, x - w, 0.0F, x + w, 3.2F, ARGB.color(alpha, rgb));
				}
			}
			rect(pose, buffer, -1.3F, 3.1F, 1.3F, 3.3F, ARGB.color(Math.round(180 * breath), rgb));
			int bands = Math.max(3, Math.round(8 * detail));
			for (int i = 0; i < bands; i++) {
				float y0 = i * 3.1F / bands;
				float y1 = (i + 1) * 3.1F / bands;
				float shimmer = 0.5F + 0.5F * Mth.sin(age * 0.12F + i * 0.9F);
				rect(pose, buffer, -1.0F, y0, 1.0F, y1, ARGB.color(Math.round(40 + 45 * shimmer * breath), rgb));
			}
		});
	}

	/** The arrival ring on the terrace and, for a soul, the homesick compass pointing to its body. */
	private static void terrace(PoseStack.Pose pose, VertexConsumer buffer, State state, float age, float detail) {
		int dashes = Math.max(12, Math.round(40 * detail));
		float spin = age * 0.01F;
		for (int i = 0; i < dashes; i += 2) {
			float t0 = spin + i * Mth.TWO_PI / dashes;
			float t1 = t0 + Mth.TWO_PI / dashes;
			flatQuad(pose, buffer, t0, 3.0F, t1, 3.0F, 0.16F, 0.03F, ARGB.color(120, 0xE8D9A8));
			flatQuad(pose, buffer, -t0 * 1.5F, 1.6F, -t1 * 1.5F, 1.6F, 0.1F, 0.035F, ARGB.color(90, 0xC9D8FF));
		}
		if (!Float.isNaN(state.compass)) {
			float t = state.compass;
			float pulse = 0.6F + 0.4F * Mth.sin(age * 0.15F);
			int color = ARGB.color(Math.round(200 * pulse), 0xC9D8FF);
			flatQuad(pose, buffer, t, 0.4F, t, 2.6F, 0.22F, 0.05F, color);
			flatQuad(pose, buffer, t - 0.25F, 2.2F, t, 2.9F, 0.16F, 0.05F, color);
			flatQuad(pose, buffer, t + 0.25F, 2.2F, t, 2.9F, 0.16F, 0.05F, color);
		}
	}

	/** The open Book of Life and Death floating over its lectern: two pages, a red seal and orbiting glyphs. */
	private static void book(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, float age) {
		float bob = Mth.sin(age * 0.07F) * 0.08F;
		poseStack.pushPose();
		poseStack.translate(0.0F, 0.6F + bob, 0.0F);
		poseStack.rotate(Mth.rotationAroundAxis(Mth.Y_AXIS, camera.orientation, new Quaternionf()));
		collector.submitCustomGeometry(poseStack, GLOW, (pose, buffer) -> {
			int page = ARGB.color(150, 0xF0E6C8);
			buffer.addVertex(pose, -0.55F, 0.0F, 0.0F).setColor(page);
			buffer.addVertex(pose, 0.0F, -0.08F, -0.12F).setColor(page);
			buffer.addVertex(pose, 0.0F, 0.42F, -0.12F).setColor(page);
			buffer.addVertex(pose, -0.55F, 0.5F, 0.0F).setColor(page);
			buffer.addVertex(pose, 0.0F, -0.08F, -0.12F).setColor(page);
			buffer.addVertex(pose, 0.55F, 0.0F, 0.0F).setColor(page);
			buffer.addVertex(pose, 0.55F, 0.5F, 0.0F).setColor(page);
			buffer.addVertex(pose, 0.0F, 0.42F, -0.12F).setColor(page);
			int ink = ARGB.color(170, 0xC0281C);
			rect(pose, buffer, 0.18F, 0.12F, 0.38F, 0.32F, ink);
			for (int i = 0; i < 6; i++) {
				rect(pose, buffer, -0.48F, 0.08F + i * 0.06F, -0.1F, 0.1F + i * 0.06F, ARGB.color(110, 0x402010));
			}
			for (int i = 0; i < 8; i++) {
				float t = age * 0.05F + i * Mth.TWO_PI / 8.0F;
				float x = Mth.cos(t) * 0.9F;
				float y = 0.25F + Mth.sin(t * 2.0F) * 0.25F;
				rect(pose, buffer, x - 0.04F, y - 0.04F, x + 0.04F, y + 0.04F, ARGB.color(200, 0xFFD27A));
			}
		});
		poseStack.popPose();
	}

	/** Incense: three thin flickering streaks rising and a small warm halo. */
	private static void altar(PoseStack.Pose pose, VertexConsumer buffer, float age) {
		for (int i = 0; i < 3; i++) {
			float x = -0.25F + i * 0.25F;
			float flick = 0.6F + 0.4F * Mth.sin(age * 0.3F + i * 2.0F);
			float top = 0.9F + 0.5F * flick;
			rect(pose, buffer, x - 0.015F, 0.5F, x + 0.015F, top, ARGB.color(Math.round(170 * flick), 0xE0D0FF));
			rect(pose, buffer, x - 0.05F, 0.45F, x + 0.05F, 0.55F, ARGB.color(220, 0xFF8A3A));
		}
		halo(pose, buffer, 0.6F, 0xB9A3D9, 70, 0.9F, age);
	}

	/** Three rings expanding on the spring and fading. */
	private static void springRings(PoseStack.Pose pose, VertexConsumer buffer, float age, float detail) {
		int dashes = Math.max(12, Math.round(36 * detail));
		for (int k = 0; k < 3; k++) {
			float phase = ((age * 0.02F) + k / 3.0F) % 1.0F;
			float r = 0.4F + phase * 2.2F;
			int alpha = Math.round(180 * (1.0F - phase));
			for (int i = 0; i < dashes; i++) {
				float t0 = i * Mth.TWO_PI / dashes;
				float t1 = (i + 1) * Mth.TWO_PI / dashes;
				flatQuad(pose, buffer, t0, r, t1, r, 0.08F, 0.03F, ARGB.color(alpha, 0xFFF4D0));
			}
		}
	}

	/** A soft glow disc of concentric quads facing the camera at height {@code y}. */
	private static void halo(PoseStack.Pose pose, VertexConsumer buffer, float y, int rgb, int alpha, float radius, float age) {
		float breath = 0.8F + 0.2F * Mth.sin(age * 0.1F);
		for (int i = 0; i < 4; i++) {
			float r = radius * (0.35F + i * 0.22F) * breath;
			rect(pose, buffer, -r, y - r, r, y + r, ARGB.color(Math.max(8, alpha / (i + 2)), rgb));
		}
	}

	/** A camera-facing ring of {@code n} segments between radii r0 and r1 around (cx, cy). */
	private static void ring(PoseStack.Pose pose, VertexConsumer buffer, float cx, float cy, float z, float r0, float r1, int rgb, int alpha,
							 int n, float spin, boolean dashed) {
		int color = ARGB.color(alpha, rgb);
		for (int i = 0; i < n; i++) {
			if (dashed && i % 2 == 1) {
				continue;
			}
			float t0 = spin + i * Mth.TWO_PI / n;
			float t1 = spin + (i + 1) * Mth.TWO_PI / n;
			buffer.addVertex(pose, cx + Mth.cos(t0) * r0, cy + Mth.sin(t0) * r0, z).setColor(color);
			buffer.addVertex(pose, cx + Mth.cos(t0) * r1, cy + Mth.sin(t0) * r1, z).setColor(color);
			buffer.addVertex(pose, cx + Mth.cos(t1) * r1, cy + Mth.sin(t1) * r1, z).setColor(color);
			buffer.addVertex(pose, cx + Mth.cos(t1) * r0, cy + Mth.sin(t1) * r0, z).setColor(color);
		}
	}

	private static void rect(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float x1, float y1, int color) {
		buffer.addVertex(pose, x0, y0, 0.0F).setColor(color);
		buffer.addVertex(pose, x1, y0, 0.0F).setColor(color);
		buffer.addVertex(pose, x1, y1, 0.0F).setColor(color);
		buffer.addVertex(pose, x0, y1, 0.0F).setColor(color);
	}
}
