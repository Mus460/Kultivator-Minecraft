package dev.kultivator.client.dunia.kota42.ui.layar;

import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarPilihan;
import dev.kultivator.client.screen.ui.GulirIsi;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Dialog;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The 42 half of the one dialog screen (DESIGN 42.11.1; {@code LayarDialog} calls it when the sheet is a conversation of a
 * townsperson, {@link Dialog#CAKAP}): the speaker strip (mood face, age and job, the bond heart, whether the person knows the
 * player's name), the tone picker (six chips; Shift+1..6 pick), the numbered options with their promise mark, a face cue
 * under the line, and the tablets Kabar (gossip, "who said so", spread the sect's news), Jalan (directions: services,
 * people, other places, each with a compass arrow, distance and "Tandai"), Hubungan (impression, debt, the strongest
 * memories, the person's card), Tolong (requests with their goals) and Beri (the item in hand and a hint at taste). Draws
 * only; ops go through the host's callback, which also checks read-only and the remote reason.
 */
public final class DialogKota {

	private final Consumer<String> kirim;
	private final BooleanSupplier tulis;
	private final Supplier<Component> alasanBaca;
	private final GulirIsi gKabar = new GulirIsi();
	private final GulirIsi gJalan = new GulirIsi();
	private final GulirIsi gHubungan = new GulirIsi();
	private final GulirIsi gTolong = new GulirIsi();
	private String nada = Dialog.NADA_SOPAN;

	public DialogKota(Consumer<String> kirim, BooleanSupplier tulis, Supplier<Component> alasanBaca) {
		this.kirim = kirim;
		this.tulis = tulis;
		this.alasanBaca = alasanBaca;
	}

	/** True when the sheet is a 42 conversation. */
	public static boolean cakap(Lembar lembar) {
		return lembar.b(Dialog.CAKAP);
	}

	public void tabBerubah() {
		gKabar.atas();
		gJalan.atas();
		gHubungan.atas();
		gTolong.atas();
	}

	public boolean roda(String tab, double mx, double my, double dy) {
		return scroll(tab).roda(mx, my, dy);
	}

	public boolean tombol(String tab, KeyEvent event) {
		return scroll(tab).tombol(event);
	}

	private GulirIsi scroll(String tab) {
		return switch (tab) {
			case Dialog.TAB_KABAR -> gKabar;
			case Dialog.TAB_JALAN -> gJalan;
			case Dialog.TAB_TOLONG -> gTolong;
			default -> gHubungan;
		};
	}

	private String opSah(String op) {
		return op;
	}

	private boolean tombolOp(Lukis l, String kunci, int x, int y, int w, int h, Component label, boolean aktif, @Nullable Component alasan, String op) {
		boolean baca = !tulis.getAsBoolean() && !LembarKunci.opBaca(LembarKunci.verb(op));
		return TalismanButton.gambar(l, kunci, x, y, w, h, label, aktif && !baca, baca ? alasanBaca.get() : alasan, () -> kirim.accept(opSah(op)));
	}

	// =================================================================== speaker strip, tone, options

	/** The speaker line over the bubble; returns the height it takes (0 when the sheet has no speaker). */
	public int kepala(Lukis l, Lembar npc, int x0, int y, int x1) {
		if (npc.kosongSemua()) {
			return 0;
		}
		String wajah = npc.s(KotaLembar.WAJAH);
		int x = x0 + 4;
		if (!wajah.isEmpty() && !KotaLembar.WAJAH_NETRAL.equals(wajah)) {
			KotaGambar.wajah(l, wajah, x, y - 1, 11);
			x += 14;
		}
		Component umurKerja = KotaGambar.umurKerja(npc);
		String akrab = npc.s(KotaLembar.AKRAB, KotaLembar.AKRAB_ASING);
		Component label = KotaGambar.t("akrab." + akrab, npc.i(KotaLembar.KESAN, 0));
		boolean tahu = npc.b(KotaLembar.TAHU_NAMA);
		Component nama = KotaGambar.t(tahu ? "dialog.tahu_nama" : "dialog.belum_tahu");
		int kananLebar = 14 + l.font.width(label) + 8 + l.font.width(nama);
		boolean sempit = x1 - x - kananLebar < 70;
		l.teks(umurKerja, x, y, sempit ? x1 - x : x1 - x - kananLebar - 6, l.gaya.teksLemah);
		int ky = sempit ? y + 11 : y;
		int kx = sempit ? x0 + 4 : x1 - kananLebar - 2;
		KotaGambar.hatiAkrab(l, npc, kx, ky, 9);
		l.teks(label, kx + 13, ky, 120, l.gaya.teks);
		l.teks(nama, kx + 13 + l.font.width(label) + 8, ky, 120, tahu ? l.gaya.teksLemah : l.gaya.teksSamar);
		return sempit ? 24 : 13;
	}

	/** The tone picker; returns the height used. */
	public int nada(Lukis l, Lembar lembar, int x0, int y, int x1) {
		Lembar n = lembar.sub(Dialog.NADA);
		List<Lembar> daftar = n.daftar(LembarKunci.DAFTAR);
		if (daftar.isEmpty()) {
			return 0;
		}
		String bawaan = n.s(Dialog.BAWAAN, Dialog.NADA_SOPAN);
		boolean ada = false;
		for (Lembar e : daftar) {
			ada |= e.id().equals(nada) && e.b(LembarKunci.BISA);
		}
		if (!ada) {
			nada = bawaan;
		}
		l.teks(KotaGambar.t("dialog.nada"), x0, y + 3, 40, l.gaya.teksSamar);
		int x = x0 + Math.min(44, l.font.width(KotaGambar.t("dialog.nada")) + 4);
		int sisa = x1 - x;
		int w = Math.max(30, Math.min(70, (sisa - 3 * (daftar.size() - 1)) / daftar.size()));
		for (int i = 0; i < daftar.size(); i++) {
			Lembar e = daftar.get(i);
			boolean bisa = e.b(LembarKunci.BISA);
			Component label = KotaGambar.t("nada." + e.id());
			Component alasan = bisa ? null : e.teksAtau(LembarKunci.ALASAN);
			Component tip = bisa ? KotaGambar.t("dialog.nada_tip", i + 1, e.ada(Dialog.PELUANG) ? e.i(Dialog.PELUANG, 0) + " %" : "—") : alasan;
			final String id = e.id();
			TalismanButton.gambar(l, "nada#" + id, x + i * (w + 3), y, w, 14, label, bisa, tip, () -> nada = id, false, id.equals(nada));
		}
		return 18;
	}

	/** Shift + digit: picks tone {@code n} (1-6); true when a tone was picked. */
	public boolean pilihNada(Lembar lembar, int n) {
		List<Lembar> daftar = lembar.sub(Dialog.NADA).daftar(LembarKunci.DAFTAR);
		if (n < 1 || n > daftar.size() || !daftar.get(n - 1).b(LembarKunci.BISA)) {
			return false;
		}
		nada = daftar.get(n - 1).id();
		return true;
	}

	/** The options with the promise mark and the niat tag. */
	public List<DaftarPilihan.Pilihan> pilihan(Lembar lembar) {
		List<DaftarPilihan.Pilihan> out = new ArrayList<>();
		List<Lembar> rows = lembar.daftar(LembarKunci.Dialog.PILIHAN);
		for (int i = 0; i < rows.size(); i++) {
			Lembar p = rows.get(i);
			Component alasan = p.teksAtau(LembarKunci.ALASAN);
			Component kunci = p.b(LembarKunci.TERKUNCI) ? (alasan != null ? alasan : Component.translatable("kultivator.gui43.kit.terkunci")) : null;
			Component teks = p.teks(LembarKunci.TEKS);
			if (p.b(Dialog.JANJI)) {
				teks = Component.literal("★ ").withColor(0xFFF0C040).append(teks);
			}
			out.add(new DaftarPilihan.Pilihan(p.i(LembarKunci.NO, i + 1), teks, kunci, false));
		}
		return out;
	}

	/** {@code ucap:NO:NADA} (the tone is left out when it is the polite default). */
	public String opUcap(int no) {
		return Dialog.NADA_SOPAN.equals(nada) ? LembarKunci.op(Dialog.OP_UCAP, no) : LembarKunci.op(Dialog.OP_UCAP, no, nada);
	}

	/** The face cue under the line ("Dia tampak tidak sabar"); returns the height used. */
	public int raut(Lukis l, Lembar lembar, int x0, int y, int x1) {
		Component raut = lembar.teksAtau(Dialog.RAUT);
		if (raut == null) {
			return 0;
		}
		l.teks(Component.literal("« ").append(raut).append(" »"), x0, y, x1 - x0, l.gaya.teksSamar);
		return 12;
	}

	// =================================================================== Kabar

	public void kabar(Lukis l, Lembar lembar, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Dialog.KABAR);
		Lembar sebar = lembar.sub(Dialog.SEBAR);
		int bawah = y1;
		if (!sebar.kosongSemua()) {
			bawah = y1 - 17;
			Component label = KotaGambar.t("dialog.sebar", sebar.l(LembarKunci.BIAYA, 5L));
			int w = TalismanButton.lebar(l.font, label, 90, x1 - x0);
			tombolOp(l, "sebar", x0, bawah + 3, w, 14, label, sebar.b(LembarKunci.BISA), sebar.teksAtau(LembarKunci.ALASAN), Dialog.OP_SEBAR);
		}
		int xr = x1 - 6;
		int y = gKabar.mulai(l, x0, y0, xr, bawah);
		if (rows.isEmpty()) {
			l.teks(KotaGambar.t("dialog.kabar_kosong"), x0, y + 4, xr - x0, l.gaya.teksSamar);
			gKabar.selesai(l, y + 20);
			return;
		}
		for (int i = 0; i < rows.size(); i++) {
			Lembar k = rows.get(i);
			int tinggi = 14 + (int) Math.ceil(Brush.wrapHeight(l.font, k.teks(LembarKunci.TEKS), xr - x0 - 14, 1.0F)) + (k.b(Dialog.TANYA) ? 18 : 0);
			l.kartu(x0, y, xr, y + tinggi, 0.0F, false);
			Brush.rect(l.g, x0 + 4, y + 4, x0 + 8, y + 12, Palette.SEAL);
			float h = l.bungkus(k.teks(LembarKunci.TEKS), x0 + 12, y + 3, xr - x0 - 18, l.gaya.teks);
			int cy = y + 4 + Math.round(h);
			List<Lembar> jejak = k.daftar(Dialog.JEJAK);
			if (!jejak.isEmpty()) {
				StringBuilder sb = new StringBuilder();
				for (Lembar j : jejak) {
					if (sb.length() > 0) {
						sb.append(" ← ");
					}
					sb.append(j.teks(LembarKunci.NAMA).getString());
				}
				l.teks(KotaGambar.t("dialog.jejak", sb.toString()), x0 + 12, cy, xr - x0 - 90, l.gaya.teksSamar);
			}
			l.kanan(KotaGambar.t("hari", k.l(LembarKunci.HARI, 0L)), xr - 4, cy, l.gaya.teksSamar);
			if (k.b(Dialog.TANYA)) {
				Component t = KotaGambar.t("dialog.tanya_sumber");
				int w = TalismanButton.lebar(l.font, t, 100, 190);
				tombolOp(l, "tanya#" + i, x0 + 12, y + tinggi - 17, w, 14, t, true, null, LembarKunci.op(Dialog.OP_TANYA_SUMBER, k.id()));
			}
			y += tinggi + 3;
		}
		gKabar.selesai(l, y);
	}

	// =================================================================== Jalan

	public void jalan(Lukis l, Lembar lembar, int x0, int y0, int x1, int y1) {
		Lembar j = lembar.sub(Dialog.JALAN);
		int xr = x1 - 6;
		int y = gJalan.mulai(l, x0, y0, xr, y1);
		if (j.kosongSemua()) {
			l.teks(KotaGambar.t("dialog.jalan_kosong"), x0, y + 4, xr - x0, l.gaya.teksSamar);
			gJalan.selesai(l, y + 20);
			return;
		}
		if (!j.b(Dialog.TAHU)) {
			l.bungkus(j.teks(LembarKunci.TEKS), x0 + 4, y + 4, xr - x0 - 8, l.gaya.teksLemah);
			gJalan.selesai(l, y + 40);
			return;
		}
		long biaya = j.l(LembarKunci.BIAYA, 0L);
		if (biaya > 0) {
			l.teks(KotaGambar.t("dialog.jalan_biaya", biaya), x0, y, xr - x0, Palette.PERINGATAN);
			y += 12;
		}
		y = daftarJalan(l, j.daftar(Dialog.LAYANAN), "dialog.jalan_layanan", x0, y, xr, "layanan");
		y = daftarJalan(l, j.daftar(Dialog.ORANG), "dialog.jalan_orang", x0, y, xr, "orang");
		y = daftarJalan(l, j.daftar(Dialog.TEMPAT_LAIN), "dialog.jalan_tempat", x0, y, xr, "tempat");
		gJalan.selesai(l, y);
	}

	private int daftarJalan(Lukis l, List<Lembar> rows, String judulKunci, int x0, int y, int xr, String awalan) {
		if (rows.isEmpty()) {
			return y;
		}
		y += l.kepala(KotaGambar.t(judulKunci), x0, y, xr);
		for (int i = 0; i < rows.size(); i++) {
			Lembar r = rows.get(i);
			l.kartu(x0, y, xr, y + 30, 0.0F, false);
			panah(l, x0 + 4, y + 7, 16, r.i(KotaLembar.ARAH, 0));
			l.teks(Brush.bold(r.teks(LembarKunci.NAMA)), x0 + 26, y + 3, xr - x0 - 100, l.gaya.teks);
			Component sampai = r.teksAtau(KotaLembar.SAMPAI);
			Component baris = KotaGambar.t("dialog.jalan_jarak", r.i(LembarKunci.JARAK, 0), r.teksAtau(KotaLembar.PATOKAN) == null ? Component.empty() : r.teks(KotaLembar.PATOKAN));
			l.teks(sampai != null ? KotaGambar.t("dialog.jalan_sampai", baris, sampai) : baris, x0 + 26, y + 15, xr - x0 - 100, l.gaya.teksLemah);
			Component teks = r.teksAtau(LembarKunci.TEKS);
			if (teks != null && l.di(x0, y, xr, y + 30)) {
				l.tip(teks);
			}
			int w = 62;
			tombolOp(l, awalan + "#" + i, xr - w - 4, y + 8, w, 14, KotaGambar.t("dialog.tandai"), r.i(LembarKunci.X, 0) != 0 || r.i(LembarKunci.Z, 0) != 0, null,
					LembarKunci.op(Dialog.OP_TANDAI_JALAN, r.id()));
			y += 33;
		}
		return y + 3;
	}

	/** A small compass arrow pointing at sector {@code arah} (0 = north, clockwise, 8 sectors). */
	private static void panah(Lukis l, int x, int y, int size, int arah) {
		float cx = x + size / 2.0F;
		float cy = y + size / 2.0F;
		Brush.disc(l.g, cx, cy, size / 2.0F, Palette.argb(0x60, 0x000000));
		double a = Math.toRadians(arah * 45.0D);
		float dx = (float) Math.sin(a);
		float dy = (float) -Math.cos(a);
		float r = size * 0.38F;
		Brush.line(l.g, cx - dx * r * 0.6F, cy - dy * r * 0.6F, cx + dx * r, cy + dy * r, 1.4F, 0xFFF0C040);
		Brush.disc(l.g, cx + dx * r, cy + dy * r, 1.8F, 0xFFFFF0C0);
	}

	// =================================================================== Hubungan

	public void hubungan(Lukis l, Lembar lembar, int x0, int y0, int x1, int y1) {
		Lembar h = lembar.sub(Dialog.HUBUNGAN);
		int xr = x1 - 6;
		int y = gHubungan.mulai(l, x0, y0, xr, y1);
		if (h.kosongSemua()) {
			l.teks(KotaGambar.t("dialog.hubungan_kosong"), x0, y + 4, xr - x0, l.gaya.teksSamar);
			gHubungan.selesai(l, y + 20);
			return;
		}
		int kesan = h.i(KotaLembar.KESAN, 0);
		int lw = Math.min(90, (xr - x0) / 3);
		l.teks(KotaGambar.t("kartu.kesan"), x0, y, lw - 4, l.gaya.teksLemah);
		Component kn = KotaGambar.tanda(kesan);
		l.kanan(kn, xr, y, l.gaya.teks);
		Bilah.gambar(l, x0 + lw, y + 3, xr - l.font.width(kn) - 6, (kesan + 100) / 200.0F, kesan >= 0 ? 0xFFE0506A : 0xFF8A1A1A, new float[] {0.5F});
		y += 12;
		y += KotaGambar.baris(l, KotaGambar.t("kartu.kenal"), KotaGambar.t("dialog.kenal_n", h.i(KotaLembar.KENAL, 0)), x0, y, xr, l.gaya.teks);
		long utang = h.l(Dialog.UTANG, 0L);
		y += KotaGambar.baris(l, KotaGambar.t("kartu.utang"), utang == 0 ? KotaGambar.t("kartu.utang_nol") : utang > 0 ? KotaGambar.t("kartu.utang_dia", Brush.angka(utang))
				: KotaGambar.t("kartu.utang_kamu", Brush.angka(-utang)), x0, y, xr, l.gaya.teks);
		y += 3;
		List<Lembar> kn2 = h.daftar(Dialog.KENANGAN);
		if (!kn2.isEmpty()) {
			y += l.kepala(KotaGambar.t("dialog.kenangan"), x0, y, xr);
			for (Lembar k : kn2) {
				IkonKota.gambar(l.g, IkonKota.kenangan(k.id()), x0, y - 2, 14);
				Component nama = k.teks(LembarKunci.NAMA);
				l.teks(k.b(Dialog.KUAT) ? Brush.bold(nama) : nama, x0 + 17, y, xr - x0 - 80, l.gaya.teks);
				l.kanan(KotaGambar.t("hari", k.l(LembarKunci.HARI, 0L)), xr, y, l.gaya.teksSamar);
				y += 14;
			}
		}
		y += 4;
		int x = x0;
		for (Lembar a : h.daftar(KotaLembar.AKSI)) {
			Component label = a.teks(LembarKunci.NAMA);
			int w = TalismanButton.lebar(l.font, label, 50, 110);
			if (x + w > xr && x > x0) {
				x = x0;
				y += 17;
			}
			String op = a.ada(LembarKunci.OP) ? a.s(LembarKunci.OP) : a.id();
			tombolOp(l, "aksi#" + a.id(), x, y, w, 14, label, a.b(LembarKunci.BISA), a.teksAtau(LembarKunci.ALASAN), op);
			x += w + 4;
		}
		Component kartu = KotaGambar.t("dialog.buka_kartu");
		int kw = TalismanButton.lebar(l.font, kartu, 70, 130);
		if (x + kw > xr && x > x0) {
			x = x0;
			y += 17;
		}
		tombolOp(l, "kartu", x, y, kw, 14, kartu, true, null, Dialog.OP_KARTU);
		gHubungan.selesai(l, y + 18);
	}

	// =================================================================== Tolong

	public void tolong(Lukis l, Lembar lembar, int x0, int y0, int x1, int y1) {
		List<Lembar> rows = lembar.daftar(Dialog.TOLONG);
		int xr = x1 - 6;
		int y = gTolong.mulai(l, x0, y0, xr, y1);
		if (rows.isEmpty()) {
			l.teks(KotaGambar.t("dialog.tolong_kosong"), x0, y + 4, xr - x0, l.gaya.teksSamar);
			gTolong.selesai(l, y + 20);
			return;
		}
		for (int i = 0; i < rows.size(); i++) {
			Lembar t = rows.get(i);
			String keadaan = t.s(LembarKunci.KEADAAN);
			boolean diterima = Dialog.TL_DITERIMA.equals(keadaan);
			boolean selesai = Dialog.TL_SELESAI.equals(keadaan);
			List<Lembar> tujuan = t.daftar(Dialog.TUJUAN);
			int tinggi = 38 + (int) Math.ceil(Brush.wrapHeight(l.font, t.teks(LembarKunci.TEKS), xr - x0 - 14, 1.0F)) + tujuan.size() * 12;
			l.kartu(x0, y, xr, y + tinggi, 0.0F, false);
			l.teks(Brush.bold(t.teks(LembarKunci.NAMA)), x0 + 6, y + 3, xr - x0 - 90, l.gaya.aksen);
			Component status = KotaGambar.t("dialog.tolong." + (selesai ? "selesai" : diterima ? "diterima" : keadaan.equals(LembarKunci.K_BATAS) ? "batas" : "baru"));
			l.kanan(status, xr - 4, y + 3, selesai ? l.gaya.naik() : diterima ? KotaGambar.EMAS : l.gaya.teksLemah);
			float h = l.bungkus(t.teks(LembarKunci.TEKS), x0 + 6, y + 15, xr - x0 - 14, l.gaya.teks);
			int cy = y + 17 + Math.round(h);
			for (Lembar g : tujuan) {
				boolean ok = g.b("selesai");
				l.g.fill(x0 + 6, cy + 1, x0 + 14, cy + 9, ok ? 0xFF2E8963 : Palette.argb(0x50, 0x000000));
				if (ok) {
					Brush.text(l.g, l.font, Component.literal("✓"), x0 + 7, cy + 1, 0xFFFFFFFF, 1.0F, false);
				}
				l.teks(g.teks(LembarKunci.TEKS), x0 + 18, cy, xr - x0 - 90, ok ? l.gaya.teksSamar : l.gaya.teks);
				if (g.i(LembarKunci.MAKS, 0) > 1) {
					l.kanan(Component.literal(g.i(LembarKunci.KEMAJUAN, 0) + " / " + g.i(LembarKunci.MAKS, 0)), xr - 4, cy, l.gaya.teksLemah);
				}
				cy += 12;
			}
			Component imbalan = t.teksAtau(Dialog.IMBALAN);
			long nb = t.l(LembarKunci.NB, 0L);
			l.teks(KotaGambar.t("dialog.imbalan", imbalan == null ? Component.empty() : imbalan, Brush.angka(nb)), x0 + 6, y + tinggi - 15, xr - x0 - 170, KotaGambar.EMAS);
			int bx = xr - 4;
			if (diterima) {
				Component s = KotaGambar.t("dialog.serahkan");
				int w = TalismanButton.lebar(l.font, s, 60, 100);
				bx -= w;
				tombolOp(l, "serahkan#" + i, bx, y + tinggi - 17, w, 14, s, true, null, LembarKunci.op(Dialog.OP_SERAHKAN_TOLONG, t.id()));
			} else if (!selesai) {
				Component s = KotaGambar.t("dialog.terima");
				int w = TalismanButton.lebar(l.font, s, 50, 90);
				bx -= w;
				tombolOp(l, "terima#" + i, bx, y + tinggi - 17, w, 14, s, !LembarKunci.K_BATAS.equals(keadaan), t.teksAtau(LembarKunci.ALASAN),
						LembarKunci.op(Dialog.OP_TERIMA_TOLONG, t.id()));
			}
			y += tinggi + 3;
		}
		gTolong.selesai(l, y);
	}

	// =================================================================== Beri

	public void beri(Lukis l, Lembar lembar, int x0, int y0, int x1, int y1) {
		Lembar b = lembar.sub(Dialog.BERI);
		if (b.kosongSemua()) {
			l.teks(KotaGambar.t("dialog.beri_kosong"), x0, y0 + 4, x1 - x0, l.gaya.teksSamar);
			return;
		}
		int y = y0 + 2;
		Lembar tangan = b.sub(LembarKunci.TANGAN);
		ItemStack item = tangan.kosongSemua() ? ItemStack.EMPTY : tangan.barang();
		l.sumur(x0, y, x0 + 40, y + 40);
		if (!item.isEmpty()) {
			l.g.pose().pushMatrix();
			l.g.pose().translate(x0 + 4, y + 4);
			l.g.pose().scale(2.0F, 2.0F);
			l.g.item(item, 0, 0);
			l.g.pose().popMatrix();
			if (l.di(x0, y, x0 + 40, y + 40)) {
				l.tip(item.getHoverName());
			}
		} else {
			Brush.text(l.g, l.font, Component.literal("·  ·  ·"), x0 + 8, y + 16, l.gaya.teksSumur(), 1.0F, false);
		}
		int tx = x0 + 48;
		l.teks(Brush.bold(item.isEmpty() ? KotaGambar.t("dialog.tangan_kosong") : item.getHoverName()), tx, y + 3, x1 - tx, l.gaya.aksen);
		Component petunjuk = b.teksAtau(Dialog.PETUNJUK);
		if (petunjuk != null) {
			l.bungkus(petunjuk, tx, y + 17, x1 - tx, l.gaya.teksLemah, 26.0F);
		}
		y += 48;
		long berikut = b.l(Dialog.BERIKUT, 0L);
		if (berikut > 0 && !b.b(LembarKunci.BISA)) {
			l.teks(KotaGambar.t("dialog.beri_berikut", berikut), x0, y, x1 - x0, l.gaya.teksSamar);
			y += 12;
		}
		Component beri = KotaGambar.t("dialog.beri");
		int w = TalismanButton.lebar(l.font, beri, 80, 140);
		tombolOp(l, "beri", x0, y, w, 14, beri, b.b(LembarKunci.BISA) && !item.isEmpty(), item.isEmpty() ? KotaGambar.t("dialog.tangan_kosong_alasan") : b.teksAtau(LembarKunci.ALASAN),
				Dialog.OP_BERI);
	}
}
