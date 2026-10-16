package dev.kultivator.cultivation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** STUB (phase 0) — owned by P1-pills. Vanilla items used as pills (DESIGN section 9). */
public final class VanillaPills {

	public static void init() {
	}

	/** Called (by the LivingEntity mixin) when the player finished eating/drinking {@code stack} (a copy). */
	public static void onConsumed(ServerPlayer player, ItemStack stack) {
	}

	private VanillaPills() {
	}
}
