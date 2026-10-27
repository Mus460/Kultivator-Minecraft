package dev.kultivator.duel.session;

import dev.kultivator.Kultivator;
import dev.kultivator.alam.Alam;
import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.duel.Duel;
import dev.kultivator.duel.DuelConfig;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.ai.DuelistBrain;
import dev.kultivator.duel.ai.DuelistGoal;
import dev.kultivator.duel.ai.DuelistProfile;
import dev.kultivator.duel.ai.DuelistRegistry;
import dev.kultivator.duel.clash.ClashClock;
import dev.kultivator.duel.clash.CompanionSword;
import dev.kultivator.duel.clash.Fighter;
import dev.kultivator.duel.clash.Fighters;
import dev.kultivator.duel.clash.HitTest;
import dev.kultivator.duel.clash.Posture;
import dev.kultivator.duel.clash.QiWave;
import dev.kultivator.duel.net.DuelNet;
import dev.kultivator.duel.net.DuelPayloads;
import dev.kultivator.duel.voice.DuelVoices;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One formal sword duel (DESIGN 35.2): the state machine GATHER → INTRO → COUNTDOWN → FIGHT → FINISHER → ENDED (the
 * INVITE phase lives in {@link DuelChallenges}), its ring, marks, stakes, the seated sect spectators, the AI goals of
 * non-player duelists and the payloads that keep every client within 64 blocks in step. The fight itself runs on top of
 * the existing {@code DuelRules} (10% line, 5 s grace): nobody dies in a formal duel and this class never sets health
 * to 0. Server thread only; memory only (a stopped server returns the stakes, {@link DuelEscrow}).
 */
public final class DuelSession {

	/** The phases (35.2.2); the ordinal is {@code DuelIds.PHASE_*}. */
	public enum Phase {
		INVITE,
		GATHER,
		INTRO,
		COUNTDOWN,
		FIGHT,
		FINISHER,
		ENDED;

		/** True while both fighters are held in place (no movement, no swings). */
		public boolean holds() {
			return this == INTRO || this == COUNTDOWN || this == FINISHER;
		}
	}

	public static final int GATHER_TICKS = 100;
	public static final int INTRO_FILM_TICKS = 100;
	public static final int INTRO_SHORT_TICKS = 40;
	public static final int COUNTDOWN_TICKS = 60;
	public static final int FINISHER_TICKS = 60;
	public static final double MARK_REACHED = 1.5D;
	public static final double GATHER_MAX_DISTANCE = 48.0D;
	public static final double UNSAFE_RADIUS = 24.0D;
	public static final double VIEW = 64.0D;
	public static final int SYNC_EVERY = 40;
	/** Sect arena: at most this many idle own disciples are seated around the ring. */
	public static final int MAX_SPECTATORS = 12;
	/** A crouching player who stays still this long during FIGHT bows out (surrender). */
	public static final int BOW_TICKS = 40;
	/** A draw at the time limit when the health shares are this close. */
	public static final float DRAW_MARGIN = 0.02F;
	/** "Real fight" for rewards: at least this long, and the loser lost 40% health or broke posture once. */
	public static final int REAL_FIGHT_TICKS = 300;
	public static final float REAL_FIGHT_LOSS = 0.40F;

	private record Seat(DiscipleEntity disciple, int activity, @Nullable BlockPos spot) {
	}

	final int id;
	/** The escrow key (unique over restarts). */
	final String key = UUID.randomUUID().toString();
	final int pair;
	final DuelRuleSet rules;
	final LivingEntity a;
	final LivingEntity b;
	/** The player who challenged (for TONTON the watcher; null when an NPC offered and the player is A). */
	final @Nullable ServerPlayer challenger;
	final ServerLevel level;

	Phase phase = Phase.GATHER;
	long phaseStart;
	int phaseLength = GATHER_TICKS;
	DuelRing ring;
	Vec3 markA;
	Vec3 markB;
	boolean sectArena;
	private final List<Seat> seats = new ArrayList<>();
	private final Map<LivingEntity, Seat> duellistActivity = new LinkedHashMap<>();
	private final Map<Mob, DuelistGoal> goals = new LinkedHashMap<>();

	/** Stake slips: the spec each side put up (GULUNGAN). */
	@Nullable String slipA;
	@Nullable String slipB;
	boolean stakesTaken;

