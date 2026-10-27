package dev.kultivator.client.sekte.gabung.ui.layar;

import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.GulirIsi;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.sekte.gabung.ui.IkonGabung;
import dev.kultivator.client.screen.ui.Lembar;
import dev.kultivator.client.tempat.ui.layar.LayarInti;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The base of every 41 screen (DESIGN 41.10, 43.7): the sect header strip (emblem in its ink colour, class plaque and
 * tier, alignment, corak, cloud palace, MEROSOT), the dark red wash of a demonic sect's stone panel ({@code #6A1420}),
 * tick/cross requirement rows, the small bar and the confirmation of dangerous buttons (43.7: the one
 * {@code SegelKonfirmasi} of the Gulungan, held 1.5 s).
 * <p>
 * A helper subclass of {@link LayarInti}, not a second base: the sheet, the tabs as subs, the info strip, the lock and the
 * input (the page's focus ring, Tab, Enter, digits) come from there; a 41 screen only draws its content in
 * {@link #isiGabung} with the single kit {@code screen/ui}. Everything it draws is text 1.0 (43.0.5).
 * <p>
 * A 41 screen opened from the hub or a command is a personal anchor session on the server ({@code GabungLayar}), so it may
 * act: {@link #hanyaBaca} is false (the server still checks every op).
 */
public abstract class LayarGabungDasar extends LayarInti {

	/** Lang prefix of the 41U texts. */
	public static final String K = "kultivator.gabung.ui.";
	/** Below this content height (GUI scale 4 on a small window) the sect strip is dropped to give the page its room. */
	protected static final int PENDEK = 135;

	/** Height of the content box this frame (set before {@link #isiGabung}). */
	protected int tinggiIsi;
	/** The scrolled column the sheet drew this frame (wheel and keys go to it), or null. */
	protected @Nullable GulirIsi gulirAktif;
	/** The page shown in the narrow layout of a split sheet ({@link #bilahHalaman}); reset by the sheet's tab change. */
	protected int halaman;

	protected LayarGabungDasar(String layar, int npcId, CompoundTag lembar, Component judul, String gayaId) {
		super(layar, npcId, lembar, judul, gayaId);
	}

	public static MutableComponent t(String kunci, Object... args) {
		return Component.translatable(K + kunci, args);
	}

	@Override
	public boolean hanyaBaca() {
		return false;
	}

	@Override
	protected final void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		// the box of the base is inset by 2: the height the old engine gave the sheet
		tinggiIsi = y1 - y0 + 4;
		gulirAktif = null;
		if (lembar.i(GabungLembar.ALIRAN, -1) == 2 && lembar.ada(GabungLembar.SEKTE_ID)) {
			// the demonic sects' stone panel washed in dark red (41.10)
			l.g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, Palette.argb(0x30, Palette.IBLIS & 0xFFFFFF));
		}
		isiGabung(l, x0, y0, x1, y1);
	}

	/** The screen's content (after the alignment wash). */
	protected abstract void isiGabung(Lukis l, int x0, int y0, int x1, int y1);

	/** The wheel goes to the scrolled column of the frame when no list took it. */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY) || gulirAktif != null && gulirAktif.roda(mouseX, mouseY, scrollY);
	}

	/** Arrow / page keys scroll the column of the frame (keyboard only) once no list owns them. */
	@Override
	protected boolean tombolLain(KeyEvent event) {
		return gulirAktif != null && gulirAktif.tombol(event);
	}

	// ================================================================== single-kit adapters (names the sheets use)

	/** A stacked heading (43.0.4). Returns the height used (13). */
	protected int subjudul(Lukis l, Component judul, int x0, int y, int x1) {
		return l.kepala(judul, x0, y, x1);
	}

	/** An inset well over a box of the layout. */
	protected void sumur(Lukis l, Kotak k) {
		l.sumur(k.x0(), k.y0(), k.x1(), k.y1());
	}

	// ================================================================== sect header

	/**
	 * The sect strip of the sheet's header keys: emblem, class plaque + tier, corak, alignment, cloud palace, MEROSOT, and
	 * a thin rule in the sect's ink colour. Returns the y under it (the same y when the box is short).
	 */
	protected int kepalaSekte(Lukis l, int x0, int y0, int x1) {
		if (!lembar.ada(GabungLembar.SEKTE_ID) || tinggiIsi < PENDEK) {
			return y0;
		}
		int y = y0 + 1;
		IkonGabung.lambang(l.g, font, lembar.i(GabungLembar.LAMBANG, 0), x0 + 6.5F, y + 6.5F, 6.5F);
		int x = x0 + 16;
		int kelas = lembar.i(GabungLembar.KELAS, 1);
		IkonGabung.gambar(l.g, IkonGabung.kelas(kelas), x, y, 12);
		x += 14;
		Component k = KelasSekte.byNomor(kelas).nama().copy().append(" · ").append(t("tier", lembar.i(GabungLembar.TINGKAT, 1)));
		int kw = Math.min(130, Math.min(font.width(k), x1 - x));
		l.teks(k, x, y + 2, kw, l.gaya.teks);
		x += kw + 6;
		String corak = lembar.s(GabungLembar.CORAK);
		if (!corak.isEmpty() && x + 60 < x1) {
			IkonGabung.gambar(l.g, IkonGabung.corak(corak), x, y, 12);
			Component c = t("corak", Corak.byId(corak, Corak.PEDANG).nama());
			int cw = Math.min(70, font.width(c));
			l.teks(c, x + 14, y + 2, cw, l.gaya.teksLemah);
			x += 14 + cw + 6;
		}
		int aliran = lembar.i(GabungLembar.ALIRAN, 0);
		if (x + 12 < x1) {
			IkonGabung.gambar(l.g, IkonGabung.ALIRAN + Math.max(0, Math.min(2, aliran)), x, y, 12);
			if (l.di(x, y, x + 12, y + 12)) {
				l.tip(t("aliran." + aliran));
			}
			x += 14;
		}
		if (lembar.b(GabungLembar.LANGIT) && x + 12 < x1) {
			IkonGabung.gambar(l.g, IkonGabung.LANGIT, x, y, 12);
			if (l.di(x, y, x + 12, y + 12)) {
				l.tip(t("langit"));
			}
			x += 14;
		}
		if (lembar.b(GabungLembar.MEROSOT) && x + 12 < x1) {
			IkonGabung.gambar(l.g, IkonGabung.MEROSOT, x, y, 12);
			if (l.di(x, y, x + 12, y + 12)) {
				l.tip(t("merosot"));
			}
		}
		int ink = Palette.opaque(lembar.i(GabungLembar.WARNA, 0xB0B0B0));
		l.g.fill(x0, y0 + 15, x1, y0 + 16, Palette.argb(0xB0, ink & 0xFFFFFF));
		return y0 + 19;
	}

	// ================================================================== rows and bars

	/**
	 * A requirement row: tick or cross, the line, then "punya" and the advice as a caption. Returns the height used
	 * (10, or 18 with a caption); draws nothing past {@code yMaks}.
	 */
	protected int barisSyarat(Lukis l, Lembar s, int x0, int y, int x1, int yMaks) {
		if (y + 9 > yMaks) {
			return 0;
		}
		boolean ok = s.b(LembarKunci.OK);
		Glyphs.Atlas.ikon(l.g, ok ? Glyphs.Atlas.CENTANG : Glyphs.Atlas.SILANG, x0, y, 9, 0xFFFFFFFF);
		l.teks(s.teks(LembarKunci.TEKS), x0 + 12, y + 1, x1 - x0 - 12, ok ? l.gaya.teks : l.gaya.teksLemah);
		Component punya = s.teksAtau(GabungLembar.PUNYA);
		Component saran = ok ? null : s.teksAtau(GabungLembar.SARAN);
		if ((punya != null || saran != null) && y + 18 <= yMaks) {
			MutableComponent cap = Component.empty();
			if (punya != null) {
				cap.append(t("punya", punya));
			}
			if (saran != null) {
				if (punya != null) {
					cap.append("  ");
				}
				cap.append(t("saran_panah", saran));
			}
			l.teks(cap, x0 + 12, y + 10, x1 - x0 - 12, saran != null ? Palette.PERINGATAN : l.gaya.teksSamar);
			return 19;
		}
		return 11;
	}

	/**
	 * The requirement lines as a scrolled list (19 px rows: the line plus its caption), so none is cut off in a short box
	 * and the arrow keys reach every one (D-41U). Draws the list's own focus ring.
	 */
	protected void daftarSyarat(Lukis l, DaftarGulir gulir, String kunci, List<Lembar> syarat, int x0, int y0, int x1, int y1) {
		if (y1 - y0 < 10) {
			return;
		}
		gulir.gambar(l, kunci, x0, y0, x1, y1, syarat.size(), 19, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) ->
				barisSyarat(lk, syarat.get(i), rx0 + 2, ry0 + 1, rx1 - 2, ry1), null, null);
	}

	/**
	 * Narrow layout only: the two panes of a split sheet become two pages behind a row of two chips. Returns the y under
	 * the row ({@code y0} itself in the other layouts, which show both panes).
	 */
	protected int bilahHalaman(Lukis l, int x0, int y0, int x1, Component a, Component b) {
		if (tata() != Tata.SEMPIT) {
			return y0;
		}
		int w = (x1 - x0 - 3) / 2;
		TalismanButton.gambar(l, "hal0", x0, y0, w, 13, a, true, null, () -> halaman = 0, false, halaman == 0);
		TalismanButton.gambar(l, "hal1", x0 + w + 3, y0, w, 13, b, true, null, () -> halaman = 1, false, halaman == 1);
		return y0 + 16;
	}

	/** {@link #bagi} in the wide layouts; in the narrow one only the chosen page, as the whole box (the other is null). */
	protected Kotak[] bagiHalaman(int x0, int y0, int x1, int y1) {
		if (tata() != Tata.SEMPIT) {
			return bagi(x0, y0, x1, y1);
		}
		Kotak penuh = new Kotak(x0, y0, x1, y1);
		return halaman == 0 ? new Kotak[] {penuh, null} : new Kotak[] {null, penuh};
	}

	/** A small bar 0..1 (the kit's {@link Bilah}) with an optional marker at {@code tanda} (-1 none). */
	protected static void batang(Lukis l, int x0, int y, int w, float isi, int warna, float tanda) {
		Bilah.gambar(l, x0, y, x0 + w, isi, warna, tanda >= 0.0F ? new float[] {tanda} : null);
	}

	/** A bond / attitude meter: the kit's bar and the number ("▮▮▮ 58"). Returns the width used. */
	protected int meter(Lukis l, int x0, int y, int nilai, int maks, int warna) {
		int lebar = 34;
		batang(l, x0, y + 2, lebar, maks <= 0 ? 0.0F : nilai / (float) maks, warna, -1.0F);
		Component v = Component.literal(Integer.toString(nilai));
		Brush.text(l.g, font, v, x0 + lebar + 4, y - 1, l.gaya.teks, 1.0F, false);
		return lebar + 6 + font.width(v);
	}

	/** The name of Pangkat ordinal {@code ordinal} in its colour. */
	protected static Component namaPangkat(int ordinal) {
		return Pangkat.byOrdinal(ordinal).nama();
	}

	/** The display name of Realm ordinal {@code ordinal}. */
	protected static Component namaRanah(int ordinal) {
		Realm[] all = Realm.values();
		return all[Math.max(0, Math.min(all.length - 1, ordinal))].displayName();
	}

	/**
	 * A dangerous write button: the press opens the Gulungan's confirm seal (43.7 {@code SegelKonfirmasi}, medium: hold
	 * 1.5 s) and the op is sent once confirmed (G13; was a second press within 3 s).
	 */
	protected boolean tombolYakin(Lukis l, String kunci, int x, int y, int w, int h, Component label, boolean aktif, @Nullable Component alasan,
			String op) {
		return tombolYakin(l, kunci, x, y, w, h, label, aktif, alasan, op, SegelKonfirmasi.Tingkat.SEDANG);
	}

	/** {@link #tombolYakin} with an explicit seal weight ({@code RINGAN}: one press on the seal, no hold). */
	protected boolean tombolYakin(Lukis l, String kunci, int x, int y, int w, int h, Component label, boolean aktif, @Nullable Component alasan,
			String op, SegelKonfirmasi.Tingkat tingkat) {
		return yakin(l, kunci, x, y, w, h, label, aktif, alasan, op, tingkat, Component.translatable("kultivator.gui43.konteks.yakin", label));
	}

	/** "hari 44 (2 hari lagi)" from a world day and today. */
	protected static Component hariKe(long hari, long hariIni) {
		long sisa = hari - hariIni;
		if (hari < 0L) {
			return t("hari.tak_tentu");
		}
		return sisa <= 0L ? t("hari.ini", hari) : sisa == 1L ? t("hari.besok", hari) : t("hari.lagi", hari, sisa);
	}

	/** Today as the client sees it (overworld clock / 24000, same as {@code Kota.day}). */
	protected long hariIni() {
		return minecraft != null && minecraft.level != null ? minecraft.level.getOverworldClockTime() / 24000L : 0L;
	}
}
