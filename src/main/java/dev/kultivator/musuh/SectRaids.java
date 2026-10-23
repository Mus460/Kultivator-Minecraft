package dev.kultivator.musuh;

import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.cultivation.ImmortalPerks;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.epic.EpicHooks;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.core.CoreView;
import dev.kultivator.formasi.core.Cores;
import dev.kultivator.formasi.fuel.Kas;
import dev.kultivator.formasi.fuel.Upkeep;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formation.Formation;
import dev.kultivator.formation.FormationData;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.build.engine.BuildEngine;
import dev.kultivator.sect.event.Duties;
import dev.kultivator.sect.event.Storehouse;
import dev.kultivator.sect.hall.GrandFormation;
import dev.kultivator.sect.hall.HallData;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.talisman.ReturnTalisman;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.Sibuk;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Serbuan Sekte Iblis, the sect raids (DESIGN 25.4.5, 25.15, owned by P8-musuh). The schedule lives in the hall section
 * {@code raid} (every {@code sectRaidIntervalDays} from hall level 3; the omen at the sunset before, two sunsets with an
 * active K1 Alarm). A raid is fought live from nightfall of the raid day only while the hall is loaded and its owner is in
 * the territory, active within {@code raidAfkSeconds} and not in a tribulation; otherwise it is settled as data at the
 * next sunrise from the defence score. Sleeping is refused in the raided territory on raid night (no time change). Raiders
 * march on the stele and channel the virtual breach meter; the stele block is never touched. A sack takes at most three
 * Lumbung stacks outside {@code #kultivator:raid_safe} and a capped share of the Kas Daya, never blocks, renown or
 * disciples. Disciples never die in a live raid: they are carried off "Terluka" through {@code depart}.
 */
public final class SectRaids {

	/** Told about the stages of every raid of any owner (module D adjusts vassal loyalty). */
	public interface RaidListener {
		/** The omen at sunset: the raid comes on {@code raidDay} (in-game day index). */
		default void onWarn(UUID owner, long raidDay) {
		}

		/** The live raid begins. */
		default void onStart(UUID owner) {
		}

		/** The raid is over (live or resolved as data); {@code repelled} true for a repel. */
		default void onEnd(UUID owner, boolean repelled) {
		}
	}

	/**
	 * Sekte yang Hidup module D (DESIGN 36.2.9): the NPC sect diplomacy names the raiders after a hostile Iblis sect or a
	 * sect at war with the owner, adds its allies to the data defence score and hears every result. Without a director
	 * (or when it answers -1 / null / 0) every raid runs exactly as before; no raid number changes.
	 */
	public interface Director {
		/** The demonic sect index 0-7 (element and loot) of the owner's next omen, or -1 to roll it as before. */
		default int pickSect(UUID owner) {
			return -1;
		}

		/** The raiders' display name for a raid of {@code sect} on {@code owner}, or null to keep {@code LootPouches.sectName}. */
		default @Nullable Component name(UUID owner, int sect) {
			return null;
		}

		/** Extra defence score of allied sects for a raid settled as data (0 without allies). */
		default int allyDefense(UUID owner) {
			return 0;
		}

		/** The result ({@code RES_*}) of every finished raid of {@code owner}. */
		default void onResult(UUID owner, int result) {
		}
	}

	/**
	 * Sekte Langit (DESIGN 37.7.5, module 37G): where the raiders of a hall arrive. Asked at the start of every spawn
	 * point search, before the dry-land search: a sky hall answers a dock or a landing point inside its rim fence (the
	 * dry-land search would find the ground below the island). Null keeps the old search. The meter, waves, timing and
	 * results never change.
	 */
	public interface SpawnPointProvider {
		@Nullable Vec3 spawn(UUID owner, ServerLevel level, int wave, RandomSource random);

		/** Extra repel chance for a raid of {@code owner} settled as data (a sky hall is hard to reach); 0 by default. */
		default double repelBonus(UUID owner) {
			return 0.0D;
		}
	}

	private static final List<SpawnPointProvider> SPAWN_POINTS = new CopyOnWriteArrayList<>();

	/** Adds a raider arrival provider (Sekte Langit); register from a module's {@code init()}. */
	public static void addSpawnPointProvider(SpawnPointProvider provider) {
		if (provider != null) {
			SPAWN_POINTS.add(provider);
		}
	}

	private static final Director NO_DIRECTOR = new Director() {
	};
	private static volatile Director director = NO_DIRECTOR;

	public static final String SECTION = "raid";
	public static final TagKey<Item> RAID_SAFE = TagKey.create(Registries.ITEM, Kultivator.id("raid_safe"));
	public static final int STATE_IDLE = 0;
	public static final int STATE_OMEN = 1;
	/** Days until a raid whose raiders could not arrive anywhere comes again. */
	static final int POSTPONE_DAYS = 2;
	/** Results kept in the raid log. */
	public static final int RES_REPELLED = 0;
	public static final int RES_HELD = 1;
	public static final int RES_SACKED = 2;
	public static final int RES_DATA_REPELLED = 3;
	public static final int RES_DATA_SACKED = 4;
	public static final int LOG_SIZE = 10;
	public static final int MIN_HALL_LEVEL = 3;
	public static final long NIGHTFALL = 13000L;
	public static final long SUNSET = 12000L;
	public static final long SUNRISE = 23000L;
	public static final long TIMEOUT_TICKS = 12000L;
	public static final long NEXT_WAVE_TICKS = 400L;
	public static final long LOW_WAVE_TICKS = 800L;
	public static final long ABSENT_TICKS = 1200L;
	public static final long TRIBULATION_GRACE = 1200L;
	public static final double METER_MAX = 100.0D;
	public static final int PK_PER_WAVE = 50;
	public static final int RENOWN_PER_WAVE = 30;
	public static final int HELPER_PK = 40;
	public static final double HELPER_SHARE = 0.05D;
	public static final int MAX_SACK_STACKS = 3;
	public static final double KAS_SHARE = 0.10D;
	/** Two in-game days of upkeep, in upkeep hours (upkeep is Daya per 72,000 ticks). */
	public static final double KAS_CAP_HOURS = 48000.0D / Upkeep.TICKS_PER_HOUR;
	public static final long HURT_TICKS = 24000L;
	public static final long REST_TICKS = 6000L;
	public static final double HURT_OUTPUT = 0.5D;
	public static final int MAX_DEFENDERS = 12;

	private static final List<RaidListener> LISTENERS = new CopyOnWriteArrayList<>();
	private static final Map<UUID, RaidState> LIVE = new LinkedHashMap<>();
	/** Owner -> game time a tribulation of theirs was last seen running. */
	private static final Map<UUID, Long> TRIBULATION_SEEN = new HashMap<>();

	// ================================================================== public API (DESIGN 25.15)

	/** True while a live raid runs at the owner's hall. */
	public static boolean active(UUID owner) {
		return owner != null && LIVE.containsKey(owner);
	}

	/** The breach meter "Segel Penjarah" 0-100 of the owner's live raid (0 without one). */
	public static int meter(UUID owner) {
		RaidState raid = owner == null ? null : LIVE.get(owner);
		return raid == null ? 0 : (int) Math.round(raid.meter);
	}

	/** The raid level {@code L_r} 1-8 for the owner (DESIGN 25.4.5), plus stars / 9 at True Immortal. */
	public static int raidLevel(UUID owner) {
		MinecraftServer server = Musuh.server();
		ServerPlayer player = server == null || owner == null ? null : server.getPlayerList().getPlayer(owner);
		if (player != null) {
			return computeRaidLevel(owner, player);
		}
		CompoundTag raid = owner == null ? new CompoundTag() : Halls.section(owner, SECTION);
		return Math.max(1, raid.getIntOr("lr", 1));
	}

	/** Adds a raid listener; register from a module's {@code init()}. */
	public static void addListener(RaidListener listener) {
		if (listener != null) {
			LISTENERS.add(listener);
		}
	}

	/** The registered listeners (for the module's own firing code). */
	static List<RaidListener> listeners() {
		return LISTENERS;
	}

	/** Sets the raid director (Sekte yang Hidup module D); null restores the default. */
	public static void setDirector(@Nullable Director value) {
		director = value == null ? NO_DIRECTOR : value;
	}

	/**
	 * Brings the owner's next raid forward to {@code inDays} days from today (a war raid of module D, DESIGN 36.2.9) on the
	 * same schedule path: only while no raid is pending or live, the hall is at least level {@link #MIN_HALL_LEVEL}, raids
	 * are on, and the planned day is later. The omen, the live fight, the data settlement and every number stay the
	 * same. True when the day moved.
	 */
	public static boolean callRaid(MinecraftServer server, UUID owner, int inDays) {
		KultivatorConfig config = KultivatorConfig.get();
		Halls.HallView hall = owner == null ? null : Halls.hallOf(owner);
		if (server == null || hall == null || !config.sectRaids || config.sectRaidIntervalDays <= 0 || LIVE.containsKey(owner)
				|| hall.level() < MIN_HALL_LEVEL) {
			return false;
		}
		CompoundTag raid = Halls.section(owner, SECTION);
		if (raid.getIntOr("state", STATE_IDLE) != STATE_IDLE) {
			return false;
		}
		long today = today(server);
		long wanted = today + Math.max(2, inDays);
		if (raid.contains("next_day") && raid.getLongOr("next_day", wanted) <= wanted) {
			return false;
		}
		if (!raid.contains("l3_day")) {
			raid.putLong("l3_day", today);
		}
		raid.putLong("next_day", wanted);
		Halls.setDirty();
		return true;
	}

	/** The raiders of the owner's live raid right now (a copy; empty without one). */
	public static List<RogueCultivatorEntity> liveRaiders(UUID owner) {
		RaidState raid = owner == null ? null : LIVE.get(owner);
		return raid == null ? List.of() : List.copyOf(raid.alive);
	}

	/** The day index of the owner's next planned raid, or -1 when none is planned. */
	public static long nextRaidDay(UUID owner) {
		CompoundTag raid = owner == null ? new CompoundTag() : Halls.section(owner, SECTION);
		return raid.getLongOr("next_day", -1L);
	}

	/** The raiders' name: the director's (module D) when it names this raid, else the demonic sect's. */
	private static Component raiderName(UUID owner, int sect) {
		Component named = null;
		try {
			named = director.name(owner, sect);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("The raid director failed to name a raid", exception);
		}
		return named != null ? named : LootPouches.sectName(sect);
	}

	// ================================================================== init

	static void init() {
		// Spine providers (DESIGN 25.2 items 13, 25.4.5).
		Halls.addRecruitPauseProvider(SectRaids::active);
		BuildEngine.addPauseProvider(SectRaids::active);
		Duties.addOutputFactorProvider(SectRaids::outputFactor);
		ReturnTalisman.addRefusalProvider(SectRaids::returnRefusal);
		DiscipleHooks.addDeathGuard(SectRaids::guardDisciple);
		Halls.addInfoProvider((viewer, owner, info) -> info.put("musuh", infoTag(owner)));
		EntitySleepEvents.ALLOW_SLEEPING.register(SectRaids::allowSleeping);
		ServerPlayerEvents.LEAVE.register(player -> {
			for (RaidState raid : LIVE.values()) {
				raid.bar.removePlayer(player);
				raid.seal.removePlayer(player);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof RogueCultivatorEntity rogue && source.getEntity() instanceof DiscipleEntity disciple && disciple.ownerId() != null) {
				RaidState raid = LIVE.get(disciple.ownerId());
				if (raid != null && raid.contains(rogue)) {
					raid.discipleHelped = true;
					raid.helpers.add(disciple.getUUID());
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			// A raid cut short by a shutdown stays an omen and is settled as data at the next sunrise.
			for (RaidState raid : LIVE.values()) {
				raid.clearBars();
				for (RogueCultivatorEntity mob : List.copyOf(raid.alive)) {
					mob.discard();
				}
			}
			LIVE.clear();
			TRIBULATION_SEEN.clear();
		});
	}

	// ================================================================== the tick

	/** Once a second: every hall's schedule and every live raid. */
	static void tick(MinecraftServer server, long tick) {
		if (tick % 20L != 0L) {
			return;
		}
		for (RaidState raid : List.copyOf(LIVE.values())) {
			try {
				liveSecond(server, raid);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Sect raid tick failed for {}", raid.owner, exception);
				end(server, raid, RES_HELD, false);
			}
		}
		KultivatorConfig config = KultivatorConfig.get();
		if (!config.sectRaids || config.sectRaidIntervalDays <= 0) {
			return;
		}
		HallData data = HallData.get(server);
		long now = server.overworld().getGameTime();
		for (UUID owner : data.owners()) {
			ServerPlayer player = server.getPlayerList().getPlayer(owner);
			if (player != null && Tribulation.isActive(player)) {
				TRIBULATION_SEEN.put(owner, now);
			}
			if (!LIVE.containsKey(owner)) {
				schedule(server, owner, player, config.sectRaidIntervalDays);
			}
			if (player != null && tick % 600L == 0L) {
				refreshCache(server, owner, player);
			}
		}
	}

	private static long clock(MinecraftServer server) {
		return server.overworld().getOverworldClockTime();
	}

	static long today(MinecraftServer server) {
		return Math.floorDiv(clock(server), 24000L);
	}

	private static long dayTime(MinecraftServer server) {
		return Math.floorMod(clock(server), 24000L);
	}

	private static void schedule(MinecraftServer server, UUID owner, @Nullable ServerPlayer player, int interval) {
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null) {
			return;
		}
		CompoundTag raid = Halls.section(owner, SECTION);
		int state = raid.getIntOr("state", STATE_IDLE);
		if (state == STATE_IDLE && hall.level() < MIN_HALL_LEVEL) {
			return;
		}
		long today = today(server);
		long time = dayTime(server);
		if (!raid.contains("next_day")) {
			raid.putLong("l3_day", today);
			raid.putLong("next_day", today + interval);
			Halls.setDirty();
		}
		long next = raid.getLongOr("next_day", today + interval);
		boolean peaceful = server.overworld().getDifficulty() == Difficulty.PEACEFUL;
		if (state == STATE_IDLE) {
			if (today < next - 2L) {
				return;
			}
			int lead = alarmActive(owner) ? 2 : 1;
			if (today >= next) {
				// Missed while the world was closed: the raid comes the next night, after a proper omen.
				next = today + 1L;
				raid.putLong("next_day", next);
				Halls.setDirty();
			}
			if (today > next - lead || today == next - lead && time >= SUNSET) {
				if (!hall.active() || peaceful) {
					raid.putLong("next_day", next + interval);
					Halls.setDirty();
				} else {
					omen(server, owner, raid, next);
				}
			}
			return;
		}
		boolean night = today == next && time >= NIGHTFALL && time < SUNRISE;
		if (night && !raid.getBooleanOr("interrupted", false)) {
			if (canStartLive(server, owner, hall, player)) {
				startLive(server, owner, hall, 1, false);
			}
		} else if (today > next || today == next && time >= SUNRISE) {
			if (!hall.active() || peaceful) {
				finishSchedule(owner, raid, next, interval);
			} else {
				resolveData(server, owner, false);
			}
		}
	}

	private static boolean alarmActive(UUID owner) {
		for (CoreView core : Cores.of(owner)) {
			if (core.spec().equals("alarm_restriction") && core.state().running()) {
				return true;
			}
		}
		return false;
	}

	private static void omen(MinecraftServer server, UUID owner, CompoundTag raid, long raidDay) {
		RandomSource random = server.overworld().getRandom();
		int picked = -1;
		try {
			picked = director.pickSect(owner);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("The raid director failed to pick a sect", exception);
		}
		int sect = picked >= 0 ? Math.floorMod(picked, RogueCultivatorEntity.SECTS) : random.nextInt(RogueCultivatorEntity.SECTS);
		raid.putInt("sect", sect);
		raid.putInt("state", STATE_OMEN);
		raid.putLong("omen_day", today(server));
		raid.remove("interrupted");
		Halls.setDirty();
		Component line = Component.translatable("kultivator.musuh.raid.omen", raiderName(owner, sect), raidDay);
		Halls.log(owner, line);
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		Halls.HallView hall = Halls.hallOf(owner);
		if (player != null) {
			Msg.chat(player, line.copy().withStyle(ChatFormatting.DARK_RED));
			if (hall != null && player.level().dimension().equals(hall.dim())) {
				double angle = random.nextDouble() * Math.PI * 2.0D;
				Vec3 at = Vec3.atBottomCenterOf(hall.pos()).add(Math.cos(angle) * 48.0D, 6.0D, Math.sin(angle) * 48.0D);
				EpicHooks.extra(player.level(), EpicIds.EXTRA_RAID_OMEN, player, null, at, sect, 0);
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.6F, 0.6F);
			}
		}
		for (RaidListener listener : LISTENERS) {
			try {
				listener.onWarn(owner, raidDay);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("A raid listener failed", exception);
			}
		}
	}

	/** The owner is present in the loaded territory, active and free (DESIGN 25.4.5). */
	private static boolean canStartLive(MinecraftServer server, UUID owner, Halls.HallView hall, @Nullable ServerPlayer player) {
		if (player == null || !hall.active() || player.isSpectator() || !player.isAlive()) {
			return false;
		}
		ServerLevel level = server.getLevel(hall.dim());
		if (level == null || player.level() != level || !level.isPositionEntityTicking(hall.pos())) {
			return false;
		}
		if (level.getDifficulty() == Difficulty.PEACEFUL || !Halls.inTerritory(owner, level, player.position()) || afk(player)) {
			return false;
		}
		Long seen = TRIBULATION_SEEN.get(owner);
		// Never during real danger elsewhere (a duel, a trial, a ritual): the omen waits through the night (Sibuk).
		return !Tribulation.isActive(player) && (seen == null || level.getGameTime() - seen >= TRIBULATION_GRACE)
				&& Sibuk.whyExcept(player, "serbuan") == null;
	}

	private static boolean afk(ServerPlayer player) {
		return Util.getMillis() - player.getLastActionTime() > KultivatorConfig.get().raidAfkSeconds * 1000L;
	}

	// ================================================================== live raids

	/** Starts a live raid now from wave {@code fromWave} (1-based); null when the hall cannot host one. */
	static @Nullable RaidState startLive(MinecraftServer server, UUID owner, Halls.HallView hall, int fromWave, boolean test) {
		ServerLevel level = server.getLevel(hall.dim());
		if (level == null || LIVE.containsKey(owner)) {
			return null;
		}
		CompoundTag section = Halls.section(owner, SECTION);
		RandomSource random = level.getRandom();
		int sect = section.contains("sect") ? section.getIntOr("sect", 0) : random.nextInt(RogueCultivatorEntity.SECTS);
		section.putInt("sect", sect);
		int hallLevel = hall.level();
		int waves = Mth.clamp(2 + hallLevel / 3, 3, 5);
		boolean finalWave = hallLevel >= 6 || section.getIntOr("count", 0) % 3 == 2;
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		int lr = player != null ? computeRaidLevel(owner, player) : Math.max(1, section.getIntOr("lr", 1));
		double first = random.nextDouble() * Math.PI * 2.0D;
		double[] angles = hallLevel >= 6 ? new double[]{first, first + Math.PI * 0.75D} : new double[]{first};
		RaidState raid = new RaidState(owner, level, hall.pos(), Math.max(16.0D, hall.radius()), hallLevel, waves, finalWave, sect, lr, angles, test,
				level.getGameTime());
		raid.wave = Mth.clamp(fromWave, 1, waves) - 1;
		raid.nextWave();
		LIVE.put(owner, raid);
		Halls.setDirty();
		if (!test && player != null) {
			// A raid is an enemy event of the shared daily pressure (it still comes: it was announced days ago).
			Sibuk.enemyTaken(player, "serbuan");
		}
		Component line = Component.translatable("kultivator.musuh.raid.start", raiderName(owner, sect), waves);
		Halls.log(owner, line);
		tellTerritory(raid, line.copy().withStyle(ChatFormatting.DARK_RED));
		Vec3 c = raid.centre();
		level.playSound(null, c.x, c.y, c.z, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 8.0F, 0.8F);
		for (RaidListener listener : LISTENERS) {
			try {
				listener.onStart(owner);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("A raid listener failed", exception);
			}
		}
		return raid;
	}

	private static void liveSecond(MinecraftServer server, RaidState raid) {
		ServerLevel level = raid.level;
		long now = level.getGameTime();
		ServerPlayer owner = server.getPlayerList().getPlayer(raid.owner);
		boolean present = owner != null && owner.level() == level && owner.isAlive() && Halls.inTerritory(raid.owner, level, owner.position())
				&& level.getDifficulty() != Difficulty.PEACEFUL && (raid.test || !afk(owner)) && level.isPositionEntityTicking(raid.stele);
		if (present) {
			raid.absentSince = -1L;
		} else if (raid.absentSince < 0L) {
			raid.absentSince = now;
		} else if (now - raid.absentSince >= ABSENT_TICKS) {
			abort(server, raid);
			return;
		}
		raid.alive.removeIf(mob -> mob.isRemoved() || !mob.isAlive());
		// The breach meter: every channelling raider by the stele adds its share.
		Vec3 centre = raid.centre();
		for (RogueCultivatorEntity mob : raid.alive) {
			if (mob.isChanneling() && mob.position().distanceToSqr(centre) <= (RogueAi.CHANNEL_RANGE + 1.0D) * (RogueAi.CHANNEL_RANGE + 1.0D)) {
				raid.meter = Math.min(METER_MAX, raid.meter + RaidState.METER_RATE[mob.tier()]);
				Fx.send(level, mob.position().add(0.0D, 1.2D, 0.0D), centre.add(0.0D, 1.0D, 0.0D), FxType.MUSUH, MusuhFxIds.SEAL, mob.tier(), 0.0F);
			}
		}
		raid.maxMeter = Math.max(raid.maxMeter, raid.meter);
		if (raid.meter >= METER_MAX) {
			end(server, raid, RES_SACKED, true);
			return;
		}
		// Spawning inside both caps; the rest of the wave waits in the queue.
		KultivatorConfig config = KultivatorConfig.get();
		while (!raid.queue.isEmpty() && raid.alive.size() < config.raidMaxAlive && RogueCultivators.alive() < config.rogueWorldCap) {
			Vec3 at = spawnPoint(server, raid, owner);
			RogueCultivatorEntity mob = at == null ? null : RogueCultivators.spawnRogue(level, at, raid.queue.peek(), raid.raidLevel,
					RogueCultivators.VARIANT_DEMONIC, RogueCultivators.ROLE_RAID, raid.stele, raid.sect);
			if (mob == null) {
				raid.spawnFails++;
				break;
			}
			raid.queue.poll();
			raid.alive.add(mob);
			raid.spawned++;
		}
		if (raid.spawnFails > 30 && !raid.queue.isEmpty()) {
			if (raid.spawned == 0) {
				// Not one raider could arrive (water all around, no solid ground): postpone instead of a free repel.
				postpone(server, raid);
				return;
			}
			// No room to arrive for half a minute: the rest of this wave never comes (and the raid is no longer flawless).
			raid.lost += raid.queue.size();
			raid.queue.clear();
		}
		if (raid.queue.isEmpty() && raid.alive.isEmpty()) {
			if (raid.wave >= raid.waves) {
				end(server, raid, RES_REPELLED, true);
				return;
			}
			if (raid.clearedAt < 0L) {
				raid.clearedAt = now;
				tellTerritory(raid, Component.translatable("kultivator.musuh.raid.wave_cleared", raid.wave, raid.waves).withStyle(ChatFormatting.GOLD));
			} else if (now - raid.clearedAt >= NEXT_WAVE_TICKS) {
				nextWave(raid);
			}
		} else {
			raid.clearedAt = -1L;
			if (raid.queue.isEmpty() && raid.alive.size() <= 2 && raid.wave < raid.waves) {
				if (raid.lowSince < 0L) {
					raid.lowSince = now;
				} else if (now - raid.lowSince >= LOW_WAVE_TICKS) {
					nextWave(raid);
				}
			} else {
				raid.lowSince = -1L;
			}
		}
		if (now - raid.startedAt >= TIMEOUT_TICKS) {
			end(server, raid, RES_HELD, true);
			return;
		}
		updateBars(raid, owner);
	}

	private static void nextWave(RaidState raid) {
		raid.nextWave();
		Vec3 c = raid.centre();
		raid.level.playSound(null, c.x, c.y, c.z, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 6.0F, 1.0F);
		Component line = raid.wave >= raid.waves && raid.finalWave
				? Component.translatable("kultivator.musuh.raid.final_wave", raiderName(raid.owner, raid.sect))
				: Component.translatable("kultivator.musuh.raid.wave", raid.wave, raid.waves);
		tellTerritory(raid, line.copy().withStyle(ChatFormatting.RED));
	}

	/**
	 * A point on the territory edge + 4..8 toward one of the raid's directions, within simulation distance of the owner, on
	 * dry solid ground: water and other fluid spots are skipped (a few nearby angles and closer scales are tried), and an
	 * island or coastal hall falls back to land 8-12 blocks from the stele. Null when there is no such ground loaded.
	 */
	private static @Nullable Vec3 spawnPoint(MinecraftServer server, RaidState raid, @Nullable ServerPlayer owner) {
		ServerLevel level = raid.level;
		RandomSource random = level.getRandom();
		for (SpawnPointProvider provider : SPAWN_POINTS) {
			try {
				Vec3 provided = provider.spawn(raid.owner, level, raid.wave, random);
				if (provided != null) {
					return provided;
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("A raid spawn point provider failed", exception);
			}
		}
		Vec3 centre = raid.centre();
		double angle = raid.angles[raid.spawnIndex++ % raid.angles.length] + (random.nextDouble() - 0.5D) * 0.5D;
		double distance = raid.radius + 4.0D + random.nextDouble() * 4.0D;
		Vec3 point = centre.add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
		if (owner != null && owner.level() == level) {
			double max = Math.max(24.0D, server.getPlayerList().getSimulationDistance() * 16.0D - 16.0D);
			Vec3 flat = new Vec3(point.x - owner.getX(), 0.0D, point.z - owner.getZ());
			if (flat.length() > max) {
				point = owner.position().add(flat.normalize().scale(max));
			}
		}
		Vec3 offset = point.subtract(centre);
		double reach = offset.horizontalDistance();
		double heading = Math.atan2(offset.z, offset.x);
		for (int attempt = 0; attempt < 4; attempt++) {
			Vec3 base = point;
			if (attempt > 0) {
				double turned = heading + (random.nextDouble() - 0.5D) * 1.2D;
				base = centre.add(Math.cos(turned) * reach, 0.0D, Math.sin(turned) * reach);
			}
			for (double scale : new double[]{1.0D, 0.75D, 0.5D}) {
				Vec3 land = dryLand(level, centre.add(base.subtract(centre).multiply(scale, 0.0D, scale)));
				if (land != null) {
					return land;
				}
			}
		}
		// The edge is water (an island or coastal hall): solid land 8-12 blocks from the stele, where the defenders stand.
		for (int attempt = 0; attempt < 16; attempt++) {
			double turned = random.nextDouble() * Math.PI * 2.0D;
			double near = 8.0D + random.nextDouble() * 4.0D;
			Vec3 land = dryLand(level, centre.add(Math.cos(turned) * near, 0.0D, Math.sin(turned) * near));
			if (land != null) {
				return land;
			}
		}
		return null;
	}

	/** The surface at {@code q} when it is entity-ticking, solid and dry (no water, lava or other fluid), else null. */
	private static @Nullable Vec3 dryLand(ServerLevel level, Vec3 q) {
		BlockPos block = BlockPos.containing(q);
		if (!level.isPositionEntityTicking(block)) {
			return null;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, block.getX(), block.getZ());
		BlockPos ground = new BlockPos(block.getX(), y - 1, block.getZ());
		if (!level.getFluidState(ground).isEmpty() || !level.getFluidState(ground.above()).isEmpty()
				|| level.getBlockState(ground).getCollisionShape(level, ground).isEmpty()) {
			return null;
		}
		return new Vec3(q.x, y, q.z);
	}

	/**
	 * Not one raider could arrive: the raid is called off without a result (no rewards, no {@code raid_flawless}, no count)
	 * and comes again in {@link #POSTPONE_DAYS} days, after a new omen.
	 */
	private static void postpone(MinecraftServer server, RaidState raid) {
		LIVE.remove(raid.owner);
		raid.clearBars();
		for (RogueCultivatorEntity mob : List.copyOf(raid.alive)) {
			mob.vanish(false);
		}
		if (!raid.test) {
			CompoundTag section = Halls.section(raid.owner, SECTION);
			section.putInt("state", STATE_IDLE);
			section.putLong("next_day", today(server) + POSTPONE_DAYS);
			section.remove("interrupted");
		}
		Halls.setDirty();
		Component line = Component.translatable("kultivator.sect_fix_feat.raid.postponed", raiderName(raid.owner, raid.sect), POSTPONE_DAYS);
		Halls.log(raid.owner, line);
		tellTerritory(raid, line.copy().withStyle(ChatFormatting.GRAY));
		ServerPlayer player = server.getPlayerList().getPlayer(raid.owner);
		if (player != null && !viewers(raid, player).contains(player)) {
			Msg.chat(player, line.copy().withStyle(ChatFormatting.GRAY));
		}
	}

	private static void updateBars(RaidState raid, @Nullable ServerPlayer owner) {
		raid.bar.setName(Component.translatable("kultivator.musuh.raid.bar", raid.wave, raid.waves, raid.remaining()));
		raid.bar.setProgress(raid.progress());
		raid.seal.setName(Component.translatable("kultivator.musuh.raid.seal_bar", (int) Math.round(raid.meter)));
		raid.seal.setProgress((float) Mth.clamp(raid.meter / METER_MAX, 0.0D, 1.0D));
		List<ServerPlayer> viewers = viewers(raid, owner);
		for (ServerPlayer shown : List.copyOf(raid.bar.getPlayers())) {
			if (!viewers.contains(shown)) {
				raid.bar.removePlayer(shown);
				raid.seal.removePlayer(shown);
			}
		}
		for (ServerPlayer viewer : viewers) {
			raid.bar.addPlayer(viewer);
			raid.seal.addPlayer(viewer);
		}
	}

	/** The owner and every player inside the raided territory. */
	private static List<ServerPlayer> viewers(RaidState raid, @Nullable ServerPlayer owner) {
		List<ServerPlayer> list = new ArrayList<>();
		for (ServerPlayer player : raid.level.players()) {
			if (player == owner || Halls.inTerritory(raid.owner, raid.level, player.position())) {
				list.add(player);
			}
		}
		if (owner != null && !list.contains(owner) && owner.level() == raid.level) {
			list.add(owner);
		}
		return list;
	}

	private static void tellTerritory(RaidState raid, Component message) {
		MinecraftServer server = raid.level.getServer();
		for (ServerPlayer player : viewers(raid, server.getPlayerList().getPlayer(raid.owner))) {
			Msg.chat(player, message);
		}
	}

	/** The owner left (or went idle) for a minute: the raiders withdraw and the raid is settled as data at sunrise. */
	private static void abort(MinecraftServer server, RaidState raid) {
		LIVE.remove(raid.owner);
		raid.clearBars();
		for (RogueCultivatorEntity mob : List.copyOf(raid.alive)) {
			mob.vanish(false);
		}
		CompoundTag section = Halls.section(raid.owner, SECTION);
		section.putBoolean("interrupted", true);
		Halls.setDirty();
		Halls.log(raid.owner, Component.translatable("kultivator.musuh.raid.interrupted"));
		ServerPlayer owner = server.getPlayerList().getPlayer(raid.owner);
		if (owner != null) {
			Msg.chat(owner, Component.translatable("kultivator.musuh.raid.interrupted").withStyle(ChatFormatting.GRAY));
		}
		if (raid.test) {
			resolveData(server, raid.owner, true);
		}
	}

	/** Called for every rogue death (before rewards): counts Tetua and Patriarchs of a live raid. */
	static void onRogueDeath(RogueCultivatorEntity rogue) {
		if (rogue.role() != RogueCultivators.ROLE_RAID) {
			return;
		}
		for (RaidState raid : LIVE.values()) {
			if (raid.contains(rogue)) {
				if (rogue.tier() == 3) {
					raid.elders++;
				} else if (rogue.tier() >= 4) {
					raid.patriarchs++;
				}
				return;
			}
		}
	}

	// ================================================================== outcomes

	/** Ends a live raid with {@code result} (repelled, held or sacked) and pays or takes what it brings. */
	static void end(MinecraftServer server, RaidState raid, int result, boolean announce) {
		if (LIVE.remove(raid.owner) == null) {
			return;
		}
		raid.clearBars();
		for (RogueCultivatorEntity mob : List.copyOf(raid.alive)) {
			mob.vanish(false);
		}
		UUID owner = raid.owner;
		if (result == RES_REPELLED && raid.spawned == 0) {
			// A raid with no raiders is no victory: no pouches, no PK, no raid_flawless; it comes again later.
			postpone(server, raid);
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		Component summary;
		switch (result) {
			case RES_REPELLED -> {
				int pk = PK_PER_WAVE * raid.waves;
				Halls.addPool(owner, pk);
				int pouches = 1 + raid.elders + 2 * raid.patriarchs;
				for (int i = 0; i < pouches; i++) {
					store(server, owner, raid.level, raid.stele, LootPouches.demonic(raid.sect, raid.raidLevel));
				}
				for (int i = 0; i < raid.patriarchs; i++) {
					store(server, owner, raid.level, raid.stele, LootPouches.banner(raid.sect));
				}
				for (DiscipleEntity disciple : Disciples.loaded(owner)) {
					if (raid.helpers.contains(disciple.getUUID())) {
						SectRanks.addPk(disciple.data(), HELPER_PK, "kultivator.musuh.pk.raid");
						DisciplePaths.grantBarShare(disciple.data(), HELPER_SHARE);
					}
				}
				if (raid.discipleHelped) {
					Halls.addRenown(owner, RENOWN_PER_WAVE * raid.waves, "kultivator.musuh.renown.raid");
				}
				if (player != null) {
					Advancements.grant(player, "raid_repelled");
					if (raid.maxMeter <= 0.0D && raid.lost == 0) {
						Advancements.grant(player, "raid_flawless");
					}
				}
				summary = Component.translatable("kultivator.musuh.raid.repelled", raiderName(owner, raid.sect), pk, pouches);
			}
			case RES_SACKED -> summary = sack(server, owner, raid.level, raid.stele, raid.sect);
			default -> {
				int pk = PK_PER_WAVE * raid.waves / 2;
				Halls.addPool(owner, pk);
				summary = Component.translatable("kultivator.musuh.raid.held", raiderName(owner, raid.sect), pk);
			}
		}
		Halls.log(owner, summary);
		if (announce) {
			tellTerritory(raid, summary.copy().withStyle(result == RES_SACKED ? ChatFormatting.DARK_RED : ChatFormatting.GOLD));
			if (player != null && !viewers(raid, player).contains(player)) {
				Msg.chat(player, summary);
			}
		}
		closeRaid(server, owner, result, raid.waves, raid.sect, (int) Math.round(raid.maxMeter), player);
	}

	/**
	 * Settles the owner's pending raid as data (DESIGN 25.4.5): {@code P(repel) = clamp(S / (8 L_r + 10 × waves), 0.10, 0.95)}.
	 * A repel gives half the live rewards (renown only when disciples defended); a failure is a capped sack.
	 */
	static void resolveData(MinecraftServer server, UUID owner, boolean announce) {
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null || LIVE.containsKey(owner)) {
			return;
		}
		CompoundTag section = Halls.section(owner, SECTION);
		ServerLevel level = server.getLevel(hall.dim());
		int waves = Mth.clamp(2 + hall.level() / 3, 3, 5);
		int sect = section.getIntOr("sect", 0);
		int lr = raidLevel(owner);
		int[] score = score(server, owner, level);
		int ally = 0;
		try {
			ally = Math.max(0, director.allyDefense(owner));
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("The raid director failed to count allies", exception);
		}
		int total = Math.max(score[0], section.getIntOr("score", 0)) + ally;
		int disciples = Math.max(score[1], section.getIntOr("score_d", 0));
		double chance = repelChance(total, lr, waves);
		double bonus = 0.0D;
		for (SpawnPointProvider provider : SPAWN_POINTS) {
			try {
				bonus += Math.max(0.0D, provider.repelBonus(owner));
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("A raid spawn point provider failed", exception);
			}
		}
		chance = Mth.clamp(chance + bonus, 0.10D, 0.95D);
		boolean repelled = level != null && level.getRandom().nextDouble() < chance;
		Component summary;
		int result;
		if (repelled) {
			int pk = PK_PER_WAVE * waves / 2;
			Halls.addPool(owner, pk);
			store(server, owner, level, hall.pos(), LootPouches.demonic(sect, lr));
			if (disciples > 0) {
				Halls.addRenown(owner, RENOWN_PER_WAVE * waves / 2, "kultivator.musuh.renown.raid");
			}
			summary = Component.translatable("kultivator.musuh.raid.data_repelled", raiderName(owner, sect), Math.round(chance * 100.0D), pk);
			result = RES_DATA_REPELLED;
		} else {
			Component sack = sack(server, owner, level, hall.pos(), sect);
			summary = Component.translatable("kultivator.musuh.raid.data_sacked", raiderName(owner, sect), Math.round(chance * 100.0D)).append(" ").append(sack);
			result = RES_DATA_SACKED;
		}
		Halls.log(owner, summary);
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player != null) {
			Msg.chat(player, summary.copy().withStyle(repelled ? ChatFormatting.GOLD : ChatFormatting.DARK_RED));
		}
		closeRaid(server, owner, result, waves, sect, 0, player);
	}

	static double repelChance(int score, int raidLevel, int waves) {
		return Mth.clamp(score / (8.0D * Math.max(1, raidLevel) + 10.0D * waves), 0.10D, 0.95D);
	}

	/** Writes the result, schedules the next raid and tells the listeners. */
	private static void closeRaid(MinecraftServer server, UUID owner, int result, int waves, int sect, int meter, @Nullable ServerPlayer player) {
		CompoundTag section = Halls.section(owner, SECTION);
		long day = section.getLongOr("next_day", today(server));
		ListTag log = section.getList("log").orElse(null);
		if (log == null) {
			log = new ListTag();
			section.put("log", log);
		}
		CompoundTag entry = new CompoundTag();
		entry.putLong("day", day);
		entry.putInt("res", result);
		entry.putInt("waves", waves);
		entry.putInt("sect", sect);
		entry.putInt("meter", meter);
		log.add(entry);
		while (log.size() > LOG_SIZE) {
			log.remove(0);
		}
		section.putInt("count", section.getIntOr("count", 0) + 1);
		finishSchedule(owner, section, day, Math.max(5, KultivatorConfig.get().sectRaidIntervalDays));
		boolean repelled = result == RES_REPELLED || result == RES_DATA_REPELLED;
		if (player != null) {
			Musuh.logEncounter(player, Musuh.KIND_RAID, repelled, waves);
		}
		for (RaidListener listener : LISTENERS) {
			try {
				listener.onEnd(owner, repelled);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("A raid listener failed", exception);
			}
		}
		try {
			director.onResult(owner, result);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("The raid director failed on a result", exception);
		}
	}

	private static void finishSchedule(UUID owner, CompoundTag section, long raidDay, int interval) {
		section.putInt("state", STATE_IDLE);
		section.putLong("next_day", raidDay + interval);
		section.remove("interrupted");
		Halls.setDirty();
	}

	/**
	 * Dijarah: at most three random Lumbung stacks outside {@code #raid_safe}, 10% of the Kas Daya capped at two in-game days
	 * of the hall's upkeep, and duty output ×0.5 for a day ("Aula Terluka"). Never blocks, renown or disciples' data.
	 */
	private static Component sack(MinecraftServer server, UUID owner, @Nullable ServerLevel level, BlockPos stele, int sect) {
		RandomSource random = server.overworld().getRandom();
		int[] chosen = {0};
		List<ItemStack> stolen = Storehouse.take(owner, stack -> {
			if (chosen[0] >= MAX_SACK_STACKS || raidSafe(stack) || random.nextDouble() >= 0.45D) {
				return false;
			}
			chosen[0]++;
			return true;
		}, Integer.MAX_VALUE);
		long total = Kas.amethyst(owner) + Kas.stone(owner);
		long loss = Math.min(Math.round(total * KAS_SHARE), upkeepCap(owner));
		long taken = 0L;
		if (loss > 0L) {
			taken = Kas.take(owner, loss, true);
			if (taken < loss) {
				taken += Kas.take(owner, loss - taken, false);
			}
		}
		CompoundTag section = Halls.section(owner, SECTION);
		section.putLong("hurt_until", server.overworld().getGameTime() + HURT_TICKS);
		Halls.setDirty();
		return Component.translatable("kultivator.musuh.raid.sacked", raiderName(owner, sect), stolen.size(), taken / 100L);
	}

	/** Items a sack never takes: the tag, containers (rings, shulkers), artifacts and trophies. */
	static boolean raidSafe(ItemStack stack) {
		return stack.is(RAID_SAFE) || stack.has(DataComponents.CONTAINER) || stack.getItem() instanceof dev.kultivator.tempa.ArtifactItem
				|| stack.getItem() instanceof dev.kultivator.tempa.StorageRingItem || stack.is(LootPouches.DEMONIC_BANNER);
	}

	/** Two in-game days of upkeep of the owner's running cores, in centi-Daya. */
	private static long upkeepCap(UUID owner) {
		long cap = 0L;
		for (CoreView core : Cores.of(owner)) {
			if (!core.state().running()) {
				continue;
			}
			FormationSpec spec = FormationRegistry.byId(core.spec());
			if (spec != null) {
				cap += Upkeep.hoursCenti(spec, KAS_CAP_HOURS);
			}
		}
		return cap;
	}

	/** Into the Lumbung; what does not fit goes to the online owner or drops by the stele. */
	private static void store(MinecraftServer server, UUID owner, @Nullable ServerLevel level, BlockPos stele, ItemStack stack) {
		ItemStack rest = Storehouse.insert(owner, stack);
		if (rest.isEmpty()) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player != null) {
			LootPouches.give(player, rest);
		} else if (level != null && level.isLoaded(stele)) {
			ItemEntity item = new ItemEntity(level, stele.getX() + 0.5D, stele.getY() + 1.2D, stele.getZ() + 0.5D, rest);
			item.setDefaultPickUpDelay();
			item.addTag(NoGrief.TAG);
			level.addFreshEntity(item);
		}
	}

	// ================================================================== defence score and raid level

	/**
	 * {@code S = Σ(home and GUARD_HALL disciples, ≤ 12)(realm + 1) + 2 × Σ running formation grades + 6 × Grand Formation
	 * layer}; returns {S, the disciples' part}.
	 */
	static int[] score(MinecraftServer server, UUID owner, @Nullable ServerLevel level) {
		int disciples = 0;
		int counted = 0;
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			if (counted >= MAX_DEFENDERS) {
				break;
			}
			if (disciple.role() == DiscipleEntity.Role.DISCIPLE && (disciple.mode() == DiscipleEntity.Mode.GUARD_HALL
					|| Halls.inTerritory(owner, disciple.level(), disciple.position()))) {
				disciples += disciple.data().realm().ordinal() + 1;
				counted++;
			}
		}
		int formations = 0;
		for (CoreView core : Cores.of(owner)) {
			if (core.state().running()) {
				FormationSpec spec = FormationRegistry.byId(core.spec());
				if (spec != null) {
					formations += spec.grade().ordinal() + 1;
				}
			}
		}
		int grand = 0;
		if (level != null) {
			for (Formation formation : FormationData.get(level).all()) {
				if (formation.owner().equals(owner) && formation.active()) {
					GrandFormation.State state = GrandFormation.state(level, formation, server.getTickCount());
					if (state.grand()) {
						grand = Math.max(grand, state.layers());
					}
				}
			}
		}
		return new int[]{disciples + 2 * formations + 6 * grand, disciples};
	}

	/** {@code clamp(max(owner realm, strongest disciple realm) − 1, 1, 8)}, plus stars / 9 at True Immortal. */
	private static int computeRaidLevel(UUID owner, ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		int best = data.realm().ordinal();
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			best = Math.max(best, disciple.data().realm().ordinal());
		}
		int level = Mth.clamp(best - 1, 1, 8);
		if (data.realm() == Realm.TRUE_IMMORTAL) {
			level += ImmortalPerks.stars(data) / 9;
		}
		return level;
	}

	/** Every 30 s while the owner is online: the raid level and defence score used when the raid is settled as data. */
	private static void refreshCache(MinecraftServer server, UUID owner, ServerPlayer player) {
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null) {
			return;
		}
		CompoundTag section = Halls.section(owner, SECTION);
		section.putInt("lr", computeRaidLevel(owner, player));
		ServerLevel level = server.getLevel(hall.dim());
		if (level != null && level.isLoaded(hall.pos())) {
			int[] score = score(server, owner, level);
			section.putInt("score", score[0]);
			section.putInt("score_d", score[1]);
		}
		Halls.setDirty();
	}

	// ================================================================== providers

	private static double outputFactor(UUID owner) {
		MinecraftServer server = Musuh.server();
		if (server == null) {
			return 1.0D;
		}
		long until = Halls.section(owner, SECTION).getLongOr("hurt_until", 0L);
		return server.overworld().getGameTime() < until ? HURT_OUTPUT : 1.0D;
	}

	private static @Nullable Component returnRefusal(ServerPlayer player) {
		for (RaidState raid : LIVE.values()) {
			if (Halls.inTerritory(raid.owner, player.level(), player.position())) {
				return Component.translatable("kultivator.musuh.raid.no_return");
			}
		}
		return null;
	}

	/** No disciple dies in a live raid: left at 1 HP, carried off "Terluka" to rest (DESIGN 25.4.5). */
	private static boolean guardDisciple(DiscipleEntity disciple, net.minecraft.world.damagesource.DamageSource source) {
		UUID owner = disciple.ownerId();
		RaidState raid = owner == null ? null : LIVE.get(owner);
		if (raid == null || !Halls.inTerritory(owner, disciple.level(), disciple.position())) {
			return false;
		}
		disciple.setHealth(1.0F);
		Component name = disciple.getDisplayName();
		Halls.storeAway(owner, disciple, raid.level.getGameTime() + REST_TICKS, "musuh_terluka", new CompoundTag());
		ServerPlayer master = disciple.level().getServer().getPlayerList().getPlayer(owner);
		if (master != null) {
			Msg.chat(master, Component.translatable("kultivator.musuh.raid.injured", name).withStyle(ChatFormatting.GOLD));
		}
		return true;
	}

	private static @Nullable Player.BedSleepingProblem allowSleeping(Player player, BlockPos pos) {
		MinecraftServer server = player.level().getServer();
		if (server == null || !(player instanceof ServerPlayer)) {
			return null;
		}
		Vec3 at = Vec3.atCenterOf(pos);
		for (RaidState raid : LIVE.values()) {
			if (Halls.inTerritory(raid.owner, player.level(), at)) {
				return new Player.BedSleepingProblem(Component.translatable("kultivator.musuh.raid.no_sleep"));
			}
		}
		long today = today(server);
		long time = dayTime(server);
		for (UUID owner : HallData.get(server).owners()) {
			CompoundTag section = Halls.section(owner, SECTION);
			if (section.getIntOr("state", STATE_IDLE) == STATE_OMEN && section.getLongOr("next_day", -1L) == today && time >= SUNSET
					&& Halls.inTerritory(owner, player.level(), at)) {
				return new Player.BedSleepingProblem(Component.translatable("kultivator.musuh.raid.no_sleep"));
			}
		}
		return null;
	}

	// ================================================================== info for the screens

	/** The Pertahanan tablet's data (also the Musuh tablet's schedule line). */
	static CompoundTag infoTag(UUID owner) {
		CompoundTag tag = new CompoundTag();
		MinecraftServer server = Musuh.server();
		KultivatorConfig config = KultivatorConfig.get();
		Halls.HallView hall = Halls.hallOf(owner);
		tag.putBoolean("enabled", config.sectRaids && config.sectRaidIntervalDays > 0);
		tag.putInt("interval", config.sectRaidIntervalDays);
		tag.putBoolean("has_hall", hall != null);
		tag.putInt("hall_level", hall == null ? 0 : hall.level());
		tag.putInt("min_level", MIN_HALL_LEVEL);
		if (server == null || hall == null) {
			return tag;
		}
		CompoundTag section = Halls.section(owner, SECTION);
		tag.putLong("today", today(server));
		tag.putLong("next_day", section.getLongOr("next_day", -1L));
		tag.putInt("state", section.getIntOr("state", STATE_IDLE));
		tag.putInt("sect", section.getIntOr("sect", -1));
		tag.putInt("count", section.getIntOr("count", 0));
		int waves = Mth.clamp(2 + hall.level() / 3, 3, 5);
		int lr = Math.max(1, section.getIntOr("lr", 1));
		int score = section.getIntOr("score", 0);
		tag.putInt("waves", waves);
		tag.putInt("lr", lr);
		tag.putInt("score", score);
		tag.putInt("chance", (int) Math.round(repelChance(score, lr, waves) * 100.0D));
		tag.putBoolean("alarm", alarmActive(owner));
		tag.putBoolean("hurt", outputFactor(owner) < 1.0D);
		RaidState raid = LIVE.get(owner);
		if (raid != null) {
			CompoundTag live = new CompoundTag();
			live.putInt("wave", raid.wave);
			live.putInt("waves", raid.waves);
			live.putInt("meter", (int) Math.round(raid.meter));
			live.putInt("left", raid.remaining());
			tag.put("live", live);
		}
		tag.put("log", section.getListOrEmpty("log").copy());
		return tag;
	}

	// ================================================================== tests (MusuhTests)

	static @Nullable RaidState live(UUID owner) {
		return LIVE.get(owner);
	}

	/** {@code uji serbuan peringatan}: the raid comes tonight (or tomorrow night after sunset), with its omen now. */
	static boolean testOmen(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		UUID owner = player.getUUID();
		if (Halls.hallOf(owner) == null || LIVE.containsKey(owner)) {
			return false;
		}
		CompoundTag section = Halls.section(owner, SECTION);
		long today = today(server);
		long raidDay = dayTime(server) < SUNSET ? today : today + 1L;
		section.putLong("next_day", raidDay);
		omen(server, owner, section, raidDay);
		return true;
	}

	/** {@code uji serbuan mulai [gelombang]}: a live raid now (owner checks except time and idleness). */
	static boolean testStart(ServerPlayer player, int wave) {
		MinecraftServer server = player.level().getServer();
		Halls.HallView hall = Halls.hallOf(player.getUUID());
		if (hall == null || LIVE.containsKey(player.getUUID())) {
			return false;
		}
		CompoundTag section = Halls.section(player.getUUID(), SECTION);
		if (section.getIntOr("state", STATE_IDLE) != STATE_OMEN) {
			section.putInt("state", STATE_OMEN);
			section.putLong("next_day", today(server));
			section.putInt("sect", player.getRandom().nextInt(RogueCultivatorEntity.SECTS));
		}
		return startLive(server, player.getUUID(), hall, wave, true) != null;
	}

	/** {@code uji serbuan menang|kalah}: forces the live raid's outcome (or settles a fresh one). */
	static boolean testForce(ServerPlayer player, boolean win) {
		MinecraftServer server = player.level().getServer();
		RaidState raid = LIVE.get(player.getUUID());
		if (raid == null) {
			if (!testStart(player, 1)) {
				return false;
			}
			raid = LIVE.get(player.getUUID());
			if (raid == null) {
				return false;
			}
		}
		if (win) {
			raid.wave = raid.waves;
		}
		end(server, raid, win ? RES_REPELLED : RES_SACKED, true);
		return true;
	}

	/** {@code uji serbuan segel <n>}: sets the live breach meter. */
	static boolean testMeter(ServerPlayer player, int value) {
		RaidState raid = LIVE.get(player.getUUID());
		if (raid == null) {
			return false;
		}
		raid.meter = Mth.clamp(value, 0, 100);
		raid.maxMeter = Math.max(raid.maxMeter, raid.meter);
		return true;
	}

	/** {@code uji serbuan data}: settles a raid as data now. */
	static boolean testData(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		UUID owner = player.getUUID();
		if (Halls.hallOf(owner) == null || LIVE.containsKey(owner)) {
			return false;
		}
		CompoundTag section = Halls.section(owner, SECTION);
		if (!section.contains("sect")) {
			section.putInt("sect", player.getRandom().nextInt(RogueCultivatorEntity.SECTS));
		}
		section.putLong("next_day", today(server));
		resolveData(server, owner, true);
		return true;
	}

	/** Raiders of live raids (the {@code debug} line). */
	static int liveCount() {
		return LIVE.size();
	}

	private SectRaids() {
	}
}