	// ---------------------------------------------------------------- the result
	@Nullable LivingEntity winner;
	@Nullable LivingEntity loser;
	int resultReason = -1;
	/** Set before a DuelRules end that needs a reason of its own (PENENTU, MENYERAH, SERI). */
	int pendingReason = -1;
	boolean decided;
	boolean decisive;
	boolean decisiveSent;
	boolean surrender;
	long fightStart;
	long endTick;
	float startShareA = 1.0F;
	float startShareB = 1.0F;
	float minShareA = 1.0F;
	float minShareB = 1.0F;
	boolean brokeA;
	boolean brokeB;
	int outA;
	int outB;
	int bowA;
	int bowB;
	private Vec3 lastPosA = Vec3.ZERO;
	private Vec3 lastPosB = Vec3.ZERO;
	private boolean tundaA;
	private boolean tundaB;
	private long wildHurtAt = Long.MIN_VALUE / 4;
	private long lastSync = Long.MIN_VALUE / 4;
	private final Set<UUID> viewers = new HashSet<>();
	private boolean ended;
	/** The dummy of a test duel (removed at the end). */
	@Nullable LivingEntity dummy;

	DuelSession(int id, int pair, DuelRuleSet rules, LivingEntity a, LivingEntity b, @Nullable ServerPlayer challenger) {
		this.id = id;
		this.pair = pair;
		this.rules = rules;
		this.a = a;
		this.b = b;
		this.challenger = challenger;
		this.level = (ServerLevel) a.level();
	}

	// ================================================================ API (C35.S2)

	public int id() {
		return id;
	}

	public Phase phase() {
		return phase;
	}

	/** {@code DuelIds.PAIR_*}. */
	public int pair() {
		return pair;
	}

	public DuelRuleSet rules() {
		return rules;
	}

	public LivingEntity a() {
		return a;
	}

	public LivingEntity b() {
		return b;
	}

	public DuelRing ring() {
		return ring;
	}

	public @Nullable ServerPlayer challenger() {
		return challenger;
	}

	/** The other duellist of {@code entity} (null when it is not one of the two). */
	public @Nullable LivingEntity partnerOf(@Nullable LivingEntity entity) {
		return entity == a ? b : entity == b ? a : null;
	}

	public boolean involves(@Nullable LivingEntity entity) {
		return entity != null && (entity == a || entity == b);
	}

	/** True once the result is known (FINISHER and ENDED). */
	public boolean decided() {
		return decided;
	}

	public boolean sectArena() {
		return sectArena;
	}

	public Vec3 mark(LivingEntity entity) {
		return entity == a ? markA : markB;
	}

	public long phaseTicks(long now) {
		return now - phaseStart;
	}

	public boolean ended() {
		return ended;
	}

	/** The Duel Dummy of a test duel: removed when the duel ends. */
	public void setDummy(@Nullable LivingEntity body) {
		dummy = body;
	}

	// ================================================================ start

	/** From {@link DuelSessions#start}: ring, marks, arena, fighters, AI; then GATHER. */
	void start(long now) {
		Vec3 preferred = null;
		if (challenger != null && pair != DuelIds.PAIR_UJI) {
			Halls.HallView hall = Halls.hallOf(challenger.getUUID());
			Vec3 mid = a.position().add(b.position()).scale(0.5D);
			if (hall != null && hall.active() && hall.dim().equals(level.dimension())
					&& mid.distanceTo(Vec3.atBottomCenterOf(hall.pos())) <= DuelRing.SECT_SNAP) {
				preferred = Vec3.atBottomCenterOf(hall.pos()).add(0.0D, 1.0D, 0.0D);
				sectArena = true;
			}
		}
		ring = DuelRing.between(level, a.position(), b.position(), rules.radius(), preferred);
		Vec3[] marks = ring.marks(a.position(), b.position());
		markA = marks[0];
		markB = marks[1];
		Fighters.enterSession(a, this, b);
		Fighters.enterSession(b, this, a);
		installAi(a, b);
		installAi(b, a);
		for (LivingEntity side : new LivingEntity[] {a, b}) {
			if (side instanceof DiscipleEntity disciple) {
				duellistActivity.put(disciple, new Seat(disciple, disciple.activity(), disciple.activitySpot()));
				disciple.setActivity(DiscipleEntity.ACTIVITY_DUELLIST, null);
			}
		}
		if (sectArena) {
			seatSpectators();
		}
		enter(Phase.GATHER, GATHER_TICKS, now);
		for (LivingEntity side : new LivingEntity[] {a, b}) {
			if (side instanceof ServerPlayer player) {
				Msg.actionBar(player, Component.translatable("kultivator.duel.msg.gather").withColor(0xE8D9A8));
			}
		}
	}

