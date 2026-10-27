package dev.kultivator.client.dunia.kota42.ui.layar;

import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.GulirIsi;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.PemilihJumlah;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Gudang;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * {@code gudang} (DESIGN 42.11.8) — warehouses, caravans, trade contracts and city orders, style kayu; the town price tab of
 * Buku Kota ends here too ("one trade ledger"). Tabs on the single kit:
 * <ul>
 * <li>Harga — the twelve price categories: factor on a 0.6-1.6 bar with a seven-day line, the town's stock on a 0-200 bar,
 * and the causes of the selected one ("festival +12 %");</li>
 * <li>Gudang — the warehouses as chips, the slot grid of the chosen one (a click on a stack takes it into the bag), the
 * bag as a strip (a click puts that stack in), and the rent offers (rent, extend);</li>
 * <li>Kirim — send a stack to another warehouse (destination chips, amount picker) and the caravans on the road with risk,
 * days left and "Kawal";</li>
 * <li>Kontrak — the player's contracts (item, route, progress, stock against need, the penalty) with "Batal", and the
 * contracts on offer; Pesanan — the city's orders with a progress bar, what the bag holds and "Serahkan".</li>
 * </ul>
 */
public final class LayarGudang extends LayarKotaDasar {

	private static final int SEL = 18;

	private final DaftarGulir dHarga = new DaftarGulir();
	private final DaftarGulir dKontrak = new DaftarGulir();
	private final DaftarGulir dPesanan = new DaftarGulir();
	private final GulirIsi gulirGudang = new GulirIsi();
	private final GulirIsi gulirKirim = new GulirIsi();
	private final GulirIsi gulirHarga = new GulirIsi();
	private final PemilihJumlah jumlahKirim = new PemilihJumlah();
	private final PemilihJumlah jumlahPesanan = new PemilihJumlah();
	private int slotKirim = -1;
	private int tujuanKirim;
	private String idPesanan = "";

	public LayarGudang(int npcId, CompoundTag lembar) {
		super(Gudang.LAYAR, npcId, lembar, KotaGambar.t("gudang.judul"), LembarKunci.GAYA_KAYU);
	}

	@Override
	protected List<Tab> daftarTab() {
		return List.of(tab(Gudang.TAB_HARGA, "kota.tab.harga", LembarKunci.Ikon.NB), tab(Gudang.TAB_GUDANG, "kota.tab.gudang", Glyphs.Atlas.TONGGAK),
				tab(Gudang.TAB_KIRIM, "kota.tab.kirim", Glyphs.Atlas.PANAH_KANAN), tab(Gudang.TAB_KONTRAK, "kota.tab.kontrak", Glyphs.Atlas.GULUNGAN),
				tab(Gudang.TAB_PESANAN, "kota.tab.pesanan", Glyphs.Atlas.PIALA));
	}

	@Override
	protected void tabBerubah() {
		long kini = Util.getMillis();
		dHarga.mulaiUlang(kini);
		dKontrak.mulaiUlang(kini);
		dPesanan.mulaiUlang(kini);
		gulirGudang.atas();
		gulirKirim.atas();
		slotKirim = -1;
	}

	@Override
	public String helpTopic() {
		return KotaLembar.TOPIK_EKONOMI;
	}

	@Override
	protected @Nullable GulirIsi gulirTab() {
		return switch (tab) {
			case Gudang.TAB_GUDANG -> gulirGudang;
			case Gudang.TAB_KIRIM -> gulirKirim;
			case Gudang.TAB_KONTRAK, Gudang.TAB_PESANAN -> null;
			default -> gulirHarga;
		};
	}

