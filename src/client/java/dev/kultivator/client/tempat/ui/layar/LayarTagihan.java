package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Potret;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Tagihan;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * {@code tagihan} (DESIGN 38.U.3.7): the damage bill on a narrow sheet of paper (at most 300 px wide in the page). Three
 * variants:
 * <ul>
 * <li><b>biasa</b>: the collector's portrait and line, the itemised list (block icon, count, subtotal), the 30 % labour,
 * the total, "Bayar", "Tawar" (once) and "Tolak", and what each choice leads to;</li>
 * <li><b>takut</b>: no numbers; the collector kneels in the portrait, the fear of the place, "Bayar walau tak diminta"
 * and "Pergi";</li>
 * <li><b>donasi</b> (monster damage): the repair progress, the materials still needed, "Donasi", "Antar bahan" and
 * "Bantu".</li>
 * </ul>
 * Single kit, text 1.0.
 */
public final class LayarTagihan extends LayarInti {

	private static final int LEBAR_MAKS = 300;
	private static final int BARIS = 11;

	public LayarTagihan(int npcId, CompoundTag lembar) {
		super(Tagihan.LAYAR, npcId, lembar, k("layar.tagihan"), LembarKunci.GAYA_KERTAS);
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
	protected Component judulBawaan() {
		return k("layar.tagihan");
	}

	@Override
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		// the narrow sheet of paper in the middle of the page
		if (x1 - x0 > LEBAR_MAKS) {
			int cx = (x0 + x1) / 2;
			x0 = cx - LEBAR_MAKS / 2;
			x1 = cx + LEBAR_MAKS / 2;
		}
		Lembar npc = lembar.sub(LembarKunci.NPC);
		String varian = lembar.s(Tagihan.VARIAN, Tagihan.V_BIASA);
		boolean takut = Tagihan.V_TAKUT.equals(varian);
		// a smaller portrait on short boxes (GUI scale 4) leaves lines for the bill itself
		int ps = y1 - y0 < 150 ? 30 : 40;
		Potret.gambar(l, x0, y0, ps, npc.i(LembarKunci.POTRET, -1), npc.i(LembarKunci.IKON, -1), takut || npc.b(LembarKunci.TAKUT), 0.3F);
		int tx = x0 + ps + 6;
		Glyphs.Atlas.ikon(l.g, Glyphs.Atlas.SURAT, tx, y0, 12, 0xFFFFFFFF);
		Component dari = lembar.ada(Tagihan.DARI) ? lembar.teks(Tagihan.DARI) : npc.teks(LembarKunci.NAMA);
		l.teks(Brush.bold(dari), tx + 15, y0 + 2, x1 - tx - 15, l.gaya.teks);
		// the collector's line beside the portrait (a tooltip holds the rest)
		float h = l.bungkus(Component.literal("“").append(lembar.teks(Tagihan.KALIMAT)).append("”").withStyle(ChatFormatting.ITALIC), tx, y0 + 13,
				x1 - tx, l.gaya.teksLemah, Math.max(ps - 13, 18));
		int y = Math.max(y0 + ps + 4, Math.round(y0 + 13 + h + 4));
		switch (varian) {
			case Tagihan.V_TAKUT -> takut(l, x0, y, x1, y1);
			case Tagihan.V_DONASI -> donasi(l, x0, y, x1, y1);
			default -> biasa(l, x0, y, x1, y1);
		}
	}

	/** A small block icon (the item drawn at 10 px; an icon, not text). */
	private static void ikon(Lukis l, ItemStack item, int x, int y) {
		if (item.isEmpty()) {
			return;
		}
		l.g.pose().pushMatrix();
		l.g.pose().translate(x, y);
		l.g.pose().scale(0.625F, 0.625F);
		l.g.item(item, 0, 0);
		l.g.pose().popMatrix();
	}

