package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.CincinWaktu;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Lembar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.PetaKota;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * {@code peta_kota} (DESIGN 38.U.3.8): the capital's map and directory, the subs Kota / Layanan / Tokoh / Acara of Dunia ›
 * Peta (43.5, G23, M12; the gate guard's "peta" service opens it as a context). Text at 1.0 (43.0.5).
 * "Peta": a schematic 3 x 3 of the walled districts in their token colours plus the outer districts, the player as a
 * pulsing dot, hover / click a district to list its services, "Tandai di peta dunia". "Layanan": every service with its
 * hours and whether it is open now. "Tokoh": recurring characters the player knows and where they are by their schedule.
 * "Acara": the calendar with countdowns.
 */
public final class LayarPetaKota extends LayarInti {

	private final DaftarGulir daftarLayanan = new DaftarGulir();
	private final DaftarGulir daftarTokoh = new DaftarGulir();
	private final DaftarGulir daftarAcara = new DaftarGulir();
	private String distrikPilih = "";

	public LayarPetaKota(int npcId, CompoundTag lembar) {
		super(PetaKota.LAYAR, npcId, lembar, k("layar.peta_kota"), LembarKunci.GAYA_GIOK);
	}

	@Override
	protected List<Tab> daftarTab() {
		return List.of(tab(PetaKota.TAB_PETA, "tab.peta", Glyphs.Atlas.PETA), tab(PetaKota.TAB_LAYANAN, "tab.layanan", LembarKunci.Ikon.LONCENG),
				tab(PetaKota.TAB_TOKOH, "tab.tokoh", Glyphs.Atlas.HATI), tab(PetaKota.TAB_ACARA, "tab.acara", Glyphs.Atlas.OBOR));
	}

	@Override
	protected void setelahLembar(boolean buka) {
		if (distrikPilih.isEmpty()) {
			distrikPilih = lembar.s(PetaKota.DISTRIK_PEMAIN, LembarKunci.DISTRIK_PUSAT);
		}
	}

	@Override
	protected void tabBerubah() {
		long now = Util.getMillis();
		daftarLayanan.mulaiUlang(now);
		daftarTokoh.mulaiUlang(now);
		daftarAcara.mulaiUlang(now);
	}

	@Override
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		switch (tab) {
			case PetaKota.TAB_LAYANAN -> layanan(l, x0, y0, x1, y1);
			case PetaKota.TAB_TOKOH -> tokoh(l, x0, y0, x1, y1);
			case PetaKota.TAB_ACARA -> acara(l, x0, y0, x1, y1);
			default -> peta(l, x0, y0, x1, y1);
		}
	}

	private void peta(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> distrik = lembar.daftar(PetaKota.DISTRIK_DAFTAR);
		Kotak[] k = bagi(x0, y0, x1, y1);
		Kotak m = k[0];
		// the walled 3 x 3 shrinks to leave rows for the outer districts (they wrap to the column width)
		int nLuar = 0;
		for (Lembar d : distrik) {
			if (d.b(PetaKota.LUAR) || d.i(PetaKota.GX, -1) < 0) {
				nLuar++;
			}
		}
		int cell0 = Math.max(16, (Math.min(m.w(), m.h() - 22) - 8) / 3);
		int perBaris = Math.max(1, (m.w() + 3) / (Math.max(30, cell0 - 4) + 3));
		int barisLuar = nLuar == 0 ? 0 : (nLuar + perBaris - 1) / perBaris;
		int catatanH = lembar.ada(PetaKota.POS_X) ? 0 : 10;
		int size = Math.max(56, Math.min(m.w(), m.h() - 12 - barisLuar * 15 - catatanH));
		int cell = Math.max(16, (size - 8) / 3);
		int gx = m.x0() + (m.w() - cell * 3) / 2;
		int gy = m.y0() + 2;
		// the wall, the moat and the four gates
		l.g.fill(gx - 5, gy - 5, gx + cell * 3 + 5, gy + cell * 3 + 5, 0xFF4F7A8A);
		l.g.fill(gx - 3, gy - 3, gx + cell * 3 + 3, gy + cell * 3 + 3, 0xFF8A7A62);
		int mid = gx + cell * 3 / 2;
		int midY = gy + cell * 3 / 2;
		l.g.fill(mid - 3, gy - 6, mid + 3, gy - 2, Palette.GERBANG);
		l.g.fill(mid - 3, gy + cell * 3 + 2, mid + 3, gy + cell * 3 + 6, Palette.GERBANG);
		l.g.fill(gx - 6, midY - 3, gx - 2, midY + 3, Palette.GERBANG);
		l.g.fill(gx + cell * 3 + 2, midY - 3, gx + cell * 3 + 6, midY + 3, Palette.GERBANG);
		int luarX = m.x0();
		int luarY = gy + cell * 3 + 8;
		for (Lembar d : distrik) {
			String id = d.id();
			int warna = Palette.distrik(id);
			int dx0;
			int dy0;
			int dw;
			int dh;
			if (d.b(PetaKota.LUAR) || d.i(PetaKota.GX, -1) < 0) {
				dw = Math.max(30, cell - 4);
				dh = 12;
				if (luarX + dw > m.x1() && luarX > m.x0()) {
					luarX = m.x0();
					luarY += 15;
				}
				dx0 = luarX;
				dy0 = luarY;
				luarX += dw + 3;
			} else {
				dx0 = gx + d.i(PetaKota.GX, 0) * cell + 1;
				dy0 = gy + d.i(PetaKota.GZ, 0) * cell + 1;
				dw = cell - 2;
				dh = cell - 2;
			}
			boolean sel = id.equals(distrikPilih);
			boolean over = l.di(dx0, dy0, dx0 + dw, dy0 + dh);
			l.g.fill(dx0, dy0, dx0 + dw, dy0 + dh, Palette.argb(sel ? 0xFF : over ? 0xE0 : 0xB8, warna & 0xFFFFFF));
			// rooftops: a little grid of houses in each block
			for (int hy = dy0 + 3; hy < dy0 + dh - 3; hy += 5) {
				for (int hx = dx0 + 3; hx < dx0 + dw - 3; hx += 5) {
					l.g.fill(hx, hy, hx + 3, hy + 3, Palette.argb(0x40, 0x000000));
				}
			}
			if (sel) {
				Brush.frame(l.g, dx0 - 1, dy0 - 1, dx0 + dw + 1, dy0 + dh + 1, Palette.GOLD_LIGHT);
			}
			if (dh > 14) {
				Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.distrik(id), dx0 + dw / 2 - 6, dy0 + dh / 2 - 6, 12, 0xFFFFFFFF);
			} else {
				// outer districts are thin strips: their name in small ink (keyboard users see it without hovering)
				Brush.fitCentered(l.g, font, d.teks(LembarKunci.NAMA), dx0 + dw / 2.0F, dy0 + 2.0F, dw - 4, 0xFFFFF8EC, 1.0F, 1.0F, true);
			}
			l.zona("distrik:" + id, dx0, dy0, dx0 + dw, dy0 + dh, () -> distrikPilih = id, true, null, true);
			if (over) {
				l.tip(d.teks(LembarKunci.NAMA));
			}
			l.fokusCincin("distrik:" + id, dx0, dy0, dx0 + dw, dy0 + dh);
		}
		if (lembar.ada(PetaKota.POS_X)) {
			float px = gx + lembar.f(PetaKota.POS_X, 1.5F) * cell;
			float pz = gy + lembar.f(PetaKota.POS_Z, 1.5F) * cell;
			float pulse = 2.0F + l.denyut() * 1.5F;
			Brush.disc(l.g, px, pz, pulse + 1.0F, Palette.argb(0x60, 0xFFFFFF));
			Brush.disc(l.g, px, pz, 2.2F, Palette.SEAL);
		} else {
			l.teks(k("peta.di_luar"), m.x0(), m.y1() - 9, m.w(), l.gaya.teksSamar);
		}
		// right: the selected district's services
		Kotak d = k[1];
		Lembar pilih = null;
		for (Lembar e : distrik) {
			if (e.id().equals(distrikPilih)) {
				pilih = e;
			}
		}
		l.kartu(d.x0(), d.y0(), d.x1(), d.y1(), 0.0F, false);
		if (pilih == null) {
			l.bungkus(k("peta.pilih_distrik"), d.x0() + 5, d.y0() + 5, d.w() - 10, l.gaya.teksSamar, d.h() - 10);
			return;
		}
		int y = d.y0() + 4;
		l.g.fill(d.x0() + 4, y, d.x0() + 8, y + 10, Palette.distrik(pilih.id()));
		l.teks(Brush.bold(pilih.teks(LembarKunci.NAMA)), d.x0() + 11, y + 1, d.w() - 14, l.gaya.teks);
		y += 14;
		for (Lembar s : pilih.daftar(PetaKota.LAYANAN)) {
			if (y > d.y1() - 30) {
				break;
			}
			Glyphs.Atlas.ikon(l.g, s.i(LembarKunci.IKON, -1), d.x0() + 5, y - 1, 10, 0xFFFFFFFF);
			l.teks(s.teks(LembarKunci.NAMA), d.x0() + 18, y, d.w() - 22, l.gaya.teks);
			Component cat = s.teksAtau(LembarKunci.CATATAN);
			if (cat != null) {
				y += 10;
				l.teks(cat, d.x0() + 18, y, d.w() - 22, l.gaya.teksSamar);
			}
			y += 11;
		}
		// "mark on world map": the first service of the district with a position
		int tx = 0;
		int tz = 0;
		boolean ada = false;
		for (Lembar s : lembar.daftar(PetaKota.LAYANAN)) {
			if (pilih.id().equals(s.s(LembarKunci.DISTRIK)) && s.ada(LembarKunci.X)) {
				tx = s.i(LembarKunci.X, 0);
				tz = s.i(LembarKunci.Z, 0);
				ada = true;
				break;
			}
		}
		tombolOp(l, "tandai", d.x0() + 4, d.y1() - 19, d.w() - 8, 15, k("peta.tandai"), ada, k("peta.tanpa_lokasi"),
				LembarKunci.op(LembarKunci.OP_TANDAI, tx, tz));
	}

	private void layanan(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(PetaKota.LAYANAN);
		if (rows.isEmpty()) {
			kosong(l, k("peta.layanan_kosong"), x0, y0, x1, y1);
			return;
		}
		daftarLayanan.gambar(l, "layanan", x0, y0, x1, y1, rows.size(), 22, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			boolean buka = e.b(PetaKota.BUKA);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			lk.g.fill(rx0, ry0, rx0 + 2, ry1, Palette.distrik(e.s(LembarKunci.DISTRIK)));
			Glyphs.Atlas.ikon(lk.g, e.i(LembarKunci.IKON, LembarKunci.Ikon.layanan(e.id())), rx0 + 5, ry0 + 3, 16, buka ? 0xFFFFFFFF : 0x90FFFFFF);
			lk.teks(e.teks(LembarKunci.NAMA), rx0 + 25, ry0 + 2, rx1 - rx0 - 100, buka ? l.gaya.teks : Palette.terkunci(l.gaya.teks));
			lk.teks(e.teks(LembarKunci.CATATAN), rx0 + 25, ry0 + 12, rx1 - rx0 - 100, l.gaya.teksSamar);
			lk.kanan(k(buka ? "peta.buka" : "peta.tutup"), rx1 - 50, ry0 + 7, buka ? 0xFF2E7A4E : Palette.BAHAYA);
			if (e.ada(LembarKunci.X)) {
				tombolOp(lk, "tandai" + i, rx1 - 46, ry0 + 4, 42, 13, k("peta.tandai_singkat"), true, null,
						LembarKunci.op(LembarKunci.OP_TANDAI, e.i(LembarKunci.X, 0), e.i(LembarKunci.Z, 0)));
			}
		}, null, null);
	}

	private void tokoh(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(PetaKota.TOKOH);
		if (rows.isEmpty()) {
			kosong(l, k("peta.tokoh_kosong"), x0, y0, x1, y1);
			return;
		}
		daftarTokoh.gambar(l, "tokoh", x0, y0, x1, y1, rows.size(), 24, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			boolean kenal = e.b(PetaKota.KENAL);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			Brush.disc(lk.g, rx0 + 11, ry0 + 11.5F, 8.0F, Palette.argb(0xC0, Palette.distrik(e.s(LembarKunci.DISTRIK)) & 0xFFFFFF));
			Glyphs.Atlas.ikon(lk.g, kenal ? Glyphs.Atlas.HATI : Glyphs.Atlas.TANYA, rx0 + 5, ry0 + 6, 12, 0xFFFFFFFF);
			Component nama = kenal ? e.teks(LembarKunci.NAMA) : k("peta.belum_kenal");
			Component gelar = e.teksAtau(LembarKunci.GELAR);
			lk.teks(gelar == null ? Brush.bold(nama) : Brush.bold(nama).copy().append(Component.literal(" · ")).append(gelar), rx0 + 24, ry0 + 3,
					rx1 - rx0 - 28, l.gaya.teks);
			lk.teks(e.teks(LembarKunci.CATATAN), rx0 + 24, ry0 + 13, rx1 - rx0 - 28, l.gaya.teksSamar);
		}, null, null);
	}

	private void acara(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(PetaKota.ACARA);
		if (rows.isEmpty()) {
			kosong(l, k("peta.acara_kosong"), x0, y0, x1, y1);
			return;
		}
		daftarAcara.gambar(l, "acara", x0, y0, x1, y1, rows.size(), 26, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			boolean aktif = e.b(LembarKunci.AKTIF);
			lk.kartu(rx0, ry0, rx1, ry1, aktif ? 0.7F : sorot ? 1.0F : 0.0F, pilih);
			// a calendar leaf with the day number
			lk.g.fill(rx0 + 4, ry0 + 3, rx0 + 22, ry0 + 22, 0xFFF4ECD4);
			lk.g.fill(rx0 + 4, ry0 + 3, rx0 + 22, ry0 + 8, Palette.SEAL);
			Brush.centered(lk.g, font, Component.literal(Integer.toString(e.i(LembarKunci.HARI, 0))), rx0 + 13, ry0 + 10, 0xFF2A1A10, 1.0F, false);
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 27, ry0 + 3, rx1 - rx0 - 70, l.gaya.teks);
			lk.teks(aktif ? k("peta.sedang_berlangsung") : e.teks(LembarKunci.CATATAN), rx0 + 27, ry0 + 14, rx1 - rx0 - 70,
					aktif ? 0xFF2E7A4E : l.gaya.teksSamar);
			if (e.ada(LembarKunci.SISA_TICK)) {
				CincinWaktu.gambar(lk, rx1 - 12, ry0 + 13, 9.0F, e.l(LembarKunci.SISA_TICK, 0L), 0L, diterima, CincinWaktu.KIRI);
			}
		}, null, null);
	}
}
