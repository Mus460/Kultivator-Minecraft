package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarPilihan;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.TabBar;
import dev.kultivator.client.screen.ui.Lembar;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.Racik;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * {@code tema_racik} (DESIGN 38.T.3, quest {@code tema_pelabuhan_pelita} chapter 3): grinding the lighthouse lens from
 * sea-salt glass in three steps (melt, grind, polish). Left the lens workshop: the furnace with its live heat gauge and
 * flame, and the lens itself as it is at this step (a glowing glass gob, a rough disc on the wheel, the polished lens
 * throwing a beam). Under it the step track and the materials (had / needed). Right the choices of this step in the kit's
 * {@link DaftarPilihan} (G59: ↑↓ Enter, a click, or keys 1-3 under Di Sini), the grade so far in stars and "Ulang" to
 * start over (the one confirm seal, light). A Di Sini page on the single kit (43.8.3); in the narrow layout workshop and
 * choices sit side by side.
 */
public final class LayarRacik extends TemaHalaman {

	/** Entry keys of {@code bahan} (C38.T3: punya I, perlu I). */
	private static final String PUNYA = "punya";
	private static final String PERLU = "perlu";

	private int langkahLalu = -1;
	private long langkahSejak = Util.getMillis();

	public LayarRacik(int npcId, CompoundTag lembar) {
		super(Racik.LAYAR, npcId, lembar, "layar.racik", Palette.Gaya.KAYU);
	}

	@Override
	protected List<TabBar.Tab> tabs() {
		return List.of();
	}

	@Override
	protected Component petunjuk() {
		return TemaUi.k("racik.kunci");
	}

	@Override
	protected void lembarBaru(boolean buka) {
		int langkah = lembar.i(Racik.LANGKAH, 1);
		if (langkah != langkahLalu) {
			langkahLalu = langkah;
			langkahSejak = Util.getMillis();
		}
	}

	private boolean bahanCukup() {
		for (Lembar b : lembar.daftar(Racik.BAHAN)) {
			if (b.i(PUNYA, 0) < b.i(PERLU, 1)) {
				return false;
			}
		}
		return true;
	}

	@Override
	protected void lukisTema(Lukis l, int x0, int y0, int x1, int y1) {
		boolean sempit = tata == Tata.SEMPIT;
		int y = y0 + strip(l, TemaUi.k("racik.ucap." + Math.max(1, Math.min(3, lembar.i(Racik.LANGKAH, 1)))), x0, y0, x1, sempit ? 30 : 34);
		Kotak[] k;
		if (sempit) {
			int mid = x0 + (x1 - x0) * 9 / 20;
			k = new Kotak[] {new Kotak(x0, y, mid - 2, y1), new Kotak(mid + 2, y, x1, y1)};
		} else {
			k = bagi(x0, y, x1, y1);
		}
		bengkel(l, k[0]);
		pilihan(l, k[1]);
	}

	// ------------------------------------------------------------------------------------------------ workshop

