package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TabBar;
import dev.kultivator.client.screen.ui.Lembar;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.Mural;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * {@code tema_mural} (DESIGN 38.T.4.5, 38.T.4.7): the Kisah Mural of the Tebing Seribu Lukisan. Tab "Kisah": the twelve
 * murals as thumbnails painted on sandstone (unread ones glow, read ones carry a check seal); the selected mural large,
 * with its tale scrolled by the mouse wheel. Reading a mural the first time sends {@code baca:NO} (the server grants +0.5
 * understanding once). Mural 7 "Pendeta yang Diusir" moves in front of the reader when {@code bergerak} is set (two
 * frames, slot 12 of {@code mural.png}). Tab "Pesan": order your own mural (50 NB, pick one of your feats) and its
 * progress; it appears in the twelfth niche after three days. A Di Sini page on the single kit (43.8.3); in the narrow
 * layout the murals take the area and Enter / a second click opens the reader in their place (Esc back).
 */
public final class LayarMural extends TemaHalaman {

	private static final String TAB_KISAH = "kisah";
	private static final String TAB_PESAN = "pesan";

	private final DaftarGulir daftar = new DaftarGulir();
	private final DaftarGulir prestasi = new DaftarGulir();
	private final Set<Integer> dikirim = new HashSet<>();
	private float gulirTeks;
	private int teksTinggi;
	private int kotakTeksY0;
	private int kotakTeksY1;
	private int kotakTeksX0;
	private int kotakTeksX1;
	private int pilihLalu = -1;
	private long gantiSejak;
	private boolean rinciSempit;

	public LayarMural(int npcId, CompoundTag lembar) {
		super(Mural.LAYAR, npcId, lembar, "layar.mural", Palette.Gaya.BATU);
	}

	@Override
	protected List<TabBar.Tab> tabs() {
		return List.of(tabTema(TAB_KISAH, "mural.tab.kisah"), tabTema(TAB_PESAN, "mural.tab.pesan"));
	}

	@Override
	protected boolean tabAda(String id) {
		return !TAB_PESAN.equals(id) || lembar.ada(Mural.PESAN_MURAL);
	}

	@Override
	protected KeadaanKosong.Gambar kosongGambar() {
		return KeadaanKosong.Gambar.GULUNGAN;
	}

	@Override
	protected Component petunjuk() {
		return TemaUi.k("mural.kunci");
	}

	@Override
	protected void tabGanti() {
		rinciSempit = false;
	}

	@Override
	public boolean rinci() {
		return rinciSempit && tata == Tata.SEMPIT && TAB_KISAH.equals(tab) || super.rinci();
	}

	@Override
	public boolean mundur() {
		if (rinciSempit && tata == Tata.SEMPIT && TAB_KISAH.equals(tab)) {
			rinciSempit = false;
			return true;
		}
		return super.mundur();
	}

