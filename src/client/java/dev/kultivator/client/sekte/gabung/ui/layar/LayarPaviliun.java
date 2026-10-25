package dev.kultivator.client.sekte.gabung.ui.layar;

import static dev.kultivator.client.sekte.gabung.ui.layar.LayarGabung2.pk;
import static dev.kultivator.client.sekte.gabung.ui.layar.LayarGabung2.u;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.sekte.gabung.ui.IkonGabung;
import dev.kultivator.client.tempat.ui.kit.DaftarGulir;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.KeadaanKosong;
import dev.kultivator.client.tempat.ui.kit.KotakCari;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.PemilihJumlah;
import dev.kultivator.client.tempat.ui.kit.Pratinjau3D;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.data.Dao;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import dev.kultivator.sekte.gabung.ui.GabungLembar.Paviliun;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code gabung_paviliun} (DESIGN 41.6.5, 41.10.4): the Paviliun Jasa, where a member trades personal PK (never NB) for
 * techniques, the sect's Teknik Warisan, pills, treasures, materials and secret-realm keys. Every goods tablet is a
 * searchable list (item, name in the quality colour, the PK price after the rank factor with the base price struck when
 * it differs, the corak seal "−20 %", the weekly stock or the lock reason) beside a detail pane with the turning 3D
 * preview, the price breakdown, the stock and an amount picker for "Tukar". The dwelling is not a tablet here: it lives in
 * Diri › Harta › Kediaman (DESIGN 43.11 G66). Pesanan lists today's three requests with "Serahkan dari tas"; Ceramah lets an Inti member buy a sermon for a
 * registered ward. Pelayan see only Pesanan (the server locks the rest).
 */
public final class LayarPaviliun extends LayarGabungDasar {

	/** The pavilion caps one exchange at this many pieces ({@code PaviliunJasa.TUKAR_MAKS}). */
	private static final int TUKAR_MAKS = 16;

	private final DaftarGulir daftar = new DaftarGulir();
	private final DaftarGulir pesanan = new DaftarGulir();
	private final DaftarGulir ceramah = new DaftarGulir();
	private final Pratinjau3D pratinjau = new Pratinjau3D();
	private final PemilihJumlah jumlah = new PemilihJumlah();
	private final List<Lembar> tampil = new ArrayList<>();
	private String tampilKunci = "";
	private long muncul;

	public LayarPaviliun(int npcId, CompoundTag lembar) {
		super(Paviliun.LAYAR, npcId, lembar, u("paviliun.judul"), TempatWarna.Gaya.GIOK);
		cari = new KotakCari();
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of(new TabLentera.Tab(Paviliun.TAB_TEKNIK, u("paviliun.tab.teknik"), IkonTempat.GULUNGAN, null),
				new TabLentera.Tab(Paviliun.TAB_WARISAN, u("paviliun.tab.warisan"), LembarKunci.Ikon.CAP_MERAH, null),
				new TabLentera.Tab(Paviliun.TAB_PIL, u("paviliun.tab.pil"), LembarKunci.Ikon.PIL, null),
				new TabLentera.Tab(Paviliun.TAB_PUSAKA, u("paviliun.tab.pusaka"), LembarKunci.Ikon.JIAN, null),
				new TabLentera.Tab(Paviliun.TAB_BAHAN, u("paviliun.tab.bahan"), LembarKunci.Ikon.HERBA, null),
				new TabLentera.Tab(Paviliun.TAB_KUOTA, u("paviliun.tab.kuota"), IkonTempat.KUNCI, null),
				new TabLentera.Tab(Paviliun.TAB_PESANAN, u("paviliun.tab.pesanan"), IkonTempat.SURAT, null),
				new TabLentera.Tab(Paviliun.TAB_CERAMAH, u("paviliun.tab.ceramah"), IkonTempat.BUKU, null));
	}

	@Override
	protected KeadaanKosong.Gambar gambarKosong() {
		return KeadaanKosong.Gambar.NAMPAN;
	}

	@Override
	protected void tabBerubah() {
		long now = Util.getMillis();
		daftar.mulaiUlang(now);
		pesanan.mulaiUlang(now);
		ceramah.mulaiUlang(now);
		jumlah.set(1);
		muncul = now;
		tampilKunci = "";
	}

