package dev.kultivator.client.sekte.gabung.ui.layar;

import static dev.kultivator.client.sekte.gabung.ui.layar.LayarGabung2.u;

import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarPilihan;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Potret;
import dev.kultivator.client.sekte.gabung.ui.IkonGabung;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.data.Dao;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import dev.kultivator.sekte.gabung.ui.GabungLembar.Shifu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code gabung_shifu} (DESIGN 41.5.7, 41.10.9): "Tetua Memilih Murid", a Di Sini prompt (43.8.2). After the oath the
 * elders who want the new member step forward: one to three cards side by side (stacked rows on a narrow page), each with
 * the elder's live portrait, office, realm and Dao, temper, the teacher's promise in quotes and a bar of how keen the
 * elder is. The choice itself is the kit's {@link DaftarPilihan} (G59: "1  Pilih" on each card; ↑↓ between the cards,
 * Enter, a click, or keys 1-3 under Di Sini, 43.17 K3). When nobody stepped forward the admissions elder takes the member
 * on: one card, no choice. The cards slide up one after another as the elders step out of the line. The page draws with
 * the single kit and its own focus ring (pattern of {@link LayarDirektori}); text is 1.0.
 */
public final class LayarShifu extends LayarGabungDasar {

	private static final long MUNCUL_MS = 220L;

	private long dibuka = Util.getMillis();

	public LayarShifu(int npcId, CompoundTag lembar) {
		super(Shifu.LAYAR, npcId, lembar, u("shifu.judul"), TempatWarna.Gaya.KERTAS);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of();
	}

	@Override
	protected void setelahLembar(boolean buka) {
		if (buka) {
			dibuka = Util.getMillis();
		}
	}

	@Override
	protected Component petunjuk() {
		return u("shifu.petunjuk");
	}

	@Override
	protected void isiGabung(dev.kultivator.client.tempat.ui.kit.Lukis l, int x0, int y0, int x1, int y1) {
		// not used: this page draws with the single kit in lukis()
	}

	@Override
	public String helpTopic() {
		return Shifu.LAYAR;
	}

	@Override
	public boolean lebar() {
		return false;
	}

