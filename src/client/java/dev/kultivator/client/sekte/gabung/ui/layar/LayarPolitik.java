package dev.kultivator.client.sekte.gabung.ui.layar;

import static dev.kultivator.client.sekte.gabung.ui.layar.LayarGabung2.pk;
import static dev.kultivator.client.sekte.gabung.ui.layar.LayarGabung2.u;

import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.DaftarSekte;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.sekte.gabung.ui.IkonGabung;
import dev.kultivator.client.screen.ui.Lembar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import dev.kultivator.sekte.gabung.ui.GabungLembar.Politik;
import dev.kultivator.sekte.gabung.ui.LembarPolitik;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;

import java.util.List;

/**
 * {@code gabung_politik} (DESIGN 41.8, 41.10.8): the council of the player's sect. Majelis: master, vice master and heir
 * (seat holders, players marked), the elders with their office and attitude toward the player, the sect's resource
 * toward the next class, its ranking; a player master sets the course (Netral / Damai / Agresif) and appoints, any
 * member may propose a fellow Inti member as elder. Suksesi: "Jubah dan Mangkuk" as three seals, the secret vote, the
 * next step with its PK, the heir election (stage, finalists, register, duel). Perang: score bar, days left, missions
 * done, the gate-to-gate map line and the Duel Juara opt-in. Kas: donations this month, the 7-day resource graph, the
 * treasury in NB, "Sumbang yang di tangan". Cabang: branch halls and tribute, Restu Gunung, and for a player master
 * absorb / merge behind a two-step confirmation.
 * <p>
 * The DESIGN 42.11.12 town politics ({@code pakta}, {@code cabang}) are pages of their own on the single kit,
 * {@code client/dunia/kota42/ui/layar/LayarPakta} and {@code LayarCabang} (D-42U).
 */
public final class LayarPolitik extends LayarGabungDasar {

	private final DaftarGulir tetua = new DaftarGulir();
	private final DaftarGulir calon = new DaftarGulir();
	private final DaftarGulir sumbangan = new DaftarGulir();
	private final DaftarGulir cabang = new DaftarGulir();

	public LayarPolitik(int npcId, CompoundTag lembar) {
		super(Politik.LAYAR, npcId, lembar, u("politik.judul"), LembarKunci.GAYA_GIOK);
	}

	/** The "?" seal: the Ensiklopedia topic of the 41 sects (the sheet id is not a topic). */
	@Override
	public String helpTopic() {
		return "gabung_sekte";
	}

	@Override
	protected List<Tab> daftarTab() {
		return List.of(tab(Politik.TAB_MAJELIS, u("politik.tab.majelis"), LembarKunci.Ikon.ISTANA),
				tab(Politik.TAB_SUKSESI, u("politik.tab.suksesi"), LembarKunci.Ikon.CAP_MERAH),
				tab(Politik.TAB_PERANG, u("politik.tab.perang"), LembarKunci.Ikon.ARENA),
				tab(Politik.TAB_KAS, u("politik.tab.kas"), LembarKunci.Ikon.BATU_TINGGI),
				tab(Politik.TAB_CABANG, u("politik.tab.cabang"), LembarKunci.Ikon.KUIL));
	}

	@Override
	protected KeadaanKosong.Gambar kosongGambar() {
		return KeadaanKosong.Gambar.GULUNGAN;
	}

	@Override
	protected void tabBerubah() {
		long now = Util.getMillis();
		tetua.mulaiUlang(now);
		calon.mulaiUlang(now);
		sumbangan.mulaiUlang(now);
		cabang.mulaiUlang(now);
		halaman = 0;
	}

	@Override
	protected void isiGabung(Lukis l, int x0, int y0, int x1, int y1) {
		int y = kepalaSekte(l, x0, y0, x1);
		switch (tab) {
			case Politik.TAB_SUKSESI -> suksesi(l, x0, y, x1, y1);
			case Politik.TAB_PERANG -> perang(l, x0, y, x1, y1);
			case Politik.TAB_KAS -> kas(l, x0, y, x1, y1);
			case Politik.TAB_CABANG -> cabang(l, x0, y, x1, y1);
			default -> majelis(l, x0, y, x1, y1);
		}
	}

	// ================================================================== Majelis

