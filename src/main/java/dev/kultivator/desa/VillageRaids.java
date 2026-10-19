package dev.kultivator.desa;

import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.musuh.SectRaids;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wave events at a plaque (DESIGN 25.6.2, 25.6.6, 25.6.7): the Malam Penjagaan trust night, the Jaga Malam request and the
 * Penjarahan Desa raids, all as small bands of rogue cultivators ({@link RogueCultivators#spawnBand}, module B) marching on
 * the plaque, with the breach meter of 25.4.5 (rogues within 6 blocks channel 1/2/4/6 per second by tier, at most 10 per
 * second here; 100 fails) shown as a red boss bar. Villagers are never targeted (module B's rules). An absent or idle
 * owner's raid resolves as data: a Pelindung Desa may repel it, otherwise the village is sacked. Source: PW ch. 27 (the
 * rival village's guardian); the waves and numbers are game design. Session state only; nothing changes a block.
 */
public final class VillageRaids {

	public static final int KIND_TRUST = 0;
	public static final int KIND_WATCH = 1;
	public static final int KIND_RAID = 2;
	public static final double CHANNEL_RANGE = 6.0D;
	public static final int METER_CAP_PER_SECOND = 10;
	private static final int[] TIER_RATE = {0, 1, 2, 4, 6};
	/** The owner must stay this close or the event stops (trust night and watch / raid). */
	private static final double KEEP_RANGE = 96.0D;
	private static final double RAID_KEEP_RANGE = 160.0D;
	/** A live raid needs the owner within this range when it begins. */
	public static final double RAID_LIVE_RANGE = 128.0D;
	private static final int WAVE_GAP_TICKS = 100;
	private static final int TIMEOUT_TICKS = 6 * 60 * 20;
	private static final int EMPTY_RETRIES = 6;

	/** One live wave event. */
	static final class Siege {
		final UUID owner;
		final int villageId;
		final int kind;
		final int realm;
		final int[][] waves;
		final long start;
		final ServerBossEvent bar;
		int wave;
		double meter;
		boolean bandAlive;
		long nextWave;
		int empty;
		int serial;

		Siege(UUID owner, int villageId, int kind, int realm, int[][] waves, long start, ServerBossEvent bar) {
			this.owner = owner;
			this.villageId = villageId;
			this.kind = kind;
			this.realm = realm;
			this.waves = waves;
			this.start = start;
			this.bar = bar;
		}
	}

	private static final Map<String, Siege> SIEGES = new HashMap<>();

	private static String key(UUID owner, int id) {
		return owner + ":" + id;
	}

	/** The live wave event at the owner's village {@code id}, or null. */
	static @Nullable Siege siegeAt(UUID owner, int id) {
		return SIEGES.get(key(owner, id));
	}

	/** True while any wave event of the owner runs. */
	static boolean anyFor(UUID owner) {
		for (Siege siege : SIEGES.values()) {
			if (siege.owner.equals(owner)) {
				return true;
			}
		}
		return false;
	}

	// ================================================================== start and stop

	/** Starts a wave event of {@code kind} at the village (false when one already runs there or nothing can come). */
	static boolean start(MinecraftServer server, UUID owner, Village village, int kind) {
		if (SIEGES.containsKey(key(owner, village.id))) {
			return false;
		}
		ServerLevel level = server.overworld();
		int raidLevel = Math.max(1, Math.min(8, SectRaids.raidLevel(owner)));
		int realm = kind == KIND_TRUST ? Math.max(1, raidLevel - 1) : raidLevel;
		int[][] waves = switch (kind) {
			case KIND_TRUST -> new int[][]{{1, 1}, {1, 1}, {1, 1, 1}};
			case KIND_WATCH -> new int[][]{{1, 1}, {1, 1, 2}};
			default -> new int[][]{{1, 1, 1}, {1, 1, 2, 2}};
		};
		ServerBossEvent bar = new ServerBossEvent(Mth.createInsecureUUID(level.getRandom()), Component.empty(), BossEvent.BossBarColor.RED,
				BossEvent.BossBarOverlay.NOTCHED_10);
		Siege siege = new Siege(owner, village.id, kind, realm, waves, level.getGameTime(), bar);
		siege.nextWave = level.getGameTime() + 60L;
		SIEGES.put(key(owner, village.id), siege);
		updateBar(siege, village);
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player != null) {
			bar.addPlayer(player);
			Msg.chat(player, Component.translatable("kultivator.desa.siege.start." + kind, village.name).withStyle(ChatFormatting.RED));
			Fx.sendTo(player, Vec3.atBottomCenterOf(village.plaque), Vec3.atBottomCenterOf(village.plaque), FxType.DESA, DesaFxIds.TOAST, 10 + kind, 0.0F);
		}
		level.playSound(null, village.bell, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.2F, 0.7F);
		Desa.dirty(owner);
		return true;
	}

	/** Stops the event at the village without an outcome (plaque gone, test command). */
	static void stop(UUID owner, int id) {
		Siege siege = SIEGES.remove(key(owner, id));
		if (siege != null) {
			siege.bar.removeAllPlayers();
			Desa.dirty(owner);
		}
	}

	static void clear() {
		for (Siege siege : SIEGES.values()) {
			siege.bar.removeAllPlayers();
		}
		SIEGES.clear();
	}

	// ================================================================== ticking

	/** Every 20 ticks: owner checks, the breach meter, waves, timeout and the bar. */
	static void tick(MinecraftServer server) {
		if (SIEGES.isEmpty()) {
			return;
		}
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		for (Siege siege : new ArrayList<>(SIEGES.values())) {
			Village village = Vassals.find(siege.owner, siege.villageId);
			if (village == null) {
				stop(siege.owner, siege.villageId);
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(siege.owner);
			double keep = siege.kind == KIND_RAID ? RAID_KEEP_RANGE : KEEP_RANGE;
			if (player == null || player.level() != level || player.position().distanceToSqr(Vec3.atCenterOf(village.plaque)) > keep * keep
					|| level.getDifficulty() == Difficulty.PEACEFUL) {
				abort(server, siege, village);
				continue;
			}
			if (!siege.bar.getPlayers().contains(player)) {
				siege.bar.addPlayer(player);
			}
			if (siege.bandAlive) {
				siege.meter = Math.min(100.0D, siege.meter + channel(level, village.plaque));
				if (siege.meter >= 100.0D) {
					finish(server, siege, village, false);
					continue;
				}
			} else if (now >= siege.nextWave) {
				if (siege.wave >= siege.waves.length) {
					finish(server, siege, village, true);
					continue;
				}
				spawnWave(level, siege, village, player);
			}
			if (now - siege.start > TIMEOUT_TICKS) {
				// Held: the rogues retreat; the event counts as survived while the seal is not broken.
				finish(server, siege, village, siege.meter < 100.0D);
				continue;
			}
			updateBar(siege, village);
		}
	}

	private static double channel(ServerLevel level, BlockPos plaque) {
		AABB box = new AABB(plaque).inflate(CHANNEL_RANGE);
		double rate = 0.0D;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, box, mob -> mob.isAlive() && RogueCultivators.isRogue(mob))) {
			int tier = Math.max(0, Math.min(4, RogueCultivators.tier(mob)));
			rate += TIER_RATE[tier == 0 ? 1 : tier];
		}
		return Math.min(METER_CAP_PER_SECOND, rate);
	}

	private static void spawnWave(ServerLevel level, Siege siege, Village village, ServerPlayer player) {
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2.0D;
		double distance = 20.0D + random.nextDouble() * 8.0D;
		Vec3 center = new Vec3(village.plaque.getX() + 0.5D + Math.cos(angle) * distance, village.plaque.getY(),
				village.plaque.getZ() + 0.5D + Math.sin(angle) * distance);
		int serial = ++siege.serial;
		int spawned = RogueCultivators.spawnBand(level, center, siege.waves[siege.wave], siege.realm, RogueCultivators.ROLE_RAID, village.plaque,
				cleared -> onBandGone(siege, serial));
		if (spawned <= 0) {
			siege.empty++;
			siege.nextWave = level.getGameTime() + 40L;
			if (siege.empty >= EMPTY_RETRIES) {
				// Nothing could come (world cap, no room): the wave counts as passed.
				siege.empty = 0;
				siege.wave++;
				Msg.actionBar(player, Component.translatable("kultivator.desa.siege.no_band"));
			}
			return;
		}
		siege.empty = 0;
		siege.wave++;
		siege.bandAlive = true;
		Msg.actionBar(player, Component.translatable("kultivator.desa.siege.wave", siege.wave, siege.waves.length, spawned).withStyle(ChatFormatting.RED));
	}

	private static void onBandGone(Siege siege, int serial) {
		if (SIEGES.get(key(siege.owner, siege.villageId)) != siege || serial != siege.serial) {
			return;
		}
		siege.bandAlive = false;
		MinecraftServer server = Vassals.server();
		siege.nextWave = (server == null ? 0L : server.overworld().getGameTime()) + WAVE_GAP_TICKS;
	}

	private static void updateBar(Siege siege, Village village) {
		siege.bar.setName(Component.translatable("kultivator.desa.siege.bar." + siege.kind, village.name, Math.max(1, siege.wave), siege.waves.length,
				(int) Math.floor(siege.meter)));
		siege.bar.setProgress((float) Math.max(0.0D, Math.min(1.0D, siege.meter / 100.0D)));
	}

	// ================================================================== outcomes

	/** The owner left or went offline: the event ends without a fight result (a raid then resolves as data). */
	private static void abort(MinecraftServer server, Siege siege, Village village) {
		stop(siege.owner, siege.villageId);
		vanish(server.overworld(), village.plaque);
		ServerPlayer player = server.getPlayerList().getPlayer(siege.owner);
		switch (siege.kind) {
			case KIND_TRUST -> {
				if (player != null) {
					Msg.chat(player, Component.translatable("kultivator.desa.trust.left", village.name).withStyle(ChatFormatting.YELLOW));
				}
			}
			case KIND_WATCH -> Requests.fail(server, siege.owner, village, "kultivator.desa.request.failed");
			default -> resolveAbsent(server, siege.owner, village);
		}
	}

	private static void finish(MinecraftServer server, Siege siege, Village village, boolean success) {
		stop(siege.owner, siege.villageId);
		ServerLevel level = server.overworld();
		vanish(level, village.plaque);
		ServerPlayer player = server.getPlayerList().getPlayer(siege.owner);
		switch (siege.kind) {
			case KIND_TRUST -> {
				if (success) {
					Pledge.complete(server, siege.owner, village);
				} else if (player != null) {
					Msg.chat(player, Component.translatable("kultivator.desa.trust.failed", village.name).withStyle(ChatFormatting.RED));
				}
			}
			case KIND_WATCH -> {
				if (success) {
					Requests.complete(server, siege.owner, village, player);
				} else {
					Requests.fail(server, siege.owner, village, "kultivator.desa.request.failed");
				}
			}
			default -> {
				if (success) {
					repelled(server, siege.owner, village, player, true);
				} else {
					sacked(server, siege.owner, village, true);
				}
			}
		}
	}

	/** Rogues still near the plaque leave with a puff when the event ends (they are never saved). */
	private static void vanish(ServerLevel level, BlockPos plaque) {
		AABB box = new AABB(plaque).inflate(48.0D, 24.0D, 48.0D);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, box, RogueCultivators::isRogue)) {
			Fx.burst(level, mob.position().add(0.0D, 1.0D, 0.0D), 0x5A3A5E, 12, 1.0F);
			mob.discard();
		}
	}

	static void repelled(MinecraftServer server, UUID owner, Village village, @Nullable ServerPlayer player, boolean live) {
		long day = Vassals.day();
		village.lastRaid = day;
		village.lastRaidResult = Village.RAID_REPELLED;
		if (live) {
			Desa.addLoyalty(server, owner, village, 10);
			if (player != null) {
				Desa.give(player, Desa.registryStack("loot_pouch", 1));
			}
		}
		boolean guardFought = live ? village.guardPresent() : village.guard != null;
		if (guardFought) {
			Halls.addRenown(owner, 20, "kultivator.desa.renown.raid");
		}
		Vassals.log(owner, village, Village.RAID_REPELLED, live);
		Component line = Component.translatable(live ? "kultivator.desa.raid.repelled" : "kultivator.desa.raid.repelled_guard", village.name,
				village.guardName);
		Halls.log(owner, line);
		if (player != null) {
			Msg.chat(player, line.copy().withStyle(ChatFormatting.GREEN));
		}
		Desa.dirty(owner);
	}

	static void sacked(MinecraftServer server, UUID owner, Village village, boolean live) {
		long day = Vassals.day();
		village.lastRaid = day;
		village.lastRaidResult = Village.RAID_SACKED;
		village.halfTribute = true;
		Desa.addLoyalty(server, owner, village, -10);
		Vassals.log(owner, village, Village.RAID_SACKED, live);
		Component line = Component.translatable("kultivator.desa.raid.sacked", village.name);
		Halls.log(owner, line);
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player != null) {
			Msg.chat(player, line.copy().withStyle(ChatFormatting.RED));
		}
		Desa.dirty(owner);
	}

	/** An absent or idle owner's raid (DESIGN 25.6.7): a Pelindung Desa may repel it, otherwise the village is sacked. */
	static void resolveAbsent(MinecraftServer server, UUID owner, Village village) {
		if (village.guard != null) {
			int raidLevel = Math.max(1, Math.min(8, SectRaids.raidLevel(owner)));
			double chance = Math.max(0.1D, Math.min(0.95D, 0.4D + 0.15D * (village.guardRealm - raidLevel)));
			if (server.overworld().getRandom().nextDouble() < chance) {
				repelled(server, owner, village, server.getPlayerList().getPlayer(owner), false);
				return;
			}
		}
		sacked(server, owner, village, false);
	}

	/**
	 * The raid schedule of one village, checked with the owner's village pass: a due raid becomes live at night with the
	 * owner near and active, and resolves as data once the night is over (or right away after a long absence).
	 */
	static boolean checkRaid(MinecraftServer server, UUID owner, Village village, long day, @Nullable ServerPlayer player) {
		if (!village.pledged || village.lost || day < village.nextRaid || siegeAt(owner, village.id) != null) {
			return false;
		}
		ServerLevel level = server.overworld();
		RandomSource random = level.getRandom();
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			village.nextRaid = day + 7 + random.nextInt(6);
			return true;
		}
		boolean nightNow = Vassals.night();
		if (day == village.nextRaid && nightNow && player != null && player.level() == level && Desa.active(player)
				&& player.position().distanceToSqr(Vec3.atCenterOf(village.plaque)) <= RAID_LIVE_RANGE * RAID_LIVE_RANGE) {
			village.nextRaid = day + 7 + random.nextInt(6);
			start(server, owner, village, KIND_RAID);
			return true;
		}
		if (day > village.nextRaid || Vassals.timeOfDay() >= 23000) {
			village.nextRaid = day + 7 + random.nextInt(6);
			resolveAbsent(server, owner, village);
			return true;
		}
		return false;
	}

	/** Entities a guard must protect against nearby (the plaque's live event), for the disciple death guard. */
	static boolean underSiege(UUID owner, int id) {
		return siegeAt(owner, id) != null;
	}

	/** The ids of the owner's villages under a live event (for the hall card). */
	static List<Integer> live(UUID owner) {
		List<Integer> list = new ArrayList<>();
		for (Siege siege : SIEGES.values()) {
			if (siege.owner.equals(owner)) {
				list.add(siege.villageId);
			}
		}
		return list;
	}

	/** {@code debug}: a short state line. */
	static String describe(UUID owner, int id) {
		Siege siege = siegeAt(owner, id);
		return siege == null ? "-" : "kind=" + siege.kind + " wave=" + siege.wave + "/" + siege.waves.length + " meter=" + (int) siege.meter
				+ " band=" + siege.bandAlive;
	}

	private VillageRaids() {
	}
}
