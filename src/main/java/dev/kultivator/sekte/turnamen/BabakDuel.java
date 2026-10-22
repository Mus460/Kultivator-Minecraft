package dev.kultivator.sekte.turnamen;

import dev.kultivator.Kultivator;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.api.DuelEndListeners;
import dev.kultivator.duel.clash.WeaponClass;
import dev.kultivator.duel.item.DuelItems;
import dev.kultivator.duel.session.DuelChallenges;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.duel.session.DuelSession;
import dev.kultivator.duel.session.DuelSessions;
import dev.kultivator.sect.event.Tournament;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One bout of the sect tournament as a Sword Duel (DESIGN 36.2.3 "babak = Duel Pedang", DESIGN 35): the two fighters
 * stand on the lei tai, {@code DuelChallenges.startDirect} opens the session with the sect rules (Bebas realm rule with its
 * underdog bonus, swords only, decided at the 10% line, ring 6, {@code detikBabak} seconds, no stake, the crowd cheering;
 * the final with the duel's intro film and 30 s more), and the session's end comes back through
 * {@link DuelEndListeners} (contract C36.T3). An empty-handed fighter is lent a wooden training sword
 * ({@code kultivator:pedang_latih}) for the bout and gives it back afterwards. Nobody dies: the duel ends at 10% health
 * and the tournament heals its fighters.
 * <p>
 * The result is queued and handed to the tournament on the next server tick (never from inside the duel's own end). A
 * session that vanishes without an end is reported as "no winner" after 2 s (the bracket then decides by strength).
 */
final class BabakDuel {

	/** The running bout of one hall. */
	private static final class Bout {
		final UUID owner;
		final LivingEntity a;
		final LivingEntity b;
		final Tournament.BoutDone done;
		final boolean lentA;
		final boolean lentB;
		int missing;
		boolean ended;

		Bout(UUID owner, LivingEntity a, LivingEntity b, Tournament.BoutDone done, boolean lentA, boolean lentB) {
			this.owner = owner;
			this.a = a;
			this.b = b;
			this.done = done;
			this.lentA = lentA;
			this.lentB = lentB;
		}
	}

	/** A decided bout waiting for the next tick. */
	private record Result(Bout bout, @Nullable LivingEntity winner, boolean leftRing) {
	}

	private static final Map<UUID, Bout> BOUTS = new HashMap<>();
	private static final ArrayDeque<Result> RESULTS = new ArrayDeque<>();
	private static final int MISSING_TICKS = 40;
	private static boolean listening;

	static void init() {
		if (!listening) {
			listening = true;
			DuelEndListeners.add(BabakDuel::onDuelEnd);
		}
	}

	/** True when {@code entity} can hold a blade for the duel (it has one, or the arena may lend one). */
	private static boolean armed(LivingEntity entity, boolean lend) {
		return WeaponClass.of(entity).blade() || lend && entity.getMainHandItem().isEmpty();
	}

	/**
	 * Starts the bout of {@code a} and {@code b} on the stage at {@code venue}; false (nothing changed) when the Sword
	 * Duel is off, the server has no room for another duel, a fighter cannot hold a blade or the session refused.
	 */
	static boolean start(UUID owner, LivingEntity a, LivingEntity b, boolean finalBout, Tournament.BoutDone done) {
		TurnamenConfig config = TurnamenConfig.get();
		if (!config.pakaiDuelPedang || BOUTS.containsKey(owner) || !DuelSessions.room()) {
			return false;
		}
		if (!armed(a, config.pinjamPedang) || !armed(b, config.pinjamPedang)) {
			return false;
		}
		boolean lentA = lend(a);
		boolean lentB = lend(b);
		int seconds = config.detikBabak + (finalBout ? 30 : 0);
		DuelRuleSet rules = DuelRuleSet.defaults()
				.withRealm(DuelRuleSet.RealmRule.BEBAS)
				.withJurus(DuelRuleSet.JurusRule.PEDANG_SAJA)
				.withWin(DuelRuleSet.WinRule.SEPULUH_PERSEN)
				.withRadius(6)
				.withIntro(finalBout)
				.withCrowd(true)
				.withoutStake();
		rules = new DuelRuleSet(rules.realm(), rules.jurus(), rules.win(), rules.radius(), seconds * 20, rules.introFilm(), rules.stake(),
				0, 0, null, rules.crowd());
		boolean own = a instanceof DiscipleEntity da && da.role() == DiscipleEntity.Role.DISCIPLE
				&& b instanceof DiscipleEntity db && db.role() == DiscipleEntity.Role.DISCIPLE;
		Bout bout = new Bout(owner, a, b, done, lentA, lentB);
		BOUTS.put(owner, bout);
		Component why;
		try {
			why = DuelChallenges.startDirect(null, a, b, own ? DuelIds.PAIR_TONTON : DuelIds.PAIR_NPC, rules);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Sect tournament: the Sword Duel bout could not start", exception);
			why = Component.literal("error");
		}
		if (why != null) {
			BOUTS.remove(owner, bout);
			giveBack(bout);
			return false;
		}
		return true;
	}

	/** Cancels the owner's running bout (its end still comes back, without a winner). */
	static void cancel(UUID owner) {
		Bout bout = BOUTS.get(owner);
		if (bout == null) {
			return;
		}
		DuelSession session = DuelSessions.byEntity(bout.a);
		if (session == null) {
			session = DuelSessions.byEntity(bout.b);
		}
		if (session != null && !session.ended()) {
			session.cancel(Component.translatable("kultivator.sekte.turnamen.bout.cancelled"));
		} else {
			finish(bout, null, false);
		}
	}

	/** True while the owner's hall fights a Sword Duel bout. */
	static boolean busy(UUID owner) {
		return BOUTS.containsKey(owner);
	}

	/** True when {@code entity} fights one of this module's bouts. */
	static boolean fighting(LivingEntity entity) {
		for (Bout bout : BOUTS.values()) {
			if (bout.a == entity || bout.b == entity) {
				return true;
			}
		}
		return false;
	}

	private static void onDuelEnd(DuelSession session, @Nullable LivingEntity winner, @Nullable LivingEntity loser) {
		for (Bout bout : BOUTS.values()) {
			if (!bout.ended && session.involves(bout.a) && session.involves(bout.b)) {
				boolean leftRing = DuelEndListeners.reason() == DuelIds.RES_KELUAR;
				finish(bout, winner, leftRing);
				return;
			}
		}
	}

	private static void finish(Bout bout, @Nullable LivingEntity winner, boolean leftRing) {
		if (bout.ended) {
			return;
		}
		bout.ended = true;
		RESULTS.add(new Result(bout, winner, leftRing));
	}

	/** Every server tick: queued results go to the tournament; vanished sessions are reported. */
	static void tick(long tick) {
		if (!RESULTS.isEmpty()) {
			List<Result> ready = new ArrayList<>(RESULTS);
			RESULTS.clear();
			for (Result result : ready) {
				BOUTS.remove(result.bout().owner, result.bout());
				giveBack(result.bout());
				try {
					result.bout().done.done(result.winner(), result.leftRing());
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.error("Sect tournament: reporting a bout failed", exception);
				}
			}
		}
		if (BOUTS.isEmpty() || tick % 10 != 0) {
			return;
		}
		for (Bout bout : List.copyOf(BOUTS.values())) {
			if (bout.ended) {
				continue;
			}
			boolean gone = DuelSessions.byEntity(bout.a) == null && DuelSessions.byEntity(bout.b) == null;
			bout.missing = gone ? bout.missing + 10 : 0;
			if (bout.missing >= MISSING_TICKS) {
				finish(bout, null, false);
			}
		}
	}

	/** Lends the training sword to an empty hand; true when it did. */
	private static boolean lend(LivingEntity entity) {
		if (!TurnamenConfig.get().pinjamPedang || WeaponClass.of(entity).blade() || !entity.getMainHandItem().isEmpty()) {
			return false;
		}
		entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(DuelItems.PEDANG_LATIH));
		return true;
	}

	/** Takes the lent swords back (only while the hand still holds the training sword). */
	private static void giveBack(Bout bout) {
		if (bout.lentA) {
			takeBack(bout.a);
		}
		if (bout.lentB) {
			takeBack(bout.b);
		}
	}

	private static void takeBack(LivingEntity entity) {
		if (entity.isAlive() && !entity.isRemoved() && entity.getMainHandItem().is(DuelItems.PEDANG_LATIH)) {
			entity.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
	}

	/** The server stopped. */
	static void clear() {
		for (Bout bout : BOUTS.values()) {
			giveBack(bout);
		}
		BOUTS.clear();
		RESULTS.clear();
	}

	private BabakDuel() {
	}
}
