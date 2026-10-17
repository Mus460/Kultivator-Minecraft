package dev.kultivator.client.disciple;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.render.MeditationPose;
import dev.kultivator.data.Rank;
import dev.kultivator.disciple.DiscipleEntity;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Draws a disciple as a player: the player model with wide or slim arms and one of the default player skins (both
 * chosen at recruitment), its robe ({@link DiscipleRobeLayer}) and anything in its hands. While it meditates or breaks
 * through it sits cross-legged: the render state carries the {@link MeditationPose#STRENGTH} that the PlayerModel mixin
 * poses the limbs with, and the model is lowered onto the ground here. Its name tag reads "&lt;name&gt; · &lt;rank&gt;"
 * in the realm colour.
 */
public class DiscipleRenderer extends LivingEntityRenderer<DiscipleEntity, DiscipleRenderState, PlayerModel> {

	/** Same proportions as real players. */
	private static final float PLAYER_SCALE = 0.9375F;
	private static final double NAME_DISTANCE_SQ = 16.0D * 16.0D;

	private final PlayerModel wideModel;
	private final PlayerModel slimModel;

	public DiscipleRenderer(EntityRendererProvider.Context context) {
		super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
		this.wideModel = this.model;
		this.slimModel = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
		this.addLayer(new DiscipleRobeLayer(this, context));
		this.addLayer(new DiscipleHeldItemLayer(this));
	}

	@Override
	public DiscipleRenderState createRenderState() {
		return new DiscipleRenderState();
	}

	@Override
	public void extractRenderState(DiscipleEntity entity, DiscipleRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
		PlayerSkin skin = DiscipleLook.skin(entity);
		state.skin = skin;
		state.texture = skin.body().texturePath();
		state.slim = skin.model() == PlayerModelType.SLIM;
		state.id = entity.getId();
		state.showCape = false;
		state.rightArmPose = state.rightHandItemState.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		state.leftArmPose = state.leftHandItemState.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;

		Rank rank = DiscipleLook.rank(entity);
		state.realm = rank.realm().ordinal();
		state.layer = rank.layer();
		state.stage = rank.stage();
		state.dao = DiscipleLook.dao(entity);
		state.rootColor = DiscipleLook.rootColor(entity);
		state.flags = DiscipleLook.flags(entity);
		state.robeColor = DiscipleLook.color(state.realm, state.dao);
		state.name = DiscipleLook.name(entity);

		float seated = DiscipleLook.seatStrength(entity, partialTicks);
		state.seated = seated;
		((FabricRenderState) state).setData(MeditationPose.STRENGTH, seated);
		if (seated > 0.0F) {
			state.walkAnimationSpeed = Mth.lerp(seated, state.walkAnimationSpeed, 0.0F);
			state.isCrouching = false;
		}
	}

	@Override
	public Identifier getTextureLocation(DiscipleRenderState state) {
		return state.texture;
	}

	/** A greyish, slightly red-tinged body while a Qi Deviation rages through its meridians. */
	@Override
	protected int getModelTint(DiscipleRenderState state) {
		if ((state.flags & DiscipleLook.DEVIATING) != 0) {
			float pulse = 0.5F + 0.5F * Mth.sin(state.ageInTicks * 0.15F);
			return ARGB.color(255, 220, (int) Mth.lerp(pulse, 150.0F, 185.0F), (int) Mth.lerp(pulse, 150.0F, 185.0F));
		}
		return -1;
	}

	@Override
	protected void scale(DiscipleRenderState state, PoseStack poseStack) {
		poseStack.scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE);
	}

	@Override
	protected void setupRotations(DiscipleRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
		MeditationPose.lower(state, poseStack);
		super.setupRotations(state, poseStack, bodyRot, entityScale);
	}

	@Override
	protected boolean shouldShowName(DiscipleEntity entity, double distanceToCameraSq) {
		return distanceToCameraSq < NAME_DISTANCE_SQ && super.shouldShowName(entity, distanceToCameraSq);
	}

	@Override
	protected Component getNameTag(DiscipleEntity entity) {
		return DiscipleLook.nameTag(entity);
	}

	@Override
	public void submit(DiscipleRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		this.model = state.slim ? slimModel : wideModel;
		super.submit(state, poseStack, collector, camera);
	}
}
