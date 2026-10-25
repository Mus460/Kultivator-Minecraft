package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.LencanaPangkat;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.Kontrak;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * {@code tema_kontrak} (DESIGN 38.T.7.7): the board of the Sarang Kontrak. Contracts are only ever against demonic beasts
 * and their elites, rival demonic groups, bandit bands and hunting righteous squads; never townsfolk, villagers,
 * disciples or players (the board says so under its title). Left the pinned notices (kind, rank seal, reward); right the
 * chosen notice as a large poster with the target, the rank, the reward, the Infami and Gentar it brings, the distance with
 * "Tandai" and "Ambil kontrak". Along the bottom the player's running contracts with their progress, "Serahkan" once
 * done and "Batalkan" (the one confirm seal, light). A Di Sini page on the single kit (43.8.3); in the narrow layout the
 * board takes the area and Enter / a second click shows the poster in its place (Esc back).
 */
public final class LayarKontrak extends TemaHalaman {

	private final DaftarGulir daftar = new DaftarGulir();
	private boolean rinciSempit;

	public LayarKontrak(int npcId, CompoundTag lembar) {
		super(Kontrak.LAYAR, npcId, lembar, "layar.kontrak", TempatWarna.Gaya.KERTAS);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of();
	}

	@Override
	protected void lembarBaru(boolean buka) {
		if (buka) {
			daftar.mulaiUlang(Util.getMillis());
			rinciSempit = false;
		}
	}

	@Override
	public boolean rinci() {
		return rinciSempit && tata == Tata.SEMPIT || super.rinci();
	}

	@Override
	public boolean mundur() {
		if (rinciSempit && tata == Tata.SEMPIT) {
			rinciSempit = false;
			return true;
		}
		return super.mundur();
	}

	private static int ikonJenis(String jenis) {
		return switch (jenis) {
			case "binatang" -> TemaUi.I_CAKAR;
			case "iblis_saingan" -> TemaUi.I_TOPENG;
			case "begal" -> TemaUi.I_KARUNG;
			case "pemburu" -> TemaUi.I_GENTAR;
			default -> TemaUi.I_KONTRAK;
		};
	}

	@Override
	protected void lukisTema(Lukis l, int x0, int y0, int x1, int y1) {
		boolean sempit = tata == Tata.SEMPIT;
		int y = y0;
		l.tengah(TemaUi.k("kontrak.aturan"), (x0 + x1) / 2.0F, y, x1 - x0, gaya.teksSamar);
		y += 12;
		List<Lembar> semua = lembar.daftar(Kontrak.KONTRAK);
		int sel = daftar.pilihan();
		boolean poster = sel >= 0 && sel < semua.size();
		if (sempit && rinciSempit && poster) {
			// the poster in place of the board (the running contracts come back with the board)
			poster(l, new Kotak(x0, y, x1, y1));
			return;
		}
		rinciSempit = false;
		List<Lembar> aktif = lembar.daftar(Kontrak.AKTIF_KONTRAK);
		int baris = Math.min(sempit ? 2 : 3, aktif.size());
		int aktifH = aktif.isEmpty() ? 0 : 15 + baris * 17;
		if (sempit) {
			papan(l, new Kotak(x0, y, x1, y1 - aktifH));
		} else {
			Kotak[] k = bagi(x0, y, x1, y1 - aktifH);
			papan(l, k[0]);
			poster(l, k[1]);
		}
		if (aktifH > 0) {
			berjalan(l, aktif, baris, x0, y1 - aktifH + 3, x1, y1);
		}
	}

