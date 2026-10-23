package dev.kultivator.sect.build.flow;

import dev.kultivator.formation.Formation;
import dev.kultivator.formation.FormationData;
import dev.kultivator.sect.hall.GrandFormation;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.langit.api.LangitApi;
import dev.kultivator.sekte.langit.api.LangitEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The runtime of module 37B (DESIGN 37.9.2), wired once from {@code LangitContent.init()}: the sky API's picture
 * ({@link LangitApiImpl}), the warded air ({@link RuangUdara}), the Ascension's queued disciples ({@link LangitAngkat}),
 * and the small per-owner state the gate and the invitations need (kept in the owner's {@code build_flow} section, so it
 * moves and is saved with the hall). The stele-move listener of the Ascension is registered by {@code HallBridge} before
 * the relocation dialog, and the commands by {@code BuildCommands}.
 */
public final class LangitBangun {

	private static boolean initialised;
	private static long ticks;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ServerTickEvents.END_SERVER_TICK.register(LangitBangun::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			if (LangitApi.get() instanceof LangitApiImpl impl) {
				impl.stop();
			}
			RuangUdara.clear();
			LangitAngkat.clear();
			LangitSesaji.clear();
		});
		RuangUdara.init();
		LangitEvents.add(new LangitEvents.Listener() {
			@Override
			public void tier(UUID owner, ServerLevel level, int tier) {
				// Blocks stand: the offering is spent for good (DESIGN 37.3.3).
				LangitSesaji.settle(owner);
			}
		});
	}

	private static void tick(MinecraftServer server) {
		ticks++;
		if (LangitApi.get() instanceof LangitApiImpl impl) {
			impl.tick(server, ticks);
		}
		RuangUdara.tick(server, ticks);
		LangitAngkat.tick(server, ticks);
	}

	// ================================================================== per-owner state

	/** True while the owner closed the gate of their sky hall ({@code /kultivasi aula langit gerbang tutup}). */
	public static boolean gateClosed(UUID owner) {
		return LangitApiImpl.data(owner).getBooleanOr("gerbang_tutup", false);
	}

	static void setGateClosed(UUID owner, boolean closed) {
		LangitApiImpl.data(owner).putBoolean("gerbang_tutup", closed);
		FlowData.dirty();
		LangitEvents.changed(owner);
	}

	/** The last world day of {@code player}'s invitation to {@code owner}'s sky hall, or {@code Long.MIN_VALUE}. */
	static long invitedUntil(UUID owner, UUID player) {
		CompoundTag invites = LangitApiImpl.data(owner).getCompoundOrEmpty("undangan");
		return invites.getLongOr(player.toString(), Long.MIN_VALUE);
	}

	static void invite(UUID owner, UUID player, long untilDay) {
		CompoundTag data = LangitApiImpl.data(owner);
		CompoundTag invites = data.getCompoundOrEmpty("undangan");
		// Old invitations go when a new one is written (the list stays short).
		long today = FlowData.day();
		CompoundTag kept = new CompoundTag();
		for (String key : invites.keySet()) {
			if (invites.getLongOr(key, Long.MIN_VALUE) >= today) {
				kept.putLong(key, invites.getLongOr(key, Long.MIN_VALUE));
			}
		}
		kept.putLong(player.toString(), untilDay);
		data.put("undangan", kept);
		FlowData.dirty();
		LangitEvents.changed(owner);
	}

	/** True when one of the owner's protective formations is grand this second (the Formasi Agung). */
	public static boolean grandActive(@Nullable MinecraftServer server, UUID owner) {
		Halls.HallView hall = Halls.hallOf(owner);
		if (server == null || hall == null || !hall.active()) {
			return false;
		}
		ServerLevel level = server.getLevel(hall.dim());
		if (level == null) {
			return false;
		}
		for (Formation formation : FormationData.get(level).all()) {
			if (formation.owner().equals(owner) && formation.active() && GrandFormation.state(level, formation, server.getTickCount()).grand()) {
				return true;
			}
		}
		return false;
	}

	private LangitBangun() {
	}
}
