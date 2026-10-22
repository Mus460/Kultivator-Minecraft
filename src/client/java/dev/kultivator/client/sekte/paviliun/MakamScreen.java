package dev.kultivator.client.sekte.paviliun;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
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

import java.util.List;

/**
 * The Makam Leluhur page of {@link PaviliunScreen} (DESIGN 36.2.5) under three small tabs:
 * <ul>
 * <li><b>Makam</b>: one row per grave: a small tombstone (gilt when enshrined), the name and realm, the day and the way
 * they fell, the deeds carved under it, and "Abadikan (n PK)" (a confirming second click) or the gilt mark of an
 * enshrined tablet;</li>
 * <li><b>Silsilah</b>: the Kitab Silsilah, newest first, with the folded count of the earliest generations;</li>
 * <li><b>Altar</b>: the offering table with its tablets, the blessing meter (tablets and freshness), the day's incense, the
 * Qingming countdown and its button.</li>
 * </ul>
 */
final class MakamScreen {

	private static final int ROW_H = 31;
	private static final int LINE_H = 11;

	static void build(PaviliunScreen s) {
		String[] subs = {"makam", "silsilah", "altar"};
		int tw = Math.min(70, (s.right - s.left - 16) / 3);
		int tx = (s.left + s.right) / 2 - (tw * 3 + 8) / 2;
		for (int i = 0; i < 3; i++) {
			int index = i;
			s.widgets.add(new TalismanButton(tx + i * (tw + 4), s.contentTop(), tw, 11, () -> Component.translatable("kultivator.sekte.paviliun.ui.sub." + subs[index]))
					.stampedWhen(() -> s.sub == index)
					.onPress(() -> s.subPage(index)));
		}
		CompoundTag v = s.makam;
		int cost = v.getIntOr("cost", 50);
		int pool = v.getIntOr("pool", 0);
		if (s.sub == 0 || s.sub == 1) {
			ListTag list = v.getListOrEmpty(s.sub == 0 ? "graves" : "lineage");
			int rowH = s.sub == 0 ? ROW_H : LINE_H;
			int visible = Math.max(1, (s.bottom - 20 - listTop(s)) / rowH);
			s.scroll = Math.max(0, Math.min(s.scroll, Math.max(0, list.size() - visible)));
			for (int i = 0; i < visible && i + s.scroll < list.size(); i++) {
				if (!(list.get(i + s.scroll) instanceof CompoundTag g) || g.getBooleanOr("enshrined", false)) {
					continue;
				}
				String soul = g.read("soul", net.minecraft.core.UUIDUtil.CODEC).map(Object::toString).orElse("");
				int y = listTop(s) + i * rowH;
				int bw = s.sub == 0 ? 74 : 56;
				int by = s.sub == 0 ? y + 9 : y - 1;
				Component name = g.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
				s.widgets.add(new TalismanButton(s.right - bw - 2, by, bw, 11, () -> Component.translatable(soul.equals(s.confirmEnshrine)
						? "kultivator.sekte.paviliun.ui.enshrine.sure" : "kultivator.sekte.paviliun.ui.enshrine", cost))
						.urgentWhen(() -> soul.equals(s.confirmEnshrine))
						.enabledWhen(() -> pool >= cost)
						.onPress(() -> {
							if (soul.equals(s.confirmEnshrine)) {
								PaviliunClient.send(PaviliunPayloads.OP_ENSHRINE, soul);
							} else {
								s.confirmEnshrine = soul;
								UiSound.ask();
							}
						})
						.tooltip(() -> List.of(Component.translatable("kultivator.sekte.paviliun.ui.enshrine.tip", name, cost, pool)
								.withStyle(ChatFormatting.GRAY))));
			}
		} else {
			boolean due = v.getLongOr("qingming_left", 1L) <= 0L;
			boolean running = v.getBooleanOr("qingming_running", false);
			s.widgets.add(new TalismanButton(s.left + 4, s.bottom - 13, Math.min(140, s.right - s.left - 70), 13,
					() -> Component.translatable(running ? "kultivator.sekte.paviliun.ui.qingming.running" : "kultivator.sekte.paviliun.ui.qingming.start"))
					.urgentWhen(() -> due && !running)
					.enabledWhen(() -> due && !running)
					.onPress(() -> {
						PaviliunClient.send(PaviliunPayloads.OP_QINGMING, "");
						s.onClose();
					})
					.tooltip(() -> List.of(Component.translatable("kultivator.sekte.paviliun.ui.qingming.tip").withStyle(ChatFormatting.GRAY))));
		}
	}

