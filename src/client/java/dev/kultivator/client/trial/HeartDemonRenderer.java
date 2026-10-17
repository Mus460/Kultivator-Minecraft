package dev.kultivator.client.trial;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.trial.HeartDemonEntity;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.UUID;

/**
 * Draws a heart demon as its owner: the player model (wide or slim arms like the owner's skin) with the owner's skin,
 * tinted a slightly translucent, slowly pulsing black-violet, plus glowing red eyes ({@link HeartDemonEyesLayer}).
 * While the dark palm gathers, the right arm is raised. The name tag reads "Iblis Hati &lt;owner&gt;".
 */
public class HeartDemonRenderer extends LivingEntityRenderer<HeartDemonEntity, HeartDemonRenderState, PlayerModel> {

	/** Same proportions as real players. */
	private static final float PLAYER_SCALE = 0.9375F;
	private static final double NAME_DISTANCE_SQ = 24.0D * 24.0D;

	private final PlayerModel wideModel;
	private final PlayerModel slimModel;

	public HeartDemonRenderer(EntityRendererProvider.Context context) {
		super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
		this.wideModel = this.model;
		this.slimModel = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
		this.addLayer(new HeartDemonEyesLayer(this));
	}

	@Override
	public HeartDemonRenderState createRenderState() {
		return new HeartDemonRenderState();
	}

	@Override
	public void extractRenderState(HeartDemonEntity entity, HeartDemonRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
		UUID owner = entity.getOwnerUUID();
		PlayerSkin skin = OwnerLook.skin(owner);
		state.skin = skin;
		state.texture = skin.body().texturePath();
		state.slim = skin.model() == PlayerModelType.SLIM;
		state.id = entity.getId();
		state.chargingPalm = entity.isChargingPalm();
		state.rightArmPose = state.chargingPalm ? HumanoidModel.ArmPose.THROW_TRIDENT : HumanoidModel.ArmPose.EMPTY;
		state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
		// The owner's own skin layer settings, when they are close enough to be known.
		Player ownerEntity = owner == null ? null : entity.level().getPlayerByUUID(owner);
		state.showHat = ownerEntity == null || ownerEntity.isModelPartShown(PlayerModelPart.HAT);
		state.showJacket = ownerEntity == null || ownerEntity.isModelPartShown(PlayerModelPart.JACKET);
		state.showLeftPants = ownerEntity == null || ownerEntity.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
		state.showRightPants = ownerEntity == null || ownerEntity.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
		state.showLeftSleeve = ownerEntity == null || ownerEntity.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
		state.showRightSleeve = ownerEntity == null || ownerEntity.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
		state.showCape = false;
	}

	@Override
	public Identifier getTextureLocation(HeartDemonRenderState state) {
		return state.texture;
	}

	/** A shadow of the owner: nearly black-violet, a little see-through, breathing slowly; darker while it gathers qi. */
	@Override
	protected int getModelTint(HeartDemonRenderState state) {
		float pulse = 0.5F + 0.5F * Mth.sin(state.ageInTicks * 0.12F);
		if (state.chargingPalm) {
			return ARGB.color(245, (int) Mth.lerp(pulse, 70.0F, 110.0F), 18, (int) Mth.lerp(pulse, 40.0F, 60.0F));
		}
		return ARGB.color(228, (int) Mth.lerp(pulse, 38.0F, 66.0F), (int) Mth.lerp(pulse, 24.0F, 38.0F), (int) Mth.lerp(pulse, 56.0F, 92.0F));
	}

	@Override
	protected void scale(HeartDemonRenderState state, PoseStack poseStack) {
		poseStack.scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE);
	}

	@Override
	protected boolean shouldShowName(HeartDemonEntity entity, double distanceToCameraSq) {
		return distanceToCameraSq < NAME_DISTANCE_SQ && super.shouldShowName(entity, distanceToCameraSq);
	}

	@Override
	protected Component getNameTag(HeartDemonEntity entity) {
		return Component.translatable("kultivator.trial.name_tag", OwnerLook.name(entity)).withStyle(ChatFormatting.DARK_PURPLE);
	}

	@Override
	public void submit(HeartDemonRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		this.model = state.slim ? slimModel : wideModel;
		super.submit(state, poseStack, collector, camera);
	}
}
