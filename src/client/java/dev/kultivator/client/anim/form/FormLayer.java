package dev.kultivator.client.anim.form;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.anim.ClientForms;
import dev.kultivator.client.anim.PoseRenderData;
import dev.kultivator.client.anim.model.FxModels;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;

/**
 * The transformation forms and worn buff looks on players and disciples (DESIGN 27.4, polish plan 3.6): 3D pieces
 * attached to the model parts, so cast poses, walking and meditation move them. Forms: the golden ape (head, torso,
 * long arms and fur skirt replace the upper body), three heads and six arms (two extra heads, four extra arms copying the
 * real arms, each with a small weapon), dragonblood and the disciple dragon form (horns, wings, a swaying tail that
 * sweeps on the art's extra beat), the ancient god (a bronze sheen and up to nine brow stars, lit by stage), the dharma
 * armour (helm, pauldrons, breastplate and a turning halo), heaven fire 1-3 (a flame mantle and shoulder flames), the
 * thunder venerable (a ring of eight drums behind the back plus lightning wings) and the seventy-two bodies (a tiger,
 * hawk, koi, bee or temple drawn instead of the player, bobbing with the walk). Worn looks: golden, violet-crackle and
 * burning-vein sheens, back wings (kunpeng, lightning, flame, wind-thunder, Lei Zhenzi), the golden cudgel, ice claws,
 * the azure sword glow and iron plates. Minimal quality keeps the forms (they are the identity) but drops the extra
 * arms' weapons, the drum ring and the outer sheens.
 */
public class FormLayer<S extends AvatarRenderState, M extends PlayerModel> extends RenderLayer<S, M> {

	private static final int GOLD = 0xFFD54A;
	private static final int BRONZE = 0xD08A40;
	private static final int VIOLET = 0xC7A2FF;
	private static final int RED = 0xFF4A2A;

	public FormLayer(RenderLayerParent<S, M> renderer, EntityRendererProvider.Context context) {
		super(renderer);
	}

