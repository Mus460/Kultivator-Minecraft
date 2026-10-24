package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.tempat.ui.kit.DaftarGulir;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.KartuBarang;
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
import dev.kultivator.dunia.tempat.ui.LembarKunci.PasarBinatang;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * {@code pasar_binatang} (DESIGN 38.U.3.9): the spirit beast market. "Kandang": cubs in their pens with the real model
 * turning on the pedestal (drag to turn it), price, stock (new cubs every 3 days). "Sesaji": offerings as goods cards.
 * "Pelatih": the player's nearby beasts with the trainer's fee (once a day each). "Inti": beast cores, bought or sold.
 */
public final class LayarPasarBinatang extends LayarTempatDasar {

	private final DaftarGulir daftar = new DaftarGulir();
	private final Pratinjau3D pratinjau = new Pratinjau3D();

	public LayarPasarBinatang(int npcId, CompoundTag lembar) {
		super(PasarBinatang.LAYAR, npcId, lembar, TeksTempat.k("layar.pasar_binatang"), TempatWarna.Gaya.KAYU);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of(tabBaru(PasarBinatang.TAB_KANDANG, "tab.kandang", LembarKunci.Ikon.PASAR_BINATANG),
				tabBaru(PasarBinatang.TAB_SESAJI, "tab.sesaji", IkonTempat.DUPA), tabBaru(PasarBinatang.TAB_PELATIH, "tab.pelatih", IkonTempat.HATI),
				tabBaru(PasarBinatang.TAB_INTI, "tab.inti", LembarKunci.Ikon.BATU_TINGGI));
	}

	@Override
	protected void tabBerubah() {
		daftar.mulaiUlang(Util.getMillis());
	}

	private String kunciDaftar() {
		return switch (tab) {
			case PasarBinatang.TAB_SESAJI -> PasarBinatang.SESAJI;
			case PasarBinatang.TAB_PELATIH -> PasarBinatang.BINATANG;
			case PasarBinatang.TAB_INTI -> PasarBinatang.INTI;
			default -> PasarBinatang.KANDANG;
		};
	}

	@Override
	protected void isi(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(kunciDaftar());
		if (rows.isEmpty()) {
			kosong(l, TeksTempat.k("pasar.kosong." + tab), x0, y0, x1, y1);
			return;
		}
		Kotak[] k = bagi(x0, y0, x1, y1);
		long uang = uang();
		long muncul = diterima;
		boolean pelatih = PasarBinatang.TAB_PELATIH.equals(tab);
		daftar.gambar(l, "daftar", k[0].x0(), k[0].y0(), k[0].x1(), k[0].y1(), rows.size(), 22, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			if (pelatih) {
				PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
				IkonTempat.gambar(lk.g, IkonTempat.HATI, rx0 + 3, ry0 + 3, 16, e.b(PasarBinatang.SUDAH) ? 0x80FFFFFF : 0xFFFFFFFF);
				lk.teks(e.teks(LembarKunci.NAMA), rx0 + 23, ry0 + 2, rx1 - rx0 - 80, gaya.teks, 0.9F);
				lk.keterangan(e.b(PasarBinatang.SUDAH) ? TeksTempat.k("pasar.sudah_latih") : TeksTempat.k("pasar.tier", e.i(PasarBinatang.TIER, 1)), rx0 + 23,
						ry0 + 12, rx1 - rx0 - 80, gaya.teksSamar);
				LabelHarga.gambar(lk, e.l(LembarKunci.HARGA, 0L), uang, rx1 - 4, ry0 + 7, true, 0.85F, false);
			} else {
				KartuBarang.gambar(lk, e, rx0, ry0, rx1, ry1, sorot, pilih, uang, muncul);
			}
		}, this::pakai, null);
		int sel = daftar.pilihan();
		detail(l, k[1], sel >= 0 && sel < rows.size() ? rows.get(sel) : null);
	}

	private String opUntuk(Lembar e) {
		return switch (tab) {
			case PasarBinatang.TAB_PELATIH -> LembarKunci.op(PasarBinatang.OP_LATIH, e.id());
			case PasarBinatang.TAB_INTI -> e.b(PasarBinatang.JUAL) ? LembarKunci.op(PasarBinatang.OP_JUAL, e.id()) : LembarKunci.op(PasarBinatang.OP_BELI, e.id());
			default -> LembarKunci.op(PasarBinatang.OP_BELI, e.id());
		};
	}

	private void pakai() {
		List<Lembar> rows = lembar.daftar(kunciDaftar());
		int i = daftar.pilihan();
		if (i >= 0 && i < rows.size() && rows.get(i).bisa() && tulisBoleh()) {
			kirim(opUntuk(rows.get(i)));
		}
	}

	private void detail(Lukis l, Kotak k, Lembar e) {
		PanelTempat.kartu(l.g, gaya, k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
		if (e == null) {
			return;
		}
		int x0 = k.x0() + 5;
		int x1 = k.x1() - 5;
		int y = k.y0() + 4;
		int pv = tata == Tata.SEMPIT ? 30 : Math.min(x1 - x0, Math.min(k.h() - 60, tata == Tata.LEBAR ? 110 : 76));
		String entitas = e.s(LembarKunci.ENTITAS);
		ItemStack item = e.barang();
		int px = x0 + (x1 - x0 - pv) / 2;
		pratinjau.gambar(l, "pratinjau", px, y, px + pv, y + pv, item, entitas.isEmpty() ? null : entitas,
				e.i(LembarKunci.IKON, LembarKunci.Ikon.PASAR_BINATANG));
		y += pv + 4;
		l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), x0, y, x1 - x0, TempatWarna.mutu(e.i(LembarKunci.MUTU, -1), gaya.gelap(), gaya.teks));
		y += 11;
		if (e.ada(LembarKunci.SISA_HARI)) {
			l.keterangan(TeksTempat.k("pasar.stok_baru", TeksTempat.hari(e.i(LembarKunci.SISA_HARI, 0))), x0, y, x1 - x0, gaya.teksSamar);
			y += 9;
		}
		Component cat = e.teksAtau(LembarKunci.CATATAN);
		if (cat != null) {
			l.bungkus(cat, x0, y, x1 - x0, gaya.teksLemah, 0.8F, k.y1() - 22 - y);
		}
		long harga = e.l(LembarKunci.HARGA, 0L);
		boolean jual = PasarBinatang.TAB_INTI.equals(tab) && e.b(PasarBinatang.JUAL);
		boolean cukup = jual || uang() < 0 || harga <= uang();
		Component label = switch (tab) {
			case PasarBinatang.TAB_PELATIH -> TeksTempat.k("pasar.latih", TeksTempat.angka(harga));
			default -> jual ? TeksTempat.k("pasar.jual", TeksTempat.angka(harga)) : TeksTempat.k("pasar.beli", TeksTempat.angka(harga));
		};
		boolean sudah = PasarBinatang.TAB_PELATIH.equals(tab) && e.b(PasarBinatang.SUDAH);
		Component alasan = sudah ? TeksTempat.k("pasar.sudah_latih") : e.teksAtau(LembarKunci.ALASAN);
		if (alasan == null && !cukup) {
			alasan = TeksTempat.k("harga.kurang", TeksTempat.angka(harga - Math.max(0L, uang())));
		}
		tombolOp(l, "aksi", x0, k.y1() - 19, x1 - x0, 15, label, e.bisa() && cukup && !sudah, alasan, opUntuk(e));
	}
}
