package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.DaftarGulir;
import dev.kultivator.client.tempat.ui.kit.GelembungDialog;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.LayarTempatDasar;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Dialog;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * {@code dialog} (DESIGN 38.U.3.1): every 38 NPC. Tablet "Bicara" — the speech bubble with the live portrait (blinking,
 * nodding while the line is written), the numbered choices (keys 1-9; an unmet condition is drawn locked with its reason
 * and cannot be picked); tablet "Jasa" — the NPC's services with hours, each opening its screen for the same NPC;
 * tablet "Quest" — the quests this NPC gives or takes. "Jasa" and "Quest" only appear when the sheet carries them.
 * When the dialog ended only "(Esc) tutup" remains.
 */
public final class LayarDialog extends LayarTempatDasar {

	private final GelembungDialog gelembung = new GelembungDialog();
	private final DaftarGulir daftarJasa = new DaftarGulir();
	private final DaftarGulir daftarQuest = new DaftarGulir();

	public LayarDialog(int npcId, CompoundTag lembar) {
		super(Dialog.LAYAR, npcId, lembar, TeksTempat.k("layar.dialog"), TempatWarna.Gaya.KERTAS);
	}

	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of(tabBaru(Dialog.TAB_BICARA, "tab.bicara", IkonTempat.GULUNGAN), tabBaru(Dialog.TAB_JASA, "tab.jasa", LembarKunci.Ikon.LONCENG),
				tabBaru(Dialog.TAB_QUEST, "tab.quest", IkonTempat.SURAT));
	}

	@Override
	protected boolean tabAda(String id) {
		return switch (id) {
			case Dialog.TAB_JASA -> !lembar.daftar(Dialog.JASA).isEmpty();
			case Dialog.TAB_QUEST -> !lembar.daftar(Dialog.QUEST).isEmpty();
			default -> true;
		};
	}

	@Override
	protected void tabBerubah() {
		daftarJasa.mulaiUlang(net.minecraft.util.Util.getMillis());
		daftarQuest.mulaiUlang(net.minecraft.util.Util.getMillis());
	}

	@Override
	protected Component judulBawaan() {
		Lembar npc = lembar.sub(LembarKunci.NPC);
		return npc.ada(LembarKunci.NAMA) ? npc.teks(LembarKunci.NAMA) : title;
	}

	@Override
	protected boolean labelTab() {
		return false;
	}

	@Override
	protected boolean angka(int n) {
		if (!Dialog.TAB_BICARA.equals(tab)) {
			return false;
		}
		for (Lembar p : lembar.daftar(Dialog.PILIHAN)) {
			if (p.i(LembarKunci.NO, -1) == n) {
				// a long line in pages: the number keys read on until the choices show
				if (gelembung.adaLanjut()) {
					gelembung.tekan();
					return true;
				}
				pilih(p);
				return true;
			}
		}
		return false;
	}

	@Override
	protected boolean spasi() {
		return Dialog.TAB_BICARA.equals(tab) && gelembung.tekan();
	}

	@Override
	protected Component petunjuk() {
		return Dialog.TAB_BICARA.equals(tab) ? TeksTempat.k("kunci.dialog") : null;
	}

	private void pilih(Lembar p) {
		if (p.b(LembarKunci.TERKUNCI)) {
			dev.kultivator.client.screen.ui.UiSound.denied();
			Component alasan = p.teksAtau(LembarKunci.ALASAN);
			if (alasan != null) {
				tampilPesan(alasan, TempatWarna.BAHAYA);
			}
			return;
		}
		if (gelembung.menulis(net.minecraft.util.Util.getMillis())) {
			gelembung.selesaikan();
		}
		kirim(LembarKunci.op(Dialog.OP_PILIH, p.i(LembarKunci.NO, 0)));
	}

	@Override
	protected void isi(Lukis l, int x0, int y0, int x1, int y1) {
		switch (tab) {
			case Dialog.TAB_JASA -> jasa(l, x0, y0, x1, y1);
			case Dialog.TAB_QUEST -> quest(l, x0, y0, x1, y1);
			default -> bicara(l, x0, y0, x1, y1);
		}
	}

	/** Height of the choice block for {@code n} choices in {@code kolom} columns (rows of 15 + the gap above). */
	private static int blokPilihan(int n, int kolom) {
		return n <= 0 ? 0 : ((n + kolom - 1) / kolom) * 15 + 6;
	}

	private void bicara(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar npc = lembar.sub(LembarKunci.NPC);
		boolean akhir = lembar.b(Dialog.AKHIR);
		List<Lembar> pilihan = akhir ? List.of() : lembar.daftar(Dialog.PILIHAN);
		int n = pilihan.size();
		int lebar = x1 - x0 - 8;
		// the bubble gets what the choices leave; many choices on a short screen go into two columns (38.U.2, scale 4)
		int bawahMin = y0 + Math.min(52, Math.max(30, y1 - y0 - 30));
		int kolom = 1;
		int blok = akhir ? 18 : blokPilihan(n, 1);
		if (!akhir && n >= 3 && y1 - blok < bawahMin && lebar >= 220) {
			kolom = 2;
			blok = blokPilihan(n, 2);
		}
		int batas = Math.max(bawahMin, Math.min(y0 + 96, y1 - blok));
		int bubbleBottom = gelembung.gambar(l, npc, lembar.teks(LembarKunci.TEKS), x0, y0, x1, batas);
		int potret = dev.kultivator.client.tempat.ui.kit.Potret.ukuran(Math.min(56, batas - y0 - 4));
		int y = Math.max(bubbleBottom, y0 + potret) + 6;
		if (akhir) {
			Brush.centered(l.g, l.font, TeksTempat.k("dialog.akhir"), (x0 + x1) / 2.0F, Math.min(y + 4, y1 - 9), gaya.teksSamar, 1.0F, false);
			return;
		}
		if (n == 0) {
			return;
		}
		boolean tunggu = gelembung.menulis(l.kini) || gelembung.adaLanjut();
		int baris = (n + kolom - 1) / kolom;
		int h = Math.max(13, Math.min(17, (y1 - y) / baris - 2));
		int kw = kolom == 1 ? lebar : (lebar - 4) / 2;
		for (int i = 0; i < n; i++) {
			Lembar p = pilihan.get(i);
			int px = x0 + 4 + (kolom == 1 ? 0 : (i % kolom) * (kw + 4));
			int py = y + (kolom == 1 ? i : i / kolom) * (h + 2);
			if (py + h > y1) {
				break;
			}
			int no = p.i(LembarKunci.NO, i + 1);
			boolean kunci = p.b(LembarKunci.TERKUNCI);
			Component teks = p.teks(LembarKunci.TEKS);
			Component label = Component.literal(no + "  ").withColor(kunci ? TempatWarna.terkunci(gaya.teks) : gaya.aksen).append(teks);
			// the choices fade in after the line is written (and after its last page)
			if (tunggu) {
				l.g.fill(px, py, px + kw, py + h, Palette.argb(0x18, 0x000000));
				continue;
			}
			tombol.gambar(l, "pilih" + no, px, py, kw, h, label, !kunci, p.teksAtau(LembarKunci.ALASAN), () -> pilih(p));
			if (kunci) {
				int pw = Math.min(66, kw / 2);
				int pita = px + kw - pw - 4;
				IkonTempat.pita(l.g, false, pita, py + (h - 10) / 2, pw, 10, 0xC0FFFFFF);
				IkonTempat.gambar(l.g, LembarKunci.Ikon.GEMBOK, pita + 4, py + (h - 8) / 2, 8, 0xFFFFFFFF);
				Component alasan = p.teksAtau(LembarKunci.ALASAN);
				if (alasan != null) {
					Brush.fit(l.g, l.font, alasan, pita + 14, py + (h - 6) / 2.0F, pw - 16, 0xFFF0F0F0, 0.6F, 0.6F, false);
				}
			}
		}
		if (tunggu && gelembung.adaLanjut()) {
			Brush.fitCentered(l.g, l.font, TeksTempat.k("dialog.lanjut"), (x0 + x1) / 2.0F, y + 2, lebar, gaya.teksSamar, 0.75F, 0.6F, false);
		}
	}

	private void jasa(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> jasa = lembar.daftar(Dialog.JASA);
		y0 += subjudul(l, TeksTempat.k("dialog.jasa_judul"), x0, y0, x1);
		daftarJasa.gambar(l, "jasa", x0, y0, x1, y1, jasa.size(), 24, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = jasa.get(i);
			boolean kunci = e.b(LembarKunci.TERKUNCI);
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			int ikon = e.i(LembarKunci.IKON, LembarKunci.Ikon.layanan(e.id()));
			IkonTempat.gambar(lk.g, ikon, rx0 + 4, ry0 + 3, 16, kunci ? 0x90FFFFFF : 0xFFFFFFFF);
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 24, ry0 + 3, rx1 - rx0 - 110, kunci ? TempatWarna.terkunci(gaya.teks) : gaya.teks);
			Component cat = kunci ? e.teksAtau(LembarKunci.ALASAN) : e.teksAtau(LembarKunci.CATATAN);
			if (cat != null) {
				lk.keterangan(cat, rx0 + 24, ry0 + 13, rx1 - rx0 - 110, kunci ? TempatWarna.BAHAYA : gaya.teksSamar);
			}
			tombolOp(lk, "jasa_buka" + i, rx1 - 74, ry0 + 4, 70, 15, TeksTempat.k("dialog.buka"), !kunci, e.teksAtau(LembarKunci.ALASAN),
					LembarKunci.op(Dialog.OP_JASA, e.id()));
		}, () -> {
			int i = daftarJasa.pilihan();
			if (i >= 0 && i < jasa.size() && !jasa.get(i).b(LembarKunci.TERKUNCI)) {
				kirim(LembarKunci.op(Dialog.OP_JASA, jasa.get(i).id()));
			}
		}, null);
	}

	private void quest(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> quest = lembar.daftar(Dialog.QUEST);
		y0 += subjudul(l, TeksTempat.k("dialog.quest_judul"), x0, y0, x1);
		daftarQuest.gambar(l, "quest", x0, y0, x1, y1, quest.size(), 30, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = quest.get(i);
			PanelTempat.kartu(lk.g, gaya, rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			IkonTempat.gambar(lk.g, IkonTempat.SURAT, rx0 + 4, ry0 + 6, 16, 0xFFFFFFFF);
			if (e.b(LembarKunci.BARU)) {
				Brush.disc(lk.g, rx0 + 19, ry0 + 6, 2.5F, TempatWarna.SEGEL);
			}
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 24, ry0 + 3, rx1 - rx0 - 106, gaya.teks);
			lk.bungkus(e.teks(LembarKunci.TEKS), rx0 + 24, ry0 + 13, rx1 - rx0 - 106, gaya.teksLemah, 0.75F, ry1 - ry0 - 14);
			tombolOp(lk, "quest_buka" + i, rx1 - 74, ry0 + 7, 70, 15, TeksTempat.k("dialog.bicarakan"), true, null,
					LembarKunci.op(Dialog.OP_QUEST, e.id()));
		}, () -> {
			int i = daftarQuest.pilihan();
			if (i >= 0 && i < quest.size()) {
				kirim(LembarKunci.op(Dialog.OP_QUEST, quest.get(i).id()));
			}
		}, null);
	}
}
