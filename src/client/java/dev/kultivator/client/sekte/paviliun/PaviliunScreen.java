package dev.kultivator.client.sekte.paviliun;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.ScrollFrame;
import dev.kultivator.client.screen.ui.SlipTooltip;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.sekte.paviliun.PaviliunPayloads;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Paviliun Kitab &amp; Makam Leluhur screen of module A (DESIGN 36.2.5 "UI"; hub card SEKTE "Paviliun &amp; Makam"), on
 * the jade scroll of the other Kultivator screens. Two talisman tabs, each a page painter of its own:
 * <ul>
 * <li>{@link RakScreen}, the Rak Kitab: the shelves as book spines coloured by grade, the free and locked places, the
 * chosen book's arts and who gave it, the readers with their progress and time left, "Setor" for the book in hand and
 * "Ambil kembali" (a confirming second click);</li>
 * <li>{@link MakamScreen}, the Makam Leluhur: the graves with their epitaphs and deeds and the "Abadikan" button, the
 * Kitab Silsilah, and the altar (blessing, tablets, freshness, the day's incense, the next Qingming and its button).</li>
 * </ul>
 * Every choice sends A's {@code PaviliunPayloads.Act}; the server checks it and answers with a fresh page.
 */
public final class PaviliunScreen extends Screen {

	static final Identifier SEAL = dev.kultivator.Kultivator.id("textures/sekte/paviliun/segel.png");
	private static final int MAX_W = 328;
	private static final int MAX_H = 240;
	private static final long UNROLL_MILLIS = 220L;

	CompoundTag rak = new CompoundTag();
	CompoundTag makam = new CompoundTag();
	int page;
	/** Makam sub-page: 0 graves, 1 lineage, 2 altar. */
	int sub;
	int scroll;
	int selectedBook = -1;
	boolean confirmWithdraw;
	@Nullable String confirmEnshrine;

	final List<Widget> widgets = new ArrayList<>();
	private @Nullable Widget pressed;
	private long openedAt;
	private long lastFrame;
	int left;
	int right;
	int top;
	int bottom;
	private int x0;
	private int x1;
	private int y0;
	private int y1;
	private static int lastSub;

	PaviliunScreen(int page, CompoundTag data) {
		super(Component.translatable("kultivator.sekte.paviliun.ui.title"));
		this.page = page;
		this.sub = lastSub;
		set(page, data);
	}

	/** A fresh page from the server. */
	void update(int newPage, CompoundTag data) {
		set(newPage, data);
		if (newPage != page) {
			page = newPage;
			scroll = 0;
		}
		confirmWithdraw = false;
		confirmEnshrine = null;
		rebuild();
	}

	private void set(int p, CompoundTag data) {
		if (p == PaviliunPayloads.PAGE_RAK) {
			rak = data;
		} else {
			makam = data;
		}
	}

	Font font() {
		return font;
	}

	int contentTop() {
		return top + 30;
	}

	// ================================================================== layout

	@Override
	protected void init() {
		if (openedAt == 0L) {
			openedAt = Util.getMillis();
			lastFrame = openedAt;
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
		rebuild();
	}

	void rebuild() {
		widgets.clear();
		pressed = null;
		int tabW = Math.min(104, (right - left - 8) / 2);
		int tx = (left + right) / 2 - (tabW * 2 + 4) / 2;
		String[] tabs = {"rak", "makam"};
		String[] ops = {PaviliunPayloads.OP_OPEN_RAK, PaviliunPayloads.OP_OPEN_MAKAM};
		for (int i = 0; i < 2; i++) {
			int index = i;
			widgets.add(new TalismanButton(tx + i * (tabW + 4), top + 12, tabW, 13, () -> Component.translatable("kultivator.sekte.paviliun.ui.tab." + tabs[index]))
					.stampedWhen(() -> page == index)
					.onPress(() -> {
						if (page != index) {
							UiSound.tab();
							PaviliunClient.send(ops[index], "");
						}
					}));
		}
		if (page == PaviliunPayloads.PAGE_RAK) {
			RakScreen.build(this);
		} else {
			MakamScreen.build(this);
		}
		widgets.add(new TalismanButton(right - 58, bottom - 13, 58, 13, () -> Component.translatable("kultivator.sekte.paviliun.ui.close"))
				.onPress(this::onClose));
	}

	void subPage(int s) {
		sub = s;
		lastSub = s;
		scroll = 0;
		UiSound.tab();
		rebuild();
	}

	// ================================================================== drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, Palette.argb(0xB0, Palette.NIGHT), Palette.argb(0xD0, Palette.NIGHT_MIST));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		long now = Util.getMillis();
		float seconds = Math.max(0.0F, Math.min(0.1F, (now - lastFrame) / 1000.0F));
		lastFrame = now;
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
		Component title = Component.translatable(page == PaviliunPayloads.PAGE_RAK ? "kultivator.sekte.paviliun.ui.title.rak"
				: "kultivator.sekte.paviliun.ui.title.makam");
		Brush.fitCentered(g, font, Brush.bold(title), (left + right) / 2.0F, top, right - left - 40, Palette.INK, 0.95F, 0.6F, false);
		seal(g, left + 8, top + 5, 14, page == PaviliunPayloads.PAGE_RAK ? "經" : "祠");
		if (page == PaviliunPayloads.PAGE_RAK) {
			RakScreen.draw(this, g, mouseX, mouseY);
		} else {
			MakamScreen.draw(this, g, mouseX, mouseY);
		}
		List<Component> tooltip = null;
		for (Widget widget : widgets) {
			widget.render(g, font, mouseX, mouseY, seconds);
			if (widget.contains(mouseX, mouseY) && !widget.tooltipLines().isEmpty()) {
				tooltip = widget.tooltipLines();
			}
		}
		super.extractRenderState(g, mouseX, mouseY, a);
		if (tooltip != null) {
			g.nextStratum();
			SlipTooltip.draw(g, font, tooltip, mouseX, mouseY, width, height);
		}
	}

	/** A vermilion seal stamp with a white glyph. */
	void seal(GuiGraphicsExtractor g, float cx, float cy, int size, String glyph) {
		int x = Math.round(cx - size / 2.0F);
		int y = Math.round(cy - size / 2.0F);
		g.blit(RenderPipelines.GUI_TEXTURED, SEAL, x, y, 0.0F, 0.0F, size, size, 32, 32, 32, 32, 0xFFFFFFFF);
		Component text = Component.literal(glyph);
		float s = size / 14.0F;
		g.pose().pushMatrix();
		g.pose().translate(cx - font.width(text) * s / 2.0F, cy - 4.0F * s);
		g.pose().scale(s, s);
		g.text(font, text, 0, 0, 0xFFFFF4E0, false);
		g.pose().popMatrix();
	}

	/** A heading with a gold rule under it; returns the y below. */
	float heading(GuiGraphicsExtractor g, Component text, float x, float y, float w) {
		Brush.fit(g, font, Brush.bold(text), x, y, w, Palette.SEAL_DARK, 0.75F, 0.5F, false);
		Brush.rect(g, x, y + 9.0F, x + w, y + 9.6F, Palette.argb(0xB0, Palette.GOLD));
		return y + 12.0F;
	}

	/** A thin progress bar with a bright head. */
	void bar(GuiGraphicsExtractor g, float x, float y, float w, float h, float t, int rgb) {
		float k = Math.max(0.0F, Math.min(1.0F, t));
		Brush.rect(g, x, y, x + w, y + h, Palette.argb(0x40, Palette.INK));
		Brush.rect(g, x, y, x + w * k, y + h, Palette.argb(0xE0, rgb));
		if (k > 0.02F && k < 0.999F) {
			Brush.rect(g, x + w * k - 1.0F, y - 0.5F, x + w * k, y + h + 0.5F, Palette.argb(0xFF, Palette.lighten(0xFF000000 | rgb, 0.5F)));
		}
		Brush.frame(g, Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), Palette.argb(0x80, Palette.INK_SOFT));
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
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int before = scroll;
		scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
		if (scroll != before) {
			rebuild();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
}
