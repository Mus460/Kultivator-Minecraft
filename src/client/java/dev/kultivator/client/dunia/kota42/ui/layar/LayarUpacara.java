package dev.kultivator.client.dunia.kota42.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.CincinWaktu;
import dev.kultivator.client.screen.ui.GulirIsi;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.PemilihJumlah;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Rombongan;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Upacara;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The two DESIGN 42.11.14 sheets, on the single kit (they were modes of the events board drawn by the old sheet engine):
 * <ul>
 * <li>{@code upacara} — the invitation card in its ink: red with a gold 囍 (wedding), white with a black 奠 (funeral, dark
 * text), egg red with 喜 (birth, month feast, zhuazhou); title, day and hour, place, the ceremony's phases as a row of knots
 * (the current one glowing), the countdown to the start and the family with their part. Under it, always in view, the gift
 * envelope (angpao or condolence money between the sheet's minimum and maximum, the suggested sum first, "Beri") and
 * "Tunjuk".</li>
 * <li>{@code rombongan} — a travelling troupe: banner with the leader and when they came and leave, today's sessions with
 * time and place (a running one glows, "Tunjuk" marks it), Lao Shuo's stories heard so far or Ah Qin's songs to "Minta",
 * and a tip (amount within the sheet's range, "Beri tip") that stays in view.</li>
 * </ul>
 * The ornamental character of the card is drawn big (an illustration, not text). Everything scrolls with Up Down PgUp PgDn
 * Home End; the picker takes the arrow keys while it has focus.
 */
public final class LayarUpacara extends LayarKotaDasar {

	private final boolean upacara;
	private final GulirIsi gulir = new GulirIsi();
	private final PemilihJumlah jumlah = new PemilihJumlah();

	public LayarUpacara(String layar, int npcId, CompoundTag lembar) {
		super(layar, npcId, lembar, SketsaKota.t(Upacara.LAYAR.equals(layar) ? "upacara.judul" : "rombongan.judul"),
				Upacara.LAYAR.equals(layar) ? LembarKunci.GAYA_KAIN : LembarKunci.GAYA_KAYU);
		this.upacara = Upacara.LAYAR.equals(layar);
	}

	@Override
	protected List<Tab> daftarTab() {
		return List.of();
	}

	@Override
	protected void setelahLembar(boolean buka) {
		if (!buka) {
			return;
		}
		if (upacara) {
			Lembar beri = lembar.sub(Upacara.BERI);
			long min = Math.max(1L, beri.l(Upacara.MIN, 2L));
			jumlah.min((int) Math.min(Integer.MAX_VALUE, min));
			jumlah.set((int) Math.min(Integer.MAX_VALUE, beri.l(Upacara.SARAN, min)));
		} else {
			long min = Math.max(1L, lembar.sub(Rombongan.ROMBONGAN).l(Rombongan.TIP_MIN, 1L));
			jumlah.min((int) Math.min(Integer.MAX_VALUE, min));
			jumlah.set((int) Math.min(Integer.MAX_VALUE, min));
		}
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
		if (upacara) {
			upacara(l, x0, y0, x1, y1);
		} else {
			rombongan(l, x0, y0, x1, y1);
		}
	}

	/** Phases of a ceremony kind in order (42.7.7). */
	private static List<String> fase(String jenis) {
		return switch (jenis) {
			case Upacara.UP_NIKAH -> List.of(Upacara.UF_MENUNGGU, Upacara.UF_JEMPUT, Upacara.UF_ARAK, Upacara.UF_SEMBAH, Upacara.UF_JAMUAN, Upacara.UF_SELESAI);
			case Upacara.UP_MAKAM -> List.of(Upacara.UF_MENUNGGU, Upacara.UF_USUNG, Upacara.UF_MAKAM, Upacara.UF_JAMUAN, Upacara.UF_SELESAI);
			case Upacara.UP_ZHUAZHOU -> List.of(Upacara.UF_MENUNGGU, Upacara.UF_PILIH_BENDA, Upacara.UF_JAMUAN, Upacara.UF_SELESAI);
			default -> List.of(Upacara.UF_MENUNGGU, Upacara.UF_JAMUAN, Upacara.UF_SELESAI);
		};
	}

	// =================================================================== upacara

	private void upacara(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar u = lembar.sub(Upacara.UNDANGAN);
		if (u.kosongSemua()) {
			kosong(l, SketsaKota.t("upacara.kosong"), x0, y0, x1, y1);
			return;
		}
		String jenis = u.s(LembarKunci.JENIS, Upacara.UP_NIKAH);
		boolean duka = Upacara.UP_MAKAM.equals(jenis);
		Lembar beri = lembar.sub(Upacara.BERI);
		boolean sudah = u.b(Upacara.SUDAH_BERI);
		boolean sempit = x1 - x0 < 260;
		// the envelope bar stays at the bottom: one row wide, two rows narrow
		int baris = sempit ? 2 : 1;
		int bh = baris * (TOMBOL_H + 3) + 4;
		int bawah = y1 - bh;
		int xr = x1 - 6;
		int y = gulir.mulai(l, x0, y0, xr, bawah - 2);
		int akhir = undangan(l, u, jenis, duka, x0, y, xr);
		// the family
		List<Lembar> keluarga = u.daftar(Upacara.KELUARGA);
		if (!keluarga.isEmpty()) {
			akhir += 6;
			akhir += l.kepala(SketsaKota.t("upacara.keluarga"), x0, akhir, xr) + 1;
			for (Lembar k : keluarga) {
				int rw = (xr - x0) / 2;
				l.teks(k.teks(LembarKunci.NAMA), x0 + 4, akhir, rw - 4, l.gaya.teks);
				l.teks(k.teks(LembarKunci.PERAN), x0 + rw + 2, akhir, xr - x0 - rw - 2, l.gaya.teksSamar);
				akhir += 11;
			}
		}
		gulir.selesai(l, akhir + 4);
		// envelope + actions
		l.sumur(x0, bawah, x1, y1);
		boolean angpao = !Upacara.BE_DUKA.equals(beri.s(LembarKunci.JENIS, duka ? Upacara.BE_DUKA : Upacara.BE_ANGPAO));
		int ey = bawah + 3;
		l.g.fill(x0 + 5, ey + 1, x0 + 19, ey + 12, angpao ? 0xFFB3202A : 0xFFF0EEE8);
		l.g.fill(x0 + 5, ey + 1, x0 + 19, ey + 4, angpao ? 0xFFD4A637 : 0xFF2A2A2A);
		int bx = x0 + 24;
		Component tunjukTeks = SketsaKota.t("tunjuk");
		int tw = TalismanButton.lebar(l.font, tunjukTeks, 54, 90);
		int tunjukX = x1 - tw - 4;
		int tunjukY = sempit ? bawah + 3 + TOMBOL_H + 3 : ey;
		if (sudah) {
			l.teks(SketsaKota.t(angpao ? "upacara.sudah_angpao" : "upacara.sudah_duka"), bx, ey + 3, (sempit ? x1 - 4 : tunjukX - 4) - bx, l.gaya.teksSumur());
		} else if (beri.kosongSemua()) {
			l.teks(SketsaKota.t("upacara.tanpa_beri"), bx, ey + 3, (sempit ? x1 - 4 : tunjukX - 4) - bx, Palette.INK_FAINT);
		} else {
			long min = Math.max(1L, beri.l(Upacara.MIN, 2L));
			long maks = Math.max(min, beri.l(Upacara.MAKS, min * 10L));
			int m = (int) Math.min(Integer.MAX_VALUE, maks);
			int pw = jumlah.min((int) Math.min(Integer.MAX_VALUE, min)).gambar(l, "beri_n", bx, ey, m);
			long nilai = jumlah.nilai();
			boolean cukup = uang() < 0 || nilai <= uang();
			Component alasan = beri.teksAtau(LembarKunci.ALASAN);
			if (alasan == null && !cukup) {
				alasan = k("harga.kurang", Brush.angka(nilai - Math.max(0L, uang())));
			}
			int beriX = bx + pw + 4;
			int beriMaks = sempit ? x1 - 4 : tunjukX - 4;
			tombolOp(l, "beri", beriX, ey, Math.max(50, beriMaks - beriX), TOMBOL_H,
					SketsaKota.t(angpao ? "upacara.beri_angpao" : "upacara.beri_duka", Brush.angka(nilai)), beri.b(LembarKunci.BISA) && cukup, alasan,
					LembarKunci.op(Upacara.OP_BERI, nilai));
		}
		tombolOp(l, "tunjuk", tunjukX, tunjukY, tw, TOMBOL_H, tunjukTeks, true, null,
				LembarKunci.op(LembarKunci.OP_TANDAI, u.i(LembarKunci.X, 0), u.i(LembarKunci.Z, 0)));
	}

	/** The invitation card; returns the y under it. */
	private int undangan(Lukis l, Lembar u, String jenis, boolean duka, int x0, int y0, int x1) {
		int kertas = switch (jenis) {
			case Upacara.UP_NIKAH -> Upacara.WARNA_NIKAH;
			case Upacara.UP_MAKAM -> Upacara.WARNA_MAKAM;
			default -> Upacara.WARNA_LAHIR;
		};
		int tinta = duka ? 0xFF1B1B1B : 0xFFF7DC7E;
		int teks = duka ? 0xFF2A2A2A : 0xFFFFF4E0;
		int lemah = duka ? 0xFF5A5A5A : 0xFFF0D8C0;
		int lipatW = 54;
		int tx = x0 + lipatW + 8;
		int tw = x1 - tx - 6;
		// measure the right-hand text first so the card is exactly as tall as its content
		Component judul = Brush.bold(u.teks(LembarKunci.NAMA));
		int jh = Math.max(1, Math.min(3, l.font.split(judul, Math.max(10, tw)).size())) * 9;
		List<String> fase = fase(jenis);
		String kini = u.s(Upacara.FASE, Upacara.UF_MENUNGGU);
		Component faseKini = SketsaKota.t("upacara.fase_kini", SketsaKota.t("upacara.fase." + kini));
		long sisa = u.l(LembarKunci.SISA_TICK, 0L);
		int tinggi = 8 + jh + 3 + 11 + 11 + 5 + 10 + 11 + 11 + (sisa > 0L ? 11 : 0) + (u.b(Upacara.HADIR) ? 11 : 0) + 6;
		tinggi = Math.max(tinggi, 96);
		int x1k = x1;
		l.g.fill(x0 + 2, y0 + 2, x1k + 2, y0 + tinggi + 2, 0x50000000);
		l.g.fillGradient(x0, y0, x1k, y0 + tinggi, kertas, Palette.darken(kertas, 0.12F));
		Brush.frame(l.g, x0 + 3, y0 + 3, x1k - 3, y0 + tinggi - 3, Palette.argb(0xB0, tinta & 0xFFFFFF));
		int lipat = x0 + lipatW;
		l.g.fill(lipat, y0 + 3, lipat + 1, y0 + tinggi - 3, Palette.argb(0x50, 0x000000));
		String aksara = switch (jenis) {
			case Upacara.UP_NIKAH -> "囍";
			case Upacara.UP_MAKAM -> "奠";
			default -> "喜";
		};
		Brush.centered(l.g, l.font, Component.literal(aksara), (x0 + lipat) / 2.0F, y0 + tinggi / 2.0F - 14, tinta, 3.0F, !duka);
		int y = y0 + 8;
		l.bungkus(judul, tx, y, tw, teks, jh);
		y += jh + 3;
		Component waktu = SketsaKota.t("upacara.waktu", u.l(LembarKunci.HARI, 0L),
				String.format(Locale.ROOT, "%02d:%02d", u.i(KotaLembar.JAM, 0), u.i(KotaLembar.MENIT, 0)));
		l.teks(waktu, tx, y, tw, lemah);
		y += 11;
		l.teks(SketsaKota.t("upacara.tempat", u.teks(LembarKunci.TEMPAT)), tx, y, tw, lemah);
		y += 12;
		// the phases as knots
		int idx = Math.max(0, fase.indexOf(kini));
		int step = Math.max(10, Math.min(28, tw / Math.max(1, fase.size())));
		for (int i = 0; i < fase.size(); i++) {
			float kx = tx + 4 + i * step;
			boolean lewat = i < idx;
			boolean sekarang = i == idx;
			int w = lewat ? tinta : sekarang ? Palette.argb(Math.round(0x90 + 0x60 * l.denyut()), tinta & 0xFFFFFF) : Palette.argb(0x60, lemah & 0xFFFFFF);
			if (i > 0) {
				l.g.fill(Math.round(kx - step + 3), y + 3, Math.round(kx - 3), y + 4, Palette.argb(0x80, lemah & 0xFFFFFF));
			}
			Brush.disc(l.g, kx, y + 3.5F, sekarang ? 3.5F : 2.5F, w);
			if (l.di(Math.round(kx - 4), y - 1, Math.round(kx + 4), y + 8)) {
				l.tip(SketsaKota.t("upacara.fase." + fase.get(i)));
			}
		}
		y += 10;
		l.teks(faseKini, tx, y, tw, teks);
		y += 11;
		if (sisa > 0L) {
			long left = CincinWaktu.sisa(sisa, diterima, l.kini);
			l.teks(SketsaKota.t("upacara.mulai_dalam", Brush.waktu(left)), tx, y, tw, lemah);
			y += 11;
		}
		if (u.b(Upacara.HADIR)) {
			l.teks(SketsaKota.t("upacara.hadir"), tx, y, tw, duka ? 0xFF3A6A3A : 0xFFB8F0B0);
		}
		return y0 + tinggi;
	}

	// =================================================================== rombongan

	private void rombongan(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar r = lembar.sub(Rombongan.ROMBONGAN);
		if (r.kosongSemua()) {
			kosong(l, SketsaKota.t("rombongan.kosong"), x0, y0, x1, y1);
			return;
		}
		int bh = TOMBOL_H + 7;
		int bawah = y1 - bh;
		int xr = x1 - 6;
		int y = gulir.mulai(l, x0, y0, xr, bawah - 2);
		boolean dua = xr - x0 >= 320;
		int mid = x0 + (xr - x0) * 11 / 20;
		int kiriX1 = dua ? mid - 6 : xr;
		int kananX0 = dua ? mid + 6 : x0;
		// banner
		Component berikut = r.teksAtau(Rombongan.BERIKUT);
		Component tinggal = berikut == null ? SketsaKota.t("rombongan.tinggal", r.l(Rombongan.TIBA, 0L), r.l(Rombongan.PERGI, 0L))
				: SketsaKota.t("rombongan.tinggal_berikut", r.l(Rombongan.TIBA, 0L), r.l(Rombongan.PERGI, 0L), berikut);
		int tw = kiriX1 - x0 - 34;
		int th = Math.max(1, l.font.split(tinggal, Math.max(10, tw)).size()) * 9;
		int spanduk = 6 + 11 + 11 + 11 + th + 6;
		l.kartu(x0, y, kiriX1, y + spanduk, 0.0F, false);
		IkonKota.gambar(l.g, IkonKota.acara("rombongan"), x0 + 6, y + 7, 20);
		l.teks(Brush.bold(r.teks(LembarKunci.NAMA)), x0 + 32, y + 5, tw, l.gaya.teks);
		l.teks(r.teks(LembarKunci.JENIS), x0 + 32, y + 16, tw, l.gaya.teksLemah);
		l.teks(SketsaKota.t("rombongan.ketua", r.teks(Rombongan.KETUA)), x0 + 32, y + 27, tw, l.gaya.teksLemah);
		l.bungkus(tinggal, x0 + 32, y + 38, tw, l.gaya.teksSamar);
		int ya = y + spanduk + 6;
		ya += l.kepala(SketsaKota.t("rombongan.sesi"), x0, ya, kiriX1) + 1;
		List<Lembar> sesi = r.daftar(Rombongan.SESI);
		if (sesi.isEmpty()) {
			l.samar(SketsaKota.t("rombongan.sesi_kosong"), x0 + 2, ya, kiriX1 - x0 - 4);
			ya += 12;
		}
		Component tunjukTeks = SketsaKota.t("tunjuk");
		int bw = TalismanButton.lebar(l.font, tunjukTeks, 52, 80);
		int n = 0;
		for (Lembar e : sesi) {
			boolean jalan = e.b(Rombongan.BERLANGSUNG);
			int rh = 24;
			if (jalan) {
				l.g.fill(x0, ya, kiriX1, ya + rh, Palette.argb(Math.round(0x50 + 0x40 * l.denyut()), 0xD4A637));
			} else {
				l.kartu(x0, ya, kiriX1, ya + rh, 0.0F, false);
			}
			Component jam = Component.literal(String.format(Locale.ROOT, "%02d:%02d", e.i(KotaLembar.JAM, 0), e.i(KotaLembar.MENIT, 0)));
			l.teks(jam, x0 + 4, ya + 8, 34, jalan ? Palette.GOLD_LIGHT : l.gaya.teks);
			int sx = x0 + 40;
			l.teks(e.teks(LembarKunci.TEKS), sx, ya + 3, kiriX1 - sx - bw - 8, l.gaya.teks);
			l.teks(e.teks(LembarKunci.TEMPAT), sx, ya + 13, kiriX1 - sx - bw - 8, l.gaya.teksSamar);
			tombolOp(l, "sesi" + n, kiriX1 - bw - 3, ya + (rh - TOMBOL_H) / 2, bw, TOMBOL_H, tunjukTeks, true, null,
					LembarKunci.op(LembarKunci.OP_TANDAI, e.i(LembarKunci.X, 0), e.i(LembarKunci.Z, 0)));
			ya += rh + 3;
			n++;
		}
		// stories or songs
		List<Lembar> kisah = lembar.daftar(Rombongan.KISAH);
		List<Lembar> lagu = lembar.daftar(Rombongan.LAGU);
		int yb = dua ? y : ya + 6;
		if (!kisah.isEmpty()) {
			long didengar = kisah.stream().filter(k -> k.b(Rombongan.DIDENGAR)).count();
			yb += l.kepala(SketsaKota.t("rombongan.kisah", didengar, kisah.size()), kananX0, yb, xr) + 1;
			for (Lembar k : kisah) {
				boolean d = k.b(Rombongan.DIDENGAR);
				dev.kultivator.client.screen.ui.Glyphs.Atlas.ikon(l.g, d ? dev.kultivator.client.screen.ui.Glyphs.Atlas.CENTANG
						: dev.kultivator.client.screen.ui.Glyphs.Atlas.BUKU, kananX0 + 2, yb, 9, d ? 0xFFFFFFFF : 0x80FFFFFF);
				l.teks(d ? k.teks(LembarKunci.NAMA) : SketsaKota.t("rombongan.belum_didengar"), kananX0 + 15, yb, xr - kananX0 - 17,
						d ? l.gaya.teks : l.gaya.teksSamar);
				yb += 12;
			}
		} else if (!lagu.isEmpty()) {
			yb += l.kepala(SketsaKota.t("rombongan.lagu"), kananX0, yb, xr) + 1;
			Component mintaTeks = SketsaKota.t("rombongan.minta");
			int mw = TalismanButton.lebar(l.font, mintaTeks, 52, 80);
			int i = 0;
			for (Lembar g : lagu) {
				boolean d = g.b(Rombongan.DIDENGAR);
				l.teks(g.teks(LembarKunci.NAMA), kananX0 + 4, yb + 3, xr - kananX0 - mw - 12, d ? l.gaya.teksLemah : l.gaya.teks);
				tombolOp(l, "minta" + i, xr - mw - 2, yb, mw, TOMBOL_H, mintaTeks, true, null, LembarKunci.op(Rombongan.OP_MINTA, g.id()));
				yb += TOMBOL_H + 3;
				i++;
			}
		} else {
			yb += Math.round(l.bungkus(SketsaKota.t("rombongan.info"), kananX0 + 2, yb, xr - kananX0 - 4, l.gaya.teksLemah)) + 2;
		}
		gulir.selesai(l, Math.max(ya, yb) + 4);
		// the tip stays in view
		l.sumur(x0, bawah, x1, y1);
		long min = Math.max(1L, r.l(Rombongan.TIP_MIN, 1L));
		long maks = Math.max(min, r.l(Rombongan.TIP_MAKS, 20L));
		int ty = bawah + 3;
		int pw = jumlah.min((int) Math.min(Integer.MAX_VALUE, min)).gambar(l, "tip_n", x0 + 4, ty, (int) Math.min(Integer.MAX_VALUE, maks));
		long nilai = jumlah.nilai();
		long uang = uang();
		boolean cukup = uang < 0 || nilai <= uang;
		int bx = x0 + 4 + pw + 4;
		tombolOp(l, "tip", bx, ty, Math.max(50, x1 - 4 - bx), TOMBOL_H, SketsaKota.t("rombongan.tip", Brush.angka(nilai)), cukup,
				k("harga.kurang", Brush.angka(nilai - Math.max(0L, uang))), LembarKunci.op(Rombongan.OP_TIP, nilai));
	}
}
