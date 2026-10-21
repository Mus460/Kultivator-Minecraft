package dev.kultivator.client.dunia.dimensi.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.disciple.DiscipleHeldItemLayer;
import dev.kultivator.client.disciple.DiscipleLook;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.client.disciple.DiscipleRobeLayer;
import dev.kultivator.client.disciple.power.PhysiqueVisuals;
import dev.kultivator.client.render.MeditationPose;
import dev.kultivator.disciple.DiscipleEntity;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jetbrains.annotations.Nullable;

/**
 * The shared renderer of module D's people (built like {@code musuh/RogueRenderer}, DESIGN 34.4 "reuse the disciple
 * model"): the disciple render state on the player model, a default skin or one of D's own skins, the disciple robe in
 * the role's colour (dropped beyond 32 b, 16 b on Minimal), the role's {@link Headwear} piece, anything held, a scale and
 * a tint per role; ghosts are drawn see-through. The name plate is the role's title within 16 b.
 */
public abstract class HumanNpcRenderer<T extends LivingEntity> extends LivingEntityRenderer<T, DiscipleRenderState, PlayerModel> {

	protected static final RenderStateDataKey<Float> SCALE = RenderStateDataKey.create(() -> "kultivator:dimensi_scale");
	protected static final RenderStateDataKey<Integer> TINT = RenderStateDataKey.create(() -> "kultivator:dimensi_tint");
	private static final float PLAYER_SCALE = 0.9375F;
	private static final double NAME_DISTANCE_SQ = 16.0D * 16.0D;
	private static final double ROBE_DISTANCE_SQ = 32.0D * 32.0D;

	private final PlayerModel wideModel;
	private final PlayerModel slimModel;

	protected HumanNpcRenderer(EntityRendererProvider.Context context) {
		super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
		this.wideModel = this.model;
		this.slimModel = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
		this.addLayer(new CulledRobe(this, new DiscipleRobeLayer(this, context)));
		this.addLayer(new HeadwearLayer(this, context));
		this.addLayer(new DiscipleHeldItemLayer(this));
	}

	@Override
	public DiscipleRenderState createRenderState() {
		return new DiscipleRenderState();
	}

	/** The role's look: skin, robe, role (ghosts: {@code PHANTOM}), headwear, tint, scale. */
	protected abstract void look(T entity, DiscipleRenderState state);

	@Override
	public void extractRenderState(T entity, DiscipleRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
		state.id = entity.getId();
		state.showCape = false;
		state.rightArmPose = state.rightHandItemState.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		state.leftArmPose = state.leftHandItemState.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		state.layer = 0;
		state.stage = 0;
		state.dao = -1;
		state.rootColor = 0xFFFFFF;
		state.flags = 0;
		state.activity = 0;
		state.sectRank = 0;
		state.stars = 0;
		state.talent = 0;
		state.power = 0;
		state.seated = 0.0F;
		state.natalItem.clear();
		state.name = entity.getName();
		state.role = DiscipleEntity.Role.RIVAL.ordinal();
		FabricRenderState data = (FabricRenderState) state;
		data.setData(MeditationPose.STRENGTH, 0.0F);
		data.setData(SCALE, 1.0F);
		data.setData(TINT, -1);
		data.setData(HeadwearLayer.KIND, Headwear.NONE);
		data.setData(HeadwearLayer.GLOW, Headwear.NONE);
		skin(state, entity.getId() & 7, false);
		look(entity, state);
	}

	/** One of the nine default characters. */
	protected static void skin(DiscipleRenderState state, int index, boolean slim) {
		PlayerSkin skin = DiscipleLook.skinByIndex(index, slim);
		state.skin = skin;
		state.texture = skin.body().texturePath();
		state.slim = skin.model() == PlayerModelType.SLIM;
	}

	/** One of D's own wide-arm skins ({@code textures/dunia/dimensi/entity/<name>.png}). */
	protected static void ownSkin(DiscipleRenderState state, Identifier texture) {
		state.texture = texture;
		state.slim = false;
	}

	protected static void set(DiscipleRenderState state, int robe, int realm, int headwear, float scale, int tint) {
		state.robeColor = robe;
		state.realm = realm;
		FabricRenderState data = (FabricRenderState) state;
		data.setData(HeadwearLayer.KIND, headwear);
		data.setData(SCALE, scale);
		data.setData(TINT, tint);
	}

	@Override
	public Identifier getTextureLocation(DiscipleRenderState state) {
		return state.texture;
	}

	@Override
	protected int getModelTint(DiscipleRenderState state) {
		int tint = ((FabricRenderState) state).getDataOrDefault(TINT, -1);
		return tint == -1 && PhysiqueVisuals.translucent(state) ? PhysiqueVisuals.modelTint(state, -1) : tint;
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
		float scale = PLAYER_SCALE * ((FabricRenderState) state).getDataOrDefault(SCALE, 1.0F);
		poseStack.scale(scale, scale, scale);
	}

	@Override
	protected boolean shouldShowName(T entity, double distanceToCameraSq) {
		return distanceToCameraSq < NAME_DISTANCE_SQ && !entity.isInvisible();
	}

	@Override
	protected Component getNameTag(T entity) {
		return entity.getName();
	}

	@Override
	public void submit(DiscipleRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		this.model = state.slim ? slimModel : wideModel;
		super.submit(state, poseStack, collector, camera);
	}

	/** A see-through ghost tint {@code (alpha, rgb)}. */
	protected static int ghostTint(int alpha, int rgb) {
		return ARGB.color(alpha, rgb);
	}

	/** The robe only within 32 blocks (16 on Minimal effects). */
	private static final class CulledRobe extends RenderLayer<DiscipleRenderState, PlayerModel> {

		private final DiscipleRobeLayer robe;

		CulledRobe(RenderLayerParent<DiscipleRenderState, PlayerModel> parent, DiscipleRobeLayer robe) {
			super(parent);
			this.robe = robe;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, DiscipleRenderState state, float yRot, float xRot) {
			double limit = EffectQuality.minimal() ? ROBE_DISTANCE_SQ / 4.0D : ROBE_DISTANCE_SQ;
			if (state.distanceToCameraSq <= limit) {
				robe.submit(poseStack, collector, lightCoords, state, yRot, xRot);
			}
		}
	}
}