	private void installAi(LivingEntity self, LivingEntity partner) {
		if (self instanceof ServerPlayer || !(self instanceof Mob mob)) {
			return;
		}
		DuelistProfile profile = DuelistRegistry.profile(self);
		Fighter fighter = Fighters.get(self);
		DuelistBrain brain = new DuelistBrain(fighter, mob, partner, this, profile);
		Fighters.setBrain(self, brain);
		DuelistGoal goal = DuelistGoal.install(mob, this, brain);
		if (goal != null) {
			goals.put(mob, goal);
		}
	}

	/** The challenger's idle disciples near the hall take seats around the ring (35.2.3). */
	private void seatSpectators() {
		if (challenger == null) {
			return;
		}
		Vec3 center = ring.center();
		double seatRadius = ring.radius() + 3.0D;
		List<DiscipleEntity> idle = new ArrayList<>();
		for (DiscipleEntity disciple : level.getEntitiesOfClass(DiscipleEntity.class, new AABB(center, center).inflate(48.0D, 12.0D, 48.0D),
				d -> d.isAlive() && d.isOwnedBy(challenger) && d.role() == DiscipleEntity.Role.DISCIPLE && d.activity() == 0
						&& !involves(d) && !DuelRules.inDuel(d) && !dev.kultivator.cultivation.DaoProtectors.isProtector(d))) {
			idle.add(disciple);
		}
		int count = Math.min(MAX_SPECTATORS, idle.size());
		for (int i = 0; i < count; i++) {
			DiscipleEntity disciple = idle.get(i);
			double angle = Math.PI * 2.0D * i / Math.max(1, count) + Math.PI / Math.max(2, count);
			Vec3 at = DuelRing.ground(level, center.add(Math.cos(angle) * seatRadius, 0.0D, Math.sin(angle) * seatRadius), center.y);
			BlockPos spot = BlockPos.containing(at.x, at.y + 0.1D, at.z);
			seats.add(new Seat(disciple, disciple.activity(), disciple.activitySpot()));
			disciple.setActivity(DiscipleEntity.ACTIVITY_SPECTATOR, spot);
			// it watches the ring from its seat
			disciple.setActivityFocus(BlockPos.containing(center));
		}
	}

	// ================================================================ the tick

	void tick(long now) {
		if (ended) {
			return;
		}
		if (phase == Phase.ENDED) {
			finish(now);
			return;
		}
		Component problem = problem(now);
		if (problem != null) {
			cancel(problem);
			return;
		}
		long age = now - phaseStart;
		switch (phase) {
			case GATHER -> {
				boolean ready = reached(a, markA) && reached(b, markB);
				if (ready || age >= phaseLength) {
					startIntro(now);
				}
			}
			case INTRO -> {
				if (age >= phaseLength) {
					enter(Phase.COUNTDOWN, COUNTDOWN_TICKS, now);
				}
			}
			case COUNTDOWN -> {
				if (age >= phaseLength) {
					startFight(now);
				}
			}
			case FIGHT -> tickFight(now, age);
			case FINISHER -> {
				if (!decisiveSent && decisive) {
					sendDecisive();
				}
				if (age >= phaseLength) {
					enter(Phase.ENDED, 1, now);
				}
			}
			default -> {
			}
		}
		if (phase != Phase.ENDED) {
			syncViewers(now);
		}
	}

	/** A reason to cancel now, or null (35.2.2: auto cancels with the stakes returned). */
	private @Nullable Component problem(long now) {
		for (LivingEntity side : new LivingEntity[] {a, b}) {
			if (side.isRemoved() || !side.isAlive() || side.level() != level) {
				return Component.translatable("kultivator.duel.reason.gone", side.getDisplayName());
			}
			if (side instanceof ServerPlayer player) {
				if (player.hasDisconnected()) {
					return Component.translatable("kultivator.duel.reason.gone", side.getDisplayName());
				}
				if (player.isCreative() || player.isSpectator()) {
					return Component.translatable("kultivator.duel.reason.mode", side.getDisplayName());
				}
				CultivationData data = Cultivators.get(player);
				if (Breakthrough.isBusy(player) || data.breakingThrough() || Cultivators.tribulationFlag.test(player)) {
					return Component.translatable("kultivator.duel.reason.breakthrough", side.getDisplayName());
				}
				if (Alam.inRun(player)) {
					return Component.translatable("kultivator.duel.reason.realm", side.getDisplayName());
				}
			}
		}
		if (phase == Phase.GATHER && a.distanceTo(b) > GATHER_MAX_DISTANCE) {
			return Component.translatable("kultivator.duel.reason.far");
		}
		if ((phase == Phase.GATHER || phase == Phase.INTRO) && (now + id) % 10 == 0 && unsafe()) {
			return Component.translatable("kultivator.duel.reason.unsafe");
		}
		return null;
	}

