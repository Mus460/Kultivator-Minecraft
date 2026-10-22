package dev.kultivator.client.dunia.reruntuhan;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.reruntuhan.RCreature;
import dev.kultivator.dunia.reruntuhan.gua.BayanganEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Draws Bayangan Cermin Hati, the mirror shadow (DESIGN 34.5.5 P5) and the floor-100 Bayangan Diri (34.5.7), as the
 * player it mirrors: the player model with that player's own skin (from the player list; the default skin of the id
 * when unknown) and arms, tinted a translucent, breathing ink-violet, with a violet glare in the eyes. It raises its
 * arm while it calls Telapak Cermin or Panah Qi; the floor-100 shadow is a little larger and darker. The name plate reads
 * "Bayangan &lt;name&gt;" within 24 blocks.
 */
public class ShadowRenderer extends LivingEntityRenderer<BayanganEntity, ShadowRenderer.State, PlayerModel> {

	private static final float PLAYER_SCALE = 0.9375F;
	private static final double NAME_DISTANCE_SQ = 24.0D * 24.0D;
	private static final RenderType EYES = RenderTypes.eyes(Kultivator.id("textures/entity/dunia/reruntuhan/mata_bayangan.png"));

	public static class State extends AvatarRenderState {
		Identifier texture = DefaultPlayerSkin.getDefaultTexture();
		boolean slim;
		int skill;
		int flags;
		int tier;
	}

	private final PlayerModel wideModel;
	private final PlayerModel slimModel;

	public ShadowRenderer(EntityRendererProvider.Context context) {
		super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.4F);
		this.wideModel = this.model;
		this.slimModel = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
		this.addLayer(new Eyes(this));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(BayanganEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
		PlayerSkin skin = skin(entity.owner());
		state.skin = skin;
		state.texture = skin.body().texturePath();
		state.slim = skin.model() == PlayerModelType.SLIM;
		state.id = entity.getId();
		state.skill = entity.skill();
		state.flags = entity.flags();
		state.tier = entity.tier();
		state.showCape = false;
		boolean casting = state.skill == BayanganEntity.PALM || state.skill == BayanganEntity.BOLT;
		state.rightArmPose = casting ? HumanoidModel.ArmPose.THROW_TRIDENT : HumanoidModel.ArmPose.EMPTY;
		state.leftArmPose = state.skill == BayanganEntity.DASH ? HumanoidModel.ArmPose.BOW_AND_ARROW : HumanoidModel.ArmPose.EMPTY;
	}

	/** The mirrored player's skin: from the player list, else the default skin of the id. */
	static PlayerSkin skin(@Nullable UUID owner) {
		if (owner == null) {
			return DefaultPlayerSkin.getDefaultSkin();
		}
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		PlayerInfo info = connection == null ? null : connection.getPlayerInfo(owner);
		return info != null ? info.getSkin() : DefaultPlayerSkin.get(owner);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return state.texture;
	}

	@Override
	protected int getModelTint(State state) {
		float breath = 0.5F + 0.5F * Mth.sin(state.ageInTicks * 0.11F);
		boolean telegraph = (state.flags & RCreature.FLAG_TELEGRAPH) != 0;
		if (telegraph) {
			return ARGB.color(220, (int) Mth.lerp(breath, 90.0F, 140.0F), 40, (int) Mth.lerp(breath, 150.0F, 200.0F));
		}
		int alpha = state.tier >= 3 ? 225 : 190;
		return ARGB.color(alpha, (int) Mth.lerp(breath, 34.0F, 58.0F), (int) Mth.lerp(breath, 22.0F, 34.0F), (int) Mth.lerp(breath, 62.0F, 96.0F));
	}

	@Override
	protected @Nullable RenderType getRenderType(State state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
		if (isBodyVisible) {
			return RenderTypes.entityTranslucent(this.getTextureLocation(state));
		}
		return super.getRenderType(state, isBodyVisible, forceTransparent, appearGlowing);
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		float s = PLAYER_SCALE * (state.tier >= 3 ? 1.08F : 1.0F);
		poseStack.scale(s, s, s);
	}

	@Override
	protected boolean shouldShowName(BayanganEntity entity, double distanceToCameraSq) {
		return distanceToCameraSq < NAME_DISTANCE_SQ;
	}

	@Override
	protected Component getNameTag(BayanganEntity entity) {
		return RTitles.name(entity.getDisplayName(), 0xD8C8FF);
	}

	@Override
	protected void extractNameTags(BayanganEntity entity, State state, float partialTicks) {
		super.extractNameTags(entity, state, partialTicks);
		state.scoreText = state.nameTag == null ? null : RTitles.shadow(entity.tier());
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		this.model = state.slim ? slimModel : wideModel;
		super.submit(state, poseStack, collector, camera);
	}

	/** The violet glare in its eyes (the vanilla "eyes" render type on an eye-only overlay). */
	private static final class Eyes extends RenderLayer<State, PlayerModel> {

		Eyes(RenderLayerParent<State, PlayerModel> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, State state, float yRot, float xRot) {
			if (!state.isInvisible) {
				collector.order(1).submitModel(this.getParentModel(), state, poseStack, EYES, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
			}
		}
	}
}
