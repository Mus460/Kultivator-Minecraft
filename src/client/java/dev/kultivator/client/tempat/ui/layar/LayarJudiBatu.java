package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.tempat.ui.kit.DaftarGulir;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.LabelHarga;
import dev.kultivator.client.tempat.ui.kit.LayarTempatDasar;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.Pratinjau3D;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.JudiBatu;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * {@code judi_batu} (DESIGN 38.U.3.9, 38.M.2.4): stone gambling. The rack of raw stones (pebble / chunk / mountain /
 * ancient) with the colour the Mata Batin saw (grey, blue, gold glow); the turning stone; "Mata Batin", "Beli", "Potong".
 * The cutting table plays a 16-tick split when a new cut arrives: the stone shakes, a saw line runs down, the halves
 * part, and the light of the result spills out (nothing: dust; small: green; medium: blue; large: purple; jackpot: a gold
 * burst with rays). Today's history and the daily limits are under the table.
 */
public final class LayarJudiBatu extends LayarTempatDasar {

	private static final long BELAH_MS = 800L;

	private final DaftarGulir rak = new DaftarGulir();
	private final DaftarGulir riwayat = new DaftarGulir();
	private final Pratinjau3D pratinjau = new Pratinjau3D();
	private String potongTerakhir = "";
	private long potongSejak = -1L;

	public LayarJudiBatu(int npcId, CompoundTag lembar) {
		super(JudiBatu.LAYAR, npcId, lembar, TeksTempat.k("layar.judi_batu"), TempatWarna.Gaya.BATU);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of();
	}

	@Override
	protected void setelahLembar(boolean buka) {
		Lembar p = lembar.sub(JudiBatu.POTONG);
		String id = p.id();
		if (!id.isEmpty() && !id.equals(potongTerakhir)) {
			potongTerakhir = id;
			potongSejak = buka ? -1L : Util.getMillis();
			if (!buka) {
				UiSound.ask();
			}
		}
	}

	private static int warnaHasil(String hasil) {
		return switch (hasil) {
			case JudiBatu.HASIL_KECIL -> 0xFF7AD08A;
			case JudiBatu.HASIL_SEDANG -> 0xFF6AA8FF;
			case JudiBatu.HASIL_BESAR -> 0xFFC08AFF;
			case JudiBatu.HASIL_JACKPOT -> 0xFFFFD25A;
			default -> 0xFFA09A90;
		};
	}

	private static int warnaMata(String warna) {
		return switch (warna) {
			case JudiBatu.WARNA_BIRU -> 0xFF6AA8FF;
			case JudiBatu.WARNA_EMAS -> 0xFFFFD25A;
			default -> 0xFFB0B0A8;
		};
	}

