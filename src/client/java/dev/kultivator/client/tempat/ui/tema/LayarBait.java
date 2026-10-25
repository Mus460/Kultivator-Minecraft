package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarPilihan;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import java.util.ArrayList;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.Bait;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * {@code tema_bait} (DESIGN 38.T.5.4): Pasangan Bait with the old poet Wen Shi in the Taman Sang Sastrawan. Two hanging
 * scrolls: the first line brushed on the left one, the right one waiting for its answer. Three candidate second lines lie
 * below as slips of paper (keys 1-3). A fitting answer is brushed onto the right scroll for a moment and a plum blossom
 * opens on the branch (three in a row win the day's reward); a poor one leaves an ink blot and the branch bare again.
 * When today's reward is already given the game stays playable for its own sake (a ribbon says so). The slips are the
 * kit's {@link DaftarPilihan} (G59): ↑↓ Enter, a click, or the number keys under Di Sini (43.17 K3).
 */
public final class LayarBait extends TemaHalaman {

	private int benarLalu = -1;
	private Component baitLalu = Component.empty();
	private Component jawabLalu = Component.empty();
	private Component jawabKirim = Component.empty();
	private Component baitKirim = Component.empty();
	private long hasilSejak = -1L;
	private boolean hasilBenar;

	public LayarBait(int npcId, CompoundTag lembar) {
		super(Bait.LAYAR, npcId, lembar, "layar.bait", TempatWarna.Gaya.KERTAS);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of();
	}

	@Override
	protected int panelMaks(Tata t) {
		return t == Tata.LEBAR ? 520 : 440;
	}

	@Override
	protected Component petunjuk() {
		return TemaUi.k("bait.kunci");
	}

	@Override
	protected void lembarBaru(boolean buka) {
		int benar = lembar.i(Bait.BENAR, 0);
		if (!buka && benarLalu >= 0 && !jawabKirim.getString().isEmpty()) {
			int maks = lembar.i(LembarKunci.MAKS, 3);
			// a full streak may come back reset to 0 together with today's reward flag
			hasilBenar = benar > benarLalu || (benar == benarLalu && benar >= maks) || (benar == 0 && benarLalu == maks - 1 && lembar.b(Bait.HARI_INI));
			hasilSejak = Util.getMillis();
			baitLalu = baitKirim;
			jawabLalu = jawabKirim;
			jawabKirim = Component.empty();
			if (hasilBenar) {
				UiSound.ask();
			} else {
				UiSound.denied();
			}
		}
		benarLalu = benar;
	}

	private void pilih(Lembar e) {
		if (!tulisBoleh()) {
			return;
		}
		baitKirim = lembar.teks(Bait.BAIT);
		jawabKirim = e.teks(LembarKunci.TEKS);
		kirim(LembarKunci.op(Bait.OP_PILIH, e.id()));
	}

	@Override
	protected void lukisTema(Lukis l, int x0, int y0, int x1, int y1) {
		int y = y0;
		if (lembar.b(Bait.HARI_INI)) {
			int w = Math.min(x1 - x0, 260);
			int px = (x0 + x1 - w) / 2;
			Glyphs.Atlas.pita(l.g, false, px, y, w, 12, 0xFFFFFFFF);
			l.tengah(TemaUi.k("bait.hari_ini"), (x0 + x1) / 2.0F, y + 2, w - 16, 0xFF3A3A3A);
			y += 15;
		}
		long sejak = hasilSejak >= 0L ? l.kini - hasilSejak : Long.MAX_VALUE;
		boolean tampilHasil = sejak < 1800L;
		List<Lembar> p = lembar.daftar(Bait.PILIHAN);
		int pilihH = DaftarPilihan.tinggi(Math.min(3, p.size()), 1, 15) + 2;
		int gulungH = Math.max(46, y1 - y - pilihH - 15);
		// the two scrolls and the plum branch between them
		int mid = (x0 + x1) / 2;
		int gw = Math.min(110, (x1 - x0) / 2 - 26);
		Component kiri = tampilHasil ? baitLalu : lembar.teks(Bait.BAIT);
		gulungan(l, mid - 14 - gw, y, mid - 14, y + gulungH, kiri, false, 0.0F);
		float tinta = tampilHasil ? Math.min(1.0F, sejak / 500.0F) : 0.0F;
		gulungan(l, mid + 14, y, mid + 14 + gw, y + gulungH, tampilHasil && hasilBenar ? jawabLalu : Component.empty(), true, tinta);
		if (tampilHasil && !hasilBenar) {
			nodaTinta(l, mid + 14 + gw / 2, y + gulungH / 2, Math.min(1.0F, sejak / 300.0F));
		}
		ranting(l, mid, y + 6, gulungH - 12, sejak);
		y += gulungH + 4;
		// streak
		int maks = Math.max(1, lembar.i(LembarKunci.MAKS, 3));
		int benar = lembar.i(Bait.BENAR, 0);
		l.tengah(TemaUi.k("bait.beruntun", benar, maks), mid, y, x1 - x0, gaya.teksLemah);
		y += 11;
		// the three answer slips (G59)
		Component kunci = tulisBoleh() ? null : alasanBaca();
		List<DaftarPilihan.Pilihan> pilihan = new ArrayList<>(3);
		for (int i = 0; i < p.size() && i < 3; i++) {
			pilihan.add(new DaftarPilihan.Pilihan(i + 1, p.get(i).teks(LembarKunci.TEKS), kunci, false));
		}
		DaftarPilihan.gambar(l, "jawab", pilihan, x0 + 6, y, x1 - 6, y1, false, c -> pilih(p.get(c.no() - 1)));
	}

	/** A hanging scroll: rods, silk border, paper, and the line brushed top to bottom in wrapped rows. */
	private void gulungan(Lukis l, int x0, int y0, int x1, int y1, Component teks, boolean jawab, float tinta) {
		l.g.fill(x0 - 3, y0, x1 + 3, y0 + 4, 0xFF5A3A20);
		l.g.fill(x0 - 4, y0 + 1, x0 - 2, y0 + 3, 0xFFD4A637);
		l.g.fill(x1 + 2, y0 + 1, x1 + 4, y0 + 3, 0xFFD4A637);
		l.g.fill(x0, y0 + 4, x1, y1 - 4, 0xFF6A8A7A);
		l.g.fill(x0 + 3, y0 + 8, x1 - 3, y1 - 8, 0xFFF4ECD4);
		l.g.fillGradient(x0 + 3, y0 + 8, x1 - 3, y0 + 18, 0x18000000, 0x00000000);
		l.g.fill(x0 - 3, y1 - 4, x1 + 3, y1, 0xFF5A3A20);
		int cx = (x0 + x1) / 2;
		if (teks.getString().isEmpty()) {
			if (jawab) {
				l.tengah(TemaUi.k("bait.menunggu"), cx, (y0 + y1) / 2.0F - 4, x1 - x0 - 10, 0x60403020);
			}
			return;
		}
		int alfa = jawab ? Math.round(255 * tinta) : 255;
		if (alfa <= 8) {
			return;
		}
		l.bungkus(teks, x0 + 7, y0 + 11, x1 - x0 - 14, Palette.argb(alfa, 0x1B1A16), y1 - y0 - 34);
		capPenyair(l, x1 - 14, y1 - 22, alfa);
	}

	/** An ink blot spreading on the answer scroll. */
	private static void nodaTinta(Lukis l, int cx, int cy, float t) {
		float r = 4.0F + 8.0F * t;
		Brush.disc(l.g, cx, cy, r, Palette.argb(Math.round(0xB0 * t), 0x1B1A16));
		for (int i = 0; i < 6; i++) {
			double a = i * 1.05D + 0.3D;
			Brush.dot(l.g, cx + (float) Math.cos(a) * r * 1.4F, cy + (float) Math.sin(a) * r * 1.3F, 1.5F * t, Palette.argb(Math.round(0x90 * t),
					0x1B1A16));
		}
	}

	/** The plum branch: one blossom per fitting answer in the streak; a new one opens after a good answer. */
	private void ranting(Lukis l, int cx, int y0, int h, long sejak) {
		Brush.line(l.g, cx, y0 + h, cx - 3, y0 + h * 0.55F, 2.0F, 0xFF3A2414);
		Brush.line(l.g, cx - 3, y0 + h * 0.55F, cx + 2, y0 + h * 0.2F, 1.5F, 0xFF3A2414);
		Brush.line(l.g, cx - 2, y0 + h * 0.4F, cx + 6, y0 + h * 0.32F, 1.0F, 0xFF3A2414);
		int benar = lembar.i(Bait.BENAR, 0);
		float[][] titik = {{cx + 2, y0 + h * 0.2F}, {cx + 6, y0 + h * 0.32F}, {cx - 3, y0 + h * 0.55F}};
		for (int i = 0; i < 3; i++) {
			boolean mekar = i < benar;
			float s = mekar ? i == benar - 1 && hasilBenar && sejak < 800L ? Math.min(1.0F, sejak / 500.0F) : 1.0F : 0.0F;
			float bx = titik[i][0];
			float by = titik[i][1];
			if (s <= 0.0F) {
				Brush.dot(l.g, bx, by, 1.2F, 0xFF6A4A30);
				continue;
			}
			for (int p = 0; p < 5; p++) {
				double a = p * Math.PI * 2.0D / 5.0D - Math.PI / 2.0D;
				Brush.disc(l.g, bx + (float) Math.cos(a) * 2.6F * s, by + (float) Math.sin(a) * 2.6F * s, 2.0F * s, 0xFFF0A0B4);
			}
			Brush.dot(l.g, bx, by, 1.0F * s, 0xFFD4A637);
		}
	}

	/** The poet's red seal at the scroll's foot. */
	private static void capPenyair(Lukis l, int x, int y, int alfa) {
		l.g.fill(x, y, x + 9, y + 9, Palette.argb(Math.round(alfa * 0.85F), 0xB3281D));
		l.g.fill(x + 2, y + 2, x + 7, y + 3, Palette.argb(alfa, 0xF4ECD4));
		l.g.fill(x + 4, y + 3, x + 5, y + 7, Palette.argb(alfa, 0xF4ECD4));
		l.g.fill(x + 2, y + 6, x + 7, y + 7, Palette.argb(alfa, 0xF4ECD4));
	}
}
