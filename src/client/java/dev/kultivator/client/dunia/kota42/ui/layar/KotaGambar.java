package dev.kultivator.client.dunia.kota42.ui.layar;

import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Potret;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Shared drawing of the 42U-1 screens with the single kit ({@code screen/ui}, text 1.0): the town strip, a townsperson row,
 * hearts and mood faces, requirement rows, small gauges. Draws only; the screen sends ops.
 */
final class KotaGambar {

	/** Lang prefix of every client text of 42U-1 ({@code lang-src/kota_hidup_ui}, keys {@code kultivator.kota42.ui.k.*}). */
	static final String K = "kultivator.kota42.ui.k.";
	static final int EMAS = 0xFFF0C040;
	static final int HIJAU = 0xFF3FA86A;

	static MutableComponent t(String kunci, Object... args) {
		return Component.translatable(K + kunci, args);
	}

	static Component n(long angka) {
		return Component.literal(Brush.angka(angka));
	}

	static Component nb(long angka) {
		return Component.literal(Brush.angka(angka) + " NB");
	}

	/** A signed number "+28" / "-3" / "0". */
	static Component tanda(long angka) {
		return Component.literal(Brush.bertanda(angka));
	}

	// ================================================================== marks

	static int warnaWajah(String wajah) {
		return switch (wajah) {
			case KotaLembar.WAJAH_SENANG -> 0xFF5FB86A;
			case KotaLembar.WAJAH_SEDIH -> 0xFF6F8FB8;
			case KotaLembar.WAJAH_TAKUT -> 0xFFD0A040;
			case KotaLembar.WAJAH_MARAH -> 0xFFC0392B;
			case KotaLembar.WAJAH_MABUK -> 0xFFB06AC8;
			case KotaLembar.WAJAH_SAKIT -> 0xFF8AA050;
			default -> 0xFF9A9A9A;
		};
	}

	/** Heart colour of an {@code akrab} band (enemy dark red, stranger grey, known pale, close rose, friend gold). */
	static int warnaHati(String akrab) {
		return switch (akrab) {
			case KotaLembar.AKRAB_MUSUH -> 0xFF8A1A1A;
			case KotaLembar.AKRAB_KENAL -> 0xFFE8A0A8;
			case KotaLembar.AKRAB_AKRAB -> 0xFFE0506A;
			case KotaLembar.AKRAB_SAHABAT -> 0xFFF0C040;
			default -> 0xFF8A8A8A;
		};
	}

	/** A small heart of {@code size} px (two discs and a triangle of rows). */
	static void hati(Lukis l, float x, float y, float size, int warna) {
		float r = size * 0.28F;
		Brush.disc(l.g, x + r, y + r, r, warna);
		Brush.disc(l.g, x + size - r, y + r, r, warna);
		int rows = Math.max(2, Math.round(size * 0.62F));
		for (int i = 0; i < rows; i++) {
			float w = size * (1.0F - i / (float) rows);
			float x0 = x + (size - w) / 2.0F;
			Brush.rect(l.g, x0, y + r + i, x0 + w, y + r + i + 1, warna);
		}
	}

	/** The heart of a bond with its tooltip ("Akrab (kesan 42)"); false when the row has no bond. */
	static void hatiAkrab(Lukis l, Lembar w, int x, int y, int size) {
		if (!w.ada(KotaLembar.AKRAB)) {
			return;
		}
		String akrab = w.s(KotaLembar.AKRAB);
		hati(l, x, y, size, warnaHati(akrab));
		if (l.di(x, y, x + size + 1, y + size)) {
			l.tip(t("akrab." + akrab, w.i(KotaLembar.KESAN, 0)));
		}
	}

	static void wajah(Lukis l, String wajah, int x, int y, int size) {
		if (wajah.isEmpty() || KotaLembar.WAJAH_NETRAL.equals(wajah)) {
			return;
		}
		IkonKota.gambar(l.g, IkonKota.wajah(wajah), x, y, size);
		if (l.di(x, y, x + size, y + size)) {
			l.tip(t("wajah." + wajah));
		}
	}

	/** "34 · Koki" of a warga row. */
	static Component umurKerja(Lembar w) {
		Component kerja = w.teksAtau(KotaLembar.KERJA);
		MutableComponent out = t("umur", w.i(KotaLembar.UMUR, 0));
		if (kerja != null) {
			out.append(" · ").append(kerja);
		}
		return out;
	}

	/** Colour of a status ring (hidup white, arsip grey, pindah blue, sekte gold, jalan green). */
	static int warnaStatus(String status) {
		return switch (status) {
			case KotaLembar.ST_ARSIP -> 0xFF8A8A8A;
			case KotaLembar.ST_PINDAH -> 0xFF5A8AD0;
			case KotaLembar.ST_SEKTE -> 0xFFF0C040;
			case KotaLembar.ST_JALAN -> 0xFF5FB86A;
			default -> 0xFFF4F4F0;
		};
	}

	// ================================================================== rows

