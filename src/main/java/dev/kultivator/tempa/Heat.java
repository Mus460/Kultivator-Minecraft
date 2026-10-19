package dev.kultivator.tempa;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The forge's heat, read (never changed) from the block directly below it, in loaded chunks only (DESIGN 25.5.2). The heat
 * index is also the grade cap: 0 Api Sejati Bawaan (nothing below; the player's own qi, qi cost ×2), 1 Api Biasa (a lit
 * campfire), 2 Api Bumi (a magma block), 3 Api Paru Bumi (a lava source, or magma with lava right beneath it), 4 Esensi Api
 * Bumi (the same with the forge below y 0), 5 Api Surgawi (a lit soul campfire plus a Heavenly Flame seed per job, or a
 * Fire-Dao player with comprehension 90+). Sumber: RMJ ch. 156, 928; Terinspirasi BTTH Heavenly Flames.
 */
public final class Heat {

	public static final int INNATE = 0;
	public static final int COMMON = 1;
	public static final int EARTH = 2;
	public static final int LUNG = 3;
	public static final int ESSENCE = 4;
	public static final int HEAVENLY = 5;
	public static final double FIRE_DAO_HEAVENLY = 90.0D;
	public static final int PRETEND_TICKS = 1200;
	/** Fire colours for the gauge and the FX, per heat. */
	public static final int[] COLORS = {0x8FD8FF, 0xFF9A3C, 0xFF5A1E, 0xFF3A10, 0xFFD27A, 0x7FE7FF};

	/** A heat reading: the heat, and whether it is Heavenly Flame fed by a seed (consumed when a Dewa job ends). */
	public record Reading(int heat, boolean usesSeed) {
	}

	private static final Map<UUID, long[]> PRETEND = new HashMap<>();

	/** Reads the heat below {@code forge}; {@code seedReady} = a Heavenly Flame seed lies in the forge's slots. */
	public static Reading read(ServerLevel level, BlockPos forge, ServerPlayer player, boolean seedReady) {
		long[] pretend = PRETEND.get(player.getUUID());
		if (pretend != null) {
			if (level.getGameTime() < pretend[1]) {
				return new Reading((int) pretend[0], false);
			}
			PRETEND.remove(player.getUUID());
		}
		BlockPos below = forge.below();
		if (!level.isLoaded(below)) {
			return new Reading(INNATE, false);
		}
		BlockState state = level.getBlockState(below);
		if (state.is(Blocks.SOUL_CAMPFIRE) && state.getValue(CampfireBlock.LIT)) {
			CultivationData data = Cultivators.get(player);
			if (data.dao() == Dao.FIRE && data.comprehension(Dao.FIRE) >= FIRE_DAO_HEAVENLY) {
				return new Reading(HEAVENLY, false);
			}
			return seedReady ? new Reading(HEAVENLY, true) : new Reading(COMMON, false);
		}
		if (state.is(Blocks.CAMPFIRE) && state.getValue(CampfireBlock.LIT)) {
			return new Reading(COMMON, false);
		}
		boolean deep = forge.getY() < 0;
		if (state.is(Blocks.MAGMA_BLOCK)) {
			BlockPos under = below.below();
			boolean lava = level.isLoaded(under) && isLavaSource(level.getBlockState(under).getFluidState());
			return new Reading(lava ? deep ? ESSENCE : LUNG : EARTH, false);
		}
		if (isLavaSource(state.getFluidState())) {
			return new Reading(deep ? ESSENCE : LUNG, false);
		}
		return new Reading(INNATE, false);
	}

	private static boolean isLavaSource(FluidState fluid) {
		return fluid.is(FluidTags.LAVA) && fluid.isSource();
	}

	/** {@code uji tempa panas <0-5>}: pretends that heat for 60 s. */
	public static void pretend(ServerPlayer player, int heat) {
		PRETEND.put(player.getUUID(), new long[] {Math.max(INNATE, Math.min(HEAVENLY, heat)), player.level().getGameTime() + PRETEND_TICKS});
	}

	static void forget(ServerPlayer player) {
		PRETEND.remove(player.getUUID());
	}

	/** "Api Bumi". */
	public static Component name(int heat) {
		return Component.translatable("kultivator.tempa.heat." + Math.max(INNATE, Math.min(HEAVENLY, heat)));
	}

	private Heat() {
	}
}
