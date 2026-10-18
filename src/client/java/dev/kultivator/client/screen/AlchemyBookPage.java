package dev.kultivator.client.screen;

import dev.kultivator.alchemy.Alchemy;
import dev.kultivator.alchemy.AlchemyFurnace;
import dev.kultivator.alchemy.AlchemyRecipes;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Kitab Alkimia tablet (DESIGN 19.3.7, {@code CultivationScreen.TAB_ALKIMIA}, also {@code /kultivasi resep}). A
 * header with the alchemy level, the XP to the next level and the success chance on a campfire and a soul campfire
 * (with the Fire Dao when the player walks it, {@code AlchemyFurnace.successChance}); a card per
 * {@code AlchemyRecipes.all()} entry (output icon and count, ingredient icons with counts, the required core, a colour
 * band, the alchemy XP, and a check mark with "Bisa diracik 2×" when the inventory holds every ingredient); a footer on
 * catalysts and auxiliary herbs. Scrolls with the wheel; hovering an icon names it.
 * <p>
 * Sources: Yue Lu Hall stores pill recipes (RMJ ch. 153); a sixth-grade alchemist (BTTH ch. 9). The cards are game design.
 */
final class AlchemyBookPage extends Page {

	private static final float CARD_H = 31.0F;
	private static final float GAP = 3.0F;
	private static final float HEADER_H = 25.0F;

	private final Eased scrollShown = new Eased(14.0F);
	private float scroll;
	private float contentH;

	AlchemyBookPage(CultivationScreen screen) {
		super(screen);
	}

	@Override
	Component title() {
		return Component.translatable("kultivator.spine7.tab.alkimia");
	}

	@Override
	Component shortName() {
		return Component.translatable("kultivator.spine7.tab.alkimia.short");
	}

	@Override
	protected void build() {
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		CultivationData data = data();
		drawHeader(g, data);

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

		int top = Math.round(y + HEADER_H);
		int bottom = y + h;
		float listH = bottom - top;
		float maxScroll = Math.max(0.0F, contentH - listH);
		scroll = Math.max(0.0F, Math.min(maxScroll, scroll));
		float offset = scrollShown.to(scroll, seconds);
		int columns = w >= 290 ? 2 : 1;
		float cardW = (w - 6.0F - (columns - 1) * GAP) / columns;
		boolean hoverList = mouseY >= top && mouseY < bottom && mouseX >= x && mouseX < x + w;

		g.enableScissor(x, top, x + w, bottom);
		List<AlchemyRecipes.Recipe> recipes = AlchemyRecipes.all();
		Set<Item> seenOutputs = new HashSet<>();
		float cursor = top + 1.0F - offset;
		List<Runnable> icons = new ArrayList<>();
		for (int i = 0; i < recipes.size(); i++) {
			AlchemyRecipes.Recipe recipe = recipes.get(i);
			boolean alternative = !seenOutputs.add(recipe.outputItem());
			int column = i % columns;
			float cx = x + 1.0F + column * (cardW + GAP);
			float cy = cursor + (i / columns) * (CARD_H + GAP);
			if (cy + CARD_H < top || cy > bottom) {
				continue;
			}
			drawCard(g, recipe, alternative, counts, cores, cx, cy, cardW, mouseX, mouseY, hoverList, icons);
		}
		int rows = (recipes.size() + columns - 1) / columns;
		float footerY = cursor + rows * (CARD_H + GAP) + 2.0F;
		// The items go on their own layer, over the card fills.
		g.nextStratum();
		for (Runnable icon : icons) {
			icon.run();
		}
		float footerH = drawFooter(g, footerY, w - 8.0F);
		contentH = footerY + footerH + offset - top + 4.0F;
		g.disableScissor();

		if (maxScroll > 0.0F) {
			float bead = top + 3.0F + (listH - 6.0F) * (offset / maxScroll);
			g.fill(x + w - 2, top, x + w - 1, bottom, Palette.CORD);
			Brush.disc(g, x + w - 1.5F, bead, 2.2F, Palette.JADE);
			Brush.disc(g, x + w - 2.0F, bead - 0.6F, 0.8F, Palette.JADE_PALE);
		}
	}

