package dev.kultivator.client.sekte.paviliun;

import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.sekte.paviliun.PaviliunPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;

/**
 * The painter of the Makam sub (DESIGN 36.2.5, 43.5 Sekte › Kas &amp; Toko › makam): three groups stacked under headings
 * in one scroll (43.0.4; they were three small tabs):
 * <ul>
 * <li><b>Makam</b>: one row per grave: a small tombstone (gilt when enshrined), the name and realm, the day and the way
 * they fell, the deeds carved under it, and "Abadikan (n PK)" behind a light confirm, or the gilt mark of an enshrined
 * tablet;</li>
 * <li><b>Silsilah</b>: the Kitab Silsilah, newest first, with the folded count of the earliest generations;</li>
 * <li><b>Altar</b>: the offering table with its tablets, the blessing meter (tablets and freshness), the day's incense, the
 * Qingming countdown and its button (which closes the Gulungan so the ceremony is seen).</li>
 * </ul>
 * Text is 1.0 (43.0.5).
 */
final class MakamScreen {

	private static final int ROW_H = 31;
	private static final int LINE_H = 10;
	private static final int BTN_H = 14;
	private static final String U = "kultivator.sekte.paviliun.ui.";

	/** Paints the Makam from {@code y} between {@code x0} and {@code x1}; returns the y under the content. */
	static int lukis(PaviliunScreen s, Lukis l, int x0, int y, int x1) {
		CompoundTag v = s.lembar();
		y = graves(s, l, v, x0, y, x1) + 6;
		y = lineage(l, v, x0, y, x1) + 6;
		return altar(s, l, v, x0, y, x1);
	}

	private static int graves(PaviliunScreen s, Lukis l, CompoundTag v, int x0, int y, int x1) {
		Font font = s.font();
		ListTag list = v.getListOrEmpty("graves");
		int slots = v.getIntOr("slots", 0);
		int cost = v.getIntOr("cost", 50);
		int pool = v.getIntOr("pool", 0);
		int w = x1 - x0;
		y += l.kepala(Component.translatable(U + "sub.makam"), x0, y, x1);
		if (list.isEmpty()) {
			y += Math.round(l.bungkus(Component.translatable(U + "graves.none"), x0 + 2, y, w - 4, l.gaya.teksLemah)) + 2;
			y += Math.round(l.bungkus(Component.translatable(slots > 0 ? U + "graves.built" : U + "graves.unbuilt", slots), x0 + 2, y, w - 4,
					l.gaya.teksSamar));
			return y;
		}
		int bw = Math.min(w / 3, 96);
		for (int i = 0; i < list.size(); i++) {
			if (!(list.get(i) instanceof CompoundTag e)) {
				continue;
			}
			if (i % 2 == 0) {
				l.g.fill(x0, y - 1, x1, y + ROW_H - 2, Palette.argb(0x14, Palette.INK));
			}
			boolean enshrined = e.getBooleanOr("enshrined", false);
			stone(l.g, x0 + 2, y + 1, enshrined);
			int tx = x0 + 18;
			int tw = x1 - bw - 4 - tx;
			Component name = e.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			Component realm = e.read("realm", ComponentSerialization.CODEC).orElse(Component.empty());
			int gold = Palette.argb(0xFF, 0x8A671B);
			int nameW = Math.min(tw, font.width(Brush.bold(name)));
			l.teks(Brush.bold(name), tx, y, nameW, enshrined ? gold : l.gaya.teks);
			l.teks(realm, tx + nameW + 4, y, tw - nameW - 4, l.gaya.teksLemah);
			Component cause = e.read("cause_text", ComponentSerialization.CODEC).orElse(Component.empty());
			int slot = e.getIntOr("slot", -1);
			Component fell = Component.translatable(U + "grave.fell", e.getLongOr("day", 0L), cause,
					slot >= 0 ? Component.translatable(U + "grave.slot", slot + 1) : Component.translatable(U + "grave.noslot"));
			l.teks(fell, tx, y + 10, tw, l.gaya.teksLemah);
			MutableComponent deeds = Component.empty();
			int n = 0;
			for (Tag t : e.getListOrEmpty("deeds")) {
				if (t instanceof CompoundTag d) {
					if (n > 0) {
						deeds.append(Component.literal(" · "));
					}
					deeds.append(Component.translatable(d.getStringOr("t", ""), d.getStringOr("a", "")));
					n++;
				}
			}
			if (n == 0) {
				deeds.append(Component.translatable(U + "grave.nodeeds"));
			}
			l.teks(deeds, tx, y + 20, x1 - tx, Palette.argb(0xFF, 0x3E5A48));
			if (enshrined) {
				l.teks(Component.translatable(U + "enshrined"), x1 - bw, y + 2, bw, gold);
			} else {
				String soul = e.read("soul", net.minecraft.core.UUIDUtil.CODEC).map(Object::toString).orElse("");
				boolean mampu = pool >= cost;
				TalismanButton.gambar(l, "makam:abadi" + i, x1 - bw, y + 1, bw, BTN_H, Component.translatable(U + "enshrine", cost), mampu && !soul.isEmpty(),
						Component.translatable(U + "enshrine.tip", name, cost, pool),
						() -> s.tanya(Component.translatable("kultivator.gui43.m08.makam.abadikan.tanya", name, cost),
								() -> PaviliunClient.send(PaviliunPayloads.OP_ENSHRINE, soul)));
				if (l.di(x1 - bw, y + 1, x1, y + 1 + BTN_H)) {
					l.tip(Component.translatable(U + "enshrine.tip", name, cost, pool).withStyle(ChatFormatting.GRAY));
				}
			}
			y += ROW_H;
		}
		y += Math.round(l.bungkus(Component.translatable(U + "graves.foot", list.size(), Math.max(slots, list.size()), v.getIntOr("buried", 0), pool),
				x0 + 2, y, w - 4, l.gaya.teksSamar));
		return y;
	}

