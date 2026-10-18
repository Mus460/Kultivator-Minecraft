package dev.kultivator.client;

import dev.kultivator.client.fx.DaoFx;
import dev.kultivator.dao.Comprehension;
import dev.kultivator.dao.DaoPaths;
import dev.kultivator.dao.TechniqueBooks;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Dao;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.technique.source.BookPools;
import dev.kultivator.technique.source.InheritanceTrials;
import dev.kultivator.technique.source.JadeSlips;
import dev.kultivator.technique.source.LearnRules;
import dev.kultivator.technique.source.TechniqueLoot;
import dev.kultivator.technique.spec.run.Mastery;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.List;

/**
 * Tooltip lines that depend on the player's own cultivation (DESIGN 22.8): enchanted books list their technique pool in
 * grade order with the art they would teach and the lock reason of each, or the insight they would give; jade slips,
 * inheritance jades, Heavenly Flame seeds and pages of the Sealing Demon Codex say whether (and why not) they can be used
 * now. Only shown when the server has the mod.
 */
public final class BookTooltips {

	private static final int LOCKED_COLOR = 0x8A8A8A;
	private static final int OK_COLOR = 0x8CE08C;
	/** At most this many pool lines per book (the rest is "+n more"). */
	private static final int POOL_LINES = 6;
	private static final String PREFIX = "kultivator.sumber.tooltip.";

	public static void init() {
		ItemTooltipCallback.EVENT.register(BookTooltips::addLines);
		// The effect dispatcher has no tick hook for the Dao ring, so its animation hooks itself in here.
		DaoFx.init();
	}

	private static void addLines(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
		if (stack.isEmpty() || !ClientCultivation.received()) {
			return;
		}
		CultivationData data = ClientCultivation.data();
		if (stack.is(Items.ENCHANTED_BOOK)) {
			book(data, stack, lines);
		} else if (stack.is(JadeSlips.JADE_SLIP)) {
			slip(data, stack, lines);
		} else if (stack.is(InheritanceTrials.GIOK_WARISAN)) {
			jade(data, stack, lines);
		} else if (TechniqueLoot.isFlameSeed(stack)) {
			seed(data, stack, lines);
		} else if (TechniqueLoot.scriptureSet(stack) == TechniqueLoot.SCRIPTURE_SET) {
			scripture(data, lines);
		}
	}

	// ================================================================== enchanted books

