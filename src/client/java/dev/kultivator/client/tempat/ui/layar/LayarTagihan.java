package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.LabelHarga;
import dev.kultivator.client.tempat.ui.kit.LayarTempatDasar;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.Potret;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Tagihan;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * {@code tagihan} (DESIGN 38.U.3.7): the damage bill on a narrow sheet of paper. Three variants:
 * <ul>
 * <li><b>biasa</b>: the collector's portrait and line, the itemised list (block icon, count, subtotal), the 30 % labour,
 * the total, "Bayar", "Tawar" (once) and "Tolak", and what each choice leads to;</li>
 * <li><b>takut</b>: no numbers; the collector kneels in the portrait, the fear of the place, "Bayar walau tak diminta"
 * and "Pergi";</li>
 * <li><b>donasi</b> (monster damage): the repair progress, the materials still needed, "Donasi", "Antar bahan" and
 * "Bantu".</li>
 * </ul>
 */
public final class LayarTagihan extends LayarTempatDasar {

	public LayarTagihan(int npcId, CompoundTag lembar) {
		super(Tagihan.LAYAR, npcId, lembar, TeksTempat.k("layar.tagihan"), TempatWarna.Gaya.KERTAS);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of();
	}

	@Override
	protected int panelMaks(Tata t) {
		return 300;
	}

	@Override
	protected int panelTinggiMaks(Tata t) {
		return 262;
	}

	@Override
	protected Component judulBawaan() {
		return TeksTempat.k("layar.tagihan");
	}

	@Override
	protected void isi(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar npc = lembar.sub(LembarKunci.NPC);
		String varian = lembar.s(Tagihan.VARIAN, Tagihan.V_BIASA);
		boolean takut = Tagihan.V_TAKUT.equals(varian);
		// a smaller portrait on short screens (GUI scale 4) leaves lines for the bill itself
		int ps = y1 - y0 < 150 ? 30 : 40;
		Potret.gambar(l, x0, y0, ps, npc.i(LembarKunci.POTRET, -1), npc.i(LembarKunci.IKON, -1), takut || npc.b(LembarKunci.TAKUT), 0.3F);
		int tx = x0 + ps + 6;
		IkonTempat.gambar(l.g, IkonTempat.SURAT, tx, y0, 12, 0xFFFFFFFF);
		Component dari = lembar.ada(Tagihan.DARI) ? lembar.teks(Tagihan.DARI) : npc.teks(LembarKunci.NAMA);
		l.teks(Brush.bold(dari), tx + 15, y0 + 2, x1 - tx - 15, gaya.teks, 0.9F);
		// the collector's line: two or three lines beside the portrait (a tooltip holds the rest)
		float h = l.bungkus(Component.literal("“").append(lembar.teks(Tagihan.KALIMAT)).append("”").withStyle(net.minecraft.ChatFormatting.ITALIC), tx,
				y0 + 13, x1 - tx, gaya.teksLemah, 0.8F, Math.max(ps - 13, 16));
		int y = Math.max(y0 + ps + 4, Math.round(y0 + 13 + h + 4));
		switch (varian) {
			case Tagihan.V_TAKUT -> takut(l, x0, y, x1, y1);
			case Tagihan.V_DONASI -> donasi(l, x0, y, x1, y1);
			default -> biasa(l, x0, y, x1, y1);
		}
	}

