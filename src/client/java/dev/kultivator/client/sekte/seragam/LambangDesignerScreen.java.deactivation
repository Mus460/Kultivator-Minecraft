package dev.kultivator.client.sekte.seragam;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.Kultivator;
import dev.kultivator.client.disciple.power.PhysiqueVisuals;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.JadeCoin;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.ScrollFrame;
import dev.kultivator.client.screen.ui.SlipTooltip;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.sekte.api.Lambang;
import dev.kultivator.sekte.seragam.SeragamData;
import dev.kultivator.sekte.seragam.SeragamPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The emblem and uniform designer (DESIGN 36.2.8; Hub › Sekte › "Lambang &amp; Seragam", {@code /kultivasi perguruan
 * lambang}). A jade scroll that fits 320 × 240: on the left the live preview (the banner cloth exactly as the poles will
 * fly it, the bare emblem, a paper doll of the chosen rank's robe in its colours and cut, the motto and the sect
 * generation); on the right two tabs:
 * <ul>
 * <li><b>Lambang</b>: the eight shapes, the thirty-two symbols, the three colours (field, symbol, border) from the
 * 32-colour palette, the motto (≤ 32 letters) and "Acak".</li>
 * <li><b>Seragam</b>: for each of the six sect ranks the robe colour and trim colour ("Asli" keeps today's Dao tint and
 * rank trim) and the cut (Jubah Panjang, Pendek, Tempur), and "Samakan semua jenjang".</li>
 * </ul>
 * "Simpan" sends the whole design ({@code LambangSet}); the footnote says whether today's free change is left or what the
 * next one costs from the hall pool. Only the hall master edits; anyone else sees it read-only. Tooltips on every
 * control; Esc returns to the hub when opened from it.
 */
public final class LambangDesignerScreen extends Screen {

	private static final Identifier ATLAS = Kultivator.id("textures/sekte/seragam/lambang_atlas.png");
	private static final int MAX_W = 304;
	private static final int MAX_H = 228;
	private static final long UNROLL_MILLIS = 220L;
	private static final String[] RANK_IDS = {"tercatat", "luar", "dalam", "inti", "tetua", "tetua_agung"};
	/** A neutral swatch that stands for "the old look" (the Dao tint is per disciple). */
	private static final int ASLI_ROBE = 0xC9D3DC;

	private final @Nullable Screen parent;
	private final String key;
	private final List<Widget> widgets = new ArrayList<>();
	private @Nullable Widget pressed;
	private @Nullable List<Component> tooltip;
	private long openedAt;
	private int left;
	private int right;
	private int top;
	private int bottom;
	private int x0;
	private int x1;
	private int y0;
	private int y1;
	private @Nullable EditBox motto;

	private SeragamData original;
	private SeragamData working;
	private int freeLeft;
	private int cost;
	private int pool;
	private final boolean canEdit;
	private final boolean hasHall;
	private int tab;
	private int colourSlot;
	private int rank;
	private long savedAt = Long.MIN_VALUE / 2;
	/** Rows of the right panel, set by {@link #build} and read by {@link #drawPaper}. */
	private int shapeY;
	private int symbolY;
	private int robeY;
	private int trimY;
	private int cutY;

	public LambangDesignerScreen(@Nullable Screen parent, String key, int freeLeft, int cost, int pool, int flags) {
		super(Component.translatable("kultivator.sekte.seragam.ui.title"));
		this.parent = parent;
		this.key = key;
		LambangCache.Entry entry = LambangCache.get(key);
		this.original = entry == null ? SeragamData.DEFAULT : entry.data;
		this.working = original;
		this.freeLeft = freeLeft;
		this.cost = cost;
		this.pool = pool;
		this.canEdit = (flags & SeragamPayloads.LambangOpen.OPEN_EDIT) != 0;
		this.hasHall = (flags & SeragamPayloads.LambangOpen.OPEN_HALL) != 0;
	}