	@Override
	protected void lukisTema(Lukis l, int x0, int y0, int x1, int y1) {
		kotakTeksY1 = kotakTeksY0;
		if (TAB_PESAN.equals(tab)) {
			pesan(l, x0, y0, x1, y1);
			return;
		}
		List<Lembar> kisah = lembar.daftar(Mural.KISAH);
		if (kisah.isEmpty()) {
			kosong(l, TemaUi.k("mural.kosong"), x0, y0, x1, y1);
			return;
		}
		boolean sempit = tata == Tata.SEMPIT;
		int kiriX1 = x0 + Math.min(150, (x1 - x0) * 2 / 5);
		Kotak kiri = sempit ? new Kotak(x0, y0, x1, y1) : new Kotak(x0, y0, kiriX1, y1);
		Kotak kanan = sempit ? new Kotak(x0, y0, x1, y1) : new Kotak(kiriX1 + 6, y0, x1, y1);
		if (!sempit || !rinciSempit) {
			rinciSempit = false;
			int dibaca = 0;
			for (Lembar e : kisah) {
				dibaca += e.b(Mural.DIBACA) ? 1 : 0;
			}
			int ly = kiri.y0() + subjudul(l, TemaUi.k("mural.dibaca", dibaca, kisah.size()), kiri.x0(), kiri.y0(), kiri.x1());
			daftar.gambar(l, "kisah", kiri.x0(), ly, kiri.x1(), kiri.y1(), kisah.size(), 26, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
				Lembar e = kisah.get(i);
				lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
				boolean baca = e.b(Mural.DIBACA);
				if (!baca) {
					TemaUi.cahaya(lk, rx0 + 13, ry0 + 12, 14, Palette.argb(Math.round(110 + 90 * lk.denyut()), 0xFFE0A0));
				}
				TemaUi.mural(lk.g, e.i(Mural.GAMBAR, i), rx0 + 2, ry0 + 1, 23, 23, baca ? 0xFFFFFFFF : 0xFFFFF0D8);
				Brush.frame(lk.g, rx0 + 2, ry0 + 1, rx0 + 25, ry0 + 24, Palette.argb(0xA0, 0x3A2A1E));
				lk.samar(TemaUi.k("mural.no", e.i(LembarKunci.NO, i + 1)), rx0 + 29, ry0 + 2, rx1 - rx0 - 44);
				lk.teks(e.teks(LembarKunci.NAMA), rx0 + 29, ry0 + 13, rx1 - rx0 - 44, baca ? gaya.teksLemah : gaya.teks);
				if (baca) {
					Glyphs.Atlas.ikon(lk.g, Glyphs.Atlas.CENTANG, rx1 - 12, ry0 + 8, 10, 0xFFFFFFFF);
				}
			}, () -> rinciSempit = true, i -> {
			});
		}
		int sel = Mth.clamp(daftar.pilihan(), 0, kisah.size() - 1);
		Lembar e = kisah.get(sel);
		if (sel != pilihLalu) {
			pilihLalu = sel;
			gulirTeks = 0.0F;
			gantiSejak = l.kini;
			int no = e.i(LembarKunci.NO, sel + 1);
			if (!e.b(Mural.DIBACA) && tulisBoleh() && dikirim.add(no)) {
				kirim(LembarKunci.op(Mural.OP_BACA, no));
			}
		}
		if (!sempit || rinciSempit) {
			baca(l, kanan, e, sel);
		}
	}

	private void baca(Lukis l, Kotak k, Lembar e, int sel) {
		l.kartu(k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
		int x0 = k.x0() + 5;
		int x1 = k.x1() - 5;
		int y = k.y0() + 4;
		int gambar = e.i(Mural.GAMBAR, sel);
		int besar = Math.min(x1 - x0, Math.min(k.h() / 2, tata == Tata.SEMPIT ? 56 : tata == Tata.LEBAR ? 112 : 76));
		int gx = (x0 + x1 - besar) / 2;
		// the mural pressed into the cliff: dark rim, then the panel; it fades in when picked
		float t = Math.min(1.0F, (l.kini - gantiSejak) / 260.0F);
		l.g.fill(gx - 3, y - 1, gx + besar + 3, y + besar + 3, 0xFF3A2A1E);
		int frame = gambar;
		if (e.b(Mural.BERGERAK) && (l.kini / 650L) % 2L == 1L) {
			frame = gambar == 6 ? 12 : gambar;
		}
		TemaUi.mural(l.g, frame, gx, y + 1, besar, besar, Palette.argb(Math.round(255 * t), 0xFFFFFF));
		if (e.b(Mural.BERGERAK)) {
			Brush.halo(l.g, gx + besar / 2.0F, y + besar / 2.0F, besar * 0.55F, 3.0F, 0xE0A060, 0.4F + 0.4F * l.denyut());
		}
		y += besar + 6;
		l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), x0, y, x1 - x0, aksen());
		y += 11;
		// the tale, scrolled by the wheel (and ↑↓ on the reader through the page's wheel handling)
		kotakTeksX0 = x0;
		kotakTeksX1 = x1;
		kotakTeksY0 = y;
		kotakTeksY1 = k.y1() - 4;
		Component teks = e.teks(LembarKunci.TEKS);
		int lebar = x1 - x0 - 4;
		teksTinggi = Math.round(Brush.wrapHeight(font, teks, lebar, 1.0F));
		int ruang = kotakTeksY1 - kotakTeksY0;
		gulirTeks = Mth.clamp(gulirTeks, 0.0F, Math.max(0, teksTinggi - ruang));
		l.klip(x0, kotakTeksY0, x1, kotakTeksY1);
		l.bungkus(teks, x0, kotakTeksY0 - gulirTeks, lebar, gaya.teks);
		l.lepasKlip();
		if (teksTinggi > ruang && ruang > 0) {
			float frac = ruang / (float) teksTinggi;
			int len = Math.max(8, Math.round(ruang * frac));
			int pos = Math.round((ruang - len) * (gulirTeks / Math.max(1.0F, teksTinggi - ruang)));
			l.g.fill(x1 - 2, kotakTeksY0, x1, kotakTeksY1, Palette.argb(0x30, 0x000000));
			l.g.fill(x1 - 2, kotakTeksY0 + pos, x1, kotakTeksY0 + pos + len, Palette.argb(0xB0, gaya.aksen));
		}
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (TAB_KISAH.equals(tab) && x >= kotakTeksX0 && x < kotakTeksX1 && y >= kotakTeksY0 && y < kotakTeksY1) {
			gulirTeks -= (float) scrollY * 12.0F;
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	private void pesan(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar p = lembar.sub(Mural.PESAN_MURAL);
		int y = y0 + strip(l, TemaUi.k("mural.ucap_pesan"), x0, y0, x1, tata == Tata.SEMPIT ? 30 : 34);
		Kotak[] k = bagi(x0, y, x1, y1);
		Kotak kiri = k[0];
		List<Lembar> pilihan = p.daftar(Mural.PILIHAN);
		int ly = kiri.y0() + subjudul(l, TemaUi.k("mural.prestasi"), kiri.x0(), kiri.y0(), kiri.x1());
		if (pilihan.isEmpty()) {
			kosong(l, TemaUi.k("mural.prestasi_kosong"), kiri.x0(), ly, kiri.x1(), kiri.y1());
		} else {
			prestasi.gambar(l, "prestasi", kiri.x0(), ly, kiri.x1(), kiri.y1() - 18, pilihan.size(), 20, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
				Lembar e = pilihan.get(i);
				lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
				Glyphs.Atlas.ikon(lk.g, e.i(LembarKunci.IKON, Glyphs.Atlas.PIALA), rx0 + 3, ry0 + 2, 15, 0xFFFFFFFF);
				lk.teks(e.teks(LembarKunci.NAMA), rx0 + 22, ry0 + 6, rx1 - rx0 - 26, gaya.teks);
			}, null, i -> {
			});
			int sel = prestasi.pilihan();
			long harga = p.l(LembarKunci.HARGA, 50L);
			boolean bisa = p.b(LembarKunci.BISA) && sel >= 0 && sel < pilihan.size() && !p.ada(Mural.MILIK);
			Component alasan = p.ada(Mural.MILIK) ? TemaUi.k("mural.sudah_pesan") : !mampu(harga) ? kurang(harga) : p.teksAtau(LembarKunci.ALASAN);
			String id = sel >= 0 && sel < pilihan.size() ? pilihan.get(sel).id() : "";
			tombolOp(l, "pesan", kiri.x0(), kiri.y1() - 15, kiri.w(), 15, TemaUi.k("mural.pesan", Brush.angka(harga)), bisa && mampu(harga), alasan,
					LembarKunci.op(Mural.OP_PESAN, id));
		}
		// right: the twelfth niche with the player's mural (or its sketch)
		Kotak kanan = k[1];
		l.kartu(kanan.x0(), kanan.y0(), kanan.x1(), kanan.y1(), 0.0F, false);
		int rx0 = kanan.x0() + 5;
		int rx1 = kanan.x1() - 5;
		int ry = kanan.y0() + 4;
		ry += subjudul(l, TemaUi.k("mural.ceruk"), rx0, ry, rx1);
		Lembar milik = p.sub(Mural.MILIK);
		boolean sempit = tata == Tata.SEMPIT;
		int besar = Math.min(rx1 - rx0, Math.min(kanan.h() - (sempit ? 24 : 50), 96));
		// narrow: the niche sits left of its caption
		int gx = sempit ? rx0 + 3 : (rx0 + rx1 - besar) / 2;
		if (besar >= 16) {
			l.g.fill(gx - 3, ry - 1, gx + besar + 3, ry + besar + 3, 0xFF3A2A1E);
			TemaUi.mural(l.g, 13, gx, ry + 1, besar, besar, 0xFFFFFFFF);
			int sel = prestasi.pilihan();
			Lembar tampil = milik.ada(LembarKunci.NAMA) ? milik : sel >= 0 && sel < pilihan.size() ? pilihan.get(sel) : null;
			if (tampil != null) {
				int s = besar / 2;
				int a = milik.ada(LembarKunci.NAMA) && milik.i(LembarKunci.SISA_HARI, 0) <= 0 ? 0xFFFFFFFF : 0x80FFFFFF;
				Glyphs.Atlas.ikon(l.g, tampil.i(LembarKunci.IKON, Glyphs.Atlas.PIALA), gx + (besar - s) / 2, ry + 1 + (besar - s) / 2, s, a);
			}
		}
		int tx = sempit && besar >= 16 ? gx + besar + 8 : rx0;
		int ty = sempit ? ry : ry + Math.max(0, besar) + 6;
		int bawah = kanan.y1() - 3;
		if (milik.ada(LembarKunci.NAMA)) {
			int sisa = milik.i(LembarKunci.SISA_HARI, 0);
			l.teks(Brush.bold(milik.teks(LembarKunci.NAMA)), tx, ty, rx1 - tx, aksen());
			ty += 11;
			if (bawah - ty >= 9) {
				l.bungkus(sisa > 0 ? TemaUi.k("mural.dilukis", Brush.hari(sisa)) : TemaUi.k("mural.selesai"), tx, ty, rx1 - tx, gaya.teksLemah, bawah - ty);
			}
		} else if (bawah - ty >= 9) {
			l.bungkus(TemaUi.k("mural.penjelasan"), tx, ty, rx1 - tx, gaya.teksSamar, bawah - ty);
		}
	}
}
