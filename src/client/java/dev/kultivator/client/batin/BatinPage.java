package dev.kultivator.client.batin;

import dev.kultivator.batin.InnerWorld;
import dev.kultivator.client.Keys;
import dev.kultivator.client.dunia.DuniaPage;
import dev.kultivator.client.dunia.DuniaScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.network.Actions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The Batin tablet of the Keabadian screen (DESIGN 26.10): the plot grid (up to 4×4; herb colour, age and the states
 * Terbakar / Terendam / Hangus; a click plants the herb in the main hand or harvests), the land-spirit card with the last
 * events, foxes and Kesuburan, pearls, the rockmen, the calamity countdown "Bencana ke-n · x tahun batin lagi" and the
 * talismans Seduh · Perkokoh · Perluas · Ambil Mutiara · Setor Bijih · Ambil Hasil. Reads {@link BatinClient#data()}.
 */
public class BatinPage extends DuniaPage {

	static final String P = "kultivator.batin.page.";
	private static final int COLS = 4;
	private static final int BURNED = 0xFF5A2418;
	private static final int FLOODED = 0xFF2C5A8A;
	private static final int SCORCHED = 0xFF3A3050;
	private int lastPlots = -1;
	private boolean lastBorn;

	public BatinPage(DuniaScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine9.tab.batin");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine9.tab.batin.short");
	}

	@Override
	public int icon() {
		return Glyphs.HALL;
	}

	private static CompoundTag data() {
		return BatinClient.data();
	}

	private static boolean born() {
		return data().getBooleanOr("born", false) && data().getBooleanOr("on", true);
	}

	private int cell() {
		int availH = h - 46;
		int maxW = Math.round(w * 0.42F);
		return Math.max(10, Math.min(22, Math.min(maxW, availH) / COLS));
	}

	@Override
	protected void build() {
		ListTag plots = data().getListOrEmpty("plots");
		lastPlots = plots.size();
		lastBorn = born();
		if (!lastBorn) {
			return;
		}
		int cell = cell();
		int top = y + 14;
		for (int i = 0; i < plots.size(); i++) {
			int index = i;
			PlotCell plot = new PlotCell(x + 2 + (i % COLS) * cell, top + (i / COLS) * cell, cell - 2, cell - 2, index);
			plot.onPress(() -> send(Actions.BATIN, 0, "plant:" + index));
			plot.tooltip(() -> plotTip(index));
			add(plot);
		}
		int buttonW = (w - 8) / 3;
		int row1 = y + h - 28;
		int row2 = y + h - 14;
		button(x + 2, row1, buttonW, "brew", "brew:1", () -> pearlsTotal() > 0 && data().getIntOr("brew", 0) < InnerWorld.BREW_PER_DAY);
		button(x + 4 + buttonW, row1, buttonW, "fortify", "fortify", () -> !data().getCompoundOrEmpty("cal").getBooleanOr("off", true)
				&& data().getCompoundOrEmpty("cal").getIntOr("fortify", 0) < InnerWorld.FORTIFY_MAX && pearlsTotal() >= InnerWorld.FORTIFY_COST);
		button(x + 6 + 2 * buttonW, row1, buttonW, "expand", "expand", () -> data().getListOrEmpty("plots").size() < InnerWorld.MAX_PLOTS
				&& data().getIntOr("batu", 0) >= InnerWorld.EXPAND_BATU);
		button(x + 2, row2, buttonW, "withdraw", "withdraw:4", () -> data().getIntOr("pearls", 0) > 0
				&& data().getIntOr("wd", 0) < InnerWorld.WITHDRAW_PER_DAY);
		button(x + 4 + buttonW, row2, buttonW, "feed", "feed", () -> true);
		button(x + 6 + 2 * buttonW, row2, buttonW, "take", "take", () -> {
			CompoundTag rock = data().getCompoundOrEmpty("rock");
			return rock.getIntOr("mid", 0) + rock.getIntOr("high", 0) + rock.getIntOr("batu", 0) > 0;
		});
	}

	private void button(int bx, int by, int bw, String key, String action, java.util.function.BooleanSupplier enabled) {
		TalismanButton button = new TalismanButton(bx, by, bw, 13, () -> Component.translatable(P + key));
		button.enabledWhen(() -> Keys.connected() && enabled.getAsBoolean() && data().getCompoundOrEmpty("cal").getIntOr("live", -1) < 0);
		button.onPress(() -> send(Actions.BATIN, 0, action));
		button.tooltip(() -> List.of(Component.translatable(P + key), Component.translatable(P + key + ".tip", InnerWorld.BREW_PER_DAY,
				InnerWorld.FORTIFY_COST, InnerWorld.EXPAND_BATU, InnerWorld.WITHDRAW_PER_DAY, InnerWorld.ROCK_WAKE).withStyle(ChatFormatting.GRAY)));
		add(button);
	}

	private static int pearlsTotal() {
		return data().getIntOr("pearls", 0) + data().getIntOr("pearl_items", 0);
	}

	@Override
	public boolean needsRebuild() {
		return data().getListOrEmpty("plots").size() != lastPlots || born() != lastBorn;
	}

	private List<Component> plotTip(int index) {
		List<Component> lines = new ArrayList<>();
		CompoundTag plot = data().getListOrEmpty("plots").getCompoundOrEmpty(index);
		lines.add(Component.translatable(P + "plot", index + 1));
		String kind = plot.getStringOr("k", "");
		int state = plot.getIntOr("s", 0);
		if (state != InnerWorld.STATE_OK) {
			lines.add(Component.translatable(P + "state." + state, plot.getLongOr("u", 0L)).withStyle(ChatFormatting.RED));
		}
		if (kind.isEmpty()) {
			lines.add(Component.translatable(state == InnerWorld.STATE_BURNED || state == InnerWorld.STATE_SCORCHED ? P + "plot.ruined" : P + "plot.empty")
					.withStyle(ChatFormatting.GRAY));
		} else {
			lines.add(Component.translatable(plot.getStringOr("n", "")).withColor(plot.getIntOr("c", 0xC8B060)));
			lines.add(Component.translatable(P + "plot.age", plot.getIntOr("a", 0)).withStyle(ChatFormatting.GOLD));
			lines.add(Component.translatable(P + "plot.harvest").withStyle(ChatFormatting.GRAY));
		}
		return lines;
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		CompoundTag data = data();
		float scale = w >= 260 ? 0.66F : 0.58F;
		if (!data.getBooleanOr("on", true)) {
			heading(g, title(), x + 2.0F, y, w - 4.0F, 0.8F);
			Brush.wrap(g, font, Component.translatable("kultivator.batin.off"), x + 4.0F, y + 16.0F, w - 8.0F, Palette.INK_SOFT, scale + 0.1F, false);
			return;
		}
		if (!born()) {
			heading(g, title(), x + 2.0F, y, w - 4.0F, 0.8F);
			float cursor = y + 16.0F;
			cursor += Brush.wrap(g, font, Component.translatable(data.getBooleanOr("ascended", false) ? P + "soon" : P + "unborn"), x + 4.0F, cursor,
					w - 8.0F, Palette.INK, scale + 0.08F, false) + 4.0F;
			Brush.wrap(g, font, Component.translatable(P + "about"), x + 4.0F, cursor, w - 8.0F, Palette.INK_SOFT, scale, false);
			sourceLine(g);
			return;
		}
		int grade = Math.max(0, Math.min(InnerWorld.GRADES.length - 1, data.getIntOr("grade", 0)));
		ListTag plots = data.getListOrEmpty("plots");
		heading(g, Component.translatable(P + "title", Component.translatable("kultivator.batin.grade." + InnerWorld.GRADES[grade]), plots.size(),
				data.getIntOr("max_plots", InnerWorld.MAX_PLOTS)), x + 2.0F, y, w - 4.0F, 0.8F);
		int cell = cell();
		float panelX = x + 6.0F + cell * COLS;
		float panelW = x + w - panelX - 1.0F;
		float cursor = y + 14.0F;
		// The land spirit's card.
		panel(g, Math.round(panelX), Math.round(cursor), x + w - 1, Math.round(cursor + 9.0F * scale * 4.0F + 6.0F));
		Brush.fit(g, font, Brush.bold(Component.translatable("kultivator.batin.spirit.name." + Math.max(0, Math.min(7, data.getIntOr("spirit", 0))))),
				panelX + 3.0F, cursor + 2.0F, panelW - 6.0F, Palette.SEAL_DARK, scale + 0.06F, 0.45F, false);
		ListTag log = data.getListOrEmpty("log");
		float line = cursor + 3.0F + 9.0F * scale;
		for (int i = log.size() - 1, shown = 0; i >= 0 && shown < 3; i--, shown++) {
			CompoundTag entry = log.getCompoundOrEmpty(i);
			Brush.fit(g, font, Component.translatable("kultivator.batin.log." + entry.getStringOr("k", "days"), entry.getIntOr("a", 0), entry.getIntOr("b", 0)),
					panelX + 3.0F, line, panelW - 6.0F, Palette.INK_SOFT, scale - 0.06F, 0.38F, false);
			line += 9.0F * scale;
		}
		cursor += 9.0F * scale * 4.0F + 9.0F;
		int foxes = data.getIntOr("foxes", 0);
		cursor += stat(g, Component.translatable(P + "foxes", foxes, data.getIntOr("fox_cap", 1000), data.getIntOr("fert", 0)), panelX, cursor, panelW, scale);
		cursor += stat(g, Component.translatable(P + "pearls", data.getIntOr("pearls", 0), InnerWorld.PEARL_CAP, data.getIntOr("pearl_rate", 1),
				data.getIntOr("brew", 0), InnerWorld.BREW_PER_DAY), panelX, cursor, panelW, scale);
		CompoundTag rock = data.getCompoundOrEmpty("rock");
		Component rockLine = rock.getBooleanOr("awake", false)
				? Component.translatable(P + "rock", rock.getIntOr("i", 0) + rock.getIntOr("g", 0) + rock.getIntOr("d", 0), InnerWorld.ROCK_BUFFER,
						rock.getIntOr("mid", 0), rock.getIntOr("high", 0), rock.getIntOr("batu", 0))
				: Component.translatable(P + "rock_sleep", rock.getIntOr("wake", 0), InnerWorld.ROCK_WAKE);
		cursor += stat(g, rockLine, panelX, cursor, panelW, scale);
		CompoundTag cal = data.getCompoundOrEmpty("cal");
		Component calLine;
		int color = Palette.GOLD_DARK;
		if (cal.getIntOr("live", -1) >= 0) {
			calLine = Component.translatable(P + "cal_live", cal.getIntOr("n", 1), Math.max(0, cal.getIntOr("live_left", 0) / 20));
			color = Palette.DANGER;
		} else if (cal.getBooleanOr("off", true)) {
			calLine = Component.translatable(P + "cal_off");
			color = Palette.INK_FAINT;
		} else {
			long in = cal.getLongOr("in", 0L);
			long years = (in * InnerWorld.YEARS_PER_DAY + 23999L) / 24000L;
			calLine = Component.translatable(P + "cal", cal.getIntOr("n", 1),
					Component.translatable("kultivator.batin.cal.type." + dev.kultivator.batin.InnerCalamity.TYPE_IDS[Math.max(0, Math.min(2, cal.getIntOr("type", 0)))]),
					years, cal.getIntOr("fortify", 0), InnerWorld.FORTIFY_MAX, cal.getIntOr("chance", 30));
		}
		Brush.wrap(g, font, calLine, panelX, cursor, panelW, color, scale, false);
	}

	private float stat(GuiGraphicsExtractor g, Component text, float sx, float sy, float width, float scale) {
		return Brush.wrap(g, font, text, sx, sy, width, Palette.INK, scale, false) + 1.5F;
	}

	private void sourceLine(GuiGraphicsExtractor g) {
		Component line = Component.translatable(P + "source").withStyle(ChatFormatting.ITALIC);
		float height = Brush.wrapHeight(font, line, w - 8.0F, 0.52F);
		Brush.wrap(g, font, line, x + 4.0F, y + h - height - 1.0F, w - 8.0F, Palette.INK_FAINT, 0.52F, false);
	}

	/** One plot: the soil, the herb's colour and age, or the ruin of a calamity. */
	private final class PlotCell extends Widget {

		private final int index;

		PlotCell(int x, int y, int w, int h, int index) {
			super(x, y, w, h);
			this.index = index;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
			CompoundTag plot = data().getListOrEmpty("plots").getCompoundOrEmpty(index);
			int state = plot.getIntOr("s", InnerWorld.STATE_OK);
			int off = pressOffset(over);
			int x0 = x + off;
			int y0 = y + off;
			int soil = switch (state) {
				case InnerWorld.STATE_BURNED -> BURNED;
				case InnerWorld.STATE_FLOODED -> FLOODED;
				case InnerWorld.STATE_SCORCHED -> SCORCHED;
				default -> 0xFF5E4630;
			};
			g.fill(x0, y0, x0 + w, y0 + h, Palette.lighten(soil, 0.15F * hover.get()));
			Brush.frame(g, x0, y0, x0 + w, y0 + h, over ? Palette.GOLD : Palette.argb(0xC0, Palette.GOLD_DARK));
			String kind = plot.getStringOr("k", "");
			float cx = x0 + w / 2.0F;
			float cy = y0 + h / 2.0F;
			if (state == InnerWorld.STATE_BURNED || state == InnerWorld.STATE_SCORCHED) {
				Brush.line(g, x0 + 3.0F, y0 + 3.0F, x0 + w - 3.0F, y0 + h - 3.0F, 1.0F, Palette.argb(0xC0, 0x1A1010));
				Brush.line(g, x0 + w - 3.0F, y0 + 3.0F, x0 + 3.0F, y0 + h - 3.0F, 1.0F, Palette.argb(0xC0, 0x1A1010));
				return;
			}
			if (kind.isEmpty()) {
				Brush.centered(g, font, Component.literal("+"), cx, cy - 3.0F, Palette.argb(0x90, Palette.TALISMAN), 0.7F, false);
				return;
			}
			int color = 0xFF000000 | plot.getIntOr("c", 0xC8B060);
			int age = plot.getIntOr("a", 0);
			float leaf = Math.max(2.0F, w * 0.22F);
			Brush.disc(g, cx, cy - h * 0.12F, leaf, color);
			Brush.rect(g, cx - 0.5F, cy - h * 0.12F, cx + 0.5F, cy + h * 0.15F, 0xFF3E6A2A);
			if (age >= 1000) {
				Brush.ring(g, cx, cy - h * 0.12F, leaf + 0.6F, leaf + 1.4F, Palette.GOLD_LIGHT);
			}
			String text = age >= 1000 ? String.format(java.util.Locale.ROOT, "%.1fk", age / 1000.0F) : String.valueOf(age);
			Brush.centered(g, font, Component.literal(text), cx, y0 + h - 6.0F, 0xFFF4E8C8, 0.45F, false);
		}
	}
}