	private static void book(CultivationData data, ItemStack stack, List<Component> lines) {
		ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
		if (!BookPools.isTechniqueBook(stored)) {
			lines.add(Component.translatable("kultivator.dao.tooltip.plain", Long.toString(Math.round(TechniqueBooks.PLAIN_BOOK_XP)))
					.withStyle(ChatFormatting.GRAY));
			return;
		}
		TechniqueBooks.Reading reading = TechniqueBooks.reading(data, stored);
		lines.add(Component.translatable(PREFIX + "book.header").withStyle(ChatFormatting.GOLD));
		if (reading.teaches() != null) {
			lines.add(Component.translatable(PREFIX + "book.teaches", LearnRules.name(reading.teaches()), LearnRules.gradeLine(reading.teaches()))
					.withColor(OK_COLOR));
		} else if (reading.useful()) {
			if (reading.known() != null) {
				lines.add(Component.translatable(PREFIX + "book.mastery", TechniqueBooks.INSIGHT_MASTERY, LearnRules.name(reading.known()))
						.withColor(OK_COLOR));
			}
			Dao insight = reading.insight();
			if (insight != null) {
				lines.add(Component.translatable(PREFIX + "book.insight", Comprehension.format(Comprehension.gainFor(data, insight, TechniqueBooks.INSIGHT)),
						DaoPaths.colored(insight)).withColor(OK_COLOR));
				// Insight books per day (DESIGN 19.3.14).
				int today = insightBooksToday(data);
				lines.add(today >= dev.kultivator.harta.Harta.BOOK_INSIGHT_DAILY
						? Component.translatable("kultivator.harta.book.tooltip_capped", dev.kultivator.harta.Harta.BOOK_INSIGHT_DAILY).withColor(LOCKED_COLOR)
						: Component.translatable("kultivator.harta.book.tooltip_today", today, dev.kultivator.harta.Harta.BOOK_INSIGHT_DAILY)
						.withStyle(ChatFormatting.DARK_GRAY));
			}
		} else {
			lines.add(Component.translatable(PREFIX + "book.nothing").withColor(LOCKED_COLOR));
		}
		List<TechniqueSpec> pool = reading.pool();
		int shown = 0;
		for (TechniqueSpec spec : pool) {
			if (shown >= POOL_LINES) {
				break;
			}
			lines.add(poolLine(data, spec));
			shown++;
		}
		if (pool.size() > shown) {
			lines.add(Component.translatable(PREFIX + "book.more", pool.size() - shown).withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	/** Insight books read today ({@code extra.harta.book_day}, {@code book_insight}; 0 on another in-game day). */
	private static int insightBooksToday(CultivationData data) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return 0;
		}
		net.minecraft.nbt.CompoundTag own = data.extra().getCompoundOrEmpty(dev.kultivator.harta.Harta.EXTRA);
		long day = Math.max(0L, minecraft.level.getOverworldClockTime() / 24000L);
		return own.getLongOr(dev.kultivator.harta.Harta.BOOK_DAY, -1L) == day ? own.getIntOr(dev.kultivator.harta.Harta.BOOK_INSIGHT, 0) : 0;
	}

	/** "• Seni Pedang Aksara Rumput (Bumi Tinggi · Serang) — terkunci: capai Penyatuan Dao". */
	private static MutableComponent poolLine(CultivationData data, TechniqueSpec spec) {
		MutableComponent line = Component.translatable(PREFIX + "book.entry", spec.displayName(), LearnRules.gradeLine(spec));
		if (data.knows(spec.id())) {
			return line.append(Component.translatable(PREFIX + "known")).withColor(LearnRules.color(spec));
		}
		List<Component> missing = LearnRules.missing(data, spec);
		if (missing.isEmpty()) {
			return line.append(Component.translatable(PREFIX + "learnable")).withColor(LearnRules.color(spec));
		}
		return line.append(Component.translatable(PREFIX + "locked", missing.get(0))).withColor(LOCKED_COLOR);
	}

	// ================================================================== jade slips, jades, seeds, pages

	private static void slip(CultivationData data, ItemStack stack, List<Component> lines) {
		TechniqueSpec spec = JadeSlips.specOfStack(stack);
		if (spec == null) {
			return;
		}
		if (data.knows(spec.id())) {
			lines.add(Component.translatable(PREFIX + "slip.known", JadeSlips.MASTERY_DUPLICATE, LearnRules.masteryName(Mastery.level(data, spec.id())))
					.withColor(OK_COLOR));
			return;
		}
		List<Component> missing = LearnRules.missing(data, spec);
		if (missing.isEmpty()) {
			lines.add(Component.translatable(PREFIX + "slip.ready").withColor(OK_COLOR));
		} else {
			lines.add(Component.translatable(PREFIX + "slip.too_deep").withStyle(ChatFormatting.GOLD));
			for (Component part : missing) {
				lines.add(Component.translatable(PREFIX + "bullet", part).withColor(LOCKED_COLOR));
			}
		}
	}

	private static void jade(CultivationData data, ItemStack stack, List<Component> lines) {
		String theme = InheritanceTrials.themeOf(stack);
		long until = data.extra().getCompoundOrEmpty(Mastery.EXTRA).getCompoundOrEmpty(InheritanceTrials.KEY).getLongOr("cd", 0L);
		Minecraft minecraft = Minecraft.getInstance();
		long now = minecraft.level == null ? 0L : minecraft.level.getGameTime();
		if (!data.realm().atLeast(InheritanceTrials.MIN_REALM)) {
			lines.add(Component.translatable(PREFIX + "jade.realm", InheritanceTrials.MIN_REALM.displayName()).withColor(LOCKED_COLOR));
		} else if (until > now) {
			lines.add(Component.translatable(PREFIX + "jade.cooldown", Math.max(1L, (until - now + 1199L) / 1200L)).withColor(LOCKED_COLOR));
		} else if (Dao.byId(theme) != null && Dao.byId(theme) != data.dao()) {
			lines.add(Component.translatable(PREFIX + "jade.dao").withColor(LOCKED_COLOR));
		} else {
			lines.add(Component.translatable(PREFIX + "jade.ready").withColor(OK_COLOR));
		}
	}

	private static void seed(CultivationData data, ItemStack stack, List<Component> lines) {
		for (TechniqueSpec spec : TechniqueLoot.seedSpecs(stack)) {
			MutableComponent line = Component.translatable(PREFIX + "seed.teaches", LearnRules.name(spec));
			if (data.knows(spec.id())) {
				lines.add(line.append(Component.translatable(PREFIX + "known")).withColor(LOCKED_COLOR));
				continue;
			}
			List<Component> missing = LearnRules.missing(data, spec);
			lines.add(missing.isEmpty() ? line.append(Component.translatable(PREFIX + "learnable")).withColor(OK_COLOR)
					: line.append(Component.translatable(PREFIX + "locked", missing.get(0))).withColor(LOCKED_COLOR));
		}
	}

	private static void scripture(CultivationData data, List<Component> lines) {
		Minecraft minecraft = Minecraft.getInstance();
		int pages = minecraft.player == null ? 0 : TechniqueLoot.scripturePages(minecraft.player);
		lines.add(Component.translatable(PREFIX + "scripture", pages, TechniqueLoot.SCRIPTURE_PIECES).withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	private BookTooltips() {
	}
}
