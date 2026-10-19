package dev.kultivator.command;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code uji hud dunia} (DESIGN 25.11): sample phase-8 boss bars for 10 s, a realm bar and the two raid bars, so the HUD
 * can be checked without opening a realm or starting a raid. The bars are plain vanilla {@link ServerBossEvent}s.
 */
public final class HudDunia {

	private static final int TICKS = 200;
	private static final List<Sample> SAMPLES = new ArrayList<>();
	private static boolean registered;

	private record Sample(long until, List<ServerBossEvent> bars) {
	}

	private HudDunia() {
	}

	/** Shows the sample bars to {@code player} for 10 s. */
	public static void show(ServerPlayer player) {
		if (!registered) {
			registered = true;
			ServerTickEvents.END_SERVER_TICK.register(server -> tick(server.overworld().getGameTime()));
		}
		ServerBossEvent realm = new ServerBossEvent(UUID.randomUUID(), Component.translatable("kultivator.alam.bar.play",
				Component.translatable("kultivator.alam.realm.heavenvoid_hall"), Component.translatable("kultivator.alam.bar.room", 2, 3), "4:32"),
				BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
		realm.setProgress(0.6F);
		ServerBossEvent raid = new ServerBossEvent(UUID.randomUUID(), Component.translatable("kultivator.musuh.raid.bar", 3, 5, 6),
				BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
		raid.setProgress(0.4F);
		ServerBossEvent seal = new ServerBossEvent(UUID.randomUUID(), Component.translatable("kultivator.musuh.raid.seal_bar", 35),
				BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
		seal.setProgress(0.35F);
		List<ServerBossEvent> bars = List.of(realm, raid, seal);
		for (ServerBossEvent bar : bars) {
			bar.addPlayer(player);
		}
		SAMPLES.add(new Sample(player.level().getServer().overworld().getGameTime() + TICKS, bars));
	}

	private static void tick(long now) {
		if (SAMPLES.isEmpty()) {
			return;
		}
		SAMPLES.removeIf(sample -> {
			if (now < sample.until()) {
				return false;
			}
			for (ServerBossEvent bar : sample.bars()) {
				bar.removeAllPlayers();
			}
			return true;
		});
	}
}
