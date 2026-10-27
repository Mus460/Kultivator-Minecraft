package dev.kultivator.client.guide;

import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.JalanPage;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.GulirTeks;
import dev.kultivator.client.screen.ui.KeyHints;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.data.Realm;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Panduan › Kitab (DESIGN 43.5, 43.12.1, G17): one page with two subs. <b>Jalan Kultivasi</b> is the fifteen Langkah
 * chapters ({@link JalanPage.Langkah}, the same class as Diri › Jalan); <b>Bab</b> is the Kitab Panduan of before: the
 * chapters in display order ({@link GuideBook}; a seal with the number, the name and a bar; jade with a check once complete,
 * the current chapter framed in pulsing gold) and the chosen chapter's steps as cards (a check or an empty ring, the title,
 * the wrapped lines, a talisman row that writes its command into the chat box and returns here on Esc (43.13 R6), and a
 * tick row for steps the server cannot check by itself). Narrow box: the list, a chapter opens as the detail (back with
 * Esc); wider: list left, cards right. Text at 1.0 (43.0.5); key names in the texts are live (33.6 R11).
 */
public final class GuideScreen extends Page {

	public static final String SUB_JALAN = "jalan_kultivasi";
	public static final String SUB_BAB = "bab";
	private static final String K = "kultivator.guide_client.";
	private static final String M = "kultivator.gui43.m17.";
	private static final int ROW = 22;
	private static final int INDENT = 13;

	/** True while Kitab › Bab is on screen: {@link GuideClient} then skips its background refresh and its open flag. */
	static boolean terbuka;

	private final JalanPage.Langkah langkah = new JalanPage.Langkah();
	private final DaftarGulir bab = new DaftarGulir();
	private final GulirTeks kartu = new GulirTeks();
	private String sub = SUB_JALAN;
	private boolean shown;
	private int selected = -1;
	private boolean picked;
	private boolean sawSync;
	private boolean rinci = true;
	private boolean sempit = true;
	private boolean tampakkan;
	private boolean kotor = true;
	private int lebarIsi = -1;
	private int seenVersion = Integer.MIN_VALUE;

	public GuideScreen() {
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.gui43.hal.panduan.kitab");
	}

	@Override
	public int glyph() {
		return Glyphs.PATH;
	}

	@Override
	public String helpTopic() {
		return "jalan";
	}

	@Override
	public List<Sub> subs() {
		return List.of(new Sub(SUB_JALAN, Component.translatable("kultivator.gui43.sub.kitab.jalan_kultivasi")),
				new Sub(SUB_BAB, Component.translatable("kultivator.gui43.sub.kitab.bab")));
	}

	@Override
	public void showSub(String id) {
		sub = SUB_BAB.equals(id) ? SUB_BAB : SUB_JALAN;
		terbuka = shown && SUB_BAB.equals(sub);
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		shown = true;
		terbuka = SUB_BAB.equals(sub);
		langkah.onShow();
		// A fresh sync while the book is shown (the old screen asked on its first init); the next chapter opens as the detail.
		GuideClient.requestSync();
		if (GuideClient.synced()) {
			pilih(GuideClient.current());
		}
		rinci = true;
		super.onShow(fokus);
	}

	@Override
	public void onHide() {
		shown = false;
		terbuka = false;
	}

	@Override
	public void onData() {
		langkah.onData();
	}

	@Override
	public boolean rinci() {
		return SUB_BAB.equals(sub) ? sempit && rinci : langkah.rinci();
	}

	@Override
	public boolean mundur() {
		if (SUB_JALAN.equals(sub)) {
			return langkah.mundur();
		}
		if (!sempit || !rinci) {
			return false;
		}
		rinci = false;
		return true;
	}

	@Override
	public void tick() {
		if (seenVersion == GuideClient.version()) {
			return;
		}
		seenVersion = GuideClient.version();
		kotor = true;
		if (!sawSync && GuideClient.synced()) {
			sawSync = true;
			if (!picked) {
				// the first sync of this book: open at the chapter the player is in
				pilih(GuideClient.current());
			}
		}
	}

