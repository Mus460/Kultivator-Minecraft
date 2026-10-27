package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.LayarTempatDasar;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Base of the 20 themed-town screens (DESIGN 38.T.13.3): a {@link LayarTempatDasar} plus what the theme screens share: tabs
 * labelled from this module's fragment, the sheet hooks on the first frame ({@link #lembarBaru}, {@link #tabGanti}), state
 * reasons for the common {@code K_*} card states and a few layout helpers. The speaker strip and the shop block live in
 * {@code TemaHalaman} (single kit); the kit-38 helpers left here ({@link #tengah}, {@link #pita}) serve
 * {@code LayarBukuDagang} until it moves to the single kit.
 * Sheet keys are those of {@code dunia/tempat/tema/bersama/TemaLayar} (C38.T3) and the common keys of {@link LembarKunci}.
 */
public abstract class LayarTemaDasar extends LayarTempatDasar {

	// No initialisers on these: the kit calls setelahLembar / tabBerubah from its constructor, before any subclass field
	// exists, so both only raise flags here and the screen handles them on its first frame (olah()).
	private int versi;
	private int versiDiolah;
	private boolean bukaTunda;
	private boolean tabTunda;

	protected LayarTemaDasar(String layar, int npcId, CompoundTag lembar, String judulKunci, TempatWarna.Gaya gaya) {
		super(layar, npcId, lembar, TemaUi.k(judulKunci), gaya);
	}

	@Override
	protected final void setelahLembar(boolean buka) {
		versi++;
		if (buka) {
			bukaTunda = true;
		}
	}

	@Override
	protected final void tabBerubah() {
		tabTunda = true;
	}

	@Override
	protected final void isi(Lukis l, int x0, int y0, int x1, int y1) {
		olah();
		gambar(l, x0, y0, x1, y1);
	}

	private void olah() {
		if (versi != versiDiolah) {
			versiDiolah = versi;
			boolean buka = bukaTunda;
			bukaTunda = false;
			lembarBaru(buka);
		}
		if (tabTunda) {
			tabTunda = false;
			tabGanti();
		}
	}

	/** A new sheet arrived ({@code buka}: (re)opened); called on the next frame, when every field exists. */
	protected void lembarBaru(boolean buka) {
	}

	/** The tab changed (reset lists here); called on the next frame. */
	protected void tabGanti() {
	}

	/** Draws the content of tab {@link #tab} into the box. */
	protected abstract void gambar(Lukis l, int x0, int y0, int x1, int y1);

	/** A tab labelled from {@code tempat_tema_ui} with a kit icon slot ({@code ikon.png}). */
	protected TabLentera.Tab tabTema(String id, String labelKunci, int ikonKit) {
		return new TabLentera.Tab(id, TemaUi.k(labelKunci), ikonKit, null);
	}

	/** The sheet's speaker card ({@code npc}); empty view when none. */
	protected Lembar npc() {
		return lembar.sub(LembarKunci.NPC);
	}

	/** The reason a card in state {@code e.keadaan()} cannot be used (its own {@code alasan} first). */
	protected @Nullable Component alasanKeadaan(Lembar e) {
		Component alasan = e.teksAtau(LembarKunci.ALASAN);
		if (alasan != null) {
			return alasan;
		}
		return switch (e.keadaan()) {
			case LembarKunci.K_HABIS -> TeksTempat.k("kartu.habis");
			case LembarKunci.K_TERKUNCI -> TemaUi.k("keadaan.terkunci");
			case LembarKunci.K_KURANG_UANG -> TemaUi.k("keadaan.kurang_uang");
			case LembarKunci.K_BATAS -> TemaUi.k("keadaan.batas");
			case LembarKunci.K_TUTUP -> TemaUi.k("keadaan.tutup");
			case LembarKunci.K_DITOLAK -> TemaUi.k("keadaan.ditolak");
			default -> null;
		};
	}

	/** True when {@code harga} can be paid from the money on hand (unknown money counts as payable; the server checks). */
	protected boolean mampu(long harga) {
		long u = uang();
		return u < 0L || harga <= u;
	}

	/** Reason for "not enough money" for {@code harga}. */
	protected Component kurang(long harga) {
		return TeksTempat.k("harga.kurang", Brush.angka(Math.max(0L, harga - Math.max(0L, uang()))));
	}

	/** A centred caption in the box (placeholders: "pilih ..."). */
	protected void tengah(Lukis l, Component t, Kotak k) {
		Brush.fitCentered(l.g, font, t, (k.x0() + k.x1()) / 2.0F, k.y0() + k.h() / 2.0F - 4, k.w() - 8, gaya.teksSamar, 1.0F, 0.6F, false);
	}

	/** A warning band across the box top (storm, jammed lift, night); returns the height used. */
	protected int pita(Lukis l, int ikon, Component teks, int x0, int y, int x1, int warna) {
		l.g.fill(x0, y, x1, y + 13, Palette.argb(0xC8, Palette.darken(warna, 0.45F) & 0xFFFFFF));
		l.g.fill(x0, y, x0 + 2, y + 13, warna);
		float pulse = 0.6F + 0.4F * l.denyut();
		TemaUi.ikon(l.g, ikon, x0 + 4, y + 1, 11, Palette.argb(Math.round(255 * pulse), 0xFFFFFF));
		l.teks(teks, x0 + 18, y + 3, x1 - x0 - 22, 0xFFFFF0E0);
		return 15;
	}

	/** Text colour on the content (accent on dark styles is gold). */
	protected int aksen() {
		return gaya.gelap() ? TempatWarna.EMAS_TERANG : gaya.aksen;
	}

	/** "+N" / "-N" coloured delta (Infami, Gentar, profit). */
	protected Component delta(int n) {
		int naik = gaya.gelap() ? 0xFF9AE0B0 : 0xFF2E7A4E;
		int turun = gaya.gelap() ? 0xFFFF8A7A : TempatWarna.BAHAYA;
		return Component.literal((n > 0 ? "+" : "") + n).withColor(n > 0 ? naik : n < 0 ? turun : gaya.teksSamar);
	}
}