	private void bengkel(Lukis l, Kotak k) {
		l.kartu(k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
		int x0 = k.x0() + 5;
		int x1 = k.x1() - 5;
		int y = k.y0() + 4;
		int total = Math.max(1, lembar.i(LembarKunci.TAHAP_TOTAL, 3));
		int langkah = Math.max(1, Math.min(total, lembar.i(Racik.LANGKAH, 1)));
		float panas = Math.max(0.0F, Math.min(1.0F, lembar.f(Racik.PANAS, 0.0F)));
		List<Lembar> bahan = lembar.daftar(Racik.BAHAN);
		int bahanH = bahan.isEmpty() ? 0 : 11 + Math.min(4, bahan.size()) * 11;
		int adeganH = Math.max(30, k.h() - 8 - 32 - bahanH);
		adegan(l, x0, y, x1, y + adeganH, langkah, panas);
		y += adeganH + 4;
		// heat gauge (with the sweet band 0.6-0.8 marked)
		TemaUi.ikon(l.g, TemaUi.I_TUNGKU, x0, y - 1, 10);
		Component persen = Component.literal(Math.round(panas * 100) + "%");
		int gx0 = x0 + 12;
		int gx1 = x1 - font.width(persen) - 4;
		l.g.fillGradient(gx0, y + 1, gx1, y + 7, 0xFF3A2A60, 0xFF201838);
		int mx0 = gx0 + Math.round((gx1 - gx0) * 0.6F);
		int mx1 = gx0 + Math.round((gx1 - gx0) * 0.8F);
		l.g.fill(mx0, y + 1, mx1, y + 7, 0x5067C99B);
		int fx = gx0 + Math.round((gx1 - gx0) * panas);
		for (int px = gx0; px < fx; px++) {
			float t = (px - gx0) / (float) Math.max(1, gx1 - gx0);
			l.g.fill(px, y + 2, px + 1, y + 6, Palette.mix(0xFFE0A040, 0xFFFF4020, t));
		}
		l.g.fill(fx - 1, y - 1, fx + 1, y + 9, Palette.GOLD_LIGHT);
		Brush.frame(l.g, gx0 - 1, y, gx1 + 1, y + 8, Palette.argb(0x90, Palette.GOLD_DARK));
		l.kanan(persen, x1, y, panas > 0.8F ? 0xFFFF8A6A : gaya.teks);
		if (l.di(gx0, y, gx1, y + 8)) {
			l.tip(List.of(TemaUi.k("racik.panas"), TemaUi.k("racik.panas_tip")));
		}
		y += 12;
		// step track
		int sw = (x1 - x0) / total;
		for (int i = 0; i < total; i++) {
			int sx = x0 + i * sw;
			boolean lewat = i + 1 < langkah;
			boolean kini = i + 1 == langkah;
			int warna = lewat ? Palette.OK : kini ? Palette.GOLD : Palette.argb(0x50, 0x000000);
			Brush.chamfer(l.g, sx + 1, y, sx + sw - 2, y + 13, 2, Palette.argb(kini ? 0xD0 : 0x80, warna & 0xFFFFFF));
			Component nama = TemaUi.k("racik.langkah." + Math.min(i + 1, 3));
			l.tengah(nama, sx + sw / 2.0F, y + 3, sw - 6, kini ? 0xFF2A1808 : 0xFFF6E8C8);
			if (l.di(sx, y, sx + sw, y + 13)) {
				l.tip(nama);
			}
		}
		y += 17;
		// materials
		if (!bahan.isEmpty() && y + 20 <= k.y1()) {
			l.teks(TemaUi.k("racik.bahan"), x0, y, x1 - x0, gaya.teksLemah);
			y += 10;
			for (int i = 0; i < bahan.size() && i < 4 && y + 10 <= k.y1(); i++) {
				Lembar b = bahan.get(i);
				int punya = b.i(PUNYA, 0);
				int perlu = b.i(PERLU, 1);
				boolean cukup = punya >= perlu;
				if (!b.barang().isEmpty()) {
					// the item icon at 10 px beside the line (a picture, not text)
					l.g.pose().pushMatrix();
					l.g.pose().translate(x0, y - 1);
					l.g.pose().scale(0.625F, 0.625F);
					l.g.item(b.barang(), 0, 0);
					l.g.pose().popMatrix();
				}
				Component jumlah = Component.literal(punya + " / " + perlu);
				l.teks(b.teks(LembarKunci.NAMA), x0 + 13, y, x1 - x0 - 17 - font.width(jumlah), gaya.teks);
				l.kanan(jumlah, x1, y, cukup ? l.gaya.naik() : l.gaya.turun());
				y += 11;
			}
		}
	}

	/**
	 * The scene of the step: 1 the furnace mouth with the glowing gob on the pipe, 2 the grinding wheel turning under a
	 * rough disc, 3 the polished lens on felt throwing a beam (better grades shine further).
	 */
	private void adegan(Lukis l, int x0, int y0, int x1, int y1, int langkah, float panas) {
		l.g.fillGradient(x0, y0, x1, y1, 0xFF2A1A12, 0xFF140C08);
		Brush.frame(l.g, x0, y0, x1, y1, Palette.argb(0x90, Palette.GOLD_DARK));
		l.klip(x0 + 1, y0 + 1, x1 - 1, y1 - 1);
		float cx = (x0 + x1) / 2.0F;
		float cy = (y0 + y1) / 2.0F + 4.0F;
		float r = Math.min(x1 - x0, y1 - y0) * 0.22F;
		long kini = l.kini;
		float masuk = Math.min(1.0F, (kini - langkahSejak) / 400.0F);
		int mutu = lembar.i(LembarKunci.MUTU, -1);
		switch (langkah) {
			case 1 -> {
				// furnace mouth
				float fw = (x1 - x0) * 0.46F;
				Brush.rect(l.g, cx - fw / 2, y0 + 6, cx + fw / 2, y1 - 6, 0xFF4A3428);
				Brush.rect(l.g, cx - fw / 2 + 4, y0 + 10, cx + fw / 2 - 4, y1 - 10, 0xFF1A0E08);
				for (int b = 0; b < 4; b++) {
					l.g.fill(Math.round(cx - fw / 2), Math.round(y0 + 6 + b * (y1 - y0 - 12) / 4.0F), Math.round(cx + fw / 2),
							Math.round(y0 + 7 + b * (y1 - y0 - 12) / 4.0F), 0x60000000);
				}
				int api = Palette.mix(0xFFE07020, 0xFFFFE080, panas);
				for (int f = 0; f < 7; f++) {
					float fx = cx - fw / 2 + 8 + f * (fw - 16) / 6.0F;
					float fh = (6.0F + 10.0F * panas) * (0.7F + 0.3F * (float) Math.sin(kini / 90.0D + f * 1.3D));
					Brush.rect(l.g, fx - 2, y1 - 11 - fh, fx + 2, y1 - 11, Palette.argb(0xC0, api & 0xFFFFFF));
				}
				TemaUi.cahaya(l, Math.round(cx), Math.round(y1 - 16), Math.round(fw * 0.6F), Palette.argb(Math.round(0x90 * panas + 0x30), 0xFF8030));
				// the gob on the blowpipe
				float gy = cy - 4 - (1.0F - masuk) * 10.0F;
				Brush.line(l.g, x0 + 4, gy - 8, cx - r * 0.5F, gy, 2.0F, 0xFF8A8A90);
				Brush.disc(l.g, cx, gy, r * 0.55F, Palette.mix(0xFFFF9040, 0xFFFFF0C0, panas));
				Brush.halo(l.g, cx, gy, r * 0.6F, 3.0F, 0xFFC060, 0.6F + 0.4F * l.denyut());
			}
			case 2 -> {
				// grinding wheel
				float wy = y1 - 10;
				Brush.disc(l.g, cx, wy, r * 1.4F, 0xFF6A6058);
				Brush.ring(l.g, cx, wy, r * 1.3F, r * 1.4F, 0xFF3A322C);
				double rot = kini / 160.0D;
				for (int s = 0; s < 8; s++) {
					double a = rot + s * Math.PI / 4.0D;
					Brush.line(l.g, cx, wy, cx + (float) Math.cos(a) * r * 1.3F, wy + (float) Math.sin(a) * r * 1.3F, 1.0F, 0x50FFFFFF);
				}
				// the rough disc above it
				float dy = wy - r * 1.4F - r * 0.32F;
				Brush.rect(l.g, cx - r, dy - r * 0.28F, cx + r, dy + r * 0.28F, 0xFFA8C8D8);
				Brush.rect(l.g, cx - r, dy - r * 0.28F, cx + r, dy - r * 0.1F, 0xFFD8EEF6);
				for (int sp = 0; sp < 6; sp++) {
					float t = ((kini / 30L + sp * 17L) % 40L) / 40.0F;
					float sx = cx + r + t * r * 1.6F;
					float sy = dy + r * 0.2F - (float) Math.sin(t * Math.PI) * r * 0.6F + t * 6;
					Brush.dot(l.g, sx, sy, 0.8F, Palette.argb(Math.round(255 * (1 - t)), 0xFFE8B0));
				}
			}
			default -> {
				// polished lens on felt with its beam
				Brush.rect(l.g, x0 + 6, y1 - 12, x1 - 6, y1 - 5, 0xFF2E4A30);
				float lenaR = r * (0.9F + 0.1F * masuk);
				float panjang = (x1 - cx) * (mutu < 0 ? 0.5F : 0.5F + 0.12F * Math.min(4, mutu));
				for (int b = 0; b < 5; b++) {
					float spread = 3.0F + b * 3.0F;
					int alfa = Math.round((0x50 - b * 0x0C) * (0.7F + 0.3F * l.denyut()));
					Brush.line(l.g, cx, cy, cx + panjang, cy - spread, 1.5F, Palette.argb(Math.max(0, alfa), 0xFFF0B0));
					Brush.line(l.g, cx, cy, cx + panjang, cy + spread, 1.5F, Palette.argb(Math.max(0, alfa), 0xFFF0B0));
				}
				Brush.disc(l.g, cx, cy, lenaR, 0xC0B8E0F0);
				Brush.ring(l.g, cx, cy, lenaR - 1.5F, lenaR, 0xFFD4A637);
				Brush.disc(l.g, cx - lenaR * 0.35F, cy - lenaR * 0.35F, lenaR * 0.25F, 0xC0FFFFFF);
				Brush.halo(l.g, cx, cy, lenaR, 2.0F, 0xFFF0B0, 0.5F + 0.5F * l.denyut());
			}
		}
		l.lepasKlip();
		l.teks(TemaUi.k("racik.langkah_ke", langkah, Math.max(1, lembar.i(LembarKunci.TAHAP_TOTAL, 3))), x0 + 4, y0 + 3, x1 - x0 - 8, 0xC0F6E8C8);
	}

	// ------------------------------------------------------------------------------------------------ choices

	private void pilihan(Lukis l, Kotak k) {
		l.kartu(k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
		int x0 = k.x0() + 5;
		int x1 = k.x1() - 5;
		int y = k.y0() + 4;
		int gy = k.y1() - 35;
		int langkah = Math.max(1, Math.min(3, lembar.i(Racik.LANGKAH, 1)));
		List<Lembar> p = lembar.daftar(Racik.PILIHAN);
		boolean cukup = bahanCukup();
		y += subjudul(l, TemaUi.k("racik.judul." + langkah), x0, y, x1 - (cukup ? 0 : 14));
		if (!cukup) {
			Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.GEMBOK, x1 - 12, k.y0() + 4, 10, 0xFFFFFFFF);
		}
		int pilihH = DaftarPilihan.tinggi(Math.min(3, p.size()), 1, 15);
		int ruangJelas = gy - 4 - y - pilihH - 3;
		if (ruangJelas >= 9) {
			y += Math.round(l.bungkus(TemaUi.k("racik.jelas." + langkah), x0, y, x1 - x0, gaya.teksLemah, Math.min(36, ruangJelas))) + 3;
		} else if (l.di(x0, k.y0(), x1, y)) {
			l.tip(TemaUi.k("racik.jelas." + langkah));
		}
		// the step's choices (G59); a lack of materials or a read-only view locks them with the reason
		Component kunci = !cukup ? TemaUi.k("racik.bahan_kurang") : !tulisBoleh() ? alasanBaca() : null;
		List<DaftarPilihan.Pilihan> pilihan = new ArrayList<>(p.size());
		for (int i = 0; i < p.size(); i++) {
			pilihan.add(new DaftarPilihan.Pilihan(i + 1, p.get(i).teks(LembarKunci.TEKS), kunci, false));
		}
		DaftarPilihan.gambar(l, "pilih", pilihan, x0, y, x1, gy - 4, false, c -> kirim(LembarKunci.op(Racik.OP_PILIH, p.get(c.no() - 1).id())));
		// grade so far
		int mutu = lembar.i(LembarKunci.MUTU, -1);
		l.garis(x0, x1, gy - 3);
		Component mutuT = TemaUi.k("racik.mutu");
		int mw = Math.min(70, font.width(mutuT) + 4);
		l.teks(mutuT, x0, gy + 2, mw, gaya.teksLemah);
		if (mutu < 0) {
			l.samar(TemaUi.k("racik.mutu_belum"), x0 + mw, gy + 2, x1 - x0 - mw);
		} else {
			int bw = TemaUi.bintang(l, Math.min(5, mutu + 1), 5, x0 + mw, gy + 1, 11);
			Component nama = TemaUi.k("racik.mutu." + Math.max(0, Math.min(4, mutu)));
			if (x1 - (x0 + mw + bw + 4) >= 20) {
				l.teks(nama, x0 + mw + bw + 4, gy + 2, x1 - x0 - mw - bw - 4, aksen());
			} else if (l.di(x0, gy, x1, gy + 12)) {
				l.tip(nama);
			}
		}
		Component ulang = TemaUi.k("racik.ulang");
		boolean baca = !tulisBoleh();
		tombol(l, "ulang", x0, k.y1() - 19, x1 - x0, 15, ulang, !baca, baca ? alasanBaca() : null,
				() -> konfirmasi(SegelKonfirmasi.Tingkat.RINGAN, Component.translatable("kultivator.gui43.konteks.yakin", ulang),
						() -> kirim(Racik.OP_ULANG)));
	}
}
