package dev.kultivator.client.sekte.paviliun;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Widget;
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
 * The Rak Kitab page of {@link PaviliunScreen} (DESIGN 36.2.5): three shelves of nine places (27, the most a hall of
 * level 7-9 holds); each book a spine in its grade's ribbon colour with gilt bands (a jade slip as a bundle of pale green
 * bamboo strips), the free places in faint outline, the places the hall's level has not opened yet hatched with the level
 * that opens them. Hover a spine for its arts; click it to choose it: its arts, grades and the giver fill the right
 * column with "Ambil kembali". Below sit the readers (name, art, progress bar, minutes left) and "Setor kitab di tangan".
 */
final class RakScreen {

	private static final int PER_SHELF = 9;
	private static final int SHELVES = 3;

	static void build(PaviliunScreen s) {
		CompoundTag v = s.rak;
		int capacity = v.getIntOr("capacity", 0);
		ListTag books = v.getListOrEmpty("books");
		float shelfW = shelfWidth(s);
		float slotW = shelfW / PER_SHELF;
		int spineH = spineHeight(s);
		for (int i = 0; i < SHELVES * PER_SHELF; i++) {
			int shelf = i / PER_SHELF;
			int x = Math.round(s.left + 4 + (i % PER_SHELF) * slotW + 1);
			int y = s.contentTop() + 4 + shelf * (spineH + 8);
			CompoundTag book = i < books.size() && books.get(i) instanceof CompoundTag b ? b : null;
			if (book == null) {
				continue;
			}
			int id = book.getIntOr("id", -1);
			s.widgets.add(new Spine(x, y, Math.max(4, Math.round(slotW) - 2), spineH, book, () -> s.selectedBook == id).onPress(() -> {
				s.selectedBook = s.selectedBook == id ? -1 : id;
				s.confirmWithdraw = false;
				s.rebuild();
			}).tooltip(() -> tooltip(book)));
		}
		float colX = s.left + 8 + shelfW;
		int colW = Math.round(s.right - colX);
		CompoundTag chosen = chosen(s);
		if (chosen != null) {
			int id = chosen.getIntOr("id", -1);
			s.widgets.add(new TalismanButton(Math.round(colX), s.contentTop() + 78, Math.min(colW, 110), 12,
					() -> Component.translatable(s.confirmWithdraw ? "kultivator.sekte.paviliun.ui.withdraw.sure" : "kultivator.sekte.paviliun.ui.withdraw"))
					.urgentWhen(() -> s.confirmWithdraw)
					.onPress(() -> {
						if (s.confirmWithdraw) {
							s.selectedBook = -1;
							PaviliunClient.send(PaviliunPayloads.OP_WITHDRAW, Integer.toString(id));
						} else {
							s.confirmWithdraw = true;
							UiSound.ask();
						}
					})
					.tooltip(() -> List.of(Component.translatable("kultivator.sekte.paviliun.ui.withdraw.tip").withStyle(ChatFormatting.GRAY))));
		}
		boolean hand = v.contains("hand");
		boolean room = books.size() < capacity;
		Component handName = v.read("hand", ComponentSerialization.CODEC).orElse(Component.empty());
		s.widgets.add(new TalismanButton(s.left + 4, s.bottom - 13, Math.min(150, s.right - s.left - 70), 13,
				() -> hand ? Component.translatable("kultivator.sekte.paviliun.ui.deposit", handName) : Component.translatable("kultivator.sekte.paviliun.ui.deposit.none"))
				.urgentWhen(() -> hand && room)
				.enabledWhen(() -> hand && room)
				.onPress(() -> PaviliunClient.send(PaviliunPayloads.OP_DEPOSIT, ""))
				.tooltip(() -> List.of(Component.translatable("kultivator.sekte.paviliun.ui.deposit.tip").withStyle(ChatFormatting.GRAY))));
	}

	private static float shelfWidth(PaviliunScreen s) {
		return (s.right - s.left - 8) * 0.58F;
	}

	private static int spineHeight(PaviliunScreen s) {
		return Math.max(18, Math.min(28, (s.bottom - 70 - s.contentTop()) / SHELVES - 8));
	}