	private void majelis(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar m = lembar.sub(Politik.MAJELIS);
		// the three seats on one line
		int x = x0;
		x = kursi(l, m.sub(Politik.KETUA), IkonGabung.KETUA, u("politik.ketua"), x, y0, x1);
		x = kursi(l, m.sub(Politik.WAKIL), IkonGabung.pangkat(Pangkat.WAKIL.ordinal()), u("politik.wakil"), x + 8, y0, x1);
		kursi(l, m.sub(Politik.PEWARIS), IkonGabung.pangkat(Pangkat.PEWARIS.ordinal()), u("politik.pewaris"), x + 8, y0, x1);
		int y = y0 + 14;
		// resource toward the next class, ranking
		int sumber = m.i(Politik.SUMBER, 0);
		int naik = m.i(Politik.NAIK, 0);
		Component baris = naik > 0 ? u("politik.sumber_naik", Brush.angka(sumber), Brush.angka(naik), m.i(Politik.PERINGKAT, 0))
				: u("politik.sumber_puncak", Brush.angka(sumber), m.i(Politik.PERINGKAT, 0));
		l.teks(baris, x0, y, x1 - x0, l.gaya.teks);
		y += 10;
		if (naik > 0) {
			batang(l, x0, y, x1 - x0, sumber / (float) naik, Palette.GOLD, -1.0F);
		}
		y += 7;
		l.garis(x0, x1, y);
		y += 4;
		y = bilahHalaman(l, x0, y, x1, u("politik.hal.tetua"), u("politik.hal.tindakan"));
		Kotak[] k = bagiHalaman(x0, y, x1, y1);
		if (k[0] == null) {
			aksiMajelis(l, k[1]);
			return;
		}
		// the elders
		List<Lembar> list = m.daftar(Politik.TETUA);
		subjudul(l, u("politik.tetua", list.size()), k[0].x0(), k[0].y0(), k[0].x1());
		tetua.gambar(l, "tetua", k[0].x0(), k[0].y0() + 14, k[0].x1(), k[0].y1(), list.size(), 22, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = list.get(i);
			boolean anda = e.b(LembarKunci.ANDA);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			if (anda) {
				lk.g.fill(rx0, ry0, rx0 + 2, ry1, Palette.GOLD);
			}
			IkonGabung.gambar(lk.g, IkonGabung.pangkat(Pangkat.TETUA.ordinal()), rx0 + 4, ry0 + 3, 15);
			int bar = Math.min(60, (rx1 - rx0) / 4);
			int tx = rx0 + 22;
			MutableComponent nama = e.teks(LembarKunci.NAMA).copy();
			if (anda) {
				nama.append(" · ").append(u("politik.anda"));
			}
			lk.teks(anda ? Brush.bold(nama) : nama, tx, ry0 + 3, rx1 - tx - bar - 10, anda ? l.gaya.aksen : l.gaya.teks);
			MutableComponent bawah = e.teks(GabungLembar.JABATAN).copy();
			if (e.ada(GabungLembar.RANAH)) {
				bawah.append(" · ").append(namaRanah(e.i(GabungLembar.RANAH, 0)));
			}
			lk.teks(bawah, tx, ry0 + 12.5F, rx1 - tx - bar - 10, l.gaya.teksSamar);
			if (!e.b(GabungLembar.PEMAIN)) {
				int sikap = e.i(GabungLembar.SIKAP, 0);
				int bx = rx1 - bar - 4;
				batang(lk, bx, ry0 + 8, bar, (sikap + 100) / 200.0F, DaftarSekte.warnaSikap(sikap), 0.5F);
				if (lk.di(bx, ry0 + 4, bx + bar, ry0 + 16)) {
					lk.tip(u("politik.sikap", Brush.bertanda(sikap)));
				}
			}
		}, null, null);
		if (k[1] != null) {
			aksiMajelis(l, k[1]);
		}
	}

	/** One seat "Ketua Mo Tianhe (T. Dewa)"; returns the x after it. */
	private int kursi(Lukis l, Lembar o, int ikon, Component label, int x, int y, int x1) {
		if (x + 30 > x1) {
			return x;
		}
		IkonGabung.gambar(l.g, ikon, x, y, 11);
		MutableComponent t = label.copy().append(" ");
		if (o.kosongSemua()) {
			t.append(u("politik.kosong_kursi"));
		} else {
			t.append(o.b(LembarKunci.ANDA) ? Brush.bold(o.teks(LembarKunci.NAMA)) : o.teks(LembarKunci.NAMA));
			if (o.ada(GabungLembar.RANAH)) {
				t.append(Component.literal(" (")).append(namaRanah(o.i(GabungLembar.RANAH, 0))).append(")");
			}
		}
		int w = Math.min(x1 - x - 13, font.width(t));
		l.teks(t, x + 13, y + 2, w, o.b(LembarKunci.ANDA) ? l.gaya.aksen : l.gaya.teks);
		return x + 13 + w;
	}

	private void aksiMajelis(Lukis l, Kotak k) {
		sumur(l, k);
		int teks = l.gaya.teksSumur();
		int x0 = k.x0() + 4;
		int x1 = k.x1() - 4;
		int y = k.y0() + 3;
		boolean ketua = lembar.b(LembarPolitik.KETUA_ANDA);
		if (ketua) {
			l.teks(Brush.bold(u("politik.arah")), x0, y, x1 - x0, teks);
			y += 11;
			String arah = lembar.s(Politik.ARAH, Politik.ARAH_NETRAL);
			String[] semua = {Politik.ARAH_NETRAL, Politik.ARAH_DAMAI, Politik.ARAH_AGRESIF};
			int bw = (x1 - x0 - 4) / 3;
			for (int i = 0; i < semua.length; i++) {
				String a = semua[i];
				boolean kini = a.equals(arah);
				TalismanButton.gambar(l, "arah" + a, x0 + i * (bw + 2), y, bw, 13, u("politik.arah." + a), !kini, kini ? u("politik.arah.kini") : null,
						() -> kirim(LembarKunci.op(Politik.OP_ARAH, a)), false, kini);
			}
			y += 16;
		}
		l.teks(Brush.bold(ketua ? u("politik.tunjuk") : u("politik.usul")), x0, y, x1 - x0, teks);
		y += 11;
		List<Lembar> list = lembar.daftar(LembarPolitik.CALON);
		if (list.isEmpty()) {
			l.bungkus(u("politik.calon_kosong"), x0, y, x1 - x0, Palette.argb(0xB0, teks & 0xFFFFFF), k.y1() - y - 2);
			return;
		}
		calon.gambar(l, "calon", x0, y, x1, k.y1() - 3, list.size(), ketua ? 30 : 17, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = list.get(i);
			int pangkat = e.i(GabungLembar.PANGKAT, 0);
			IkonGabung.gambar(lk.g, IkonGabung.pangkat(pangkat), rx0, ry0 + 2, 11);
			if (ketua) {
				lk.teks(e.teks(LembarKunci.NAMA), rx0 + 14, ry0 + 3, rx1 - rx0 - 14, teks);
				int bw = (rx1 - rx0 - 4) / 3;
				String[][] jab = {{"TETUA", "politik.tunjuk.tetua"}, {"WAKIL", "politik.tunjuk.wakil"}, {"PEWARIS", "politik.tunjuk.pewaris"}};
				for (int j = 0; j < jab.length; j++) {
					tombolYakin(lk, "tunjuk" + i + j, rx0 + j * (bw + 2), ry0 + 13, bw, 13, u(jab[j][1]), true, null,
							LembarKunci.op(Politik.OP_TUNJUK, jab[j][0], e.id()));
				}
			} else {
				int bw = Math.min(64, (rx1 - rx0) / 3);
				lk.teks(e.teks(LembarKunci.NAMA), rx0 + 14, ry0 + 3, rx1 - rx0 - 20 - bw, teks);
				tombolOp(lk, "usul" + i, rx1 - bw, ry0 + 1, bw, 13, u("politik.usulkan"), true, null, LembarKunci.op(Politik.OP_USUL, e.id()));
			}
		}, null, null);
	}

	// ================================================================== Suksesi

	private void suksesi(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar s = lembar.sub(Politik.SUKSESI);
		int bab = s.i(Politik.BAB, 0);
		List<Lembar> tahap = s.daftar(LembarKunci.TAHAP);
		int n = Math.max(1, tahap.size());
		int y;
		if (tinggiIsi < PENDEK) {
			// a short box: the stage as one line instead of three seals on a thread
			Component etape = bab >= 1 && bab <= tahap.size() ? tahap.get(bab - 1).teks(LembarKunci.TEKS) : Component.empty();
			l.teks(u("politik.suksesi.bab", Math.max(1, Math.min(bab, n)), n, etape), x0, y0 + 1, x1 - x0, l.gaya.teks);
			y = y0 + 12;
		} else {
			// three seals on a thread
			int seg = (x1 - x0) / n;
			int cy = y0 + 12;
			l.g.fill(x0 + seg / 2, cy, x1 - seg / 2, cy + 1, Palette.argb(0x80, Palette.GOLD & 0xFFFFFF));
			for (int i = 0; i < tahap.size(); i++) {
				Lembar t = tahap.get(i);
				boolean done = t.b(Politik.SELESAI);
				boolean kini = !done && bab == i + 1;
				int cx = x0 + seg * i + seg / 2;
				IkonGabung.cap(l.g, cx - 9, cy - 9, 18, done ? 0xD4A637 : kini ? 0x2E8963 : 0x8A8A8A, kini, !done && !kini, l.denyut());
				Brush.centered(l.g, font, Component.literal(Integer.toString(i + 1)), cx, cy - 3.5F, 0xFFFFFFFF, 1.0F, true);
				Component label = t.teks(LembarKunci.TEKS);
				Brush.fitCentered(l.g, font, label, cx, cy + 12, seg - 6, done ? Palette.OK : kini ? l.gaya.aksen : l.gaya.teksSamar, 1.0F, 1.0F, false);
			}
			y = cy + 24;
		}
		l.garis(x0, x1, y);
		y += 4;
		y = bilahHalaman(l, x0, y, x1, u("politik.hal.suksesi"), u("politik.hal.pewaris"));
		boolean bersuara = lembar.b(LembarPolitik.BERSUARA);
		Component calonKetua = s.teksAtau(LembarPolitik.CALON_KETUA);
		int kolom = tata() == Tata.SEMPIT ? x1 : x0 + (x1 - x0) / 2 - 3;
		int by = y1 - 16;
		if (tata() == Tata.SEMPIT && halaman == 1) {
			pewaris(l, s, bersuara, x0, y, x1, by);
			return;
		}
		// left: the succession
		int ly = y;
		l.teks(Brush.bold(u("politik.suksesi.judul")), x0, ly, kolom - x0, l.gaya.teks);
		ly += 11;
		if (bab >= 4) {
			l.teks(u("politik.suksesi.selesai"), x0, ly, kolom - x0, Palette.OK);
			ly += 10;
		} else if (calonKetua != null) {
			l.teks(u("politik.suksesi.sidang", calonKetua), x0, ly, kolom - x0, l.gaya.teks);
			ly += 10;
			l.teks(u("politik.suksesi.rahasia", s.i(LembarPolitik.MASUK, 0)), x0, ly, kolom - x0, l.gaya.teksSamar);
			ly += 10;
			if (bersuara && ly + 14 < by) {
				int bw = (kolom - x0 - 4) / 2;
				tombolOp(l, "ya", x0, ly, bw, 13, u("politik.setuju"), true, null, LembarKunci.op(Politik.OP_SUARA, "ya"));
				tombolOp(l, "tidak", x0 + bw + 4, ly, bw, 13, u("politik.tolak"), true, null, LembarKunci.op(Politik.OP_SUARA, "tidak"));
				ly += 16;
			}
		} else {
			ly += Math.round(l.bungkus(u("politik.suksesi.ket"), x0, ly, kolom - x0, l.gaya.teksLemah, by - ly - 30)) + 2;
		}
		Component alasan = s.teksAtau(LembarKunci.ALASAN);
		if (alasan != null && ly + 9 < by - 2) {
			l.teks(alasan, x0, ly, kolom - x0, l.gaya.gelap() ? 0xFFFF9A8A : Palette.BAHAYA);
		}
		boolean sempit = tata() == Tata.SEMPIT;
		// the narrow page shares its width with "Tentang" (it was dropped there: a member could not object)
		int bw = sempit && calonKetua != null ? (kolom - x0 - 4) / 2 : Math.min(170, kolom - x0);
		tombolOp(l, "suksesi", x0, by, bw, 15, u("politik.suksesi.lanjut", Brush.angka(s.i(LembarKunci.BIAYA, 0))), s.b(LembarKunci.BISA), alasan,
				LembarPolitik.OP_SUKSESI);
		if (calonKetua != null && (sempit || x0 + bw + 70 < kolom)) {
			tombolYakin(l, "tentang", x0 + bw + 4, by, sempit ? kolom - x0 - bw - 4 : Math.min(80, kolom - x0 - bw - 4), 15, u("politik.tentang"), true,
					null, LembarPolitik.OP_TENTANG);
		}
		if (tata() == Tata.SEMPIT) {
			return;
		}
		// right: the heir election
		pewaris(l, s, bersuara, kolom + 6, y, x1, by);
	}

	/** The heir election (right column, or its own page in the narrow layout). */
	private void pewaris(Lukis l, Lembar s, boolean bersuara, int rx0, int y, int x1, int by) {
		sumur(l, new Kotak(rx0, y, x1, by - 3));
		int teks = l.gaya.teksSumur();
		int lemah = Palette.argb(0xB0, teks & 0xFFFFFF);
		int ry = y + 3;
		int tahapPewaris = s.i(LembarPolitik.PEWARIS_TAHAP, 0);
		l.teks(Brush.bold(u("politik.pewaris.judul")), rx0 + 4, ry, x1 - rx0 - 8, teks);
		ry += 11;
		l.teks(u("politik.pewaris.tahap." + Math.max(0, Math.min(3, tahapPewaris))), rx0 + 4, ry, x1 - rx0 - 8, lemah);
		ry += 10;
		List<Lembar> fin = s.daftar(LembarPolitik.FINALIS);
		boolean suara = tahapPewaris == 3 && bersuara;
		for (int i = 0; i < fin.size() && ry + 13 < by - 22; i++) {
			Lembar f = fin.get(i);
			boolean anda = f.b(LembarKunci.ANDA);
			l.teks(Component.literal((i + 1) + ". ").append(f.teks(LembarKunci.NAMA)), rx0 + 6, ry + 2, x1 - rx0 - 70, anda ? l.gaya.aksen : teks);
			if (suara && !anda) {
				tombolOp(l, "pilihF" + i, x1 - 58, ry, 54, 12, u("politik.pilih"), true, null, LembarKunci.op(Politik.OP_SUARA, f.id()));
			}
			ry += 13;
		}
		if (fin.isEmpty()) {
			l.teks(u("politik.pewaris.belum"), rx0 + 6, ry, x1 - rx0 - 10, lemah);
		}
		int pw = (x1 - rx0 - 12) / 2;
		Component tolakPewaris = s.teksAtau(LembarPolitik.PEWARIS_ALASAN);
		tombolOp(l, "pewaris", rx0 + 4, by - 20, pw, 14, u("politik.pewaris.daftar"), tolakPewaris == null, tolakPewaris, LembarPolitik.OP_PEWARIS);
		tombolOp(l, "tanding", rx0 + 8 + pw, by - 20, pw, 14, u("politik.pewaris.tanding"), tahapPewaris == 2, u("politik.pewaris.bukan_laga"),
				LembarPolitik.OP_TANDING);
	}

	// ================================================================== Perang

	private void perang(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar p = lembar.sub(Politik.PERANG);
		if (p.kosongSemua()) {
			kosong(l, u("politik.perang.damai"), x0, y0, x1, y1);
			return;
		}
		int skor = p.i(LembarKunci.SKOR, 0);
		l.teks(Brush.bold(u("politik.perang.lawan", p.teks(Politik.LAWAN))), x0, y0, x1 - x0 - 70, l.gaya.teks);
		l.kanan(u("politik.perang.sisa", p.i(LembarKunci.SISA_HARI, 0)), x1, y0 + 1, l.gaya.teksLemah);
		int y = y0 + 13;
		// the tug-of-war bar: -100 .. +100 on the kit's bar, the centre marked, the signed score at its end
		Component skorT = Component.literal(Brush.bertanda(skor));
		int sw = font.width(skorT);
		l.kanan(skorT, x1, y, skor >= 0 ? l.gaya.naik() : l.gaya.turun());
		Bilah.gambar(l, x0, y + 2, x1 - sw - 4, (Math.max(-100, Math.min(100, skor)) + 100) / 200.0F, skor >= 0 ? 0xFF2E8963 : 0xFFB03030,
				new float[] {0.5F});
		y += 12;
		y = bilahHalaman(l, x0, y, x1, u("politik.hal.misi"), u("politik.hal.peta"));
		Kotak[] k = bagiHalaman(x0, y, x1, y1);
		if (k[0] == null) {
			peta(l, k[1], p);
			return;
		}
		// missions done in this war
		subjudul(l, u("politik.perang.misi"), k[0].x0(), k[0].y0(), k[0].x1());
		int my = k[0].y0() + 14;
		for (Lembar e : p.daftar(Politik.MISI)) {
			if (my + 10 > k[0].y1() - 18) {
				break;
			}
			boolean done = e.b(Politik.SELESAI);
			Glyphs.Atlas.ikon(l.g, done ? Glyphs.Atlas.CENTANG : Glyphs.Atlas.SILANG, k[0].x0(), my, 9, done ? 0xFFFFFFFF : 0x90FFFFFF);
			l.teks(e.teks(LembarKunci.NAMA), k[0].x0() + 12, my + 1, k[0].w() - 12, done ? l.gaya.teks : l.gaya.teksLemah);
			my += 11;
		}
		boolean ikut = p.b(Politik.IKUT);
		TalismanButton.gambar(l, "ikut", k[0].x0(), k[0].y1() - 15, k[0].w(), 14, ikut ? u("politik.perang.duel_ya") : u("politik.perang.duel_tidak"), true,
				null, () -> kirim(LembarKunci.op(Politik.OP_IKUT_DUEL, ikut ? 0 : 1)), false, ikut);
		if (k[1] != null) {
			peta(l, k[1], p);
		}
	}

	/** The gate-to-gate line on a little ink map (our gate jade, theirs red). */
	private void peta(Lukis l, Kotak k, Lembar p) {
		sumur(l, k);
		int teks = l.gaya.teksSumur();
		int[] g = p.ia(Politik.GARIS);
		l.teks(u("politik.perang.peta"), k.x0() + 4, k.y0() + 3, k.w() - 8, Palette.argb(0xB0, teks & 0xFFFFFF));
		if (g.length < 4) {
			Brush.fitCentered(l.g, font, u("politik.perang.peta_tak_ada"), (k.x0() + k.x1()) / 2.0F, (k.y0() + k.y1()) / 2.0F - 4, k.w() - 10, teks, 1.0F,
					1.0F, false);
			return;
		}
		float pad = 14.0F;
		float ax = k.x0() + pad;
		float bx = k.x1() - pad;
		float cy = (k.y0() + k.y1()) / 2.0F + 4;
		// orient the line by the real bearing, scaled to the box
		double dx = g[2] - g[0];
		double dz = g[3] - g[1];
		double len = Math.max(1.0D, Math.sqrt(dx * dx + dz * dz));
		float r = Math.min((bx - ax) / 2.0F, (k.y1() - k.y0()) / 2.0F - pad);
		float cx = (ax + bx) / 2.0F;
		float x1 = cx - (float) (dx / len) * r;
		float y1 = cy - (float) (dz / len) * r;
		float x2 = cx + (float) (dx / len) * r;
		float y2 = cy + (float) (dz / len) * r;
		for (int i = 0; i < 12; i++) {
			if (i % 2 == 0) {
				float t0 = i / 12.0F;
				float t1 = (i + 1) / 12.0F;
				Brush.line(l.g, x1 + (x2 - x1) * t0, y1 + (y2 - y1) * t0, x1 + (x2 - x1) * t1, y1 + (y2 - y1) * t1, 1.2F, Palette.argb(0xC0, 0x3A2A1A));
			}
		}
		Brush.disc(l.g, x1, y1, 4.5F, 0xFF2E8963);
		Brush.disc(l.g, x2, y2, 4.5F, 0xFFB03030);
		Brush.centered(l.g, font, u("politik.perang.kita"), x1, y1 + 6, teks, 1.0F, false);
		Brush.centered(l.g, font, p.teks(Politik.LAWAN), x2, y2 + 6, teks, 1.0F, false);
		l.teks(u("politik.perang.jarak", Math.round(len)), k.x0() + 4, k.y1() - 10, k.w() - 8, Palette.argb(0xB0, teks & 0xFFFFFF));
	}

	// ================================================================== Kas

	private void kas(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar kas = lembar.sub(Politik.KAS);
		int y = y0;
		l.teks(u("politik.kas.isi"), x0, y + 1.0F, 90, l.gaya.teksSamar);
		LabelHarga.gambar(l, kas.l(LembarPolitik.KAS_NB, 0L), -1L, x0 + 92, y, false, false);
		if (kas.ada(Politik.KAS_KETUA)) {
			l.kanan(u("politik.kas.bagian_ketua", Brush.angka(kas.i(Politik.KAS_KETUA, 0))), x1, y + 1.0F, Palette.GOLD_DARK);
		}
		y += 12;
		y = bilahHalaman(l, x0, y, x1, u("politik.hal.sumbangan"), u("politik.hal.grafik"));
		Kotak[] k = bagiHalaman(x0, y, x1, y1 - 18);
		if (k[0] == null) {
			grafik(l, k[1], kas.ia(Politik.GRAFIK));
			tombolSumbang(l, kas, x0, x1, y1);
			return;
		}
		// the 7-day graph
		if (k[1] != null) {
			grafik(l, k[1], kas.ia(Politik.GRAFIK));
		}
		// donations this month
		List<Lembar> list = kas.daftar(Politik.SUMBANGAN);
		subjudul(l, u("politik.kas.sumbangan"), k[0].x0(), k[0].y0(), k[0].x1());
		if (list.isEmpty()) {
			l.teks(u("politik.kas.belum"), k[0].x0(), k[0].y0() + 15, k[0].w(), l.gaya.teksSamar);
		} else {
			int maks = 1;
			for (Lembar e : list) {
				maks = Math.max(maks, e.i(LembarKunci.JUMLAH, 0));
			}
			final int puncak = maks;
			sumbangan.gambar(l, "sumbangan", k[0].x0(), k[0].y0() + 14, k[0].x1(), k[0].y1(), list.size(), 13, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
				Lembar e = list.get(i);
				boolean anda = e.b(LembarKunci.ANDA);
				int jml = e.i(LembarKunci.JUMLAH, 0);
				int lebar = Math.round((rx1 - rx0) * 0.45F * jml / (float) puncak);
				lk.g.fill(rx1 - lebar, ry0 + 2, rx1, ry1 - 2, Palette.argb(anda ? 0x90 : 0x50, Palette.GOLD & 0xFFFFFF));
				lk.teks(Component.literal((i + 1) + ". ").append(e.teks(LembarKunci.NAMA)), rx0 + 2, ry0 + 2.5F, (rx1 - rx0) * 0.55F, anda ? l.gaya.aksen : l.gaya.teks);
				pk(lk, jml, -1L, rx1 - 2, ry0 + 2.5F, true);
			}, null, null);
		}
		tombolSumbang(l, kas, x0, x1, y1);
	}

	/** "Sumbang" with what the hand holds (disabled with the reason when it is empty or worthless). */
	private void tombolSumbang(Lukis l, Lembar kas, int x0, int x1, int y1) {
		int nilai = kas.i(LembarPolitik.NILAI_TANGAN, 0);
		Component tangan = kas.teksAtau(LembarKunci.TANGAN);
		Component label = tangan == null ? u("politik.kas.sumbang_kosong") : u("politik.kas.sumbang", tangan, Brush.angka(nilai));
		tombolOp(l, "sumbang", x0, y1 - 15, x1 - x0, 15, label, nilai > 0, tangan == null ? u("politik.kas.tangan_kosong") : u("politik.kas.tak_bernilai"),
				Politik.OP_SUMBANG);
	}

	/** The 7-day resource graph: the kit's ledger chart ({@link Bilah#grafik7}) in a well, "hari ini" under the last bar. */
	private void grafik(Lukis l, Kotak k, int[] g) {
		sumur(l, k);
		int teks = l.gaya.teksSumur();
		l.teks(u("politik.kas.grafik"), k.x0() + 4, k.y0() + 3, k.w() - 8, Palette.argb(0xB0, teks & 0xFFFFFF));
		if (g.length == 0) {
			return;
		}
		int gx0 = k.x0() + 6;
		int gx1 = k.x1() - 6;
		int gy0 = k.y0() + 14;
		int gy1 = k.y1() - 12;
		if (gy1 - gy0 < 10) {
			return;
		}
		long[] nilai = new long[g.length];
		for (int i = 0; i < g.length; i++) {
			nilai[i] = g[i];
		}
		Bilah.grafik7(l, nilai, gx0, gy0, gx1, gy1);
		float lebar = (gx1 - gx0) / (float) g.length;
		Brush.centered(l.g, font, u("politik.kas.hari_ini"), gx1 - lebar / 2.0F, gy1 + 2, teks, 1.0F, false);
	}

	// ================================================================== Cabang

	private void cabang(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar c = lembar.sub(Politik.CABANG);
		int y = y0;
		Component induk = c.teksAtau(LembarPolitik.INDUK);
		if (induk != null) {
			l.teks(u("politik.cabang.induk", induk), x0, y, x1 - x0 - 80, Palette.GOLD_DARK);
			tombolYakin(l, "merdeka", x1 - 76, y - 2, 76, 13, u("politik.cabang.merdeka"), true, null, LembarPolitik.OP_MERDEKA);
			y += 13;
		}
		if (c.b(Politik.TERSERAP)) {
			l.teks(u("politik.cabang.terserap"), x0, y, x1 - x0, Palette.PERINGATAN);
			y += 11;
		}
		y = bilahHalaman(l, x0, y, x1, u("politik.hal.daftar"), u("politik.hal.restu"));
		Kotak[] k = bagiHalaman(x0, y, x1, y1);
		if (k[0] == null) {
			restu(l, k[1], c);
			return;
		}
		List<Lembar> list = c.daftar(LembarKunci.DAFTAR);
		subjudul(l, u("politik.cabang.daftar", list.size()), k[0].x0(), k[0].y0(), k[0].x1());
		if (list.isEmpty()) {
			l.teks(u("politik.cabang.kosong"), k[0].x0(), k[0].y0() + 15, k[0].w(), l.gaya.teksSamar);
		} else {
			cabang.gambar(l, "cabang", k[0].x0(), k[0].y0() + 14, k[0].x1(), k[0].y1(), list.size(), 22, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
				Lembar e = list.get(i);
				lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
				Glyphs.Atlas.ikon(lk.g, LembarKunci.Ikon.KUIL, rx0 + 3, ry0 + 3, 15, 0xFFFFFFFF);
				int pw = pk(lk, e.i(Politik.UPETI, 0), -1L, rx1 - 4, ry0 + 2, true);
				lk.kanan(u("politik.cabang.upeti"), rx1 - 4, ry0 + 12, l.gaya.teksSamar);
				lk.teks(e.teks(LembarKunci.NAMA), rx0 + 22, ry0 + 3, rx1 - rx0 - 30 - pw, e.b(LembarKunci.ANDA) ? l.gaya.aksen : l.gaya.teks);
				lk.teks(u("politik.cabang.pemilik", e.teks(Politik.PEMILIK)), rx0 + 22, ry0 + 12.5F, rx1 - rx0 - 30 - pw, l.gaya.teksSamar);
			}, null, null);
		}
		if (k[1] != null) {
			restu(l, k[1], c);
		}
	}

	/** Restu Gunung (found a branch) and, for a player master, absorb / merge. */
	private void restu(Lukis l, Kotak k, Lembar c) {
		sumur(l, k);
		int teks = l.gaya.teksSumur();
		int lemah = Palette.argb(0xB0, teks & 0xFFFFFF);
		int x0 = k.x0() + 4;
		int x1 = k.x1() - 4;
		int y = k.y0() + 3;
		l.teks(Brush.bold(u("politik.restu.judul")), x0, y, x1 - x0, teks);
		y += 12;
		int restu = c.i(LembarPolitik.RESTU, 0);
		String[] langkah = {"politik.restu.1", "politik.restu.2", "politik.restu.3"};
		for (int i = 0; i < langkah.length; i++) {
			boolean done = restu > i;
			Glyphs.Atlas.ikon(l.g, done ? Glyphs.Atlas.CENTANG : Glyphs.Atlas.SILANG, x0, y, 9, done ? 0xFFFFFFFF : 0x90FFFFFF);
			Component t = i == 0 ? u(langkah[i], Brush.angka(c.i(LembarPolitik.RESTU_PK, 0))) : u(langkah[i]);
			l.teks(t, x0 + 12, y + 1, x1 - x0 - 12, done ? teks : lemah);
			y += 11;
		}
		Component tolak = c.teksAtau(LembarPolitik.CABANG_ALASAN);
		if (tolak != null) {
			y += Math.round(l.bungkus(tolak, x0, y, x1 - x0, Palette.BAHAYA, 18)) + 1;
		}
		int bw = (x1 - x0 - 4) / 2;
		if (y + 14 < k.y1()) {
			tombolOp(l, "restu", x0, y, bw, 13, u("politik.restu.persembahan"), tolak == null && restu < 1, restu >= 1 ? u("politik.restu.sudah") : tolak,
					LembarPolitik.OP_RESTU);
			tombolOp(l, "ajukanCabang", x0 + bw + 4, y, bw, 13, u("politik.restu.ajukan"), tolak == null && restu >= 2,
					tolak != null ? tolak : u("politik.restu.belum"), LembarPolitik.OP_CABANG);
			y += 17;
		}
		if (!lembar.b(LembarPolitik.KETUA_ANDA) || y + 30 > k.y1()) {
			return;
		}
		l.garis(x0, x1, y);
		y += 3;
		l.teks(Brush.bold(u("politik.satukan.judul")), x0, y, x1 - x0, teks);
		y += 11;
		satukan(l, "serap", Politik.OP_SERAP, Politik.OP_SERAP_YA, c.b(Politik.SERAP), x0, y, bw);
		satukan(l, "lebur", Politik.OP_LEBUR, Politik.OP_LEBUR_YA, c.b(Politik.LEBUR), x0 + bw + 4, y, bw);
	}

	/**
	 * Absorb / merge: the press asks the server (it explains the consequence in the chat) and opens the heavy seal of the
	 * Gulungan (43.7: tick + hold, blood red); holding it confirms.
	 */
	private void satukan(Lukis l, String kunci, String op, String opYa, boolean bisa, int x, int y, int w) {
		TalismanButton.gambar(l, kunci, x, y, w, 14, u("politik.satukan." + kunci), bisa, u("politik.satukan.tidak"), () -> {
			kirim(op);
			konfirmasi(SegelKonfirmasi.Tingkat.BERAT, u("politik.satukan." + kunci + ".ya"), () -> kirim(opYa));
		}, false, false);
	}
}
