package dev.kultivator.client.dunia.kota42.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.GulirIsi;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Potret;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.Lembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Perkara;
import dev.kultivator.dunia.kota42.ui.LembarPerkara;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * {@code perkara} (DESIGN 42.11.9, keys {@link Perkara}) — one hearing, on the single kit (it was the court screen's
 * half-built 42 mode). The head names the case and the days left before it goes stale; small chips switch between the
 * other open cases. Three tabs, each one scrolled column (Up Down PgUp PgDn Home End):
 * <ul>
 * <li>Sidang — the two parties face to face (side by side in a wide box, stacked in a narrow one) with portrait, role, age,
 * job, temperament and their claim, then the witnesses: "Panggil" turns a row into a speech bubble with the testimony.</li>
 * <li>Bukti — item, note and gossip cards; "Telusuri" follows a gossip to its spreader and shows the chain.</li>
 * <li>Putusan — the bribe envelope in danger colours (accept asks the confirm seal), the result seal, and three verdicts
 * with their estimated consequence, each asking the confirm seal.</li>
 * </ul>
 */
public final class LayarPerkara extends LayarKotaDasar {

	private static final String T_SIDANG = "sidang";
	private static final String T_BUKTI = "bukti";
	private static final String T_PUTUSAN = "putusan";

	private final GulirIsi gulir = new GulirIsi();

	public LayarPerkara(int npcId, CompoundTag lembar) {
		super(Perkara.LAYAR, npcId, lembar, SketsaKota.t("perkara.judul"), LembarKunci.GAYA_BATU);
	}

	@Override
	protected List<Tab> daftarTab() {
		return List.of(tab(T_SIDANG, "kota.tab.sidang", LembarKunci.Ikon.YAMEN), tab(T_BUKTI, "kota.tab.bukti", Glyphs.Atlas.GULUNGAN),
				tab(T_PUTUSAN, "kota.tab.putusan", LembarKunci.Ikon.CAP_MERAH));
	}

	@Override
	protected void tabBerubah() {
		gulir.atas();
	}

	@Override
	protected @Nullable Component petunjuk() {
		return SketsaKota.t("perkara.petunjuk");
	}

	@Override
	public String helpTopic() {
		return KotaLembar.TOPIK_KEHIDUPAN;
	}

	@Override
	protected @Nullable GulirIsi gulirTab() {
		return gulir;
	}

