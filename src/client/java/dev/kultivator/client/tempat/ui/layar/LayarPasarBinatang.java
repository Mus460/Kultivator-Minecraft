package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KartuBarang;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Pratinjau3D;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.PasarBinatang;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * {@code pasar_binatang} (DESIGN 38.U.3.9, G19): the spirit beast market. "Kandang": cubs in their pens with the real model
 * turning on the pedestal (drag to turn it), price, stock (new cubs every 3 days). "Sesaji": offerings as goods cards.
 * "Pelatih": the player's nearby beasts with the trainer's fee (once a day each). "Inti": beast cores, bought or sold.
 * Single kit, text 1.0.
 */
public final class LayarPasarBinatang extends LayarInti {

	private final DaftarGulir daftar = new DaftarGulir();
	private final Pratinjau3D pratinjau = new Pratinjau3D();

	public LayarPasarBinatang(int npcId, CompoundTag lembar) {
		super(PasarBinatang.LAYAR, npcId, lembar, k("layar.pasar_binatang"), LembarKunci.GAYA_KAYU);
	}

	@Override
	protected List<Tab> daftarTab() {
		return List.of(tab(PasarBinatang.TAB_KANDANG, "tab.kandang", LembarKunci.Ikon.PASAR_BINATANG), tab(PasarBinatang.TAB_SESAJI, "tab.sesaji", Glyphs.Atlas.DUPA),
				tab(PasarBinatang.TAB_PELATIH, "tab.pelatih", Glyphs.Atlas.HATI), tab(PasarBinatang.TAB_INTI, "tab.inti", LembarKunci.Ikon.BATU_TINGGI));
	}

	@Override
	protected void tabBerubah() {
		daftar.mulaiUlang(Util.getMillis());
	}

	@Override
	public boolean lebar() {
		return false;
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
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(kunciDaftar());
		if (rows.isEmpty()) {
			kosong(l, k("pasar.kosong." + tab), x0, y0, x1, y1);
			return;
		}
		Kotak[] k = bagi(x0, y0, x1, y1);
		long uang = uang();
		long muncul = diterima;
		boolean pelatih = PasarBinatang.TAB_PELATIH.equals(tab);
		daftar.gambar(l, "daftar", k[0].x0(), k[0].y0(), k[0].x1(), k[0].y1(), rows.size(), 22, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			if (pelatih) {
				lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
				Glyphs.Atlas.ikon(lk.g, Glyphs.Atlas.HATI, rx0 + 3, ry0 + 3, 16, e.b(PasarBinatang.SUDAH) ? 0x80FFFFFF : 0xFFFFFFFF);
				int hw = LabelHarga.gambar(lk, e.l(LembarKunci.HARGA, 0L), uang, rx1 - 4, ry0 + 7, true, false);
				lk.teks(e.teks(LembarKunci.NAMA), rx0 + 23, ry0 + 2, rx1 - rx0 - 31 - hw, lk.gaya.teks);
				lk.teks(e.b(PasarBinatang.SUDAH) ? k("pasar.sudah_latih") : k("pasar.tier", e.i(PasarBinatang.TIER, 1)), rx0 + 23, ry0 + 12, rx1 - rx0 - 31 - hw,
						lk.gaya.teksSamar);
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
		l.kartu(k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
		if (e == null) {
			return;
		}
		int x0 = k.x0() + 5;
		int x1 = k.x1() - 5;
		int y = k.y0() + 4;
		int by = k.y1() - TOMBOL_H - 4;
		int pv = tata() == Tata.SEMPIT ? 30 : Math.min(x1 - x0, Math.min(k.h() - 62, tata() == Tata.LEBAR ? 110 : 76));
		String entitas = e.s(LembarKunci.ENTITAS);
		ItemStack item = e.barang();
		int px = x0 + (x1 - x0 - pv) / 2;
		pratinjau.gambar(l, "pratinjau", px, y, px + pv, y + pv, item, entitas.isEmpty() ? null : entitas, e.i(LembarKunci.IKON, LembarKunci.Ikon.PASAR_BINATANG));
		y += pv + 4;
		l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), x0, y, x1 - x0, mutu(l, e.i(LembarKunci.MUTU, -1)));
		y += 11;
		if (e.ada(LembarKunci.SISA_HARI) && y + 9 <= by) {
			l.samar(k("pasar.stok_baru", Brush.hari(e.i(LembarKunci.SISA_HARI, 0))), x0, y, x1 - x0);
			y += 10;
		}
		Component cat = e.teksAtau(LembarKunci.CATATAN);
		if (cat != null) {
			l.bungkus(cat, x0, y, x1 - x0, l.gaya.teksLemah, by - 3 - y);
		}
		long harga = e.l(LembarKunci.HARGA, 0L);
		boolean jual = PasarBinatang.TAB_INTI.equals(tab) && e.b(PasarBinatang.JUAL);
		boolean cukup = jual || uang() < 0 || harga <= uang();
		Component label = switch (tab) {
			case PasarBinatang.TAB_PELATIH -> k("pasar.latih", Brush.angka(harga));
			default -> jual ? k("pasar.jual", Brush.angka(harga)) : k("pasar.beli", Brush.angka(harga));
		};
		boolean sudah = PasarBinatang.TAB_PELATIH.equals(tab) && e.b(PasarBinatang.SUDAH);
		Component alasan = sudah ? k("pasar.sudah_latih") : e.teksAtau(LembarKunci.ALASAN);
		if (alasan == null && !cukup) {
			alasan = k("harga.kurang", Brush.angka(harga - Math.max(0L, uang())));
		}
		tombolOp(l, "aksi", x0, by, x1 - x0, TOMBOL_H, label, e.bisa() && cukup && !sudah, alasan, opUntuk(e));
	}
}
