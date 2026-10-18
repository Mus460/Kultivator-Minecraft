package dev.kultivator.sect.event;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.sect.hall.Candidates;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.Talents;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Perang Seratus Dinasti, the Hundred Dynasties War (DESIGN 20.3.13). On every 4th recruitment day at hall level 3+ the
 * Sect Hall (module A) receives 8 candidates; the master starts the war ("Mulai Perang" on the Calon or Arena tablet)
 * any time that day. Today's candidates fight a 4 + 2 + 1 single-elimination bracket in the ring at the stele: temporary
 * {@link DuelRules} pairs between CANDIDATE bodies, 30 s bouts, base stats, the 10% health end. The champion is crowned
 * by module A ({@code Candidates.crownChampion}: root +1 grade, a 50% physique roll, Tekad 100, joins free as Murid Luar
 * at Qi Refining Early with bond 20); every candidate stays recruitable until sunrise. Entities only, no block.
 * <p>
 * Sumber: Martial Universe (the Hundred Dynasties War; champion Lin Dong is taken into the Dao Sect); Battle Through the
 * Heavens (Jia Nan Academy accepts only a region's best youths).
 */
final class HundredDynasties {

	public static final int MIN_HALL_LEVEL = 3;
	public static final int MAX_FIGHTERS = 8;
	public static final int BOUT_TICKS = 600;
	public static final int PAUSE_TICKS = 60;
	/** Candidates whose stroll anchor is this close to the stele take part. */
	public static final double SEARCH_RADIUS = 24.0D;

	static final int STATE_WAITING = 0;
	static final int STATE_RUNNING = 1;
	static final int STATE_DONE = 2;

	private static final class War {
		final UUID owner;
		final ServerLevel level;
		final Vec3 center;
		final Bracket bracket;
		final Map<Bracket.Entrant, DiscipleEntity> youths = new HashMap<>();
		@Nullable Bracket.Match current;
		@Nullable DiscipleEntity bodyA;
		@Nullable DiscipleEntity bodyB;
		int pause = PAUSE_TICKS;
		boolean fighting;

		War(UUID owner, ServerLevel level, Vec3 center, Bracket bracket) {
			this.owner = owner;
			this.level = level;
			this.center = center;
			this.bracket = bracket;
		}
	}

	private static final Map<UUID, War> WARS = new HashMap<>();

	static boolean running(UUID owner) {
		return WARS.containsKey(owner);
	}

	private static CompoundTag saved(UUID owner) {
		return EventUtil.section(owner).getCompoundOrEmpty("war");
	}

	/** True when today's war has been fought already. */
	private static boolean doneToday(UUID owner) {
		CompoundTag war = saved(owner);
		return war.getLongOr("day", Long.MIN_VALUE) == EventUtil.day() && war.getIntOr("state", STATE_WAITING) == STATE_DONE;
	}

	/** Today's candidates of the hall standing near the stele (module A's views), up to 8. */
	static List<DiscipleEntity> youths(Halls.HallView hall) {
		List<DiscipleEntity> list = new ArrayList<>();
		ServerLevel level = EventUtil.hallLevel(hall);
		if (level == null || !EventUtil.hallLoaded(hall)) {
			return list;
		}
		Vec3 center = EventUtil.center(hall);
		AABB box = new AABB(hall.pos()).inflate(SEARCH_RADIUS + 12.0D, 16.0D, SEARCH_RADIUS + 12.0D);
		for (DiscipleEntity body : level.getEntitiesOfClass(DiscipleEntity.class, box, each -> each.isAlive()
				&& each.role() == DiscipleEntity.Role.CANDIDATE)) {
			BlockPos anchor = body.roleAnchor();
			Vec3 home = anchor == null ? body.position() : Vec3.atBottomCenterOf(anchor);
			double dx = home.x - center.x;
			double dz = home.z - center.z;
			if (dx * dx + dz * dz <= SEARCH_RADIUS * SEARCH_RADIUS && !DuelRules.inDuel(body)) {
				list.add(body);
			}
		}
		list.sort(Comparator.comparingInt((DiscipleEntity body) -> Talents.tekad(body.data())).reversed());
		return list.size() > MAX_FIGHTERS ? new ArrayList<>(list.subList(0, MAX_FIGHTERS)) : list;
	}

	/**
	 * "Mulai Perang": the war of today's candidates starts. {@code test} (debug command) ignores the war day and starts
	 * with whatever candidates are present.
	 */
	static boolean start(ServerPlayer player, boolean test) {
		UUID owner = player.getUUID();
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null || !EventUtil.hallLoaded(hall)) {
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.tourney.need_hall").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (!test && (hall.level() < MIN_HALL_LEVEL || !Candidates.isWarDay(owner) || doneToday(owner))) {
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.war.none").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (WARS.containsKey(owner) || DuelArena.busy(owner) || Tournament.running(owner) || VoidRealm.climbing(owner) || PromotionTrial.running(owner)) {
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.arena.busy").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		List<DiscipleEntity> bodies = youths(hall);
		int size = 1;
		while (size * 2 <= bodies.size()) {
			size *= 2;
		}
		if (bodies.size() < 2) {
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.war.too_few").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		List<Bracket.Entrant> seeds = new ArrayList<>();
		Map<Bracket.Entrant, DiscipleEntity> map = new HashMap<>();
		for (DiscipleEntity body : bodies.subList(0, size)) {
			int tekad = Talents.tekad(body.data());
			Bracket.Entrant entrant = new Bracket.Entrant(Bracket.YOUTH, body.getUUID(), body.getName(), body.data(), body.skinIndex(),
					body.slimSkin(), tekad);
			entrant.tekad = tekad;
			seeds.add(entrant);
			map.put(entrant, body);
		}
		War war = new War(owner, EventUtil.hallLevel(hall), EventUtil.center(hall), new Bracket(seeds, false));
		war.youths.putAll(map);
		WARS.put(owner, war);
		CompoundTag tag = new CompoundTag();
		tag.putLong("day", EventUtil.day());
		tag.putInt("state", STATE_RUNNING);
		EventUtil.section(owner).put("war", tag);
		EventUtil.dirty();
		EventUtil.chat(player, Component.translatable("kultivator.sectevent.war.begins", size).withStyle(ChatFormatting.GOLD));
		war.level.playSound(null, war.center.x, war.center.y, war.center.z, SoundEvents.RAID_HORN, SoundSource.NEUTRAL, 0.5F, 1.0F);
		Halls.log(owner, Component.translatable("kultivator.sectevent.log.war_start", size));
		return true;
	}

	static void tick(MinecraftServer server) {
		if (WARS.isEmpty()) {
			return;
		}
		for (War war : new ArrayList<>(WARS.values())) {
			if (EventUtil.player(war.owner) == null) {
				cancel(war);
				continue;
			}
			if (war.fighting || --war.pause > 0) {
				continue;
			}
			Bracket.Match match = war.bracket.next();
			if (match == null) {
				finish(war);
				continue;
			}
			startBout(war, match);
		}
	}

	private static void startBout(War war, Bracket.Match match) {
		war.current = match;
		war.pause = PAUSE_TICKS;
		war.bodyA = step(war, match.a, -1);
		war.bodyB = step(war, match.b, 1);
		if (war.bodyA == null || war.bodyB == null) {
			// A youth is gone (it left at sunrise, or there is no room): the other goes through.
			Bracket.Entrant winner = war.bodyA != null ? match.a : war.bodyB != null ? match.b : Bracket.simulate(match, war.level.getRandom());
			release(war);
			report(war, match, winner);
			return;
		}
		DuelArena.Bout bout = DuelArena.start(war.owner, war.level, war.center, war.bodyA, war.bodyB, BOUT_TICKS, DuelArena.OUT_RADIUS, false,
				(winner, loser, reason) -> boutEnded(war, match, winner));
		if (bout == null) {
			release(war);
			report(war, match, Bracket.simulate(match, war.level.getRandom()));
			return;
		}
		war.fighting = true;
		if (war.bracket.rounds.get(war.bracket.rounds.size() - 1).size() == 1 && !match.bronze) {
			dev.kultivator.epic.EpicHooks.tournament(war.level, dev.kultivator.epic.EpicIds.TOUR_INTRO, dev.kultivator.epic.EpicIds.TOUR_HUNDRED_DYNASTIES, war.bodyA, war.bodyB, war.center);
		}
		EventUtil.chat(EventUtil.player(war.owner), Component.translatable("kultivator.sectevent.tourney.bout", match.a.name, match.b.name)
				.withStyle(ChatFormatting.YELLOW));
	}

	/** The youth steps into its side of the ring ({@code side} -1 or 1), fully healed; null when it cannot. */
	private static @Nullable DiscipleEntity step(War war, Bracket.Entrant entrant, int side) {
		DiscipleEntity body = war.youths.get(entrant);
		if (body == null || body.isRemoved() || !body.isAlive() || body.level() != war.level || body.role() != DiscipleEntity.Role.CANDIDATE) {
			return null;
		}
		Vec3 target = war.center.add(side * Tournament.FIGHTER_OFFSET, 0.0D, 0.0D);
		Vec3 safe = Targeting.safeSpot(war.level, body, target);
		if (safe == null) {
			return null;
		}
		EventUtil.fx(war.level, body.position(), SectEventFxIds.DEPART, entrant.data.rootColor(), 0.0F);
		body.getNavigation().stop();
		body.snapTo(safe.x, safe.y, safe.z, side < 0 ? -90.0F : 90.0F, 0.0F);
		body.setActivity(DiscipleEntity.ACTIVITY_DUELLIST, null);
		body.setHealth(body.getMaxHealth());
		EventUtil.fx(war.level, body.position(), SectEventFxIds.RETURN, entrant.data.rootColor(), 0.0F);
		return body;
	}

	private static void boutEnded(War war, Bracket.Match match, @Nullable LivingEntity winner) {
		war.fighting = false;
		war.pause = PAUSE_TICKS;
		Bracket.Entrant result;
		if (winner != null && winner == war.bodyA) {
			result = match.a;
		} else if (winner != null && winner == war.bodyB) {
			result = match.b;
		} else {
			boolean aGone = war.bodyA == null || war.bodyA.isRemoved();
			boolean bGone = war.bodyB == null || war.bodyB.isRemoved();
			result = aGone && !bGone ? match.b : bGone && !aGone ? match.a : Bracket.simulate(match, war.level.getRandom());
		}
		release(war);
		if (WARS.get(war.owner) == war) {
			report(war, match, result);
		}
	}

	private static void report(War war, Bracket.Match match, Bracket.Entrant winner) {
		war.bracket.report(match, winner);
		Bracket.Entrant loser = match.loser();
		EventUtil.chat(EventUtil.player(war.owner), Component.translatable("kultivator.sectevent.tourney.result", winner.name,
				loser == null ? Component.empty() : loser.name).withStyle(ChatFormatting.GRAY));
		war.current = null;
	}

	/** The two youths of the last bout are healed and stroll back to their spots (they are module A's candidates). */
	private static void release(War war) {
		for (DiscipleEntity body : new DiscipleEntity[]{war.bodyA, war.bodyB}) {
			if (body != null && !body.isRemoved()) {
				body.setHealth(body.getMaxHealth());
				body.setTarget(null);
				if (body.activity() == DiscipleEntity.ACTIVITY_DUELLIST) {
					body.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
				}
			}
		}
		war.bodyA = null;
		war.bodyB = null;
	}

	/** The champion is crowned by the Sect Hall; everyone stays a candidate until sunrise. */
	private static void finish(War war) {
		WARS.remove(war.owner, war);
		Bracket.Entrant champion = war.bracket.first();
		CompoundTag saved = EventUtil.sub(EventUtil.section(war.owner), "war");
		saved.putLong("day", EventUtil.day());
		saved.putInt("state", STATE_DONE);
		ServerPlayer player = EventUtil.player(war.owner);
		if (champion != null) {
			DiscipleEntity body = war.youths.get(champion);
			if (body != null && !body.isRemoved()) {
				Candidates.crownChampion(war.owner, body);
			}
			saved.store("champion", ComponentSerialization.CODEC, champion.name);
			EventUtil.fx(war.level, war.center, SectEventFxIds.CHAMPION, 0, 0.0F);
			dev.kultivator.epic.EpicHooks.tournament(war.level, dev.kultivator.epic.EpicIds.TOUR_WINNER, dev.kultivator.epic.EpicIds.TOUR_HUNDRED_DYNASTIES, body, null, war.center);
			war.level.playSound(null, war.center.x, war.center.y, war.center.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.NEUTRAL, 1.0F, 1.2F);
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.war.champion", champion.name).withStyle(ChatFormatting.GOLD));
			if (player != null) {
				Advancements.grant(player, "hundred_dynasties_champion");
			}
			Halls.log(war.owner, Component.translatable("kultivator.sectevent.log.war_champion", champion.name));
		}
		EventUtil.dirty();
	}

	private static void cancel(War war) {
		WARS.remove(war.owner, war);
		if (war.fighting) {
			DuelArena.cancel(war.owner);
		}
		release(war);
		// The youths wait: the war can start again today.
		EventUtil.sub(EventUtil.section(war.owner), "war").putInt("state", STATE_WAITING);
		EventUtil.dirty();
	}

	/** Today's youths, the state and the bracket for the Arena tablet. */
	static CompoundTag forClient(UUID owner, @Nullable Halls.HallView hall) {
		CompoundTag tag = new CompoundTag();
		CompoundTag saved = saved(owner);
		boolean today = hall != null && hall.level() >= MIN_HALL_LEVEL && Candidates.isWarDay(owner);
		tag.putBoolean("today", today);
		boolean savedToday = saved.getLongOr("day", Long.MIN_VALUE) == EventUtil.day();
		tag.putInt("state", WARS.containsKey(owner) ? STATE_RUNNING : savedToday ? saved.getIntOr("state", STATE_WAITING) : STATE_WAITING);
		ListTag youths = new ListTag();
		if (today && hall != null) {
			for (DiscipleEntity body : youths(hall)) {
				CompoundTag entry = new CompoundTag();
				entry.store("name", ComponentSerialization.CODEC, body.getName());
				entry.putInt("tekad", Talents.tekad(body.data()));
				youths.add(entry);
			}
		}
		tag.put("youths", youths);
		if (savedToday) {
			saved.read("champion", ComponentSerialization.CODEC).ifPresent(name -> tag.store("champion", ComponentSerialization.CODEC, name));
		}
		War war = WARS.get(owner);
		if (war != null) {
			tag.put("bracket", war.bracket.forClient(war.current));
		}
		return tag;
	}

	static void clear() {
		WARS.clear();
	}

	private HundredDynasties() {
	}
}
