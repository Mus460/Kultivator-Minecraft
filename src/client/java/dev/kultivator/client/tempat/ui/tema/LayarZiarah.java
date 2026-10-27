package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.TabBar;
import dev.kultivator.client.screen.ui.Lembar;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.Ziarah;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * {@code tema_ziarah} (DESIGN 38.T.6.4): the pilgrimage of the Seribu Anak Tangga, registered at the Kuil Kaki. Left the
 * mountain with the stairway zigzagging up through the nine landings to the summit shrine: every landing whose lantern the
 * pilgrim has lit glows, the next one pulses, a click marks a landing on the world map. Right the state of the pilgrimage
 * (not started, walking, done, cancelled with the reason), the rules as a list (no flying, no cable car, no pulley, no
 * teleport, within one day) and the Restu Puncak blessing with its days, then "Daftar ziarah" or "Berhenti" (the one
 * confirm seal of the Gulungan, light: the walk so far is lost). A Di Sini page on the single kit (43.8.3); in the
 * narrow layout the mountain and the state sit side by side.
 */
public final class LayarZiarah extends TemaHalaman {

	public LayarZiarah(int npcId, CompoundTag lembar) {
		super(Ziarah.LAYAR, npcId, lembar, "layar.ziarah", Palette.Gaya.BATU);
	}

	@Override
	protected List<TabBar.Tab> tabs() {
		return List.of();
	}

	@Override
	protected void lukisTema(Lukis l, int x0, int y0, int x1, int y1) {
		String status = switch (lembar.s(LembarKunci.STATUS, Ziarah.S_BELUM)) {
			case Ziarah.S_BERJALAN -> Ziarah.S_BERJALAN;
			case Ziarah.S_SELESAI -> Ziarah.S_SELESAI;
			case Ziarah.S_BATAL -> Ziarah.S_BATAL;
			default -> Ziarah.S_BELUM;
		};
		int y = y0;
		if (Ziarah.S_BATAL.equals(status)) {
			Component alasan = lembar.teksAtau(Ziarah.BATAL);
			y += pita(l, TemaUi.I_BORDES, alasan != null ? TemaUi.k("ziarah.batal_karena", alasan) : TemaUi.k("ziarah.batal"), x0, y, x1,
					Palette.BAHAYA);
		}
		boolean sempit = tata == Tata.SEMPIT;
		y += strip(l, TemaUi.k("ziarah.ucap." + status), x0, y, x1, sempit ? 30 : 32);
		Kotak[] k;
		if (sempit) {
			int mid = x0 + (x1 - x0) * 2 / 5;
			k = new Kotak[] {new Kotak(x0, y, mid - 2, y1), new Kotak(mid + 2, y, x1, y1)};
		} else {
			k = bagi(x0, y, x1, y1);
		}
		tangga(l, k[0], status);
		status(l, k[1], status);
	}

	private void tangga(Lukis l, Kotak k, String status) {
		List<Lembar> bordes = lembar.daftar(Ziarah.BORDES);
		l.sumur(k.x0(), k.y0(), k.x1(), k.y1());
		TemaUi.latarGunung(l.g, k.x0() + 1, k.y0() + 1, k.x1() - 1, k.y1() - 1, 0xFFFFFFFF);
		int n = Math.max(9, bordes.size());
		float[] px = new float[n + 1];
		float[] py = new float[n + 1];
		float bawah = k.y1() - 10;
		float atas = k.y0() + 22;
		for (int i = 0; i <= n; i++) {
			float t = i / (float) n;
			py[i] = bawah - (bawah - atas) * t;
			float lebar = (k.w() - 30) * (0.5F - 0.32F * t);
			px[i] = (k.x0() + k.x1()) / 2.0F + (i % 2 == 0 ? -lebar : lebar) * 0.6F;
		}
		px[n] = (k.x0() + k.x1()) / 2.0F;
		// the stairway: stepped segments between the landings
		int nyala = 0;
		for (Lembar b : bordes) {
			if (b.b(Ziarah.DINYALAKAN)) {
				nyala++;
			}
		}
		for (int i = 0; i < n; i++) {
			int warna = i < nyala ? 0xFFE8D8A8 : 0xC0A8A8A0;
			float ax = px[i];
			float ay = py[i];
			float bx = px[i + 1];
			float by = py[i + 1];
			int anak = 6;
			for (int s = 0; s < anak; s++) {
				float t0 = s / (float) anak;
				float t1 = (s + 1) / (float) anak;
				float sx0 = ax + (bx - ax) * t0;
				float sy0 = ay + (by - ay) * t0;
				float sx1 = ax + (bx - ax) * t1;
				Brush.line(l.g, sx0, sy0, sx1, sy0, 1.2F, warna);
				Brush.line(l.g, sx1, sy0, sx1, ay + (by - ay) * t1, 1.0F, warna);
			}
		}
		// summit shrine
		float sx = px[n];
		float sy = py[n];
		if (Ziarah.S_SELESAI.equals(status)) {
			TemaUi.cahaya(l, Math.round(sx), Math.round(sy - 4), 22, Palette.argb(0xA0, 0xFFE8A0));
		}
		TemaUi.ikon(l.g, TemaUi.I_KUIL, Math.round(sx) - 8, Math.round(sy) - 14, 16);
		// landings (the number shows when the stair is wide enough; always in the tooltip)
		boolean nomor = k.w() >= 110;
		for (int i = 0; i < bordes.size() && i < n; i++) {
			Lembar b = bordes.get(i);
			boolean lit = b.b(Ziarah.DINYALAKAN);
			boolean berikut = !lit && i == nyala && Ziarah.S_BERJALAN.equals(status);
			int cx = Math.round(px[i]);
			int cy = Math.round(py[i]);
			if (lit) {
				TemaUi.cahaya(l, cx, cy - 3, 9, Palette.argb(0x90, 0xFFC060));
			}
			TemaUi.lentera(l.g, lit ? 2 : berikut ? 1 : 0, cx - 4, cy - 10, 8, 10,
					berikut ? Palette.argb(Math.round(150 + 105 * l.denyut()), 0xFFFFFF) : 0xFFFFFFFF);
			int no = b.i(LembarKunci.NO, i + 1);
			if (nomor) {
				Brush.text(l.g, font, Component.literal(Integer.toString(no)), cx + 6, cy - 9, lit ? 0xFFFFF0C0 : 0xC0E0E0E0, 1.0F, true);
			}
			final int bx = b.i(LembarKunci.X, 0);
			final int bz = b.i(LembarKunci.Z, 0);
			boolean adaPos = b.ada(LembarKunci.X);
			l.zona("bordes" + i, cx - 6, cy - 12, cx + 10, cy + 2, adaPos ? () -> kirim(LembarKunci.op(LembarKunci.OP_TANDAI, bx, bz)) : null, adaPos,
					null, true);
			l.fokusCincin("bordes" + i, cx - 6, cy - 12, cx + 10, cy + 2);
			if (l.di(cx - 6, cy - 12, cx + 10, cy + 2)) {
				l.tip(List.of(Brush.bold(Component.literal(no + ". ").append(b.teks(LembarKunci.NAMA))), TemaUi.k(lit ? "ziarah.menyala" : "ziarah.padam"),
						TemaUi.k("ziarah.tandai")));
			}
		}
		Brush.text(l.g, font, TemaUi.k("ziarah.bordes_n", nyala, bordes.size()), k.x0() + 4, k.y0() + 3, 0xFFF4F0E4, 1.0F, true);
	}

