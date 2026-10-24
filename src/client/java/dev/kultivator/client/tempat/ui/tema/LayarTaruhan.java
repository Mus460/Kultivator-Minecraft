package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.tempat.ui.kit.CincinWaktu;
import dev.kultivator.client.tempat.ui.kit.LabelHarga;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.Potret;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.Taruhan;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * {@code tema_taruhan} (DESIGN 38.T.7.5): Tu Gang's betting table at the Arena Darah and the Tangga Darah ladder.
 * <ul>
 * <li><b>Taruhan</b>: the next bout. Two fighter cards (live portrait in the blood-iron frame, name and epithet, tier,
 * temper, a chance bar and the odds chip) face each other across the "VS" seal under the countdown. Click a card to
 * back that side, set the stake with jade coins up to tonight's limit and "Pasang"; the bet already placed lies
 * on the plate with its possible payout. Six bets a night.</li>
 * <li><b>Tangga</b>: the nine rungs of the ladder with the player's rung lit, the next opponent's card, the entry fee,
 * the prize and "Tantang" (five a day).</li>
 * </ul>
 */
public final class LayarTaruhan extends LayarTemaDasar {

	private static final int[] LANGKAH = {1, 5, 10};

	private String sisi = Taruhan.KIRI;
	private long taruh = 1L;
	private int langkahPilih = 1;

	public LayarTaruhan(int npcId, CompoundTag lembar) {
		super(Taruhan.LAYAR, npcId, lembar, "layar.taruhan", TempatWarna.Gaya.BATU);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of(tabTema(Taruhan.TAB_TARUHAN, "taruhan.tab.taruhan", LembarKunci.Ikon.JUDI_BATU),
				tabTema(Taruhan.TAB_TANGGA, "taruhan.tab.tangga", LembarKunci.Ikon.ARENA));
	}

	@Override
	protected boolean tabAda(String id) {
		return !Taruhan.TAB_TANGGA.equals(id) || lembar.ada(Taruhan.TANGGA);
	}

	@Override
	protected Component petunjuk() {
		return Taruhan.TAB_TANGGA.equals(tab) ? null : TemaUi.k("taruhan.kunci");
	}

	@Override
	protected void lembarBaru(boolean buka) {
		taruh = Math.max(1L, Math.min(taruh, maks()));
		Lembar pasang = lembar.sub(Taruhan.TARUHAN).sub(Taruhan.PASANG);
		if (pasang.ada(Taruhan.SISI)) {
			sisi = pasang.s(Taruhan.SISI, Taruhan.KIRI);
		}
	}

	private long maks() {
		return Math.max(1L, lembar.sub(Taruhan.TARUHAN).l(LembarKunci.MAKS, 20L));
	}

	@Override
	protected boolean angka(int n) {
		if (!Taruhan.TAB_TANGGA.equals(tab) && n >= 1 && n <= 3) {
			langkahPilih = n - 1;
			UiSound.tab();
			return true;
		}
		return false;
	}

	@Override
	protected boolean spasi() {
		if (Taruhan.TAB_TANGGA.equals(tab)) {
			Lembar t = lembar.sub(Taruhan.TANGGA);
			if (t.b(LembarKunci.BISA) && tulisBoleh()) {
				kirim(Taruhan.OP_TANTANG);
			}
			return true;
		}
		if (bisaPasang()) {
			kirim(LembarKunci.op(Taruhan.OP_PASANG, sisi, taruh));
		}
		return true;
	}

	private boolean bisaPasang() {
		Lembar t = lembar.sub(Taruhan.TARUHAN);
		return t.i(Taruhan.SISA, 6) > 0 && !t.sub(Taruhan.PASANG).ada(Taruhan.SISI) && mampu(taruh) && tulisBoleh()
				&& lembar.sub(Taruhan.LAGA).ada(Taruhan.KIRI);
	}

