package dev.kultivator.formasi.core;

import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.fuel.Upkeep;
import dev.kultivator.formasi.spec.FormationSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Virtual test cores of {@code uji formasi pasang} (DESIGN 24.10): no pattern check, no blocks, owner = caller, full Daya,
 * marked UJI. Never saved, never counted toward limits or slots, removed after the minutes or on restart.
 */
public final class VirtualCores {

	public static final int DEFAULT_MINUTES = 5;

	/** Places a virtual core of {@code spec} at {@code eye}; {@code forced} skips the hall gate. */
	public static CoreRecord place(ServerPlayer player, ServerLevel level, FormationSpec spec, BlockPos eye, int minutes, boolean forced) {
		CoreStore store = CoreTicker.store(level);
		CoreRecord record = store.createVirtual(spec.id(), eye, player.getUUID(), player.getPlainTextName());
		record.on = true;
		record.forced = forced;
		record.daya = Upkeep.capCenti(spec);
		record.expires = level.getGameTime() + Math.max(1, minutes) * 1200L;
		CoreTicker.refreshState(level, record);
		return record;
	}

	/** The virtual core nearest to {@code at} within 64 blocks, or null. */
	public static @Nullable CoreRecord nearest(ServerLevel level, Vec3 at) {
		CoreRecord best = null;
		double bestDistance = 64.0D * 64.0D;
		for (CoreRecord record : CoreTicker.store(level).all()) {
			if (!record.virtual) {
				continue;
			}
			double distance = Vec3.atCenterOf(record.eye).distanceToSqr(at);
			if (distance < bestDistance) {
				best = record;
				bestDistance = distance;
			}
		}
		return best;
	}

	/** Removes every virtual core of the level; returns how many. */
	public static int clear(ServerLevel level) {
		CoreStore store = CoreTicker.store(level);
		int removed = 0;
		for (CoreRecord record : store.list()) {
			if (record.virtual) {
				store.remove(record.id);
				removed++;
			}
		}
		return removed;
	}

	/** True when the spec id is known. */
	public static boolean known(String id) {
		return FormationRegistry.byId(id) != null;
	}

	private VirtualCores() {
	}
}
