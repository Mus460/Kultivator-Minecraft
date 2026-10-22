package dev.kultivator.duel.session;

import dev.kultivator.Kultivator;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.dao.Comprehension;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.duel.DuelConfig;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.ai.DuelStyle;
import dev.kultivator.duel.ai.DuelistProfile;
import dev.kultivator.duel.ai.DuelistRegistry;
import dev.kultivator.duel.api.Duelist;
import dev.kultivator.duel.clash.Fighter;
import dev.kultivator.duel.net.DuelNet;
import dev.kultivator.duel.net.DuelPayloads;
import dev.kultivator.duel.voice.DuelVoices;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The end of a formal duel (DESIGN 35.2.6): stakes, records, Honour, rewards, advancements, the closing lines and the
 * {@code DuelResult} card. Called once by the session when it reaches ENDED.
 */
final class DuelResults {

	/** XP of a real win: this share of the winner's {@code rank.xpRequired()}. */
	static final double WIN_XP_SHARE = 0.08D;
	/** Sword Dao comprehension of a real win: 0.6 + 0.2 per Perfect Deflect, at most 2.0. */
	static final double WIN_DAO_BASE = 0.6D;
	static final double WIN_DAO_PER_PERFECT = 0.2D;
	static final double WIN_DAO_MAX = 2.0D;
	/** "Belajar dari kekalahan": a loss with 3+ Perfect Deflects. */
	static final double LOSS_DAO = 0.3D;
	static final int LOSS_DAO_PERFECTS = 3;
	/** The SANTO_PEDANG of step 61+ (Dewa Abadi) whose defeat gives {@code duel_sword_saint}. */
	static final int SAINT_STEP = 61;

	static void settle(DuelSession session, long now) {
		MinecraftServer server = session.level.getServer();
		LivingEntity a = session.a;
		LivingEntity b = session.b;
		LivingEntity winner = session.winner;
		LivingEntity loser = session.loser;
		int reason = session.resultReason < 0 ? DuelIds.RES_BATAL : session.resultReason;
		boolean real = winner != null && loser != null && reason != DuelIds.RES_BATAL && reason != DuelIds.RES_SERI;

		// Stakes.
		Component stakeLine;
		try {
			stakeLine = real && session.stakesTaken ? DuelStakes.award(session, winner, loser)
					: real ? DuelStakes.line(session.rules, session) : DuelStakes.refund(session);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Duel: settling the stakes of session {} failed, returning them", session.id, exception);
			DuelEscrow.get(server).returnAll(server, session.key);
			stakeLine = Component.translatable("kultivator.duel.msg.stake_returned");
		}

		// Records and Honour (players and disciples; NPCs rate with their profile's Honour).
		Fighter fa = session.fighter(a);
		Fighter fb = session.fighter(b);
		int deltaA = 0;
		int deltaB = 0;
		if (reason != DuelIds.RES_BATAL) {
			DuelRecords records = DuelRecords.get(server);
			UUID idA = recordId(a);
			UUID idB = recordId(b);
			int resultA = winner == a ? 1 : winner == b ? -1 : 0;
			long time = session.level.getGameTime();
			note(records, idA, idB, b, time, resultA, fa, winner == a && reason == DuelIds.RES_PENENTU);
			note(records, idB, idA, a, time, -resultA, fb, winner == b && reason == DuelIds.RES_PENENTU);
			if (DuelConfig.get().honour && (idA != null || idB != null)) {
				UUID keyA = idA != null ? idA : a.getUUID();
				UUID keyB = idB != null ? idB : b.getUUID();
				if (records.takePair(keyA, keyB, time)) {
					int ra = DuelSession.honourOf(a);
					int rb = DuelSession.honourOf(b);
					boolean honourStake = session.rules.stake() == DuelRuleSet.StakeKind.KEHORMATAN;
					double scoreA = resultA > 0 ? 1.0D : resultA < 0 ? 0.0D : 0.5D;
					if (idA != null) {
						deltaA = DuelRecords.delta(ra, rb, scoreA, honourStake);
						DuelRecords.Entry entry = records.entryOrNew(idA);
						entry.honour = Math.max(0, entry.honour + deltaA);
					}
					if (idB != null) {
						deltaB = DuelRecords.delta(rb, ra, 1.0D - scoreA, honourStake);
						DuelRecords.Entry entry = records.entryOrNew(idB);
						entry.honour = Math.max(0, entry.honour + deltaB);
					}
				}
			}
			if (a instanceof ServerPlayer pa && records.honour(pa.getUUID()) >= 1300) {
				Advancements.grant(pa, "duel_honour_1300");
			}
			if (b instanceof ServerPlayer pb && records.honour(pb.getUUID()) >= 1300) {
				Advancements.grant(pb, "duel_honour_1300");
			}
		}

		// Rewards and advancements of a real result.
		if (real) {
			reward(session, winner, loser, now);
			if (winner instanceof ServerPlayer player) {
				Advancements.grant(player, "duel_first_win");
				DuelistProfile beaten = DuelistRegistry.profile(loser);
				if (beaten != null && beaten.style() == DuelStyle.SANTO_PEDANG && beaten.rankStep() >= SAINT_STEP) {
					Advancements.grant(player, "duel_sword_saint");
				}
			}
			if (session.pair == DuelIds.PAIR_TONTON && session.challenger != null) {
				Advancements.grant(session.challenger, "duel_disciples_watched");
			}
			DuelVoices.npcLine(winner, DuelIds.LINE_VICTORY);
			DuelVoices.npcLine(loser, DuelIds.LINE_DEFEAT);
			DuelVoices.cheer(session, DuelIds.CHEER_END);
		}

		// Told to duelists of other modules.
		tellDuelist(a, winner == a);
		tellDuelist(b, winner == b);

		// The card.
		Component title = title(reason, winner);
		int duration = session.fightStart > 0L ? (int) Math.max(0L, (session.endTick > 0L ? session.endTick : now) - session.fightStart) : 0;
		int maxChain = Math.max(fa != null ? fa.statMaxChain : 0, fb != null ? fb.statMaxChain : 0);
		for (ServerPlayer player : session.resultRecipients()) {
			int delta = player == a ? deltaA : player == b ? deltaB : 0;
			DuelPayloads.DuelResult payload = new DuelPayloads.DuelResult(session.id, winner == null ? -1 : winner.getId(),
					loser == null ? -1 : loser.getId(), reason, fa != null ? fa.statHits : 0, fb != null ? fb.statHits : 0,
					fa != null ? fa.statPerfects : 0, fb != null ? fb.statPerfects : 0, Math.min(255, maxChain), duration, delta, title,
					stakeLine);
			DuelNet.send(player, payload);
			if (delta != 0 && (player == a || player == b)) {
				Msg.chat(player, Component.translatable("kultivator.duel.msg.honour", (delta > 0 ? "+" : "") + delta,
						DuelRecords.get(server).honour(player.getUUID())).withColor(delta > 0 ? 0x9FE0A0 : 0xE0A080));
			}
		}
		session.sendEnded(now);
		dev.kultivator.duel.api.DuelEndListeners.fire(session, winner, loser, reason);

		// A test dummy leaves with its duel.
		if (session.dummy != null && session.dummy.isAlive()) {
			session.dummy.discard();
		}
	}

