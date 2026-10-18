package dev.kultivator.client.mixin;

import dev.kultivator.client.epic.sky.SkyDirector;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 7b sky grades (DESIGN 23.9.1): the value of the generic {@code EnvironmentAttributeProbe.getValue} (erased to
 * {@code Object}) may be replaced at RETURN by {@link SkyDirector#override}, which keeps a whitelist and returns at once
 * while no grade is alive. Client mixin config only.
 */
@Mixin(EnvironmentAttributeProbe.class)
public abstract class EnvironmentProbeMixin {

	@Inject(method = "getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;F)Ljava/lang/Object;", at = @At("RETURN"), cancellable = true)
	private void kultivator$skyGrade(EnvironmentAttribute<?> attribute, float partialTicks, CallbackInfoReturnable<Object> cir) {
		SkyDirector.override(attribute, cir);
	}
}
