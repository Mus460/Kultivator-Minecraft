package dev.kultivator.formasi.core;

import dev.kultivator.Kultivator;
import dev.kultivator.formasi.Formasi;
import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.SyncPart;
import dev.kultivator.formasi.effect.CoreContextImpl;
import dev.kultivator.formasi.effect.FormationEffect;
import dev.kultivator.formasi.own.Ownership;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.network.Net;
import dev.kultivator.network.Payloads;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code FormasiSync} (DESIGN 24.12, 24.8): per player, cores within {@link #RANGE} blocks of the same dimension and the
 * module parts (B's {@code conceal}); at most once per second per player and only on change, at most
 * {@link #PER_TICK} players handled per tick.
 */
public final class SyncSender {

	public static final double RANGE = 96.0D;
	public static final int PER_TICK = 8;
	public static final int INTERVAL = 20;

	private static final Map<UUID, CompoundTag> LAST = new HashMap<>();
	private static final Map<UUID, Long> LAST_TIME = new HashMap<>();
	private static int cursor;

	public static void tick(MinecraftServer server, long tick) {
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		if (players.isEmpty()) {
			return;
		}
		int count = Math.min(PER_TICK, players.size());
		for (int i = 0; i < count; i++) {
			cursor = Math.floorMod(cursor, players.size());
			ServerPlayer player = players.get(cursor++);
			Long last = LAST_TIME.get(player.getUUID());
			if (last != null && tick - last < INTERVAL && tick >= last) {
				continue;
			}
			LAST_TIME.put(player.getUUID(), tick);
			send(player, false);
		}
	}

	/** Composes and sends the player's sync when it changed ({@code force}: always). */
	public static void send(ServerPlayer player, boolean force) {
		CompoundTag data = compose(player);
		CompoundTag last = LAST.get(player.getUUID());
		if (!force && data.equals(last)) {
			return;
		}
		LAST.put(player.getUUID(), data);
		Net.send(player, new Payloads.FormasiSync(data.copy()));
	}

	private static CompoundTag compose(ServerPlayer player) {
		CompoundTag out = new CompoundTag();
		ListTag cores = new ListTag();
		if (player.level() instanceof ServerLevel level) {
			CoreStore store = CoreTicker.store(level);
			Vec3 at = player.position();
			for (CoreRecord record : store.all()) {
				if (Vec3.atCenterOf(record.eye).distanceToSqr(at) > RANGE * RANGE) {
					continue;
				}
				FormationSpec spec = FormationRegistry.byId(record.spec);
				CompoundTag core = new CompoundTag();
				core.putInt("id", record.id);
				core.putInt("net", spec == null ? -1 : spec.netId());
				core.putInt("x", record.eye.getX());
				core.putInt("y", record.eye.getY());
				core.putInt("z", record.eye.getZ());
				core.putInt("rot", record.rot);
				core.putFloat("r", (float) record.radius);
				core.putInt("st", record.state.ordinal());
				core.putBoolean("mem", Ownership.isMember(level.getServer(), record.owner, player));
				if (record.idle) {
					core.putBoolean("idle", true);
				}
				if (record.cracked) {
					core.putBoolean("crack", true);
				}
				if (record.flagTarget >= 0) {
					core.putInt("flag", record.flagTarget);
				}
				CompoundTag ext = record.ext.copy();
				FormationEffect effect = spec == null ? null : FormationRegistry.effectOf(spec);
				if (effect != null && record.state.running()) {
					try {
						effect.sync(new CoreContextImpl(level, record, spec, player), ext);
					} catch (RuntimeException exception) {
						Kultivator.LOGGER.error("Formation {} sync failed", spec.id(), exception);
					}
				}
				if (!ext.isEmpty()) {
					core.put("ext", ext);
				}
				cores.add(core);
			}
		}
		out.put("cores", cores);
		for (SyncPart part : Formasi.syncParts()) {
			try {
				part.write(player, out);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Formasi sync part failed", exception);
			}
		}
		return out;
	}

	/** Forgets a player's last sync (they changed dimension or rejoined). */
	public static void forget(UUID player) {
		LAST.remove(player);
		LAST_TIME.remove(player);
	}

	public static void clear() {
		LAST.clear();
		LAST_TIME.clear();
		cursor = 0;
	}

	private SyncSender() {
	}
}