	/** Another monster (not one of the pair) within 24 blocks of the ring. */
	private boolean unsafe() {
		Vec3 center = ring.center();
		AABB box = new AABB(center, center).inflate(UNSAFE_RADIUS, 12.0D, UNSAFE_RADIUS);
		return !level.getEntitiesOfClass(Mob.class, box, mob -> mob.isAlive() && mob instanceof Enemy && mob != a && mob != b
				&& !(mob instanceof DiscipleEntity) && mob.distanceToSqr(center) <= UNSAFE_RADIUS * UNSAFE_RADIUS).isEmpty();
	}

	private static boolean reached(LivingEntity entity, Vec3 mark) {
		double dx = entity.getX() - mark.x;
		double dz = entity.getZ() - mark.z;
		return dx * dx + dz * dz <= MARK_REACHED * MARK_REACHED;
	}

	private void startIntro(long now) {
		// The stakes leave the inventories now (both proved they have them); a missing stake cancels the duel.
		if (rules.stake().items() && !stakesTaken) {
			Component missing = DuelStakes.collect(this);
			if (missing != null) {
				cancel(missing);
				return;
			}
		}
		int length = rules.introFilm() ? Math.min(INTRO_FILM_TICKS, DuelConfig.get().introFilmMaxTicks) : INTRO_SHORT_TICKS;
		if (pair == DuelIds.PAIR_UJI) {
			length = INTRO_SHORT_TICKS;
		}
		enter(Phase.INTRO, length, now);
		for (LivingEntity side : new LivingEntity[] {a, b}) {
			if (!(side instanceof ServerPlayer)) {
				DuelVoices.npcLine(side, openLine(side));
			}
		}
	}

	/** The opening line of a non-player duellist (the Dunia voices when they exist, the disciple lines otherwise). */
	private String openLine(LivingEntity side) {
		if (side instanceof DiscipleEntity) {
			return level.getRandom().nextBoolean() ? DuelIds.LINE_OPEN : DuelIds.LINE_OPEN_ALT;
		}
		DuelistProfile profile = DuelistRegistry.profile(side);
		if (profile != null && profile.offersChallenges()) {
			String key = profile.style() == dev.kultivator.duel.ai.DuelStyle.SANTO_PEDANG ? "kota.wanderer_dao" : "kota.rival_claim";
			if (dev.kultivator.voice.VoiceEvents.exists(key)) {
				return key;
			}
		}
		return DuelIds.LINE_OPEN;
	}

	private void startFight(long now) {
		if (!DuelRules.begin(a, b, 0, this::onRulesEnd)) {
			cancel(Component.translatable("kultivator.duel.reason.busy"));
			return;
		}
		fightStart = now;
		startShareA = share(a);
		startShareB = share(b);
		minShareA = startShareA;
		minShareB = startShareB;
		lastPosA = a.position();
		lastPosB = b.position();
		CompanionSword.ground(a, true);
		CompanionSword.ground(b, true);
		enter(Phase.FIGHT, rules.timeLimitTicks(), now);
	}

