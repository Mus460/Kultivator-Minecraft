package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeadaanTerkunci;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Pratinjau3D;
import dev.kultivator.client.screen.ui.TabBar;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Lembar;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.Tawar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * {@code tema_tawar} (DESIGN 38.T.4.5, wireframe of 38.T.13.3): haggling at a bazaar stall of the Kota Oasis. Under the
 * striped awning: the trader's live portrait and last line, the mood gauge, the goods turning on the lacquer pedestal,
 * the opening price and the hint "≈ ? NB" (never the hidden floor), the round pips, the offer with jade coins (−/+ by the
 * chosen step 1 / 5 / 10, ←/→ while focused, keys 1-3 pick the step), and the buttons Tawar (Space), Terima N NB and
 * Pergi. A small ledger keeps this visit's offers and the replies. A trader who refuses ({@code menolak}, mood below 20)
 * locks the screen with his line. A Di Sini page on the single kit (43.8.3); in the narrow layout (GUI scale 4) the goods
 * shrink to one line and the mood gauge to a bar under the trader, so the offer and its buttons always fit.
 */
public final class LayarTawar extends TemaHalaman {

	private final Pratinjau3D pratinjau = new Pratinjau3D();
	private long tawaran = -1L;
	private int langkahPilih = 1;
	private int putaranLalu = -1;
	private long terakhirDikirim = -1L;
	private final List<long[]> catatan = new ArrayList<>();
	private final List<Component> balasan = new ArrayList<>();
	private long getarSejak = -1L;
	private int suasanaLalu = -1;

	public LayarTawar(int npcId, CompoundTag lembar) {
		super(Tawar.LAYAR, npcId, lembar, "layar.tawar", Palette.Gaya.KAIN);
	}

	@Override
	protected List<TabBar.Tab> tabs() {
		return List.of();
	}

	@Override
	protected int panelMaks(Tata t) {
		return t == Tata.LEBAR ? 520 : 440;
	}

	@Override
	protected Component petunjuk() {
		return TemaUi.k("tawar.kunci");
	}

	@Override
	protected void lembarBaru(boolean buka) {
		int putaran = lembar.i(Tawar.PUTARAN, 1);
		long dariServer = lembar.l(Tawar.TAWARAN, -1L);
		if (buka || tawaran < 0L) {
			tawaran = dariServer >= 0L ? dariServer : Math.max(1L, Math.round(lembar.l(Tawar.BUKA, 0L) * 0.75D));
		}
		if (putaranLalu >= 0 && putaran != putaranLalu && terakhirDikirim >= 0L) {
			catatan.add(new long[] {putaranLalu, terakhirDikirim});
			balasan.add(lembar.teks(Tawar.UCAPAN));
			if (catatan.size() > 6) {
				catatan.remove(0);
				balasan.remove(0);
			}
		}
		int suasana = lembar.i(Tawar.SUASANA, 50);
		if (suasanaLalu >= 0 && suasana < suasanaLalu - 10) {
			getarSejak = Util.getMillis();
			UiSound.denied();
		}
		suasanaLalu = suasana;
		putaranLalu = putaran;
	}

	private int[] langkah() {
		int[] l = lembar.ia(Tawar.LANGKAH);
		return l.length == 0 ? new int[] {1, 5, 10} : l;
	}

	private int langkahNb() {
		int[] l = langkah();
		return l[Math.max(0, Math.min(l.length - 1, langkahPilih))];
	}

	private void ubah(int arah) {
		long buka = Math.max(1L, lembar.l(Tawar.BUKA, 1L));
		long step = langkahNb() * (Minecraft.getInstance().hasShiftDown() ? 10L : 1L);
		long baru = Math.max(1L, Math.min(buka, tawaran + arah * step));
		if (baru != tawaran) {
			tawaran = baru;
			UiSound.toggle(arah > 0);
		} else {
			UiSound.denied();
		}
	}

