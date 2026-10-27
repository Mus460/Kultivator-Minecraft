package dev.kultivator.client.hall;

import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.InkField;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.GulirIsi;
import dev.kultivator.client.sekte.mandiri.MandiriClient;
import dev.kultivator.client.sekte.seragam.LambangPanel;
import dev.kultivator.client.sekte.seragam.SeragamClient;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.mandiri.api.IzinKegiatan;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.Kehatian;
import dev.kultivator.sekte.mandiri.api.ModeMandiri;
import dev.kultivator.sekte.mandiri.cerita.MandiriPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * Sekte › Ikhtisar › Identitas (DESIGN 43.5, 43.11 G44): everything that names and dresses the hall, as headings in one
 * scroll: "Nama Aula" (rename, up to 24 characters; was on the Aula tablet), the emblem and uniform designer of module S
 * ({@link LambangPanel}; was {@code LambangDesignerScreen}) and "Kebijakan Aula" (the hall policy of 39.7.4; was a tab of
 * the MandiriBoard, data handed over through {@link HallClient#papanMandiri}). Only the hall master edits.
 */
public class IdentitasPage extends Page {

	/** Key prefix of the mandiri story texts (as {@code JalanHidupTab.L}). */
	private static final String L = "kultivator.sekte.mandiri.cerita.";
	private static final String M = "kultivator.gui43.m05.";
	private static final int BARIS = 10;
	private static final long ULANG_MS = 5000L;

	private final GulirIsi kolom = new GulirIsi();
	private final LambangPanel lambang = new LambangPanel();
	private final InkField nama = new InkField(0, 0, 80, 14, 24, this::ganti);
	private long mintaPapan = Long.MIN_VALUE / 2;
	private long mintaLambang = Long.MIN_VALUE / 2;
	private boolean lambangMati;

	public IdentitasPage() {
		nama.hint(Component.translatable(M + "identitas.nama.hint"));
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.gui43.sub.ikhtisar.identitas");
	}

	@Override
	public int glyph() {
		return Glyphs.SEAL;
	}

	@Override
	public String helpTopic() {
		return "aula";
	}

	@Override
	protected void build() {
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		HallClient.minta();
		nama.setText(HallClient.info().getStringOr("name", ""));
		mintaPapan = Long.MIN_VALUE / 2;
		mintaLambang = Long.MIN_VALUE / 2;
		super.onShow(fokus);
	}

	@Override
	public void onHide() {
		nama.setFocused(false);
	}

	@Override
	public void tick() {
		long now = Util.getMillis();
		if (now - mintaPapan >= ULANG_MS) {
			mintaPapan = now;
			MandiriClient.aksi("papan", "", "", 0);
		}
		if (!lambang.siap() && !lambangMati && now - mintaLambang >= ULANG_MS) {
			mintaLambang = now;
			lambangMati = !SeragamClient.minta();
		}
	}

	private void ganti(String text) {
		String t = text == null ? "" : text.trim();
		if (!t.isEmpty() && HallClient.aulaSendiri()) {
			HallClient.send(Halls.OP_RENAME, t);
			nama.setFocused(false);
			pesan(Component.translatable(M + "identitas.nama.terkirim", t), Palette.JADE_DARK);
		}
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	@Override
	protected void lukis(Lukis l) {
		int cy = kolom.mulai(l, x, y, x + w - 5, y + h);
		int x1 = x + w - 5;
		cy = namaAula(l, x, cy, x1);
		cy += 4;
		if (lambang.siap()) {
			cy = lambang.gambar(l, x, cy, x1);
		} else {
			cy += l.kepala(Component.translatable("kultivator.sekte.seragam.ui.title"), x, cy, x1);
			l.samar(Component.translatable(lambangMati ? "kultivator.sekte.seragam.ui.mati" : "kultivator.gui43.konteks.memuat"), x + 2, cy, x1 - x - 2);
			cy += BARIS + 2;
		}
		cy = kebijakan(l, x, cy + 4, x1);
		kolom.selesai(l, cy);
	}

	// ================================================================== name

	private int namaAula(Lukis l, int x0, int y0, int x1) {
		int cy = y0 + l.kepala(Component.translatable(M + "identitas.nama"), x0, y0, x1);
		boolean own = HallClient.aulaSendiri();
		if (!own) {
			l.teks(HallClient.hallName(HallClient.info()), x0 + 2, cy, x1 - x0 - 2, l.gaya.teks);
			return cy + BARIS + 2;
		}
		Component label = Component.translatable("kultivator.hall.ui.rename");
		int bw = TalismanButton.lebar(l.font, label, 60, 110);
		nama.x = x0;
		nama.y = cy;
		nama.w = Math.max(TalismanButton.MIN_W, x1 - x0 - bw - 4);
		nama.h = 14;
		l.widget("nama", nama);
		if (TalismanButton.gambar(l, "ganti_nama", x1 - bw, cy, bw, 14, label, !nama.text().isBlank(), null, () -> ganti(nama.text()))) {
			l.tip(List.of(label, Component.translatable("kultivator.hall.ui.rename.tip")));
		}
		return cy + 18;
	}

	// ================================================================== hall policy (39.7.4)

	private int kebijakan(Lukis l, int x0, int y0, int x1) {
		int cy = y0 + l.kepala(Component.translatable(L + "papan.tampil.kebijakan"), x0, y0, x1);
		CompoundTag data = HallClient.papanMandiri();
		if (data == null) {
			// the server only listens for the board while Murid Mandiri is on (nothing is ever sent otherwise)
			boolean mati = !ClientPlayNetworking.canSend(MandiriPayloads.Aksi.TYPE);
			l.samar(Component.translatable(mati ? L + "papan.tampil.kebijakan.mati" : "kultivator.gui43.konteks.memuat"), x0 + 2, cy, x1 - x0 - 2);
			return cy + BARIS + 2;
		}
		boolean own = data.getBooleanOr("own", false);
		Kebijakan kb = Kebijakan.fromTag(data.getCompoundOrEmpty("kb"), Kebijakan.bawaan(ModeMandiri.SEIMBANG, 2, 3));
		int w = x1 - x0;
		cy = judulKecil(l, L + "tab.kb.mode", x0, cy, x1);
		ModeMandiri[] modes = ModeMandiri.values();
		cy = chips(l, "kb_mode", x0, cy, x1, modes.length, i -> Component.translatable(modes[i].kunciNama()), i -> kb.mode() == modes[i],
				own, i -> MandiriClient.aksi("aula_mode", "", modes[i].id(), 0), i -> L + "mode.desc." + modes[i].id());
		cy = judulKecil(l, L + "tab.kb.risiko", x0, cy, x1);
		Kehatian[] risks = Kehatian.values();
		cy = chips(l, "kb_risiko", x0, cy, x1, risks.length, i -> Component.translatable(risks[i].kunciNama()), i -> kb.risiko() == risks[i],
				own, i -> MandiriClient.aksi("aula_risiko", "", risks[i].id(), 0), i -> L + "kehatian.desc." + risks[i].id());
		cy = angka(l, "kb_saku", x0, cy, x1, Component.translatable(L + "papan.kb.saku", kb.uangSakuPersen()), own, kb.uangSakuPersen(), 0, 200, 25,
				n -> MandiriClient.aksi("aula_saku", "", "", n), L + "papan.kb.saku.tip");
		cy = angka(l, "kb_titipan", x0, cy, x1, Component.translatable(L + "papan.kb.titipan", NilaiBatu.text(kb.titipanNbMinggu())), own,
				kb.titipanNbMinggu(), 0, 5000, 100, n -> MandiriClient.aksi("aula_titipan", "", "", n), L + "papan.kb.titipan.tip");
		cy = angka(l, "kb_tembus", x0, cy, x1, Component.translatable(L + "papan.kb.tunggu_tembus", kb.tungguTerobosanHari()), own,
				kb.tungguTerobosanHari(), 0, 30, 1, n -> MandiriClient.aksi("aula_tunggu", "", "terobosan", n), L + "papan.kb.tunggu_tembus.tip");
		cy = angka(l, "kb_dao", x0, cy, x1, Component.translatable(L + "papan.kb.tunggu_dao", kb.tungguDaoHari()), own, kb.tungguDaoHari(), 0, 30, 1,
				n -> MandiriClient.aksi("aula_tunggu", "", "dao", n), L + "papan.kb.tunggu_dao.tip");
		cy = judulKecil(l, L + "papan.kb.jenjang", x0, cy + 2, x1);
		Kebijakan.Jenjang[] ranks = Kebijakan.Jenjang.values();
		cy = chips(l, "kb_jenjang", x0, cy, x1, ranks.length,
				i -> Component.translatable(L + "papan.kb.jenjang." + ranks[i].name().toLowerCase(Locale.ROOT)), i -> kb.jenjang() == ranks[i], own,
				i -> MandiriClient.aksi("aula_jenjang", "", ranks[i].name(), 0),
				i -> L + "papan.kb.jenjang." + ranks[i].name().toLowerCase(Locale.ROOT) + ".tip");
		cy = judulKecil(l, L + "papan.kb.seteru", x0, cy, x1);
		Kebijakan.SikapSeteru[] feud = Kebijakan.SikapSeteru.values();
		cy = chips(l, "kb_seteru", x0, cy, x1, feud.length,
				i -> Component.translatable(L + "papan.kb.seteru." + feud[i].name().toLowerCase(Locale.ROOT)), i -> kb.seteru() == feud[i], own,
				i -> MandiriClient.aksi("aula_seteru", "", feud[i].name(), 0),
				i -> L + "papan.kb.seteru." + feud[i].name().toLowerCase(Locale.ROOT) + ".tip");
		boolean careful = kb.hatiHatiTerobosan();
		cy = centang(l, "kb_hati", x0, cy, x1, Component.translatable(L + "papan.kb.hati"), careful, own,
				() -> MandiriClient.aksi("aula_hati", "", "", careful ? 0 : 1), L + "papan.kb.hati.tip");
		cy += 2;
		// permissions and notices: two check columns when there is room, else one
		int cols = w >= 240 ? 2 : 1;
		int cw = (w - (cols - 1) * 4) / cols;
		cy = judulKecil(l, L + "tab.kb.izin", x0, cy, x1);
		IzinKegiatan[] groups = IzinKegiatan.values();
		for (int i = 0; i < groups.length; i++) {
			IzinKegiatan group = groups[i];
			boolean on = group.boleh(kb.izin());
			int mask = on ? kb.izin() & ~group.bit() : kb.izin() | group.bit();
			centang(l, "kb_izin" + i, x0 + (i % cols) * (cw + 4), cy + (i / cols) * (BARIS + 3), x0 + (i % cols) * (cw + 4) + cw,
					Component.translatable(group.kunciNama()), on, own, () -> MandiriClient.aksi("aula_izin", "", "", mask), L + "izin.desc." + group.id());
		}
		cy += ((groups.length + cols - 1) / cols) * (BARIS + 3) + 2;
		cy = judulKecil(l, L + "papan.kb.beritahu", x0, cy, x1);
		Kebijakan.Pemberitahuan[] notices = Kebijakan.Pemberitahuan.values();
		for (int i = 0; i < notices.length; i++) {
			Kebijakan.Pemberitahuan n = notices[i];
			boolean on = kb.diberitahu(n);
			int mask = on ? kb.beritahu() & ~n.bit() : kb.beritahu() | n.bit();
			String id = n.name().toLowerCase(Locale.ROOT);
			centang(l, "kb_beritahu" + i, x0 + (i % cols) * (cw + 4), cy + (i / cols) * (BARIS + 3), x0 + (i % cols) * (cw + 4) + cw,
					Component.translatable(L + "beritahu." + id), on, own, () -> MandiriClient.aksi("aula_beritahu", "", "", mask), L + "beritahu." + id + ".tip");
		}
		cy += ((notices.length + cols - 1) / cols) * (BARIS + 3);
		if (!own) {
			cy += Math.round(l.bungkus(Component.translatable(M + "identitas.hanya_ketua"), x0 + 2, cy + 2, w - 2, l.gaya.teksSamar)) + 2;
		}
		return cy + 2;
	}

	private int judulKecil(Lukis l, String key, int x0, int y, int x1) {
		l.teks(Brush.bold(Component.translatable(key)), x0, y, x1 - x0, Palette.SEAL_DARK);
		return y + BARIS + 1;
	}

	/** A row of choice chips ({@code n} of them, as many per row as fit at 1.0); returns the y below. */
	private int chips(Lukis l, String kunci, int x0, int y, int x1, int n, java.util.function.IntFunction<Component> label,
			java.util.function.IntPredicate on, boolean own, IntConsumer aksi, java.util.function.IntFunction<String> tip) {
		int w = x1 - x0;
		int widest = 0;
		for (int i = 0; i < n; i++) {
			widest = Math.max(widest, TalismanButton.lebar(l.font, label.apply(i), 40, 140));
		}
		int perRow = Math.max(1, Math.min(n, (w + 3) / (widest + 3)));
		int cw = (w - (perRow - 1) * 3) / perRow;
		for (int i = 0; i < n; i++) {
			int idx = i;
			boolean chosen = on.test(i);
			Component lbl = label.apply(i);
			if (TalismanButton.gambar(l, kunci + i, x0 + (i % perRow) * (cw + 3), y + (i / perRow) * 16, cw, 14, lbl, own && !chosen,
					own ? null : Component.translatable(M + "identitas.hanya_ketua"), () -> aksi.accept(idx), false, chosen)) {
				l.tip(List.of(lbl.copy().withStyle(ChatFormatting.GOLD), Component.translatable(tip.apply(i))));
			}
		}
		return y + ((n + perRow - 1) / perRow) * 16 + 2;
	}

	/** A check line; returns the y below. */
	private int centang(Lukis l, String kunci, int x0, int y, int x1, Component label, boolean on, boolean own, Runnable aksi, String tipKey) {
		boolean over = l.di(x0, y, x1, y + 12);
		Brush.rect(l.g, x0 + 1.0F, y + 1.0F, x0 + 10.0F, y + 10.0F, Palette.argb(0x60, Palette.PAPER_SHADE));
		Brush.frame(l.g, x0 + 1, y + 1, x0 + 10, y + 10, over && own ? Palette.GOLD : Palette.INK_SOFT);
		if (on) {
			Brush.line(l.g, x0 + 2.5F, y + 5.5F, x0 + 4.8F, y + 8.5F, 1.2F, Palette.JADE_DARK);
			Brush.line(l.g, x0 + 4.8F, y + 8.5F, x0 + 9.0F, y + 2.5F, 1.2F, Palette.JADE_DARK);
		}
		l.teks(label, x0 + 13, y + 2, x1 - x0 - 13, on ? l.gaya.teks : l.gaya.teksSamar);
		l.zona(kunci, x0, y, x1, y + 12, aksi, own, own ? null : Component.translatable(M + "identitas.hanya_ketua"), true);
		l.fokusCincin(kunci, x0, y, x1, y + 12);
		if (over) {
			l.tip(List.of(label.copy().withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY), Component.translatable(tipKey)));
		}
		return y + BARIS + 3;
	}

	/** A number with − / + buttons (16 × 14); returns the y below. */
	private int angka(Lukis l, String kunci, int x0, int y, int x1, Component label, boolean own, int value, int min, int max, int step,
			IntConsumer send, String tipKey) {
		l.teks(label, x0, y + 3, x1 - x0 - 40, l.gaya.teks);
		if (l.di(x0, y, x1 - 40, y + 14)) {
			l.tip(List.of(label.copy().withStyle(ChatFormatting.GOLD), Component.translatable(tipKey)));
		}
		Component alasan = own ? null : Component.translatable(M + "identitas.hanya_ketua");
		TalismanButton.gambar(l, kunci + "_kurang", x1 - 36, y, 17, 14, Component.literal("−"), own && value > min, alasan,
				() -> send.accept(Math.max(min, value - step)));
		TalismanButton.gambar(l, kunci + "_tambah", x1 - 17, y, 17, 14, Component.literal("+"), own && value < max, alasan,
				() -> send.accept(Math.min(max, value + step)));
		return y + 16;
	}

	// ================================================================== input

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY) || kolom.roda(mouseX, mouseY, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return super.keyPressed(event) || !mauTombol() && kolom.tombol(event);
	}
}