	private void biasa(Lukis l, int x0, int y, int x1, int y1) {
		PanelTempat.garis(l.g, gaya, x0, x1, y);
		y += 4;
		// the block under the items: labour 12, total 16, buttons 19, one line per consequence
		int akibat = (lembar.ada(Tagihan.AKIBAT_BAYAR) ? 9 : 0) + (lembar.ada(Tagihan.AKIBAT_TOLAK) ? 9 : 0);
		int bawah = 12 + 16 + 19 + akibat;
		List<Lembar> rincian = lembar.daftar(Tagihan.RINCIAN);
		int muat = Math.max(1, (y1 - bawah - y) / 10);
		// more items than lines: the last line sums the rest ("dan 3 lainnya ...... 4,2")
		int tampil = rincian.size() <= muat ? rincian.size() : muat - 1;
		for (int i = 0; i < tampil; i++) {
			Lembar r = rincian.get(i);
			if (!r.barang().isEmpty()) {
				l.g.pose().pushMatrix();
				l.g.pose().translate(x0 + 2, y - 1);
				l.g.pose().scale(0.6F, 0.6F);
				l.g.item(r.barang(), 0, 0);
				l.g.pose().popMatrix();
			}
			Component nama = TeksTempat.k("tagihan.baris", r.teks(LembarKunci.NAMA), r.i(LembarKunci.JUMLAH, 0));
			Component sub = Component.literal(TeksTempat.desimal1(r.f(Tagihan.SUBTOTAL, 0.0F)));
			titik(l, nama, sub, x0 + 14, y, x1);
			y += 10;
		}
		if (tampil < rincian.size()) {
			float sisa = 0.0F;
			List<Component> tip = new java.util.ArrayList<>();
			tip.add(TeksTempat.k("tagihan.rincian_judul"));
			for (int i = tampil; i < rincian.size(); i++) {
				Lembar r = rincian.get(i);
				sisa += r.f(Tagihan.SUBTOTAL, 0.0F);
				tip.add(TeksTempat.k("tagihan.baris", r.teks(LembarKunci.NAMA), r.i(LembarKunci.JUMLAH, 0)).copy()
						.append(Component.literal("  " + TeksTempat.desimal1(r.f(Tagihan.SUBTOTAL, 0.0F)))));
			}
			titik(l, TeksTempat.k("tagihan.lainnya", rincian.size() - tampil), Component.literal(TeksTempat.desimal1(sisa)), x0 + 14, y, x1);
			if (l.di(x0, y, x1, y + 9)) {
				l.tip(tip);
			}
			y += 10;
		}
		Component upah = TeksTempat.k("tagihan.upah", lembar.i(Tagihan.UPAH_PERSEN, 30));
		titik(l, upah, Component.literal(TeksTempat.desimal1(lembar.f(Tagihan.UPAH, 0.0F))), x0 + 14, y, x1);
		y += 12;
		long total = lembar.l(Tagihan.TOTAL, 0L);
		l.g.fill(x1 - 90, y - 2, x1, y - 1, gaya.teks);
		l.kanan(Brush.bold(TeksTempat.k("tagihan.total")), x1 - 60, y + 2, gaya.teks, 1.0F);
		LabelHarga.gambar(l, total, uang(), x1, y + 2, true, 1.0F, false);
		y += 16;
		int bw = (x1 - x0 - 8) / 3;
		boolean cukup = uang() < 0 || total <= uang();
		tombolOp(l, "bayar", x0, y, bw, 15, TeksTempat.k("tagihan.bayar", TeksTempat.angka(total)), cukup,
				TeksTempat.k("harga.kurang", TeksTempat.angka(total - Math.max(0L, uang()))), Tagihan.OP_BAYAR);
		tombolOp(l, "tawar", x0 + bw + 4, y, bw, 15, TeksTempat.k("tagihan.tawar"), !lembar.b(Tagihan.SUDAH_TAWAR), TeksTempat.k("tagihan.sudah_tawar"),
				Tagihan.OP_TAWAR);
		tombolOp(l, "tolak", x0 + 2 * (bw + 4), y, bw, 15, TeksTempat.k("tagihan.tolak"), true, null, Tagihan.OP_TOLAK);
		y += 19;
		Component ab = lembar.teksAtau(Tagihan.AKIBAT_BAYAR);
		if (ab != null) {
			l.keterangan(TeksTempat.k("tagihan.akibat_bayar", ab), x0, y, x1 - x0, 0xFF2E7A4E);
			y += 9;
		}
		Component at = lembar.teksAtau(Tagihan.AKIBAT_TOLAK);
		if (at != null) {
			l.keterangan(TeksTempat.k("tagihan.akibat_tolak", at), x0, y, x1 - x0, TempatWarna.BAHAYA);
		}
	}

	/** "Name ....... 9,6" with a dotted leader. */
	private void titik(Lukis l, Component kiri, Component kanan, int x0, int y, int x1) {
		int kw = Math.round(font.width(kanan) * 0.85F);
		int nw = Math.min(x1 - x0 - kw - 10, Math.round(font.width(kiri) * 0.85F));
		l.teks(kiri, x0, y, x1 - x0 - kw - 10, gaya.teks, 0.85F);
		for (int x = x0 + nw + 3; x < x1 - kw - 3; x += 3) {
			l.g.fill(x, y + 6, x + 1, y + 7, Palette.argb(0x90, gaya.teksSamar & 0xFFFFFF));
		}
		l.kanan(kanan, x1, y, gaya.teks, 0.85F);
	}

