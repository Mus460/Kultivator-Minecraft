package dev.kultivator.formasi;

import dev.kultivator.formasi.core.CoreView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

/** Replaces the empty-hand sneak-use of an eye for one family (DESIGN 24.2.5), e.g. module B's node map on transport eyes. */
@FunctionalInterface
public interface UseHandler {
	InteractionResult use(ServerPlayer player, CoreView core);
}
