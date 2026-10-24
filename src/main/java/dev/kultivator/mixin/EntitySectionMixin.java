package dev.kultivator.mixin;

import java.util.ArrayList;
import java.util.stream.Stream;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code EntitySection.getEntities()} streams a snapshot instead of the live list. {@code PersistentEntitySectionManager
 * .updateChunkStatus} walks that stream while it stops ticking and tracking a demoted chunk's entities; an unload
 * listener that adds or removes an entity in the same section then threw a ConcurrentModificationException in the
 * server tick (crash 2026-10-24, flying past an NPC Cloud Palace). The callers only read the stream once, so a copy
 * keeps their behaviour.
 */
@Mixin(EntitySection.class)
public abstract class EntitySectionMixin<T extends EntityAccess> {

	@Shadow
	@Final
	private ClassInstanceMultiMap<T> storage;

	@Inject(method = "getEntities()Ljava/util/stream/Stream;", at = @At("HEAD"), cancellable = true)
	private void kultivator$snapshot(CallbackInfoReturnable<Stream<T>> cir) {
		cir.setReturnValue(new ArrayList<>(this.storage).stream());
	}
}
