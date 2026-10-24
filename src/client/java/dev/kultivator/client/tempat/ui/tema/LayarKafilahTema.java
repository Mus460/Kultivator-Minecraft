package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.CincinWaktu;
import dev.kultivator.client.tempat.ui.kit.DaftarGulir;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.PemilihJumlah;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.Kafilah;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * {@code tema_kafilah} (DESIGN 38.T.4.4): the caravan board of the karavanserai and the Kandang Unta. Left the schedule
 * (camels in the string, destination, departure day, state); right the selected caravan's route across the dunes with
 * the string of camels where it is now, "ikut sebagai penumpang" (8 NB) and "kawal" (the guild mission), and below the
 * camel hire: days, price, the running hire with its countdown and "kembalikan". A sandstorm puts a warning band on top
 * and stops all departures.
 */
public final class LayarKafilahTema extends LayarTemaDasar {

	private final DaftarGulir daftar = new DaftarGulir();
	private final PemilihJumlah hari = new PemilihJumlah();

	public LayarKafilahTema(int npcId, CompoundTag lembar) {
		super(Kafilah.LAYAR, npcId, lembar, "layar.kafilah", TempatWarna.Gaya.KAIN);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of();
	}

	private Component hariBerangkat(int hariKe) {
		if (lembar.ada(LembarKunci.HARI)) {
			return TeksTempat.hari(hariKe - lembar.i(LembarKunci.HARI, 0));
		}
		return TemaUi.k("kafilah.hari_ke", hariKe);
	}

	private static int warnaStatus(String s) {
		return switch (s) {
			case "tiba" -> TempatWarna.OK;
			case "bongkar" -> TempatWarna.PERINGATAN;
			case "berangkat" -> 0xFF6FA8C8;
			default -> 0xFFA08A70;
		};
	}

