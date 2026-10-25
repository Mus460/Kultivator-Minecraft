package dev.kultivator.client.tempat.ui.kit;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

/**
 * {@code GelembungDialog} (DESIGN 38.U.2, 38.U.3.1): the speech panel: the live portrait ({@link Potret}) on the left, the
 * speaker's name and role title, the sect line, and the current line written out like a brush (about 60 characters per
 * second; a click or Space shows it all at once). A new line restarts the brush. The speaker's "talking" amount drives the
 * portrait's nod.
 *
 * <p>Small windows (GUI scale 4 on a laptop gives a 126 px content area): a line that does not fit the bubble is first
 * written smaller (0.85, then 0.75), and when it still does not fit it is split into pages; a pulsing "▼ 1/3" marks
 * more, and a click or Space turns the page. The screen keeps its choices hidden until the last page ({@link #adaLanjut}).
 */
public final class GelembungDialog {

	private static final float HURUF_PER_DETIK = 60.0F;
	private static final float[] UKURAN = {1.0F, 0.85F, 0.75F};

	private String teksTerakhir = "";
	private long mulai;
	private boolean selesaiPaksa;
	private int halaman;
	private int jumlahHalaman = 1;
	private int hurufHalaman = Integer.MAX_VALUE;

	/** Shows the whole current page now. */
	public void selesaikan() {
		selesaiPaksa = true;
	}

	/** True while the brush is still writing the current page. */
	public boolean menulis(long kini) {
		int total = hurufHalaman == Integer.MAX_VALUE ? teksTerakhir.length() : hurufHalaman;
		return !selesaiPaksa && (kini - mulai) / 1000.0F * HURUF_PER_DETIK < total;
	}

	/** True when the line has pages after the current one. */
	public boolean adaLanjut() {
		return halaman < jumlahHalaman - 1;
	}

	/** Turns to the next page when the current one is fully written; true when it turned. */
	public boolean lanjut() {
		long now = Util.getMillis();
		if (menulis(now) || !adaLanjut()) {
			return false;
		}
		halaman++;
		mulai = now;
		selesaiPaksa = false;
		hurufHalaman = Integer.MAX_VALUE;
		UiSound.tab();
		return true;
	}

	/** A click or Space: finish the brush, else turn the page. True when something happened. */
	public boolean tekan() {
		if (menulis(Util.getMillis())) {
			selesaikan();
			return true;
		}
		return lanjut();
	}