	/** A newer {@code LambangOpen} for the same hall (after a save): refresh the cost line and the saved design. */
	void refresh(int freeLeft, int cost, int pool) {
		this.freeLeft = freeLeft;
		this.cost = cost;
		this.pool = pool;
		LambangCache.Entry entry = LambangCache.get(key);
		if (entry != null) {
			boolean pristine = working.sameDesign(original);
			original = entry.data;
			if (pristine) {
				working = original;
				if (motto != null) {
					motto.setValue(working.motto());
				}
			}
		}
	}

	String key() {
		return key;
	}

	// ================================================================== layout

	@Override
	protected void init() {
		if (openedAt == 0L) {
			openedAt = Util.getMillis();
			UiSound.open();
		}
		int w = Math.min(MAX_W, width - 12);
		int h = Math.min(MAX_H, height - 8);
		x0 = (width - w) / 2;
		x1 = x0 + w;
		y0 = (height - h) / 2 + 4;
		y1 = y0 + h - 8;
		left = x0 + ScrollFrame.SIDE + 8;
		right = x1 - ScrollFrame.SIDE - 8;
		top = y0 + ScrollFrame.END + 4;
		bottom = y1 - ScrollFrame.END - 4;
		widgets.clear();
		pressed = null;
		motto = null;
		build();
	}

	private int panelLeft() {
		return left + 100;
	}

