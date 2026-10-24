package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.DaftarGulir;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.LabelHarga;
import dev.kultivator.client.tempat.ui.kit.LayarTempatDasar;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Kafilah;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * {@code kafilah} (DESIGN 38.U.3.9): the town side of caravans on the roads: each caravan's route from its origin to its
 * destination with where it is now, the risk of the road (a red bar) and the escort reward, "Kawal".
 */
public final class LayarKafilah extends LayarTempatDasar {

	private final DaftarGulir daftar = new DaftarGulir();

	public LayarKafilah(int npcId, CompoundTag lembar) {
		super(Kafilah.LAYAR, npcId, lembar, TeksTempat.k("layar.kafilah"), TempatWarna.Gaya.KAYU);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of();
	}

	@Override
	protected void isi(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(LembarKunci.DAFTAR);
		if (rows.isEmpty()) {
			kosong(l, TeksTempat.k("kafilah.kosong"), x0, y0, x1, y1);
			return;
		}
		daftar.gambar(l, "kafilah", x0, y0, x1, y1, rows.size(), 46, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			IkonTempat.gambar(lk.g, LembarKunci.Ikon.KAFILAH, rx0 + 3, ry0 + 3, 16, 0xFFFFFFFF);
			int bx = rx1 - 70;
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 23, ry0 + 3, bx - rx0 - 28, gaya.teks, 0.9F);
			lk.keterangan(TeksTempat.k("kafilah.rute", e.teks(Kafilah.ASAL), e.teks(LembarKunci.TUJUAN)), rx0 + 23, ry0 + 13, bx - rx0 - 28, gaya.teksLemah);
			// route line with the caravan
			int lx0 = rx0 + 8;
			int lx1 = bx - 8;
			int ly = ry0 + 30;
			float p = Math.max(0.0F, Math.min(1.0F, e.f(LembarKunci.KEMAJUAN, 0.0F)));
			for (int x = lx0; x < lx1; x += 4) {
				lk.g.fill(x, ly, x + 2, ly + 1, Palette.argb(0x90, gaya.teksSamar & 0xFFFFFF));
			}
			lk.g.fill(lx0, ly, lx0 + Math.round((lx1 - lx0) * p), ly + 1, TempatWarna.EMAS);
			Brush.disc(lk.g, lx0, ly + 0.5F, 2.0F, TempatWarna.OK);
			Brush.disc(lk.g, lx1, ly + 0.5F, 2.0F, TempatWarna.SEGEL);
			IkonTempat.gambar(lk.g, LembarKunci.Ikon.KAFILAH, Math.round(lx0 + (lx1 - lx0) * p - 5), ly - 9, 10, 0xFFFFFFFF);
			int risiko = Math.max(0, Math.min(100, e.i(LembarKunci.RISIKO, 0)));
			lk.keterangan(TeksTempat.k("kafilah.risiko", risiko), rx0 + 8, ry0 + 35, 70, risiko >= 25 ? 0xFFFF8A7A : gaya.teksSamar);
			lk.g.fill(rx0 + 60, ry0 + 37, rx0 + 100, ry0 + 39, Palette.argb(0x60, 0x000000));
			lk.g.fill(rx0 + 60, ry0 + 37, rx0 + 60 + risiko * 40 / 100, ry0 + 39, TempatWarna.BAHAYA);
			LabelHarga.gambar(lk, e.l(Kafilah.IMBALAN, 0L), -1L, rx1 - 4, ry0 + 32, true, 0.85F, false);
			boolean bisa = e.b(LembarKunci.BISA);
			Component alasan = e.teksAtau(LembarKunci.ALASAN);
			tombolOp(lk, "kawal" + i, bx, ry0 + 6, 66, 15, TeksTempat.k("kafilah.kawal"), bisa, alasan, LembarKunci.op(Kafilah.OP_KAWAL, e.id()));
		}, () -> {
			int i = daftar.pilihan();
			if (i >= 0 && i < rows.size() && rows.get(i).b(LembarKunci.BISA) && tulisBoleh()) {
				kirim(LembarKunci.op(Kafilah.OP_KAWAL, rows.get(i).id()));
			}
		}, null);
	}
}
