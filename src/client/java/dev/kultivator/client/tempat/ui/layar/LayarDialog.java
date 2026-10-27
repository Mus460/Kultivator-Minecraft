package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.dunia.kota42.ui.layar.DialogKota;
import dev.kultivator.client.hub.DiSini;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.DaftarPilihan;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Potret;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Dialog;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * {@code dialog} (DESIGN 38.U.3.1, 43.8.3): the one NPC conversation of the mod (G55): every 38 NPC, the 41 recruiters and,
 * through {@code DimensiJembatan}, the eleven people of the underworld and the sea. Sub "Bicara" — the speech bubble with the
 * live portrait (blinking, nodding while the line is written) and the numbered choices ({@link DaftarPilihan}: ↑↓ Enter,
 * a click, or under Di Sini the keys 1-9, 43.17 K3; an unmet condition is locked with its reason); sub "Jasa" — the NPC's
 * services with hours, each opening its sheet for the same NPC (the talk stays one step back); sub "Quest" — the quests this
 * NPC gives or takes. "Jasa" and "Quest" only appear when the sheet carries them. When the dialog ended only the closing line
 * remains. Drawn with the single kit (text 1.0: a long line is split into pages, never shrunk).
 * <p>
 * A townsperson's conversation ({@code KotaLembar.Dialog#CAKAP}, DESIGN 42.11.1) adds the speaker strip, the tone picker, the
 * promise mark and the tablets Kabar, Jalan, Hubungan, Tolong and Beri, drawn by {@link DialogKota}; its options send
 * {@code ucap:NO:NADA}.
 * <p>
 * Bridged sheets ({@code DimensiJembatan}): the ops go to the bridge instead of the 38 server, a choice marked {@link #YAKIN}
 * asks the Gulungan's confirm seal first (medium: hold 1.5 s; was the scroll's 2 s hold), and the help topic is the
 * bridge's.
 */
public final class LayarDialog extends LayarInti {

	/** Client-only choice key (bridged sheets): the choice is confirmed with the seal before its op is sent. */
	public static final String YAKIN = "yakin";
	private static final int PILIHAN_H = 15;

	private final Gelembung gelembung = new Gelembung();
	private final DaftarGulir daftarJasa = new DaftarGulir();
	private final DaftarGulir daftarQuest = new DaftarGulir();
	private final @Nullable Consumer<String> jembatan;
	private final @Nullable String bantuan;
	private final DialogKota kota = new DialogKota(this::kirim, this::tulisBoleh, this::alasanBaca);

	public LayarDialog(int npcId, CompoundTag lembar) {
		this(npcId, lembar, null, null);
	}

	/** A bridged dialog: {@code jembatan} gets every op, {@code bantuan} is the help topic (null = the sheet's). */
	public LayarDialog(int npcId, CompoundTag lembar, @Nullable Consumer<String> jembatan, @Nullable String bantuan) {
		super(Dialog.LAYAR, npcId, lembar, k("layar.dialog"), LembarKunci.GAYA_KERTAS);
		this.jembatan = jembatan;
		this.bantuan = bantuan;
	}

	@Override
	protected List<Tab> daftarTab() {
		if (cakap()) {
			return List.of(tab(Dialog.TAB_BICARA, "tab.bicara", Glyphs.Atlas.GULUNGAN), tab(KotaLembar.Dialog.TAB_KABAR, "kota.tab.kabar_dialog", Glyphs.Atlas.PIALA),
					tab(KotaLembar.Dialog.TAB_JALAN, "kota.tab.jalan", Glyphs.Atlas.KOMPAS), tab(KotaLembar.Dialog.TAB_HUBUNGAN, "kota.tab.hubungan", Glyphs.Atlas.HATI),
					tab(KotaLembar.Dialog.TAB_TOLONG, "kota.tab.tolong", Glyphs.Atlas.TANYA), tab(KotaLembar.Dialog.TAB_BERI, "kota.tab.beri", Glyphs.Atlas.BINTANG_KOSONG),
					tab(Dialog.TAB_JASA, "tab.jasa", LembarKunci.Ikon.LONCENG), tab(Dialog.TAB_QUEST, "tab.quest", Glyphs.Atlas.SURAT));
		}
		return List.of(tab(Dialog.TAB_BICARA, "tab.bicara", Glyphs.Atlas.GULUNGAN), tab(Dialog.TAB_JASA, "tab.jasa", LembarKunci.Ikon.LONCENG),
				tab(Dialog.TAB_QUEST, "tab.quest", Glyphs.Atlas.SURAT));
	}

