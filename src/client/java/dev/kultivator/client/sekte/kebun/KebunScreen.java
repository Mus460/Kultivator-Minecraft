package dev.kultivator.client.sekte.kebun;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.sekte.kebun.Kebun;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The board of the Kebun Obat &amp; Tambang Roh (DESIGN 36.2.10 "UI"): a paper scroll with two pages, opened by the hub
 * card, a click on a bed's soil or the mine's mouth, or {@code /kultivasi perguruan kebun}. Every button sends one
 * {@code KebunPayloads.Action}; the server checks it and answers with a fresh {@code Open}, so the board never guesses.
 * <ul>
 * <li><b>Kebun</b>: the spirit beds as a grid of cards (the herb, its stage, a growth bar in the herb's own colour, the time
 * left, the replant mark and the gardener's leaf); the chosen bed's panel (sow one of the nine seeds from the Lumbung or the
 * pack, harvest, uproot, replant on or off); the gardener slots (one per two beds) with each gardener's streak of work
 * days; "Panen semua"; the board's log.</li>
 * <li><b>Tambang</b>: the shaft drawn as nine strata with the depth reached, the cave-in risk from level 5, the shift
 * slots (2/3/4/6 by depth) with streak, today's and the total haul, the yield table of the level, and the cost of going
 * deeper (PK from the hall pool, wood and stone from the deposits).</li>
 * </ul>
 * Choosing a worker opens a list of the disciples near the stele with their roots (Kayu/Air for the garden, Tanah/Logam for
 * the mine: ×1.1), rest and mood. Text keys {@code kultivator.sekte.kebun.ui.*}.
 */
public class KebunScreen extends Screen {

	private static final int W = 440;
	private static final int H = 272;
	private static final String[] STAGE_GLYPH = {"·", "芽", "苗", "蕾", "華"};

	private int page;
	private CompoundTag data;
	private int selected = -1;
	/** The worker list while choosing: "rawat" or "gali" and the slot (1-based), else null. */
	private @Nullable String picking;
	private int pickSlot;
	private int x0;
	private int y0;
	private int x1;
	private int y1;
	private final List<Hit> hits = new ArrayList<>();
	private final Map<String, ItemStack> icons = new HashMap<>();

	private record Hit(float x0, float y0, float x1, float y1, Runnable action) {
		boolean contains(double x, double y) {
			return x >= x0 && x <= x1 && y >= y0 && y <= y1;
		}
	}

	public KebunScreen(int page, CompoundTag data) {
		super(Component.translatable("kultivator.sekte.kebun.ui.judul"));
		this.page = page == Kebun.PAGE_MINE ? Kebun.PAGE_MINE : Kebun.PAGE_GARDEN;
		this.data = data == null ? new CompoundTag() : data.copy();
	}

	/** A fresh state from the server (the board stays open). */
	void update(int page, CompoundTag data) {
		this.page = page == Kebun.PAGE_MINE ? Kebun.PAGE_MINE : Kebun.PAGE_GARDEN;
		this.data = data == null ? new CompoundTag() : data.copy();
		if (selected >= data.getIntOr("plots", 0)) {
			selected = -1;
		}
	}

	@Override
	protected void init() {
		int w = Math.min(W, width - 12);
		int h = Math.min(H, height - 10);
		x0 = (width - w) / 2;
		x1 = x0 + w;
		y0 = (height - h) / 2;
		y1 = y0 + h;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, Palette.NIGHT, Palette.MOUNTAIN);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		hits.clear();
		long now = Util.getMillis();
		// the scroll: shadow, paper, the two rods, a jade frame
		g.fill(x0 + 3, y0 + 4, x1 + 3, y1 + 4, 0x60000000);
		g.fillGradient(x0, y0, x1, y1, Palette.PAPER_TOP, Palette.PAPER_BOTTOM);
		g.fillGradient(x0 - 4, y0 - 3, x1 + 4, y0 + 3, Palette.ROD_LIGHT, Palette.ROD_DARK);
		g.fillGradient(x0 - 4, y1 - 3, x1 + 4, y1 + 3, Palette.ROD_LIGHT, Palette.ROD_DARK);
		Brush.frame(g, x0, y0, x1, y1, Palette.JADE_DARK);
		header(g, mouseX, mouseY);
		if (page == Kebun.PAGE_MINE) {
			mine(g, mouseX, mouseY, now);
		} else {
			garden(g, mouseX, mouseY, now);
		}
		if (picking != null) {
			picker(g, mouseX, mouseY);
		}
		footer(g);
	}

	// ================================================================== header and footer

	private void header(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		float cx = (x0 + x1) / 2.0F;
		Brush.fitCentered(g, font, Brush.bold(title), cx, y0 + 7.0F, 220.0F, Palette.INK, 1.1F, 0.6F, false);
		Brush.disc(g, x1 - 20.0F, y0 + 13.0F, 9.0F, Palette.SEAL);
		Brush.centered(g, font, Component.literal(page == Kebun.PAGE_MINE ? "礦" : "藥"), x1 - 20.0F, y0 + 9.0F, 0xFFFFF4E0, 1.0F, false);
		// the two page tabs
		tab(g, x0 + 12, Kebun.PAGE_GARDEN, Component.translatable("kultivator.sekte.kebun.ui.tab_kebun"), mouseX, mouseY);
		tab(g, x0 + 76, Kebun.PAGE_MINE, Component.translatable("kultivator.sekte.kebun.ui.tab_tambang"), mouseX, mouseY);
		Brush.rect(g, x0 + 10.0F, y0 + 22.0F, x1 - 10.0F, y0 + 23.0F, Palette.argb(0x90, Palette.JADE_DARK));
	}

	private void tab(GuiGraphicsExtractor g, int x, int which, Component label, int mouseX, int mouseY) {
		int y = y0 + 7;
		boolean on = page == which;
		boolean hover = mouseX >= x && mouseX <= x + 60 && mouseY >= y && mouseY <= y + 13;
		int color = on ? Palette.JADE : hover ? Palette.JADE_LIGHT : Palette.JADE_DULL;
		Brush.chamfer(g, x, y, x + 60, y + 13, 2, color);
		Brush.fitCentered(g, font, Brush.bold(label), x + 30.0F, y + 3.0F, 56.0F, 0xFFFFF8E8, 0.65F, 0.4F, false);
		hits.add(new Hit(x, y, x + 60, y + 13, () -> {
			if (page != which) {
				picking = null;
				KebunClient.send("buka", which, "");
			}
		}));
	}

	private void footer(GuiGraphicsExtractor g) {
		float y = y1 - 30.0F;
		Brush.rect(g, x0 + 12.0F, y - 2.0F, x1 - 12.0F, y - 1.0F, Palette.argb(0x60, Palette.JADE_DARK));
		ListTag log = data.getListOrEmpty("log");
		float ly = y;
		for (int i = 0; i < Math.min(2, log.size()); i++) {
			if (!(log.get(i) instanceof CompoundTag entry)) {
				continue;
			}
			Component text = entry.read("text", ComponentSerialization.CODEC).orElse(Component.empty());
			Component line = Component.translatable("kultivator.sekte.kebun.ui.log_baris", entry.getLongOr("day", 0L), text);
			Brush.fit(g, font, line, x0 + 14.0F, ly, x1 - x0 - 28.0F, i == 0 ? Palette.INK_SOFT : Palette.INK_FAINT, 0.55F, 0.4F, false);
			ly += 8.0F;
		}
		Brush.wrap(g, font, Component.translatable(page == Kebun.PAGE_MINE ? "kultivator.sekte.kebun.sumber.tambang" : "kultivator.sekte.kebun.sumber.kebun")
				.withStyle(ChatFormatting.ITALIC), x0 + 14.0F, y1 - 12.0F, x1 - x0 - 28.0F, Palette.INK_FAINT, 0.5F, false);
	}

	// ================================================================== the garden

	private void garden(GuiGraphicsExtractor g, int mouseX, int mouseY, long now) {
		int plots = data.getIntOr("plots", 0);
		float top = y0 + 28.0F;
		float split = x0 + (x1 - x0) * 0.60F;
		if (!data.getBooleanOr("garden_on", true)) {
			Brush.fitCentered(g, font, Component.translatable("kultivator.sekte.kebun.ui.kebun_mati"), (x0 + x1) / 2.0F, top + 40.0F, 300.0F,
					Palette.INK_FAINT, 0.8F, 0.5F, false);
			return;
		}
		if (plots <= 0) {
			Brush.wrap(g, font, Component.translatable("kultivator.sekte.kebun.ui.belum_petak"), x0 + 20.0F, top + 30.0F, x1 - x0 - 40.0F,
					Palette.INK_SOFT, 0.7F, false);
			return;
		}
		ListTag beds = data.getListOrEmpty("beds");
		int cols = plots <= 4 ? Math.max(2, plots) : plots <= 8 ? 4 : 4;
		int rows = (plots + cols - 1) / cols;
		float gridW = split - x0 - 20.0F;
		float cellW = Math.min(64.0F, gridW / cols);
		float cellH = Math.min(46.0F, (y1 - 96.0F - top) / Math.max(1, rows));
		Brush.text(g, font, Brush.bold(Component.translatable("kultivator.sekte.kebun.ui.petak_judul", plots, data.getIntOr("hall", 0))),
				x0 + 12.0F, top, Palette.JADE_DARK, 0.7F, false);
		float gy = top + 10.0F;
		for (int i = 0; i < plots; i++) {
			CompoundTag bed = i < beds.size() && beds.get(i) instanceof CompoundTag b ? b : new CompoundTag();
			float cx = x0 + 12.0F + (i % cols) * cellW;
			float cy = gy + (i / cols) * cellH;
			bedCard(g, i, bed, cx, cy, cx + cellW - 4.0F, cy + cellH - 4.0F, mouseX, mouseY, now);
		}
		float bottom = gy + rows * cellH + 2.0F;
		gardeners(g, x0 + 12.0F, bottom, split - 6.0F, mouseX, mouseY);
		Brush.rect(g, split, top, split + 0.8F, y1 - 36.0F, Palette.argb(0x60, Palette.JADE_DARK));
		bedPanel(g, beds, split + 8.0F, top, x1 - 12.0F, mouseX, mouseY);
	}

	private void bedCard(GuiGraphicsExtractor g, int index, CompoundTag bed, float ax, float ay, float bx, float by, int mouseX, int mouseY,
			long now) {
		String herb = bed.getStringOr("herb", "");
		int stage = bed.getIntOr("stage", 0);
		int rgb = bed.getIntOr("color", 0x8CFFB0);
		boolean ripe = stage >= 4;
		boolean hover = mouseX >= ax && mouseX <= bx && mouseY >= ay && mouseY <= by;
		boolean chosen = selected == index;
		float pulse = 0.5F + 0.5F * (float) Math.sin(now / 260.0D + index);
		// the card: tilled earth under a paper label
		Brush.chamferGradient(g, (int) ax, (int) ay, (int) bx, (int) by, 3, 0xFF6B5136, 0xFF4A3622);
		if (ripe) {
			Brush.rect(g, ax - 1.0F, ay - 1.0F, bx + 1.0F, by + 1.0F, Palette.argb((int) (0x40 + 0x60 * pulse), rgb));
			Brush.chamferGradient(g, (int) ax, (int) ay, (int) bx, (int) by, 3, 0xFF6B5136, 0xFF4A3622);
		}
		if (chosen || hover) {
			Brush.frame(g, (int) ax, (int) ay, (int) bx, (int) by, chosen ? Palette.GOLD : Palette.JADE_LIGHT);
		}
		float midX = (ax + bx) / 2.0F;
		Brush.text(g, font, Component.literal(String.valueOf(index + 1)), ax + 3.0F, ay + 2.0F, 0xFFE8D8B8, 0.55F, false);
		if (herb.isEmpty()) {
			Brush.centered(g, font, Component.literal("+"), midX, ay + 8.0F, 0xFFB8A27E, 1.4F, false);
			Brush.fitCentered(g, font, Component.translatable("kultivator.sekte.kebun.ui.kosong"), midX, by - 9.0F, bx - ax - 4.0F, 0xFFD8C8A8,
					0.55F, 0.35F, false);
		} else {
			if (stage >= 3) {
				Brush.halo(g, midX, ay + 15.0F, 5.0F, 6.0F, rgb & 0xFFFFFF, ripe ? 0.6F + 0.4F * pulse : 0.35F);
			}
			ItemStack icon = icon(bed.getStringOr("item", ""));
			if (stage >= 3) {
				g.item(icon, (int) midX - 8, (int) ay + 7);
			} else {
				Brush.centered(g, font, Component.literal(STAGE_GLYPH[Math.max(0, Math.min(4, stage))]), midX, ay + 9.0F, Palette.argb(0xFF, rgb), 1.2F,
						false);
			}
			// the growth bar in the herb's colour
			float prog = ripe ? 1.0F : Math.max(0.0F, Math.min(1.0F, bed.getFloatOr("prog", 0.0F)));
			float barY = by - 11.0F;
			Brush.rect(g, ax + 4.0F, barY, bx - 4.0F, barY + 2.0F, 0xFF2E2216);
			Brush.rect(g, ax + 4.0F, barY, ax + 4.0F + (bx - ax - 8.0F) * prog, barY + 2.0F, Palette.argb(0xFF, ripe ? Palette.mix(rgb | 0xFF000000,
					0xFFFFFFFF, 0.3F * pulse) : rgb));
			Component state = ripe ? Component.translatable("kultivator.sekte.kebun.ui.siap_panen")
					: Component.translatable("kultivator.sekte.kebun.stage." + Math.max(0, Math.min(4, stage)));
			Brush.fitCentered(g, font, state, midX, by - 8.0F, bx - ax - 4.0F, ripe ? Palette.GOLD_LIGHT : 0xFFE8D8B8, 0.52F, 0.35F, false);
			if (bed.getBooleanOr("auto", false)) {
				Brush.text(g, font, Component.literal("↻"), bx - 8.0F, ay + 2.0F, Palette.JADE_PALE, 0.6F, false);
			}
			if (bed.getBooleanOr("tended", false)) {
				Brush.text(g, font, Component.literal("✿"), bx - 8.0F, ay + 9.0F, Palette.JADE_LIGHT, 0.55F, false);
			}
			if (hover) {
				List<Component> tip = new ArrayList<>();
				tip.add(Brush.bold(herbName(bed.getStringOr("item", ""))));
				tip.add(Component.translatable("kultivator.sekte.kebun.ui.tip_tahap", Component.translatable("kultivator.sekte.kebun.stage." + stage),
						Math.round(prog * 100.0F)));
				if (!ripe) {
					tip.add(Component.translatable("kultivator.sekte.kebun.ui.tip_sisa", timeLeft(bed.getLongOr("left", 0L))));
				}
				tip.add(Component.translatable("kultivator.sekte.kebun.ui.tip_umur", bed.getIntOr("age", 0)));
				tip.add(Component.translatable(bed.getBooleanOr("tended", false) ? "kultivator.sekte.kebun.ui.tip_dirawat"
						: "kultivator.sekte.kebun.ui.tip_tak_dirawat"));
				g.setTooltipForNextFrame(font, tip, java.util.Optional.empty(), mouseX, mouseY);
			}
		}
		hits.add(new Hit(ax, ay, bx, by, () -> {
			picking = null;
			if (ripe && selected == index) {
				KebunClient.send("panen", index, "");
			}
			selected = index;
		}));
	}

	private void bedPanel(GuiGraphicsExtractor g, ListTag beds, float left, float top, float right, int mouseX, int mouseY) {
		float y = top;
		if (selected < 0 || selected >= beds.size() || !(beds.get(selected) instanceof CompoundTag bed)) {
			y += line(g, Brush.bold(Component.translatable("kultivator.sekte.kebun.ui.pilih_petak")), left, y, right - left, Palette.JADE_DARK, 0.72F);
			y += line(g, Component.translatable("kultivator.sekte.kebun.ui.pilih_petak_tip"), left, y, right - left, Palette.INK_SOFT, 0.6F);
			y += 4.0F;
			harvestAll(g, beds, left, y, right, mouseX, mouseY);
			return;
		}
		String herb = bed.getStringOr("herb", "");
		int stage = bed.getIntOr("stage", 0);
		y += line(g, Brush.bold(Component.translatable("kultivator.sekte.kebun.ui.petak_n", selected + 1)), left, y, right - left, Palette.JADE_DARK,
				0.75F);
		if (herb.isEmpty()) {
			y += line(g, Component.translatable("kultivator.sekte.kebun.ui.pilih_benih"), left, y, right - left, Palette.INK_SOFT, 0.6F);
			seeds(g, left, y, right, mouseX, mouseY);
			return;
		}
		int rgb = bed.getIntOr("color", 0x8CFFB0);
		ItemStack icon = icon(bed.getStringOr("item", ""));
		g.item(icon, (int) left, (int) y);
		Brush.fit(g, font, Brush.bold(herbName(bed.getStringOr("item", ""))), left + 20.0F, y + 1.0F, right - left - 20.0F, Palette.argb(0xFF,
				Palette.mix(rgb | 0xFF000000, Palette.INK, 0.55F)), 0.7F, 0.45F, false);
		Brush.fit(g, font, Component.translatable("kultivator.sekte.kebun.ui.tip_umur", bed.getIntOr("age", 0)), left + 20.0F, y + 9.0F,
				right - left - 20.0F, Palette.INK_FAINT, 0.55F, 0.4F, false);
		y += 20.0F;
		boolean ripe = stage >= 4;
		y += line(g, ripe ? Component.translatable("kultivator.sekte.kebun.ui.siap_panen").withStyle(ChatFormatting.BOLD)
				: Component.translatable("kultivator.sekte.kebun.ui.tip_sisa", timeLeft(bed.getLongOr("left", 0L))), left, y, right - left,
				ripe ? Palette.GOLD_DARK : Palette.INK, 0.62F);
		y += line(g, Component.translatable(bed.getBooleanOr("tended", false) ? "kultivator.sekte.kebun.ui.tip_dirawat"
				: "kultivator.sekte.kebun.ui.tip_tak_dirawat"), left, y, right - left, bed.getBooleanOr("tended", false) ? Palette.JADE_DARK
				: Palette.WARN, 0.58F);
		y += 4.0F;
		int index = selected;
		float bw = (right - left - 6.0F) / 2.0F;
		if (ripe) {
			button(g, left, y, right, Component.translatable("kultivator.sekte.kebun.ui.panen"), Palette.GOLD_DARK, mouseX, mouseY,
					() -> KebunClient.send("panen", index, ""));
			y += 16.0F;
		}
		boolean auto = bed.getBooleanOr("auto", false);
		button(g, left, y, left + bw, Component.translatable(auto ? "kultivator.sekte.kebun.ui.ulang_on" : "kultivator.sekte.kebun.ui.ulang_off"),
				auto ? Palette.JADE : Palette.JADE_DULL, mouseX, mouseY, () -> KebunClient.send("ulang", index, ""));
		button(g, left + bw + 6.0F, y, right, Component.translatable("kultivator.sekte.kebun.ui.cabut"), Palette.SEAL, mouseX, mouseY,
				() -> KebunClient.send("cabut", index, ""));
		y += 18.0F;
		y += line(g, Component.translatable("kultivator.sekte.kebun.ui.ulang_tip"), left, y, right - left, Palette.INK_FAINT, 0.52F);
		y += 2.0F;
		harvestAll(g, beds, left, y, right, mouseX, mouseY);
	}

	private void harvestAll(GuiGraphicsExtractor g, ListTag beds, float left, float y, float right, int mouseX, int mouseY) {
		int ripe = 0;
		for (Tag tag : beds) {
			if (tag instanceof CompoundTag b && b.getIntOr("stage", 0) >= 4) {
				ripe++;
			}
		}
		if (ripe > 0) {
			button(g, left, y, right, Component.translatable("kultivator.sekte.kebun.ui.panen_semua", ripe), Palette.GOLD_DARK, mouseX, mouseY,
					() -> KebunClient.send("panen", -1, ""));
			y += 16.0F;
		}
		CompoundTag stat = data.getCompoundOrEmpty("stat");
		line(g, Component.translatable("kultivator.sekte.kebun.ui.statistik_kebun", stat.getIntOr("panen", 0), stat.getIntOr("herba", 0)), left, y + 2.0F,
				right - left, Palette.INK_FAINT, 0.55F);
	}

	/** The nine seeds: icon, name, days to ripen, how many in the Lumbung and in the pack. */
	private void seeds(GuiGraphicsExtractor g, float left, float top, float right, int mouseX, int mouseY) {
		float y = top;
		int index = selected;
		for (Tag tag : data.getListOrEmpty("seeds")) {
			if (!(tag instanceof CompoundTag seed) || y > y1 - 46.0F) {
				continue;
			}
			int store = seed.getIntOr("store", 0);
			int hand = seed.getIntOr("hand", 0);
			boolean have = store + hand > 0;
			float rowB = y + 12.0F;
			boolean hover = have && mouseX >= left && mouseX <= right && mouseY >= y && mouseY <= rowB;
			if (hover) {
				Brush.rect(g, left - 2.0F, y - 1.0F, right + 2.0F, rowB, Palette.argb(0x50, Palette.JADE_LIGHT));
			}
			ItemStack icon = icon(seed.getStringOr("item", ""));
			g.pose().pushMatrix();
			g.pose().translate(left, y - 1.0F);
			g.pose().scale(0.75F, 0.75F);
			g.item(icon, 0, 0);
			g.pose().popMatrix();
			int rgb = seed.getIntOr("color", 0x8CFFB0);
			int ink = have ? Palette.mix(rgb | 0xFF000000, Palette.INK, 0.6F) : Palette.INK_FAINT;
			Brush.fit(g, font, herbName(seed.getStringOr("item", "")), left + 15.0F, y + 2.0F, right - left - 70.0F, ink, 0.58F, 0.38F, false);
			Brush.text(g, font, Component.translatable("kultivator.sekte.kebun.ui.benih_hari", 1 + seed.getIntOr("mutu", 0)), right - 54.0F, y + 2.0F,
					Palette.INK_SOFT, 0.52F, false);
			Brush.text(g, font, Component.translatable("kultivator.sekte.kebun.ui.benih_stok", store, hand), right - 28.0F, y + 2.0F,
					have ? Palette.JADE_DARK : Palette.INK_FAINT, 0.52F, false);
			if (have) {
				String id = seed.getStringOr("id", "");
				hits.add(new Hit(left, y, right, rowB, () -> KebunClient.send("tanam", index, id)));
			}
			y += 13.0F;
		}
	}

	/** The gardener slots: one per two beds. */
	private void gardeners(GuiGraphicsExtractor g, float left, float top, float right, int mouseX, int mouseY) {
		int slots = data.getIntOr("tslots", 0);
		if (slots <= 0) {
			return;
		}
		Brush.text(g, font, Brush.bold(Component.translatable("kultivator.sekte.kebun.ui.perawat", data.getIntOr("rest", 5))), left, top,
				Palette.JADE_DARK, 0.62F, false);
		Map<Integer, CompoundTag> bySlot = workers(data.getListOrEmpty("tenders"));
		float y = top + 9.0F;
		int cols = Math.min(3, slots);
		float w = (right - left) / cols;
		for (int s = 1; s <= slots; s++) {
			float ax = left + ((s - 1) % cols) * w;
			float ay = y + ((s - 1) / cols) * 22.0F;
			workerChip(g, "rawat", s, bySlot.get(s), Component.translatable("kultivator.sekte.kebun.ui.slot_petak", 2 * s - 1, Math.min(2 * s,
					data.getIntOr("plots", 0))), ax, ay, ax + w - 4.0F, ay + 19.0F, mouseX, mouseY);
		}
	}

	/** One worker slot: the name, streak and two small seals (release, rest), or "+ pilih" when empty. */
	private void workerChip(GuiGraphicsExtractor g, String kind, int slot, @Nullable CompoundTag worker, Component label, float ax, float ay, float bx,
			float by, int mouseX, int mouseY) {
		boolean hover = mouseX >= ax && mouseX <= bx && mouseY >= ay && mouseY <= by;
		Brush.chamfer(g, (int) ax, (int) ay, (int) bx, (int) by, 2, hover ? Palette.PAPER_DEEP : Palette.PAPER_SHADE);
		if (worker == null) {
			Brush.fit(g, font, label, ax + 3.0F, ay + 2.0F, bx - ax - 6.0F, Palette.INK_FAINT, 0.5F, 0.35F, false);
			Brush.fit(g, font, Component.translatable("kultivator.sekte.kebun.ui.pilih_murid"), ax + 3.0F, ay + 10.0F, bx - ax - 6.0F, Palette.JADE_DARK,
					0.58F, 0.38F, false);
			hits.add(new Hit(ax, ay, bx, by, () -> {
				picking = kind;
				pickSlot = slot;
			}));
			return;
		}
		Component name = worker.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
		int streak = worker.getIntOr("streak", 0);
		int rest = data.getIntOr("rest", 5);
		boolean tired = streak >= rest;
		boolean here = worker.getBooleanOr("here", true);
		Brush.fit(g, font, Brush.bold(name), ax + 3.0F, ay + 2.0F, bx - ax - 30.0F, here ? Palette.INK : Palette.INK_FAINT, 0.58F, 0.38F, false);
		Component sub = "gali".equals(kind)
				? Component.translatable("kultivator.sekte.kebun.ui.penambang_baris", streak, worker.getIntOr("today", 0), worker.getIntOr("total", 0))
				: Component.translatable("kultivator.sekte.kebun.ui.perawat_baris", streak);
		Brush.fit(g, font, sub, ax + 3.0F, ay + 10.5F, bx - ax - 30.0F, tired ? Palette.DANGER : Palette.INK_SOFT, 0.5F, 0.32F, false);
		// two seals: 離 release, 休 a day off
		float sx = bx - 25.0F;
		seal(g, sx, ay + 3.0F, "離", Palette.SEAL, mouseX, mouseY, () -> KebunClient.send(kind + "_lepas", slot, ""),
				Component.translatable("kultivator.sekte.kebun.ui.lepas"));
		seal(g, sx + 12.0F, ay + 3.0F, "休", tired ? Palette.WARN : Palette.JADE, mouseX, mouseY, () -> KebunClient.send(kind + "_libur", slot, ""),
				Component.translatable("kultivator.sekte.kebun.ui.libur", rest));
	}

	private void seal(GuiGraphicsExtractor g, float x, float y, String glyph, int color, int mouseX, int mouseY, Runnable action, Component tip) {
		boolean hover = mouseX >= x && mouseX <= x + 11.0F && mouseY >= y && mouseY <= y + 11.0F;
		Brush.chamfer(g, (int) x, (int) y, (int) x + 11, (int) y + 11, 1, hover ? Palette.mix(color, 0xFFFFFFFF, 0.25F) : color);
		Brush.centered(g, font, Component.literal(glyph), x + 5.5F, y + 2.0F, 0xFFFFF8E8, 0.7F, false);
		if (hover) {
			g.setTooltipForNextFrame(tip, mouseX, mouseY);
		}
		hits.add(new Hit(x, y, x + 11.0F, y + 11.0F, action));
	}

	// ================================================================== the mine

	private void mine(GuiGraphicsExtractor g, int mouseX, int mouseY, long now) {
		float top = y0 + 28.0F;
		CompoundTag mine = data.getCompoundOrEmpty("mine");
		if (!data.getBooleanOr("mine_on", true)) {
			Brush.fitCentered(g, font, Component.translatable("kultivator.sekte.kebun.ui.tambang_mati"), (x0 + x1) / 2.0F, top + 40.0F, 300.0F,
					Palette.INK_FAINT, 0.8F, 0.5F, false);
			return;
		}
		if (!mine.getBooleanOr("built", false)) {
			Brush.wrap(g, font, Component.translatable("kultivator.sekte.kebun.ui.belum_tambang"), x0 + 20.0F, top + 30.0F, x1 - x0 - 40.0F,
					Palette.INK_SOFT, 0.7F, false);
			return;
		}
		int level = Math.max(1, mine.getIntOr("level", 1));
		shaft(g, x0 + 14.0F, top, x0 + 96.0F, y1 - 40.0F, level, mine.getIntOr("danger", 0), now);
		float mid = x0 + 104.0F;
		float split = x0 + (x1 - x0) * 0.62F;
		// the shifts
		int slots = mine.getIntOr("slots", 2);
		Brush.text(g, font, Brush.bold(Component.translatable("kultivator.sekte.kebun.ui.giliran", slots, data.getIntOr("rest", 5))), mid, top,
				Palette.JADE_DARK, 0.66F, false);
		Map<Integer, CompoundTag> bySlot = workers(mine.getListOrEmpty("miners"));
		float y = top + 10.0F;
		float w = (split - mid - 6.0F) / 2.0F;
		for (int s = 1; s <= slots; s++) {
			float ax = mid + ((s - 1) % 2) * w;
			float ay = y + ((s - 1) / 2) * 23.0F;
			workerChip(g, "gali", s, bySlot.get(s), Component.translatable(s == 1 ? "kultivator.sekte.kebun.ui.slot_mulut"
					: "kultivator.sekte.kebun.ui.slot_dalam", s), ax, ay, ax + w - 4.0F, ay + 20.0F, mouseX, mouseY);
		}
		float below = y + ((slots + 1) / 2) * 23.0F + 4.0F;
		CompoundTag stat = data.getCompoundOrEmpty("stat");
		below += line(g, Component.translatable("kultivator.sekte.kebun.ui.statistik_tambang", stat.getIntOr("gali", 0), stat.getIntOr("inti", 0)), mid,
				below, split - mid - 6.0F, Palette.INK_SOFT, 0.56F);
		line(g, Component.translatable("kultivator.sekte.kebun.ui.giliran_tip"), mid, below + 1.0F, split - mid - 6.0F, Palette.INK_FAINT, 0.5F);
		Brush.rect(g, split, top, split + 0.8F, y1 - 36.0F, Palette.argb(0x60, Palette.JADE_DARK));
		table(g, mine, level, split + 8.0F, top, x1 - 12.0F, mouseX, mouseY);
	}

	/** The shaft: nine strata from the moss at the mouth down to the five-coloured deep, the depth reached lit. */
	private void shaft(GuiGraphicsExtractor g, float ax, float ay, float bx, float by, int level, int danger, long now) {
		int[] strata = {0xFF7C6A4A, 0xFF6F604A, 0xFF625A50, 0xFF55524F, 0xFF4A4A52, 0xFF403F4E, 0xFF3A3450, 0xFF35304E, 0xFF2E2846};
		float top = ay + 12.0F;
		float h = (by - top - 12.0F) / 9.0F;
		Brush.text(g, font, Brush.bold(Component.translatable("kultivator.sekte.kebun.ui.kedalaman", level)), ax, ay, Palette.JADE_DARK, 0.66F, false);
		// the hill and the mouth on top
		Brush.rect(g, ax, top - 3.0F, bx, top, 0xFF4F7A3A);
		float pulse = 0.5F + 0.5F * (float) Math.sin(now / 300.0D);
		for (int i = 0; i < 9; i++) {
			float sy = top + i * h;
			Brush.rect(g, ax, sy, bx, sy + h, strata[i]);
			// a vein or two in each stratum, brighter the deeper
			int vein = i >= 6 ? 0xFFE8C060 : i >= 3 ? 0xFFC9A04A : 0xFFB08850;
			Brush.rect(g, ax + 6.0F + (i * 13) % 40, sy + h * 0.4F, ax + 14.0F + (i * 13) % 40, sy + h * 0.4F + 1.0F, Palette.argb(0xB0, vein));
			if (i >= 6) {
				int[] five = {0xFF5AAAFF, 0xFFE8403A, 0xFFFFCE48, 0xFFF4F4EC, 0xFF4FB08E};
				Brush.dot(g, ax + 50.0F + (i * 7) % 20, sy + h * 0.6F, 1.2F, five[i % 5]);
			}
			int n = i + 1;
			int ink = n <= level ? 0xFFF4E8C8 : 0x90F4E8C8;
			Brush.text(g, font, Component.literal(String.valueOf(n)), bx - 8.0F, sy + h / 2.0F - 3.0F, ink, 0.55F, false);
			if (n >= 5) {
				Brush.rect(g, ax, sy, ax + 2.0F, sy + h, Palette.argb(0x90, Palette.DANGER));
			}
		}
		// the shaft cut down to the depth reached, with the lamp at its foot
		float shaftX = ax + (bx - ax) * 0.42F;
		float foot = top + level * h;
		Brush.rect(g, shaftX - 3.0F, top - 3.0F, shaftX + 3.0F, foot, 0xFF1A1410);
		Brush.rect(g, shaftX - 4.0F, top - 3.0F, shaftX - 3.0F, foot, 0xFF8A6A3A);
		Brush.rect(g, shaftX + 3.0F, top - 3.0F, shaftX + 4.0F, foot, 0xFF8A6A3A);
		Brush.halo(g, shaftX, foot - 3.0F, 2.0F, 6.0F, 0xFFC447, 0.5F + 0.4F * pulse);
		Brush.dot(g, shaftX, foot - 3.0F, 1.6F, 0xFFFFE6A8);
		if (danger > 0) {
			Brush.fit(g, font, Component.translatable("kultivator.sekte.kebun.ui.bahaya", danger), ax, by - 8.0F, bx - ax, Palette.DANGER, 0.55F, 0.35F,
					false);
		} else {
			Brush.fit(g, font, Component.translatable("kultivator.sekte.kebun.ui.aman"), ax, by - 8.0F, bx - ax, Palette.JADE_DARK, 0.55F, 0.35F, false);
		}
	}

	/** The yield table of the level and the cost of the next. */
	private void table(GuiGraphicsExtractor g, CompoundTag mine, int level, float left, float top, float right, int mouseX, int mouseY) {
		float y = top;
		y += line(g, Brush.bold(Component.translatable("kultivator.sekte.kebun.ui.tabel", level)), left, y, right - left, Palette.JADE_DARK, 0.66F);
		for (Tag tag : mine.getListOrEmpty("table")) {
			if (!(tag instanceof CompoundTag row) || y > y1 - 100.0F) {
				continue;
			}
			ItemStack icon = icon(row.getStringOr("item", ""));
			g.pose().pushMatrix();
			g.pose().translate(left, y - 1.0F);
			g.pose().scale(0.7F, 0.7F);
			g.item(icon, 0, 0);
			g.pose().popMatrix();
			Component name = icon.isEmpty() ? Component.literal("?") : icon.getHoverName();
			int pct = row.getIntOr("pct", 0);
			Brush.fit(g, font, Component.translatable("kultivator.sekte.kebun.ui.tabel_baris", row.getIntOr("count", 1), name), left + 14.0F, y + 1.5F,
					right - left - 40.0F, Palette.INK, 0.54F, 0.36F, false);
			Brush.text(g, font, Component.literal(pct + "%"), right - 22.0F, y + 1.5F, pct <= 3 ? Palette.GOLD_DARK : Palette.INK_SOFT, 0.54F, false);
			y += 11.0F;
		}
		y += 4.0F;
		if (!mine.contains("cost")) {
			line(g, Component.translatable("kultivator.sekte.kebun.ui.terdalam").withStyle(ChatFormatting.BOLD), left, y, right - left, Palette.GOLD_DARK,
					0.62F);
			return;
		}
		CompoundTag cost = mine.getCompoundOrEmpty("cost");
		y += line(g, Brush.bold(Component.translatable("kultivator.sekte.kebun.ui.perdalam_judul", level + 1)), left, y, right - left, Palette.JADE_DARK,
				0.62F);
		boolean okPk = cost.getBooleanOr("ok_pk", false);
		boolean okWood = cost.getLongOr("have_kayu", 0L) >= cost.getIntOr("kayu", 0);
		boolean okStone = cost.getLongOr("have_batu", 0L) >= cost.getIntOr("batu", 0);
		y += line(g, Component.translatable("kultivator.sekte.kebun.ui.biaya_pk", cost.getIntOr("pk", 0)), left + 4.0F, y, right - left - 4.0F,
				okPk ? Palette.JADE_DARK : Palette.DANGER, 0.56F);
		y += line(g, Component.translatable("kultivator.sekte.kebun.ui.biaya_kayu", cost.getIntOr("kayu", 0), cost.getLongOr("have_kayu", 0L)),
				left + 4.0F, y, right - left - 4.0F, okWood ? Palette.JADE_DARK : Palette.DANGER, 0.56F);
		y += line(g, Component.translatable("kultivator.sekte.kebun.ui.biaya_batu", cost.getIntOr("batu", 0), cost.getLongOr("have_batu", 0L)),
				left + 4.0F, y, right - left - 4.0F, okStone ? Palette.JADE_DARK : Palette.DANGER, 0.56F);
		y += 2.0F;
		boolean ok = okPk && okWood && okStone;
		button(g, left, y, right, Component.translatable("kultivator.sekte.kebun.ui.perdalam"), ok ? Palette.GOLD_DARK : Palette.LOCKED, mouseX, mouseY,
				() -> KebunClient.send("dalam", 0, ""));
	}

	// ================================================================== the worker list

	private void picker(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		float w = 230.0F;
		float h = Math.min(190.0F, y1 - y0 - 50.0F);
		float ax = (x0 + x1) / 2.0F - w / 2.0F;
		float ay = y0 + 30.0F;
		float bx = ax + w;
		float by = ay + h;
		// a modal sheet: the board's own buttons are off while choosing; a click on the sheet's paper does nothing, one outside it
		// closes the list (hits are tried in order, so the rows and Batal come first)
		hits.clear();
		g.fill((int) ax + 3, (int) ay + 4, (int) bx + 3, (int) by + 4, 0x70000000);
		g.fillGradient((int) ax, (int) ay, (int) bx, (int) by, Palette.PAPER_TOP, Palette.PAPER_SHADE);
		Brush.frame(g, (int) ax, (int) ay, (int) bx, (int) by, Palette.GOLD_DARK);
		boolean mine = "gali".equals(picking);
		Brush.fitCentered(g, font, Brush.bold(Component.translatable(mine ? "kultivator.sekte.kebun.ui.pilih_penambang" : "kultivator.sekte.kebun.ui.pilih_perawat",
				pickSlot)), (ax + bx) / 2.0F, ay + 5.0F, w - 20.0F, Palette.INK, 0.72F, 0.45F, false);
		Brush.fitCentered(g, font, Component.translatable(mine ? "kultivator.sekte.kebun.ui.akar_tambang" : "kultivator.sekte.kebun.ui.akar_kebun"),
				(ax + bx) / 2.0F, ay + 15.0F, w - 20.0F, Palette.INK_FAINT, 0.52F, 0.35F, false);
		float y = ay + 26.0F;
		ListTag cands = data.getListOrEmpty("cands");
		if (cands.isEmpty()) {
			Brush.wrap(g, font, Component.translatable("kultivator.sekte.kebun.ui.tak_ada_murid"), ax + 10.0F, y + 6.0F, w - 20.0F, Palette.INK_SOFT, 0.6F,
					false);
		}
		String kind = picking;
		int slot = pickSlot;
		int rest = data.getIntOr("rest", 5);
		for (Tag tag : cands) {
			if (!(tag instanceof CompoundTag c) || y > by - 24.0F) {
				continue;
			}
			Component name = c.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			boolean root = mine ? c.getBooleanOr("stone", false) : c.getBooleanOr("green", false);
			boolean defiant = c.getBooleanOr("defiant", false);
			boolean resting = c.getBooleanOr("resting", false);
			boolean busy = c.getBooleanOr("busy", false);
			boolean blocked = defiant || resting || busy;
			String job = c.getStringOr("job", "");
			float rowB = y + 13.0F;
			boolean hover = !blocked && mouseX >= ax + 6.0F && mouseX <= bx - 6.0F && mouseY >= y && mouseY <= rowB;
			if (hover) {
				Brush.rect(g, ax + 5.0F, y - 1.0F, bx - 5.0F, rowB, Palette.argb(0x60, Palette.JADE_LIGHT));
			}
			Brush.fit(g, font, root ? Brush.bold(name) : name, ax + 10.0F, y + 2.0F, 100.0F, blocked ? Palette.INK_FAINT : Palette.INK, 0.6F, 0.4F, false);
			Component note;
			int noteColor = Palette.INK_SOFT;
			if (defiant) {
				note = Component.translatable("kultivator.sekte.kebun.ui.c_membangkang");
				noteColor = Palette.DANGER;
			} else if (resting) {
				note = Component.translatable("kultivator.sekte.kebun.ui.c_libur");
			} else if (busy) {
				note = Component.translatable("kultivator.sekte.kebun.ui.c_terobosan");
			} else if (!job.isEmpty()) {
				note = Component.translatable("kultivator.sekte.kebun.ui.c_kerja." + ("tambang".equals(job) || "kebun".equals(job) || "tugas".equals(job)
						? job : "lain"));
			} else {
				note = Component.translatable("kultivator.sekte.kebun.ui.c_luang");
				noteColor = Palette.JADE_DARK;
			}
			int streak = c.getIntOr("streak", 0);
			if (streak > 0 && !blocked) {
				note = Component.translatable("kultivator.sekte.kebun.ui.c_beruntun", note, streak);
				if (streak >= rest) {
					noteColor = Palette.WARN;
				}
			}
			Brush.fit(g, font, note, ax + 114.0F, y + 2.5F, 84.0F, noteColor, 0.52F, 0.34F, false);
			if (root) {
				Brush.text(g, font, Component.literal(mine ? "⛏" : "✿"), bx - 22.0F, y + 2.0F, Palette.GOLD_DARK, 0.62F, false);
				if (hover) {
					g.setTooltipForNextFrame(Component.translatable(mine ? "kultivator.sekte.kebun.ui.akar_tambang" : "kultivator.sekte.kebun.ui.akar_kebun"),
							mouseX, mouseY);
				}
			}
			if (!blocked) {
				String soul = c.getStringOr("soul", "");
				hits.add(new Hit(ax + 6.0F, y, bx - 6.0F, rowB, () -> {
					picking = null;
					KebunClient.send(kind, slot, soul);
				}));
			}
			y += 14.0F;
		}
		button(g, bx - 70.0F, by - 18.0F, bx - 8.0F, Component.translatable("kultivator.sekte.kebun.ui.batal"), Palette.JADE_DULL, mouseX, mouseY,
				() -> picking = null);
		hits.add(new Hit(ax, ay, bx, by, () -> {
		}));
		hits.add(new Hit(-10000.0F, -10000.0F, 10000.0F, 10000.0F, () -> picking = null));
	}

	// ================================================================== helpers

	private void button(GuiGraphicsExtractor g, float ax, float ay, float bx, Component label, int color, int mouseX, int mouseY, Runnable action) {
		float by = ay + 13.0F;
		boolean hover = mouseX >= ax && mouseX <= bx && mouseY >= ay && mouseY <= by;
		Brush.chamfer(g, (int) ax, (int) ay, (int) bx, (int) by, 2, hover ? Palette.mix(color, 0xFFFFFFFF, 0.25F) : color);
		Brush.fitCentered(g, font, Brush.bold(label), (ax + bx) / 2.0F, ay + 3.0F, bx - ax - 6.0F, 0xFFFFF8E8, 0.62F, 0.38F, false);
		hits.add(new Hit(ax, ay, bx, by, action));
	}

	private float line(GuiGraphicsExtractor g, Component text, float x, float y, float room, int color, float scale) {
		return Brush.wrap(g, font, text, x, y, room, color, scale, false) + 1.5F;
	}

	private static Map<Integer, CompoundTag> workers(ListTag list) {
		Map<Integer, CompoundTag> out = new HashMap<>();
		for (Tag tag : list) {
			if (tag instanceof CompoundTag w) {
				out.put(w.getIntOr("slot", 0), w);
			}
		}
		return out;
	}

	/** The item of a registry id for an icon (made once per id while the board is open). */
	private ItemStack icon(String id) {
		return icons.computeIfAbsent(id, key -> {
			Identifier identifier = key.isEmpty() ? null : Identifier.tryParse(key);
			Item item = identifier == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(identifier);
			return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
		});
	}

	private Component herbName(String itemId) {
		ItemStack stack = icon(itemId);
		return stack.isEmpty() ? Component.literal("?") : Component.translatable(stack.getItem().getDescriptionId());
	}

	/** Ticks of growth left as world days, or hours under a day. */
	private static Component timeLeft(long ticks) {
		if (ticks >= 24000L) {
			return Component.translatable("kultivator.sekte.kebun.ui.hari", String.format(java.util.Locale.ROOT, "%.1f", ticks / 24000.0D));
		}
		return Component.translatable("kultivator.sekte.kebun.ui.jam", Math.max(1L, (ticks + 999L) / 1000L));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		for (Hit hit : List.copyOf(hits)) {
			if (hit.contains(mx, my)) {
				hit.action().run();
				return true;
			}
		}
		if (picking == null && mx >= x0 && mx <= x1 && my >= y0 && my <= y1) {
			selected = -1;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			if (picking != null) {
				picking = null;
				return true;
			}
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