	private void build() {
		int px = panelLeft();
		int pw = right - px;
		// the tabs
		int tw = 52;
		add(new TalismanButton(right - tw * 2 - 4, top, tw, 12, () -> Component.translatable("kultivator.sekte.seragam.ui.tab.lambang"))
				.stampedWhen(() -> tab == 0).onPress(() -> switchTab(0))
				.tooltip(() -> List.of(Component.translatable("kultivator.sekte.seragam.ui.tab.lambang.tip"))));
		add(new TalismanButton(right - tw, top, tw, 12, () -> Component.translatable("kultivator.sekte.seragam.ui.tab.seragam"))
				.stampedWhen(() -> tab == 1).onPress(() -> switchTab(1))
				.tooltip(() -> List.of(Component.translatable("kultivator.sekte.seragam.ui.tab.seragam.tip"))));
		int y = top + 16;
		if (tab == 0) {
			// shapes (label above), 8 cells of 18
			shapeY = y + 7;
			int cell = Math.min(18, (pw - 7 * 2) / 8);
			for (int i = 0; i < Lambang.SHAPES; i++) {
				int shape = i;
				add(new MaskCell(px + i * (cell + 2), shapeY, cell, cell, true, shape)).onPress(() -> edit(e -> new Lambang(shape, e.symbol(), e.field(), e.charge(), e.border())))
						.tooltip(() -> List.of(Component.translatable("kultivator.sekte.seragam.bentuk." + shape)));
			}
			// symbols, 8 × 4 cells of 16
			symbolY = shapeY + cell + 9;
			int sc = Math.min(16, (pw - 7 * 2) / 8);
			for (int i = 0; i < Lambang.SYMBOLS; i++) {
				int symbol = i;
				add(new MaskCell(px + (i % 8) * (sc + 2), symbolY + (i / 8) * (sc + 2), sc, sc, false, symbol))
						.onPress(() -> edit(e -> new Lambang(e.shape(), symbol, e.field(), e.charge(), e.border())))
						.tooltip(() -> List.of(Component.translatable("kultivator.sekte.seragam.simbol." + symbol)));
			}
			y = symbolY + 4 * (sc + 2) + 1;
			// the three colour slots, then the palette
			int slotW = (pw - 2 * 3) / 3;
			for (int s = 0; s < 3; s++) {
				int slot = s;
				add(new JadeCoin(px + s * (slotW + 3), y, slotW, 11, () -> Component.translatable("kultivator.sekte.seragam.ui.warna." + slot),
						() -> colourSlot == slot).onPress(() -> colourSlot = slot)
						.tooltip(() -> List.of(Component.translatable("kultivator.sekte.seragam.ui.warna." + slot + ".tip"))));
			}
			y += 13;
			addPalette(px, y, pw, false, index -> edit(e -> switch (colourSlot) {
				case 0 -> new Lambang(e.shape(), e.symbol(), index, e.charge(), e.border());
				case 1 -> new Lambang(e.shape(), e.symbol(), e.field(), index, e.border());
				default -> new Lambang(e.shape(), e.symbol(), e.field(), e.charge(), index);
			}), () -> switch (colourSlot) {
				case 0 -> working.emblem().field();
				case 1 -> working.emblem().charge();
				default -> working.emblem().border();
			});
			y += 2 * 10 + 2;
			// the motto (its hint names it)
			motto = new EditBox(font, px, y, pw, 12, Component.translatable("kultivator.sekte.seragam.ui.semboyan"));
			motto.setMaxLength(SeragamData.MOTTO_MAX);
			motto.setValue(working.motto());
			motto.setHint(Component.translatable("kultivator.sekte.seragam.ui.semboyan.hint"));
			motto.setResponder(text -> working = working.withMotto(text));
			motto.setEditable(canEdit);
			addRenderableWidget(motto);
		} else {
			// ranks
			int rw = (pw - 5 * 2) / 6;
			for (int r = 0; r < SeragamData.RANKS; r++) {
				int which = r;
				add(new JadeCoin(px + r * (rw + 2), y, rw, 12, () -> Component.translatable("kultivator.sekte.seragam.ui.jenjang." + which),
						() -> rank == which).onPress(() -> rank = which)
						.tooltip(() -> List.of(Component.translatable("kultivator.hall.rank." + RANK_IDS[which]))));
			}
			robeY = y + 22;
			addPalette(px, robeY, pw, true, index -> working = working.withRank(rank, index, working.trimIndex(rank)), () -> working.robeIndex(rank));
			trimY = robeY + 29;
			addPalette(px, trimY, pw, true, index -> working = working.withRank(rank, working.robeIndex(rank), index), () -> working.trimIndex(rank));
			cutY = trimY + 29;
			int cw = (pw - 2 * 3) / 3;
			for (int c = 0; c < SeragamData.CUTS; c++) {
				int cut = c;
				add(new JadeCoin(px + c * (cw + 3), cutY, cw, 12, () -> Component.translatable("kultivator.sekte.seragam.ui.potongan." + cut),
						() -> working.cutOf(rank) == cut).onPress(() -> working = working.withCut(rank, cut))
						.enabledWhen(() -> canEdit)
						.tooltip(() -> List.of(Component.translatable("kultivator.sekte.seragam.ui.potongan." + cut + ".tip"))));
			}
			add(new TalismanButton(px, cutY + 16, pw, 12, () -> Component.translatable("kultivator.sekte.seragam.ui.salin"))
					.onPress(this::copyToAllRanks).enabledWhen(() -> canEdit)
					.tooltip(() -> List.of(Component.translatable("kultivator.sekte.seragam.ui.salin.tip"))));
		}
		// footer: Acak, Kembalikan, Simpan
		int fy = bottom - 13;
		int bw = 56;
		add(new TalismanButton(panelLeft(), fy, bw, 13, () -> Component.translatable("kultivator.sekte.seragam.ui.acak"))
				.onPress(this::randomise).enabledWhen(() -> canEdit && tab == 0)
				.tooltip(() -> List.of(Component.translatable("kultivator.sekte.seragam.ui.acak.tip"))));
		add(new TalismanButton(panelLeft() + bw + 4, fy, bw, 13, () -> Component.translatable("kultivator.sekte.seragam.ui.batal"))
				.onPress(this::revert).enabledWhen(() -> canEdit && !working.sameDesign(original))
				.tooltip(() -> List.of(Component.translatable("kultivator.sekte.seragam.ui.batal.tip"))));
		add(new TalismanButton(right - 64, fy, 64, 13, () -> Component.translatable("kultivator.sekte.seragam.ui.simpan"))
				.urgentWhen(() -> canEdit && (!working.sameDesign(original) || !original.designed())).onPress(this::save)
				.enabledWhen(() -> canEdit && Util.getMillis() - savedAt > 800L)
				.tooltip(() -> List.of(costLine(), Component.translatable("kultivator.sekte.seragam.ui.simpan.tip"))));
	}