	private boolean bisaTawar() {
		return !lembar.b(Tawar.MENOLAK) && lembar.i(Tawar.PUTARAN, 1) <= lembar.i(Tawar.PUTARAN_MAKS, 3) && tulisBoleh();
	}

	private void tawar() {
		if (bisaTawar()) {
			terakhirDikirim = tawaran;
			kirim(LembarKunci.op(Tawar.OP_TAWAR, tawaran));
		}
	}

	@Override
	protected boolean spasi() {
		tawar();
		return true;
	}

	/** K3 under Di Sini: keys 1-3 pick the offer step. */
	@Override
	public boolean angka(int n) {
		if (n >= 1 && n <= langkah().length) {
			langkahPilih = n - 1;
			UiSound.tab();
			return true;
		}
		return false;
	}

	@Override
	protected void lukisTema(Lukis l, int x0, int y0, int x1, int y1) {
		boolean sempit = tata == Tata.SEMPIT;
		int y = y0;
		if (!sempit) {
			TemaUi.tenda(l.g, x0, x1, y0 - 2);
			y += 15;
		}
		Lembar barang = lembar.sub(Tawar.BARANG);
		if (lembar.b(Tawar.MENOLAK)) {
			KeadaanTerkunci.gambar(l, lembar.teks(Tawar.UCAPAN), TemaUi.k("tawar.menolak"), x0, y, x1, y1 - 20, Util.getMillis() - diterima);
			int bw = 80;
			tombolOp(l, "pergi", (x0 + x1 - bw) / 2, y1 - 17, bw, 15, TemaUi.k("tawar.pergi"), true, null, Tawar.OP_PERGI);
			return;
		}
		// speaker strip with the mood gauge on its right (narrow: a bar under it)
		int suasana = lembar.i(Tawar.SUASANA, 50);
		int gaugeW = sempit ? 0 : 82;
		int stripH = sempit ? 32 : 44;
		boolean getar = getarSejak >= 0L && l.kini - getarSejak < 400L;
		int dx = getar ? Math.round((float) Math.sin(l.kini / 22.0D) * 2.0F) : 0;
		l.g.pose().pushMatrix();
		l.g.pose().translate(dx, 0.0F);
		int sh = strip(l, lembar.teks(Tawar.UCAPAN), x0, y, x1 - gaugeW - (gaugeW > 0 ? 4 : 0), stripH);
		l.g.pose().popMatrix();
		if (gaugeW > 0) {
			suasana(l, suasana, x1 - gaugeW, y, x1, y + stripH);
			y += Math.max(sh, stripH + 4);
		} else {
			y += sh;
			y += suasanaBaris(l, suasana, x0, y, x1);
		}
		if (sempit) {
			y += barangRingkas(l, barang, x0, y, x1);
			tawarBlok(l, new Kotak(x0, y, x1, y1), suasana);
			return;
		}
		Kotak[] k = bagi(x0, y, x1, y1);
		barang(l, barang, k[0]);
		tawarBlok(l, k[1], suasana);
	}

	private static int warnaSuasana(int suasana) {
		return suasana < 20 ? Palette.BAHAYA : suasana < 50 ? Palette.PERINGATAN : Palette.OK;
	}

	private void suasana(Lukis l, int suasana, int x0, int y0, int x1, int y1) {
		l.sumur(x0, y0, x1, y1);
		int ink = gaya.teksSumur();
		TemaUi.ikon(l.g, TemaUi.I_SUASANA, x0 + 3, y0 + 3, 14);
		l.teks(TemaUi.k("tawar.suasana"), x0 + 19, y0 + 6, x1 - x0 - 22, ink);
		int warna = warnaSuasana(suasana);
		TemaUi.meter(l, -1, x0 + 4, y0 + 20, x1 - 4, suasana / 100.0F, warna, 0.2F);
		l.tengah(Component.literal(Integer.toString(suasana)), (x0 + x1) / 2.0F, y0 + 31, x1 - x0 - 4, warna);
		if (l.di(x0, y0, x1, y1)) {
			l.tip(List.of(TemaUi.k("tawar.suasana_tip"), TemaUi.k("tawar.suasana_batas")));
		}
	}