	public FormLayer(RenderLayerParent<S, M> renderer) {
		super(renderer);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
		if (state.isInvisible) {
			return;
		}
		ClientForms.View view = PoseRenderData.form(state);
		if (view.id().isEmpty() && view.effects().isEmpty()) {
			return;
		}
		M model = this.getParentModel();
		float age = state.ageInTicks;
		float grow = view.id().isEmpty() ? 1.0F : Mth.clamp(view.age() / 8.0F, 0.0F, 1.0F);
		String form = view.id();
		if (form.startsWith("seventy_two:")) {
			beastBody(form.substring("seventy_two:".length()), poseStack, collector, light, state, model, grow);
		} else {
			switch (form) {
				case "golden_ape" -> {
					onPart(model, model.head, poseStack, collector, light, "ape_head", grow);
					onPart(model, model.body, poseStack, collector, light, "ape_torso", grow);
					onPart(model, model.body, poseStack, collector, light, "ape_fur_skirt", grow);
					onPart(model, model.rightArm, poseStack, collector, light, "ape_arm_r", grow);
					onPart(model, model.leftArm, poseStack, collector, light, "ape_arm_l", grow);
				}
				case "three_heads_six_arms" -> threeHeads(poseStack, collector, light, state, model, grow, age);
				case "dragonblood" -> dragon(poseStack, collector, light, model, grow, age, "", state.id, false);
				case "dragon_form" -> dragon(poseStack, collector, light, model, grow, age, "_lavender", state.id, true);
				case "ancient_god" -> {
					sheen(poseStack, collector, state, BRONZE, 60.0F, 100.0F, 0.08F);
					browStars(poseStack, collector, model, Math.max(1, view.stage()), grow);
				}
				case "dharma" -> dharma(poseStack, collector, light, model, grow, age);
				case "heaven_fire_1", "heaven_fire_2", "heaven_fire_3" -> heavenFire(Math.max(form.charAt(form.length() - 1) - '0', view.stage()),
						poseStack, collector, light, model, grow, age);
				case "heaven_fire" -> heavenFire(Math.max(1, view.stage()), poseStack, collector, light, model, grow, age);
				case "thunder_venerable" -> {
					drums(poseStack, collector, light, model, grow, age);
					wings("lightning_wings", poseStack, collector, light, model, grow, age, 28.0F, 10.0F);
				}
				case "lei_zhenzi" -> {
					wings("lei_zhenzi_wings", poseStack, collector, light, model, grow, age, 34.0F, 8.0F);
					cudgel(poseStack, collector, light, state, model, grow);
				}
				default -> {
				}
			}
		}
		for (String effect : view.effects()) {
			float in = 1.0F;
			float since = ClientForms.effectAge(state.id, effect);
			if (since >= 0.0F) {
				in = Mth.clamp((since + 1.0F) / 6.0F, 0.0F, 1.0F);
			}
			int left = ClientForms.effectLeft(state.id, effect);
			if (left > 0 && left < 6) {
				in = Math.min(in, left / 6.0F);
			}
			switch (effect) {
				case "golden_body" -> sheen(poseStack, collector, state, GOLD, 70.0F, 120.0F, 0.12F);
				case "lightning_armor" -> {
					float flicker = (Mth.sin(age * 1.7F) * Mth.sin(age * 0.53F) + 1.0F) * 0.5F;
					sheen(poseStack, collector, state, VIOLET, 40.0F + 80.0F * flicker, 60.0F + 90.0F * flicker, 0.0F);
				}
				case "burning_heart" -> {
					// a heartbeat: a sharp double pulse once a second
					float beat = (age % 20.0F) / 20.0F;
					float pulse = beat < 0.12F ? beat / 0.12F : beat < 0.3F ? 1.0F - (beat - 0.12F) / 0.18F * 0.6F : beat < 0.4F ? 0.4F + (beat - 0.3F) * 6.0F
							: Math.max(0.0F, 1.0F - (beat - 0.4F) * 2.5F);
					sheen(poseStack, collector, state, RED, 30.0F + 110.0F * pulse, 30.0F + 110.0F * pulse, 0.0F);
				}
				case "dharma" -> dharma(poseStack, collector, light, model, in, age);
				case "dragon_form" -> dragon(poseStack, collector, light, model, in, age, "_lavender", state.id, true);
				case "kunpeng_wings" -> wings("kunpeng_wings", poseStack, collector, light, model, in, age, 40.0F, 6.0F);
				case "lightning_wings" -> wings("lightning_wings", poseStack, collector, light, model, in, age, 28.0F, 10.0F);
				case "flame_wings" -> wings("flame_wings", poseStack, collector, light, model, in, age, 30.0F, 12.0F);
				case "wind_thunder_wings" -> wings("wind_thunder_wings", poseStack, collector, light, model, in, age, 38.0F, 5.0F);
				case "lei_zhenzi_wings" -> wings("lei_zhenzi_wings", poseStack, collector, light, model, in, age, 34.0F, 8.0F);
				case "golden_cudgel" -> cudgel(poseStack, collector, light, state, model, in);
				case "ice_claw" -> {
					onPart(model, model.rightArm, poseStack, collector, light, "ice_claw", in);
					poseStack.pushPose();
					model.root().translateAndRotate(poseStack);
					model.leftArm.translateAndRotate(poseStack);
					FormModels.submitPiece("ice_claw", poseStack, collector, light, in, 2.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
					poseStack.popPose();
				}
				case "azure_sword" -> {
					poseStack.pushPose();
					model.translateToHand(state, HumanoidArm.RIGHT, poseStack);
					// held steady (no size throb)
					FormModels.submitPiece("light_sword", poseStack, collector, LightCoordsUtil.FULL_BRIGHT, in, -1.0F, 10.0F, -1.0F, 0.0F, 90.0F,
							0.0F, 0.85F);
					poseStack.popPose();
				}
				case "iron_plates" -> {
					poseStack.pushPose();
					model.root().translateAndRotate(poseStack);
					model.body.translateAndRotate(poseStack);
					FormModels.submitPiece("iron_plate", poseStack, collector, light, in, 0.0F, 4.5F, -2.6F, 0.0F, 0.0F, 0.0F, 1.1F);
					FormModels.submitPiece("iron_plate", poseStack, collector, light, in, 0.0F, 4.5F, 2.6F, 180.0F, 0.0F, 0.0F, 1.1F);
					poseStack.popPose();
				}
				default -> {
				}
			}
		}
	}

	// ================================================================== forms

	private void beastBody(String kind, PoseStack ps, SubmitNodeCollector c, int light, S state, M model, float grow) {
		String piece = switch (kind) {
			case "tiger" -> "tiger_orange";
			case "eagle", "hawk" -> "hawk";
			case "fish", "koi" -> "koi";
			case "insect", "bee" -> "bee_small";
			case "temple" -> "small_temple";
			default -> "";
		};
		if (piece.isEmpty()) {
			return;
		}
		float scale = switch (piece) {
			case "tiger_orange" -> 1.6F;
			case "koi" -> 1.4F;
			case "bee_small" -> 1.2F;
			case "small_temple" -> 1.25F;
			default -> 1.3F;
		};
		float walk = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
		float bob = Math.abs(Mth.sin(state.walkAnimationPos * 0.66F)) * 0.07F * walk;
		if (piece.equals("koi") || piece.equals("bee_small")) {
			bob += Mth.sin(state.ageInTicks * 0.2F) * 0.05F;
		}
		ps.pushPose();
		model.root().translateAndRotate(ps);
		// the feet are 24 pixels below the root; lift the piece so its lowest point stands on them
		float lift = FxModels.info(piece).baseY() * scale;
		ps.translate(0.0F, 24.0F / 16.0F + lift - bob, 0.0F);
		ps.scale(scale, scale, scale);
		FormModels.submitPiece(piece, ps, c, light, grow);
		ps.popPose();
	}

	private void threeHeads(PoseStack ps, SubmitNodeCollector c, int light, S state, M model, float grow, float age) {
		ps.pushPose();
		model.root().translateAndRotate(ps);
		model.body.translateAndRotate(ps);
		// two more heads at the shoulders, turned out by 40 degrees
		FormModels.submitPiece("extra_head", ps, c, light, grow, -5.6F, -0.4F, 0.6F, 40.0F, 0.0F, 0.0F, 0.9F);
		FormModels.submitPiece("extra_head", ps, c, light, grow, 5.6F, -0.4F, 0.6F, -40.0F, 0.0F, 0.0F, 0.9F);
		// four more arms copying the real arms, spread out further
		float[][] slots = {{-6.4F, 2.6F, 1.6F, 1.0F}, {6.4F, 2.6F, 1.6F, -1.0F}, {-6.0F, 6.0F, 0.4F, 1.0F}, {6.0F, 6.0F, 0.4F, -1.0F}};
		boolean weapons = !EffectQuality.minimal();
		for (int i = 0; i < slots.length; i++) {
			float[] slot = slots[i];
			ModelPart source = slot[3] > 0.0F ? model.rightArm : model.leftArm;
			float spread = (i < 2 ? 0.35F : 0.7F) * slot[3];
			float wave = Mth.sin(age * 0.12F + i) * 0.08F;
			ps.pushPose();
			ps.translate(slot[0] / 16.0F, slot[1] / 16.0F, slot[2] / 16.0F);
			ps.rotate(new Quaternionf().rotationZYX(source.zRot + spread, source.yRot - model.body.yRot, source.xRot - model.body.xRot + wave));
			ps.scale(0.85F, 0.85F, 0.85F);
			FormModels.submitPiece("extra_arm", ps, c, light, grow);
			if (weapons) {
				String weapon = i % 2 == 0 ? "light_sword" : "ruyi_staff_gold";
				FormModels.submitPiece(weapon, ps, c, LightCoordsUtil.FULL_BRIGHT, grow, 0.0F, 10.5F, -1.0F, 0.0F, 90.0F, 0.0F,
						i % 2 == 0 ? 0.7F : 0.45F);
			}
			ps.popPose();
		}
		ps.popPose();
	}

	private void dragon(PoseStack ps, SubmitNodeCollector c, int light, M model, float grow, float age, String suffix, int entityId,
			boolean flying) {
		onPart(model, model.head, ps, c, light, "dragon_horns" + suffix, grow);
		ps.pushPose();
		model.root().translateAndRotate(ps);
		model.body.translateAndRotate(ps);
		if (flying) {
			wingPair("dragon_wings", ps, c, light, grow, age, 30.0F, 9.0F);
		} else {
			FormModels.submitPiece("dragon_wings_stub" + suffix, ps, c, light, grow);
		}
		// the tail hangs from the small of the back, sways, and sweeps 270 degrees on the art's beat
		float sweep = ClientForms.pulseAge(entityId, "tail");
		float yaw = Mth.sin(age * 0.15F) * 18.0F;
		if (sweep >= 0.0F && sweep < 8.0F) {
			float k = sweep / 8.0F;
			yaw += 270.0F * (k * k * (3.0F - 2.0F * k));
		}
		FormModels.submitPiece("dragon_tail" + suffix, ps, c, light, grow, 0.0F, 11.0F, 1.5F, yaw, -12.0F, 0.0F, 1.0F);
		ps.popPose();
	}

	private void browStars(PoseStack ps, SubmitNodeCollector c, M model, int lit, float grow) {
		ps.pushPose();
		model.root().translateAndRotate(ps);
		model.head.translateAndRotate(ps);
		for (int i = 0; i < Math.min(9, lit); i++) {
			float x = (i - 4) * 0.8F;
			float y = -6.2F + Math.abs(i - 4) * 0.18F;
			FormModels.submitPiece("brow_star", ps, c, LightCoordsUtil.FULL_BRIGHT, grow, x, y, -4.35F, 0.0F, 0.0F, 45.0F, 0.6F);
		}
		ps.popPose();
	}

	private void dharma(PoseStack ps, SubmitNodeCollector c, int light, M model, float grow, float age) {
		onPart(model, model.head, ps, c, light, "dharma_helm", grow);
		onPart(model, model.body, ps, c, light, "dharma_breastplate", grow);
		onPart(model, model.rightArm, ps, c, light, "dharma_pauldron_r", grow);
		onPart(model, model.leftArm, ps, c, light, "dharma_pauldron_l", grow);
		// the halo turns slowly behind the head (the ring's centre is 6 px up and 6 px back from the neck)
		ps.pushPose();
		model.root().translateAndRotate(ps);
		model.head.translateAndRotate(ps);
		ps.translate(0.0F, -6.0F / 16.0F, 6.0F / 16.0F);
		ps.rotateDegrees(Axis.ZP, age * 1.5F);
		ps.translate(0.0F, 6.0F / 16.0F, -6.0F / 16.0F);
		FormModels.submitPiece("dharma_halo", ps, c, LightCoordsUtil.FULL_BRIGHT, grow);
		ps.popPose();
	}

	private void heavenFire(int stage, PoseStack ps, SubmitNodeCollector c, int light, M model, float grow, float age) {
		int s = Mth.clamp(stage, 1, 3);
		float flicker = 1.0F + 0.06F * Mth.sin(age * 0.9F) + 0.03F * Mth.sin(age * 2.3F);
		ps.pushPose();
		model.root().translateAndRotate(ps);
		model.body.translateAndRotate(ps);
		FormModels.submitPiece("flame_mantle_" + s, ps, c, LightCoordsUtil.FULL_BRIGHT, grow, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, flicker);
		ps.popPose();
		ps.pushPose();
		model.root().translateAndRotate(ps);
		model.rightArm.translateAndRotate(ps);
		FormModels.submitPiece("flame_shoulder_" + s, ps, c, LightCoordsUtil.FULL_BRIGHT, grow, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, flicker);
		ps.popPose();
		ps.pushPose();
		model.root().translateAndRotate(ps);
		model.leftArm.translateAndRotate(ps);
		FormModels.submitPiece("flame_shoulder_" + s, ps, c, LightCoordsUtil.FULL_BRIGHT, grow, 2.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, flicker);
		ps.popPose();
	}

	private void drums(PoseStack ps, SubmitNodeCollector c, int light, M model, float grow, float age) {
		int n = EffectQuality.minimal() ? 4 : 8;
		ps.pushPose();
		model.root().translateAndRotate(ps);
		model.body.translateAndRotate(ps);
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2.0D * i / n + age * 0.02D;
			float x = (float) Math.cos(a) * 11.0F;
			float y = 4.0F + (float) Math.sin(a) * 11.0F;
			// the drums circle at a steady size (no per-drum throb)
			FormModels.submitPiece("thunder_drum", ps, c, light, grow, x, y, 8.0F, 0.0F, 0.0F, (float) Math.toDegrees(a), 0.5F);
		}
		ps.popPose();
	}

