package dev.kultivator.client.dunia.kota;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.KeyHints;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.ScrollFrame;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.dunia.kota.KotaNet;
import dev.kultivator.dunia.kota.SainganData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * The "Saingan" card of the hub's Dunia section (audit M5, S21; data from M2): the rival groups of the towns that hunt
 * the player, one row each with the group's name in its colour, its style, where its region lies from here (only once it
 * is known), the grudge it holds against the player (a bar 0-100: what decides its ambushes), its record and whether its
 * leader has fallen to the player; under the list today's ambushes and the shared enemy pressure against their daily caps.
 * Opening asks the server ({@link KotaNet.SainganAsk}); the answer comes as a {@link KotaNet.Screen} of kind
 * {@link KotaNet#SCREEN_SAINGAN} ({@link KotaClient} hands it here). Until then it says it is reading; a server without the
 * list (an older one) gets a plain line instead of a blank page. Read-only: nothing is sent but the request. Esc goes back
 * to the hub.
 * <p>
 * Contract with the server (M2, {@code KotaNet.TAB_SAINGAN}, {@code Saingan.view}): data {@code {on:bool, band:int,
 * groups:[{id, sekte, pola, a, b, style:key, color:rgb, grudge:0-100, beaten:bool, wins, losses, known:bool, x, z}], ambush_today,
 * ambush_cap, pressure_today, pressure_cap, pending:bool}}; {@code x/z} are read only when {@code known}. A group is the
 * travelling band of NPC sect {@code sekte} (integration audit T12): {@code pola/a/b} are that sect's name words, rebuilt
 * with {@link SainganData#nameFrom} so the row reads exactly like the Diplomasi screen and the map.
 */
public final class SainganScreen extends Screen {

	/** The {@code KotaNet.Screen.screen} kind of the answer. */
	public static final int SCREEN = KotaNet.SCREEN_SAINGAN;
	private static final String K = "kultivator.saingan.ui.";
	private static final long WAIT_MILLIS = 3000L;
	private static final int W = 360;
	private static final int H = 236;
	private static final int ROW = 22;

	private static @Nullable SainganScreen open;
	private @Nullable CompoundTag data;
	private final long openedAt = Util.getMillis();
	private final KeyHints.SealCorner help = new KeyHints.SealCorner("saingan");
	private int x0;
	private int y0;
	private int x1;
	private int y1;
	private int scroll;

	public SainganScreen() {
		super(Component.translatable(K + "title"));
	}

	/** Asks the server for the list (a quiet no-op while not connected). */
	public static void request() {
		if (ClientPlayNetworking.canSend(KotaNet.SainganAsk.TYPE)) {
			// the card opens this screen itself, so the answer only fills it
			ClientPlayNetworking.send(new KotaNet.SainganAsk(false));
		}
	}

	/** The server's answer: fills the open card, or opens one when the server asked to open it. */
	static void onData(boolean openIt, CompoundTag tag) {
		SainganScreen screen = open;
		if (screen != null) {
			screen.data = tag.copy();
			return;
		}
		if (openIt && Minecraft.getInstance().player != null) {
			SainganScreen fresh = new SainganScreen();
			fresh.data = tag.copy();
			Minecraft.getInstance().gui.setScreen(fresh);
		}
	}

	@Override
	protected void init() {
		open = this;
		int w = Math.min(W, width - 16);
		int h = Math.min(H, height - 12);
		x0 = (width - w) / 2;
		x1 = x0 + w;
		y0 = (height - h) / 2;
		y1 = y0 + h;
		help.place(x1 - 12.0F, y0 + 12.0F);
	}

	@Override
	public void removed() {
		if (open == this) {
			open = null;
		}
		super.removed();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, Palette.argb(0xA0, Palette.NIGHT), Palette.argb(0xC8, Palette.NIGHT_MIST));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		ScrollFrame.paper(g, x0, x1, y0, y1, y0, y1);
		ScrollFrame.rod(g, x0, x1, y0);
		ScrollFrame.rod(g, x0, x1, y1);
		g.nextStratum();
		drawList(g);
		help.render(g, font, mouseX, mouseY, width, height);
	}

	private void drawList(GuiGraphicsExtractor g) {
		float cx = (x0 + x1) / 2.0F;
		Brush.fitCentered(g, font, Brush.bold(title), cx, y0 + 9.0F, x1 - x0 - 48.0F, Palette.SEAL_DARK, 1.0F, 0.6F, false);
		Brush.rect(g, x0 + 14.0F, y0 + 21.0F, x1 - 14.0F, y0 + 21.6F, Palette.argb(0xB0, Palette.GOLD));
		float left = x0 + 14.0F;
		float room = x1 - x0 - 28.0F;
		CompoundTag d = data;
		if (d == null) {
			boolean late = Util.getMillis() - openedAt > WAIT_MILLIS;
			Component line = late ? Component.translatable(K + "none") : Component.translatable(K + "loading")
					.append(".".repeat((int) ((Util.getMillis() - openedAt) / 300L % 4L)));
			Brush.wrap(g, font, line, left, (y0 + y1) / 2.0F - 6.0F, room, Palette.INK_SOFT, 0.8F, false);
			return;
		}
		float y = y0 + 25.0F;
		if (!d.getBooleanOr("on", true)) {
			Brush.wrap(g, font, Component.translatable(K + "off"), left, (y0 + y1) / 2.0F - 6.0F, room, Palette.INK_SOFT, 0.8F, false);
			return;
		}
		y += Brush.wrap(g, font, Component.translatable(K + "intro"), left, y, room, Palette.INK_SOFT, 0.62F, false) + 3.0F;
		// today's pressure: ambushes of the rivals and the shared enemy budget (both daily caps of M2)
		Component today = Component.translatable(K + "today", d.getIntOr("ambush_today", 0), d.getIntOr("ambush_cap", 0),
				d.getIntOr("pressure_today", 0), d.getIntOr("pressure_cap", 0));
		Brush.fit(g, font, today, left, y1 - 12.0F, room * 0.58F, Palette.INK_SOFT, 0.55F, 0.45F, false);
		if (d.getBooleanOr("pending", false)) {
			Brush.fit(g, font, Component.translatable(K + "pending").withStyle(ChatFormatting.RED), left, y, room, Palette.DANGER, 0.6F, 0.45F, false);
			y += 8.0F;
		}
		var list = d.getListOrEmpty("groups");
		if (list.isEmpty()) {
			Brush.wrap(g, font, Component.translatable(K + "empty"), left, y + 4.0F, room, Palette.INK_FAINT, 0.72F, false);
			return;
		}
		float listTop = y;
		float listBottom = y1 - 16.0F;
		int fits = Math.max(1, (int) ((listBottom - listTop) / ROW));
		scroll = Mth.clamp(scroll, 0, Math.max(0, list.size() - fits));
		LocalPlayer player = Minecraft.getInstance().player;
		for (int i = scroll; i < list.size() && i < scroll + fits; i++) {
			Tag t = list.get(i);
			if (t instanceof CompoundTag row) {
				drawRow(g, row, left, listTop + (i - scroll) * ROW, room, player);
			}
		}
		if (list.size() > fits) {
			Brush.fit(g, font, Component.translatable(K + "more", scroll + 1, Math.min(list.size(), scroll + fits), list.size()), left + room * 0.6F,
					y1 - 12.0F, room * 0.4F, Palette.INK_FAINT, 0.55F, 0.45F, false);
		}
	}

	private void drawRow(GuiGraphicsExtractor g, CompoundTag row, float left, float top, float room, @Nullable LocalPlayer player) {
		int rgb = row.getIntOr("color", 0x3A3050) & 0xFFFFFF;
		boolean beaten = row.getBooleanOr("beaten", false);
		// a colour chip, the name (the sect's name words, T12) and the style (a translation key)
		Brush.rect(g, left, top + 1.0F, left + 4.0F, top + 9.0F, Palette.opaque(rgb));
		Component name = SainganData.nameFrom(row);
		Component style = Component.translatable(row.getStringOr("style", "kultivator.kota.gaya." + SainganData.STYLES[SainganData.STYLES.length - 1]));
		Component head = Component.translatable(K + "row", Brush.bold(name), style);
		float nameRoom = room * 0.62F;
		Brush.fit(g, font, head, left + 7.0F, top, nameRoom, Palette.INK, 0.7F, 0.5F, false);
		// where: direction and distance from here once the region is known
		Component where;
		if (row.getBooleanOr("known", false) && player != null) {
			double dx = row.getIntOr("x", 0) + 0.5D - player.getX();
			double dz = row.getIntOr("z", 0) + 0.5D - player.getZ();
			int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
			// 0 = north (-Z), clockwise in eighths
			double degrees = Math.toDegrees(Math.atan2(dx, -dz));
			int octant = Math.floorMod((int) Math.round(degrees / 45.0D), 8);
			where = Component.translatable(K + "where", Component.translatable(K + "arah." + octant), distance);
		} else {
			where = Component.translatable(K + "where.unknown");
		}
		Brush.fit(g, font, where, left + 7.0F + nameRoom + 4.0F, top + 0.5F, room - nameRoom - 11.0F, Palette.INK_SOFT, 0.6F, 0.45F, false);
		// the grudge bar, and the fallen leader's mark
		int grudge = Mth.clamp(row.getIntOr("grudge", 0), 0, 100);
		float barX = left + 7.0F;
		float barW = Math.min(110.0F, room * 0.4F);
		float barY = top + 11.0F;
		Brush.rect(g, barX, barY, barX + barW, barY + 3.0F, Palette.argb(0x50, Palette.INK_FAINT));
		int barColor = grudge >= 70 ? Palette.DANGER : grudge >= 35 ? Palette.GOLD_DARK : Palette.JADE_DARK;
		Brush.rect(g, barX, barY, barX + barW * grudge / 100.0F, barY + 3.0F, barColor);
		Brush.fit(g, font, Component.translatable(K + "grudge", grudge), barX + barW + 4.0F, barY - 2.0F, room * 0.3F, Palette.INK_SOFT, 0.55F,
				0.45F, false);
		Component leader = beaten ? Component.translatable(K + "leader.beaten").withStyle(ChatFormatting.DARK_GREEN)
				: Component.translatable(K + "leader.alive", row.getIntOr("wins", 0), row.getIntOr("losses", 0));
		float leaderX = barX + barW + 4.0F + room * 0.3F;
		Brush.fit(g, font, leader, leaderX, barY - 2.0F, left + room - leaderX, beaten ? Palette.JADE_DARK : Palette.INK_FAINT, 0.55F, 0.45F, false);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0.0D) {
			scroll += scrollY > 0.0D ? -1 : 1;
			scroll = Math.max(0, scroll);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && help.click(event.x(), event.y())) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (help.release(event.x(), event.y())) {
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public void onClose() {
		UiSound.close();
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