	private static int lineage(Lukis l, CompoundTag v, int x0, int y, int x1) {
		ListTag list = v.getListOrEmpty("lineage");
		int w = x1 - x0;
		y += l.kepala(Component.translatable(U + "sub.silsilah"), x0, y, x1);
		if (list.isEmpty()) {
			return y + Math.round(l.bungkus(Component.translatable(U + "lineage.none"), x0 + 2, y, w - 4, l.gaya.teksLemah));
		}
		int gold = Palette.argb(0xFF, 0x8A671B);
		for (Tag t : list) {
			if (!(t instanceof CompoundTag e)) {
				continue;
			}
			boolean enshrined = e.getBooleanOr("enshrined", false);
			Brush.dot(l.g, x0 + 3, y + 4.0F, 1.5F, enshrined ? Palette.argb(0xFF, Palette.GOLD) : Palette.argb(0xC0, Palette.INK_SOFT));
			Component name = e.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			Component realm = e.read("realm", ComponentSerialization.CODEC).orElse(Component.empty());
			Component cause = e.read("cause_text", ComponentSerialization.CODEC).orElse(Component.empty());
			Component line = Component.translatable(U + "lineage.line", name, realm, e.getLongOr("day", 0L), cause);
			l.teks(line, x0 + 8, y, w - 22, enshrined ? gold : l.gaya.teks);
			if (enshrined) {
				l.kanan(Component.literal("✦"), x1, y, Palette.argb(0xFF, Palette.GOLD_DARK));
			}
			y += LINE_H;
		}
		return y + Math.round(l.bungkus(Component.translatable(U + "lineage.foot", v.getIntOr("lineage_total", list.size()), v.getIntOr("folded", 0)),
				x0 + 2, y + 1, w - 4, l.gaya.teksSamar)) + 1;
	}

