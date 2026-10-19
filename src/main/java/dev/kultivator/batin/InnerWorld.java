package dev.kultivator.batin;

import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.ascension.Ascension;
import dev.kultivator.ascension.Stars;
import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.goals.Goals;
import dev.kultivator.harta.HartaComponents;
import dev.kultivator.harta.HerbKind;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.sect.event.SectEventItems;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The inner world's data and rules (DESIGN 26.6.2, 26.6.3): grade, plots, foxes, rockmen and the immortal essence pearls,
 * all pure data in {@code extra.batin} with zero ticking. Everything advances by elapsed overworld days (game time / 24,000,
 * so sleeping does not speed it up) when the Batin page opens or on the player's periodic day check, at most
 * {@link #CATCH_UP} days at once. Only clicks move herbs and items.
 * <p>
 * Sumber: Reverend Insanity ch. 409 "Managing the Blessed Land" (time inside runs five times faster, green grape-like
 * immortal essence pearls, an underground rockman tribe that eats ore, millions of foxes). Plots, rates, caps and every
 * number are game design.
 */
public final class InnerWorld {

	public static final String[] GRADES = {"kecil", "sedang", "besar", "agung"};
	public static final int[] GRADE_PLOTS = {4, 6, 9, 12};
	public static final int[] GRADE_PEARLS = {1, 2, 3, 4};
	public static final int[] GRADE_FOXES = {1000, 2000, 3000, 4000};
	public static final int MAX_PLOTS = 16;
	public static final int MIN_USABLE = 2;
	/** Herb years per outside day, before Kesuburan (RevI's ×5 time as the growth multiplier: 5 × 5 years). */
	public static final int HERB_PER_DAY = 25;
	public static final int HERB_CAP = 3000;
	public static final int SECOND_HERB_AGE = 100;
	/** A harvest gives the second herb only after the plot itself grew this many years (no plant-and-harvest loop). */
	public static final int SECOND_HERB_GROWN = 100;
	public static final int FOX_START = 100;
	public static final double FOX_GROWTH = 0.05D;
	public static final double FERTILITY_PER_100 = 0.01D;
	public static final double FERTILITY_CAP = 0.40D;
	public static final int ROCK_WAKE = 64;
	public static final int ROCK_BUFFER = 256;
	public static final int ROCK_DAILY = 32;
	public static final int ROCK_IRON_GROUP = 8;
	public static final int ROCK_GOLD_GROUP = 4;
	public static final int ROCK_BATU_EVERY = 7;
	public static final int PEARL_CAP = 99;
	public static final int WITHDRAW_PER_DAY = 16;
	public static final int BREW_PER_DAY = 5;
	public static final double BREW_SHARE = 0.01D;
	public static final int FORTIFY_COST = 10;
	public static final int FORTIFY_MAX = 3;
	public static final int EXPAND_BATU = 16;
	public static final int CATCH_UP = 10;
	public static final int RUIN_DAYS = 3;
	/** 1 outside day = 2 Tahun Batin (game scale). */
	public static final int YEARS_PER_DAY = 2;

	public static final int STATE_OK = 0;
	public static final int STATE_BURNED = 1;
	public static final int STATE_FLOODED = 2;
	public static final int STATE_SCORCHED = 3;
	public static final String FOUNDATION = "foundation";

	public static final TagKey<Item> ROCKMEN_ORE = TagKey.create(Registries.ITEM, Kultivator.id("rockmen_ore"));
	public static final TagKey<Item> PRIMORDIAL_MATERIAL = TagKey.create(Registries.ITEM, Kultivator.id("primordial_material"));
	public static final String BATU_ID = "immortal_origin_stone";

	// ================================================================== data access

	/** {@code extra.batin}, created when missing (server writes). */
	static CompoundTag root(CultivationData data) {
		return section(data.extra(), Batin.EXTRA_KEY);
	}

	/** {@code extra.batin} read-only. */
	static CompoundTag view(CultivationData data) {
		return data.extra().getCompoundOrEmpty(Batin.EXTRA_KEY);
	}

	static CompoundTag section(CompoundTag parent, String key) {
		CompoundTag tag = parent.getCompound(key).orElse(null);
		if (tag == null) {
			tag = new CompoundTag();
			parent.put(key, tag);
		}
		return tag;
	}

	static ListTag list(CompoundTag parent, String key) {
		ListTag tag = parent.getList(key).orElse(null);
		if (tag == null) {
			tag = new ListTag();
			parent.put(key, tag);
		}
		return tag;
	}

	public static boolean born(CultivationData data) {
		return view(data).getBooleanOr("born", false);
	}

	/** The outside day (overworld game time / 24,000). */
	public static long day(MinecraftServer server) {
		return server.overworld().getGameTime() / 24000L;
	}

	static long day(ServerPlayer player) {
		return day(player.level().getServer());
	}

	public static int grade(CompoundTag batin) {
		return Math.max(0, Math.min(GRADES.length - 1, batin.getIntOr("grade", 0)));
	}

	public static int foxCap(CompoundTag batin) {
		return GRADE_FOXES[grade(batin)];
	}

	/** Kesuburan: +1% herb growth per 100 foxes, at most +40%. */
	public static double fertility(int foxes) {
		return Math.min(FERTILITY_CAP, Math.max(0, foxes) / 100 * FERTILITY_PER_100);
	}

	/** The grade from the breakthroughs (DESIGN 26.6.2): average ≤ Normal Kecil, Perfect Sedang, Heavenly Besar, + flawless Agung. */
	public static int computeGrade(CultivationData data) {
		int sum = 0;
		int count = 0;
		for (Realm realm : Realm.values()) {
			if (!realm.atLeast(Realm.FOUNDATION) || realm.ordinal() > data.realm().ordinal()) {
				continue;
			}
			BreakthroughQuality quality = data.quality(realm);
			sum += quality == null ? BreakthroughQuality.NORMAL.ordinal() : quality.ordinal();
			count++;
		}
		int average = count == 0 ? BreakthroughQuality.NORMAL.ordinal() : (int) Math.round(sum / (double) count);
		if (average >= BreakthroughQuality.HEAVENLY.ordinal()) {
			return Ascension.flawlessPath(data) ? 3 : 2;
		}
		return average >= BreakthroughQuality.PERFECT.ordinal() ? 1 : 0;
	}

	// ================================================================== birth

	/** Creates (or with {@code reset} recreates) the inner world; {@code grade} -1 = from the breakthroughs. */
	public static void birth(ServerPlayer player, int grade, boolean reset) {
		CultivationData data = Cultivators.get(player);
		if (born(data) && !reset) {
			return;
		}
		CompoundTag batin = new CompoundTag();
		int chosen = grade < 0 ? computeGrade(data) : Math.max(0, Math.min(GRADES.length - 1, grade));
		long day = day(player);
		batin.putBoolean("born", true);
		batin.putInt("grade", chosen);
		ListTag plots = new ListTag();
		for (int i = 0; i < GRADE_PLOTS[chosen]; i++) {
			plots.add(new CompoundTag());
		}
		batin.put("plots", plots);
		batin.putInt("foxes", FOX_START);
		batin.putInt("pearls", 0);
		batin.putLong("last_day", day);
		batin.putInt("spirit", player.getRandom().nextInt(LandSpirit.NAMES));
		CompoundTag cal = new CompoundTag();
		cal.putInt("n", 1);
		int every = KultivatorConfig.get().innerWorldCalamityDays;
		cal.putLong("due", every <= 0 ? -1L : day + every);
		batin.put("cal", cal);
		batin.put("rock", new CompoundTag());
		// Created arts survive a test reset (they belong to the player, not to the land).
		ListTag arts = view(data).getListOrEmpty("ciptaan");
		if (!arts.isEmpty()) {
			batin.put("ciptaan", arts.copy());
		}
		data.extra().put(Batin.EXTRA_KEY, batin);
		LandSpirit.log(batin, day, "born", chosen, 0);
		Cultivators.dirty(player);
		Batin.dirty(player);
		Advancements.grant(player, "batin_born");
		checkPlots(player, batin);
		Fx.send(player.level(), player.position(), FxType.BATIN, BatinFxIds.BORN, chosen, player.getBbHeight());
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.7F);
		Msg.chat(player, Component.translatable("kultivator.batin.born", Component.translatable("kultivator.batin.grade." + GRADES[chosen]),
				GRADE_PLOTS[chosen]).withStyle(ChatFormatting.LIGHT_PURPLE));
		Msg.chat(player, LandSpirit.say(batin, Component.translatable("kultivator.batin.spirit.greet")));
	}

	// ================================================================== the lazy day

	/** Advances the elapsed outside days (at most {@link #CATCH_UP}); returns how many were processed. */
	static int catchUp(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		if (!born(data)) {
			return 0;
		}
		CompoundTag batin = root(data);
		long day = day(player);
		long last = batin.getLongOr("last_day", day);
		if (last > day) {
			// The world's time went back (a command): start counting again from today.
			batin.putLong("last_day", day);
			return 0;
		}
		int days = (int) Math.min(CATCH_UP, day - last);
		if (days > 0) {
			advance(player, batin, days, last);
		}
		batin.putLong("last_day", day);
		return days;
	}

	/** {@code uji batin hari <n>}: n days pass now (≤ 30), the calendar of the calamity included. */
	static void advanceTest(ServerPlayer player, int days) {
		CultivationData data = Cultivators.get(player);
		if (!born(data)) {
			return;
		}
		CompoundTag batin = root(data);
		long last = batin.getLongOr("last_day", day(player));
		advance(player, batin, Math.max(0, Math.min(30, days)), last);
		CompoundTag cal = section(batin, "cal");
		long due = cal.getLongOr("due", -1L);
		if (due > 0L) {
			// The calamity's due day moves closer by the same days (the outside clock did not move).
			cal.putLong("due", due - days);
		}
	}

	/** One step per day: plot states expire, herbs grow, foxes breed, pearls fill, the rockmen eat. */
	private static void advance(ServerPlayer player, CompoundTag batin, int days, long fromDay) {
		int grade = grade(batin);
		ListTag plots = list(batin, "plots");
		int foxes = batin.getIntOr("foxes", FOX_START);
		int pearls = batin.getIntOr("pearls", 0);
		CompoundTag rock = section(batin, "rock");
		for (int d = 1; d <= days; d++) {
			long today = fromDay + d;
			double growth = HERB_PER_DAY * (1.0D + fertility(foxes));
			for (int i = 0; i < plots.size(); i++) {
				CompoundTag plot = plots.getCompoundOrEmpty(i);
				int state = plot.getIntOr("s", STATE_OK);
				if (state != STATE_OK && plot.getLongOr("u", 0L) <= today) {
					plot.putInt("s", STATE_OK);
					state = STATE_OK;
				}
				if (!plot.getStringOr("k", "").isEmpty() && state != STATE_BURNED && state != STATE_SCORCHED) {
					int age = plot.getIntOr("a", 1);
					plot.putInt("a", Math.max(age, Math.min(HERB_CAP, (int) Math.round(age + growth))));
				}
			}
			foxes = Math.min(GRADE_FOXES[grade], Math.max(foxes + 1, (int) Math.ceil(foxes * (1.0D + FOX_GROWTH))));
			pearls = Math.min(PEARL_CAP, pearls + GRADE_PEARLS[grade]);
			if (rock.getBooleanOr("awake", false)) {
				rockmenDay(rock);
			}
		}
		batin.putInt("foxes", foxes);
		batin.putInt("pearls", pearls);
		if (days > 0) {
			LandSpirit.log(batin, fromDay + days, "days", days, pearls);
			Batin.dirty(player);
		}
	}

	/** The rockmen eat up to 32 items: a diamond/emerald ore → 1 High, 4 gold/redstone/lapis → 1 Mid, 8 iron/copper → 1 Mid. */
	private static void rockmenDay(CompoundTag rock) {
		int budget = ROCK_DAILY;
		int diamonds = rock.getIntOr("d", 0);
		int golds = rock.getIntOr("g", 0);
		int irons = rock.getIntOr("i", 0);
		int high = Math.min(diamonds, budget);
		diamonds -= high;
		budget -= high;
		int goldGroups = Math.min(golds / ROCK_GOLD_GROUP, budget / ROCK_GOLD_GROUP);
		golds -= goldGroups * ROCK_GOLD_GROUP;
		budget -= goldGroups * ROCK_GOLD_GROUP;
		int ironGroups = Math.min(irons / ROCK_IRON_GROUP, budget / ROCK_IRON_GROUP);
		irons -= ironGroups * ROCK_IRON_GROUP;
		rock.putInt("d", diamonds);
		rock.putInt("g", golds);
		rock.putInt("i", irons);
		rock.putInt("high", Math.min(999, rock.getIntOr("high", 0) + high));
		rock.putInt("mid", Math.min(999, rock.getIntOr("mid", 0) + goldGroups + ironGroups));
		int fed = rock.getIntOr("days", 0) + 1;
		rock.putInt("days", fed);
		if (fed % ROCK_BATU_EVERY == 0) {
			rock.putInt("batu", Math.min(99, rock.getIntOr("batu", 0) + 1));
		}
	}

	// ================================================================== plots

	public static int usablePlots(ListTag plots) {
		int n = 0;
		for (int i = 0; i < plots.size(); i++) {
			int state = plots.getCompoundOrEmpty(i).getIntOr("s", STATE_OK);
			if (state != STATE_BURNED && state != STATE_SCORCHED) {
				n++;
			}
		}
		return n;
	}

	/** Plants the herb in the main hand into plot {@code index} (consumed: one herb per plot). */
	static void plant(ServerPlayer player, int index) {
		CompoundTag batin = root(Cultivators.get(player));
		ListTag plots = list(batin, "plots");
		if (index < 0 || index >= plots.size()) {
			return;
		}
		CompoundTag plot = plots.getCompoundOrEmpty(index);
		int state = plot.getIntOr("s", STATE_OK);
		if (state == STATE_BURNED || state == STATE_SCORCHED) {
			Msg.actionBar(player, Component.translatable("kultivator.batin.plot.ruined", Math.max(0L, plot.getLongOr("u", 0L) - day(player)))
					.withStyle(ChatFormatting.RED));
			return;
		}
		if (!plot.getStringOr("k", "").isEmpty()) {
			harvest(player, index);
			return;
		}
		ItemStack held = player.getMainHandItem();
		if (!SpiritHerbs.isHerb(held)) {
			Msg.actionBar(player, Component.translatable("kultivator.batin.plot.need_herb").withStyle(ChatFormatting.YELLOW));
			return;
		}
		HerbKind kind = SpiritHerbs.kind(held);
		int age = Math.max(1, SpiritHerbs.age(held));
		CompoundTag planted = new CompoundTag();
		planted.putString("k", kind == null ? FOUNDATION : kind.id());
		planted.putInt("a", age);
		planted.putInt("p", age);
		planted.putInt("s", state);
		planted.putLong("u", plot.getLongOr("u", 0L));
		plots.set(index, planted);
		held.shrink(1);
		Cultivators.dirty(player);
		Batin.dirty(player);
		Fx.sendTo(player, player.position(), player.position(), FxType.BATIN, BatinFxIds.PLOT, 0, 0.0F);
		Msg.actionBar(player, Component.translatable("kultivator.batin.plot.planted", herbName(planted), age).withStyle(ChatFormatting.GREEN));
	}

	/** Harvests plot {@code index}: the herb at its age, and from 1,000 years a second herb of 100 years. */
	static void harvest(ServerPlayer player, int index) {
		CompoundTag batin = root(Cultivators.get(player));
		ListTag plots = list(batin, "plots");
		if (index < 0 || index >= plots.size()) {
			return;
		}
		CompoundTag plot = plots.getCompoundOrEmpty(index);
		String kind = plot.getStringOr("k", "");
		if (kind.isEmpty()) {
			return;
		}
		int age = Math.max(1, plot.getIntOr("a", 1));
		int planted = plot.getIntOr("p", age);
		ItemStack herb = herbStack(kind, age);
		CompoundTag empty = new CompoundTag();
		if (plot.getIntOr("s", STATE_OK) != STATE_OK) {
			empty.putInt("s", plot.getIntOr("s", STATE_OK));
			empty.putLong("u", plot.getLongOr("u", 0L));
		}
		plots.set(index, empty);
		give(player, herb);
		if (age >= SpiritHerbs.MILLENNIUM && age - planted >= SECOND_HERB_GROWN) {
			give(player, herbStack(kind, SECOND_HERB_AGE));
		}
		if (age >= SpiritHerbs.MILLENNIUM) {
			Advancements.grant(player, "batin_millennium");
		}
		batin.putInt("harvests", batin.getIntOr("harvests", 0) + 1);
		Goals.addWorldCounter(player, Goals.W_PLOTS, 1);
		Cultivators.dirty(player);
		Batin.dirty(player);
		Fx.sendTo(player, player.position(), player.position(), FxType.BATIN, BatinFxIds.PLOT, 1, 0.0F);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROP_BREAK, SoundSource.PLAYERS, 0.8F, 1.2F);
		Msg.actionBar(player, Component.translatable("kultivator.batin.plot.harvested", herb.getHoverName(), age).withStyle(ChatFormatting.GREEN));
	}

	static ItemStack herbStack(String kind, int age) {
		if (FOUNDATION.equals(kind)) {
			ItemStack stack = new ItemStack(SectEventItems.FOUNDATION_HERB);
			stack.set(HartaComponents.HERB_AGE, Math.max(1, Math.min(HartaComponents.MAX_HERB_AGE, age)));
			return stack;
		}
		HerbKind herb = HerbKind.byId(kind);
		return herb == null ? ItemStack.EMPTY : SpiritHerbs.create(herb, age);
	}

	static Component herbName(CompoundTag plot) {
		ItemStack stack = herbStack(plot.getStringOr("k", ""), 1);
		return stack.isEmpty() ? Component.literal("?") : stack.getHoverName();
	}

	/** The herb's colour for the plot grid. */
	static int herbColor(String kind) {
		HerbKind herb = HerbKind.byId(kind);
		return herb == null ? 0xC8B060 : herb.color();
	}

	/** +1 plot (Perluas, the token); false at 16. Grants {@code batin_nine} from 9 plots. */
	static boolean addPlot(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		if (!born(data)) {
			return false;
		}
		CompoundTag batin = root(data);
		ListTag plots = list(batin, "plots");
		if (plots.size() >= MAX_PLOTS) {
			return false;
		}
		plots.add(new CompoundTag());
		LandSpirit.log(batin, day(player), "expand", plots.size(), 0);
		Cultivators.dirty(player);
		Batin.dirty(player);
		checkPlots(player, batin);
		Msg.chat(player, Component.translatable("kultivator.batin.expand.done", plots.size(), MAX_PLOTS).withStyle(ChatFormatting.GREEN));
		return true;
	}

	static void checkPlots(ServerPlayer player, CompoundTag batin) {
		if (batin.getListOrEmpty("plots").size() >= 9) {
			Advancements.grant(player, "batin_nine");
		}
	}

	/** Perluas: 16 Batu Asal Abadi + 1 primordial material from the inventory, then +1 plot. */
	static void expand(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		if (!born(data)) {
			return;
		}
		if (view(data).getListOrEmpty("plots").size() >= MAX_PLOTS) {
			Msg.chat(player, Component.translatable("kultivator.batin.expand.full").withStyle(ChatFormatting.YELLOW));
			return;
		}
		Item batu = registryItem(BATU_ID);
		if (batu == Items.AIR || count(player, batu) < EXPAND_BATU || countTag(player, PRIMORDIAL_MATERIAL) < 1) {
			Msg.chat(player, Component.translatable("kultivator.batin.expand.cost", EXPAND_BATU).withStyle(ChatFormatting.YELLOW));
			return;
		}
		take(player, batu, EXPAND_BATU);
		takeTag(player, PRIMORDIAL_MATERIAL, 1);
		addPlot(player);
	}

	// ================================================================== pearls

	public static int pearls(CultivationData data) {
		return view(data).getIntOr("pearls", 0);
	}

	/** Pearls the player can spend: the counter plus pearl items in the inventory. */
	static int pearlsAvailable(ServerPlayer player) {
		return pearls(Cultivators.get(player)) + count(player, BatinItems.IMMORTAL_ESSENCE_PEARL);
	}

	/** Takes {@code n} pearls (the counter first, then pearl items); false (nothing taken) when there are not enough. */
	static boolean takePearls(ServerPlayer player, int n) {
		if (n <= 0) {
			return true;
		}
		if (pearlsAvailable(player) < n) {
			return false;
		}
		CompoundTag batin = root(Cultivators.get(player));
		int counter = batin.getIntOr("pearls", 0);
		int fromCounter = Math.min(counter, n);
		batin.putInt("pearls", counter - fromCounter);
		take(player, BatinItems.IMMORTAL_ESSENCE_PEARL, n - fromCounter);
		Cultivators.dirty(player);
		Batin.dirty(player);
		return true;
	}

	/** Puts pearl items back into the counter (at most 99); returns how many were stored. */
	static int depositPearls(ServerPlayer player, int count) {
		CultivationData data = Cultivators.get(player);
		if (!born(data)) {
			Msg.actionBar(player, Component.translatable("kultivator.batin.none").withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		CompoundTag batin = root(data);
		int have = batin.getIntOr("pearls", 0);
		int stored = Math.max(0, Math.min(count, PEARL_CAP - have));
		if (stored <= 0) {
			Msg.actionBar(player, Component.translatable("kultivator.batin.pearl.full", PEARL_CAP).withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		batin.putInt("pearls", have + stored);
		Cultivators.dirty(player);
		Batin.dirty(player);
		Msg.actionBar(player, Component.translatable("kultivator.batin.pearl.stored", stored, have + stored).withStyle(ChatFormatting.GREEN));
		return stored;
	}

	/** Withdraws pearls as items (≤ 16 per day). */
	static void withdraw(ServerPlayer player, int wanted) {
		CompoundTag batin = root(Cultivators.get(player));
		long day = day(player);
		int done = batin.getLongOr("wd_day", -1L) == day ? batin.getIntOr("wd_n", 0) : 0;
		int n = Math.min(Math.min(wanted, WITHDRAW_PER_DAY - done), batin.getIntOr("pearls", 0));
		if (n <= 0) {
			Msg.chat(player, Component.translatable("kultivator.batin.pearl.withdraw_none", WITHDRAW_PER_DAY).withStyle(ChatFormatting.YELLOW));
			return;
		}
		batin.putInt("pearls", batin.getIntOr("pearls", 0) - n);
		batin.putLong("wd_day", day);
		batin.putInt("wd_n", done + n);
		give(player, new ItemStack(BatinItems.IMMORTAL_ESSENCE_PEARL, n));
		Cultivators.dirty(player);
		Batin.dirty(player);
		Msg.chat(player, Component.translatable("kultivator.batin.pearl.withdrawn", n).withStyle(ChatFormatting.GREEN));
	}

	/** Seduh: each pearl gives 1% of {@code Stars.barSize} as Qi Abadi (≤ 5 per day). */
	static void brew(ServerPlayer player, int wanted) {
		CultivationData data = Cultivators.get(player);
		if (!born(data)) {
			Msg.chat(player, Component.translatable("kultivator.batin.none").withStyle(ChatFormatting.YELLOW));
			return;
		}
		CompoundTag batin = root(data);
		long day = day(player);
		int done = batin.getLongOr("br_day", -1L) == day ? batin.getIntOr("br_n", 0) : 0;
		int n = Math.min(Math.max(0, wanted), Math.min(BREW_PER_DAY - done, pearlsAvailable(player)));
		if (n <= 0) {
			Msg.chat(player, Component.translatable("kultivator.batin.brew.none", BREW_PER_DAY).withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (!takePearls(player, n)) {
			return;
		}
		batin.putLong("br_day", day);
		batin.putInt("br_n", done + n);
		double amount = Stars.barSize(player) * BREW_SHARE * n;
		Stars.grantShare(player, BREW_SHARE * n);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.8F, 1.2F);
		Msg.chat(player, Component.translatable("kultivator.batin.brew.done", n, Msg.number(amount)).withStyle(ChatFormatting.LIGHT_PURPLE));
		Batin.dirty(player);
	}

	/** Perkokoh: 10 pearls per step (≤ 3) before the next calamity. */
	static void fortify(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		if (!born(data)) {
			return;
		}
		CompoundTag cal = section(root(data), "cal");
		if (cal.getLongOr("due", -1L) < 0L || InnerCalamity.active(player.getUUID())) {
			Msg.chat(player, Component.translatable("kultivator.batin.fortify.off").withStyle(ChatFormatting.YELLOW));
			return;
		}
		int steps = cal.getIntOr("fortify", 0);
		if (steps >= FORTIFY_MAX) {
			Msg.chat(player, Component.translatable("kultivator.batin.fortify.max", FORTIFY_MAX).withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (!takePearls(player, FORTIFY_COST)) {
			Msg.chat(player, Component.translatable("kultivator.batin.fortify.cost", FORTIFY_COST).withStyle(ChatFormatting.YELLOW));
			return;
		}
		cal.putInt("fortify", steps + 1);
		Cultivators.dirty(player);
		Batin.dirty(player);
		Msg.chat(player, Component.translatable("kultivator.batin.fortify.done", steps + 1, FORTIFY_MAX,
				Math.round(InnerCalamity.surviveChance(root(data)) * 100.0D)).withStyle(ChatFormatting.GREEN));
	}

	// ================================================================== rockmen

	/** 0 iron/copper, 1 gold/redstone/lapis, 2 diamond/emerald; -1 not rockmen food. */
	static int oreClass(ItemStack stack) {
		if (stack.isEmpty() || !stack.is(ROCKMEN_ORE) || stack.is(Items.ANCIENT_DEBRIS)) {
			return -1;
		}
		if (stack.is(Items.DIAMOND_ORE) || stack.is(Items.DEEPSLATE_DIAMOND_ORE) || stack.is(Items.EMERALD_ORE) || stack.is(Items.DEEPSLATE_EMERALD_ORE)) {
			return 2;
		}
		if (stack.is(Items.GOLD_ORE) || stack.is(Items.DEEPSLATE_GOLD_ORE) || stack.is(Items.NETHER_GOLD_ORE) || stack.is(Items.RAW_GOLD)
				|| stack.is(Items.REDSTONE_ORE) || stack.is(Items.DEEPSLATE_REDSTONE_ORE) || stack.is(Items.LAPIS_ORE) || stack.is(Items.DEEPSLATE_LAPIS_ORE)) {
			return 1;
		}
		return 0;
	}

	/** Lubang Bijih: ores from the inventory (consumed; no withdrawal). The first 64 awaken the rockmen. */
	static void feed(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		if (!born(data)) {
			return;
		}
		CompoundTag rock = section(root(data), "rock");
		boolean awake = rock.getBooleanOr("awake", false);
		int wake = rock.getIntOr("wake", 0);
		int[] buffer = {rock.getIntOr("i", 0), rock.getIntOr("g", 0), rock.getIntOr("d", 0)};
		int taken = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			int kind = oreClass(stack);
			if (kind < 0) {
				continue;
			}
			while (!stack.isEmpty()) {
				if (!awake) {
					int n = Math.min(stack.getCount(), ROCK_WAKE - wake);
					stack.shrink(n);
					wake += n;
					taken += n;
					if (wake >= ROCK_WAKE) {
						awake = true;
					}
					continue;
				}
				int room = ROCK_BUFFER - buffer[0] - buffer[1] - buffer[2];
				if (room <= 0) {
					break;
				}
				int n = Math.min(stack.getCount(), room);
				stack.shrink(n);
				buffer[kind] += n;
				taken += n;
			}
		}
		if (taken <= 0) {
			Msg.chat(player, Component.translatable(awake && buffer[0] + buffer[1] + buffer[2] >= ROCK_BUFFER ? "kultivator.batin.rock.full"
					: "kultivator.batin.rock.none", ROCK_BUFFER).withStyle(ChatFormatting.YELLOW));
			return;
		}
		boolean woke = awake && !rock.getBooleanOr("awake", false);
		rock.putBoolean("awake", awake);
		rock.putInt("wake", Math.min(ROCK_WAKE, wake));
		rock.putInt("i", buffer[0]);
		rock.putInt("g", buffer[1]);
		rock.putInt("d", buffer[2]);
		Cultivators.dirty(player);
		Batin.dirty(player);
		if (woke) {
			rock.putInt("days", 0);
			LandSpirit.log(root(data), day(player), "rockmen", 0, 0);
			Advancements.grant(player, "rockmen");
			Msg.chat(player, LandSpirit.say(root(data), Component.translatable("kultivator.batin.spirit.rockmen")));
		}
		Msg.chat(player, Component.translatable(awake ? "kultivator.batin.rock.fed" : "kultivator.batin.rock.waking", taken,
				awake ? buffer[0] + buffer[1] + buffer[2] : wake, awake ? ROCK_BUFFER : ROCK_WAKE).withStyle(ChatFormatting.GREEN));
	}

	/** Takes what the rockmen made (Mid and High spirit stones, Batu Asal Abadi). */
	static void takeOutput(ServerPlayer player) {
		CompoundTag rock = section(root(Cultivators.get(player)), "rock");
		int mid = rock.getIntOr("mid", 0);
		int high = rock.getIntOr("high", 0);
		int batu = rock.getIntOr("batu", 0);
		if (mid + high + batu <= 0) {
			Msg.chat(player, Component.translatable("kultivator.batin.rock.nothing").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (mid > 0) {
			giveMany(player, SpiritStones.stack(SpiritStones.MID, 1), mid);
		}
		if (high > 0) {
			giveMany(player, SpiritStones.stack(SpiritStones.HIGH, 1), high);
		}
		Item batuItem = registryItem(BATU_ID);
		int batuLeft = batu;
		if (batu > 0 && batuItem != Items.AIR) {
			giveMany(player, new ItemStack(batuItem), batu);
			batuLeft = 0;
		}
		rock.putInt("mid", 0);
		rock.putInt("high", 0);
		rock.putInt("batu", batuLeft);
		Cultivators.dirty(player);
		Batin.dirty(player);
		Msg.chat(player, Component.translatable("kultivator.batin.rock.taken", mid, high, batu - batuLeft).withStyle(ChatFormatting.GREEN));
	}

	// ================================================================== inventory helpers

	static Item registryItem(String path) {
		return BuiltInRegistries.ITEM.getValue(Kultivator.id(path));
	}

	static int count(ServerPlayer player, Item item) {
		int n = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	static int countTag(ServerPlayer player, TagKey<Item> tag) {
		int n = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(tag)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	static void take(ServerPlayer player, Item item, int amount) {
		int left = amount;
		for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item)) {
				int n = Math.min(left, stack.getCount());
				stack.shrink(n);
				left -= n;
			}
		}
	}

	static void takeTag(ServerPlayer player, TagKey<Item> tag, int amount) {
		int left = amount;
		for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(tag)) {
				int n = Math.min(left, stack.getCount());
				stack.shrink(n);
				left -= n;
			}
		}
	}

	static @Nullable ItemStack registryStack(String path, int count) {
		Item item = registryItem(path);
		return item == Items.AIR ? null : new ItemStack(item, count);
	}

	/** {@code total} copies of {@code one}, split into full stacks. */
	static void giveMany(ServerPlayer player, ItemStack one, int total) {
		int left = total;
		int max = Math.max(1, one.getMaxStackSize());
		while (left > 0) {
			int n = Math.min(max, left);
			give(player, one.copyWithCount(n));
			left -= n;
		}
	}

	/** Into the inventory, the rest at the feet (block-safe item entity). */
	static void give(ServerPlayer player, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5D, player.getZ(), stack);
			item.setDefaultPickUpDelay();
			item.addTag(NoGrief.TAG);
			player.level().addFreshEntity(item);
		}
	}

	private InnerWorld() {
	}
}