	@Override
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		int y = KotaGambar.kota(l, lembar, x0, y0, x1);
		switch (tab) {
			case Gudang.TAB_GUDANG -> gudang(l, x0, y, x1, y1);
			case Gudang.TAB_KIRIM -> kirimTab(l, x0, y, x1, y1);
			case Gudang.TAB_KONTRAK -> kontrak(l, x0, y, x1, y1);
			case Gudang.TAB_PESANAN -> pesanan(l, x0, y, x1, y1);
			default -> harga(l, x0, y, x1, y1);
		}
	}

	// ------------------------------------------------------------------ prices

	private void harga(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Gudang.HARGA);
		if (rows.isEmpty()) {
			kosong(l, KotaGambar.t("gudang.harga_kosong"), x0, y0, x1, y1);
			return;
		}
		Kotak[] b = bagi(x0, y0, x1, y1);
		Kotak kiri = b[0];
		dHarga.gambar(l, "harga", kiri.x0(), kiri.y0(), kiri.x1(), kiri.y1(), rows.size(), 26, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			IkonKota.gambar(lk.g, IkonKota.kategori(e.id()), rx0 + 3, ry0 + 5, 16);
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 23, ry0 + 3, rx1 - rx0 - 96, lk.gaya.teks);
			float faktor = e.f(KotaLembar.Buku.FAKTOR, 1.0F);
			lk.kanan(Component.literal("×" + String.format(java.util.Locale.ROOT, "%.2f", faktor)), rx1 - 4, ry0 + 3, faktor > 1.1F ? Palette.PERINGATAN : faktor < 0.9F ? lk.gaya.naik() : lk.gaya.teks);
			Bilah.gambar(lk, rx0 + 23, ry0 + 15, rx0 + 23 + 60, (faktor - 0.6F) / 1.0F, faktor > 1.1F ? 0xFFD0A040 : faktor < 0.9F ? 0xFF5FA88C : 0xFFA8A8A0, new float[] {0.4F});
			int[] g = e.ia(KotaLembar.GRAFIK);
			KotaGambar.garis(lk, g, rx0 + 90, ry0 + 14, Math.max(rx0 + 100, rx1 - 50), ry1 - 4, 0xFF5A8AD0);
		}, null, null);
		Kotak kanan = b[1];
		l.sumur(kanan.x0(), kanan.y0(), kanan.x1(), kanan.y1());
		int sel = dHarga.pilihan();
		if (sel < 0 || sel >= rows.size()) {
			return;
		}
		Lembar e = rows.get(sel);
		int dx = kanan.x0() + 5;
		int xr = kanan.x1() - 10;
		int y = gulirHarga.mulai(l, kanan.x0() + 1, kanan.y0() + 1, kanan.x1() - 6, kanan.y1() - 1) + 3;
		int ink = l.gaya.teksSumur();
		IkonKota.gambar(l.g, IkonKota.kategori(e.id()), dx, y - 1, 16);
		l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), dx + 20, y, xr - dx - 20, ink);
		y += 18;
		int stok = e.i(KotaLembar.Buku.STOK, 100);
		int lw = Math.min(70, (xr - dx) / 3);
		l.teks(KotaGambar.t("gudang.stok", stok), dx, y, lw, Palette.INK_FAINT);
		Bilah.gambar(l, dx + lw, y + 3, xr, stok / 200.0F, stok < 70 ? 0xFFD0A040 : 0xFF5FA88C, new float[] {0.5F});
		y += 14;
		l.teks(Brush.bold(KotaGambar.t("gudang.sebab")), dx, y, xr - dx, ink);
		y += 12;
		List<Lembar> sebab = e.daftar(KotaLembar.SEBAB);
		if (sebab.isEmpty()) {
			l.teks(KotaGambar.t("gudang.sebab_kosong"), dx, y, xr - dx, Palette.INK_FAINT);
		}
		for (Lembar s : sebab) {
			int n = s.i(LembarKunci.NILAI, 0);
			l.teks(s.teks(LembarKunci.TEKS), dx, y, xr - dx - 34, ink);
			l.kanan(Component.literal(Brush.bertanda(n) + " %"), xr, y, n > 0 ? Palette.BAHAYA : n < 0 ? 0xFF2E7A4E : Palette.INK_FAINT);
			y += 11;
		}
		gulirHarga.selesai(l, y + 2);
	}

	// ------------------------------------------------------------------ warehouses

	private int terpilih(List<Lembar> daftar) {
		String p = lembar.s(KotaLembar.PILIH);
		for (int i = 0; i < daftar.size(); i++) {
			if (daftar.get(i).id().equals(p)) {
				return i;
			}
		}
		return daftar.isEmpty() ? -1 : 0;
	}

	private void chips(Lukis l, List<Lembar> daftar, int sel, int x0, int y, int x1, String awalan) {
		int x = x0;
		for (int i = 0; i < daftar.size(); i++) {
			Lembar g = daftar.get(i);
			Component label = KotaGambar.t("gudang.chip", g.teks(LembarKunci.TEMPAT), g.i(LembarKunci.SLOT, 0), g.i(Gudang.SLOT_MAKS, 0));
			int w = TalismanButton.lebar(l.font, label, 60, 150);
			if (x + w > x1 && x > x0) {
				break;
			}
			final int ke = i;
			TalismanButton.gambar(l, awalan + i, x, y, w, 14, label, true, null, () -> {
				if (awalan.equals("chip#")) {
					pilihBaris(Gudang.TAB_GUDANG, daftar.get(ke).id());
				} else {
					tujuanKirim = ke;
				}
			}, false, i == sel);
			x += w + 3;
		}
	}

	private void gudang(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> daftar = lembar.daftar(Gudang.GUDANG);
		List<Lembar> tawaran = lembar.daftar(Gudang.TAWARAN_SEWA);
		int xr = x1 - 6;
		int y = gulirGudang.mulai(l, x0, y0, xr, y1);
		if (daftar.isEmpty() && tawaran.isEmpty()) {
			gulirGudang.selesai(l, y);
			kosong(l, KotaGambar.t("gudang.kosong"), x0, y0, x1, y1);
			return;
		}
		int sel = terpilih(daftar);
		if (!daftar.isEmpty()) {
			chips(l, daftar, sel, x0, y, xr, "chip#");
			y += 18;
			Lembar g = daftar.get(sel);
			boolean aktif = g.b(LembarKunci.AKTIF);
			l.teks(KotaGambar.t("gudang.info", g.teks(LembarKunci.NAMA), g.i(LembarKunci.SLOT, 0), g.i(Gudang.SLOT_MAKS, 0), g.l(KotaLembar.SAMPAI, 0L)), x0, y, xr - x0,
					aktif ? l.gaya.teks : Palette.BAHAYA);
			y += 12;
			y = grid(l, g, lembar.daftar(Gudang.ISI), x0, y, xr);
			y += 4;
			y = tas(l, g, x0, y, xr);
			Component perpanjang = KotaGambar.t("gudang.perpanjang");
			int pw = lebarTombol(l, perpanjang, 80, 160);
			tulis(l, "perpanjang", x0, y, pw, 14, perpanjang, true, null, LembarKunci.op(Gudang.OP_PERPANJANG, g.id()));
			tunjuk(l, "tunjuk", x0 + pw + 4, y, 48, 14, g.i(LembarKunci.X, 0), g.i(LembarKunci.Z, 0));
			y += 20;
		}
		if (!tawaran.isEmpty()) {
			y += l.kepala(KotaGambar.t("gudang.sewa_baru"), x0, y, xr);
			for (int i = 0; i < tawaran.size(); i++) {
				Lembar t = tawaran.get(i);
				l.kartu(x0, y, xr, y + 22, 0.0F, false);
				IkonKota.gambar(l.g, IkonKota.GUDANG, x0 + 4, y + 3, 16);
				l.teks(KotaGambar.t("gudang.sewa_nama", t.teks(LembarKunci.NAMA), t.i(Gudang.SLOT_MAKS, 0)), x0 + 26, y + 7, xr - x0 - 150, l.gaya.teks);
				LabelHarga.gambar(l, t.l(LembarKunci.HARGA, 0L), -1L, xr - 70, y + 7, true, false);
				tulis(l, "sewa#" + i, xr - 66, y + 4, 62, 14, KotaGambar.t("gudang.sewa"), t.b(LembarKunci.BISA), t.teksAtau(LembarKunci.ALASAN),
						LembarKunci.op(Gudang.OP_SEWA, t.id()));
				y += 25;
			}
		}
		gulirGudang.selesai(l, y);
	}

	/** The slot grid of warehouse {@code g}: filled slots show item and count; a click takes the stack. */
	private int grid(Lukis l, Lembar g, List<Lembar> isi, int x0, int y, int xr) {
		int maks = Math.max(1, g.i(Gudang.SLOT_MAKS, 27));
		int kolom = Math.max(1, Math.min(9, (xr - x0) / SEL));
		java.util.Map<Integer, Lembar> peta = new java.util.HashMap<>();
		for (Lembar s : isi) {
			peta.put(s.i(LembarKunci.SLOT, -1), s);
		}
		int baris = (maks + kolom - 1) / kolom;
		int lebar = kolom * SEL;
		int ox = x0 + Math.max(0, (xr - x0 - lebar) / 2);
		for (int i = 0; i < maks; i++) {
			int cx = ox + (i % kolom) * SEL;
			int cy = y + (i / kolom) * SEL;
			Lembar s = peta.get(i);
			l.g.fill(cx, cy, cx + SEL - 1, cy + SEL - 1, s != null ? Palette.argb(0x55, 0xD4A637) : Palette.argb(0x40, 0x000000));
			Brush.frame(l.g, cx, cy, cx + SEL - 1, cy + SEL - 1, Palette.argb(0x60, 0x000000));
			if (s != null) {
				ItemStack item = s.barang();
				l.g.item(item, cx + 1, cy + 1);
				int n = s.i(LembarKunci.JUMLAH, 1);
				if (n > 1) {
					Brush.text(l.g, l.font, Component.literal(Integer.toString(n)), cx + SEL - 2 - l.font.width(Integer.toString(n)), cy + SEL - 9, 0xFFFFFFFF, 1.0F, true);
				}
				final int slot = i;
				l.zona("slot#" + i, cx, cy, cx + SEL - 1, cy + SEL - 1, () -> {
					if (tulisBoleh()) {
						kirim(LembarKunci.op(Gudang.OP_AMBIL, g.id(), slot));
					}
				}, tulisBoleh(), tulisBoleh() ? null : alasanBaca(), false);
				if (l.di(cx, cy, cx + SEL - 1, cy + SEL - 1)) {
					l.tip(List.of(item.getHoverName(), KotaGambar.t("gudang.ambil_tip")));
				}
			}
		}
		return y + baris * SEL;
	}

	/** The player's bag (36 slots) as a strip of stacks; a click puts that stack into the warehouse. */
	private int tas(Lukis l, Lembar g, int x0, int y, int xr) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return y;
		}
		l.teks(KotaGambar.t("gudang.tas"), x0, y, xr - x0, l.gaya.teksSamar);
		y += 11;
		int kolom = Math.max(1, Math.min(9, (xr - x0) / SEL));
		int lebar = kolom * SEL;
		int ox = x0 + Math.max(0, (xr - x0 - lebar) / 2);
		int n = 0;
		for (int i = 0; i < 36; i++) {
			ItemStack item = mc.player.getInventory().getItem(i);
			int cx = ox + (n % kolom) * SEL;
			int cy = y + (n / kolom) * SEL;
			n++;
			l.g.fill(cx, cy, cx + SEL - 1, cy + SEL - 1, Palette.argb(0x30, 0x000000));
			if (item.isEmpty()) {
				continue;
			}
			l.g.item(item, cx + 1, cy + 1);
			if (item.getCount() > 1) {
				Brush.text(l.g, l.font, Component.literal(Integer.toString(item.getCount())), cx + SEL - 2 - l.font.width(Integer.toString(item.getCount())), cy + SEL - 9, 0xFFFFFFFF, 1.0F, true);
			}
			final int slot = i;
			l.zona("tas#" + i, cx, cy, cx + SEL - 1, cy + SEL - 1, () -> {
				if (tulisBoleh()) {
					kirim(LembarKunci.op(Gudang.OP_TARUH, g.id(), slot));
				}
			}, tulisBoleh(), tulisBoleh() ? null : alasanBaca(), false);
			if (l.di(cx, cy, cx + SEL - 1, cy + SEL - 1)) {
				l.tip(List.of(item.getHoverName(), KotaGambar.t("gudang.taruh_tip")));
			}
		}
		return y + ((n + kolom - 1) / kolom) * SEL + 4;
	}

	// ------------------------------------------------------------------ send

	private void kirimTab(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> daftar = lembar.daftar(Gudang.GUDANG);
		List<Lembar> jalan = lembar.daftar(Gudang.KIRIM);
		int xr = x1 - 6;
		int y = gulirKirim.mulai(l, x0, y0, xr, y1);
		if (daftar.isEmpty() && jalan.isEmpty()) {
			gulirKirim.selesai(l, y);
			kosong(l, KotaGambar.t("gudang.kirim_kosong"), x0, y0, x1, y1);
			return;
		}
		if (daftar.size() >= 2) {
			int dari = terpilih(daftar);
			y += l.kepala(KotaGambar.t("gudang.kirim_dari", daftar.get(dari).teks(LembarKunci.TEMPAT)), x0, y, xr);
			List<Lembar> isi = lembar.daftar(Gudang.ISI);
			if (tujuanKirim == dari || tujuanKirim >= daftar.size()) {
				tujuanKirim = dari == 0 ? 1 : 0;
			}
			l.teks(KotaGambar.t("gudang.kirim_ke"), x0, y + 3, 60, l.gaya.teksLemah);
			chips(l, daftar, tujuanKirim, x0 + 40, y, xr, "tujuan#");
			y += 19;
			for (Lembar s : isi) {
				int slot = s.i(LembarKunci.SLOT, -1);
				boolean pilih = slot == slotKirim;
				l.kartu(x0, y, xr, y + 20, 0.0F, pilih);
				ItemStack item = s.barang();
				l.g.item(item, x0 + 3, y + 2);
				l.teks(KotaGambar.t("gudang.isi_baris", item.getHoverName(), s.i(LembarKunci.JUMLAH, 1)), x0 + 24, y + 6, xr - x0 - 28, l.gaya.teks);
				l.zona("kirim_slot#" + slot, x0, y, xr, y + 20, () -> {
					slotKirim = slot;
					jumlahKirim.set(1);
				}, true, null, true);
				y += 22;
			}
			if (isi.isEmpty()) {
				l.teks(KotaGambar.t("gudang.kirim_kosong_isi"), x0, y, xr - x0, l.gaya.teksSamar);
				y += 14;
			}
			Lembar terpilihIsi = null;
			for (Lembar s : isi) {
				if (s.i(LembarKunci.SLOT, -1) == slotKirim) {
					terpilihIsi = s;
				}
			}
			int maks = terpilihIsi == null ? 1 : terpilihIsi.i(LembarKunci.JUMLAH, 1);
			int pw = jumlahKirim.gambar(l, "kirim_n", x0, y, maks);
			Component kirimLabel = KotaGambar.t("gudang.kirim_ke_tombol", daftar.get(tujuanKirim).teks(LembarKunci.TEMPAT));
			int w = lebarTombol(l, kirimLabel, 80, xr - x0 - pw - 6);
			tulis(l, "kirim", x0 + pw + 4, y, w, 14, kirimLabel, terpilihIsi != null, terpilihIsi == null ? KotaGambar.t("gudang.pilih_tumpukan") : null,
					LembarKunci.op(Gudang.OP_KIRIM, daftar.get(dari).id(), daftar.get(tujuanKirim).id(), slotKirim, jumlahKirim.nilai()));
			y += 22;
		}
		y += l.kepala(KotaGambar.t("gudang.kafilah"), x0, y, xr);
		if (jalan.isEmpty()) {
			l.teks(KotaGambar.t("gudang.kafilah_kosong"), x0, y, xr - x0, l.gaya.teksSamar);
			y += 14;
		}
		for (int i = 0; i < jalan.size(); i++) {
			Lembar k = jalan.get(i);
			l.kartu(x0, y, xr, y + 36, 0.0F, false);
			IkonKota.gambar(l.g, IkonKota.KAFILAH, x0 + 4, y + 10, 16);
			l.teks(KotaGambar.t("gudang.kafilah_rute", k.teks("dari"), k.teks(Gudang.KE)), x0 + 26, y + 3, xr - x0 - 100, l.gaya.teks);
			StringBuilder isi = new StringBuilder();
			for (Lembar s : k.daftar(Gudang.ISI)) {
				if (isi.length() > 0) {
					isi.append(", ");
				}
				isi.append(s.i(LembarKunci.JUMLAH, 1)).append(' ').append(s.teks(LembarKunci.NAMA).getString());
			}
			l.teks(Component.literal(isi.toString()), x0 + 26, y + 14, xr - x0 - 32, l.gaya.teksLemah);
			l.teks(KotaGambar.t("gudang.kafilah_sisa", k.i(LembarKunci.SISA_HARI, 0), k.i(Gudang.RISIKO, 0)), x0 + 26, y + 25, xr - x0 - 100, k.i(Gudang.RISIKO, 0) > 10 ? Palette.PERINGATAN : l.gaya.teksSamar);
			boolean kawal = k.b(Gudang.KAWAL);
			tulis(l, "kawal#" + i, xr - 70, y + 20, 66, 14, KotaGambar.t(kawal ? "gudang.dikawal" : "gudang.kawal", Brush.angka(k.l(Gudang.ONGKOS, 0L))), !kawal, null,
					LembarKunci.op(Gudang.OP_KAWAL, k.id()));
			y += 40;
		}
		gulirKirim.selesai(l, y);
	}

	// ------------------------------------------------------------------ contracts

	private void kontrak(Lukis l, int x0, int y0, int x1, int y1) {
		int tingkat = lembar.i(Gudang.TINGKAT, 1);
		l.teks(KotaGambar.t("gudang.nama_dagang", lembar.i(Gudang.NAMA_DAGANG, 0), KotaGambar.t("tingkat." + Math.min(5, Math.max(1, tingkat)))), x0, y0, x1 - x0 - 100, l.gaya.teks);
		l.kanan(KotaGambar.t("gudang.kontrak_maks", lembar.daftar(Gudang.KONTRAK).size(), lembar.i(Gudang.KONTRAK_MAKS, 2)), x1, y0, l.gaya.teksLemah);
		int y = y0 + 13;
		List<Lembar> milik = lembar.daftar(Gudang.KONTRAK);
		List<Lembar> tawaran = lembar.daftar(Gudang.TAWARAN);
		List<Lembar> semua = new ArrayList<>(milik);
		int nMilik = milik.size();
		semua.addAll(tawaran);
		if (semua.isEmpty()) {
			kosong(l, KotaGambar.t("gudang.kontrak_kosong"), x0, y, x1, y1);
			return;
		}
		dKontrak.judul(i -> false, 13).gambar(l, "kontrak", x0, y, x1, y1, semua.size(), 44, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = semua.get(i);
			boolean tawar = i >= nMilik;
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			ItemStack item = e.barang();
			if (!item.isEmpty()) {
				lk.g.item(item, rx0 + 4, ry0 + 4);
			}
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 24, ry0 + 3, rx1 - rx0 - 120, lk.gaya.teks);
			if (tawar) {
				lk.kanan(KotaGambar.t("gudang.tawaran"), rx1 - 5, ry0 + 3, KotaGambar.EMAS);
			} else {
				lk.kanan(KotaGambar.t("gudang.kali", e.i(Gudang.KALI, 0), e.i(Gudang.KALI_MAKS, 0)), rx1 - 5, ry0 + 3, lk.gaya.teksLemah);
			}
			boolean cukup = e.b(Gudang.CUKUP);
			if (!tawar) {
				lk.teks(KotaGambar.t("gudang.stok_perlu", e.i(KotaLembar.Buku.STOK, 0), e.i(LembarKunci.JUMLAH, 0), cukup ? "✓" : "✗"), rx0 + 24, ry0 + 14, rx1 - rx0 - 100,
						cukup ? lk.gaya.naik() : lk.gaya.turun());
			}
			lk.teks(KotaGambar.t("gudang.kontrak_rinci", e.i(Gudang.TIAP, 7), String.format(java.util.Locale.ROOT, "%.1f", e.f(Gudang.HARGA_SATUAN, 0.0F)), e.i(Gudang.DENDA, 0)),
					rx0 + 24, ry0 + 25, rx1 - rx0 - 100, lk.gaya.teksSamar);
			if (tawar) {
				tulis(lk, "ambil#" + i, rx1 - 84, ry1 - 17, 80, 14, KotaGambar.t("gudang.ambil_kontrak"), e.b(LembarKunci.BISA), e.teksAtau(LembarKunci.ALASAN),
						LembarKunci.op(Gudang.OP_AMBIL_KONTRAK, e.id()));
			} else {
				String op = LembarKunci.op(Gudang.OP_BATAL_KONTRAK, e.id());
				boolean baca = !tulisBoleh();
				aksi(lk, "batal#" + i, rx1 - 84, ry1 - 17, 80, 14, KotaGambar.t("gudang.batal_kontrak"), !baca, baca ? alasanBaca() : null,
						() -> konfirmasi(SegelKonfirmasi.Tingkat.SEDANG, KotaGambar.t("gudang.batal_yakin", e.teks(LembarKunci.NAMA), e.i(Gudang.DENDA, 0)), () -> kirim(op)));
			}
		}, null, null);
	}

	// ------------------------------------------------------------------ city orders

	private void pesanan(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Gudang.PESANAN);
		if (rows.isEmpty()) {
			kosong(l, KotaGambar.t("gudang.pesanan_kosong"), x0, y0, x1, y1);
			return;
		}
		int bawah = y1 - 18;
		dPesanan.gambar(l, "pesanan", x0, y0, x1, bawah, rows.size(), 34, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			ItemStack item = e.barang();
			if (!item.isEmpty()) {
				lk.g.item(item, rx0 + 4, ry0 + 8);
			}
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 24, ry0 + 3, rx1 - rx0 - 120, lk.gaya.teks);
			int perlu = Math.max(1, e.i(Gudang.PERLU, 1));
			int kumpul = e.i(Gudang.TERKUMPUL, 0);
			Bilah.gambar(lk, rx0 + 24, ry0 + 16, rx0 + 24 + Math.min(110, rx1 - rx0 - 120), kumpul / (float) perlu, 0xFF5FA88C, null);
			lk.teks(Component.literal(kumpul + " / " + perlu), rx0 + 30 + Math.min(110, rx1 - rx0 - 120), ry0 + 14, 60, lk.gaya.teksLemah);
			lk.teks(KotaGambar.t("gudang.punya", e.i(Gudang.PUNYA, 0)), rx0 + 24, ry0 + 24, rx1 - rx0 - 100, lk.gaya.teksSamar);
			LabelHarga.gambar(lk, e.l(LembarKunci.HARGA, 0L), -1L, rx1 - 4, ry0 + 3, true, false);
			lk.kanan(KotaGambar.t("gudang.nama_baik", e.i(LembarKunci.NAMA_BAIK, 0)), rx1 - 4, ry0 + 14, lk.gaya.teksLemah);
		}, null, i -> {
			String id = rows.get(i).id();
			if (!id.equals(idPesanan)) {
				idPesanan = id;
				jumlahPesanan.set(1);
			}
		});
		int sel = dPesanan.pilihan();
		if (sel < 0 || sel >= rows.size()) {
			return;
		}
		Lembar e = rows.get(sel);
		int sisa = Math.max(0, e.i(Gudang.PERLU, 0) - e.i(Gudang.TERKUMPUL, 0));
		int maks = Math.max(1, Math.min(e.i(Gudang.PUNYA, 0), sisa));
		int by = y1 - 16;
		int pw = jumlahPesanan.gambar(l, "serah_n", x0, by, maks);
		Component serah = KotaGambar.t("gudang.serahkan");
		int w = lebarTombol(l, serah, 70, 130);
		boolean ada = e.i(Gudang.PUNYA, 0) > 0 && sisa > 0;
		tulis(l, "serahkan", x0 + pw + 4, by, w, 14, serah, ada, ada ? null : KotaGambar.t(sisa <= 0 ? "gudang.sudah_penuh" : "gudang.tas_kosong"),
				LembarKunci.op(Gudang.OP_SERAHKAN, e.id(), Math.min(jumlahPesanan.nilai(), maks)));
	}
}
