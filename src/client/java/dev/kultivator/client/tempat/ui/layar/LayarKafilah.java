package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.CincinWaktu;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.PemilihJumlah;
import dev.kultivator.client.screen.ui.Lembar;
import dev.kultivator.client.screen.ui.TabBar;
import dev.kultivator.client.tempat.ui.tema.TemaHalaman;
import dev.kultivator.client.tempat.ui.tema.TemaUi;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * The one caravan page (G56, DESIGN 43.11): both caravan sheets open here under Di Sini.
 * <ul>
 * <li>{@code kafilah} (38.U.3.9, road mode): the town side of caravans on the roads: each caravan's route from its origin
 * to its destination with where it is now, the risk of the road (a red bar) and the escort reward, "Kawal".</li>
 * <li>{@code tema_kafilah} (38.T.4.4, theme mode; was {@code LayarKafilahTema}): the caravan board of the karavanserai and
 * the Kandang Unta. Left the schedule (camels, destination, departure day, state); right the selected caravan's route
 * across the dunes with "ikut sebagai penumpang" and "kawal", and below the camel hire with its countdown. A sandstorm
 * puts a warning band on top and stops all departures. Narrow layout: the schedule takes the area and Enter / a second
 * click shows the caravan in its place (Esc back), the hire is a two-row block at the bottom.</li>
 * </ul>
 * Single kit, text 1.0 and 1.5; one Ensiklopedia topic ({@code kafilah}) for both modes.
 */
public final class LayarKafilah extends TemaHalaman {

	private final boolean tema;
	private final DaftarGulir daftar = new DaftarGulir();
	private final PemilihJumlah hari = new PemilihJumlah();
	private boolean rinciSempit;

	/** Road mode ({@code kafilah}). */
	public LayarKafilah(int npcId, CompoundTag lembar) {
		this(LembarKunci.Kafilah.LAYAR, npcId, lembar, Palette.Gaya.dari(LembarKunci.GAYA_KAYU, Palette.Gaya.KERTAS), false);
	}

	/** Theme mode ({@code tema_kafilah}); the factory {@code TemaUiClient} registers. */
	public static LayarKafilah tema(int npcId, CompoundTag lembar) {
		return new LayarKafilah(TemaLayar.Kafilah.LAYAR, npcId, lembar, Palette.Gaya.KAIN, true);
	}

	private LayarKafilah(String layar, int npcId, CompoundTag lembar, Palette.Gaya gaya, boolean tema) {
		super(layar, npcId, lembar, "layar.kafilah", gaya);
		this.tema = tema;
	}

	@Override
	protected Component judulBawaan() {
		// the base titles from the theme fragment ("Papan Kafilah"); the road board keeps its 38 name ("Kafilah")
		return tema ? super.judulBawaan() : LayarInti.k("layar.kafilah");
	}

	@Override
	public String helpTopic() {
		return LembarKunci.Kafilah.LAYAR;
	}

	@Override
	protected List<TabBar.Tab> tabs() {
		return List.of();
	}

	@Override
	public boolean lebar() {
		return tema && super.lebar();
	}

	@Override
	public boolean rinci() {
		return rinciSempit && tata == Tata.SEMPIT || super.rinci();
	}

	@Override
	public boolean mundur() {
		if (rinciSempit && tata == Tata.SEMPIT) {
			rinciSempit = false;
			return true;
		}
		return super.mundur();
	}

	@Override
	protected void lukisTema(Lukis l, int x0, int y0, int x1, int y1) {
		if (tema) {
			lukisTemaMode(l, x0, y0, x1, y1);
		} else {
			lukisJalan(l, x0, y0 + 2, x1, y1);
		}
	}

