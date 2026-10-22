package dev.kultivator.client.dunia.naga;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.Kultivator;
import dev.kultivator.beast.BeastData;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.CommonFx;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.animal.fox.FoxModel;
import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.FoxRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.FoxRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The looks of tier 4-6 spirit beasts (DESIGN 34.5.12), drawn as layers on the vanilla wolf and fox renderers
 * (Fabric's {@code LivingEntityRenderLayerRegistrationCallback}, verified in fabric-rendering-v1):
 * <ul>
 * <li>wolves: an emissive star-silver overlay (4), the Tiangou's pale overlay and a silver moon halo behind the head (5),
 * the Moon-Devouring Tiangou's indigo-gold overlay and the eclipse halo (6);</li>
 * <li>foxes: nine model tails fanned behind the body (moon silver at 4, heavenly white gold at 5, gold at 6) that sway, and
 * the overlay of their tier; Maximal adds light trails from the tail tips.</li>
 * </ul>
 * Budgets: Minimal draws only the halo or a single extra tail (at most 12 quads a beast), Normal nine tails and the
 * overlay, Maximal the trails as well. Vanilla render states carry no entity id, so a client-tick list of the tier 4+
 * beasts nearby is matched to the state by its interpolated position.
 */
public final class BeastTierLayers {

	private static final Identifier[] WOLF_OVERLAY = {
			Kultivator.id("textures/dunia/naga/wolf_bintang_perak.png"),
			Kultivator.id("textures/dunia/naga/wolf_tiangou.png"),
			Kultivator.id("textures/dunia/naga/wolf_penelan_bulan.png")};
	private static final Identifier[] FOX_OVERLAY = {
			Kultivator.id("textures/dunia/naga/fox_bulan.png"),
			Kultivator.id("textures/dunia/naga/fox_langit.png"),
			Kultivator.id("textures/dunia/naga/fox_emas.png")};
	private static final String[] FOX_TAIL = {"moon", "sky", "gold"};
	private static final double RANGE = 64.0D;

	/** A tier 4+ beast near the camera. */
	private record Entry(LivingEntity entity, int kind, int tier) {
	}

	private static final List<Entry> NEAR = new ArrayList<>();

	static void init() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof WolfRenderer wolf) {
				helper.register(new WolfLayer(wolf));
			} else if (renderer instanceof FoxRenderer fox) {
				helper.register(new FoxLayer(fox));
			}
		});
	}

	static void clear() {
		NEAR.clear();
	}

	/** Every client tick: the tier 4+ wolves and foxes within 64 blocks; Maximal fox trails. */
	static void tick(Minecraft minecraft) {
		NEAR.clear();
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		Vec3 eye = minecraft.player.position();
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(RANGE),
				e -> (e instanceof Wolf || e instanceof Fox) && e.isAlive() && e.hasAttached(BeastData.TYPE))) {
			BeastData data = entity.getAttached(BeastData.TYPE);
			if (data != null && data.tier() >= 4) {
				NEAR.add(new Entry(entity, data.kind(), data.tier()));
				if (data.isFox() && EffectQuality.maximal() && !minecraft.isPaused() && entity.tickCount % 3 == 0) {
					trail(entity, data.tier());
				}
			}
		}
	}

	private static void trail(LivingEntity fox, int tier) {
		float yaw = fox.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 back = new Vec3(Mth.sin(yaw), 0.0D, -Mth.cos(yaw));
		Vec3 base = fox.position().add(back.scale(0.6D * fox.getScale())).add(0.0D, 0.75D * fox.getScale(), 0.0D);
		int rgb = tier >= BeastData.TOP_TIER ? 0xFFD040 : tier == 5 ? 0xFFF2C8 : 0xC8DAFF;
		double spread = (fox.getRandom().nextDouble() - 0.5D) * 1.2D;
		Vec3 side = new Vec3(-back.z, 0.0D, back.x).scale(spread);
		CommonFx.add(CommonFx.glow(rgb, 0.7F, 18), base.add(side).add(0.0D, Math.abs(spread) * 0.4D, 0.0D), back.scale(0.02D), false);
	}

	/** The tier 4+ entry whose interpolated position is this render state's (null for an ordinary beast). */
	private static @Nullable Entry match(LivingEntityRenderState state, boolean fox) {
		Entry best = null;
		double bestSq = 0.25D;
		for (Entry entry : NEAR) {
			if ((entry.kind == BeastData.FOX) != fox) {
				continue;
			}
			LivingEntity entity = entry.entity;
			float partial = Mth.clamp(state.ageInTicks - entity.tickCount, 0.0F, 1.0F);
			Vec3 at = entity.getPosition(partial);
			double dx = at.x - state.x;
			double dy = at.y - state.y;
			double dz = at.z - state.z;
			double d = dx * dx + dy * dy + dz * dz;
			if (d < bestSq) {
				bestSq = d;
				best = entry;
			}
		}
		return best;
	}

	/** The emissive overlay of the tier over the beast's own model (any uv layout: a sheen and star glints). */
	private static <S extends LivingEntityRenderState> void overlay(net.minecraft.client.model.Model<? super S> model, S state, PoseStack poseStack,
			SubmitNodeCollector collector, Identifier texture, float pulse) {
		int alpha = Math.round(150 + 90 * pulse);
		collector.order(1).submitModel(model, state, poseStack, RenderTypes.entityTranslucentEmissive(texture), LightCoordsUtil.FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, ARGB.color(alpha, 0xFFFFFF), null, state.outlineColor);
	}

	// ================================================================== wolves

	static final class WolfLayer extends RenderLayer<WolfRenderState, WolfModel> {

		WolfLayer(RenderLayerParent<WolfRenderState, WolfModel> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, WolfRenderState state, float yRot, float xRot) {
			if (state.isInvisible || state.isBaby) {
				return;
			}
			Entry entry = match(state, false);
			if (entry == null) {
				return;
			}
			int step = Mth.clamp(entry.tier - 4, 0, 2);
			float pulse = 0.5F + 0.5F * Mth.sin(state.ageInTicks * 0.08F);
			WolfModel model = this.getParentModel();
			if (!EffectQuality.minimal()) {
				overlay(model, state, poseStack, collector, WOLF_OVERLAY[step], pulse);
			}
			ModelPart root = model.root();
			if (!root.hasChild("head")) {
				return;
			}
			if (!EffectQuality.minimal()) {
				// the star-silver ruff behind the head (tier 4+), breathing with the wolf
				poseStack.pushPose();
				root.translateAndRotate(poseStack);
				root.getChild("head").translateAndRotate(poseStack);
				poseStack.translate(1.0F / 16.0F, 0.5F / 16.0F, 2.6F / 16.0F);
				float ruff = (entry.tier >= 6 ? 0.62F : 0.55F) * (1.0F + 0.03F * pulse);
				poseStack.scale(ruff, ruff, ruff);
				NagaModels.submitOnPart("star_ruff", poseStack, collector, entry.tier >= 5 ? LightCoordsUtil.FULL_BRIGHT : light);
				poseStack.popPose();
			}
			if (entry.tier >= BeastData.TOP_TIER) {
				// Tiangou Penelan Bulan carries the moon in its jaws
				poseStack.pushPose();
				root.translateAndRotate(poseStack);
				root.getChild("head").translateAndRotate(poseStack);
				poseStack.translate(1.0F / 16.0F, 2.0F / 16.0F, -5.4F / 16.0F);
				poseStack.rotate(Axis.YP.rotationDegrees(state.ageInTicks * 1.5F));
				poseStack.scale(0.3F, 0.3F, 0.3F);
				NagaModels.submitOnPart("moon_orb", poseStack, collector, LightCoordsUtil.FULL_BRIGHT);
				poseStack.popPose();
			}
			if (entry.tier >= 5) {
				poseStack.pushPose();
				root.translateAndRotate(poseStack);
				root.getChild("head").translateAndRotate(poseStack);
				// behind and above the head, turning slowly
				poseStack.translate(1.0F / 16.0F, -4.5F / 16.0F, 3.5F / 16.0F);
				poseStack.rotate(Axis.ZP.rotationDegrees(state.ageInTicks * (entry.tier >= 6 ? -1.2F : 0.8F)));
				float s = entry.tier >= 6 ? 0.95F : 0.85F;
				poseStack.scale(s, s, s);
				NagaModels.submitOnPart(entry.tier >= 6 ? "halo_eclipse" : "halo_moon", poseStack, collector, LightCoordsUtil.FULL_BRIGHT);
				poseStack.popPose();
			}
		}
	}

	// ================================================================== foxes

	static final class FoxLayer extends RenderLayer<FoxRenderState, FoxModel> {

		FoxLayer(RenderLayerParent<FoxRenderState, FoxModel> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, FoxRenderState state, float yRot, float xRot) {
			if (state.isInvisible || state.isBaby) {
				return;
			}
			Entry entry = match(state, true);
			if (entry == null) {
				return;
			}
			int step = Mth.clamp(entry.tier - 4, 0, 2);
			float pulse = 0.5F + 0.5F * Mth.sin(state.ageInTicks * 0.1F);
			FoxModel model = this.getParentModel();
			if (!EffectQuality.minimal()) {
				overlay(model, state, poseStack, collector, FOX_OVERLAY[step], pulse);
			}
			ModelPart root = model.root();
			if (!root.hasChild("body") || !root.getChild("body").hasChild("tail")) {
				return;
			}
			ModelPart body = root.getChild("body");
			ModelPart tail = body.getChild("tail");
			int tails = EffectQuality.minimal() ? 1 : 9;
			boolean tips = !EffectQuality.minimal();
			String variant = FOX_TAIL[step];
			int tailLight = entry.tier >= 6 ? LightCoordsUtil.FULL_BRIGHT : light;
			float fold = state.isSleeping ? 0.25F : state.isSitting ? 0.6F : 1.0F;
			for (int i = 0; i < tails; i++) {
				float spread = tails == 1 ? 0.0F : (i - (tails - 1) * 0.5F) / ((tails - 1) * 0.5F);
				float sway = Mth.sin(state.ageInTicks * 0.09F + i * 0.8F) * 9.0F;
				float yaw = spread * 62.0F * fold + sway * 0.6F;
				float lift = (18.0F + 26.0F * (1.0F - Math.abs(spread))) * fold + sway * 0.3F;
				poseStack.pushPose();
				root.translateAndRotate(poseStack);
				body.translateAndRotate(poseStack);
				tail.translateAndRotate(poseStack);
				// tail space: +y runs along the tail, +z is up (the body lies rotated by 90 degrees)
				poseStack.translate(1.0F / 16.0F, 1.0F / 16.0F, 2.0F / 16.0F);
				poseStack.rotate(Axis.ZP.rotationDegrees(yaw));
				poseStack.rotate(Axis.XP.rotationDegrees(lift));
				// the piece's +z (the tail axis) onto +y, its +y (up) onto +z
				poseStack.rotate(Axis.XP.rotationDegrees(90.0F));
				poseStack.rotate(Axis.YP.rotationDegrees(180.0F));
				poseStack.scale(0.62F, 0.62F, 0.62F);
				NagaModels.submit("fox_tail_" + variant, poseStack, collector, tailLight);
				if (tips) {
					poseStack.translate(0.0F, 0.0F, 8.0F / 16.0F);
					poseStack.rotate(Axis.XP.rotationDegrees(-14.0F - 8.0F * Mth.sin(state.ageInTicks * 0.13F + i)));
					NagaModels.submit("fox_tail_tip_" + variant, poseStack, collector, LightCoordsUtil.FULL_BRIGHT);
				}
				poseStack.popPose();
			}
		}
	}

	private BeastTierLayers() {
	}
}
