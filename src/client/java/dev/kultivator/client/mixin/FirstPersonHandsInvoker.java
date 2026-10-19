package dev.kultivator.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Access to the first-person empty-arm draw, so a two-handed cast pose can show the empty off hand too. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public interface FirstPersonHandsInvoker {

	@Invoker("renderPlayerArm")
	void kultivator$renderPlayerArm(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, float inverseArmHeight,
			float attackValue, HumanoidArm arm, PlayerRenderState playerState);
}
