package dev.kultivator.client.batin;

import dev.kultivator.batin.InnerWorld;
import dev.kultivator.client.Keys;
import dev.kultivator.client.abadi.AbadiClient;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.GulirTeks;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Keabadian › Batin (DESIGN 26.10, 43.5; a page since 43 M16): the plot grid (up to 4×4; herb colour, age and the states
 * Terbakar / Terendam / Hangus; a click plants the herb in the main hand or harvests), the land-spirit card with the last
 * events, foxes and Kesuburan, pearls, the rockmen, the calamity countdown "Bencana ke-n · x tahun batin lagi" and the
 * talismans Seduh · Perkokoh · Perluas · Ambil Mutiara · Setor Bijih · Ambil Hasil. Opens after the ascent (the old hub
 * card's lock). Text at 1.0 (43.0.5); the lines scroll in the kit list. Reads {@link BatinClient#data()}, asked for while
 * shown (43.0.12).
 */
public class BatinPage extends Page {

	static final String P = "kultivator.batin.page.";
	private static final int COLS = 4;
	private static final int BUTTON_H = 14;
	private static final int BURNED = 0xFF5A2418;
	private static final int FLOODED = 0xFF2C5A8A;
	private static final int SCORCHED = 0xFF3A3050;
	private final GulirTeks baris = new GulirTeks();
	private int lastPlots = -1;
	private boolean lastBorn;

	public BatinPage() {
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
	public int glyph() {
		return Glyphs.HALL;
	}

	@Override
	public String helpTopic() {
		return "dunia_batin";
	}

	@Override
	public @Nullable Component kunci() {
		return AbadiClient.kunciNaik();
	}

	private static CompoundTag batin() {
		return BatinClient.data();
	}

	private static boolean born() {
		return batin().getBooleanOr("born", false) && batin().getBooleanOr("on", true);
	}

	/** The plot cell size: the grid takes at most 42 % of the width and the height above the two button rows. */
	private int cell() {
		int availH = h - 2 * BUTTON_H - 8;
		int maxW = Math.round(w * 0.42F);
		return Math.max(12, Math.min(26, Math.min(maxW, availH) / COLS));
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		super.onShow(fokus);
		Keys.send(new Payloads.Action(Actions.BATIN, 0, "sync"));
	}

	@Override
	public void tick() {
		if (w > 0 && needsRebuild()) {
			layout(x, y, w, h);
		}
	}

	@Override
	protected void build() {
		ListTag plots = batin().getListOrEmpty("plots");
		lastPlots = plots.size();
		lastBorn = born();
		if (!lastBorn) {
			return;
		}
		int cell = cell();
		for (int i = 0; i < plots.size(); i++) {
			int index = i;
			PlotCell plot = new PlotCell(x + (i % COLS) * cell, y + (i / COLS) * cell, cell - 2, cell - 2, index);
			plot.onPress(() -> send(Actions.BATIN, 0, "plant:" + index));
			plot.tooltip(() -> plotTip(index));
			add(plot);
		}
		int gap = 3;
		int buttonW = (w - 2 * gap) / 3;
		int row2 = y + h - BUTTON_H;
		int row1 = row2 - BUTTON_H - 2;
		button(x, row1, buttonW, "brew", "brew:1", () -> pearlsTotal() > 0 && batin().getIntOr("brew", 0) < InnerWorld.BREW_PER_DAY);
		button(x + buttonW + gap, row1, buttonW, "fortify", "fortify", () -> !batin().getCompoundOrEmpty("cal").getBooleanOr("off", true)
				&& batin().getCompoundOrEmpty("cal").getIntOr("fortify", 0) < InnerWorld.FORTIFY_MAX && pearlsTotal() >= InnerWorld.FORTIFY_COST);
		button(x + 2 * (buttonW + gap), row1, buttonW, "expand", "expand", () -> batin().getListOrEmpty("plots").size() < InnerWorld.MAX_PLOTS
				&& batin().getIntOr("batu", 0) >= InnerWorld.EXPAND_BATU);
		button(x, row2, buttonW, "withdraw", "withdraw:4", () -> batin().getIntOr("pearls", 0) > 0
				&& batin().getIntOr("wd", 0) < InnerWorld.WITHDRAW_PER_DAY);
		button(x + buttonW + gap, row2, buttonW, "feed", "feed", () -> true);
		button(x + 2 * (buttonW + gap), row2, buttonW, "take", "take", () -> {
			CompoundTag rock = batin().getCompoundOrEmpty("rock");
			return rock.getIntOr("mid", 0) + rock.getIntOr("high", 0) + rock.getIntOr("batu", 0) > 0;
		});
	}

	private void button(int bx, int by, int bw, String key, String action, java.util.function.BooleanSupplier enabled) {
		TalismanButton button = new TalismanButton(bx, by, bw, BUTTON_H, () -> Component.translatable(P + key));
		button.enabledWhen(() -> Keys.connected() && enabled.getAsBoolean() && batin().getCompoundOrEmpty("cal").getIntOr("live", -1) < 0);
		button.onPress(() -> send(Actions.BATIN, 0, action));
		button.tooltip(() -> List.of(Component.translatable(P + key), Component.translatable(P + key + ".tip", InnerWorld.BREW_PER_DAY,
				InnerWorld.FORTIFY_COST, InnerWorld.EXPAND_BATU, InnerWorld.WITHDRAW_PER_DAY, InnerWorld.ROCK_WAKE).withStyle(ChatFormatting.GRAY)));
		add(button);
	}

	private static int pearlsTotal() {
		return batin().getIntOr("pearls", 0) + batin().getIntOr("pearl_items", 0);
	}

	@Override
	public boolean needsRebuild() {
		return batin().getListOrEmpty("plots").size() != lastPlots || born() != lastBorn;
	}

	private List<Component> plotTip(int index) {
		List<Component> lines = new ArrayList<>();
		CompoundTag plot = batin().getListOrEmpty("plots").getCompoundOrEmpty(index);
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
	}

	@Override
	protected void lukis(Lukis l) {
		CompoundTag data = batin();
		if (data.isEmpty()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.LENTERA, Component.translatable("kultivator.abadi.page.loading"), null, null, x, y, x + w, y + h);
			return;
		}
		if (!data.getBooleanOr("on", true)) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.GULUNGAN, Component.translatable("kultivator.batin.off"), null, null, x, y, x + w, y + h);
			return;
		}
		if (!born()) {
			AbadiClient.mulai(baris, w, Payloads.DuniaSync.CH_BATIN);
			baris.tambah(Component.translatable(data.getBooleanOr("ascended", false) ? P + "soon" : P + "unborn"), Palette.INK);
			baris.jarak();
			baris.tambah(Component.translatable(P + "about"), Palette.INK_SOFT);
			baris.jarak();
			baris.tambah(Component.translatable(P + "source").withStyle(ChatFormatting.ITALIC), Palette.INK_FAINT);
			baris.gambar(l, "batin", x, y, x + w, y + h);
			return;
		}
		int grade = Math.max(0, Math.min(InnerWorld.GRADES.length - 1, data.getIntOr("grade", 0)));
		ListTag plots = data.getListOrEmpty("plots");
		int panelX = x + cell() * COLS + 4;
		int panelW = x + w - panelX;
		AbadiClient.mulai(baris, panelW, Payloads.DuniaSync.CH_BATIN);
		baris.tambah(Brush.bold(Component.translatable(P + "title", Component.translatable("kultivator.batin.grade." + InnerWorld.GRADES[grade]),
				plots.size(), data.getIntOr("max_plots", InnerWorld.MAX_PLOTS))), Palette.SEAL_DARK);
		// The land spirit's card: its name and the last three events.
		baris.tambah(Brush.bold(Component.translatable("kultivator.batin.spirit.name." + Math.max(0, Math.min(7, data.getIntOr("spirit", 0))))),
				Palette.GOLD_DARK);
		ListTag log = data.getListOrEmpty("log");
		for (int i = log.size() - 1, shown = 0; i >= 0 && shown < 3; i--, shown++) {
			CompoundTag entry = log.getCompoundOrEmpty(i);
			baris.tambah(Component.translatable("kultivator.batin.log." + entry.getStringOr("k", "days"), entry.getIntOr("a", 0), entry.getIntOr("b", 0)),
					Palette.INK_SOFT);
		}
		baris.jarak();
		baris.tambah(Component.translatable(P + "foxes", data.getIntOr("foxes", 0), data.getIntOr("fox_cap", 1000), data.getIntOr("fert", 0)), Palette.INK);
		baris.tambah(Component.translatable(P + "pearls", data.getIntOr("pearls", 0), InnerWorld.PEARL_CAP, data.getIntOr("pearl_rate", 1),
				data.getIntOr("brew", 0), InnerWorld.BREW_PER_DAY), Palette.INK);
		CompoundTag rock = data.getCompoundOrEmpty("rock");
		baris.tambah(rock.getBooleanOr("awake", false)
				? Component.translatable(P + "rock", rock.getIntOr("i", 0) + rock.getIntOr("g", 0) + rock.getIntOr("d", 0), InnerWorld.ROCK_BUFFER,
						rock.getIntOr("mid", 0), rock.getIntOr("high", 0), rock.getIntOr("batu", 0))
				: Component.translatable(P + "rock_sleep", rock.getIntOr("wake", 0), InnerWorld.ROCK_WAKE), Palette.INK);
		CompoundTag cal = data.getCompoundOrEmpty("cal");
		if (cal.getIntOr("live", -1) >= 0) {
			baris.tambah(Component.translatable(P + "cal_live", cal.getIntOr("n", 1), Math.max(0, cal.getIntOr("live_left", 0) / 20)), Palette.DANGER);
		} else if (cal.getBooleanOr("off", true)) {
			baris.tambah(Component.translatable(P + "cal_off"), Palette.INK_FAINT);
		} else {
			long in = cal.getLongOr("in", 0L);
			long years = (in * InnerWorld.YEARS_PER_DAY + 23999L) / 24000L;
			baris.tambah(Component.translatable(P + "cal", cal.getIntOr("n", 1),
					Component.translatable("kultivator.batin.cal.type." + dev.kultivator.batin.InnerCalamity.TYPE_IDS[Math.max(0, Math.min(2, cal.getIntOr("type", 0)))]),
					years, cal.getIntOr("fortify", 0), InnerWorld.FORTIFY_MAX, cal.getIntOr("chance", 30)), Palette.GOLD_DARK);
		}
		baris.gambar(l, "batin", panelX, y, x + w, y + h - 2 * BUTTON_H - 6);
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
			CompoundTag plot = batin().getListOrEmpty("plots").getCompoundOrEmpty(index);
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
				Brush.centered(g, font, Component.literal("+"), cx, cy - 4.0F, Palette.argb(0x90, Palette.TALISMAN), 1.0F, false);
				return;
			}
			int color = 0xFF000000 | plot.getIntOr("c", 0xC8B060);
			int age = plot.getIntOr("a", 0);
			// The age needs a cell of 20 px at text 1.0 (43.0.5); smaller cells show it in the tooltip only.
			boolean label = h >= 20;
			float leafY = label ? cy - h * 0.18F : cy;
			float leaf = Math.max(2.0F, w * 0.2F);
			Brush.disc(g, cx, leafY, leaf, color);
			Brush.rect(g, cx - 0.5F, leafY, cx + 0.5F, leafY + h * 0.2F, 0xFF3E6A2A);
			if (age >= 1000) {
				Brush.ring(g, cx, leafY, leaf + 0.6F, leaf + 1.4F, Palette.GOLD_LIGHT);
			}
			if (label) {
				String text = age >= 1000 ? String.format(java.util.Locale.ROOT, "%.1fk", age / 1000.0F) : String.valueOf(age);
				Brush.fitCentered(g, font, Component.literal(text), cx, y0 + h - 9.0F, w - 2.0F, 0xFFF4E8C8, 1.0F, 1.0F, false);
			}
		}
	}
}
