package dev.kultivator.client.dunia.kota42.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.Potret;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Shared drawing of the 42U-2 screen modes (DESIGN 42.11.9-42.11.14) that live inside existing 38/41 screens (Yamen,
 * black market, betting table, recruitment stall, 41 politics, events) and the new {@code LayarKavling}: the town strip,
 * a townsperson row (portrait, name, age and job, mood glyph, heart in the colour of the bond), requirement rows, bars and
 * the turning silhouette of a growth plan. Only kit calls; never sends ops (the host screen does).
 */
public final class SketsaKota {

	/** Lang prefix of every 42U-2 text ({@code lang-src/kota_hidup_ui2}). */
	public static final String K = "kultivator.kota42.ui2.";
	/** The Ghost Market's green accent ({@code KotaLembar.PasarHantu.WARNA_HIJAU}). */
	public static final int HIJAU = KotaLembar.PasarHantu.WARNA_HIJAU;

	public static MutableComponent t(String kunci, Object... args) {
		return Component.translatable(K + kunci, args);
	}

	// ================================================================== small marks

	/** The mood glyph beside a name (42.11.1: ☺ ☁ ! ✕ ≈ +; netral none). */
	public static String glyphWajah(String wajah) {
		return switch (wajah) {
			case KotaLembar.WAJAH_SENANG -> "☺";
			case KotaLembar.WAJAH_SEDIH -> "☁";
			case KotaLembar.WAJAH_TAKUT -> "!";
			case KotaLembar.WAJAH_MARAH -> "✕";
			case KotaLembar.WAJAH_MABUK -> "≈";
			case KotaLembar.WAJAH_SAKIT -> "+";
			default -> "";
		};
	}

