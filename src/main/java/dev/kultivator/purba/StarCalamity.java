package dev.kultivator.purba;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.ascension.Stars;
import dev.kultivator.cultivation.ImmortalPerks;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.epic.EpicHooks;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Bencana Bintang, the player's Seven Calamities (DESIGN 26.5.2; Renegade Immortal ch. ~1157-1159, DESIGN 20.3.19): calamity
 * k (1-7) gates star {@code f + 9(k − 1)} with f = 6 / 7 / 8 by the average breakthrough quality (the disciples' rule). A
 * {@link Stars.StarGate} stops condensing before a gated star; the run ({@code bencana mulai}) deducts the star's cost,
 * shows a 6 s omen, then {@code 6 + k} strikes of {@code (0.12 + 0.01 × trial) × max HP} (the last ×2) through
 * {@link Tribulation#strike}, an envoy wave of {@code 2 + k} every 3 strikes. Passing condenses the star; finishing a Trial
 * (k = 2, 4, 7) multiplies T by 1.1 (≤ 1.331), registered through {@code TechniquePower.addPlayerStarFactor}. Failing gives
 * Luka Bencana (Qi Deviation 12,000 ticks) and 50% of the cost back. Calamities already behind an old save's stars are
 * Terlewati (skipped): they never take a star and can be run later for T alone. Segel Penangkal (3 heaven seal shards)
 * makes every strike of one run ×0.85.
 */
final class StarCalamity {

	public static final int COUNT = 7;
	public static final int STARS_PER_STAGE = 9;
	public static final int OMEN_TICKS = 120;
	public static final int STRIKE_INTERVAL = 60;
	public static final double STRIKE_BASE = 0.12D;
	public static final double STRIKE_PER_TRIAL = 0.01D;
	public static final double LAST_STRIKE = 2.0D;
	public static final double SEAL_FACTOR = 0.85D;
	public static final int SEAL_SHARDS = 3;
	public static final double TRIAL_STEP = 1.1D;
	public static final int DEVIATION_TICKS = 12000;
	public static final double REFUND_ON_FAIL = 0.5D;
	public static final int RESULTS = 5;

	/** A live calamity run. */
	static final class Run {
		final UUID player;
		final int k;
		/** True when the run condenses the gated star (false: a skipped calamity taken for T alone). */
		final boolean gate;
		final double cost;
		final boolean seal;
		final int total;
		final ServerBossEvent bar;
		int strikes;
		long next;
		long endAt = -1L;
		boolean failed;

		Run(UUID player, int k, boolean gate, double cost, boolean seal, long now) {
			this.player = player;
			this.k = k;
			this.gate = gate;
			this.cost = cost;
			this.seal = seal;
			this.total = 6 + k;
			this.next = now + OMEN_TICKS;
			this.bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("kultivator.purba.calamity.bar", k, 0, total),
					BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
		}
	}

	private static final Map<UUID, Run> RUNS = new HashMap<>();

	static void init() {
		Stars.addGate(StarCalamity::gate);
		TechniquePower.addPlayerStarFactor(StarCalamity::trialFactor);
	}

	static void clear() {
		for (Run run : RUNS.values()) {
			run.bar.removeAllPlayers();
		}
		RUNS.clear();
	}

	static boolean running(ServerPlayer player) {
		return RUNS.containsKey(player.getUUID());
	}

	static @Nullable Run run(ServerPlayer player) {
		return RUNS.get(player.getUUID());
	}

	// ================================================================== data (pure reads)

	/** The calamity section {@code extra.purba.calamity}. */
	static CompoundTag calamity(CultivationData data) {
		return data.extra().getCompoundOrEmpty(Purba.EXTRA_KEY).getCompoundOrEmpty("calamity");
	}

	/** The first gate: 6 (average quality Normal or lower), 7 (Perfect), 8 (Heavenly), the 20.3.19 rule. */
	static int firstGate(CultivationData data) {
		double quality = data.qualityFactor();
		if (quality >= 1.29D) {
			return 8;
		}
		return quality >= 1.149D ? 7 : 6;
	}

	/** The star calamity {@code k} (1-7) gates. */
	static int gateStar(CultivationData data, int k) {
		return firstGate(data) + STARS_PER_STAGE * (Mth.clamp(k, 1, COUNT) - 1);
	}

	static boolean passed(CultivationData data, int k) {
		return (calamity(data).getIntOr("passed", 0) & (1 << k)) != 0;
	}

	/** Skipped: marked so, or (before the first-load mark) already behind the player's stars. */
	static boolean skipped(CultivationData data, int k) {
		CompoundTag tag = calamity(data);
		if ((tag.getIntOr("skipped", 0) & (1 << k)) != 0) {
			return true;
		}
		return !tag.getBooleanOr("init", false) && gateStar(data, k) <= TechniquePower.playerStars(data);
	}

	/** The lowest calamity neither passed nor skipped, or 0 when all seven are done. */
	static int nextOpen(CultivationData data) {
		for (int k = 1; k <= COUNT; k++) {
			if (!passed(data, k) && !skipped(data, k)) {
				return k;
			}
		}
		return 0;
	}

	/** The calamity that stops the next star now, or 0. */
	static int pending(CultivationData data) {
		int k = nextOpen(data);
		return k > 0 && gateStar(data, k) <= TechniquePower.playerStars(data) + 1 ? k : 0;
	}

	/** The lowest skipped calamity not yet passed (taken for T alone), or 0. */
	static int firstSkipped(CultivationData data) {
		for (int k = 1; k <= COUNT; k++) {
			if (skipped(data, k) && !passed(data, k)) {
				return k;
			}
		}
		return 0;
	}

	/** The T factor stored in {@code extra.purba.t} (1.0-1.331). Pure: read by {@code TechniquePower}. */
	static double trialFactor(CultivationData data) {
		double t = data.extra().getCompoundOrEmpty(Purba.EXTRA_KEY).getDoubleOr("t", 1.0D);
		return Double.isFinite(t) ? Mth.clamp(t, TechniquePower.PLAYER_STAR_FACTOR_MIN, TechniquePower.PLAYER_STAR_FACTOR_MAX) : 1.0D;
	}

	/** The trial of calamity k: 1 for k 1-2, 2 for k 3-4, 3 for k 5-7. */
	static int trialOf(int k) {
		return k <= 2 ? 1 : k <= 4 ? 2 : 3;
	}

	/** Trials whose every calamity is passed. */
	static int trialsDone(CultivationData data) {
		int done = 0;
		if (passed(data, 1) && passed(data, 2)) {
			done++;
		}
		if (passed(data, 3) && passed(data, 4)) {
			done++;
		}
		if (passed(data, 5) && passed(data, 6) && passed(data, 7)) {
			done++;
		}
		return done;
	}

	/** True after at least one calamity was passed or skipped (the Heavens notice the player: Murka Langit). */
	static boolean noticed(CultivationData data) {
		for (int k = 1; k <= COUNT; k++) {
			if (passed(data, k) || skipped(data, k)) {
				return true;
			}
		}
		return false;
	}

	/** On join: calamities behind the player's stars at the first load become Terlewati (never removes a star). */
	static void initFor(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		CompoundTag purba = Purba.section(data);
		CompoundTag tag = purba.getCompoundOrEmpty("calamity");
		if (tag.getBooleanOr("init", false)) {
			return;
		}
		int stars = TechniquePower.playerStars(data);
		int bits = tag.getIntOr("skipped", 0);
		for (int k = 1; k <= COUNT; k++) {
			if (gateStar(data, k) <= stars) {
				bits |= 1 << k;
			}
		}
		tag.putInt("skipped", bits);
		tag.putBoolean("init", true);
		purba.put("calamity", tag);
		Cultivators.dirty(player);
	}

	// ================================================================== the gate

	private static @Nullable Component gate(ServerPlayer player, int nextStar) {
		if (!KultivatorConfig.get().starCalamities) {
			return null;
		}
		CultivationData data = Cultivators.get(player);
		int k = nextOpen(data);
		if (k <= 0 || gateStar(data, k) > nextStar) {
			return null;
		}
		MutableComponent link = Component.translatable("kultivator.purba.calamity.start_link").withStyle(style -> style
				.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent.RunCommand("/kultivasi bencana mulai")));
		return Component.translatable("kultivator.purba.calamity.waits", k, gateStar(data, k), link).withStyle(ChatFormatting.LIGHT_PURPLE);
	}

	// ================================================================== the run

	/** The cost of the next star (with the star cost modifiers, e.g. the Esensi and Batu discounts). */
	static double nextCost(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		return dev.kultivator.disciple.path.Stars.cost(TechniquePower.playerStars(data) + 1) * Stars.costFactor(player);
	}

	/** Why a run of {@code k} (0 = the default choice) cannot start now, or null. */
	static @Nullable Component refusal(ServerPlayer player, int k) {
		if (!KultivatorConfig.get().starCalamities) {
			return Component.translatable("kultivator.purba.calamity.disabled");
		}
		CultivationData data = Cultivators.get(player);
		if (!data.rank().isMax()) {
			return Component.translatable("kultivator.purba.calamity.need_peak");
		}
		if (Purba.busy(player)) {
			return Component.translatable("kultivator.spine9.refuse.busy");
		}
		int chosen = choose(data, k);
		if (chosen <= 0) {
			return Component.translatable(k > 0 ? "kultivator.purba.calamity.not_available" : "kultivator.purba.calamity.none", k);
		}
		boolean gate = chosen == pending(data);
		if (gate && data.storedQi() + 1.0E-6D < nextCost(player)) {
			return Component.translatable("kultivator.purba.calamity.need_qi", Msg.number(nextCost(player)), Msg.number(data.storedQi()));
		}
		return null;
	}

	/** The calamity a run would take: {@code k} when it is the pending gate or a skipped one, else the default. */
	static int choose(CultivationData data, int k) {
		int pending = pending(data);
		if (k > 0) {
			if (k == pending || skipped(data, k) && !passed(data, k)) {
				return k;
			}
			return 0;
		}
		return pending > 0 ? pending : firstSkipped(data);
	}

	/** Starts calamity {@code k} (0 = the pending gate, else the first skipped one). {@code force}: tests skip the cost and refusals. */
	static boolean start(ServerPlayer player, int k, boolean force) {
		if (running(player)) {
			Msg.chat(player, Component.translatable("kultivator.spine9.refuse.busy").withStyle(ChatFormatting.RED));
			return false;
		}
		CultivationData data = Cultivators.get(player);
		int chosen = force ? Mth.clamp(k <= 0 ? Math.max(1, nextOpen(data)) : k, 1, COUNT) : choose(data, k);
		if (!force) {
			Component why = refusal(player, k);
			if (why != null) {
				Msg.chat(player, why.copy().withStyle(ChatFormatting.RED));
				return false;
			}
		}
		boolean gate = chosen == pending(data) && !passed(data, chosen);
		double cost = 0.0D;
		if (gate && !force) {
			cost = nextCost(player);
			data.setStoredQi(Math.max(0.0D, data.storedQi() - cost));
		}
		CompoundTag purba = Purba.section(data);
		boolean seal = false;
		if (purba.getBooleanOr("segel", false) && takeShards(player, SEAL_SHARDS)) {
			seal = true;
			Msg.chat(player, Component.translatable("kultivator.purba.calamity.seal_used", SEAL_SHARDS).withStyle(ChatFormatting.AQUA));
		}
		Cultivators.dirty(player);
		long now = player.level().getGameTime();
		Run run = new Run(player.getUUID(), chosen, gate, cost, seal, now);
		RUNS.put(player.getUUID(), run);
		run.bar.addPlayer(player);
		Fx.send(player.level(), player.position(), FxType.PURBA, PurbaFxIds.WRATH_SKY, 1, 0.0F);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5F, 0.6F);
		Msg.chat(player, Component.translatable(gate ? "kultivator.purba.calamity.begin" : "kultivator.purba.calamity.begin_t", chosen, run.total,
				Component.translatable("kultivator.purba.calamity.trial." + trialOf(chosen))).withStyle(ChatFormatting.LIGHT_PURPLE));
		Msg.chat(player, Component.translatable("kultivator.purba.calamity.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		Purba.sync(player);
		return true;
	}

	/** Takes {@code count} heaven seal shards from the inventory (all or nothing). */
	static boolean takeShards(ServerPlayer player, int count) {
		Inventory inventory = player.getInventory();
		int have = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(PurbaItems.HEAVEN_SEAL_SHARD)) {
				have += stack.getCount();
			}
		}
		if (have < count) {
			return false;
		}
		int left = count;
		for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(PurbaItems.HEAVEN_SEAL_SHARD)) {
				int take = Math.min(left, stack.getCount());
				stack.shrink(take);
				left -= take;
			}
		}
		inventory.setChanged();
		return true;
	}

	static int shards(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		int have = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(PurbaItems.HEAVEN_SEAL_SHARD)) {
				have += stack.getCount();
			}
		}
		return have;
	}

	static void tick(MinecraftServer server) {
		if (RUNS.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Run>> iterator = RUNS.entrySet().iterator();
		while (iterator.hasNext()) {
			Run run = iterator.next().getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(run.player);
			if (player == null) {
				// Gone without the leave event (it normally calls abandon, which returns the cost in full).
				run.bar.removeAllPlayers();
				iterator.remove();
				continue;
			}
			if (run.failed) {
				iterator.remove();
				finish(player, run, false);
				continue;
			}
			step(player, run);
			if (run.endAt >= 0L && player.level().getGameTime() >= run.endAt) {
				iterator.remove();
				finish(player, run, !run.failed);
			}
		}
	}

	private static void step(ServerPlayer player, Run run) {
		long now = player.level().getGameTime();
		if (run.strikes >= run.total || now < run.next) {
			return;
		}
		run.strikes++;
		run.next = now + STRIKE_INTERVAL;
		double share = (STRIKE_BASE + STRIKE_PER_TRIAL * trialOf(run.k)) * (run.strikes == run.total ? LAST_STRIKE : 1.0D) * (run.seal ? SEAL_FACTOR : 1.0D);
		Fx.send(player.level(), player.position(), FxType.PURBA, PurbaFxIds.STRIKE, run.k, run.strikes == run.total ? 2.0F : 1.0F);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.WEATHER, 1.2F,
				run.strikes == run.total ? 0.5F : 0.8F);
		run.bar.setName(Component.translatable("kultivator.purba.calamity.bar", run.k, run.strikes, run.total));
		run.bar.setProgress(Mth.clamp(1.0F - run.strikes / (float) run.total, 0.0F, 1.0F));
		if (Tribulation.strike(player, share)) {
			run.failed = true;
			return;
		}
		if (run.strikes % 3 == 0 && run.strikes < run.total) {
			EnvoyAi.spawnWave(player, 2 + run.k, run.k, cleared -> {
			});
		}
		if (run.strikes >= run.total) {
			run.endAt = now + 40L;
		}
	}

	/** DeathGuard {@code LANGIT}: a lethal blow during the run fails it at 1 HP. */
	static boolean guard(ServerPlayer player) {
		Run run = RUNS.get(player.getUUID());
		if (run == null) {
			return false;
		}
		player.setHealth(1.0F);
		run.failed = true;
		return true;
	}

	/** Pass or fail; the wave envoys of the run leave. */
	static void finish(ServerPlayer player, Run run, boolean pass) {
		run.bar.removeAllPlayers();
		EnvoyAi.dismissFor(player.getUUID());
		Fx.send(player.level(), player.position(), FxType.PURBA, PurbaFxIds.WRATH_SKY, 0, 0.0F);
		CultivationData data = Cultivators.get(player);
		CompoundTag purba = Purba.section(data);
		CompoundTag tag = purba.getCompoundOrEmpty("calamity");
		if (pass) {
			tag.putInt("passed", tag.getIntOr("passed", 0) | (1 << run.k));
			purba.put("calamity", tag);
			double before = trialFactor(data);
			double t = Math.min(TechniquePower.PLAYER_STAR_FACTOR_MAX, Math.pow(TRIAL_STEP, trialsDone(data)));
			purba.putDouble("t", Math.max(before, t));
			Cultivators.dirty(player);
			if (run.gate) {
				int stars = TechniquePower.playerStars(data) + 1;
				ImmortalPerks.setStars(player, stars);
				Stars.condensed(player, stars);
			} else {
				dev.kultivator.cultivation.Stats.apply(player);
			}
			Msg.chat(player, Component.translatable(run.gate ? "kultivator.purba.calamity.pass" : "kultivator.purba.calamity.pass_t", run.k,
					String.format(java.util.Locale.ROOT, "%.3f", trialFactor(data))).withStyle(ChatFormatting.GOLD));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
			if (run.k == 1) {
				Advancements.grant(player, "star_calamity_1");
			}
			if (run.k == 2) {
				Advancements.grant(player, "first_trial");
			}
			if (run.k == COUNT) {
				trampler(player, purba);
			}
		} else {
			// Luka Bencana: Qi Deviation 12,000 ticks (set directly: the ascended's immunity to deviation does not cover a
			// calamity the Heavens send), no star, half the cost back.
			data.setDeviationTicks(Math.max(data.deviationTicks(), DEVIATION_TICKS));
			if (run.cost > 0.0D) {
				data.setStoredQi(data.storedQi() + run.cost * REFUND_ON_FAIL);
			}
			Cultivators.refreshVisual(player);
			Cultivators.syncNow(player);
			Msg.chat(player, Component.translatable("kultivator.purba.calamity.fail", run.k, Msg.number(run.cost * REFUND_ON_FAIL))
					.withStyle(ChatFormatting.DARK_RED));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 0.5F);
		}
		addResult(player, purba, run.k, pass);
		Cultivators.dirty(player);
		Purba.sync(player);
	}

	/** The logged-out end of a run: nothing is lost (the cost goes back in full). */
	static void abandon(ServerPlayer player) {
		Run run = RUNS.remove(player.getUUID());
		if (run == null) {
			return;
		}
		run.bar.removeAllPlayers();
		EnvoyAi.dismissFor(player.getUUID());
		if (run.cost > 0.0D) {
			CultivationData data = Cultivators.get(player);
			data.setStoredQi(data.storedQi() + run.cost);
			Cultivators.dirty(player);
		}
	}

	/** After k = 7: the title Penginjak Langit, a server announcement, the EXTRA beat and the advancement. */
	private static void trampler(ServerPlayer player, CompoundTag purba) {
		purba.putBoolean("title", true);
		Advancements.grant(player, "heaven_trampler_player");
		Msg.broadcast(player.level().getServer(), Component.translatable("kultivator.purba.calamity.trampler_broadcast", player.getDisplayName())
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		EpicHooks.extra(player.level(), EpicIds.EXTRA_PLAYER_TRAMPLING, player, player, player.position(), 0, 0);
	}

	private static void addResult(ServerPlayer player, CompoundTag purba, int k, boolean pass) {
		ListTag list = purba.getListOrEmpty("results").copy();
		CompoundTag entry = new CompoundTag();
		entry.putInt("k", k);
		entry.putBoolean("pass", pass);
		entry.putLong("day", Purba.today(player.level().getServer()));
		list.add(entry);
		while (list.size() > RESULTS) {
			list.remove(0);
		}
		purba.put("results", list);
	}

	// ================================================================== tests

	/** {@code uji bencana <k> lulus|gagal}: the result at once. */
	static void force(ServerPlayer player, int k, boolean pass) {
		Run existing = RUNS.remove(player.getUUID());
		if (existing != null) {
			existing.bar.removeAllPlayers();
		}
		CultivationData data = Cultivators.get(player);
		boolean gate = k == pending(data) && !passed(data, k);
		Run run = new Run(player.getUUID(), Mth.clamp(k, 1, COUNT), gate, 0.0D, false, player.level().getGameTime());
		finish(player, run, pass);
	}

	/** {@code uji bencana lewati <k>}: marks k skipped. */
	static void skip(ServerPlayer player, int k) {
		CultivationData data = Cultivators.get(player);
		CompoundTag purba = Purba.section(data);
		CompoundTag tag = purba.getCompoundOrEmpty("calamity");
		tag.putInt("skipped", tag.getIntOr("skipped", 0) | (1 << Mth.clamp(k, 1, COUNT)));
		tag.putBoolean("init", true);
		purba.put("calamity", tag);
		Cultivators.dirty(player);
		Purba.sync(player);
	}

	/** {@code uji bencana t <x>}. */
	static void setT(ServerPlayer player, double t) {
		CultivationData data = Cultivators.get(player);
		Purba.section(data).putDouble("t", Mth.clamp(t, TechniquePower.PLAYER_STAR_FACTOR_MIN, TechniquePower.PLAYER_STAR_FACTOR_MAX));
		Cultivators.dirty(player);
		dev.kultivator.cultivation.Stats.apply(player);
		Purba.sync(player);
	}

	private StarCalamity() {
	}
}
