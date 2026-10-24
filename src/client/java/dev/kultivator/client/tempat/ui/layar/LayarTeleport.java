package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.DaftarGulir;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.LabelHarga;
import dev.kultivator.client.tempat.ui.kit.LayarTempatDasar;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.Pratinjau3D;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Dermaga;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Teleport;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * {@code teleport} and {@code dermaga} (DESIGN 38.U.3.9, 38.B.4.3): the formation station / post discs and the Sky Dock.
 * Left: known destinations with their kind icon, distance and fare (unknown ones locked "Temukan dulu", every row refused
 * while wanted, fare in red when short). Right: a mini map with the player at the centre and the destinations as dots in
 * their real direction (the chosen one with a dashed line), the fare with companions, free trips left today, and
 * "Berangkat" (a 3 s channel on the HUD). The dock adds the ship turning on its pedestal, the flight time (a skippable
 * scene), allied sky palaces, the hours ("Tutup malam") and the sky guests of 37.
 */
public final class LayarTeleport extends LayarTempatDasar {

	private final DaftarGulir daftar = new DaftarGulir();
	private final Pratinjau3D kapal = new Pratinjau3D();
	private final boolean dermaga;

	public LayarTeleport(String layar, int npcId, CompoundTag lembar) {
		super(layar, npcId, lembar, TeksTempat.k(Dermaga.LAYAR.equals(layar) ? "layar.dermaga" : "layar.teleport"), TempatWarna.Gaya.GIOK);
		this.dermaga = Dermaga.LAYAR.equals(layar);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of();
	}

	@Override
	protected dev.kultivator.client.tempat.ui.kit.KeadaanKosong.Gambar gambarKosong() {
		return dev.kultivator.client.tempat.ui.kit.KeadaanKosong.Gambar.LENTERA;
	}