	private static int listTop(PaviliunScreen s) {
		return s.contentTop() + 16;
	}

	// ================================================================== drawing

	static void draw(PaviliunScreen s, GuiGraphicsExtractor g, int mouseX, int mouseY) {
		switch (s.sub) {
			case 0 -> graves(s, g);
			case 1 -> lineage(s, g);
			default -> altar(s, g);
		}
	}

	private static void graves(PaviliunScreen s, GuiGraphicsExtractor g) {
		Font font = s.font();
		CompoundTag v = s.makam;
		ListTag list = v.getListOrEmpty("graves");
		int slots = v.getIntOr("slots", 0);
		float w = s.right - s.left - 8;
		if (list.isEmpty()) {
			float y = listTop(s) + 4;
			y += Brush.wrap(g, font, Component.translatable("kultivator.sekte.paviliun.ui.graves.none"), s.left + 6, y, w - 4, Palette.INK_SOFT, 0.62F,
					false) + 4;
			Brush.wrap(g, font, Component.translatable(slots > 0 ? "kultivator.sekte.paviliun.ui.graves.built" : "kultivator.sekte.paviliun.ui.graves.unbuilt",
					slots), s.left + 6, y, w - 4, Palette.INK_FAINT, 0.55F, false);
			return;
		}
		int visible = Math.max(1, (s.bottom - 20 - listTop(s)) / ROW_H);
		for (int i = 0; i < visible && i + s.scroll < list.size(); i++) {
			if (!(list.get(i + s.scroll) instanceof CompoundTag e)) {
				continue;
			}
			float y = listTop(s) + i * ROW_H;
			if (i % 2 == 0) {
				g.fill(s.left, Math.round(y - 1), s.right, Math.round(y + ROW_H - 2), Palette.argb(0x14, Palette.INK));
			}
			boolean enshrined = e.getBooleanOr("enshrined", false);
			stone(g, s.left + 4, y + 1, enshrined);
			float tx = s.left + 20;
			float tw = w - 96;
			Component name = e.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			Component realm = e.read("realm", ComponentSerialization.CODEC).orElse(Component.empty());
			float drawn = Brush.fit(g, font, Brush.bold(name), tx, y, tw * 0.6F, enshrined ? Palette.argb(0xFF, 0x8A671B) : Palette.INK, 0.72F, 0.5F, false);
			Brush.fit(g, font, realm, tx + drawn + 4, y + 1, tw - drawn - 4, Palette.INK_SOFT, 0.55F, 0.4F, false);
			Component cause = e.read("cause_text", ComponentSerialization.CODEC).orElse(Component.empty());
			int slot = e.getIntOr("slot", -1);
			Component fell = Component.translatable("kultivator.sekte.paviliun.ui.grave.fell", e.getLongOr("day", 0L), cause,
					slot >= 0 ? Component.translatable("kultivator.sekte.paviliun.ui.grave.slot", slot + 1)
							: Component.translatable("kultivator.sekte.paviliun.ui.grave.noslot"));
			Brush.fit(g, font, fell, tx, y + 9, tw, Palette.INK_SOFT, 0.52F, 0.38F, false);
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
				deeds.append(Component.translatable("kultivator.sekte.paviliun.ui.grave.nodeeds"));
			}
			Brush.fit(g, font, deeds, tx, y + 17, tw + 70, Palette.argb(0xFF, 0x3E5A48), 0.5F, 0.34F, false);
			if (enshrined) {
				Brush.fit(g, font, Component.translatable("kultivator.sekte.paviliun.ui.enshrined"), s.right - 74, y + 10, 72,
						Palette.argb(0xFF, 0x8A671B), 0.58F, 0.42F, false);
			}
		}
		footer(s, g, Component.translatable("kultivator.sekte.paviliun.ui.graves.foot", list.size(), Math.max(slots, list.size()), v.getIntOr("buried", 0),
				v.getIntOr("pool", 0)), list.size(), visible);
	}

	private static void lineage(PaviliunScreen s, GuiGraphicsExtractor g) {
		Font font = s.font();
		CompoundTag v = s.makam;
		ListTag list = v.getListOrEmpty("lineage");
		float w = s.right - s.left - 8;
		if (list.isEmpty()) {
			Brush.wrap(g, font, Component.translatable("kultivator.sekte.paviliun.ui.lineage.none"), s.left + 6, listTop(s) + 4, w - 4, Palette.INK_SOFT,
					0.62F, false);
			return;
		}
		int visible = Math.max(1, (s.bottom - 20 - listTop(s)) / LINE_H);
		for (int i = 0; i < visible && i + s.scroll < list.size(); i++) {
			if (!(list.get(i + s.scroll) instanceof CompoundTag e)) {
				continue;
			}
			float y = listTop(s) + i * LINE_H;
			boolean enshrined = e.getBooleanOr("enshrined", false);
			Brush.dot(g, s.left + 7, y + 3.5F, 1.5F, enshrined ? Palette.argb(0xFF, Palette.GOLD) : Palette.argb(0xC0, Palette.INK_SOFT));
			Component name = e.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			Component realm = e.read("realm", ComponentSerialization.CODEC).orElse(Component.empty());
			Component cause = e.read("cause_text", ComponentSerialization.CODEC).orElse(Component.empty());
			Component line = Component.translatable("kultivator.sekte.paviliun.ui.lineage.line", name, realm, e.getLongOr("day", 0L), cause);
			Brush.fit(g, font, line, s.left + 12, y, w - 72, enshrined ? Palette.argb(0xFF, 0x8A671B) : Palette.INK, 0.56F, 0.4F, false);
			if (enshrined) {
				Brush.fit(g, font, Component.literal("✦"), s.right - 12, y, 10, Palette.argb(0xFF, Palette.GOLD_DARK), 0.7F, 0.5F, false);
			}
		}
		Component foot = Component.translatable("kultivator.sekte.paviliun.ui.lineage.foot", v.getIntOr("lineage_total", list.size()), v.getIntOr("folded", 0));
		footer(s, g, foot, list.size(), visible);
	}

	private static void altar(PaviliunScreen s, GuiGraphicsExtractor g) {
		Font font = s.font();
		CompoundTag v = s.makam;
		float w = s.right - s.left - 8;
		float half = w / 2.0F - 4;
		float x = s.left + 4;
		float y = listTop(s);
		// the offering table with its spirit tablets
		int tablets = v.getIntOr("tablets", 0);
		float tableY = y + 34;
		Brush.rect(g, x + 6, tableY, x + half - 6, tableY + 4, Palette.argb(0xFF, 0x7A1E16));
		Brush.rect(g, x + 6, tableY + 4, x + half - 6, tableY + 5, Palette.argb(0xFF, 0x45100B));
		Brush.rect(g, x + 10, tableY + 5, x + 12, tableY + 14, Palette.argb(0xFF, 0x5A160F));
		Brush.rect(g, x + half - 12, tableY + 5, x + half - 10, tableY + 14, Palette.argb(0xFF, 0x5A160F));
		int shown = Math.min(12, tablets);
		float span = half - 24;
		for (int i = 0; i < shown; i++) {
			float tx = x + 12 + (shown == 1 ? span / 2.0F : span * i / (shown - 1)) - 2;
			float th = 18 + (i % 3 == 1 ? 4 : 0);
			Brush.rect(g, tx - 1, tableY - th - 1, tx + 5, tableY, Palette.argb(0xFF, 0x3A1E10));
			Brush.rect(g, tx, tableY - th, tx + 4, tableY - 1, Palette.argb(0xFF, 0x2A1610));
			Brush.rect(g, tx + 1, tableY - th + 2, tx + 3, tableY - 3, Palette.argb(0xD0, Palette.GOLD));
		}
		if (shown == 0) {
			Brush.fitCentered(g, font, Component.translatable("kultivator.sekte.paviliun.ui.altar.empty"), x + half / 2.0F, tableY - 12, half - 8,
					Palette.INK_FAINT, 0.55F, 0.4F, false);
		}
		// incense in the censer (a few curling strokes while today's incense burns)
		boolean incense = v.getBooleanOr("incense_today", false);
		float cx = x + half / 2.0F;
		Brush.rect(g, cx - 5, tableY + 6, cx + 5, tableY + 12, Palette.argb(0xFF, 0x6E5A2A));
		if (incense) {
			double t = Util.getMillis() / 600.0D;
			for (int k = 0; k < 5; k++) {
				float sy = tableY + 4 - k * 5;
				float sx = cx + (float) Math.sin(t + k * 0.9D) * (1.5F + k);
				Brush.dot(g, sx, sy, 1.2F + k * 0.25F, Palette.argb(Math.max(20, 150 - k * 28), 0xD8D8D0));
			}
		}
		float ly = tableY + 18;
		int blessing = v.getIntOr("blessing", 0);
		Brush.fit(g, font, Component.translatable("kultivator.sekte.paviliun.ui.altar.blessing", String.format(java.util.Locale.ROOT, "%.1f", blessing / 10.0D)),
				x, ly, half, Palette.SEAL_DARK, 0.7F, 0.5F, false);
		s.bar(g, x, ly + 9, half, 4, blessing / 150.0F, 0xD4A637);
		ly += 17;
		Brush.fit(g, font, Component.translatable("kultivator.sekte.paviliun.ui.altar.tablets", tablets, Math.min(10, tablets)), x, ly, half, Palette.INK,
				0.56F, 0.4F, false);
		ly += 9;
		int fresh = v.getIntOr("fresh", 0);
		Brush.fit(g, font, Component.translatable("kultivator.sekte.paviliun.ui.altar.fresh", fresh), x, ly, half, Palette.INK, 0.56F, 0.4F, false);
		s.bar(g, x, ly + 8, half, 2.5F, fresh / 100.0F, 0x67C99B);
		ly += 13;
		Brush.fit(g, font, Component.translatable(incense ? "kultivator.sekte.paviliun.ui.altar.incense.done" : "kultivator.sekte.paviliun.ui.altar.incense.todo"),
				x, ly, half, incense ? Palette.JADE_DARK : Palette.WARN, 0.56F, 0.4F, false);

		// right column: Qingming and the rules
		float rx = x + half + 8;
		float ry = y;
		ry = s.heading(g, Component.translatable("kultivator.sekte.paviliun.ui.qingming"), rx, ry, half);
		long left = v.getLongOr("qingming_left", 0L);
		boolean running = v.getBooleanOr("qingming_running", false);
		Component when = running ? Component.translatable("kultivator.sekte.paviliun.ui.qingming.now")
				: left <= 0L ? Component.translatable("kultivator.sekte.paviliun.ui.qingming.due")
				: Component.translatable("kultivator.sekte.paviliun.ui.qingming.left", left);
		Brush.fit(g, font, when, rx, ry, half, running || left <= 0L ? Palette.SEAL : Palette.INK, 0.66F, 0.45F, false);
		ry += 11;
		ry += Brush.wrap(g, font, Component.translatable("kultivator.sekte.paviliun.ui.qingming.about", v.getIntOr("qingming_every", 15)), rx, ry, half,
				Palette.INK_SOFT, 0.52F, false) + 4;
		ry = s.heading(g, Component.translatable("kultivator.sekte.paviliun.ui.altar.rules"), rx, ry, half);
		Brush.wrap(g, font, Component.translatable(v.getBooleanOr("altar", false) ? "kultivator.sekte.paviliun.ui.altar.rules.text"
				: "kultivator.sekte.paviliun.ui.altar.rules.noaltar"), rx, ry, half, Palette.INK_SOFT, 0.5F, false);
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

	private static void footer(PaviliunScreen s, GuiGraphicsExtractor g, Component text, int total, int visible) {
		float y = s.bottom - 11;
		Brush.fit(g, s.font(), text, s.left + 4, y, s.right - s.left - 70, Palette.INK_FAINT, 0.55F, 0.4F, false);
		if (total > visible) {
			Brush.fit(g, s.font(), Component.translatable("kultivator.sekte.paviliun.ui.scroll", s.scroll + 1, Math.min(total, s.scroll + visible), total),
					s.right - 130, y, 66, Palette.INK_FAINT, 0.5F, 0.38F, false);
		}
	}

	private MakamScreen() {
	}
}
