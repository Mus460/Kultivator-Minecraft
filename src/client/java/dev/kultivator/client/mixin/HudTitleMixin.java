package dev.kultivator.client.mixin;

import dev.kultivator.client.epic.screen.TitleCard;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No double titles (DESIGN 23.8.3): while a 7b card shows for a SUPPRESS_TITLE beat, the vanilla title and subtitle the
 * server sends for the same beat are dropped on this client only. Only calls {@link TitleCard}.
 */
@Mixin(Hud.class)
public abstract class HudTitleMixin {

	@Inject(method = {"setTitle(Lnet/minecraft/network/chat/Component;)V", "setSubtitle(Lnet/minecraft/network/chat/Component;)V"},
			at = @At("HEAD"), cancellable = true)
	private void kultivator$cardTitle(Component text, CallbackInfo ci) {
		if (TitleCard.suppressVanillaTitle()) {
			ci.cancel();
		}
	}
}
