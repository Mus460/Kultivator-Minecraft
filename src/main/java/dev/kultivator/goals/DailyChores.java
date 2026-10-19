package dev.kultivator.goals;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.data.XpSource;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Kewajiban Harian / Daily Chores (DESIGN 19.3.9 c, config {@code dailyTasks}, help {@code harian}): three of the
 * player's own chores per in-game day (overworld game time / 24,000); a refresh also needs 18,000 ticks played since
 * the last one, so sleeping cannot farm them. Each chore gives 4% of the current bar, all three one Low spirit stone, and
 * a streak of consecutive full days adds 10% per day up to 50% (a missed day resets it). Saved in
 * {@code extra.goals.daily {day, n, last, chores [{id, need, have, base}], done, streak, last_full, full}}.
 * <p>
 * Sumber: RI ch. 10 (as a servant disciple Wang Lin must fill ten vats of water a day). The streak is game design.
 * <p>
 * Phase 8 (DESIGN 25.8.3) adds four world chores: slay 2 rogue cultivators, finish 1 village request, forge or refine
 * once, clear a secret realm; each joins the pool once its feature was met (an advancement or a vassal).
 * <p>
 * Phase 9 (DESIGN 26.9, P9-batin) adds four Immortal chores: slay 3 Alam Abadi natives, mine 1 Batu Asal Abadi, harvest an
 * inner-world plot, bid at an Immortal auction; each joins the pool once the player ascended (or has an inner world).
 */
public final class DailyChores {

	public static final String KEY = "daily";
	public static final int COUNT = 3;
	public static final long DAY_TICKS = 24000L;
	/** Ticks played since the last refresh before a new set may come. */
	public static final int MIN_PLAYED = 18000;
	public static final double REWARD_SHARE = 0.04D;
	public static final double STREAK_STEP = 0.10D;
	public static final int STREAK_MAX = 5;
	public static final int STREAK_ADVANCEMENT = 5;

	/** The pool; chores whose feature is unavailable are never rolled. */
	public enum Chore {
		KILLS, ELITE, MED_LONG, MED_SITE, TECH, REFINE, ORES, HERBS, THUNDER, TALISMAN,
		/** Phase 8 (DESIGN 25.8.3). */
		ROGUES, VILLAGE, FORGE, CHAMBER,
		/** Phase 9 (DESIGN 26.9). */
		NATIVES, ORIGIN, PLOT, BID;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** Stored in seconds and shown in minutes. */
		public boolean seconds() {
			return this == MED_LONG || this == MED_SITE;
		}

		public static @Nullable Chore byId(String id) {
			for (Chore chore : values()) {
				if (chore.id().equals(id)) {
					return chore;
				}
			}
			return null;
		}
	}

	/** How much a chore asks at the player's realm (seconds for the meditation chores). */
	public static int need(Chore chore, CultivationData data) {
		return switch (chore) {
			case KILLS -> 8 + 2 * data.realm().ordinal();
			case ELITE, THUNDER, VILLAGE, FORGE, CHAMBER, ORIGIN, PLOT, BID -> 1;
			case NATIVES -> 3;
			case ROGUES -> 2;
			case MED_LONG -> 8 * 60;
			case MED_SITE -> 5 * 60;
			case TECH -> 5;
			case REFINE, HERBS, TALISMAN -> 2;
			case ORES -> 6;
		};
	}

	/** {@code extra.goals.daily} read-only. */
	public static CompoundTag view(CultivationData data) {
		return GoalCounters.view(data).getCompoundOrEmpty(KEY);
	}

	/** The streak bonus factor (1.0 to 1.5). */
	public static double streakFactor(int streak) {
		return 1.0D + STREAK_STEP * Math.max(0, Math.min(STREAK_MAX, streak));
	}

	// ================================================================== server

