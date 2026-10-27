package dev.kultivator.client.dunia.kota42.ui.layar;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.GulirIsi;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Potret;
import dev.kultivator.client.screen.ui.Lembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Kartu;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Pohon;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * {@code warga_kartu} and {@code warga_pohon} (DESIGN 42.11.2, 42.11.3): the card of one townsperson and the family tree,
 * one class because the tree is the card's last tab. The card has four tabs on the single kit:
 * <ul>
 * <li>Diri — portrait with the status ring, name (and hanzi), age, job with skill bar, temperament, root, habits, mood and
 * health bars, birth, the zhuazhou object, the wish; then "Tentang kamu" (times met, impression bar, debt, the strongest
 * memories) and the action talismans the server allows (Beri, Pekerjakan, Tanam modal, Pinjam, Ajak ke sekte, Tunjuk);</li>
 * <li>Keluarga — one card per relative with the bond icon, status and "Buka kartu"; Teman — friends, lovers, enemies and
 * rivals with a strength bar and "where now"; Ingatan — every memory of the player with its icon and the change it made;</li>
 * <li>Pohon — the server's graph laid out by generation (couples side by side with a double rule, children hanging from the
 * couple's midpoint); drag or the arrow keys pan, a click opens a living person's card, an archived person's summary is a
 * tooltip, "Naik" / "Turun" re-centre on a parent or a child, "Makam" marks the grave.</li>
 * </ul>
 * The tree answers {@code warga_pohon}; from the card the tab {@code pohon} is a server jump.
 */
public final class LayarWarga extends LayarKotaDasar {

	private final boolean pohon;
	private final DaftarGulir dKeluarga = new DaftarGulir();
	private final DaftarGulir dTeman = new DaftarGulir();
	private final DaftarGulir dIngatan = new DaftarGulir();
	private final GulirIsi gulirDiri = new GulirIsi();
	private long pinjam = 50L;
	private float panX;
	private float panY;
	private boolean menggeser;
	private int pusatTerakhir = -1;
	private int kotakX0;
	private int kotakY0;
	private int kotakX1;
	private int kotakY1;

	public LayarWarga(String layar, int npcId, CompoundTag lembar) {
		super(layar, npcId, lembar, KotaGambar.t("warga." + (Pohon.LAYAR.equals(layar) ? "pohon" : "kartu")), LembarKunci.GAYA_KERTAS);
		this.pohon = Pohon.LAYAR.equals(layar);
	}

	@Override
	protected List<Tab> daftarTab() {
		if (pohon) {
			return List.of(tab(Kartu.TAB_POHON, "kota.tab.pohon", Glyphs.Atlas.TONGGAK));
		}
		return List.of(tab(Kartu.TAB_DIRI, "kota.tab.diri", Glyphs.Atlas.GULUNGAN),
				tab(Kartu.TAB_KELUARGA, "kota.tab.keluarga", Glyphs.Atlas.HATI),
				tab(Kartu.TAB_TEMAN, "kota.tab.teman", Glyphs.Atlas.BINTANG_PENUH),
				tab(Kartu.TAB_INGATAN, "kota.tab.ingatan", Glyphs.Atlas.BUKU),
				tab(Kartu.TAB_POHON, "kota.tab.pohon", Glyphs.Atlas.TONGGAK));
	}

	@Override
	protected Set<String> lompat() {
		return pohon ? Set.of() : Set.of(Kartu.TAB_POHON);
	}

	@Override
	protected void tabBerubah() {
		long kini = Util.getMillis();
		dKeluarga.mulaiUlang(kini);
		dTeman.mulaiUlang(kini);
		dIngatan.mulaiUlang(kini);
		gulirDiri.atas();
	}

	@Override
	protected @Nullable Component petunjuk() {
		return pohon ? KotaGambar.t("pohon.petunjuk") : null;
	}

	@Override
	public String helpTopic() {
		return KotaLembar.TOPIK_WARGA;
	}

