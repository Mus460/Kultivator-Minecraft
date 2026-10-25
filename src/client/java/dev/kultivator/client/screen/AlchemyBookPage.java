package dev.kultivator.client.screen;

import dev.kultivator.alchemy.Alchemy;
import dev.kultivator.alchemy.AlchemyFurnace;
import dev.kultivator.alchemy.AlchemyRecipes;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Dao;
import dev.kultivator.harta.BeastCores;
import dev.kultivator.harta.HartaText;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Diri › Alkimia (DESIGN 19.3.7, 43.5; also {@code /kultivasi resep}). A header with the alchemy level, the XP to the
 * next level and the success chance on a campfire and a soul campfire (with the Fire Dao when the player walks it,
 * {@code AlchemyFurnace.successChance}); a card per {@code AlchemyRecipes.all()} entry (output icon and count, ingredient
 * icons with counts, the required core, a colour band, the alchemy XP, and a check mark with "Bisa diracik 2×" when the
 * inventory holds every ingredient); after the cards the note on catalysts and auxiliary herbs. The cards and the note
 * are one {@code DaftarGulir} (wheel, rail, ↑↓ after Tab; a grid of two columns on a wide page); hovering a card or an
 * icon names it. All text is drawn at 1.0 (43.0.5).
 * <p>
 * Sources: Yue Lu Hall stores pill recipes (RMJ ch. 153); a sixth-grade alchemist (BTTH ch. 9). The cards are game design.
 */
final class AlchemyBookPage extends Page {

	private static final int CARD_H = 35;
	/** A card is at least this wide; a wider list holds two columns. */
	private static final int CARD_MIN_W = 170;
	private static final int NOTE_LINE_H = 10;
	private static final int HEADING_H = 12;

	/** The note under the cards: its heading, then its lines wrapped to the list width. */
	private final List<FormattedCharSequence> noteLines = new ArrayList<>();
	private final DaftarGulir list = new DaftarGulir().judul(i -> i >= AlchemyRecipes.all().size(), NOTE_LINE_H);
	private int headerH = HEADING_H + 10;
	/** The header line when it needed more than two lines (shown whole as the header's tooltip), else null. */
	private @Nullable Component headerCut;
	private int noteWidth = -1;