	@Override
	protected void isi(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> tujuan = lembar.daftar(LembarKunci.TUJUAN);
		boolean buronan = lembar.b(Teleport.BURONAN);
		boolean tutup = dermaga && lembar.ada(Dermaga.BUKA) && !lembar.b(Dermaga.BUKA);
		int y = y0;
		if (buronan || tutup) {
			int c = buronan ? TempatWarna.BAHAYA : TempatWarna.PERINGATAN;
			l.g.fill(x0, y, x1, y + 13, Palette.argb(0xB0, c & 0xFFFFFF));
			Component t = buronan ? TeksTempat.k("teleport.buronan") : lembar.ada(LembarKunci.CATATAN) ? lembar.teks(LembarKunci.CATATAN)
					: TeksTempat.k("teleport.tutup_malam");
			Brush.fitCentered(l.g, font, Brush.bold(t), (x0 + x1) / 2.0F, y + 3, x1 - x0 - 8, 0xFFFFF4EC, 1.0F, 0.6F, false);
			y += 16;
		}
		if (tujuan.isEmpty()) {
			kosong(l, TeksTempat.k("teleport.kosong"), x0, y, x1, y1);
			return;
		}
		Kotak[] k = bagi(x0, y, x1, y1);
		Kotak kiri = k[0];
		long uang = uang();
		daftar.gambar(l, "tujuan", kiri.x0(), kiri.y0(), kiri.x1(), kiri.y1(), tujuan.size(), 24, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = tujuan.get(i);
			boolean kunci = e.b(LembarKunci.TERKUNCI) || buronan;
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			int ikon = e.i(LembarKunci.IKON, dermaga ? LembarKunci.Ikon.DERMAGA : LembarKunci.Ikon.TELEPORT);
			IkonTempat.gambar(lk.g, kunci ? LembarKunci.Ikon.GEMBOK : ikon, rx0 + 3, ry0 + 4, 16, kunci ? 0xA0FFFFFF : 0xFFFFFFFF);
			int warna = kunci ? TempatWarna.terkunci(gaya.teks) : gaya.teks;
			int hargaW = 0;
			if (!kunci && e.ada(LembarKunci.HARGA)) {
				hargaW = LabelHarga.gambar(lk, e.l(LembarKunci.HARGA, 0L), uang, rx1 - 3, ry0 + 4, true, 0.85F, false);
			}
			lk.teks(e.teks(LembarKunci.NAMA), rx0 + 23, ry0 + 3, rx1 - rx0 - 30 - hargaW, warna, 0.9F);
			Component bawah = kunci ? (e.ada(LembarKunci.ALASAN) ? e.teks(LembarKunci.ALASAN) : TeksTempat.k("teleport.temukan_dulu"))
					: dermaga && e.ada(Dermaga.DURASI) ? TeksTempat.k("teleport.jarak_durasi", TeksTempat.angka(e.i(LembarKunci.JARAK, 0)),
							e.i(Dermaga.DURASI, 0)) : TeksTempat.k("teleport.jarak", TeksTempat.angka(e.i(LembarKunci.JARAK, 0)));
			lk.keterangan(bawah, rx0 + 23, ry0 + 13, rx1 - rx0 - 30, kunci ? 0xFFB05040 : gaya.teksSamar);
			if (dermaga && e.b(Dermaga.LANGIT)) {
				IkonTempat.gambar(lk.g, LembarKunci.Ikon.distrik(LembarKunci.DISTRIK_AWAN), rx1 - 13, ry0 + 13, 10, 0xFFFFFFFF);
			}
		}, this::berangkat, null);
		Kotak kanan = k[1];
		int sel = daftar.pilihan();
		detail(l, kanan, sel >= 0 && sel < tujuan.size() ? tujuan.get(sel) : null, tujuan, buronan || tutup);
	}

	private void berangkat() {
		List<Lembar> tujuan = lembar.daftar(LembarKunci.TUJUAN);
		int i = daftar.pilihan();
		if (i >= 0 && i < tujuan.size() && !tujuan.get(i).b(LembarKunci.TERKUNCI) && tulisBoleh()) {
			kirim(LembarKunci.op(dermaga ? Dermaga.OP_BERANGKAT : Teleport.OP_BERANGKAT, tujuan.get(i).id()));
		}
	}

	private void detail(Lukis l, Kotak k, Lembar e, List<Lembar> semua, boolean ditolak) {
		PanelTempat.kartu(l.g, gaya, k.x0(), k.y0(), k.x1(), k.y1(), 0.0F, false);
		int x0 = k.x0() + 5;
		int x1 = k.x1() - 5;
		int y = k.y0() + 4;
		if (dermaga && tata != Tata.SEMPIT) {
			int pv = tata == Tata.LEBAR ? 80 : 56;
			String ship = lembar.s(LembarKunci.ENTITAS);
			kapal.gambar(l, "kapal", x1 - pv, y, x1, y + pv, ItemStack.EMPTY, ship.isEmpty() ? null : ship, LembarKunci.Ikon.DERMAGA);
		}
		// mini map: player in the centre, destinations by real direction
		int mapSize = Math.min(dermaga ? (x1 - x0) - (tata == Tata.LEBAR ? 86 : 62) : x1 - x0, Math.min(90, k.h() - 70));
		if (mapSize >= 40) {
			int mx = x0;
			int my = y;
			float cx = mx + mapSize / 2.0F;
			float cy = my + mapSize / 2.0F;
			Brush.disc(l.g, cx, cy, mapSize / 2.0F, Palette.argb(0x60, 0x184B36));
			Brush.ring(l.g, cx, cy, mapSize / 2.0F - 1.5F, mapSize / 2.0F, Palette.JADE_DARK);
			Brush.ring(l.g, cx, cy, mapSize / 4.0F - 0.5F, mapSize / 4.0F, Palette.argb(0x50, 0xFFFFFF));
			l.g.fill(Math.round(cx), my + 2, Math.round(cx) + 1, my + 6, Palette.argb(0xA0, 0xFFFFFF));
			Brush.centered(l.g, font, TeksTempat.k("teleport.utara"), cx, my + 6, 0xC0FFFFFF, 0.6F, false);
			Minecraft mc = Minecraft.getInstance();
			double px = mc.player == null ? 0 : mc.player.getX();
			double pz = mc.player == null ? 0 : mc.player.getZ();
			double maxD = 1;
			for (Lembar t : semua) {
				if (t.ada(LembarKunci.X)) {
					maxD = Math.max(maxD, Math.hypot(t.i(LembarKunci.X, 0) - px, t.i(LembarKunci.Z, 0) - pz));
				}
			}
			float r = mapSize / 2.0F - 5.0F;
			for (Lembar t : semua) {
				if (!t.ada(LembarKunci.X)) {
					continue;
				}
				double dx = t.i(LembarKunci.X, 0) - px;
				double dz = t.i(LembarKunci.Z, 0) - pz;
				double d = Math.hypot(dx, dz);
				float f = (float) Math.sqrt(d / maxD);
				float tx = cx + (float) (d == 0 ? 0 : dx / d) * r * f;
				float tz = cy + (float) (d == 0 ? 0 : dz / d) * r * f;
				boolean pilih = t == e;
				if (pilih) {
					int steps = 10;
					for (int s = 0; s < steps; s += 2) {
						Brush.line(l.g, cx + (tx - cx) * s / steps, cy + (tz - cy) * s / steps, cx + (tx - cx) * (s + 1) / steps, cy + (tz - cy) * (s + 1) / steps,
								1.0F, TempatWarna.EMAS_TERANG);
					}
				}
				Brush.disc(l.g, tx, tz, pilih ? 2.6F : 1.6F, t.b(LembarKunci.TERKUNCI) ? 0xFF8D928E : pilih ? TempatWarna.EMAS_TERANG : Palette.JADE_LIGHT);
			}
			Brush.disc(l.g, cx, cy, 2.2F + l.denyut(), TempatWarna.SEGEL);
			y += mapSize + 4;
		} else if (dermaga) {
			y += 60;
		}
		if (e != null) {
			l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), x0, y, x1 - x0, gaya.teks);
			y += 11;
		}
		int pengikut = lembar.i(Teleport.PENGIKUT, 0);
		long hargaPengikut = lembar.l(Teleport.HARGA_PENGIKUT, 0L);
		long harga = e == null ? 0L : e.l(LembarKunci.HARGA, 0L);
		long total = harga + pengikut * hargaPengikut;
		if (pengikut > 0) {
			y += barisNilai(l, TeksTempat.k("teleport.pengikut", pengikut), TeksTempat.nb(pengikut * hargaPengikut, false), x0, y, x1);
		}
		int gratis = lembar.i(Teleport.GRATIS_SISA, 0);
		if (gratis > 0) {
			y += barisNilai(l, TeksTempat.k("teleport.gratis"), Component.literal(Integer.toString(gratis)), x0, y, x1);
			total = 0L;
		}
		if (dermaga) {
			for (Lembar tamu : lembar.daftar(Dermaga.TAMU_LANGIT)) {
				if (y > k.y1() - 32) {
					break;
				}
				l.keterangan(TeksTempat.k("teleport.tamu", tamu.teks(LembarKunci.NAMA), tamu.i(LembarKunci.HARI, 0)), x0, y, x1 - x0, gaya.teksSamar);
				y += 9;
			}
		}
		boolean kunci = e == null || e.b(LembarKunci.TERKUNCI);
		boolean cukup = uang() < 0 || total <= uang();
		Component alasan = ditolak ? (lembar.b(Teleport.BURONAN) ? TeksTempat.k("teleport.buronan") : TeksTempat.k("teleport.tutup_malam"))
				: kunci ? (e != null && e.ada(LembarKunci.ALASAN) ? e.teks(LembarKunci.ALASAN) : TeksTempat.k("teleport.pilih"))
						: TeksTempat.k("harga.kurang", TeksTempat.angka(total - Math.max(0L, uang())));
		Component label = total > 0 ? TeksTempat.k("teleport.berangkat_harga", TeksTempat.angka(total)) : TeksTempat.k("teleport.berangkat");
		String op = LembarKunci.op(dermaga ? Dermaga.OP_BERANGKAT : Teleport.OP_BERANGKAT, e == null ? "" : e.id());
		tombolOp(l, "berangkat", x0, k.y1() - 19, x1 - x0, 15, label, !ditolak && !kunci && cukup, alasan, op);
		int kanal = lembar.i(Teleport.KANAL_DETIK, 3);
		l.keterangan(TeksTempat.k("teleport.kanal", kanal), x0, k.y1() - 29, x1 - x0, gaya.teksSamar);
	}
}