	/**
	 * A townsperson row: portrait (live body, else a silhouette) with a status ring, bold name with the mood face, "age ·
	 * job" under it and the bond heart on the right. {@code hHati} keeps room on the right for the caller's column.
	 */
	static void warga(Lukis l, Lembar w, int x0, int y0, int x1, int y1, boolean sorot, boolean pilih, int kananRuang) {
		l.kartu(x0, y0, x1, y1, sorot ? 1.0F : 0.0F, pilih);
		int ps = Math.max(16, Math.min(36, y1 - y0 - 4));
		int py = y0 + (y1 - y0 - ps) / 2;
		Potret.gambar(l, x0 + 2, py, ps, w.i(LembarKunci.POTRET, -1), -1, false, 0.0F);
		String status = w.s(KotaLembar.STATUS, KotaLembar.ST_HIDUP);
		if (!KotaLembar.ST_HIDUP.equals(status)) {
			Brush.frame(l.g, x0 + 2, py, x0 + 2 + ps, py + ps, warnaStatus(status));
		}
		int tx = x0 + ps + 7;
		int ex = x1 - 4 - kananRuang;
		Component nama = w.teks(LembarKunci.NAMA);
		String wajah = w.s(KotaLembar.WAJAH);
		boolean adaWajah = !wajah.isEmpty() && !KotaLembar.WAJAH_NETRAL.equals(wajah);
		l.teks(Brush.bold(nama), tx, y0 + 3, ex - tx - (adaWajah ? 14 : 0) - 12, l.gaya.teks);
		int nw = Math.min(ex - tx - (adaWajah ? 14 : 0) - 12, l.font.width(Brush.bold(nama)));
		if (adaWajah) {
			wajah(l, wajah, tx + nw + 3, y0 + 1, 10);
		}
		if (y1 - y0 >= 24) {
			l.teks(umurKerja(w), tx, y0 + 14, ex - tx - 12, l.gaya.teksLemah);
		}
		hatiAkrab(l, w, ex - 10, y0 + 4, 9);
	}

	/** Ticks or crosses a requirement row (ok, teks, punya); returns the height used (0 when it does not fit). */
	static int syarat(Lukis l, Lembar s, int x0, int y, int x1, int yMaks) {
		if (y + 11 > yMaks) {
			return 0;
		}
		boolean ok = s.b(LembarKunci.OK);
		l.g.fill(x0, y + 1, x0 + 8, y + 9, ok ? 0xFF2E8963 : 0xFF8A3A30);
		Brush.text(l.g, l.font, Component.literal(ok ? "✓" : "✗"), x0 + 1, y + 1, 0xFFFFFFFF, 1.0F, false);
		Component punya = s.teksAtau("punya");
		int pw = punya == null ? 0 : Math.min(80, l.font.width(punya) + 4);
		l.teks(s.teks(LembarKunci.TEKS), x0 + 12, y + 1, x1 - x0 - 12 - pw, ok ? l.gaya.teks : l.gaya.teksLemah);
		if (punya != null) {
			l.kanan(punya, x1, y + 1, ok ? l.gaya.teksSamar : Palette.PERINGATAN);
		}
		return 11;
	}

	// ================================================================== town strip

	/**
	 * The town header line(s): prosperity bar with its trend, mood, tax and the protector. Returns the y under it (unchanged
	 * when the sheet has no town header).
	 */
	static int kota(Lukis l, Lembar lembar, int x0, int y, int x1) {
		if (!lembar.ada(KotaLembar.KEMAKMURAN)) {
			return y;
		}
		int makmur = lembar.i(KotaLembar.KEMAKMURAN, 0);
		Component lbl = t("kota.makmur", makmur);
		int lw = l.font.width(lbl) + 2;
		l.teks(lbl, x0, y, lw, l.gaya.teksLemah);
		int bx = x0 + lw + 2;
		int bw = Math.max(36, Math.min(80, (x1 - x0) / 4));
		Bilah.gambar(l, bx, y + 3, bx + bw, makmur / 100.0F, makmur >= 55 ? 0xFF5FA88C : makmur >= 35 ? 0xFFD0A040 : 0xFFB03030, new float[] {0.55F});
		int tren = lembar.i(KotaLembar.TREN, 0);
		IkonKota.gambar(l.g, IkonKota.tren(tren), bx + bw + 2, y - 1, 10);
		int x = bx + bw + 16;
		int suasana = lembar.i(KotaLembar.SUASANA_KOTA, 0);
		Component s = t("kota.suasana", Brush.bertanda(suasana));
		int sw = l.font.width(s);
		if (x + sw < x1) {
			l.teks(s, x, y, sw + 1, suasana >= 0 ? l.gaya.teksLemah : Palette.PERINGATAN);
			x += sw + 8;
		}
		if (lembar.ada(KotaLembar.PAJAK)) {
			Component p = t("kota.pajak", lembar.i(KotaLembar.PAJAK, 0));
			int pw = l.font.width(p);
			if (x + pw < x1) {
				l.teks(p, x, y, pw + 1, l.gaya.teksLemah);
				x += pw + 8;
			}
		}
		Lembar pel = lembar.sub(KotaLembar.PELINDUNG);
		int h = 12;
		if (!pel.kosongSemua()) {
			boolean anda = pel.b(LembarKunci.ANDA);
			Component n = anda ? t("kota.pelindung_anda", pel.teks(LembarKunci.NAMA)) : t("kota.pelindung", pel.teks(LembarKunci.NAMA));
			if (x + 30 < x1) {
				l.teks(n, x, y, x1 - x, anda ? EMAS : l.gaya.teksSamar);
			} else {
				l.teks(n, x0, y + h, x1 - x0, anda ? EMAS : l.gaya.teksSamar);
				h += 12;
			}
		}
		return y + h + 2;
	}

