package dev.kultivator.client.disciple;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.anim.CastPoses;
import dev.kultivator.client.anim.ClientForms;
import dev.kultivator.client.anim.PoseApplier;
import dev.kultivator.client.anim.PoseRenderData;
import dev.kultivator.client.anim.form.FormLayer;
import dev.kultivator.client.anim.model.ModelFx;
import dev.kultivator.client.disciple.power.DutyPoses;
import dev.kultivator.client.disciple.power.NatalTreasureLayer;
import dev.kultivator.client.disciple.power.PhysiqueVisuals;
import dev.kultivator.client.fx.spec.AvatarLayer;
import dev.kultivator.client.render.MeditationPose;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Rank;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.power.PowerIds;
import dev.kultivator.technique.spec.dsl.Pose;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a disciple as a player: the player model with wide or slim arms and one of the default player skins (both
 * chosen at recruitment), its robe ({@link DiscipleRobeLayer}) and anything in its hands. While it meditates or breaks
 * through it sits cross-legged: the render state carries the {@link MeditationPose#STRENGTH} that the PlayerModel mixin
 * poses the limbs with, and the model is lowered onto the ground here. Its name tag reads "&lt;name&gt; · &lt;rank&gt;"
 * in the realm colour.
 * <p>
 * Polish pass (DESIGN 27.2, 27.4): the cast pose and form of the disciple are extracted like a player's
 * ({@link PoseRenderData}); the dragon form, the spread Kunpeng wings and the fire lotus charge come from its synced
 * power and look bits; a sword pose (or a sword disciple in a fight) puts its natal sword, or a qi sword, in the right
 * hand; the AVATAR and CLONE renderers and the form pieces are layers here too; Divine Clones fade in over 10 ticks and
 * out over the fade the server announces, with a white rim while they fade.
 */
public class DiscipleRenderer extends LivingEntityRenderer<DiscipleEntity, DiscipleRenderState, PlayerModel> {

	/** Same proportions as real players. */
	private static final float PLAYER_SCALE = 0.9375F;
	private static final double NAME_DISTANCE_SQ = 16.0D * 16.0D;
	private static final float CLONE_FADE_IN = 10.0F;
	/** Disciples whose fire lotus charge pose this renderer started (render states are fresh every frame). */
	private static final java.util.Set<Integer> LOTUS_HELD = new java.util.HashSet<>();

	private final PlayerModel wideModel;
	private final PlayerModel slimModel;

	public DiscipleRenderer(EntityRendererProvider.Context context) {
		// Phase 6: the player model plus the duty poses of module C (DESIGN 20.3.9, 20.3.22).
		super(context, new DutyPoses.Model(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
		this.wideModel = this.model;
		this.slimModel = new DutyPoses.Model(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
		this.addLayer(new DiscipleRobeLayer(this, context));
		this.addLayer(new NatalTreasureLayer(this));
		this.addLayer(new DiscipleHeldItemLayer(this));
		this.addLayer(new FormLayer<>(this));
		this.addLayer(new AvatarLayer<>(this));
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

		Rank rank = DiscipleLook.rank(entity);
		state.realm = rank.realm().ordinal();
		state.layer = rank.layer();
		state.stage = rank.stage();
		state.dao = DiscipleLook.dao(entity);
		state.rootColor = DiscipleLook.rootColor(entity);
		state.flags = DiscipleLook.flags(entity);
		state.robeColor = DiscipleLook.color(state.realm, state.dao);
		state.name = DiscipleLook.name(entity);
		// Phase 6 (module C): role, duty, sect rank, stars, talent and power looks; the natal treasure's item.
		state.role = entity.syncedRole().ordinal();
		state.activity = entity.syncedActivity();
		state.sectRank = entity.syncedSectRank();
		state.stars = entity.syncedStars();
		state.talent = entity.syncedTalent();
		state.power = entity.syncedPower();
		ItemStack natal = PhysiqueVisuals.natalStack(state.power);
		if (natal.isEmpty()) {
			state.natalItem.clear();
		} else {
			this.itemModelResolver.updateForLiving(state.natalItem, natal, ItemDisplayContext.FIXED, entity);
		}
		ItemStack open = PhysiqueVisuals.openParasol(state.power);
		if (open.isEmpty()) {
			state.parasolItem.clear();
		} else {
			this.itemModelResolver.updateForLiving(state.parasolItem, open, ItemDisplayContext.FIXED, entity);
		}

		float seated = DiscipleLook.seatStrength(entity, partialTicks);
		state.seated = seated;
		((FabricRenderState) state).setData(MeditationPose.STRENGTH, seated);
		if (seated > 0.0F) {
			state.walkAnimationSpeed = Mth.lerp(seated, state.walkAnimationSpeed, 0.0F);
			state.isCrouching = false;
		}

		// the fire lotus charge holds the lotus overhead while the bit is set (the release pose comes from PowerFx)
		boolean lotus = (state.power & PowerIds.BIT_LOTUS) != 0;
		Pose playing = CastPoses.poseOf(entity.getId());
		if (lotus && playing == null) {
			CastPoses.start(entity.getId(), Pose.CHARGE_OVERHEAD, 400);
			LOTUS_HELD.add(entity.getId());
		} else if (!lotus && LOTUS_HELD.remove(entity.getId()) && playing == Pose.CHARGE_OVERHEAD) {
			CastPoses.stop(entity.getId());
		}

		// cast pose, form and the disciple-only looks (dragon form, spread Kunpeng wings)
		PoseRenderData.extract(entity, state, partialTicks);
		List<String> extra = new ArrayList<>(2);
		if ((state.power & PowerIds.BIT_DRAGON) != 0) {
			extra.add("dragon_form");
		}
		if ((state.flags & PowerIds.BIT_KUNPENG) != 0) {
			extra.add("kunpeng_wings");
		}
		if (!extra.isEmpty()) {
			ClientForms.View view = PoseRenderData.form(state);
			List<String> effects = new ArrayList<>(view.effects());
			for (String effect : extra) {
				if (!effects.contains(effect)) {
					effects.add(effect);
				}
			}
			((FabricRenderState) state).setData(PoseRenderData.FORM, new ClientForms.View(view.id(), view.stage(), view.age(), effects));
		}

		// a sword in the hand while it swings one (or a sword disciple fights)
		CastPoses.Snapshot pose = PoseRenderData.pose(state);
		state.castActive = pose != null;
		boolean swordPose = pose != null && (pose.pose() == Pose.SWORD_SLASH || pose.pose() == Pose.SWORD_THRUST || pose.pose() == Pose.SWORD_SKY);
		boolean swordFight = state.dao == Dao.SWORD.ordinal() && (state.flags & DiscipleLook.FIGHTING) != 0;
		if (state.rightHandItemState.isEmpty() && (swordPose || swordFight) && state.seated <= 0.5F) {
			ItemStack sword = PhysiqueVisuals.handSword(state.power);
			this.itemModelResolver.updateForLiving(state.rightHandItemState, sword, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity);
			state.heldSword = true;
		} else {
			state.heldSword = false;
		}
		state.rightArmPose = state.rightHandItemState.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		state.leftArmPose = state.leftHandItemState.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;

		// Divine Clones: fade in over 10 ticks, out over the announced fade
		state.cloneFade = 1.0F;
		if (state.role == DiscipleEntity.Role.CLONE.ordinal()) {
			float in = Mth.clamp((entity.tickCount + partialTicks) / CLONE_FADE_IN, 0.0F, 1.0F);
			float out = PhysiqueVisuals.cloneFadeOut(entity.getId(), partialTicks);
			state.cloneFade = Math.min(in, out);
		}
	}

	@Override
	public Identifier getTextureLocation(DiscipleRenderState state) {
		return state.texture;
	}

	/**
	 * A greyish, slightly red-tinged body while a Qi Deviation rages through its meridians; otherwise module C's looks
	 * (phantom, clone and spirit body see-through, rival and candidate tints, gate veins, dragon form, golden glint).
	 */
	@Override
	protected int getModelTint(DiscipleRenderState state) {
		int deviating = -1;
		if ((state.flags & DiscipleLook.DEVIATING) != 0) {
			float pulse = 0.5F + 0.5F * Mth.sin(state.ageInTicks * 0.15F);
			deviating = ARGB.color(255, 220, (int) Mth.lerp(pulse, 150.0F, 185.0F), (int) Mth.lerp(pulse, 150.0F, 185.0F));
		}
		int tint = PhysiqueVisuals.modelTint(state, deviating);
		if (state.cloneFade < 1.0F && tint != -1) {
			// a fading clone: thinner and whiter
			float f = Mth.clamp(state.cloneFade, 0.0F, 1.0F);
			int alpha = Math.round(ARGB.alpha(tint) * f);
			float white = 1.0F - f;
			tint = ARGB.color(alpha, (int) Mth.lerp(white, ARGB.red(tint), 255.0F), (int) Mth.lerp(white, ARGB.green(tint), 255.0F),
					(int) Mth.lerp(white, ARGB.blue(tint), 255.0F));
		}
		return tint;
	}

	/** See-through bodies (the Void Realm spirit body, Divine Clones, phantoms) use a translucent render type. */
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
	protected void setupRotations(DiscipleRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
		MeditationPose.lower(state, poseStack);
		PoseApplier.lift(state, poseStack);
		super.setupRotations(state, poseStack, bodyRot, entityScale);
		PoseApplier.tilt(state, poseStack);
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

	/** The qi sword of a disciple without a natal sword (also used by M2's display props for disciple casters). */
	public static ItemStack qiSword() {
		return ModelFx.stack("light_sword");
	}
}