	@Override
	protected void gambar(Lukis l, int x0, int y0, int x1, int y1) {
		int y = y0;
		boolean badai = lembar.b(Kafilah.BADAI);
		if (badai) {
			y += pita(l, TemaUi.I_BADAI, TemaUi.k("kafilah.badai"), x0, y, x1, TemaUi.PASIR);
		}
		y += strip(l, TemaUi.k(badai ? "kafilah.ucap_badai" : "kafilah.ucap"), x0, y, x1, 34);
		Kotak[] k = bagi(x0, y, x1, y1);
		List<Lembar> jadwal = lembar.daftar(Kafilah.JADWAL);
		Kotak kiri = k[0];
		int ly = kiri.y0() + subjudul(l, TemaUi.k("kafilah.jadwal"), kiri.x0(), kiri.y0(), kiri.x1());
		if (jadwal.isEmpty()) {
			kosong(l, TemaUi.k("kafilah.kosong"), kiri.x0(), ly, kiri.x1(), kiri.y1());
		} else {
			daftar.gambar(l, "jadwal", kiri.x0(), ly, kiri.x1(), kiri.y1(), jadwal.size(), 24, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
				Lembar e = jadwal.get(i);
				PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
				TemaUi.ikon(lk.g, TemaUi.I_UNTA, rx0 + 3, ry0 + 4, 16);
				Brush.text(lk.g, font, Component.literal("×" + e.i(Kafilah.UNTA, 0)), rx0 + 12, ry0 + 15, gaya.teks, 0.6F, false);
				lk.teks(e.teks(LembarKunci.NAMA), rx0 + 23, ry0 + 3, rx1 - rx0 - 80, gaya.teks, 0.9F);
				lk.keterangan(TemaUi.k("kafilah.ke", e.teks(LembarKunci.TUJUAN)), rx0 + 23, ry0 + 13, rx1 - rx0 - 80, gaya.teksSamar);
				String s = e.s(LembarKunci.KEADAAN);
				TemaUi.chip(lk, -1, TemaUi.k("kafilah.status." + (s.isEmpty() ? "di_jalan" : s)), rx1 - 56, ry0 + 2, warnaStatus(s));
				lk.keterangan(hariBerangkat(e.i(Kafilah.BERANGKAT_HARI, 0)), rx1 - 56, ry0 + 14, 54, gaya.teksLemah);
			}, null, i -> {
			});
		}
		int sel = daftar.pilihan();
		detail(l, k[1], sel >= 0 && sel < jadwal.size() ? jadwal.get(sel) : null, badai);
	}

	private void detail(Lukis l, Kotak k, Lembar e, boolean badai) {
		PanelTempat.kartu(l.g, gaya, k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
		int x0 = k.x0() + 5;
		int x1 = k.x1() - 5;
		int y = k.y0() + 4;
		Lembar sewa = lembar.sub(Kafilah.SEWA);
		int sewaTinggi = sewa.kosongSemua() ? 0 : 50;
		if (e != null) {
			y += subjudul(l, e.teks(LembarKunci.NAMA), x0, y, x1);
			int ry1 = Math.min(y + 40, k.y1() - sewaTinggi - 40);
			rute(l, e, x0, y, x1, ry1);
			y = ry1 + 4;
			Lembar ikut = lembar.sub(Kafilah.IKUT);
			long ongkos = ikut.l(Kafilah.PENUMPANG, 8L);
			boolean berangkat = "di_jalan".equals(e.s(LembarKunci.KEADAAN));
			boolean bisa = ikut.b(LembarKunci.BISA) && !badai && !berangkat;
			Component alasan = badai ? TemaUi.k("kafilah.badai_tolak") : berangkat ? TemaUi.k("kafilah.sudah_berangkat") : ikut.teksAtau(LembarKunci.ALASAN);
			int half = (x1 - x0 - 4) / 2;
			tombolOp(l, "ikut", x0, y, half, 15, TemaUi.k("kafilah.ikut", TeksTempat.angka(ongkos)), bisa && mampu(ongkos), mampu(ongkos) ? alasan
					: kurang(ongkos), LembarKunci.op(Kafilah.OP_IKUT, e.id()));
			boolean kawal = ikut.b(Kafilah.PENGAWAL);
			tombolOp(l, "kawal", x0 + half + 4, y, half, 15, TemaUi.k("kafilah.kawal"), kawal && !badai && !berangkat, kawal ? alasan
					: TemaUi.k("kafilah.kawal_tidak"), LembarKunci.op(Kafilah.OP_KAWAL, e.id()));
			y += 18;
			l.bungkus(TemaUi.k("kafilah.tidur_pelana"), x0, y, x1 - x0, gaya.teksSamar, 0.7F);
		} else {
			tengah(l, TemaUi.k("kafilah.pilih"), new Kotak(k.x0(), k.y0(), k.x1(), k.y1() - sewaTinggi));
		}
		if (sewaTinggi > 0) {
			sewa(l, sewa, x0, k.y1() - sewaTinggi, x1, k.y1() - 3);
		}
	}

	/** The route: dunes, the dotted road from the oasis to the destination, the camel string where the caravan is now. */
	private void rute(Lukis l, Lembar e, int x0, int y0, int x1, int y1) {
		PanelTempat.sumur(l.g, gaya, x0, y0, x1, y1);
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
		int unta = Math.max(1, Math.min(8, e.i(Kafilah.UNTA, 4)));
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
		l.keterangan(TemaUi.k("kafilah.ke", e.teks(LembarKunci.TUJUAN)), x0 + 3, y1 - 9, x1 - x0 - 6, 0xFF5A3A1A);
	}

	private void sewa(Lukis l, Lembar sewa, int x0, int y0, int x1, int y1) {
		PanelTempat.sumur(l.g, gaya, x0, y0, x1, y1);
		int ink = gaya.teksSumur();
		TemaUi.ikon(l.g, TemaUi.I_SEWA, x0 + 3, y0 + 3, 12);
		l.teks(Brush.bold(TemaUi.k("kafilah.sewa")), x0 + 18, y0 + 4, x1 - x0 - 22, ink, 0.85F);
		long harga = sewa.l(LembarKunci.HARGA, 12L);
		if (sewa.b(LembarKunci.AKTIF)) {
			long sisa = CincinWaktu.gambar(l, x0 + 14, y0 + 30, 9.0F, sewa.l(LembarKunci.SISA_TICK, 0L), 24000L, diterima, CincinWaktu.TANPA);
			l.teks(TemaUi.k("kafilah.sewa_aktif", TeksTempat.waktu(sisa)), x0 + 28, y0 + 22, x1 - x0 - 32, ink, 0.75F);
			tombolOp(l, "kembalikan", x1 - 78, y1 - 17, 74, 14, TemaUi.k("kafilah.kembalikan"), true, null, Kafilah.OP_KEMBALIKAN);
			return;
		}
		int maks = Math.max(1, sewa.i(Kafilah.HARI_MAKS, 7));
		l.keterangan(TemaUi.k("kafilah.per_hari", TeksTempat.angka(harga)), x0 + 4, y0 + 18, x1 - x0 - 8, ink);
		int pw = hari.gambar(l, "hari", x0 + 4, y1 - 16, maks);
		long total = harga * hari.nilai();
		l.keterangan(TemaUi.k("kafilah.hari_n", hari.nilai()), x0 + pw + 8, y1 - 14, 40, ink);
		tombolOp(l, "sewa", x1 - 86, y1 - 17, 82, 14, TemaUi.k("kafilah.sewa_tombol", TeksTempat.angka(total)), mampu(total), kurang(total),
				LembarKunci.op(Kafilah.OP_SEWA, hari.nilai()));
	}
}
