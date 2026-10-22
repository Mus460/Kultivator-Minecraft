package dev.kultivator.sekte.hubungan;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.session.DuelChallenges;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.duel.session.DuelSession;
import dev.kultivator.duel.session.DuelSessions;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.VoiceDisciples;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Duel Kehormatan, the honour duel of two disciples who bear a grudge (DESIGN 36.2.2, contract C36.R4). Once a day a
 * grudge pair asks the master with a chance of {@code peluangDuel} (10%); the master may also call it from the Hubungan
 * tab. Approved, both line up before the hall stele ({@link GiliranSosial}) and fight a Duel Pedang through
 * {@code DuelChallenges.startDirect} (the master as challenger, two of his disciples watched: {@code PAIR_TONTON}; the
 * duel's own rules stop it at 10% health, nobody dies, no stakes). Whatever the outcome, once they really fought the
 * grudge becomes a rivalry and the affinity rises by 30; the winner is honoured. The result is read each second from
 * {@code DuelSessions.byEntity} (nothing in the duel module is edited): the side with more health left won.
 * <p>
 * Sumber: the lei tai, the open challenge stage of Chinese martial tradition, where quarrels were settled by a bout before
 * witnesses.
 */
final class DuelKehormatan {

	private static final int START_GRACE = 200;
	private static final int MAX_TICKS = 20 * 60 * 6;

	private static final class Watch {
		final UUID owner;
		final DiscipleEntity a;
		final DiscipleEntity b;
		final long started;
		@Nullable DuelSession session;
		boolean fought;

		Watch(UUID owner, DiscipleEntity a, DiscipleEntity b, long started) {
			this.owner = owner;
			this.a = a;
			this.b = b;
			this.started = started;
		}
	}

	private static final List<Watch> WATCHES = new ArrayList<>();
	private static boolean registered;

	static void init() {
		if (registered) {
			return;
		}
		registered = true;
		ServerTickEvents.END_SERVER_TICK.register(DuelKehormatan::tick);
	}

	static void clear() {
		WATCHES.clear();
	}

	// ================================================================== asking

	/** The daily round: each grudge pair asks with a chance of {@code peluangDuel}. */
	static void dailyRoll(ServerPlayer master, long today) {
		HubunganConfig config = HubunganConfig.get();
		if (!config.dendamAktif || config.peluangDuel <= 0.0D) {
			return;
		}
		UUID owner = master.getUUID();
		for (TabelPasangan.Pair pair : TabelPasangan.all(owner)) {
			if (pair.kind() != Ikatan.DENDAM || Upacara.pending(owner, pair) != null) {
				continue;
			}
			if (Hubungan.random().nextDouble() < config.peluangDuel) {
				ask(owner, pair.a, pair.b, today);
			}
		}
	}

	/** Puts the request before the master (also {@code uji perguruan hubungan duel}). */
	static void ask(UUID owner, UUID a, UUID b, long today) {
		int id = TabelPasangan.addRequest(owner, Upacara.DUEL, a, b, today);
		Upacara.announce(owner, id, Upacara.DUEL, a, b);
	}

	static void answer(ServerPlayer master, TabelPasangan.Request request, boolean yes) {
		if (yes) {
			accept(master, request.a(), request.b());
		} else {
			Msg.chat(master, Component.translatable("kultivator.sekte.hubungan.minta.duel.ditolak", TabelPasangan.name(master.getUUID(), request.a()),
					TabelPasangan.name(master.getUUID(), request.b())).withColor(0xA0A0A0));
		}
	}

	static void fromScroll(ServerPlayer master, UUID a, UUID b) {
		TabelPasangan.Pair pair = TabelPasangan.find(master.getUUID(), a, b);
		TabelPasangan.Request open = pair == null ? null : Upacara.pending(master.getUUID(), pair);
		if (open != null) {
			TabelPasangan.removeRequest(master.getUUID(), open.id());
		}
		accept(master, a, b);
	}

	/** The master agreed: both line up before the stele. */
	static void accept(ServerPlayer master, UUID a, UUID b) {
		UUID owner = master.getUUID();
		TabelPasangan.Pair pair = TabelPasangan.find(owner, a, b);
		if (pair == null || pair.kind() != Ikatan.DENDAM) {
			Msg.chat(master, Component.translatable("kultivator.sekte.hubungan.alasan.bukan_dendam").withColor(0xE0A060));
			return;
		}
		Map<UUID, DiscipleEntity> bodies = Hubungan.loaded(owner);
		DiscipleEntity ea = bodies.get(a);
		DiscipleEntity eb = bodies.get(b);
		if (ea == null || eb == null) {
			Msg.chat(master, Component.translatable("kultivator.sekte.hubungan.alasan.tidak_dimuat").withColor(0xE0A060));
			return;
		}
		Component why = GiliranSosial.startRite(owner, GiliranSosial.DUEL, ea, eb);
		if (why != null) {
			Msg.chat(master, Component.translatable("kultivator.sekte.hubungan.alasan.gagal", why).withColor(0xE0A060));
			return;
		}
		Msg.chat(master, Component.translatable("kultivator.sekte.hubungan.duel.berbaris", ea.getName(), eb.getName()).withColor(0xE8B070));
	}

	static void lineupFailed(UUID owner, DiscipleEntity a, DiscipleEntity b) {
		MinecraftServer server = Hubungan.server();
		ServerPlayer master = server == null ? null : server.getPlayerList().getPlayer(owner);
		if (master != null) {
			Msg.chat(master, Component.translatable("kultivator.sekte.hubungan.duel.batal", a.getName(), b.getName()).withColor(0xA0A0A0));
		}
	}

	// ================================================================== the fight

	/** Both stand before the stele: the Duel Pedang begins. */
	static void begin(UUID owner, DiscipleEntity a, DiscipleEntity b) {
		MinecraftServer server = Hubungan.server();
		ServerPlayer master = server == null ? null : server.getPlayerList().getPlayer(owner);
		int gap = a.data().realm().ordinal() - b.data().realm().ordinal();
		DuelRuleSet rules = DuelRuleSet.suggested(gap).withoutStake().withCrowd(true);
		Component why = DuelChallenges.startDirect(master, a, b, DuelIds.PAIR_TONTON, rules);
		if (why != null) {
			if (master != null) {
				Msg.chat(master, Component.translatable("kultivator.sekte.hubungan.duel.gagal", why).withColor(0xE0A060));
			}
			return;
		}
		HubunganPayloads.bubble(a, b, HubunganPayloads.ICON_PEDANG, 80);
		SekteLog.log(server, owner, SekteIds.R, Component.translatable("kultivator.sekte.hubungan.log.duel", a.getName(), b.getName()));
		WATCHES.add(new Watch(owner, a, b, server == null ? 0L : server.getTickCount()));
	}

	private static void tick(MinecraftServer server) {
		if (WATCHES.isEmpty() || server.getTickCount() % 20 != 3) {
			return;
		}
		long now = server.getTickCount();
		for (Watch watch : List.copyOf(WATCHES)) {
			DuelSession session = DuelSessions.byEntity(watch.a);
			if (session != null) {
				watch.session = session;
				if (session.phase().ordinal() >= DuelSession.Phase.FIGHT.ordinal()) {
					watch.fought = true;
				}
				if (!session.ended() && now - watch.started < MAX_TICKS) {
					continue;
				}
			} else if (watch.session == null && now - watch.started < START_GRACE) {
				continue;
			}
			WATCHES.remove(watch);
			settle(server, watch);
		}
	}

	private static void settle(MinecraftServer server, Watch watch) {
		ServerPlayer master = server.getPlayerList().getPlayer(watch.owner);
		if (!watch.fought) {
			if (master != null) {
				Msg.chat(master, Component.translatable("kultivator.sekte.hubungan.duel.batal", watch.a.getName(), watch.b.getName())
						.withColor(0xA0A0A0));
			}
			return;
		}
		DiscipleEntity winner = health(watch.a) >= health(watch.b) ? watch.a : watch.b;
		DiscipleEntity loser = winner == watch.a ? watch.b : watch.a;
		TabelPasangan.Pair pair = TabelPasangan.getOrCreate(watch.owner, watch.a.soulId(), watch.b.soulId());
		if (pair == null) {
			return;
		}
		Ikatan was = pair.kind();
		pair.setAffinity(pair.affinity() + 30);
		pair.setWounded(false);
		pair.pushHistory(SekteIds.R + ".duel", 30, Hubungan.today());
		if (was == Ikatan.DENDAM) {
			Hubungan.setKind(watch.owner, pair, was, Ikatan.SAINGAN, watch.a, watch.b, false);
		} else {
			Hubungan.change(watch.owner, watch.a.soulId(), watch.b.soulId(), 0, SekteIds.R + ".duel", Hubungan.QUIET);
		}
		Ingatan.remember(server, winner.data(), SekteIds.R + ".duel_menang", loser.soulId(), 4, "kultivator.sekte.hubungan.ingat.duel_menang",
				loser.getName().getString());
		Ingatan.remember(server, loser.data(), SekteIds.R + ".duel_kalah", winner.soulId(), 2, "kultivator.sekte.hubungan.ingat.duel_kalah",
				winner.getName().getString());
		SekteBus.honored(server, watch.owner, winner.soulId(), SekteIds.R + ".duel", 3);
		VoiceDisciples.line(winner, "victory", 600);
		Component line = Component.translatable("kultivator.sekte.hubungan.duel.selesai", winner.getName(), loser.getName());
		SekteLog.log(server, watch.owner, SekteIds.R, line);
		if (master != null) {
			Msg.chat(master, line.copy().withColor(0xF2C66A));
		}
		if (!winner.isRemoved() && !loser.isRemoved()) {
			Upacara.scene(watch.owner, winner, loser, Upacara.BEAT_SETTLED);
		}
	}

	private static float health(DiscipleEntity body) {
		return body.isRemoved() || !body.isAlive() ? 0.0F : body.getHealth() / Math.max(1.0F, body.getMaxHealth());
	}

	private DuelKehormatan() {
	}
}