	/** Every 100 ticks: a new set when a new day began (and enough was played), then the progress and rewards. */
	static boolean update(ServerPlayer player, CultivationData data) {
		if (!KultivatorConfig.get().dailyTasks) {
			return false;
		}
		CompoundTag daily = GoalCounters.section(GoalCounters.goals(data), KEY);
		long day = today(player);
		int played = GoalCounters.value(data, GoalCounters.PLAYED);
		boolean changed = false;
		boolean none = daily.getListOrEmpty("chores").isEmpty();
		if (none || daily.getLongOr("day", -1L) != day && played - daily.getIntOr("last", 0) >= MIN_PLAYED) {
			refresh(player, data, daily, day, played);
			changed = true;
		}
		return progress(player, data, daily) || changed;
	}

	private static long today(ServerPlayer player) {
		return player.level().getServer().overworld().getGameTime() / DAY_TICKS;
	}

	private static void refresh(ServerPlayer player, CultivationData data, CompoundTag daily, long day, int played) {
		long lastFull = daily.getLongOr("last_full", Long.MIN_VALUE / 2);
		if (lastFull < day - 1L && daily.getIntOr("streak", 0) > 0) {
			daily.putInt("streak", 0);
		}
		List<Chore> pool = new ArrayList<>();
		for (Chore chore : Chore.values()) {
			if (available(player, data, chore)) {
				pool.add(chore);
			}
		}
		ListTag chores = new ListTag();
		for (int i = 0; i < COUNT && !pool.isEmpty(); i++) {
			Chore chore = pool.remove(player.getRandom().nextInt(pool.size()));
			CompoundTag entry = new CompoundTag();
			entry.putString("id", chore.id());
			entry.putInt("need", need(chore, data));
			entry.putInt("have", 0);
			entry.putInt("base", counter(player, data, chore));
			chores.add(entry);
		}
		daily.put("chores", chores);
		daily.putLong("day", day);
		daily.putInt("n", daily.getIntOr("n", 0) + 1);
		daily.putInt("last", played);
		daily.putInt("done", 0);
		daily.putBoolean("full", false);
	}

	private static boolean available(ServerPlayer player, CultivationData data, Chore chore) {
		KultivatorConfig config = KultivatorConfig.get();
		boolean refining = data.realm().atLeast(Realm.QI_REFINING);
		return switch (chore) {
			case KILLS, MED_LONG, ORES, THUNDER -> true;
			case ELITE -> refining && config.eliteMonsters;
			case MED_SITE -> RealmTrials.done(player, "spiritual_site");
			case TECH -> RealmTrials.done(player, "first_technique");
			case REFINE -> RealmTrials.done(player, "first_alchemy");
			case HERBS -> refining && config.herbHarvest;
			case TALISMAN -> RealmTrials.done(player, "first_talisman");
			case ROGUES -> refining && RealmTrials.done(player, "rogue_slain");
			case VILLAGE -> config.vassalVillages && dev.kultivator.desa.Vassals.count(player.getUUID()) > 0;
			case FORGE -> config.artifactForging && RealmTrials.done(player, "first_forge");
			case CHAMBER -> config.secretRealms && RealmTrials.done(player, "realm_gate");
			case NATIVES, ORIGIN -> config.immortalRealm && dev.kultivator.abadi.Abadi.ascended(player);
			case PLOT -> config.innerWorld && dev.kultivator.batin.Batin.has(player);
			case BID -> config.immortalAuction && dev.kultivator.abadi.Abadi.ascended(player);
		};
	}

