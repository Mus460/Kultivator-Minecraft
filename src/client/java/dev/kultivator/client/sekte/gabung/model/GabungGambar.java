package dev.kultivator.client.sekte.gabung.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * Shared bits of the four 41 test-entity renderers (DESIGN 41.11.3): the mob-style model space (turned to the yaw, y
 * down, 24 = the ground, the {@code BendaRenderer} convention of 38MD), band cube lists on the 32 px band atlases of
 * {@code tools/gen_gabung_assets.py}, a hit-shake curve and the common state. Client only.
 */
final class GabungGambar {

	/** The common state: yaw, game time with the partial tick, distance-based detail. */
	static class State extends EntityRenderState {
		float yaw;
		float waktu;
		boolean rinci;
	}

	/** Beyond this squared distance the glow and animated details are skipped. */
	static final double RINCI_SQ = 40.0D * 40.0D;

	private GabungGambar() {
	}

	/** Pushes the model space of an entity of yaw {@code yaw}: callers pop. */
	static void ruangModel(PoseStack poseStack, float yaw) {
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, 180.0F - yaw);
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		poseStack.translate(0.0F, -1.501F, 0.0F);
	}

	/** A cube list starting on band {@code band} of a band atlas. */
	static CubeListBuilder band(int band) {
		return CubeListBuilder.create().texOffs(0, band * 32);
	}

	/** A damped wobble 1 -> 0 over {@code lama} ticks since {@code umur} (0 once finished). */
	static float redam(float umur, float lama) {
		if (umur < 0.0F || umur >= lama) {
			return 0.0F;
		}
		float t = umur / lama;
		return (1.0F - t) * (1.0F - t);
	}
}
