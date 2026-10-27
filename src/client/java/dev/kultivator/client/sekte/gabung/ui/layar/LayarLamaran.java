package dev.kultivator.client.sekte.gabung.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.sekte.diplomasi.LuarPage;
import dev.kultivator.client.sekte.gabung.ui.IkonGabung;
import dev.kultivator.client.screen.ui.Lembar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.gabung.api.Pintu;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import dev.kultivator.sekte.gabung.ui.GabungLembar.Lamaran;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * {@code gabung_lamaran} (DESIGN 41.10.2): one tablet per door (Gerbang, Token, Undangan, Pelayan, Tamu; a door that cannot
 * be used is a locked tablet with its reason). The door's page: the test day, the stages it runs as a chain of icons,
 * the best rank it gives, every requirement line with ticks and crosses and advice, the running application, and
 * "Ajukan lamaran" (or "Batalkan"). Tab Token also lists the tokens in the bag and whether this sect takes them.
 */
public final class LayarLamaran extends LayarGabungDasar {

	private final DaftarGulir syaratGulir = new DaftarGulir();
	private final DaftarGulir tokenGulir = new DaftarGulir();

	public LayarLamaran(int npcId, CompoundTag lembar) {
		super(Lamaran.LAYAR, npcId, lembar, t("lamaran.judul_bawaan"), LembarKunci.GAYA_KERTAS);
	}

	@Override
	protected List<Tab> daftarTab() {
		return List.of(tab(Lamaran.TAB_GERBANG, Pintu.GERBANG.nama(), LembarKunci.Ikon.KUIL),
				tab(Lamaran.TAB_TOKEN, Pintu.TOKEN.nama(), LembarKunci.Ikon.JIMAT),
				tab(Lamaran.TAB_UNDANGAN, Pintu.UNDANGAN.nama(), Glyphs.Atlas.SURAT),
				tab(Lamaran.TAB_PELAYAN, Pintu.PELAYAN.nama(), Glyphs.Atlas.TONGGAK),
				tab(Lamaran.TAB_TAMU, Pintu.TAMU.nama(), Glyphs.Atlas.DUPA));
	}

	@Override
	protected KeadaanKosong.Gambar kosongGambar() {
		return KeadaanKosong.Gambar.GULUNGAN;
	}

	@Override
	protected void tabBerubah() {
		syaratGulir.mulaiUlang(Util.getMillis());
		tokenGulir.mulaiUlang(Util.getMillis());
		halaman = 0;
	}

	private Lembar pintu() {
		for (Lembar p : lembar.daftar(Lamaran.PINTU_DAFTAR)) {
			if (p.id().equals(tab)) {
				return p;
			}
		}
		return Lembar.kosong();
	}