	private void cudgel(PoseStack ps, SubmitNodeCollector c, int light, S state, M model, float grow) {
		ps.pushPose();
		model.translateToHand(state, HumanoidArm.RIGHT, ps);
		FormModels.submitPiece("ruyi_staff_gold", ps, c, LightCoordsUtil.FULL_BRIGHT, grow, -1.0F, 10.0F, -1.0F, 0.0F, 90.0F, 0.0F, 0.6F);
		ps.popPose();
	}

	/** A pair of back wings flapping {@code degrees} with {@code period} ticks, spread up from the shoulder blades. */
	private void wings(String kind, PoseStack ps, SubmitNodeCollector c, int light, M model, float grow, float age, float degrees,
			float period) {
		ps.pushPose();
		model.root().translateAndRotate(ps);
		model.body.translateAndRotate(ps);
		wingPair(kind, ps, c, light, grow, age, degrees, period);
		ps.popPose();
	}

	private static void wingPair(String kind, PoseStack ps, SubmitNodeCollector c, int light, float grow, float age, float degrees,
			float period) {
		float flap = Mth.sin((float) (Math.PI * 2.0D * age / Math.max(2.0F, period))) * degrees * 0.5F;
		float raise = 18.0F + flap;
		// part space has y down and the wearer's right at -x: the right wing (+x in the model) lifts with a +z roll
		// both swept back by 20 degrees (part +z is behind)
		FormModels.submitPiece(kind + "_r", ps, c, light, grow, -1.5F, 2.5F, 2.4F, 20.0F, 0.0F, raise, 0.95F);
		FormModels.submitPiece(kind + "_l", ps, c, light, grow, 1.5F, 2.5F, 2.4F, -20.0F, 0.0F, -raise, 0.95F);
	}

