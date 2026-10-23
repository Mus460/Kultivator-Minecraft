package dev.kultivator.client.sekte.langit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.anim.form.FormModels;
import dev.kultivator.client.disciple.DiscipleLook;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.client.epic.kosmik.KosmikTypes;
import dev.kultivator.sekte.langit.api.LangitIds;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * The disciple poses of the sky sect's activities 25-27 (DESIGN 37.5.3, 37.5.7; {@link LangitIds}):
 * <ul>
 * <li>{@code ACT_TERBANG_PEDANG} 25, the sword patrol: standing on the sword, feet together, the body leaning into the
 * wind, the right hand raised before the chest in the two-finger sword seal, the left hand folded behind the back, body
 * and head swaying with the flight. It replaces the walk cycle (the server moves the flyer with {@code setPos}, so the
 * vanilla walk animation would pedal the legs). {@link SwordLayer} draws the 3D natal-sword model flat under the soles
 * over a jade sword-light (Minimal: the sword-light only, the "simple sword" of 37.5.3).</li>
 * <li>{@code ACT_JAGA_TEPI} 26, the rim guard: hands clasped behind the back, chin up, looking slowly along the rim.</li>
 * <li>{@code ACT_MEMANDANG} 27, watching the sea at dawn or dusk: seated (the meditation sit) the head lifts toward the
 * horizon; standing, one hand at the chin while the other rests behind the back.</li>
 * </ul>
 * DESIGN 37 asked for {@code SektePoses.register}, but that hook only accepts 13-24 (it throws for 25-27) and the
 * {@code DutyPoses} blend only runs while the disciple stands still; so the poses are applied from a client mixin at
 * every return of {@code DutyPoses.apply} ({@code client.mixin.LangitDutyPosesMixin}) and the sword by a render layer
 * added through Fabric's {@code LivingEntityRenderLayerRegistrationCallback}. No DESIGN 36 file is edited.
 */
public final class LangitPoses {

	private static final float BLEND_TICKS = 6.0F;
	private static final Map<Integer, float[]> BLENDS = new HashMap<>();

	/** From the mixin at every return of {@code DutyPoses.apply}. */
	public static void apply(PlayerModel model, AvatarRenderState avatar) {
		if (!(avatar instanceof DiscipleRenderState state)) {
			return;
		}
		int activity = state.activity;
		if (activity < LangitIds.ACT_TERBANG_PEDANG || activity > LangitIds.ACT_MEMANDANG || state.castActive) {
			if (!BLENDS.isEmpty()) {
				BLENDS.remove(state.id);
			}
			return;
		}
		float t = state.ageInTicks;
		if (activity == LangitIds.ACT_TERBANG_PEDANG) {
			fly(model, t);
			return;
		}
		boolean still = (state.flags & DiscipleLook.FIGHTING) == 0 && state.walkAnimationSpeed <= 0.1F;
		float s = blend(state.id, still, t);
		if (s <= 0.0F) {
			return;
		}
		if (activity == LangitIds.ACT_JAGA_TEPI) {
			guard(model, s, t);
		} else if (state.seated > 0.5F) {
			model.head.xRot = Mth.lerp(s, model.head.xRot, -0.22F + Mth.sin(t * 0.02F) * 0.04F);
		} else {
			watch(model, s, t);
		}
	}

	private static void fly(PlayerModel model, float t) {
		float sway = Mth.sin(t * 0.09F);
		float gust = Mth.sin(t * 0.23F + 1.1F) * 0.04F;
		// the legs together and straight, braced a little apart in the stance
		set(model.rightLeg, 0.05F, 0.0F, 0.06F);
		set(model.leftLeg, -0.05F, 0.0F, -0.06F);
		// the body leans into the wind
		model.body.xRot = 0.18F + gust;
		model.body.zRot = sway * 0.05F;
		// the right hand raised before the chest in the sword seal, the left behind the back
		set(model.rightArm, -1.35F + gust, -0.55F, 0.12F + sway * 0.03F);
		set(model.leftArm, 0.55F, 0.25F, 0.35F);
		model.head.xRot = 0.1F + sway * 0.03F;
		model.head.zRot = -sway * 0.04F;
	}

	private static void guard(PlayerModel model, float s, float t) {
		lerp(model.rightArm, s, 0.42F, 0.0F, 0.32F);
		lerp(model.leftArm, s, 0.42F, 0.0F, -0.32F);
		model.head.xRot = Mth.lerp(s, model.head.xRot, -0.12F);
		model.head.yRot = Mth.lerp(s, model.head.yRot, Mth.sin(t * 0.015F) * 0.5F);
	}

