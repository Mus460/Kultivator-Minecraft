package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.CincinWaktu;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.Lift;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * {@code tema_lift} (DESIGN 38.T.6.4): the cable car station of Paman Tie Suo. Left the rope schema: the valley station,
 * the summit station, the carrying rope sagging over the middle pylon, the bull wheels turning, and the cabin hanging
 * where it really is now, swaying on its hanger (still and red-flagged when jammed). At night the sky darkens, stars
 * come out and the stations hang their "closed" boards. Right the timetable: next departure, opening hours, fare up and
 * down, seats taken in the cabin and the buttons "Naik" / "Turun".
 */
public final class LayarLift extends LayarTemaDasar {

	public LayarLift(int npcId, CompoundTag lembar) {
		super(Lift.LAYAR, npcId, lembar, "layar.lift", TempatWarna.Gaya.KAYU);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of();
	}

	@Override
	protected void gambar(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar jadwal = lembar.sub(Lift.JADWAL);
		Lembar skema = lembar.sub(Lift.SKEMA);
		boolean macet = skema.b(Lift.MACET);
		boolean tutup = jadwal.b(Lift.TUTUP);
		int y = y0;
		if (macet) {
			y += pita(l, TemaUi.I_MACET, TemaUi.k("lift.macet"), x0, y, x1, TempatWarna.PERINGATAN);
		} else if (tutup) {
			y += pita(l, TemaUi.I_MALAM, TemaUi.k("lift.malam", jadwal.teks(Lift.JAM_BUKA)), x0, y, x1, 0xFF4A5A8A);
		}
		y += strip(l, TemaUi.k(macet ? "lift.ucap_macet" : tutup ? "lift.ucap_malam" : "lift.ucap"), x0, y, x1, 32);
		Kotak[] k = bagi(x0, y, x1, y1);
		skema(l, k[0], skema, macet, tutup);
		jadwal(l, k[1], jadwal, skema, macet, tutup);
	}

