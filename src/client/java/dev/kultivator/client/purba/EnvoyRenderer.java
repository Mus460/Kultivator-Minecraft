package dev.kultivator.client.purba;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.disciple.DiscipleHeldItemLayer;
import dev.kultivator.client.disciple.DiscipleLook;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.client.disciple.DiscipleRobeLayer;
import dev.kultivator.client.render.MeditationPose;
import dev.kultivator.client.sosok.SosokJembatan;
import dev.kultivator.client.sosok.SosokRender;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.purba.HeavenEnvoyEntity;
import dev.kultivator.sosok.Gaya;
import dev.kultivator.sosok.Sosok;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
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
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jetbrains.annotations.Nullable;

/**
 * Draws Utusan Murka Langit and the Panglima Langit (DESIGN 26.5.4) like the rogue cultivator: the disciple's render state
 * on the player model, one of the default skins, the disciple robe in white-gold cloth (dropped beyond 32 blocks, 16 on
 * Minimal) and anything in its hands, slightly translucent. The halo ring is drawn by {@code PurbaFx}. The name plate is its
 * title (never a custom name), within 16 blocks.
 * <p>
 * DESIGN 40.4.1 D (module 40R): the envoy is a Sosok body made on the client ({@code utusan_langit}: a man in the
 * white-gold immortal robe under duty armour, gold guan), still slightly see-through; the halo stays PurbaFx's. Gerak
 * gives it the military stance and walk; a cast pose (27) now shows on it like on the rogues.
 */
public class EnvoyRenderer extends LivingEntityRenderer<HeavenEnvoyEntity, DiscipleRenderState, PlayerModel> {

	private static final float PLAYER_SCALE = 0.9375F;
	private static final float PANGLIMA_SCALE = 1.1F;
	private static final double NAME_DISTANCE_SQ = 16.0D * 16.0D;
	private static final double ROBE_DISTANCE_SQ = 32.0D * 32.0D;
	private static final int ENVOY_ROBE = 0xF4EED8;
	private static final int PANGLIMA_ROBE = 0xE8C66A;
	private static final String RUPA = "utusan_langit";

	/** The Sosok body (128 × 128 atlas mesh). */
	private final PlayerModel sosokWide;
	private final PlayerModel sosokSlim;
	/** The old player-skin body (last fallback). */
	private final PlayerModel wideModel;
	private final PlayerModel slimModel;

	public EnvoyRenderer(EntityRendererProvider.Context context) {
		super(context, new PlayerModel(SosokJembatan.tubuh(context, false), false), 0.5F);
		this.sosokWide = this.model;
		this.sosokSlim = new PlayerModel(SosokJembatan.tubuh(context, true), true);
		this.wideModel = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false);
		this.slimModel = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
		this.addLayer(new SosokRender.TanpaSosok(this, new CulledRobe(this, new DiscipleRobeLayer(this, context))));
		SosokRender.pasangLapisan(this::addLayer, this, context);
		this.addLayer(new DiscipleHeldItemLayer(this));
	}

	@Override
	public DiscipleRenderState createRenderState() {
		return new DiscipleRenderState();
	}

	@Override
	public void extractRenderState(HeavenEnvoyEntity entity, DiscipleRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
		PlayerSkin skin = DiscipleLook.skinByIndex(entity.skin(), false);
		state.skin = skin;
		state.texture = skin.body().texturePath();
		state.slim = skin.model() == PlayerModelType.SLIM;
		state.id = entity.getId();
		state.showCape = false;
		state.rightArmPose = state.rightHandItemState.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		state.leftArmPose = state.leftHandItemState.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		boolean panglima = entity.isPanglima();
		state.realm = 8;
		state.layer = 0;
		state.stage = panglima ? 3 : 1;
		state.dao = -1;
		state.rootColor = 0xFFE08A;
		state.flags = 0;
		state.robeColor = panglima ? PANGLIMA_ROBE : ENVOY_ROBE;
		state.name = entity.plate();
		state.role = DiscipleEntity.Role.RIVAL.ordinal();
		state.activity = 0;
		state.sectRank = 0;
		state.stars = 0;
		state.talent = 0;
		state.power = 0;
		state.seated = 0.0F;
		state.natalItem.clear();
		((FabricRenderState) state).setData(MeditationPose.STRENGTH, 0.0F);
		// DESIGN 40: the client-made Sosok of the envoy (a man, its own face per entity), then the cast/duel poses and Gerak
		Sosok sosok = SosokRender.dariLama(entity, RUPA, entity.skin(), -1);
		SosokRender.ekstrak(entity, state, partialTicks, new SosokRender.RupaMasukan(RUPA, state.realm, -1, 0, 0, null, 0, 0, true),
				sosok, Gaya.NETRAL, true, sosok.skala());
		dev.kultivator.client.anim.PoseRenderData.extract(entity, state, partialTicks);
		SosokRender.selesai(entity, state, partialTicks);
		SosokRender.labelLod(entity, state, partialTicks);
	}

	@Override
	public Identifier getTextureLocation(DiscipleRenderState state) {
		return state.texture;
	}

	@Override
	protected int getModelTint(DiscipleRenderState state) {
		return ARGB.color(215, 255, 248, 226);
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
		float scale = (state.stage >= 3 ? PANGLIMA_SCALE : PLAYER_SCALE) * SosokRender.skalaGambar(state);
		poseStack.scale(scale, scale, scale);
	}

	@Override
	protected void setupRotations(DiscipleRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
		dev.kultivator.client.anim.PoseApplier.lift(state, poseStack);
		SosokRender.angkat(state, poseStack);
		super.setupRotations(state, poseStack, bodyRot, entityScale);
		SosokRender.baring(state, poseStack);
		dev.kultivator.client.anim.PoseApplier.tilt(state, poseStack);
	}

	@Override
	protected boolean shouldShowName(HeavenEnvoyEntity entity, double distanceToCameraSq) {
		return distanceToCameraSq < NAME_DISTANCE_SQ && super.shouldShowName(entity, distanceToCameraSq);
	}

	@Override
	protected Component getNameTag(HeavenEnvoyEntity entity) {
		return entity.plate();
	}

	@Override
	public void submit(DiscipleRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (SosokRender.aktif(state)) {
			this.model = state.slim ? sosokSlim : sosokWide;
		} else {
			this.model = state.slim ? slimModel : wideModel;
		}
		super.submit(state, poseStack, collector, camera);
	}

	/** The robe only within 32 blocks (16 on Minimal effects). */
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
