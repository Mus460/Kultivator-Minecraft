package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.dunia.kota42.ui.layar.SketsaKota;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.CincinWaktu;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.LabelHarga;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Rombongan;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Upacara;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * The two DESIGN 42.11.14 modes of the events screen {@link LayarAcara} (jadwal 41/42 0.1 "No GUI twice"; drawn here so
 * the 38 board stays readable):
 * <ul>
 * <li>{@code upacara} — the invitation card in its ink: red with a gold 囍 (wedding), white with a black 奠 (funeral,
 * dark text), egg red with 喜 (birth, month feast, zhuazhou); title, day and hour, place, the ceremony's phases as a row of
 * knots (the current one glowing), the countdown to the start, the family with their part, and the gift envelope
 * (angpao or condolence money: amount between the sheet's min and max, the suggested sum first, "Beri") and "Tunjuk".</li>
 * <li>{@code rombongan} — a travelling troupe: who they are, the leader, arrived / leaving / next town, today's sessions
 * with time and place (a running one glows), Lao Shuo's stories or Ah Qin's songs heard so far ("Minta" a song), and a tip
 * (amount within the sheet's range, "Beri tip").</li>
 * </ul>
 */
final class LayarAcaraKota {

	static final String UPACARA = Upacara.LAYAR;
	static final String ROMBONGAN = Rombongan.LAYAR;

	static Component judul(String layar) {
		return switch (layar) {
			case Upacara.LAYAR -> SketsaKota.t("upacara.judul");
			case Rombongan.LAYAR -> SketsaKota.t("rombongan.judul");
			default -> TeksTempat.k("layar.acara");
		};
	}

	/** Draws the mode; the amount the player picks lives in {@code s.jumlah42} (-1 = not yet). */
	static void isi(LayarAcara s, Lukis l, int x0, int y0, int x1, int y1) {
		if (UPACARA.equals(s.layar())) {
			upacara(s, l, x0, y0, x1, y1);
		} else {
			rombongan(s, l, x0, y0, x1, y1);
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

	private static void upacara(LayarAcara s, Lukis l, int x0, int y0, int x1, int y1) {
		long jumlah = s.jumlah42;
		Lembar lembar = s.lembar42();
		Lembar u = lembar.sub(Upacara.UNDANGAN);
		if (u.kosongSemua()) {
			s.kosong42(l, SketsaKota.t("upacara.kosong"), x0, y0, x1, y1);
			return;
		}
		String jenis = u.s(LembarKunci.JENIS, Upacara.UP_NIKAH);
		boolean duka = Upacara.UP_MAKAM.equals(jenis);
		int kertas = switch (jenis) {
			case Upacara.UP_NIKAH -> Upacara.WARNA_NIKAH;
			case Upacara.UP_MAKAM -> Upacara.WARNA_MAKAM;
			default -> Upacara.WARNA_LAHIR;
		};
		int tinta = duka ? 0xFF1B1B1B : 0xFFF7DC7E;
		int teks = duka ? 0xFF2A2A2A : 0xFFFFF4E0;
		int lemah = duka ? 0xFF5A5A5A : 0xFFF0D8C0;
		// the card, folded: left the big character, right the text
		int cx1 = Math.min(x1, x0 + Math.max(200, (x1 - x0) * 3 / 5));
		int cy1 = Math.min(y1 - 36, y0 + 120);
		l.g.fill(x0 + 2, y0 + 2, cx1 + 2, cy1 + 2, 0x50000000);
		l.g.fillGradient(x0, y0, cx1, cy1, kertas, Palette.darken(kertas, 0.12F));
		Brush.frame(l.g, x0 + 3, y0 + 3, cx1 - 3, cy1 - 3, Palette.argb(0xB0, tinta & 0xFFFFFF));
		int lipat = x0 + Math.min(60, (cx1 - x0) / 3);
		l.g.fill(lipat, y0 + 3, lipat + 1, cy1 - 3, Palette.argb(0x50, 0x000000));
		String aksara = switch (jenis) {
			case Upacara.UP_NIKAH -> "囍";
			case Upacara.UP_MAKAM -> "奠";
			default -> "喜";
		};
		Brush.centered(l.g, l.font, Component.literal(aksara), (x0 + lipat) / 2.0F, (y0 + cy1) / 2.0F - 14, tinta, 3.0F, !duka);
		int tx = lipat + 6;
		int tw = cx1 - tx - 6;
		int y = y0 + 7;
		l.teks(Brush.bold(u.teks(LembarKunci.NAMA)), tx, y, tw, teks, 0.95F);
		y += 12;
		Component waktu = SketsaKota.t("upacara.waktu", u.l(LembarKunci.HARI, 0L), String.format(java.util.Locale.ROOT, "%02d:%02d",
				u.i(KotaLembar.JAM, 0), u.i(KotaLembar.MENIT, 0)));
		l.keterangan(waktu, tx, y, tw, lemah);
		y += 9;
		l.keterangan(SketsaKota.t("upacara.tempat", u.teks(LembarKunci.TEMPAT)), tx, y, tw, lemah);
		y += 12;
		// phases as knots
		List<String> fase = fase(jenis);
		String kini = u.s(Upacara.FASE, Upacara.UF_MENUNGGU);
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
		y += 9;
		l.keterangan(SketsaKota.t("upacara.fase_kini", SketsaKota.t("upacara.fase." + kini)), tx, y, tw, teks);
		y += 10;
		long sisa = u.l(LembarKunci.SISA_TICK, 0L);
		if (sisa > 0L && y + 10 < cy1) {
			long left = CincinWaktu.sisa(sisa, s.diterima42(), l.kini);
			l.keterangan(SketsaKota.t("upacara.mulai_dalam", TeksTempat.waktu(left)), tx, y, tw, lemah);
			y += 10;
		}
		if (u.b(Upacara.HADIR) && y + 10 < cy1) {
			l.keterangan(SketsaKota.t("upacara.hadir"), tx, y, tw, duka ? 0xFF3A6A3A : 0xFFB8F0B0);
		}
		// right: the family
		TempatWarna.Gaya gaya = s.gaya42();
		int fx = cx1 + 6;
		if (fx + 50 < x1) {
			int fy = y0 + s.subjudul42(l, SketsaKota.t("upacara.keluarga"), fx, y0, x1);
			for (Lembar k : u.daftar(Upacara.KELUARGA)) {
				if (fy + 18 > cy1) {
					break;
				}
				l.teks(k.teks(LembarKunci.NAMA), fx, fy, x1 - fx, gaya.teks, 0.8F);
				l.keterangan(k.teks(LembarKunci.PERAN), fx, fy + 9, x1 - fx, gaya.teksSamar);
				fy += 19;
			}
		}
		// the envelope
		Lembar beri = lembar.sub(Upacara.BERI);
		int by = y1 - 32;
		boolean sudah = u.b(Upacara.SUDAH_BERI);
		long min = Math.max(1L, beri.l(Upacara.MIN, 2L));
		long maks = Math.max(min, beri.l(Upacara.MAKS, min * 10L));
		if (jumlah < 0L) {
			jumlah = Math.max(min, Math.min(maks, beri.l(Upacara.SARAN, min)));
		}
		jumlah = Math.max(min, Math.min(maks, jumlah));
		s.jumlah42 = jumlah;
		boolean angpao = !Upacara.BE_DUKA.equals(beri.s(LembarKunci.JENIS, duka ? Upacara.BE_DUKA : Upacara.BE_ANGPAO));
		PanelTempat.kartu(l.g, gaya, x0, by - 2, x1, y1, 0.0F, false);
		// a tiny envelope in the right ink
		l.g.fill(x0 + 4, by + 3, x0 + 18, by + 22, angpao ? 0xFFB3202A : 0xFFF0EEE8);
		l.g.fill(x0 + 4, by + 3, x0 + 18, by + 7, angpao ? 0xFFD4A637 : 0xFF2A2A2A);
		if (sudah) {
			l.teks(SketsaKota.t(angpao ? "upacara.sudah_angpao" : "upacara.sudah_duka"), x0 + 24, by + 9, x1 - x0 - 120, gaya.teksLemah, 0.85F);
		} else if (beri.kosongSemua()) {
			l.teks(SketsaKota.t("upacara.tanpa_beri"), x0 + 24, by + 9, x1 - x0 - 120, gaya.teksSamar, 0.8F);
		} else {
			final long nilai = jumlah;
			long langkah = Math.max(1L, (maks - min) / 10L);
			s.tombol42(l, "beri#kurang", x0 + 22, by + 4, 14, 13, Component.literal("−"), nilai > min, () -> s.jumlah42 = Math.max(min, s.jumlah42 - langkah));
			LabelHarga.gambar(l, nilai, s.uang42(), x0 + 40, by + 7, false, 0.9F, false);
			s.tombol42(l, "beri#tambah", x0 + 84, by + 4, 14, 13, Component.literal("+"), nilai < maks, () -> s.jumlah42 = Math.min(maks, s.jumlah42 + langkah));
			l.keterangan(SketsaKota.t("upacara.saran", TeksTempat.angka(beri.l(Upacara.SARAN, min))), x0 + 22, by + 19, 90, gaya.teksSamar);
			long uang = s.uang42();
			boolean cukup = uang < 0 || nilai <= uang;
			Component alasan = beri.teksAtau(LembarKunci.ALASAN);
			if (alasan == null && !cukup) {
				alasan = TeksTempat.k("harga.kurang", TeksTempat.angka(nilai - Math.max(0L, uang)));
			}
			s.op42(l, "beri", x0 + 104, by + 4, Math.max(60, x1 - x0 - 194), 14, SketsaKota.t(angpao ? "upacara.beri_angpao" : "upacara.beri_duka",
					TeksTempat.angka(nilai)), beri.b(LembarKunci.BISA) && cukup, alasan, LembarKunci.op(Upacara.OP_BERI, nilai));
		}
		s.op42(l, "tunjuk", x1 - 84, by + 4, 80, 14, SketsaKota.t("tunjuk"), true, null,
				LembarKunci.op(LembarKunci.OP_TANDAI, u.i(LembarKunci.X, 0), u.i(LembarKunci.Z, 0)));
	}

	// =================================================================== rombongan

	private static void rombongan(LayarAcara s, Lukis l, int x0, int y0, int x1, int y1) {
		long jumlah = s.jumlah42;
		Lembar lembar = s.lembar42();
		Lembar r = lembar.sub(Rombongan.ROMBONGAN);
		TempatWarna.Gaya gaya = s.gaya42();
		if (r.kosongSemua()) {
			s.kosong42(l, SketsaKota.t("rombongan.kosong"), x0, y0, x1, y1);
			return;
		}
		int mid = x0 + (x1 - x0) * 11 / 20;
		// the troupe banner
		PanelTempat.kartu(l.g, gaya, x0, y0, mid - 3, y0 + 40, 0.0F, false);
		IkonTempat.gambar(l.g, IkonTempat.PIALA, x0 + 4, y0 + 5, 18, 0xFFFFFFFF);
		l.teks(Brush.bold(r.teks(LembarKunci.NAMA)), x0 + 26, y0 + 3, mid - x0 - 32, gaya.teks, 0.95F);
		l.keterangan(r.teks(LembarKunci.JENIS), x0 + 26, y0 + 13, mid - x0 - 32, gaya.teksLemah);
		l.keterangan(SketsaKota.t("rombongan.ketua", r.teks(Rombongan.KETUA)), x0 + 26, y0 + 21, mid - x0 - 32, gaya.teksLemah);
		Component berikut = r.teksAtau(Rombongan.BERIKUT);
		l.keterangan(berikut == null ? SketsaKota.t("rombongan.tinggal", r.l(Rombongan.TIBA, 0L), r.l(Rombongan.PERGI, 0L))
				: SketsaKota.t("rombongan.tinggal_berikut", r.l(Rombongan.TIBA, 0L), r.l(Rombongan.PERGI, 0L), berikut), x0 + 26, y0 + 30, mid - x0 - 32,
				gaya.teksSamar);
		// today's sessions
		int y = y0 + 44;
		y += s.subjudul42(l, SketsaKota.t("rombongan.sesi"), x0, y, mid - 3);
		List<Lembar> sesi = r.daftar(Rombongan.SESI);
		if (sesi.isEmpty()) {
			l.keterangan(SketsaKota.t("rombongan.sesi_kosong"), x0, y, mid - x0 - 3, gaya.teksSamar);
		}
		int n = 0;
		for (Lembar e : sesi) {
			if (y + 16 > y1 - 34) {
				break;
			}
			boolean jalan = e.b(Rombongan.BERLANGSUNG);
			if (jalan) {
				l.g.fill(x0, y - 1, mid - 3, y + 15, Palette.argb(Math.round(0x50 + 0x40 * l.denyut()), 0xD4A637));
			}
			Brush.text(l.g, l.font, Component.literal(String.format(java.util.Locale.ROOT, "%02d:%02d", e.i(KotaLembar.JAM, 0), e.i(KotaLembar.MENIT, 0))),
					x0 + 2, y + 3, jalan ? TempatWarna.EMAS_TERANG : gaya.teks, 0.85F, false);
			l.teks(e.teks(LembarKunci.TEKS), x0 + 32, y, mid - x0 - 96, gaya.teks, 0.8F);
			l.keterangan(e.teks(LembarKunci.TEMPAT), x0 + 32, y + 8, mid - x0 - 96, gaya.teksSamar);
			s.op42(l, "sesi" + n, mid - 60, y, 54, 13, SketsaKota.t("tunjuk"), true, null,
					LembarKunci.op(LembarKunci.OP_TANDAI, e.i(LembarKunci.X, 0), e.i(LembarKunci.Z, 0)));
			y += 17;
			n++;
		}
		// right: stories or songs
		List<Lembar> kisah = lembar.daftar(Rombongan.KISAH);
		List<Lembar> lagu = lembar.daftar(Rombongan.LAGU);
		int ry = y0;
		if (!kisah.isEmpty()) {
			long didengar = kisah.stream().filter(k -> k.b(Rombongan.DIDENGAR)).count();
			ry += s.subjudul42(l, SketsaKota.t("rombongan.kisah", didengar, kisah.size()), mid + 3, ry, x1);
			for (Lembar k : kisah) {
				if (ry + 10 > y1 - 34) {
					break;
				}
				boolean d = k.b(Rombongan.DIDENGAR);
				IkonTempat.gambar(l.g, d ? IkonTempat.CENTANG : IkonTempat.BUKU, mid + 3, ry, 9, d ? 0xFFFFFFFF : 0x80FFFFFF);
				l.teks(d ? k.teks(LembarKunci.NAMA) : SketsaKota.t("rombongan.belum_didengar"), mid + 15, ry + 1, x1 - mid - 18, d ? gaya.teks : gaya.teksSamar, 0.8F);
				ry += 11;
			}
		} else if (!lagu.isEmpty()) {
			ry += s.subjudul42(l, SketsaKota.t("rombongan.lagu"), mid + 3, ry, x1);
			int i = 0;
			for (Lembar g : lagu) {
				if (ry + 14 > y1 - 34) {
					break;
				}
				boolean d = g.b(Rombongan.DIDENGAR);
				l.teks(g.teks(LembarKunci.NAMA), mid + 4, ry + 3, x1 - mid - 64, d ? gaya.teksLemah : gaya.teks, 0.8F);
				s.op42(l, "minta" + i, x1 - 54, ry, 52, 13, SketsaKota.t("rombongan.minta"), true, null, LembarKunci.op(Rombongan.OP_MINTA, g.id()));
				ry += 15;
				i++;
			}
		} else {
			l.bungkus(SketsaKota.t("rombongan.info"), mid + 3, ry, x1 - mid - 3, gaya.teksLemah, 0.75F, y1 - 40 - ry);
		}
		// the tip
		long min = Math.max(1L, r.l(Rombongan.TIP_MIN, 1L));
		long maks = Math.max(min, r.l(Rombongan.TIP_MAKS, 20L));
		if (jumlah < 0L) {
			jumlah = min;
		}
		jumlah = Math.max(min, Math.min(maks, jumlah));
		s.jumlah42 = jumlah;
		int by = y1 - 18;
		final long nilai = jumlah;
		long langkah = Math.max(1L, (maks - min) / 8L);
		s.tombol42(l, "tip#kurang", x0, by, 14, 14, Component.literal("−"), nilai > min, () -> s.jumlah42 = Math.max(min, s.jumlah42 - langkah));
		LabelHarga.gambar(l, nilai, s.uang42(), x0 + 18, by + 3, false, 0.9F, false);
		s.tombol42(l, "tip#tambah", x0 + 62, by, 14, 14, Component.literal("+"), nilai < maks, () -> s.jumlah42 = Math.min(maks, s.jumlah42 + langkah));
		long uang = s.uang42();
		boolean cukup = uang < 0 || nilai <= uang;
		s.op42(l, "tip", x0 + 82, by, x1 - x0 - 82, 14, SketsaKota.t("rombongan.tip", TeksTempat.angka(nilai)), cukup,
				TeksTempat.k("harga.kurang", TeksTempat.angka(nilai - Math.max(0L, uang))), LembarKunci.op(Rombongan.OP_TIP, nilai));
	}

	private LayarAcaraKota() {
	}
}