	@Override
	protected void isi(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> batu = lembar.daftar(JudiBatu.RAK);
		// limits line
		Component batas = TeksTempat.k("judi.batas", lembar.i(JudiBatu.SISA, 0), lembar.i(JudiBatu.PURBA_SISA, 0), lembar.i(JudiBatu.MATA_BATIN_SISA, 0));
		l.keterangan(batas, x0, y0, x1 - x0, gaya.teksLemah);
		y0 += 11;
		Kotak[] k = bagi(x0, y0, x1, y1);
		Kotak kiri = k[0];
		long uang = uang();
		if (batu.isEmpty()) {
			kosong(l, TeksTempat.k("judi.rak_kosong"), kiri.x0(), kiri.y0(), kiri.x1(), kiri.y1());
		} else {
			rak.gambar(l, "rak", kiri.x0(), kiri.y0(), kiri.x1(), kiri.y1(), batu.size(), 22, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
				Lembar e = batu.get(i);
				PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
				if (e.b(JudiBatu.DIPERIKSA)) {
					IkonTempat.cahaya(lk.g, rx0 - 2, ry0 - 3, 26, 26, Palette.argb(0xD0, warnaMata(e.s(JudiBatu.WARNA)) & 0xFFFFFF));
				}
				if (!e.barang().isEmpty()) {
					lk.g.item(e.barang(), rx0 + 3, ry0 + 3);
				} else {
					IkonTempat.gambar(lk.g, LembarKunci.Ikon.BATU_ASAL, rx0 + 3, ry0 + 3, 16, 0xFFFFFFFF);
				}
				int hw = LabelHarga.gambar(lk, e.l(LembarKunci.HARGA, 0L), uang, rx1 - 4, ry0 + 7, true, 0.85F, false);
				lk.teks(e.teks(LembarKunci.NAMA), rx0 + 23, ry0 + 2, rx1 - rx0 - 30 - hw, gaya.teks, 0.9F);
				lk.keterangan(TeksTempat.k("judi.tingkat." + e.s(JudiBatu.TINGKAT, JudiBatu.TINGKAT_KERIKIL)), rx0 + 23, ry0 + 12, rx1 - rx0 - 30 - hw,
						gaya.teksSamar);
			}, null, null);
		}
		meja(l, k[1], batu);
	}

	private void meja(Lukis l, Kotak k, List<Lembar> batu) {
		PanelTempat.kartu(l.g, gaya, k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
		int x0 = k.x0() + 5;
		int x1 = k.x1() - 5;
		int y = k.y0() + 4;
		int sel = rak.pilihan();
		Lembar e = sel >= 0 && sel < batu.size() ? batu.get(sel) : null;
		Lembar potong = lembar.sub(JudiBatu.POTONG);
		long t = potongSejak < 0L ? Long.MAX_VALUE : Util.getMillis() - potongSejak;
		int tableH = Math.min(84, k.h() - 70);
		float cx = (x0 + x1) / 2.0F;
		float cy = y + tableH / 2.0F;
		// the cutting table: dark slab with a brass rail
		l.g.fill(x0, y + tableH - 10, x1, y + tableH, 0xFF3A3530);
		l.g.fill(x0, y + tableH - 11, x1, y + tableH - 10, TempatWarna.EMAS_GELAP);
		if (potong.ada(LembarKunci.ID) && t < BELAH_MS + 2500L) {
			belah(l, potong, cx, cy, tableH, t);
		} else if (e != null && tableH >= 32) {
			int pv = tableH - 12;
			pratinjau.gambar(l, "pratinjau", Math.round(cx - pv / 2.0F), y, Math.round(cx + pv / 2.0F), y + pv, e.barang(), null, LembarKunci.Ikon.BATU_ASAL);
			if (e.b(JudiBatu.DIPERIKSA)) {
				int w = warnaMata(e.s(JudiBatu.WARNA));
				Brush.halo(l.g, cx, cy - 4, pv * 0.32F, 3.0F, w & 0xFFFFFF, 0.6F + 0.4F * l.denyut());
				l.keterangan(TeksTempat.k("judi.mata_lihat." + e.s(JudiBatu.WARNA, JudiBatu.WARNA_ABU)), x0, y + tableH - 9, x1 - x0, 0xFFF0E8D8);
			}
		}
		y += tableH + 4;
		// buttons
		int bw = (x1 - x0 - 8) / 3;
		boolean ada = e != null;
		boolean mataBoleh = lembar.b(JudiBatu.MATA_BATIN_BOLEH) && lembar.i(JudiBatu.MATA_BATIN_SISA, 0) > 0 && ada && !e.b(JudiBatu.DIPERIKSA);
		Component mataAlasan = !lembar.b(JudiBatu.MATA_BATIN_BOLEH) ? TeksTempat.k("judi.mata_terkunci") : ada && e.b(JudiBatu.DIPERIKSA)
				? TeksTempat.k("judi.sudah_diperiksa") : TeksTempat.k("judi.mata_habis");
		tombolOp(l, "periksa", x0, y, bw, 15, TeksTempat.k("judi.mata_batin"), mataBoleh, mataAlasan,
				LembarKunci.op(JudiBatu.OP_PERIKSA, ada ? e.id() : ""));
		long harga = ada ? e.l(LembarKunci.HARGA, 0L) : 0L;
		boolean cukup = uang() < 0 || harga <= uang();
		Component alasanBeli = ada ? (e.teksAtau(LembarKunci.ALASAN) != null ? e.teks(LembarKunci.ALASAN)
				: TeksTempat.k("harga.kurang", TeksTempat.angka(harga - Math.max(0L, uang())))) : TeksTempat.k("judi.pilih_batu");
		tombolOp(l, "beli", x0 + bw + 4, y, bw, 15, TeksTempat.k("judi.beli"), ada && e.bisa() && cukup, alasanBeli,
				LembarKunci.op(JudiBatu.OP_BELI, ada ? e.id() : ""));
		tombolOp(l, "potong", x0 + 2 * (bw + 4), y, bw, 15, TeksTempat.k("judi.potong"), ada && e.bisa() && cukup, alasanBeli,
				LembarKunci.op(JudiBatu.OP_POTONG, ada ? e.id() : ""));
		y += 19;
		// today's history
		List<Lembar> hist = lembar.daftar(LembarKunci.RIWAYAT);
		if (!hist.isEmpty() && y < k.y1() - 14) {
			l.keterangan(TeksTempat.k("judi.riwayat"), x0, y, x1 - x0, gaya.teksLemah);
			y += 9;
			riwayat.gambar(l, "riwayat", x0, y, x1, k.y1() - 3, hist.size(), 11, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
				Lembar h = hist.get(i);
				String hasil = h.s(JudiBatu.HASIL, JudiBatu.HASIL_KOSONG);
				Brush.disc(lk.g, rx0 + 4, ry0 + 5, 3.0F, warnaHasil(hasil));
				lk.teks(h.teks(LembarKunci.NAMA), rx0 + 10, ry0 + 1.5F, (rx1 - rx0) * 0.5F, gaya.teks, 0.75F);
				lk.keterangan(TeksTempat.k("judi.hasil." + hasil), rx0 + 10 + (rx1 - rx0) * 0.5F, ry0 + 2, (rx1 - rx0) * 0.25F, warnaHasil(hasil));
				if (h.l(LembarKunci.NILAI, 0L) > 0) {
					LabelHarga.gambar(lk, h.l(LembarKunci.NILAI, 0L), -1L, rx1 - 2, ry0 + 1.5F, true, 0.75F, true);
				}
			}, null, null);
		}
	}

	/** The 16-tick split, then the result lingering for a while. */
	private void belah(Lukis l, Lembar p, float cx, float cy, int tableH, long t) {
		String hasil = p.s(JudiBatu.HASIL, JudiBatu.HASIL_KOSONG);
		int warna = warnaHasil(hasil);
		float s = Math.min(1.0F, t / (float) BELAH_MS);
		float size = tableH * 0.62F;
		float shake = s < 0.5F ? (float) Math.sin(t / 18.0D) * 1.5F * (1.0F - s * 2.0F) : 0.0F;
		float part = s < 0.6F ? 0.0F : (s - 0.6F) / 0.4F * size * 0.28F;
		int stone = 0xFF7E766A;
		int stoneDark = 0xFF5A534A;
		// light from inside once the halves part
		if (part > 0.0F) {
			float glow = Math.min(1.0F, part / (size * 0.2F));
			int a = Math.round(220 * glow);
			IkonTempat.cahaya(l.g, Math.round(cx - size), Math.round(cy - size), Math.round(size * 2), Math.round(size * 2), Palette.argb(a, warna & 0xFFFFFF));
			if (JudiBatu.HASIL_JACKPOT.equals(hasil)) {
				for (int r = 0; r < 10; r++) {
					double ang = r * Math.PI / 5.0D + t / 900.0D;
					float len = size * (0.8F + 0.3F * (float) Math.sin(t / 120.0D + r));
					Brush.line(l.g, cx, cy, cx + (float) Math.cos(ang) * len, cy + (float) Math.sin(ang) * len, 1.4F, Palette.argb(a / 2, 0xFFE8A0));
				}
			}
			Brush.disc(l.g, cx, cy, size * 0.18F * glow, warna);
		}
		// two halves
		float hw = size / 2.0F;
		l.g.fill(Math.round(cx - hw - part + shake), Math.round(cy - hw), Math.round(cx - part + shake), Math.round(cy + hw), stone);
		l.g.fill(Math.round(cx + part + shake), Math.round(cy - hw), Math.round(cx + hw + part + shake), Math.round(cy + hw), stoneDark);
		l.g.fill(Math.round(cx - hw - part + shake), Math.round(cy - hw), Math.round(cx - part + shake), Math.round(cy - hw + 3), 0xFF968C7E);
		// the saw line running down before the split
		if (s < 0.6F) {
			float sy = cy - hw + (s / 0.6F) * hw * 2.0F;
			l.g.fill(Math.round(cx - 0.5F + shake), Math.round(cy - hw), Math.round(cx + 0.5F + shake), Math.round(sy), 0xFFF0E8D8);
			Brush.dot(l.g, cx + shake, sy, 1.5F, 0xFFFFF0C0);
		}
		if (s >= 1.0F) {
			Component nama = p.ada(LembarKunci.NAMA) ? p.teks(LembarKunci.NAMA) : TeksTempat.k("judi.hasil." + hasil);
			Brush.fitCentered(l.g, font, Brush.bold(nama), cx, cy + hw + 2, size * 3, warna, 1.0F, 0.6F, true);
			if (!p.barang().isEmpty()) {
				l.g.item(p.barang(), Math.round(cx - 8), Math.round(cy - 8));
			}
		}
	}
}