	/** The lifetime counter a chore counts from (the unbroken meditation chore is special: the best run today). */
	private static int counter(ServerPlayer player, CultivationData data, Chore chore) {
		return switch (chore) {
			case KILLS -> GoalCounters.value(data, GoalCounters.KILLS);
			case ELITE -> GoalCounters.elites(data, 1);
			case MED_LONG -> 0;
			case MED_SITE -> GoalCounters.value(data, GoalCounters.MED_SITE20);
			case TECH -> data.techniqueKills();
			case REFINE -> GoalCounters.refined(data);
			case ORES -> GoalCounters.value(data, GoalCounters.ORES);
			case HERBS -> GoalCounters.herbsFound(data);
			case THUNDER -> GoalCounters.thunderKills(data);
			case TALISMAN -> GoalCounters.talismansMade(data);
			case ROGUES -> Goals.worldCounter(data, Goals.W_ROGUES);
			case VILLAGE -> Goals.worldCounter(data, Goals.W_REQUESTS);
			// Forging and refining counters are module C's data (extra.tempa.made / refined, DESIGN 25.14).
			case FORGE -> data.extra().getCompoundOrEmpty("tempa").getIntOr("made", 0) + data.extra().getCompoundOrEmpty("tempa").getIntOr("refined", 0);
			case CHAMBER -> Goals.worldCounter(data, Goals.W_CLEARS);
			case NATIVES -> Goals.worldCounter(data, Goals.W_NATIVES);
			case PLOT -> Goals.worldCounter(data, Goals.W_PLOTS);
			// Module A's data (DESIGN 26.13 extra.abadi): a lifetime counter when it keeps one, else today's count.
			case ORIGIN -> abadiCounter(player, data.extra().getCompoundOrEmpty("abadi"), "mined_total", "mined", "mined_day");
			case BID -> abadiCounter(player, data.extra().getCompoundOrEmpty("abadi").getCompoundOrEmpty("auction"), "bids", "won", "");
		};
	}

	/**
	 * A counter of module A for the phase-9 chores: the lifetime key when present, else the fallback key (a daily count is
	 * read only on its own day, so a new day starts from 0 and the chore's "have" never falls).
	 */
	private static int abadiCounter(ServerPlayer player, CompoundTag tag, String lifetime, String fallback, String dayKey) {
		if (tag.contains(lifetime)) {
			return tag.getIntOr(lifetime, 0);
		}
		if (!dayKey.isEmpty() && tag.getLongOr(dayKey, -1L) != today(player)) {
			return 0;
		}
		return tag.getIntOr(fallback, 0);
	}

	private static boolean progress(ServerPlayer player, CultivationData data, CompoundTag daily) {
		ListTag chores = daily.getListOrEmpty("chores");
		int done = daily.getIntOr("done", 0);
		boolean changed = false;
		for (int i = 0; i < chores.size(); i++) {
			CompoundTag entry = chores.getCompoundOrEmpty(i);
			Chore chore = Chore.byId(entry.getStringOr("id", ""));
			if (chore == null) {
				continue;
			}
			int need = Math.max(1, entry.getIntOr("need", 1));
			int old = entry.getIntOr("have", 0);
			int have;
			if (chore == Chore.MED_LONG) {
				have = Math.max(old, GoalCounters.run(player));
			} else {
				have = Math.max(old, counter(player, data, chore) - entry.getIntOr("base", 0));
			}
			have = Math.min(need, Math.max(0, have));
			if (have != old) {
				entry.putInt("have", have);
				changed = true;
			}
			if (have >= need && (done & (1 << i)) == 0) {
				done |= 1 << i;
				daily.putInt("done", done);
				reward(player, data, daily, chore);
				changed = true;
			}
		}
		if (!chores.isEmpty() && done == (1 << chores.size()) - 1 && !daily.getBooleanOr("full", false)) {
			completeDay(player, daily);
			changed = true;
		}
		return changed;
	}