	public static int warnaWajah(String wajah) {
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

	/** Heart colour of an {@code akrab} band (enemy red, stranger grey, known pale, close rose, friend gold). */
	public static int warnaHati(String akrab) {
		return switch (akrab) {
			case KotaLembar.AKRAB_MUSUH -> 0xFF6A1A1A;
			case KotaLembar.AKRAB_KENAL -> 0xFFE8A0A8;
			case KotaLembar.AKRAB_AKRAB -> 0xFFE0506A;
			case KotaLembar.AKRAB_SAHABAT -> 0xFFF0C040;
			default -> 0xFF8A8A8A;
		};
	}

	/** A small heart (two discs and a triangle of rects) of {@code size} px. */
	public static void hati(Lukis l, float x, float y, float size, int warna) {
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

	/** A bar 0..1 with a faint groove, optional marker at {@code tanda} (-1 none). */
	public static void batang(Lukis l, int x0, int y, int w, int h, float isi, int warna, float tanda) {
		l.g.fill(x0, y, x0 + w, y + h, Palette.argb(0x55, 0x000000));
		int fw = Math.round(w * Math.max(0.0F, Math.min(1.0F, isi)));
		if (fw > 0) {
			l.g.fillGradient(x0, y, x0 + fw, y + h, warna, Palette.darken(warna, 0.25F));
		}
		l.g.fill(x0, y, x0 + w, y + 1, 0x28FFFFFF);
		if (tanda >= 0.0F) {
			int tx = x0 + Math.round(w * tanda);
			l.g.fill(tx, y - 1, tx + 1, y + h + 1, 0xFFFFF0C0);
		}
	}

	/** Ticks or crosses a requirement row (ok, teks, punya caption); returns the height used (0 when it does not fit). */
	public static int syarat(Lukis l, Lembar s, int x0, int y, int x1, int yMaks) {
		if (y + 9 > yMaks) {
			return 0;
		}
		boolean ok = s.b(LembarKunci.OK);
		IkonTempat.gambar(l.g, ok ? IkonTempat.CENTANG : IkonTempat.SILANG, x0, y, 9, 0xFFFFFFFF);
		Component punya = s.teksAtau(KotaLembar.Cabang.PUNYA);
		int pw = punya == null ? 0 : Math.min(70, Math.round(l.font.width(punya) * 0.75F) + 4);
		l.teks(s.teks(LembarKunci.TEKS), x0 + 12, y + 1, x1 - x0 - 12 - pw, ok ? l.gaya.teks : l.gaya.teksLemah, 0.8F);
		if (punya != null) {
			l.kanan(punya, x1, y + 2, ok ? l.gaya.teksSamar : TempatWarna.PERINGATAN, 0.75F);
		}
		return 11;
	}

	// ================================================================== townsperson

	/** "34 · Koki" (age and job of a warga row). */
	public static Component umurKerja(Lembar w) {
		Component kerja = w.teksAtau(KotaLembar.KERJA);
		MutableComponent out = t("umur", w.i(KotaLembar.UMUR, 0));
		if (kerja != null) {
			out.append(" · ").append(kerja);
		}
		return out;
	}

	/**
	 * A townsperson card row: portrait ({@code potret} entity, else a brush silhouette), bold name with the mood glyph,
	 * "age · job" under it and the heart of the bond on the right. Returns the x where free space starts after the name
	 * block (for the caller's extra column).
	 */
	public static int warga(Lukis l, Lembar w, int x0, int y0, int x1, int y1, boolean sorot, boolean pilih, TempatWarna.Gaya gaya) {
		PanelTempat.kartu(l.g, gaya, x0, y0, x1, y1, sorot ? 1.0F : 0.0F, pilih);
		int ps = Math.max(14, Math.min(36, y1 - y0 - 4));
		Potret.gambar(l, x0 + 2, y0 + (y1 - y0 - ps) / 2, ps, w.i(LembarKunci.POTRET, -1), LembarKunci.Ikon.TIDAK_ADA, false, 0.0F);
		int tx = x0 + ps + 6;
		int hatiX = x1 - 12;
		Component nama = w.teks(LembarKunci.NAMA);
		String glyph = glyphWajah(w.s(KotaLembar.WAJAH));
		int namaW = Math.min(hatiX - tx - 14, Math.round(l.font.width(nama) * 0.9F));
		l.teks(Brush.bold(nama), tx, y0 + 3, hatiX - tx - 14, gaya.teks, 0.9F);
		if (!glyph.isEmpty()) {
			Brush.text(l.g, l.font, Component.literal(glyph), tx + namaW + 3, y0 + 3, warnaWajah(w.s(KotaLembar.WAJAH)), 0.9F, false);
		}
		if (y1 - y0 >= 20) {
			l.keterangan(umurKerja(w), tx, y0 + 13, hatiX - tx - 4, gaya.teksLemah);
		}
		if (w.ada(KotaLembar.AKRAB)) {
			hati(l, hatiX, y0 + 4, 8, warnaHati(w.s(KotaLembar.AKRAB)));
			if (l.di(hatiX, y0 + 3, hatiX + 9, y0 + 12)) {
				l.tip(t("akrab." + w.s(KotaLembar.AKRAB), w.i(KotaLembar.KESAN, 0)));
			}
		}
		return tx;
	}

	// ================================================================== town strip

	/**
	 * One line under the title: prosperity bar, town mood, tax and the protector seal. Returns the y under it (unchanged
	 * when the sheet has no town header).
	 */
	public static int kota(Lukis l, Lembar lembar, int x0, int y, int x1, TempatWarna.Gaya gaya) {
		if (!lembar.ada(KotaLembar.KEMAKMURAN)) {
			return y;
		}
		int makmur = lembar.i(KotaLembar.KEMAKMURAN, 0);
		Component lbl = t("kota.makmur", makmur);
		int lw = Math.round(l.font.width(lbl) * 0.75F);
		l.keterangan(lbl, x0, y, lw + 2, gaya.teksLemah);
		int bx = x0 + lw + 4;
		int bw = Math.max(30, Math.min(70, (x1 - x0) / 5));
		batang(l, bx, y + 2, bw, 4, makmur / 100.0F, makmur >= 55 ? 0xFF5FA88C : makmur >= 35 ? 0xFFD0A040 : 0xFFB03030, 0.55F);
		int x = bx + bw + 8;
		int suasana = lembar.i(KotaLembar.SUASANA_KOTA, 0);
		Component s = t("kota.suasana", TeksTempat.bertanda(suasana));
		l.keterangan(s, x, y, 70, suasana >= 0 ? gaya.teksLemah : TempatWarna.PERINGATAN);
		x += Math.min(70, Math.round(l.font.width(s) * 0.75F)) + 8;
		if (lembar.ada(KotaLembar.PAJAK) && x + 40 < x1) {
			Component p = t("kota.pajak", lembar.i(KotaLembar.PAJAK, 0));
			l.keterangan(p, x, y, 50, gaya.teksLemah);
			x += Math.min(50, Math.round(l.font.width(p) * 0.75F)) + 8;
		}
		Lembar pel = lembar.sub(KotaLembar.PELINDUNG);
		if (!pel.kosongSemua() && x + 30 < x1) {
			Component n = pel.b(LembarKunci.ANDA) ? t("kota.pelindung_anda", pel.teks(LembarKunci.NAMA)) : t("kota.pelindung", pel.teks(LembarKunci.NAMA));
			l.keterangan(n, x, y, x1 - x, pel.b(LembarKunci.ANDA) ? TempatWarna.EMAS : gaya.teksSamar);
		}
		return y + 10;
	}

	// ================================================================== plan silhouette

	/**
	 * The turning preview of a growth plan (42.11.13): the server's column heights and top colours (&le; 32 x 32) drawn as
	 * an oblique block pile that spins slowly ({@code sudut} radians), each column a top face and two shaded sides. Fits
	 * the box; nothing when the block is empty.
	 */
	public static void siluet(Lukis l, Lembar s, int x0, int y0, int x1, int y1, float sudut) {
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
		// ground shadow
		Brush.disc(l.g, cx, baseY + span * 0.12F * unit, span * 0.45F * unit, Palette.argb(0x40, 0x000000));
		// painter's order: far rows first (sorted by rotated depth)
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
			// side (darker, lit from the left), then the top face
			l.g.fill(Math.round(sx - half), Math.round(top + unit * 0.25F), Math.round(sx), Math.round(sy + unit * 0.25F), Palette.darken(c, 0.35F));
			l.g.fill(Math.round(sx), Math.round(top + unit * 0.25F), Math.round(sx + half), Math.round(sy + unit * 0.25F), Palette.darken(c, 0.5F));
			l.g.fill(Math.round(sx - half), Math.round(top - unit * 0.25F), Math.round(sx + half), Math.round(top + unit * 0.25F), Palette.lighten(c, 0.08F));
		}
	}

	private SketsaKota() {
	}
}
