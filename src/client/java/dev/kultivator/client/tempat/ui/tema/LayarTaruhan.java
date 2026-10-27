package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.dunia.kota42.ui.layar.SketsaKota;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.CincinWaktu;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Potret;
import dev.kultivator.client.screen.ui.TabBar;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Lembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
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
 * The one betting page of the mod (jadwal 41/42 0.1 "No GUI twice"): the DESIGN 42.11.10 dice table of a gambling house
 * ({@code guanpu}, keys {@link KotaLembar.Guanpu}) opens here as its dice tab — a green cloth, the bamboo cup that shakes
 * for 20 ticks after a throw and three dice that fall, bounce and settle (client physics, faces from the sheet), the
 * Besar / Kecil / Kembar board with the payout and the honest odds of each bet, the stake (steps 1/5/10, keys 1-3), the
 * throws left today and the last ten throws; tab Guanpu: the merchant's prizes, the price of one throw and the chance
 * written out. A Di Sini page on the single kit (43.8.3); the number keys pick the stake step under Di Sini (43.17 K3).
 */
public final class LayarTaruhan extends TemaHalaman {

	private static final int[] LANGKAH = {1, 5, 10};
	/** Dice animation: cup shake (20 ticks), then the fall and roll. */
	private static final long KOCOK_MS = KotaLembar.Guanpu.KOCOK_TICK * 50L;
	private static final long GULIR_MS = 900L;

	private String sisi = Taruhan.KIRI;
	private long taruh = 1L;
	private int langkahPilih = 1;
	private String btPilih = KotaLembar.Guanpu.BT_BESAR;
	private long lemparSejak = -1L;
	private final java.util.Random acak = new java.util.Random();
	private final DaftarGulir daftarDadu = new DaftarGulir();

	public LayarTaruhan(int npcId, CompoundTag lembar) {
		this(Taruhan.LAYAR, npcId, lembar);
	}

	/** The betting page for the 38.T id or the 42 dice table id ({@code guanpu}). */
	public LayarTaruhan(String layar, int npcId, CompoundTag lembar) {
		super(layar, npcId, lembar, "layar.taruhan", KotaLembar.Guanpu.LAYAR.equals(layar) ? Palette.Gaya.KAIN : Palette.Gaya.BATU);
	}

	private boolean dadu() {
		return KotaLembar.Guanpu.LAYAR.equals(layar);
	}

	@Override
	protected List<TabBar.Tab> tabs() {
		if (dadu()) {
			return List.of(TabBar.Tab.of(KotaLembar.Guanpu.TAB_BESAR_KECIL, SketsaKota.t("guanpu.tab.besar_kecil")),
					TabBar.Tab.of(KotaLembar.Guanpu.TAB_GUANPU, SketsaKota.t("guanpu.tab.guanpu")));
		}
		return List.of(tabTema(Taruhan.TAB_TARUHAN, "taruhan.tab.taruhan"),
				tabTema(Taruhan.TAB_TANGGA, "taruhan.tab.tangga"));
	}

	@Override
	protected boolean tabAda(String id) {
		return dadu() || !Taruhan.TAB_TANGGA.equals(id) || lembar.ada(Taruhan.TANGGA);
	}

	@Override
	protected Component petunjuk() {
		if (dadu()) {
			return SketsaKota.t("guanpu.kunci");
		}
		return Taruhan.TAB_TANGGA.equals(tab) ? null : TemaUi.k("taruhan.kunci");
	}

	@Override
	protected void tabGanti() {
		daftarDadu.mulaiUlang(net.minecraft.util.Util.getMillis());
	}

	@Override
	protected void lembarBaru(boolean buka) {
		if (dadu()) {
			Lembar meja = lembar.sub(KotaLembar.Guanpu.MEJA);
			long min = Math.max(1L, meja.l(KotaLembar.Guanpu.MIN, 1L));
			taruh = Math.max(min, Math.min(taruh, Math.max(min, meja.l(LembarKunci.MAKS, 50L))));
			if (lembar.b(KotaLembar.Guanpu.LEMPAR)) {
				lemparSejak = net.minecraft.util.Util.getMillis();
				UiSound.toggle(true);
			}
			return;
		}
		taruh = Math.max(1L, Math.min(taruh, maks()));
		Lembar pasang = lembar.sub(Taruhan.TARUHAN).sub(Taruhan.PASANG);
		if (pasang.ada(Taruhan.SISI)) {
			sisi = pasang.s(Taruhan.SISI, Taruhan.KIRI);
		}
	}

