package dev.kultivator.sect.event;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kompetisi Daftar Kuat and Turnamen Elit (DESIGN 20.3.12): every {@code tournamentIntervalDays} at noon (or from the
 * Arena tablet, every 3 days at most) up to 8 of the master's disciples fight a single-elimination bracket in the ring at
 * the stele, one bout at a time ({@link DuelArena}); empty slots are north-bank rivals. Every third competition is a
 * Turnamen Elit with three visiting champions, 2.5x renown and the Primordial Ark afterwards. Spectators sit (activity
 * 8). Rewards are PK, bar shares, comprehension, bond and the titles "Nomor Satu Daftar Kuat" (7 days, +10% technique
 * damage, gold name) and "Juara Peringkat"; the all-time top ten are kept on bamboo slips.
 * <p>
 * Sumber: A Will Eternal (the Outer Sect Chosen battles, the Legacy Echelon); Battle Through the Heavens ch. 566-573 (the
 * Strong Ranking competition down to a top ten); I Shall Seal the Heavens ch. 27-31 (the final bout announced by the
 * Grand Elder); Against the Gods ch. 434-436 and 1359; Soul Land (the Shrek Seven Monsters win the Continental Elite
 * competition); Return of the Mount Hua Sect (victories over Zhongnan spread the name).
 */
public final class Tournament {

	public static final String TITLE_FIRST = "nomor_satu";
	public static final String TITLE_ELITE = "juara_peringkat";
	public static final String TITLE_SACRED_BORDER = "batas_suci";
	/** Permanent: finished a Kompetisi or Turnamen Elit in the top three. */
	public static final String TITLE_TOP3 = "top3";

	public static final int MAX_DISCIPLES = 8;
	public static final int MIN_IN_TERRITORY = 3;
	public static final double MASTER_RANGE = 32.0D;
	public static final int MANUAL_COOLDOWN_DAYS = 3;
	public static final int ELITE_EVERY = 3;
	public static final int CHAMPIONS = 3;
	public static final int BOUT_TICKS = 1200;
	public static final int PAUSE_TICKS = 60;
	public static final double FIGHTER_OFFSET = 4.0D;
	public static final double SEAT_RADIUS = 13.0D;
	public static final int[] PLACE_PK = {300, 150, 75};
	public static final double[] PLACE_SHARE = {0.40D, 0.25D, 0.15D};
	public static final int[] PLACE_RENOWN = {200, 100, 50};
	public static final double ELITE_RENOWN = 2.5D;
	public static final int ENTRANT_PK = 20;
	public static final double ENTRANT_SHARE = 0.05D;
	public static final double ENTRANT_COMPREHENSION = 2.0D;
	public static final int BOND_PER_WIN = 3;
	public static final long TITLE_TICKS = 7L * 24000L;
	public static final double FIRST_TECHNIQUE_BONUS = 1.10D;
	public static final double SACRED_BORDER_XP = 1.10D;
	/** Points on the all-time list: 1st, 2nd, 3rd, any entrant, per won bout. */
	public static final int[] RANK_POINTS = {10, 6, 4};
	public static final int RANK_ENTRANT = 1;

	private static final class Run {
		final UUID owner;
		final ServerLevel level;
		final Vec3 center;
		final BlockPos ring;
		final boolean elite;
		final Bracket bracket;
		final List<Bracket.Entrant> entrants;
		final Map<UUID, BlockPos> seats = new HashMap<>();
		@Nullable Bracket.Match current;
		@Nullable DiscipleEntity bodyA;
		@Nullable DiscipleEntity bodyB;
		int pause = PAUSE_TICKS;
		boolean fighting;

		Run(UUID owner, ServerLevel level, Vec3 center, boolean elite, Bracket bracket, List<Bracket.Entrant> entrants) {
			this.owner = owner;
			this.level = level;
			this.center = center;
			this.ring = BlockPos.containing(center);
			this.elite = elite;
			this.bracket = bracket;
			this.entrants = entrants;
		}
	}

