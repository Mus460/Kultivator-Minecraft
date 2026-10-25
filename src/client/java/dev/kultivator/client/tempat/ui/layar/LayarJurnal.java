package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.CincinWaktu;
import dev.kultivator.client.tempat.ui.kit.DaftarGulir;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.LayarTempatDasar;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Jurnal;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * {@code jurnal} (DESIGN 38.U.3.9, 38.S.1): Catatan Perjalanan, the page Dunia › Perjalanan of the Gulungan Utama (43.5,
 * M12; read only from there, tracking and marking are read ops; {@code /kultivasi jurnal} opens it as a context). Text at
 * 1.0 (43.0.5). "Aktif": the open quests by place with stage dots and deadline rings, the tracked one sealed, detail with
 * "Lacak" (the compass needle) and "Tandai". "Selesai": finished quests with the branch taken. "Alur": the capital's
 * storyline chapter by chapter; chapters not yet open stay sealed.
 */
public final class LayarJurnal extends LayarTempatDasar {

	private final DaftarGulir daftarAktif = new DaftarGulir();
	private final DaftarGulir daftarSelesai = new DaftarGulir();
	private final DaftarGulir daftarAlur = new DaftarGulir();

	public LayarJurnal(int npcId, CompoundTag lembar) {
		super(Jurnal.LAYAR, npcId, lembar, TeksTempat.k("layar.jurnal"), TempatWarna.Gaya.KERTAS);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of(tabBaru(Jurnal.TAB_AKTIF, "tab.aktif", IkonTempat.KOMPAS), tabBaru(Jurnal.TAB_SELESAI, "tab.selesai", IkonTempat.CENTANG),
				tabBaru(Jurnal.TAB_ALUR, "tab.alur", IkonTempat.BUKU));
	}

	@Override
	protected dev.kultivator.client.tempat.ui.kit.KeadaanKosong.Gambar gambarKosong() {
		return dev.kultivator.client.tempat.ui.kit.KeadaanKosong.Gambar.GULUNGAN;
	}

	@Override
	protected void tabBerubah() {
		long now = Util.getMillis();
		daftarAktif.mulaiUlang(now);
		daftarSelesai.mulaiUlang(now);
		daftarAlur.mulaiUlang(now);
	}

	@Override
	protected void isi(Lukis l, int x0, int y0, int x1, int y1) {
		switch (tab) {
			case Jurnal.TAB_SELESAI -> selesai(l, x0, y0, x1, y1);
			case Jurnal.TAB_ALUR -> alur(l, x0, y0, x1, y1);
			default -> aktif(l, x0, y0, x1, y1);
		}
	}

