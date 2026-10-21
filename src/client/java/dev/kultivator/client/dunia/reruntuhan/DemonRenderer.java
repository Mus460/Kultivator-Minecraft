package dev.kultivator.client.dunia.reruntuhan;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.reruntuhan.RCreature;
import dev.kultivator.dunia.reruntuhan.penjara.IblisKecilEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * Draws an Iblis Kecil, a lesser demon of the prison's cells (DESIGN 34.5.6): the player model in one of the module's
 * three generated demon skins (red, ash, jade; ridged horns on the hat layer, bone bracers, claws) with eyes glowing in
 * the dark (the vanilla "eyes" render type), hunched a little, arms forward while it pounces, flushed red while it
 * gathers itself for the leap.
 */
public class DemonRenderer extends LivingEntityRenderer<IblisKecilEntity, DemonRenderer.State, PlayerModel> {

	private static final Identifier[] SKINS = {Kultivator.id("textures/entity/dunia/reruntuhan/iblis_kecil_0.png"),
			Kultivator.id("textures/entity/dunia/reruntuhan/iblis_kecil_1.png"), Kultivator.id("textures/entity/dunia/reruntuhan/iblis_kecil_2.png")};
	private static final RenderType[] EYES = {RenderTypes.eyes(Kultivator.id("textures/entity/dunia/reruntuhan/mata_iblis_0.png")),
			RenderTypes.eyes(Kultivator.id("textures/entity/dunia/reruntuhan/mata_iblis_1.png")),
			RenderTypes.eyes(Kultivator.id("textures/entity/dunia/reruntuhan/mata_iblis_2.png"))};

	public static class State extends AvatarRenderState {
		int kind;
		int flags;
		int skill;
	}

	public DemonRenderer(EntityRendererProvider.Context context) {
		super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.45F);
		this.addLayer(new Eyes(this));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(IblisKecilEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
		state.kind = Mth.clamp(entity.kind(), 0, 2);
		state.flags = entity.flags();
		state.skill = entity.skill();
		state.id = entity.getId();
		state.showCape = false;
		boolean leaping = state.skill == IblisKecilEntity.LEAP;
		state.rightArmPose = leaping ? HumanoidModel.ArmPose.CROSSBOW_CHARGE : HumanoidModel.ArmPose.EMPTY;
		state.leftArmPose = leaping ? HumanoidModel.ArmPose.CROSSBOW_CHARGE : HumanoidModel.ArmPose.EMPTY;
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return SKINS[state.kind];
	}

	@Override
	protected int getModelTint(State state) {
		if ((state.flags & RCreature.FLAG_TELEGRAPH) != 0) {
			float k = 0.5F + 0.5F * Mth.sin(state.ageInTicks * 0.8F);
			return ARGB.color(255, 255, (int) Mth.lerp(k, 200.0F, 140.0F), (int) Mth.lerp(k, 200.0F, 140.0F));
		}
		return -1;
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		// a hunched, slightly smaller frame than a man
		poseStack.scale(0.9F, 0.86F, 0.9F);
	}

	@Override
	protected boolean shouldShowName(IblisKecilEntity entity, double distanceToCameraSq) {
		return false;
	}

	private static final class Eyes extends RenderLayer<State, PlayerModel> {

		Eyes(RenderLayerParent<State, PlayerModel> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, State state, float yRot, float xRot) {
			if (!state.isInvisible) {
				collector.order(1).submitModel(this.getParentModel(), state, poseStack, EYES[state.kind], lightCoords, OverlayTexture.NO_OVERLAY,
						state.outlineColor);
			}
		}
	}
}