	AlchemyBookPage(CultivationScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine7.tab.alkimia");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine7.tab.alkimia.short");
	}

	@Override
	public String helpTopic() {
		return "alchemy";
	}

	@Override
	protected void build() {
		noteWidth = -1;
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		headerH = drawHeader(g, data());
	}

	/** Level, XP to the next level and the success chances (campfire, soul campfire, with the Fire Dao when walked); returns its height. */
	private int drawHeader(GuiGraphicsExtractor g, CultivationData data) {
		int level = Alchemy.level(data);
		heading(g, Component.translatable("kultivator.harta.book.header", level), x + 2.0F, y + 1.0F, w - 6.0F, 1.0F);
		boolean fire = data.dao() == Dao.FIRE;
		Component xp = level >= Alchemy.MAX_LEVEL
				? Component.translatable("kultivator.harta.book.xp_max", Msg.number(data.alchemyXp()))
				: Component.translatable("kultivator.harta.book.xp", Msg.number(data.alchemyXp()), Msg.number(Alchemy.xpForLevel(level + 1)));
		Component chance = Component.translatable("kultivator.harta.book.chance",
				HartaText.percent(AlchemyFurnace.successChance(level, false, fire)),
				HartaText.percent(AlchemyFurnace.successChance(level, true, fire)));
		Component line = fire
				? Component.translatable("kultivator.harta.book.header_line_fire", xp, chance, HartaText.percent(AlchemyFurnace.FIRE_DAO_CHANCE))
				: Component.translatable("kultivator.harta.book.header_line", xp, chance);
		// At most two lines at 1.0; a third is cut (the whole line is in the tooltip of the cut text).
		List<FormattedCharSequence> lines = font.split(line, Math.max(10, w - 8));
		headerCut = lines.size() > 2 ? line : null;
		if (lines.size() <= 1) {
			Brush.fit(g, font, line, x + 4.0F, y + HEADING_H + 1.0F, w - 8.0F, Palette.INK_SOFT, 1.0F, 1.0F, false);
			return HEADING_H + 12;
		}
		Brush.text(g, font, lines.get(0), x + 4.0F, y + HEADING_H + 1.0F, Palette.INK_SOFT, 1.0F, false);
		Brush.text(g, font, lines.get(1), x + 4.0F, y + HEADING_H + 10.0F, Palette.INK_SOFT, 1.0F, false);
		return HEADING_H + 21;
	}

	// ================================================================== the list

	@Override
	protected void lukis(Lukis l) {
		if (headerCut != null && l.di(x, y, x + w, y + headerH)) {
			l.tip(headerCut);
		}
		Inventory inventory = inventory();
		Map<Item, Integer> counts = new HashMap<>();
		List<ItemStack> cores = new ArrayList<>();
		if (inventory != null) {
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (stack.isEmpty()) {
					continue;
				}
				counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
				if (BeastCores.isCore(stack)) {
					cores.add(stack);
				}
			}
		}
		List<AlchemyRecipes.Recipe> recipes = AlchemyRecipes.all();
		int top = y + headerH;
		int noteW = w - 14;
		if (noteW != noteWidth) {
			noteWidth = noteW;
			rebuildNote(noteW);
		}
		// Which recipes repeat an output already listed ("cara lain").
		boolean[] alternative = new boolean[recipes.size()];
		Set<Item> seen = new HashSet<>();
		for (int i = 0; i < recipes.size(); i++) {
			alternative[i] = !seen.add(recipes.get(i).outputItem());
		}
		int total = recipes.size() + 1 + noteLines.size();
		list.gambarKisi(l, "resep", x, top, x + w, y + h, total, CARD_MIN_W, CARD_H, (lk, i, x0, y0, x1, y1, over, chosen) -> {
			if (i < recipes.size()) {
				drawCard(lk, recipes.get(i), alternative[i], counts, cores, x0, y0, x1, y1, over || chosen && lk.difokus("resep"));
			} else if (i == recipes.size()) {
				Brush.rect(lk.g, x0 + 2.0F, y0 + 3.0F, x0 + 5.0F, y0 + 6.0F, Palette.SEAL);
				lk.teks(Brush.bold(Component.translatable("kultivator.harta.book.footer.title")), x0 + 7.0F, y0 + 1.0F, x1 - x0 - 9.0F, Palette.SEAL_DARK);
				Brush.rect(lk.g, x0 + 2.0F, y1 - 0.6F, x1 - 2.0F, y1, Palette.argb(0xA0, Palette.GOLD));
			} else {
				Brush.text(lk.g, font, noteLines.get(i - recipes.size() - 1), x0 + 5.0F, y0 + 1.0F, Palette.INK_SOFT, 1.0F, false);
			}
		}, null, null);
	}

	/** The note: what stones, a catalyst core and an auxiliary herb do in the pot (DESIGN 19.3.7), wrapped to {@code width}. */
	private void rebuildNote(int width) {
		noteLines.clear();
		Component text = Component.translatable("kultivator.harta.book.footer",
				HartaText.percent(SpiritStones.LOW_CATALYST_CHANCE), HartaText.percent(SpiritStones.MID_CATALYST_CHANCE),
				HartaText.decimal(SpiritStones.MID_CATALYST_QUALITY), HartaText.decimal(SpiritHerbs.AUX_QUALITY_CAP),
				HartaText.percent(BeastCores.CATALYST_CHANCE[0]), HartaText.percent(BeastCores.CATALYST_CHANCE[3]),
				HartaText.percent(AlchemyFurnace.MAX_CHANCE));
		noteLines.addAll(font.split(text, Math.max(10, width)));
	}

	private void drawCard(Lukis l, AlchemyRecipes.Recipe recipe, boolean alternative, Map<Item, Integer> counts, List<ItemStack> cores,
						  int x0, int y0, int x1, int y1, boolean hovered) {
		GuiGraphicsExtractor g = l.g;
		int color = recipe.color();
		int times = craftable(recipe, counts, cores);
		Brush.chamferGradient(g, x0, y0, x1, y1, 2, Palette.argb(hovered ? 0xE0 : 0xB0, Palette.PAPER_TOP & 0xFFFFFF),
				Palette.argb(hovered ? 0xE0 : 0xB0, Palette.PAPER_SHADE & 0xFFFFFF));
		Brush.rect(g, x0, y0 + 1, x0 + 3, y1 - 1, Palette.argb(0xFF, Palette.onPaper(color)));
		Brush.frame(g, x0, y0, x1, y1, Palette.argb(times > 0 ? 0xC0 : 0x70, times > 0 ? Palette.JADE & 0xFFFFFF : Palette.GOLD_DARK & 0xFFFFFF));

		// Output icon and count.
		ItemStack output = new ItemStack(recipe.outputItem(), Math.max(1, recipe.count()));
		int ox = x0 + 5;
		int oy = y0 + 3;
		g.item(output, ox, oy);
		g.itemDecorations(font, output, ox, oy);
		boolean iconTip = hoverIcon(l, output, ox, oy, 16);

		// Name and XP on the first line.
		int textX = x0 + 25;
		int right = x1 - 3;
		Component xp = Component.translatable("kultivator.harta.book.card_xp", Msg.number(recipe.xp()));
		int xpW = font.width(xp);
		boolean showXp = right - textX - xpW >= 60;
		if (showXp) {
			Brush.text(g, font, xp, right - xpW, y0 + 2.0F, Palette.INK_FAINT, 1.0F, false);
		}
		Component name = recipe.displayName();
		if (alternative) {
			name = Component.translatable("kultivator.harta.book.alternative", name);
		}
		Brush.fit(g, font, Brush.bold(name).copy().withColor(Palette.onPaper(color)), textX, y0 + 2.0F,
				Math.max(10.0F, (showXp ? right - xpW - 3.0F : right) - textX), Palette.INK, 1.0F, 1.0F, false);

		// Ingredients (and the required core) on the second line; the item icons keep their small size, counts are 1.0.
		int ix = textX;
		int iy = y0 + 12;
		int limit = right;
		for (Map.Entry<Item, Integer> entry : recipe.ingredients().entrySet()) {
			ItemStack stack = new ItemStack(entry.getKey());
			Component many = entry.getValue() > 1 ? Component.literal("×" + entry.getValue()) : null;
			int need = 12 + (many == null ? 0 : font.width(many) + 1);
			if (ix + need > limit) {
				Brush.text(g, font, Component.literal("…"), ix, iy + 2.0F, Palette.INK_SOFT, 1.0F, false);
				ix = Integer.MAX_VALUE / 2;
				break;
			}
			smallIcon(g, stack, ix, iy);
			iconTip |= hoverIcon(l, stack, ix, iy, 12);
			if (counts.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
				Brush.rect(g, ix, iy + 11.5F, ix + 11.0F, iy + 12.3F, Palette.argb(0xB0, Palette.DANGER & 0xFFFFFF));
			}
			ix += 12;
			if (many != null) {
				Brush.text(g, font, many, ix, iy + 2.0F, Palette.INK_SOFT, 1.0F, false);
				ix += font.width(many) + 2;
			}
		}
		if (recipe.core() != null && ix + 18 <= limit) {
			AlchemyRecipes.CoreNeed need = recipe.core();
			ItemStack core = BeastCores.create(need.element(), need.minGrade());
			Brush.text(g, font, Component.literal("+"), ix, iy + 2.0F, Palette.SEAL_DARK, 1.0F, false);
			int fx = ix + 6;
			smallIcon(g, core, fx, iy);
			if (l.di(fx, iy, fx + 12, iy + 12)) {
				l.tip(List.of(Component.translatable("kultivator.harta.book.core_need", need.element().displayName(),
						BeastCores.gradeName(need.minGrade())).withColor(need.element().color())));
				iconTip = true;
			}
			if (!hasCore(need, cores)) {
				Brush.rect(g, fx, iy + 11.5F, fx + 11.0F, iy + 12.3F, Palette.argb(0xB0, Palette.DANGER & 0xFFFFFF));
			}
		}

		// The verdict on the third line.
		Component verdict = times > 0
				? Component.literal("✔ ").append(x1 - x0 >= 150 ? Component.translatable("kultivator.harta.book.craftable", times)
				: Component.translatable("kultivator.harta.book.craftable_short", times))
				: Component.translatable("kultivator.harta.book.missing");
		Brush.fit(g, font, verdict, textX, y0 + 24.0F, right - textX, times > 0 ? Palette.JADE_DARK : Palette.INK_FAINT, 1.0F, 1.0F, false);

		if (hovered && !iconTip && l.di(x0, y0, x1, y1)) {
			List<Component> tip = new ArrayList<>();
			tip.add(recipe.displayName());
			tip.add(AlchemyRecipes.ingredientList(recipe).withStyle(ChatFormatting.GRAY));
			tip.add(Component.translatable("kultivator.harta.book.card_xp", Msg.number(recipe.xp())).withStyle(ChatFormatting.DARK_GRAY));
			tip.add(times > 0 ? Component.translatable("kultivator.harta.book.craftable", times).withStyle(ChatFormatting.GREEN)
					: Component.translatable("kultivator.harta.book.missing").withStyle(ChatFormatting.RED));
			l.tip(tip);
		}
	}

	/** An ingredient icon at 0.7 (an item picture, not text). */
	private static void smallIcon(GuiGraphicsExtractor g, ItemStack stack, int ix, int iy) {
		g.pose().pushMatrix();
		g.pose().translate(ix, iy);
		g.pose().scale(0.7F, 0.7F);
		g.item(stack, 0, 0);
		g.pose().popMatrix();
	}

	private static boolean hoverIcon(Lukis l, ItemStack stack, int ix, int iy, int size) {
		if (l.di(ix, iy, ix + size, iy + size)) {
			l.tip(List.of(stack.getHoverName()));
			return true;
		}
		return false;
	}

	/** How many times the inventory could fill the pot for {@code recipe} (0 = something is missing). */
	private static int craftable(AlchemyRecipes.Recipe recipe, Map<Item, Integer> counts, List<ItemStack> cores) {
		int times = Integer.MAX_VALUE;
		for (Map.Entry<Item, Integer> entry : recipe.ingredients().entrySet()) {
			times = Math.min(times, counts.getOrDefault(entry.getKey(), 0) / Math.max(1, entry.getValue()));
		}
		if (recipe.core() != null) {
			int matching = 0;
			for (ItemStack core : cores) {
				if (recipe.core().accepts(BeastCores.element(core), BeastCores.grade(core))) {
					matching += core.getCount();
				}
			}
			times = Math.min(times, matching);
		}
		return times == Integer.MAX_VALUE ? 0 : times;
	}

	private static boolean hasCore(AlchemyRecipes.CoreNeed need, List<ItemStack> cores) {
		for (ItemStack core : cores) {
			if (need.accepts(BeastCores.element(core), BeastCores.grade(core))) {
				return true;
			}
		}
		return false;
	}

	private static Inventory inventory() {
		Player player = Minecraft.getInstance().player;
		return player == null ? null : player.getInventory();
	}
}
