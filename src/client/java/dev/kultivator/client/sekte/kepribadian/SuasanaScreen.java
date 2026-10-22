package dev.kultivator.client.sekte.kepribadian;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.ScrollFrame;
import dev.kultivator.client.screen.ui.SlipTooltip;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.kepribadian.KepribadianPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * "Suasana Hati Murid", module P's hub card (section MURID, DESIGN 36.2.1): one jade scroll with every loaded disciple's
 * heart, the most troubled first: its state stripe, name, trait seals, a loyalty bar with the leaving threshold marked,
 * contentment and the countdown of one preparing to leave; below, the ones that left the sect and how. The list comes
 * from module P's {@code Daftar} payload, asked for when the scroll opens. Not a pause screen; the wheel scrolls.
 */
final class SuasanaScreen extends Screen {

	private static final String P = "kultivator.sekte.kepribadian.";
	private static final int MAX_W = 360;
	private static final int MAX_H = 250;
	private static final int ROW_H = 18;
	private static final long UNROLL_MILLIS = 250L;

	private static @Nullable KepribadianPayloads.Daftar latest;
	private static long version;

	private long seen = -1L;
	private long openedAt;
	private int scroll;
	private int x0;
	private int x1;
	private int top;
	private int bottom;
	private @Nullable List<Component> tooltip;

	SuasanaScreen() {
		super(Component.translatable(P + "suasana.judul"));
	}

	static void onList(KepribadianPayloads.Daftar list) {
		latest = list;
		version++;
	}

	static void open() {
		latest = null;
		KepribadianPayloads.Aksi ask = new KepribadianPayloads.Aksi(-1, KepribadianPayloads.A_LIST, "");
		if (ClientPlayNetworking.canSend(ask.type())) {
			ClientPlayNetworking.send(ask);
		}
		net.minecraft.client.Minecraft.getInstance().gui.setScreen(new SuasanaScreen());
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
		tooltip = null;
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
		Brush.fitCentered(g, font, Brush.bold(title), (cx0 + cx1) / 2.0F, cy, cx1 - cx0, Palette.SEAL_DARK, 1.0F, 0.6F, false);
		Brush.rect(g, cx0, cy + 11, cx1, cy + 11.6F, Palette.argb(0xB0, Palette.GOLD));
		int listTop = cy + 16;
		int listBottom = bottom - ScrollFrame.END - 4;
		KepribadianPayloads.Daftar list = latest;
		if (list == null) {
			Brush.fitCentered(g, font, Component.translatable(P + "suasana.memuat"), (cx0 + cx1) / 2.0F, listTop + 10, cx1 - cx0, Palette.INK_SOFT,
					0.8F, 0.5F, false);
			return;
		}
		g.enableScissor(cx0 - 2, listTop, cx1 + 2, listBottom);
		int yy = listTop - scroll;
		if (list.rows().isEmpty()) {
			Brush.fit(g, font, Component.translatable(P + "suasana.kosong"), cx0, yy + 4, cx1 - cx0, Palette.INK_SOFT, 0.75F, 0.5F, false);
			yy += ROW_H;
		}
		for (KepribadianPayloads.Baris row : list.rows()) {
			if (yy + ROW_H >= listTop && yy <= listBottom) {
				row(g, row, cx0, cx1, yy, mouseX, mouseY, listTop, listBottom);
			}
			yy += ROW_H;
		}
		yy += 4;
		if (!list.gone().isEmpty()) {
			Brush.fit(g, font, Brush.bold(Component.translatable(P + "suasana.yangpergi", list.gone().size())), cx0, yy, cx1 - cx0, Palette.SEAL_DARK,
					0.75F, 0.5F, false);
			yy += 11;
			for (KepribadianPayloads.Pergi gone : list.gone()) {
				Component how = Component.translatable("kultivator.sekte.kepergian." + gone.how());
				Component line = Component.empty().append(gone.name()).append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(how)
						.append(Component.translatable(P + "cmd.hari_lalu", Math.max(0L, list.today() - gone.day())));
				Brush.fit(g, font, line, cx0 + 4, yy, cx1 - cx0 - 4, Palette.INK_SOFT, 0.62F, 0.45F, false);
				yy += 9;
			}
		}
		g.disableScissor();
		int content = yy + scroll - listTop;
		maxScroll = Math.max(0, content - (listBottom - listTop));
		if (tooltip != null) {
			g.nextStratum();
			SlipTooltip.draw(g, font, tooltip, mouseX, mouseY, width, height);
		}
	}

