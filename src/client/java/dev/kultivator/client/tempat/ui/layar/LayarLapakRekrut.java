package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.DaftarGulir;
import dev.kultivator.client.tempat.ui.kit.LayarTempatDasar;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.Potret;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.LapakRekrut;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * {@code lapak_rekrut} (DESIGN 38.U.3.9, 38.M.4.3): the recruitment stall on Gate Opening Day. The stall rent first; then
 * the young candidates as cards with their live portrait, spirit root, a short talent line and the chance they accept
 * (a jade bar), "Undang" with the invitations left today. Locked as a whole below hall level 2 ({@code kunci_layar}).
 */
public final class LayarLapakRekrut extends LayarTempatDasar {

	private final DaftarGulir calon = new DaftarGulir();

	public LayarLapakRekrut(int npcId, CompoundTag lembar) {
		super(LapakRekrut.LAYAR, npcId, lembar, TeksTempat.k("layar.lapak_rekrut"), TempatWarna.Gaya.KAYU);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of();
	}

	@Override
	protected void isi(Lukis l, int x0, int y0, int x1, int y1) {
		int y = y0;
		boolean sudahSewa = lembar.b(LapakRekrut.SUDAH_SEWA);
		int sisa = lembar.i(LapakRekrut.UNDANG_SISA, 0);
		if (!sudahSewa) {
			long sewa = lembar.l(LapakRekrut.SEWA, 0L);
			PanelTempat.kartu(l.g, gaya, x0, y, x1, y + 22, 0.0F, false);
			l.teks(TeksTempat.k("rekrut.sewa_dulu"), x0 + 5, y + 7, x1 - x0 - 130, gaya.teks, 0.9F);
			boolean cukup = uang() < 0 || sewa <= uang();
			tombolOp(l, "sewa", x1 - 120, y + 4, 116, 15, TeksTempat.k("rekrut.sewa", TeksTempat.angka(sewa)), cukup,
					TeksTempat.k("harga.kurang", TeksTempat.angka(sewa - Math.max(0L, uang()))), LapakRekrut.OP_SEWA);
			y += 26;
		} else {
			l.keterangan(TeksTempat.k("rekrut.undang_sisa", sisa), x0, y + 1, x1 - x0, sisa > 0 ? gaya.teksLemah : TempatWarna.BAHAYA);
			y += 11;
		}
		List<Lembar> rows = lembar.daftar(LapakRekrut.CALON);
		if (rows.isEmpty()) {
			kosong(l, TeksTempat.k("rekrut.kosong"), x0, y, x1, y1);
			return;
		}
		calon.gambar(l, "calon", x0, y, x1, y1, rows.size(), 40, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			Potret.gambar(lk, rx0 + 2, ry0 + 2, 35, e.i(LembarKunci.POTRET, -1), LembarKunci.Ikon.LAPAK_SEKTE, false, sorot ? 0.6F : 0.0F);
			int tx = rx0 + 42;
			int bx = rx1 - 64;
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), tx, ry0 + 3, bx - tx - 4, gaya.teks, 0.9F);
			lk.keterangan(TeksTempat.k("rekrut.akar", e.teks(LapakRekrut.AKAR)), tx, ry0 + 13, bx - tx - 4, gaya.teksLemah);
			lk.keterangan(e.teks(LapakRekrut.BAKAT), tx, ry0 + 21, bx - tx - 4, gaya.teksSamar);
			int peluang = Math.max(0, Math.min(100, e.i(LembarKunci.PELUANG, 0)));
			int barX1 = bx - 8;
			lk.g.fill(tx, ry0 + 31, barX1, ry0 + 34, Palette.argb(0x50, 0x000000));
			lk.g.fill(tx, ry0 + 31, tx + Math.round((barX1 - tx) * peluang / 100.0F), ry0 + 34, Palette.JADE_LIGHT);
			Component alasan = e.teksAtau(LembarKunci.ALASAN);
			boolean bisa = e.bisa() && sudahSewa && sisa > 0;
			if (alasan == null) {
				alasan = !sudahSewa ? TeksTempat.k("rekrut.sewa_dulu") : TeksTempat.k("rekrut.undangan_habis");
			}
			tombolOp(lk, "undang" + i, bx, ry0 + 6, 60, 15, TeksTempat.k("rekrut.undang"), bisa, alasan, LembarKunci.op(LapakRekrut.OP_UNDANG, e.id()));
			lk.kanan(TeksTempat.k("rekrut.peluang", peluang), rx1 - 4, ry0 + 26, gaya.teksLemah, 0.7F);
		}, null, null);
	}
}
