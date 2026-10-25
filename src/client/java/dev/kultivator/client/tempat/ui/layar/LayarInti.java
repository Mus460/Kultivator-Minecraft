package dev.kultivator.client.tempat.ui.layar;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.Kultivator;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.KeadaanTerkunci;
import dev.kultivator.client.screen.ui.LencanaPangkat;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.tempat.ui.kit.LayarTempatDasar;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * The shared helper of the core 38 sheets (DESIGN 43.6, 43.7; M19): a {@link LayarTempatDasar} whose content is drawn
 * with the single kit {@code screen/ui} and the page's own focus ring, not the old sheet engine. The sheet contract, tabs
 * as subs, read-only views, the session and Di Sini stay in the base; this class only replaces the drawing and the input:
 * <ul>
 * <li>{@link #drawBehind} applies the sheet; {@link #lukis} draws the style's base, the info strip (district, subtitle or
 * place, "only viewing", test ribbon, discount, Nama Baik), the screen lock or the sheet's content ({@link #isiInti});</li>
 * <li>mouse, keys, Tab and digits go to {@code fokusRing} (Space first to {@link #spasi}, then the screen's own keys);</li>
 * <li>small helpers at text size 1.0 (43.0.5): {@link #nilai}, {@link #kosong}, {@link #tombolOp}, {@link #yakin}.</li>
 * </ul>
 * Not a second base: a helper subclass like {@code LayarGabungDasar}. The only kit-38 types left here are the base's own
 * signature ({@link TempatWarna.Gaya}, {@link TabLentera.Tab}); P1 removes them with the base's bridge.
 */
public abstract class LayarInti extends LayarTempatDasar {

	/** Lang prefix of the 38 sheet texts ({@code TeksTempat.K}). */
	public static final String K = "kultivator.tempat_ui.";
	/** Lang prefix of this module's new texts. */
	public static final String M = "kultivator.gui43.m19.";
	/** Height of the info strip. */
	private static final int STRIP = 12;
	/** Standard button height (43.3.2: 14 outside the narrow layout). */
	protected static final int TOMBOL_H = 14;

	/** One tab of the sheet: id, label, atlas icon (its lock comes from the sheet's {@code tab_kunci}). */
	protected record Tab(String id, Component label, int ikon) {
	}

	/** A tab labelled with the 38 sheet text {@code labelKunci} ({@link #k}). */
	protected static Tab tab(String id, String labelKunci, int ikon) {
		return new Tab(id, k(labelKunci), ikon);
	}

	private long terkunciSejak = -1L;

	protected LayarInti(String layar, int npcId, CompoundTag lembar, Component judul, String gayaId) {
		super(layar, npcId, lembar, judul, TempatWarna.Gaya.dari(gayaId, TempatWarna.Gaya.KERTAS));
	}

	/** A 38 sheet text ({@code kultivator.tempat_ui.<kunci>}). */
	public static MutableComponent k(String kunci, Object... args) {
		return Component.translatable(K + kunci, args);
	}

	/** A text of this module ({@code kultivator.gui43.m19.<kunci>}). */
	public static MutableComponent m(String kunci, Object... args) {
		return Component.translatable(M + kunci, args);
	}

	// =================================================================== for the sheets

	/** The sheet's tabs in order. */
	protected abstract List<Tab> daftarTab();

	/** Draws the content of tab {@link #tab} into the box with the single kit. */
	protected abstract void isiInti(Lukis l, int x0, int y0, int x1, int y1);

	/** Illustration of the empty state. */
	protected KeadaanKosong.Gambar kosongGambar() {
		return KeadaanKosong.Gambar.NAMPAN;
	}

	@Override
	protected final List<TabLentera.Tab> tabs() {
		List<Tab> semua = daftarTab();
		List<TabLentera.Tab> out = new ArrayList<>(semua.size());
		for (Tab t : semua) {
			out.add(new TabLentera.Tab(t.id(), t.label(), t.ikon(), null));
		}
		return out;
	}

	/**
	 * True while the sheet shows a mode still drawn by the old sheet engine (the half-built 42 modes of the court, the
	 * recruitment office and the events board call 42-G2 painters made for it; 43.16.6: 42U rebuilds them on the single
	 * kit). Then {@link #isi} draws and the base handles the input; otherwise {@link #isiInti} and the focus ring.
	 */
	protected boolean mesinLama() {
		return false;
	}

	/** The old-engine content of a {@link #mesinLama} mode (unused otherwise). */
	@Override
	protected void isi(dev.kultivator.client.tempat.ui.kit.Lukis l, int x0, int y0, int x1, int y1) {
	}

	// =================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		if (mesinLama()) {
			super.drawBehind(g, mouseX, mouseY, seconds);
			return;
		}
		// applies the first sheet (F2); the content is drawn in lukis() with the page's focus ring
		tabSekarang();
	}

	@Override
	protected final void lukis(Lukis l) {
		if (mesinLama()) {
			return;
		}
		int x0 = x;
		int y0 = y;
		int x1 = x + w;
		int y1 = y + h;
		if (l.gaya != Palette.Gaya.KERTAS) {
			// the sheet's own style (red lacquer, stone ...) under its content: its text colours are made for it
			l.g.fill(x0, y0, x1, y1, l.gaya.dasar);
		}
		if (adaStrip()) {
			strip(l, x0, y0, x1);
			y0 += STRIP + 3;
		}
		if (lembar.ada(LembarKunci.KUNCI_LAYAR)) {
			long now = Util.getMillis();
			if (terkunciSejak < 0L) {
				terkunciSejak = now;
			}
			Lembar k = lembar.sub(LembarKunci.KUNCI_LAYAR);
			KeadaanTerkunci.gambar(l, k.teks(LembarKunci.ALASAN), k.teksAtau(LembarKunci.SYARAT), x0, y0, x1, y1, now - terkunciSejak);
			return;
		}
		terkunciSejak = -1L;
		try {
			isiInti(l, x0 + 2, y0 + 2, x1 - 2, y1 - 2);
		} catch (RuntimeException ex) {
			Kultivator.LOGGER.warn("Tempat page {} failed to draw", layar, ex);
			l.teks(k("galat"), x0 + 4, y0 + 4, x1 - x0 - 8, Palette.BAHAYA);
		}
	}

	private boolean adaStrip() {
		return !lembar.s(LembarKunci.DISTRIK).isEmpty() || lembar.ada(LembarKunci.SUBJUDUL) || lembar.ada(LembarKunci.TEMPAT)
				|| lembar.ada(LembarKunci.NAMA_BAIK) || lembar.f(LembarKunci.DISKON, 0.0F) > 0.001F || lembar.b(UJI) || hanyaBaca();
	}

	/**
	 * The strip over the content (what the old header carried besides title and money): district, subtitle or place,
	 * then from the right "only viewing", the test ribbon, the discount and the place's Nama Baik.
	 */
	private void strip(Lukis l, int x0, int y, int x1) {
		int x = x0 + 2;
		int kanan = x1 - 2;
		String distrik = lembar.s(LembarKunci.DISTRIK);
		if (!distrik.isEmpty()) {
			l.g.fill(x - 1, y, x + 12, y + STRIP, Palette.argb(0xC0, warnaDistrik(distrik) & 0xFFFFFF));
			Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.distrik(distrik), x, y + 1, 10, 0xFFFFFFFF);
			x += 15;
		}
		if (hanyaBaca()) {
			Component b = k("hanya_baca.kaki");
			int bw = Math.min(kanan - x - 20, l.font.width(b) + 6);
			if (bw > 20) {
				l.g.fill(kanan - bw, y, kanan, y + STRIP, Palette.argb(0x60, Palette.INK_FAINT & 0xFFFFFF));
				l.teks(b, kanan - bw + 3, y + 2, bw - 6, l.gaya.teks);
				kanan -= bw + 4;
			}
		}
		if (lembar.b(UJI) && kanan - x > 60) {
			Glyphs.Atlas.pita(l.g, true, kanan - 34, y, 32, STRIP, 0xFFFFFFFF);
			l.tengah(k("uji.pita"), kanan - 18, y + 2, 30, 0xFF5A3A08);
			kanan -= 38;
		}
		float diskon = lembar.f(LembarKunci.DISKON, 0.0F);
		if (diskon > 0.001F) {
			Component d = k("diskon", Math.round(diskon * 100));
			int dw = l.font.width(d) + 6;
			if (kanan - dw - x > 40) {
				l.g.fill(kanan - dw, y, kanan, y + STRIP, Palette.SEAL);
				l.teks(d, kanan - dw + 3, y + 2, dw - 4, 0xFFFFF0E0);
				kanan -= dw + 4;
			}
		}
		if (lembar.ada(LembarKunci.NAMA_BAIK) && kanan - x > 80) {
			kanan -= LencanaPangkat.namaBaik(l, lembar.i(LembarKunci.NAMA_BAIK, 0), lembar.i(LembarKunci.PANGKAT_NB, LembarKunci.PANGKAT_ASING), kanan,
					y + 2) + 4;
		}
		Component sub = lembar.teksAtau(LembarKunci.SUBJUDUL);
		Component baris = sub != null ? sub : lembar.teksAtau(LembarKunci.TEMPAT);
		if (baris != null && kanan - x > 12) {
			l.teks(baris, x, y + 2, kanan - x - 2, l.gaya.teksLemah);
		}
		Component hint = petunjuk();
		if (hint != null && l.di(x0, y, x1, y + STRIP)) {
			l.tip(hint);
		}
		l.g.fill(x0, y + STRIP + 1, x1, y + STRIP + 2, Palette.argb(0x70, (l.gaya.gelap() ? Palette.GOLD : l.gaya.teksSamar) & 0xFFFFFF));
	}

	/** District colour of a {@code DISTRIK_*} id (the palace gold when unknown is the centre's jade). */
	protected static int warnaDistrik(String id) {
		return switch (id) {
			case LembarKunci.DISTRIK_ISTANA -> 0xFFC9A24A;
			case LembarKunci.DISTRIK_CENDEKIA -> 0xFF2A3A5C;
			case LembarKunci.DISTRIK_AWAN -> 0xFF6FA8C8;
			case LembarKunci.DISTRIK_GILDA -> 0xFF8C6A3A;
			case LembarKunci.DISTRIK_PASAR -> 0xFFD08A2A;
			case LembarKunci.DISTRIK_HUNIAN -> 0xFF7A5A3A;
			case LembarKunci.DISTRIK_ARENA -> 0xFF7A1A1A;
			case LembarKunci.DISTRIK_KUMUH -> 0xFF6A6A60;
			case LembarKunci.DISTRIK_GERBANG -> 0xFF8E1F1F;
			case LembarKunci.DISTRIK_LUAR -> 0xFF4F8A3A;
			default -> 0xFF5FA88C;
		};
	}

	// =================================================================== helpers (text 1.0)

	/** A label / value row (value right-aligned); returns the height used (11). */
	protected int nilai(Lukis l, Component label, Component nilai, int x0, int y, int x1) {
		int vw = l.font.width(nilai);
		l.teks(label, x0, y, Math.max(8, x1 - x0 - vw - 4), l.gaya.teksLemah);
		l.kanan(nilai, x1, y, l.gaya.teks);
		return 11;
	}

	/** Empty state with the sheet's {@code kosong} compound (or {@code cadangan}). */
	protected void kosong(Lukis l, @Nullable Component cadangan, int x0, int y0, int x1, int y1) {
		KeadaanKosong.gambar(l, kosongGambar(), lembar.sub(LembarKunci.KOSONG), cadangan, x0, y0, x1, y1, this::kirim);
	}

	/** A talisman that sends {@code op} when allowed (also checks read-only); true when hovered. */
	protected boolean tombolOp(Lukis l, String kunci, int x, int y, int w, int h, Component label, boolean aktif, @Nullable Component alasan,
			String op) {
		boolean baca = !tulisBoleh() && !LembarKunci.opBaca(LembarKunci.verb(op));
		return TalismanButton.gambar(l, kunci, x, y, w, h, label, aktif && !baca, baca ? alasanBaca() : alasan, () -> kirim(op));
	}

	/**
	 * A talisman whose op is sent after the Gulungan's confirm seal (43.7 {@code SegelKonfirmasi}; G13, was a second press
	 * within 3 s): {@code tingkat} light = click Ya, medium = hold 1.5 s, heavy = tick + hold.
	 */
	protected boolean yakin(Lukis l, String kunci, int x, int y, int w, int h, Component label, boolean aktif, @Nullable Component alasan,
			String op, SegelKonfirmasi.Tingkat tingkat, Component teks) {
		boolean baca = !tulisBoleh() && !LembarKunci.opBaca(LembarKunci.verb(op));
		return TalismanButton.gambar(l, kunci, x, y, w, h, label, aktif && !baca, baca ? alasanBaca() : alasan,
				() -> konfirmasi(tingkat, teks, () -> kirim(op)));
	}

	/** Colour of a {@code PESAN_*} status. */
	protected static int status(String jenis) {
		return Palette.status(jenis);
	}

	/** An item grade's name colour on this paper. */
	protected static int mutu(Lukis l, int mutu) {
		return Palette.mutu(mutu, l.gaya.gelap(), l.gaya.teks);
	}

	/** Rounded "12.345" as a component. */
	protected static Component angka(long n) {
		return Component.literal(Brush.angka(n));
	}

	// =================================================================== input (the page's focus ring)

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (mesinLama()) {
			return super.mouseClicked(event, doubleClick);
		}
		return fokusRing.klik(event.x(), event.y(), event.button());
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (mesinLama()) {
			return super.mouseReleased(event);
		}
		return fokusRing.lepas(event.x(), event.y());
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (mesinLama()) {
			return super.mouseDragged(event, dx, dy);
		}
		return fokusRing.seret(event.x(), event.y(), dx);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mesinLama()) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}
		return fokusRing.gulir(mouseX, mouseY, scrollY);
	}

	/** Space first to the screen's quick action (auction bid, dialog page), then the focus ring and the screen's keys. */
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (mesinLama()) {
			return super.keyPressed(event);
		}
		if (modal() || fokusRing.mauTombol()) {
			return fokusRing.tombol(event);
		}
		if (event.key() == InputConstants.KEY_SPACE && spasi()) {
			return true;
		}
		return fokusRing.tombol(event) || tombolLain(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (mesinLama()) {
			return super.charTyped(event);
		}
		return fokusRing.ketik(event);
	}

	@Override
	public boolean fokusTab(boolean back) {
		if (mesinLama()) {
			return super.fokusTab(back);
		}
		fokusRing.geser(back ? -1 : 1);
		return true;
	}

	/** Under Di Sini the digits pick the numbered choices drawn this frame (43.17 K3). */
	@Override
	public boolean angka(int n) {
		return !mesinLama() && fokusRing.angka(n);
	}
}
