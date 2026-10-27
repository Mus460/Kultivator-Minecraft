package dev.kultivator.client.dunia.kota42.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.GulirIsi;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.Lembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Cabang;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * {@code cabang} (DESIGN 42.11.12, keys {@link Cabang}) — a sect branch in a house of the player, on the single kit (it was a
 * mode inside the 41 politics screen; opens from the pact sheet, from the {@code papan_cabang} sign and from the hub).
 * Without a branch: the card and the requirements (what the sign needs). With one: the name and renown, the three kinds as
 * cards ("Pilih" for another one, locked until the change day), the blocks the kind needs with have / need, "Rumah" (opens
 * the house sheet) and "Tutup" (asks the confirm seal), then the guards on duty, what the branch does and its last reports.
 * One scrolled column (two columns in a wide box): Up Down PgUp PgDn Home End.
 */
public final class LayarCabang extends LayarKotaDasar {

	private final GulirIsi gulir = new GulirIsi();

	public LayarCabang(int npcId, CompoundTag lembar) {
		super(Cabang.LAYAR, npcId, lembar, SketsaKota.t("cabang.judul"), LembarKunci.GAYA_GIOK);
	}

	@Override
	protected List<Tab> daftarTab() {
		return List.of(tab(Cabang.LAYAR, "kota.tab.cabang", LembarKunci.Ikon.KUIL));
	}

	@Override
	public String helpTopic() {
		return KotaLembar.TOPIK_SEKTE;
	}

	@Override
	protected @Nullable GulirIsi gulirTab() {
		return gulir;
	}

	private static int ikonJenis(String jenis) {
		return switch (jenis) {
			case Cabang.CJ_TOKO_PIL -> LembarKunci.Ikon.PIL;
			case Cabang.CJ_BALAI_LATIHAN -> LembarKunci.Ikon.ARENA;
			case Cabang.CJ_KLINIK -> LembarKunci.Ikon.TABIB;
			default -> LembarKunci.Ikon.KUIL;
		};
	}

	private long hariIni() {
		return minecraft != null && minecraft.level != null ? minecraft.level.getOverworldClockTime() / 24000L : 0L;
	}