	private void tickFight(long now, long age) {
		if (!DuelRules.dueling(a, b)) {
			// DuelRules ended on its own path without telling us (should not happen): make sure nothing stays.
			if (!decided) {
				onRulesEnd(null, null, DuelRules.Reason.CANCELLED);
			}
			return;
		}
		minShareA = Math.min(minShareA, share(a));
		minShareB = Math.min(minShareB, share(b));
		Fighter fa = Fighters.peek(a);
		Fighter fb = Fighters.peek(b);
		if (fa != null && fa.staggered(now)) {
			brokeA = true;
		}
		if (fb != null && fb.staggered(now)) {
			brokeB = true;
		}
		// Ring out (35.2.3): 60 ticks in a row beyond the line.
		boolean wasOutA = outA > 0;
		boolean wasOutB = outB > 0;
		outA = ring.outside(a) ? outA + 1 : 0;
		outB = ring.outside(b) ? outB + 1 : 0;
		if (wasOutA != outA > 0 || wasOutB != outB > 0) {
			lastSync = Long.MIN_VALUE / 4;
		}
		int limit = DuelConfig.get().ringOutTicks;
		if (outA >= limit) {
			DuelRules.end(a, null, DuelRules.Reason.LEFT_RING);
			return;
		}
		if (outB >= limit) {
			DuelRules.end(b, null, DuelRules.Reason.LEFT_RING);
			return;
		}
		// No flying during the duel: the sword comes down again.
		if (now % 5 == 0) {
			CompanionSword.ground(a, false);
			CompanionSword.ground(b, false);
		}
		// Bowing out: crouching still for 2 s.
		bowA = bowing(a, lastPosA) ? bowA + 1 : 0;
		bowB = bowing(b, lastPosB) ? bowB + 1 : 0;
		lastPosA = a.position();
		lastPosB = b.position();
		if (bowA >= BOW_TICKS) {
			surrender(a);
			return;
		}
		if (bowB >= BOW_TICKS) {
			surrender(b);
			return;
		}
		// The AI yields by its style.
		if (now % 10 == 0) {
			for (LivingEntity side : new LivingEntity[] {a, b}) {
				Fighter fighter = Fighters.peek(side);
				if (fighter != null && fighter.brain() != null && fighter.brain().wantsSurrender(now, rules)) {
					surrender(side);
					return;
				}
			}
		}
		// Time limit: the higher health share wins; a draw within 2%.
		if (age >= rules.timeLimitTicks()) {
			float sa = share(a);
			float sb = share(b);
			if (Math.abs(sa - sb) < DRAW_MARGIN) {
				pendingReason = DuelIds.RES_SERI;
				DuelRules.cancel(a);
			} else {
				DuelRules.end(sa > sb ? b : a, sa > sb ? a : b, DuelRules.Reason.TIMEOUT);
			}
		}
	}

	private static boolean bowing(LivingEntity entity, Vec3 last) {
		return entity instanceof ServerPlayer player && player.isShiftKeyDown() && player.onGround()
				&& player.position().distanceToSqr(last) < 1.0E-4D && Fighters.peek(player) != null
				&& Fighters.peek(player).swing() == null;
	}

	private static float share(LivingEntity entity) {
		return entity.getHealth() / Math.max(1.0F, entity.getMaxHealth());
	}

	// ================================================================ results

	/** The DuelRules listener: the fight is decided (or cancelled). */
	void onRulesEnd(@Nullable LivingEntity win, @Nullable LivingEntity lose, DuelRules.Reason reason) {
		if (decided || ended) {
			return;
		}
		decided = true;
		endTick = ClashClock.now();
		if (reason == DuelRules.Reason.CANCELLED) {
			resultReason = pendingReason == DuelIds.RES_SERI ? DuelIds.RES_SERI : DuelIds.RES_BATAL;
			winner = null;
			loser = null;
			enter(Phase.ENDED, 1, endTick);
			return;
		}
		winner = win;
		loser = lose;
		resultReason = switch (reason) {
			case TIMEOUT -> DuelIds.RES_WAKTU;
			case LEFT_RING -> DuelIds.RES_KELUAR;
			default -> pendingReason == DuelIds.RES_PENENTU || pendingReason == DuelIds.RES_MENYERAH ? pendingReason : DuelIds.RES_SEPULUH_PERSEN;
		};
		surrender = resultReason == DuelIds.RES_MENYERAH;
		decisive = resultReason == DuelIds.RES_PENENTU || resultReason == DuelIds.RES_SEPULUH_PERSEN;
		if (loser != null) {
			Fighters.endStagger(loser);
		}
		// A Better Combat hit or a technique that crossed the 10% line has no DECISIVE result of its own: the FINISHER tick
		// sends one unless the exchange that decided it already did (Exchange marks it right after this listener).
		enter(Phase.FINISHER, FINISHER_TICKS, endTick);
	}

	/** Exchange marks its HIT as the decisive one. */
	public void markDecisiveSent() {
		decisiveSent = true;
	}

	private void sendDecisive() {
		decisiveSent = true;
		LivingEntity victim = loser != null ? loser : b;
		LivingEntity by = winner != null ? winner : a;
		Vec3 at = victim.position().add(0.0D, victim.getBbHeight() * 0.6D, 0.0D);
		int outcome = resultReason == DuelIds.RES_PENENTU ? DuelIds.OUT_DEATHBLOW : DuelIds.OUT_HIT;
		Duel.sendResultClash(by, victim, outcome, at, DuelIds.CF_DECISIVE);
	}