	/** A "label  value" line (value right); returns the height used (12). */
	static int baris(Lukis l, Component label, Component nilai, int x0, int y, int x1, int warnaNilai) {
		int vw = l.font.width(nilai);
		l.teks(label, x0, y, Math.max(8, x1 - x0 - vw - 6), l.gaya.teksLemah);
		l.kanan(nilai, x1, y, warnaNilai);
		return 12;
	}

	/** A sparkline of a series (ints, scaled to the box); the last point is marked. */
	static void garis(Lukis l, int[] nilai, int x0, int y0, int x1, int y1, int warna) {
		if (nilai.length < 2 || x1 - x0 < 8 || y1 - y0 < 6) {
			return;
		}
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (int v : nilai) {
			lo = Math.min(lo, v);
			hi = Math.max(hi, v);
		}
		float rentang = Math.max(1, hi - lo);
		float px = x0;
		float py = y1;
		for (int i = 0; i < nilai.length; i++) {
			float cx = x0 + (x1 - x0) * i / (float) (nilai.length - 1);
			float cy = y1 - (y1 - y0) * (nilai[i] - lo) / rentang;
			if (i > 0) {
				Brush.line(l.g, px, py, cx, cy, 1.0F, warna);
			}
			px = cx;
			py = cy;
		}
		Brush.disc(l.g, px, py, 1.6F, warna);
	}

	// ================================================================== plan silhouette

	/**
	 * The turning preview of a growth plan (42.11.13) with the single kit: the server's column heights and top colours
	 * (&le; 32 x 32) drawn as an oblique block pile that spins slowly ({@code sudut} radians), each column a top face and two
	 * shaded sides. Fits the box; nothing when the block is empty.
	 */
	static void siluet(Lukis l, Lembar s, int x0, int y0, int x1, int y1, float sudut) {
		int w = s.i(KotaLembar.LEBAR, 0);
		int d = s.i(KotaLembar.PANJANG, 0);
		int[] tinggi = s.ia(KotaLembar.Kavling.TINGGI);
		int[] warna = s.ia(KotaLembar.WARNA);
		if (w <= 0 || d <= 0 || tinggi.length < w * d || warna.length < w * d) {
			return;
		}
		int maxH = 1;
		for (int h : tinggi) {
			maxH = Math.max(maxH, h);
		}
		float cos = (float) Math.cos(sudut);
		float sin = (float) Math.sin(sudut);
		float span = (float) Math.sqrt(w * w + d * d);
		float unit = Math.min((x1 - x0) / (span + 2.0F), (y1 - y0) / (span * 0.5F + maxH * 0.9F + 2.0F));
		unit = Math.max(1.0F, unit);
		float cx = (x0 + x1) / 2.0F;
		float baseY = y1 - 4 - span * 0.25F * unit;
		Brush.disc(l.g, cx, baseY + span * 0.12F * unit, span * 0.45F * unit, Palette.argb(0x40, 0x000000));
		int n = w * d;
		float[] depth = new float[n];
		Integer[] order = new Integer[n];
		for (int i = 0; i < n; i++) {
			float px = (i % w) - w / 2.0F + 0.5F;
			float pz = (i / w) - d / 2.0F + 0.5F;
			depth[i] = px * sin + pz * cos;
			order[i] = i;
		}
		java.util.Arrays.sort(order, (a, b) -> Float.compare(depth[a], depth[b]));
		float hu = unit * 0.9F;
		for (int idx : order) {
			int h = tinggi[idx];
			int c = warna[idx];
			if (h <= 0 || c == 0) {
				continue;
			}
			float px = (idx % w) - w / 2.0F + 0.5F;
			float pz = (idx / w) - d / 2.0F + 0.5F;
			float rx = px * cos - pz * sin;
			float rz = px * sin + pz * cos;
			float sx = cx + rx * unit;
			float sy = baseY + rz * unit * 0.5F;
			float top = sy - h * hu;
			float half = unit * 0.55F;
			l.g.fill(Math.round(sx - half), Math.round(top + unit * 0.25F), Math.round(sx), Math.round(sy + unit * 0.25F), Palette.darken(c, 0.35F));
			l.g.fill(Math.round(sx), Math.round(top + unit * 0.25F), Math.round(sx + half), Math.round(sy + unit * 0.25F), Palette.darken(c, 0.5F));
			l.g.fill(Math.round(sx - half), Math.round(top - unit * 0.25F), Math.round(sx + half), Math.round(top + unit * 0.25F), Palette.lighten(c, 0.08F));
		}
	}

	private KotaGambar() {
	}
}