	/** True when the sheet is the conversation of a townsperson (42.11.1). */
	private boolean cakap() {
		return lembar != null && DialogKota.cakap(lembar);
	}

	@Override
	protected boolean tabAda(String id) {
		return switch (id) {
			case Dialog.TAB_JASA -> !lembar.daftar(Dialog.JASA).isEmpty();
			case Dialog.TAB_QUEST -> !lembar.daftar(Dialog.QUEST).isEmpty();
			case KotaLembar.Dialog.TAB_JALAN -> !lembar.sub(KotaLembar.Dialog.JALAN).kosongSemua();
			case KotaLembar.Dialog.TAB_TOLONG -> !lembar.daftar(KotaLembar.Dialog.TOLONG).isEmpty();
			case KotaLembar.Dialog.TAB_BERI -> !lembar.sub(KotaLembar.Dialog.BERI).kosongSemua();
			default -> true;
		};
	}

	@Override
	protected void tabBerubah() {
		daftarJasa.mulaiUlang(Util.getMillis());
		daftarQuest.mulaiUlang(Util.getMillis());
		kota.tabBerubah();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (cakap() && kota.roda(tab, mouseX, mouseY, scrollY)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	protected boolean tombolLain(KeyEvent event) {
		return cakap() && kota.tombol(tab, event);
	}

	@Override
	protected Component judulBawaan() {
		Lembar npc = lembar.sub(LembarKunci.NPC);
		return npc.ada(LembarKunci.NAMA) ? npc.teks(LembarKunci.NAMA) : title;
	}

	@Override
	public String helpTopic() {
		return bantuan != null ? bantuan : super.helpTopic();
	}

	@Override
	public boolean lebar() {
		return false;
	}

	// =================================================================== ops (bridged sheets go to their bridge)

	@Override
	protected void kirim(String op) {
		if (jembatan != null) {
			jembatan.accept(op);
			return;
		}
		super.kirim(op);
	}

	/** A bridged dialog has no 38 server session to end. */
	@Override
	public void onHide() {
		if (jembatan != null) {
			DiSini.lepas(this);
			return;
		}
		super.onHide();
	}

	// =================================================================== keys

	/**
	 * Digit {@code n} under Di Sini (K3): a long line turns its page first, then the choice numbered {@code n} is picked
	 * (also while the line is still being written: it finishes it). Digits without such a choice stay the Gulungan's.
	 */
	@Override
	public boolean angka(int n) {
		if (!Dialog.TAB_BICARA.equals(tab) || lembar.b(Dialog.AKHIR)) {
			return false;
		}
		// 42.5A.7: Shift + 1..6 picks the tone, plain digits pick the option
		if (cakap() && Minecraft.getInstance().hasShiftDown()) {
			return kota.pilihNada(lembar, n);
		}
		for (Lembar p : lembar.daftar(Dialog.PILIHAN)) {
			if (p.i(LembarKunci.NO, -1) == n) {
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
		return Dialog.TAB_BICARA.equals(tab) ? k("kunci.dialog") : null;
	}

	private void pilih(Lembar p) {
		if (p.b(LembarKunci.TERKUNCI)) {
			UiSound.denied();
			Component alasan = p.teksAtau(LembarKunci.ALASAN);
			if (alasan != null) {
				tampilPesan(alasan, Palette.BAHAYA);
			}
			return;
		}
		if (gelembung.menulis(Util.getMillis())) {
			gelembung.selesaikan();
		}
		String op = cakap() ? kota.opUcap(p.i(LembarKunci.NO, 0)) : LembarKunci.op(Dialog.OP_PILIH, p.i(LembarKunci.NO, 0));
		if (p.b(YAKIN)) {
			konfirmasi(SegelKonfirmasi.Tingkat.SEDANG, Component.translatable("kultivator.gui43.konteks.yakin", p.teks(LembarKunci.TEKS)), () -> kirim(op));
			return;
		}
		kirim(op);
	}

	private @Nullable Lembar pilihanNo(int no) {
		for (Lembar p : lembar.daftar(Dialog.PILIHAN)) {
			if (p.i(LembarKunci.NO, -1) == no) {
				return p;
			}
		}
		return null;
	}

	// =================================================================== drawing

	@Override
	protected void isiInti(Lukis l, int x0, int y0, int x1, int y1) {
		switch (tab) {
			case Dialog.TAB_JASA -> jasa(l, x0, y0, x1, y1);
			case Dialog.TAB_QUEST -> quest(l, x0, y0, x1, y1);
			case KotaLembar.Dialog.TAB_KABAR -> kota.kabar(l, lembar, x0, y0, x1, y1);
			case KotaLembar.Dialog.TAB_JALAN -> kota.jalan(l, lembar, x0, y0, x1, y1);
			case KotaLembar.Dialog.TAB_HUBUNGAN -> kota.hubungan(l, lembar, x0, y0, x1, y1);
			case KotaLembar.Dialog.TAB_TOLONG -> kota.tolong(l, lembar, x0, y0, x1, y1);
			case KotaLembar.Dialog.TAB_BERI -> kota.beri(l, lembar, x0, y0, x1, y1);
			default -> bicara(l, x0, y0, x1, y1);
		}
	}

	private void bicara(Lukis l, int x0, int y0, int x1, int y1) {
		Lembar npc = lembar.sub(LembarKunci.NPC);
		boolean akhir = lembar.b(Dialog.AKHIR);
		boolean cakap = cakap();
		if (cakap) {
			y0 += kota.kepala(l, npc, x0, y0, x1);
		}
		List<DaftarPilihan.Pilihan> pilihan = akhir ? List.of() : cakap ? kota.pilihan(lembar) : DaftarPilihan.Pilihan.dari(lembar.daftar(Dialog.PILIHAN));
		int n = pilihan.size();
		int ekstra = cakap && !akhir ? (lembar.sub(KotaLembar.Dialog.NADA).daftar(LembarKunci.DAFTAR).isEmpty() ? 0 : 18) + (lembar.ada(KotaLembar.Dialog.RAUT) ? 12 : 0) : 0;
		int lebar = x1 - x0 - 8;
		// the bubble gets what the choices leave; many choices on a short box go into two columns (scale 4)
		int bawahMin = y0 + Math.min(52, Math.max(30, y1 - y0 - 30));
		int kolom = DaftarPilihan.kolom(n, lebar, y1 - bawahMin - 6);
		int blok = akhir ? 18 : n == 0 ? 0 : DaftarPilihan.tinggi(n, kolom, PILIHAN_H) + 6 + ekstra;
		int batas = Math.max(bawahMin, Math.min(y0 + 96, y1 - blok));
		int bubbleBottom = gelembung.gambar(l, npc, lembar.teks(LembarKunci.TEKS), x0, y0, x1, batas);
		int potret = Potret.ukuran(Math.min(56, batas - y0 - 4));
		int y = Math.max(bubbleBottom, y0 + potret) + 6;
		if (akhir) {
			l.tengah(k("dialog.akhir"), (x0 + x1) / 2.0F, Math.min(y + 4, y1 - 9), lebar, l.gaya.teksSamar);
			return;
		}
		if (n == 0) {
			return;
		}
		if (cakap) {
			y += kota.raut(l, lembar, x0 + 4, y - 2, x1 - 4);
			y += kota.nada(l, lembar, x0 + 4, y, x1 - 4);
		}
		// the choices fade in after the line is written (and after its last page)
		boolean tunggu = gelembung.menulis(l.kini) || gelembung.adaLanjut();
		DaftarPilihan.gambar(l, "pilih", pilihan, x0 + 4, y, x1 - 4, y1, tunggu, p -> {
			Lembar e = pilihanNo(p.no());
			if (e != null) {
				pilih(e);
			}
		});
		if (tunggu && gelembung.adaLanjut()) {
			l.tengah(k("dialog.lanjut"), (x0 + x1) / 2.0F, y + 3, lebar, l.gaya.teksSamar);
		}
	}

	private void jasa(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> jasa = lembar.daftar(Dialog.JASA);
		y0 += l.kepala(k("dialog.jasa_judul"), x0, y0, x1);
		daftarJasa.gambar(l, "jasa", x0, y0, x1, y1, jasa.size(), 26, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = jasa.get(i);
			boolean kunci = e.b(LembarKunci.TERKUNCI);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			int ikon = e.i(LembarKunci.IKON, LembarKunci.Ikon.layanan(e.id()));
			Glyphs.Atlas.ikon(lk.g, ikon, rx0 + 4, ry0 + 4, 16, kunci ? 0x90FFFFFF : 0xFFFFFFFF);
			int bw = Math.min(70, (rx1 - rx0) / 3);
			int tw = rx1 - rx0 - 30 - bw;
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 24, ry0 + 3, tw, kunci ? Palette.terkunci(lk.gaya.teks) : lk.gaya.teks);
			Component cat = kunci ? e.teksAtau(LembarKunci.ALASAN) : e.teksAtau(LembarKunci.CATATAN);
			if (cat != null) {
				lk.teks(cat, rx0 + 24, ry0 + 14, tw, kunci ? Palette.BAHAYA : lk.gaya.teksSamar);
			}
			tombolOp(lk, "jasa_buka" + i, rx1 - bw - 4, ry0 + (ry1 - ry0 - TOMBOL_H) / 2, bw, TOMBOL_H, k("dialog.buka"), !kunci,
					e.teksAtau(LembarKunci.ALASAN), LembarKunci.op(Dialog.OP_JASA, e.id()));
		}, () -> {
			int i = daftarJasa.pilihan();
			if (i >= 0 && i < jasa.size() && !jasa.get(i).b(LembarKunci.TERKUNCI) && tulisBoleh()) {
				kirim(LembarKunci.op(Dialog.OP_JASA, jasa.get(i).id()));
			}
		}, null);
	}

	private void quest(Lukis l, int x0, int y0, int x1, int y1) {
		List<Lembar> quest = lembar.daftar(Dialog.QUEST);
		y0 += l.kepala(k("dialog.quest_judul"), x0, y0, x1);
		// G22: the one active list with stages is Dunia › Perjalanan; here only the matters this NPC can discuss
		int by = y1 - TOMBOL_H;
		dev.kultivator.client.screen.ui.TalismanButton.gambar(l, "quest_perjalanan", x0, by, x1 - x0, TOMBOL_H, k("dialog.perjalanan"), true, null,
				() -> dev.kultivator.client.hub.HubNav.buka(dev.kultivator.client.hub.Tujuan.of(dev.kultivator.client.hub.HubNav.DUNIA,
						dev.kultivator.client.hub.Urutan.ID_PERJALANAN)));
		y1 = by - 3;
		daftarQuest.gambar(l, "quest", x0, y0, x1, y1, quest.size(), 22, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Lembar e = quest.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			Glyphs.Atlas.ikon(lk.g, Glyphs.Atlas.SURAT, rx0 + 4, ry0 + 3, 16, 0xFFFFFFFF);
			if (e.b(LembarKunci.BARU)) {
				Brush.disc(lk.g, rx0 + 19, ry0 + 4, 2.5F, Palette.SEAL);
			}
			int bw = Math.min(80, (rx1 - rx0) / 3);
			int tw = rx1 - rx0 - 30 - bw;
			lk.teks(Brush.bold(e.teks(LembarKunci.NAMA)), rx0 + 24, ry0 + (ry1 - ry0 - 8) / 2, tw, lk.gaya.teks);
			tombolOp(lk, "quest_buka" + i, rx1 - bw - 4, ry0 + (ry1 - ry0 - TOMBOL_H) / 2, bw, TOMBOL_H, k("dialog.bicarakan"), true, null,
					LembarKunci.op(Dialog.OP_QUEST, e.id()));
		}, () -> {
			int i = daftarQuest.pilihan();
			if (i >= 0 && i < quest.size()) {
				kirim(LembarKunci.op(Dialog.OP_QUEST, quest.get(i).id()));
			}
		}, null);
	}

	// =================================================================== the speech bubble

	/**
	 * The speech panel (was {@code GelembungDialog}, now on the single kit): the live portrait on the left, the speaker's
	 * name and role title, the sect line, and the current line written out like a brush (about 60 characters per second; a
	 * click or Space shows it all at once). A new line restarts the brush; the speaker nods while it writes. Text is 1.0
	 * (43.0.5): a line that does not fit the bubble is split into pages with a pulsing "▼ 1/3", and the choices wait until
	 * the last page ({@link #adaLanjut}).
	 */
	private static final class Gelembung {

		private static final float HURUF_PER_DETIK = 60.0F;
		private static final float BARIS = 10.0F;

		private String teksTerakhir = "";
		private long mulai;
		private boolean selesaiPaksa;
		private int halaman;
		private int jumlahHalaman = 1;
		private int hurufHalaman = Integer.MAX_VALUE;

		void selesaikan() {
			selesaiPaksa = true;
		}

		boolean menulis(long kini) {
			int total = hurufHalaman == Integer.MAX_VALUE ? teksTerakhir.length() : hurufHalaman;
			return !selesaiPaksa && (kini - mulai) / 1000.0F * HURUF_PER_DETIK < total;
		}

		boolean adaLanjut() {
			return halaman < jumlahHalaman - 1;
		}

		boolean lanjut() {
			long now = Util.getMillis();
			if (menulis(now) || !adaLanjut()) {
				return false;
			}
			halaman++;
			mulai = now;
			selesaiPaksa = false;
			hurufHalaman = Integer.MAX_VALUE;
			UiSound.tab();
			return true;
		}

		/** A click or Space: finish the brush, else turn the page. True when something happened. */
		boolean tekan() {
			if (menulis(Util.getMillis())) {
				selesaikan();
				return true;
			}
			return lanjut();
		}

		/** Draws the bubble in the box; returns the bottom y of the text block. */
		int gambar(Lukis l, Lembar npc, Component teks, int x0, int y0, int x1, int y1) {
			String plain = teks.getString();
			if (!plain.equals(teksTerakhir)) {
				teksTerakhir = plain;
				mulai = l.kini;
				selesaiPaksa = false;
				halaman = 0;
				hurufHalaman = Integer.MAX_VALUE;
			}
			int potret = Potret.ukuran(Math.min(56, y1 - y0 - 4));
			float bicara = menulis(l.kini) ? 1.0F : 0.15F;
			Potret.gambar(l, x0, y0, potret, npc.i(LembarKunci.POTRET, -1), npc.i(LembarKunci.IKON, -1), npc.b(LembarKunci.TAKUT), bicara);
			int tx = x0 + potret + 8;
			// name line: name · title, sect under it
			Component nama = npc.teks(LembarKunci.NAMA);
			Component gelar = npc.teksAtau(LembarKunci.GELAR);
			Component kepala = gelar == null ? Brush.bold(nama)
					: Brush.bold(nama).copy().append(Component.literal(" · ").withColor(l.gaya.teksSamar)).append(gelar.copy().withColor(l.gaya.teksLemah));
			l.teks(kepala, tx, y0 + 1, x1 - tx, l.gaya.aksen);
			int ty = y0 + 12;
			Component sekte = npc.teksAtau(LembarKunci.SEKTE);
			if (sekte != null) {
				l.samar(sekte, tx, ty, x1 - tx);
				ty += 10;
			}
			// the bubble: an inset well with a tail pointing at the portrait
			int bx0 = tx - 4;
			int by0 = ty + 1;
			int bx1 = x1;
			int ruangTeks = Math.max(10, y1 - by0 - 6);
			int lebarTeks = Math.max(20, bx1 - bx0 - 10);
			List<FormattedCharSequence> lines = l.font.split(teks, lebarTeks);
			int perHalaman = Math.max(1, (int) Math.floor((ruangTeks + 2.0F) / BARIS));
			jumlahHalaman = Math.max(1, (lines.size() + perHalaman - 1) / perHalaman);
			halaman = Math.min(halaman, jumlahHalaman - 1);
			int dari = halaman * perHalaman;
			int sampai = Math.min(lines.size(), dari + perHalaman);
			int huruf = 0;
			for (int i = dari; i < sampai; i++) {
				huruf += panjang(lines.get(i));
			}
			hurufHalaman = huruf;
			int barisTampil = sampai - dari;
			int by1 = jumlahHalaman > 1 ? y1 : Math.max(by0 + 20, Math.min(y1, Math.round(by0 + 6 + barisTampil * BARIS)));
			l.sumur(bx0, by0, bx1, by1);
			for (int k = 0; k < 4; k++) {
				l.g.fill(bx0 - 1 - k, by0 + 6 + k, bx0, by0 + 7 + k, l.gaya.sumur);
			}
			int shown = selesaiPaksa ? Integer.MAX_VALUE : Math.round((l.kini - mulai) / 1000.0F * HURUF_PER_DETIK);
			int sisa = shown;
			int warna = l.gaya.teksSumur();
			for (int i = dari; i < sampai; i++) {
				if (sisa <= 0) {
					break;
				}
				FormattedCharSequence line = lines.get(i);
				int len = panjang(line);
				float ly = by0 + 4 + (i - dari) * BARIS;
				if (sisa >= len) {
					Brush.text(l.g, l.font, line, bx0 + 5, ly, warna, 1.0F, false);
				} else {
					FormattedCharSequence part = potong(line, sisa);
					Brush.text(l.g, l.font, part, bx0 + 5, ly, warna, 1.0F, false);
					// the wet brush tip
					float w = l.font.width(part);
					Brush.dot(l.g, bx0 + 6 + w, ly + 6, 1.0F, Palette.argb(0xC0, warna & 0xFFFFFF));
				}
				sisa -= len;
			}
			if (jumlahHalaman > 1) {
				// "▼ 2/3": more to read; pulses once the page is written
				boolean siap = !menulis(l.kini) && adaLanjut();
				Component tanda = Component.literal((adaLanjut() ? "▼ " : "") + (halaman + 1) + "/" + jumlahHalaman);
				float tw = l.font.width(tanda);
				float bob = siap ? (float) Math.sin(l.kini / 160.0D) : 0.0F;
				int a = siap ? 0xFF : 0x90;
				Brush.text(l.g, l.font, tanda, bx1 - tw - 4, by1 - 10 + bob, Palette.argb(a, warna & 0xFFFFFF), 1.0F, false);
			}
			l.zona("gelembung", bx0, by0, bx1, by1, this::tekan, true, null, false);
			return by1;
		}

		private static int panjang(FormattedCharSequence seq) {
			int[] n = {0};
			seq.accept((index, style, cp) -> {
				n[0]++;
				return true;
			});
			return n[0];
		}

		private static FormattedCharSequence potong(FormattedCharSequence seq, int max) {
			return sink -> {
				int[] n = {0};
				return seq.accept((index, style, cp) -> {
					if (n[0]++ >= max) {
						return false;
					}
					return sink.accept(index, style, cp);
				});
			};
		}
	}
}
