package dev.kultivator.client.screen;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.Keys;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SealCard;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.dao.Comprehension;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Technique;
import dev.kultivator.network.Actions;
import dev.kultivator.technique.FlyingSword;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Techniques: every art as a seal card in a grid that wraps to the window (universal arts first, then each Dao's
 * pair). Click a learned card to select it for the cast key; locked cards tell in their tooltip how they are learned
 * (realm, Dao, or which enchanted book to comprehend).
 */
final class TechniquesPage extends Page {

	private static final int GAP = 4;
	private static final int HEADER = 12;
	private static final int FOOTER = 13;

	private final List<SealCard> cards = new ArrayList<>();
	private final List<Integer> baseY = new ArrayList<>();
	private final Eased scrollShown = new Eased(14.0F);
	private float scroll;
	private int gridX;
	private int gridY;
	private int gridW;
	private int gridH;
	private float contentH;

	TechniquesPage(CultivationScreen screen) {
		super(screen);
	}

	@Override
	Component title() {
		return Component.translatable("kultivator.screen.tab.techniques");
	}

	@Override
	Component shortName() {
		return Component.translatable("kultivator.screen.tab.techniques.short");
	}

	@Override
	protected void build() {
		cards.clear();
		baseY.clear();
		gridY = y + HEADER;
		gridH = h - HEADER - FOOTER;
		int cols = Math.max(3, (w + GAP) / (46 + GAP));
		int size = Math.max(34, Math.min(56, (w - (cols - 1) * GAP) / cols));
		gridW = cols * size + (cols - 1) * GAP;
		gridX = x + (w - gridW) / 2;
		Technique[] all = Technique.values();
		int rows = (all.length + cols - 1) / cols;
		contentH = rows * (size + GAP) - GAP + 6;
		for (int i = 0; i < all.length; i++) {
			Technique technique = all[i];
			int cx = gridX + (i % cols) * (size + GAP);
			int cy = gridY + 3 + (i / cols) * (size + GAP);
			SealCard card = new SealCard(cx, cy, size, technique);
			card.enabledWhen(() -> data().knows(technique));
			card.onPress(() -> {
				if (data().selected() != technique) {
					send(Actions.SELECT_TECHNIQUE, 0, technique.id());
				}
			});
			card.tooltip(() -> tip(technique));
			cards.add(card);
			baseY.add(cy);
			add(card);
		}
	}

	@Override
	protected boolean visible(Widget widget) {
		return widget.y + widget.h > gridY && widget.y < gridY + gridH;
	}

	@Override
	protected boolean hoverable(double mouseX, double mouseY) {
		return inside(mouseX, mouseY) && mouseY >= gridY && mouseY < gridY + gridH;
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		CultivationData data = data();
		float maxScroll = Math.max(0.0F, contentH - gridH);
		scroll = Math.max(0.0F, Math.min(maxScroll, scroll));
		int offset = Math.round(scrollShown.to(scroll, seconds));
		for (int i = 0; i < cards.size(); i++) {
			cards.get(i).y = baseY.get(i) - offset;
		}

		int learned = data.learnedTechniques().size();
		Component count = Component.translatable("kultivator.screen.tech.count", learned, Technique.values().length);
		heading(g, count, x + 2.0F, y, w * 0.45F, 0.8F);
		Component keys = Component.translatable("kultivator.screen.tech.keys", Keys.CAST.getTranslatedKeyMessage(),
				Keys.PREVIOUS.getTranslatedKeyMessage(), Keys.NEXT.getTranslatedKeyMessage());
		float keysScale = Brush.fitScale(font, keys, w * 0.5F, 0.7F, 0.45F);
		float keysWidth = Math.min(w * 0.5F, font.width(keys) * keysScale);
		Brush.fit(g, font, keys, x + w - keysWidth - 2.0F, y + 1.0F, w * 0.5F, Palette.INK_SOFT, 0.7F, 0.45F, false);

		g.enableScissor(x, gridY, x + w, gridY + gridH);
	}