	/** Level, XP to the next level and the success chances (campfire, soul campfire, with the Fire Dao when walked). */
	private void drawHeader(GuiGraphicsExtractor g, CultivationData data) {
		int level = Alchemy.level(data);
		heading(g, Component.translatable("kultivator.harta.book.header", level), x + 2.0F, y + 1.0F, w - 6.0F, 0.85F);
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
		Brush.fit(g, font, line, x + 4.0F, y + 13.5F, w - 8.0F, Palette.INK_SOFT, 0.62F, 0.45F, false);
	}

	private void drawCard(GuiGraphicsExtractor g, AlchemyRecipes.Recipe recipe, boolean alternative, Map<Item, Integer> counts,
						  List<ItemStack> cores, float cx, float cy, float cardW, int mouseX, int mouseY, boolean hoverList, List<Runnable> icons) {
		int color = recipe.color();
		boolean hovered = hoverList && mouseX >= cx && mouseX < cx + cardW && mouseY >= cy && mouseY < cy + CARD_H;
		int times = craftable(recipe, counts, cores);
		int x0 = Math.round(cx);
		int y0 = Math.round(cy);
		int x1 = Math.round(cx + cardW);
		int y1 = Math.round(cy + CARD_H);
		Brush.chamferGradient(g, x0, y0, x1, y1, 2, Palette.argb(hovered ? 0xE0 : 0xB0, Palette.PAPER_TOP & 0xFFFFFF),
				Palette.argb(hovered ? 0xE0 : 0xB0, Palette.PAPER_SHADE & 0xFFFFFF));
		Brush.rect(g, x0, y0 + 1, x0 + 3, y1 - 1, Palette.argb(0xFF, Palette.onPaper(color)));
		Brush.frame(g, x0, y0, x1, y1, Palette.argb(times > 0 ? 0xC0 : 0x70, times > 0 ? Palette.JADE & 0xFFFFFF : Palette.GOLD_DARK & 0xFFFFFF));

		// Output icon and count.
		ItemStack output = new ItemStack(recipe.outputItem(), Math.max(1, recipe.count()));
		float ox = cx + 5.0F;
		float oy = cy + 2.0F;
		icons.add(() -> icon(g, output, ox, oy, 1.0F, true));
		hoverIcon(output, ox, oy, 16.0F, mouseX, mouseY, hoverList);

		// Name, XP and the check mark.
		float textX = cx + 24.0F;
		float right = cx + cardW - 3.0F;
		Component xp = Component.translatable("kultivator.harta.book.card_xp", Msg.number(recipe.xp()));
		float xpW = font.width(xp) * 0.55F;
		Brush.text(g, font, xp, right - xpW, cy + 3.0F, Palette.INK_FAINT, 0.55F, false);
		Component name = recipe.displayName();
		if (alternative) {
			name = Component.translatable("kultivator.harta.book.alternative", name);
		}
		Brush.fit(g, font, Brush.bold(name).copy().withColor(Palette.onPaper(color)), textX, cy + 2.5F, Math.max(10.0F, right - xpW - 3.0F - textX),
				Palette.INK, 0.7F, 0.5F, false);
		if (times > 0) {
			Component can = cardW >= 150.0F ? Component.translatable("kultivator.harta.book.craftable", times)
					: Component.translatable("kultivator.harta.book.craftable_short", times);
			Component mark = Component.literal("✔ ").append(can);
			float markW = font.width(mark) * 0.55F;
			Brush.text(g, font, mark, right - markW, cy + CARD_H - 8.0F, Palette.JADE_DARK, 0.55F, false);
		}

		// Ingredients (and the required core).
		float ix = textX;
		float iy = cy + 13.0F;
		float iconScale = 0.7F;
		float limit = right - (times > 0 ? 34.0F : 0.0F);
		for (Map.Entry<Item, Integer> entry : recipe.ingredients().entrySet()) {
			ItemStack stack = new ItemStack(entry.getKey());
			if (ix + 11.0F > limit) {
				Brush.text(g, font, Component.literal("…"), ix, iy + 3.0F, Palette.INK_SOFT, 0.6F, false);
				ix = Float.MAX_VALUE;
				break;
			}
			float fx = ix;
			icons.add(() -> icon(g, stack, fx, iy, iconScale, false));
			hoverIcon(stack, fx, iy, 11.5F, mouseX, mouseY, hoverList);
			boolean have = counts.getOrDefault(entry.getKey(), 0) >= entry.getValue();
			if (!have) {
				Brush.rect(g, fx, iy + 11.5F, fx + 11.0F, iy + 12.3F, Palette.argb(0xB0, Palette.DANGER & 0xFFFFFF));
			}
			ix += 12.0F;
			if (entry.getValue() > 1) {
				Component many = Component.literal("×" + entry.getValue());
				Brush.text(g, font, many, ix - 1.0F, iy + 4.0F, Palette.INK_SOFT, 0.55F, false);
				ix += font.width(many) * 0.55F + 1.0F;
			}
		}
		if (recipe.core() != null && ix < limit - 11.0F) {
			AlchemyRecipes.CoreNeed need = recipe.core();
			ItemStack core = BeastCores.create(need.element(), need.minGrade());
			Brush.text(g, font, Component.literal("+"), ix, iy + 3.0F, Palette.SEAL_DARK, 0.7F, false);
			float fx = ix + 5.0F;
			icons.add(() -> icon(g, core, fx, iy, iconScale, false));
			if (hoverList && mouseX >= fx && mouseX < fx + 11.5F && mouseY >= iy && mouseY < iy + 11.5F) {
				screen.showTooltip(List.of(Component.translatable("kultivator.harta.book.core_need", need.element().displayName(),
						BeastCores.gradeName(need.minGrade())).withColor(need.element().color())));
			}
			if (!hasCore(need, cores)) {
				Brush.rect(g, fx, iy + 11.5F, fx + 11.0F, iy + 12.3F, Palette.argb(0xB0, Palette.DANGER & 0xFFFFFF));
			}
		}

		if (hovered) {
			List<Component> tip = new ArrayList<>();
			tip.add(recipe.displayName());
			tip.add(AlchemyRecipes.ingredientList(recipe).withStyle(ChatFormatting.GRAY));
			tip.add(Component.translatable("kultivator.harta.book.card_xp", Msg.number(recipe.xp())).withStyle(ChatFormatting.DARK_GRAY));
			tip.add(times > 0 ? Component.translatable("kultivator.harta.book.craftable", times).withStyle(ChatFormatting.GREEN)
					: Component.translatable("kultivator.harta.book.missing").withStyle(ChatFormatting.RED));
			screen.showTooltip(tip);
		}
	}

