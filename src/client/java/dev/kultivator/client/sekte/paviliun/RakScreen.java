package dev.kultivator.client.sekte.paviliun;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Lukis;
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
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The painter of the Rak Kitab sub (DESIGN 36.2.5, 43.5 Sekte › Kas &amp; Toko › rak). Top row: the shelf tally and "Setor
 * kitab di tangan". Then three shelves of nine places (27, the most a hall of level 7-9 holds); each book a spine in its
 * grade's ribbon colour with gilt bands (a jade slip as a bundle of pale green bamboo strips), the free places in faint
 * outline, the places the hall's level has not opened yet hatched. A spine is a focusable zone: hover for its arts, click
 * (or Enter) to choose it; the chosen book's arts, grades and giver fill the side column (below the shelves when narrow)
 * with "Ambil kembali" behind a light confirm. Below sit the readers with their progress and minutes left. Text is 1.0.
 */
final class RakScreen {

	private static final int PER_SHELF = 9;
	private static final int SHELVES = 3;
	private static final int SPINE_H = 22;
	private static final int BTN_H = 14;
	private static final String[] GLYPHS = {"經", "訣", "典", "錄", "法", "書", "譜", "卷"};
	private static final String U = "kultivator.sekte.paviliun.ui.";

	/** Paints the Rak Kitab from {@code y} between {@code x0} and {@code x1}; returns the y under the content. */
	static int lukis(PaviliunScreen s, Lukis l, int x0, int y, int x1) {
		CompoundTag v = s.lembar();
		Font font = s.font();
		int capacity = v.getIntOr("capacity", 0);
		int level = v.getIntOr("level", 0);
		ListTag books = v.getListOrEmpty("books");
		int w = x1 - x0;

		// top row: tally on the left, the deposit talisman on the right
		boolean hand = v.contains("hand");
		boolean room = books.size() < capacity;
		Component handName = v.read("hand", ComponentSerialization.CODEC).orElse(Component.empty());
		Component setor = hand ? Component.translatable(U + "deposit", handName) : Component.translatable(U + "deposit.none");
		int bw = Math.min(w / 2, TalismanButton.lebar(font, setor, 70, 170));
		Component alasan = !hand ? Component.translatable(U + "deposit.none") : !room ? Component.translatable(U + "shelf", books.size(), capacity, level) : null;
		TalismanButton.gambar(l, "rak:setor", x1 - bw, y, bw, BTN_H, setor, hand && room, alasan, () -> PaviliunClient.send(PaviliunPayloads.OP_DEPOSIT, ""),
				hand && room, false);
		if (l.di(x1 - bw, y, x1, y + BTN_H)) {
			l.tip(Component.translatable(U + "deposit.tip").withStyle(ChatFormatting.GRAY));
		}
		l.teks(Component.translatable(U + "shelf", books.size(), capacity, level), x0, y + 3, w - bw - 6, l.gaya.teksLemah);
		y += BTN_H + 4;

		// the bookcase (left; full width when narrow) and the side column
		boolean lebar = w >= 300;
		float shelfW = lebar ? w * 0.58F : w;
		float slotW = shelfW / PER_SHELF;
		int caseTop = y;
		int caseBottom = caseTop + 4 + SHELVES * (SPINE_H + 8);
		shelves(l, x0, caseTop, shelfW, slotW, capacity, books.size());
		for (int i = 0; i < books.size() && i < SHELVES * PER_SHELF; i++) {
			if (!(books.get(i) instanceof CompoundTag book)) {
				continue;
			}
			int bx = Math.round(x0 + (i % PER_SHELF) * slotW + 1);
			int by = caseTop + 4 + (i / PER_SHELF) * (SPINE_H + 8);
			int sw = Math.max(4, Math.round(slotW) - 2);
			spine(s, l, book, bx, by, sw);
		}
		int under = caseBottom + 3;
		if (capacity < SHELVES * PER_SHELF) {
			int need = capacity < PER_SHELF * 2 ? 4 : 7;
			l.samar(Component.translatable(U + "locked", need), x0, under, shelfW);
			under += 10;
		}

		int colX = lebar ? Math.round(x0 + shelfW + 8) : x0;
		int colY = lebar ? caseTop : under + 2;
		int colEnd = side(s, l, v, colX, colY, x1);
		y = Math.max(under, colEnd) + 4;

		// readers
		ListTag readers = v.getListOrEmpty("readers");
		y += l.kepala(Component.translatable(U + "readers", readers.size(), v.getIntOr("readers_max", 4)), x0, y, x1);
		if (readers.isEmpty()) {
			l.samar(Component.translatable(U + "readers.none"), x0 + 2, y, w - 4);
			return y + 10;
		}
		int cols = lebar ? 2 : 1;
		float colW = (w - (cols - 1) * 8) / (float) cols;
		for (int i = 0; i < readers.size(); i++) {
			if (!(readers.get(i) instanceof CompoundTag r)) {
				continue;
			}
			float cx = x0 + (i % cols) * (colW + 8);
			int cy = y + (i / cols) * 22;
			int rgb = r.getIntOr("rgb", 0x5FE3C0);
			Component name = r.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			Component spec = r.read("spec", ComponentSerialization.CODEC).orElse(Component.literal("…"));
			l.teks(Component.translatable(U + "reader", name, spec), cx, cy, colW, l.gaya.teks);
			float progress = r.getFloatOr("progress", 0.0F);
			int eta = r.getIntOr("eta", 0);
			Component sisa = Component.translatable(U + "eta", Math.round(progress * 100.0F), Math.max(1, (eta + 59) / 60));
			int sisaW = Math.min(Math.round(colW / 2), font.width(sisa));
			PaviliunScreen.bar(l.g, cx, cy + 12, colW - sisaW - 4, 3, progress, rgb);
			l.teks(sisa, cx + colW - sisaW, cy + 10, sisaW, l.gaya.teksSamar);
		}
		return y + ((readers.size() + cols - 1) / cols) * 22;
	}