	@Override
	protected void drawAbove(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		g.disableScissor();
		float maxScroll = Math.max(0.0F, contentH - gridH);
		if (maxScroll > 0.0F) {
			float track = gridH - 6.0F;
			float bead = gridY + 3.0F + track * (scrollShown.get() / maxScroll);
			g.fill(x + w - 2, gridY, x + w - 1, gridY + gridH, Palette.CORD);
			Brush.disc(g, x + w - 1.5F, bead, 2.2F, Palette.JADE);
			Brush.disc(g, x + w - 2.0F, bead - 0.6F, 0.8F, Palette.JADE_PALE);
		}
		// The selected art along the bottom.
		CultivationData data = data();
		Technique selected = data.selected();
		Component line;
		int color;
		if (selected == null) {
			line = Component.translatable("kultivator.screen.tech.none_selected");
			color = Palette.INK_FAINT;
		} else {
			line = Component.translatable("kultivator.screen.tech.selected", selected.displayName(), selected.qiCost(),
					SealCard.seconds(selected.cooldownTicks()));
			color = selected.dao() == null ? Palette.JADE_DARK : Palette.onPaper(selected.dao().color());
		}
		Brush.rect(g, x + 2.0F, y + h - FOOTER + 1.0F, x + w - 2.0F, y + h - FOOTER + 1.6F, Palette.argb(0xA0, Palette.GOLD));
		Brush.fitCentered(g, font, line, x + w / 2.0F, y + h - FOOTER + 4.0F, w - 6.0F, color, 0.8F, 0.5F, false);
	}

	@Override
	boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (mouseY < gridY || mouseY >= gridY + gridH) {
			return false;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (inside(mouseX, mouseY)) {
			scroll -= (float) amount * 18.0F;
			return true;
		}
		return false;
	}

	private static List<Component> tip(Technique technique) {
		CultivationData data = data();
		Dao dao = technique.dao();
		List<Component> lines = new ArrayList<>();
		lines.add(technique.displayName().copy().withColor(dao == null ? 0xFFE6B3 : dao.color()));
		lines.add(technique.description().copy().withStyle(ChatFormatting.GRAY));
		boolean flying = technique == Technique.FLYING_SWORD;
		String costKey = flying ? "kultivator.screen.tech.cost_per_second" : "kultivator.screen.tech.cost";
		Object cost = flying ? Comprehension.format(FlyingSword.drainPerSecond(data)) : technique.qiCost();
		lines.add(Component.translatable(costKey, cost, SealCard.seconds(technique.cooldownTicks())).withColor(0x8FD0FF));
		lines.add(dao == null ? Component.translatable("kultivator.screen.tech.universal").withStyle(ChatFormatting.DARK_AQUA)
				: Component.translatable("kultivator.screen.tech.dao", dao.displayName()).withColor(dao.color()));
		if (data.knows(technique)) {
			long left = ClientCultivation.cooldownLeft(technique);
			if (left > 0L) {
				lines.add(Component.translatable("kultivator.screen.tech.cooling", SealCard.seconds(left)).withStyle(ChatFormatting.RED));
			}
			lines.add(technique == data.selected()
					? Component.translatable("kultivator.screen.tech.is_selected", Keys.CAST.getTranslatedKeyMessage()).withStyle(ChatFormatting.GOLD)
					: Component.translatable("kultivator.screen.tech.click").withStyle(ChatFormatting.YELLOW));
			return lines;
		}
		lines.add(Component.translatable("kultivator.screen.tech.locked").withStyle(ChatFormatting.RED));
		Component realm = technique.minRealm().displayName().copy().withColor(technique.minRealm().color());
		switch (technique.unlock()) {
			case REALM -> lines.add(Component.translatable("kultivator.screen.tech.unlock_realm", realm));
			case DAO -> lines.add(Component.translatable("kultivator.screen.tech.unlock_dao",
					dao == null ? Component.empty() : dao.displayName().copy().withColor(dao.color()), realm));
			case BOOK -> {
				lines.add(Component.translatable("kultivator.screen.tech.unlock_book", books(technique), realm));
				if (dao != null) {
					lines.add(Component.translatable("kultivator.screen.tech.unlock_book_dao", dao.displayName().copy().withColor(dao.color()),
							Math.round(technique.minComprehension())));
				}
			}
		}
		return lines;
	}

	/** The enchantments whose books teach this technique (DESIGN 7), joined with "or". */
	private static Component books(Technique technique) {
		String[] ids;
		if (technique == Technique.BUDDHA_PALM) {
			ids = new String[]{"smite"};
		} else if (technique.dao() == null) {
			ids = new String[0];
		} else {
			ids = switch (technique.dao()) {
				case SWORD -> new String[]{"sharpness", "sweeping_edge"};
				case FIRE -> new String[]{"fire_aspect", "flame"};
				case ICE -> new String[]{"frost_walker"};
				case LIGHTNING -> new String[]{"channeling"};
				case BODY -> new String[]{"protection", "thorns"};
				case LIFE -> new String[]{"mending"};
				case SHADOW -> new String[]{"swift_sneak", "feather_falling"};
				case SPACE -> new String[]{"infinity", "loyalty"};
			};
		}
		MutableComponent joined = Component.empty();
		for (int i = 0; i < ids.length; i++) {
			if (i > 0) {
				joined.append(Component.translatable("kultivator.screen.tech.or"));
			}
			joined.append(Component.translatable("enchantment.minecraft." + ids[i]).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		return joined;
	}
}
