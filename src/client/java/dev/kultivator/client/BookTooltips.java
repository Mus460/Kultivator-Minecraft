package dev.kultivator.client;

import dev.kultivator.client.fx.DaoFx;
import dev.kultivator.dao.Comprehension;
import dev.kultivator.dao.TechniqueBooks;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Technique;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Tooltip lines on vanilla enchanted books (technique books, DESIGN 7): one line per technique the book teaches, with
 * its Dao, realm and comprehension requirement, in the Dao's colour, greyed while the player cannot learn it yet. A
 * book that teaches nothing says it still gives some cultivation. Only shown when the server has the mod.
 */
public final class BookTooltips {

	private static final int LOCKED_COLOR = 0x8A8A8A;

	public static void init() {
		ItemTooltipCallback.EVENT.register(BookTooltips::addLines);
		// The effect dispatcher has no tick hook for the Dao ring, so its animation hooks itself in here.
		DaoFx.init();
	}

	private static void addLines(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
		if (stack.isEmpty() || !stack.is(Items.ENCHANTED_BOOK) || !ClientCultivation.received()) {
			return;
		}
		List<Technique> taught = TechniqueBooks.techniquesOf(stack);
		if (taught.isEmpty()) {
			lines.add(Component.translatable("kultivator.dao.tooltip.plain", Long.toString(Math.round(TechniqueBooks.PLAIN_BOOK_XP)))
					.withStyle(ChatFormatting.GRAY));
			return;
		}
		CultivationData data = ClientCultivation.data();
		for (Technique technique : taught) {
			lines.add(line(data, technique));
		}
	}

	/** "Technique book: Thousand Swords (Sword Dao, Nascent Soul, Comprehension 30%) — Sneak + use to comprehend". */
	private static MutableComponent line(CultivationData data, Technique technique) {
		Dao dao = technique.dao();
		Component realm = technique.minRealm().displayName();
		Component details = dao == null
				? Component.translatable("kultivator.dao.tooltip.details_universal", realm)
				: technique.minComprehension() > 0.0D
				? Component.translatable("kultivator.dao.tooltip.details_dao", dao.displayName(), realm,
				Comprehension.format(technique.minComprehension()))
				: Component.translatable("kultivator.dao.tooltip.details_dao_free", dao.displayName(), realm);
		MutableComponent line = Component.translatable("kultivator.dao.tooltip.book", technique.displayName(), details);
		if (TechniqueBooks.canLearn(data, technique)) {
			return line.append(Component.translatable("kultivator.dao.tooltip.learn")).withColor(TechniqueBooks.color(technique));
		}
		Dao insight = TechniqueBooks.insightDao(data, technique);
		if (insight != null) {
			String key = data.knows(technique) ? "kultivator.dao.tooltip.known" : "kultivator.dao.tooltip.insight";
			return line.append(Component.translatable(key, Comprehension.format(Comprehension.gainFor(data, insight, TechniqueBooks.INSIGHT))))
					.withColor(TechniqueBooks.color(technique));
		}
		String key = data.knows(technique) ? "kultivator.dao.tooltip.known_no_dao" : "kultivator.dao.tooltip.locked";
		return line.append(Component.translatable(key)).withColor(LOCKED_COLOR);
	}

	private BookTooltips() {
	}
}
