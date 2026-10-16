package dev.kultivator.mixin;

import dev.kultivator.Kultivator;
import dev.kultivator.cultivation.VanillaPills;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reports finished eating/drinking of the vanilla pills to {@link VanillaPills#onConsumed}. The used stack is
 * remembered at the start of {@code completeUsingItem} (a copy, since finishing shrinks or replaces it) and only
 * reported at the end when the use really completed. Pure HEAD/RETURN injections, so other mods hooking the same
 * method keep working; if one of them cancels the call, the remembered stack is simply dropped next time.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

	@Unique
	private @Nullable ItemStack kultivator$pillCopy;
	@Unique
	private @Nullable ItemStack kultivator$pillStack;
	@Unique
	private int kultivator$pillCount;

	@Inject(method = "completeUsingItem()V", at = @At("HEAD"))
	private void kultivator$rememberPill(CallbackInfo ci) {
		kultivator$pillCopy = null;
		kultivator$pillStack = null;
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(self instanceof ServerPlayer) || self.level().isClientSide() || !self.isUsingItem()) {
			return;
		}
		ItemStack using = self.getUseItem();
		// Same check as vanilla before it finishes the use: the stack in use is still the one in the hand.
		if (using.isEmpty() || using != self.getItemInHand(self.getUsedItemHand()) || !VanillaPills.isEdiblePill(using)) {
			return;
		}
		kultivator$pillStack = using;
		kultivator$pillCount = using.getCount();
		kultivator$pillCopy = using.copy();
	}

	@Inject(method = "completeUsingItem()V", at = @At("RETURN"))
	private void kultivator$reportPill(CallbackInfo ci) {
		ItemStack copy = kultivator$pillCopy;
		ItemStack original = kultivator$pillStack;
		int count = kultivator$pillCount;
		kultivator$pillCopy = null;
		kultivator$pillStack = null;
		if (copy == null || !((Object) this instanceof ServerPlayer player)) {
			return;
		}
		boolean finished = !player.isUsingItem() || player.getUseItem() != original || original == null || original.getCount() != count;
		if (!finished) {
			return;
		}
		try {
			VanillaPills.onConsumed(player, copy);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Kultivator pill hook failed", exception);
		}
	}
}
