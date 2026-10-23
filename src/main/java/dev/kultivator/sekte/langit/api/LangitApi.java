package dev.kultivator.sekte.langit.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sekte Langit as every module sees it (DESIGN 37.9.1): a static facade over the implementation the build module (37B)
 * sets from {@code LangitContent.init()} ({@code sect.build.flow.LangitApiImpl}). Every method has a safe default (no
 * sky halls), so 37M, 37V and 37G work whether or not the engine is present. Server thread only, except
 * {@link #at}/{@link #underSkyIsland} which only read a volatile cache and are safe from worldgen/spawn checks.
 */
public interface LangitApi {

	LangitApi NONE = new LangitApi() {
	};

	/** The registered implementation, or {@link #NONE}. */
	static LangitApi get() {
		return Holder.api;
	}

	/** Called once by {@code LangitContent.init()}; null restores {@link #NONE}. */
	static void set(@Nullable LangitApi api) {
		Holder.api = api == null ? NONE : api;
	}

	/** True when {@code owner}'s hall stands (not tethered) on a sky island. */
	default boolean isSkyHall(UUID owner) {
		return false;
	}

	/** {@code owner}'s sky site, tethered or not; null without one. */
	default @Nullable SkyHallView skyHall(UUID owner) {
		return null;
	}

	/** The sky hall whose warded volume (or stair column) holds {@code pos}, or null. */
	default @Nullable SkyHallView at(Level level, BlockPos pos) {
		return null;
	}

	/** Every sky site of {@code level} (tethered ones included). */
	default List<SkyHallView> all(ServerLevel level) {
		return List.of();
	}

	/** True when {@code pos} lies in the footprint column of a sky island, below its lowest block (its shadow). */
	default boolean underSkyIsland(Level level, BlockPos pos) {
		return false;
	}

	/** A named anchor of {@code owner}'s sky site ("tahta", "arrival", "dermaga_0_p_s", "feri_atas"...), or null. */
	default @Nullable BlockPos anchor(UUID owner, String id) {
		return null;
	}

	/** Every anchor of {@code owner}'s sky site (id → world position), empty without one. */
	default Map<String, BlockPos> anchors(UUID owner) {
		return Map.of();
	}

	/** The owner, a sect mate of the owner, a disciple of the owner, or a tame animal of one of them. */
	default boolean member(UUID owner, Entity entity) {
		return false;
	}

	/** True while {@code player} holds a valid invitation ("name in the register") to {@code owner}'s sky hall. */
	default boolean invited(UUID owner, UUID player) {
		return false;
	}

	/** Keeps the registered implementation (not part of the API). */
	final class Holder {
		private static volatile LangitApi api = NONE;

		private Holder() {
		}
	}
}
