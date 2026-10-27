package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Potret;
import dev.kultivator.client.screen.ui.Lembar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.LapakRekrut;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * {@code lapak_rekrut} (DESIGN 38.U.3.9, 38.M.4.3, G77): the recruitment stall on Gate Opening Day. The stall rent first;
 * then the young candidates as cards with their live portrait, spirit root, a short talent line and the chance they accept
 * (a jade bar), "Undang" with the invitations left today. Locked as a whole below hall level 2 ({@code kunci_layar}).
 * Drawn with the single kit at text 1.0.
 * <p>
 * The DESIGN 42.11.11 recruitment office ({@code kantor_rekrut}) is its own page on the single kit,
 * {@code client/dunia/kota42/ui/layar/LayarKantor} (D-42U).
 */
public final class LayarLapakRekrut extends LayarInti {

	private final DaftarGulir calon = new DaftarGulir();

	public LayarLapakRekrut(int npcId, CompoundTag lembar) {
		super(LapakRekrut.LAYAR, npcId, lembar, k("layar.lapak_rekrut"), LembarKunci.GAYA_KAYU);
	}

	@Override
	protected List<Tab> daftarTab() {
		return List.of();
	}

	@Override
	public boolean lebar() {
		return false;
	}

	@Override
	protected void tabBerubah() {
		calon.mulaiUlang(Util.getMillis());
	}

	@Override
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		int y = y0;
		boolean sudahSewa = lembar.b(LapakRekrut.SUDAH_SEWA);
		int sisa = lembar.i(LapakRekrut.UNDANG_SISA, 0);
		if (!sudahSewa) {
			long sewa = lembar.l(LapakRekrut.SEWA, 0L);
			int bw = Math.min(130, (x1 - x0) / 2);
			l.kartu(x0, y, x1, y + 22, 0.0F, false);
			l.teks(k("rekrut.sewa_dulu"), x0 + 5, y + 7, x1 - x0 - bw - 14, l.gaya.teks);
			boolean cukup = uang() < 0 || sewa <= uang();
			tombolOp(l, "sewa", x1 - bw - 4, y + 4, bw, TOMBOL_H, k("rekrut.sewa", Brush.angka(sewa)), cukup, k("harga.kurang", Brush.angka(sewa - Math.max(0L, uang()))),
					LapakRekrut.OP_SEWA);
			y += 26;
		} else {
			l.teks(k("rekrut.undang_sisa", sisa), x0, y + 1, x1 - x0, sisa > 0 ? l.gaya.teksLemah : l.gaya.turun());
			y += 12;
		}
		List<Lembar> rows = lembar.daftar(LapakRekrut.CALON);
		if (rows.isEmpty()) {
			kosong(l, k("rekrut.kosong"), x0, y, x1, y1);
			return;
		}
		calon.gambar(l, "calon", x0, y, x1, y1, rows.size(), 42, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			Potret.gambar(lk, rx0 + 2, ry0 + 3, 36, e.i(LembarKunci.POTRET, -1), LembarKunci.Ikon.LAPAK_SEKTE, false, sorot ? 0.6F : 0.0F);
			int tx = rx0 + 42;
			int bw = 62;
			int bx = rx1 - bw - 4;
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), tx, ry0 + 2, bx - tx - 4, lk.gaya.teks);
			lk.teks(k("rekrut.akar", e.teks(LapakRekrut.AKAR)), tx, ry0 + 12, bx - tx - 4, lk.gaya.teksLemah);
			lk.teks(e.teks(LapakRekrut.BAKAT), tx, ry0 + 22, rx1 - tx - 4, lk.gaya.teksSamar);
			int peluang = Math.max(0, Math.min(100, e.i(LembarKunci.PELUANG, 0)));
			Component pt = k("rekrut.peluang", peluang);
			int pw = lk.font.width(pt);
			Bilah.gambar(lk, tx, ry0 + 35, rx1 - pw - 10, peluang / 100.0F, Palette.JADE_LIGHT, new float[0]);
			lk.kanan(pt, rx1 - 4, ry0 + 32, lk.gaya.teksLemah);
			Component alasan = e.teksAtau(LembarKunci.ALASAN);
			boolean bisa = e.bisa() && sudahSewa && sisa > 0;
			if (alasan == null) {
				alasan = !sudahSewa ? k("rekrut.sewa_dulu") : k("rekrut.undangan_habis");
			}
			tombolOp(lk, "undang" + i, bx, ry0 + 4, bw, TOMBOL_H, k("rekrut.undang"), bisa, alasan, LembarKunci.op(LapakRekrut.OP_UNDANG, e.id()));
		}, null, null);
	}
}