	private static void watch(PlayerModel model, float s, float t) {
		lerp(model.rightArm, s, -2.05F, -0.55F, 0.0F);
		lerp(model.leftArm, s, 0.4F, 0.0F, -0.3F);
		model.head.xRot = Mth.lerp(s, model.head.xRot, -0.18F + Mth.sin(t * 0.03F) * 0.03F);
	}

	private static void set(ModelPart part, float x, float y, float z) {
		part.xRot = x;
		part.yRot = y;
		part.zRot = z;
	}

	private static void lerp(ModelPart part, float s, float x, float y, float z) {
		part.xRot = Mth.lerp(s, part.xRot, x);
		part.yRot = Mth.lerp(s, part.yRot, y);
		part.zRot = Mth.lerp(s, part.zRot, z);
	}

	private static float blend(int id, boolean on, float now) {
		float[] b = BLENDS.get(id);
		if (b == null) {
			b = new float[] {on ? 1.0F : 0.0F, now - BLEND_TICKS};
			BLENDS.put(id, b);
		} else if ((b[0] > 0.5F) != on) {
			float current = value(b, now);
			b[0] = on ? 1.0F : 0.0F;
			b[1] = now - (on ? current : 1.0F - current) * BLEND_TICKS;
		} else if (now < b[1]) {
			b[1] = now - BLEND_TICKS;
		}
		if (BLENDS.size() > 256) {
			BLENDS.clear();
		}
		return value(b, now);
	}

	private static float value(float[] b, float now) {
		float t = Mth.clamp((now - b[1]) / BLEND_TICKS, 0.0F, 1.0F);
		float v = b[0] > 0.5F ? t : 1.0F - t;
		return v * v * (3.0F - 2.0F * v);
	}

	static void clear() {
		BLENDS.clear();
	}

	/**
	 * The patrol sword under a flying disciple's feet: the natal-sword model lying flat, tip forward, bobbing and rocking,
	 * over a jade sword-light ({@code pedang.png}, emissive) a little longer than the blade.
	 */
	public static final class SwordLayer extends RenderLayer<DiscipleRenderState, PlayerModel> {

		/** The model's feet in layer space (24 model pixels below the neck pivot). */
		private static final float FEET_Y = 24.0F / 16.0F;
		/** (x, y, z) corners in model space: the hilt end at z +0.675, the tip at z -0.675, 0.2 to each side. */
		private static final float[] QUAD = {-0.2F, 0.06F, 0.675F, 0.2F, 0.06F, 0.675F, 0.2F, 0.06F, -0.675F, -0.2F, 0.06F, -0.675F};
		private static final float[] U = {0.0F, 0.0F, 1.0F, 1.0F};
		private static final float[] V = {0.0F, 1.0F, 1.0F, 0.0F};
		private static final int GLOW = 0xD9B8FFE6;
		private final SubmitNodeCollector.CustomGeometryRenderer glow = this::glow;

		public SwordLayer(RenderLayerParent<DiscipleRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, DiscipleRenderState state, float yRot, float xRot) {
			if (state.isInvisible || state.activity != LangitIds.ACT_TERBANG_PEDANG) {
				return;
			}
			float age = state.ageInTicks;
			float bob = Mth.sin(age * 0.11F) * 0.035F;
			poseStack.pushPose();
			poseStack.translate(0.0F, FEET_Y + 0.07F + bob, 0.0F);
			poseStack.rotateDegrees(Axis.ZP, Mth.sin(age * 0.067F) * 3.5F);
			poseStack.rotateDegrees(Axis.XP, Mth.sin(age * 0.05F + 1.3F) * 1.5F);
			if (!EffectQuality.minimal()) {
				int light = LightCoordsUtil.addSmoothBlockEmission(lightCoords, 0.6F);
				// the piece stands up its +y; turned 90 degrees about X its tip points forward (model -z)
				FormModels.submitPiece("natal_sword", poseStack, collector, light, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 90.0F, 0.0F, 1.7F);
			}
			// the sword-light: a flat glowing quad just under the blade, tip forward (the writer runs later, so it is fixed)
			collector.submitCustomGeometry(poseStack, KosmikTypes.glow(AwanSink.PEDANG), glow);
			poseStack.popPose();
		}

		private void glow(PoseStack.Pose pose, VertexConsumer buffer) {
			for (int i = 0; i < 4; i++) {
				buffer.addVertex(pose, QUAD[i * 3], QUAD[i * 3 + 1], QUAD[i * 3 + 2])
						.setColor(GLOW)
						.setUv(U[i], V[i])
						.setOverlay(OverlayTexture.NO_OVERLAY)
						.setLight(LightCoordsUtil.FULL_BRIGHT)
						.setNormal(pose, 0.0F, -1.0F, 0.0F);
			}
		}
	}

	private LangitPoses() {
	}
}
