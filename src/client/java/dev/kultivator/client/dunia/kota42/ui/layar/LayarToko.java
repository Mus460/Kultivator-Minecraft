package dev.kultivator.client.dunia.kota42.ui.layar;

import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.GulirIsi;
import dev.kultivator.client.screen.ui.InkField;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.PemilihJumlah;
import dev.kultivator.client.screen.ui.Potret;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.Lembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar.LapakSewa;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Toko;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * {@code toko_pemain} (DESIGN 42.11.6) and {@code lapak_sewa} (42.11.14): the player's own shop and the rent of a town stall,
 * style kayu. The shop has five tabs on the single kit:
 * <ul>
 * <li>Rak — the shelves as a list of stacks (item, count, price, price against value as a small meter, sale chance) beside
 * the chosen stack: the town factor and its trend, the expected sales, a number picker for the price (never above 1.5 x
 * value) with "Pasang harga" and "Harga pasar", and the day's sales against the cap;</li>
 * <li>Harga — the price list of every stack in one table; Pegawai — the clerk (a townsperson, or a disciple who takes no
 * wage), candidates and "Berhentikan"; Laci — the till, "Ambil laci", open/closed, sales to the Rumah Uang, and the shop's
 * name (one text field, Enter renames); Laporan — the last seven days of sales as the kit's ledger chart, the top eight
 * goods, complaints and thefts.</li>
 * </ul>
 * {@code lapak_sewa}: the town's stalls by district with rent per week, renter and days left; rent for 1 or 4 weeks, extend,
 * release (a light confirm) and "Buka tokonya".
 */
public final class LayarToko extends LayarKotaDasar {

	private final boolean lapak;
	private final DaftarGulir dRak = new DaftarGulir();
	private final DaftarGulir dHarga = new DaftarGulir();
	private final DaftarGulir dPegawai = new DaftarGulir();
	private final DaftarGulir dLapak = new DaftarGulir();
	private final DaftarGulir dBarang = new DaftarGulir();
	private final GulirIsi gulirLaci = new GulirIsi();
	private final GulirIsi gulirRak = new GulirIsi();
	private final GulirIsi gulirLaporan = new GulirIsi();
	private final PemilihJumlah harga = new PemilihJumlah();
	private final InkField nama = new InkField(0, 0, 100, 14, Toko.NAMA_MAKS, this::ganti);
	private String idHarga = "";
	private String namaDiisi = "";
	private String idLapak = "";

	public LayarToko(String layar, int npcId, CompoundTag lembar) {
		super(layar, npcId, lembar, KotaGambar.t(LapakSewa.LAYAR.equals(layar) ? "lapak.judul" : "toko.judul"), LembarKunci.GAYA_KAYU);
		this.lapak = LapakSewa.LAYAR.equals(layar);
	}

	@Override
	protected List<Tab> daftarTab() {
		if (lapak) {
			return List.of(tab("lapak", "kota.tab.lapak", Glyphs.Atlas.KUNCI));
		}
		return List.of(tab(Toko.TAB_RAK, "kota.tab.rak", Glyphs.Atlas.TONGGAK), tab(Toko.TAB_HARGA, "kota.tab.harga_toko", LembarKunci.Ikon.NB),
				tab(Toko.TAB_PEGAWAI, "kota.tab.pegawai", Glyphs.Atlas.HATI), tab(Toko.TAB_LACI, "kota.tab.laci", Glyphs.Atlas.KUNCI),
				tab(Toko.TAB_LAPORAN, "kota.tab.laporan", Glyphs.Atlas.BUKU));
	}

	@Override
	protected void tabBerubah() {
		long kini = Util.getMillis();
		dRak.mulaiUlang(kini);
		dHarga.mulaiUlang(kini);
		dPegawai.mulaiUlang(kini);
		dLapak.mulaiUlang(kini);
		dBarang.mulaiUlang(kini);
		gulirLaci.atas();
		gulirLaporan.atas();
		idHarga = "";
	}

	@Override
	protected void setelahLembar(boolean buka) {
		String n = lembar.sub(Toko.TOKO).teks(LembarKunci.NAMA).getString();
		if (!n.equals(namaDiisi) && !nama.focused()) {
			namaDiisi = n;
			nama.setText(n);
		}
	}