	private void aktif(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(LembarKunci.AKTIF);
		if (rows.isEmpty()) {
			kosong(l, TeksTempat.k("jurnal.aktif_kosong"), x0, y0, x1, y1);
			return;
		}
		String lacak = lembar.s(Jurnal.LACAK);
		Kotak[] k = bagi(x0, y0, x1, y1);
		daftarAktif.gambar(l, "aktif", k[0].x0(), k[0].y0(), k[0].x1(), k[0].y1(), rows.size(), 28, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			boolean dilacak = e.id().equals(lacak);
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			IkonTempat.gambar(lk.g, dilacak ? IkonTempat.KOMPAS : IkonTempat.SURAT, rx0 + 3, ry0 + 4, 14, 0xFFFFFFFF);
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 21, ry0 + 2, rx1 - rx0 - 40, dilacak ? gaya.aksen : gaya.teks);
			lk.teks(e.teks(LembarKunci.TEMPAT), rx0 + 21, ry0 + 12, rx1 - rx0 - 40, gaya.teksSamar);
			int tahap = e.i(LembarKunci.TAHAP, 0);
			int total = Math.max(1, e.i(LembarKunci.TAHAP_TOTAL, 1));
			for (int s = 0; s < total && s < 10; s++) {
				Brush.disc(lk.g, rx0 + 23 + s * 5, ry0 + 24, 1.6F, s < tahap ? TempatWarna.OK : Palette.argb(0x60, 0x000000));
			}
			if (e.ada(LembarKunci.SISA_TICK)) {
				CincinWaktu.gambar(lk, rx1 - 10, ry0 + 13, 7.0F, e.l(LembarKunci.SISA_TICK, 0L), 0L, diterima, CincinWaktu.TANPA);
			}
		}, this::lacakPilihan, null);
		int sel = daftarAktif.pilihan();
		Lembar e = sel >= 0 && sel < rows.size() ? rows.get(sel) : null;
		Kotak d = k[1];
		PanelTempat.kartu(l.g, gaya, d.x0(), d.y0(), d.x1(), d.y1(), 0.0F, false);
		if (e == null) {
			return;
		}
		int dx0 = d.x0() + 5;
		int dx1 = d.x1() - 5;
		int y = d.y0() + 4;
		l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), dx0, y, dx1 - dx0, gaya.aksen);
		y += 11;
		l.teks(TeksTempat.k("jurnal.tahap", e.i(LembarKunci.TAHAP, 0), Math.max(1, e.i(LembarKunci.TAHAP_TOTAL, 1)), e.teks(LembarKunci.TEMPAT)), dx0,
				y, dx1 - dx0, gaya.teksSamar);
		y += 11;
		// the big deadline ring only on a tall detail (GUI scale 4 stacks list over a low detail: the row's small ring stays)
		boolean cincin = e.ada(LembarKunci.SISA_TICK) && d.h() >= 90;
		// the quest text stops above the deadline ring and the buttons (full text as tooltip when cut)
		int teksBawah = d.y1() - (cincin ? 58 : 23);
		l.bungkus(e.teks(LembarKunci.TEKS), dx0, y, dx1 - dx0, gaya.teks, 1.0F, teksBawah - y);
		if (cincin) {
			CincinWaktu.gambar(l, dx0 + 8, d.y1() - 46, 8.0F, e.l(LembarKunci.SISA_TICK, 0L), 0L, diterima, CincinWaktu.KANAN);
		}
		boolean dilacak = e.id().equals(lacak);
		int half = (dx1 - dx0 - 4) / 2;
		tombolOp(l, "lacak", dx0, d.y1() - 19, half, 15, TeksTempat.k(dilacak ? "jurnal.dilacak" : "jurnal.lacak"), e.b(Jurnal.ADA_ARAH) && !dilacak,
				dilacak ? TeksTempat.k("jurnal.dilacak") : TeksTempat.k("jurnal.tanpa_arah"), LembarKunci.op(LembarKunci.OP_LACAK, e.id()));
		tombolOp(l, "tandai", dx0 + half + 4, d.y1() - 19, half, 15, TeksTempat.k("jurnal.tandai"), e.ada(LembarKunci.X), TeksTempat.k("jurnal.tanpa_arah"),
				LembarKunci.op(LembarKunci.OP_TANDAI, e.i(LembarKunci.X, 0), e.i(LembarKunci.Z, 0)));
	}

	private void lacakPilihan() {
		List<Lembar> rows = lembar.daftar(LembarKunci.AKTIF);
		int i = daftarAktif.pilihan();
		if (i >= 0 && i < rows.size() && rows.get(i).b(Jurnal.ADA_ARAH)) {
			kirim(LembarKunci.op(LembarKunci.OP_LACAK, rows.get(i).id()));
		}
	}

	private void selesai(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Jurnal.SELESAI);
		if (rows.isEmpty()) {
			kosong(l, TeksTempat.k("jurnal.selesai_kosong"), x0, y0, x1, y1);
			return;
		}
		daftarSelesai.gambar(l, "selesai", x0, y0, x1, y1, rows.size(), 26, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			IkonTempat.gambar(lk.g, IkonTempat.CENTANG, rx0 + 3, ry0 + 5, 14, 0xFFFFFFFF);
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 21, ry0 + 2, rx1 - rx0 - 70, gaya.teks);
			lk.kanan(TeksTempat.k("jurnal.hari", e.i(LembarKunci.HARI, 0)), rx1 - 4, ry0 + 2, gaya.teksSamar, 1.0F);
			Component hasil = e.teksAtau(Jurnal.HASIL);
			lk.teks(hasil != null ? TeksTempat.k("jurnal.hasil", e.teks(LembarKunci.TEMPAT), hasil) : e.teks(LembarKunci.TEMPAT), rx0 + 21, ry0 + 13,
					rx1 - rx0 - 26, gaya.teksLemah);
		}, null, null);
	}

	private void alur(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Jurnal.ALUR);
		if (rows.isEmpty()) {
			kosong(l, TeksTempat.k("jurnal.alur_kosong"), x0, y0, x1, y1);
			return;
		}
		daftarAlur.gambar(l, "alur", x0, y0, x1, y1, rows.size(), 44, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			boolean buka = e.b(Jurnal.TERBUKA);
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			// chapter seal with the number
			Brush.disc(lk.g, rx0 + 12, ry0 + 12, 9.0F, buka ? TempatWarna.SEGEL : 0xFF8D928E);
			Brush.centered(lk.g, font, Component.literal(Integer.toString(e.i(Jurnal.BAB, i + 1))), rx0 + 12, ry0 + 8, 0xFFFFF0E0, 1.0F, false);
			if (!buka) {
				lk.teks(TeksTempat.k("jurnal.bab_tersegel"), rx0 + 26, ry0 + 8, rx1 - rx0 - 30, TempatWarna.terkunci(gaya.teks));
				return;
			}
			lk.teks(Brush.bold(e.teks(LembarKunci.JUDUL)), rx0 + 26, ry0 + 3, rx1 - rx0 - 30, gaya.teks);
			lk.bungkus(e.teks(Jurnal.RINGKASAN), rx0 + 26, ry0 + 14, rx1 - rx0 - 30, gaya.teksLemah, 1.0F, ry1 - ry0 - 15);
		}, null, null);
	}
}
