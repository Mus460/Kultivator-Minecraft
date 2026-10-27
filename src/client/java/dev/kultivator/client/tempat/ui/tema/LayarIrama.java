package dev.kultivator.client.tempat.ui.tema;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.KartuBarang;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TabBar;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.Irama;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * {@code tema_irama} (DESIGN 38.T.4.5, 38.T.5.4): the rhythm mini-game shared by the whirling dance of the Malam Tari Putar
 * (oasis, 30 s), the silk loom (Kota Air, 20 s; Kain Xiu Niang three rounds) and the dragon-boat drum (60 s). The client
 * plays the beat track locally and sends the result once ({@code hasil:SKOR}, checked by the server).
 * <p>
 * Notes run along four lanes (up, right, down, left) towards the gold ring; press the arrow key (or W D S A) as a note
 * crosses it: within 3 ticks "Pas!" (full), within 6 "Baik" (60 %), a note that passes unhit is "Luput". The score is the
 * share of the full marks, 0-100. Space starts after a three-beat count-in. The side picture follows the pattern: the
 * dancer whirls a frame on every hit, the shuttle flies across the warp and the cloth grows, the drumstick strikes the
 * drum and the boat surges. A Di Sini page on the single kit (43.8.3): the arrow keys (and W D S A) reach the game
 * before the focus ring ({@link TemaHalaman#keyPressed} asks {@link #tombolLain} first); text is 1.0 / 1.5.
 */
public final class LayarIrama extends TemaHalaman {

	private static final float PAS = 3.0F;
	private static final float BAIK = 6.0F;
	private static final long HITUNG_MS = 1500L;
	private static final float PIKSEL_PER_TICK = 3.2F;

	private enum Fase {
		TUNGGU, HITUNG, MAIN, SELESAI
	}

	private Fase fase = Fase.TUNGGU;
	private long mulai;
	private int[] ketukan = new int[0];
	private int[] arah = new int[0];
	/** Per beat: 0 open, 1 pas, 2 baik, 3 luput. */
	private int[] nilai = new int[0];
	private int durasi;
	private int kombo;
	private int komboMaks;
	private int skor;
	private boolean terkirim;
	private String sidik = "";
	private long nilaiSejak;
	private int nilaiTerakhir;
	private long pukulSejak = -100000L;
	private int frameTari;
	private final long[] laneNyala = new long[4];

	public LayarIrama(int npcId, CompoundTag lembar) {
		super(Irama.LAYAR, npcId, lembar, "layar.irama", Palette.Gaya.KAIN);
	}

	@Override
	protected List<TabBar.Tab> tabs() {
		return List.of();
	}

	@Override
	protected int panelMaks(Tata t) {
		return t == Tata.LEBAR ? 560 : 460;
	}

	@Override
	protected Component petunjuk() {
		return TemaUi.k("irama.kunci");
	}

	@Override
	protected Component judulBawaan() {
		return TemaUi.k("irama.judul." + pola());
	}

	private String pola() {
		String p = lembar.s(Irama.POLA, Irama.POLA_TARI_PUTAR);
		return switch (p) {
			case Irama.POLA_TENUN, Irama.POLA_GENDERANG -> p;
			default -> Irama.POLA_TARI_PUTAR;
		};
	}

	@Override
	protected void lembarBaru(boolean buka) {
		int[] k = lembar.ia(Irama.KETUKAN);
		int[] a = lembar.ia(Irama.ARAH);
		String baru = pola() + ":" + lembar.i(Irama.BABAK, 1) + ":" + Arrays.hashCode(k);
		if (!baru.equals(sidik)) {
			sidik = baru;
			int n = Math.min(k.length, a.length);
			ketukan = Arrays.copyOf(k, n);
			arah = Arrays.copyOf(a, n);
			nilai = new int[n];
			durasi = Math.max(lembar.i(Irama.DURASI, 600), n == 0 ? 0 : ketukan[n - 1] + 20);
			fase = Fase.TUNGGU;
			kombo = 0;
			komboMaks = 0;
			skor = 0;
			terkirim = false;
		}
	}

	private float tickKini(long kini) {
		return (kini - mulai - HITUNG_MS) / 50.0F;
	}

	private void mulaiMain() {
		if (fase == Fase.TUNGGU && ketukan.length > 0 && tulisBoleh()) {
			fase = Fase.HITUNG;
			mulai = Util.getMillis();
			UiSound.open();
		}
	}

	@Override
	protected boolean spasi() {
		mulaiMain();
		return true;
	}

	@Override
	protected boolean tombolLain(KeyEvent event) {
		int d = switch (event.key()) {
			case InputConstants.KEY_UP, InputConstants.KEY_W -> 0;
			case InputConstants.KEY_RIGHT, InputConstants.KEY_D -> 1;
			case InputConstants.KEY_DOWN, InputConstants.KEY_S -> 2;
			case InputConstants.KEY_LEFT, InputConstants.KEY_A -> 3;
			default -> -1;
		};
		if (d < 0) {
			return false;
		}
		tekan(d);
		return true;
	}

	private void tekan(int d) {
		long kini = Util.getMillis();
		laneNyala[d] = kini;
		if (fase != Fase.MAIN) {
			return;
		}
		float t = tickKini(kini);
		int best = -1;
		float bestDt = Float.MAX_VALUE;
		for (int i = 0; i < ketukan.length; i++) {
			if (nilai[i] != 0 || arah[i] != d) {
				continue;
			}
			float dt = Math.abs(ketukan[i] - t);
			if (dt <= BAIK && dt < bestDt) {
				best = i;
				bestDt = dt;
			}
			if (ketukan[i] - t > BAIK) {
				break;
			}
		}
		if (best < 0) {
			kombo = 0;
			return;
		}
		nilai[best] = bestDt <= PAS ? 1 : 2;
		kombo++;
		komboMaks = Math.max(komboMaks, kombo);
		nilaiTerakhir = nilai[best];
		nilaiSejak = kini;
		pukulSejak = kini;
		frameTari = (frameTari + 1) % 4;
		UiSound.hover();
	}

	private void majukan(long kini) {
		if (fase == Fase.HITUNG && kini - mulai >= HITUNG_MS) {
			fase = Fase.MAIN;
		}
		if (fase != Fase.MAIN) {
			return;
		}
		float t = tickKini(kini);
		for (int i = 0; i < ketukan.length; i++) {
			if (nilai[i] == 0 && t - ketukan[i] > BAIK) {
				nilai[i] = 3;
				kombo = 0;
				nilaiTerakhir = 3;
				nilaiSejak = kini;
			}
		}
		if (t > durasi) {
			fase = Fase.SELESAI;
			skor = hitungSkor();
			if (!terkirim) {
				terkirim = true;
				kirim(LembarKunci.op(Irama.OP_HASIL, skor));
			}
		}
	}

	private int hitungSkor() {
		if (nilai.length == 0) {
			return 0;
		}
		float sum = 0.0F;
		for (int v : nilai) {
			sum += v == 1 ? 1.0F : v == 2 ? 0.6F : 0.0F;
		}
		return Math.round(100.0F * sum / nilai.length);
	}

	@Override
	protected void lukisTema(Lukis l, int x0, int y0, int x1, int y1) {
		majukan(l.kini);
		int babak = lembar.i(Irama.BABAK, 1);
		int babakTotal = lembar.i(Irama.BABAK_TOTAL, 1);
		int lulus = lembar.i(Irama.LULUS, 70);
		// header line: round, pass mark, live score
		int y = y0;
		int kini = fase == Fase.SELESAI ? skor : hitungSkorSementara();
		Component skorT = TemaUi.k("irama.skor", kini);
		int sw = font.width(skorT);
		l.kanan(skorT, x1, y, kini >= lulus ? Palette.GOLD_LIGHT : gaya.teks);
		int hx = x0;
		if (babakTotal > 1) {
			Component b = TemaUi.k("irama.babak", babak, babakTotal);
			int bw = Math.min((x1 - x0 - sw) / 2, font.width(b) + 8);
			l.teks(b, hx, y, bw, gaya.teksLemah);
			hx += bw;
		}
		l.teks(TemaUi.k("irama.lulus", lulus), hx, y, x1 - hx - sw - 6, gaya.teksLemah);
		y += 12;
		boolean lebar = tata != Tata.SEMPIT;
		int gambarW = lebar ? Math.min(120, (x1 - x0) / 3) : 0;
		int jx1 = x1 - (gambarW > 0 ? gambarW + 6 : 0);
		jalur(l, x0, y, jx1, y1 - 16);
		if (gambarW > 0) {
			l.sumur(x1 - gambarW, y, x1, y1 - 16);
			ilustrasi(l, x1 - gambarW, y, x1, y1 - 16);
		}
		// time bar
		float frac = fase == Fase.MAIN ? Math.max(0.0F, tickKini(l.kini)) / Math.max(1, durasi) : fase == Fase.SELESAI ? 1.0F : 0.0F;
		TemaUi.meter(l, -1, x0, y1 - 11, x1, frac, 0xFFD4A637, -1.0F);
		if (fase == Fase.TUNGGU) {
			lapis(l, x0, y, jx1, y1 - 16, TemaUi.k(ketukan.length == 0 ? "irama.tanpa_lagu" : "irama.mulai"));
			if (ketukan.length > 0) {
				int bw = 90;
				TalismanButton.gambar(l, "mulai", (x0 + jx1 - bw) / 2, (y + y1 - 16) / 2 + 10, bw, 15, TemaUi.k("irama.tombol_mulai"), tulisBoleh(),
						alasanBaca(), this::mulaiMain);
			}
		} else if (fase == Fase.HITUNG) {
			long sisa = HITUNG_MS - (l.kini - mulai);
			int n = (int) Math.max(1L, (sisa + 499L) / 500L);
			// the count-in pulses by its ink, not its size (43.0.5: 1.5 is the largest text)
			int alfa = Math.round(160 + 95 * ((sisa % 500L) / 500.0F));
			Brush.centered(l.g, font, Brush.bold(Component.literal(Integer.toString(n))), (x0 + jx1) / 2.0F, (y + y1) / 2.0F - 10,
					Palette.argb(alfa, Palette.GOLD_LIGHT), 1.5F, true);
		} else if (fase == Fase.SELESAI) {
			hasil(l, x0, y, jx1, y1 - 16, lulus);
		}
	}

	private int hitungSkorSementara() {
		float sum = 0.0F;
		for (int v : nilai) {
			sum += v == 1 ? 1.0F : v == 2 ? 0.6F : 0.0F;
		}
		return nilai.length == 0 ? 0 : Math.round(100.0F * sum / nilai.length);
	}

	private void jalur(Lukis l, int x0, int y0, int x1, int y1) {
		l.sumur(x0, y0, x1, y1);
		l.g.fillGradient(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xF02A0E22, 0xF0180818);
		int h = (y1 - y0 - 6) / 4;
		int hitX = x0 + 26;
		long kini = l.kini;
		float t = fase == Fase.MAIN ? tickKini(kini) : fase == Fase.HITUNG ? -(HITUNG_MS - (kini - mulai)) / 50.0F : fase == Fase.SELESAI ? durasi : 0.0F;
		for (int d = 0; d < 4; d++) {
			int ly = y0 + 3 + d * h;
			boolean nyala = kini - laneNyala[d] < 120L;
			l.g.fill(x0 + 2, ly, x1 - 2, ly + h - 1, Palette.argb(nyala ? 0x40 : 0x18, 0xFFFFFF));
			l.g.fill(x0 + 2, ly + h - 1, x1 - 2, ly + h, Palette.argb(0x40, 0x000000));
			// the gold ring at the hit line
			int rs = Math.min(h, 20);
			TemaUi.iramaBesar(l.g, 0, hitX - rs / 2, ly + (h - rs) / 2, rs, nyala ? 0xFFFFFFFF : 0xB0FFFFFF);
			TemaUi.nada(l.g, d, false, hitX - 6, ly + (h - 12) / 2, 12, 0x50FFFFFF);
		}
		l.klip(x0 + 2, y0 + 1, x1 - 2, y1 - 1);
		for (int i = 0; i < ketukan.length; i++) {
			float dx = (ketukan[i] - t) * PIKSEL_PER_TICK;
			int nx = Math.round(hitX + dx);
			if (nx > x1 + 10) {
				break;
			}
			if (nx < x0 - 16) {
				continue;
			}
			int d = Math.floorMod(arah[i], 4);
			int ly = y0 + 3 + d * h;
			int size = Math.min(h - 2, 16);
			int v = nilai[i];
			if (v == 1 || v == 2) {
				float age = (kini - nilaiSejak) / 200.0F;
				if (age > 1.0F && dx < -6) {
					continue;
				}
				TemaUi.nada(l.g, d, true, hitX - size / 2, ly + (h - size) / 2, size, Palette.argb(Math.round(255 * Math.max(0.0F, 1.0F - age)), 0xFFFFFF));
				continue;
			}
			int a = v == 3 ? 0x60 : 0xFF;
			TemaUi.nada(l.g, d, false, nx - size / 2, ly + (h - size) / 2, size, Palette.argb(a, 0xFFFFFF));
		}
		l.lepasKlip();
		// judgement popup and combo
		if (kini - nilaiSejak < 600L && nilaiTerakhir != 0) {
			float age = (kini - nilaiSejak) / 600.0F;
			String kunci = nilaiTerakhir == 1 ? "irama.pas" : nilaiTerakhir == 2 ? "irama.baik" : "irama.luput";
			int warna = nilaiTerakhir == 1 ? Palette.GOLD_LIGHT : nilaiTerakhir == 2 ? 0xFF9AE0B0 : 0xFFFF8A7A;
			Brush.centered(l.g, font, Brush.bold(TemaUi.k(kunci)), hitX + 40, y0 + 4 - age * 6, Palette.argb(Math.round(255 * (1.0F - age)), warna & 0xFFFFFF),
					1.0F, true);
		}
		if (kombo >= 3) {
			Component k = TemaUi.k("irama.kombo", kombo);
			Brush.text(l.g, font, k, x1 - 4 - font.width(k), y1 - 11, Palette.GOLD_LIGHT, 1.0F, true);
		}
	}

	/** The side picture of the pattern, driven by the hits. */
	private void ilustrasi(Lukis l, int x0, int y0, int x1, int y1) {
		l.klip(x0 + 1, y0 + 1, x1 - 1, y1 - 1);
		long kini = l.kini;
		float pukul = Math.max(0.0F, 1.0F - (kini - pukulSejak) / 180.0F);
		int cx = (x0 + x1) / 2;
		int h = y1 - y0;
		float maju = nilai.length == 0 ? 0.0F : jumlahKena() / (float) nilai.length;
		switch (pola()) {
			case Irama.POLA_TENUN -> {
				int s = Math.min(x1 - x0 - 8, h - 30);
				int lx = cx - s / 2;
				int ly = y0 + 8;
				TemaUi.iramaBesar(l.g, 2, lx, ly, s, 0xFFFFFFFF);
				// the cloth grows from the bottom with the progress
				int kain = Math.round((s * 0.6F) * maju);
				l.g.fill(lx + s / 6, ly + s - s / 8 - kain, lx + s - s / 6, ly + s - s / 8, Palette.argb(0xE0, 0xC8507A));
				for (int yy = ly + s - s / 8 - kain; yy < ly + s - s / 8; yy += 3) {
					l.g.fill(lx + s / 6, yy, lx + s - s / 6, yy + 1, Palette.argb(0x60, 0xF7DC7E));
				}
				float lintas = (float) Math.sin((kini - pukulSejak) / 180.0D);
				int sx = Math.round(cx - s / 3.0F + (frameTari % 2 == 0 ? 1 : -1) * lintas * s / 3.0F);
				TemaUi.iramaKecil(l.g, 0, sx - 8, ly + s / 3, 16, 0xFFFFFFFF);
			}
			case Irama.POLA_GENDERANG -> {
				int s = Math.min(x1 - x0 - 16, h / 2);
				int dy = y0 + 10;
				float skala = 1.0F + 0.08F * pukul;
				l.g.pose().pushMatrix();
				l.g.pose().translate(cx, dy + s / 2.0F);
				l.g.pose().scale(skala, skala);
				TemaUi.iramaBesar(l.g, 1, -s / 2, -s / 2, s, 0xFFFFFFFF);
				l.g.pose().popMatrix();
				l.g.pose().pushMatrix();
				l.g.pose().translate(cx + s / 2.0F, dy + 2);
				l.g.pose().rotate(-0.9F * pukul);
				TemaUi.iramaKecil(l.g, 1, -4, -12, 16, 0xFFFFFFFF);
				l.g.pose().popMatrix();
				// the dragon boat surges with the beat
				int by = y1 - 22;
				float laju = (kini / 30.0F) % 64.0F;
				for (int wx = x0 - 64 + Math.round(laju); wx < x1; wx += 16) {
					l.g.fill(wx, by + 10, wx + 8, by + 11, Palette.argb(0x90, 0x9AD0EE));
				}
				int bx = x0 + 6 + Math.round((x1 - x0 - 50) * maju);
				l.g.fill(bx, by + 4, bx + 40, by + 8, 0xFFB03020);
				l.g.fill(bx + 36, by, bx + 44, by + 5, 0xFFD4A637);
				for (int r = 0; r < 4; r++) {
					int rx = bx + 6 + r * 8;
					int ay = Math.round(pukul * 3);
					l.g.fill(rx, by + 1, rx + 2, by + 4, 0xFF3A2A1E);
					l.g.fill(rx - 2 - ay, by + 6, rx, by + 11, 0xFF8A6A3A);
				}
			}
			default -> {
				int w = Math.min(x1 - x0 - 10, (h - 10) * 2 / 3);
				int hh = w * 3 / 2;
				int frame = fase == Fase.MAIN && kombo >= 5 ? (int) ((kini / 120L) % 4L) : frameTari;
				TemaUi.cahaya(l, cx, y0 + 6 + hh / 2, w, Palette.argb(Math.round(60 + 120 * pukul), 0xFFD27A));
				TemaUi.penari(l.g, frame, cx - w / 2, y0 + 6, w, hh, 0xFFFFFFFF);
				for (int k = 0; k < 6; k++) {
					double a = kini / 500.0D + k * Math.PI / 3.0D;
					int px = Math.round(cx + (float) Math.cos(a) * w * 0.7F);
					int py = Math.round(y0 + 6 + hh * 0.75F + (float) Math.sin(a) * 4.0F);
					l.g.fill(px, py, px + 2, py + 2, Palette.argb(0xA0, 0xF7DC7E));
				}
			}
		}
		l.lepasKlip();
	}

	private int jumlahKena() {
		int n = 0;
		for (int v : nilai) {
			n += v == 1 || v == 2 ? 1 : 0;
		}
		return n;
	}

	private void lapis(Lukis l, int x0, int y0, int x1, int y1, Component teks) {
		l.g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0x90000000);
		float w = x1 - x0 - 24;
		float th = Brush.wrapHeight(font, teks, w, 1.0F);
		Brush.wrap(l.g, font, teks, x0 + 12, (y0 + y1) / 2.0F - th - 4, w, 0xFFF4E8D0, 1.0F, false);
	}

	private void hasil(Lukis l, int x0, int y0, int x1, int y1, int lulus) {
		l.g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xA0000000);
		float cx = (x0 + x1) / 2.0F;
		float cy = (y0 + y1) / 2.0F;
		Brush.centered(l.g, font, Brush.bold(TemaUi.k("irama.skor_akhir", skor)), cx, cy - 26, 0xFFF4E8D0, 1.5F, true);
		int pas = 0;
		int baik = 0;
		int luput = 0;
		for (int v : nilai) {
			pas += v == 1 ? 1 : 0;
			baik += v == 2 ? 1 : 0;
			luput += v == 3 ? 1 : 0;
		}
		l.tengah(TemaUi.k("irama.rincian", pas, baik, luput, komboMaks), cx, cy - 8, x1 - x0 - 8, 0xFFD8C8B8);
		float t = Math.min(1.0F, (l.kini - mulai - HITUNG_MS - durasi * 50L) / 400.0F);
		KartuBarang.cap(l, TemaUi.k(skor >= lulus ? "irama.cap_lulus" : "irama.cap_gagal"), cx, cy + 16, 70, Math.max(0.05F, t));
		l.tengah(TemaUi.k("irama.dikirim"), cx, y1 - 12, x1 - x0 - 8, 0xFFB8A890);

	}
}