	@Override
	public String helpTopic() {
		return KotaLembar.TOPIK_EKONOMI;
	}

	private void ganti(String teks) {
		String t = teks == null ? "" : teks.trim();
		if (!t.isEmpty() && !t.equals(namaDiisi) && tulisBoleh()) {
			kirim(Toko.OP_NAMA + ":" + t);
		}
	}

	@Override
	protected @Nullable GulirIsi gulirTab() {
		if (lapak) {
			return null;
		}
		return switch (tab) {
			case Toko.TAB_LACI -> gulirLaci;
			case Toko.TAB_LAPORAN -> gulirLaporan;
			case Toko.TAB_RAK -> gulirRak;
			default -> null;
		};
	}

	// =================================================================== drawing

	@Override
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		if (lapak) {
			gambarLapak(l, x0, y0, x1, y1);
			return;
		}
		Lembar t = lembar.sub(Toko.TOKO);
		if (t.kosongSemua()) {
			kosong(l, KotaGambar.t("toko.kosong"), x0, y0, x1, y1);
			return;
		}
		int y = kepala(l, t, x0, y0, x1);
		switch (tab) {
			case Toko.TAB_HARGA -> hargaTab(l, x0, y, x1, y1);
			case Toko.TAB_PEGAWAI -> pegawai(l, x0, y, x1, y1);
			case Toko.TAB_LACI -> laci(l, t, x0, y, x1, y1);
			case Toko.TAB_LAPORAN -> laporan(l, x0, y, x1, y1);
			default -> rak(l, t, x0, y, x1, y1);
		}
	}

	/** Name, reputation bar, open/closed seal and the tax / brand line; returns the y under it. */
	private int kepala(Lukis l, Lembar t, int x0, int y0, int x1) {
		int y = KotaGambar.kota(l, lembar, x0, y0, x1);
		Component n = t.teks(LembarKunci.NAMA);
		l.teks(Brush.bold(n), x0, y, x1 - x0 - 110, l.gaya.aksen);
		boolean buka = t.b(Toko.BUKA);
		Component status = buka ? KotaGambar.t("toko.buka") : KotaGambar.t("toko.tutup");
		l.kanan(status, x1, y, buka ? l.gaya.naik() : l.gaya.turun());
		y += 12;
		Component tutup = t.teksAtau(Toko.TUTUP);
		int rep = t.i(Toko.REPUTASI, 0);
		int lw = Math.min(80, (x1 - x0) / 3);
		l.teks(KotaGambar.t("toko.reputasi", rep), x0, y, lw, l.gaya.teksLemah);
		Bilah.gambar(l, x0 + lw, y + 3, x0 + lw + Math.min(80, (x1 - x0) / 3), rep / 100.0F, rep >= 60 ? 0xFF5FA88C : 0xFFD0A040, new float[] {0.5F});
		int ex = x0 + lw + Math.min(80, (x1 - x0) / 3) + 8;
		Component pajak = KotaGambar.t("toko.pajak", t.i(KotaLembar.PAJAK, 0));
		l.teks(t.b(Toko.MEREK) ? KotaGambar.t("toko.merek", pajak) : pajak, ex, y, x1 - ex, l.gaya.teksLemah);
		y += 12;
		if (tutup != null && !buka) {
			l.teks(tutup, x0, y, x1 - x0, Palette.PERINGATAN);
			y += 11;
		}
		return y + 2;
	}

	// ------------------------------------------------------------------ stacks

	private record Tumpukan(int rak, int slot, Lembar isi) {
	}

	private List<Tumpukan> tumpukan() {
		List<Tumpukan> out = new ArrayList<>();
		for (Lembar r : lembar.daftar(Toko.RAK)) {
			for (Lembar s : r.daftar(Toko.ISI)) {
				out.add(new Tumpukan(r.i(LembarKunci.NO, 0), s.i(LembarKunci.SLOT, 0), s));
			}
		}
		return out;
	}

	private void rak(Lukis l, Lembar t, int x0, int y0, int x1, int y1) {
		List<Tumpukan> semua = tumpukan();
		int jumlahRak = lembar.daftar(Toko.RAK).size();
		if (semua.isEmpty()) {
			kosong(l, KotaGambar.t("toko.rak_kosong", jumlahRak), x0, y0, x1, y1);
			return;
		}
		int bawah = y1 - 12;
		long batas = t.l(KotaLembar.BATAS, 0L);
		if (batas > 0) {
			long pakai = t.l(KotaLembar.TERPAKAI, 0L);
			Bilah.gambar(l, x0, y1 - 8, x1, pakai / (float) batas, pakai >= batas ? 0xFFD0603A : 0xFF5FA88C, null);
			l.teks(KotaGambar.t("toko.batas", Brush.angka(pakai), Brush.angka(batas)), x0, y1 - 20, x1 - x0, l.gaya.teksSamar);
			bawah = y1 - 22;
		}
		Kotak[] b = bagi(x0, y0, x1, bawah);
		Kotak kiri = b[0];
		dRak.gambar(l, "rak", kiri.x0(), kiri.y0(), kiri.x1(), kiri.y1(), semua.size(), 30, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Tumpukan s = semua.get(i);
			Lembar e = s.isi();
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			ItemStack item = e.barang();
			if (!item.isEmpty()) {
				lk.g.item(item, rx0 + 4, ry0 + 7);
			}
			lk.teks(KotaGambar.t("toko.rak_baris", s.rak(), e.teks(LembarKunci.NAMA), e.i(LembarKunci.JUMLAH, 0)), rx0 + 24, ry0 + 3, rx1 - rx0 - 28, lk.gaya.teks);
			LabelHarga.gambar(lk, e.l(LembarKunci.HARGA, 0L), -1L, rx0 + 24, ry0 + 16, false, false);
			meter(lk, e.f(Toko.H, 1.0F), rx1 - 74, ry0 + 18, 40);
			lk.kanan(KotaGambar.t("toko.laku", e.i(Toko.LAKU, 0)), rx1 - 4, ry0 + 16, lk.gaya.teksLemah);
		}, null, i -> {
			Tumpukan s = semua.get(i);
			String id = s.rak() + ":" + s.slot();
			if (!id.equals(idHarga)) {
				idHarga = id;
				harga.set(Math.max(1, (int) Math.min(Integer.MAX_VALUE, s.isi().l(LembarKunci.HARGA, 1L))));
				kirim(LembarKunci.op(KotaLembar.OP_PILIH, s.rak(), s.slot()));
			}
		});
		Kotak kanan = b[1];
		l.sumur(kanan.x0(), kanan.y0(), kanan.x1(), kanan.y1());
		int sel = dRak.pilihan();
		Tumpukan s = sel >= 0 && sel < semua.size() ? semua.get(sel) : null;
		if (s == null) {
			return;
		}
		if (idHarga.isEmpty()) {
			idHarga = s.rak() + ":" + s.slot();
			harga.set(Math.max(1, (int) Math.min(Integer.MAX_VALUE, s.isi().l(LembarKunci.HARGA, 1L))));
		}
		Lembar r = lembar.sub(KotaLembar.RINCI);
		boolean cocok = !r.kosongSemua() && r.i("rak", -1) == s.rak() && r.i(LembarKunci.SLOT, -1) == s.slot();
		Lembar d = cocok ? r : s.isi();
		int ink = l.gaya.teksSumur();
		int dx = kanan.x0() + 5;
		int dy = gulirRak.mulai(l, kanan.x0() + 1, kanan.y0() + 1, kanan.x1() - 6, kanan.y1() - 1) + 3;
		int xr = kanan.x1() - 10;
		ItemStack item = s.isi().barang();
		if (!item.isEmpty()) {
			l.g.pose().pushMatrix();
			l.g.pose().translate(dx, dy);
			l.g.pose().scale(2.0F, 2.0F);
			l.g.item(item, 0, 0);
			l.g.pose().popMatrix();
		}
		l.teks(Brush.bold(s.isi().teks(LembarKunci.NAMA)), dx + 36, dy + 2, xr - dx - 36, ink);
		l.teks(KotaGambar.t("toko.nilai", Brush.angka(d.l(LembarKunci.NILAI, s.isi().l(LembarKunci.NILAI, 0L)))), dx + 36, dy + 14, xr - dx - 36, Palette.INK_FAINT);
		dy += 38;
		float faktor = d.f(Toko.FAKTOR, 1.0F);
		int tren = d.i("tren", 0);
		IkonKota.gambar(l.g, IkonKota.tren(tren), dx, dy - 1, 10);
		l.teks(KotaGambar.t("toko.faktor", String.format(java.util.Locale.ROOT, "%.2f", faktor)), dx + 12, dy, xr - dx - 12, ink);
		dy += 12;
		l.teks(KotaGambar.t("toko.laku_jam", d.i(Toko.LAKU, 0)), dx, dy, xr - dx, ink);
		dy += 12;
		long pasar = d.l(Toko.HARGA_PASAR, 0L);
		long maks = Math.max(1L, d.l(Toko.HARGA_MAKS, Integer.MAX_VALUE));
		l.teks(KotaGambar.t("toko.pasar", Brush.angka(pasar), Brush.angka(maks)), dx, dy, xr - dx, Palette.INK_FAINT);
		dy += 14;
		int mx = (int) Math.min(Integer.MAX_VALUE, maks);
		int pw = harga.gambar(l, "harga_n", dx, dy, mx);
		int bx = dx + pw + 4;
		Component pasang = KotaGambar.t("toko.pasang");
		int w1 = lebarTombol(l, pasang, 50, 90);
		tulis(l, "pasang", bx, dy, w1, 14, pasang, true, null, LembarKunci.op(Toko.OP_HARGA, s.rak(), s.slot(), harga.nilai()));
		dy += 17;
		Component hp = KotaGambar.t("toko.harga_pasar");
		int w2 = lebarTombol(l, hp, 60, xr - dx);
		tulis(l, "harga_pasar", dx, dy, w2, 14, hp, true, null, LembarKunci.op(Toko.OP_HARGA_PASAR, s.rak(), s.slot()));
		gulirRak.selesai(l, dy + 18);
	}

	/** A price-against-value meter: green under value, gold near, red above (0.5-1.5). */
	private void meter(Lukis l, float h, int x, int y, int w) {
		float f = Math.max(0.0F, Math.min(1.0F, (h - 0.5F)));
		int warna = h < 0.9F ? 0xFF5FA88C : h <= 1.15F ? 0xFFD0A040 : 0xFFD0603A;
		Bilah.gambar(l, x, y, x + w, f, warna, new float[] {0.5F});
		if (l.di(x, y - 2, x + w, y + 6)) {
			l.tip(KotaGambar.t("toko.h_tip", Math.round(h * 100)));
		}
	}

	// ------------------------------------------------------------------ price list

	private void hargaTab(Lukis l, int x0, int y0, int x1, int y1) {
		List<Tumpukan> semua = tumpukan();
		if (semua.isEmpty()) {
			kosong(l, KotaGambar.t("toko.rak_kosong", lembar.daftar(Toko.RAK).size()), x0, y0, x1, y1);
			return;
		}
		dHarga.gambar(l, "harga", x0, y0, x1, y1, semua.size(), 24, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = semua.get(i).isi();
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			ItemStack item = e.barang();
			if (!item.isEmpty()) {
				lk.g.item(item, rx0 + 3, ry0 + 4);
			}
			lk.teks(KotaGambar.t("toko.rak_baris", semua.get(i).rak(), e.teks(LembarKunci.NAMA), e.i(LembarKunci.JUMLAH, 0)), rx0 + 22, ry0 + 8, rx1 - rx0 - 190, lk.gaya.teks);
			meter(lk, e.f(Toko.H, 1.0F), rx1 - 160, ry0 + 10, 40);
			lk.kanan(KotaGambar.t("toko.laku", e.i(Toko.LAKU, 0)), rx1 - 90, ry0 + 8, lk.gaya.teksLemah);
			LabelHarga.gambar(lk, e.l(LembarKunci.HARGA, 0L), -1L, rx1 - 4, ry0 + 8, true, false);
		}, null, null);
	}

	// ------------------------------------------------------------------ staff

	private void pegawai(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Toko.PEGAWAI);
		int bawah = y1 - 17;
		boolean adaMurid = false;
		for (Lembar r : rows) {
			adaMurid |= r.b(Toko.MURID) && r.b(Toko.DIPEKERJAKAN);
		}
		final boolean sudahMurid = adaMurid;
		Component murid = KotaGambar.t("toko.tugaskan_murid");
		int mw = lebarTombol(l, murid, 80, 160);
		tulis(l, "murid", x0, bawah + 3, mw, 14, murid, !sudahMurid, sudahMurid ? KotaGambar.t("toko.murid_sudah") : null, Toko.OP_PEKERJAKAN_MURID);
		if (rows.isEmpty()) {
			kosong(l, KotaGambar.t("toko.pegawai_kosong"), x0, y0, x1, bawah);
			return;
		}
		dPegawai.gambar(l, "pegawai", x0, y0, x1, bawah, rows.size(), 40, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			boolean kerja = e.b(Toko.DIPEKERJAKAN);
			boolean muridIni = e.b(Toko.MURID);
			if (e.ada(KotaLembar.WARGA)) {
				KotaGambar.warga(lk, e, rx0, ry0, rx1, ry1, sorot, pilih, 96);
			} else {
				lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
				Potret.gambar(lk, rx0 + 2, ry0 + 4, 32, -1, -1, false, 0.0F);
				lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 40, ry0 + 4, rx1 - rx0 - 140, lk.gaya.teks);
				lk.teks(KotaGambar.t("toko.murid"), rx0 + 40, ry0 + 15, rx1 - rx0 - 140, lk.gaya.teksLemah);
			}
			lk.teks(muridIni ? KotaGambar.t("toko.tanpa_gaji") : KotaGambar.t("toko.gaji_n", Brush.angka(e.l(Toko.GAJI, 0L))), rx1 - 96, ry0 + 4, 92, KotaGambar.EMAS);
			Component watak = e.teksAtau(KotaLembar.WATAK_NAMA);
			if (watak != null) {
				lk.teks(KotaGambar.t("toko.watak_ahli", watak, e.i(KotaLembar.AHLI, 0)), rx1 - 96, ry0 + 15, 92, lk.gaya.teksSamar);
			}
			String op = kerja ? LembarKunci.op(Toko.OP_BERHENTIKAN, e.id().isEmpty() ? Integer.toString(e.i(KotaLembar.WARGA, 0)) : e.id())
					: muridIni ? Toko.OP_PEKERJAKAN_MURID : LembarKunci.op(Toko.OP_PEKERJAKAN, e.i(KotaLembar.WARGA, 0));
			tulis(lk, "pegawai#" + i, rx1 - 96, ry1 - 17, 80, 14, KotaGambar.t(kerja ? "toko.berhentikan" : "toko.pekerjakan"), true, null, op);
		}, null, null);
	}

	// ------------------------------------------------------------------ till

	private void laci(Lukis l, Lembar t, int x0, int y0, int x1, int y1) {
		int xr = x1 - 6;
		int y = gulirLaci.mulai(l, x0, y0, xr, y1);
		IkonKota.gambar(l.g, IkonKota.LACI, x0, y, 24);
		l.teks(KotaGambar.t("toko.laci"), x0 + 30, y, xr - x0 - 30, l.gaya.teksLemah);
		LabelHarga.gambar(l, t.l(Toko.LACI, 0L), -1L, x0 + 30, y + 12, false, false);
		y += 30;
		Component ambil = KotaGambar.t("toko.ambil_laci");
		int w = lebarTombol(l, ambil, 80, 140);
		tulis(l, "ambil", x0, y, w, 14, ambil, t.l(Toko.LACI, 0L) > 0, t.l(Toko.LACI, 0L) > 0 ? null : KotaGambar.t("toko.laci_kosong"), Toko.OP_AMBIL_LACI);
		y += 20;
		boolean buka = t.b(Toko.BUKA);
		Component bk = KotaGambar.t(buka ? "toko.tutup_toko" : "toko.buka_toko");
		w = lebarTombol(l, bk, 80, 140);
		tulis(l, "buka", x0, y, w, 14, bk, true, null, LembarKunci.op(Toko.OP_BUKA, buka ? 0 : 1));
		y += 20;
		boolean ke = t.b(Toko.KE_BANK);
		Component kb = KotaGambar.t(ke ? "toko.ke_bank_ya" : "toko.ke_bank_tidak");
		w = lebarTombol(l, kb, 80, 200);
		tulis(l, "ke_bank", x0, y, w, 14, kb, true, null, LembarKunci.op(Toko.OP_KE_BANK, ke ? 0 : 1));
		y += 22;
		y += l.kepala(KotaGambar.t("toko.nama_toko"), x0, y, xr);
		nama.x = x0;
		nama.y = y;
		nama.w = Math.max(60, xr - x0);
		nama.h = 14;
		l.widget("nama", nama);
		y += 18;
		l.teks(KotaGambar.t("toko.nama_info", Toko.NAMA_MAKS), x0, y, xr - x0, l.gaya.teksSamar);
		y += 13;
		gulirLaci.selesai(l, y);
	}

	// ------------------------------------------------------------------ report

	private void laporan(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar r = lembar.sub(Toko.LAPORAN);
		if (r.kosongSemua()) {
			kosong(l, KotaGambar.t("toko.laporan_kosong"), x0, y0, x1, y1);
			return;
		}
		int xr = x1 - 6;
		int y = gulirLaporan.mulai(l, x0, y0, xr, y1);
		List<Lembar> hari = r.daftar(LembarKunci.HARI);
		long[] nb = new long[hari.size()];
		long terjual = 0;
		long pembeli = 0;
		for (int i = 0; i < hari.size(); i++) {
			nb[i] = hari.get(i).l(LembarKunci.NB, 0L);
			terjual += hari.get(i).i(Toko.TERJUAL, 0);
			pembeli += hari.get(i).i(Toko.PEMBELI, 0);
		}
		y += l.kepala(KotaGambar.t("toko.tujuh_hari"), x0, y, xr);
		Bilah.grafik7(l, nb, x0, y, xr, y + 44);
		y += 50;
		y += KotaGambar.baris(l, KotaGambar.t("toko.pembeli"), KotaGambar.t("toko.pembeli_n", pembeli, terjual), x0, y, xr, l.gaya.teks);
		y += KotaGambar.baris(l, KotaGambar.t("toko.keluhan"), Component.literal(Integer.toString(r.i(Toko.KELUHAN, 0))), x0, y, xr,
				r.i(Toko.KELUHAN, 0) > 0 ? Palette.PERINGATAN : l.gaya.teks);
		y += KotaGambar.baris(l, KotaGambar.t("toko.pencurian"), Component.literal(Integer.toString(r.i(Toko.PENCURIAN, 0))), x0, y, xr,
				r.i(Toko.PENCURIAN, 0) > 0 ? Palette.BAHAYA : l.gaya.teks);
		y += 3;
		y += l.kepala(KotaGambar.t("toko.terlaris"), x0, y, xr);
		List<Lembar> barang = r.daftar(LembarKunci.BARANG);
		if (barang.isEmpty()) {
			l.teks(KotaGambar.t("toko.terlaris_kosong"), x0, y, xr - x0, l.gaya.teksSamar);
			y += 13;
		}
		for (Lembar b : barang) {
			ItemStack item = b.barang();
			if (!item.isEmpty()) {
				l.g.item(item, x0, y - 3);
			}
			l.teks(b.teks(LembarKunci.NAMA), x0 + 20, y, xr - x0 - 120, l.gaya.teks);
			l.kanan(KotaGambar.t("toko.terjual_n", b.i(Toko.TERJUAL, 0), Brush.angka(b.l(LembarKunci.NB, 0L))), xr, y, l.gaya.teksLemah);
			y += 17;
		}
		gulirLaporan.selesai(l, y);
	}

	// ------------------------------------------------------------------ lapak_sewa

	private void gambarLapak(Lukis l, int x0, int y0, int x1, int y1) {
		int y = KotaGambar.kota(l, lembar, x0, y0, x1);
		List<Lembar> rows = lembar.daftar(LembarKunci.DAFTAR);
		if (rows.isEmpty()) {
			kosong(l, KotaGambar.t("lapak.kosong"), x0, y, x1, y1);
			return;
		}
		int bawah = y1 - 18;
		dLapak.gambar(l, "lapak", x0, y, x1, bawah, rows.size(), 36, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			IkonKota.gambar(lk.g, IkonKota.USAHA, rx0 + 4, ry0 + 9, 16);
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 26, ry0 + 3, rx1 - rx0 - 120, lk.gaya.teks);
			String keadaan = e.s(LembarKunci.KEADAAN);
			boolean anda = e.b(LembarKunci.ANDA);
			lk.kanan(anda ? KotaGambar.t("lapak.milikmu") : KotaGambar.t("lapak.keadaan." + keadaan), rx1 - 5, ry0 + 3, anda ? KotaGambar.EMAS : lk.gaya.teksLemah);
			Component penyewa = e.teksAtau(LapakSewa.PENYEWA);
			Component baris = penyewa != null && !anda ? KotaGambar.t("lapak.disewa", penyewa, e.l(KotaLembar.SAMPAI, 0L))
					: anda ? KotaGambar.t("lapak.sampai", e.l(KotaLembar.SAMPAI, 0L)) : KotaGambar.t("lapak.distrik." + e.s(LembarKunci.DISTRIK, "pasar"));
			lk.teks(baris, rx0 + 26, ry0 + 14, rx1 - rx0 - 120, lk.gaya.teksLemah);
			LabelHarga.gambar(lk, e.l(LembarKunci.HARGA, 0L), -1L, rx1 - 4, ry0 + 22, true, false);
		}, null, i -> idLapak = rows.get(i).id());
		int sel = dLapak.pilihan();
		if (sel < 0 || sel >= rows.size()) {
			return;
		}
		Lembar e = rows.get(sel);
		String id = e.id();
		boolean anda = e.b(LembarKunci.ANDA);
		boolean kosong = LembarKunci.K_OK.equals(e.s(LembarKunci.KEADAAN)) || "kosong".equals(e.s(LembarKunci.KEADAAN));
		int by = y1 - 16;
		int bx = x0;
		if (!anda) {
			for (int mgg : new int[] {1, 4}) {
				Component c = KotaGambar.t("lapak.sewa_n", mgg);
				int w = lebarTombol(l, c, 60, 100);
				tulis(l, "sewa#" + mgg, bx, by, w, 14, c, kosong || e.b(LembarKunci.BISA), e.teksAtau(LembarKunci.ALASAN), LembarKunci.op(LapakSewa.OP_SEWA, id, mgg));
				bx += w + 3;
			}
		} else {
			for (int mgg : new int[] {1, 4}) {
				Component c = KotaGambar.t("lapak.perpanjang_n", mgg);
				int w = lebarTombol(l, c, 70, 110);
				tulis(l, "perpanjang#" + mgg, bx, by, w, 14, c, true, null, LembarKunci.op(LapakSewa.OP_PERPANJANG, id, mgg));
				bx += w + 3;
			}
			Component toko = KotaGambar.t("lapak.buka_toko");
			int w = lebarTombol(l, toko, 70, 120);
			tulis(l, "toko", bx, by, w, 14, toko, true, null, LembarKunci.op(LapakSewa.OP_TOKO, id));
			bx += w + 3;
			Component lepas = KotaGambar.t("lapak.lepas");
			w = lebarTombol(l, lepas, 50, 90);
			String op = LembarKunci.op(LapakSewa.OP_LEPAS, id);
			boolean baca = !tulisBoleh();
			aksi(l, "lepas", bx, by, w, 14, lepas, !baca, baca ? alasanBaca() : null, () -> konfirmasi(SegelKonfirmasi.Tingkat.RINGAN,
					KotaGambar.t("lapak.lepas_yakin", e.teks(LembarKunci.NAMA)), () -> kirim(op)));
			bx += w + 3;
		}
		if (bx + 48 < x1) {
			tunjuk(l, "tunjuk", bx, by, 48, 14, e.i(LembarKunci.X, 0), e.i(LembarKunci.Z, 0));
		}
	}
}
