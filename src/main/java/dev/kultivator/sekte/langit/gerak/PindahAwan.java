package dev.kultivator.sekte.langit.gerak;

import dev.kultivator.sekte.langit.api.LangitIds;
import dev.kultivator.util.Advancements;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The look and the record of a Formasi Pindah Awan trip (DESIGN 37.5.1; the trip itself is {@code TransportChannel}'s,
 * started by {@code TransportNetwork.travel}): when the channel starts, a blue-white pillar and a cloud whirl rise at both
 * nodes ({@code FX_PINDAH}); when the traveller stands on the far node, a wind-charge burst sounds there, a second whirl
 * closes and the first trip grants {@code sekte_langit/pindah_awan}. Server only; no block changes.
 */
public final class PindahAwan {

	/** Arrival within this many blocks of the destination eye. */
	public static final double ARRIVE_RANGE = 3.0D;
	/** A trip not seen arriving within its channel plus this many ticks is forgotten. */
	public static final long LATE = 60L;

	private record Trip(ResourceKey<Level> dim, BlockPos from, BlockPos to, long deadline) {
	}

	private static final Map<UUID, Trip> TRIPS = new HashMap<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ServerTickEvents.END_SERVER_TICK.register(PindahAwan::tick);
		ServerPlayerEvents.LEAVE.register(player -> TRIPS.remove(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TRIPS.clear());
	}

	/** From {@code TransportNetwork.travel}: {@code player} started a trip touching a Cloud Transfer node. */
	public static void started(ServerPlayer player, BlockPos fromEye, BlockPos toEye, int channelTicks) {
		init();
		ServerLevel level = player.level();
		TRIPS.put(player.getUUID(), new Trip(level.dimension(), fromEye.immutable(), toEye.immutable(),
				level.getGameTime() + Math.max(0, channelTicks) + LATE));
		LangitGerak.fx(level, LangitIds.FX_PINDAH, fromEye.above(), toEye.above());
	}

	private static void tick(MinecraftServer server) {
		if (TRIPS.isEmpty() || server.getTickCount() % 5 != 0) {
			return;
		}
		Iterator<Map.Entry<UUID, Trip>> it = TRIPS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Trip> entry = it.next();
			Trip trip = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.level().dimension().equals(trip.dim())) {
				it.remove();
				continue;
			}
			ServerLevel level = player.level();
			double dx = player.getX() - (trip.to().getX() + 0.5D);
			double dz = player.getZ() - (trip.to().getZ() + 0.5D);
			double dy = player.getY() - (trip.to().getY() + 1.0D);
			if (dx * dx + dz * dz <= ARRIVE_RANGE * ARRIVE_RANGE && Math.abs(dy) <= 4.0D) {
				it.remove();
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS,
						0.7F, 1.2F);
				LangitGerak.fx(level, LangitIds.FX_PINDAH, trip.to().above(), trip.from().above());
				Advancements.grant(player, LangitIds.ADV + "pindah_awan");
				continue;
			}
			if (level.getGameTime() > trip.deadline()) {
				it.remove();
			}
		}
	}

	private PindahAwan() {
	}
}