	/**
	 * A Deathblow of {@code attacker} on {@code victim} (35.3.9). Under the Deathblow rule in FIGHT it wins at once (the
	 * victim's health goes to max(10%, health − 30%)); true when this session decided it, false when it is a heavy blow.
	 */
	public boolean deathblow(LivingEntity attacker, LivingEntity victim) {
		if (phase != Phase.FIGHT || decided || rules.win() != DuelRuleSet.WinRule.TEBASAN_PENENTU || partnerOf(attacker) != victim) {
			return false;
		}
		float floor = victim.getMaxHealth() * DuelRules.END_FRACTION;
		float after = Math.max(floor, victim.getHealth() - victim.getMaxHealth() * 0.30F);
		if (victim.getHealth() > after) {
			victim.setHealth(after);
		}
		pendingReason = DuelIds.RES_PENENTU;
		decisiveSent = true;
		DuelRules.end(victim, attacker, DuelRules.Reason.DEFEAT);
		return true;
	}

	/** {@code entity} yields (command, HUD, bow, AI). Before the fight it simply withdraws (cancel, stakes back). */
	public void surrender(LivingEntity entity) {
		if (!involves(entity) || decided) {
			return;
		}
		if (phase != Phase.FIGHT) {
			cancel(Component.translatable("kultivator.duel.reason.withdrawn", entity.getDisplayName()));
			return;
		}
		pendingReason = DuelIds.RES_MENYERAH;
		LivingEntity other = partnerOf(entity);
		if (other != null && entity instanceof Mob) {
			dev.kultivator.duel.voice.DuelVoices.kiai(entity, DuelIds.VOICE_HURT);
		}
		DuelRules.end(entity, other, DuelRules.Reason.DEFEAT);
	}

	/** Both players asked to postpone after a wild monster hurt one of them (35.2.5): cancelled. */
	public void tunda(ServerPlayer player) {
		if (!involves(player) || decided) {
			return;
		}
		if (player == a) {
			tundaA = true;
		} else {
			tundaB = true;
		}
		boolean aAgrees = tundaA || !(a instanceof ServerPlayer);
		boolean bAgrees = tundaB || !(b instanceof ServerPlayer);
		if (aAgrees && bAgrees) {
			pendingReason = DuelIds.RES_SERI;
			cancel(Component.translatable("kultivator.duel.reason.postponed"));
		} else {
			LivingEntity other = partnerOf(player);
			if (other instanceof ServerPlayer otherPlayer) {
				Msg.chat(otherPlayer, Component.translatable("kultivator.duel.msg.tunda_asked", player.getDisplayName()).withColor(0xE8D9A8));
			}
		}
	}

	/** A wild monster hurt a duellist: the HUD may propose to postpone (shown to both players). */
	public void wildHurt(long now) {
		if (now - wildHurtAt < 200) {
			return;
		}
		wildHurtAt = now;
		for (LivingEntity side : new LivingEntity[] {a, b}) {
			if (side instanceof ServerPlayer player) {
				Msg.chat(player, Component.translatable("kultivator.duel.msg.wild_hurt").withColor(0xE0A060));
			}
		}
	}

	/** Cancels the duel (stakes return, no Honour). */
	public void cancel(Component reason) {
		if (ended) {
			return;
		}
		// A duel already decided (FINISHER) keeps its result: it only ends sooner.
		if (!decided) {
			for (LivingEntity side : new LivingEntity[] {a, b}) {
				if (side instanceof ServerPlayer player && !player.hasDisconnected()) {
					Msg.chat(player, Component.translatable("kultivator.duel.msg.cancelled", reason).withColor(0xE0A060));
				}
			}
			if (challenger != null && !involves(challenger) && !challenger.hasDisconnected()) {
				Msg.chat(challenger, Component.translatable("kultivator.duel.msg.cancelled", reason).withColor(0xE0A060));
			}
		}
		if (phase == Phase.FIGHT && DuelRules.dueling(a, b)) {
			if (pendingReason != DuelIds.RES_SERI) {
				pendingReason = DuelIds.RES_BATAL;
			}
			DuelRules.cancel(a);
		}
		if (!decided) {
			decided = true;
			resultReason = pendingReason == DuelIds.RES_SERI ? DuelIds.RES_SERI : DuelIds.RES_BATAL;
			winner = null;
			loser = null;
			endTick = ClashClock.now();
		}
		enter(Phase.ENDED, 1, ClashClock.now());
		finish(ClashClock.now());
	}