	/**
	 * Draws the bubble in the box; {@code npc} is the sheet's speaker card, {@code teks} the line. Returns the bottom y of
	 * the text block.
	 */
	public int gambar(Lukis l, Lembar npc, Component teks, int x0, int y0, int x1, int y1) {
		String plain = teks.getString();
		if (!plain.equals(teksTerakhir)) {
			teksTerakhir = plain;
			mulai = l.kini;
			selesaiPaksa = false;
			halaman = 0;
			hurufHalaman = Integer.MAX_VALUE;
		}
		int potret = Potret.ukuran(Math.min(56, y1 - y0 - 4));
		boolean takut = npc.b(LembarKunci.TAKUT);
		float bicara = menulis(l.kini) ? 1.0F : 0.15F;
		Potret.gambar(l, x0, y0, potret, npc.i(LembarKunci.POTRET, -1), npc.i(LembarKunci.IKON, -1), takut, bicara);
		int tx = x0 + potret + 8;
		// name line: name · title, sect under it
		Component nama = npc.teks(LembarKunci.NAMA);
		Component gelar = npc.teksAtau(LembarKunci.GELAR);
		Component kepala = gelar == null ? Brush.bold(nama) : Brush.bold(nama).copy().append(Component.literal(" · ").withColor(l.gaya.teksSamar))
				.append(gelar.copy().withColor(l.gaya.teksLemah));
		l.teks(kepala, tx, y0 + 1, x1 - tx, l.gaya.aksen);
		int ty = y0 + 12;
		Component sekte = npc.teksAtau(LembarKunci.SEKTE);
		if (sekte != null) {
			l.keterangan(sekte, tx, ty, x1 - tx, l.gaya.teksSamar);
			ty += 9;
		}
		// the bubble: a soft paper well with a tail pointing at the portrait
		int bx0 = tx - 4;
		int by0 = ty + 1;
		int bx1 = x1;
		int ruangTeks = Math.max(10, y1 - by0 - 6);
		int lebarTeks = Math.max(20, bx1 - bx0 - 10);
		// the largest size that fits; else the smallest size in pages
		float s = UKURAN[UKURAN.length - 1];
		List<FormattedCharSequence> lines = null;
		for (float u : UKURAN) {
			List<FormattedCharSequence> coba = l.font.split(teks, Math.max(10, Math.round(lebarTeks / u)));
			if (coba.size() * 10.0F * u - 2.0F * u <= ruangTeks) {
				s = u;
				lines = coba;
				break;
			}
		}
		if (lines == null) {
			lines = l.font.split(teks, Math.max(10, Math.round(lebarTeks / s)));
		}
		float lh = 10.0F * s;
		int perHalaman = Math.max(1, (int) Math.floor((ruangTeks + 2.0F * s) / lh));
		jumlahHalaman = Math.max(1, (lines.size() + perHalaman - 1) / perHalaman);
		halaman = Math.min(halaman, jumlahHalaman - 1);
		int dari = halaman * perHalaman;
		int sampai = Math.min(lines.size(), dari + perHalaman);
		int huruf = 0;
		for (int i = dari; i < sampai; i++) {
			huruf += panjang(lines.get(i));
		}
		hurufHalaman = huruf;
		int barisTampil = sampai - dari;
		int by1 = jumlahHalaman > 1 ? y1 : Math.max(by0 + 20, Math.min(y1, Math.round(by0 + 6 + barisTampil * lh)));
		PanelTempat.sumur(l.g, l.gaya, bx0, by0, bx1, by1);
		for (int k = 0; k < 4; k++) {
			l.g.fill(bx0 - 1 - k, by0 + 6 + k, bx0, by0 + 7 + k, l.gaya.sumur);
		}
		int shown = selesaiPaksa ? Integer.MAX_VALUE : Math.round((l.kini - mulai) / 1000.0F * HURUF_PER_DETIK);
		int sisa = shown;
		int warna = l.gaya.teksSumur();
		for (int i = dari; i < sampai; i++) {
			if (sisa <= 0) {
				break;
			}
			FormattedCharSequence line = lines.get(i);
			int len = panjang(line);
			float ly = by0 + 4 + (i - dari) * lh;
			if (sisa >= len) {
				Brush.text(l.g, l.font, line, bx0 + 5, ly, warna, s, false);
			} else {
				FormattedCharSequence part = potong(line, sisa);
				Brush.text(l.g, l.font, part, bx0 + 5, ly, warna, s, false);
				// the wet brush tip
				float w = l.font.width(part) * s;
				Brush.dot(l.g, bx0 + 6 + w, ly + 6 * s, 1.0F, Palette.argb(0xC0, warna & 0xFFFFFF));
			}
			sisa -= len;
		}
		if (jumlahHalaman > 1) {
			// "▼ 2/3": more to read; pulses once the page is written
			boolean siap = !menulis(l.kini) && adaLanjut();
			Component tanda = Component.literal((adaLanjut() ? "▼ " : "") + (halaman + 1) + "/" + jumlahHalaman);
			float tw = l.font.width(tanda) * 0.6F;
			float bob = siap ? (float) Math.sin(l.kini / 160.0D) * 1.0F : 0.0F;
			int a = siap ? 0xFF : 0x90;
			Brush.text(l.g, l.font, tanda, bx1 - tw - 4, by1 - 7 + bob, Palette.argb(a, warna & 0xFFFFFF), 0.6F, false);
		}
		l.zona("gelembung", bx0, by0, bx1, by1, this::tekan, true, null, false);
		return by1;
	}

	private static int panjang(FormattedCharSequence seq) {
		int[] n = {0};
		seq.accept((index, style, cp) -> {
			n[0]++;
			return true;
		});
		return n[0];
	}

	private static FormattedCharSequence potong(FormattedCharSequence seq, int max) {
		return sink -> {
			int[] n = {0};
			return seq.accept((index, style, cp) -> {
				if (n[0]++ >= max) {
					return false;
				}
				return sink.accept(index, style, cp);
			});
		};
	}
}
