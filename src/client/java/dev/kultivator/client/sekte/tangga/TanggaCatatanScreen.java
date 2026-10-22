package dev.kultivator.client.sekte.tangga;

import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.ScrollFrame;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.sekte.tangga.TanggaPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * "Catatan Tangga" (R23 of the integration audit): the hub card of the Murid section opens this scroll instead of closing
 * the hub and printing to chat. One row per loaded disciple: steps of the last climb, its best, the heart verdict; a gold
 * row reached the peak. The data comes from module G's {@code Catatan} payload, asked for when the scroll opens (the same
 * records as {@code /kultivasi perguruan tangga}). Not a pause screen; the wheel scrolls; Esc returns to the hub.
 */
final class TanggaCatatanScreen extends Screen {

	private static final String G = "kultivator.sekte.tangga.";
	private static final int MAX_W = 320;
	private static final int MAX_H = 230;
	private static final int ROW_H = 14;
	private static final long UNROLL_MILLIS = 250L;

	private static @Nullable CompoundTag latest;
	private static long version;

	private long seen = -1L;
	private long openedAt;
	private int scroll;
	private int maxScroll;
	private int x0;
	private int x1;
	private int top;
	private int bottom;

	TanggaCatatanScreen() {
		super(Component.translatable("kultivator.hub.card.sekte_tangga_catatan"));
	}

	static void onRecords(TanggaPayloads.Catatan payload) {
		latest = payload.data();
		version++;
	}

	/** The hub card: asks the server and shows the scroll at once (a "loading" line until the answer). */
	static void open() {
		latest = null;
		version++;
		if (ClientPlayNetworking.canSend(TanggaPayloads.Minta.TYPE)) {
			ClientPlayNetworking.send(new TanggaPayloads.Minta());
		}
		HubNav.openChild(new TanggaCatatanScreen(), HubNav.MURID);
	}

	@Override
	protected void init() {
		if (openedAt == 0L) {
			openedAt = Util.getMillis();
			UiSound.open();
		}
		int w = Math.min(MAX_W, width - 16);
		int h = Math.min(MAX_H, height - 12);
		x0 = (width - w) / 2;
		x1 = x0 + w;
		top = (height - h) / 2;
		bottom = top + h;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, Palette.argb(0xB0, Palette.NIGHT), Palette.argb(0xD0, Palette.NIGHT_MIST));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		if (seen != version) {
			seen = version;
			scroll = 0;
		}
		float open = Eased.out(Math.min(1.0F, (Util.getMillis() - openedAt) / (float) UNROLL_MILLIS));
		int mid = (top + bottom) / 2;
		int half = Math.round((bottom - top) / 2.0F * open);
		ScrollFrame.paper(g, x0, x1, mid - half, mid + half, top, bottom);
		ScrollFrame.rod(g, x0, x1, mid - half);
		ScrollFrame.rod(g, x0, x1, mid + half);
		g.nextStratum();
		if (open < 0.95F) {
			return;
		}
		int cx0 = x0 + ScrollFrame.SIDE + 8;
		int cx1 = x1 - ScrollFrame.SIDE - 8;
		int cy = top + ScrollFrame.END + 6;
		CompoundTag data = latest;
		Component heading = data == null ? title : Component.translatable(G + "list.title",
				Component.translatable(data.getBooleanOr("stair", false) ? G + "list.stair" : G + "list.no_stair"));
		Brush.fitCentered(g, font, Brush.bold(heading), (cx0 + cx1) / 2.0F, cy, cx1 - cx0, Palette.SEAL_DARK, 1.0F, 0.55F, false);
		Brush.rect(g, cx0, cy + 11, cx1, cy + 11.6F, Palette.argb(0xB0, Palette.GOLD));
		int listTop = cy + 16;
		int listBottom = bottom - ScrollFrame.END - 16;
		if (data == null) {
			Brush.fitCentered(g, font, Component.translatable("kultivator.sekte_sambung.tangga.memuat"), (cx0 + cx1) / 2.0F, listTop + 10, cx1 - cx0,
					Palette.INK_SOFT, 0.8F, 0.5F, false);
			return;
		}
		ListTag rows = data.getListOrEmpty("rows");
		g.enableScissor(cx0 - 2, listTop, cx1 + 2, listBottom);
		int yy = listTop - scroll;
		if (rows.isEmpty()) {
			Brush.fit(g, font, Component.translatable(G + "list.empty"), cx0, yy + 3, cx1 - cx0, Palette.INK_SOFT, 0.75F, 0.5F, false);
			yy += ROW_H;
		}
		for (Tag tag : rows) {
			if (tag instanceof CompoundTag row && yy + ROW_H >= listTop && yy <= listBottom) {
				row(g, row, cx0, cx1, yy);
			}
			yy += ROW_H;
		}
		g.disableScissor();
		maxScroll = Math.max(0, yy + scroll - listTop - (listBottom - listTop));
		Brush.fit(g, font, Component.translatable(G + "list.hint", data.getIntOr("pk", 0), data.getIntOr("hari", 0)), cx0, listBottom + 3,
				cx1 - cx0, Palette.INK_SOFT, 0.6F, 0.4F, false);
	}

	private void row(GuiGraphicsExtractor g, CompoundTag row, int cx0, int cx1, int yy) {
		Component name = Component.literal(row.getStringOr("name", "?"));
		int steps = row.getIntOr("steps", -1);
		boolean peak = row.getBooleanOr("peak", false);
		Brush.rect(g, cx0, yy, cx1, yy + ROW_H - 2, Palette.argb(0x2C, Palette.PAPER_DEEP));
		if (peak) {
			Brush.rect(g, cx0, yy, cx0 + 3, yy + ROW_H - 2, Palette.opaque(0xF2C14E));
		}
		Component line = steps < 0 ? Component.translatable(G + "list.none", name)
				: Component.translatable(G + "list.line", name, steps, row.getIntOr("best", steps),
						Component.translatable(G + "hati." + Math.max(0, Math.min(3, row.getIntOr("heart", 0)))));
		Brush.fit(g, font, line, cx0 + 6, yy + 3, cx1 - cx0 - 8, peak ? Palette.SEAL_DARK : steps < 0 ? Palette.INK_SOFT : Palette.INK, 0.72F, 0.45F,
				false);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.round(scrollY * ROW_H)));
		return true;
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
