package dev.kultivator.client.mixin;

import dev.kultivator.client.epic.kosmik.Kosmik3D;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Film Kosmik 3D (DESIGN 32.1.2): name tags render in the see-through text phase without depth, so while a 3D stage
 * covers the frame the names of other players and named mobs near the pocket would float over space. This clears them
 * for that frame only. Cosmetic, this client only; idle cost one boolean.
 */
@Mixin(EntityRenderer.class)
public abstract class KosmikNameTagMixin {

	@Inject(method = "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V",
			at = @At("TAIL"))
	private void kultivator$hideInStage(Entity entity, EntityRenderState state, float partialTicks, double nameTagDistance,
										double belowNameDistance, CallbackInfo ci) {
		if (Kosmik3D.covering()) {
			state.nameTag = null;
			state.scoreText = null;
		}
	}
}
