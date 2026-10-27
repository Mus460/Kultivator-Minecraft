package dev.kultivator.client.tempat.ui.layar;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.Kultivator;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.TabBar;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.tempat.ui.LayarTempatDasar;
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
 * Every sheet of {@code tempat/ui/layar} (also the ledger, the journal and the city map) is a subclass of this one. The
 * old kit-38 engine is gone: the base keeps only the sheet model.
 */
public abstract class LayarInti extends LayarTempatDasar {

	/** Lang prefix of the 38 sheet texts ({@code TeksTempat.K}). */
	public static final String K = "kultivator.tempat_ui.";
	/** Lang prefix of this module's new texts. */
	public static final String M = "kultivator.gui43.m19.";
	/** Standard button height (43.3.2: 14 outside the narrow layout). */
	protected static final int TOMBOL_H = 14;

	/** One tab of the sheet: id, label, atlas icon (its lock comes from the sheet's {@code tab_kunci}). */
	protected record Tab(String id, Component label, int ikon) {
	}

	/** A tab labelled with the 38 sheet text {@code labelKunci} ({@link #k}). */
	protected static Tab tab(String id, String labelKunci, int ikon) {
		return new Tab(id, k(labelKunci), ikon);
	}

	/** A tab with a ready-made label (sheets whose texts live outside {@code kultivator.tempat_ui.*}). */
	protected static Tab tab(String id, Component label, int ikon) {
		return new Tab(id, label, ikon);
	}

	protected LayarInti(String layar, int npcId, CompoundTag lembar, Component judul, String gayaId) {
		super(layar, npcId, lembar, judul, Palette.Gaya.dari(gayaId, Palette.Gaya.KERTAS));
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
	protected final List<TabBar.Tab> tabs() {
		List<Tab> semua = daftarTab();
		List<TabBar.Tab> out = new ArrayList<>(semua.size());
		for (Tab t : semua) {
			out.add(TabBar.Tab.of(t.id(), t.label()));
		}
		return out;
	}

	// =================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		// applies the first sheet (F2); the content is drawn in lukis() with the page's focus ring
		tabSekarang();
	}

	@Override
	protected final void lukis(Lukis l) {
		int x0 = x;
		int y0 = y;
		int x1 = x + w;
		int y1 = y + h;
		if (l.gaya != Palette.Gaya.KERTAS) {
			// the sheet's own style (red lacquer, stone ...) under its content: its text colours are made for it
			l.g.fill(x0, y0, x1, y1, l.gaya.dasar);
		}
		y0 += stripInfo(l, x0, y0, x1);
		if (kunciLayar(l, x0, y0, x1, y1)) {
			return;
		}
		try {
			isiInti(l, x0 + 2, y0 + 2, x1 - 2, y1 - 2);
		} catch (RuntimeException ex) {
			Kultivator.LOGGER.warn("Tempat page {} failed to draw", layar, ex);
			l.teks(k("galat"), x0 + 4, y0 + 4, x1 - x0 - 8, Palette.BAHAYA);
		}
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
		return fokusRing.klik(event.x(), event.y(), event.button());
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return fokusRing.lepas(event.x(), event.y());
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return fokusRing.seret(event.x(), event.y(), dx);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return fokusRing.gulir(mouseX, mouseY, scrollY);
	}

	/** Space first to the screen's quick action (auction bid, dialog page), then the focus ring and the screen's keys. */
	@Override
	public boolean keyPressed(KeyEvent event) {
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
		return fokusRing.ketik(event);
	}

	@Override
	public boolean fokusTab(boolean back) {
		fokusRing.geser(back ? -1 : 1);
		return true;
	}

	/** Under Di Sini the digits pick the numbered choices drawn this frame (43.17 K3). */
	@Override
	public boolean angka(int n) {
		return fokusRing.angka(n);
	}
}
