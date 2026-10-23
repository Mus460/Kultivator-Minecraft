package dev.kultivator.sekte.langit.gerak;

import dev.kultivator.data.Cultivators;
import dev.kultivator.sekte.langit.api.LangitIds;
import dev.kultivator.sekte.langit.api.SkyHallView;
import dev.kultivator.technique.FlightLease;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Seribu Anak Tangga, the stair tracker of DESIGN 37.5.2: a player who sets out from the foot of a sky sect's Tangga Awan
 * ({@code tangga_kaki}) and reaches the Root Gate ({@code tangga_puncak}) on foot in one go (no flying, no riding, no
 * teleport, not caught by the Cloud Net, within 15 minutes) earns {@code sekte_langit/seribu_anak_tangga}, with a bell
 * from the island and a line of praise. Any sky hall's stair counts (visitors climb too). Checked every 10 ticks;
 * read-only.
 */
public final class LangitTanggaJejak {

	public static final long LIMIT = 20L * 60L * 15L;
	public static final double START_RANGE = 4.0D;
	public static final double END_RANGE = 3.5D;
	/** More than this between two checks (10 ticks) is a teleport. */
	public static final double JUMP = 8.0D;

	private record Trip(UUID owner, ResourceKey<Level> dim, long start, Vec3 last) {
	}

	private static final Map<UUID, Trip> TRIPS = new HashMap<>();

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(LangitTanggaJejak::tick);
		ServerPlayerEvents.LEAVE.register(player -> TRIPS.remove(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TRIPS.clear());
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 10 != 3) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.players().isEmpty()) {
				continue;
			}
			java.util.List<SkyHallView> halls = LangitJatuh.halls(level);
			if (halls.isEmpty()) {
				continue;
			}
			long now = level.getGameTime();
			for (ServerPlayer player : level.players()) {
				Trip trip = TRIPS.get(player.getUUID());
				if (trip == null) {
					maybeStart(player, halls, now);
					continue;
				}
				if (!trip.dim().equals(level.dimension()) || cheated(player, trip, now)) {
					TRIPS.remove(player.getUUID());
					maybeStart(player, halls, now);
					continue;
				}
				BlockPos top = LangitGerak.anchor(trip.owner(), NaikTanggaGoal.TOP);
				if (top != null && near(player.position(), top, END_RANGE)) {
					TRIPS.remove(player.getUUID());
					finish(level, player, now - trip.start());
					continue;
				}
				TRIPS.put(player.getUUID(), new Trip(trip.owner(), trip.dim(), trip.start(), player.position()));
			}
		}
		if (!TRIPS.isEmpty()) {
			Iterator<Map.Entry<UUID, Trip>> it = TRIPS.entrySet().iterator();
			while (it.hasNext()) {
				if (server.getPlayerList().getPlayer(it.next().getKey()) == null) {
					it.remove();
				}
			}
		}
	}

	private static void maybeStart(ServerPlayer player, java.util.List<SkyHallView> halls, long now) {
		if (!player.onGround() || flying(player)) {
			return;
		}
		for (SkyHallView hall : halls) {
			if (hall.tier() < NaikTanggaGoal.MIN_TIER) {
				continue;
			}
			BlockPos foot = LangitGerak.anchor(hall.owner(), NaikTanggaGoal.FOOT);
			if (foot != null && near(player.position(), foot, START_RANGE)) {
				TRIPS.put(player.getUUID(), new Trip(hall.owner(), player.level().dimension(), now, player.position()));
				return;
			}
		}
	}

	private static boolean cheated(ServerPlayer player, Trip trip, long now) {
		if (now - trip.start() > LIMIT || flying(player) || player.isPassenger()) {
			return true;
		}
		if (player.position().distanceTo(trip.last()) > JUMP) {
			return true;
		}
		return LangitJatuh.lastCaught(player.getUUID()) >= trip.start();
	}

	private static boolean flying(ServerPlayer player) {
		if (player.getAbilities().flying || player.isFallFlying() || FlightLease.holder(player) != null || player.hasEffect(MobEffects.LEVITATION)) {
			return true;
		}
		try {
			return Cultivators.get(player).flyingSword();
		} catch (RuntimeException exception) {
			return false;
		}
	}

	private static boolean near(Vec3 at, BlockPos anchor, double range) {
		double dx = at.x - (anchor.getX() + 0.5D);
		double dz = at.z - (anchor.getZ() + 0.5D);
		return dx * dx + dz * dz <= range * range && Math.abs(at.y - anchor.getY()) <= 3.0D;
	}

	private static void finish(ServerLevel level, ServerPlayer player, long ticks) {
		long seconds = Math.max(1L, ticks / 20L);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8F, 1.1F);
		Msg.chat(player, Component.translatable("kultivator.sekte_langit.gerak.tangga.seribu", seconds / 60L, seconds % 60L).withColor(0xF2D27A));
		Advancements.grant(player, LangitIds.ADV + "seribu_anak_tangga");
	}

	/** For tests: true while {@code player} is on a counted climb. */
	public static boolean tracking(@Nullable ServerPlayer player) {
		return player != null && TRIPS.containsKey(player.getUUID());
	}

	private LangitTanggaJejak() {
	}
}
