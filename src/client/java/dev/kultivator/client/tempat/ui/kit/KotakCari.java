package dev.kultivator.client.tempat.ui.kit;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import java.util.Locale;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * The shop search slip (DESIGN 38.U.2: {@code /} searches the shop list): {@code /} or a click starts writing, Backspace
 * erases, Enter or Escape puts the brush down (Escape with text clears it first). Filtering is client side; screens ask
 * {@link #cocok} per entry name.
 */
public final class KotakCari {

	public static final int MAKS = 32;

	private String teks = "";
	private boolean aktif;
	/** The '/' that opened the slip by keyboard also arrives as a typed character: drop that one. */
	private boolean abaikanGaris;

	public String teks() {
		return teks;
	}

	public boolean aktif() {
		return aktif;
	}

	public void berhenti() {
		aktif = false;
	}

	public void mulai() {
		aktif = true;
		abaikanGaris = false;
		UiSound.press();
	}

	/** Starts writing from the {@code /} key (its character event is swallowed). */
	public void mulaiDariTombol() {
		mulai();
		abaikanGaris = true;
	}

	/** True when {@code nama} matches the search (case-insensitive substring; empty search matches all). */
	public boolean cocok(Component nama) {
		return teks.isEmpty() || nama.getString().toLowerCase(Locale.ROOT).contains(teks.toLowerCase(Locale.ROOT));
	}

	public boolean kosong() {
		return teks.isEmpty();
	}

	public void gambar(Lukis l, String kunci, int x, int y, int w, int h) {
		dev.kultivator.client.screen.ui.Ketik.fokus(this, aktif);
		l.zona(kunci, x, y, x + w, y + h, this::mulai, true, null, true);
		boolean over = l.di(x, y, x + w, y + h);
		PanelTempat.sumur(l.g, l.gaya, x, y, x + w, y + h);
		Brush.frame(l.g, x, y, x + w, y + h, aktif ? Palette.SEAL : Palette.argb(over ? 0xE0 : 0x90, TempatWarna.EMAS & 0xFFFFFF));
		Brush.line(l.g, x + 3.0F, y + h - 3.0F, x + 7.0F, y + 3.0F, 1.2F, Palette.INK_SOFT);
		Brush.dot(l.g, x + 3.0F, y + h - 3.0F, 1.0F, Palette.INK);
		float ukuran = 1.0F; // 43.0.5
		float tx = x + 10.0F;
		float ty = y + (h - 8.0F * ukuran) / 2.0F;
		int warnaTeks = l.gaya.teksSumur();
		if (teks.isEmpty() && !aktif) {
			Brush.fit(l.g, l.font, TeksTempat.k("cari.petunjuk"), tx, ty, w - 13.0F, Palette.argb(0x90, warnaTeks & 0xFFFFFF), ukuran, ukuran, false);
		} else {
			boolean caret = aktif && l.kini / 400L % 2L == 0L;
			String shown = teks;
			while (shown.length() > 1 && l.font.width(shown + "_") * ukuran > w - 13.0F) {
				shown = shown.substring(1);
			}
			Brush.text(l.g, l.font, Component.literal(shown + (caret ? "_" : "")), tx, ty, warnaTeks, ukuran, false);
		}
		l.fokusCincin(kunci, x, y, x + w, y + h);
		if (aktif) {
			l.cari = this;
		}
	}

	/** Keys while writing; true when consumed. */
	public boolean tombol(KeyEvent event) {
		if (!aktif) {
			return false;
		}
		int key = event.key();
		if (key == InputConstants.KEY_ESCAPE) {
			if (!teks.isEmpty()) {
				teks = "";
			} else {
				aktif = false;
			}
			return true;
		}
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_TAB) {
			aktif = false;
			return key != InputConstants.KEY_TAB;
		}
		if (key == InputConstants.KEY_BACKSPACE) {
			if (!teks.isEmpty()) {
				teks = teks.substring(0, teks.length() - 1);
			}
			return true;
		}
		return key != InputConstants.KEY_UP && key != InputConstants.KEY_DOWN && key != InputConstants.KEY_PAGEUP
				&& key != InputConstants.KEY_PAGEDOWN;
	}

	public boolean ketik(CharacterEvent event) {
		if (!aktif) {
			return false;
		}
		int c = event.codepoint();
		if (abaikanGaris) {
			abaikanGaris = false;
			if (c == '/') {
				return true;
			}
		}
		if (!Character.isBmpCodePoint(c) || Character.isISOControl(c)) {
			return true;
		}
		if (teks.length() < MAKS) {
			teks = teks + (char) c;
		}
		return true;
	}
}
