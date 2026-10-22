package dev.kultivator.client.disciple.power;

import dev.kultivator.client.disciple.DiscipleLook;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.disciple.DiscipleEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * The looping duty poses of the Tugas Sekte (DESIGN 20.3.9, drawn by module C from {@code syncedActivity()}): carrying
 * two buckets on a pole over the shoulder, tending herbs bent over with a sickle and a basket, stirring a wok with a
 * ladle and scattering feed from a basket on the arm (the props are drawn by {@code DiscipleHeldItemLayer}). Applied
 * after the normal animation to the disciple's body model and its robe, only while it stands still and calm: never while
 * it walks, fights or plays a cast pose (polish plan 2.6 [224]); it blends in and out over 6 ticks.
 */
public final class DutyPoses {

	private static final float BLEND_TICKS = 6.0F;

	/** Per disciple: whether the duty pose was on and when that changed (entity age). */
	private static final Map<Integer, float[]> BLENDS = new HashMap<>();

	public static void apply(PlayerModel model, AvatarRenderState avatar) {
		if (!(avatar instanceof DiscipleRenderState state) || state.seated > 0.5F) {
			return;
		}
		boolean duty = state.activity >= DiscipleEntity.ACTIVITY_WATER && state.activity <= DiscipleEntity.ACTIVITY_FARM
				|| dev.kultivator.client.sekte.SektePoses.handles(state.activity);
		boolean on = duty && !state.castActive && (state.flags & DiscipleLook.FIGHTING) == 0 && state.walkAnimationSpeed <= 0.1F;
		float s = blend(state.id, on, state.ageInTicks);
		if (s <= 0.0F || !duty) {
			return;
		}
		float t = state.ageInTicks;
		switch (state.activity) {
			case DiscipleEntity.ACTIVITY_WATER -> {
				// the carrying pole over the right shoulder, the right hand steadying it, the left swinging a little
				float bob = Mth.sin(t * 0.25F) * 0.04F;
				pose(model.rightArm, s, -2.9F + bob, 0.0F, -0.2F);
				pose(model.leftArm, s, -0.25F - bob, 0.0F, -0.15F);
				model.body.xRot = Mth.lerp(s, model.body.xRot, 0.05F);
				model.head.zRot = Mth.lerp(s, model.head.zRot, -0.06F);
			}
			case DiscipleEntity.ACTIVITY_GARDEN -> {
				// bent over the herb beds, cutting with the sickle in the right hand, the basket in the left
				float pick = Mth.sin(t * 0.18F);
				model.body.xRot = Mth.lerp(s, model.body.xRot, 0.55F);
				model.head.xRot = Mth.lerp(s, model.head.xRot, 0.6F);
				pose(model.rightArm, s, -1.1F + pick * 0.3F, 0.0F, 0.1F);
				pose(model.leftArm, s, -0.75F, 0.15F, -0.1F);
				bend(model, s);
			}
			case DiscipleEntity.ACTIVITY_KITCHEN -> {
				// stirring the spirit rice: the ladle circles, the left hand holds the wok's handle
				pose(model.rightArm, s, -1.05F + Mth.sin(t * 0.3F) * 0.18F, 0.0F, Mth.cos(t * 0.3F) * 0.2F);
				pose(model.leftArm, s, -0.7F, 0.0F, -0.15F);
				model.head.xRot = Mth.lerp(s, model.head.xRot, 0.45F);
			}
			case DiscipleEntity.ACTIVITY_FARM -> {
				// scattering feed from the basket on the left arm
				float throwing = Math.max(0.0F, Mth.sin(t * 0.22F));
				pose(model.rightArm, s, -0.9F - throwing * 0.5F, 0.0F, -0.25F - throwing * 0.5F);
				pose(model.leftArm, s, -0.55F, 0.0F, 0.3F);
				model.head.xRot = Mth.lerp(s, model.head.xRot, 0.3F);
			}
			// Sekte yang Hidup's activities 13-24 (DESIGN 36.3.3): the owner module's registered pose.
			default -> dev.kultivator.client.sekte.SektePoses.apply(model, state, s, t);
		}
	}

	/** True when the duty prop should be drawn (the pose is mostly on). */
	public static boolean showsProp(DiscipleRenderState state) {
		float[] b = BLENDS.get(state.id);
		if (b == null) {
			return false;
		}
		return value(b, state.ageInTicks) > 0.5F;
	}

	private static void pose(ModelPart part, float s, float x, float y, float z) {
		part.xRot = Mth.lerp(s, part.xRot, x);
		part.yRot = Mth.lerp(s, part.yRot, y);
		part.zRot = Mth.lerp(s, part.zRot, z);
	}

	/** A bent-over body moves the head and shoulders forward and down with it (like the vanilla crouch). */
	private static void bend(PlayerModel model, float s) {
		float k = s * 0.55F / 0.5F;
		model.head.y += 4.2F * k;
		model.body.y += 3.2F * k;
		model.rightArm.y += 3.2F * k;
		model.leftArm.y += 3.2F * k;
		model.rightLeg.z += 4.0F * k;
		model.leftLeg.z += 4.0F * k;
	}

	private static float blend(int id, boolean on, float now) {
		float[] b = BLENDS.get(id);
		if (b == null) {
			b = new float[] {on ? 1.0F : 0.0F, now - BLEND_TICKS};
			BLENDS.put(id, b);
		} else if ((b[0] > 0.5F) != on) {
			float current = value(b, now);
			b[0] = on ? 1.0F : 0.0F;
			b[1] = now - (on ? current : 1.0F - current) * BLEND_TICKS;
		} else if (now < b[1]) {
			b[1] = now - BLEND_TICKS;
		}
		if (BLENDS.size() > 256) {
			BLENDS.clear();
		}
		return value(b, now);
	}

	private static float value(float[] b, float now) {
		float t = Mth.clamp((now - b[1]) / BLEND_TICKS, 0.0F, 1.0F);
		float v = b[0] > 0.5F ? t : 1.0F - t;
		return v * v * (3.0F - 2.0F * v);
	}

	/** The disciple's body model: the player model plus the duty poses, then the cast pose again on top. */
	public static final class Model extends PlayerModel {

		public Model(net.minecraft.client.model.geom.ModelPart root, boolean slim) {
			super(root, slim);
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			// super already ran the cast pose (the PlayerModel mixin's TAIL); the duty pose stays off while one plays
			super.setupAnim(state);
			apply(this, state);
		}
	}

	private DutyPoses() {
	}
}
