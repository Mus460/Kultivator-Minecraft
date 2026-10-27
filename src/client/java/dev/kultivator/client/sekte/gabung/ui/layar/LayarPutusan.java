package dev.kultivator.client.sekte.gabung.ui.layar;

import static dev.kultivator.client.sekte.gabung.ui.layar.LayarGabung2.u;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.KartuHasil;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.sekte.gabung.ui.IkonGabung;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.api.Pintu;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import dev.kultivator.sekte.gabung.ui.GabungLembar.Putusan;
import net.minecraft.client.gui.Font;
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
 * {@code gabung_putusan} (DESIGN 41.5.5, 41.10.9): the verdict after the entrance test, a Di Sini prompt (43.8.2) drawn
 * with the kit's one result card ({@link KartuHasil}, G65). Over the card the sect strip and the total score 0-100 on a
 * scale marked at the refusal line (35), Murid Luar (55) and Calon Istimewa (80); the card's stamp is the verdict
 * (成 diterima, 極 Calon Istimewa, 敗 ditolak), its team line the rank given and the door, its body the stages (a stage
 * skipped by the door counts 0.8, marked so) and each term of the score with its sign, then the PK bonus and the next
 * step. "Menuju Upacara Sumpah" when accepted; "Masuk lewat Pintu Pelayan" when the servant door is offered; else
 * "Tutup". The page draws with the single kit and its own focus ring (pattern of {@link LayarDirektori}); text is 1.0.
 */
public final class LayarPutusan extends LayarGabungDasar {

	private static final int[] GARIS = {35, 55, 80};

	private final KartuHasil kartu = new KartuHasil();
	private long dibuka = Util.getMillis();
	private boolean bunyi;

	public LayarPutusan(int npcId, CompoundTag lembar) {
		super(Putusan.LAYAR, npcId, lembar, u("putusan.judul"), TempatWarna.Gaya.KERTAS);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of();
	}

	@Override
	protected void setelahLembar(boolean buka) {
		if (buka) {
			dibuka = Util.getMillis();
			bunyi = false;
			kartu.mulaiUlang(dibuka);
		}
	}

	@Override
	protected void isiGabung(dev.kultivator.client.tempat.ui.kit.Lukis l, int x0, int y0, int x1, int y1) {
		// not used: this page draws with the single kit in lukis()
	}

	@Override
	public String helpTopic() {
		return Putusan.LAYAR;
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
		boolean terima = lembar.b(Putusan.DITERIMA);
		if (!bunyi && l.kini - dibuka > 320L) {
			bunyi = true;
			if (terima) {
				UiSound.press();
			} else {
				UiSound.denied();
			}
		}
		// a short box (GUI scale 4) gives the strip's 19 px to the card
		int yy = y1 - y0 < PENDEK ? y0 : kepalaSekte(l, lembar, x0, y0, x1);
		// the score scale over the card
		int skor = Math.max(0, Math.min(100, lembar.i(LembarKunci.SKOR, 0)));
		Component skorT = Brush.bold(u("putusan.skor", skor));
		int lw = font.width(skorT) + 6;
		l.teks(skorT, x0, yy, lw, l.gaya.teks);
		int bx0 = x0 + lw;
		int bw = x1 - bx0;
		l.g.fill(bx0, yy + 1, x1, yy + 8, Palette.argb(0x40, 0x000000));
		float isi = Math.min(1.0F, skor / 100.0F * Math.min(1.0F, (l.kini - dibuka) / 700.0F));
		l.g.fill(bx0, yy + 2, bx0 + Math.round(bw * isi), yy + 7, terima ? 0xFF2E8963 : 0xFF9A9A92);
		for (int i = 0; i < GARIS.length; i++) {
			int gx = bx0 + Math.round(bw * GARIS[i] / 100.0F);
			l.g.fill(gx, yy, gx + 1, yy + 9, 0xFF5A3A08);
			if (l.di(gx - 3, yy - 1, gx + 4, yy + 10)) {
				l.tip(u("putusan.garis." + i, GARIS[i]));
			}
		}
		yy += 13;
		int by = y1 - 15;
		kartu.gambar(l, "putusan", -1, u(terima ? "putusan.diterima" : "putusan.ditolak"), lembar.b(Putusan.ISTIMEWA) ? u("putusan.istimewa") : null,
				KartuHasil.Nilai.of(!terima ? 0 : lembar.b(Putusan.ISTIMEWA) ? 3 : 2), regu(terima), isi(terima), x0, yy, x1, by - 3);
		// the next step
		int bw2 = Math.min(180, (x1 - x0 - 4) / 2);
		float cx = (x0 + x1) / 2.0F;
		if (terima) {
			tombolOp(l, Math.round(cx - bw2 / 2.0F), by, bw2, u("putusan.sumpah"), Putusan.OP_SUMPAH);
		} else if (lembar.b(Putusan.PELAYAN)) {
			tombolOp(l, x0, by, bw2, u("putusan.pelayan"), Putusan.OP_PELAYAN);
			TalismanButton.gambar(l, "tutup", x1 - bw2, by, bw2, 14, u("putusan.tutup"), true, null, this::onClose);
		} else {
			TalismanButton.gambar(l, "tutup", Math.round(cx - bw2 / 2.0F), by, bw2, 14, u("putusan.tutup"), true, null, this::onClose);
		}
	}

	private void tombolOp(Lukis l, int bx, int by, int bw, Component label, String op) {
		TalismanButton.gambar(l, op, bx, by, bw, 14, label, tulisBoleh(), tulisBoleh() ? null : alasanBaca(), () -> kirim(op));
	}