	/** The dark lacquer case, the planks, the empty and the locked places. */
	private static void shelves(Lukis l, int x0, int caseTop, float shelfW, float slotW, int capacity, int count) {
		GuiGraphicsExtractor g = l.g;
		float sx = x0;
		int caseBottom = caseTop + 4 + SHELVES * (SPINE_H + 8);
		Brush.rect(g, sx, caseTop - 2, sx + shelfW, caseBottom + 1, Palette.argb(0xFF, 0x3A1E10));
		Brush.rect(g, sx + 2, caseTop, sx + shelfW - 2, caseBottom - 1, Palette.argb(0xFF, 0x22120A));
		for (int shelf = 0; shelf < SHELVES; shelf++) {
			float py = caseTop + 4 + shelf * (SPINE_H + 8) + SPINE_H;
			Brush.rect(g, sx, py, sx + shelfW, py + 3, Palette.argb(0xFF, 0x7E4A27));
			Brush.rect(g, sx, py + 3, sx + shelfW, py + 4, Palette.argb(0xFF, 0x45230F));
		}
		for (int i = count; i < SHELVES * PER_SHELF; i++) {
			int shelf = i / PER_SHELF;
			float px = sx + (i % PER_SHELF) * slotW + 1;
			float py = caseTop + 4 + shelf * (SPINE_H + 8);
			if (i < capacity) {
				Brush.frame(g, Math.round(px), Math.round(py + 3), Math.round(px + slotW - 2), Math.round(py + SPINE_H), Palette.argb(0x50, Palette.PAPER_SHADE));
			} else {
				for (int k = 0; k < SPINE_H; k += 3) {
					Brush.rect(g, px, py + k, px + slotW - 2, py + k + 1, Palette.argb(0x30, Palette.LOCKED));
				}
			}
		}
	}

