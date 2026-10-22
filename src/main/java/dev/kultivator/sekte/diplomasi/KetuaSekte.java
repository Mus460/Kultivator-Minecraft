package dev.kultivator.sekte.diplomasi;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The NPC sect master (DESIGN 36.2.9 "Ketua Sekte (bos, sekali per perang)"): a body of role RIVAL at the throne of its
 * mountain gate while a player is near, or summoned to the hall by its duel letter when the world has no gate of that
 * sect. During a war the master of the hall may challenge it once a day: a duel under {@code disciple.DuelRules} (both
 * stop at 10% health, nobody dies, three minutes at most). Beating it wins the war ({@link Perang#menang}). Outside a war
 * talking to it opens the Diplomasi screen on its sect.
 */
final class KetuaSekte {

	/** Duel length (ticks). */
	static final int DUEL_TICKS = 20 * 180;
	/** A master summoned to the hall waits this long (ticks) before it leaves. */
	static final long HALL_WAIT = 20L * 60L * 5L;

	private record Boss(String sectId, @Nullable UUID hallOwner, long leaveAt) {
	}

	private static final Map<UUID, Boss> BOSSES = new HashMap<>();
	private static final Map<UUID, DiscipleEntity> ENTITIES = new HashMap<>();
	/** Owners whose hall waits for a summoned master: owner → sect id. */
	private static final Map<UUID, String> SUMMONS = new HashMap<>();

	static void clear() {
		for (DiscipleEntity body : ENTITIES.values()) {
			body.discard();
		}
		BOSSES.clear();
		ENTITIES.clear();
		SUMMONS.clear();
	}

	/** The master at its throne anchor (from {@link AnggotaSekte}). */
	static @Nullable DiscipleEntity spawnAtGate(ServerLevel level, Vec3 pos, SekteNpc sect, BlockPos anchor) {
		DiscipleEntity body = Tubuh.spawn(level, pos, sect, sect.masterRankStep(), 99,
				Component.translatable(GeneratorNama.KEY + "ketua.plat", sect.master(), sect.name()), anchor, false);
		if (body != null) {
			BOSSES.put(body.getUUID(), new Boss(sect.id, null, Long.MAX_VALUE));
			ENTITIES.put(body.getUUID(), body);
		}
		return body;
	}

	/** The master accepted to come to the hall: it appears by the stele when its master is there. */
	static void summonToHall(MinecraftServer server, UUID owner, SekteNpc sect) {
		SUMMONS.put(owner, sect.id);
		Diplomasi.tell(server, owner, Component.translatable(GeneratorNama.KEY + "pesan.ketua_datang", sect.master(), sect.name()), ChatFormatting.RED);
	}

	static boolean isBoss(DiscipleEntity body) {
		return BOSSES.containsKey(body.getUUID());
	}

	static void forget(DiscipleEntity body) {
		BOSSES.remove(body.getUUID());
		ENTITIES.remove(body.getUUID());
	}

	/** Once a second: summoned masters appear, masters past their time leave. */
	static void second(MinecraftServer server) {
		if (!SUMMONS.isEmpty()) {
			for (Map.Entry<UUID, String> entry : List.copyOf(SUMMONS.entrySet())) {
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				Halls.HallView hall = Halls.hallOf(entry.getKey());
				SekteNpc sect = DiplomasiData.get(server).sect(entry.getValue());
				if (sect == null || hall == null) {
					SUMMONS.remove(entry.getKey());
					continue;
				}
				if (player == null || !player.level().dimension().equals(hall.dim())
						|| player.distanceToSqr(Vec3.atCenterOf(hall.pos())) > 48.0D * 48.0D) {
					continue;
				}
				SUMMONS.remove(entry.getKey());
				ServerLevel level = player.level();
				BlockPos spot = Tubuh.ground(level, hall.pos().offset(0, 0, 9));
				DiscipleEntity body = Tubuh.spawn(level, Vec3.atBottomCenterOf(spot), sect, sect.masterRankStep(), 99,
						Component.translatable(GeneratorNama.KEY + "ketua.plat", sect.master(), sect.name()), spot, false);
				if (body != null) {
					BOSSES.put(body.getUUID(), new Boss(sect.id, entry.getKey(), level.getGameTime() + HALL_WAIT));
					ENTITIES.put(body.getUUID(), body);
					body.getLookControl().setLookAt(player);
					Voice.say(body, "boss.awaken", null);
					player.sendSystemMessage(Component.translatable(GeneratorNama.KEY + "pesan.ketua_tiba", sect.master()).withStyle(ChatFormatting.DARK_RED));
				}
			}
		}
		if (!BOSSES.isEmpty()) {
			for (Map.Entry<UUID, Boss> entry : List.copyOf(BOSSES.entrySet())) {
				DiscipleEntity body = ENTITIES.get(entry.getKey());
				if (body == null || body.isRemoved()) {
					forget(entry.getKey());
					continue;
				}
				if (!DuelRules.inDuel(body) && body.level().getGameTime() >= entry.getValue().leaveAt) {
					forget(entry.getKey());
					Tubuh.vanish(body);
				}
			}
		}
	}

	private static void forget(UUID id) {
		BOSSES.remove(id);
		ENTITIES.remove(id);
	}

	/** Talking to a sect master: the war duel, or the Diplomasi screen on its sect. */
	static boolean interact(ServerPlayer player, DiscipleEntity body) {
		Boss boss = BOSSES.get(body.getUUID());
		if (boss == null) {
			return false;
		}
		MinecraftServer server = player.level().getServer();
		DiplomasiData data = DiplomasiData.get(server);
		SekteNpc sect = data.sect(boss.sectId);
		if (sect == null) {
			return true;
		}
		Diplomasi.meet(server, player.getUUID(), sect);
		Relasi r = data.relasi(player.getUUID(), sect);
		if (r.perang) {
			challenge(player, body, sect, r);
		} else {
			Voice.say(body, "elder.welcome", null);
			DiplomasiPayloads.open(player, sect.id, 0);
		}
		return true;
	}

	/** The war duel with the sect master (once a day). */
	static void challenge(ServerPlayer player, DiscipleEntity body, SekteNpc sect, Relasi r) {
		MinecraftServer server = player.level().getServer();
		long today = Diplomasi.today(server);
		if (r.ketuaDay >= today) {
			player.sendSystemMessage(Component.translatable(GeneratorNama.KEY + "pesan.ketua_besok", sect.master()).withStyle(ChatFormatting.GRAY));
			return;
		}
		if (DuelRules.inDuel(player) || DuelRules.inDuel(body)) {
			return;
		}
		body.setHealth(body.getMaxHealth());
		UUID owner = player.getUUID();
		boolean started = DuelRules.begin(player, body, DUEL_TICKS, (winner, loser, reason) -> {
			MinecraftServer srv = Diplomasi.server();
			if (srv == null) {
				return;
			}
			DiplomasiData data = DiplomasiData.get(srv);
			Relasi rel = data.relasi(owner, sect);
			rel.ketuaDay = Diplomasi.today(srv);
			data.setDirty();
			body.setTarget(null);
			ServerPlayer online = srv.getPlayerList().getPlayer(owner);
			if (winner != null && winner.getUUID().equals(owner) && reason != DuelRules.Reason.CANCELLED) {
				Voice.sayHere(body, "boss.defeat", null);
				if (rel.perang) {
					Perang.menang(srv, owner, sect, online);
				}
				forget(body);
				Tubuh.vanish(body);
			} else if (reason != DuelRules.Reason.CANCELLED) {
				Voice.say(body, "boss.taunt", null);
				if (online != null) {
					online.sendSystemMessage(Component.translatable(GeneratorNama.KEY + "pesan.ketua_kalah", sect.master()).withStyle(ChatFormatting.RED));
				}
			}
		});
		if (started) {
			body.setTarget(player);
			Voice.say(body, "boss.taunt", null);
			player.sendSystemMessage(Component.translatable(GeneratorNama.KEY + "pesan.ketua_duel", sect.master()).withStyle(ChatFormatting.GOLD));
		}
	}

	private KetuaSekte() {
	}
}
