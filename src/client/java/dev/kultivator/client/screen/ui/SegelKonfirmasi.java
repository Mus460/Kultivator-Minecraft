package dev.kultivator.client.screen.ui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * The single confirmation of the kit (DESIGN 43.7, G13, 43.17 K2): a card laid over the page (the page dims under it)
 * with the question, a red "Ya" seal and "Batal". Three weights:
 * <ul>
 * <li>{@link Tingkat#RINGAN}: click Ya (or Enter);</li>
 * <li>{@link Tingkat#SEDANG}: hold Ya (mouse or Enter) for 1.5 s while a ring fills;</li>
 * <li>{@link Tingkat#BERAT} / {@link Tingkat#BERAT_LAMA}: tick "Saya mengerti" first, then hold 3 / 5 s; blood red.</li>
 * </ul>
 * Esc or Batal cancels. It replaces the click-twice-in-3-s pattern, {@code JadeCoin.askingWhen}, the Kehancuran confirm
 * screen, the motion / photosensitivity warnings and every hand-made hold. The page's {@link Fokus} owns one and gives it
 * every input while open; the hold is read from the live mouse and key state each frame, so no release event is needed.
 */
public final class SegelKonfirmasi {

	/** How heavy the decision is. */
	public enum Tingkat {
		RINGAN(0L, false), SEDANG(1500L, false), BERAT(3000L, true), BERAT_LAMA(5000L, true);

		final long tahanMs;
		final boolean centang;

		Tingkat(long tahanMs, boolean centang) {
			this.tahanMs = tahanMs;
			this.centang = centang;
		}
	}

	private static final String K = "kultivator.gui43.kit.konfirmasi.";
	private static final int W = 220;

	private @Nullable Tingkat tingkat;
	private Component teks = Component.empty();
	private @Nullable Runnable ya;
	private boolean dicentang;
	private boolean tahanMouse;
	/** Enter was up at least once since the card opened (the Enter that opened it must not confirm it). */
	private boolean enterSiap;
	private float isi;
	private long dibuka;
	// boxes of the last frame (layout coordinates)
	private int yaX0;
	private int yaY0;
	private int yaX1;
	private int yaY1;
	private int batalX0;
	private int batalY0;
	private int batalX1;
	private int batalY1;
	private int cenX0;
	private int cenY0;
	private int cenX1;
	private int cenY1;
	private int kartuX0;
	private int kartuY0;
	private int kartuX1;
	private int kartuY1;

	/** Opens the card; a confirm already open is replaced. */
	public void buka(Tingkat tingkat, Component teks, Runnable ya) {
		this.tingkat = tingkat;
		this.teks = teks;
		this.ya = ya;
		this.dicentang = false;
		this.tahanMouse = false;
		this.enterSiap = false;
		this.isi = 0.0F;
		this.dibuka = net.minecraft.util.Util.getMillis();
		UiSound.ask();
	}

	public boolean terbuka() {
		return tingkat != null;
	}

	public void tutup() {
		tingkat = null;
		ya = null;
		tahanMouse = false;
	}

	private void setuju() {
		Runnable r = ya;
		tutup();
		UiSound.press();
		if (r != null) {
			r.run();
		}
	}

	private boolean siap() {
		return tingkat != null && (!tingkat.centang || dicentang);
	}

	// ------------------------------------------------------------------ input

	public void klik(double x, double y) {
		if (tingkat == null) {
			return;
		}
		if (in(x, y, batalX0, batalY0, batalX1, batalY1)) {
			UiSound.close();
			tutup();
			return;
		}
		if (tingkat.centang && in(x, y, cenX0, cenY0, cenX1, cenY1)) {
			dicentang = !dicentang;
			UiSound.toggle(dicentang);
			return;
		}
		if (in(x, y, yaX0, yaY0, yaX1, yaY1)) {
			if (!siap()) {
				UiSound.denied();
				return;
			}
			if (tingkat.tahanMs <= 0L) {
				setuju();
			} else {
				tahanMouse = true;
			}
		}
	}

	public void lepas() {
		tahanMouse = false;
	}

	/** Keys while open: Esc cancels, Enter confirms (light) or starts the hold, Space ticks the box; all others eaten. */
	public boolean tombol(KeyEvent event) {
		if (tingkat == null) {
			return false;
		}
		int key = event.key();
		if (event.isEscape()) {
			UiSound.close();
			tutup();
		} else if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) && tingkat.tahanMs <= 0L) {
			if (enterSiap) {
				setuju();
			}
		} else if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) && !siap()) {
			UiSound.denied();
		} else if (key == InputConstants.KEY_SPACE && tingkat.centang) {
			dicentang = !dicentang;
			UiSound.toggle(dicentang);
		}
		return true;
	}

	private static boolean in(double x, double y, int x0, int y0, int x1, int y1) {
		return x >= x0 && y >= y0 && x < x1 && y < y1;
	}

	// ------------------------------------------------------------------ drawing

	/** Draws the card over the box (x0..y1); mouse in layout coordinates. Runs the hold. */
	public void gambar(GuiGraphicsExtractor g, Font font, int x0, int y0, int x1, int y1, int mx, int my, float detik, long kini) {
		if (tingkat == null) {
			return;
		}
		boolean berat = tingkat.centang;
		g.fill(x0, y0, x1, y1, berat ? 0xA0200808 : 0x90101418);
		int w = Math.min(W, x1 - x0 - 8);
		List<FormattedCharSequence> lines = font.split(teks, Math.max(20, w - 16));
		int baris = Math.min(lines.size(), Math.max(1, (y1 - y0 - 60) / 9));
		int h = 12 + 9 * baris + 6 + (berat ? 16 : 0) + 18 + 8;
		int cx = (x0 + x1) / 2;
		kartuX0 = cx - w / 2;
		kartuX1 = kartuX0 + w;
		kartuY0 = Math.max(y0 + 2, (y0 + y1) / 2 - h / 2);
		kartuY1 = Math.min(y1 - 2, kartuY0 + h);
		float buka = Math.min(1.0F, (kini - dibuka) / 140.0F);
		int tepi = berat ? Palette.SEAL_DARK : Palette.GOLD;
		g.fill(kartuX0 + 3, kartuY0 + 3, kartuX1 + 3, kartuY1 + 3, 0x60000000);
		g.fillGradient(kartuX0, kartuY0, kartuX1, kartuY1, berat ? 0xFFF0D8CC : Palette.PAPER_TOP, berat ? 0xFFD9B4A4 : Palette.PAPER_BOTTOM);
		Brush.frame(g, kartuX0, kartuY0, kartuX1, kartuY1, tepi);
		Brush.frame(g, kartuX0 + 2, kartuY0 + 2, kartuX1 - 2, kartuY1 - 2, Palette.argb(0x90, tepi));
		int ty = kartuY0 + 8;
		for (int i = 0; i < baris; i++) {
			FormattedCharSequence line = lines.get(i);
			Brush.text(g, font, line, cx - font.width(line) / 2.0F, ty + i * 9, berat ? Palette.SEAL_DARK : Palette.INK, 1.0F, false);
		}
		ty += 9 * baris + 6;
		if (berat) {
			cenX0 = kartuX0 + 10;
			cenY0 = ty;
			cenX1 = kartuX1 - 10;
			cenY1 = ty + 12;
			boolean over = in(mx, my, cenX0, cenY0, cenX1, cenY1);
			g.fill(cenX0, cenY0 + 1, cenX0 + 10, cenY0 + 11, 0xFFF6EED2);
			Brush.frame(g, cenX0, cenY0 + 1, cenX0 + 10, cenY0 + 11, over ? Palette.SEAL : Palette.SEAL_DARK);
			if (dicentang) {
				Brush.line(g, cenX0 + 2.0F, cenY0 + 6.0F, cenX0 + 4.5F, cenY0 + 9.0F, 1.4F, Palette.SEAL);
				Brush.line(g, cenX0 + 4.5F, cenY0 + 9.0F, cenX0 + 8.5F, cenY0 + 2.5F, 1.4F, Palette.SEAL);
			}
			Brush.fit(g, font, Component.translatable(K + "mengerti"), cenX0 + 14.0F, cenY0 + 2.0F, cenX1 - cenX0 - 14.0F, Palette.SEAL_DARK, 1.0F,
					1.0F, false);
			ty += 16;
		} else {
			cenX0 = cenY0 = cenX1 = cenY1 = 0;
		}
		int bw = (w - 30) / 2;
		batalX0 = kartuX0 + 10;
		batalY0 = ty;
		batalX1 = batalX0 + bw;
		batalY1 = ty + 16;
		yaX1 = kartuX1 - 10;
		yaX0 = yaX1 - bw;
		yaY0 = ty;
		yaY1 = ty + 16;
		// the hold: mouse held on Ya, or Enter held, while ready
		boolean overYa = in(mx, my, yaX0, yaY0, yaX1, yaY1);
		Minecraft mc = Minecraft.getInstance();
		boolean mouseDown = tahanMouse && overYa && mc.mouseHandler.isLeftPressed();
		boolean enterDown = InputConstants.isKeyDown(InputConstants.KEY_RETURN) || InputConstants.isKeyDown(InputConstants.KEY_NUMPADENTER);
		if (!mc.mouseHandler.isLeftPressed()) {
			tahanMouse = false;
		}
		if (!enterDown) {
			enterSiap = true;
		}
		enterDown &= enterSiap;
		if (tingkat.tahanMs > 0L) {
			boolean tahan = siap() && (mouseDown || enterDown);
			isi = tahan ? isi + detik * 1000.0F / tingkat.tahanMs : Math.max(0.0F, isi - detik * 2.5F);
			if (isi >= 1.0F) {
				setuju();
				return;
			}
		}
		tombol(g, font, batalX0, batalY0, batalX1, batalY1, Component.translatable(K + "batal"), in(mx, my, batalX0, batalY0, batalX1, batalY1),
				false, 0.0F, true);
		Component label = Component.translatable(tingkat.tahanMs > 0L ? K + "tahan" : K + "ya");
		tombol(g, font, yaX0, yaY0, yaX1, yaY1, label, overYa, true, isi, siap());
		if (buka < 1.0F) {
			g.fill(kartuX0, kartuY0, kartuX1, kartuY1, Palette.argb(Math.round(180 * (1.0F - buka)), Palette.PAPER_TOP));
		}
	}

	private void tombol(GuiGraphicsExtractor g, Font font, int x0, int y0, int x1, int y1, Component label, boolean over, boolean merah, float isi,
			boolean aktif) {
		int top = merah ? (aktif ? Palette.mix(Palette.SEAL, Palette.SEAL_GLOW, over ? 0.35F : 0.0F) : Palette.grey(Palette.SEAL, 0.7F))
				: Palette.lighten(Palette.TALISMAN, over ? 0.15F : 0.0F);
		int bottom = merah ? (aktif ? Palette.SEAL_DARK : Palette.grey(Palette.SEAL_DARK, 0.7F)) : Palette.TALISMAN_LOW;
		g.fill(x0 + 1, y0 + 2, x1 + 1, y1 + 1, 0x40000000);
		g.fillGradient(x0, y0, x1, y1, top, bottom);
		if (isi > 0.0F) {
			g.fill(x0, y1 - 3, x0 + Math.round((x1 - x0) * Math.min(1.0F, isi)), y1, Palette.GOLD_LIGHT);
		}
		Brush.frame(g, x0, y0, x1, y1, merah ? Palette.SEAL_DARK : Palette.TALISMAN_EDGE);
		int ink = merah ? Palette.TALISMAN : Palette.INK;
		float w = Math.min(x1 - x0 - 6, font.width(label));
		Brush.fit(g, font, label, (x0 + x1) / 2.0F - w / 2.0F, y0 + (y1 - y0 - 8) / 2.0F, x1 - x0 - 6, ink, 1.0F, 1.0F, false);
	}

	/** Tooltip lines for the mouse at (mx, my) (why Ya is not ready yet). */
	public List<Component> tip(int mx, int my) {
		if (tingkat == null || !in(mx, my, yaX0, yaY0, yaX1, yaY1)) {
			return List.of();
		}
		if (!siap()) {
			return List.of(Component.translatable(K + "centang_dulu"));
		}
		return tingkat.tahanMs > 0L ? List.of(Component.translatable(K + "tahan_tip", tingkat.tahanMs / 1000.0F)) : List.of();
	}
}
