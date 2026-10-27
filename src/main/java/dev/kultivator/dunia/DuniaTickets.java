package dev.kultivator.dunia;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The only chunk tickets of DESIGN 34 (rule W5, 34.4.7, contract C34.S4): {@code kultivator:dunia}, a loading ticket that
 * expires by itself after 100 ticks, radius at most 1, at most {@link #MAX_LIVE} at once (the oldest is dropped). Used
 * before a ship's cloud leap lands and before a dimension arrival; nothing else may load chunks for this phase.
 */
public final class DuniaTickets {

	public static final long TIMEOUT = 100L;
	public static final int MAX_LIVE = 4;

	public static final TicketType TYPE = Registry.register(BuiltInRegistries.TICKET_TYPE, Kultivator.id("dunia"),
			new TicketType(TIMEOUT, TicketType.FLAG_LOADING));

	private record Live(ServerLevel level, ChunkPos pos, int radius, long until) {
	}

	private static final Deque<Live> LIVE = new ArrayDeque<>();

	static void init() {
		// the type registers with the class; a closed world must not stay referenced by the live tickets
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LIVE.clear());
	}

	/** Loads {@code pos} (radius ≤ 1) for the next 100 ticks. */
	public static void touch(ServerLevel level, ChunkPos pos, int radius) {
		int r = Math.max(0, Math.min(1, radius));
		long now = level.getGameTime();
		LIVE.removeIf(live -> live.level().getGameTime() >= live.until());
		while (LIVE.size() >= MAX_LIVE) {
			Live oldest = LIVE.pollFirst();
			if (oldest != null) {
				oldest.level().getChunkSource().removeTicketWithRadius(TYPE, oldest.pos(), oldest.radius());
			}
		}
		level.getChunkSource().addTicketWithRadius(TYPE, pos, r);
		LIVE.addLast(new Live(level, pos, r, now + TIMEOUT));
	}

	/** Tickets alive now. */
	public static int live() {
		LIVE.removeIf(live -> live.level().getGameTime() >= live.until());
		return LIVE.size();
	}

	private DuniaTickets() {
	}
}