	@Override
	protected void setelahLembar(boolean buka) {
		tampilKunci = "";
	}

	@Override
	protected Component petunjuk() {
		return barangTab() ? u("paviliun.petunjuk") : null;
	}

	private boolean barangTab() {
		return !Paviliun.TAB_PESANAN.equals(tab) && !Paviliun.TAB_CERAMAH.equals(tab);
	}

	private int pkPunya() {
		return lembar.i(LembarKunci.PK, 0);
	}

	@Override
	protected void isiGabung(Lukis l, int x0, int y0, int x1, int y1) {
		int y = kepalaSekte(l, x0, y0, x1);
		y = bilah(l, x0, y, x1);
		switch (tab) {
			case Paviliun.TAB_PESANAN -> pesanan(l, x0, y, x1, y1);
			case Paviliun.TAB_CERAMAH -> ceramah(l, x0, y, x1, y1);
			default -> barang(l, x0, y, x1, y1);
		}
	}

	/** Search (goods tablets), the rank factor seal and the PK on hand. Returns the y under it. */
	private int bilah(Lukis l, int x0, int y, int x1) {
		int kanan = x1;
		kanan -= pk(l, pkPunya(), -1L, kanan, y + 2, true, 1.0F) + 4;
		l.kanan(u("paviliun.pk_anda"), kanan, y + 2, gaya.teksSamar, 1.0F);
		kanan -= font.width(u("paviliun.pk_anda")) + 8;
		int pangkat = lembar.i(GabungLembar.PANGKAT_ANGGOTA, 0);
		float pengali = lembar.f(Paviliun.PENGALI, 1.0F);
		MutableComponent seal = Pangkat.byOrdinal(Math.max(0, pangkat)).nama().copy()
				.append(Component.literal(String.format(java.util.Locale.ROOT, " ×%.2f", pengali)));
		int sw = font.width(seal) + 18;
		if (kanan - sw > x0 + 60) {
			int sx = kanan - sw;
			l.g.fill(sx, y, sx + sw, y + 11, pengali < 0.999F ? TempatWarna.SEGEL : Palette.argb(0x50, 0x000000));
			IkonGabung.gambar(l.g, IkonGabung.pangkat(Math.max(0, pangkat)), sx + 2, y + 1, 9);
			Brush.text(l.g, font, seal, sx + 14, y + 2, 0xFFFFF4E8, 1.0F, false);
			if (l.di(sx, y, sx + sw, y + 11)) {
				l.tip(u("paviliun.pengali.tip", Math.round((1.0F - pengali) * 100)));
			}
			kanan = sx - 6;
		}
		if (barangTab() && cari != null) {
			cari.gambar(l, "cari", x0, y, Math.max(40, Math.min(150, kanan - x0)), 12);
		}
		PanelTempat.garis(l.g, gaya, x0, x1, y + 15);
		return y + 19;
	}

	// ================================================================== goods tablets

	private List<Lembar> saring() {
		String kunci = tab + "|" + (cari == null ? "" : cari.teks()) + "|" + System.identityHashCode(lembar);
		if (kunci.equals(tampilKunci)) {
			return tampil;
		}
		tampil.clear();
		for (Lembar e : lembar.daftar(LembarKunci.DAFTAR)) {
			if (e.s(Paviliun.TAB_ENTRI).equals(tab) && (cari == null || cari.cocok(e.teks(LembarKunci.NAMA)))) {
				tampil.add(e);
			}
		}
		tampilKunci = kunci;
		return tampil;
	}

