package dev.kultivator.client.abadi;

import dev.kultivator.client.dunia.DuniaPage;
import dev.kultivator.client.dunia.DuniaScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.network.Actions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.List;

/**
 * The Lelang tablet of the Keabadian screen (DESIGN 26.10, 26.8, owned by P9-langit): the session clock (or the time until the
 * next session), the five lot cards with base price, current price and leading bidder (the lot on the block is marked, with
 * its countdown), the +5% / +10% / +25% talismans for that lot and the two consignment slots with a "consign the item in hand"
 * talisman. Bids only count while the viewer is in Alam Abadi. Reads {@code AbadiClient.lelang()}.
 */
public class LelangPage extends DuniaPage {

	private static final int[] STEPS = {5, 10, 25};
	private static final int ROW = 15;
	private static final int TOP = 24;

	public LelangPage(DuniaScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine9.tab.lelang");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine9.tab.lelang.short");
	}

	@Override
	public int icon() {
		return Glyphs.TREASURE;
	}

	private static CompoundTag page() {
		return AbadiClient.lelang();
	}

	private static CompoundTag currentLot() {
		CompoundTag page = page();
		ListTag lots = page.getListOrEmpty("lots");
		int current = page.getIntOr("current", -1);
		return current >= 0 && current < lots.size() && lots.get(current) instanceof CompoundTag lot ? lot : new CompoundTag();
	}

	private static boolean canBid() {
		CompoundTag page = page();
		CompoundTag lot = currentLot();
		return page.getBooleanOr("active", false) && page.getBooleanOr("in_realm", false) && !page.getBooleanOr("banned", false)
				&& lot.getIntOr("state", 0) == 1 && !lot.getBooleanOr("mine", false);
	}

	private int buttonsY() {
		return y + TOP + ROW * 5 + 3;
	}

	@Override
	protected void build() {
		int bw = Math.max(30, Math.min(56, (w - 16) / 4));
		for (int i = 0; i < STEPS.length; i++) {
			int step = STEPS[i];
			TalismanButton button = add(new TalismanButton(x + 4 + i * (bw + 4), buttonsY(), bw, 14, () -> Component.translatable("kultivator.abadi.lelang.step", step)));
			button.enabledWhen(LelangPage::canBid);
			button.onPress(() -> {
				CompoundTag lot = currentLot();
				send(Actions.ABADI, 0, "bid:" + page().getIntOr("current", -1) + ":" + lot.getIntOr("price", 0) + ":" + step);
			});
			button.tooltip(() -> {
				CompoundTag lot = currentLot();
				int price = lot.getIntOr("price", 0);
				boolean noBid = lot.getLongOr("sell", -1L) < 0L;
				int offer = noBid ? lot.getIntOr("base", 0) : (int) Math.ceil(price * (1.0D + step / 100.0D));
				return List.of(Component.translatable("kultivator.abadi.lelang.step_tip", offer, page().getIntOr("stones", 0)));
			});
		}
		int cw = Math.min(120, w - 8);
		TalismanButton consign = add(new TalismanButton(x + w - cw - 4, y + h - 18, cw, 14, () -> Component.translatable("kultivator.abadi.lelang.consign")));
		consign.enabledWhen(() -> page().getBooleanOr("active", false) && page().getBooleanOr("in_realm", false));
		consign.onPress(() -> send(Actions.ABADI, 0, "titip"));
		consign.tooltip(() -> List.of(Component.translatable("kultivator.abadi.lelang.consign_tip", page().getIntOr("consign_max", 2))));
	}

	/** Ticks as {@code m:ss}. */
	private static String clock(long ticks) {
		long seconds = (Math.max(0L, ticks) + 19L) / 20L;
		return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		CompoundTag page = page();
		heading(g, title(), x + 2.0F, y, w - 4.0F, 0.8F);
		if (page.isEmpty()) {
			Brush.wrap(g, font, Component.translatable("kultivator.abadi.page.loading"), x + 4.0F, y + 16.0F, w - 8.0F, Palette.INK_SOFT, 0.8F, false);
			return;
		}
		boolean active = page.getBooleanOr("active", false);
		Component status;
		if (!page.getBooleanOr("enabled", true)) {
			status = Component.translatable("kultivator.abadi.lelang.page.disabled");
		} else if (active) {
			status = Component.translatable("kultivator.abadi.lelang.page.running", clock(page.getLongOr("ends", 0L) - AbadiClient.age()));
		} else {
			status = Component.translatable("kultivator.abadi.lelang.page.next", clock(page.getLongOr("next", 0L) - AbadiClient.age()));
		}
		if (!page.getBooleanOr("in_realm", false)) {
			status = Component.translatable("kultivator.abadi.page.sep", status, Component.translatable("kultivator.abadi.lelang.page.only_realm"));
		}
		if (page.getBooleanOr("banned", false)) {
			status = Component.translatable("kultivator.abadi.page.sep", status, Component.translatable("kultivator.abadi.lelang.page.banned"));
		}
		Brush.fit(g, font, status, x + 4.0F, y + 13.0F, w - 8.0F, Palette.INK_SOFT, 0.7F, 0.45F, false);
		ListTag lots = page.getListOrEmpty("lots");
		int current = page.getIntOr("current", -1);
		if (lots.isEmpty()) {
			panel(g, x + 2, y + TOP, x + w - 2, y + TOP + ROW * 5);
			Brush.wrap(g, font, Component.translatable("kultivator.abadi.lelang.page.empty"), x + 6.0F, y + TOP + 4.0F, w - 12.0F, Palette.INK_FAINT, 0.7F, false);
		}
		for (int i = 0; i < lots.size() && i < 5; i++) {
			if (lots.get(i) instanceof CompoundTag lot) {
				drawLot(g, i, lot, i == current);
			}
		}
		drawConsign(g, page);
	}

	private void drawLot(GuiGraphicsExtractor g, int index, CompoundTag lot, boolean onBlock) {
		int ry = y + TOP + index * ROW;
		int state = lot.getIntOr("state", 0);
		if (onBlock && state == 1) {
			g.fill(x + 2, ry, x + w - 2, ry + ROW - 1, Palette.argb(0x50, Palette.GOLD_LIGHT));
			Brush.frame(g, x + 2, ry, x + w - 2, ry + ROW - 1, Palette.argb(0xE0, Palette.GOLD));
		} else {
			g.fill(x + 2, ry, x + w - 2, ry + ROW - 1, Palette.argb(0x30, Palette.PAPER_DEEP));
		}
		Component name = lot.read("name", ComponentSerialization.CODEC).orElse(Component.empty());
		Component leader = lot.read("leader", ComponentSerialization.CODEC).orElse(Component.empty());
		Brush.fit(g, font, Component.literal((index + 1) + ". ").append(name), x + 5.0F, ry + 1.0F, w * 0.55F, state == 3 ? Palette.INK_FAINT : Palette.INK,
				0.72F, 0.45F, false);
		Component price = Component.translatable("kultivator.abadi.lelang.page.price", lot.getIntOr("price", 0), lot.getIntOr("base", 0));
		Brush.fit(g, font, price, x + 5.0F + w * 0.57F, ry + 1.0F, w * 0.40F, Palette.GOLD_DARK, 0.7F, 0.45F, false);
		Component line;
		switch (state) {
			case 2 -> line = Component.translatable("kultivator.abadi.lelang.page.sold", leader);
			case 3 -> line = Component.translatable("kultivator.abadi.lelang.state.unsold");
			case 1 -> {
				long present = Math.max(0L, lot.getLongOr("present", 0L) - AbadiClient.age());
				long sell = lot.getLongOr("sell", -1L) < 0L ? -1L : Math.max(0L, lot.getLongOr("sell", -1L) - AbadiClient.age());
				if (present > 0L && sell < 0L) {
					line = Component.translatable("kultivator.abadi.lelang.page.present", clock(present));
				} else if (sell >= 0L) {
					line = Component.translatable("kultivator.abadi.lelang.page.leading", leader, clock(sell));
				} else {
					line = Component.translatable("kultivator.abadi.lelang.page.open");
				}
			}
			default -> line = Component.translatable("kultivator.abadi.lelang.state.waiting");
		}
		int color = lot.getBooleanOr("mine", false) ? Palette.JADE_DARK : state == 2 ? Palette.SEAL_DARK : Palette.INK_SOFT;
		Brush.fit(g, font, line, x + 12.0F, ry + 8.0F, w - 18.0F, color, 0.6F, 0.4F, false);
	}

	private void drawConsign(GuiGraphicsExtractor g, CompoundTag page) {
		float ly = buttonsY() + 18.0F;
		float bottom = y + h - 20.0F;
		if (ly > bottom - 8.0F) {
			return;
		}
		Brush.fit(g, font, Component.translatable("kultivator.abadi.lelang.page.consign", page.getIntOr("consign_max", 2)), x + 4.0F, ly, w - 8.0F,
				Palette.SEAL_DARK, 0.7F, 0.45F, false);
		ly += 9.0F;
		ListTag consign = page.getListOrEmpty("consign");
		if (consign.isEmpty() && ly < bottom - 7.0F) {
			Brush.fit(g, font, Component.translatable("kultivator.abadi.lelang.page.consign_none"), x + 8.0F, ly, w - 12.0F, Palette.INK_FAINT, 0.65F, 0.4F, false);
		}
		for (Tag tag : consign) {
			if (!(tag instanceof CompoundTag entry) || ly > bottom - 7.0F) {
				break;
			}
			Component name = entry.read("name", ComponentSerialization.CODEC).orElse(Component.empty());
			Brush.fit(g, font, Component.translatable("kultivator.abadi.lelang.page.consign_line", name, entry.getIntOr("value", 0)), x + 8.0F, ly, w - 12.0F,
					Palette.INK, 0.65F, 0.4F, false);
			ly += 8.0F;
		}
	}
}