	private void papan(Lukis l, Kotak k) {
		List<Lembar> semua = lembar.daftar(Kontrak.KONTRAK);
		// cork board
		l.g.fill(k.x0(), k.y0(), k.x1(), k.y1(), 0xFF6A4A2E);
		for (int i = 0; i < 40; i++) {
			int sx = k.x0() + (int) ((i * 53L + 7L) % Math.max(1, k.w()));
			int sy = k.y0() + (int) ((i * 31L + 3L) % Math.max(1, k.h()));
			l.g.fill(sx, sy, sx + 1, sy + 1, 0x40000000);
		}
		Brush.frame(l.g, k.x0(), k.y0(), k.x1(), k.y1(), 0xFF3A2414);
		if (semua.isEmpty()) {
			kosong(l, TemaUi.k("kontrak.kosong"), k.x0() + 2, k.y0() + 2, k.x1() - 2, k.y1() - 2);
			return;
		}
		boolean sempit = tata == Tata.SEMPIT;
		daftar.gambar(l, "kontrak", k.x0() + 3, k.y0() + 3, k.x1() - 3, k.y1() - 3, semua.size(), 26, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = semua.get(i);
			float miring = ((i * 37) % 5 - 2) * 0.4F;
			int px0 = rx0 + 2 + Math.round(miring);
			lk.g.fill(px0 + 1, ry0 + 2, rx1 - 1, ry1, 0x50000000);
			lk.g.fill(px0, ry0 + 1, rx1 - 2, ry1 - 1, e.bisa() ? pilih ? 0xFFFFF4D8 : sorot ? 0xFFF8ECCC : 0xFFEEE0BC : 0xFFB8B0A0);
			Brush.disc(lk.g, (px0 + rx1) / 2.0F, ry0 + 2.5F, 1.8F, 0xFFB3281D);
			TemaUi.ikon(lk.g, ikonJenis(e.s(LembarKunci.JENIS)), px0 + 3, ry0 + 5, 14);
			Component imb = Component.literal(Brush.singkat(e.l(Kontrak.IMBALAN, 0L)));
			int iw = font.width(imb);
			int kanan = Math.max(iw, 14) + 26;
			lk.teks(e.teks(LembarKunci.NAMA), px0 + 20, ry0 + 3, rx1 - px0 - 20 - kanan, 0xFF2A1A10);
			lk.teks(e.teks(Kontrak.SASARAN), px0 + 20, ry0 + 13, rx1 - px0 - 20 - kanan, 0xFF6A5040);
			LencanaPangkat.gambar(lk, e.s(Kontrak.PANGKAT, "B"), rx1 - 20, ry0 + 2, 12);
			lk.kanan(imb, rx1 - 5, ry0 + 15, 0xFF7A5A10);
			if (!e.bisa()) {
				Glyphs.Atlas.ikon(lk.g, LembarKunci.Ikon.GEMBOK, rx1 - kanan + 4, ry0 + 8, 10, 0xFFFFFFFF);
			}
		}, sempit ? () -> rinciSempit = true : this::ambil, null);
	}

	private void ambil() {
		List<Lembar> semua = lembar.daftar(Kontrak.KONTRAK);
		int i = daftar.pilihan();
		if (i >= 0 && i < semua.size() && semua.get(i).bisa() && tulisBoleh()) {
			kirim(LembarKunci.op(Kontrak.OP_AMBIL, semua.get(i).id()));
		}
	}

	private void poster(Lukis l, Kotak k) {
		List<Lembar> semua = lembar.daftar(Kontrak.KONTRAK);
		int sel = daftar.pilihan();
		if (sel < 0 || sel >= semua.size()) {
			l.kartu(k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
			tengah(l, TemaUi.k("kontrak.pilih"), k);
			return;
		}
		Lembar e = semua.get(sel);
		// the poster paper with burnt edges
		l.g.fill(k.x0() + 2, k.y0() + 2, k.x1() + 1, k.y1() + 1, 0x60000000);
		l.g.fillGradient(k.x0(), k.y0(), k.x1(), k.y1(), 0xFFF2E4C0, 0xFFE0CC9C);
		Brush.frame(l.g, k.x0(), k.y0(), k.x1(), k.y1(), 0xFF5A3A1A);
		for (int i = 0; i < 10; i++) {
			int ex = k.x0() + (int) ((i * 41L) % Math.max(1, k.w()));
			l.g.fill(ex, k.y0(), ex + 3, k.y0() + 1, 0x805A3A1A);
			l.g.fill(ex + 5, k.y1() - 1, ex + 8, k.y1(), 0x805A3A1A);
		}
		int ink = 0xFF2A1A10;
		int x0 = k.x0() + 6;
		int x1 = k.x1() - 6;
		int y = k.y0() + 4;
		int by = k.y1() - 19;
		Component dicari = Brush.bold(TemaUi.k("kontrak.dicari"));
		float s = font.width(dicari) * 1.5F <= x1 - x0 ? 1.5F : 1.0F;
		Brush.centered(l.g, font, dicari, (x0 + x1) / 2.0F, y, Palette.SEAL, s, false);
		y += s > 1.0F ? 16 : 12;
		int badge = 24;
		LencanaPangkat.gambar(l, e.s(Kontrak.PANGKAT, "B"), x0, y, badge);
		TemaUi.ikon(l.g, ikonJenis(e.s(LembarKunci.JENIS)), x1 - 20, y + 2, 20);
		l.teks(Brush.bold(e.teks(Kontrak.SASARAN)), x0 + badge + 4, y + 2, x1 - x0 - badge - 28, ink);
		l.teks(TemaUi.k("kontrak.jenis." + jenisAman(e.s(LembarKunci.JENIS))), x0 + badge + 4, y + 13, x1 - x0 - badge - 28, 0xFF6A5040);
		y += badge + 3;
		l.teks(e.teks(LembarKunci.NAMA), x0, y, x1 - x0, 0xFF4A3420);
		y += 11;
		l.teks(TemaUi.k("kontrak.imbalan"), x0, y + 1, x1 - x0 - 70, 0xFF6A5040);
		LabelHarga.gambar(l, e.l(Kontrak.IMBALAN, 0L), -1L, x1, y + 1, true, false);
		y += 12;
		int inf = e.i(Kontrak.INFAMI, 0);
		int gen = e.i(Kontrak.GENTAR, 0);
		int half = (x1 - x0) / 2;
		TemaUi.ikon(l.g, TemaUi.I_INFAMI, x0, y - 1, 10);
		l.teks(TemaUi.k("kontrak.infami", delta(inf)), x0 + 12, y, half - 14, 0xFF4A3420);
		TemaUi.ikon(l.g, TemaUi.I_GENTAR, x0 + half, y - 1, 10);
		l.teks(TemaUi.k("kontrak.gentar", delta(gen)), x0 + half + 12, y, half - 12, 0xFF4A3420);
		y += 12;
		if (e.ada(LembarKunci.JARAK) && y + 14 <= by - 2) {
			l.teks(TemaUi.k("kontrak.jarak", e.i(LembarKunci.JARAK, 0)), x0, y + 2, x1 - x0 - 62, 0xFF6A5040);
			if (e.ada(LembarKunci.X)) {
				tombolOp(l, "tandai", x1 - 58, y - 1, 58, 14, TemaUi.k("kontrak.tandai"), true, null,
						LembarKunci.op(LembarKunci.OP_TANDAI, e.i(LembarKunci.X, 0), e.i(LembarKunci.Z, 0)));
			}
		}
		boolean sudah = false;
		for (Lembar a : lembar.daftar(Kontrak.AKTIF_KONTRAK)) {
			sudah |= a.id().equals(e.id());
		}
		Component alasan = sudah ? TemaUi.k("kontrak.sudah") : !e.bisa() ? alasanKeadaan(e) : null;
		if (by - 30 > y + 14) {
			Glyphs.Atlas.stempel(l.g, Glyphs.Atlas.Stempel.BULAT, x1 - 30, by - 30, 28, 28, Palette.argb(0x90, 0xB3281D));
		}
		tombolOp(l, "ambil", x0, by, x1 - x0, 15, TemaUi.k("kontrak.ambil"), e.bisa() && !sudah, alasan, LembarKunci.op(Kontrak.OP_AMBIL, e.id()));
	}

	private static String jenisAman(String jenis) {
		return switch (jenis) {
			case "binatang", "iblis_saingan", "begal", "pemburu" -> jenis;
			default -> "binatang";
		};
	}

	private void berjalan(Lukis l, List<Lembar> aktif, int baris, int x0, int y0, int x1, int y1) {
		l.garis(x0, x1, y0 - 2);
		l.teks(TemaUi.k("kontrak.berjalan", aktif.size()), x0, y0 + 1, x1 - x0, gaya.teksLemah);
		int y = y0 + 12;
		boolean sempit = tata == Tata.SEMPIT;
		int bw = sempit ? 52 : 64;
		for (int i = 0; i < baris; i++) {
			Lembar a = aktif.get(i);
			float f = Math.max(0.0F, Math.min(1.0F, a.f(LembarKunci.KEMAJUAN, 0.0F)));
			boolean selesai = f >= 0.999F;
			int warna = selesai ? Palette.OK : Palette.PERINGATAN;
			Component sisa = Brush.hari(a.i(LembarKunci.SISA_HARI, 0));
			if (sempit) {
				// name and days over a thin progress bar, the two buttons on the right
				int tx1 = x1 - bw * 2 - 8;
				int sw = font.width(sisa);
				l.teks(a.teks(LembarKunci.NAMA), x0, y, tx1 - x0 - sw - 4, gaya.teks);
				l.kanan(sisa, tx1, y, gaya.teksSamar);
				Bilah.gambar(l, x0, y + 11, tx1, f, warna, null);
			} else {
				l.teks(a.teks(LembarKunci.NAMA), x0, y + 2, (x1 - x0) * 0.34F, gaya.teks);
				int mx0 = x0 + Math.round((x1 - x0) * 0.36F);
				int mx1 = x1 - bw * 2 - 50;
				TemaUi.meter(l, -1, mx0, y + 2, Math.max(mx0 + 20, mx1), f, warna, -1.0F);
				l.samar(sisa, Math.max(mx0 + 22, mx1 + 3), y + 2, 44);
			}
			tombolOp(l, "serah" + i, x1 - bw * 2 - 3, y, bw, 14, TemaUi.k("kontrak.serah"), selesai, selesai ? null : TemaUi.k("kontrak.belum"),
					LembarKunci.op(Kontrak.OP_SERAH, a.id()));
			Component batal = TemaUi.k("kontrak.batal");
			String op = LembarKunci.op(Kontrak.OP_BATAL, a.id());
			boolean baca = !tulisBoleh();
			tombol(l, "batal" + i, x1 - bw, y, bw, 14, batal, !baca, baca ? alasanBaca() : null,
					() -> konfirmasi(SegelKonfirmasi.Tingkat.RINGAN, Component.translatable("kultivator.gui43.konteks.yakin", batal).append(" ")
							.append(a.teks(LembarKunci.NAMA)), () -> kirim(op)));
			y += 17;
		}
	}
}