	private <W extends Widget> W add(W widget) {
		widgets.add(widget);
		return widget;
	}

	private void switchTab(int to) {
		if (tab != to) {
			tab = to;
			UiSound.tab();
			rebuildWidgets();
		}
	}

	/** Two rows of 16 palette swatches (plus "Asli" in front when {@code withAsli}). */
	private void addPalette(int px, int y, int pw, boolean withAsli, java.util.function.IntConsumer pick, java.util.function.IntSupplier current) {
		int startX = px;
		int size = 9;
		if (withAsli) {
			add(new Swatch(startX, y, 18, size * 2 + 1, -1, current)).onPress(() -> pick.accept(-1)).enabledWhen(() -> canEdit)
					.tooltip(() -> List.of(Component.translatable("kultivator.sekte.seragam.ui.asli"), Component.translatable("kultivator.sekte.seragam.ui.asli.tip")));
			startX += 21;
		}
		int step = Math.min(10, (px + pw - startX) / 16);
		for (int i = 0; i < Lambang.PALETTE.length; i++) {
			int index = i;
			add(new Swatch(startX + (i % 16) * step, y + (i / 16) * (size + 1), step - 1, size, index, current)).onPress(() -> pick.accept(index))
					.enabledWhen(() -> canEdit)
					.tooltip(() -> List.of(Component.translatable("kultivator.sekte.seragam.warna." + index)));
		}
	}

	private void edit(java.util.function.UnaryOperator<Lambang> change) {
		if (canEdit) {
			working = working.withEmblem(change.apply(working.emblem()));
		}
	}

	private void copyToAllRanks() {
		SeragamData data = working;
		for (int r = 0; r < SeragamData.RANKS; r++) {
			data = data.withRank(r, working.robeIndex(rank), working.trimIndex(rank)).withCut(r, working.cutOf(rank));
		}
		working = data;
	}

	private void randomise() {
		working = working.withEmblem(Lambang.seeded(ThreadLocalRandom.current().nextLong()));
	}

	private void revert() {
		working = original;
		if (motto != null) {
			motto.setValue(working.motto());
		}
	}

	private void save() {
		savedAt = Util.getMillis();
		ClientPlayNetworking.send(new SeragamPayloads.LambangSet(working));
	}

	private Component costLine() {
		if (!hasHall) {
			return Component.translatable("kultivator.sekte.seragam.ui.tanpa_aula");
		}
		if (!canEdit) {
			return Component.translatable("kultivator.sekte.seragam.ui.bukan_ketua");
		}
		return freeLeft > 0 ? Component.translatable("kultivator.sekte.seragam.ui.biaya.gratis", freeLeft)
				: Component.translatable("kultivator.sekte.seragam.ui.biaya.pk", cost, pool);
	}

