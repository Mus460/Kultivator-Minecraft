package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.CincinWaktu;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KartuBarang;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.PapanSkor;
import dev.kultivator.client.screen.ui.Pratinjau3D;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Lelang;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * {@code lelang} (DESIGN 38.U.3.3, G20): the one auction of the mod: the town Balai Lelang, the capital's Lelang Agung and,
 * through {@code LelangJembatan}, Keabadian › Lelang. Sub "Lot": the lot (number of total, name, seller, base price, the
 * turning preview or a sealed mystery box), the current price rolling up with its leader, the lot's countdown ring, the bid
 * board (newest first, a new bid flashes gold), and bid buttons for each step (Space = the first step). States: not yet
 * started (schedule and countdown), between lots, sold (the red "TERJUAL" seal spins in). Sub "Titip": the item in hand
 * with its estimate and three reserve choices (or, {@link #TETAP}, one "Titipkan" at the house's fixed value), and the
 * consigned items with their status. Sub "Hasil": won lots, consignment income and what is owed. A sheet in another
 * currency ({@link #MATA_UANG} {@link #BATU_ASAL}) shows its prices as that currency, not as Nilai Batu. Single kit, text
 * 1.0 (the price 1.5).
 */
public final class LayarLelang extends LayarInti {

	/** Client-side sheet key (bridged auctions): the currency of every price; absent = Nilai Batu. */
	public static final String MATA_UANG = "mata_uang";
	/** {@link #MATA_UANG} of the Immortal auction. */
	public static final String BATU_ASAL = "batu_asal";
	/** Titip key: the house sets one value per item (one "Titipkan" button, no estimate or reserve choice). */
	public static final String TETAP = "tetap";

	private final Pratinjau3D pratinjau = new Pratinjau3D();
	private final PapanSkor papan = new PapanSkor();
	private final LabelHarga.Gulir hargaGulir = new LabelHarga.Gulir();
	private final DaftarGulir daftarTitip = new DaftarGulir();
	private final DaftarGulir daftarHasil = new DaftarGulir();
	private int lotTerakhir = -1;
	private long terjualSejak = -1L;
	private Supplier<Component> kunci = () -> null;

	public LayarLelang(int npcId, CompoundTag lembar) {
		super(Lelang.LAYAR, npcId, lembar, k("layar.lelang"), LembarKunci.GAYA_KAIN);
	}

	/** The page lock of a bridged auction (Keabadian › Lelang opens after the ascent, like the old card). */
	public LayarLelang kunciDari(Supplier<Component> kunci) {
		this.kunci = kunci;
		return this;
	}

	@Override
	public @Nullable Component kunci() {
		return kunci.get();
	}

	@Override
	protected List<Tab> daftarTab() {
		return List.of(tab(Lelang.TAB_LOT, "tab.lot", LembarKunci.Ikon.LELANG), tab(Lelang.TAB_TITIP, "tab.titip", LembarKunci.Ikon.TIMBANGAN),
				tab(Lelang.TAB_HASIL, "tab.hasil", Glyphs.Atlas.GULUNGAN));
	}

	@Override
	public boolean lebar() {
		return false;
	}

	@Override
	protected void setelahLembar(boolean buka) {
		Lembar lot = lembar.sub(Lelang.LOT);
		int no = lot.i(LembarKunci.NO, -1);
		if (no != lotTerakhir) {
			lotTerakhir = no;
			terjualSejak = -1L;
		}
		if (lot.b(Lelang.TERJUAL) && terjualSejak < 0L) {
			terjualSejak = Util.getMillis();
			UiSound.ask();
		}
	}

	@Override
	protected Component petunjuk() {
		return Lelang.TAB_LOT.equals(tab) ? k("kunci.tawar") : null;
	}

	// =================================================================== currency

	private boolean batuAsal() {
		return BATU_ASAL.equals(lembar.s(MATA_UANG));
	}

	/** Money on hand in the sheet's currency (-1 = unknown). */
	private long dompet() {
		return lembar.l(LembarKunci.UANG, -1L);
	}

	/** The Gulungan's purse is Nilai Batu: another currency is shown in the strip instead. */
	@Override
	public long uang() {
		return batuAsal() ? -1L : super.uang();
	}

	/** An amount in the sheet's currency as text ("120 NB" / "120 Batu Asal"). */
	private Component jumlah(long n, boolean pendek) {
		return batuAsal() ? m("lelang.batu_asal", pendek ? Brush.singkat(n) : Brush.angka(n)) : Brush.nb(n, pendek);
	}

	/** A price at (x, y) in the sheet's currency; returns its width. */
	private int harga(Lukis l, long n, long punya, float x, float y, boolean kanan) {
		if (!batuAsal()) {
			return LabelHarga.gambar(l, n, punya, x, y, kanan, false);
		}
		Component t = jumlah(n, false);
		int w = l.font.width(t);
		int warna = punya >= 0L && n > punya ? l.gaya.turun() : l.gaya.teks;
		l.teks(t, kanan ? x - w : x, y, w + 1, warna);
		return w;
	}

	// =================================================================== keys

	@Override
	protected boolean spasi() {
		if (!Lelang.TAB_LOT.equals(tab) || !bolehTawar()) {
			return false;
		}
		int[] langkah = lembar.ia(Lelang.LANGKAH);
		kirim(LembarKunci.op(Lelang.OP_TAWAR, langkah.length > 0 ? langkah[0] : 5));
		return true;
	}

	private boolean bolehTawar() {
		Lembar lot = lembar.sub(Lelang.LOT);
		return Lelang.S_BERJALAN.equals(lembar.s(LembarKunci.STATUS)) && lot.ada(LembarKunci.NO) && !lot.b(Lelang.TERJUAL) && !lot.b(LembarKunci.ANDA)
				&& tulisBoleh();
	}

	// =================================================================== drawing

	@Override
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		switch (tab) {
			case Lelang.TAB_TITIP -> titip(l, x0, y0, x1, y1);
			case Lelang.TAB_HASIL -> hasil(l, x0, y0, x1, y1);
			default -> lot(l, x0, y0, x1, y1);
		}
	}

	private void lot(Lukis l, int x0, int y0, int x1, int y1) {
		String status = lembar.s(LembarKunci.STATUS, Lelang.S_BELUM);
		long sisa = lembar.l(LembarKunci.SISA_TICK, 0L);
		boolean agung = Lelang.PROFIL_AGUNG.equals(lembar.s(Lelang.PROFIL));
		if (Lelang.S_BELUM.equals(status) || Lelang.S_SELESAI.equals(status)) {
			float cx = (x0 + x1) / 2.0F;
			Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.LELANG, Math.round(cx - 16), y0 + 10, 32, 0xFFFFFFFF);
			Component judul = k(Lelang.S_BELUM.equals(status) ? "lelang.belum" : "lelang.selesai");
			l.tengah(Brush.bold(judul), cx, y0 + 48, x1 - x0 - 8, l.gaya.teks);
			Component jadwal = lembar.teksAtau(Lelang.JADWAL);
			if (jadwal != null) {
				l.tengah(jadwal, cx, y0 + 60, x1 - x0 - 20, l.gaya.teksLemah);
			}
			if (Lelang.S_BELUM.equals(status) && sisa > 0) {
				CincinWaktu.gambar(l, cx, y0 + 92, 16.0F, sisa, 0L, diterima, CincinWaktu.DALAM);
			}
			if (agung) {
				l.samar(k("lelang.komisi", Math.round(lembar.f(Lelang.KOMISI, 0.1F) * 100)), x0 + 4, y1 - 10, x1 - x0 - 8);
			}
			return;
		}
		Lembar lot = lembar.sub(Lelang.LOT);
		Kotak[] k = bagi(x0, y0, x1, y1);
		boolean sempit = tata() == Tata.SEMPIT;
		// left: lot card
		Kotak kiri = sempit ? new Kotak(x0, y0, x1, y0 + 46) : k[0];
		int lx0 = kiri.x0();
		int ly = kiri.y0();
		l.teks(Brush.bold(k("lelang.lot_no", lot.i(LembarKunci.NO, 1), lot.i(Lelang.TOTAL, 1))), lx0, ly, kiri.w(), Palette.GOLD_LIGHT);
		ly += 11;
		l.teks(Brush.bold(lot.teks(LembarKunci.NAMA)), lx0, ly, kiri.w(), mutu(l, lot.i(LembarKunci.MUTU, -1)));
		ly += 11;
		Component penjual = lot.teksAtau(Lelang.PENJUAL);
		if (penjual != null && !sempit) {
			l.teks(k("lelang.penjual", penjual), lx0, ly, kiri.w(), l.gaya.teksLemah);
			ly += 11;
		}
		l.teks(m("lelang.dasar", jumlah(lot.l(Lelang.DASAR, 0L), false)), lx0, ly, kiri.w(), l.gaya.teksLemah);
		ly += 12;
		int pv = sempit ? 0 : Math.min(kiri.w() - 8, Math.min(kiri.y1() - ly - 4, tata() == Tata.LEBAR ? 110 : 80));
		if (pv >= 32) {
			int px = lx0 + (kiri.w() - pv) / 2;
			if (lot.b(Lelang.MISTERI)) {
				kotakMisteri(l, px, ly, pv);
			} else {
				pratinjau.gambar(l, "pratinjau", px, ly, px + pv, ly + pv, lot.barang(), null, LembarKunci.Ikon.LELANG);
			}
			if (lot.b(Lelang.TERJUAL)) {
				float t = (Util.getMillis() - terjualSejak) / 350.0F;
				KartuBarang.cap(l, k("lelang.terjual"), px + pv / 2.0F, ly + pv / 2.0F, pv * 0.9F, t);
			}
		}
		// right: price, leader, ring, bid board, bid buttons
		Kotak kanan = sempit ? new Kotak(x0, y0 + 48, x1, y1) : k[1];
		l.kartu(kanan.x0(), kanan.y0(), kanan.x1(), kanan.y1(), 0.0F, false);
		int rx0 = kanan.x0() + 5;
		int rx1 = kanan.x1() - 5;
		int ry = kanan.y0() + 4;
		long kini = lot.l(Lelang.KINI, lot.l(Lelang.DASAR, 0L));
		long shown = hargaGulir.nilai(kini, l.kini);
		l.teks(k("lelang.harga_kini"), rx0, ry, rx1 - rx0 - 30, l.gaya.teksLemah);
		long lotSisa = lot.l(LembarKunci.SISA_TICK, sisa);
		if (!lot.b(Lelang.TERJUAL)) {
			CincinWaktu.gambar(l, rx1 - 12, ry + 12, 12.0F, lotSisa, 0L, diterima, CincinWaktu.DALAM);
		}
		ry += 11;
		// the price at title size (1.5), gold while it rolls
		l.judul(jumlah(shown, false), rx0, ry, rx1 - rx0 - 30, hargaGulir.bergulir(l.kini) ? Palette.GOLD_LIGHT : l.gaya.teks);
		ry += 16;
		Component pemimpin = lot.teksAtau(Lelang.PEMIMPIN);
		if (lot.b(LembarKunci.ANDA)) {
			l.teks(k("lelang.anda_memimpin"), rx0, ry, rx1 - rx0, l.gaya.naik());
		} else if (pemimpin != null) {
			l.teks(k("lelang.oleh", pemimpin), rx0, ry, rx1 - rx0, l.gaya.teksLemah);
		}
		ry += 12;
		if (Lelang.S_JEDA.equals(status)) {
			l.samar(k("lelang.jeda", Brush.waktu(CincinWaktu.sisa(sisa, diterima, l.kini))), rx0, ry, rx1 - rx0);
			ry += 11;
		}
		ry += l.kepala(k("lelang.papan"), rx0, ry, rx1);
		List<Lembar> tawaran = lembar.daftar(Lelang.TAWARAN);
		int by = kanan.y1() - TOMBOL_H - 4;
		int rows = Math.max(1, (by - 2 - ry) / 11);
		if (tawaran.isEmpty()) {
			l.samar(k("lelang.belum_ada_tawaran"), rx0, ry + 2, rx1 - rx0);
		} else {
			papan.gambar(l, tawaran, LembarKunci.NB, rx0, ry, rx1, 11, Math.min(8, rows), !batuAsal());
		}
		// bid buttons
		int[] langkah = lembar.ia(Lelang.LANGKAH);
		if (langkah.length == 0) {
			langkah = new int[] {5, 10, 25};
		}
		int bw = (rx1 - rx0 - (langkah.length - 1) * 3) / langkah.length;
		boolean boleh = bolehTawar();
		long min = lembar.l(Lelang.TAWAR_MIN, kini);
		long punya = dompet();
		for (int i = 0; i < langkah.length; i++) {
			long tawar = Math.max(min, Math.round(kini * (1.0D + langkah[i] / 100.0D)));
			boolean cukup = punya < 0 || tawar <= punya;
			Component alasan = !boleh ? (lot.b(LembarKunci.ANDA) ? k("lelang.anda_memimpin") : k("lelang.tidak_bisa"))
					: m("lelang.kurang", jumlah(tawar - Math.max(0L, punya), false));
			Component label = k("lelang.tawar", langkah[i], Brush.singkat(tawar));
			tombolOp(l, "tawar" + i, rx0 + i * (bw + 3), by, bw, TOMBOL_H, label, boleh && cukup, alasan, LembarKunci.op(Lelang.OP_TAWAR, langkah[i]));
		}
	}

	private void kotakMisteri(Lukis l, int x, int y, int size) {
		float bob = (float) Math.sin(Util.getMillis() / 400.0D) * 1.5F;
		int s = Math.round(size * 0.7F);
		int bx = x + (size - s) / 2;
		int by = Math.round(y + (size - s) / 2.0F + bob);
		l.g.fill(bx + 2, by + 3, bx + s + 2, by + s + 3, 0x50000000);
		l.g.fillGradient(bx, by, bx + s, by + s, 0xFF5A1E1E, 0xFF2E0E0E);
		l.g.fill(bx + s / 2 - 2, by, bx + s / 2 + 2, by + s, Palette.GOLD);
		l.g.fill(bx, by + s / 2 - 2, bx + s, by + s / 2 + 2, Palette.GOLD);
		Brush.frame(l.g, bx, by, bx + s, by + s, Palette.GOLD_DARK);
		Glyphs.Atlas.ikon(l.g, Glyphs.Atlas.TANYA, bx + s / 2 - 8, by + s / 2 - 8, 16, 0xFFFFFFFF);
		if (l.di(bx, by, bx + s, by + s)) {
			l.tip(k("lelang.misteri"));
		}
	}

	private void titip(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar titip = lembar.sub(Lelang.TITIP);
		boolean tetap = titip.b(TETAP);
		Kotak[] k = bagi(x0, y0, x1, y1);
		Kotak kiri = k[0];
		int y = kiri.y0() + l.kepala(k("lelang.titip_judul"), kiri.x0(), kiri.y0(), kiri.x1());
		Lembar tangan = titip.sub(LembarKunci.TANGAN);
		boolean bisa = titip.b(LembarKunci.BISA);
		int bawah = kiri.y1() - 11;
		if (tangan.ada(LembarKunci.NAMA)) {
			if (!tangan.barang().isEmpty()) {
				l.g.item(tangan.barang(), kiri.x0(), y);
			}
			l.teks(tangan.teks(LembarKunci.NAMA), kiri.x0() + 20, y + 4, kiri.w() - 20, l.gaya.teks);
			y += 20;
			long kira = tangan.l(Lelang.PERKIRAAN, 0L);
			if (tetap) {
				// the house values every item itself: one button
				l.bungkus(m("lelang.tetap"), kiri.x0(), y, kiri.w(), l.gaya.teksSamar, Math.max(9, bawah - TOMBOL_H - 4 - y));
				tombolOp(l, "titip", kiri.x0(), bawah - TOMBOL_H - 2, kiri.w(), TOMBOL_H, m("lelang.titipkan"), bisa, titip.teksAtau(LembarKunci.ALASAN),
						LembarKunci.op(Lelang.OP_TITIP, kira));
			} else {
				y += nilai(l, k("lelang.perkiraan"), jumlah(kira, false), kiri.x0(), y, kiri.x1());
				l.samar(k("lelang.pilih_dasar"), kiri.x0(), y + 1, kiri.w());
				y += 12;
				int[] persen = {80, 100, 120};
				for (int i = 0; i < persen.length && y + TOMBOL_H <= bawah; i++) {
					long dasar = Math.max(1L, kira * persen[i] / 100L);
					tombolOp(l, "titip" + i, kiri.x0(), y, kiri.w(), TOMBOL_H, k("lelang.titip_dasar", persen[i], Brush.angka(dasar)), bisa,
							titip.teksAtau(LembarKunci.ALASAN), LembarKunci.op(Lelang.OP_TITIP, dasar));
					y += TOMBOL_H + 2;
				}
			}
		} else {
			l.bungkus(titip.ada(LembarKunci.ALASAN) ? titip.teks(LembarKunci.ALASAN) : k("lelang.pegang_barang"), kiri.x0(), y + 2, kiri.w(),
					l.gaya.teksSamar, bawah - 2 - y);
		}
		List<Lembar> daftar = titip.daftar(LembarKunci.DAFTAR);
		l.samar(k("lelang.titip_maks", daftar.size(), titip.i(LembarKunci.MAKS, 3)), kiri.x0(), kiri.y1() - 9, kiri.w());
		// right: consigned items
		Kotak kanan = k[1];
		int ry = kanan.y0() + l.kepala(k("lelang.titipan"), kanan.x0(), kanan.y0(), kanan.x1());
		if (daftar.isEmpty()) {
			kosong(l, k("lelang.titipan_kosong"), kanan.x0(), ry, kanan.x1(), kanan.y1());
			return;
		}
		daftarTitip.gambar(l, "titipan", kanan.x0(), ry, kanan.x1(), kanan.y1(), daftar.size(), 25, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = daftar.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			if (!e.barang().isEmpty()) {
				lk.g.item(e.barang(), rx0 + 3, ry0 + 4);
			}
			String st = e.s(LembarKunci.STATUS, Lelang.T_ANTRE);
			int bw = Lelang.T_ANTRE.equals(st) ? 56 : 0;
			lk.teks(e.teks(LembarKunci.NAMA), rx0 + 22, ry0 + 3, rx1 - rx0 - 26 - bw, lk.gaya.teks);
			int sc = switch (st) {
				case Lelang.T_TERJUAL -> lk.gaya.naik();
				case Lelang.T_KEMBALI -> lk.gaya.turun();
				case Lelang.T_DILELANG -> Palette.GOLD_DARK;
				default -> lk.gaya.teksSamar;
			};
			Component status = st.equals(Lelang.T_TERJUAL) ? m("lelang.terjual", jumlah(e.l(Lelang.HASIL, 0L), false)) : k("lelang.status." + st);
			lk.teks(status, rx0 + 22, ry0 + 14, rx1 - rx0 - 26 - bw, sc);
			if (bw > 0) {
				tombolOp(lk, "tarik" + i, rx1 - bw - 4, ry0 + (ry1 - ry0 - TOMBOL_H) / 2, bw, TOMBOL_H, k("lelang.tarik"), true, null,
						LembarKunci.op(Lelang.OP_TARIK, e.id()));
			}
		}, null, null);
	}

	private void hasil(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> hasil = lembar.daftar(Lelang.HASIL);
		long utang = lembar.l(Lelang.UTANG, 0L);
		int y = y0;
		if (utang > 0) {
			l.kartu(x0, y, x1, y + 22, 0.0F, false);
			int bw = 70;
			l.teks(k("lelang.utang"), x0 + 5, y + 7, x1 - x0 - bw - 90, l.gaya.teks);
			harga(l, utang, -1L, x1 - bw - 10, y + 7, true);
			tombolOp(l, "ambil_hasil", x1 - bw - 4, y + 4, bw, TOMBOL_H, k("lelang.ambil"), true, null, Lelang.OP_AMBIL_HASIL);
			y += 26;
		}
		if (hasil.isEmpty()) {
			kosong(l, k("lelang.hasil_kosong"), x0, y, x1, y1);
			return;
		}
		daftarHasil.gambar(l, "hasil", x0, y, x1, y1, hasil.size(), 19, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = hasil.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			String jenis = e.s(LembarKunci.JENIS, Lelang.H_MENANG);
			int ikon = switch (jenis) {
				case Lelang.H_TITIP -> LembarKunci.Ikon.TIMBANGAN;
				case Lelang.H_UTANG -> LembarKunci.Ikon.GEMBOK;
				default -> Glyphs.Atlas.PIALA;
			};
			Glyphs.Atlas.ikon(lk.g, ikon, rx0 + 3, ry0 + 1, 16, 0xFFFFFFFF);
			Component jenisTeks = k("lelang.hasil." + jenis);
			int jw = Math.min(70, lk.font.width(jenisTeks));
			int nw = 70;
			lk.teks(e.teks(LembarKunci.NAMA), rx0 + 23, ry0 + 5, rx1 - rx0 - 23 - jw - nw - 8, lk.gaya.teks);
			lk.teks(jenisTeks, rx1 - nw - jw - 4, ry0 + 5, jw, lk.gaya.teksSamar);
			long nb = Lelang.H_MENANG.equals(jenis) ? -e.l(LembarKunci.NB, 0L) : e.l(LembarKunci.NB, 0L);
			if (batuAsal()) {
				lk.kanan(m("lelang.batu_asal", Brush.bertanda(nb)), rx1 - 4, ry0 + 5, nb > 0 ? lk.gaya.naik() : nb < 0 ? lk.gaya.turun() : lk.gaya.teksSamar);
			} else {
				LabelHarga.nbBertanda(lk, nb, rx1 - 4, ry0 + 5, true);
			}
		}, null, null);
	}
}