	private long maks() {
		return Math.max(1L, lembar.sub(Taruhan.TARUHAN).l(LembarKunci.MAKS, 20L));
	}

	/** K3 under Di Sini: keys 1-3 pick the stake step. */
	@Override
	public boolean angka(int n) {
		if (!Taruhan.TAB_TANGGA.equals(tab) && n >= 1 && n <= 3) {
			langkahPilih = n - 1;
			UiSound.tab();
			return true;
		}
		return false;
	}

	@Override
	protected boolean spasi() {
		if (dadu()) {
			if (KotaLembar.Guanpu.TAB_BESAR_KECIL.equals(tab) && tulisBoleh() && !berguling()) {
				kirim(KotaLembar.Guanpu.OP_LEMPAR);
			}
			return true;
		}
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
	protected void lukisTema(Lukis l, int x0, int y0, int x1, int y1) {
		if (dadu()) {
			meja(l, x0, y0, x1, y1);
			return;
		}
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
		int taruhH = 50;
		int ky1 = y1 - taruhH;
		int mid = (x0 + x1) / 2;
		int kh = Math.max(40, ky1 - y0 - 2);
		int kw = Math.min(110, Math.min((x1 - x0) / 2 - 22, Math.round(kh / 1.3F)));
		kh = Math.min(kh, Math.round(kw * 1.5F));
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
		int foot = y + h - 17;
		int ps = Math.max(12, Math.min(w - in * 2, foot - y - 26));
		int py = y + Math.round(h * 6.0F / 96.0F);
		Potret.gambar(l, x + (w - ps) / 2, py, ps, p.i(LembarKunci.POTRET, -1), LembarKunci.Ikon.ARENA, false, 0.0F);
		int ty = py + ps + 3;
		int tx0 = x + in;
		int tx1 = x + w - in;
		float cx = (tx0 + tx1) / 2.0F;
		l.tengah(Brush.bold(p.teks(LembarKunci.NAMA)), cx, ty, tx1 - tx0, 0xFFFFF0E0);
		ty += 10;
		int tier = Math.max(0, Math.min(9, p.i(Taruhan.TIER, 1)));
		if (ty + 9 <= foot) {
			l.tengah(p.teks(Taruhan.JULUKAN), cx, ty, tx1 - tx0, 0xFFE0A0A0);
			ty += 10;
		}
		if (ty + 9 <= foot) {
			TemaUi.bintang(l, Math.min(5, (tier + 1) / 2), 5, Math.round(cx) - 22, ty, 9);
			ty += 10;
		}
		if (ty + 9 <= foot) {
			l.tengah(p.teks(Taruhan.WATAK), cx, ty, tx1 - tx0, 0xFFC8B8B0);
		}
		// chance bar and odds chip at the card foot
		float peluang = Math.max(0.0F, Math.min(1.0F, p.f(LembarKunci.PELUANG, 0.5F)));
		TemaUi.meter(l, -1, tx0, y + h - 15, tx1, peluang, TemaUi.DARAH_TERANG, 0.5F);
		Component odds = Component.literal("×" + String.format(Locale.ROOT, "%.2f", p.f(Taruhan.ODDS, 2.0F)));
		int cw = font.width(odds) + 8;
		TemaUi.chipOdds(l.g, x + w / 2 - cw / 2, y + h - 6, cw, 13);
		l.tengah(odds, x + w / 2.0F, y + h - 4, cw, 0xFF2A1808);
		if (dipilih) {
			Brush.frame(l.g, x - 1, y - 1, x + w + 1, y + h + 1, Palette.GOLD_LIGHT);
		}
		l.zona("sisi_" + id, x, y, x + w, y + h, () -> {
			sisi = id;
			UiSound.toggle(true);
		}, true, null, true);
		l.fokusCincin("sisi_" + id, x, y, x + w, y + h);
		if (over) {
			l.tip(List.of(Brush.bold(p.teks(LembarKunci.NAMA)), p.teks(Taruhan.JULUKAN), TemaUi.k("taruhan.peluang", Math.round(peluang * 100.0F)),
					TemaUi.k("taruhan.tier", tier), p.teks(Taruhan.WATAK)));
		}
	}

	private void taruhan(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar t = lembar.sub(Taruhan.TARUHAN);
		Lembar pasang = t.sub(Taruhan.PASANG);
		l.kartu(x0, y0, x1, y1, 0.0F, false);
		int ix0 = x0 + 5;
		int ix1 = x1 - 5;
		int y = y0 + 3;
		int sisa = t.i(Taruhan.SISA, 6);
		Component sisaT = TemaUi.k("taruhan.sisa", sisa);
		int sw = Math.min((ix1 - ix0) / 2, font.width(sisaT) + 6);
		l.teks(sisaT, ix0, y, sw, sisa > 0 ? gaya.teksLemah : l.gaya.turun());
		l.samar(TemaUi.k("taruhan.maks", Brush.angka(maks())), ix0 + sw, y, ix1 - ix0 - sw);
		y += 12;
		if (pasang.ada(Taruhan.SISI)) {
			// the bet already on the plate
			int pw = Math.min(160, ix1 - ix0 - 70);
			TemaUi.pelatTaruhan(l.g, ix0, y, pw, 18);
			String s = pasang.s(Taruhan.SISI, Taruhan.KIRI);
			Lembar p = lembar.sub(Taruhan.LAGA).sub(s);
			long nb = pasang.l(LembarKunci.NB, 0L);
			l.teks(TemaUi.k("taruhan.dipasang", Brush.angka(nb), p.teks(LembarKunci.NAMA)), ix0 + 6, y + 5, pw - 12, 0xFFFFF0E0);
			long bayar = Math.round(nb * p.f(Taruhan.ODDS, 2.0F));
			l.teks(TemaUi.k("taruhan.bila_menang"), ix0 + pw + 6, y, ix1 - ix0 - pw - 6, gaya.teksLemah);
			LabelHarga.gambar(l, bayar, -1L, ix0 + pw + 6, y + 10, false, true);
			return;
		}
		// stake: steps, coins, amount, payout
		for (int i = 0; i < LANGKAH.length; i++) {
			int cx = ix0 + i * 27;
			boolean on = i == langkahPilih;
			final int idx = i;
			l.g.fill(cx, y, cx + 25, y + 13, on ? Palette.argb(0xD0, Palette.JADE) : Palette.argb(0x30, 0x000000));
			l.tengah(Component.literal("±" + LANGKAH[i]), cx + 12.5F, y + 3, 24, on ? 0xFFF4FFF8 : gaya.teksLemah);
			l.zona("langkah" + i, cx, y, cx + 25, y + 13, () -> langkahPilih = idx, true, null, true);
			l.fokusCincin("langkah" + i, cx, y, cx + 25, y + 13);
		}
		int mx = ix0 + 84;
		koin(l, "kurang", mx, y - 1, "−", taruh > 1L, () -> ubah(-1));
		l.sumur(mx + 16, y - 2, mx + 74, y + 13);
		l.tengah(Brush.bold(Brush.nb(taruh, false)), mx + 45, y + 2, 56, gaya.teksSumur());
		Lukis.Zona z = l.zona("taruh", mx + 16, y - 2, mx + 74, y + 13, null, true, null, true);
		if (z != null) {
			z.geser = this::ubah;
		}
		l.fokusCincin("taruh", mx + 16, y - 2, mx + 74, y + 13);
		koin(l, "tambah", mx + 76, y - 1, "+", taruh < maks(), () -> ubah(1));
		Lembar p = lembar.sub(Taruhan.LAGA).sub(sisi);
		long bayar = Math.round(taruh * p.f(Taruhan.ODDS, 2.0F));
		int bx = mx + 96;
		if (bx + 30 < ix1) {
			int pw = LabelHarga.gambar(l, bayar, -1L, bx, y + 2, false, true);
			if (l.di(bx, y, bx + pw, y + 12)) {
				l.tip(TemaUi.k("taruhan.bila_menang"));
			}
		}
		boolean bisa = bisaPasang();
		Component alasan = sisa <= 0 ? TemaUi.k("taruhan.batas") : !mampu(taruh) ? kurang(taruh) : null;
		tombolOp(l, "pasang", ix0, y1 - 17, ix1 - ix0, 14, TemaUi.k("taruhan.pasang", Brush.angka(taruh), p.teks(LembarKunci.NAMA)), bisa, alasan,
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
		boolean sempit = tata == Tata.SEMPIT;
		int y = y0 + strip(l, TemaUi.k("taruhan.ucap_tangga"), x0, y0, x1, sempit ? 30 : 32);
		Kotak[] k;
		if (sempit) {
			int mid = x0 + (x1 - x0) * 2 / 5;
			k = new Kotak[] {new Kotak(x0, y, mid - 2, y1), new Kotak(mid + 2, y, x1, y1)};
		} else {
			k = bagi(x0, y, x1, y1);
		}
		Kotak a = k[0];
		l.sumur(a.x0(), a.y0(), a.x1(), a.y1());
		l.g.fillGradient(a.x0() + 1, a.y0() + 1, a.x1() - 1, a.y1() - 1, 0xFF2A0C10, 0xFF120408);
		int peringkat = Math.max(1, Math.min(9, t.i(Taruhan.PERINGKAT, 9)));
		int rungH = Math.max(9, (a.h() - 8) / 9);
		int rw = Math.min(a.w() - 34, 120);
		int rx = (a.x0() + a.x1()) / 2 - rw / 2 + 4;
		for (int n = 1; n <= 9; n++) {
			int ry = a.y0() + 4 + (n - 1) * rungH;
			boolean kini = n == peringkat;
			boolean atas = n < peringkat;
			int warna = kini ? 0xFFFFE0A0 : atas ? 0xFF9A7A7A : 0xFFD8B0A0;
			TemaUi.anakTangga(l.g, rx, ry, rw, Math.min(16, rungH - 1), warna);
			Brush.text(l.g, font, Component.literal(Integer.toString(n)), rx - 9, ry + rungH / 2.0F - 4, kini ? Palette.GOLD_LIGHT : 0xFFA08080, 1.0F,
					true);
			if (kini) {
				TemaUi.ikon(l.g, TemaUi.I_ORANG, rx + rw + 2, ry, Math.min(12, rungH));
				Brush.halo(l.g, rx + rw / 2.0F, ry + rungH / 2.0F, rw / 2.0F, 1.5F, 0xFFC060, l.denyut());
			} else if (n == peringkat - 1) {
				TemaUi.ikon(l.g, TemaUi.I_TOPENG, rx + rw + 2, ry, Math.min(12, rungH));
			}
		}
		Kotak d = k[1];
		l.kartu(d.x0(), d.y0(), d.x1(), d.y1(), 0.0F, false);
		int dx0 = d.x0() + 5;
		int dx1 = d.x1() - 5;
		int dy = d.y0() + 4;
		int by = d.y1() - 19;
		dy += subjudul(l, TemaUi.k("taruhan.peringkatmu", peringkat), dx0, dy, dx1);
		Lembar lawan = t.sub(Taruhan.LAWAN);
		if (lawan.ada(LembarKunci.NAMA)) {
			Component judul = TemaUi.k("taruhan.lawan");
			int jw = Math.min((dx1 - dx0) / 2, font.width(judul) + 4);
			l.teks(judul, dx0, dy, jw, gaya.teksLemah);
			l.teks(Brush.bold(lawan.teks(LembarKunci.NAMA)), dx0 + jw, dy, dx1 - dx0 - jw, aksen());
			dy += 10;
			l.samar(lawan.teks(Taruhan.JULUKAN), dx0, dy, dx1 - dx0 - 50);
			TemaUi.bintang(l, Math.min(5, (lawan.i(Taruhan.TIER, 1) + 1) / 2), 5, dx1 - 45, dy, 9);
			dy += 12;
		} else if (peringkat == 1) {
			dy += Math.round(l.bungkus(TemaUi.k("taruhan.juara"), dx0, dy, dx1 - dx0, aksen(), 18)) + 2;
		}
		dy += barisNilai(l, TemaUi.k("taruhan.biaya"), Brush.nb(t.l(LembarKunci.BIAYA, 0L), false), dx0, dy, dx1);
		dy += barisNilai(l, TemaUi.k("taruhan.hadiah"), Brush.nb(t.l(Taruhan.HADIAH, 0L), false), dx0, dy, dx1);
		dy += barisNilai(l, TemaUi.k("taruhan.sisa_tantang"), Component.literal(t.i(Taruhan.SISA, 5) + " / 5"), dx0, dy, dx1) + 3;
		if (by - dy >= 11) {
			l.bungkus(TemaUi.k("taruhan.tangga_jelas"), dx0, dy, dx1 - dx0, gaya.teksSamar, by - dy - 2);
		}
		long biaya = t.l(LembarKunci.BIAYA, 0L);
		boolean bisa = t.b(LembarKunci.BISA) && mampu(biaya);
		Component alasan = !t.b(LembarKunci.BISA) ? t.teksAtau(LembarKunci.ALASAN) : !mampu(biaya) ? kurang(biaya) : null;
		tombolOp(l, "tantang", dx0, by, dx1 - dx0, 15, TemaUi.k("taruhan.tantang", Brush.angka(biaya)), bisa, alasan, Taruhan.OP_TANTANG);
	}

	// ------------------------------------------------------------------------------------------------ 42 guanpu dice

	private boolean berguling() {
		return lemparSejak >= 0L && net.minecraft.util.Util.getMillis() - lemparSejak < KOCOK_MS + GULIR_MS;
	}

	private void meja(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar meja = lembar.sub(KotaLembar.Guanpu.MEJA);
		int sisa = meja.i(KotaLembar.Guanpu.SISA, 0);
		int batas = Math.max(1, meja.i(KotaLembar.Guanpu.BATAS_HARI, 20));
		Component sisaT = SketsaKota.t("guanpu.sisa", sisa, batas);
		int sw = font.width(sisaT);
		l.teks(Brush.bold(meja.teks(LembarKunci.TEMPAT)), x0, y0, x1 - x0 - sw - 6, gaya.teks);
		l.kanan(sisaT, x1, y0, sisa > 0 ? gaya.teksLemah : l.gaya.turun());
		int y = y0 + 12;
		// the green cloth with the cup and dice
		int mejaH = Math.min(tata == Tata.SEMPIT ? 50 : 72, (y1 - y) / 2);
		int mx0 = x0;
		int mx1 = Math.min(x1, x0 + Math.max(150, (x1 - x0) / 2));
		cawan(l, mx0, y, mx1, y + mejaH);
		// beside the cloth: the last result and the history
		int hx = mx1 + 6;
		Lembar hasil = lembar.sub(KotaLembar.Guanpu.HASIL);
		int hy = y;
		if (!hasil.kosongSemua() && !berguling()) {
			boolean menang = hasil.b(KotaLembar.Guanpu.MENANG);
			l.teks(Brush.bold(SketsaKota.t(menang ? "guanpu.menang" : "guanpu.kalah", Brush.angka(hasil.l("nb", 0L)))), hx, hy, x1 - hx,
					menang ? l.gaya.naik() : l.gaya.turun());
			hy += 10;
			Component t = hasil.teksAtau(LembarKunci.TEKS);
			if (t != null) {
				hy += Math.round(l.bungkus(t, hx, hy, x1 - hx, gaya.teksLemah, 18));
			}
			hy += 2;
		}
		if (hx + 40 < x1 && hy + 19 <= y + mejaH) {
			l.samar(SketsaKota.t("guanpu.riwayat"), hx, hy, x1 - hx);
			hy += 10;
			List<Lembar> riwayat = lembar.daftar(LembarKunci.RIWAYAT);
			for (Lembar r : riwayat) {
				if (hy + 9 > y + mejaH) {
					break;
				}
				int[] d = r.ia(KotaLembar.Guanpu.DADU);
				int dx = hx;
				for (int i = 0; i < Math.min(3, d.length); i++) {
					daduKecil(l, dx, hy + 1, 7, d[i]);
					dx += 8;
				}
				Component nb = null;
				if (r.ada(KotaLembar.Guanpu.MENANG)) {
					boolean m = r.b(KotaLembar.Guanpu.MENANG);
					nb = Component.literal((m ? "+" : "−") + Brush.angka(r.l("nb", 0L))).withColor(m ? l.gaya.naik() : l.gaya.turun());
					l.kanan(nb, x1, hy, 0xFFFFFFFF);
				}
				String h = r.s(KotaLembar.Guanpu.HASIL);
				l.teks(SketsaKota.t("guanpu.hasil." + (h.isEmpty() ? "kecil" : h)), dx + 3, hy, x1 - dx - 5 - (nb == null ? 0 : font.width(nb) + 2),
						gaya.teksLemah);
				hy += 10;
			}
		}
		y += mejaH + 4;
		if (KotaLembar.Guanpu.TAB_GUANPU.equals(tab)) {
			hadiah(l, sisa, x0, y, x1, y1);
		} else {
			papan(l, meja, sisa, x0, y, x1, y1);
		}
	}

	/** The cloth, the bamboo cup shaking, then three dice falling, bouncing and settling on the sheet's faces. */
	private void cawan(Lukis l, int x0, int y0, int x1, int y1) {
		l.g.fillGradient(x0, y0, x1, y1, 0xFF1F6A3A, 0xFF0F3A20);
		Brush.frame(l.g, x0, y0, x1, y1, 0xFF6A4A20);
		Brush.frame(l.g, x0 + 2, y0 + 2, x1 - 2, y1 - 2, Palette.argb(0x60, 0xD4A637));
		int[] akhir = lembar.ia(KotaLembar.Guanpu.DADU);
		long kini = net.minecraft.util.Util.getMillis();
		long t = lemparSejak < 0L ? Long.MAX_VALUE : kini - lemparSejak;
		float cx = (x0 + x1) / 2.0F;
		float lantai = y1 - 10;
		int ukuran = Math.max(9, Math.min(14, (y1 - y0) / 5));
		if (t < KOCOK_MS) {
			// the cup shakes over the cloth
			float goyang = (float) Math.sin(t / 30.0D) * 4.0F;
			cangkir(l, cx + goyang, y0 + 8, (y1 - y0) * 0.55F);
			return;
		}
		if (t < KOCOK_MS + GULIR_MS) {
			float p = (t - KOCOK_MS) / (float) GULIR_MS;
			for (int i = 0; i < 3; i++) {
				// each die: thrown sideways, a damped bounce, faces flicker until it rests
				float arah = (i - 1) * 0.9F;
				float x = cx + arah * (x1 - x0) * 0.22F * (float) Math.sqrt(p);
				float tinggi = (float) Math.abs(Math.cos(p * Math.PI * (2.5F + i * 0.4F))) * (1.0F - p) * (y1 - y0) * 0.5F;
				float yy = lantai - tinggi - ukuran / 2.0F;
				int muka = p < 0.85F ? 1 + acak.nextInt(6) : (i < akhir.length ? akhir[i] : 1);
				daduBesar(l, Math.round(x - ukuran / 2.0F), Math.round(yy), ukuran, muka, (1.0F - p) * 25.0F * (i - 1));
			}
			return;
		}
		if (akhir.length >= 3) {
			for (int i = 0; i < 3; i++) {
				float x = cx + (i - 1) * (x1 - x0) * 0.22F;
				daduBesar(l, Math.round(x - ukuran / 2.0F), Math.round(lantai - ukuran), ukuran, akhir[i], 0.0F);
			}
			int jumlah = lembar.i(LembarKunci.JUMLAH, akhir[0] + akhir[1] + akhir[2]);
			Brush.centered(l.g, font, SketsaKota.t("guanpu.jumlah", jumlah), cx, y0 + 5, 0xFFF4FFF0, 1.0F, true);
		} else {
			cangkir(l, cx, y0 + 8, (y1 - y0) * 0.55F);
			l.tengah(SketsaKota.t("guanpu.belum"), cx, y1 - 11, x1 - x0 - 6, 0xFFB8E0C0);
		}
	}

	/** A bamboo cup: banded body, darker rim. */
	private static void cangkir(Lukis l, float cx, float y, float h) {
		float w = h * 0.7F;
		int x0 = Math.round(cx - w / 2.0F);
		int x1 = Math.round(cx + w / 2.0F);
		int y1 = Math.round(y + h);
		l.g.fillGradient(x0, Math.round(y), x1, y1, Palette.BAMBOO, Palette.BAMBOO_ALT);
		for (int b = 1; b < 4; b++) {
			int by = Math.round(y + h * b / 4.0F);
			l.g.fill(x0, by, x1, by + 1, Palette.BAMBOO_KNOT);
		}
		l.g.fill(x0 - 1, y1 - 2, x1 + 1, y1, Palette.CORD);
		l.g.fill(x0 + 1, Math.round(y), x0 + 3, y1 - 2, 0x30FFFFFF);
	}

	/** A die of {@code s} px with face {@code muka} (1 and 4 in red pips, the Chinese way), tilted by {@code miring} px. */
	private static void daduBesar(Lukis l, int x, int y, int s, int muka, float miring) {
		int geser = Math.round(miring / 10.0F);
		l.g.fill(x + 1 + geser, y + 1, x + s + 1 + geser, y + s + 1, 0x60000000);
		Brush.chamfer(l.g, x + geser, y, x + s + geser, y + s, 2, 0xFFF6F2E8);
		l.g.fill(x + geser, y + s - 2, x + s + geser, y + s, 0xFFD8D2C4);
		pip(l, x + geser, y, s, muka, Math.max(1.2F, s / 9.0F));
	}

	private static void daduKecil(Lukis l, int x, int y, int s, int muka) {
		l.g.fill(x, y, x + s, y + s, 0xFFF6F2E8);
		pip(l, x, y, s, muka, 0.8F);
	}

	private static void pip(Lukis l, int x, int y, int s, int muka, float r) {
		int warna = muka == 1 || muka == 4 ? 0xFFC0202A : 0xFF1B2521;
		float a = s * 0.25F;
		float b = s * 0.5F;
		float c = s * 0.75F;
		float[][] pos = switch (Math.max(1, Math.min(6, muka))) {
			case 1 -> new float[][] {{b, b}};
			case 2 -> new float[][] {{a, a}, {c, c}};
			case 3 -> new float[][] {{a, a}, {b, b}, {c, c}};
			case 4 -> new float[][] {{a, a}, {c, a}, {a, c}, {c, c}};
			case 5 -> new float[][] {{a, a}, {c, a}, {b, b}, {a, c}, {c, c}};
			default -> new float[][] {{a, a}, {c, a}, {a, b}, {c, b}, {a, c}, {c, c}};
		};
		float rr = muka == 1 ? r * 1.6F : r;
		for (float[] p : pos) {
			Brush.disc(l.g, x + p[0], y + p[1], rr, warna);
		}
	}

	/** Besar-Kecil: the bets with payout and honest odds, the stake and the throw. */
	private void papan(Lukis l, Lembar meja, int sisa, int x0, int y0, int x1, int y1) {
		List<Lembar> bets = lembar.daftar(KotaLembar.Guanpu.TARUHAN);
		int bawah = y1 - 34;
		if (bets.isEmpty()) {
			kosong(l, SketsaKota.t("guanpu.taruhan_kosong"), x0, y0, x1, bawah);
		} else {
			daftarDadu.gambar(l, "taruhan42", x0, y0, x1, bawah, bets.size(), 15, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
				Lembar b = bets.get(i);
				boolean dipilih = b.id().equals(btPilih);
				lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih || dipilih);
				int sepertiga = (rx1 - rx0) / 3;
				lk.teks(Brush.bold(b.teks(LembarKunci.NAMA)), rx0 + 4, ry0 + 3, sepertiga - 4, dipilih ? Palette.GOLD_LIGHT : gaya.teks);
				lk.teks(SketsaKota.t("guanpu.bayar", b.teks(KotaLembar.Guanpu.BAYAR)), rx0 + sepertiga + 4, ry0 + 3, sepertiga - 4, gaya.teksLemah);
				lk.teks(SketsaKota.t("guanpu.peluang", b.teks(LembarKunci.PELUANG)), rx0 + sepertiga * 2 + 4, ry0 + 3, sepertiga - 8, gaya.teksSamar);
				if (b.l("nb", 0L) > 0L) {
					lk.g.fill(rx0, ry0, rx0 + 2, ry1, Palette.GOLD);
				}
			}, null, i -> {
				if (i >= 0 && i < bets.size()) {
					btPilih = bets.get(i).id();
				}
			});
		}
		long min = Math.max(1L, meja.l(KotaLembar.Guanpu.MIN, 1L));
		long maks = Math.max(min, meja.l(LembarKunci.MAKS, 50L));
		int by = y1 - 32;
		l.samar(SketsaKota.t("guanpu.taruhan_batas", Brush.angka(min), Brush.angka(maks), LANGKAH[langkahPilih]), x0, by, x1 - x0);
		by += 11;
		tombol(l, "nb_kurang", x0, by, 16, 14, Component.literal("−"), taruh > min, null, () -> taruh = Math.max(min, taruh - LANGKAH[langkahPilih]));
		LabelHarga.gambar(l, taruh, uang(), x0 + 19, by + 3, false, false);
		tombol(l, "nb_tambah", x0 + 62, by, 16, 14, Component.literal("+"), taruh < maks, null, () -> taruh = Math.min(maks, taruh + LANGKAH[langkahPilih]));
		int bw = (x1 - x0 - 86) / 2;
		boolean bisa = sisa > 0 && mampu(taruh) && !berguling();
		Component alasan = sisa <= 0 ? SketsaKota.t("guanpu.habis") : !mampu(taruh) ? kurang(taruh) : SketsaKota.t("guanpu.tunggu");
		tombolOp(l, "taruh", x0 + 84, by, bw, 14, SketsaKota.t("guanpu.taruh"), bisa, alasan, LembarKunci.op(KotaLembar.Guanpu.OP_TARUH, btPilih, taruh));
		tombolOp(l, "lempar", x0 + 88 + bw, by, bw, 14, SketsaKota.t("guanpu.lempar"), sisa > 0 && !berguling(),
				sisa <= 0 ? SketsaKota.t("guanpu.habis") : SketsaKota.t("guanpu.tunggu"), KotaLembar.Guanpu.OP_LEMPAR);
	}