	/** Road mode ({@code kafilah}, DESIGN 38.U.3.9): the caravans on the roads with route, risk and "Kawal". */
	private void lukisJalan(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(LembarKunci.DAFTAR);
		if (rows.isEmpty()) {
			kosong(l, LayarInti.k("kafilah.kosong"), x0, y0, x1, y1);
			return;
		}
		daftar.gambar(l, "kafilah", x0, y0, x1, y1, rows.size(), 48, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			Glyphs.Atlas.ikon(lk.g, LembarKunci.Ikon.KAFILAH, rx0 + 3, ry0 + 3, 16, 0xFFFFFFFF);
			int bw = 66;
			int bx = rx1 - bw - 4;
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 23, ry0 + 3, bx - rx0 - 28, lk.gaya.teks);
			lk.teks(LayarInti.k("kafilah.rute", e.teks(LembarKunci.Kafilah.ASAL), e.teks(LembarKunci.TUJUAN)), rx0 + 23, ry0 + 13, bx - rx0 - 28, lk.gaya.teksLemah);
			// route line with the caravan
			int lx0 = rx0 + 8;
			int lx1 = bx - 8;
			int ly = ry0 + 30;
			float p = Math.max(0.0F, Math.min(1.0F, e.f(LembarKunci.KEMAJUAN, 0.0F)));
			for (int x = lx0; x < lx1; x += 4) {
				lk.g.fill(x, ly, x + 2, ly + 1, Palette.argb(0x90, lk.gaya.teksSamar & 0xFFFFFF));
			}
			lk.g.fill(lx0, ly, lx0 + Math.round((lx1 - lx0) * p), ly + 1, Palette.GOLD);
			Brush.disc(lk.g, lx0, ly + 0.5F, 2.0F, Palette.OK);
			Brush.disc(lk.g, lx1, ly + 0.5F, 2.0F, Palette.SEAL);
			Glyphs.Atlas.ikon(lk.g, LembarKunci.Ikon.KAFILAH, Math.round(lx0 + (lx1 - lx0) * p - 5), ly - 9, 10, 0xFFFFFFFF);
			// risk with its bar, the escort reward on the right
			int risiko = Math.max(0, Math.min(100, e.i(LembarKunci.RISIKO, 0)));
			int hw = LabelHarga.gambar(lk, e.l(LembarKunci.Kafilah.IMBALAN, 0L), -1L, rx1 - 4, ry0 + 35, true, false);
			Component rt = LayarInti.k("kafilah.risiko", risiko);
			int rw = Math.min(lk.font.width(rt), (rx1 - rx0) / 3);
			lk.teks(rt, rx0 + 8, ry0 + 35, rw, risiko >= 25 ? lk.gaya.turun() : lk.gaya.teksSamar);
			int bar0 = rx0 + 12 + rw;
			int bar1 = Math.min(bar0 + 60, rx1 - hw - 10);
			if (bar1 - bar0 >= 16) {
				Bilah.gambar(lk, bar0, ry0 + 37, bar1, risiko / 100.0F, Palette.BAHAYA, new float[0]);
			}
			Component alasan = e.teksAtau(LembarKunci.ALASAN);
			tombolOp(lk, "kawal" + i, bx, ry0 + 5, bw, 14, LayarInti.k("kafilah.kawal"), e.b(LembarKunci.BISA), alasan, LembarKunci.op(LembarKunci.Kafilah.OP_KAWAL, e.id()));
		}, () -> {
			int i = daftar.pilihan();
			if (i >= 0 && i < rows.size() && rows.get(i).b(LembarKunci.BISA) && tulisBoleh()) {
				kirim(LembarKunci.op(LembarKunci.Kafilah.OP_KAWAL, rows.get(i).id()));
			}
		}, null);
	}

	private Component hariBerangkat(int hariKe) {
		if (lembar.ada(LembarKunci.HARI)) {
			return Brush.hari(hariKe - lembar.i(LembarKunci.HARI, 0));
		}
		return TemaUi.k("kafilah.hari_ke", hariKe);
	}

	private static int warnaStatus(String s) {
		return switch (s) {
			case "tiba" -> Palette.OK;
			case "bongkar" -> Palette.PERINGATAN;
			case "berangkat" -> 0xFF6FA8C8;
			default -> 0xFFA08A70;
		};
	}

	/** Theme mode ({@code tema_kafilah}, DESIGN 38.T.4.4): schedule, route over the dunes, ride / escort, camel hire. */
	private void lukisTemaMode(Lukis l, int x0, int y0, int x1, int y1) {
		int y = y0;
		boolean sempit = tata == Tata.SEMPIT;
		boolean badai = lembar.b(TemaLayar.Kafilah.BADAI);
		if (badai) {
			y += pita(l, TemaUi.I_BADAI, TemaUi.k("kafilah.badai"), x0, y, x1, TemaUi.PASIR);
		}
		y += strip(l, TemaUi.k(badai ? "kafilah.ucap_badai" : "kafilah.ucap"), x0, y, x1, sempit ? 30 : 34);
		List<Lembar> jadwal = lembar.daftar(TemaLayar.Kafilah.JADWAL);
		int sel = daftar.pilihan();
		Lembar dipilih = sel >= 0 && sel < jadwal.size() ? jadwal.get(sel) : null;
		if (sempit) {
			Lembar sewa = lembar.sub(TemaLayar.Kafilah.SEWA);
			int sewaH = sewa.kosongSemua() ? 0 : 36;
			int atas = y1 - (sewaH > 0 ? sewaH + 3 : 0);
			if (rinciSempit && dipilih != null) {
				l.kartu(x0, y, x1, atas, 0.0F, false);
				kafilah(l, dipilih, x0 + 5, y + 4, x1 - 5, atas - 3, badai);
			} else {
				rinciSempit = false;
				jadwal(l, jadwal, new Kotak(x0, y, x1, atas));
			}
			if (sewaH > 0) {
				sewa(l, sewa, x0, y1 - sewaH, x1, y1);
			}
			return;
		}
		Kotak[] k = bagi(x0, y, x1, y1);
		jadwal(l, jadwal, k[0]);
		detail(l, k[1], dipilih, badai);
	}

	private void jadwal(Lukis l, List<Lembar> jadwal, Kotak kiri) {
		int ly = kiri.y0() + subjudul(l, TemaUi.k("kafilah.jadwal"), kiri.x0(), kiri.y0(), kiri.x1());
		if (jadwal.isEmpty()) {
			kosong(l, TemaUi.k("kafilah.kosong"), kiri.x0(), ly, kiri.x1(), kiri.y1());
			return;
		}
		daftar.gambar(l, "jadwal", kiri.x0(), ly, kiri.x1(), kiri.y1(), jadwal.size(), 24, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = jadwal.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			TemaUi.ikon(lk.g, TemaUi.I_UNTA, rx0 + 3, ry0 + 2, 14);
			lk.teks(Component.literal("×" + e.i(TemaLayar.Kafilah.UNTA, 0)), rx0 + 3, ry0 + 14, 18, gaya.teks);
			String s = e.s(LembarKunci.KEADAAN);
			Component status = TemaUi.k("kafilah.status." + (s.isEmpty() ? "di_jalan" : s));
			int cw = Math.min(70, font.width(status) + 6);
			lk.teks(e.teks(LembarKunci.NAMA), rx0 + 23, ry0 + 2, rx1 - rx0 - 28 - cw, gaya.teks);
			lk.samar(TemaUi.k("kafilah.ke", e.teks(LembarKunci.TUJUAN)), rx0 + 23, ry0 + 13, rx1 - rx0 - 28 - cw);
			TemaUi.chip(lk, -1, status, rx1 - cw - 2, ry0 + 1, warnaStatus(s));
			lk.kanan(hariBerangkat(e.i(TemaLayar.Kafilah.BERANGKAT_HARI, 0)), rx1 - 3, ry0 + 14, gaya.teksLemah);
		}, () -> rinciSempit = true, i -> {
		});
	}

	private void detail(Lukis l, Kotak k, Lembar e, boolean badai) {
		l.kartu(k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
		int x0 = k.x0() + 5;
		int x1 = k.x1() - 5;
		Lembar sewa = lembar.sub(TemaLayar.Kafilah.SEWA);
		int sewaTinggi = sewa.kosongSemua() ? 0 : 50;
		if (e != null) {
			kafilah(l, e, x0, k.y0() + 4, x1, k.y1() - sewaTinggi - 3, badai);
		} else {
			tengah(l, TemaUi.k("kafilah.pilih"), new Kotak(k.x0(), k.y0(), k.x1(), k.y1() - sewaTinggi));
		}
		if (sewaTinggi > 0) {
			sewa(l, sewa, x0, k.y1() - sewaTinggi, x1, k.y1() - 3);
		}
	}

	/** One caravan: its name, the route over the dunes, "ikut" / "kawal" and the saddle-sleep note. */
	private void kafilah(Lukis l, Lembar e, int x0, int y0, int x1, int y1, boolean badai) {
		int y = y0 + subjudul(l, e.teks(LembarKunci.NAMA), x0, y0, x1);
		int ry1 = Math.min(y + 40, y1 - 20);
		if (ry1 - y >= 18) {
			rute(l, e, x0, y, x1, ry1);
			y = ry1 + 4;
		}
		Lembar ikut = lembar.sub(TemaLayar.Kafilah.IKUT);
		long ongkos = ikut.l(TemaLayar.Kafilah.PENUMPANG, 8L);
		boolean berangkat = "di_jalan".equals(e.s(LembarKunci.KEADAAN));
		boolean bisa = ikut.b(LembarKunci.BISA) && !badai && !berangkat;
		Component alasan = badai ? TemaUi.k("kafilah.badai_tolak") : berangkat ? TemaUi.k("kafilah.sudah_berangkat") : ikut.teksAtau(LembarKunci.ALASAN);
		int half = (x1 - x0 - 4) / 2;
		tombolOp(l, "ikut", x0, y, half, 15, TemaUi.k("kafilah.ikut", Brush.angka(ongkos)), bisa && mampu(ongkos), mampu(ongkos) ? alasan
				: kurang(ongkos), LembarKunci.op(TemaLayar.Kafilah.OP_IKUT, e.id()));
		boolean kawal = ikut.b(TemaLayar.Kafilah.PENGAWAL);
		tombolOp(l, "kawal", x0 + half + 4, y, half, 15, TemaUi.k("kafilah.kawal"), kawal && !badai && !berangkat, kawal ? alasan
				: TemaUi.k("kafilah.kawal_tidak"), LembarKunci.op(TemaLayar.Kafilah.OP_KAWAL, e.id()));
		y += 18;
		if (y1 - y >= 9) {
			l.bungkus(TemaUi.k("kafilah.tidur_pelana"), x0, y, x1 - x0, gaya.teksSamar, y1 - y);
		}
	}

	/** The route: dunes, the dotted road from the oasis to the destination, the camel string where the caravan is now. */
	private void rute(Lukis l, Lembar e, int x0, int y0, int x1, int y1) {
		l.sumur(x0, y0, x1, y1);
		l.klip(x0 + 1, y0 + 1, x1 - 1, y1 - 1);
		int h = y1 - y0;
		l.g.fillGradient(x0, y0, x1, y0 + h / 2, 0xFFF4DDB0, 0xFFE8C890);
		for (int i = 0; i < 4; i++) {
			float cx = x0 + (x1 - x0) * (0.15F + i * 0.27F);
			Brush.disc(l.g, cx, y1 + h * 0.15F, h * 0.55F, Palette.argb(0xFF, i % 2 == 0 ? 0xD8B070 : 0xC89A5A));
		}
		int ry = y0 + h / 2 + 2;
		for (int x = x0 + 14; x < x1 - 14; x += 5) {
			l.g.fill(x, ry, x + 2, ry + 1, Palette.argb(0xA0, 0x6A4A2A));
		}
		TemaUi.ikon(l.g, TemaUi.I_OASIS, x0 + 2, ry - 12, 12);
		TemaUi.ikon(l.g, TemaUi.I_ASAL, x1 - 13, ry - 10, 10);
		String s = e.s(LembarKunci.KEADAAN);
		float t = switch (s) {
			case "tiba", "bongkar" -> 0.02F;
			case "berangkat" -> 0.12F;
			default -> 0.5F + 0.04F * (float) Math.sin(l.kini / 2400.0D);
		};
		int unta = Math.max(1, Math.min(8, e.i(TemaLayar.Kafilah.UNTA, 4)));
		int bob = (int) (l.kini / 300L % 2L);
		for (int i = 0; i < unta; i++) {
			int cx = x0 + 14 + Math.round((x1 - x0 - 40) * t) + i * 8;
			if (cx > x1 - 20) {
				break;
			}
			if (i > 0) {
				l.g.fill(cx - 3, ry - 6, cx + 1, ry - 5, Palette.argb(0xC0, 0x5A3A1A));
			}
			TemaUi.ikon(l.g, TemaUi.I_UNTA, cx, ry - 12 + ((i + bob) % 2), 10);
		}
		l.lepasKlip();
		if (h >= 30) {
			l.teks(TemaUi.k("kafilah.ke", e.teks(LembarKunci.TUJUAN)), x0 + 3, y1 - 10, x1 - x0 - 6, 0xFF5A3A1A);
		}
	}

	/** The camel hire: per-day price, day picker and "Sewa", or the running hire with its countdown and "Kembalikan". */
	private void sewa(Lukis l, Lembar sewa, int x0, int y0, int x1, int y1) {
		l.sumur(x0, y0, x1, y1);
		int ink = gaya.teksSumur();
		TemaUi.ikon(l.g, TemaUi.I_SEWA, x0 + 3, y0 + 3, 12);
		long harga = sewa.l(LembarKunci.HARGA, 12L);
		Component perHari = TemaUi.k("kafilah.per_hari", Brush.angka(harga));
		boolean aktif = sewa.b(LembarKunci.AKTIF);
		int pw0 = aktif ? 0 : font.width(perHari) + 4;
		l.teks(Brush.bold(TemaUi.k("kafilah.sewa")), x0 + 18, y0 + 4, x1 - x0 - 22 - pw0, ink);
		int by = y1 - 17;
		if (aktif) {
			long sisa = CincinWaktu.gambar(l, x0 + 11, by + 7, 7.0F, sewa.l(LembarKunci.SISA_TICK, 0L), 24000L, diterima, CincinWaktu.TANPA);
			l.teks(TemaUi.k("kafilah.sewa_aktif", Brush.waktu(sisa)), x0 + 22, by + 3, x1 - x0 - 108, ink);
			tombolOp(l, "kembalikan", x1 - 82, by, 78, 15, TemaUi.k("kafilah.kembalikan"), true, null, TemaLayar.Kafilah.OP_KEMBALIKAN);
			return;
		}
		l.kanan(perHari, x1 - 4, y0 + 4, ink);
		int maks = Math.max(1, sewa.i(TemaLayar.Kafilah.HARI_MAKS, 7));
		int pw = hari.gambar(l, "hari", x0 + 4, by + 1, maks);
		long total = harga * hari.nilai();
		l.teks(TemaUi.k("kafilah.hari_n", hari.nilai()), x0 + pw + 8, by + 3, Math.max(0, x1 - x0 - pw - 104), ink);
		tombolOp(l, "sewa", x1 - 90, by, 86, 15, TemaUi.k("kafilah.sewa_tombol", Brush.angka(total)), mampu(total), kurang(total),
				LembarKunci.op(TemaLayar.Kafilah.OP_SEWA, hari.nilai()));
	}
}
