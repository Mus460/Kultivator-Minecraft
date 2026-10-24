package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.DaftarGulir;
import dev.kultivator.client.tempat.ui.kit.GelembungDialog;
import dev.kultivator.client.tempat.ui.kit.KartuBarang;
import dev.kultivator.client.tempat.ui.kit.LabelHarga;
import dev.kultivator.client.tempat.ui.kit.LayarTempatDasar;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.PemilihJumlah;
import dev.kultivator.client.tempat.ui.kit.Pratinjau3D;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * Base of the 20 themed-town screens (DESIGN 38.T.13.3): a {@link LayarTempatDasar} (the 38UI frame, read-only for 38TV-U)
 * plus what the theme screens share: tabs labelled from this module's fragment, the speaker strip (live portrait, name,
 * the trader's last line written by brush), state reasons for the common {@code K_*} card states and a few layout helpers.
 * Sheet keys are those of {@code dunia/tempat/tema/bersama/TemaLayar} (C38.T3) and the common keys of {@link LembarKunci}.
 */
public abstract class LayarTemaDasar extends LayarTempatDasar {

	protected final GelembungDialog gelembung = new GelembungDialog();
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

	/**
	 * The speaker strip: portrait, name · title and {@code ucapan} written out by brush, inside a card from x0 to x1.
	 * Returns the height used (0 when the sheet has no speaker).
	 */
	protected int strip(Lukis l, @Nullable Component ucapan, int x0, int y0, int x1, int tinggi) {
		Lembar n = npc();
		if (!n.ada(LembarKunci.NAMA)) {
			return 0;
		}
		PanelTempat.kartu(l.g, gaya, x0, y0, x1, y0 + tinggi, 0.0F, false);
		gelembung.gambar(l, n, ucapan == null ? Component.empty() : ucapan, x0 + 3, y0 + 3, x1 - 3, y0 + tinggi - 3);
		return tinggi + 4;
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
		return TeksTempat.k("harga.kurang", TeksTempat.angka(Math.max(0L, harga - Math.max(0L, uang()))));
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
		l.teks(teks, x0 + 18, y + 3, x1 - x0 - 22, 0xFFFFF0E0, 0.85F);
		return 15;
	}

	/** Text colour on the content (accent on dark styles is gold). */
	protected int aksen() {
		return gaya.gelap() ? TempatWarna.EMAS_TERANG : gaya.aksen;
	}

	// ------------------------------------------------------------------------------------------------ shop block

	/** State of one shop list of a themed screen (list, turning preview, amount). */
	protected static final class Toko {
		public final DaftarGulir daftar = new DaftarGulir();
		public final Pratinjau3D pratinjau = new Pratinjau3D();
		public final PemilihJumlah jumlah = new PemilihJumlah();

		public void mulaiUlang() {
			daftar.mulaiUlang(Util.getMillis());
			jumlah.set(1);
		}
	}

	/** Extra lines of a shop entry's detail (tea effect, herb age, dark origin ...); returns the height used. */
	@FunctionalInterface
	protected interface DetailEkstra {
		int gambar(Lukis l, Lembar e, int x0, int y, int x1);
	}