	/** Exchange results the session cares about (cheers, real-fight tracking). */
	public void onOutcome(int outcome, @Nullable LivingEntity attacker, @Nullable LivingEntity defender) {
		if (outcome == DuelIds.OUT_PERFECT && attacker != null) {
			Fighter fighter = Fighters.peek(attacker);
			if (fighter != null && fighter.lastResolved() != null && fighter.lastResolved().heavy()) {
				DuelVoices.cheer(this, DuelIds.CHEER_PERFECT);
			}
		}
		if (outcome == DuelIds.OUT_POSTURE_BREAK || outcome == DuelIds.OUT_GUARD_BREAK) {
			if (defender == a) {
				brokeA = true;
			} else if (defender == b) {
				brokeB = true;
			}
		}
	}

	// ================================================================ phases and payloads

	private void enter(Phase next, int length, long now) {
		phase = next;
		phaseStart = now;
		phaseLength = length;
		boolean hold = next.holds();
		if (next != Phase.ENDED) {
			Fighters.setHeld(a, hold);
			Fighters.setHeld(b, hold);
		}
		if (next == Phase.COUNTDOWN || next == Phase.INTRO || next == Phase.FINISHER) {
			face(a, b);
			face(b, a);
		}
		lastSync = Long.MIN_VALUE / 4;
		if (next != Phase.ENDED) {
			sync(now);
		}
	}

	/** Turns a held non-player toward the other (players turn themselves). */
	private static void face(LivingEntity self, LivingEntity other) {
		if (self instanceof Mob mob) {
			mob.getLookControl().setLookAt(other, 30.0F, 30.0F);
		}
	}

	private void syncViewers(long now) {
		if (now - lastSync >= SYNC_EVERY) {
			sync(now);
			return;
		}
		if ((now + id) % 10 == 0) {
			// A player who came into range gets the session at once (35.8.2: re-sync when tracking starts).
			for (ServerPlayer player : PlayerLookup.around(level, ring.center(), VIEW)) {
				if (!viewers.contains(player.getUUID())) {
					sync(now);
					return;
				}
			}
		}
	}

	/** Sends the session to the duellists, the watcher and every player within 64 blocks. */
	void sync(long now) {
		lastSync = now;
		DuelPayloads.DuelSessionSync payload = syncPayload(now);
		Set<UUID> seen = new HashSet<>();
		for (ServerPlayer player : recipients()) {
			if (seen.add(player.getUUID())) {
				DuelNet.send(player, payload);
			}
		}
		viewers.clear();
		viewers.addAll(seen);
		// The bars of both fighters follow every session sync (a new viewer needs them too).
		Fighter fa = Fighters.peek(a);
		Fighter fb = Fighters.peek(b);
		if (fa != null) {
			Fighters.syncFighter(fa, now, true);
		}
		if (fb != null) {
			Fighters.syncFighter(fb, now, true);
		}
	}

	private List<ServerPlayer> recipients() {
		List<ServerPlayer> list = new ArrayList<>();
		if (a instanceof ServerPlayer pa && !pa.hasDisconnected()) {
			list.add(pa);
		}
		if (b instanceof ServerPlayer pb && !pb.hasDisconnected()) {
			list.add(pb);
		}
		if (challenger != null && !challenger.hasDisconnected() && challenger.level() == level) {
			list.add(challenger);
		}
		if (ring != null) {
			list.addAll(PlayerLookup.around(level, ring.center(), VIEW));
		}
		return list;
	}

	DuelPayloads.DuelSessionSync syncPayload(long now) {
		int phaseLeft = (int) Math.max(0L, phaseLength - (now - phaseStart));
		int fightLeft = phase == Phase.FIGHT ? phaseLeft : phase.ordinal() < Phase.FIGHT.ordinal() ? rules.timeLimitTicks() : 0;
		int flags = (rules.introFilm() ? DuelIds.SF_INTRO_FILM : 0) | (rules.crowd() || sectArena ? DuelIds.SF_CROWD : 0)
				| (outA > 0 ? DuelIds.SF_OUT_A : 0) | (outB > 0 ? DuelIds.SF_OUT_B : 0) | (sectArena ? DuelIds.SF_SECT_ARENA : 0)
				| (decisive ? DuelIds.SF_DECISIVE : 0) | (surrender ? DuelIds.SF_SURRENDER : 0);
		Vec3 center = ring.center();
		return new DuelPayloads.DuelSessionSync(id, phase.ordinal(), pair, a.getId(), b.getId(), (float) center.x, (float) center.y,
				(float) center.z, Math.round(ring.radius()), (float) markA.x, (float) markA.z, (float) markB.x, (float) markB.z, rules.bytes(),
				phaseLeft, fightLeft, (int) now, a.getDisplayName(), b.getDisplayName(), rankLine(a), rankLine(b), QiWave.qiColor(a),
				QiWave.qiColor(b), flags);
	}

