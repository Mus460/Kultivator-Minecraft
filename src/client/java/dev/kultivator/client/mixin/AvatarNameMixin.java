package dev.kultivator.client.mixin;

import dev.kultivator.client.formasi.ConcealClient;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Concealed names (DESIGN 24.7.4, owned by P7c-katalog-ui): a player listed in {@code FormasiSync.conceal} gets no
 * nametag on this client. Cosmetic only; the tab list and the locator bar still show players.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarNameMixin {

	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("HEAD"), cancellable = true)
	private void kultivator$concealName(Avatar entity, double distanceToCameraSq, CallbackInfoReturnable<Boolean> cir) {
		if (ConcealClient.hidden(entity.getId())) {
			cir.setReturnValue(false);
		}
	}
}
