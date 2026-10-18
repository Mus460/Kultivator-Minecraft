package dev.kultivator.formasi.spec;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** One emitted block of a pattern (DESIGN 24.2.2): offset from the eye, canonical state, exact item, and the eye flag. */
public record PatternBlock(int dx, int dy, int dz, BlockState state, @Nullable Item exactItem, boolean core) {
}