	/** "realm · title" of a duellist (35.2.6), used on the intro card and the duel screen. */
	public static Component rankLine(LivingEntity entity) {
		Rank rank = Posture.rank(entity);
		MutableComponent line = rank != null ? rank.displayName().copy() : entity.getType().getDescription().copy();
		int honour = honourOf(entity);
		return line.append(Component.literal(" · ")).append(DuelRecords.title(honour));
	}

	/** Honour of a player, a disciple (by soul) or an NPC (its profile). */
	public static int honourOf(LivingEntity entity) {
		if (entity.level() instanceof ServerLevel serverLevel) {
			if (entity instanceof ServerPlayer player) {
				return DuelRecords.get(serverLevel.getServer()).honour(player.getUUID());
			}
			if (entity instanceof DiscipleEntity disciple && disciple.soulId() != null) {
				return DuelRecords.get(serverLevel.getServer()).honour(disciple.soulId());
			}
		}
		DuelistProfile profile = DuelistRegistry.profile(entity);
		return profile != null ? profile.honour() : DuelRecords.DEFAULT_HONOUR;
	}

	// ================================================================ the end

	/** ENDED: stakes, records, rewards, the result card; every modifier, goal and seat is undone. */
	private void finish(long now) {
		if (ended) {
			return;
		}
		ended = true;
		phase = Phase.ENDED;
		try {
			if (DuelRules.dueling(a, b)) {
				DuelRules.cancel(a);
			}
			undo();
			DuelResults.settle(this, now);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Duel: finishing session {} failed", id, exception);
			try {
				DuelEscrow.get(level.getServer()).returnAll(level.getServer(), key);
			} catch (RuntimeException ignored) {
				// Nothing more can be done; the escrow returns it on the next start.
			}
		} finally {
			DuelSessions.remove(this);
		}
	}

	/** Undoes everything the session changed on entities. */
	private void undo() {
		Fighters.setHeld(a, false);
		Fighters.setHeld(b, false);
		Fighters.leaveSession(a);
		Fighters.leaveSession(b);
		for (Map.Entry<Mob, DuelistGoal> entry : goals.entrySet()) {
			DuelistGoal.uninstall(entry.getKey(), entry.getValue());
		}
		goals.clear();
		for (Seat seat : seats) {
			if (seat.disciple().isAlive() && seat.disciple().activity() == DiscipleEntity.ACTIVITY_SPECTATOR) {
				seat.disciple().setActivity(seat.activity(), seat.spot());
			}
		}
		seats.clear();
		for (Seat seat : duellistActivity.values()) {
			if (seat.disciple().isAlive() && seat.disciple().activity() == DiscipleEntity.ACTIVITY_DUELLIST) {
				seat.disciple().setActivity(seat.activity(), seat.spot());
			}
		}
		duellistActivity.clear();
	}

	/** The ENDED sync (once): clients forget the session. */
	void sendEnded(long now) {
		DuelPayloads.DuelSessionSync payload = syncPayload(now);
		for (ServerPlayer player : recipients()) {
			DuelNet.send(player, payload);
		}
	}

	/** The players who receive the result card. */
	List<ServerPlayer> resultRecipients() {
		List<ServerPlayer> list = new ArrayList<>();
		Set<UUID> seen = new HashSet<>();
		for (ServerPlayer player : recipients()) {
			if (seen.add(player.getUUID())) {
				list.add(player);
			}
		}
		return list;
	}

	/** The fighter of {@code entity} (stats of this duel), or null when it was forgotten. */
	@Nullable Fighter fighter(LivingEntity entity) {
		return Fighters.peek(entity);
	}

	/** Facing helper for commands and AI. */
	public boolean faces(LivingEntity self) {
		LivingEntity other = partnerOf(self);
		return other != null && HitTest.facing(self, other, 90.0D);
	}
}