	private void skema(Lukis l, Kotak k, Lembar s, boolean macet, boolean malam) {
		PanelTempat.sumur(l.g, gaya, k.x0(), k.y0(), k.x1(), k.y1());
		int x0 = k.x0() + 1;
		int y0 = k.y0() + 1;
		int x1 = k.x1() - 1;
		int y1 = k.y1() - 1;
		l.klip(x0, y0, x1, y1);
		if (malam) {
			l.g.fillGradient(x0, y0, x1, y1, 0xFF0A1322, 0xFF1A2B44);
			for (int i = 0; i < 18; i++) {
				int sx = x0 + (int) ((i * 37L + 11L) % Math.max(1, x1 - x0));
				int sy = y0 + (int) ((i * 23L + 5L) % Math.max(1, (y1 - y0) / 2));
				float kedip = 0.5F + 0.5F * (float) Math.sin(l.kini / 400.0D + i);
				l.g.fill(sx, sy, sx + 1, sy + 1, Palette.argb(Math.round(0x60 + 0x9F * kedip), 0xFFFFFF));
			}
			TemaUi.latarGunung(l.g, x0, y0 + (y1 - y0) / 4, x1, y1, 0xFF404860);
		} else {
			l.g.fillGradient(x0, y0, x1, y1, 0xFFB8D4E8, 0xFFE8EEF0);
			TemaUi.latarGunung(l.g, x0, y0 + (y1 - y0) / 5, x1, y1, 0xFFFFFFFF);
		}
		// stations
		float ax = x0 + 14;
		float ay = y1 - 18;
		float bx = x1 - 16;
		float by = y0 + 18;
		float tiang = Math.max(0.2F, Math.min(0.8F, s.f(Lift.TIANG, 0.5F)));
		float tx = ax + (bx - ax) * tiang;
		float ty = ay + (by - ay) * tiang - 6;
		stasiun(l, ax, ay, macet, malam, true);
		stasiun(l, bx, by, macet, malam, false);
		// the pylon
		Brush.line(l.g, tx, ty, tx - 5, y1, 1.5F, 0xFF4A3A2A);
		Brush.line(l.g, tx, ty, tx + 5, y1, 1.5F, 0xFF4A3A2A);
		for (int r = 1; r < 5; r++) {
			float ry = ty + (y1 - ty) * r / 5.0F;
			float lw = 5.0F * r / 5.0F;
			Brush.line(l.g, tx - lw, ry, tx + lw, ry, 0.8F, 0xFF4A3A2A);
		}
		Brush.rect(l.g, tx - 6, ty - 1, tx + 6, ty + 1, 0xFF2A1E14);
		// the rope: two sagging spans
		int tali = malam ? 0xFFA0A8B8 : 0xFF3A3A40;
		span(l, ax, ay - 10, tx, ty, 5.0F, tali);
		span(l, tx, ty, bx, by - 10, 5.0F, tali);
		// the cabin where it is
		float kabin = Math.max(0.0F, Math.min(1.0F, s.f(Lift.KABIN, 0.0F)));
		float[] p = titik(ax, ay - 10, tx, ty, bx, by - 10, tiang, kabin);
		float ayun = macet ? 0.0F : (float) Math.sin(l.kini / 520.0D) * 2.0F;
		float cx = p[0];
		float cy = p[1];
		Brush.line(l.g, cx, cy, cx + ayun, cy + 7, 1.0F, 0xFF2A2A30);
		float kx = cx + ayun;
		float ky = cy + 7;
		Brush.rect(l.g, kx - 8, ky, kx + 8, ky + 10, macet ? 0xFF8A3A2A : 0xFFB83A2A);
		Brush.rect(l.g, kx - 8, ky, kx + 8, ky + 2, 0xFF5A1A14);
		Brush.rect(l.g, kx - 6, ky + 3, kx - 1, ky + 7, malam ? 0xFFF7DC7E : 0xFFD8E8F0);
		Brush.rect(l.g, kx + 1, ky + 3, kx + 6, ky + 7, malam ? 0xFFF7DC7E : 0xFFD8E8F0);
		if (malam) {
			TemaUi.cahaya(l, Math.round(kx), Math.round(ky + 5), 10, 0x60F7DC7E);
		}
		if (macet) {
			float f = 0.5F + 0.5F * l.denyut();
			Brush.line(l.g, kx + 8, ky, kx + 8, ky - 8, 1.0F, 0xFF2A2A30);
			Brush.rect(l.g, kx + 8, ky - 8, kx + 14, ky - 4, Palette.argb(Math.round(255 * f), 0xD03020));
		}
		l.lepasKlip();
		Brush.text(l.g, font, TemaUi.k("lift.lembah"), x0 + 3, y1 - 9, malam ? 0xFFE0E4F0 : 0xFF2A2A30, 0.6F, false);
		Brush.text(l.g, font, TemaUi.k("lift.puncak"), x1 - 3 - font.width(TemaUi.k("lift.puncak")) * 0.6F, y0 + 3, malam ? 0xFFE0E4F0 : 0xFF2A2A30,
				0.6F, false);
		if (l.di(x0, y0, x1, y1)) {
			l.tip(TemaUi.k("lift.posisi", Math.round(kabin * 100.0F)));
		}
	}

	private void stasiun(Lukis l, float x, float y, boolean macet, boolean malam, boolean bawah) {
		Brush.rect(l.g, x - 10, y - 10, x + 10, y + 6, 0xFF6A4A30);
		Brush.rect(l.g, x - 12, y - 13, x + 12, y - 10, 0xFF3A2414);
		Brush.rect(l.g, x - 7, y - 6, x + 7, y + 6, 0xFF2A1A10);
		// the bull wheel
		float wx = x;
		float wy = y - 10;
		Brush.ring(l.g, wx, wy, 3.0F, 4.5F, 0xFF8A8A90);
		double a = macet ? 0.0D : l.kini / (bawah ? 240.0D : -240.0D);
		for (int s = 0; s < 3; s++) {
			double b = a + s * Math.PI * 2.0D / 3.0D;
			Brush.line(l.g, wx, wy, wx + (float) Math.cos(b) * 3.5F, wy + (float) Math.sin(b) * 3.5F, 0.8F, 0xFF8A8A90);
		}
		if (malam) {
			Brush.rect(l.g, x - 6, y - 4, x + 6, y + 1, 0xFFE8D8B0);
			Brush.rect(l.g, x - 5, y - 3, x + 5, y, 0xFF8A2A1A);
		} else {
			TemaUi.cahaya(l, Math.round(x), Math.round(y), 6, 0x40F7DC7E);
		}
	}

