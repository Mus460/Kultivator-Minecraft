package dev.kultivator.desa;

import net.minecraft.core.BlockPos;

/** One vassal village as other modules see it (DESIGN 25.15): its bell, name, loyalty 0-100, counted villagers and whether a raid or quest threatens it. */
public record VillageView(BlockPos bell, String name, int loyalty, int villagers, boolean threatened) {
}