	@Override
	protected void isiGabung(Lukis l, int x0, int y0, int x1, int y1) {
		int y = kepalaSekte(l, x0, y0, x1);
		Lembar p = pintu();
		if (p.kosongSemua()) {
			kosong(l, t("lamaran.tak_ada_pintu"), x0, y, x1, y1);
			return;
		}
		Pintu pintu = Pintu.byId(p.id(), Pintu.GERBANG);
		long hariIni = hariIni();
		// the door line and the test day
		IkonGabung.gambar(l.g, IkonGabung.PINTU + pintu.ordinal(), x0, y, 16);
		l.teks(Brush.bold(t("lamaran.pintu", pintu.nama())), x0 + 19, y + 1, (x1 - x0) / 2 - 20, l.gaya.teks);
		Component hari = pintu == Pintu.PELAYAN || pintu == Pintu.TAMU ? t("lamaran.kapan_saja")
				: t("lamaran.hari_ujian", hariKe(lembar.l(GabungLembar.HARI_UJIAN, -1L), hariIni));
		l.kanan(hari, x1, y + 1, l.gaya.teksLemah);
		y += 12;
		// the stages as a chain of icons, then the best rank
		int sx = x0 + 19;
		List<String> tahap = p.sl(LembarKunci.TAHAP);
		for (int i = 0; i < tahap.size(); i++) {
			String id = tahap.get(i);
			int ikon = switch (id) {
				case Lamaran.TAHAP_TANGGA -> IkonGabung.TEKANAN;
				case Lamaran.TAHAP_CORAK -> IkonGabung.ujianCorak(lembar.s(GabungLembar.CORAK));
				case Lamaran.TAHAP_HATI, Lamaran.TAHAP_WAWANCARA -> IkonGabung.HATI;
				default -> IkonGabung.KURSI;
			};
			IkonGabung.gambar(l.g, ikon, sx, y, 10);
			Component nama = t("lamaran.tahap." + id);
			int nw = Math.min(90, font.width(nama));
			if (sx + 12 + nw > x1) {
				break; // P5: text 1.0 (43.0.5); the full chain stays in the requirement lines below
			}
			l.teks(nama, sx + 12, y + 1, 90, l.gaya.teks);
			sx += 12 + nw + 3;
			if (i < tahap.size() - 1 && sx + 8 < x1) {
				l.teks(Component.literal("›"), sx, y + 1, 6, l.gaya.teksSamar);
				sx += 8;
			}
		}
		int pangkat = p.i(GabungLembar.PANGKAT, 0);
		Component maks = t("lamaran.pangkat_maks", namaPangkat(pangkat));
		int mw = font.width(maks);
		if (sx + mw + 16 < x1) {
			IkonGabung.gambar(l.g, IkonGabung.pangkat(pangkat), x1 - mw - 12, y, 10);
			l.kanan(maks, x1, y + 1, l.gaya.teksLemah);
		}
		y += 13;
		l.garis(x0, x1, y);
		y += 4;
		int by = y1 - 18;
		// tab Token: the tokens of the bag on the right (a second page behind two chips in the narrow layout)
		int sx1 = x1;
		boolean adaToken = Lamaran.TAB_TOKEN.equals(tab);
		boolean halamanToken = false;
		if (adaToken && tata() == Tata.SEMPIT) {
			y = bilahHalaman(l, x0, y, x1, t("lamaran.hal.syarat"), t("lamaran.hal.token"));
			halamanToken = halaman == 1;
		} else if (adaToken) {
			sx1 = x0 + (x1 - x0) * 3 / 5;
			token(l, sx1 + 6, y, x1, by - 4);
		}
		int yMaks = by - 14;
		if (halamanToken) {
			token(l, x0, y, x1, yMaks);
		} else {
			daftarSyarat(l, syaratGulir, "syarat", p.daftar(LembarKunci.SYARAT), x0, y, sx1, yMaks);
		}
		// the application line
		Lembar calon = lembar.sub(Lamaran.CALON);
		Component status;
		int warna;
		if (!calon.kosongSemua()) {
			boolean sini = calon.b(Lamaran.SINI);
			status = sini ? t("lamaran.calon_sini", Pintu.byId(calon.s(GabungLembar.PINTU), Pintu.GERBANG).nama(),
					hariKe(calon.l(Lamaran.SAMPAI, -1L), hariIni)) : t("lamaran.calon_lain", calon.teks(GabungLembar.NAMA_SEKTE));
			warna = sini ? Palette.OK : Palette.PERINGATAN;
		} else if (lembar.ada(Lamaran.JEDA)) {
			status = t("lamaran.jeda", hariKe(lembar.l(Lamaran.JEDA, -1L), hariIni));
			warna = Palette.PERINGATAN;
		} else if (!p.b(LembarKunci.BISA) && p.teksAtau(LembarKunci.ALASAN) != null) {
			// the locked button's reason in plain sight (a disabled button is not a Tab stop)
			status = p.teks(LembarKunci.ALASAN);
			warna = Palette.PERINGATAN;
		} else {
			status = t("lamaran.catatan_jeda");
			warna = l.gaya.teksSamar;
		}
		l.teks(status, x0, by - 11, x1 - x0, warna);
		boolean sini = !calon.kosongSemua() && calon.b(Lamaran.SINI);
		int bw = Math.min(150, (x1 - x0 - 4) / 2);
		if (sini) {
			tombolYakin(l, "batal", x0, by, bw, 15, t("lamaran.batal"), true, null, Lamaran.OP_BATAL);
		} else {
			boolean bisa = p.b(LembarKunci.BISA);
			tombolOp(l, "ajukan", x0, by, bw, 15, t("lamaran.ajukan"), bisa, p.teksAtau(LembarKunci.ALASAN),
					LembarKunci.op(Lamaran.OP_AJUKAN, pintu.id()));
		}
		// back to the sect in the one sect list (Sekte › Luar › Sekte Dunia, 43.12.4: the application is its detail)
		String sekte = lembar.s(GabungLembar.SEKTE_ID);
		int kw = 0;
		if (!sekte.isEmpty()) {
			Component ke = Component.translatable("kultivator.gui43.m11.ke_sekte_dunia");
			kw = Math.min(110, (x1 - x0 - 4) / 2 - bw / 2);
			if (kw >= 40) {
				TalismanButton.gambar(l, "sekte_dunia", x1 - kw, by, kw, 15, ke, true, null, () -> LuarPage.bukaSekte(sekte));
			} else {
				kw = 0;
			}
		}
		Component catatan = t("lamaran.tahap_jumlah", tahap.size());
		l.teks(catatan, x0 + bw + 8, by + 4, x1 - x0 - bw - 8 - (kw > 0 ? kw + 4 : 0), l.gaya.teksSamar);
	}

	/** The tokens in the bag, with whether this sect takes them (a scrolled list: arrow keys reach every one). */
	private void token(Lukis l, int x0, int y0, int x1, int y1) {
		l.kartu(x0, y0, x1, y1, 0.0F, false);
		l.teks(t("lamaran.token.judul"), x0 + 4, y0 + 3, x1 - x0 - 8, l.gaya.gelap() ? Palette.GOLD_LIGHT : l.gaya.aksen);
		List<Lembar> token = lembar.daftar(Lamaran.TOKEN);
		int y = y0 + 14;
		if (token.isEmpty()) {
			l.bungkus(t("lamaran.token.kosong"), x0 + 4, y, x1 - x0 - 8, l.gaya.teksSamar, y1 - y - 2);
			return;
		}
		tokenGulir.gambar(l, "token", x0 + 1, y, x1 - 1, y1 - 1, token.size(), 20, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = token.get(i);
			ItemStack stack = e.barang();
			if (!stack.isEmpty()) {
				lk.g.item(stack, rx0 + 2, ry0);
			}
			boolean sah = e.b(Lamaran.SAH);
			lk.teks(e.teks(LembarKunci.NAMA), rx0 + 21, ry0 + 1, rx1 - rx0 - 35, l.gaya.teks);
			Glyphs.Atlas.ikon(lk.g, sah ? Glyphs.Atlas.CENTANG : Glyphs.Atlas.SILANG, rx1 - 12, ry0 + 1, 9, 0xFFFFFFFF);
			Component bawah = sah ? t("lamaran.token.sah") : e.teks(LembarKunci.ALASAN);
			lk.teks(bawah, rx0 + 21, ry0 + 10, rx1 - rx0 - 25, sah ? Palette.OK : Palette.BAHAYA);
			if (lk.di(rx0, ry0, rx1, ry1) && !stack.isEmpty()) {
				lk.tip(stack.getHoverName());
			}
		}, null, null);
	}
}
