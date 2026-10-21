package dev.kultivator.dunia.langit;

import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.dunia.api.SkyService;
import dev.kultivator.dunia.api.SpiritWeather;
import dev.kultivator.dunia.api.TreasureBirth;
import dev.kultivator.dunia.api.TreasureService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Module L's services (contract C34.L1), set into {@code DuniaApi} by {@link Langit#init()}: the sky (the running event,
 * the spirit weather of a position, L's meditation site factor) and the treasure births (spawn, live list, claim, the
 * rivals' claim). The other modules call only these and listen to {@code DuniaEvents}.
 */
final class LangitService implements SkyService, TreasureService {

	@Override
	public @Nullable SkyKind active(ServerLevel level) {
		return SkyEvents.active(level);
	}

	@Override
	public SpiritWeather weatherAt(ServerLevel level, BlockPos pos) {
		return SpiritWeathers.at(level, pos);
	}

	@Override
	public double siteFactor(ServerPlayer player) {
		return LangitSites.factor(player);
	}

	@Override
	public @Nullable TreasureBirth spawn(ServerLevel level, BlockPos pos, int tier, String kind) {
		return TreasureBirths.spawnNow(level, pos, tier, kind, false);
	}

	@Override
	public List<TreasureBirth> live(ServerLevel level) {
		return TreasureBirths.live(level);
	}

	@Override
	public boolean claim(ServerPlayer player, long id) {
		return TreasureBirths.claim(player, id);
	}

	@Override
	public boolean claimByRival(ServerLevel level, long id, String rivalId) {
		return TreasureBirths.claimByRival(level, id, rivalId);
	}
}
