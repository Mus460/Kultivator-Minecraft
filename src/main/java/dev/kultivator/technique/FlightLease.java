package dev.kultivator.technique;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The shared flight lease (DESIGN 22.2.7): real flight ({@code mayfly}) is granted through one lease per player, so two
 * flight sources (the Flying Sword, Lei Zhenzi's wings) never revoke each other's flight. A second source asking while
 * another holds the lease is refused. Creative and spectator players keep their own abilities (nothing is granted or taken
 * from them). Session only; owned by {@code FlyingSword}.
 */
public final class FlightLease {

	private static final Map<UUID, FlightLease> LEASES = new HashMap<>();

	private final ServerPlayer player;
	private final String source;
	private final boolean granted;
	private boolean released;

	private FlightLease(ServerPlayer player, String source, boolean granted) {
		this.player = player;
		this.source = source;
		this.granted = granted;
	}

	static void init() {
		ServerPlayerEvents.LEAVE.register(player -> forget(player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> LEASES.remove(newPlayer.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LEASES.clear());
	}

	/** Takes the player's flight lease for {@code source}; null when another source holds it or flight is not possible. */
	public static @Nullable FlightLease acquire(ServerPlayer player, String source) {
		FlightLease held = LEASES.get(player.getUUID());
		if (held != null && !held.released) {
			return held.source.equals(source) && held.player == player ? held : null;
		}
		if (player.isSpectator() || player.isRemoved() || !player.isAlive()) {
			return null;
		}
		boolean grant = !player.isCreative();
		if (grant) {
			Abilities abilities = player.getAbilities();
			abilities.mayfly = true;
			abilities.flying = true;
			player.onUpdateAbilities();
		}
		FlightLease lease = new FlightLease(player, source, grant);
		LEASES.put(player.getUUID(), lease);
		return lease;
	}

	/** The lease the player holds now, or null. */
	public static @Nullable FlightLease holder(ServerPlayer player) {
		FlightLease lease = LEASES.get(player.getUUID());
		return lease == null || lease.released || lease.player != player ? null : lease;
	}

	/** Gives the flight back (removes {@code mayfly} unless creative or spectator). */
	public void release() {
		if (released) {
			return;
		}
		released = true;
		if (LEASES.get(player.getUUID()) == this) {
			LEASES.remove(player.getUUID());
		}
		if (granted && !player.isCreative() && !player.isSpectator()) {
			Abilities abilities = player.getAbilities();
			abilities.mayfly = false;
			abilities.flying = false;
			player.onUpdateAbilities();
		}
	}

	/**
	 * Drops a leaving player's lease and gives the flight back before the player is saved: this listener runs before the
	 * technique runner's LEAVE abort, so a holder whose own end handling finds the lease gone (Lei Zhenzi's wings) can never
	 * leave {@code mayfly} saved on a survival player. Releasing twice is harmless.
	 */
	static void forget(ServerPlayer player) {
		FlightLease lease = LEASES.get(player.getUUID());
		if (lease != null) {
			lease.release();
		}
		LEASES.remove(player.getUUID());
	}

	public ServerPlayer player() {
		return player;
	}

	public String source() {
		return source;
	}
}
