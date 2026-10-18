package dev.kultivator.formasi;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Adds module fields to a player's {@code FormasiSync} (DESIGN 24.12), e.g. module B's {@code conceal} list. */
@FunctionalInterface
public interface SyncPart {
	void write(ServerPlayer viewer, CompoundTag out);
}
