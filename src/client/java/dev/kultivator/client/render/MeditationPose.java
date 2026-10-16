package dev.kultivator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.ClientCultivation;
import dev.kultivator.data.VisualState;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * The cross-legged meditation pose (lotus seat) of the player model, used by the client mixins on the player renderer
 * and model. While a player's synced look says MEDITATING (or BREAKING_THROUGH without TRIBULATION) the model is
 * lowered onto the ground, the thighs point forward and cross, the hands rest on the knees palms up and the head stays
 * level; the walking animation is frozen. The pose blends in and out over {@link #BLEND_TICKS}.
 * <p>
 * Model space: y points down, the front is -z, the right limbs are at -x. Rotations are applied X first, then Y, then Z.
 */
public final class MeditationPose {

	/** 0..1 how far into the pose the player is, stored in the render state by the renderer mixin. */
	public static final RenderStateDataKey<Float> STRENGTH = RenderStateDataKey.create(() -> "kultivator:meditation_pose");

	/** How far the model sinks (blocks, before the entity scale) so the crossed legs rest on the ground. */
	public static final float LOWER = 0.55F;
	private static final float BLEND_TICKS = 8.0F;

	// Thighs: pitched forward to (nearly) horizontal and yawed towards each other so they cross in front.
	private static final float RIGHT_LEG_PITCH = -1.48F;
	private static final float LEFT_LEG_PITCH = -1.56F;
	private static final float LEG_YAW = 0.52F;
	private static final float LEG_ROLL = 0.06F;
	/** The right shin lies over the left one (model units, negative is up). */
	private static final float RIGHT_LEG_LIFT = -0.5F;
	// Arms: forward and down to the knees, a little inward, twisted so the palms face up.
	private static final float ARM_PITCH = -0.68F;
	private static final float ARM_TWIST = 0.18F;
	private static final float ARM_INWARD = 0.32F;

	/** Per entity id: whether it was posed last time and when that changed (entity age in ticks). */
	private static final Map<Integer, Blend> BLENDS = new HashMap<>();

	private static final class Blend {
		boolean on;
		float changedAt;

		Blend(boolean on, float changedAt) {
			this.on = on;
			this.changedAt = changedAt;
		}

		float value(float now) {
			float t = Mth.clamp((now - changedAt) / BLEND_TICKS, 0.0F, 1.0F);
			return on ? t : 1.0F - t;
		}
	}

	/** True when a synced look asks for the seated pose. */
	public static boolean posed(VisualState visual) {
		return visual.has(VisualState.MEDITATING) || visual.has(VisualState.BREAKING_THROUGH) && !visual.has(VisualState.TRIBULATION);
	}

	/** True when the entity may sit right now (alive, on its own feet, not swimming, gliding or sleeping). */
	public static boolean canSit(Avatar entity) {
		return entity.isAlive() && !entity.isPassenger() && !entity.isSleeping() && !entity.isFallFlying() && !entity.isSwimming();
	}

	/** 0..1 eased strength of the pose for this entity now. */
	public static float strength(Avatar entity, float partialTicks) {
		boolean on = canSit(entity) && posed(ClientCultivation.visual(entity));
		float now = entity.tickCount + partialTicks;
		Blend blend = BLENDS.get(entity.getId());
		if (blend == null) {
			// First time we see this entity: already seated if it is meditating (e.g. it just came into view).
			blend = new Blend(on, now - BLEND_TICKS);
			BLENDS.put(entity.getId(), blend);
		} else if (blend.on != on) {
			float current = blend.value(now);
			blend.on = on;
			blend.changedAt = now - (on ? current : 1.0F - current) * BLEND_TICKS;
		} else if (now < blend.changedAt) {
			// The entity's age went backwards (a new entity with the same id).
			blend.changedAt = now - BLEND_TICKS;
		}
		float t = blend.value(now);
		return t * t * (3.0F - 2.0F * t);
	}

	/** Called by the renderer mixin after the vanilla extraction: stores the strength and freezes the walk cycle. */
	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		float strength = strength(entity, partialTicks);
		((FabricRenderState) state).setData(STRENGTH, strength);
		if (strength <= 0.0F) {
			return;
		}
		state.walkAnimationSpeed = Mth.lerp(strength, state.walkAnimationSpeed, 0.0F);
		state.isCrouching = false;
		// The cape drapes back onto the ground behind the seated body instead of poking into it.
		state.capeLean = Mth.lerp(strength, state.capeLean, 55.0F);
		state.capeFlap = Mth.lerp(strength, state.capeFlap, 0.0F);
	}

	/** The pose strength stored in a render state (0 when none was stored, e.g. for other renderers' states). */
	public static float strength(AvatarRenderState state) {
		Float value = ((FabricRenderState) state).getDataOrDefault(STRENGTH, 0.0F);
		return value == null ? 0.0F : value;
	}

	/** Called at the start of the renderer's setupRotations (the pose stack is already scaled by the entity scale). */
	public static void lower(AvatarRenderState state, PoseStack poseStack) {
		float strength = strength(state);
		if (strength > 0.0F) {
			poseStack.translate(0.0F, -LOWER * strength, 0.0F);
		}
	}

	/** Called after PlayerModel.setupAnim (also for the armour, cape and ears models, which share the state). */
	public static void apply(PlayerModel model, AvatarRenderState state) {
		float s = strength(state);
		if (s <= 0.0F) {
			return;
		}
		float breath = Mth.sin(state.ageInTicks * 0.07F);

		// Head level, eyes calm, only a little turn left and right.
		model.head.xRot = Mth.lerp(s, model.head.xRot, 0.06F + breath * 0.015F);
		model.head.yRot = Mth.lerp(s, model.head.yRot, model.head.yRot * 0.45F);
		model.head.zRot = Mth.lerp(s, model.head.zRot, 0.0F);
		// Back straight.
		model.body.xRot = Mth.lerp(s, model.body.xRot, 0.0F);
		model.body.yRot = Mth.lerp(s, model.body.yRot, 0.0F);
		model.body.zRot = Mth.lerp(s, model.body.zRot, 0.0F);

		pose(model.rightLeg, s, RIGHT_LEG_PITCH, -LEG_YAW, LEG_ROLL);
		pose(model.leftLeg, s, LEFT_LEG_PITCH, LEG_YAW, -LEG_ROLL);
		model.rightLeg.y += RIGHT_LEG_LIFT * s;

		float armPitch = ARM_PITCH + breath * 0.012F;
		pose(model.rightArm, s, armPitch, ARM_TWIST, -ARM_INWARD);
		pose(model.leftArm, s, armPitch, -ARM_TWIST, ARM_INWARD);
	}

	private static void pose(ModelPart part, float strength, float xRot, float yRot, float zRot) {
		part.xRot = Mth.lerp(strength, part.xRot, xRot);
		part.yRot = Mth.lerp(strength, part.yRot, yRot);
		part.zRot = Mth.lerp(strength, part.zRot, zRot);
	}

	/** Forgets entities that are gone (called now and then from the client tick). */
	public static void cleanup(ClientLevel level) {
		BLENDS.keySet().removeIf(id -> {
			Entity entity = level.getEntity(id);
			return entity == null || entity.isRemoved();
		});
	}

	/** Forgets everything (world change). */
	public static void clear() {
		BLENDS.clear();
	}

	private MeditationPose() {
	}
}
