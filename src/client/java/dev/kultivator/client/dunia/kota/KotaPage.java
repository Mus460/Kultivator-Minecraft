package dev.kultivator.client.dunia.kota;

import dev.kultivator.client.dunia.DuniaPage;
import dev.kultivator.client.dunia.DuniaScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Widget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Base of module K's pages: reads its tab's compound of {@link KotaClient#data()}, sends actions, and draws the shared
 * pieces (the stone purse line, item cards, progress bars, the "go there" placeholder). Item icons are made once per
 * item id (lazily, never in a static initialiser).
 */
abstract class KotaPage extends DuniaPage {

	private static final Map<String, ItemStack> ICONS = new HashMap<>();

	protected KotaPage(DuniaScreen screen) {
		super(screen);
	}

	/** The whole screen data. */
	protected static CompoundTag all() {
		return KotaClient.data();
	}

	protected static CompoundTag tab(String key) {
		return KotaClient.tab(key);
	}

	protected static Component read(CompoundTag tag, String key) {
		return tag.read(key, ComponentSerialization.CODEC).orElse(Component.empty());
	}

	protected static void act(String op) {
		KotaClient.send(op);
	}

	/** An item icon by id ("kultivator:foundation_pill"). */
	protected static ItemStack icon(String id) {
		return ICONS.computeIfAbsent(id, key -> {
			Identifier parsed = Identifier.tryParse(key);
			Item item = parsed == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(parsed);
			return item == Items.AIR ? new ItemStack(Items.PAPER) : new ItemStack(item);
		});
	}

	/** "Batu: 1 234 NB · Nama Baik 12" under the heading. */
	protected void purse(GuiGraphicsExtractor g, float py) {
		CompoundTag data = all();
		Component line = Component.translatable("kultivator.kota.layar.dompet", data.getLongOr("nb", 0L), data.getIntOr("rep", 0));
		if (data.getBooleanOr("discount", false)) {
			line = Component.translatable("kultivator.kota.layar.sep", line, Component.translatable("kultivator.kota.layar.diskon"));
		}
		if (data.getBooleanOr("raided", false)) {
			line = Component.translatable("kultivator.kota.layar.sep", line, Component.translatable("kultivator.kota.layar.razia").withStyle(style -> style.withColor(0xC02020)));
		}
		Brush.fit(g, font, line, x + 4.0F, py, w - 8.0F, Palette.INK_SOFT, 0.68F, 0.45F, false);
	}

	/** The Immortal City's heading and its purse line (Batu Asal Abadi). */
	protected void abadiHeader(GuiGraphicsExtractor g, Component title) {
		heading(g, title, x + 2.0F, y, w - 4.0F, 0.8F);
		Component line = Component.translatable("kultivator.kota.abadi.dompet", all().getIntOr("origin", 0));
		if (!all().getBooleanOr("immortal", false)) {
			line = Component.translatable("kultivator.kota.layar.sep", line, Component.translatable("kultivator.kota.abadi.fana"));
		}
		Brush.fit(g, font, line, x + 4.0F, y + 13.0F, w - 8.0F, Palette.INK_SOFT, 0.66F, 0.42F, false);
	}

	/** A wide placeholder panel: "Visit X to ..." */
	protected void elsewhere(GuiGraphicsExtractor g, Component text, int top) {
		panel(g, x + 2, top, x + w - 2, Math.min(y + h - 2, top + 44));
		Brush.wrap(g, font, text, x + 8.0F, top + 6.0F, w - 16.0F, Palette.INK_SOFT, 0.72F, false);
	}

	/** A horizontal jade bar 0..1. */
	protected static void bar(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float value, int color) {
		g.fill(x0, y0, x1, y1, Palette.argb(0x60, Palette.PAPER_DEEP));
		int fill = x0 + Math.round((x1 - x0) * Math.max(0.0F, Math.min(1.0F, value)));
		if (fill > x0) {
			g.fillGradient(x0, y0, fill, y1, Palette.lighten(color, 0.2F), color);
		}
		Brush.frame(g, x0, y0, x1, y1, Palette.argb(0xC0, Palette.GOLD_DARK));
	}

	/** Ticks as {@code m:ss}. */
	protected static String clock(long ticks) {
		long seconds = (Math.max(0L, ticks) + 19L) / 20L;
		return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
	}

	/**
	 * A ware card: the item icon, its name, a line under it (price, stock), the whole card is the button. Disabled cards
	 * are greyed (sold out, too poor, wrong NPC).
	 */
	static final class Card extends Widget {
		private final Supplier<ItemStack> icon;
		private final Supplier<Component> name;
		private final Supplier<Component> line;
		private final BooleanSupplier highlighted;

		Card(int x, int y, int w, int h, Supplier<ItemStack> icon, Supplier<Component> name, Supplier<Component> line, BooleanSupplier highlighted) {
			super(x, y, w, h);
			this.icon = icon;
			this.name = name;
			this.line = line;
			this.highlighted = highlighted;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
			boolean on = isEnabled();
			float glow = on ? hover.get() : 0.0F;
			int press = pressOffset(over);
			int x0 = x + press;
			int y0 = y + press;
			int top = on ? Palette.lighten(Palette.PAPER_TOP, 0.06F * glow) : Palette.grey(Palette.PAPER_TOP, 0.4F);
			int bottom = on ? Palette.PAPER_SHADE : Palette.grey(Palette.PAPER_SHADE, 0.4F);
			g.fillGradient(x0, y0, x0 + w, y0 + h, Palette.argb(0xD0, top), Palette.argb(0xD0, bottom));
			boolean hl = highlighted.getAsBoolean();
			Brush.frame(g, x0, y0, x0 + w, y0 + h, hl ? Palette.SEAL : on ? Palette.argb(0xC0, Palette.GOLD) : Palette.argb(0x80, Palette.LOCKED));
			if (glow > 0.02F) {
				g.fill(x0 + 1, y0 + 1, x0 + w - 1, y0 + 2, Palette.argb(Math.round(0x60 * glow), 0xFFFFFF));
			}
			// the icon on a small jade disc
			float cx = x0 + 10.0F;
			float cy = y0 + h / 2.0F;
			Brush.disc(g, cx, cy, 8.5F, Palette.argb(0x50, on ? Palette.JADE : Palette.LOCKED));
			g.pose().pushMatrix();
			float scale = Math.min(1.0F, (h - 4) / 16.0F);
			g.pose().translate(cx - 8.0F * scale, cy - 8.0F * scale);
			g.pose().scale(scale, scale);
			g.item(icon.get(), 0, 0);
			g.pose().popMatrix();
			float tx = x0 + 21.0F;
			float room = w - 24.0F;
			Brush.fit(g, font, name.get(), tx, y0 + 2.0F, room, on ? Palette.INK : Palette.INK_FAINT, 0.68F, 0.42F, false);
			Brush.fit(g, font, line.get(), tx, y0 + h - 8.0F, room, on ? Palette.GOLD_DARK : Palette.INK_FAINT, 0.6F, 0.4F, false);
		}
	}

	/** Lines of a tooltip. */
	protected static List<Component> lines(Component... lines) {
		return List.of(lines);
	}
}