	private static void reward(ServerPlayer player, CultivationData data, CompoundTag daily, Chore chore) {
		double amount = data.rank().xpRequired() * REWARD_SHARE * streakFactor(daily.getIntOr("streak", 0));
		double gained = Progression.grantXp(player, amount, XpSource.TRIAL);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.6F, 1.4F);
		Msg.chat(player, Component.translatable("kultivator.goals.daily.done_one", name(chore, need(chore, data)),
				Msg.number(Math.max(0.0D, gained))).withStyle(ChatFormatting.GREEN));
	}

	private static void completeDay(ServerPlayer player, CompoundTag daily) {
		long day = daily.getLongOr("day", 0L);
		long lastFull = daily.getLongOr("last_full", Long.MIN_VALUE / 2);
		int streak = lastFull == day - 1L ? daily.getIntOr("streak", 0) + 1 : lastFull == day ? daily.getIntOr("streak", 0) : 1;
		daily.putInt("streak", streak);
		daily.putLong("last_full", day);
		daily.putBoolean("full", true);
		give(player, SpiritStones.stack(SpiritStones.LOW, 1));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9F, 1.2F);
		Msg.chat(player, Component.translatable("kultivator.goals.daily.done_all", streak,
				Math.round((streakFactor(streak) - 1.0D) * 100.0D)).withStyle(ChatFormatting.GOLD));
		if (streak >= STREAK_ADVANCEMENT) {
			Advancements.grant(player, "daily_streak_5");
		}
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5D, player.getZ(), stack);
			item.setDefaultPickUpDelay();
			item.addTag(NoGrief.TAG);
			item.setPortalCooldown();
			player.level().addFreshEntity(item);
		}
	}

	/** "Kill 12 monsters", "Meditate 8 min without a break". */
	public static MutableComponent name(Chore chore, int need) {
		return Component.translatable("kultivator.goals.daily.chore." + chore.id(), chore.seconds() ? need / 60 : need);
	}

	// ================================================================== commands

	/** {@code /kultivasi harian}: today's chores and the streak in chat. */
	static int print(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		if (!KultivatorConfig.get().dailyTasks) {
			Msg.chat(player, Component.translatable("kultivator.goals.daily.off").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		if (update(player, data)) {
			Cultivators.dirty(player);
		}
		CompoundTag daily = view(data);
		int streak = daily.getIntOr("streak", 0);
		Msg.chat(player, Component.translatable("kultivator.goals.daily.header", streak, Math.round((streakFactor(streak) - 1.0D) * 100.0D))
				.withStyle(ChatFormatting.GOLD));
		ListTag chores = daily.getListOrEmpty("chores");
		int done = daily.getIntOr("done", 0);
		for (int i = 0; i < chores.size(); i++) {
			CompoundTag entry = chores.getCompoundOrEmpty(i);
			Chore chore = Chore.byId(entry.getStringOr("id", ""));
			if (chore == null) {
				continue;
			}
			int need = entry.getIntOr("need", 1);
			int have = entry.getIntOr("have", 0);
			String amount = chore.seconds() ? have / 60 + "/" + need / 60 : have + "/" + need;
			boolean ok = (done & (1 << i)) != 0;
			Msg.chat(player, Component.literal(ok ? "✓ " : "✗ ").withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED)
					.append(name(chore, need).withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" (" + amount + ")").withStyle(ChatFormatting.GRAY)));
		}
		Msg.chat(player, Component.translatable("kultivator.goals.daily.reward_line").withStyle(ChatFormatting.GRAY));
		return 1;
	}

	/** {@code uji harian ulang}: a new set right now. */
	static void reroll(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		CompoundTag daily = GoalCounters.section(GoalCounters.goals(data), KEY);
		refresh(player, data, daily, today(player), GoalCounters.value(data, GoalCounters.PLAYED));
		Cultivators.dirty(player);
	}

	/** {@code uji harian selesai}: completes today's chores (rewards included). */
	static void completeAll(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		update(player, data);
		CompoundTag daily = GoalCounters.section(GoalCounters.goals(data), KEY);
		ListTag chores = daily.getListOrEmpty("chores");
		for (Tag tag : chores) {
			if (tag instanceof CompoundTag entry) {
				entry.putInt("have", entry.getIntOr("need", 1));
				// Keeps the next update from lowering it again: the counter-based "have" never goes below this.
				entry.putInt("base", Integer.MIN_VALUE / 2);
			}
		}
		progress(player, data, daily);
		Cultivators.dirty(player);
	}

	/** {@code uji harian beruntun <n>}: sets the streak (yesterday counts as a full day). */
	static void setStreak(ServerPlayer player, int streak) {
		CultivationData data = Cultivators.get(player);
		CompoundTag daily = GoalCounters.section(GoalCounters.goals(data), KEY);
		daily.putInt("streak", Math.max(0, streak));
		if (streak > 0 && !daily.getBooleanOr("full", false)) {
			daily.putLong("last_full", today(player) - 1L);
		}
		Cultivators.dirty(player);
	}

	private DailyChores() {
	}
}
