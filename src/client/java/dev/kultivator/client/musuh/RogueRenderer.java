package dev.kultivator.client.musuh;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.disciple.DiscipleHeldItemLayer;
import dev.kultivator.client.disciple.DiscipleLook;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.client.disciple.DiscipleRobeLayer;
import dev.kultivator.client.disciple.power.PhysiqueVisuals;
import dev.kultivator.client.render.MeditationPose;
import dev.kultivator.client.sosok.SosokJembatan;
import dev.kultivator.client.sosok.SosokRender;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.musuh.RogueCultivatorEntity;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.sosok.Gaya;
import dev.kultivator.sosok.Sosok;
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
 * <p>
 * DESIGN 40.4.1 D (module 40R): a rogue is a Sosok body made on the client ({@code liar.<sect 0-7>}): its old slim arms
 * decide the sex, its height and face come from the Sosok, its cloth is its demonic sect's (40K's row), the cloth mask
 * stays on top. Illusory phantoms and energy bodies wear the same look see-through. Gerak animates it (arms folded,
 * the prowling walk) under the duel and cast poses.
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
	/** DESIGN 40.4.1 D look keys of the eight demonic sects. */
	private static final String[] RUPA = {"liar.0", "liar.1", "liar.2", "liar.3", "liar.4", "liar.5", "liar.6", "liar.7"};

	/** The Sosok body (128 × 128 atlas mesh). */
	private final PlayerModel sosokWide;
	private final PlayerModel sosokSlim;
	/** The old player-skin body (last fallback). */
	private final PlayerModel wideModel;
	private final PlayerModel slimModel;

	public RogueRenderer(EntityRendererProvider.Context context) {
		super(context, new PlayerModel(SosokJembatan.tubuh(context, false), false), 0.5F);
		this.sosokWide = this.model;
		this.sosokSlim = new PlayerModel(SosokJembatan.tubuh(context, true), true);
		this.wideModel = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false);
		this.slimModel = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
		this.addLayer(new SosokRender.TanpaSosok(this, new CulledRobe(this, new DiscipleRobeLayer(this, context))));
		SosokRender.pasangLapisan(this::addLayer, this, context);
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
		// DESIGN 40: the client-made Sosok of this rogue (old slim arms = woman), its sect's look, see-through phantoms
		String key = RUPA[Mth.clamp(entity.sect(), 0, RUPA.length - 1)];
		Sosok sosok = SosokRender.dariLama(entity, key, entity.skin() * 8 + entity.sect(), entity.slim() ? 1 : 0);
		SosokRender.ekstrak(entity, state, partialTicks, new SosokRender.RupaMasukan(key, state.realm, -1, 0, 0, null, 0, 0,
				PhysiqueVisuals.translucent(state)), sosok, Gaya.NETRAL, true, sosok.skala());
		// DESIGN 35.5.5: the sword duel's poses (and any cast pose) on rogue cultivators
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
		// DESIGN 40.6.3: vanilla entity cutout for a Sosok body
		if (isBodyVisible && !forceTransparent && SosokRender.aktif(state)) {
			return RenderTypes.entityCutout(this.getTextureLocation(state));
		}
		return super.getRenderType(state, isBodyVisible, forceTransparent, appearGlowing);
	}

	@Override
	protected void scale(DiscipleRenderState state, PoseStack poseStack) {
		// the server never sets a rogue's scale attribute: the Sosok's height is drawn here
		float scale = PLAYER_SCALE * SosokRender.skalaGambar(state);
		poseStack.scale(scale, scale, scale);
	}

	/** The pose's lift (the duel's kneel) before the body turn and its tilt after it, as for players and disciples. */
	@Override
	protected void setupRotations(DiscipleRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
		dev.kultivator.client.anim.PoseApplier.lift(state, poseStack);
		SosokRender.angkat(state, poseStack);
		super.setupRotations(state, poseStack, bodyRot, entityScale);
		SosokRender.baring(state, poseStack);
		dev.kultivator.client.anim.PoseApplier.tilt(state, poseStack);
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
		if (SosokRender.aktif(state)) {
			this.model = state.slim ? sosokSlim : sosokWide;
		} else {
			this.model = state.slim ? slimModel : wideModel;
		}
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