	/**
	 * The common shop block of the themed screens: the goods list ({@link KartuBarang}) in {@code daftarK} and the detail
	 * in {@code detailK} (turning preview, name in grade colour, note, stock, price with the sheet's discount, the
	 * {@code ekstra} lines, an amount picker when {@code jumlah} and the buy button sending {@code opBeli:ID[:N]}).
	 * {@code tutup} (non-null) disables buying with that reason (storm, refused, night).
	 */
	protected void toko(Lukis l, String kunci, Toko t, List<Lembar> barang, Kotak daftarK, Kotak detailK, String opBeli, boolean jumlah,
			@Nullable DetailEkstra ekstra, @Nullable Component tutup, Component kosongTeks) {
		if (barang.isEmpty()) {
			kosong(l, kosongTeks, daftarK.x0(), daftarK.y0(), daftarK.x1(), daftarK.y1());
		} else {
			long u = uang();
			long muncul = diterima;
			t.daftar.gambar(l, kunci, daftarK.x0(), daftarK.y0(), daftarK.x1(), daftarK.y1(), barang.size(), 22,
					(lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> KartuBarang.gambar(lk, barang.get(i), rx0, ry0, rx1, ry1, sorot, pilih, u, muncul),
					() -> {
						int i = t.daftar.pilihan();
						if (tutup == null && i >= 0 && i < barang.size() && barang.get(i).bisa() && tulisBoleh()) {
							Lembar e = barang.get(i);
							kirim(jumlah ? LembarKunci.op(opBeli, e.id(), t.jumlah.nilai()) : LembarKunci.op(opBeli, e.id()));
						}
					}, i -> t.jumlah.set(1));
		}
		int sel = t.daftar.pilihan();
		Lembar e = sel >= 0 && sel < barang.size() ? barang.get(sel) : null;
		PanelTempat.kartu(l.g, gaya, detailK.x0(), detailK.y0(), detailK.x1(), detailK.y1(), 0.0F, false);
		if (e == null) {
			tengah(l, TemaUi.k("toko.pilih"), detailK);
			return;
		}
		int x0 = detailK.x0() + 5;
		int x1 = detailK.x1() - 5;
		int y = detailK.y0() + 4;
		int pv = tata == Tata.SEMPIT ? 28 : tata == Tata.LEBAR ? 76 : 52;
		int slot = TemaUi.ikonBarang(e.id());
		t.pratinjau.gambar(l, kunci + "_3d", x0, y, x0 + pv, y + pv, e.barang(), null, slot < 0 ? e.i(LembarKunci.IKON, LembarKunci.Ikon.HERBA) : -1);
		if (e.barang().isEmpty() && slot >= 0) {
			TemaUi.ikon(l.g, slot, x0 + pv / 2 - 12, y + pv / 2 - 12, 24);
		}
		int tx = x0 + pv + 6;
		l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), tx, y, x1 - tx, TempatWarna.mutu(e.i(LembarKunci.MUTU, -1), gaya.gelap(), gaya.teks));
		int ty = y + 11;
		Component catatan = e.teksAtau(LembarKunci.CATATAN);
		if (catatan != null) {
			ty += Math.round(l.bungkus(catatan, tx, ty, x1 - tx, gaya.teksLemah, 0.7F)) + 2;
		}
		int stok = e.i(LembarKunci.STOK, -1);
		l.keterangan(stok < 0 ? TeksTempat.k("kartu.stok_tak_terbatas") : TeksTempat.k("kartu.stok", stok), tx, ty, x1 - tx, gaya.teksSamar);
		ty += 10;
		long harga = e.l(LembarKunci.HARGA, 0L);
		LabelHarga.gambar(l, harga, uang(), tx, ty, false, 1.0F, false);
		float diskon = lembar.f(LembarKunci.DISKON, 0.0F);
		if (diskon > 0.001F) {
			TemaUi.chip(l, -1, TemaUi.k("toko.diskon", Math.round(diskon * 100.0F)), x1 - 46, ty, TempatWarna.OK);
		}
		ty += 12;
		int ey = Math.max(ty, y + pv + 4);
		if (ekstra != null) {
			ekstra.gambar(l, e, x0, ey, x1);
		}
		int by = detailK.y1() - 19;
		int n = jumlah ? t.jumlah.nilai() : 1;
		int pw = 0;
		if (jumlah) {
			int maks = stok < 0 ? 64 : Math.max(1, stok);
			pw = t.jumlah.gambar(l, kunci + "_n", x1 - 50, by + 1, maks) + 6;
		}
		long total = harga * n;
		boolean bisa = tutup == null && e.bisa() && mampu(total);
		Component alasan = tutup != null ? tutup : !e.bisa() ? alasanKeadaan(e) : !mampu(total) ? kurang(total) : null;
		tombolOp(l, kunci + "_beli", x0, by, x1 - x0 - pw, 15, TemaUi.k("toko.beli", TeksTempat.angka(total)), bisa, alasan,
				jumlah ? LembarKunci.op(opBeli, e.id(), n) : LembarKunci.op(opBeli, e.id()));
		if (!e.bisa() && alasan != null && tutup == null) {
			l.keterangan(alasan, x0, by - 10, x1 - x0, gaya.gelap() ? 0xFFFF9A8A : TempatWarna.BAHAYA);
		}
	}

	/** "+N" / "-N" coloured delta (Infami, Gentar, profit). */
	protected Component delta(int n) {
		int naik = gaya.gelap() ? 0xFF9AE0B0 : 0xFF2E7A4E;
		int turun = gaya.gelap() ? 0xFFFF8A7A : TempatWarna.BAHAYA;
		return Component.literal((n > 0 ? "+" : "") + n).withColor(n > 0 ? naik : n < 0 ? turun : gaya.teksSamar);
	}
}