	private void ubah(int arah) {
		long step = LANGKAH[langkahPilih] * (Minecraft.getInstance().hasShiftDown() ? 10L : 1L);
		long baru = Math.max(1L, Math.min(maks(), taruh + arah * step));
		if (baru != taruh) {
			taruh = baru;
			UiSound.toggle(arah > 0);
		} else {
			UiSound.denied();
		}
	}

	@Override
	protected void gambar(Lukis l, int x0, int y0, int x1, int y1) {
		if (Taruhan.TAB_TANGGA.equals(tab)) {
			tangga(l, x0, y0, x1, y1);
		} else {
			laga(l, x0, y0, x1, y1);
		}
	}

	// ------------------------------------------------------------------------------------------------ the bout

	private void laga(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar laga = lembar.sub(Taruhan.LAGA);
		if (!laga.ada(Taruhan.KIRI)) {
			kosong(l, TemaUi.k("taruhan.belum_ada"), x0, y0, x1, y1);
			return;
		}
		int taruhH = 52;
		int ky1 = y1 - taruhH;
		int mid = (x0 + x1) / 2;
		int kw = Math.min(110, (x1 - x0) / 2 - 22);
		int kh = Math.min(ky1 - y0 - 2, Math.round(kw * 1.5F));
		petarung(l, laga.sub(Taruhan.KIRI), Taruhan.KIRI, mid - 20 - kw, y0, kw, kh);
		petarung(l, laga.sub(Taruhan.KANAN), Taruhan.KANAN, mid + 20, y0, kw, kh);
		// VS seal and countdown between the cards
		float pulse = 0.85F + 0.15F * l.denyut();
		int vs = Math.round(30 * pulse);
		TemaUi.segelVs(l.g, mid - vs / 2, y0 + kh / 2 - vs / 2, vs, 0xFFFFFFFF);
		if (laga.ada(LembarKunci.SISA_TICK)) {
			CincinWaktu.gambar(l, mid, y0 + 12, 10.0F, laga.l(LembarKunci.SISA_TICK, 0L), 2400L, diterima, CincinWaktu.DALAM);
		}
		taruhan(l, x0, ky1 + 3, x1, y1);
	}

	private void petarung(Lukis l, Lembar p, String id, int x, int y, int w, int h) {
		boolean dipilih = id.equals(sisi);
		boolean over = l.di(x, y, x + w, y + h);
		if (dipilih) {
			TemaUi.cahaya(l, x + w / 2, y + h / 2, Math.round(w * 0.75F), Palette.argb(0x70, TemaUi.DARAH & 0xFFFFFF));
		}
		TemaUi.kartuPetarung(l.g, x, y, w, h);
		int in = Math.max(4, Math.round(w * 6.0F / 64.0F));
		int ps = w - in * 2;
		int py = y + Math.round(h * 6.0F / 96.0F);
		Potret.gambar(l, x + in, py, ps, p.i(LembarKunci.POTRET, -1), LembarKunci.Ikon.ARENA, false, 0.0F);
		int ty = py + ps + 3;
		int tx0 = x + in;
		int tx1 = x + w - in;
		Brush.fitCentered(l.g, font, Brush.bold(p.teks(LembarKunci.NAMA)), (tx0 + tx1) / 2.0F, ty, tx1 - tx0, 0xFFFFF0E0, 0.85F, 0.6F, true);
		ty += 9;
		Brush.fitCentered(l.g, font, p.teks(Taruhan.JULUKAN), (tx0 + tx1) / 2.0F, ty, tx1 - tx0, 0xFFE0A0A0, 0.65F, 0.5F, false);
		ty += 8;
		int tier = Math.max(0, Math.min(9, p.i(Taruhan.TIER, 1)));
		if (ty + 8 < y + h - 18) {
			TemaUi.bintang(l, Math.min(5, (tier + 1) / 2), 5, (tx0 + tx1) / 2 - 22, ty, 9);
			ty += 9;
		}
		if (ty + 8 < y + h - 18) {
			Brush.fitCentered(l.g, font, p.teks(Taruhan.WATAK), (tx0 + tx1) / 2.0F, ty, tx1 - tx0, 0xFFC8B8B0, 0.6F, 0.5F, false);
		}
		// chance bar and odds chip at the card foot
		float peluang = Math.max(0.0F, Math.min(1.0F, p.f(LembarKunci.PELUANG, 0.5F)));
		int by = y + h - 15;
		TemaUi.meter(l, -1, tx0, by, tx1, peluang, TemaUi.DARAH_TERANG, 0.5F);
		int cw = 30;
		TemaUi.chipOdds(l.g, (x + x + w) / 2 - cw / 2, y + h - 6, cw, 13);
		Brush.centered(l.g, font, Component.literal("×" + String.format(Locale.ROOT, "%.2f", p.f(Taruhan.ODDS, 2.0F))), (x + x + w) / 2.0F,
				y + h - 3, 0xFF2A1808, 0.65F, false);
		if (dipilih) {
			Brush.frame(l.g, x - 1, y - 1, x + w + 1, y + h + 1, TempatWarna.EMAS_TERANG);
		}
		l.zona("sisi_" + id, x, y, x + w, y + h, () -> {
			sisi = id;
			UiSound.toggle(true);
		}, true, null, true);
		l.fokusCincin("sisi_" + id, x, y, x + w, y + h);
		if (over) {
			l.tip(List.of(Brush.bold(p.teks(LembarKunci.NAMA)), TemaUi.k("taruhan.peluang", Math.round(peluang * 100.0F)), TemaUi.k("taruhan.tier",
					tier)));
		}
	}

