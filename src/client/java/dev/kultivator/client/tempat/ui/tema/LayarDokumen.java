package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.Dokumen;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * {@code tema_dokumen} (DESIGN 38.T.3, quest {@code tema_pelabuhan_pelita} chapter 2): three cargo manifests on the
 * harbour master's desk, one of them forged. The customs ledger runs along the top; the manifests lie side by side on
 * their paper (one at a time with arrows on a narrow window). Hovering a line lights the same line in the ledger and in
 * the other manifests, a click circles it in red ink as suspicious (a note for the player only), and the chosen manifest
 * is accused with "Tandai palsu" (Space). The tries left are the harbour master's seals; a wrong accusation shakes the desk.
 */
public final class LayarDokumen extends LayarTemaDasar {

	private int pilih = -1;
	private int lihat;
	private final Set<String> curiga = new HashSet<>();
	private int percobaanLalu = -1;
	private long getarSejak = -1L;
	private String labelSorot = "";

	public LayarDokumen(int npcId, CompoundTag lembar) {
		super(Dokumen.LAYAR, npcId, lembar, "layar.dokumen", TempatWarna.Gaya.KERTAS);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of();
	}

	@Override
	protected int panelMaks(Tata t) {
		return t == Tata.LEBAR ? 580 : 470;
	}

	@Override
	protected Component petunjuk() {
		return TemaUi.k("dokumen.kunci");
	}

	@Override
	protected void lembarBaru(boolean buka) {
		int p = lembar.i(Dokumen.PERCOBAAN, 3);
		if (percobaanLalu >= 0 && p < percobaanLalu) {
			getarSejak = Util.getMillis();
			UiSound.denied();
		}
		percobaanLalu = p;
		if (buka) {
			pilih = -1;
			curiga.clear();
		}
	}

	@Override
	protected boolean angka(int n) {
		List<Lembar> surat = lembar.daftar(Dokumen.SURAT);
		if (n >= 1 && n <= surat.size()) {
			pilih = n - 1;
			lihat = n - 1;
			UiSound.tab();
			return true;
		}
		return false;
	}

	@Override
	protected boolean spasi() {
		tuduh();
		return true;
	}

	private boolean bisaTuduh() {
		List<Lembar> surat = lembar.daftar(Dokumen.SURAT);
		return pilih >= 0 && pilih < surat.size() && lembar.i(Dokumen.PERCOBAAN, 3) > 0 && tulisBoleh();
	}

	private void tuduh() {
		if (bisaTuduh()) {
			kirim(LembarKunci.op(Dokumen.OP_PALSU, lembar.daftar(Dokumen.SURAT).get(pilih).id()));
		}
	}

	@Override
	protected void gambar(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> surat = lembar.daftar(Dokumen.SURAT);
		List<Lembar> catatan = lembar.daftar(LembarKunci.CATATAN);
		boolean getar = getarSejak >= 0L && l.kini - getarSejak < 450L;
		int dx = getar ? Math.round((float) Math.sin(l.kini / 20.0D) * 2.5F) : 0;
		l.g.pose().pushMatrix();
		l.g.pose().translate(dx, 0.0F);
		String sorotTadi = labelSorot;
		labelSorot = "";
		int y = y0;
		y += buku(l, catatan, x0, y, x1, sorotTadi);
		int bawah = y1 - 20;
		if (surat.isEmpty()) {
			kosong(l, TemaUi.k("dokumen.kosong"), x0, y, x1, bawah);
		} else if (tata == Tata.SEMPIT) {
			lihat = Math.max(0, Math.min(lihat, surat.size() - 1));
			int ax = x0 + 14;
			panah(l, "kiri", x0, y + (bawah - y) / 2 - 7, IkonTempat.PANAH_KIRI, lihat > 0, () -> lihat--);
			panah(l, "kanan", x1 - 14, y + (bawah - y) / 2 - 7, IkonTempat.PANAH_KANAN, lihat < surat.size() - 1, () -> lihat++);
			lembarSurat(l, surat.get(lihat), lihat, ax + 2, y, x1 - 16, bawah, catatan, sorotTadi);
		} else {
			int n = Math.min(3, surat.size());
			int w = (x1 - x0 - (n - 1) * 5) / n;
			for (int i = 0; i < n; i++) {
				int sx = x0 + i * (w + 5);
				lembarSurat(l, surat.get(i), i, sx, y, sx + w, bawah, catatan, sorotTadi);
			}
		}
		l.g.pose().popMatrix();
		kaki(l, x0, y1 - 17, x1);
	}

