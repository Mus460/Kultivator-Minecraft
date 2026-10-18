package dev.kultivator.disciple.path;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.cultivation.DaoProtectors;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The calamities of the Bintang Abadi path (DESIGN 20.3.19). Sumber: <i>Renegade Immortal</i>, ch. ~1157-1159: Ancient
 * Gods face Three Trials with Seven Calamities (2 + 2 + 3); failing means death, here an injury (Luka Bencana).
 * <p>
 * Calamity k gates star {@code f + 9(k - 1)} ({@link Stars#gateStar}). The master starts it from the Jalan tab; the
 * star's cost is paid at the start. A 6 s omen, then {@code 6 + k} strikes of {@code (0.12 + 0.01 x trial) x max health},
 * one every 60 ticks, the last doubled; a strike that would kill fails the calamity (strikes never kill). Dao Protector
 * waves come every 3 strikes; Hu Fa applies. The same engine runs the soul-lamp trials ({@link SoulLamps}) with
 * {@code k = 8 + lamps out}. Everything visible is particles (the tribulation clouds and bolts), never a LightningBolt
 * entity, no weather and no block. One calamity per master at a time.
 */
public final class Calamity {

	/** Seven calamities in three Trials (2 + 2 + 3). */
	public static final int COUNT = 7;
	public static final int OMEN_TICKS = 120;
	public static final int STRIKE_INTERVAL = 60;
	private static final int SETTLE_TICKS = 30;
	public static final double BASE_SHARE = 0.12D;
	public static final double SHARE_PER_TRIAL = 0.01D;
	public static final double LAST_STRIKE_FACTOR = 2.0D;
	/** Failure: Luka Bencana, a Qi Deviation of 12,000 ticks, and half the cost back as Stored Qi. */
	public static final int INJURY_TICKS = 12_000;
	public static final double FAIL_REFUND = 0.5D;
	public static final int WAVE_EVERY = 3;
	/** Performance caps for the waves (DESIGN 20.1): protectors alive at once, and sent during one run. */
	public static final int WAVE_ALIVE_CAP = 12;
	public static final int WAVE_RUN_CAP = 40;
	private static final double WAVE_SEARCH = 48.0D;
	public static final double MAX_DRIFT = 6.0D;
	public static final double START_RANGE = 16.0D;
	public static final double MIN_HEALTH = 0.5D;
	public static final int RENOWN = 100;
	private static final double SKY_HEIGHT = 18.0D;

	private enum Phase { OMEN, STRIKES, SETTLING }

	/** A running calamity or lamp trial (session state; a marker in the data survives an unload). */
	static final class Run {
		final DiscipleEntity entity;
		final UUID owner;
		final int k;
		final boolean lamp;
		final double cost;
		final int strikeCount;
		final Vec3 start;
		final ResourceKey<Level> dimension;
		final int variant;
		final DiscipleEntity.Mode previousMode;
		Phase phase = Phase.OMEN;
		int ticks;
		int strikesDone;
		int nextStrikeAt = OMEN_TICKS;
		int protectorsSent;

		Run(DiscipleEntity entity, UUID owner, int k, boolean lamp, double cost, ServerLevel level) {
			this.entity = entity;
			this.owner = owner;
			this.k = k;
			this.lamp = lamp;
			this.cost = cost;
			this.strikeCount = 6 + k;
			this.start = entity.position();
			this.dimension = level.dimension();
			this.variant = Tribulation.variant(level);
			this.previousMode = entity.mode();
		}
	}

	private static final Map<UUID, Run> RUNS = new HashMap<>();

	// ================================================================== rules

	/** The Trial a calamity belongs to: 1 (k 1-2), 2 (k 3-4), 3 (k 5-7 and every lamp). */
	public static int trialOf(int k) {
		return k <= 2 ? 1 : k <= 4 ? 2 : 3;
	}

	/** Finished Trials after {@code passed} calamities (a Trial ends at k = 2, 4 and 7). */
	public static int trialsFor(int passed) {
		return passed >= 7 ? 3 : passed >= 4 ? 2 : passed >= 2 ? 1 : 0;
	}

	/** Share of max health one strike of calamity k takes (before Hu Fa and the last strike's x2). */
	public static double share(int k) {
		return (BASE_SHARE + SHARE_PER_TRIAL * trialOf(k)) * Math.max(0.0D, KultivatorConfig.get().tribulationDamageScale);
	}

	public static boolean running(DiscipleEntity entity) {
		Run run = RUNS.get(entity.soulId());
		return run != null && run.entity == entity;
	}

	/** True while any calamity of {@code owner} runs. */
	static boolean ownerBusy(UUID owner) {
		for (Run run : RUNS.values()) {
			if (run.owner.equals(owner)) {
				return true;
			}
		}
		return false;
	}

	/** The share of max health of the strike being dealt right now by the disciple's calamity (Tahan Petir), or 0. */
	static double currentShare(DiscipleEntity entity) {
		Run run = RUNS.get(entity.soulId());
		if (run == null) {
			return 0.0D;
		}
		boolean last = run.strikesDone >= run.strikeCount;
		return share(run.k) * (last ? LAST_STRIKE_FACTOR : 1.0D);
	}

	/** Why calamity {@code k} cannot start now (a translation key), or null. */
	static @Nullable String blocker(DiscipleEntity entity, @Nullable ServerPlayer master, int k, boolean lamp, boolean debug) {
		DiscipleData data = entity.data();
		String p = DisciplePaths.PREFIX;
		if (!data.rank().isMax()) {
			return p + "why.not_peak";
		}
		if (data.breakingThrough() || running(entity) || DuelRules.inDuel(entity)) {
			return p + "why.busy";
		}
		if (data.deviating()) {
			return p + "why.deviating";
		}
		if (!lamp && !debug && k <= 0) {
			return p + "why.no_calamity";
		}
		if (!lamp && !debug && data.storedQi() + 1.0E-6D < Stars.cost(DisciplePaths.stars(data) + 1)) {
			return p + "why.star_qi";
		}
		if (master == null || !DisciplePaths.near(master, entity, START_RANGE)) {
			return p + "why.master_far";
		}
		if (ownerBusy(master.getUUID())) {
			return p + "why.one_calamity";
		}
		if (entity.getHealth() < entity.getMaxHealth() * MIN_HEALTH) {
			return p + "why.injured";
		}
		return null;
	}

	// ================================================================== start

	/**
	 * Starts the next calamity ({@code debug}: even before its gate, without paying) or a lamp trial. True when it began;
	 * otherwise the master is told why.
	 */
	static boolean start(DiscipleEntity entity, ServerPlayer master, boolean debug, boolean lamp) {
		DiscipleData data = entity.data();
		int k = lamp ? SoulLamps.LAMP_BASE_K + SoulLamps.out(data) : debug ? Math.min(COUNT, DisciplePaths.calamitiesPassed(data) + 1)
				: Stars.pendingCalamity(data);
		if (!lamp && debug && DisciplePaths.calamitiesPassed(data) >= COUNT) {
			k = 0;
		}
		String why = blocker(entity, master, k, lamp, debug);
		if (why == null && !lamp && k <= 0) {
			why = DisciplePaths.PREFIX + "why.no_calamity";
		}
		if (why != null || !(entity.level() instanceof ServerLevel level)) {
			Msg.chat(master, Component.translatable(why == null ? DisciplePaths.PREFIX + "why.busy" : why, DisciplePaths.name(entity))
					.withColor(DisciplePaths.REFUSE));
			return false;
		}
		double cost = lamp || debug ? 0.0D : Stars.cost(DisciplePaths.stars(data) + 1);
		if (cost > 0.0D) {
			data.setStoredQi(data.storedQi() - cost);
		}
		Run run = new Run(entity, master.getUUID(), k, lamp, cost, level);
		RUNS.put(entity.soulId(), run);
		// A marker in the data: an unload, a restart or a death in the middle is settled when the disciple ticks again.
		CompoundTag marker = new CompoundTag();
		marker.putDouble("cost", cost);
		marker.putBoolean("struck", false);
		marker.putBoolean("lamp", lamp);
		// The order to give back if the run is cut off (the session state with run.previousMode is lost then).
		marker.putString("prev_mode", entity.mode().id());
		DisciplePaths.section(data).put("run", marker);
		if (entity.mode() != DiscipleEntity.Mode.MEDITATE) {
			// Sits where it is for the whole calamity (MEDITATE keeps it at its spot and out of fights).
			entity.setMode(DiscipleEntity.Mode.MEDITATE);
		}
		HuFa.begin(entity, null, data.rank(), true);
		sendOmen(level, run, OMEN_TICKS);
		Fx.send(level, entity.position(), FxType.STAR, PathFxIds.CALAMITY_OMEN, k, OMEN_TICKS / 20.0F);
		dev.kultivator.epic.EpicHooks.calamity(entity, master, dev.kultivator.epic.EpicIds.CAL_OMEN, k, 0, run.strikeCount);
		rumble(level, run.start, run.variant, entity.getRandom());
		DisciplePaths.sound(level, entity.position(), SoundEvents.WARDEN_HEARTBEAT, 1.0F, 0.6F);
		Component name = DisciplePaths.name(entity);
		String key = lamp ? "lamp.start" : "calamity.start";
		Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + key, name, lamp ? k - SoulLamps.LAMP_BASE_K + 1 : k, run.strikeCount)
				.withStyle(ChatFormatting.DARK_PURPLE));
		Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "calamity.hint").withColor(DisciplePaths.MUTED));
		DisciplePaths.syncPower(entity);
		return true;
	}

	// ================================================================== tick

	static void tick(MinecraftServer server) {
		if (RUNS.isEmpty()) {
			return;
		}
		for (Run run : List.copyOf(RUNS.values())) {
			DiscipleEntity entity = run.entity;
			if (entity.isRemoved()) {
				// Unloaded, departed or discarded: the marker in its saved data settles the calamity later.
				RUNS.remove(entity.soulId(), run);
				HuFa.end(entity, false);
				continue;
			}
			if (!entity.isAlive() || !(entity.level() instanceof ServerLevel level)) {
				fail(run, null, "calamity.fail_fell");
				continue;
			}
			tickRun(run, level);
		}
	}

	private static void tickRun(Run run, ServerLevel level) {
		DiscipleEntity entity = run.entity;
		run.ticks++;
		holdStill(entity);
		double dx = entity.getX() - run.start.x;
		double dz = entity.getZ() - run.start.z;
		boolean away = !level.dimension().equals(run.dimension) || dx * dx + dz * dz > MAX_DRIFT * MAX_DRIFT;
		if (away) {
			if (run.strikesDone > 0) {
				fail(run, level, "calamity.fail_fled");
			} else {
				cancel(run, level, "calamity.cancel_moved");
			}
			return;
		}
		switch (run.phase) {
			case OMEN -> {
				if (run.ticks % 20 == 0) {
					sendOmen(level, run, Math.max(0, OMEN_TICKS - run.ticks));
					Fx.send(level, entity.position(), FxType.STAR, PathFxIds.CALAMITY_OMEN, run.k, Math.max(0, OMEN_TICKS - run.ticks) / 20.0F);
				}
				if (run.ticks % 40 == 0) {
					rumble(level, run.start, run.variant, entity.getRandom());
				}
				if (run.ticks == OMEN_TICKS - 30) {
					dev.kultivator.epic.EpicHooks.calamity(entity, level.getServer().getPlayerList().getPlayer(run.owner), dev.kultivator.epic.EpicIds.CAL_PRE, run.k, 1, run.strikeCount);
				}
				if (run.ticks >= OMEN_TICKS) {
					run.phase = Phase.STRIKES;
					run.nextStrikeAt = run.ticks;
				}
			}
			case STRIKES -> {
				if (run.nextStrikeAt - run.ticks == 30 && run.strikesDone < run.strikeCount) {
					dev.kultivator.epic.EpicHooks.calamity(entity, level.getServer().getPlayerList().getPlayer(run.owner), dev.kultivator.epic.EpicIds.CAL_PRE, run.k, run.strikesDone + 1, run.strikeCount);
				}
				if (run.ticks >= run.nextStrikeAt) {
					if (!strike(run, level)) {
						return;
					}
					run.nextStrikeAt = run.ticks + STRIKE_INTERVAL;
					if (run.strikesDone >= run.strikeCount) {
						run.phase = Phase.SETTLING;
						run.nextStrikeAt = run.ticks + SETTLE_TICKS;
					}
				} else if (run.nextStrikeAt - run.ticks == STRIKE_INTERVAL / 2) {
					sendOmen(level, run, 0);
					rumble(level, run.start, run.variant, entity.getRandom());
				}
			}
			case SETTLING -> {
				if (run.ticks >= run.nextStrikeAt) {
					pass(run, level);
				}
			}
		}
	}

	/** One strike. False when the calamity ended with it (a strike that would have killed). */
	private static boolean strike(Run run, ServerLevel level) {
		DiscipleEntity entity = run.entity;
		DiscipleData data = entity.data();
		run.strikesDone++;
		int number = run.strikesDone;
		boolean last = number >= run.strikeCount;
		if (number == 1) {
			DisciplePaths.child(DisciplePaths.section(data), "run").putBoolean("struck", true);
		}
		Vec3 feet = entity.position();
		Fx.send(level, feet, feet.add(0.0D, SKY_HEIGHT, 0.0D), FxType.TRIBULATION_STRIKE, run.variant, number, last ? 2.0F : 1.0F);
		DisciplePaths.sound(level, feet.add(0.0D, 3.0D, 0.0D), SoundEvents.LIGHTNING_BOLT_THUNDER, last ? 2.4F : 1.7F, 0.7F);
		DisciplePaths.sound(level, feet, SoundEvents.LIGHTNING_BOLT_IMPACT, last ? 1.3F : 0.9F, 0.55F);
		ServerPlayer master = level.getServer().getPlayerList().getPlayer(run.owner);
		dev.kultivator.epic.EpicHooks.calamity(entity, master, dev.kultivator.epic.EpicIds.CAL_STRIKE, run.k, number, run.strikeCount);
		if (master != null && master.level() == level && master.distanceToSqr(entity) < 64.0D * 64.0D) {
			Msg.actionBar(master, Component.translatable(DisciplePaths.PREFIX + "calamity.strike", number, run.strikeCount, DisciplePaths.name(entity))
					.withStyle(last ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE));
		}
		boolean skipped = DiscipleHooks.strikeSkipped(entity, data.rank(), number);
		double share = share(run.k) * (last ? LAST_STRIKE_FACTOR : 1.0D);
		float amount = skipped ? 0.0F : (float) Math.max(0.0D, entity.getMaxHealth() * share * DiscipleHooks.strikeFactor(entity, data.rank(), number));
		float pool = entity.getHealth() + entity.getAbsorptionAmount();
		boolean wouldKill = !skipped && amount >= pool;
		float dealt = Math.min(amount, pool - 1.0F);
		if (dealt > 0.0F) {
			entity.hurtServer(level, Tribulation.damageSource(level), dealt);
		}
		if (entity.isAlive() && entity.getHealth() < 1.0F) {
			entity.setHealth(1.0F);
		}
		if (wouldKill) {
			fail(run, level, "calamity.fail_strike");
			return false;
		}
		if (!last && number % WAVE_EVERY == 0 && KultivatorConfig.get().daoProtectorEvent) {
			// Laptop budget (DESIGN 20.1): at most WAVE_ALIVE_CAP protectors alive and WAVE_RUN_CAP sent per run.
			int alive = level.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(WAVE_SEARCH),
					mob -> mob.isAlive() && DaoProtectors.isWaveOf(mob, entity)).size();
			int size = Math.min(2 + Math.min(run.k, 12), Math.min(WAVE_ALIVE_CAP - alive, WAVE_RUN_CAP - run.protectorsSent));
			int arrived = size <= 0 ? 0 : DaoProtectors.spawnWaveFor(level, entity, master, data.rank(), size, 1.0D + 0.5D * run.k);
			run.protectorsSent += arrived;
			if (arrived > 0 && master != null) {
				Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "calamity.wave", arrived, DisciplePaths.name(entity))
						.withStyle(ChatFormatting.DARK_RED));
			}
		}
		return true;
	}

	// ================================================================== endings

	private static void pass(Run run, ServerLevel level) {
		DiscipleEntity entity = run.entity;
		DiscipleData data = entity.data();
		finishRun(run, level, true);
		CompoundTag path = DisciplePaths.section(data);
		ServerPlayer master = level.getServer().getPlayerList().getPlayer(run.owner);
		Component name = DisciplePaths.name(entity);
		Vec3 pos = entity.position();
		Fx.send(level, run.start, run.start.add(0.0D, SKY_HEIGHT, 0.0D), FxType.TRIBULATION_END, run.variant, 1, 0.0F);
		Fx.send(level, pos, FxType.STAR, PathFxIds.CALAMITY_PASS, run.k, 1.0F);
		dev.kultivator.epic.EpicHooks.calamity(entity, master, dev.kultivator.epic.EpicIds.CAL_PASS, run.k, run.strikesDone, run.strikeCount);
		DisciplePaths.sound(level, pos, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.2F);
		DisciplePaths.sound(level, pos, SoundEvents.BELL_RESONATE, 1.0F, 0.9F);
		entity.setHealth(entity.getMaxHealth());
		if (run.lamp) {
			SoulLamps.onPassed(entity, master);
			return;
		}
		int passed = Math.min(COUNT, DisciplePaths.calamitiesPassed(data) + 1);
		path.putInt("calamity", passed);
		int before = path.getIntOr("trials", 0);
		int trials = Math.max(before, trialsFor(passed));
		path.putInt("trials", trials);
		int stars = DisciplePaths.stars(data);
		path.putInt("stars", stars + 1);
		UUID owner = entity.ownerId();
		if (owner != null) {
			Halls.addRenown(owner, RENOWN, DisciplePaths.PREFIX + "renown.calamity");
		}
		if (master != null) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "calamity.pass", name, passed).withColor(DisciplePaths.GOLD));
			if (trials > before) {
				Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "calamity.trial_done", name, trials,
						String.format(java.util.Locale.ROOT, "%.3f", Stars.trialFactor(trials))).withStyle(ChatFormatting.LIGHT_PURPLE));
			}
			Advancements.grant(master, "disciple_calamity");
		}
		if (passed >= COUNT) {
			Msg.broadcast(level.getServer(), Component.translatable(DisciplePaths.PREFIX + "trampling.announce", name).withStyle(ChatFormatting.GOLD));
			if (master != null) {
				Advancements.grant(master, "heaven_trampling");
			}
		}
		Stars.afterStarChange(entity, stars, stars + 1, true);
	}

	private static void fail(Run run, @Nullable ServerLevel level, String messageKey) {
		DiscipleEntity entity = run.entity;
		DiscipleData data = entity.data();
		finishRun(run, level, false);
		data.setDeviationTicks(INJURY_TICKS);
		if (run.cost > 0.0D) {
			data.setStoredQi(data.storedQi() + run.cost * FAIL_REFUND);
		}
		if (level != null) {
			Vec3 pos = entity.position();
			Fx.send(level, run.start, run.start.add(0.0D, SKY_HEIGHT, 0.0D), FxType.TRIBULATION_END, run.variant, 0, 0.0F);
			Fx.send(level, pos, FxType.DEVIATION, 0, 0, 1.5F);
			dev.kultivator.epic.EpicHooks.calamity(entity, level.getServer().getPlayerList().getPlayer(run.owner), dev.kultivator.epic.EpicIds.CAL_FAIL, run.k, run.strikesDone, run.strikeCount);
			DisciplePaths.sound(level, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0F, 0.4F);
			DisciplePaths.sound(level, pos, SoundEvents.WITHER_AMBIENT, 0.5F, 1.3F);
		}
		entity.refreshSynced();
		ServerPlayer master = level == null ? null : level.getServer().getPlayerList().getPlayer(run.owner);
		if (master != null) {
			Component name = DisciplePaths.name(entity);
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + messageKey, name).withStyle(ChatFormatting.RED));
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "calamity.injury", name, INJURY_TICKS / 1200).withColor(0xFF8A7A));
		}
	}

	/** Ends without a result (moved before the first strike): the cost comes back in full. */
	private static void cancel(Run run, ServerLevel level, String messageKey) {
		DiscipleEntity entity = run.entity;
		finishRun(run, level, false);
		if (run.cost > 0.0D) {
			entity.data().setStoredQi(entity.data().storedQi() + run.cost);
		}
		Fx.send(level, run.start, run.start.add(0.0D, SKY_HEIGHT, 0.0D), FxType.TRIBULATION_END, run.variant, 0, 0.0F);
		ServerPlayer master = level.getServer().getPlayerList().getPlayer(run.owner);
		dev.kultivator.epic.EpicHooks.calamity(entity, master, dev.kultivator.epic.EpicIds.CAL_ABORT, run.k, run.strikesDone, run.strikeCount);
		if (master != null) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + messageKey, DisciplePaths.name(entity)).withStyle(ChatFormatting.GRAY));
		}
	}

	/** Common ending: the run, its marker, the protectors and the temporary order go. */
	private static void finishRun(Run run, @Nullable ServerLevel level, boolean success) {
		DiscipleEntity entity = run.entity;
		RUNS.remove(entity.soulId(), run);
		DisciplePaths.section(entity.data()).remove("run");
		DaoProtectors.clearFor(entity);
		HuFa.end(entity, success);
		if (entity.isAlive() && !entity.isRemoved() && run.previousMode != DiscipleEntity.Mode.MEDITATE && entity.mode() == DiscipleEntity.Mode.MEDITATE) {
			entity.setMode(run.previousMode);
		}
		DisciplePaths.syncPower(entity);
	}

	/**
	 * A calamity that was cut off by an unload, a restart or a death (its marker is still in the data): before the first
	 * strike the cost comes back, after it the calamity counts as failed.
	 */
	static void resolveStale(DiscipleEntity entity) {
		CompoundTag path = DisciplePaths.tag(entity.data());
		if (!path.contains("run") || running(entity)) {
			return;
		}
		CompoundTag marker = path.getCompoundOrEmpty("run");
		DiscipleData data = entity.data();
		double cost = marker.getDoubleOr("cost", 0.0D);
		boolean struck = marker.getBooleanOr("struck", false);
		DisciplePaths.section(data).remove("run");
		ServerPlayer master = entity.owner();
		if (struck) {
			data.setDeviationTicks(INJURY_TICKS);
			data.setStoredQi(data.storedQi() + cost * FAIL_REFUND);
			if (master != null) {
				Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "calamity.fail_abandoned", DisciplePaths.name(entity))
						.withStyle(ChatFormatting.RED));
			}
		} else {
			data.setStoredQi(data.storedQi() + cost);
		}
		// The temporary MEDITATE order ends with the run.
		DiscipleEntity.Mode prev = DiscipleEntity.Mode.byId(marker.getStringOr("prev_mode", ""));
		if (prev != null && prev != DiscipleEntity.Mode.MEDITATE && entity.mode() == DiscipleEntity.Mode.MEDITATE) {
			entity.setMode(prev);
		}
		entity.refreshSynced();
	}

	static void clear() {
		RUNS.clear();
	}

	// ================================================================== helpers

	private static void holdStill(DiscipleEntity entity) {
		entity.getNavigation().stop();
		entity.setTarget(null);
		Vec3 motion = entity.getDeltaMovement();
		if (motion.x != 0.0D || motion.z != 0.0D || motion.y > 0.0D) {
			entity.setDeltaMovement(0.0D, Math.min(0.0D, motion.y), 0.0D);
		}
	}

	private static void sendOmen(ServerLevel level, Run run, int ticksLeft) {
		Fx.send(level, run.start, run.start.add(0.0D, SKY_HEIGHT, 0.0D), FxType.TRIBULATION_OMEN, run.variant, ticksLeft * 2, 0.0F);
	}

	private static void rumble(ServerLevel level, Vec3 at, int variant, RandomSource random) {
		Vec3 sky = at.add(0.0D, 10.0D, 0.0D);
		DisciplePaths.sound(level, sky, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.3F + random.nextFloat() * 0.1F, 0.45F + random.nextFloat() * 0.15F);
		if (random.nextInt(3) == 0) {
			DisciplePaths.sound(level, sky, variant == Tribulation.VARIANT_END ? SoundEvents.ENDER_DRAGON_GROWL : SoundEvents.BEACON_AMBIENT, 0.3F, 0.5F);
		}
	}

	/** The disciples whose calamity of {@code owner} runs (for the Jalan tab of the master's other disciples). */
	static List<DiscipleEntity> runningFor(UUID owner) {
		List<DiscipleEntity> list = new ArrayList<>();
		for (Run run : RUNS.values()) {
			if (run.owner.equals(owner)) {
				list.add(run.entity);
			}
		}
		return list;
	}

	private Calamity() {
	}
}