	// ================================================================== drawing (single kit)

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		// applies the first sheet; the content is drawn in lukis() with the page's focus ring
		tabSekarang();
	}

	@Override
	protected void lukis(Lukis l) {
		int x0 = x + 2;
		int x1 = x + w - 2;
		int y0 = y + 2;
		int y1 = y + h - 2;
		int yy = y1 - y0 < PENDEK ? y0 : LayarPutusan.kepalaSekte(l, lembar, x0, y0, x1);
		boolean kasihan = lembar.b(Shifu.KASIHAN);
		yy += Math.round(l.bungkus(kasihan ? u("shifu.kasihan") : u("shifu.ket"), x0, yy, x1 - x0, kasihan ? Palette.PERINGATAN : l.gaya.teksLemah, 18))
				+ 3;
		List<Lembar> list = lembar.daftar(Shifu.TAWARAN);
		if (list.isEmpty()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.LENTERA,
					lembar.sub(LembarKunci.KOSONG), u("shifu.kosong"), x0, yy, x1, y1, this::kirim);
			return;
		}
		int n = list.size();
		boolean tumpuk = tata() == Tata.SEMPIT && n > 1;
		for (int i = 0; i < n; i++) {
			int cx0;
			int cx1;
			int cy0;
			int cy1;
			if (tumpuk) {
				int ch = (y1 - yy - (n - 1) * 3) / n;
				cx0 = x0;
				cx1 = x1;
				cy0 = yy + i * (ch + 3);
				cy1 = cy0 + ch;
			} else {
				int cw = Math.min(170, (x1 - x0 - (n - 1) * 6) / n);
				int total = cw * n + (n - 1) * 6;
				cx0 = x0 + (x1 - x0 - total) / 2 + i * (cw + 6);
				cx1 = cx0 + cw;
				cy0 = yy;
				cy1 = y1;
			}
			float t = Math.min(1.0F, Math.max(0.0F, (l.kini - dibuka - i * 120L) / (float) MUNCUL_MS));
			if (t <= 0.0F) {
				continue;
			}
			int geser = Math.round((1.0F - t * (2.0F - t)) * 18.0F);
			if (tumpuk) {
				baris(l, list.get(i), i, cx0, cy0, cx1, cy1, kasihan, geser);
			} else {
				kartu(l, list.get(i), i, cx0, cy0 + geser, cx1, cy1 + geser, kasihan);
			}
		}
	}

	/** The choice of elder {@code i} ({@link DaftarPilihan}, one entry; all cards share the group, so ↑↓ walks them). */
	private void pilih(Lukis l, Lembar e, int i, int x0, int y0, int x1, boolean kasihan) {
		Component label = kasihan ? u("shifu.ikuti") : u("shifu.pilih");
		Component kunci = tulisBoleh() ? null : alasanBaca();
		DaftarPilihan.gambar(l, "pilih", List.of(new DaftarPilihan.Pilihan(i + 1, label, kunci, false)), x0, y0, x1, y0 + 15, false,
				p -> kirim(LembarKunci.op(Shifu.OP_PILIH, e.id())));
	}

	/** The elder's lines that fit from y (name, office · realm, Dao, temper); returns the y under them. */
	private int keterangan(Lukis l, Lembar e, int tx, int ty, int tw, int yMaks) {
		IkonGabung.gambar(l.g, IkonGabung.SHIFU, tx, ty, 10);
		l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), tx + 12, ty + 1, tw - 12, l.gaya.teks);
		ty += 11;
		MutableComponent jab = e.teks(GabungLembar.JABATAN).copy().append(" · ").append(namaRanah(e.i(GabungLembar.RANAH, 0)));
		if (ty + 9 <= yMaks) {
			l.teks(jab, tx, ty, tw, l.gaya.teksLemah);
			ty += 10;
		}
		Dao dao = Dao.byId(e.s(GabungLembar.DAO));
		if (dao != null && ty + 9 <= yMaks) {
			l.teks(u("shifu.dao", dao.displayName()), tx, ty, tw, l.gaya.teksLemah);
			ty += 10;
		}
		if (ty + 9 <= yMaks) {
			l.samar(e.teks(GabungLembar.WATAK).copy().withStyle(ChatFormatting.ITALIC), tx, ty, tw);
			ty += 11;
		}
		return ty;
	}

	private static Component janji(Lembar e) {
		return Component.literal("“").append(e.teks(Shifu.JANJI)).append("”").withStyle(ChatFormatting.ITALIC);
	}

	/** Every line of an elder, for the tooltip of a compact row. */
	private List<Component> semua(Lembar e) {
		List<Component> tip = new ArrayList<>();
		tip.add(Brush.bold(e.teks(LembarKunci.NAMA)));
		tip.add(e.teks(GabungLembar.JABATAN).copy().append(" · ").append(namaRanah(e.i(GabungLembar.RANAH, 0))));
		Dao dao = Dao.byId(e.s(GabungLembar.DAO));
		if (dao != null) {
			tip.add(u("shifu.dao", dao.displayName()));
		}
		tip.add(e.teks(GabungLembar.WATAK));
		tip.add(janji(e));
		tip.add(u("shifu.minat").copy().append(" " + Math.max(0, Math.min(100, e.i(Shifu.MINAT, 50)))));
		return tip;
	}

	/** A narrow-page row: portrait, the lines that fit, the keenness bar and the choice on the right. */
	private void baris(Lukis l, Lembar e, int i, int x0, int y0, int x1, int y1, boolean kasihan, int geser) {
		int gx = x0 + geser;
		boolean hover = l.di(x0, y0, x1, y1);
		l.kartu(gx, y0, x1, y1, hover ? 1.0F : 0.0F, false);
		int foto = Math.max(16, Math.min(40, y1 - y0 - 4));
		Potret.gambar(l, gx + 2, y0 + 2, foto, e.i(GabungLembar.POTRET, -1), Potret.IKON_BAWAAN, false, 0.0F);
		int bw = Math.min(90, (x1 - gx) / 3);
		int tx = gx + foto + 6;
		int tw = x1 - bw - 6 - tx;
		int ty = keterangan(l, e, tx, y0 + 2, tw, y1 - 6);
		int minat = Math.max(0, Math.min(100, e.i(Shifu.MINAT, 50)));
		if (ty <= y1 - 5) {
			Bilah.gambar(l, tx, y1 - 5, tx + tw, minat / 100.0F, minat >= 80 ? Palette.GOLD : 0xFF2E8963, null);
		}
		pilih(l, e, i, x1 - bw - 3, y0 + (y1 - y0 - 15) / 2, x1 - 3, kasihan);
		if (hover && l.mx < x1 - bw - 3) {
			l.tip(semua(e));
		}
	}

	private void kartu(Lukis l, Lembar e, int i, int x0, int y0, int x1, int y1, boolean kasihan) {
		boolean hover = l.di(x0, y0, x1, y1);
		l.kartu(x0, y0, x1, y1, hover ? 1.0F : 0.0F, false);
		int by = y1 - 18;
		int foto = Math.max(28, Math.min(56, Math.min((x1 - x0) / 2, (y1 - y0) / 3)));
		Potret.gambar(l, (x0 + x1 - foto) / 2, y0 + 5, foto, e.i(GabungLembar.POTRET, -1), Potret.IKON_BAWAAN, false, 0.0F);
		int tx = x0 + 5;
		int tw = x1 - 5 - tx;
		int ty = keterangan(l, e, tx, y0 + foto + 9, tw, by - 16);
		// the promise in quotes takes what the keenness bar and the choice leave
		Component janji = janji(e);
		if (by - 14 - ty >= 9) {
			ty += Math.round(l.bungkus(janji, tx, ty, tw, l.gaya.teks, by - 14 - ty)) + 3;
		} else if (hover) {
			l.tip(janji);
		}
		int minat = Math.max(0, Math.min(100, e.i(Shifu.MINAT, 50)));
		if (by - 12 >= ty - 3) {
			Component m = u("shifu.minat");
			int mw = Math.min(tw / 2, font.width(m) + 4);
			l.samar(m, tx, by - 13, mw);
			Bilah.gambar(l, tx + mw, by - 10, x1 - 5, minat / 100.0F, minat >= 80 ? Palette.GOLD : 0xFF2E8963, null);
		}
		pilih(l, e, i, x0 + 4, by, x1 - 4, kasihan);
		if (hover) {
			l.g.fill(x0, y0, x1, y0 + 1, Palette.argb(0xC0, Palette.GOLD));
		}
	}

	// ================================================================== input (the page's focus ring)

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return fokusRing.klik(event.x(), event.y(), event.button());
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return fokusRing.lepas(event.x(), event.y());
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return fokusRing.seret(event.x(), event.y(), dx);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return fokusRing.gulir(mouseX, mouseY, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return fokusRing.tombol(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return fokusRing.ketik(event);
	}

	@Override
	public boolean fokusTab(boolean back) {
		fokusRing.geser(back ? -1 : 1);
		return true;
	}

	@Override
	public boolean mauTombol() {
		return fokusRing.mauTombol();
	}

	/** K3 (43.17): under Di Sini the number keys pick the elder (the choices register their numbers). */
	@Override
	public boolean angka(int n) {
		return fokusRing.angka(n);
	}
}