	private void panah(Lukis l, String kunci, int x, int y, int ikon, boolean aktif, Runnable aksi) {
		IkonTempat.gambar(l.g, ikon, x, y, 14, aktif ? 0xFFFFFFFF : 0x60FFFFFF);
		l.zona(kunci, x, y, x + 14, y + 14, aksi, aktif, null, true);
		l.fokusCincin(kunci, x, y, x + 14, y + 14);
	}

	/** The customs ledger: a green-bound strip of label / value pairs. Returns the height used. */
	private int buku(Lukis l, List<Lembar> catatan, int x0, int y0, int x1, String sorot) {
		if (catatan.isEmpty()) {
			return 0;
		}
		int kolom = tata == Tata.SEMPIT ? 2 : 3;
		int baris = (catatan.size() + kolom - 1) / kolom;
		int h = 16 + baris * 10;
		l.g.fill(x0, y0, x1, y0 + h, 0xFFE8E0C4);
		l.g.fill(x0, y0, x0 + 5, y0 + h, 0xFF2E5A3E);
		l.g.fill(x0 + 5, y0, x0 + 6, y0 + h, 0xFF183A26);
		Brush.frame(l.g, x0, y0, x1, y0 + h, 0x80403020);
		TemaUi.ikon(l.g, TemaUi.I_CAP, x0 + 8, y0 + 2, 11);
		l.teks(Brush.bold(TemaUi.k("dokumen.buku")), x0 + 21, y0 + 3, x1 - x0 - 26, 0xFF2E3A2E, 0.8F);
		int cw = (x1 - x0 - 12) / kolom;
		for (int i = 0; i < catatan.size(); i++) {
			Lembar c = catatan.get(i);
			int cx = x0 + 9 + (i % kolom) * cw;
			int cy = y0 + 14 + (i / kolom) * 10;
			String label = c.teks(Dokumen.LABEL).getString();
			boolean lit = label.equals(sorot);
			if (lit) {
				l.g.fill(cx - 2, cy - 1, cx + cw - 3, cy + 9, 0x60F7DC7E);
			}
			l.teks(c.teks(Dokumen.LABEL), cx, cy, cw * 0.42F, 0xFF5A5040, 0.7F);
			l.teks(c.teks(Dokumen.ISI), cx + cw * 0.44F, cy, cw * 0.54F, 0xFF1B2521, 0.7F);
		}
		return h + 5;
	}

	/** One manifest on its paper: header, ruled lines, suspicious circles, the selection and the seal. */
	private void lembarSurat(Lukis l, Lembar s, int i, int x0, int y0, int x1, int y1, List<Lembar> catatan, String sorot) {
		boolean dipilih = i == pilih;
		float angkat = dipilih ? 2.0F : 0.0F;
		int ay = y0 + 2 - Math.round(angkat);
		l.g.fill(x0 + 2, y0 + 4, x1 + 2, y1 + 2, 0x40000000);
		TemaUi.surat(l.g, x0, ay, x1, y1 - 2);
		if (dipilih) {
			Brush.frame(l.g, x0 - 1, ay - 1, x1 + 1, y1 - 1, TempatWarna.SEGEL);
			Brush.halo(l.g, (x0 + x1) / 2.0F, ay, 3.0F, 1.0F, TempatWarna.SEGEL & 0xFFFFFF, l.denyut());
		}
		int ink = 0xFF2A2218;
		int ix0 = x0 + 7;
		int ix1 = x1 - 7;
		int y = ay + 6;
		Brush.fitCentered(l.g, font, Brush.bold(s.teks(LembarKunci.JUDUL).getString().isEmpty() ? TemaUi.k("dokumen.surat_no", i + 1)
				: s.teks(LembarKunci.JUDUL)), (ix0 + ix1) / 2.0F, y, ix1 - ix0, ink, 0.85F, 0.6F, false);
		y += 10;
		l.g.fill(ix0, y, ix1, y + 1, 0x80604830);
		y += 4;
		List<Lembar> baris = s.daftar(Dokumen.BARIS);
		int lineH = Math.max(11, Math.min(20, (y1 - y - 26) / Math.max(1, baris.size())));
		for (int r = 0; r < baris.size() && y + lineH <= y1 - 22; r++) {
			Lembar b = baris.get(r);
			String label = b.teks(Dokumen.LABEL).getString();
			String kunci = s.id() + ":" + label;
			boolean over = l.di(ix0 - 2, y - 1, ix1 + 2, y + lineH - 1);
			if (over) {
				labelSorot = label;
			}
			if (label.equals(sorot)) {
				// the same line lit on every paper and in the ledger; the player compares, the screen never tells
				l.g.fill(ix0 - 2, y - 1, ix1 + 2, y + lineH - 2, cari(catatan, label) != null ? 0x50F7DC7E : 0x30F7DC7E);
			}
			l.teks(b.teks(Dokumen.LABEL), ix0, y, (ix1 - ix0) * 0.38F, 0xFF6A5A44, 0.7F);
			l.teks(b.teks(Dokumen.ISI), ix0 + (ix1 - ix0) * 0.4F, y, (ix1 - ix0) * 0.6F, ink, 0.75F);
			l.g.fill(ix0, y + lineH - 3, ix1, y + lineH - 2, 0x30604830);
			if (curiga.contains(kunci)) {
				lingkaranTinta(l, ix0 + (ix1 - ix0) * 0.4F - 2, y - 2, ix1 + 1, y + lineH - 3);
			}
			l.zona("baris:" + kunci, ix0 - 2, y - 1, ix1 + 2, y + lineH - 1, () -> {
				if (!curiga.remove(kunci)) {
					curiga.add(kunci);
				}
				UiSound.toggle(curiga.contains(kunci));
			}, true, null, false);
			y += lineH;
		}
		// the manifest's own seal and the pick zone
		int sealY = y1 - 22;
		IkonTempat.stempel(l.g, IkonTempat.Stempel.BULAT, x1 - 26, sealY - 2, 18, 18, Palette.argb(0xB0, 0xB3281D));
		Brush.text(l.g, font, Component.literal(Integer.toString(i + 1)), x0 + 7, sealY + 4, 0xFF6A5A44, 0.75F, false);
		Component pilihTeks = TemaUi.k(dipilih ? "dokumen.dipilih" : "dokumen.pilih");
		Brush.fitCentered(l.g, font, pilihTeks, (x0 + x1) / 2.0F, sealY + 5, x1 - x0 - 50, dipilih ? TempatWarna.SEGEL : 0xFF6A5A44, 0.75F, 0.6F,
				false);
		final int idx = i;
		l.zona("surat" + i, x0, sealY - 4, x1, y1 - 2, () -> pilih = idx == pilih ? -1 : idx, true, null, true);
		l.fokusCincin("surat" + i, x0, sealY - 4, x1, y1 - 2);
	}