	/** The narrow mood row: icon, the bar with the refusal mark at 20, the number. Returns the height used. */
	private int suasanaBaris(Lukis l, int suasana, int x0, int y, int x1) {
		int warna = warnaSuasana(suasana);
		Component n = Component.literal(Integer.toString(suasana));
		TemaUi.meter(l, TemaUi.I_SUASANA, x0, y + 1, x1 - font.width(n) - 4, suasana / 100.0F, warna, 0.2F);
		l.kanan(n, x1, y, warna);
		if (l.di(x0, y, x1, y + 10)) {
			l.tip(List.of(TemaUi.k("tawar.suasana_tip"), TemaUi.k("tawar.suasana_batas")));
		}
		return 12;
	}

	/** The narrow goods line: name × amount, opening price, round pips (the rest in its tooltip). Returns the height used. */
	private int barangRingkas(Lukis l, Lembar barang, int x0, int y, int x1) {
		Component nama = TemaUi.k("tawar.barang", barang.teks(LembarKunci.NAMA), barang.i(LembarKunci.JUMLAH, 1));
		int maks = Math.max(1, lembar.i(Tawar.PUTARAN_MAKS, 3));
		int pipW = pip(l, x1 - 13 * maks, y - 1);
		Component harga = Brush.nb(lembar.l(Tawar.BUKA, 0L), false);
		int hw = font.width(harga);
		l.teks(Brush.bold(nama), x0, y, x1 - x0 - pipW - hw - 10, aksen());
		l.kanan(harga, x1 - pipW - 4, y, gaya.teksLemah);
		if (l.di(x0, y, x1 - pipW, y + 10)) {
			Component kira = lembar.ada(Tawar.DASAR_KIRA) ? lembar.teks(Tawar.DASAR_KIRA) : TemaUi.k("tawar.dasar_tanya");
			l.tip(List.of(nama, TemaUi.k("tawar.harga_buka").copy().append(": ").append(harga),
					TemaUi.k("tawar.harga_dasar").copy().append(": ").append(kira)));
		}
		return 13;
	}

	/** Round pips from x (one per round, the current one glowing); returns the width. */
	private int pip(Lukis l, int x, int y) {
		int putaran = lembar.i(Tawar.PUTARAN, 1);
		int maks = Math.max(1, lembar.i(Tawar.PUTARAN_MAKS, 3));
		for (int i = 0; i < maks; i++) {
			int cx = x + 5 + i * 13;
			boolean lewat = i < putaran - 1;
			boolean kini = i == putaran - 1;
			Brush.disc(l.g, cx, y + 5, 4.5F, lewat ? Palette.argb(0xB0, 0x5A4A50) : kini ? Palette.GOLD : Palette.argb(0x50, 0x000000));
			if (kini) {
				Brush.halo(l.g, cx, y + 5, 5.0F, 1.5F, Palette.GOLD_LIGHT & 0xFFFFFF, l.denyut());
			}
			if (lewat) {
				Glyphs.Atlas.ikon(l.g, Glyphs.Atlas.SILANG, cx - 3, y + 2, 7, 0xFFFFFFFF);
			}
		}
		if (l.di(x, y, x + maks * 13, y + 10)) {
			l.tip(TemaUi.k("tawar.putaran", Math.min(putaran, maks), maks));
		}
		return maks * 13;
	}

