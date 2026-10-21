package dev.kultivator.dunia.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A treasure birth (DESIGN 34.5.9, contract C34.S3): the rainbow pillar of module L. {@code tier} 1-4 (T1 Mortal-Foundation
 * .. T4 Immortal+), {@code kind} one of {@code herba inti giok pusaka api batu}, {@code born} and {@code ends} in game time
 * of {@code dim}. Immutable; the owner (L) keeps the live list, the others only read it.
 */
public record TreasureBirth(long id, ResourceKey<Level> dim, BlockPos pos, int tier, String kind, long born, long ends) {

	public TreasureBirth {
		pos = pos.immutable();
		kind = kind == null ? "herba" : kind;
	}

	/** True while {@code gameTime} lies between birth and end. */
	public boolean live(long gameTime) {
		return gameTime >= born && gameTime < ends;
	}
}