	private static Lembar cari(List<Lembar> catatan, String label) {
		for (Lembar c : catatan) {
			if (c.teks(Dokumen.LABEL).getString().equals(label)) {
				return c;
			}
		}
		return null;
	}

	/** A hand-drawn red ink ellipse around a value. */
	private static void lingkaranTinta(Lukis l, float x0, float y0, float x1, float y1) {
		float cx = (x0 + x1) / 2.0F;
		float cy = (y0 + y1) / 2.0F;
		float rx = (x1 - x0) / 2.0F;
		float ry = (y1 - y0) / 2.0F + 1.0F;
		float px = cx + rx;
		float py = cy;
		for (int i = 1; i <= 26; i++) {
			double a = i / 24.0D * Math.PI * 2.0D;
			float wob = 1.0F + 0.06F * (float) Math.sin(i * 1.7D);
			float nx = cx + (float) Math.cos(a) * rx * wob;
			float ny = cy + (float) Math.sin(a) * ry * wob;
			Brush.line(l.g, px, py, nx, ny, 1.0F, 0xD0B3281D);
			px = nx;
			py = ny;
		}
	}

	/** Footer: tries as seals, the hint and the accuse button. */
	private void kaki(Lukis l, int x0, int y, int x1) {
		int sisa = lembar.i(Dokumen.PERCOBAAN, 3);
		l.keterangan(TemaUi.k("dokumen.percobaan"), x0, y + 3, 52, gaya.teksLemah);
		for (int i = 0; i < 3; i++) {
			int cx = x0 + 54 + i * 14;
			IkonTempat.stempel(l.g, IkonTempat.Stempel.BULAT, cx, y, 12, 12, i < sisa ? Palette.argb(0xE0, 0xB3281D) : 0x40808080);
		}
		int bw = Math.min(140, (x1 - x0) / 2);
		boolean bisa = bisaTuduh();
		Component alasan = sisa <= 0 ? TemaUi.k("dokumen.habis") : pilih < 0 ? TemaUi.k("dokumen.pilih_dulu") : null;
		tombol.gambar(l, "palsu", x1 - bw, y - 1, bw, 15, TemaUi.k("dokumen.tandai"), bisa, bisa ? null : alasan != null ? alasan : alasanBaca(),
				this::tuduh, bisa, false);
		if (!curiga.isEmpty()) {
			l.keterangan(TemaUi.k("dokumen.curiga", curiga.size()), x0 + 100, y + 3, x1 - bw - x0 - 104, TempatWarna.SEGEL);
		} else {
			l.keterangan(TemaUi.k("dokumen.tips"), x0 + 100, y + 3, x1 - bw - x0 - 104, gaya.teksSamar);
		}
	}
}
