package dev.kultivator.client.dunia.reruntuhan;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.disciple.DiscipleLook;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.client.disciple.DiscipleRobeLayer;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.dunia.reruntuhan.gua.SisaJiwaEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jetbrains.annotations.Nullable;

/**
 * Draws Sisa Jiwa Leluhur, the ancestor's remnant soul of an Aula Warisan (DESIGN 34.5.5), on the disciple's player
 * model (built like {@code RogueRenderer} and the town NPCs): a fixed elder skin by the ruin, the disciple robe in the
 * ruin's Dao colour with the trims and emblem of a high realm, see-through and pale, floating a hand's breadth above the
 * floor and drifting up and down. Speaking it glows; granting the inheritance it shines. The name plate shows within
 * 12 blocks.
 */
public class SoulRenderer extends LivingEntityRenderer<SisaJiwaEntity, DiscipleRenderState, PlayerModel> {

	private static final float PLAYER_SCALE = 0.9375F;
	private static final double NAME_DISTANCE_SQ = 12.0D * 12.0D;

	public SoulRenderer(EntityRendererProvider.Context context) {
		super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.0F);
		this.addLayer(new DiscipleRobeLayer(this, context));
	}

	@Override
	public DiscipleRenderState createRenderState() {
		return new DiscipleRenderState();
	}

	@Override
	public void extractRenderState(SisaJiwaEntity entity, DiscipleRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
		PlayerSkin skin = DiscipleLook.skinByIndex(Math.floorMod(entity.skin(), 9), false);
		state.skin = skin;
		state.texture = skin.body().texturePath();
		state.slim = false;
		state.id = entity.getId();
		state.showCape = false;
		state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
		state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
		if (entity.glow() > 0) {
			// talking: one hand raised in turn as it explains, and a slow nod (through the state, so the robe layer's
			// model follows the same pose)
			boolean right = (((int) (state.ageInTicks / 24.0F)) & 1) == 0;
			state.rightArmPose = right ? HumanoidModel.ArmPose.ITEM : HumanoidModel.ArmPose.EMPTY;
			state.leftArmPose = right ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
			state.xRot += 6.0F * Mth.sin(state.ageInTicks * 0.35F);
		}
		int tier = Mth.clamp(entity.tier(), 1, 3);
		// the robe of a high realm: Nascent Soul trims for Kecil, Deity Transformation, Dao Union and beyond for the others
		state.realm = tier == 1 ? 5 : tier == 2 ? 7 : 8;
		state.layer = 0;
		state.stage = 0;
		state.dao = -1;
		state.rootColor = entity.robe();
		state.robeColor = entity.robe();
		state.flags = entity.glow();
		state.role = DiscipleEntity.Role.PHANTOM.ordinal();
		state.activity = 0;
		state.sectRank = 0;
		state.name = entity.getDisplayName();
		state.natalItem.clear();
	}

	@Override
	public Identifier getTextureLocation(DiscipleRenderState state) {
		return state.texture;
	}

	@Override
	protected int getModelTint(DiscipleRenderState state) {
		float breath = 0.5F + 0.5F * Mth.sin(state.ageInTicks * 0.07F);
		int alpha = switch (state.flags) {
			case 2 -> 230;
			case 1 -> 200;
			default -> 150 + Math.round(20 * breath);
		};
		int tint = RDraw.mix(0xFFFFFF, state.robeColor & 0xFFFFFF, 0.25F);
		return ARGB.color(alpha, tint);
	}

	@Override
	protected @Nullable RenderType getRenderType(DiscipleRenderState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
		if (isBodyVisible) {
			return RenderTypes.entityTranslucent(this.getTextureLocation(state));
		}
		return super.getRenderType(state, isBodyVisible, forceTransparent, appearGlowing);
	}

	@Override
	protected void scale(DiscipleRenderState state, PoseStack poseStack) {
		poseStack.scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE);
	}

	@Override
	protected void setupRotations(DiscipleRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
		poseStack.translate(0.0F, 0.12F + 0.06F * Mth.sin(state.ageInTicks * 0.05F), 0.0F);
		super.setupRotations(state, poseStack, bodyRot, entityScale);
	}

	@Override
	protected boolean shouldShowName(SisaJiwaEntity entity, double distanceToCameraSq) {
		return distanceToCameraSq < NAME_DISTANCE_SQ;
	}

	@Override
	protected Component getNameTag(SisaJiwaEntity entity) {
		return RTitles.name(entity.getDisplayName(), 0xE8F6FF);
	}

	@Override
	protected void extractNameTags(SisaJiwaEntity entity, DiscipleRenderState state, float partialTicks) {
		super.extractNameTags(entity, state, partialTicks);
		state.scoreText = state.nameTag == null ? null : RTitles.soul(entity.tier());
	}
}