	private static @Nullable CompoundTag chosen(PaviliunScreen s) {
		for (Tag tag : s.rak.getListOrEmpty("books")) {
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
		lines.add(Component.translatable("kultivator.sekte.paviliun.ui.book.by", book.getStringOr("by", "?"), book.getLongOr("day", 0L))
				.withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}

	// ================================================================== drawing

	static void draw(PaviliunScreen s, GuiGraphicsExtractor g, int mouseX, int mouseY) {
		CompoundTag v = s.rak;
		Font font = s.font();
		int capacity = v.getIntOr("capacity", 0);
		int level = v.getIntOr("level", 0);
		ListTag books = v.getListOrEmpty("books");
		float shelfW = shelfWidth(s);
		float slotW = shelfW / PER_SHELF;
		int spineH = spineHeight(s);
		float sx = s.left + 4;
		// the bookcase: dark lacquer back, three planks, carved posts
		float caseTop = s.contentTop();
		float caseBottom = caseTop + 4 + SHELVES * (spineH + 8);
		Brush.rect(g, sx - 3, caseTop - 2, sx + shelfW + 3, caseBottom + 1, Palette.argb(0xFF, 0x3A1E10));
		Brush.rect(g, sx - 1, caseTop, sx + shelfW + 1, caseBottom - 1, Palette.argb(0xFF, 0x22120A));
		for (int shelf = 0; shelf < SHELVES; shelf++) {
			float py = caseTop + 4 + shelf * (spineH + 8) + spineH;
			Brush.rect(g, sx - 3, py, sx + shelfW + 3, py + 3, Palette.argb(0xFF, 0x7E4A27));
			Brush.rect(g, sx - 3, py + 3, sx + shelfW + 3, py + 4, Palette.argb(0xFF, 0x45230F));
		}
		for (int i = books.size(); i < SHELVES * PER_SHELF; i++) {
			int shelf = i / PER_SHELF;
			float x = sx + (i % PER_SHELF) * slotW + 1;
			float y = caseTop + 4 + shelf * (spineH + 8);
			if (i < capacity) {
				Brush.frame(g, Math.round(x), Math.round(y + 3), Math.round(x + slotW - 2), Math.round(y + spineH), Palette.argb(0x50, Palette.PAPER_SHADE));
			} else {
				// a place the hall has not opened yet: hatched
				for (int k = 0; k < spineH; k += 3) {
					Brush.rect(g, x, y + k, x + slotW - 2, y + k + 1, Palette.argb(0x30, Palette.LOCKED));
				}
			}
		}
		if (capacity < SHELVES * PER_SHELF) {
			int need = capacity < PER_SHELF * 2 ? 4 : 7;
			Brush.fit(g, font, Component.translatable("kultivator.sekte.paviliun.ui.locked", need), sx, caseBottom + 3, shelfW, Palette.INK_FAINT,
					0.55F, 0.4F, false);
		}
		Brush.fit(g, font, Component.translatable("kultivator.sekte.paviliun.ui.shelf", books.size(), capacity, level), sx, caseBottom + 11, shelfW,
				Palette.INK_SOFT, 0.6F, 0.42F, false);

		// right column: the chosen book, else the pavilion's tally
		float colX = s.left + 8 + shelfW;
		float colW = s.right - colX;
		float y = s.contentTop();
		CompoundTag chosen = chosen(s);
		if (chosen != null) {
			y = s.heading(g, chosen.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?")), colX, y, colW);
			int n = 0;
			for (Tag t : chosen.getListOrEmpty("specs")) {
				if (!(t instanceof CompoundTag spec) || n >= 5) {
					continue;
				}
				int rgb = spec.getIntOr("rgb", 0xC8962E);
				Brush.dot(g, colX + 3, y + 3.5F, 1.8F, Palette.argb(0xFF, rgb));
				Brush.fit(g, font, spec.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?")), colX + 8, y, colW * 0.62F, Palette.INK, 0.62F,
						0.42F, false);
				Brush.fit(g, font, spec.read("grade_name", ComponentSerialization.CODEC).orElse(Component.empty()), colX + 8 + colW * 0.62F, y, colW * 0.36F,
						Palette.argb(0xFF, Palette.darken(0xFF000000 | rgb, 0.3F)), 0.55F, 0.4F, false);
				y += 9;
				n++;
			}
			Brush.fit(g, font, Component.translatable("kultivator.sekte.paviliun.ui.book.by", chosen.getStringOr("by", "?"), chosen.getLongOr("day", 0L)),
					colX, y + 2, colW, Palette.INK_FAINT, 0.52F, 0.4F, false);
		} else {
			y = s.heading(g, Component.translatable("kultivator.sekte.paviliun.ui.pavilion"), colX, y, colW);
			Component state = v.getBooleanOr("library", false) ? Component.translatable("kultivator.sekte.paviliun.ui.pavilion.open")
					: Component.translatable("kultivator.sekte.paviliun.ui.pavilion.none");
			y += Brush.wrap(g, font, state, colX, y, colW, v.getBooleanOr("library", false) ? Palette.JADE_DARK : Palette.WARN, 0.58F, false) + 3;
			y += Brush.wrap(g, font, Component.translatable("kultivator.sekte.paviliun.ui.pavilion.rule", v.getIntOr("readers_max", 4)), colX, y, colW,
					Palette.INK_SOFT, 0.55F, false) + 3;
			Brush.fit(g, font, Component.translatable("kultivator.sekte.paviliun.ui.learned", v.getIntOr("learned", 0)), colX, y, colW, Palette.INK,
					0.6F, 0.42F, false);
		}

		// readers
		float ry = Math.max(caseBottom + 22, s.contentTop() + 96);
		float rw = s.right - s.left - 8;
		ListTag readers = v.getListOrEmpty("readers");
		ry = s.heading(g, Component.translatable("kultivator.sekte.paviliun.ui.readers", readers.size(), v.getIntOr("readers_max", 4)), s.left + 4, ry, rw);
		if (readers.isEmpty()) {
			Brush.fit(g, font, Component.translatable("kultivator.sekte.paviliun.ui.readers.none"), s.left + 6, ry, rw - 4, Palette.INK_FAINT, 0.58F, 0.42F,
					false);
			return;
		}
		float half = rw / 2.0F - 4;
		for (int i = 0; i < readers.size() && i < 4; i++) {
			if (!(readers.get(i) instanceof CompoundTag r)) {
				continue;
			}
			float cx = s.left + 4 + (i % 2) * (half + 8);
			float cy = ry + (i / 2) * 17;
			if (cy > s.bottom - 30) {
				break;
			}
			int rgb = r.getIntOr("rgb", 0x5FE3C0);
			Component name = r.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			Component spec = r.read("spec", ComponentSerialization.CODEC).orElse(Component.literal("…"));
			Brush.fit(g, font, Component.translatable("kultivator.sekte.paviliun.ui.reader", name, spec), cx, cy, half, Palette.INK, 0.58F, 0.4F, false);
			float progress = r.getFloatOr("progress", 0.0F);
			s.bar(g, cx, cy + 8, half * 0.7F, 3, progress, rgb);
			int eta = r.getIntOr("eta", 0);
			Brush.fit(g, font, Component.translatable("kultivator.sekte.paviliun.ui.eta", Math.round(progress * 100.0F), Math.max(1, (eta + 59) / 60)),
					cx + half * 0.7F + 3, cy + 6.5F, half * 0.3F - 3, Palette.INK_SOFT, 0.5F, 0.36F, false);
		}
	}

	/** One book on the shelf. */
	private static final class Spine extends Widget {
		private final CompoundTag book;
		private final java.util.function.BooleanSupplier chosen;
		private final int rgb;
		private final boolean slip;
		private final String glyph;

		Spine(int x, int y, int w, int h, CompoundTag book, java.util.function.BooleanSupplier chosen) {
			super(x, y, w, h);
			this.book = book;
			this.chosen = chosen;
			ListTag specs = book.getListOrEmpty("specs");
			this.rgb = specs.isEmpty() || !(specs.get(0) instanceof CompoundTag first) ? 0xC8962E : first.getIntOr("rgb", 0xC8962E);
			this.slip = "giok".equals(book.getStringOr("kind", "buku"));
			int id = book.getIntOr("id", 0);
			this.glyph = GLYPHS[Math.floorMod(id * 7, GLYPHS.length)];
		}

		private static final String[] GLYPHS = {"經", "訣", "典", "錄", "法", "書", "譜", "卷"};

		@Override
		protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
			boolean on = chosen.getAsBoolean();
			int lift = on ? 3 : Math.round(hover.get() * 2.0F);
			int bx = x;
			int by = y - lift + 2;
			int r = x + w;
			int b = y + h;
			if (slip) {
				// a bundle of bamboo strips tied with red cord
				for (int k = bx; k < r; k += 2) {
					g.fill(k, by + 2, Math.min(r, k + 2) - 0, b, (k - bx) % 4 == 0 ? Palette.argb(0xFF, 0xA8D8A0) : Palette.argb(0xFF, 0x86B97E));
				}
				g.fill(bx, by + h / 3, r, by + h / 3 + 1, Palette.argb(0xFF, 0xB3281D));
				g.fill(bx, by + 2 * h / 3, r, by + 2 * h / 3 + 1, Palette.argb(0xFF, 0xB3281D));
			} else {
				int base = 0xFF000000 | rgb;
				g.fillGradient(bx, by, r, b, Palette.lighten(base, 0.12F), Palette.darken(base, 0.35F));
				g.fill(bx, by + 2, r, by + 3, Palette.argb(0xE0, Palette.GOLD_LIGHT));
				g.fill(bx, b - 4, r, b - 3, Palette.argb(0xE0, Palette.GOLD_LIGHT));
				g.fill(bx, by, bx + 1, b, Palette.argb(0x60, 0x000000));
				// the title label: a paper slip with one character
				int lx0 = bx + Math.max(1, w / 4);
				int lx1 = r - Math.max(1, w / 4);
				int ly0 = by + 6;
				int ly1 = Math.min(b - 6, ly0 + Math.max(8, h / 2));
				g.fill(lx0, ly0, lx1, ly1, Palette.argb(0xFF, 0xEFE3C2));
				if (lx1 - lx0 >= 5) {
					Component c = Component.literal(glyph);
					float sc = Math.min(0.75F, (lx1 - lx0) / 9.0F);
					g.pose().pushMatrix();
					g.pose().translate((lx0 + lx1) / 2.0F - font.width(c) * sc / 2.0F, ly0 + 1);
					g.pose().scale(sc, sc);
					g.text(font, c, 0, 0, 0xFF1B2521, false);
					g.pose().popMatrix();
				}
			}
			if (on || over) {
				Brush.frame(g, bx - 1, by - 1, r + 1, b + 1, on ? Palette.SEAL : Palette.argb(0xC0, Palette.GOLD));
			}
			if (on) {
				float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 220.0D);
				Brush.rect(g, bx, by - 3, r, by - 2, Palette.argb(Math.round(120 + 100 * pulse), Palette.SEAL_GLOW));
			}
		}
	}

	private RakScreen() {
	}
}