	private void onPart(M model, ModelPart part, PoseStack ps, SubmitNodeCollector c, int light, String piece, float grow) {
		ps.pushPose();
		model.root().translateAndRotate(ps);
		part.translateAndRotate(ps);
		FormModels.submitPiece(piece, ps, c, light, grow);
		ps.popPose();
	}

	/** The body drawn again over itself, tinted and full bright (the sheen of golden and bronze bodies, armour crackle). */
	private void sheen(PoseStack ps, SubmitNodeCollector c, S state, int rgb, float alphaLow, float alphaHigh, float speed) {
		if (EffectQuality.minimal() && alphaHigh < 100.0F) {
			return;
		}
		float pulse = speed <= 0.0F ? 1.0F : 0.5F + 0.5F * Mth.sin(state.ageInTicks * speed);
		int alpha = Math.round(Mth.clamp(Mth.lerp(pulse, alphaLow, alphaHigh), 0.0F, 255.0F));
		Identifier skin = state.skin.body().texturePath();
		c.order(2).submitModel(this.getParentModel(), state, ps, RenderTypes.entityTranslucentEmissive(skin), LightCoordsUtil.FULL_BRIGHT,
				LivingEntityRenderer.getOverlayCoords(state, 0.0F), ARGB.color(alpha, rgb), null, 0);
	}
}