	private void status(Lukis l, Kotak k, String status) {
		l.kartu(k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
		int x0 = k.x0() + 5;
		int x1 = k.x1() - 5;
		int y = k.y0() + 4;
		int by = k.y1() - 19;
		y += subjudul(l, TemaUi.k("ziarah.status." + status), x0, y, x1);
		Lembar restu = lembar.sub(Ziarah.RESTU);
		int sisa = restu.i(LembarKunci.SISA_HARI, 0);
		int jeda = restu.i(Ziarah.JEDA_HARI, 0);
		// the blessing and the cooldown keep their place above the button; the rules take what is left
		int bawahAturan = by - 15 - (jeda > 0 ? 10 : 0);
		List<String> syarat = lembar.sl(Ziarah.SYARAT_ZIARAH);
		if (!syarat.isEmpty() && y + 23 <= bawahAturan) {
			y += subjudul(l, TemaUi.k("ziarah.syarat"), x0, y, x1) - 2;
			for (int i = 0; i < syarat.size(); i++) {
				if (y + 10 > bawahAturan) {
					l.samar(Component.literal("+" + (syarat.size() - i)), x0 + 12, y - 1, x1 - x0 - 12);
					break;
				}
				Glyphs.Atlas.ikon(l.g, Glyphs.Atlas.SILANG, x0, y, 9, Ziarah.S_BATAL.equals(status) ? 0xFFFF8A7A : 0xFFFFFFFF);
				l.teks(TemaUi.k("ziarah.aturan." + syarat.get(i)), x0 + 12, y, x1 - x0 - 12, gaya.teks);
				y += 10;
			}
			y += 3;
		}
		y = Math.max(y, bawahAturan);
		TemaUi.ikon(l.g, TemaUi.I_RESTU, x0, y, 12);
		if (sisa > 0) {
			l.teks(TemaUi.k("ziarah.restu_aktif", Brush.hari(sisa)), x0 + 14, y + 2, x1 - x0 - 14, aksen());
		} else {
			l.teks(TemaUi.k("ziarah.restu"), x0 + 14, y + 2, x1 - x0 - 14, gaya.teksLemah);
		}
		y += 13;
		if (jeda > 0) {
			l.samar(TemaUi.k("ziarah.jeda", Brush.hari(jeda)), x0, y, x1 - x0);
		}
		if (Ziarah.S_BERJALAN.equals(status)) {
			Component label = TemaUi.k("ziarah.berhenti");
			boolean baca = !tulisBoleh();
			tombol(l, "berhenti", x0, by, x1 - x0, 15, label, !baca, baca ? alasanBaca() : null,
					() -> konfirmasi(SegelKonfirmasi.Tingkat.RINGAN, Component.translatable("kultivator.gui43.konteks.yakin", label),
							() -> kirim(Ziarah.OP_BERHENTI)));
		} else {
			boolean bisa = jeda <= 0;
			tombolOp(l, "daftar", x0, by, x1 - x0, 15, TemaUi.k("ziarah.daftar"), bisa, bisa ? null : TemaUi.k("ziarah.jeda", Brush.hari(jeda)),
					Ziarah.OP_DAFTAR);
		}
	}
}
