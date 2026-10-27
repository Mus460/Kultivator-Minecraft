package dev.kultivator.client.sekte.paviliun;

import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.GulirIsi;
import dev.kultivator.sekte.paviliun.PaviliunPayloads;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The Rak Kitab and the Makam Leluhur of module A (DESIGN 36.2.5) as subs of Sekte › Kas &amp; Toko (DESIGN 43.5, 43.12.1:
 * was a screen of its own with two talisman tabs). One page per sub ({@link PaviliunPayloads#PAGE_RAK} or
 * {@link PaviliunPayloads#PAGE_MAKAM}), drawn immediately with the single kit and scrolled as one column:
 * <ul>
 * <li>{@link RakScreen} paints the shelves, the chosen book, the readers and "Setor";</li>
 * <li>{@link MakamScreen} paints the graves, the Kitab Silsilah and the altar stacked under headings (43.0.4).</li>
 * </ul>
 * The page asks for its data when shown ({@link PaviliunClient#minta}); every choice sends A's {@code PaviliunPayloads.Act}
 * and the server answers with a fresh page.
 */
public final class PaviliunScreen extends Page {

	final int page;
	int selectedBook = -1;
	private final GulirIsi gulir = new GulirIsi();

	public PaviliunScreen(int page) {
		this.page = page;
	}

	/** The last data of this page from the server (empty until it arrives). */
	CompoundTag lembar() {
		return PaviliunClient.data(page);
	}

	Font font() {
		return font;
	}

	/** A light confirm over the page (43.7: Rak / Makam used a second click before). */
	void tanya(Component teks, Runnable ya) {
		konfirmasi(SegelKonfirmasi.Tingkat.RINGAN, teks, ya);
	}

	// ================================================================== identity

	@Override
	public Component title() {
		return Component.translatable(page == PaviliunPayloads.PAGE_RAK ? "kultivator.sekte.paviliun.ui.title.rak" : "kultivator.sekte.paviliun.ui.title.makam");
	}

	@Override
	public int glyph() {
		return page == PaviliunPayloads.PAGE_RAK ? Glyphs.CODEX : Glyphs.HALL;
	}

	@Override
	public String helpTopic() {
		return "sekte_paviliun";
	}

	// ================================================================== life

	@Override
	protected void build() {
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		PaviliunClient.minta(page);
		super.onShow(fokus);
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	@Override
	protected void lukis(Lukis l) {
		CompoundTag v = lembar();
		if (v.isEmpty()) {
			l.tengah(Component.translatable("kultivator.gui43.konteks.memuat"), x + w / 2.0F, y + h / 2.0F - 4.0F, w - 8, Palette.INK_FAINT);
			return;
		}
		int atas = gulir.mulai(l, x, y, x + w - 5, y + h) + 2;
		int kanan = x + w - 7;
		int bawah = page == PaviliunPayloads.PAGE_RAK ? RakScreen.lukis(this, l, x + 2, atas, kanan) : MakamScreen.lukis(this, l, x + 2, atas, kanan);
		gulir.selesai(l, bawah + 2);
	}

	// ================================================================== input

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
			return true;
		}
		return gulir.roda(mouseX, mouseY, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return super.keyPressed(event) || gulir.tombol(event);
	}

	// ================================================================== painters' helpers

	/** A thin progress bar with a bright head. */
	static void bar(GuiGraphicsExtractor g, float x, float y, float w, float h, float t, int rgb) {
		float k = Math.max(0.0F, Math.min(1.0F, t));
		Brush.rect(g, x, y, x + w, y + h, Palette.argb(0x40, Palette.INK));
		Brush.rect(g, x, y, x + w * k, y + h, Palette.argb(0xE0, rgb));
		if (k > 0.02F && k < 0.999F) {
			Brush.rect(g, x + w * k - 1.0F, y - 0.5F, x + w * k, y + h + 0.5F, Palette.argb(0xFF, Palette.lighten(0xFF000000 | rgb, 0.5F)));
		}
		Brush.frame(g, Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), Palette.argb(0x80, Palette.INK_SOFT));
	}
}
