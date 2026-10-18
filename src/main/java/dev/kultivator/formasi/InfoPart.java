package dev.kultivator.formasi;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Adds module fields to the {@code formasi} part of {@code HallInfo} (DESIGN 24.12), e.g. module B's {@code ext}. */
@FunctionalInterface
public interface InfoPart {
	void write(ServerPlayer viewer, UUID owner, CompoundTag formasi);
}
