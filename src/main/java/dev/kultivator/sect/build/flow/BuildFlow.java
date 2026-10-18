package dev.kultivator.sect.build.flow;

import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * The flow's heartbeat: server lifecycle, the per-tick work (preview polling, ceremony steps, bows, builder swings, the
 * status push) and once a second per online hall owner (tier missions and offers, builders, gathering returns, the
 * one-time "[Pilih Templat Sekte]" prompt). Nothing here places or changes a block; only the engine's {@code BuildPlacer}
 * does, inside the confirmed footprint (DESIGN 21.10).
 */
public final class BuildFlow {

	/** Seconds between two guarantee checks. */
	private static final int MINUTE = 60;

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(BuildFlow::setServer);
		ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
			setServer(null);
			Founding.clearSession();
			Ceremonies.clearSession();
			Builders.clearSession();
			Relocation.clearSession();
			Ledger.clearSession();
			Deposits.clearSession();
			BuildInfo.clearSession();
			BuildCommands.clearSession();
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			Founding.forget(player.getUUID());
			Relocation.forget(player.getUUID());
			Builders.setFpsLow(player.getUUID(), false);
		});
		ServerTickEvents.END_SERVER_TICK.register(BuildFlow::tick);
	}

	private static void setServer(MinecraftServer server) {
		FlowData.setServer(server);
		Ceremonies.setServer(server);
		Founding.setServer(server);
		Relocation.setServer(server);
	}

	private static void tick(MinecraftServer server) {
		if (FlowData.server() != server) {
			setServer(server);
		}
		try {
			Founding.tick(server);
			Ceremonies.tick(server);
			Builders.tick(server);
			BuildInfo.pushStatus(server);
			BuildCommands.tick(server);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Sect construction tick failed", exception);
		}
		int tick = server.getTickCount();
		if (tick % 20 != 0) {
			return;
		}
		int second = tick / 20;
		for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
			UUID owner = player.getUUID();
			if (Halls.hallOf(owner) == null) {
				continue;
			}
			try {
				second(server, player, second);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Sect construction failed for a hall", exception);
			}
		}
	}

	/** Once a second for an online owner with a hall. */
	private static void second(MinecraftServer server, ServerPlayer player, int second) {
		UUID owner = player.getUUID();
		GatherExpeditions.tick(server, owner);
		if (!KultivatorConfig.get().sectConstruction) {
			return;
		}
		BuildMissions.tick(player);
		Builders.tick20(server, owner);
		prompt(player);
		if (Math.floorMod(second + owner.hashCode(), MINUTE) == 0) {
			Upgrade.tickMinute(owner);
		}
	}

	/** The one-time chat prompt of a hall without a template (DESIGN 21.12 step 1). */
	private static void prompt(ServerPlayer player) {
		UUID owner = player.getUUID();
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null || !hall.active() || !EngineApi.registered() || EngineApi.get().template(owner) > 0
				|| FlowData.flag(owner, FlowData.F_SELF_BUILD) || FlowData.flag(owner, FlowData.F_PROMPTED)) {
			return;
		}
		FlowData.setFlag(owner, FlowData.F_PROMPTED, true);
		MutableComponent line = Component.translatable("kultivator.sectbuild.prompt").withStyle(ChatFormatting.GOLD);
		line.append(Component.literal(" "));
		line.append(Ceremonies.button("kultivator.sectbuild.button.choose_template", "/kultivasi aula bangun templat"));
		line.append(Component.literal(" "));
		line.append(Ceremonies.button("kultivator.sectbuild.button.self_build", "/kultivasi aula bangun sendiri"));
		Msg.chat(player, line);
	}

	private BuildFlow() {
	}
}