	private void barang(Lukis l, Lembar barang, Kotak k) {
		l.kartu(k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
		int x0 = k.x0() + 5;
		int x1 = k.x1() - 5;
		int y = k.y0() + 4;
		int jumlah = barang.i(LembarKunci.JUMLAH, 1);
		Component nama = barang.teks(LembarKunci.NAMA);
		l.teks(Brush.bold(TemaUi.k("tawar.barang", nama, jumlah)), x0, y, x1 - x0, aksen());
		y += 12;
		int pv = tata == Tata.LEBAR ? 84 : 56;
		int pvY = Math.min(y, k.y1() - pv - 4);
		pratinjau.gambar(l, "pratinjau", x1 - pv, pvY, x1, pvY + pv, barang.barang(), null, LembarKunci.Ikon.HERBA);
		int tx1 = x1 - pv - 4;
		y += barisNilai(l, TemaUi.k("tawar.harga_buka"), Brush.nb(lembar.l(Tawar.BUKA, 0L), false), x0, y, tx1) + 1;
		Component kira = lembar.ada(Tawar.DASAR_KIRA) ? lembar.teks(Tawar.DASAR_KIRA) : TemaUi.k("tawar.dasar_tanya");
		y += barisNilai(l, TemaUi.k("tawar.harga_dasar"), kira, x0, y, tx1) + 3;
		int putaran = lembar.i(Tawar.PUTARAN, 1);
		int maks = Math.max(1, lembar.i(Tawar.PUTARAN_MAKS, 3));
		l.teks(TemaUi.k("tawar.putaran", Math.min(putaran, maks), maks), x0, y, tx1 - x0, gaya.teksLemah);
		y += 10;
		pip(l, x0, y);
		y += 14;
		// this visit's ledger
		if (!catatan.isEmpty() && y + 14 < k.y1()) {
			y += subjudul(l, TemaUi.k("tawar.catatan"), x0, y, tx1);
			for (int i = catatan.size() - 1; i >= 0 && y + 9 <= k.y1() - 2; i--) {
				long[] c = catatan.get(i);
				Component baris = TemaUi.k("tawar.catatan_baris", c[0], Brush.angka(c[1]));
				int bw = Math.min(70, font.width(baris) + 4);
				l.teks(baris, x0, y, bw, gaya.teksLemah);
				l.samar(balasan.get(i), x0 + bw, y, tx1 - x0 - bw);
				y += 10;
			}
		}
	}

	private void tawarBlok(Lukis l, Kotak k, int suasana) {
		boolean sempit = tata == Tata.SEMPIT;
		l.kartu(k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
		int x0 = k.x0() + 5;
		int x1 = k.x1() - 5;
		int y = k.y0() + 3;
		if (!sempit) {
			y += subjudul(l, TemaUi.k("tawar.tawaranmu"), x0, y, x1);
		}
		// step chips (keys 1-3 under Di Sini)
		int[] langkah = langkah();
		Component label = TemaUi.k("tawar.langkah");
		int lw = Math.min(56, font.width(label) + 4);
		l.teks(label, x0, y + 2, lw, gaya.teksLemah);
		for (int i = 0; i < langkah.length; i++) {
			int cx = x0 + lw + i * 30;
			boolean on = i == langkahPilih;
			final int idx = i;
			boolean over = l.di(cx, y, cx + 27, y + 13);
			l.g.fill(cx, y, cx + 27, y + 13, on ? Palette.argb(0xD0, Palette.JADE) : Palette.argb(over ? 0x60 : 0x30, 0x000000));
			Brush.frame(l.g, cx, y, cx + 27, y + 13, on ? Palette.JADE_LIGHT : Palette.argb(0x80, Palette.GOLD));
			l.tengah(Component.literal("±" + langkah[i]), cx + 13.5F, y + 3, 26, on ? 0xFFF4FFF8 : gaya.teksLemah);
			l.zona("langkah" + i, cx, y, cx + 27, y + 13, () -> langkahPilih = idx, true, null, true);
			l.fokusCincin("langkah" + i, cx, y, cx + 27, y + 13);
		}
		y += 16;
		// the offer with jade coins
		long buka = lembar.l(Tawar.BUKA, 0L);
		int cy = y + 1;
		int mid = (x0 + x1) / 2;
		koin(l, "kurang", mid - 54, cy, "−", tawaran > 1L, () -> ubah(-1));
		koin(l, "tambah", mid + 40, cy, "+", tawaran < buka, () -> ubah(1));
		l.sumur(mid - 37, cy - 1, mid + 36, cy + 15);
		l.tengah(Brush.bold(Brush.nb(tawaran, false)), mid, cy + 3, 70, gaya.teksSumur());
		Lukis.Zona z = l.zona("tawaran", mid - 37, cy - 1, mid + 36, cy + 15, null, true, null, true);
		if (z != null) {
			z.geser = this::ubah;
		}
		l.fokusCincin("tawaran", mid - 37, cy - 1, mid + 36, cy + 15);
		y += 19;
		// how far the offer is from the opening price
		int by = k.y1() - 18;
		float persen = buka > 0L ? tawaran / (float) buka : 1.0F;
		boolean rendah = persen < 0.72F;
		if (y + 9 <= by - 1) {
			l.tengah(TemaUi.k("tawar.dari_buka", Math.round(persen * 100.0F)), mid, y, x1 - x0, rendah ? l.gaya.turun() : gaya.teksSamar);
			if (rendah) {
				if (y + 19 <= by - 1) {
					y += 10;
					l.tengah(TemaUi.k("tawar.awas_rendah"), mid, y, x1 - x0, l.gaya.turun());
				} else if (l.di(x0, y, x1, y + 10)) {
					l.tip(TemaUi.k("tawar.awas_rendah"));
				}
			}
		}
		// the trader's price over the buttons when there is room
		long terima = lembar.l(Tawar.TERIMA, -1L);
		if (terima >= 0L && by - 11 > y + 10) {
			int ty = by - 11;
			l.teks(TemaUi.k("tawar.pedagang_mau"), x0, ty, x1 - x0 - 60, gaya.teksLemah);
			LabelHarga.gambar(l, terima, uang(), x1, ty, true, false);
		}
		// buttons at the bottom
		int w3 = (x1 - x0 - 8) / 3;
		boolean bisa = bisaTawar();
		Component habis = lembar.i(Tawar.PUTARAN, 1) > lembar.i(Tawar.PUTARAN_MAKS, 3) ? TemaUi.k("tawar.putaran_habis") : null;
		TalismanButton.gambar(l, "tawar", x0, by, w3, 15, TemaUi.k("tawar.tawar"), bisa, habis != null ? habis : alasanBaca(), this::tawar,
				suasana >= 20 && bisa, false);
		boolean bisaTerima = terima >= 0L && mampu(terima);
		Component alasanTerima = terima < 0L ? TemaUi.k("tawar.belum_ada_harga") : !mampu(terima) ? kurang(terima) : null;
		tombolOp(l, "terima", x0 + w3 + 4, by, w3, 15, terima >= 0L ? TemaUi.k("tawar.terima", Brush.angka(terima)) : TemaUi.k("tawar.terima_kosong"),
				bisaTerima, alasanTerima, Tawar.OP_TERIMA);
		tombolOp(l, "pergi", x0 + (w3 + 4) * 2, by, w3, 15, TemaUi.k("tawar.pergi"), true, null, Tawar.OP_PERGI);
	}

	private void koin(Lukis l, String kunci, int x, int y, String tanda, boolean on, Runnable aksi) {
		int d = 14;
		boolean over = l.di(x, y, x + d, y + d);
		float r = d / 2.0F;
		Brush.disc(l.g, x + r, y + r + 0.5F, r, Palette.argb(0x50, 0x000000));
		Brush.disc(l.g, x + r, y + r, r, on ? (over ? Palette.JADE_LIGHT : Palette.JADE) : Palette.JADE_DULL);
		Brush.ring(l.g, x + r, y + r, r - 1.2F, r, on ? Palette.JADE_DARK : Palette.grey(Palette.JADE_DARK, 0.6F));
		Brush.centered(l.g, font, Component.literal(tanda), x + r, y + r - 4.0F, on ? 0xFFF4FFF8 : 0xFFB8C4BC, 1.0F, false);
		l.zona(kunci, x, y, x + d, y + d, aksi, on, null, false);
	}
}
