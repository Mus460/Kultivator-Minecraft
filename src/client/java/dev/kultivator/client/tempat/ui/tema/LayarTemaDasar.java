package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TabBar;
import dev.kultivator.client.tempat.ui.LayarTempatDasar;
import dev.kultivator.client.screen.ui.Lembar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/**
 * Base of the 20 themed-town screens (DESIGN 38.T.13.3): a {@link LayarTempatDasar} (the sheet model: server sheet, tabs,
 * ops, strip, lock) plus what the theme screens share: tabs labelled from this module's fragment ({@link TabBar.Tab}), the
 * sheet hooks on the first frame ({@link #lembarBaru}, {@link #tabGanti}), state reasons for the common {@code K_*} card
 * states and a few text helpers. It draws nothing: the speaker strip, the shop block and every drawing helper live in
 * {@code TemaHalaman} (single kit {@code screen/ui}).
 * Sheet keys are those of {@code dunia/tempat/tema/bersama/TemaLayar} (C38.T3) and the common keys of {@link LembarKunci}.
 */
public abstract class LayarTemaDasar extends LayarTempatDasar {

	// No initialisers on these: the kit calls setelahLembar / tabBerubah from its constructor, before any subclass field
	// exists, so both only raise flags here and the screen handles them on its first frame (olah()).
	private int versi;
	private int versiDiolah;
	private boolean bukaTunda;
	private boolean tabTunda;

	protected LayarTemaDasar(String layar, int npcId, CompoundTag lembar, String judulKunci, Palette.Gaya gaya) {
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

	/** Applies the first sheet and runs the pending hooks; {@code TemaHalaman} calls it at the start of every frame. */
	protected final void olah() {
		tabSekarang();
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

	/** Text of the kit-38 sheet fragment ({@code kultivator.tempat_ui.*}). */
	protected static MutableComponent tt(String kunci, Object... args) {
		return Component.translatable("kultivator.tempat_ui." + kunci, args);
	}

	/** A tab labelled from {@code tempat_tema_ui}. */
	protected TabBar.Tab tabTema(String id, String labelKunci) {
		return TabBar.Tab.of(id, TemaUi.k(labelKunci));
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
			case LembarKunci.K_HABIS -> tt("kartu.habis");
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
		return tt("harga.kurang", Brush.angka(Math.max(0L, harga - Math.max(0L, uang()))));
	}

	/** Text colour on the content (accent on dark styles is gold). */
	protected int aksen() {
		return gaya.gelap() ? Palette.GOLD_LIGHT : gaya.aksen;
	}

	/** "+N" / "-N" coloured delta (Infami, Gentar, profit). */
	protected Component delta(int n) {
		int naik = gaya.gelap() ? 0xFF9AE0B0 : 0xFF2E7A4E;
		int turun = gaya.gelap() ? 0xFFFF8A7A : Palette.BAHAYA;
		return Component.literal((n > 0 ? "+" : "") + n).withColor(n > 0 ? naik : n < 0 ? turun : gaya.teksSamar);
	}
}