	@Override
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		int y = KotaGambar.kota(l, lembar, x0, y0, x1);
		Lembar c = lembar.sub(Cabang.CABANG);
		int xr = x1 - 6;
		int yIsi = gulir.mulai(l, x0, y, xr, y1);
		int akhir = c.b(Cabang.ADA) ? ada(l, c, x0, yIsi, xr) : belum(l, x0, yIsi, xr);
		gulir.selesai(l, akhir + 4);
	}

	/** No branch yet: the card and the requirements. Returns the y under it. */
	private int belum(Lukis l, int x0, int y0, int x1) {
		Component info = SketsaKota.t("cabang.belum_info");
		int tw = x1 - x0 - 40;
		int ih = Math.max(10, l.font.split(info, Math.max(10, tw)).size() * 9);
		List<Lembar> syarat = lembar.daftar(LembarKunci.SYARAT);
		int tinggi = 6 + 12 + ih + 8 + syarat.size() * 11 + 6;
		l.kartu(x0, y0, x1, y0 + tinggi, 0.0F, false);
		Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.KUIL, x0 + 6, y0 + 6, 24, 0xFFFFFFFF);
		l.teks(Brush.bold(SketsaKota.t("cabang.belum")), x0 + 36, y0 + 5, tw, l.gaya.teks);
		l.bungkus(info, x0 + 36, y0 + 17, tw, l.gaya.teksLemah);
		int sy = y0 + 6 + 12 + ih + 8;
		for (Lembar s : syarat) {
			sy += KotaGambar.syarat(l, s, x0 + 6, sy, x1 - 6, Integer.MAX_VALUE);
		}
		return y0 + tinggi;
	}

	private int ada(Lukis l, Lembar c, int x0, int y0, int x1) {
		Component renown = SketsaKota.t("cabang.renown", c.i(Cabang.RENOWN, 0));
		int rw = l.font.width(renown) + 8;
		l.teks(Brush.bold(c.teks(LembarKunci.NAMA)), x0, y0, x1 - x0 - rw, l.gaya.teks);
		l.kanan(renown, x1, y0, l.gaya.teksLemah);
		int y = y0 + 14;
		boolean dua = x1 - x0 >= 300;
		int mid = x0 + (x1 - x0) * 52 / 100;
		int kiriX1 = dua ? mid - 6 : x1;
		int yKiri = kiri(l, c, x0, y, kiriX1);
		if (dua) {
			return Math.max(yKiri, kanan(l, c, mid + 6, y, x1));
		}
		return kanan(l, c, x0, yKiri + 6, x1);
	}

	/** The three kinds, the needed blocks and the two buttons; returns the y under the column. */
	private int kiri(Lukis l, Lembar c, int x0, int y0, int x1) {
		String jenisKini = c.s(LembarKunci.JENIS);
		long ganti = c.l(Cabang.GANTI, -1L);
		boolean kunci = ganti > hariIni();
		int y = y0;
		int bw = TalismanButton.lebar(l.font, SketsaKota.t("cabang.pilih"), 52, 90);
		for (Lembar j : lembar.daftar(LembarKunci.JENIS)) {
			boolean dipilih = j.id().equals(jenisKini) || j.b(KotaLembar.PILIH);
			Component isi = j.teks(LembarKunci.TEKS);
			int tw = x1 - x0 - 24 - bw - 8;
			int ih = Math.max(1, l.font.split(isi, Math.max(10, tw)).size()) * 9;
			int tinggi = Math.max(26, 5 + 11 + ih + 4);
			l.kartu(x0, y, x1, y + tinggi, 0.0F, dipilih);
			Glyphs.Atlas.ikon(l.g, ikonJenis(j.id()), x0 + 4, y + (tinggi - 14) / 2, 14, 0xFFFFFFFF);
			l.teks(Brush.bold(j.teks(LembarKunci.NAMA)), x0 + 24, y + 3, tw, dipilih ? l.gaya.aksen : l.gaya.teks);
			l.bungkus(isi, x0 + 24, y + 14, tw, l.gaya.teksSamar);
			if (!dipilih) {
				tombolOp(l, "jenis" + j.id(), x1 - bw - 4, y + (tinggi - TOMBOL_H) / 2, bw, TOMBOL_H, SketsaKota.t("cabang.pilih"), !kunci,
						SketsaKota.t("cabang.ganti_kunci", ganti), LembarKunci.op(Cabang.OP_JENIS, j.id()));
			} else {
				l.kanan(SketsaKota.t("cabang.aktif"), x1 - 4, y + (tinggi - 9) / 2, KotaGambar.EMAS);
			}
			y += tinggi + 3;
		}
		y += 2;
		y += l.kepala(SketsaKota.t("cabang.isi"), x0, y, x1) + 1;
		for (Lembar i : c.daftar(Cabang.ISI)) {
			boolean ok = i.b(LembarKunci.OK);
			Component hitung = Component.literal(i.i(Cabang.PUNYA, 0) + "/" + i.i(Cabang.PERLU, 0));
			int hw = l.font.width(hitung) + 6;
			Glyphs.Atlas.ikon(l.g, ok ? Glyphs.Atlas.CENTANG : Glyphs.Atlas.SILANG, x0 + 2, y, 9, 0xFFFFFFFF);
			l.teks(i.teks(LembarKunci.TEKS), x0 + 14, y + 1, x1 - x0 - 16 - hw, ok ? l.gaya.teks : l.gaya.teksLemah);
			l.kanan(hitung, x1 - 2, y + 1, ok ? l.gaya.teksSamar : Palette.PERINGATAN);
			y += 12;
		}
		y += 4;
		Component rumah = SketsaKota.t("cabang.rumah");
		Component tutup = SketsaKota.t("cabang.tutup");
		if (x1 - x0 < 200) {
			tombolOp(l, "rumah", x0, y, x1 - x0, TOMBOL_H, rumah, true, null, Cabang.OP_RUMAH);
			yakin(l, "tutupCabang", x0, y + TOMBOL_H + 3, x1 - x0, TOMBOL_H, tutup, true, null, Cabang.OP_TUTUP_YA, SegelKonfirmasi.Tingkat.SEDANG, yakinTeks(tutup));
			return y + 2 * TOMBOL_H + 6;
		}
		int w = (x1 - x0 - 4) / 2;
		tombolOp(l, "rumah", x0, y, w, TOMBOL_H, rumah, true, null, Cabang.OP_RUMAH);
		yakin(l, "tutupCabang", x0 + w + 4, y, w, TOMBOL_H, tutup, true, null, Cabang.OP_TUTUP_YA, SegelKonfirmasi.Tingkat.SEDANG, yakinTeks(tutup));
		return y + TOMBOL_H + 3;
	}

	/** Guards, effects and reports; returns the y under the column. */
	private int kanan(Lukis l, Lembar c, int x0, int y0, int x1) {
		List<Lembar> penjaga = c.daftar(Cabang.PENJAGA);
		int y = y0 + l.kepala(SketsaKota.t("cabang.penjaga", penjaga.size()), x0, y0, x1) + 1;
		if (penjaga.isEmpty()) {
			y += Math.round(l.bungkus(SketsaKota.t("cabang.penjaga_kosong"), x0 + 2, y, x1 - x0 - 4, Palette.PERINGATAN)) + 2;
		}
		for (Lembar g : penjaga) {
			l.teks(g.teks(LembarKunci.NAMA), x0 + 2, y, x1 - x0 - 4, l.gaya.teks);
			y += 11;
		}
		y += 4;
		y += l.kepala(SketsaKota.t("cabang.efek"), x0, y, x1) + 1;
		for (Lembar e : c.daftar(Cabang.EFEK)) {
			y += Math.round(l.bungkus(Component.literal("• ").append(e.teks(LembarKunci.TEKS)), x0 + 2, y, x1 - x0 - 4, l.gaya.teksLemah)) + 2;
		}
		y += 4;
		y += l.kepala(SketsaKota.t("cabang.laporan"), x0, y, x1) + 1;
		List<Lembar> laporan = c.daftar(Cabang.LAPORAN);
		if (laporan.isEmpty()) {
			l.samar(SketsaKota.t("cabang.laporan_kosong"), x0 + 2, y, x1 - x0 - 4);
			y += 12;
		}
		for (Lembar r : laporan) {
			long nb = r.l("nb", 0L);
			Component angka = nb == 0L ? null : Component.literal(Brush.bertanda(nb) + " NB");
			int aw = angka == null ? 0 : l.font.width(angka) + 6;
			Component baris = SketsaKota.t("cabang.laporan_baris", r.l(LembarKunci.HARI, 0L), r.teks(LembarKunci.TEKS));
			float h = l.bungkus(baris, x0 + 2, y, x1 - x0 - 4 - aw, l.gaya.teks);
			if (angka != null) {
				l.kanan(angka, x1 - 2, y, nb > 0 ? Palette.OK : Palette.BAHAYA);
			}
			y += Math.round(h) + 3;
		}
		return y;
	}
}