	private static final Map<UUID, Run> RUNS = new HashMap<>();

	/** Called once from {@link SectEvents#init()}. */
	static void init() {
		DiscipleHooks.addDamageFactor((entity, target, technique) -> {
			double factor = DuelArena.underdogFactor(entity, target);
			if (technique && entity.role() == DiscipleEntity.Role.DISCIPLE && hasTitle(entity.data(), TITLE_FIRST)) {
				factor *= FIRST_TECHNIQUE_BONUS;
			}
			return factor;
		});
		DiscipleHooks.addXpFactor((entity, data, source) -> hasTitle(data, TITLE_SACRED_BORDER) ? SACRED_BORDER_XP : 1.0D);
		DiscipleHooks.addTick20(Tournament::titleName);
	}

	// ================================================================== API (DESIGN 20.4.5)

	/** True when the disciple holds the title {@code id} ("nomor_satu", "juara_peringkat", "batas_suci", "top3") right now. */
	public static boolean hasTitle(DiscipleData data, String id) {
		long until = data.extra().getCompoundOrEmpty(Duties.EXTRA).getCompoundOrEmpty("titles").getLongOr(id, 0L);
		return until > EventUtil.gameTime();
	}

	public static boolean running(UUID owner) {
		return RUNS.containsKey(owner);
	}

	/** The gold name of Nomor Satu Daftar Kuat, removed again when the title ends. */
	private static void titleName(DiscipleEntity entity) {
		Component name = entity.getCustomName();
		if (name == null || entity.role() != DiscipleEntity.Role.DISCIPLE) {
			return;
		}
		boolean gold = hasTitle(entity.data(), TITLE_FIRST);
		CompoundTag extra = entity.data().extra().getCompoundOrEmpty(Duties.EXTRA);
		boolean marked = extra.getBooleanOr("gold_name", false);
		TextColor color = name.getStyle().getColor();
		TextColor goldColor = TextColor.fromLegacyFormat(ChatFormatting.GOLD);
		if (gold && (color == null || !color.equals(goldColor))) {
			entity.setCustomName(name.copy().withStyle(ChatFormatting.GOLD));
			EventUtil.extra(entity.data()).putBoolean("gold_name", true);
		} else if (!gold && marked) {
			// Only the gold this title gave is taken away again.
			if (color != null && color.equals(goldColor)) {
				entity.setCustomName(name.copy().withStyle(style -> style.withColor((TextColor) null)));
			}
			EventUtil.extra(entity.data()).remove("gold_name");
		}
	}

	// ================================================================== schedule

	static CompoundTag events(UUID owner) {
		return EventUtil.sub(EventUtil.section(owner), "events");
	}

	/** The day index of the next scheduled competition (set when the hall is first seen). */
	static long nextDay(UUID owner) {
		CompoundTag events = events(owner);
		if (!events.contains("next_tournament")) {
			events.putLong("next_tournament", EventUtil.day() + KultivatorConfig.get().tournamentIntervalDays);
			EventUtil.dirty();
		}
		return events.getLongOr("next_tournament", 0L);
	}

	/** True when the next competition is a Turnamen Elit. */
	static boolean nextIsElite(UUID owner) {
		return (events(owner).getIntOr("count", 0) + 1) % ELITE_EVERY == 0;
	}

	/** Noon: a due competition starts if it can (else it waits for the next noon). */
	static void onNoon(UUID owner, Halls.HallView hall) {
		if (!KultivatorConfig.get().sectEvents || EventUtil.day() < nextDay(owner) || RUNS.containsKey(owner)) {
			return;
		}
		ServerPlayer player = EventUtil.player(owner);
		String why = blocker(owner, hall, player, false);
		if (why != null) {
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.tourney.waits", Component.translatable(why)).withStyle(ChatFormatting.GRAY));
			return;
		}
		start(owner, hall, player, false);
	}

