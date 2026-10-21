package dev.kultivator.client.dunia.kota;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.disciple.DiscipleHeldItemLayer;
import dev.kultivator.client.disciple.DiscipleLook;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.client.disciple.DiscipleRobeLayer;
import dev.kultivator.client.render.MeditationPose;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
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
 * Draws a {@code kultivator_npc} with the disciple's player model (DESIGN 34.5.1, built like {@code RogueRenderer}): one
 * of the default skins by look seed, the disciple robe tinted by role (or the rival group's colour), its held item, the
 * cross-legged pose while seated (teahouse wanderers), a soft see-through look for ghosts. The robe is dropped beyond
 * 32 blocks (16 on Minimal effects); the name plate (role title and name) shows within 16 blocks. Immortals wear the
 * white-gold robe and a faint luminous tint.
 */
public class NpcRenderer extends LivingEntityRenderer<KultivatorNpc, DiscipleRenderState, PlayerModel> {

	static final RenderStateDataKey<Integer> NPC_FLAGS = RenderStateDataKey.create(() -> "kultivator:kota_npc_flags");
	static final RenderStateDataKey<Integer> NPC_ROLE = RenderStateDataKey.create(() -> "kultivator:kota_npc_role");
	private static final float PLAYER_SCALE = 0.9375F;
	private static final double NAME_DISTANCE_SQ = 16.0D * 16.0D;
	private static final double ROBE_DISTANCE_SQ = 32.0D * 32.0D;

	private final PlayerModel wideModel;
	private final PlayerModel slimModel;

	public NpcRenderer(EntityRendererProvider.Context context) {
		super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
		this.wideModel = this.model;
		this.slimModel = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
		this.addLayer(new CulledRobe(this, new DiscipleRobeLayer(this, context)));
		this.addLayer(new DiscipleHeldItemLayer(this));
	}

	@Override
	public DiscipleRenderState createRenderState() {
		return new DiscipleRenderState();
	}

	@Override
	public void extractRenderState(KultivatorNpc entity, DiscipleRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
		int look = entity.look();
		boolean slim = entity.female() || Math.floorMod(look >>> 13, 4) == 0;
		PlayerSkin skin = DiscipleLook.skinByIndex(Math.floorMod(look, 9), slim);
		state.skin = skin;
		state.texture = skin.body().texturePath();
		state.slim = skin.model() == PlayerModelType.SLIM;
		state.id = entity.getId();
		state.showCape = false;
		state.rightArmPose = state.rightHandItemState.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		state.leftArmPose = state.leftHandItemState.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		NpcRole role = entity.role();
		// the robe's gold trims and emblem follow the realm (Mortal townsfolk wear a plain Qi Refining robe)
		state.realm = Mth.clamp(Math.max(1, entity.realm()), 1, 8);
		state.layer = 0;
		state.stage = entity.stage();
		state.dao = entity.dao() == null ? -1 : entity.dao().ordinal();
		state.rootColor = entity.robe();
		state.flags = 0;
		state.robeColor = entity.robe();
		state.name = entity.plate();
		// RIVAL: plain gold trims from Golden Core and the emblem from Nascent Soul (no sect-rank trims)
		state.role = DiscipleEntity.Role.RIVAL.ordinal();
		if (entity.hasFlag(KultivatorNpc.FLAG_GHOST)) {
			state.role = DiscipleEntity.Role.PHANTOM.ordinal();
		}
		state.activity = 0;
		state.sectRank = 0;
		state.stars = 0;
		state.talent = 0;
		state.power = 0;
		float seated = entity.hasFlag(KultivatorNpc.FLAG_SITTING) ? 1.0F : 0.0F;
		state.seated = seated;
		if (seated > 0.0F) {
			state.walkAnimationSpeed = 0.0F;
		}
		state.natalItem.clear();
		FabricRenderState data = (FabricRenderState) state;
		data.setData(MeditationPose.STRENGTH, seated);
		data.setData(NPC_FLAGS, entity.flags());
		data.setData(NPC_ROLE, role.ordinal());
	}

	@Override
	public Identifier getTextureLocation(DiscipleRenderState state) {
		return state.texture;
	}

	@Override
	protected int getModelTint(DiscipleRenderState state) {
		int role = ((FabricRenderState) state).getDataOrDefault(NPC_ROLE, 0);
		int flags = ((FabricRenderState) state).getDataOrDefault(NPC_FLAGS, 0);
		if ((flags & KultivatorNpc.FLAG_GHOST) != 0) {
			return ARGB.color(150, 210, 230, 255);
		}
		if (NpcRole.byOrdinal(role).immortal()) {
			return ARGB.color(255, 255, 250, 238);
		}
		return -1;
	}

	@Override
	protected @Nullable RenderType getRenderType(DiscipleRenderState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
		int flags = ((FabricRenderState) state).getDataOrDefault(NPC_FLAGS, 0);
		if (isBodyVisible && (flags & KultivatorNpc.FLAG_GHOST) != 0) {
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
		MeditationPose.lower(state, poseStack);
		super.setupRotations(state, poseStack, bodyRot, entityScale);
	}

	@Override
	protected boolean shouldShowName(KultivatorNpc entity, double distanceToCameraSq) {
		return distanceToCameraSq < NAME_DISTANCE_SQ && super.shouldShowName(entity, distanceToCameraSq);
	}

	@Override
	protected Component getNameTag(KultivatorNpc entity) {
		return entity.plate();
	}

	@Override
	public void submit(DiscipleRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		this.model = state.slim ? slimModel : wideModel;
		super.submit(state, poseStack, collector, camera);
	}

	/** The robe only within 32 blocks (16 on Minimal effects): the laptop's frame time (DESIGN 34.0 rule V3). */
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
