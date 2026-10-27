package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.Kultivator;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.KartuBarang;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.PemilihJumlah;
import dev.kultivator.client.screen.ui.Potret;
import dev.kultivator.client.screen.ui.Pratinjau3D;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.Lembar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * The twenty themed sheets on the single kit (DESIGN 43.7, 43.8.3): a {@link LayarTemaDasar} (the sheet, tabs as subs, the
 * hooks {@link #lembarBaru}/{@link #tabGanti}) that draws with {@code screen/ui} and the page's own focus ring instead of
 * the old sheet engine, so a themed page under Di Sini behaves like every other page of the Gulungan (Tab ring, Enter,
 * number keys for numbered choices, the one confirm seal). Text is 1.0 and 1.5 only (43.0.5).
 * <p>
 * The helpers keep the names the sheets used ({@link #tombolOp}, {@link #kosong}, {@link #toko}, {@link #strip} ...) and
 * take the single kit's {@link Lukis}. A sheet draws its content in {@link #lukisTema}; the strip over it (district,
 * subtitle, Nama Baik, discount, "only viewing") and the locked state are drawn here.
 */
public abstract class TemaHalaman extends LayarTemaDasar {

	private static final float HURUF_PER_DETIK = 60.0F;

	/** The shop whose detail replaces its list this frame (narrow layout, 43.3.1); Esc / the header's back closes it. */
	private @Nullable Toko tokoRinci;
	// the speaker line written out like a brush (the old speech bubble, at 1.0)
	private String ucapanTerakhir = "";
	private long ucapanMulai;

	protected TemaHalaman(String layar, int npcId, CompoundTag lembar, String judulKunci, Palette.Gaya gaya) {
		super(layar, npcId, lembar, judulKunci, gaya);
	}

	/** Draws the content of tab {@link #tab} into the box with the single kit. */
	protected abstract void lukisTema(Lukis l, int x0, int y0, int x1, int y1);

	/** Illustration of the empty state ({@link #kosong}). */
	protected KeadaanKosong.Gambar kosongGambar() {
		return KeadaanKosong.Gambar.NAMPAN;
	}

	// ================================================================== frame

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		// applies the first sheet and runs the hooks (lembarBaru / tabGanti); the content is drawn in lukis()
		olah();
	}

	@Override
	protected final void lukis(Lukis l) {
		tokoRinci = null;
		int x0 = x;
		int y0 = y;
		int x1 = x + w;
		int y1 = y + h;
		if (gaya != Palette.Gaya.KERTAS) {
			// the sheet's own style (night market, red lacquer ...) under its content: its text colours are made for it
			l.g.fill(x0, y0, x1, y1, gaya.dasar);
		}
		y0 += stripInfo(l, x0, y0, x1);
		if (kunciLayar(l, x0, y0, x1, y1)) {
			return;
		}
		try {
			lukisTema(l, x0 + 2, y0, x1 - 2, y1 - 2);
		} catch (RuntimeException ex) {
			Kultivator.LOGGER.warn("Themed page {} failed to draw", layar, ex);
			l.teks(tt("galat"), x0 + 4, y0 + 4, x1 - x0 - 8, Palette.BAHAYA);
		}
	}

	// ================================================================== input (the page's focus ring)

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

	/** The confirm seal first, then the sheet's quick action (Space) and own keys, then the focus ring. */
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (modal() || fokusRing.mauTombol()) {
			return fokusRing.tombol(event);
		}
		if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_SPACE && spasi()) {
			return true;
		}
		return tombolLain(event) || fokusRing.tombol(event);
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

	@Override
	public boolean mauTombol() {
		return fokusRing.mauTombol();
	}

	@Override
	public boolean rinci() {
		return tokoRinci != null && tokoRinci.rinci || super.rinci();
	}

	@Override
	public boolean mundur() {
		if (tokoRinci != null && tokoRinci.rinci) {
			tokoRinci.rinci = false;
			return true;
		}
		return super.mundur();
	}

	/** K3 (43.17): under Di Sini the number keys pick the numbered choices drawn this frame ({@code DaftarPilihan}). */
	@Override
	public boolean angka(int n) {
		return fokusRing.angka(n);
	}

	// ================================================================== helpers (single kit)

	/** A talisman that sends {@code op} when allowed (also checks read-only). */
	protected boolean tombolOp(Lukis l, String kunci, int x, int y, int w, int h, Component label, boolean aktif, @Nullable Component alasan,
			String op) {
		boolean baca = !tulisBoleh() && !LembarKunci.opBaca(LembarKunci.verb(op));
		return TalismanButton.gambar(l, kunci, x, y, w, Math.max(14, h), label, aktif && !baca, baca ? alasanBaca() : alasan, () -> kirim(op));
	}

	/** A plain talisman running {@code aksi} (local choices, no op). */
	protected boolean tombol(Lukis l, String kunci, int x, int y, int w, int h, Component label, boolean aktif, @Nullable Component alasan,
			@Nullable Runnable aksi) {
		return TalismanButton.gambar(l, kunci, x, y, w, Math.max(14, h), label, aktif, alasan, aksi);
	}

	/** Empty state with the sheet's {@code kosong} compound (or a fallback sentence). */
	protected void kosong(Lukis l, @Nullable Component cadangan, int x0, int y0, int x1, int y1) {
		KeadaanKosong.gambar(l, kosongGambar(), lembar.sub(LembarKunci.KOSONG), cadangan, x0, y0, x1, y1, this::kirim);
	}

	/** A stacked heading (43.0.4). Returns the height used (13). */
	protected int subjudul(Lukis l, Component t, int x0, int y, int x1) {
		return l.kepala(t, x0, y, x1);
	}

	/** A label / value row (value right-aligned, 1.0). Returns the height used (10). */
	protected int barisNilai(Lukis l, Component label, Component nilai, int x0, int y, int x1) {
		int vw = font.width(nilai);
		l.teks(label, x0, y, x1 - x0 - vw - 4, gaya.teksLemah);
		l.kanan(nilai, x1, y, gaya.teks);
		return 10;
	}

	protected void sumur(Lukis l, Kotak k) {
		l.sumur(k.x0(), k.y0(), k.x1(), k.y1());
	}

	/** A centred caption in the box (placeholders: "pilih ..."). */
	protected void tengah(Lukis l, Component t, Kotak k) {
		l.tengah(t, (k.x0() + k.x1()) / 2.0F, k.y0() + k.h() / 2.0F - 4, k.w() - 8, gaya.teksSamar);
	}

	/** A warning band across the box top (storm, jammed lift, night); returns the height used. */
	protected int pita(Lukis l, int ikon, Component teks, int x0, int y, int x1, int warna) {
		l.g.fill(x0, y, x1, y + 13, Palette.argb(0xC8, Palette.darken(warna, 0.45F)));
		l.g.fill(x0, y, x0 + 2, y + 13, warna);
		float pulse = 0.6F + 0.4F * l.denyut();
		TemaUi.ikon(l.g, ikon, x0 + 4, y + 1, 11, Palette.argb(Math.round(255 * pulse), 0xFFFFFF));
		l.teks(teks, x0 + 18, y + 2, x1 - x0 - 22, 0xFFFFF0E0);
		return 15;
	}

	/**
	 * The speaker strip: portrait, name · title and {@code ucapan} written out like a brush (1.0, cut with "…" and the
	 * whole line as tooltip when it does not fit), inside a card from x0 to x1. Returns the height used (0 without speaker).
	 */
	protected int strip(Lukis l, @Nullable Component ucapan, int x0, int y0, int x1, int tinggi) {
		Lembar n = npc();
		if (!n.ada(LembarKunci.NAMA)) {
			return 0;
		}
		int y1 = y0 + tinggi;
		l.kartu(x0, y0, x1, y1, 0.0F, false);
		Component teks = ucapan == null ? Component.empty() : ucapan;
		String polos = teks.getString();
		if (!polos.equals(ucapanTerakhir)) {
			ucapanTerakhir = polos;
			ucapanMulai = l.kini;
		}
		int tampil = Math.round((l.kini - ucapanMulai) / 1000.0F * HURUF_PER_DETIK);
		boolean menulis = tampil < polos.length();
		int potret = Potret.ukuran(Math.min(56, tinggi - 6));
		Potret.gambar(l, x0 + 3, y0 + 3, potret, n.i(LembarKunci.POTRET, -1), n.i(LembarKunci.IKON, -1), n.b(LembarKunci.TAKUT), menulis ? 1.0F : 0.15F);
		int tx = x0 + potret + 9;
		Component nama = n.teks(LembarKunci.NAMA);
		Component gelar = n.teksAtau(LembarKunci.GELAR);
		Component kepala = gelar == null ? Brush.bold(nama)
				: Brush.bold(nama).copy().append(Component.literal(" · ").withColor(gaya.teksSamar)).append(gelar.copy().withColor(gaya.teksLemah));
		l.teks(kepala, tx, y0 + 3, x1 - tx - 3, aksen());
		if (!polos.isEmpty() && y1 - (y0 + 14) >= 9) {
			Component tulis = menulis ? Component.literal(polos.substring(0, Math.max(0, tampil))).withStyle(teks.getStyle()) : teks;
			l.bungkus(tulis, tx, y0 + 14, x1 - tx - 4, gaya.teksLemah, y1 - (y0 + 14) - 2);
			if (menulis && l.di(x0, y0, x1, y1)) {
				l.tip(teks);
			}
		}
		l.zona("strip", x0, y0, x1, y1, () -> ucapanMulai = 0L, true, null, false);
		return tinggi + 4;
	}

	// ================================================================== shop block

	/** State of one shop list of a themed page (list, turning preview, amount). */
	protected static final class Toko {
		public final DaftarGulir daftar = new DaftarGulir();
		public final Pratinjau3D pratinjau = new Pratinjau3D();
		public final PemilihJumlah jumlah = new PemilihJumlah();
		/** Narrow layout: the detail is shown in place of the list. */
		public boolean rinci;

		public void mulaiUlang() {
			daftar.mulaiUlang(Util.getMillis());
			jumlah.set(1);
			rinci = false;
		}
	}

	/** Extra lines of a shop entry's detail (tea effect, herb age, dark origin ...); returns the height used. */
	@FunctionalInterface
	protected interface DetailEkstra {
		int gambar(Lukis l, Lembar e, int x0, int y, int x1);
	}

	/**
	 * The one shop composition of the themed pages (G19: {@link KartuBarang} list + detail with {@link LabelHarga} and
	 * {@link PemilihJumlah}): the goods list in {@code daftarK} and the detail in {@code detailK} (turning preview, name in
	 * grade colour, note, stock, price with the sheet's discount, the {@code ekstra} lines, the amount picker when
	 * {@code jumlah}, the buy talisman sending {@code opBeli:ID[:N]}). {@code tutup} (non-null) disables buying with that
	 * reason (storm, refused, night). In the narrow layout the list takes the whole area and Enter / a second click shows
	 * the detail in its place (43.3.1; the header's back control and Esc return to the list).
	 */
	protected void toko(Lukis l, String kunci, Toko t, List<Lembar> barang, Kotak daftarK, Kotak detailK, String opBeli, boolean jumlah,
			@Nullable DetailEkstra ekstra, @Nullable Component tutup, Component kosongTeks) {
		boolean sempit = tata == Tata.SEMPIT;
		if (sempit) {
			Kotak semua = new Kotak(daftarK.x0(), Math.min(daftarK.y0(), detailK.y0()), daftarK.x1(), Math.max(daftarK.y1(), detailK.y1()));
			int pilih = t.daftar.pilihan();
			if (t.rinci && pilih >= 0 && pilih < barang.size()) {
				tokoRinci = t;
				daftarK = semua;
				detailK = semua;
			} else {
				t.rinci = false;
				daftarK = semua;
				detailK = null;
			}
		}
		if (detailK == daftarK) {
			// narrow detail: no list this frame
		} else if (barang.isEmpty()) {
			kosong(l, kosongTeks, daftarK.x0(), daftarK.y0(), daftarK.x1(), daftarK.y1());
		} else {
			long u = uang();
			long muncul = diterima;
			t.daftar.gambar(l, kunci, daftarK.x0(), daftarK.y0(), daftarK.x1(), daftarK.y1(), barang.size(), 24,
					(lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> KartuBarang.gambar(lk, barang.get(i), rx0, ry0, rx1, ry1, sorot, pilih, u, muncul),
					() -> {
						int i = t.daftar.pilihan();
						if (sempit) {
							t.rinci = i >= 0 && i < barang.size();
						} else if (tutup == null && i >= 0 && i < barang.size() && barang.get(i).bisa() && tulisBoleh()) {
							Lembar e = barang.get(i);
							kirim(jumlah ? LembarKunci.op(opBeli, e.id(), t.jumlah.nilai()) : LembarKunci.op(opBeli, e.id()));
						}
					}, i -> t.jumlah.set(1));
		}
		if (detailK == null) {
			return;
		}
		int sel = t.daftar.pilihan();
		Lembar e = sel >= 0 && sel < barang.size() ? barang.get(sel) : null;
		l.kartu(detailK.x0(), detailK.y0(), detailK.x1(), detailK.y1(), 0.0F, false);
		if (e == null) {
			tengah(l, TemaUi.k("toko.pilih"), detailK);
			return;
		}
		int x0 = detailK.x0() + 5;
		int x1 = detailK.x1() - 5;
		int y = detailK.y0() + 4;
		int pv = tata == Tata.SEMPIT ? 28 : tata == Tata.LEBAR ? 72 : 48;
		int slot = TemaUi.ikonBarang(e.id());
		t.pratinjau.gambar(l, kunci + "_3d", x0, y, x0 + pv, y + pv, e.barang(), null, slot < 0 ? e.i(LembarKunci.IKON, LembarKunci.Ikon.HERBA) : -1);
		if (e.barang().isEmpty() && slot >= 0) {
			TemaUi.ikon(l.g, slot, x0 + pv / 2 - 12, y + pv / 2 - 12, 24);
		}
		int tx = x0 + pv + 6;
		l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), tx, y, x1 - tx, Palette.mutu(e.i(LembarKunci.MUTU, -1), gaya.gelap(), gaya.teks));
		int ty = y + 11;
		int by = detailK.y1() - 19;
		Component catatan = e.teksAtau(LembarKunci.CATATAN);
		if (catatan != null) {
			ty += Math.round(l.bungkus(catatan, tx, ty, x1 - tx, gaya.teksLemah, Math.max(9, Math.min(27, by - ty - 24)))) + 2;
		}
		int stok = e.i(LembarKunci.STOK, -1);
		l.samar(stok < 0 ? tt("kartu.stok_tak_terbatas") : tt("kartu.stok", stok), tx, ty, x1 - tx);
		ty += 10;
		long harga = e.l(LembarKunci.HARGA, 0L);
		LabelHarga.gambar(l, harga, uang(), tx, ty, false, false);
		float diskon = lembar.f(LembarKunci.DISKON, 0.0F);
		if (diskon > 0.001F) {
			TemaUi.chip(l, -1, TemaUi.k("toko.diskon", Math.round(diskon * 100.0F)), x1 - 50, ty, Palette.OK);
		}
		ty += 12;
		int ey = Math.max(ty, y + pv + 4);
		if (ekstra != null && ey < by - 10) {
			l.klip(x0, ey, x1, by - 2);
			ekstra.gambar(l, e, x0, ey, x1);
			l.lepasKlip();
		}
		int n = jumlah ? t.jumlah.nilai() : 1;
		int pw = 0;
		if (jumlah) {
			int maks = stok < 0 ? 64 : Math.max(1, stok);
			pw = PemilihJumlah.lebar(l, maks);
			t.jumlah.gambar(l, kunci + "_n", x1 - pw, by + 1, maks);
			pw += 6;
		}
		long total = harga * n;
		boolean bisa = tutup == null && e.bisa() && mampu(total);
		Component alasan = tutup != null ? tutup : !e.bisa() ? alasanKeadaan(e) : !mampu(total) ? kurang(total) : null;
		tombolOp(l, kunci + "_beli", x0, by, x1 - x0 - pw, 15, TemaUi.k("toko.beli", Brush.angka(total)), bisa, alasan,
				jumlah ? LembarKunci.op(opBeli, e.id(), n) : LembarKunci.op(opBeli, e.id()));
		if (!e.bisa() && alasan != null && tutup == null) {
			l.teks(alasan, x0, by - 10, x1 - x0, l.gaya.turun());
		}
	}
}