	private void takut(Lukis l, int x0, int y, int x1, int y1) {
		int meter = Math.max(0, Math.min(100, lembar.i(Tagihan.TAKUT_METER, 0)));
		l.keterangan(TeksTempat.k("tagihan.ketakutan", meter), x0, y + 2, x1 - x0, gaya.teksLemah);
		y += 12;
		l.g.fill(x0, y, x1, y + 5, Palette.argb(0x40, 0x000000));
		l.g.fillGradient(x0, y, x0 + Math.round((x1 - x0) * meter / 100.0F), y + 5, 0xFFB03030, 0xFF6A1420);
		y += 12;
		int by = y1 - 18;
		l.bungkus(TeksTempat.k("tagihan.takut_jelas"), x0, y, x1 - x0, gaya.teksSamar, 0.8F, by - 3 - y);
		int bw = (x1 - x0 - 4) / 2;
		tombolOp(l, "bayar_rela", x0, by, bw + 20, 15, TeksTempat.k("tagihan.bayar_rela"), true, null, Tagihan.OP_BAYAR_RELA);
		tombolOp(l, "pergi", x0 + bw + 24, by, x1 - x0 - bw - 24, 15, TeksTempat.k("tagihan.pergi"), true, null, Tagihan.OP_PERGI);
	}

	private void donasi(Lukis l, int x0, int y, int x1, int y1) {
		Lembar d = lembar.sub(Tagihan.DONASI);
		float kemajuan = Math.max(0.0F, Math.min(1.0F, d.f(LembarKunci.KEMAJUAN, 0.0F)));
		l.keterangan(TeksTempat.k("tagihan.kemajuan", TeksTempat.persen(kemajuan)), x0, y + 2, x1 - x0, gaya.teksLemah);
		y += 11;
		l.g.fill(x0, y, x1, y + 6, Palette.argb(0x40, 0x000000));
		l.g.fillGradient(x0, y, x0 + Math.round((x1 - x0) * kemajuan), y + 6, 0xFF7AC08A, 0xFF2E7A4E);
		y += 10;
		for (Lembar p : d.daftar(Tagihan.PERLU)) {
			if (y + 10 > y1 - 21) {
				break;
			}
			int ada = p.i(Tagihan.ADA, 0);
			int perlu = p.i(LembarKunci.JUMLAH, 0);
			if (!p.barang().isEmpty()) {
				l.g.pose().pushMatrix();
				l.g.pose().translate(x0, y - 1);
				l.g.pose().scale(0.6F, 0.6F);
				l.g.item(p.barang(), 0, 0);
				l.g.pose().popMatrix();
			}
			l.teks(p.teks(LembarKunci.NAMA), x0 + 12, y, x1 - x0 - 60, gaya.teks, 0.8F);
			l.kanan(Component.literal(ada + " / " + perlu), x1, y, ada >= perlu ? 0xFF2E7A4E : gaya.teksLemah, 0.8F);
			y += 10;
		}
		long min = Math.max(1L, d.l(Tagihan.MIN, 10L));
		int by = y1 - 18;
		int bw = (x1 - x0 - 8) / 3;
		tombolOp(l, "donasi", x0, by, bw, 15, TeksTempat.k("tagihan.donasi", TeksTempat.angka(min)), uang() < 0 || uang() >= min,
				TeksTempat.k("harga.kurang", TeksTempat.angka(min - Math.max(0L, uang()))), LembarKunci.op(Tagihan.OP_DONASI, min));
		tombolOp(l, "antar", x0 + bw + 4, by, bw, 15, TeksTempat.k("tagihan.antar"), true, null, Tagihan.OP_ANTAR);
		tombolOp(l, "bantu", x0 + 2 * (bw + 4), by, bw, 15, TeksTempat.k("tagihan.bantu"), d.b(Tagihan.BANTU), TeksTempat.k("tagihan.tak_bisa_bantu"),
				Tagihan.OP_BANTU);
	}
}