	/** The footer: what stones, a catalyst core and an auxiliary herb do in the pot (DESIGN 19.3.7). */
	private float drawFooter(GuiGraphicsExtractor g, float fy, float width) {
		heading(g, Component.translatable("kultivator.harta.book.footer.title"), x + 2.0F, fy, width, 0.8F);
		float used = 12.0F;
		Component text = Component.translatable("kultivator.harta.book.footer",
				HartaText.percent(SpiritStones.LOW_CATALYST_CHANCE), HartaText.percent(SpiritStones.MID_CATALYST_CHANCE),
				HartaText.decimal(SpiritStones.MID_CATALYST_QUALITY), HartaText.decimal(SpiritHerbs.AUX_QUALITY_CAP),
				HartaText.percent(BeastCores.CATALYST_CHANCE[0]), HartaText.percent(BeastCores.CATALYST_CHANCE[3]),
				HartaText.percent(AlchemyFurnace.MAX_CHANCE));
		used += Brush.wrap(g, font, text, x + 5.0F, fy + used, width - 3.0F, Palette.INK_SOFT, 0.6F, false);
		return used;
	}

	private void icon(GuiGraphicsExtractor g, ItemStack stack, float ix, float iy, float scale, boolean decorations) {
		g.pose().pushMatrix();
		g.pose().translate(ix, iy);
		g.pose().scale(scale, scale);
		g.item(stack, 0, 0);
		if (decorations) {
			g.itemDecorations(font, stack, 0, 0);
		}
		g.pose().popMatrix();
	}

	private void hoverIcon(ItemStack stack, float ix, float iy, float size, int mouseX, int mouseY, boolean hoverList) {
		if (hoverList && mouseX >= ix && mouseX < ix + size && mouseY >= iy && mouseY < iy + size) {
			screen.showTooltip(List.of(stack.getHoverName()));
		}
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

	@Override
	boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (inside(mouseX, mouseY)) {
			scroll -= (float) amount * 16.0F;
			return true;
		}
		return false;
	}
}