	/** Guanpu: the merchant's prizes, one throw each at 1/8 of the value, won on 15 or more; the chance written out. */
	private void hadiah(Lukis l, int sisa, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(KotaLembar.Guanpu.GUANPU);
		l.samar(SketsaKota.t("guanpu.hadiah_info"), x0, y0, x1 - x0);
		int y = y0 + 11;
		if (rows.isEmpty()) {
			kosong(l, SketsaKota.t("guanpu.hadiah_kosong"), x0, y, x1, y1);
			return;
		}
		daftarDadu.gambar(l, "hadiah42", x0, y, x1, y1, rows.size(), 24, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			lk.g.item(e.barang(), rx0 + 4, ry0 + 4);
			long harga = e.l(KotaLembar.Guanpu.HARGA_LEMPAR, 0L);
			Component label = SketsaKota.t("guanpu.lempar_harga", Brush.angka(harga));
			int bw = Math.min(100, Math.max(70, font.width(label) + 14));
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 24, ry0 + 2, rx1 - rx0 - 30 - bw, gaya.teks);
			lk.teks(SketsaKota.t("guanpu.hadiah_baris", e.i(KotaLembar.Guanpu.MENANG_JIKA, 15), e.teks(LembarKunci.PELUANG), e.i(KotaLembar.Guanpu.SISA, 1)),
					rx0 + 24, ry0 + 12, rx1 - rx0 - 30 - bw, gaya.teksLemah);
			tombolOp(lk, "guanpu" + i, rx1 - bw - 3, ry0 + 5, bw, 14, label,
					sisa > 0 && mampu(harga) && !berguling() && e.i(KotaLembar.Guanpu.SISA, 1) > 0, sisa <= 0 ? SketsaKota.t("guanpu.habis") : kurang(harga),
					LembarKunci.op(KotaLembar.Guanpu.OP_LEMPAR_GUANPU, e.id()));
		}, null, null);
	}
}
