package dev.kultivator.client.dunia.kota;

import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.dunia.kota.KotaNet;
import dev.kultivator.dunia.kota.SainganData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Dunia › Musuh › Saingan (audit M5, S21, data from M2; DESIGN 43.5, 43.12.1: the old card screen is now this sub of the
 * Musuh page, laid out for the box it gets, R8): the rival groups of the towns that hunt the player, one row each with the
 * group's name in its colour, its style, where its region lies from here (only once it is known), the grudge it holds
 * against the player (a bar 0-100: what decides its ambushes), its record and whether its leader has fallen to the player;
 * at the foot today's ambushes and the shared enemy pressure against their daily caps. Showing the sub asks the server
 * ({@link KotaNet.SainganAsk}); the answer comes as a {@link KotaNet.Screen} of kind {@link KotaNet#SCREEN_SAINGAN}
 * ({@link KotaClient} hands it to {@link #onData}). Until then it says it is reading; a server without the list (an older
 * one) gets a plain line instead of a blank page. Read-only. Text at 1.0 (43.0.5), cut with "…" and whole in the tooltip.
 * <p>
 * Contract with the server (M2, {@code KotaNet.TAB_SAINGAN}, {@code Saingan.view}): data {@code {on:bool, band:int,
 * groups:[{id, sekte, pola, a, b, style:key, color:rgb, grudge:0-100, beaten:bool, wins, losses, known:bool, x, z}], ambush_today,
 * ambush_cap, pressure_today, pressure_cap, pending:bool}}; {@code x/z} are read only when {@code known}. A group is the
 * travelling band of NPC sect {@code sekte} (integration audit T12): {@code pola/a/b} are that sect's name words, rebuilt
 * with {@link SainganData#nameFrom} so the row reads exactly like the Diplomasi page and the map.
 */
public final class SainganScreen {

	/** The {@code KotaNet.Screen.screen} kind of the answer. */
	public static final int SCREEN = KotaNet.SCREEN_SAINGAN;
	private static final String K = "kultivator.saingan.ui.";
	private static final long WAIT_MILLIS = 3000L;
	private static final int ROW = 22;

	private static @Nullable CompoundTag data;
	private static long askedAt;

	private final DaftarGulir daftar = new DaftarGulir();

	/** Asks the server for the list (a quiet no-op while not connected). */
	public static void request() {
		askedAt = Util.getMillis();
		if (ClientPlayNetworking.canSend(KotaNet.SainganAsk.TYPE)) {
			// the sub is already on screen, so the answer only fills it
			ClientPlayNetworking.send(new KotaNet.SainganAsk(false));
		}
	}

	/** The server's answer: fills the sub; when the server asked to open it, the Gulungan opens there (43.4.4). */
	static void onData(boolean openIt, CompoundTag tag) {
		data = tag.copy();
		if (openIt && Minecraft.getInstance().player != null) {
			HubNav.buka(Tujuan.of(HubNav.DUNIA, Urutan.ID_MUSUH).sub("saingan"));
		}
	}

	/** Forgets the list (disconnect). */
	public static void reset() {
		data = null;
	}

	/** Draws the sub into the box. */
	public void gambar(Lukis l, int x0, int y0, int x1, int y1) {
		CompoundTag d = data;
		if (d == null) {
			boolean late = Util.getMillis() - askedAt > WAIT_MILLIS;
			Component line = late ? Component.translatable(K + "none")
					: Component.translatable(K + "loading").append(".".repeat((int) ((Util.getMillis() - askedAt) / 300L % 4L)));
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.LENTERA, line, null, null, x0, y0, x1, y1);
			return;
		}
		if (!d.getBooleanOr("on", true)) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.LENTERA, Component.translatable(K + "off"), null, null, x0, y0, x1, y1);
			return;
		}
		int room = x1 - x0 - 4;
		int y = y0 + 1;
		y += Math.round(l.bungkus(Component.translatable(K + "intro"), x0 + 2, y, room, l.gaya.teksLemah, 18.0F)) + 2;
		if (d.getBooleanOr("pending", false)) {
			l.teks(Component.translatable(K + "pending"), x0 + 2, y, room, Palette.DANGER);
			y += 10;
		}
		// today's pressure: ambushes of the rivals and the shared enemy budget (both daily caps of M2)
		int listBottom = y1 - 11;
		l.teks(Component.translatable(K + "today", d.getIntOr("ambush_today", 0), d.getIntOr("ambush_cap", 0),
				d.getIntOr("pressure_today", 0), d.getIntOr("pressure_cap", 0)), x0 + 2, y1 - 9, room, l.gaya.teksSamar);
		ListTag list = d.getListOrEmpty("groups");
		if (list.isEmpty()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.LENTERA, Component.translatable(K + "empty"), null, null, x0, y, x1, listBottom);
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		daftar.gambar(l, "saingan", x0, y, x1, listBottom, list.size(), ROW, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Tag t = list.get(i);
			if (t instanceof CompoundTag row) {
				row(lk, row, rx0, ry0, rx1, ry1, sorot, player);
			}
		}, null, null);
	}

	private static void row(Lukis l, CompoundTag row, int left, int top, int right, int bottom, boolean sorot, @Nullable LocalPlayer player) {
		l.kartu(left, top, right, bottom, sorot ? 1.0F : 0.0F, false);
		int rgb = row.getIntOr("color", 0x3A3050) & 0xFFFFFF;
		boolean beaten = row.getBooleanOr("beaten", false);
		int room = right - left - 10;
		// a colour chip, the name (the sect's name words, T12) and the style (a translation key)
		l.g.fill(left + 3, top + 2, left + 6, top + 10, Palette.opaque(rgb));
		Component name = SainganData.nameFrom(row);
		Component style = Component.translatable(row.getStringOr("style", "kultivator.kota.gaya." + SainganData.STYLES[SainganData.STYLES.length - 1]));
		int nameRoom = room * 3 / 5;
		l.teks(Component.translatable(K + "row", name, style), left + 9, top + 2, nameRoom, l.gaya.teks);
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
		l.teks(where, left + 13 + nameRoom, top + 2, right - left - 17 - nameRoom, l.gaya.teksLemah);
		// the grudge bar, its number, and the leader's record (or its fall)
		int grudge = Mth.clamp(row.getIntOr("grudge", 0), 0, 100);
		int barW = Math.min(90, room / 3);
		int barY = top + 15;
		int barColor = grudge >= 70 ? Palette.DANGER : grudge >= 35 ? Palette.GOLD_DARK : Palette.JADE_DARK;
		Bilah.gambar(l, left + 9, barY - 1, left + 9 + barW, grudge / 100.0F, barColor, null);
		Component grudgeText = Component.translatable(K + "grudge", grudge);
		int gx = left + 13 + barW;
		l.teks(grudgeText, gx, top + 12, l.font.width(grudgeText) + 1, barColor);
		int lx = gx + l.font.width(grudgeText) + 8;
		Component leader = beaten ? Component.translatable(K + "leader.beaten")
				: Component.translatable(K + "leader.alive", row.getIntOr("wins", 0), row.getIntOr("losses", 0));
		l.teks(leader, lx, top + 12, right - 4 - lx, beaten ? Palette.JADE_DARK : l.gaya.teksSamar);
		if (l.di(left, top, right, bottom)) {
			l.tip(List.of(Component.translatable(K + "row", name, style), where, grudgeText, leader));
		}
	}
}