	@Override
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar c = lembar.sub(Perkara.PERKARA);
		if (c.kosongSemua()) {
			kosong(l, SketsaKota.t("perkara.kosong"), x0, y0, x1, y1);
			return;
		}
		int y = kepalaKasus(l, c, x0, y0, x1);
		int xr = x1 - 6;
		int yIsi = gulir.mulai(l, x0, y, xr, y1);
		int akhir = switch (tab) {
			case T_BUKTI -> bukti(l, x0, yIsi, xr);
			case T_PUTUSAN -> putusan(l, x0, yIsi, xr);
			default -> sidang(l, x0, yIsi, xr);
		};
		gulir.selesai(l, akhir + 4);
	}

	/** Case name with the days left, its one-line summary and the chips of the other open cases; returns the y under them. */
	private int kepalaKasus(Lukis l, Lembar c, int x0, int y0, int x1) {
		int sisa = c.i(LembarKunci.SISA_HARI, 0);
		Component kanan = SketsaKota.t("perkara.sisa", sisa);
		int kw = l.font.width(kanan) + 6;
		l.teks(Brush.bold(c.teks(LembarKunci.NAMA)), x0, y0, x1 - x0 - kw, l.gaya.teks);
		l.kanan(kanan, x1, y0, sisa <= 1 ? Palette.BAHAYA : l.gaya.teksLemah);
		l.teks(c.teks(LembarKunci.TEKS), x0, y0 + 11, x1 - x0, l.gaya.teksLemah);
		int y = y0 + 23;
		List<Lembar> lain = lembar.daftar(LembarPerkara.TERBUKA);
		if (lain.size() > 1) {
			int cx = x0;
			int baris = 0;
			for (Lembar o : lain) {
				Component n = Component.literal("#" + o.id());
				int w = Math.max(TalismanButton.MIN_W, l.font.width(n) + 14);
				if (cx + w > x1) {
					if (++baris >= 2) {
						break;
					}
					cx = x0;
					y += 15;
				}
				boolean dipilih = o.b(KotaLembar.PILIH);
				TalismanButton.gambar(l, "kasus" + o.id(), cx, y, w, 13, n, !dipilih, null,
						() -> kirim(LembarKunci.op(LembarKunci.OP_TAB, Perkara.LAYAR, o.id())), false, dipilih);
				cx += w + 3;
			}
			y += 17;
		}
		return y;
	}

	// =================================================================== Sidang

	private int sidang(Lukis l, int x0, int y0, int x1) {
		List<Lembar> pihak = lembar.daftar(Perkara.PIHAK);
		boolean dua = x1 - x0 >= 300 && pihak.size() >= 2;
		int y = y0;
		int tinggiKartu = 74;
		int mid = (x0 + x1) / 2;
		for (int i = 0; i < pihak.size() && i < 2; i++) {
			int bx0 = dua ? (i == 0 ? x0 : mid + 4) : x0;
			int bx1 = dua ? (i == 0 ? mid - 4 : x1) : x1;
			int by = dua ? y0 : y0 + i * (tinggiKartu + 6);
			kartuPihak(l, pihak.get(i), bx0, by, bx1, by + tinggiKartu);
		}
		y = dua ? y0 + tinggiKartu : y0 + Math.min(2, pihak.size()) * (tinggiKartu + 6) - 6;
		if (pihak.size() >= 2) {
			int vy = dua ? y0 + tinggiKartu / 2 - 9 : y0 + tinggiKartu + 3 - 9;
			Glyphs.Atlas.stempel(l.g, Glyphs.Atlas.Stempel.BULAT, mid - 9, vy, 18, 18, Palette.argb(0xD0, 0x7A1A1A));
			IkonKota.gambar(l.g, IkonKota.TIMBANGAN, mid - 6, vy + 3, 12);
		}
		y += 6;
		y += l.kepala(SketsaKota.t("perkara.saksi"), x0, y, x1) + 1;
		List<Lembar> saksi = lembar.daftar(Perkara.SAKSI);
		if (saksi.isEmpty()) {
			l.samar(SketsaKota.t("perkara.saksi_kosong"), x0 + 2, y, x1 - x0 - 4);
			return y + 12;
		}
		for (int i = 0; i < saksi.size(); i++) {
			y = baris(l, saksi.get(i), i, x0, y, x1);
		}
		return y;
	}

	private void kartuPihak(Lukis l, Lembar p, int x0, int y0, int x1, int y1) {
		l.kartu(x0, y0, x1, y1, 0.0F, false);
		int ps = 36;
		Potret.gambar(l, x0 + 4, y0 + 4, ps, p.i(LembarKunci.POTRET, -1), -1, false, 0.0F);
		int tx = x0 + ps + 10;
		int tw = x1 - tx - 4;
		boolean penggugat = KotaLembar.Perkara.PP_PENGGUGAT.equals(p.s(Perkara.PERAN_PIHAK));
		l.teks(SketsaKota.t("perkara.pihak." + p.s(Perkara.PERAN_PIHAK)), tx, y0 + 3, tw, penggugat ? Palette.PERINGATAN : l.gaya.teksSamar);
		l.teks(Brush.bold(p.teks(LembarKunci.NAMA)), tx, y0 + 13, tw, l.gaya.teks);
		l.teks(SketsaKota.umurKerja(p), tx, y0 + 24, tw, l.gaya.teksLemah);
		if (p.ada(KotaLembar.WATAK)) {
			l.teks(SketsaKota.t("watak." + p.s(KotaLembar.WATAK)), tx, y0 + 35, tw, Palette.PERINGATAN);
		}
		// the claim as a quote under the portrait
		l.bungkus(SketsaKota.t("perkara.klaim", p.teks(Perkara.KLAIM)), x0 + 5, y0 + 44, x1 - x0 - 10, l.gaya.teks, y1 - y0 - 46);
	}

	/** One witness: the person row, then "Panggil" or the testimony bubble. Returns the y under it. */
	private int baris(Lukis l, Lembar s, int i, int x0, int y, int x1) {
		boolean dipanggil = s.b(Perkara.DIPANGGIL);
		int bw = TalismanButton.lebar(l.font, SketsaKota.t("perkara.panggil"), 56, 90);
		int tinggi = 34;
		KotaGambar.warga(l, s, x0, y, x1, y + tinggi, false, false, dipanggil ? 0 : bw + 4);
		if (!dipanggil) {
			tombolOp(l, "panggil" + i, x1 - bw - 4, y + (tinggi - TOMBOL_H) / 2, bw, TOMBOL_H, SketsaKota.t("perkara.panggil"), true, null,
					LembarKunci.op(Perkara.OP_PANGGIL, s.i(KotaLembar.WARGA, -1)));
			return y + tinggi + 3;
		}
		Component kata = s.teks(Perkara.KESAKSIAN);
		int lebar = x1 - x0 - 16;
		int baris = Math.max(1, l.font.split(kata, Math.max(10, lebar - 8)).size());
		int bh = baris * 9 + 8;
		int by = y + tinggi + 2;
		l.g.fill(x0 + 12, by, x1, by + bh, Palette.argb(0xE0, 0xF4ECD4));
		l.g.fill(x0 + 8, by + 3, x0 + 12, by + 7, Palette.argb(0xE0, 0xF4ECD4));
		l.bungkus(kata, x0 + 16, by + 4, lebar - 8, Palette.INK);
		return by + bh + 4;
	}

	// =================================================================== Bukti

	private int bukti(Lukis l, int x0, int y0, int x1) {
		List<Lembar> rows = lembar.daftar(Perkara.BUKTI);
		if (rows.isEmpty()) {
			l.teks(SketsaKota.t("perkara.bukti_kosong"), x0 + 2, y0 + 4, x1 - x0 - 4, l.gaya.teksLemah);
			return y0 + 20;
		}
		int y = y0;
		int bw = TalismanButton.lebar(l.font, SketsaKota.t("perkara.telusuri"), 58, 100);
		for (int i = 0; i < rows.size(); i++) {
			Lembar b = rows.get(i);
			String jenis = b.s(LembarKunci.JENIS);
			boolean telusur = b.b(Perkara.DITELUSURI);
			int tx = x0 + 28;
			int tw = x1 - tx - 6;
			Component isi = b.teks(LembarKunci.TEKS);
			int isiBaris = Math.max(1, l.font.split(isi, Math.max(10, tw)).size());
			List<Lembar> rantai = b.daftar(Perkara.RANTAI);
			Component rantaiTeks = null;
			if (!rantai.isEmpty()) {
				Component chain = Component.empty();
				for (int r = 0; r < rantai.size(); r++) {
					chain = chain.copy().append(r == 0 ? Component.empty() : Component.literal(" ← ")).append(rantai.get(r).teks(LembarKunci.NAMA));
				}
				rantaiTeks = SketsaKota.t("perkara.rantai", chain);
			}
			int rantaiBaris = rantaiTeks == null ? 0 : Math.max(1, l.font.split(rantaiTeks, Math.max(10, tw)).size());
			int tombolBaris = telusur ? 0 : TOMBOL_H + 3;
			int tinggi = 6 + 11 + isiBaris * 9 + (rantaiBaris > 0 ? rantaiBaris * 9 + 2 : 0) + tombolBaris + 3;
			l.kartu(x0, y, x1, y + tinggi, 0.0F, false);
			if (KotaLembar.Perkara.BK_BARANG.equals(jenis) && !b.barang().isEmpty()) {
				ItemStack item = b.barang();
				l.g.item(item, x0 + 6, y + 6);
			} else {
				Glyphs.Atlas.ikon(l.g, KotaLembar.Perkara.BK_GOSIP.equals(jenis) ? Glyphs.Atlas.RANTAI : Glyphs.Atlas.GULUNGAN, x0 + 6, y + 6, 16, 0xFFFFFFFF);
			}
			int kanan = telusur ? 14 : 0;
			l.teks(Brush.bold(b.teks(LembarKunci.NAMA)), tx, y + 3, tw - kanan, l.gaya.teks);
			if (telusur) {
				Glyphs.Atlas.ikon(l.g, Glyphs.Atlas.CENTANG, x1 - 14, y + 3, 10, 0xFFFFFFFF);
			}
			int ty = y + 14;
			l.bungkus(isi, tx, ty, tw, telusur ? l.gaya.teks : l.gaya.teksSamar);
			ty += isiBaris * 9;
			if (rantaiTeks != null) {
				l.bungkus(rantaiTeks, tx, ty + 1, tw, Palette.PERINGATAN);
				ty += rantaiBaris * 9 + 2;
			}
			if (!telusur) {
				tombolOp(l, "telusuri" + i, x1 - bw - 4, ty + 1, bw, TOMBOL_H, SketsaKota.t("perkara.telusuri"), true, null,
						LembarKunci.op(Perkara.OP_TELUSURI, b.id()));
			}
			y += tinggi + 4;
		}
		return y;
	}

	// =================================================================== Putusan

	private int putusan(Lukis l, int x0, int y0, int x1) {
		int y = y0;
		Lembar amplop = lembar.sub(Perkara.AMPLOP);
		if (!amplop.kosongSemua()) {
			// a red envelope on the table, in danger colours; accepting it is the dangerous choice
			Component dari = SketsaKota.t("perkara.amplop", amplop.teks(KotaLembar.DARI), Brush.angka(amplop.l(LembarKunci.HARGA, 0L)));
			Component risiko = SketsaKota.t("perkara.amplop_risiko");
			int tw = x1 - x0 - 36;
			int dh = l.font.split(dari, Math.max(10, tw)).size() * 9;
			int rh = l.font.split(risiko, Math.max(10, tw)).size() * 9;
			int tinggi = 6 + dh + 2 + rh + 4 + TOMBOL_H + 5;
			l.g.fill(x0, y, x1, y + tinggi, Palette.argb(0xC0, 0x5A0E0E));
			l.g.fill(x0, y, x1, y + 1, Palette.BAHAYA);
			l.g.fill(x0 + 6, y + 7, x0 + 28, y + 27, 0xFFB3202A);
			l.g.fill(x0 + 6, y + 7, x0 + 28, y + 11, 0xFFD4A637);
			l.bungkus(dari, x0 + 32, y + 5, tw, 0xFFFFE4D0);
			l.bungkus(risiko, x0 + 32, y + 7 + dh, tw, 0xFFE0A090);
			int by = y + 6 + dh + 2 + rh + 4;
			int terima = TalismanButton.lebar(l.font, SketsaKota.t("perkara.amplop_terima"), 60, 130);
			int tolak = TalismanButton.lebar(l.font, SketsaKota.t("perkara.amplop_tolak"), 56, 130);
			int bx = x1 - 4 - tolak;
			tombolOp(l, "amplop_tolak", bx, by, tolak, TOMBOL_H, SketsaKota.t("perkara.amplop_tolak"), true, null, Perkara.OP_AMPLOP_TOLAK);
			yakin(l, "amplop_terima", bx - 4 - terima, by, terima, TOMBOL_H, SketsaKota.t("perkara.amplop_terima"), true, null, Perkara.OP_AMPLOP_TERIMA,
					SegelKonfirmasi.Tingkat.SEDANG, yakinTeks(SketsaKota.t("perkara.amplop_terima")));
			y += tinggi + 5;
		}
		Lembar hasil = lembar.sub(Perkara.HASIL);
		if (!hasil.kosongSemua()) {
			Component teks = hasil.teks(LembarKunci.TEKS);
			int tw = x1 - x0 - 44;
			int th = Math.max(26, l.font.split(teks, Math.max(10, tw)).size() * 9);
			l.sumur(x0, y, x1, y + th + 8);
			Glyphs.Atlas.stempel(l.g, Glyphs.Atlas.Stempel.PERSEGI, x0 + 5, y + 4, 26, 26, Palette.argb(0xE0, Palette.SEAL & 0xFFFFFF));
			l.bungkus(teks, x0 + 36, y + 4, tw, l.gaya.teksSumur());
			y += th + 13;
		}
		y += l.kepala(SketsaKota.t("perkara.putusan"), x0, y, x1) + 1;
		List<Lembar> rows = lembar.daftar(Perkara.PUTUSAN);
		for (Lembar v : rows) {
			int no = v.i(LembarKunci.NO, 0);
			Component teks = v.teks(LembarKunci.TEKS);
			Component akibat = SketsaKota.t("perkara.akibat", v.teks(Perkara.AKIBAT));
			int tw = x1 - x0 - 26;
			int th = Math.max(1, l.font.split(teks, Math.max(10, tw)).size()) * 9;
			int ah = Math.max(1, l.font.split(akibat, Math.max(10, tw)).size()) * 9;
			int tinggi = 5 + th + 2 + ah + 4 + TOMBOL_H + 5;
			l.kartu(x0, y, x1, y + tinggi, 0.0F, false);
			Brush.text(l.g, l.font, Component.literal(Integer.toString(no)), x0 + 6, y + 5, Palette.SEAL, 1.5F, false);
			l.bungkus(teks, x0 + 22, y + 5, tw, l.gaya.teks);
			l.bungkus(akibat, x0 + 22, y + 7 + th, tw, l.gaya.teksLemah);
			int bw = TalismanButton.lebar(l.font, SketsaKota.t("perkara.putuskan"), 70, 140);
			yakin(l, "putus" + no, x1 - bw - 4, y + tinggi - TOMBOL_H - 4, bw, TOMBOL_H, SketsaKota.t("perkara.putuskan"), v.b(LembarKunci.BISA),
					v.teksAtau(LembarKunci.ALASAN), LembarKunci.op(Perkara.OP_PUTUSKAN, no), SegelKonfirmasi.Tingkat.SEDANG,
					yakinTeks(SketsaKota.t("perkara.putuskan")));
			y += tinggi + 4;
		}
		if (rows.isEmpty()) {
			l.samar(SketsaKota.t("perkara.putusan_kosong"), x0 + 2, y, x1 - x0 - 4);
			y += 12;
		}
		return y;
	}
}