	/** One book on the shelf: a focusable zone that chooses it. */
	private static void spine(PaviliunScreen s, Lukis l, CompoundTag book, int x, int y, int w) {
		int id = book.getIntOr("id", -1);
		boolean on = s.selectedBook == id;
		boolean over = l.di(x, y, x + w, y + SPINE_H);
		String kunci = "rak:buku" + id;
		l.zona(kunci, x, y, x + w, y + SPINE_H, () -> {
			UiSound.press();
			s.selectedBook = s.selectedBook == id ? -1 : id;
		}, true, null, true);
		ListTag specs = book.getListOrEmpty("specs");
		int rgb = specs.isEmpty() || !(specs.get(0) instanceof CompoundTag first) ? 0xC8962E : first.getIntOr("rgb", 0xC8962E);
		boolean slip = "giok".equals(book.getStringOr("kind", "buku"));
		GuiGraphicsExtractor g = l.g;
		int lift = on ? 3 : over ? 2 : 0;
		int by = y - lift + 2;
		int r = x + w;
		int b = y + SPINE_H;
		if (slip) {
			// a bundle of bamboo strips tied with red cord
			for (int k = x; k < r; k += 2) {
				g.fill(k, by + 2, Math.min(r, k + 2), b, (k - x) % 4 == 0 ? Palette.argb(0xFF, 0xA8D8A0) : Palette.argb(0xFF, 0x86B97E));
			}
			g.fill(x, by + SPINE_H / 3, r, by + SPINE_H / 3 + 1, Palette.argb(0xFF, 0xB3281D));
			g.fill(x, by + 2 * SPINE_H / 3, r, by + 2 * SPINE_H / 3 + 1, Palette.argb(0xFF, 0xB3281D));
		} else {
			int base = 0xFF000000 | rgb;
			g.fillGradient(x, by, r, b, Palette.lighten(base, 0.12F), Palette.darken(base, 0.35F));
			g.fill(x, by + 2, r, by + 3, Palette.argb(0xE0, Palette.GOLD_LIGHT));
			g.fill(x, b - 4, r, b - 3, Palette.argb(0xE0, Palette.GOLD_LIGHT));
			g.fill(x, by, x + 1, b, Palette.argb(0x60, 0x000000));
			// the title label: a paper slip with one character, written at 1.0 only when the slip is wide enough (43.0.5)
			Font font = s.font();
			Component c = Component.literal(GLYPHS[Math.floorMod(id * 7, GLYPHS.length)]);
			int lx0 = x + Math.max(1, (w - font.width(c) - 2) / 2);
			int lx1 = r - Math.max(1, (w - font.width(c) - 2) / 2);
			int ly0 = by + 5;
			int ly1 = Math.min(b - 5, ly0 + 11);
			g.fill(lx0, ly0, lx1, ly1, Palette.argb(0xFF, 0xEFE3C2));
			if (lx1 - lx0 >= font.width(c)) {
				g.text(font, c, (lx0 + lx1 - font.width(c)) / 2, ly0 + 2, 0xFF1B2521, false);
			}
		}
		if (on || over) {
			Brush.frame(g, x - 1, by - 1, r + 1, b + 1, on ? Palette.SEAL : Palette.argb(0xC0, Palette.GOLD));
		}
		if (on) {
			float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 220.0D);
			Brush.rect(g, x, by - 3, r, by - 2, Palette.argb(Math.round(120 + 100 * pulse), Palette.SEAL_GLOW));
		}
		l.fokusCincin(kunci, x, y, r, b);
		if (over) {
			l.tip(tooltip(book));
		}
	}

	/** The side column: the chosen book with "Ambil kembali", else the pavilion's tally. Returns the y under it. */
	private static int side(PaviliunScreen s, Lukis l, CompoundTag v, int x0, int y, int x1) {
		int w = x1 - x0;
		CompoundTag chosen = chosen(s, v);
		if (chosen == null) {
			y += l.kepala(Component.translatable(U + "pavilion"), x0, y, x1);
			boolean library = v.getBooleanOr("library", false);
			y += Math.round(l.bungkus(Component.translatable(library ? U + "pavilion.open" : U + "pavilion.none"), x0, y, w,
					library ? Palette.JADE_DARK : Palette.WARN)) + 2;
			y += Math.round(l.bungkus(Component.translatable(U + "pavilion.rule", v.getIntOr("readers_max", 4)), x0, y, w, l.gaya.teksLemah)) + 2;
			l.teks(Component.translatable(U + "learned", v.getIntOr("learned", 0)), x0, y, w, l.gaya.teks);
			return y + 10;
		}
		Component name = chosen.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
		y += l.kepala(name, x0, y, x1);
		int n = 0;
		for (Tag t : chosen.getListOrEmpty("specs")) {
			if (!(t instanceof CompoundTag spec) || n >= 5) {
				continue;
			}
			int rgb = spec.getIntOr("rgb", 0xC8962E);
			Component grade = spec.read("grade_name", ComponentSerialization.CODEC).orElse(Component.empty());
			int gw = Math.min(w / 2, s.font().width(grade));
			Brush.dot(l.g, x0 + 3, y + 4.0F, 1.8F, Palette.argb(0xFF, rgb));
			l.teks(spec.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?")), x0 + 8, y, w - gw - 12, l.gaya.teks);
			l.teks(grade, x1 - gw, y, gw, Palette.argb(0xFF, Palette.darken(0xFF000000 | rgb, 0.3F)));
			y += 10;
			n++;
		}
		l.samar(Component.translatable(U + "book.by", chosen.getStringOr("by", "?"), chosen.getLongOr("day", 0L)), x0, y + 1, w);
		y += 12;
		int id = chosen.getIntOr("id", -1);
		Component ambil = Component.translatable(U + "withdraw");
		TalismanButton.gambar(l, "rak:ambil", x0, y, Math.min(w, TalismanButton.lebar(s.font(), ambil, 70, 140)), BTN_H, ambil, true, null,
				() -> s.tanya(Component.translatable("kultivator.gui43.m08.rak.ambil.tanya", name), () -> {
					s.selectedBook = -1;
					PaviliunClient.send(PaviliunPayloads.OP_WITHDRAW, Integer.toString(id));
				}));
		if (l.di(x0, y, x0 + Math.min(w, 140), y + BTN_H)) {
			l.tip(Component.translatable(U + "withdraw.tip").withStyle(ChatFormatting.GRAY));
		}
		return y + BTN_H;
	}

	private static @Nullable CompoundTag chosen(PaviliunScreen s, CompoundTag v) {
		for (Tag tag : v.getListOrEmpty("books")) {
			if (tag instanceof CompoundTag b && b.getIntOr("id", -1) == s.selectedBook) {
				return b;
			}
		}
		return null;
	}

	private static List<Component> tooltip(CompoundTag book) {
		List<Component> lines = new ArrayList<>();
		lines.add(Brush.bold(book.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"))));
		for (Tag t : book.getListOrEmpty("specs")) {
			if (t instanceof CompoundTag spec) {
				Component name = spec.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
				Component grade = spec.read("grade_name", ComponentSerialization.CODEC).orElse(Component.empty());
				lines.add(Component.literal(" · ").append(name).append(Component.literal("  ")).append(grade.copy().withColor(spec.getIntOr("rgb", 0xFFFFFF))));
			}
		}
		lines.add(Component.translatable(U + "book.by", book.getStringOr("by", "?"), book.getLongOr("day", 0L)).withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}

	private RakScreen() {
	}
}
