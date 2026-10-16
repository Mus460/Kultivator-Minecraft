package dev.kultivator.client;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.ModAttachments;
import dev.kultivator.data.Technique;
import dev.kultivator.data.VisualState;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** The local player's cultivation as last sent by the server, plus helpers to read other players' synced look. */
public final class ClientCultivation {

	private static CultivationData data = new CultivationData();
	private static boolean received;
	/** Server game time of the snapshot and the client game time it arrived at. */
	private static long serverTime;
	private static long clientTimeAtReceive;
	private static long version;

	public static void update(Payloads.SyncSelf sync) {
		data = CultivationData.loadFromServer(sync.data());
		received = true;
		serverTime = sync.gameTime();
		Minecraft minecraft = Minecraft.getInstance();
		clientTimeAtReceive = minecraft.level == null ? 0L : minecraft.level.getGameTime();
		version++;
	}

	public static void clear() {
		data = new CultivationData();
		received = false;
		version++;
	}

	/** The latest snapshot (a blank Mortal before the first one arrives). Read only; changes are made on the server. */
	public static CultivationData data() {
		return data;
	}

	/** False until the server sent the first snapshot (e.g. on a server without the mod). */
	public static boolean received() {
		return received;
	}

	/** Increases with every snapshot, so screens can notice new data cheaply. */
	public static long version() {
		return version;
	}

	/** The server's game time now, estimated from the snapshot. */
	public static long serverGameTime() {
		Minecraft minecraft = Minecraft.getInstance();
		long now = minecraft.level == null ? clientTimeAtReceive : minecraft.level.getGameTime();
		return serverTime + Math.max(0L, now - clientTimeAtReceive);
	}

	/** Ticks until the technique is ready. */
	public static long cooldownLeft(Technique technique) {
		return data.cooldownLeft(technique, serverGameTime());
	}

	/** The synced look of any player (or NONE). */
	public static VisualState visual(Entity entity) {
		if (!(entity instanceof Player)) {
			return VisualState.NONE;
		}
		VisualState state = entity.getAttached(ModAttachments.VISUAL);
		return state == null ? VisualState.NONE : state;
	}

	private ClientCultivation() {
	}
}
