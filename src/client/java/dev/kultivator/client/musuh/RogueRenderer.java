package dev.kultivator.client.musuh;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.disciple.DiscipleHeldItemLayer;
import dev.kultivator.client.disciple.DiscipleLook;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.client.disciple.DiscipleRobeLayer;
import dev.kultivator.client.disciple.power.PhysiqueVisuals;
import dev.kultivator.client.render.MeditationPose;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.musuh.RogueCultivatorEntity;
import dev.kultivator.musuh.RogueCultivators;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jetbrains.annotations.Nullable;

/**
 * Draws a rogue cultivator as a player (DESIGN 25.4.2): the disciple's render state, one of the default skins, the
 * disciple robe in dark cloth (dropped beyond 32 blocks), a cloth mask tinted with the tier's trim colour
 * ({@link RogueMaskLayer}) and anything in its hands. Illusory phantoms and energy bodies are see-through, demonic sect
 * members carry a faint blood tint. Its name plate is its title (never a custom name), shown within 16 blocks.
 */
public class RogueRenderer extends LivingEntityRenderer<RogueCultivatorEntity, DiscipleRenderState, PlayerModel> {

	/** The rogue's tier and variant, carried on the shared disciple render state. */
	static final RenderStateDataKey<Integer> TIER = RenderStateDataKey.create(() -> "kultivator:rogue_tier");
	static final RenderStateDataKey<Integer> VARIANT = RenderStateDataKey.create(() -> "kultivator:rogue_variant");
	private static final float PLAYER_SCALE = 0.9375F;
	private static final double NAME_DISTANCE_SQ = 16.0D * 16.0D;
	private static final double ROBE_DISTANCE_SQ = 32.0D * 32.0D;
	/** Robe cloth by variant (rogue, ghost, energy, demonic) and the Patriarch's. */
	private static final int[] ROBE = {0x34343C, 0x4A3A5E, 0x6C9CC8, 0x3A1418};
	private static final int PATRIARCH_ROBE = 0x1A0A0E;

	private final PlayerModel wideModel;
	private final PlayerModel slimModel;

	public RogueRenderer(EntityRendererProvider.Context context) {
		super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
		this.wideModel = this.model;
		this.slimModel = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
		this.addLayer(new CulledRobe(this, new DiscipleRobeLayer(this, context)));
		this.addLayer(new RogueMaskLayer(this, context));
		this.addLayer(new DiscipleHeldItemLayer(this));
	}

	@Override
	public DiscipleRenderState createRenderState() {
		return new DiscipleRenderState();
	}

	@Override
	public void extractRenderState(RogueCultivatorEntity entity, DiscipleRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
		PlayerSkin skin = DiscipleLook.skinByIndex(entity.skin(), entity.slim());
		state.skin = skin;
		state.texture = skin.body().texturePath();
		state.slim = skin.model() == PlayerModelType.SLIM;
		state.id = entity.getId();
		state.showCape = false;
		state.rightArmPose = state.rightHandItemState.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		state.leftArmPose = state.leftHandItemState.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		int variant = entity.variant();
		int tier = entity.tier();
		// The robe needs Qi Refining at least; the disciple robe's gold trims and emblem follow the realm.
		state.realm = Mth.clamp(Math.max(1, entity.realm()), 1, 8);
		state.layer = 0;
		state.stage = entity.stage();
		state.dao = -1;
		state.rootColor = entity.element().color();
		state.flags = 0;
		state.robeColor = tier >= 4 ? PATRIARCH_ROBE : ROBE[Mth.clamp(variant, 0, ROBE.length - 1)];
		state.name = entity.plate();
		state.role = variant == RogueCultivators.VARIANT_GHOST ? DiscipleEntity.Role.PHANTOM.ordinal()
				: variant == RogueCultivators.VARIANT_ENERGY ? DiscipleEntity.Role.CLONE.ordinal() : DiscipleEntity.Role.RIVAL.ordinal();
		state.activity = 0;
		state.sectRank = 0;
		state.stars = 0;
		state.talent = 0;
		state.power = 0;
		state.seated = 0.0F;
		state.natalItem.clear();
		FabricRenderState data = (FabricRenderState) state;
		data.setData(MeditationPose.STRENGTH, 0.0F);
		data.setData(TIER, tier);
		data.setData(VARIANT, variant);
	}

	@Override
	public Identifier getTextureLocation(DiscipleRenderState state) {
		return state.texture;
	}

	@Override
	protected int getModelTint(DiscipleRenderState state) {
		int variant = ((FabricRenderState) state).getDataOrDefault(VARIANT, RogueCultivators.VARIANT_ROGUE);
		if (variant == RogueCultivators.VARIANT_DEMONIC) {
			return ARGB.color(255, 238, 204, 204);
		}
		return PhysiqueVisuals.modelTint(state, -1);
	}

	@Override
	protected @Nullable RenderType getRenderType(DiscipleRenderState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
		if (isBodyVisible && PhysiqueVisuals.translucent(state)) {
			return RenderTypes.entityTranslucent(this.getTextureLocation(state));
		}
		return super.getRenderType(state, isBodyVisible, forceTransparent, appearGlowing);
	}

	@Override
	protected void scale(DiscipleRenderState state, PoseStack poseStack) {
		poseStack.scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE);
	}

	@Override
	protected boolean shouldShowName(RogueCultivatorEntity entity, double distanceToCameraSq) {
		return distanceToCameraSq < NAME_DISTANCE_SQ && super.shouldShowName(entity, distanceToCameraSq);
	}

	@Override
	protected Component getNameTag(RogueCultivatorEntity entity) {
		return entity.plate();
	}

	@Override
	public void submit(DiscipleRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		this.model = state.slim ? slimModel : wideModel;
		super.submit(state, poseStack, collector, camera);
	}

	/** The robe only within 32 blocks (16 on Minimal effects), to keep the laptop's frame time low (DESIGN 25.13). */
	private static final class CulledRobe extends RenderLayer<DiscipleRenderState, PlayerModel> {

		private final DiscipleRobeLayer robe;

		CulledRobe(RenderLayerParent<DiscipleRenderState, PlayerModel> parent, DiscipleRobeLayer robe) {
			super(parent);
			this.robe = robe;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, DiscipleRenderState state, float yRot, float xRot) {
			double limit = EffectQuality.minimal() ? ROBE_DISTANCE_SQ / 4.0D : ROBE_DISTANCE_SQ;
			if (state.distanceToCameraSq <= limit) {
				robe.submit(poseStack, submitNodeCollector, lightCoords, state, yRot, xRot);
			}
		}
	}
}