	private void biasa(Lukis l, int x0, int y, int x1, int y1) {
		l.garis(x0, x1, y);
		y += 4;
		// the block under the items: labour, total, buttons, one line per consequence
		int akibat = (lembar.ada(Tagihan.AKIBAT_BAYAR) ? 10 : 0) + (lembar.ada(Tagihan.AKIBAT_TOLAK) ? 10 : 0);
		int bawah = BARIS + 2 + 14 + TOMBOL_H + 4 + akibat;
		List<Lembar> rincian = lembar.daftar(Tagihan.RINCIAN);
		int muat = Math.max(1, (y1 - bawah - y) / BARIS);
		// more items than lines: the last line sums the rest ("dan 3 lainnya ...... 4,2")
		int tampil = rincian.size() <= muat ? rincian.size() : muat - 1;
		for (int i = 0; i < tampil; i++) {
			Lembar r = rincian.get(i);
			ikon(l, r.barang(), x0 + 1, y - 1);
			Component nama = k("tagihan.baris", r.teks(LembarKunci.NAMA), r.i(LembarKunci.JUMLAH, 0));
			titik(l, nama, Component.literal(Brush.desimal1(r.f(Tagihan.SUBTOTAL, 0.0F))), x0 + 14, y, x1);
			y += BARIS;
		}
		if (tampil < rincian.size()) {
			float sisa = 0.0F;
			List<Component> tip = new ArrayList<>();
			tip.add(k("tagihan.rincian_judul"));
			for (int i = tampil; i < rincian.size(); i++) {
				Lembar r = rincian.get(i);
				sisa += r.f(Tagihan.SUBTOTAL, 0.0F);
				tip.add(k("tagihan.baris", r.teks(LembarKunci.NAMA), r.i(LembarKunci.JUMLAH, 0)).copy()
						.append(Component.literal("  " + Brush.desimal1(r.f(Tagihan.SUBTOTAL, 0.0F)))));
			}
			titik(l, k("tagihan.lainnya", rincian.size() - tampil), Component.literal(Brush.desimal1(sisa)), x0 + 14, y, x1);
			if (l.di(x0, y, x1, y + 9)) {
				l.tip(tip);
			}
			y += BARIS;
		}
		titik(l, k("tagihan.upah", lembar.i(Tagihan.UPAH_PERSEN, 30)), Component.literal(Brush.desimal1(lembar.f(Tagihan.UPAH, 0.0F))), x0 + 14, y, x1);
		y += BARIS + 2;
		long total = lembar.l(Tagihan.TOTAL, 0L);
		l.g.fill(x1 - 100, y - 2, x1, y - 1, l.gaya.teks);
		int hw = LabelHarga.gambar(l, total, uang(), x1, y + 2, true, false);
		l.kanan(Brush.bold(k("tagihan.total")), x1 - hw - 6, y + 2, l.gaya.teks);
		y += 14;
		int bw = (x1 - x0 - 8) / 3;
		boolean cukup = uang() < 0 || total <= uang();
		tombolOp(l, "bayar", x0, y, bw, TOMBOL_H, k("tagihan.bayar", Brush.angka(total)), cukup, k("harga.kurang", Brush.angka(total - Math.max(0L, uang()))),
				Tagihan.OP_BAYAR);
		tombolOp(l, "tawar", x0 + bw + 4, y, bw, TOMBOL_H, k("tagihan.tawar"), !lembar.b(Tagihan.SUDAH_TAWAR), k("tagihan.sudah_tawar"), Tagihan.OP_TAWAR);
		tombolOp(l, "tolak", x0 + 2 * (bw + 4), y, bw, TOMBOL_H, k("tagihan.tolak"), true, null, Tagihan.OP_TOLAK);
		y += TOMBOL_H + 4;
		Component ab = lembar.teksAtau(Tagihan.AKIBAT_BAYAR);
		if (ab != null) {
			l.teks(k("tagihan.akibat_bayar", ab), x0, y, x1 - x0, l.gaya.naik());
			y += 10;
		}
		Component at = lembar.teksAtau(Tagihan.AKIBAT_TOLAK);
		if (at != null) {
			l.teks(k("tagihan.akibat_tolak", at), x0, y, x1 - x0, l.gaya.turun());
		}
	}