	@Override
	protected void build() {
		kotor = true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!mauTombol()) {
			boolean used = SUB_JALAN.equals(sub) ? langkah.tombol(event, fokusRing.kunci()) : kartu.tombol(event, fokusRing.kunci(), sempit && rinci);
			if (used) {
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	@Override
	protected void lukis(Lukis l) {
		if (SUB_JALAN.equals(sub)) {
			langkah.gambar(l, x, y, x + w, y + h);
			return;
		}
		int count = GuideBook.chapterCount();
		if (selected < 0) {
			selected = GuideClient.synced() ? GuideClient.current() : 0;
		}
		selected = Math.max(0, Math.min(Math.max(0, count - 1), selected));
		sempit = w < 320;
		if (sempit && rinci) {
			kartu(l, x, y, x + w, y + h);
		} else if (sempit) {
			daftar(l, x, y, x + w, y + h);
		} else {
			int listW = Math.max(110, Math.min(150, Math.round(w * 0.32F)));
			daftar(l, x, y, x + listW, y + h);
			l.g.fill(x + listW + 3, y, x + listW + 4, y + h, Palette.argb(0x90, Palette.GOLD));
			kartu(l, x + listW + 8, y, x + w, y + h);
		}
	}

	private void pilih(int chapter) {
		int count = GuideBook.chapterCount();
		if (count == 0) {
			return;
		}
		int target = Math.max(0, Math.min(count - 1, chapter));
		if (target != selected) {
			selected = target;
			kartu.keAtas();
			kotor = true;
		}
		bab.setPilih(target);
		tampakkan = true;
	}

	// ================================================================ the chapter list

	/** The overall count over the chapter list, then one row per chapter. */
	private void daftar(Lukis l, int x0, int y0, int x1, int y1) {
		Component count;
		int color;
		if (!GuideClient.synced()) {
			// without the mod on the server no sync ever comes: say so instead of "opening..." forever
			count = Component.translatable(GuideClient.connected() ? K + "loading" : "kultivator.gui43.p8.panduan.luring");
			color = l.gaya.teksSamar;
		} else if (GuideClient.allDone()) {
			count = Component.translatable(K + "all_done");
			color = Palette.JADE_DARK;
		} else {
			count = Component.translatable(M + "bab.ringkas", GuideClient.current() + 1, GuideClient.doneCount(), GuideBook.stepCount());
			color = Palette.JADE_DARK;
		}
		l.teks(count, x0 + 2, y0 + 1, x1 - x0 - 4, color);
		boolean narrow = sempit;
		bab.gambar(l, "kitab.bab", x0, y0 + 12, x1, y1, GuideBook.chapterCount(), ROW, (lk, i, a, b, c, d, sorot, terpilih) -> {
			baris(lk, i, a, b, c, d, sorot);
			lk.zona("kitab.bab#p" + i, a, b, c, d, () -> {
				picked = true;
				UiSound.tab();
				pilih(i);
				rinci = narrow;
			}, true, null, false);
		}, () -> {
			picked = true;
			pilih(bab.pilihan());
			rinci = narrow;
		}, narrow ? null : i -> {
			picked = true;
			pilih(i);
		});
		if (tampakkan) {
			tampakkan = false;
			bab.setPilih(selected);
		}
	}

	private void baris(Lukis l, int index, int x0, int y0, int x1, int y1, boolean sorot) {
		boolean synced = GuideClient.synced();
		GuideBook.Chapter chapter = GuideBook.chapter(index);
		int size = chapter.steps().size();
		int doneIn = synced ? GuideClient.doneIn(index) : 0;
		boolean complete = synced && size > 0 && doneIn >= size;
		boolean current = synced && !GuideClient.allDone() && index == GuideClient.current();
		l.kartu(x0, y0, x1, y1, sorot ? 1.0F : 0.0F, index == selected);
		float pulse = l.denyut();
		if (current) {
			Brush.frame(l.g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, Palette.mix(Palette.GOLD, Palette.GOLD_LIGHT, pulse));
		}
		// the seal: red with the number, gold for the current chapter, jade with a check when complete
		int seal = 13;
		int sx = x0 + 4;
		int sy = y0 + (y1 - y0 - seal) / 2;
		l.g.fill(sx, sy, sx + seal, sy + seal, complete ? Palette.JADE : current ? Palette.GOLD : Palette.SEAL);
		Brush.frame(l.g, sx + 1, sy + 1, sx + seal - 1, sy + seal - 1, Palette.argb(0xA0, complete ? Palette.JADE_PALE : Palette.TALISMAN));
		if (complete) {
			check(l.g, sx + seal / 2.0F, sy + seal / 2.0F, 8.0F, 0xFFFFFFFF);
		} else {
			Component number = Component.literal(String.valueOf(index + 1));
			Brush.text(l.g, l.font, number, sx + (seal - l.font.width(number)) / 2.0F + 0.5F, sy + 3.0F, current ? Palette.INK : Palette.TALISMAN, 1.0F,
					false);
		}
		int nx = sx + seal + 4;
		int nameColor = current ? Palette.GOLD_DARK : complete ? Palette.JADE_DARK : l.gaya.teks;
		l.teks(chapter.title(), nx, y0 + 2, x1 - nx - 3, nameColor);
		int fill = complete ? Palette.JADE : current ? Palette.GOLD : Palette.JADE_DULL;
		Bilah.gambar(l, nx, y0 + 13, x1 - 4, size > 0 ? doneIn / (float) size : 0.0F, fill, null);
		if (l.di(x0, y0, x1, y1)) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.translatable(K + "chapter", index + 1, chapter.title()));
			if (synced) {
				lines.add(Component.translatable(K + "chapter.count", doneIn, size).withStyle(ChatFormatting.GRAY));
			}
			if (complete) {
				lines.add(Component.translatable(K + "chapter.done").withStyle(ChatFormatting.GREEN));
			} else if (current) {
				lines.add(Component.translatable(K + "chapter.current").withStyle(ChatFormatting.GOLD));
			}
			// who the chapter is for (integration audit T1): its realm, and a hall of your own for the sect chapters
			Realm realm = chapter.realm();
			if (realm != null) {
				lines.add(Component.translatable(K + "chapter.realm", realm.displayName()).withStyle(ChatFormatting.GRAY));
			}
			if (chapter.needsHall()) {
				lines.add(Component.translatable(K + "chapter.hall").withStyle(ChatFormatting.GRAY));
			}
			l.tip(lines);
		}
	}