	private static @Nullable UUID recordId(LivingEntity entity) {
		if (entity instanceof ServerPlayer player) {
			return player.getUUID();
		}
		if (entity instanceof DiscipleEntity disciple && disciple.role() == DiscipleEntity.Role.DISCIPLE) {
			return disciple.soulId();
		}
		return null;
	}

	private static void note(DuelRecords records, @Nullable UUID id, @Nullable UUID opponentId, LivingEntity opponent, long time, int result,
			@Nullable Fighter fighter, boolean deathblow) {
		if (id == null) {
			return;
		}
		records.note(id, opponentId != null ? opponentId : opponent.getUUID(), opponent.getName().getString(), time, result);
		DuelRecords.Entry entry = records.entryOrNew(id);
		if (fighter != null) {
			entry.perfects += fighter.statPerfects;
			entry.bindsWon += fighter.statBindsWon;
			entry.maxChain = Math.max(entry.maxChain, fighter.statMaxChain);
		}
		if (deathblow) {
			entry.deathblows++;
		}
	}

	/** XP and Sword Dao for a real win (35.2.6), once per pair per 10 minutes; "learning from defeat" for the loser. */
	private static void reward(DuelSession session, LivingEntity winner, LivingEntity loser, long now) {
		if (!DuelConfig.get().rewards || session.pair == DuelIds.PAIR_UJI) {
			return;
		}
		long fight = (session.endTick > 0L ? session.endTick : now) - session.fightStart;
		boolean loserA = loser == session.a;
		float lost = loserA ? session.startShareA - session.minShareA : session.startShareB - session.minShareB;
		boolean broke = loserA ? session.brokeA : session.brokeB;
		boolean realFight = session.fightStart > 0L && fight >= DuelSession.REAL_FIGHT_TICKS && (lost >= DuelSession.REAL_FIGHT_LOSS || broke);
		if (!realFight) {
			return;
		}
		DuelRecords records = DuelRecords.get(session.level.getServer());
		if (!records.takeReward(winner.getUUID(), loser.getUUID(), session.level.getGameTime())) {
			return;
		}
		Fighter fw = session.fighter(winner);
		Fighter fl = session.fighter(loser);
		if (winner instanceof ServerPlayer player) {
			double xp = Cultivators.get(player).rank().xpRequired() * WIN_XP_SHARE;
			if (xp > 0.0D) {
				Progression.addXp(player, xp, XpSource.SECT);
			}
			int perfects = fw != null ? fw.statPerfects : 0;
			Comprehension.grant(player, Dao.SWORD, Math.min(WIN_DAO_MAX, WIN_DAO_BASE + WIN_DAO_PER_PERFECT * perfects));
		}
		if (loser instanceof ServerPlayer player && fl != null && fl.statPerfects >= LOSS_DAO_PERFECTS) {
			Comprehension.grant(player, Dao.SWORD, LOSS_DAO);
			Msg.chat(player, Component.translatable("kultivator.duel.msg.learned").withColor(0xB8D8F0));
		}
	}

	private static void tellDuelist(LivingEntity entity, boolean won) {
		if (entity instanceof Duelist duelist) {
			try {
				duelist.onDuelEnd(won);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Duelist {} failed on the end of its duel", entity, exception);
			}
		}
	}

	/** The card title: "勝 — name", "Seri", "Dibatalkan"... */
	static Component title(int reason, @Nullable LivingEntity winner) {
		return switch (reason) {
			case DuelIds.RES_SERI -> Component.translatable("kultivator.duel.result.draw");
			case DuelIds.RES_BATAL -> Component.translatable("kultivator.duel.result.cancelled");
			case DuelIds.RES_MENYERAH -> Component.translatable("kultivator.duel.result.yield", winner == null ? Component.empty()
					: winner.getDisplayName());
			default -> Component.translatable("kultivator.duel.result.win", winner == null ? Component.empty() : winner.getDisplayName(),
					Component.translatable("kultivator.duel.reason.result." + reason));
		};
	}

	private DuelResults() {
	}
}
