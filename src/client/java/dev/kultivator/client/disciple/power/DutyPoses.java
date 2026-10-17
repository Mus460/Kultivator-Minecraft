package dev.kultivator.client.disciple.power;

import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.disciple.DiscipleEntity;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;

/**
 * The looping duty poses of the Tugas Sekte (DESIGN 20.3.9, drawn by module C from {@code syncedActivity()}): carrying
 * water on a pole, tending herbs bent over, stirring a wok and scattering feed. Applied after the normal animation to
 * the disciple's body model and its robe, only while it stands (the seated activities use the meditation pose).
 */
public final class DutyPoses {

	public static void apply(PlayerModel model, AvatarRenderState avatar) {
		if (!(avatar instanceof DiscipleRenderState state) || state.seated > 0.5F) {
			return;
		}
		float t = state.ageInTicks;
		switch (state.activity) {
			case DiscipleEntity.ACTIVITY_WATER -> {
				// Two buckets on a carrying pole: both hands up at the shoulders, a slow bob.
				float bob = Mth.sin(t * 0.25F) * 0.05F;
				model.rightArm.xRot = -2.6F + bob;
				model.leftArm.xRot = -2.6F - bob;
				model.rightArm.zRot = 0.25F;
				model.leftArm.zRot = -0.25F;
				model.body.xRot = 0.05F;
			}
			case DiscipleEntity.ACTIVITY_GARDEN -> {
				// Bent over the herb beds, picking in turn.
				float pick = Mth.sin(t * 0.18F);
				model.body.xRot = 0.55F;
				model.head.xRot = 0.6F;
				model.rightArm.xRot = -1.1F + pick * 0.25F;
				model.leftArm.xRot = -1.1F - pick * 0.25F;
				model.rightArm.zRot = 0.1F;
				model.leftArm.zRot = -0.1F;
			}
			case DiscipleEntity.ACTIVITY_KITCHEN -> {
				// Stirring the spirit rice in a wok: the right hand circles, the left holds the handle.
				model.rightArm.xRot = -1.05F + Mth.sin(t * 0.3F) * 0.18F;
				model.rightArm.zRot = Mth.cos(t * 0.3F) * 0.2F;
				model.leftArm.xRot = -0.7F;
				model.leftArm.zRot = -0.15F;
				model.head.xRot = 0.45F;
			}
			case DiscipleEntity.ACTIVITY_FARM -> {
				// Scattering feed from a basket on the left arm.
				float throwing = Math.max(0.0F, Mth.sin(t * 0.22F));
				model.rightArm.xRot = -0.9F - throwing * 0.5F;
				model.rightArm.zRot = -0.25F - throwing * 0.5F;
				model.leftArm.xRot = -0.55F;
				model.leftArm.zRot = 0.3F;
				model.head.xRot = 0.3F;
			}
			default -> {
			}
		}
	}

	/** The disciple's body model: the player model plus the duty poses. */
	public static final class Model extends PlayerModel {

		public Model(net.minecraft.client.model.geom.ModelPart root, boolean slim) {
			super(root, slim);
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			apply(this, state);
		}
	}

	private DutyPoses() {
	}
}