	/** A rope span from A to B sagging by {@code sag} px at the middle. */
	private static void span(Lukis l, float ax, float ay, float bx, float by, float sag, int warna) {
		float px = ax;
		float py = ay;
		for (int i = 1; i <= 16; i++) {
			float t = i / 16.0F;
			float nx = ax + (bx - ax) * t;
			float ny = ay + (by - ay) * t + (float) Math.sin(t * Math.PI) * sag;
			Brush.line(l.g, px, py, nx, ny, 1.0F, warna);
			px = nx;
			py = ny;
		}
	}

	/** The point on the two-span rope at {@code f} 0..1 of the whole run (pylon at {@code tiang}). */
	private static float[] titik(float ax, float ay, float tx, float ty, float bx, float by, float tiang, float f) {
		if (f <= tiang) {
			float t = f / tiang;
			return new float[] {ax + (tx - ax) * t, ay + (ty - ay) * t + (float) Math.sin(t * Math.PI) * 5.0F};
		}
		float t = (f - tiang) / (1.0F - tiang);
		return new float[] {tx + (bx - tx) * t, ty + (by - ty) * t + (float) Math.sin(t * Math.PI) * 5.0F};
	}

	private void jadwal(Lukis l, Kotak k, Lembar jadwal, Lembar skema, boolean macet, boolean tutup) {
		PanelTempat.kartu(l.g, gaya, k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
		int x0 = k.x0() + 5;
		int x1 = k.x1() - 5;
		int y = k.y0() + 4;
		y += subjudul(l, TemaUi.k("lift.jadwal"), x0, y, x1);
		if (jadwal.ada(Lift.BERANGKAT_TICK) && !tutup) {
			l.keterangan(TemaUi.k("lift.berangkat"), x0, y + 6, x1 - x0 - 30, gaya.teksLemah);
			CincinWaktu.gambar(l, x1 - 12, y + 9, 10.0F, jadwal.l(Lift.BERANGKAT_TICK, 0L), 1200L, diterima, CincinWaktu.KIRI);
			y += 22;
		}
		y += barisNilai(l, TemaUi.k("lift.jam_buka"), jadwal.teks(Lift.JAM_BUKA), x0, y, x1);
		long ongkos = lembar.l(Lift.ONGKOS, 1L);
		y += barisNilai(l, TemaUi.k("lift.ongkos_naik"), TeksTempat.nb(ongkos, false), x0, y, x1);
		y += barisNilai(l, TemaUi.k("lift.ongkos_turun"), TemaUi.k("lift.gratis"), x0, y, x1) + 3;
		// seats
		int kursi = Math.max(0, Math.min(8, skema.i(Lift.KURSI, 4)));
		int penumpang = Math.max(0, Math.min(kursi, skema.i(Lift.PENUMPANG, 0)));
		l.keterangan(TemaUi.k("lift.kursi", penumpang, kursi), x0, y, 70, gaya.teksLemah);
		for (int i = 0; i < kursi; i++) {
			int sx = x0 + 72 + i * 9;
			boolean isi = i < penumpang;
			l.g.fill(sx, y + 1, sx + 7, y + 7, isi ? 0xFFB83A2A : Palette.argb(0x50, 0x000000));
			l.g.fill(sx, y, sx + 7, y + 1, isi ? 0xFF5A1A14 : 0x30000000);
			if (isi) {
				TemaUi.ikon(l.g, TemaUi.I_ORANG, sx, y - 1, 7);
			}
		}
		y += 12;
		String arah = lembar.s(Lift.ARAH, "naik");
		l.keterangan(TemaUi.k("lift.arah." + ("turun".equals(arah) ? "turun" : "naik")), x0, y, x1 - x0, gaya.teksSamar);
		int by = k.y1() - 19;
		int w2 = (x1 - x0 - 4) / 2;
		boolean penuh = penumpang >= kursi && kursi > 0;
		Component alasan = macet ? TemaUi.k("lift.alasan_macet") : tutup ? TemaUi.k("lift.alasan_tutup") : penuh ? TemaUi.k("lift.penuh") : null;
		boolean bisa = alasan == null;
		tombolOp(l, "naik", x0, by, w2, 15, TemaUi.k("lift.naik", TeksTempat.angka(ongkos)), bisa && mampu(ongkos), alasan != null ? alasan
				: mampu(ongkos) ? null : kurang(ongkos), Lift.OP_NAIK);
		tombolOp(l, "turun", x0 + w2 + 4, by, w2, 15, TemaUi.k("lift.turun"), bisa, alasan, Lift.OP_TURUN);
	}
}