	private void taruhan(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar t = lembar.sub(Taruhan.TARUHAN);
		Lembar pasang = t.sub(Taruhan.PASANG);
		PanelTempat.kartu(l.g, gaya, x0, y0, x1, y1, 0.0F, false);
		int ix0 = x0 + 5;
		int ix1 = x1 - 5;
		int y = y0 + 4;
		int sisa = t.i(Taruhan.SISA, 6);
		l.keterangan(TemaUi.k("taruhan.sisa", sisa), ix0, y, 100, sisa > 0 ? gaya.teksLemah : TempatWarna.BAHAYA);
		l.keterangan(TemaUi.k("taruhan.maks", TeksTempat.angka(maks())), ix0 + 102, y, ix1 - ix0 - 102, gaya.teksSamar);
		y += 11;
		if (pasang.ada(Taruhan.SISI)) {
			// the bet already on the plate
			int pw = Math.min(160, ix1 - ix0);
			TemaUi.pelatTaruhan(l.g, ix0, y, pw, 18);
			String s = pasang.s(Taruhan.SISI, Taruhan.KIRI);
			Lembar p = lembar.sub(Taruhan.LAGA).sub(s);
			long nb = pasang.l(LembarKunci.NB, 0L);
			l.teks(TemaUi.k("taruhan.dipasang", TeksTempat.angka(nb), p.teks(LembarKunci.NAMA)), ix0 + 6, y + 5, pw - 12, 0xFFFFF0E0, 0.75F);
			long bayar = Math.round(nb * p.f(Taruhan.ODDS, 2.0F));
			l.keterangan(TemaUi.k("taruhan.bila_menang"), ix0 + pw + 6, y + 1, ix1 - ix0 - pw - 6, gaya.teksLemah);
			LabelHarga.gambar(l, bayar, -1L, ix0 + pw + 6, y + 10, false, 0.8F, true);
			return;
		}
		// stake: steps, coins, amount
		for (int i = 0; i < LANGKAH.length; i++) {
			int cx = ix0 + i * 26;
			boolean on = i == langkahPilih;
			final int idx = i;
			l.g.fill(cx, y, cx + 23, y + 11, on ? Palette.argb(0xD0, Palette.JADE & 0xFFFFFF) : Palette.argb(0x30, 0x000000));
			Brush.centered(l.g, font, Component.literal("±" + LANGKAH[i]), cx + 11.5F, y + 2, on ? 0xFFF4FFF8 : gaya.teksLemah, 0.7F, false);
			l.zona("langkah" + i, cx, y, cx + 23, y + 11, () -> langkahPilih = idx, true, null, true);
			l.fokusCincin("langkah" + i, cx, y, cx + 23, y + 11);
		}
		int mx = ix0 + 84;
		koin(l, "kurang", mx, y - 1, "−", taruh > 1L, () -> ubah(-1));
		PanelTempat.sumur(l.g, gaya, mx + 16, y - 2, mx + 74, y + 13);
		Brush.centered(l.g, font, Brush.bold(TeksTempat.nb(taruh, false)), mx + 45, y + 2, gaya.teksSumur(), 0.9F, false);
		Lukis.Zona z = l.zona("taruh", mx + 16, y - 2, mx + 74, y + 13, null, true, null, true);
		if (z != null) {
			z.geser = this::ubah;
		}
		l.fokusCincin("taruh", mx + 16, y - 2, mx + 74, y + 13);
		koin(l, "tambah", mx + 76, y - 1, "+", taruh < maks(), () -> ubah(1));
		Lembar p = lembar.sub(Taruhan.LAGA).sub(sisi);
		long bayar = Math.round(taruh * p.f(Taruhan.ODDS, 2.0F));
		int bx = mx + 96;
		if (bx + 60 < ix1) {
			l.keterangan(TemaUi.k("taruhan.bila_menang"), bx, y - 1, ix1 - bx, gaya.teksSamar);
			LabelHarga.gambar(l, bayar, -1L, bx, y + 7, false, 0.75F, true);
		}
		y += 17;
		boolean bisa = bisaPasang();
		Component alasan = sisa <= 0 ? TemaUi.k("taruhan.batas") : !mampu(taruh) ? kurang(taruh) : null;
		tombolOp(l, "pasang", ix0, y1 - 18, ix1 - ix0, 14, TemaUi.k("taruhan.pasang", TeksTempat.angka(taruh), p.teks(LembarKunci.NAMA)), bisa, alasan,
				LembarKunci.op(Taruhan.OP_PASANG, sisi, taruh));
	}