	// ================================================================ the step cards

	/** The chosen chapter's heading and count, then its description and step cards in one scroll. */
	private void kartu(Lukis l, int x0, int y0, int x1, int y1) {
		if (GuideBook.chapterCount() == 0) {
			return;
		}
		boolean synced = GuideClient.synced();
		Component heading = Brush.bold(Component.translatable(K + "chapter", selected + 1, GuideBook.chapter(selected).title()));
		int size = GuideBook.chapter(selected).steps().size();
		Component count = synced ? Component.translatable(K + "chapter.count", GuideClient.doneIn(selected), size) : Component.empty();
		int countW = l.font.width(count);
		Brush.rect(l.g, x0, y0 + 3.0F, x0 + 4.0F, y0 + 7.0F, Palette.SEAL);
		l.teks(heading, x0 + 6, y0 + 1, x1 - x0 - countW - 12, Palette.SEAL_DARK);
		l.kanan(count, x1, y0 + 1, Palette.JADE_DARK);
		l.g.fill(x0, y0 + 11, x1, y0 + 12, Palette.argb(0xB0, Palette.GOLD));
		int lebar = x1 - x0 - GulirTeks.RUANG_BATANG;
		if (kotor || lebar != lebarIsi) {
			kotor = false;
			lebarIsi = lebar;
			isi(l, lebar);
		}
		kartu.gambar(l, "kitab.kartu", x0, y0 + 15, x1, y1);
	}

	private void isi(Lukis l, int lebar) {
		kartu.kosongkan();
		GuideBook.Chapter chapter = GuideBook.chapter(selected);
		Component desc = chapter.desc();
		if (desc != null) {
			kartu.bungkus(l.font, KeyHints.fill(desc), 0, lebar, Palette.INK_SOFT);
		}
		boolean synced = GuideClient.synced();
		int next = firstOpen();
		for (GuideBook.Step step : chapter.steps()) {
			boolean done = synced && GuideClient.done(step.index());
			boolean isNext = synced && step.index() == next;
			int edge = done ? Palette.JADE : isNext ? Palette.GOLD : Palette.argb(0x60, Palette.INK_FAINT);
			int start = kartu.jumlah();
			// Key tokens ([[key:meditate]] ...) show the keys bound right now (DESIGN 33.6 R11).
			int first = kartu.bungkus(l.font, Brush.bold(KeyHints.fill(GuideBook.text(step.titleKey(), step.id()))), INDENT, lebar,
					done ? Palette.JADE_DARK : Palette.INK);
			kartu.bungkus(l.font, KeyHints.fill(GuideBook.body(step)), INDENT, lebar, done ? Palette.INK_SOFT : Palette.mix(Palette.INK, Palette.INK_SOFT, 0.3F));
			String hint = step.hint();
			if (hint != null && !hint.isEmpty()) {
				String text = chatText(hint);
				kartu.baris((lk, i, a, b, c, d, sorot, pilih) -> {
					Component label = Component.literal(text);
					int bw = TalismanButton.lebar(lk.font, label, 60, c - a - INDENT - 2);
					if (TalismanButton.gambar(lk, "kitab.chip." + step.index(), a + INDENT, b + 1, bw, 13, label, true, null, () -> chat(text))) {
						lk.tip(List.of(label, Component.translatable(K + "chip.tip").withStyle(ChatFormatting.GRAY)));
					}
				}, () -> chat(text));
			}
			if (step.manual()) {
				kartu.baris((lk, i, a, b, c, d, sorot, pilih) -> {
					boolean ticked = GuideClient.synced() && GuideClient.ticked(step.index());
					Component label = Component.translatable(ticked ? K + "tick.off" : K + "tick.on");
					int bw = TalismanButton.lebar(lk.font, label, 60, c - a - INDENT - 2);
					boolean bisa = GuideClient.synced() && GuideClient.connected();
					if (TalismanButton.gambar(lk, "kitab.centang." + step.index(), a + INDENT, b + 1, bw, 13, label, bisa,
							Component.translatable(K + "loading"), () -> centang(step), false, ticked)) {
						lk.tip(List.of(label, Component.translatable(K + "mark.manual").withStyle(ChatFormatting.GRAY)));
					}
				}, () -> centang(step));
			}
			kartu.garis((lk, i, a, b, c, d, sorot, pilih) -> {
			});
			// the card: a coloured edge down its lines and its mark (with the mark's tooltip) on the title's first line
			for (int k = start; k < kartu.jumlah() - 1; k++) {
				boolean top = k == first;
				kartu.bungkusUlang(k, (base) -> (lk, i, a, b, c, d, sorot, pilih) -> {
					lk.g.fill(a, b, c, d + 1, done ? Palette.argb(0x30, Palette.JADE_LIGHT) : Palette.argb(0x30, 0xFFFFFF));
					lk.g.fill(a, b, a + 2, d + 1, edge);
					if (top) {
						tanda(lk, step, a + 8.0F, b + 4.5F, done, isNext);
					}
					base.gambar(lk, i, a, b, c, d, sorot, pilih);
				});
			}
		}
	}