	/** "Name ....... 9,6" with a dotted leader. */
	private void titik(Lukis l, Component kiri, Component kanan, int x0, int y, int x1) {
		int kw = l.font.width(kanan);
		int ruang = x1 - x0 - kw - 10;
		int nw = Math.min(ruang, l.font.width(kiri));
		l.teks(kiri, x0, y, ruang, l.gaya.teks);
		for (int x = x0 + nw + 3; x < x1 - kw - 3; x += 3) {
			l.g.fill(x, y + 7, x + 1, y + 8, Palette.argb(0x90, l.gaya.teksSamar & 0xFFFFFF));
		}
		l.kanan(kanan, x1, y, l.gaya.teks);
	}

	private void takut(Lukis l, int x0, int y, int x1, int y1) {
		int meter = Math.max(0, Math.min(100, lembar.i(Tagihan.TAKUT_METER, 0)));
		l.teks(k("tagihan.ketakutan", meter), x0, y + 2, x1 - x0, l.gaya.teksLemah);
		y += 13;
		l.g.fill(x0, y, x1, y + 5, Palette.argb(0x40, 0x000000));
		l.g.fillGradient(x0, y, x0 + Math.round((x1 - x0) * meter / 100.0F), y + 5, 0xFFB03030, 0xFF6A1420);
		y += 11;
		int by = y1 - TOMBOL_H;
		l.bungkus(k("tagihan.takut_jelas"), x0, y, x1 - x0, l.gaya.teksSamar, by - 3 - y);
		int bw = (x1 - x0 - 4) / 2;
		tombolOp(l, "bayar_rela", x0, by, bw + 20, TOMBOL_H, k("tagihan.bayar_rela"), true, null, Tagihan.OP_BAYAR_RELA);
		tombolOp(l, "pergi", x0 + bw + 24, by, x1 - x0 - bw - 24, TOMBOL_H, k("tagihan.pergi"), true, null, Tagihan.OP_PERGI);
	}

	private void donasi(Lukis l, int x0, int y, int x1, int y1) {
		Lembar d = lembar.sub(Tagihan.DONASI);
		float kemajuan = Math.max(0.0F, Math.min(1.0F, d.f(LembarKunci.KEMAJUAN, 0.0F)));
		l.teks(k("tagihan.kemajuan", Brush.persen(kemajuan)), x0, y + 2, x1 - x0, l.gaya.teksLemah);
		y += 12;
		l.g.fill(x0, y, x1, y + 6, Palette.argb(0x40, 0x000000));
		l.g.fillGradient(x0, y, x0 + Math.round((x1 - x0) * kemajuan), y + 6, 0xFF7AC08A, 0xFF2E7A4E);
		y += 10;
		int by = y1 - TOMBOL_H;
		for (Lembar p : d.daftar(Tagihan.PERLU)) {
			if (y + BARIS > by - 3) {
				break;
			}
			int ada = p.i(Tagihan.ADA, 0);
			int perlu = p.i(LembarKunci.JUMLAH, 0);
			ikon(l, p.barang(), x0, y - 1);
			Component jml = Component.literal(ada + " / " + perlu);
			int jw = l.font.width(jml);
			l.teks(p.teks(LembarKunci.NAMA), x0 + 13, y, x1 - x0 - 17 - jw, l.gaya.teks);
			l.kanan(jml, x1, y, ada >= perlu ? l.gaya.naik() : l.gaya.teksLemah);
			y += BARIS;
		}
		long min = Math.max(1L, d.l(Tagihan.MIN, 10L));
		int bw = (x1 - x0 - 8) / 3;
		tombolOp(l, "donasi", x0, by, bw, TOMBOL_H, k("tagihan.donasi", Brush.angka(min)), uang() < 0 || uang() >= min,
				k("harga.kurang", Brush.angka(min - Math.max(0L, uang()))), LembarKunci.op(Tagihan.OP_DONASI, min));
		tombolOp(l, "antar", x0 + bw + 4, by, bw, TOMBOL_H, k("tagihan.antar"), true, null, Tagihan.OP_ANTAR);
		tombolOp(l, "bantu", x0 + 2 * (bw + 4), by, bw, TOMBOL_H, k("tagihan.bantu"), d.b(Tagihan.BANTU), k("tagihan.tak_bisa_bantu"), Tagihan.OP_BANTU);
	}
}