	private void koin(Lukis l, String kunci, int x, int y, String tanda, boolean on, Runnable aksi) {
		int d = 14;
		float r = d / 2.0F;
		Brush.disc(l.g, x + r, y + r, r, on ? (l.di(x, y, x + d, y + d) ? Palette.JADE_LIGHT : Palette.JADE) : Palette.JADE_DULL);
		Brush.ring(l.g, x + r, y + r, r - 1.2F, r, Palette.JADE_DARK);
		Brush.centered(l.g, font, Component.literal(tanda), x + r, y + r - 4.0F, 0xFFF4FFF8, 1.0F, false);
		l.zona(kunci, x, y, x + d, y + d, aksi, on, null, false);
	}

	// ------------------------------------------------------------------------------------------------ the ladder

	private void tangga(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar t = lembar.sub(Taruhan.TANGGA);
		int y = y0 + strip(l, TemaUi.k("taruhan.ucap_tangga"), x0, y0, x1, 32);
		Kotak[] k = bagi(x0, y, x1, y1);
		Kotak a = k[0];
		PanelTempat.sumur(l.g, gaya, a.x0(), a.y0(), a.x1(), a.y1());
		l.g.fillGradient(a.x0() + 1, a.y0() + 1, a.x1() - 1, a.y1() - 1, 0xFF2A0C10, 0xFF120408);
		int peringkat = Math.max(1, Math.min(9, t.i(Taruhan.PERINGKAT, 9)));
		int rungH = Math.max(10, (a.h() - 8) / 9);
		int rw = Math.min(a.w() - 30, 120);
		int rx = (a.x0() + a.x1()) / 2 - rw / 2;
		for (int n = 1; n <= 9; n++) {
			int ry = a.y0() + 4 + (n - 1) * rungH;
			boolean kini = n == peringkat;
			boolean atas = n < peringkat;
			int warna = kini ? 0xFFFFE0A0 : atas ? 0xFF9A7A7A : 0xFFD8B0A0;
			TemaUi.anakTangga(l.g, rx, ry, rw, Math.min(16, rungH - 1), warna);
			Brush.text(l.g, font, Component.literal(Integer.toString(n)), rx - 12, ry + rungH / 2.0F - 3, kini ? TempatWarna.EMAS_TERANG : 0xFFA08080,
					0.75F, true);
			if (kini) {
				TemaUi.ikon(l.g, TemaUi.I_ORANG, rx + rw + 3, ry, Math.min(12, rungH));
				Brush.halo(l.g, rx + rw / 2.0F, ry + rungH / 2.0F, rw / 2.0F, 1.5F, 0xFFC060, l.denyut());
			} else if (n == peringkat - 1) {
				TemaUi.ikon(l.g, TemaUi.I_TOPENG, rx + rw + 3, ry, Math.min(12, rungH));
			}
		}
		Kotak d = k[1];
		PanelTempat.kartu(l.g, gaya, d.x0(), d.y0(), d.x1(), d.y1(), 0.0F, false);
		int dx0 = d.x0() + 5;
		int dx1 = d.x1() - 5;
		int dy = d.y0() + 4;
		dy += subjudul(l, TemaUi.k("taruhan.peringkatmu", peringkat), dx0, dy, dx1);
		Lembar lawan = t.sub(Taruhan.LAWAN);
		if (lawan.ada(LembarKunci.NAMA)) {
			l.keterangan(TemaUi.k("taruhan.lawan"), dx0, dy, dx1 - dx0, gaya.teksLemah);
			dy += 10;
			l.teks(Brush.bold(lawan.teks(LembarKunci.NAMA)), dx0, dy, dx1 - dx0, aksen(), 0.9F);
			dy += 10;
			l.keterangan(lawan.teks(Taruhan.JULUKAN), dx0, dy, dx1 - dx0 - 50, gaya.teksSamar);
			TemaUi.bintang(l, Math.min(5, (lawan.i(Taruhan.TIER, 1) + 1) / 2), 5, dx1 - 45, dy - 1, 9);
			dy += 13;
		} else if (peringkat == 1) {
			l.bungkus(TemaUi.k("taruhan.juara"), dx0, dy, dx1 - dx0, aksen(), 0.8F);
			dy += 20;
		}
		dy += barisNilai(l, TemaUi.k("taruhan.biaya"), TeksTempat.nb(t.l(LembarKunci.BIAYA, 0L), false), dx0, dy, dx1);
		dy += barisNilai(l, TemaUi.k("taruhan.hadiah"), TeksTempat.nb(t.l(Taruhan.HADIAH, 0L), false), dx0, dy, dx1);
		dy += barisNilai(l, TemaUi.k("taruhan.sisa_tantang"), Component.literal(t.i(Taruhan.SISA, 5) + " / 5"), dx0, dy, dx1) + 3;
		l.bungkus(TemaUi.k("taruhan.tangga_jelas"), dx0, dy, dx1 - dx0, gaya.teksSamar, 0.7F);
		long biaya = t.l(LembarKunci.BIAYA, 0L);
		boolean bisa = t.b(LembarKunci.BISA) && mampu(biaya);
		Component alasan = !t.b(LembarKunci.BISA) ? t.teksAtau(LembarKunci.ALASAN) : !mampu(biaya) ? kurang(biaya) : null;
		tombolOp(l, "tantang", dx0, d.y1() - 19, dx1 - dx0, 15, TemaUi.k("taruhan.tantang", TeksTempat.angka(biaya)), bisa, alasan,
				Taruhan.OP_TANTANG);
	}
}