	/** The mark: a jade disc with a check, or an empty ring (gold for the next step); its tooltip says how it gets ticked. */
	private static void tanda(Lukis l, GuideBook.Step step, float mx, float my, boolean done, boolean next) {
		if (done) {
			Brush.disc(l.g, mx, my, 4.2F, Palette.JADE);
			check(l.g, mx, my, 5.5F, 0xFFFFFFFF);
		} else {
			Brush.ring(l.g, mx, my, 3.0F, 4.0F, next ? Palette.GOLD_DARK : Palette.INK_FAINT);
		}
		if (l.di(Math.round(mx) - 5, Math.round(my) - 5, Math.round(mx) + 5, Math.round(my) + 5)) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.translatable(done ? K + "mark.done" : K + "mark.open"));
			lines.add(Component.translatable(step.manual() ? K + "mark.manual" : K + "mark.auto").withStyle(ChatFormatting.GRAY));
			if (step.optional()) {
				lines.add(Component.translatable(K + "mark.optional").withStyle(ChatFormatting.DARK_AQUA));
			}
			l.tip(lines);
		}
	}

	/** The global index of the first unfinished core step of the chosen chapter (else its first unfinished step), or −1. */
	private int firstOpen() {
		int any = -1;
		for (GuideBook.Step step : GuideBook.chapter(selected).steps()) {
			if (GuideClient.done(step.index())) {
				continue;
			}
			if (step.core()) {
				return step.index();
			}
			if (any < 0) {
				any = step.index();
			}
		}
		return any;
	}

	private void centang(GuideBook.Step step) {
		if (GuideClient.synced() && GuideClient.connected()) {
			UiSound.toggle(!GuideClient.ticked(step.index()));
			GuideClient.toggle(step);
		} else {
			UiSound.denied();
		}
	}

	/** R6 (43.13): the command goes into the chat box; Esc there comes back to this page. */
	private void chat(String text) {
		HubNav.kembaliSetelah(ChatScreen.class);
		minecraft.gui.setScreen(new ChatScreen(text, false));
	}

	/** A check mark of about {@code size} GUI px centred on {@code cx, cy}. */
	private static void check(GuiGraphicsExtractor g, float cx, float cy, float size, int color) {
		float s = size / 2.0F;
		float thick = Math.max(1.0F, size * 0.2F);
		Brush.line(g, cx - s * 0.75F, cy, cx - s * 0.2F, cy + s * 0.6F, thick, color);
		Brush.line(g, cx - s * 0.25F, cy + s * 0.6F, cx + s * 0.8F, cy - s * 0.65F, thick, color);
	}

	/** The command as it is written into the chat box (always starting with a slash). */
	private static String chatText(@Nullable String hint) {
		if (hint == null) {
			return "";
		}
		String text = hint.strip();
		return text.startsWith("/") ? text : "/" + text;
	}
}
