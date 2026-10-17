package dev.kultivator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.data.Realm;
import dev.kultivator.data.VisualState;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * The Dao circle (Lingkaran Dao): a flat rune array lying on the ground under the feet while meditating, breaking
 * through or holding a Dao Domain. Drawn as textured horizontal quads with the translucent emissive entity render type
 * (full bright, so it glows at night and in shader packs), tinted with the Dao colour (the realm colour before a Dao),
 * turning slowly and counter-rotated against the body so it stays aligned with the world. A domain makes it larger
 * and faster. Effect Quality decides how many rings: Minimal only the main array, Normal adds a counter-rotating lotus
 * ring, Maximal also sends faint copies of the array rising up around the body.
 */
public class DaoCircleLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

	private static final RenderType MAIN = RenderTypes.entityTranslucentEmissive(Kultivator.id("textures/misc/dao_circle.png"));
	private static final RenderType INNER = RenderTypes.entityTranslucentEmissive(Kultivator.id("textures/misc/dao_circle_inner.png"));
	/** The model's feet in layer space (24 model pixels below the neck pivot, plus the renderer's 0.001 offset). */
	private static final float FEET_Y = 1.501F;
	/** The player renderer scales the model by this; world sizes are divided by it. */
	private static final float PLAYER_SCALE = 0.9375F;
	/** World blocks above the ground, against z-fighting with the floor. */
	private static final float LIFT = 0.03F;
	/** The avatar renderer moves a crouching model down by two pixels (before the entity scale). */
	private static final float CROUCH_DROP = 2.0F / 16.0F;
	private static final float RADIUS = 1.4F;
	private static final float DOMAIN_RADIUS = 2.3F;
	/** Degrees per tick. */
	private static final float SPIN = 0.9F;
	private static final float DOMAIN_SPIN = 2.6F;
	private static final int GHOST_PERIOD = 46;

	public DaoCircleLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, AvatarRenderState state,
					   float yRot, float xRot) {
		if (state.isInvisible) {
			return;
		}
		VisualState visual = CultivatorLook.visual(state);
		boolean domain = visual.has(VisualState.DOMAIN);
		boolean active = domain || visual.has(VisualState.MEDITATING) || visual.has(VisualState.BREAKING_THROUGH);
		if (!active || !visual.has(VisualState.SHOW_DAO_CIRCLE) || !CultivatorLook.atLeast(visual, Realm.QI_REFINING)) {
			return;
		}
		float age = state.ageInTicks;
		int rgb = CultivatorLook.color(visual);
		float spin = (age * (domain ? DOMAIN_SPIN : SPIN)) % 360.0F;
		float radius = (domain ? DOMAIN_RADIUS : RADIUS) / PLAYER_SCALE;
		// Fades in with the meditation seat; a domain or a breakthrough shows it at once.
		float seat = MeditationPose.strength(state);
		float fade = domain || visual.has(VisualState.BREAKING_THROUGH) ? 1.0F : Math.max(0.15F, seat);
		float pulse = 0.5F + 0.5F * Mth.sin(age * 0.1F);
		// The meditation pose and crouching lower the model; put the circle back on the real ground.
		float lowered = MeditationPose.LOWER * seat + (state.isCrouching ? CROUCH_DROP : 0.0F);
		float groundY = FEET_Y - (lowered + LIFT) / PLAYER_SCALE;
		OrderedSubmitNodeCollector collector = submitNodeCollector.order(1);

		int mainAlpha = Math.round((150.0F + 70.0F * pulse) * fade);
		disc(poseStack, collector, MAIN, state, groundY, spin, radius, ARGB.color(mainAlpha, rgb));
		if (!EffectQuality.minimal()) {
			int innerAlpha = Math.round((120.0F + 60.0F * (1.0F - pulse)) * fade);
			disc(poseStack, collector, INNER, state, groundY - 0.012F, -spin * 1.6F, radius * 0.56F,
					ARGB.color(innerAlpha, CultivatorLook.lighten(rgb, 0.35F)));
		}
		if (EffectQuality.maximal()) {
			// Two ghost arrays rising and fading in turn, half a period apart.
			for (int i = 0; i < 2; i++) {
				float t = ((age + i * GHOST_PERIOD * 0.5F) % GHOST_PERIOD) / GHOST_PERIOD;
				float rise = t * (domain ? 2.2F : 1.6F) / PLAYER_SCALE;
				int ghostAlpha = Math.round(90.0F * (1.0F - t) * fade);
				if (ghostAlpha > 4) {
					disc(poseStack, collector, MAIN, state, groundY - rise, spin * 0.6F, radius * (1.0F - 0.3F * t),
							ARGB.color(ghostAlpha, CultivatorLook.lighten(rgb, 0.2F)));
				}
			}
		}
	}

	/**
	 * One textured disc at layer height {@code y} (layer y points down), turned to {@code worldAngle} degrees in the
	 * world: the layer space is turned by the body (180 - bodyRot) and mirrored by the renderer, so a layer rotation
	 * {@code a} ends up as world rotation {@code 180 - bodyRot - a}.
	 */
	private static void disc(PoseStack poseStack, OrderedSubmitNodeCollector collector, RenderType type, AvatarRenderState state,
							 float y, float worldAngle, float radius, int color) {
		poseStack.pushPose();
		poseStack.translate(0.0F, y, 0.0F);
		poseStack.rotateDegrees(Axis.YP, 180.0F - state.bodyRot - worldAngle);
		collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> quad(pose, buffer, radius, color));
		poseStack.popPose();
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer buffer, float r, int color) {
		vertex(pose, buffer, -r, -r, 0.0F, 0.0F, color);
		vertex(pose, buffer, -r, r, 0.0F, 1.0F, color);
		vertex(pose, buffer, r, r, 1.0F, 1.0F, color);
		vertex(pose, buffer, r, -r, 1.0F, 0.0F, color);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float z, float u, float v, int color) {
		// Layer y points down, so the normal (0, -1, 0) faces up.
		buffer.addVertex(pose, x, 0.0F, z)
				.setColor(color)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightCoordsUtil.FULL_BRIGHT)
				.setNormal(pose, 0.0F, -1.0F, 0.0F);
	}
}
