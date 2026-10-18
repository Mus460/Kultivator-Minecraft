package dev.kultivator.formasi.core;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A read-only snapshot of one registered (or virtual) core (DESIGN 24.14.3): {@code kind} 0 personal, 1 sect;
 * {@code dayaCenti} in centi-Daya; {@code ext} the module-B fields (node name, ...), never changed through the view.
 */
public record CoreView(int id, String spec, ResourceKey<Level> dim, BlockPos eye, int rot, UUID owner, int kind, CoreState state,
		@Nullable Component reason, boolean idle, boolean cracked, long dayaCenti, int keepers, double power, boolean virtual,
		CompoundTag ext) {

	public static final int KIND_PERSONAL = 0;
	public static final int KIND_SECT = 1;
}
