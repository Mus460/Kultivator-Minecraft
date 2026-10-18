package dev.kultivator.sect.hall;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.alchemy.AlchemyComponents;
import dev.kultivator.alchemy.AlchemyItems;
import dev.kultivator.alchemy.PillQuality;
import dev.kultivator.cultivation.SpiritRoots;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.RootGrade;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntities;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleNames;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hari Penerimaan Murid (DESIGN 20.3.3-20.3.4): candidates arrive at sunrise on the arrival days, are tested at the stele
 * and accepted as disciples with {@code Disciples.recruitCandidate}. Each candidate is a record in {@code core.candidates}
 * (data first); the CANDIDATE entity is only a view, re-created while the master is near and never saved.
 * <p>
 * Sources: <i>A Record of a Mortal's Journey</i> ch. 1-5 (the Seven Mysteries Sect's test, its three-stage climb, Han
 * Li's determination and the unofficial disciple), <i>Renegade Immortal</i> ch. 3-29 (the honorary disciple), <i>Battle
 * Through the Heavens</i> (the testing stone and its 1-9 Duan, Jia Nan Academy's recruiters), <i>Tales of Demons and
 * Gods</i> ch. 13-14 (soul colours), <i>Soul Land</i> (yearly awakenings), <i>I Shall Seal the Heavens</i> ch. 19 (taken
 * in as servants), <i>Return of the Mount Hua Sect</i> (talented youths avoid a fallen sect); the Immortal Ascension
 * Token is from <i>A Record of a Mortal's Journey</i>.
 */
public final class Candidates {

	public static final int VERDICT_REJECTED = 0;
	public static final int VERDICT_RECORDED = 1;
	public static final int VERDICT_PASSED = 2;
	/** Tekad for the second tier, and the Tekad that makes a Murid Tercatat. */
	public static final int TEKAD_TIER = 40;
	public static final int TEKAD_RECORDED = 70;
	/** The third tier: {@code grade x 20 + Tekad >= 110}. */
	public static final int CLIMB_SUM = 110;
	/** The servant path (ISSTH) costs this much pool PK. */
	public static final int SERVANT_COST = 100;
	public static final int TEST_TICKS = 120;
	public static final int TIER_TICKS = 60;
	public static final double SPAWN_MIN = 8.0D;
	public static final double SPAWN_MAX = 14.0D;
	public static final double VIEW_RANGE = 64.0D;
	public static final double ARRIVAL_RANGE = 128.0D;
	public static final int MAX_VIEWS = 6;
	/** The Hundred Dynasties War brings eight youths (the one documented exception to the 6-body limit of 20.1). */
	public static final int WAR_CANDIDATES = 8;
	/** A record whose safe spot could not be found tries again after this many ticks. */
	public static final int SPOT_RETRY_TICKS = 200;
	/**
	 * Perang Seratus Dinasti (DESIGN 20.3.13, fought by module B): every 4th arrival day from hall level 3 replaces the
	 * normal arrival with eight youths. Counted from the founding day ({@code (day - founded) % (interval x 4) == 0}).
	 */
	public static final int WAR_EVERY = 4;
	public static final int WAR_LEVEL = 3;
	public static final int TOKEN_MIN_TEKAD = 60;
	/** Bond a champion crowned here (not one module B sends with its bond already given) starts with. */
	public static final int CHAMPION_BOND = 20;
	/** Renown gates (Mount Hua): a HIGH root below 300 renown, a HEAVENLY root below 1,000, comes one grade lower. */
	public static final long HIGH_GATE = 300L;
	public static final long HEAVENLY_GATE = 1000L;
	/** Candidates sit and doze between these times of day. */
	public static final int NIGHT_FROM = 12500;
	public static final int NIGHT_TO = 23500;
	private static final int BOW_TICKS = 40;
	private static final int MIST = 0xD8E4E8;

	/** Which hall and record a candidate body is the view of. */
	private record Ref(UUID owner, int id) {
	}

	/** A running entrance test (6 s). */
	private static final class Test {
		final UUID owner;
		final int id;
		final UUID tester;
		final ServerLevel level;
		final BlockPos stele;
		final long start;
		final int tiers;
		final int duan;
		final int colour;

		Test(UUID owner, int id, UUID tester, ServerLevel level, BlockPos stele, long start, int tiers, int duan, int colour) {
			this.owner = owner;
			this.id = id;
			this.tester = tester;
			this.level = level;
			this.stele = stele;
			this.start = start;
			this.tiers = tiers;
			this.duan = duan;
			this.colour = colour;
		}
	}

	/** A candidate bowing before it vanishes. */
	private static final class Leaving {
		final DiscipleEntity entity;
		int ticks = BOW_TICKS;

		Leaving(DiscipleEntity entity) {
			this.entity = entity;
		}
	}

	/** An accepted youth bowing to the master (DESIGN 20.3.3: recruit FX plus a jade ring and a bow); it stays. */
	private static final class Bowing {
		final DiscipleEntity entity;
		int ticks = ACCEPT_BOW_TICKS;

		Bowing(DiscipleEntity entity) {
			this.entity = entity;
		}
	}

	private static final int ACCEPT_BOW_TICKS = 30;
	private static final Map<UUID, Map<Integer, DiscipleEntity>> VIEWS = new HashMap<>();
	private static final Map<UUID, Ref> BY_ENTITY = new HashMap<>();
	private static final List<Test> TESTS = new ArrayList<>();
	private static final List<Leaving> LEAVING = new ArrayList<>();
	private static final List<Bowing> BOWING = new ArrayList<>();
	/** Owner -> record id -> server tick before which no new safe-spot search is made (transient). */
	private static final Map<UUID, Map<Integer, Integer>> RETRY = new HashMap<>();

	static void clearAll() {
		VIEWS.clear();
		BY_ENTITY.clear();
		TESTS.clear();
		LEAVING.clear();
		BOWING.clear();
		RETRY.clear();
	}

	// ================================================================== records

	static ListTag list(CompoundTag core) {
		ListTag list = core.getList("candidates").orElse(null);
		if (list == null) {
			list = new ListTag();
			core.put("candidates", list);
		}
		return list;
	}

	static @Nullable CompoundTag find(CompoundTag core, int id) {
		for (Tag tag : list(core)) {
			if (tag instanceof CompoundTag rec && rec.getIntOr("id", -1) == id) {
				return rec;
			}
		}
		return null;
	}

	private static void remove(CompoundTag core, int id) {
		ListTag list = list(core);
		for (int i = list.size() - 1; i >= 0; i--) {
			if (list.get(i) instanceof CompoundTag rec && rec.getIntOr("id", -1) == id) {
				list.remove(i);
			}
		}
	}

	static DiscipleData dataOf(CompoundTag rec) {
		return DiscipleData.load(rec.getCompoundOrEmpty("data"));
	}

	private static void storeData(CompoundTag rec, DiscipleData data) {
		rec.put("data", data.save());
	}

	static Component nameOf(CompoundTag rec) {
		return rec.read("name", ComponentSerialization.CODEC).orElse(Component.translatable("entity.kultivator.disciple"));
	}

	/** Tiers of the climb: +1 for a LOW+ root, +1 for Tekad 40+, +1 for {@code grade x 20 + Tekad >= 110}. */
	public static int tiers(DiscipleData data) {
		int grade = data.rootGrade().ordinal();
		int tekad = Talents.tekad(data);
		return (grade >= RootGrade.LOW.ordinal() ? 1 : 0) + (tekad >= TEKAD_TIER ? 1 : 0) + (grade * 20 + tekad >= CLIMB_SUM ? 1 : 0);
	}

	/** Lulus (all three tiers), Murid Tercatat (Tekad 70+, the Han Li / Wang Lin path) or Ditolak. */
	public static int verdict(DiscipleData data) {
		if (tiers(data) >= 3) {
			return VERDICT_PASSED;
		}
		return Talents.tekad(data) >= TEKAD_RECORDED ? VERDICT_RECORDED : VERDICT_REJECTED;
	}

	/** A new record (the rolls written into {@code data}); not yet listed. */
	private static CompoundTag newRecord(CompoundTag core, DiscipleData data, RandomSource random, String source) {
		CompoundTag rec = new CompoundTag();
		int id = core.getIntOr("next_id", 1);
		core.putInt("next_id", id + 1);
		rec.putInt("id", id);
		List<Component> taken = new ArrayList<>();
		for (Tag tag : list(core)) {
			if (tag instanceof CompoundTag other) {
				taken.add(nameOf(other));
			}
		}
		rec.store("name", ComponentSerialization.CODEC, DiscipleNames.pick(random, taken));
		rec.putInt("skin", random.nextInt(DiscipleEntity.SKIN_COUNT));
		rec.putBoolean("slim", random.nextBoolean());
		rec.putLong("expires_day", Halls.dayIndex() + 1L);
		rec.putString("source", source == null ? "" : source);
		storeData(rec, data);
		return rec;
	}

	/** A youth of the normal arrival: root roll, renown gate, then the talents (Hanya Monster: only HIGH+ roots or physiques). */
	private static CompoundTag rollNormal(CompoundTag core, int hallLevel, long renown, boolean monstersOnly, RandomSource random, String source) {
		DiscipleData data = null;
		boolean gated = false;
		for (int attempt = 0; attempt < 40; attempt++) {
			data = DiscipleData.fresh(random);
			gated = gate(data, renown);
			Talents.rollFor(data, -1, -1.0D, -1.0D, hallLevel, random);
			if (!monstersOnly || Talents.physique(data) > 0 || data.rootGrade().ordinal() >= RootGrade.HIGH.ordinal()) {
				break;
			}
			if (attempt == 39) {
				data.setRootGrade(RootGrade.HIGH);
				Talents.setSoulColor(data, Talents.rollColour(RootGrade.HIGH, random));
				Talents.refreshDuan(data);
			}
		}
		CompoundTag rec = newRecord(core, data, random, source);
		rec.putBoolean("gated", gated);
		return rec;
	}

	/** Talented youths avoid an unknown sect: one grade lower. True when a gate applied. */
	private static boolean gate(DiscipleData data, long renown) {
		RootGrade grade = data.rootGrade();
		if (grade == RootGrade.HEAVENLY && renown < HEAVENLY_GATE) {
			data.setRootGrade(RootGrade.HIGH);
			return true;
		}
		if (grade == RootGrade.HIGH && renown < HIGH_GATE) {
			data.setRootGrade(RootGrade.MID);
			return true;
		}
		return false;
	}

	// ================================================================== schedule

	/**
	 * True for an arrival day (DESIGN 20.3.3): the first sunrise after founding, then every day with
	 * {@code d = founded_day (mod recruitmentIntervalDays)}; every 4th of those is the Hundred Dynasties War.
	 */
	static boolean isArrivalDay(CompoundTag core, long day) {
		long founded = core.getLongOr("founded_day", 0L);
		int interval = Math.max(1, KultivatorConfig.get().recruitmentIntervalDays);
		return day == founded + 1L || day > founded && Math.floorMod(day - founded, (long) interval) == 0L;
	}

	/** True when {@code day} is a Hundred Dynasties War day of this hall (eight youths come instead of the arrival). */
	static boolean isWarDay(UUID owner, long day) {
		int interval = Math.max(1, KultivatorConfig.get().recruitmentIntervalDays);
		CompoundTag core = Halls.coreOf(owner);
		long founded = core == null ? 0L : core.getLongOr("founded_day", 0L);
		return KultivatorConfig.get().sectEvents && Halls.level(owner) >= WAR_LEVEL && day > founded
				&& Math.floorMod(day - founded, (long) interval * WAR_EVERY) == 0L;
	}

	/** Candidate bodies a hall may show at once: 6, or 8 on a war day. */
	static int maxViews(UUID owner) {
		return isWarDay(owner, Halls.dayIndex()) ? WAR_CANDIDATES : MAX_VIEWS;
	}

	/** The next arrival day after the last one (today when today's arrival is still waiting). */
	static long nextArrivalDay(CompoundTag core, long today) {
		long last = core.getLongOr("last_arrival", core.getLongOr("founded_day", today));
		for (long day = Math.max(today, last + 1L); day < today + 64L; day++) {
			if (isArrivalDay(core, day)) {
				return day;
			}
		}
		return today + KultivatorConfig.get().recruitmentIntervalDays;
	}

	/** True when the next arrival day will be the Hundred Dynasties War. */
	static boolean nextIsWar(UUID owner, CompoundTag core, long today) {
		return isWarDay(owner, nextArrivalDay(core, today));
	}

	/** True on a war day (module B's Perang Seratus Dinasti instead of the normal arrival). */
	public static boolean isWarDay(UUID owner) {
		return isWarDay(owner, Halls.dayIndex());
	}

	// ================================================================== the hall tick (once a second per active hall)

	static boolean tick(MinecraftServer server, UUID owner, CompoundTag core, ServerLevel level, BlockPos stele, long day, int timeOfDay) {
		boolean changed = false;
		boolean loaded = level.isLoaded(stele);
		ServerPlayer master = server.getPlayerList().getPlayer(owner);
		double masterDistSq = master != null && master.level() == level ? horizontalSq(master.position(), stele) : Double.MAX_VALUE;

		// Yesterday's candidates leave first, so an arrival never doubles the bodies.
		ListTag list = list(core);
		for (int i = list.size() - 1; i >= 0; i--) {
			if (list.get(i) instanceof CompoundTag rec && rec.getLongOr("expires_day", 0L) <= day) {
				leave(owner, rec.getIntOr("id", -1), level, true);
				forgetRetry(owner, rec.getIntOr("id", -1));
				list.remove(i);
				changed = true;
			}
		}

		if (KultivatorConfig.get().sectHall && loaded && masterDistSq <= ARRIVAL_RANGE * ARRIVAL_RANGE
				&& core.getLongOr("last_arrival", day) < day && isArrivalDay(core, day)) {
			core.putLong("last_arrival", day);
			if (isWarDay(owner, day)) {
				// The Hundred Dynasties War replaces today's arrival: eight youths come to fight (module B runs the bouts).
				Halls.log(owner, Component.translatable("kultivator.hall.cand.war_day"));
				if (master != null) {
					Msg.chat(master, Component.translatable("kultivator.hall.cand.war_day").withStyle(ChatFormatting.RED));
				}
				arriveWar(server, owner, core, level, stele, master);
			} else {
				arrive(server, owner, core, level, stele, master, -1);
			}
			changed = true;
		}

		Map<Integer, DiscipleEntity> views = VIEWS.computeIfAbsent(owner, id -> new HashMap<>());
		views.values().removeIf(entity -> {
			if (entity.isRemoved() || !entity.isAlive()) {
				BY_ENTITY.remove(entity.getUUID());
				return true;
			}
			return false;
		});
		if (loaded && masterDistSq <= VIEW_RANGE * VIEW_RANGE) {
			int cap = maxViews(owner);
			for (Tag tag : list) {
				if (views.size() >= cap) {
					break;
				}
				if (tag instanceof CompoundTag rec && !views.containsKey(rec.getIntOr("id", -1))) {
					spawnView(owner, rec, level, stele, false);
				}
			}
		}
		boolean night = timeOfDay >= NIGHT_FROM && timeOfDay < NIGHT_TO;
		for (DiscipleEntity entity : views.values()) {
			if (DuelRules.inDuel(entity)) {
				continue;
			}
			if (night && entity.activity() == DiscipleEntity.ACTIVITY_NONE) {
				entity.setActivity(DiscipleEntity.ACTIVITY_SPECTATOR, entity.blockPosition());
			} else if (!night && entity.activity() == DiscipleEntity.ACTIVITY_SPECTATOR) {
				entity.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
			}
		}
		return changed;
	}

	private static double horizontalSq(Vec3 pos, BlockPos stele) {
		double dx = pos.x - (stele.getX() + 0.5D);
		double dz = pos.z - (stele.getZ() + 0.5D);
		return dx * dx + dz * dz;
	}

	/**
	 * Candidates arrive ({@code count} < 0: the hall's number, one less with Hanya Monster): out of a travellers' mist 8-14
	 * blocks from the stele, a distant bell, a chat line and a title for the master.
	 */
	static int arrive(MinecraftServer server, UUID owner, CompoundTag core, ServerLevel level, BlockPos stele, @Nullable ServerPlayer master, int count) {
		RandomSource random = level.getRandom();
		int hallLevel = Halls.level(owner);
		long renown = core.getLongOr("renown", 0L);
		boolean monsters = core.getBooleanOr("monster", false);
		int n;
		if (count >= 0) {
			n = Math.max(1, Math.min(HallLevel.MAX_CANDIDATES, count));
		} else {
			core.putInt("arrivals", core.getIntOr("arrivals", 0) + 1);
			n = HallLevel.candidates(hallLevel) + (renown >= HallLevel.FAME_RENOWN ? 1 : 0);
			// Phase 6b: +1 for large templates at tier 7 or more (DESIGN 21.1); the maximum of 6 still holds.
			n += HallLevel.candidateBonus(owner);
			n = Math.min(HallLevel.MAX_CANDIDATES, n);
			if (monsters) {
				n = Math.max(1, n - 1);
			}
		}
		List<CompoundTag> fresh = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			CompoundTag rec = rollNormal(core, hallLevel, renown, monsters, random, "arrival");
			list(core).add(rec);
			fresh.add(rec);
		}
		rollSpecial(core, fresh, random);
		Halls.setDirty();
		for (CompoundTag rec : fresh) {
			spawnView(owner, rec, level, stele, true);
		}
		bells(level, stele);
		dev.kultivator.epic.EpicHooks.recruit(level, owner, dev.kultivator.epic.EpicIds.RECRUIT_ARRIVAL, Vec3.atBottomCenterOf(stele), n, 0);
		Component line = Component.translatable("kultivator.hall.cand.arrived", n);
		Halls.log(owner, line);
		if (master != null) {
			Msg.chat(master, line.copy().withStyle(ChatFormatting.GOLD));
			Msg.chat(master, Component.translatable("kultivator.hall.cand.arrived_hint").withStyle(ChatFormatting.GRAY));
			Msg.title(master, Component.translatable("kultivator.hall.cand.title").withStyle(ChatFormatting.GOLD),
					Component.translatable("kultivator.hall.cand.subtitle", n).withStyle(ChatFormatting.YELLOW), 10, 50, 20);
			toast(master, stele, n, false);
		}
		return n;
	}

	/**
	 * Perang Seratus Dinasti (DESIGN 20.3.13): the eight youths of a war day arrive (normal rolls, marked {@code war}, not
	 * clamped by {@code HallLevel.MAX_CANDIDATES}); module B pits them against each other at the stele.
	 */
	static int arriveWar(MinecraftServer server, UUID owner, CompoundTag core, ServerLevel level, BlockPos stele, @Nullable ServerPlayer master) {
		RandomSource random = level.getRandom();
		int hallLevel = Halls.level(owner);
		long renown = core.getLongOr("renown", 0L);
		core.putInt("arrivals", core.getIntOr("arrivals", 0) + 1);
		List<CompoundTag> fresh = new ArrayList<>();
		for (int i = 0; i < WAR_CANDIDATES; i++) {
			CompoundTag rec = rollNormal(core, hallLevel, renown, false, random, "war");
			rec.putBoolean("war", true);
			list(core).add(rec);
			fresh.add(rec);
		}
		rollSpecial(core, fresh, random);
		Halls.setDirty();
		for (CompoundTag rec : fresh) {
			// Yesterday's records have expired by now, so these eight are the hall's only bodies.
			spawnView(owner, rec, level, stele, true, true);
		}
		bells(level, stele);
		dev.kultivator.epic.EpicHooks.recruit(level, owner, dev.kultivator.epic.EpicIds.RECRUIT_ARRIVAL, Vec3.atBottomCenterOf(stele), WAR_CANDIDATES, 1);
		Component line = Component.translatable("kultivator.hall.cand.war_arrived", WAR_CANDIDATES);
		Halls.log(owner, line);
		if (master != null) {
			Msg.chat(master, line.copy().withStyle(ChatFormatting.GOLD));
			Msg.title(master, Component.translatable("kultivator.hall.cand.war_title").withStyle(ChatFormatting.RED),
					Component.translatable("kultivator.hall.cand.subtitle", WAR_CANDIDATES).withStyle(ChatFormatting.YELLOW), 10, 50, 20);
			toast(master, stele, WAR_CANDIDATES, true);
		}
		return WAR_CANDIDATES;
	}

	private static void bells(ServerLevel level, BlockPos stele) {
		Vec3 top = Vec3.atBottomCenterOf(stele).add(0.0D, 1.0D, 0.0D);
		level.playSound(null, top.x, top.y, top.z, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 2.5F, 0.55F);
		level.playSound(null, top.x, top.y, top.z, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.5F, 0.6F);
	}

	/** The arrival toast for the master only (client {@code HallFx} shows it): b = count, f = 1 on a war day. */
	private static void toast(ServerPlayer master, BlockPos stele, int count, boolean war) {
		Vec3 top = Vec3.atBottomCenterOf(stele);
		Fx.sendTo(master, top, top, FxType.HALL, HallFxIds.ARRIVAL_TOAST, count, war ? 1.0F : 0.0F);
	}

	/** At most one special candidate per arrival: Beggar 2% (once per hall), Regressor 3%, Blocked Dantian 4%. */
	private static void rollSpecial(CompoundTag core, List<CompoundTag> fresh, RandomSource random) {
		if (fresh.isEmpty()) {
			return;
		}
		double r = random.nextDouble();
		int type;
		if (r < Talents.PENGEMIS_CHANCE && !core.getBooleanOr("beggar", false)) {
			type = Talents.PENGEMIS;
			core.putBoolean("beggar", true);
		} else if (r < Talents.PENGEMIS_CHANCE + Talents.REGRESOR_CHANCE) {
			type = Talents.REGRESOR;
		} else if (r < Talents.PENGEMIS_CHANCE + Talents.REGRESOR_CHANCE + Talents.DANTIAN_CHANCE) {
			type = Talents.DANTIAN_TERSUMBAT;
		} else {
			return;
		}
		CompoundTag rec = fresh.get(random.nextInt(fresh.size()));
		DiscipleData data = dataOf(rec);
		Talents.makeSpecial(data, type, random);
		storeData(rec, data);
	}

	/** Candidates other modules send (envoys, the war): their data is already rolled for the root. */
	static void addFromSpecs(UUID owner, CompoundTag core, List<Halls.CandidateSpec> specs) {
		ServerLevel level = Halls.levelOf(core);
		BlockPos stele = Halls.posOf(core);
		RandomSource random = level != null ? level.getRandom() : RandomSource.create();
		int hallLevel = Halls.level(owner);
		for (Halls.CandidateSpec spec : specs) {
			if (spec == null || spec.data() == null) {
				continue;
			}
			DiscipleData data = spec.data().copy();
			Talents.rollFor(data, spec.tekad(), spec.talentChance(), spec.physiqueChance(), hallLevel, random);
			if (spec.tested()) {
				Talents.setTested(data, true);
			}
			CompoundTag rec = newRecord(core, data, random, spec.source());
			if ("war".equals(spec.source())) {
				rec.putBoolean("war", true);
				// Module B sends the champion tested (Qi Refining, bond already given) and the other finalist untested.
				if (spec.tested()) {
					rec.putBoolean("champion", true);
				}
			}
			list(core).add(rec);
			if (level != null && stele != null && !Halls.dormant(core)) {
				spawnView(owner, rec, level, stele, true);
			}
		}
		Halls.setDirty();
	}

	/** Undangan Khusus: one extra candidate now (normal rolls). False without an active hall. */
	static boolean invite(UUID owner) {
		CompoundTag core = Halls.coreOf(owner);
		ServerLevel level = core == null ? null : Halls.levelOf(core);
		BlockPos stele = core == null ? null : Halls.posOf(core);
		if (core == null || level == null || stele == null || Halls.dormant(core)) {
			return false;
		}
		CompoundTag rec = rollNormal(core, Halls.level(owner), core.getLongOr("renown", 0L), false, level.getRandom(), "invite");
		list(core).add(rec);
		Halls.setDirty();
		spawnView(owner, rec, level, stele, true);
		Halls.log(owner, Component.translatable("kultivator.hall.cand.invited", nameOf(rec)));
		return true;
	}

	/** The Immortal Ascension Token used on the master's active stele: a tested candidate (root MID+, Tekad 60+) at once. */
	static void useToken(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack stack) {
		UUID owner = player.getUUID();
		CompoundTag core = Halls.coreOf(owner);
		if (core == null || Halls.dormant(core) || !pos.equals(Halls.posOf(core)) || level != Halls.levelOf(core)) {
			Msg.chat(player, Component.translatable("kultivator.hall.token.wrong_stele").withStyle(ChatFormatting.YELLOW));
			return;
		}
		RandomSource random = level.getRandom();
		DiscipleData data = DiscipleData.fresh(random);
		for (int attempt = 0; attempt < 40 && data.rootGrade().ordinal() < RootGrade.MID.ordinal(); attempt++) {
			data = DiscipleData.fresh(random);
		}
		if (data.rootGrade().ordinal() < RootGrade.MID.ordinal()) {
			data.setRootGrade(RootGrade.MID);
		}
		Talents.rollFor(data, TOKEN_MIN_TEKAD + random.nextInt(101 - TOKEN_MIN_TEKAD), -1.0D, -1.0D, Halls.level(owner), random);
		if (data.rootGrade().ordinal() < RootGrade.MID.ordinal()) {
			// The Heretic God Veins hide under a trash root; the token still promises MID: no veins for it.
			Talents.setPhysique(data, 0);
			data.setRootGrade(RootGrade.MID);
			Talents.setSoulColor(data, Talents.rollColour(RootGrade.MID, random));
			Talents.refreshDuan(data);
		}
		Talents.setTested(data, true);
		CompoundTag rec = newRecord(core, data, random, "token");
		rec.putBoolean("token", true);
		list(core).add(rec);
		Halls.setDirty();
		if (!player.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
		Vec3 top = Vec3.atBottomCenterOf(pos).add(0.0D, 1.2D, 0.0D);
		Fx.send(level, top, FxType.HALL, HallFxIds.TOKEN, 0xFFD54A, 3.0F);
		level.playSound(null, top.x, top.y, top.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.4F);
		level.playSound(null, top.x, top.y, top.z, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.2F, 1.0F);
		spawnView(owner, rec, level, pos, true);
		Msg.chat(player, Component.translatable("kultivator.hall.token.used", nameOf(rec)).withStyle(ChatFormatting.GOLD));
		Halls.log(owner, Component.translatable("kultivator.hall.log.token", nameOf(rec)));
		Advancements.grant(player, "immortal_token");
	}

	// ================================================================== views

	private static @Nullable DiscipleEntity spawnView(UUID owner, CompoundTag rec, ServerLevel level, BlockPos stele, boolean arriving) {
		return spawnView(owner, rec, level, stele, arriving, false);
	}

	/**
	 * Spawns the body of a record on a safe spot 8-14 blocks from the stele (its last spot when it still fits). At most
	 * {@link #maxViews} bodies exist (DESIGN 20.1); a record over the cap keeps waiting and the per-second loop shows it
	 * when a slot frees. {@code force} (the master accepting this very record, which turns it into a disciple at once)
	 * skips the cap and the retry wait.
	 */
	private static @Nullable DiscipleEntity spawnView(UUID owner, CompoundTag rec, ServerLevel level, BlockPos stele, boolean arriving, boolean force) {
		int id = rec.getIntOr("id", -1);
		Map<Integer, DiscipleEntity> views = VIEWS.computeIfAbsent(owner, key -> new HashMap<>());
		DiscipleEntity existing = views.get(id);
		if (existing != null && !existing.isRemoved()) {
			return existing;
		}
		views.values().removeIf(entity -> {
			if (entity.isRemoved()) {
				BY_ENTITY.remove(entity.getUUID());
				return true;
			}
			return false;
		});
		if (!force && views.size() >= maxViews(owner)) {
			return null;
		}
		if (!level.isLoaded(stele)) {
			return null;
		}
		int now = level.getServer().getTickCount();
		Map<Integer, Integer> retry = RETRY.get(owner);
		Integer nextTry = retry == null ? null : retry.get(id);
		if (!force && nextTry != null && now < nextTry) {
			return null;
		}
		DiscipleEntity probe = DiscipleEntities.DISCIPLE.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (probe == null) {
			return null;
		}
		Vec3 spot = null;
		int[] last = rec.getIntArray("pos").orElse(null);
		if (last != null && last.length == 3) {
			Vec3 at = new Vec3(last[0] + 0.5D, last[1], last[2] + 0.5D);
			if (horizontalSq(at, stele) <= (SPAWN_MAX + 4.0D) * (SPAWN_MAX + 4.0D) && level.isLoaded(BlockPos.containing(at))) {
				spot = Targeting.safeSpot(level, probe, at);
			}
		}
		RandomSource random = level.getRandom();
		if (spot == null) {
			// Phase 6b: the built plan's arrival anchor and kept ring cells first (DESIGN 21.17); none keeps the ring below.
			BlockPos provided = Halls.arrivalSpot(owner, level, stele, random);
			if (provided != null && level.isLoaded(provided)) {
				spot = Targeting.safeSpot(level, probe, Vec3.atBottomCenterOf(provided));
			}
		}
		for (int attempt = 0; spot == null && attempt < 16; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double radius = SPAWN_MIN + random.nextDouble() * (SPAWN_MAX - SPAWN_MIN);
			Vec3 near = new Vec3(stele.getX() + 0.5D + Math.cos(angle) * radius, stele.getY(), stele.getZ() + 0.5D + Math.sin(angle) * radius);
			if (level.isLoaded(BlockPos.containing(near))) {
				spot = Targeting.safeSpot(level, probe, near);
			}
		}
		if (spot == null) {
			RETRY.computeIfAbsent(owner, key -> new HashMap<>()).put(id, now + SPOT_RETRY_TICKS);
			return null;
		}
		forgetRetry(owner, id);
		DiscipleData data = dataOf(rec);
		DiscipleEntity body = Disciples.createCandidate(level, spot, data, nameOf(rec), rec.getIntOr("skin", 0), rec.getBooleanOr("slim", false),
				BlockPos.containing(spot));
		if (body == null) {
			return null;
		}
		BlockPos at = body.blockPosition();
		rec.putIntArray("pos", new int[]{at.getX(), at.getY(), at.getZ()});
		body.setSyncedTalent(Talents.packed(data));
		views.put(id, body);
		BY_ENTITY.put(body.getUUID(), new Ref(owner, id));
		if (arriving) {
			Fx.send(level, spot, FxType.HALL, HallFxIds.MIST, MIST, 1.0F);
			level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.NEUTRAL, 0.6F, 0.9F);
		}
		return body;
	}

	private static void forgetRetry(UUID owner, int id) {
		Map<Integer, Integer> retry = RETRY.get(owner);
		if (retry != null) {
			retry.remove(id);
			if (retry.isEmpty()) {
				RETRY.remove(owner);
			}
		}
	}

	/** The candidate's body leaves (bowing first when {@code bow}); the record is the caller's business. */
	private static void leave(UUID owner, int id, @Nullable ServerLevel level, boolean bow) {
		Map<Integer, DiscipleEntity> views = VIEWS.get(owner);
		DiscipleEntity entity = views == null ? null : views.remove(id);
		if (entity == null || entity.isRemoved()) {
			return;
		}
		BY_ENTITY.remove(entity.getUUID());
		if (bow) {
			entity.setTarget(null);
			LEAVING.add(new Leaving(entity));
		} else {
			vanish(entity);
		}
	}

	private static void vanish(DiscipleEntity entity) {
		if (entity.level() instanceof ServerLevel level && !entity.isRemoved()) {
			Vec3 at = entity.position();
			Fx.send(level, at, FxType.HALL, HallFxIds.MIST, MIST, 0.0F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.NEUTRAL, 0.5F, 1.3F);
		}
		entity.discard();
	}

	/** Every candidate of the owner leaves (dormant hall); the records go too. */
	static void clearHall(UUID owner, CompoundTag core) {
		for (Tag tag : list(core)) {
			if (tag instanceof CompoundTag rec) {
				leave(owner, rec.getIntOr("id", -1), null, true);
			}
		}
		list(core).clear();
		TESTS.removeIf(test -> test.owner.equals(owner));
	}

	/** The hall moved: the bodies vanish and come back near the new stele (records kept, old spots forgotten). */
	static void moveHall(UUID owner, CompoundTag core) {
		for (Tag tag : list(core)) {
			if (tag instanceof CompoundTag rec) {
				leave(owner, rec.getIntOr("id", -1), null, false);
				rec.remove("pos");
			}
		}
		TESTS.removeIf(test -> test.owner.equals(owner));
	}

	/** The body of record {@code id}, spawned near the stele when it has none yet ({@code force}: past the body cap). */
	private static @Nullable DiscipleEntity body(UUID owner, CompoundTag core, CompoundTag rec, boolean force) {
		Map<Integer, DiscipleEntity> views = VIEWS.get(owner);
		DiscipleEntity entity = views == null ? null : views.get(rec.getIntOr("id", -1));
		if (entity != null && !entity.isRemoved() && entity.isAlive()) {
			return entity;
		}
		ServerLevel level = Halls.levelOf(core);
		BlockPos stele = Halls.posOf(core);
		return level == null || stele == null || Halls.dormant(core) ? null : spawnView(owner, rec, level, stele, true, force);
	}

	/** The entity id of the record's body (-1 none). */
	static int entityId(UUID owner, int id) {
		Map<Integer, DiscipleEntity> views = VIEWS.get(owner);
		DiscipleEntity entity = views == null ? null : views.get(id);
		return entity == null || entity.isRemoved() ? -1 : entity.getId();
	}

	// ================================================================== server tick: tests and bows

	static void tickServer(MinecraftServer server) {
		if (!BOWING.isEmpty()) {
			for (Iterator<Bowing> it = BOWING.iterator(); it.hasNext(); ) {
				Bowing bowing = it.next();
				DiscipleEntity entity = bowing.entity;
				if (entity.isRemoved() || --bowing.ticks <= 0) {
					it.remove();
					continue;
				}
				// Down to 45 degrees and back up, then the new disciple follows its master.
				int done = ACCEPT_BOW_TICKS - bowing.ticks;
				entity.getNavigation().stop();
				entity.setXRot(done < ACCEPT_BOW_TICKS / 2 ? done * 3.0F : bowing.ticks * 3.0F);
			}
		}
		if (!LEAVING.isEmpty()) {
			for (Iterator<Leaving> it = LEAVING.iterator(); it.hasNext(); ) {
				Leaving leaving = it.next();
				DiscipleEntity entity = leaving.entity;
				if (entity.isRemoved()) {
					it.remove();
					continue;
				}
				entity.getNavigation().stop();
				entity.setXRot(Math.min(60.0F, (BOW_TICKS - leaving.ticks + 1) * 6.0F));
				if (--leaving.ticks <= 0) {
					it.remove();
					vanish(entity);
				}
			}
		}
		if (TESTS.isEmpty()) {
			return;
		}
		long now = server.getTickCount();
		for (Iterator<Test> it = TESTS.iterator(); it.hasNext(); ) {
			Test test = it.next();
			long t = now - test.start;
			CompoundTag core = Halls.coreOf(test.owner);
			CompoundTag rec = core == null ? null : find(core, test.id);
			if (rec == null) {
				it.remove();
				continue;
			}
			Map<Integer, DiscipleEntity> views = VIEWS.get(test.owner);
			DiscipleEntity entity = views == null ? null : views.get(test.id);
			Vec3 at = entity != null && !entity.isRemoved() ? entity.position() : Vec3.atBottomCenterOf(test.stele);
			ServerLevel level = test.level;
			if (t == 0L) {
				Fx.send(level, at, FxType.HALL, HallFxIds.TEST_TIERS, test.tiers, 3.0F);
				dev.kultivator.epic.EpicHooks.recruit(level, test.owner, dev.kultivator.epic.EpicIds.RECRUIT_TEST, Vec3.atBottomCenterOf(test.stele), dev.kultivator.epic.EpicIds.packRecruitTest(test.duan, test.colour, 0), test.tiers);
			}
			if (t > 0L && t <= TIER_TICKS && t % 20L == 0L) {
				int tier = (int) (t / 20L);
				if (tier <= test.tiers) {
					level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.2F, 0.7F + 0.25F * tier);
				} else {
					level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.NEUTRAL, 0.8F, 0.5F);
				}
			}
			if (t == TIER_TICKS) {
				Vec3 top = Vec3.atBottomCenterOf(test.stele).add(0.0D, 1.0D, 0.0D);
				Fx.send(level, top, at, FxType.HALL, HallFxIds.DUAN, Talents.COLOUR_RGB[test.colour], test.duan);
			}
			if (t > TIER_TICKS && t < TEST_TICKS) {
				for (int segment = 1; segment <= test.duan; segment++) {
					if (t == TIER_TICKS + Math.round(segment * 55.0F / test.duan)) {
						Vec3 top = Vec3.atBottomCenterOf(test.stele).add(0.0D, 1.0D + segment * 0.5D, 0.0D);
						level.playSound(null, top.x, top.y, top.z, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.0F,
								0.6F + 0.12F * segment);
					}
				}
			}
			if (t >= TEST_TICKS) {
				it.remove();
				reveal(server, test, core, rec, entity);
			}
		}
	}

	/** Ujian Masuk: costs one amethyst shard (the one in hand, else any), then the 6 s cinematic. */
	static void startTest(ServerPlayer player, UUID owner, int id, boolean shardInHand) {
		CompoundTag core = Halls.coreOf(owner);
		CompoundTag rec = core == null ? null : find(core, id);
		if (rec == null || Halls.dormant(core)) {
			return;
		}
		DiscipleData data = dataOf(rec);
		if (Talents.tested(data)) {
			Msg.chat(player, Component.translatable("kultivator.hall.cand.already_tested", nameOf(rec)).withStyle(ChatFormatting.GRAY));
			return;
		}
		for (Test test : TESTS) {
			if (test.owner.equals(owner) && test.id == id) {
				return;
			}
		}
		DiscipleEntity entity = body(owner, core, rec, false);
		ServerLevel level = Halls.levelOf(core);
		BlockPos stele = Halls.posOf(core);
		if (entity == null || level == null || stele == null) {
			Msg.chat(player, Component.translatable("kultivator.hall.cand.not_here", nameOf(rec)).withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (!player.hasInfiniteMaterials() && !takeShard(player, shardInHand)) {
			Msg.chat(player, Component.translatable("kultivator.hall.cand.need_shard").withStyle(ChatFormatting.YELLOW));
			return;
		}
		int colour = Talents.soulColor(data);
		TESTS.add(new Test(owner, id, player.getUUID(), level, stele, level.getServer().getTickCount(), tiers(data), Talents.duan(data), colour));
		entity.getNavigation().stop();
		entity.getLookControl().setLookAt(stele.getX() + 0.5D, stele.getY() + 1.0D, stele.getZ() + 0.5D);
		Msg.chat(player, Component.translatable("kultivator.hall.cand.test_start", nameOf(rec)).withStyle(ChatFormatting.AQUA));
	}

	private static boolean takeShard(ServerPlayer player, boolean inHand) {
		ItemStack hand = player.getMainHandItem();
		if (inHand && hand.is(Items.AMETHYST_SHARD)) {
			hand.shrink(1);
			return true;
		}
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(Items.AMETHYST_SHARD)) {
				stack.shrink(1);
				inventory.setChanged();
				return true;
			}
		}
		return false;
	}

	/** The test ends: everything is revealed on the card and in chat. */
	private static void reveal(MinecraftServer server, Test test, CompoundTag core, CompoundTag rec, @Nullable DiscipleEntity entity) {
		DiscipleData data = dataOf(rec);
		Talents.setTested(data, true);
		storeData(rec, data);
		Halls.setDirty();
		if (entity != null && !entity.isRemoved()) {
			Talents.setTested(entity.data(), true);
			entity.setSyncedTalent(Talents.packed(data));
		}
		ServerPlayer tester = server.getPlayerList().getPlayer(test.tester);
		ServerPlayer master = server.getPlayerList().getPlayer(test.owner);
		for (ServerPlayer player : new ServerPlayer[]{tester, master != tester ? master : null}) {
			if (player == null) {
				continue;
			}
			for (Component line : resultLines(rec, data)) {
				Msg.chat(player, line);
			}
			Halls.refreshViewer(player);
		}
	}

	/** The chat result of a test: root, colour and Duan; talents and physique; special; the verdict. */
	private static List<Component> resultLines(CompoundTag rec, DiscipleData data) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("kultivator.hall.cand.result", nameOf(rec), SpiritRoots.describe(data.rootElements(), data.rootGrade()),
				Talents.colourName(Talents.soulColor(data)), Talents.duan(data)).withStyle(ChatFormatting.GOLD));
		int physique = Talents.physique(data);
		lines.add(Component.translatable("kultivator.hall.cand.result_gifts", Talents.talentList(Talents.talents(data)),
				physique > 0 ? Talents.physiqueName(physique) : Component.translatable("kultivator.hall.physique.none")).withStyle(ChatFormatting.AQUA));
		int special = Talents.special(data);
		if (special == Talents.REGRESOR) {
			lines.add(Component.translatable("kultivator.hall.cand.result_regressor", Realm.byOrdinal(Talents.regressorRealm(data)).displayName())
					.withStyle(ChatFormatting.LIGHT_PURPLE));
		} else if (special == Talents.DANTIAN_TERSUMBAT) {
			lines.add(Component.translatable("kultivator.hall.cand.result_special", Talents.specialName(special)).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		if (Talents.genius(data)) {
			lines.add(Component.translatable("kultivator.hall.cand.result_genius").withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		lines.add(verdictLine(rec, data));
		return lines;
	}

	static MutableComponent verdictLine(CompoundTag rec, DiscipleData data) {
		if (rec.getBooleanOr("token", false) || rec.getBooleanOr("champion", false)) {
			return Component.translatable("kultivator.hall.verdict.token").withStyle(ChatFormatting.GOLD);
		}
		return switch (verdict(data)) {
			case VERDICT_PASSED -> Component.translatable("kultivator.hall.verdict.passed").withStyle(ChatFormatting.GREEN);
			case VERDICT_RECORDED -> Component.translatable("kultivator.hall.verdict.recorded").withStyle(ChatFormatting.YELLOW);
			default -> Component.translatable("kultivator.hall.verdict.rejected." + Math.floorMod(rec.getIntOr("id", 0), 4), SERVANT_COST)
					.withStyle(ChatFormatting.RED);
		};
	}

	// ================================================================== acceptance

	/** Terima: the cap, the servant fee, then {@code Disciples.recruitCandidate} at the candidate's spot. */
	static boolean accept(ServerPlayer player, UUID owner, int id) {
		CompoundTag core = Halls.coreOf(owner);
		CompoundTag rec = core == null ? null : find(core, id);
		if (rec == null || !player.getUUID().equals(owner) || Halls.dormant(core)) {
			return false;
		}
		for (Test test : TESTS) {
			if (test.owner.equals(owner) && test.id == id) {
				Msg.chat(player, Component.translatable("kultivator.hall.cand.testing", nameOf(rec)).withStyle(ChatFormatting.YELLOW));
				return false;
			}
		}
		int alive = Disciples.aliveCount(Cultivators.get(player));
		int cap = Disciples.cap(player);
		if (alive >= cap) {
			Msg.chat(player, Component.translatable("kultivator.disciples.full", alive, cap).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		DiscipleData data = dataOf(rec);
		boolean token = rec.getBooleanOr("token", false);
		boolean champion = rec.getBooleanOr("champion", false);
		int verdict = verdict(data);
		boolean servant = !token && !champion && verdict == VERDICT_REJECTED;
		if (servant && core.getIntOr("pool", 0) < SERVANT_COST) {
			Msg.chat(player, Component.translatable("kultivator.hall.cand.servant_short", SERVANT_COST).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		DiscipleEntity entity = body(owner, core, rec, true);
		if (entity == null) {
			Msg.chat(player, Component.translatable("kultivator.hall.cand.not_here", nameOf(rec)).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		Talents.setTested(data, true);
		int rank = token || champion || verdict == VERDICT_PASSED ? SectRanks.LUAR : SectRanks.TERCATAT;
		if (verdict == VERDICT_RECORDED && !token && !champion
				&& (data.rootGrade() == RootGrade.TRASH || data.rootGrade() == RootGrade.LOW)) {
			// Han Li's persistence on a poor root.
			Talents.addTalent(data, Talents.HATI_GIGIH);
		}
		if (champion && data.realm() == Realm.MORTAL) {
			data.setRank(new Rank(Realm.QI_REFINING, 0, 0));
			data.setXp(0.0D);
			data.setQi(DiscipleCultivation.maxQi(data));
		}
		SectRanks.setJoined(data, rank);
		entity.setData(data);
		entity.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		if (!Disciples.recruitCandidate(player, entity)) {
			return false;
		}
		Map<Integer, DiscipleEntity> views = VIEWS.get(owner);
		if (views != null) {
			views.remove(id);
		}
		BY_ENTITY.remove(entity.getUUID());
		remove(core, id);
		forgetRetry(owner, id);
		BOWING.add(new Bowing(entity));
		if (servant) {
			Halls.spendPool(owner, SERVANT_COST);
		}
		if (rec.getBooleanOr("crowned", false)) {
			DisciplePaths.addBond(entity.data(), CHAMPION_BOND, "kultivator.hall.bond.champion");
		}
		Halls.setDirty();
		entity.setSyncedSectRank(rank);
		entity.setSyncedTalent(Talents.packed(entity.data()));
		Halls.noteRank(owner, entity.soulId(), rank, false, DisciplePaths.power(entity.data()));
		if (entity.level() instanceof ServerLevel level) {
			Vec3 at = entity.position();
			Fx.send(level, at, FxType.HALL, HallFxIds.RECRUIT, data.rootColor(), 2.5F);
			dev.kultivator.epic.EpicHooks.recruit(level, owner, dev.kultivator.epic.EpicIds.RECRUIT_ACCEPT, at, data.rootColor(), entity.getId());
			level.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 0.8F, 1.3F);
		}
		Component name = entity.getDisplayName();
		Msg.chat(player, Component.translatable(servant ? "kultivator.hall.cand.joined_servant" : "kultivator.hall.cand.joined", name,
				SectRanks.rankName(rank)).withStyle(ChatFormatting.GOLD));
		Halls.log(owner, Component.translatable("kultivator.hall.log.joined", entity.getName(), SectRanks.rankName(rank)));
		Advancements.grant(player, "first_recruit_hall");
		if (verdict == VERDICT_RECORDED && rank == SectRanks.TERCATAT) {
			Advancements.grant(player, "determined_disciple");
		}
		if (token) {
			ItemStack pill = AlchemyComponents.withQuality(new ItemStack(AlchemyItems.FOUNDATION_PILL), PillQuality.MID);
			Contribution.give(player, pill);
			Msg.chat(player, Component.translatable("kultivator.hall.token.pill").withStyle(ChatFormatting.AQUA));
		}
		return true;
	}

	/** Undang pergi: the candidate bows and leaves. */
	static void dismiss(ServerPlayer player, UUID owner, int id) {
		CompoundTag core = Halls.coreOf(owner);
		CompoundTag rec = core == null ? null : find(core, id);
		if (rec == null || !player.getUUID().equals(owner)) {
			return;
		}
		leave(owner, id, null, true);
		remove(core, id);
		TESTS.removeIf(test -> test.owner.equals(owner) && test.id == id);
		Halls.setDirty();
		Msg.chat(player, Component.translatable("kultivator.hall.cand.dismissed", nameOf(rec)).withStyle(ChatFormatting.GRAY));
	}

	/**
	 * Crowns a candidate body of this hall as a Hundred Dynasties champion (DESIGN 20.3.13; module B normally sends its
	 * champion through {@code Halls.addCandidates} instead): root one grade up, a 50% physique roll, Tekad 100, tested;
	 * recruitable free as Murid Luar at Qi Refining Early with bond 20. False when it is no candidate here.
	 */
	public static boolean crownChampion(UUID owner, DiscipleEntity candidate) {
		Ref ref = candidate == null ? null : BY_ENTITY.get(candidate.getUUID());
		CompoundTag core = Halls.coreOf(owner);
		if (ref == null || !ref.owner().equals(owner) || core == null) {
			return false;
		}
		CompoundTag rec = find(core, ref.id());
		if (rec == null) {
			return false;
		}
		DiscipleData data = dataOf(rec);
		RandomSource random = candidate.getRandom();
		data.setRootGrade(data.rootGrade().up());
		Talents.setSoulColor(data, Math.max(Talents.soulColor(data), Talents.byGrade(data.rootGrade())));
		if (Talents.physique(data) == 0 && random.nextDouble() < 0.5D) {
			Talents.setPhysique(data, Talents.rollPhysique(random));
			if (Talents.physique(data) == Talents.NADI_DEWA_SESAT) {
				data.setRootGrade(RootGrade.TRASH);
				Talents.setSoulColor(data, Talents.MERAH);
			}
		}
		Talents.setTekad(data, 100);
		Talents.setTested(data, true);
		Talents.refreshDuan(data);
		storeData(rec, data);
		rec.putBoolean("champion", true);
		rec.putBoolean("crowned", true);
		candidate.setData(data.copy());
		candidate.setSyncedTalent(Talents.packed(data));
		Halls.setDirty();
		Halls.log(owner, Component.translatable("kultivator.hall.log.champion", nameOf(rec)));
		return true;
	}

	/** A player right-clicked a candidate: an amethyst shard tests it, otherwise the master opens the Calon tablet. */
	public static void onInteract(ServerPlayer player, DiscipleEntity candidate) {
		Ref ref = BY_ENTITY.get(candidate.getUUID());
		if (ref == null) {
			// Not one of the hall's candidates (module B's war youths are CANDIDATE bodies too): leave it alone.
			return;
		}
		if (!player.getUUID().equals(ref.owner())) {
			CompoundTag core = Halls.coreOf(ref.owner());
			Component hall = core == null ? Component.literal("?") : Halls.hallName(core);
			Msg.actionBar(player, Component.translatable("kultivator.hall.cand.other", candidate.getDisplayName(), hall).withStyle(ChatFormatting.GRAY));
			return;
		}
		candidate.getLookControl().setLookAt(player, 30.0F, 30.0F);
		CompoundTag core = Halls.coreOf(ref.owner());
		CompoundTag rec = core == null ? null : find(core, ref.id());
		if (rec != null && player.getMainHandItem().is(Items.AMETHYST_SHARD) && !Talents.tested(dataOf(rec))) {
			startTest(player, ref.owner(), ref.id(), true);
			return;
		}
		Halls.openScreen(player, ref.owner(), Halls.PAGE_CALON);
	}

	// ================================================================== screen info

	/** The candidates for the Calon tablet; hidden values are -1 until tested. */
	static ListTag infoTags(UUID owner, CompoundTag core, long dayTime) {
		ListTag list = new ListTag();
		for (Tag tag : list(core)) {
			if (!(tag instanceof CompoundTag rec)) {
				continue;
			}
			DiscipleData data = dataOf(rec);
			boolean tested = Talents.tested(data);
			int id = rec.getIntOr("id", -1);
			CompoundTag entry = new CompoundTag();
			entry.putInt("id", id);
			entry.putInt("eid", entityId(owner, id));
			rec.read("name", ComponentSerialization.CODEC).ifPresent(name -> entry.store("name", ComponentSerialization.CODEC, name));
			entry.putInt("skin", rec.getIntOr("skin", 0));
			entry.putBoolean("slim", rec.getBooleanOr("slim", false));
			entry.putInt("age", Talents.age(data));
			entry.putInt("tekad", Talents.tekad(data));
			entry.putBoolean("tested", tested);
			boolean testing = false;
			for (Test test : TESTS) {
				testing |= test.owner.equals(owner) && test.id == id;
			}
			entry.putBoolean("testing", testing);
			entry.putInt("grade", tested ? data.rootGrade().ordinal() : -1);
			entry.putInt("elements", tested ? data.rootElements() : 0);
			entry.putInt("colour", tested ? Talents.soulColor(data) : -1);
			entry.putInt("talents", tested ? Talents.talents(data) : 0);
			entry.putInt("physique", tested ? Talents.physique(data) : 0);
			int special = Talents.special(data);
			entry.putInt("special", tested && special != Talents.PENGEMIS ? special : 0);
			entry.putInt("realm_r", tested && special == Talents.REGRESOR ? Talents.regressorRealm(data) : 0);
			entry.putInt("duan", tested ? Talents.duan(data) : 0);
			entry.putInt("tiers", tested ? tiers(data) : -1);
			entry.putInt("verdict", tested ? verdict(data) : -1);
			entry.putBoolean("genius", Talents.genius(data) && tested);
			entry.putBoolean("gated", rec.getBooleanOr("gated", false));
			entry.putBoolean("token", rec.getBooleanOr("token", false));
			entry.putBoolean("war", rec.getBooleanOr("war", false));
			entry.putBoolean("champion", rec.getBooleanOr("champion", false));
			entry.putString("source", rec.getStringOr("source", ""));
			entry.putLong("expires", Math.max(0L, rec.getLongOr("expires_day", 0L) * 24000L - dayTime));
			list.add(entry);
		}
		return list;
	}

	private Candidates() {
	}
}
