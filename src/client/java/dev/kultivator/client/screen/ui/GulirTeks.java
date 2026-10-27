package dev.kultivator.client.screen.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * One scroll of variable content on the kit's {@link DaftarGulir} (43.7, G12): text lines of {@link #GARIS} px (list
 * headings, never selected) and rows of {@link #BARIS} px (selectable with ↑↓; Enter runs the row's action). Text is drawn
 * at 1.0 (43.0.5), never shrunk. Two ways to fill it:
 * <ul>
 * <li>built once when the data or the width changes ({@link #kosongkan}, {@link #bungkus}, {@link #kepala}, {@link #baris}
 * ...): Diri › Jalan, Panduan › Kitab and Ensiklopedia;</li>
 * <li>refilled every frame for a column ({@link #mulai}, {@link #tambah}, {@link #jarak}): the Keabadian pages and the
 * Menara sub (was {@code AbadiClient.Baris}, audit ulang G-3).</li>
 * </ul>
 */
public final class GulirTeks {

	public static final int GARIS = 10;
	public static final int BARIS = 15;
	/** Width the scroll bar takes from the right of the box (wrap text at {@code w - RUANG_BATANG}). */
	public static final int RUANG_BATANG = 8;
	private static final int OK = 0xFF2E8963;

	private final DaftarGulir daftar = new DaftarGulir();
	private final List<DaftarGulir.Baris> isi = new ArrayList<>();
	private final List<Runnable> aksi = new ArrayList<>();
	private final BitSet garis = new BitSet();
	private int tunda = -1;
	private String kunci = "";
	private int tinggi = 100;
	private int lebar = 100;

	public GulirTeks() {
		daftar.judul(garis::get, GARIS);
	}

	public void kosongkan() {
		isi.clear();
		aksi.clear();
		garis.clear();
	}

	/** Starts a new per-frame fill for a column {@code lebar} wide ({@link #tambah} wraps to it). */
	public GulirTeks mulai(int lebar) {
		kosongkan();
		this.lebar = Math.max(20, lebar);
		return this;
	}

	public GulirTeks tambah(Component text, int warna) {
		return tambah(text, warna, 0);
	}

	/** Adds {@code text} wrapped to the {@link #mulai} column; {@code tanda} (ARGB, 0 = none) is a square before its first line. */
	public GulirTeks tambah(Component text, int warna, int tanda) {
		Font font = Minecraft.getInstance().font;
		int indent = tanda != 0 ? 9 : 0;
		boolean first = true;
		for (FormattedCharSequence row : font.split(text, Math.max(10, lebar - 6 - indent))) {
			int mark = first ? tanda : 0;
			garis((l, i, x0, y0, x1, y1, sorot, pilih) -> {
				if (mark != 0) {
					Brush.rect(l.g, x0 + 1.0F, y0 + 2.0F, x0 + 6.0F, y0 + 7.0F, mark);
				}
				Brush.text(l.g, l.font, row, x0 + indent, y0 + 1.0F, warna, 1.0F, false);
			});
			first = false;
		}
		return this;
	}

	/** A blank line between groups. */
	public GulirTeks jarak() {
		garis((l, i, x0, y0, x1, y1, sorot, pilih) -> {
		});
		return this;
	}

	/** Back to the top with the entry cascade (new sub, new chapter). */
	public void keAtas() {
		daftar.mulaiUlang(Util.getMillis());
		tunda = -1;
	}

	/** The number of entries so far (the index the next entry gets). */
	public int jumlah() {
		return isi.size();
	}

	/** Scrolls entry {@code i} to the top on the next frame. */
	public void ke(int i) {
		tunda = i;
	}

	/** A text line (10 px). */
	public void garis(DaftarGulir.Baris baris) {
		garis.set(isi.size());
		isi.add(baris);
		aksi.add(null);
	}

	/** A row (15 px); {@code jalan} runs on Enter while the row is selected (null: none). */
	public void baris(DaftarGulir.Baris baris, @Nullable Runnable jalan) {
		isi.add(baris);
		aksi.add(jalan);
	}

	/** Wraps entry {@code i}'s painter (a card edge, a mark on a first line): {@code f} gets the old painter. */
	public void bungkusUlang(int i, UnaryOperator<DaftarGulir.Baris> f) {
		if (i >= 0 && i < isi.size()) {
			isi.set(i, f.apply(isi.get(i)));
		}
	}

	/** A line cut to the width ("…" and tooltip when longer). */
	public void teks(Component t, int indent, int warna) {
		garis((l, i, x0, y0, x1, y1, sorot, pilih) -> l.teks(t, x0 + indent, y0, x1 - x0 - indent, warna));
	}

	/** {@code t} wrapped into lines {@code lebar} wide (minus {@code indent}). Returns the index of the first line. */
	public int bungkus(Font font, Component t, int indent, int lebar, int warna) {
		int first = isi.size();
		for (FormattedCharSequence row : font.split(t, Math.max(10, lebar - indent))) {
			garis((l, i, x0, y0, x1, y1, sorot, pilih) -> Brush.text(l.g, l.font, row, x0 + indent, y0, warna, 1.0F, false));
		}
		return first;
	}

	/** The source note at the end: faint italics. */
	public void sumber(Font font, Component t, int lebar) {
		bungkus(font, t.copy().withStyle(ChatFormatting.ITALIC), 2, lebar, Palette.INK_FAINT);
	}

	/** A heading line: a small seal, the title in the paper's accent and a gold rule under it. */
	public void kepala(Component t) {
		garis((l, i, x0, y0, x1, y1, sorot, pilih) -> {
			Brush.rect(l.g, x0, y0 + 2.0F, x0 + 4.0F, y0 + 6.0F, Palette.SEAL);
			l.teks(Brush.bold(t), x0 + 6, y0, x1 - x0 - 6, l.gaya.aksen);
			l.g.fill(x0, y1, x1, y1 + 1, Palette.argb(0xB0, Palette.GOLD));
		});
	}

	/** A progress row: a mark, the name (cut), the amount on the right and a bar under them. */
	public void kemajuan(Component tanda, int warnaTanda, Component nama, Component jumlah, float bagian, boolean selesai) {
		baris((l, i, x0, y0, x1, y1, sorot, pilih) -> {
			l.teks(tanda, x0 + 2, y0, 10, warnaTanda);
			int jw = l.font.width(jumlah);
			l.teks(nama, x0 + 13, y0, x1 - x0 - 13 - jw - 6, selesai ? l.gaya.teksLemah : l.gaya.teks);
			l.kanan(jumlah, x1 - 2, y0, selesai ? OK : l.gaya.teksLemah);
			Bilah.gambar(l, x0 + 13, y0 + 10, x1 - 2, bagian, selesai ? Palette.JADE : 0, null);
		}, null);
	}

	/** A talisman row: one click (or Enter while selected) runs {@code jalan}; disabled with {@code alasan}. */
	public void tombol(String kunci, Component label, boolean aktif, @Nullable Component alasan, Runnable jalan) {
		baris((l, i, x0, y0, x1, y1, sorot, pilih) -> {
			int bw = TalismanButton.lebar(l.font, label, 60, x1 - x0 - 4);
			TalismanButton.gambar(l, kunci, x0 + 2, y0 + 1, bw, 13, label, aktif, alasan, jalan);
		}, () -> {
			if (aktif) {
				jalan.run();
			}
		});
	}

	/**
	 * ↑↓ PgUp PgDn Home End scroll the text (actions are reached with Tab like every talisman) while this scroll has the
	 * focus ({@code fokus} = the page's focus key), or with nothing focused when {@code tanpaFokus}. True when used.
	 */
	public boolean tombol(KeyEvent event, @Nullable String fokus, boolean tanpaFokus) {
		if (fokus == null ? !tanpaFokus : !fokus.equals(kunci)) {
			return false;
		}
		float halaman = Math.max(1.0F, (tinggi - GARIS) / 18.0F);
		float langkah = switch (event.key()) {
			case InputConstants.KEY_UP -> GARIS / 18.0F;
			case InputConstants.KEY_DOWN -> -GARIS / 18.0F;
			case InputConstants.KEY_PAGEUP -> halaman;
			case InputConstants.KEY_PAGEDOWN -> -halaman;
			case InputConstants.KEY_HOME -> 100000.0F;
			case InputConstants.KEY_END -> -100000.0F;
			default -> 0.0F;
		};
		if (langkah == 0.0F) {
			return false;
		}
		daftar.roda(langkah);
		return true;
	}

	/** Draws the entries into the box (the list scrolls when they do not fit). */
	public void gambar(Lukis l, String kunci, int x0, int y0, int x1, int y1) {
		if (y1 - y0 < GARIS) {
			return;
		}
		this.kunci = kunci;
		this.tinggi = y1 - y0;
		daftar.gambar(l, kunci, x0, y0, x1, y1, isi.size(), BARIS, (lk, i, a, b, c, d, sorot, pilih) -> {
			if (pilih && aksi.get(i) != null) {
				lk.kartu(a, b, c, d, 0.0F, true);
			}
			isi.get(i).gambar(lk, i, a, b, c, d, sorot, pilih);
		}, this::pakai, null);
		if (tunda >= 0 && tunda < isi.size()) {
			// Two jumps after the first layout: to the end, then back up to the entry, so it lands at the top.
			daftar.setPilih(isi.size() - 1);
			daftar.setPilih(tunda);
			tunda = -1;
		}
	}

	private void pakai() {
		int p = daftar.pilihan();
		Runnable r = p >= 0 && p < aksi.size() ? aksi.get(p) : null;
		if (r != null) {
			r.run();
		}
	}
}
