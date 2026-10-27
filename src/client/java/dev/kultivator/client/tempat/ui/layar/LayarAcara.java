package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.CincinWaktu;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.PapanSkor;
import dev.kultivator.client.screen.ui.Potret;
import dev.kultivator.client.screen.ui.Lembar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Acara;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Lelang;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * {@code acara} (DESIGN 38.U.3.9, 38.B.6, G71): festival and contest boards, live. The event's status and countdown, entry
 * fee, "Daftar" / "Tonton"; the live score board ({@link PapanSkor}: rows slide when ranks change) for the Pill Meet and
 * festivals; the bracket of the Grand Tournament drawn as joined lines round by round (the player's matches in gold); the
 * lantern riddles with three answers; and, for an event that ran as data with no player near, its summary. Single kit,
 * text 1.0.
 * <p>
 * The 42 ids {@code upacara} (an invitation card) and {@code rombongan} (a travelling troupe's programme) have their own page
 * on the single kit, {@code client/dunia/kota42/ui/layar/LayarUpacara} (D-42U).
 */
public final class LayarAcara extends LayarInti {

	private final PapanSkor papan = new PapanSkor();

	public LayarAcara(int npcId, CompoundTag lembar) {
		super(Acara.LAYAR, npcId, lembar, k("layar.acara"), LembarKunci.GAYA_KAIN);
	}

	@Override
	protected List<Tab> daftarTab() {
		return List.of();
	}

	@Override
	public boolean lebar() {
		return false;
	}

	@Override
	protected Component judulBawaan() {
		String jenis = lembar.s(LembarKunci.JENIS);
		return jenis.isEmpty() ? title : k("acara.jenis." + jenis);
	}

	@Override
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		boolean istana = Acara.ACARA_ISTANA.equals(lembar.s(Acara.ACARA_ID));
		int y = y0;
		// head row only for real events (the palace audience and the bare calendar have no status)
		if (lembar.ada(LembarKunci.STATUS)) {
			y = kepalaAcara(l, x0, y0, x1) + TOMBOL_H + 4;
		}
		Component ringkasan = lembar.teksAtau(Acara.RINGKASAN);
		if (ringkasan != null) {
			l.kartu(x0, y, x1, y1, 0.0F, false);
			Glyphs.Atlas.ikon(l.g, Glyphs.Atlas.GULUNGAN, x0 + 4, y + 4, 16, 0xFFFFFFFF);
			l.bungkus(ringkasan, x0 + 24, y + 6, x1 - x0 - 30, l.gaya.teks, y1 - y - 10);
			return;
		}
		List<Lembar> skor = lembar.daftar(LembarKunci.SKOR);
		List<Lembar> bracket = lembar.daftar(Acara.BRACKET);
		Lembar teka = lembar.sub(Acara.TEKA);
		List<Lembar> jadwal = lembar.daftar(Acara.JADWAL);
		boolean adaKanan = teka.ada(LembarKunci.ID) || !jadwal.isEmpty();
		Kotak[] k = adaKanan ? bagi(x0, y, x1, y1) : new Kotak[] {new Kotak(x0, y, x1, y1), null};
		Kotak kiri = k[0];
		Component catatan = lembar.teksAtau(LembarKunci.CATATAN);
		if (istana) {
			balairung(l, kiri, catatan);
		} else if (!bracket.isEmpty()) {
			bracket(l, bracket, kiri);
		} else if (!skor.isEmpty()) {
			int sy = kiri.y0() + l.kepala(k("acara.papan_skor"), kiri.x0(), kiri.y0(), kiri.x1());
			papan.gambar(l, skor, LembarKunci.SKOR, kiri.x0(), sy, kiri.x1(), 12, (kiri.y1() - sy) / 12, false);
		} else {
			// the bare calendar ("no event today") says so instead of "no scores yet"
			kosong(l, catatan != null ? catatan : k("acara.belum_ada_skor"), kiri.x0(), kiri.y0(), kiri.x1(), kiri.y1());
		}
		if (k[1] != null) {
			Kotak kanan = k[1];
			int ry = kanan.y0();
			if (teka.ada(LembarKunci.ID)) {
				ry = teka(l, teka, kanan, ry, istana);
			}
			if (!jadwal.isEmpty() && ry < kanan.y1() - 22) {
				// in the palace the rows are the standing orders of this season (state in the note column)
				ry += l.kepala(k(istana ? "acara.perintah" : "acara.jadwal"), kanan.x0(), ry, kanan.x1());
				for (Lembar j : jadwal) {
					if (ry > kanan.y1() - 10) {
						break;
					}
					Component cat = j.teks(LembarKunci.CATATAN);
					if (istana) {
						int nw = Math.round(kanan.w() * 0.45F);
						l.teks(j.teks(LembarKunci.NAMA), kanan.x0(), ry, nw, l.gaya.teks);
						l.teks(cat, kanan.x0() + nw + 4, ry, kanan.w() - nw - 4, l.gaya.teksSamar);
					} else {
						int cw = Math.min(l.font.width(cat), kanan.w() / 3);
						l.teks(k("acara.jadwal_baris", j.i(LembarKunci.HARI, 0), j.teks(LembarKunci.NAMA)), kanan.x0(), ry, kanan.w() - cw - 6, l.gaya.teks);
						l.teks(cat, kanan.x1() - cw, ry, cw, l.gaya.teksSamar);
					}
					ry += 11;
				}
			}
		}
	}

	/** Status, countdown, "Tonton" and "Daftar" of a real event; returns the row's y. */
	private int kepalaAcara(Lukis l, int x0, int y, int x1) {
		String status = lembar.s(LembarKunci.STATUS, Lelang.S_BELUM);
		Component st = Brush.bold(k("acara.status." + status));
		int sc = Lelang.S_BERJALAN.equals(status) ? l.gaya.naik() : Lelang.S_SELESAI.equals(status) ? l.gaya.teksSamar
				: l.gaya.gelap() ? Palette.GOLD_LIGHT : Palette.GOLD_DARK;
		int sw = Math.min(110, l.font.width(st));
		l.teks(st, x0, y + 3, sw, sc);
		int bx = x1;
		if (Lelang.S_BERJALAN.equals(status)) {
			bx -= 64;
			tombolOp(l, "tonton", bx, y, 64, TOMBOL_H, k("acara.tonton"), true, null, Acara.OP_TONTON);
		}
		if (lembar.ada(LembarKunci.BIAYA) && !Lelang.S_SELESAI.equals(status)) {
			boolean terdaftar = lembar.b(Acara.TERDAFTAR);
			long biaya = lembar.l(LembarKunci.BIAYA, 0L);
			Component label = terdaftar ? k("acara.terdaftar") : k("acara.daftar", Brush.angka(biaya));
			boolean cukup = uang() < 0 || biaya <= uang();
			int dw = Math.min(96, Math.max(60, bx - x0 - sw - 30));
			bx -= dw + 4;
			tombolOp(l, "daftar", bx, y, dw, TOMBOL_H, label, !terdaftar && cukup,
					terdaftar ? k("acara.sudah_terdaftar") : k("harga.kurang", Brush.angka(biaya - Math.max(0L, uang()))), Acara.OP_DAFTAR);
		}
		if (lembar.ada(LembarKunci.SISA_TICK) && !Lelang.S_SELESAI.equals(status)) {
			int cx = Math.min(x0 + sw + 12, bx - 10);
			CincinWaktu.gambar(l, cx, y + 7, 7.0F, lembar.l(LembarKunci.SISA_TICK, 0L), 0L, diterima, CincinWaktu.TANPA);
			if (l.di(cx - 8, y, cx + 8, y + 15)) {
				l.tip(Brush.waktu(CincinWaktu.sisa(lembar.l(LembarKunci.SISA_TICK, 0L), diterima, l.kini)));
			}
		}
		return y;
	}

	/**
	 * The audience hall (palace audience and court orders, 38.B): the speaker's live portrait under a red and gold
	 * canopy, name and title, and the hall's note (orders reset each season) under it.
	 */
	private void balairung(Lukis l, Kotak k, @Nullable Component catatan) {
		l.kartu(k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
		Lembar npc = lembar.sub(LembarKunci.NPC);
		float cx = (k.x0() + k.x1()) / 2.0F;
		int catatanH = catatan == null ? 0 : 28;
		int ps = Potret.ukuran(Math.min(k.w() - 16, k.h() - 36 - catatanH));
		int py = k.y0() + 10;
		// the canopy: a lacquered beam with gold studs and two hanging tassels
		int bx0 = Math.round(cx - ps / 2.0F - 10);
		int bx1 = Math.round(cx + ps / 2.0F + 10);
		l.g.fill(bx0, k.y0() + 3, bx1, k.y0() + 7, Palette.SEAL);
		l.g.fill(bx0, k.y0() + 3, bx1, k.y0() + 4, Palette.GOLD);
		for (int sx = bx0 + 4; sx < bx1 - 2; sx += 8) {
			l.g.fill(sx, k.y0() + 5, sx + 2, k.y0() + 6, Palette.GOLD_LIGHT);
		}
		l.g.fill(bx0 + 1, k.y0() + 7, bx0 + 2, py + ps / 2, Palette.GOLD_DARK);
		l.g.fill(bx1 - 2, k.y0() + 7, bx1 - 1, py + ps / 2, Palette.GOLD_DARK);
		Brush.disc(l.g, bx0 + 1.5F, py + ps / 2.0F + 2, 2.0F, Palette.SEAL);
		Brush.disc(l.g, bx1 - 1.5F, py + ps / 2.0F + 2, 2.0F, Palette.SEAL);
		Potret.gambar(l, Math.round(cx - ps / 2.0F), py, ps, npc.i(LembarKunci.POTRET, -1), LembarKunci.Ikon.ISTANA, npc.b(LembarKunci.TAKUT), 0.2F);
		int y = py + ps + 4;
		if (npc.ada(LembarKunci.NAMA)) {
			l.tengah(Brush.bold(npc.teks(LembarKunci.NAMA)), cx, y, k.w() - 8, l.gaya.gelap() ? Palette.GOLD_LIGHT : l.gaya.aksen);
			y += 11;
			Component gelar = npc.teksAtau(LembarKunci.GELAR);
			if (gelar != null) {
				l.tengah(gelar, cx, y, k.w() - 8, l.gaya.teksLemah);
				y += 10;
			}
		}
		if (catatan != null) {
			int ty = Math.max(y + 2, k.y1() - catatanH);
			l.bungkus(catatan, k.x0() + 5, ty, k.w() - 10, l.gaya.teksSamar, k.y1() - ty - 3);
		}
	}

	private int teka(Lukis l, Lembar teka, Kotak k, int y, boolean istana) {
		l.kartu(k.x0(), y, k.x1(), Math.min(k.y1(), y + 124), 0.0F, false);
		int x0 = k.x0() + 5;
		int x1 = k.x1() - 5;
		// a lantern riddle, or the words spoken in the palace hall (audience / court orders)
		Glyphs.Atlas.ikon(l.g, istana ? LembarKunci.Ikon.ISTANA : Glyphs.Atlas.OBOR, x0, y + 3, 12, 0xFFFFFFFF);
		Component judul = istana ? k("perintah".equals(teka.id()) ? "acara.istana_perintah" : "acara.istana_audiensi")
				: k("acara.teka_judul", teka.i(Acara.SISA, 0));
		l.teks(Brush.bold(judul), x0 + 15, y + 4, x1 - x0 - 15, l.gaya.gelap() ? Palette.GOLD_LIGHT : Palette.GOLD_DARK);
		y += 17;
		List<Lembar> jawaban = teka.daftar(Acara.JAWABAN);
		// the riddle gets what the three answer slips leave (a tooltip keeps the whole riddle on small screens)
		int nJawab = Math.min(3, jawaban.size());
		float h = l.bungkus(teka.teks(Acara.SOAL).copy().withStyle(ChatFormatting.ITALIC), x0, y, x1 - x0, l.gaya.teks, k.y1() - y - 6 - nJawab * (TOMBOL_H + 2));
		y += Math.round(h) + 4;
		String id = teka.id();
		for (int i = 0; i < jawaban.size() && i < 3; i++) {
			Component t = istana ? jawaban.get(i).teks(LembarKunci.TEKS) : Component.literal((char) ('A' + i) + ". ").append(jawaban.get(i).teks(LembarKunci.TEKS));
			tombolOp(l, "jawab" + i, x0, y, x1 - x0, TOMBOL_H, t, teka.i(Acara.SISA, 1) > 0, k("acara.teka_habis"), LembarKunci.op(Acara.OP_JAWAB, id, i));
			y += TOMBOL_H + 2;
		}
		return y + 6;
	}

	/** The bracket: rounds as columns, each match a pair of names joined to the next round. */
	private void bracket(Lukis l, List<Lembar> matches, Kotak k) {
		int y0 = k.y0() + l.kepala(k("acara.bracket"), k.x0(), k.y0(), k.x1());
		int maxBabak = 0;
		for (Lembar m : matches) {
			maxBabak = Math.max(maxBabak, m.i(Acara.BABAK, 0));
		}
		int cols = maxBabak + 1;
		int colW = (k.w() - 4) / Math.max(1, cols);
		int emas = l.gaya.gelap() ? Palette.GOLD_LIGHT : Palette.GOLD_DARK;
		for (int b = 0; b <= maxBabak; b++) {
			List<Lembar> round = new ArrayList<>();
			for (Lembar m : matches) {
				if (m.i(Acara.BABAK, 0) == b) {
					round.add(m);
				}
			}
			int n = Math.max(1, round.size());
			float slot = (k.y1() - y0) / (float) n;
			int cx = k.x0() + b * colW;
			for (int i = 0; i < round.size(); i++) {
				Lembar m = round.get(i);
				int my = Math.round(y0 + slot * i + slot / 2.0F - 10);
				int menang = m.i(Acara.MENANG, -1);
				boolean anda = m.b(LembarKunci.ANDA);
				int boxW = colW - 8;
				l.g.fill(cx, my, cx + boxW, my + 20, anda ? Palette.argb(0x70, Palette.GOLD & 0xFFFFFF) : l.gaya.kartu);
				Brush.frame(l.g, cx, my, cx + boxW, my + 20, Palette.argb(0x80, Palette.GOLD & 0xFFFFFF));
				l.teks(m.teks(Acara.A), cx + 2, my + 1, boxW - 4, menang == 0 ? emas : menang == 1 ? l.gaya.teksSamar : l.gaya.teks);
				l.teks(m.teks(Acara.B), cx + 2, my + 11, boxW - 4, menang == 1 ? emas : menang == 0 ? l.gaya.teksSamar : l.gaya.teks);
				if (b < maxBabak) {
					int lineY = my + 10;
					l.g.fill(cx + boxW, lineY, cx + colW, lineY + 1, Palette.argb(0x90, Palette.GOLD & 0xFFFFFF));
				}
			}
		}
	}
}