	/** The card's team line: the rank given (when accepted) and the door. */
	private Component regu(boolean terima) {
		MutableComponent r = Component.empty();
		if (terima && lembar.ada(GabungLembar.PANGKAT)) {
			r.append(u("putusan.pangkat", namaPangkat(lembar.i(GabungLembar.PANGKAT, 0)))).append("  ·  ");
		}
		Pintu pintu = Pintu.byId(lembar.s(GabungLembar.PINTU), Pintu.GERBANG);
		return r.append(u("putusan.pintu", pintu.nama()));
	}

	/** The card's body: the stages, each term of the score, the PK bonus and the advice. */
	private List<KartuHasil.Bagian> isi(boolean terima) {
		List<KartuHasil.Bagian> out = new ArrayList<>();
		boolean pertama = true;
		for (Lembar e : lembar.daftar(Putusan.TAHAP)) {
			float skor = Math.max(0.0F, Math.min(1.0F, e.f(LembarKunci.SKOR, 0.0F)));
			boolean lewat = e.b(Putusan.DILEWATI);
			MutableComponent t = e.teks(LembarKunci.NAMA).copy().append(": " + Math.round(skor * 100) + "%");
			if (lewat) {
				t.append(" — ").append(u("putusan.dilewati"));
			}
			int warna = lewat ? 0x9A9A92 : skor >= 0.8F ? Palette.GOLD & 0xFFFFFF : 0x2E8963;
			out.add(new KartuHasil.Bagian(pertama ? u("putusan.tahap") : null, t, KartuHasil.BIASA, warna));
			pertama = false;
		}
		pertama = true;
		for (Lembar e : lembar.daftar(Putusan.RINCIAN)) {
			int nilai = e.i(LembarKunci.NILAI, 0);
			Component t = Component.literal(Brush.bertanda(nilai) + "  ").append(e.teks(LembarKunci.TEKS));
			int status = nilai > 0 ? KartuHasil.BERHASIL : nilai < 0 ? KartuHasil.GAGAL : KartuHasil.BIASA;
			out.add(new KartuHasil.Bagian(pertama ? u("putusan.rincian") : null, t, status, 0));
			pertama = false;
		}
		int bonus = lembar.i(Putusan.BONUS, 0);
		if (bonus > 0) {
			out.add(KartuHasil.Bagian.judul(u("putusan.bonus"), Component.literal("+" + Brush.angka(bonus) + " PK")));
		}
		Component saran = lembar.teksAtau(GabungLembar.SARAN);
		if (saran != null) {
			out.add(new KartuHasil.Bagian(null, saran, terima ? KartuHasil.BERHASIL : KartuHasil.GAGAL, 0));
		}
		return out;
	}

	/**
	 * The sect strip of a 41 sheet on the single kit (the old-kit {@code kepalaSekte} of the base, text 1.0): emblem, class
	 * plaque + tier, corak, alignment, cloud palace, MEROSOT and a rule in the sect's ink. Returns the y under it.
	 */
	static int kepalaSekte(Lukis l, Lembar lembar, int x0, int y0, int x1) {
		if (!lembar.ada(GabungLembar.SEKTE_ID)) {
			return y0;
		}
		Font font = l.font;
		int y = y0 + 1;
		IkonGabung.lambang(l.g, font, lembar.i(GabungLembar.LAMBANG, 0), x0 + 6.5F, y + 6.5F, 6.5F);
		int x = x0 + 16;
		int kelas = lembar.i(GabungLembar.KELAS, 1);
		IkonGabung.gambar(l.g, IkonGabung.kelas(kelas), x, y, 12);
		x += 14;
		Component k = KelasSekte.byNomor(kelas).nama().copy().append(" · ").append(t("tier", lembar.i(GabungLembar.TINGKAT, 1)));
		int kw = Math.min(130, Math.min(font.width(k), x1 - x));
		l.teks(k, x, y + 2, kw, l.gaya.teks);
		x += kw + 6;
		String corak = lembar.s(GabungLembar.CORAK);
		if (!corak.isEmpty() && x + 60 < x1) {
			IkonGabung.gambar(l.g, IkonGabung.corak(corak), x, y, 12);
			Component c = t("corak", Corak.byId(corak, Corak.PEDANG).nama());
			int cw = Math.min(70, font.width(c));
			l.teks(c, x + 14, y + 2, cw, l.gaya.teksLemah);
			x += 14 + cw + 6;
		}
		int aliran = lembar.i(GabungLembar.ALIRAN, 0);
		if (x + 12 < x1) {
			IkonGabung.gambar(l.g, IkonGabung.ALIRAN + Math.max(0, Math.min(2, aliran)), x, y, 12);
			if (l.di(x, y, x + 12, y + 12)) {
				l.tip(t("aliran." + aliran));
			}
			x += 14;
		}
		if (lembar.b(GabungLembar.LANGIT) && x + 12 < x1) {
			IkonGabung.gambar(l.g, IkonGabung.LANGIT, x, y, 12);
			if (l.di(x, y, x + 12, y + 12)) {
				l.tip(t("langit"));
			}
			x += 14;
		}
		if (lembar.b(GabungLembar.MEROSOT) && x + 12 < x1) {
			IkonGabung.gambar(l.g, IkonGabung.MEROSOT, x, y, 12);
			if (l.di(x, y, x + 12, y + 12)) {
				l.tip(t("merosot"));
			}
		}
		int ink = Palette.opaque(lembar.i(GabungLembar.WARNA, 0xB0B0B0));
		l.g.fill(x0, y0 + 15, x1, y0 + 16, Palette.argb(0xB0, ink & 0xFFFFFF));
		return y0 + 19;
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

	@Override
	public boolean angka(int n) {
		return fokusRing.angka(n);
	}
}