	private static int altar(PaviliunScreen s, Lukis l, CompoundTag v, int x0, int y, int x1) {
		int w = x1 - x0;
		y += l.kepala(Component.translatable(U + "sub.altar"), x0, y, x1);
		boolean lebar = w >= 300;
		int half = lebar ? (w - 8) / 2 : w;
		int top = y;
		GuiGraphicsExtractor g = l.g;
		// the offering table with its spirit tablets
		int tablets = v.getIntOr("tablets", 0);
		int tw = Math.min(half, 160);
		float tableY = y + 30;
		Brush.rect(g, x0 + 6, tableY, x0 + tw - 6, tableY + 4, Palette.argb(0xFF, 0x7A1E16));
		Brush.rect(g, x0 + 6, tableY + 4, x0 + tw - 6, tableY + 5, Palette.argb(0xFF, 0x45100B));
		Brush.rect(g, x0 + 10, tableY + 5, x0 + 12, tableY + 14, Palette.argb(0xFF, 0x5A160F));
		Brush.rect(g, x0 + tw - 12, tableY + 5, x0 + tw - 10, tableY + 14, Palette.argb(0xFF, 0x5A160F));
		int shown = Math.min(12, tablets);
		float span = tw - 24;
		for (int i = 0; i < shown; i++) {
			float tx = x0 + 12 + (shown == 1 ? span / 2.0F : span * i / (shown - 1)) - 2;
			float th = 18 + (i % 3 == 1 ? 4 : 0);
			Brush.rect(g, tx - 1, tableY - th - 1, tx + 5, tableY, Palette.argb(0xFF, 0x3A1E10));
			Brush.rect(g, tx, tableY - th, tx + 4, tableY - 1, Palette.argb(0xFF, 0x2A1610));
			Brush.rect(g, tx + 1, tableY - th + 2, tx + 3, tableY - 3, Palette.argb(0xD0, Palette.GOLD));
		}
		if (shown == 0) {
			l.tengah(Component.translatable(U + "altar.empty"), x0 + tw / 2.0F, tableY - 14, tw - 8, l.gaya.teksSamar);
		}
		// incense in the censer (a few curling strokes while today's incense burns)
		boolean incense = v.getBooleanOr("incense_today", false);
		float cx = x0 + tw / 2.0F;
		Brush.rect(g, cx - 5, tableY + 6, cx + 5, tableY + 12, Palette.argb(0xFF, 0x6E5A2A));
		if (incense) {
			double t = Util.getMillis() / 600.0D;
			for (int k = 0; k < 5; k++) {
				float sy = tableY + 4 - k * 5;
				float sx = cx + (float) Math.sin(t + k * 0.9D) * (1.5F + k);
				Brush.dot(g, sx, sy, 1.2F + k * 0.25F, Palette.argb(Math.max(20, 150 - k * 28), 0xD8D8D0));
			}
		}
		int ly = Math.round(tableY) + 18;
		int blessing = v.getIntOr("blessing", 0);
		l.teks(Component.translatable(U + "altar.blessing", String.format(java.util.Locale.ROOT, "%.1f", blessing / 10.0D)), x0, ly, half, Palette.SEAL_DARK);
		PaviliunScreen.bar(g, x0, ly + 10, half, 4, blessing / 150.0F, 0xD4A637);
		ly += 18;
		l.teks(Component.translatable(U + "altar.tablets", tablets, Math.min(10, tablets)), x0, ly, half, l.gaya.teks);
		ly += 10;
		int fresh = v.getIntOr("fresh", 0);
		l.teks(Component.translatable(U + "altar.fresh", fresh), x0, ly, half, l.gaya.teks);
		PaviliunScreen.bar(g, x0, ly + 10, half, 3, fresh / 100.0F, 0x67C99B);
		ly += 16;
		l.teks(Component.translatable(incense ? U + "altar.incense.done" : U + "altar.incense.todo"), x0, ly, half, incense ? Palette.JADE_DARK : Palette.WARN);
		ly += 12;

		// Qingming and the rules: beside the table when wide, under it when narrow
		int rx = lebar ? x0 + half + 8 : x0;
		int ry = lebar ? top : ly;
		ry += l.kepala(Component.translatable(U + "qingming"), rx, ry, x1);
		long left = v.getLongOr("qingming_left", 0L);
		boolean running = v.getBooleanOr("qingming_running", false);
		boolean due = left <= 0L;
		Component when = running ? Component.translatable(U + "qingming.now") : due ? Component.translatable(U + "qingming.due")
				: Component.translatable(U + "qingming.left", left);
		l.teks(when, rx, ry, half, running || due ? Palette.SEAL : l.gaya.teks);
		ry += 11;
		ry += Math.round(l.bungkus(Component.translatable(U + "qingming.about", v.getIntOr("qingming_every", 15)), rx, ry, half, l.gaya.teksLemah)) + 3;
		Component label = Component.translatable(running ? U + "qingming.running" : U + "qingming.start");
		int bw = Math.min(half, TalismanButton.lebar(s.font(), label, 90, 180));
		TalismanButton.gambar(l, "makam:qingming", rx, ry, bw, BTN_H, label, due && !running, Component.translatable(U + "qingming.tip"), () -> {
			PaviliunClient.send(PaviliunPayloads.OP_QINGMING, "");
			// the ceremony is seen in the world (as before: the screen closed)
			HubNav.tutupSemua();
		}, due && !running, false);
		if (l.di(rx, ry, rx + bw, ry + BTN_H)) {
			l.tip(Component.translatable(U + "qingming.tip").withStyle(ChatFormatting.GRAY));
		}
		ry += BTN_H + 4;
		ry += l.kepala(Component.translatable(U + "altar.rules"), rx, ry, x1);
		ry += Math.round(l.bungkus(Component.translatable(v.getBooleanOr("altar", false) ? U + "altar.rules.text" : U + "altar.rules.noaltar"), rx, ry, half,
				l.gaya.teksLemah));
		return Math.max(ly, ry);
	}

	/** A little tombstone: a rounded grey stone on a base, gilt when its tablet is on the altar. */
	private static void stone(GuiGraphicsExtractor g, float x, float y, boolean gilt) {
		int face = gilt ? 0xFFE2D2A0 : 0xFFB8BCB6;
		int edge = gilt ? 0xFF8A671B : 0xFF6E746E;
		Brush.rect(g, x + 1, y + 3, x + 11, y + 20, edge);
		Brush.rect(g, x + 2, y + 2, x + 10, y + 20, face);
		Brush.rect(g, x + 3, y + 1, x + 9, y + 2, face);
		Brush.rect(g, x, y + 20, x + 12, y + 23, 0xFF5C5048);
		Brush.rect(g, x + 5.5F, y + 5, x + 6.5F, y + 17, gilt ? 0xFF8A671B : 0xFF4A504A);
	}

	private MakamScreen() {
	}
}