	/** "Lewati minggu ini": the due competition is skipped until the next interval. */
	static void skip(ServerPlayer player) {
		events(player.getUUID()).putLong("next_tournament", EventUtil.day() + KultivatorConfig.get().tournamentIntervalDays);
		EventUtil.dirty();
		EventUtil.chat(player, Component.translatable("kultivator.sectevent.tourney.skipped").withStyle(ChatFormatting.GRAY));
	}

	/** From the Arena tablet (every 3 days at most). */
	static boolean manual(ServerPlayer player) {
		UUID owner = player.getUUID();
		Halls.HallView hall = Halls.hallOf(owner);
		CompoundTag events = events(owner);
		long ready = events.contains("manual_day") ? events.getLongOr("manual_day", 0L) + MANUAL_COOLDOWN_DAYS : Long.MIN_VALUE;
		if (EventUtil.day() < ready) {
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.tourney.manual_wait", ready).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		String why = hall == null ? "kultivator.sectevent.no_hall" : blocker(owner, hall, player, false);
		if (why != null) {
			EventUtil.chat(player, Component.translatable(why).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (!start(owner, hall, player, false)) {
			return false;
		}
		events.putLong("manual_day", EventUtil.day());
		EventUtil.dirty();
		return true;
	}

	/** Why a competition cannot start now (a key), or null. {@code test} skips the presence rules. */
	static @Nullable String blocker(UUID owner, Halls.HallView hall, @Nullable ServerPlayer player, boolean test) {
		if (!EventUtil.hallLoaded(hall)) {
			return "kultivator.sectevent.tourney.need_hall";
		}
		if (RUNS.containsKey(owner) || DuelArena.busy(owner) || VoidRealm.climbing(owner) || HundredDynasties.running(owner)
				|| PromotionTrial.running(owner)) {
			return "kultivator.sectevent.arena.busy";
		}
		if (player == null || player.level() != EventUtil.hallLevel(hall)) {
			return "kultivator.sectevent.tourney.need_master";
		}
		if (test) {
			return candidates(owner, hall).isEmpty() ? "kultivator.sectevent.tourney.need_entrants" : null;
		}
		if (player.distanceToSqr(EventUtil.center(hall)) > MASTER_RANGE * MASTER_RANGE) {
			return "kultivator.sectevent.tourney.need_master";
		}
		if (candidates(owner, hall).size() < MIN_IN_TERRITORY) {
			return "kultivator.sectevent.tourney.need_disciples";
		}
		return null;
	}

	/** Outer+ disciples in the territory, free, strongest first. */
	private static List<DiscipleEntity> candidates(UUID owner, Halls.HallView hall) {
		List<DiscipleEntity> list = new ArrayList<>();
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			if (EventUtil.free(disciple) && SectRanks.rank(disciple.data()) >= 1 && Halls.inTerritory(owner, disciple.level(), disciple.position())
					&& !disciple.departed()) {
				list.add(disciple);
			}
		}
		list.sort(Comparator.comparingInt((DiscipleEntity d) -> DisciplePaths.power(d.data())).reversed());
		return list;
	}

	static boolean excluded(UUID owner, UUID soul) {
		String id = soul.toString();
		for (Tag tag : EventUtil.section(owner).getListOrEmpty("excluded")) {
			if (id.equals(tag.asString().orElse(""))) {
				return true;
			}
		}
		return false;
	}

	/** Ticks or unticks a disciple for the next bracket. */
	static void toggleExcluded(UUID owner, UUID soul) {
		ListTag list = EventUtil.list(EventUtil.section(owner), "excluded");
		String id = soul.toString();
		for (int i = 0; i < list.size(); i++) {
			if (id.equals(list.get(i).asString().orElse(""))) {
				list.remove(i);
				EventUtil.dirty();
				return;
			}
		}
		list.add(StringTag.valueOf(id));
		EventUtil.dirty();
	}

	// ================================================================== running

	/** Builds the bracket and starts; {@code test} skips the presence rules. */
	static boolean start(UUID owner, Halls.HallView hall, @Nullable ServerPlayer player, boolean test) {
		ServerLevel level = EventUtil.hallLevel(hall);
		if (level == null) {
			return false;
		}
		List<Bracket.Entrant> disciples = new ArrayList<>();
		for (DiscipleEntity disciple : candidates(owner, hall)) {
			if (disciples.size() >= MAX_DISCIPLES) {
				break;
			}
			if (!excluded(owner, disciple.soulId())) {
				disciples.add(new Bracket.Entrant(Bracket.DISCIPLE, disciple.soulId(), disciple.getName(), disciple.data(), disciple.skinIndex(),
						disciple.slimSkin(), DisciplePaths.power(disciple.data())));
			}
		}
		if (disciples.isEmpty()) {
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.tourney.need_entrants").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		CompoundTag events = events(owner);
		boolean elite = nextIsElite(owner);
		RandomSource random = level.getRandom();
		List<Bracket.Entrant> seeds = new ArrayList<>(disciples);
		int best = disciples.get(0).data.rank().step();
		if (elite) {
			for (int i = 0; i < CHAMPIONS; i++) {
				seeds.add(Rivals.champion(random, best - 1 + i, random.nextInt(Rivals.SECT_COUNT)));
			}
		}
		int median = disciples.get(disciples.size() / 2).data.rank().step();
		int slots = Bracket.slotsFor(Math.max(4, seeds.size()));
		while (seeds.size() < slots) {
			seeds.add(Rivals.rival(random, median - 1 + random.nextInt(3)));
		}
		seeds.sort(Comparator.comparingInt((Bracket.Entrant e) -> e.power).reversed());
		Run run = new Run(owner, level, EventUtil.center(hall), elite, new Bracket(seeds, true), seeds);
		RUNS.put(owner, run);
		events.putInt("count", events.getIntOr("count", 0) + 1);
		events.putLong("next_tournament", EventUtil.day() + KultivatorConfig.get().tournamentIntervalDays);
		EventUtil.dirty();
		seat(run);
		Component title = Component.translatable(elite ? "kultivator.sectevent.tourney.elite" : "kultivator.sectevent.tourney.name");
		if (player != null) {
			Msg.title(player, title.copy().withStyle(ChatFormatting.GOLD), Component.translatable("kultivator.sectevent.tourney.subtitle", seeds.size())
					.withStyle(ChatFormatting.YELLOW), 10, 50, 15);
		}
		announce(run, Component.translatable("kultivator.sectevent.tourney.begins", title, seeds.size()).withStyle(ChatFormatting.GOLD));
		level.playSound(null, run.center.x, run.center.y, run.center.z, SoundEvents.RAID_HORN, SoundSource.NEUTRAL, 0.6F, 1.4F);
		Halls.log(owner, Component.translatable("kultivator.sectevent.log.tourney_start", title));
		return true;
	}

	/** Spectator seats for the master's entrants on a circle around the ring (activity 8). */
	private static void seat(Run run) {
		List<Bracket.Entrant> own = new ArrayList<>();
		for (Bracket.Entrant entrant : run.entrants) {
			if (entrant.disciple()) {
				own.add(entrant);
			}
		}
		for (int i = 0; i < own.size(); i++) {
			Bracket.Entrant entrant = own.get(i);
			DiscipleEntity disciple = entrant.soul == null ? null : EventUtil.bySoul(run.owner, entrant.soul);
			if (disciple == null) {
				continue;
			}
			double angle = Math.PI * 2.0D * i / Math.max(1, own.size());
			Vec3 near = run.center.add(Math.cos(angle) * SEAT_RADIUS, 0.0D, Math.sin(angle) * SEAT_RADIUS);
			Vec3 safe = run.level.isLoaded(BlockPos.containing(near)) ? Targeting.safeSpot(run.level, disciple, near) : null;
			BlockPos seat = safe != null ? BlockPos.containing(safe) : disciple.blockPosition();
			run.seats.put(entrant.soul, seat);
			Duties.release(run.owner, entrant.soul, null);
			BlazingTower.stop(run.owner, entrant.soul, false);
			VoidRealm.dequeue(run.owner, entrant.soul);
			disciple.setTarget(null);
			disciple.setActivity(DiscipleEntity.ACTIVITY_SPECTATOR, seat);
		}
	}

	static void tick(MinecraftServer server) {
		if (RUNS.isEmpty()) {
			return;
		}
		for (Run run : new ArrayList<>(RUNS.values())) {
			ServerPlayer player = EventUtil.player(run.owner);
			if (player == null) {
				cancel(run, "kultivator.sectevent.tourney.cancelled");
				continue;
			}
			if (run.fighting) {
				continue;
			}
			if (--run.pause > 0) {
				continue;
			}
			Bracket.Match match = run.bracket.next();
			if (match == null) {
				finish(run);
				continue;
			}
			startBout(run, match);
		}
	}

	private static void startBout(Run run, Bracket.Match match) {
		run.current = match;
		run.pause = PAUSE_TICKS;
		DiscipleEntity a = body(run, match.a, -1);
		DiscipleEntity b = body(run, match.b, 1);
		if (a == null || b == null) {
			// A fighter cannot appear (a disciple left, no room): the other wins, or the stronger visitor.
			Bracket.Entrant winner = a != null ? match.a : b != null ? match.b : Bracket.simulate(match, run.level.getRandom());
			removeVisitor(run, a, match.a);
			removeVisitor(run, b, match.b);
			report(run, match, winner, true);
			return;
		}
		run.bodyA = a;
		run.bodyB = b;
		DuelArena.Bout bout = DuelArena.start(run.owner, run.level, run.center, a, b, BOUT_TICKS, DuelArena.OUT_RADIUS, true,
				(winner, loser, reason) -> boutEnded(run, match, winner, reason));
		if (bout == null) {
			Bracket.Entrant winner = Bracket.simulate(match, run.level.getRandom());
			cleanupBodies(run);
			report(run, match, winner, true);
			return;
		}
		run.fighting = true;
		if (!match.bronze && run.bracket.rounds.get(run.bracket.rounds.size() - 1).size() == 1) {
			dev.kultivator.epic.EpicHooks.tournament(run.level, dev.kultivator.epic.EpicIds.TOUR_INTRO, run.elite ? dev.kultivator.epic.EpicIds.TOUR_ELITE : dev.kultivator.epic.EpicIds.TOUR_INTERNAL, a, b, run.center);
		}
		announce(run, Component.translatable(match.bronze ? "kultivator.sectevent.tourney.bronze_bout" : "kultivator.sectevent.tourney.bout",
				match.a.name, match.b.name).withStyle(ChatFormatting.YELLOW));
	}

	/**
	 * The fighter's body at its side of the ring ({@code side} -1 or 1): a disciple steps in (activity 9), a visitor is
	 * spawned. Null when it cannot. Bouts between two visitors are never fought (simulated).
	 */
	private static @Nullable DiscipleEntity body(Run run, Bracket.Entrant entrant, int side) {
		Bracket.Match current = run.current;
		boolean bothVisitors = current != null && !current.a.disciple() && !current.b.disciple();
		if (bothVisitors) {
			return null;
		}
		Vec3 target = run.center.add(side * FIGHTER_OFFSET, 0.0D, 0.0D);
		if (entrant.disciple()) {
			DiscipleEntity disciple = entrant.soul == null ? null : EventUtil.bySoul(run.owner, entrant.soul);
			if (disciple == null || !EventUtil.free(disciple) || disciple.level() != run.level) {
				return null;
			}
			Vec3 spot = Targeting.safeSpot(run.level, disciple, target);
			if (spot == null) {
				return null;
			}
			disciple.setTarget(null);
			disciple.snapTo(spot.x, spot.y, spot.z, side < 0 ? -90.0F : 90.0F, 0.0F);
			disciple.setActivity(DiscipleEntity.ACTIVITY_DUELLIST, null);
			disciple.setHealth(disciple.getMaxHealth());
			return disciple;
		}
		DiscipleEntity probe = Disciples.loaded(run.owner).isEmpty() ? null : Disciples.loaded(run.owner).get(0);
		Vec3 spot = probe == null ? target : Targeting.safeSpot(run.level, probe, target);
		if (spot == null) {
			spot = target;
		}
		DiscipleEntity body = Rivals.spawn(run.level, spot, entrant, run.ring);
		if (body != null) {
			EventUtil.fx(run.level, spot, SectEventFxIds.RETURN, entrant.data.rootColor(), 0.0F);
		}
		return body;
	}

	private static void boutEnded(Run run, Bracket.Match match, @Nullable LivingEntity winner, DuelRules.Reason reason) {
		run.fighting = false;
		run.pause = PAUSE_TICKS;
		Bracket.Entrant result;
		if (winner == run.bodyA) {
			result = match.a;
		} else if (winner == run.bodyB) {
			result = match.b;
		} else {
			// Cancelled (a fighter left the world): whoever is still standing, else the stronger.
			boolean aGone = run.bodyA == null || run.bodyA.isRemoved() || !run.bodyA.isAlive();
			boolean bGone = run.bodyB == null || run.bodyB.isRemoved() || !run.bodyB.isAlive();
			result = aGone && !bGone ? match.b : bGone && !aGone ? match.a : Bracket.simulate(match, run.level.getRandom());
		}
		cleanupBodies(run);
		if (RUNS.get(run.owner) != run) {
			return;
		}
		report(run, match, result, false);
		if (reason == DuelRules.Reason.LEFT_RING) {
			announce(run, Component.translatable("kultivator.sectevent.tourney.left_ring").withStyle(ChatFormatting.GRAY));
		}
	}

	private static void report(Run run, Bracket.Match match, Bracket.Entrant winner, boolean quiet) {
		run.bracket.report(match, winner);
		Bracket.Entrant loser = match.loser();
		if (winner.disciple() && winner.soul != null) {
			DiscipleEntity disciple = EventUtil.bySoul(run.owner, winner.soul);
			if (disciple != null) {
				DisciplePaths.addBond(disciple.data(), BOND_PER_WIN, "kultivator.sectevent.bond.bout");
			}
		}
		announce(run, Component.translatable("kultivator.sectevent.tourney.result", winner.name, loser == null ? Component.empty() : loser.name)
				.withStyle(winner.disciple() ? ChatFormatting.AQUA : ChatFormatting.GRAY));
		run.current = null;
	}

	/** Visitors fade away; the master's fighters get healed and go back to their seats. */
	private static void cleanupBodies(Run run) {
		for (DiscipleEntity body : new DiscipleEntity[]{run.bodyA, run.bodyB}) {
			if (body == null || body.isRemoved()) {
				continue;
			}
			if (body.role() != DiscipleEntity.Role.DISCIPLE) {
				EventUtil.fx(run.level, body.position(), SectEventFxIds.PHANTOM_DISSOLVE, 0, 0.0F);
				RivalSkills.forget(body.getUUID());
				body.discard();
			} else {
				body.setHealth(body.getMaxHealth());
				body.setTarget(null);
				BlockPos seat = run.seats.get(body.soulId());
				body.setActivity(seat != null ? DiscipleEntity.ACTIVITY_SPECTATOR : DiscipleEntity.ACTIVITY_NONE, seat);
			}
		}
		run.bodyA = null;
		run.bodyB = null;
	}

	private static void removeVisitor(Run run, @Nullable DiscipleEntity body, Bracket.Entrant entrant) {
		if (body != null && !entrant.disciple() && !body.isRemoved()) {
			body.discard();
		} else if (body != null && entrant.disciple()) {
			BlockPos seat = run.seats.get(body.soulId());
			body.setActivity(seat != null ? DiscipleEntity.ACTIVITY_SPECTATOR : DiscipleEntity.ACTIVITY_NONE, seat);
		}
	}

	/** Places, rewards, titles, the all-time list, the Ark after an Elite, and everyone back to their orders. */
	private static void finish(Run run) {
		RUNS.remove(run.owner, run);
		Bracket.Entrant[] places = {run.bracket.first(), run.bracket.second(), run.bracket.third()};
		ServerPlayer player = EventUtil.player(run.owner);
		long now = EventUtil.gameTime();
		List<UUID> top = new ArrayList<>();
		for (Bracket.Entrant entrant : run.entrants) {
			if (!entrant.disciple() || entrant.soul == null) {
				continue;
			}
			DiscipleEntity disciple = EventUtil.bySoul(run.owner, entrant.soul);
			int place = -1;
			for (int i = 0; i < places.length; i++) {
				if (places[i] == entrant) {
					place = i;
				}
			}
			if (disciple != null) {
				DiscipleData data = disciple.data();
				SectRanks.addPk(data, ENTRANT_PK + (place >= 0 ? PLACE_PK[place] : 0), "kultivator.sectevent.pk.tourney");
				DisciplePaths.grantBarShare(data, ENTRANT_SHARE + (place >= 0 ? PLACE_SHARE[place] : 0.0D));
				data.addComprehension(ENTRANT_COMPREHENSION);
				if (place >= 0) {
					// A top-3 finish counts for the Murid Inti promotion (module A reads it through hasTitle).
					EventUtil.sub(EventUtil.extra(data), "titles").putLong(TITLE_TOP3, Long.MAX_VALUE);
				}
				if (place == 0) {
					CompoundTag titles = EventUtil.sub(EventUtil.extra(data), "titles");
					titles.putLong(TITLE_FIRST, now + TITLE_TICKS);
					if (run.elite) {
						titles.putLong(TITLE_ELITE, Long.MAX_VALUE);
					}
				}
				disciple.refreshSynced();
				if (disciple.activity() == DiscipleEntity.ACTIVITY_SPECTATOR || disciple.activity() == DiscipleEntity.ACTIVITY_DUELLIST) {
					disciple.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
				}
			}
			if (place >= 0) {
				Halls.addRenown(run.owner, (int) Math.round(PLACE_RENOWN[place] * (run.elite ? ELITE_RENOWN : 1.0D)), "kultivator.sectevent.renown.tourney");
				top.add(entrant.soul);
			}
			rank(run.owner, entrant, place);
		}
		Bracket.Entrant champion = places[0];
		if (champion != null) {
			EventUtil.fx(run.level, run.center, SectEventFxIds.CHAMPION, run.elite ? 1 : 0, 0.0F);
			dev.kultivator.epic.EpicHooks.tournament(run.level, dev.kultivator.epic.EpicIds.TOUR_WINNER, run.elite ? dev.kultivator.epic.EpicIds.TOUR_ELITE : dev.kultivator.epic.EpicIds.TOUR_INTERNAL, champion.disciple() && champion.soul != null ? EventUtil.bySoul(run.owner, champion.soul) : null, null, run.center);
			run.level.playSound(null, run.center.x, run.center.y, run.center.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.NEUTRAL, 1.0F, 1.0F);
			announce(run, Component.translatable("kultivator.sectevent.tourney.champion", champion.name,
					places[1] == null ? Component.literal("-") : places[1].name, places[2] == null ? Component.literal("-") : places[2].name)
					.withStyle(ChatFormatting.GOLD));
			if (champion.disciple() && player != null) {
				Advancements.grant(player, "tournament_champion");
			}
			Halls.log(run.owner, Component.translatable("kultivator.sectevent.log.tourney_end", champion.name));
		}
		if (run.elite && !top.isEmpty()) {
			Halls.HallView hall = Halls.hallOf(run.owner);
			if (hall != null) {
				PrimordialArk.openFor(run.owner, hall, top);
			}
		}
		EventUtil.dirty();
	}

	/** The all-time top ten: points per place, per entry and per won bout. */
	private static void rank(UUID owner, Bracket.Entrant entrant, int place) {
		if (entrant.soul == null) {
			return;
		}
		CompoundTag board = EventUtil.sub(EventUtil.section(owner), "ranking");
		CompoundTag entry = EventUtil.sub(board, entrant.soul.toString());
		entry.store("name", ComponentSerialization.CODEC, entrant.name);
		entry.putInt("points", entry.getIntOr("points", 0) + RANK_ENTRANT + entrant.wins + (place >= 0 ? RANK_POINTS[place] : 0));
		entry.putInt("wins", entry.getIntOr("wins", 0) + entrant.wins);
		if (place == 0) {
			entry.putInt("firsts", entry.getIntOr("firsts", 0) + 1);
		}
		if (board.size() > 30) {
			// Keep the list short: drop the lowest scores.
			String lowest = null;
			int points = Integer.MAX_VALUE;
			for (String key : board.keySet()) {
				int each = board.getCompoundOrEmpty(key).getIntOr("points", 0);
				if (each < points) {
					points = each;
					lowest = key;
				}
			}
			if (lowest != null) {
				board.remove(lowest);
			}
		}
	}

	private static void cancel(Run run, String reasonKey) {
		RUNS.remove(run.owner, run);
		if (run.fighting) {
			DuelArena.cancel(run.owner);
		}
		cleanupBodies(run);
		for (Bracket.Entrant entrant : run.entrants) {
			DiscipleEntity disciple = entrant.soul == null ? null : EventUtil.bySoul(run.owner, entrant.soul);
			if (disciple != null && (disciple.activity() == DiscipleEntity.ACTIVITY_SPECTATOR || disciple.activity() == DiscipleEntity.ACTIVITY_DUELLIST)) {
				disciple.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
			}
		}
		Halls.log(run.owner, Component.translatable(reasonKey));
	}

	private static void announce(Run run, Component line) {
		ServerPlayer player = EventUtil.player(run.owner);
		if (player != null) {
			Msg.chat(player, line);
		}
	}

	// ================================================================== client view

	static CompoundTag forClient(UUID owner) {
		CompoundTag tag = new CompoundTag();
		CompoundTag events = events(owner);
		tag.putLong("next", nextDay(owner));
		tag.putInt("count", events.getIntOr("count", 0));
		tag.putBoolean("elite_next", nextIsElite(owner));
		tag.putLong("manual_ready", events.contains("manual_day") ? events.getLongOr("manual_day", 0L) + MANUAL_COOLDOWN_DAYS : Long.MIN_VALUE);
		tag.put("excluded", EventUtil.section(owner).getListOrEmpty("excluded").copy());
		Run run = RUNS.get(owner);
		if (run != null) {
			tag.putBoolean("running", true);
			tag.putBoolean("elite", run.elite);
			tag.put("bracket", run.bracket.forClient(run.current));
		}
		ListTag ranking = new ListTag();
		CompoundTag board = EventUtil.section(owner).getCompoundOrEmpty("ranking");
		List<CompoundTag> entries = new ArrayList<>();
		for (String key : board.keySet()) {
			entries.add(board.getCompoundOrEmpty(key));
		}
		entries.sort((a, b) -> Integer.compare(b.getIntOr("points", 0), a.getIntOr("points", 0)));
		for (int i = 0; i < Math.min(10, entries.size()); i++) {
			ranking.add(entries.get(i).copy());
		}
		tag.put("ranking", ranking);
		return tag;
	}

	static void clear() {
		RUNS.clear();
	}

	private Tournament() {
	}
}
