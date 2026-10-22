package dev.kultivator.sekte.diplomasi;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.api.DuelEndListeners;
import dev.kultivator.duel.session.DuelChallenges;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.duel.session.DuelSession;
import dev.kultivator.duel.session.DuelSessions;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Sibuk;
import dev.kultivator.voice.Voice;
import dev.kultivator.sekte.SekteSuara;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The NPC sect master (DESIGN 36.2.9 "Ketua Sekte (bos, sekali per perang)"): a body of role RIVAL at the throne of its
 * mountain gate while a player is near, or summoned to the hall by its duel letter when the world has no gate of that
 * sect. During a war the master of the hall may challenge it once a day: a Sword Duel (R8: through {@code DuelChallenges}
 * after {@code Sibuk}, with its intro and ring; both stop at 10% health, nobody dies, three minutes at most). Beating it wins the war ({@link Perang#menang}). Outside a war
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
	/** R8: sect masters in a Sword Duel with a hall master: body id -> the fight. */
	private static final Map<UUID, Fight> FIGHTS = new HashMap<>();
	private static boolean listening;

	private record Fight(UUID owner, String sectId) {
	}

	static void clear() {
		for (DiscipleEntity body : ENTITIES.values()) {
			body.discard();
		}
		BOSSES.clear();
		ENTITIES.clear();
		SUMMONS.clear();
		FIGHTS.clear();
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
					SekteSuara.say(body, "boss.sect_master", "boss.awaken");
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
				if (!DuelRules.inDuel(body) && DuelSessions.byEntity(body) == null && body.level().getGameTime() >= entry.getValue().leaveAt) {
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
		if (DuelRules.inDuel(player) || DuelRules.inDuel(body) || DuelSessions.byEntity(player) != null || DuelSessions.byEntity(body) != null) {
			return;
		}
		// R8: the one "busy" door and the Sword Duel's own checks, intro and ring (no more bare DuelRules.begin).
		String busy = Sibuk.why(player);
		if (busy != null) {
			player.sendSystemMessage(Sibuk.text(busy).withStyle(ChatFormatting.GRAY));
			return;
		}
		Component why = DuelChallenges.selfProblem(player);
		if (why == null) {
			body.setHealth(body.getMaxHealth());
			DuelRuleSet rules = DuelRuleSet.defaults().withWin(DuelRuleSet.WinRule.SEPULUH_PERSEN).withJurus(DuelRuleSet.JurusRule.JURUS_BEBAS)
					.withIntro(true).withoutStake();
			rules = new DuelRuleSet(rules.realm(), rules.jurus(), rules.win(), rules.radius(), DUEL_TICKS, rules.introFilm(), rules.stake(), 0, 0,
					null, false);
			why = DuelChallenges.startDirect(player, player, body, DuelIds.PAIR_NPC, rules);
		}
		if (why != null) {
			player.sendSystemMessage(why.copy().withStyle(ChatFormatting.YELLOW));
			return;
		}
		FIGHTS.put(body.getUUID(), new Fight(player.getUUID(), sect.id));
		Voice.say(body, "boss.taunt", null);
		player.sendSystemMessage(Component.translatable(GeneratorNama.KEY + "pesan.ketua_duel", sect.master()).withStyle(ChatFormatting.GOLD));
	}

	/** Registers the duel-end listener once (from {@link Diplomasi#init}). */
	static void init() {
		if (!listening) {
			listening = true;
			DuelEndListeners.add(KetuaSekte::onDuelEnd);
		}
	}

	/** A Sword Duel ended: when one side is a challenged sect master, settle it on the next tick (never inside the call). */
	private static void onDuelEnd(DuelSession session, @Nullable LivingEntity winner, @Nullable LivingEntity loser) {
		for (LivingEntity side : new LivingEntity[]{session.a(), session.b()}) {
			Fight fight = side == null ? null : FIGHTS.remove(side.getUUID());
			if (fight != null && side instanceof DiscipleEntity body) {
				boolean ownerWon = winner != null && winner.getUUID().equals(fight.owner());
				boolean decided = winner != null || loser != null;
				Voice.later(1, () -> settle(fight, body, ownerWon, decided));
				return;
			}
		}
	}

	private static void settle(Fight fight, DiscipleEntity body, boolean ownerWon, boolean decided) {
		MinecraftServer srv = Diplomasi.server();
		if (srv == null) {
			return;
		}
		DiplomasiData data = DiplomasiData.get(srv);
		SekteNpc sect = data.sect(fight.sectId());
		if (sect == null) {
			return;
		}
		UUID owner = fight.owner();
		Relasi rel = data.relasi(owner, sect);
		rel.ketuaDay = Diplomasi.today(srv);
		data.setDirty();
		body.setTarget(null);
		ServerPlayer online = srv.getPlayerList().getPlayer(owner);
		if (ownerWon) {
			Voice.sayHere(body, SekteSuara.key("boss.sect_master_defeat", "boss.defeat"), null);
			if (rel.perang) {
				Perang.menang(srv, owner, sect, online);
			}
			forget(body);
			Tubuh.vanish(body);
		} else if (decided) {
			Voice.say(body, "boss.taunt", null);
			if (online != null) {
				online.sendSystemMessage(Component.translatable(GeneratorNama.KEY + "pesan.ketua_kalah", sect.master()).withStyle(ChatFormatting.RED));
			}
		}
	}

	private KetuaSekte() {
	}
}