	private void barang(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> list = saring();
		if (list.isEmpty()) {
			kosong(l, cari != null && !cari.kosong() ? u("paviliun.tidak_ketemu") : u("paviliun.tab_kosong." + tab), x0, y0, x1, y1);
			return;
		}
		Kotak[] k = bagi(x0, y0, x1, y1);
		long now = muncul;
		daftar.gambar(l, "barang", k[0].x0(), k[0].y0(), k[0].x1(), k[0].y1(), list.size(), 24, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) ->
				baris(lk, list.get(i), rx0, ry0, rx1, ry1, sorot, pilih, now), this::tukar, i -> jumlah.set(1));
		int sel = daftar.pilihan();
		detail(l, k[1], sel >= 0 && sel < list.size() ? list.get(sel) : null);
	}

	private void baris(Lukis l, Lembar e, int x0, int y0, int x1, int y1, boolean sorot, boolean pilih, long muncul) {
		String keadaan = e.keadaan();
		boolean kunci = LembarKunci.K_TERKUNCI.equals(keadaan);
		boolean habis = LembarKunci.K_HABIS.equals(keadaan);
		boolean redup = kunci || habis;
		PanelTempat.kartu(l.g, gaya, x0, y0, x1, y1, sorot ? 1.0F : 0.0F, pilih);
		if (e.b(Paviliun.WARISAN)) {
			// the Teknik Warisan: a thin gold rim
			l.g.fill(x0, y0, x0 + 2, y1, TempatWarna.EMAS);
		}
		ItemStack item = e.barang();
		int iy = y0 + (y1 - y0 - 16) / 2;
		if (!item.isEmpty()) {
			l.g.item(item, x0 + 4, iy);
		}
		if (redup) {
			l.g.fill(x0 + 4, iy, x0 + 20, iy + 16, Palette.argb(0x70, gaya.dasar & 0xFFFFFF));
		}
		int kanan = x1 - 4;
		int harga = e.i(LembarKunci.HARGA, 0);
		int dasar = e.i(Paviliun.HARGA_DASAR, harga);
		int pw = pk(l, harga, redup ? -1L : pkPunya(), kanan, y0 + 3, true, 1.0F);
		if (dasar > harga) {
			Component coret = Component.literal(TeksTempat.angka(dasar)).withStyle(ChatFormatting.STRIKETHROUGH);
			l.kanan(coret, kanan, y0 + 13, gaya.teksSamar, 1.0F);
		}
		if (kunci) {
			IkonTempat.gambar(l.g, LembarKunci.Ikon.GEMBOK, kanan - pw - 11, y0 + 2, 9, 0xFFFFFFFF);
			pw += 11;
		}
		int tx = x0 + 24;
		int room = kanan - pw - 6 - tx;
		int warna = redup ? TempatWarna.terkunci(gaya.teks) : TempatWarna.mutu(e.i(LembarKunci.MUTU, -1), gaya.gelap(), gaya.teks);
		l.teks(e.teks(LembarKunci.NAMA), tx, y0 + 3, room, warna);
		Component bawah;
		int bawahWarna = gaya.teksSamar;
		if (redup) {
			bawah = e.teksAtau(LembarKunci.ALASAN) != null ? e.teks(LembarKunci.ALASAN) : u("paviliun.habis");
			bawahWarna = gaya.gelap() ? 0xFFFF9A8A : TempatWarna.BAHAYA;
		} else {
			int stok = e.i(LembarKunci.STOK, -1);
			bawah = stok < 0 ? u("paviliun.stok_bebas") : u("paviliun.stok", stok);
		}
		int bx = tx;
		if (e.b(Paviliun.CORAK_DISKON) && !redup) {
			Component c = u("paviliun.corak_seal");
			int cw = font.width(c) + 4;
			l.g.fill(bx, y0 + 13, bx + cw, y0 + 23, Palette.argb(0xC0, 0x2E8963));
			Brush.text(l.g, font, c, bx + 2, y0 + 14, 0xFFF4FFF8, 1.0F, false);
			bx += cw + 3;
		}
		l.teks(bawah, bx, y0 + 14, kanan - pw - 6 - bx, bawahWarna);
		if (l.di(x0, y0, x1, y1) && !item.isEmpty()) {
			List<Component> tip = new ArrayList<>();
			tip.add(item.getHoverName());
			if (e.teksAtau(LembarKunci.ALASAN) != null) {
				tip.add(e.teks(LembarKunci.ALASAN).copy().withColor(0xFFFF8A7A));
			}
			l.tip(tip);
		}
	}

	private void detail(Lukis l, Kotak k, Lembar e) {
		sumur(l, k);
		if (e == null) {
			return;
		}
		int x0 = k.x0() + 5;
		int x1 = k.x1() - 5;
		int y = k.y0() + 4;
		int by = k.y1() - 19;
		boolean bisa = e.bisa();
		int teks = gaya.teksSumur();
		int lemah = Palette.argb(0xB0, teks & 0xFFFFFF);
		// the preview: big in the wide layout, a band otherwise
		int ph = tata == Tata.LEBAR ? Math.min(80, (by - y) / 2) : Math.min(46, (by - y) / 3);
		if (ph >= 24) {
			pratinjau.gambar(l, "pratinjau", x0, y, x1, y + ph, e.barang(), null, -1);
			y += ph + 3;
		}
		int warna = TempatWarna.mutu(e.i(LembarKunci.MUTU, -1), false, teks);
		l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), x0, y, x1 - x0, warna);
		y += 11;
		Dao dao = e.ada(GabungLembar.DAO) ? Dao.byId(e.s(GabungLembar.DAO)) : null;
		MutableComponent jenis = u("paviliun.jenis." + e.s(Paviliun.TAB_ENTRI));
		if (dao != null) {
			jenis.append(" · ").append(dao.displayName());
		}
		if (e.b(Paviliun.WARISAN)) {
			jenis.append(" · ").append(u("paviliun.warisan_tanda"));
		}
		l.teks(jenis, x0, y, x1 - x0, lemah);
		y += 11;
		// price breakdown
		int harga = e.i(LembarKunci.HARGA, 0);
		int dasar = e.i(Paviliun.HARGA_DASAR, harga);
		if (y + 10 < by - 16) {
			l.teks(u("paviliun.harga_dasar"), x0, y, 70, lemah);
			pk(l, dasar, -1L, x1, y, true, 1.0F, teks);
			y += 10;
		}
		float pengali = lembar.f(Paviliun.PENGALI, 1.0F);
		if (pengali < 0.999F && y + 10 < by - 16) {
			l.teks(u("paviliun.potong_pangkat", Math.round((1.0F - pengali) * 100)), x0, y, x1 - x0, TempatWarna.OK);
			y += 10;
		}
		if (e.b(Paviliun.CORAK_DISKON) && y + 10 < by - 16) {
			l.teks(u("paviliun.potong_corak"), x0, y, x1 - x0, TempatWarna.OK);
			y += 10;
		}
		if (y + 10 < by - 16) {
			int stok = e.i(LembarKunci.STOK, -1);
			l.teks(stok < 0 ? u("paviliun.stok_bebas") : u("paviliun.stok_minggu", stok), x0, y, x1 - x0, teks);
			y += 10;
		}
		Component alasan = e.teksAtau(LembarKunci.ALASAN);
		if (alasan != null && !bisa && y + 10 < by - 16) {
			y += Math.round(l.bungkus(alasan, x0, y, x1 - x0, TempatWarna.BAHAYA, 1.0F, by - 16 - y)) + 2;
		}
		// amount and the exchange button
		int stok = e.i(LembarKunci.STOK, -1);
		int mampu = harga <= 0 ? TUKAR_MAKS : pkPunya() / Math.max(1, harga);
		int maks = Math.max(1, Math.min(TUKAR_MAKS, Math.min(stok < 0 ? TUKAR_MAKS : stok, mampu)));
		int jy = by - 15;
		int jw = jumlah.gambar(l, "jumlah", x0, jy, maks);
		pk(l, (long) harga * jumlah.nilai(), pkPunya(), x0 + jw + 6, jy + 2, false, 1.0F, teks);
		boolean cukup = (long) harga * jumlah.nilai() <= pkPunya();
		Component tolak = !bisa ? (alasan != null ? alasan : u("paviliun.habis")) : !cukup ? u("paviliun.kurang_pk") : null;
		tombolOp(l, "tukar", x0, by, x1 - x0, 15, u("paviliun.tukar", jumlah.nilai(), TeksTempat.angka((long) harga * jumlah.nilai())), bisa && cukup,
				tolak, LembarKunci.op(Paviliun.OP_TUKAR, e.id(), jumlah.nilai()));
	}

	private void tukar() {
		List<Lembar> list = saring();
		int i = daftar.pilihan();
		if (i >= 0 && i < list.size() && list.get(i).bisa()) {
			kirim(LembarKunci.op(Paviliun.OP_TUKAR, list.get(i).id(), jumlah.nilai()));
		}
	}

	// ================================================================== Pesanan, Ceramah

	private void pesanan(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> list = lembar.daftar(Paviliun.PESANAN);
		l.teks(u("paviliun.pesanan.ket"), x0, y0, x1 - x0, gaya.teksSamar);
		y0 += 11;
		if (list.isEmpty()) {
			kosong(l, u("paviliun.pesanan.kosong"), x0, y0, x1, y1);
			return;
		}
		pesanan.gambar(l, "pesanan", x0, y0, x1, y1, list.size(), 30, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = list.get(i);
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			ItemStack item = e.barang();
			if (!item.isEmpty()) {
				lk.g.item(item, rx0 + 5, ry0 + 7);
			}
			int perlu = Math.max(1, e.i(LembarKunci.JUMLAH, 1));
			int punya = e.i(GabungLembar.PUNYA, 0);
			boolean siap = punya >= perlu;
			int bw = Math.min(110, (rx1 - rx0) / 3);
			int bx = rx1 - bw - 4;
			int tx = rx0 + 26;
			lk.teks(e.teks(LembarKunci.NAMA).copy().append(" ×" + perlu), tx, ry0 + 4, bx - tx - 60, gaya.teks);
			int iw = pk(lk, e.i(LembarKunci.PK, 0), -1L, bx - 6, ry0 + 4, true, 1.0F);
			lk.teks(u("paviliun.pesanan.imbalan"), bx - 8 - iw - font.width(u("paviliun.pesanan.imbalan")), ry0 + 4, 60,
					gaya.teksSamar);
			batang(lk, tx, ry0 + 17, Math.max(20, bx - tx - 50), 4, punya / (float) perlu, siap ? TempatWarna.OK : TempatWarna.PERINGATAN, -1.0F);
			lk.teks(u("paviliun.pesanan.punya", punya, perlu), tx + Math.max(20, bx - tx - 50) + 4, ry0 + 16, 46, siap ? TempatWarna.OK : gaya.teksSamar);
			tombolOp(lk, "serah" + i, bx, ry0 + 7, bw, 15, u("paviliun.pesanan.serahkan"), siap, u("paviliun.pesanan.kurang", perlu - punya),
					LembarKunci.op(Paviliun.OP_SERAHKAN, e.id()));
		}, null, null);
	}

	private void ceramah(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> list = lembar.daftar(Paviliun.CERAMAH);
		l.teks(u("paviliun.ceramah.ket"), x0, y0, x1 - x0, gaya.teksSamar);
		y0 += 11;
		if (list.isEmpty()) {
			kosong(l, u("paviliun.ceramah.kosong"), x0, y0, x1, y1);
			return;
		}
		ceramah.gambar(l, "ceramah", x0, y0, x1, y1, list.size(), 26, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = list.get(i);
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			IkonGabung.gambar(lk.g, IkonGabung.MURID, rx0 + 4, ry0 + 5, 16);
			int bw = Math.min(120, (rx1 - rx0) / 3);
			int bx = rx1 - bw - 4;
			lk.teks(e.teks(LembarKunci.NAMA), rx0 + 24, ry0 + 4, bx - rx0 - 30, gaya.teks);
			boolean bisa = e.b(LembarKunci.BISA);
			Component bawah = bisa ? u("paviliun.ceramah.siap") : e.teks(LembarKunci.ALASAN);
			lk.teks(bawah, rx0 + 24, ry0 + 14, bx - rx0 - 30, bisa ? TempatWarna.OK : TempatWarna.BAHAYA);
			int harga = e.i(LembarKunci.HARGA, 0);
			tombolOp(lk, "ceramah" + i, bx, ry0 + 5, bw, 15, u("paviliun.ceramah.tombol", TeksTempat.angka(harga)), bisa && harga <= pkPunya(),
					bisa ? u("paviliun.kurang_pk") : e.teksAtau(LembarKunci.ALASAN), LembarKunci.op(Paviliun.OP_CERAMAH, e.id()));
		}, null, null);
	}
}