	// =================================================================== input

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!pohon && Kartu.TAB_DIRI.equals(tab) && gulirDiri.roda(mouseX, mouseY, scrollY)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (pohon && event.x() >= kotakX0 && event.x() < kotakX1 && event.y() >= kotakY0 && event.y() < kotakY1 && (menggeser || Math.abs(dx) + Math.abs(dy) > 0.5)) {
			menggeser = true;
			panX += (float) dx;
			panY += (float) dy;
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		boolean was = menggeser;
		menggeser = false;
		if (was) {
			// the drag ended: do not let the release count as a click on a node
			fokusRing.lepas(-1000, -1000);
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	protected boolean tombolLain(KeyEvent event) {
		if (!pohon) {
			return Kartu.TAB_DIRI.equals(tab) && gulirDiri.tombol(event);
		}
		int key = event.key();
		float langkah = 24.0F;
		switch (key) {
			case InputConstants.KEY_LEFT -> panX += langkah;
			case InputConstants.KEY_RIGHT -> panX -= langkah;
			case InputConstants.KEY_UP -> panY += langkah;
			case InputConstants.KEY_DOWN -> panY -= langkah;
			case InputConstants.KEY_HOME -> {
				panX = 0;
				panY = 0;
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	// =================================================================== drawing

	@Override
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		if (pohon) {
			gambarPohon(l, x0, y0, x1, y1);
			return;
		}
		switch (tab) {
			case Kartu.TAB_KELUARGA -> keluarga(l, x0, y0, x1, y1);
			case Kartu.TAB_TEMAN -> teman(l, x0, y0, x1, y1);
			case Kartu.TAB_INGATAN -> ingatan(l, x0, y0, x1, y1);
			default -> diri(l, x0, y0, x1, y1);
		}
	}

	private static Component tahap(String tahap) {
		String id = KotaLembar.TAHAP_MUDA.equals(tahap) ? "muda" : tahap;
		return Component.translatable("kultivator.kota42.tahap." + id);
	}

	private void diri(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar d = lembar.sub(Kartu.DIRI);
		if (d.kosongSemua()) {
			kosong(l, KotaGambar.t("kartu.kosong"), x0, y0, x1, y1);
			return;
		}
		int xr = x1 - 6;
		int y = gulirDiri.mulai(l, x0, y0, xr, y1);
		y = KotaGambar.kota(l, lembar, x0, y, xr);
		// ---- header: portrait, name, facts
		int ps = 56;
		Potret.gambar(l, x0 + 2, y + 1, ps, d.i(LembarKunci.POTRET, -1), -1, false, 0.0F);
		String status = d.s(KotaLembar.STATUS, KotaLembar.ST_HIDUP);
		if (!KotaLembar.ST_HIDUP.equals(status)) {
			Brush.frame(l.g, x0 + 2, y + 1, x0 + 2 + ps, y + 1 + ps, KotaGambar.warnaStatus(status));
		}
		int tx = x0 + ps + 10;
		int yy = y;
		Component nama = Brush.bold(d.teks(LembarKunci.NAMA));
		l.teks(nama, tx, yy, xr - tx - 14, l.gaya.aksen);
		int nw = Math.min(xr - tx - 14, l.font.width(nama));
		Component han = d.teksAtau(KotaLembar.NAMA_HAN);
		if (han != null && tx + nw + 6 + l.font.width(han) < xr - 14) {
			l.teks(han, tx + nw + 6, yy, xr - tx - nw - 20, Palette.GOLD_DARK);
		}
		KotaGambar.wajah(l, d.s(KotaLembar.WAJAH), xr - 12, yy - 1, 12);
		yy += 12;
		Component kelamin = KotaGambar.t("kelamin." + (d.i(KotaLembar.KELAMIN, 0) == KotaLembar.WANITA ? "wanita" : "pria"));
		l.teks(KotaGambar.t("kartu.garis1", kelamin, d.i(KotaLembar.UMUR, 0), tahap(d.s(KotaLembar.TAHAP))), tx, yy, xr - tx, l.gaya.teks);
		yy += 12;
		Component kerja = d.teksAtau(KotaLembar.KERJA);
		if (kerja != null) {
			l.teks(kerja, tx, yy, xr - tx - 70, l.gaya.teks);
			int ahli = d.i(KotaLembar.AHLI, 0);
			if (ahli > 0) {
				int bx = Math.max(tx + 30, xr - 66);
				Bilah.gambar(l, bx, yy + 3, xr, ahli / 100.0F, 0xFF5FA88C, null);
				if (l.di(bx, yy, xr, yy + 9)) {
					l.tip(KotaGambar.t("kartu.ahli", ahli));
				}
			}
			yy += 12;
		}
		Component tk = d.teksAtau(Kartu.TEMPAT_KERJA);
		if (tk != null && !tk.getString().isEmpty()) {
			l.teks(tk, tx, yy, xr - tx, l.gaya.teksLemah);
			yy += 12;
		}
		Component watak = d.teksAtau(KotaLembar.WATAK_NAMA);
		int akar = d.i(KotaLembar.AKAR, 0);
		if (watak != null) {
			l.teks(KotaGambar.t("kartu.watak", watak), tx, yy, (xr - tx) / 2, l.gaya.teksLemah);
		}
		int ax = tx + (xr - tx) / 2 + 4;
		IkonKota.gambar(l.g, akar > 0 && d.b(KotaLembar.AKAR_PASTI) ? IkonKota.AKAR : IkonKota.AKAR_TANYA, ax, yy - 1, 10);
		Component akarTeks = akar > 0 && d.b(KotaLembar.AKAR_PASTI)
				? KotaGambar.t("kartu.akar", Component.translatable("kultivator.root_grade." + new String[] {"trash", "low", "mid", "high", "heavenly"}[Math.min(4, akar - 1)]))
				: akar > 0 ? KotaGambar.t("kartu.akar_kira") : KotaGambar.t("kartu.akar_fana");
		l.teks(akarTeks, ax + 12, yy, xr - ax - 12, l.gaya.teksLemah);
		yy += 12;
		y = Math.max(yy, y + ps + 4);
		// ---- bars: mood, health
		int suasana = d.i(KotaLembar.SUASANA, 0);
		y += barisBilah(l, KotaGambar.t("kartu.suasana"), (suasana + 100) / 200.0F, KotaGambar.tanda(suasana), suasana >= 0 ? 0xFF5FA88C : 0xFFB03030, x0, y, xr);
		int sehat = d.i(Kartu.KESEHATAN, 100);
		y += barisBilah(l, KotaGambar.t("kartu.kesehatan"), sehat / 100.0F, Component.literal(Integer.toString(sehat)),
				d.b(Kartu.SAKIT) ? 0xFFB03030 : 0xFF5FA88C, x0, y, xr);
		// ---- habits, zhuazhou, wish, birth
		List<Lembar> kebiasaan = d.daftar(KotaLembar.KEBIASAAN);
		if (!kebiasaan.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			for (Lembar h : kebiasaan) {
				if (sb.length() > 0) {
					sb.append(" · ");
				}
				sb.append(h.teks(LembarKunci.NAMA).getString());
			}
			y += baris(l, KotaGambar.t("kartu.kebiasaan"), Component.literal(sb.toString()), x0, y, xr);
		}
		Lembar zh = d.sub(Kartu.ZHUAZHOU);
		if (!zh.kosongSemua()) {
			int slot = IkonKota.zhuazhou(zh.id());
			IkonKota.gambar(l.g, slot, xr - 14, y - 2, 14);
			l.teks(KotaGambar.t("kartu.zhuazhou", zh.teks(LembarKunci.NAMA)), x0, y, xr - x0 - 18, l.gaya.teksLemah);
			y += 13;
		}
		Lembar lahir = d.sub(Kartu.LAHIR);
		if (!lahir.kosongSemua()) {
			Component asal = d.teksAtau(Kartu.ASAL);
			Component teks = asal != null ? asal : KotaGambar.t("kartu.lahir", lahir.l(LembarKunci.HARI, 0L), lahir.teks(LembarKunci.TEMPAT));
			l.teks(teks, x0, y, xr - x0, l.gaya.teksLemah);
			y += 12;
		}
		Component lokasi = d.teksAtau(KotaLembar.LOKASI);
		if (lokasi != null) {
			l.teks(lokasi, x0, y, xr - x0, l.gaya.teksSamar);
			y += 12;
		}
		Component mimpi = d.teksAtau(Kartu.MIMPI);
		if (mimpi != null) {
			l.sumur(x0, y, xr, y + 4 + Math.round(Brush.wrapHeight(l.font, mimpi, xr - x0 - 10, 1.0F)) + 4);
			y += 4 + Math.round(l.bungkus(KotaGambar.t("kartu.mimpi", mimpi), x0 + 5, y + 4, xr - x0 - 10, l.gaya.teksSumur())) + 6;
		}
		// ---- about you
		Lembar p = lembar.sub(KotaLembar.PEMAIN);
		y += l.kepala(KotaGambar.t("kartu.pemain"), x0, y, xr);
		if (p.kosongSemua()) {
			l.teks(KotaGambar.t("kartu.belum_kenal"), x0, y, xr - x0, l.gaya.teksSamar);
			y += 13;
		} else {
			int kesan = p.i(KotaLembar.KESAN, 0);
			y += barisBilah(l, KotaGambar.t("kartu.kesan"), (kesan + 100) / 200.0F, KotaGambar.tanda(kesan), kesan >= 0 ? 0xFFE0506A : 0xFF8A1A1A, x0, y, xr);
			y += baris(l, KotaGambar.t("kartu.kenal"), KotaGambar.t("kartu.kenal_n", p.i(KotaLembar.KENAL, 0), KotaGambar.t(p.b(KotaLembar.TAHU_NAMA) ? "ya" : "tidak")), x0, y, xr);
			long utang = p.l(Kartu.UTANG, 0L);
			y += baris(l, KotaGambar.t("kartu.utang"), utang == 0 ? KotaGambar.t("kartu.utang_nol") : utang > 0 ? KotaGambar.t("kartu.utang_dia", Brush.angka(utang))
					: KotaGambar.t("kartu.utang_kamu", Brush.angka(-utang)), x0, y, xr);
			if (p.i(Kartu.BANTU, 0) > 0) {
				y += baris(l, KotaGambar.t("kartu.bantu"), KotaGambar.t("kartu.bantu_n", p.i(Kartu.BANTU, 0)), x0, y, xr);
			}
			List<Lembar> kn = p.daftar(Kartu.KENANGAN);
			for (Lembar k : kn) {
				y += kenangan(l, k, x0, y, xr, false);
			}
			long berikut = p.l(Kartu.BERIKUT, 0L);
			if (berikut > 0) {
				l.teks(KotaGambar.t("kartu.hadiah_berikut", berikut), x0, y, xr - x0, l.gaya.teksSamar);
				y += 12;
			}
		}
		// ---- actions
		y += 4;
		y = aksi(l, d, x0, y, xr);
		gulirDiri.selesai(l, y);
	}

	private int barisBilah(Lukis l, Component label, float frac, Component nilai, int warna, int x0, int y, int x1) {
		int lw = Math.min(90, (x1 - x0) / 3);
		l.teks(label, x0, y, lw - 4, l.gaya.teksLemah);
		int vw = l.font.width(nilai);
		l.kanan(nilai, x1, y, l.gaya.teks);
		Bilah.gambar(l, x0 + lw, y + 3, x1 - vw - 6, frac, warna, new float[] {0.5F});
		return 12;
	}

	private int baris(Lukis l, Component label, Component nilai, int x0, int y, int x1) {
		return KotaGambar.baris(l, label, nilai, x0, y, x1, l.gaya.teks);
	}

	/** A memory line: its icon, name (gold star when strong), and the day; {@code teks} adds the one-line story under it. */
	private int kenangan(Lukis l, Lembar k, int x0, int y, int x1, boolean teks) {
		IkonKota.gambar(l.g, IkonKota.kenangan(k.id()), x0, y - 2, 14);
		Component nama = k.teks(LembarKunci.NAMA);
		l.teks(k.b(Kartu.KUAT) ? Brush.bold(nama) : nama, x0 + 17, y, x1 - x0 - 80, l.gaya.teks);
		l.kanan(KotaGambar.t("hari", k.l(LembarKunci.HARI, 0L)), x1, y, l.gaya.teksSamar);
		int h = 14;
		Component cerita = teks ? k.teksAtau(LembarKunci.TEKS) : null;
		if (cerita != null) {
			l.teks(cerita, x0 + 17, y + 11, x1 - x0 - 20, l.gaya.teksLemah);
			h += 11;
		}
		return h;
	}

	private int aksi(Lukis l, Lembar d, int x0, int y, int xr) {
		List<Lembar> aksi = lembar.daftar(KotaLembar.AKSI);
		int x = x0;
		boolean adaPinjam = false;
		for (Lembar a : aksi) {
			String id = a.id();
			adaPinjam |= Kartu.A_PINJAM.equals(id) || Kartu.A_PINJAMKAN.equals(id);
		}
		if (adaPinjam) {
			y += 2;
			aksi(l, "pinjam#kurang", x0, y, 16, 14, Component.literal("−"), pinjam > 10L, null, () -> pinjam = Math.max(10L, pinjam - 10L));
			l.tengah(KotaGambar.nb(pinjam), x0 + 18 + 28, y + 3, 56, l.gaya.teks);
			aksi(l, "pinjam#tambah", x0 + 18 + 60, y, 16, 14, Component.literal("+"), true, null, () -> pinjam += 10L);
			y += 18;
		}
		for (int i = 0; i < aksi.size(); i++) {
			Lembar a = aksi.get(i);
			String id = a.id();
			Component label = a.teks(LembarKunci.NAMA);
			int w = lebarTombol(l, label, 50, 110);
			if (x + w > xr && x > x0) {
				x = x0;
				y += 17;
			}
			String op = a.ada(LembarKunci.OP) ? a.s(LembarKunci.OP) : id;
			if (Kartu.A_PINJAM.equals(id) || Kartu.A_PINJAMKAN.equals(id)) {
				op = LembarKunci.op(id, pinjam);
			}
			tulis(l, "aksi#" + i, x, y, w, 14, label, a.b(LembarKunci.BISA), a.teksAtau(LembarKunci.ALASAN), op);
			x += w + 4;
		}
		int tx = d.i(LembarKunci.X, 0);
		int tz = d.i(LembarKunci.Z, 0);
		if (tx != 0 || tz != 0) {
			Component label = KotaGambar.t("tunjuk");
			int w = lebarTombol(l, label, 50, 90);
			if (x + w > xr && x > x0) {
				x = x0;
				y += 17;
			}
			tunjuk(l, "tunjuk", x, y, w, 14, tx, tz);
		}
		return y + 18;
	}

	// ------------------------------------------------------------------ keluarga

	private void keluarga(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Kartu.KELUARGA);
		if (rows.isEmpty()) {
			kosong(l, KotaGambar.t("keluarga.kosong"), x0, y0, x1, y1);
			return;
		}
		dKeluarga.gambar(l, "keluarga", x0, y0, x1, y1, rows.size(), 40, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			KotaGambar.warga(lk, e, rx0, ry0, rx1, ry1, sorot, pilih, 92);
			String ikatan = e.s(Kartu.IKATAN);
			IkonKota.gambar(lk.g, IkonKota.keluarga(ikatan), rx1 - 96, ry0 + 4, 14);
			lk.teks(KotaGambar.t("ikatan." + ikatan), rx1 - 80, ry0 + 6, 76, lk.gaya.teksLemah);
			String status = e.s(KotaLembar.STATUS, KotaLembar.ST_HIDUP);
			Component ket = e.b(Kartu.ARSIP) ? KotaGambar.t("keluarga.wafat", e.l(Kartu.WAFAT, 0L)) : !KotaLembar.ST_HIDUP.equals(status)
					? KotaGambar.t("status." + status) : KotaGambar.t("keluarga.buka");
			lk.teks(ket, rx1 - 96, ry0 + 20, 92, e.b(Kartu.ARSIP) ? lk.gaya.teksSamar : lk.gaya.aksen);
		}, () -> bukaKartu(dKeluarga, rows), null);
	}

	private void bukaKartu(DaftarGulir dg, List<Lembar> rows) {
		int i = dg.pilihan();
		if (i < 0 || i >= rows.size()) {
			return;
		}
		Lembar e = rows.get(i);
		if (e.i(KotaLembar.WARGA, 0) > 0 && !e.b(Kartu.ARSIP)) {
			if (jauh()) {
				kirim(LembarKunci.op(LembarKunci.OP_TAB, "kartu", e.i(KotaLembar.WARGA, 0)));
			} else {
				kirim(LembarKunci.op(KotaLembar.OP_KARTU, e.i(KotaLembar.WARGA, 0)));
			}
		}
	}

	// ------------------------------------------------------------------ teman

	private void teman(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Kartu.TEMAN);
		if (rows.isEmpty()) {
			kosong(l, KotaGambar.t("teman.kosong"), x0, y0, x1, y1);
			return;
		}
		dTeman.gambar(l, "teman", x0, y0, x1, y1, rows.size(), 40, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			int ps = Math.max(16, Math.min(32, ry1 - ry0 - 6));
			Potret.gambar(lk, rx0 + 2, ry0 + (ry1 - ry0 - ps) / 2, ps, e.i(LembarKunci.POTRET, -1), -1, false, 0.0F);
			int tx = rx0 + ps + 7;
			String jenis = e.s(LembarKunci.JENIS);
			IkonKota.gambar(lk.g, IkonKota.hubungan(jenis), rx1 - 18, ry0 + 3, 14);
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), tx, ry0 + 3, rx1 - tx - 24, lk.gaya.teks);
			lk.teks(KotaGambar.t("jenis." + jenis, e.l(Kartu.SEJAK, 0L)), tx, ry0 + 14, rx1 - tx - 8, lk.gaya.teksLemah);
			int kuat = e.i(Kartu.KEKUATAN, 0);
			int bw = Math.min(70, (rx1 - tx) / 3);
			Bilah.gambar(lk, tx, ry0 + 27, tx + bw, (kuat + 100) / 200.0F, kuat >= 0 ? 0xFFE0506A : 0xFF8A1A1A, new float[] {0.5F});
			Component lokasi = e.teksAtau(KotaLembar.LOKASI);
			if (lokasi != null) {
				lk.teks(lokasi, tx + bw + 6, ry0 + 25, rx1 - tx - bw - 10, lk.gaya.teksSamar);
			}
		}, () -> bukaKartu(dTeman, rows), null);
	}

	// ------------------------------------------------------------------ ingatan

	private void ingatan(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Kartu.INGATAN);
		if (rows.isEmpty()) {
			kosong(l, KotaGambar.t("ingatan.kosong"), x0, y0, x1, y1);
			return;
		}
		dIngatan.gambar(l, "ingatan", x0, y0, x1, y1, rows.size(), 30, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = rows.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			IkonKota.gambar(lk.g, IkonKota.kenangan(e.id()), rx0 + 4, ry0 + 7, 16);
			int kesan = e.i(KotaLembar.KESAN, 0);
			lk.teks(e.b(Kartu.KUAT) ? Brush.bold(e.teks(LembarKunci.NAMA)) : e.teks(LembarKunci.NAMA), rx0 + 26, ry0 + 3, rx1 - rx0 - 110, lk.gaya.teks);
			Component cerita = e.teksAtau(LembarKunci.TEKS);
			if (cerita != null) {
				lk.teks(cerita, rx0 + 26, ry0 + 16, rx1 - rx0 - 34, lk.gaya.teksLemah);
			}
			lk.kanan(KotaGambar.t("hari", e.l(LembarKunci.HARI, 0L)), rx1 - 4, ry0 + 3, lk.gaya.teksSamar);
			if (kesan != 0) {
				lk.kanan(KotaGambar.t("kesan_ubah", Brush.bertanda(kesan)), rx1 - 60, ry0 + 3, kesan > 0 ? lk.gaya.naik() : lk.gaya.turun());
			}
		}, null, null);
	}

	// =================================================================== the family tree

	private static final class Simpul {
		final Lembar data;
		final int id;
		final int gen;
		final int ayah;
		final int ibu;
		final int pasangan;
		final boolean arsip;
		float x;
		float y;

		Simpul(Lembar data, int id, int gen, int ayah, int ibu, int pasangan, boolean arsip) {
			this.data = data;
			this.id = id;
			this.gen = gen;
			this.ayah = ayah;
			this.ibu = ibu;
			this.pasangan = pasangan;
			this.arsip = arsip;
		}

		Lembar data() { return data; }
		int id() { return id; }
		int gen() { return gen; }
		int ayah() { return ayah; }
		int ibu() { return ibu; }
		int pasangan() { return pasangan; }
		boolean arsip() { return arsip; }
	}

	private static final int SEL_W = 58;
	private static final int SEL_H = 52;
	private static final int JARAK_GEN = 64;

	private void gambarPohon(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> baris = lembar.daftar(Pohon.SIMPUL);
		if (baris.isEmpty()) {
			kosong(l, KotaGambar.t("pohon.kosong"), x0, y0, x1, y1);
			return;
		}
		int pusat = lembar.i(Pohon.PUSAT, 0);
		if (pusat != pusatTerakhir) {
			pusatTerakhir = pusat;
			panX = 0;
			panY = 0;
		}
		int bawah = y1 - 18;
		kotakX0 = x0;
		kotakY0 = y0;
		kotakX1 = x1;
		kotakY1 = bawah;
		// ---- layout by generation
		Map<Integer, Simpul> peta = new HashMap<>();
		Map<Integer, List<Simpul>> baris2 = new HashMap<>();
		int genMin = 0;
		int genMaks = 0;
		for (Lembar b : baris) {
			Simpul s = new Simpul(b, b.i(KotaLembar.WARGA, 0), b.i(Pohon.GEN, 0), b.i(Pohon.AYAH, 0), b.i(Pohon.IBU, 0), b.i(Pohon.PASANGAN, 0), b.b(Pohon.ARSIP));
			peta.put(s.id(), s);
			baris2.computeIfAbsent(s.gen(), g -> new ArrayList<>()).add(s);
			genMin = Math.min(genMin, s.gen());
			genMaks = Math.max(genMaks, s.gen());
		}
		Map<Integer, Float> xPos = new HashMap<>();
		for (int g = genMin; g <= genMaks; g++) {
			List<Simpul> row = baris2.getOrDefault(g, List.of());
			// order: by the mean x of the parents (already placed), then by birth; spouses stay adjacent
			List<Simpul> urut = new ArrayList<>(row);
			urut.sort((a, b) -> Float.compare(kunciUrut(a, xPos, peta), kunciUrut(b, xPos, peta)));
			List<Simpul> akhir = new ArrayList<>();
			for (Simpul s : urut) {
				if (akhir.contains(s)) {
					continue;
				}
				akhir.add(s);
				Simpul pas = peta.get(s.pasangan());
				if (pas != null && pas.gen() == g && !akhir.contains(pas)) {
					akhir.add(pas);
				}
			}
			float lebar = akhir.size() * SEL_W;
			float mulai = -lebar / 2.0F;
			// children are pulled under their parents when the row above has room: keep the mean aligned
			float geser = 0;
			if (g > genMin) {
				float jumlah = 0;
				int n = 0;
				for (int i = 0; i < akhir.size(); i++) {
					float anak = kunciUrut(akhir.get(i), xPos, peta);
					if (anak != Float.MAX_VALUE) {
						jumlah += anak - (mulai + i * SEL_W + SEL_W / 2.0F);
						n++;
					}
				}
				if (n > 0) {
					geser = jumlah / n;
				}
			}
			for (int i = 0; i < akhir.size(); i++) {
				Simpul s = akhir.get(i);
				s.x = mulai + i * SEL_W + SEL_W / 2.0F + geser;
				s.y = (g - genMin) * JARAK_GEN;
				xPos.put(s.id(), s.x);
			}
		}
		float cx = (x0 + x1) / 2.0F + panX;
		float tinggiTotal = (genMaks - genMin) * JARAK_GEN + SEL_H;
		float cy = y0 + Math.max(4.0F, ((bawah - y0) - tinggiTotal) / 2.0F) + panY;
		l.klip(x0, y0, x1, bawah);
		l.g.fill(x0, y0, x1, bawah, Palette.argb(0x18, 0x000000));
		// connectors first
		for (Simpul s : peta.values()) {
			Simpul pas = peta.get(s.pasangan());
			if (pas != null && pas.gen() == s.gen() && s.id() < pas.id()) {
				float ay = cy + s.y + 18;
				Brush.rect(l.g, cx + s.x + 10, ay - 1.5F, cx + pas.x - 10, ay - 0.5F, 0xFFC0504A);
				Brush.rect(l.g, cx + s.x + 10, ay + 0.5F, cx + pas.x - 10, ay + 1.5F, 0xFFC0504A);
			}
		}
		Map<Long, List<Simpul>> keluarga = new HashMap<>();
		for (Simpul s : peta.values()) {
			if (s.ayah() > 0 || s.ibu() > 0) {
				keluarga.computeIfAbsent(((long) s.ayah() << 32) | (s.ibu() & 0xFFFFFFFFL), k -> new ArrayList<>()).add(s);
			}
		}
		for (Map.Entry<Long, List<Simpul>> e : keluarga.entrySet()) {
			int ayah = (int) (e.getKey() >> 32);
			int ibu = (int) (long) e.getKey();
			Simpul a = peta.get(ayah);
			Simpul b = peta.get(ibu);
			if (a == null && b == null) {
				continue;
			}
			float px = a != null && b != null ? (a.x + b.x) / 2.0F : a != null ? a.x : b.x;
			float py = (a != null ? a.y : b.y) + 18;
			float busY = cy + (a != null ? a.y : b.y) + SEL_H - 8;
			int garis = Palette.argb(0xB0, l.gaya.teksSamar);
			Brush.rect(l.g, cx + px - 0.5F, cy + py, cx + px + 0.5F, busY, garis);
			float kiri = px;
			float kanan = px;
			for (Simpul c : e.getValue()) {
				kiri = Math.min(kiri, c.x);
				kanan = Math.max(kanan, c.x);
			}
			Brush.rect(l.g, cx + kiri - 0.5F, busY, cx + kanan + 0.5F, busY + 1.0F, garis);
			for (Simpul c : e.getValue()) {
				Brush.rect(l.g, cx + c.x - 0.5F, busY, cx + c.x + 0.5F, cy + c.y + 3, garis);
			}
		}
		for (Simpul s : peta.values()) {
			simpul(l, s, Math.round(cx + s.x), Math.round(cy + s.y), s.id() == pusat);
		}
		l.lepasKlip();
		// ---- footer buttons
		Simpul tengah = peta.get(pusat);
		int by = y1 - 15;
		int x = x0;
		Simpul orangTua = null;
		if (tengah != null) {
			orangTua = peta.get(tengah.ayah()) != null ? peta.get(tengah.ayah()) : peta.get(tengah.ibu());
		}
		Simpul anak = null;
		for (Simpul s : peta.values()) {
			if (tengah != null && (s.ayah() == tengah.id() || s.ibu() == tengah.id()) && (anak == null || s.x < anak.x)) {
				anak = s;
			}
		}
		final Simpul naik = orangTua;
		final Simpul turun = anak;
		Component lNaik = KotaGambar.t("pohon.naik");
		int w = lebarTombol(l, lNaik, 50, 90);
		aksi(l, "naik", x, by, w, 14, lNaik, naik != null, naik == null ? KotaGambar.t("pohon.tanpa_naik") : null,
				() -> kirim(LembarKunci.op(Pohon.OP_PUSAT, naik.id())));
		x += w + 4;
		Component lTurun = KotaGambar.t("pohon.turun");
		w = lebarTombol(l, lTurun, 50, 90);
		aksi(l, "turun", x, by, w, 14, lTurun, turun != null, turun == null ? KotaGambar.t("pohon.tanpa_turun") : null,
				() -> kirim(LembarKunci.op(Pohon.OP_PUSAT, turun.id())));
		x += w + 4;
		Component lKartu = KotaGambar.t("pohon.kartu");
		w = lebarTombol(l, lKartu, 50, 90);
		boolean hidup = tengah != null && !tengah.arsip();
		aksi(l, "kartu", x, by, w, 14, lKartu, hidup, hidup ? null : KotaGambar.t("pohon.arsip"),
				() -> kirim(LembarKunci.op(KotaLembar.OP_KARTU, pusat)));
		x += w + 4;
		if (tengah != null && tengah.arsip() && !tengah.data().sub(Pohon.MAKAM).kosongSemua()) {
			Lembar mk = tengah.data().sub(Pohon.MAKAM);
			Component lMakam = KotaGambar.t("pohon.makam", mk.teks(LembarKunci.NAMA), mk.i(Pohon.NOMOR, 0));
			w = lebarTombol(l, lMakam, 60, x1 - x - 2);
			tunjuk(l, "makam", x, by, w, 14, mk.i(LembarKunci.X, 0), mk.i(LembarKunci.Z, 0));
		}
	}

	private static float kunciUrut(Simpul s, Map<Integer, Float> xPos, Map<Integer, Simpul> peta) {
		Float a = xPos.get(s.ayah());
		Float b = xPos.get(s.ibu());
		if (a != null && b != null) {
			return (a + b) / 2.0F;
		}
		if (a != null) {
			return a;
		}
		if (b != null) {
			return b;
		}
		// a spouse married in: next to the partner
		Float p = xPos.get(s.pasangan());
		return p != null ? p + 1 : Float.MAX_VALUE;
	}

	private void simpul(Lukis l, Simpul s, int cx, int top, boolean pusat) {
		Lembar d = s.data();
		String status = d.s(KotaLembar.STATUS, KotaLembar.ST_HIDUP);
		int ring = KotaGambar.warnaStatus(status);
		int ps = 28;
		int px = cx - ps / 2;
		if (pusat) {
			Brush.halo(l.g, cx, top + ps / 2.0F, ps * 0.9F, 5.0F, 0xF0C040, 0.8F);
		}
		boolean klik = !s.arsip() && s.id() > 0;
		Runnable aksi = klik ? () -> kirim(LembarKunci.op(KotaLembar.OP_KARTU, s.id())) : null;
		l.zona("simpul#" + s.id(), px - 2, top - 2, px + ps + 2, top + SEL_H - 4, aksi, klik || s.arsip(), null, true);
		Potret.gambar(l, px, top, ps, d.i(LembarKunci.POTRET, -1), -1, false, 0.0F);
		if (s.arsip()) {
			l.g.fill(px + 2, top + 2, px + ps - 2, top + ps - 2, Palette.argb(0x70, 0x808080));
			Brush.text(l.g, l.font, Component.literal("✝"), cx - 3, top + ps / 2.0F - 4, 0xFFFFFFFF, 1.0F, true);
		}
		Brush.frame(l.g, px, top, px + ps, top + ps, pusat ? KotaGambar.EMAS : ring);
		if (d.i(KotaLembar.AKAR, 0) >= 2 && d.b(KotaLembar.AKAR_PASTI)) {
			IkonKota.gambar(l.g, IkonKota.AKAR, px + ps - 9, top - 3, 10);
		}
		Component nama = d.teks(LembarKunci.NAMA);
		l.tengah(nama, cx, top + ps + 2, SEL_W - 2, pusat ? l.gaya.aksen : l.gaya.teks);
		Component umur = s.arsip() ? KotaGambar.t("pohon.wafat_hari", d.l(Pohon.WAFAT, 0L)) : KotaGambar.t("pohon.umur", d.i(KotaLembar.UMUR, 0));
		l.tengah(umur, cx, top + ps + 12, SEL_W - 2, l.gaya.teksSamar);
		if (l.di(px - 2, top - 2, px + ps + 2, top + SEL_H - 4)) {
			List<Component> tip = new ArrayList<>();
			tip.add(Brush.bold(nama));
			tip.add(KotaGambar.umurKerja(d));
			if (s.arsip()) {
				tip.add(KotaGambar.t("pohon.arsip_ringkas", d.l(Pohon.LAHIR, 0L), d.l(Pohon.WAFAT, 0L)));
				Lembar mk = d.sub(Pohon.MAKAM);
				if (!mk.kosongSemua()) {
					tip.add(KotaGambar.t("pohon.makam_di", mk.teks(LembarKunci.NAMA), mk.i(Pohon.NOMOR, 0)));
				}
			} else if (d.ada(KotaLembar.LOKASI)) {
				tip.add(d.teks(KotaLembar.LOKASI));
			}
			l.tip(tip);
		}
	}
}