	private int maxScroll;

	private void row(GuiGraphicsExtractor g, KepribadianPayloads.Baris row, int cx0, int cx1, int yy, int mouseX, int mouseY, int listTop,
			int listBottom) {
		int state = Math.max(0, Math.min(3, row.state()));
		int rgb = HatiTab.STATE_RGB[state];
		boolean hover = mouseX >= cx0 && mouseX < cx1 && mouseY >= Math.max(yy, listTop) && mouseY < Math.min(yy + ROW_H - 2, listBottom);
		Brush.rect(g, cx0, yy, cx1, yy + ROW_H - 2, Palette.argb(hover ? 0x50 : 0x2C, Palette.PAPER_DEEP));
		Brush.rect(g, cx0, yy, cx0 + 3, yy + ROW_H - 2, Palette.opaque(rgb));
		int nameW = Math.round((cx1 - cx0) * 0.30F);
		Brush.fit(g, font, Brush.bold(row.name()), cx0 + 6, yy + 4, nameW - 8, Palette.INK, 0.75F, 0.45F, false);
		int ix = cx0 + nameW;
		String[] ids = row.traits().isEmpty() ? new String[0] : row.traits().split(",");
		List<Component> traitNames = new ArrayList<>();
		for (String id : ids) {
			Sifat sifat = Sifat.byId(id);
			if (sifat == null) {
				continue;
			}
			int icon = sifat.ordinal();
			g.blit(RenderPipelines.GUI_TEXTURED, HatiTab.SIFAT_ATLAS, ix, yy + 3, (icon % 4) * 16.0F, (icon / 4) * 16.0F, 11, 11, 16, 16, 64, 48,
					0xFFFFFFFF);
			traitNames.add(Component.translatable(sifat.key()).withColor(HatiTab.AXIS_RGB[sifat.axis()]));
			ix += 12;
		}
		int barX = cx0 + nameW + 40;
		int barW = Math.max(30, Math.round((cx1 - cx0) * 0.28F));
		Brush.rect(g, barX, yy + 6, barX + barW, yy + 10, Palette.argb(0x50, Palette.PAPER_DEEP));
		Brush.rect(g, barX, yy + 6, barX + barW * Math.max(0, Math.min(100, row.loyalty())) / 100.0F, yy + 10, Palette.opaque(rgb));
		for (int mark : new int[]{30, 50}) {
			Brush.rect(g, barX + barW * mark / 100.0F - 0.5F, yy + 5, barX + barW * mark / 100.0F + 0.5F, yy + 11, Palette.INK_SOFT);
		}
		int tx = barX + barW + 6;
		String stateId = new String[]{"setia", "gelisah", "membangkang", "akan_pergi"}[state];
		Component label = row.daysLeft() >= 0 ? Component.translatable(P + "suasana.sisa",
				Component.translatable("kultivator.sekte.keadaan." + stateId), row.daysLeft())
				: Component.translatable("kultivator.sekte.keadaan." + stateId);
		Brush.fit(g, font, label, tx, yy + 4, cx1 - tx - 2, Palette.onPaper(rgb), 0.68F, 0.42F, false);
		if (hover) {
			List<Component> lines = new ArrayList<>();
			lines.add(row.name().copy().withStyle(ChatFormatting.GOLD));
			lines.addAll(traitNames);
			lines.add(Component.translatable(P + "suasana.angka", row.loyalty(), row.contentment()).withStyle(ChatFormatting.GRAY));
			if ((row.flags() & KepribadianPayloads.F_SEALED) != 0) {
				lines.add(Component.translatable(P + "tab.terikat").withStyle(ChatFormatting.LIGHT_PURPLE));
			}
			if ((row.flags() & KepribadianPayloads.F_PENDING) != 0) {
				lines.add(Component.translatable(P + "suasana.pending").withStyle(ChatFormatting.RED));
			}
			lines.add(Component.translatable(P + "suasana.petunjuk").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			tooltip = lines;
		}
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