	// ================================================================== drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, Palette.argb(0xB0, Palette.NIGHT), Palette.argb(0xD0, Palette.NIGHT_MIST));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		long now = Util.getMillis();
		float seconds = 0.05F;
		tooltip = null;
		float open = Eased.out(Math.min(1.0F, (now - openedAt) / (float) UNROLL_MILLIS));
		int mid = (y0 + y1) / 2;
		int half = Math.round((y1 - y0) / 2.0F * open);
		ScrollFrame.paper(g, x0, x1, mid - half, mid + half, y0, y1);
		ScrollFrame.rod(g, x0, x1, mid - half);
		ScrollFrame.rod(g, x0, x1, mid + half);
		g.nextStratum();
		if (open < 0.95F) {
			return;
		}
		drawPaper(g);
		for (Widget widget : widgets) {
			widget.render(g, font, mouseX, mouseY, seconds);
			if (widget.contains(mouseX, mouseY) && !widget.tooltipLines().isEmpty()) {
				tooltip = widget.tooltipLines();
			}
		}
		super.extractRenderState(g, mouseX, mouseY, a);
		if (tooltip == null && mouseX >= left && mouseX < panelLeft() + 40 && mouseY >= top && mouseY < top + 10) {
			tooltip = List.of(title, Component.translatable("kultivator.sekte.seragam.sumber"));
		}
		if (tooltip != null && !tooltip.isEmpty()) {
			g.nextStratum();
			SlipTooltip.draw(g, font, tooltip, mouseX, mouseY, width, height);
		}
	}

	private void drawPaper(GuiGraphicsExtractor g) {
		Brush.fit(g, font, Brush.bold(title), left, top + 2, panelLeft() - left + 40, Palette.SEAL_DARK, 0.85F, 0.55F, false);
		// ---- the preview column
		Identifier preview = LambangAtlas.preview(working);
		int px = left;
		int py = top + 16;
		Brush.text(g, font, Component.translatable("kultivator.sekte.seragam.ui.bendera"), px, py, Palette.INK_SOFT, 0.6F, false);
		py += 7;
		// a shadowed lacquer arm and the cloth below it
		g.fill(px + 2, py, px + 46, py + 3, 0xFF6E160F);
		g.fill(px + 2, py, px + 4, py + 3, 0xFFD4A637);
		g.fill(px + 44, py, px + 46, py + 3, 0xFFD4A637);
		if (preview != null) {
			g.blit(RenderPipelines.GUI_TEXTURED, preview, px + 4, py + 3, 0.0F, 0.0F, 40, 80, 64, 128, 128, 128);
			g.blit(RenderPipelines.GUI_TEXTURED, preview, px + 50, py + 3, 64.0F, 0.0F, 44, 44, 64, 64, 128, 128);
		}
		Brush.text(g, font, Component.translatable("kultivator.sekte.seragam.ui.generasi", working.generation()), px + 50, py + 50,
				Palette.INK_SOFT, 0.55F, false);
		Component mottoText = working.motto().isEmpty() ? Component.translatable("kultivator.sekte.seragam.no_motto")
				: Component.literal("「" + working.motto() + "」");
		Brush.wrap(g, font, mottoText, px + 50, py + 58, 44, Palette.INK, 0.55F, false);
		// the robe doll of the chosen rank
		int dy = py + 90;
		Brush.text(g, font, Component.translatable("kultivator.sekte.seragam.ui.jubah_prev").copy().append(" · ")
				.append(Component.translatable("kultivator.hall.rank." + RANK_IDS[rank])), px, dy, Palette.INK_SOFT, 0.55F, false);
		drawDoll(g, px + 22, dy + 8, preview);
		// read-only and cost lines at the foot of the preview column
		Brush.wrap(g, font, costLine(), left, bottom - 22, 94, canEdit ? Palette.JADE_DARK : Palette.SEAL_DARK, 0.55F, false);
		// ---- section labels of the right panel
		int rx = panelLeft();
		if (tab == 0) {
			Brush.text(g, font, Component.translatable("kultivator.sekte.seragam.ui.bentuk"), rx, shapeY - 6, Palette.INK_SOFT, 0.55F, false);
			Brush.text(g, font, Component.translatable("kultivator.sekte.seragam.ui.simbol"), rx, symbolY - 6, Palette.INK_SOFT, 0.55F, false);
		} else {
			Brush.text(g, font, Component.translatable("kultivator.sekte.seragam.ui.jubah"), rx, robeY - 7, Palette.INK_SOFT, 0.55F, false);
			Brush.text(g, font, Component.translatable("kultivator.sekte.seragam.ui.trim"), rx, trimY - 7, Palette.INK_SOFT, 0.55F, false);
			Brush.text(g, font, Component.translatable("kultivator.sekte.seragam.ui.potongan"), rx, cutY - 7, Palette.INK_SOFT, 0.55F, false);
		}
	}

	/** A paper doll of the robe: body, sleeves, sash, skirt of the cut's length, trims, the emblem on the chest. */
	private void drawDoll(GuiGraphicsExtractor g, int cx, int y, @Nullable Identifier preview) {
		int robe = working.robeRgb(rank) >= 0 ? working.robeRgb(rank) : ASLI_ROBE;
		int trim = working.trimRgb(rank) >= 0 ? working.trimRgb(rank) : PhysiqueVisuals.RANK_TRIM[rank];
		int cut = working.cutOf(rank);
		int body = 0xFF000000 | robe;
		int dark = 0xFF000000 | LambangAtlas.mix(robe, 0x000000, 0.25F);
		int edge = 0xFF000000 | trim;
		// head and hands
		g.fill(cx - 4, y, cx + 4, y + 8, 0xFFE8C9A8);
		g.fill(cx - 4, y, cx + 4, y + 2, 0xFF2B2018);
		// torso
		g.fill(cx - 8, y + 9, cx + 8, y + 25, body);
		// sleeves: wide cuffs for the long and short robes, tight with bracers for the battle robe
		int sleeve = cut == SeragamData.CUT_TEMPUR ? 3 : 5;
		g.fill(cx - 8 - sleeve, y + 9, cx - 8, y + 23, dark);
		g.fill(cx + 8, y + 9, cx + 8 + sleeve, y + 23, dark);
		if (cut == SeragamData.CUT_TEMPUR) {
			g.fill(cx - 8 - sleeve, y + 17, cx - 8, y + 23, 0xFF5A3A22);
			g.fill(cx + 8, y + 17, cx + 8 + sleeve, y + 23, 0xFF5A3A22);
			g.fill(cx - 9, y + 9, cx + 9, y + 11, 0xFF5A3A22);
		} else {
			g.fill(cx - 8 - sleeve, y + 21, cx - 8, y + 23, edge);
			g.fill(cx + 8, y + 21, cx + 8 + sleeve, y + 23, edge);
		}
		g.fill(cx - 4 - sleeve - 3, y + 23, cx - 8 - sleeve + 4, y + 26, 0xFFE8C9A8);
		g.fill(cx + 8 + sleeve - 4, y + 23, cx + 4 + sleeve + 7, y + 26, 0xFFE8C9A8);
		// the crossed collar
		for (int i = 0; i < 6; i++) {
			g.fill(cx - 3 + i, y + 9 + i, cx - 1 + i, y + 10 + i, edge);
		}
		// sash or plated belt
		g.fill(cx - 8, y + 21, cx + 8, y + 24, cut == SeragamData.CUT_TEMPUR ? 0xFF5A3A22 : 0xFF2C2C34);
		// skirt by cut
		int skirt = cut == SeragamData.CUT_PANJANG ? 22 : (cut == SeragamData.CUT_PENDEK ? 11 : 15);
		g.fill(cx - 9, y + 24, cx + 9, y + 24 + skirt, body);
		g.fill(cx - 9, y + 24 + skirt - 2, cx + 9, y + 24 + skirt, edge);
		if (cut == SeragamData.CUT_TEMPUR) {
			g.fill(cx - 1, y + 28, cx + 1, y + 24 + skirt, 0xFF2C2C34);
		}
		// legs below a short or battle robe
		if (cut != SeragamData.CUT_PANJANG) {
			g.fill(cx - 6, y + 24 + skirt, cx - 1, y + 46, cut == SeragamData.CUT_TEMPUR ? 0xFF5A3A22 : dark);
			g.fill(cx + 1, y + 24 + skirt, cx + 6, y + 46, cut == SeragamData.CUT_TEMPUR ? 0xFF5A3A22 : dark);
		}
		// the emblem embroidered on the chest
		if (preview != null) {
			g.blit(RenderPipelines.GUI_TEXTURED, preview, cx + 1, y + 12, 64.0F, 0.0F, 6, 6, 64, 64, 128, 128);
		}
	}

	// ================================================================== input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			for (Widget widget : widgets) {
				if (widget.contains(event.x(), event.y())) {
					widget.press();
					pressed = widget;
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		Widget widget = pressed;
		if (widget != null) {
			pressed = null;
			widget.release(event.x(), event.y());
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public void onClose() {
		if (parent != null && minecraft != null) {
			minecraft.gui.setScreen(parent);
			return;
		}
		super.onClose();
	}

	@Override
	public void removed() {
		UiSound.close();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ================================================================== controls

	/** A shape or symbol cell: the mask from the atlas tinted with the working colours, a gold ring when chosen. */
	private final class MaskCell extends Widget {
		private final boolean shape;
		private final int index;

		MaskCell(int x, int y, int w, int h, boolean shape, int index) {
			super(x, y, w, h);
			this.shape = shape;
			this.index = index;
		}

		@Override
		public boolean isEnabled() {
			return canEdit;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
			Lambang e = working.emblem();
			boolean chosen = shape ? e.shape() == index : e.symbol() == index;
			int press = pressOffset(over);
			int bx = x + press;
			int by = y + press;
			g.fill(bx, by, bx + w, by + h, chosen ? Palette.argb(0x60, Palette.GOLD_LIGHT) : Palette.argb(0x28, Palette.INK));
			if (shape) {
				int u = index * 64;
				g.blit(RenderPipelines.GUI_TEXTURED, ATLAS, bx + 1, by + 1, u, 0.0F, w - 2, h - 2, 64, 64, 512, 384, 0xFF000000 | e.fieldRgb());
				g.blit(RenderPipelines.GUI_TEXTURED, ATLAS, bx + 1, by + 1, u, 64.0F, w - 2, h - 2, 64, 64, 512, 384, 0xFF000000 | e.borderRgb());
			} else {
				int u = (index % 8) * 64;
				int v = 128 + (index / 8) * 64;
				g.blit(RenderPipelines.GUI_TEXTURED, ATLAS, bx + 1, by + 1, 0.0F, 0.0F, w - 2, h - 2, 64, 64, 512, 384,
						Palette.argb(0x70, e.fieldRgb()));
				g.blit(RenderPipelines.GUI_TEXTURED, ATLAS, bx + 1, by + 1, u, v, w - 2, h - 2, 64, 64, 512, 384, 0xFF000000 | e.chargeRgb());
			}
			if (chosen) {
				Brush.frame(g, bx - 1, by - 1, bx + w + 1, by + h + 1, Palette.GOLD);
			} else if (hover.get() > 0.05F) {
				Brush.frame(g, bx, by, bx + w, by + h, Palette.argb(Math.round(0xA0 * hover.get()), Palette.SEAL_GLOW));
			}
		}
	}

	/** A palette swatch (index 0-31) or the "Asli" swatch (index -1). */
	private final class Swatch extends Widget {
		private final int index;
		private final java.util.function.IntSupplier current;

		Swatch(int x, int y, int w, int h, int index, java.util.function.IntSupplier current) {
			super(x, y, w, h);
			this.index = index;
			this.current = current;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
			boolean chosen = current.getAsInt() == index;
			int press = pressOffset(over);
			int bx = x + press;
			int by = y + press;
			if (index < 0) {
				g.fill(bx, by, bx + w, by + h, 0xFFE6EDDB);
				Brush.line(g, bx + 1, by + h - 1, bx + w - 1, by + 1, 1.0F, Palette.SEAL);
				Brush.centered(g, font, Component.translatable("kultivator.sekte.seragam.ui.asli.short"), bx + w / 2.0F, by + h / 2.0F - 2.0F,
						Palette.INK, 0.5F, false);
			} else {
				g.fill(bx, by, bx + w, by + h, 0xFF000000 | Lambang.PALETTE[index]);
			}
			Brush.frame(g, bx, by, bx + w, by + h, chosen ? Palette.GOLD : Palette.argb(0x60, Palette.INK));
			if (chosen) {
				Brush.frame(g, bx - 1, by - 1, bx + w + 1, by + h + 1, Palette.SEAL);
			}
		}
	}
}
